package com.moai.works.api.mcplog;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Component;

/**
 * MCP 세션 id → 그 세션을 연 키 · 클라이언트 정보. 연결을 시작할 때 넣고(접근 기록에 남긴 뒤) 세션이 끝나면 뺀다.
 * 같은 세션의 다른 요청(도구 목록 · 도구 호출 · 알림)의 줄에 그 클라이언트 정보를 붙이는 데만 쓴다 — 판정에는 쓰지 않는다.
 *
 * <p>MCP 세션은 인스턴스 메모리에 있으므로(전송) 이 표도 메모리에 둔다. 가장 오래 안 쓴 것부터 버리고 {@value #MAX_SESSIONS}개까지.
 * 세션을 연 키와 다른 키로 온 요청에는 붙이지 않는다(남의 세션 id 를 들고 와도 그 클라이언트 정보를 빌리지 못한다).
 */
@Component
public class McpSessionRegistry {

  static final int MAX_SESSIONS = 10_000;

  private record Session(UUID keyId, McpClientInfo client) {
  }

  private final Map<String, Session> sessions = new LinkedHashMap<>(256, 0.75f, true) {
    @Override
    protected boolean removeEldestEntry(Map.Entry<String, Session> eldest) {
      return size() > MAX_SESSIONS;
    }
  };

  synchronized void remember(String sessionId, UUID keyId, McpClientInfo client) {
    if (sessionId != null) {
      sessions.put(sessionId, new Session(keyId, client == null ? McpClientInfo.NONE : client));
    }
  }

  /** 그 세션을 이 키가 열었으면 클라이언트 정보, 아니면 없음 */
  synchronized McpClientInfo client(String sessionId, UUID keyId) {
    if (sessionId == null) {
      return McpClientInfo.NONE;
    }
    Session s = sessions.get(sessionId);
    return s != null && Objects.equals(s.keyId(), keyId) ? s.client() : McpClientInfo.NONE;
  }

  synchronized void forget(String sessionId) {
    if (sessionId != null) {
      sessions.remove(sessionId);
    }
  }
}
