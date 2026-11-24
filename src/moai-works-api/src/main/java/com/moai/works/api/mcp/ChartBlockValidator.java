package com.moai.works.api.mcp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.TokenStreamLocation;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@code ```chart} 블록 검사 — 일감 설명 · 댓글에 MCP 로 쓴 차트가 화면에서 그려질지(일감 #217). 꼴은 화면(web-frontend)과 함께 정한
 * "```chart 블록 꼴 v1"이고 화면도 같은 규칙으로 본다: 틀리면 화면은 그림 대신 적힌 글과 까닭 한 줄, 서버는 결과 {@code checks} 에
 * {@code CHART_INVALID}(WARN). <b>쓰기는 막지 않는다</b> — 알리기만 한다.
 *
 * <ul>
 *   <li>블록 하나 = JSON 객체 하나. 엄격하게 읽는다 — 주석 · 끝 쉼표 · 작은따옴표 · 따옴표 없는 키 · NaN · 같은 키 두 번 · 객체 뒤의 다른 글은
 *       틀림(같은 키 두 번은 화면의 JSON.parse 가 받아 뒤의 값으로 그린다 — 서버만 더 엄격하다). 글자 수는 유니코드 글자(code point —
 *       화면과 같다), 블록은 {@value #MAX_CHARS} 자까지</li>
 *   <li>공통 키 type(필수) · title · height · unit, 종류마다 키가 따로 있다({@link #validate}). 모르는 키는 틀림이 아니라 무시 —
 *       {@code CHART_UNKNOWN_KEY}(INFO)로 알린다. 다른 종류에서만 쓰는 키(예: 선 차트의 horizontal)도 무시되는 키다</li>
 *   <li>선택 키에 {@code null} 을 주면 넣지 않은 것과 같다. 필수 키의 {@code null} 은 없는 것이다</li>
 *   <li>까닭에는 <b>사용자가 쓴 값을 넣지 않는다</b> — 아는 키 이름 · 자리 번호(0부터, JSON 배열 자리) · 한도만. 모르는 키 이름은 사용자 글이라
 *       {@code items} 에 한 줄로 펴서 따로 준다</li>
 * </ul>
 */
final class ChartBlockValidator {

  private static final Logger log = LoggerFactory.getLogger(ChartBlockValidator.class);

  /** 블록 하나의 최대 글자 수 */
  static final int MAX_CHARS = 20_000;

  /** 한 차트의 점 수 합계 한도 — bar · line · area 는 series 마다 data 길이의 합, scatter 는 series 점의 합 */
  static final int MAX_POINTS = 2_000;

  /** 차트 종류 — 이 차례로 알린다 */
  static final List<String> TYPES = List.of("bar", "line", "area", "pie", "donut", "scatter", "radar", "gauge", "sparkline");

  /** 같은 키 두 번 — 화면은 그리므로(JSON.parse 는 뒤의 값) 메시지가 "화면에 글이 보인다"고 하지 않는다 */
  static final String DUPLICATE = "같은 키가 두 번 나옴";

  /** 차트 블록의 언어 이름(대소문자 무시) */
  static final String LANGUAGE = "chart";

  private static final Set<String> COMMON = Set.of("type", "title", "height", "unit");
  private static final Pattern HEX = Pattern.compile("^#(?:[0-9a-fA-F]{3}|[0-9a-fA-F]{6})$");

  /** JSON 을 엄격하게 — 같은 키 두 번은 틀림(주석 · 끝 쉼표 · 작은따옴표 · NaN 은 Jackson 기본이 막는다), 겹침은 얕게(차트는 4단계면 된다) */
  private static final JsonMapper STRICT = JsonMapper.builder(JsonFactory.builder()
          .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(32).build()).build())
      .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
      // 뒤에 남은 글은 아래에서 따로 본다 — 까닭을 "JSON 이 아님" 대신 "객체 뒤에 다른 글"로 알리려고
      .disable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
      .build();

  private ChartBlockValidator() {
  }

  /**
   * 블록 하나의 검사 결과.
   *
   * @param error   틀린 까닭 한 줄(맞으면 null) — 서버 글뿐(아는 키 이름 · 자리 번호 · 한도)
   * @param ignored 무시되는 모르는 키의 자리(예: {@code foo} · {@code series[1].colour}) — 사용자 글이 섞인다
   */
  record Result(String error, List<String> ignored) {

    boolean valid() {
      return error == null;
    }
  }

  /** 검사를 멈추는 틀림 — 까닭 한 줄 */
  private static final class Invalid extends RuntimeException {
    Invalid(String reason) {
      super(reason, null, false, false);
    }
  }

  // ── 블록 하나 ────────────────────────────────────────────────────────

  /** {@code ```chart} 블록의 내용(울타리 사이 글) 하나를 본다 — 처음 만난 틀림 하나와 무시되는 키들 */
  static Result validate(String content) {
    List<String> ignored = new ArrayList<>();
    try {
      check(content == null ? "" : content, ignored);
      return new Result(null, ignored);
    } catch (Invalid e) {
      return new Result(e.getMessage(), ignored);
    }
  }

  private static void check(String content, List<String> ignored) {
    if (chars(content) > MAX_CHARS) {
      throw new Invalid("블록이 " + String.format(Locale.ROOT, "%,d", MAX_CHARS) + "자를 넘음");
    }
    if (content.isBlank()) {
      throw new Invalid("비어 있음 — JSON 객체 하나를 넣는다");
    }
    JsonNode root;
    try (JsonParser parser = STRICT.createParser(content)) {
      try {
        root = STRICT.readTree(parser);
      } catch (JacksonException e) {
        throw new Invalid(parseError(e));
      }
      // 값 하나 뒤에는 빈칸만 — 다른 글 · 둘째 값이 오면 틀림(화면의 JSON.parse 와 같다)
      try {
        if (parser.nextToken() != null) {
          throw new Invalid("JSON 객체 뒤에 다른 글이 있음" + where(parser.currentTokenLocation()));
        }
      } catch (JacksonException e) {
        throw new Invalid("JSON 객체 뒤에 다른 글이 있음" + where(e.getLocation()));
      }
    }
    if (root == null || !root.isObject()) {
      throw new Invalid("JSON 객체가 아님 — { … } 하나여야 함");
    }
    JsonNode typeNode = root.get("type");
    if (typeNode == null || typeNode.isNull()) {
      throw new Invalid("type 이(가) 없음 — " + String.join(" · ", TYPES) + " 가운데 하나");
    }
    String type = typeNode.isString() ? typeNode.stringValue() : null;
    if (type == null || !TYPES.contains(type)) {
      throw new Invalid("type 을(를) 모름 — " + String.join(" · ", TYPES) + " 가운데 하나");
    }
    Set<String> known = switch (type) {
      case "bar" -> Set.of("labels", "series", "stacked", "horizontal", "xLabel", "yLabel");
      case "line", "area" -> Set.of("labels", "series", "stacked", "xLabel", "yLabel");
      case "pie", "donut" -> Set.of("data");
      case "scatter" -> Set.of("series", "xLabel", "yLabel");
      case "radar" -> Set.of("labels", "series", "max");
      case "gauge" -> Set.of("value", "min", "max");
      default -> Set.of("data", "plot", "labels");
    };
    unknown(root, "", COMMON, known, ignored);

    // 공통
    text(root, "title", 100);
    number(root, "height", 160, 600);
    text(root, "unit", 20);

    switch (type) {
      case "bar", "line", "area" -> cartesian(root, type, ignored);
      case "pie", "donut" -> pie(root, ignored);
      case "scatter" -> scatter(root, ignored);
      case "radar" -> radar(root, ignored);
      case "gauge" -> gauge(root);
      default -> sparkline(root);
    }
  }

  // ── 종류마다 ──────────────────────────────────────────────────────────

  /** bar · line · area — labels · series(data 는 수 또는 null), 섞기(series[].type)는 bar · line 차트에서만 */
  private static void cartesian(JsonNode root, String type, List<String> ignored) {
    int labels = labels(root, "labels", 1, 200, true);
    JsonNode series = array(root, "series", 1, 12, true);
    boolean mixable = !"area".equals(type);
    long points = 0;
    for (int i = 0; i < series.size(); i++) {
      String path = "series[" + i + "]";
      JsonNode s = object(series.get(i), path);
      unknown(s, path + ".", Set.of("name", "data", "color"), mixable ? Set.of("type") : Set.of(), ignored);
      requiredText(s, path, "name", 60);
      JsonNode data = arrayOf(s, path, "data");
      if (data.size() != labels) {
        throw new Invalid(path + ".data 길이가 labels 와 다름");
      }
      for (int j = 0; j < data.size(); j++) {
        JsonNode v = data.get(j);
        if (!v.isNull()) {
          finite(v, path + ".data[" + j + "]", "수나 null(빈칸)이어야 함");
        }
      }
      points += data.size();
      if (mixable) {
        JsonNode t = s.get("type");
        if (t != null && !t.isNull() && !(t.isString() && Set.of("bar", "line").contains(t.stringValue()))) {
          throw new Invalid(path + ".type 은(는) bar · line 가운데 하나");
        }
      }
      color(s, path + ".color");
    }
    if (points > MAX_POINTS) {
      throw new Invalid("점이 모두 " + String.format(Locale.ROOT, "%,d", MAX_POINTS) + "개를 넘음(labels × series)");
    }
    bool(root, "stacked");
    if ("bar".equals(type)) {
      bool(root, "horizontal");
    }
    text(root, "xLabel", 40);
    text(root, "yLabel", 40);
  }

  /** pie · donut — data: [{label, value ≥ 0, color?}] 1~50 */
  private static void pie(JsonNode root, List<String> ignored) {
    JsonNode data = array(root, "data", 1, 50, true);
    for (int i = 0; i < data.size(); i++) {
      String path = "data[" + i + "]";
      JsonNode d = object(data.get(i), path);
      unknown(d, path + ".", Set.of("label", "value", "color"), Set.of(), ignored);
      requiredText(d, path, "label", 60);
      JsonNode v = d.get("value");
      if (v == null || v.isNull()) {
        throw new Invalid(path + ".value 이(가) 없음");
      }
      double value = finite(v, path + ".value", "수여야 함");
      if (value < 0) {
        throw new Invalid(path + ".value 는 0 이상이어야 함");
      }
      color(d, path + ".color");
    }
  }

  /** scatter — series: [{name, data: [[x, y], …] 1~1000, color?}] 1~12, 점 합계 ≤ 2,000 */
  private static void scatter(JsonNode root, List<String> ignored) {
    JsonNode series = array(root, "series", 1, 12, true);
    long points = 0;
    for (int i = 0; i < series.size(); i++) {
      String path = "series[" + i + "]";
      JsonNode s = object(series.get(i), path);
      unknown(s, path + ".", Set.of("name", "data", "color"), Set.of(), ignored);
      requiredText(s, path, "name", 60);
      JsonNode data = arrayOf(s, path, "data");
      if (data.isEmpty() || data.size() > 1000) {
        throw new Invalid(path + ".data 는 점 1~1,000개여야 함");
      }
      for (int j = 0; j < data.size(); j++) {
        String at = path + ".data[" + j + "]";
        JsonNode p = data.get(j);
        if (!p.isArray() || p.size() != 2) {
          throw new Invalid(at + " 은(는) [x, y] 수 둘이어야 함");
        }
        finite(p.get(0), at + "[0]", "수여야 함");
        finite(p.get(1), at + "[1]", "수여야 함");
      }
      points += data.size();
      color(s, path + ".color");
    }
    if (points > MAX_POINTS) {
      throw new Invalid("점이 모두 " + String.format(Locale.ROOT, "%,d", MAX_POINTS) + "개를 넘음(series 합계)");
    }
    text(root, "xLabel", 40);
    text(root, "yLabel", 40);
  }

  /** radar — labels 3~20, series 1~8(data 는 수만 — null 없음), max? */
  private static void radar(JsonNode root, List<String> ignored) {
    int labels = labels(root, "labels", 3, 20, true);
    JsonNode series = array(root, "series", 1, 8, true);
    for (int i = 0; i < series.size(); i++) {
      String path = "series[" + i + "]";
      JsonNode s = object(series.get(i), path);
      unknown(s, path + ".", Set.of("name", "data", "color"), Set.of(), ignored);
      requiredText(s, path, "name", 60);
      JsonNode data = arrayOf(s, path, "data");
      if (data.size() != labels) {
        throw new Invalid(path + ".data 길이가 labels 와 다름");
      }
      for (int j = 0; j < data.size(); j++) {
        finite(data.get(j), path + ".data[" + j + "]", "수여야 함(radar 는 null 없음)");
      }
      color(s, path + ".color");
    }
    optionalNumber(root, "max");
  }

  /** gauge — value 필수, min · max(기본 0 · 100), min < max, min ≤ value ≤ max */
  private static void gauge(JsonNode root) {
    JsonNode v = root.get("value");
    if (v == null || v.isNull()) {
      throw new Invalid("value 이(가) 없음");
    }
    double value = finite(v, "value", "수여야 함");
    Double min = optionalNumber(root, "min");
    Double max = optionalNumber(root, "max");
    double lo = min == null ? 0 : min;
    double hi = max == null ? 100 : max;
    if (lo >= hi) {
      throw new Invalid("min 은(는) max 보다 작아야 함(기본 0 · 100)");
    }
    if (value < lo || value > hi) {
      throw new Invalid("value 가 min ~ max 밖(기본 0 ~ 100)");
    }
  }

  /** sparkline — data 수 2~500(null 없음), plot line · bar, labels?(data 와 같은 길이) */
  private static void sparkline(JsonNode root) {
    JsonNode data = array(root, "data", 2, 500, true);
    for (int i = 0; i < data.size(); i++) {
      finite(data.get(i), "data[" + i + "]", "수여야 함(sparkline 은 null 없음)");
    }
    JsonNode plot = root.get("plot");
    if (plot != null && !plot.isNull() && !(plot.isString() && Set.of("line", "bar").contains(plot.stringValue()))) {
      throw new Invalid("plot 은(는) line · bar 가운데 하나");
    }
    JsonNode labels = root.get("labels");
    if (labels != null && !labels.isNull()) {
      // 화면과 같은 차례 — 배열인지 · 길이 · 글
      if (!labels.isArray()) {
        throw new Invalid("labels 은(는) 배열이어야 함");
      }
      if (labels.size() != data.size()) {
        throw new Invalid("labels 길이가 data 와 다름");
      }
      labels(root, "labels", 0, Integer.MAX_VALUE, false);
    }
  }

  // ── 칸 ──────────────────────────────────────────────────────────────

  /** 모르는 키 — 무시되는 키로 모은다(자리 + 키 이름) */
  private static void unknown(JsonNode node, String prefix, Set<String> base, Set<String> extra, List<String> ignored) {
    for (String name : node.propertyNames()) {
      if (!base.contains(name) && !extra.contains(name)) {
        ignored.add(prefix + name);
      }
    }
  }

  /** 글 배열(각 ≤ 60자) — 개수 min~max. 길이를 돌려준다 */
  private static int labels(JsonNode root, String key, int min, int max, boolean required) {
    JsonNode labels = array(root, key, min, max, required);
    for (int i = 0; i < labels.size(); i++) {
      JsonNode l = labels.get(i);
      if (!l.isString()) {
        throw new Invalid(key + "[" + i + "] 은(는) 글이어야 함");
      }
      if (chars(l.stringValue()) > 60) {
        throw new Invalid(key + "[" + i + "] 이(가) 60자를 넘음");
      }
    }
    return labels.size();
  }

  /** 배열 키 — 없으면(필수일 때) · 배열이 아니면 · 개수가 범위 밖이면 틀림 */
  private static JsonNode array(JsonNode root, String key, int min, int max, boolean required) {
    JsonNode a = root.get(key);
    if (a == null || a.isNull()) {
      if (required) {
        throw new Invalid(key + " 이(가) 없음");
      }
      return STRICT.createArrayNode();
    }
    if (!a.isArray()) {
      throw new Invalid(key + " 은(는) 배열이어야 함");
    }
    if (a.size() < min || a.size() > max) {
      throw new Invalid(key + " 은(는) " + count(min, max) + "여야 함");
    }
    return a;
  }

  /** 객체 안의 필수 배열 칸(series[].data) */
  private static JsonNode arrayOf(JsonNode node, String path, String key) {
    JsonNode a = node.get(key);
    if (a == null || a.isNull()) {
      throw new Invalid(path + "." + key + " 이(가) 없음");
    }
    if (!a.isArray()) {
      throw new Invalid(path + "." + key + " 은(는) 배열이어야 함");
    }
    return a;
  }

  private static JsonNode object(JsonNode node, String path) {
    if (node == null || !node.isObject()) {
      throw new Invalid(path + " 은(는) 객체 { … } 여야 함");
    }
    return node;
  }

  private static void requiredText(JsonNode node, String path, String key, int max) {
    JsonNode t = node.get(key);
    if (t == null || t.isNull()) {
      throw new Invalid(path + "." + key + " 이(가) 없음");
    }
    if (!t.isString()) {
      throw new Invalid(path + "." + key + " 은(는) 글이어야 함");
    }
    if (chars(t.stringValue()) > max) {
      throw new Invalid(path + "." + key + " 이(가) " + max + "자를 넘음");
    }
  }

  /** 선택 글 키 — 있으면 글 · max 자 이하 */
  private static void text(JsonNode root, String key, int max) {
    JsonNode t = root.get(key);
    if (t == null || t.isNull()) {
      return;
    }
    if (!t.isString()) {
      throw new Invalid(key + " 은(는) 글이어야 함");
    }
    if (chars(t.stringValue()) > max) {
      throw new Invalid(key + " 이(가) " + max + "자를 넘음");
    }
  }

  /** 선택 수 키 — 있으면 유한한 수 · 범위 안 */
  private static void number(JsonNode root, String key, double min, double max) {
    Double v = optionalNumber(root, key);
    if (v != null && (v < min || v > max)) {
      throw new Invalid(key + " 은(는) " + plain(min) + "~" + plain(max) + " 사이여야 함");
    }
  }

  private static Double optionalNumber(JsonNode root, String key) {
    JsonNode v = root.get(key);
    if (v == null || v.isNull()) {
      return null;
    }
    return finite(v, key, "수여야 함");
  }

  private static void bool(JsonNode root, String key) {
    JsonNode b = root.get(key);
    if (b != null && !b.isNull() && !b.isBoolean()) {
      throw new Invalid(key + " 은(는) true · false 여야 함");
    }
  }

  /** 색 — #rgb · #rrggbb 만(CSS 이름 · rgb() · url() 은 틀림) */
  private static void color(JsonNode node, String path) {
    int dot = path.lastIndexOf('.');
    JsonNode c = node.get(path.substring(dot + 1));
    if (c == null || c.isNull()) {
      return;
    }
    if (!c.isString() || !HEX.matcher(c.stringValue()).matches()) {
      throw new Invalid(path + " 색 꼴이 틀림 — #rgb · #rrggbb 만");
    }
  }

  /** 유한한 JSON 수 — 글 "5" · true · 1e400(무한) 은 틀림. must = 틀렸을 때의 말(예: "수여야 함") */
  private static double finite(JsonNode v, String path, String must) {
    if (v == null || !v.isNumber()) {
      throw new Invalid(path + " 은(는) " + must);
    }
    double d = v.numberValue().doubleValue();
    if (!Double.isFinite(d)) {
      throw new Invalid(path + " 이(가) 너무 큼 — 유한한 수여야 함");
    }
    return d;
  }

  /** 글자 수 — 유니코드 글자(code point). 화면(chart-spec.ts 의 Array.from(text).length)과 같다 */
  static int chars(String text) {
    return text.codePointCount(0, text.length());
  }

  private static String count(int min, int max) {
    return max == Integer.MAX_VALUE ? min + "개 이상" : String.format(Locale.ROOT, "%,d~%,d개", min, max);
  }

  private static String plain(double d) {
    return d == Math.rint(d) ? String.valueOf((long) d) : String.valueOf(d);
  }

  /** JSON 을 읽지 못한 까닭 — 자리(블록 안 줄 · 칸)만, 읽은 글은 넣지 않는다(Jackson 의 문구는 읽은 글을 품어 쓰지 않는다) */
  private static String parseError(JacksonException e) {
    String original = e.getOriginalMessage() == null ? "" : e.getOriginalMessage();
    if (original.startsWith("Duplicate")) {
      return DUPLICATE + where(e.getLocation());
    }
    return "JSON 이 아님" + where(e.getLocation()) + " — 주석 · 끝 쉼표 · 작은따옴표 · 따옴표 없는 키 · NaN 은 쓸 수 없음";
  }

  private static String where(TokenStreamLocation at) {
    return at == null || at.getLineNr() < 1 ? "" : "(블록 안 " + at.getLineNr() + "줄 " + Math.max(at.getColumnNr(), 1) + "칸 근처)";
  }

  // ── 글 전체 → checks ─────────────────────────────────────────────────

  /** 글 하나에서 알릴 checks 줄의 최대 수 — 넘치면 마지막 한 줄로 남은 수를 알린다 */
  static final int CHECKS_MAX = 5;

  /** CHART_UNKNOWN_KEY 의 items 최대 수 · 글자 수 */
  private static final int ITEMS_MAX = 5;
  private static final int ITEM_MAX = 60;

  /**
   * MCP 로 쓴 글 하나의 {@code ```chart} 블록마다 검사해 결과 {@code checks} 줄로 — 틀리면 {@code CHART_INVALID}(WARN), 무시되는 키가 있으면
   * {@code CHART_UNKNOWN_KEY}(INFO). 글에 차트 블록이 없으면 빈 목록.
   *
   * @param where    글의 자리 — 메시지의 "(where L줄)"(예: 본문 · setSection · 댓글). 서버 글
   * @param markdown MCP 로 쓴 글(null 이면 빈 목록)
   */
  static List<Map<String, Object>> checks(String where, String markdown) {
    if (markdown == null || markdown.isEmpty() || !markdown.toLowerCase(Locale.ROOT).contains(LANGUAGE)) {
      return new ArrayList<>();
    }
    try {
      return scan(where, markdown);
    } catch (RuntimeException e) {
      // 검사는 쓰기를 막지 않는다 — 셈하다 틀려도(버그) 쓴 결과는 그대로 돌려주고 서버 로그에 남긴다(IssueGuide 와 같다)
      log.error("[mcp] {} 의 차트 블록을 검사하지 못했다 — checks 없이 준다", where, e);
      return new ArrayList<>();
    }
  }

  private static List<Map<String, Object>> scan(String where, String markdown) {
    List<Map<String, Object>> out = new ArrayList<>();
    int n = 0;
    int skippedInvalid = 0;
    int skippedUnknown = 0;
    for (MarkdownFences.Fence fence : MarkdownFences.of(markdown)) {
      if (!LANGUAGE.equals(fence.language())) {
        continue;
      }
      n++;
      Result r = validate(fence.content());
      String head = n + " 번째 차트 블록(" + where + " " + fence.line() + "줄): ";
      if (!r.valid()) {
        if (out.size() < CHECKS_MAX) {
          String screen = r.error().startsWith(DUPLICATE) ? " — 화면은 뒤의 값으로 그리지만 어느 값을 뜻했는지 모호하다. "
              : " — 화면에는 차트 대신 적힌 글과 까닭이 보인다. ";
          out.add(check("CHART_INVALID", IssueGuide.WARN, head + r.error() + screen + "꼴은 get_markdown_guide(topic=chart).", null));
        } else {
          skippedInvalid++;
        }
      }
      if (!r.ignored().isEmpty()) {
        if (out.size() < CHECKS_MAX) {
          out.add(check("CHART_UNKNOWN_KEY", IssueGuide.INFO, head + "모르는 키 " + r.ignored().size() + "개(items)는 무시된다 — "
              + "오타면 고친다. 꼴은 get_markdown_guide(topic=chart).", items(r.ignored())));
        } else {
          skippedUnknown++;
        }
      }
    }
    if (skippedInvalid > 0) {
      out.add(check("CHART_INVALID", IssueGuide.WARN, where + "에 틀린 차트 블록이 " + skippedInvalid + "개 더 있다 — 앞의 것을 고친 뒤 "
          + "다른 차트 블록도 get_markdown_guide(topic=chart) 의 꼴과 견준다.", null));
    } else if (skippedUnknown > 0) {
      out.add(check("CHART_UNKNOWN_KEY", IssueGuide.INFO, where + "에 모르는 키가 있는 차트 블록이 " + skippedUnknown + "개 더 있다.", null));
    }
    return out;
  }

  private static Map<String, Object> check(String code, String level, String message, List<String> items) {
    Map<String, Object> c = new LinkedHashMap<>();
    c.put("code", code);
    c.put("level", level);
    c.put("message", message);
    if (items != null) {
      c.put("items", items);
    }
    return c;
  }

  /** 모르는 키 자리 — 사용자 글이라 한 줄로 펴서 자른다(IssueGuide 의 items 와 같다) */
  private static List<String> items(List<String> paths) {
    List<String> out = new ArrayList<>();
    for (String p : paths) {
      if (out.size() == ITEMS_MAX) {
        out.add("외 " + (paths.size() - ITEMS_MAX) + "개");
        break;
      }
      out.add(McpInstructions.oneLine(p, ITEM_MAX));
    }
    return out;
  }
}
