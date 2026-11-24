package com.moai.works.api.mcp;

import static com.jayway.jsonpath.JsonPath.read;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
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

import com.moai.works.api.config.AppVersion;
import com.moai.works.api.support.ApiTest;
import com.moai.works.api.support.McpTestClient;
import com.moai.works.api.support.TestApi;
import com.moai.works.api.support.TestUser;

import tools.jackson.databind.json.JsonMapper;

/**
 * MCP 서버는 무상태다(일감 #240) — api 를 다시 띄워도 열려 있던 클라이언트가 그대로 일한다.
 *
 * <ul>
 *   <li>세션 id 를 주지도 요구하지도 않는다 — 낡은(서버가 준 적 없는) {@code Mcp-Session-Id} 와 initialize 없이도 도구 목록 · 호출 · 알림이 된다</li>
 *   <li>initialize 는 JSON 으로 바로 답하고 안내는 연결마다 지금 DB 로 만든다. serverInfo 는 입구 이름 · 빌드 판</li>
 *   <li>접근 기록: 연결 시작 줄은 clientInfo 를 모두, 다른 줄은 같은 키 · 같은 User-Agent 로 이어 붙인 clientInfo 와 User-Agent ·
 *       MCP-Protocol-Version 머리글</li>
 *   <li>GET 405(Allow: POST, DELETE) · DELETE 204 · 그 밖 405. 전송 단계 오류는 {@code {"code","message"}} — Java 예외 모양이 없다</li>
 * </ul>
 */
@ApiTest
class McpStatelessTest {

  private static final String P = "nostate";
  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final String INITIALIZE = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":"
      + "\"2025-06-18\",\"capabilities\":{\"roots\":{}},\"clientInfo\":{\"name\":\"stateless-client\",\"version\":\"3.1\","
      + "\"title\":\"무상태 시험\"}}}";
  /** Java 예외를 그대로 본문에 넣으면 나오는 이름들 */
  private static final List<String> EXCEPTION_FIELDS = List.of("localizedMessage", "jsonRpcError", "stackTrace", "\"cause\"",
      "suppressed", "io.modelcontextprotocol", "java.lang");

  @Autowired
  TestApi api;
  @Autowired
  MockMvc mvc;
  @Autowired
  JdbcTemplate jdbc;
  @Autowired
  AppVersion appVersion;
  @Autowired
  McpInstructions instructions;

  private record Key(UUID id, String token) {
  }

  private record Space(String ws, TestUser owner, TestUser member) {
  }

  private Space space() throws Exception {
    TestUser owner = api.user("owner");
    String ws = api.teamWorkspace(owner, "무상태");
    TestUser member = api.user("member");
    api.join(ws, owner, member, "member");
    api.post(owner, "/workspaces/{ws}/projects", Map.of("key", P, "name", "무상태", "visibility", "WORKSPACE"), ws)
        .andExpect(status().isCreated());
    return new Space(ws, owner, member);
  }

  private Key key(TestUser who) throws Exception {
    var issued = api.post(who, "/me/mcp-keys", Map.of("name", "무상태 시험 키")).andExpect(status().isCreated());
    return new Key(UUID.fromString(TestApi.read(issued, "$.view.id")), TestApi.read(issued, "$.key"));
  }

  /** MCP POST — 실제 클라이언트처럼 Accept 둘 · JSON 본문. 세션 id · User-Agent · 프로토콜 판은 주면 싣는다 */
  private MvcResult send(String path, String token, String body, String userAgent, String session, String protocol)
      throws Exception {
    MockHttpServletRequestBuilder req = post(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
        .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM).contentType(MediaType.APPLICATION_JSON).content(body);
    if (userAgent != null) {
      req.header(HttpHeaders.USER_AGENT, userAgent);
    }
    if (session != null) {
      req.header("Mcp-Session-Id", session);
    }
    if (protocol != null) {
      req.header("MCP-Protocol-Version", protocol);
    }
    return perform(req);
  }

  private MvcResult perform(MockHttpServletRequestBuilder req) throws Exception {
    MvcResult r = mvc.perform(req).andReturn();
    assertThat(r.getRequest().isAsyncStarted()).as("붙잡아 두지 않고 바로 답한다").isFalse();
    return r;
  }

  private static String rpc(int id, String method, Map<String, Object> params) throws Exception {
    return JSON.writeValueAsString(params == null ? Map.of("jsonrpc", "2.0", "id", id, "method", method)
        : Map.of("jsonrpc", "2.0", "id", id, "method", method, "params", params));
  }

  private static String body(MvcResult r) throws Exception {
    return r.getResponse().getContentAsString(StandardCharsets.UTF_8);
  }

  private List<Map<String, Object>> rows(UUID keyId) {
    return jdbc.queryForList("SELECT * FROM mcp_access_log WHERE key_id = ? ORDER BY occurred_at, id", keyId);
  }

  // ── 다시 띄운 뒤 ──────────────────────────────────────────────────

  @Test
  void 낡은_세션_id_를_들고_initialize_없이_와도_도구_목록과_호출이_된다() throws Exception {
    Space s = space();
    Key k = key(s.owner());
    // api 를 다시 띄우기 전에 받은 세션 id 를 그대로 들고 오는 클라이언트 — 서버는 이 값을 준 적도, 기억한 적도 없다
    String stale = UUID.randomUUID().toString();
    String agent = "RestartAgent/1.0 (시험)";

    MvcResult list = send(McpTestClient.ISSUE, k.token(), rpc(7, "tools/list", null), agent, stale, "2025-06-18");
    assertThat(list.getResponse().getStatus()).isEqualTo(200);
    assertThat(list.getResponse().getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
    assertThat(list.getResponse().getHeader("Mcp-Session-Id")).isNull();
    List<String> tools = read(body(list), "$.result.tools[*].name");
    assertThat(tools).hasSize(24).contains("search_issues", "create_issue", "get_markdown_guide");

    MvcResult call = send(McpTestClient.ISSUE, k.token(), rpc(8, "tools/call", Map.of("name", "list_projects",
        "arguments", Map.of())), agent, stale, "2025-06-18");
    assertThat(call.getResponse().getStatus()).isEqualTo(200);
    List<String> projects = read((String) read(body(call), "$.result.content[0].text"), "$.projects[*].key");
    assertThat(projects).contains(P);

    // 쓰기도 — 판정은 요청마다 키로
    MvcResult created = send(McpTestClient.ISSUE, k.token(), rpc(9, "tools/call", Map.of("name", "create_issue",
        "arguments", Map.of("project", P, "type", "bug", "title", "다시 띄운 뒤"))), agent, stale, "2025-06-18");
    assertThat((Integer) read((String) read(body(created), "$.result.content[0].text"), "$.summary.number")).isPositive();

    // 알림 · 다른 입구도
    MvcResult note = send(McpTestClient.ISSUE, k.token(), "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}", agent,
        stale, "2025-06-18");
    assertThat(note.getResponse().getStatus()).isEqualTo(202);
    MvcResult project = send(McpTestClient.PROJECT, k.token(), rpc(10, "tools/list", null), agent, stale, "2025-06-18");
    assertThat((List<String>) read(body(project), "$.result.tools[*].name")).hasSize(7);
    // 끝내기(DELETE)도 오류가 아니다 — 끝낼 세션이 없다
    MvcResult end = perform(delete(McpTestClient.ISSUE).header(HttpHeaders.AUTHORIZATION, "Bearer " + k.token())
        .header("Mcp-Session-Id", stale).header(HttpHeaders.USER_AGENT, agent));
    assertThat(end.getResponse().getStatus()).isEqualTo(204);

    // 접근 기록 — 모두 남고, 보낸 세션 id · User-Agent · 프로토콜 판은 그대로. 이 서버가 본 연결 시작이 없어 클라이언트 이름은 비어 있다
    List<Map<String, Object>> rows = rows(k.id());
    assertThat(rows).extracting(r -> r.get("mcp_method")).containsExactlyInAnyOrder("tools/list", "tools/call", "tools/call",
        "notifications/initialized", "tools/list", "session/end");
    assertThat(rows).allSatisfy(r -> {
      assertThat(r.get("mcp_session_id")).isEqualTo(stale);
      assertThat(r.get("user_agent")).isEqualTo(agent);
      assertThat(r.get("client_name")).isNull();
      if (!"session/end".equals(r.get("mcp_method"))) {
        assertThat(r.get("protocol_version")).isEqualTo("2025-06-18");
      }
    });
    assertThat(rows).allSatisfy(r -> assertThat(r.get("outcome")).isEqualTo("OK"));
    assertThat(rows.stream().filter(r -> "create_issue".equals(r.get("tool")))).singleElement()
        .satisfies(r -> assertThat(r.get("operation")).isEqualTo("CREATE"));
  }

  // ── 연결 시작 ─────────────────────────────────────────────────────

  @Test
  void initialize_는_세션_id_없이_JSON_으로_답하고_안내는_연결마다_지금_DB_로_만든다() throws Exception {
    Space s = space();
    Key k = key(s.member());
    MvcResult issue = send(McpTestClient.ISSUE, k.token(), INITIALIZE, "InitAgent/1", null, null);
    assertThat(issue.getResponse().getStatus()).isEqualTo(200);
    assertThat(issue.getResponse().getHeader("Mcp-Session-Id")).as("세션 id 를 주지 않는다").isNull();
    assertThat(issue.getResponse().getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
    String init = body(issue);
    assertThat((String) read(init, "$.result.protocolVersion")).isEqualTo("2025-06-18");
    assertThat((String) read(init, "$.result.serverInfo.name")).isEqualTo("moai-works-issue");
    assertThat((String) read(init, "$.result.serverInfo.version")).isEqualTo(appVersion.value());
    assertThat((Boolean) read(init, "$.result.capabilities.tools.listChanged")).isFalse();
    // 서버를 만들 때의 고정 글이 아니라 이 연결의 안내 — 지금 DB 로 만든 것과 같다
    assertThat((String) read(init, "$.result.instructions")).isEqualTo(instructions.issue())
        .startsWith(McpInstructions.ISSUE_HEAD);

    String project = body(send(McpTestClient.PROJECT, k.token(), INITIALIZE, "InitAgent/1", null, null));
    assertThat((String) read(project, "$.result.serverInfo.name")).isEqualTo("moai-works-project");
    assertThat((String) read(project, "$.result.instructions")).isEqualTo(instructions.project())
        .startsWith(McpInstructions.PROJECT_HEAD);

    // 같은 클라이언트가 다시 연결을 시작해도 된다(서버에 남는 것이 없다)
    assertThat(send(McpTestClient.ISSUE, k.token(), INITIALIZE, "InitAgent/1", null, null).getResponse().getStatus()).isEqualTo(200);
    assertThat(rows(k.id())).extracting(r -> r.get("mcp_method")).containsOnly("initialize").hasSize(3);
  }

  // ── 접근 기록의 클라이언트 정보 ────────────────────────────────────

  @Test
  void 클라이언트_정보는_같은_키_같은_User_Agent_의_줄에_이어_붙고_User_Agent_와_프로토콜_판은_늘_남는다() throws Exception {
    Space s = space();
    Key k = key(s.member());
    Key other = key(s.owner());
    String agent = "ClientInfoAgent/" + UUID.randomUUID();
    send(McpTestClient.ISSUE, k.token(), INITIALIZE, agent, null, null);
    // 연결 시작과 다른 입구 · 세션 id 없이 — 같은 키 · 같은 User-Agent 면 붙는다
    send(McpTestClient.PROJECT, k.token(), rpc(2, "tools/list", null), agent, null, "2025-06-18");
    send(McpTestClient.ISSUE, k.token(), rpc(3, "tools/call", Map.of("name", "list_workspaces", "arguments", Map.of())), agent,
        null, "2025-06-18");
    // 같은 키의 다른 프로그램 — 이름은 붙지 않고 User-Agent · 판은 남는다
    send(McpTestClient.ISSUE, k.token(), rpc(4, "tools/list", null), "OtherAgent/2", null, "2025-03-26");
    // 다른 키는 같은 User-Agent 여도 빌리지 못한다
    send(McpTestClient.ISSUE, other.token(), rpc(5, "tools/list", null), agent, null, null);

    List<Map<String, Object>> rows = rows(k.id());
    assertThat(rows).hasSize(4);
    Map<String, Object> init = rows.stream().filter(r -> "initialize".equals(r.get("mcp_method"))).findFirst().orElseThrow();
    assertThat(init.get("client_name")).isEqualTo("stateless-client");
    assertThat(init.get("client_version")).isEqualTo("3.1");
    assertThat(init.get("client_title")).isEqualTo("무상태 시험");
    assertThat(init.get("client_capabilities")).isEqualTo("roots");
    assertThat(init.get("protocol_version")).isEqualTo("2025-06-18");
    assertThat(init.get("mcp_session_id")).isNull();
    List<Map<String, Object>> later = rows.stream()
        .filter(r -> !"initialize".equals(r.get("mcp_method")) && agent.equals(r.get("user_agent"))).toList();
    assertThat(later).hasSize(2).allSatisfy(r -> {
      assertThat(r.get("client_name")).isEqualTo("stateless-client");
      assertThat(r.get("client_version")).isEqualTo("3.1");
      assertThat(r.get("protocol_version")).isEqualTo("2025-06-18");
      assertThat(r.get("mcp_session_id")).isNull();
    });
    assertThat(later).extracting(r -> r.get("entry") + " " + r.get("mcp_method") + " " + r.get("tool"))
        .containsExactlyInAnyOrder("PROJECT tools/list null", "ISSUE tools/call list_workspaces");
    Map<String, Object> stranger = rows.stream().filter(r -> "OtherAgent/2".equals(r.get("user_agent"))).findFirst().orElseThrow();
    assertThat(stranger.get("user_agent")).isEqualTo("OtherAgent/2");
    assertThat(stranger.get("client_name")).isNull();
    assertThat(stranger.get("protocol_version")).isEqualTo("2025-03-26");
    Map<String, Object> borrowed = rows(other.id()).get(0);
    assertThat(borrowed.get("user_agent")).isEqualTo(agent);
    assertThat(borrowed.get("client_name")).as("다른 키의 클라이언트 정보는 빌리지 못한다").isNull();
  }

  // ── HTTP 메서드 ───────────────────────────────────────────────────

  @Test
  void GET_은_405_와_Allow_DELETE_는_204_그_밖의_메서드는_405() throws Exception {
    Space s = space();
    Key k = key(s.member());
    MvcResult stream = perform(get(McpTestClient.ISSUE).header(HttpHeaders.AUTHORIZATION, "Bearer " + k.token())
        .accept(MediaType.TEXT_EVENT_STREAM));
    assertThat(stream.getResponse().getStatus()).isEqualTo(405);
    assertThat(stream.getResponse().getHeader(HttpHeaders.ALLOW)).isEqualTo("POST, DELETE");
    assertThat((String) read(body(stream), "$.code")).isEqualTo("MCP_STREAM_NOT_SUPPORTED");

    MvcResult end = perform(delete(McpTestClient.PROJECT).header(HttpHeaders.AUTHORIZATION, "Bearer " + k.token()));
    assertThat(end.getResponse().getStatus()).isEqualTo(204);

    MvcResult other = perform(put(McpTestClient.ISSUE).header(HttpHeaders.AUTHORIZATION, "Bearer " + k.token())
        .contentType(MediaType.APPLICATION_JSON).content("{}"));
    assertThat(other.getResponse().getStatus()).isEqualTo(405);
    assertThat(other.getResponse().getHeader(HttpHeaders.ALLOW)).isEqualTo("POST, DELETE");
    assertThat((String) read(body(other), "$.code")).isEqualTo("MCP_METHOD_NOT_ALLOWED");

    assertThat(rows(k.id())).extracting(r -> r.get("mcp_method") + " " + r.get("http_status") + " " + r.get("error_code"))
        .containsExactlyInAnyOrder("session/stream 405 MCP_STREAM_NOT_SUPPORTED", "session/end 204 null", "unknown 405 MCP_METHOD_NOT_ALLOWED");
  }

  // ── 전송 단계 오류 ────────────────────────────────────────────────

  @Test
  void 전송_단계_오류는_우리_꼴로_답하고_Java_예외_모양이_없다() throws Exception {
    Space s = space();
    Key k = key(s.member());
    String auth = "Bearer " + k.token();

    // 깨진 JSON · 묶음(배열) · 응답 · method 없는 객체 — 400 MCP_INVALID_MESSAGE
    for (String broken : List.of("{not json", "[" + rpc(1, "ping", null) + "]", "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{}}",
        "{\"jsonrpc\":\"2.0\",\"id\":1}", "")) {
      MvcResult r = send(McpTestClient.ISSUE, k.token(), broken, null, null, null);
      assertThat(r.getResponse().getStatus()).as(broken).isEqualTo(400);
      assertOurs(r, "MCP_INVALID_MESSAGE");
    }
    // Content-Type 이 JSON 이 아니다 — 415
    MvcResult text = perform(post(McpTestClient.ISSUE).header(HttpHeaders.AUTHORIZATION, auth)
        .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM).contentType(MediaType.TEXT_PLAIN).content(rpc(1, "ping", null)));
    assertThat(text.getResponse().getStatus()).isEqualTo(415);
    assertOurs(text, "MCP_UNSUPPORTED_MEDIA_TYPE");
    // JSON 을 받지 않는 Accept(SSE 만) — 406. Accept 가 없으면 JSON 으로 답한다
    MvcResult sseOnly = perform(post(McpTestClient.ISSUE).header(HttpHeaders.AUTHORIZATION, auth)
        .accept(MediaType.TEXT_EVENT_STREAM).contentType(MediaType.APPLICATION_JSON).content(rpc(1, "ping", null)));
    assertThat(sseOnly.getResponse().getStatus()).isEqualTo(406);
    assertOurs(sseOnly, "MCP_NOT_ACCEPTABLE");
    MvcResult noAccept = perform(post(McpTestClient.ISSUE).header(HttpHeaders.AUTHORIZATION, auth)
        .contentType(MediaType.APPLICATION_JSON).content(rpc(1, "ping", null)));
    assertThat(noAccept.getResponse().getStatus()).isEqualTo(200);
    assertThat((Map<String, Object>) read(body(noAccept), "$.result")).isEmpty();

    // JSON-RPC 안의 오류 — JSON-RPC 오류로, data.code 와 한국어 문구. 예외 문구 · 클래스 이름은 없다
    String unknownMethod = body(send(McpTestClient.ISSUE, k.token(), rpc(11, "resources/list", null), null, null, null));
    assertThat((Integer) read(unknownMethod, "$.id")).isEqualTo(11);
    assertThat((Integer) read(unknownMethod, "$.error.code")).isEqualTo(-32601);
    assertThat((String) read(unknownMethod, "$.error.data.code")).isEqualTo("METHOD_NOT_FOUND");
    assertThat((String) read(unknownMethod, "$.error.message")).contains("resources/list");
    String unknownTool = body(send(McpTestClient.ISSUE, k.token(), rpc(12, "tools/call", Map.of("name", "drop_everything",
        "arguments", Map.of())), null, null, null));
    assertThat((String) read(unknownTool, "$.error.data.code")).isEqualTo("TOOL_NOT_FOUND");
    assertThat((String) read(unknownTool, "$.error.message")).contains("drop_everything");
    String badParams = body(send(McpTestClient.ISSUE, k.token(), "{\"jsonrpc\":\"2.0\",\"id\":13,\"method\":\"tools/call\",\"params\":\"x\"}",
        null, null, null));
    assertThat((Integer) read(badParams, "$.id")).isEqualTo(13);
    assertThat((String) read(badParams, "$.error.data.code")).isEqualTo("INVALID_PARAMS");
    for (String rpcError : List.of(unknownMethod, unknownTool, badParams)) {
      EXCEPTION_FIELDS.forEach(f -> assertThat(rpcError).doesNotContain(f));
      assertThat(rpcError).doesNotContain("Cannot").doesNotContain("Exception");
    }

    // 접근 기록 — 응답과 같은 code
    List<Map<String, Object>> rows = rows(k.id());
    assertThat(rows.stream().filter(r -> "MCP_INVALID_MESSAGE".equals(r.get("error_code")))).hasSize(5)
        .allSatisfy(r -> assertThat(r.get("http_status")).isEqualTo(400));
    assertThat(rows.stream().filter(r -> "MCP_UNSUPPORTED_MEDIA_TYPE".equals(r.get("error_code")))).hasSize(1);
    assertThat(rows.stream().filter(r -> "MCP_NOT_ACCEPTABLE".equals(r.get("error_code")))).hasSize(1);
    assertThat(rows.stream().filter(r -> "METHOD_NOT_FOUND".equals(r.get("error_code")))).hasSize(1);
    assertThat(rows.stream().filter(r -> "TOOL_NOT_FOUND".equals(r.get("error_code")))).hasSize(1);
    assertThat(rows.stream().filter(r -> "INVALID_PARAMS".equals(r.get("error_code")))).hasSize(1);
  }

  private static void assertOurs(MvcResult r, String code) throws Exception {
    String body = body(r);
    assertThat(r.getResponse().getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
    assertThat((Map<String, Object>) read(body, "$")).containsOnlyKeys("code", "message");
    assertThat((String) read(body, "$.code")).isEqualTo(code);
    EXCEPTION_FIELDS.forEach(f -> assertThat(body).doesNotContain(f));
  }
}
