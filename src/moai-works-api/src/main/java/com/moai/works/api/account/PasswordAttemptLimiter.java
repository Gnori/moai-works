package com.moai.works.api.account;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.moai.works.api.common.ApiException;

/**
 * 비밀번호 변경의 "현재 비밀번호" 틀린 횟수 — 사람마다 15분에 5번까지, 넘으면 그 창이 지날 때까지 429 {@code RATE_LIMITED}
 * (맞는 비밀번호를 넣어도). 맞히면 횟수를 지운다. 로그인 시도 제한(web-backend)처럼 인스턴스 메모리에 둔다.
 *
 * <p>Keycloak 무차별 대입 잠금(10번)도 이 확인을 센다. 여기서 먼저(5번) 막으므로 비밀번호 변경 칸에서 틀린 것만으로는 로그인이 잠기지 않는다.
 */
@Component
public class PasswordAttemptLimiter {

  static final int MAX_FAILURES = 5;
  static final Duration WINDOW = Duration.ofMinutes(15);

  private final Map<UUID, Deque<Instant>> failures = new ConcurrentHashMap<>();
  private final Clock clock;

  public PasswordAttemptLimiter(Clock clock) {
    this.clock = clock;
  }

  /** 막혀 있으면 429 */
  public void check(UUID userId) {
    Deque<Instant> q = failures.get(userId);
    if (q == null) {
      return;
    }
    Instant now = Instant.now(clock);
    synchronized (q) {
      prune(q, now);
      if (q.size() >= MAX_FAILURES) {
        long wait = Math.max(1, Duration.between(now, q.peekFirst().plus(WINDOW)).toSeconds());
        throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED",
            "현재 비밀번호를 여러 번 틀렸습니다. " + Math.max(1, (wait + 59) / 60) + "분 뒤 다시 시도하십시오.")
            .with("retryAfterSeconds", wait);
      }
    }
  }

  /** 틀렸다. @return 남은 횟수 */
  public int recordFailure(UUID userId) {
    Instant now = Instant.now(clock);
    Deque<Instant> q = failures.computeIfAbsent(userId, k -> new ArrayDeque<>());
    synchronized (q) {
      prune(q, now);
      q.addLast(now);
      return Math.max(0, MAX_FAILURES - q.size());
    }
  }

  /** 맞혔다 — 센 것을 지운다 */
  public void reset(UUID userId) {
    failures.remove(userId);
  }

  private static void prune(Deque<Instant> q, Instant now) {
    while (!q.isEmpty() && !q.peekFirst().isAfter(now.minus(WINDOW))) {
      q.pollFirst();
    }
  }
}
