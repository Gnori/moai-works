package com.moai.works.api.mcp;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.function.RequestPredicates;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import com.moai.works.api.mcplog.McpRequestInfo;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.server.McpStatelessServerHandler;
import io.modelcontextprotocol.server.McpTransportContextExtractor;
import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpStatelessServerTransport;
import reactor.core.Exceptions;
import reactor.core.publisher.Mono;

/**
 * MCP 전송 — Streamable HTTP, <b>무상태</b>(일감 #240). 입구 하나({@code /mcp/issue} · {@code /mcp/project})의 WebMvc 라우터이고,
 * MCP 서버({@code McpStatelessSyncServer})가 {@link #setMcpHandler} 로 처리기를 건다.
 *
 * <ul>
 *   <li>{@code POST} — JSON-RPC 요청 하나를 받아 처리기에 넘기고 {@code application/json} 으로 바로 답한다. 알림은 202. <b>세션 id 를 주지도
 *       요구하지도 않는다</b> — 클라이언트가 보낸 {@code Mcp-Session-Id}(api 를 다시 띄우기 전에 받은 낡은 값이어도)는 보지 않으므로, 다시 띄워도
 *       열려 있던 클라이언트가 그대로 일한다. 도구 · 판정 · 접근 기록은 요청마다 키로 정해진다</li>
 *   <li>{@code GET}(알림 스트림) — 405 {@code MCP_STREAM_NOT_SUPPORTED}, {@code Allow: POST, DELETE}. 먼저 보낼 알림이 없고, 붙잡아 두면
 *       web-backend 중계 스레드가 묶인다(일감 #239). MCP 규약상 405 면 클라이언트는 스트림 없이 일한다</li>
 *   <li>{@code DELETE}(세션 끝) — 끝낼 세션이 없어 아무것도 하지 않고 204. 다시 띄우기 전의 세션 id 를 든 클라이언트가 보내도 오류가 아니다</li>
 *   <li>그 밖의 HTTP 메서드 — 405 {@code MCP_METHOD_NOT_ALLOWED}</li>
 * </ul>
 *
 * <p>Spring AI 2.1.0 의 {@code WebMvcStatelessServerTransport} 와 같은 일을 하되 전송 단계 오류(깨진 JSON · 틀린 Content-Type · Accept ·
 * 처리 실패)를 우리 꼴 {@code {"code","message"}}(MCP 키 필터의 거절과 같은 꼴)로 답한다 — 그쪽은 {@code McpError} 예외 객체를 본문에 그대로
 * 넣어 {@code jsonRpcError} · {@code cause} · {@code localizedMessage} 같은 Java 예외 모양이 나간다. 거절한 code 는 접근 기록 줄에도 같게
 * 남는다({@link McpRequestInfo#transportError}). JSON-RPC 안의 오류(모르는 메서드 · 없는 도구)는 JSON-RPC 오류로 답한다({@link McpRpcErrors}).
 */
final class McpHttpTransport implements McpStatelessServerTransport {

  private static final Logger log = LoggerFactory.getLogger(McpHttpTransport.class);

  /** 이 서버가 받는 HTTP 메서드 — GET · 그 밖의 405 에 싣는다 */
  static final String ALLOW = "POST, DELETE";

  private final McpJsonMapper jsonMapper;
  private final String endpoint;
  private final McpTransportContextExtractor<ServerRequest> contextExtractor;
  private final UnaryOperator<McpStatelessServerHandler> decorator;
  private final RouterFunction<ServerResponse> routerFunction;
  private volatile McpStatelessServerHandler handler;
  private volatile boolean closing;

  /**
   * @param decorator MCP 서버가 건 처리기를 감싼다 — 연결 안내 · 접근 기록 · JSON-RPC 오류 꼴({@link McpServerConfiguration})
   */
  McpHttpTransport(McpJsonMapper jsonMapper, String endpoint, McpTransportContextExtractor<ServerRequest> contextExtractor,
      UnaryOperator<McpStatelessServerHandler> decorator) {
    this.jsonMapper = jsonMapper;
    this.endpoint = endpoint;
    this.contextExtractor = contextExtractor;
    this.decorator = decorator;
    this.routerFunction = RouterFunctions.route()
        .POST(endpoint, this::post)
        .GET(endpoint, this::stream)
        .DELETE(endpoint, this::end)
        .route(RequestPredicates.path(endpoint), this::otherMethod)
        .build();
  }

  RouterFunction<ServerResponse> routerFunction() {
    return routerFunction;
  }

  @Override
  public void setMcpHandler(McpStatelessServerHandler mcpHandler) {
    this.handler = decorator.apply(mcpHandler);
  }

  @Override
  public Mono<Void> closeGracefully() {
    return Mono.fromRunnable(() -> closing = true);
  }

  // ── POST — JSON-RPC 하나 ──────────────────────────────────────────

  private ServerResponse post(ServerRequest request) {
    McpStatelessServerHandler h = handler;
    if (closing || h == null) {
      return error(request, HttpStatus.SERVICE_UNAVAILABLE, "MCP_UNAVAILABLE", "MCP 서버가 멈추는 중입니다. 잠시 뒤 다시 부르십시오.");
    }
    if (!acceptsJson(request)) {
      return error(request, HttpStatus.NOT_ACCEPTABLE, "MCP_NOT_ACCEPTABLE",
          "Accept 에 application/json 이 있어야 합니다(Streamable HTTP: application/json, text/event-stream). 이 서버는 JSON 으로만 답합니다.");
    }
    if (!jsonBody(request)) {
      return error(request, HttpStatus.UNSUPPORTED_MEDIA_TYPE, "MCP_UNSUPPORTED_MEDIA_TYPE",
          "본문은 Content-Type: application/json 의 JSON-RPC 메시지여야 합니다.");
    }
    McpSchema.JSONRPCMessage message;
    try {
      String body = new String(request.servletRequest().getInputStream().readAllBytes(), StandardCharsets.UTF_8);
      message = McpSchema.deserializeJsonRpcMessage(jsonMapper, body);
    } catch (IOException | RuntimeException e) {
      log.debug("[mcp] 읽지 못한 본문 — {}", e.toString());
      message = null;
    }
    if (!(message instanceof McpSchema.JSONRPCRequest) && !(message instanceof McpSchema.JSONRPCNotification)) {
      // 깨진 JSON · 배열(묶음) · 응답(이 서버는 클라이언트에게 묻지 않는다) · method 없는 객체
      return error(request, HttpStatus.BAD_REQUEST, "MCP_INVALID_MESSAGE",
          "본문이 JSON-RPC 요청이나 알림 하나가 아닙니다(깨진 JSON · 묶음 · 응답은 받지 않습니다).");
    }
    McpTransportContext context = contextExtractor.extract(request);
    try {
      if (message instanceof McpSchema.JSONRPCRequest rpc) {
        McpSchema.JSONRPCResponse response = h.handleRequest(context, rpc)
            .contextWrite(c -> c.put(McpTransportContext.KEY, context))
            .block();
        if (response == null) {
          throw new IllegalStateException("처리기가 답을 주지 않았다: " + rpc.method());
        }
        return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).body(jsonMapper.writeValueAsBytes(response));
      }
      h.handleNotification(context, (McpSchema.JSONRPCNotification) message)
          .contextWrite(c -> c.put(McpTransportContext.KEY, context))
          .block();
      return ServerResponse.accepted().build();
    } catch (IOException | RuntimeException e) {
      return failed(request, Exceptions.unwrap(e));
    }
  }

  /** 처리 실패 — 우리 오류(JSON-RPC 오류의 data.code, 예: 접근 기록 저장 실패)면 그 code, 아니면 까닭을 감추고 서버 기록에 */
  private ServerResponse failed(ServerRequest request, Throwable e) {
    if (e instanceof McpError mcp && mcp.getJsonRpcError() != null
        && mcp.getJsonRpcError().data() instanceof Map<?, ?> data && data.get("code") instanceof String code) {
      return error(request, HttpStatus.INTERNAL_SERVER_ERROR, code, mcp.getJsonRpcError().message());
    }
    log.error("[mcp] {} 요청을 처리하지 못했다", endpoint, e);
    return error(request, HttpStatus.INTERNAL_SERVER_ERROR, "MCP_INTERNAL_ERROR", "요청을 처리하지 못했습니다. 서버 기록을 확인하십시오.");
  }

  /** Accept 가 없거나 JSON 을 받는다(application/json · application/* · *&#47;*) — text/event-stream 만이면 안 된다(SSE 로 답하지 않는다) */
  private static boolean acceptsJson(ServerRequest request) {
    List<MediaType> accept;
    try {
      accept = request.headers().accept();
    } catch (InvalidMediaTypeException e) {
      return false;
    }
    return accept.isEmpty() || accept.stream().anyMatch(m -> m.isCompatibleWith(MediaType.APPLICATION_JSON));
  }

  private static boolean jsonBody(ServerRequest request) {
    try {
      return request.headers().contentType().map(MediaType.APPLICATION_JSON::equalsTypeAndSubtype).orElse(false);
    } catch (InvalidMediaTypeException e) {
      return false;
    }
  }

  // ── GET · DELETE · 그 밖 ───────────────────────────────────────────

  private ServerResponse stream(ServerRequest request) {
    return error(request, HttpStatus.METHOD_NOT_ALLOWED, "MCP_STREAM_NOT_SUPPORTED",
        "이 서버는 알림 스트림(GET)을 쓰지 않습니다. POST 로 부르십시오.");
  }

  private ServerResponse end(ServerRequest request) {
    return ServerResponse.noContent().build();
  }

  private ServerResponse otherMethod(ServerRequest request) {
    return error(request, HttpStatus.METHOD_NOT_ALLOWED, "MCP_METHOD_NOT_ALLOWED",
        "MCP 입구는 POST(JSON-RPC 요청 · 알림)와 DELETE 만 받습니다.");
  }

  // ── 우리 꼴의 오류 ────────────────────────────────────────────────

  private ServerResponse error(ServerRequest request, HttpStatus status, String code, String message) {
    McpRequestInfo info = McpRequestInfo.of(request.servletRequest());
    if (info != null) {
      info.transportError(code);
    }
    Map<String, String> body = new LinkedHashMap<>();
    body.put("code", code);
    body.put("message", message);
    ServerResponse.BodyBuilder response = ServerResponse.status(status).contentType(MediaType.APPLICATION_JSON);
    if (status == HttpStatus.METHOD_NOT_ALLOWED) {
      response = response.header(HttpHeaders.ALLOW, ALLOW);
    }
    try {
      return response.body(jsonMapper.writeValueAsBytes(body));
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }
}
