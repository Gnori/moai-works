package com.moai.works.api.workspace;

import java.time.Instant;
import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * 워크스페이스 역할 (사본). 정본은 Keycloak 조직 그룹 {@code /roles/<roleKey>} 와 그 그룹의 클라이언트 역할 매핑이다.
 * 권한 판정은 이 역할의 {@link #permissions}(role_permission 표) 로 한다 — Keycloak 에 쓴 직후 함께 고친다.
 */
@Entity
@Table(name = "workspace_role")
public class WorkspaceRole {

  @Id
  private UUID id;

  @Column(name = "workspace_id", nullable = false)
  private UUID workspaceId;

  @Column(name = "role_key", nullable = false, length = 63)
  private String roleKey;

  @Column(nullable = false, length = 100)
  private String name;

  @Column(length = 500)
  private String description;

  @Column(nullable = false)
  private boolean builtin;

  /** 권한 코드 — role_permission 표 (permission 표를 FK 로 참조하므로 없는 코드는 들어갈 수 없다) */
  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(name = "role_permission", joinColumns = @JoinColumn(name = "role_id"))
  @Column(name = "permission_code", nullable = false, length = 100)
  private Set<String> permissions = new TreeSet<>();

  @Column(name = "keycloak_group_id", length = 64)
  private String keycloakGroupId;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(name = "row_version", nullable = false)
  private int rowVersion;

  protected WorkspaceRole() {
  }

  private WorkspaceRole(UUID workspaceId, String roleKey, String name, String description, boolean builtin,
      Collection<String> permissions, String keycloakGroupId, Instant now) {
    this.id = UUID.randomUUID();
    this.workspaceId = workspaceId;
    this.roleKey = roleKey;
    this.name = name;
    this.description = description;
    this.builtin = builtin;
    this.permissions = new TreeSet<>(permissions);
    this.keycloakGroupId = keycloakGroupId;
    this.createdAt = now;
    this.updatedAt = now;
  }

  public static WorkspaceRole builtin(UUID workspaceId, BuiltinRole role, String keycloakGroupId, Instant now) {
    return new WorkspaceRole(workspaceId, role.key(), role.displayName(), role.description(), true,
        role.permissions().stream().map(Permission::code).toList(), keycloakGroupId, now);
  }

  /** 사용자 정의 역할 — role_key 는 custom-고유id, Keycloak 조직 그룹 이름과 같다. */
  public static WorkspaceRole custom(UUID workspaceId, String roleKey, String name, String description,
      Collection<String> permissions, String keycloakGroupId, Instant now) {
    return new WorkspaceRole(workspaceId, roleKey, name, description, false, permissions, keycloakGroupId, now);
  }

  /** 이름 · 설명 · 권한을 바꾼다 (사용자 정의 역할만). */
  public void update(String name, String description, Collection<String> permissions, Instant now) {
    if (builtin) {
      throw new IllegalStateException("기본 역할은 바꿀 수 없습니다");
    }
    this.name = name;
    this.description = description;
    this.permissions.clear();
    this.permissions.addAll(permissions);
    this.updatedAt = now;
  }

  /** Keycloak 에서 읽은 매핑으로 사본을 맞춘다(대사). 바뀌었으면 true. */
  public boolean syncPermissions(Collection<String> fromKeycloak, Instant now) {
    Set<String> next = new TreeSet<>(fromKeycloak);
    if (next.equals(permissions)) {
      return false;
    }
    this.permissions.clear();
    this.permissions.addAll(next);
    this.updatedAt = now;
    return true;
  }

  public UUID getId() {
    return id;
  }

  public UUID getWorkspaceId() {
    return workspaceId;
  }

  public String getRoleKey() {
    return roleKey;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public boolean isBuiltin() {
    return builtin;
  }

  public Set<String> getPermissions() {
    return Set.copyOf(permissions);
  }

  public String getKeycloakGroupId() {
    return keycloakGroupId;
  }
}
