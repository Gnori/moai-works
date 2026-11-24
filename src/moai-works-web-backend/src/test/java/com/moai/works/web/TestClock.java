package com.moai.works.web;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

/** 시험이 돌리는 시계 — 미끄럼 창이 지나는 것을 기다리지 않고 본다 */
public final class TestClock extends Clock {

  private volatile Instant now = Instant.parse("2026-11-24T00:00:00Z");

  public void advance(Duration d) {
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
