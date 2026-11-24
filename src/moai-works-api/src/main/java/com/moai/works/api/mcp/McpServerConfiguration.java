package com.moai.works.api.mcp;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.McpToolUtils;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import com.moai.works.api.config.AppVersion;
import com.moai.works.api.config.MoaiWorksProperties;
import com.moai.works.api.mcplog.McpAccessRecorder;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpStatelessServerHandler;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.spec.McpSchema;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

/**
 * MCP 서버 — 기능별 입구 둘(Streamable HTTP, 무상태). 바깥 AI Agent 가 붙는다.
 *
 * <ul>
 *   <li>{@code /mcp/issue} — 일감: 프로젝트 목록 + 일감 도구 전부 + 모듈 보기(list_modules) + Agent 스킬 읽기(list_skills · get_skill)
 *       + 설명 · 댓글의 Markdown 꼴 안내(get_markdown_guide — 일감 #217)</li>
 *   <li>{@code /mcp/project} — 프로젝트 관리: 프로젝트 목록 + 일감 템플릿 관리(보기 · 정하기 · 되돌리기) + 모듈 보기 · 관리(list_modules · set_module).
 *       프로젝트 만들기 · 지우기 같은 관리 기능 자리</li>
 * </ul>
 * 키 하나로 두 입구 모두 붙는다(2026-11-23 사용자: "다른 관리 기능과 이슈 관리 기능을 서로 구분" ·
 * "프로젝트 생성이나 삭제 등등은 관리 기능" ·
 * "키는 권한만 있으면 어떤 mcp 엔드포인트든 연결 가능해야 해").
 *
 * <p>seed-site-keeper-api {@code McpKeyServerConfiguration} 과 같은 방식: Spring AI 자동 설정 서버는 끄고
 * ({@code spring.ai.mcp.server.enabled=false}) 전송 · 서버를 여기서 직접 만든다. 입구마다
 * 도구 묶음을 따로 둘 수 있다(워크스페이스 입구 구조는 design/07).
 * 인증은 이 앞의 MCP 키 필터가 하고, {@code contextExtractor} 가 키 주인을 도구까지 옮긴다.
 *
 * <p><b>무상태</b>(일감 #240): 서버는 세션을 두지 않는다 — {@link McpHttpTransport} + {@code McpStatelessSyncServer}(MCP Java SDK 2.2.0).
 * 세션 id 를 주지도 요구하지도 않아 api 를 다시 띄워도 열려 있던 클라이언트(낡은 {@code Mcp-Session-Id} 를 보내는)가 그대로 일한다.
 * 도구 · 판정 · 접근 기록은 요청마다 키로 정해지므로 세션에 둘 것이 없다. 전에는 세션을 메모리에 두는
 * {@code WebMvcStreamableServerTransportProvider} 라 다시 띄울 때마다 열린 세션이 모두 404 "Session not found" 로 끊겼다.
 *
 * <p>도구 인자: Spring AI 가 {@code @Tool} 메서드에서 만든 도구를 {@link McpToolArguments} 로 감싸 꼴을 고르게 풀고, SDK 의 입력 검사는 끈다
 * (틀린 꼴도 {@code {"error": {…, "code": "INVALID_ARGUMENT", "field"}}} — 2026-11-24, 일감 #177).
 *
 * <p>접근 기록(FR-MCP-006): 연결 시작 · 도구 목록 · 도구 호출은 {@link McpAccessRecorder} 가 결과를 돌려주기 전에 한 줄씩 남긴다 —
 * 처리기의 initialize · 도구 목록 거르개 · 도구마다의 처리기를 감싼다. 저장하지 못하면 그 요청이 실패한다.
 *
 * <p>연결 안내(initialize 의 {@code instructions})와 판(serverInfo.version) — 일감 #183. 안내는 서버 빌더의 고정 글 대신 MCP 서버의 처리기를
 * 감싸 <b>initialize 마다</b> {@link McpInstructions} 로 만든다(스킬마다 지금 리비전 · 마지막 바뀐 점 · 사본 갱신 방법). 판은 {@link AppVersion}
 * (Maven 판 + 짧은 커밋 — 전에는 "0.1.0" 고정).
 */
@Configuration
public class McpServerConfiguration implements DisposableBean {

  private static final Logger log = LoggerFactory.getLogger(McpServerConfiguration.class);

  private final List<McpStatelessSyncServer> servers = new ArrayList<>();
  private final JsonMapper jsonMapper;
  private final McpAccessRecorder recorder;
  private final McpInstructions instructions;
  private final String version;

  public McpServerConfiguration(JsonMapper jsonMapper, McpAccessRecorder recorder, McpInstructions instructions,
      AppVersion version) {
    this.jsonMapper = jsonMapper;
    this.recorder = recorder;
    this.instructions = instructions;
    this.version = version.value();
  }

  @Bean
  public RouterFunction<ServerResponse> issueMcpRouter(MoaiWorksProperties properties, ProjectMcpTools projects,
      IssueMcpTools issues, ModuleMcpTools modules, SkillMcpTools skills, MarkdownMcpTools markdown) {
    return server("moai-works-issue", properties.mcp().issueEndpoint(), instructions::issue, projects, issues, modules,
        skills, markdown);
  }

  @Bean
  public RouterFunction<ServerResponse> projectMcpRouter(MoaiWorksProperties properties, ProjectMcpTools projects,
      TemplateMcpTools templates, ModuleMcpTools modules, ModuleManageMcpTools moduleAdmin) {
    return server("moai-works-project", properties.mcp().projectEndpoint(), instructions::project, projects, templates,
        modules, moduleAdmin);
  }

  private RouterFunction<ServerResponse> server(String name, String endpoint, Supplier<String> guide,
      Object... toolObjects) {
    // 처리기 감싸기(안에서 밖으로): JSON-RPC 오류 꼴 → initialize 결과의 안내를 이 연결의 것으로 → 연결 시작을 접근 기록에
    McpHttpTransport transport = new McpHttpTransport(new JacksonMcpJsonMapper(jsonMapper), endpoint, McpCallContext::extract,
        handler -> recorder.initializing(withInstructions(McpRpcErrors.wrap(handler), guide)));
    ToolCallback[] callbacks = MethodToolCallbackProvider.builder().toolObjects(toolObjects).build().getToolCallbacks();
    // 인자 꼴은 McpToolArguments 가 본다 — 틀리면 다른 도구 오류와 같은 JSON(INVALID_ARGUMENT · field), 같은 뜻이면 같은 꼴(일감 #177)
    // 결과는 McpResults 가 줄인다 — 빈 값 · 사람 id(일감 #282). REST 는 이 길을 지나지 않는다
    List<ToolCallback> guarded = McpResults.compact(McpToolArguments.guard(callbacks, jsonMapper), jsonMapper);
    List<SyncToolSpecification> specs = recorder.wrap(endpoint,
        guarded.stream().map(c -> McpToolUtils.toStatelessSyncToolSpecification(c, null)).toList());
    McpStatelessSyncServer server = McpServer.sync(transport)
        .serverInfo(name, version)
        .capabilities(McpSchema.ServerCapabilities.builder().tools(false).build())
        // SDK 의 입력 검사는 영어 평문(isError)으로 답해 JSON 으로 읽는 클라이언트가 깨진다 — 끄고 McpToolArguments 가 같은 일을 한다
        .validateToolInputs(false)
        .tools(specs)
        .addToolFilter(recorder::listed)
        .build();
    servers.add(server);
    log.info("[mcp] {} {} → {} (무상태, 도구 {}개: {})", name, version, endpoint, specs.size(),
        specs.stream().map(s -> s.tool().name()).toList());
    // GET(알림 스트림)은 전송이 405(Allow: POST, DELETE)로 바로 거절한다 — 먼저 보낼 알림이 없는데 붙잡아 두면 web-backend 중계가 연결마다
    // 요청 스레드 하나를 영영 묶었고, 200개가 다 차자 :11002 가 멎었다(2026-11-24 일감 #239)
    return transport.routerFunction();
  }

  @Override
  public void destroy() {
    servers.forEach(McpStatelessSyncServer::closeGracefully);
  }

  /**
   * initialize 마다 안내를 새로 — MCP 서버가 만든 initialize 결과에서 {@code instructions} 만 바꾼다(나머지 — 프로토콜 판 · 능력 · serverInfo 는
   * 그대로). guide 는 실패해도 고정 글을 돌려주므로 연결을 막지 않는다({@link McpInstructions}). 무상태라 "연결"은 initialize 요청 하나다.
   */
  static McpStatelessServerHandler withInstructions(McpStatelessServerHandler delegate, Supplier<String> guide) {
    return new McpStatelessServerHandler() {
      @Override
      public Mono<McpSchema.JSONRPCResponse> handleRequest(McpTransportContext context, McpSchema.JSONRPCRequest request) {
        Mono<McpSchema.JSONRPCResponse> response = delegate.handleRequest(context, request);
        if (!McpSchema.METHOD_INITIALIZE.equals(request.method())) {
          return response;
        }
        return response.map(r -> r.result() instanceof McpSchema.InitializeResult init
            ? McpSchema.JSONRPCResponse.result(r.id(), new McpSchema.InitializeResult(init.protocolVersion(), init.capabilities(),
                init.serverInfo(), guide.get(), init.meta()))
            : r);
      }

      @Override
      public Mono<Void> handleNotification(McpTransportContext context, McpSchema.JSONRPCNotification notification) {
        return delegate.handleNotification(context, notification);
      }
    };
  }
}
