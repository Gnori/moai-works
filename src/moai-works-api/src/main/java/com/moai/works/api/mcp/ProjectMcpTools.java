package com.moai.works.api.mcp;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import com.moai.works.api.mcpkey.McpKeyPrincipal;
import com.moai.works.api.project.ProjectService;
import com.moai.works.api.project.ProjectViews;
import com.moai.works.api.security.access.WorkspaceContext;
import com.moai.works.api.workspace.Workspace;
import com.moai.works.api.workspace.WorkspaceAccessService;
import com.moai.works.api.workspace.WorkspaceRepositories;

/**
 * 프로젝트 MCP 도구 — 일감 입구({@code /mcp/issue})와 프로젝트 관리 입구({@code /mcp/project}) 모두에 있다(Agent 가 프로젝트 key 를 알아야 하므로).
 *
 * <p>범위는 모델이 넘긴 인자가 아니라 키 문맥({@link McpCallContext} — 키의 워크스페이스 · 주인)으로 정한다. 도구는 REST 와
 * 같은 서비스 · 같은 판정을 부르고, 호출마다 키 주인의 지금 권한으로 다시 판정한다.
 *
 * <p>지금 있는 도구: {@code list_projects}(2026-11-22 — 골격에서 실제 목록으로). 나머지 읽기 · 쓰기 도구는 MCP 단계에서
 * 더한다(design/06 §8).
 */
@Component
public class ProjectMcpTools {

  private final WorkspaceRepositories.Workspaces workspaces;
  private final WorkspaceAccessService workspaceAccess;
  private final ProjectService projects;

  public ProjectMcpTools(WorkspaceRepositories.Workspaces workspaces, WorkspaceAccessService workspaceAccess,
      ProjectService projects) {
    this.workspaces = workspaces;
    this.workspaceAccess = workspaceAccess;
    this.projects = projects;
  }

  @Tool(name = "list_projects", description = "이 MCP 키로 작업할 수 있는 프로젝트 목록을 돌려준다 — 키의 워크스페이스에서 "
      + "키 주인이 읽을 수 있는 열린(보관하지 않은) 프로젝트. 프로젝트마다 key · 이름 · 설명 · 공개 범위 · 상태 · 마지막으로 바뀐 때. 인자는 없다.")
  public Map<String, Object> listProjects(ToolContext toolContext) {
    WorkspaceContext ws = workspaceOf(McpCallContext.of(toolContext).principal());
    List<Map<String, Object>> items = projects.list(ws, false).stream().map(ProjectMcpTools::project).toList();
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("workspace", Map.of("slug", ws.workspace().getSlug(), "name", ws.workspace().getName()));
    result.put("projects", items);
    result.put("count", items.size());
    return result;
  }

  /** 키의 워크스페이스 · 주인으로 지금 권한의 워크스페이스 문맥을 만든다(키 필터가 이미 검사했지만 호출마다 다시 본다) */
  private WorkspaceContext workspaceOf(McpKeyPrincipal principal) {
    if (principal == null) {
      throw new IllegalStateException("MCP 키 문맥이 없습니다.");
    }
    Workspace ws = workspaces.findById(principal.workspaceId()).filter(Workspace::isActive)
        .orElseThrow(() -> new IllegalStateException("쓸 수 없는 워크스페이스입니다."));
    return workspaceAccess.contextFor(ws, principal.ownerUserId())
        .orElseThrow(() -> new IllegalStateException("키 주인이 이 워크스페이스의 멤버가 아닙니다."));
  }

  private static Map<String, Object> project(ProjectViews.Summary p) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("key", p.key());
    m.put("name", p.name());
    m.put("description", p.description());
    m.put("visibility", p.visibility().name());
    m.put("status", p.status().name());
    m.put("updatedAt", p.updatedAt() == null ? null : p.updatedAt().toString());
    return m;
  }
}
