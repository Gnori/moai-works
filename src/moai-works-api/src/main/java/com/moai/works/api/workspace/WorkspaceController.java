package com.moai.works.api.workspace;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.moai.works.api.config.EndPointPaths;
import com.moai.works.api.security.access.CurrentUser;
import com.moai.works.api.security.access.UserApi;
import com.moai.works.api.security.access.WorkspaceApi;
import com.moai.works.api.security.access.WorkspaceContext;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 워크스페이스 · 멤버 · 역할 · 가입 신청 · 감사. 모든 메서드에 접근 어노테이션이 있다 — 권한은 거기서 판정이 끝난다.
 * 멤버가 아니면 404, 멤버인데 권한이 없으면 403.
 */
@RestController
@RequestMapping(EndPointPaths.V1 + "/workspaces")
public class WorkspaceController {

  private final WorkspaceService workspaces;
  private final MembershipService membership;
  private final JoinRequestService joinRequests;
  private final RoleService roleService;
  private final InvitationService invitations;

  public WorkspaceController(WorkspaceService workspaces, MembershipService membership,
      JoinRequestService joinRequests, RoleService roleService, InvitationService invitations) {
    this.workspaces = workspaces;
    this.membership = membership;
    this.joinRequests = joinRequests;
    this.roleService = roleService;
    this.invitations = invitations;
  }

  // ── 찾기 · 만들기 ──────────────────────────────────────────────────

  @GetMapping
  @UserApi
  public List<WorkspaceViews.Discoverable> discover(CurrentUser user, @RequestParam(required = false) String query) {
    return workspaces.discover(user.id(), query);
  }

  /** 주소(slug)는 받지 않는다 — 서버가 workspace-고유id 로 정한다. */
  public record CreateRequest(@NotBlank @Size(max = 100) String name, @Size(max = 1000) String description,
      @NotNull Workspace.Visibility visibility, @NotNull Workspace.JoinPolicy joinPolicy) {
  }

  @PostMapping
  @UserApi
  @ResponseStatus(HttpStatus.CREATED)
  public WorkspaceViews.Detail create(CurrentUser user, @Valid @RequestBody CreateRequest r) {
    return workspaces.createTeam(user, r.name(), r.description(), r.visibility(), r.joinPolicy());
  }

  // ── 워크스페이스 ───────────────────────────────────────────────────

  @GetMapping("/{workspace}")
  @WorkspaceApi(Permission.WORKSPACE_READ)
  public WorkspaceViews.Detail detail(WorkspaceContext ctx) {
    return workspaces.detail(ctx);
  }

  public record SettingsRequest(@NotBlank @Size(max = 100) String name, @Size(max = 1000) String description,
      @NotNull Workspace.Visibility visibility, @NotNull Workspace.JoinPolicy joinPolicy,
      @NotNull List<String> allowedJoinDomains, @NotBlank String defaultRoleKey) {
  }

  @PatchMapping("/{workspace}")
  @WorkspaceApi(Permission.WORKSPACE_SETTINGS_MANAGE)
  public WorkspaceViews.Detail updateSettings(WorkspaceContext ctx, CurrentUser user,
      @Valid @RequestBody SettingsRequest r) {
    return workspaces.updateSettings(ctx, r.name(), r.description(), r.visibility(), r.joinPolicy(),
        r.allowedJoinDomains(), r.defaultRoleKey(), user);
  }

  @GetMapping("/{workspace}/roles")
  @WorkspaceApi(Permission.WORKSPACE_READ)
  public List<WorkspaceViews.Role> roles(WorkspaceContext ctx) {
    return workspaces.roles(ctx);
  }

  public record RoleRequest(@NotBlank @Size(max = 100) String name, @Size(max = 500) String description,
      @NotEmpty Set<String> permissions) {
  }

  /** 사용자 정의 역할 만들기 — 내가 가진 권한 안에서만 (권한 상승 방지) */
  @PostMapping("/{workspace}/roles")
  @WorkspaceApi(Permission.ROLE_MANAGE)
  @ResponseStatus(HttpStatus.CREATED)
  public WorkspaceViews.Role createRole(WorkspaceContext ctx, CurrentUser user, @Valid @RequestBody RoleRequest r) {
    return roleService.create(ctx, r.name(), r.description(), r.permissions(), user.actor());
  }

  @PatchMapping("/{workspace}/roles/{key}")
  @WorkspaceApi(Permission.ROLE_MANAGE)
  public WorkspaceViews.Role updateRole(WorkspaceContext ctx, CurrentUser user, @PathVariable String key,
      @Valid @RequestBody RoleRequest r) {
    return roleService.update(ctx, key, r.name(), r.description(), r.permissions(), user.actor());
  }

  @DeleteMapping("/{workspace}/roles/{key}")
  @WorkspaceApi(Permission.ROLE_MANAGE)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteRole(WorkspaceContext ctx, CurrentUser user, @PathVariable String key) {
    roleService.delete(ctx, key, user.actor());
  }

  // ── 초대 ───────────────────────────────────────────────────────────

  public record InviteRequest(@NotBlank @jakarta.validation.constraints.Email @Size(max = 320) String email,
      @NotBlank String roleKey, @Size(max = 500) String message) {
  }

  /** 초대 보내기 — 링크는 응답에 한 번만(메일이 로그 모드라 화면이 복사해 전한다) */
  @PostMapping("/{workspace}/invitations")
  @WorkspaceApi(Permission.MEMBER_INVITE)
  @ResponseStatus(HttpStatus.CREATED)
  public WorkspaceViews.InvitationCreated invite(WorkspaceContext ctx, CurrentUser user,
      @Valid @RequestBody InviteRequest r) {
    return invitations.create(ctx, r.email(), r.roleKey(), r.message(), user);
  }

  @GetMapping("/{workspace}/invitations")
  @WorkspaceApi(Permission.MEMBER_INVITE)
  public List<WorkspaceViews.InvitationView> pendingInvitations(WorkspaceContext ctx) {
    return invitations.pending(ctx);
  }

  @DeleteMapping("/{workspace}/invitations/{id}")
  @WorkspaceApi(Permission.MEMBER_INVITE)
  public WorkspaceViews.InvitationView revokeInvitation(WorkspaceContext ctx, CurrentUser user, @PathVariable UUID id) {
    return invitations.revoke(ctx, id, user);
  }

  // ── 멤버 ───────────────────────────────────────────────────────────

  @GetMapping("/{workspace}/members")
  @WorkspaceApi(Permission.MEMBER_READ)
  public List<WorkspaceViews.Member> members(WorkspaceContext ctx) {
    return workspaces.members(ctx);
  }

  public record RolesRequest(@NotEmpty Set<String> roles) {
  }

  @PutMapping("/{workspace}/members/{userId}/roles")
  @WorkspaceApi(Permission.MEMBER_ROLE_ASSIGN)
  public Set<String> setRoles(WorkspaceContext ctx, CurrentUser user, @PathVariable UUID userId,
      @Valid @RequestBody RolesRequest r) {
    return membership.setRoles(ctx, userId, r.roles(), user.actor());
  }

  @DeleteMapping("/{workspace}/members/{userId}")
  @WorkspaceApi(Permission.MEMBER_REMOVE)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void removeMember(WorkspaceContext ctx, CurrentUser user, @PathVariable UUID userId) {
    membership.removeMember(ctx, userId, user.actor());
  }

  @PostMapping("/{workspace}/leave")
  @WorkspaceApi(Permission.WORKSPACE_READ)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void leave(WorkspaceContext ctx, CurrentUser user) {
    membership.leave(ctx, user.actor());
  }

  // ── 가입 신청 ──────────────────────────────────────────────────────

  public record ApplyRequest(@Size(max = 500) String message) {
  }

  /** 멤버가 아닌 사람이 신청한다 — 그래서 @WorkspaceApi 가 아니라 @UserApi */
  @PostMapping("/{workspace}/join-requests")
  @UserApi
  @ResponseStatus(HttpStatus.CREATED)
  public WorkspaceViews.JoinRequestView apply(CurrentUser user, @PathVariable String workspace,
      @Valid @RequestBody(required = false) ApplyRequest r) {
    JoinRequest request = joinRequests.apply(workspace, user, r == null ? null : r.message());
    return workspaces.joinRequestViews(List.of(request)).get(0);
  }

  @GetMapping("/{workspace}/join-requests")
  @WorkspaceApi(Permission.MEMBER_REQUEST_REVIEW)
  public List<WorkspaceViews.JoinRequestView> pending(WorkspaceContext ctx) {
    return workspaces.joinRequestViews(joinRequests.pending(ctx));
  }

  public record DecisionRequest(String roleKey, @Size(max = 500) String note) {
  }

  @PostMapping("/{workspace}/join-requests/{id}/approve")
  @WorkspaceApi(Permission.MEMBER_REQUEST_REVIEW)
  public WorkspaceViews.JoinRequestView approve(WorkspaceContext ctx, CurrentUser user, @PathVariable UUID id,
      @Valid @RequestBody(required = false) DecisionRequest r) {
    JoinRequest done = joinRequests.approve(ctx, id, r == null ? null : r.roleKey(), r == null ? null : r.note(),
        user.actor());
    return workspaces.joinRequestViews(List.of(done)).get(0);
  }

  @PostMapping("/{workspace}/join-requests/{id}/reject")
  @WorkspaceApi(Permission.MEMBER_REQUEST_REVIEW)
  public WorkspaceViews.JoinRequestView reject(WorkspaceContext ctx, CurrentUser user, @PathVariable UUID id,
      @Valid @RequestBody(required = false) DecisionRequest r) {
    JoinRequest done = joinRequests.reject(ctx, id, r == null ? null : r.note(), user.actor());
    return workspaces.joinRequestViews(List.of(done)).get(0);
  }

  // ── 감사 ───────────────────────────────────────────────────────────

  @GetMapping("/{workspace}/audit")
  @WorkspaceApi(Permission.AUDIT_READ)
  public List<WorkspaceViews.AuditEntry> audit(WorkspaceContext ctx,
      @RequestParam(defaultValue = "50") int limit) {
    return workspaces.audit(ctx, limit);
  }
}
