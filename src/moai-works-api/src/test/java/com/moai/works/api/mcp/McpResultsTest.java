package com.moai.works.api.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * MCP 결과 줄이기({@link McpResults}, 일감 #282) — 빈 값 빼기 · 비어도 남기는 칸 · 사람 줄이기 · 배열 원소 · 오류 결과. DB 없이.
 */
class McpResultsTest {

  private static Map<String, Object> map(Object... kv) {
    Map<String, Object> m = new LinkedHashMap<>();
    for (int i = 0; i < kv.length; i += 2) {
      m.put((String) kv[i], kv[i + 1]);
    }
    return m;
  }

  @Test
  void 빈_값_null_빈_글_빈_목록_빈_객체_false_는_빼고_0_은_남긴다() {
    Map<String, Object> nested = new HashMap<>();
    nested.put("x", null);
    Map<String, Object> in = map("a", null, "b", "", "c", List.of(), "d", Map.of(), "e", false, "f", 0, "g", true, "h", "글",
        "i", nested, "j", map("k", List.of(), "l", 1));
    assertThat(McpResults.compact("get_issue", in)).containsExactly(Map.entry("f", 0), Map.entry("g", true),
        Map.entry("h", "글"), Map.entry("j", Map.of("l", 1)));
  }

  @Test
  void 비어도_남기는_칸은_어디서든_남는다() {
    Map<String, Object> in = map("checks", List.of(), "next", List.of(), "items", List.of(), "hasNext", false,
        "row", map("active", false, "truncated", false, "deleted", false));
    Map<String, Object> out = McpResults.compact("search_issues", in);
    assertThat(out).containsEntry("checks", List.of()).containsEntry("next", List.of()).containsEntry("items", List.of())
        .containsEntry("hasNext", false).containsEntry("row", Map.of("active", false, "truncated", false));
  }

  @Test
  void 도구_결과_맨_위의_목록과_답인_참거짓은_그_도구에서만_남는다() {
    assertThat(McpResults.compact("list_projects", map("projects", List.of(), "count", 0)))
        .containsEntry("projects", List.of());
    assertThat(McpResults.compact("list_modules", map("modules", List.of(), "canManage", false)))
        .containsEntry("modules", List.of()).containsEntry("canManage", false);
    assertThat(McpResults.compact("log_work", map("byHuman", false, "deleted", false))).containsOnlyKeys("byHuman");
    // 다른 도구 · 안쪽의 같은 이름은 빈 값 규칙 그대로
    assertThat(McpResults.compact("get_issue", map("modules", List.of(), "x", map("byHuman", false)))).isEmpty();
    assertThat(McpResults.KEEP_TOP.keySet()).allMatch(t -> t.matches("[a-z_]+"));
  }

  @Test
  void 사람은_아이디로_표시_이름이_다르면_username_과_name() {
    Map<String, Object> same = map("id", "u1", "username", "jacky", "displayName", "jacky");
    Map<String, Object> differs = map("id", "u2", "username", "jacky", "displayName", "재키");
    Map<String, Object> noName = map("id", "u3", "username", "kim", "displayName", null);
    Map<String, Object> unknown = map("id", "u4", "username", null, "displayName", null);
    Map<String, Object> out = McpResults.compact("get_issue", map("manager", same, "assignee", differs, "createdBy", noName,
        "deletedBy", unknown, "workLogs", List.of(map("user", differs, "hours", 1.5))));
    assertThat(out).containsEntry("manager", "jacky").containsEntry("assignee", Map.of("username", "jacky", "name", "재키"))
        .containsEntry("createdBy", "kim").containsEntry("deletedBy", "u4");
    assertThat(out.get("workLogs")).isEqualTo(List.of(Map.of("user", Map.of("username", "jacky", "name", "재키"), "hours", 1.5)));
    // 다른 모양(칸이 더 있음)은 사람으로 보지 않는다
    assertThat(McpResults.compact("x", map("m", map("id", "1", "username", "a", "displayName", "b", "more", 1))))
        .containsEntry("m", Map.of("id", "1", "username", "a", "displayName", "b", "more", 1));
  }

  @Test
  void 배열의_원소는_빼지_않고_원소인_객체의_안만_줄인다() {
    List<Object> data = Arrays.asList(1, null, 3);
    Map<String, Object> out = McpResults.compact("x", map("data", data, "rows", List.of(map("a", null, "b", 2), map("c", false))));
    assertThat((List<Object>) out.get("data")).containsExactly(1, null, 3);
    assertThat(out.get("rows")).isEqualTo(List.of(Map.of("b", 2), Map.of()));
  }

  @Test
  void 오류_결과는_그대로() {
    Map<String, Object> error = map("error", map("status", 400, "code", "CHECK_ITEM_NOT_FOUND", "unmatched", List.of(), "x", null));
    assertThat(McpResults.compact("update_issue", error)).isSameAs(error);
    assertThat(McpResults.AS_IS).contains("get_markdown_guide");
  }
}
