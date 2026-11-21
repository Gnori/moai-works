package com.moai.works.api.workspace;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** 워크스페이스 API 응답 모양. 엔티티를 그대로 내보내지 않는다. */
public final class WorkspaceViews {

  private WorkspaceViews() {
  }

  /** 내 워크스페이스 목록 한 줄 */
  public record Summary(String slug, String name, Workspace.Kind kind, Workspace.Status status, List<String> myRoles,
      long memberCount) {
  }

  /** 워크스페이스 상세 (멤버에게) */
  public record Detail(UUID id, String slug, String name, String description, Workspace.Kind kind,
      Workspace.Visibility visibility, Workspace.JoinPolicy joinPolicy, List<String> allowedJoinDomains,
      String defaultRoleKey, Workspace.Status status, Instant createdAt, long memberCount, List<String> myRoles,
      List<String> myPermissions) {
  }

  /** 공개 워크스페이스 찾기 결과 */
  public record Discoverable(String slug, String name, String description, Workspace.JoinPolicy joinPolicy,
      long memberCount, String myStatus) {
  }

  public record Member(UUID userId, String username, String displayName, String email, WorkspaceMember.Status status,
      WorkspaceMember.JoinedVia joinedVia, Instant joinedAt, List<String> roles) {
  }

  /** @param memberCount 이 역할을 가진 멤버 수 (지울 수 있는지 화면이 판단) */
  public record Role(String key, String name, String description, boolean builtin, List<String> permissions,
      long memberCount) {

    static Role of(WorkspaceRole r, long memberCount) {
      return new Role(r.getRoleKey(), r.getName(), r.getDescription(), r.isBuiltin(),
          r.getPermissions().stream().sorted().toList(), memberCount);
    }
  }

  /** 초대 (보낸 쪽 · 받는 쪽 공통) */
  public record InvitationView(UUID id, String workspaceSlug, String workspaceName, String email, String roleKey,
      String roleName, String invitedByName, String message, Invitation.Status status, Instant createdAt,
      Instant expiresAt, Instant decidedAt) {
  }

  /** 초대를 만든 직후에만 — 초대 링크(토큰 원문)는 이 응답에만 있다 */
  public record InvitationCreated(InvitationView invitation, String inviteUrl) {
  }

  public record JoinRequestView(UUID id, String workspaceSlug, String workspaceName, UUID userId, String username,
      String displayName, String email, String message, JoinRequest.Status status, String grantedRoleKey,
      Instant requestedAt, Instant decidedAt, String decisionNote) {
  }

  public record AuditEntry(UUID id, Instant occurredAt, String actorType, String actorName, String action,
      String targetType, String targetId, String details) {
  }
}
