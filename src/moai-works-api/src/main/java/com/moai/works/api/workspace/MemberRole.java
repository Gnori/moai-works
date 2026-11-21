package com.moai.works.api.workspace;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** 멤버가 가진 역할 하나 (사본). 정본은 Keycloak 조직 그룹 가입. 권한은 역할들의 합집합. */
@Entity
@Table(name = "member_role")
public class MemberRole {

  @EmbeddedId
  private Id id;

  @Column(name = "granted_by")
  private UUID grantedBy;

  @Column(name = "granted_at", nullable = false)
  private Instant grantedAt;

  protected MemberRole() {
  }

  public MemberRole(UUID workspaceId, UUID userId, String roleKey, UUID grantedBy, Instant now) {
    this.id = new Id(workspaceId, userId, roleKey);
    this.grantedBy = grantedBy;
    this.grantedAt = now;
  }

  public UUID getWorkspaceId() {
    return id.workspaceId;
  }

  public UUID getUserId() {
    return id.userId;
  }

  public String getRoleKey() {
    return id.roleKey;
  }

  public Instant getGrantedAt() {
    return grantedAt;
  }

  @Embeddable
  public static class Id implements Serializable {

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "role_key", nullable = false, length = 63)
    private String roleKey;

    protected Id() {
    }

    public Id(UUID workspaceId, UUID userId, String roleKey) {
      this.workspaceId = workspaceId;
      this.userId = userId;
      this.roleKey = roleKey;
    }

    @Override
    public boolean equals(Object o) {
      return o instanceof Id other && workspaceId.equals(other.workspaceId) && userId.equals(other.userId)
          && roleKey.equals(other.roleKey);
    }

    @Override
    public int hashCode() {
      return Objects.hash(workspaceId, userId, roleKey);
    }
  }
}
