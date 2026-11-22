package com.moai.works.api.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.moai.works.api.config.EndPointPaths;
import com.moai.works.api.support.ApiTest;
import com.moai.works.api.support.TestApi;
import com.moai.works.api.support.TestTokens;
import com.moai.works.api.support.TestUser;

/**
 * MCP 키 규칙 (moai-wiki 의 McpKeyRuleTest 와 같은 시험) — 키는 사용자 계정에 묶이고(서비스 계정은 주인이 될 수 없다),
 * 원문은 mai-wks- 로 시작한다. 앞부분이 다른 키는 DB 를 보지 않고 모양에서 거절한다. 2026-11-22 사용자 결정.
 */
@ApiTest
class McpKeyRuleTest {

  private static final String KEYS = EndPointPaths.BASE_PATH + "/mcp-keys";

  @Autowired
  TestApi api;
  @Autowired
  MockMvc mvc;

  private ResultActions issue(String bearer) throws Exception {
    return mvc.perform(post(KEYS).header(HttpHeaders.AUTHORIZATION, "Bearer " + bearer)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"name\":\"시험 키\",\"endpoint\":\"PROJECT\"}"));
  }

  @Test
  void 키는_사용자_계정이_발급하고_원문은_mai_wks_로_시작한다() throws Exception {
    TestUser person = api.user("person");
    ResultActions issued = issue(api.tokens().of(person)).andExpect(status().isCreated());
    String token = TestApi.read(issued, "$.key");

    assertThat(token).startsWith("mai-wks-").hasSize("mai-wks-".length() + 43);
    // 목록에는 원문이 없고 앞부분만 보인다 — 주인은 발급한 계정이다
    mvc.perform(get(KEYS).header(HttpHeaders.AUTHORIZATION, "Bearer " + api.tokens().of(person)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].keyPrefix").value(token.substring(0, "mai-wks-".length() + 4)))
        .andExpect(jsonPath("$[0].key").doesNotExist());
  }

  @Test
  void 서비스_계정은_키의_주인이_될_수_없다() throws Exception {
    issue(api.tokens().ofService(TestTokens.WEB_CLIENT)).andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("USER_ONLY"));
    issue(api.tokens().ofService("moai-works-automation")).andExpect(status().isForbidden());
  }

  @Test
  void 앞부분이_다른_키는_모양에서_거절한다() throws Exception {
    // 예전 앞부분(mwk_mcp_) · 다른 제품의 앞부분(moai-wiki 의 mai-wi-) · 길이가 틀린 것
    for (String token : new String[] {"mwk_mcp_" + "A".repeat(43), "mai-wi-" + "A".repeat(43),
        "mai-wks-" + "A".repeat(42), "mai-wks-" + "A".repeat(44)}) {
      mvc.perform(post("/mcp/project").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
              .contentType(MediaType.APPLICATION_JSON).content("{}"))
          .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("MCP_KEY_INVALID"));
    }
    mvc.perform(post("/mcp/project").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("MCP_KEY_REQUIRED"));
  }
}
