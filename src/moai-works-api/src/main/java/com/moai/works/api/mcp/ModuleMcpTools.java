package com.moai.works.api.mcp;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.moai.works.api.project.ProjectModuleService;
import com.moai.works.api.security.access.ProjectContext;
import com.moai.works.api.workspace.Permission;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * 프로젝트 모듈 보기 MCP 도구({@code list_modules}) — 일감 입구({@code /mcp/issue})와 프로젝트 관리 입구({@code /mcp/project}) 모두에 있다
 * (Agent 가 일감을 만들 때 관련 모듈 key 를 알아야 하고, 관리할 때도 지금 목록을 본다 — 2026-11-23, 일감 #27).
 *
 * <p>REST {@code GET …/modules} 와 같은 {@link ProjectModuleService} · 같은 판정({@code issue.read})이다. 범위는 {@link McpScope}.
 */
@Component
public class ModuleMcpTools {

  private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
  };

  private final McpScope scope;
  private final ProjectModuleService modules;
  private final JsonMapper json;

  public ModuleMcpTools(McpScope scope, ProjectModuleService modules, JsonMapper json) {
    this.scope = scope;
    this.modules = modules;
    this.json = McpTime.mapper(json); // 결과의 시각은 한국 시간 ISO(일감 #204)
  }

  @Tool(name = "list_modules", description = "프로젝트의 모듈(순서대로) — 일감의 관련 모듈로 고르는 것. key · name · description · position · "
      + "active(false 면 새로 붙일 수 없다) · issueCount. create_issue · update_issue 의 modules 와 search_issues 의 module 에 key 를 넣는다. "
      + "canManage 는 키 주인이 모듈을 관리할 수 있는지.")
  public Map<String, Object> listModules(@ToolParam(description = "프로젝트 key") String project,
      @ToolParam(required = false, description = McpScope.WORKSPACE_PARAM) String workspace, ToolContext toolContext) {
    return McpScope.run(() -> {
      ProjectContext ctx = McpScope.require(scope.project(toolContext, workspace, project), Permission.ISSUE_READ);
      Map<String, Object> out = new LinkedHashMap<>();
      out.put("project", ctx.project().getKey());
      out.put("workspace", ctx.workspace().workspace().getSlug());
      out.put("canManage", ctx.has(Permission.PROJECT_MANAGE) && ctx.project().isOpenToReaders());
      out.put("modules", modules.list(ctx).stream().map(m -> json.convertValue(m, MAP)).toList());
      return out;
    });
  }
}
