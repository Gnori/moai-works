package com.moai.works.api.workspace;

import static com.jayway.jsonpath.JsonPath.read;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.moai.works.api.storage.ObjectStore;
import com.moai.works.api.support.ApiTest;
import com.moai.works.api.support.FakeKeycloak;
import com.moai.works.api.support.McpTestClient;
import com.moai.works.api.support.MemoryObjectStore;
import com.moai.works.api.support.TestApi;
import com.moai.works.api.support.TestUser;

/**
 * 시스템 관리자의 팀 워크스페이스 사용 중지 · 다시 사용 · 삭제 (FR-WSP-019 · FR-WSP-020, WBS 1.8).
 * 2026-11-22 사용자: "팀 워크스페이스는 시스템 어드민이 사용 못 하게 막을 수도 있어" · "다만 삭제는 사용 중지 후에만 가능해".
 *
 * <ul>
 *   <li>사용 중지하면 멤버의 REST 는 403 WORKSPACE_SUSPENDED(멤버가 아니면 404), MCP 키에게는 보이지 않는다. Keycloak 조직도 꺼진다</li>
 *   <li>다시 사용하게 하면 모든 것이 그대로 돌아온다</li>
 *   <li>삭제는 사용 중지한 팀 워크스페이스만(아니면 409 WORKSPACE_NOT_SUSPENDED), 확인 문구(주소)를 api 가 다시 본다</li>
 *   <li>삭제하면 Keycloak 조직 · 사본 · 첨부 파일이 지워지고, 행 · 이력 · 감사는 남는다</li>
 *   <li>모든 조치는 감사(admin.workspace.*)에 남고, 시스템 관리자가 아니면 403 이다</li>
 * </ul>
 */
@ApiTest
class WorkspaceSuspendTest {

  private static final String ADMIN = "/admin/workspaces";
  private static final String SUSPENDED = "WORKSPACE_SUSPENDED";
  private static final String P = "alpha";

  @Autowired
  TestApi api;
  @Autowired
  MockMvc mvc;
  @Autowired
  JdbcTemplate jdbc;
  @Autowired
  FakeKeycloak keycloak;
  @Autowired
  MemoryObjectStore store;

  private record Space(String ws, TestUser owner, TestUser member, TestUser outsider, TestUser sysadmin, int issue) {
  }

  /** 팀 워크스페이스(퍼블릭) · 멤버 · 프로젝트 하나 · 멤버에게 맡긴 일감 하나 */
  private Space space(String name) throws Exception {
    TestUser owner = api.user("owner");
    String ws = api.teamWorkspace(owner, name);
    TestUser member = api.user("member");
    api.join(ws, owner, member, "member");
    api.post(owner, "/workspaces/{ws}/projects", Map.of("key", P, "name", "알파", "visibility", "WORKSPACE"), ws)
        .andExpect(status().isCreated());
    int n = TestApi.read(api.post(owner, "/workspaces/{ws}/projects/{p}/issues",
        Map.of("type", "feature", "title", "사용 중지 시험 일감", "assigneeId", member.id().toString()), ws, P)
        .andExpect(status().isCreated()), "$.number");
    return new Space(ws, owner, member, api.user("outsider"), api.user("sysadmin", "system-admin"), n);
  }

  private String orgId(String ws) {
    return jdbc.queryForObject("SELECT keycloak_org_id FROM workspace WHERE slug = ?", String.class, ws);
  }

  private String wsId(String ws) {
    return jdbc.queryForObject("SELECT id::text FROM workspace WHERE slug = ?", String.class, ws);
  }

  private void suspend(Space s) throws Exception {
    api.post(s.sysadmin(), ADMIN + "/{slug}/suspend", Map.of("reason", "이용 약관 위반 조사"), s.ws())
        .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SUSPENDED"));
  }

  // ── 사용 중지 · 다시 사용 — REST ────────────────────────────────────

  @Test
  void 사용_중지하면_멤버의_REST_가_모두_막히고_다시_사용하면_그대로_돌아온다() throws Exception {
    Space s = space("멈출 팀");
    String issues = "/workspaces/{ws}/projects/{p}/issues";
    // 멤버에게 맡긴 일감의 알림 등 — 사용 중지 동안 숨겼다가 다시 사용하면 그대로 돌아와야 한다
    int unreadBefore = TestApi.read(api.get(s.member(), "/me/notifications"), "$.unread");
    assertThat(unreadBefore).isPositive();

    api.post(s.sysadmin(), ADMIN + "/{slug}/suspend", Map.of("reason", "  이용 약관 위반 조사  "), s.ws())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.slug").value(s.ws()))
        .andExpect(jsonPath("$.status").value("SUSPENDED"))
        .andExpect(jsonPath("$.suspendedReason").value("이용 약관 위반 조사"))
        .andExpect(jsonPath("$.suspendedAt").isNotEmpty());
    // Keycloak 먼저 — 조직이 꺼진다
    assertThat(keycloak.organizationEnabled(orgId(s.ws()))).isFalse();

    // 멤버(소유자 포함)는 무엇을 하든 403 WORKSPACE_SUSPENDED — 화면이 사용 중지 안내를 보인다
    for (TestUser who : List.of(s.owner(), s.member())) {
      api.get(who, "/workspaces/{ws}", s.ws()).andExpect(status().isForbidden())
          .andExpect(jsonPath("$.code").value(SUSPENDED));
      api.get(who, "/workspaces/{ws}/projects", s.ws()).andExpect(status().isForbidden())
          .andExpect(jsonPath("$.code").value(SUSPENDED));
      api.get(who, issues, s.ws(), P).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(SUSPENDED));
      api.get(who, "/workspaces/{ws}/issues/{n}", s.ws(), s.issue()).andExpect(status().isForbidden());
    }
    api.post(s.owner(), issues, Map.of("type", "feature", "title", "못 만듦"), s.ws(), P).andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value(SUSPENDED));
    api.patch(s.owner(), "/workspaces/{ws}", Map.of("name", "못 바꿈", "visibility", "PUBLIC", "joinPolicy", "APPROVAL",
        "allowedJoinDomains", List.of(), "defaultRoleKey", "member"), s.ws()).andExpect(status().isForbidden());
    api.get(s.owner(), "/workspaces/{ws}/members", s.ws()).andExpect(status().isForbidden());
    api.get(s.owner(), "/workspaces/{ws}/audit", s.ws()).andExpect(status().isForbidden());
    api.put(s.member(), "/me/last-workspace", Map.of("slug", s.ws())).andExpect(status().isNotFound());

    // 멤버가 아니면 있는지도 알리지 않는다 — 찾기에 없고, 가입 신청도 없는 워크스페이스와 같다
    api.get(s.outsider(), "/workspaces/{ws}", s.ws()).andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("WORKSPACE_NOT_FOUND"));
    api.get(s.outsider(), "/workspaces?query={q}", "멈출 팀").andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.slug == '" + s.ws() + "')]").isEmpty());
    api.post(s.outsider(), "/workspaces/{ws}/join-requests", Map.of("message", "들어가고 싶다"), s.ws())
        .andExpect(status().isNotFound());

    // 내 워크스페이스에는 "사용 중지" 로 남고(화면이 안내한다), 들어갈 곳으로는 고르지 않는다
    api.get(s.member(), "/me").andExpect(status().isOk())
        .andExpect(jsonPath("$.workspaces[?(@.slug == '" + s.ws() + "')].status").value("SUSPENDED"))
        .andExpect(jsonPath("$.landingWorkspace").value(api.personalWorkspace(s.member())));
    // 그 워크스페이스의 알림도 알림함에서 숨긴다(지우지 않는다)
    api.get(s.member(), "/me/notifications").andExpect(status().isOk())
        .andExpect(jsonPath("$.unread").value(0))
        .andExpect(jsonPath("$.items[?(@.workspaceSlug == '" + s.ws() + "')]").isEmpty());

    // 다시 사용 — Keycloak 조직을 켜고, 모든 것이 그대로다
    api.post(s.sysadmin(), ADMIN + "/{slug}/resume", null, s.ws()).andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACTIVE"))
        .andExpect(jsonPath("$.suspendedReason").isEmpty())
        .andExpect(jsonPath("$.suspendedAt").isEmpty());
    assertThat(keycloak.organizationEnabled(orgId(s.ws()))).isTrue();
    api.get(s.owner(), "/workspaces/{ws}", s.ws()).andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACTIVE"))
        .andExpect(jsonPath("$.memberCount").value(2))
        .andExpect(jsonPath("$.myRoles[0]").value("owner"));
    api.get(s.member(), "/workspaces/{ws}/issues/{n}", s.ws(), s.issue()).andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("사용 중지 시험 일감"));
    api.get(s.member(), "/me/notifications").andExpect(jsonPath("$.unread").value(unreadBefore))
        .andExpect(jsonPath("$.items[0].workspaceSlug").value(s.ws()));
    api.post(s.owner(), issues, Map.of("type", "feature", "title", "다시 만듦"), s.ws(), P)
        .andExpect(status().isCreated());
    api.get(s.outsider(), "/workspaces?query={q}", "멈출 팀")
        .andExpect(jsonPath("$[?(@.slug == '" + s.ws() + "')]").isNotEmpty());
  }

  // ── 사용 중지 — MCP ─────────────────────────────────────────────────

  @Test
  void 사용_중지한_워크스페이스는_MCP_키에게_보이지_않고_다시_사용하면_돌아온다() throws Exception {
    Space s = space("MCP 멈출 팀");
    String token = TestApi.read(api.post(s.member(), "/me/mcp-keys", Map.of("name", "에이전트 키"))
        .andExpect(status().isCreated()), "$.key");
    McpTestClient mcp = new McpTestClient(mvc, token);
    McpTestClient project = new McpTestClient(mvc, token, McpTestClient.PROJECT);
    String personal = api.personalWorkspace(s.member());

    assertThat((List<String>) read(project.call("list_workspaces", Map.of()), "$.workspaces[*].slug"))
        .containsExactlyInAnyOrder(personal, s.ws());
    assertThat((String) read(mcp.call("get_issue", Map.of("project", P, "number", s.issue())), "$.summary.title"))
        .isEqualTo("사용 중지 시험 일감");

    suspend(s);

    // 키 하나로 닿는 워크스페이스 목록에서 빠지고, 이름을 줘도 없는 것과 같다 — 같은 세션 · 같은 키여도 다음 호출부터
    assertThat((List<String>) read(project.call("list_workspaces", Map.of()), "$.workspaces[*].slug"))
        .containsExactly(personal);
    assertThat((List<String>) read(project.call("list_projects", Map.of()), "$.projects[*].key")).doesNotContain(P);
    assertThat((String) read(project.call("list_projects", Map.of("workspace", s.ws())), "$.error.code"))
        .isEqualTo("WORKSPACE_NOT_FOUND");
    assertThat((String) read(mcp.call("get_issue", Map.of("project", P, "workspace", s.ws(), "number", s.issue())),
        "$.error.code")).isEqualTo("WORKSPACE_NOT_FOUND");
    assertThat((String) read(mcp.call("get_issue", Map.of("project", P, "number", s.issue())), "$.error.code"))
        .isEqualTo("PROJECT_NOT_FOUND");
    assertThat((String) read(mcp.call("create_issue", Map.of("project", P, "type", "feature", "title", "못 만듦")),
        "$.error.code")).isEqualTo("PROJECT_NOT_FOUND");

    api.post(s.sysadmin(), ADMIN + "/{slug}/resume", null, s.ws()).andExpect(status().isOk());

    assertThat((List<String>) read(project.call("list_workspaces", Map.of()), "$.workspaces[*].slug"))
        .containsExactlyInAnyOrder(personal, s.ws());
    assertThat((String) read(mcp.call("get_issue", Map.of("project", P, "workspace", s.ws(), "number", s.issue())),
        "$.summary.title")).isEqualTo("사용 중지 시험 일감");
  }

  // ── 규칙 ───────────────────────────────────────────────────────────

  @Test
  void 사용_중지하지_않은_워크스페이스는_지울_수_없고_확인_문구가_틀리면_거절한다() throws Exception {
    Space s = space("지우기 규칙 팀");

    // 사용 중인 워크스페이스 — 확인 문구가 맞아도 409
    api.delete(s.sysadmin(), ADMIN + "/{slug}?confirm={c}", s.ws(), s.ws()).andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("WORKSPACE_NOT_SUSPENDED"));
    api.delete(s.sysadmin(), ADMIN + "/{slug}", s.ws()).andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("WORKSPACE_NOT_SUSPENDED"));
    assertThat(keycloak.organizationExists(orgId(s.ws()))).isTrue();

    suspend(s);
    // 확인 문구는 api 가 다시 본다 — 없거나 틀리면 400, 아무것도 지우지 않는다
    api.delete(s.sysadmin(), ADMIN + "/{slug}", s.ws()).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("CONFIRM_MISMATCH"));
    api.delete(s.sysadmin(), ADMIN + "/{slug}?confirm={c}", s.ws(), "workspace-wrong").andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("CONFIRM_MISMATCH"));
    assertThat(keycloak.organizationExists(orgId(s.ws()))).isTrue();
    api.get(s.sysadmin(), ADMIN + "?status=SUSPENDED&query={q}", s.ws()).andExpect(jsonPath("$.length()").value(1));

    // 상태가 맞지 않는 조치 — 사용 중지한 것을 또 사용 중지 · 사용 중인 것을 다시 사용
    api.post(s.sysadmin(), ADMIN + "/{slug}/suspend", Map.of("reason", "또"), s.ws()).andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("WORKSPACE_NOT_ACTIVE"));
    api.post(s.sysadmin(), ADMIN + "/{slug}/resume", null, s.ws()).andExpect(status().isOk());
    api.post(s.sysadmin(), ADMIN + "/{slug}/resume", null, s.ws()).andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("WORKSPACE_NOT_SUSPENDED"));
    // 이유는 반드시 적는다
    api.post(s.sysadmin(), ADMIN + "/{slug}/suspend", Map.of("reason", " "), s.ws()).andExpect(status().isBadRequest());
    api.post(s.sysadmin(), ADMIN + "/{slug}/suspend", Map.of("reason", "x".repeat(501)), s.ws())
        .andExpect(status().isBadRequest());
    // 없는 주소
    api.post(s.sysadmin(), ADMIN + "/{slug}/suspend", Map.of("reason", "없음"), "workspace-nonexistent")
        .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("WORKSPACE_NOT_FOUND"));
  }

  @Test
  void 개인_워크스페이스는_사용_중지하거나_지울_수_없다() throws Exception {
    TestUser person = api.user("person");
    TestUser sysadmin = api.user("sysadmin", "system-admin");
    String personal = api.personalWorkspace(person);

    api.post(sysadmin, ADMIN + "/{slug}/suspend", Map.of("reason", "개인"), personal).andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("PERSONAL_WORKSPACE"));
    api.delete(sysadmin, ADMIN + "/{slug}?confirm={c}", personal, personal).andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("PERSONAL_WORKSPACE"));
    api.get(sysadmin, ADMIN + "/{slug}/deletion-preview", personal).andExpect(status().isConflict());
    // 목록에는 종류와 함께 보인다(계정을 정지해 막는다)
    api.get(sysadmin, ADMIN + "?kind=PERSONAL&query={q}", personal).andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].kind").value("PERSONAL"))
        .andExpect(jsonPath("$[0].owners[0].id").value(person.id().toString()));
    api.get(person, "/workspaces/{ws}", personal).andExpect(status().isOk());
  }

  @Test
  void 목록은_이름_주소_소유자_멤버_수_상태까지이고_내용은_없다() throws Exception {
    Space s = space("목록 팀 " + System.nanoTime());

    String list = TestApi.read(api.get(s.sysadmin(), ADMIN + "?kind=TEAM&query={q}", s.ws()).andExpect(status().isOk()), "$")
        .toString();
    api.get(s.sysadmin(), ADMIN + "?kind=TEAM&query={q}", s.ws()).andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].slug").value(s.ws()))
        .andExpect(jsonPath("$[0].name").value(org.hamcrest.Matchers.startsWith("목록 팀")))
        .andExpect(jsonPath("$[0].kind").value("TEAM"))
        .andExpect(jsonPath("$[0].visibility").value("PUBLIC"))
        .andExpect(jsonPath("$[0].status").value("ACTIVE"))
        .andExpect(jsonPath("$[0].memberCount").value(2))
        .andExpect(jsonPath("$[0].owners.length()").value(1))
        .andExpect(jsonPath("$[0].owners[0].id").value(s.owner().id().toString()))
        .andExpect(jsonPath("$[0].owners[0].displayName").isNotEmpty())
        .andExpect(jsonPath("$[0].createdAt").isNotEmpty())
        // 내용 · 설정은 넣지 않는다
        .andExpect(jsonPath("$[0].description").doesNotExist())
        .andExpect(jsonPath("$[0].projects").doesNotExist())
        .andExpect(jsonPath("$[0].allowedJoinDomains").doesNotExist());
    assertThat(list).doesNotContain("사용 중지 시험 일감").doesNotContain("알파");
    // 이름으로도 찾는다 · 상태로 거른다
    api.get(s.sysadmin(), ADMIN + "?query={q}", "목록 팀").andExpect(jsonPath("$[?(@.slug == '" + s.ws() + "')]").isNotEmpty());
    api.get(s.sysadmin(), ADMIN + "?status=SUSPENDED&query={q}", s.ws()).andExpect(jsonPath("$.length()").value(0));

    // 삭제 미리보기도 수만 — 멤버 · 프로젝트(기본 프로젝트 없음) · 일감 · 첨부
    api.get(s.sysadmin(), ADMIN + "/{slug}/deletion-preview", s.ws()).andExpect(status().isOk())
        .andExpect(jsonPath("$.slug").value(s.ws()))
        .andExpect(jsonPath("$.members").value(2))
        .andExpect(jsonPath("$.projects").value(1))
        .andExpect(jsonPath("$.issues").value(1))
        .andExpect(jsonPath("$.attachments").value(0));
  }

  @Test
  void 시스템_관리자가_아니면_403_이다() throws Exception {
    Space s = space("남의 팀");
    TestUser officer = api.user("officer", "security-officer");
    for (TestUser who : List.of(s.owner(), s.member(), officer)) {
      api.get(who, ADMIN).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PLATFORM_ROLE_REQUIRED"));
      api.post(who, ADMIN + "/{slug}/suspend", Map.of("reason", "소유자도 못 함"), s.ws()).andExpect(status().isForbidden())
          .andExpect(jsonPath("$.code").value("PLATFORM_ROLE_REQUIRED"));
      api.post(who, ADMIN + "/{slug}/resume", null, s.ws()).andExpect(status().isForbidden());
      api.get(who, ADMIN + "/{slug}/deletion-preview", s.ws()).andExpect(status().isForbidden());
      api.delete(who, ADMIN + "/{slug}?confirm={c}", s.ws(), s.ws()).andExpect(status().isForbidden());
    }
    api.call(null, org.springframework.http.HttpMethod.GET, ADMIN, null).andExpect(status().isUnauthorized());
    // 아무것도 바뀌지 않았다
    api.get(s.owner(), "/workspaces/{ws}", s.ws()).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));
    // 시스템 관리자도 그 워크스페이스의 내용에는 여전히 들어가지 못한다
    api.get(s.sysadmin(), "/workspaces/{ws}", s.ws()).andExpect(status().isNotFound());
  }

  // ── 삭제 ───────────────────────────────────────────────────────────

  @Test
  void 삭제하면_Keycloak_조직_사본_첨부_파일이_지워지고_행과_이력과_감사는_남는다() throws Exception {
    Space s = space("지울 팀");
    String id = wsId(s.ws());
    String org = orgId(s.ws());
    api.upload(s.member(), "/workspaces/{ws}/issues/{n}/attachments", "spec.txt", "text/plain",
        "명세".getBytes(StandardCharsets.UTF_8), Map.of(), s.ws(), s.issue()).andExpect(status().isCreated());
    api.post(s.owner(), "/workspaces/{ws}/invitations", Map.of("email", "later@example.com", "roleKey", "member"), s.ws())
        .andExpect(status().isCreated());
    assertThat(store.keys(ObjectStore.Zone.ATTACHMENT, "ws/" + id + "/")).hasSize(1);
    String token = TestApi.read(api.post(s.member(), "/me/mcp-keys", Map.of("name", "에이전트 키"))
        .andExpect(status().isCreated()), "$.key");

    api.post(s.sysadmin(), ADMIN + "/{slug}/suspend", Map.of("reason", "운영 종료"), s.ws()).andExpect(status().isOk());
    api.get(s.sysadmin(), ADMIN + "/{slug}/deletion-preview", s.ws()).andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUSPENDED"))
        .andExpect(jsonPath("$.members").value(2))
        .andExpect(jsonPath("$.issues").value(1))
        .andExpect(jsonPath("$.attachments").value(1))
        .andExpect(jsonPath("$.attachmentBytes").value("명세".getBytes(StandardCharsets.UTF_8).length));

    api.delete(s.sysadmin(), ADMIN + "/{slug}?confirm={c}", s.ws(), s.ws()).andExpect(status().isNoContent());

    // Keycloak 조직(멤버십 · 역할 그룹 · 프로젝트 역할 그룹)과 첨부 파일이 사라진다
    assertThat(keycloak.organizationExists(org)).isFalse();
    assertThat(store.keys(ObjectStore.Zone.ATTACHMENT, "ws/" + id + "/")).isEmpty();
    // 사본 · 설정 행은 지운다
    for (String table : List.of("workspace_member", "member_role", "workspace_role", "invitation", "join_request",
        "issue_template", "notification")) {
      assertThat(jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE workspace_id = ?::uuid", Long.class, id))
          .as(table).isZero();
    }
    assertThat(jdbc.queryForObject("SELECT count(*) FROM project_role_binding b JOIN project p ON p.id = b.project_id"
        + " WHERE p.workspace_id = ?::uuid", Long.class, id)).isZero();
    // 행 · 이력은 남고(아무에게도 보이지 않는다), 첨부 기록에는 지움 표시
    assertThat(jdbc.queryForObject("SELECT status FROM workspace WHERE id = ?::uuid", String.class, id)).isEqualTo("DELETED");
    assertThat(jdbc.queryForObject("SELECT deleted_at IS NOT NULL FROM workspace WHERE id = ?::uuid", Boolean.class, id)).isTrue();
    assertThat(jdbc.queryForList("SELECT status FROM project WHERE workspace_id = ?::uuid", String.class, id))
        .containsOnly("DELETED");
    assertThat(jdbc.queryForObject("SELECT count(*) FROM issue i JOIN project p ON p.id = i.project_id"
        + " WHERE p.workspace_id = ?::uuid", Long.class, id)).isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM issue_attachment WHERE workspace_id = ?::uuid AND deleted_at IS NULL",
        Long.class, id)).isZero();

    // 누구에게도 없는 워크스페이스 — 예전 멤버 · 시스템 관리자 목록 · MCP 키 모두
    api.get(s.owner(), "/workspaces/{ws}", s.ws()).andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("WORKSPACE_NOT_FOUND"));
    api.get(s.member(), "/me").andExpect(jsonPath("$.workspaces[?(@.slug == '" + s.ws() + "')]").isEmpty());
    api.get(s.sysadmin(), ADMIN + "?query={q}", s.ws()).andExpect(jsonPath("$.length()").value(0));
    api.delete(s.sysadmin(), ADMIN + "/{slug}?confirm={c}", s.ws(), s.ws()).andExpect(status().isNotFound());
    api.post(s.sysadmin(), ADMIN + "/{slug}/resume", null, s.ws()).andExpect(status().isNotFound());
    McpTestClient project = new McpTestClient(mvc, token, McpTestClient.PROJECT);
    assertThat((List<String>) read(project.call("list_workspaces", Map.of()), "$.workspaces[*].slug"))
        .containsExactly(api.personalWorkspace(s.member()));
    // 만든 사람의 팀 워크스페이스 수에서 빠진다
    api.get(s.owner(), "/me").andExpect(jsonPath("$.teamWorkspacesCreated").value(0));
  }

  // ── 감사 ───────────────────────────────────────────────────────────

  @Test
  void 사용_중지_다시_사용_삭제가_감사에_남는다() throws Exception {
    Space s = space("감사 팀");
    String id = wsId(s.ws());
    suspend(s);
    api.post(s.sysadmin(), ADMIN + "/{slug}/resume", null, s.ws()).andExpect(status().isOk());

    // 다시 사용한 뒤에는 그 워크스페이스의 감사(audit.read)에서 소유자도 본다 — 누가 언제 왜 멈췄는지
    var audit = api.get(s.owner(), "/workspaces/{ws}/audit?action=admin.workspace", s.ws()).andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].action").value("admin.workspace.resume"))
        .andExpect(jsonPath("$[1].action").value("admin.workspace.suspend"))
        .andExpect(jsonPath("$[1].actorId").value(s.sysadmin().id().toString()));
    assertThat((String) read((String) TestApi.read(audit, "$[1].details"), "$.reason")).isEqualTo("이용 약관 위반 조사");

    suspend(s);
    api.delete(s.sysadmin(), ADMIN + "/{slug}?confirm={c}", s.ws(), s.ws()).andExpect(status().isNoContent());

    List<Map<String, Object>> rows = jdbc.queryForList("SELECT action, actor_id::text AS actor, target_type, target_id,"
        + " details::text AS details FROM audit_log WHERE workspace_id = ?::uuid AND action LIKE 'admin.workspace.%'"
        + " ORDER BY occurred_at, id", id);
    assertThat(rows).extracting(r -> r.get("action")).containsExactly("admin.workspace.suspend", "admin.workspace.resume",
        "admin.workspace.suspend", "admin.workspace.delete");
    assertThat(rows).allSatisfy(r -> {
      assertThat(r.get("actor")).isEqualTo(s.sysadmin().id().toString());
      assertThat(r.get("target_type")).isEqualTo("workspace");
      assertThat(r.get("target_id")).isEqualTo(id);
    });
    String deleted = (String) rows.get(3).get("details");
    assertThat((String) read(deleted, "$.slug")).isEqualTo(s.ws());
    assertThat((Integer) read(deleted, "$.members")).isEqualTo(2);
    assertThat((Integer) read(deleted, "$.issues")).isEqualTo(1);
    // 감사는 삭제 뒤에도 남는다(추가 전용)
    assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE workspace_id = ?::uuid", Long.class, id))
        .isGreaterThan(4);
  }
}
