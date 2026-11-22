package com.moai.works.api.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.moai.works.api.support.ApiTest;
import com.moai.works.api.support.TestApi;
import com.moai.works.api.support.TestUser;

/**
 * 알림 (design/07) — 무엇을 누구에게 알리는가 · 자기 일은 알리지 않음 · 묶기 · 받기 설정 · 아침 알림 · 알림함.
 */
@ApiTest
class NotificationTest {

  private static final String BASE = "/workspaces/{ws}/projects/{project}";
  private static final String P = "work";
  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

  @Autowired
  TestApi api;
  @Autowired
  NotificationReminders reminders;
  @Autowired
  JdbcTemplate jdbc;

  private record Space(String ws, TestUser owner, TestUser admin, TestUser alice, TestUser bob) {
  }

  /** 소유자 · 관리자 · 큐레이터 둘(일감을 만들고 고칠 수 있다) */
  private Space space() throws Exception {
    TestUser owner = api.user("owner");
    String ws = api.teamWorkspace(owner, "알림 시험");
    TestUser admin = api.user("admin");
    TestUser alice = api.user("alice");
    TestUser bob = api.user("bob");
    api.join(ws, owner, admin, "admin");
    api.join(ws, owner, alice, "curator");
    api.join(ws, owner, bob, "curator");
    api.post(owner, "/workspaces/{ws}/projects", Map.of("key", P, "name", "업무", "visibility", "WORKSPACE"), ws)
        .andExpect(status().isCreated());
    // 가입 승인 · 가입 신청 알림은 이 시험의 관심 밖 — 비우고 시작한다
    for (TestUser u : List.of(owner, admin, alice, bob)) {
      clear(u);
    }
    return new Space(ws, owner, admin, alice, bob);
  }

  private int issue(Space s, TestUser by, Map<String, Object> body) throws Exception {
    Map<String, Object> b = new HashMap<>(Map.of("type", "task", "title", "알림 일감"));
    b.putAll(body);
    return TestApi.<Integer>read(api.post(by, BASE + "/issues", b, s.ws(), P).andExpect(status().isCreated()),
        "$.number");
  }

  private void patch(Space s, TestUser by, int number, Map<String, Object> changes) throws Exception {
    Map<String, Object> body = new HashMap<>(changes);
    body.put("rowVersion", TestApi.<Integer>read(api.get(by, BASE + "/issues/{n}", s.ws(), P, number), "$.rowVersion"));
    api.patch(by, BASE + "/issues/{n}", body, s.ws(), P, number).andExpect(status().isOk());
  }

  private List<String> kinds(TestUser who) throws Exception {
    return TestApi.read(api.get(who, "/me/notifications?unread=true"), "$.items[*].kind");
  }

  private void clear(TestUser who) throws Exception {
    api.post(who, "/me/notifications/read-all", null).andExpect(status().isOk());
  }

  @Test
  void 담당자로_정하면_알리고_자기_일은_자기에게_알리지_않는다() throws Exception {
    Space s = space();
    int n = issue(s, s.alice(), Map.of("assigneeId", s.bob().id().toString()));

    assertThat(kinds(s.bob())).containsExactly("issue.assigned");
    api.get(s.bob(), "/me/notifications").andExpect(jsonPath("$.unread").value(1))
        .andExpect(jsonPath("$.items[0].params.number").value(n))
        .andExpect(jsonPath("$.items[0].params.project").value(P))
        .andExpect(jsonPath("$.items[0].params.via").value("WEB"))
        .andExpect(jsonPath("$.items[0].workspaceSlug").value(s.ws()))
        .andExpect(jsonPath("$.items[0].actorName").exists());
    // 만든 사람이 관리자다 — 자기에게는 알리지 않는다
    assertThat(kinds(s.alice())).isEmpty();
  }

  @Test
  void 담당자를_바꾸면_새_담당자와_옛_담당자에게_상태는_지켜보는_사람에게() throws Exception {
    Space s = space();
    int n = issue(s, s.alice(), Map.of("assigneeId", s.bob().id().toString()));
    clear(s.bob());

    patch(s, s.alice(), n, Map.of("assigneeId", s.admin().id().toString()));
    assertThat(kinds(s.admin())).containsExactly("issue.assigned");
    assertThat(kinds(s.bob())).containsExactly("issue.unassigned");

    // 상태 · 내용 변경은 관리자 · 담당자에게 — 고친 사람은 빼고. 여러 번 고쳐도 읽지 않은 동안은 한 줄
    clear(s.admin());
    patch(s, s.owner(), n, Map.of("status", "IN_PROGRESS"));
    patch(s, s.owner(), n, Map.of("title", "고친 제목"));
    patch(s, s.owner(), n, Map.of("dueDate", LocalDate.now(SEOUL).plusDays(10).toString()));
    assertThat(kinds(s.admin())).containsExactlyInAnyOrder("issue.status_changed", "issue.updated");
    assertThat(kinds(s.alice())).containsExactlyInAnyOrder("issue.status_changed", "issue.updated");
    api.get(s.alice(), "/me/notifications?unread=true")
        .andExpect(jsonPath("$.items[?(@.kind == 'issue.updated')].count").value(2))
        .andExpect(jsonPath("$.items[?(@.kind == 'issue.status_changed')].params.to").value("IN_PROGRESS"));
  }

  @Test
  void 댓글은_관리자_담당자_앞서_댓글을_단_사람에게() throws Exception {
    Space s = space();
    int n = issue(s, s.alice(), Map.of("assigneeId", s.bob().id().toString()));
    clear(s.bob());
    api.post(s.admin(), BASE + "/issues/{n}/comments", Map.of("notes", "확인 부탁"), s.ws(), P, n)
        .andExpect(status().is2xxSuccessful());
    assertThat(kinds(s.alice())).containsExactly("issue.commented");
    assertThat(kinds(s.bob())).containsExactly("issue.commented");
    assertThat(kinds(s.admin())).isEmpty();

    // 앞서 댓글을 단 관리자도 다음 댓글을 받는다
    api.post(s.bob(), BASE + "/issues/{n}/comments", Map.of("notes", "봤습니다"), s.ws(), P, n)
        .andExpect(status().is2xxSuccessful());
    assertThat(kinds(s.admin())).containsExactly("issue.commented");
  }

  @Test
  void 작업_시간은_관리자에게_예상_공수를_넘으면_한_번() throws Exception {
    Space s = space();
    int n = issue(s, s.alice(), Map.of("assigneeId", s.bob().id().toString(), "estimatedHours", 4));
    clear(s.bob());
    String today = LocalDate.now(SEOUL).toString();
    api.post(s.bob(), BASE + "/issues/{n}/work-logs", Map.of("workDate", today, "hours", 3), s.ws(), P, n)
        .andExpect(status().is2xxSuccessful());
    assertThat(kinds(s.alice())).containsExactly("issue.work_logged");

    api.post(s.bob(), BASE + "/issues/{n}/work-logs", Map.of("workDate", today, "hours", 2), s.ws(), P, n)
        .andExpect(status().is2xxSuccessful());
    api.post(s.bob(), BASE + "/issues/{n}/work-logs", Map.of("workDate", today, "hours", 1), s.ws(), P, n)
        .andExpect(status().is2xxSuccessful());
    assertThat(kinds(s.alice())).containsExactlyInAnyOrder("issue.work_logged", "issue.over_estimate");
    api.get(s.alice(), "/me/notifications")
        .andExpect(jsonPath("$.items[?(@.kind == 'issue.over_estimate')].count").value(1))
        .andExpect(jsonPath("$.items[?(@.kind == 'issue.work_logged')].count").value(3));
    // 넘긴 본인(담당자)에게는 알리지 않는다
    assertThat(kinds(s.bob())).isEmpty();
  }

  @Test
  void 하위_일감과_연결과_지우기() throws Exception {
    Space s = space();
    int parent = issue(s, s.alice(), Map.of("assigneeId", s.bob().id().toString()));
    clear(s.bob());
    int child = issue(s, s.owner(), Map.of("parent", parent));
    assertThat(kinds(s.alice())).containsExactly("issue.child_added");
    api.get(s.bob(), "/me/notifications").andExpect(jsonPath("$.items[0].params.childNumber").value(child));

    clear(s.alice());
    clear(s.bob());
    int other = issue(s, s.owner(), Map.of());
    api.post(s.owner(), BASE + "/issues/{n}/links", Map.of("target", parent, "type", "RELATES"), s.ws(), P, other)
        .andExpect(status().is2xxSuccessful());
    assertThat(kinds(s.alice())).containsExactly("issue.linked");

    clear(s.alice());
    api.delete(s.owner(), BASE + "/issues/{n}", s.ws(), P, child).andExpect(status().is2xxSuccessful());
    api.delete(s.owner(), BASE + "/issues/{n}", s.ws(), P, other).andExpect(status().is2xxSuccessful());
    patch(s, s.owner(), parent, Map.of("managerId", s.admin().id().toString()));
    assertThat(kinds(s.admin())).contains("issue.manager_assigned");
  }

  @Test
  void 받기_설정으로_끄고_꼭_받아야_하는_것은_끌_수_없다() throws Exception {
    Space s = space();
    api.put(s.bob(), "/me/notifications/preferences/{k}", Map.of("enabled", false), "issue.assigned")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.kind == 'issue.assigned')].enabled").value(false))
        .andExpect(jsonPath("$[?(@.kind == 'mcp_key.expired')].mandatory").value(true));
    issue(s, s.alice(), Map.of("assigneeId", s.bob().id().toString()));
    assertThat(kinds(s.bob())).isEmpty();

    api.put(s.bob(), "/me/notifications/preferences/{k}", Map.of("enabled", false), "mcp_key.expired")
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("NOTIFICATION_MANDATORY"));
    api.put(s.bob(), "/me/notifications/preferences/{k}", Map.of("enabled", false), "nope.kind")
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_NOTIFICATION_KIND"));
    // 다시 켜면 받는다
    api.put(s.bob(), "/me/notifications/preferences/{k}", Map.of("enabled", true), "issue.assigned")
        .andExpect(status().isOk());
    issue(s, s.alice(), Map.of("assigneeId", s.bob().id().toString()));
    assertThat(kinds(s.bob())).containsExactly("issue.assigned");
  }

  @Test
  void 워크스페이스_가입_역할_내보내기() throws Exception {
    Space s = space();
    // 가입 신청은 처리할 수 있는 사람(소유자 · 관리자)에게, 결과는 신청한 사람에게
    TestUser newcomer = api.user("newcomer");
    api.join(s.ws(), s.owner(), newcomer, "member");
    assertThat(kinds(s.admin())).contains("join.requested");
    assertThat(kinds(newcomer)).containsExactly("join.approved");

    api.setRoles(s.ws(), s.owner(), newcomer, "curator");
    assertThat(kinds(newcomer)).contains("member.roles_changed");

    api.put(s.owner(), BASE + "/roles/{user}", Map.of("roles", List.of("project-editor")), s.ws(), P, newcomer.id())
        .andExpect(status().isOk());
    assertThat(kinds(newcomer)).contains("project.roles_changed");

    api.delete(s.owner(), "/workspaces/{ws}/members/{user}", s.ws(), newcomer.id()).andExpect(status().is2xxSuccessful());
    assertThat(kinds(newcomer)).contains("member.removed");
    // 스스로 나간 사람에게는 알리지 않는다
    api.post(s.bob(), "/workspaces/{ws}/leave", null, s.ws()).andExpect(status().is2xxSuccessful());
    assertThat(kinds(s.bob())).doesNotContain("member.removed");
  }

  @Test
  void 관리자가_MCP_키를_만료시키면_주인에게_알린다() throws Exception {
    Space s = space();
    api.post(s.alice(), "/workspaces/{ws}/mcp-keys", Map.of("name", "노트북"), s.ws()).andExpect(status().isCreated());
    String id = TestApi.read(api.get(s.alice(), "/workspaces/{ws}/mcp-keys", s.ws()), "$[0].id");
    api.post(s.admin(), "/workspaces/{ws}/mcp-keys/{id}/expire", Map.of("note", "분실 신고"), s.ws(), id)
        .andExpect(status().isOk());
    assertThat(kinds(s.alice())).containsExactly("mcp_key.expired");
    api.get(s.alice(), "/me/notifications").andExpect(jsonPath("$.items[0].params.note").value("분실 신고"));
  }

  @Test
  void 아침_알림은_기한_임박_기한_지남_키_만료_예정을_한_번씩() throws Exception {
    Space s = space();
    LocalDate today = LocalDate.now(SEOUL);
    int soon = issue(s, s.alice(), Map.of("assigneeId", s.bob().id().toString(),
        "dueDate", today.plusDays(1).toString()));
    int late = issue(s, s.alice(), Map.of("assigneeId", s.bob().id().toString(),
        "startDate", today.minusDays(5).toString(), "dueDate", today.minusDays(2).toString()));
    int done = issue(s, s.alice(), Map.of("assigneeId", s.bob().id().toString(), "status", "DONE",
        "dueDate", today.minusDays(1).toString()));
    api.post(s.bob(), "/workspaces/{ws}/mcp-keys", Map.of("name", "곧 끝남", "days", 3), s.ws())
        .andExpect(status().isCreated());
    clear(s.bob());
    clear(s.alice());

    reminders.run();
    reminders.run();   // 두 번 돌아도 한 번만

    List<Map<String, Object>> bob = TestApi.read(api.get(s.bob(), "/me/notifications?unread=true"), "$.items");
    assertThat(bob).extracting(m -> m.get("kind") + "#" + ((Map<?, ?>) m.get("params")).get("number"))
        .contains("issue.due_soon#" + soon, "issue.overdue#" + late)
        .doesNotContain("issue.overdue#" + done, "issue.due_soon#" + done);
    assertThat(bob).filteredOn(m -> "mcp_key.expiring".equals(m.get("kind"))).hasSize(1);
    assertThat(bob).allSatisfy(m -> assertThat(m.get("count")).isEqualTo(1));
    // 관리자도 기한 알림을 받는다(만든 사람이지만 시스템이 보낸 알림이다)
    assertThat(kinds(s.alice())).contains("issue.due_soon", "issue.overdue");
  }

  @Test
  void 더_보기는_같은_밀리초의_줄을_건너뛰지_않는다() throws Exception {
    Space s = space();
    // 알림함 — 같은 시각에 묶인 알림 다섯
    for (int i = 0; i < 5; i++) {
      jdbc.update("INSERT INTO notification (id, recipient_id, kind, subject_type, params, count, created_at, updated_at)"
          + " VALUES (gen_random_uuid(), ?::uuid, 'issue.assigned', 'issue', '{}', 1, TIMESTAMPTZ '2026-01-01 00:00:00.123Z',"
          + " TIMESTAMPTZ '2026-01-01 00:00:00.123Z')", s.bob().id().toString());
    }
    java.util.Set<String> seen = new java.util.HashSet<>();
    String next = null;
    do {
      org.springframework.test.web.servlet.ResultActions page = next == null
          ? api.get(s.bob(), "/me/notifications?limit=2")
          : api.get(s.bob(), "/me/notifications?limit=2&before={b}", next);
      seen.addAll(TestApi.<List<String>>read(page, "$.items[*].id"));
      next = TestApi.read(page, "$.next");
    } while (next != null);
    // 넣은 다섯과 원래 있던 것(가입 승인 등)이 모두 한 번씩 — 건너뛴 줄이 없다
    Integer all = jdbc.queryForObject("SELECT count(*) FROM notification WHERE recipient_id = ?::uuid", Integer.class,
        s.bob().id().toString());
    assertThat(seen).hasSize(all).hasSizeGreaterThanOrEqualTo(5);

    // 감사 기록 — 같은 시각의 줄 다섯
    String wsId = jdbc.queryForObject("SELECT id::text FROM workspace WHERE slug = ?", String.class, s.ws());
    for (int i = 0; i < 5; i++) {
      jdbc.update("INSERT INTO audit_log (id, occurred_at, actor_type, workspace_id, action, details)"
          + " VALUES (gen_random_uuid(), TIMESTAMPTZ '2026-01-01 00:00:00.456Z', 'SYSTEM', ?::uuid, 'test.same_ms', '{}')", wsId);
    }
    java.util.Set<String> rows = new java.util.HashSet<>();
    String before = null;
    String beforeId = null;
    for (int page = 0; page < 5; page++) {
      String path = "/workspaces/{ws}/audit?action=test.same_ms&limit=2"
          + (before == null ? "" : "&before=" + before + "&beforeId=" + beforeId);
      List<Map<String, Object>> items = TestApi.read(api.get(s.owner(), path, s.ws()), "$");
      if (items.isEmpty()) {
        break;
      }
      items.forEach(m -> rows.add((String) m.get("id")));
      before = (String) items.get(items.size() - 1).get("occurredAt");
      beforeId = (String) items.get(items.size() - 1).get("id");
    }
    assertThat(rows).hasSize(5);
  }

  @Test
  void 알림함은_자기_것만_읽고_고친다() throws Exception {
    Space s = space();
    issue(s, s.alice(), Map.of("assigneeId", s.bob().id().toString()));
    String id = TestApi.read(api.get(s.bob(), "/me/notifications"), "$.items[0].id");

    api.post(s.alice(), "/me/notifications/{id}/read", null, id).andExpect(status().isNotFound());
    api.get(s.bob(), "/me/notifications/unread-count").andExpect(jsonPath("$.unread").value(1));
    api.post(s.bob(), "/me/notifications/{id}/read", null, id).andExpect(status().isOk())
        .andExpect(jsonPath("$.unread").value(0));
    api.get(s.bob(), "/me/notifications?unread=true").andExpect(jsonPath("$.items.length()").value(0));
    api.get(s.bob(), "/me/notifications").andExpect(jsonPath("$.items[0].readAt").exists());
    // 시스템 관리자 계정에는 알림함이 없다
    TestUser sysadmin = api.user("sysadmin", "system-admin");
    api.get(sysadmin, "/me/notifications").andExpect(status().isForbidden());
  }
}
