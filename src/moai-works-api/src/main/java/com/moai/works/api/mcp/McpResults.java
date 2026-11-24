package com.moai.works.api.mcp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * MCP 도구 결과를 작게 — 도구가 돌려준 JSON 을 Agent 에게 보내기 전에 한 곳에서 줄인다(일감 #282). Agent 는 단계마다 결과를 다시 읽으므로
 * 빈 칸 · 되풀이가 그대로 맥락 비용이다. REST 응답은 이 길을 지나지 않는다(도구 콜백만 감싼다 — {@link McpServerConfiguration}).
 *
 * <ul>
 *   <li><b>빈 값은 싣지 않는다</b> — null · 빈 글 · 빈 목록 · 빈 객체(안을 줄인 뒤 빈 것 포함) · false. 없는 칸은 그 값이다(null · 0개 · false).
 *       숫자 0 은 값이라 남긴다</li>
 *   <li><b>비어도 남기는 칸</b> — 어디서든 {@link #KEEP}(Agent 가 찾는 칸: checks · next · 목록의 items · hasNext · active(false 가 뜻) ·
 *       truncated), 도구 결과 맨 위에서는 {@link #KEEP_TOP}(그 도구가 돌려주는 목록 자체 — 비면 "없음"이 답이다 · 답인 참거짓)</li>
 *   <li><b>사람</b> {@code {id, username, displayName}} 은 아이디 글 하나로, 표시 이름이 아이디와 다르면 {@code {username, name}} 으로 —
 *       MCP 도구는 사람을 아이디(username)나 me 로 받으므로 id 는 쓸 데가 없다</li>
 *   <li>배열의 원소는 빼지 않는다(자리가 뜻인 값 — 예: 차트 data 의 null) — 원소인 객체의 안만 줄인다</li>
 *   <li>그대로 두는 것: 오류 결과 {@code {"error": …}}(붙인 값이 비어도 뜻이 있다), {@link #AS_IS} 의 도구(꼴 안내 — 예의 값 그대로)</li>
 * </ul>
 * 일감 결과의 모양(이력 · summary 와 겹치는 칸 · 모듈 key · 찾기 줄의 프로젝트)은 {@link IssueShapes} 가 도구에서 정한다.
 */
final class McpResults {

  private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
  };

  /** 비어도 · false 여도 남기는 칸 — 결과의 어디에 있든 */
  static final Set<String> KEEP = Set.of("checks", "next", "items", "hasNext", "active", "truncated");

  /** 도구 결과 맨 위에서 비어도 남기는 칸 — 그 도구가 돌려주는 목록 자체와 답인 참거짓 */
  static final Map<String, Set<String>> KEEP_TOP = Map.ofEntries(
      Map.entry("list_workspaces", Set.of("workspaces")),
      Map.entry("list_projects", Set.of("projects")),
      Map.entry("list_issue_types", Set.of("types")),
      Map.entry("list_modules", Set.of("modules", "canManage")),
      Map.entry("list_attachments", Set.of("attachments")),
      Map.entry("list_skills", Set.of("skills")),
      Map.entry("get_skill", Set.of("changes")),
      Map.entry("list_issue_templates", Set.of("templates", "canManage")),
      Map.entry("log_work", Set.of("byHuman")),
      Map.entry("set_module", Set.of("created")));

  /** 줄이지 않고 그대로 보내는 도구 */
  static final Set<String> AS_IS = Set.of("get_markdown_guide");

  private McpResults() {
  }

  /** 입구 하나의 도구들을 감싼다 */
  static List<ToolCallback> compact(List<ToolCallback> callbacks, JsonMapper json) {
    List<ToolCallback> out = new ArrayList<>();
    for (ToolCallback c : callbacks) {
      out.add(AS_IS.contains(c.getToolDefinition().name()) ? c : new Compacted(c, json));
    }
    return out;
  }

  /** 도구 결과(JSON 객체) 하나를 줄인다 — 오류 결과는 그대로 */
  static Map<String, Object> compact(String tool, Map<String, Object> result) {
    if (result.containsKey("error")) {
      return result;
    }
    Set<String> keepTop = KEEP_TOP.getOrDefault(tool, Set.of());
    Map<String, Object> out = new LinkedHashMap<>();
    for (Map.Entry<String, Object> e : result.entrySet()) {
      Object v = value(e.getValue());
      if (keepTop.contains(e.getKey()) || KEEP.contains(e.getKey()) || !empty(v)) {
        out.put(e.getKey(), v);
      }
    }
    return out;
  }

  private static Object value(Object v) {
    if (v instanceof Map<?, ?> m) {
      if (isPerson(m)) {
        return person(m.get("id"), m.get("username"), m.get("displayName"));
      }
      Map<String, Object> out = new LinkedHashMap<>();
      for (Map.Entry<?, ?> e : m.entrySet()) {
        String key = String.valueOf(e.getKey());
        Object child = value(e.getValue());
        if (KEEP.contains(key) || !empty(child)) {
          out.put(key, child);
        }
      }
      return out;
    }
    if (v instanceof List<?> list) {
      List<Object> out = new ArrayList<>(list.size());
      for (Object item : list) {
        out.add(value(item));
      }
      return out;
    }
    return v;
  }

  private static boolean empty(Object v) {
    return v == null || Boolean.FALSE.equals(v) || (v instanceof String s && s.isEmpty())
        || (v instanceof List<?> l && l.isEmpty()) || (v instanceof Map<?, ?> m && m.isEmpty());
  }

  /** 사람 보기({@code IssueViews.Person} 의 모양 그대로) */
  private static boolean isPerson(Map<?, ?> m) {
    return m.size() == 3 && m.containsKey("id") && m.containsKey("username") && m.containsKey("displayName");
  }

  /**
   * 사람 하나 — 아이디 글, 표시 이름이 아이디와 다르면 {@code {username, name}}. 아이디를 모르면(계정 사본이 없음) 이름, 그것도 없으면 id.
   * 이력처럼 id · 이름만 있는 값은 {@link IssueShapes} 가 계정 사본으로 아이디를 찾아 이것을 부른다.
   */
  static Object person(Object id, Object username, Object name) {
    String u = username == null ? null : username.toString();
    String n = name == null ? null : name.toString();
    if (u == null || u.isBlank()) {
      return n != null && !n.isBlank() ? n : id == null ? null : id.toString();
    }
    if (n == null || n.isBlank() || n.equals(u)) {
      return u;
    }
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("username", u);
    m.put("name", n);
    return m;
  }

  // ── 감싼 도구 ──────────────────────────────────────────────────────────

  /** 도구가 돌려준 JSON 객체를 줄여 다시 쓴다 — JSON 객체가 아니면(그럴 일은 없다) 그대로 */
  private static final class Compacted implements ToolCallback {

    private final ToolCallback delegate;
    private final JsonMapper json;
    private final String name;

    Compacted(ToolCallback delegate, JsonMapper json) {
      this.delegate = delegate;
      this.json = json;
      this.name = delegate.getToolDefinition().name();
    }

    @Override
    public ToolDefinition getToolDefinition() {
      return delegate.getToolDefinition();
    }

    @Override
    public ToolMetadata getToolMetadata() {
      return delegate.getToolMetadata();
    }

    @Override
    public String call(String toolInput) {
      return call(toolInput, null);
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
      String result = delegate.call(toolInput, toolContext);
      if (result == null || !result.startsWith("{")) {
        return result;
      }
      try {
        return json.writeValueAsString(compact(name, json.readValue(result, MAP)));
      } catch (JacksonException e) {
        return result;
      }
    }
  }
}
