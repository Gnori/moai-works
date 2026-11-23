package com.moai.works.api.mcp;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.stereotype.Component;

import com.moai.works.api.common.Actor;
import com.moai.works.api.common.ApiException;
import com.moai.works.api.mcpkey.McpKeyPrincipal;
import com.moai.works.api.mcplog.McpRequestInfo;
import com.moai.works.api.project.ProjectAccessService;
import com.moai.works.api.security.access.ProjectContext;
import com.moai.works.api.security.access.WorkspaceContext;
import com.moai.works.api.workspace.BuiltinRole;
import com.moai.works.api.workspace.Permission;
import com.moai.works.api.workspace.Workspace;
import com.moai.works.api.workspace.WorkspaceAccessService;
import com.moai.works.api.workspace.WorkspaceMember;
import com.moai.works.api.workspace.WorkspaceRepositories;
import com.moai.works.api.workspace.WorkspaceRole;

/**
 * MCP 도구의 범위 — 모델이 넘긴 인자가 아니라 키 문맥(키 주인)에서 정하고, 호출마다 키 주인의 지금 권한을 다시 본다.
 * 모든 MCP 도구가 이 한 곳을 쓴다(REST 의 접근 어노테이션 자리).
 *
 * <p>키는 사용자의 것이다(2026-11-23 사용자 결정, ADR-029 — "키는 로그인한 사용자 기준으로 받는 것이고 그 사용자가 소속된 워크스페이스,
 * 소속된 프로젝트는 모두 접근 가능하고 권한에 따라 하는 거야").
 * <ul>
 *   <li><b>키로 쓸 수 있는 워크스페이스</b> = 활성 워크스페이스 ∧ 주인이 활성 멤버 ∧ 주인의 지금 역할에 {@code mcp.key.issue.personal}.
 *       그 밖의 워크스페이스는 키에게 보이지 않는다 — 워크스페이스 관리자는 역할에서 이 권한을 빼서 Agent 를 막는다.
 *       시스템 관리자가 사용 중지한 워크스페이스(SUSPENDED) · 지운 워크스페이스도 활성이 아니라 보이지 않는다 — 목록에서 빠지고, 이름을 줘도
 *       없는 것과 같은 404 다(REST 는 멤버에게 403 WORKSPACE_SUSPENDED 로 알리지만 MCP 는 숨긴다). 다시 사용하게 하면 다음 호출부터 돌아온다</li>
 *   <li>{@code workspace} 를 주면 그곳이 쓸 수 있는 곳이어야 한다(아니면 404 {@code WORKSPACE_NOT_FOUND} — 없는 것과 구별하지 않는다)</li>
 *   <li>비우면 쓸 수 있는 워크스페이스 모두에서 프로젝트 key 를 찾는다 — 하나면 그것, 없으면 {@code PROJECT_NOT_FOUND},
 *       여럿이면 {@code PROJECT_AMBIGUOUS}(문구에 워크스페이스 주소들)</li>
 * </ul>
 * 판정은 요청마다 DB 사본으로 한다 — 멤버십을 빼거나 정지하거나 역할을 바꾸면 다음 호출부터 반영된다.
 */
@Component
public class McpScope {

  /** 도구의 {@code workspace} 인자 설명 — 프로젝트를 받는 도구마다 같은 글을 쓴다 */
  public static final String WORKSPACE_PARAM = "워크스페이스 주소(slug, list_workspaces 의 slug) — 비우면 키로 일할 수 있는 모든 워크스페이스에서 "
      + "찾는다. 같은 프로젝트 key 가 여러 워크스페이스에 있으면(PROJECT_AMBIGUOUS) 넣는다";

  private final WorkspaceRepositories.Workspaces workspaces;
  private final WorkspaceRepositories.Members members;
  private final WorkspaceRepositories.Roles roles;
  private final WorkspaceAccessService workspaceAccess;
  private final ProjectAccessService projectAccess;

  public McpScope(WorkspaceRepositories.Workspaces workspaces, WorkspaceRepositories.Members members,
      WorkspaceRepositories.Roles roles, WorkspaceAccessService workspaceAccess, ProjectAccessService projectAccess) {
    this.workspaces = workspaces;
    this.members = members;
    this.roles = roles;
    this.workspaceAccess = workspaceAccess;
    this.projectAccess = projectAccess;
  }

  public McpKeyPrincipal principal(ToolContext toolContext) {
    McpKeyPrincipal p = McpCallContext.of(toolContext).principal();
    if (p == null) {
      throw ApiException.forbidden("MCP_KEY_REQUIRED", "MCP 키 문맥이 없습니다.");
    }
    return p;
  }

  // ── 워크스페이스 ──────────────────────────────────────────────────

  /**
   * 이 사람이 MCP 키로 일할 수 있는 워크스페이스들의 지금 문맥 — 개인 워크스페이스 먼저, 그다음 이름 순.
   * 키 발급(적어도 한 곳은 있어야 한다)과 도구가 함께 쓴다.
   */
  public List<WorkspaceContext> usableWorkspaces(UUID userId) {
    List<UUID> ids = members.findByUser(userId).stream().filter(WorkspaceMember::isActive)
        .map(WorkspaceMember::getWorkspaceId).toList();
    return workspaces.findAllById(ids).stream()
        .sorted(Comparator.comparing((Workspace w) -> w.getKind() != Workspace.Kind.PERSONAL)
            .thenComparing(Workspace::getName).thenComparing(Workspace::getSlug))
        .map(w -> usable(w, userId)).flatMap(Optional::stream).toList();
  }

  /**
   * 그 워크스페이스를 이 사람이 MCP 키로 쓸 수 있으면 지금 문맥, 아니면 empty(키에게 보이지 않는다) — 첨부 티켓도 이것으로 다시 본다.
   * 활성이 아니면(사용 중지 · 지움 · 만드는 중) 멤버여도 empty 다.
   */
  public Optional<WorkspaceContext> usable(Workspace ws, UUID userId) {
    if (ws == null || !ws.isActive()) {
      return Optional.empty();
    }
    return workspaceAccess.contextFor(ws, userId).filter(ctx -> ctx.has(Permission.MCP_KEY_ISSUE_PERSONAL));
  }

  /** 키로 쓸 수 있는 워크스페이스들 */
  public List<WorkspaceContext> workspaces(ToolContext toolContext) {
    return usableWorkspaces(principal(toolContext).ownerUserId());
  }

  /**
   * {@code workspace} 인자의 문맥 — 주면 그곳(쓸 수 없으면 404 {@code WORKSPACE_NOT_FOUND}). 비우면 쓸 수 있는 곳이 하나일 때 그곳,
   * 없으면 {@code WORKSPACE_NOT_FOUND}, 여럿이면 400 {@code WORKSPACE_REQUIRED}(문구에 워크스페이스 주소들).
   * 프로젝트 없이 워크스페이스에서 하는 일(워크스페이스 기본 템플릿)이 쓴다.
   */
  public WorkspaceContext workspace(ToolContext toolContext, String workspace) {
    McpKeyPrincipal p = principal(toolContext);
    String slug = blankToNull(workspace);
    if (slug != null) {
      return resolved(toolContext, named(p, slug));
    }
    List<WorkspaceContext> all = usableWorkspaces(p.ownerUserId());
    if (all.size() == 1) {
      return resolved(toolContext, all.get(0));
    }
    if (all.isEmpty()) {
      throw ApiException.notFound("WORKSPACE_NOT_FOUND", "이 MCP 키로 일할 수 있는 워크스페이스가 없습니다.");
    }
    throw ApiException.badRequest("WORKSPACE_REQUIRED",
        "워크스페이스가 여럿입니다 — workspace 에 다음 가운데 하나를 넣어 다시 부르십시오: " + slugs(all));
  }

  /** 이름(주소)으로 고른 워크스페이스 — 없거나 · 멤버가 아니거나 · MCP 권한이 없으면 모두 같은 404 */
  private WorkspaceContext named(McpKeyPrincipal p, String slug) {
    return workspaces.findBySlug(slug).flatMap(w -> usable(w, p.ownerUserId()))
        .orElseThrow(() -> ApiException.notFound("WORKSPACE_NOT_FOUND", "워크스페이스를 찾을 수 없습니다: " + slug));
  }

  /** 그 워크스페이스에서 이 사람이 가진 역할 이름들 — 기본 역할 순서(소유자 · 관리자 · 큐레이터 · 멤버) 다음 사용자 정의 역할 */
  public List<String> roleNames(WorkspaceContext ws) {
    Map<String, String> names = roles.findByWorkspaceIdOrderByBuiltinDescRoleKeyAsc(ws.workspaceId()).stream()
        .collect(Collectors.toMap(WorkspaceRole::getRoleKey, WorkspaceRole::getName, (a, b) -> a));
    return ws.roleKeys().stream()
        .sorted(Comparator.comparingInt((String k) -> BuiltinRole.ofKey(k).map(Enum::ordinal).orElse(100))
            .thenComparing(k -> names.getOrDefault(k, k)))
        .map(k -> names.getOrDefault(k, k)).toList();
  }

  // ── 프로젝트 ──────────────────────────────────────────────────────

  /**
   * 키 주인의 지금 권한으로 그 프로젝트의 문맥 — 독자가 아니면 PROJECT_NOT_FOUND (REST 와 같다).
   *
   * @param workspace 워크스페이스 주소 — 비우면 쓸 수 있는 모든 워크스페이스에서 찾는다(여럿이면 PROJECT_AMBIGUOUS)
   */
  public ProjectContext project(ToolContext toolContext, String workspace, String key) {
    McpKeyPrincipal p = principal(toolContext);
    String projectKey = key == null ? "" : key.trim();
    String slug = blankToNull(workspace);
    if (slug != null) {
      return resolved(toolContext, projectAccess.contextFor(named(p, slug), projectKey).orElseThrow(() -> projectNotFound(key)));
    }
    List<ProjectContext> found = usableWorkspaces(p.ownerUserId()).stream()
        .map(ws -> projectAccess.contextFor(ws, projectKey)).flatMap(Optional::stream).toList();
    if (found.size() == 1) {
      return resolved(toolContext, found.get(0));
    }
    if (found.isEmpty()) {
      throw projectNotFound(key);
    }
    throw ApiException.badRequest("PROJECT_AMBIGUOUS", "프로젝트 " + projectKey
        + " 가 여러 워크스페이스에 있습니다 — workspace 에 다음 가운데 하나를 넣어 다시 부르십시오: "
        + slugs(found.stream().map(ProjectContext::workspace).toList()));
  }

  /**
   * 이번 호출이 쓴 워크스페이스(와 프로젝트)를 접근 기록에 적는다 — 키 주인의 지금 권한으로 정해진 곳만. 못 정했으면(없음 · 여럿) 적지 않아
   * 그 줄은 워크스페이스 없이 키 주인에게만 보인다(FR-MCP-010 — 다른 워크스페이스 기록이 섞이지 않게).
   */
  private static WorkspaceContext resolved(ToolContext toolContext, WorkspaceContext ws) {
    McpRequestInfo info = McpCallContext.of(toolContext).request();
    if (info != null) {
      info.resolved(ws.workspaceId(), null);
    }
    return ws;
  }

  private static ProjectContext resolved(ToolContext toolContext, ProjectContext ctx) {
    McpRequestInfo info = McpCallContext.of(toolContext).request();
    if (info != null) {
      info.resolved(ctx.workspaceId(), ctx.project().getKey());
    }
    return ctx;
  }

  private static ApiException projectNotFound(String key) {
    return ApiException.notFound("PROJECT_NOT_FOUND", "프로젝트를 찾을 수 없습니다: " + key);
  }

  private static String slugs(List<WorkspaceContext> list) {
    return list.stream().map(ws -> ws.workspace().getSlug()).collect(Collectors.joining(", "));
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  // ── 판정 · 값 ─────────────────────────────────────────────────────

  public static ProjectContext require(ProjectContext ctx, Permission permission) {
    if (!ctx.has(permission)) {
      throw ApiException.forbidden("PERMISSION_REQUIRED", "권한 " + permission.code() + " 이(가) 필요합니다.");
    }
    return ctx;
  }

  public static WorkspaceContext require(WorkspaceContext ctx, Permission permission) {
    if (!ctx.has(permission)) {
      throw ApiException.forbidden("PERMISSION_REQUIRED", "권한 " + permission.code() + " 이(가) 필요합니다.");
    }
    return ctx;
  }

  /** 한 사람 = 키 주인(사람) */
  public static Actor actor(McpKeyPrincipal p) {
    return Actor.user(p.ownerUserId(), p.ownerName(), null);
  }

  /** 오류를 예외 대신 {@code {"error": {"status", "code", "message"}}} 로 — REST 와 같은 code */
  public static Map<String, Object> run(Supplier<Map<String, Object>> body) {
    try {
      return body.get();
    } catch (ApiException e) {
      Map<String, Object> error = new LinkedHashMap<>();
      error.put("status", e.status().value());
      error.put("code", e.code());
      error.put("message", e.getMessage());
      return Map.of("error", error);
    }
  }
}
