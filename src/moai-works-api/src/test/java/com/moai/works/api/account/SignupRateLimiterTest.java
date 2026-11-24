package com.moai.works.api.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

import com.moai.works.api.common.ApiException;

/**
 * 공개 경로 속도 제한 (일감 #136) — 키(IP · 이메일)마다 시간당 상한, 세는 키의 수에 끝이 있다(한 시간 쉰 키만 치우고 바쁜 키의 셈은
 * 그대로, 치우기는 1분에 한 번까지). DB 없이 돈다.
 */
class SignupRateLimiterTest {

  private static final class TestClock extends Clock {
    private Instant now = Instant.parse("2026-11-24T00:00:00Z");

    void advance(Duration d) {
      now = now.plus(d);
    }

    @Override
    public Instant instant() {
      return now;
    }

    @Override
    public ZoneId getZone() {
      return ZoneId.of("UTC");
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }
  }

  private final TestClock clock = new TestClock();
  private final SignupRateLimiter limiter = new SignupRateLimiter(clock);

  @Test
  void 키마다_시간당_상한을_넘으면_429_이고_한_시간이_지나면_풀린다() {
    limiter.check("resend", "198.51.100.1", 2);
    limiter.check("resend", "198.51.100.1", 2);
    assertThatThrownBy(() -> limiter.check("resend", "198.51.100.1", 2))
        .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.status().value()).isEqualTo(429));
    // 묶음 · 키가 다르면 따로 센다
    limiter.check("signup", "198.51.100.1", 2);
    limiter.check("resend", "198.51.100.2", 2);
    clock.advance(Duration.ofHours(1).plusSeconds(1));
    limiter.check("resend", "198.51.100.1", 2);
  }

  @Test
  void 세는_키의_수에_끝이_있다_한_시간_쉰_키만_치우고_바쁜_키의_셈은_남긴다() {
    for (int i = 0; i < SignupRateLimiter.SWEEP_ABOVE / 2; i++) {
      limiter.check("password-reset-email", "spray-" + i + "@test.moai-works.local", 5);
    }
    clock.advance(Duration.ofMinutes(50));
    limiter.check("password-reset", "198.51.100.9", 1);
    for (int i = 0; i < SignupRateLimiter.SWEEP_ABOVE / 2; i++) {
      limiter.check("password-reset", "10.0." + (i >> 8) + "." + (i & 255), 10);
    }
    assertThat(limiter.tracked()).isEqualTo(SignupRateLimiter.SWEEP_ABOVE + 1);

    clock.advance(Duration.ofMinutes(20));
    limiter.check("resend", "192.0.2.1", 10);
    // 70분 쉰 이메일 자리는 치우고, 20분 전에 센 IP 자리는 남는다
    assertThat(limiter.tracked()).isEqualTo(SignupRateLimiter.SWEEP_ABOVE / 2 + 2);
    // 치워도 바쁜 키의 셈은 그대로
    assertThatThrownBy(() -> limiter.check("password-reset", "198.51.100.9", 1)).isInstanceOf(ApiException.class);
  }
}
