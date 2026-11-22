package com.moai.works.api.issue;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/** 줄 단위 차이 — 리비전 비교(FR-WIKI-007). */
class LineDiffTest {

  private static List<String> marks(LineDiff.Result result) {
    return result.lines().stream()
        .map(line -> (line.op() == LineDiff.Op.ADD ? "+" : line.op() == LineDiff.Op.DEL ? "-" : " ") + line.text())
        .toList();
  }

  @Test
  void 고친_줄은_지운_줄과_넣은_줄로_나온다() {
    LineDiff.Result result = LineDiff.diff("가\n나\n다\n라", "가\n나를 고침\n다\n라\n마");
    assertThat(marks(result)).containsExactly(" 가", "-나", "+나를 고침", " 다", " 라", "+마");
    assertThat(result.added()).isEqualTo(2);
    assertThat(result.removed()).isEqualTo(1);
    assertThat(result.coarse()).isFalse();
  }

  @Test
  void 줄_번호는_옛_글과_새_글의_자리를_따로_센다() {
    LineDiff.Result result = LineDiff.diff("가\n나\n다", "가\n새 줄\n나\n다");
    assertThat(result.lines()).extracting(LineDiff.Line::oldLine).containsExactly(1, null, 2, 3);
    assertThat(result.lines()).extracting(LineDiff.Line::newLine).containsExactly(1, 2, 3, 4);
  }

  @Test
  void 빈_글과_견주면_모두_넣거나_지운_줄이다() {
    assertThat(marks(LineDiff.diff("", "가\n나"))).containsExactly("+가", "+나");
    assertThat(marks(LineDiff.diff("가\n나", null))).containsExactly("-가", "-나");
    assertThat(LineDiff.diff("같다", "같다").lines()).extracting(LineDiff.Line::op).containsOnly(LineDiff.Op.SAME);
    assertThat(LineDiff.diff("", "").lines()).isEmpty();
  }

  @Test
  void 가운데가_너무_크면_줄을_맞추지_않고_통째로_바꾼_것으로_준다() {
    StringBuilder oldText = new StringBuilder("머리\n");
    StringBuilder newText = new StringBuilder("머리\n");
    for (int i = 0; i < 2100; i++) {
      oldText.append("옛 ").append(i).append('\n');
      newText.append("새 ").append(i).append('\n');
    }
    oldText.append("꼬리");
    newText.append("꼬리");
    LineDiff.Result result = LineDiff.diff(oldText.toString(), newText.toString());
    assertThat(result.coarse()).isTrue();
    assertThat(result.removed()).isEqualTo(2100);
    assertThat(result.added()).isEqualTo(2100);
    // 앞뒤의 같은 줄은 그대로 맞춘다
    assertThat(result.lines().getFirst().op()).isEqualTo(LineDiff.Op.SAME);
    assertThat(result.lines().getLast().text()).isEqualTo("꼬리");
  }

  @Test
  void 삼만_자_두_판의_비교가_빨리_끝난다() {
    StringBuilder text = new StringBuilder();
    for (int i = 0; text.length() < 29_000; i++) {
      text.append("## 섹션 ").append(i).append(" {#section-").append(i).append("}\n\n")
          .append("이 섹션의 본문 줄이다. ".repeat(8)).append("\n\n");
    }
    String oldText = text.toString();
    String newText = oldText.replace("섹션 3 ", "셋째 섹션 ").replace("섹션 40 ", "마흔째 섹션 ") + "\n맨 끝에 붙인 줄";
    long started = System.nanoTime();
    LineDiff.Result result = LineDiff.diff(oldText, newText);
    long millis = (System.nanoTime() - started) / 1_000_000;
    assertThat(result.coarse()).isFalse();
    assertThat(result.added()).isGreaterThanOrEqualTo(3);
    assertThat(millis).isLessThan(2_000); // 요구사항: 2초 안
  }
}
