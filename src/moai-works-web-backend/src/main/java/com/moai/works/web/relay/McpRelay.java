package com.moai.works.web.relay;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.moai.works.web.config.SlidingWindowCounter;
import com.moai.works.web.config.WebProperties;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 바깥 MCP 클라이언트의 {@code /mcp/**} 를 api {@code /mcp/**} 로 그대로 흘려보낸다 (2026-11-22 사용자 결정 — MCP 설계 「중계」).
 *
 * <ul>
 *   <li>판정하지 않는다 — 키 검사 · 범위 · 권한 · 접근 기록은 api 한 곳</li>
 *   <li>로그인 세션 · CSRF · 쿠키를 쓰지 않는다. 세션의 사용자 토큰을 붙이지 않고, 요청의 {@code Authorization}(MCP 키)을 그대로 넘긴다</li>
 *   <li>헤더는 허용 목록만. 클라이언트가 보낸 X-Forwarded-For 는 버리고 실제 접속 IP 로 새로 쓴다. 요청 id 를 붙인다</li>
 *   <li>Streamable HTTP — POST 응답(JSON · SSE)과 GET SSE 를 버퍼에 담지 않고 조각마다 흘린다</li>
 *   <li>IP 별 분당 요청 수 제한(키 검사 전 앞단). 넘으면 429. 세는 IP 의 수에 끝을 둔다 — 1만 개를 넘으면 지난 1분에 요청이 없던
 *       IP 의 자리를 치운다({@link SlidingWindowCounter}, 2026-11-24 일감 #136 — moai-wiki 세션이 같은 자리를 찾아 알려 줌)</li>
 * </ul>
 */
@Component
public class McpRelay {

  private static final Logger log = LoggerFactory.getLogger(McpRelay.class);
  public static final String PREFIX = "/mcp";
  private static final Set<String> REQUEST_HEADERS = Set.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE,
      HttpHeaders.ACCEPT, "Mcp-Session-Id", "MCP-Protocol-Version", "Last-Event-ID", HttpHeaders.USER_AGENT);
  private static final List<String> RESPONSE_HEADERS = List.of(HttpHeaders.CONTENT_TYPE, "Mcp-Session-Id",
      HttpHeaders.WWW_AUTHENTICATE, HttpHeaders.RETRY_AFTER, HttpHeaders.CACHE_CONTROL,
      // 첨부 티켓 받기(/mcp/files/download) — 파일 이름 · 내용 해시 · 보안 머리글을 그대로 넘긴다
      HttpHeaders.CONTENT_DISPOSITION, HttpHeaders.ETAG, "X-Content-Type-Options", "Content-Security-Policy");
  private static final Set<String> BODY_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

  /** 이보다 많은 IP 를 세고 있으면 지난 1분에 요청이 없던 IP 의 자리를 치운다 (moai-wiki 와 같은 값) */
  static final int SWEEP_ABOVE = SlidingWindowCounter.SWEEP_ABOVE;

  private final RestClient rest;
  private final String mcpUrl;
  private final int perIpPerMinute;
  private final SlidingWindowCounter hits;

  public McpRelay(WebProperties properties, ClientHttpRequestFactory outboundRequestFactory, Clock clock) {
    this.rest = RestClient.builder().requestFactory(outboundRequestFactory).build();
    String url = properties.api().mcpUrl();
    this.mcpUrl = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    this.perIpPerMinute = properties.mcp() == null ? 600 : properties.mcp().perIpPerMinute();
    this.hits = new SlidingWindowCounter(Duration.ofMinutes(1), clock);
  }

  public void relay(HttpServletRequest request, HttpServletResponse response) throws IOException {
    String ip = request.getRemoteAddr();
    if (!allow(ip)) {
      response.setHeader(HttpHeaders.RETRY_AFTER, "60");
      Problems.write(response, 429, "MCP_RATE_LIMITED", "요청이 너무 많습니다. 잠시 뒤 다시 시도하십시오.");
      return;
    }
    String path = request.getRequestURI().substring(request.getContextPath().length());
    if (!path.equals(PREFIX) && !path.startsWith(PREFIX + "/")) {
      Problems.write(response, 404, "NOT_FOUND", "없는 주소입니다.");
      return;
    }
    if ("GET".equals(request.getMethod()) && !path.startsWith(PREFIX + "/files/")) {
      // 알림 스트림(GET)은 api 로 보내지 않는다 — api 도 405 지만 중계에서 한 번 더(moai-wiki 와 같은 이중 방어, 2026-11-24 일감 #239).
      // 첨부 받기(GET /mcp/files/download)만 넘긴다
      response.setHeader(HttpHeaders.ALLOW, "POST, DELETE");
      Problems.write(response, 405, "MCP_STREAM_NOT_SUPPORTED", "이 서버는 알림 스트림(GET)을 쓰지 않습니다. POST 로 부르십시오.");
      return;
    }
    String query = request.getQueryString();
    URI target = URI.create(mcpUrl + path.substring(PREFIX.length()) + (query == null ? "" : "?" + query));
    String requestId = UUID.randomUUID().toString();
    RestClient.RequestBodySpec spec = rest.method(HttpMethod.valueOf(request.getMethod())).uri(target)
        .headers(h -> {
          for (String name : REQUEST_HEADERS) {
            String value = request.getHeader(name);
            if (value != null) {
              h.set(name, value);
            }
          }
          // 클라이언트가 보낸 값은 버리고 실제 접속 IP 로 — api 가 믿는 중계에서 온 값만 쓴다
          h.set("X-Forwarded-For", ip);
          h.set("X-Correlation-Id", requestId);
        });
    if (BODY_METHODS.contains(request.getMethod()) && hasBody(request)) {
      spec.body(out -> request.getInputStream().transferTo(out));
    }
    try {
      spec.exchange((req, res) -> {
        response.setStatus(res.getStatusCode().value());
        for (String name : RESPONSE_HEADERS) {
          String value = res.getHeaders().getFirst(name);
          if (value != null) {
            response.setHeader(name, value);
          }
        }
        stream(res.getBody(), response.getOutputStream(), response);
        return null;
      });
    } catch (RestClientException e) {
      boolean timedOut = timedOut(e);
      log.error("MCP 중계 실패 {} {} ({}){}: {}", request.getMethod(), path, requestId, timedOut ? " — 응답 기다림 초과" : "", e.getMessage());
      if (!response.isCommitted()) {
        if (timedOut) {
          Problems.write(response, 504, "API_TIMEOUT", "서버가 제때 답하지 않았습니다. 잠시 뒤 다시 시도하십시오.");
        } else {
          Problems.write(response, 502, "API_UNAVAILABLE", "서버에 연결할 수 없습니다. 잠시 뒤 다시 시도하십시오.");
        }
      }
    }
  }

  /** 조각마다 바로 내보낸다 — SSE 가 버퍼에 갇히지 않게. 클라이언트가 끊으면 조용히 멈춘다 */
  private static void stream(InputStream body, OutputStream out, HttpServletResponse response) throws IOException {
    byte[] buffer = new byte[8192];
    try (InputStream in = body) {
      int n;
      while ((n = in.read(buffer)) != -1) {
        out.write(buffer, 0, n);
        response.flushBuffer();
      }
    }
  }

  /** api 가 머리글을 제때 주지 않아 끊었나(OutboundHttp 의 응답 기다림 한도) */
  private static boolean timedOut(Throwable e) {
    for (Throwable c = e; c != null; c = c.getCause()) {
      if (c instanceof java.net.http.HttpTimeoutException || c instanceof java.net.SocketTimeoutException) {
        return true;
      }
    }
    return false;
  }

  boolean allow(String ip) {
    return hits.tryAcquire(ip, perIpPerMinute);
  }

  /** 지금 세고 있는 IP 수 (시험용) */
  int tracked() {
    return hits.tracked();
  }

  private static boolean hasBody(HttpServletRequest request) {
    return request.getContentLengthLong() > 0 || request.getHeader(HttpHeaders.TRANSFER_ENCODING) != null;
  }
}
