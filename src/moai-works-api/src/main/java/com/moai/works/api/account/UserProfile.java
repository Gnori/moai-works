package com.moai.works.api.account;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * 계정 — 정본은 Keycloak 사용자, 이 행은 판정 · 화면용 사본과 가입 상태.
 *
 * <p>{@link #id} 는 Keycloak 사용자 id(토큰 {@code sub}) 그대로다.
 */
@Entity
@Table(name = "user_profile")
public class UserProfile {

  @Id
  private UUID id;

  @Column(nullable = false, unique = true)
  private String username;

  @Column(nullable = false, length = 320)
  private String email;

  @Column(name = "display_name", nullable = false, length = 100)
  private String displayName;

  @Enumerated(EnumType.STRING)
  @Column(name = "account_status", nullable = false, length = 30)
  private AccountStatus status;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private AccountOrigin origin;

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "platform_roles", nullable = false, columnDefinition = "text[]")
  private List<String> platformRoles = new ArrayList<>();

  @Column(name = "personal_workspace_id")
  private UUID personalWorkspaceId;

  @Column(name = "last_workspace_id")
  private UUID lastWorkspaceId;

  @Column(nullable = false, length = 10)
  private String locale = "ko";

  @Column(name = "signed_up_at", nullable = false)
  private Instant signedUpAt;

  @Column(name = "activated_at")
  private Instant activatedAt;

  @Column(name = "last_login_at")
  private Instant lastLoginAt;

  /** 만들 수 있는 팀 워크스페이스 수. 가입하면 0 이고(못 만든다), 시스템 관리자가 계정마다 정해 준다 */
  @Column(name = "team_workspace_limit", nullable = false)
  private int teamWorkspaceLimit;

  /**
   * 다른 로그인을 끊은 때(비밀번호 변경 · 재설정, V22) — 이보다 먼저 시작한 로그인의 토큰은 받지 않는다.
   * 초 단위로 올려 둔다: 토큰의 auth_time · iat 이 초 단위라, 끊기 바로 전 같은 초에 받은 토큰도 걸린다.
   */
  @Column(name = "sessions_revoked_at")
  private Instant sessionsRevokedAt;

  /** 끊을 때 남긴 로그인(Keycloak 세션 id = 토큰 sid) — 비밀번호를 바꾼 그 브라우저. 재설정이면 null */
  @Column(name = "kept_session_id", length = 64)
  private String keptSessionId;

  @Version
  @Column(name = "row_version", nullable = false)
  private int rowVersion;

  protected UserProfile() {
  }

  public UserProfile(UUID id, String username, String email, String displayName, AccountStatus status,
      AccountOrigin origin, Instant now) {
    this.id = id;
    this.username = username;
    this.email = email.toLowerCase();
    this.displayName = displayName;
    this.status = status;
    this.origin = origin;
    this.signedUpAt = now;
    if (status == AccountStatus.ACTIVE) {
      this.activatedAt = now;
    }
  }

  /** 상태를 바꾼다. 처음 ACTIVE 가 되면 활성화 시각을 남긴다. */
  public void changeStatus(AccountStatus next, Instant now) {
    if (!status.canMoveTo(next)) {
      throw new IllegalStateException("계정 상태를 " + status + " → " + next + " 로 바꿀 수 없습니다");
    }
    this.status = next;
    if (next == AccountStatus.ACTIVE && activatedAt == null) {
      this.activatedAt = now;
    }
  }

  public void rename(String displayName) {
    this.displayName = displayName;
  }

  /** Keycloak 토큰의 렐름 역할로 플랫폼 역할 사본을 맞춘다. 바뀌었으면 true. */
  public boolean syncPlatformRoles(List<String> roles) {
    List<String> next = roles.stream().filter(PlatformRole::isPlatformRole).sorted().distinct().toList();
    if (next.equals(platformRoles)) {
      return false;
    }
    this.platformRoles = new ArrayList<>(next);
    return true;
  }

  public void recordLogin(Instant now) {
    this.lastLoginAt = now;
  }

  public void assignPersonalWorkspace(UUID workspaceId) {
    this.personalWorkspaceId = workspaceId;
  }

  public void rememberWorkspace(UUID workspaceId) {
    this.lastWorkspaceId = workspaceId;
  }

  /** 시스템 관리자가 이 계정이 만들 수 있는 팀 워크스페이스 수를 정한다(0 이면 못 만든다) */
  public void allowTeamWorkspaces(int limit) {
    if (limit < 0 || limit > 100) {
      throw new IllegalArgumentException("팀 워크스페이스 수는 0 ~ 100 이어야 합니다");
    }
    this.teamWorkspaceLimit = limit;
  }

  public int getTeamWorkspaceLimit() {
    return teamWorkspaceLimit;
  }

  /**
   * 이 계정의 다른 로그인을 끊는다 — {@code at} 보다 먼저 시작한 로그인의 토큰은 {@link #sessionRevoked} 가 거절한다.
   *
   * @param keep 계속 쓸 로그인(Keycloak 세션 id). null 이면 모두 끊는다
   */
  public void revokeSessions(Instant at, String keep) {
    this.sessionsRevokedAt = at.getNano() == 0 ? at : java.time.Instant.ofEpochSecond(at.getEpochSecond() + 1);
    this.keptSessionId = keep;
  }

  /**
   * 이 토큰의 로그인이 끊긴 것인가.
   *
   * @param sessionId       토큰의 sid (없으면 null)
   * @param authenticatedAt 그 로그인이 시작된 때(auth_time, 없으면 iat). 모르면(null) 끊긴 것으로 본다
   */
  public boolean sessionRevoked(String sessionId, Instant authenticatedAt) {
    if (sessionsRevokedAt == null) {
      return false;
    }
    if (keptSessionId != null && keptSessionId.equals(sessionId)) {
      return false;
    }
    return authenticatedAt == null || authenticatedAt.isBefore(sessionsRevokedAt);
  }

  public Instant getSessionsRevokedAt() {
    return sessionsRevokedAt;
  }

  public boolean isActive() {
    return status == AccountStatus.ACTIVE;
  }

  public boolean hasPlatformRole(String role) {
    return platformRoles.contains(role);
  }

  public UUID getId() {
    return id;
  }

  public String getUsername() {
    return username;
  }

  public String getEmail() {
    return email;
  }

  public String getDisplayName() {
    return displayName;
  }

  public AccountStatus getStatus() {
    return status;
  }

  public AccountOrigin getOrigin() {
    return origin;
  }

  public List<String> getPlatformRoles() {
    return List.copyOf(platformRoles);
  }

  public UUID getPersonalWorkspaceId() {
    return personalWorkspaceId;
  }

  public UUID getLastWorkspaceId() {
    return lastWorkspaceId;
  }

  public String getLocale() {
    return locale;
  }

  public Instant getSignedUpAt() {
    return signedUpAt;
  }

  public Instant getActivatedAt() {
    return activatedAt;
  }

  public Instant getLastLoginAt() {
    return lastLoginAt;
  }
}
