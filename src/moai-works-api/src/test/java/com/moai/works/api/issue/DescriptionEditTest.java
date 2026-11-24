package com.moai.works.api.issue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.moai.works.api.common.ApiException;

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
    assertThat(r.report().section()).containsEntry("action", "APPENDED");
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

  @Test
  void 절을_먼저_채우고_그_다음_체크한다() {
    DescriptionEdit edit = new DescriptionEdit(new DescriptionEdit.Section("확인", "- [ ] 새 확인 항목"), List.of("새 확인 항목"), null);
    assertThat(edit.apply("## 배경\n글").description()).endsWith("## 확인\n\n- [x] 새 확인 항목");
    assertThat(new DescriptionEdit(null, List.of(" ", ""), null).isEmpty()).isTrue();
  }
}
