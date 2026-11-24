package com.moai.works.api.account;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

import com.moai.works.api.common.ApiException;

/**
 * 공개 경로(가입 · 인증 코드 · 비밀번호 찾기)의 IP · 이메일별 시간당 요청 수 제한 — 한 시간 미끄럼 창.
 * 인스턴스 메모리에 둔다. 여러 인스턴스로 늘리면 Redis 카운터로 옮긴다(design/08 사용량 제한과 같은 저장소).
 *
 * <p><b>세는 키의 수에 끝이 있다</b>(2026-11-24, 일감 #136 — web-backend MCP 중계의 같은 구멍을 moai-wiki 세션이 알려 줌). 키(IP ·
 * 이메일)는 요청이 정하므로 지우지 않으면 새 IP · 새 이메일마다 자리가 늘어 메모리가 끝없이 찬다. 세는 키가 {@link #SWEEP_ABOVE} 를
 * 넘으면 지난 한 시간에 요청이 없던 키의 자리를 치운다. 모든 키가 바쁠 때 요청마다 전체를 훑지 않게 치우기는 1분에 한 번까지다.
 * 한 키의 셈과 그 키 자리 치우기는 {@link ConcurrentHashMap#compute} 의 같은 잠금 안에서 일어난다(치우는 쪽이 방금 센 자리를 지우지 못한다).
 */
@Component
public class SignupRateLimiter {

  /** 세는 키가 이보다 많으면 치운다 */
  static final int SWEEP_ABOVE = 10_000;
  private static final Duration WINDOW = Duration.ofHours(1);
  private static final Duration SWEEP_EVERY = Duration.ofMinutes(1);

  private final Map<String, Deque<Instant>> hits = new ConcurrentHashMap<>();
  private final AtomicLong nextSweepAt = new AtomicLong(Long.MIN_VALUE);
  private final Clock clock;

  public SignupRateLimiter(Clock clock) {
    this.clock = clock;
  }

  public void check(String bucket, String key, int perHour) {
    Instant now = Instant.now(clock);
    if (hits.size() > SWEEP_ABOVE) {
      sweepIfDue(now);
    }
    boolean[] allowed = {false};
    hits.compute(bucket + ":" + key, (k, q) -> {
      Deque<Instant> d = q == null ? new ArrayDeque<>() : q;
      expire(d, now);
      if (d.size() < perHour) {
        d.addLast(now);
        allowed[0] = true;
      }
      return d;
    });
    if (!allowed[0]) {
      throw new ApiException(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED",
          "요청이 너무 많습니다. 잠시 뒤 다시 시도하십시오.");
    }
  }

  /** 지난 한 시간에 요청이 없던 키의 자리를 치운다 — 1분에 한 스레드만 */
  private void sweepIfDue(Instant now) {
    long due = nextSweepAt.get();
    long at = now.toEpochMilli();
    if (at < due || !nextSweepAt.compareAndSet(due, at + SWEEP_EVERY.toMillis())) {
      return;
    }
    for (String key : hits.keySet()) {
      hits.computeIfPresent(key, (k, q) -> {
        expire(q, now);
        return q.isEmpty() ? null : q;
      });
    }
  }

  private static void expire(Deque<Instant> q, Instant now) {
    Instant from = now.minus(WINDOW);
    while (!q.isEmpty() && q.peekFirst().isBefore(from)) {
      q.pollFirst();
    }
  }

  /** 지금 세고 있는 키의 수 (시험용) */
  int tracked() {
    return hits.size();
  }
}
