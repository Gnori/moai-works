package com.moai.works.api.issue;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 일감 API 응답 모양. 엔티티를 그대로 내보내지 않는다. */
public final class IssueViews {

  private IssueViews() {
  }

  /** 사람 한 명 (관리자 · 담당자 · 만든 사람) */
  public record Person(UUID id, String username, String displayName) {
  }

  public record Type(String key, String name, String description) {
  }

  /** 다른 일감을 가리킬 때 (상위 · 하위 · 연결) */
  public record Ref(int number, String title, Issue.Status status, String type) {
  }

  /** 목록 한 줄 */
  /**
   * 목록 한 줄.
   *
   * @param childCount 바로 아래 하위 일감 수
   * @param overdue    기한 지남 — 열린 일감이고 완료 기한이 오늘보다 앞
   */
  public record Summary(int number, String type, String title, Issue.Status status, Person manager, Person assignee,
      Integer parent, LocalDate dueDate, LocalDate startDate, LocalDate plannedEndDate, LocalDate completedOn,
      LocalDate closedOn, Instant updatedAt, int childCount, boolean overdue, BigDecimal estimatedHours,
      BigDecimal spentHours) {
  }

  public record Page(List<Summary> items, long total, int page, int size) {
  }

  /**
   * 연결 하나 — 이 일감 쪽에서 본 모양.
   *
   * @param direction OUTGOING(이 일감이 source) · INCOMING(이 일감이 target). RELATES 는 방향이 없지만 그대로 적는다
   */
  public record Link(UUID id, IssueLink.Type type, String direction, Ref issue) {
  }

  /** 이력 한 줄. changes 는 [{field, before, after}] — 사람은 {id, name}, 상위 일감은 번호, 연결은 {type, direction, number} */
  public record Journal(UUID id, String actorType, UUID actorId, String actorName, String via, Instant createdAt,
      String notes, List<Map<String, Object>> changes, Integer revisionNo) {
  }

  public record Detail(int number, String type, String title, String description, Issue.Status status,
      Person manager, Person assignee, Ref parent, LocalDate dueDate, LocalDate startDate, LocalDate plannedEndDate,
      LocalDate completedOn, LocalDate closedOn, Person createdBy, Instant createdAt, Instant updatedAt,
      int rowVersion, boolean overdue, int revisionNo, BigDecimal estimatedHours, BigDecimal spentHours,
      List<Ref> children, List<Link> links, List<Journal> journal, List<WorkLog> workLogs) {
  }

  /** 리비전 목록 한 줄 — 누가 · 어느 길로 · 언제 · 무엇을 바꿨나 */
  public record RevisionItem(int no, String actorType, UUID actorId, String actorName, String via, Instant createdAt,
      List<String> changedFields, boolean current) {
  }

  /** @param next 다음 쪽을 부를 때 before 에 넣는 판 번호. 마지막 쪽이면 null */
  public record RevisionList(List<RevisionItem> items, Integer next) {
  }

  /** 한 판의 일감 전체 */
  public record Revision(int no, String actorType, UUID actorId, String actorName, String via, Instant createdAt,
      String type, String title, String description, Issue.Status status, Person manager, Person assignee,
      Integer parent, LocalDate dueDate, LocalDate startDate, LocalDate plannedEndDate, LocalDate completedOn,
      LocalDate closedOn, BigDecimal estimatedHours, boolean current) {
  }

  /**
   * 두 판의 차이 — 필드는 전 · 후, 설명은 git 처럼 줄 단위(LineDiff).
   *
   * @param from 옛 쪽 판 번호 (1판과 "없음"을 견주면 null)
   */
  public record RevisionDiff(Integer from, int to, RevisionItem fromInfo, RevisionItem toInfo,
      List<Map<String, Object>> fields, List<LineDiff.Line> descriptionLines, int added, int removed, boolean coarse) {
  }

  /** 작업 시간 기록 한 줄 — 누가 · 어느 날 · 몇 시간 · 무엇을 · 어느 길로 */
  public record WorkLog(UUID id, Person user, LocalDate workDate, BigDecimal hours, String note, String via,
      Instant createdAt) {
  }
}
