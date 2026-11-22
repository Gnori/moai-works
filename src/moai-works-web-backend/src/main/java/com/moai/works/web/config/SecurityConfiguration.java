package com.moai.works.web.config;

import java.io.IOException;
import java.util.List;

import org.springframework.security.web.context.NullSecurityContextRepository;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.core.annotation.Order;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.DelegatingSecurityContextRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import com.moai.works.web.auth.KeycloakPasswordAuthenticationProvider;
import com.moai.works.web.auth.SessionTokens;
import com.moai.works.web.relay.Problems;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * BFF 보안 (Spring Security). 로그인 상태는 SecurityContext, 세션은 Spring Session(Redis) — 여러 인스턴스가 같은 세션을 본다.
 * <ul>
 *   <li><b>인증</b>: Keycloak 패스워드 그랜트(AuthenticationProvider). 로그인 처리는 AuthController 가 이 구성요소로 한다</li>
 *   <li><b>인가</b>: "로그인했나"만 본다 — {@code /api/**} 는 로그인 필요(가입만 예외). 무엇을 할 수 있는지는 api 가 요청마다 판정</li>
 *   <li><b>세션</b>: 로그인 때 세션 id 교체(세션 고정 방지) · CSRF 토큰 교체. 익명 요청은 세션을 만들지 않는다(요청 캐시 끔)</li>
 *   <li><b>로그아웃</b>: {@code POST /api/auth/sign-out} — Keycloak 세션 종료 → 세션 삭제 → 204</li>
 *   <li><b>CSRF</b>: SPA 방식 — 쿠키 XSRF-TOKEN(JS 가 읽음) → 헤더 X-XSRF-TOKEN. 같은 출처이므로 CORS 는 열지 않는다</li>
 *   <li><b>보안 헤더</b>: CSP(같은 출처만, 인라인 스타일은 MUI/Emotion 때문에 허용), 프레임 금지, Referrer 제한</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

  private static final String CSP = String.join("; ",
      "default-src 'self'",
      "script-src 'self'",
      "style-src 'self' 'unsafe-inline'",
      "img-src 'self' data: blob:",
      "font-src 'self' data:",
      "connect-src 'self'",
      "object-src 'none'",
      "base-uri 'self'",
      "form-action 'self'",
      "frame-ancestors 'none'");

  /** CSRF 토큰 저장소(쿠키) — 필터와 로그인 때의 토큰 교체가 같은 것을 쓴다 */
  @Bean
  public CookieCsrfTokenRepository csrfTokenRepository() {
    return CookieCsrfTokenRepository.withHttpOnlyFalse();
  }

  /** 로그인 상태 저장소 — 세션(=Redis). 같은 요청 안에서는 요청 속성에도 둔다 */
  @Bean
  public SecurityContextRepository securityContextRepository() {
    return new DelegatingSecurityContextRepository(new RequestAttributeSecurityContextRepository(),
        new HttpSessionSecurityContextRepository());
  }

  /** 로그인할 때 — 세션 id 교체(세션 고정 방지) + CSRF 토큰 교체 */
  @Bean
  public SessionAuthenticationStrategy sessionAuthenticationStrategy(CookieCsrfTokenRepository csrfTokens) {
    return new CompositeSessionAuthenticationStrategy(
        List.of(new ChangeSessionIdAuthenticationStrategy(), new CsrfAuthenticationStrategy(csrfTokens)));
  }

  @Bean
  public AuthenticationManager authenticationManager(KeycloakPasswordAuthenticationProvider keycloak) {
    return new ProviderManager(keycloak);
  }

  /**
   * {@code /mcp/**} — 바깥 MCP 클라이언트의 중계 경로. 로그인 세션 · CSRF · 쿠키를 쓰지 않는다(세션을 만들지 않는다).
   * 인증은 요청의 MCP 키이고 api 가 검사한다 — 여기서는 판정하지 않고 흘려보낸다(MCP 설계 「중계」).
   */
  @Bean
  @Order(1)
  public SecurityFilterChain mcpRelayChain(HttpSecurity http) throws Exception {
    http.securityMatcher("/mcp", "/mcp/**")
        .csrf(csrf -> csrf.disable())
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .requestCache(cache -> cache.disable())
        .securityContext(context -> context.securityContextRepository(new NullSecurityContextRepository()))
        .anonymous(anonymous -> anonymous.disable())
        .authorizeHttpRequests(authz -> authz.anyRequest().permitAll())
        .headers(headers -> headers
            .httpStrictTransportSecurity(hsts -> hsts.requestMatcher(HstsRule::applies))
            .frameOptions(frame -> frame.deny()))
        .formLogin(form -> form.disable())
        .httpBasic(basic -> basic.disable())
        .logout(logout -> logout.disable());
    return http.build();
  }

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http, CookieCsrfTokenRepository csrfTokens,
      SecurityContextRepository contexts, SessionTokens tokens) throws Exception {
    http.securityContext(context -> context.securityContextRepository(contexts))
        // 익명 요청을 세션에 저장하지 않는다 — 로그인 전에는 Redis 에 세션이 생기지 않는다
        .requestCache(cache -> cache.disable())
        .csrf(csrf -> csrf.spa().csrfTokenRepository(csrfTokens))
        .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
        .authorizeHttpRequests(authz -> authz
            .requestMatchers(HttpMethod.POST, "/api/auth/sign-in").permitAll()
            .requestMatchers("/api/v1/signup", "/api/v1/signup/**").permitAll()
            .requestMatchers("/api/**").authenticated()
            .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
            .requestMatchers("/actuator/**").denyAll()
            // 나머지는 React 정적 파일 · 화면 주소
            .anyRequest().permitAll())
        .exceptionHandling(e -> e
            .authenticationEntryPoint((request, response, ex) ->
                Problems.write(response, 401, "UNAUTHENTICATED", "로그인이 필요합니다."))
            .accessDeniedHandler((request, response, ex) -> {
              if (ex instanceof CsrfException) {
                Problems.write(response, 403, "CSRF_INVALID",
                    "보안 토큰이 없거나 만료됐습니다. 페이지를 새로 고친 뒤 다시 시도하십시오.");
              } else {
                Problems.write(response, 403, "FORBIDDEN", "허용되지 않은 요청입니다.");
              }
            }))
        .logout(logout -> logout
            .logoutRequestMatcher(PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/auth/sign-out"))
            // 세션을 지우기 전에 Keycloak 세션부터 끝낸다(갱신 토큰 무효화)
            .addLogoutHandler((request, response, auth) -> tokens.endKeycloakSession(request))
            // 로그아웃은 CSRF 토큰도 지운다 — 화면이 바로 다시 로그인할 수 있게 새 토큰을 싣는다
            .logoutSuccessHandler((request, response, auth) -> {
              csrfTokens.saveToken(csrfTokens.generateToken(request), request, response);
              response.setStatus(HttpServletResponse.SC_NO_CONTENT);
            }))
        .headers(headers -> headers
            // HTTPS 만 쓰라는 표시는 운영 도메인에만 — localhost 에 걸리면 이 PC 의 다른 HTTP 서비스가 열리지 않는다
            .httpStrictTransportSecurity(hsts -> hsts.requestMatcher(HstsRule::applies))
            .contentSecurityPolicy(csp -> csp.policyDirectives(CSP))
            .frameOptions(frame -> frame.deny())
            .referrerPolicy(ref -> ref.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)))
        .formLogin(form -> form.disable())
        .httpBasic(basic -> basic.disable());
    return http.build();
  }

  /** 지연 로딩되는 CSRF 토큰을 매 요청 읽어 쿠키가 항상 내려가게 한다 (SPA 가 첫 POST 전에 쿠키를 가진다). */
  static final class CsrfCookieFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
      CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
      if (token != null) {
        token.getToken();
      }
      chain.doFilter(request, response);
    }
  }
}
