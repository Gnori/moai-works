package com.moai.works.web.relay;

import java.io.IOException;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 바깥 MCP 클라이언트(Claude Code 등)의 연결 주소 — {@code https://<web-backend>/mcp/issue}(일감) · {@code /mcp/project}(프로젝트 관리),
 * 첨부 티켓 {@code /mcp/files/*}. 경로를 가리지 않고 {@code /mcp/**} 를 그대로 넘긴다.
 * 로그인 보안 체인 밖({@code SecurityConfiguration#mcpRelayChain})이고, 판정 없이 api 로 흘려보낸다.
 */
@RestController
public class McpRelayController {

  private final McpRelay relay;

  public McpRelayController(McpRelay relay) {
    this.relay = relay;
  }

  @RequestMapping({McpRelay.PREFIX, McpRelay.PREFIX + "/**"})
  public void relay(HttpServletRequest request, HttpServletResponse response) throws IOException {
    relay.relay(request, response);
  }
}
