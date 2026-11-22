package com.moai.works.api.mcpkey;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.moai.works.api.config.EndPointPaths;
import com.moai.works.api.security.access.CurrentUser;
import com.moai.works.api.security.access.UserApi;
import com.moai.works.api.security.access.WorkspaceApi;
import com.moai.works.api.security.access.WorkspaceContext;
import com.moai.works.api.workspace.Permission;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

/**
 * MCP 키 발급 · 관리 (2026-11-22 사용자 결정 — ADR-022 · ADR-024).
 *
 * <ul>
 *   <li>키는 워크스페이스 안에서 받는다 — {@code mcp.key.issue.personal}(기본 역할 넷 모두). 원문은 발급 응답에 한 번만</li>
 *   <li>목록: {@code mcp.key.manage}(기본 역할로는 소유자 · 관리자)는 그 워크스페이스의 모든 키(만료된 것까지 발급 이력), 그 밖에는 내 키만</li>
 *   <li>만료시키기: 내 키, 또는 {@code mcp.key.manage} 면 어떤 키든. 지우기는 없다</li>
 *   <li>{@code /me/mcp-keys}: 모든 워크스페이스의 내 키 — 개인 설정 화면</li>
 * </ul>
 */
@RestController
public class McpKeyController {

  private static final String WS = EndPointPaths.V1 + "/workspaces/{workspace}/mcp-keys";

  private final McpKeyService service;

  public McpKeyController(McpKeyService service) {
    this.service = service;
  }

  /** @param days 기간(일) — 비우면 기본(90), 1~최대(365) */
  public record IssueRequest(String name, Integer days) {
  }

  public record ExpireRequest(@Size(max = 500) String note) {
  }

  @PostMapping(WS)
  @ResponseStatus(HttpStatus.CREATED)
  @WorkspaceApi(Permission.MCP_KEY_ISSUE_PERSONAL)
  public McpKeyService.Issued issue(WorkspaceContext ctx, CurrentUser user, @RequestBody IssueRequest r,
      @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
    return service.issue(ctx, user, r.name(), r.days(), userAgent);
  }

  /** @param status ACTIVE · EXPIRED · ALL(기본) · @param owner 주인 사용자 id (관리하는 사람만 뜻이 있다) */
  @GetMapping(WS)
  @WorkspaceApi(Permission.WORKSPACE_READ)
  public List<McpKeyService.KeyView> list(WorkspaceContext ctx, @RequestParam(required = false) String status,
      @RequestParam(required = false) UUID owner) {
    return service.list(ctx, status, owner);
  }

  @PostMapping(WS + "/{id}/expire")
  @WorkspaceApi(Permission.WORKSPACE_READ)
  public McpKeyService.KeyView expire(WorkspaceContext ctx, CurrentUser user, @PathVariable UUID id,
      @Valid @RequestBody(required = false) ExpireRequest r,
      @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
    return service.expire(ctx, id, r == null ? null : r.note(), user.actor(), userAgent);
  }

  /** 내 키 — 모든 워크스페이스. 시스템 관리자 계정은 키가 없으므로 막는다(@UserApi 기본) */
  @GetMapping(EndPointPaths.V1 + "/me/mcp-keys")
  @UserApi
  public List<McpKeyService.KeyView> mine(CurrentUser user, @RequestParam(required = false) String status) {
    return service.mine(user, status);
  }
}
