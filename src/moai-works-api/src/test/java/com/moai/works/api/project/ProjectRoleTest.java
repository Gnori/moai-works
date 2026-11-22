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
import com.moai.works.api.workspace.WorkspaceRepositories;

/**
 * 프로젝트 역할 — 주고 빼기, 지정 멤버 프로젝트의 독자, 권한 합치기, 권한 상승 방지, Keycloak 그룹(정본)과 사본 (design/04).
 */
@ApiTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProjectRoleTest {

  @Autowired
  TestApi api;

  @Autowired
  WorkspaceRepositories.Workspaces workspaces;

  TestUser owner;
  TestUser admin;
  TestUser curator;
  TestUser member;
  String ws;

  @BeforeAll
  void setUp() throws Exception {
    owner = api.user("owner");
    admin = api.user("admin");
    curator = api.user("curator");
    member = api.user("member");
    ws = api.teamWorkspace(owner, "프로젝트 역할 시험");
    api.join(ws, owner, admin, "admin");
    api.join(ws, owner, curator, "curator");
    api.join(ws, owner, member, "member");
  }

  private void project(String key, String visibility) throws Exception {
    api.post(owner, "/workspaces/{ws}/projects", Map.of("key", key, "name", key, "visibility", visibility), ws)
        .andExpect(status().isCreated());
  }

  private ResultActions setRoles(TestUser by, String project, TestUser target, String... roles) throws Exception {
    return api.put(by, "/workspaces/{ws}/projects/{project}/roles/{user}", Map.of("roles", List.of(roles)), ws,
        project, target.id());
  }

  private List<String> permissions(TestUser as, String project) throws Exception {
    return TestApi.read(api.get(as, "/workspaces/{ws}/projects/{project}", ws, project).andExpect(status().isOk()),
        "$.myPermissions");
  }

  @Test
  void 지정_멤버_프로젝트는_프로젝트_역할을_받으면_독자가_된다() throws Exception {
    project("closed", "RESTRICTED");
    api.get(member, "/workspaces/{ws}/projects/{project}", ws, "closed").andExpect(status().isNotFound());

    setRoles(admin, "closed", member, "project-reader").andExpect(status().isOk())
        .andExpect(jsonPath("$.roles[0]").value("project-reader"));
    api.get(member, "/workspaces/{ws}/projects/{project}", ws, "closed").andExpect(status().isOk())
        .andExpect(jsonPath("$.myRoles[0]").value("project-reader"));
    List<String> keys = TestApi.read(api.get(member, "/workspaces/{ws}/projects", ws), "$[*].key");
    assertThat(keys).contains("closed");

    // 빼면 다시 없는 것과 같다
    setRoles(admin, "closed", member).andExpect(status().isOk());
    api.get(member, "/workspaces/{ws}/projects/{project}", ws, "closed").andExpect(status().isNotFound());
  }

  @Test
  void 프로젝트_권한은_워크스페이스_역할과_프로젝트_역할의_합이고_그_프로젝트에서만_통한다() throws Exception {
    project("alpha", "WORKSPACE");
    project("beta", "WORKSPACE");
    assertThat(permissions(member, "alpha")).doesNotContain("issue.create");

    setRoles(owner, "alpha", member, "project-editor").andExpect(status().isOk());
    assertThat(permissions(member, "alpha")).contains("issue.read", "issue.comment", "issue.create", "issue.edit")
        .doesNotContain("issue.delete", "project.manage");
    assertThat(permissions(member, "beta")).doesNotContain("issue.create");

    // 역할을 여러 개 가질 수 있고 권한은 합친다
    setRoles(owner, "alpha", member, "project-reader", "project-curator").andExpect(status().isOk());
    assertThat(permissions(member, "alpha")).contains("issue.create", "issue.manager.assign");
    List<String> roles = TestApi.read(api.get(member, "/workspaces/{ws}/projects/{project}", ws, "alpha"),
        "$.myRoles");
    assertThat(roles).containsExactly("project-curator", "project-reader");
  }

  @Test
  void 프로젝트_관리자는_그_프로젝트의_설정과_역할을_다루지만_보관은_못_한다() throws Exception {
    project("gamma", "WORKSPACE");
    setRoles(owner, "gamma", member, "project-admin").andExpect(status().isOk());

    api.patch(member, "/workspaces/{ws}/projects/{project}", Map.of("name", "감마", "visibility", "WORKSPACE"), ws,
        "gamma").andExpect(status().isOk());
    setRoles(member, "gamma", curator, "project-editor").andExpect(status().isOk());
    api.post(member, "/workspaces/{ws}/projects/{project}/archive", null, ws, "gamma")
        .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PERMISSION_REQUIRED"));
  }

  @Test
  void 관리_권한이_없으면_역할을_못_준다_권한_밖의_역할도_못_준다() throws Exception {
    project("delta", "WORKSPACE");
    setRoles(curator, "delta", member, "project-reader").andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("PERMISSION_REQUIRED"));

    // project.manage 는 있지만 일감을 만들 수 없는 사람 — 독자 역할은 주지만 편집자 역할은 못 준다
    TestUser lead = api.user("lead");
    api.join(ws, owner, lead, "member");
    String role = api.customRole(ws, owner, "프로젝트 정리", Set.of("workspace.read", "project.manage", "issue.read",
        "issue.comment"));
    api.setRoles(ws, owner, lead, role);
    setRoles(lead, "delta", member, "project-reader").andExpect(status().isOk());
    setRoles(lead, "delta", member, "project-reader", "project-editor").andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ROLE_ESCALATION"));
  }

  @Test
  void 없는_역할과_멤버가_아닌_사람은_거절한다() throws Exception {
    project("epsilon", "WORKSPACE");
    setRoles(owner, "epsilon", member, "project-boss").andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("PROJECT_ROLE_NOT_FOUND"));
    TestUser stranger = api.user("stranger");
    setRoles(owner, "epsilon", stranger, "project-reader").andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
  }

  @Test
  void 역할을_바꾸면_Keycloak_그룹과_사본이_같이_바뀌고_감사에_남는다() throws Exception {
    project("zeta", "RESTRICTED");
    TestUser person = api.user("synced");
    api.join(ws, owner, person, "member");
    String org = workspaces.findBySlug(ws).orElseThrow().getKeycloakOrgId();

    setRoles(owner, "zeta", person, "project-editor").andExpect(status().isOk());
    assertThat(api.keycloak().groupNamesOf(org, person.id().toString())).contains("member", "project-editor");
    List<String> holders = TestApi.read(api.get(owner, "/workspaces/{ws}/projects/{project}/roles", ws, "zeta"),
        "$[*].userId");
    assertThat(holders).contains(person.id().toString());

    setRoles(owner, "zeta", person).andExpect(status().isOk());
    assertThat(api.keycloak().groupNamesOf(org, person.id().toString())).doesNotContain("project-editor");

    List<String> actions = TestApi.read(api.get(owner, "/workspaces/{ws}/audit?limit=200", ws), "$[*].action");
    assertThat(actions).contains("project.roles.update");
  }

  @Test
  void 워크스페이스를_나가면_프로젝트_역할도_사라진다() throws Exception {
    project("eta", "RESTRICTED");
    TestUser leaver = api.user("leaver");
    api.join(ws, owner, leaver, "member");
    setRoles(owner, "eta", leaver, "project-reader").andExpect(status().isOk());

    api.delete(owner, "/workspaces/{ws}/members/{user}", ws, leaver.id()).andExpect(status().isNoContent());
    List<String> holders = TestApi.read(api.get(owner, "/workspaces/{ws}/projects/{project}/roles", ws, "eta"),
        "$[*].userId");
    assertThat(holders).doesNotContain(leaver.id().toString());
  }
}
