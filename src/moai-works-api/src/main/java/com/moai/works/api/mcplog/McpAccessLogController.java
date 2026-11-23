package com.moai.works.api.mcplog;

import java.io.IOException;
import java.io.Writer;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.moai.works.api.audit.AuditService;
import com.moai.works.api.config.EndPointPaths;
import com.moai.works.api.mcpkey.McpApiKey;
import com.moai.works.api.mcpkey.McpKeyService;
import com.moai.works.api.security.access.CurrentUser;
import com.moai.works.api.security.access.UserApi;
import com.moai.works.api.security.access.WorkspaceApi;
import com.moai.works.api.security.access.WorkspaceContext;
import com.moai.works.api.workspace.Permission;

import jakarta.servlet.http.HttpServletResponse;

/**
 * MCP 접근 기록 보기 · 거르기 · 내려받기 (FR-MCP-010, 2026-11-22 사용자: "필터 기능도 잘 만들고, 당연히 워크스페이스의 권한이 있는 사용자는
 * 그 이력을 볼 수 있어야").
 *
 * <ul>
 *   <li>{@code /workspaces/{workspace}/mcp-access-log}: {@code audit.read} — 그 워크스페이스에서 쓴 줄만(도구가 그 워크스페이스로 정한 호출).
 *       없으면 403, 멤버가 아니면 404. 연결 시작 · 도구 목록 · 거절처럼 워크스페이스가 없는 줄은 여기에 나오지 않는다</li>
 *   <li>{@code /me/mcp-keys/{id}/access-log}: 키 주인 — 자기 키의 줄 전부(워크스페이스가 없는 줄 · 모든 워크스페이스). 남의 키는 404</li>
 *   <li>거르기는 모두 함께(AND): 키 · 사람(키 주인) · 도구 · 작업 종류 · 결과 · 클라이언트 이름 · 프로젝트 · 세션 · IP · 기간.
 *       최신순, 커서는 앞 쪽 마지막 줄의 시각 · id({@code before} · {@code beforeId}), 한 번에 최대 200줄</li>
 *   <li>{@code .csv}: 거른 결과(최대 10만 줄). 내려받기는 보내기 전에 감사 {@code mcp.access_log.export}(조건 · 줄 수)로 남는다</li>
 * </ul>
 */
@RestController
public class McpAccessLogController {

  private static final String WS = EndPointPaths.V1 + "/workspaces/{workspace}/mcp-access-log";
  private static final String MINE = EndPointPaths.V1 + "/me/mcp-keys/{id}/access-log";
  private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

  private final McpAccessLogService logs;
  private final McpKeyService keys;
  private final AuditService audit;

  public McpAccessLogController(McpAccessLogService logs, McpKeyService keys, AuditService audit) {
    this.logs = logs;
    this.keys = keys;
    this.audit = audit;
  }

  /**
   * 요청 조건 — 이름은 화면 주소의 조건과 같다. {@code operation} · {@code outcome} 은 쉼표로 여럿.
   *
   * @param limit 한 번에 받을 줄 수(기본 50, 최대 200)
   */
  public record Query(UUID key, UUID person, String tool, List<String> operation, List<String> outcome, String client,
      String project, String session, String ip, Instant from, Instant to, Instant before, UUID beforeId, Integer limit) {

    McpAccessLogService.Filter filter() {
      return new McpAccessLogService.Filter(key, person, tool, operation, outcome, client, project, session, ip, from, to,
          before, beforeId);
    }

    int pageSize() {
      return limit == null ? 50 : limit;
    }
  }

  // ── 워크스페이스 (audit.read) ─────────────────────────────────────

  @GetMapping(WS)
  @WorkspaceApi(Permission.AUDIT_READ)
  public List<McpAccessLogService.Entry> list(WorkspaceContext ctx, @ModelAttribute Query q) {
    return logs.search(McpAccessLogService.Scope.workspace(ctx.workspaceId()), q.filter(), q.pageSize());
  }

  @GetMapping(WS + ".csv")
  @WorkspaceApi(Permission.AUDIT_READ)
  public void export(WorkspaceContext ctx, CurrentUser user, @ModelAttribute Query q, HttpServletResponse response)
      throws IOException {
    McpAccessLogService.Scope scope = McpAccessLogService.Scope.workspace(ctx.workspaceId());
    McpAccessLogService.Filter filter = q.filter();
    Map<String, Object> details = exportDetails(filter, logs.count(scope, filter));
    details.put("workspace", ctx.workspace().getSlug());
    // 보내기 전에 감사 — 남기지 못하면 내려보내지 않는다
    audit.record(user.actor(), ctx.workspaceId(), "mcp.access_log.export", "workspace", ctx.workspaceId().toString(), details);
    write(response, "mcp-access-log-" + ctx.workspace().getSlug(), scope, filter);
  }

  // ── 내 키 (키 주인) ───────────────────────────────────────────────

  @GetMapping(MINE)
  @UserApi
  public List<McpAccessLogService.Entry> mine(CurrentUser user, @PathVariable UUID id, @ModelAttribute Query q) {
    McpApiKey key = keys.requireMine(user, id);
    return logs.search(McpAccessLogService.Scope.key(key.getId()), q.filter(), q.pageSize());
  }

  @GetMapping(MINE + ".csv")
  @UserApi
  public void exportMine(CurrentUser user, @PathVariable UUID id, @ModelAttribute Query q, HttpServletResponse response)
      throws IOException {
    McpApiKey key = keys.requireMine(user, id);
    McpAccessLogService.Scope scope = McpAccessLogService.Scope.key(key.getId());
    McpAccessLogService.Filter filter = q.filter();
    Map<String, Object> details = exportDetails(filter, logs.count(scope, filter));
    details.put("keyId", key.getId().toString());
    details.put("keyPrefix", key.getKeyPrefix());
    details.put("owner", key.getOwnerUserId().toString());
    // 키는 사용자의 것이라 사용자 단위 감사(워크스페이스 없음 — 키 발급 · 만료와 같다)
    audit.record(user.actor(), null, "mcp.access_log.export", "mcp_key", key.getId().toString(), details);
    write(response, "mcp-access-log-" + key.getKeyPrefix(), scope, filter);
  }

  // ── 도움 ──────────────────────────────────────────────────────────

  private static Map<String, Object> exportDetails(McpAccessLogService.Filter filter, int rows) {
    Map<String, Object> details = new LinkedHashMap<>();
    details.put("filters", filter.describe());
    details.put("rows", rows);
    return details;
  }

  private void write(HttpServletResponse response, String name, McpAccessLogService.Scope scope,
      McpAccessLogService.Filter filter) throws IOException {
    String file = name.replaceAll("[^A-Za-z0-9_-]", "_") + "-" + FILE_TIME.format(Instant.now()) + ".csv";
    response.setContentType("text/csv;charset=UTF-8");
    response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file + "\"");
    response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
    Writer out = response.getWriter();
    logs.export(scope, filter, out);
    out.flush();
  }
}
