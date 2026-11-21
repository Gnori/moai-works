package com.moai.works.api.workspace;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

import com.moai.works.api.support.ApiTest;
import com.moai.works.api.support.TestApi;
import com.moai.works.api.support.TestUser;

/**
 * 팀 워크스페이스 규칙 (2026-11-21 사용자 결정).
 *
 * <ul>
 *   <li>가입한 계정은 팀 워크스페이스를 만들 수 없다. 시스템 관리자가 허용한 계정만, 허용한 수까지 만든다</li>
 *   <li>퍼블릭만 찾기에 보이고 가입할 수 있다. 가입 방식은 자동 가입 · 관리자 승인 후 가입</li>
 *   <li>프라이빗은 다른 사람에게 보이지 않는다</li>
 * </ul>
 */
@ApiTest
class TeamWorkspaceRuleTest {

  @Autowired
  TestApi api;

  private ResultActions create(TestUser by, String name, String visibility, String joinPolicy) throws Exception {
    return api.post(by, "/workspaces", Map.of("name", name, "visibility", visibility, "joinPolicy", joinPolicy));
  }

  @Test
  void 가입한_계정은_팀_워크스페이스를_만들_수_없다() throws Exception {
    TestUser fresh = api.user("fresh");
    api.get(fresh, "/me").andExpect(status().isOk())
        .andExpect(jsonPath("$.teamWorkspaceLimit").value(0))
        .andExpect(jsonPath("$.teamWorkspacesCreated").value(0))
        .andExpect(jsonPath("$.personalWorkspace").isNotEmpty());
    create(fresh, "못 만드는 팀", "PUBLIC", "APPROVAL").andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("TEAM_WORKSPACE_NOT_ALLOWED"));
  }

  @Test
  void 시스템_관리자가_허용한_수까지만_만든다() throws Exception {
    TestUser maker = api.user("maker");
    api.allowTeamWorkspaces(maker, 2);
    api.get(maker, "/me").andExpect(jsonPath("$.teamWorkspaceLimit").value(2));

    create(maker, "첫째", "PUBLIC", "APPROVAL").andExpect(status().isCreated());
    create(maker, "둘째", "PRIVATE", "APPROVAL").andExpect(status().isCreated());
    api.get(maker, "/me").andExpect(jsonPath("$.teamWorkspacesCreated").value(2));
    create(maker, "셋째", "PUBLIC", "AUTO").andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("TEAM_WORKSPACE_LIMIT"));

    // 시스템 관리자가 올려 주면 더 만든다
    api.allowTeamWorkspaces(maker, 3);
    create(maker, "셋째", "PUBLIC", "AUTO").andExpect(status().isCreated());

    // 0 으로 되돌려도 있는 워크스페이스는 그대로이고, 더 만들지 못할 뿐이다
    api.allowTeamWorkspaces(maker, 0);
    api.get(maker, "/me").andExpect(jsonPath("$.teamWorkspacesCreated").value(3))
        .andExpect(jsonPath("$.workspaces.length()").value(4));
    create(maker, "넷째", "PUBLIC", "AUTO").andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("TEAM_WORKSPACE_NOT_ALLOWED"));
  }

  @Test
  void 한도는_시스템_관리자만_정한다() throws Exception {
    TestUser someone = api.user("someone");
    TestUser sysadmin = api.user("sysadmin", "system-admin");

    // 자기 한도를 스스로 올릴 수 없다
    api.put(someone, "/admin/accounts/{id}/team-workspace-limit", Map.of("limit", 5), someone.id())
        .andExpect(status().isForbidden());
    api.put(sysadmin, "/admin/accounts/{id}/team-workspace-limit", Map.of("limit", 101), someone.id())
        .andExpect(status().isBadRequest());
    api.put(sysadmin, "/admin/accounts/{id}/team-workspace-limit", Map.of("limit", 2), someone.id())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.teamWorkspaceLimit").value(2))
        .andExpect(jsonPath("$.teamWorkspacesCreated").value(0));
    api.get(sysadmin, "/admin/accounts").andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.id == '" + someone.id() + "')].teamWorkspaceLimit").value(2));
  }

  @Test
  void 퍼블릭은_찾기에_보이고_가입_방식대로_들어온다() throws Exception {
    TestUser owner = api.user("owner");
    TestUser visitor = api.user("visitor");
    api.allowTeamWorkspaces(owner, 3);
    String auto = TestApi.read(create(owner, "자동 가입 팀", "PUBLIC", "AUTO").andExpect(status().isCreated()), "$.slug");
    String approval = TestApi.read(create(owner, "승인 가입 팀", "PUBLIC", "APPROVAL").andExpect(status().isCreated()),
        "$.slug");
    String hidden = TestApi.read(create(owner, "프라이빗 팀", "PRIVATE", "AUTO").andExpect(status().isCreated()), "$.slug");

    api.get(visitor, "/workspaces?query={q}", "팀").andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.slug == '" + auto + "')].joinPolicy").value("AUTO"))
        .andExpect(jsonPath("$[?(@.slug == '" + approval + "')].joinPolicy").value("APPROVAL"))
        .andExpect(jsonPath("$[?(@.slug == '" + hidden + "')]").isEmpty());

    // 자동 가입: 신청하면 바로 멤버다
    api.post(visitor, "/workspaces/{ws}/join-requests", Map.of("message", "들어갑니다"), auto)
        .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("APPROVED"));
    api.get(visitor, "/workspaces/{ws}", auto).andExpect(status().isOk());

    // 승인 후 가입: 관리자가 승인할 때까지 멤버가 아니다
    api.post(visitor, "/workspaces/{ws}/join-requests", Map.of("message", "신청합니다"), approval)
        .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PENDING"));
    api.get(visitor, "/workspaces/{ws}", approval).andExpect(status().isNotFound());

    // 프라이빗: 보이지 않고, 주소를 알아도 신청할 수 없다
    api.post(visitor, "/workspaces/{ws}/join-requests", Map.of("message", "두드립니다"), hidden)
        .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("WORKSPACE_NOT_FOUND"));
    api.get(visitor, "/workspaces/{ws}", hidden).andExpect(status().isNotFound());
  }
}
