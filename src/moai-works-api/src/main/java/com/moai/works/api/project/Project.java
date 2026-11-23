package com.moai.works.api.project;

import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

import com.moai.works.api.common.Ids;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * 프로젝트 — 워크스페이스 안의 일감 묶음이자 <b>읽기 권한의 경계</b>다(design/04). moai-wiki 의 위키 자리다.
 * 한 프로젝트의 독자는 그 프로젝트의 일감을 모두 읽는다.
 */
@Entity
@Table(name = "project")
public class Project {

  /** 주소 이름 규칙: 영문 소문자로 시작, 소문자 · 숫자 · 하이픈, 2~32자, 하이픈으로 끝나지 않음 */
  public static final Pattern KEY_PATTERN = Pattern.compile("^[a-z][a-z0-9-]{0,30}[a-z0-9]$");

  public enum Visibility {
    /** 워크스페이스의 활성 멤버 전원이 독자 */
    WORKSPACE,
    /** 지정 멤버만 — 프로젝트 역할을 받은 멤버와 소유자 · 관리자 */
    RESTRICTED
  }

  public enum Status {
    ACTIVE,
    /** 보관 — 독자에게 보이지 않는다. 관리하는 사람이 복원하거나 지운다 */
    ARCHIVED,
    /** 지움 — 누구에게도 보이지 않는다. 보존 기간 뒤 행을 지운다 */
    DELETED
  }

  @Id
  private UUID id;

  @Column(name = "workspace_id", nullable = false)
  private UUID workspaceId;

  /** 주소 이름. 워크스페이스 안에서 하나이고 바꾸지 않는다 */
  @Column(name = "key", nullable = false, length = 32)
  private String key;

  @Column(nullable = false, length = 100)
  private String name;

  @Column(length = 1000)
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private Visibility visibility;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private Status status;

  @Column(name = "created_by", nullable = false)
  private UUID createdBy;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "archived_at")
  private Instant archivedAt;

  @Column(name = "deleted_at")
  private Instant deletedAt;

  @Version
  @Column(name = "row_version", nullable = false)
  private int rowVersion;

  protected Project() {
  }

  public Project(UUID workspaceId, String key, String name, String description, Visibility visibility, UUID createdBy,
      Instant now) {
    if (!KEY_PATTERN.matcher(key).matches()) {
      throw new IllegalArgumentException("프로젝트 주소 이름은 영문 소문자로 시작하는 2~32자(소문자 · 숫자 · 하이픈)여야 합니다: " + key);
    }
    this.id = Ids.next();
    this.workspaceId = workspaceId;
    this.key = key;
    this.name = name;
    this.description = description;
    this.visibility = visibility;
    this.status = Status.ACTIVE;
    this.createdBy = createdBy;
    this.createdAt = now;
    this.updatedAt = now;
  }

  public void updateSettings(String name, String description, Visibility visibility, Instant now) {
    this.name = name;
    this.description = description;
    this.visibility = visibility;
    this.updatedAt = now;
  }

  public void archive(Instant now) {
    requireStatus(Status.ACTIVE);
    this.status = Status.ARCHIVED;
    this.archivedAt = now;
    this.updatedAt = now;
  }

  public void restore(Instant now) {
    requireStatus(Status.ARCHIVED);
    this.status = Status.ACTIVE;
    this.archivedAt = null;
    this.updatedAt = now;
  }

  /** 지운다 — 보관한 프로젝트만 (FR-PRJ-005) */
  public void delete(Instant now) {
    requireStatus(Status.ARCHIVED);
    this.status = Status.DELETED;
    this.deletedAt = now;
    this.updatedAt = now;
  }

  private void requireStatus(Status... allowed) {
    for (Status s : allowed) {
      if (s == status) {
        return;
      }
    }
    throw new IllegalStateException("프로젝트 상태가 " + status + " 라서 할 수 없습니다");
  }

  /** 독자에게 보이는 상태인가 — 보관한 프로젝트는 관리하는 사람에게만, 지운 것은 아무에게도 보이지 않는다 */
  public boolean isOpenToReaders() {
    return status == Status.ACTIVE;
  }

  public UUID getId() {
    return id;
  }

  public UUID getWorkspaceId() {
    return workspaceId;
  }

  public String getKey() {
    return key;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public Visibility getVisibility() {
    return visibility;
  }

  public Status getStatus() {
    return status;
  }

  public UUID getCreatedBy() {
    return createdBy;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public Instant getArchivedAt() {
    return archivedAt;
  }
}
