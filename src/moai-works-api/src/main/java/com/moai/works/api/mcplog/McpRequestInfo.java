package com.moai.works.api.mcplog;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;


import com.moai.works.api.config.McpEndpoint;
import com.moai.works.api.mcpkey.McpKeyPrincipal;
import com.moai.works.api.mcpkey.McpKeyService;

import io.modelcontextprotocol.common.McpTransportContext;
import jakarta.servlet.http.HttpServletRequest;

/**
 * MCP HTTP 요청 하나의 접근 기록 재료 — {@link McpAccessLogFilter} 가 요청을 받자마자 만들어 request attribute 에 두고,
 * MCP 전송 문맥에도 실어(키 필터 · 도구 · {@code McpScope}) 채운 뒤 {@link McpAccessRecorder} 가 한 줄로 남긴다.
 *
 * <ul>
 *   <li>받은 때의 값(시각 · 경로 · JSON-RPC 메서드 · 도구 · 인자 · 클라이언트가 보낸 세션 id · User-Agent · {@code MCP-Protocol-Version} ·
 *       IP · trace_id)은 바뀌지 않는다. 서버는 세션을 두지 않는다(무상태 — 일감 #240) — 세션 id 는 클라이언트가 보냈으면 그대로 적을 뿐이다</li>
 *   <li>키 필터가 키(찾았으면 거절이어도) · 주인 · 거절 이유를, 도구가 정한 워크스페이스 · 프로젝트를, 전송이 요청을 거절한 code 를 채운다</li>
 *   <li>한 요청은 한 줄만 — {@link #markRecorded()} 로 먼저 맡은 쪽이 남긴다(도구 호출 · 도구 목록 · 연결 시작은 MCP 서버 안에서,
 *       그 밖은 필터가 응답 뒤에)</li>
 * </ul>
 * 키 원문은 받지 않는다.
 */
public final class McpRequestInfo {

  public static final String ATTRIBUTE = McpRequestInfo.class.getName();

  private final Instant receivedAt;
  private final long startNanos;
  private final String httpMethod;
  private final String path;
  private final McpEndpoint entry;
  private final String rpcMethod;
  private final boolean rpcRequest;
  private final String tool;
  private final Map<String, Object> arguments;
  private final McpClientInfo initClient;
  private final String sessionId;
  private final String userAgent;
  private final String protocolVersion;
  private final String ip;
  private final String traceId;

  private final AtomicBoolean recorded = new AtomicBoolean();
  private volatile McpKeyService.KeyRef key;
  private volatile McpKeyPrincipal principal;
  private volatile Integer rejectStatus;
  private volatile String rejectCode;
  private volatile UUID workspaceId;
  private volatile String projectKey;
  private volatile String transportError;

  /**
   * @param entry      들어온 입구 — 입구가 아닌 경로면 null
   * @param rpcMethod  JSON-RPC 메서드 — 본문이 없거나 풀지 못하면 null
   * @param rpcRequest 답을 받는 요청인가(id 가 있다) — 알림이면 false
   * @param tool       {@code tools/call} 의 도구 이름
   * @param arguments  {@code tools/call} 의 인자(요약은 기록할 때 한다)
   * @param initClient {@code initialize} 본문의 클라이언트 정보
   * @param sessionId  클라이언트가 보낸 {@code Mcp-Session-Id} — 서버가 준 것이 아니다(서버는 주지 않는다)
   * @param protocolVersion 클라이언트가 보낸 {@code MCP-Protocol-Version} 머리글
   */
  McpRequestInfo(Instant receivedAt, long startNanos, String httpMethod, String path, McpEndpoint entry,
      String rpcMethod, boolean rpcRequest, String tool, Map<String, Object> arguments, McpClientInfo initClient,
      String sessionId, String userAgent, String protocolVersion, String ip, String traceId) {
    this.receivedAt = receivedAt.truncatedTo(ChronoUnit.MILLIS);
    this.startNanos = startNanos;
    this.httpMethod = httpMethod;
    this.path = path;
    this.entry = entry;
    this.rpcMethod = rpcMethod;
    this.rpcRequest = rpcRequest;
    this.tool = tool;
    this.arguments = arguments;
    this.initClient = initClient == null ? McpClientInfo.NONE : initClient;
    this.sessionId = sessionId;
    this.userAgent = userAgent;
    this.protocolVersion = protocolVersion;
    this.ip = ip;
    this.traceId = traceId;
  }

  // ── 찾기 ──────────────────────────────────────────────────────────

  public static McpRequestInfo of(HttpServletRequest request) {
    return request.getAttribute(ATTRIBUTE) instanceof McpRequestInfo info ? info : null;
  }

  /** MCP 전송 문맥(도구 · 도구 목록 거르개)에서 */
  public static McpRequestInfo from(McpTransportContext context) {
    return context != null && context.get(ATTRIBUTE) instanceof McpRequestInfo info ? info : null;
  }

  // ── 채우기 ────────────────────────────────────────────────────────

  /** 키 필터 — 찾은 키(거절이어도). 틀린 키 · 키 없음이면 부르지 않는다 */
  public void key(McpKeyService.KeyRef key) {
    this.key = key;
  }

  /** 키 필터 — 통과한 호출자 */
  public void principal(McpKeyPrincipal principal) {
    this.principal = principal;
  }

  /** 키 필터 — 거절(401 · 403 · 404)과 그 code */
  public void reject(int status, String code) {
    this.rejectStatus = status;
    this.rejectCode = code;
  }

  /** {@code McpScope} — 이번 호출이 쓴 워크스페이스 · 프로젝트(키 주인의 지금 권한으로 정해진 것만) */
  public void resolved(UUID workspaceId, String projectKey) {
    this.workspaceId = workspaceId;
    this.projectKey = projectKey;
  }

  /** MCP 전송 — 요청을 HTTP 오류로 거절한 code(본문 · Accept · Content-Type …). 접근 기록 줄의 code 가 응답의 code 와 같게 */
  public void transportError(String code) {
    this.transportError = code;
  }

  /** 이 요청의 줄을 내가 남긴다 — 처음 부른 쪽만 true */
  boolean markRecorded() {
    return recorded.compareAndSet(false, true);
  }

  boolean recorded() {
    return recorded.get();
  }

  // ── 값 ────────────────────────────────────────────────────────────

  int elapsedMillis() {
    long ms = (System.nanoTime() - startNanos) / 1_000_000L;
    return (int) Math.min(Math.max(ms, 0), Integer.MAX_VALUE);
  }

  Instant receivedAt() {
    return receivedAt;
  }

  String httpMethod() {
    return httpMethod;
  }

  String path() {
    return path;
  }

  McpEndpoint entry() {
    return entry;
  }

  String rpcMethod() {
    return rpcMethod;
  }

  boolean rpcRequest() {
    return rpcRequest;
  }

  String tool() {
    return tool;
  }

  Map<String, Object> arguments() {
    return arguments;
  }

  McpClientInfo initClient() {
    return initClient;
  }

  String sessionId() {
    return sessionId;
  }

  String userAgent() {
    return userAgent;
  }

  String protocolVersion() {
    return protocolVersion;
  }

  String ip() {
    return ip;
  }

  String traceId() {
    return traceId;
  }

  McpKeyService.KeyRef keyRef() {
    return key;
  }

  McpKeyPrincipal principal() {
    return principal;
  }

  boolean rejected() {
    return rejectStatus != null;
  }

  Integer rejectStatus() {
    return rejectStatus;
  }

  String rejectCode() {
    return rejectCode;
  }

  UUID workspaceId() {
    return workspaceId;
  }

  String projectKey() {
    return projectKey;
  }

  String transportError() {
    return transportError;
  }
}
