package com.moai.works.api.support;

import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import com.moai.works.api.config.MoaiWorksProperties;
import com.moai.works.api.security.SecurityConfiguration;

/**
 * 자동 테스트 공통 설정 ({@link ApiTest} 가 불러온다).
 * <ul>
 *   <li>DB — 시험 전용 스키마(기본 {@value #DEFAULT_TEST_SCHEMA})를 비우고 Flyway 로 다시 만든다. 스키마 이름이
 *       {@code moai_works_test} 또는 {@code moai_works_test_<이름>} 이 아니면 시작하지 않는다(개발 데이터가 있는 public 스키마를 지우는 사고를 막는다).
 *       여러 worktree 에서 함께 돌릴 때는 {@code MOAI_WORKS_TEST_DB_SCHEMA=moai_works_test_<이름>} 으로 서로 다른 스키마를 쓴다.</li>
 *   <li>Keycloak 관리 API — {@link FakeKeycloak}</li>
 *   <li>토큰 — 시험용 키로 서명한 것을, 운영과 같은 내용 검사(만료 · 발급자 · audience)로 확인한다</li>
 * </ul>
 * 모든 테스트 클래스가 같은 설정을 쓰므로 스프링 문맥과 DB 초기화는 한 번의 실행에 한 번만 일어난다.
 * 그래서 테스트는 서로 겹치지 않는 사람 · 워크스페이스를 새로 만들어 쓴다({@link TestApi}).
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestInfrastructure {

  public static final String DEFAULT_TEST_SCHEMA = "moai_works_test";
  /** 이번 실행의 시험 스키마 — application-test.yaml 과 같은 env(MOAI_WORKS_TEST_DB_SCHEMA)를 본다 */
  public static final String TEST_SCHEMA = testSchema();

  static String testSchema() {
    String schema = System.getenv("MOAI_WORKS_TEST_DB_SCHEMA");
    if (schema == null || schema.isBlank()) {
      return DEFAULT_TEST_SCHEMA;
    }
    if (!schema.matches("moai_works_test(_[a-z0-9]+)*")) {
      throw new IllegalStateException("MOAI_WORKS_TEST_DB_SCHEMA 는 moai_works_test 또는 moai_works_test_<이름> 이어야 한다: " + schema);
    }
    return schema;
  }

  @Bean
  FlywayMigrationStrategy recreateTestSchema() {
    return flyway -> {
      String[] schemas = flyway.getConfiguration().getSchemas();
      if (schemas.length != 1 || !TEST_SCHEMA.equals(schemas[0])) {
        throw new IllegalStateException("테스트는 스키마 " + TEST_SCHEMA + " 에서만 돈다. 지금 설정: "
            + java.util.Arrays.toString(schemas));
      }
      flyway.clean();
      flyway.migrate();
    };
  }

  /** 연결이 정말 시험 스키마를 보는지 한 번 더 확인한다. */
  @Bean
  TestSchemaGuard testSchemaGuard(JdbcTemplate jdbc) {
    String current = jdbc.queryForObject("select current_schema()", String.class);
    if (!TEST_SCHEMA.equals(current)) {
      throw new IllegalStateException("DB 연결의 스키마가 " + current + " 다. " + TEST_SCHEMA + " 여야 한다.");
    }
    return new TestSchemaGuard(current);
  }

  record TestSchemaGuard(String schema) {
  }

  @Bean
  @Primary
  FakeKeycloak fakeKeycloak(MoaiWorksProperties properties) {
    return new FakeKeycloak(properties);
  }

  /** 객체 저장소는 부르지 않는다 — 메모리에 둔다 */
  @Bean
  @Primary
  MemoryObjectStore memoryObjectStore() {
    return new MemoryObjectStore();
  }

  @Bean
  TestTokens testTokens(MoaiWorksProperties properties) {
    return new TestTokens(properties);
  }

  @Bean
  @Primary
  JwtDecoder testJwtDecoder(TestTokens tokens, MoaiWorksProperties properties) {
    NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(tokens.publicKey()).build();
    decoder.setJwtValidator(SecurityConfiguration.tokenValidator(properties.keycloak()));
    return decoder;
  }

  @Bean
  TestApi testApi(MockMvc mvc, TestTokens tokens, FakeKeycloak keycloak) {
    return new TestApi(mvc, tokens, keycloak);
  }
}
