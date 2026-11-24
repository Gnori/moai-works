package com.moai.works.api.mcp;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import com.moai.works.api.common.ApiException;

/**
 * 일감 설명 · 댓글의 Markdown 꼴 안내 MCP 도구({@code get_markdown_guide}) — 일감 입구({@code /mcp/issue})에 있다(일감 #217).
 * 화면이 {@code ```mermaid}(다이어그램) · {@code ```chart}(차트, JSON) · 코드 블록(언어 · title · 강조 줄 · diff)을 그리게 되어, MCP 로
 * 글을 쓰는 Agent 가 그 꼴을 서버에서 바로 읽게 한다. 쓰기 도구의 설명은 한 줄로 이 도구를 가리키고({@link #MARKDOWN_LINE}), 틀린
 * {@code ```chart} 는 쓰기 결과의 {@code checks} 가 알린다({@link ChartBlockValidator}).
 *
 * <p>읽기만 — 고정 글이라 DB 의 일감 · 프로젝트를 읽지 않는다. 그래도 키로 일할 수 있는 워크스페이스가 하나는 있어야 한다
 * ({@code list_issue_types} 처럼 멤버가 쓰는 도구 — 없으면 {@code WORKSPACE_NOT_FOUND}). 다른 도구처럼 접근 기록에 남는다(워크스페이스 없이
 * 키 주인에게만 보이는 줄, 인자 요약에 topic).
 *
 * <p>글은 결과의 크기를 작게 둔다 — topic=all 이 6 KB(UTF-8) 안. 차트 꼴은 화면과 함께 정한 "```chart 블록 꼴 v1"이고 검사 규칙은
 * {@link ChartBlockValidator} 와 같다.
 */
@Component
public class MarkdownMcpTools {

  /** 쓰기 도구(create_issue · update_issue · add_comment · update_comment) 설명에 넣는 한 줄 */
  static final String MARKDOWN_LINE = "글은 Markdown — ```mermaid · ```chart(JSON) · 코드 블록(title=\"…\" · {1,3-5} 강조 줄)이 그림으로 "
      + "보인다. 꼴은 get_markdown_guide, 틀린 chart 는 checks 의 CHART_INVALID(막지 않는다).";

  /**
   * 코드 블록 언어 — 화면(web-frontend)이 색칠하는 언어. highlight.js 의 common 묶음에 더한 것들. 화면이 등록한 언어가 바뀌면
   * <b>이 한 곳만</b> 고친다(일감 #217).
   */
  static final String CODE_LANGUAGES = "highlight.js common 묶음(arduino · bash · c · cpp · csharp · css · diff · go · graphql · ini · java · "
      + "javascript · json · kotlin · less · lua · makefile · markdown · objectivec · perl · php · plaintext · python · r · ruby · "
      + "rust · scss · sql · swift · typescript · vbnet · wasm · xml(html) · yaml · console($ 명령 기록) — 별칭 js · ts · py · sh · shell · "
      + "yml 도) + "
      + "dockerfile · nginx · apache · powershell · dos · gradle · groovy · scala · dart · elixir · erlang · haskell · clojure · "
      + "protobuf · http · properties · cmake · latex · matlab · julia · ocaml · fsharp · vim · x86asm · nix · pgsql · handlebars · "
      + "django · verilog · vhdl. 모르는 언어 · 언어 없음은 색 없이 글로";

  /** topic 값 — all 은 모두 */
  static final List<String> TOPICS = List.of("chart", "mermaid", "code", "all");

  private final McpScope scope;

  public MarkdownMcpTools(McpScope scope) {
    this.scope = scope;
  }

  @Tool(name = "get_markdown_guide", description = "설명 · 댓글의 Markdown 꼴 — ```mermaid(다이어그램) · ```chart(JSON 차트 — 종류마다 "
      + "키 · 한도 · 예) · 코드 블록(언어 · title · 강조 줄 · diff) · 기본(GFM · #번호 · 첨부). topic 으로 하나만(chart · mermaid · code, "
      + "기본 all). 쓰기 결과 checks 에 CHART_INVALID 가 오면 이것으로 고친다.")
  public Map<String, Object> getMarkdownGuide(
      @ToolParam(required = false, description = "chart · mermaid · code · all(기본 all)") String topic,
      @ToolParam(required = false, description = "워크스페이스 slug — 비워도 된다") String workspace,
      ToolContext toolContext) {
    return McpScope.run(() -> {
      // 다른 도구처럼 workspace 를 넘기는 Agent 가 많다 — 거절하지 않고, 주면 그 워크스페이스에서 일할 수 있는지만 본다(일감 #217 확인 중 찾음)
      if (workspace != null && !workspace.isBlank()) {
        scope.workspace(toolContext, workspace);
      } else if (scope.workspaces(toolContext).isEmpty()) {
        throw ApiException.notFound("WORKSPACE_NOT_FOUND", "이 MCP 키로 일할 수 있는 워크스페이스가 없습니다.");
      }
      return guide(topic);
    });
  }

  /** 안내 — topic 하나 또는 all(기본 · 차트 · mermaid · 코드). 모르는 topic 은 INVALID_ARGUMENT(field topic) */
  static Map<String, Object> guide(String topic) {
    String t = topic == null || topic.isBlank() ? "all" : topic.trim().toLowerCase(Locale.ROOT);
    if (!TOPICS.contains(t)) {
      throw ApiException.badRequest("INVALID_ARGUMENT", "topic 은 chart · mermaid · code · all 가운데 하나입니다.")
          .with("field", "topic");
    }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("topic", t);
    boolean all = "all".equals(t);
    if (all) {
      out.put("basics", basics());
    }
    if (all || "mermaid".equals(t)) {
      out.put("mermaid", mermaid());
    }
    if (all || "chart".equals(t)) {
      out.put("chart", chart());
    }
    if (all || "code".equals(t)) {
      out.put("code", code());
    }
    return out;
  }

  private static Map<String, Object> basics() {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("markdown", "GFM — 표(| 칸 | 칸 | + |---|---|) · 체크 목록(- [ ] 할 일 · - [x] 한 일) · 취소선 ~~글~~ · 링크");
    m.put("issueLink", "#123 — 같은 워크스페이스의 그 일감으로 가는 링크");
    m.put("attachment", "![이름](attachment:<id>) — 첨부(list_attachments 의 markdown). 이미지는 그 자리에 그려진다");
    m.put("html", "HTML 은 그리지 않는다(글자 그대로 보인다)");
    return m;
  }

  private static Map<String, Object> mermaid() {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("fence", "```mermaid → 첫 줄은 종류 이름 → ```");
    m.put("kinds", "flowchart(graph) · sequenceDiagram · stateDiagram-v2 · classDiagram · erDiagram · gantt · pie · journey · "
        + "mindmap · timeline · gitGraph …");
    m.put("example", "```mermaid\nflowchart LR\n  A[등록] --> B[진행] --> C{확인}\n  C -->|통과| D[완료]\n  C -->|문제| B\n```");
    m.put("tips", "노드 글에 괄호 · 따옴표 · 기호가 있으면 A[\"글 (괄호)\"] 처럼 큰따옴표로 감싼다");
    m.put("onError", "문법이 틀리면 화면에 그림 대신 글과 까닭이 보인다(서버는 mermaid 문법을 검사하지 않는다)");
    return m;
  }

  private static Map<String, Object> chart() {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("fence", "```chart → JSON 객체 하나 → ```. 주석 · 끝 쉼표 · 작은따옴표 · 같은 키 두 번은 안 된다. 블록 20,000자까지(글자 수는 "
        + "유니코드 글자)");
    m.put("common", "type(필수: " + String.join(" · ", ChartBlockValidator.TYPES) + ") · title(≤100자) · "
        + "height(160~600, 기본 300 · gauge 200 · sparkline 80) · unit(≤20자, 값 뒤 단위)");
    Map<String, Object> keys = new LinkedHashMap<>();
    keys.put("bar · line · area", "labels(필수, 글 ≤60자 1~200개) · series(필수, 1~12개: {name(필수, ≤60자), data(수 · null=빈칸, "
        + "labels 와 같은 길이), type?(bar · line — bar · line 차트에서 섞기), color?}) · stacked?(true · false) · "
        + "horizontal?(bar 만, 가로 막대) · xLabel · yLabel(≤40자)");
    keys.put("pie · donut", "data(필수, 1~50개: {label(필수, ≤60자), value(필수, 0 이상), color?})");
    keys.put("scatter", "series(필수, 1~12개: {name, data: [[x, y], …] 1~1,000점, color?}) · xLabel · yLabel");
    keys.put("radar", "labels(필수, 3~20개) · series(필수, 1~8개: {name, data(수만 — null 없음, labels 와 같은 길이), color?}) · max?");
    keys.put("gauge", "value(필수) · min · max(기본 0 · 100, min < max, min ≤ value ≤ max)");
    keys.put("sparkline", "data(필수, 수 2~500개, null 없음) · plot?(line 기본 · bar) · labels?(data 와 같은 길이 — 툴팁)");
    m.put("keys", keys);
    m.put("rules", "수는 유한한 JSON 수. 점 합계 ≤ 2,000(bar · line · area 는 labels × series, scatter 는 series 합). "
        + "색은 #rgb · #rrggbb 만. 글은 글로만 그린다(HTML 아님). 바깥 주소에서 받아 오는 키는 없다. 모르는 키는 무시된다. "
        + "선택 키의 null 은 넣지 않은 것과 같다");
    m.put("onError", "틀리면 화면에 차트 대신 글과 까닭 한 줄. MCP 로 쓰면 결과 checks 에 CHART_INVALID(WARN) \"N 번째 차트 블록(본문 L줄): "
        + "까닭\"(자리 번호는 0부터), 모르는 키는 CHART_UNKNOWN_KEY(INFO) — 쓰기는 막지 않는다");
    m.put("examples", examples());
    return m;
  }

  /** 종류마다 가장 작은 맞는 예 — ```chart 블록 안에 그대로 넣는 JSON(키 차례는 type 먼저) */
  private static Map<String, Object> examples() {
    Map<String, Object> e = new LinkedHashMap<>();
    e.put("bar", obj("type", "bar", "labels", List.of("월", "화", "수"), "series", List.of(
        obj("name", "완료", "data", List.of(3, 5, 2)), obj("name", "등록", "data", List.of(4, 2, 3), "type", "line"))));
    e.put("line", obj("type", "line", "labels", List.of("1주", "2주", "3주"), "series",
        List.of(obj("name", "남은 일", "data", List.of(9, 6, 2)))));
    e.put("area", obj("type", "area", "labels", List.of("1월", "2월"), "series",
        List.of(obj("name", "시간", "data", List.of(12, 18)))));
    e.put("pie", obj("type", "pie", "data", List.of(obj("label", "버그", "value", 12), obj("label", "새기능", "value", 20))));
    e.put("donut", obj("type", "donut", "data", List.of(obj("label", "완료", "value", 7, "color", "#2e7d32"),
        obj("label", "남음", "value", 3))));
    e.put("scatter", obj("type", "scatter", "series", List.of(obj("name", "일감", "data",
        List.of(List.of(2, 3.5), List.of(5, 8))))));
    e.put("radar", obj("type", "radar", "labels", List.of("속도", "품질", "범위"), "series",
        List.of(obj("name", "이번 주", "data", List.of(4, 3, 5))), "max", 5));
    e.put("gauge", obj("type", "gauge", "value", 63, "unit", "%"));
    e.put("sparkline", obj("type", "sparkline", "data", List.of(3, 5, 4, 8)));
    return e;
  }

  /** 차례를 지키는 JSON 객체 — 키 · 값 번갈아 */
  private static Map<String, Object> obj(Object... kv) {
    Map<String, Object> m = new LinkedHashMap<>();
    for (int i = 0; i < kv.length; i += 2) {
      m.put((String) kv[i], kv[i + 1]);
    }
    return m;
  }

  private static Map<String, Object> code() {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("fence", "```언어 title=\"src/app.ts\" {1,3-5} → 코드 → ```");
    m.put("languages", CODE_LANGUAGES);
    m.put("title", "title=\"…\" — 블록 위에 파일 이름");
    m.put("highlight", "{1,3-5} — 그 줄들을 강조");
    m.put("diff", "```diff — + 줄은 더함, - 줄은 뺌 색으로");
    m.put("view", "줄 번호가 붙고, 긴 블록은 접혀 보이며 펼칠 수 있다");
    m.put("example", "```ts title=\"src/app.ts\" {2}\nconst a = 1;\nconst b = a + 1;\n```");
    return m;
  }
}
