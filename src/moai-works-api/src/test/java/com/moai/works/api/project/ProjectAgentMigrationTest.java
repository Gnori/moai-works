package com.moai.works.api.project;

import static org.assertj.core.api.Assertions.assertThat;

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
 * V29 — 지금 일감에 쓰인 세션 이름을 프로젝트마다 에이전트 멤버(AUTO)로 채운다(2026-11-25, 일감 #285).
 *
 * <p>따로 둔 시험 스키마를 V28 까지 만들고 옛 데이터를 넣은 뒤 V29 를 적용한다. 대소문자만 다른 이름은 하나(가장 많이 쓴 글자), 주인은 그 이름의 일감에
 * MCP 로 마지막으로 손댄 사람 · 없으면 가장 최근에 만든 일감의 만든 사람, 지운 일감에만 쓰인 이름은 넣지 않는다, 만든 때는 처음 보인 때.
 */
@ApiTest
class ProjectAgentMigrationTest {

  private static final String SCHEMA = TestInfrastructure.TEST_SCHEMA + "_v29";

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

  private UUID user(JdbcTemplate jdbc, String name) {
    UUID id = UUID.randomUUID();
    jdbc.update("INSERT INTO " + t("user_profile") + " (id, username, email, display_name, account_status, origin, signed_up_at)"
        + " VALUES (?, ?, ?, ?, 'ACTIVE', 'SIGNUP', now())", id, "v29-" + name, "v29-" + name + "@test.invalid", name);
    return id;
  }

  private UUID issue(JdbcTemplate jdbc, UUID ws, UUID project, int number, String session, UUID creator, int minute,
      boolean deleted) {
    UUID id = UUID.randomUUID();
    jdbc.update("INSERT INTO " + t("issue") + " (id, workspace_id, project_id, number, issue_type, title, status, manager_id,"
        + " created_by, created_at, updated_at, deleted_at, agent_session, revision_no, row_version)"
        + " VALUES (?, ?, ?, ?, 'feature', '옛 일감', 'NEW', ?, ?, ?, ?, ?, ?, 1, 1)", id, ws, project, number, creator, creator,
        at(minute), at(minute + 1), deleted ? at(minute + 2) : null, session);
    return id;
  }

  private void journal(JdbcTemplate jdbc, UUID issue, UUID actor, String via, int minute) {
    jdbc.update("INSERT INTO " + t("issue_journal") + " (id, issue_id, actor_id, actor_type, actor_name, via, created_at, changes)"
        + " VALUES (?, ?, ?, 'USER', '옛 사람', ?, ?, '[]'::jsonb)", UUID.randomUUID(), issue, actor, via, at(minute));
  }

  @Test
  void V29_는_쓰인_세션_이름을_프로젝트마다_하나씩_채우고_주인을_이력에서_고른다() {
    dropSchema();
    flyway("28").migrate();
    JdbcTemplate jdbc = new JdbcTemplate(dataSource());
    UUID owner = user(jdbc, "owner");
    UUID agentKey = user(jdbc, "agent-key");
    UUID laterKey = user(jdbc, "later-key");
    UUID webPerson = user(jdbc, "web-person");
    UUID ws = UUID.randomUUID();
    jdbc.update("INSERT INTO " + t("workspace") + " (id, slug, name, kind, visibility, join_policy, status, created_by, created_at,"
        + " updated_at) VALUES (?, 'workspace-v29abcdefghj', '옛 워크스페이스', 'TEAM', 'PUBLIC', 'APPROVAL', 'ACTIVE', ?, now(), now())",
        ws, owner);
    UUID works = UUID.randomUUID();
    UUID wiki = UUID.randomUUID();
    for (Object[] p : List.of(new Object[] {works, "works"}, new Object[] {wiki, "wiki"})) {
      jdbc.update("INSERT INTO " + t("project") + " (id, workspace_id, key, name, visibility, status, created_by, created_at,"
          + " updated_at) VALUES (?, ?, ?, ?, 'WORKSPACE', 'ACTIVE', ?, now(), now())", p[0], ws, p[1], p[1], owner);
    }
    // works: "moai-works" 둘 · "Moai-Works" 하나(같은 에이전트 — 많이 쓴 글자), MCP 이력은 agent-key(먼저) · later-key(나중)
    UUID w1 = issue(jdbc, ws, works, 1, "moai-works", owner, 10, false);
    UUID w2 = issue(jdbc, ws, works, 2, "Moai-Works", owner, 5, false);
    UUID w3 = issue(jdbc, ws, works, 3, "moai-works", owner, 20, false);
    journal(jdbc, w1, agentKey, "MCP", 30);
    journal(jdbc, w2, laterKey, "MCP", 40);
    journal(jdbc, w3, owner, "WEB", 50);   // 웹 손질은 주인으로 보지 않는다
    // works: 웹에서만 적은 이름 — 가장 최근에 만든 일감의 만든 사람
    issue(jdbc, ws, works, 4, "web-only", owner, 1, false);
    UUID web2 = issue(jdbc, ws, works, 5, "web-only", webPerson, 2, false);
    journal(jdbc, web2, webPerson, "WEB", 60);
    // works: 지운 일감에만 쓰인 이름은 넣지 않는다
    issue(jdbc, ws, works, 6, "gone-only", owner, 3, true);
    // wiki: 같은 이름이라도 프로젝트마다 따로
    UUID k1 = issue(jdbc, ws, wiki, 7, "moai-works", webPerson, 7, false);
    journal(jdbc, k1, agentKey, "MCP", 70);
    issue(jdbc, ws, wiki, 8, null, owner, 8, false);

    flyway(null).migrate();

    List<Map<String, Object>> rows = jdbc.queryForList("SELECT a.project_id, a.name, a.owner_user_id, a.source, a.active,"
        + " a.created_at, a.created_by, a.description FROM " + t("project_agent") + " a ORDER BY a.project_id = ? DESC, a.name", works);
    assertThat(rows).hasSize(3);
    Map<String, Object> worksAgent = rows.stream().filter(r -> r.get("project_id").equals(works) && "moai-works".equals(r.get("name")))
        .findFirst().orElseThrow();
    assertThat(worksAgent.get("owner_user_id")).isEqualTo(laterKey);
    assertThat(worksAgent.get("source")).isEqualTo("AUTO");
    assertThat(worksAgent.get("active")).isEqualTo(true);
    assertThat(worksAgent.get("created_by")).isNull();
    assertThat(worksAgent.get("description")).isNull();
    assertThat(((Timestamp) worksAgent.get("created_at")).toInstant()).isEqualTo(at(5).toInstant());
    Map<String, Object> webOnly = rows.stream().filter(r -> "web-only".equals(r.get("name"))).findFirst().orElseThrow();
    assertThat(webOnly.get("owner_user_id")).isEqualTo(webPerson);
    Map<String, Object> wikiAgent = rows.stream().filter(r -> r.get("project_id").equals(wiki)).findFirst().orElseThrow();
    assertThat(wikiAgent.get("name")).isEqualTo("moai-works");
    assertThat(wikiAgent.get("owner_user_id")).isEqualTo(agentKey);
    assertThat(rows).noneMatch(r -> "gone-only".equals(r.get("name")) || "Moai-Works".equals(r.get("name")));
  }
}
