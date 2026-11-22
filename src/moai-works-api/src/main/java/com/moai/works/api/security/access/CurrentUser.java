package com.moai.works.api.security.access;

import java.util.List;
import java.util.UUID;

import com.moai.works.api.account.PlatformRole;
import com.moai.works.api.common.Actor;

/**
 * {@link UserApi} · {@link WorkspaceApi} · {@link PlatformApi} 를 통과한 사람 호출자 (활성 계정).
 *
 * @param id            Keycloak 사용자 id
 * @param username      Keycloak username
 * @param email         이메일
 * @param displayName   표시 이름
 * @param platformRoles 플랫폼 역할 (system-admin …)
 * @param ip            요청 IP (감사용)
 */
public record CurrentUser(UUID id, String username, String email, String displayName, List<String> platformRoles,
    String ip) {

  public Actor actor() {
    return Actor.user(id, displayName, ip);
  }

  /**
   * 시스템 관리자 계정인가. 시스템 관리자는 시스템만 관리한다 — 워크스페이스를 만들지도, 들어가지도 못한다
   * (2026-11-22 사용자 결정, SECURITY.md §6.1).
   */
  public boolean systemAdmin() {
    return platformRoles.contains(PlatformRole.SYSTEM_ADMIN);
  }
}
