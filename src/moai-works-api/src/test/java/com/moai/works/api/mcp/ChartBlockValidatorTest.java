package com.moai.works.api.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

/**
 * ```chart 블록 꼴 v1 검사 — 화면과 같은 규칙(일감 #217). 종류마다 맞음 · 틀림, 한도, 색, null 빈칸, 모르는 키, 객체가 아닌 JSON, 엄격한 JSON,
 * 큰 글, 메시지에 사용자 값이 없음. DB 없이.
 */
class ChartBlockValidatorTest {

  private static ChartBlockValidator.Result v(String json) {
    return ChartBlockValidator.validate(json);
  }

  private static void ok(String json) {
    ChartBlockValidator.Result r = v(json);
    assertThat(r.error()).as(json).isNull();
    assertThat(r.valid()).isTrue();
  }

  private static void bad(String json, String reason) {
    ChartBlockValidator.Result r = v(json);
    assertThat(r.valid()).as(json).isFalse();
    assertThat(r.error()).as(json).contains(reason);
  }

  private static String nums(int n) {
    return IntStream.range(0, n).mapToObj(String::valueOf).collect(Collectors.joining(",", "[", "]"));
  }

  private static String strs(int n) {
    return IntStream.range(0, n).mapToObj(i -> "\"l" + i + "\"").collect(Collectors.joining(",", "[", "]"));
  }

  // ── 종류마다 맞는 것 ────────────────────────────────────────────────

  @Test
  void 종류마다_가장_작은_예와_약속의_예는_맞다() {
    ok("{\"type\":\"bar\",\"labels\":[\"월\"],\"series\":[{\"name\":\"완료\",\"data\":[3]}]}");
    ok("{\"type\":\"line\",\"labels\":[\"a\",\"b\"],\"series\":[{\"name\":\"s\",\"data\":[1,null]}]}");
    ok("{\"type\":\"area\",\"labels\":[\"a\"],\"series\":[{\"name\":\"s\",\"data\":[1.5]}],\"stacked\":true}");
    ok("{\"type\":\"pie\",\"data\":[{\"label\":\"버그\",\"value\":0}]}");
    ok("{\"type\":\"donut\",\"data\":[{\"label\":\"a\",\"value\":2,\"color\":\"#ABC\"}]}");
    ok("{\"type\":\"scatter\",\"series\":[{\"name\":\"s\",\"data\":[[1,2],[-3.5,4e2]]}]}");
    ok("{\"type\":\"radar\",\"labels\":[\"a\",\"b\",\"c\"],\"series\":[{\"name\":\"s\",\"data\":[1,2,3]}],\"max\":5}");
    ok("{\"type\":\"gauge\",\"value\":63}");
    ok("{\"type\":\"sparkline\",\"data\":[1,2]}");
    // 약속 문서의 예 셋
    ok("{\"type\":\"bar\",\"title\":\"주간 처리 일감\",\"labels\":[\"월\",\"화\",\"수\",\"목\",\"금\"],\"series\":[{\"name\":\"완료\","
        + "\"data\":[3,5,2,6,4]},{\"name\":\"새로 등록\",\"data\":[4,2,3,3,5],\"type\":\"line\"}],\"unit\":\"건\"}");
    ok("{\"type\":\"donut\",\"title\":\"유형별\",\"data\":[{\"label\":\"버그\",\"value\":12},{\"label\":\"새기능\",\"value\":20},"
        + "{\"label\":\"테스트\",\"value\":7}]}");
    ok("{\"type\":\"gauge\",\"title\":\"수용 기준 진행\",\"value\":63,\"max\":100,\"unit\":\"%\"}");
    // 여러 줄 · 앞뒤 빈칸
    ok("\n  {\n    \"type\": \"gauge\",\n    \"value\": 1\n  }\n");
  }

  @Test
  void 안내_도구의_예는_모두_맞다() throws Exception {
    @SuppressWarnings("unchecked")
    Map<String, Object> chart = (Map<String, Object>) MarkdownMcpTools.guide("chart").get("chart");
    @SuppressWarnings("unchecked")
    Map<String, Object> examples = (Map<String, Object>) chart.get("examples");
    assertThat(examples.keySet()).containsExactlyElementsOf(ChartBlockValidator.TYPES);
    tools.jackson.databind.json.JsonMapper json = new tools.jackson.databind.json.JsonMapper();
    for (Map.Entry<String, Object> e : examples.entrySet()) {
      String text = json.writeValueAsString(e.getValue());
      assertThat(text).startsWith("{\"type\":\"" + e.getKey() + "\"");
      ChartBlockValidator.Result r = v(text);
      assertThat(r.error()).as(e.getKey()).isNull();
      assertThat(r.ignored()).as(e.getKey()).isEmpty();
    }
  }

  @Test
  void 섞기_가로_막대_축_이름과_선택_키의_null() {
    ok("{\"type\":\"bar\",\"horizontal\":true,\"stacked\":false,\"xLabel\":\"x\",\"yLabel\":\"y\",\"labels\":[\"a\"],"
        + "\"series\":[{\"name\":\"s\",\"data\":[1],\"type\":\"bar\",\"color\":\"#123456\"},{\"name\":\"t\",\"data\":[2],\"type\":\"line\"}]}");
    ok("{\"type\":\"line\",\"labels\":[\"a\"],\"series\":[{\"name\":\"s\",\"data\":[1],\"type\":\"bar\"}]}");
    ok("{\"type\":\"gauge\",\"value\":5,\"title\":null,\"height\":null,\"unit\":null,\"min\":null}");
    ok("{\"type\":\"bar\",\"height\":160,\"labels\":[\"a\"],\"series\":[{\"name\":\"s\",\"data\":[1],\"color\":null}]}");
    ok("{\"type\":\"bar\",\"height\":600.0,\"labels\":[\"a\"],\"series\":[{\"name\":\"\",\"data\":[1]}]}");
  }

  // ── 종류마다 틀린 것 ────────────────────────────────────────────────

  @Test
  void type_이_없거나_모르거나_글이_아니면_틀림() {
    bad("{}", "type 이(가) 없음");
    bad("{\"type\":null}", "type 이(가) 없음");
    bad("{\"type\":\"histogram\"}", "type 을(를) 모름");
    bad("{\"type\":\"Bar\"}", "type 을(를) 모름");
    bad("{\"type\":1}", "type 을(를) 모름");
  }

  @Test
  void bar_line_area_의_틀림() {
    bad("{\"type\":\"bar\",\"series\":[{\"name\":\"s\",\"data\":[1]}]}", "labels 이(가) 없음");
    bad("{\"type\":\"bar\",\"labels\":\"a\",\"series\":[]}", "labels 은(는) 배열이어야 함");
    bad("{\"type\":\"bar\",\"labels\":[],\"series\":[]}", "labels 은(는) 1~200개여야 함");
    bad("{\"type\":\"bar\",\"labels\":[1],\"series\":[]}", "labels[0] 은(는) 글이어야 함");
    bad("{\"type\":\"line\",\"labels\":[\"a\"]}", "series 이(가) 없음");
    bad("{\"type\":\"line\",\"labels\":[\"a\"],\"series\":[]}", "series 은(는) 1~12개여야 함");
    bad("{\"type\":\"line\",\"labels\":[\"a\"],\"series\":[1]}", "series[0] 은(는) 객체");
    bad("{\"type\":\"line\",\"labels\":[\"a\"],\"series\":[{\"data\":[1]}]}", "series[0].name 이(가) 없음");
    bad("{\"type\":\"line\",\"labels\":[\"a\"],\"series\":[{\"name\":2,\"data\":[1]}]}", "series[0].name 은(는) 글이어야 함");
    bad("{\"type\":\"line\",\"labels\":[\"a\"],\"series\":[{\"name\":\"s\"}]}", "series[0].data 이(가) 없음");
    bad("{\"type\":\"area\",\"labels\":[\"a\",\"b\"],\"series\":[{\"name\":\"s\",\"data\":[1,2]},{\"name\":\"t\",\"data\":[1]}]}",
        "series[1].data 길이가 labels 와 다름");
    bad("{\"type\":\"bar\",\"labels\":[\"a\"],\"series\":[{\"name\":\"s\",\"data\":[\"5\"]}]}", "series[0].data[0] 은(는) 수나 null(빈칸)이어야 함");
    bad("{\"type\":\"bar\",\"labels\":[\"a\"],\"series\":[{\"name\":\"s\",\"data\":[true]}]}", "series[0].data[0]");
    bad("{\"type\":\"bar\",\"labels\":[\"a\"],\"series\":[{\"name\":\"s\",\"data\":[1],\"type\":\"pie\"}]}",
        "series[0].type 은(는) bar · line");
    bad("{\"type\":\"bar\",\"labels\":[\"a\"],\"series\":[{\"name\":\"s\",\"data\":[1]}],\"stacked\":\"yes\"}", "stacked 은(는) true · false");
    bad("{\"type\":\"bar\",\"labels\":[\"a\"],\"series\":[{\"name\":\"s\",\"data\":[1]}],\"horizontal\":1}", "horizontal 은(는) true · false");
    bad("{\"type\":\"bar\",\"labels\":[\"a\"],\"series\":[{\"name\":\"s\",\"data\":[1]}],\"xLabel\":5}", "xLabel 은(는) 글이어야 함");
  }

  @Test
  void pie_donut_의_틀림() {
    bad("{\"type\":\"pie\"}", "data 이(가) 없음");
    bad("{\"type\":\"pie\",\"data\":[]}", "data 은(는) 1~50개여야 함");
    bad("{\"type\":\"donut\",\"data\":[[1]]}", "data[0] 은(는) 객체");
    bad("{\"type\":\"pie\",\"data\":[{\"value\":1}]}", "data[0].label 이(가) 없음");
    bad("{\"type\":\"pie\",\"data\":[{\"label\":\"a\"}]}", "data[0].value 이(가) 없음");
    bad("{\"type\":\"pie\",\"data\":[{\"label\":\"a\",\"value\":-1}]}", "data[0].value 는 0 이상");
    bad("{\"type\":\"pie\",\"data\":[{\"label\":\"a\",\"value\":\"1\"}]}", "data[0].value 은(는) 수여야 함");
    bad("{\"type\":\"pie\",\"data\":[{\"label\":\"a\",\"value\":null}]}", "data[0].value 이(가) 없음");
  }

  @Test
  void scatter_의_틀림() {
    bad("{\"type\":\"scatter\"}", "series 이(가) 없음");
    bad("{\"type\":\"scatter\",\"series\":[{\"name\":\"s\",\"data\":[]}]}", "series[0].data 는 점 1~1,000개");
    bad("{\"type\":\"scatter\",\"series\":[{\"name\":\"s\",\"data\":[[1]]}]}", "series[0].data[0] 은(는) [x, y] 수 둘");
    bad("{\"type\":\"scatter\",\"series\":[{\"name\":\"s\",\"data\":[1,2]}]}", "series[0].data[0] 은(는) [x, y]");
    bad("{\"type\":\"scatter\",\"series\":[{\"name\":\"s\",\"data\":[[1,null]]}]}", "series[0].data[0][1] 은(는) 수여야 함");
    bad("{\"type\":\"scatter\",\"series\":[{\"name\":\"s\",\"data\":[[1,2,3]]}]}", "[x, y] 수 둘");
  }

  @Test
  void radar_의_틀림() {
    bad("{\"type\":\"radar\",\"labels\":[\"a\",\"b\"],\"series\":[{\"name\":\"s\",\"data\":[1,2]}]}", "labels 은(는) 3~20개");
    bad("{\"type\":\"radar\",\"labels\":" + strs(21) + ",\"series\":[]}", "labels 은(는) 3~20개");
    bad("{\"type\":\"radar\",\"labels\":[\"a\",\"b\",\"c\"],\"series\":[{\"name\":\"s\",\"data\":[1,2]}]}", "길이가 labels 와 다름");
    bad("{\"type\":\"radar\",\"labels\":[\"a\",\"b\",\"c\"],\"series\":[{\"name\":\"s\",\"data\":[1,null,3]}]}",
        "series[0].data[1] 은(는) 수여야 함(radar 는 null 없음)");
    String nine = IntStream.range(0, 9).mapToObj(i -> "{\"name\":\"s\",\"data\":[1,2,3]}").collect(Collectors.joining(",", "[", "]"));
    bad("{\"type\":\"radar\",\"labels\":[\"a\",\"b\",\"c\"],\"series\":" + nine + "}", "series 은(는) 1~8개");
    bad("{\"type\":\"radar\",\"labels\":[\"a\",\"b\",\"c\"],\"series\":[{\"name\":\"s\",\"data\":[1,2,3]}],\"max\":\"5\"}", "max 은(는) 수여야 함");
  }

  @Test
  void gauge_의_틀림() {
    bad("{\"type\":\"gauge\"}", "value 이(가) 없음");
    bad("{\"type\":\"gauge\",\"value\":\"63\"}", "value 은(는) 수여야 함");
    bad("{\"type\":\"gauge\",\"value\":101}", "value 가 min ~ max 밖");
    bad("{\"type\":\"gauge\",\"value\":-1}", "value 가 min ~ max 밖");
    bad("{\"type\":\"gauge\",\"value\":5,\"min\":10,\"max\":10}", "min 은(는) max 보다 작아야 함");
    bad("{\"type\":\"gauge\",\"value\":5,\"min\":200}", "min 은(는) max 보다 작아야 함");
    ok("{\"type\":\"gauge\",\"value\":-5,\"min\":-10,\"max\":0}");
    ok("{\"type\":\"gauge\",\"value\":100}");
    ok("{\"type\":\"gauge\",\"value\":150,\"max\":200}");
  }

  @Test
  void sparkline_의_틀림() {
    bad("{\"type\":\"sparkline\"}", "data 이(가) 없음");
    bad("{\"type\":\"sparkline\",\"data\":[1]}", "data 은(는) 2~500개");
    bad("{\"type\":\"sparkline\",\"data\":" + nums(501) + "}", "data 은(는) 2~500개");
    bad("{\"type\":\"sparkline\",\"data\":[1,null]}", "data[1] 은(는) 수여야 함(sparkline 은 null 없음)");
    bad("{\"type\":\"sparkline\",\"data\":[1,2],\"plot\":\"area\"}", "plot 은(는) line · bar");
    bad("{\"type\":\"sparkline\",\"data\":[1,2],\"labels\":[\"a\"]}", "labels 길이가 data 와 다름");
    bad("{\"type\":\"sparkline\",\"data\":[1,2],\"labels\":[\"a\",3]}", "labels[1] 은(는) 글이어야 함");
    // 화면과 같은 차례 — 길이를 글보다 먼저 본다
    bad("{\"type\":\"sparkline\",\"data\":[1,2],\"labels\":[3]}", "labels 길이가 data 와 다름");
    bad("{\"type\":\"sparkline\",\"data\":[1,2],\"labels\":\"a\"}", "labels 은(는) 배열이어야 함");
    ok("{\"type\":\"sparkline\",\"data\":" + nums(500) + ",\"plot\":\"bar\"}");
    ok("{\"type\":\"sparkline\",\"data\":[1,2],\"labels\":[\"a\",\"b\"],\"plot\":\"line\"}");
  }

  // ── 한도 ────────────────────────────────────────────────────────────

  @Test
  void 글자_수_개수_높이_점_합계의_한도() {
    String a100 = "가".repeat(100);
    ok("{\"type\":\"gauge\",\"value\":1,\"title\":\"" + a100 + "\"}");
    bad("{\"type\":\"gauge\",\"value\":1,\"title\":\"" + a100 + "x\"}", "title 이(가) 100자를 넘음");
    bad("{\"type\":\"gauge\",\"value\":1,\"unit\":\"" + "u".repeat(21) + "\"}", "unit 이(가) 20자를 넘음");
    bad("{\"type\":\"gauge\",\"value\":1,\"height\":159}", "height 은(는) 160~600 사이");
    bad("{\"type\":\"gauge\",\"value\":1,\"height\":600.5}", "height 은(는) 160~600 사이");
    bad("{\"type\":\"gauge\",\"value\":1,\"height\":\"300\"}", "height 은(는) 수여야 함");
    bad("{\"type\":\"bar\",\"labels\":[\"" + "l".repeat(61) + "\"],\"series\":[]}", "labels[0] 이(가) 60자를 넘음");
    bad("{\"type\":\"bar\",\"labels\":[\"a\"],\"series\":[{\"name\":\"" + "n".repeat(61) + "\",\"data\":[1]}]}",
        "series[0].name 이(가) 60자를 넘음");
    bad("{\"type\":\"bar\",\"labels\":[\"a\"],\"series\":[{\"name\":\"s\",\"data\":[1]}],\"yLabel\":\"" + "y".repeat(41) + "\"}",
        "yLabel 이(가) 40자를 넘음");
    bad("{\"type\":\"bar\",\"labels\":" + strs(201) + ",\"series\":[]}", "labels 은(는) 1~200개");
    String thirteen = IntStream.range(0, 13).mapToObj(i -> "{\"name\":\"s\",\"data\":[1]}").collect(Collectors.joining(",", "[", "]"));
    bad("{\"type\":\"line\",\"labels\":[\"a\"],\"series\":" + thirteen + "}", "series 은(는) 1~12개");
    bad("{\"type\":\"pie\",\"data\":" + IntStream.range(0, 51).mapToObj(i -> "{\"label\":\"a\",\"value\":1}")
        .collect(Collectors.joining(",", "[", "]")) + "}", "data 은(는) 1~50개");

    // 점 합계 — 200 × 10 = 2,000 은 되고, 200 × 11 은 넘는다
    String ten = IntStream.range(0, 10).mapToObj(i -> "{\"name\":\"s\",\"data\":" + nums(200) + "}").collect(Collectors.joining(","));
    ok("{\"type\":\"bar\",\"labels\":" + strs(200) + ",\"series\":[" + ten + "]}");
    bad("{\"type\":\"bar\",\"labels\":" + strs(200) + ",\"series\":[" + ten + ",{\"name\":\"s\",\"data\":" + nums(200) + "}]}",
        "점이 모두 2,000개를 넘음");
    // scatter — series 하나 1,000점까지, 합계 2,000
    String pts1000 = IntStream.range(0, 1000).mapToObj(i -> "[" + i + ",1]").collect(Collectors.joining(",", "[", "]"));
    bad("{\"type\":\"scatter\",\"series\":[{\"name\":\"s\",\"data\":" + pts1000.replace("]]", "],[1,1]]") + "}]}",
        "series[0].data 는 점 1~1,000개");
    ok("{\"type\":\"scatter\",\"series\":[{\"name\":\"a\",\"data\":" + pts1000 + "},{\"name\":\"b\",\"data\":" + pts1000 + "}]}");
    bad("{\"type\":\"scatter\",\"series\":[{\"name\":\"a\",\"data\":" + pts1000 + "},{\"name\":\"b\",\"data\":" + pts1000
        + "},{\"name\":\"c\",\"data\":[[1,1]]}]}", "점이 모두 2,000개를 넘음");
  }

  @Test
  void 글자_수는_유니코드_글자로_센다() {
    // 이모지 하나는 한 글자 — 화면(Array.from(text).length)과 같게 센다
    ok("{\"type\":\"gauge\",\"value\":1,\"unit\":\"" + "😀".repeat(20) + "\"}");
    bad("{\"type\":\"gauge\",\"value\":1,\"unit\":\"" + "😀".repeat(21) + "\"}", "unit 이(가) 20자를 넘음");
    ok("{\"type\":\"gauge\",\"value\":1,\"title\":\"" + "👍🏽".repeat(50) + "\"}");
    // 블록도 — 이모지 15,000개(UTF-16 으로는 30,000)는 20,000자 안이다
    String emoji = "{\"type\":\"gauge\",\"value\":1,\"x\":\"" + "😀".repeat(15_000) + "\"}";
    assertThat(emoji.length()).isGreaterThan(ChartBlockValidator.MAX_CHARS);
    assertThat(v(emoji).valid()).isTrue();
  }

  // ── 색 ──────────────────────────────────────────────────────────────

  @Test
  void 색은_hex_세_자리와_여섯_자리만() {
    for (String c : List.of("#fff", "#FFF", "#a1B2c3", "#000000")) {
      ok("{\"type\":\"pie\",\"data\":[{\"label\":\"a\",\"value\":1,\"color\":\"" + c + "\"}]}");
    }
    for (String c : List.of("red", "#ffff", "#ffffffff", "#ggg", "fff", "rgb(0,0,0)", "url(https://x/y)", " #fff", "#fff ", "")) {
      bad("{\"type\":\"pie\",\"data\":[{\"label\":\"a\",\"value\":1,\"color\":\"" + c + "\"}]}", "data[0].color 색 꼴이 틀림");
    }
    bad("{\"type\":\"bar\",\"labels\":[\"a\"],\"series\":[{\"name\":\"s\",\"data\":[1],\"color\":123}]}", "series[0].color 색 꼴이 틀림");
    bad("{\"type\":\"scatter\",\"series\":[{\"name\":\"s\",\"data\":[[1,1]],\"color\":\"blue\"}]}", "series[0].color 색 꼴이 틀림");
    bad("{\"type\":\"radar\",\"labels\":[\"a\",\"b\",\"c\"],\"series\":[{\"name\":\"s\",\"data\":[1,2,3],\"color\":\"#12\"}]}",
        "series[0].color 색 꼴이 틀림");
  }

  // ── null 빈칸 ────────────────────────────────────────────────────────

  @Test
  void null_빈칸은_bar_line_area_에서만() {
    ok("{\"type\":\"bar\",\"labels\":[\"a\",\"b\"],\"series\":[{\"name\":\"s\",\"data\":[null,null]}]}");
    ok("{\"type\":\"area\",\"labels\":[\"a\",\"b\",\"c\"],\"series\":[{\"name\":\"s\",\"data\":[1,null,3]}]}");
    bad("{\"type\":\"radar\",\"labels\":[\"a\",\"b\",\"c\"],\"series\":[{\"name\":\"s\",\"data\":[null,1,2]}]}", "null 없음");
    bad("{\"type\":\"sparkline\",\"data\":[null,1]}", "null 없음");
    bad("{\"type\":\"scatter\",\"series\":[{\"name\":\"s\",\"data\":[null]}]}", "[x, y]");
    // labels 의 null 은 빈칸이 아니다
    bad("{\"type\":\"bar\",\"labels\":[null],\"series\":[{\"name\":\"s\",\"data\":[1]}]}", "labels[0] 은(는) 글이어야 함");
  }

  // ── 모르는 키 ────────────────────────────────────────────────────────

  @Test
  void 모르는_키는_틀림이_아니라_무시되는_키로_모은다() {
    ChartBlockValidator.Result r = v("{\"type\":\"bar\",\"lables\":[\"a\"],\"labels\":[\"a\"],\"series\":[{\"name\":\"s\",\"data\":[1],"
        + "\"colour\":\"#fff\"}],\"legend\":false}");
    assertThat(r.valid()).isTrue();
    assertThat(r.ignored()).containsExactly("lables", "legend", "series[0].colour");

    // 다른 종류에서만 쓰는 키도 무시 — 선 차트의 horizontal, area 의 series[].type, pie 의 labels
    assertThat(v("{\"type\":\"line\",\"horizontal\":true,\"labels\":[\"a\"],\"series\":[{\"name\":\"s\",\"data\":[1]}]}").ignored())
        .containsExactly("horizontal");
    ChartBlockValidator.Result area = v("{\"type\":\"area\",\"labels\":[\"a\"],\"series\":[{\"name\":\"s\",\"data\":[1],\"type\":\"bar\"}]}");
    assertThat(area.valid()).isTrue();
    assertThat(area.ignored()).containsExactly("series[0].type");
    assertThat(v("{\"type\":\"pie\",\"labels\":[\"a\"],\"data\":[{\"label\":\"a\",\"value\":1,\"x\":1}]}").ignored())
        .containsExactly("labels", "data[0].x");
    assertThat(v("{\"type\":\"gauge\",\"value\":1,\"url\":\"https://x\"}").ignored()).containsExactly("url");

    // 틀린 블록도 그때까지 본 모르는 키를 준다 — 오타(lables)가 까닭(labels 없음)을 밝힌다
    ChartBlockValidator.Result typo = v("{\"type\":\"bar\",\"lables\":[\"a\"],\"series\":[{\"name\":\"s\",\"data\":[1]}]}");
    assertThat(typo.error()).contains("labels 이(가) 없음");
    assertThat(typo.ignored()).containsExactly("lables");
  }

  // ── JSON ────────────────────────────────────────────────────────────

  @Test
  void JSON_객체_하나가_아니면_틀림() {
    bad("", "비어 있음");
    bad("  \n ", "비어 있음");
    bad(null, "비어 있음");
    for (String json : List.of("[]", "[{\"type\":\"bar\"}]", "\"bar\"", "1", "null", "true")) {
      bad(json, "JSON 객체가 아님");
    }
    bad("type: bar", "JSON 이 아님");
    bad("{\"type\":\"gauge\",\"value\":1} {}", "JSON 객체 뒤에 다른 글이 있음");
    bad("{\"type\":\"gauge\",\"value\":1} x", "JSON 객체 뒤에 다른 글이 있음");
  }

  @Test
  void 엄격한_JSON_주석_끝_쉼표_작은따옴표_따옴표_없는_키_NaN_같은_키() {
    bad("{\"type\":\"gauge\",\"value\":1,}", "JSON 이 아님");
    bad("{\"type\":\"gauge\",\"value\":1 // 주석\n}", "JSON 이 아님");
    bad("{\"type\":\"gauge\",/* 주석 */\"value\":1}", "JSON 이 아님");
    bad("{'type':'gauge','value':1}", "JSON 이 아님");
    bad("{type:\"gauge\",value:1}", "JSON 이 아님");
    bad("{\"type\":\"gauge\",\"value\":NaN}", "JSON 이 아님");
    bad("{\"type\":\"gauge\",\"value\":Infinity}", "JSON 이 아님");
    bad("{\"type\":\"gauge\",\"value\":01}", "JSON 이 아님");
    bad("{\"type\":\"gauge\",\"value\":1,\"value\":2}", "같은 키가 두 번 나옴");
    bad("{\"type\":\"pie\",\"data\":[{\"label\":\"a\",\"label\":\"b\",\"value\":1}]}", "같은 키가 두 번 나옴");
    // 같은 키 두 번은 화면이 그리므로 메시지가 "글이 보인다"고 하지 않는다
    assertThat((String) ChartBlockValidator.checks("본문", "```chart\n{\"type\":\"gauge\",\"type\":\"gauge\",\"value\":1}\n```")
        .get(0).get("message")).contains("같은 키가 두 번 나옴").contains("화면은 뒤의 값으로 그리지만").doesNotContain("차트 대신");
    // 자리(블록 안 줄 · 칸)를 알린다
    assertThat(v("{\n  \"type\": \"gauge\",\n  \"value\": 1,\n}").error()).contains("블록 안 4줄");
  }

  @Test
  void 무한이_되는_큰_수와_수가_아닌_값() {
    bad("{\"type\":\"gauge\",\"value\":1e400,\"max\":1e401}", "value 이(가) 너무 큼");
    bad("{\"type\":\"bar\",\"labels\":[\"a\"],\"series\":[{\"name\":\"s\",\"data\":[-1e999]}]}", "series[0].data[0] 이(가) 너무 큼");
    ok("{\"type\":\"gauge\",\"value\":1e2,\"max\":1.7e308}");
    ok("{\"type\":\"gauge\",\"value\":12345678901234567890,\"max\":1e20}");
  }

  // ── 큰 글 ───────────────────────────────────────────────────────────

  @Test
  void 큰_블록과_깊은_겹침은_틀림으로_빨리_끝난다() {
    String big = "{\"type\":\"gauge\",\"value\":1,\"title\":\"" + "x".repeat(ChartBlockValidator.MAX_CHARS) + "\"}";
    bad(big, "블록이 20,000자를 넘음");
    // 정확히 20,000자는 본다(그 뒤 title 길이로 틀림)
    String exact = "{\"type\":\"gauge\",\"value\":1,\"title\":\"" + "x".repeat(ChartBlockValidator.MAX_CHARS - 37) + "\"}";
    assertThat(exact).hasSize(ChartBlockValidator.MAX_CHARS);
    bad(exact, "title 이(가) 100자를 넘음");
    // 깊게 겹친 배열 — 파서 한도(32)로 틀림, 스택이 넘치지 않는다
    String deep = "{\"type\":\"bar\",\"labels\":" + "[".repeat(5000) + "]".repeat(5000) + "}";
    bad(deep, "JSON 이 아님");
  }

  @Test
  void 아주_큰_글의_많은_블록도_빨리_보고_알림은_다섯_줄과_남은_수() {
    StringBuilder md = new StringBuilder("# 큰 글\n");
    for (int i = 0; i < 3000; i++) {
      md.append(i % 2 == 0 ? "```chart\n{\"type\":\"gauge\",\"value\":1}\n```\n" : "```chart\n{\"type\":\"gauge\"}\n```\n");
      md.append("글 ").append("가".repeat(200)).append('\n');
    }
    long start = System.nanoTime();
    List<Map<String, Object>> checks = ChartBlockValidator.checks("본문", md.toString());
    long ms = (System.nanoTime() - start) / 1_000_000;
    assertThat(ms).as("1MB 남짓의 글 — " + ms + "ms").isLessThan(5_000);
    assertThat(checks).hasSize(ChartBlockValidator.CHECKS_MAX + 1);
    assertThat(checks.get(0).get("message")).asString().startsWith("2 번째 차트 블록(본문 6줄): value 이(가) 없음");
    assertThat(checks.get(ChartBlockValidator.CHECKS_MAX).get("message")).asString().contains("틀린 차트 블록이 1495개 더 있다");
  }

  // ── 글 전체 → checks ─────────────────────────────────────────────────

  @Test
  void checks_는_N_번째_블록과_줄을_알리고_사용자_값을_메시지에_넣지_않는다() {
    String secret = "비밀<script>alert(1)</script>";
    String md = "본문\n\n```chart\n{\"type\":\"bar\",\"labels\":[\"" + secret + "\"],\"series\":[{\"name\":\"" + secret
        + "\",\"data\":[\"" + secret + "\"]}]}\n```\n\n```mermaid\ngraph LR\n```\n\n~~~Chart title=\"x\"\n{\"type\":\"gauge\",\"value\":5,\""
        + secret + "\":1}\n~~~\n\n```js\n{\"type\":1}\n```\n";
    List<Map<String, Object>> checks = ChartBlockValidator.checks("본문", md);
    assertThat(checks).extracting(c -> c.get("code")).containsExactly("CHART_INVALID", "CHART_UNKNOWN_KEY");
    Map<String, Object> invalid = checks.get(0);
    assertThat(invalid.get("level")).isEqualTo("WARN");
    assertThat((String) invalid.get("message")).startsWith("1 번째 차트 블록(본문 3줄): series[0].data[0] 은(는) 수나 null(빈칸)이어야 함")
        .contains("get_markdown_guide(topic=chart)").doesNotContain("비밀").doesNotContain("script");
    assertThat(invalid).doesNotContainKey("items");
    Map<String, Object> unknown = checks.get(1);
    assertThat(unknown.get("level")).isEqualTo("INFO");
    assertThat((String) unknown.get("message")).startsWith("2 번째 차트 블록(본문 11줄): 모르는 키 1개(items)는 무시된다")
        .doesNotContain("비밀");
    // 모르는 키 이름은 사용자 글 — items 에 한 줄로 펴서 60자까지
    @SuppressWarnings("unchecked")
    List<String> items = (List<String>) unknown.get("items");
    assertThat(items).containsExactly(secret);
  }

  @Test
  void 맞는_차트_차트가_없는_글_null_은_알림이_없다() {
    assertThat(ChartBlockValidator.checks("본문", null)).isEmpty();
    assertThat(ChartBlockValidator.checks("본문", "")).isEmpty();
    assertThat(ChartBlockValidator.checks("본문", "## 차트 이야기\n```json\n{}\n```")).isEmpty();
    assertThat(ChartBlockValidator.checks("댓글", "```chart\n{\"type\":\"sparkline\",\"data\":[1,2]}\n```")).isEmpty();
    // 코드 블록 안에 보인 ```chart 예는 차트가 아니다
    assertThat(ChartBlockValidator.checks("본문", "````markdown\n```chart\n{틀림}\n```\n````")).isEmpty();
  }

  @Test
  void 닫지_않은_블록과_목록_안의_블록도_본다() {
    List<Map<String, Object>> unclosed = ChartBlockValidator.checks("댓글", "끝\n```chart\n{\"type\":\"gauge\",\"value\":1}\n덧붙인 글");
    assertThat(unclosed).hasSize(1);
    assertThat((String) unclosed.get(0).get("message")).startsWith("1 번째 차트 블록(댓글 2줄): JSON 객체 뒤에 다른 글이 있음");
    List<Map<String, Object>> listed = ChartBlockValidator.checks("setSection", "- 결과\n\n  ```chart\n  {\"type\":\"pie\"}\n  ```");
    assertThat(listed).hasSize(1);
    assertThat((String) listed.get(0).get("message")).startsWith("1 번째 차트 블록(setSection 3줄): data 이(가) 없음");
  }

  @Test
  void 모르는_키가_많으면_items_는_다섯과_외_N개() {
    List<String> keys = new ArrayList<>();
    for (int i = 0; i < 8; i++) {
      keys.add("\"k" + i + "\":1");
    }
    Collections.shuffle(keys);
    List<Map<String, Object>> checks = ChartBlockValidator.checks("본문",
        "```chart\n{\"type\":\"gauge\",\"value\":1," + String.join(",", keys) + "}\n```");
    assertThat(checks).hasSize(1);
    @SuppressWarnings("unchecked")
    List<String> items = (List<String>) checks.get(0).get("items");
    assertThat(items).hasSize(6).last().isEqualTo("외 3개");
    assertThat((String) checks.get(0).get("message")).contains("모르는 키 8개(items)");
  }
}
