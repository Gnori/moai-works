package com.moai.works.api.mcplog;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * MCP 클라이언트 정보 — {@code initialize} 의 {@code clientInfo}(이름 · 버전 · 제목) · 프로토콜 판 · 능력(capabilities) 이름들.
 * 연결을 시작할 때 세션에 묶어 두고({@link McpSessionRegistry}) 그 세션의 모든 줄에 붙인다.
 * 글은 모두 바깥 클라이언트가 보낸 값이라 길이를 자르고 키 모양을 가린다({@link McpSecrets}).
 */
public record McpClientInfo(String name, String version, String title, String protocolVersion, String capabilities) {

  public static final McpClientInfo NONE = new McpClientInfo(null, null, null, null, null);

  /** {@code initialize} 요청의 params(JSON 을 푼 Map) → 클라이언트 정보. 모양이 틀리면 있는 것만 */
  static McpClientInfo fromInitializeParams(Object params) {
    if (!(params instanceof Map<?, ?> p)) {
      return NONE;
    }
    String name = null;
    String version = null;
    String title = null;
    if (p.get("clientInfo") instanceof Map<?, ?> info) {
      name = text(info.get("name"), 200);
      version = text(info.get("version"), 100);
      title = text(info.get("title"), 200);
    }
    String capabilities = null;
    if (p.get("capabilities") instanceof Map<?, ?> caps && !caps.isEmpty()) {
      List<String> names = new ArrayList<>();
      caps.keySet().forEach(k -> names.add(String.valueOf(k)));
      names.sort(String::compareTo);
      capabilities = McpSecrets.cut(String.join(",", names), 200);
    }
    return new McpClientInfo(name, version, title, text(p.get("protocolVersion"), 20), capabilities);
  }

  private static String text(Object value, int max) {
    return value instanceof String s && !s.isBlank() ? McpSecrets.cut(McpSecrets.mask(s.trim()), max) : null;
  }
}
