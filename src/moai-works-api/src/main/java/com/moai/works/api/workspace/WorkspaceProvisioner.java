package com.moai.works.api.workspace;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.moai.works.api.account.UserProfile;
import com.moai.works.api.account.UserProfileRepository;
import com.moai.works.api.audit.AuditService;
import com.moai.works.api.common.Actor;
import com.moai.works.api.common.ApiException;
import com.moai.works.api.keycloak.KeycloakAdminClient;
import com.moai.works.api.keycloak.KeycloakOperationLog;
import com.moai.works.api.project.ProjectService;

/**
 * 워크스페이스 만들기 (design/03 §8.2) — Keycloak 먼저, 사본은 그다음. 단계마다 기록해 끊겨도 이어서 끝낸다.
 *
 * <pre>
 * 1  DB    workspace 행 (PROVISIONING)                       ← 개인이면 계정에 바로 연결(같은 계정 동시 요청 차단)
 * 2  KC    조직 생성 (alias = 슬러그, 속성 moai.workspaceId)   ← 이미 있으면 속성으로 찾아 쓴다
 * 3  KC    조직 그룹 /roles 와 기본 역할 4개 + 권한 코드 매핑
 * 4  DB    workspace_role · role_permission 사본
 * 5  KC    만든 사람을 조직 멤버로, /roles/owner 에 가입
 * 6  DB    workspace_member · member_role 사본, 워크스페이스 ACTIVE, 감사
 * 7  DB    개인 워크스페이스면 첫 프로젝트 "내 프로젝트"
 * </pre>
 * 2~6 은 {@link #resume} 로 몇 번이든 다시 돌릴 수 있다(멱등).
 */
@Service
public class WorkspaceProvisioner {

  private static final Logger log = LoggerFactory.getLogger(WorkspaceProvisioner.class);
  static final String ORG_ATTRIBUTE = "moai.workspaceId";
  private static final String ROLES_GROUP = "roles";

  private final WorkspaceRepositories.Workspaces workspaces;
  private final WorkspaceRepositories.Roles roles;
  private final WorkspaceRepositories.Members members;
  private final WorkspaceRepositories.MemberRoles memberRoles;
  private final UserProfileRepository users;
  private final KeycloakAdminClient keycloak;
  private final KeycloakOperationLog operations;
  private final AuditService audit;
  private final ProjectService projects;
  private final TransactionTemplate tx;
  private final Clock clock;

  public WorkspaceProvisioner(WorkspaceRepositories.Workspaces workspaces, WorkspaceRepositories.Roles roles,
      WorkspaceRepositories.Members members, WorkspaceRepositories.MemberRoles memberRoles, UserProfileRepository users,
      KeycloakAdminClient keycloak, KeycloakOperationLog operations, AuditService audit, ProjectService projects,
      PlatformTransactionManager txManager, Clock clock) {
    this.workspaces = workspaces;
    this.roles = roles;
    this.members = members;
    this.memberRoles = memberRoles;
    this.users = users;
    this.keycloak = keycloak;
    this.operations = operations;
    this.audit = audit;
    this.projects = projects;
    this.tx = new TransactionTemplate(txManager);
    this.clock = clock;
  }

  /**
   * 계정의 개인 워크스페이스를 보장한다 — 없으면 만들고, 만들다 끊긴 것이면 이어서 끝낸다.
   *
   * @return 개인 워크스페이스 id
   */
  public UUID ensurePersonalWorkspace(UUID userId, Actor actor) {
    UUID workspaceId = tx.execute(s -> {
      UserProfile user = users.lockById(userId).orElseThrow();
      if (user.getPersonalWorkspaceId() != null) {
        return user.getPersonalWorkspaceId();
      }
      Instant now = Instant.now(clock);
      Workspace ws = Workspace.personal(newSlug(), user.getDisplayName() + "의 워크스페이스", userId, now);
      workspaces.saveAndFlush(ws);
      user.assignPersonalWorkspace(ws.getId());
      return ws.getId();
    });
    resume(workspaceId, actor);
    return workspaceId;
  }

  /**
   * 팀 워크스페이스를 만든다. 만든 사람이 소유자다. 주소(workspace-고유id)는 여기서 정한다.
   * 시스템 관리자가 허용한 계정만, 허용한 수까지 만든다({@code user_profile.team_workspace_limit}).
   */
  public Workspace createTeamWorkspace(UUID creatorId, String name, String description,
      Workspace.Visibility visibility, Workspace.JoinPolicy joinPolicy, Actor actor) {
    UUID workspaceId = tx.execute(s -> {
      // 계정을 잠그고 센다 — 같은 계정의 동시 요청이 한도를 넘기지 못한다
      int limit = users.lockById(creatorId).orElseThrow().getTeamWorkspaceLimit();
      if (limit == 0) {
        throw ApiException.forbidden("TEAM_WORKSPACE_NOT_ALLOWED",
            "팀 워크스페이스는 시스템 관리자가 허용한 계정만 만들 수 있습니다.");
      }
      if (workspaces.countTeamWorkspacesCreatedBy(creatorId) >= limit) {
        throw ApiException.forbidden("TEAM_WORKSPACE_LIMIT", "만들 수 있는 팀 워크스페이스 수(" + limit
            + "개)를 다 썼습니다. 더 필요하면 시스템 관리자에게 요청하십시오.");
      }
      Workspace ws = Workspace.team(newSlug(), name, description, visibility, joinPolicy, creatorId, Instant.now(clock));
      workspaces.saveAndFlush(ws);
      return ws.getId();
    });
    resume(workspaceId, actor);
    return workspaces.findById(workspaceId).orElseThrow();
  }

  /** PROVISIONING 워크스페이스를 끝까지 만든다. 이미 ACTIVE 면 아무 일도 하지 않는다. */
  public void resume(UUID workspaceId, Actor actor) {
    Workspace ws = workspaces.findById(workspaceId).orElseThrow();
    if (ws.getStatus() != Workspace.Status.PROVISIONING) {
      return;
    }
    UUID saga = ws.getId();
    String key = "ws:" + ws.getId() + ":";
    String creator = ws.getCreatedBy().toString();

    // 2. 조직
    String orgId = operations.run(saga, "CREATE_ORG", key + "org", "{\"alias\":\"" + ws.getSlug() + "\"}",
        () -> keycloak.findOrganizationByAttribute(ORG_ATTRIBUTE, ws.getId().toString())
            .orElseGet(() -> keycloak.createOrganization(ws.getName(), ws.getSlug(),
                Map.of(ORG_ATTRIBUTE, List.of(ws.getId().toString()), "moai.kind", List.of(ws.getKind().name())))));
    tx.executeWithoutResult(s -> workspaces.findById(ws.getId()).orElseThrow().linkKeycloakOrganization(orgId));

    // 3. 역할 그룹 + 권한 코드
    String rolesGroup = operations.run(saga, "CREATE_ORG_GROUP", key + "group:roles", "{}",
        () -> keycloak.ensureOrganizationGroup(orgId, ROLES_GROUP));
    Map<BuiltinRole, String> groupIds = new EnumMap<>(BuiltinRole.class);
    for (BuiltinRole role : BuiltinRole.values()) {
      String groupId = operations.run(saga, "CREATE_ORG_GROUP", key + "group:roles/" + role.key(), "{}",
          () -> keycloak.ensureOrganizationSubGroup(orgId, rolesGroup, role.key()));
      operations.run(saga, "MAP_CLIENT_ROLES", key + "perm:" + role.key(),
          "{\"count\":" + role.permissions().size() + "}",
          () -> keycloak.setGroupPermissions(orgId, groupId, role.permissions().stream().map(Permission::code).toList()));
      groupIds.put(role, groupId);
    }

    // 4. 역할 사본
    tx.executeWithoutResult(s -> {
      Instant now = Instant.now(clock);
      for (BuiltinRole role : BuiltinRole.values()) {
        if (roles.findByWorkspaceIdAndRoleKey(ws.getId(), role.key()).isEmpty()) {
          roles.save(WorkspaceRole.builtin(ws.getId(), role, groupIds.get(role), now));
        }
      }
    });

    // 5. 만든 사람 = 소유자
    operations.run(saga, "ADD_MEMBER", key + "member:" + creator, "{}",
        () -> keycloak.addOrganizationMember(orgId, creator));
    operations.run(saga, "JOIN_GROUP", key + "join:" + creator + ":owner", "{}",
        () -> keycloak.joinOrganizationGroup(orgId, groupIds.get(BuiltinRole.OWNER), creator));

    // 6. 멤버십 사본 · 활성
    tx.executeWithoutResult(s -> {
      Instant now = Instant.now(clock);
      UUID creatorId = ws.getCreatedBy();
      if (members.find(ws.getId(), creatorId).isEmpty()) {
        members.saveAndFlush(new WorkspaceMember(ws.getId(), creatorId, WorkspaceMember.JoinedVia.CREATOR, now));
      }
      if (memberRoles.find(ws.getId(), creatorId).stream().noneMatch(r -> r.getRoleKey().equals("owner"))) {
        memberRoles.save(new MemberRole(ws.getId(), creatorId, BuiltinRole.OWNER.key(), creatorId, now));
      }
      Workspace current = workspaces.findById(ws.getId()).orElseThrow();
      current.activate(now);
      audit.record(actor, ws.getId(), "workspace.create", "workspace", ws.getId().toString(),
          Map.of("slug", ws.getSlug(), "kind", ws.getKind().name(), "keycloakOrgId", orgId));
    });
    // 7. 개인 워크스페이스에는 바로 쓸 프로젝트 하나("내 프로젝트")를 만들어 둔다 (design/03 §8 · design/04)
    if (ws.getKind() == Workspace.Kind.PERSONAL) {
      projects.ensureDefault(ws.getId(), ws.getCreatedBy(), actor);
    }
    log.info("워크스페이스 {} ({}) 준비 완료", ws.getSlug(), ws.getKind());
  }

  /** workspace-고유id — 겹치면 다시 뽑는다(사실상 일어나지 않음) */
  private String newSlug() {
    for (int i = 0; i < 5; i++) {
      String slug = WorkspaceSlugs.generate();
      if (!workspaces.existsBySlug(slug)) {
        return slug;
      }
    }
    throw new IllegalStateException("워크스페이스 주소를 만들지 못했습니다");
  }
}
