package com.moai.works.api.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 시험의 시계 — 운영과 같은 시스템 시각(밀리초 단위)에 앞당길 만큼({@link #advance})을 더한다. 기본은 0 이라 다른 시험에는 그대로다.
 * 상태 구간의 길이처럼 서버가 재는 시간을 기다리지 않고 시험한다(일감 #275 — 36초보다 짧은 구간은 작업 시간을 묻지 않는다).
 * 앞당긴 시험은 끝날 때 {@link #reset} 한다(스프링 문맥은 모든 시험이 함께 쓴다).
 */
public final class TestClock extends Clock {

  private final AtomicReference<Duration> offset;
  private final ZoneId zone;

  public TestClock() {
    this(new AtomicReference<>(Duration.ZERO), ZoneOffset.UTC);
  }

  private TestClock(AtomicReference<Duration> offset, ZoneId zone) {
    this.offset = offset;
    this.zone = zone;
  }

  /** 시계를 그만큼 앞당긴다 — 쌓인다 */
  public void advance(Duration by) {
    offset.updateAndGet(d -> d.plus(by));
  }

  /** 시스템 시각으로 되돌린다 */
  public void reset() {
    offset.set(Duration.ZERO);
  }

  @Override
  public ZoneId getZone() {
    return zone;
  }

  @Override
  public Clock withZone(ZoneId zone) {
    return new TestClock(offset, zone);
  }

  @Override
  public Instant instant() {
    return Instant.now().plus(offset.get()).truncatedTo(ChronoUnit.MILLIS);
  }
}
