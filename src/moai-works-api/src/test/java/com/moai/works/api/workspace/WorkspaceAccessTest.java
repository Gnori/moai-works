package com.moai.works.api.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.ResultActions;

import com.moai.works.api.support.ApiTest;
import com.moai.works.api.support.TestApi;
import com.moai.works.api.support.TestUser;

/**
 * 워크스페이스 접근 판정 — 멤버가 아니면 404(존재를 숨김), 멤버인데 권한이 없으면 403. (SECURITY.md §6.1 · §6.3)
 */
@ApiTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WorkspaceAccessTest {

  @Autowired
  TestApi api;

  TestUser owner;
  TestUser admin;
  TestUser member;
  TestUser outsider;
  TestUser sysadmin;
  String ws;

  @BeforeAll
  void setUp() throws Exception {
    owner = api.user("owner");
    admin = api.user("admin");
    member = api.user("member");
    outsider = api.user("outsider");
    sysadmin = api.user("sysadmin", "system-admin");
    ws = api.teamWorkspace(owner, "접근 판정 시험");
    api.join(ws, owner, admin, "admin");
    api.join(ws, owner, member, "member");
  }

  /** 워크스페이스 안의 모든 경로 (방법, 경로, 본문) */
  private List<Object[]> workspacePaths() {
    Map<String, Object> settings = Map.of("name", "이름", "visibility", "PUBLIC", "joinPolicy", "APPROVAL",
        "allowedJoinDomains", List.of(), "defaultRoleKey", "member");
    Map<String, Object> role = Map.of("name", "역할", "permissions", List.of("workspace.read"));
    UUID someone = UUID.randomUUID();
    return List.of(
        new Object[] {HttpMethod.GET, "/workspaces/" + ws, null},
        new Object[] {HttpMethod.PATCH, "/workspaces/" + ws, settings},
        new Object[] {HttpMethod.GET, "/workspaces/" + ws + "/roles", null},
        new Object[] {HttpMethod.POST, "/workspaces/" + ws + "/roles", role},
        new Object[] {HttpMethod.PATCH, "/workspaces/" + ws + "/roles/custom-x", role},
        new Object[] {HttpMethod.DELETE, "/workspaces/" + ws + "/roles/custom-x", null},
        new Object[] {HttpMethod.GET, "/workspaces/" + ws + "/members", null},
        new Object[] {HttpMethod.PUT, "/workspaces/" + ws + "/members/" + someone + "/roles",
            Map.of("roles", List.of("member"))},
        new Object[] {HttpMethod.DELETE, "/workspaces/" + ws + "/members/" + someone, null},
        new Object[] {HttpMethod.POST, "/workspaces/" + ws + "/leave", null},
        new Object[] {HttpMethod.POST, "/workspaces/" + ws + "/invitations",
            Map.of("email", "x@test.moai-works.local", "roleKey", "member")},
        new Object[] {HttpMethod.GET, "/workspaces/" + ws + "/invitations", null},
        new Object[] {HttpMethod.DELETE, "/workspaces/" + ws + "/invitations/" + someone, null},
        new Object[] {HttpMethod.GET, "/workspaces/" + ws + "/join-requests", null},
        new Object[] {HttpMethod.POST, "/workspaces/" + ws + "/join-requests/" + someone + "/approve", null},
        new Object[] {HttpMethod.POST, "/workspaces/" + ws + "/join-requests/" + someone + "/reject", null},
        new Object[] {HttpMethod.GET, "/workspaces/" + ws + "/audit", null});
  }

  private ResultActions call(TestUser as, Object[] path) throws Exception {
    return api.call(as, (HttpMethod) path[0], (String) path[1], path[2]);
  }

  @Test
  void 멤버가_아니면_모든_경로가_404() throws Exception {
    for (Object[] path : workspacePaths()) {
      call(outsider, path).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("WORKSPACE_NOT_FOUND"));
    }
  }

  @Test
  void 없는_워크스페이스와_남의_워크스페이스는_구별되지_않는다() throws Exception {
    ResultActions existing = api.get(outsider, "/workspaces/{ws}", ws);
    ResultActions missing = api.get(outsider, "/workspaces/{ws}", "workspace-zzzzzzzzzzzz");
    // 요청한 주소(instance)만 다르고 상태 · 코드 · 설명이 모두 같다
    for (String field : List.of("$.status", "$.code", "$.title", "$.detail")) {
      assertThat(TestApi.<Object>read(existing, field)).isEqualTo(TestApi.<Object>read(missing, field));
    }
    assertThat(existing.andReturn().getResponse().getStatus()).isEqualTo(404);
    assertThat(missing.andReturn().getResponse().getStatus()).isEqualTo(404);
  }

  @Test
  void 시스템_관리자도_멤버가_아니면_404() throws Exception {
    api.get(sysadmin, "/workspaces/{ws}", ws).andExpect(status().isNotFound());
    api.get(sysadmin, "/workspaces/{ws}/members", ws).andExpect(status().isNotFound());
    api.get(sysadmin, "/workspaces/{ws}/audit", ws).andExpect(status().isNotFound());
  }

  @Test
  void 남의_개인_워크스페이스는_404() throws Exception {
    String personal = api.personalWorkspace(owner);
    api.get(owner, "/workspaces/{ws}", personal).andExpect(status().isOk());
    api.get(admin, "/workspaces/{ws}", personal).andExpect(status().isNotFound());
  }

  @Test
  void 멤버는_읽기만_되고_관리_경로는_403() throws Exception {
    api.get(member, "/workspaces/{ws}", ws).andExpect(status().isOk());
    api.get(member, "/workspaces/{ws}/roles", ws).andExpect(status().isOk());
    api.get(member, "/workspaces/{ws}/members", ws).andExpect(status().isOk());
    List<String> managed = List.of("PATCH /workspaces/" + ws, "POST /workspaces/" + ws + "/roles",
        "POST /workspaces/" + ws + "/invitations", "GET /workspaces/" + ws + "/invitations",
        "GET /workspaces/" + ws + "/join-requests", "GET /workspaces/" + ws + "/audit");
    for (Object[] path : workspacePaths()) {
      if (managed.contains(path[0] + " " + path[1])) {
        call(member, path).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PERMISSION_REQUIRED"));
      }
    }
    api.put(member, "/workspaces/{ws}/members/{u}/roles", Map.of("roles", List.of("admin")), ws, member.id())
        .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PERMISSION_REQUIRED"));
    api.delete(member, "/workspaces/{ws}/members/{u}", ws, admin.id())
        .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PERMISSION_REQUIRED"));
  }

  @Test
  void 관리자는_관리_경로를_쓴다() throws Exception {
    api.get(admin, "/workspaces/{ws}/audit", ws).andExpect(status().isOk());
    api.get(admin, "/workspaces/{ws}/invitations", ws).andExpect(status().isOk());
    api.get(admin, "/workspaces/{ws}/join-requests", ws).andExpect(status().isOk());
  }

  @Test
  void 내_권한_목록은_역할의_권한_합집합이다() throws Exception {
    List<String> ownerPermissions = TestApi.read(api.get(owner, "/workspaces/{ws}", ws), "$.myPermissions");
    assertThat(ownerPermissions).hasSize(Permission.values().length);

    List<String> adminPermissions = TestApi.read(api.get(admin, "/workspaces/{ws}", ws), "$.myPermissions");
    assertThat(adminPermissions).doesNotContain("workspace.ownership.transfer", "workspace.delete")
        .hasSize(Permission.values().length - 2);

    List<String> memberPermissions = TestApi.read(api.get(member, "/workspaces/{ws}", ws), "$.myPermissions");
    assertThat(memberPermissions).containsExactlyInAnyOrder("workspace.read", "member.read", "mcp.key.issue.personal");
  }

  @Test
  void 역할이_바뀌면_다음_요청부터_반영된다() throws Exception {
    TestUser mover = api.user("mover");
    api.join(ws, owner, mover, "member");
    api.get(mover, "/workspaces/{ws}/audit", ws).andExpect(status().isForbidden());
    api.setRoles(ws, owner, mover, "admin");
    api.get(mover, "/workspaces/{ws}/audit", ws).andExpect(status().isOk());
    api.setRoles(ws, owner, mover, "member");
    api.get(mover, "/workspaces/{ws}/audit", ws).andExpect(status().isForbidden());
  }

  @Test
  void 내보낸_멤버는_바로_404() throws Exception {
    TestUser leaver = api.user("leaver");
    api.join(ws, owner, leaver, "member");
    api.get(leaver, "/workspaces/{ws}", ws).andExpect(status().isOk());
    api.delete(owner, "/workspaces/{ws}/members/{u}", ws, leaver.id()).andExpect(status().isNoContent());
    api.get(leaver, "/workspaces/{ws}", ws).andExpect(status().isNotFound());
  }

  @Test
  void 플랫폼_역할이_없으면_관리자_api_가_403() throws Exception {
    api.get(owner, "/admin/accounts").andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("PLATFORM_ROLE_REQUIRED"));
    api.get(sysadmin, "/admin/accounts").andExpect(status().isOk());
  }

  @Test
  void 정지된_계정은_토큰이_살아_있어도_다음_요청부터_403() throws Exception {
    TestUser target = api.user("suspend-target");
    api.join(ws, owner, target, "member");
    api.get(target, "/workspaces/{ws}", ws).andExpect(status().isOk());

    api.post(sysadmin, "/admin/accounts/{id}/suspend", null, target.id()).andExpect(status().isOk());
    api.get(target, "/workspaces/{ws}", ws).andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_ACTIVE"));
    api.get(target, "/me").andExpect(status().isForbidden());
    assertThat(api.keycloak().userEnabled(target.id().toString())).isFalse();

    api.post(sysadmin, "/admin/accounts/{id}/resume", null, target.id()).andExpect(status().isOk());
    api.get(target, "/workspaces/{ws}", ws).andExpect(status().isOk());
  }

  @Test
  void 시스템_관리자는_자기_계정을_정지할_수_없다() throws Exception {
    api.post(sysadmin, "/admin/accounts/{id}/suspend", null, sysadmin.id()).andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("SELF_SUSPEND"));
  }
}
