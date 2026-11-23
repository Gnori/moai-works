package com.moai.works.api.issue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import com.moai.works.api.support.ApiTest;
import com.moai.works.api.support.TestApi;
import com.moai.works.api.support.TestUser;

/**
 * 댓글 고치기 · 지우기 (FR-ISS-019, 일감 #4) — 쓴 사람만 고치고(지금 issue.comment 도), 지우기는 쓴 사람 또는 project.manage.
 * 고치면 앞 글을 쌓아 두고 판이 오르며, 지우면 "지운 댓글"로 제자리에 남고 글은 더 내주지 않는다. 이력 · 감사 · DB 트리거.
 */
@ApiTest
class CommentTest {

  private static final String P = "talk";
  private static final String ISSUE = "/workspaces/{ws}/issues/{n}";
  private static final String COMMENTS = ISSUE + "/comments";
  private static final String COMMENT = COMMENTS + "/{id}";

  @Autowired
  TestApi api;
  @Autowired
  JdbcTemplate jdbc;

  /** 소유자(project.manage) · 큐레이터 · 멤버 둘, 일감 하나 */
  private record Space(String ws, TestUser owner, TestUser curator, TestUser member, TestUser other, int issue) {
  }

  private Space space() throws Exception {
    TestUser owner = api.user("owner");
    String ws = api.teamWorkspace(owner, "댓글 시험");
    TestUser curator = api.user("curator");
    TestUser member = api.user("member");
    TestUser other = api.user("other");
    api.join(ws, owner, curator, "curator");
    api.join(ws, owner, member, "member");
    api.join(ws, owner, other, "member");
    api.post(owner, "/workspaces/{ws}/projects", Map.of("key", P, "name", "이야기", "visibility", "WORKSPACE"), ws)
        .andExpect(status().isCreated());
    int n = TestApi.read(api.post(curator, "/workspaces/{ws}/projects/{p}/issues", Map.of("type", "feature", "title", "댓글"),
        ws, P).andExpect(status().isCreated()), "$.number");
    return new Space(ws, owner, curator, member, other, n);
  }

  /** 댓글을 달고 그 댓글(이력 줄)의 id */
  private String comment(Space s, TestUser who, String notes) throws Exception {
    ResultActions r = api.post(who, COMMENTS, Map.of("notes", notes), s.ws(), s.issue()).andExpect(status().isOk());
    List<String> ids = TestApi.read(r, "$.journal[?(@.notes == '" + notes + "')].id");
    return ids.get(ids.size() - 1);
  }

  private static Map<String, Object> edit(String notes, Integer rowVersion) {
    Map<String, Object> m = new HashMap<>();
    m.put("notes", notes);
    m.put("rowVersion", rowVersion);
    return m;
  }

  private ResultActions detail(Space s) throws Exception {
    return api.get(s.owner(), ISSUE, s.ws(), s.issue()).andExpect(status().isOk());
  }

  @Test
  void 쓴_사람만_고치고_남은_403_고친_표시와_앞_글이_남는다() throws Exception {
    Space s = space();
    String id = comment(s, s.member(), "처음 글");
    int issueRowVersion = TestApi.read(detail(s), "$.rowVersion");

    // 남 — 같은 멤버도, 관리 권한(project.manage)이 있는 소유자도 고치지 못한다
    api.patch(s.other(), COMMENT, edit("남이 고침", 0), s.ws(), s.issue(), id).andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("PERMISSION_REQUIRED"));
    api.patch(s.owner(), COMMENT, edit("관리자가 고침", 0), s.ws(), s.issue(), id).andExpect(status().isForbidden());

    // 쓴 사람 — 고친 표시(editedAt) · 판이 오르고, 일감의 rowVersion · 판은 그대로다
    api.patch(s.member(), COMMENT, edit("고친 글", 0), s.ws(), s.issue(), id).andExpect(status().isOk())
        .andExpect(jsonPath("$.rowVersion").value(issueRowVersion))
        .andExpect(jsonPath("$.revisionNo").value(1));
    detail(s)
        .andExpect(jsonPath("$.journal[?(@.id == '" + id + "')].notes").value("고친 글"))
        .andExpect(jsonPath("$.journal[?(@.id == '" + id + "')].rowVersion").value(1))
        .andExpect(jsonPath("$.journal[?(@.id == '" + id + "')].editedAt", Matchers.everyItem(Matchers.notNullValue())))
        .andExpect(jsonPath("$.journal[?(@.id == '" + id + "')].deleted").value(false))
        // 이력에 한 줄(판은 아님) — 댓글 id · 쓴 사람만, 글은 넣지 않는다
        .andExpect(jsonPath("$.journal[?(@.changes[0].field == 'comment')].changes[0].after.id").value(id))
        .andExpect(jsonPath("$.journal[?(@.changes[0].field == 'comment')].revisionNo").value(Matchers.contains(Matchers.nullValue())))
        .andExpect(jsonPath("$.journal[?(@.changes[0].field == 'comment')].changes[0].after.author.id")
            .value(s.member().id().toString()));
    String historyLine = TestApi.read(detail(s), "$.journal[?(@.changes[0].field == 'comment')].changes[0]").toString();
    assertThat(historyLine).doesNotContain("처음 글").doesNotContain("고친 글");

    // 앞 글은 쌓아 둔다(옛 글을 보는 API 는 아직 없다)
    List<Map<String, Object>> kept = jdbc.queryForList(
        "SELECT row_version, notes, replaced_by::text AS by, replaced_via FROM issue_comment_revision WHERE journal_id = ?::uuid", id);
    assertThat(kept).hasSize(1);
    assertThat(kept.get(0)).containsEntry("row_version", 0).containsEntry("notes", "처음 글")
        .containsEntry("by", s.member().id().toString()).containsEntry("replaced_via", "WEB");

    // 낡은 rowVersion 이면 409 CONCURRENT_UPDATE(지금 판을 함께), 비우면 400
    api.patch(s.member(), COMMENT, edit("늦은 고침", 0), s.ws(), s.issue(), id).andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CONCURRENT_UPDATE")).andExpect(jsonPath("$.rowVersion").value(1));
    api.patch(s.member(), COMMENT, edit("판 없음", null), s.ws(), s.issue(), id).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("ROW_VERSION_REQUIRED"));
    // 같은 글이면 바뀌는 것이 없다(판 · 이력 그대로)
    api.patch(s.member(), COMMENT, edit("고친 글", 1), s.ws(), s.issue(), id).andExpect(status().isOk());
    detail(s).andExpect(jsonPath("$.journal[?(@.id == '" + id + "')].rowVersion").value(1))
        .andExpect(jsonPath("$.journal[?(@.changes[0].field == 'comment')]", Matchers.hasSize(1)));

    // 감사 issue.comment.update — 경로 WEB
    List<String> details = TestApi.read(api.get(s.owner(), "/workspaces/{ws}/audit?action=issue.comment.update", s.ws()),
        "$[*].details");
    assertThat(details).hasSize(1).allMatch(d -> d.contains("WEB") && d.contains(id));
  }

  @Test
  void 지금_댓글_권한이_없으면_고치지_못하지만_자기_댓글은_지운다() throws Exception {
    Space s = space();
    String id = comment(s, s.member(), "권한이 바뀔 사람의 글");
    String readOnly = api.customRole(s.ws(), s.owner(), "읽기만", Set.of("workspace.read", "member.read", "issue.read"));
    api.setRoles(s.ws(), s.owner(), s.member(), readOnly);
    api.patch(s.member(), COMMENT, edit("고침", 0), s.ws(), s.issue(), id).andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("PERMISSION_REQUIRED"));
    api.delete(s.member(), COMMENT, s.ws(), s.issue(), id).andExpect(status().isOk())
        .andExpect(jsonPath("$.journal[?(@.id == '" + id + "')].deleted").value(true));
  }

  @Test
  void 지우기는_쓴_사람이나_project_manage_만_하고_지운_댓글로_남는다() throws Exception {
    Space s = space();
    String mine = comment(s, s.member(), "내가 지울 글");
    String theirs = comment(s, s.curator(), "관리자가 지울 글");

    // 남의 댓글 — 멤버 · 큐레이터는 403
    api.delete(s.other(), COMMENT, s.ws(), s.issue(), mine).andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("PERMISSION_REQUIRED"));
    api.delete(s.member(), COMMENT, s.ws(), s.issue(), theirs).andExpect(status().isForbidden());

    api.delete(s.member(), COMMENT, s.ws(), s.issue(), mine).andExpect(status().isOk());
    // 관리 권한(project.manage)이 있는 소유자는 남의 댓글을 지운다 — 그래도 고치지는 못한다(위 시험)
    api.delete(s.owner(), COMMENT, s.ws(), s.issue(), theirs).andExpect(status().isOk());

    // 제자리에 "지운 댓글"로 남는다 — 글은 없고 누가 · 언제
    String body = detail(s)
        .andExpect(jsonPath("$.journal[?(@.id == '" + mine + "')].deleted").value(true))
        .andExpect(jsonPath("$.journal[?(@.id == '" + mine + "')].notes").value(Matchers.contains(Matchers.nullValue())))
        .andExpect(jsonPath("$.journal[?(@.id == '" + mine + "')].deletedBy.id").value(s.member().id().toString()))
        .andExpect(jsonPath("$.journal[?(@.id == '" + theirs + "')].deletedBy.id").value(s.owner().id().toString()))
        .andExpect(jsonPath("$.journal[?(@.id == '" + theirs + "')].deletedAt", Matchers.everyItem(Matchers.notNullValue())))
        // 이력에 지운 줄 둘(after = null)
        .andExpect(jsonPath("$.journal[?(@.changes[0].field == 'comment')]", Matchers.hasSize(2)))
        .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
    assertThat(body).doesNotContain("내가 지울 글").doesNotContain("관리자가 지울 글");
    // 글은 DB 에는 남는다
    assertThat(jdbc.queryForObject("SELECT notes FROM issue_journal WHERE id = ?::uuid", String.class, mine))
        .isEqualTo("내가 지울 글");

    // 지운 댓글은 고치거나 다시 지울 수 없다
    api.patch(s.member(), COMMENT, edit("되살림", 0), s.ws(), s.issue(), mine).andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("COMMENT_DELETED"));
    api.delete(s.member(), COMMENT, s.ws(), s.issue(), mine).andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("COMMENT_DELETED"));

    // 감사 issue.comment.delete — 지운 사람 · 쓴 사람 · 경로
    List<String> details = TestApi.read(api.get(s.owner(), "/workspaces/{ws}/audit?action=issue.comment.delete", s.ws()),
        "$[*].details");
    assertThat(details).hasSize(2).allMatch(d -> d.contains("WEB"));
    assertThat(details.get(0)).contains(theirs, s.curator().id().toString());
  }

  @Test
  void 없는_댓글_댓글이_아닌_이력_줄은_404_보관한_프로젝트는_409() throws Exception {
    Space s = space();
    String id = comment(s, s.member(), "글");
    String creationLine = TestApi.read(detail(s), "$.journal[0].id");
    api.patch(s.member(), COMMENT, edit("x", 0), s.ws(), s.issue(), creationLine).andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("COMMENT_NOT_FOUND"));
    api.delete(s.owner(), COMMENT, s.ws(), s.issue(), "0196a000-0000-7000-8000-000000000000")
        .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("COMMENT_NOT_FOUND"));
    // 다른 일감의 주소로는 없는 것과 같다
    int other = TestApi.read(api.post(s.curator(), "/workspaces/{ws}/projects/{p}/issues", Map.of("type", "bug", "title", "딴 일감"),
        s.ws(), P).andExpect(status().isCreated()), "$.number");
    api.delete(s.member(), COMMENT, s.ws(), other, id).andExpect(status().isNotFound());
    // 빈 글 · 너무 긴 글
    api.patch(s.member(), COMMENT, edit(" ", 0), s.ws(), s.issue(), id).andExpect(status().isBadRequest());
    api.patch(s.member(), COMMENT, edit("가".repeat(30001), 0), s.ws(), s.issue(), id).andExpect(status().isBadRequest());

    // 보관한 프로젝트는 관리자에게만 보이고, 관리자도 댓글을 바꾸지 못한다
    api.post(s.owner(), "/workspaces/{ws}/projects/{p}/archive", null, s.ws(), P).andExpect(status().is2xxSuccessful());
    api.delete(s.owner(), COMMENT, s.ws(), s.issue(), id).andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("PROJECT_NOT_WRITABLE"));
  }

  @Test
  void 댓글_목록은_오래된_것부터_쪽으로_나누고_지운_댓글은_글_없이_남는다() throws Exception {
    Space s = space();
    String first = comment(s, s.member(), "하나");
    comment(s, s.curator(), "둘");
    comment(s, s.member(), "셋");
    api.delete(s.member(), COMMENT, s.ws(), s.issue(), first).andExpect(status().isOk());

    api.get(s.other(), "/workspaces/{ws}/issues/{n}/comments?size=2", s.ws(), s.issue()).andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(3)).andExpect(jsonPath("$.totalPages").value(2))
        .andExpect(jsonPath("$.hasNext").value(true))
        .andExpect(jsonPath("$.items[0].id").value(first))
        .andExpect(jsonPath("$.items[0].deleted").value(true))
        .andExpect(jsonPath("$.items[0].notes").doesNotExist())
        .andExpect(jsonPath("$.items[1].notes").value("둘"))
        .andExpect(jsonPath("$.items[1].rowVersion").value(0));
    api.get(s.other(), "/workspaces/{ws}/issues/{n}/comments?size=2&page=1", s.ws(), s.issue())
        .andExpect(jsonPath("$.hasNext").value(false)).andExpect(jsonPath("$.items[0].notes").value("셋"));
  }

  @Test
  void DB_트리거가_이력_지우기와_지운_댓글_고치기_앞_글_없는_고치기를_막는다() throws Exception {
    Space s = space();
    String live = comment(s, s.member(), "살아 있는 글");
    String gone = comment(s, s.member(), "지울 글");
    api.delete(s.member(), COMMENT, s.ws(), s.issue(), gone).andExpect(status().isOk());

    assertThatThrownBy(() -> jdbc.update("DELETE FROM issue_journal WHERE id = ?::uuid", live))
        .hasMessageContaining("지울 수 없다");
    assertThatThrownBy(() -> jdbc.update("UPDATE issue_journal SET notes = '바꿈' WHERE id = ?::uuid", gone))
        .hasMessageContaining("지운 댓글은 바꿀 수 없다");
    assertThatThrownBy(() -> jdbc.update("UPDATE issue_journal SET notes_deleted_at = NULL, notes_deleted_by = NULL"
        + " WHERE id = ?::uuid", gone)).hasMessageContaining("지운 댓글은 바꿀 수 없다");
    // 앞 글을 남기지 않고 글을 바꾸지 못한다
    assertThatThrownBy(() -> jdbc.update("UPDATE issue_journal SET notes = '몰래', notes_row_version = 1,"
        + " notes_edited_at = now() WHERE id = ?::uuid", live)).hasMessageContaining("먼저 남겨야");
    assertThatThrownBy(() -> jdbc.update("UPDATE issue_journal SET notes = NULL WHERE id = ?::uuid", live))
        .hasMessageContaining("비울 수 없다");
    assertThatThrownBy(() -> jdbc.update("UPDATE issue_journal SET actor_name = 'x' WHERE id = ?::uuid", live))
        .hasMessageContaining("이력 줄은 바꿀 수 없다");
    // 옛 글은 쌓기만 한다
    api.patch(s.member(), COMMENT, edit("고친 글", 0), s.ws(), s.issue(), live).andExpect(status().isOk());
    assertThatThrownBy(() -> jdbc.update("UPDATE issue_comment_revision SET notes = 'x' WHERE journal_id = ?::uuid", live))
        .hasMessageContaining("추가만");
    assertThatThrownBy(() -> jdbc.update("DELETE FROM issue_comment_revision WHERE journal_id = ?::uuid", live))
        .hasMessageContaining("추가만");
  }
}
