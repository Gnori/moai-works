package com.moai.works.api.issue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import com.moai.works.api.support.ApiTest;
import com.moai.works.api.support.TestApi;
import com.moai.works.api.support.TestUser;

/**
 * 일감 리비전 · 차이 · 누가 · 작업 시간 (design/05, 2026-11-22 사용자 결정).
 * 리비전은 moai-wiki 의 페이지 리비전과 같은 모양이다 — 바뀔 때마다 한 판, 덮어쓰지 않는다.
 */
@ApiTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IssueRevisionTest {

  @Autowired
  TestApi api;
  @Autowired
  JdbcTemplate jdbc;

  TestUser owner;
  TestUser editor;
  TestUser helper;
  TestUser member;
  String ws;

  static final String P = "rev";
  static final String BASE = "/workspaces/{ws}/projects/{project}";

  @BeforeAll
  void setUp() throws Exception {
    owner = api.user("owner");
    editor = api.user("editor");
    helper = api.user("helper");
    member = api.user("member");
    ws = api.teamWorkspace(owner, "리비전 시험");
    api.join(ws, owner, editor, "member");
    api.join(ws, owner, helper, "member");
    api.join(ws, owner, member, "member");
    api.post(owner, "/workspaces/{ws}/projects", Map.of("key", P, "name", "리비전", "visibility", "WORKSPACE"), ws)
        .andExpect(status().isCreated());
    for (TestUser u : List.of(editor, helper)) {
      api.put(owner, BASE + "/roles/{user}", Map.of("roles", List.of("project-editor")), ws, P, u.id())
          .andExpect(status().isOk());
    }
  }

  private int newIssue(TestUser by, Map<String, Object> body) throws Exception {
    return TestApi.<Integer>read(api.post(by, BASE + "/issues", body, ws, P).andExpect(status().isCreated()),
        "$.number");
  }

  private ResultActions patch(TestUser by, int number, Map<String, Object> changes) throws Exception {
    int version = TestApi.<Integer>read(api.get(owner, BASE + "/issues/{n}", ws, P, number), "$.rowVersion");
    Map<String, Object> body = new HashMap<>(changes);
    body.put("rowVersion", version);
    return api.patch(by, BASE + "/issues/{n}", body, ws, P, number);
  }

  private static String today() {
    return LocalDate.now(ZoneId.of("Asia/Seoul")).toString();
  }

  // ── 리비전 ─────────────────────────────────────────────────────────

  @Test
  void 만들면_1판이고_필드를_바꿀_때마다_판이_쌓이며_누가_바꿨는지_남는다() throws Exception {
    int n = newIssue(editor, Map.of("type", "feature", "title", "첫 제목", "description", "가\n나\n다"));
    api.get(owner, BASE + "/issues/{n}", ws, P, n).andExpect(jsonPath("$.revisionNo").value(1))
        .andExpect(jsonPath("$.journal[0].revisionNo").value(1));

    patch(helper, n, Map.of("title", "둘째 제목", "description", "가\n나 고침\n다\n라")).andExpect(status().isOk())
        .andExpect(jsonPath("$.revisionNo").value(2));
    // 댓글만 달면 판이 생기지 않는다
    api.post(member, BASE + "/issues/{n}/comments", Map.of("notes", "봤습니다"), ws, P, n).andExpect(status().isOk())
        .andExpect(jsonPath("$.revisionNo").value(2));

    String list = BASE + "/issues/{n}/revisions";
    api.get(member, list, ws, P, n).andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].no").value(2)).andExpect(jsonPath("$.items[0].current").value(true))
        .andExpect(jsonPath("$.items[0].actorId").value(helper.id().toString()))
        .andExpect(jsonPath("$.items[0].via").value("WEB"))
        .andExpect(jsonPath("$.items[1].no").value(1))
        .andExpect(jsonPath("$.items[1].actorId").value(editor.id().toString()))
        .andExpect(jsonPath("$.next").doesNotExist());
    List<String> fields = TestApi.read(api.get(member, list, ws, P, n), "$.items[0].changedFields");
    assertThat(fields).containsExactlyInAnyOrder("title", "description");

    // 옛 판은 그때 모습 그대로다
    api.get(member, list + "/{no}", ws, P, n, 1).andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("첫 제목")).andExpect(jsonPath("$.description").value("가\n나\n다"))
        .andExpect(jsonPath("$.current").value(false));
  }

  @Test
  void 두_판의_차이는_필드는_전후로_설명은_git_처럼_줄_단위로_보인다() throws Exception {
    int n = newIssue(editor, Map.of("type", "feature", "title", "차이", "description", "하나\n둘\n셋"));
    patch(editor, n, Map.of("status", "IN_PROGRESS", "description", "하나\n둘 고침\n셋\n넷", "estimatedHours", 8))
        .andExpect(status().isOk());

    String diff = BASE + "/issues/{n}/revisions/{no}/diff";
    ResultActions d = api.get(member, diff, ws, P, n, 2).andExpect(status().isOk())
        .andExpect(jsonPath("$.from").value(1)).andExpect(jsonPath("$.to").value(2))
        .andExpect(jsonPath("$.added").value(2)).andExpect(jsonPath("$.removed").value(1))
        .andExpect(jsonPath("$.toInfo.actorId").value(editor.id().toString()));
    List<String> fields = TestApi.read(d, "$.fields[*].field");
    assertThat(fields).containsExactlyInAnyOrder("status", "estimatedHours");
    List<String> ops = TestApi.read(d, "$.descriptionLines[*].op");
    assertThat(ops).containsExactly("SAME", "DEL", "ADD", "SAME", "ADD");

    // 1판은 "없음"과 견준다 — 모든 필드가 새로 생긴 것으로 나온다
    api.get(member, diff, ws, P, n, 1).andExpect(jsonPath("$.from").doesNotExist())
        .andExpect(jsonPath("$.added").value(3));
    // 아무 두 판이나 견줄 수 있고, 같은 판 · 없는 판은 거절한다
    api.get(member, diff + "?from=2", ws, P, n, 1).andExpect(status().isOk());
    api.get(member, diff + "?from=2", ws, P, n, 2).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("SAME_REVISION"));
    api.get(member, diff, ws, P, n, 9).andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("REVISION_NOT_FOUND"));
  }

  @Test
  void 리비전은_고치거나_지울_수_없다() throws Exception {
    int n = newIssue(editor, Map.of("type", "feature", "title", "못 고치는 판"));
    assertThatThrownBy(() -> jdbc.update("UPDATE issue_revision SET title = 'x' WHERE issue_id = "
        + "(SELECT id FROM issue WHERE number = ? AND project_id = (SELECT id FROM project WHERE key = ? LIMIT 1))",
        n, P)).hasMessageContaining("추가만");
  }

  // ── 작업 시간 ──────────────────────────────────────────────────────

  @Test
  void 예상_공수를_정하고_작업_시간은_여러_번_누가_적었는지와_함께_쌓인다() throws Exception {
    int n = newIssue(editor, Map.of("type", "feature", "title", "공수", "estimatedHours", 10,
        "assigneeId", editor.id().toString()));
    api.get(owner, BASE + "/issues/{n}", ws, P, n).andExpect(jsonPath("$.estimatedHours").value(10));

    String logs = BASE + "/issues/{n}/work-logs";
    api.post(editor, logs, Map.of("workDate", today(), "hours", 2, "note", "설계"), ws, P, n)
        .andExpect(status().isOk());
    api.post(editor, logs, Map.of("workDate", "2026-11-01", "hours", 1.5), ws, P, n).andExpect(status().isOk());
    // 담당자가 아니어도 일감을 고칠 수 있는 사람이면 적는다
    api.post(helper, logs, Map.of("workDate", today(), "hours", 3, "note", "검토 도움"), ws, P, n)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.spentHours").value(6.5))
        .andExpect(jsonPath("$.workLogs.length()").value(3));
    List<String> who = TestApi.read(api.get(owner, BASE + "/issues/{n}", ws, P, n), "$.workLogs[*].user.id");
    assertThat(who).contains(editor.id().toString(), helper.id().toString());

    // 목록에도 예상 · 쓴 시간이 보인다
    api.get(owner, BASE + "/issues?q={q}", ws, P, "공수").andExpect(jsonPath("$.items[0].estimatedHours").value(10))
        .andExpect(jsonPath("$.items[0].spentHours").value(6.5));
    // 작업 시간은 판을 만들지 않고 이력에 남는다
    api.get(owner, BASE + "/issues/{n}", ws, P, n).andExpect(jsonPath("$.revisionNo").value(1));
    List<String> journalFields = TestApi.read(api.get(owner, BASE + "/issues/{n}", ws, P, n),
        "$.journal[*].changes[*].field");
    assertThat(journalFields).contains("workLog");
  }

  @Test
  void 작업_시간의_규칙과_지우기() throws Exception {
    int n = newIssue(editor, Map.of("type", "feature", "title", "시간 규칙"));
    String logs = BASE + "/issues/{n}/work-logs";
    api.post(member, logs, Map.of("workDate", today(), "hours", 1), ws, P, n).andExpect(status().isForbidden());
    api.post(editor, logs, Map.of("workDate", "2999-01-01", "hours", 1), ws, P, n).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_WORK_DATE"));
    for (Object bad : List.of(0, 25, -1)) {
      api.post(editor, logs, Map.of("workDate", today(), "hours", bad), ws, P, n).andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("INVALID_WORK_HOURS"));
    }

    api.post(editor, logs, Map.of("workDate", today(), "hours", 2), ws, P, n).andExpect(status().isOk());
    String editorLog = TestApi.read(api.get(owner, BASE + "/issues/{n}", ws, P, n), "$.workLogs[0].id");
    // 남의 기록은 project.manage 가 있어야 지운다
    api.delete(helper, logs + "/{id}", ws, P, n, editorLog).andExpect(status().isForbidden());
    api.delete(owner, logs + "/{id}", ws, P, n, editorLog).andExpect(status().isOk())
        .andExpect(jsonPath("$.spentHours").value(0)).andExpect(jsonPath("$.workLogs").isEmpty());
    api.delete(owner, logs + "/{id}", ws, P, n, editorLog).andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("WORK_LOG_NOT_FOUND"));
    // 자기 기록은 자기가 지운다
    api.post(helper, logs, Map.of("workDate", today(), "hours", 1), ws, P, n).andExpect(status().isOk());
    String helperLog = TestApi.read(api.get(owner, BASE + "/issues/{n}", ws, P, n), "$.workLogs[0].id");
    api.delete(helper, logs + "/{id}", ws, P, n, helperLog).andExpect(status().isOk());

    // 지운 사람이 남는다
    String deletedBy = jdbc.queryForObject("SELECT deleted_by::text FROM issue_work_log WHERE id = ?::uuid",
        String.class, editorLog);
    assertThat(deletedBy).isEqualTo(owner.id().toString());
    List<String> actions = TestApi.read(api.get(owner, "/workspaces/{ws}/audit?limit=200", ws), "$[*].action");
    assertThat(actions).contains("issue.work_log.add", "issue.work_log.remove");
  }

  @Test
  void 작업_시간_기록은_지울_수_없고_값을_바꿀_수_없다() throws Exception {
    int n = newIssue(editor, Map.of("type", "feature", "title", "보호"));
    api.post(editor, BASE + "/issues/{n}/work-logs", Map.of("workDate", today(), "hours", 2), ws, P, n)
        .andExpect(status().isOk());
    String id = TestApi.read(api.get(owner, BASE + "/issues/{n}", ws, P, n), "$.workLogs[0].id");
    assertThatThrownBy(() -> jdbc.update("DELETE FROM issue_work_log WHERE id = ?::uuid", id))
        .hasMessageContaining("지울 수 없다");
    assertThatThrownBy(() -> jdbc.update("UPDATE issue_work_log SET hours = 9 WHERE id = ?::uuid", id))
        .hasMessageContaining("바꿀 수 없다");
  }

  @Test
  void 변화_기록의_시각은_밀리초까지다() throws Exception {
    int n = newIssue(editor, Map.of("type", "feature", "title", "밀리초"));
    // 마이크로초 자리가 남지 않는다 — 밀리초 단위로 잘려 저장된다
    Long micros = jdbc.queryForObject("SELECT (extract(microseconds FROM created_at)::bigint % 1000) FROM issue"
        + " WHERE number = ? AND project_id = (SELECT id FROM project WHERE key = ? LIMIT 1)", Long.class, n, P);
    assertThat(micros).isZero();
    String createdAt = TestApi.read(api.get(owner, BASE + "/issues/{n}", ws, P, n), "$.createdAt");
    assertThat(createdAt).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d{1,3})?Z");
  }
}
