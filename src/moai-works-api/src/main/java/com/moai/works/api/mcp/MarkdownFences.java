package com.moai.works.api.mcp;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Markdown 글에서 울타리 코드 블록(```` ``` ```` · {@code ~~~})을 찾는다 — 화면(web-frontend 의 CommonMark 파서)이 코드 블록으로 보는 것과
 * 같은 것을 고르려고(일감 #217, MCP 가 쓴 {@code ```chart} 블록 검사).
 *
 * <p>CommonMark 의 울타리 규칙을 따른다:
 * <ul>
 *   <li>여는 줄: 들여쓰기 0~3칸 + 같은 글자 3개 이상(```` ` ```` 또는 {@code ~}). 그 뒤가 정보 글(info) — 첫 낱말이 언어.
 *       ```` ` ```` 울타리의 정보 글에는 ```` ` ```` 가 들어갈 수 없다(그러면 울타리가 아니다)</li>
 *   <li>닫는 줄: 들여쓰기 0~3칸 + 여는 줄과 같은 글자를 같은 수 이상, 뒤에는 빈칸만. 다른 글자 · 짧은 울타리는 내용이다</li>
 *   <li>닫지 않은 울타리는 글 끝까지(또는 그 울타리를 품은 목록 항목이 끝날 때까지)가 내용이다</li>
 *   <li>4칸 이상 들여 쓴 줄은 울타리가 아니다(들여쓴 코드 · 문단 이어짐) — 목록 항목 안이면 그 항목의 내용 칸만큼 뺀 뒤 센다</li>
 *   <li>목록 항목({@code -} · {@code +} · {@code *} · {@code 1.} · {@code 1)}) 안의 울타리 — 항목의 내용 칸(표시 + 빈칸)을 기억하고, 그보다 덜
 *       들여 쓴 줄(빈 줄 빼고)이 오면 항목이 끝나 그 안의 울타리도 닫힌다. {@code - ```chart} 처럼 항목 첫 줄에 바로 열어도 된다</li>
 *   <li>탭은 4칸 자리로 센다. 줄 끝은 {@code \n} · {@code \r\n} · {@code \r}</li>
 * </ul>
 * 인용({@code >}) 안의 울타리는 보지 않는다(화면은 그리지만 이 검사는 건너뛴다 — 막지 않는 알림이라 놓쳐도 쓰기는 그대로다).
 */
final class MarkdownFences {

  /** 여는 울타리 — 들여쓰기 · 울타리 · 정보 글 */
  private static final Pattern OPEN = Pattern.compile("^( {0,3})(`{3,}|~{3,})(.*)$");

  /** 목록 항목 표시 — 들여쓰기 · 표시 · 뒤 빈칸(없으면 줄 끝) · 나머지 */
  private static final Pattern LIST_ITEM = Pattern.compile("^( {0,3})([-+*]|\\d{1,9}[.)])( +|$)(.*)$");

  /** 줄긋기(*** · - - -) — 목록 항목이 아니다 */
  private static final Pattern THEMATIC_BREAK = Pattern.compile("^ {0,3}([-*_])(?: *\\1){2,} *$");

  private static final Pattern LINE_BREAK = Pattern.compile("\r\n|\r|\n");

  private MarkdownFences() {
  }

  /**
   * 울타리 코드 블록 하나.
   *
   * @param line    여는 줄 번호(1부터 — 글 전체에서)
   * @param marker  울타리 글자(` 또는 ~)
   * @param length  울타리 길이
   * @param info    정보 글(앞뒤 빈칸을 걷음, 없으면 빈 글)
   * @param content 내용 — 여는 줄과 닫는 줄 사이의 줄들을 {@code \n} 으로 이은 것(줄 앞 빈칸은 그대로 — 칸 번호가 글과 같다)
   * @param closed  닫는 줄이 있었나(없으면 글 끝 · 항목 끝까지)
   */
  record Fence(int line, char marker, int length, String info, String content, boolean closed) {

    /** 언어 — 정보 글의 첫 낱말, 소문자. 없으면 빈 글 */
    String language() {
      String first = info.isEmpty() ? "" : info.split("[ \\t]+", 2)[0];
      return first.toLowerCase(Locale.ROOT);
    }
  }

  /** 글의 모든 울타리 코드 블록 — 나온 차례대로. null · 빈 글이면 빈 목록 */
  static List<Fence> of(String markdown) {
    List<Fence> out = new ArrayList<>();
    if (markdown == null || markdown.isEmpty()) {
      return out;
    }
    String[] lines = LINE_BREAK.split(markdown, -1);
    // 열린 목록 항목들의 내용 칸(바깥 → 안)
    Deque<Integer> items = new ArrayDeque<>();
    Open open = null;
    for (int i = 0; i < lines.length; i++) {
      String line = lines[i];
      if (open != null) {
        if (line.isBlank()) {
          open.add(line);
          continue;
        }
        int indent = indent(line);
        if (indent < open.container) {
          // 울타리를 품은 목록 항목이 끝났다 — 울타리도 여기서 끝(CommonMark: 그릇이 닫히면 코드 블록도 닫힌다)
          out.add(open.fence(false));
          open = null;
          // 이 줄은 울타리 밖의 줄로 다시 본다
        } else {
          Matcher close = OPEN.matcher(strip(line, open.container));
          if (close.matches() && close.group(2).charAt(0) == open.marker && close.group(2).length() >= open.length
              && close.group(3).isBlank()) {
            out.add(open.fence(true));
            open = null;
          } else {
            open.add(line);
          }
          continue;
        }
      }
      if (line.isBlank()) {
        continue;
      }
      int indent = indent(line);
      while (!items.isEmpty() && indent < items.peek()) {
        items.pop();
      }
      int container = items.isEmpty() ? 0 : items.peek();
      String rest = strip(line, container);
      // 목록 항목 표시(겹쳐도 — "- 1. ```chart")를 걷고 항목의 내용 칸을 기억한다
      while (!THEMATIC_BREAK.matcher(rest).matches()) {
        Matcher item = LIST_ITEM.matcher(rest);
        if (!item.matches()) {
          break;
        }
        int spaces = item.group(3).length();
        // 표시 뒤 빈칸이 5칸 이상이거나 줄이 끝나면 내용은 표시 + 1칸에서 시작한다
        int gap = spaces == 0 || spaces > 4 ? 1 : spaces;
        container += item.group(1).length() + item.group(2).length() + gap;
        items.push(container);
        rest = spaces > 4 ? " ".repeat(spaces - 1) + item.group(4) : item.group(4);
      }
      Matcher m = OPEN.matcher(rest);
      if (m.matches()) {
        char marker = m.group(2).charAt(0);
        String info = m.group(3);
        if (marker == '`' && info.indexOf('`') >= 0) {
          continue;
        }
        open = new Open(i + 1, marker, m.group(2).length(), info.strip(), container);
      }
    }
    if (open != null) {
      out.add(open.fence(false));
    }
    return out;
  }

  /** 열린 울타리 — 내용을 모은다 */
  private static final class Open {
    final int line;
    final char marker;
    final int length;
    final String info;
    final int container;
    final StringBuilder content = new StringBuilder();
    boolean empty = true;

    Open(int line, char marker, int length, String info, int container) {
      this.line = line;
      this.marker = marker;
      this.length = length;
      this.info = info;
      this.container = container;
    }

    void add(String line) {
      if (!empty) {
        content.append('\n');
      }
      content.append(line);
      empty = false;
    }

    Fence fence(boolean closed) {
      return new Fence(line, marker, length, info, content.toString(), closed);
    }
  }

  /** 줄 앞 빈칸의 칸 수 — 탭은 다음 4칸 자리까지 */
  static int indent(String line) {
    int col = 0;
    for (int i = 0; i < line.length(); i++) {
      char c = line.charAt(i);
      if (c == ' ') {
        col++;
      } else if (c == '\t') {
        col += 4 - col % 4;
      } else {
        break;
      }
    }
    return col;
  }

  /** 줄 앞에서 빈칸 n 칸을 걷는다(탭은 칸으로 풀어서) — 남은 빈칸은 그대로 */
  static String strip(String line, int n) {
    if (n <= 0) {
      return line;
    }
    int col = 0;
    int i = 0;
    while (i < line.length() && col < n) {
      char c = line.charAt(i);
      if (c == ' ') {
        col++;
      } else if (c == '\t') {
        col += 4 - col % 4;
      } else {
        break;
      }
      i++;
    }
    // 탭이 n 을 넘겨 걸쳤으면 넘친 칸만큼 빈칸으로 돌려준다
    return " ".repeat(Math.max(0, col - n)) + line.substring(i);
  }
}
