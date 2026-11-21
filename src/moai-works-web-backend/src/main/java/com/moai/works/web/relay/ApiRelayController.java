package com.moai.works.web.relay;

import java.io.IOException;
import java.util.Optional;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.HandlerMapping;

import com.moai.works.web.auth.SessionTokens;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * {@code /api/v1/**} → api {@code /moai-works-api/v1/**} 1:1. 경로 표 없음 — 새 API 를 만들어도 BFF 는 고치지 않는다.
 * 가입(/api/v1/signup/**)만 토큰 없이 넘기고, 나머지는 로그인 세션의 사용자 토큰을 싣는다.
 * 로그인했는지는 Spring Security 인가 규칙이 먼저 본다(SecurityConfiguration). 권한은 api 가 판정한다.
 */
@RestController
public class ApiRelayController {

  private static final String PREFIX = "/api/v1";

  private final ApiRelay relay;
  private final SessionTokens tokens;

  public ApiRelayController(ApiRelay relay, SessionTokens tokens) {
    this.relay = relay;
    this.tokens = tokens;
  }

  @RequestMapping(PREFIX + "/**")
  public void relay(HttpServletRequest request, HttpServletResponse response) throws IOException {
    String full = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
    String path = full.substring(PREFIX.length());
    if (path.startsWith("/signup/") || path.equals("/signup")) {
      relay.relay(request, response, path, null);
      return;
    }
    Optional<String> token = tokens.validAccessToken(request);
    if (token.isEmpty()) {
      // 로그인은 돼 있지만 토큰을 더 쓸 수 없다(갱신 실패) — 로그아웃시키고 다시 로그인하게 한다
      tokens.signOutLocally(request, response);
      Problems.write(response, 401, "UNAUTHENTICATED", "로그인이 필요합니다.");
      return;
    }
    relay.relay(request, response, path, token.get());
  }
}
