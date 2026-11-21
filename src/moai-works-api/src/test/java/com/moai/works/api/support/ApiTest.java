package com.moai.works.api.support;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * api 통합 테스트 — 애플리케이션 전체를 띄우고 HTTP 요청(MockMvc)으로 시험한다.
 * 보안 필터 체인 · 접근 어노테이션 판정 · 서비스 · DB 까지 진짜 코드가 돈다. 가짜는 Keycloak 관리 API 뿐이다.
 *
 * <pre>
 * {@literal @}ApiTest
 * class SomethingTest {
 *   {@literal @}Autowired TestApi api;
 * }
 * </pre>
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestInfrastructure.class)
public @interface ApiTest {
}
