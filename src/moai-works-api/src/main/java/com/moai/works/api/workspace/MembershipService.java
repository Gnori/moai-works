package com.moai.works.api.workspace;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
import com.moai.works.api.mcpkey.McpKeyService;
import com.moai.works.api.project.ProjectRoleBindingRepository;
import com.moai.works.api.security.access.WorkspaceContext;

/**
 * 멤버십과 역할 바꾸기 — 정본은 Keycloak(조직 멤버 · 조직 그룹 가입), 그다음 사본(DB).
 *
 * <p><b>권한 상승 방지 규칙</b> (SeedSiteKeeper 에서 본 위험을 막는다)
 * <ul>
 *   <li>역할을 주거나 뺏으려면 {@code member.role.assign} 이 있어야 하고, 그 역할의 권한 코드를 <b>모두 가진</b> 사람만 할 수 있다</li>
 *   <li>소유자 역할을 주고 뺏는 일은 소유자({@code workspace.ownership.transfer})만</li>
 *   <li>소유자를 가진 멤버의 역할 변경 · 제거 · 정지는 소유자만</li>
 *   <li>활성 소유자는 항상 1명 이상 — 마지막 소유자는 빼거나 내보낼 수 없다</li>
 *   <li>멤버는 역할이 최소 1개</li>
 * </ul>
 */
@Service
public class MembershipService {

  private final WorkspaceRepositories.Roles roles;
  private final WorkspaceRepositories.Members members;
  private final WorkspaceRepositories.MemberRoles memberRoles;
  private final WorkspaceAccessService access;
  private final KeycloakAdminClient keycloak;
  private final KeycloakOperationLog operations;
  private final AuditService audit;
  private final ProjectRoleBindingRepository projectRoles;
  private final McpKeyService mcpKeys;
  private final TransactionTemplate tx;
  private final Clock clock;

  public MembershipService(WorkspaceRepositories.Roles roles, WorkspaceRepositories.Members members,
      WorkspaceRepositories.MemberRoles memberRoles, WorkspaceAccessService access, KeycloakAdminClient keycloak,
      KeycloakOperationLog operations, AuditService audit, ProjectRoleBindingRepository projectRoles,
      McpKeyService mcpKeys, PlatformTransactionManager txManager, Clock clock) {
    this.roles = roles;
    this.members = members;
    this.memberRoles = memberRoles;
    this.access = access;
    this.keycloak = keycloak;
    this.operations = operations;
    this.audit = audit;
    this.projectRoles = projectRoles;
    this.mcpKeys = mcpKeys;
    this.tx = new TransactionTemplate(txManager);
    this.clock = clock;
  }

  /**
   * 멤버로 넣는다 (가입 승인 · 자동 가입 · 초대 수락). 이미 멤버면 409.
   *
   * @param saga 묶음 id (가입 신청 id 등)
   */
  public void addMember(Workspace ws, UUID userId, String roleKey, WorkspaceMember.JoinedVia via, UUID saga,
      Actor actor) {
    WorkspaceRole role = roles.findByWorkspaceIdAndRoleKey(ws.getId(), roleKey)
        .orElseThrow(() -> ApiException.badRequest("ROLE_NOT_FOUND", "역할이 없습니다: " + roleKey));
    if (members.find(ws.getId(), userId).isPresent()) {
      throw ApiException.conflict("ALREADY_MEMBER", "이미 멤버입니다.");
    }
    String key = "ws:" + ws.getId() + ":member:" + userId + ":" + saga;
    operations.run(saga, "ADD_MEMBER", key + ":add", "{}",
        () -> keycloak.addOrganizationMember(ws.getKeycloakOrgId(), userId.toString()));
    operations.run(saga, "JOIN_GROUP", key + ":join:" + roleKey, "{}",
        () -> keycloak.joinOrganizationGroup(ws.getKeycloakOrgId(), role.getKeycloakGroupId(), userId.toString()));
    tx.executeWithoutResult(s -> {
      Instant now = Instant.now(clock);
      members.saveAndFlush(new WorkspaceMember(ws.getId(), userId, via, now));
      memberRoles.save(new MemberRole(ws.getId(), userId, roleKey, actor.id(), now));
      audit.record(actor, ws.getId(), "member.add", "user", userId.toString(),
          Map.of("role", roleKey, "via", via.name()));
    });
  }

  /** 멤버의 역할을 원하는 집합으로 바꾼다 (더하고 뺀다). */
  public Set<String> setRoles(WorkspaceContext by, UUID targetUserId, Set<String> wanted, Actor actor) {
    Workspace ws = by.workspace();
    if (wanted.isEmpty()) {
      throw ApiException.badRequest("ROLE_REQUIRED", "역할을 하나 이상 지정하십시오.");
    }
    WorkspaceMember target = members.find(ws.getId(), targetUserId)
        .orElseThrow(() -> ApiException.notFound("MEMBER_NOT_FOUND", "멤버가 아닙니다."));
    Map<String, WorkspaceRole> all = roles.findByWorkspaceIdOrderByBuiltinDescRoleKeyAsc(ws.getId()).stream()
        .collect(Collectors.toMap(WorkspaceRole::getRoleKey, r -> r, (a, b) -> a, LinkedHashMap::new));
    for (String key : wanted) {
      if (!all.containsKey(key)) {
        throw ApiException.badRequest("ROLE_NOT_FOUND", "역할이 없습니다: " + key);
      }
    }
    Set<String> current = memberRoles.find(ws.getId(), targetUserId).stream().map(MemberRole::getRoleKey)
        .collect(Collectors.toCollection(TreeSet::new));
    Set<String> adding = new TreeSet<>(wanted);
    adding.removeAll(current);
    Set<String> removing = new TreeSet<>(current);
    removing.removeAll(wanted);
    if (adding.isEmpty() && removing.isEmpty()) {
      return current;
    }

    // 권한 상승 방지
    requireCanManageMember(by, current);
    Set<String> touched = new HashSet<>(adding);
    touched.addAll(removing);
    for (String key : touched) {
      requireCanGrant(by, all.get(key));
    }
    if (removing.contains(BuiltinRole.OWNER.key()) && target.isActive()
        && memberRoles.countActiveWithRole(ws.getId(), BuiltinRole.OWNER.key()) <= 1) {
      throw ApiException.conflict("LAST_OWNER", "마지막 소유자의 소유자 역할은 뺄 수 없습니다.");
    }

    UUID saga = UUID.randomUUID();
    String keyBase = "ws:" + ws.getId() + ":roles:" + targetUserId + ":" + saga;
    for (String key : adding) {
      operations.run(saga, "JOIN_GROUP", keyBase + ":join:" + key, "{}", () -> keycloak.joinOrganizationGroup(
          ws.getKeycloakOrgId(), all.get(key).getKeycloakGroupId(), targetUserId.toString()));
    }
    for (String key : removing) {
      operations.run(saga, "LEAVE_GROUP", keyBase + ":leave:" + key, "{}", () -> keycloak.leaveOrganizationGroup(
          ws.getKeycloakOrgId(), all.get(key).getKeycloakGroupId(), targetUserId.toString()));
    }
    tx.executeWithoutResult(s -> {
      Instant now = Instant.now(clock);
      for (String key : removing) {
        memberRoles.deleteById(new MemberRole.Id(ws.getId(), targetUserId, key));
      }
      for (String key : adding) {
        memberRoles.save(new MemberRole(ws.getId(), targetUserId, key, actor.id(), now));
      }
      audit.record(actor, ws.getId(), "member.roles.update", "user", targetUserId.toString(),
          Map.of("before", current, "after", new TreeSet<>(wanted)));
    });
    return new TreeSet<>(wanted);
  }

  /** 멤버를 내보낸다 (가입 취소). Keycloak 조직에서 빼고 사본을 지운다. */
  public void removeMember(WorkspaceContext by, UUID targetUserId, Actor actor) {
    Workspace ws = by.workspace();
    if (targetUserId.equals(by.userId())) {
      throw ApiException.badRequest("USE_LEAVE", "자기 자신은 '나가기'로 탈퇴하십시오.");
    }
    members.find(ws.getId(), targetUserId).orElseThrow(() -> ApiException.notFound("MEMBER_NOT_FOUND", "멤버가 아닙니다."));
    Set<String> current = memberRoles.find(ws.getId(), targetUserId).stream().map(MemberRole::getRoleKey)
        .collect(Collectors.toSet());
    requireCanManageMember(by, current);
    detach(ws, targetUserId, current, "member.remove", actor);
  }

  /** 본인이 나간다. 개인 워크스페이스와 마지막 소유자는 나갈 수 없다. */
  public void leave(WorkspaceContext self, Actor actor) {
    Workspace ws = self.workspace();
    if (ws.getKind() == Workspace.Kind.PERSONAL) {
      throw ApiException.badRequest("PERSONAL_WORKSPACE", "개인 워크스페이스는 나갈 수 없습니다.");
    }
    detach(ws, self.userId(), self.roleKeys(), "member.leave", actor);
  }

  private void detach(Workspace ws, UUID userId, Set<String> currentRoles, String action, Actor actor) {
    if (currentRoles.contains(BuiltinRole.OWNER.key())
        && memberRoles.countActiveWithRole(ws.getId(), BuiltinRole.OWNER.key()) <= 1) {
      throw ApiException.conflict("LAST_OWNER", "마지막 소유자는 나가거나 내보낼 수 없습니다. 소유자를 먼저 지정하십시오.");
    }
    Map<String, WorkspaceRole> all = roles.findByWorkspaceIdOrderByBuiltinDescRoleKeyAsc(ws.getId()).stream()
        .collect(Collectors.toMap(WorkspaceRole::getRoleKey, r -> r));
    UUID saga = UUID.randomUUID();
    String keyBase = "ws:" + ws.getId() + ":detach:" + userId + ":" + saga;
    for (String key : currentRoles) {
      WorkspaceRole role = all.get(key);
      if (role != null) {
        operations.run(saga, "LEAVE_GROUP", keyBase + ":leave:" + key, "{}",
            () -> keycloak.leaveOrganizationGroup(ws.getKeycloakOrgId(), role.getKeycloakGroupId(), userId.toString()));
      }
    }
    operations.run(saga, "REMOVE_MEMBER", keyBase + ":remove", "{}",
        () -> keycloak.removeOrganizationMember(ws.getKeycloakOrgId(), userId.toString()));
    tx.executeWithoutResult(s -> {
      members.deleteById(new WorkspaceMember.Id(ws.getId(), userId));
      // 프로젝트 역할 사본도 지운다 — Keycloak 의 그룹 소속은 조직에서 빠질 때 함께 빠진다
      projectRoles.deleteByUserInWorkspace(userId, ws.getId());
      // 그 사람의 이 워크스페이스 MCP 키도 만료시킨다(지우지 않는다) — 키는 멤버십 없이는 닿지 않지만 이력에 이유를 남긴다
      mcpKeys.expireForLeaving(ws.getId(), userId, actor);
      audit.record(actor, ws.getId(), action, "user", userId.toString(), Map.of("roles", new TreeSet<>(currentRoles)));
    });
  }

  /** 대상이 소유자면 소유자만 손댈 수 있다. */
  private static void requireCanManageMember(WorkspaceContext by, Set<String> targetRoles) {
    if (targetRoles.contains(BuiltinRole.OWNER.key()) && !by.isOwner()) {
      throw ApiException.forbidden("OWNER_PROTECTED", "소유자는 소유자만 바꿀 수 있습니다.");
    }
  }

  /** 이 역할을 주고 뺏을 수 있는가 — 역할의 권한을 모두 가진 사람만. 소유자 역할은 소유권 이전 권한이 있어야. */
  private void requireCanGrant(WorkspaceContext by, WorkspaceRole role) {
    if (!by.has(Permission.MEMBER_ROLE_ASSIGN)) {
      throw ApiException.forbidden("PERMISSION_REQUIRED", "권한 member.role.assign 이(가) 필요합니다.");
    }
    if (role.getRoleKey().equals(BuiltinRole.OWNER.key()) && !by.has(Permission.WORKSPACE_OWNERSHIP_TRANSFER)) {
      throw ApiException.forbidden("OWNER_ONLY", "소유자 역할은 소유자만 주고 뺏을 수 있습니다.");
    }
    Set<String> mine = by.permissions().stream().map(Permission::code).collect(Collectors.toSet());
    Set<String> missing = new TreeSet<>(role.getPermissions());
    missing.removeAll(mine);
    if (!missing.isEmpty()) {
      throw ApiException.forbidden("ESCALATION_DENIED", "자기에게 없는 권한이 든 역할은 다룰 수 없습니다: " + missing);
    }
  }

  /** 역할을 줄 수 있는지 미리 확인 (가입 승인 때) */
  public void requireCanGrant(WorkspaceContext by, String roleKey) {
    WorkspaceRole role = roles.findByWorkspaceIdAndRoleKey(by.workspaceId(), roleKey)
        .orElseThrow(() -> ApiException.badRequest("ROLE_NOT_FOUND", "역할이 없습니다: " + roleKey));
    if (role.getRoleKey().equals(BuiltinRole.OWNER.key()) && !by.has(Permission.WORKSPACE_OWNERSHIP_TRANSFER)) {
      throw ApiException.forbidden("OWNER_ONLY", "소유자 역할은 소유자만 줄 수 있습니다.");
    }
    Set<String> mine = by.permissions().stream().map(Permission::code).collect(Collectors.toSet());
    if (!mine.containsAll(role.getPermissions())) {
      throw ApiException.forbidden("ESCALATION_DENIED", "자기에게 없는 권한이 든 역할은 줄 수 없습니다.");
    }
  }
}
