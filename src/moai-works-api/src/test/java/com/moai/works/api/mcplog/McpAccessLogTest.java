package com.moai.works.api.mcplog;

import static com.jayway.jsonpath.JsonPath.read;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.moai.works.api.config.EndPointPaths;
import com.moai.works.api.support.ApiTest;
import com.moai.works.api.support.TestApi;
import com.moai.works.api.support.TestUser;

import tools.jackson.databind.json.JsonMapper;

/**
 * MCP 접근 기록 (FR-MCP-006 · FR-MCP-010 — 2026-11-22 사용자: "MCP 키 발급을 해 주면 감사 데이터 모두 기록해야 해. 클라이언트 정보 자세하게.
 * 읽기, 수정, 삭제, 생성 모두" · "필터 기능도 잘 만들고").
 *
 * <ul>
 *   <li>요청 하나 = 한 줄 — 연결 시작 · 알림 · 도구 목록 · 도구 호출(읽기 · 만들기 · 고치기 · 지우기 · 연결) · 세션 끝 · 키 검사 거절</li>
 *   <li>줄마다 키 · 주인 · 입구 · 워크스페이스 · 도구 · 작업 종류 · 대상 · 결과 · 걸린 시간 · 클라이언트 정보 · User-Agent · IP · 세션 id</li>
 *   <li>키 원문 · 설명 본문은 어디에도 없다. DB 가 고치기 · 지우기를 막는다. 저장하지 못하면 그 요청도 실패한다(쓰기는 되돌린다)</li>
 *   <li>audit.read 는 그 워크스페이스 줄을 모두, 키 주인은 자기 키의 줄을 모두(워크스페이스 없는 줄까지). 다른 워크스페이스 줄은 섞이지 않는다</li>
 * </ul>
 */
@ApiTest
class McpAccessLogTest {

  private static final String P = "logp";
  private static final String WS_LOG = "/workspaces/{ws}/mcp-access-log";
  private static final String MY_LOG = "/me/mcp-keys/{id}/access-log";
  private static final JsonMapper JSON = JsonMapper.builder().build();

  @Autowired
  TestApi api;
  @Autowired
  MockMvc mvc;
  @Autowired
  JdbcTemplate jdbc;

  // ── 준비 ──────────────────────────────────────────────────────────

  private record Space(String ws, UUID wsId, TestUser owner, TestUser curator, TestUser member) {
  }

  private Space space(String name) throws Exception {
    TestUser owner = api.user("owner");
    String ws = api.teamWorkspace(owner, name);
    TestUser curator = api.user("curator");
    TestUser member = api.user("member");
    api.join(ws, owner, curator, "curator");
    api.join(ws, owner, member, "member");
    api.post(owner, "/workspaces/{ws}/projects", Map.of("key", P, "name", "접근 기록", "visibility", "WORKSPACE"), ws)
        .andExpect(status().isCreated());
    UUID wsId = jdbc.queryForObject("SELECT id FROM workspace WHERE slug = ?", UUID.class, ws);
    return new Space(ws, wsId, owner, curator, member);
  }

  private record Key(UUID id, String token) {
  }

  private Key key(TestUser who) throws Exception {
    var issued = api.post(who, "/me/mcp-keys", Map.of("name", "기록 시험 키")).andExpect(status().isCreated());
    return new Key(UUID.fromString(TestApi.read(issued, "$.view.id")), TestApi.read(issued, "$.key"));
  }

  private static Map<String, Object> args(Object... kv) {
    Map<String, Object> m = new LinkedHashMap<>();
    for (int i = 0; i < kv.length; i += 2) {
      m.put((String) kv[i], kv[i + 1]);
    }
    return m;
  }

  /** 시험용 MCP 연결 — User-Agent · X-Forwarded-For · 접속 주소를 정할 수 있다 */
  private final class Conn {
    final String token;
    final String path;
    String userAgent = "AccessLogAgent/2.5 (시험)";
    String forwardedFor;
    String remoteAddr;
    String session;
    int id = 10;

    Conn(String token, String path) {
      this.token = token;
      this.path = path;
    }

    Conn(String token) {
      this(token, "/mcp/issue");
    }

    MvcResult send(Map<String, Object> body) throws Exception {
      MockHttpServletRequestBuilder req = post(path).accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
          .contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(body));
      return perform(req);
    }

    MvcResult perform(MockHttpServletRequestBuilder req) throws Exception {
      if (token != null) {
        req.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
      }
      if (userAgent != null) {
        req.header(HttpHeaders.USER_AGENT, userAgent);
      }
      if (session != null) {
        req.header("Mcp-Session-Id", session);
      }
      if (forwardedFor != null) {
        req.header("X-Forwarded-For", forwardedFor);
      }
      if (remoteAddr != null) {
        String addr = remoteAddr;
        req.with(r -> {
          r.setRemoteAddr(addr);
          return r;
        });
      }
      MvcResult r = mvc.perform(req).andReturn();
      if (r.getRequest().isAsyncStarted()) {
        r = mvc.perform(asyncDispatch(r)).andReturn();
      }
      return r;
    }

    /** 연결 시작만 — 결과는 부른 쪽이 본다 */
    MvcResult initialize() throws Exception {
      return send(Map.of("jsonrpc", "2.0", "id", 1, "method", "initialize", "params", Map.of(
          "protocolVersion", "2025-06-18", "capabilities", Map.of("roots", Map.of(), "sampling", Map.of()),
          "clientInfo", Map.of("name", "access-test-client", "version", "2.5.0", "title", "접근 기록 시험"))));
    }

    /** 연결 시작 + 시작했다는 알림 */
    Conn connect() throws Exception {
      MvcResult init = initialize();
      assertThat(init.getResponse().getStatus()).isEqualTo(200);
      session = init.getResponse().getHeader("Mcp-Session-Id");
      assertThat(session).isNotBlank();
      send(Map.of("jsonrpc", "2.0", "method", "notifications/initialized"));
      return this;
    }

    /** 도구를 부르고 JSON-RPC 답 전체(글자)를 돌려준다 */
    String rpc(String tool, Map<String, Object> arguments) throws Exception {
      return message(send(Map.of("jsonrpc", "2.0", "id", ++id, "method", "tools/call", "params",
          Map.of("name", tool, "arguments", arguments))));
    }

    /** 도구 결과(글자) */
    String call(String tool, Map<String, Object> arguments) throws Exception {
      return read(rpc(tool, arguments), "$.result.content[0].text");
    }

    String toolsList() throws Exception {
      return message(send(Map.of("jsonrpc", "2.0", "id", ++id, "method", "tools/list")));
    }
  }

  private static String message(MvcResult r) throws Exception {
    String body = r.getResponse().getContentAsString(StandardCharsets.UTF_8);
    return body.lines().filter(l -> l.startsWith("data:")).map(l -> l.substring(5).trim()).findFirst().orElse(body);
  }

  private List<Map<String, Object>> rows(UUID keyId) {
    return jdbc.queryForList("SELECT * FROM mcp_access_log WHERE key_id = ? ORDER BY occurred_at, id", keyId);
  }

  private List<Map<String, Object>> rowsByAgent(String userAgent) {
    return jdbc.queryForList("SELECT * FROM mcp_access_log WHERE user_agent = ? ORDER BY occurred_at, id", userAgent);
  }

  private static Map<String, Object> only(List<Map<String, Object>> rows, String column, Object value) {
    List<Map<String, Object>> found = rows.stream().filter(r -> value.equals(r.get(column))).toList();
    assertThat(found).as(column + " = " + value).hasSize(1);
    return found.get(0);
  }

  private static Map<String, Object> tool(List<Map<String, Object>> rows, String tool) {
    return only(rows, "tool", tool);
  }

  // ── 한 줄씩 · 무엇이 남나 ─────────────────────────────────────────

  @Test
  void 요청마다_한_줄이고_작업_종류_대상_결과_클라이언트_정보가_남는다() throws Exception {
    Space s = space("기록 한 줄");
    Key k = key(s.curator());
    Conn c = new Conn(k.token());
    c.forwardedFor = "203.0.113.7";   // 믿는 중계(127.0.0.1)가 붙인 클라이언트 IP
    c.connect();
    c.toolsList();
    c.call("search_issues", args("project", P));
    int n = read(c.call("create_issue", args("project", P, "type", "bug", "title", "기록할 일감", "description", "본문")), "$.number");
    int m = read(c.call("create_issue", args("project", P, "type", "bug", "title", "연결 대상")), "$.number");
    int rowVersion = read(c.call("get_issue", args("project", P, "number", n)), "$.rowVersion");
    c.call("update_issue", args("project", P, "number", n, "rowVersion", rowVersion, "title", "고친 제목"));
    c.call("add_comment", args("project", P, "number", n, "text", "댓글"));
    c.call("link_issues", args("project", P, "number", n, "target", m, "type", "RELATES"));
    c.call("log_work", args("project", P, "number", n, "workDate", LocalDate.now(ZoneId.of("Asia/Seoul")).toString(), "hours", 1));
    c.call("list_projects", args());
    // 지우기 권한은 소유자에게 — 소유자 키로 지운다(다른 키의 줄)
    Key ownerKey = key(s.owner());
    Conn o = new Conn(ownerKey.token()).connect();
    o.call("delete_issue", args("project", P, "number", m, "reason", "시험"));

    List<Map<String, Object>> rows = rows(k.id());
    // 연결 시작 · 알림 · 도구 목록 · 도구 호출 9번 = 12줄
    assertThat(rows).hasSize(12);
    Map<String, Object> init = only(rows, "mcp_method", "initialize");
    assertThat(init.get("operation")).isEqualTo("CONNECT");
    assertThat(init.get("outcome")).isEqualTo("OK");
    assertThat(init.get("mcp_session_id")).isEqualTo(c.session);
    assertThat(init.get("protocol_version")).isEqualTo("2025-06-18");
    assertThat(init.get("client_capabilities")).isEqualTo("roots,sampling");
    assertThat(init.get("client_title")).isEqualTo("접근 기록 시험");
    assertThat(init.get("workspace_id")).isNull();
    assertThat(only(rows, "mcp_method", "notifications/initialized").get("operation")).isEqualTo("CONNECT");
    assertThat(only(rows, "mcp_method", "tools/list").get("operation")).isEqualTo("LIST");

    assertThat(tool(rows, "search_issues").get("operation")).isEqualTo("READ");
    assertThat(tool(rows, "get_issue").get("operation")).isEqualTo("READ");
    assertThat(tool(rows, "get_issue").get("issue_number")).isEqualTo(n);
    assertThat(tool(rows, "update_issue").get("operation")).isEqualTo("UPDATE");
    assertThat(tool(rows, "add_comment").get("operation")).isEqualTo("CREATE");
    assertThat(tool(rows, "log_work").get("operation")).isEqualTo("CREATE");
    Map<String, Object> link = tool(rows, "link_issues");
    assertThat(link.get("operation")).isEqualTo("LINK");
    assertThat(link.get("target_ref")).isEqualTo("#" + m);
    assertThat(rows.stream().filter(r -> "create_issue".equals(r.get("tool"))))
        .allSatisfy(r -> assertThat(r.get("operation")).isEqualTo("CREATE")).hasSize(2);
    // 여러 워크스페이스에 걸친 목록은 워크스페이스가 없다
    assertThat(tool(rows, "list_projects").get("workspace_id")).isNull();
    assertThat(only(rows(ownerKey.id()), "tool", "delete_issue").get("operation")).isEqualTo("DELETE");

    for (Map<String, Object> r : rows) {
      assertThat(r.get("key_id")).isEqualTo(k.id());
      assertThat(r.get("key_owner_id")).isEqualTo(s.curator().id());
      assertThat((String) r.get("key_owner_name")).isNotBlank();
      assertThat((String) r.get("key_prefix")).startsWith("mai-wks-");
      assertThat(r.get("entry")).isEqualTo("ISSUE");
      assertThat(r.get("client_name")).isEqualTo("access-test-client");
      assertThat(r.get("client_version")).isEqualTo("2.5.0");
      assertThat(r.get("user_agent")).isEqualTo("AccessLogAgent/2.5 (시험)");
      assertThat(r.get("client_ip")).isEqualTo("203.0.113.7");
      assertThat(r.get("mcp_session_id")).isEqualTo(c.session);
      assertThat((Integer) r.get("duration_ms")).isGreaterThanOrEqualTo(0);
      assertThat((String) r.get("trace_id")).isNotBlank();
      if ("tools/call".equals(r.get("mcp_method")) && !"list_projects".equals(r.get("tool"))) {
        assertThat(r.get("workspace_id")).isEqualTo(s.wsId());
        assertThat(r.get("project_key")).isEqualTo(P);
        assertThat(r.get("outcome")).isEqualTo("OK");
        assertThat(r.get("http_status")).isEqualTo(200);
      }
    }
  }

  @Test
  void 직접_붙은_클라이언트가_꾸민_X_Forwarded_For_는_쓰지_않는다() throws Exception {
    Space s = space("IP 꾸미기");
    Key k = key(s.member());
    Conn c = new Conn(k.token());
    c.remoteAddr = "198.51.100.9";   // 믿는 중계가 아니다
    c.forwardedFor = "1.2.3.4";
    c.connect();
    assertThat(rows(k.id())).isNotEmpty().allSatisfy(r -> assertThat(r.get("client_ip")).isEqualTo("198.51.100.9"));
  }

  @Test
  void 권한이_없는_도구_호출은_거부로_없는_도구는_오류로_남는다() throws Exception {
    Space s = space("거부 오류");
    Key curatorKey = key(s.curator());
    int n = read(new Conn(curatorKey.token()).connect().call("create_issue", args("project", P, "type", "bug", "title", "x")), "$.number");
    Key k = key(s.member());
    Conn c = new Conn(k.token()).connect();
    assertThat((String) read(c.call("delete_issue", args("project", P, "number", n, "reason", "x")), "$.error.code"))
        .isEqualTo("PERMISSION_REQUIRED");
    c.rpc("drop_everything", args("project", P));
    c.call("get_issue", args("project", "nope", "number", 1));

    List<Map<String, Object>> rows = rows(k.id());
    Map<String, Object> denied = tool(rows, "delete_issue");
    assertThat(denied.get("operation")).isEqualTo("DELETE");
    assertThat(denied.get("outcome")).isEqualTo("DENIED");
    assertThat(denied.get("http_status")).isEqualTo(403);
    assertThat(denied.get("error_code")).isEqualTo("PERMISSION_REQUIRED");
    // 키 주인이 그 워크스페이스에서 시도했다 — 그곳 감사 권한자에게 보인다
    assertThat(denied.get("workspace_id")).isEqualTo(s.wsId());
    Map<String, Object> unknown = tool(rows, "drop_everything");
    assertThat(unknown.get("outcome")).isEqualTo("ERROR");
    assertThat(unknown.get("error_code")).isEqualTo("TOOL_NOT_FOUND");
    Map<String, Object> missing = tool(rows, "get_issue");
    assertThat(missing.get("outcome")).isEqualTo("ERROR");
    assertThat(missing.get("error_code")).isEqualTo("PROJECT_NOT_FOUND");
    assertThat(missing.get("workspace_id")).isNull();
  }

  @Test
  void 키_검사에서_거절된_요청도_거부로_남는다() throws Exception {
    Space s = space("거절");
    String agent = "RejectAgent/" + UUID.randomUUID();
    Map<String, Object> init = Map.of("jsonrpc", "2.0", "id", 1, "method", "initialize", "params", Map.of(
        "protocolVersion", "2025-06-18", "capabilities", Map.of(), "clientInfo", Map.of("name", "rejected-client", "version", "9")));

    Conn none = new Conn(null);
    none.userAgent = agent;
    assertThat(none.send(init).getResponse().getStatus()).isEqualTo(401);
    Conn wrong = new Conn("mai-wks-" + "A".repeat(43));
    wrong.userAgent = agent;
    assertThat(wrong.send(init).getResponse().getStatus()).isEqualTo(401);
    Conn elsewhere = new Conn(null, "/mcp/other");
    elsewhere.userAgent = agent;
    assertThat(elsewhere.send(init).getResponse().getStatus()).isEqualTo(404);

    List<Map<String, Object>> anonymous = rowsByAgent(agent);
    assertThat(anonymous).hasSize(3).allSatisfy(r -> {
      assertThat(r.get("outcome")).isEqualTo("DENIED");
      assertThat(r.get("key_id")).isNull();
      assertThat(r.get("operation")).isEqualTo("CONNECT");
      assertThat(r.get("client_name")).isEqualTo("rejected-client");
    });
    assertThat(anonymous).extracting(r -> r.get("error_code"))
        .containsExactlyInAnyOrder("MCP_KEY_REQUIRED", "MCP_KEY_INVALID", "MCP_ENDPOINT_NOT_FOUND");
    Map<String, Object> other = only(anonymous, "error_code", "MCP_ENDPOINT_NOT_FOUND");
    assertThat(other.get("entry")).isEqualTo("UNKNOWN");
    assertThat(other.get("request_path")).isEqualTo("/mcp/other");
    assertThat(other.get("http_status")).isEqualTo(404);

    // 만료된 키 · 주인 계정 정지 — 찾은 키는 줄에 남는다
    Key expired = key(s.member());
    api.post(s.member(), "/me/mcp-keys/{id}/expire", Map.of("note", "시험"), expired.id()).andExpect(status().isOk());
    Conn late = new Conn(expired.token());
    assertThat(late.rpc("create_issue", args("project", P, "type", "bug", "title", "늦음"))).contains("MCP_KEY_EXPIRED");
    Map<String, Object> exp = only(rows(expired.id()), "error_code", "MCP_KEY_EXPIRED");
    assertThat(exp.get("outcome")).isEqualTo("DENIED");
    assertThat(exp.get("http_status")).isEqualTo(401);
    assertThat(exp.get("tool")).isEqualTo("create_issue");
    assertThat(exp.get("operation")).isEqualTo("CREATE");
    assertThat(exp.get("key_owner_id")).isEqualTo(s.member().id());

    Key frozen = key(s.curator());
    jdbc.update("UPDATE user_profile SET account_status = 'SUSPENDED' WHERE id = ?", s.curator().id());
    try {
      assertThat(new Conn(frozen.token()).send(init).getResponse().getStatus()).isEqualTo(403);
    } finally {
      jdbc.update("UPDATE user_profile SET account_status = 'ACTIVE' WHERE id = ?", s.curator().id());
    }
    assertThat(only(rows(frozen.id()), "error_code", "MCP_PRINCIPAL_INACTIVE").get("outcome")).isEqualTo("DENIED");
  }

  @Test
  void 세션_끝도_한_줄로_남는다() throws Exception {
    Space s = space("세션 끝");
    Key k = key(s.member());
    Conn c = new Conn(k.token()).connect();
    MvcResult end = c.perform(delete("/mcp/issue"));
    assertThat(end.getResponse().getStatus()).isLessThan(300);
    Map<String, Object> row = only(rows(k.id()), "mcp_method", "session/end");
    assertThat(row.get("operation")).isEqualTo("CONNECT");
    assertThat(row.get("outcome")).isEqualTo("OK");
    assertThat(row.get("client_name")).isEqualTo("access-test-client");
  }

  // ── 남기지 않는 것 · 막는 것 ──────────────────────────────────────

  @Test
  void 키_원문과_설명_본문은_어디에도_없다() throws Exception {
    Space s = space("원문 없음");
    Key k = key(s.curator());
    String marker = "본문표지-" + UUID.randomUUID();
    Conn c = new Conn(k.token());
    c.userAgent = "LeakyAgent/1 key=" + k.token();
    c.connect();
    int n = read(c.call("create_issue", args("project", P, "type", "bug", "title", "제목", "description", "설명 " + marker)), "$.number");
    c.call("add_comment", args("project", P, "number", n, "text", "댓글 " + marker));
    // 키를 인자에 넣어도(실수) 남지 않는다
    c.call("search_issues", args("project", P, "q", k.token()));

    List<Map<String, Object>> rows = rows(k.id());
    assertThat(rows).hasSizeGreaterThanOrEqualTo(5);
    String everything = rows.toString();
    assertThat(everything).doesNotContain(k.token()).doesNotContain(marker);
    assertThat(everything).doesNotContain(k.token().substring("mai-wks-".length()));
    // 설명은 길이와 sha256 만
    String arguments = String.valueOf(tool(rows, "create_issue").get("arguments"));
    assertThat(arguments).contains("\"length\"").contains("\"sha256\"").contains("\"title\": \"제목\"");
    // 감사 기록 · 화면 · CSV 에도 없다
    assertThat(jdbc.queryForList("SELECT details::text AS d FROM audit_log WHERE occurred_at > now() - interval '10 minutes'").toString())
        .doesNotContain(k.token());
    String view = api.get(s.curator(), MY_LOG, k.id()).andExpect(status().isOk()).andReturn().getResponse()
        .getContentAsString(StandardCharsets.UTF_8);
    assertThat(view).doesNotContain(k.token()).doesNotContain(marker);
    String csv = api.get(s.owner(), WS_LOG + ".csv", s.ws()).andExpect(status().isOk()).andReturn().getResponse()
        .getContentAsString(StandardCharsets.UTF_8);
    assertThat(csv).doesNotContain(k.token()).doesNotContain(marker);
  }

  @Test
  void 접근_기록은_DB_가_고치기_지우기_비우기를_막는다() throws Exception {
    Space s = space("추가 전용");
    Key k = key(s.member());
    new Conn(k.token()).connect();
    UUID id = (UUID) rows(k.id()).get(0).get("id");
    assertThatThrownBy(() -> jdbc.update("UPDATE mcp_access_log SET outcome = 'OK', client_ip = '0.0.0.0' WHERE id = ?", id))
        .hasMessageContaining("추가만");
    assertThatThrownBy(() -> jdbc.update("DELETE FROM mcp_access_log WHERE id = ?", id)).hasMessageContaining("추가만");
    assertThatThrownBy(() -> jdbc.execute("TRUNCATE mcp_access_log")).hasMessageContaining("추가만");
    assertThat(rows(k.id())).hasSize(2);
  }

  @Test
  void 접근_기록을_저장하지_못하면_요청도_실패하고_쓰기는_되돌린다() throws Exception {
    Space s = space("기록 실패");
    Key k = key(s.curator());
    Conn c = new Conn(k.token()).connect();
    int before = rows(k.id()).size();
    Integer issues = jdbc.queryForObject("SELECT count(*) FROM issue i JOIN project p ON p.id = i.project_id WHERE p.workspace_id = ?",
        Integer.class, s.wsId());

    jdbc.execute("CREATE OR REPLACE FUNCTION test_refuse_access_log() RETURNS trigger LANGUAGE plpgsql AS $$ "
        + "BEGIN RAISE EXCEPTION '시험: 접근 기록 저장 실패'; END $$");
    jdbc.execute("CREATE TRIGGER trg_test_refuse_access_log BEFORE INSERT ON mcp_access_log "
        + "FOR EACH ROW EXECUTE FUNCTION test_refuse_access_log()");
    try {
      // 쓰기 도구 — 실패하고, 일감도 생기지 않는다
      String created = c.rpc("create_issue", args("project", P, "type", "bug", "title", "남지 않을 일감"));
      assertThat(created).contains("\"error\"").contains(McpAccessRecorder.FAILED_CODE).doesNotContain("\"result\"");
      assertThat(jdbc.queryForObject("SELECT count(*) FROM issue i JOIN project p ON p.id = i.project_id WHERE p.workspace_id = ?",
          Integer.class, s.wsId())).isEqualTo(issues);
      // 읽기 도구 — 결과를 주지 않는다
      String searched = c.rpc("search_issues", args("project", P));
      assertThat(searched).contains("\"error\"").doesNotContain("\"result\"");
      // 도구 목록
      assertThat(c.toolsList()).contains("\"error\"").doesNotContain("\"tools\"");
      // 연결 시작 — 500, 세션 id 를 주지 않는다
      Conn fresh = new Conn(k.token());
      MvcResult init = fresh.send(Map.of("jsonrpc", "2.0", "id", 1, "method", "initialize", "params", Map.of(
          "protocolVersion", "2025-06-18", "capabilities", Map.of(), "clientInfo", Map.of("name", "x", "version", "1"))));
      assertThat(init.getResponse().getStatus()).isEqualTo(500);
      assertThat(init.getResponse().getHeader("Mcp-Session-Id")).isNull();
      // 알림(응답을 아직 보내지 않은 요청) — 500
      MvcResult note = c.send(Map.of("jsonrpc", "2.0", "method", "notifications/initialized"));
      assertThat(note.getResponse().getStatus()).isEqualTo(500);
      assertThat(note.getResponse().getContentAsString()).contains(McpAccessRecorder.FAILED_CODE);
    } finally {
      jdbc.execute("DROP TRIGGER trg_test_refuse_access_log ON mcp_access_log");
      jdbc.execute("DROP FUNCTION test_refuse_access_log()");
    }
    assertThat(rows(k.id())).hasSize(before);
    // 다시 저장할 수 있으면 그대로 돈다
    assertThat((Integer) read(c.call("create_issue", args("project", P, "type", "bug", "title", "이제 남음")), "$.number")).isPositive();
    assertThat(rows(k.id())).hasSize(before + 1);
  }

  @Test
  void 키의_호출_수는_접근_기록의_줄_수와_같다() throws Exception {
    Space s = space("호출 수");
    Key k = key(s.member());
    api.get(s.member(), "/me/mcp-keys").andExpect(jsonPath("$[0].callCount").value(0));
    Conn c = new Conn(k.token()).connect();
    c.toolsList();
    c.call("list_workspaces", args());
    c.call("search_issues", args("project", P));
    // 틀린 키의 시도는 이 키의 수에 들어가지 않는다
    assertThat(new Conn("mai-wks-" + "B".repeat(43)).initialize().getResponse().getStatus()).isEqualTo(401);
    int count = jdbc.queryForObject("SELECT count(*) FROM mcp_access_log WHERE key_id = ?", Integer.class, k.id());
    assertThat(count).isEqualTo(5);
    api.get(s.member(), "/me/mcp-keys").andExpect(jsonPath("$[0].callCount").value(count));
    // 워크스페이스 관리 화면에는 호출 수를 주지 않는다(다른 워크스페이스의 일까지 세므로)
    api.get(s.owner(), "/workspaces/{ws}/mcp-keys?owner={o}", s.ws(), s.member().id())
        .andExpect(jsonPath("$[0].callCount").doesNotExist());
  }

  // ── 보기 · 범위 ───────────────────────────────────────────────────

  @Test
  void audit_read_는_그_워크스페이스_줄을_모두_보고_없으면_403_비멤버는_404_다른_워크스페이스_줄은_없다() throws Exception {
    Space a = space("보기 A");
    TestUser bOwner = api.user("b-owner");
    String b = api.teamWorkspace(bOwner, "보기 B");
    api.join(b, bOwner, a.member(), "member");
    api.post(bOwner, "/workspaces/{ws}/projects", Map.of("key", P, "name", "B 프로젝트", "visibility", "WORKSPACE"), b)
        .andExpect(status().isCreated());
    Key k = key(a.member());
    Conn c = new Conn(k.token()).connect();
    c.call("search_issues", args("project", P, "workspace", a.ws()));
    c.call("list_issue_types", args("project", P, "workspace", a.ws()));
    c.call("search_issues", args("project", P, "workspace", b));
    c.call("list_projects", args());
    TestUser outsider = api.user("outsider");

    // 소유자(audit.read) — A 에서 쓴 줄 두 개만
    var seen = api.get(a.owner(), WS_LOG + "?key={k}", a.ws(), k.id()).andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2));
    List<String> slugs = TestApi.read(seen, "$[*].workspaceSlug");
    assertThat(slugs).containsOnly(a.ws());
    List<String> tools = TestApi.read(seen, "$[*].tool");
    assertThat(tools).containsExactlyInAnyOrder("search_issues", "list_issue_types");
    // B 의 소유자는 B 에서 쓴 줄 하나만
    api.get(bOwner, WS_LOG + "?key={k}", b, k.id()).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].workspaceSlug").value(b));
    // audit.read 가 없는 멤버는 403, 비멤버는 404
    api.get(a.member(), WS_LOG, a.ws()).andExpect(status().isForbidden());
    api.get(a.member(), WS_LOG + ".csv", a.ws()).andExpect(status().isForbidden());
    api.get(outsider, WS_LOG, a.ws()).andExpect(status().isNotFound());

    // 키 주인은 자기 키의 줄을 모두 — 워크스페이스가 없는 줄(연결 · 알림 · 여러 곳 목록)까지
    int all = rows(k.id()).size();
    assertThat(all).isEqualTo(6);
    var mine = api.get(a.member(), MY_LOG, k.id()).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(all));
    List<String> methods = TestApi.read(mine, "$[*].mcpMethod");
    assertThat(methods).contains("initialize", "notifications/initialized", "tools/call");
    // 남의 키는 없는 것과 같다
    api.get(a.owner(), MY_LOG, k.id()).andExpect(status().isNotFound());
    api.get(outsider, MY_LOG + ".csv", k.id()).andExpect(status().isNotFound());
  }

  @Test
  void 거르기_조건마다_맞는_줄만_나오고_함께_걸_수_있다() throws Exception {
    Space s = space("거르기");
    Key ck = key(s.curator());
    Key mk = key(s.member());
    Conn cc = new Conn(ck.token()).connect();
    Conn mc = new Conn(mk.token());
    mc.connect();
    // 클라이언트 이름을 다르게 — 회원 쪽은 새 세션
    Conn beta = new Conn(mk.token());
    MvcResult init = beta.send(Map.of("jsonrpc", "2.0", "id", 1, "method", "initialize", "params", Map.of(
        "protocolVersion", "2025-06-18", "capabilities", Map.of(), "clientInfo", Map.of("name", "Beta-Agent", "version", "0.9"))));
    beta.session = init.getResponse().getHeader("Mcp-Session-Id");

    int n = read(cc.call("create_issue", args("project", P, "type", "bug", "title", "거르기")), "$.number");
    cc.call("search_issues", args("project", P));
    mc.call("get_issue", args("project", P, "number", n));
    mc.call("delete_issue", args("project", P, "number", n, "reason", "x"));   // 거부
    beta.call("search_issues", args("project", P));

    String q = WS_LOG + "?limit=200";
    assertThat(ids(api.get(s.owner(), q, s.ws()))).hasSize(5);
    assertRows(s, "key=" + ck.id(), 2, r -> assertThat(r.get("keyId")).isEqualTo(ck.id().toString()));
    assertRows(s, "person=" + s.member().id(), 3, r -> assertThat(r.get("ownerId")).isEqualTo(s.member().id().toString()));
    assertRows(s, "tool=search_issues", 2, r -> assertThat(r.get("tool")).isEqualTo("search_issues"));
    assertRows(s, "operation=CREATE,DELETE", 2, r -> assertThat(r.get("operation")).isIn("CREATE", "DELETE"));
    assertRows(s, "operation=CREATE&operation=DELETE", 2, r -> assertThat(r.get("operation")).isIn("CREATE", "DELETE"));
    assertRows(s, "outcome=DENIED", 1, r -> assertThat(r.get("errorCode")).isEqualTo("PERMISSION_REQUIRED"));
    assertRows(s, "client=beta", 1, r -> assertThat(r.get("clientName")).isEqualTo("Beta-Agent"));
    assertRows(s, "project=" + P, 5, r -> assertThat(r.get("projectKey")).isEqualTo(P));
    assertRows(s, "session=" + beta.session, 1, r -> assertThat(r.get("sessionId")).isEqualTo(beta.session));
    assertRows(s, "from=" + Instant.now().plus(1, ChronoUnit.HOURS), 0, r -> { });
    assertRows(s, "to=" + Instant.now().minus(1, ChronoUnit.HOURS), 0, r -> { });
    assertRows(s, "from=" + Instant.now().minus(1, ChronoUnit.HOURS) + "&to=" + Instant.now().plus(1, ChronoUnit.HOURS), 5, r -> { });
    // 함께 — 회원 · 읽기 · 성공
    assertRows(s, "person=" + s.member().id() + "&operation=READ&outcome=OK", 2, r -> {
      assertThat(r.get("operation")).isEqualTo("READ");
      assertThat(r.get("outcome")).isEqualTo("OK");
    });
    assertRows(s, "person=" + s.member().id() + "&tool=search_issues&client=BETA&outcome=OK", 1, r -> { });
    // 모르는 값은 400
    api.get(s.owner(), WS_LOG + "?operation=DROP", s.ws()).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_FILTER"));
    api.get(s.owner(), WS_LOG + "?outcome=MAYBE", s.ws()).andExpect(status().isBadRequest());
    // 내 키 화면도 같은 거르기
    api.get(s.member(), MY_LOG + "?operation=CONNECT", mk.id()).andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(3));   // 연결 시작 둘 + 알림 하나
  }

  private interface RowCheck {
    void check(Map<String, Object> row);
  }

  private void assertRows(Space s, String query, int expected, RowCheck check) throws Exception {
    var result = api.get(s.owner(), WS_LOG + "?limit=200&" + query, s.ws()).andExpect(status().isOk());
    List<Map<String, Object>> list = TestApi.read(result, "$");
    assertThat(list).as(query).hasSize(expected);
    list.forEach(check::check);
  }

  private static List<String> ids(org.springframework.test.web.servlet.ResultActions r) throws Exception {
    return TestApi.read(r.andExpect(status().isOk()), "$[*].id");
  }

  @Test
  void 커서로_이어_받으면_겹치거나_빠지는_줄이_없다() throws Exception {
    Space s = space("커서");
    Key k = key(s.member());
    Conn c = new Conn(k.token()).connect();
    for (int i = 0; i < 7; i++) {
      c.call("search_issues", args("project", P));
    }
    List<String> all = ids(api.get(s.owner(), WS_LOG + "?limit=200", s.ws()));
    assertThat(all).hasSize(7);
    List<String> paged = new ArrayList<>();
    String before = null;
    String beforeId = null;
    for (int guard = 0; guard < 10; guard++) {
      String url = WS_LOG + "?limit=3" + (before == null ? "" : "&before=" + before + "&beforeId=" + beforeId);
      var page = api.get(s.owner(), url, s.ws()).andExpect(status().isOk());
      List<Map<String, Object>> list = TestApi.read(page, "$");
      list.forEach(r -> paged.add((String) r.get("id")));
      if (list.size() < 3) {
        break;
      }
      before = (String) list.get(list.size() - 1).get("occurredAt");
      beforeId = (String) list.get(list.size() - 1).get("id");
    }
    assertThat(paged).containsExactlyElementsOf(all);
  }

  // ── 내려받기 ──────────────────────────────────────────────────────

  @Test
  void CSV_는_거른_결과와_같고_내려받기가_감사에_남는다() throws Exception {
    Space s = space("내려받기");
    Key k = key(s.curator());
    Conn c = new Conn(k.token());
    c.userAgent = "=HYPERLINK(1)";   // 바깥 클라이언트가 정하는 값 — 수식으로 읽히면 안 된다
    c.connect();
    c.call("search_issues", args("project", P));
    c.call("search_issues", args("project", P, "q", "찾기"));
    c.call("create_issue", args("project", P, "type", "bug", "title", "csv"));

    List<String> listed = ids(api.get(s.owner(), WS_LOG + "?tool=search_issues&limit=200", s.ws()));
    MvcResult csv = api.get(s.owner(), WS_LOG + ".csv?tool=search_issues", s.ws()).andExpect(status().isOk()).andReturn();
    assertThat(csv.getResponse().getContentType()).startsWith("text/csv");
    assertThat(csv.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION)).startsWith("attachment;").contains(".csv");
    String body = csv.getResponse().getContentAsString(StandardCharsets.UTF_8);
    List<String> lines = body.lines().toList();
    assertThat(lines.get(0)).startsWith("﻿occurredAt,durationMs,outcome");
    assertThat(lines).hasSize(listed.size() + 1);
    assertThat(lines.subList(1, lines.size())).allSatisfy(l -> assertThat(l).contains("search_issues"));
    // 수식처럼 보이는 셀은 글자로
    assertThat(body).doesNotContain(",=HYPERLINK").contains(",'=HYPERLINK(1),");

    List<Map<String, Object>> audits = jdbc.queryForList(
        "SELECT details::text AS d, workspace_id FROM audit_log WHERE action = 'mcp.access_log.export' AND actor_id = ?", s.owner().id());
    assertThat(audits).hasSize(1);
    assertThat(audits.get(0).get("workspace_id")).isEqualTo(s.wsId());
    assertThat((String) audits.get(0).get("d")).contains("\"rows\": " + listed.size()).contains("search_issues");
    // 워크스페이스 감사 화면에서도 보인다
    api.get(s.owner(), "/workspaces/{ws}/audit?action=mcp.access_log", s.ws()).andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1));

    // 내 키의 CSV — 사용자 단위 감사(워크스페이스 없음)
    MvcResult mine = api.get(s.curator(), MY_LOG + ".csv", k.id()).andExpect(status().isOk()).andReturn();
    assertThat(mine.getResponse().getContentAsString(StandardCharsets.UTF_8).lines().count()).isEqualTo(rows(k.id()).size() + 1);
    List<Map<String, Object>> mineAudit = jdbc.queryForList(
        "SELECT workspace_id, target_id FROM audit_log WHERE action = 'mcp.access_log.export' AND actor_id = ?", s.curator().id());
    assertThat(mineAudit).hasSize(1);
    assertThat(mineAudit.get(0).get("workspace_id")).isNull();
    assertThat(mineAudit.get(0).get("target_id")).isEqualTo(k.id().toString());
  }

  // ── 순수 규칙 ─────────────────────────────────────────────────────

  @Test
  void 작업_종류는_도구_이름의_앞말로_정한다() {
    Map<String, McpOperation> expected = new HashMap<>();
    expected.put("list_workspaces", McpOperation.READ);
    expected.put("list_projects", McpOperation.READ);
    expected.put("search_issues", McpOperation.READ);
    expected.put("get_issue", McpOperation.READ);
    expected.put("get_issue_template", McpOperation.READ);
    expected.put("list_attachments", McpOperation.READ);
    expected.put("request_attachment_download", McpOperation.READ);
    expected.put("create_issue", McpOperation.CREATE);
    expected.put("add_comment", McpOperation.CREATE);
    expected.put("log_work", McpOperation.CREATE);
    expected.put("request_attachment_upload", McpOperation.CREATE);
    expected.put("update_issue", McpOperation.UPDATE);
    expected.put("update_comment", McpOperation.UPDATE);
    expected.put("set_issue_template", McpOperation.UPDATE);
    expected.put("delete_issue", McpOperation.DELETE);
    expected.put("delete_comment", McpOperation.DELETE);
    expected.put("delete_attachment", McpOperation.DELETE);
    expected.put("reset_issue_template", McpOperation.DELETE);
    expected.put("link_issues", McpOperation.LINK);
    expected.put("unlink_issues", McpOperation.LINK);
    expected.forEach((tool, op) -> assertThat(McpOperation.ofTool(tool)).as(tool).isEqualTo(op));
  }

  @Test
  void 인자_요약은_본문과_긴_글을_길이와_해시로만_남기고_키_모양을_가린다() {
    Map<String, Object> summary = McpSecrets.summarizeArguments(args("project", P, "number", 3, "description", "짧은 설명",
        "title", "x".repeat(201), "q", "찾기 mai-wks-" + "C".repeat(43), "flag", true));
    assertThat(summary.get("project")).isEqualTo(P);
    assertThat(summary.get("number")).isEqualTo(3);
    assertThat(summary.get("flag")).isEqualTo(true);
    assertThat(summary.get("description")).isEqualTo(McpSecrets.digest("짧은 설명"));
    assertThat(summary.get("title")).isEqualTo(McpSecrets.digest("x".repeat(201)));
    assertThat((String) summary.get("q")).doesNotContain("CCCCCCCC").contains("mai-wks-…(가림)");
    assertThat(McpAccessLogService.csvCell("=1+1")).isEqualTo("'=1+1");
    assertThat(McpAccessLogService.csvCell("a,\"b\"")).isEqualTo("\"a,\"\"b\"\"\"");
  }
}
