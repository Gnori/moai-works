package com.moai.works.api;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@ConfigurationPropertiesScan
// 저장소 인터페이스를 쓰는 서비스 · 엔티티 안에 중첩해 둔다(작은 묶음) — 중첩 인터페이스도 찾게 한다.
@EnableJpaRepositories(basePackages = "com.moai.works.api", considerNestedRepositories = true)
public class MoaiWorksApiApplication {

  public static void main(String[] args) {
    // JVM 기본 시간대를 UTC 로 고정한다 — KST PC 에서 그냥 띄우면 시각이 9시간 어긋나 저장된다
    // (seed-site-keeper-api 1122 에서 겪은 일).
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    SpringApplication.run(MoaiWorksApiApplication.class, args);
  }
}
