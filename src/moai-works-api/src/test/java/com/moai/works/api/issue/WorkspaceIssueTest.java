package com.moai.works.api.issue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import com.moai.works.api.support.ApiTest;
import com.moai.works.api.support.TestApi;
import com.moai.works.api.support.TestUser;

/**
 * 일감은 워크스페이스 아래에 있다 — 번호는 워크스페이스에서 하나, 프로젝트는 속성(옮길 수 있다), 워크스페이스 일감 목록 · 찾기,
 * 다른 프로젝트와 연결 (2026-11-24 사용자 결정, ADR-030 · moai-works 일감 #29 — "레드마인을 생각하면 돼").
 */
@ApiTest
class WorkspaceIssueTest {

  private static final String ISSUE = "/workspaces/{ws}/issues/{n}";
  private static final String PROJECT_ISSUES = "/workspaces/{ws}/projects/{p}/issues";
  private static final String WS_ISSUES = "/workspaces/{ws}/issues";
  /** 가장 작은 PNG 머리 */
  private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D, 'I', 'H', 'D', 'R'};

  @Autowired
  TestApi api;
  @Autowired
  JdbcTemplate jdbc;

  /**
   * 워크스페이스 하나 — 프로젝트 alpha · beta(공개), secret(지정 멤버). editor 는 멤버이면서 alpha · beta 의 편집자,
   * member 는 그냥 멤버(읽기 · 댓글), alphaOnly 는 alpha 의 편집자이기만 하다.
   */
  private record Space(String ws, TestUser owner, TestUser editor, TestUser member, TestUser alphaOnly) {
  }

  private Space space() throws Exception {
    TestUser owner = api.user("owner");
    String ws = api.teamWorkspace(owner, "워크스페이스 일감");
    TestUser editor = api.user("editor");
    TestUser member = api.user("member");
    TestUser alphaOnly = api.user("alpha-only");
    api.join(ws, owner, editor, "member");
    api.join(ws, owner, member, "member");
    api.join(ws, owner, alphaOnly, "member");
    project(ws, owner, "alpha", "알파", "WORKSPACE");
    project(ws, owner, "beta", "베타", "WORKSPACE");
    project(ws, owner, "secret", "비밀", "RESTRICTED");
    for (String p : List.of("alpha", "beta")) {
      api.put(owner, "/workspaces/{ws}/projects/{p}/roles/{user}", Map.of("roles", List.of("project-editor")), ws, p,
          editor.id()).andExpect(status().isOk());
    }
    api.put(owner, "/workspaces/{ws}/projects/{p}/roles/{user}", Map.of("roles", List.of("project-editor")), ws, "alpha",
        alphaOnly.id()).andExpect(status().isOk());
    return new Space(ws, owner, editor, member, alphaOnly);
  }

  private void project(String ws, TestUser owner, String key, String name, String visibility) throws Exception {
    api.post(owner, "/workspaces/{ws}/projects", Map.of("key", key, "name", name, "visibility", visibility), ws)
        .andExpect(status().isCreated());
  }

  private int create(String ws, TestUser by, String project, Map<String, Object> body) throws Exception {
    Map<String, Object> b = new HashMap<>(body);
    b.putIfAbsent("type", "feature");
    return TestApi.read(api.post(by, PROJECT_ISSUES, b, ws, project).andExpect(status().isCreated()), "$.number");
  }

  private int create(Space s, TestUser by, String project, String title) throws Exception {
    return create(s.ws(), by, project, Map.of("title", title));
  }

  private ResultActions patch(Space s, TestUser by, int n, Map<String, Object> changes) throws Exception {
    int rowVersion = TestApi.read(api.get(s.owner(), ISSUE, s.ws(), n).andExpect(status().isOk()), "$.rowVersion");
    Map<String, Object> body = new HashMap<>(changes);
    body.putIfAbsent("rowVersion", rowVersion);
    return api.patch(by, ISSUE, body, s.ws(), n);
  }

  // ── 번호 ───────────────────────────────────────────────────────────

  @Test
  void 번호는_워크스페이스에서_하나이고_다른_워크스페이스와는_따로다() throws Exception {
    Space s = space();
    int a1 = create(s, s.editor(), "alpha", "알파 첫 일");
    int b1 = create(s, s.editor(), "beta", "베타 첫 일");
    int a2 = create(s, s.editor(), "alpha", "알파 둘째 일");
    assertThat(b1).isEqualTo(a1 + 1);
    assertThat(a2).isEqualTo(b1 + 1);
    // 프로젝트 없이 번호만으로 — 응답에 그 일감의 프로젝트
    api.get(s.member(), ISSUE, s.ws(), b1).andExpect(status().isOk()).andExpect(jsonPath("$.number").value(b1))
        .andExpect(jsonPath("$.project.key").value("beta")).andExpect(jsonPath("$.project.name").value("베타"));

    // 다른 워크스페이스는 자기 순번을 1 부터
    TestUser other = api.user("other-owner");
    String ws2 = api.teamWorkspace(other, "다른 워크스페이스");
    project(ws2, other, "alpha", "알파", "WORKSPACE");
    assertThat(create(ws2, other, "alpha", Map.of("title", "다른 곳 첫 일"))).isEqualTo(1);
    assertThat(create(ws2, other, "alpha", Map.of("title", "다른 곳 둘째 일"))).isEqualTo(2);
    // 순번은 워크스페이스 행에 있다 — 프로젝트의 옛 순번 열은 없다
    assertThat(jdbc.queryForObject("SELECT issue_seq FROM workspace WHERE slug = ?", Integer.class, ws2)).isEqualTo(2);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.columns WHERE table_schema = current_schema()"
        + " AND table_name = 'project' AND column_name = 'issue_seq'", Integer.class)).isZero();
  }

  @Test
  void 동시에_만들어도_번호가_겹치지_않는다() throws Exception {
    Space s = space();
    int before = create(s, s.owner(), "alpha", "앞");
    ExecutorService pool = Executors.newFixedThreadPool(4);
    CountDownLatch start = new CountDownLatch(1);
    List<Future<Integer>> results = new ArrayList<>();
    for (int i = 0; i < 8; i++) {
      String project = i % 2 == 0 ? "alpha" : "beta";
      String title = "동시 " + i;
      results.add(pool.submit(() -> {
        start.await();
        return create(s, s.editor(), project, title);
      }));
    }
    start.countDown();
    List<Integer> numbers = new ArrayList<>();
    for (Future<Integer> f : results) {
      numbers.add(f.get());
    }
    pool.shutdown();
    Collections.sort(numbers);
    // 겹치지도 빠지지도 않는다 — 앞 번호 다음부터 여덟 개
    List<Integer> expected = new ArrayList<>();
    for (int i = 1; i <= 8; i++) {
      expected.add(before + i);
    }
    assertThat(numbers).isEqualTo(expected);
  }

  @Test
  void 지운_일감의_번호는_다시_쓰지_않는다() throws Exception {
    Space s = space();
    int n = create(s, s.owner(), "alpha", "지울 일");
    api.delete(s.owner(), ISSUE, s.ws(), n).andExpect(status().isNoContent());
    api.get(s.owner(), ISSUE, s.ws(), n).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ISSUE_NOT_FOUND"));
    int next = create(s, s.owner(), "beta", "다음 일");
    assertThat(next).isEqualTo(n + 1);
  }

  // ── 정식 주소 · 판정 ────────────────────────────────────────────────

  @Test
  void 정식_주소는_그_일감의_지금_프로젝트로_판정한다() throws Exception {
    Space s = space();
    int hidden = create(s, s.owner(), "secret", "비밀 일");
    int open = create(s, s.owner(), "alpha", "공개 일");
    // 독자가 아니면 없는 번호와 같은 404 — 어느 프로젝트인지도 숨긴다
    api.get(s.member(), ISSUE, s.ws(), hidden).andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("ISSUE_NOT_FOUND"));
    api.get(s.member(), ISSUE, s.ws(), 99999).andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("ISSUE_NOT_FOUND"));
    api.get(s.member(), ISSUE + "/comments", s.ws(), hidden).andExpect(status().isNotFound());
    api.get(s.owner(), ISSUE, s.ws(), hidden).andExpect(status().isOk()).andExpect(jsonPath("$.project.key").value("secret"));
    // 독자인데 권한이 없으면 403
    api.patch(s.member(), ISSUE, Map.of("rowVersion", 0, "title", "멤버가 고침"), s.ws(), open).andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("PERMISSION_REQUIRED"));
    // 멤버가 아니면 워크스페이스부터 없다
    TestUser stranger = api.user("stranger");
    api.get(stranger, ISSUE, s.ws(), open).andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("WORKSPACE_NOT_FOUND"));
  }

  @Test
  void 옛_링크는_번호_바꾼_기록으로_지금_번호를_찾는다() throws Exception {
    Space s = space();
    int n = create(s, s.owner(), "alpha", "옛 번호가 있던 일");
    int hidden = create(s, s.owner(), "secret", "옛 번호가 있던 비밀 일");
    // V23 이 남기는 기록과 같은 모양 — alpha 의 옛 #7 은 지금 n, secret 의 옛 #7 은 지금 hidden
    record(s.ws(), n, "alpha", 7);
    record(s.ws(), hidden, "secret", 7);
    String ref = "/workspaces/{ws}/projects/{p}/issue-refs/{n}";
    api.get(s.member(), ref, s.ws(), "alpha", 7).andExpect(status().isOk()).andExpect(jsonPath("$.number").value(n));
    // 읽을 수 없는 일감 · 없는 프로젝트 · 없는 번호는 모두 같은 404
    api.get(s.member(), ref, s.ws(), "secret", 7).andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("ISSUE_NOT_FOUND"));
    api.get(s.owner(), ref, s.ws(), "secret", 7).andExpect(status().isOk()).andExpect(jsonPath("$.number").value(hidden));
    api.get(s.member(), ref, s.ws(), "nothing", 7).andExpect(status().isNotFound());
    // 기록이 없는 번호(V23 뒤의 일감)는 그 프로젝트의 그 번호
    api.get(s.member(), ref, s.ws(), "alpha", n).andExpect(status().isOk()).andExpect(jsonPath("$.number").value(n));
    api.get(s.member(), ref, s.ws(), "beta", n).andExpect(status().isNotFound());
  }

  private void record(String ws, int number, String projectKey, int oldNumber) {
    jdbc.update("""
        INSERT INTO issue_number_change (issue_id, workspace_id, project_id, project_key, old_number, new_number, changed_at)
        SELECT i.id, i.workspace_id, i.project_id, p.key, ?, i.number, now()
        FROM issue i JOIN project p ON p.id = i.project_id JOIN workspace w ON w.id = i.workspace_id
        WHERE w.slug = ? AND i.number = ? AND p.key = ?""", oldNumber, ws, number, projectKey);
  }

  // ── 워크스페이스 목록 · 찾기 ─────────────────────────────────────────

  @Test
  void 워크스페이스_목록은_읽을_수_있는_프로젝트만_세고_프로젝트로_거르고_번호로_찾는다() throws Exception {
    Space s = space();
    int a = create(s, s.owner(), "alpha", "알파 로그인 버그");
    int b = create(s, s.owner(), "beta", "베타 보고서");
    int hidden = create(s, s.owner(), "secret", "비밀 로그인 버그");

    ResultActions all = api.get(s.member(), WS_ISSUES + "?size=100", s.ws()).andExpect(status().isOk());
    List<Integer> numbers = TestApi.read(all, "$.items[*].number");
    assertThat(numbers).contains(a, b).doesNotContain(hidden);
    assertThat((Integer) TestApi.read(all, "$.total")).isEqualTo(2);
    List<String> projects = TestApi.read(all, "$.items[*].project.key");
    assertThat(projects).containsExactlyInAnyOrder("alpha", "beta");
    // 소유자는 지정 멤버 프로젝트도 읽는다
    assertThat((Integer) TestApi.read(api.get(s.owner(), WS_ISSUES, s.ws()), "$.total")).isEqualTo(3);

    // 프로젝트로 거르기(여러 번) — 읽을 수 없는 프로젝트를 줘도 그 프로젝트의 일감은 나오지 않는다
    assertThat(TestApi.<List<Integer>>read(api.get(s.member(), WS_ISSUES + "?project=beta", s.ws()), "$.items[*].number"))
        .containsExactly(b);
    assertThat(TestApi.<List<Integer>>read(api.get(s.member(), WS_ISSUES + "?project=alpha&project=beta", s.ws()),
        "$.items[*].number")).containsExactlyInAnyOrder(a, b);
    api.get(s.member(), WS_ISSUES + "?project=secret", s.ws()).andExpect(jsonPath("$.total").value(0));
    // 찾기 — 제목 · 설명, 또는 #번호(번호는 워크스페이스에서 하나)
    assertThat(TestApi.<List<Integer>>read(api.get(s.member(), WS_ISSUES + "?q=로그인", s.ws()), "$.items[*].number"))
        .containsExactly(a);
    assertThat(TestApi.<List<Integer>>read(api.get(s.member(), WS_ISSUES + "?q={q}", s.ws(), "#" + b), "$.items[*].number"))
        .containsExactly(b);
    api.get(s.member(), WS_ISSUES + "?q={q}", s.ws(), "#" + hidden).andExpect(jsonPath("$.total").value(0));
    // 거르기 칸 — 유형 · 쓰인 에이전트 세션 · 모듈
    api.get(s.member(), "/workspaces/{ws}/issue-types", s.ws()).andExpect(status().isOk());
    api.get(s.member(), "/workspaces/{ws}/agent-sessions", s.ws()).andExpect(status().isOk());
    api.get(s.member(), "/workspaces/{ws}/issue-modules", s.ws()).andExpect(status().isOk());
    api.get(s.member(), WS_ISSUES + "?sort=nope", s.ws()).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_SORT"));
  }

  @Test
  void 워크스페이스_목록의_세션_모듈_거르기는_읽을_수_있는_프로젝트에서만() throws Exception {
    Space s = space();
    api.post(s.owner(), "/workspaces/{ws}/projects/{p}/modules", Map.of("key", "api", "name", "API"), s.ws(), "alpha")
        .andExpect(status().isCreated());
    api.post(s.owner(), "/workspaces/{ws}/projects/{p}/modules", Map.of("key", "api", "name", "API"), s.ws(), "beta")
        .andExpect(status().isCreated());
    api.post(s.owner(), "/workspaces/{ws}/projects/{p}/modules", Map.of("key", "vault", "name", "금고"), s.ws(), "secret")
        .andExpect(status().isCreated());
    int a = create(s.ws(), s.owner(), "alpha", Map.of("title", "알파 api", "modules", List.of("api"), "agentSession", "moai-works"));
    int b = create(s.ws(), s.owner(), "beta", Map.of("title", "베타 api", "modules", List.of("api")));
    create(s.ws(), s.owner(), "secret", Map.of("title", "금고", "modules", List.of("vault"), "agentSession", "hidden-session"));

    assertThat(TestApi.<List<Integer>>read(api.get(s.member(), WS_ISSUES + "?module=api", s.ws()), "$.items[*].number"))
        .containsExactlyInAnyOrder(a, b);
    // 읽을 수 없는 프로젝트에만 있는 모듈은 없는 모듈과 같다
    api.get(s.member(), WS_ISSUES + "?module=vault", s.ws()).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("UNKNOWN_MODULE"));
    List<String> modules = TestApi.read(api.get(s.member(), "/workspaces/{ws}/issue-modules", s.ws()), "$[*].key");
    assertThat(modules).containsExactly("api");
    List<String> sessions = TestApi.read(api.get(s.member(), "/workspaces/{ws}/agent-sessions", s.ws()), "$[*].name");
    assertThat(sessions).containsExactly("moai-works");
    assertThat(TestApi.<List<Integer>>read(api.get(s.member(), WS_ISSUES + "?agentSession=moai-works", s.ws()),
        "$.items[*].number")).containsExactly(a);
  }

  // ── 연결 ───────────────────────────────────────────────────────────

  @Test
  void 다른_프로젝트와_연결하고_읽을_수_없는_쪽은_숨긴다() throws Exception {
    Space s = space();
    int a = create(s, s.owner(), "alpha", "알파 일");
    int b = create(s, s.owner(), "beta", "베타 일");
    int hidden = create(s, s.owner(), "secret", "비밀 일");
    // 다른 프로젝트여도 같은 워크스페이스면 연결된다
    api.post(s.editor(), ISSUE + "/links", Map.of("target", b, "type", "RELATES"), s.ws(), a).andExpect(status().isOk())
        .andExpect(jsonPath("$.links[0].issue.number").value(b))
        .andExpect(jsonPath("$.links[0].issue.project.key").value("beta"));
    api.get(s.member(), ISSUE, s.ws(), b).andExpect(jsonPath("$.links[0].issue.number").value(a));
    // 읽을 수 없는 일감과는 연결할 수 없다 — 없는 번호와 같은 400
    api.post(s.editor(), ISSUE + "/links", Map.of("target", hidden, "type", "RELATES"), s.ws(), a)
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("LINK_TARGET_NOT_FOUND"));
    // 소유자는 비밀 일감과 잇는다 — 비밀 프로젝트를 못 읽는 사람에게는 그 연결이 보이지 않는다
    api.post(s.owner(), ISSUE + "/links", Map.of("target", hidden, "type", "BLOCKS"), s.ws(), a).andExpect(status().isOk());
    assertThat(TestApi.<List<Integer>>read(api.get(s.owner(), ISSUE, s.ws(), a), "$.links[*].issue.number"))
        .containsExactlyInAnyOrder(b, hidden);
    assertThat(TestApi.<List<Integer>>read(api.get(s.member(), ISSUE, s.ws(), a), "$.links[*].issue.number"))
        .containsExactly(b);
    assertThat(TestApi.<List<String>>read(api.get(s.member(), ISSUE, s.ws(), a), "$.links[*].issue.title"))
        .doesNotContain("비밀 일");
  }

  // ── 옮기기 ─────────────────────────────────────────────────────────

  @Test
  void 옮기기는_두_프로젝트의_권한을_보고_하위까지_옮기며_상위는_끊고_모듈은_key_로_잇는다() throws Exception {
    Space s = space();
    for (String[] m : List.of(new String[] {"alpha", "api"}, new String[] {"alpha", "web"}, new String[] {"beta", "api"})) {
      api.post(s.owner(), "/workspaces/{ws}/projects/{p}/modules", Map.of("key", m[1], "name", m[1].toUpperCase()), s.ws(), m[0])
          .andExpect(status().isCreated());
    }
    int top = create(s, s.editor(), "alpha", "남을 상위");
    int root = create(s.ws(), s.editor(), "alpha", Map.of("title", "옮길 일", "parent", top, "modules", List.of("api", "web")));
    int child = create(s.ws(), s.editor(), "alpha", Map.of("title", "하위", "parent", root, "modules", List.of("web")));
    int grandchild = create(s.ws(), s.editor(), "alpha", Map.of("title", "하위의 하위", "parent", child));
    String attachment = TestApi.read(api.upload(s.editor(), ISSUE + "/attachments", "화면.png", "image/png", PNG, Map.of(),
        s.ws(), root).andExpect(status().isCreated()), "$.id");
    api.post(s.member(), ISSUE + "/comments", Map.of("notes", "옮기기 전 댓글"), s.ws(), root).andExpect(status().isOk());

    // 권한: 고칠 수 없으면 403, 옮길 프로젝트를 읽을 수 없으면 없는 것과 같은 400, 옮길 곳에 만들 권한이 없으면 403
    patch(s, s.member(), root, Map.of("project", "beta")).andExpect(status().isForbidden());
    patch(s, s.editor(), root, Map.of("project", "secret")).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("TARGET_PROJECT_NOT_FOUND"));
    patch(s, s.editor(), root, Map.of("project", "nothing")).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("TARGET_PROJECT_NOT_FOUND"));
    patch(s, s.alphaOnly(), root, Map.of("project", "beta")).andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("PERMISSION_REQUIRED"));

    // 유형: 옮길 곳에서 쓸 수 없는 유형이면 거절 — 하위 일감 것도. 같은 요청에서 바꾸면 된다(뿌리)
    jdbc.update("UPDATE issue SET issue_type = 'task' WHERE number = ? AND workspace_id = (SELECT id FROM workspace WHERE slug = ?)",
        child, s.ws());
    patch(s, s.editor(), root, Map.of("project", "beta")).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("ISSUE_TYPE_NOT_ALLOWED"))
        .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("#" + child)));
    jdbc.update("UPDATE issue SET issue_type = 'feature' WHERE number = ? AND workspace_id = (SELECT id FROM workspace WHERE slug = ?)",
        child, s.ws());
    jdbc.update("UPDATE issue SET issue_type = 'task' WHERE number = ? AND workspace_id = (SELECT id FROM workspace WHERE slug = ?)",
        root, s.ws());
    patch(s, s.editor(), root, Map.of("project", "beta")).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("ISSUE_TYPE_NOT_ALLOWED"));

    // 옮긴다 — 번호는 그대로, 상위는 끊기고, 모듈은 같은 key 가 있는 api 만 남는다
    patch(s, s.editor(), root, Map.of("project", "beta", "type", "bug")).andExpect(status().isOk())
        .andExpect(jsonPath("$.number").value(root)).andExpect(jsonPath("$.project.key").value("beta"))
        .andExpect(jsonPath("$.type").value("bug")).andExpect(jsonPath("$.parent").doesNotExist())
        .andExpect(jsonPath("$.modules[*].key").value(org.hamcrest.Matchers.contains("api")))
        .andExpect(jsonPath("$.children[0].number").value(child));
    // 하위 줄기도 함께 — 상위 · 하위는 그대로 이어진다
    api.get(s.member(), ISSUE, s.ws(), child).andExpect(jsonPath("$.project.key").value("beta"))
        .andExpect(jsonPath("$.parent.number").value(root)).andExpect(jsonPath("$.modules").isEmpty())
        .andExpect(jsonPath("$.journal[-1].changes[?(@.field == 'project')].after.key").value("beta"))
        .andExpect(jsonPath("$.journal[-1].changes[?(@.field == 'modules')].before[0].key").value("web"));
    api.get(s.member(), ISSUE, s.ws(), grandchild).andExpect(jsonPath("$.project.key").value("beta"))
        .andExpect(jsonPath("$.parent.number").value(child));
    api.get(s.member(), ISSUE, s.ws(), top).andExpect(jsonPath("$.project.key").value("alpha"))
        .andExpect(jsonPath("$.children").isEmpty());
    assertThat(TestApi.<List<Integer>>read(api.get(s.member(), PROJECT_ISSUES + "?size=100", s.ws(), "beta"),
        "$.items[*].number")).containsExactlyInAnyOrder(root, child, grandchild);
    assertThat(TestApi.<List<Integer>>read(api.get(s.member(), PROJECT_ISSUES + "?size=100", s.ws(), "alpha"),
        "$.items[*].number")).containsExactly(top);

    // 이력 — 프로젝트 · 상위 · 모듈 · 유형이 한 줄에, 새 판에 프로젝트, 판 차이에 project
    ResultActions detail = api.get(s.member(), ISSUE, s.ws(), root);
    List<String> fields = TestApi.read(detail, "$.journal[-1].changes[*].field");
    assertThat(fields).contains("project", "parent", "modules", "type");
    detail.andExpect(jsonPath("$.journal[-1].changes[0].before.key").value("alpha"))
        .andExpect(jsonPath("$.journal[-1].changes[0].after.key").value("beta"));
    int revisionNo = TestApi.read(detail, "$.revisionNo");
    api.get(s.member(), ISSUE + "/revisions/{no}", s.ws(), root, revisionNo).andExpect(jsonPath("$.project.key").value("beta"));
    api.get(s.member(), ISSUE + "/revisions/{no}", s.ws(), root, 1).andExpect(jsonPath("$.project.key").value("alpha"));
    assertThat(TestApi.<List<String>>read(api.get(s.member(), ISSUE + "/revisions/{no}/diff", s.ws(), root, revisionNo),
        "$.fields[*].field")).contains("project");
    // 댓글 · 첨부는 일감을 따라온다 — 받기도 그대로(저장소 객체 이름은 그대로, 첨부 행의 프로젝트만 따라간다)
    assertThat(TestApi.<List<String>>read(detail, "$.journal[*].notes")).contains("옮기기 전 댓글");
    api.get(s.member(), ISSUE + "/attachments/{id}/content", s.ws(), root, attachment).andExpect(status().isOk())
        .andExpect(content().bytes(PNG));
    assertThat(jdbc.queryForObject("SELECT p.key FROM issue_attachment a JOIN project p ON p.id = a.project_id WHERE a.id = ?",
        String.class, UUID.fromString(attachment))).isEqualTo("beta");
    // 감사 — issue.move 하나(옮긴 번호들 · 끊은 상위 · 빠진 모듈)
    api.get(s.owner(), "/workspaces/{ws}/audit?action=issue.move", s.ws()).andExpect(jsonPath("$[0].action").value("issue.move"))
        .andExpect(jsonPath("$[0].details").value(org.hamcrest.Matchers.allOf(org.hamcrest.Matchers.containsString("alpha"),
            org.hamcrest.Matchers.containsString("beta"), org.hamcrest.Matchers.containsString("web"))));
    // 옮긴 뒤 옛 프로젝트로는 고칠 수 없다(그 일감은 이제 beta 의 일) — alpha 만 편집하는 사람에게 403
    patch(s, s.alphaOnly(), root, Map.of("title", "alpha 편집자가 고침")).andExpect(status().isForbidden());
  }
}
