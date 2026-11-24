package com.moai.works.api.mcp;

import static com.jayway.jsonpath.JsonPath.read;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.moai.works.api.support.ApiTest;
import com.moai.works.api.support.McpTestClient;
import com.moai.works.api.support.TestApi;
import com.moai.works.api.support.TestUser;

/**
 * MCP 인자의 꼴 — 틀리면 다른 도구 오류와 같은 JSON(400 INVALID_ARGUMENT · 한국어 message · field), 같은 뜻이면 같은 꼴
 * (일감 번호는 정수 · 숫자 글, 여러 값은 쉼표 글 · 글 배열). 2026-11-24, 일감 #177.
 */
@ApiTest
class McpArgumentFormTest {

  private static final String P = "form";

  @Autowired
  TestApi api;
  @Autowired
  MockMvc mvc;
  @Autowired
  JdbcTemplate jdbc;

  private record Space(String ws, TestUser curator) {
  }

  private Space space() throws Exception {
    TestUser owner = api.user("owner");
    String ws = api.teamWorkspace(owner, "인자 꼴");
    TestUser curator = api.user("curator");
    api.join(ws, owner, curator, "curator");
    api.post(owner, "/workspaces/{ws}/projects", Map.of("key", P, "name", "인자 꼴", "visibility", "WORKSPACE"), ws)
        .andExpect(status().isCreated());
    for (String m : List.of("api", "docs", "infra")) {
      api.post(owner, "/workspaces/{ws}/projects/{p}/modules", Map.of("key", m, "name", m), ws, P)
          .andExpect(status().isCreated());
    }
    return new Space(ws, curator);
  }

  private record Key(UUID id, McpTestClient client) {
  }

  private Key key(TestUser who) throws Exception {
    var issued = api.post(who, "/me/mcp-keys", Map.of("name", "꼴 키")).andExpect(status().isCreated());
    return new Key(UUID.fromString(TestApi.read(issued, "$.view.id")), new McpTestClient(mvc, TestApi.read(issued, "$.key")));
  }

  private static Map<String, Object> args(Object... kv) {
    Map<String, Object> m = new HashMap<>();
    for (int i = 0; i < kv.length; i += 2) {
      m.put((String) kv[i], kv[i + 1]);
    }
    return m;
  }

  private static void invalid(String result, String field) {
    assertThat((Integer) read(result, "$.error.status")).isEqualTo(400);
    assertThat((String) read(result, "$.error.code")).isEqualTo("INVALID_ARGUMENT");
    assertThat((String) read(result, "$.error.field")).isEqualTo(field);
    assertThat((String) read(result, "$.error.message")).matches(".*[가-힣].*");
  }

  @Test
  void 꼴이_틀린_인자는_평문이_아니라_같은_오류_모양에_field_로_온다() throws Exception {
    Space s = space();
    Key k = key(s.curator());
    McpTestClient mcp = k.client();
    // 예전에는 "Tool (search_issues) input validation failed: …" 평문이었다
    invalid(mcp.call("search_issues", args("project", P, "overdue", "yes")), "overdue");
    invalid(mcp.call("search_issues", args("project", P, "module", List.of("api", 3))), "module");
    invalid(mcp.call("search_issues", args("project", P, "page", "다음")), "page");
    invalid(mcp.call("get_issue", args("number", "열두")), "number");
    invalid(mcp.call("get_issue", args("number", 1.5)), "number");
    invalid(mcp.call("get_issue", args("number", 1, "bogus", true)), "bogus");
    invalid(mcp.call("get_issue", args()), "number");
    invalid(mcp.call("create_issue", args("project", P, "type", "bug", "title", "x", "estimatedHours", "많이")), "estimatedHours");
    invalid(mcp.call("log_work", args("number", 1, "workDate", "2026-11-24", "hours", 1, "note", List.of("a"))), "note");
    invalid(mcp.call("get_issue", args("number", 99999999999L)), "number");

    // 접근 기록에도 도구 호출의 오류(400 INVALID_ARGUMENT)로 남는다
    List<Map<String, Object>> rows = jdbc.queryForList(
        "SELECT * FROM mcp_access_log WHERE key_id = ? AND tool = 'get_issue' ORDER BY occurred_at, id", k.id());
    assertThat(rows).isNotEmpty().allSatisfy(r -> {
      assertThat(r.get("outcome")).isEqualTo("ERROR");
      assertThat(r.get("http_status")).isEqualTo(400);
      assertThat(r.get("error_code")).isEqualTo("INVALID_ARGUMENT");
    });
  }

  @Test
  void 선택_인자의_null_은_넣지_않은_것과_같다() throws Exception {
    Space s = space();
    McpTestClient mcp = key(s.curator()).client();
    Map<String, Object> a = args("project", P, "type", "bug", "title", "null 인자");
    a.put("assignee", null);
    a.put("parent", null);
    a.put("modules", null);
    int n = read(mcp.call("create_issue", a), "$.number");
    Map<String, Object> g = args("number", n);
    g.put("project", null);
    assertThat((Integer) read(mcp.call("get_issue", g), "$.number")).isEqualTo(n);
    // 필수 인자의 null 은 빠진 것과 같다
    Map<String, Object> missing = args("project", P);
    missing.put("number", null);
    invalid(mcp.call("get_issue", missing), "number");
  }

  @Test
  void 일감_번호는_어느_도구에서든_정수도_숫자_글도_된다() throws Exception {
    Space s = space();
    McpTestClient mcp = key(s.curator()).client();
    int parent = read(mcp.call("create_issue", args("project", P, "type", "feature", "title", "상위")), "$.number");
    // create_issue.parent 는 정수 칸 — "#번호" 도 된다
    String child = mcp.call("create_issue", args("project", P, "type", "feature", "title", "하위", "parent", "#" + parent));
    int c = read(child, "$.number");
    assertThat((Integer) read(child, "$.parent.number")).isEqualTo(parent);
    // number 를 글로
    assertThat((String) read(mcp.call("get_issue", args("number", String.valueOf(c))), "$.title")).isEqualTo("하위");
    assertThat((String) read(mcp.call("get_issue", args("number", " #" + c + " ")), "$.title")).isEqualTo("하위");
    // search_issues.parent 는 글 칸(top 도 받는다) — 정수도 된다
    List<Integer> byInt = read(mcp.call("search_issues", args("project", P, "parent", parent)), "$.items[*].number");
    List<Integer> byText = read(mcp.call("search_issues", args("project", P, "parent", "#" + parent)), "$.items[*].number");
    assertThat(byInt).containsExactly(c).isEqualTo(byText);
    // link_issues.target 도
    String linked = mcp.call("link_issues", args("number", parent, "target", String.valueOf(c), "type", "RELATES"));
    assertThat((List<Integer>) read(linked, "$.links[*].issue.number")).contains(c);
    // update_issue 의 parent 를 글로(비우기는 clear)
    int rowVersion = read(mcp.call("get_issue", args("number", c)), "$.rowVersion");
    String cleared = mcp.call("update_issue", args("number", "#" + c, "rowVersion", rowVersion, "clear", List.of("parent")));
    assertThat((Object) read(cleared, "$.parent")).isNull();
  }

  @Test
  void 여러_값은_쉼표_글도_글_배열도_된다() throws Exception {
    Space s = space();
    McpTestClient mcp = key(s.curator()).client();
    int a = read(mcp.call("create_issue", args("project", P, "type", "bug", "title", "api 버그", "modules", "api, docs")), "$.number");
    int b = read(mcp.call("create_issue", args("project", P, "type", "feature", "title", "infra 일",
        "modules", List.of("infra"))), "$.number");
    read(mcp.call("create_issue", args("project", P, "type", "research", "title", "모듈 없음")), "$.number");
    assertThat((List<String>) read(mcp.call("get_issue", args("number", a)), "$.modules[*].key")).containsExactly("api", "docs");

    List<Integer> csv = read(mcp.call("search_issues", args("project", P, "module", "api,infra", "sort", "number")),
        "$.items[*].number");
    List<Integer> array = read(mcp.call("search_issues", args("project", P, "module", List.of("api", "infra"), "sort", "number")),
        "$.items[*].number");
    assertThat(csv).containsExactly(a, b).isEqualTo(array);
    assertThat((List<Integer>) read(mcp.call("search_issues", args("project", List.of(P), "type", List.of("bug", "feature"),
        "status", List.of("NEW"), "sort", "number")), "$.items[*].number")).containsExactly(a, b);
    // 빈 배열 · 빈 글은 넣지 않은 것과 같다(거르지 않는다)
    assertThat((Integer) read(mcp.call("search_issues", args("project", P, "module", List.of())), "$.total")).isEqualTo(3);

    // update_issue.modules 를 쉼표 글로, clear 를 배열로
    int rowVersion = read(mcp.call("get_issue", args("number", b)), "$.rowVersion");
    String updated = mcp.call("update_issue", args("number", b, "rowVersion", rowVersion, "modules", "docs,api"));
    assertThat((List<String>) read(updated, "$.modules[*].key")).containsExactlyInAnyOrder("api", "docs");
    String cleared = mcp.call("update_issue", args("number", b, "rowVersion", read(updated, "$.rowVersion"),
        "clear", List.of("modules", "dueDate")));
    assertThat((List<String>) read(cleared, "$.modules")).isEmpty();
  }

  @Test
  void 도구_목록의_입력_틀이_받는_꼴을_그대로_알린다() throws Exception {
    Space s = space();
    String tools = key(s.curator()).client().toolsJson();
    assertThat((List<String>) read(tools, "$.result.tools[?(@.name == 'get_issue')].inputSchema.properties.number.anyOf[*].type"))
        .containsExactly("integer", "string");
    assertThat((List<String>) read(tools,
        "$.result.tools[?(@.name == 'search_issues')].inputSchema.properties.parent.anyOf[*].type"))
        .containsExactly("string", "integer");
    assertThat((List<String>) read(tools,
        "$.result.tools[?(@.name == 'search_issues')].inputSchema.properties.module.anyOf[*].type"))
        .containsExactly("string", "array");
    assertThat((List<String>) read(tools,
        "$.result.tools[?(@.name == 'update_issue')].inputSchema.properties.modules.anyOf[*].type"))
        .containsExactly("array", "string");
    // 넓히지 않은 칸은 그대로
    assertThat((List<String>) read(tools,
        "$.result.tools[?(@.name == 'update_issue')].inputSchema.properties.rowVersion.type")).containsExactly("integer");
    assertThat((List<String>) read(tools,
        "$.result.tools[?(@.name == 'create_issue')].inputSchema.properties.type.type")).containsExactly("string");
  }
}
