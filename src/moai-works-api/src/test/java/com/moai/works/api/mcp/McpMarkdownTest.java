package com.moai.works.api.mcp;

import static com.jayway.jsonpath.JsonPath.read;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.moai.works.api.support.ApiTest;
import com.moai.works.api.support.McpTestClient;
import com.moai.works.api.support.TestApi;
import com.moai.works.api.support.TestUser;

/**
 * 설명 · 댓글의 Markdown 꼴 — get_markdown_guide(주제마다 · 크기 · 권한 · 접근 기록), 쓰기 도구 설명의 한 줄, MCP 로 쓴 ```chart 블록의
 * checks(CHART_INVALID · CHART_UNKNOWN_KEY — 막지 않는다)와 댓글 결과의 checks · next. 2026-11-24, 일감 #217.
 */
@ApiTest
class McpMarkdownTest {

  private static final String P = "md";
  private static final String GOOD = "```chart\n{\"type\":\"gauge\",\"title\":\"진행\",\"value\":63,\"unit\":\"%\"}\n```";
  /** 틀린 차트 — data 길이가 labels 와 다르다. 사용자 글(비밀값처럼 보이는 이름)은 메시지에 나오면 안 된다 */
  private static final String BAD = "```chart\n{\"type\":\"bar\",\"labels\":[\"a\",\"b\"],\"series\":[{\"name\":\"비밀이름\",\"data\":[1]}]}\n```";

  @Autowired
  TestApi api;
  @Autowired
  MockMvc mvc;
  @Autowired
  JdbcTemplate jdbc;

  private record Space(String ws, TestUser owner, TestUser curator) {
  }

  private Space space() throws Exception {
    TestUser owner = api.user("owner");
    String ws = api.teamWorkspace(owner, "마크다운");
    TestUser curator = api.user("curator");
    api.join(ws, owner, curator, "curator");
    api.post(owner, "/workspaces/{ws}/projects", Map.of("key", P, "name", "마크다운", "visibility", "WORKSPACE"), ws)
        .andExpect(status().isCreated());
    return new Space(ws, owner, curator);
  }

  private McpTestClient client(TestUser who) throws Exception {
    return new McpTestClient(mvc, TestApi.read(api.post(who, "/me/mcp-keys", Map.of("name", "마크다운 키"))
        .andExpect(status().isCreated()), "$.key"));
  }

  private static Map<String, Object> args(Object... kv) {
    Map<String, Object> m = new HashMap<>();
    for (int i = 0; i < kv.length; i += 2) {
      m.put((String) kv[i], kv[i + 1]);
    }
    return m;
  }

  private static List<String> codes(String result) {
    return read(result, "$.checks[*].code");
  }

  private static String message(String result, String code) {
    List<String> found = read(result, "$.checks[?(@.code == '" + code + "')].message");
    assertThat(found).as(code + " 이 있어야 한다").isNotEmpty();
    return found.get(0);
  }

  // ── 알림 스트림(GET) ─────────────────────────────────────────────────
  @Test
  void 알림_스트림_GET_은_405_로_바로_거절한다_붙잡아_두지_않는다() throws Exception {
    Space s = space();
    var opened = client(s.curator()).openStream();
    assertThat(opened.getRequest().isAsyncStarted()).as("붙잡아 두지 않는다").isFalse();
    assertThat(opened.getResponse().getStatus()).isEqualTo(405);
    assertThat(opened.getResponse().getHeader("Allow")).isEqualTo("POST, DELETE");
  }

  // ── get_markdown_guide ───────────────────────────────────────────────

  @Test
  void get_markdown_guide_는_주제마다_꼴을_주고_all_은_6KB_안이다() throws Exception {
    Space s = space();
    McpTestClient mcp = client(s.curator());
    assertThat(mcp.tools()).contains("get_markdown_guide");

    String all = mcp.call("get_markdown_guide", Map.of());
    assertThat((Map<String, Object>) read(all, "$")).containsOnlyKeys("topic", "basics", "mermaid", "chart", "code");
    assertThat((String) read(all, "$.topic")).isEqualTo("all");
    assertThat(all.getBytes(StandardCharsets.UTF_8).length).as("all 의 크기").isLessThan(6 * 1024);
    // 기본: 표 · 체크 목록 · #번호 · 첨부 · HTML
    assertThat((String) read(all, "$.basics.markdown")).contains("표").contains("- [ ]");
    assertThat((String) read(all, "$.basics.issueLink")).contains("#123");
    assertThat((String) read(all, "$.basics.attachment")).contains("![이름](attachment:<id>)");
    assertThat((String) read(all, "$.basics.html")).contains("HTML 은 그리지 않는다");
    // mermaid: 종류 · 예 · 틀리면
    assertThat((String) read(all, "$.mermaid.kinds")).contains("flowchart", "sequenceDiagram", "stateDiagram", "classDiagram",
        "erDiagram", "gantt", "pie", "journey", "mindmap", "timeline", "gitGraph");
    assertThat((String) read(all, "$.mermaid.example")).startsWith("```mermaid\n").endsWith("\n```");
    assertThat((String) read(all, "$.mermaid.onError")).contains("문법이 틀리면 화면에 그림 대신 글과 까닭이 보인다");
    // chart: 종류마다 키 · 한도 · 색 · 예 아홉
    assertThat((Map<String, Object>) read(all, "$.chart.keys")).hasSize(6);
    assertThat((String) read(all, "$.chart.rules")).contains("2,000").contains("#rgb · #rrggbb");
    assertThat((Map<String, Object>) read(all, "$.chart.examples")).containsOnlyKeys(ChartBlockValidator.TYPES);
    // code: 언어(한 상수) · 꼴
    assertThat((String) read(all, "$.code.languages")).isEqualTo(MarkdownMcpTools.CODE_LANGUAGES)
        .contains("highlight.js common", "dockerfile", "vhdl", "pgsql", "x86asm");
    assertThat((String) read(all, "$.code.fence")).contains("title=\"src/app.ts\" {1,3-5}");
    assertThat((String) read(all, "$.code.diff")).contains("```diff");

    // 주제 하나 — 그 칸만(대소문자 · 빈칸 무시)
    for (String topic : List.of("chart", "mermaid", "code")) {
      String one = mcp.call("get_markdown_guide", args("topic", " " + topic.toUpperCase() + " "));
      assertThat((Map<String, Object>) read(one, "$")).as(topic).containsOnlyKeys("topic", topic);
      assertThat(one.length()).isLessThan(all.length());
    }
    // 다른 도구처럼 workspace 를 넘겨도 된다 — 모르는 워크스페이스면 그 오류
    String withWs = mcp.call("get_markdown_guide", args("topic", "chart", "workspace", s.ws()));
    assertThat((Map<String, Object>) read(withWs, "$")).containsOnlyKeys("topic", "chart");
    String unknownWs = mcp.call("get_markdown_guide", args("workspace", "no-such-workspace"));
    assertThat((String) read(unknownWs, "$.error.code")).isNotBlank();
    String bad = mcp.call("get_markdown_guide", args("topic", "html"));
    assertThat((String) read(bad, "$.error.code")).isEqualTo("INVALID_ARGUMENT");
    assertThat((String) read(bad, "$.error.field")).isEqualTo("topic");

    // 읽기도 접근 기록에 남는다(작업 READ, 인자 요약에 topic)
    List<Map<String, Object>> rows = jdbc.queryForList(
        "SELECT operation, outcome, arguments::text AS a FROM mcp_access_log WHERE tool = 'get_markdown_guide' AND key_owner_id = ?"
            + " ORDER BY occurred_at", s.curator().id());
    assertThat(rows).hasSize(7).allSatisfy(r -> assertThat(r.get("operation")).isEqualTo("READ"));
    assertThat((String) rows.get(1).get("a")).contains("topic");
  }

  @Test
  void get_markdown_guide_는_키로_일할_워크스페이스가_없으면_WORKSPACE_NOT_FOUND() throws Exception {
    TestUser lonely = api.user("lonely");
    McpTestClient mcp = client(lonely);
    assertThat((String) read(mcp.call("get_markdown_guide", Map.of()), "$.topic")).isEqualTo("all");
    jdbc.update("UPDATE workspace SET status = 'SUSPENDED' WHERE slug = ?", api.personalWorkspace(lonely));
    assertThat((String) read(mcp.call("get_markdown_guide", Map.of()), "$.error.code")).isEqualTo("WORKSPACE_NOT_FOUND");
  }

  @Test
  void 쓰기_도구_네_개의_설명에_Markdown_꼴_한_줄이_있다() throws Exception {
    Space s = space();
    String listed = client(s.curator()).toolsJson();
    for (String tool : List.of("create_issue", "update_issue", "add_comment", "update_comment")) {
      String d = ((List<String>) read(listed, "$.result.tools[?(@.name == '" + tool + "')].description")).get(0);
      assertThat(d).as(tool).contains(MarkdownMcpTools.MARKDOWN_LINE)
          .contains("```mermaid").contains("```chart").contains("{1,3-5}").contains("get_markdown_guide");
    }
    String guide = ((List<String>) read(listed, "$.result.tools[?(@.name == 'get_markdown_guide')].description")).get(0);
    assertThat(guide).contains("topic").contains("CHART_INVALID");
    assertThat((List<Object>) read(listed, "$.result.tools[?(@.name == 'get_markdown_guide')].inputSchema.required[*]")).isEmpty();
    assertThat((List<String>) read(listed, "$.result.tools[?(@.name == 'get_markdown_guide')].inputSchema.properties.topic.type"))
        .containsExactly("string");
  }

  // ── 차트 검사 ─────────────────────────────────────────────────────────

  @Test
  void create_issue_는_틀린_차트를_CHART_INVALID_로_알리고_막지_않는다() throws Exception {
    Space s = space();
    McpTestClient mcp = client(s.curator());

    String good = mcp.call("create_issue", args("project", P, "type", "feature", "title", "맞는 차트", "description",
        "## 배경\n" + GOOD + "\n"));
    assertThat(codes(good)).noneMatch(c -> c.startsWith("CHART_"));
    assertThat((List<String>) read(good, "$.next")).noneMatch(n -> n.contains("CHART_INVALID"));

    String description = "## 배경\n왜\n\n" + BAD + "\n\n- 목록\n\n  ```chart\n  {\"type\":\"gauge\",\"value\":5,\"colour\":\"#fff\"}\n  ```\n";
    String created = mcp.call("create_issue", args("project", P, "type", "feature", "title", "틀린 차트", "description", description));
    int n = read(created, "$.summary.number");
    assertThat(codes(created)).contains("CHART_INVALID", "CHART_UNKNOWN_KEY");
    assertThat(message(created, "CHART_INVALID")).startsWith("1 번째 차트 블록(본문 4줄): series[0].data 길이가 labels 와 다름")
        .contains("get_markdown_guide(topic=chart)").doesNotContain("비밀이름");
    assertThat((List<String>) read(created, "$.checks[?(@.code == 'CHART_INVALID')].level")).containsOnly("WARN");
    assertThat(message(created, "CHART_UNKNOWN_KEY")).startsWith("2 번째 차트 블록(본문 10줄): 모르는 키 1개(items)");
    assertThat((List<List<String>>) read(created, "$.checks[?(@.code == 'CHART_UNKNOWN_KEY')].items")).containsExactly(List.of("colour"));
    assertThat((List<String>) read(created, "$.checks[?(@.code == 'CHART_UNKNOWN_KEY')].level")).containsOnly("INFO");
    // next 는 1~3줄 그대로 — 차트 고치기는 "checks 의 빠진 것" 줄에 붙거나 한 줄로 들어간다
    List<String> next = read(created, "$.next");
    assertThat(next).hasSizeBetween(1, 3).anyMatch(line -> line.contains("CHART_INVALID") && line.contains("get_markdown_guide"));
    // 막지 않는다 — 글은 그대로 저장
    assertThat((String) read(mcp.call("get_issue", args("number", n)), "$.description")).isEqualTo(description);
    // 읽기(get_issue)는 차트를 다시 검사하지 않는다(쓴 결과에서만)
    assertThat(codes(mcp.call("get_issue", args("number", n)))).noneMatch(c -> c.startsWith("CHART_"));
  }

  @Test
  void update_issue_는_설명_채운_절_함께_단_댓글의_차트를_각각_본다() throws Exception {
    Space s = space();
    McpTestClient mcp = client(s.curator());
    String created = mcp.call("create_issue", args("project", P, "type", "feature", "title", "고칠 차트", "description", "## 배경\n왜"));
    int n = read(created, "$.summary.number");
    int rv = read(created, "$.summary.rowVersion");

    String section = mcp.call("update_issue", args("number", n, "rowVersion", rv,
        "setSection", Map.of("heading", "처리 결과", "content", "- 바뀐 것\n\n" + BAD), "comment", "댓글 차트\n" + BAD.replace("bar", "pie")));
    assertThat((List<String>) read(section, "$.checks[?(@.code == 'CHART_INVALID')].message"))
        .anyMatch(m -> m.startsWith("1 번째 차트 블록(setSection 3줄): series[0].data 길이가 labels 와 다름"))
        .anyMatch(m -> m.startsWith("1 번째 차트 블록(댓글 2줄): data 이(가) 없음"));
    int rv2 = read(section, "$.summary.rowVersion");

    String fixed = mcp.call("update_issue", args("number", n, "rowVersion", rv2, "description", "## 배경\n" + GOOD));
    assertThat(codes(fixed)).noneMatch(c -> c.startsWith("CHART_"));
    // 이번에 쓰지 않은 글(앞서 채운 절은 덮였다) · 쓰지 않은 호출은 보지 않는다
    String status = mcp.call("update_issue", args("number", n, "rowVersion", read(fixed, "$.summary.rowVersion"), "title", "제목만"));
    assertThat(codes(status)).noneMatch(c -> c.startsWith("CHART_"));
  }

  @Test
  void 댓글_달기_고치기_결과에_checks_next_와_commentId_rowVersion() throws Exception {
    Space s = space();
    McpTestClient mcp = client(s.curator());
    int n = read(mcp.call("create_issue", args("project", P, "type", "bug", "title", "댓글 차트")), "$.summary.number");

    String good = mcp.call("add_comment", args("number", n, "text", "진행\n" + GOOD));
    assertThat((List<Object>) read(good, "$.checks")).isEmpty();
    assertThat((List<Object>) read(good, "$.next")).isEmpty();
    assertThat((Boolean) read(good, "$.commented")).isTrue();
    assertThat((String) read(good, "$.commentId")).isNotBlank();

    String bad = mcp.call("add_comment", args("number", n, "text", "결과\n\n" + BAD));
    String id = read(bad, "$.commentId");
    int rowVersion = read(bad, "$.rowVersion");
    assertThat(message(bad, "CHART_INVALID")).startsWith("1 번째 차트 블록(댓글 3줄): series[0].data 길이가 labels 와 다름")
        .doesNotContain("비밀이름");
    assertThat((List<String>) read(bad, "$.next")).singleElement().asString()
        .contains("update_comment(number=" + n + ", commentId=" + id + ", rowVersion=" + rowVersion);
    // commentId 는 방금 단 댓글 — list_comments 의 마지막 줄과 같다
    assertThat((List<String>) read(mcp.call("list_comments", args("number", n)), "$.items[*].id")).last().isEqualTo(id);

    // 고치기 — 맞게 고치면 checks · next 가 비고, 다시 틀리면 새 rowVersion 으로 알린다
    String fixed = mcp.call("update_comment", args("number", n, "commentId", id, "rowVersion", rowVersion, "text", "결과\n" + GOOD));
    assertThat((List<Object>) read(fixed, "$.checks")).isEmpty();
    assertThat((List<Object>) read(fixed, "$.next")).isEmpty();
    int rv2 = read(fixed, "$.rowVersion");
    String broken = mcp.call("update_comment", args("number", n, "commentId", id, "rowVersion", rv2,
        "text", "~~~ChArT\n{\"type\":\"gauge\",\"value\":1,}\n~~~"));
    assertThat(message(broken, "CHART_INVALID")).startsWith("1 번째 차트 블록(댓글 1줄): JSON 이 아님(블록 안 1줄");
    assertThat((List<String>) read(broken, "$.next")).singleElement().asString()
        .contains("rowVersion=" + read(broken, "$.rowVersion"));
  }
}
