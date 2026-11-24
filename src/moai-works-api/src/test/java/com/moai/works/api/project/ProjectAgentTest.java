package com.moai.works.api.project;

import static com.jayway.jsonpath.JsonPath.read;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.moai.works.api.support.ApiTest;
import com.moai.works.api.support.McpTestClient;
import com.moai.works.api.support.TestApi;
import com.moai.works.api.support.TestUser;

/**
 * 프로젝트의 에이전트 멤버 — 일감에서 자동 등록(만들기 · 고치기 · 옮기기, 웹 · MCP) · 대소문자 없이 하나 · 손으로 더하기 · 고치기 · 쓰지 않음(지우지
 * 않음) · 일감 수 · 권한 · 감사 · MCP checks · 세션 이름 고르기 (2026-11-25 사용자, 일감 #285: "프로젝트 멤버에 에이전트 세션네임도 추가해줘" ·
 * "멤버는 사람, 에이전트 2가지로 나눠 지는거지").
 */
@ApiTest
class ProjectAgentTest {

  private static final String P = "agentm";
  private static final String BASE = "/workspaces/{ws}/projects/{p}";
  private static final String AGENTS = BASE + "/agents";
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
    String ws = api.teamWorkspace(owner, "에이전트");
    TestUser curator = api.user("curator");
    TestUser member = api.user("member");
    api.join(ws, owner, curator, "curator");
    api.join(ws, owner, member, "member");
    api.post(owner, "/workspaces/{ws}/projects", Map.of("key", P, "name", "에이전트 멤버", "visibility", "WORKSPACE"), ws)
        .andExpect(status().isCreated());
    return new Space(ws, owner, curator, member);
  }

  private int issue(Space s, TestUser by, Map<String, Object> extra) throws Exception {
    return issue(s, by, P, extra);
  }

  private int issue(Space s, TestUser by, String project, Map<String, Object> extra) throws Exception {
    Map<String, Object> body = new HashMap<>(Map.of("type", "feature", "title", "에이전트 일감"));
    body.putAll(extra);
    return TestApi.read(api.post(by, BASE + "/issues", body, s.ws(), project).andExpect(status().isCreated()), "$.number");
  }

  private int rowVersion(Space s, int n) throws Exception {
    return TestApi.read(api.get(s.owner(), ISSUE, s.ws(), n), "$.rowVersion");
  }

  private String agentId(Space s, String project, String name) throws Exception {
    List<String> ids = TestApi.read(api.get(s.owner(), AGENTS, s.ws(), project), "$[?(@.name == '" + name + "')].id");
    assertThat(ids).as("에이전트 %s", name).hasSize(1);
    return ids.get(0);
  }

  private McpTestClient client(TestUser who) throws Exception {
    String key = TestApi.read(api.post(who, "/me/mcp-keys", Map.of("name", "에이전트 키")).andExpect(status().isCreated()), "$.key");
    return new McpTestClient(mvc, key);
  }

  private static Map<String, Object> args(Object... kv) {
    Map<String, Object> m = new HashMap<>();
    for (int i = 0; i < kv.length; i += 2) {
      m.put((String) kv[i], kv[i + 1]);
    }
    return m;
  }

  // ── 자동 등록 ──────────────────────────────────────────────────────

  @Test
  void 일감을_만들고_고치고_옮길_때_새_이름은_자동으로_등록되고_대소문자_없이_하나다() throws Exception {
    Space s = space();
    api.get(s.member(), AGENTS, s.ws(), P).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));

    // 만들기 — 주인 = 만든 사람(웹)
    issue(s, s.curator(), Map.of("agentSession", "moai-works"));
    api.get(s.member(), AGENTS, s.ws(), P).andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].name").value("moai-works")).andExpect(jsonPath("$[0].source").value("AUTO"))
        .andExpect(jsonPath("$[0].active").value(true)).andExpect(jsonPath("$[0].owner.username").value(s.curator().username()))
        .andExpect(jsonPath("$[0].description").doesNotExist());
    // 대소문자만 다른 이름은 같은 에이전트다 — 더 생기지 않는다
    issue(s, s.owner(), Map.of("agentSession", "MOAI-Works"));
    api.get(s.member(), AGENTS, s.ws(), P).andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].name").value("moai-works")).andExpect(jsonPath("$[0].totalIssues").value(2))
        .andExpect(jsonPath("$[0].owner.username").value(s.curator().username()));

    // 고치기 — 세션을 넣으면(주인 = 고친 사람)
    int plain = issue(s, s.curator(), Map.of());
    api.patch(s.owner(), ISSUE, Map.of("rowVersion", rowVersion(s, plain), "agentSession", "moai-wiki"), s.ws(), plain)
        .andExpect(status().isOk());
    api.get(s.member(), AGENTS, s.ws(), P).andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[?(@.name == 'moai-wiki')].owner.username").value(s.owner().username()));

    // 옮기기 — 옮긴 일감과 하위 일감의 세션이 옮길 프로젝트의 에이전트가 된다(주인 = 옮긴 사람)
    api.post(s.owner(), "/workspaces/{ws}/projects", Map.of("key", "other", "name", "다른", "visibility", "WORKSPACE"), s.ws())
        .andExpect(status().isCreated());
    int root = issue(s, s.curator(), Map.of("agentSession", "mover"));
    issue(s, s.curator(), Map.of("parent", root, "agentSession", "child-agent"));
    api.patch(s.owner(), ISSUE, Map.of("rowVersion", rowVersion(s, root), "project", "other"), s.ws(), root)
        .andExpect(status().isOk()).andExpect(jsonPath("$.project.key").value("other"));
    List<String> names = TestApi.read(api.get(s.member(), AGENTS, s.ws(), "other"), "$[*].name");
    assertThat(names).containsExactly("child-agent", "mover");
    api.get(s.member(), AGENTS, s.ws(), "other")
        .andExpect(jsonPath("$[?(@.name == 'mover')].owner.username").value(s.owner().username()))
        .andExpect(jsonPath("$[?(@.name == 'mover')].totalIssues").value(1));
    // 옛 프로젝트의 에이전트는 남는다(지우지 않는다) — 일감 수만 0
    api.get(s.member(), AGENTS, s.ws(), P).andExpect(jsonPath("$[?(@.name == 'mover')].totalIssues").value(0));

    // 감사 — AUTO, 그 이름이 들어온 일감 번호와 함께. 이미 있는 이름은 더 남지 않는다
    List<String> details = TestApi.read(api.get(s.owner(), "/workspaces/{ws}/audit?action=project.agent.create&limit=50", s.ws()),
        "$[*].details");
    // P: moai-works · moai-wiki · mover · child-agent, other: mover · child-agent
    assertThat(details).hasSize(6).allMatch(d -> d.contains("AUTO") && d.contains("WEB"));
  }

  @Test
  void 같은_이름을_동시에_넣어도_하나만_생긴다() throws Exception {
    Space s = space();
    // 만들기는 워크스페이스 번호 순번에서 줄을 서므로 고치기로 겨룬다 — 일감 넷에 같은 새 이름(대소문자만 다르게)을 한꺼번에
    List<Integer> numbers = new java.util.ArrayList<>();
    for (int i = 0; i < 4; i++) {
      numbers.add(issue(s, s.curator(), Map.of()));
    }
    List<Integer> versions = new java.util.ArrayList<>();
    for (int n : numbers) {
      versions.add(rowVersion(s, n));
    }
    List<Thread> threads = new java.util.ArrayList<>();
    List<Throwable> errors = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
    java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
    for (int i = 0; i < 4; i++) {
      TestUser by = i % 2 == 0 ? s.curator() : s.owner();
      String name = i % 2 == 0 ? "race-agent" : "Race-Agent";
      int n = numbers.get(i);
      int rv = versions.get(i);
      Thread t = new Thread(() -> {
        try {
          start.await();
          api.patch(by, ISSUE, Map.of("rowVersion", rv, "agentSession", name), s.ws(), n).andExpect(status().isOk());
        } catch (Throwable e) {
          errors.add(e);
        }
      });
      threads.add(t);
      t.start();
    }
    start.countDown();
    for (Thread t : threads) {
      t.join();
    }
    assertThat(errors).isEmpty();
    api.get(s.member(), AGENTS, s.ws(), P).andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].totalIssues").value(4));
    assertThat(jdbc.queryForObject("SELECT count(*) FROM project_agent WHERE lower(name) = 'race-agent'", Integer.class)).isEqualTo(1);
  }

  // ── 손으로 더하기 · 고치기 ───────────────────────────────────────────

  @Test
  void 손으로_더하고_설명_주인_쓰는지를_고치며_지우지_않는다() throws Exception {
    Space s = space();
    // 주인을 비우면 부른 사람 — 일감이 없어도 둔다
    api.post(s.owner(), AGENTS, Map.of("name", "  claude-docs  ", "description", " 문서 세션 "), s.ws(), P)
        .andExpect(status().isCreated()).andExpect(jsonPath("$.name").value("claude-docs"))
        .andExpect(jsonPath("$.source").value("MANUAL")).andExpect(jsonPath("$.description").value("문서 세션"))
        .andExpect(jsonPath("$.owner.username").value(s.owner().username())).andExpect(jsonPath("$.openIssues").value(0))
        .andExpect(jsonPath("$.totalIssues").value(0)).andExpect(jsonPath("$.lastActivityAt").doesNotExist());
    api.post(s.owner(), AGENTS, Map.of("name", "wiki-bot", "owner", s.member().username()), s.ws(), P)
        .andExpect(status().isCreated()).andExpect(jsonPath("$.owner.username").value(s.member().username()));

    // 규칙 — 대소문자만 다른 같은 이름 409, 이름 · 설명 · 주인
    api.post(s.owner(), AGENTS, Map.of("name", "Claude-Docs"), s.ws(), P).andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("AGENT_NAME_TAKEN"));
    api.post(s.owner(), AGENTS, Map.of("name", "   "), s.ws(), P).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_AGENT_SESSION"));
    api.post(s.owner(), AGENTS, Map.of("name", "a".repeat(101)), s.ws(), P).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_AGENT_SESSION"));
    api.post(s.owner(), AGENTS, Map.of("name", "two\nlines"), s.ws(), P).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_AGENT_SESSION"));
    api.post(s.owner(), AGENTS, Map.of("name", "long-desc", "description", "x".repeat(201)), s.ws(), P)
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("AGENT_DESCRIPTION_TOO_LONG"));
    api.post(s.owner(), AGENTS, Map.of("name", "nobody", "owner", "no-such-user"), s.ws(), P).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("PERSON_NOT_FOUND"));

    // 고치기 — 설명 · 주인, 낡은 판은 409, 모르는 항목 · 이름 바꾸기는 400
    String id = agentId(s, P, "claude-docs");
    List<Integer> versions = TestApi.read(api.get(s.owner(), AGENTS, s.ws(), P), "$[?(@.name == 'claude-docs')].rowVersion");
    int v0 = versions.get(0);
    api.patch(s.owner(), AGENTS + "/{id}", Map.of("description", "문서 · 위키 세션", "owner", s.curator().username(), "rowVersion", v0),
        s.ws(), P, id).andExpect(status().isOk()).andExpect(jsonPath("$.description").value("문서 · 위키 세션"))
        .andExpect(jsonPath("$.owner.username").value(s.curator().username())).andExpect(jsonPath("$.rowVersion").value(1));
    api.patch(s.owner(), AGENTS + "/{id}", Map.of("description", "늦음", "rowVersion", v0), s.ws(), P, id)
        .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONCURRENT_UPDATE"));
    api.patch(s.owner(), AGENTS + "/{id}", Map.of("name", "renamed"), s.ws(), P, id).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"));
    Map<String, Object> noOwner = new HashMap<>();
    noOwner.put("owner", null);
    api.patch(s.owner(), AGENTS + "/{id}", noOwner, s.ws(), P, id).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("PERSON_NOT_FOUND"));
    api.patch(s.owner(), AGENTS + "/{id}", Map.of("active", false), s.ws(), P, java.util.UUID.randomUUID())
        .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("AGENT_NOT_FOUND"));
    Map<String, Object> clear = new HashMap<>();
    clear.put("description", null);
    api.patch(s.owner(), AGENTS + "/{id}", clear, s.ws(), P, id).andExpect(status().isOk())
        .andExpect(jsonPath("$.description").doesNotExist());

    // 쓰지 않음 — 목록에 남고(쓰는 것 뒤로) 다시 쓸 수 있다. 지우기는 api · DB 모두 없다
    api.patch(s.owner(), AGENTS + "/{id}", Map.of("active", false), s.ws(), P, id).andExpect(status().isOk())
        .andExpect(jsonPath("$.active").value(false));
    api.get(s.member(), AGENTS, s.ws(), P).andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].name").value("wiki-bot")).andExpect(jsonPath("$[1].name").value("claude-docs"))
        .andExpect(jsonPath("$[1].active").value(false));
    api.patch(s.owner(), AGENTS + "/{id}", Map.of("active", true), s.ws(), P, id).andExpect(jsonPath("$.active").value(true));
    api.call(s.owner(), org.springframework.http.HttpMethod.DELETE, AGENTS + "/{id}", null, s.ws(), P, id)
        .andExpect(status().is4xxClientError());
    assertThatThrownBy(() -> jdbc.update("DELETE FROM project_agent WHERE name = 'claude-docs'")).hasMessageContaining("지울 수 없습니다");
    assertThatThrownBy(() -> jdbc.update("UPDATE project_agent SET name = 'renamed' WHERE name = 'claude-docs'"))
        .hasMessageContaining("바꿀 수 없습니다");

    // 감사 — 더하기 둘(MANUAL) · 고치기 넷(설명 · 주인 / 설명 지움 / 쓰지 않음 / 다시 씀), 바꾼 것이 없으면 남지 않는다
    List<String> actions = TestApi.read(api.get(s.owner(), "/workspaces/{ws}/audit?action=project.agent&limit=50", s.ws()),
        "$[*].action");
    assertThat(actions.stream().filter("project.agent.create"::equals)).hasSize(2);
    assertThat(actions.stream().filter("project.agent.update"::equals)).hasSize(4);
    api.get(s.owner(), "/workspaces/{ws}/audit?action=project.agent.update&limit=50", s.ws())
        .andExpect(jsonPath("$[3].details").value(Matchers.allOf(Matchers.containsString("owner"),
            Matchers.containsString(s.curator().username()), Matchers.containsString("ownerBefore"), Matchers.containsString("WEB"))));
    api.get(s.owner(), "/workspaces/{ws}/audit?action=project.agent.create&limit=50", s.ws())
        .andExpect(jsonPath("$[0].details").value(Matchers.containsString("MANUAL")));
  }

  @Test
  void 일감_수는_대소문자_없이_세고_열린_일감은_확인_상태를_포함한다() throws Exception {
    Space s = space();
    issue(s, s.curator(), Map.of("agentSession", "stats"));
    issue(s, s.curator(), Map.of("agentSession", "STATS", "status", "DONE"));
    int review = issue(s, s.curator(), Map.of("agentSession", "Stats"));
    api.patch(s.curator(), ISSUE, Map.of("rowVersion", rowVersion(s, review), "status", "REVIEW", "notes", "결과를 확인해 주세요"),
        s.ws(), review).andExpect(status().isOk());
    int gone = issue(s, s.owner(), Map.of("agentSession", "stats"));
    api.delete(s.owner(), ISSUE, s.ws(), gone).andExpect(status().isNoContent());
    String last = TestApi.read(api.get(s.owner(), ISSUE, s.ws(), review), "$.updatedAt");

    api.get(s.member(), AGENTS, s.ws(), P).andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].name").value("stats")).andExpect(jsonPath("$[0].openIssues").value(2))
        .andExpect(jsonPath("$[0].totalIssues").value(3));
    String lastActivity = TestApi.read(api.get(s.member(), AGENTS, s.ws(), P), "$[0].lastActivityAt");
    assertThat(java.time.Instant.parse(lastActivity)).isEqualTo(java.time.Instant.parse(last));
  }

  @Test
  void 보기는_독자_누구나_관리는_프로젝트_관리_권한이_있어야_한다() throws Exception {
    Space s = space();
    issue(s, s.curator(), Map.of("agentSession", "perm-agent"));
    String id = agentId(s, P, "perm-agent");
    // 큐레이터(일감은 고치지만 project.manage 없음) · 멤버는 403, 독자가 아니면 404
    api.get(s.member(), AGENTS, s.ws(), P).andExpect(status().isOk());
    api.post(s.curator(), AGENTS, Map.of("name", "x"), s.ws(), P).andExpect(status().isForbidden());
    api.patch(s.curator(), AGENTS + "/{id}", Map.of("active", false), s.ws(), P, id).andExpect(status().isForbidden());
    api.patch(s.member(), AGENTS + "/{id}", Map.of("description", "내 것"), s.ws(), P, id).andExpect(status().isForbidden());
    TestUser outsider = api.user("outsider");
    api.get(outsider, AGENTS, s.ws(), P).andExpect(status().isNotFound());

    // 프로젝트 관리자 역할을 받으면 큐레이터도 관리한다
    api.put(s.owner(), BASE + "/roles/{user}", Map.of("roles", List.of("project-admin")), s.ws(), P, s.curator().id())
        .andExpect(status().isOk());
    api.patch(s.curator(), AGENTS + "/{id}", Map.of("description", "큐레이터가 고침"), s.ws(), P, id).andExpect(status().isOk());

    // 주인은 이 프로젝트를 읽을 수 있어야 한다 — 지정 멤버 프로젝트에 역할이 없는 멤버는 400
    api.post(s.owner(), "/workspaces/{ws}/projects", Map.of("key", "secret", "name", "비공개", "visibility", "RESTRICTED"), s.ws())
        .andExpect(status().isCreated());
    api.post(s.owner(), AGENTS, Map.of("name", "secret-agent", "owner", s.member().username()), s.ws(), "secret")
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("USER_NOT_PROJECT_READER"));
    api.get(s.member(), AGENTS, s.ws(), "secret").andExpect(status().isNotFound());

    // 보관한 프로젝트는 409
    api.post(s.owner(), BASE + "/archive", null, s.ws(), P).andExpect(status().isOk());
    api.post(s.owner(), AGENTS, Map.of("name", "late"), s.ws(), P).andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("PROJECT_NOT_WRITABLE"));
  }

  // ── MCP ───────────────────────────────────────────────────────────

  @Test
  void MCP_로_새_이름을_넣으면_등록을_알리고_쓰지_않는_에이전트면_막지_않고_경고한다() throws Exception {
    Space s = space();
    McpTestClient mcp = client(s.curator());
    String created = mcp.call("create_issue", args("project", P, "type", "feature", "title", "MCP 일감", "agentSession", "mcp-agent"));
    assertThat((List<String>) read(created, "$.checks[?(@.code == 'AGENT_REGISTERED')].level")).containsExactly("INFO");
    assertThat((List<List<String>>) read(created, "$.checks[?(@.code == 'AGENT_REGISTERED')].items")).containsExactly(List.of("mcp-agent"));
    int n = read(created, "$.summary.number");
    api.get(s.member(), AGENTS, s.ws(), P).andExpect(jsonPath("$[0].name").value("mcp-agent"))
        .andExpect(jsonPath("$[0].owner.username").value(s.curator().username()));
    api.get(s.owner(), "/workspaces/{ws}/audit?action=project.agent.create", s.ws())
        .andExpect(jsonPath("$[0].details").value(Matchers.allOf(Matchers.containsString("AUTO"), Matchers.containsString("MCP"))));

    // 이미 있는 이름이면 알리지 않는다
    String again = mcp.call("create_issue", args("project", P, "type", "bug", "title", "또", "agentSession", "MCP-Agent"));
    assertThat((List<?>) read(again, "$.checks[?(@.code == 'AGENT_REGISTERED')]")).isEmpty();

    // 쓰지 않음으로 둔 에이전트 — 이름을 넣어도 막지 않고 WARN AGENT_INACTIVE
    api.patch(s.owner(), AGENTS + "/{id}", Map.of("active", false), s.ws(), P, agentId(s, P, "mcp-agent")).andExpect(status().isOk());
    int rv = read(mcp.call("get_issue", args("number", n)), "$.summary.rowVersion");
    String updated = mcp.call("update_issue", args("number", n, "rowVersion", rv, "agentSession", "mcp-agent", "status", "IN_PROGRESS"));
    assertThat((List<String>) read(updated, "$.checks[?(@.code == 'AGENT_INACTIVE')].level")).containsExactly("WARN");
    assertThat((String) read(updated, "$.summary.agentSession")).isEqualTo("mcp-agent");
    // 세션을 주지 않은 고치기에는 알리지 않는다(이번에 들어온 이름이 아니다)
    rv = read(updated, "$.summary.rowVersion");
    String plain = mcp.call("update_issue", args("number", n, "rowVersion", rv, "estimatedHours", 2));
    assertThat((List<?>) read(plain, "$.checks[?(@.code == 'AGENT_INACTIVE')]")).isEmpty();
    // 웹도 막지 않는다
    issue(s, s.owner(), Map.of("agentSession", "mcp-agent"));

    // 고치기로 새 이름을 넣어도 등록을 알린다
    rv = read(plain, "$.summary.rowVersion");
    String renamed = mcp.call("update_issue", args("number", n, "rowVersion", rv, "agentSession", "second-agent"));
    assertThat((List<List<String>>) read(renamed, "$.checks[?(@.code == 'AGENT_REGISTERED')].items")).containsExactly(List.of("second-agent"));
  }

  @Test
  void 세션_이름_고르기는_쓰는_에이전트_먼저_그다음_쓰지_않는_에이전트_그다음_멤버_아닌_이름() throws Exception {
    Space s = space();
    issue(s, s.curator(), Map.of("agentSession", "zeta"));
    issue(s, s.curator(), Map.of("agentSession", "Zeta"));
    int old = issue(s, s.curator(), Map.of("agentSession", "alpha"));
    api.post(s.owner(), AGENTS, Map.of("name", "manual-only"), s.ws(), P).andExpect(status().isCreated());
    api.post(s.owner(), AGENTS, Map.of("name", "retired-empty"), s.ws(), P).andExpect(status().isCreated());
    api.patch(s.owner(), AGENTS + "/{id}", Map.of("active", false), s.ws(), P, agentId(s, P, "alpha")).andExpect(status().isOk());
    api.patch(s.owner(), AGENTS + "/{id}", Map.of("active", false), s.ws(), P, agentId(s, P, "retired-empty")).andExpect(status().isOk());
    // 멤버가 아닌 이름 — V29 전에 쓰인 이름처럼(이력 밖에서 바꾼다)
    jdbc.update("UPDATE issue SET agent_session = 'ghost' WHERE number = ? AND project_id = (SELECT id FROM project WHERE key = ?"
        + " AND workspace_id = (SELECT id FROM workspace WHERE slug = ?))", old, P, s.ws());
    issue(s, s.curator(), Map.of("agentSession", "alpha"));

    api.get(s.member(), BASE + "/agent-sessions", s.ws(), P).andExpect(status().isOk())
        .andExpect(jsonPath("$[*].name").value(Matchers.contains("manual-only", "zeta", "alpha", "ghost")))
        .andExpect(jsonPath("$[0].issueCount").value(0)).andExpect(jsonPath("$[0].registered").value(true))
        .andExpect(jsonPath("$[0].active").value(true))
        .andExpect(jsonPath("$[1].issueCount").value(2))
        .andExpect(jsonPath("$[2].registered").value(true)).andExpect(jsonPath("$[2].active").value(false))
        .andExpect(jsonPath("$[3].registered").value(false)).andExpect(jsonPath("$[3].issueCount").value(1));
    // 워크스페이스 목록은 그대로(쓰인 이름만, 표시 없음)
    api.get(s.member(), "/workspaces/{ws}/agent-sessions", s.ws()).andExpect(status().isOk())
        .andExpect(jsonPath("$[0].registered").doesNotExist());
  }
}
