package com.moai.works.api.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * 울타리 코드 블록 찾기 — 화면(CommonMark)이 코드 블록으로 보는 것과 같은 것을 고른다(일감 #217, ```chart 검사의 바탕). DB 없이.
 */
class MarkdownFencesTest {

  private static List<MarkdownFences.Fence> of(String md) {
    return MarkdownFences.of(md);
  }

  @Test
  void 백틱과_물결_울타리_줄_번호_정보_글() {
    List<MarkdownFences.Fence> f = of("# 제목\n\n```chart\n{\"a\":1}\n```\n글\n~~~ts title=\"x.ts\" {1,3-5}\nconst a = 1;\n~~~\n");
    assertThat(f).hasSize(2);
    assertThat(f.get(0).line()).isEqualTo(3);
    assertThat(f.get(0).marker()).isEqualTo('`');
    assertThat(f.get(0).language()).isEqualTo("chart");
    assertThat(f.get(0).content()).isEqualTo("{\"a\":1}");
    assertThat(f.get(0).closed()).isTrue();
    assertThat(f.get(1).line()).isEqualTo(7);
    assertThat(f.get(1).marker()).isEqualTo('~');
    assertThat(f.get(1).info()).isEqualTo("ts title=\"x.ts\" {1,3-5}");
    assertThat(f.get(1).language()).isEqualTo("ts");
    assertThat(f.get(1).content()).isEqualTo("const a = 1;");
  }

  @Test
  void 언어는_대소문자를_가리지_않고_정보_글의_첫_낱말이다() {
    assertThat(of("```CHART\n{}\n```").get(0).language()).isEqualTo("chart");
    assertThat(of("``` Chart  title=\"주간\"\n{}\n```").get(0).language()).isEqualTo("chart");
    assertThat(of("```\t chart\n{}\n```").get(0).language()).isEqualTo("chart");
    assertThat(of("```\n{}\n```").get(0).language()).isEmpty();
    assertThat(of("```charts\n{}\n```").get(0).language()).isEqualTo("charts");
  }

  @Test
  void 닫는_울타리는_같은_글자를_같은_수_이상_뒤에는_빈칸만() {
    // 네 개로 열면 세 개는 내용이다 — 안에 ``` 를 품는 블록
    List<MarkdownFences.Fence> f = of("````md\n```chart\n{}\n```\n````\n");
    assertThat(f).hasSize(1);
    assertThat(f.get(0).language()).isEqualTo("md");
    assertThat(f.get(0).content()).isEqualTo("```chart\n{}\n```");
    // 물결로 연 블록은 백틱으로 닫히지 않는다
    assertThat(of("~~~chart\n{}\n```\n~~~").get(0).content()).isEqualTo("{}\n```");
    // 더 긴 닫기 · 뒤 빈칸은 닫힌다, 뒤에 글이 있으면 내용
    assertThat(of("```chart\n{}\n`````   \n밖").get(0).content()).isEqualTo("{}");
    assertThat(of("```chart\n{}\n``` 아님\n```").get(0).content()).isEqualTo("{}\n``` 아님");
    // 닫는 줄도 4칸 들여 쓰면 내용이다
    assertThat(of("```chart\n{}\n    ```\n```").get(0).content()).isEqualTo("{}\n    ```");
  }

  @Test
  void 닫지_않은_울타리는_글_끝까지() {
    List<MarkdownFences.Fence> f = of("앞\n```chart\n{\"type\":\"bar\"}\n\n뒤 글도 내용");
    assertThat(f).hasSize(1);
    assertThat(f.get(0).closed()).isFalse();
    assertThat(f.get(0).content()).isEqualTo("{\"type\":\"bar\"}\n\n뒤 글도 내용");
  }

  @Test
  void 네_칸_들여쓴_줄과_백틱이_든_정보_글은_울타리가_아니다() {
    assertThat(of("    ```chart\n    {}\n    ```")).isEmpty();
    assertThat(of("글\n    ```chart\n{}")).isEmpty();
    assertThat(of("```chart `x`\n{}\n```")).hasSize(1).first().satisfies(f -> assertThat(f.language()).isEmpty());
    // 물결 울타리의 정보 글에는 백틱이 들어가도 된다
    assertThat(of("~~~chart `x`\n{}\n~~~").get(0).language()).isEqualTo("chart");
    // 세 칸까지는 울타리
    assertThat(of("   ```chart\n{}\n   ```").get(0).content()).isEqualTo("{}");
  }

  @Test
  void 목록_항목_안의_들여쓴_울타리() {
    String md = "- 항목\n\n    ```chart\n    {\"type\":\"gauge\",\"value\":1}\n    ```\n- 다음";
    // 항목 내용 칸 2 — 4칸 들여쓰기는 항목 안에서 2칸이라 울타리
    List<MarkdownFences.Fence> f = of(md);
    assertThat(f).hasSize(1);
    assertThat(f.get(0).line()).isEqualTo(3);
    assertThat(f.get(0).content().strip()).isEqualTo("{\"type\":\"gauge\",\"value\":1}");
    assertThat(f.get(0).closed()).isTrue();

    // 번호 목록(내용 칸 3) · 항목 첫 줄에서 바로 열기 · 겹친 목록
    assertThat(of("1. 첫째\n   ```chart\n   {}\n   ```").get(0).content()).isEqualTo("   {}");
    assertThat(of("- ```chart\n  {}\n  ```").get(0).language()).isEqualTo("chart");
    assertThat(of("- 바깥\n  - 안\n      ```chart\n      {}\n      ```").get(0).line()).isEqualTo(3);
    // 탭 들여쓰기도 칸으로 센다
    assertThat(of("- 항목\n\t```chart\n\t{}\n\t```")).hasSize(1);
  }

  @Test
  void 항목이_끝나면_그_안의_울타리도_닫힌다() {
    List<MarkdownFences.Fence> f = of("- 항목\n  ```chart\n  {}\n덜 들여 쓴 줄\n```chart\n{}\n```");
    assertThat(f).hasSize(2);
    assertThat(f.get(0).closed()).isFalse();
    assertThat(f.get(0).content()).isEqualTo("  {}");
    // 덜 들여 쓴 줄 뒤의 울타리는 맨 위 울타리로 다시 본다
    assertThat(f.get(1).line()).isEqualTo(5);
    assertThat(f.get(1).closed()).isTrue();
  }

  @Test
  void 목록이_끝난_뒤의_맨_위_울타리와_줄긋기() {
    assertThat(of("- 항목\n\n```chart\n{}\n```").get(0).line()).isEqualTo(3);
    // 줄긋기(* * *)는 목록이 아니라 그 뒤 4칸 줄은 들여쓴 코드다
    assertThat(of("* * *\n    ```chart\n    {}")).isEmpty();
  }

  @Test
  void 줄_끝은_CRLF_CR_도_되고_빈_글은_없다() {
    assertThat(of("```chart\r\n{}\r\n```\r\n").get(0).content()).isEqualTo("{}");
    assertThat(of("```chart\r{}\r```").get(0).content()).isEqualTo("{}");
    assertThat(of(null)).isEmpty();
    assertThat(of("")).isEmpty();
    assertThat(of("```")).hasSize(1).first().satisfies(f -> assertThat(f.content()).isEmpty());
  }

  @Test
  void 칸_세기() {
    assertThat(MarkdownFences.indent("\t a")).isEqualTo(5);
    assertThat(MarkdownFences.indent("  \ta")).isEqualTo(4);
    assertThat(MarkdownFences.strip("\tx", 2)).isEqualTo("  x");
    assertThat(MarkdownFences.strip("    x", 2)).isEqualTo("  x");
    assertThat(MarkdownFences.strip(" x", 2)).isEqualTo("x");
  }
}
