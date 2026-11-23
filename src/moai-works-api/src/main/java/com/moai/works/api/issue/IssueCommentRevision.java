package com.moai.works.api.issue;

import java.time.Instant;
import java.util.UUID;

import com.moai.works.api.common.Ids;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 바뀌기 전 댓글 글 한 줄 — 댓글을 고칠 때마다 앞 글을 그대로 남긴다(V14, FR-ISS-019). 쌓기만 하고 DB 트리거가 고치기 · 지우기를 막는다.
 * 옛 글을 보여 주는 화면 · API 는 아직 없다 — 잃지 않게 남겨 두기만 한다.
 */
@Entity
@Table(name = "issue_comment_revision")
public class IssueCommentRevision {

  @Id
  private UUID id;

  /** 댓글 = 이력 줄 */
  @Column(name = "journal_id", nullable = false)
  private UUID journalId;

  /** 그 글이 쓰이던 판 (0 = 처음 단 글) */
  @Column(name = "row_version", nullable = false)
  private int rowVersion;

  @Column(nullable = false, columnDefinition = "text")
  private String notes;

  @Column(name = "replaced_by", nullable = false)
  private UUID replacedBy;

  @Column(name = "replaced_via", nullable = false, length = 10)
  private String replacedVia;

  @Column(name = "replaced_at", nullable = false)
  private Instant replacedAt;

  protected IssueCommentRevision() {
  }

  /** 고치기 전 모습을 남긴다 — 그 댓글의 지금 글 · 판, 고친 사람 · 길 · 때 */
  IssueCommentRevision(IssueJournal comment, UUID by, IssueJournal.Via via, Instant now) {
    this.id = Ids.next();
    this.journalId = comment.getId();
    this.rowVersion = comment.getNotesRowVersion();
    this.notes = comment.getNotes();
    this.replacedBy = by;
    this.replacedVia = via.name();
    this.replacedAt = now;
  }

  public UUID getId() {
    return id;
  }

  public UUID getJournalId() {
    return journalId;
  }

  public int getRowVersion() {
    return rowVersion;
  }

  public String getNotes() {
    return notes;
  }

  public UUID getReplacedBy() {
    return replacedBy;
  }

  public String getReplacedVia() {
    return replacedVia;
  }

  public Instant getReplacedAt() {
    return replacedAt;
  }
}
