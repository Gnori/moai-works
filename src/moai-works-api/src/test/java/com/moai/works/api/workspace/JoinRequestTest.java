package com.moai.works.api.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

import com.moai.works.api.support.ApiTest;
import com.moai.works.api.support.TestApi;
import com.moai.works.api.support.TestUser;

/** 가입 신청 — 공개 팀 워크스페이스에만 신청하고, 승인하는 사람이 줄 수 있는 역할로만 들어온다. */
@ApiTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JoinRequestTest {

  @Autowired
  TestApi api;

  TestUser owner;
  TestUser admin;
  String ws;

  @BeforeAll
  void setUp() throws Exception {
    owner = api.user("owner");
    admin = api.user("admin");
    ws = api.teamWorkspace(owner, "가입 신청 시험");
    api.join(ws, owner, admin, "admin");
  }

  private ResultActions apply(TestUser user, String workspace) throws Exception {
    return api.post(user, "/workspaces/{ws}/join-requests", Map.of("message", "함께 쓰고 싶습니다"), workspace);
  }

  private void settings(TestUser by, String workspace, String name, String visibility, String joinPolicy,
      List<String> domains) throws Exception {
    api.patch(by, "/workspaces/{ws}", Map.of("name", name, "visibility", visibility, "joinPolicy", joinPolicy,
        "allowedJoinDomains", domains, "defaultRoleKey", "member"), workspace).andExpect(status().isOk());
  }

  @Test
  void 신청하고_승인받으면_멤버가_된다() throws Exception {
    TestUser newcomer = api.user("newcomer");
    String id = TestApi.read(apply(newcomer, ws).andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("PENDING")), "$.id");
    api.get(newcomer, "/workspaces/{ws}", ws).andExpect(status().isNotFound());

    List<String> pending = TestApi.read(api.get(admin, "/workspaces/{ws}/join-requests", ws), "$[*].id");
    assertThat(pending).contains(id);

    api.post(admin, "/workspaces/{ws}/join-requests/{id}/approve", Map.of("roleKey", "admin"), ws, id)
        .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"))
        .andExpect(jsonPath("$.grantedRoleKey").value("admin"));
    List<String> roles = TestApi.read(api.get(newcomer, "/workspaces/{ws}", ws).andExpect(status().isOk()), "$.myRoles");
    assertThat(roles).containsExactly("admin");

    // 처리된 신청은 다시 처리할 수 없다
    api.post(admin, "/workspaces/{ws}/join-requests/{id}/approve", null, ws, id).andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("REQUEST_CLOSED"));
    // 이미 멤버면 다시 신청할 수 없다
    apply(newcomer, ws).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ALREADY_MEMBER"));
  }

  @Test
  void 역할을_지정하지_않으면_기본_역할로_들어온다() throws Exception {
    TestUser newcomer = api.user("default-role");
    String id = TestApi.read(apply(newcomer, ws).andExpect(status().isCreated()), "$.id");
    api.post(owner, "/workspaces/{ws}/join-requests/{id}/approve", null, ws, id).andExpect(status().isOk())
        .andExpect(jsonPath("$.grantedRoleKey").value("member"));
  }

  @Test
  void 대기_중인_신청은_하나만_있고_본인이_철회한다() throws Exception {
    TestUser newcomer = api.user("twice");
    String id = TestApi.read(apply(newcomer, ws).andExpect(status().isCreated()), "$.id");
    apply(newcomer, ws).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("REQUEST_PENDING"));

    TestUser other = api.user("not-mine");
    api.delete(other, "/me/join-requests/{id}", id).andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("REQUEST_NOT_FOUND"));
    api.delete(newcomer, "/me/join-requests/{id}", id).andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CANCELED"));
    // 철회한 뒤에는 다시 신청할 수 있다
    apply(newcomer, ws).andExpect(status().isCreated());
  }

  @Test
  void 거절된_뒤에는_기다려야_다시_신청할_수_있다() throws Exception {
    TestUser newcomer = api.user("rejected");
    String id = TestApi.read(apply(newcomer, ws).andExpect(status().isCreated()), "$.id");
    api.post(admin, "/workspaces/{ws}/join-requests/{id}/reject", Map.of("note", "지금은 받지 않습니다"), ws, id)
        .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"));
    api.get(newcomer, "/workspaces/{ws}", ws).andExpect(status().isNotFound());
    apply(newcomer, ws).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("REJOIN_WAIT"));
  }

  @Test
  void 승인하는_사람이_줄_수_없는_역할로는_승인할_수_없다() throws Exception {
    TestUser newcomer = api.user("wants-owner");
    String id = TestApi.read(apply(newcomer, ws).andExpect(status().isCreated()), "$.id");
    api.post(admin, "/workspaces/{ws}/join-requests/{id}/approve", Map.of("roleKey", "owner"), ws, id)
        .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("OWNER_ONLY"));
    api.get(newcomer, "/workspaces/{ws}", ws).andExpect(status().isNotFound());
  }

  @Test
  void 다른_워크스페이스의_신청은_처리할_수_없다() throws Exception {
    TestUser newcomer = api.user("elsewhere");
    String id = TestApi.read(apply(newcomer, ws).andExpect(status().isCreated()), "$.id");
    TestUser stranger = api.user("stranger");
    String other = api.teamWorkspace(stranger, "다른 팀");
    api.post(stranger, "/workspaces/{ws}/join-requests/{id}/approve", null, other, id).andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("REQUEST_NOT_FOUND"));
  }

  @Test
  void 비공개_개인_초대_전용_워크스페이스에는_신청할_수_없다() throws Exception {
    TestUser boss = api.user("boss");
    TestUser newcomer = api.user("knocker");

    // 개인 워크스페이스 — 있는지도 알 수 없다
    apply(newcomer, api.personalWorkspace(boss)).andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("WORKSPACE_NOT_FOUND"));

    // 프라이빗 팀 워크스페이스 — 찾기에도 안 나오고, 신청하면 있는지도 알려 주지 않는다. 가입 방식을 무엇으로 보내든 같다
    String team = api.teamWorkspace(boss, "닫힌 팀");
    settings(boss, team, "닫힌 팀", "PRIVATE", "AUTO", List.of());
    apply(newcomer, team).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("WORKSPACE_NOT_FOUND"));
    api.get(newcomer, "/workspaces?query={q}", "닫힌 팀").andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.slug == '" + team + "')]").isEmpty());
    // 프라이빗에는 가입 방식이 없다 — 저장된 값은 하나로 맞춰진다
    api.get(boss, "/workspaces/{ws}", team).andExpect(jsonPath("$.visibility").value("PRIVATE"))
        .andExpect(jsonPath("$.joinPolicy").value("APPROVAL"));

    // "초대로만"이라는 가입 방식은 없다(프라이빗이 그 뜻이다)
    api.patch(boss, "/workspaces/{ws}", Map.of("name", "닫힌 팀", "visibility", "PUBLIC", "joinPolicy", "INVITE_ONLY",
        "allowedJoinDomains", List.of(), "defaultRoleKey", "member"), team).andExpect(status().isBadRequest());
  }

  @Test
  void 허용한_이메일_도메인만_신청할_수_있다() throws Exception {
    TestUser boss = api.user("domain-boss");
    String team = api.teamWorkspace(boss, "도메인 제한 팀");
    settings(boss, team, "도메인 제한 팀", "PUBLIC", "APPROVAL", List.of("example.com"));
    apply(api.user("outside-domain"), team).andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("EMAIL_DOMAIN_NOT_ALLOWED"));
  }

  @Test
  void 자동_가입이면_신청하자마자_기본_역할의_멤버가_된다() throws Exception {
    TestUser boss = api.user("auto-boss");
    String team = api.teamWorkspace(boss, "열린 팀");
    settings(boss, team, "열린 팀", "PUBLIC", "AUTO", List.of());
    TestUser newcomer = api.user("walk-in");
    apply(newcomer, team).andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("APPROVED"));
    List<String> roles = TestApi.read(api.get(newcomer, "/workspaces/{ws}", team).andExpect(status().isOk()), "$.myRoles");
    assertThat(roles).containsExactly("member");
  }
}
