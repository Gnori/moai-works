package com.moai.works.api.mcp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.moai.works.api.project.ProjectService;
import com.moai.works.api.project.ProjectViews;
import com.moai.works.api.security.access.WorkspaceContext;

/**
 * 워크스페이스 · 프로젝트 목록 MCP 도구 — 일감 입구({@code /mcp/issue})와 프로젝트 관리 입구({@code /mcp/project}) 모두에 있다
 * (Agent 가 워크스페이스 주소 · 프로젝트 key 를 알아야 하므로).
 *
 * <p>키는 사용자의 것이다(ADR-029) — 키로 쓸 수 있는 워크스페이스 모두에 걸쳐 본다. 어느 워크스페이스를 쓸 수 있는지는
 * {@link McpScope} 한 곳이 정하고(멤버 ∧ 지금 역할에 {@code mcp.key.issue.personal}), 목록은 REST 와 같은 {@link ProjectService} ·
 * 같은 판정을 부른다. 호출마다 키 주인의 지금 권한으로 다시 판정한다.
 */
@Component
public class ProjectMcpTools {

  private final McpScope scope;
  private final ProjectService projects;

  public ProjectMcpTools(McpScope scope, ProjectService projects) {
    this.scope = scope;
    this.projects = projects;
  }

  @Tool(name = "list_workspaces", description = "이 MCP 키로 일할 수 있는 워크스페이스 목록 — 키 주인이 멤버이고 역할에 MCP 키로 일하기 권한"
      + "(mcp.key.issue.personal)이 있는 곳. 워크스페이스마다 slug(주소) · 이름 · 종류(PERSONAL · TEAM) · 키 주인의 역할 이름. "
      + "다른 도구의 workspace 인자에는 이 slug 를 넣는다. 인자는 없다.")
  public Map<String, Object> listWorkspaces(ToolContext toolContext) {
    return McpScope.run(() -> {
      List<Map<String, Object>> items = scope.workspaces(toolContext).stream().map(ws -> {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("slug", ws.workspace().getSlug());
        m.put("name", ws.workspace().getName());
        m.put("kind", ws.workspace().getKind().name());
        m.put("roles", scope.roleNames(ws));
        return m;
      }).toList();
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("workspaces", items);
      result.put("count", items.size());
      return result;
    });
  }

  @Tool(name = "list_projects", description = "이 MCP 키로 작업할 수 있는 프로젝트 목록 — 키로 일할 수 있는 모든 워크스페이스(list_workspaces)에서 "
      + "키 주인이 읽을 수 있는 열린(보관하지 않은) 프로젝트. 프로젝트마다 key · 이름 · 설명 · 공개 범위 · 상태 · 마지막으로 바뀐 때 · "
      + "workspace(slug · name). 같은 key 가 여러 워크스페이스에 있으면 다른 도구에 workspace 를 함께 넣는다.")
  public Map<String, Object> listProjects(
      @ToolParam(required = false, description = "워크스페이스 주소(slug) — 주면 그 워크스페이스의 프로젝트만") String workspace,
      ToolContext toolContext) {
    return McpScope.run(() -> {
      List<WorkspaceContext> spaces = workspace == null || workspace.isBlank() ? scope.workspaces(toolContext)
          : List.of(scope.workspace(toolContext, workspace));
      List<Map<String, Object>> items = new ArrayList<>();
      for (WorkspaceContext ws : spaces) {
        Map<String, Object> where = new LinkedHashMap<>();
        where.put("slug", ws.workspace().getSlug());
        where.put("name", ws.workspace().getName());
        projects.list(ws, false).forEach(p -> items.add(project(p, where)));
      }
      Map<String, Object> result = new LinkedHashMap<>();
      result.put("projects", items);
      result.put("count", items.size());
      return result;
    });
  }

  private static Map<String, Object> project(ProjectViews.Summary p, Map<String, Object> workspace) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("key", p.key());
    m.put("name", p.name());
    m.put("description", p.description());
    m.put("visibility", p.visibility().name());
    m.put("status", p.status().name());
    m.put("updatedAt", McpTime.format(p.updatedAt()));
    m.put("workspace", workspace);
    return m;
  }
}
