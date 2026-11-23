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

  /** @param active 새 일감에서 고를 수 있는가(false = 더 쓰지 않는 옛 유형 — 이름만 보인다) */
  public record Type(String key, String name, String description, boolean active) {
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

  /**
   * 목록 한 쪽 — 다음 쪽이 있는지(hasNext)와 전체 쪽 수를 함께 준다(2026-11-22 사용자: "다음 페이지가 있는지 없는지 알아야 조회를 제대로 하겠지").
   *
   * @param page 0 부터 · {@code totalPages} 전체 쪽 수(없으면 0) · {@code hasNext} 다음 쪽이 있는가
   */
  public record Page(List<Summary> items, long total, int page, int size, int totalPages, boolean hasNext) {
  }

  /**
   * 연결 하나 — 이 일감 쪽에서 본 모양.
   *
   * @param direction OUTGOING(이 일감이 source) · INCOMING(이 일감이 target). RELATES 는 방향이 없지만 그대로 적는다
   */
  public record Link(UUID id, IssueLink.Type type, String direction, Ref issue) {
  }

  /**
   * 이력 한 줄. changes 는 [{field, before, after}] — 사람은 {id, name}, 상위 일감은 번호, 연결은 {type, direction, number},
   * 댓글 고치기 · 지우기는 {@code comment} — {id, author{id, name}}(지우면 after 가 null).
   *
   * <p>댓글이 있는 줄(지운 댓글 포함)만 아래 값이 있다(FR-ISS-019):
   *
   * @param notes     댓글 Markdown — 지운 댓글은 null(글을 더 내주지 않는다)
   * @param rowVersion 댓글 글의 판 — 고칠 때 보낸다(다르면 409 CONCURRENT_UPDATE). 댓글이 없는 줄은 null
   * @param editedAt  마지막으로 고친 때(밀리초) — 고친 적이 없으면 null
   * @param deleted   지운 댓글("지운 댓글"로 제자리에 남는다)
   * @param deletedBy 지운 사람 — 쓴 사람이 아니면 project.manage 가 있는 사람
   */
  public record Journal(UUID id, String actorType, UUID actorId, String actorName, String via, Instant createdAt,
      String notes, List<Map<String, Object>> changes, Integer revisionNo, Integer rowVersion, Instant editedAt,
      boolean deleted, Instant deletedAt, Person deletedBy) {
  }

  /**
   * 댓글 하나 — 댓글 목록(쪽 나누기 — REST · MCP list_comments). 댓글 id 는 그 이력 줄의 id 다.
   *
   * @param notes      댓글 Markdown — 지운 댓글은 null
   * @param rowVersion 고칠 때 보내는 댓글 글의 판
   * @param withChanges 일감을 고치면서 함께 단 댓글인가(같은 이력 줄에 필드 변경이 있다)
   */
  public record Comment(UUID id, String actorType, UUID actorId, String actorName, String via, Instant createdAt,
      String notes, int rowVersion, Instant editedAt, boolean deleted, Instant deletedAt, Person deletedBy,
      boolean withChanges) {
  }

  /** 댓글 한 쪽 — 오래된 것부터. {@code page} 0 부터 · {@code hasNext} 다음 쪽이 있는가 */
  public record CommentPage(int number, List<Comment> items, long total, int page, int size, int totalPages,
      boolean hasNext) {
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
