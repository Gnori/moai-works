package com.moai.works.api.workspace;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.moai.works.api.config.EndPointPaths;
import com.moai.works.api.support.ApiTest;
import com.moai.works.api.support.TestApi;
import com.moai.works.api.support.TestUser;

/**
 * 시스템 관리자 계정은 시스템만 관리한다 (2026-11-22 사용자 결정).
 *
 * <ul>
 *   <li>개인 워크스페이스가 생기지 않는다. 팀 워크스페이스도 만들 수 없다(허용 수를 줄 수도 없다)</li>
 *   <li>워크스페이스에 들어가지 못한다 — 찾기 · 가입 신청 · 초대 수락이 모두 막힌다</li>
 *   <li>예전에 생긴 멤버십이 남아 있어도 워크스페이스 영역은 열리지 않는다(멤버가 아닌 사람과 같은 404)</li>
 * </ul>
 */
@ApiTest
class SystemAdminRuleTest {

  private static final String DENIED = "SYSTEM_ADMIN_NOT_ALLOWED";

  @Autowired
  TestApi api;
  @Autowired
  MockMvc mvc;

  /** 같은 계정에 시스템 관리자 역할을 준 뒤의 토큰 */
  private static TestUser promoted(TestUser user) {
    return new TestUser(user.id(), user.username(), user.email(), user.displayName(), List.of("system-admin"));
  }

  @Test
  void 로그인해도_개인_워크스페이스가_생기지_않는다() throws Exception {
    TestUser sysadmin = api.user("sysadmin", "system-admin");

    api.get(sysadmin, "/me").andExpect(status().isOk())
        .andExpect(jsonPath("$.platformRoles[0]").value("system-admin"))
        .andExpect(jsonPath("$.personalWorkspace").isEmpty())
        .andExpect(jsonPath("$.landingWorkspace").isEmpty())
        .andExpect(jsonPath("$.workspaces.length()").value(0));
    // 다시 로그인해도 같다
    api.post(sysadmin, "/me/login", null).andExpect(status().isOk())
        .andExpect(jsonPath("$.personalWorkspace").isEmpty());
    // 이름 바꾸기처럼 자기 계정을 다루는 일은 된다
    api.patch(sysadmin, "/me", Map.of("displayName", "시스템 관리자")).andExpect(status().isOk())
        .andExpect(jsonPath("$.displayName").value("시스템 관리자"));
    // 시스템 관리 화면의 입구는 열린다
    api.get(sysadmin, "/admin/accounts").andExpect(status().isOk());
  }

  @Test
  void 팀_워크스페이스를_만들_수_없고_허용_수도_줄_수_없다() throws Exception {
    TestUser sysadmin = api.user("sysadmin", "system-admin");
    TestUser other = api.user("other-sysadmin", "system-admin");

    api.post(sysadmin, "/workspaces", Map.of("name", "못 만드는 팀", "visibility", "PUBLIC", "joinPolicy", "APPROVAL"))
        .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(DENIED));
    // 다른 시스템 관리자가 허용 수를 주려 해도 안 된다 — 자기 자신에게도
    api.put(other, "/admin/accounts/{id}/team-workspace-limit", Map.of("limit", 3), sysadmin.id())
        .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SYSTEM_ADMIN_ACCOUNT"));
    api.put(sysadmin, "/admin/accounts/{id}/team-workspace-limit", Map.of("limit", 3), sysadmin.id())
        .andExpect(status().isConflict());
    api.get(other, "/admin/accounts").andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.id == '" + sysadmin.id() + "')].teamWorkspaceLimit").value(0));
  }

  @Test
  void 워크스페이스에_들어가지_못한다() throws Exception {
    TestUser owner = api.user("owner");
    TestUser sysadmin = api.user("sysadmin", "system-admin");
    api.allowTeamWorkspaces(owner, 2);
    String open = TestApi.read(api.post(owner, "/workspaces",
        Map.of("name", "누구나 들어오는 팀", "visibility", "PUBLIC", "joinPolicy", "AUTO"))
        .andExpect(status().isCreated()), "$.slug");

    // 찾기 · 가입 신청 · 내 워크스페이스 · 내 신청 · 내 초대 — 워크스페이스 쪽 입구는 모두 막힌다
    api.get(sysadmin, "/workspaces").andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(DENIED));
    api.post(sysadmin, "/workspaces/{ws}/join-requests", Map.of("message", "들어가고 싶다"), open)
        .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(DENIED));
    api.get(sysadmin, "/me/workspaces").andExpect(status().isForbidden());
    api.get(sysadmin, "/me/join-requests").andExpect(status().isForbidden());
    api.get(sysadmin, "/me/invitations").andExpect(status().isForbidden());
    api.put(sysadmin, "/me/last-workspace", Map.of("slug", open)).andExpect(status().isForbidden());

    // 초대를 받아도 수락할 수 없다
    String inviteUrl = TestApi.read(api.post(owner, "/workspaces/{ws}/invitations",
        Map.of("email", sysadmin.email(), "roleKey", "member"), open).andExpect(status().isCreated()), "$.inviteUrl");
    String token = inviteUrl.substring(inviteUrl.lastIndexOf('/') + 1);
    api.post(sysadmin, "/invitations/{token}/accept", null, token)
        .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(DENIED));

    // 멤버가 되지 않았다
    api.get(owner, "/workspaces/{ws}/members", open).andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1));
    api.get(sysadmin, "/workspaces/{ws}", open).andExpect(status().isNotFound());
  }

  @Test
  void 예전_멤버십이_남아_있어도_워크스페이스_영역은_열리지_않는다() throws Exception {
    // 보통 계정으로 워크스페이스 · 프로젝트를 쓰던 사람에게 시스템 관리자 역할을 준 경우
    TestUser person = api.user("person");
    String personal = api.personalWorkspace(person);
    String team = api.teamWorkspace(person, "쓰던 팀");
    api.post(person, "/workspaces/{ws}/projects", Map.of("key", "notes", "name", "노트", "visibility", "WORKSPACE"), team)
        .andExpect(status().isCreated());
    api.get(person, "/workspaces/{ws}", team).andExpect(status().isOk());

    TestUser asAdmin = promoted(person);
    api.post(asAdmin, "/me/login", null).andExpect(status().isOk())
        .andExpect(jsonPath("$.workspaces.length()").value(0))
        .andExpect(jsonPath("$.personalWorkspace").isEmpty())
        .andExpect(jsonPath("$.landingWorkspace").isEmpty())
        .andExpect(jsonPath("$.teamWorkspaceLimit").value(0));
    // 소유자였던 워크스페이스도 멤버가 아닌 사람과 같은 404 — 내용은 그대로 남는다
    api.get(asAdmin, "/workspaces/{ws}", team).andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("WORKSPACE_NOT_FOUND"));
    api.get(asAdmin, "/workspaces/{ws}", personal).andExpect(status().isNotFound());
    api.get(asAdmin, "/workspaces/{ws}/projects/{project}", team, "notes").andExpect(status().isNotFound());
    api.get(asAdmin, "/workspaces/{ws}/projects/{project}/issues", team, "notes").andExpect(status().isNotFound());
    api.post(asAdmin, "/workspaces", Map.of("name", "또 하나", "visibility", "PUBLIC", "joinPolicy", "AUTO"))
        .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(DENIED));

    // 계정 관리 목록이 남은 멤버십 수를 알린다(개인 + 팀) — 시스템 관리자가 보고 정리한다. 보통 계정은 0
    TestUser sysadmin = api.user("sysadmin", "system-admin");
    api.get(sysadmin, "/admin/accounts").andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.id == '" + person.id() + "')].leftoverMemberships").value(2))
        .andExpect(jsonPath("$[?(@.id == '" + sysadmin.id() + "')].leftoverMemberships").value(0));

    // 역할을 거두면 다시 쓴다 — 지운 것이 없다
    api.get(person, "/workspaces/{ws}/projects/{project}", team, "notes").andExpect(status().isOk());
    api.get(person, "/me").andExpect(jsonPath("$.workspaces.length()").value(2));
  }

  @Test
  void MCP_키를_발급할_수_없다() throws Exception {
    TestUser sysadmin = api.user("sysadmin", "system-admin");
    // 키는 개인 설정(/me/mcp-keys)에서 받는다 — 시스템 관리자 계정은 받기 · 목록 · 만료시키기 모두 막힌다(ADR-023 · ADR-029)
    api.post(sysadmin, "/me/mcp-keys", java.util.Map.of("name", "시험")).andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value(DENIED));
    api.get(sysadmin, "/me/mcp-keys").andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(DENIED));
    api.post(sysadmin, "/me/mcp-keys/{id}/expire", java.util.Map.of(), java.util.UUID.randomUUID())
        .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(DENIED));
    // 남의 워크스페이스 키 목록도 멤버가 아니라 404
    TestUser person = api.user("person");
    api.get(sysadmin, "/workspaces/{ws}/mcp-keys", api.personalWorkspace(person)).andExpect(status().isNotFound());
  }
}
