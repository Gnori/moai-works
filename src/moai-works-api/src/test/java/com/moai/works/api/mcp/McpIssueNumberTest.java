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
 * MCP 의 일감 번호 — 번호는 워크스페이스에서 하나라 번호로 가리키는 도구는 project 가 없어도 되고, 판정은 그 일감의 지금 프로젝트로 한다.
 * search_issues 는 project 없이 워크스페이스 전체, 옮기기는 update_issue 의 moveToProject (ADR-030, moai-works 일감 #29).
 */
@ApiTest
class McpIssueNumberTest {

  @Autowired
  TestApi api;
  @Autowired
  MockMvc mvc;

  private record Space(String ws, TestUser owner, TestUser editor, TestUser member) {
  }

  /** alpha · beta(공개) · secret(지정 멤버). editor 는 alpha · beta 의 편집자, member 는 그냥 멤버 */
  private Space space() throws Exception {
    TestUser owner = api.user("owner");
    String ws = api.teamWorkspace(owner, "MCP 번호");
    TestUser editor = api.user("editor");
    TestUser member = api.user("member");
    api.join(ws, owner, editor, "member");
    api.join(ws, owner, member, "member");
    for (String[] p : List.of(new String[] {"alpha", "WORKSPACE"}, new String[] {"beta", "WORKSPACE"},
        new String[] {"secret", "RESTRICTED"})) {
      api.post(owner, "/workspaces/{ws}/projects", Map.of("key", p[0], "name", p[0].toUpperCase(), "visibility", p[1]), ws)
          .andExpect(status().isCreated());
    }
    for (String p : List.of("alpha", "beta")) {
      api.put(owner, "/workspaces/{ws}/projects/{p}/roles/{user}", Map.of("roles", List.of("project-editor")), ws, p,
          editor.id()).andExpect(status().isOk());
    }
    return new Space(ws, owner, editor, member);
  }

  private McpTestClient client(TestUser who) throws Exception {
    String key = TestApi.read(api.post(who, "/me/mcp-keys", Map.of("name", "번호 키")).andExpect(status().isCreated()), "$.key");
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
  void 번호만으로_부르고_다른_프로젝트를_주면_분명한_오류를_준다() throws Exception {
    Space s = space();
    McpTestClient editor = client(s.editor());
    McpTestClient member = client(s.member());
    int a = read(editor.call("create_issue", args("project", "alpha", "type", "bug", "title", "알파 버그")), "$.number");
    int b = read(editor.call("create_issue", args("project", "beta", "type", "bug", "title", "베타 버그")), "$.number");
    assertThat(b).isEqualTo(a + 1);

    // 번호만 — 결과에 지금 프로젝트
    String got = member.call("get_issue", args("number", b));
    assertThat((String) read(got, "$.project.key")).isEqualTo("beta");
    assertThat((String) read(got, "$.title")).isEqualTo("베타 버그");
    // 준 프로젝트가 다르면 ISSUE_PROJECT_MISMATCH — 읽을 수 있는 일감이라 지금 프로젝트를 알린다
    String mismatch = member.call("get_issue", args("project", "alpha", "number", b));
    assertThat((String) read(mismatch, "$.error.code")).isEqualTo("ISSUE_PROJECT_MISMATCH");
    assertThat((String) read(mismatch, "$.error.message")).contains("beta");
    // 맞는 프로젝트를 주면 그대로
    assertThat((Integer) read(member.call("get_issue", args("project", "beta", "number", b)), "$.number")).isEqualTo(b);

    // 번호만으로 댓글 · 작업 시간 · 고치기 — 결과마다 project
    assertThat((String) read(member.call("add_comment", args("number", a, "text", "번호만으로 댓글")), "$.project.key"))
        .isEqualTo("alpha");
    assertThat((String) read(editor.call("log_work", args("number", a, "workDate", java.time.LocalDate.now(
        java.time.ZoneId.of("Asia/Seoul")).toString(), "hours", 1.5)), "$.project.key")).isEqualTo("alpha");
    int rowVersion = read(member.call("get_issue", args("number", a)), "$.rowVersion");
    assertThat((String) read(editor.call("update_issue", args("number", a, "rowVersion", rowVersion, "status", "IN_PROGRESS")),
        "$.status")).isEqualTo("IN_PROGRESS");
    // 다른 프로젝트 일감과 연결 — 대상 번호는 워크스페이스 번호
    String linked = editor.call("link_issues", args("number", a, "target", b, "type", "RELATES"));
    assertThat((Integer) read(linked, "$.links[0].issue.number")).isEqualTo(b);
    assertThat((String) read(linked, "$.links[0].issue.project.key")).isEqualTo("beta");
  }

  @Test
  void 읽을_수_없는_일감은_없는_것과_같다() throws Exception {
    Space s = space();
    int hidden = TestApi.read(api.post(s.owner(), "/workspaces/{ws}/projects/{p}/issues", Map.of("type", "bug", "title", "비밀"),
        s.ws(), "secret").andExpect(status().isCreated()), "$.number");
    McpTestClient member = client(s.member());
    assertThat((String) read(member.call("get_issue", args("number", hidden)), "$.error.code")).isEqualTo("ISSUE_NOT_FOUND");
    // 읽을 수 없는 일감의 프로젝트를 짐작해 넣어도 프로젝트부터 없다 — 일감이 어디 있는지 알려 주지 않는다
    assertThat((String) read(member.call("get_issue", args("project", "secret", "number", hidden)), "$.error.code"))
        .isEqualTo("PROJECT_NOT_FOUND");
    String wrong = member.call("get_issue", args("project", "alpha", "number", hidden));
    assertThat((String) read(wrong, "$.error.code")).isEqualTo("ISSUE_NOT_FOUND");
    assertThat((String) read(wrong, "$.error.message")).doesNotContain("secret");
    assertThat((String) read(member.call("add_comment", args("number", hidden, "text", "몰래")), "$.error.code"))
        .isEqualTo("ISSUE_NOT_FOUND");
    assertThat((String) read(client(s.owner()).call("get_issue", args("number", hidden)), "$.project.key")).isEqualTo("secret");
  }

  @Test
  void search_issues_는_project_없이_워크스페이스_전체에서_읽을_수_있는_것만() throws Exception {
    Space s = space();
    McpTestClient editor = client(s.editor());
    int a = read(editor.call("create_issue", args("project", "alpha", "type", "bug", "title", "로그인 버그")), "$.number");
    int b = read(editor.call("create_issue", args("project", "beta", "type", "feature", "title", "보고서")), "$.number");
    api.post(s.owner(), "/workspaces/{ws}/projects/{p}/issues", Map.of("type", "bug", "title", "비밀 로그인 버그"), s.ws(), "secret")
        .andExpect(status().isCreated());
    McpTestClient member = client(s.member());
    // 개인 워크스페이스도 있어 워크스페이스가 여럿 — 비우면 WORKSPACE_REQUIRED
    assertThat((String) read(member.call("search_issues", args()), "$.error.code")).isEqualTo("WORKSPACE_REQUIRED");
    String all = member.call("search_issues", args("workspace", s.ws()));
    assertThat((Integer) read(all, "$.total")).isEqualTo(2);
    assertThat((List<String>) read(all, "$.items[*].project.key")).containsExactlyInAnyOrder("alpha", "beta");
    assertThat((String) read(all, "$.workspace")).isEqualTo(s.ws());
    // 프로젝트 여럿(쉼표) · 하나
    assertThat((List<Integer>) read(member.call("search_issues", args("project", "alpha,beta")), "$.items[*].number"))
        .containsExactlyInAnyOrder(a, b);
    assertThat((List<Integer>) read(member.call("search_issues", args("project", "beta")), "$.items[*].number"))
        .containsExactly(b);
    assertThat((String) read(member.call("search_issues", args("project", "alpha,secret")), "$.error.code"))
        .isEqualTo("PROJECT_NOT_FOUND");
    // 찾기 — 말 · #번호
    assertThat((List<Integer>) read(member.call("search_issues", args("workspace", s.ws(), "q", "로그인")), "$.items[*].number"))
        .containsExactly(a);
    assertThat((List<Integer>) read(member.call("search_issues", args("workspace", s.ws(), "q", "#" + b)), "$.items[*].number"))
        .containsExactly(b);
  }

  @Test
  void update_issue_의_moveToProject_로_옮긴다() throws Exception {
    Space s = space();
    McpTestClient editor = client(s.editor());
    int n = read(editor.call("create_issue", args("project", "alpha", "type", "bug", "title", "옮길 일")), "$.number");
    int rowVersion = read(editor.call("get_issue", args("number", n)), "$.rowVersion");
    String moved = editor.call("update_issue", args("number", n, "rowVersion", rowVersion, "moveToProject", "beta"));
    assertThat((String) read(moved, "$.project.key")).isEqualTo("beta");
    assertThat((Integer) read(moved, "$.number")).isEqualTo(n);
    // 읽을 수 없는 곳으로는 — 없는 것과 같은 오류
    int again = read(moved, "$.rowVersion");
    assertThat((String) read(editor.call("update_issue", args("number", n, "rowVersion", again, "moveToProject", "secret")),
        "$.error.code")).isEqualTo("TARGET_PROJECT_NOT_FOUND");
    // 옮긴 뒤 옛 프로젝트를 주면 MISMATCH
    assertThat((String) read(editor.call("get_issue", args("project", "alpha", "number", n)), "$.error.code"))
        .isEqualTo("ISSUE_PROJECT_MISMATCH");
    api.get(s.owner(), "/workspaces/{ws}/audit?action=issue.move", s.ws()).andExpect(jsonPath("$[0].action").value("issue.move"))
        .andExpect(jsonPath("$[0].details").value(org.hamcrest.Matchers.containsString("MCP")));
  }

  @Test
  void 같은_번호가_여러_워크스페이스에_있으면_workspace_를_넣으라고_한다() throws Exception {
    TestUser owner = api.user("multi");
    String ws1 = api.teamWorkspace(owner, "첫 팀");
    String ws2 = api.teamWorkspace(owner, "둘째 팀");
    for (String ws : List.of(ws1, ws2)) {
      api.post(owner, "/workspaces/{ws}/projects", Map.of("key", "work", "name", "일", "visibility", "WORKSPACE"), ws)
          .andExpect(status().isCreated());
      api.post(owner, "/workspaces/{ws}/projects/{p}/issues", Map.of("type", "bug", "title", ws + " 의 첫 일"), ws, "work")
          .andExpect(status().isCreated()).andExpect(jsonPath("$.number").value(1));
    }
    api.post(owner, "/workspaces/{ws}/projects/{p}/issues", Map.of("type", "bug", "title", "둘째 팀의 둘째 일"), ws2, "work")
        .andExpect(status().isCreated()).andExpect(jsonPath("$.number").value(2));
    McpTestClient mcp = client(owner);
    String ambiguous = mcp.call("get_issue", args("number", 1));
    assertThat((String) read(ambiguous, "$.error.code")).isEqualTo("ISSUE_AMBIGUOUS");
    assertThat((String) read(ambiguous, "$.error.message")).contains(ws1).contains(ws2);
    assertThat((String) read(mcp.call("get_issue", args("number", 1, "workspace", ws1)), "$.title")).isEqualTo(ws1 + " 의 첫 일");
    // 그 번호를 준 곳이 하나뿐이면 그곳
    assertThat((String) read(mcp.call("get_issue", args("number", 2)), "$.title")).isEqualTo("둘째 팀의 둘째 일");
    // 같은 project key 가 여러 워크스페이스에 있으면 그것은 PROJECT_AMBIGUOUS 그대로
    assertThat((String) read(mcp.call("get_issue", args("project", "work", "number", 2)), "$.error.code"))
        .isEqualTo("PROJECT_AMBIGUOUS");
  }
}
