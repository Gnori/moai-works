package com.moai.works.api.mcp;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.execution.ToolExecutionException;
import org.springframework.ai.tool.metadata.ToolMetadata;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * MCP 도구 인자의 꼴 — 도구 메서드에 넘기기 전에 고르게 풀고, 틀리면 다른 도구 오류와 같은
 * {@code {"error": {"status": 400, "code": "INVALID_ARGUMENT", "message", "field"}}} 로 돌려준다(일감 #177).
 *
 * <p>왜 여기서 하나 — MCP Java SDK(2.2)는 도구를 부르기 전에 입력 틀(JSON Schema)로 인자를 보고, 틀리면
 * {@code "Tool (x) input validation failed: …"} 라는 영어 평문을 {@code isError} 결과로 돌려준다. 도구까지 오지 않아
 * {@link McpScope#run} 의 오류 모양을 쓸 수 없고, Agent 클라이언트가 결과를 JSON 으로 읽다가 깨진다. 그래서
 * <ul>
 *   <li>SDK 의 입력 검사는 끈다({@code validateToolInputs(false)}, {@link McpServerConfiguration})</li>
 *   <li>Spring AI 가 {@code @Tool} 메서드에서 만든 {@link ToolCallback} 을 이 클래스가 감싼다 — 입력 틀은 Spring AI 가 만든 것
 *       (모양: properties 마다 type string · integer · number · boolean · array(items string), required, additionalProperties false)을
 *       그대로 정본으로 쓰고, 아래 두 가지만 넓혀 알린다</li>
 *   <li>틀은 SDK 나 다른 라이브러리의 검사기가 아니라 여기서 본다 — 오류마다 어느 인자인지(field)와 한국어 문구를 주려면 그래야 하고,
 *       Spring AI 가 만드는 틀은 위 모양뿐이라 그것만 보면 된다. 모르는 모양의 칸은 그대로 넘긴다</li>
 * </ul>
 *
 * <p><b>같은 뜻이면 같은 꼴</b>(넓혀 알리는 것 — tools/list 의 입력 틀이 anyOf 로 둘 다 보인다):
 * <ul>
 *   <li>일감 번호 {@code number} · {@code parent} · {@code target} — 정수 {@code 12} 도, 숫자 글 {@code "12"} · {@code "#12"} 도 된다.
 *       글로 받는 칸({@code search_issues.parent} — {@code top} 도 받는다)에 정수를 주면 글로 바꾼다</li>
 *   <li>여러 값 — 쉼표 글({@code "NEW,IN_PROGRESS"})도 글 배열({@code ["NEW","IN_PROGRESS"]})도 된다: {@link #LISTS} 의 글 칸은 배열을
 *       쉼표로 이어 붙이고, 글 배열 칸({@code modules})은 쉼표 글을 나눈다. 빈 배열 · 빈 글은 넣지 않은 것과 같다</li>
 *   <li>한 값이 글 한 줄인 배열({@link #WHOLE_ITEMS} — {@code update_issue} 의 {@code checkItems} · {@code uncheckItems})은 글 하나도 받지만
 *       쉼표로 나누지 않는다(체크 목록 글에 쉼표가 들어 있을 수 있다)</li>
 *   <li>객체 배열 칸({@code update_issue.moveItems})은 객체 하나도 받아 그 한 값의 배열로 — 배열의 값은 모두 객체여야 한다(일감 #199)</li>
 *   <li>선택 인자에 {@code null} 을 주면 넣지 않은 것과 같다</li>
 * </ul>
 * 그 밖의 칸은 틀 그대로다(정수 칸에 글이면 INVALID_ARGUMENT). 정수는 int 범위({@link #LONGS} 만 long). 모르는 인자 · 빠진 필수 인자도
 * INVALID_ARGUMENT 다.
 *
 * <p>도구 메서드가 {@link com.moai.works.api.common.ApiException} 밖의 예외를 던지면(버그) 평문 대신 500 {@code INTERNAL_ERROR} 로
 * 돌려주고 서버 로그에 남긴다 — 접근 기록은 오류로 남고 도구의 쓰기는 되돌린다({@code McpAccessRecorder}).
 */
final class McpToolArguments {

  private static final Logger log = LoggerFactory.getLogger(McpToolArguments.class);
  private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
  };

  /** 일감 번호 칸 — 어느 도구에서든 정수와 숫자 글을 함께 받는다 */
  static final Set<String> ISSUE_NUMBERS = Set.of("number", "parent", "target");

  /** 쉼표로 여러 값을 받는 글 칸 — 도구마다. 글 배열도 받는다 */
  static final Map<String, Set<String>> LISTS = Map.of(
      "search_issues", Set.of("project", "status", "type", "module", "priority"),
      "update_issue", Set.of("clear"));

  /** long 인 정수 칸 — 나머지 정수 칸은 int */
  static final Map<String, Set<String>> LONGS = Map.of("request_attachment_upload", Set.of("size"));

  /** 글 배열 칸인데 쉼표로 나누지 않는 것 — 값 하나가 글 한 줄이라 쉼표를 품을 수 있다. 글 하나를 주면 그 한 값의 배열(일감 #178) */
  static final Map<String, Set<String>> WHOLE_ITEMS = Map.of("update_issue", Set.of("checkItems", "uncheckItems"));

  private static final Pattern ISSUE_NUMBER_TEXT = Pattern.compile("^\\s*#?\\s*(\\d{1,9})\\s*$");
  private static final Object ABSENT = new Object();

  private McpToolArguments() {
  }

  /** 입구 하나의 도구들을 감싼다. {@link #LISTS} · {@link #LONGS} 에 적은 칸이 그 도구에 없으면 기동을 멈춘다(이름이 바뀌어 조용히 빠지지 않게) */
  static List<ToolCallback> guard(ToolCallback[] callbacks, JsonMapper json) {
    List<ToolCallback> out = new ArrayList<>();
    for (ToolCallback c : callbacks) {
      out.add(new Guarded(c, json));
    }
    return out;
  }

  // ── 칸 ──────────────────────────────────────────────────────────────

  private enum Kind {
    /** 일감 번호(정수 칸) — 숫자 글도 받는다 */
    ISSUE_NUMBER_INT,
    /** 일감 번호(글 칸) — 정수도 받아 글로 */
    ISSUE_NUMBER_TEXT,
    /** 쉼표 글 칸 — 글 배열도 받아 쉼표로 */
    LIST_TEXT,
    /** 글 배열 칸 — 쉼표 글도 받아 나눈다 */
    LIST_ARRAY,
    /** 글 배열 칸 — 글 하나도 받아 그 한 값의 배열로(나누지 않는다) */
    ITEM_ARRAY,
    /** 객체 칸 — JSON 객체여야 한다. 안은 도구가 본다 */
    OBJECT,
    /** 객체 배열 칸 — 객체 하나도 받아 그 한 값의 배열로. 안은 도구가 본다 */
    OBJECT_ARRAY,
    STRING,
    INTEGER,
    LONG,
    NUMBER,
    BOOLEAN,
    /** 모르는 모양 — 보지 않고 넘긴다 */
    OTHER
  }

  private record Param(String name, Kind kind, boolean required) {
  }

  /** 도구 하나의 칸들과 알릴 입력 틀 */
  private record Shape(Map<String, Param> params, String advertised) {
  }

  private static Shape shape(ToolDefinition def, JsonMapper json) {
    String tool = def.name();
    Map<String, Object> schema = json.readValue(def.inputSchema(), MAP);
    Map<String, Object> props = schema.get("properties") instanceof Map<?, ?> p ? cast(p) : new LinkedHashMap<>();
    Set<String> required = new LinkedHashSet<>();
    if (schema.get("required") instanceof List<?> r) {
      r.forEach(x -> required.add(String.valueOf(x)));
    }
    Set<String> lists = LISTS.getOrDefault(tool, Set.of());
    Set<String> longs = LONGS.getOrDefault(tool, Set.of());
    Set<String> wholeItems = WHOLE_ITEMS.getOrDefault(tool, Set.of());
    for (String name : lists) {
      if (!(props.get(name) instanceof Map<?, ?> ps) || !"string".equals(ps.get("type"))) {
        throw new IllegalStateException("MCP 도구 " + tool + " 에 쉼표 글 칸 " + name + " 이(가) 없다 — McpToolArguments.LISTS 를 고친다");
      }
    }
    for (String name : wholeItems) {
      if (!(props.get(name) instanceof Map<?, ?> ps) || !"array".equals(ps.get("type"))) {
        throw new IllegalStateException("MCP 도구 " + tool + " 에 글 배열 칸 " + name + " 이(가) 없다 — McpToolArguments.WHOLE_ITEMS 를 고친다");
      }
    }
    for (String name : longs) {
      if (!(props.get(name) instanceof Map<?, ?> ps) || !"integer".equals(ps.get("type"))) {
        throw new IllegalStateException("MCP 도구 " + tool + " 에 정수 칸 " + name + " 이(가) 없다 — McpToolArguments.LONGS 를 고친다");
      }
    }
    Map<String, Param> params = new LinkedHashMap<>();
    Map<String, Object> advertisedProps = new LinkedHashMap<>();
    for (Map.Entry<String, Object> e : props.entrySet()) {
      String name = e.getKey();
      Map<String, Object> ps = e.getValue() instanceof Map<?, ?> m ? cast(m) : Map.of();
      Object type = ps.get("type");
      Object itemType = ps.get("items") instanceof Map<?, ?> items ? items.get("type") : null;
      Kind kind;
      if (ISSUE_NUMBERS.contains(name) && "integer".equals(type)) {
        kind = Kind.ISSUE_NUMBER_INT;
      } else if (ISSUE_NUMBERS.contains(name) && "string".equals(type)) {
        kind = Kind.ISSUE_NUMBER_TEXT;
      } else if (lists.contains(name)) {
        kind = Kind.LIST_TEXT;
      } else if ("array".equals(type) && "string".equals(itemType)) {
        kind = wholeItems.contains(name) ? Kind.ITEM_ARRAY : Kind.LIST_ARRAY;
      } else if ("array".equals(type) && "object".equals(itemType)) {
        kind = Kind.OBJECT_ARRAY;
      } else if (type instanceof String t) {
        kind = switch (t) {
          case "string" -> Kind.STRING;
          case "integer" -> longs.contains(name) ? Kind.LONG : Kind.INTEGER;
          case "number" -> Kind.NUMBER;
          case "boolean" -> Kind.BOOLEAN;
          case "object" -> Kind.OBJECT;
          default -> Kind.OTHER;
        };
      } else {
        kind = Kind.OTHER;
      }
      params.put(name, new Param(name, kind, required.contains(name)));
      advertisedProps.put(name, advertise(kind, ps));
    }
    Map<String, Object> advertised = new LinkedHashMap<>(schema);
    // $schema(2020-12 주소)는 MCP 의 기본 방언이라 알리지 않는다 — 도구마다 같은 60자가 tools/list 에 되풀이됐다(일감 #282)
    advertised.remove("$schema");
    advertised.put("properties", advertisedProps);
    return new Shape(params, json.writeValueAsString(advertised));
  }

  /** 넓힌 칸은 anyOf 로 두 꼴을 함께 알린다 — 정본 꼴이 먼저 */
  private static Object advertise(Kind kind, Map<String, Object> ps) {
    Object description = ps.get("description");
    Map<String, Object> out = new LinkedHashMap<>();
    switch (kind) {
      case ISSUE_NUMBER_INT, ISSUE_NUMBER_TEXT -> {
        Map<String, Object> integer = Map.of("type", "integer");
        Map<String, Object> text = Map.of("type", "string");
        out.put("anyOf", kind == Kind.ISSUE_NUMBER_INT ? List.of(integer, text) : List.of(text, integer));
        out.put("description", suffix(description, "12 · \"#12\" 모두 된다"));
      }
      case LIST_TEXT, LIST_ARRAY -> {
        Map<String, Object> text = Map.of("type", "string");
        Map<String, Object> array = Map.of("type", "array", "items", Map.of("type", "string"));
        out.put("anyOf", kind == Kind.LIST_TEXT ? List.of(text, array) : List.of(array, text));
        out.put("description", suffix(description, "쉼표 글 · 글 배열 모두 된다"));
      }
      case ITEM_ARRAY -> {
        out.put("anyOf", List.of(Map.of("type", "array", "items", Map.of("type", "string")), Map.of("type", "string")));
        out.put("description", suffix(description, "글 배열이나 글 하나 — 쉼표로 나누지 않는다"));
      }
      case OBJECT_ARRAY -> {
        Object items = ps.get("items");
        out.put("anyOf", List.of(Map.of("type", "array", "items", items), items));
        out.put("description", suffix(description, "객체 배열, 또는 객체 하나"));
      }
      default -> {
        return ps;
      }
    }
    return out;
  }

  private static String suffix(Object description, String note) {
    return description == null ? note : description + " (" + note + ")";
  }

  // ── 풀기 ────────────────────────────────────────────────────────────

  /** 인자 꼴이 틀렸다 — 어느 칸인지와 한국어 문구 */
  static final class Invalid extends RuntimeException {
    private final String field;

    Invalid(String field, String message) {
      super(message, null, false, false);
      this.field = field;
    }

    String field() {
      return field;
    }
  }

  /** 받은 인자를 고르게 푼다 — 틀리면 {@link Invalid}. 결과는 도구 메서드에 넘길 인자 */
  private static Map<String, Object> normalize(Shape shape, Map<String, Object> args) {
    Map<String, Object> out = new LinkedHashMap<>();
    for (Map.Entry<String, Object> e : args.entrySet()) {
      String name = e.getKey();
      Param p = shape.params().get(name);
      if (p == null) {
        throw new Invalid(name, "모르는 인자입니다: " + name + " — 이 도구의 인자는 "
            + (shape.params().isEmpty() ? "없습니다" : String.join(" · ", shape.params().keySet())) + ".");
      }
      Object v = e.getValue() == null ? ABSENT : value(p, e.getValue());
      if (v != ABSENT) {
        out.put(name, v);
      }
    }
    for (Param p : shape.params().values()) {
      if (p.required() && !out.containsKey(p.name())) {
        throw new Invalid(p.name(), "필수 인자 " + p.name() + " 이(가) 없습니다.");
      }
    }
    return out;
  }

  private static Object value(Param p, Object v) {
    String name = p.name();
    return switch (p.kind()) {
      case STRING -> {
        if (v instanceof String s) {
          yield s;
        }
        throw new Invalid(name, name + " 은(는) 글(문자열)이어야 합니다.");
      }
      case INTEGER -> integer(name, v, Integer.MIN_VALUE, Integer.MAX_VALUE);
      case LONG -> integer(name, v, Long.MIN_VALUE, Long.MAX_VALUE);
      case NUMBER -> {
        if (v instanceof Number) {
          yield v;
        }
        throw new Invalid(name, name + " 은(는) 숫자여야 합니다(예: 1.5).");
      }
      case BOOLEAN -> {
        if (v instanceof Boolean) {
          yield v;
        }
        throw new Invalid(name, name + " 은(는) true 나 false 여야 합니다.");
      }
      case ISSUE_NUMBER_INT -> {
        if (v instanceof String s) {
          Matcher m = ISSUE_NUMBER_TEXT.matcher(s);
          if (m.matches()) {
            yield Integer.valueOf(m.group(1));
          }
          throw new Invalid(name, name + " 은(는) 일감 번호여야 합니다 — 정수 12 나 글 \"12\" · \"#12\": " + cut(s));
        }
        if (isIntegral(v)) {
          yield integer(name, v, Integer.MIN_VALUE, Integer.MAX_VALUE);
        }
        throw new Invalid(name, name + " 은(는) 일감 번호여야 합니다 — 정수 12 나 글 \"12\" · \"#12\".");
      }
      case ISSUE_NUMBER_TEXT -> {
        if (v instanceof String s) {
          yield s;
        }
        if (isIntegral(v)) {
          yield String.valueOf(((Number) integer(name, v, Integer.MIN_VALUE, Integer.MAX_VALUE)).intValue());
        }
        throw new Invalid(name, name + " 은(는) 일감 번호(정수 12 · 글 \"#12\")여야 합니다.");
      }
      case LIST_TEXT -> {
        if (v instanceof String s) {
          yield s.isBlank() ? ABSENT : s;
        }
        if (v instanceof List<?> list) {
          List<String> items = texts(name, list);
          yield items.isEmpty() ? ABSENT : String.join(",", items);
        }
        throw new Invalid(name, name + " 은(는) 쉼표로 나눈 글(\"a,b\")이나 글 배열([\"a\",\"b\"])이어야 합니다.");
      }
      case LIST_ARRAY -> {
        if (v instanceof List<?> list) {
          yield texts(name, list);
        }
        if (v instanceof String s) {
          List<String> items = Arrays.stream(s.split(",")).map(String::trim).filter(x -> !x.isEmpty()).toList();
          yield items.isEmpty() ? ABSENT : items;
        }
        throw new Invalid(name, name + " 은(는) 글 배열([\"a\",\"b\"])이나 쉼표로 나눈 글(\"a,b\")이어야 합니다.");
      }
      case ITEM_ARRAY -> {
        if (v instanceof List<?> list) {
          yield texts(name, list);
        }
        if (v instanceof String s) {
          yield s.isBlank() ? ABSENT : List.of(s.strip());
        }
        throw new Invalid(name, name + " 은(는) 글 배열([\"항목 글\"])이나 글 하나여야 합니다.");
      }
      case OBJECT -> {
        if (v instanceof Map<?, ?>) {
          yield v;
        }
        throw new Invalid(name, name + " 은(는) JSON 객체여야 합니다(도구 설명의 예를 보십시오).");
      }
      case OBJECT_ARRAY -> {
        if (v instanceof Map<?, ?>) {
          yield List.of(v);
        }
        if (v instanceof List<?> list) {
          if (list.stream().anyMatch(x -> !(x instanceof Map<?, ?>))) {
            throw new Invalid(name, name + " 배열의 값은 모두 JSON 객체여야 합니다(도구 설명의 예를 보십시오).");
          }
          yield list.isEmpty() ? ABSENT : list;
        }
        throw new Invalid(name, name + " 은(는) JSON 객체 배열이나 객체 하나여야 합니다(도구 설명의 예를 보십시오).");
      }
      case OTHER -> v;
    };
  }

  private static List<String> texts(String name, List<?> list) {
    List<String> out = new ArrayList<>();
    for (Object item : list) {
      if (!(item instanceof String s)) {
        throw new Invalid(name, name + " 배열의 값은 모두 글(문자열)이어야 합니다.");
      }
      if (!s.isBlank()) {
        out.add(s.trim());
      }
    }
    return out;
  }

  private static boolean isIntegral(Object v) {
    if (v instanceof Integer || v instanceof Long || v instanceof Short || v instanceof Byte || v instanceof BigInteger) {
      return true;
    }
    if (v instanceof Double || v instanceof Float || v instanceof BigDecimal) {
      try {
        new BigDecimal(v.toString()).toBigIntegerExact();
        return true;
      } catch (ArithmeticException | NumberFormatException e) {
        return false;
      }
    }
    return false;
  }

  /** 정수 칸 — JSON 수로 온 정수만(12.0 도 된다), 범위 안에서 */
  private static Object integer(String name, Object v, long min, long max) {
    if (!isIntegral(v)) {
      throw new Invalid(name, name + " 은(는) 정수여야 합니다(예: 3).");
    }
    BigInteger n = new BigDecimal(v.toString()).toBigIntegerExact();
    if (n.compareTo(BigInteger.valueOf(min)) < 0 || n.compareTo(BigInteger.valueOf(max)) > 0) {
      throw new Invalid(name, name + " 의 값이 너무 큽니다(또는 작습니다): " + n);
    }
    return max == Long.MAX_VALUE ? (Object) n.longValue() : (Object) n.intValue();
  }

  private static String cut(String s) {
    return s.length() > 40 ? s.substring(0, 40) + "…" : s;
  }

  @SuppressWarnings("unchecked")
  private static Map<String, Object> cast(Map<?, ?> m) {
    return new LinkedHashMap<>((Map<String, Object>) m);
  }

  // ── 감싼 도구 ──────────────────────────────────────────────────────────

  /** 인자를 풀어 넘기고, 틀리면 · 도구가 예외로 끝나면 오류 모양으로 돌려준다 */
  private static final class Guarded implements ToolCallback {

    private final ToolCallback delegate;
    private final JsonMapper json;
    private final Shape shape;
    private final ToolDefinition definition;

    Guarded(ToolCallback delegate, JsonMapper json) {
      this.delegate = delegate;
      this.json = json;
      ToolDefinition def = delegate.getToolDefinition();
      this.shape = shape(def, json);
      this.definition = ToolDefinition.builder().name(def.name()).description(def.description())
          .inputSchema(shape.advertised()).build();
    }

    @Override
    public ToolDefinition getToolDefinition() {
      return definition;
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
      String normalized;
      try {
        normalized = json.writeValueAsString(normalize(shape, parse(toolInput)));
      } catch (Invalid e) {
        return error(400, "INVALID_ARGUMENT", e.getMessage(), e.field());
      }
      try {
        return delegate.call(normalized, toolContext);
      } catch (ToolExecutionException e) {
        if (e.getCause() instanceof JacksonException) {
          return error(400, "INVALID_ARGUMENT", "인자를 읽을 수 없습니다 — 도구 설명의 꼴로 넣으십시오.", null);
        }
        // 도구 메서드가 업무 오류(ApiException — McpScope.run 이 받는다) 밖의 예외로 끝났다: 버그다
        log.error("[mcp] 도구 {} 가 예외로 끝났다", definition.name(), e.getCause() == null ? e : e.getCause());
        return error(500, "INTERNAL_ERROR", "도구를 처리하지 못했습니다 — 잠시 뒤 다시 하고, 되풀이되면 관리자에게 알리십시오.", null);
      } catch (RuntimeException e) {
        // 인자를 메서드 꼴로 바꾸지 못했다(위에서 본 틀 밖의 경우)
        log.warn("[mcp] 도구 {} 의 인자를 풀지 못했다: {}", definition.name(), e.getClass().getSimpleName());
        return error(400, "INVALID_ARGUMENT", "인자를 읽을 수 없습니다 — 도구 설명의 꼴로 넣으십시오.", null);
      }
    }

    private Map<String, Object> parse(String toolInput) {
      if (toolInput == null || toolInput.isBlank() || "null".equals(toolInput.trim())) {
        return new LinkedHashMap<>();
      }
      try {
        return json.readValue(toolInput, MAP);
      } catch (JacksonException e) {
        throw new Invalid(null, "인자는 JSON 객체여야 합니다.");
      }
    }

    private String error(int status, String code, String message, String field) {
      Map<String, Object> error = new LinkedHashMap<>();
      error.put("status", status);
      error.put("code", code);
      error.put("message", message);
      if (field != null) {
        error.put("field", field);
      }
      return json.writeValueAsString(Map.of("error", error));
    }
  }
}
