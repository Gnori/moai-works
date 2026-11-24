package com.moai.works.api.mcp;

import static com.jayway.jsonpath.JsonPath.read;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import com.moai.works.api.support.ApiTest;
import com.moai.works.api.support.McpTestClient;
import com.moai.works.api.support.TestApi;
import com.moai.works.api.support.TestUser;

/**
 * update_issue 로 설명을 통째로 보내지 않고 끝내기 — setSection(절 채우기) · checkItems · uncheckItems 를 상태 · 댓글과 한 번에,
 * 설명 바뀜은 이력 · 판에 그대로, 맞지 않는 체크 글은 CHECK_ITEM_NOT_FOUND(아무것도 안 바뀜). 2026-11-24, 일감 #178.
 */
@ApiTest
class McpCompletionTest {

  private static final String P = "done";
  private static final String DESCRIPTION = """
      ## 배경
      MCP 로 끝내기를 쉽게.

      ## 수용 기준
      - [ ] 절을 채운다
      - [ ] 체크한다, 쉼표가 있어도

      ## 처리 결과 (선택)
      """;

  @Autowired
  TestApi api;
  @Autowired
  MockMvc mvc;

  private record Space(String ws, TestUser curator) {
  }

  private Space space() throws Exception {
    TestUser owner = api.user("owner");
    String ws = api.teamWorkspace(owner, "끝내기");
    TestUser curator = api.user("curator");
    api.join(ws, owner, curator, "curator");
    api.post(owner, "/workspaces/{ws}/projects", Map.of("key", P, "name", "끝내기", "visibility", "WORKSPACE"), ws)
        .andExpect(status().isCreated());
    return new Space(ws, curator);
  }

  private McpTestClient client(TestUser who) throws Exception {
    return new McpTestClient(mvc, TestApi.read(api.post(who, "/me/mcp-keys", Map.of("name", "끝내기 키"))
        .andExpect(status().isCreated()), "$.key"));
  }

  private static Map<String, Object> args(Object... kv) {
    Map<String, Object> m = new HashMap<>();
    for (int i = 0; i < kv.length; i += 2) {
      m.put((String) kv[i], kv[i + 1]);
    }
    return m;
  }

  @Test
  void 상태_절_체크_댓글을_한_번에_하고_설명_바뀜은_이력과_판에_남는다() throws Exception {
    Space s = space();
    McpTestClient mcp = client(s.curator());
    String created = mcp.call("create_issue", args("project", P, "type", "feature", "title", "끝내기", "description", DESCRIPTION,
        "status", "TESTING"));
    int n = read(created, "$.number");
    int revision = read(created, "$.revisionNo");

    String done = mcp.call("update_issue", args("number", n, "rowVersion", read(created, "$.rowVersion"), "status", "DONE",
        "comment", "끝냈습니다", "setSection", Map.of("heading", "처리 결과", "content", "- 바뀐 것: setSection\n- 커밋: abc123"),
        "checkItems", "all"));
    assertThat((String) read(done, "$.status")).isEqualTo("DONE");
    String description = read(done, "$.description");
    assertThat(description).contains("- [x] 절을 채운다").contains("- [x] 체크한다, 쉼표가 있어도")
        .endsWith("## 처리 결과 (선택)\n\n- 바뀐 것: setSection\n- 커밋: abc123").startsWith("## 배경\nMCP 로 끝내기를 쉽게.");
    assertThat((String) read(done, "$.descriptionEdit.section.action")).isEqualTo("REPLACED");
    assertThat((List<String>) read(done, "$.descriptionEdit.checked")).containsExactly("절을 채운다", "체크한다, 쉼표가 있어도");
    assertThat((Object) read(done, "$.templateCheck")).isNotNull();
    // 한 이력 줄에 상태 · 설명 바뀜과 댓글, 새 판 하나
    assertThat((List<String>) read(done, "$.journal[-1].changes[*].field")).contains("status", "description");
    assertThat((String) read(done, "$.journal[-1].notes")).isEqualTo("끝냈습니다");
    assertThat((Integer) read(done, "$.revisionNo")).isEqualTo(revision + 1);
    api.get(s.curator(), "/workspaces/{ws}/issues/{n}/revisions/{no}/diff", s.ws(), n, revision + 1)
        .andExpect(status().isOk()).andExpect(jsonPath("$.added").value(org.hamcrest.Matchers.greaterThan(0)));

    // 체크 풀기 — 글 하나(쉼표를 나누지 않는다)
    String back = mcp.call("update_issue", args("number", n, "rowVersion", read(done, "$.rowVersion"),
        "uncheckItems", "체크한다, 쉼표가 있어도"));
    assertThat((String) read(back, "$.description")).contains("- [ ] 체크한다, 쉼표가 있어도").contains("- [x] 절을 채운다");
    assertThat((List<String>) read(back, "$.descriptionEdit.unchecked")).containsExactly("체크한다, 쉼표가 있어도");
  }

  @Test
  void 맞지_않는_체크_글은_CHECK_ITEM_NOT_FOUND_이고_아무것도_바뀌지_않는다() throws Exception {
    Space s = space();
    McpTestClient mcp = client(s.curator());
    String created = mcp.call("create_issue", args("project", P, "type", "feature", "title", "못 찾음", "description", DESCRIPTION));
    int n = read(created, "$.number");
    int rowVersion = read(created, "$.rowVersion");

    String missing = mcp.call("update_issue", args("number", n, "rowVersion", rowVersion, "status", "IN_PROGRESS",
        "setSection", Map.of("heading", "처리 결과", "content", "x"), "checkItems", List.of("절을 채운다", "없는 항목")));
    assertThat((String) read(missing, "$.error.code")).isEqualTo("CHECK_ITEM_NOT_FOUND");
    assertThat((Integer) read(missing, "$.error.status")).isEqualTo(400);
    assertThat((String) read(missing, "$.error.message")).contains("없는 항목");
    String now = mcp.call("get_issue", args("number", n));
    assertThat((Integer) read(now, "$.rowVersion")).isEqualTo(rowVersion);
    assertThat((String) read(now, "$.status")).isEqualTo("NEW");
    assertThat((String) read(now, "$.description")).isEqualTo(read(created, "$.description"));

    // 낡은 rowVersion 은 체크 글보다 먼저 CONCURRENT_UPDATE
    mcp.call("add_comment", args("number", n, "text", "rowVersion 은 그대로"));
    String moved = mcp.call("update_issue", args("number", n, "rowVersion", rowVersion, "title", "바뀜"));
    assertThat((String) read(mcp.call("update_issue", args("number", n, "rowVersion", rowVersion, "checkItems", "없는 항목")),
        "$.error.code")).isEqualTo("CONCURRENT_UPDATE");

    // description 을 함께 주면 그것 먼저, 그다음 절 · 체크
    String both = mcp.call("update_issue", args("number", n, "rowVersion", read(moved, "$.rowVersion"),
        "description", "## 새 설명\n- [ ] 새 항목", "checkItems", "새 항목", "setSection", Map.of("heading", "결론", "content", "됨")));
    assertThat((String) read(both, "$.description")).isEqualTo("## 새 설명\n- [x] 새 항목\n\n## 결론\n\n됨");
    assertThat((String) read(both, "$.descriptionEdit.section.action")).isEqualTo("APPENDED");
  }

  @Test
  void 입력_틀이_setSection_은_객체_checkItems_는_배열이나_글_하나로_알린다() throws Exception {
    Space s = space();
    McpTestClient mcp = client(s.curator());
    String tools = mcp.toolsJson();
    String base = "$.result.tools[?(@.name == 'update_issue')].inputSchema.properties.";
    assertThat((List<String>) read(tools, base + "setSection.type")).containsExactly("object");
    assertThat((List<String>) read(tools, base + "setSection.properties.heading.description")).singleElement().asString()
        .contains("절 제목");
    assertThat((List<List<String>>) read(tools, base + "setSection.required")).containsExactly(List.of("heading"));
    assertThat((List<String>) read(tools, base + "checkItems.anyOf[*].type")).containsExactly("array", "string");
    // 객체가 아니면 같은 오류 모양
    int n = read(mcp.call("create_issue", args("project", P, "type", "bug", "title", "꼴")), "$.number");
    String wrong = mcp.call("update_issue", args("number", n, "rowVersion", 0, "setSection", "처리 결과"));
    assertThat((String) read(wrong, "$.error.code")).isEqualTo("INVALID_ARGUMENT");
    assertThat((String) read(wrong, "$.error.field")).isEqualTo("setSection");
  }
}
