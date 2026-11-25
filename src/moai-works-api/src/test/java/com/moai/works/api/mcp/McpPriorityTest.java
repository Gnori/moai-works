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
 * MCP 의 우선순위 (2026-11-26, 일감 #302) — create_issue · update_issue 의 priority, summary 에 늘 priority(보통도),
 * search_issues 의 priority 거르기 · sort=priority · 줄의 priority, 다음 일감 길잡이의 sort=priority, 긴급 알림은 키 주인에게도.
 */
@ApiTest
class McpPriorityTest {

  private static final String P = "prio";

  @Autowired
  TestApi api;
  @Autowired
  MockMvc mvc;

  private record Space(String ws, TestUser owner, TestUser curator) {
  }

  private Space space() throws Exception {
    TestUser owner = api.user("owner");
    String ws = api.teamWorkspace(owner, "MCP 우선순위");
    TestUser curator = api.user("curator");
    api.join(ws, owner, curator, "curator");
    api.post(owner, "/workspaces/{ws}/projects", Map.of("key", P, "name", "우선순위", "visibility", "WORKSPACE"), ws)
        .andExpect(status().isCreated());
    return new Space(ws, owner, curator);
  }

  private McpTestClient client(TestUser who) throws Exception {
    String key = TestApi.read(api.post(who, "/me/mcp-keys", Map.of("name", "에이전트 키")).andExpect(status().isCreated()), "$.key");
    return new McpTestClient(mvc, key);
  }

  private static Map<String, Object> args(Object... kv) {
    Map<String, Object> m = new HashMap<>();
    for (int i = 0; i < kv.length; i += 2) {
      m.put((String) kv[i], kv[i + 1]);
    }
    return m;
  }

  @Test
  void 만들기_고치기의_priority_와_summary_의_우선순위() throws Exception {
    Space s = space();
    McpTestClient mcp = client(s.curator());
    // 비우면 보통 — summary 에 늘 싣는다(빈 값이 아니다)
    String plain = mcp.call("create_issue", args("project", P, "type", "feature", "title", "보통 일"));
    assertThat((String) read(plain, "$.summary.priority")).isEqualTo("NORMAL");

    String urgent = mcp.call("create_issue", args("project", P, "type", "bug", "title", "급한 버그", "priority", "urgent"));
    int n = read(urgent, "$.summary.number");
    assertThat((String) read(urgent, "$.summary.priority")).isEqualTo("URGENT");
    // 본문에서 다시 싣지 않는다(summary 에 있다)
    assertThat((Map<String, Object>) read(urgent, "$")).doesNotContainKey("priority");

    String lowered = mcp.call("update_issue", args("project", P, "number", n, "rowVersion", read(urgent, "$.summary.rowVersion"),
        "priority", "HIGH"));
    assertThat((String) read(lowered, "$.summary.priority")).isEqualTo("HIGH");
    String bad = mcp.call("update_issue", args("project", P, "number", n, "rowVersion", read(lowered, "$.summary.rowVersion"),
        "priority", "SOON"));
    assertThat((String) read(bad, "$.error.code")).isEqualTo("INVALID_ISSUE_PRIORITY");
    // 이력에 우선순위가 남는다(웹과 같은 서비스)
    api.get(s.owner(), "/workspaces/{ws}/issues/{n}", s.ws(), n)
        .andExpect(jsonPath("$.journal[1].changes[0].field").value("priority"))
        .andExpect(jsonPath("$.journal[1].changes[0].before").value("URGENT"))
        .andExpect(jsonPath("$.journal[1].changes[0].after").value("HIGH"))
        .andExpect(jsonPath("$.journal[1].via").value("MCP"));
  }

  @Test
  void 찾기는_우선순위로_거르고_긴급부터_정렬하며_줄마다_우선순위를_싣는다() throws Exception {
    Space s = space();
    McpTestClient mcp = client(s.curator());
    int low = read(mcp.call("create_issue", args("project", P, "type", "feature", "title", "낮음", "priority", "LOW")),
        "$.summary.number");
    int urgent = read(mcp.call("create_issue", args("project", P, "type", "feature", "title", "긴급", "priority", "URGENT")),
        "$.summary.number");
    int normal = read(mcp.call("create_issue", args("project", P, "type", "feature", "title", "보통")), "$.summary.number");
    int high = read(mcp.call("create_issue", args("project", P, "type", "feature", "title", "높음", "priority", "HIGH")),
        "$.summary.number");

    String sorted = mcp.call("search_issues", args("project", P, "status", "open", "sort", "priority"));
    assertThat((List<Integer>) read(sorted, "$.items[*].number")).containsExactly(urgent, high, normal, low);
    assertThat((List<String>) read(sorted, "$.items[*].priority")).containsExactly("URGENT", "HIGH", "NORMAL", "LOW");

    String picked = mcp.call("search_issues", args("project", P, "priority", "URGENT,high"));
    assertThat((List<Integer>) read(picked, "$.items[*].number")).containsExactly(high, urgent);
    // 글 배열도 된다(status · module 과 같다)
    String array = mcp.call("search_issues", args("project", P, "priority", List.of("LOW", "NORMAL")));
    assertThat((List<Integer>) read(array, "$.items[*].number")).containsExactly(normal, low);
    // 프로젝트 없이(워크스페이스 목록)도 같다
    String ws = mcp.call("search_issues", args("workspace", s.ws(), "priority", "LOW"));
    assertThat((List<Integer>) read(ws, "$.items[*].number")).containsExactly(low);
    assertThat((String) read(mcp.call("search_issues", args("workspace", s.ws(), "priority", "SOON")), "$.error.code"))
        .isEqualTo("INVALID_ISSUE_PRIORITY");
  }

  @Test
  void 다음_일감_길잡이는_우선순위순으로_찾게_한다() throws Exception {
    Space s = space();
    McpTestClient mcp = client(s.curator());
    String created = mcp.call("create_issue", args("project", P, "type", "feature", "title", "끝낼 일"));
    int n = read(created, "$.summary.number");
    String done = mcp.call("update_issue", args("project", P, "number", n, "rowVersion", read(created, "$.summary.rowVersion"),
        "status", "DONE"));
    assertThat(String.join("\n", (List<String>) read(done, "$.next")))
        .contains("search_issues(agentSession=내 세션, status=open, sort=priority)");
    // 도구 정의에 priority 인자 · sort=priority · 핵심 규칙의 다음 일감
    String tools = mcp.toolsJson();
    assertThat(tools).contains("URGENT 먼저").contains("LOW · NORMAL(기본) · HIGH · URGENT");
    assertThat(McpInstructions.ISSUE_RULES).contains("sort=priority");
  }

  @Test
  void MCP_로_긴급으로_올리면_키_주인인_관리자에게도_알린다() throws Exception {
    // 사람의 키로 Agent 가 한 일은 키 주인 자신에게도 알린다(일감 #286 규칙) — 긴급도 같다
    Space s = space();
    McpTestClient mcp = client(s.curator());
    String created = mcp.call("create_issue", args("project", P, "type", "feature", "title", "Agent 가 올린 일"));
    int n = read(created, "$.summary.number");
    api.post(s.curator(), "/me/notifications/read-all", Map.of()).andExpect(status().is2xxSuccessful());
    mcp.call("update_issue", args("project", P, "number", n, "rowVersion", read(created, "$.summary.rowVersion"),
        "priority", "URGENT"));
    api.get(s.curator(), "/me/notifications?unread=true")
        .andExpect(jsonPath("$.items[?(@.kind == 'issue.priority_urgent')].params.via").value("MCP"))
        .andExpect(jsonPath("$.items[?(@.kind == 'issue.priority_urgent')].params.from").value("NORMAL"));
  }
}
