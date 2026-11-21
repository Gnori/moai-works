package com.moai.works.web.auth;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.moai.works.web.config.WebProperties;

/**
 * Keycloak 토큰 엔드포인트 — 클라이언트 moai-works-web-backend 로 로그인 · 갱신 · 로그아웃.
 * 패스워드 그랜트는 자체 로그인 화면을 쓰기로 한 결정(ADR-021)에 따른 것이다.
 */
@Component
public class KeycloakTokenClient {

  private static final Logger log = LoggerFactory.getLogger(KeycloakTokenClient.class);
  private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() {
  };

  private final WebProperties.Keycloak keycloak;
  private final RestClient rest;
  private final Clock clock;

  public KeycloakTokenClient(WebProperties properties, ClientHttpRequestFactory outboundRequestFactory, Clock clock) {
    this.keycloak = properties.keycloak();
    this.rest = RestClient.builder().requestFactory(outboundRequestFactory).build();
    this.clock = clock;
  }

  /** 아이디(또는 이메일)와 비밀번호로 로그인. 실패 사유는 {@link SignInException#code()}. */
  public TokenSet passwordGrant(String username, String password) {
    MultiValueMap<String, String> form = form("password");
    form.add("username", username);
    form.add("password", password);
    form.add("scope", "openid email profile");
    try {
      return toTokens(post(keycloak.tokenUri(), form));
    } catch (HttpClientErrorException e) {
      String body = e.getResponseBodyAsString();
      if (body.contains("Account disabled")) {
        throw new SignInException(403, "ACCOUNT_NOT_ACTIVE", "계정이 활성 상태가 아닙니다. 이메일 인증이나 관리자 승인을 기다리는 중일 수 있습니다.");
      }
      if (body.contains("not fully set up")) {
        throw new SignInException(403, "ACCOUNT_SETUP_REQUIRED", "계정 설정이 끝나지 않았습니다. 관리자에게 문의하십시오.");
      }
      if (body.contains("temporarily disabled") || body.contains("locked")) {
        throw new SignInException(423, "ACCOUNT_LOCKED", "로그인 실패가 많아 잠시 잠겼습니다. 잠시 뒤 다시 시도하십시오.");
      }
      throw new SignInException(401, "INVALID_CREDENTIALS", "아이디 또는 비밀번호가 맞지 않습니다.");
    } catch (RestClientException e) {
      log.error("Keycloak 로그인 요청 실패: {}", e.getMessage());
      throw new SignInException(503, "IDENTITY_PROVIDER_UNAVAILABLE", "로그인 서버에 연결할 수 없습니다.");
    }
  }

  /** 갱신 토큰으로 새 토큰. 실패(만료 · 로그아웃됨)면 null. */
  public TokenSet refresh(String refreshToken) {
    MultiValueMap<String, String> form = form("refresh_token");
    form.add("refresh_token", refreshToken);
    try {
      return toTokens(post(keycloak.tokenUri(), form));
    } catch (RestClientException | SignInException e) {
      log.info("토큰 갱신 실패 — 다시 로그인해야 합니다: {}", e.getMessage());
      return null;
    }
  }

  /** Keycloak 세션을 끝낸다(갱신 토큰 무효화). 실패해도 BFF 세션은 지운다. */
  public void logout(String refreshToken) {
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("client_id", keycloak.clientId());
    form.add("client_secret", keycloak.clientSecret());
    form.add("refresh_token", refreshToken);
    try {
      rest.post().uri(keycloak.logoutUri()).contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form)
          .retrieve().toBodilessEntity();
    } catch (RestClientException e) {
      log.warn("Keycloak 로그아웃 실패(세션은 지움): {}", e.getMessage());
    }
  }

  private MultiValueMap<String, String> form(String grantType) {
    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    form.add("grant_type", grantType);
    form.add("client_id", keycloak.clientId());
    form.add("client_secret", keycloak.clientSecret());
    return form;
  }

  private Map<String, Object> post(String uri, MultiValueMap<String, String> form) {
    return rest.post().uri(uri).contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().body(MAP);
  }

  private TokenSet toTokens(Map<String, Object> body) {
    if (body == null || body.get("access_token") == null) {
      throw new SignInException(502, "IDENTITY_PROVIDER_ERROR", "로그인 서버 응답이 올바르지 않습니다.");
    }
    Instant now = Instant.now(clock);
    long accessTtl = body.get("expires_in") instanceof Number n ? n.longValue() : 300;
    long refreshTtl = body.get("refresh_expires_in") instanceof Number n ? n.longValue() : 1800;
    return new TokenSet(String.valueOf(body.get("access_token")), now.plusSeconds(accessTtl),
        body.get("refresh_token") == null ? null : String.valueOf(body.get("refresh_token")),
        now.plusSeconds(refreshTtl));
  }
}
