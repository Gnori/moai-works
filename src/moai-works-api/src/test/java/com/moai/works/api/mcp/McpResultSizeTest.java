package com.moai.works.api.mcp;

import static com.jayway.jsonpath.JsonPath.read;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
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

import tools.jackson.databind.json.JsonMapper;

/**
 * MCP 결과 · 도구 정의의 크기(일감 #282) — Agent 는 단계마다 도구 정의와 결과를 다시 읽는다. 세 가지를 잰다:
 * 일감 입구의 tools/list(도구 정의 JSON) · 설명을 여러 번 고치고 댓글이 많은 일감의 get_issue · 30줄 search_issues.
 * 줄이기 전 크기({@link #TOOLS_BEFORE} 등)는 같은 데이터로 바꾸기 전 코드에서 잰 값이다. 잰 값은 표준 출력의 [mcp-size] 줄.
 */
@ApiTest
class McpResultSizeTest {

  private static final String P = "size";
  private static final JsonMapper JSON = new JsonMapper();

  /** 줄이기 전(2026-11-25, 같은 시험 데이터)의 크기 — 글자 수 */
  static final int TOOLS_BEFORE = 38_872;
  static final int ISSUE_BEFORE = 59_679;
  static final int SEARCH_BEFORE = 22_028;

  @Autowired
  TestApi api;
  @Autowired
  MockMvc mvc;

  private static Map<String, Object> args(Object... kv) {
    Map<String, Object> m = new HashMap<>();
    for (int i = 0; i < kv.length; i += 2) {
      m.put((String) kv[i], kv[i + 1]);
    }
    return m;
  }

  /** 기능 일감 설명 — 절 다섯 · 체크 목록 여섯, 2,000자 남짓 */
  private static String description() {
    StringBuilder b = new StringBuilder();
    b.append("## 배경\n\n");
    for (int i = 0; i < 6; i++) {
      b.append("에이전트가 일감을 읽을 때마다 결과가 커서 맥락을 많이 쓴다. 단계마다 같은 값을 다시 읽는다(").append(i).append(").\n");
    }
    b.append("\n## 요구사항\n\n");
    for (int i = 0; i < 6; i++) {
      b.append("- 요구 ").append(i).append(": 빈 칸은 싣지 않고, 사람은 아이디로, 모듈은 key 로 줄인다. 뜻은 그대로 둔다.\n");
    }
    b.append("\n## 수용 기준\n\n");
    for (int i = 0; i < 6; i++) {
      b.append("- [ ] 기준 ").append(i).append(": 결과 크기가 줄고 시험이 모두 통과한다\n");
    }
    b.append("\n## 설계\n\n");
    for (int i = 0; i < 8; i++) {
      b.append("가운데에서 한 번에 줄이고, 일감 결과는 도구에서 모양을 정한다. REST 응답은 바꾸지 않는다(").append(i).append(").\n");
    }
    b.append("\n## 처리 결과\n\n- (끝날 때 채운다)\n");
    return b.toString();
  }

  @Test
  void 도구_정의_바쁜_일감_30줄_찾기의_크기() throws Exception {
    TestUser owner = api.user("owner");
    String ws = api.teamWorkspace(owner, "크기");
    TestUser curator = api.user("curator");
    TestUser member = api.user("member");
    api.join(ws, owner, curator, "curator");
    api.join(ws, owner, member, "member");
    api.post(owner, "/workspaces/{ws}/projects", Map.of("key", P, "name", "크기 재기", "visibility", "WORKSPACE"), ws)
        .andExpect(status().isCreated());
    for (String m : List.of("api", "web-frontend", "docs")) {
      api.post(owner, "/workspaces/{ws}/projects/{p}/modules", Map.of("key", m, "name", m.toUpperCase()), ws, P)
          .andExpect(status().isCreated());
    }
    String key = TestApi.read(api.post(curator, "/me/mcp-keys", Map.of("name", "크기 키")).andExpect(status().isCreated()), "$.key");
    McpTestClient mcp = new McpTestClient(mvc, key);

    // 1. 도구 정의
    List<Object> tools = read(mcp.toolsJson(), "$.result.tools");
    int toolsSize = JSON.writeValueAsString(tools).length();
    List<String> perTool = new ArrayList<>();
    for (Object t : tools) {
      perTool.add(read(t, "$.name") + "=" + JSON.writeValueAsString(t).length());
    }

    // 2. 바쁜 일감 — 설명을 고칠 때마다 이력이 전 · 후 글을 품는다
    String created = mcp.call("create_issue", args("project", P, "type", "feature", "title", "결과 크기 줄이기", "description",
        description(), "assignee", member.username(), "dueDate", "2026-12-31", "agentSession", "mcp-trim", "modules",
        List.of("api", "docs")));
    int n = read(created, "$.summary.number");
    int rv = read(created, "$.summary.rowVersion");
    rv = read(mcp.call("update_issue", args("number", n, "rowVersion", rv, "status", "IN_PROGRESS", "startDate", "2026-11-25",
        "plannedEndDate", "2026-11-28", "estimatedHours", 6, "comment", "시작합니다 — 먼저 크기를 잰다")), "$.summary.rowVersion");
    for (int i = 0; i < 6; i++) {
      rv = read(mcp.call("update_issue", args("number", n, "rowVersion", rv, "setSection", Map.of("heading", "처리 결과",
          "content", "- 단계 " + i + ": 바꾼 것을 적는다\n- 커밋: abc" + i, "mode", "append"))), "$.summary.rowVersion");
      rv = read(mcp.call("update_issue", args("number", n, "rowVersion", rv, "checkItems", List.of("기준 " + i))),
          "$.summary.rowVersion");
      mcp.call("add_comment", args("number", n, "text", "진행 " + i + " — 결과 모양을 바꾸고 시험을 고쳤다. 다음은 설명 줄이기. "
          + "확인한 것: tools/list 크기, get_issue 크기, 찾기 30줄 크기. 남은 것: 문서."));
      mcp.call("log_work", args("number", n, "hours", 0.5, "note", "구현 " + i + " — 근거: 진행 10:00 → 10:30(30분)"));
    }
    rv = read(mcp.call("update_issue", args("number", n, "rowVersion", rv, "setSection", Map.of("heading", "처리 결과",
        "content", "- 바뀐 것: 결과 · 정의를 줄였다\n- 커밋: def"))), "$.summary.rowVersion");
    mcp.call("update_issue", args("number", n, "rowVersion", rv, "status", "TESTING", "comment", "시험으로"));
    String issue = mcp.call("get_issue", args("number", n));

    // 3. 30줄 찾기 — 줄마다 관리자 · 담당자 · 모듈 · 세션 · 기한
    for (int i = 0; i < 30; i++) {
      mcp.call("create_issue", args("project", P, "type", i % 2 == 0 ? "bug" : "feature", "title", "찾기 줄 " + i + " — 목록 크기 재기",
          "assignee", i % 3 == 0 ? member.username() : "me", "dueDate", "2026-12-" + (10 + i % 15), "agentSession", "mcp-trim",
          "modules", i % 2 == 0 ? List.of("api") : List.of("web-frontend", "docs")));
    }
    String search = mcp.call("search_issues", args("project", P, "size", 30));
    // 이력 모든 줄 — 설명은 글자 수(include journal) · 설명 글까지(include journal, journalDescription = 예전 이력의 글)
    String issueAll = mcp.call("get_issue", args("number", n, "include", List.of("journal")));
    String issueText = mcp.call("get_issue", args("number", n, "include", List.of("journal", "journalDescription")));

    System.out.println("[mcp-size] tools/list(/mcp/issue) " + tools.size() + "개 = " + toolsSize + "자(전 " + TOOLS_BEFORE + ")");
    System.out.println("[mcp-size] 도구별 " + perTool);
    System.out.println("[mcp-size] get_issue(이력 " + read(issue, "$.journalCount") + "줄 — 설명 고침 14 · 댓글 8 · 작업 시간 6) 기본 "
        + issue.length() + "자 · 이력 모두 " + issueAll.length() + "자 · 이력 모두 + 설명 글 " + issueText.length() + "자(전 "
        + ISSUE_BEFORE + ")");
    System.out.println("[mcp-size] search_issues 30줄 = " + search.length() + "자(전 " + SEARCH_BEFORE + ")");

    // 줄인 만큼 — 도구 정의 25% 넘게, 바쁜 일감 · 찾기는 크게
    assertThat(toolsSize).isLessThanOrEqualTo(TOOLS_BEFORE * 3 / 4);
    assertThat(issue.length()).isLessThanOrEqualTo(ISSUE_BEFORE / 5);
    assertThat(issueAll.length()).isLessThan(issueText.length() / 3);
    assertThat(search.length()).isLessThanOrEqualTo(SEARCH_BEFORE * 3 / 5);
  }
}
