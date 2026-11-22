package com.moai.works.api.project;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/** 프로젝트 역할 사본 한 줄 — 정본은 Keycloak 조직 그룹 {@code /projects/<project id>/<role>} (design/04) */
@Entity
@Table(name = "project_role_binding")
@IdClass(ProjectRoleBinding.Key.class)
public class ProjectRoleBinding {

  public static class Key implements Serializable {
    private UUID projectId;
    private UUID userId;
    private String role;

    public Key() {
    }

    public Key(UUID projectId, UUID userId, String role) {
      this.projectId = projectId;
      this.userId = userId;
      this.role = role;
    }

    @Override
    public boolean equals(Object o) {
      return o instanceof Key k && Objects.equals(projectId, k.projectId) && Objects.equals(userId, k.userId)
          && Objects.equals(role, k.role);
    }

    @Override
    public int hashCode() {
      return Objects.hash(projectId, userId, role);
    }
  }

  @Id
  @Column(name = "project_id")
  private UUID projectId;

  @Id
  @Column(name = "user_id")
  private UUID userId;

  @Id
  @Column(name = "role", length = 30)
  private String role;

  @Column(name = "keycloak_group_id", length = 64)
  private String keycloakGroupId;

  @Column(name = "granted_by")
  private UUID grantedBy;

  @Column(name = "granted_at", nullable = false)
  private Instant grantedAt;

  protected ProjectRoleBinding() {
  }

  public ProjectRoleBinding(UUID projectId, UUID userId, ProjectRole role, String keycloakGroupId, UUID grantedBy,
      Instant grantedAt) {
    this.projectId = projectId;
    this.userId = userId;
    this.role = role.key();
    this.keycloakGroupId = keycloakGroupId;
    this.grantedBy = grantedBy;
    this.grantedAt = grantedAt;
  }

  public UUID getProjectId() {
    return projectId;
  }

  public UUID getUserId() {
    return userId;
  }

  public String getRole() {
    return role;
  }

  public String getKeycloakGroupId() {
    return keycloakGroupId;
  }

  public UUID getGrantedBy() {
    return grantedBy;
  }

  public Instant getGrantedAt() {
    return grantedAt;
  }
}
