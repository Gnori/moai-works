package com.moai.works.api.mcpkey;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** MCP 키 원문의 모양 — 앞부분 mai-wks- + 32바이트 난수(base64url 43자). 2026-11-22 사용자 결정 */
class McpKeyTokenCodecTest {

  @Test
  void 발급하는_키는_mai_wks_로_시작하고_모양이_맞다() {
    String key = McpKeyTokenCodec.generate();
    assertThat(key).startsWith("mai-wks-").hasSize("mai-wks-".length() + 43);
    assertThat(McpKeyTokenCodec.wellFormed(key)).isTrue();
    assertThat(McpKeyTokenCodec.displayPrefix(key)).startsWith("mai-wks-").hasSize(12);
    assertThat(McpKeyTokenCodec.generate()).isNotEqualTo(key);
  }

  @Test
  void 다른_앞부분이나_길이는_모양부터_틀리다() {
    String body = "A".repeat(43);
    assertThat(McpKeyTokenCodec.wellFormed("mwk_mcp_" + body)).isFalse();
    assertThat(McpKeyTokenCodec.wellFormed("mai-wki-" + body)).isFalse();
    assertThat(McpKeyTokenCodec.wellFormed("mai-wks-" + body.substring(1))).isFalse();
    assertThat(McpKeyTokenCodec.wellFormed(null)).isFalse();
  }
}
