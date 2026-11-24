package com.moai.works.web.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import com.moai.works.web.TestClock;
import com.moai.works.web.config.SlidingWindowCounter;
import com.moai.works.web.config.WebProperties;

/** 로그인 시도 제한 — IP 분당 · 아이디 15분당(대소문자 무시), 세는 IP · 아이디의 수에 끝이 있다 (일감 #136) */
class SignInRateLimiterTest {

  private final TestClock clock = new TestClock();
  private final SignInRateLimiter limiter = new SignInRateLimiter(
      new WebProperties(null, null, new WebProperties.SignIn(3, 2), null, null), clock);

  @Test
  void IP_마다_분당_상한을_넘으면_429_이고_1분이_지나면_풀린다() {
    for (int i = 0; i < 3; i++) {
      limiter.check("198.51.100.1", "user-" + i);
    }
    assertThatThrownBy(() -> limiter.check("198.51.100.1", "user-9"))
        .isInstanceOfSatisfying(SignInException.class, e -> assertThat(e.status()).isEqualTo(429));
    limiter.check("198.51.100.2", "user-9");
    clock.advance(Duration.ofSeconds(61));
    limiter.check("198.51.100.1", "user-8");
  }

  @Test
  void 아이디마다_15분_상한은_IP_를_바꿔도_대소문자를_바꿔도_같이_센다() {
    limiter.check("198.51.100.1", "moaiworks01");
    limiter.check("198.51.100.2", "MoaiWorks01");
    assertThatThrownBy(() -> limiter.check("198.51.100.3", "MOAIWORKS01")).isInstanceOf(SignInException.class);
    clock.advance(Duration.ofMinutes(15).plusSeconds(1));
    limiter.check("198.51.100.4", "moaiworks01");
  }

  @Test
  void 세는_IP_와_아이디의_수에_끝이_있다() {
    for (int i = 0; i <= SlidingWindowCounter.SWEEP_ABOVE; i++) {
      limiter.check("10.0." + (i >> 8) + "." + (i & 255), "spray-" + i);
    }
    assertThat(limiter.tracked()).isEqualTo(2 * (SlidingWindowCounter.SWEEP_ABOVE + 1));
    clock.advance(Duration.ofMinutes(16));
    limiter.check("192.0.2.1", "after");
    assertThat(limiter.tracked()).isEqualTo(2);
  }
}
