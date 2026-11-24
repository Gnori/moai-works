package com.moai.works.api.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import com.moai.works.api.skill.SkillService;
import com.moai.works.api.skill.SkillViews;

/** MCP 연결 안내(일감 #183 · 핵심 규칙 #182) — 길이 상한 · 줄이기 · 한 줄로 펴기 · DB 를 못 읽을 때 — DB 없이 */
class McpInstructionsTest {

  private static SkillViews.Summary skill(String name, int revision, String summary) {
    SkillViews.Change change = new SkillViews.Change(revision, Instant.now(), "관리자", "UPDATE", null, summary);
    return new SkillViews.Summary(name, "설명", revision, Instant.now(), null, false, null, null, change);
  }

  private static McpInstructions with(List<SkillViews.Summary> list) {
    SkillService service = mock(SkillService.class);
    when(service.list(false, false)).thenReturn(list);
    return new McpInstructions(service);
  }

  @Test
  void 스킬마다_한_줄이고_요약이_없는_옛_리비전은_번호만() {
    String text = with(List.of(skill("moai-works-issues", 4, "보류 · 취소 상태를 더함"), skill("old-skill", 2, null))).issue();
    assertThat(text).startsWith(McpInstructions.ISSUE_HEAD)
        .contains("\n- moai-works-issues 리비전 4: 보류 · 취소 상태를 더함\n- old-skill 리비전 2\n")
        .contains(McpInstructions.SUMMARY_GUARD).endsWith(McpInstructions.ISSUE_HOW);
  }

  @Test
  void 일감_입구는_핵심_규칙을_싣고_프로젝트_관리_입구는_싣지_않는다() {
    McpInstructions instructions = with(List.of(skill("moai-works-issues", 6, "길잡이")));
    String issue = instructions.issue();
    assertThat(issue).startsWith(McpInstructions.ISSUE_HEAD + "\n\n" + McpInstructions.ISSUE_RULES + "\n\n" + McpInstructions.SKILLS_HEAD)
        .contains("\n- moai-works-issues 리비전 6: 길잡이\n").endsWith(McpInstructions.ISSUE_HOW);
    // 상태마다 넣을 것 · 까닭 · 구간마다 작업 시간 · 끝내기 · 종료는 사람
    assertThat(McpInstructions.ISSUE_RULES).contains("dueDate", "agentSession", "프로젝트 key 아님", "startDate · plannedEndDate",
        "log_work", "statusChangedAt", "reason(까닭) 필수", "delete_issue", "setSection", "checkItems", "사람이 확인한 뒤", "checks", "next");
    assertThat(McpInstructions.ISSUE_RULES.lines().count()).isLessThanOrEqualTo(8);
    assertThat(issue.length()).isLessThanOrEqualTo(McpInstructions.LIMIT);
    assertThat(instructions.project()).doesNotContain(McpInstructions.ISSUE_RULES).doesNotContain("핵심 규칙");
  }

  @Test
  void 스킬이_많거나_요약이_길어도_2000자를_넘지_않고_나머지는_수만_알린다() {
    List<SkillViews.Summary> many = new ArrayList<>();
    for (int i = 0; i < 40; i++) {
      many.add(skill("skill-" + i, i + 1, "아주 긴 요약 ".repeat(60)));
    }
    for (String text : List.of(with(many).issue(), with(many).project())) {
      assertThat(text.length()).isLessThanOrEqualTo(McpInstructions.LIMIT);
      assertThat(text).contains("\n- skill-0 리비전 1: ").containsPattern("\n- 외 \\d+개 — list_skills 로 본다\n");
      // 요약은 스킬마다 120자로 자른다
      String first = text.lines().filter(l -> l.startsWith("- skill-0 ")).findFirst().orElseThrow();
      assertThat(first).endsWith("…").hasSizeLessThanOrEqualTo("- skill-0 리비전 1: ".length() + McpInstructions.SUMMARY_MAX);
    }
  }

  @Test
  void 요약은_한_줄로_펴고_제어_문자와_보이지_않는_서식_문자를_걷는다() {
    assertThat(McpInstructions.oneLine("첫 줄\n\n둘째\t줄‮ 숨김​끝 ", 120)).isEqualTo("첫 줄 둘째 줄 숨김 끝");
    assertThat(McpInstructions.oneLine("가나다라마바", 4)).isEqualTo("가나다…");
    assertThat(McpInstructions.oneLine(null, 10)).isNull();
  }

  @Test
  void 스킬이_없거나_DB_를_못_읽어도_연결을_막지_않는다() {
    assertThat(with(List.of()).issue()).contains(McpInstructions.NO_SKILLS).doesNotContain("리비전");
    SkillService broken = mock(SkillService.class);
    when(broken.list(false, false)).thenThrow(new DataAccessResourceFailureException("DB 없음"));
    String text = new McpInstructions(broken).project();
    assertThat(text).startsWith(McpInstructions.PROJECT_HEAD).contains(McpInstructions.UNAVAILABLE)
        .contains(McpInstructions.PROJECT_HOW);
  }
}
