package db.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.function.IntUnaryOperator;

import org.junit.jupiter.api.Test;

/**
 * V23 의 글 속 #번호 바꾸기 — 화면(markdown-plugins.ts 의 ISSUE_REF)이 일감 링크로 그리는 곳만 바꾸고, 코드 · 링크 · 주소는 그대로 둔다.
 */
class IssueRefRewriteTest {

  /** 옛 번호 1 → 11, 2 → 12, 12 → 57. 그 밖은 모르는 번호 */
  private static final IntUnaryOperator MAP = n -> Map.of(1, 11, 2, 12, 12, 57).getOrDefault(n, -1);

  private static String rewrite(String text) {
    return V23__workspace_issue_number.rewriteRefs(text, MAP).text();
  }

  @Test
  void 글_속_번호를_바꾸고_센다() {
    V23__workspace_issue_number.Rewrite r = V23__workspace_issue_number.rewriteRefs("#1 은 #2 와 (#12) 에 이어진다.\n- #1 목록", MAP);
    assertThat(r.text()).isEqualTo("#11 은 #12 와 (#57) 에 이어진다.\n- #11 목록");
    assertThat(r.count()).isEqualTo(4);
    // 모르는 번호는 그대로
    assertThat(rewrite("#3 과 #999")).isEqualTo("#3 과 #999");
    // 줄 끝 · CRLF · 강조 · 제목
    assertThat(rewrite("**#1**\r\n## #2 제목\r\n> #12")).isEqualTo("**#11**\r\n## #12 제목\r\n> #57");
  }

  @Test
  void 화면이_번호로_보지_않는_꼴은_그대로() {
    // 글자 · 숫자 · & · # · / 바로 뒤, 뒤에 글자가 붙은 것
    assertThat(rewrite("C#1 &#12; ##1 a/#2 #1a #12번")).isEqualTo("C#1 &#12; ##1 a/#2 #1a #12번");
    // 인라인 코드 · 링크(글과 주소) · 그림 · 꺾쇠 자동 링크 · 맨 주소 · 링크 정의
    assertThat(rewrite("`#1` 와 #1")).isEqualTo("`#1` 와 #11");
    assertThat(rewrite("``a `#1` b`` #2")).isEqualTo("``a `#1` b`` #12");
    assertThat(rewrite("[#1 보기](https://example.com/#2) 뒤 #1")).isEqualTo("[#1 보기](https://example.com/#2) 뒤 #11");
    assertThat(rewrite("![#1](attachment:abc) [x][#2]")).isEqualTo("![#1](attachment:abc) [x][#2]");
    assertThat(rewrite("<https://example.com/a#1> https://example.com/b-#2 www.example.com/c-#12 끝 #1"))
        .isEqualTo("<https://example.com/a#1> https://example.com/b-#2 www.example.com/c-#12 끝 #11");
    assertThat(rewrite("[ref]: https://example.com/#1\n#1")).isEqualTo("[ref]: https://example.com/#1\n#11");
    // 체크 목록의 [ ] · [x] 는 링크가 아니다
    assertThat(rewrite("- [x] #1 끝냄\n- [ ] #2 남음")).isEqualTo("- [x] #11 끝냄\n- [ ] #12 남음");
  }

  @Test
  void 코드_블록은_그대로() {
    String fenced = "앞 #1\n```bash\necho #1\n```\n~~~\n#2\n~~~\n뒤 #2";
    assertThat(rewrite(fenced)).isEqualTo("앞 #11\n```bash\necho #1\n```\n~~~\n#2\n~~~\n뒤 #12");
    // 닫히지 않은 펜스는 끝까지 코드
    assertThat(rewrite("```\n#1")).isEqualTo("```\n#1");
    // 들여쓴 코드 블록(빈 줄 뒤 네 칸) — 목록의 들여쓴 줄은 글이다
    assertThat(rewrite("글 #1\n\n    code #1\n\n뒤 #1")).isEqualTo("글 #11\n\n    code #1\n\n뒤 #11");
    assertThat(rewrite("- 위 #1\n\n    아래 #2")).isEqualTo("- 위 #11\n\n    아래 #12");
    assertThat(rewrite("- 위\n    - 하위 #1")).isEqualTo("- 위\n    - 하위 #11");
  }

  @Test
  void 빈_글과_번호가_없는_글() {
    assertThat(V23__workspace_issue_number.rewriteRefs("", MAP).count()).isZero();
    assertThat(V23__workspace_issue_number.rewriteRefs(null, MAP).text()).isEmpty();
    assertThat(rewrite("번호 없음")).isEqualTo("번호 없음");
  }
}
