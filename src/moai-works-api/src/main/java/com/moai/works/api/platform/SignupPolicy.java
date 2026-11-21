package com.moai.works.api.platform;

import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * 가입 정책 (design/03 §7.1). 시스템 관리자만 바꾼다. 세 스위치(도메인 제한 · 누구나 · 승인)는 서로 독립이다.
 *
 * @param enabled                   가입을 받는가
 * @param allowedEmailDomains       비어 있으면 누구나, 있으면 그 도메인만
 * @param requireEmailVerification  이메일 인증 코드를 확인해야 활성화
 * @param requireAdminApproval      시스템 관리자가 승인해야 활성화
 * @param rateLimitPerIpHour        IP 하나의 시간당 가입 요청 수
 * @param teamWorkspaceGrantDefault 시스템 관리자가 계정에 팀 워크스페이스 만들기를 허용할 때 기본으로 주는 수.
 *                                  계정마다 실제로 만들 수 있는 수는 {@code user_profile.team_workspace_limit}(가입하면 0)
 */
public record SignupPolicy(
    @NotNull Boolean enabled,
    @NotNull List<@Pattern(regexp = "^[a-z0-9.-]+\\.[a-z]{2,}$", message = "도메인 형식이 아닙니다") String> allowedEmailDomains,
    @NotNull Boolean requireEmailVerification,
    @NotNull Boolean requireAdminApproval,
    @NotNull @Min(1) @Max(10000) Integer rateLimitPerIpHour,
    @NotNull @Min(1) @Max(100) Integer teamWorkspaceGrantDefault) {

  /** 이 이메일로 가입할 수 있는 도메인인가 */
  public boolean allowsEmail(String email) {
    if (allowedEmailDomains.isEmpty()) {
      return true;
    }
    String domain = email.substring(email.indexOf('@') + 1).toLowerCase();
    return allowedEmailDomains.stream().anyMatch(d -> d.equalsIgnoreCase(domain));
  }
}
