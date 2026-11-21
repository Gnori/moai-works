package com.moai.works.web.auth;

import java.util.Base64;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 아이디 · 비밀번호 → Keycloak 패스워드 그랜트(클라이언트 moai-works-web-backend). 실패는 {@link SignInException}(code 로 사유).
 * 비밀번호는 Keycloak 에만 넘기고 어디에도 남기지 않는다.
 */
@Component
public class KeycloakPasswordAuthenticationProvider implements AuthenticationProvider {

  private final KeycloakTokenClient keycloak;
  private final JsonMapper json;

  public KeycloakPasswordAuthenticationProvider(KeycloakTokenClient keycloak, JsonMapper json) {
    this.keycloak = keycloak;
    this.json = json;
  }

  @Override
  public Authentication authenticate(Authentication authentication) {
    TokenSet issued = keycloak.passwordGrant(authentication.getName(), String.valueOf(authentication.getCredentials()));
    return new KeycloakAuthentication(userOf(issued.accessToken()), issued);
  }

  @Override
  public boolean supports(Class<?> authentication) {
    return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
  }

  /**
   * 액세스 토큰의 sub · preferred_username. Keycloak 에서 방금 뒤 채널로 받은 토큰이라 서명은 여기서 다시 보지 않는다
   * (요청마다 api 가 서명 · 발급자 · audience 를 검사한다).
   */
  private SessionUser userOf(String accessToken) {
    String[] parts = accessToken.split("\\.");
    try {
      JsonNode claims = json.readTree(Base64.getUrlDecoder().decode(parts[1]));
      String sub = claims.path("sub").asString();
      if (sub == null || sub.isBlank()) {
        throw new IllegalArgumentException("sub 없음");
      }
      return new SessionUser(sub, claims.path("preferred_username").asString());
    } catch (JacksonException | IllegalArgumentException | ArrayIndexOutOfBoundsException e) {
      throw new SignInException(502, "IDENTITY_PROVIDER_ERROR", "로그인 서버 응답이 올바르지 않습니다.");
    }
  }
}
