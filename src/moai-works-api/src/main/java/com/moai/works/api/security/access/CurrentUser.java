package com.moai.works.api.security.access;

import java.util.List;
import java.util.UUID;

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
}
