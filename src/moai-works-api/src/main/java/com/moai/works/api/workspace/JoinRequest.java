package com.moai.works.api.workspace;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** 가입 신청 (정본, design/03 §10). 한 워크스페이스에 한 사람의 대기 신청은 하나뿐이다. */
@Entity
@Table(name = "join_request")
public class JoinRequest {

  public enum Status {
    PENDING,
    APPROVED,
    REJECTED,
    CANCELED,
    EXPIRED
  }

  @Id
  private UUID id;

  @Column(name = "workspace_id", nullable = false)
  private UUID workspaceId;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(length = 500)
  private String message;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private Status status;

  @Column(name = "granted_role_key", length = 63)
  private String grantedRoleKey;

  @Column(name = "decided_by")
  private UUID decidedBy;

  @Column(name = "decided_at")
  private Instant decidedAt;

  @Column(name = "decision_note", length = 500)
  private String decisionNote;

  @Column(name = "requested_at", nullable = false)
  private Instant requestedAt;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Version
  @Column(name = "row_version", nullable = false)
  private int rowVersion;

  protected JoinRequest() {
  }

  public JoinRequest(UUID workspaceId, UUID userId, String message, Instant now, Instant expiresAt) {
    this.id = UUID.randomUUID();
    this.workspaceId = workspaceId;
    this.userId = userId;
    this.message = message;
    this.status = Status.PENDING;
    this.requestedAt = now;
    this.expiresAt = expiresAt;
  }

  public void approve(UUID by, String roleKey, String note, Instant now) {
    decide(Status.APPROVED, by, note, now);
    this.grantedRoleKey = roleKey;
  }

  public void reject(UUID by, String note, Instant now) {
    decide(Status.REJECTED, by, note, now);
  }

  public void cancel(Instant now) {
    decide(Status.CANCELED, userId, null, now);
  }

  private void decide(Status next, UUID by, String note, Instant now) {
    if (status != Status.PENDING) {
      throw new IllegalStateException("이미 처리된 신청입니다: " + status);
    }
    this.status = next;
    this.decidedBy = by;
    this.decidedAt = now;
    this.decisionNote = note;
  }

  public boolean isPending() {
    return status == Status.PENDING;
  }

  public UUID getId() {
    return id;
  }

  public UUID getWorkspaceId() {
    return workspaceId;
  }

  public UUID getUserId() {
    return userId;
  }

  public String getMessage() {
    return message;
  }

  public Status getStatus() {
    return status;
  }

  public String getGrantedRoleKey() {
    return grantedRoleKey;
  }

  public UUID getDecidedBy() {
    return decidedBy;
  }

  public Instant getDecidedAt() {
    return decidedAt;
  }

  public String getDecisionNote() {
    return decisionNote;
  }

  public Instant getRequestedAt() {
    return requestedAt;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }
}
