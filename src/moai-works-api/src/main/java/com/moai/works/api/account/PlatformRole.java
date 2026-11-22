package com.moai.works.api.account;

import java.util.Collection;

import com.moai.works.api.common.ApiException;

/** 플랫폼 층 역할 = Keycloak 렐름 역할 (design/03 §3.1). 워크스페이스 역할과 다른 층이다. */
public final class PlatformRole {

  /**
   * 가입 정책 · 계정 승인 · 워크스페이스 정지 — <b>시스템만 관리한다</b>(2026-11-22 사용자 결정). 워크스페이스 영역과 나뉜다:
   * 개인 워크스페이스가 생기지 않고, 워크스페이스를 만들거나 들어갈 수 없다(가입 신청 · 초대 수락도 안 됨). 내용도 볼 수 없다.
   */
  public static final String SYSTEM_ADMIN = "system-admin";
  /** 보안 정책 · MCP 클라이언트 신뢰 승인 (아직 이 역할을 쓰는 API 는 없다) */
  public static final String SECURITY_OFFICER = "security-officer";

  private PlatformRole() {
  }

  public static boolean isSystemAdmin(Collection<String> platformRoles) {
    return platformRoles != null && platformRoles.contains(SYSTEM_ADMIN);
  }

  /** 시스템 관리자 계정이면 403 — 워크스페이스를 만들거나 들어가는 입구에서 부른다 */
  public static void requireNotSystemAdmin(Collection<String> platformRoles) {
    if (isSystemAdmin(platformRoles)) {
      throw ApiException.forbidden("SYSTEM_ADMIN_NO_WORKSPACE",
          "시스템 관리자 계정은 시스템만 관리합니다. 워크스페이스를 만들거나 들어갈 수 없습니다.");
    }
  }

  public static boolean isPlatformRole(String role) {
    return SYSTEM_ADMIN.equals(role) || SECURITY_OFFICER.equals(role);
  }
}
