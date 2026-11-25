package com.moai.works.api.issue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
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
 * V31 — 옛 이력 줄 · 작업 시간 기록 가운데 MCP 로 쓴 것에 그 일감의 지금 agentSession 을 채운다(2026-11-26, 일감 #304).
 *
 * <p>따로 둔 시험 스키마를 V30 까지 만들고 옛 데이터를 넣은 뒤 V31 을 적용한다. 웹 줄 · 세션이 없는 일감의 줄은 없음(NULL)으로 남고, 다른 값은 그대로,
 * 적용한 뒤에는 보호 트리거가 다시 켜져 이름을 바꾸지 못한다.
 */
@ApiTest
class HistoryAgentSessionMigrationTest {

  private static final String SCHEMA = TestInfrastructure.TEST_SCHEMA + "_v31";

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

  private String t(String table) {
    return SCHEMA + "." + table;
  }

  private static Timestamp at(int minute) {
    return Timestamp.from(Instant.parse("2026-11-20T00:00:00Z").plusSeconds(minute * 60L));
  }

  private UUID issue(JdbcTemplate jdbc, UUID ws, UUID project, int number, String session, UUID creator) {
    UUID id = UUID.randomUUID();
    jdbc.update("INSERT INTO " + t("issue") + " (id, workspace_id, project_id, number, issue_type, title, status, manager_id,"
        + " created_by, created_at, updated_at, agent_session, revision_no, row_version)"
        + " VALUES (?, ?, ?, ?, 'feature', '옛 일감', 'NEW', ?, ?, ?, ?, ?, 1, 1)", id, ws, project, number, creator, creator,
        at(number), at(number), session);
    return id;
  }

  private UUID journal(JdbcTemplate jdbc, UUID issue, UUID actor, String via, String notes) {
    UUID id = UUID.randomUUID();
    jdbc.update("INSERT INTO " + t("issue_journal") + " (id, issue_id, actor_id, actor_type, actor_name, via, created_at, notes,"
        + " changes) VALUES (?, ?, ?, 'USER', '옛 사람', ?, ?, ?, '[]'::jsonb)", id, issue, actor, via, at(30), notes);
    return id;
  }

  private UUID workLog(JdbcTemplate jdbc, UUID issue, UUID user, String via) {
    UUID id = UUID.randomUUID();
    jdbc.update("INSERT INTO " + t("issue_work_log") + " (id, issue_id, user_id, work_date, hours, note, via, by_human, created_at)"
        + " VALUES (?, ?, ?, DATE '2026-11-20', 0.5, '옛 기록', ?, false, ?)", id, issue, user, via, at(40));
    return id;
  }

  private String session(JdbcTemplate jdbc, String table, UUID id) {
    return jdbc.queryForObject("SELECT agent_session FROM " + t(table) + " WHERE id = ?", String.class, id);
  }

  @Test
  void V31_은_MCP_줄에만_일감의_지금_세션을_채우고_웹_줄은_비워_둔다() {
    dropSchema();
    flyway("30").migrate();
    JdbcTemplate jdbc = new JdbcTemplate(dataSource());
    UUID person = UUID.randomUUID();
    jdbc.update("INSERT INTO " + t("user_profile") + " (id, username, email, display_name, account_status, origin, signed_up_at)"
        + " VALUES (?, 'v31-person', 'v31-person@test.invalid', '옛 사람', 'ACTIVE', 'SIGNUP', now())", person);
    UUID ws = UUID.randomUUID();
    jdbc.update("INSERT INTO " + t("workspace") + " (id, slug, name, kind, visibility, join_policy, status, created_by, created_at,"
        + " updated_at) VALUES (?, 'workspace-v32abcdefghj', '옛 워크스페이스', 'TEAM', 'PUBLIC', 'APPROVAL', 'ACTIVE', ?, now(), now())",
        ws, person);
    UUID project = UUID.randomUUID();
    jdbc.update("INSERT INTO " + t("project") + " (id, workspace_id, key, name, visibility, status, created_by, created_at,"
        + " updated_at) VALUES (?, ?, 'works', 'works', 'WORKSPACE', 'ACTIVE', ?, now(), now())", project, ws, person);
    UUID withSession = issue(jdbc, ws, project, 1, "moai-works", person);
    UUID noSession = issue(jdbc, ws, project, 2, null, person);
    UUID mcpComment = journal(jdbc, withSession, person, "MCP", "Agent 가 단 댓글");
    UUID mcpChange = journal(jdbc, withSession, person, "MCP", null);
    UUID webComment = journal(jdbc, withSession, person, "WEB", "사람이 단 댓글");
    UUID mcpNoSession = journal(jdbc, noSession, person, "MCP", "세션 없는 일감");
    UUID mcpLog = workLog(jdbc, withSession, person, "MCP");
    UUID webLog = workLog(jdbc, withSession, person, "WEB");
    UUID mcpLogNoSession = workLog(jdbc, noSession, person, "MCP");
    // 지운 기록도 채운다(트리거를 끄고 채운다)
    UUID deletedLog = workLog(jdbc, withSession, person, "MCP");
    jdbc.update("UPDATE " + t("issue_work_log") + " SET deleted_at = now(), deleted_by = ? WHERE id = ?", person, deletedLog);

    flyway(null).migrate();

    assertThat(session(jdbc, "issue_journal", mcpComment)).isEqualTo("moai-works");
    assertThat(session(jdbc, "issue_journal", mcpChange)).isEqualTo("moai-works");
    assertThat(session(jdbc, "issue_journal", webComment)).isNull();
    assertThat(session(jdbc, "issue_journal", mcpNoSession)).isNull();
    assertThat(session(jdbc, "issue_work_log", mcpLog)).isEqualTo("moai-works");
    assertThat(session(jdbc, "issue_work_log", deletedLog)).isEqualTo("moai-works");
    assertThat(session(jdbc, "issue_work_log", webLog)).isNull();
    assertThat(session(jdbc, "issue_work_log", mcpLogNoSession)).isNull();
    // 다른 값은 그대로
    Map<String, Object> row = jdbc.queryForMap("SELECT notes, via, actor_name, notes_row_version FROM " + t("issue_journal")
        + " WHERE id = ?", mcpComment);
    assertThat(row).containsEntry("notes", "Agent 가 단 댓글").containsEntry("via", "MCP").containsEntry("actor_name", "옛 사람")
        .containsEntry("notes_row_version", 0);

    // 트리거는 다시 켜져 있다 — 채운 이름도 바꾸지 못한다
    assertThatThrownBy(() -> jdbc.update("UPDATE " + t("issue_journal") + " SET agent_session = NULL WHERE id = ?", mcpComment))
        .hasMessageContaining("이력 줄은 바꿀 수 없다");
    assertThatThrownBy(() -> jdbc.update("UPDATE " + t("issue_work_log") + " SET agent_session = 'x', deleted_at = now(),"
        + " deleted_by = ? WHERE id = ?", person, mcpLog)).hasMessageContaining("말고는 바꿀 수 없다");
  }
}
