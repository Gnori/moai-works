package com.moai.works.api.workspace;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * 초대 (정본, design/03 §10). 이메일 하나에 묶이고, 그 이메일의 계정만 수락할 수 있다.
 * 링크 토큰은 원문을 저장하지 않고 SHA-256 만 둔다(MCP 키와 같은 원칙). 7일 뒤 만료.
 */
@Entity
@Table(name = "invitation")
public class Invitation {

  public enum Status {
    PENDING,
    ACCEPTED,
    DECLINED,
    REVOKED,
    EXPIRED
  }

  @Id
  private UUID id;

  @Column(name = "workspace_id", nullable = false)
  private UUID workspaceId;

  @Column(nullable = false, length = 320)
  private String email;

  @Column(name = "role_key", nullable = false, length = 63)
  private String roleKey;

  @Column(length = 500)
  private String message;

  @Column(name = "invited_by", nullable = false)
  private UUID invitedBy;

  @Column(name = "token_hash", nullable = false, unique = true, length = 64)
  private String tokenHash;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private Status status;

  @Column(name = "accepted_user_id")
  private UUID acceptedUserId;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "decided_at")
  private Instant decidedAt;

  @Version
  @Column(name = "row_version", nullable = false)
  private int rowVersion;

  protected Invitation() {
  }

  public Invitation(UUID workspaceId, String email, String roleKey, String message, UUID invitedBy, String tokenHash,
      Instant now, Instant expiresAt) {
    this.id = UUID.randomUUID();
    this.workspaceId = workspaceId;
    this.email = email.toLowerCase();
    this.roleKey = roleKey;
    this.message = message;
    this.invitedBy = invitedBy;
    this.tokenHash = tokenHash;
    this.status = Status.PENDING;
    this.createdAt = now;
    this.expiresAt = expiresAt;
  }

  /** 대기 중이고 기한 안인가 */
  public boolean open(Instant now) {
    return status == Status.PENDING && now.isBefore(expiresAt);
  }

  /** 기한이 지난 대기 초대를 만료로 바꾼다. 바뀌었으면 true. */
  public boolean expireIfDue(Instant now) {
    if (status == Status.PENDING && !now.isBefore(expiresAt)) {
      status = Status.EXPIRED;
      decidedAt = now;
      return true;
    }
    return false;
  }

  public void accept(UUID userId, Instant now) {
    close(Status.ACCEPTED, now);
    this.acceptedUserId = userId;
  }

  public void decline(Instant now) {
    close(Status.DECLINED, now);
  }

  public void revoke(Instant now) {
    close(Status.REVOKED, now);
  }

  private void close(Status next, Instant now) {
    if (!open(now)) {
      throw new IllegalStateException("이미 처리됐거나 기한이 지난 초대입니다: " + status);
    }
    this.status = next;
    this.decidedAt = now;
  }

  public boolean isFor(String email) {
    return this.email.equalsIgnoreCase(email);
  }

  public UUID getId() {
    return id;
  }

  public UUID getWorkspaceId() {
    return workspaceId;
  }

  public String getEmail() {
    return email;
  }

  public String getRoleKey() {
    return roleKey;
  }

  public String getMessage() {
    return message;
  }

  public UUID getInvitedBy() {
    return invitedBy;
  }

  public Status getStatus() {
    return status;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public Instant getDecidedAt() {
    return decidedAt;
  }

  public interface Repository extends JpaRepository<Invitation, UUID> {

    Optional<Invitation> findByTokenHash(String tokenHash);

    @Query("select i from Invitation i where i.workspaceId = ?1 and i.status = com.moai.works.api.workspace.Invitation.Status.PENDING"
        + " order by i.createdAt desc")
    List<Invitation> findPending(UUID workspaceId);

    @Query("select i from Invitation i where lower(i.email) = lower(?1)"
        + " and i.status = com.moai.works.api.workspace.Invitation.Status.PENDING order by i.createdAt desc")
    List<Invitation> findPendingForEmail(String email);

    @Query("select i from Invitation i where i.workspaceId = ?1 and lower(i.email) = lower(?2)"
        + " and i.status = com.moai.works.api.workspace.Invitation.Status.PENDING")
    Optional<Invitation> findPendingFor(UUID workspaceId, String email);
  }
}
