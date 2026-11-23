package com.moai.works.api.project;

import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

import com.moai.works.api.common.Ids;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * 프로젝트 모듈 하나 — 프로젝트마다의 모듈 목록(예: api · web-backend · web-frontend · infra · docs). 일감에 여럿 붙인다(issue_module)
 * (2026-11-23 사용자, 일감 #27: "프로젝트에서 모듈을 등록해서 항목을 관리하도록 하자" · "이슈에 멀티로 관련 모듈을 등록할 수 있게 해 줘").
 *
 * <p>일감 유형처럼 지우지 않는다 — 쓰지 않음({@code active = false})으로 두면 새로 고를 수 없고 이미 붙은 일감에는 그대로 남는다.
 * key 는 바꾸지 않는다(MCP · 목록 거르기 값). DB 트리거가 지우기와 key 바꾸기를 막는다(V21).
 */
@Entity
@Table(name = "project_module")
public class ProjectModule {

  /** 영문 소문자로 시작하는 1~32자(소문자 · 숫자 · 하이픈), 하이픈으로 끝나지 않는다 — V21 의 CHECK 와 같다 */
  public static final Pattern KEY_PATTERN = Pattern.compile("^[a-z]([a-z0-9-]{0,30}[a-z0-9])?$");

  @Id
  private UUID id;

  @Column(name = "project_id", nullable = false)
  private UUID projectId;

  @Column(name = "module_key", nullable = false, length = 32)
  private String key;

  @Column(nullable = false, length = 100)
  private String name;

  @Column(length = 500)
  private String description;

  @Column(nullable = false)
  private int position;

  @Column(nullable = false)
  private boolean active;

  @Column(name = "created_by")
  private UUID createdBy;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_by")
  private UUID updatedBy;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(name = "row_version", nullable = false)
  private int rowVersion;

  protected ProjectModule() {
  }

  ProjectModule(UUID projectId, String key, String name, String description, int position, UUID by, Instant now) {
    this.id = Ids.next();
    this.projectId = projectId;
    this.key = key;
    this.name = name;
    this.description = description;
    this.position = position;
    this.active = true;
    this.createdBy = by;
    this.createdAt = now;
    this.updatedBy = by;
    this.updatedAt = now;
  }

  void rename(String name) {
    this.name = name;
  }

  void describe(String description) {
    this.description = description;
  }

  void activate(boolean active) {
    this.active = active;
  }

  void moveTo(int position) {
    this.position = position;
  }

  void touch(UUID by, Instant now) {
    this.updatedBy = by;
    this.updatedAt = now;
  }

  public UUID getId() {
    return id;
  }

  public UUID getProjectId() {
    return projectId;
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

  public int getPosition() {
    return position;
  }

  public boolean isActive() {
    return active;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public UUID getUpdatedBy() {
    return updatedBy;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public int getRowVersion() {
    return rowVersion;
  }
}
