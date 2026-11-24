package com.moai.works.web.config;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 키(IP · 아이디)마다 미끄럼 창 안의 요청 수를 센다 — 인스턴스 메모리. MCP 중계의 IP 별 제한과 로그인 시도 제한이 쓴다.
 *
 * <p><b>세는 키의 수에 끝이 있다</b>(2026-11-24, 일감 #136 — moai-wiki 세션이 같은 자리를 찾아 알려 줌). 키를 지우지 않으면 새 IP ·
 * 새 아이디마다 자리가 늘어 메모리가 끝없이 찬다. 세는 키가 {@link #SWEEP_ABOVE} 를 넘으면 창 동안 요청이 없던 키의 자리를 치운다.
 * 모든 키가 바쁠 때 요청마다 전체를 훑지 않게, 치우기는 {@link #SWEEP_EVERY} 에 한 번까지다. 그래서 자리 수는 "창 + 1분 동안 요청한
 * 키의 수"를 넘지 않는다.
 *
 * <p>스레드: 한 키의 셈과 그 키 자리 치우기는 {@link ConcurrentHashMap#compute} 의 같은 잠금 안에서 일어난다 — 치우는 쪽이 방금 센
 * 자리를 지워 그 요청이 셈에서 빠지는 일이 없다.
 */
public final class SlidingWindowCounter {

  /** 세는 키가 이보다 많으면 치운다 */
  public static final int SWEEP_ABOVE = 10_000;
  /** 치우기는 이 간격에 한 번까지 */
  public static final Duration SWEEP_EVERY = Duration.ofMinutes(1);

  private final Duration window;
  private final int sweepAbove;
  private final Clock clock;
  private final Map<String, Deque<Instant>> hits = new ConcurrentHashMap<>();
  /** 다음에 치울 수 있는 때(epoch 밀리초) */
  private final AtomicLong nextSweepAt = new AtomicLong(Long.MIN_VALUE);

  public SlidingWindowCounter(Duration window, Clock clock) {
    this(window, SWEEP_ABOVE, clock);
  }

  SlidingWindowCounter(Duration window, int sweepAbove, Clock clock) {
    this.window = window;
    this.sweepAbove = sweepAbove;
    this.clock = clock;
  }

  /** 이 키의 요청 하나를 센다. 창 안에 이미 {@code max} 번이면 세지 않고 false */
  public boolean tryAcquire(String key, int max) {
    Instant now = Instant.now(clock);
    if (hits.size() > sweepAbove) {
      sweepIfDue(now);
    }
    boolean[] allowed = {false};
    hits.compute(key, (k, q) -> {
      Deque<Instant> d = q == null ? new ArrayDeque<>() : q;
      expire(d, now);
      if (d.size() < max) {
        d.addLast(now);
        allowed[0] = true;
      }
      return d;
    });
    return allowed[0];
  }

  /** 창 동안 요청이 없던 키의 자리를 치운다 — {@link #SWEEP_EVERY} 에 한 스레드만 */
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

  private void expire(Deque<Instant> q, Instant now) {
    Instant from = now.minus(window);
    while (!q.isEmpty() && q.peekFirst().isBefore(from)) {
      q.pollFirst();
    }
  }

  /** 지금 세고 있는 키의 수 (시험 · 확인용) */
  public int tracked() {
    return hits.size();
  }
}
