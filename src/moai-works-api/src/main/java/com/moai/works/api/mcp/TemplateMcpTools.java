package com.moai.works.api.mcp;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.moai.works.api.issue.IssueTemplateService;
import com.moai.works.api.mcpkey.McpKeyPrincipal;
import com.moai.works.api.security.access.ProjectContext;
import com.moai.works.api.security.access.WorkspaceContext;
import com.moai.works.api.workspace.Permission;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * 일감 템플릿 관리 MCP 도구 — 프로젝트 관리 입구({@code /mcp/project})에만 있다(2026-11-23 사용자: "이슈의 유형별로 작성 템플릿 넣어 주고
 * 그걸 관리할 수 있게 해 줘. 또한 mcp 제공해서" · 결정 "관리 도구도"). 일감 입구에는 읽기({@code get_issue_template})만 있다.
 *
 * <p>REST 와 같은 {@link IssueTemplateService} 를 부르고, 권한도 같다 — 워크스페이스 기본은 {@code workspace.settings.manage},
 * 프로젝트 덮어쓰기는 {@code project.manage}. 한 사람은 키 주인, 감사의 경로는 {@code MCP}.
 * 어느 워크스페이스인지는 {@code workspace} 인자와 {@link McpScope} 가 정한다(키는 사용자의 것 — ADR-029).
 */
@Component
public class TemplateMcpTools {

  private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
  };

  private final McpScope scope;
  private final IssueTemplateService templates;
  private final JsonMapper json;

  public TemplateMcpTools(McpScope scope, IssueTemplateService templates, JsonMapper json) {
    this.scope = scope;
    this.templates = templates;
    this.json = McpTime.mapper(json); // 결과의 시각은 한국 시간 ISO(일감 #204)
  }

  @Tool(name = "list_issue_templates", description = "일감 유형별 작성 템플릿 목록. project 를 주면 그 프로젝트에서 실제로 쓰이는 것"
      + "(source: PROJECT 프로젝트가 덮어씀 · WORKSPACE 워크스페이스 기본 · BUILTIN 처음 템플릿, inherited = 위에서 물려받음), "
      + "비우면 워크스페이스 기본. 템플릿의 ## 제목이 항목이고 끝이 (선택) 이면 비워도 되는 항목이다.")
  public Map<String, Object> listIssueTemplates(
      @ToolParam(required = false, description = "프로젝트 key — 비우면 워크스페이스 기본") String project,
      @ToolParam(required = false, description = McpScope.WORKSPACE_PARAM + ". 프로젝트 없이 부를 때 워크스페이스가 여럿이면 꼭 넣는다(WORKSPACE_REQUIRED)") String workspace,
      ToolContext toolContext) {
    return McpScope.run(() -> {
      Map<String, Object> out = new LinkedHashMap<>();
      if (project == null || project.isBlank()) {
        WorkspaceContext ws = McpScope.require(scope.workspace(toolContext, workspace), Permission.WORKSPACE_READ);
        out.put("scope", "WORKSPACE");
        out.put("workspace", ws.workspace().getSlug());
        out.put("canManage", ws.has(Permission.WORKSPACE_SETTINGS_MANAGE));
        out.put("templates", templates.forWorkspace(ws).stream().map(this::map).toList());
      } else {
        ProjectContext ctx = McpScope.require(scope.project(toolContext, workspace, project), Permission.ISSUE_READ);
        out.put("scope", "PROJECT");
        out.put("workspace", ctx.workspace().workspace().getSlug());
        out.put("project", ctx.project().getKey());
        out.put("canManage", ctx.has(Permission.PROJECT_MANAGE));
        out.put("templates", templates.forProject(ctx).stream().map(this::map).toList());
      }
      return out;
    });
  }

  @Tool(name = "set_issue_template", description = "일감 유형의 작성 템플릿을 정한다. project 를 주면 그 프로젝트만 덮어쓰고(project.manage), "
      + "비우면 워크스페이스 기본을 정한다(workspace.settings.manage). body 는 Markdown — ## 제목이 항목, 끝에 (선택) 을 붙이면 비워도 되는 항목. "
      + "이미 정한 것을 고칠 때는 list_issue_templates 의 rowVersion 을 넣는다(그 사이 바뀌었으면 CONCURRENT_UPDATE). 감사에 남는다.")
  public Map<String, Object> setIssueTemplate(
      @ToolParam(description = "유형 key — feature · bug · test · research") String type,
      @ToolParam(description = "템플릿 Markdown(30000자까지)") String body,
      @ToolParam(required = false, description = "프로젝트 key — 비우면 워크스페이스 기본") String project,
      @ToolParam(required = false, description = McpScope.WORKSPACE_PARAM + ". 프로젝트 없이 부를 때 워크스페이스가 여럿이면 꼭 넣는다(WORKSPACE_REQUIRED)") String workspace,
      @ToolParam(required = false, description = "고칠 때 읽은 rowVersion") Integer rowVersion, ToolContext toolContext) {
    return McpScope.run(() -> {
      McpKeyPrincipal p = scope.principal(toolContext);
      if (project == null || project.isBlank()) {
        return map(templates.setWorkspace(scope.workspace(toolContext, workspace), type, body, rowVersion, McpScope.actor(p), "MCP"));
      }
      return map(templates.setProject(scope.project(toolContext, workspace, project), type, body, rowVersion, McpScope.actor(p), "MCP"));
    });
  }

  @Tool(name = "reset_issue_template", description = "정한 템플릿을 지우고 한 단계 위로 되돌린다 — project 를 주면 프로젝트 덮어쓰기를 지워 "
      + "워크스페이스 기본(없으면 처음 템플릿)으로(project.manage), 비우면 워크스페이스 기본을 지워 처음 템플릿으로(workspace.settings.manage). "
      + "결과는 되돌린 뒤 쓰이는 템플릿. 감사에 남는다.")
  public Map<String, Object> resetIssueTemplate(
      @ToolParam(description = "유형 key") String type,
      @ToolParam(required = false, description = "프로젝트 key — 비우면 워크스페이스 기본") String project,
      @ToolParam(required = false, description = McpScope.WORKSPACE_PARAM + ". 프로젝트 없이 부를 때 워크스페이스가 여럿이면 꼭 넣는다(WORKSPACE_REQUIRED)") String workspace,
      ToolContext toolContext) {
    return McpScope.run(() -> {
      McpKeyPrincipal p = scope.principal(toolContext);
      if (project == null || project.isBlank()) {
        return map(templates.resetWorkspace(scope.workspace(toolContext, workspace), type, McpScope.actor(p), "MCP"));
      }
      return map(templates.resetProject(scope.project(toolContext, workspace, project), type, McpScope.actor(p), "MCP"));
    });
  }

  private Map<String, Object> map(Object view) {
    return json.convertValue(view, MAP);
  }
}
