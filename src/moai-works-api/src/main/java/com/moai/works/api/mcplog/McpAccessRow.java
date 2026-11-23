package com.moai.works.api.mcplog;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.moai.works.api.config.McpEndpoint;

/**
 * 접근 기록에 쓸 한 줄 — {@link McpAccessRecorder} 가 {@link McpRequestInfo} 에서 채우고 {@link McpAccessLogService#insert} 가 쓴다.
 * 표({@code mcp_access_log}, V19)의 열과 하나씩 맞는다. 키 원문 · 본문 칸은 없다.
 */
final class McpAccessRow {

  final UUID id = UUID.randomUUID();
  Instant occurredAt;
  int durationMs;
  UUID keyId;
  String keyPrefix;
  String keyName;
  UUID keyOwnerId;
  String keyOwnerName;
  String entry = "UNKNOWN";
  String requestPath;
  UUID workspaceId;
  String mcpMethod;
  String tool;
  McpOperation operation = McpOperation.CONNECT;
  String projectKey;
  Integer issueNumber;
  String targetRef;
  Map<String, Object> arguments;
  McpOutcome outcome = McpOutcome.OK;
  int httpStatus = 200;
  String errorCode;
  McpClientInfo client = McpClientInfo.NONE;
  String sessionId;
  String userAgent;
  String clientIp;
  String traceId;

  /** 결과 셋 — OK(성공) · DENIED(키 검사 거절 · 권한 없음 401/403) · ERROR(그 밖의 실패) */
  enum McpOutcome {
    OK,
    DENIED,
    ERROR
  }

  McpAccessRow entry(McpEndpoint endpoint) {
    this.entry = endpoint == null ? "UNKNOWN" : endpoint.name();
    return this;
  }

  McpAccessRow method(String method, McpOperation operation) {
    this.mcpMethod = method;
    this.operation = operation;
    return this;
  }

  McpAccessRow result(McpOutcome outcome, int httpStatus, String errorCode) {
    this.outcome = outcome;
    this.httpStatus = httpStatus;
    this.errorCode = errorCode;
    return this;
  }
}
