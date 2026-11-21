package com.moai.works.api.workspace;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.moai.works.api.config.EndPointPaths;
import com.moai.works.api.security.access.CurrentUser;
import com.moai.works.api.security.access.UserApi;

/**
 * 받은 초대 — 로그인한 사람 본인. 링크(토큰)로도, 내 초대 목록(id)으로도 받을 수 있다.
 * 수락은 초대받은 이메일의 계정만 된다.
 */
@RestController
@UserApi
public class InvitationController {

  private static final String BY_TOKEN = EndPointPaths.V1 + "/invitations/{token}";
  private static final String MINE = EndPointPaths.V1 + "/me/invitations";

  private final InvitationService invitations;

  public InvitationController(InvitationService invitations) {
    this.invitations = invitations;
  }

  @GetMapping(BY_TOKEN)
  public WorkspaceViews.InvitationView preview(@PathVariable String token) {
    return invitations.preview(token);
  }

  @PostMapping(BY_TOKEN + "/accept")
  public WorkspaceViews.InvitationView acceptByToken(CurrentUser user, @PathVariable String token) {
    return invitations.acceptByToken(token, user);
  }

  @PostMapping(BY_TOKEN + "/decline")
  public WorkspaceViews.InvitationView declineByToken(CurrentUser user, @PathVariable String token) {
    return invitations.declineByToken(token, user);
  }

  @GetMapping(MINE)
  public List<WorkspaceViews.InvitationView> mine(CurrentUser user) {
    return invitations.mine(user);
  }

  @PostMapping(MINE + "/{id}/accept")
  public WorkspaceViews.InvitationView accept(CurrentUser user, @PathVariable UUID id) {
    return invitations.accept(id, user);
  }

  @PostMapping(MINE + "/{id}/decline")
  public WorkspaceViews.InvitationView decline(CurrentUser user, @PathVariable UUID id) {
    return invitations.decline(id, user);
  }
}
