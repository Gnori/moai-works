package com.moai.works.api.issue;

import java.util.ArrayList;
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
 *       같은 제목이 여럿이면 첫 절</li>
 *   <li><b>체크</b>({@code check} · {@code uncheck}): 체크 목록 줄({@code - [ ] 글} · {@code * [x] 글} · {@code 1. [ ] 글})의 글에
 *       준 글이 들어 있으면(앞뒤 빈칸 · 빈칸 여럿은 하나로) 그 줄을 {@code [x]} · {@code [ ]} 로. 글과 똑같은 줄이 있으면 그 줄만, 없으면 들어 있는
 *       줄 모두. {@code "all"} 이면 모든 체크 목록 줄. 어느 줄에도 맞지 않는 글이 있으면 아무것도 바꾸지 않고 400
 *       {@code CHECK_ITEM_NOT_FOUND}(값 {@code unmatched} 에 그 글들)</li>
 *   <li>순서: (준 설명 →) 절 → 체크 → 체크 풀기. 코드 블록({@code ```} · {@code ~~~}) 안의 줄은 제목 · 체크 목록으로 보지 않는다</li>
 * </ul>
 *
 * @param section 채울 절 — 없으면 null
 * @param check   [x] 로 바꿀 줄의 글들(또는 "all") — 없으면 빈 목록
 * @param uncheck [ ] 로 바꿀 줄의 글들(또는 "all")
 */
public record DescriptionEdit(Section section, List<String> check, List<String> uncheck) {

  /** 채울 절 — 제목과 본문 */
  public record Section(String heading, String content) {
  }

  /**
   * 무엇을 바꿨나 — 결과에 실어 Agent 가 확인한다.
   *
   * @param section   {@code {heading, action: REPLACED | APPENDED}} — 절을 주지 않았으면 null
   * @param checked   [x] 로 바뀐 줄의 글(이미 [x] 였던 줄은 빼고)
   * @param unchecked [ ] 로 바뀐 줄의 글
   */
  public record Report(Map<String, Object> section, List<String> checked, List<String> unchecked) {
  }

  public record Result(String description, Report report) {
  }

  private static final Pattern TASK = Pattern.compile("^(\\s*(?:[-*+]|\\d{1,9}[.)])\\s+\\[)([ xX])(\\]\\s+)(.*)$");
  private static final Pattern HEADING = Pattern.compile("^ {0,3}(#{1,6})\\s+(.*?)\\s*#*\\s*$");
  private static final Pattern FENCE = Pattern.compile("^ {0,3}(`{3,}|~{3,})");
  private static final String OPTIONAL = "(선택)";
  private static final int MAX_HEADING = 200;

  public DescriptionEdit {
    check = check == null ? List.of() : clean(check);
    uncheck = uncheck == null ? List.of() : clean(uncheck);
  }

  /** 할 일이 있나 — 절도 체크도 없으면 설명을 건드리지 않는다 */
  public boolean isEmpty() {
    return section == null && check.isEmpty() && uncheck.isEmpty();
  }

  /** 설명에 고치기를 한다. 틀린 입력 · 맞지 않는 체크 글은 ApiException(아무것도 바꾸지 않는다) */
  public Result apply(String description) {
    List<String> lines = new ArrayList<>(List.of((description == null ? "" : description).split("\n", -1)));
    Map<String, Object> sectionReport = null;
    if (section != null) {
      sectionReport = setSection(lines);
    }
    List<String> checked = mark(lines, check, true, "checkItems");
    List<String> unchecked = mark(lines, uncheck, false, "uncheckItems");
    return new Result(String.join("\n", lines), new Report(sectionReport, checked, unchecked));
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

  /** 체크하지 않은 체크 목록 줄({@code - [ ] 글})의 글 — 코드 블록 밖, 차례대로 */
  public static List<String> uncheckedItems(String description) {
    List<String> lines = List.of((description == null ? "" : description).split("\n", -1));
    boolean[] fenced = fenced(lines);
    List<String> out = new ArrayList<>();
    for (int i = 0; i < lines.size(); i++) {
      Matcher m = TASK.matcher(lines.get(i));
      if (!fenced[i] && m.matches() && m.group(2).equals(" ")) {
        out.add(m.group(4).strip());
      }
    }
    return out;
  }

  // ── 절 ──────────────────────────────────────────────────────────────

  private Map<String, Object> setSection(List<String> lines) {
    String heading = headingText(section.heading());
    if (heading.isEmpty() || heading.contains("\n") || heading.length() > MAX_HEADING) {
      throw ApiException.badRequest("INVALID_ARGUMENT", "setSection.heading 에 절 제목 한 줄(" + MAX_HEADING
          + "자까지, 예: 처리 결과)을 넣으십시오.").with("field", "setSection");
    }
    String content = section.content() == null ? "" : section.content().strip();
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
    if (start < 0) {
      // 끝의 빈 줄을 걷고 빈 줄 하나를 두고 붙인다
      while (!lines.isEmpty() && lines.get(lines.size() - 1).isBlank()) {
        lines.remove(lines.size() - 1);
      }
      if (!lines.isEmpty()) {
        lines.add("");
      }
      lines.add("## " + heading);
      lines.add("");
      if (!content.isEmpty()) {
        lines.addAll(List.of(content.split("\n", -1)));
      }
      report.put("action", "APPENDED");
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
    List<String> body = new ArrayList<>();
    body.add("");
    if (!content.isEmpty()) {
      body.addAll(List.of(content.split("\n", -1)));
      if (end < lines.size()) {
        body.add("");
      }
    }
    lines.subList(start + 1, end).clear();
    lines.addAll(start + 1, body);
    report.put("action", "REPLACED");
    return report;
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
      targets.addAll(tasks);
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
