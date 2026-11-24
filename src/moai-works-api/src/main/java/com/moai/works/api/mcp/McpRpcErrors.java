package com.moai.works.api.mcp;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpStatelessServerHandler;
import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.JSONRPCResponse.JSONRPCError;
import reactor.core.publisher.Mono;

/**
 * JSON-RPC 오류를 우리 꼴로 — MCP SDK 가 만든 오류(모르는 메서드 · 없는 도구 · 처리 중 예외)에 {@code data.code} 와 한국어 문구를 단다
 * (일감 #240). 도구가 돌려준 오류({@code {"error": {…, "code"}}} 결과)와 접근 기록 저장 실패({@code data.code = MCP_ACCESS_LOG_FAILED})처럼
 * 이미 {@code data.code} 가 있는 오류는 그대로 둔다.
 *
 * <p>처리 중 예외(-32603)는 SDK 가 예외 문구를 그대로 실어 보내 Java 클래스 이름 · 역직렬화 까닭이 새어 나갈 수 있다 — 문구는 감추고 서버
 * 기록에만 남긴다. SDK 가 답(Mono)을 만들기 전에 던진 예외는 {@code params} 를 풀지 못한 것이다(예: {@code tools/call} 의 params 가 글 ·
 * 도구 이름이 없음) — HTTP 500 대신 JSON-RPC {@code INVALID_PARAMS} 로 답한다.
 */
final class McpRpcErrors {

  private static final Logger log = LoggerFactory.getLogger(McpRpcErrors.class);

  private McpRpcErrors() {
  }

  static McpStatelessServerHandler wrap(McpStatelessServerHandler delegate) {
    return new McpStatelessServerHandler() {
      @Override
      public Mono<McpSchema.JSONRPCResponse> handleRequest(McpTransportContext context, McpSchema.JSONRPCRequest request) {
        Mono<McpSchema.JSONRPCResponse> response;
        try {
          response = delegate.handleRequest(context, request);
        } catch (RuntimeException e) {
          JSONRPCError error;
          if (e instanceof McpError mcp && mcp.getJsonRpcError() != null) {
            error = mcp.getJsonRpcError();
          } else {
            log.debug("[mcp] {} 의 params 를 풀지 못했다 — {}", request.method(), e.toString());
            error = new JSONRPCError(McpSchema.ErrorCodes.INVALID_PARAMS, "params", null);
          }
          response = Mono.just(McpSchema.JSONRPCResponse.error(request.id(), error));
        }
        return response.map(r -> normalize(request, r));
      }

      @Override
      public Mono<Void> handleNotification(McpTransportContext context, McpSchema.JSONRPCNotification notification) {
        return delegate.handleNotification(context, notification);
      }
    };
  }

  static McpSchema.JSONRPCResponse normalize(McpSchema.JSONRPCRequest request, McpSchema.JSONRPCResponse response) {
    JSONRPCError error = response.error();
    if (error == null || (error.data() instanceof Map<?, ?> data && data.get("code") instanceof String)) {
      return response;
    }
    int code = error.code();
    JSONRPCError ours = switch (code) {
      case McpSchema.ErrorCodes.METHOD_NOT_FOUND -> ours(code, "METHOD_NOT_FOUND",
          "모르는 메서드입니다: " + request.method() + " — 이 서버는 initialize · ping · tools/list · tools/call 을 받습니다.");
      case McpSchema.ErrorCodes.INVALID_PARAMS -> error.message() != null && error.message().startsWith("Unknown tool")
          ? ours(code, "TOOL_NOT_FOUND", "없는 도구입니다: " + toolName(request) + " — tools/list 로 이름을 확인하십시오.")
          : ours(code, "INVALID_PARAMS", "params 의 꼴이 틀립니다.");
      case McpSchema.ErrorCodes.INVALID_REQUEST -> ours(code, "INVALID_REQUEST", "JSON-RPC 요청의 꼴이 틀립니다.");
      default -> {
        log.warn("[mcp] {} 를 처리하지 못했다 — {} {}", request.method(), code, error.message());
        yield ours(McpSchema.ErrorCodes.INTERNAL_ERROR, "INTERNAL_ERROR", "요청을 처리하지 못했습니다. params 의 꼴을 확인하십시오.");
      }
    };
    return McpSchema.JSONRPCResponse.error(response.id(), ours);
  }

  private static JSONRPCError ours(int rpcCode, String code, String message) {
    return new JSONRPCError(rpcCode, message, Map.of("code", code));
  }

  private static String toolName(McpSchema.JSONRPCRequest request) {
    if (request.params() instanceof Map<?, ?> params && params.get("name") instanceof String name && !name.isBlank()) {
      return name.length() <= 60 ? name : name.substring(0, 60) + "…";
    }
    return "(이름 없음)";
  }
}
