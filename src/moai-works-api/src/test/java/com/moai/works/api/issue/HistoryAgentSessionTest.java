package com.moai.works.api.issue;

import static com.jayway.jsonpath.JsonPath.read;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
 * 이력 줄 · 작업 시간 기록의 에이전트 세션 이름(2026-11-26, 일감 #304 — V31) — "moaiworks01 MCP" 뒤에 그 세션 이름.
 * MCP 로 쓴 줄은 그 호출이 준 세션 이름, 없으면 일감의 (그 호출로 바뀐 뒤의) agentSession. 사람이 웹에서 쓴 줄은 없음("사람이 하면 사람").
 */
@ApiTest
class HistoryAgentSessionTest {

  private static final String P = "label";
  private static final String ISSUE = "/workspaces/{ws}/issues/{n}";

  @Autowired
  TestApi api;
  @Autowired
  MockMvc mvc;
  @Autowired
  JdbcTemplate jdbc;

  private record Space(String ws, TestUser owner, TestUser curator, TestUser member) {
  }

  private Space space() throws Exception {
    TestUser owner = api.user("owner");
    String ws = api.teamWorkspace(owner, "세션 이름표");
    TestUser curator = api.user("curator");
    TestUser member = api.user("member");
    api.join(ws, owner, curator, "curator");
    api.join(ws, owner, member, "member");
    api.post(owner, "/workspaces/{ws}/projects", Map.of("key", P, "name", "이름표 시험", "visibility", "WORKSPACE"), ws)
        .andExpect(status().isCreated());
    return new Space(ws, owner, curator, member);
  }

  private McpTestClient client(TestUser who) throws Exception {
    String key = TestApi.read(api.post(who, "/me/mcp-keys", Map.of("name", "이름표 키")).andExpect(status().isCreated()), "$.key");
    return new McpTestClient(mvc, key);
  }

  private static Map<String, Object> args(Object... kv) {
    Map<String, Object> m = new HashMap<>();
    for (int i = 0; i < kv.length; i += 2) {
      m.put((String) kv[i], kv[i + 1]);
    }
    return m;
  }

  /** 일감 보기(REST)의 이력 줄들 — 오래된 것부터 */
  private List<Map<String, Object>> journal(Space s, int n) throws Exception {
    return TestApi.read(api.get(s.owner(), ISSUE, s.ws(), n), "$.journal");
  }

  /** 그 글의 댓글 줄 */
  private static Map<String, Object> comment(List<Map<String, Object>> lines, String notes) {
    return lines.stream().filter(j -> notes.equals(j.get("notes"))).findFirst().orElseThrow();
  }

  /** 작업 시간을 더한 줄들 — 적은 차례대로 */
  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> workLogLines(List<Map<String, Object>> lines) {
    return lines.stream().filter(j -> ((List<Map<String, Object>>) j.get("changes")).stream()
        .anyMatch(c -> "workLog".equals(c.get("field")) && c.get("after") != null)).toList();
  }

  @Test
  void MCP_줄은_준_세션_이름_없으면_일감의_세션_웹_줄은_없음() throws Exception {
    Space s = space();
    McpTestClient mcp = client(s.curator());
    String created = mcp.call("create_issue", args("project", P, "type", "feature", "title", "이름표", "agentSession", "moai-works"));
    int n = read(created, "$.summary.number");
    int rv = read(created, "$.summary.rowVersion");

    // 만들기 줄 — 준 세션 이름(= 일감의 세션)
    assertThat(journal(s, n).get(0)).containsEntry("via", "MCP").containsEntry("agentSession", "moai-works");

    // add_comment — 비우면 일감의 세션, 주면 그 이름(앞뒤 공백을 뺀다). 일감의 세션은 그대로, 에이전트 멤버로 등록하지 않는다
    mcp.call("add_comment", args("number", n, "text", "일감의 세션으로"));
    String side = mcp.call("add_comment", args("number", n, "text", "내 세션으로", "agentSession", "  side-session  "));
    assertThat((Boolean) read(side, "$.commented")).isTrue();
    // log_work — 같은 규칙. 기록 줄과 이력 줄이 같은 이름
    mcp.call("log_work", args("number", n, "hours", 0.25, "note", "일감의 세션"));
    mcp.call("log_work", args("number", n, "hours", 0.5, "note", "내 세션", "agentSession", "side-session"));

    List<Map<String, Object>> lines = journal(s, n);
    assertThat(comment(lines, "일감의 세션으로")).containsEntry("agentSession", "moai-works");
    assertThat(comment(lines, "내 세션으로")).containsEntry("agentSession", "side-session");
    assertThat(workLogLines(lines)).extracting(j -> j.get("agentSession")).containsExactly("moai-works", "side-session");
    List<Map<String, Object>> logs = TestApi.read(api.get(s.owner(), ISSUE, s.ws(), n), "$.workLogs");
    assertThat(logs).extracting(w -> w.get("note") + "=" + w.get("agentSession"))
        .containsExactlyInAnyOrder("일감의 세션=moai-works", "내 세션=side-session");
    api.get(s.owner(), ISSUE, s.ws(), n).andExpect(jsonPath("$.agentSession").value("moai-works"));
    List<String> agents = TestApi.read(api.get(s.owner(), "/workspaces/{ws}/projects/{p}/agents", s.ws(), P), "$[*].name");
    assertThat(agents).containsExactly("moai-works");

    // update_issue — 그 고치기로 바뀐 뒤의 세션(준 이름)
    String moved = mcp.call("update_issue", args("number", n, "rowVersion", rv, "agentSession", "moai-wiki", "comment", "넘겨받음"));
    rv = read(moved, "$.summary.rowVersion");
    assertThat(comment(journal(s, n), "넘겨받음")).containsEntry("agentSession", "moai-wiki");
    // 세션을 비우는 고치기 · 그 뒤의 댓글 — 이름이 없으면 없음
    rv = read(mcp.call("update_issue", args("number", n, "rowVersion", rv, "clear", "agentSession", "comment", "세션 비움")),
        "$.summary.rowVersion");
    mcp.call("add_comment", args("number", n, "text", "세션 없는 일감"));
    lines = journal(s, n);
    assertThat(comment(lines, "세션 비움")).containsEntry("agentSession", null);
    assertThat(comment(lines, "세션 없는 일감")).containsEntry("agentSession", null);

    // 사람이 웹에서 쓴 줄 — 일감에 세션이 있어도 없음(REST 는 null 로 싣는다)
    api.patch(s.curator(), ISSUE, Map.of("rowVersion", rv, "agentSession", "moai-works"), s.ws(), n).andExpect(status().isOk());
    api.post(s.curator(), ISSUE + "/comments", Map.of("notes", "사람이 단 댓글"), s.ws(), n).andExpect(status().isOk());
    api.post(s.curator(), ISSUE + "/work-logs", Map.of("workDate", "2026-11-20", "hours", 1, "note", "사람이 적음"), s.ws(), n)
        .andExpect(status().isOk());
    lines = journal(s, n);
    assertThat(comment(lines, "사람이 단 댓글")).containsEntry("via", "WEB").containsEntry("agentSession", null);
    assertThat(lines.get(lines.size() - 1)).containsEntry("via", "WEB").containsEntry("agentSession", null);
    logs = TestApi.read(api.get(s.owner(), ISSUE, s.ws(), n), "$.workLogs");
    assertThat(logs.stream().filter(w -> "사람이 적음".equals(w.get("note"))).findFirst().orElseThrow())
        .containsEntry("agentSession", null);
    // REST 댓글 목록에도
    List<Map<String, Object>> comments = TestApi.read(api.get(s.owner(), ISSUE + "/comments?size=50", s.ws(), n), "$.items");
    assertThat(comment(comments, "내 세션으로")).containsEntry("agentSession", "side-session");
    assertThat(comment(comments, "사람이 단 댓글")).containsEntry("agentSession", null);

    // MCP 결과 — list_comments · get_issue 이력 · workLogs 에 세션 이름(빈 값은 싣지 않는다)
    List<Map<String, Object>> listed = read(mcp.call("list_comments", args("number", n)), "$.items");
    assertThat(comment(listed, "내 세션으로")).containsEntry("agentSession", "side-session");
    assertThat(comment(listed, "사람이 단 댓글")).doesNotContainKey("agentSession");
    String issue = mcp.call("get_issue", args("number", n, "include", List.of("journal", "workLogs")));
    List<Map<String, Object>> mcpLines = read(issue, "$.journal");
    assertThat(comment(mcpLines, "일감의 세션으로")).containsEntry("agentSession", "moai-works");
    assertThat(comment(mcpLines, "사람이 단 댓글")).doesNotContainKey("agentSession");
    List<String> mcpLogs = read(issue, "$.workLogs[?(@.note == '내 세션')].agentSession");
    assertThat(mcpLogs).containsExactly("side-session");
  }

  @Test
  void 세션_이름은_일감의_세션과_같은_규칙으로_본다() throws Exception {
    Space s = space();
    McpTestClient mcp = client(s.curator());
    int n = read(mcp.call("create_issue", args("project", P, "type", "feature", "title", "규칙")), "$.summary.number");
    for (String bad : List.of("x".repeat(101), "줄\n바꿈")) {
      assertThat((String) read(mcp.call("add_comment", args("number", n, "text", "x", "agentSession", bad)), "$.error.code"))
          .isEqualTo("INVALID_AGENT_SESSION");
      assertThat((String) read(mcp.call("log_work", args("number", n, "hours", 0.1, "agentSession", bad)), "$.error.code"))
          .isEqualTo("INVALID_AGENT_SESSION");
    }
    // 세션이 없는 일감에 빈 이름 — 없음
    mcp.call("add_comment", args("number", n, "text", "빈 이름", "agentSession", "   "));
    assertThat(comment(journal(s, n), "빈 이름")).containsEntry("agentSession", null);
  }

  @Test
  void 알림의_누가_뒤에_세션_이름을_싣는다() throws Exception {
    Space s = space();
    McpTestClient mcp = client(s.curator());
    int n = read(mcp.call("create_issue", args("project", P, "type", "feature", "title", "알림", "agentSession", "moai-works",
        "assignee", s.member().username())), "$.summary.number");
    api.get(s.member(), "/me/notifications?unread=true")
        .andExpect(jsonPath("$.items[?(@.kind == 'issue.assigned')].params.agentSession").value("moai-works"));
    api.post(s.member(), "/me/notifications/read-all", Map.of()).andExpect(status().is2xxSuccessful());
    mcp.call("add_comment", args("number", n, "text", "내 세션의 댓글", "agentSession", "side-session"));
    api.get(s.member(), "/me/notifications?unread=true")
        .andExpect(jsonPath("$.items[?(@.kind == 'issue.commented')].params.via").value("MCP"))
        .andExpect(jsonPath("$.items[?(@.kind == 'issue.commented')].params.agentSession").value("side-session"));
    // 사람이 웹에서 한 일 — 세션 이름 없음
    api.post(s.member(), "/me/notifications/read-all", Map.of()).andExpect(status().is2xxSuccessful());
    api.post(s.curator(), ISSUE + "/comments", Map.of("notes", "사람"), s.ws(), n).andExpect(status().isOk());
    List<Map<String, Object>> items = TestApi.read(api.get(s.member(), "/me/notifications?unread=true"), "$.items");
    assertThat(items).hasSize(1);
    @SuppressWarnings("unchecked")
    Map<String, Object> params = (Map<String, Object>) items.get(0).get("params");
    assertThat(params).containsEntry("via", "WEB").doesNotContainKey("agentSession");
  }

  @Test
  void 한_번_적은_세션_이름은_DB_가_바꾸지_못하게_한다() throws Exception {
    Space s = space();
    McpTestClient mcp = client(s.curator());
    int n = read(mcp.call("create_issue", args("project", P, "type", "feature", "title", "보호", "agentSession", "moai-works")),
        "$.summary.number");
    mcp.call("add_comment", args("number", n, "text", "지킬 줄"));
    String logId = read(mcp.call("log_work", args("number", n, "hours", 0.2, "note", "지킬 기록")), "$.workLogId");
    String lineId = (String) comment(journal(s, n), "지킬 줄").get("id");
    assertThatThrownBy(() -> jdbc.update("UPDATE issue_journal SET agent_session = 'other' WHERE id = ?::uuid", lineId))
        .hasMessageContaining("이력 줄은 바꿀 수 없다");
    assertThatThrownBy(() -> jdbc.update("UPDATE issue_journal SET agent_session = NULL WHERE id = ?::uuid", lineId))
        .hasMessageContaining("이력 줄은 바꿀 수 없다");
    // 작업 시간 기록 — 지움 표시와 함께여도 이름은 바꾸지 못한다
    assertThatThrownBy(() -> jdbc.update("UPDATE issue_work_log SET agent_session = 'other', deleted_at = now(),"
        + " deleted_by = user_id WHERE id = ?::uuid", logId)).hasMessageContaining("지움 표시(deleted_at · deleted_by) 말고는");
    // 규칙(CHECK) — 앞뒤 공백 · 제어 문자는 DB 도 막는다
    assertThatThrownBy(() -> jdbc.update("INSERT INTO issue_journal (id, issue_id, actor_type, actor_name, via, agent_session,"
        + " created_at) SELECT gen_random_uuid(), issue_id, 'USER', 'x', 'MCP', ' x', now() FROM issue_journal WHERE id = ?::uuid",
        lineId)).hasMessageContaining("ck_issue_journal_agent_session");
    assertThat(jdbc.queryForObject("SELECT agent_session FROM issue_work_log WHERE id = ?::uuid", String.class, logId))
        .isEqualTo("moai-works");
  }
}
