package com.moai.works.api.security;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Date;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;

import com.moai.works.api.config.MoaiWorksProperties;
import com.moai.works.api.support.ApiTest;
import com.moai.works.api.support.TestApi;
import com.moai.works.api.support.TestTokens;
import com.moai.works.api.support.TestUser;

/** 누구인가 — REST 는 유효한 Keycloak 토큰(서명 · 만료 · 발급자 · audience)이 있어야 한다. (SECURITY.md §5.1) */
@ApiTest
class TokenAuthenticationTest {

  @Autowired
  TestApi api;

  @Autowired
  MoaiWorksProperties properties;

  @Test
  void 토큰이_없으면_401() throws Exception {
    api.get(null, "/me").andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }

  @Test
  void 유효한_토큰이면_통과한다() throws Exception {
    TestUser user = api.user("token-ok");
    api.get(user, "/me").andExpect(status().isOk()).andExpect(jsonPath("$.email").value(user.email()))
        .andExpect(jsonPath("$.status").value("ACTIVE"));
  }

  @Test
  void 만료된_토큰은_401() throws Exception {
    TestUser user = api.user("token-expired");
    String expired = api.tokens().of(user, c -> c.issueTime(Date.from(Instant.now().minusSeconds(900)))
        .expirationTime(Date.from(Instant.now().minusSeconds(600))));
    api.callWithToken(expired, HttpMethod.GET, "/me", null).andExpect(status().isUnauthorized());
  }

  @Test
  void audience_에_이_api_가_없으면_401() throws Exception {
    TestUser user = api.user("token-aud");
    String other = api.tokens().of(user, c -> c.audience("account"));
    api.callWithToken(other, HttpMethod.GET, "/me", null).andExpect(status().isUnauthorized());
  }

  @Test
  void 발급자가_다르면_401() throws Exception {
    TestUser user = api.user("token-iss");
    String other = api.tokens().of(user, c -> c.issuer("http://other.test.invalid/realms/moai-works"));
    api.callWithToken(other, HttpMethod.GET, "/me", null).andExpect(status().isUnauthorized());
  }

  @Test
  void 다른_키로_서명한_토큰은_401() throws Exception {
    TestUser user = api.user("token-key");
    String forged = new TestTokens(properties).of(user);
    api.callWithToken(forged, HttpMethod.GET, "/me", null).andExpect(status().isUnauthorized());
  }

  @Test
  void 가입_정책은_토큰_없이_본다() throws Exception {
    api.get(null, "/signup/policy").andExpect(status().isOk());
  }

  @Test
  void 서비스_계정은_사람용_api_를_못_쓴다() throws Exception {
    String service = api.tokens().ofService("moai-works-automation");
    api.callWithToken(service, HttpMethod.GET, "/me", null).andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("USER_ONLY"));
  }
}
