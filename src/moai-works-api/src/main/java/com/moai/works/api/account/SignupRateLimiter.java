package com.moai.works.api.account;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.moai.works.api.common.ApiException;

/**
 * 공개 경로(가입 · 인증 코드)의 IP 별 시간당 요청 수 제한 — 한 시간 미끄럼 창.
 * 인스턴스 메모리에 둔다. 여러 인스턴스로 늘리면 Redis 카운터로 옮긴다(design/08 사용량 제한과 같은 저장소).
 */
@Component
public class SignupRateLimiter {

  private static final Duration WINDOW = Duration.ofHours(1);
  private final Map<String, Deque<Instant>> hits = new ConcurrentHashMap<>();
  private final Clock clock;

  public SignupRateLimiter(Clock clock) {
    this.clock = clock;
  }

  public void check(String bucket, String ip, int perHour) {
    Instant now = Instant.now(clock);
    Deque<Instant> q = hits.computeIfAbsent(bucket + ":" + ip, k -> new ArrayDeque<>());
    synchronized (q) {
      while (!q.isEmpty() && q.peekFirst().isBefore(now.minus(WINDOW))) {
        q.pollFirst();
      }
      if (q.size() >= perHour) {
        throw new ApiException(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED",
            "요청이 너무 많습니다. 잠시 뒤 다시 시도하십시오.");
      }
      q.addLast(now);
    }
  }
}
