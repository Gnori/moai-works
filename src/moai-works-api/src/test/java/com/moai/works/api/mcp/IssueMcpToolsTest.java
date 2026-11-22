package com.moai.works.api.mcp;

import static com.jayway.jsonpath.JsonPath.read;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;
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
 * 일감 MCP 도구 — REST 와 같은 서비스 · 같은 판정, 한 사람은 키 주인 · 경로 MCP, 찾기 · 쪽 나누기 · 다음 쪽 (2026-11-22).
 */
@ApiTest
class IssueMcpToolsTest {

  private static final String P = "agent";

  @Autowired
  TestApi api;
  @Autowired
  MockMvc mvc;

  private record Space(String ws, TestUser owner, TestUser curator, TestUser member) {
  }

  private Space space() throws Exception {
    TestUser owner = api.user("owner");
    String ws = api.teamWorkspace(owner, "MCP 일감");
    TestUser curator = api.user("curator");
    TestUser member = api.user("member");
    api.join(ws, owner, curator, "curator");
    api.join(ws, owner, member, "member");
    api.post(owner, "/workspaces/{ws}/projects", Map.of("key", P, "name", "에이전트", "visibility", "WORKSPACE"), ws)
        .andExpect(status().isCreated());
    return new Space(ws, owner, curator, member);
  }

  private McpTestClient client(TestUser who, String ws) throws Exception {
    String key = TestApi.read(api.post(who, "/workspaces/{ws}/mcp-keys", Map.of("name", "에이전트 키"), ws)
        .andExpect(status().isCreated()), "$.key");
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
  void 도구_목록() throws Exception {
    Space s = space();
    assertThat(client(s.curator(), s.ws()).tools()).contains("list_projects", "list_issue_types", "search_issues", "get_issue",
        "create_issue", "update_issue", "add_comment", "log_work", "link_issues", "delete_issue");
  }

  @Test
  void 지우기는_웹과_같은_권한에_이유가_필수이고_감사에_경로와_이유가_남는다() throws Exception {
    Space s = space();
    McpTestClient curator = client(s.curator(), s.ws());
    int n = read(curator.call("create_issue", args("project", P, "type", "bug", "title", "지울 것")), "$.number");
    assertThat((String) read(client(s.member(), s.ws()).call("delete_issue", args("project", P, "number", n, "reason", "x")),
        "$.error.code")).isEqualTo("PERMISSION_REQUIRED");
    // 지우기 권한(issue.delete)은 기본 역할로는 소유자 · 관리자
    McpTestClient owner = client(s.owner(), s.ws());
    assertThat((String) read(owner.call("delete_issue", args("project", P, "number", n, "reason", " ")), "$.error.code"))
        .isEqualTo("DELETE_REASON_REQUIRED");
    assertThat((Boolean) read(owner.call("delete_issue", args("project", P, "number", n, "reason", "중복 등록")), "$.deleted"))
        .isTrue();
    api.get(s.owner(), "/workspaces/{ws}/projects/{p}/issues/{n}", s.ws(), P, n).andExpect(status().isNotFound());
    api.get(s.owner(), "/workspaces/{ws}/audit?action=issue.delete", s.ws())
        .andExpect(jsonPath("$[0].details").value(org.hamcrest.Matchers.allOf(
            org.hamcrest.Matchers.containsString("MCP"), org.hamcrest.Matchers.containsString("중복 등록"))));
  }

  @Test
  void 만들고_고치고_댓글_작업_시간까지_한_사람은_키_주인이고_경로는_MCP() throws Exception {
    Space s = space();
    McpTestClient mcp = client(s.curator(), s.ws());
    String created = mcp.call("create_issue", args("project", P, "type", "feature", "title", "[FR-X-001] 첨부파일",
        "description", "## 수용 기준\n- [ ] 올린다", "assignee", s.member().username(), "estimatedHours", 6));
    int n = read(created, "$.number");
    assertThat((String) read(created, "$.assignee.username")).isEqualTo(s.member().username());
    assertThat((String) read(created, "$.manager.id")).isEqualTo(s.curator().id().toString());
    int rowVersion = read(created, "$.rowVersion");

    String updated = mcp.call("update_issue", args("project", P, "number", n, "rowVersion", rowVersion,
        "status", "in_progress", "dueDate", "2026-12-31", "comment", "시작합니다"));
    assertThat((String) read(updated, "$.status")).isEqualTo("IN_PROGRESS");
    // 옛 rowVersion 이면 REST 와 같은 code
    String stale = mcp.call("update_issue", args("project", P, "number", n, "rowVersion", rowVersion, "title", "늦음"));
    assertThat((String) read(stale, "$.error.code")).isEqualTo("CONCURRENT_UPDATE");

    mcp.call("add_comment", args("project", P, "number", n, "text", "진행 메모"));
    String logged = mcp.call("log_work", args("project", P, "number", n, "workDate",
        LocalDate.now(ZoneId.of("Asia/Seoul")).toString(), "hours", 2.5, "note", "조사"));
    assertThat(((Number) read(logged, "$.spentHours")).doubleValue()).isEqualTo(2.5);

    // 웹에서 보면 이력의 한 사람은 키 주인, 경로는 MCP
    api.get(s.owner(), "/workspaces/{ws}/projects/{p}/issues/{n}", s.ws(), P, n)
        .andExpect(jsonPath("$.journal[0].via").value("MCP"))
        .andExpect(jsonPath("$.journal[0].actorName").exists())
        .andExpect(jsonPath("$.workLogs[0].user.id").value(s.curator().id().toString()));
    // 담당자에게 알림이 간다(MCP 경로)
    api.get(s.member(), "/me/notifications?unread=true")
        .andExpect(jsonPath("$.items[?(@.kind == 'issue.assigned')].params.via").value("MCP"));
  }

  @Test
  void 권한과_범위는_REST_와_같다() throws Exception {
    Space s = space();
    api.post(s.owner(), "/workspaces/{ws}/projects", Map.of("key", "hidden", "name", "지정", "visibility", "RESTRICTED"), s.ws())
        .andExpect(status().isCreated());
    McpTestClient member = client(s.member(), s.ws());
    // 멤버는 읽기 · 댓글만 — 만들기는 PERMISSION_REQUIRED
    String denied = member.call("create_issue", args("project", P, "type", "bug", "title", "막힘"));
    assertThat((String) read(denied, "$.error.code")).isEqualTo("PERMISSION_REQUIRED");
    // 독자가 아닌 프로젝트는 없는 것과 같다
    String hidden = member.call("search_issues", args("project", "hidden"));
    assertThat((String) read(hidden, "$.error.code")).isEqualTo("PROJECT_NOT_FOUND");
    String nothing = member.call("search_issues", args("project", "no-such"));
    assertThat((String) read(nothing, "$.error.code")).isEqualTo("PROJECT_NOT_FOUND");
    // 옛 유형은 REST 처럼 거절
    String retired = client(s.curator(), s.ws()).call("create_issue", args("project", P, "type", "task", "title", "옛"));
    assertThat((String) read(retired, "$.error.code")).isEqualTo("ISSUE_TYPE_RETIRED");
  }

  @Test
  void 찾기는_제목_설명_번호로_찾고_쪽마다_다음_쪽이_있는지_알려_준다() throws Exception {
    Space s = space();
    McpTestClient mcp = client(s.curator(), s.ws());
    for (int i = 1; i <= 7; i++) {
      mcp.call("create_issue", args("project", P, "type", "test", "title", "항목 " + i,
          "description", i == 3 ? "본문에만 있는 말: 펭귄" : "보통"));
    }
    // 설명에서 찾는다
    String byBody = mcp.call("search_issues", args("project", P, "q", "펭귄"));
    assertThat((List<String>) read(byBody, "$.items[*].title")).containsExactly("항목 3");

    // 3개씩 끝까지 — 겹치거나 빠지지 않는다
    List<Integer> seen = new java.util.ArrayList<>();
    Integer page = 0;
    int pages = 0;
    while (page != null) {
      String r = mcp.call("search_issues", args("project", P, "size", 3, "page", page, "sort", "number"));
      seen.addAll(read(r, "$.items[*].number"));
      assertThat((Integer) read(r, "$.total")).isEqualTo(7);
      assertThat((Integer) read(r, "$.totalPages")).isEqualTo(3);
      boolean hasNext = read(r, "$.hasNext");
      page = hasNext ? read(r, "$.nextPage") : null;
      pages++;
    }
    assertThat(pages).isEqualTo(3);
    assertThat(seen).hasSize(7).doesNotHaveDuplicates().isSorted();
  }

  @Test
  void REST_목록도_다음_쪽과_상태_차례_정렬_기한_지남을_준다() throws Exception {
    Space s = space();
    String base = "/workspaces/{ws}/projects/{p}/issues";
    LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
    for (String st : List.of("CLOSED", "TESTING", "NEW", "DONE", "IN_PROGRESS")) {
      api.post(s.curator(), base, Map.of("type", "bug", "title", "상태 " + st, "status", st), s.ws(), P).andExpect(status().isCreated());
    }
    api.post(s.curator(), base, Map.of("type", "bug", "title", "늦음", "startDate", today.minusDays(5).toString(),
        "dueDate", today.minusDays(1).toString()), s.ws(), P).andExpect(status().isCreated());

    api.get(s.member(), base + "?size=2&page=0", s.ws(), P)
        .andExpect(jsonPath("$.hasNext").value(true)).andExpect(jsonPath("$.totalPages").value(3));
    api.get(s.member(), base + "?size=2&page=2", s.ws(), P).andExpect(jsonPath("$.hasNext").value(false));
    // 상태 정렬은 등록 < 진행 < 테스트 < 완료 < 종료
    List<String> order = TestApi.read(api.get(s.member(), base + "?sort=status&q=상태", s.ws(), P), "$.items[*].status");
    assertThat(order).containsExactly("NEW", "IN_PROGRESS", "TESTING", "DONE", "CLOSED");
    List<String> late = TestApi.read(api.get(s.member(), base + "?overdue=true", s.ws(), P), "$.items[*].title");
    assertThat(late).containsExactly("늦음");
    List<String> ranged = TestApi.read(api.get(s.member(), base + "?dueFrom={f}&dueTo={t}", s.ws(), P,
        today.minusDays(2), today), "$.items[*].title");
    assertThat(ranged).containsExactly("늦음");
  }
}
