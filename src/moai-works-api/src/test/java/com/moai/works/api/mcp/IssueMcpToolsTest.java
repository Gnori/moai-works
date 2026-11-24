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

  /** 키는 사용자의 것 — 개인 설정에서 받는다(ADR-029) */
  private McpTestClient client(TestUser who) throws Exception {
    String key = TestApi.read(api.post(who, "/me/mcp-keys", Map.of("name", "에이전트 키"))
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
    assertThat(client(s.curator()).tools()).contains("list_projects", "list_issue_types", "search_issues", "get_issue",
        "create_issue", "update_issue", "add_comment", "list_comments", "update_comment", "delete_comment", "log_work",
        "delete_work_log", "link_issues", "delete_issue", "get_markdown_guide");
  }

  @Test
  void 지우기는_웹과_같은_권한에_이유가_필수이고_감사에_경로와_이유가_남는다() throws Exception {
    Space s = space();
    McpTestClient curator = client(s.curator());
    int n = read(curator.call("create_issue", args("project", P, "type", "bug", "title", "지울 것")), "$.summary.number");
    assertThat((String) read(client(s.member()).call("delete_issue", args("project", P, "number", n, "reason", "x")),
        "$.error.code")).isEqualTo("PERMISSION_REQUIRED");
    // 지우기 권한(issue.delete)은 기본 역할로는 소유자 · 관리자
    McpTestClient owner = client(s.owner());
    assertThat((String) read(owner.call("delete_issue", args("project", P, "number", n, "reason", " ")), "$.error.code"))
        .isEqualTo("DELETE_REASON_REQUIRED");
    assertThat((Boolean) read(owner.call("delete_issue", args("project", P, "number", n, "reason", "중복 등록")), "$.deleted"))
        .isTrue();
    api.get(s.owner(), "/workspaces/{ws}/issues/{n}", s.ws(), n).andExpect(status().isNotFound());
    api.get(s.owner(), "/workspaces/{ws}/audit?action=issue.delete", s.ws())
        .andExpect(jsonPath("$[0].details").value(org.hamcrest.Matchers.allOf(
            org.hamcrest.Matchers.containsString("MCP"), org.hamcrest.Matchers.containsString("중복 등록"))));
  }

  @Test
  void 만들고_고치고_댓글_작업_시간까지_한_사람은_키_주인이고_경로는_MCP() throws Exception {
    Space s = space();
    McpTestClient mcp = client(s.curator());
    String created = mcp.call("create_issue", args("project", P, "type", "feature", "title", "[FR-X-001] 첨부파일",
        "description", "## 수용 기준\n- [ ] 올린다", "assignee", s.member().username(), "estimatedHours", 6));
    int n = read(created, "$.summary.number");
    assertThat((String) read(created, "$.assignee.username")).isEqualTo(s.member().username());
    // 사람은 아이디로 — MCP 도구는 사람을 아이디 · me 로 받는다(id 는 싣지 않는다, 일감 #282)
    assertThat((String) read(created, "$.manager.username")).isEqualTo(s.curator().username());
    assertThat((Map<String, Object>) read(created, "$.manager")).doesNotContainKey("id");
    int rowVersion = read(created, "$.summary.rowVersion");

    String updated = mcp.call("update_issue", args("project", P, "number", n, "rowVersion", rowVersion,
        "status", "in_progress", "dueDate", "2026-12-31", "comment", "시작합니다"));
    assertThat((String) read(updated, "$.summary.status")).isEqualTo("IN_PROGRESS");
    // 옛 rowVersion 이면 REST 와 같은 code
    String stale = mcp.call("update_issue", args("project", P, "number", n, "rowVersion", rowVersion, "title", "늦음"));
    assertThat((String) read(stale, "$.error.code")).isEqualTo("CONCURRENT_UPDATE");

    mcp.call("add_comment", args("project", P, "number", n, "text", "진행 메모"));
    String logged = mcp.call("log_work", args("project", P, "number", n, "workDate",
        LocalDate.now(ZoneId.of("Asia/Seoul")).toString(), "hours", 2.5, "note", "조사"));
    assertThat(((Number) read(logged, "$.spentHours")).doubleValue()).isEqualTo(2.5);

    // 웹에서 보면 이력의 한 사람은 키 주인, 경로는 MCP
    api.get(s.owner(), "/workspaces/{ws}/issues/{n}", s.ws(), n)
        .andExpect(jsonPath("$.journal[0].via").value("MCP"))
        .andExpect(jsonPath("$.journal[0].actorName").exists())
        .andExpect(jsonPath("$.workLogs[0].user.id").value(s.curator().id().toString()));
    // 담당자에게 알림이 간다(MCP 경로)
    api.get(s.member(), "/me/notifications?unread=true")
        .andExpect(jsonPath("$.items[?(@.kind == 'issue.assigned')].params.via").value("MCP"));
  }

  @Test
  void 댓글_목록_고치기_지우기는_REST_와_같은_판정이고_이력_감사의_경로는_MCP() throws Exception {
    Space s = space();
    McpTestClient member = client(s.member());
    McpTestClient owner = client(s.owner());
    int n = read(client(s.curator()).call("create_issue", args("project", P, "type", "bug", "title", "댓글 도구")), "$.summary.number");
    String comments = "/workspaces/{ws}/issues/{n}/comments";
    api.post(s.curator(), comments, Map.of("notes", "큐레이터의 첫 댓글"), s.ws(), n).andExpect(status().isOk());
    for (int i = 2; i <= 20; i++) {
      api.post(s.curator(), comments, Map.of("notes", "큐레이터 댓글 " + i), s.ws(), n).andExpect(status().isOk());
    }
    member.call("add_comment", args("project", P, "number", n, "text", "멤버 글"));

    // 목록 — 오래된 것부터 20개씩, 다음 쪽이 있는지 알려 준다
    String first = member.call("list_comments", args("project", P, "number", n));
    assertThat((Integer) read(first, "$.total")).isEqualTo(21);
    assertThat((List<Object>) read(first, "$.items")).hasSize(20);
    assertThat((Boolean) read(first, "$.hasNext")).isTrue();
    assertThat((Integer) read(first, "$.nextPage")).isEqualTo(1);
    String second = member.call("list_comments", args("project", P, "number", n, "page", 1));
    assertThat((Boolean) read(second, "$.hasNext")).isFalse();
    assertThat((String) read(second, "$.items[0].notes")).isEqualTo("멤버 글");
    String mine = read(second, "$.items[0].id");
    String curators = read(first, "$.items[0].id");

    // 고치기 — 쓴 사람만. 남의 것은 PERMISSION_REQUIRED, 낡은 rowVersion 은 CONCURRENT_UPDATE
    assertThat((String) read(member.call("update_comment", args("project", P, "number", n, "commentId", curators,
        "text", "남의 글 고침", "rowVersion", 0)), "$.error.code")).isEqualTo("PERMISSION_REQUIRED");
    assertThat((String) read(owner.call("update_comment", args("project", P, "number", n, "commentId", mine,
        "text", "관리자가 고침", "rowVersion", 0)), "$.error.code")).isEqualTo("PERMISSION_REQUIRED");
    String updated = member.call("update_comment", args("project", P, "number", n, "commentId", mine, "text", "고친 멤버 글",
        "rowVersion", 0));
    assertThat((Integer) read(updated, "$.rowVersion")).isEqualTo(1);
    assertThat((String) read(updated, "$.editedAt")).isNotBlank();
    assertThat((String) read(member.call("update_comment", args("project", P, "number", n, "commentId", mine,
        "text", "늦음", "rowVersion", 0)), "$.error.code")).isEqualTo("CONCURRENT_UPDATE");
    assertThat((String) read(member.call("update_comment", args("project", P, "number", n, "commentId", "nope",
        "text", "x", "rowVersion", 0)), "$.error.code")).isEqualTo("COMMENT_NOT_FOUND");

    // 지우기 — 남의 것은 project.manage 가 있어야(소유자). 지운 댓글은 글 없이 제자리에
    assertThat((String) read(member.call("delete_comment", args("project", P, "number", n, "commentId", curators)),
        "$.error.code")).isEqualTo("PERMISSION_REQUIRED");
    assertThat((Boolean) read(owner.call("delete_comment", args("project", P, "number", n, "commentId", curators)),
        "$.deleted")).isTrue();
    String after = member.call("list_comments", args("project", P, "number", n));
    assertThat((Boolean) read(after, "$.items[0].deleted")).isTrue();
    assertThat((String) read(after, "$.items[0].deletedBy.username")).isEqualTo(s.owner().username());
    assertThat(after).doesNotContain("큐레이터의 첫 댓글");
    assertThat(member.call("get_issue", args("project", P, "number", n))).doesNotContain("큐레이터의 첫 댓글");
    assertThat((String) read(member.call("update_comment", args("project", P, "number", n, "commentId", curators,
        "text", "x", "rowVersion", 0)), "$.error.code")).isEqualTo("PERMISSION_REQUIRED");
    assertThat((String) read(owner.call("delete_comment", args("project", P, "number", n, "commentId", curators)),
        "$.error.code")).isEqualTo("COMMENT_DELETED");

    // 이력 한 줄씩(판 아님)과 감사 — 한 사람은 키 주인, 경로는 MCP
    api.get(s.owner(), "/workspaces/{ws}/issues/{n}", s.ws(), n)
        .andExpect(jsonPath("$.journal[?(@.changes[0].field == 'comment')].via",
            org.hamcrest.Matchers.contains("MCP", "MCP")))
        .andExpect(jsonPath("$.journal[?(@.changes[0].field == 'comment')].actorId",
            org.hamcrest.Matchers.contains(s.member().id().toString(), s.owner().id().toString())));
    for (String action : List.of("issue.comment.update", "issue.comment.delete")) {
      List<String> details = TestApi.read(api.get(s.owner(), "/workspaces/{ws}/audit?action=" + action, s.ws()), "$[*].details");
      assertThat(details).hasSize(1).allMatch(d -> d.contains("MCP"));
    }

    // 워크스페이스는 부를 때마다 고른다 — 같은 프로젝트 key 가 다른 워크스페이스에도 있으면 workspace 를 넣는다(ADR-029)
    TestUser bOwner = api.user("b-owner");
    String b = api.teamWorkspace(bOwner, "같은 key 팀");
    api.join(b, bOwner, s.member(), "member");
    api.post(bOwner, "/workspaces/{ws}/projects", Map.of("key", P, "name", "B 의 에이전트", "visibility", "WORKSPACE"), b)
        .andExpect(status().isCreated());
    assertThat((String) read(member.call("list_comments", args("project", P, "number", n)), "$.error.code"))
        .isEqualTo("PROJECT_AMBIGUOUS");
    assertThat((String) read(member.call("delete_comment", args("project", P, "number", n, "commentId", mine)),
        "$.error.code")).isEqualTo("PROJECT_AMBIGUOUS");
    assertThat((Integer) read(member.call("list_comments", args("project", P, "workspace", s.ws(), "number", n)), "$.total"))
        .isEqualTo(21);
    // B 의 같은 key 프로젝트에는 그 일감이 없다 · 키로 쓸 수 없는 워크스페이스는 없는 것과 같다
    assertThat((String) read(member.call("update_comment", args("project", P, "workspace", b, "number", n,
        "commentId", mine, "text", "B 에서", "rowVersion", 1)), "$.error.code")).isEqualTo("ISSUE_NOT_FOUND");
    assertThat((String) read(member.call("delete_comment", args("project", P, "workspace", "workspace-zzzzzzzzzzzz",
        "number", n, "commentId", mine)), "$.error.code")).isEqualTo("WORKSPACE_NOT_FOUND");
    assertThat((Boolean) read(member.call("delete_comment", args("project", P, "workspace", s.ws(), "number", n,
        "commentId", mine)), "$.deleted")).isTrue();
  }

  @Test
  void 권한과_범위는_REST_와_같다() throws Exception {
    Space s = space();
    api.post(s.owner(), "/workspaces/{ws}/projects", Map.of("key", "hidden", "name", "지정", "visibility", "RESTRICTED"), s.ws())
        .andExpect(status().isCreated());
    McpTestClient member = client(s.member());
    // 멤버는 읽기 · 댓글만 — 만들기는 PERMISSION_REQUIRED
    String denied = member.call("create_issue", args("project", P, "type", "bug", "title", "막힘"));
    assertThat((String) read(denied, "$.error.code")).isEqualTo("PERMISSION_REQUIRED");
    // 독자가 아닌 프로젝트는 없는 것과 같다
    String hidden = member.call("search_issues", args("project", "hidden"));
    assertThat((String) read(hidden, "$.error.code")).isEqualTo("PROJECT_NOT_FOUND");
    String nothing = member.call("search_issues", args("project", "no-such"));
    assertThat((String) read(nothing, "$.error.code")).isEqualTo("PROJECT_NOT_FOUND");
    // 옛 유형은 REST 처럼 거절
    String retired = client(s.curator()).call("create_issue", args("project", P, "type", "task", "title", "옛"));
    assertThat((String) read(retired, "$.error.code")).isEqualTo("ISSUE_TYPE_RETIRED");
  }

  @Test
  void 찾기는_제목_설명_번호로_찾고_쪽마다_다음_쪽이_있는지_알려_준다() throws Exception {
    Space s = space();
    McpTestClient mcp = client(s.curator());
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
  void 보류_취소는_comment_로_까닭을_넣어야_옮기고_찾기의_묶음과_기한_지남이_REST_와_같다() throws Exception {
    Space s = space();
    McpTestClient mcp = client(s.curator());
    String yesterday = LocalDate.now(ZoneId.of("Asia/Seoul")).minusDays(1).toString();
    // 만들 때는 보류 · 취소가 안 된다 — REST 와 같은 code
    assertThat((String) read(mcp.call("create_issue", args("project", P, "type", "bug", "title", "바로 취소", "status", "cancelled")),
        "$.error.code")).isEqualTo("STATUS_REASON_REQUIRED");
    String held = mcp.call("create_issue", args("project", P, "type", "feature", "title", "멈출 일", "dueDate", yesterday));
    int hold = read(held, "$.summary.number");
    int rowVersion = read(held, "$.summary.rowVersion");
    String denied = mcp.call("update_issue", args("project", P, "number", hold, "rowVersion", rowVersion, "status", "on_hold"));
    assertThat((String) read(denied, "$.error.code")).isEqualTo("STATUS_REASON_REQUIRED");
    assertThat((Integer) read(denied, "$.error.status")).isEqualTo(400);
    String onHold = mcp.call("update_issue", args("project", P, "number", hold, "rowVersion", rowVersion, "status", "ON_HOLD",
        "comment", "사람의 결정을 기다린다"));
    assertThat((String) read(onHold, "$.summary.status")).isEqualTo("ON_HOLD");
    assertThat((Boolean) read(onHold, "$.overdue")).isTrue();
    // 까닭은 같은 이력 줄의 댓글 — 한 사람은 키 주인, 경로 MCP
    api.get(s.owner(), "/workspaces/{ws}/issues/{n}", s.ws(), hold)
        .andExpect(jsonPath("$.journal[-1].notes").value("사람의 결정을 기다린다"))
        .andExpect(jsonPath("$.journal[-1].via").value("MCP"))
        .andExpect(jsonPath("$.journal[-1].changes[0].after").value("ON_HOLD"));

    String dup = mcp.call("create_issue", args("project", P, "type", "bug", "title", "중복"));
    int cancelled = read(dup, "$.summary.number");
    String done = mcp.call("update_issue", args("project", P, "number", cancelled, "rowVersion", read(dup, "$.summary.rowVersion"),
        "status", "CANCELLED", "comment", "#" + hold + " 과 중복"));
    assertThat((String) read(done, "$.summary.status")).isEqualTo("CANCELLED");
    assertThat((String) read(done, "$.summary.closedOn")).isEqualTo(LocalDate.now(ZoneId.of("Asia/Seoul")).toString());
    assertThat((Map<String, Object>) read(done, "$.summary")).doesNotContainKey("completedOn"); // 빈 값은 싣지 않는다(일감 #282)

    List<Integer> open = read(mcp.call("search_issues", args("project", P, "status", "open")), "$.items[*].number");
    assertThat(open).contains(hold).doesNotContain(cancelled);
    List<Integer> closed = read(mcp.call("search_issues", args("project", P, "status", "closed")), "$.items[*].number");
    assertThat(closed).contains(cancelled).doesNotContain(hold);
    List<Integer> late = read(mcp.call("search_issues", args("project", P, "overdue", true)), "$.items[*].number");
    assertThat(late).containsExactly(hold);
    List<Integer> picked = read(mcp.call("search_issues", args("project", P, "status", "ON_HOLD,CANCELLED")), "$.items[*].number");
    assertThat(picked).containsExactlyInAnyOrder(hold, cancelled);
    // REST 의 묶음과 같다
    List<Integer> restOpen = TestApi.read(api.get(s.member(), "/workspaces/{ws}/projects/{p}/issues?status=open", s.ws(), P),
        "$.items[*].number");
    assertThat(restOpen).containsExactlyInAnyOrderElementsOf(open);

    // 취소에서 다시 열면 까닭 없이 되고 종료일을 비운다
    String reopened = mcp.call("update_issue", args("project", P, "number", cancelled, "rowVersion", read(done, "$.summary.rowVersion"),
        "status", "NEW"));
    assertThat((String) read(reopened, "$.summary.status")).isEqualTo("NEW");
    assertThat((Map<String, Object>) read(reopened, "$.summary")).doesNotContainKey("closedOn"); // 빈 값은 싣지 않는다(일감 #282)
  }

  @Test
  void 도구_설명은_상태_열하나와_까닭_관리자_확인_규칙을_알린다() throws Exception {
    Space s = space();
    String listed = client(s.curator()).toolsJson();
    for (String tool : List.of("search_issues", "update_issue")) {
      List<String> statusParam = read(listed, "$.result.tools[?(@.name == '" + tool + "')].inputSchema.properties.status.description");
      assertThat(statusParam).singleElement().asString().contains("NEW", "REQUIREMENT_REVIEW", "REQUIREMENT_CONFIRMED", "IN_PROGRESS",
          "TESTING", "REVIEW", "CONFIRMED", "ON_HOLD", "DONE", "CLOSED", "CANCELLED");
    }
    // 묶음 open · closed 는 search_issues 의 status 인자에(일감 #282 — 상태 목록을 도구마다 되풀이하지 않는다)
    List<String> searchStatus = read(listed, "$.result.tools[?(@.name == 'search_issues')].inputSchema.properties.status.description");
    assertThat(searchStatus).singleElement().asString().contains("open(닫히지 않은 것 모두 — 확인 넷 · 보류 포함)",
        "closed(DONE · CLOSED · CANCELLED)");
    List<String> search = read(listed, "$.result.tools[?(@.name == 'search_issues')].description");
    assertThat(search).singleElement().asString().contains("status=open", "status=REQUIREMENT_REVIEW,REVIEW",
        "status=REQUIREMENT_CONFIRMED,CONFIRMED");
    // 상태의 뜻 · reason · 관리자 확인은 update_issue 한 곳에
    List<String> update = read(listed, "$.result.tools[?(@.name == 'update_issue')].description");
    assertThat(update).singleElement().asString().contains("REQUIREMENT_REVIEW 요구사항 확인요청", "REQUIREMENT_CONFIRMED 요구사항 확인완료",
        "REVIEW 확인요청", "CONFIRMED 확인완료", "ON_HOLD 보류", "reason(까닭 — comment 도 된다) 필수", "확인요청을 거둘 때",
        "관리자가 웹에서만", "시간을 적지 말고 다른 일감", "NEW → IN_PROGRESS, TESTING → DONE 도 된다");
    List<String> create = read(listed, "$.result.tools[?(@.name == 'create_issue')].description");
    assertThat(create).singleElement().asString().contains("update_issue", "확인요청(REQUIREMENT_REVIEW · REVIEW)",
        "확인완료(REQUIREMENT_CONFIRMED · CONFIRMED)");
  }

  @Test
  void 확인요청은_reason_으로_옮기고_확인완료는_키_주인이_관리자여도_MCP_로는_안_되며_next_가_다음을_알린다() throws Exception {
    // 2026-11-25, 일감 #272 — Agent 는 스스로 확인할 수 없다(키 주인 = 관리자여도). 확인은 관리자가 웹에서
    Space s = space();
    McpTestClient mcp = client(s.curator());
    String created = mcp.call("create_issue", args("project", P, "type", "feature", "title", "확인 받을 일"));
    int n = read(created, "$.summary.number");
    assertThat((String) read(created, "$.manager.username")).isEqualTo(s.curator().username());

    String denied = mcp.call("update_issue", args("project", P, "number", n, "rowVersion", read(created, "$.summary.rowVersion"),
        "status", "requirement_review"));
    assertThat((String) read(denied, "$.error.code")).isEqualTo("STATUS_REASON_REQUIRED");
    String asked = mcp.call("update_issue", args("project", P, "number", n, "rowVersion", read(created, "$.summary.rowVersion"),
        "status", "REQUIREMENT_REVIEW", "reason", "이해한 요구사항: 목록에 확인 넷을 더한다"));
    assertThat((String) read(asked, "$.summary.status")).isEqualTo("REQUIREMENT_REVIEW");
    assertThat((List<String>) read(asked, "$.next")).last().asString().startsWith("관리자 확인을 기다린다 — 시간을 적지 말고 다른 일감을 잡는다")
        .contains("REQUIREMENT_CONFIRMED");
    // Agent 가 관리자의 키로 올린 요청도 관리자(= 키 주인)에게 알린다 — 사람이 직접 한 일이 아니다(실제 서버에서 찾음)
    api.get(s.curator(), "/me/notifications?unread=true")
        .andExpect(jsonPath("$.items[?(@.kind == 'issue.review_requested')].params.via").value("MCP"))
        .andExpect(jsonPath("$.items[?(@.kind == 'issue.review_requested')].params.number").value(n));

    // 확인완료는 MCP 로 옮기지 못한다 — 키 주인이 관리자여도. 일감 그대로
    String self = mcp.call("update_issue", args("project", P, "number", n, "rowVersion", read(asked, "$.summary.rowVersion"),
        "status", "REQUIREMENT_CONFIRMED"));
    assertThat((String) read(self, "$.error.code")).isEqualTo("CONFIRM_BY_MANAGER_ONLY");
    assertThat((Integer) read(self, "$.error.status")).isEqualTo(403);
    assertThat((String) read(self, "$.error.message")).contains("웹에서만").contains("관리자에게");
    api.get(s.owner(), "/workspaces/{ws}/issues/{n}", s.ws(), n).andExpect(jsonPath("$.status").value("REQUIREMENT_REVIEW"));
    // 만들기로도 안 된다
    assertThat((String) read(mcp.call("create_issue", args("project", P, "type", "bug", "title", "바로 확인", "status", "CONFIRMED")),
        "$.error.code")).isEqualTo("CONFIRM_BY_MANAGER_ONLY");
    // 관리자 확인을 기다리는 일감으로 찾는다
    List<Integer> waiting = read(mcp.call("search_issues", args("project", P, "status", "REQUIREMENT_REVIEW,REVIEW")), "$.items[*].number");
    assertThat(waiting).containsExactly(n);
    List<Integer> open = read(mcp.call("search_issues", args("project", P, "status", "open")), "$.items[*].number");
    assertThat(open).contains(n);

    // 관리자가 웹에서 확인한다 → MCP 의 next 는 진행으로 시작
    api.patch(s.curator(), "/workspaces/{ws}/issues/{n}", Map.of("rowVersion", (Integer) read(asked, "$.summary.rowVersion"),
        "status", "REQUIREMENT_CONFIRMED"), s.ws(), n).andExpect(status().isOk());
    String confirmed = mcp.call("get_issue", args("project", P, "number", n));
    assertThat((List<String>) read(confirmed, "$.next")).last().asString().startsWith("요구사항이 확인됐다 — IN_PROGRESS 로 시작");

    String started = mcp.call("update_issue", args("project", P, "number", n, "rowVersion", read(confirmed, "$.summary.rowVersion"),
        "status", "IN_PROGRESS", "startDate", LocalDate.now(ZoneId.of("Asia/Seoul")).toString()));
    String review = mcp.call("update_issue", args("project", P, "number", n, "rowVersion", read(started, "$.summary.rowVersion"),
        "status", "REVIEW", "reason", "한 것: 목록 · 확인: 화면에서 넷이 보인다"));
    assertThat((String) read(review, "$.summary.status")).isEqualTo("REVIEW");
    assertThat((List<String>) read(review, "$.next")).last().asString().startsWith("관리자 확인을 기다린다").contains("CONFIRMED");
    // 등록 · 요구사항 확인에서 바로 확인요청으로 가면 진행 구간을 건너뛴 것이지만, 여기서는 진행을 거쳤다
    assertThat((List<String>) read(review, "$.checks[*].code")).doesNotContain("STARTED_WITHOUT_IN_PROGRESS");
    assertThat((String) read(mcp.call("update_issue", args("project", P, "number", n, "rowVersion", read(review, "$.summary.rowVersion"),
        "status", "CONFIRMED")), "$.error.code")).isEqualTo("CONFIRM_BY_MANAGER_ONLY");
    api.patch(s.curator(), "/workspaces/{ws}/issues/{n}", Map.of("rowVersion", (Integer) read(review, "$.summary.rowVersion"),
        "status", "CONFIRMED"), s.ws(), n).andExpect(status().isOk());
    String done = mcp.call("get_issue", args("project", P, "number", n));
    assertThat((List<String>) read(done, "$.next")).last().asString().startsWith("확인됐다 — DONE(");
    // 확인완료 → 완료는 MCP 로 된다
    String finished = mcp.call("update_issue", args("project", P, "number", n, "rowVersion", read(done, "$.summary.rowVersion"), "status", "DONE"));
    assertThat((String) read(finished, "$.summary.status")).isEqualTo("DONE");
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
    // 보류 · 취소는 만든 뒤 까닭과 함께 옮긴다(2026-11-24, 일감 #152)
    for (String st : List.of("CANCELLED", "ON_HOLD")) {
      int n = TestApi.read(api.post(s.curator(), base, Map.of("type", "bug", "title", "상태 " + st), s.ws(), P)
          .andExpect(status().isCreated()), "$.number");
      api.patch(s.curator(), "/workspaces/{ws}/issues/{n}", Map.of("rowVersion", 1, "status", st, "notes", "까닭"), s.ws(), n)
          .andExpect(status().isOk());
    }
    // 관리자 확인 넷(2026-11-25, 일감 #272) — 확인요청은 까닭과 함께 옮기고, 확인완료는 관리자(만든 사람)가 웹에서 바로 만든다
    for (String st : List.of("REVIEW", "REQUIREMENT_REVIEW")) {
      int n = TestApi.read(api.post(s.curator(), base, Map.of("type", "bug", "title", "상태 " + st), s.ws(), P)
          .andExpect(status().isCreated()), "$.number");
      api.patch(s.curator(), "/workspaces/{ws}/issues/{n}", Map.of("rowVersion", 1, "status", st, "notes", "확인할 것"), s.ws(), n)
          .andExpect(status().isOk());
    }
    for (String st : List.of("CONFIRMED", "REQUIREMENT_CONFIRMED")) {
      api.post(s.curator(), base, Map.of("type", "bug", "title", "상태 " + st, "status", st), s.ws(), P).andExpect(status().isCreated());
    }
    // 상태 정렬은 등록 < 요구사항 확인요청 < 요구사항 확인완료 < 진행 < 테스트 < 확인요청 < 확인완료 < 보류 < 완료 < 종료 < 취소
    List<String> order = TestApi.read(api.get(s.member(), base + "?sort=status&q=상태", s.ws(), P), "$.items[*].status");
    assertThat(order).containsExactly("NEW", "REQUIREMENT_REVIEW", "REQUIREMENT_CONFIRMED", "IN_PROGRESS", "TESTING", "REVIEW",
        "CONFIRMED", "ON_HOLD", "DONE", "CLOSED", "CANCELLED");
    List<String> late = TestApi.read(api.get(s.member(), base + "?overdue=true", s.ws(), P), "$.items[*].title");
    assertThat(late).containsExactly("늦음");
    List<String> ranged = TestApi.read(api.get(s.member(), base + "?dueFrom={f}&dueTo={t}", s.ws(), P,
        today.minusDays(2), today), "$.items[*].title");
    assertThat(ranged).containsExactly("늦음");
  }
}
