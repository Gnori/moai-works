package com.moai.works.api.mcplog;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;

/**
 * (키 id, User-Agent) → 그 클라이언트가 마지막 연결 시작({@code initialize})에서 밝힌 클라이언트 정보. 연결 시작을 접근 기록에 남긴 뒤 넣고,
 * 같은 키 · 같은 User-Agent 로 온 다른 요청(도구 목록 · 도구 호출 · 알림)의 줄에 붙인다 — 판정에는 쓰지 않는다.
 *
 * <p>MCP 서버는 세션을 두지 않으므로(무상태 — 일감 #240) 세션 id 로 묶지 못한다. 대신 요청마다 오는 두 값으로 묶는다: 키(누가)와
 * User-Agent(어느 프로그램 · 판). 같은 키 · 같은 User-Agent 의 두 클라이언트가 서로 다른 이름을 밝히면 나중 것이 남는다 — 최선의 덧붙임이고,
 * 줄마다 User-Agent 와 {@code MCP-Protocol-Version} 머리글은 늘 그대로 남는다.
 *
 * <p>메모리에만 둔다 — 다시 띄우면 비고, 다음 연결 시작에서 다시 찬다(그 사이의 줄은 클라이언트 이름이 비고 User-Agent 만 있다).
 * 가장 오래 안 쓴 것부터 버리고 {@value #MAX_CLIENTS}개까지. 다른 키로 온 요청에는 붙지 않는다(키 id 가 묶음의 한쪽이다).
 */
@Component
public class McpClientRegistry {

  static final int MAX_CLIENTS = 10_000;

  private record Client(UUID keyId, String userAgent) {
  }

  private final int max;
  private final Map<Client, McpClientInfo> clients;

  public McpClientRegistry() {
    this(MAX_CLIENTS);
  }

  McpClientRegistry(int max) {
    this.max = max;
    this.clients = new LinkedHashMap<>(256, 0.75f, true) {
      @Override
      protected boolean removeEldestEntry(Map.Entry<Client, McpClientInfo> eldest) {
        return size() > McpClientRegistry.this.max;
      }
    };
  }

  /** 연결 시작에서 밝힌 클라이언트 정보를 둔다 — 키를 모르면(거절된 요청) 두지 않는다 */
  synchronized void remember(UUID keyId, String userAgent, McpClientInfo client) {
    if (keyId != null && client != null && client != McpClientInfo.NONE) {
      clients.put(new Client(keyId, userAgent), client);
    }
  }

  /** 이 키 · 이 User-Agent 가 마지막으로 밝힌 클라이언트 정보, 없으면 없음 */
  synchronized McpClientInfo recall(UUID keyId, String userAgent) {
    if (keyId == null) {
      return McpClientInfo.NONE;
    }
    McpClientInfo c = clients.get(new Client(keyId, userAgent));
    return c == null ? McpClientInfo.NONE : c;
  }

  synchronized int size() {
    return clients.size();
  }
}
