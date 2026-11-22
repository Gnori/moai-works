package com.moai.works.api.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import com.moai.works.api.config.MoaiWorksProperties;

/** X-Forwarded-For 는 믿는 중계에서 온 것만 쓴다 — api 에 직접 붙은 클라이언트는 IP 를 꾸미지 못한다 (2026-11-22, MCP 설계 「중계」) */
class ClientIpTest {

  private final ClientIp clientIp = new ClientIp(new MoaiWorksProperties(null, null,
      new MoaiWorksProperties.Security("x", List.of("127.0.0.1", "::1")), null, null, null));

  private static MockHttpServletRequest from(String remote, String forwarded) {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRemoteAddr(remote);
    if (forwarded != null) {
      request.addHeader("X-Forwarded-For", forwarded);
    }
    return request;
  }

  @Test
  void 믿는_중계에서_온_요청은_중계가_덧붙인_값을_쓴다() {
    assertThat(clientIp.of(from("127.0.0.1", "203.0.113.7"))).isEqualTo("203.0.113.7");
    // 클라이언트가 앞에 꾸며 넣은 값은 버리고, 중계가 덧붙인 맨 오른쪽 값
    assertThat(clientIp.of(from("::1", "10.0.0.1, 203.0.113.7"))).isEqualTo("203.0.113.7");
  }

  @Test
  void 직접_붙은_요청의_X_Forwarded_For_는_버린다() {
    assertThat(clientIp.of(from("198.51.100.20", "1.2.3.4"))).isEqualTo("198.51.100.20");
    assertThat(clientIp.of(from("198.51.100.20", null))).isEqualTo("198.51.100.20");
    assertThat(clientIp.of(from("127.0.0.1", null))).isEqualTo("127.0.0.1");
  }
}
