package com.moai.works.api.mcplog;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.moai.works.api.mcpkey.McpKeyService;
import com.moai.works.api.mcplog.McpAccessRow.McpOutcome;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpStatelessServerFeatures.SyncToolSpecification;
import io.modelcontextprotocol.server.McpStatelessServerHandler;
import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

/**
 * MCP 요청 하나 = 접근 기록 한 줄 (FR-MCP-006, 2026-11-22 사용자: "MCP 키 발급을 해 주면 감사 데이터 모두 기록해야 해. 클라이언트 정보 자세하게.
 * 읽기, 수정, 삭제, 생성 모두").
 *
 * <p>데이터를 돌려주는 요청은 <b>줄을 저장한 뒤에야 결과를 돌려준다</b> — 저장하지 못하면 그 요청도 실패한다:
 * <ul>
 *   <li><b>도구 호출</b>({@link #wrap}): 도구와 이 줄을 한 트랜잭션에서 — 줄을 못 쓰면 도구가 한 쓰기도 되돌리고 JSON-RPC 오류
 *       ({@value #FAILED_CODE}). 도구가 오류를 돌려준 호출은 도구 쪽을 되돌린 뒤 따로 남긴다</li>
 *   <li><b>도구 목록</b>({@link #listed}): 목록 거르개가 처음 불릴 때 남긴다 — 못 쓰면 도구 목록 요청이 실패한다</li>
 *   <li><b>연결 시작</b>({@link #initializing}): MCP 서버가 만든 initialize 결과를 돌려주기 전에 남긴다 — 못 쓰면 500</li>
 * </ul>
 * 그 밖의 요청(키 검사 거절 · 알림 · ping · DELETE · 모르는 메서드 · 서버가 도구까지 넘기지 않은 호출 · 전송 단계 거절)은
 * {@link McpAccessLogFilter} 가 응답 뒤에 {@link #fallback} 으로 남긴다.
 *
 * <p>클라이언트 정보(일감 #240 — MCP 서버는 세션을 두지 않는다): 연결 시작 줄에는 그 본문의 {@code clientInfo} 를 모두 남기고,
 * (키, User-Agent) → 그 클라이언트 정보를 {@link McpClientRegistry} 에 둔다. 다른 줄은 같은 키 · 같은 User-Agent 로 찾은 것을 붙인다(최선의
 * 덧붙임 — 다시 띄운 뒤 첫 연결 시작 전까지는 비어 있다). User-Agent 와 요청의 {@code MCP-Protocol-Version} 머리글은 줄마다 그대로 남는다.
 */
@Component
public class McpAccessRecorder {

  private static final Logger log = LoggerFactory.getLogger(McpAccessRecorder.class);
  public static final String FAILED_CODE = "MCP_ACCESS_LOG_FAILED";
  private static final Set<String> SERVED_METHODS = Set.of("initialize", "ping", "tools/list", "tools/call");

  private final McpAccessLogService logs;
  private final McpClientRegistry clients;
  private final TransactionTemplate tx;
  private final JsonMapper json;
  /** 입구 경로마다 등록된 도구 이름 — 도구까지 가지 못한 호출이 "없는 도구"인지 "인자 오류"인지 가린다 */
  private final Map<String, Set<String>> toolsByPath = new ConcurrentHashMap<>();

  public McpAccessRecorder(McpAccessLogService logs, McpClientRegistry clients, PlatformTransactionManager txManager,
      JsonMapper json) {
    this.logs = logs;
    this.clients = clients;
    this.tx = new TransactionTemplate(txManager);
    this.json = json;
  }

  // ── 도구 호출 ─────────────────────────────────────────────────────

  /** 입구 하나의 도구들을 감싼다 — 호출마다 한 줄 */
  public List<SyncToolSpecification> wrap(String endpointPath, List<SyncToolSpecification> specs) {
    toolsByPath.put(endpointPath, Set.copyOf(specs.stream().map(s -> s.tool().name()).toList()));
    return specs.stream().map(s -> new SyncToolSpecification(s.tool(), recorded(s.callHandler()))).toList();
  }

  private BiFunction<McpTransportContext, CallToolRequest, CallToolResult> recorded(
      BiFunction<McpTransportContext, CallToolRequest, CallToolResult> handler) {
    return (context, request) -> toolCall(context, request, () -> handler.apply(context, request));
  }

  CallToolResult toolCall(McpTransportContext context, CallToolRequest request, Supplier<CallToolResult> call) {
    McpRequestInfo info = McpRequestInfo.from(context);
    if (info == null || !info.markRecorded()) {
      // 접근 기록 문맥 없이 도구를 부르지 않는다(남기지 못한 호출이 없게)
      throw failed(null);
    }
    McpOperation operation = McpOperation.ofTool(request.name());
    Outcome[] outcome = {null};
    try {
      CallToolResult result = tx.execute(status -> {
        CallToolResult r = call.get();
        outcome[0] = outcome(r);
        if (outcome[0].outcome() == McpOutcome.OK) {
          logs.insert(toolRow(info, request, operation, outcome[0]));
        } else {
          // 도구가 오류를 돌려줬다 — 도구 쪽에 남은 것이 있으면 되돌리고 줄은 따로 남긴다
          status.setRollbackOnly();
        }
        return r;
      });
      if (outcome[0].outcome() != McpOutcome.OK) {
        logs.insert(toolRow(info, request, operation, outcome[0]));
      }
      return result;
    } catch (RuntimeException e) {
      boolean logBroken = e instanceof McpAccessLogService.WriteFailed;
      if (!logBroken && (outcome[0] == null || outcome[0].outcome() == McpOutcome.OK)) {
        // 도구는 끝났는데 저장(커밋)이 실패했다 — 실패한 호출로 한 줄 남기고 요청을 실패시킨다
        tryInsert(toolRow(info, request, operation, new Outcome(McpOutcome.ERROR, 500, "INTERNAL_ERROR")));
      }
      log.error("[mcp] 도구 호출 {} 를 실패로 끝낸다 — {}", request.name(), logBroken ? "접근 기록을 저장하지 못함" : e.toString(),
          logBroken ? e.getCause() : e);
      throw logBroken ? failed(e) : e;
    }
  }

  private McpAccessRow toolRow(McpRequestInfo info, CallToolRequest request, McpOperation operation, Outcome outcome) {
    McpAccessRow row = base(info).method("tools/call", operation).result(outcome.outcome(), outcome.status(), outcome.code());
    row.client = client(info);
    target(row, request.name(), request.arguments());
    row.workspaceId = info.workspaceId();
    if (info.projectKey() != null) {
      row.projectKey = info.projectKey();
    }
    return row;
  }

  /** 도구 결과 → 결과 셋. 도구는 오류를 {@code {"error": {status, code, message}}} 로 돌려준다(REST 와 같은 code) */
  private Outcome outcome(CallToolResult r) {
    if (r == null || Boolean.TRUE.equals(r.isError())) {
      return new Outcome(McpOutcome.ERROR, 500, "TOOL_ERROR");
    }
    String text = r.content() == null ? null : r.content().stream()
        .filter(McpSchema.TextContent.class::isInstance).map(c -> ((McpSchema.TextContent) c).text()).findFirst().orElse(null);
    if (text != null && text.startsWith("{\"error\"")) {
      try {
        if (json.readValue(text, Map.class).get("error") instanceof Map<?, ?> error) {
          int status = error.get("status") instanceof Number n ? n.intValue() : 500;
          String code = error.get("code") instanceof String c ? c : null;
          return new Outcome(status == 401 || status == 403 ? McpOutcome.DENIED : McpOutcome.ERROR, status, code);
        }
      } catch (RuntimeException ignored) {
        // 오류 모양이 아니면 성공으로 본다
      }
    }
    return new Outcome(McpOutcome.OK, 200, null);
  }

  private record Outcome(McpOutcome outcome, int status, String code) {
  }

  // ── 도구 목록 ─────────────────────────────────────────────────────

  /** 도구 목록 거르개 — 요청마다 처음 불릴 때 한 줄. 늘 모든 도구를 보인다 */
  public boolean listed(McpTransportContext context, McpSchema.Tool tool) {
    McpRequestInfo info = McpRequestInfo.from(context);
    if (info == null) {
      throw failed(null);
    }
    if (info.markRecorded()) {
      McpAccessRow row = base(info).method("tools/list", McpOperation.LIST).result(McpOutcome.OK, 200, null);
      row.client = client(info);
      try {
        logs.insert(row);
      } catch (McpAccessLogService.WriteFailed e) {
        log.error("[mcp] 도구 목록을 실패로 끝낸다 — 접근 기록을 저장하지 못함", e.getCause());
        throw failed(e);
      }
    }
    return true;
  }

  // ── 연결 시작 ─────────────────────────────────────────────────────

  /**
   * MCP 서버의 처리기를 감싼다 — {@code initialize} 의 답을 돌려주기 전에 연결 시작 한 줄을 남기고, (키, User-Agent) → 클라이언트 정보를 둔다.
   * 줄을 못 쓰면 오류({@value #FAILED_CODE})가 전송까지 올라가 500 이 된다. 그 밖의 요청은 그대로 넘긴다.
   */
  public McpStatelessServerHandler initializing(McpStatelessServerHandler delegate) {
    return new McpStatelessServerHandler() {
      @Override
      public Mono<McpSchema.JSONRPCResponse> handleRequest(McpTransportContext context, McpSchema.JSONRPCRequest request) {
        Mono<McpSchema.JSONRPCResponse> response = delegate.handleRequest(context, request);
        if (!"initialize".equals(request.method())) {
          return response;
        }
        return response.map(r -> {
          initialized(McpRequestInfo.from(context), request, r);
          return r;
        });
      }

      @Override
      public Mono<Void> handleNotification(McpTransportContext context, McpSchema.JSONRPCNotification notification) {
        return delegate.handleNotification(context, notification);
      }
    };
  }

  private void initialized(McpRequestInfo info, McpSchema.JSONRPCRequest request, McpSchema.JSONRPCResponse response) {
    if (info == null || !info.markRecorded()) {
      throw failed(null);
    }
    McpClientInfo client = info.initClient();
    if (client.name() == null) {
      client = McpClientInfo.fromInitializeParams(request.params());
    }
    McpAccessRow row = base(info).method("initialize", McpOperation.CONNECT);
    if (response.error() == null) {
      row.result(McpOutcome.OK, 200, null);
    } else {
      // JSON-RPC 오류로 답한 연결 시작(params 꼴이 틀림 등) — 오류로 남기고 클라이언트 정보는 두지 않는다
      row.result(McpOutcome.ERROR, 200, rpcErrorCode(response.error()));
    }
    row.client = client;
    try {
      logs.insert(row);
    } catch (McpAccessLogService.WriteFailed e) {
      log.error("[mcp] 연결 시작을 실패로 끝낸다 — 접근 기록을 저장하지 못함", e.getCause());
      throw failed(e);
    }
    McpKeyService.KeyRef key = info.keyRef();
    if (response.error() == null && key != null) {
      clients.remember(key.id(), info.userAgent(), client);
    }
  }

  /** JSON-RPC 오류의 code — 우리 오류는 data.code, 아니면 JSON-RPC 번호로 */
  private static String rpcErrorCode(McpSchema.JSONRPCResponse.JSONRPCError error) {
    if (error.data() instanceof Map<?, ?> data && data.get("code") instanceof String code) {
      return McpSecrets.cut(code, 60);
    }
    return switch (error.code()) {
      case McpSchema.ErrorCodes.METHOD_NOT_FOUND -> "METHOD_NOT_FOUND";
      case McpSchema.ErrorCodes.INVALID_PARAMS -> "INVALID_PARAMS";
      case McpSchema.ErrorCodes.INVALID_REQUEST -> "INVALID_REQUEST";
      default -> "INTERNAL_ERROR";
    };
  }

  // ── 그 밖 (필터가 응답 뒤에) ───────────────────────────────────────

  /**
   * MCP 서버 안에서 남기지 않은 요청을 남긴다 — 키 검사 거절 · 알림 · ping · DELETE(세션 끝 — 끝낼 세션은 없다) · 스트림(GET) ·
   * 모르는 메서드 · 서버가 도구까지 넘기지 않은 도구 호출(없는 도구 · 인자 모양 오류) · 전송 단계 거절(깨진 본문 · Content-Type · Accept).
   *
   * @param status 보낸 HTTP 상태
   */
  void fallback(McpRequestInfo info, int status) {
    if (!info.markRecorded()) {
      return;
    }
    McpAccessRow row = base(info);
    String method = methodName(info);
    McpOperation operation = switch (method) {
      case "tools/call" -> McpOperation.ofTool(info.tool());
      case "tools/list" -> McpOperation.LIST;
      default -> McpOperation.CONNECT;
    };
    row.method(method, operation);
    if (info.rejected()) {
      row.result(McpOutcome.DENIED, info.rejectStatus(), info.rejectCode());
    } else if (status >= 400) {
      row.result(McpOutcome.ERROR, status, info.transportError() != null ? info.transportError() : statusCode(status));
    } else if ("tools/call".equals(method)) {
      // 도구를 감싼 곳까지 오지 않았다 — 서버가 JSON-RPC 오류로 답했다
      Set<String> tools = info.path() == null ? Set.of() : toolsByPath.getOrDefault(info.path(), Set.of());
      row.result(McpOutcome.ERROR, status, info.tool() == null ? "INVALID_PARAMS"
          : tools.contains(info.tool()) ? "INVALID_ARGUMENT" : "TOOL_NOT_FOUND");
    } else if (info.rpcRequest() && !SERVED_METHODS.contains(method)) {
      row.result(McpOutcome.ERROR, status, "METHOD_NOT_FOUND");
    } else {
      row.result(McpOutcome.OK, status, null);
    }
    if ("tools/call".equals(method)) {
      target(row, info.tool(), info.arguments());
    }
    // 거절된 연결 시작도 본문의 클라이언트 정보는 남긴다
    row.client = "initialize".equals(method) ? info.initClient() : client(info);
    logs.insert(row);
  }

  private static String methodName(McpRequestInfo info) {
    if ("DELETE".equalsIgnoreCase(info.httpMethod())) {
      return "session/end";
    }
    if ("GET".equalsIgnoreCase(info.httpMethod())) {
      return "session/stream";
    }
    return info.rpcMethod() == null ? "unknown" : info.rpcMethod();
  }

  private static String statusCode(int status) {
    return switch (status) {
      case 400 -> "INVALID_REQUEST";
      case 404 -> "NOT_FOUND";
      case 405 -> "METHOD_NOT_ALLOWED";
      case 406 -> "NOT_ACCEPTABLE";
      case 413 -> "PAYLOAD_TOO_LARGE";
      case 415 -> "UNSUPPORTED_MEDIA_TYPE";
      case 500 -> "INTERNAL_ERROR";
      case 503 -> "UNAVAILABLE";
      default -> "HTTP_" + status;
    };
  }

  // ── 공통 ──────────────────────────────────────────────────────────

  /** 받은 때의 값 · 키 · 주인 — 어느 요청에나 같다 */
  private McpAccessRow base(McpRequestInfo info) {
    McpAccessRow row = new McpAccessRow();
    row.occurredAt = info.receivedAt();
    row.durationMs = info.elapsedMillis();
    row.entry(info.entry());
    row.requestPath = info.path();
    McpKeyService.KeyRef key = info.keyRef();
    if (key != null) {
      row.keyId = key.id();
      row.keyPrefix = key.prefix();
      row.keyName = key.name();
      row.keyOwnerId = key.ownerId();
      row.keyOwnerName = key.ownerName();
    }
    row.sessionId = info.sessionId();
    row.userAgent = info.userAgent();
    row.clientIp = info.ip();
    row.traceId = info.traceId();
    return row;
  }

  /**
   * 연결 시작이 아닌 줄의 클라이언트 정보 — 같은 키 · 같은 User-Agent 가 마지막 연결 시작에서 밝힌 것(없으면 빈 것). 프로토콜 판은 이 요청의
   * {@code MCP-Protocol-Version} 머리글이 있으면 그것(요청마다 오는 실제 값)
   */
  private McpClientInfo client(McpRequestInfo info) {
    McpKeyService.KeyRef key = info.keyRef();
    McpClientInfo c = clients.recall(key == null ? null : key.id(), info.userAgent());
    return c.withProtocolVersion(McpSecrets.cut(McpSecrets.mask(info.protocolVersion()), 20));
  }

  /** 도구 인자에서 대상 — 프로젝트 key · 일감 번호 · 그 밖(연결 대상 · 첨부 · 템플릿 유형 · 모듈 key · 스킬 이름), 그리고 인자 요약 */
  private static void target(McpAccessRow row, String tool, Map<String, Object> args) {
    row.tool = tool == null ? null : McpSecrets.cut(tool, 60);
    if (args == null) {
      return;
    }
    if (args.get("project") instanceof String project && !project.isBlank()) {
      row.projectKey = McpSecrets.cut(project.trim(), 32);
    }
    Integer number = issueNumber(args.get("number"));
    if (number != null) {
      row.issueNumber = number;
    }
    Integer target = issueNumber(args.get("target"));
    if (target != null) {
      row.targetRef = "#" + target;
    } else if (args.get("attachmentId") instanceof String a && !a.isBlank()) {
      row.targetRef = "attachment:" + McpSecrets.cut(a.trim(), 80);
    } else if (args.get("commentId") instanceof String c && !c.isBlank()) {
      row.targetRef = "comment:" + McpSecrets.cut(c.trim(), 80);
    } else if (args.get("workLogId") instanceof String w && !w.isBlank()) {
      row.targetRef = "workLog:" + McpSecrets.cut(w.trim(), 80);
    } else if (tool != null && tool.contains("template") && args.get("type") instanceof String type && !type.isBlank()) {
      row.targetRef = "type:" + McpSecrets.cut(type.trim(), 80);
    } else if (tool != null && tool.contains("module") && args.get("key") instanceof String key && !key.isBlank()) {
      row.targetRef = "module:" + McpSecrets.cut(key.trim(), 80);
    } else if (tool != null && tool.contains("skill") && args.get("name") instanceof String name && !name.isBlank()) {
      // 스킬 받기(get_skill) — 어느 스킬의 어느 리비전을 받았는지(2026-11-24, 일감 #30)
      row.targetRef = "skill:" + McpSecrets.cut(name.trim(), 64)
          + (args.get("revision") instanceof Number rev ? "@" + rev.intValue() : "");
    }
    row.arguments = McpSecrets.summarizeArguments(args);
  }

  /** 일감 번호 인자 — 정수도, 숫자 글("12" · "#12")도 받는다(일감 #177 — 도구가 둘 다 받는다). 아니면 null */
  private static Integer issueNumber(Object value) {
    if (value instanceof Number n) {
      return n.intValue();
    }
    if (value instanceof String s) {
      String digits = s.trim().replaceFirst("^#\\s*", "");
      if (digits.matches("\\d{1,9}")) {
        return Integer.valueOf(digits);
      }
    }
    return null;
  }

  private void tryInsert(McpAccessRow row) {
    try {
      logs.insert(row);
    } catch (RuntimeException e) {
      log.error("[mcp] 실패한 호출의 접근 기록도 남기지 못했다", e);
    }
  }

  /** 접근 기록을 남기지 못해 요청을 처리하지 않았다는 JSON-RPC 오류 */
  static McpError failed(Throwable cause) {
    return McpError.builder(McpSchema.ErrorCodes.INTERNAL_ERROR)
        .message("MCP 접근 기록을 남기지 못해 요청을 처리하지 않았습니다 (" + FAILED_CODE + ")")
        .data(Map.of("code", FAILED_CODE))
        .build();
  }
}
