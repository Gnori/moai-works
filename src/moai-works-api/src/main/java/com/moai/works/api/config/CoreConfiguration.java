package com.moai.works.api.config;

import java.time.Clock;
import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 공통 빈. 시각은 항상 {@link Clock} 으로 얻는다(시험에서 고정할 수 있게). */
@Configuration
public class CoreConfiguration {

  @Bean
  /**
   * 기록 시각은 밀리초까지 — 생성 · 수정 같은 변화 기록(일감 · 리비전 · 이력 · 작업 시간 · 감사)의 시각을 밀리초 단위로
   * 남긴다(2026-11-22 사용자 결정: "생성, 수정, 등등 변화에 대한 건 밀리세컨드까지"). 날짜만 다루는 값(완료 기한 · 시작일 ·
   * 완료 예정일 · 완료일 · 종료일)은 LocalDate 라 시각이 없다.
   */
  public Clock clock() {
    return Clock.tick(Clock.systemUTC(), Duration.ofMillis(1));
  }
}
