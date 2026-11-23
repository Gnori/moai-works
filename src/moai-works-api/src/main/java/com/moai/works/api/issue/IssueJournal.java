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
 *
 * <p>댓글(notes)만은 그 자리에서 고치고 지운다(V14, FR-ISS-019) — 고치면 앞 글을 {@link IssueCommentRevision} 에 남기고 판
 * (notesRowVersion)을 올린다. 지우면 지움 표시(누가 · 언제)만 하고 글은 남기되 더 내주지 않는다. 나머지 값은 DB 트리거가 지킨다.
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

  /** 댓글 글의 판 — 0 = 처음 단 글, 고칠 때마다 1 올린다(동시 수정 검사) */
  @Column(name = "notes_row_version", nullable = false)
  private int notesRowVersion;

  /** 댓글을 마지막으로 고친 때 — 고친 적이 없으면 null */
  @Column(name = "notes_edited_at")
  private Instant notesEditedAt;

  @Column(name = "notes_deleted_at")
  private Instant notesDeletedAt;

  @Column(name = "notes_deleted_by")
  private UUID notesDeletedBy;

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

  /** 댓글이 있는 줄인가 — 지운 댓글도 댓글이다(글은 DB 에 남는다) */
  public boolean isComment() {
    return notes != null;
  }

  public int getNotesRowVersion() {
    return notesRowVersion;
  }

  public Instant getNotesEditedAt() {
    return notesEditedAt;
  }

  public Instant getNotesDeletedAt() {
    return notesDeletedAt;
  }

  public UUID getNotesDeletedBy() {
    return notesDeletedBy;
  }

  public boolean isNotesDeleted() {
    return notesDeletedAt != null;
  }

  /** 밖으로 내줄 댓글 글 — 지운 댓글은 null(글은 DB 에만 남는다) */
  public String visibleNotes() {
    return isNotesDeleted() ? null : notes;
  }

  /** 댓글 글을 바꾼다 — 앞 글은 부르는 쪽이 먼저 {@link IssueCommentRevision} 에 남긴다(트리거가 확인한다) */
  void editNotes(String text, Instant now) {
    this.notes = text;
    this.notesRowVersion++;
    this.notesEditedAt = now;
  }

  void markNotesDeleted(UUID by, Instant now) {
    this.notesDeletedAt = now;
    this.notesDeletedBy = by;
  }
}
