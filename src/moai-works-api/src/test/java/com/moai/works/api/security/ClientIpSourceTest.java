package com.moai.works.api.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.moai.works.api.config.EndPointPaths;
import com.moai.works.api.support.ApiTest;
import com.moai.works.api.support.TestApi;
import com.moai.works.api.support.TestUser;

import tools.jackson.databind.json.JsonMapper;

/**
 * 클라이언트 IP 의 출처 (일감 #136) — REST 감사 기록 · 공개 경로 속도 제한의 IP 는 {@link ClientIp} 하나에서 온다.
 * 믿는 중계(web-backend, 시험에서는 기본값 루프백)가 붙인 X-Forwarded-For 의 맨 오른쪽 값만 쓰고, api 에 직접 붙은 요청이 보낸
 * X-Forwarded-For 는 버리고 접속 주소를 쓴다. MCP 접근 기록의 IP 는 {@code McpAccessLogTest} 가 본다.
 */
@ApiTest
class ClientIpSourceTest {

  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final AtomicInteger SEQ = new AtomicInteger();

  @Autowired
  TestApi api;

  @Autowired
  MockMvc mvc;

  @Autowired
  JdbcTemplate jdbc;

  private ResultActions rename(TestUser user, String name, String remote, String forwardedFor) throws Exception {
    return mvc.perform(patch(EndPointPaths.V1 + "/me").with(r -> {
      r.setRemoteAddr(remote);
      return r;
    }).headers(forwarded(forwardedFor)).header(HttpHeaders.AUTHORIZATION, "Bearer " + api.tokens().of(user))
        .contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(Map.of("displayName", name))));
  }

  private ResultActions resend(String remote, String forwardedFor) throws Exception {
    String email = "ip-source-" + SEQ.incrementAndGet() + "@test.moai-works.local";
    return mvc.perform(post(EndPointPaths.V1 + "/signup/resend").with(r -> {
      r.setRemoteAddr(remote);
      return r;
    }).headers(forwarded(forwardedFor)).contentType(MediaType.APPLICATION_JSON)
        .content(JSON.writeValueAsString(Map.of("email", email))));
  }

  private static HttpHeaders forwarded(String value) {
    HttpHeaders h = new HttpHeaders();
    if (value != null) {
      h.add("X-Forwarded-For", value);
    }
    return h;
  }

  private String auditIp(TestUser user, String name) {
    List<String> ips = jdbc.queryForList("select ip from audit_log where action = 'account.rename' and target_id = ?"
        + " and details->>'after' = ?", String.class, user.id().toString(), name);
    assertThat(ips).hasSize(1);
    return ips.get(0);
  }

  @Test
  void REST_감사의_IP_는_직접_붙은_요청이_꾸민_X_Forwarded_For_가_아니라_접속_주소다() throws Exception {
    TestUser user = api.user("ip-direct");
    rename(user, "직접 붙음", "198.51.100.40", "1.2.3.4").andExpect(status().isOk());
    rename(user, "직접 붙음 여러 값", "198.51.100.41", "1.2.3.4, 5.6.7.8").andExpect(status().isOk());

    assertThat(auditIp(user, "직접 붙음")).isEqualTo("198.51.100.40");
    assertThat(auditIp(user, "직접 붙음 여러 값")).isEqualTo("198.51.100.41");
  }

  @Test
  void REST_감사의_IP_는_믿는_중계가_붙인_X_Forwarded_For_의_맨_오른쪽_값이다() throws Exception {
    TestUser user = api.user("ip-relay");
    // 중계는 브라우저 값을 버리고 접속 주소 하나만 붙인다. 앞에 다른 값이 있어도 중계가 덧붙인 맨 오른쪽만 믿는다
    rename(user, "중계 거침", "127.0.0.1", "203.0.113.41").andExpect(status().isOk());
    rename(user, "중계 앞 꾸밈", "127.0.0.1", "1.2.3.4, 203.0.113.42").andExpect(status().isOk());
    rename(user, "중계 값 없음", "127.0.0.1", null).andExpect(status().isOk());

    assertThat(auditIp(user, "중계 거침")).isEqualTo("203.0.113.41");
    assertThat(auditIp(user, "중계 앞 꾸밈")).isEqualTo("203.0.113.42");
    assertThat(auditIp(user, "중계 값 없음")).isEqualTo("127.0.0.1");
  }

  @Test
  void 공개_경로_속도_제한은_꾸민_X_Forwarded_For_로_비켜_가지_못한다() throws Exception {
    // 가입 인증 코드 다시 보내기 — IP 당 시간에 10번
    for (int i = 0; i < 10; i++) {
      resend("198.51.100.136", "192.0.2." + i).andExpect(status().isAccepted());
    }
    resend("198.51.100.136", "192.0.2.200").andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    resend("198.51.100.136", null).andExpect(status().isTooManyRequests());
    // 다른 접속 주소는 따로 센다
    resend("198.51.100.137", "192.0.2.200").andExpect(status().isAccepted());
  }

  @Test
  void 믿는_중계를_거친_공개_경로는_중계가_붙인_클라이언트_IP_마다_센다() throws Exception {
    for (int i = 0; i < 10; i++) {
      resend("127.0.0.1", "203.0.113.136").andExpect(status().isAccepted());
    }
    resend("127.0.0.1", "203.0.113.136").andExpect(status().isTooManyRequests());
    // 같은 중계에서 온 다른 사람은 막히지 않는다
    resend("127.0.0.1", "203.0.113.137").andExpect(status().isAccepted());
  }
}
