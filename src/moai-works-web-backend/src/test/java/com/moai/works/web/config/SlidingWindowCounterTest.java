package com.moai.works.web.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import com.moai.works.web.TestClock;

/**
 * 미끄럼 창 셈 (일감 #136) — 창 안의 수 · 창이 지나면 풀림 · 세는 키의 수에 끝(쉬는 키만 치우고 바쁜 키의 셈은 그대로) ·
 * 치우기는 1분에 한 번까지 · 여러 스레드가 같은 키를 세도 정확히 상한까지만.
 * 치우는 기준은 시험이 작게 잡는다(운영은 1만).
 */
class SlidingWindowCounterTest {

  private final TestClock clock = new TestClock();

  @Test
  void 창_안에서는_상한까지만_세고_창이_지나면_풀린다() {
    SlidingWindowCounter counter = new SlidingWindowCounter(Duration.ofMinutes(1), clock);
    assertThat(counter.tryAcquire("a", 2)).isTrue();
    assertThat(counter.tryAcquire("a", 2)).isTrue();
    assertThat(counter.tryAcquire("a", 2)).isFalse();
    assertThat(counter.tryAcquire("b", 2)).isTrue();
    clock.advance(Duration.ofSeconds(60));
    assertThat(counter.tryAcquire("a", 2)).isFalse();
    clock.advance(Duration.ofSeconds(1));
    assertThat(counter.tryAcquire("a", 2)).isTrue();
  }

  @Test
  void 기준을_넘으면_창_동안_요청이_없던_키만_치우고_바쁜_키의_셈은_남긴다() {
    SlidingWindowCounter counter = new SlidingWindowCounter(Duration.ofMinutes(1), 10, clock);
    for (int i = 0; i < 6; i++) {
      assertThat(counter.tryAcquire("idle-" + i, 2)).isTrue();
    }
    clock.advance(Duration.ofSeconds(50));
    assertThat(counter.tryAcquire("busy-0", 2)).isTrue();
    assertThat(counter.tryAcquire("busy-0", 2)).isTrue();
    for (int i = 1; i < 5; i++) {
      assertThat(counter.tryAcquire("busy-" + i, 2)).isTrue();
    }
    assertThat(counter.tracked()).isEqualTo(11);

    clock.advance(Duration.ofSeconds(20));
    assertThat(counter.tryAcquire("new", 2)).isTrue();
    // idle-* (70초 쉼)는 치우고 busy-* (20초 전)와 new 는 남는다
    assertThat(counter.tracked()).isEqualTo(6);
    // 치워도 바쁜 키의 셈은 그대로 — 치우기로 제한이 풀리지 않는다
    assertThat(counter.tryAcquire("busy-0", 2)).isFalse();
  }

  @Test
  void 기준_밑이면_치우지_않는다() {
    SlidingWindowCounter counter = new SlidingWindowCounter(Duration.ofMinutes(1), 10, clock);
    for (int i = 0; i < 10; i++) {
      counter.tryAcquire("k-" + i, 1);
    }
    clock.advance(Duration.ofMinutes(5));
    counter.tryAcquire("x", 1);
    assertThat(counter.tracked()).isEqualTo(11);
  }

  @Test
  void 치우기는_1분에_한_번까지다() {
    SlidingWindowCounter counter = new SlidingWindowCounter(Duration.ofMinutes(1), 10, clock);
    for (int i = 0; i < 10; i++) {
      counter.tryAcquire("old-" + i, 1);
    }
    clock.advance(Duration.ofSeconds(30));
    counter.tryAcquire("z", 1);
    counter.tryAcquire("w", 1);       // 11개 → 치움(첫 번째): 모두 1분 안이라 남는다
    assertThat(counter.tracked()).isEqualTo(12);

    clock.advance(Duration.ofSeconds(40));
    counter.tryAcquire("v", 1);       // old-* 는 70초 쉬었지만 첫 치우기에서 1분이 안 됐다 — 치우지 않는다
    assertThat(counter.tracked()).isEqualTo(13);

    clock.advance(Duration.ofSeconds(21));
    counter.tryAcquire("u", 1);       // 첫 치우기에서 1분이 지났다 — 1분 넘게 쉰 old-* · z · w 를 치운다
    assertThat(counter.tracked()).isEqualTo(2);
  }

  @Test
  void 여러_스레드가_같은_키를_세도_정확히_상한까지만_허락한다() throws Exception {
    SlidingWindowCounter counter = new SlidingWindowCounter(Duration.ofMinutes(1), 50, clock);
    int threads = 8;
    int perThread = 400;
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    CountDownLatch go = new CountDownLatch(1);
    try {
      List<Future<Integer>> results = new ArrayList<>();
      for (int t = 0; t < threads; t++) {
        int id = t;
        results.add(pool.submit(() -> {
          go.await();
          int allowed = 0;
          for (int i = 0; i < perThread; i++) {
            if (counter.tryAcquire("same", 1000)) {
              allowed++;
            }
            // 다른 키도 함께 늘려 치우기가 도는 중에도 세게 한다
            counter.tryAcquire("other-" + id + "-" + i, 1);
          }
          return allowed;
        }));
      }
      go.countDown();
      int total = 0;
      for (Future<Integer> r : results) {
        total += r.get(30, TimeUnit.SECONDS);
      }
      assertThat(total).isEqualTo(1000);
      assertThat(counter.tryAcquire("same", 1000)).isFalse();
    } finally {
      pool.shutdownNow();
    }
  }
}
