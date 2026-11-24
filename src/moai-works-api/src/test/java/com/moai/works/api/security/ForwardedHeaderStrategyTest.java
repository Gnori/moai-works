package com.moai.works.api.security;

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
 * {@link ClientIp} 는 {@code request.getRemoteAddr()} 가 TCP 접속 주소라고 보고 그 주소가 믿는 중계인지 가린다 (일감 #136).
 * 전달 머리글 처리 방식을 비워 두면 Spring Boot 가 클라우드(쿠버네티스 등)를 알아챌 때 스스로 켜서(Tomcat RemoteIpValve), 내부망 주소에서
 * 온 요청의 X-Forwarded-For 가 접속 주소를 바꾼다 — 그러면 직접 붙은 요청이 IP 를 꾸밀 수 있다. 그래서 설정에 {@code none} 을 박아 둔다.
 * 바꾸면 이 시험이 알린다(SECURITY.md §11 「프록시 뒤 IP」 · {@code trusted-proxies} 를 함께 고친다).
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
