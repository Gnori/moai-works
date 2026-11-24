package com.moai.works.api.issue;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
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
 * 일감 — 워크스페이스 안의 할 일 하나 (design/05). moai-wiki 의 페이지 자리다.
 * 일감은 워크스페이스 아래에 있고 프로젝트는 일감의 속성이다(옮길 수 있다 — Redmine 의 Project 칸, ADR-030).
 * 번호는 워크스페이스 안 순번이라 지운 일감 것까지 다시 쓰지 않는다. 상위 일감은 같은 프로젝트여야 하고(DB 의 FK 가 막는다),
 * 프로젝트는 같은 워크스페이스여야 한다(복합 FK).
 */
@Entity
@Table(name = "issue")
public class Issue {

  /**
   * 상태 — 고정 11개, 이 차례가 목록 · 정렬 · 화면의 차례다(등록 · 요구사항 확인요청 · 요구사항 확인완료 · 진행 · 테스트 · 확인요청 · 확인완료 ·
   * 보류 · 완료 · 종료 · 취소). 아무 상태로나 옮길 수 있다 (design/05) — 단 확인완료 둘은 관리자만 웹에서(IssueService).
   * 열린 일감 = 등록 · 확인 넷 · 진행 · 테스트 · 보류, 닫힌 일감 = 완료 · 종료 · 취소. 보류 · 취소 · 확인요청으로 옮길 때는 까닭(댓글)이 있어야 한다
   * (2026-11-24 사용자: "이슈 상태에 취소, 보류도 만들어" — moai-works 일감 #152 ·
   * 2026-11-25 사용자: "이슈 상태에 확인요청, 확인완료 상태 추가하자" · "요구사항 확인요청, 요구사항 확인완료 2가지 건도 더 추가해" — 일감 #272)
   */
  public enum Status {
    /** 등록 (2026-11-22 화면 이름 "신규" → "등록") */
    NEW("등록"),
    /**
     * 요구사항 확인요청 — 열린 상태. 일을 시작하기 전에 Agent · 담당자가 이해한 요구사항을 적고 관리자에게 맞는지 확인해 달라고 한다.
     * 무엇을 확인할지(까닭)가 있어야 옮긴다. 고를 수 있는 단계다(등록 → 진행도 그대로 된다)
     */
    REQUIREMENT_REVIEW("요구사항 확인요청"),
    /** 요구사항 확인완료 — 열린 상태. 관리자가 요구사항을 확인했다 → 진행(IN_PROGRESS)으로 시작한다. 관리자(또는 project.manage)만 웹에서 옮긴다 */
    REQUIREMENT_CONFIRMED("요구사항 확인완료"),
    /** 진행 */
    IN_PROGRESS("진행"),
    /** 테스트 — 열린 상태. 날짜를 채우지 않는다 (2026-11-22 사용자: "상태 : 등록, 진행, 테스트, 완료, 종료") */
    TESTING("테스트"),
    /**
     * 확인요청 — 열린 상태. 일을 마친 뒤(보통 테스트에서) 관리자에게 결과를 확인해 달라고 한다. 무엇을 확인할지(까닭)가 있어야 옮긴다.
     * 고를 수 있는 단계다(테스트 → 완료도 그대로 된다)
     */
    REVIEW("확인요청"),
    /** 확인완료 — 열린 상태. 관리자가 결과를 확인했다 → 완료(DONE)로 끝낸다. 관리자(또는 project.manage)만 웹에서 옮긴다 */
    CONFIRMED("확인완료"),
    /** 보류 — 열린 상태. 사람의 결정 · 다른 일을 기다리며 멈춤. 날짜를 채우지 않고 기한 지남은 그대로 센다. 까닭이 있어야 옮긴다 */
    ON_HOLD("보류"),
    /** 완료 — 완료일을 채운다 */
    DONE("완료"),
    /** 종료 — 완료한 일을 사람이 확인해 닫음. 종료일을 채운다 */
    CLOSED("종료"),
    /** 취소 — 닫힌 상태. 하지 않기로 함 · 중복. 종료일을 채우고 완료일은 비운다. 까닭이 있어야 옮긴다 */
    CANCELLED("취소");

    /** 오류 문구 · 도구 설명에 쓰는 값 목록 */
    public static final String CODES = "NEW · REQUIREMENT_REVIEW · REQUIREMENT_CONFIRMED · IN_PROGRESS · TESTING · REVIEW · CONFIRMED · "
        + "ON_HOLD · DONE · CLOSED · CANCELLED";

    private final String label;

    Status(String label) {
      this.label = label;
    }

    /** 한국어 이름 — 오류 문구 · MCP 길잡이 */
    public String label() {
      return label;
    }

    /** 열린 일감인가 — 목록의 open 묶음 · 기한 지남 · 아침 알림. 닫힌 것은 완료 · 종료 · 취소뿐이다 */
    public boolean isOpen() {
      return this != DONE && this != CLOSED && this != CANCELLED;
    }

    /** 이 상태로 옮길 때 까닭(댓글)이 있어야 하는가 — 보류 · 취소 · 확인요청 둘(무엇을 확인할지) */
    public boolean needsReason() {
      return this == ON_HOLD || this == CANCELLED || isReview();
    }

    /** 관리자 확인을 기다리는 상태인가 — 요구사항 확인요청 · 확인요청 */
    public boolean isReview() {
      return this == REQUIREMENT_REVIEW || this == REVIEW;
    }

    /** 관리자가 확인한 상태인가 — 요구사항 확인완료 · 확인완료. 관리자(또는 project.manage)만 웹에서 옮긴다 */
    public boolean isConfirmation() {
      return this == REQUIREMENT_CONFIRMED || this == CONFIRMED;
    }

    /** 확인요청의 짝인 확인완료 — 요구사항 확인요청 → 요구사항 확인완료, 확인요청 → 확인완료. 확인요청이 아니면 null */
    public Status confirmation() {
      return switch (this) {
        case REQUIREMENT_REVIEW -> REQUIREMENT_CONFIRMED;
        case REVIEW -> CONFIRMED;
        default -> null;
      };
    }

    /**
     * 확인요청에서 그 짝인 확인완료가 아닌 곳으로 옮기는가 — 관리자의 반려 · 요청한 사람의 거둠. 까닭이 있어야 한다
     * (요구사항 확인요청 → 등록 · 진행, 확인요청 → 진행 등)
     */
    public static boolean returnsReview(Status from, Status to) {
      return from != null && to != null && from != to && from.isReview() && to != from.confirmation();
    }

    /** 열린 상태들 (차례대로) */
    public static List<Status> open() {
      return Arrays.stream(values()).filter(Status::isOpen).toList();
    }

    /** 닫힌 상태들 (차례대로) */
    public static List<Status> closed() {
      return Arrays.stream(values()).filter(s -> !s.isOpen()).toList();
    }
  }

  @Id
  private UUID id;

  /** 일감이 속한 워크스페이스 — 바뀌지 않는다 */
  @Column(name = "workspace_id", nullable = false, updatable = false)
  private UUID workspaceId;

  /** 지금 프로젝트 — 권한 경계. 옮기면 바뀐다(IssueService 의 옮기기) */
  @Column(name = "project_id", nullable = false)
  private UUID projectId;

  /** 워크스페이스 안 번호 (#1, #2 …) */
  @Column(nullable = false, updatable = false)
  private int number;

  @Column(name = "issue_type", nullable = false, length = 30)
  private String issueType;

  @Column(nullable = false, length = 200)
  private String title;

  @Column(nullable = false, columnDefinition = "text")
  private String description;

  /** 상태 — 'REQUIREMENT_CONFIRMED'(21자)가 들어가게 30자(V28) */
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private Status status;

  /**
   * 상태의 차례(정렬용, 읽기만) — 등록 1 · 요구사항 확인요청 2 · 요구사항 확인완료 3 · 진행 4 · 테스트 5 · 확인요청 6 · 확인완료 7 · 보류 8 ·
   * 완료 9 · 종료 10 · 취소 11
   */
  @org.hibernate.annotations.Formula("CASE status WHEN 'NEW' THEN 1 WHEN 'REQUIREMENT_REVIEW' THEN 2 WHEN 'REQUIREMENT_CONFIRMED' THEN 3 "
      + "WHEN 'IN_PROGRESS' THEN 4 WHEN 'TESTING' THEN 5 WHEN 'REVIEW' THEN 6 WHEN 'CONFIRMED' THEN 7 WHEN 'ON_HOLD' THEN 8 "
      + "WHEN 'DONE' THEN 9 WHEN 'CLOSED' THEN 10 ELSE 11 END")
  private int statusRank;

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

  /** 예상 작업 시간(시간) */
  @Column(name = "estimated_hours", precision = 7, scale = 2)
  private BigDecimal estimatedHours;

  /**
   * 에이전트 세션 — 이 일을 맡은 AI Agent 세션의 이름(예: moai-works · moai-wiki). 없어도 된다
   * (2026-11-23 사용자: "여러 세션들이 한 프로젝트에서 작업하는데 어떤 세션의 작업인지 모르겠다")
   */
  @Column(name = "agent_session", length = 100)
  private String agentSession;

  /** 지금 판 번호 — 만들 때 1, 필드가 바뀔 때마다 하나씩 (issue_revision) */
  @Column(name = "revision_no", nullable = false)
  private int revisionNo;

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

  public Issue(UUID workspaceId, UUID projectId, int number, String issueType, String title, String description,
      UUID managerId, UUID createdBy, Instant now) {
    this.id = Ids.next();
    this.workspaceId = workspaceId;
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

  /** 다른 프로젝트로 옮긴다 — 같은 워크스페이스의 프로젝트만(DB 의 복합 FK). 판정 · 하위 · 이력은 IssueService 가 한다 */
  void setProjectId(UUID projectId) {
    this.projectId = projectId;
  }

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

  void setEstimatedHours(BigDecimal estimatedHours) {
    this.estimatedHours = estimatedHours;
  }

  void setAgentSession(String agentSession) {
    this.agentSession = agentSession;
  }

  /** 새 판 번호를 받는다 — 호출자는 같은 트랜잭션에서 그 판(issue_revision)을 남긴다 */
  int nextRevision() {
    return ++revisionNo;
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

  public BigDecimal getEstimatedHours() {
    return estimatedHours;
  }

  public String getAgentSession() {
    return agentSession;
  }

  public int getRevisionNo() {
    return revisionNo;
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
