package com.moai.works.web.auth;

import java.io.Serializable;
import java.time.Instant;

/**
 * 세션에 두는 Keycloak 토큰 — 브라우저로는 절대 나가지 않는다.
 *
 * @param accessToken      api 에 실어 보낼 토큰
 * @param accessExpiresAt  만료 시각
 * @param refreshToken     갱신 · 로그아웃용
 * @param refreshExpiresAt 갱신 토큰 만료 시각 (이후엔 다시 로그인)
 */
public record TokenSet(String accessToken, Instant accessExpiresAt, String refreshToken, Instant refreshExpiresAt)
    implements Serializable {

  /** 곧(여유 시간 안에) 만료되는가 */
  public boolean accessExpiresWithin(java.time.Duration margin, Instant now) {
    return !now.plus(margin).isBefore(accessExpiresAt);
  }

  public boolean refreshUsable(Instant now) {
    return refreshToken != null && now.isBefore(refreshExpiresAt);
  }
}
