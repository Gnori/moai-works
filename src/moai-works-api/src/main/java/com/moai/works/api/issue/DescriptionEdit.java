package com.moai.works.api.issue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.moai.works.api.common.ApiException;

/**
 * 설명(Markdown)을 통째로 보내지 않고 고치기 — 절 하나 채우기와 체크 목록 표시(2026-11-24, 일감 #178).
 * 일을 끝낼 때 Agent 가 설명 전체를 다시 써서 {@code - [ ]} 를 {@code - [x]} 로 바꾸고 {@code ## 처리 결과} 를 붙이던 것을 대신한다 —
 * 다른 사람이 고친 부분을 덮어쓰거나 글을 잘못 옮길 일이 없다.
 *
 * <ul>
 *   <li><b>절 채우기</b>({@code section}): {@code ## <제목>} 절의 본문(다음 {@code #} · {@code ##} 제목 앞까지 — {@code ###} 은 본문)을
 *       {@code content} 로 바꾼다. 없으면 설명 끝에 그 절을 붙인다. 제목은 끝의 " (선택)" 과 앞의 {@code #} 을 떼고 견준다(대소문자 무시).
 *       같은 제목이 여럿이면 첫 절. {@link Mode}(2026-11-25, 일감 #237): {@code replace}(기본 — 본문을 바꾼다) · {@code append}(절 끝, 소절
 *       뒤에 더한다) · {@code prepend}(제목 줄 바로 뒤에 더한다). 바꾸기는 지운 것({@link Removed} — 새 글에 없는 옛 줄 · 소절 · 코드 블록)과
 *       옛 본문({@link Previous}, {@value #PREVIOUS_MAX}자까지)을 보고에 싣는다 — Agent 가 "절에 결과를 쓴다" 며 소절 · 차트를 통째로 지우고도
 *       몰랐다(일감 #237)</li>
 *   <li><b>체크</b>({@code check} · {@code uncheck}): 체크 목록 줄({@code - [ ] 글} · {@code * [x] 글} · {@code 1. [ ] 글})의 글에
 *       준 글이 들어 있으면(앞뒤 빈칸 · 빈칸 여럿은 하나로) 그 줄을 {@code [x]} · {@code [ ]} 로. 글과 똑같은 줄이 있으면 그 줄만, 없으면 들어 있는
 *       줄 모두. {@code "all"} 이면 모든 체크 목록 줄. 어느 줄에도 맞지 않는 글이 있으면 아무것도 바꾸지 않고 400
 *       {@code CHECK_ITEM_NOT_FOUND}(값 {@code unmatched} 에 그 글들)</li>
 *   <li><b>넘기기</b>({@code move} — 2026-11-24 일감 #199): 확인하지 못한 체크 목록 줄을 다른 일감으로 넘긴다 — 줄 끝에
 *       {@code " → #<번호>"} 를 붙인다. 체크하지 않은 줄({@code [ ]})만 보고 맞추기는 체크와 같다({@code "all"} 이면 아직 넘기지 않은 체크하지
 *       않은 줄 모두). 맞는 줄이 없으면 400 {@code CHECK_ITEM_NOT_FOUND}(field {@code moveItems}). 넘겨받을 일감이 있는지 · 읽을 수 있는지는
 *       부르는 쪽({@code IssueService.update})이 먼저 본다</li>
 *   <li><b>넘긴 줄</b>: 체크하지 않은 줄의 글이 넘김 표시로 끝나면 넘긴 줄이다 — {@code → #12} · {@code -> #12}(괄호로 싸도 된다) ·
 *       {@code (#12 로 넘김)} · {@code (#12 으로 넘김)} · {@code (moved to #12)}(대소문자 무시). 남은 줄({@link #uncheckedItems})로 세지 않고,
 *       {@code checkItems: "all"} 도 건드리지 않는다</li>
 *   <li>순서: (준 설명 →) 절 → 넘기기 → 체크 → 체크 풀기. 코드 블록({@code ```} · {@code ~~~}) 안의 줄은 제목 · 체크 목록으로 보지 않는다</li>
 * </ul>
 *
 * @param section 채울 절 — 없으면 null
 * @param check   [x] 로 바꿀 줄의 글들(또는 "all") — 없으면 빈 목록
 * @param uncheck [ ] 로 바꿀 줄의 글들(또는 "all")
 * @param move    다른 일감으로 넘길 줄 — 없으면 빈 목록
 */
public record DescriptionEdit(Section section, List<String> check, List<String> uncheck, List<Move> move) {

  /**
   * 채울 절 — 제목 · 본문 · 방법.
   *
   * @param mode 방법 — null 이면 {@link Mode#REPLACE}
   */
  public record Section(String heading, String content, Mode mode) {

    public Section {
      mode = mode == null ? Mode.REPLACE : mode;
    }

    /** 바꾸기(기본) */
    public Section(String heading, String content) {
      this(heading, content, Mode.REPLACE);
    }
  }

  /**
   * 절 채우기의 방법(2026-11-25, 일감 #237) — 절은 {@code ## 제목} 줄부터 다음 {@code #} · {@code ##} 제목 앞까지라 {@code ###} 소절도 든다.
   * 절이 없으면 어느 방법이든 설명 끝에 그 절을 붙인다({@code CREATED}).
   */
  public enum Mode {
    /** 본문을 content 로 바꾼다(소절까지 통째로) — {@code REPLACED} */
    REPLACE,
    /** 절 끝(소절 뒤)에 content 를 더한다 — {@code APPENDED} */
    APPEND,
    /** 제목 줄 바로 뒤에 content 를 더한다 — {@code PREPENDED} */
    PREPEND;

    /** 입력 · 보고의 글 — replace · append · prepend */
    public String value() {
      return name().toLowerCase(Locale.ROOT);
    }

    /** 입력 글(대소문자 · 앞뒤 빈칸 무시) — 비면 REPLACE, 모르는 값이면 400 INVALID_ARGUMENT(field {@code setSection.mode}) */
    public static Mode of(String value) {
      if (value == null || value.isBlank()) {
        return REPLACE;
      }
      for (Mode m : values()) {
        if (m.value().equals(value.strip().toLowerCase(Locale.ROOT))) {
          return m;
        }
      }
      throw ApiException.badRequest("INVALID_ARGUMENT", "setSection.mode 는 replace(기본 — 절 본문을 바꾼다) · append(절 끝, 소절 뒤에 "
          + "더한다) · prepend(제목 줄 바로 뒤에 더한다) 가운데 하나입니다. 아무것도 바꾸지 않았습니다.").with("field", "setSection.mode");
    }
  }

  /**
   * 바꾸기가 지운 것 — 옛 본문에 있었는데 새 글(content)에 없는 것(일감 #237). 새 글에 같은 것을 옮겨 적었으면 세지 않는다.
   *
   * @param chars       새 글에 없는 옛 줄들의 글자 수(줄마다 앞뒤 빈칸을 걷고, 빈 줄은 빼고)
   * @param lines       그 줄 수
   * @param subHeadings 새 글에 없는 소절 제목({@code ###} ~ {@code ######}, 코드 블록 밖) 수
   * @param codeBlocks  새 글에 없는 코드 블록 수 — 울타리 언어로 나눔
   */
  public record Removed(int chars, int lines, int subHeadings, CodeBlocks codeBlocks) {
  }

  /**
   * 코드 블록 수 — 울타리 정보 글의 첫 낱말(언어)로 나눈다.
   *
   * @param chart   {@code ```chart}
   * @param mermaid {@code ```mermaid}
   * @param other   그 밖(언어 없음 포함)
   */
  public record CodeBlocks(int chart, int mermaid, int other) {

    public int total() {
      return chart + mermaid + other;
    }
  }

  /**
   * 바꾸기 전의 본문 — 되돌리려면 이것으로(일감 #237).
   *
   * @param text      옛 본문(앞뒤 빈 줄을 걷음) — {@value #PREVIOUS_MAX}자까지
   * @param chars     옛 본문 전체의 글자 수
   * @param truncated text 가 잘렸나 — 그러면 전체는 일감의 리비전(바로 앞 판)에 있다
   */
  public record Previous(String text, int chars, boolean truncated) {
  }

  /**
   * 넘길 줄 하나 — 체크하지 않은 줄의 글(또는 "all")과 넘겨받을 일감 번호(같은 워크스페이스).
   *
   * @param item 줄의 글 — 체크와 같은 맞추기
   * @param to   넘겨받을 일감 번호
   */
  public record Move(String item, Integer to) {
  }

  /**
   * 무엇을 바꿨나 — 결과에 실어 Agent 가 확인한다.
   *
   * @param section   {@code {heading, mode, action: REPLACED | APPENDED | PREPENDED | CREATED}} — REPLACED 면 {@code removed}
   *                  ({@link Removed}) · {@code previous}({@link Previous})도. 절을 주지 않았으면 null
   * @param checked   [x] 로 바뀐 줄의 글(이미 [x] 였던 줄은 빼고)
   * @param unchecked [ ] 로 바뀐 줄의 글
   * @param moved     넘긴 줄 {@code {item, to}} — 이미 그 번호로 넘긴 줄은 빼고
   */
  public record Report(Map<String, Object> section, List<String> checked, List<String> unchecked,
      List<Map<String, Object>> moved) {
  }

  public record Result(String description, Report report) {
  }

  private static final Pattern TASK = Pattern.compile("^(\\s*(?:[-*+]|\\d{1,9}[.)])\\s+\\[)([ xX])(\\]\\s+)(.*)$");

  /**
   * 넘김 표시 — 줄의 글 끝에 있을 때만: {@code → #12} · {@code -> #12}(괄호로 싸도 된다) · {@code (#12 로 넘김)} · {@code (#12 으로 넘김)} ·
   * {@code (moved to #12)}. 대소문자 무시, 끝의 마침표 하나는 봐준다
   */
  private static final Pattern HANDOFF = Pattern.compile("(?:\\(?\\s*(?:→|->)\\s*#(\\d{1,9})\\s*\\)?"
      + "|\\(\\s*#(\\d{1,9})\\s*(?:으로|로)\\s*넘김\\s*\\)"
      + "|\\(\\s*moved\\s+to\\s+#(\\d{1,9})\\s*\\))\\s*\\.?\\s*$", Pattern.CASE_INSENSITIVE);
  private static final Pattern HEADING = Pattern.compile("^ {0,3}(#{1,6})\\s+(.*?)\\s*#*\\s*$");
  private static final Pattern FENCE = Pattern.compile("^ {0,3}(`{3,}|~{3,})");
  private static final String OPTIONAL = "(선택)";
  private static final int MAX_HEADING = 200;

  /** 보고에 싣는 옛 본문의 최대 글자 수 */
  public static final int PREVIOUS_MAX = 4000;

  public DescriptionEdit {
    check = check == null ? List.of() : clean(check);
    uncheck = uncheck == null ? List.of() : clean(uncheck);
    move = move == null ? List.of() : cleanMoves(move);
  }

  /** 넘기기 없이 — 절 · 체크 · 체크 풀기만 */
  public DescriptionEdit(Section section, List<String> check, List<String> uncheck) {
    this(section, check, uncheck, null);
  }

  /** 할 일이 있나 — 절도 체크도 넘기기도 없으면 설명을 건드리지 않는다 */
  public boolean isEmpty() {
    return section == null && check.isEmpty() && uncheck.isEmpty() && move.isEmpty();
  }

  /** 설명에 고치기를 한다. 틀린 입력 · 맞지 않는 체크 글은 ApiException(아무것도 바꾸지 않는다) */
  public Result apply(String description) {
    List<String> lines = new ArrayList<>(List.of((description == null ? "" : description).split("\n", -1)));
    Map<String, Object> sectionReport = null;
    if (section != null) {
      sectionReport = setSection(lines);
    }
    List<Map<String, Object>> moved = moveLines(lines, move);
    List<String> checked = mark(lines, check, true, "checkItems");
    List<String> unchecked = mark(lines, uncheck, false, "uncheckItems");
    return new Result(String.join("\n", lines), new Report(sectionReport, checked, unchecked, moved));
  }

  /**
   * 줄의 글이 넘김 표시로 끝나면 넘겨받은 일감 번호, 아니면 null — {@code → #12} · {@code -> #12} · {@code (#12 로 넘김)} ·
   * {@code (moved to #12)}
   */
  public static Integer handoffTarget(String itemText) {
    if (itemText == null) {
      return null;
    }
    Matcher m = HANDOFF.matcher(itemText);
    if (!m.find()) {
      return null;
    }
    for (int g = 1; g <= 3; g++) {
      if (m.group(g) != null) {
        return Integer.valueOf(m.group(g));
      }
    }
    return null;
  }

  // ── 읽기 (MCP 결과의 checks — 일감 #182) ─────────────────────────────

  /**
   * {@code ## <heading>} 절의 본문 — 절 채우기와 같은 규칙으로 찾는다(앞의 {@code #} · 끝의 " (선택)" 은 떼고 대소문자 무시, 두 단계 제목만,
   * 코드 블록 밖, 같은 제목이 여럿이면 첫 절). 본문은 다음 {@code #} · {@code ##} 제목 앞까지이고 앞뒤 빈칸을 걷는다. 절이 없으면 null.
   */
  public static String sectionBody(String description, String heading) {
    String wanted = headingText(heading);
    if (wanted.isEmpty()) {
      return null;
    }
    List<String> lines = List.of((description == null ? "" : description).split("\n", -1));
    boolean[] fenced = fenced(lines);
    for (int i = 0; i < lines.size(); i++) {
      Matcher m = HEADING.matcher(lines.get(i));
      if (!fenced[i] && m.matches() && m.group(1).length() == 2 && same(m.group(2), wanted)) {
        StringBuilder body = new StringBuilder();
        for (int j = i + 1; j < lines.size(); j++) {
          Matcher next = HEADING.matcher(lines.get(j));
          if (!fenced[j] && next.matches() && next.group(1).length() <= 2) {
            break;
          }
          body.append(lines.get(j)).append('\n');
        }
        return body.toString().strip();
      }
    }
    return null;
  }

  /** 체크하지 않은 체크 목록 줄({@code - [ ] 글})의 글 — 코드 블록 밖, 차례대로. 다른 일감으로 넘긴 줄({@link #handoffTarget})은 뺀다 */
  public static List<String> uncheckedItems(String description) {
    List<String> lines = List.of((description == null ? "" : description).split("\n", -1));
    boolean[] fenced = fenced(lines);
    List<String> out = new ArrayList<>();
    for (int i = 0; i < lines.size(); i++) {
      Matcher m = TASK.matcher(lines.get(i));
      if (!fenced[i] && m.matches() && m.group(2).equals(" ") && handoffTarget(m.group(4)) == null) {
        out.add(m.group(4).strip());
      }
    }
    return out;
  }

  /** 넘겨받을 일감 번호들 — 부르는 쪽이 있는지 · 읽을 수 있는지 먼저 본다 */
  public List<Integer> moveTargets() {
    return move.stream().map(Move::to).distinct().toList();
  }

  // ── 절 ──────────────────────────────────────────────────────────────

  private Map<String, Object> setSection(List<String> lines) {
    String heading = headingText(section.heading());
    if (heading.isEmpty() || heading.contains("\n") || heading.length() > MAX_HEADING) {
      throw ApiException.badRequest("INVALID_ARGUMENT", "setSection.heading 에 절 제목 한 줄(" + MAX_HEADING
          + "자까지, 예: 처리 결과)을 넣으십시오.").with("field", "setSection");
    }
    String content = section.content() == null ? "" : section.content().strip();
    List<String> added = content.isEmpty() ? List.of() : List.of(content.split("\n", -1));
    boolean[] fenced = fenced(lines);
    int start = -1;
    for (int i = 0; i < lines.size(); i++) {
      Matcher m = HEADING.matcher(lines.get(i));
      if (!fenced[i] && m.matches() && m.group(1).length() == 2 && same(m.group(2), heading)) {
        start = i;
        break;
      }
    }
    Map<String, Object> report = new LinkedHashMap<>();
    report.put("heading", heading);
    report.put("mode", section.mode().value());
    if (start < 0) {
      // 끝의 빈 줄을 걷고 빈 줄 하나를 두고 붙인다 — 어느 방법이든 같다
      while (!lines.isEmpty() && lines.get(lines.size() - 1).isBlank()) {
        lines.remove(lines.size() - 1);
      }
      if (!lines.isEmpty()) {
        lines.add("");
      }
      lines.add("## " + heading);
      lines.add("");
      lines.addAll(added);
      report.put("action", "CREATED");
      return report;
    }
    int end = lines.size();
    for (int i = start + 1; i < lines.size(); i++) {
      Matcher m = HEADING.matcher(lines.get(i));
      if (!fenced[i] && m.matches() && m.group(1).length() <= 2) {
        end = i;
        break;
      }
    }
    boolean last = end == lines.size();
    switch (section.mode()) {
      case REPLACE -> {
        List<String> old = new ArrayList<>(lines.subList(start + 1, end));
        lines.subList(start + 1, end).clear();
        lines.addAll(start + 1, block(added, !last));
        report.put("action", "REPLACED");
        report.put("removed", removed(old, added));
        report.put("previous", previous(old));
      }
      case APPEND -> {
        // 절 끝(소절 뒤)의 빈 줄을 걷고 빈 줄 하나를 두고 더한다
        if (!added.isEmpty()) {
          int keep = end;
          while (keep > start + 1 && lines.get(keep - 1).isBlank()) {
            keep--;
          }
          lines.subList(keep, end).clear();
          lines.addAll(keep, block(added, !last));
        }
        report.put("action", "APPENDED");
      }
      case PREPEND -> {
        // 제목 줄 바로 뒤 — 옛 본문 앞의 빈 줄을 걷고 빈 줄로 나눈다
        if (!added.isEmpty()) {
          int first = start + 1;
          while (first < end && lines.get(first).isBlank()) {
            first++;
          }
          lines.subList(start + 1, first).clear();
          lines.addAll(start + 1, block(added, first < end || !last));
        }
        report.put("action", "PREPENDED");
      }
    }
    return report;
  }

  /** 빈 줄 하나 + 글 + (뒤에 무엇이 이어지면) 빈 줄 하나. 글이 없으면 빈 줄 하나 */
  private static List<String> block(List<String> added, boolean followed) {
    List<String> out = new ArrayList<>();
    out.add("");
    if (!added.isEmpty()) {
      out.addAll(added);
      if (followed) {
        out.add("");
      }
    }
    return out;
  }

  /** 옛 본문 — 앞뒤 빈 줄을 걷고 {@value #PREVIOUS_MAX}자까지(유니코드 글자로 센다) */
  private static Previous previous(List<String> old) {
    String text = String.join("\n", old).strip();
    int chars = text.codePointCount(0, text.length());
    if (chars <= PREVIOUS_MAX) {
      return new Previous(text, chars, false);
    }
    return new Previous(text.substring(0, text.offsetByCodePoints(0, PREVIOUS_MAX)), chars, true);
  }

  /**
   * 바꾸기가 지운 것 — 옛 본문의 줄 · 소절 제목 · 코드 블록 가운데 새 글에 없는 것(같은 것이 여럿이면 그 수만큼 견준다). 줄은 앞뒤 빈칸을 걷고
   * 견주고 빈 줄은 세지 않는다. 코드 블록은 언어와 내용(줄마다 앞뒤 빈칸을 걷음)이 같으면 같은 블록이다
   */
  private static Removed removed(List<String> old, List<String> added) {
    Map<String, Integer> keptLines = counts(added.stream().map(String::strip).filter(l -> !l.isEmpty()).toList());
    int lines = 0;
    int chars = 0;
    for (String line : old) {
      String l = line.strip();
      if (!l.isEmpty() && !take(keptLines, l)) {
        lines++;
        chars += l.codePointCount(0, l.length());
      }
    }
    Map<String, Integer> keptHeadings = counts(subHeadings(added));
    int subHeadings = (int) subHeadings(old).stream().filter(h -> !take(keptHeadings, h)).count();
    Map<String, Integer> keptBlocks = counts(codeBlocks(added));
    int chart = 0;
    int mermaid = 0;
    int other = 0;
    for (String b : codeBlocks(old)) {
      if (take(keptBlocks, b)) {
        continue;
      }
      String language = b.substring(0, b.indexOf('\n'));
      if (language.equals("chart")) {
        chart++;
      } else if (language.equals("mermaid")) {
        mermaid++;
      } else {
        other++;
      }
    }
    return new Removed(chars, lines, subHeadings, new CodeBlocks(chart, mermaid, other));
  }

  /** 코드 블록 밖의 소절 제목({@code ###} ~ {@code ######}) — "### 제목" 꼴로 */
  private static List<String> subHeadings(List<String> lines) {
    boolean[] fenced = fenced(lines);
    List<String> out = new ArrayList<>();
    for (int i = 0; i < lines.size(); i++) {
      Matcher m = HEADING.matcher(lines.get(i));
      if (!fenced[i] && m.matches() && m.group(1).length() >= 3) {
        out.add(m.group(1) + " " + m.group(2).strip());
      }
    }
    return out;
  }

  /**
   * 코드 블록들 — {@link #fenced} 와 같은 울타리 규칙. 하나마다 "언어\n내용"(언어는 정보 글의 첫 낱말 · 소문자, 내용은 줄마다 앞뒤 빈칸을
   * 걷음) — 닫지 않은 블록은 끝까지
   */
  private static List<String> codeBlocks(List<String> lines) {
    List<String> out = new ArrayList<>();
    String open = null;
    String language = null;
    StringBuilder body = null;
    for (String line : lines) {
      Matcher m = FENCE.matcher(line);
      if (open == null) {
        if (m.find()) {
          open = m.group(1);
          String info = line.strip().substring(open.length()).strip();
          language = info.isEmpty() ? "" : info.split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
          body = new StringBuilder();
        }
      } else if (m.find() && m.group(1).charAt(0) == open.charAt(0) && m.group(1).length() >= open.length()
          && line.strip().length() == m.group(1).length()) {
        out.add(language + "\n" + body.toString().strip());
        open = null;
      } else {
        body.append(line.strip()).append('\n');
      }
    }
    if (open != null) {
      out.add(language + "\n" + body.toString().strip());
    }
    return out;
  }

  private static Map<String, Integer> counts(List<String> values) {
    Map<String, Integer> out = new HashMap<>();
    values.forEach(v -> out.merge(v, 1, Integer::sum));
    return out;
  }

  /** 하나 꺼낸다 — 남은 것이 있었으면 true */
  private static boolean take(Map<String, Integer> counts, String value) {
    Integer n = counts.get(value);
    if (n == null || n == 0) {
      return false;
    }
    counts.put(value, n - 1);
    return true;
  }

  /** 견줄 제목 — 앞의 #, 끝의 " (선택)" · 빈칸을 뗀다 */
  private static String headingText(String value) {
    String h = value == null ? "" : value.strip();
    h = h.replaceFirst("^#{1,6}\\s*", "").strip();
    if (h.endsWith(OPTIONAL)) {
      h = h.substring(0, h.length() - OPTIONAL.length()).strip();
    }
    return h;
  }

  private static boolean same(String docHeading, String wanted) {
    return headingText(docHeading).toLowerCase(Locale.ROOT).equals(wanted.toLowerCase(Locale.ROOT));
  }

  // ── 체크 ────────────────────────────────────────────────────────────

  private static List<String> mark(List<String> lines, List<String> wanted, boolean checked, String field) {
    if (wanted.isEmpty()) {
      return List.of();
    }
    boolean[] fenced = fenced(lines);
    List<Integer> tasks = new ArrayList<>();
    for (int i = 0; i < lines.size(); i++) {
      if (!fenced[i] && TASK.matcher(lines.get(i)).matches()) {
        tasks.add(i);
      }
    }
    List<Integer> targets = new ArrayList<>();
    if (wanted.stream().anyMatch(w -> w.equalsIgnoreCase("all"))) {
      // 체크 all 은 다른 일감으로 넘긴 줄을 건드리지 않는다 — 여기서 확인한 것이 아니다(일감 #199)
      tasks.stream().filter(i -> !checked || !handedOff(lines.get(i))).forEach(targets::add);
    } else {
      List<String> unmatched = new ArrayList<>();
      for (String w : wanted) {
        String want = normalize(w);
        List<Integer> exact = tasks.stream().filter(i -> normalize(taskText(lines.get(i))).equals(want)).toList();
        List<Integer> found = exact.isEmpty()
            ? tasks.stream().filter(i -> normalize(taskText(lines.get(i))).contains(want)).toList()
            : exact;
        if (found.isEmpty()) {
          unmatched.add(w);
        }
        found.stream().filter(i -> !targets.contains(i)).forEach(targets::add);
      }
      if (!unmatched.isEmpty()) {
        throw ApiException.badRequest("CHECK_ITEM_NOT_FOUND", "체크 목록에서 찾지 못한 항목이 있습니다(" + field + "): "
            + String.join(" · ", unmatched) + " — get_issue 의 설명에서 '- [ ] …' 줄의 글을 보고 다시 넣으십시오. 아무것도 바꾸지 않았습니다.")
            .with("field", field).with("unmatched", unmatched);
      }
    }
    List<String> changed = new ArrayList<>();
    targets.stream().sorted().forEach(i -> {
      Matcher m = TASK.matcher(lines.get(i));
      if (m.matches()) {
        boolean now = !m.group(2).equals(" ");
        if (now != checked) {
          lines.set(i, m.group(1) + (checked ? "x" : " ") + m.group(3) + m.group(4));
          changed.add(m.group(4).strip());
        }
      }
    });
    return changed;
  }

  private static String taskText(String line) {
    Matcher m = TASK.matcher(line);
    return m.matches() ? m.group(4) : "";
  }

  private static String normalize(String s) {
    return s.strip().replaceAll("\\s+", " ");
  }

  private static List<String> clean(List<String> items) {
    return items.stream().filter(s -> s != null && !s.isBlank()).map(String::strip).toList();
  }

  // ── 넘기기 (일감 #199) ──────────────────────────────────────────────

  /** 체크하지 않은 줄이고 글이 넘김 표시로 끝나는가 */
  private static boolean handedOff(String line) {
    Matcher m = TASK.matcher(line);
    return m.matches() && m.group(2).equals(" ") && handoffTarget(m.group(4)) != null;
  }

  /** 맞는 체크하지 않은 줄 끝에 " → #번호" 를 붙인다 — 이미 그 번호로 넘긴 줄은 그대로. 맞지 않는 글이 있으면 아무것도 바꾸지 않고 400 */
  private static List<Map<String, Object>> moveLines(List<String> lines, List<Move> moves) {
    if (moves.isEmpty()) {
      return List.of();
    }
    boolean[] fenced = fenced(lines);
    List<Integer> open = new ArrayList<>();
    for (int i = 0; i < lines.size(); i++) {
      Matcher m = TASK.matcher(lines.get(i));
      if (!fenced[i] && m.matches() && m.group(2).equals(" ")) {
        open.add(i);
      }
    }
    Map<Integer, Integer> targetByLine = new LinkedHashMap<>();
    List<String> unmatched = new ArrayList<>();
    for (Move mv : moves) {
      List<Integer> found;
      if (mv.item().equalsIgnoreCase("all")) {
        found = open.stream().filter(i -> !handedOff(lines.get(i))).toList();
      } else {
        String want = normalize(mv.item());
        List<Integer> exact = open.stream().filter(i -> normalize(taskText(lines.get(i))).equals(want)).toList();
        found = exact.isEmpty() ? open.stream().filter(i -> normalize(taskText(lines.get(i))).contains(want)).toList() : exact;
        if (found.isEmpty()) {
          unmatched.add(mv.item());
        }
      }
      found.forEach(i -> targetByLine.putIfAbsent(i, mv.to()));
    }
    if (!unmatched.isEmpty()) {
      throw ApiException.badRequest("CHECK_ITEM_NOT_FOUND", "체크하지 않은 체크 목록 줄에서 찾지 못한 항목이 있습니다(moveItems): "
          + String.join(" · ", unmatched) + " — get_issue 의 설명에서 '- [ ] …' 줄의 글을 보고 다시 넣으십시오(이미 체크한 줄은 넘기지 "
          + "않습니다). 아무것도 바꾸지 않았습니다.").with("field", "moveItems").with("unmatched", unmatched);
    }
    List<Map<String, Object>> moved = new ArrayList<>();
    targetByLine.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> {
      Matcher m = TASK.matcher(lines.get(e.getKey()));
      if (m.matches() && !e.getValue().equals(handoffTarget(m.group(4)))) {
        lines.set(e.getKey(), m.group(1) + m.group(2) + m.group(3) + m.group(4).stripTrailing() + " → #" + e.getValue());
        Map<String, Object> one = new LinkedHashMap<>();
        one.put("item", m.group(4).strip());
        one.put("to", e.getValue());
        moved.add(one);
      }
    });
    return moved;
  }

  private static List<Move> cleanMoves(List<Move> moves) {
    List<Move> out = new ArrayList<>();
    for (Move mv : moves) {
      if (mv == null) {
        continue;
      }
      if (mv.item() == null || mv.item().isBlank()) {
        throw ApiException.badRequest("INVALID_ARGUMENT", "moveItems 의 item 에 넘길 체크 목록 줄의 글(또는 \"all\")을 넣으십시오.")
            .with("field", "moveItems");
      }
      if (mv.to() == null || mv.to() <= 0) {
        throw ApiException.badRequest("INVALID_ARGUMENT", "moveItems 의 to 에 넘겨받을 일감 번호(같은 워크스페이스, 예: 200)를 넣으십시오.")
            .with("field", "moveItems");
      }
      out.add(new Move(mv.item().strip(), mv.to()));
    }
    return List.copyOf(out);
  }

  /** 줄마다 코드 블록 안인가(울타리 줄 자체도 안으로 본다) */
  private static boolean[] fenced(List<String> lines) {
    boolean[] out = new boolean[lines.size()];
    String open = null;
    for (int i = 0; i < lines.size(); i++) {
      Matcher m = FENCE.matcher(lines.get(i));
      if (open == null) {
        if (m.find()) {
          open = m.group(1);
          out[i] = true;
        }
      } else {
        out[i] = true;
        if (m.find() && m.group(1).charAt(0) == open.charAt(0) && m.group(1).length() >= open.length()
            && lines.get(i).strip().length() == m.group(1).length()) {
          open = null;
        }
      }
    }
    return out;
  }
}
