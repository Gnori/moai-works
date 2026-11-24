package com.moai.works.api.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;

import tools.jackson.databind.json.JsonMapper;

/** 시험용 MCP 클라이언트 — 키 하나로 입구 하나(기본 일감 입구 /mcp/issue)에 세션을 열고 도구를 부른다. 결과는 도구가 돌려준 JSON 글자 */
public final class McpTestClient {

  private static final JsonMapper JSON = new JsonMapper();
  private final MockMvc mvc;
  public static final String ISSUE = "/mcp/issue";
  public static final String PROJECT = "/mcp/project";

  private final String token;
  private final String path;
  private final String session;
  private int id = 10;

  public McpTestClient(MockMvc mvc, String token) throws Exception {
    this(mvc, token, ISSUE);
  }

  public McpTestClient(MockMvc mvc, String token, String path) throws Exception {
    this.mvc = mvc;
    this.token = token;
    this.path = path;
    MvcResult init = send(null, Map.of("jsonrpc", "2.0", "id", 1, "method", "initialize", "params",
        Map.of("protocolVersion", "2025-06-18", "capabilities", Map.of(), "clientInfo", Map.of("name", "test", "version", "1"))));
    this.session = init.getResponse().getHeader("Mcp-Session-Id");
    send(session, Map.of("jsonrpc", "2.0", "method", "notifications/initialized"));
  }

  /** 도구를 불러 결과 JSON 글자를 돌려준다 */
  public String call(String tool, Map<String, Object> arguments) throws Exception {
    MvcResult r = send(session, Map.of("jsonrpc", "2.0", "id", ++id, "method", "tools/call", "params",
        Map.of("name", tool, "arguments", arguments)));
    String body = r.getResponse().getContentAsString(StandardCharsets.UTF_8);
    String json = body.lines().filter(l -> l.startsWith("data:")).map(l -> l.substring(5).trim()).findFirst().orElse(body);
    return JsonPath.read(json, "$.result.content[0].text");
  }

  /** 도구 목록의 이름들 */
  public java.util.List<String> tools() throws Exception {
    MvcResult r = send(session, Map.of("jsonrpc", "2.0", "id", ++id, "method", "tools/list"));
    String body = r.getResponse().getContentAsString(StandardCharsets.UTF_8);
    String json = body.lines().filter(l -> l.startsWith("data:")).map(l -> l.substring(5).trim()).findFirst().orElse(body);
    return JsonPath.read(json, "$.result.tools[*].name");
  }

  /** 도구 목록 원문(JSON-RPC 결과) — 설명 · 입력 틀까지 볼 때 */
  public String toolsJson() throws Exception {
    MvcResult r = send(session, Map.of("jsonrpc", "2.0", "id", ++id, "method", "tools/list"));
    String body = r.getResponse().getContentAsString(StandardCharsets.UTF_8);
    return body.lines().filter(l -> l.startsWith("data:")).map(l -> l.substring(5).trim()).findFirst().orElse(body);
  }

  /** 서버가 먼저 보내는 알림 스트림(GET)을 열어 본다 — 이 서버는 405 로 바로 거절한다 */
  public MvcResult openStream() throws Exception {
    return mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token).header("Mcp-Session-Id", session)
        .accept(MediaType.TEXT_EVENT_STREAM)).andReturn();
  }

  private MvcResult send(String sessionId, Map<String, Object> body) throws Exception {
    var req = post(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
        .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM).contentType(MediaType.APPLICATION_JSON)
        .content(JSON.writeValueAsString(body));
    if (sessionId != null) {
      req = req.header("Mcp-Session-Id", sessionId);
    }
    MvcResult r = mvc.perform(req).andReturn();
    if (r.getRequest().isAsyncStarted()) {
      r = mvc.perform(asyncDispatch(r)).andReturn();
    }
    return r;
  }
}
