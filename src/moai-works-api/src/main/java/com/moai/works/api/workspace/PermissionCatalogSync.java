package com.moai.works.api.workspace;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.moai.works.api.keycloak.KeycloakAdminClient;
import com.moai.works.api.keycloak.KeycloakException;

/**
 * 권한 코드 카탈로그를 맞춘다 — 정본은 코드의 {@link Permission} enum.
 * <ol>
 *   <li>DB {@code permission} 표: 없는 코드는 넣고, enum 에서 빠진 코드는 retired 로 표시(행은 남김)</li>
 *   <li>Keycloak 클라이언트 역할과 대조: 빠진 코드가 있으면 경고 — 만드는 일은 설치 스크립트
 *       (infra/keycloak/ensure-realm.py) 몫이다. api 서비스 계정에 클라이언트 관리 권한을 주지 않기 위해서다.</li>
 *   <li>모두 있으면 {@link PermissionCatalogMigrator} 가 기존 워크스페이스의 역할을 새 카탈로그에 맞춘다
 *       (카탈로그가 바뀐 때에만)</li>
 * </ol>
 */
@Component
public class PermissionCatalogSync {

  private static final Logger log = LoggerFactory.getLogger(PermissionCatalogSync.class);

  private final JdbcTemplate jdbc;
  private final KeycloakAdminClient keycloak;
  private final PermissionCatalogMigrator migrator;
  private final Clock clock;

  public PermissionCatalogSync(JdbcTemplate jdbc, KeycloakAdminClient keycloak, PermissionCatalogMigrator migrator,
      Clock clock) {
    this.jdbc = jdbc;
    this.keycloak = keycloak;
    this.migrator = migrator;
    this.clock = clock;
  }

  /** DB 표는 컨텍스트가 뜨기 전에 맞춰야 한다(역할 저장이 FK 로 참조) — 빈 초기화 때 실행. */
  @jakarta.annotation.PostConstruct
  public void syncDatabase() {
    Instant now = Instant.now(clock);
    for (Permission p : Permission.values()) {
      jdbc.update("""
          INSERT INTO permission (code, description, owner_only) VALUES (?, ?, ?)
          ON CONFLICT (code) DO UPDATE SET owner_only = EXCLUDED.owner_only, retired_at = NULL
          """, p.code(), p.code(), p.ownerOnly());
    }
    List<String> codes = Permission.all().stream().map(Permission::code).toList();
    int retired = jdbc.update("UPDATE permission SET retired_at = ? WHERE retired_at IS NULL AND NOT (code = ANY (?))",
        java.sql.Timestamp.from(now), codes.toArray(String[]::new));
    if (retired > 0) {
      log.warn("카탈로그에서 빠진 권한 코드 {}개를 retired 로 표시", retired);
    }
  }

  @EventListener(ApplicationReadyEvent.class)
  public void verifyKeycloak() {
    try {
      Set<String> defined = new TreeSet<>(keycloak.definedPermissions());
      Set<String> missing = new TreeSet<>();
      Permission.all().forEach(p -> {
        if (!defined.contains(p.code())) {
          missing.add(p.code());
        }
      });
      if (missing.isEmpty()) {
        log.info("권한 코드 {}개가 Keycloak 클라이언트 역할과 일치", Permission.values().length);
        // 카탈로그가 바뀌었으면 이미 있는 워크스페이스의 역할을 새 카탈로그에 맞춘다
        migrator.applyIfChanged();
      } else {
        log.error("Keycloak 에 없는 권한 코드 {} — infra/keycloak/ensure-realm.py 를 실행하십시오. "
            + "그때까지 기존 워크스페이스의 역할은 옛 권한 그대로다", missing);
      }
    } catch (KeycloakException e) {
      log.error("Keycloak 권한 코드 대조 실패: {}", e.getMessage());
    }
  }
}
