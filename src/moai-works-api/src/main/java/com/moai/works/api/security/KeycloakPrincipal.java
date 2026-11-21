package com.moai.works.api.security;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Keycloak 토큰(JWT)으로 확인한 호출자.
 *
 * @param subject        {@code sub} — 사용자 · 서비스 계정의 고정 id (계정은 이 값으로 잇는다. username 은 바뀔 수 있다)
 * @param username       {@code preferred_username}
 * @param clientId       토큰을 받은 클라이언트({@code azp})
 * @param roles          렐름 역할({@code realm_access.roles}) — 플랫폼 역할 판정에 쓴다
 * @param email          이메일 (서비스 계정은 없음)
 * @param name           표시 이름({@code name}, 없으면 null)
 * @param serviceAccount client_credentials 로 받은 서비스 계정 토큰인가
 */
public record KeycloakPrincipal(String subject, String username, String clientId, List<String> roles, String email,
    String name, boolean serviceAccount) {

  static final String SERVICE_ACCOUNT_PREFIX = "service-account-";

  static KeycloakPrincipal from(Jwt jwt) {
    String username = jwt.getClaimAsString("preferred_username");
    if (username == null) {
      username = jwt.getSubject();
    }
    return new KeycloakPrincipal(jwt.getSubject(), username, jwt.getClaimAsString("azp"), realmRoles(jwt.getClaims()),
        jwt.getClaimAsString("email"), jwt.getClaimAsString("name"), username.startsWith(SERVICE_ACCOUNT_PREFIX));
  }

  /** 사람 계정의 id (Keycloak 사용자 id) */
  public UUID userId() {
    return UUID.fromString(subject);
  }

  public boolean hasRealmRole(String role) {
    return roles.contains(role);
  }

  private static List<String> realmRoles(Map<String, Object> claims) {
    if (claims.get("realm_access") instanceof Map<?, ?> access && access.get("roles") instanceof Collection<?> roles) {
      return roles.stream().map(String::valueOf).toList();
    }
    return List.of();
  }
}
