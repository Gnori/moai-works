package com.moai.works.api.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;

import com.moai.works.api.support.ApiTest;
import com.moai.works.api.support.TestApi;
import com.moai.works.api.support.TestUser;

/**
 * 멤버십 규칙 — 권한 상승 방지 · 소유자 보호 · 마지막 소유자 보호, 그리고 Keycloak(정본)과 DB 사본이 같은가.
 * (SECURITY.md §6.5 · §6.6)
 */
@ApiTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MembershipRulesTest {

  @Autowired
  TestApi api;

  @Autowired
  WorkspaceRepositories.Workspaces workspaces;

  @Autowired
  WorkspaceRepositories.MemberRoles memberRoles;

  TestUser owner;
  TestUser admin;
  TestUser member;
  String ws;

  @BeforeAll
  void setUp() throws Exception {
    owner = api.user("owner");
    admin = api.user("admin");
    member = api.user("member");
    ws = api.teamWorkspace(owner, "멤버십 규칙 시험");
    api.join(ws, owner, admin, "admin");
    api.join(ws, owner, member, "member");
  }

  private org.springframework.test.web.servlet.ResultActions setRoles(TestUser by, TestUser target, String... roles)
      throws Exception {
    return api.put(by, "/workspaces/{ws}/members/{u}/roles", Map.of("roles", List.of(roles)), ws, target.id());
  }

  // ── 권한 상승 방지 ─────────────────────────────────────────────────

  @Test
  void 관리자는_소유자_역할을_줄_수_없다() throws Exception {
    setRoles(admin, member, "member", "owner").andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("OWNER_ONLY"));
    setRoles(admin, admin, "admin", "owner").andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("OWNER_ONLY"));
  }

  @Test
  void 관리자는_소유자를_바꾸거나_내보낼_수_없다() throws Exception {
    setRoles(admin, owner, "member").andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("OWNER_PROTECTED"));
    api.delete(admin, "/workspaces/{ws}/members/{u}", ws, owner.id()).andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("OWNER_PROTECTED"));
  }

  @Test
  void 자기에게_없는_권한이_든_역할은_줄_수_없다() throws Exception {
    // 역할을 줄 수는 있지만(member.role.assign) 가진 권한이 적은 사람
    TestUser helper = api.user("helper");
    api.join(ws, owner, helper, "member");
    String assigner = api.customRole(ws, owner, "역할 부여 도우미",
        Set.of("workspace.read", "member.read", "member.role.assign"));
    api.setRoles(ws, owner, helper, "member", assigner);

    TestUser target = api.user("target");
    api.join(ws, owner, target, "member");
    setRoles(helper, target, "admin").andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ESCALATION_DENIED"));
    // 자기 자신에게도 못 준다
    setRoles(helper, helper, "member", assigner, "admin").andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ESCALATION_DENIED"));
    // 자기가 가진 권한 안의 역할은 줄 수 있다
    setRoles(helper, target, "member", assigner).andExpect(status().isOk());
  }

  // ── 마지막 소유자 ──────────────────────────────────────────────────

  @Test
  void 마지막_소유자는_역할을_빼거나_나갈_수_없다() throws Exception {
    TestUser solo = api.user("solo-owner");
    String team = api.teamWorkspace(solo, "소유자 한 명");
    api.put(solo, "/workspaces/{ws}/members/{u}/roles", Map.of("roles", List.of("admin")), team, solo.id())
        .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LAST_OWNER"));
    api.post(solo, "/workspaces/{ws}/leave", null, team).andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("LAST_OWNER"));
  }

  @Test
  void 소유자가_둘이면_하나는_내려올_수_있다() throws Exception {
    TestUser first = api.user("owner-1");
    TestUser second = api.user("owner-2");
    String team = api.teamWorkspace(first, "소유자 두 명");
    api.join(team, first, second, "member");
    api.setRoles(team, first, second, "owner");
    api.setRoles(team, second, first, "admin");
    // 이제 second 가 마지막 소유자다
    api.post(second, "/workspaces/{ws}/leave", null, team).andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("LAST_OWNER"));
    api.post(first, "/workspaces/{ws}/leave", null, team).andExpect(status().isNoContent());
  }

  // ── 그 밖의 규칙 ───────────────────────────────────────────────────

  @Test
  void 자기_자신은_내보내기가_아니라_나가기로() throws Exception {
    api.delete(owner, "/workspaces/{ws}/members/{u}", ws, owner.id()).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("USE_LEAVE"));
  }

  @Test
  void 개인_워크스페이스는_나갈_수_없다() throws Exception {
    api.post(owner, "/workspaces/{ws}/leave", null, api.personalWorkspace(owner)).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("PERSONAL_WORKSPACE"));
  }

  @Test
  void 역할은_하나_이상_있어야_하고_있는_역할이어야_한다() throws Exception {
    setRoles(owner, member).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    setRoles(owner, member, "no-such-role").andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("ROLE_NOT_FOUND"));
  }

  @Test
  void 멤버가_아닌_사람의_역할은_바꿀_수_없다() throws Exception {
    api.put(owner, "/workspaces/{ws}/members/{u}/roles", Map.of("roles", List.of("member")), ws, UUID.randomUUID())
        .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
    api.delete(owner, "/workspaces/{ws}/members/{u}", ws, UUID.randomUUID()).andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("MEMBER_NOT_FOUND"));
  }

  @Test
  void 멤버는_나갈_수_있고_나가면_404() throws Exception {
    TestUser guest = api.user("guest");
    api.join(ws, owner, guest, "member");
    api.post(guest, "/workspaces/{ws}/leave", null, ws).andExpect(status().isNoContent());
    api.get(guest, "/workspaces/{ws}", ws).andExpect(status().isNotFound());
  }

  // ── Keycloak(정본)과 사본 ──────────────────────────────────────────

  @Test
  void 역할을_바꾸면_Keycloak_그룹과_DB_사본이_같이_바뀐다() throws Exception {
    TestUser person = api.user("synced");
    api.join(ws, owner, person, "member");
    Workspace workspace = workspaces.findBySlug(ws).orElseThrow();
    String org = workspace.getKeycloakOrgId();

    assertThat(api.keycloak().isOrganizationMember(org, person.id().toString())).isTrue();
    assertThat(api.keycloak().groupNamesOf(org, person.id().toString())).containsExactly("member");

    api.setRoles(ws, owner, person, "admin", "member");
    Set<String> copy = memberRoles.find(workspace.getId(), person.id()).stream().map(MemberRole::getRoleKey)
        .collect(Collectors.toSet());
    assertThat(copy).containsExactlyInAnyOrder("admin", "member");
    assertThat(api.keycloak().groupNamesOf(org, person.id().toString())).containsExactlyInAnyOrder("admin", "member");

    api.delete(owner, "/workspaces/{ws}/members/{u}", ws, person.id()).andExpect(status().isNoContent());
    assertThat(api.keycloak().isOrganizationMember(org, person.id().toString())).isFalse();
    assertThat(api.keycloak().groupNamesOf(org, person.id().toString())).isEmpty();
    assertThat(memberRoles.find(workspace.getId(), person.id())).isEmpty();
  }

  @Test
  void 만든_사람은_소유자이고_Keycloak_owner_그룹에_들어_있다() throws Exception {
    Workspace workspace = workspaces.findBySlug(ws).orElseThrow();
    assertThat(api.keycloak().groupNamesOf(workspace.getKeycloakOrgId(), owner.id().toString()))
        .containsExactly("owner");
    List<String> roles = TestApi.read(api.get(owner, "/workspaces/{ws}", ws), "$.myRoles");
    assertThat(roles).containsExactly("owner");
  }
}
