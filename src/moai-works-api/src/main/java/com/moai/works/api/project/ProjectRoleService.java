package com.moai.works.api.project;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.moai.works.api.account.UserProfile;
import com.moai.works.api.account.UserProfileRepository;
import com.moai.works.api.audit.AuditService;
import com.moai.works.api.notification.NotificationKind;
import com.moai.works.api.notification.NotificationService;
import com.moai.works.api.common.Actor;
import com.moai.works.api.common.ApiException;
import com.moai.works.api.keycloak.KeycloakAdminClient;
import com.moai.works.api.keycloak.KeycloakOperationLog;
import com.moai.works.api.security.access.ProjectContext;
import com.moai.works.api.workspace.BuiltinRole;
import com.moai.works.api.workspace.MemberRole;
import com.moai.works.api.workspace.Workspace;
import com.moai.works.api.workspace.WorkspaceMember;
import com.moai.works.api.workspace.WorkspaceRepositories;

/**
 * 프로젝트 역할 주기 · 빼기 (design/04) — moai-wiki 의 위키 역할 설계와 같다.
 *
 * <p>정본은 Keycloak 조직 그룹 {@code /projects/<project id>/<role>}. 늘 Keycloak 에 먼저 쓰고(단계마다 기록해 끊겨도
 * 이어서 끝낸다) 그다음 사본 {@code project_role_binding} 을 고친다. 판정은 사본으로 한다.
 *
 * <p><b>권한 상승 방지:</b> 주거나 빼는 역할의 권한을 이 프로젝트에서 모두 가진 사람만 그 역할을 손댈 수 있다.
 */
@Service
public class ProjectRoleService {

  static final String PROJECTS_GROUP = "projects";

  private final ProjectRoleBindingRepository bindings;
  private final WorkspaceRepositories.Members members;
  private final WorkspaceRepositories.MemberRoles memberRoles;
  private final UserProfileRepository users;
  private final KeycloakAdminClient keycloak;
  private final KeycloakOperationLog operations;
  private final AuditService audit;
  private final NotificationService notifications;
  private final TransactionTemplate tx;
  private final Clock clock;

  public ProjectRoleService(ProjectRoleBindingRepository bindings, WorkspaceRepositories.Members members,
      WorkspaceRepositories.MemberRoles memberRoles,
      UserProfileRepository users, KeycloakAdminClient keycloak, KeycloakOperationLog operations, AuditService audit,
      NotificationService notifications, PlatformTransactionManager txManager, Clock clock) {
    this.bindings = bindings;
    this.members = members;
    this.memberRoles = memberRoles;
    this.users = users;
    this.keycloak = keycloak;
    this.operations = operations;
    this.audit = audit;
    this.notifications = notifications;
    this.tx = new TransactionTemplate(txManager);
    this.clock = clock;
  }

  /** 그 워크스페이스의 활성 소유자들 */
  private Set<UUID> workspaceOwners(UUID workspaceId) {
    Set<UUID> active = members.findByWorkspace(workspaceId).stream().filter(WorkspaceMember::isActive)
        .map(m -> m.getUserId()).collect(Collectors.toSet());
    return memberRoles.findByWorkspace(workspaceId).stream()
        .filter(r -> BuiltinRole.OWNER.key().equals(r.getRoleKey()) && active.contains(r.getUserId()))
        .map(MemberRole::getUserId).collect(Collectors.toCollection(java.util.LinkedHashSet::new));
  }

  /** 프로젝트 역할을 받은 사람 목록 (이름순) — 워크스페이스 소유자는 늘 프로젝트 관리자로 들어 있다 */
  /**
   * @param workspaceOwner 워크스페이스 소유자 — 모든 프로젝트에 프로젝트 관리자로 늘 들어 있고 빼거나 바꿀 수 없다
   */
  public record Holder(UUID userId, String username, String displayName, List<String> roles, boolean workspaceOwner) {
  }

  @Transactional(readOnly = true)
  public List<Holder> list(ProjectContext context) {
    Map<UUID, List<String>> byUser = new LinkedHashMap<>();
    for (ProjectRoleBinding b : bindings.findByProjectIdOrderByUserIdAscRoleAsc(context.projectId())) {
      byUser.computeIfAbsent(b.getUserId(), k -> new ArrayList<>()).add(b.getRole());
    }
    // 워크스페이스 소유자(활성 멤버)는 받은 역할과 상관없이 프로젝트 관리자로 늘 보인다
    Set<UUID> owners = workspaceOwners(context.workspaceId());
    owners.forEach(id -> byUser.put(id, new ArrayList<>(List.of(ProjectRole.ADMIN.key()))));
    Map<UUID, UserProfile> profiles = users.findAllById(byUser.keySet()).stream()
        .collect(Collectors.toMap(UserProfile::getId, u -> u));
    return byUser.entrySet().stream().filter(e -> profiles.containsKey(e.getKey()))
        .map(e -> {
          UserProfile u = profiles.get(e.getKey());
          return new Holder(u.getId(), u.getUsername(), u.getDisplayName(), sorted(e.getValue()),
              owners.contains(u.getId()));
        })
        .sorted(Comparator.comparing((Holder h) -> h.displayName() == null ? h.username() : h.displayName()))
        .toList();
  }

  /** 한 사람의 프로젝트 역할을 원하는 집합으로 바꾼다 (빈 집합 = 모두 뺀다). */
  public List<String> setRoles(ProjectContext by, UUID targetUserId, Set<String> wanted, Actor actor) {
    Workspace ws = by.workspace().workspace();
    WorkspaceMember member = members.find(ws.getId(), targetUserId)
        .orElseThrow(() -> ApiException.notFound("MEMBER_NOT_FOUND", "워크스페이스 멤버가 아닙니다."));
    if (!member.isActive()) {
      throw ApiException.notFound("MEMBER_NOT_FOUND", "활성 멤버에게만 프로젝트 역할을 줄 수 있습니다.");
    }
    if (workspaceOwners(ws.getId()).contains(targetUserId)) {
      throw ApiException.conflict("WORKSPACE_OWNER_FIXED",
          "워크스페이스 소유자는 모든 프로젝트의 프로젝트 관리자로 늘 들어 있어 빼거나 바꿀 수 없습니다.");
    }
    Set<ProjectRole> want = EnumSet.noneOf(ProjectRole.class);
    for (String key : wanted) {
      want.add(ProjectRole.ofKey(key)
          .orElseThrow(() -> ApiException.badRequest("UNKNOWN_PROJECT_ROLE", "프로젝트 역할이 없습니다: " + key)));
    }
    Set<ProjectRole> current = EnumSet.noneOf(ProjectRole.class);
    bindings.findByProjectIdAndUserId(by.projectId(), targetUserId)
        .forEach(b -> ProjectRole.ofKey(b.getRole()).ifPresent(current::add));
    Set<ProjectRole> adding = EnumSet.copyOf(want.isEmpty() ? EnumSet.noneOf(ProjectRole.class) : want);
    adding.removeAll(current);
    Set<ProjectRole> removing = current.isEmpty() ? EnumSet.noneOf(ProjectRole.class) : EnumSet.copyOf(current);
    removing.removeAll(want);
    if (adding.isEmpty() && removing.isEmpty()) {
      return keys(current);
    }
    // 권한 상승 방지 — 손대는 역할의 권한을 모두 가져야 한다
    for (ProjectRole role : union(adding, removing)) {
      if (!by.permissions().containsAll(role.permissions())) {
        throw ApiException.forbidden("ESCALATION_DENIED", "자기 권한 밖의 프로젝트 역할은 주거나 뺄 수 없습니다: " + role.key());
      }
    }

    UUID saga = UUID.randomUUID();
    String orgId = ws.getKeycloakOrgId();
    String projectKey = "prj:" + by.projectId();
    Map<ProjectRole, String> groupIds = new LinkedHashMap<>();
    String projectsGroup = operations.run(saga, "CREATE_ORG_GROUP", "ws:" + ws.getId() + ":group:projects", "{}",
        () -> keycloak.ensureOrganizationGroup(orgId, PROJECTS_GROUP));
    String projectGroup = operations.run(saga, "CREATE_ORG_GROUP", projectKey + ":group", "{}",
        () -> keycloak.ensureOrganizationSubGroup(orgId, projectsGroup, by.projectId().toString()));
    for (ProjectRole role : union(adding, removing)) {
      groupIds.put(role, operations.run(saga, "CREATE_ORG_GROUP", projectKey + ":group:" + role.key(), "{}",
          () -> keycloak.ensureOrganizationSubGroup(orgId, projectGroup, role.key())));
    }
    String keyBase = projectKey + ":roles:" + targetUserId + ":" + saga;
    for (ProjectRole role : adding) {
      operations.run(saga, "JOIN_GROUP", keyBase + ":join:" + role.key(), "{}",
          () -> keycloak.joinOrganizationGroup(orgId, groupIds.get(role), targetUserId.toString()));
    }
    for (ProjectRole role : removing) {
      operations.run(saga, "LEAVE_GROUP", keyBase + ":leave:" + role.key(), "{}",
          () -> keycloak.leaveOrganizationGroup(orgId, groupIds.get(role), targetUserId.toString()));
    }
    tx.executeWithoutResult(s -> {
      Instant now = Instant.now(clock);
      for (ProjectRole role : removing) {
        bindings.deleteById(new ProjectRoleBinding.Key(by.projectId(), targetUserId, role.key()));
      }
      for (ProjectRole role : adding) {
        bindings.save(new ProjectRoleBinding(by.projectId(), targetUserId, role, groupIds.get(role), actor.id(), now));
      }
      audit.record(actor, ws.getId(), "project.roles.update", "project", by.projectId().toString(),
          Map.of("project", by.project().getKey(), "user", targetUserId.toString(), "before", keys(current),
              "after", keys(want)));
      Map<String, Object> params = new java.util.LinkedHashMap<>();
      params.put("project", by.project().getKey());
      params.put("projectName", by.project().getName());
      params.put("roles", keys(want));
      notifications.notify(targetUserId, ws.getId(), NotificationKind.PROJECT_ROLES_CHANGED, by.projectId(), params,
          actor);
    });
    return keys(want);
  }

  private static Set<ProjectRole> union(Set<ProjectRole> a, Set<ProjectRole> b) {
    Set<ProjectRole> all = EnumSet.noneOf(ProjectRole.class);
    all.addAll(a);
    all.addAll(b);
    return all;
  }

  private static List<String> keys(Set<ProjectRole> roles) {
    return roles.stream().map(ProjectRole::key).collect(Collectors.toCollection(TreeSet::new)).stream().toList();
  }

  private static List<String> sorted(List<String> roles) {
    return new TreeSet<>(roles).stream().toList();
  }
}
