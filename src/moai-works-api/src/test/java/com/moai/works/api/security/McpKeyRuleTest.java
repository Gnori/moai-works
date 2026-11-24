package com.moai.works.api.security;

import static com.jayway.jsonpath.JsonPath.read;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.moai.works.api.config.EndPointPaths;
import com.moai.works.api.mcpkey.McpKeyTokenCodec;
import com.moai.works.api.support.ApiTest;
import com.moai.works.api.support.McpTestClient;
import com.moai.works.api.support.TestApi;
import com.moai.works.api.support.TestTokens;
import com.moai.works.api.support.TestUser;

/**
 * MCP 키 발급 · 관리 규칙 (2026-11-22 사용자 결정, 2026-11-23 키는 사용자의 것 — ADR-029).
 *
 * <ul>
 *   <li>키는 사람 계정이 개인 설정에서 받는다(워크스페이스를 고르지 않는다) — 원문은 mai-wks- 로 시작하고 발급 응답에만 있다</li>
 *   <li>키 하나로 주인이 멤버이고 MCP 권한(mcp.key.issue.personal)이 있는 워크스페이스 모두에 닿는다. 그 밖의 워크스페이스는 보이지 않는다</li>
 *   <li>프로젝트 key 가 여러 워크스페이스에 있으면 PROJECT_AMBIGUOUS — workspace 를 넣으면 된다</li>
 *   <li>키는 주인만 만료시킨다. 워크스페이스 관리자(mcp.key.manage)는 멤버들의 키를 보기만 한다</li>
 *   <li>키는 지우지 않는다 — 발급 · 만료시키기는 사용자 단위 감사(워크스페이스 없음)로 남는다</li>
 * </ul>
 */
@ApiTest
class McpKeyRuleTest {

  private static final String MINE = "/me/mcp-keys";
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

  private ResultActions issue(TestUser as, Object body) throws Exception {
    return mvc.perform(post(EndPointPaths.V1 + MINE)
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + api.tokens().of(as))
        .header(HttpHeaders.USER_AGENT, BROWSER)
        .contentType(MediaType.APPLICATION_JSON)
        .content(body instanceof String s ? s : new tools.jackson.databind.json.JsonMapper().writeValueAsString(body)));
  }

  private String key(TestUser as, String name) throws Exception {
    return TestApi.read(issue(as, Map.of("name", name)).andExpect(status().isCreated()), "$.key");
  }

  private ResultActions mcp(String token) throws Exception {
    return mvc.perform(post("/mcp/issue").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
        .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
        .contentType(MediaType.APPLICATION_JSON).content(INITIALIZE));
  }

  private static Map<String, Object> args(Object... kv) {
    Map<String, Object> m = new HashMap<>();
    for (int i = 0; i < kv.length; i += 2) {
      m.put((String) kv[i], kv[i + 1]);
    }
    return m;
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

  private void project(TestUser by, String ws, String key, String name) throws Exception {
    api.post(by, "/workspaces/{ws}/projects", Map.of("key", key, "name", name, "visibility", "WORKSPACE"), ws)
        .andExpect(status().isCreated());
  }

  // ── 발급 · 목록 ───────────────────────────────────────────────────

  @Test
  void 키는_개인_설정에서_받고_원문은_한_번만_mai_wks_로_보인다() throws Exception {
    Space s = space();
    ResultActions issued = issue(s.member(), Map.of("name", "노트북 Claude Code", "days", 30))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.view.status").value("ACTIVE"))
        .andExpect(jsonPath("$.view.owner.id").value(s.member().id().toString()))
        .andExpect(jsonPath("$.view.issuedUserAgent").value(BROWSER))
        .andExpect(jsonPath("$.view.mine").value(true))
        .andExpect(jsonPath("$.view.workspace").doesNotExist());
    String token = TestApi.read(issued, "$.key");
    assertThat(token).startsWith("mai-wks-").hasSize("mai-wks-".length() + 43);

    // 내 키 목록에는 원문이 없고 앞부분만, 워크스페이스 칸도 없다
    api.get(s.member(), MINE).andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].keyPrefix").value(token.substring(0, "mai-wks-".length() + 4)))
        .andExpect(jsonPath("$[0].key").doesNotExist())
        .andExpect(jsonPath("$[0].workspace").doesNotExist());
    // 키로 MCP 에 붙는다
    mcp(token).andExpect(status().isOk());
    // 다른 사람에게는 보이지 않는다
    api.get(s.other(), MINE).andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void 관리자는_멤버들의_키를_보기만_하고_멤버는_워크스페이스_키_목록을_못_본다() throws Exception {
    Space s = space();
    key(s.member(), "멤버 키");
    key(s.other(), "다른 멤버 키");
    key(s.admin(), "관리자 키");
    TestUser stranger = api.user("stranger");
    key(stranger, "남의 키");

    // 워크스페이스 키 목록은 mcp.key.manage 만 — 내 키는 개인 설정(/me/mcp-keys)에서 본다
    api.get(s.member(), KEYS, s.ws()).andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("PERMISSION_REQUIRED"));

    // 이 워크스페이스 멤버의 키만(멤버가 아닌 사람의 키는 없다). 발급한 곳은 주인에게만 보인다
    api.get(s.admin(), KEYS, s.ws()).andExpect(jsonPath("$.length()").value(3))
        .andExpect(jsonPath("$[?(@.name == '남의 키')]").isEmpty())
        .andExpect(jsonPath("$[?(@.name == '관리자 키')].issuedUserAgent").value(BROWSER));
    List<Object> othersIp = TestApi.read(api.get(s.admin(), KEYS, s.ws()), "$[?(@.name == '다른 멤버 키')].issuedIp");
    assertThat(othersIp).containsExactly((Object) null);
    api.get(s.owner(), KEYS + "?owner={o}", s.ws(), s.other().id()).andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].owner.id").value(s.other().id().toString()))
        .andExpect(jsonPath("$[0].mine").value(false));
    // 멤버가 아닌 주인으로 거르면 빈 목록
    api.get(s.owner(), KEYS + "?owner={o}", s.ws(), stranger.id()).andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void 키는_지우지_않고_주인만_만료시킨다() throws Exception {
    Space s = space();
    String token = key(s.member(), "멤버 키");
    String id = TestApi.read(api.get(s.member(), MINE), "$[0].id");

    // 관리자도 남의 키는 만료시킬 수 없다 — 사용자 키는 주인의 다른 워크스페이스에도 닿는다(없는 것과 같은 404)
    api.post(s.admin(), MINE + "/{id}/expire", Map.of("note", "노트북 분실"), id).andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("MCP_KEY_NOT_FOUND"));
    mcp(token).andExpect(status().isOk());
    // 주인은 이유와 함께 만료시킨다
    api.post(s.member(), MINE + "/{id}/expire", Map.of("note", "노트북 분실"), id).andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("EXPIRED"))
        .andExpect(jsonPath("$.expiredBy.id").value(s.member().id().toString()))
        .andExpect(jsonPath("$.expireReason").value("MANUAL"))
        .andExpect(jsonPath("$.expireNote").value("노트북 분실"));
    // 다음 요청부터 막힌다
    mcp(token).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("MCP_KEY_EXPIRED"));
    // 다시 만료시킬 수 없다
    api.post(s.member(), MINE + "/{id}/expire", Map.of(), id).andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("MCP_KEY_ALREADY_EXPIRED"));
    // 지운 것이 아니다 — 만료된 키도 목록(발급 이력)에 남고, 상태로 거른다
    api.get(s.member(), MINE + "?status=EXPIRED").andExpect(jsonPath("$.length()").value(1));
    api.get(s.member(), MINE + "?status=ACTIVE").andExpect(jsonPath("$.length()").value(0));
    api.get(s.owner(), KEYS + "?status=EXPIRED", s.ws()).andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].expireReason").value("MANUAL"));
    // 이유 없이도 만료시킨다
    key(s.member(), "두 번째");
    String second = TestApi.read(api.get(s.member(), MINE + "?status=ACTIVE"), "$[0].id");
    api.post(s.member(), MINE + "/{id}/expire", null, second).andExpect(status().isOk());
  }

  @Test
  void 발급과_만료는_사용자_단위_감사로_남고_워크스페이스_감사는_그대로_돈다() throws Exception {
    Space s = space();
    key(s.member(), "멤버 키");
    String id = TestApi.read(api.get(s.member(), MINE), "$[0].id");
    api.post(s.member(), MINE + "/{id}/expire", Map.of("note", "점검"), id).andExpect(status().isOk());

    List<Map<String, Object>> rows = jdbc.queryForList(
        "SELECT action, workspace_id, actor_id, details::text AS details FROM audit_log WHERE target_id = ? ORDER BY occurred_at", id);
    assertThat(rows).extracting(r -> r.get("action")).containsExactly("mcp.key.issue", "mcp.key.expire");
    assertThat(rows).allSatisfy(r -> {
      assertThat(r.get("workspace_id")).isNull();
      assertThat(r.get("actor_id")).isEqualTo(s.member().id());
    });
    assertThat((String) rows.get(0).get("details")).contains(BROWSER);
    // 워크스페이스 감사 화면은 워크스페이스 없는 기록 때문에 깨지지 않는다 — 키 기록은 거기에 없다
    api.get(s.owner(), "/workspaces/{ws}/audit?limit=50", s.ws()).andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.action =~ /mcp\\.key.*/)]").isEmpty());
    api.get(s.owner(), "/workspaces/{ws}/audit?person={p}&action=mcp.key", s.ws(), s.member().id())
        .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void 키는_DB_에서도_지우거나_발급_정보를_바꿀_수_없다() throws Exception {
    Space s = space();
    key(s.member(), "멤버 키");
    String id = TestApi.read(api.get(s.member(), MINE), "$[0].id");
    assertThatThrownBy(() -> jdbc.update("DELETE FROM mcp_api_key WHERE id = ?::uuid", id))
        .hasMessageContaining("지울 수 없다");
    assertThatThrownBy(() -> jdbc.update("UPDATE mcp_api_key SET owner_user_id = ?::uuid WHERE id = ?::uuid",
        s.other().id().toString(), id)).hasMessageContaining("바꿀 수 없다");
    assertThatThrownBy(() -> jdbc.update(
        "UPDATE mcp_api_key SET expires_at = expires_at + interval '1 day' WHERE id = ?::uuid", id))
        .hasMessageContaining("늘릴 수 없다");
  }

  @Test
  void 이름과_기간을_검사한다() throws Exception {
    Space s = space();
    issue(s.member(), Map.of("name", " ")).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_KEY_NAME"));
    issue(s.member(), Map.of("name", "키", "days", 366)).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_KEY_DAYS"));
    issue(s.member(), Map.of("name", "키", "days", 0)).andExpect(status().isBadRequest());
  }

  @Test
  void 서비스_계정은_키의_주인이_될_수_없다() throws Exception {
    for (String client : new String[] {TestTokens.WEB_CLIENT, "moai-works-automation"}) {
      api.callWithToken(api.tokens().ofService(client), HttpMethod.POST, MINE, Map.of("name", "서비스 키"))
          .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("USER_ONLY"));
    }
  }

  @Test
  void MCP_로_일할_수_있는_워크스페이스가_하나도_없으면_받을_수_없다() throws Exception {
    TestUser lonely = api.user("lonely");
    // 개인 워크스페이스를 쓸 수 없게 되면 MCP 권한이 있는 워크스페이스가 없다
    jdbc.update("UPDATE workspace SET status = 'SUSPENDED' WHERE slug = ?", api.personalWorkspace(lonely));
    issue(lonely, Map.of("name", "못 받음")).andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("MCP_KEY_ISSUE_NOT_ALLOWED"));
    api.get(lonely, MINE).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
  }

  // ── 키 하나 · 여러 워크스페이스 ──────────────────────────────────────

  @Test
  void 키_하나로_두_워크스페이스에_닿는다() throws Exception {
    Space a = space();
    TestUser bOwner = api.user("b-owner");
    String b = api.teamWorkspace(bOwner, "두 번째 팀");
    api.join(b, bOwner, a.member(), "curator");
    project(a.owner(), a.ws(), "alpha", "A 의 프로젝트");
    project(bOwner, b, "beta", "B 의 프로젝트");
    McpTestClient mcp = new McpTestClient(mvc, key(a.member(), "모든 곳"));

    // 쓸 수 있는 워크스페이스 — 개인 · A(멤버) · B(큐레이터), 역할 이름과 함께
    String spaces = mcp.call("list_workspaces", Map.of());
    List<String> slugs = read(spaces, "$.workspaces[*].slug");
    assertThat(slugs).containsExactlyInAnyOrder(api.personalWorkspace(a.member()), a.ws(), b);
    assertThat((List<List<String>>) read(spaces, "$.workspaces[?(@.slug == '" + b + "')].roles")).containsExactly(List.of("큐레이터"));
    assertThat((List<List<String>>) read(spaces, "$.workspaces[?(@.slug == '" + a.ws() + "')].roles")).containsExactly(List.of("멤버"));

    // 프로젝트 목록은 두 워크스페이스에 걸치고, 줄마다 워크스페이스가 붙는다
    String projects = mcp.call("list_projects", Map.of());
    assertThat((List<String>) read(projects, "$.projects[*].key")).contains("alpha", "beta");
    assertThat((List<String>) read(projects, "$.projects[?(@.key == 'beta')].workspace.slug")).containsExactly(b);
    assertThat((List<String>) read(projects, "$.projects[?(@.key == 'alpha')].workspace.name")).containsExactly("키 시험");
    // workspace 로 거르면 그곳만
    assertThat((List<String>) read(mcp.call("list_projects", args("workspace", b)), "$.projects[*].key")).containsExactly("beta");

    // 같은 키로 B 에서는 일감을 만든다(큐레이터), A 에서는 멤버라 못 만든다 — 워크스페이스마다 그곳의 권한
    int betaIssue = read(mcp.call("create_issue", args("project", "beta", "type", "feature", "title", "B 에서")), "$.number");
    assertThat(betaIssue).isPositive();
    assertThat((String) read(mcp.call("create_issue", args("project", "alpha", "type", "feature", "title", "A 에서")),
        "$.error.code")).isEqualTo("PERMISSION_REQUIRED");
    assertThat((Integer) read(mcp.call("search_issues", args("project", "alpha")), "$.total")).isZero();
    // 일감 기록은 그 워크스페이스에 남는다(경로 MCP)
    api.get(bOwner, "/workspaces/{ws}/issues/{n}", b, betaIssue).andExpect(status().isOk())
        .andExpect(jsonPath("$.journal[0].via").value("MCP"));
  }

  @Test
  void 같은_프로젝트_key_가_두_워크스페이스에_있으면_PROJECT_AMBIGUOUS_이고_workspace_를_넣으면_된다() throws Exception {
    Space a = space();
    TestUser bOwner = api.user("b-owner");
    String b = api.teamWorkspace(bOwner, "같은 이름 팀");
    api.join(b, bOwner, a.member(), "curator");
    project(a.owner(), a.ws(), "shared", "A 의 shared");
    project(bOwner, b, "shared", "B 의 shared");
    McpTestClient mcp = new McpTestClient(mvc, key(a.member(), "겹침"));

    String ambiguous = mcp.call("search_issues", args("project", "shared"));
    assertThat((String) read(ambiguous, "$.error.code")).isEqualTo("PROJECT_AMBIGUOUS");
    assertThat((String) read(ambiguous, "$.error.message")).contains(a.ws(), b);
    assertThat((String) read(mcp.call("create_issue", args("project", "shared", "type", "bug", "title", "어디?")),
        "$.error.code")).isEqualTo("PROJECT_AMBIGUOUS");

    // workspace 를 넣으면 그곳에서
    String created = mcp.call("create_issue", args("project", "shared", "workspace", b, "type", "bug", "title", "B 에"));
    int n = read(created, "$.number");
    assertThat((String) read(mcp.call("get_issue", args("project", "shared", "workspace", b, "number", n)), "$.title"))
        .isEqualTo("B 에");
    assertThat((Integer) read(mcp.call("search_issues", args("project", "shared", "workspace", a.ws())), "$.total")).isZero();
    // 워크스페이스 기본 템플릿처럼 프로젝트 없이 하는 일도 워크스페이스가 여럿이면 고르라고 한다
    McpTestClient manage = new McpTestClient(mvc, key(a.member(), "관리 입구"), McpTestClient.PROJECT);
    assertThat((String) read(manage.call("list_issue_templates", Map.of()), "$.error.code")).isEqualTo("WORKSPACE_REQUIRED");
    assertThat((String) read(manage.call("list_issue_templates", args("workspace", b)), "$.workspace")).isEqualTo(b);
  }

  @Test
  void 멤버가_아니거나_MCP_권한이_없는_워크스페이스는_키에게_보이지_않는다() throws Exception {
    Space s = space();
    project(s.owner(), s.ws(), "gated", "막을 프로젝트");
    TestUser stranger = api.user("stranger");
    String strangerWs = api.teamWorkspace(stranger, "남의 팀");
    McpTestClient mcp = new McpTestClient(mvc, key(s.member(), "막힐 키"));
    assertThat((List<String>) read(mcp.call("list_workspaces", Map.of()), "$.workspaces[*].slug")).contains(s.ws());

    // 멤버가 아닌 워크스페이스 · 없는 워크스페이스는 같은 404
    for (String ws : new String[] {strangerWs, "workspace-zzzzzzzzzzzz"}) {
      assertThat((String) read(mcp.call("list_projects", args("workspace", ws)), "$.error.code")).isEqualTo("WORKSPACE_NOT_FOUND");
    }

    // 관리자가 역할에서 MCP 권한을 빼면(읽기 · 댓글만 남긴 역할) 다음 호출부터 보이지 않는다
    String noMcp = api.customRole(s.ws(), s.owner(), "MCP 없음",
        Set.of("workspace.read", "member.read", "issue.read", "issue.comment"));
    api.setRoles(s.ws(), s.owner(), s.member(), noMcp);
    assertThat((List<String>) read(mcp.call("list_workspaces", Map.of()), "$.workspaces[*].slug")).doesNotContain(s.ws());
    assertThat((List<String>) read(mcp.call("list_projects", Map.of()), "$.projects[*].key")).doesNotContain("gated");
    assertThat((String) read(mcp.call("search_issues", args("project", "gated")), "$.error.code")).isEqualTo("PROJECT_NOT_FOUND");
    assertThat((String) read(mcp.call("search_issues", args("project", "gated", "workspace", s.ws())), "$.error.code"))
        .isEqualTo("WORKSPACE_NOT_FOUND");
    // 키 자체는 그대로 — 개인 워크스페이스에는 계속 닿는다
    assertThat((List<String>) read(mcp.call("list_workspaces", Map.of()), "$.workspaces[*].slug"))
        .containsExactly(api.personalWorkspace(s.member()));

    // 권한을 돌려주면 다시 보인다
    api.setRoles(s.ws(), s.owner(), s.member(), "member");
    assertThat((Integer) read(mcp.call("search_issues", args("project", "gated")), "$.total")).isZero();

    // 내보내면 다음 호출부터 보이지 않고, 키는 만료되지 않는다(다른 워크스페이스에도 닿으므로)
    api.delete(s.owner(), "/workspaces/{ws}/members/{user}", s.ws(), s.member().id()).andExpect(status().is2xxSuccessful());
    assertThat((String) read(mcp.call("search_issues", args("project", "gated")), "$.error.code")).isEqualTo("PROJECT_NOT_FOUND");
    api.get(s.member(), MINE).andExpect(jsonPath("$[0].status").value("ACTIVE"));
  }

  @Test
  void 워크스페이스를_떠나도_키는_만료되지_않는다() throws Exception {
    Space s = space();
    String token = key(s.member(), "멤버 키");
    api.post(s.member(), "/workspaces/{ws}/leave", null, s.ws()).andExpect(status().is2xxSuccessful());

    mcp(token).andExpect(status().isOk());
    api.get(s.member(), MINE).andExpect(jsonPath("$[0].status").value("ACTIVE"))
        .andExpect(jsonPath("$[0].expireReason").doesNotExist());
    // 떠난 사람의 키는 그 워크스페이스 관리 화면에서도 빠진다
    api.get(s.owner(), KEYS + "?owner={o}", s.ws(), s.member().id()).andExpect(jsonPath("$.length()").value(0));
    List<String> slugs = read(new McpTestClient(mvc, token).call("list_workspaces", Map.of()), "$.workspaces[*].slug");
    assertThat(slugs).doesNotContain(s.ws());
  }

  @Test
  void 주인_계정을_쓸_수_없으면_키도_막힌다() throws Exception {
    Space s = space();
    String token = key(s.member(), "정지될 키");
    jdbc.update("UPDATE user_profile SET account_status = 'SUSPENDED' WHERE id = ?", s.member().id());
    mcp(token).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("MCP_PRINCIPAL_INACTIVE"));
    jdbc.update("UPDATE user_profile SET account_status = 'ACTIVE' WHERE id = ?", s.member().id());
    mcp(token).andExpect(status().isOk());
  }

  /** V13 전에 받은 키(행에 주인만 남는다)도 그대로 붙는다 — 워크스페이스 열을 지운 뒤의 행 모양으로 넣어 본다 */
  @Test
  void V13_전에_받은_키도_그대로_쓴다() throws Exception {
    Space s = space();
    project(s.owner(), s.ws(), "legacy", "옛 키 프로젝트");
    byte[] raw = new byte[32];
    new SecureRandom().nextBytes(raw);
    String token = McpKeyTokenCodec.PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
    jdbc.update("INSERT INTO mcp_api_key (id, name, key_prefix, key_hash, owner_user_id, owner_name, created_at, expires_at)"
        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?)", UUID.randomUUID(), "my-home-mac (옛 키)", token.substring(0, 12),
        McpKeyTokenCodec.hash(token), s.member().id(), "옛 주인", Timestamp.from(now.minus(10, ChronoUnit.DAYS)),
        Timestamp.from(now.plus(80, ChronoUnit.DAYS)));

    McpTestClient mcp = new McpTestClient(mvc, token);
    assertThat(mcp.tools()).hasSize(22).contains("list_workspaces");
    assertThat((List<String>) read(mcp.call("list_projects", Map.of()), "$.projects[*].key")).contains("legacy");
    assertThat((List<String>) read(mcp.call("list_workspaces", Map.of()), "$.workspaces[*].slug")).contains(s.ws());
    api.get(s.member(), MINE).andExpect(jsonPath("$[0].name").value("my-home-mac (옛 키)"))
        .andExpect(jsonPath("$[0].lastUsedAt").exists());
  }

  // ── 입구 ──────────────────────────────────────────────────────────

  /**
   * 입구는 기능으로 나눈다(일감 /mcp/issue · 프로젝트 관리 /mcp/project)와 키는 입구에 묶이지 않는다(2026-11-23 사용자: "다른 관리 기능과 이슈 관리 기능을 서로 구분" ·
   * "키는 권한만 있으면 어떤 mcp 엔드포인트든 연결 가능해야 해").
   */
  @Test
  void 키_하나로_일감_입구와_프로젝트_관리_입구에_모두_붙고_입구마다_도구가_다르다() throws Exception {
    Space s = space();
    String key = key(s.member(), "한 키");

    List<String> issueTools = new McpTestClient(mvc, key, McpTestClient.ISSUE).tools();
    assertThat(issueTools).hasSize(22).contains("list_workspaces", "list_projects", "search_issues", "create_issue",
        "delete_issue", "request_attachment_upload", "get_issue_template", "list_modules", "list_skills", "get_skill")
        .doesNotContain("set_module");

    var manage = new McpTestClient(mvc, key, McpTestClient.PROJECT);
    assertThat(manage.tools()).containsExactlyInAnyOrder("list_workspaces", "list_projects", "list_issue_templates",
        "set_issue_template", "reset_issue_template", "list_modules", "set_module");
    // 프로젝트 관리 입구에서도 범위는 같은 키 주인의 권한이다
    assertThat((List<String>) read(manage.call("list_workspaces", Map.of()), "$.workspaces[*].slug")).contains(s.ws());
  }

  @Test
  void list_projects_는_키_주인이_읽을_수_있는_열린_프로젝트만_준다() throws Exception {
    Space s = space();
    api.post(s.owner(), "/workspaces/{ws}/projects", Map.of("key", "open-one", "name", "열린 프로젝트", "visibility", "WORKSPACE"),
        s.ws()).andExpect(status().isCreated());
    api.post(s.owner(), "/workspaces/{ws}/projects", Map.of("key", "secret", "name", "지정 멤버", "visibility", "RESTRICTED"),
        s.ws()).andExpect(status().isCreated());

    List<String> memberKeys = read(new McpTestClient(mvc, key(s.member(), "멤버 키")).call("list_projects", Map.of()),
        "$.projects[*].key");
    assertThat(memberKeys).contains("open-one").doesNotContain("secret");
    List<String> ownerKeys = read(new McpTestClient(mvc, key(s.owner(), "소유자 키")).call("list_projects", Map.of()),
        "$.projects[*].key");
    assertThat(ownerKeys).contains("open-one", "secret");
  }

  @Test
  void 없는_MCP_입구는_404_와_입구_안내() throws Exception {
    Space s = space();
    String key = key(s.member(), "틀린 주소");
    // 키가 있어도 없어도 404 — 맞는 주소를 알려 준다
    mvc.perform(post("/mcp/manage").header(HttpHeaders.AUTHORIZATION, "Bearer " + key)
            .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
            .contentType(MediaType.APPLICATION_JSON).content(INITIALIZE))
        .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("MCP_ENDPOINT_NOT_FOUND"))
        .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("/mcp/issue")));
    mvc.perform(post("/mcp/nothing").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isNotFound());
  }

  @Test
  void 앞부분이_다른_키는_모양에서_거절한다() throws Exception {
    // 예전 앞부분(mwk_mcp_) · 다른 제품의 앞부분(moai-wiki 의 mai-wi-) · 길이가 틀린 것
    for (String token : new String[] {"mwk_mcp_" + "A".repeat(43), "mai-wi-" + "A".repeat(43),
        "mai-wks-" + "A".repeat(42), "mai-wks-" + "A".repeat(44)}) {
      for (String entry : new String[] {"/mcp/issue", "/mcp/project"}) {
        mvc.perform(post(entry).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("MCP_KEY_INVALID"));
      }
    }
    for (String entry : new String[] {"/mcp/issue", "/mcp/project"}) {
      mvc.perform(post(entry).contentType(MediaType.APPLICATION_JSON).content("{}"))
          .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("MCP_KEY_REQUIRED"))
          // MCP 클라이언트가 키를 달라는 뜻으로 읽는다 — web-backend 중계도 그대로 넘긴다(McpRelayTest, 일감 #136)
          .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"));
    }
  }
}
