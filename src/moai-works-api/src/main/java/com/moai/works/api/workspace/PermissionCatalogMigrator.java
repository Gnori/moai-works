package com.moai.works.api.workspace;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.moai.works.api.keycloak.KeycloakAdminClient;

/**
 * 권한 코드 카탈로그나 기본 역할의 권한이 바뀌었을 때, 이미 있는 워크스페이스의 역할을 새 카탈로그에 맞춘다.
 *
 * <p>카탈로그는 플랫폼이 정하는 것이라(워크스페이스는 코드를 못 만든다) 이 경우에만 api 가 주도해서 양쪽을 고친다:
 * <ol>
 *   <li>기본 역할 — 권한을 {@link BuiltinRole} 정의와 같게</li>
 *   <li>사용자 정의 역할 — 카탈로그에서 빠진 코드를 뺀다(이름이 바뀐 코드는 Flyway 가 사본에서 먼저 옮긴다)</li>
 * </ol>
 * 순서는 늘 Keycloak(조직 그룹의 클라이언트 역할 매핑) 먼저, 그다음 DB 사본이다.
 *
 * <p>워크스페이스마다 Keycloak 을 부르므로 기동할 때마다 하지 않는다. 카탈로그의 지문을 {@code platform_setting} 에 두고,
 * 지문이 달라졌을 때만 돈다. 중간에 실패하면 지문을 저장하지 않아 다음 기동이 처음부터 다시 한다(몇 번 돌려도 결과가 같다).
 * 워크스페이스가 아주 많아지면 기동 경로에서 빼서 배치로 옮긴다.
 */
@Component
public class PermissionCatalogMigrator {

  private static final Logger log = LoggerFactory.getLogger(PermissionCatalogMigrator.class);
  static final String APPLIED_KEY = "permission.catalog.applied";

  private final WorkspaceRepositories.Workspaces workspaces;
  private final WorkspaceRepositories.Roles roles;
  private final KeycloakAdminClient keycloak;
  private final JdbcTemplate jdbc;
  private final TransactionTemplate tx;
  private final Clock clock;

  public PermissionCatalogMigrator(WorkspaceRepositories.Workspaces workspaces, WorkspaceRepositories.Roles roles,
      KeycloakAdminClient keycloak, JdbcTemplate jdbc, PlatformTransactionManager txManager, Clock clock) {
    this.workspaces = workspaces;
    this.roles = roles;
    this.keycloak = keycloak;
    this.jdbc = jdbc;
    this.tx = new TransactionTemplate(txManager);
    this.clock = clock;
  }

  /**
   * 카탈로그가 마지막으로 맞춘 것과 다르면 모든 워크스페이스의 역할을 맞춘다.
   *
   * @return 고친 역할 수 (지문이 같아 건너뛰었으면 -1)
   */
  public int applyIfChanged() {
    String current = fingerprint();
    List<String> applied = jdbc.queryForList("SELECT value #>> '{}' FROM platform_setting WHERE key = ?", String.class,
        APPLIED_KEY);
    if (!applied.isEmpty() && current.equals(applied.get(0))) {
      return -1;
    }
    int changed = 0;
    int checked = 0;
    for (Workspace ws : workspaces.findAll()) {
      if (ws.getKeycloakOrgId() == null) {
        continue;   // 만들다 끊긴 워크스페이스 — 이어서 만들 때 새 정의로 만들어진다
      }
      for (WorkspaceRole role : roles.findByWorkspaceIdOrderByBuiltinDescRoleKeyAsc(ws.getId())) {
        checked++;
        if (apply(ws, role)) {
          changed++;
        }
      }
    }
    jdbc.update("""
        INSERT INTO platform_setting (key, value, updated_at) VALUES (?, to_jsonb(?::text), ?)
        ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value, updated_at = EXCLUDED.updated_at,
            row_version = platform_setting.row_version + 1
        """, APPLIED_KEY, current, java.sql.Timestamp.from(Instant.now(clock)));
    log.info("권한 카탈로그 적용: 역할 {}개 확인, {}개 고침 (지문 {})", checked, changed, current.substring(0, 12));
    return changed;
  }

  private boolean apply(Workspace ws, WorkspaceRole role) {
    Set<String> desired = desired(role);
    if (role.getKeycloakGroupId() == null) {
      log.warn("역할 {}({})에 Keycloak 그룹이 없어 건너뜀", role.getRoleKey(), ws.getSlug());
      return false;
    }
    keycloak.setGroupPermissions(ws.getKeycloakOrgId(), role.getKeycloakGroupId(), desired);
    if (desired.equals(role.getPermissions())) {
      return false;
    }
    Set<String> before = new TreeSet<>(role.getPermissions());
    tx.executeWithoutResult(s -> roles.findById(role.getId()).orElseThrow().syncPermissions(desired, Instant.now(clock)));
    log.info("역할 {}({}) 권한을 카탈로그에 맞춤: {} → {}", role.getRoleKey(), ws.getSlug(), before, desired);
    return true;
  }

  /** 이 역할이 가져야 할 권한 코드 */
  private static Set<String> desired(WorkspaceRole role) {
    if (role.isBuiltin()) {
      return BuiltinRole.ofKey(role.getRoleKey())
          .map(b -> b.permissions().stream().map(Permission::code).collect(Collectors.toCollection(TreeSet::new)))
          .orElseGet(() -> new TreeSet<>(role.getPermissions()));
    }
    return role.getPermissions().stream().filter(code -> Permission.ofCode(code).isPresent())
        .collect(Collectors.toCollection(TreeSet::new));
  }

  /** 카탈로그(권한 코드 · 소유자 전용 여부 · 기본 역할의 권한)의 지문 */
  static String fingerprint() {
    StringBuilder sb = new StringBuilder();
    for (Permission p : Permission.values()) {
      sb.append(p.code()).append(p.ownerOnly() ? "!" : "").append(',');
    }
    for (BuiltinRole role : BuiltinRole.values()) {
      sb.append('|').append(role.key()).append('=')
          .append(role.permissions().stream().map(Permission::code).sorted().collect(Collectors.joining(",")));
    }
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
          .digest(sb.toString().getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
