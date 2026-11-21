package com.moai.works.api.workspace;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.moai.works.api.audit.AuditService;
import com.moai.works.api.common.Actor;
import com.moai.works.api.common.ApiException;
import com.moai.works.api.keycloak.KeycloakAdminClient;
import com.moai.works.api.keycloak.KeycloakOperationLog;
import com.moai.works.api.security.access.WorkspaceContext;

/**
 * 사용자 정의 역할 (design/03 §10) — 워크스페이스가 권한 코드를 골라 만든다. 기본 역할 3개는 바꿀 수 없다.
 *
 * <p>정본은 Keycloak 조직 그룹 {@code /roles/custom-<id>} 와 그 그룹의 클라이언트 역할 매핑, 그다음 사본(DB).
 *
 * <p><b>권한 상승 방지</b>
 * <ul>
 *   <li>역할에 넣는 권한 코드는 만드는 사람이 <b>모두 가진</b> 것이어야 한다</li>
 *   <li>소유자 전용 코드(소유권 이전 · 삭제)는 넣을 수 없다</li>
 *   <li>이미 있는 역할을 고치거나 지우려면, 그 역할의 지금 권한도 모두 가져야 한다(더 큰 역할을 줄일 수 없다)</li>
 * </ul>
 */
@Service
public class RoleService {

  private static final String ROLES_GROUP = "roles";
  private static final char[] ALPHABET = "abcdefghjkmnpqrstvwxyz23456789".toCharArray();
  private static final SecureRandom RANDOM = new SecureRandom();

  private final WorkspaceRepositories.Roles roles;
  private final WorkspaceRepositories.MemberRoles memberRoles;
  private final WorkspaceRepositories.Workspaces workspaces;
  private final KeycloakAdminClient keycloak;
  private final KeycloakOperationLog operations;
  private final AuditService audit;
  private final TransactionTemplate tx;
  private final Clock clock;

  public RoleService(WorkspaceRepositories.Roles roles, WorkspaceRepositories.MemberRoles memberRoles,
      WorkspaceRepositories.Workspaces workspaces, KeycloakAdminClient keycloak, KeycloakOperationLog operations,
      AuditService audit, PlatformTransactionManager txManager, Clock clock) {
    this.roles = roles;
    this.memberRoles = memberRoles;
    this.workspaces = workspaces;
    this.keycloak = keycloak;
    this.operations = operations;
    this.audit = audit;
    this.tx = new TransactionTemplate(txManager);
    this.clock = clock;
  }

  public WorkspaceViews.Role create(WorkspaceContext by, String name, String description, Set<String> permissionCodes,
      Actor actor) {
    Set<String> codes = validate(by, permissionCodes);
    Workspace ws = by.workspace();
    String key = "custom-" + randomId();
    UUID saga = UUID.randomUUID();
    String opKey = "ws:" + ws.getId() + ":role:" + key;
    String rolesGroup = operations.run(saga, "CREATE_ORG_GROUP", opKey + ":parent", "{}",
        () -> keycloak.ensureOrganizationGroup(ws.getKeycloakOrgId(), ROLES_GROUP));
    String groupId = operations.run(saga, "CREATE_ORG_GROUP", opKey + ":group", "{}",
        () -> keycloak.ensureOrganizationSubGroup(ws.getKeycloakOrgId(), rolesGroup, key));
    operations.run(saga, "MAP_CLIENT_ROLES", opKey + ":perm", "{\"count\":" + codes.size() + "}",
        () -> keycloak.setGroupPermissions(ws.getKeycloakOrgId(), groupId, codes));
    WorkspaceRole role = tx.execute(s -> {
      WorkspaceRole r = roles.save(WorkspaceRole.custom(ws.getId(), key, name.trim(), blankToNull(description), codes,
          groupId, Instant.now(clock)));
      audit.record(actor, ws.getId(), "role.create", "role", key,
          Map.of("name", name.trim(), "permissions", new TreeSet<>(codes)));
      return r;
    });
    return WorkspaceViews.Role.of(role, 0);
  }

  public WorkspaceViews.Role update(WorkspaceContext by, String key, String name, String description,
      Set<String> permissionCodes, Actor actor) {
    WorkspaceRole role = customRole(by, key);
    requireHoldsAll(by, role.getPermissions());
    Set<String> codes = validate(by, permissionCodes);
    Workspace ws = by.workspace();
    UUID saga = UUID.randomUUID();
    operations.run(saga, "MAP_CLIENT_ROLES", "ws:" + ws.getId() + ":role:" + key + ":perm:" + saga,
        "{\"count\":" + codes.size() + "}",
        () -> keycloak.setGroupPermissions(ws.getKeycloakOrgId(), role.getKeycloakGroupId(), codes));
    WorkspaceRole updated = tx.execute(s -> {
      WorkspaceRole r = roles.findById(role.getId()).orElseThrow();
      Set<String> before = new TreeSet<>(r.getPermissions());
      r.update(name.trim(), blankToNull(description), codes, Instant.now(clock));
      audit.record(actor, ws.getId(), "role.update", "role", key,
          Map.of("name", name.trim(), "before", before, "after", new TreeSet<>(codes)));
      return r;
    });
    return WorkspaceViews.Role.of(updated, memberRoles.countWithRole(ws.getId(), key));
  }

  public void delete(WorkspaceContext by, String key, Actor actor) {
    WorkspaceRole role = customRole(by, key);
    requireHoldsAll(by, role.getPermissions());
    Workspace ws = workspaces.findById(by.workspaceId()).orElseThrow();
    long holders = memberRoles.countWithRole(ws.getId(), key);
    if (holders > 0) {
      throw ApiException.conflict("ROLE_IN_USE", "이 역할을 가진 멤버가 " + holders + "명 있습니다. 먼저 역할을 바꾸십시오.");
    }
    if (key.equals(ws.getDefaultRoleKey())) {
      throw ApiException.conflict("ROLE_IS_DEFAULT", "새 멤버의 기본 역할입니다. 설정에서 기본 역할을 먼저 바꾸십시오.");
    }
    UUID saga = UUID.randomUUID();
    operations.run(saga, "DELETE_ORG_GROUP", "ws:" + ws.getId() + ":role:" + key + ":delete", "{}",
        () -> keycloak.deleteOrganizationGroup(ws.getKeycloakOrgId(), role.getKeycloakGroupId()));
    tx.executeWithoutResult(s -> {
      roles.deleteById(role.getId());
      audit.record(actor, ws.getId(), "role.delete", "role", key,
          Map.of("name", role.getName(), "permissions", new TreeSet<>(role.getPermissions())));
    });
  }

  /** 권한 코드 확인 — 있는 코드, 소유자 전용 아님, 만드는 사람이 모두 가짐. 최소 1개. */
  private Set<String> validate(WorkspaceContext by, Set<String> permissionCodes) {
    if (permissionCodes == null || permissionCodes.isEmpty()) {
      throw ApiException.badRequest("PERMISSION_REQUIRED_IN_ROLE", "권한을 하나 이상 고르십시오.");
    }
    Set<String> codes = new TreeSet<>();
    for (String code : permissionCodes) {
      Permission p = Permission.ofCode(code)
          .orElseThrow(() -> ApiException.badRequest("UNKNOWN_PERMISSION", "없는 권한 코드입니다: " + code));
      if (p.ownerOnly()) {
        throw ApiException.badRequest("OWNER_ONLY_PERMISSION", "소유자 전용 권한은 역할에 넣을 수 없습니다: " + code);
      }
      codes.add(p.code());
    }
    requireHoldsAll(by, codes);
    return codes;
  }

  private static void requireHoldsAll(WorkspaceContext by, Set<String> codes) {
    Set<String> mine = by.permissions().stream().map(Permission::code).collect(Collectors.toSet());
    Set<String> missing = new TreeSet<>(codes);
    missing.removeAll(mine);
    if (!missing.isEmpty()) {
      throw ApiException.forbidden("ESCALATION_DENIED", "자기에게 없는 권한은 역할에 넣거나 뺄 수 없습니다: " + missing);
    }
  }

  private WorkspaceRole customRole(WorkspaceContext by, String key) {
    WorkspaceRole role = roles.findByWorkspaceIdAndRoleKey(by.workspaceId(), key)
        .orElseThrow(() -> ApiException.notFound("ROLE_NOT_FOUND", "역할이 없습니다: " + key));
    if (role.isBuiltin()) {
      throw ApiException.forbidden("BUILTIN_ROLE", "기본 역할은 바꾸거나 지울 수 없습니다.");
    }
    return role;
  }

  private static String randomId() {
    StringBuilder sb = new StringBuilder(10);
    for (int i = 0; i < 10; i++) {
      sb.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
    }
    return sb.toString();
  }

  private static String blankToNull(String s) {
    return s == null || s.isBlank() ? null : s.trim();
  }
}
