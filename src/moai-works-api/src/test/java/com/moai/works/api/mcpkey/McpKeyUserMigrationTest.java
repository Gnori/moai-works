package com.moai.works.api.mcpkey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

/**
 * V13 — MCP 키를 워크스페이스에서 떼어도 이미 받은 키는 그대로 남는다(ADR-029: "지금 키는 그대로 쓴다 — 묶음만 없앤다").
 *
 * <p>같은 DB 의 따로 둔 시험 스키마({@value #SCHEMA})를 V12 까지 만들고, 워크스페이스에 묶인 옛 키를 넣은 뒤 V13 을 적용한다.
 * 키 행(이름 · 해시 · 주인 · 기간)이 그대로이고, 워크스페이스 열만 없어지고, 지우기 · 발급 정보 바꾸기를 막는 트리거가 그대로인지 본다.
 * 그 행 모양의 키가 실제로 붙는지는 {@code McpKeyRuleTest.V13_전에_받은_키도_그대로_쓴다} 가 본다.
 */
@ApiTest
class McpKeyUserMigrationTest {

  private static final String SCHEMA = "moai_works_test_v13";

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

  @Test
  void V13_은_옛_키를_그대로_두고_워크스페이스_묶음만_없앤다() {
    dropSchema();
    flyway("12").migrate();
    JdbcTemplate jdbc = new JdbcTemplate(dataSource());
    UUID owner = UUID.randomUUID();
    UUID ws = UUID.randomUUID();
    UUID key = UUID.randomUUID();
    String hash = "a".repeat(64);
    jdbc.update("INSERT INTO " + SCHEMA + ".user_profile (id, username, email, display_name, account_status, origin, signed_up_at)"
        + " VALUES (?, 'v13-owner', 'v13-owner@test.invalid', '옛 키 주인', 'ACTIVE', 'SIGNUP', now())", owner);
    jdbc.update("INSERT INTO " + SCHEMA + ".workspace (id, slug, name, kind, visibility, join_policy, status, created_by,"
        + " created_at, updated_at) VALUES (?, 'workspace-abcdefghjkmn', '옛 워크스페이스', 'PERSONAL', 'PRIVATE', 'APPROVAL',"
        + " 'ACTIVE', ?, now(), now())", ws, owner);
    jdbc.update("INSERT INTO " + SCHEMA + ".mcp_api_key (id, name, key_prefix, key_hash, owner_name, created_at, expires_at,"
        + " last_used_at, workspace_id, owner_user_id, issued_ip, issued_user_agent)"
        + " VALUES (?, 'my-home-mac', 'mai-wks-Ab12', ?, '옛 키 주인', TIMESTAMPTZ '2026-11-01 00:00:00Z',"
        + " TIMESTAMPTZ '2027-01-30 00:00:00Z', TIMESTAMPTZ '2026-11-22 00:00:00Z', ?, ?, '127.0.0.1', 'curl/8')",
        key, hash, ws, owner);

    flyway(null).migrate();

    assertThat(jdbc.queryForObject("SELECT max(version::int) FROM " + SCHEMA + ".flyway_schema_history WHERE success",
        Integer.class)).isGreaterThanOrEqualTo(13);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.columns WHERE table_schema = ? AND"
        + " table_name = 'mcp_api_key' AND column_name = 'workspace_id'", Integer.class, SCHEMA)).isZero();
    Map<String, Object> row = jdbc.queryForMap("SELECT name, key_hash, owner_user_id, expires_at::text AS expires,"
        + " expire_reason FROM " + SCHEMA + ".mcp_api_key WHERE id = ?", key);
    assertThat(row.get("name")).isEqualTo("my-home-mac");
    assertThat(row.get("key_hash")).isEqualTo(hash);
    assertThat(row.get("owner_user_id")).isEqualTo(owner);
    assertThat((String) row.get("expires")).startsWith("2027-01-30");
    assertThat(row.get("expire_reason")).isNull();

    // 보호 트리거는 workspace_id 없이 다시 만들어져 그대로 막는다
    assertThatThrownBy(() -> jdbc.update("DELETE FROM " + SCHEMA + ".mcp_api_key WHERE id = ?", key))
        .hasMessageContaining("지울 수 없다");
    assertThatThrownBy(() -> jdbc.update("UPDATE " + SCHEMA + ".mcp_api_key SET key_hash = ? WHERE id = ?", "b".repeat(64), key))
        .hasMessageContaining("바꿀 수 없다");
    assertThatThrownBy(() -> jdbc.update("UPDATE " + SCHEMA + ".mcp_api_key SET expires_at = expires_at + interval '1 day'"
        + " WHERE id = ?", key)).hasMessageContaining("늘릴 수 없다");
    // 마지막 사용 시각은 그대로 쓸 수 있다
    assertThat(jdbc.update("UPDATE " + SCHEMA + ".mcp_api_key SET last_used_at = now() WHERE id = ?", key)).isEqualTo(1);
  }
}
