package com.moai.works.api.workspace;

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

/** 초대 — 줄 수 있는 역할로만 보내고, 초대받은 이메일의 계정만 수락한다. 링크는 한 번만 쓴다. */
@ApiTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InvitationTest {

  @Autowired
  TestApi api;

  TestUser owner;
  TestUser admin;
  TestUser member;
  String ws;

  @BeforeAll
  void setUp() throws Exception {
    owner = api.user("owner");
    admin = api.user("admin");
    member = api.user("member");
    ws = api.teamWorkspace(owner, "초대 시험");
    api.join(ws, owner, admin, "admin");
    api.join(ws, owner, member, "member");
  }

  private ResultActions invite(TestUser by, TestUser to, String roleKey) throws Exception {
    return api.post(by, "/workspaces/{ws}/invitations", Map.of("email", to.email(), "roleKey", roleKey), ws);
  }

  /** 초대 링크에서 토큰만 꺼낸다 */
  private static String token(ResultActions created) throws Exception {
    String url = TestApi.read(created, "$.inviteUrl");
    return url.substring(url.lastIndexOf('/') + 1);
  }

  @Test
  void 초대_링크로_수락하면_멤버가_된다() throws Exception {
    TestUser guest = api.user("guest");
    ResultActions created = invite(admin, guest, "admin").andExpect(status().isCreated())
        .andExpect(jsonPath("$.invitation.status").value("PENDING"));
    String token = token(created);

    api.get(guest, "/workspaces/{ws}", ws).andExpect(status().isNotFound());
    api.get(guest, "/invitations/{token}", token).andExpect(status().isOk())
        .andExpect(jsonPath("$.roleKey").value("admin"));
    api.post(guest, "/invitations/{token}/accept", null, token).andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACCEPTED"));

    List<String> roles = TestApi.read(api.get(guest, "/workspaces/{ws}", ws).andExpect(status().isOk()), "$.myRoles");
    assertThat(roles).containsExactly("admin");
    // 쓴 링크는 다시 못 쓴다
    api.post(guest, "/invitations/{token}/accept", null, token).andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("INVITATION_CLOSED"));
  }

  @Test
  void 초대받은_이메일이_아닌_계정은_수락할_수_없다() throws Exception {
    TestUser invited = api.user("invited");
    TestUser other = api.user("other");
    String token = token(invite(owner, invited, "member").andExpect(status().isCreated()));

    // 내용은 보이지만(화면이 안내한다) 수락 · 거절은 막힌다
    api.get(other, "/invitations/{token}", token).andExpect(status().isOk());
    api.post(other, "/invitations/{token}/accept", null, token).andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("INVITATION_EMAIL_MISMATCH"));
    api.post(other, "/invitations/{token}/decline", null, token).andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("INVITATION_EMAIL_MISMATCH"));
    api.get(other, "/workspaces/{ws}", ws).andExpect(status().isNotFound());
  }

  @Test
  void 초대_권한이_없으면_보낼_수_없다() throws Exception {
    TestUser guest = api.user("guest-2");
    invite(member, guest, "member").andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("PERMISSION_REQUIRED"));
  }

  @Test
  void 줄_수_없는_역할로는_초대할_수_없다() throws Exception {
    TestUser guest = api.user("guest-3");
    // 관리자는 소유자 역할로 초대할 수 없다
    invite(admin, guest, "owner").andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("OWNER_ONLY"));

    // 초대는 할 수 있지만 가진 권한이 적은 사람은 더 큰 역할로 초대할 수 없다
    TestUser inviter = api.user("inviter");
    api.join(ws, owner, inviter, "member");
    String role = api.customRole(ws, owner, "초대 담당", Set.of("workspace.read", "member.read", "member.invite"));
    api.setRoles(ws, owner, inviter, "member", role);
    invite(inviter, guest, "admin").andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ESCALATION_DENIED"));
    invite(inviter, guest, "member").andExpect(status().isCreated());
  }

  @Test
  void 같은_이메일에_대기_초대는_하나만_이미_멤버면_못_보낸다() throws Exception {
    TestUser guest = api.user("guest-4");
    invite(owner, guest, "member").andExpect(status().isCreated());
    invite(owner, guest, "member").andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("INVITATION_PENDING"));
    invite(owner, member, "member").andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ALREADY_MEMBER"));
    invite(owner, guest, "no-such-role").andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("ROLE_NOT_FOUND"));
  }

  @Test
  void 취소한_초대는_수락할_수_없다() throws Exception {
    TestUser guest = api.user("guest-5");
    ResultActions created = invite(owner, guest, "member").andExpect(status().isCreated());
    String id = TestApi.read(created, "$.invitation.id");
    String token = token(created);

    api.delete(admin, "/workspaces/{ws}/invitations/{id}", ws, id).andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("REVOKED"));
    api.post(guest, "/invitations/{token}/accept", null, token).andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("INVITATION_CLOSED"));
    api.get(guest, "/workspaces/{ws}", ws).andExpect(status().isNotFound());
  }

  @Test
  void 받은_초대_목록에서_거절한다() throws Exception {
    TestUser guest = api.user("guest-6");
    String id = TestApi.read(invite(owner, guest, "member").andExpect(status().isCreated()), "$.invitation.id");

    List<String> mine = TestApi.read(api.get(guest, "/me/invitations").andExpect(status().isOk()), "$[*].id");
    assertThat(mine).containsExactly(id);
    api.post(guest, "/me/invitations/{id}/decline", null, id).andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("DECLINED"));
    List<String> after = TestApi.read(api.get(guest, "/me/invitations"), "$[*].id");
    assertThat(after).isEmpty();
    api.get(guest, "/workspaces/{ws}", ws).andExpect(status().isNotFound());
  }

  @Test
  void 다른_워크스페이스의_초대는_취소할_수_없다() throws Exception {
    TestUser guest = api.user("guest-7");
    String id = TestApi.read(invite(owner, guest, "member").andExpect(status().isCreated()), "$.invitation.id");
    TestUser stranger = api.user("stranger");
    String other = api.teamWorkspace(stranger, "다른 팀");
    api.delete(stranger, "/workspaces/{ws}/invitations/{id}", other, id).andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("INVITATION_NOT_FOUND"));
  }

  @Test
  void 없는_링크는_404() throws Exception {
    api.get(member, "/invitations/{token}", "no-such-token").andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("INVITATION_NOT_FOUND"));
  }
}
