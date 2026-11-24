package com.moai.works.api.issue;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.moai.works.api.common.Ids;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 작업 시간 기록 한 줄 — 누가 · 어느 날 · 몇 시간 · 무엇을 (design/05). 지우면 지움 표시만 한다 */
@Entity
@Table(name = "issue_work_log")
public class IssueWorkLog {

  @Id
  private UUID id;

  @Column(name = "issue_id", nullable = false)
  private UUID issueId;

  /** 일한 사람 = 기록한 사람 */
  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "work_date", nullable = false)
  private LocalDate workDate;

  @Column(nullable = false, precision = 5, scale = 2)
  private BigDecimal hours;

  @Column(length = 1000)
  private String note;

  @Column(nullable = false, length = 10)
  private String via;

  /** 사람이 한 일의 시간인가 — 웹에서 적음 · MCP 로 사용자가 알려 준 시간(V27, 일감 #204). Agent 가 MCP 로 적은 제 일의 시간은 false */
  @Column(name = "by_human", nullable = false)
  private boolean byHuman;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "deleted_at")
  private Instant deletedAt;

  @Column(name = "deleted_by")
  private UUID deletedBy;

  protected IssueWorkLog() {
  }

  public IssueWorkLog(UUID issueId, UUID userId, LocalDate workDate, BigDecimal hours, String note,
      IssueJournal.Via via, boolean byHuman, Instant now) {
    this.id = Ids.next();
    this.issueId = issueId;
    this.userId = userId;
    this.workDate = workDate;
    this.hours = hours;
    this.note = note;
    this.via = via.name();
    this.byHuman = byHuman;
    this.createdAt = now;
  }

  void markDeleted(UUID by, Instant now) {
    this.deletedAt = now;
    this.deletedBy = by;
  }

  public boolean isDeleted() {
    return deletedAt != null;
  }

  public UUID getId() {
    return id;
  }

  public UUID getIssueId() {
    return issueId;
  }

  public UUID getUserId() {
    return userId;
  }

  public LocalDate getWorkDate() {
    return workDate;
  }

  public BigDecimal getHours() {
    return hours;
  }

  public String getNote() {
    return note;
  }

  public String getVia() {
    return via;
  }

  public boolean isByHuman() {
    return byHuman;
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
