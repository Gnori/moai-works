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

  /**
   * 일감의 프로젝트 — 일감은 워크스페이스 아래에 있고 프로젝트는 속성이다(ADR-030). 번호는 워크스페이스에서 하나라 프로젝트 없이 가리킨다.
   *
   * @param key  주소 이름 · {@code name} 지금 이름
   */
  public record ProjectRef(String key, String name) {
  }

  /** 다른 일감을 가리킬 때 (상위 · 하위 · 연결) — 연결은 다른 프로젝트일 수 있다(읽을 수 있는 것만 내준다) */
  public record Ref(int number, ProjectRef project, String title, Issue.Status status, String type) {
  }

  /**
   * 목록 한 줄.
   *
   * @param childCount 바로 아래 하위 일감 수
   * @param overdue    기한 지남 — 열린 일감이고 완료 기한이 오늘보다 앞
   * @param agentSession 이 일을 맡은 AI Agent 세션 이름 — 없으면 null
   * @param rowVersion   고칠 때 보내는 판 — 목록에서 바로 고치게(MCP search_issues 가 get_issue 를 따로 부르지 않게, 일감 #199)
   * @param statusChangedAt 지금 상태가 된 때 — {@link Detail#statusChangedAt} 와 같은 규칙(일감 #199)
   * @param priority     우선순위 — 늘 있다(비우면 NORMAL, 일감 #302)
   */
  public record Summary(int number, ProjectRef project, String type, String title, Issue.Status status,
      Issue.Priority priority, Person manager, Person assignee,
      Integer parent, LocalDate dueDate, LocalDate startDate, LocalDate plannedEndDate, LocalDate completedOn,
      LocalDate closedOn, Instant updatedAt, int childCount, boolean overdue, BigDecimal estimatedHours,
      BigDecimal spentHours, String agentSession, List<ModuleRef> modules, int rowVersion, Instant statusChangedAt) {
  }

  /** 일감에 붙은 모듈 하나 — 모듈 순서. active=false 는 쓰지 않게 된 모듈(이미 붙은 일감에는 남는다) */
  public record ModuleRef(String key, String name, boolean active) {
  }

  /**
   * 고를 에이전트 세션 이름 하나와 그 세션이 맡은 일감 수(지운 일감 빼고).
   *
   * @param registered 그 프로젝트의 에이전트 멤버인가(2026-11-25, 일감 #285) — 프로젝트 목록에만 있고 워크스페이스 목록에는 없다(null)
   * @param active     쓰는 에이전트 멤버인가 — 프로젝트 목록에만
   */
  public record AgentSession(String name, long issueCount, Boolean registered, Boolean active) {

    public AgentSession(String name, long issueCount) {
      this(name, issueCount, null, null);
    }
  }

  /** 워크스페이스 일감 목록의 모듈 거르기 칸 — 읽을 수 있는 프로젝트들의 모듈 key(같은 key 는 하나로) */
  public record ModuleOption(String key, String name) {
  }

  /** 옛 링크(프로젝트 주소 + 그 프로젝트 안 옛 번호)가 가리키는 일감의 지금 번호 */
  public record IssueNumber(int number) {
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
   *
   * <p>{@code agentSession} 은 MCP 로 쓴 줄의 에이전트 세션 이름 — 사람이 웹에서 쓴 줄은 null(V31, 일감 #304)
   */
  public record Journal(UUID id, String actorType, UUID actorId, String actorName, String via, String agentSession,
      Instant createdAt,
      String notes, List<Map<String, Object>> changes, Integer revisionNo, Integer rowVersion, Instant editedAt,
      boolean deleted, Instant deletedAt, Person deletedBy) {
  }

  /**
   * 댓글 하나 — 댓글 목록(쪽 나누기 — REST · MCP list_comments). 댓글 id 는 그 이력 줄의 id 다.
   *
   * @param notes      댓글 Markdown — 지운 댓글은 null
   * @param rowVersion 고칠 때 보내는 댓글 글의 판
   * @param withChanges 일감을 고치면서 함께 단 댓글인가(같은 이력 줄에 필드 변경이 있다)
   * @param agentSession MCP 로 단 댓글의 에이전트 세션 이름 — 사람이 웹에서 단 댓글은 null(V31, 일감 #304)
   */
  public record Comment(UUID id, String actorType, UUID actorId, String actorName, String via, String agentSession,
      Instant createdAt,
      String notes, int rowVersion, Instant editedAt, boolean deleted, Instant deletedAt, Person deletedBy,
      boolean withChanges) {
  }

  /** 댓글 한 쪽 — 오래된 것부터. {@code page} 0 부터 · {@code hasNext} 다음 쪽이 있는가 */
  public record CommentPage(int number, List<Comment> items, long total, int page, int size, int totalPages,
      boolean hasNext) {
  }

  /**
   * 일감 하나.
   *
   * @param statusChangedAt 지금 상태가 된 때 — 이력에서 지금 상태로 바뀐 마지막 줄(만들 때 줄 포함)의 시각(2026-11-24, 일감 #176).
   *                        Agent 가 "이 구간이 시작한 뒤 얼마나 지났나"를 짐작하지 않고 셈하게
   * @param workLogs        작업 시간 기록 — 지우지 않은 것만(REST · 화면). MCP get_issue 는 지운 것까지 {@code deleted} 로 보인다
   */
  public record Detail(int number, ProjectRef project, String type, String title, String description, Issue.Status status,
      Issue.Priority priority, Person manager, Person assignee, Ref parent, LocalDate dueDate, LocalDate startDate, LocalDate plannedEndDate,
      LocalDate completedOn, LocalDate closedOn, Person createdBy, Instant createdAt, Instant updatedAt,
      Instant statusChangedAt, int rowVersion, boolean overdue, int revisionNo, BigDecimal estimatedHours, BigDecimal spentHours,
      String agentSession, List<ModuleRef> modules, List<Ref> children, List<Link> links, List<Journal> journal,
      List<WorkLog> workLogs) {
  }

  /** 리비전 목록 한 줄 — 누가 · 어느 길로 · 언제 · 무엇을 바꿨나 */
  public record RevisionItem(int no, String actorType, UUID actorId, String actorName, String via, Instant createdAt,
      List<String> changedFields, boolean current) {
  }

  /** @param next 다음 쪽을 부를 때 before 에 넣는 판 번호. 마지막 쪽이면 null */
  public record RevisionList(List<RevisionItem> items, Integer next) {
  }

  /** 한 판의 일감 전체 — project 는 그 판의 프로젝트(옮기면 바뀐다) */
  public record Revision(int no, String actorType, UUID actorId, String actorName, String via, Instant createdAt,
      ProjectRef project, String type, String title, String description, Issue.Status status, Issue.Priority priority,
      Person manager, Person assignee, Integer parent, LocalDate dueDate, LocalDate startDate, LocalDate plannedEndDate, LocalDate completedOn,
      LocalDate closedOn, BigDecimal estimatedHours, String agentSession, List<Map<String, Object>> modules,
      boolean current) {
  }

  /**
   * 두 판의 차이 — 필드는 전 · 후, 설명은 git 처럼 줄 단위(LineDiff).
   *
   * @param from 옛 쪽 판 번호 (1판과 "없음"을 견주면 null)
   */
  public record RevisionDiff(Integer from, int to, RevisionItem fromInfo, RevisionItem toInfo,
      List<Map<String, Object>> fields, List<LineDiff.Line> descriptionLines, int added, int removed, boolean coarse) {
  }

  /**
   * 작업 시간 기록 한 줄 — 누가 · 어느 날 · 몇 시간 · 무엇을 · 어느 길로. 기록은 쌓기만 하고 지우면 지움 표시만 한다(DB 트리거).
   *
   * @param byHuman   사람이 한 일의 시간인가 — 웹에서 적은 기록 · MCP 로 사용자가 알려 준 사람의 시간이라 적은 기록은 true, Agent 가
   *                  MCP 로 적은 제 일의 시간은 false(2026-11-24, 일감 #204 — V27). MCP summary 의 spentHoursHuman 이 이것의 합이다
   * @param agentSession MCP 로 적은 기록의 에이전트 세션 이름 — 사람이 웹에서 적은 기록은 null(V31, 일감 #304)
   * @param deleted   지운 기록인가 — 합계(spentHours)에서 빠진다. REST · 화면 목록에는 지운 것이 없다
   * @param deletedAt 지운 때 · {@code deletedBy} 지운 사람 — 지운 기록만
   */
  public record WorkLog(UUID id, Person user, LocalDate workDate, BigDecimal hours, String note, String via, boolean byHuman,
      String agentSession, Instant createdAt, boolean deleted, Instant deletedAt, Person deletedBy) {
  }

  /**
   * 바로 아래 하위 일감에 적힌 작업 시간 한 줄 — 하위 번호 · 적은 사람 · 시간 · 적은 때만(글은 없다). MCP update_issue 가 진행 · 테스트 구간을
   * 끝낼 때 그 구간의 시간이 하위(시험 일감)에 적혔는지 본다(2026-11-25, 일감 #275).
   */
  public record ChildWorkLog(int number, UUID userId, BigDecimal hours, Instant createdAt) {
  }
}
