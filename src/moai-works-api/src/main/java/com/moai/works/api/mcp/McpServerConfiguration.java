package com.moai.works.api.mcp;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.mcp.McpToolUtils;
import org.springframework.ai.mcp.server.webmvc.transport.WebMvcStreamableServerTransportProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import com.moai.works.api.config.MoaiWorksProperties;
import com.moai.works.api.mcplog.McpAccessRecorder;

import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpStreamableServerSession;
import io.modelcontextprotocol.spec.McpStreamableServerTransportProvider;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

/**
 * MCP 서버 — 기능별 입구 둘(Streamable HTTP). 바깥 AI Agent 가 붙는다.
 *
 * <ul>
 *   <li>{@code /mcp/issue} — 일감: 프로젝트 목록 + 일감 도구 전부 + 모듈 보기(list_modules)</li>
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
 */
@Configuration
public class McpServerConfiguration implements DisposableBean {

  private static final Logger log = LoggerFactory.getLogger(McpServerConfiguration.class);

  private final List<McpSyncServer> servers = new ArrayList<>();
  private final JsonMapper jsonMapper;
  private final McpAccessRecorder recorder;
  private final String version;

  public McpServerConfiguration(JsonMapper jsonMapper, McpAccessRecorder recorder,
      @Value("${moai-works.mcp.server-version:0.1.0}") String version) {
    this.jsonMapper = jsonMapper;
    this.recorder = recorder;
    this.version = version;
  }

  @Bean
  public RouterFunction<ServerResponse> issueMcpRouter(MoaiWorksProperties properties, ProjectMcpTools projects,
      IssueMcpTools issues, ModuleMcpTools modules) {
    return server("moai-works-issue", properties.mcp().issueEndpoint(), projects, issues, modules);
  }

  @Bean
  public RouterFunction<ServerResponse> projectMcpRouter(MoaiWorksProperties properties, ProjectMcpTools projects,
      TemplateMcpTools templates, ModuleMcpTools modules, ModuleManageMcpTools moduleAdmin) {
    return server("moai-works-project", properties.mcp().projectEndpoint(), projects, templates, modules, moduleAdmin);
  }

  private RouterFunction<ServerResponse> server(String name, String endpoint, Object... toolObjects) {
    WebMvcStreamableServerTransportProvider transport = WebMvcStreamableServerTransportProvider.builder()
        .jsonMapper(new JacksonMcpJsonMapper(jsonMapper))
        .mcpEndpoint(endpoint)
        .contextExtractor(McpCallContext::extract)
        .build();
    ToolCallback[] callbacks = MethodToolCallbackProvider.builder().toolObjects(toolObjects).build().getToolCallbacks();
    List<SyncToolSpecification> specs = recorder.wrap(endpoint, McpToolUtils.toSyncToolSpecification(List.of(callbacks)));
    McpSyncServer server = McpServer.sync(new RecordingTransport(transport, recorder))
        .serverInfo(name, version)
        .capabilities(McpSchema.ServerCapabilities.builder().tools(false).build())
        .tools(specs)
        .addToolFilter(recorder::listed)
        .build();
    servers.add(server);
    log.info("[mcp] {} → {} (도구 {}개: {})", name, endpoint, specs.size(),
        specs.stream().map(s -> s.tool().name()).toList());
    return transport.getRouterFunction();
  }

  @Override
  public void destroy() {
    servers.forEach(McpSyncServer::closeGracefully);
  }

  /** 전송은 그대로 두고 세션 만들기만 감싼다 — 연결 시작을 접근 기록에 남긴 뒤 세션 id 를 돌려준다 */
  private record RecordingTransport(McpStreamableServerTransportProvider delegate, McpAccessRecorder recorder)
      implements McpStreamableServerTransportProvider {

    @Override
    public void setSessionFactory(McpStreamableServerSession.Factory factory) {
      delegate.setSessionFactory(recorder.sessionFactory(factory));
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
