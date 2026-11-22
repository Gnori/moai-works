package com.moai.works.api.platform;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.moai.works.api.account.AccountStatus;
import com.moai.works.api.account.PlatformRole;
import com.moai.works.api.account.UserProfile;
import com.moai.works.api.account.UserProfileRepository;
import com.moai.works.api.audit.AuditService;
import com.moai.works.api.common.ApiException;
import com.moai.works.api.keycloak.KeycloakAdminClient;
import com.moai.works.api.security.access.CurrentUser;
import com.moai.works.api.workspace.WorkspaceMember;
import com.moai.works.api.workspace.WorkspaceProvisioner;
import com.moai.works.api.workspace.WorkspaceRepositories;

/**
 * 시스템 관리자의 계정 관리 — 승인 · 거절 · 정지 · 복구. Keycloak 먼저(사용자 켜기/끄기), 그다음 사본.
 * 시스템 관리자도 워크스페이스 <b>내용</b>은 볼 수 없다(design/03 §3.1) — 여기에는 계정 정보만 있다.
 */
@Service
public class AccountAdminService {

  private final UserProfileRepository users;
  private final WorkspaceRepositories.Workspaces workspaces;
  private final WorkspaceRepositories.Members members;
  private final KeycloakAdminClient keycloak;
  private final WorkspaceProvisioner provisioner;
  private final AuditService audit;
  private final TransactionTemplate tx;
  private final Clock clock;

  public AccountAdminService(UserProfileRepository users, KeycloakAdminClient keycloak,
      WorkspaceProvisioner provisioner, WorkspaceRepositories.Workspaces workspaces,
      WorkspaceRepositories.Members members, AuditService audit, PlatformTransactionManager txManager, Clock clock) {
    this.workspaces = workspaces;
    this.members = members;
    this.users = users;
    this.keycloak = keycloak;
    this.provisioner = provisioner;
    this.audit = audit;
    this.tx = new TransactionTemplate(txManager);
    this.clock = clock;
  }

  /**
   * @param teamWorkspaceLimit    만들 수 있는 팀 워크스페이스 수(0 이면 못 만든다)
   * @param teamWorkspacesCreated 지금까지 만든 팀 워크스페이스 수(지운 것 제외)
   * @param leftoverMemberships   시스템 관리자 계정에 남아 있는 워크스페이스 멤버십 수 — 워크스페이스를 쓰던 계정에
   *                              시스템 관리자 역할을 준 경우다. 그 계정은 워크스페이스에 들어가지 못하지만 멤버 목록에는
   *                              남아 있으므로, 시스템 관리자가 보고 정리하게 알린다. 보통 계정은 늘 0 이다
   */
  public record AccountView(UUID id, String username, String email, String displayName, AccountStatus status,
      String origin, List<String> platformRoles, Instant signedUpAt, Instant activatedAt, Instant lastLoginAt,
      int teamWorkspaceLimit, long teamWorkspacesCreated, long leftoverMemberships) {
  }

  private AccountView view(UserProfile u) {
    long leftover = u.hasPlatformRole(PlatformRole.SYSTEM_ADMIN)
        ? members.findByUser(u.getId()).stream().filter(WorkspaceMember::isActive).count()
        : 0;
    return new AccountView(u.getId(), u.getUsername(), u.getEmail(), u.getDisplayName(), u.getStatus(),
        u.getOrigin().name(), u.getPlatformRoles(), u.getSignedUpAt(), u.getActivatedAt(), u.getLastLoginAt(),
        u.getTeamWorkspaceLimit(), workspaces.countTeamWorkspacesCreatedBy(u.getId()), leftover);
  }

  /**
   * 이 계정이 만들 수 있는 팀 워크스페이스 수를 정한다. 0 이면 못 만든다(가입한 계정의 기본값).
   * 이미 만든 수보다 작게 정해도 된다 — 있는 워크스페이스는 그대로이고, 더 만들지 못할 뿐이다.
   * 시스템 관리자 계정에는 허용할 수 없다(409) — 워크스페이스를 만들지도 들어가지도 못하는 계정이다.
   */
  public AccountView setTeamWorkspaceLimit(UUID id, int limit, CurrentUser by) {
    return tx.execute(s -> {
      UserProfile u = users.lockById(id)
          .orElseThrow(() -> ApiException.notFound("ACCOUNT_NOT_FOUND", "계정이 없습니다."));
      if (limit > 0 && u.hasPlatformRole(PlatformRole.SYSTEM_ADMIN)) {
        throw ApiException.conflict("SYSTEM_ADMIN_ACCOUNT", "시스템 관리자 계정에는 팀 워크스페이스를 허용할 수 없습니다.");
      }
      int before = u.getTeamWorkspaceLimit();
      u.allowTeamWorkspaces(limit);
      audit.record(by.actor(), null, "account.team_workspace_limit", "user", id.toString(),
          Map.of("before", before, "after", limit));
      return view(u);
    });
  }

  public List<AccountView> list(AccountStatus status, int limit) {
    int size = Math.min(Math.max(limit, 1), 500);
    List<UserProfile> list = status == null
        ? users.findAll(PageRequest.of(0, size, org.springframework.data.domain.Sort.by("signedUpAt").descending()))
            .getContent()
        : users.findByStatusOrderBySignedUpAtAsc(status, PageRequest.of(0, size));
    return list.stream().map(this::view).toList();
  }

  public AccountView approve(UUID id, CurrentUser by) {
    AccountView v = change(id, AccountStatus.PENDING_APPROVAL, AccountStatus.ACTIVE, true, "account.approve", by);
    provisioner.ensurePersonalWorkspace(id, by.actor());
    return v;
  }

  public AccountView reject(UUID id, CurrentUser by) {
    return change(id, AccountStatus.PENDING_APPROVAL, AccountStatus.REJECTED, false, "account.reject", by);
  }

  public AccountView suspend(UUID id, CurrentUser by) {
    if (id.equals(by.id())) {
      throw ApiException.badRequest("SELF_SUSPEND", "자기 계정은 정지할 수 없습니다.");
    }
    return change(id, AccountStatus.ACTIVE, AccountStatus.SUSPENDED, false, "account.suspend", by);
  }

  public AccountView resume(UUID id, CurrentUser by) {
    return change(id, AccountStatus.SUSPENDED, AccountStatus.ACTIVE, true, "account.resume", by);
  }

  private AccountView change(UUID id, AccountStatus from, AccountStatus to, boolean enabled, String action,
      CurrentUser by) {
    UserProfile user = users.findById(id).orElseThrow(() -> ApiException.notFound("ACCOUNT_NOT_FOUND", "계정이 없습니다."));
    if (user.getStatus() != from) {
      throw ApiException.conflict("INVALID_STATE", "계정 상태가 " + from + " 가 아닙니다: " + user.getStatus());
    }
    keycloak.updateUser(id.toString(), Map.of("enabled", enabled));
    return tx.execute(s -> {
      UserProfile u = users.findById(id).orElseThrow();
      u.changeStatus(to, Instant.now(clock));
      audit.record(by.actor(), null, action, "user", id.toString(), Map.of("before", from.name(), "after", to.name()));
      return view(u);
    });
  }
}
