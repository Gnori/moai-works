package com.moai.works.web.relay;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.moai.works.web.TestClock;
import com.moai.works.web.config.OutboundHttp;
import com.moai.works.web.config.WebProperties;
import com.sun.net.httpserver.HttpServer;

/**
 * MCP 중계 (일감 #136 — moai-wiki 의 McpRelayTest 와 같은 자리) — 판정 없이 api 로 흘려보낸다. 키(Authorization)는 그대로 넘기고,
 * 허용한 헤더만 오가고, 클라이언트 IP 는 접속 주소로 새로 쓰고, api 의 거절은 상태 · 머리글 · 본문 그대로 돌려준다.
 *
 * <p>api 자리에는 이 시험이 띄운 작은 HTTP 서버를 둔다(평문 — 시험 안에서만). <b>운영과 같은 JDK HttpClient 로 부른다</b> —
 * {@code HttpURLConnection}(SimpleClientHttpRequestFactory)은 {@code WWW-Authenticate} 가 붙은 401 의 본문을 버려, 거절 본문이
 * 빠지는 문제를 가린다(2026-11-24 moai-wiki 세션이 알려 줌).
 */
class McpRelayTest {

  private HttpServer api;
  private final TestClock clock = new TestClock();
  private final List<Map<String, String>> seen = new CopyOnWriteArrayList<>();
  private final List<String> paths = new CopyOnWriteArrayList<>();
  private final List<String> bodies = new CopyOnWriteArrayList<>();

  @BeforeEach
  void start() throws IOException {
    api = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    api.createContext("/mcp/", exchange -> {
      Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
      exchange.getRequestHeaders().forEach((k, v) -> headers.put(k, v.get(0)));
      seen.add(headers);
      paths.add(exchange.getRequestURI().toString());
      bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
      String path = exchange.getRequestURI().getPath();
      if (path.endsWith("/hang")) {
        // 머리글을 주지 않고 붙잡는 api(옛 MCP GET 스트림) — 중계가 한도까지만 기다리고 끊어야 한다
        try {
          Thread.sleep(5_000);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
        exchange.close();
        return;
      }
      int status;
      String body;
      if (path.endsWith("/denied")) {
        // 키 검사 거절 — MCP 클라이언트는 WWW-Authenticate 와 본문의 code 로 무엇이 틀렸는지 안다
        status = 401;
        body = "{\"status\":401,\"code\":\"MCP_KEY_EXPIRED\",\"detail\":\"만료된 키입니다.\"}";
        exchange.getResponseHeaders().add("WWW-Authenticate", "Bearer error=\"invalid_token\"");
        exchange.getResponseHeaders().add("Content-Type", "application/problem+json");
      } else if (path.endsWith("/forbidden")) {
        status = 403;
        body = "{\"status\":403,\"code\":\"MCP_PRINCIPAL_INACTIVE\",\"detail\":\"키 주인 계정을 쓸 수 없습니다.\"}";
        exchange.getResponseHeaders().add("Content-Type", "application/problem+json");
      } else {
        status = 200;
        body = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{}}";
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.getResponseHeaders().add("Mcp-Session-Id", "session-1");
      }
      // 허용 목록 밖 — 중계가 넘기지 않아야 한다
      exchange.getResponseHeaders().add("Set-Cookie", "SESSION=stolen");
      exchange.getResponseHeaders().add("X-Internal", "internal-detail");
      byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(status, bytes.length);
      exchange.getResponseBody().write(bytes);
      exchange.close();
    });
    api.start();
  }

  @AfterEach
  void stop() {
    api.stop(0);
  }

  private McpRelay relay(int perIpPerMinute) {
    return relay("http://127.0.0.1:" + api.getAddress().getPort() + "/mcp", perIpPerMinute);
  }

  private McpRelay relay(String mcpUrl, int perIpPerMinute) {
    WebProperties properties = new WebProperties(new WebProperties.Keycloak("http://127.0.0.1:1", "moai-works", "web", "x"),
        new WebProperties.Api("http://127.0.0.1:1/v1", mcpUrl), null, new WebProperties.Mcp(perIpPerMinute), null);
    return new McpRelay(properties, new JdkClientHttpRequestFactory(HttpClient.newHttpClient()), clock);
  }

  private static MockHttpServletRequest post(String path, String ip) {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
    request.setRemoteAddr(ip);
    request.setContentType("application/json");
    request.setContent("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}".getBytes(StandardCharsets.UTF_8));
    return request;
  }

  private MockHttpServletResponse call(McpRelay relay, MockHttpServletRequest request) throws IOException {
    MockHttpServletResponse response = new MockHttpServletResponse();
    relay.relay(request, response);
    return response;
  }

  @Test
  void 키와_허용한_헤더만_넘기고_클라이언트_IP_는_접속_주소로_새로_쓴다() throws IOException {
    MockHttpServletRequest request = post("/mcp/issue", "203.0.113.7");
    request.setQueryString("a=1");
    request.addHeader("Authorization", "Bearer mai-wks-test-key");
    request.addHeader("Mcp-Session-Id", "session-1");
    request.addHeader("MCP-Protocol-Version", "2025-06-18");
    request.addHeader("Accept", "application/json, text/event-stream");
    request.addHeader("Last-Event-ID", "event-9");
    request.addHeader("User-Agent", "claude-code/2.1");
    // 넘기지 않는 것 — 로그인 쿠키 · 꾸민 IP · 꾸민 요청 id · 신원 헤더
    request.addHeader("Cookie", "__Host-MOAI_WORKS_SESSION=abc");
    request.addHeader("X-Forwarded-For", "1.2.3.4");
    request.addHeader("X-Real-IP", "1.2.3.4");
    request.addHeader("Forwarded", "for=1.2.3.4");
    request.addHeader("X-Correlation-Id", "forged-id");
    request.addHeader("X-User-Id", "admin");

    MockHttpServletResponse response = call(relay(600), request);

    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(paths).containsExactly("/mcp/issue?a=1");
    assertThat(bodies.get(0)).contains("tools/list");
    Map<String, String> sent = seen.get(0);
    assertThat(sent.get("Authorization")).isEqualTo("Bearer mai-wks-test-key");
    assertThat(sent.get("Mcp-Session-Id")).isEqualTo("session-1");
    assertThat(sent.get("MCP-Protocol-Version")).isEqualTo("2025-06-18");
    assertThat(sent.get("Last-Event-ID")).isEqualTo("event-9");
    assertThat(sent.get("User-Agent")).isEqualTo("claude-code/2.1");
    // 클라이언트가 보낸 X-Forwarded-For 는 버리고 접속 주소 하나만 — api 는 믿는 중계의 이 값만 쓴다(ClientIp)
    assertThat(sent.get("X-Forwarded-For")).isEqualTo("203.0.113.7");
    assertThat(sent.get("X-Correlation-Id")).isNotBlank().isNotEqualTo("forged-id");
    assertThat(sent).doesNotContainKeys("Cookie", "X-User-Id", "X-Real-IP", "Forwarded");
    // 응답도 허용한 헤더만 — api 가 무엇을 붙여도 쿠키는 나가지 않는다
    assertThat(response.getHeader("Mcp-Session-Id")).isEqualTo("session-1");
    assertThat(response.getContentType()).startsWith("application/json");
    assertThat(response.getHeader("Set-Cookie")).isNull();
    assertThat(response.getHeader("X-Internal")).isNull();
    assertThat(response.getContentAsString()).contains("\"result\"");
  }

  @Test
  void 판정하지_않는다_키가_없어도_넘기고_api_의_401_을_머리글과_본문까지_그대로_돌려준다() throws IOException {
    MockHttpServletResponse response = call(relay(600), post("/mcp/issue/denied", "203.0.113.7"));

    assertThat(seen.get(0)).doesNotContainKey("Authorization");
    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(response.getHeader("WWW-Authenticate")).isEqualTo("Bearer error=\"invalid_token\"");
    assertThat(response.getContentType()).startsWith("application/problem+json");
    assertThat(response.getContentAsString(StandardCharsets.UTF_8))
        .isEqualTo("{\"status\":401,\"code\":\"MCP_KEY_EXPIRED\",\"detail\":\"만료된 키입니다.\"}");
    assertThat(response.getHeader("Set-Cookie")).isNull();
  }

  @Test
  void api_의_403_도_상태와_본문_그대로다() throws IOException {
    MockHttpServletRequest request = post("/mcp/project/forbidden", "203.0.113.7");
    request.addHeader("Authorization", "Bearer mai-wks-test-key");

    MockHttpServletResponse response = call(relay(600), request);

    assertThat(seen.get(0).get("Authorization")).isEqualTo("Bearer mai-wks-test-key");
    assertThat(response.getStatus()).isEqualTo(403);
    assertThat(response.getContentAsString(StandardCharsets.UTF_8)).contains("MCP_PRINCIPAL_INACTIVE")
        .contains("키 주인 계정을 쓸 수 없습니다.");
  }

  @Test
  void IP_마다_분당_요청_수를_넘으면_429_이고_1분이_지나면_풀린다() throws IOException {
    McpRelay relay = relay(3);
    for (int i = 0; i < 3; i++) {
      assertThat(call(relay, post("/mcp/issue", "198.51.100.1")).getStatus()).isEqualTo(200);
    }
    MockHttpServletResponse limited = call(relay, post("/mcp/issue", "198.51.100.1"));
    assertThat(limited.getStatus()).isEqualTo(429);
    assertThat(limited.getHeader("Retry-After")).isEqualTo("60");
    assertThat(limited.getContentAsString()).contains("MCP_RATE_LIMITED");
    // 꾸민 X-Forwarded-For 로는 비켜 가지 못한다 — 세는 것은 접속 주소다
    MockHttpServletRequest forged = post("/mcp/issue", "198.51.100.1");
    forged.addHeader("X-Forwarded-For", "192.0.2.99");
    assertThat(call(relay, forged).getStatus()).isEqualTo(429);
    // 막힌 요청은 api 에 닿지 않는다. 다른 IP 는 따로 센다
    assertThat(paths).hasSize(3);
    assertThat(call(relay, post("/mcp/issue", "198.51.100.2")).getStatus()).isEqualTo(200);

    clock.advance(Duration.ofSeconds(61));
    assertThat(call(relay, post("/mcp/issue", "198.51.100.1")).getStatus()).isEqualTo(200);
  }

  @Test
  void 세는_IP_의_수에_끝이_있다_요청이_끊긴_IP_의_자리는_치운다() {
    McpRelay relay = relay(600);
    for (int i = 0; i <= McpRelay.SWEEP_ABOVE; i++) {
      assertThat(relay.allow("10." + (i >> 16) + "." + ((i >> 8) & 255) + "." + (i & 255))).isTrue();
    }
    assertThat(relay.tracked()).isEqualTo(McpRelay.SWEEP_ABOVE + 1);
    clock.advance(Duration.ofMinutes(2));
    assertThat(relay.allow("192.0.2.1")).isTrue();
    assertThat(relay.tracked()).isEqualTo(1);
  }

  @Test
  void api_가_머리글을_주지_않으면_한도까지만_기다리고_504_로_알린다_스레드를_붙잡지_않는다() throws IOException {
    WebProperties properties = new WebProperties(new WebProperties.Keycloak("http://127.0.0.1:1", "moai-works", "web", "x"),
        new WebProperties.Api("http://127.0.0.1:1/v1", "http://127.0.0.1:" + api.getAddress().getPort() + "/mcp"), null,
        new WebProperties.Mcp(600), null);
    JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(HttpClient.newHttpClient());
    factory.setReadTimeout(Duration.ofMillis(300));
    McpRelay relay = new McpRelay(properties, factory, clock);
    MockHttpServletRequest request = post("/mcp/hang", "203.0.113.9");
    long started = System.nanoTime();
    MockHttpServletResponse response = call(relay, request);
    assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(3));
    assertThat(response.getStatus()).isEqualTo(504);
    assertThat(response.getContentAsString()).contains("API_TIMEOUT");
  }

  @Test
  void 알림_스트림_GET_은_api_로_보내지_않고_405_첨부_받기_GET_은_넘긴다() throws IOException {
    McpRelay relay = relay(600);
    MockHttpServletRequest stream = new MockHttpServletRequest("GET", "/mcp/issue");
    stream.setRemoteAddr("203.0.113.10");
    stream.addHeader("Accept", "text/event-stream");
    MockHttpServletResponse refused = call(relay, stream);
    assertThat(refused.getStatus()).isEqualTo(405);
    assertThat(refused.getHeader("Allow")).isEqualTo("POST, DELETE");
    assertThat(refused.getContentAsString()).contains("MCP_STREAM_NOT_SUPPORTED");
    assertThat(paths).isEmpty();

    MockHttpServletRequest download = new MockHttpServletRequest("GET", "/mcp/files/download");
    download.setRemoteAddr("203.0.113.10");
    assertThat(call(relay, download).getStatus()).isEqualTo(200);
    assertThat(paths).containsExactly("/mcp/files/download");
  }

  @Test
  void 밖으로_부르는_HTTP_는_연결과_응답_머리글에_한도가_있다() {
    assertThat(OutboundHttp.CONNECT_TIMEOUT).isPositive();
    assertThat(OutboundHttp.RESPONSE_TIMEOUT).isPositive().isLessThanOrEqualTo(Duration.ofMinutes(5));
  }

  @Test
  void api_에_닿지_못하면_502_로_알린다() throws IOException {
    MockHttpServletResponse response = call(relay("http://127.0.0.1:1/mcp", 600), post("/mcp/issue", "203.0.113.7"));
    assertThat(response.getStatus()).isEqualTo(502);
    assertThat(response.getContentType()).startsWith("application/problem+json");
    assertThat(response.getContentAsString()).contains("API_UNAVAILABLE");
  }
}
