package com.moai.works.web.auth;

import java.io.Serial;
import java.util.List;
import java.util.Optional;

import org.springframework.security.authentication.AbstractAuthenticationToken;

/**
 * 로그인 상태 — Spring Security 의 인증 정보. 세션(Redis)에는 이것(SecurityContext)과 토큰(SessionTokens)이 따로 들어간다.
 *
 * <p>권한(authorities)은 두지 않는다 — web-backend 는 "로그인했나"만 보고, 무엇을 할 수 있는지는 api 가 요청마다 정한다.
 * 토큰은 로그인 요청 동안만 이 객체에 실려(issuedTokens) 세션 속성으로 옮겨지고, 직렬화되지 않는다(transient).
 */
public final class KeycloakAuthentication extends AbstractAuthenticationToken {

  @Serial
  private static final long serialVersionUID = 1L;

  private final SessionUser user;
  private final transient TokenSet issued;

  KeycloakAuthentication(SessionUser user, TokenSet issued) {
    super(List.of());
    this.user = user;
    this.issued = issued;
    setAuthenticated(true);
  }

  @Override
  public Object getCredentials() {
    return null;
  }

  @Override
  public SessionUser getPrincipal() {
    return user;
  }

  /** 로그인 요청에서만 있다. 세션에서 다시 읽은 인증 정보에는 없다 */
  public Optional<TokenSet> issuedTokens() {
    return Optional.ofNullable(issued);
  }
}
