package com.moai.works.api.mcplog;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import com.moai.works.api.config.McpEndpoint;
import com.moai.works.api.config.MoaiWorksProperties;
import com.moai.works.api.security.ClientIp;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

/**
 * MCP 요청마다 접근 기록 재료({@link McpRequestInfo})를 만들고, MCP 서버 안에서 남기지 않은 요청을 응답 뒤에 남긴다(FR-MCP-006).
 * MCP 키 필터보다 <b>앞</b>에 둔다 — 키 검사에서 거절된 요청(키 없음 · 틀림 · 만료 · 주인 계정 정지 · 없는 입구)도 남기기 위해서다.
 *
 * <ul>
 *   <li>본문(JSON-RPC)을 한 번 읽어 두고 뒤(MCP 전송)에 그대로 다시 준다 — 메서드 · 도구 · 인자 · {@code initialize} 의 클라이언트 정보를 꺼낸다</li>
 *   <li>IP 는 {@link ClientIp}(믿는 중계의 X-Forwarded-For 만), trace_id 는 믿는 중계가 붙인 {@code X-Correlation-Id}(없으면 새로)</li>
 *   <li>도구 호출 · 도구 목록 · 연결 시작은 MCP 서버 안({@link McpAccessRecorder})이 결과를 돌려주기 전에 남긴다. 나머지는 여기서 응답 뒤에 —
 *       응답을 아직 보내지 않았으면 저장 실패 때 500 으로 바꾸고, 이미 보냈으면(스트림) 서버 로그에 오류를 남긴다</li>
 * </ul>
 * {@code /mcp/files/**}(일회용 첨부 티켓)는 MCP 키 요청이 아니라 보지 않는다. {@code @Component} 로 두지 않는다 — 보안 설정이 만든다.
 */
public class McpAccessLogFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger(McpAccessLogFilter.class);
  private static final String TICKET_PATHS = "/mcp/files/";
  private static final Pattern TRACE_ID = Pattern.compile("[A-Za-z0-9._:-]{1,64}");

  private final MoaiWorksProperties.Mcp mcp;
  private final McpAccessRecorder recorder;
  private final ClientIp clientIp;
  private final JsonMapper json;

  public McpAccessLogFilter(MoaiWorksProperties.Mcp mcp, McpAccessRecorder recorder, ClientIp clientIp, JsonMapper json) {
    this.mcp = mcp;
    this.recorder = recorder;
    this.clientIp = clientIp;
    this.json = json;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return request.getRequestURI().startsWith(TICKET_PATHS);
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    Instant receivedAt = Instant.now();
    long start = System.nanoTime();
    HttpServletRequest req = request;
    Map<String, Object> message = null;
    if ("POST".equalsIgnoreCase(request.getMethod())) {
      byte[] body = request.getInputStream().readAllBytes();
      req = new CachedBodyRequest(request, body);
      message = parse(body);
    }
    String path = request.getRequestURI();
    McpEndpoint entry = mcp.endpointOf(path);
    String rpcMethod = message == null ? null : text(message.get("method"));
    Map<String, Object> params = message != null && message.get("params") instanceof Map<?, ?> p ? cast(p) : null;
    String tool = "tools/call".equals(rpcMethod) && params != null ? text(params.get("name")) : null;
    Map<String, Object> arguments = "tools/call".equals(rpcMethod) && params != null && params.get("arguments") instanceof Map<?, ?> a
        ? cast(a) : null;
    McpClientInfo initClient = "initialize".equals(rpcMethod) ? McpClientInfo.fromInitializeParams(params) : null;
    McpRequestInfo info = new McpRequestInfo(receivedAt, start, request.getMethod(), path, entry, rpcMethod,
        message != null && message.containsKey("id") && rpcMethod != null, tool, arguments, initClient,
        header(request, "Mcp-Session-Id", 100), header(request, HttpHeaders.USER_AGENT, 500), clientIp.of(request),
        traceId(request));
    req.setAttribute(McpRequestInfo.ATTRIBUTE, info);

    chain.doFilter(req, response);

    if (info.recorded()) {
      return;
    }
    try {
      recorder.fallback(info, response.getStatus());
    } catch (RuntimeException e) {
      log.error("[mcp] 접근 기록을 저장하지 못했다 — {} {} ({})", request.getMethod(), path, info.rpcMethod(), e);
      if (!response.isCommitted() && !info.rejected()) {
        response.resetBuffer();
        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"code\":\"" + McpAccessRecorder.FAILED_CODE
            + "\",\"message\":\"MCP 접근 기록을 남기지 못해 요청을 처리하지 않았습니다.\"}");
      }
    }
  }

  /** JSON-RPC 한 통 — 객체가 아니면(묶음 · 깨진 JSON) null */
  private Map<String, Object> parse(byte[] body) {
    if (body.length == 0) {
      return null;
    }
    try {
      Object value = json.readValue(body, Object.class);
      return value instanceof Map<?, ?> m ? cast(m) : null;
    } catch (RuntimeException e) {
      return null;
    }
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> cast(Map<?, ?> map) {
    Map<String, Object> out = new LinkedHashMap<>();
    ((Map<Object, Object>) map).forEach((k, v) -> out.put(String.valueOf(k), v));
    return out;
  }

  private static String text(Object value) {
    return value instanceof String s && !s.isBlank() ? McpSecrets.cut(s.trim(), 60) : null;
  }

  private static String header(HttpServletRequest request, String name, int max) {
    String v = request.getHeader(name);
    return v == null || v.isBlank() ? null : McpSecrets.cut(v.trim(), max);
  }

  /** 믿는 중계가 붙인 요청 id 를 이어 쓴다 — 직접 온 요청이 보낸 값은 쓰지 않고 새로 만든다 */
  private String traceId(HttpServletRequest request) {
    String v = request.getHeader("X-Correlation-Id");
    if (v != null && clientIp.fromTrustedProxy(request) && TRACE_ID.matcher(v.trim()).matches()) {
      return v.trim();
    }
    return UUID.randomUUID().toString();
  }

  /** 읽어 둔 본문을 다시 주는 요청 */
  static final class CachedBodyRequest extends HttpServletRequestWrapper {

    private final byte[] body;

    CachedBodyRequest(HttpServletRequest request, byte[] body) {
      super(request);
      this.body = body;
    }

    @Override
    public ServletInputStream getInputStream() {
      ByteArrayInputStream in = new ByteArrayInputStream(body);
      return new ServletInputStream() {
        @Override
        public int read() {
          return in.read();
        }

        @Override
        public int read(byte[] b, int off, int len) {
          return in.read(b, off, len);
        }

        @Override
        public boolean isFinished() {
          return in.available() == 0;
        }

        @Override
        public boolean isReady() {
          return true;
        }

        @Override
        public void setReadListener(ReadListener listener) {
          throw new UnsupportedOperationException("읽어 둔 본문은 비동기로 읽지 않는다");
        }
      };
    }

    @Override
    public BufferedReader getReader() {
      String encoding = getCharacterEncoding();
      Charset charset = encoding == null ? StandardCharsets.UTF_8 : Charset.forName(encoding);
      return new BufferedReader(new InputStreamReader(getInputStream(), charset));
    }

    @Override
    public int getContentLength() {
      return body.length;
    }

    @Override
    public long getContentLengthLong() {
      return body.length;
    }
  }
}
