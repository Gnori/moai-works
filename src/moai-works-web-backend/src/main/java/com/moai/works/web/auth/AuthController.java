package com.moai.works.web.auth;

import java.io.IOException;
import java.util.Optional;

import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.moai.works.web.relay.ApiRelay;
import com.moai.works.web.relay.Problems;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 로그인 세션. 로그인 상태는 Spring Security 의 SecurityContext 이고, 세션은 Spring Session(Redis)에 있다.
 * <ul>
 *   <li>{@code POST /api/auth/sign-in} — 인증(Keycloak 패스워드 그랜트) → api /me/login(계정 상태 확인)
 *       → 세션 id 교체 · CSRF 토큰 교체 → SecurityContext 와 토큰을 세션에</li>
 *   <li>{@code POST /api/auth/sign-out} — Spring Security 로그아웃(SecurityConfiguration): Keycloak 세션 종료 + 세션 삭제</li>
 *   <li>{@code GET /api/auth/session} — 로그인했으면(인가 규칙) api /me, 아니면 401</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

  private final AuthenticationManager authentication;
  private final SessionAuthenticationStrategy sessionStrategy;
  private final SecurityContextRepository contexts;
  private final SessionTokens tokens;
  private final SignInRateLimiter limiter;
  private final KeycloakTokenClient keycloak;
  private final ApiRelay api;
  private final SecurityContextHolderStrategy holder = SecurityContextHolder.getContextHolderStrategy();

  public AuthController(AuthenticationManager authentication, SessionAuthenticationStrategy sessionStrategy,
      SecurityContextRepository contexts, SessionTokens tokens, SignInRateLimiter limiter, KeycloakTokenClient keycloak,
      ApiRelay api) {
    this.authentication = authentication;
    this.sessionStrategy = sessionStrategy;
    this.contexts = contexts;
    this.tokens = tokens;
    this.limiter = limiter;
    this.keycloak = keycloak;
    this.api = api;
  }

  /** @param username 아이디 또는 이메일 */
  public record SignInRequest(@NotBlank @Size(max = 320) String username, @NotBlank @Size(max = 128) String password) {
  }

  @PostMapping("/sign-in")
  public void signIn(@Valid @RequestBody SignInRequest body, HttpServletRequest request, HttpServletResponse response)
      throws IOException {
    KeycloakAuthentication signedIn;
    try {
      limiter.check(request.getRemoteAddr(), body.username().trim());
      signedIn = (KeycloakAuthentication) authentication.authenticate(
          UsernamePasswordAuthenticationToken.unauthenticated(body.username().trim(), body.password()));
    } catch (SignInException e) {
      Problems.write(response, e.status(), e.code(), e.getMessage());
      return;
    }
    TokenSet issued = signedIn.issuedTokens().orElseThrow();

    // 로그인할 수 있는 계정인지는 api 가 정한다 — 거절하면 Keycloak 세션을 바로 끝내고, 세션은 만들지 않는다
    ApiRelay.Result me = api.call(HttpMethod.POST, "/me/login", issued.accessToken());
    if (!me.ok()) {
      keycloak.logout(issued.refreshToken());
      write(response, me);
      return;
    }

    // 세션 고정 방지(로그인 전 세션 id 버림) + CSRF 토큰 교체 → 로그인 상태와 토큰을 세션(Redis)에
    sessionStrategy.onAuthentication(signedIn, request, response);
    SecurityContext context = holder.createEmptyContext();
    context.setAuthentication(signedIn);
    holder.setContext(context);
    contexts.saveContext(context, request, response);
    tokens.store(request.getSession(), issued);
    // 바뀐 CSRF 토큰 쿠키를 이 응답에 싣는다 (화면이 다음 요청부터 새 토큰을 쓴다)
    if (request.getAttribute(CsrfToken.class.getName()) instanceof CsrfToken csrf) {
      csrf.getToken();
    }
    write(response, me);
  }

  @GetMapping("/session")
  public void session(HttpServletRequest request, HttpServletResponse response) throws IOException {
    Optional<String> token = tokens.validAccessToken(request);
    if (token.isEmpty()) {
      tokens.signOutLocally(request, response);
      Problems.write(response, 401, "UNAUTHENTICATED", "로그인이 필요합니다.");
      return;
    }
    ApiRelay.Result me = api.call(HttpMethod.GET, "/me", token.get());
    if (me.status() == 401 || me.status() == 403) {
      // 토큰이 거절됐거나(만료 · 폐기) 계정이 더는 활성이 아니다 — 로그아웃시킨다
      tokens.signOutLocally(request, response);
    }
    write(response, me);
  }

  private static void write(HttpServletResponse response, ApiRelay.Result result) throws IOException {
    response.setStatus(result.status());
    response.setContentType(result.ok() ? "application/json;charset=UTF-8" : "application/problem+json;charset=UTF-8");
    response.getWriter().write(result.body());
  }
}
