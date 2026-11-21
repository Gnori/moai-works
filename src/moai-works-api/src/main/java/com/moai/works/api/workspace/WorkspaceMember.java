package com.moai.works.api.workspace;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** 워크스페이스 멤버십 (사본). 정본은 Keycloak 조직 멤버. 역할은 {@link MemberRole} 로 여러 개. */
@Entity
@Table(name = "workspace_member")
public class WorkspaceMember {

  public enum Status {
    ACTIVE,
    /** 관리자가 정지 — 권한 없음, 멤버십은 남는다 */
    SUSPENDED
  }

  public enum JoinedVia {
    CREATOR,
    REQUEST,
    AUTO,
    INVITE,
    ADMIN
  }

  @EmbeddedId
  private Id id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private Status status;

  @Enumerated(EnumType.STRING)
  @Column(name = "joined_via", nullable = false, length = 20)
  private JoinedVia joinedVia;

  @Column(name = "joined_at", nullable = false)
  private Instant joinedAt;

  @Version
  @Column(name = "row_version", nullable = false)
  private int rowVersion;

  protected WorkspaceMember() {
  }

  public WorkspaceMember(UUID workspaceId, UUID userId, JoinedVia joinedVia, Instant now) {
    this.id = new Id(workspaceId, userId);
    this.status = Status.ACTIVE;
    this.joinedVia = joinedVia;
    this.joinedAt = now;
  }

  public void suspend() {
    this.status = Status.SUSPENDED;
  }

  public void resume() {
    this.status = Status.ACTIVE;
  }

  public boolean isActive() {
    return status == Status.ACTIVE;
  }

  public UUID getWorkspaceId() {
    return id.workspaceId;
  }

  public UUID getUserId() {
    return id.userId;
  }

  public Status getStatus() {
    return status;
  }

  public JoinedVia getJoinedVia() {
    return joinedVia;
  }

  public Instant getJoinedAt() {
    return joinedAt;
  }

  @Embeddable
  public static class Id implements Serializable {

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    protected Id() {
    }

    public Id(UUID workspaceId, UUID userId) {
      this.workspaceId = workspaceId;
      this.userId = userId;
    }

    @Override
    public boolean equals(Object o) {
      return o instanceof Id other && workspaceId.equals(other.workspaceId) && userId.equals(other.userId);
    }

    @Override
    public int hashCode() {
      return Objects.hash(workspaceId, userId);
    }
  }
}
