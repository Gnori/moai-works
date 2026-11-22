package com.moai.works.api.account;

/** 플랫폼 층 역할 = Keycloak 렐름 역할 (design/03 §3.1). 워크스페이스 역할과 다른 층이다. */
public final class PlatformRole {

  /** 가입 정책 · 계정 승인 · 워크스페이스 정지. 워크스페이스 내용은 볼 수 없다. */
  public static final String SYSTEM_ADMIN = "system-admin";
  /** 보안 정책 · MCP 클라이언트 신뢰 승인 (아직 이 역할을 쓰는 API 는 없다) */
  public static final String SECURITY_OFFICER = "security-officer";

  private PlatformRole() {
  }

  public static boolean isPlatformRole(String role) {
    return SYSTEM_ADMIN.equals(role) || SECURITY_OFFICER.equals(role);
  }
}
