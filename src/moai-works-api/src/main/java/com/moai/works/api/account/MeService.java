package com.moai.works.api.account;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.moai.works.api.audit.AuditService;
import com.moai.works.api.common.ApiException;
import com.moai.works.api.security.access.CurrentUser;
import com.moai.works.api.workspace.WorkspaceAccessService;
import com.moai.works.api.workspace.WorkspaceProvisioner;
import com.moai.works.api.workspace.WorkspaceService;
import com.moai.works.api.workspace.WorkspaceViews;

/** 내 정보 · 로그인 기록 · 들어갈 워크스페이스. */
@Service
public class MeService {

  private final UserProfileRepository users;
  private final WorkspaceProvisioner provisioner;
  private final WorkspaceService workspaces;
  private final WorkspaceAccessService access;
  private final AuditService audit;
  private final TransactionTemplate tx;
  private final Clock clock;

  public MeService(UserProfileRepository users, WorkspaceProvisioner provisioner, WorkspaceService workspaces,
      WorkspaceAccessService access, AuditService audit, PlatformTransactionManager txManager, Clock clock) {
    this.users = users;
    this.provisioner = provisioner;
    this.workspaces = workspaces;
    this.access = access;
    this.audit = audit;
    this.tx = new TransactionTemplate(txManager);
    this.clock = clock;
  }

  /**
   * @param landingWorkspace      로그인 뒤 들어갈 워크스페이스 슬러그
   * @param teamWorkspaceLimit    만들 수 있는 팀 워크스페이스 수 — 0 이면 못 만든다(시스템 관리자가 정한다)
   * @param teamWorkspacesCreated 지금까지 만든 팀 워크스페이스 수(지운 것 제외)
   */
  public record Me(UUID id, String username, String email, String displayName, AccountStatus status,
      List<String> platformRoles, String personalWorkspace, String landingWorkspace,
      List<WorkspaceViews.Summary> workspaces, Instant lastLoginAt, int teamWorkspaceLimit,
      long teamWorkspacesCreated) {
  }

  /** BFF 가 로그인 직후 부른다 — 로그인 기록, 개인 워크스페이스 보장(만들다 끊긴 것도 이어서), 내 정보. */
  public Me login(CurrentUser user) {
    tx.executeWithoutResult(s -> {
      UserProfile p = users.findById(user.id()).orElseThrow();
      p.recordLogin(Instant.now(clock));
      audit.record(user.actor(), null, "account.login", "user", user.id().toString(), Map.of());
    });
    provisioner.ensurePersonalWorkspace(user.id(), user.actor());
    return me(user);
  }

  public Me me(CurrentUser user) {
    UserProfile p = users.findById(user.id()).orElseThrow();
    String personal = p.getPersonalWorkspaceId() == null ? null
        : workspaces.mine(user.id()).stream().filter(w -> w.kind() == com.moai.works.api.workspace.Workspace.Kind.PERSONAL)
            .map(WorkspaceViews.Summary::slug).findFirst().orElse(null);
    return new Me(p.getId(), p.getUsername(), p.getEmail(), p.getDisplayName(), p.getStatus(), p.getPlatformRoles(),
        personal, workspaces.landing(p).orElse(personal), workspaces.mine(user.id()), p.getLastLoginAt(),
        p.getTeamWorkspaceLimit(), workspaces.teamWorkspacesCreatedBy(user.id()));
  }

  public Me rename(CurrentUser user, String displayName) {
    tx.executeWithoutResult(s -> {
      UserProfile p = users.findById(user.id()).orElseThrow();
      String before = p.getDisplayName();
      p.rename(displayName.trim());
      audit.record(user.actor(), null, "account.rename", "user", user.id().toString(),
          Map.of("before", before, "after", displayName.trim()));
    });
    return me(user);
  }

  /** 마지막에 쓴 워크스페이스를 기억한다 — 다음 로그인 때 그리로 들어간다. 멤버가 아니면 404. */
  public void rememberWorkspace(CurrentUser user, String slug) {
    var ctx = access.contextFor(slug, user.id())
        .orElseThrow(() -> ApiException.notFound("WORKSPACE_NOT_FOUND", "워크스페이스를 찾을 수 없습니다."));
    tx.executeWithoutResult(s -> users.findById(user.id()).orElseThrow().rememberWorkspace(ctx.workspaceId()));
  }
}
