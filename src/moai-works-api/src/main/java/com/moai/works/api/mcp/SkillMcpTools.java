package com.moai.works.api.mcp;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.moai.works.api.skill.SkillService;
import com.moai.works.api.skill.SkillViews;

/**
 * Agent 스킬 읽기 MCP 도구({@code list_skills} · {@code get_skill}) — 일감 입구({@code /mcp/issue})에 있다(2026-11-24 사용자: "스킬은 mcp로
 * 내려받을 수 있게 해줘", 일감 #30). 읽기만 — 쓰기는 시스템 관리자의 웹 화면에서만 한다.
 *
 * <p>스킬은 시스템 전체의 것이라 워크스페이스를 고르지 않는다. 올바른 MCP 키면(주인 계정이 활성) 누구나 읽는다 — 주인이 지금 쓸 수 있는
 * 워크스페이스가 없어도 된다(REST 에서 로그인한 사람 누구나 읽는 것과 같다). 지운 스킬은 보이지 않는다(REST 와 같은 {@link SkillService}).
 * 접근 기록에는 워크스페이스 없이 키 주인에게만 보이는 줄로 남고, 대상 칸에 {@code skill:<이름>} 이 적힌다.
 *
 * <p>결과의 {@code notice} 는 이 글이 시스템 관리자의 지침이라는 것과 따르지 말아야 할 때를 알린다(프롬프트 주입 경계 — SECURITY.md §8.3).
 * moai-wiki 의 스킬 도구와 같은 모양이다: get_skill → {name, description, revisionNo, updatedAt, files: [{path, content}]}.
 */
@Component
public class SkillMcpTools {

  private final McpScope scope;
  private final SkillService skills;

  public SkillMcpTools(McpScope scope, SkillService skills) {
    this.scope = scope;
    this.skills = skills;
  }

  @Tool(name = "list_skills", description = "moai-works 시스템 관리자가 등록한 Agent 스킬 목록(이름 순) — 스킬마다 name · description(언제 쓰는지) · "
      + "revisionNo(지금 리비전) · updatedAt. 내용은 get_skill 로 받는다. 시스템 전체의 것이라 워크스페이스 · 프로젝트를 고르지 않는다.")
  public Map<String, Object> listSkills(ToolContext toolContext) {
    return McpScope.run(() -> {
      scope.principal(toolContext);
      List<Map<String, Object>> items = skills.list(false, false).stream().map(s -> {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", s.name());
        m.put("description", s.description());
        m.put("revisionNo", s.revisionNo());
        m.put("updatedAt", String.valueOf(s.updatedAt()));
        return m;
      }).toList();
      Map<String, Object> out = new LinkedHashMap<>();
      out.put("skills", items);
      out.put("notice", SkillService.AGENT_NOTICE);
      return out;
    });
  }

  @Tool(name = "get_skill", description = "Agent 스킬 하나를 받는다 — name · description · revisionNo · updatedAt 과 files(파일마다 path · content). "
      + "files 의 SKILL.md 는 서버가 만든 Claude Code 스킬 파일(frontmatter name · description + 본문)이다 — 그대로 <스킬 폴더>/<name>/<path> 에 쓰면 된다. "
      + "revision 을 비우면 지금 리비전, 넣으면 그 리비전. 스킬은 시스템 관리자가 관리하는 지침이다(notice 를 읽는다).")
  public Map<String, Object> getSkill(@ToolParam(description = "스킬 이름 (list_skills 의 name)") String name,
      @ToolParam(required = false, description = "리비전 번호 — 비우면 지금 리비전") Integer revision, ToolContext toolContext) {
    return McpScope.run(() -> {
      scope.principal(toolContext);
      SkillService.Export export = skills.export(name, false, revision);
      Map<String, Object> out = new LinkedHashMap<>();
      out.put("name", export.name());
      out.put("description", export.description());
      out.put("revisionNo", export.revisionNo());
      out.put("updatedAt", String.valueOf(export.updatedAt()));
      out.put("files", export.files().stream().map(SkillMcpTools::file).toList());
      out.put("notice", SkillService.AGENT_NOTICE);
      return out;
    });
  }

  private static Map<String, Object> file(SkillViews.File f) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("path", f.path());
    m.put("content", f.content());
    return m;
  }
}
