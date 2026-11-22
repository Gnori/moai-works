package com.moai.works.api.issue;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.ColumnTransformer;

import com.moai.works.api.common.Ids;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 일감 이력 한 줄 — 고칠 때마다 한 줄(필드 변경), 댓글은 notes 만 있는 줄. 덮어쓰지 않는다 (design/05).
 * moai-wiki 의 페이지 리비전 자리다.
 */
@Entity
@Table(name = "issue_journal")
public class IssueJournal {

  public enum Via {
    WEB,
    MCP
  }

  @Id
  private UUID id;

  @Column(name = "issue_id", nullable = false)
  private UUID issueId;

  /** 사람이면 그 사람, 서비스(MCP 서비스 키)면 null */
  @Column(name = "actor_id")
  private UUID actorId;

  @Column(name = "actor_type", nullable = false, length = 20)
  private String actorType;

  @Column(name = "actor_name", nullable = false, length = 200)
  private String actorName;

  @Column(nullable = false, length = 10)
  private String via;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(columnDefinition = "text")
  private String notes;

  /** [{"field","before","after"}] */
  @Column(nullable = false, columnDefinition = "jsonb")
  @ColumnTransformer(write = "?::jsonb")
  private String changes;

  /** 이 줄이 만든 판 번호 — 댓글 · 연결 · 작업 시간만 있는 줄은 null */
  @Column(name = "revision_no")
  private Integer revisionNo;

  protected IssueJournal() {
  }

  public IssueJournal(UUID issueId, UUID actorId, String actorType, String actorName, Via via, Instant now,
      String notes, String changesJson) {
    this.id = Ids.next();
    this.issueId = issueId;
    this.actorId = actorId;
    this.actorType = actorType;
    this.actorName = actorName;
    this.via = via.name();
    this.createdAt = now;
    this.notes = notes;
    this.changes = changesJson;
  }

  public UUID getId() {
    return id;
  }

  public UUID getIssueId() {
    return issueId;
  }

  public UUID getActorId() {
    return actorId;
  }

  public String getActorType() {
    return actorType;
  }

  public String getActorName() {
    return actorName;
  }

  public String getVia() {
    return via;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public String getNotes() {
    return notes;
  }

  public String getChanges() {
    return changes;
  }

  public Integer getRevisionNo() {
    return revisionNo;
  }

  void setRevisionNo(Integer revisionNo) {
    this.revisionNo = revisionNo;
  }
}
