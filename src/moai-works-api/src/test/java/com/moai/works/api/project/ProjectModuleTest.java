package com.moai.works.api.project;

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
 * 프로젝트 모듈과 일감의 관련 모듈 — 관리 권한(project.manage) · 쓰지 않음(지우지 않음) · 순서 · 감사 · 일감에 여럿 · 이력 · 판 · 거르기 · MCP
 * (2026-11-23 사용자, 일감 #27: "프로젝트에서 모듈을 등록해서 항목을 관리하도록 하자" · "이슈에 멀티로 관련 모듈을 등록할 수 있게 해 줘" ·
 * "등록 수정이겠지 당연히").
 */
@ApiTest
class ProjectModuleTest {

  private static final String P = "mods";
  private static final String BASE = "/workspaces/{ws}/projects/{p}";
  private static final String MODULES = BASE + "/modules";

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
    String ws = api.teamWorkspace(owner, "모듈");
    TestUser curator = api.user("curator");
    TestUser member = api.user("member");
    api.join(ws, owner, curator, "curator");
    api.join(ws, owner, member, "member");
    api.post(owner, "/workspaces/{ws}/projects", Map.of("key", P, "name", "모듈 시험", "visibility", "WORKSPACE"), ws)
        .andExpect(status().isCreated());
    return new Space(ws, owner, curator, member);
  }

  /** moai-works 의 모듈 다섯 */
  private void seed(Space s) throws Exception {
    for (String[] m : new String[][] { { "api", "API" }, { "web-backend", "웹 백엔드" }, { "web-frontend", "웹 프런트" },
        { "infra", "인프라" }, { "docs", "문서" } }) {
      api.post(s.owner(), MODULES, Map.of("key", m[0], "name", m[1]), s.ws(), P).andExpect(status().isCreated());
    }
  }

  private int issue(Space s, TestUser by, Map<String, Object> extra) throws Exception {
    Map<String, Object> body = new HashMap<>(Map.of("type", "feature", "title", "모듈 일감"));
    body.putAll(extra);
    return TestApi.read(api.post(by, BASE + "/issues", body, s.ws(), P).andExpect(status().isCreated()), "$.number");
  }

  private int rowVersion(Space s, int n) throws Exception {
    return TestApi.read(api.get(s.owner(), BASE + "/issues/{n}", s.ws(), P, n), "$.rowVersion");
  }

  private String key(TestUser who) throws Exception {
    return TestApi.read(api.post(who, "/me/mcp-keys", Map.of("name", "모듈 키")).andExpect(status().isCreated()), "$.key");
  }

  private static Map<String, Object> args(Object... kv) {
    Map<String, Object> m = new HashMap<>();
    for (int i = 0; i < kv.length; i += 2) {
      m.put((String) kv[i], kv[i + 1]);
    }
    return m;
  }

  // ── 모듈 관리 ──────────────────────────────────────────────────────

  @Test
  void 관리는_프로젝트_관리_권한이_있어야_하고_보기는_독자_누구나() throws Exception {
    Space s = space();
    api.post(s.owner(), MODULES, Map.of("key", "api", "name", " API ", "description", "REST · MCP"), s.ws(), P)
        .andExpect(status().isCreated()).andExpect(jsonPath("$.key").value("api")).andExpect(jsonPath("$.name").value("API"))
        .andExpect(jsonPath("$.position").value(0)).andExpect(jsonPath("$.active").value(true))
        .andExpect(jsonPath("$.issueCount").value(0));

    // 큐레이터(일감은 고치지만 project.manage 없음) · 멤버는 관리하지 못한다
    api.post(s.curator(), MODULES, Map.of("key", "docs", "name", "문서"), s.ws(), P).andExpect(status().isForbidden());
    api.patch(s.curator(), MODULES + "/api", Map.of("name", "에이피아이"), s.ws(), P).andExpect(status().isForbidden());
    api.put(s.member(), MODULES + "/order", Map.of("keys", List.of("api")), s.ws(), P).andExpect(status().isForbidden());
    api.get(s.member(), MODULES, s.ws(), P).andExpect(status().isOk()).andExpect(jsonPath("$[0].key").value("api"))
        .andExpect(jsonPath("$[0].description").value("REST · MCP"));
    TestUser outsider = api.user("outsider");
    api.get(outsider, MODULES, s.ws(), P).andExpect(status().isNotFound());

    // 프로젝트 관리자 역할을 받으면 큐레이터도 관리한다
    api.put(s.owner(), BASE + "/roles/{user}", Map.of("roles", List.of("project-admin")), s.ws(), P, s.curator().id())
        .andExpect(status().isOk());
    api.post(s.curator(), MODULES, Map.of("key", "docs", "name", "문서"), s.ws(), P).andExpect(status().isCreated())
        .andExpect(jsonPath("$.position").value(1));
  }

  @Test
  void key_이름_설명을_검사하고_같은_key_는_409() throws Exception {
    Space s = space();
    api.post(s.owner(), MODULES, Map.of("key", "Web", "name", "웹"), s.ws(), P).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_MODULE_KEY"));
    api.post(s.owner(), MODULES, Map.of("key", "web-", "name", "웹"), s.ws(), P).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_MODULE_KEY"));
    api.post(s.owner(), MODULES, Map.of("key", "a".repeat(33), "name", "웹"), s.ws(), P).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_MODULE_KEY"));
    api.post(s.owner(), MODULES, Map.of("key", "web", "name", "  "), s.ws(), P).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_MODULE_NAME"));
    api.post(s.owner(), MODULES, Map.of("key", "web", "name", "웹", "description", "x".repeat(501)), s.ws(), P)
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MODULE_DESCRIPTION_TOO_LONG"));
    api.post(s.owner(), MODULES, Map.of("key", "w", "name", "한 글자 key"), s.ws(), P).andExpect(status().isCreated());
    api.post(s.owner(), MODULES, Map.of("key", "w", "name", "또"), s.ws(), P).andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("MODULE_KEY_TAKEN"));
    // 다른 프로젝트에는 같은 key 를 둘 수 있다
    api.post(s.owner(), "/workspaces/{ws}/projects", Map.of("key", "other", "name", "다른", "visibility", "WORKSPACE"), s.ws())
        .andExpect(status().isCreated());
    api.post(s.owner(), MODULES, Map.of("key", "w", "name", "다른 프로젝트"), s.ws(), "other").andExpect(status().isCreated());
  }

  @Test
  void 이름_설명을_바꾸고_쓰지_않음으로_두고_다시_쓴다_지우기는_없다() throws Exception {
    Space s = space();
    seed(s);
    int v0 = TestApi.read(api.get(s.owner(), MODULES, s.ws(), P), "$[0].rowVersion");
    api.patch(s.owner(), MODULES + "/api", Map.of("name", "API 서버", "description", "Spring Boot", "rowVersion", v0), s.ws(), P)
        .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("API 서버"))
        .andExpect(jsonPath("$.description").value("Spring Boot"));
    // 낡은 판 · 모르는 항목 · 없는 모듈
    api.patch(s.owner(), MODULES + "/api", Map.of("name", "늦음", "rowVersion", v0), s.ws(), P).andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CONCURRENT_UPDATE"));
    api.patch(s.owner(), MODULES + "/api", Map.of("key", "server"), s.ws(), P).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("UNKNOWN_FIELD"));
    api.patch(s.owner(), MODULES + "/nope", Map.of("name", "없음"), s.ws(), P).andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("MODULE_NOT_FOUND"));

    api.patch(s.owner(), MODULES + "/infra", Map.of("active", false), s.ws(), P).andExpect(status().isOk())
        .andExpect(jsonPath("$.active").value(false));
    // 쓰지 않는 모듈도 목록에 남는다(지우지 않는다)
    api.get(s.member(), MODULES, s.ws(), P).andExpect(jsonPath("$.length()").value(5))
        .andExpect(jsonPath("$[?(@.key == 'infra')].active").value(false));
    api.patch(s.owner(), MODULES + "/infra", Map.of("active", true), s.ws(), P).andExpect(jsonPath("$.active").value(true));
    api.call(s.owner(), org.springframework.http.HttpMethod.DELETE, MODULES + "/infra", null, s.ws(), P)
        .andExpect(status().is4xxClientError());
    // DB 도 지우기 · key 바꾸기를 막는다
    assertThatThrownBy(() -> jdbc.update("DELETE FROM project_module WHERE module_key = 'infra'"))
        .hasMessageContaining("지울 수 없습니다");
    assertThatThrownBy(() -> jdbc.update("UPDATE project_module SET module_key = 'infra2' WHERE module_key = 'infra'"))
        .hasMessageContaining("바꿀 수 없습니다");

    List<String> actions = TestApi.read(api.get(s.owner(), "/workspaces/{ws}/audit?action=project.module&limit=50", s.ws()),
        "$[*].action");
    assertThat(actions).containsOnly("project.module.create", "project.module.update");
    assertThat(actions.stream().filter("project.module.update"::equals)).hasSize(3);
    api.get(s.owner(), "/workspaces/{ws}/audit?action=project.module.update", s.ws())
        .andExpect(jsonPath("$[0].details").value(org.hamcrest.Matchers.allOf(org.hamcrest.Matchers.containsString("infra"),
            org.hamcrest.Matchers.containsString("WEB"))));
  }

  @Test
  void 순서는_모듈_전부를_한_번씩_주어야_한다() throws Exception {
    Space s = space();
    seed(s);
    api.put(s.owner(), MODULES + "/order", Map.of("keys", List.of("docs", "api")), s.ws(), P).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_MODULE_ORDER"));
    api.put(s.owner(), MODULES + "/order", Map.of("keys", List.of("docs", "api", "api", "infra", "web-backend")), s.ws(), P)
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_MODULE_ORDER"));
    api.put(s.owner(), MODULES + "/order", Map.of("keys", List.of("docs", "web-frontend", "web-backend", "api", "infra")),
        s.ws(), P).andExpect(status().isOk()).andExpect(jsonPath("$[0].key").value("docs"))
        .andExpect(jsonPath("$[4].key").value("infra")).andExpect(jsonPath("$[3].position").value(3));
    api.get(s.owner(), "/workspaces/{ws}/audit?action=project.module.reorder", s.ws())
        .andExpect(jsonPath("$.length()").value(1));
  }

  @Test
  void 보관한_프로젝트의_모듈은_바꿀_수_없다() throws Exception {
    Space s = space();
    seed(s);
    api.post(s.owner(), BASE + "/archive", null, s.ws(), P).andExpect(status().isOk());
    api.post(s.owner(), MODULES, Map.of("key", "new", "name", "새"), s.ws(), P).andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("PROJECT_NOT_WRITABLE"));
    api.patch(s.owner(), MODULES + "/api", Map.of("active", false), s.ws(), P).andExpect(status().isConflict());
  }

  // ── 일감의 관련 모듈 ─────────────────────────────────────────────────

  @Test
  void 일감에_여럿을_붙이고_바꾸고_비우면_이력과_판에_남는다() throws Exception {
    Space s = space();
    seed(s);
    int n = issue(s, s.curator(), Map.of("modules", List.of("web-frontend", "API", "api")));
    // 모듈 순서대로 · 겹친 key 는 하나 · key 는 대소문자 무시
    api.get(s.member(), BASE + "/issues/{n}", s.ws(), P, n).andExpect(status().isOk())
        .andExpect(jsonPath("$.modules[*].key").value(org.hamcrest.Matchers.contains("api", "web-frontend")))
        .andExpect(jsonPath("$.modules[0].name").value("API"))
        .andExpect(jsonPath("$.journal[0].changes[?(@.field == 'modules')].after[*].key")
            .value(org.hamcrest.Matchers.contains("api", "web-frontend")));
    api.get(s.member(), BASE + "/issues?q={q}", s.ws(), P, "#" + n)
        .andExpect(jsonPath("$.items[0].modules[*].key").value(org.hamcrest.Matchers.contains("api", "web-frontend")));

    // 통째로 바꾼다 — 판 · 이력 · 감사
    api.patch(s.curator(), BASE + "/issues/{n}", Map.of("rowVersion", rowVersion(s, n), "modules", List.of("docs", "api")),
        s.ws(), P, n).andExpect(status().isOk())
        .andExpect(jsonPath("$.modules[*].key").value(org.hamcrest.Matchers.contains("api", "docs")))
        .andExpect(jsonPath("$.revisionNo").value(2))
        .andExpect(jsonPath("$.journal[1].changes[0].field").value("modules"))
        .andExpect(jsonPath("$.journal[1].changes[0].before[*].key").value(org.hamcrest.Matchers.contains("api", "web-frontend")))
        .andExpect(jsonPath("$.journal[1].changes[0].after[*].key").value(org.hamcrest.Matchers.contains("api", "docs")));
    // 같은 묶음을 다른 순서로 주면 바뀐 것이 없다
    api.patch(s.curator(), BASE + "/issues/{n}", Map.of("rowVersion", rowVersion(s, n), "modules", List.of("docs", "api")),
        s.ws(), P, n).andExpect(jsonPath("$.revisionNo").value(2));
    Map<String, Object> clear = new HashMap<>();
    clear.put("rowVersion", rowVersion(s, n));
    clear.put("modules", null);
    api.patch(s.curator(), BASE + "/issues/{n}", clear, s.ws(), P, n).andExpect(status().isOk())
        .andExpect(jsonPath("$.modules.length()").value(0)).andExpect(jsonPath("$.revisionNo").value(3));

    api.get(s.member(), BASE + "/issues/{n}/revisions/2", s.ws(), P, n)
        .andExpect(jsonPath("$.modules[*].key").value(org.hamcrest.Matchers.contains("api", "docs")));
    api.get(s.member(), BASE + "/issues/{n}/revisions/3/diff", s.ws(), P, n)
        .andExpect(jsonPath("$.fields[?(@.field == 'modules')].before[*].key").value(org.hamcrest.Matchers.contains("api", "docs")));
    api.get(s.owner(), "/workspaces/{ws}/audit?action=issue.update", s.ws())
        .andExpect(jsonPath("$[0].details").value(org.hamcrest.Matchers.containsString("modules")));

    api.patch(s.curator(), BASE + "/issues/{n}", Map.of("rowVersion", rowVersion(s, n), "modules", "api"), s.ws(), P, n)
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_FIELD"));
  }

  @Test
  void 없는_모듈과_쓰지_않는_모듈은_새로_붙일_수_없지만_이미_붙은_것은_남는다() throws Exception {
    Space s = space();
    seed(s);
    api.post(s.curator(), BASE + "/issues", Map.of("type", "bug", "title", "없는 모듈", "modules", List.of("api", "mobile")), s.ws(), P)
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("UNKNOWN_MODULE"))
        .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("mobile")));
    int n = issue(s, s.curator(), Map.of("modules", List.of("infra")));
    api.patch(s.owner(), MODULES + "/infra", Map.of("active", false), s.ws(), P).andExpect(status().isOk());

    api.post(s.curator(), BASE + "/issues", Map.of("type", "bug", "title", "쓰지 않는 모듈", "modules", List.of("infra")), s.ws(), P)
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MODULE_INACTIVE"));
    // 이미 붙은 쓰지 않는 모듈 — 다른 것을 고쳐도 · 모듈 묶음에 그대로 두어도 된다
    api.patch(s.curator(), BASE + "/issues/{n}", Map.of("rowVersion", rowVersion(s, n), "title", "제목만"), s.ws(), P, n)
        .andExpect(status().isOk()).andExpect(jsonPath("$.modules[0].key").value("infra"))
        .andExpect(jsonPath("$.modules[0].active").value(false));
    api.patch(s.curator(), BASE + "/issues/{n}", Map.of("rowVersion", rowVersion(s, n), "modules", List.of("infra", "docs")),
        s.ws(), P, n).andExpect(status().isOk())
        .andExpect(jsonPath("$.modules[*].key").value(org.hamcrest.Matchers.contains("infra", "docs")));
    // 빼고 나면 다시 붙일 수 없다
    api.patch(s.curator(), BASE + "/issues/{n}", Map.of("rowVersion", rowVersion(s, n), "modules", List.of("docs")), s.ws(), P, n)
        .andExpect(status().isOk());
    api.patch(s.curator(), BASE + "/issues/{n}", Map.of("rowVersion", rowVersion(s, n), "modules", List.of("infra")), s.ws(), P, n)
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MODULE_INACTIVE"));
  }

  @Test
  void 목록은_모듈_가운데_하나라도_붙은_일감으로_거르고_모듈마다_일감_수를_센다() throws Exception {
    Space s = space();
    seed(s);
    int apiOnly = issue(s, s.curator(), Map.of("modules", List.of("api")));
    int both = issue(s, s.curator(), Map.of("modules", List.of("api", "docs")));
    int docs = issue(s, s.curator(), Map.of("modules", List.of("docs")));
    int web = issue(s, s.curator(), Map.of("modules", List.of("web-frontend")));
    int none = issue(s, s.curator(), Map.of());
    int gone = issue(s, s.owner(), Map.of("modules", List.of("api")));
    api.delete(s.owner(), BASE + "/issues/{n}", s.ws(), P, gone).andExpect(status().isNoContent());

    List<Integer> found = TestApi.read(api.get(s.member(), BASE + "/issues?module=api&module=docs&sort=number", s.ws(), P),
        "$.items[*].number");
    assertThat(found).containsExactly(apiOnly, both, docs);
    found = TestApi.read(api.get(s.member(), BASE + "/issues?module=web-frontend", s.ws(), P), "$.items[*].number");
    assertThat(found).containsExactly(web);
    assertThat(none).isPositive();
    api.get(s.member(), BASE + "/issues?module=mobile", s.ws(), P).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("UNKNOWN_MODULE"));
    // 쓰지 않는 모듈로도 거른다(옛 일감을 찾는다)
    api.patch(s.owner(), MODULES + "/web-frontend", Map.of("active", false), s.ws(), P).andExpect(status().isOk());
    found = TestApi.read(api.get(s.member(), BASE + "/issues?module=web-frontend", s.ws(), P), "$.items[*].number");
    assertThat(found).containsExactly(web);

    // 지운 일감은 세지 않는다
    api.get(s.member(), MODULES, s.ws(), P).andExpect(jsonPath("$[?(@.key == 'api')].issueCount").value(2))
        .andExpect(jsonPath("$[?(@.key == 'docs')].issueCount").value(2))
        .andExpect(jsonPath("$[?(@.key == 'infra')].issueCount").value(0));
  }

  // ── MCP ─────────────────────────────────────────────────────────────

  @Test
  void MCP_일감_입구는_모듈을_보고_일감에_붙이고_거른다() throws Exception {
    Space s = space();
    seed(s);
    McpTestClient mcp = new McpTestClient(mvc, key(s.curator()));
    String list = mcp.call("list_modules", args("project", P));
    assertThat((List<String>) read(list, "$.modules[*].key")).containsExactly("api", "web-backend", "web-frontend", "infra", "docs");
    assertThat((Boolean) read(list, "$.canManage")).isFalse();
    assertThat(mcp.tools()).contains("list_modules").doesNotContain("set_module");

    String created = mcp.call("create_issue", args("project", P, "type", "feature", "title", "모듈 일감",
        "modules", List.of("web-frontend", "api"), "agentSession", "moai-works"));
    int n = read(created, "$.number");
    assertThat((List<String>) read(created, "$.modules[*].key")).containsExactly("api", "web-frontend");
    assertThat((String) read(mcp.call("create_issue", args("project", P, "type", "bug", "title", "x", "modules", List.of("nope"))),
        "$.error.code")).isEqualTo("UNKNOWN_MODULE");

    int rv = read(mcp.call("get_issue", args("project", P, "number", n)), "$.rowVersion");
    String updated = mcp.call("update_issue", args("project", P, "number", n, "rowVersion", rv, "modules", List.of("docs")));
    assertThat((List<String>) read(updated, "$.modules[*].key")).containsExactly("docs");
    int other = read(mcp.call("create_issue", args("project", P, "type", "bug", "title", "API 버그", "modules", List.of("api"))),
        "$.number");

    String found = mcp.call("search_issues", args("project", P, "module", "docs, api"));
    assertThat((List<Integer>) read(found, "$.items[*].number")).containsExactlyInAnyOrder(n, other);
    assertThat((List<String>) read(found, "$.items[?(@.number == " + n + ")].modules[*].key")).containsExactly("docs");

    rv = read(updated, "$.rowVersion");
    String cleared = mcp.call("update_issue", args("project", P, "number", n, "rowVersion", rv, "clear", "modules"));
    assertThat((List<Object>) read(cleared, "$.modules")).isEmpty();
  }

  @Test
  void MCP_프로젝트_관리_입구에서_모듈을_관리한다_권한은_웹과_같다() throws Exception {
    Space s = space();
    seed(s);
    McpTestClient curator = new McpTestClient(mvc, key(s.curator()), McpTestClient.PROJECT);
    assertThat((String) read(curator.call("set_module", args("project", P, "key", "mobile", "name", "모바일")), "$.error.code"))
        .isEqualTo("PERMISSION_REQUIRED");

    McpTestClient owner = new McpTestClient(mvc, key(s.owner()), McpTestClient.PROJECT);
    assertThat(owner.tools()).contains("list_modules", "set_module");
    String added = owner.call("set_module", args("project", P, "key", "mobile", "name", "모바일", "description", "앱", "position", 0));
    assertThat((Boolean) read(added, "$.created")).isTrue();
    assertThat((Integer) read(added, "$.module.position")).isZero();
    assertThat((String) read(owner.call("set_module", args("project", P, "key", "tools")), "$.error.code"))
        .isEqualTo("INVALID_MODULE_NAME");

    String changed = owner.call("set_module", args("project", P, "key", "infra", "active", false, "name", "인프라(옛)"));
    assertThat((Boolean) read(changed, "$.created")).isFalse();
    assertThat((Boolean) read(changed, "$.module.active")).isFalse();
    assertThat((String) read(changed, "$.module.name")).isEqualTo("인프라(옛)");
    String moved = owner.call("set_module", args("project", P, "key", "docs", "position", 1));
    assertThat((Integer) read(moved, "$.module.position")).isEqualTo(1);
    assertThat((List<String>) read(owner.call("list_modules", args("project", P)), "$.modules[*].key"))
        .containsExactly("mobile", "docs", "api", "web-backend", "web-frontend", "infra");

    // 감사에는 경로 MCP
    api.get(s.owner(), "/workspaces/{ws}/audit?action=project.module.create", s.ws())
        .andExpect(jsonPath("$[0].details").value(org.hamcrest.Matchers.allOf(org.hamcrest.Matchers.containsString("mobile"),
            org.hamcrest.Matchers.containsString("MCP"))));
    api.get(s.owner(), "/workspaces/{ws}/audit?action=project.module.reorder", s.ws())
        .andExpect(jsonPath("$[0].details").value(org.hamcrest.Matchers.containsString("MCP")));
  }
}
