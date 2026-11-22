package com.moai.works.api.issue;

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
import jakarta.persistence.Version;

/**
 * 일감 — 프로젝트 안의 할 일 하나 (design/05). moai-wiki 의 페이지 자리다.
 * 번호는 프로젝트 안 순번이고, 상위 일감은 같은 프로젝트여야 한다(DB 의 FK 가 막는다).
 */
@Entity
@Table(name = "issue")
public class Issue {

  /** 상태 — 고정 4개. 아무 상태로나 옮길 수 있다 (design/05) */
  public enum Status {
    /** 신규 */
    NEW,
    /** 진행 */
    IN_PROGRESS,
    /** 완료 — 완료일을 채운다 */
    DONE,
    /** 종료 — 종료일을 채운다 */
    CLOSED;

    public boolean isOpen() {
      return this == NEW || this == IN_PROGRESS;
    }
  }

  @Id
  private UUID id;

  @Column(name = "project_id", nullable = false)
  private UUID projectId;

  @Column(nullable = false)
  private int number;

  @Column(name = "issue_type", nullable = false, length = 30)
  private String issueType;

  @Column(nullable = false, length = 200)
  private String title;

  @Column(nullable = false, columnDefinition = "text")
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private Status status;

  /** 관리자 */
  @Column(name = "manager_id", nullable = false)
  private UUID managerId;

  /** 담당자 (없어도 된다) */
  @Column(name = "assignee_id")
  private UUID assigneeId;

  /** 상위 일감 */
  @Column(name = "parent_id")
  private UUID parentId;

  /** 완료 기한 */
  @Column(name = "due_date")
  private LocalDate dueDate;

  /** 시작일 */
  @Column(name = "start_date")
  private LocalDate startDate;

  /** 완료 예정일 */
  @Column(name = "planned_end_date")
  private LocalDate plannedEndDate;

  /** 완료일 */
  @Column(name = "completed_on")
  private LocalDate completedOn;

  /** 종료일 */
  @Column(name = "closed_on")
  private LocalDate closedOn;

  @Column(name = "created_by", nullable = false)
  private UUID createdBy;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "deleted_at")
  private Instant deletedAt;

  @Version
  @Column(name = "row_version", nullable = false)
  private int rowVersion;

  protected Issue() {
  }

  public Issue(UUID projectId, int number, String issueType, String title, String description, UUID managerId,
      UUID createdBy, Instant now) {
    this.id = Ids.next();
    this.projectId = projectId;
    this.number = number;
    this.issueType = issueType;
    this.title = title;
    this.description = description;
    this.status = Status.NEW;
    this.managerId = managerId;
    this.createdBy = createdBy;
    this.createdAt = now;
    this.updatedAt = now;
  }

  public void touch(Instant now) {
    this.updatedAt = now;
  }

  public void markDeleted(Instant now) {
    this.deletedAt = now;
    this.updatedAt = now;
  }

  public boolean isDeleted() {
    return deletedAt != null;
  }

  // 값 바꾸기 — 규칙 검사와 이력은 IssueService 가 한다

  void setIssueType(String issueType) {
    this.issueType = issueType;
  }

  void setTitle(String title) {
    this.title = title;
  }

  void setDescription(String description) {
    this.description = description;
  }

  void setStatus(Status status) {
    this.status = status;
  }

  void setManagerId(UUID managerId) {
    this.managerId = managerId;
  }

  void setAssigneeId(UUID assigneeId) {
    this.assigneeId = assigneeId;
  }

  void setParentId(UUID parentId) {
    this.parentId = parentId;
  }

  void setDueDate(LocalDate dueDate) {
    this.dueDate = dueDate;
  }

  void setStartDate(LocalDate startDate) {
    this.startDate = startDate;
  }

  void setPlannedEndDate(LocalDate plannedEndDate) {
    this.plannedEndDate = plannedEndDate;
  }

  void setCompletedOn(LocalDate completedOn) {
    this.completedOn = completedOn;
  }

  void setClosedOn(LocalDate closedOn) {
    this.closedOn = closedOn;
  }

  public UUID getId() {
    return id;
  }

  public UUID getProjectId() {
    return projectId;
  }

  public int getNumber() {
    return number;
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

  public Status getStatus() {
    return status;
  }

  public UUID getManagerId() {
    return managerId;
  }

  public UUID getAssigneeId() {
    return assigneeId;
  }

  public UUID getParentId() {
    return parentId;
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

  public UUID getCreatedBy() {
    return createdBy;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public int getRowVersion() {
    return rowVersion;
  }
}
