package com.moai.works.api.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

import com.moai.works.api.support.ApiTest;
import com.moai.works.api.support.TestApi;
import com.moai.works.api.support.TestUser;

/**
 * 프로젝트 — 만들기 · 보기 · 설정 · 보관 · 삭제와 접근 판정 (moai-wiki 의 WikiTest 와 같은 시험).
 * 독자가 아니면 404(존재를 숨김), 독자인데 권한이 없으면 403. (design/04)
 */
@ApiTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProjectTest {

  @Autowired
  TestApi api;

  TestUser owner;
  TestUser admin;
  TestUser curator;
  TestUser member;
  TestUser outsider;
  String ws;

  @BeforeAll
  void setUp() throws Exception {
    owner = api.user("owner");
    admin = api.user("admin");
    curator = api.user("curator");
    member = api.user("member");
    outsider = api.user("outsider");
    ws = api.teamWorkspace(owner, "프로젝트 시험");
    api.join(ws, owner, admin, "admin");
    api.join(ws, owner, curator, "curator");
    api.join(ws, owner, member, "member");
  }

  private ResultActions create(TestUser by, String workspace, String key, String name, String visibility)
      throws Exception {
    return api.post(by, "/workspaces/{ws}/projects", Map.of("key", key, "name", name, "visibility", visibility),
        workspace);
  }

  private List<String> keys(TestUser as, boolean archived) throws Exception {
    return TestApi.read(api.get(as, "/workspaces/{ws}/projects?archived=" + archived, ws).andExpect(status().isOk()),
        "$[*].key");
  }

  private static final Map<String, Object> SETTINGS = Map.of("name", "고친 이름", "description", "설명",
      "visibility", "WORKSPACE");

  // ── 만들기 · 보기 ──────────────────────────────────────────────────

  @Test
  void 소유자가_만든_프로젝트는_멤버_모두에게_보인다() throws Exception {
    create(owner, ws, "hr", "인사 프로젝트", "WORKSPACE").andExpect(status().isCreated())
        .andExpect(jsonPath("$.key").value("hr")).andExpect(jsonPath("$.status").value("ACTIVE"));

    for (TestUser reader : List.of(owner, admin, curator, member)) {
      assertThat(keys(reader, false)).contains("hr");
      api.get(reader, "/workspaces/{ws}/projects/{project}", ws, "hr").andExpect(status().isOk())
          .andExpect(jsonPath("$.name").value("인사 프로젝트"));
    }
    // 내 권한 — 멤버는 읽고 댓글만, 큐레이터는 일감을 만들고 고친다
    List<String> memberPermissions = TestApi.read(api.get(member, "/workspaces/{ws}/projects/{project}", ws, "hr"),
        "$.myPermissions");
    assertThat(memberPermissions).contains("issue.read", "issue.comment")
        .doesNotContain("issue.create", "issue.edit", "project.manage");
    List<String> curatorPermissions = TestApi.read(api.get(curator, "/workspaces/{ws}/projects/{project}", ws, "hr"),
        "$.myPermissions");
    assertThat(curatorPermissions).contains("issue.create", "issue.edit", "issue.manager.assign")
        .doesNotContain("project.manage", "issue.delete");
  }

  @Test
  void 만들기_권한이_없으면_403_멤버가_아니면_404() throws Exception {
    create(member, ws, "nope", "안 됨", "WORKSPACE").andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("PERMISSION_REQUIRED"));
    create(curator, ws, "nope", "안 됨", "WORKSPACE").andExpect(status().isForbidden());
    create(outsider, ws, "nope", "안 됨", "WORKSPACE").andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("WORKSPACE_NOT_FOUND"));
    api.get(outsider, "/workspaces/{ws}/projects", ws).andExpect(status().isNotFound());
  }

  @Test
  void 프로젝트_만들기_권한이_든_역할이면_만든다() throws Exception {
    TestUser maker = api.user("maker");
    api.join(ws, owner, maker, "member");
    String role = api.customRole(ws, owner, "프로젝트 담당", Set.of("workspace.read", "project.create"));
    api.setRoles(ws, owner, maker, "member", role);
    create(maker, ws, "by-maker", "담당자가 만든 프로젝트", "WORKSPACE").andExpect(status().isCreated());
    // 만들 수는 있지만 설정을 고칠 권한은 없다
    api.patch(maker, "/workspaces/{ws}/projects/{project}", SETTINGS, ws, "by-maker").andExpect(status().isForbidden());
  }

  @Test
  void 주소_이름_규칙과_중복() throws Exception {
    for (String bad : List.of("A", "x", "Hr", "9proj", "proj-", "프로젝트", "has space", "a".repeat(33))) {
      create(owner, ws, bad, "이름", "WORKSPACE").andExpect(status().isBadRequest());
    }
    create(owner, ws, "new", "예약어", "WORKSPACE").andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("RESERVED_PROJECT_KEY"));
    create(owner, ws, "dup", "첫째", "WORKSPACE").andExpect(status().isCreated());
    create(admin, ws, "dup", "둘째", "WORKSPACE").andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("PROJECT_KEY_TAKEN"));
  }

  @Test
  void 같은_주소_이름이라도_워크스페이스가_다르면_서로_모른다() throws Exception {
    TestUser other = api.user("other-owner");
    String otherWs = api.teamWorkspace(other, "다른 팀");
    create(owner, ws, "shared-key", "우리 프로젝트", "WORKSPACE").andExpect(status().isCreated());
    create(other, otherWs, "shared-key", "남의 프로젝트", "WORKSPACE").andExpect(status().isCreated());

    api.get(owner, "/workspaces/{ws}/projects/{project}", ws, "shared-key").andExpect(jsonPath("$.name").value("우리 프로젝트"));
    api.get(other, "/workspaces/{ws}/projects/{project}", otherWs, "shared-key")
        .andExpect(jsonPath("$.name").value("남의 프로젝트"));
    api.get(owner, "/workspaces/{ws}/projects/{project}", otherWs, "shared-key").andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("WORKSPACE_NOT_FOUND"));
  }

  @Test
  void 없는_프로젝트는_404() throws Exception {
    api.get(owner, "/workspaces/{ws}/projects/{project}", ws, "no-such-project").andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"));
  }

  // ── 지정 멤버 프로젝트 ─────────────────────────────────────────────────

  @Test
  void 지정_멤버_프로젝트는_독자가_아니면_없는_것과_같다() throws Exception {
    create(admin, ws, "secret", "지정 멤버 프로젝트", "RESTRICTED").andExpect(status().isCreated());

    // 소유자 · 관리자는 모든 프로젝트의 독자다
    for (TestUser reader : List.of(owner, admin)) {
      assertThat(keys(reader, false)).contains("secret");
      api.get(reader, "/workspaces/{ws}/projects/{project}", ws, "secret").andExpect(status().isOk());
    }
    // 큐레이터 · 멤버에게는 목록에도 없고, 직접 열어도 없는 프로젝트와 똑같은 404 다
    for (TestUser nonReader : List.of(curator, member)) {
      assertThat(keys(nonReader, false)).doesNotContain("secret");
      ResultActions hidden = api.get(nonReader, "/workspaces/{ws}/projects/{project}", ws, "secret")
          .andExpect(status().isNotFound());
      ResultActions missing = api.get(nonReader, "/workspaces/{ws}/projects/{project}", ws, "no-such-project")
          .andExpect(status().isNotFound());
      for (String field : List.of("$.code", "$.detail", "$.title")) {
        assertThat(TestApi.<Object>read(hidden, field)).isEqualTo(TestApi.<Object>read(missing, field));
      }
      api.patch(nonReader, "/workspaces/{ws}/projects/{project}", SETTINGS, ws, "secret").andExpect(status().isNotFound());
    }
  }

  @Test
  void 공개_범위를_바꾸면_다음_요청부터_반영된다() throws Exception {
    create(owner, ws, "toggle", "바뀌는 프로젝트", "WORKSPACE").andExpect(status().isCreated());
    api.get(member, "/workspaces/{ws}/projects/{project}", ws, "toggle").andExpect(status().isOk());

    api.patch(admin, "/workspaces/{ws}/projects/{project}", Map.of("name", "바뀌는 프로젝트", "visibility", "RESTRICTED"), ws,
        "toggle").andExpect(status().isOk());
    api.get(member, "/workspaces/{ws}/projects/{project}", ws, "toggle").andExpect(status().isNotFound());

    api.patch(admin, "/workspaces/{ws}/projects/{project}", Map.of("name", "바뀌는 프로젝트", "visibility", "WORKSPACE"), ws,
        "toggle").andExpect(status().isOk());
    api.get(member, "/workspaces/{ws}/projects/{project}", ws, "toggle").andExpect(status().isOk());
  }

  // ── 설정 ───────────────────────────────────────────────────────────

  @Test
  void 설정은_관리_권한이_있어야_고친다() throws Exception {
    create(owner, ws, "settings-project", "처음 이름", "WORKSPACE").andExpect(status().isCreated());
    for (TestUser reader : List.of(curator, member)) {
      api.patch(reader, "/workspaces/{ws}/projects/{project}", SETTINGS, ws, "settings-project")
          .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PERMISSION_REQUIRED"));
    }
    api.patch(admin, "/workspaces/{ws}/projects/{project}", SETTINGS, ws, "settings-project").andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("고친 이름")).andExpect(jsonPath("$.key").value("settings-project"));

    // 감사에 남는다
    List<String> actions = TestApi.read(api.get(owner, "/workspaces/{ws}/audit?limit=200", ws), "$[*].action");
    assertThat(actions).contains("project.create", "project.update");
  }

  // ── 보관 · 복원 · 삭제 ─────────────────────────────────────────────

  @Test
  void 보관하면_독자에게_사라지고_관리하는_사람만_본다() throws Exception {
    create(owner, ws, "old-project", "오래된 프로젝트", "WORKSPACE").andExpect(status().isCreated());
    api.post(curator, "/workspaces/{ws}/projects/{project}/archive", null, ws, "old-project").andExpect(status().isForbidden());

    api.post(admin, "/workspaces/{ws}/projects/{project}/archive", Map.of("reason", "더 쓰지 않음"), ws, "old-project")
        .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ARCHIVED"));
    api.post(admin, "/workspaces/{ws}/projects/{project}/archive", null, ws, "old-project").andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("PROJECT_ALREADY_ARCHIVED"));

    for (TestUser reader : List.of(curator, member)) {
      assertThat(keys(reader, false)).doesNotContain("old-project");
      assertThat(keys(reader, true)).doesNotContain("old-project");
      api.get(reader, "/workspaces/{ws}/projects/{project}", ws, "old-project").andExpect(status().isNotFound())
          .andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"));
    }
    assertThat(keys(admin, false)).doesNotContain("old-project");
    assertThat(keys(admin, true)).contains("old-project");
    api.get(admin, "/workspaces/{ws}/projects/{project}", ws, "old-project").andExpect(status().isOk());

    api.post(admin, "/workspaces/{ws}/projects/{project}/restore", null, ws, "old-project").andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACTIVE"));
    api.get(member, "/workspaces/{ws}/projects/{project}", ws, "old-project").andExpect(status().isOk());
    api.post(admin, "/workspaces/{ws}/projects/{project}/restore", null, ws, "old-project").andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("PROJECT_NOT_ARCHIVED"));
  }

  @Test
  void 삭제는_보관한_프로젝트만_주소_이름을_다시_넣어야_한다() throws Exception {
    create(owner, ws, "to-delete", "지울 프로젝트", "WORKSPACE").andExpect(status().isCreated());
    api.delete(admin, "/workspaces/{ws}/projects/{project}?confirm=to-delete", ws, "to-delete")
        .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PROJECT_NOT_ARCHIVED"));

    api.post(admin, "/workspaces/{ws}/projects/{project}/archive", null, ws, "to-delete").andExpect(status().isOk());
    api.delete(admin, "/workspaces/{ws}/projects/{project}?confirm=wrong", ws, "to-delete")
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CONFIRM_MISMATCH"));
    api.delete(curator, "/workspaces/{ws}/projects/{project}?confirm=to-delete", ws, "to-delete")
        .andExpect(status().isNotFound());

    api.delete(admin, "/workspaces/{ws}/projects/{project}?confirm=to-delete", ws, "to-delete")
        .andExpect(status().isNoContent());
    api.get(owner, "/workspaces/{ws}/projects/{project}", ws, "to-delete").andExpect(status().isNotFound());
    assertThat(keys(owner, true)).doesNotContain("to-delete");
    // 지운 프로젝트의 주소 이름은 다시 쓸 수 있다
    create(owner, ws, "to-delete", "새로 만든 프로젝트", "WORKSPACE").andExpect(status().isCreated());
  }

  // ── 개인 워크스페이스 ──────────────────────────────────────────────

  @Test
  void 개인_워크스페이스에는_내_프로젝트가_하나_만들어져_있다() throws Exception {
    TestUser solo = api.user("solo");
    String personal = api.personalWorkspace(solo);
    List<String> names = TestApi.read(api.get(solo, "/workspaces/{ws}/projects", personal).andExpect(status().isOk()),
        "$[*].name");
    assertThat(names).containsExactly("내 프로젝트");
    api.get(solo, "/workspaces/{ws}/projects/{project}", personal, "my").andExpect(status().isOk())
        .andExpect(jsonPath("$.visibility").value("WORKSPACE"));
    // 팀 워크스페이스는 비어 있다
    String team = api.teamWorkspace(solo, "빈 팀");
    List<String> none = TestApi.read(api.get(solo, "/workspaces/{ws}/projects", team), "$[*].key");
    assertThat(none).isEmpty();
  }
}
