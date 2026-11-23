package com.moai.works.web.relay;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.moai.works.web.config.WebProperties;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * moai-works-api 로 요청을 그대로 넘긴다 — 사용자 토큰을 Authorization 에 싣는다.
 *
 * <p>규칙 (SeedSiteKeeper BFF 에서 본 문제를 막는다)
 * <ul>
 *   <li>브라우저 헤더는 <b>허용 목록</b>만 넘긴다. Cookie · Authorization · X-Forwarded-* · 신원 헤더는 버린다</li>
 *   <li>X-Forwarded-For 는 BFF 가 직접 정한다(브라우저 값 무시) — api 는 감사 기록에만 쓴다</li>
 *   <li>api 는 신원 헤더를 읽지 않는다. 신원은 토큰, 워크스페이스는 경로로만 정해진다</li>
 *   <li>요청 · 응답 본문은 버퍼에 다 담지 않고 흘려보낸다</li>
 * </ul>
 */
@Component
public class ApiRelay {

  private static final Logger log = LoggerFactory.getLogger(ApiRelay.class);
  private static final Set<String> REQUEST_HEADERS = Set.of(HttpHeaders.ACCEPT, HttpHeaders.ACCEPT_LANGUAGE,
      HttpHeaders.CONTENT_TYPE, HttpHeaders.IF_MATCH, HttpHeaders.IF_NONE_MATCH, HttpHeaders.RANGE, "X-Correlation-Id",
      // 브라우저 정보 — api 가 감사 기록(MCP 키 발급 · 만료시키기 등)에 남긴다. 판정에는 쓰지 않는다
      HttpHeaders.USER_AGENT);
  /**
   * api 응답에서 넘기는 머리글. 첨부 받기의 {@code Content-Security-Policy: default-src 'none'; sandbox} 와 nosniff 를 넘겨야
   * 브라우저가 그 응답을 화면용 정책이 아니라 api 가 정한 정책으로 받는다 — 빠지면 아래 보안 머리글 필터가 화면용 정책을 채운다
   * (Spring Security 의 머리글 필터는 이미 있는 머리글을 덮어쓰지 않는다). 2026-11-23 moai-wiki 세션이 같은 누락을 찾아 알려 줌.
   */
  private static final List<String> RESPONSE_HEADERS = List.of(HttpHeaders.CONTENT_TYPE,
      HttpHeaders.CONTENT_DISPOSITION, HttpHeaders.CACHE_CONTROL, HttpHeaders.ETAG, HttpHeaders.LAST_MODIFIED,
      HttpHeaders.RETRY_AFTER, "X-Correlation-Id", "Content-Security-Policy", "X-Content-Type-Options");
  private static final Set<String> BODY_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

  private final RestClient rest;
  private final String baseUrl;

  public ApiRelay(WebProperties properties, ClientHttpRequestFactory outboundRequestFactory) {
    this.rest = RestClient.builder().requestFactory(outboundRequestFactory).build();
    this.baseUrl = properties.api().baseUrl();
  }

  /**
   * 브라우저 요청을 api 경로 {@code path}(v1 아래)로 넘기고 응답을 그대로 돌려준다. token 이 null 이면 토큰 없이(공개 경로).
   *
   * @param onUnauthorized api 가 401(토큰 거절)을 돌려줬을 때 응답을 쓰기 전에 할 일 — 로그인 세션을 끝낸다. null 이면 없음
   */
  public void relay(HttpServletRequest request, HttpServletResponse response, String path, String token,
      Runnable onUnauthorized) throws IOException {
    String query = request.getQueryString();
    URI target = URI.create(baseUrl + path + (query == null ? "" : "?" + query));
    RestClient.RequestBodySpec spec = rest.method(HttpMethod.valueOf(request.getMethod())).uri(target)
        .headers(h -> {
          for (String name : REQUEST_HEADERS) {
            String value = request.getHeader(name);
            if (value != null) {
              h.set(name, value);
            }
          }
          h.set("X-Forwarded-For", request.getRemoteAddr());
          if (token != null) {
            h.setBearerAuth(token);
          }
        });
    if (BODY_METHODS.contains(request.getMethod()) && hasBody(request)) {
      spec.body(out -> request.getInputStream().transferTo(out));
    }
    try {
      spec.exchange((req, res) -> {
        if (res.getStatusCode().value() == 401 && onUnauthorized != null) {
          onUnauthorized.run();
        }
        response.setStatus(res.getStatusCode().value());
        for (String name : RESPONSE_HEADERS) {
          String value = res.getHeaders().getFirst(name);
          if (value != null) {
            response.setHeader(name, value);
          }
        }
        try (InputStream in = res.getBody()) {
          in.transferTo(response.getOutputStream());
        }
        return null;
      });
    } catch (RestClientException e) {
      log.error("api 전달 실패 {} {}: {}", request.getMethod(), path, e.getMessage());
      Problems.write(response, 502, "API_UNAVAILABLE", "서버에 연결할 수 없습니다. 잠시 뒤 다시 시도하십시오.");
    }
  }

  /** BFF 자신이 api 를 부를 때 (로그인 직후 /me/login 등) — 상태와 본문(JSON 문자열). */
  public Result call(HttpMethod method, String path, String token) {
    try {
      return rest.method(method).uri(URI.create(baseUrl + path))
          .headers(h -> {
            h.setBearerAuth(token);
            h.setAccept(List.of(MediaType.APPLICATION_JSON));
          })
          .exchange((req, res) -> new Result(res.getStatusCode().value(),
              new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8)));
    } catch (RestClientException e) {
      log.error("api 호출 실패 {} {}: {}", method, path, e.getMessage());
      return new Result(502, "{\"status\":502,\"code\":\"API_UNAVAILABLE\",\"detail\":\"서버에 연결할 수 없습니다.\"}");
    }
  }

  public record Result(int status, String body) {
    public boolean ok() {
      return status >= 200 && status < 300;
    }
  }

  private static boolean hasBody(HttpServletRequest request) {
    return request.getContentLengthLong() > 0 || request.getHeader(HttpHeaders.TRANSFER_ENCODING) != null;
  }
}
