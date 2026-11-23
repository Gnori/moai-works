package com.moai.works.api.account;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.moai.works.api.config.EndPointPaths;
import com.moai.works.api.security.KeycloakPrincipal;
import com.moai.works.api.security.access.CurrentUser;
import com.moai.works.api.security.access.UserApi;
import com.moai.works.api.workspace.JoinRequestService;
import com.moai.works.api.workspace.WorkspaceService;
import com.moai.works.api.workspace.WorkspaceViews;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 내 정보 · 내 워크스페이스 · 내 가입 신청 (사람 계정 본인).
 * 시스템 관리자 계정은 내 정보 · 로그인 기록 · 이름 바꾸기 · 비밀번호 변경만 쓴다 — 워크스페이스 쪽 입구는 막힌다({@link UserApi}).
 */
@RestController
@RequestMapping(EndPointPaths.V1 + "/me")
@UserApi
public class MeController {

  private final MeService me;
  private final WorkspaceService workspaces;
  private final JoinRequestService joinRequests;
  private final PasswordService passwords;

  public MeController(MeService me, WorkspaceService workspaces, JoinRequestService joinRequests,
      PasswordService passwords) {
    this.me = me;
    this.workspaces = workspaces;
    this.joinRequests = joinRequests;
    this.passwords = passwords;
  }

  @GetMapping
  @UserApi(systemAdmin = true)
  public MeService.Me get(CurrentUser user) {
    return me.me(user);
  }

  /** BFF 가 로그인 직후 한 번 부른다 — 로그인 기록 · 개인 워크스페이스 보장(시스템 관리자 계정에는 만들지 않는다) */
  @PostMapping("/login")
  @UserApi(systemAdmin = true)
  public MeService.Me login(CurrentUser user) {
    return me.login(user);
  }

  public record RenameRequest(@NotBlank @Size(max = 100) String displayName) {
  }

  @PatchMapping
  @UserApi(systemAdmin = true)
  public MeService.Me rename(CurrentUser user, @Valid @RequestBody RenameRequest r) {
    return me.rename(user, r.displayName());
  }

  /**
   * @param signOutOtherSessions 다른 기기 · 브라우저의 로그인을 끊는가(비우면 끊는다). 이 브라우저는 그대로 쓴다
   */
  public record ChangePasswordRequest(@NotBlank @Size(max = 128) String currentPassword,
      @NotBlank @Size(min = 8, max = 128) String newPassword, Boolean signOutOtherSessions) {
  }

  /**
   * 비밀번호 변경 (FR-WSP-006) — 현재 비밀번호를 Keycloak 으로 확인한 뒤 바꾼다. 시스템 관리자 계정도 쓴다.
   * 틀린 현재 비밀번호는 400 {@code CURRENT_PASSWORD_INVALID}(401 아님 — 화면을 로그아웃시키지 않는다).
   */
  @PostMapping("/password")
  @UserApi(systemAdmin = true)
  public PasswordService.ChangeResult changePassword(CurrentUser user, @AuthenticationPrincipal KeycloakPrincipal token,
      @Valid @RequestBody ChangePasswordRequest r) {
    return passwords.change(user, token.sessionId(), r.currentPassword(), r.newPassword(),
        r.signOutOtherSessions() == null || r.signOutOtherSessions());
  }

  @GetMapping("/workspaces")
  public List<WorkspaceViews.Summary> workspaces(CurrentUser user) {
    return workspaces.mine(user.id());
  }

  public record LastWorkspaceRequest(@NotBlank String slug) {
  }

  @PutMapping("/last-workspace")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void lastWorkspace(CurrentUser user, @Valid @RequestBody LastWorkspaceRequest r) {
    me.rememberWorkspace(user, r.slug());
  }

  @GetMapping("/join-requests")
  public List<WorkspaceViews.JoinRequestView> myJoinRequests(CurrentUser user) {
    return workspaces.joinRequestViews(joinRequests.mine(user.id()));
  }

  @DeleteMapping("/join-requests/{id}")
  public WorkspaceViews.JoinRequestView cancel(CurrentUser user, @PathVariable UUID id) {
    return workspaces.joinRequestViews(List.of(joinRequests.cancel(id, user))).get(0);
  }
}
