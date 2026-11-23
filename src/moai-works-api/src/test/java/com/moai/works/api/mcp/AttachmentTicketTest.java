package com.moai.works.api.mcp;

import static com.jayway.jsonpath.JsonPath.read;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.moai.works.api.support.ApiTest;
import com.moai.works.api.support.McpTestClient;
import com.moai.works.api.support.TestApi;
import com.moai.works.api.support.TestUser;

/** MCP 첨부 — 일회용 티켓으로 REST 올리기 · 받기 (2026-11-22 사용자: "임시 티켓을 받아 쓰고 쓰면 바로 익스파이어 되는 티켓 시스템") */
@ApiTest
class AttachmentTicketTest {

  private static final String P = "tickets";

  @Autowired
  TestApi api;
  @Autowired
  MockMvc mvc;

  private record Space(String ws, TestUser owner, TestUser curator, int issue, String key, McpTestClient mcp) {
  }

  private Space space() throws Exception {
    TestUser owner = api.user("owner");
    String ws = api.teamWorkspace(owner, "티켓 시험");
    TestUser curator = api.user("curator");
    api.join(ws, owner, curator, "curator");
    api.post(owner, "/workspaces/{ws}/projects", Map.of("key", P, "name", "티켓", "visibility", "WORKSPACE"), ws)
        .andExpect(status().isCreated());
    int n = TestApi.read(api.post(curator, "/workspaces/{ws}/projects/{p}/issues", Map.of("type", "feature", "title", "첨부"),
        ws, P).andExpect(status().isCreated()), "$.number");
    String key = TestApi.read(api.post(curator, "/me/mcp-keys", Map.of("name", "에이전트"))
        .andExpect(status().isCreated()), "$.key");
    return new Space(ws, owner, curator, n, key, new McpTestClient(mvc, key));
  }

  private static Map<String, Object> args(Object... kv) {
    Map<String, Object> m = new HashMap<>();
    for (int i = 0; i < kv.length; i += 2) {
      m.put((String) kv[i], kv[i + 1]);
    }
    return m;
  }

  private ResultActions putFile(String ticket, byte[] body) throws Exception {
    return mvc.perform(put("/mcp/files/upload").header(HttpHeaders.AUTHORIZATION, "Bearer " + ticket)
        .contentType(MediaType.APPLICATION_OCTET_STREAM).content(body));
  }

  @Test
  void 티켓으로_한_번_올리고_같은_티켓은_다시_못_쓴다() throws Exception {
    Space s = space();
    byte[] body = "보고서 내용".getBytes(StandardCharsets.UTF_8);
    String issued = s.mcp().call("request_attachment_upload", args("project", P, "number", s.issue(), "fileName", "report.txt",
        "size", body.length, "contentType", "text/plain"));
    assertThat((String) read(issued, "$.uploadUrl")).endsWith("/mcp/files/upload");
    String ticket = read(issued, "$.ticket");
    assertThat(ticket).startsWith("mwt_");

    putFile(ticket, body).andExpect(status().isCreated())
        .andExpect(jsonPath("$.fileName").value("report.txt"))
        .andExpect(jsonPath("$.via").value("MCP"))
        .andExpect(jsonPath("$.uploadedBy.id").value(s.curator().id().toString()));
    // 한 번 쓰면 끝
    putFile(ticket, body).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("TICKET_INVALID"));
    // 웹 목록 · MCP 목록에 같이 보인다
    String listed = s.mcp().call("list_attachments", args("project", P, "number", s.issue()));
    assertThat((List<String>) read(listed, "$.attachments[*].fileName")).containsExactly("report.txt");
    // 미리보기 그림은 화면 몫 — MCP 결과에는 없다
    assertThat((List<Object>) read(listed, "$.attachments[*].preview")).isEmpty();
  }

  @Test
  void Content_Type_이_양식이거나_없어도_본문을_그대로_받는다() throws Exception {
    // curl --data-binary 는 Content-Type: application/x-www-form-urlencoded 를 붙인다 — 이진 데이터의 % 를 양식으로 풀면 안 된다
    Space s = space();
    byte[] body = new byte[] { (byte) 0x89, 'P', 'N', 'G', '%', 'D', '@', '%', 'z', '=', '&', 0, 1, 2 };
    for (String type : new String[] { MediaType.APPLICATION_FORM_URLENCODED_VALUE, null }) {
      String ticket = read(s.mcp().call("request_attachment_upload", args("project", P, "number", s.issue(), "fileName", "shot.bin",
          "size", body.length)), "$.ticket");
      var request = put("/mcp/files/upload").header(HttpHeaders.AUTHORIZATION, "Bearer " + ticket).content(body);
      if (type != null) {
        request = request.contentType(type);
      }
      String id = TestApi.read(mvc.perform(request).andExpect(status().isCreated()), "$.id");
      // 받은 내용이 올린 바이트 그대로다
      String down = read(s.mcp().call("request_attachment_download", args("project", P, "number", s.issue(), "attachmentId", id)),
          "$.ticket");
      mvc.perform(get("/mcp/files/download").header(HttpHeaders.AUTHORIZATION, "Bearer " + down)).andExpect(status().isOk())
          .andExpect(content().bytes(body));
    }
  }

  @Test
  void 알린_크기를_넘거나_틀린_티켓이면_거절() throws Exception {
    Space s = space();
    String ticket = read(s.mcp().call("request_attachment_upload", args("project", P, "number", s.issue(), "fileName", "a.bin",
        "size", 4)), "$.ticket");
    putFile(ticket, new byte[10]).andExpect(status().isPayloadTooLarge()).andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"));
    putFile("mwt_" + "A".repeat(43), new byte[1]).andExpect(status().isUnauthorized());
    putFile(s.key(), new byte[1]).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("TICKET_INVALID"));
    mvc.perform(put("/mcp/files/upload").content(new byte[1])).andExpect(status().isUnauthorized());
    // 상한을 넘는 크기는 티켓부터 안 준다(시험 설정 1MB)
    String big = s.mcp().call("request_attachment_upload", args("project", P, "number", s.issue(), "fileName", "big.bin",
        "size", 2 * 1024 * 1024));
    assertThat((String) read(big, "$.error.code")).isEqualTo("FILE_TOO_LARGE");
  }

  @Test
  void 받기_티켓과_키를_만료시키면_티켓도_못_쓴다() throws Exception {
    Space s = space();
    byte[] body = "PNG 아님".getBytes(StandardCharsets.UTF_8);
    String up = read(s.mcp().call("request_attachment_upload", args("project", P, "number", s.issue(), "fileName", "n.txt",
        "size", body.length)), "$.ticket");
    String id = TestApi.read(putFile(up, body).andExpect(status().isCreated()), "$.id");

    String down = read(s.mcp().call("request_attachment_download", args("project", P, "number", s.issue(), "attachmentId", id)),
        "$.ticket");
    mvc.perform(get("/mcp/files/download").header(HttpHeaders.AUTHORIZATION, "Bearer " + down)).andExpect(status().isOk())
        .andExpect(content().bytes(body))
        .andExpect(header().string("Content-Disposition", Matchers.startsWith("attachment")))
        .andExpect(header().string("X-Content-Type-Options", "nosniff"))
        .andExpect(header().string("Content-Security-Policy", "default-src 'none'; sandbox"));
    mvc.perform(get("/mcp/files/download").header(HttpHeaders.AUTHORIZATION, "Bearer " + down))
        .andExpect(status().isUnauthorized());
    // 올리기 티켓으로 받을 수 없다(종류가 다르다)
    String other = read(s.mcp().call("request_attachment_upload", args("project", P, "number", s.issue(), "fileName", "x",
        "size", 1)), "$.ticket");
    mvc.perform(get("/mcp/files/download").header(HttpHeaders.AUTHORIZATION, "Bearer " + other)).andExpect(status().isUnauthorized());

    // 티켓을 받은 뒤 키를 만료시키면 티켓도 못 쓴다
    String late = read(s.mcp().call("request_attachment_upload", args("project", P, "number", s.issue(), "fileName", "late.txt",
        "size", 1)), "$.ticket");
    String keyId = TestApi.read(api.get(s.curator(), "/me/mcp-keys"), "$[0].id");
    api.post(s.curator(), "/me/mcp-keys/{id}/expire", Map.of(), keyId).andExpect(status().isOk());
    putFile(late, new byte[1]).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("MCP_KEY_NOT_USABLE"));
  }

  /**
   * 키는 사용자의 것이고(ADR-029) 티켓은 도구가 정한 프로젝트의 워크스페이스에 묶인다 — 같은 key 의 프로젝트가 다른 워크스페이스에도 있으면
   * workspace 를 넣어 받고, 그 사이 그 워크스페이스의 MCP 권한을 잃으면 티켓도 못 쓴다(키에게 보이지 않는 워크스페이스).
   */
  @Test
  void 티켓은_고른_워크스페이스의_일감에_묶이고_MCP_권한을_잃으면_못_쓴다() throws Exception {
    Space s = space();
    TestUser other = api.user("other-owner");
    String otherWs = api.teamWorkspace(other, "다른 팀");
    api.join(otherWs, other, s.curator(), "curator");
    api.post(other, "/workspaces/{ws}/projects", Map.of("key", P, "name", "같은 key", "visibility", "WORKSPACE"), otherWs)
        .andExpect(status().isCreated());
    int n = TestApi.read(api.post(s.curator(), "/workspaces/{ws}/projects/{p}/issues", Map.of("type", "bug", "title", "다른 팀 일감"),
        otherWs, P).andExpect(status().isCreated()), "$.number");

    // 워크스페이스를 비우면 고르라고 한다
    assertThat((String) read(s.mcp().call("request_attachment_upload", args("project", P, "number", n, "fileName", "x.txt",
        "size", 1)), "$.error.code")).isEqualTo("PROJECT_AMBIGUOUS");
    String ticket = read(s.mcp().call("request_attachment_upload", args("project", P, "workspace", otherWs, "number", n,
        "fileName", "other.txt", "size", 2)), "$.ticket");
    putFile(ticket, new byte[] {'o', 'k'}).andExpect(status().isCreated());
    api.get(other, "/workspaces/{ws}/projects/{p}/issues/{n}/attachments", otherWs, P, n)
        .andExpect(jsonPath("$[0].fileName").value("other.txt"));
    api.get(s.owner(), "/workspaces/{ws}/projects/{p}/issues/{n}/attachments", s.ws(), P, s.issue())
        .andExpect(jsonPath("$.length()").value(0));

    // 티켓을 받은 뒤 그 워크스페이스에서 MCP 권한을 빼면(역할 바꾸기) 티켓도 막힌다
    String late = read(s.mcp().call("request_attachment_upload", args("project", P, "workspace", otherWs, "number", n,
        "fileName", "late.txt", "size", 1)), "$.ticket");
    String noMcp = api.customRole(otherWs, other, "MCP 없음", java.util.Set.of("workspace.read", "member.read", "issue.read",
        "issue.comment"));
    api.setRoles(otherWs, other, s.curator(), noMcp);
    putFile(late, new byte[1]).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("WORKSPACE_NOT_FOUND"));
  }

  @Test
  void MCP_로_첨부를_지운다() throws Exception {
    Space s = space();
    String up = read(s.mcp().call("request_attachment_upload", args("project", P, "number", s.issue(), "fileName", "d.txt",
        "size", 1)), "$.ticket");
    String id = TestApi.read(putFile(up, new byte[] {'d'}).andExpect(status().isCreated()), "$.id");
    assertThat((Boolean) read(s.mcp().call("delete_attachment", args("project", P, "number", s.issue(), "attachmentId", id)),
        "$.deleted")).isTrue();
    api.get(s.owner(), "/workspaces/{ws}/projects/{p}/issues/{n}/attachments", s.ws(), P, s.issue())
        .andExpect(jsonPath("$.length()").value(0));
  }
}
