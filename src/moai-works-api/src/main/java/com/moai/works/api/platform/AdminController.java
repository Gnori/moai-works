package com.moai.works.api.platform;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.moai.works.api.account.AccountStatus;
import com.moai.works.api.account.PlatformRole;
import com.moai.works.api.config.EndPointPaths;
import com.moai.works.api.security.access.CurrentUser;
import com.moai.works.api.security.access.PlatformApi;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;

/** 시스템 관리자 — 가입 정책 · 계정 승인 · 정지. 워크스페이스 관리자와 다른 층(렐름 역할 system-admin). */
@RestController
@RequestMapping(EndPointPaths.V1 + "/admin")
@PlatformApi(role = PlatformRole.SYSTEM_ADMIN)
public class AdminController {

  private final PlatformSettingService settings;
  private final AccountAdminService accounts;

  public AdminController(PlatformSettingService settings, AccountAdminService accounts) {
    this.settings = settings;
    this.accounts = accounts;
  }

  @GetMapping("/settings/signup")
  public SignupPolicy signupPolicy() {
    return settings.signupPolicy();
  }

  @PutMapping("/settings/signup")
  public SignupPolicy updateSignupPolicy(CurrentUser user, @Valid @RequestBody SignupPolicy policy) {
    return settings.updateSignupPolicy(policy, user.actor());
  }

  @GetMapping("/accounts")
  public List<AccountAdminService.AccountView> accounts(@RequestParam(required = false) AccountStatus status,
      @RequestParam(defaultValue = "100") int limit) {
    return accounts.list(status, limit);
  }

  @PostMapping("/accounts/{id}/approve")
  public AccountAdminService.AccountView approve(CurrentUser user, @PathVariable UUID id) {
    return accounts.approve(id, user);
  }

  @PostMapping("/accounts/{id}/reject")
  public AccountAdminService.AccountView reject(CurrentUser user, @PathVariable UUID id) {
    return accounts.reject(id, user);
  }

  @PostMapping("/accounts/{id}/suspend")
  public AccountAdminService.AccountView suspend(CurrentUser user, @PathVariable UUID id) {
    return accounts.suspend(id, user);
  }

  /** 이 계정이 만들 수 있는 팀 워크스페이스 수 — 0 이면 못 만든다(가입한 계정의 기본값) */
  public record TeamWorkspaceLimitRequest(@NotNull @Min(0) @Max(100) Integer limit) {
  }

  @PutMapping("/accounts/{id}/team-workspace-limit")
  public AccountAdminService.AccountView teamWorkspaceLimit(CurrentUser user, @PathVariable UUID id,
      @Valid @RequestBody TeamWorkspaceLimitRequest r) {
    return accounts.setTeamWorkspaceLimit(id, r.limit(), user);
  }

  @PostMapping("/accounts/{id}/resume")
  public AccountAdminService.AccountView resume(CurrentUser user, @PathVariable UUID id) {
    return accounts.resume(id, user);
  }
}
