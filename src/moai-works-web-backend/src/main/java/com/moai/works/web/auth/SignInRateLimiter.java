package com.moai.works.web.auth;

import java.time.Clock;
import java.time.Duration;

import org.springframework.stereotype.Component;

import com.moai.works.web.config.SlidingWindowCounter;
import com.moai.works.web.config.WebProperties;

/**
 * 로그인 시도 제한 — IP 별 분당, 아이디별 15분당. Keycloak 무차별 대입 잠금과 별도로 앞단에서 거른다.
 * 인스턴스 메모리. 여러 대로 늘리면 세션 저장소와 함께 공유 저장소로 옮긴다.
 * 세는 IP · 아이디의 수에 끝이 있다 — 1만 개를 넘으면 창 동안 시도가 없던 자리를 치운다({@link SlidingWindowCounter}, 2026-11-24 일감 #136).
 */
@Component
public class SignInRateLimiter {

  private final SlidingWindowCounter byIp;
  private final SlidingWindowCounter byUsername;
  private final WebProperties.SignIn limits;

  public SignInRateLimiter(WebProperties properties, Clock clock) {
    this.limits = properties.signIn();
    this.byIp = new SlidingWindowCounter(Duration.ofMinutes(1), clock);
    this.byUsername = new SlidingWindowCounter(Duration.ofMinutes(15), clock);
  }

  public void check(String ip, String username) {
    if (!byIp.tryAcquire(ip, limits.perIpPerMinute())
        || !byUsername.tryAcquire(username.toLowerCase(), limits.perUsernamePer15Minutes())) {
      throw new SignInException(429, "RATE_LIMITED", "로그인 시도가 너무 많습니다. 잠시 뒤 다시 시도하십시오.");
    }
  }

  /** 지금 세고 있는 IP · 아이디 수 (시험용) */
  int tracked() {
    return byIp.tracked() + byUsername.tracked();
  }
}
