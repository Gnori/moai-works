package com.moai.works.api.issue;

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
  public record Summary(int number, String type, String title, Issue.Status status, Person manager, Person assignee,
      Integer parent, LocalDate dueDate, LocalDate startDate, LocalDate plannedEndDate, LocalDate completedOn,
      LocalDate closedOn, Instant updatedAt) {
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

  /** 이력 한 줄. changes 는 [{field, before, after}] — 사람은 {id, name}, 상위 일감은 번호 */
  public record Journal(UUID id, String actorType, UUID actorId, String actorName, String via, Instant createdAt,
      String notes, List<Map<String, Object>> changes) {
  }

  public record Detail(int number, String type, String title, String description, Issue.Status status,
      Person manager, Person assignee, Ref parent, LocalDate dueDate, LocalDate startDate, LocalDate plannedEndDate,
      LocalDate completedOn, LocalDate closedOn, Person createdBy, Instant createdAt, Instant updatedAt,
      int rowVersion, List<Ref> children, List<Link> links, List<Journal> journal) {
  }
}
