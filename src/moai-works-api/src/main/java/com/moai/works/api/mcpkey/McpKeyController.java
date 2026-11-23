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
 * MCP 키 발급 · 관리 (2026-11-22 사용자 결정 — ADR-022 · ADR-024, 2026-11-23 키는 사용자의 것 — ADR-029).
 *
 * <ul>
 *   <li>{@code /me/mcp-keys}: 내 키 — 받기(워크스페이스를 고르지 않는다, 원문은 응답에 한 번만) · 목록 · 만료시키기. 시스템 관리자 계정은
 *       막힌다({@link UserApi} 기본). {@code mcp.key.issue.personal} 이 있는 워크스페이스가 하나도 없으면 받을 수 없다</li>
 *   <li>{@code /workspaces/{workspace}/mcp-keys}: {@code mcp.key.manage}(기본 역할로는 소유자 · 관리자)가 그 워크스페이스 멤버들의 키를
 *       본다(만료된 것까지 발급 이력). 보기만 한다 — 키는 주인의 다른 워크스페이스에도 닿으므로 주인만 만료시킨다</li>
 *   <li>지우기는 없다</li>
 * </ul>
 */
@RestController
public class McpKeyController {

  private static final String MINE = EndPointPaths.V1 + "/me/mcp-keys";
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

  @PostMapping(MINE)
  @ResponseStatus(HttpStatus.CREATED)
  @UserApi
  public McpKeyService.Issued issue(CurrentUser user, @RequestBody IssueRequest r,
      @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
    return service.issue(user, r.name(), r.days(), userAgent);
  }

  /** 내 키 — @param status ACTIVE · EXPIRED · ALL(기본) */
  @GetMapping(MINE)
  @UserApi
  public List<McpKeyService.KeyView> mine(CurrentUser user, @RequestParam(required = false) String status) {
    return service.mine(user, status);
  }

  /** 내 키를 만료시킨다 — 남의 키는 404 */
  @PostMapping(MINE + "/{id}/expire")
  @UserApi
  public McpKeyService.KeyView expire(CurrentUser user, @PathVariable UUID id,
      @Valid @RequestBody(required = false) ExpireRequest r,
      @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
    return service.expire(user, id, r == null ? null : r.note(), userAgent);
  }

  /** 이 워크스페이스 멤버들의 키 — @param status ACTIVE · EXPIRED · ALL(기본) · @param owner 주인 사용자 id */
  @GetMapping(WS)
  @WorkspaceApi(Permission.MCP_KEY_MANAGE)
  public List<McpKeyService.KeyView> list(WorkspaceContext ctx, @RequestParam(required = false) String status,
      @RequestParam(required = false) UUID owner) {
    return service.forWorkspace(ctx, status, owner);
  }
}
