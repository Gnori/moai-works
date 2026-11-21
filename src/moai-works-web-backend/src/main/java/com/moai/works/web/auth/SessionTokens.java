package com.moai.works.web.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * 세션 속 Keycloak 토큰. 세션은 Spring Session(Redis)이라 어느 web-backend 인스턴스든 같은 토큰을 본다.
 *
 * <p>만료 30초 전이면 갱신한다. 같은 세션의 요청이 동시에(다른 인스턴스에서도) 갱신할 수 있는데, 둘 다 유효하다 —
 * 렐름이 쓴 갱신 토큰을 무효화하지 않게 둔다(revokeRefreshToken=false, infra/keycloak/ensure-realm.py 가 맞춘다).
 * 마지막에 저장한 쪽이 세션에 남는다. 갱신이 안 되면(갱신 토큰 만료 · Keycloak 에서 로그아웃) empty — 다시 로그인.
 */
@Component
public class SessionTokens {

  static final String ATTRIBUTE = SessionTokens.class.getName() + ".tokens";
  private static final Duration MARGIN = Duration.ofSeconds(30);

  private final KeycloakTokenClient keycloak;
  private final Clock clock;

  public SessionTokens(KeycloakTokenClient keycloak, Clock clock) {
    this.keycloak = keycloak;
    this.clock = clock;
  }

  public void store(HttpSession session, TokenSet tokens) {
    session.setAttribute(ATTRIBUTE, tokens);
  }

  public Optional<TokenSet> current(HttpServletRequest request) {
    HttpSession session = request.getSession(false);
    return session == null ? Optional.empty() : Optional.ofNullable((TokenSet) session.getAttribute(ATTRIBUTE));
  }

  /** api 에 실어 보낼 유효한 액세스 토큰. 토큰이 없거나 갱신이 안 되면 empty. */
  public Optional<String> validAccessToken(HttpServletRequest request) {
    HttpSession session = request.getSession(false);
    TokenSet tokens = session == null ? null : (TokenSet) session.getAttribute(ATTRIBUTE);
    if (tokens == null) {
      return Optional.empty();
    }
    Instant now = Instant.now(clock);
    if (!tokens.accessExpiresWithin(MARGIN, now)) {
      return Optional.of(tokens.accessToken());
    }
    TokenSet refreshed = tokens.refreshUsable(now) ? keycloak.refresh(tokens.refreshToken()) : null;
    if (refreshed == null) {
      session.removeAttribute(ATTRIBUTE);
      return Optional.empty();
    }
    session.setAttribute(ATTRIBUTE, refreshed);
    return Optional.of(refreshed.accessToken());
  }

  /** Keycloak 세션을 끝낸다(갱신 토큰 무효화). 로그아웃 처리기가 세션을 지우기 전에 부른다. */
  public void endKeycloakSession(HttpServletRequest request) {
    current(request).filter(t -> t.refreshToken() != null).ifPresent(t -> keycloak.logout(t.refreshToken()));
  }

  /**
   * 이 요청에서 로그아웃시킨다 — 토큰을 더 쓸 수 없거나(갱신 실패) api 가 계정을 거절했을 때.
   * Keycloak 세션 종료 → 세션 삭제(Redis) · SecurityContext 비움.
   */
  public void signOutLocally(HttpServletRequest request, HttpServletResponse response) {
    endKeycloakSession(request);
    new SecurityContextLogoutHandler().logout(request, response, null);
  }
}
