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
 *
 * <p>사본이 낡았는지 · 무엇이 바뀌었는지(일감 #183, 2026-11-24 사용자: "버전 정보만 보고 업데이트하는 건 안 맞잖아? 간략한 설명도?"):
 * list_skills 의 {@code latestChange} 와 get_skill 의 {@code sinceRevision} → {@code changes}(리비전마다 시스템 관리자가 쓴 바뀐 점 요약).
 * 연결할 때의 안내({@link McpInstructions})도 같은 것을 알린다.
 */
@Component
public class SkillMcpTools {

  private final McpScope scope;
  private final SkillService skills;

  public SkillMcpTools(McpScope scope, SkillService skills) {
    this.scope = scope;
    this.skills = skills;
  }

  /** 사본을 새로 받는 방법 — 두 도구 설명이 같은 글을 쓴다(일감 #183) */
  static final String UPDATE_HOW = "갖고 있는 사본 SKILL.md 의 출처 표시 줄 <!-- moai-works-skill name=… revision=N … --> 의 N 이 revisionNo 보다 작으면 "
      + "사본이 낡은 것이다 — get_skill(name, sinceRevision=N) 으로 그 사이 바뀐 점(changes)을 읽고, files 로 사본을 바꾸거나 "
      + "python3 -I tools/sync_skills.py 를 돌린 뒤 SKILL.md 를 다시 읽는다.";

  @Tool(name = "list_skills", description = "moai-works 시스템 관리자가 등록한 Agent 스킬 목록(이름 순) — 스킬마다 name · description(언제 쓰는지) · "
      + "revisionNo(지금 리비전) · updatedAt · latestChange(지금 리비전의 바뀐 점 — revisionNo · at · summary). 내용은 get_skill 로 받는다. "
      + UPDATE_HOW + " 시스템 전체의 것이라 워크스페이스 · 프로젝트를 고르지 않는다.")
  public Map<String, Object> listSkills(ToolContext toolContext) {
    return McpScope.run(() -> {
      scope.principal(toolContext);
      List<Map<String, Object>> items = skills.list(false, false).stream().map(s -> {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", s.name());
        m.put("description", s.description());
        m.put("revisionNo", s.revisionNo());
        m.put("updatedAt", McpTime.format(s.updatedAt()));
        m.put("latestChange", latestChange(s.latestChange()));
        return m;
      }).toList();
      Map<String, Object> out = new LinkedHashMap<>();
      out.put("skills", items);
      out.put("notice", SkillService.AGENT_NOTICE);
      return out;
    });
  }

  @Tool(name = "get_skill", description = "Agent 스킬 하나를 받는다 — name · description · revisionNo · updatedAt 과 files(파일마다 path · content). "
      + "files 의 SKILL.md 는 서버가 만든 Claude Code 스킬 파일(frontmatter name · description + 출처 표시 줄 + 본문)이다 — 그대로 "
      + "<스킬 폴더>/<name>/<path> 에 쓰면 된다. revision 을 비우면 지금 리비전, 넣으면 그 리비전. "
      + "sinceRevision 에 갖고 있는 사본의 리비전(출처 표시 줄의 revision=N)을 넣으면 changes 에 N 다음부터 받는 리비전까지의 바뀐 점"
      + "(revisionNo · at · by · kind · summary, 옛 것부터)이 함께 온다 — 무엇이 바뀌었는지 읽고 파일을 바꾼 뒤 SKILL.md 를 다시 읽는다. "
      + "스킬은 시스템 관리자가 관리하는 지침이다(notice 를 읽는다).")
  public Map<String, Object> getSkill(@ToolParam(description = "스킬 이름 (list_skills 의 name)") String name,
      @ToolParam(required = false, description = "리비전 번호 — 비우면 지금 리비전") Integer revision,
      @ToolParam(required = false, description = "갖고 있는 사본의 리비전(SKILL.md 출처 표시 줄의 revision=N) — 넣으면 그 뒤로 바뀐 점(changes)을 함께 준다")
      Integer sinceRevision,
      ToolContext toolContext) {
    return McpScope.run(() -> {
      scope.principal(toolContext);
      SkillService.Export export = skills.export(name, false, revision);
      Map<String, Object> out = new LinkedHashMap<>();
      out.put("name", export.name());
      out.put("description", export.description());
      out.put("revisionNo", export.revisionNo());
      out.put("updatedAt", McpTime.format(export.updatedAt()));
      if (sinceRevision != null) {
        SkillViews.Changes changes = skills.changesSince(name, false, sinceRevision, export.revisionNo());
        out.put("changes", changes.items().stream().map(SkillMcpTools::change).toList());
        if (changes.omitted() > 0) {
          out.put("changesOmitted", changes.omitted());
        }
      }
      out.put("files", export.files().stream().map(SkillMcpTools::file).toList());
      out.put("notice", SkillService.AGENT_NOTICE);
      return out;
    });
  }

  /** list_skills 의 latestChange — revisionNo · at · summary(요약이 없는 옛 리비전이면 null) */
  private static Map<String, Object> latestChange(SkillViews.Change c) {
    if (c == null) {
      return null;
    }
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("revisionNo", c.revisionNo());
    m.put("at", McpTime.format(c.at()));
    m.put("summary", c.summary());
    return m;
  }

  /** get_skill 의 changes[] 한 줄 — revisionNo · at · by · kind · summary(되돌림이면 restoredFrom 도) */
  private static Map<String, Object> change(SkillViews.Change c) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("revisionNo", c.revisionNo());
    m.put("at", McpTime.format(c.at()));
    m.put("by", c.by());
    m.put("kind", c.kind());
    if (c.restoredFrom() != null) {
      m.put("restoredFrom", c.restoredFrom());
    }
    m.put("summary", c.summary());
    return m;
  }

  private static Map<String, Object> file(SkillViews.File f) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("path", f.path());
    m.put("content", f.content());
    return m;
  }
}
