package com.moai.works.api.issue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 공수 집계 CSV — 화면의 표와 같은 줄 · 같은 값을 적는다(맨 끝 줄은 합계). 엑셀이 한글을 깨뜨리지 않게 UTF-8 BOM 으로 시작한다.
 *
 * <p>글자 칸(기준 값 · 이름)이 {@code = + - @} · 탭 · CR 로 시작하면 앞에 {@code '} 를 붙인다 — 스프레드시트가 남이 쓴 일감 제목을
 * 수식으로 실행하지 않게(CSV 수식 주입). 숫자 칸은 서버가 만든 값이라 그대로 둔다.
 */
final class EffortCsv {

  private EffortCsv() {
  }

  private static final Map<String, List<String>> HEADERS = Map.of(
      "ko", List.of("이름", "예상 공수(시간)", "실제 투입(시간)", "차이(시간)", "비율(%)", "일감 수", "기록 수"),
      "en", List.of("Name", "Estimated (h)", "Actual (h)", "Difference (h)", "Ratio (%)", "Issues", "Logs"));

  private static final Map<String, Map<String, String>> GROUP_NAMES = Map.of(
      "ko", Map.of("person", "사람", "project", "프로젝트", "issue", "일감", "type", "유형", "day", "날", "week", "주",
          "month", "달"),
      "en", Map.of("person", "Person", "project", "Project", "issue", "Issue", "type", "Type", "day", "Day", "week",
          "Week", "month", "Month"));

  private static final Map<String, String> TOTAL = Map.of("ko", "합계", "en", "Total");

  /** @param lang 머리 줄의 언어 — en, 그 밖은 ko */
  static String write(EffortService.Report report, String lang) {
    String l = "en".equals(lang) ? "en" : "ko";
    StringBuilder out = new StringBuilder("﻿");
    List<String> header = new ArrayList<>();
    header.add(GROUP_NAMES.get(l).get(report.groupBy()));
    header.addAll(HEADERS.get(l));
    line(out, header);
    for (EffortService.Row r : report.rows()) {
      line(out, List.of(text(keyOf(report.groupBy(), r)), text(r.label()), num(r.estimatedHours()),
          num(r.actualHours()), num(r.diffHours()), r.ratio() == null ? "" : r.ratio().toString(),
          Integer.toString(r.issueCount()), Integer.toString(r.logCount())));
    }
    EffortService.Totals t = report.total();
    line(out, List.of(TOTAL.get(l), "", num(t.estimatedHours()), num(t.actualHours()), num(t.diffHours()),
        t.ratio() == null ? "" : t.ratio().toString(), Integer.toString(t.issueCount()), Integer.toString(t.logCount())));
    return out.toString();
  }

  /** 첫 칸 — 사람은 아이디(없으면 id), 그 밖은 줄의 기준 값(프로젝트 주소 이름 · 주소#번호 · 유형 key · 기간) */
  private static String keyOf(String groupBy, EffortService.Row r) {
    if ("person".equals(groupBy) && r.person() != null && r.person().username() != null) {
      return r.person().username();
    }
    return r.key();
  }

  private static String num(BigDecimal value) {
    return value == null ? "" : value.stripTrailingZeros().toPlainString();
  }

  /** 수식으로 읽힐 수 있는 글자 칸 막기 */
  static String text(String value) {
    if (value == null || value.isEmpty()) {
      return "";
    }
    char first = value.charAt(0);
    return first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r'
        ? "'" + value : value;
  }

  private static void line(StringBuilder out, List<String> cells) {
    for (int i = 0; i < cells.size(); i++) {
      if (i > 0) {
        out.append(',');
      }
      out.append(quote(cells.get(i)));
    }
    out.append("\r\n");
  }

  /** RFC 4180 — 쉼표 · 따옴표 · 줄바꿈이 있으면 따옴표로 감싸고 안의 따옴표는 두 번 */
  private static String quote(String value) {
    if (value.indexOf(',') < 0 && value.indexOf('"') < 0 && value.indexOf('\n') < 0 && value.indexOf('\r') < 0) {
      return value;
    }
    return '"' + value.replace("\"", "\"\"") + '"';
  }
}
