package com.moai.works.api.issue;

import static com.jayway.jsonpath.JsonPath.read;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.moai.works.api.support.ApiTest;
import com.moai.works.api.support.McpTestClient;
import com.moai.works.api.support.TestApi;
import com.moai.works.api.support.TestUser;

/**
 * 첨부의 에이전트 세션 이름(2026-11-26, 일감 #312 — V32) — "첨부파일도 에이전트 세션네임". 이력 줄 · 작업 시간 기록(일감 #304)과 같은 규칙:
 * MCP 로 올린 첨부는 올리기 티켓이 준 세션 이름, 없으면 올릴(PUT) 때의 그 일감의 agentSession. 사람이 웹에서 올린 첨부는 없음.
 */
@ApiTest
class AttachmentAgentSessionTest {

  private static final String P = "files";
  private static final String ISSUE = "/workspaces/{ws}/issues/{n}";
  private static final String ATTACHMENTS = ISSUE + "/attachments";

  @Autowired
  TestApi api;
  @Autowired
  MockMvc mvc;
  @Autowired
  JdbcTemplate jdbc;

  private record Space(String ws, TestUser owner, TestUser curator, McpTestClient mcp) {
  }

  private Space space() throws Exception {
    TestUser owner = api.user("owner");
    String ws = api.teamWorkspace(owner, "첨부 이름표");
    TestUser curator = api.user("curator");
    api.join(ws, owner, curator, "curator");
    api.post(owner, "/workspaces/{ws}/projects", Map.of("key", P, "name", "첨부 이름표 시험", "visibility", "WORKSPACE"), ws)
        .andExpect(status().isCreated());
    String key = TestApi.read(api.post(curator, "/me/mcp-keys", Map.of("name", "이름표 키")).andExpect(status().isCreated()), "$.key");
    return new Space(ws, owner, curator, new McpTestClient(mvc, key));
  }

  private static Map<String, Object> args(Object... kv) {
    Map<String, Object> m = new HashMap<>();
    for (int i = 0; i < kv.length; i += 2) {
      m.put((String) kv[i], kv[i + 1]);
    }
    return m;
  }

  /** 올리기 티켓을 받는다 — {@code session} 이 null 이면 agentSession 을 주지 않는다 */
  private String ticket(Space s, int n, String name, byte[] body, String session) throws Exception {
    Map<String, Object> a = args("project", P, "number", n, "fileName", name, "size", body.length, "contentType", "text/plain");
    if (session != null) {
      a.put("agentSession", session);
    }
    return read(s.mcp().call("request_attachment_upload", a), "$.ticket");
  }

  /** 티켓으로 올리고 만든 첨부의 응답(JSON) */
  private String putFile(String ticket, byte[] body) throws Exception {
    return mvc.perform(put("/mcp/files/upload").header(HttpHeaders.AUTHORIZATION, "Bearer " + ticket)
        .contentType(MediaType.APPLICATION_OCTET_STREAM).content(body)).andExpect(status().isCreated())
        .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
  }

  private static byte[] bytes(String text) {
    return text.getBytes(StandardCharsets.UTF_8);
  }

  /** REST 첨부 목록의 그 이름의 첨부 */
  private Map<String, Object> listed(Space s, int n, String name) throws Exception {
    List<Map<String, Object>> rows = TestApi.read(api.get(s.owner(), ATTACHMENTS, s.ws(), n).andExpect(status().isOk()), "$");
    return rows.stream().filter(a -> name.equals(a.get("fileName"))).findFirst().orElseThrow();
  }

  /** 첨부를 더한 이력 줄의 세션 이름 — 그 첨부 id 의 줄 */
  @SuppressWarnings("unchecked")
  private Object journalSession(Space s, int n, String attachmentId) throws Exception {
    List<Map<String, Object>> lines = TestApi.read(api.get(s.owner(), ISSUE, s.ws(), n), "$.journal");
    return lines.stream().filter(j -> ((List<Map<String, Object>>) j.get("changes")).stream()
        .anyMatch(c -> "attachment".equals(c.get("field")) && c.get("after") instanceof Map<?, ?> after
            && attachmentId.equals(after.get("id")))).findFirst().orElseThrow().get("agentSession");
  }

  @Test
  void MCP_첨부는_티켓이_준_세션_이름_없으면_올릴_때_일감의_세션_웹_첨부는_없음() throws Exception {
    Space s = space();
    String created = s.mcp().call("create_issue", args("project", P, "type", "feature", "title", "첨부 이름표",
        "agentSession", "moai-works"));
    int n = read(created, "$.summary.number");
    int rv = read(created, "$.summary.rowVersion");

    // 세션 이름을 주지 않은 티켓 — 일감의 세션
    String plain = putFile(ticket(s, n, "plain.txt", bytes("일감의 세션"), null), bytes("일감의 세션"));
    assertThat((String) read(plain, "$.agentSession")).isEqualTo("moai-works");
    // 준 이름(앞뒤 공백을 뺀다) — 일감의 세션은 그대로, 에이전트 멤버로 등록하지 않는다
    String side = putFile(ticket(s, n, "side.txt", bytes("내 세션"), "  side-session  "), bytes("내 세션"));
    assertThat((String) read(side, "$.agentSession")).isEqualTo("side-session");
    api.get(s.owner(), ISSUE, s.ws(), n).andExpect(jsonPath("$.agentSession").value("moai-works"));
    List<String> agents = TestApi.read(api.get(s.owner(), "/workspaces/{ws}/projects/{p}/agents", s.ws(), P), "$[*].name");
    assertThat(agents).containsExactly("moai-works");

    // 이름을 주지 않은 티켓은 받을 때가 아니라 올릴 때의 일감 세션 — 그 사이 바뀌면 바뀐 이름
    String later = ticket(s, n, "later.txt", bytes("나중"), null);
    s.mcp().call("update_issue", args("number", n, "rowVersion", rv, "agentSession", "moai-wiki"));
    String laterView = putFile(later, bytes("나중"));
    assertThat((String) read(laterView, "$.agentSession")).isEqualTo("moai-wiki");

    // 사람이 웹에서 올린 첨부 — 일감에 세션이 있어도 없음(REST 는 null 로 싣는다)
    api.upload(s.curator(), ATTACHMENTS, "web.txt", "text/plain", bytes("사람"), Map.of(), s.ws(), n)
        .andExpect(status().isCreated()).andExpect(jsonPath("$.via").value("WEB"))
        .andExpect(jsonPath("$.agentSession").value(Matchers.nullValue()));

    // REST 목록
    assertThat(listed(s, n, "plain.txt")).containsEntry("via", "MCP").containsEntry("agentSession", "moai-works");
    assertThat(listed(s, n, "side.txt")).containsEntry("agentSession", "side-session");
    assertThat(listed(s, n, "later.txt")).containsEntry("agentSession", "moai-wiki");
    Map<String, Object> web = listed(s, n, "web.txt");
    assertThat(web).containsEntry("via", "WEB").containsKey("agentSession");
    assertThat(web.get("agentSession")).isNull();

    // 이력 줄도 첨부와 같은 이름
    assertThat(journalSession(s, n, read(side, "$.id"))).isEqualTo("side-session");
    assertThat(journalSession(s, n, read(plain, "$.id"))).isEqualTo("moai-works");
    assertThat(journalSession(s, n, (String) web.get("id"))).isNull();

    // MCP list_attachments — MCP 첨부에 세션 이름, 웹 첨부에는 싣지 않는다(빈 값)
    List<Map<String, Object>> mcp = read(s.mcp().call("list_attachments", args("project", P, "number", n)), "$.attachments");
    Map<String, Map<String, Object>> byName = new HashMap<>();
    mcp.forEach(a -> byName.put((String) a.get("fileName"), a));
    assertThat(byName.get("side.txt")).containsEntry("agentSession", "side-session");
    assertThat(byName.get("plain.txt")).containsEntry("agentSession", "moai-works");
    assertThat(byName.get("web.txt")).doesNotContainKey("agentSession");
  }

  @Test
  void 세션_이름은_일감의_세션과_같은_규칙으로_보고_세션이_없으면_없음() throws Exception {
    Space s = space();
    int n = read(s.mcp().call("create_issue", args("project", P, "type", "feature", "title", "규칙")), "$.summary.number");
    for (String bad : List.of("x".repeat(101), "줄\n바꿈")) {
      String out = s.mcp().call("request_attachment_upload", args("project", P, "number", n, "fileName", "a.txt", "size", 1,
          "agentSession", bad));
      assertThat((String) read(out, "$.error.code")).isEqualTo("INVALID_AGENT_SESSION");
    }
    // 세션이 없는 일감에 빈 이름 — 없음
    String view = putFile(ticket(s, n, "none.txt", bytes("x"), "   "), bytes("x"));
    assertThat((Object) read(view, "$.agentSession")).isNull();
  }

  @Test
  void 한_번_적은_세션_이름은_DB_가_바꾸지_못하고_지움_옮기기는_그대로_된다() throws Exception {
    Space s = space();
    api.post(s.owner(), "/workspaces/{ws}/projects", Map.of("key", "other", "name", "옮길 곳", "visibility", "WORKSPACE"), s.ws())
        .andExpect(status().isCreated());
    String created = s.mcp().call("create_issue", args("project", P, "type", "feature", "title", "보호", "agentSession", "moai-works"));
    int n = read(created, "$.summary.number");
    String kept = read(putFile(ticket(s, n, "kept.txt", bytes("지킬 첨부"), "side-session"), bytes("지킬 첨부")), "$.id");
    String gone = read(putFile(ticket(s, n, "gone.txt", bytes("지울 첨부"), null), bytes("지울 첨부")), "$.id");

    assertThatThrownBy(() -> jdbc.update("UPDATE issue_attachment SET agent_session = 'other' WHERE id = ?::uuid", kept))
        .hasMessageContaining("지움 표시만 한 번 할 수 있다");
    assertThatThrownBy(() -> jdbc.update("UPDATE issue_attachment SET agent_session = NULL WHERE id = ?::uuid", kept))
        .hasMessageContaining("지움 표시만 한 번 할 수 있다");
    // 지움 표시와 함께여도 이름은 바꾸지 못한다
    assertThatThrownBy(() -> jdbc.update("UPDATE issue_attachment SET agent_session = 'other', deleted_at = now(),"
        + " deleted_by = uploaded_by WHERE id = ?::uuid", kept)).hasMessageContaining("지움 표시만 한 번 할 수 있다");
    // 규칙(CHECK) — 앞뒤 공백 · 제어 문자는 DB 도 막는다(티켓도)
    assertThatThrownBy(() -> jdbc.update("UPDATE attachment_ticket SET agent_session = ' x' WHERE kind = 'UPLOAD'"))
        .hasMessageContaining("ck_attachment_ticket_agent_session");

    // 지우기(지움 표시)는 그대로 된다 — 이름은 남는다
    String deleted = s.mcp().call("delete_attachment", args("project", P, "number", n, "attachmentId", gone));
    assertThat((Boolean) read(deleted, "$.deleted")).isTrue();
    assertThat(jdbc.queryForObject("SELECT agent_session FROM issue_attachment WHERE id = ?::uuid AND deleted_at IS NOT NULL",
        String.class, gone)).isEqualTo("moai-works");

    // 일감을 옮기면 첨부의 프로젝트가 따라간다 — 세션 이름은 그대로
    int rv = TestApi.read(api.get(s.owner(), ISSUE, s.ws(), n), "$.rowVersion");
    api.patch(s.curator(), ISSUE, Map.of("rowVersion", rv, "project", "other"), s.ws(), n).andExpect(status().isOk())
        .andExpect(jsonPath("$.project.key").value("other"));
    assertThat(jdbc.queryForObject("SELECT agent_session FROM issue_attachment WHERE id = ?::uuid", String.class, kept))
        .isEqualTo("side-session");
    assertThat(listed(s, n, "kept.txt")).containsEntry("agentSession", "side-session");
  }
}
