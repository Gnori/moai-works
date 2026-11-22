package com.moai.works.api.issue;

import java.time.Instant;
import java.util.UUID;

import com.moai.works.api.common.Ids;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 일감 첨부파일의 메타데이터 — 파일 원문은 객체 저장소에만(V9). 지우기는 지움 표시만 한 번 */
@Entity
@Table(name = "issue_attachment")
public class IssueAttachment {

  @Id
  private UUID id;

  @Column(name = "workspace_id", nullable = false)
  private UUID workspaceId;

  @Column(name = "project_id", nullable = false)
  private UUID projectId;

  @Column(name = "issue_id", nullable = false)
  private UUID issueId;

  @Column(name = "file_name", nullable = false, length = 255)
  private String fileName;

  @Column(name = "content_type", nullable = false, length = 100)
  private String contentType;

  @Column(name = "size_bytes", nullable = false)
  private long sizeBytes;

  @Column(nullable = false, length = 64)
  @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.CHAR)
  private String sha256;

  @Column(nullable = false)
  private boolean image;

  @Column(name = "storage_key", nullable = false, length = 300)
  private String storageKey;

  @Column(name = "uploaded_by", nullable = false)
  private UUID uploadedBy;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private IssueJournal.Via via;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "deleted_at")
  private Instant deletedAt;

  @Column(name = "deleted_by")
  private UUID deletedBy;

  protected IssueAttachment() {
  }

  public IssueAttachment(UUID id, UUID workspaceId, UUID projectId, UUID issueId, String fileName, String contentType,
      long sizeBytes, String sha256, boolean image, String storageKey, UUID uploadedBy, IssueJournal.Via via,
      Instant createdAt) {
    this.id = id == null ? Ids.next() : id;
    this.workspaceId = workspaceId;
    this.projectId = projectId;
    this.issueId = issueId;
    this.fileName = fileName;
    this.contentType = contentType;
    this.sizeBytes = sizeBytes;
    this.sha256 = sha256;
    this.image = image;
    this.storageKey = storageKey;
    this.uploadedBy = uploadedBy;
    this.via = via;
    this.createdAt = createdAt;
  }

  public void markDeleted(UUID by, Instant now) {
    this.deletedAt = now;
    this.deletedBy = by;
  }

  public boolean isDeleted() {
    return deletedAt != null;
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

  public UUID getIssueId() {
    return issueId;
  }

  public String getFileName() {
    return fileName;
  }

  public String getContentType() {
    return contentType;
  }

  public long getSizeBytes() {
    return sizeBytes;
  }

  public String getSha256() {
    return sha256;
  }

  public boolean isImage() {
    return image;
  }

  public String getStorageKey() {
    return storageKey;
  }

  public UUID getUploadedBy() {
    return uploadedBy;
  }

  public IssueJournal.Via getVia() {
    return via;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getDeletedAt() {
    return deletedAt;
  }

  public UUID getDeletedBy() {
    return deletedBy;
  }
}
