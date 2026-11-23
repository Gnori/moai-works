package com.moai.works.api.mcp;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.moai.works.api.mcpkey.McpKeyPrincipal;
import com.moai.works.api.project.ProjectModuleService;
import com.moai.works.api.security.access.ProjectContext;
import com.moai.works.api.workspace.Permission;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * 프로젝트 모듈 관리 MCP 도구({@code set_module}) — 프로젝트 관리 입구({@code /mcp/project})에만 있다(2026-11-23, 일감 #27 —
 * 관리 도구는 관리 입구에, ADR-028). 일감 입구에는 보기({@code list_modules})만 있다.
 *
 * <p>REST 와 같은 {@link ProjectModuleService} 를 부르고 권한도 같다({@code project.manage} — 서비스가 다시 본다). 지우기 도구는 없다
 * (쓰지 않음 active=false 로 둔다). 한 사람은 키 주인, 감사의 경로는 {@code MCP}.
 */
@Component
public class ModuleManageMcpTools {

  private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
  };

  private final McpScope scope;
  private final ProjectModuleService modules;
  private final JsonMapper json;

  public ModuleManageMcpTools(McpScope scope, ProjectModuleService modules, JsonMapper json) {
    this.scope = scope;
    this.modules = modules;
    this.json = json;
  }

  @Tool(name = "set_module", description = "프로젝트 모듈을 더하거나 고친다(project.manage 권한). key 가 없으면 새로 더하고(name 필수, 목록 끝), 있으면 준 것만 "
      + "바꾼다 — name(이름) · description(설명, 빈 글이면 지움) · active(false 면 쓰지 않음 — 새 일감에 고를 수 없고 이미 붙은 일감에는 남는다, "
      + "true 면 다시 씀) · position(목록 자리, 0 부터). 지우기는 없다. key 는 영문 소문자로 시작하는 1~32자(소문자 · 숫자 · 하이픈)이고 "
      + "만든 뒤에는 바꿀 수 없다. 결과: module · created(새로 더했는지). 감사에 남는다. 사용자가 모듈을 바꾸라고 했을 때만 부른다.")
  public Map<String, Object> setModule(@ToolParam(description = "프로젝트 key") String project,
      @ToolParam(required = false, description = McpScope.WORKSPACE_PARAM) String workspace,
      @ToolParam(description = "모듈 key (예: web-frontend)") String key,
      @ToolParam(required = false, description = "이름(100자까지) — 새로 더할 때는 꼭") String name,
      @ToolParam(required = false, description = "설명(500자까지). 빈 글이면 지운다") String description,
      @ToolParam(required = false, description = "false 면 쓰지 않음, true 면 다시 씀") Boolean active,
      @ToolParam(required = false, description = "목록에서의 자리(0 부터, 끝을 넘으면 끝)") Integer position,
      ToolContext toolContext) {
    return McpScope.run(() -> {
      McpKeyPrincipal p = scope.principal(toolContext);
      ProjectContext ctx = McpScope.require(scope.project(toolContext, workspace, project), Permission.PROJECT_MANAGE);
      ProjectModuleService.SetResult result = modules.set(ctx, key, name, description, active, position, McpScope.actor(p),
          "MCP");
      Map<String, Object> out = new LinkedHashMap<>();
      out.put("project", ctx.project().getKey());
      out.put("module", json.convertValue(result.module(), MAP));
      out.put("created", result.created());
      return out;
    });
  }
}
