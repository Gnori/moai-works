package com.moai.works.web.relay;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;

import com.moai.works.web.auth.KeycloakTokenClient;
import com.moai.works.web.auth.SessionTokens;
import com.moai.works.web.config.WebProperties;
import com.sun.net.httpserver.HttpServer;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * api 로 넘기기 — 공개 경로(가입 · 비밀번호 찾기)는 토큰 없이, api 가 토큰을 401 로 거절하면 응답을 쓰기 전에 이 세션을 끝낸다
 * (비밀번호를 바꾸며 끊은 다른 기기의 로그인이 처음 요청에서 Redis 세션까지 지워지게 — SECURITY.md §4.5).
 * api 자리에는 이 시험이 띄운 작은 HTTP 서버를 둔다(평문 — 시험 안에서만).
 */
class ApiRelayTest {

  private HttpServer api;
  private ApiRelay relay;
  private final List<String> authorizations = new ArrayList<>();

  @BeforeEach
  void start() throws IOException {
    api = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    api.createContext("/v1/", exchange -> {
      String auth = exchange.getRequestHeaders().getFirst("Authorization");
      authorizations.add(auth == null ? "(없음)" : auth);
      boolean revoked = exchange.getRequestURI().getPath().endsWith("/revoked");
      byte[] body = (revoked ? "{\"code\":\"SESSION_REVOKED\"}" : "{\"ok\":true}").getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().add("Content-Type", revoked ? "application/problem+json" : "application/json");
      exchange.sendResponseHeaders(revoked ? 401 : 200, body.length);
      exchange.getResponseBody().write(body);
      exchange.close();
    });
    api.start();
    WebProperties properties = new WebProperties(new WebProperties.Keycloak("http://127.0.0.1:1", "moai-works", "web", "x"),
        new WebProperties.Api("http://127.0.0.1:" + api.getAddress().getPort() + "/v1", null), null, null, null);
    relay = new ApiRelay(properties, new SimpleClientHttpRequestFactory());
  }

  @AfterEach
  void stop() {
    api.stop(0);
  }

  @Test
  void api_가_401_이면_응답을_쓰기_전에_로그아웃한다() throws IOException {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/me/revoked");
    MockHttpServletResponse response = new MockHttpServletResponse();
    AtomicBoolean signedOut = new AtomicBoolean();
    relay.relay(request, response, "/me/revoked", "token-1", () -> {
      assertThat(response.isCommitted()).isFalse();
      signedOut.set(true);
    });
    assertThat(signedOut).isTrue();
    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(response.getContentAsString()).contains("SESSION_REVOKED");
  }

  @Test
  void 다른_응답이면_로그아웃하지_않는다() throws IOException {
    MockHttpServletResponse response = new MockHttpServletResponse();
    AtomicBoolean signedOut = new AtomicBoolean();
    relay.relay(new MockHttpServletRequest("GET", "/api/v1/me"), response, "/me", "token-1", () -> signedOut.set(true));
    assertThat(signedOut).isFalse();
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(authorizations).containsExactly("Bearer token-1");
  }

  @Test
  void 공개_경로는_가입과_비밀번호_찾기뿐이다() {
    assertThat(ApiRelayController.isPublic("/signup")).isTrue();
    assertThat(ApiRelayController.isPublic("/signup/verify")).isTrue();
    assertThat(ApiRelayController.isPublic("/password-reset/request")).isTrue();
    assertThat(ApiRelayController.isPublic("/password-reset/confirm")).isTrue();
    assertThat(ApiRelayController.isPublic("/signupx")).isFalse();
    assertThat(ApiRelayController.isPublic("/password-resetx/request")).isFalse();
    assertThat(ApiRelayController.isPublic("/me/password")).isFalse();
    assertThat(ApiRelayController.isPublic("/me")).isFalse();
  }

  @Test
  void 공개_경로는_로그인_세션이_있어도_토큰을_싣지_않는다() throws IOException {
    WebProperties props = new WebProperties(new WebProperties.Keycloak("http://127.0.0.1:1", "moai-works", "web", "x"),
        null, null, null, null);
    SessionTokens tokens = new SessionTokens(new KeycloakTokenClient(props, new SimpleClientHttpRequestFactory(),
        Clock.systemUTC()), Clock.systemUTC()) {
      @Override
      public Optional<String> validAccessToken(HttpServletRequest request) {
        return Optional.of("session-token");
      }

      @Override
      public void signOutLocally(HttpServletRequest request, HttpServletResponse response) {
        throw new AssertionError("공개 경로에서 로그아웃하지 않는다");
      }
    };
    ApiRelayController controller = new ApiRelayController(relay, tokens);

    controller.relay(at("/api/v1/password-reset/request", "POST"), new MockHttpServletResponse());
    controller.relay(at("/api/v1/me", "GET"), new MockHttpServletResponse());
    assertThat(authorizations).containsExactly("(없음)", "Bearer session-token");
  }

  private static MockHttpServletRequest at(String path, String method) {
    MockHttpServletRequest request = new MockHttpServletRequest(method, path);
    request.setAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE, path);
    return request;
  }
}
