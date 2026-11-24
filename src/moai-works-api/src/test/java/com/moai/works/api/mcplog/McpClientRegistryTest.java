package com.moai.works.api.mcplog;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;

/**
 * (키, User-Agent) → 클라이언트 정보 표(일감 #240) — 끝이 있고(가장 오래 안 쓴 것부터 버림), 키가 다르면 빌리지 못하고, 키를 모르면 두지 않는다.
 * DB 없이 돈다.
 */
class McpClientRegistryTest {

  private static McpClientInfo client(String name) {
    return new McpClientInfo(name, "1", null, "2025-06-18", null);
  }

  @Test
  void 운영의_한도는_1만() {
    assertThat(McpClientRegistry.MAX_CLIENTS).isEqualTo(10_000);
  }

  @Test
  void 한도를_넘으면_가장_오래_안_쓴_것부터_버린다() {
    McpClientRegistry clients = new McpClientRegistry(3);
    UUID key = UUID.randomUUID();
    clients.remember(key, "a", client("A"));
    clients.remember(key, "b", client("B"));
    clients.remember(key, "c", client("C"));
    // a 를 쓰면 b 가 가장 오래 안 쓴 것이 된다
    assertThat(clients.recall(key, "a").name()).isEqualTo("A");
    clients.remember(key, "d", client("D"));
    assertThat(clients.size()).isEqualTo(3);
    assertThat(clients.recall(key, "b")).isEqualTo(McpClientInfo.NONE);
    assertThat(clients.recall(key, "a").name()).isEqualTo("A");
    assertThat(clients.recall(key, "c").name()).isEqualTo("C");
    assertThat(clients.recall(key, "d").name()).isEqualTo("D");
    for (int i = 0; i < 100; i++) {
      clients.remember(UUID.randomUUID(), "x" + i, client("X"));
    }
    assertThat(clients.size()).isEqualTo(3);
  }

  @Test
  void 같은_키_같은_User_Agent_는_나중_것이_남고_다른_키는_빌리지_못한다() {
    McpClientRegistry clients = new McpClientRegistry(10);
    UUID key = UUID.randomUUID();
    clients.remember(key, "agent/1", client("first"));
    clients.remember(key, "agent/1", client("second"));
    assertThat(clients.recall(key, "agent/1").name()).isEqualTo("second");
    assertThat(clients.recall(key, "agent/2")).isEqualTo(McpClientInfo.NONE);
    assertThat(clients.recall(UUID.randomUUID(), "agent/1")).isEqualTo(McpClientInfo.NONE);
    // User-Agent 가 없는 클라이언트도 키로 묶인다
    clients.remember(key, null, client("no-agent"));
    assertThat(clients.recall(key, null).name()).isEqualTo("no-agent");
    // 키를 모르면(거절된 요청) 두지도 찾지도 않는다
    clients.remember(null, "agent/1", client("anonymous"));
    assertThat(clients.recall(null, "agent/1")).isEqualTo(McpClientInfo.NONE);
    assertThat(clients.size()).isEqualTo(2);
  }

  @Test
  void 프로토콜_판은_요청의_머리글이_있으면_그것() {
    McpClientInfo c = client("A");
    assertThat(c.withProtocolVersion(null)).isSameAs(c);
    assertThat(c.withProtocolVersion("2025-11-25").protocolVersion()).isEqualTo("2025-11-25");
    assertThat(c.withProtocolVersion("2025-11-25").name()).isEqualTo("A");
    assertThat(McpClientInfo.NONE.withProtocolVersion("2025-06-18").name()).isNull();
  }
}
