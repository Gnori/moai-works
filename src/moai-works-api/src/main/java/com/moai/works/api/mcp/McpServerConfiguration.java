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

import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.spec.McpSchema;
import tools.jackson.databind.json.JsonMapper;

/**
 * MCP 서버 — 프로젝트 관리용 {@code /mcp/project} (Streamable HTTP). 바깥 AI Agent 가 붙는다.
 *
 * <p>seed-site-keeper-api {@code McpKeyServerConfiguration} 과 같은 방식: Spring AI 자동 설정 서버는 끄고
 * ({@code spring.ai.mcp.server.enabled=false}) 전송 프로바이더 · 서버를 여기서 직접 만든다. 입구마다
 * 도구 묶음을 따로 둘 수 있다(워크스페이스 입구 구조는 design/07).
 * 인증은 이 앞의 MCP 키 필터가 하고, {@code contextExtractor} 가 키 주인을 도구까지 옮긴다.
 */
@Configuration
public class McpServerConfiguration implements DisposableBean {

  private static final Logger log = LoggerFactory.getLogger(McpServerConfiguration.class);

  private final List<McpSyncServer> servers = new ArrayList<>();
  private final JsonMapper jsonMapper;
  private final String version;

  public McpServerConfiguration(JsonMapper jsonMapper, @Value("${moai-works.mcp.server-version:0.1.0}") String version) {
    this.jsonMapper = jsonMapper;
    this.version = version;
  }

  @Bean
  public RouterFunction<ServerResponse> projectMcpRouter(MoaiWorksProperties properties, ProjectMcpTools projects,
      IssueMcpTools issues) {
    return server("moai-works-project", properties.mcp().projectEndpoint(), projects, issues);
  }

  private RouterFunction<ServerResponse> server(String name, String endpoint, Object... toolObjects) {
    WebMvcStreamableServerTransportProvider transport = WebMvcStreamableServerTransportProvider.builder()
        .jsonMapper(new JacksonMcpJsonMapper(jsonMapper))
        .mcpEndpoint(endpoint)
        .contextExtractor(McpCallContext::extract)
        .build();
    ToolCallback[] callbacks = MethodToolCallbackProvider.builder().toolObjects(toolObjects).build().getToolCallbacks();
    List<SyncToolSpecification> specs = McpToolUtils.toSyncToolSpecification(List.of(callbacks));
    McpSyncServer server = McpServer.sync(transport)
        .serverInfo(name, version)
        .capabilities(McpSchema.ServerCapabilities.builder().tools(false).build())
        .tools(specs)
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
}
