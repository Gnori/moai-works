package com.moai.works.api.mcp;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import com.moai.works.api.mcpkey.McpKeyPrincipal;

/**
 * 프로젝트 관리용 MCP 도구 ({@code /mcp/project}) — 바깥 AI Agent 가 쓴다.
 *
 * <p>골격: {@code list_projects} 자리만 있다(프로젝트 데이터 없음). design/07 §4 의 도구(일감 찾기 · 읽기 · 만들기 ·
 * 고치기 · 상태 바꾸기 · 메모 · 시간 기록 …)를 여기에 더한다. 도구는 REST 와 같은 서비스와 같은 권한 판정을 쓴다.
 */
@Component
public class ProjectMcpTools {

  @Tool(name = "list_projects", description = "호출자가 볼 수 있는 프로젝트 목록을 돌려준다. 인자는 없다.")
  public Map<String, Object> listProjects(ToolContext toolContext) {
    McpCallContext call = McpCallContext.of(toolContext);
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("projects", List.of());
    McpKeyPrincipal p = call.principal();
    result.put("caller", p == null ? null : p.ownerName());
    result.put("note", "골격 — 프로젝트 데이터가 아직 없다");
    return result;
  }
}
