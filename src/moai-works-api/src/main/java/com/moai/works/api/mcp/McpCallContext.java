package com.moai.works.api.mcp;

import java.util.HashMap;
import java.util.Map;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.mcp.McpToolUtils;
import org.springframework.web.servlet.function.ServerRequest;

import com.moai.works.api.mcpkey.McpKeyPrincipal;

import io.modelcontextprotocol.common.McpTransportContext;
import jakarta.servlet.http.HttpServletRequest;

/**
 * MCP 도구 한 번 호출의 문맥 — 누가(키 주인).
 *
 * <p>{@link #extract} 가 요청마다 키 주인을 MCP 전송 문맥에 담고, 도구는 {@link #of(ToolContext)} 로 읽는다.
 *
 * <p>⚠️ 범위(워크스페이스 · 프로젝트)는 <b>모델이 넘긴 인자가 아니라 이 문맥</b>으로 정한다. 모델이 넘긴 프로젝트도
 * 이 주인의 권한으로 다시 판정한다(design/07).
 *
 * @param principal 키 주인 (MCP 키 필터가 확인)
 */
public record McpCallContext(McpKeyPrincipal principal) {

  private static final String KEY_PRINCIPAL = "moai-works.principal";

  /** Streamable HTTP 요청마다 부른다 — 전송 프로바이더의 contextExtractor. */
  static McpTransportContext extract(ServerRequest request) {
    HttpServletRequest http = request.servletRequest();
    Map<String, Object> values = new HashMap<>();
    if (http.getAttribute(McpKeyPrincipal.REQUEST_ATTRIBUTE) instanceof McpKeyPrincipal principal) {
      values.put(KEY_PRINCIPAL, principal);
    }
    return McpTransportContext.create(values);
  }

  /** {@code @Tool} 메서드의 {@link ToolContext} 에서 이번 호출 문맥을 꺼낸다. 문맥이 없으면 주인이 null. */
  public static McpCallContext of(ToolContext toolContext) {
    McpTransportContext transport = McpToolUtils.getMcpExchange(toolContext)
        .map(exchange -> exchange.transportContext())
        .orElse(McpTransportContext.EMPTY);
    return new McpCallContext(transport.get(KEY_PRINCIPAL) instanceof McpKeyPrincipal p ? p : null);
  }
}
