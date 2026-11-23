package com.moai.works.api.issue;

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
 * 일감의 에이전트 세션 — 이 일을 맡은 AI Agent 세션 이름(없어도 된다) · 이력 · 판 · 목록 거르기 · 세션 이름 목록 · MCP
 * (2026-11-23 사용자, 일감 #26: "이슈 아이템에 필수는 등록 내용은 아니야 agent session name 항목 추가해 줘" · "필터 조건에도 넣고" ·
 * "mcp 기능도 당연히 추가하고").
 */
@ApiTest
class AgentSessionTest {

  private static final String P = "agents";
  private static final String BASE = "/workspaces/{ws}/projects/{p}";

  @Autowired
  TestApi api;
  @Autowired
  MockMvc mvc;

  private record Space(String ws, TestUser owner, TestUser curator, TestUser member) {
  }

  private Space space() throws Exception {
    TestUser owner = api.user("owner");
    String ws = api.teamWorkspace(owner, "세션");
    TestUser curator = api.user("curator");
    TestUser member = api.user("member");
    api.join(ws, owner, curator, "curator");
    api.join(ws, owner, member, "member");
    api.post(owner, "/workspaces/{ws}/projects", Map.of("key", P, "name", "세션 시험", "visibility", "WORKSPACE"), ws)
        .andExpect(status().isCreated());
    return new Space(ws, owner, curator, member);
  }

  private int create(Space s, TestUser by, Map<String, Object> extra) throws Exception {
    Map<String, Object> body = new HashMap<>(Map.of("type", "feature", "title", "세션 일감"));
    body.putAll(extra);
    return TestApi.read(api.post(by, BASE + "/issues", body, s.ws(), P).andExpect(status().isCreated()), "$.number");
  }

  private int rowVersion(Space s, int n) throws Exception {
    return TestApi.read(api.get(s.owner(), BASE + "/issues/{n}", s.ws(), P, n), "$.rowVersion");
  }

  private McpTestClient client(TestUser who) throws Exception {
    String key = TestApi.read(api.post(who, "/me/mcp-keys", Map.of("name", "세션 키")).andExpect(status().isCreated()), "$.key");
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
  void 없어도_되고_앞뒤_공백을_빼고_보기_목록_이력_판에_남는다() throws Exception {
    Space s = space();
    int none = create(s, s.curator(), Map.of());
    api.get(s.member(), BASE + "/issues/{n}", s.ws(), P, none).andExpect(jsonPath("$.agentSession").doesNotExist());
    int blank = create(s, s.curator(), Map.of("agentSession", "   "));
    api.get(s.member(), BASE + "/issues/{n}", s.ws(), P, blank).andExpect(jsonPath("$.agentSession").doesNotExist());

    int n = create(s, s.curator(), Map.of("agentSession", "  moai-works  "));
    api.get(s.member(), BASE + "/issues/{n}", s.ws(), P, n).andExpect(status().isOk())
        .andExpect(jsonPath("$.agentSession").value("moai-works"))
        .andExpect(jsonPath("$.journal[0].changes[?(@.field == 'agentSession')].after").value("moai-works"));
    api.get(s.member(), BASE + "/issues?q={q}", s.ws(), P, "#" + n)
        .andExpect(jsonPath("$.items[0].agentSession").value("moai-works"));

    // 바꾸면 이력 한 줄 · 새 판, 비우면(null · 빈 글) 없음
    api.patch(s.curator(), BASE + "/issues/{n}", Map.of("rowVersion", rowVersion(s, n), "agentSession", "moai-wiki"), s.ws(), P, n)
        .andExpect(status().isOk()).andExpect(jsonPath("$.agentSession").value("moai-wiki"))
        .andExpect(jsonPath("$.revisionNo").value(2))
        .andExpect(jsonPath("$.journal[1].changes[0].field").value("agentSession"))
        .andExpect(jsonPath("$.journal[1].changes[0].before").value("moai-works"))
        .andExpect(jsonPath("$.journal[1].changes[0].after").value("moai-wiki"));
    Map<String, Object> clear = new HashMap<>();
    clear.put("rowVersion", rowVersion(s, n));
    clear.put("agentSession", null);
    api.patch(s.curator(), BASE + "/issues/{n}", clear, s.ws(), P, n).andExpect(status().isOk())
        .andExpect(jsonPath("$.agentSession").doesNotExist())
        .andExpect(jsonPath("$.journal[2].changes[0].after").doesNotExist());

    api.get(s.member(), BASE + "/issues/{n}/revisions/2", s.ws(), P, n).andExpect(jsonPath("$.agentSession").value("moai-wiki"));
    api.get(s.member(), BASE + "/issues/{n}/revisions/3/diff", s.ws(), P, n)
        .andExpect(jsonPath("$.fields[?(@.field == 'agentSession')].before").value("moai-wiki"));
    List<String> fields = TestApi.read(api.get(s.member(), BASE + "/issues/{n}/revisions", s.ws(), P, n), "$.items[0].changedFields");
    assertThat(fields).containsExactly("agentSession");
    // 감사에는 바뀐 필드 이름
    api.get(s.owner(), "/workspaces/{ws}/audit?action=issue.update", s.ws())
        .andExpect(jsonPath("$[0].details").value(org.hamcrest.Matchers.containsString("agentSession")));
  }

  @Test
  void 너무_길거나_제어_문자가_있으면_거절한다() throws Exception {
    Space s = space();
    api.post(s.curator(), BASE + "/issues", Map.of("type", "bug", "title", "긴 세션", "agentSession", "a".repeat(101)), s.ws(), P)
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_AGENT_SESSION"));
    api.post(s.curator(), BASE + "/issues", Map.of("type", "bug", "title", "줄바꿈", "agentSession", "moai\nworks"), s.ws(), P)
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_AGENT_SESSION"));
    int n = create(s, s.curator(), Map.of("agentSession", "a".repeat(100)));
    api.patch(s.curator(), BASE + "/issues/{n}", Map.of("rowVersion", rowVersion(s, n), "agentSession", 3), s.ws(), P, n)
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_FIELD"));
    // 고치기 권한이 없으면 403 (멤버)
    api.patch(s.member(), BASE + "/issues/{n}", Map.of("rowVersion", rowVersion(s, n), "agentSession", "x"), s.ws(), P, n)
        .andExpect(status().isForbidden());
  }

  @Test
  void 목록은_세션으로_대소문자_없이_거르고_없음도_거르며_쓰인_세션_이름을_준다() throws Exception {
    Space s = space();
    int works = create(s, s.curator(), Map.of("agentSession", "moai-works"));
    int works2 = create(s, s.curator(), Map.of("agentSession", "Moai-Works"));
    int wiki = create(s, s.curator(), Map.of("agentSession", "moai-wiki"));
    int nobody = create(s, s.curator(), Map.of());
    int gone = create(s, s.owner(), Map.of("agentSession", "old-session"));
    api.delete(s.owner(), BASE + "/issues/{n}", s.ws(), P, gone).andExpect(status().isNoContent());

    List<Integer> found = TestApi.read(api.get(s.member(), BASE + "/issues?agentSession=MOAI-works&sort=number", s.ws(), P),
        "$.items[*].number");
    assertThat(found).containsExactly(works, works2);
    found = TestApi.read(api.get(s.member(), BASE + "/issues?agentSession=none", s.ws(), P), "$.items[*].number");
    assertThat(found).containsExactly(nobody);
    found = TestApi.read(api.get(s.member(), BASE + "/issues?agentSession=moai-wiki&status=open", s.ws(), P), "$.items[*].number");
    assertThat(found).containsExactly(wiki);

    // 지운 일감의 세션은 빠진다 — 이름순, 일감 수
    api.get(s.member(), BASE + "/agent-sessions", s.ws(), P).andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(3))
        .andExpect(jsonPath("$[0].name").value("moai-wiki"))
        .andExpect(jsonPath("$[0].issueCount").value(1))
        .andExpect(jsonPath("$[*].name").value(org.hamcrest.Matchers.containsInAnyOrder("moai-wiki", "moai-works", "Moai-Works")));
    // 독자가 아니면 404
    TestUser outsider = api.user("outsider");
    api.get(outsider, BASE + "/agent-sessions", s.ws(), P).andExpect(status().isNotFound());
  }

  @Test
  void 세션만_바꾸면_관리자에게_내용_바뀜_알림을_보내지_않는다() throws Exception {
    Space s = space();
    // 관리자 = 소유자, 큐레이터가 세션만 바꾼다
    int n = create(s, s.curator(), Map.of("managerId", s.owner().id().toString()));
    api.post(s.owner(), "/me/notifications/read-all", null).andExpect(status().isOk());
    api.patch(s.curator(), BASE + "/issues/{n}", Map.of("rowVersion", rowVersion(s, n), "agentSession", "moai-works"), s.ws(), P, n)
        .andExpect(status().isOk());
    List<String> kinds = TestApi.read(api.get(s.owner(), "/me/notifications?unread=true"), "$.items[*].kind");
    assertThat(kinds).doesNotContain("issue.updated");
  }

  @Test
  void MCP_로_만들고_고치고_비우고_세션으로_찾는다() throws Exception {
    Space s = space();
    McpTestClient mcp = client(s.curator());
    String created = mcp.call("create_issue", args("project", P, "type", "feature", "title", "세션 일감", "agentSession", "moai-works"));
    int n = read(created, "$.number");
    assertThat((String) read(created, "$.agentSession")).isEqualTo("moai-works");
    int other = read(mcp.call("create_issue", args("project", P, "type", "bug", "title", "세션 없음")), "$.number");

    String found = mcp.call("search_issues", args("project", P, "agentSession", "moai-works"));
    assertThat((List<Integer>) read(found, "$.items[*].number")).containsExactly(n);
    assertThat((List<String>) read(found, "$.items[*].agentSession")).containsExactly("moai-works");
    assertThat((List<Integer>) read(mcp.call("search_issues", args("project", P, "agentSession", "none")), "$.items[*].number"))
        .containsExactly(other);

    int rv = read(mcp.call("get_issue", args("project", P, "number", n)), "$.rowVersion");
    String updated = mcp.call("update_issue", args("project", P, "number", n, "rowVersion", rv, "status", "IN_PROGRESS",
        "agentSession", "moai-wiki"));
    assertThat((String) read(updated, "$.agentSession")).isEqualTo("moai-wiki");
    rv = read(updated, "$.rowVersion");
    String cleared = mcp.call("update_issue", args("project", P, "number", n, "rowVersion", rv, "clear", "agentSession"));
    assertThat((Object) read(cleared, "$.agentSession")).isNull();

    String bad = mcp.call("create_issue", args("project", P, "type", "bug", "title", "긴 세션", "agentSession", "x".repeat(101)));
    assertThat((String) read(bad, "$.error.code")).isEqualTo("INVALID_AGENT_SESSION");
    // 웹에서 보면 MCP 로 바꾼 줄
    api.get(s.owner(), BASE + "/issues/{n}", s.ws(), P, n)
        .andExpect(jsonPath("$.journal[1].via").value("MCP"))
        .andExpect(jsonPath("$.journal[1].changes[?(@.field == 'agentSession')].after").value("moai-wiki"));
  }
}
