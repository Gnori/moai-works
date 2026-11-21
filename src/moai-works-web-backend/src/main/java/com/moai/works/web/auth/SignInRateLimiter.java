package com.moai.works.web.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.moai.works.web.config.WebProperties;

/**
 * 로그인 시도 제한 — IP 별 분당, 아이디별 15분당. Keycloak 무차별 대입 잠금과 별도로 앞단에서 거른다.
 * 인스턴스 메모리. 여러 대로 늘리면 세션 저장소와 함께 공유 저장소로 옮긴다.
 */
@Component
public class SignInRateLimiter {

  private final Map<String, Deque<Instant>> hits = new ConcurrentHashMap<>();
  private final WebProperties.SignIn limits;
  private final Clock clock;

  public SignInRateLimiter(WebProperties properties, Clock clock) {
    this.limits = properties.signIn();
    this.clock = clock;
  }

  public void check(String ip, String username) {
    hit("ip:" + ip, limits.perIpPerMinute(), Duration.ofMinutes(1));
    hit("user:" + username.toLowerCase(), limits.perUsernamePer15Minutes(), Duration.ofMinutes(15));
  }

  private void hit(String key, int max, Duration window) {
    Instant now = Instant.now(clock);
    Deque<Instant> q = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
    synchronized (q) {
      while (!q.isEmpty() && q.peekFirst().isBefore(now.minus(window))) {
        q.pollFirst();
      }
      if (q.size() >= max) {
        throw new SignInException(429, "RATE_LIMITED", "로그인 시도가 너무 많습니다. 잠시 뒤 다시 시도하십시오.");
      }
      q.addLast(now);
    }
  }
}
