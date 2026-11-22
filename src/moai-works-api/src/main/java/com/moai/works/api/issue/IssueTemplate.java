package com.moai.works.api.issue;

import java.time.Instant;
import java.util.UUID;

import com.moai.works.api.common.Ids;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * 유형별 일감 템플릿 한 벌 — 워크스페이스 기본({@code projectId == null}) 또는 프로젝트 덮어쓰기.
 * 고르는 순서는 프로젝트 → 워크스페이스 → 코드의 처음 템플릿({@link IssueTemplateService#BUILTIN})이다.
 */
@Entity
@Table(name = "issue_template")
public class IssueTemplate {

  @Id
  private UUID id;

  @Column(name = "workspace_id", nullable = false)
  private UUID workspaceId;

  @Column(name = "project_id")
  private UUID projectId;

  @Column(name = "issue_type", nullable = false, length = 30)
  private String issueType;

  @Column(nullable = false, columnDefinition = "text")
  private String body;

  @Column(name = "updated_by")
  private UUID updatedBy;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(name = "row_version", nullable = false)
  private int rowVersion;

  protected IssueTemplate() {
  }

  public IssueTemplate(UUID workspaceId, UUID projectId, String issueType, String body, UUID updatedBy, Instant now) {
    this.id = Ids.next();
    this.workspaceId = workspaceId;
    this.projectId = projectId;
    this.issueType = issueType;
    this.body = body;
    this.updatedBy = updatedBy;
    this.updatedAt = now;
  }

  void change(String body, UUID updatedBy, Instant now) {
    this.body = body;
    this.updatedBy = updatedBy;
    this.updatedAt = now;
  }

  public UUID getId() {
    return id;
  }

  public UUID getWorkspaceId() {
    return workspaceId;
  }

  public UUID getProjectId() {
    return projectId;
  }

  public String getIssueType() {
    return issueType;
  }

  public String getBody() {
    return body;
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
