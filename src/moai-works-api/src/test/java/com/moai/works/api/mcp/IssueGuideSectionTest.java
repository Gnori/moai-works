package com.moai.works.api.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.moai.works.api.issue.DescriptionEdit;
import com.moai.works.api.issue.DescriptionEdit.Mode;

/**
 * update_issue 의 setSection(replace)이 크게 지웠을 때의 checks — {@code SECTION_REPLACED_LARGE}(DB 없이). 소절 제목 · 코드 블록이 하나라도,
 * 또는 새 글에 없는 옛 글이 500자를 넘으면 WARN, 메시지는 수만. 2026-11-25, 일감 #237.
 */
class IssueGuideSectionTest {

  private static DescriptionEdit.Report edit(String description, String heading, String content, Mode mode) {
    return new DescriptionEdit(new DescriptionEdit.Section(heading, content, mode), null, null).apply(description).report();
  }

  private static List<Map<String, Object>> replace(String description, String heading, String content) {
    return IssueGuide.sectionChecks(edit(description, heading, content, Mode.REPLACE));
  }

  private static String message(List<Map<String, Object>> checks) {
    assertThat(checks).singleElement().satisfies(c -> assertThat(c).containsEntry("code", "SECTION_REPLACED_LARGE")
        .containsEntry("level", "WARN").doesNotContainKey("items"));
    return (String) checks.get(0).get("message");
  }

  @Test
  void 소절_코드_블록을_하나라도_지우거나_500자를_넘게_지우면_WARN_이고_메시지는_수만_알린다() {
    String doc = "## 테스트 결과 (선택)\n\n### 비밀소절\n```chart\n{}\n```\n```mermaid\nflowchart LR\n```\n```sql\nselect 1\n```\n";
    // 줄 10: ### 비밀소절(8) · ```chart(8) · {}(2) · ```(3) · ```mermaid(10) · flowchart LR(12) · ```(3) · ```sql(6) · select 1(8) · ```(3)
    assertThat(message(replace(doc, "테스트 결과", "통과")))
        .contains("소절(### 제목) 1개", "코드 블록 3개(chart 1 · mermaid 1 · 그 밖 1)", "글 10줄(63자)", "descriptionEdit.section.previous",
            "truncated", "리비전 탭", "revisionNo", "mode=append")
        // 사용자 글(절 제목 · 옛 본문 · 새 글)은 메시지에 넣지 않는다
        .doesNotContain("비밀소절", "테스트 결과", "flowchart", "select", "통과");

    // 소절 하나만 · 코드 블록 하나만이어도
    assertThat(message(replace("## 가\n### 나\n", "가", "x"))).contains("소절(### 제목) 1개", "글 1줄(5자)").doesNotContain("코드 블록 ");
    assertThat(message(replace("## 가\n```\n1\n```\n", "가", "x"))).contains("코드 블록 1개(그 밖 1)").doesNotContain("소절(");

    // 글만이면 500자를 넘을 때
    assertThat(message(replace("## 가\n" + "다".repeat(501) + "\n", "가", "x"))).contains("글 1줄(501자)");
    assertThat(replace("## 가\n" + "다".repeat(500) + "\n", "가", "x")).isEmpty();
    assertThat(replace("## 가\n" + "다".repeat(300) + "\n\n" + "라".repeat(201) + "\n", "가", "x")).hasSize(1);
  }

  @Test
  void 옮겨_적은_replace_append_prepend_새_절_빈_템플릿_절에는_알리지_않는다() {
    String doc = "## 가\n### 나\n```chart\n{}\n```\n" + "라".repeat(800) + "\n\n## 끝\n";
    String body = DescriptionEdit.sectionBody(doc, "가");
    assertThat(replace(doc, "가", body + "\n\n더한 결과")).isEmpty();
    assertThat(IssueGuide.sectionChecks(edit(doc, "가", "더한 결과", Mode.APPEND))).isEmpty();
    assertThat(IssueGuide.sectionChecks(edit(doc, "가", "더한 결과", Mode.PREPEND))).isEmpty();
    // 절이 없어 새로 붙임
    assertThat(replace("## 다른\n" + "x".repeat(900), "가", "새")).isEmpty();
    // 템플릿 그대로 빈 절 · 안내 글뿐인 절(버그 템플릿)
    assertThat(replace("## 목적\n본다\n\n## 테스트 결과 (선택)\n", "테스트 결과", "통과 12건")).isEmpty();
    assertThat(replace("## 처리 결과 (선택)\n완료할 때 채운다 — 원인 · 고친 것 · 커밋 · 확인한 것(화면 캡처) · 남은 것\n", "처리 결과", "- 고침"))
        .isEmpty();
    // 절 고치기가 없거나 체크만
    assertThat(IssueGuide.sectionChecks(null)).isEmpty();
    assertThat(IssueGuide.sectionChecks(new DescriptionEdit(null, List.of("all"), null).apply("- [ ] a").report())).isEmpty();
  }
}
