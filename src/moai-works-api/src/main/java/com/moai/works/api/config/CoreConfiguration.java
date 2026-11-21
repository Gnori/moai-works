package com.moai.works.api.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 공통 빈. 시각은 항상 {@link Clock} 으로 얻는다(시험에서 고정할 수 있게). */
@Configuration
public class CoreConfiguration {

  @Bean
  public Clock clock() {
    return Clock.systemUTC();
  }
}
