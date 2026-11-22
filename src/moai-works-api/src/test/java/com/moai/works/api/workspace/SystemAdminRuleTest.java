package com.moai.works.api.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.moai.works.api.support.ApiTest;
import com.moai.works.api.support.TestApi;
import com.moai.works.api.support.TestUser;

/**
 * 시스템 관리자는 시스템만 관리한다 — 워크스페이스 영역과 나뉜다(2026-11-22 사용자 결정).
 * 개인 워크스페이스가 생기지 않고, 워크스페이스를 만들거나(허용 수를 받아도) 들어갈 수(가입 신청 · 초대 수락) 없다.
 */
@ApiTest
class SystemAdminRuleTest {

  @Autowired
  TestApi api;

  @Test
  void 시스템_관리자는_로그인해도_개인_워크스페이스가_생기지_않는다() throws Exception {
    TestUser sysadmin = api.user("sysadmin", "system-admin");
    api.get(sysadmin, "/me").andExpect(status().isOk())
        .andExpect(jsonPath("$.personalWorkspace").doesNotExist())
        .andExpect(jsonPath("$.landingWorkspace").doesNotExist())
        .andExpect(jsonPath("$.workspaces").isEmpty())
        .andExpect(jsonPath("$.platformRoles[0]").value("system-admin"));
    // 다시 로그인해도 그대로다
    api.post(sysadmin, "/me/login", null).andExpect(status().isOk()).andExpect(jsonPath("$.workspaces").isEmpty());
    // 시스템 관리 기능은 쓴다
    api.get(sysadmin, "/admin/accounts").andExpect(status().isOk());
  }

  @Test
  void 시스템_관리자는_허용_수를_받아도_팀_워크스페이스를_만들_수_없다() throws Exception {
    TestUser sysadmin = api.user("sysadmin", "system-admin");
    api.put(sysadmin, "/admin/accounts/{id}/team-workspace-limit", Map.of("limit", 3), sysadmin.id())
        .andExpect(status().isOk());
    api.post(sysadmin, "/workspaces", Map.of("name", "관리자 팀", "visibility", "PUBLIC", "joinPolicy", "AUTO"))
        .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("SYSTEM_ADMIN_NO_WORKSPACE"));
  }

  @Test
  void 시스템_관리자는_가입_신청도_초대_수락도_할_수_없다() throws Exception {
    TestUser owner = api.user("owner");
    TestUser sysadmin = api.user("sysadmin", "system-admin");
    api.allowTeamWorkspaces(owner, 2);
    String open = TestApi.read(api.post(owner, "/workspaces",
        Map.of("name", "자동 가입 팀", "visibility", "PUBLIC", "joinPolicy", "AUTO")).andExpect(status().isCreated()),
        "$.slug");

    api.post(sysadmin, "/workspaces/{ws}/join-requests", Map.of("message", "들어가고 싶습니다"), open)
        .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("SYSTEM_ADMIN_NO_WORKSPACE"));

    String url = TestApi.read(api.post(owner, "/workspaces/{ws}/invitations",
        Map.of("email", sysadmin.email(), "roleKey", "member"), open).andExpect(status().isCreated()), "$.inviteUrl");
    String token = url.substring(url.lastIndexOf('/') + 1);
    api.post(sysadmin, "/invitations/{token}/accept", null, token).andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("SYSTEM_ADMIN_NO_WORKSPACE"));

    // 멤버가 아니므로 워크스페이스는 404 그대로다
    api.get(sysadmin, "/workspaces/{ws}", open).andExpect(status().isNotFound());
    List<String> mine = TestApi.read(api.get(sysadmin, "/me"), "$.workspaces[*].slug");
    assertThat(mine).isEmpty();
  }
}
