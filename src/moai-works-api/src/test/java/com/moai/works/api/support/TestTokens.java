package com.moai.works.api.support;

import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

import com.moai.works.api.config.MoaiWorksProperties;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

/**
 * 시험용 키로 서명한 Keycloak 모양의 토큰을 만든다.
 *
 * <p>테스트 요청은 이 토큰을 {@code Authorization: Bearer} 로 실어 진짜 필터 체인(서명 · 만료 · 발급자 · audience 확인)을 지난다.
 * 키는 테스트를 돌릴 때마다 새로 만들고 어디에도 저장하지 않는다.
 */
public final class TestTokens {

  /** 사람이 로그인할 때 토큰을 받는 클라이언트 (BFF) */
  public static final String WEB_CLIENT = "moai-works-web-backend";

  private final RSAKey key;
  private final MoaiWorksProperties.Keycloak keycloak;

  public TestTokens(MoaiWorksProperties properties) {
    this.keycloak = properties.keycloak();
    try {
      this.key = new RSAKeyGenerator(2048).keyID("moai-works-test").generate();
    } catch (JOSEException e) {
      throw new IllegalStateException("시험용 키를 만들지 못했습니다", e);
    }
  }

  public RSAPublicKey publicKey() {
    try {
      return key.toRSAPublicKey();
    } catch (JOSEException e) {
      throw new IllegalStateException(e);
    }
  }

  /** 정상 토큰 (5분) */
  public String of(TestUser user) {
    return sign(claims(user).build());
  }

  /** 내용을 바꾼 토큰 — 만료 · 다른 audience · 다른 발급자 같은 거절 시험에 쓴다. */
  public String of(TestUser user, java.util.function.Consumer<JWTClaimsSet.Builder> change) {
    JWTClaimsSet.Builder builder = claims(user);
    change.accept(builder);
    return sign(builder.build());
  }

  /** 서비스 계정 토큰 (client_credentials) — username 이 service-account- 로 시작한다 */
  public String ofService(String clientId) {
    Instant now = Instant.now();
    return sign(new JWTClaimsSet.Builder()
        .issuer(keycloak.issuer())
        .audience(keycloak.clientId())
        .subject(java.util.UUID.nameUUIDFromBytes(clientId.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString())
        .claim("preferred_username", "service-account-" + clientId)
        .claim("azp", clientId)
        .claim("realm_access", Map.of("roles", List.of()))
        .issueTime(Date.from(now))
        .expirationTime(Date.from(now.plusSeconds(300)))
        .build());
  }

  private JWTClaimsSet.Builder claims(TestUser user) {
    Instant now = Instant.now();
    return new JWTClaimsSet.Builder()
        .issuer(keycloak.issuer())
        .audience(keycloak.clientId())
        .subject(user.id().toString())
        .claim("preferred_username", user.username())
        .claim("email", user.email())
        .claim("name", user.displayName())
        .claim("azp", WEB_CLIENT)
        .claim("realm_access", Map.of("roles", user.platformRoles()))
        .issueTime(Date.from(now))
        .expirationTime(Date.from(now.plusSeconds(300)));
  }

  private String sign(JWTClaimsSet claims) {
    try {
      SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims);
      jwt.sign(new RSASSASigner(key));
      return jwt.serialize();
    } catch (JOSEException e) {
      throw new IllegalStateException("토큰 서명 실패", e);
    }
  }
}
