package com.moai.works.web.auth;

import java.io.Serializable;

import org.springframework.security.core.AuthenticatedPrincipal;

/**
 * 로그인한 사람 — SecurityContext 의 principal. 세션(Redis)에 들어가므로 작게 둔다.
 *
 * @param id       Keycloak 사용자 id (토큰 sub) — api 의 사용자 id 와 같다
 * @param username 아이디 (토큰 preferred_username) — 기록 · 화면용
 */
public record SessionUser(String id, String username) implements AuthenticatedPrincipal, Serializable {

  /** Spring Security 가 쓰는 이름 — 바뀌지 않는 id */
  @Override
  public String getName() {
    return id;
  }
}
