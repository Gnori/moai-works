package com.moai.works.api.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.moai.works.api.config.EndPointPaths;
import com.moai.works.api.support.ApiTest;
import com.moai.works.api.support.TestApi;
import com.moai.works.api.support.TestTokens;
import com.moai.works.api.support.TestUser;

/**
 * MCP 키 발급 · 관리 규칙 (2026-11-22 사용자 결정).
 *
 * <ul>
 *   <li>키는 워크스페이스 안에서 사람 계정이 받는다 — 원문은 mai-wks- 로 시작하고 발급 응답에만 있다</li>
 *   <li>관리자(mcp.key.manage)는 그 워크스페이스의 모든 키를 만료된 것까지 보고 어떤 키든 만료시킨다. 멤버는 자기 키만</li>
 *   <li>키는 지우지 않는다 — 만료시킬 뿐이고, 발급 · 만료시키기는 IP · User-Agent 와 함께 감사에 남는다</li>
 *   <li>워크스페이스를 떠나면 그 사람의 키는 만료된다</li>
 * </ul>
 */
@ApiTest
class McpKeyRuleTest {

  private static final String KEYS = "/workspaces/{ws}/mcp-keys";
  private static final String BROWSER = "Mozilla/5.0 (Macintosh) 시험 브라우저";
  private static final String INITIALIZE = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{"
      + "\"protocolVersion\":\"2025-06-18\",\"capabilities\":{},\"clientInfo\":{\"name\":\"test\",\"version\":\"1\"}}}";

  @Autowired
  TestApi api;
  @Autowired
  MockMvc mvc;
  @Autowired
  JdbcTemplate jdbc;

  private ResultActions issue(TestUser as, String ws, Object body) throws Exception {
    return mvc.perform(post(EndPointPaths.V1 + "/workspaces/{ws}/mcp-keys", ws)
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + api.tokens().of(as))
        .header(HttpHeaders.USER_AGENT, BROWSER)
        .contentType(MediaType.APPLICATION_JSON)
        .content(body instanceof String s ? s : new tools.jackson.databind.json.JsonMapper().writeValueAsString(body)));
  }

  private ResultActions mcp(String token) throws Exception {
    return mvc.perform(post("/mcp/project").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
        .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
        .contentType(MediaType.APPLICATION_JSON).content(INITIALIZE));
  }

  private record Space(String ws, TestUser owner, TestUser admin, TestUser member, TestUser other) {
  }

  private Space space() throws Exception {
    TestUser owner = api.user("owner");
    String ws = api.teamWorkspace(owner, "키 시험");
    TestUser admin = api.user("admin");
    TestUser member = api.user("member");
    TestUser other = api.user("other");
    api.join(ws, owner, admin, "admin");
    api.join(ws, owner, member, "member");
    api.join(ws, owner, other, "member");
    return new Space(ws, owner, admin, member, other);
  }

  @Test
  void 멤버는_자기_키를_받고_원문은_한_번만_mai_wks_로_보인다() throws Exception {
    Space s = space();
    ResultActions issued = issue(s.member(), s.ws(), Map.of("name", "노트북 Claude Code", "days", 30))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.view.status").value("ACTIVE"))
        .andExpect(jsonPath("$.view.owner.id").value(s.member().id().toString()))
        .andExpect(jsonPath("$.view.issuedUserAgent").value(BROWSER))
        .andExpect(jsonPath("$.view.mine").value(true));
    String token = TestApi.read(issued, "$.key");
    assertThat(token).startsWith("mai-wks-").hasSize("mai-wks-".length() + 43);

    // 목록에는 원문이 없고 앞부분만
    api.get(s.member(), KEYS, s.ws()).andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].keyPrefix").value(token.substring(0, "mai-wks-".length() + 4)))
        .andExpect(jsonPath("$[0].key").doesNotExist());
    // 키로 MCP 에 붙는다
    mcp(token).andExpect(status().isOk());
    // 발급은 감사에 남는다 — 누구 · 앞부분 · User-Agent
    api.get(s.owner(), "/workspaces/{ws}/audit?limit=50", s.ws())
        .andExpect(jsonPath("$[?(@.action == 'mcp.key.issue')].actorId").value(s.member().id().toString()));
  }

  @Test
  void 관리자는_모든_키를_보고_멤버는_자기_키만_본다() throws Exception {
    Space s = space();
    issue(s.member(), s.ws(), Map.of("name", "멤버 키")).andExpect(status().isCreated());
    issue(s.other(), s.ws(), Map.of("name", "다른 멤버 키")).andExpect(status().isCreated());
    issue(s.admin(), s.ws(), Map.of("name", "관리자 키")).andExpect(status().isCreated());

    api.get(s.member(), KEYS, s.ws()).andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].name").value("멤버 키"));
    // 멤버가 남의 주인으로 걸러도 자기 키만
    api.get(s.member(), KEYS + "?owner={o}", s.ws(), s.other().id()).andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].name").value("멤버 키"));

    api.get(s.admin(), KEYS, s.ws()).andExpect(jsonPath("$.length()").value(3))
        .andExpect(jsonPath("$[?(@.name == '다른 멤버 키')].issuedIp").exists());
    api.get(s.owner(), KEYS + "?owner={o}", s.ws(), s.other().id()).andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].owner.id").value(s.other().id().toString()))
        .andExpect(jsonPath("$[0].mine").value(false));
  }

  @Test
  void 키는_지우지_않고_만료시킨다_관리자는_어떤_키든() throws Exception {
    Space s = space();
    String token = TestApi.read(issue(s.member(), s.ws(), Map.of("name", "멤버 키")), "$.key");
    String id = TestApi.read(api.get(s.member(), KEYS, s.ws()), "$[0].id");

    // 다른 멤버는 남의 키를 만료시킬 수 없다
    api.post(s.other(), KEYS + "/{id}/expire", Map.of(), s.ws(), id).andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("PERMISSION_REQUIRED"));
    // 관리자는 이유와 함께 만료시킨다
    api.post(s.admin(), KEYS + "/{id}/expire", Map.of("note", "노트북 분실"), s.ws(), id).andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("EXPIRED"))
        .andExpect(jsonPath("$.expiredBy.id").value(s.admin().id().toString()))
        .andExpect(jsonPath("$.expireReason").value("MANUAL"))
        .andExpect(jsonPath("$.expireNote").value("노트북 분실"));
    // 다음 요청부터 막힌다
    mcp(token).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("MCP_KEY_EXPIRED"));
    // 다시 만료시킬 수 없다
    api.post(s.member(), KEYS + "/{id}/expire", Map.of(), s.ws(), id).andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("MCP_KEY_ALREADY_EXPIRED"));
    // 지운 것이 아니다 — 만료된 키도 목록(발급 이력)에 남고, 상태로 거른다
    api.get(s.owner(), KEYS + "?status=EXPIRED", s.ws()).andExpect(jsonPath("$.length()").value(1));
    api.get(s.owner(), KEYS + "?status=ACTIVE", s.ws()).andExpect(jsonPath("$.length()").value(0));
    api.get(s.member(), KEYS, s.ws()).andExpect(jsonPath("$[0].status").value("EXPIRED"));
    List<String> actions = TestApi.read(api.get(s.owner(), "/workspaces/{ws}/audit?limit=50", s.ws()), "$[*].action");
    assertThat(actions).contains("mcp.key.issue", "mcp.key.expire");
  }

  @Test
  void 감사_기록에서_사람별로_키_기록을_걸러_본다() throws Exception {
    Space s = space();
    issue(s.member(), s.ws(), Map.of("name", "멤버 키")).andExpect(status().isCreated());
    issue(s.other(), s.ws(), Map.of("name", "다른 멤버 키")).andExpect(status().isCreated());
    String id = TestApi.read(api.get(s.member(), KEYS, s.ws()), "$[0].id");
    api.post(s.admin(), KEYS + "/{id}/expire", Map.of("note", "점검"), s.ws(), id).andExpect(status().isOk());

    // 그 사람 기준 — 자기가 받은 것 + 관리자가 그 사람 키를 만료시킨 것. 다른 멤버의 키는 없다
    String audit = "/workspaces/{ws}/audit?person={p}&action=mcp.key";
    List<String> actions = TestApi.read(api.get(s.owner(), audit, s.ws(), s.member().id()), "$[*].action");
    assertThat(actions).containsExactly("mcp.key.expire", "mcp.key.issue");
    api.get(s.owner(), audit, s.ws(), s.member().id())
        .andExpect(jsonPath("$[0].actorId").value(s.admin().id().toString()))
        .andExpect(jsonPath("$[0].ip").exists())
        .andExpect(jsonPath("$[1].details").value(org.hamcrest.Matchers.containsString(BROWSER)));
    // 묶음 앞부분은 점까지 맞아야 한다 — mcp.k 로는 걸리지 않는다
    api.get(s.owner(), "/workspaces/{ws}/audit?action=mcp.k", s.ws()).andExpect(jsonPath("$.length()").value(0));
    // 감사 권한이 없는 멤버는 감사 기록을 볼 수 없다(자기 키 목록만)
    api.get(s.member(), "/workspaces/{ws}/audit", s.ws()).andExpect(status().isForbidden());
  }

  @Test
  void 멤버는_자기_키를_만료시킨다() throws Exception {
    Space s = space();
    issue(s.member(), s.ws(), Map.of("name", "멤버 키")).andExpect(status().isCreated());
    String id = TestApi.read(api.get(s.member(), KEYS, s.ws()), "$[0].id");
    api.post(s.member(), KEYS + "/{id}/expire", null, s.ws(), id).andExpect(status().isOk())
        .andExpect(jsonPath("$.expiredBy.id").value(s.member().id().toString()));
  }

  @Test
  void 키는_DB_에서도_지우거나_발급_정보를_바꿀_수_없다() throws Exception {
    Space s = space();
    issue(s.member(), s.ws(), Map.of("name", "멤버 키")).andExpect(status().isCreated());
    String id = TestApi.read(api.get(s.member(), KEYS, s.ws()), "$[0].id");
    assertThatThrownBy(() -> jdbc.update("DELETE FROM mcp_api_key WHERE id = ?::uuid", id))
        .hasMessageContaining("지울 수 없다");
    assertThatThrownBy(() -> jdbc.update("UPDATE mcp_api_key SET owner_user_id = ?::uuid WHERE id = ?::uuid",
        s.other().id().toString(), id)).hasMessageContaining("바꿀 수 없다");
    assertThatThrownBy(() -> jdbc.update(
        "UPDATE mcp_api_key SET expires_at = expires_at + interval '1 day' WHERE id = ?::uuid", id))
        .hasMessageContaining("늘릴 수 없다");
  }

  @Test
  void 워크스페이스를_떠나면_키가_만료된다() throws Exception {
    Space s = space();
    String token = TestApi.read(issue(s.member(), s.ws(), Map.of("name", "멤버 키")), "$.key");
    api.post(s.member(), "/workspaces/{ws}/leave", null, s.ws()).andExpect(status().is2xxSuccessful());

    mcp(token).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("MCP_KEY_EXPIRED"));
    api.get(s.owner(), KEYS + "?owner={o}", s.ws(), s.member().id())
        .andExpect(jsonPath("$[0].status").value("EXPIRED"))
        .andExpect(jsonPath("$[0].expireReason").value("MEMBER_LEFT"));
  }

  @Test
  void 내_키는_모든_워크스페이스에서_모아_본다() throws Exception {
    Space s = space();
    String personal = api.personalWorkspace(s.member());
    issue(s.member(), s.ws(), Map.of("name", "팀 키")).andExpect(status().isCreated());
    issue(s.member(), personal, Map.of("name", "개인 키")).andExpect(status().isCreated());

    api.get(s.member(), "/me/mcp-keys").andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[?(@.name == '팀 키')].workspace").value(s.ws()))
        .andExpect(jsonPath("$[?(@.name == '개인 키')].workspace").value(personal));
    // 다른 사람에게는 보이지 않는다
    api.get(s.other(), "/me/mcp-keys").andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void 이름과_기간을_검사한다() throws Exception {
    Space s = space();
    issue(s.member(), s.ws(), Map.of("name", " ")).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_KEY_NAME"));
    issue(s.member(), s.ws(), Map.of("name", "키", "days", 366)).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_KEY_DAYS"));
    issue(s.member(), s.ws(), Map.of("name", "키", "days", 0)).andExpect(status().isBadRequest());
  }

  @Test
  void 서비스_계정은_키의_주인이_될_수_없다() throws Exception {
    Space s = space();
    for (String client : new String[] {TestTokens.WEB_CLIENT, "moai-works-automation"}) {
      api.callWithToken(api.tokens().ofService(client), HttpMethod.POST, KEYS, Map.of("name", "서비스 키"), s.ws())
          .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("USER_ONLY"));
    }
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
