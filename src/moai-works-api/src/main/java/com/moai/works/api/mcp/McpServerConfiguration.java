package com.moai.works.api.mcp;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.McpToolUtils;
import org.springframework.ai.mcp.server.webmvc.transport.WebMvcStreamableServerTransportProvider;
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

import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpStreamableServerSession;
import io.modelcontextprotocol.spec.McpStreamableServerSession.McpStreamableServerSessionInit;
import io.modelcontextprotocol.spec.McpStreamableServerTransportProvider;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

/**
 * MCP 서버 — 기능별 입구 둘(Streamable HTTP). 바깥 AI Agent 가 붙는다.
 *
 * <ul>
 *   <li>{@code /mcp/issue} — 일감: 프로젝트 목록 + 일감 도구 전부 + 모듈 보기(list_modules) + Agent 스킬 읽기(list_skills · get_skill)</li>
 *   <li>{@code /mcp/project} — 프로젝트 관리: 프로젝트 목록 + 일감 템플릿 관리(보기 · 정하기 · 되돌리기) + 모듈 보기 · 관리(list_modules · set_module).
 *       프로젝트 만들기 · 지우기 같은 관리 기능 자리</li>
 * </ul>
 * 키 하나로 두 입구 모두 붙는다(2026-11-23 사용자: "다른 관리 기능과 이슈 관리 기능을 서로 구분" ·
 * "프로젝트 생성이나 삭제 등등은 관리 기능" ·
 * "키는 권한만 있으면 어떤 mcp 엔드포인트든 연결 가능해야 해").
 *
 * <p>seed-site-keeper-api {@code McpKeyServerConfiguration} 과 같은 방식: Spring AI 자동 설정 서버는 끄고
 * ({@code spring.ai.mcp.server.enabled=false}) 전송 프로바이더 · 서버를 여기서 직접 만든다. 입구마다
 * 도구 묶음을 따로 둘 수 있다(워크스페이스 입구 구조는 design/07).
 * 인증은 이 앞의 MCP 키 필터가 하고, {@code contextExtractor} 가 키 주인을 도구까지 옮긴다.
 *
 * <p>접근 기록(FR-MCP-006): 연결 시작 · 도구 목록 · 도구 호출은 {@link McpAccessRecorder} 가 결과를 돌려주기 전에 한 줄씩 남긴다 —
 * 세션 만들기 · 도구 목록 거르개 · 도구마다의 처리기를 감싼다. 저장하지 못하면 그 요청이 실패한다.
 *
 * <p>연결 안내(initialize 의 {@code instructions})와 판(serverInfo.version) — 일감 #183. 안내는 서버 빌더의 고정 글 대신 세션 만들기를 감싸
 * <b>연결마다</b> {@link McpInstructions} 로 만든다(스킬마다 지금 리비전 · 마지막 바뀐 점 · 사본 갱신 방법). 판은 {@link AppVersion}
 * (Maven 판 + 짧은 커밋 — 전에는 "0.1.0" 고정).
 */
@Configuration
public class McpServerConfiguration implements DisposableBean {

  private static final Logger log = LoggerFactory.getLogger(McpServerConfiguration.class);

  private final List<McpSyncServer> servers = new ArrayList<>();
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
      IssueMcpTools issues, ModuleMcpTools modules, SkillMcpTools skills) {
    return server("moai-works-issue", properties.mcp().issueEndpoint(), instructions::issue, projects, issues, modules,
        skills);
  }

  @Bean
  public RouterFunction<ServerResponse> projectMcpRouter(MoaiWorksProperties properties, ProjectMcpTools projects,
      TemplateMcpTools templates, ModuleMcpTools modules, ModuleManageMcpTools moduleAdmin) {
    return server("moai-works-project", properties.mcp().projectEndpoint(), instructions::project, projects, templates,
        modules, moduleAdmin);
  }

  private RouterFunction<ServerResponse> server(String name, String endpoint, Supplier<String> guide,
      Object... toolObjects) {
    WebMvcStreamableServerTransportProvider transport = WebMvcStreamableServerTransportProvider.builder()
        .jsonMapper(new JacksonMcpJsonMapper(jsonMapper))
        .mcpEndpoint(endpoint)
        .contextExtractor(McpCallContext::extract)
        .build();
    ToolCallback[] callbacks = MethodToolCallbackProvider.builder().toolObjects(toolObjects).build().getToolCallbacks();
    List<SyncToolSpecification> specs = recorder.wrap(endpoint, McpToolUtils.toSyncToolSpecification(List.of(callbacks)));
    McpSyncServer server = McpServer.sync(new RecordingTransport(transport, recorder, guide))
        .serverInfo(name, version)
        .capabilities(McpSchema.ServerCapabilities.builder().tools(false).build())
        .tools(specs)
        .addToolFilter(recorder::listed)
        .build();
    servers.add(server);
    log.info("[mcp] {} {} → {} (도구 {}개: {})", name, version, endpoint, specs.size(),
        specs.stream().map(s -> s.tool().name()).toList());
    return transport.getRouterFunction();
  }

  @Override
  public void destroy() {
    servers.forEach(McpSyncServer::closeGracefully);
  }

  /**
   * 연결마다 안내를 새로 — SDK 가 만든 initialize 결과에서 {@code instructions} 만 바꾼다(나머지는 그대로). guide 는 실패해도 고정 글을 돌려주므로
   * 연결을 막지 않는다({@link McpInstructions}).
   */
  static McpStreamableServerSession.Factory withInstructions(McpStreamableServerSession.Factory factory,
      Supplier<String> guide) {
    return request -> {
      McpStreamableServerSessionInit init = factory.startSession(request);
      return new McpStreamableServerSessionInit(init.session(), init.initResult().map(r -> new McpSchema.InitializeResult(
          r.protocolVersion(), r.capabilities(), r.serverInfo(), guide.get(), r.meta())));
    };
  }

  /**
   * 전송은 그대로 두고 세션 만들기만 감싼다 — initialize 결과의 안내를 이 연결의 것(guide)으로 바꾸고, 연결 시작을 접근 기록에 남긴 뒤
   * 세션 id 를 돌려준다
   */
  private record RecordingTransport(McpStreamableServerTransportProvider delegate, McpAccessRecorder recorder,
      Supplier<String> guide) implements McpStreamableServerTransportProvider {

    @Override
    public void setSessionFactory(McpStreamableServerSession.Factory factory) {
      delegate.setSessionFactory(recorder.sessionFactory(withInstructions(factory, guide)));
    }

    @Override
    public Mono<Void> notifyClients(String method, Object params) {
      return delegate.notifyClients(method, params);
    }

    @Override
    public Mono<Void> notifyClient(String sessionId, String method, Object params) {
      return delegate.notifyClient(sessionId, method, params);
    }

    @Override
    public Mono<Void> closeGracefully() {
      return delegate.closeGracefully();
    }

    @Override
    public void close() {
      delegate.close();
    }

    @Override
    public List<String> protocolVersions() {
      return delegate.protocolVersions();
    }
  }
}
