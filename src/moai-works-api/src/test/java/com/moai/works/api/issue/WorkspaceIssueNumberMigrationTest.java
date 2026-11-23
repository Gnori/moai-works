package com.moai.works.api.issue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import com.moai.works.api.support.ApiTest;
import com.moai.works.api.support.TestInfrastructure;

/**
 * V23 — 프로젝트 안 번호를 워크스페이스 번호로 다시 매긴다(2026-11-24 사용자 결정, ADR-034 · moai-works 일감 #29).
 *
 * <p>따로 둔 시험 스키마를 V21 까지 만들고, 두 프로젝트가 모두 #1 · #2 를 가진 옛 데이터를 넣은 뒤 V23 을 적용한다.
 * 만든 때 순서로 다시 매겼는지, 가리키는 번호(판의 상위 번호 · 이력 · 알림 · 접근 기록 · 첨부 티켓)가 같은 일감을 가리키는지,
 * 설명 · 댓글의 #번호를 그 프로젝트 번호로 읽어 바꿨는지(코드 · 링크는 그대로), 옛 판은 그대로인지, 바꾼 기록 · 시스템 이력이 남는지 본다.
 */
@ApiTest
class WorkspaceIssueNumberMigrationTest {

  private static final String SCHEMA = TestInfrastructure.TEST_SCHEMA + "_v23";

  @Autowired
  Environment env;

  private DriverManagerDataSource dataSource() {
    return new DriverManagerDataSource(env.getRequiredProperty("spring.datasource.url"),
        env.getRequiredProperty("spring.datasource.username"), env.getRequiredProperty("spring.datasource.password"));
  }

  private Flyway flyway(String target) {
    var config = Flyway.configure().dataSource(dataSource()).schemas(SCHEMA).defaultSchema(SCHEMA)
        .locations("classpath:db/migration").cleanDisabled(false);
    return (target == null ? config : config.target(target)).load();
  }

  @AfterEach
  void dropSchema() {
    new JdbcTemplate(dataSource()).execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
  }

  private JdbcTemplate jdbc() {
    return new JdbcTemplate(dataSource());
  }

  private String t(String table) {
    return SCHEMA + "." + table;
  }

  private static Timestamp at(int minute) {
    return Timestamp.from(Instant.parse("2026-11-22T00:00:00Z").plusSeconds(minute * 60L));
  }

  private UUID issue(JdbcTemplate jdbc, UUID project, int number, int minute, String title, String description, UUID parent,
      UUID owner, boolean deleted) {
    UUID id = UUID.randomUUID();
    jdbc.update("INSERT INTO " + t("issue") + " (id, project_id, number, issue_type, title, description, status, manager_id,"
        + " parent_id, created_by, created_at, updated_at, deleted_at, revision_no, row_version)"
        + " VALUES (?, ?, ?, 'feature', ?, ?, 'NEW', ?, ?, ?, ?, ?, ?, 1, 3)", id, project, number, title, description, owner,
        parent, owner, at(minute), at(minute), deleted ? at(minute + 1) : null);
    // 1판 — 그때의 상위 번호(같은 프로젝트의 옛 번호)
    Integer parentNumber = parent == null ? null
        : jdbc.queryForObject("SELECT number FROM " + t("issue") + " WHERE id = ?", Integer.class, parent);
    jdbc.update("INSERT INTO " + t("issue_revision") + " (id, issue_id, revision_no, issue_type, title, description, status,"
        + " manager_id, parent_number, actor_id, actor_type, actor_name, via, created_at)"
        + " VALUES (?, ?, 1, 'feature', ?, ?, 'NEW', ?, ?, ?, 'USER', '옛 사람', 'WEB', ?)", UUID.randomUUID(), id, title,
        description, owner, parentNumber, owner, at(minute));
    return id;
  }

  private UUID journal(JdbcTemplate jdbc, UUID issue, String notes, String changes, UUID owner, int minute) {
    UUID id = UUID.randomUUID();
    jdbc.update("INSERT INTO " + t("issue_journal") + " (id, issue_id, actor_id, actor_type, actor_name, via, created_at, notes,"
        + " changes) VALUES (?, ?, ?, 'USER', '옛 사람', 'WEB', ?, ?, ?::jsonb)", id, issue, owner, at(minute), notes, changes);
    return id;
  }

  @Test
  void V23_은_워크스페이스마다_만든_때_순서로_다시_매기고_가리키는_번호와_글_속_번호를_같은_일감으로_잇는다() {
    dropSchema();
    flyway("21").migrate();
    JdbcTemplate jdbc = jdbc();
    UUID owner = UUID.randomUUID();
    UUID ws = UUID.randomUUID();
    UUID other = UUID.randomUUID();
    jdbc.update("INSERT INTO " + t("user_profile") + " (id, username, email, display_name, account_status, origin, signed_up_at)"
        + " VALUES (?, 'v23-owner', 'v23-owner@test.invalid', '옛 주인', 'ACTIVE', 'SIGNUP', now())", owner);
    for (Object[] w : List.of(new Object[] {ws, "workspace-abcdefghjkmn"}, new Object[] {other, "workspace-bcdefghjkmnp"})) {
      jdbc.update("INSERT INTO " + t("workspace") + " (id, slug, name, kind, visibility, join_policy, status, created_by,"
          + " created_at, updated_at) VALUES (?, ?, '옛 워크스페이스', 'TEAM', 'PUBLIC', 'APPROVAL', 'ACTIVE', ?, now(), now())",
          w[0], w[1], owner);
    }
    UUID web = UUID.randomUUID();
    UUID works = UUID.randomUUID();
    UUID notes = UUID.randomUUID();
    for (Object[] p : List.of(new Object[] {web, ws, "web", 3}, new Object[] {works, ws, "works", 4},
        new Object[] {notes, other, "notes", 1})) {
      jdbc.update("INSERT INTO " + t("project") + " (id, workspace_id, key, name, visibility, status, issue_seq, created_by,"
          + " created_at, updated_at) VALUES (?, ?, ?, ?, 'WORKSPACE', 'ACTIVE', ?, ?, now(), now())", p[0], p[1], p[2], p[2],
          p[3], owner);
    }
    // 옛 번호: web #1 · #2 · #3, works #1 · #2 · #3 · #4(지움), notes #1. 만든 때: web#1 < works#1 < web#2 < works#2 < works#3
    //   < web#3 = works#4(같은 때 — 옛 번호 순). → 새 번호 1 · 2 · 3 · 4 · 5 · 6 · 7, 다른 워크스페이스는 1
    UUID web1 = issue(jdbc, web, 1, 1, "웹 첫 일", "", null, owner, false);
    UUID works1 = issue(jdbc, works, 1, 2, "웍스 첫 일", "웍스 #2 · #3 을 본다", null, owner, false);
    UUID web2 = issue(jdbc, web, 2, 3, "웹 둘째 일", "상위는 #1, 웍스 일은 아니다", web1, owner, false);
    String description = "#1 의 하위. #3 은 이어서, #9 는 없다.\n`#1` 은 코드 · [#1](https://example.com/#1) 링크\n"
        + "```\n#1 펜스\n```\n끝 #1";
    UUID works2 = issue(jdbc, works, 2, 4, "웍스 둘째 일", description, works1, owner, false);
    UUID works3 = issue(jdbc, works, 3, 5, "웍스 셋째 일", "", null, owner, false);
    UUID web3 = issue(jdbc, web, 3, 6, "웹 셋째 일", "", null, owner, false);
    UUID works4 = issue(jdbc, works, 4, 6, "웍스 지운 일", "#1", null, owner, true);
    UUID notes1 = issue(jdbc, notes, 1, 0, "다른 곳 일", "#1 자기", null, owner, false);
    // 이력: 상위 바꿈(웍스 #2 의 상위 → #1), 연결(웍스 #1 → 웍스 #3), 댓글(웹 #2 에 "#2 와 #1")
    journal(jdbc, works2, null, "[{\"field\":\"parent\",\"before\":null,\"after\":1}]", owner, 10);
    journal(jdbc, works1, null, "[{\"field\":\"link\",\"before\":null,\"after\":{\"type\":\"RELATES\",\"direction\":\"OUTGOING\","
        + "\"number\":3}}]", owner, 11);
    UUID comment = journal(jdbc, web2, "#2 와 #1 을 같이 본다 — `#2`", "[]", owner, 12);
    UUID deletedComment = journal(jdbc, web2, "지운 댓글 #2", "[]", owner, 13);
    jdbc.update("UPDATE " + t("issue_journal") + " SET notes_deleted_at = now(), notes_deleted_by = ? WHERE id = ?", owner,
        deletedComment);
    // 알림 — 웍스 #1 에 하위(웍스 #2)가 생김
    jdbc.update("INSERT INTO " + t("notification") + " (id, recipient_id, workspace_id, kind, subject_type, subject_id, params,"
        + " created_at, updated_at) VALUES (?, ?, ?, 'issue.child_added', 'issue', ?, ?::jsonb, now(), now())",
        UUID.randomUUID(), owner, ws, works1, "{\"project\":\"works\",\"number\":1,\"childNumber\":2,\"title\":\"웍스 첫 일\"}");
    // MCP 접근 기록 — 웍스 #2 읽기, 웍스 #1 → #3 연결
    for (Object[] row : List.of(new Object[] {"get_issue", 2, null}, new Object[] {"link_issues", 1, "3"})) {
      jdbc.update("INSERT INTO " + t("mcp_access_log") + " (id, occurred_at, duration_ms, entry, request_path, workspace_id,"
          + " mcp_method, tool, operation, project_key, issue_number, target_ref, outcome, http_status)"
          + " VALUES (?, now(), 1, 'ISSUE', '/mcp/issue', ?, 'tools/call', ?, 'READ', 'works', ?, ?, 'OK', 200)",
          UUID.randomUUID(), ws, row[0], row[1], row[2]);
    }
    // 첨부 티켓 — 웹 #2
    UUID key = UUID.randomUUID();
    jdbc.update("INSERT INTO " + t("mcp_api_key") + " (id, name, key_prefix, key_hash, owner_name, created_at, expires_at,"
        + " owner_user_id, issued_ip, issued_user_agent) VALUES (?, 'v23', 'mai-wks-v23a', ?, '옛 주인', now(),"
        + " now() + interval '1 day', ?, '127.0.0.1', 'test')", key, "c".repeat(64), owner);
    jdbc.update("INSERT INTO " + t("attachment_ticket") + " (id, token_hash, kind, mcp_key_id, owner_user_id, workspace_id,"
        + " project_id, issue_number, file_name, created_at, expires_at) VALUES (?, ?, 'UPLOAD', ?, ?, ?, ?, 2, 'a.png', now(),"
        + " now() + interval '10 minutes')", UUID.randomUUID(), "d".repeat(64), key, owner, ws, web);

    flyway(null).migrate();

    // ── 번호 ──
    Map<UUID, Integer> expected = Map.of(web1, 1, works1, 2, web2, 3, works2, 4, works3, 5, web3, 6, works4, 7, notes1, 1);
    expected.forEach((id, number) -> assertThat(jdbc.queryForObject("SELECT number FROM " + t("issue") + " WHERE id = ?",
        Integer.class, id)).as("새 번호 %s", id).isEqualTo(number));
    assertThat(jdbc.queryForObject("SELECT issue_seq FROM " + t("workspace") + " WHERE id = ?", Integer.class, ws)).isEqualTo(7);
    assertThat(jdbc.queryForObject("SELECT issue_seq FROM " + t("workspace") + " WHERE id = ?", Integer.class, other)).isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT workspace_id FROM " + t("issue") + " WHERE id = ?", UUID.class, works2)).isEqualTo(ws);
    // 일감의 row_version · 고친 때는 그대로
    assertThat(jdbc.queryForObject("SELECT row_version FROM " + t("issue") + " WHERE id = ?", Integer.class, works2)).isEqualTo(3);
    assertThat(jdbc.queryForObject("SELECT updated_at FROM " + t("issue") + " WHERE id = ?", Timestamp.class, works2))
        .isEqualTo(at(4));
    // 번호 바꾼 기록 — 옛 → 새, 일감마다
    List<Map<String, Object>> changes = jdbc.queryForList("SELECT project_key, old_number, new_number FROM "
        + t("issue_number_change") + " WHERE workspace_id = ? ORDER BY new_number", ws);
    assertThat(changes).extracting(m -> m.get("project_key") + "#" + m.get("old_number") + "→" + m.get("new_number"))
        .containsExactly("web#1→1", "works#1→2", "web#2→3", "works#2→4", "works#3→5", "web#3→6", "works#4→7");

    // ── 가리키는 번호 ──
    assertThat(jdbc.queryForObject("SELECT parent_number FROM " + t("issue_revision") + " WHERE issue_id = ? AND revision_no = 1",
        Integer.class, works2)).isEqualTo(2);
    assertThat(jdbc.queryForObject("SELECT parent_number FROM " + t("issue_revision") + " WHERE issue_id = ? AND revision_no = 1",
        Integer.class, web2)).isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT changes::text FROM " + t("issue_journal") + " WHERE issue_id = ? AND created_at = ?",
        String.class, works2, at(10))).contains("\"after\": 2");
    assertThat(jdbc.queryForObject("SELECT changes::text FROM " + t("issue_journal") + " WHERE issue_id = ? AND created_at = ?",
        String.class, works1, at(11))).contains("\"number\": 5");
    assertThat(jdbc.queryForObject("SELECT params::text FROM " + t("notification") + " WHERE subject_id = ?", String.class, works1))
        .contains("\"number\": 2").contains("\"childNumber\": 4");
    assertThat(jdbc.queryForList("SELECT issue_number || ':' || coalesce(target_ref, '') FROM " + t("mcp_access_log")
        + " ORDER BY tool", String.class)).containsExactly("4:", "2:5");
    assertThat(jdbc.queryForObject("SELECT issue_number FROM " + t("attachment_ticket"), Integer.class)).isEqualTo(3);

    // ── 글 속 #번호 — 그 일감의 프로젝트 번호로 읽는다. 코드 · 링크 · 모르는 번호는 그대로 ──
    assertThat(jdbc.queryForObject("SELECT description FROM " + t("issue") + " WHERE id = ?", String.class, works2))
        .isEqualTo("#2 의 하위. #5 은 이어서, #9 는 없다.\n`#1` 은 코드 · [#1](https://example.com/#1) 링크\n"
            + "```\n#1 펜스\n```\n끝 #2");
    assertThat(jdbc.queryForObject("SELECT description FROM " + t("issue") + " WHERE id = ?", String.class, works1))
        .isEqualTo("웍스 #4 · #5 을 본다");
    // 웹 #2 의 "#1" 은 웹 #1(새 번호도 1) — 그대로
    assertThat(jdbc.queryForObject("SELECT description FROM " + t("issue") + " WHERE id = ?", String.class, web2))
        .isEqualTo("상위는 #1, 웍스 일은 아니다");
    // 지운 일감 · 다른 워크스페이스(번호 그대로)는 건드리지 않는다
    assertThat(jdbc.queryForObject("SELECT description FROM " + t("issue") + " WHERE id = ?", String.class, works4)).isEqualTo("#1");
    assertThat(jdbc.queryForObject("SELECT notes FROM " + t("issue_journal") + " WHERE id = ?", String.class, comment))
        .isEqualTo("#3 와 #1 을 같이 본다 — `#2`");
    assertThat(jdbc.queryForObject("SELECT notes FROM " + t("issue_journal") + " WHERE id = ?", String.class, deletedComment))
        .isEqualTo("지운 댓글 #2");
    // 댓글 판 · 고친 때는 그대로(이 바꿈은 사람이 고친 것이 아니다)
    assertThat(jdbc.queryForMap("SELECT notes_row_version, notes_edited_at FROM " + t("issue_journal") + " WHERE id = ?", comment))
        .containsEntry("notes_row_version", 0).containsEntry("notes_edited_at", null);
    // 옛 판은 역사 — 1판의 설명은 그대로
    assertThat(jdbc.queryForObject("SELECT description FROM " + t("issue_revision") + " WHERE issue_id = ? AND revision_no = 1",
        String.class, works2)).isEqualTo(description);
    // 바꾸기 전 글이 남는다
    assertThat(jdbc.queryForList("SELECT kind || ':' || refs FROM " + t("issue_number_text_change") + " WHERE issue_id = ?",
        String.class, works2)).containsExactly("DESCRIPTION:3");
    assertThat(jdbc.queryForObject("SELECT comment_refs FROM " + t("issue_number_change") + " WHERE issue_id = ?", Integer.class,
        web2)).isEqualTo(1);

    // ── 시스템 이력 · 새 판 ──
    Map<String, Object> line = jdbc.queryForMap("SELECT actor_type, actor_name, via, changes::text AS changes, revision_no FROM "
        + t("issue_journal") + " WHERE issue_id = ? AND via = 'SYSTEM'", works2);
    assertThat(line).containsEntry("actor_type", "SERVICE").containsEntry("actor_name", "moai-works").containsEntry("revision_no", 2);
    assertThat((String) line.get("changes")).contains("\"field\": \"number\"").contains("\"project\": \"works\"")
        .contains("\"field\": \"description\"");
    Map<String, Object> revision = jdbc.queryForMap("SELECT revision_no, description, parent_number, via, project_id FROM "
        + t("issue_revision") + " WHERE issue_id = ? ORDER BY revision_no DESC LIMIT 1", works2);
    assertThat(revision).containsEntry("revision_no", 2).containsEntry("parent_number", 2).containsEntry("via", "SYSTEM")
        .containsEntry("project_id", works);
    assertThat(jdbc.queryForObject("SELECT revision_no FROM " + t("issue") + " WHERE id = ?", Integer.class, works2)).isEqualTo(2);
    // 번호가 그대로이고 설명도 그대로인 일감(웹 #1)에는 이력을 남기지 않는다. 번호만 바뀐 일감(웍스 #3)은 이력만, 판은 그대로
    assertThat(jdbc.queryForObject("SELECT count(*) FROM " + t("issue_journal") + " WHERE issue_id = ? AND via = 'SYSTEM'",
        Integer.class, web1)).isZero();
    assertThat(jdbc.queryForMap("SELECT revision_no FROM " + t("issue_journal") + " WHERE issue_id = ? AND via = 'SYSTEM'", works3))
        .containsEntry("revision_no", null);
    assertThat(jdbc.queryForObject("SELECT revision_no FROM " + t("issue") + " WHERE id = ?", Integer.class, works3)).isEqualTo(1);

    // ── 제약 · 보호 ──
    assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.columns WHERE table_schema = ? AND"
        + " table_name = 'project' AND column_name = 'issue_seq'", Integer.class, SCHEMA)).isZero();
    assertThatThrownBy(() -> jdbc.update("UPDATE " + t("issue") + " SET number = 1 WHERE id = ?", works2))
        .hasMessageContaining("ux_issue_workspace_number");
    // 프로젝트는 같은 워크스페이스여야 한다
    assertThatThrownBy(() -> jdbc.update("UPDATE " + t("issue") + " SET project_id = ? WHERE id = ?", notes, web3))
        .hasMessageContaining("fk_issue_project_same_workspace");
    // 보호 트리거는 다시 켜져 있다
    assertThatThrownBy(() -> jdbc.update("UPDATE " + t("issue_revision") + " SET title = 'x' WHERE issue_id = ?", works2))
        .hasMessageContaining("추가만");
    assertThatThrownBy(() -> jdbc.update("DELETE FROM " + t("mcp_access_log"))).hasMessageContaining("추가만");
    assertThatThrownBy(() -> jdbc.update("UPDATE " + t("issue_journal") + " SET changes = '[{\"field\": \"x\"}]'::jsonb WHERE id = ?", comment))
        .hasMessageContaining("바꿀 수 없다");
  }
}
