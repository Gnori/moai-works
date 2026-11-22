package com.moai.works.api.issue;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.moai.works.api.common.Ids;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 일감의 한 판 — 그 시점의 일감 전체와 누가 · 어느 길로 바꿨는지 (design/05). moai-wiki 의 page_revision 자리다.
 * 추가만 한다(DB 트리거가 고치기 · 지우기를 막는다).
 */
@Entity
@Table(name = "issue_revision")
public class IssueRevision {

  @Id
  private UUID id;

  @Column(name = "issue_id", nullable = false)
  private UUID issueId;

  @Column(name = "revision_no", nullable = false)
  private int revisionNo;

  @Column(name = "journal_id")
  private UUID journalId;

  @Column(name = "issue_type", nullable = false, length = 30)
  private String issueType;

  @Column(nullable = false, length = 200)
  private String title;

  @Column(nullable = false, columnDefinition = "text")
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private Issue.Status status;

  @Column(name = "manager_id", nullable = false)
  private UUID managerId;

  @Column(name = "assignee_id")
  private UUID assigneeId;

  @Column(name = "parent_number")
  private Integer parentNumber;

  @Column(name = "due_date")
  private LocalDate dueDate;

  @Column(name = "start_date")
  private LocalDate startDate;

  @Column(name = "planned_end_date")
  private LocalDate plannedEndDate;

  @Column(name = "completed_on")
  private LocalDate completedOn;

  @Column(name = "closed_on")
  private LocalDate closedOn;

  @Column(name = "estimated_hours", precision = 7, scale = 2)
  private BigDecimal estimatedHours;

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

  protected IssueRevision() {
  }

  /** 지금 일감의 모습을 한 판으로 담는다 */
  static IssueRevision of(Issue i, int revisionNo, UUID journalId, Integer parentNumber, UUID actorId, String actorType,
      String actorName, IssueJournal.Via via, Instant now) {
    IssueRevision r = new IssueRevision();
    r.id = Ids.next();
    r.issueId = i.getId();
    r.revisionNo = revisionNo;
    r.journalId = journalId;
    r.issueType = i.getIssueType();
    r.title = i.getTitle();
    r.description = i.getDescription();
    r.status = i.getStatus();
    r.managerId = i.getManagerId();
    r.assigneeId = i.getAssigneeId();
    r.parentNumber = parentNumber;
    r.dueDate = i.getDueDate();
    r.startDate = i.getStartDate();
    r.plannedEndDate = i.getPlannedEndDate();
    r.completedOn = i.getCompletedOn();
    r.closedOn = i.getClosedOn();
    r.estimatedHours = i.getEstimatedHours();
    r.actorId = actorId;
    r.actorType = actorType;
    r.actorName = actorName;
    r.via = via.name();
    r.createdAt = now;
    return r;
  }

  public UUID getId() {
    return id;
  }

  public UUID getIssueId() {
    return issueId;
  }

  public int getRevisionNo() {
    return revisionNo;
  }

  public UUID getJournalId() {
    return journalId;
  }

  public String getIssueType() {
    return issueType;
  }

  public String getTitle() {
    return title;
  }

  public String getDescription() {
    return description;
  }

  public Issue.Status getStatus() {
    return status;
  }

  public UUID getManagerId() {
    return managerId;
  }

  public UUID getAssigneeId() {
    return assigneeId;
  }

  public Integer getParentNumber() {
    return parentNumber;
  }

  public LocalDate getDueDate() {
    return dueDate;
  }

  public LocalDate getStartDate() {
    return startDate;
  }

  public LocalDate getPlannedEndDate() {
    return plannedEndDate;
  }

  public LocalDate getCompletedOn() {
    return completedOn;
  }

  public LocalDate getClosedOn() {
    return closedOn;
  }

  public BigDecimal getEstimatedHours() {
    return estimatedHours;
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
}
