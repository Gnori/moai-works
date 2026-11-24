package com.moai.works.web.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.web.server.autoconfigure.ForwardedHeaderStrategy;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

/**
 * 클라이언트 IP 는 TCP 접속 주소다 (일감 #136). MCP 중계의 IP 별 제한 · 로그인 시도 제한 · api 로 넘기는 X-Forwarded-For 는 모두
 * {@code request.getRemoteAddr()} 를 쓴다. 전달 머리글 처리 방식을 비워 두면 Spring Boot 가 클라우드(쿠버네티스 등)를 알아챌 때
 * 스스로 켜서(Tomcat RemoteIpValve), 내부망 주소에서 온 요청의 X-Forwarded-For 가 접속 주소를 바꾼다 — 그래서 설정에 {@code none} 을
 * 박아 둔다. 역방향 프록시 뒤에 두려고 이 값을 바꾸면 이 시험이 알린다(SECURITY.md §11 「프록시 뒤 IP」를 함께 고친다).
 */
class ForwardedHeaderStrategyTest {

  @Test
  void 설정이_전달_머리글로_접속_주소를_바꾸지_않게_박아_둔다() throws IOException {
    StandardEnvironment environment = new StandardEnvironment();
    for (PropertySource<?> source : new YamlPropertySourceLoader().load("application",
        new ClassPathResource("application.yaml"))) {
      environment.getPropertySources().addLast(source);
    }
    assertThat(Binder.get(environment).bind("server.request.header.forwarded.strategy", ForwardedHeaderStrategy.class)
        .orElse(null)).isEqualTo(ForwardedHeaderStrategy.NONE);
  }
}
