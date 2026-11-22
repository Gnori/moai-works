package com.moai.works.api.security;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.moai.works.api.config.EndPointPaths;
import com.moai.works.api.config.MoaiWorksProperties;
import com.moai.works.api.mcpkey.McpKeyService;

/**
 * 인증 입구 둘 — 서로 다른 필터 체인:
 * <ul>
 *   <li><b>MCP</b> {@code /mcp/**} — MCP 키({@link McpKeyAuthenticationFilter}). Keycloak 을 부르지 않는다.</li>
 *   <li><b>REST</b> {@code /moai-works-api/**} — Keycloak JWT 를 JWKS 로 직접 검증(서명 · 만료 · 발급자 · audience
 *   {@code moai-works-api}). 요청마다 Keycloak 에 묻지 않는다.</li>
 * </ul>
 * 여기서는 "누구인가"만 정한다. <b>무엇을 할 수 있는가</b>는 엔드포인트마다 붙은 접근 어노테이션을
 * {@link com.moai.works.api.security.access.AccessInterceptor} 가 판정한다(기본 거부, 어노테이션이 없으면 기동 실패).
 * 세션 없음(stateless). BFF 가 무엇을 확인했든 api 는 다시 판정한다 — BFF 를 믿지 않는다.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfiguration {

  /** 공개 경로 — 가입 · 인증 코드 (속도 제한은 컨트롤러가 건다) */
  public static final String PUBLIC_API = EndPointPaths.V1 + "/signup/**";

  @Bean
  @Order(1)
  public SecurityFilterChain mcpChain(HttpSecurity http, MoaiWorksProperties properties, McpKeyService mcpKeyService)
      throws Exception {
    McpKeyAuthenticationFilter mcpKeyFilter = new McpKeyAuthenticationFilter(properties.mcp(), mcpKeyService);
    http.securityMatcher("/mcp/**")
        .csrf(csrf -> csrf.disable())
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        // async 디스패치에서 재인가가 실패하지 않게(Streamable HTTP 응답 등) request attribute 저장소를 쓴다.
        .securityContext(context -> context.requireExplicitSave(false))
        .exceptionHandling(exception -> exception.authenticationEntryPoint(
            (request, response, e) -> JsonErrors.write(response, 401, "UNAUTHORIZED", "MCP 키가 필요합니다.")))
        // /mcp/files/** 는 MCP 키가 아니라 일회용 첨부 티켓으로 들어온다 — 판정은 AttachmentTicketService.use 한 곳
        .authorizeHttpRequests(authz -> authz.requestMatchers("/mcp/files/**").permitAll().anyRequest().authenticated())
        .headers(headers -> headers.httpStrictTransportSecurity(hsts -> hsts.requestMatcher(HstsRule::applies)))
        .addFilterBefore(mcpKeyFilter, UsernamePasswordAuthenticationFilter.class);
    return http.build();
  }

  @Bean
  @Order(2)
  public SecurityFilterChain restChain(HttpSecurity http) throws Exception {
    http.csrf(csrf -> csrf.disable())   // 쿠키를 쓰지 않는 Bearer 전용 API — CSRF 대상 아님 (CSRF 는 BFF 가 막는다)
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .exceptionHandling(exception -> exception
            .authenticationEntryPoint(
                (request, response, e) -> JsonErrors.write(response, 401, "UNAUTHORIZED", "인증이 필요합니다."))
            .accessDeniedHandler(
                (request, response, e) -> JsonErrors.write(response, 403, "FORBIDDEN", "권한이 없습니다.")))
        .authorizeHttpRequests(authz -> authz
            .requestMatchers("/actuator/health", "/actuator/health/**", "/error").permitAll()
            .requestMatchers(PUBLIC_API).permitAll()
            .anyRequest().authenticated())
        .headers(headers -> headers.httpStrictTransportSecurity(hsts -> hsts.requestMatcher(HstsRule::applies)))
        .oauth2ResourceServer(oauth -> oauth
            .jwt(jwt -> jwt.jwtAuthenticationConverter(SecurityConfiguration::toAuthentication))
            .authenticationEntryPoint(
                (request, response, e) -> JsonErrors.write(response, 401, "UNAUTHORIZED", "토큰이 없거나 만료됐습니다.")));
    return http.build();
  }

  /** JWKS 로 서명을 확인하고, 발급자와 audience(이 API 클라이언트)를 확인한다. */
  @Bean
  public JwtDecoder jwtDecoder(MoaiWorksProperties properties) {
    MoaiWorksProperties.Keycloak kc = properties.keycloak();
    NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(kc.jwksUri()).build();
    decoder.setJwtValidator(tokenValidator(kc));
    return decoder;
  }

  /**
   * 토큰 내용 검사 — 만료 · 발급자 · audience(이 API 클라이언트). 서명 확인은 디코더 몫이다.
   * 자동 테스트가 시험용 키로 만든 디코더에 같은 검사를 붙이려고 따로 뺐다.
   */
  public static OAuth2TokenValidator<Jwt> tokenValidator(MoaiWorksProperties.Keycloak kc) {
    OAuth2TokenValidator<Jwt> audience = jwt -> jwt.getAudience() != null && jwt.getAudience().contains(kc.clientId())
        ? OAuth2TokenValidatorResult.success()
        : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "audience 에 " + kc.clientId() + " 가 없습니다", null));
    return new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(kc.issuer()), audience);
  }

  private static AbstractAuthenticationToken toAuthentication(Jwt jwt) {
    KeycloakPrincipal principal = KeycloakPrincipal.from(jwt);
    List<SimpleGrantedAuthority> authorities = principal.roles().stream()
        .map(role -> new SimpleGrantedAuthority("ROLE_" + role)).toList();
    return new UsernamePasswordAuthenticationToken(principal, jwt, authorities);
  }
}
