package com.moai.works.api.issue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import com.moai.works.api.support.ApiTest;
import com.moai.works.api.support.TestInfrastructure;

/**
 * V32 — 옛 첨부 가운데 MCP 로 올린 것에 그 일감의 지금 agentSession 을 채운다(2026-11-26, 일감 #312).
 *
 * <p>따로 둔 시험 스키마를 V31 까지 만들고 옛 데이터를 넣은 뒤 V32 를 적용한다. 웹 첨부 · 세션이 없는 일감의 첨부는 없음(NULL)으로 남고, 지운 ·
 * 정리한 첨부도 채우며, 다른 값은 그대로, 적용한 뒤에는 보호 트리거가 다시 켜져 이름을 바꾸지 못한다(지움 · 정리 표시는 그대로 된다).
 */
@ApiTest
class AttachmentAgentSessionMigrationTest {

  private static final String SCHEMA = TestInfrastructure.TEST_SCHEMA + "_v32";

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

  private UUID attachment(JdbcTemplate jdbc, UUID ws, UUID project, UUID issue, UUID by, String via) {
    UUID id = UUID.randomUUID();
    jdbc.update("INSERT INTO " + t("issue_attachment") + " (id, workspace_id, project_id, issue_id, file_name, content_type,"
        + " size_bytes, sha256, image, storage_key, uploaded_by, via, created_at)"
        + " VALUES (?, ?, ?, ?, '옛 첨부.txt', 'text/plain', 3, ?, false, ?, ?, ?, ?)", id, ws, project, issue, "a".repeat(64),
        "ws/" + ws + "/p/" + project + "/2026/11/" + id, by, via, at(40));
    return id;
  }

  private String session(JdbcTemplate jdbc, UUID id) {
    return jdbc.queryForObject("SELECT agent_session FROM " + t("issue_attachment") + " WHERE id = ?", String.class, id);
  }

  @Test
  void V32_는_MCP_첨부에만_일감의_지금_세션을_채우고_웹_첨부는_비워_둔다() {
    dropSchema();
    flyway("31").migrate();
    JdbcTemplate jdbc = new JdbcTemplate(dataSource());
    UUID person = UUID.randomUUID();
    jdbc.update("INSERT INTO " + t("user_profile") + " (id, username, email, display_name, account_status, origin, signed_up_at)"
        + " VALUES (?, 'v32-person', 'v32-person@test.invalid', '옛 사람', 'ACTIVE', 'SIGNUP', now())", person);
    UUID ws = UUID.randomUUID();
    jdbc.update("INSERT INTO " + t("workspace") + " (id, slug, name, kind, visibility, join_policy, status, created_by, created_at,"
        + " updated_at) VALUES (?, 'workspace-v32abcdefghj', '옛 워크스페이스', 'TEAM', 'PUBLIC', 'APPROVAL', 'ACTIVE', ?, now(), now())",
        ws, person);
    UUID project = UUID.randomUUID();
    jdbc.update("INSERT INTO " + t("project") + " (id, workspace_id, key, name, visibility, status, created_by, created_at,"
        + " updated_at) VALUES (?, ?, 'works', 'works', 'WORKSPACE', 'ACTIVE', ?, now(), now())", project, ws, person);
    UUID withSession = issue(jdbc, ws, project, 1, "moai-works", person);
    UUID noSession = issue(jdbc, ws, project, 2, null, person);
    UUID mcp = attachment(jdbc, ws, project, withSession, person, "MCP");
    UUID web = attachment(jdbc, ws, project, withSession, person, "WEB");
    UUID mcpNoSession = attachment(jdbc, ws, project, noSession, person, "MCP");
    // 지운 첨부 · 정리한 첨부도 채운다(트리거를 끄고 채운다)
    UUID deleted = attachment(jdbc, ws, project, withSession, person, "MCP");
    jdbc.update("UPDATE " + t("issue_attachment") + " SET deleted_at = now(), deleted_by = ? WHERE id = ?", person, deleted);
    UUID purged = attachment(jdbc, ws, project, withSession, person, "MCP");
    jdbc.update("UPDATE " + t("issue_attachment") + " SET deleted_at = now(), deleted_by = ? WHERE id = ?", person, purged);
    jdbc.update("UPDATE " + t("issue_attachment") + " SET purged_at = now() WHERE id = ?", purged);

    flyway(null).migrate();

    assertThat(session(jdbc, mcp)).isEqualTo("moai-works");
    assertThat(session(jdbc, deleted)).isEqualTo("moai-works");
    assertThat(session(jdbc, purged)).isEqualTo("moai-works");
    assertThat(session(jdbc, web)).isNull();
    assertThat(session(jdbc, mcpNoSession)).isNull();
    // 다른 값은 그대로
    Map<String, Object> row = jdbc.queryForMap("SELECT file_name, via, uploaded_by, deleted_at FROM " + t("issue_attachment")
        + " WHERE id = ?", mcp);
    assertThat(row).containsEntry("file_name", "옛 첨부.txt").containsEntry("via", "MCP").containsEntry("uploaded_by", person)
        .containsEntry("deleted_at", null);

    // 트리거는 다시 켜져 있다 — 채운 이름도 바꾸지 못한다
    assertThatThrownBy(() -> jdbc.update("UPDATE " + t("issue_attachment") + " SET agent_session = NULL WHERE id = ?", mcp))
        .hasMessageContaining("지움 표시만 한 번 할 수 있다");
    assertThatThrownBy(() -> jdbc.update("UPDATE " + t("issue_attachment") + " SET agent_session = 'x' WHERE id = ?", web))
        .hasMessageContaining("지움 표시만 한 번 할 수 있다");
    // 지움 표시 · 정리 표시는 그대로 된다
    jdbc.update("UPDATE " + t("issue_attachment") + " SET deleted_at = now(), deleted_by = ? WHERE id = ?", person, mcp);
    jdbc.update("UPDATE " + t("issue_attachment") + " SET purged_at = now() WHERE id = ?", mcp);
    assertThat(session(jdbc, mcp)).isEqualTo("moai-works");
    // 일감을 옮길 때 프로젝트와 함께 이름을 바꾸지 못한다 — 트리거가 issue 를 이름만으로 찾으므로 시험 스키마를 search_path 로 둔 한 연결에서
    UUID other = UUID.randomUUID();
    jdbc.update("INSERT INTO " + t("project") + " (id, workspace_id, key, name, visibility, status, created_by, created_at,"
        + " updated_at) VALUES (?, ?, 'other', 'other', 'WORKSPACE', 'ACTIVE', ?, now(), now())", other, ws, person);
    jdbc.execute((ConnectionCallback<Void>) c -> {
      try (Statement st = c.createStatement()) {
        st.execute("SET search_path TO " + SCHEMA);
        st.execute("UPDATE issue SET project_id = '" + other + "' WHERE id = '" + noSession + "'");
        assertThatThrownBy(() -> st.execute("UPDATE issue_attachment SET project_id = '" + other + "', agent_session = 'x'"
            + " WHERE id = '" + mcpNoSession + "'")).hasMessageContaining("프로젝트를 바꿀 때 다른 값을 함께 바꿀 수 없다");
        assertThat(st.executeUpdate("UPDATE issue_attachment SET project_id = '" + other + "' WHERE id = '" + mcpNoSession + "'"))
            .isEqualTo(1);
      }
      return null;
    });
  }
}
