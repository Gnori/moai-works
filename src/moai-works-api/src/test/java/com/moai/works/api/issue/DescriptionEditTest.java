package com.moai.works.api.issue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.moai.works.api.common.ApiException;
import com.moai.works.api.issue.DescriptionEdit.CodeBlocks;
import com.moai.works.api.issue.DescriptionEdit.Mode;
import com.moai.works.api.issue.DescriptionEdit.Previous;
import com.moai.works.api.issue.DescriptionEdit.Removed;

/** 설명 고치기 — 절 채우기 · 체크 목록 표시(DB 없이). 2026-11-24, 일감 #178 */
class DescriptionEditTest {

  private static final String DOC = """
      ## 배경

      글을 고친다.

      ## 수용 기준
      - [ ] API 가 오류를 JSON 으로 준다
      - [ ] API 테스트
      * [x] 문서
      1. [ ] 화면, 390px 확인

      ```markdown
      ## 처리 결과
      - [ ] 코드 블록 안의 체크
      ```

      ## 처리 결과 (선택)

      (아직)

      ### 세부
      옛 세부

      ## 남은 것
      없음
      """;

  private static DescriptionEdit section(String heading, String content) {
    return new DescriptionEdit(new DescriptionEdit.Section(heading, content), null, null);
  }

  @Test
  void 절이_있으면_다음_같은_수준_제목_앞까지_본문을_바꾸고_선택_꼬리표는_없어도_맞는다() {
    DescriptionEdit.Result r = section("처리 결과", "- 바뀐 것: 오류 모양\n- 커밋: abc").apply(DOC);
    assertThat(r.report().section()).containsEntry("action", "REPLACED").containsEntry("heading", "처리 결과");
    assertThat(r.description()).contains("## 처리 결과 (선택)\n\n- 바뀐 것: 오류 모양\n- 커밋: abc\n\n## 남은 것\n없음")
        .doesNotContain("(아직)").doesNotContain("옛 세부")
        // 코드 블록 안의 같은 제목은 건드리지 않는다
        .contains("```markdown\n## 처리 결과\n- [ ] 코드 블록 안의 체크\n```");
    // 앞의 ## 를 붙여 줘도, 대소문자가 달라도 같은 절
    assertThat(section("## 처리 결과 (선택)", "x").apply(DOC).report().section()).containsEntry("action", "REPLACED");
  }

  @Test
  void 절이_없으면_끝에_붙인다() {
    DescriptionEdit.Result r = section("테스트 결과", "통과 12건").apply("## 배경\n\n글\n\n\n");
    assertThat(r.description()).isEqualTo("## 배경\n\n글\n\n## 테스트 결과\n\n통과 12건");
    // 2026-11-25(일감 #237)부터 CREATED — 예전 APPENDED 는 mode=append 의 것
    assertThat(r.report().section()).containsEntry("action", "CREATED").containsEntry("mode", "replace");
    assertThat(section("결론", "끝").apply(null).description()).isEqualTo("## 결론\n\n끝");
    // 마지막 절을 바꾸기 — 뒤에 아무것도 없다
    assertThat(section("남은 것", "더 없음").apply(DOC).description()).endsWith("## 남은 것\n\n더 없음");
  }

  @Test
  void 체크는_글이_들어_있는_줄을_x_로_하고_똑같은_줄이_있으면_그_줄만() {
    DescriptionEdit.Result r = new DescriptionEdit(null, List.of(" API 테스트 ", "390px"), null).apply(DOC);
    assertThat(r.description()).contains("- [x] API 테스트\n").contains("- [ ] API 가 오류를 JSON 으로 준다")
        .contains("1. [x] 화면, 390px 확인").contains("- [ ] 코드 블록 안의 체크");
    assertThat(r.report().checked()).containsExactly("API 테스트", "화면, 390px 확인");
    // 들어 있는 줄이 여럿이면(똑같은 줄이 없으면) 모두
    assertThat(new DescriptionEdit(null, List.of("API"), null).apply(DOC).report().checked())
        .containsExactly("API 가 오류를 JSON 으로 준다", "API 테스트");
  }

  @Test
  void all_은_모든_체크_목록_줄이고_이미_x_인_줄은_보고에서_뺀다_체크_풀기도_된다() {
    DescriptionEdit.Result r = new DescriptionEdit(null, List.of("all"), null).apply(DOC);
    assertThat(r.description()).doesNotContain("- [ ] API").doesNotContain("1. [ ]").contains("* [x] 문서")
        .contains("- [ ] 코드 블록 안의 체크");
    assertThat(r.report().checked()).hasSize(3);
    DescriptionEdit.Result back = new DescriptionEdit(null, null, List.of("문서")).apply(r.description());
    assertThat(back.description()).contains("* [ ] 문서");
    assertThat(back.report().unchecked()).containsExactly("문서");
    // 체크 목록이 없어도 all 은 오류가 아니다
    assertThat(new DescriptionEdit(null, List.of("ALL"), null).apply("글만").report().checked()).isEmpty();
  }

  @Test
  void 맞지_않는_글이_있으면_CHECK_ITEM_NOT_FOUND_이고_그_글들을_알린다() {
    assertThatThrownBy(() -> new DescriptionEdit(null, List.of("API 테스트", "없는 항목", "코드 블록 안의 체크"), null).apply(DOC))
        .isInstanceOfSatisfying(ApiException.class, e -> {
          assertThat(e.code()).isEqualTo("CHECK_ITEM_NOT_FOUND");
          assertThat(e.status().value()).isEqualTo(400);
          assertThat(e.properties()).containsEntry("unmatched", List.of("없는 항목", "코드 블록 안의 체크"))
              .containsEntry("field", "checkItems");
        });
    assertThatThrownBy(() -> new DescriptionEdit(null, null, List.of("없음")).apply(DOC))
        .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.properties()).containsEntry("field", "uncheckItems"));
    assertThatThrownBy(() -> section(" ", "x").apply(DOC))
        .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("INVALID_ARGUMENT"));
  }

  // ── 넘기기 (일감 #199) ──────────────────────────────────────────────

  private static DescriptionEdit move(String item, int to) {
    return new DescriptionEdit(null, null, null, List.of(new DescriptionEdit.Move(item, to)));
  }

  @Test
  void 넘김_표시로_끝나는_줄은_남은_줄로_세지_않는다() {
    String doc = """
        - [ ] 화살표 → #12
        - [ ] 아스키 화살표 -> #13
        - [ ] 괄호 화살표 (→ #14)
        - [ ] 한국어 (#15 로 넘김)
        - [ ] 으로 (#16 으로 넘김).
        - [ ] 영어 (Moved to #17)
        - [ ] 가운데 → #18 에 있으면 넘긴 것이 아니다
        - [ ] 그냥 남은 줄
        - [x] 체크한 줄 → #19
        """;
    assertThat(DescriptionEdit.uncheckedItems(doc)).containsExactly("가운데 → #18 에 있으면 넘긴 것이 아니다", "그냥 남은 줄");
    assertThat(DescriptionEdit.handoffTarget("화살표 → #12")).isEqualTo(12);
    assertThat(DescriptionEdit.handoffTarget("한국어 (#15 로 넘김)")).isEqualTo(15);
    assertThat(DescriptionEdit.handoffTarget("영어 (moved to #17) ")).isEqualTo(17);
    assertThat(DescriptionEdit.handoffTarget("#12 를 본다")).isNull();
  }

  @Test
  void 넘기기는_체크하지_않은_줄_끝에_화살표와_번호를_붙이고_체크_all_은_넘긴_줄을_건드리지_않는다() {
    DescriptionEdit.Result r = move("390px", 200).apply(DOC);
    assertThat(r.description()).contains("1. [ ] 화면, 390px 확인 → #200");
    assertThat(r.report().moved()).containsExactly(java.util.Map.of("item", "화면, 390px 확인", "to", 200));
    assertThat(DescriptionEdit.uncheckedItems(r.description())).containsExactly("API 가 오류를 JSON 으로 준다", "API 테스트");
    // 같은 번호로 다시 넘기면 그대로(보고에서 빠진다)
    DescriptionEdit.Result again = move("390px", 200).apply(r.description());
    assertThat(again.description()).isEqualTo(r.description());
    assertThat(again.report().moved()).isEmpty();
    // 체크 all 은 넘긴 줄을 빼고, 넘기기가 체크보다 먼저다
    DescriptionEdit both = new DescriptionEdit(null, List.of("all"), null, List.of(new DescriptionEdit.Move("API 테스트", 201)));
    DescriptionEdit.Result done = both.apply(DOC);
    assertThat(done.description()).contains("- [ ] API 테스트 → #201").contains("- [x] API 가 오류를 JSON 으로 준다")
        .contains("1. [x] 화면, 390px 확인");
    assertThat(DescriptionEdit.uncheckedItems(done.description())).isEmpty();
    // all — 아직 넘기지 않은 체크하지 않은 줄 모두(코드 블록 밖)
    DescriptionEdit.Result all = move("all", 202).apply(r.description());
    assertThat(all.report().moved()).extracting(m -> m.get("item")).containsExactly("API 가 오류를 JSON 으로 준다", "API 테스트");
    assertThat(all.description()).contains("1. [ ] 화면, 390px 확인 → #200").contains("- [ ] 코드 블록 안의 체크\n");
  }

  @Test
  void 넘길_줄이_없거나_체크한_줄이면_CHECK_ITEM_NOT_FOUND_이고_빈_값은_INVALID_ARGUMENT() {
    assertThatThrownBy(() -> new DescriptionEdit(null, null, null,
        List.of(new DescriptionEdit.Move("API 테스트", 200), new DescriptionEdit.Move("문서", 200))).apply(DOC))
        .isInstanceOfSatisfying(ApiException.class, e -> {
          assertThat(e.code()).isEqualTo("CHECK_ITEM_NOT_FOUND");
          assertThat(e.properties()).containsEntry("field", "moveItems").containsEntry("unmatched", List.of("문서"));
        });
    assertThatThrownBy(() -> move(" ", 200)).isInstanceOfSatisfying(ApiException.class,
        e -> assertThat(e.code()).isEqualTo("INVALID_ARGUMENT"));
    assertThatThrownBy(() -> new DescriptionEdit(null, null, null, List.of(new DescriptionEdit.Move("API", null))))
        .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.properties()).containsEntry("field", "moveItems"));
    assertThat(move("API 테스트", 7).moveTargets()).containsExactly(7);
    assertThat(move("API 테스트", 7).isEmpty()).isFalse();
  }

  // ── 절 채우기의 방법 · 지운 것 (일감 #237) ───────────────────────

  /** 결과 절 아래 소절 · 차트 · 흐름도 · 코드 블록 — 일감 #237 에서 replace 로 지워진 모양 */
  private static final String RESULTS = """
      ## 목적
      본다

      ## 테스트 결과 (선택)

      요약 한 줄

      ### 1. 응답 시간
      ```chart
      {"type": "bar"}
      ```

      ### 2. 흐름
      ```mermaid
      flowchart LR
        A --> B
      ```
      ```java
      int x = 1;
      ```

      ## 남은 것
      없음
      """;

  private static DescriptionEdit section(String heading, String content, Mode mode) {
    return new DescriptionEdit(new DescriptionEdit.Section(heading, content, mode), null, null);
  }

  @Test
  void append_는_절_끝_소절_뒤에_prepend_는_제목_줄_바로_뒤에_더하고_옛_본문은_그대로_둔다() {
    DescriptionEdit.Result a = section("테스트 결과", "- 다시 돌림: 통과", Mode.APPEND).apply(RESULTS);
    assertThat(a.description()).isEqualTo(RESULTS.replace("int x = 1;\n```\n\n## 남은 것", "int x = 1;\n```\n\n- 다시 돌림: 통과\n\n## 남은 것"));
    assertThat(a.report().section()).containsEntry("mode", "append").containsEntry("action", "APPENDED")
        .doesNotContainKeys("removed", "previous");

    DescriptionEdit.Result p = section("## 테스트 결과 (선택)", "요약: 모두 통과", Mode.PREPEND).apply(RESULTS);
    assertThat(p.description()).isEqualTo(RESULTS.replace("## 테스트 결과 (선택)\n\n요약 한 줄",
        "## 테스트 결과 (선택)\n\n요약: 모두 통과\n\n요약 한 줄"));
    assertThat(p.report().section()).containsEntry("mode", "prepend").containsEntry("action", "PREPENDED");

    // 마지막 절
    assertThat(section("남은 것", "더 있음", Mode.APPEND).apply(RESULTS).description()).endsWith("## 남은 것\n없음\n\n더 있음");
    assertThat(section("남은 것", "앞", Mode.PREPEND).apply(RESULTS).description()).endsWith("## 남은 것\n\n앞\n\n없음\n");
    // 빈 절 — 두 방법 모두 바꾸기와 같은 모양
    for (Mode m : Mode.values()) {
      assertThat(section("가", "x", m).apply("## 가\n\n\n## 나\n").description()).as(m.value()).isEqualTo("## 가\n\nx\n\n## 나\n");
    }
    // 더할 글이 없으면 그대로
    assertThat(section("테스트 결과", " \n ", Mode.APPEND).apply(RESULTS).description()).isEqualTo(RESULTS);
    assertThat(section("테스트 결과", null, Mode.PREPEND).apply(RESULTS).description()).isEqualTo(RESULTS);
  }

  @Test
  void 절이_없으면_어느_방법이든_끝에_붙이고_모르는_방법은_INVALID_ARGUMENT_다() {
    for (Mode m : Mode.values()) {
      DescriptionEdit.Result r = section("결론", "끝", m).apply("## 배경\n글\n");
      assertThat(r.description()).as(m.value()).isEqualTo("## 배경\n글\n\n## 결론\n\n끝");
      assertThat(r.report().section()).containsEntry("mode", m.value()).containsEntry("action", "CREATED")
          .doesNotContainKeys("removed", "previous");
    }
    assertThat(Mode.of(null)).isEqualTo(Mode.REPLACE);
    assertThat(Mode.of(" ")).isEqualTo(Mode.REPLACE);
    assertThat(Mode.of(" Append ")).isEqualTo(Mode.APPEND);
    assertThat(Mode.of("PREPEND")).isEqualTo(Mode.PREPEND);
    assertThat(new DescriptionEdit.Section("가", "x").mode()).isEqualTo(Mode.REPLACE);
    assertThat(new DescriptionEdit.Section("가", "x", null).mode()).isEqualTo(Mode.REPLACE);
    for (String wrong : List.of("merge", "add", "replace-all")) {
      assertThatThrownBy(() -> Mode.of(wrong)).isInstanceOfSatisfying(ApiException.class, e -> {
        assertThat(e.code()).isEqualTo("INVALID_ARGUMENT");
        assertThat(e.status().value()).isEqualTo(400);
        assertThat(e.properties()).containsEntry("field", "setSection.mode");
        assertThat(e.getMessage()).contains("replace").contains("append").contains("prepend").doesNotContain(wrong);
      });
    }
  }

  @Test
  void replace_는_새_글에_없는_줄_소절_코드_블록을_언어별로_세고_옛_본문을_싣는다() {
    DescriptionEdit.Result r = section("테스트 결과", "- 통과 12건").apply(RESULTS);
    assertThat(r.description()).contains("## 테스트 결과 (선택)\n\n- 통과 12건\n\n## 남은 것").doesNotContain("### 1.");
    Map<String, Object> s = r.report().section();
    assertThat(s).containsEntry("mode", "replace").containsEntry("action", "REPLACED");
    // 빈 줄을 빼고 13줄: 요약 한 줄(6) · ### 1. 응답 시간(12) · ```chart(8) · {"type": "bar"}(15) · ```(3) · ### 2. 흐름(9) · ```mermaid(10) ·
    // flowchart LR(12) · A --> B(7, 앞 빈칸은 걷는다) · ```(3) · ```java(7) · int x = 1;(10) · ```(3)
    assertThat(s.get("removed")).isEqualTo(new Removed(105, 13, 2, new CodeBlocks(1, 1, 1)));
    String body = DescriptionEdit.sectionBody(RESULTS, "테스트 결과");
    assertThat(body).startsWith("요약 한 줄").endsWith("int x = 1;\n```");
    assertThat(s.get("previous")).isEqualTo(new Previous(body, body.length(), false));

    // 옛 본문을 옮겨 적고 더한 replace 는 지운 것이 없다 — 읽고 고쳐 보내는 흔한 쓰기
    assertThat(section("테스트 결과", body + "\n\n- 통과 12건").apply(RESULTS).report().section().get("removed"))
        .isEqualTo(new Removed(0, 0, 0, new CodeBlocks(0, 0, 0)));
    // 차트는 옮기고 흐름도는 고치고 java 블록 · 둘째 소절은 뺐다 — 바뀐 흐름도도 지운 것으로 센다(내용이 다르면 다른 블록)
    String edited = "### 1. 응답 시간\n```chart\n{\"type\": \"bar\"}\n```\n\n```Mermaid\nflowchart LR\n  A --> C\n```";
    Removed partial = (Removed) section("테스트 결과", edited).apply(RESULTS).report().section().get("removed");
    assertThat(partial.subHeadings()).isEqualTo(1);
    assertThat(partial.codeBlocks()).isEqualTo(new CodeBlocks(0, 1, 1));
    // 새 글에 없는 줄: 요약 한 줄 · ### 2. 흐름 · ```mermaid(줄은 대소문자를 가린다 — 새 글은 ```Mermaid) · A --> B · ```java · int x = 1; ·
    // ``` 하나(``` 셋 가운데 둘은 새 글에 있다)
    assertThat(partial.lines()).isEqualTo(7);
    // 언어 없는 블록 · 물결 울타리 · 정보 글이 붙은 언어는 그 밖 · 그 언어로
    String fences = "## 가\n```\n글\n```\n~~~chart title=\"x\"\n{}\n~~~\n````mermaid\na\n````\n";
    assertThat(((Removed) section("가", "").apply(fences).report().section().get("removed")).codeBlocks())
        .isEqualTo(new CodeBlocks(1, 1, 1));
    // 코드 블록 안의 ### 는 소절이 아니다
    assertThat(((Removed) section("가", "").apply("## 가\n```\n### 안\n```\n### 밖\n").report().section().get("removed"))
        .subHeadings()).isEqualTo(1);
  }

  @Test
  void replace_의_옛_본문은_4000자까지_싣고_잘렸으면_알린다_빈_절은_0() {
    // 유니코드 글자로 센다 — 이모지(두 char)를 반으로 자르지 않는다
    String big = "## 결과\n" + "가".repeat(3000) + "\n" + "😀".repeat(2000) + "\n\n## 끝\n";
    Map<String, Object> s = section("결과", "새 글").apply(big).report().section();
    Previous previous = (Previous) s.get("previous");
    assertThat(previous.truncated()).isTrue();
    assertThat(previous.chars()).isEqualTo(5001);
    assertThat(previous.text()).isEqualTo("가".repeat(3000) + "\n" + "😀".repeat(999));
    assertThat(previous.text().codePointCount(0, previous.text().length())).isEqualTo(DescriptionEdit.PREVIOUS_MAX);
    assertThat(s.get("removed")).isEqualTo(new Removed(5000, 2, 0, new CodeBlocks(0, 0, 0)));
    // 딱 4000자는 자르지 않는다
    String exact = "## 결과\n" + "나".repeat(4000) + "\n";
    assertThat(section("결과", "x").apply(exact).report().section().get("previous"))
        .isEqualTo(new Previous("나".repeat(4000), 4000, false));
    // 템플릿 그대로 빈 절 · 안내 글만 있는 절
    assertThat(section("테스트 결과", "통과").apply("## 목적\n본다\n\n## 테스트 결과 (선택)\n").report().section())
        .containsEntry("removed", new Removed(0, 0, 0, new CodeBlocks(0, 0, 0))).containsEntry("previous", new Previous("", 0, false));
  }

  @Test
  void 절을_먼저_채우고_그_다음_체크한다() {
    DescriptionEdit edit = new DescriptionEdit(new DescriptionEdit.Section("확인", "- [ ] 새 확인 항목"), List.of("새 확인 항목"), null);
    assertThat(edit.apply("## 배경\n글").description()).endsWith("## 확인\n\n- [x] 새 확인 항목");
    assertThat(new DescriptionEdit(null, List.of(" ", ""), null).isEmpty()).isTrue();
  }
}
