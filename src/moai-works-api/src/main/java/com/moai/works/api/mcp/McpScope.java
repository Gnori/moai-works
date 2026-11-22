package com.moai.works.api.mcp;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.stereotype.Component;

import com.moai.works.api.common.Actor;
import com.moai.works.api.common.ApiException;
import com.moai.works.api.mcpkey.McpKeyPrincipal;
import com.moai.works.api.project.ProjectAccessService;
import com.moai.works.api.security.access.ProjectContext;
import com.moai.works.api.security.access.WorkspaceContext;
import com.moai.works.api.workspace.Permission;
import com.moai.works.api.workspace.Workspace;
import com.moai.works.api.workspace.WorkspaceAccessService;
import com.moai.works.api.workspace.WorkspaceRepositories;

/**
 * MCP 도구의 범위 — 모델이 넘긴 인자가 아니라 키 문맥(키의 워크스페이스 · 주인)에서 정하고, 호출마다 키 주인의 지금 권한을 다시 본다.
 * 모든 MCP 도구가 이 한 곳을 쓴다(REST 의 접근 어노테이션 자리).
 */
@Component
public class McpScope {

  private final WorkspaceRepositories.Workspaces workspaces;
  private final WorkspaceAccessService workspaceAccess;
  private final ProjectAccessService projectAccess;

  public McpScope(WorkspaceRepositories.Workspaces workspaces, WorkspaceAccessService workspaceAccess,
      ProjectAccessService projectAccess) {
    this.workspaces = workspaces;
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

  /** 키의 워크스페이스에서 키 주인의 지금 문맥 */
  public WorkspaceContext workspace(ToolContext toolContext) {
    McpKeyPrincipal p = principal(toolContext);
    Workspace ws = workspaces.findById(p.workspaceId()).filter(Workspace::isActive)
        .orElseThrow(() -> ApiException.forbidden("MCP_WORKSPACE_INACTIVE", "쓸 수 없는 워크스페이스입니다."));
    return workspaceAccess.contextFor(ws, p.ownerUserId())
        .orElseThrow(() -> ApiException.forbidden("MCP_PRINCIPAL_INACTIVE", "키 주인이 이 워크스페이스의 멤버가 아닙니다."));
  }

  /** 키 주인의 지금 권한으로 그 프로젝트의 문맥 — 독자가 아니면 PROJECT_NOT_FOUND (REST 와 같다) */
  public ProjectContext project(ToolContext toolContext, String key) {
    return projectAccess.contextFor(workspace(toolContext), key == null ? "" : key.trim())
        .orElseThrow(() -> ApiException.notFound("PROJECT_NOT_FOUND", "프로젝트를 찾을 수 없습니다: " + key));
  }

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
