package com.moai.works.api.issue;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.moai.works.api.account.UserProfile;
import com.moai.works.api.account.UserProfileRepository;
import com.moai.works.api.audit.AuditService;
import com.moai.works.api.common.Actor;
import com.moai.works.api.common.ApiException;
import com.moai.works.api.project.Project;
import com.moai.works.api.project.ProjectAccessService;
import com.moai.works.api.project.ProjectRepository;
import com.moai.works.api.notification.NotificationKind;
import com.moai.works.api.notification.NotificationService;
import com.moai.works.api.security.access.ProjectContext;
import com.moai.works.api.workspace.Permission;
import com.moai.works.api.workspace.WorkspaceAccessService;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * 일감 — 만들기 · 보기 · 고치기 · 지우기 · 댓글 · 연결 · 목록 (design/05). moai-wiki 의 페이지 서비스 자리다.
 *
 * <p>누가 부를 수 있는지(프로젝트의 독자인가 · 코드가 있나)는 {@code @ProjectApi} 가 이미 판정했다. 여기서는 일감마다의 규칙
 * (관리자 바꾸기는 issue.manager.assign, 보관한 프로젝트는 쓰기 금지, 상위 일감 순환 · 깊이, 날짜 순서)을 본다.
 * MCP 도구도 이 서비스를 그대로 부른다(같은 판정 — design/06).
 */
@Service
public class IssueService {

  /** 상위 · 하위 깊이 상한 (맨 위 = 1단계) */
  static final int MAX_DEPTH = 10;
  static final int MAX_TITLE = 200;
  static final int MAX_TEXT = 30000;
  /** "오늘"을 정하는 시간대 — 워크스페이스 시간대 설정이 생기면 그것을 쓴다 */
  static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
  private static final Set<String> PATCH_FIELDS = Set.of("rowVersion", "type", "title", "description", "status",
      "managerId", "assigneeId", "parent", "dueDate", "startDate", "plannedEndDate", "completedOn", "closedOn",
      "notes", "estimatedHours");
  static final BigDecimal MAX_ESTIMATE = new BigDecimal("10000");
  static final BigDecimal MAX_WORK_HOURS = new BigDecimal("24");
  static final int REVISION_PAGE = 50;
  /** 댓글 목록 한 쪽 상한 — 댓글 하나가 3만 자까지라 너무 크게 받지 않는다 */
  static final int MAX_COMMENT_PAGE = 50;

  private final IssueRepositories.Issues issues;
  private final IssueRepositories.Types types;
  private final IssueRepositories.Links links;
  private final IssueRepositories.Journals journals;
  private final IssueRepositories.CommentRevisions commentRevisions;
  private final NotificationService notifications;
  private final IssueRepositories.Revisions revisions;
  private final IssueRepositories.WorkLogs workLogs;
  private final ProjectRepository projects;
  private final ProjectAccessService projectAccess;
  private final WorkspaceAccessService workspaceAccess;
  private final UserProfileRepository users;
  private final AuditService audit;
  private final JsonMapper json;
  private final TransactionTemplate tx;
  private final Clock clock;

  public IssueService(IssueRepositories.Issues issues, IssueRepositories.Types types, IssueRepositories.Links links,
      IssueRepositories.Journals journals, IssueRepositories.CommentRevisions commentRevisions,
      IssueRepositories.Revisions revisions, IssueRepositories.WorkLogs workLogs, ProjectRepository projects, ProjectAccessService projectAccess,
      WorkspaceAccessService workspaceAccess, UserProfileRepository users, AuditService audit, JsonMapper json,
      NotificationService notifications, PlatformTransactionManager txManager, Clock clock) {
    this.issues = issues;
    this.types = types;
    this.links = links;
    this.journals = journals;
    this.commentRevisions = commentRevisions;
    this.revisions = revisions;
    this.workLogs = workLogs;
    this.projects = projects;
    this.projectAccess = projectAccess;
    this.workspaceAccess = workspaceAccess;
    this.users = users;
    this.audit = audit;
    this.json = json;
    this.notifications = notifications;
    this.tx = new TransactionTemplate(txManager);
    this.clock = clock;
  }

  // ── 유형 ──────────────────────────────────────────────────────────

  @Transactional(readOnly = true)
  public List<IssueViews.Type> types() {
    return types.findAllByOrderByPositionAsc().stream()
        .map(t -> new IssueViews.Type(t.getKey(), t.getName(), t.getDescription(), t.isActive())).toList();
  }

  // ── 목록 ──────────────────────────────────────────────────────────

  /**
   * 목록 조건.
   *
   * @param statuses 상태(비면 전부)
   * @param types    유형 key(비면 전부)
   * @param manager  관리자 id
   * @param assignee 담당자 id
   * @param noAssignee 담당자가 없는 것만
   * @param parent   이 번호의 바로 아래 일감만
   * @param topLevel 상위 일감이 없는 것만
   * @param q        제목에 든 글자, 또는 "#12" · "12" 같은 번호
   * @param sort     number · updated · due · start · planned (앞에 - 를 붙이면 내림차순)
   */
  /**
   * @param q       제목 · 설명에 든 말(대소문자 무시), 또는 #번호 · 번호
   * @param dueFrom 완료 기한이 이날부터 · {@code dueTo} 이날까지(둘 다 포함)
   * @param overdue 기한 지난 열린 일감만
   */
  public record ListQuery(Collection<Issue.Status> statuses, Collection<String> types, UUID manager, UUID assignee,
      boolean noAssignee, Integer parent, boolean topLevel, String q, LocalDate dueFrom, LocalDate dueTo,
      boolean overdue, String sort, int page, int size) {
  }

  @Transactional(readOnly = true)
  public IssueViews.Page list(ProjectContext ctx, ListQuery query) {
    UUID projectId = ctx.projectId();
    Specification<Issue> spec = (root, cq, cb) -> cb.and(cb.equal(root.get("projectId"), projectId),
        cb.isNull(root.get("deletedAt")));
    if (query.statuses() != null && !query.statuses().isEmpty()) {
      spec = spec.and((root, cq, cb) -> root.get("status").in(query.statuses()));
    }
    if (query.types() != null && !query.types().isEmpty()) {
      spec = spec.and((root, cq, cb) -> root.get("issueType").in(query.types()));
    }
    if (query.manager() != null) {
      spec = spec.and((root, cq, cb) -> cb.equal(root.get("managerId"), query.manager()));
    }
    if (query.noAssignee()) {
      spec = spec.and((root, cq, cb) -> cb.isNull(root.get("assigneeId")));
    } else if (query.assignee() != null) {
      spec = spec.and((root, cq, cb) -> cb.equal(root.get("assigneeId"), query.assignee()));
    }
    if (query.topLevel()) {
      spec = spec.and((root, cq, cb) -> cb.isNull(root.get("parentId")));
    } else if (query.parent() != null) {
      UUID parentId = issues.findLive(projectId, query.parent()).map(Issue::getId).orElse(null);
      spec = spec.and((root, cq, cb) -> parentId == null ? cb.disjunction() : cb.equal(root.get("parentId"), parentId));
    }
    String q = query.q() == null ? "" : query.q().trim();
    if (!q.isEmpty()) {
      Integer number = parseNumber(q);
      String like = "%" + q.toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
      // 제목 · 설명 어디에 있든 찾는다. 번호 꼴이면 그 번호도
      spec = spec.and((root, cq, cb) -> {
        jakarta.persistence.criteria.Predicate text = cb.or(cb.like(cb.lower(root.get("title")), like, '\\'),
            cb.like(cb.lower(root.get("description")), like, '\\'));
        return number != null ? cb.or(cb.equal(root.get("number"), number), text) : text;
      });
    }
    if (query.dueFrom() != null) {
      spec = spec.and((root, cq, cb) -> cb.greaterThanOrEqualTo(root.get("dueDate"), query.dueFrom()));
    }
    if (query.dueTo() != null) {
      spec = spec.and((root, cq, cb) -> cb.lessThanOrEqualTo(root.get("dueDate"), query.dueTo()));
    }
    if (query.overdue()) {
      LocalDate todayForOverdue = today();
      spec = spec.and((root, cq, cb) -> cb.and(cb.lessThan(root.get("dueDate"), todayForOverdue),
          root.get("status").in(List.of(Issue.Status.NEW, Issue.Status.IN_PROGRESS, Issue.Status.TESTING))));
    }
    int size = Math.clamp(query.size() <= 0 ? 50 : query.size(), 1, 100);
    int page = Math.max(0, query.page());
    org.springframework.data.domain.Page<Issue> result = issues.findAll(spec,
        PageRequest.of(page, size, sortOf(query.sort())));
    Map<UUID, UserProfile> people = people(result.getContent().stream()
        .flatMap(i -> java.util.stream.Stream.of(i.getManagerId(), i.getAssigneeId())).toList());
    Map<UUID, Integer> parentNumbers = numbersOf(result.getContent().stream().map(Issue::getParentId)
        .filter(Objects::nonNull).collect(Collectors.toSet()));
    Map<UUID, Long> childCounts = new HashMap<>();
    if (!result.getContent().isEmpty()) {
      issues.countChildrenOf(result.getContent().stream().map(Issue::getId).toList())
          .forEach(row -> childCounts.put((UUID) row[0], (Long) row[1]));
    }
    Map<UUID, BigDecimal> spentById = new HashMap<>();
    if (!result.getContent().isEmpty()) {
      workLogs.spentOf(result.getContent().stream().map(Issue::getId).toList())
          .forEach(row -> spentById.put((UUID) row[0], (BigDecimal) row[1]));
    }
    LocalDate today = today();
    List<IssueViews.Summary> items = result.getContent().stream().map(i -> new IssueViews.Summary(i.getNumber(),
        i.getIssueType(), i.getTitle(), i.getStatus(), person(people, i.getManagerId()),
        person(people, i.getAssigneeId()), i.getParentId() == null ? null : parentNumbers.get(i.getParentId()),
        i.getDueDate(), i.getStartDate(), i.getPlannedEndDate(), i.getCompletedOn(), i.getClosedOn(),
        i.getUpdatedAt(), childCounts.getOrDefault(i.getId(), 0L).intValue(), overdue(i, today), i.getEstimatedHours(),
        spentById.getOrDefault(i.getId(), BigDecimal.ZERO))).toList();
    return new IssueViews.Page(items, result.getTotalElements(), page, size, result.getTotalPages(), result.hasNext());
  }

  private static Sort sortOf(String sort) {
    String s = sort == null || sort.isBlank() ? "-number" : sort.trim();
    boolean desc = s.startsWith("-");
    String key = desc ? s.substring(1) : s;
    String property = switch (key) {
      case "number" -> "number";
      case "updated" -> "updatedAt";
      case "due" -> "dueDate";
      case "start" -> "startDate";
      case "planned" -> "plannedEndDate";
      case "created" -> "createdAt";
      case "status" -> "statusRank";   // 등록 < 진행 < 테스트 < 완료 < 종료 (코드값 글자순이 아니라)
      case "title" -> "title";
      default -> throw ApiException.badRequest("INVALID_SORT", "정렬할 수 없는 항목입니다: " + key);
    };
    Sort.Order order = desc ? Sort.Order.desc(property).nullsLast() : Sort.Order.asc(property).nullsLast();
    return Sort.by(order, Sort.Order.desc("number"));
  }

  private static Integer parseNumber(String q) {
    String digits = q.startsWith("#") ? q.substring(1) : q;
    if (digits.isEmpty() || digits.length() > 9 || !digits.chars().allMatch(Character::isDigit)) {
      return null;
    }
    return Integer.parseInt(digits);
  }

  // ── 만들기 ────────────────────────────────────────────────────────

  /** 만들 때 넣는 값. 비운 값은 기본값(상태 NEW · 관리자 = 만든 사람) */
  public record CreateCommand(String type, String title, String description, Issue.Status status, UUID managerId,
      UUID assigneeId, Integer parent, LocalDate dueDate, LocalDate startDate, LocalDate plannedEndDate,
      LocalDate completedOn, LocalDate closedOn, BigDecimal estimatedHours) {
  }

  public IssueViews.Detail create(ProjectContext ctx, CreateCommand c, Actor actor, IssueJournal.Via via) {
    requireOpen(ctx);
    UUID creator = requirePerson(actor);
    Values v = new Values();
    v.type = c.type();
    v.title = c.title();
    v.description = c.description() == null ? "" : c.description();
    v.status = c.status() == null ? Issue.Status.NEW : c.status();
    v.managerId = c.managerId() == null ? creator : c.managerId();
    v.assigneeId = c.assigneeId();
    v.parentId = c.parent() == null ? null : requireParent(ctx, c.parent()).getId();
    v.dueDate = c.dueDate();
    v.startDate = c.startDate();
    v.plannedEndDate = c.plannedEndDate();
    v.completedOn = c.completedOn();
    v.closedOn = c.closedOn();
    v.estimatedHours = c.estimatedHours();
    // 만들 때부터 완료 · 종료면 그날을 채운다
    fillStatusDates(v, null, c.completedOn() != null, c.closedOn() != null);
    if (!v.managerId.equals(creator) && !ctx.has(Permission.ISSUE_MANAGER_ASSIGN)) {
      throw ApiException.forbidden("PERMISSION_REQUIRED", "다른 사람을 관리자로 정하려면 권한 issue.manager.assign 이(가) 필요합니다.");
    }
    validate(ctx, v, null, true, true);

    Issue saved = tx.execute(s -> {
      Project project = projects.lockById(ctx.projectId()).orElseThrow();
      int number = project.nextIssueNumber();
      Instant now = Instant.now(clock);
      Issue issue = new Issue(project.getId(), number, v.type, v.title.trim(), v.description, v.managerId, creator, now);
      copy(v, issue);
      issue = issues.saveAndFlush(issue);
      // 만들기도 이력의 첫 줄이자 1판 — 처음 값을 전 값 없이 담는다(누가 · 웹 · MCP 어느 길로 만들었는지 보인다)
      IssueJournal first = journal(issue.getId(), actor, via, null, diff(new Values(), v));
      revise(issue, first, actor, via);
      audit.record(actor, ctx.workspaceId(), "issue.create", "issue", issue.getId().toString(),
          Map.of("project", project.getKey(), "number", number, "title", issue.getTitle(), "via", via.name()));
      notifyCreated(ctx, issue, actor, via);
      return issue;
    });
    return detail(ctx, saved.getNumber());
  }

  // ── 보기 ──────────────────────────────────────────────────────────

  @Transactional(readOnly = true)
  public IssueViews.Detail detail(ProjectContext ctx, int number) {
    Issue issue = requireIssue(ctx, number);
    List<Issue> children = issues.findChildren(issue.getId());
    List<IssueLink> touching = links.findTouching(issue.getId());
    Set<UUID> otherIds = new HashSet<>();
    touching.forEach(l -> otherIds.add(l.getSourceIssueId().equals(issue.getId()) ? l.getTargetIssueId()
        : l.getSourceIssueId()));
    if (issue.getParentId() != null) {
      otherIds.add(issue.getParentId());
    }
    Map<UUID, Issue> others = issues.findAllById(otherIds).stream().filter(i -> !i.isDeleted())
        .collect(Collectors.toMap(Issue::getId, Function.identity()));
    Map<UUID, UserProfile> people = people(java.util.Arrays.asList(issue.getManagerId(), issue.getAssigneeId(),
        issue.getCreatedBy()));

    List<IssueViews.Link> linkViews = new ArrayList<>();
    for (IssueLink l : touching) {
      boolean outgoing = l.getSourceIssueId().equals(issue.getId());
      Issue other = others.get(outgoing ? l.getTargetIssueId() : l.getSourceIssueId());
      if (other != null) {
        linkViews.add(new IssueViews.Link(l.getId(), l.getType(), outgoing ? "OUTGOING" : "INCOMING", ref(other)));
      }
    }
    List<IssueJournal> lines = journals.findByIssueIdOrderByCreatedAtAscIdAsc(issue.getId());
    Map<UUID, UserProfile> deleters = people(lines.stream().map(IssueJournal::getNotesDeletedBy).toList());
    List<IssueViews.Journal> journal = lines.stream().map(j -> journalView(j, deleters)).toList();
    List<IssueWorkLog> logs = workLogs.findLive(issue.getId());
    Map<UUID, UserProfile> loggers = people(logs.stream().map(IssueWorkLog::getUserId).toList());
    List<IssueViews.WorkLog> logViews = logs.stream().map(w -> new IssueViews.WorkLog(w.getId(),
        person(loggers, w.getUserId()), w.getWorkDate(), w.getHours(), w.getNote(), w.getVia(), w.getCreatedAt()))
        .toList();
    BigDecimal spent = logs.stream().map(IssueWorkLog::getHours).reduce(BigDecimal.ZERO, BigDecimal::add);
    Issue parent = issue.getParentId() == null ? null : others.get(issue.getParentId());
    return new IssueViews.Detail(issue.getNumber(), issue.getIssueType(), issue.getTitle(), issue.getDescription(),
        issue.getStatus(), person(people, issue.getManagerId()), person(people, issue.getAssigneeId()),
        parent == null ? null : ref(parent), issue.getDueDate(), issue.getStartDate(), issue.getPlannedEndDate(),
        issue.getCompletedOn(), issue.getClosedOn(), person(people, issue.getCreatedBy()), issue.getCreatedAt(),
        issue.getUpdatedAt(), issue.getRowVersion(), overdue(issue, today()), issue.getRevisionNo(),
        issue.getEstimatedHours(), spent, children.stream().map(IssueService::ref).toList(), linkViews, journal,
        logViews);
  }

  // ── 고치기 ────────────────────────────────────────────────────────

  /**
   * 고친다 — 요청에 든 필드만 바꾼다(null 이면 비운다). rowVersion 이 지금과 다르면 409 CONCURRENT_UPDATE.
   * notes 를 함께 넣으면 같은 이력 줄에 댓글로 남는다.
   */
  public IssueViews.Detail update(ProjectContext ctx, int number, Map<String, Object> body, Actor actor,
      IssueJournal.Via via) {
    requireOpen(ctx);
    for (String key : body.keySet()) {
      if (!PATCH_FIELDS.contains(key)) {
        throw ApiException.badRequest("UNKNOWN_FIELD", "고칠 수 없는 항목입니다: " + key);
      }
    }
    if (!(body.get("rowVersion") instanceof Number rowVersion)) {
      throw ApiException.badRequest("ROW_VERSION_REQUIRED", "rowVersion 을 넣으십시오(읽을 때 받은 값).");
    }
    Issue current = requireIssue(ctx, number);
    if (current.getRowVersion() != rowVersion.intValue()) {
      throw ApiException.conflict("CONCURRENT_UPDATE", "다른 사람이 먼저 고쳤습니다. 다시 읽고 고치십시오.");
    }
    Values before = Values.of(current);
    Values v = Values.of(current);
    if (body.containsKey("type")) {
      v.type = text(body, "type");
    }
    if (body.containsKey("title")) {
      v.title = text(body, "title");
    }
    if (body.containsKey("description")) {
      String d = text(body, "description");
      v.description = d == null ? "" : d;
    }
    if (body.containsKey("status")) {
      v.status = enumValue(body, "status");
    }
    if (body.containsKey("managerId")) {
      v.managerId = uuid(body, "managerId");
    }
    if (body.containsKey("assigneeId")) {
      v.assigneeId = uuid(body, "assigneeId");
    }
    if (body.containsKey("parent")) {
      Object p = body.get("parent");
      if (p == null) {
        v.parentId = null;
      } else if (p instanceof Number n) {
        v.parentId = requireParent(ctx, n.intValue()).getId();
      } else {
        throw ApiException.badRequest("INVALID_FIELD", "parent 는 일감 번호여야 합니다.");
      }
    }
    if (body.containsKey("dueDate")) {
      v.dueDate = date(body, "dueDate");
    }
    if (body.containsKey("startDate")) {
      v.startDate = date(body, "startDate");
    }
    if (body.containsKey("plannedEndDate")) {
      v.plannedEndDate = date(body, "plannedEndDate");
    }
    if (body.containsKey("completedOn")) {
      v.completedOn = date(body, "completedOn");
    }
    if (body.containsKey("closedOn")) {
      v.closedOn = date(body, "closedOn");
    }
    if (body.containsKey("estimatedHours")) {
      v.estimatedHours = hours(body, "estimatedHours");
    }
    String notes = body.containsKey("notes") ? text(body, "notes") : null;
    if (notes != null && notes.isBlank()) {
      notes = null;
    }
    if (notes != null && !ctx.has(Permission.ISSUE_COMMENT)) {
      throw ApiException.forbidden("PERMISSION_REQUIRED", "댓글을 함께 남기려면 권한 issue.comment 이(가) 필요합니다.");
    }
    if (v.status != before.status) {
      fillStatusDates(v, before.status, body.containsKey("completedOn"), body.containsKey("closedOn"));
    }
    if (!Objects.equals(v.managerId, before.managerId) && !ctx.has(Permission.ISSUE_MANAGER_ASSIGN)) {
      throw ApiException.forbidden("PERMISSION_REQUIRED", "관리자를 바꾸려면 권한 issue.manager.assign 이(가) 필요합니다.");
    }
    validate(ctx, v, current, !Objects.equals(v.managerId, before.managerId),
        !Objects.equals(v.assigneeId, before.assigneeId));
    if (!Objects.equals(v.parentId, before.parentId) && v.parentId != null) {
      checkParent(current.getId(), v.parentId);
    }
    if (notes != null && notes.length() > MAX_TEXT) {
      throw ApiException.badRequest("COMMENT_TOO_LONG", "댓글은 " + MAX_TEXT + "자를 넘을 수 없습니다.");
    }

    List<Map<String, Object>> changes = diff(before, v);
    if (changes.isEmpty() && notes == null) {
      return detail(ctx, number);
    }
    String finalNotes = notes;
    tx.executeWithoutResult(s -> {
      Issue issue = issues.findById(current.getId()).orElseThrow();
      if (issue.getRowVersion() != rowVersion.intValue()) {
        throw ApiException.conflict("CONCURRENT_UPDATE", "다른 사람이 먼저 고쳤습니다. 다시 읽고 고치십시오.");
      }
      if (!changes.isEmpty()) {
        copy(v, issue);
        issue.touch(Instant.now(clock));
        audit.record(actor, ctx.workspaceId(), "issue.update", "issue", issue.getId().toString(),
            Map.of("project", ctx.project().getKey(), "number", number, "via", via.name(),
                "fields", changes.stream().map(c -> c.get("field")).toList()));
      }
      IssueJournal line = journal(issue.getId(), actor, via, finalNotes, changes);
      if (!changes.isEmpty()) {
        // 필드가 바뀌었으면 새 판 — 그 시점의 일감 전체를 남긴다(댓글만 있는 줄은 판이 아니다)
        revise(issue, line, actor, via);
      }
      if (finalNotes != null) {
        audit.record(actor, ctx.workspaceId(), "issue.comment", "issue", issue.getId().toString(),
            Map.of("project", ctx.project().getKey(), "number", number, "via", via.name()));
      }
      if (!changes.isEmpty()) {
        notifyUpdated(ctx, issue, before.managerId, before.assigneeId, before.status, before.parentId, changes, actor,
            via);
      }
      if (finalNotes != null) {
        notifyCommented(ctx, issue, actor, via);
      }
    });
    return detail(ctx, number);
  }

  // ── 리비전 · 차이 ─────────────────────────────────────────────────

  /** 판들 — 새 판부터. before 가 있으면 그 번호보다 앞선 판부터 */
  @Transactional(readOnly = true)
  public IssueViews.RevisionList revisions(ProjectContext ctx, int number, Integer before, int limit) {
    Issue issue = requireIssue(ctx, number);
    int size = Math.clamp(limit <= 0 ? REVISION_PAGE : limit, 1, REVISION_PAGE);
    List<IssueRevision> rows = revisions.findBefore(issue.getId(), before == null ? Integer.MAX_VALUE : before,
        PageRequest.of(0, size + 1));
    boolean more = rows.size() > size;
    List<IssueRevision> page = more ? rows.subList(0, size) : rows;
    Map<UUID, List<String>> fields = changedFields(page);
    List<IssueViews.RevisionItem> items = page.stream()
        .map(r -> item(r, fields.getOrDefault(r.getJournalId(), List.of()), issue)).toList();
    return new IssueViews.RevisionList(items, more ? page.getLast().getRevisionNo() : null);
  }

  @Transactional(readOnly = true)
  public IssueViews.Revision revision(ProjectContext ctx, int number, int no) {
    Issue issue = requireIssue(ctx, number);
    IssueRevision r = requireRevision(issue, no);
    Map<UUID, UserProfile> people = people(java.util.Arrays.asList(r.getManagerId(), r.getAssigneeId()));
    return new IssueViews.Revision(r.getRevisionNo(), r.getActorType(), r.getActorId(), r.getActorName(), r.getVia(),
        r.getCreatedAt(), r.getIssueType(), r.getTitle(), r.getDescription(), r.getStatus(),
        person(people, r.getManagerId()), person(people, r.getAssigneeId()), r.getParentNumber(), r.getDueDate(),
        r.getStartDate(), r.getPlannedEndDate(), r.getCompletedOn(), r.getClosedOn(), r.getEstimatedHours(),
        r.getRevisionNo() == issue.getRevisionNo());
  }

  /**
   * 두 판의 차이 — 필드는 전 · 후, 설명은 줄 단위.
   *
   * @param no   새 쪽 판
   * @param from 옛 쪽 판. 비면 바로 앞 판(1판이면 "없음"과 견준다)
   */
  @Transactional(readOnly = true)
  public IssueViews.RevisionDiff diff(ProjectContext ctx, int number, int no, Integer from) {
    Issue issue = requireIssue(ctx, number);
    IssueRevision to = requireRevision(issue, no);
    int fromNo = from == null ? no - 1 : from;
    if (fromNo == no) {
      throw ApiException.badRequest("SAME_REVISION", "같은 판끼리는 견줄 수 없습니다.");
    }
    IssueRevision base = fromNo <= 0 ? null : requireRevision(issue, fromNo);
    Map<UUID, UserProfile> people = people(java.util.Arrays.asList(to.getManagerId(), to.getAssigneeId(),
        base == null ? null : base.getManagerId(), base == null ? null : base.getAssigneeId()));
    List<Map<String, Object>> fields = new ArrayList<>();
    fieldChange(fields, "type", base == null ? null : base.getIssueType(), to.getIssueType());
    fieldChange(fields, "title", base == null ? null : base.getTitle(), to.getTitle());
    fieldChange(fields, "status", base == null ? null : base.getStatus().name(), to.getStatus().name());
    fieldChange(fields, "manager", base == null ? null : personRef(people, base.getManagerId()),
        personRef(people, to.getManagerId()));
    fieldChange(fields, "assignee", base == null ? null : personRef(people, base.getAssigneeId()),
        personRef(people, to.getAssigneeId()));
    fieldChange(fields, "parent", base == null ? null : base.getParentNumber(), to.getParentNumber());
    fieldChange(fields, "dueDate", base == null ? null : str(base.getDueDate()), str(to.getDueDate()));
    fieldChange(fields, "startDate", base == null ? null : str(base.getStartDate()), str(to.getStartDate()));
    fieldChange(fields, "plannedEndDate", base == null ? null : str(base.getPlannedEndDate()),
        str(to.getPlannedEndDate()));
    fieldChange(fields, "completedOn", base == null ? null : str(base.getCompletedOn()), str(to.getCompletedOn()));
    fieldChange(fields, "closedOn", base == null ? null : str(base.getClosedOn()), str(to.getClosedOn()));
    fieldChange(fields, "estimatedHours", base == null ? null : plain(base.getEstimatedHours()),
        plain(to.getEstimatedHours()));
    LineDiff.Result lines = LineDiff.diff(base == null ? "" : base.getDescription(), to.getDescription());
    List<IssueRevision> pair = base == null ? List.of(to) : List.of(base, to);
    Map<UUID, List<String>> changed = changedFields(pair);
    return new IssueViews.RevisionDiff(base == null ? null : base.getRevisionNo(), to.getRevisionNo(),
        base == null ? null : item(base, changed.getOrDefault(base.getJournalId(), List.of()), issue),
        item(to, changed.getOrDefault(to.getJournalId(), List.of()), issue), fields, lines.lines(), lines.added(),
        lines.removed(), lines.coarse());
  }

  private static void fieldChange(List<Map<String, Object>> out, String field, Object before, Object after) {
    if (!Objects.equals(before, after)) {
      out.add(change(field, before, after));
    }
  }

  private static String str(LocalDate d) {
    return d == null ? null : d.toString();
  }

  private static Map<String, Object> personRef(Map<UUID, UserProfile> people, UUID id) {
    if (id == null) {
      return null;
    }
    UserProfile u = people.get(id);
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", id.toString());
    m.put("name", u == null ? id.toString() : (u.getDisplayName() == null ? u.getUsername() : u.getDisplayName()));
    return m;
  }

  /** 판마다 바뀐 필드 이름 — 그 판을 만든 이력 줄에서 읽는다 */
  private Map<UUID, List<String>> changedFields(List<IssueRevision> rows) {
    Set<UUID> ids = rows.stream().map(IssueRevision::getJournalId).filter(Objects::nonNull).collect(Collectors.toSet());
    Map<UUID, List<String>> out = new HashMap<>();
    journals.findAllById(ids).forEach(j -> out.put(j.getId(),
        json.readValue(j.getChanges(), new TypeReference<List<Map<String, Object>>>() {
        }).stream().map(c -> String.valueOf(c.get("field"))).toList()));
    return out;
  }

  private static IssueViews.RevisionItem item(IssueRevision r, List<String> fields, Issue issue) {
    return new IssueViews.RevisionItem(r.getRevisionNo(), r.getActorType(), r.getActorId(), r.getActorName(),
        r.getVia(), r.getCreatedAt(), fields, r.getRevisionNo() == issue.getRevisionNo());
  }

  private IssueRevision requireRevision(Issue issue, int no) {
    return revisions.findByIssueIdAndRevisionNo(issue.getId(), no)
        .orElseThrow(() -> ApiException.notFound("REVISION_NOT_FOUND", no + "번째 판이 없습니다."));
  }

  // ── 작업 시간 ──────────────────────────────────────────────────────

  /**
   * 작업 시간을 기록한다 — 한 일감에 여러 번(일하면서 그때그때). 담당자가 아니어도 일감을 고칠 수 있는 사람이면 기록한다.
   * 일한 사람은 늘 부른 사람이다(남의 시간을 대신 적지 않는다).
   */
  public IssueViews.Detail logWork(ProjectContext ctx, int number, LocalDate workDate, BigDecimal hours, String note,
      Actor actor, IssueJournal.Via via) {
    requireOpen(ctx);
    UUID worker = requirePerson(actor);
    if (workDate == null) {
      throw ApiException.badRequest("WORK_DATE_REQUIRED", "일한 날을 넣으십시오.");
    }
    if (workDate.isAfter(today())) {
      throw ApiException.badRequest("INVALID_WORK_DATE", "앞으로의 날짜에는 작업 시간을 적을 수 없습니다.");
    }
    if (hours == null || hours.signum() <= 0 || hours.compareTo(MAX_WORK_HOURS) > 0) {
      throw ApiException.badRequest("INVALID_WORK_HOURS", "작업 시간은 0 보다 크고 24 시간 이하입니다.");
    }
    BigDecimal h = hours.setScale(2, java.math.RoundingMode.HALF_UP);
    String text = note == null || note.isBlank() ? null : note.trim();
    if (text != null && text.length() > 1000) {
      throw ApiException.badRequest("TEXT_TOO_LONG", "메모는 1000자를 넘을 수 없습니다.");
    }
    Issue issue = requireIssue(ctx, number);
    tx.executeWithoutResult(s -> {
      BigDecimal spentBefore = workLogs.spent(issue.getId());
      IssueWorkLog log = workLogs.save(new IssueWorkLog(issue.getId(), worker, workDate, h, text, via,
          Instant.now(clock)));
      journal(issue.getId(), actor, via, null, List.of(change("workLog", null, workLogValue(log))));
      audit.record(actor, ctx.workspaceId(), "issue.work_log.add", "issue", issue.getId().toString(),
          Map.of("project", ctx.project().getKey(), "number", number, "workLog", log.getId().toString(),
              "date", workDate.toString(), "hours", plain(h), "via", via.name()));
      notifyWorkLogged(ctx, issue, workDate, h, spentBefore, actor, via);
    });
    return detail(ctx, number);
  }

  /** 작업 시간 기록을 지운다 — 자기 기록은 그대로, 남의 기록은 project.manage 가 있어야. 지움 표시만 하고 누가 지웠는지 남긴다 */
  public IssueViews.Detail removeWork(ProjectContext ctx, int number, UUID logId, Actor actor, IssueJournal.Via via) {
    requireOpen(ctx);
    UUID by = requirePerson(actor);
    Issue issue = requireIssue(ctx, number);
    IssueWorkLog log = workLogs.findById(logId)
        .filter(w -> w.getIssueId().equals(issue.getId()) && !w.isDeleted())
        .orElseThrow(() -> ApiException.notFound("WORK_LOG_NOT_FOUND", "작업 시간 기록을 찾을 수 없습니다."));
    if (!log.getUserId().equals(by) && !ctx.has(Permission.PROJECT_MANAGE)) {
      throw ApiException.forbidden("PERMISSION_REQUIRED", "남의 작업 시간을 지우려면 권한 project.manage 이(가) 필요합니다.");
    }
    tx.executeWithoutResult(s -> {
      IssueWorkLog locked = workLogs.findById(log.getId()).orElseThrow();
      locked.markDeleted(by, Instant.now(clock));
      journal(issue.getId(), actor, via, null, List.of(change("workLog", workLogValue(locked), null)));
      audit.record(actor, ctx.workspaceId(), "issue.work_log.remove", "issue", issue.getId().toString(),
          Map.of("project", ctx.project().getKey(), "number", number, "workLog", locked.getId().toString(),
              "worker", locked.getUserId().toString(), "via", via.name()));
    });
    return detail(ctx, number);
  }

  private Map<String, Object> workLogValue(IssueWorkLog log) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", log.getId().toString());
    m.put("user", personValue(log.getUserId()));
    m.put("date", log.getWorkDate().toString());
    m.put("hours", plain(log.getHours()));
    if (log.getNote() != null) {
      m.put("note", log.getNote());
    }
    return m;
  }

  // ── 댓글 ──────────────────────────────────────────────────────────

  public IssueViews.Detail comment(ProjectContext ctx, int number, String notes, Actor actor, IssueJournal.Via via) {
    requireOpen(ctx);
    checkCommentText(notes);
    Issue issue = requireIssue(ctx, number);
    tx.executeWithoutResult(s -> {
      journal(issue.getId(), actor, via, notes, List.of());
      audit.record(actor, ctx.workspaceId(), "issue.comment", "issue", issue.getId().toString(),
          Map.of("project", ctx.project().getKey(), "number", number, "via", via.name()));
      notifyCommented(ctx, issue, actor, via);
    });
    return detail(ctx, number);
  }

  /**
   * 댓글 목록 — 오래된 것부터 한 쪽씩(이력 줄기와 같은 차례). 지운 댓글은 글 없이 제자리에 남는다(deleted).
   *
   * @param page 0 부터 · {@code size} 1 ~ {@link #MAX_COMMENT_PAGE}
   */
  @Transactional(readOnly = true)
  public IssueViews.CommentPage comments(ProjectContext ctx, int number, int page, int size) {
    Issue issue = requireIssue(ctx, number);
    int p = Math.max(page, 0);
    int n = Math.min(Math.max(size, 1), MAX_COMMENT_PAGE);
    org.springframework.data.domain.Page<IssueJournal> result = journals.findComments(issue.getId(), PageRequest.of(p, n));
    Map<UUID, UserProfile> deleters = people(result.getContent().stream().map(IssueJournal::getNotesDeletedBy).toList());
    List<IssueViews.Comment> items = result.getContent().stream()
        .map(j -> new IssueViews.Comment(j.getId(), j.getActorType(), j.getActorId(), j.getActorName(), j.getVia(),
            j.getCreatedAt(), j.visibleNotes(), j.getNotesRowVersion(), j.getNotesEditedAt(), j.isNotesDeleted(),
            j.getNotesDeletedAt(), person(deleters, j.getNotesDeletedBy()), !changesOf(j).isEmpty()))
        .toList();
    return new IssueViews.CommentPage(number, items, result.getTotalElements(), p, n, result.getTotalPages(),
        result.hasNext());
  }

  /**
   * 댓글을 고친다 — <b>쓴 사람만</b>(issue.comment 는 부르는 쪽 — {@code @ProjectApi} · MCP 가 지금 권한으로 본다).
   * 앞 글은 issue_comment_revision 에 쌓아 두고 판(rowVersion)을 올린다. 읽을 때 받은 rowVersion 과 지금 판이 다르면
   * 409 CONCURRENT_UPDATE(응답에 지금 rowVersion). 글이 같으면 아무것도 하지 않는다.
   * 이력에 한 줄(field comment, 판은 아님) · 감사 issue.comment.update. 일감의 rowVersion · 고친 때는 그대로다(댓글 달기와 같다).
   */
  public IssueViews.Detail editComment(ProjectContext ctx, int number, UUID commentId, String notes, Integer rowVersion,
      Actor actor, IssueJournal.Via via) {
    requireOpen(ctx);
    UUID by = requirePerson(actor);
    if (rowVersion == null) {
      throw ApiException.badRequest("ROW_VERSION_REQUIRED", "rowVersion 을 넣으십시오(댓글을 읽을 때 받은 값).");
    }
    checkCommentText(notes);
    Issue issue = requireIssue(ctx, number);
    IssueJournal current = requireComment(issue, commentId);
    if (!by.equals(current.getActorId())) {
      throw ApiException.forbidden("PERMISSION_REQUIRED", "자기가 쓴 댓글만 고칠 수 있습니다.");
    }
    requireLiveComment(current);
    requireCommentVersion(current, rowVersion);
    if (notes.equals(current.getNotes())) {
      return detail(ctx, number);
    }
    tx.executeWithoutResult(s -> {
      IssueJournal locked = journals.lockById(current.getId()).orElseThrow();
      requireLiveComment(locked);
      requireCommentVersion(locked, rowVersion);
      Instant now = Instant.now(clock);
      // 앞 글을 먼저 남긴다 — 트리거가 남은 것을 확인하고서야 글을 바꾸게 한다
      commentRevisions.saveAndFlush(new IssueCommentRevision(locked, by, via, now));
      locked.editNotes(notes, now);
      journal(issue.getId(), actor, via, null, List.of(change("comment", commentValue(locked), commentValue(locked))));
      audit.record(actor, ctx.workspaceId(), "issue.comment.update", "issue", issue.getId().toString(),
          Map.of("project", ctx.project().getKey(), "number", number, "comment", locked.getId().toString(),
              "rowVersion", locked.getNotesRowVersion(), "via", via.name()));
    });
    return detail(ctx, number);
  }

  /**
   * 댓글을 지운다 — 쓴 사람, 또는 project.manage. 지움 표시(누가 · 언제)만 하고 글은 DB 에 남지만 REST · MCP 는 더 내주지 않는다.
   * 함께 올린 첨부는 그대로 일감의 첨부로 남는다. 이력에 한 줄(field comment, after = null) · 감사 issue.comment.delete.
   */
  public IssueViews.Detail deleteComment(ProjectContext ctx, int number, UUID commentId, Actor actor,
      IssueJournal.Via via) {
    requireOpen(ctx);
    UUID by = requirePerson(actor);
    Issue issue = requireIssue(ctx, number);
    IssueJournal current = requireComment(issue, commentId);
    if (!by.equals(current.getActorId()) && !ctx.has(Permission.PROJECT_MANAGE)) {
      throw ApiException.forbidden("PERMISSION_REQUIRED", "남의 댓글을 지우려면 권한 project.manage 이(가) 필요합니다.");
    }
    requireLiveComment(current);
    tx.executeWithoutResult(s -> {
      IssueJournal locked = journals.lockById(current.getId()).orElseThrow();
      requireLiveComment(locked);
      locked.markNotesDeleted(by, Instant.now(clock));
      journal(issue.getId(), actor, via, null, List.of(change("comment", commentValue(locked), null)));
      Map<String, Object> details = new LinkedHashMap<>();
      details.put("project", ctx.project().getKey());
      details.put("number", number);
      details.put("comment", locked.getId().toString());
      if (locked.getActorId() != null) {
        details.put("author", locked.getActorId().toString());
      }
      details.put("via", via.name());
      audit.record(actor, ctx.workspaceId(), "issue.comment.delete", "issue", issue.getId().toString(), details);
    });
    return detail(ctx, number);
  }

  /** 댓글 글 검사 — 비면 INVALID_COMMENT, 3만 자를 넘으면 COMMENT_TOO_LONG (달기 · 고치기가 같이 쓴다) */
  private static void checkCommentText(String notes) {
    if (notes == null || notes.isBlank()) {
      throw ApiException.badRequest("INVALID_COMMENT", "댓글 내용을 넣으십시오.");
    }
    if (notes.length() > MAX_TEXT) {
      throw ApiException.badRequest("COMMENT_TOO_LONG", "댓글은 " + MAX_TEXT + "자를 넘을 수 없습니다.");
    }
  }

  /** 그 일감의 댓글(이력 줄) — 없거나 · 다른 일감 것이거나 · 댓글이 없는 줄이면 404 COMMENT_NOT_FOUND */
  private IssueJournal requireComment(Issue issue, UUID commentId) {
    return (commentId == null ? java.util.Optional.<IssueJournal>empty() : journals.findById(commentId))
        .filter(j -> j.getIssueId().equals(issue.getId()) && j.isComment())
        .orElseThrow(() -> ApiException.notFound("COMMENT_NOT_FOUND", "댓글을 찾을 수 없습니다."));
  }

  private static void requireLiveComment(IssueJournal comment) {
    if (comment.isNotesDeleted()) {
      throw ApiException.conflict("COMMENT_DELETED", "이미 지운 댓글입니다.");
    }
  }

  private static void requireCommentVersion(IssueJournal comment, int rowVersion) {
    if (comment.getNotesRowVersion() != rowVersion) {
      throw ApiException.conflict("CONCURRENT_UPDATE", "다른 곳에서 먼저 고친 댓글입니다. 다시 읽고 고치십시오.")
          .with("rowVersion", comment.getNotesRowVersion());
    }
  }

  /** 이력의 댓글 값 — 댓글 id 와 쓴 사람(그때의 이름). 글은 넣지 않는다(지운 댓글의 글이 이력으로 새지 않게) */
  private static Map<String, Object> commentValue(IssueJournal comment) {
    Map<String, Object> author = new LinkedHashMap<>();
    author.put("id", comment.getActorId() == null ? null : comment.getActorId().toString());
    author.put("name", comment.getActorName());
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", comment.getId().toString());
    m.put("author", author);
    return m;
  }

  /** 이력 한 줄의 응답 모양 — 지운 댓글은 글을 빼고 지운 사람 · 때만 */
  private IssueViews.Journal journalView(IssueJournal j, Map<UUID, UserProfile> deleters) {
    return new IssueViews.Journal(j.getId(), j.getActorType(), j.getActorId(), j.getActorName(), j.getVia(),
        j.getCreatedAt(), j.visibleNotes(), changesOf(j), j.getRevisionNo(),
        j.isComment() ? j.getNotesRowVersion() : null, j.getNotesEditedAt(), j.isNotesDeleted(), j.getNotesDeletedAt(),
        person(deleters, j.getNotesDeletedBy()));
  }

  private List<Map<String, Object>> changesOf(IssueJournal j) {
    return json.readValue(j.getChanges(), new TypeReference<List<Map<String, Object>>>() {
    });
  }

  // ── 연결 ──────────────────────────────────────────────────────────

  public IssueViews.Detail link(ProjectContext ctx, int number, int target, IssueLink.Type type, Actor actor,
      IssueJournal.Via via) {
    requireOpen(ctx);
    if (type == null) {
      throw ApiException.badRequest("LINK_TYPE_REQUIRED", "연결 종류를 넣으십시오.");
    }
    Issue source = requireIssue(ctx, number);
    if (target == number) {
      throw ApiException.badRequest("INVALID_ISSUE_LINK", "자기 자신과는 연결할 수 없습니다.");
    }
    Issue other = issues.findLive(ctx.projectId(), target).orElseThrow(() -> ApiException.badRequest(
        "LINK_TARGET_NOT_FOUND", "이 프로젝트에 연결할 일감이 없습니다: #" + target));
    if (links.existsPair(source.getId(), other.getId())) {
      throw ApiException.conflict("ISSUE_LINK_EXISTS", "이미 연결된 일감입니다: #" + target);
    }
    tx.executeWithoutResult(s -> {
      links.saveAndFlush(new IssueLink(source.getId(), other.getId(), type, requirePerson(actor), Instant.now(clock)));
      journal(source.getId(), actor, via, null, List.of(change("link", null, linkValue(type, "OUTGOING", other))));
      journal(other.getId(), actor, via, null, List.of(change("link", null, linkValue(type, "INCOMING", source))));
      audit.record(actor, ctx.workspaceId(), "issue.link.add", "issue", source.getId().toString(),
          Map.of("project", ctx.project().getKey(), "number", number, "target", target, "type", type.name(),
              "via", via.name()));
      notifyLinked(ctx, source, other, type, "OUTGOING", actor, via);
      notifyLinked(ctx, other, source, type, "INCOMING", actor, via);
    });
    return detail(ctx, number);
  }

  public IssueViews.Detail unlink(ProjectContext ctx, int number, UUID linkId, Actor actor, IssueJournal.Via via) {
    requireOpen(ctx);
    Issue issue = requireIssue(ctx, number);
    IssueLink link = links.findById(linkId)
        .filter(l -> l.getSourceIssueId().equals(issue.getId()) || l.getTargetIssueId().equals(issue.getId()))
        .orElseThrow(() -> ApiException.notFound("ISSUE_LINK_NOT_FOUND", "연결을 찾을 수 없습니다."));
    boolean outgoing = link.getSourceIssueId().equals(issue.getId());
    UUID otherId = outgoing ? link.getTargetIssueId() : link.getSourceIssueId();
    Issue other = issues.findById(otherId).orElseThrow();
    tx.executeWithoutResult(s -> {
      links.deleteById(link.getId());
      journal(issue.getId(), actor, via, null,
          List.of(change("link", linkValue(link.getType(), outgoing ? "OUTGOING" : "INCOMING", other), null)));
      if (!other.isDeleted()) {
        journal(other.getId(), actor, via, null,
            List.of(change("link", linkValue(link.getType(), outgoing ? "INCOMING" : "OUTGOING", issue), null)));
      }
      audit.record(actor, ctx.workspaceId(), "issue.link.remove", "issue", issue.getId().toString(),
          Map.of("project", ctx.project().getKey(), "number", number, "target", other.getNumber(),
              "type", link.getType().name(), "via", via.name()));
    });
    return detail(ctx, number);
  }

  // ── 지우기 ────────────────────────────────────────────────────────

  /** 지운다 — 하위 일감이 있으면 거절한다(먼저 옮기거나 지운다). 연결은 함께 지운다. 행은 지움 표시만 한다 */
  public void delete(ProjectContext ctx, int number, Actor actor) {
    delete(ctx, number, null, actor, IssueJournal.Via.WEB);
  }

  /**
   * 지운다(경로 · 이유와 함께) — MCP 로 지울 때는 이유가 필수다(2026-11-22 사용자: MCP 로 일감 CRUD).
   *
   * @param reason 감사에 남길 이유(MCP 는 필수, 웹은 없어도 된다)
   */
  public void delete(ProjectContext ctx, int number, String reason, Actor actor, IssueJournal.Via via) {
    requireOpen(ctx);
    Issue issue = requireIssue(ctx, number);
    if (issues.countChildren(issue.getId()) > 0) {
      throw ApiException.conflict("ISSUE_HAS_CHILDREN", "하위 일감이 있어 지울 수 없습니다. 하위 일감을 먼저 옮기거나 지우십시오.");
    }
    tx.executeWithoutResult(s -> {
      Issue locked = issues.findById(issue.getId()).orElseThrow();
      // 연결은 함께 지우고, 상대 일감의 이력에 풀린 것을 남긴다
      for (IssueLink link : links.findTouching(locked.getId())) {
        UUID otherId = link.getSourceIssueId().equals(locked.getId()) ? link.getTargetIssueId()
            : link.getSourceIssueId();
        String otherDirection = link.getSourceIssueId().equals(locked.getId()) ? "INCOMING" : "OUTGOING";
        links.delete(link);
        issues.findById(otherId).filter(o -> !o.isDeleted()).ifPresent(o -> journal(o.getId(), actor,
            via, null, List.of(change("link", linkValue(link.getType(), otherDirection, locked), null))));
      }
      locked.markDeleted(Instant.now(clock));
      Map<String, Object> details = new LinkedHashMap<>();
      details.put("project", ctx.project().getKey());
      details.put("number", locked.getNumber());
      details.put("title", locked.getTitle());
      details.put("via", via.name());
      if (reason != null && !reason.isBlank()) {
        details.put("reason", reason.trim());
      }
      audit.record(actor, ctx.workspaceId(), "issue.delete", "issue", locked.getId().toString(), details);
      notifications.notifyAll(watchers(locked), ctx.workspaceId(), NotificationKind.ISSUE_DELETED, locked.getId(),
          about(ctx, locked, via), actor);
    });
  }

  // ── 알림 (design/07) ───────────────────────────────────────────────
  // 그 일을 하는 트랜잭션 안에서 쌓는다. 자기가 한 일은 자기에게 알리지 않는다(NotificationService 가 걸러 낸다).

  private static final Set<String> STATE_FIELDS = Set.of("status", "manager", "assignee", "completedOn", "closedOn");

  private Map<String, Object> about(ProjectContext ctx, Issue issue, IssueJournal.Via via) {
    return NotificationService.aboutIssue(ctx.project().getKey(), ctx.project().getName(), issue.getNumber(),
        issue.getTitle(), via.name());
  }

  /** 일감을 지켜보는 사람 — 관리자 · 담당자 */
  private static Set<UUID> watchers(Issue issue) {
    Set<UUID> out = new java.util.LinkedHashSet<>();
    out.add(issue.getManagerId());
    if (issue.getAssigneeId() != null) {
      out.add(issue.getAssigneeId());
    }
    return out;
  }

  private void notifyCreated(ProjectContext ctx, Issue issue, Actor actor, IssueJournal.Via via) {
    notifications.notify(issue.getManagerId(), ctx.workspaceId(), NotificationKind.ISSUE_MANAGER_ASSIGNED,
        issue.getId(), about(ctx, issue, via), actor);
    notifications.notify(issue.getAssigneeId(), ctx.workspaceId(), NotificationKind.ISSUE_ASSIGNED, issue.getId(),
        about(ctx, issue, via), actor);
    if (issue.getParentId() != null) {
      notifyChildAdded(ctx, issue.getParentId(), issue, actor, via);
    }
  }

  private void notifyChildAdded(ProjectContext ctx, UUID parentId, Issue child, Actor actor, IssueJournal.Via via) {
    issues.findById(parentId).filter(p -> !p.isDeleted()).ifPresent(parent -> {
      Map<String, Object> params = about(ctx, parent, via);
      params.put("childNumber", child.getNumber());
      params.put("childTitle", child.getTitle());
      notifications.notifyAll(watchers(parent), ctx.workspaceId(), NotificationKind.ISSUE_CHILD_ADDED, parent.getId(),
          params, actor);
    });
  }

  /** 고친 뒤 — 새 담당자 · 옛 담당자 · 새 관리자 · 상태 · 내용 · 새 상위 */
  private void notifyUpdated(ProjectContext ctx, Issue issue, UUID managerBefore, UUID assigneeBefore,
      Issue.Status statusBefore, UUID parentBefore, List<Map<String, Object>> changes, Actor actor,
      IssueJournal.Via via) {
    Set<UUID> told = new java.util.HashSet<>();
    if (!Objects.equals(issue.getAssigneeId(), assigneeBefore)) {
      if (issue.getAssigneeId() != null) {
        notifications.notify(issue.getAssigneeId(), ctx.workspaceId(), NotificationKind.ISSUE_ASSIGNED, issue.getId(),
            about(ctx, issue, via), actor);
        told.add(issue.getAssigneeId());
      }
      if (assigneeBefore != null) {
        notifications.notify(assigneeBefore, ctx.workspaceId(), NotificationKind.ISSUE_UNASSIGNED, issue.getId(),
            about(ctx, issue, via), actor);
      }
    }
    if (!Objects.equals(issue.getManagerId(), managerBefore)) {
      notifications.notify(issue.getManagerId(), ctx.workspaceId(), NotificationKind.ISSUE_MANAGER_ASSIGNED,
          issue.getId(), about(ctx, issue, via), actor);
      told.add(issue.getManagerId());
    }
    Set<UUID> others = watchers(issue);
    others.removeAll(told);
    if (issue.getStatus() != statusBefore) {
      Map<String, Object> params = about(ctx, issue, via);
      params.put("from", statusBefore.name());
      params.put("to", issue.getStatus().name());
      notifications.notifyAll(others, ctx.workspaceId(), NotificationKind.ISSUE_STATUS_CHANGED, issue.getId(), params,
          actor);
    }
    List<Object> fields = changes.stream().map(c -> c.get("field")).filter(f -> !STATE_FIELDS.contains(f)).toList();
    if (!fields.isEmpty()) {
      Map<String, Object> params = about(ctx, issue, via);
      params.put("fields", fields);
      notifications.notifyAll(others, ctx.workspaceId(), NotificationKind.ISSUE_UPDATED, issue.getId(), params, actor);
    }
    if (issue.getParentId() != null && !issue.getParentId().equals(parentBefore)) {
      notifyChildAdded(ctx, issue.getParentId(), issue, actor, via);
    }
  }

  /** 댓글 — 관리자 · 담당자 · 앞서 댓글을 단 사람 */
  private void notifyCommented(ProjectContext ctx, Issue issue, Actor actor, IssueJournal.Via via) {
    Set<UUID> to = watchers(issue);
    to.addAll(notifications.commenters(issue.getId()));
    notifications.notifyAll(to, ctx.workspaceId(), NotificationKind.ISSUE_COMMENTED, issue.getId(),
        about(ctx, issue, via), actor);
  }

  /** 연결 — 그 일감의 관리자 · 담당자에게, 상대 일감과 함께 */
  private void notifyLinked(ProjectContext ctx, Issue issue, Issue other, IssueLink.Type type, String direction,
      Actor actor, IssueJournal.Via via) {
    Map<String, Object> params = about(ctx, issue, via);
    params.put("linkType", type.name());
    params.put("direction", direction);
    params.put("otherNumber", other.getNumber());
    params.put("otherTitle", other.getTitle());
    notifications.notifyAll(watchers(issue), ctx.workspaceId(), NotificationKind.ISSUE_LINKED, issue.getId(), params,
        actor);
  }

  /** 작업 시간 — 관리자에게. 소요 시간이 예상 공수를 처음 넘으면 관리자 · 담당자에게 */
  private void notifyWorkLogged(ProjectContext ctx, Issue issue, LocalDate workDate, BigDecimal hours,
      BigDecimal spentBefore, Actor actor, IssueJournal.Via via) {
    BigDecimal before = spentBefore == null ? BigDecimal.ZERO : spentBefore;
    BigDecimal after = before.add(hours);
    Map<String, Object> params = about(ctx, issue, via);
    params.put("workDate", workDate.toString());
    params.put("hours", plain(hours));
    params.put("spent", plain(after));
    if (issue.getEstimatedHours() != null) {
      params.put("estimated", plain(issue.getEstimatedHours()));
    }
    notifications.notify(issue.getManagerId(), ctx.workspaceId(), NotificationKind.WORK_LOGGED, issue.getId(), params,
        actor);
    BigDecimal estimate = issue.getEstimatedHours();
    if (estimate != null && estimate.signum() > 0 && before.compareTo(estimate) <= 0 && after.compareTo(estimate) > 0) {
      notifications.notifyAll(watchers(issue), ctx.workspaceId(), NotificationKind.WORK_OVER_ESTIMATE, issue.getId(),
          params, actor);
    }
  }

  // ── 규칙 ──────────────────────────────────────────────────────────

  /** 고칠 값을 모아 두는 그릇 */
  private static final class Values {
    String type;
    String title;
    String description;
    Issue.Status status;
    UUID managerId;
    UUID assigneeId;
    UUID parentId;
    LocalDate dueDate;
    LocalDate startDate;
    LocalDate plannedEndDate;
    LocalDate completedOn;
    LocalDate closedOn;
    BigDecimal estimatedHours;

    static Values of(Issue i) {
      Values v = new Values();
      v.type = i.getIssueType();
      v.title = i.getTitle();
      v.description = i.getDescription();
      v.status = i.getStatus();
      v.managerId = i.getManagerId();
      v.assigneeId = i.getAssigneeId();
      v.parentId = i.getParentId();
      v.dueDate = i.getDueDate();
      v.startDate = i.getStartDate();
      v.plannedEndDate = i.getPlannedEndDate();
      v.completedOn = i.getCompletedOn();
      v.closedOn = i.getClosedOn();
      v.estimatedHours = i.getEstimatedHours();
      return v;
    }
  }

  /**
   * 상태가 바뀌면 날짜를 채우거나 비운다 (design/05). 요청이 그 날짜를 직접 넣었으면 건드리지 않는다.
   * <ul>
   *   <li>완료(DONE) — 완료일이 비어 있으면 오늘 (종료일은 그대로)</li>
   *   <li>종료(CLOSED) — 종료일이 비어 있으면 오늘 (완료일은 그대로)</li>
   *   <li>신규 · 진행으로 되돌리면 — 완료일 · 종료일을 비운다</li>
   * </ul>
   */
  private void fillStatusDates(Values v, Issue.Status from, boolean completedGiven, boolean closedGiven) {
    LocalDate today = today();
    switch (v.status) {
      case DONE -> {
        if (!completedGiven && v.completedOn == null) {
          v.completedOn = today;
        }
      }
      case CLOSED -> {
        if (!closedGiven && v.closedOn == null) {
          v.closedOn = today;
        }
      }
      case NEW, IN_PROGRESS, TESTING -> {
        if (!completedGiven) {
          v.completedOn = null;
        }
        if (!closedGiven) {
          v.closedOn = null;
        }
      }
    }
  }

  private void validate(ProjectContext ctx, Values v, Issue current, boolean checkManager, boolean checkAssignee) {
    if (v.title == null || v.title.isBlank()) {
      throw ApiException.badRequest("INVALID_ISSUE_TITLE", "제목을 넣으십시오.");
    }
    if (v.title.trim().length() > MAX_TITLE) {
      throw ApiException.badRequest("INVALID_ISSUE_TITLE", "제목은 " + MAX_TITLE + "자를 넘을 수 없습니다.");
    }
    if (v.description.length() > MAX_TEXT) {
      throw ApiException.badRequest("ISSUE_DESCRIPTION_TOO_LONG", "설명은 " + MAX_TEXT + "자를 넘을 수 없습니다.");
    }
    IssueType type = v.type == null ? null : types.findById(v.type).orElse(null);
    if (type == null) {
      throw ApiException.badRequest("UNKNOWN_ISSUE_TYPE", "일감 유형이 없습니다: " + v.type);
    }
    // 더 쓰지 않는 옛 유형은 새로 고를 수 없다 — 이미 그 유형인 일감은 다른 것을 고칠 때 그대로 둘 수 있다
    if (!type.isActive() && (current == null || !type.getKey().equals(current.getIssueType()))) {
      throw ApiException.badRequest("ISSUE_TYPE_RETIRED", "더 쓰지 않는 일감 유형입니다: " + v.type);
    }
    if (v.status == null) {
      throw ApiException.badRequest("STATUS_REQUIRED", "상태를 넣으십시오.");
    }
    if (v.estimatedHours != null && (v.estimatedHours.signum() < 0 || v.estimatedHours.compareTo(MAX_ESTIMATE) > 0)) {
      throw ApiException.badRequest("INVALID_ESTIMATE", "예상 작업 시간은 0 ~ 10000 시간입니다.");
    }
    if (v.managerId == null) {
      throw ApiException.badRequest("ISSUE_MANAGER_REQUIRED", "관리자를 정하십시오.");
    }
    if (checkManager) {
      requireReader(ctx, v.managerId, "USER_NOT_PROJECT_READER", "관리자");
    }
    if (checkAssignee && v.assigneeId != null) {
      requireReader(ctx, v.assigneeId, "USER_NOT_PROJECT_READER", "담당자");
    }
    if (before(v.plannedEndDate, v.startDate) || before(v.dueDate, v.startDate)) {
      throw ApiException.badRequest("INVALID_ISSUE_DATES", "시작일은 완료 예정일 · 완료 기한보다 늦을 수 없습니다.");
    }
    if (before(v.closedOn, v.completedOn)) {
      throw ApiException.badRequest("INVALID_ISSUE_DATES", "완료일은 종료일보다 늦을 수 없습니다.");
    }
  }

  private LocalDate today() {
    return LocalDate.now(clock.withZone(ZONE));
  }

  /** 기한 지남 — 열린 일감(신규 · 진행)이고 완료 기한이 오늘보다 앞이다 */
  private static boolean overdue(Issue i, LocalDate today) {
    return i.getStatus().isOpen() && i.getDueDate() != null && i.getDueDate().isBefore(today);
  }

  private static boolean before(LocalDate a, LocalDate b) {
    return a != null && b != null && a.isBefore(b);
  }

  /** 관리자 · 담당자는 그 프로젝트의 독자인 활성 멤버여야 한다 — 판정은 REST 와 같은 서비스로 한다 */
  private void requireReader(ProjectContext ctx, UUID userId, String code, String what) {
    boolean reader = workspaceAccess.contextFor(ctx.workspace().workspace(), userId)
        .flatMap(ws -> projectAccess.contextFor(ws, ctx.project()))
        .filter(p -> p.has(Permission.ISSUE_READ)).isPresent();
    if (!reader) {
      throw ApiException.badRequest(code, what + "는 이 프로젝트를 볼 수 있는 워크스페이스 멤버여야 합니다.");
    }
  }

  /** 새 상위 일감 — 자기 아래로 옮기면 순환이고, 깊이는 맨 위부터 MAX_DEPTH 단계까지 */
  private void checkParent(UUID issueId, UUID newParentId) {
    int parentLevel = 0;
    UUID cursor = newParentId;
    while (cursor != null) {
      if (cursor.equals(issueId)) {
        throw ApiException.badRequest("PARENT_CYCLE", "자기 자신이나 자기 하위 일감을 상위 일감으로 둘 수 없습니다.");
      }
      parentLevel++;
      if (parentLevel > MAX_DEPTH) {
        break;
      }
      cursor = issues.findById(cursor).map(Issue::getParentId).orElse(null);
    }
    if (parentLevel + 1 + subtreeHeight(issueId) > MAX_DEPTH) {
      throw ApiException.badRequest("PARENT_DEPTH_EXCEEDED", "상위 · 하위는 " + MAX_DEPTH + "단계까지입니다.");
    }
  }

  /** 이 일감 아래로 몇 단계가 더 있나 (하위가 없으면 0) */
  private int subtreeHeight(UUID issueId) {
    int height = 0;
    Deque<UUID> level = new ArrayDeque<>(List.of(issueId));
    while (!level.isEmpty() && height <= MAX_DEPTH) {
      Deque<UUID> next = new ArrayDeque<>();
      for (UUID id : level) {
        issues.findChildren(id).forEach(c -> next.add(c.getId()));
      }
      if (next.isEmpty()) {
        break;
      }
      height++;
      level = next;
    }
    return height;
  }

  private static void copy(Values v, Issue issue) {
    issue.setIssueType(v.type);
    issue.setTitle(v.title.trim());
    issue.setDescription(v.description);
    issue.setStatus(v.status);
    issue.setManagerId(v.managerId);
    issue.setAssigneeId(v.assigneeId);
    issue.setParentId(v.parentId);
    issue.setDueDate(v.dueDate);
    issue.setStartDate(v.startDate);
    issue.setPlannedEndDate(v.plannedEndDate);
    issue.setCompletedOn(v.completedOn);
    issue.setClosedOn(v.closedOn);
    issue.setEstimatedHours(v.estimatedHours);
  }

  /** 바뀐 필드 — 이력에 남긴다. 사람은 {id, name}, 상위 일감은 번호, 설명은 전 · 후 글 전체 */
  private List<Map<String, Object>> diff(Values a, Values b) {
    List<Map<String, Object>> changes = new ArrayList<>();
    addIfChanged(changes, "type", a.type, b.type);
    addIfChanged(changes, "title", a.title, b.title == null ? null : b.title.trim());
    String beforeText = a.description == null ? "" : a.description;
    String afterText = b.description == null ? "" : b.description;
    if (!beforeText.equals(afterText)) {
      changes.add(change("description", beforeText.isEmpty() ? null : beforeText, afterText.isEmpty() ? null : afterText));
    }
    addIfChanged(changes, "status", a.status == null ? null : a.status.name(), b.status == null ? null : b.status.name());
    if (!Objects.equals(a.managerId, b.managerId)) {
      changes.add(change("manager", personValue(a.managerId), personValue(b.managerId)));
    }
    if (!Objects.equals(a.assigneeId, b.assigneeId)) {
      changes.add(change("assignee", personValue(a.assigneeId), personValue(b.assigneeId)));
    }
    if (!Objects.equals(a.parentId, b.parentId)) {
      changes.add(change("parent", numberOf(a.parentId), numberOf(b.parentId)));
    }
    addIfChanged(changes, "dueDate", a.dueDate, b.dueDate);
    addIfChanged(changes, "startDate", a.startDate, b.startDate);
    addIfChanged(changes, "plannedEndDate", a.plannedEndDate, b.plannedEndDate);
    addIfChanged(changes, "completedOn", a.completedOn, b.completedOn);
    addIfChanged(changes, "closedOn", a.closedOn, b.closedOn);
    if (a.estimatedHours == null ? b.estimatedHours != null
        : b.estimatedHours == null || a.estimatedHours.compareTo(b.estimatedHours) != 0) {
      changes.add(change("estimatedHours", plain(a.estimatedHours), plain(b.estimatedHours)));
    }
    return changes;
  }

  private static void addIfChanged(List<Map<String, Object>> changes, String field, Object before, Object after) {
    if (!Objects.equals(before, after)) {
      changes.add(change(field, before == null ? null : before.toString(), after == null ? null : after.toString()));
    }
  }

  private static Map<String, Object> change(String field, Object before, Object after) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("field", field);
    m.put("before", before);
    m.put("after", after);
    return m;
  }

  /** 이력의 연결 값 — 이 일감 쪽에서 본 방향(OUTGOING · INCOMING)과 상대 번호 */
  private static Map<String, Object> linkValue(IssueLink.Type type, String direction, Issue other) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("type", type.name());
    m.put("direction", direction);
    m.put("number", other.getNumber());
    return m;
  }

  private Map<String, Object> personValue(UUID id) {
    if (id == null) {
      return null;
    }
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("id", id.toString());
    m.put("name", users.findById(id).map(u -> u.getDisplayName() == null ? u.getUsername() : u.getDisplayName())
        .orElse(id.toString()));
    return m;
  }

  private Integer numberOf(UUID issueId) {
    return issueId == null ? null : issues.findById(issueId).map(Issue::getNumber).orElse(null);
  }

  /** 첨부를 더하거나 뺀 것을 이력에 남긴다 — 판은 만들지 않는다(댓글 · 작업 시간처럼). AttachmentService 가 부른다 */
  void journalAttachment(UUID issueId, Actor actor, IssueJournal.Via via, Map<String, Object> before,
      Map<String, Object> after) {
    journal(issueId, actor, via, null, List.of(change("attachment", before, after)));
  }

  private IssueJournal journal(UUID issueId, Actor actor, IssueJournal.Via via, String notes,
      List<Map<String, Object>> changes) {
    String actorType = actor.type() == Actor.Type.USER ? "USER" : "SERVICE";
    return journals.save(new IssueJournal(issueId, actor.type() == Actor.Type.USER ? actor.id() : null, actorType,
        actor.name() == null ? actorType : actor.name(), via, Instant.now(clock), notes,
        json.writeValueAsString(changes)));
  }

  /** 새 판을 남긴다 — 일감의 판 번호를 올리고, 그 시점의 모습 · 누가 · 어느 길로를 담는다. 이력 줄에 판 번호를 적는다 */
  private void revise(Issue issue, IssueJournal line, Actor actor, IssueJournal.Via via) {
    int no = issue.nextRevision();
    String actorType = actor.type() == Actor.Type.USER ? "USER" : "SERVICE";
    revisions.save(IssueRevision.of(issue, no, line.getId(), numberOf(issue.getParentId()),
        actor.type() == Actor.Type.USER ? actor.id() : null, actorType,
        actor.name() == null ? actorType : actor.name(), via, Instant.now(clock)));
    line.setRevisionNo(no);
  }

  private static String plain(BigDecimal value) {
    return value == null ? null : value.stripTrailingZeros().toPlainString();
  }

  private static void requireOpen(ProjectContext ctx) {
    if (!ctx.project().isOpenToReaders()) {
      throw ApiException.conflict("PROJECT_NOT_WRITABLE", "보관한 프로젝트의 일감은 바꿀 수 없습니다. 먼저 복원하십시오.");
    }
  }

  private static UUID requirePerson(Actor actor) {
    if (actor.id() == null) {
      throw ApiException.forbidden("USER_ONLY", "사람이나 서비스 계정만 일감을 쓸 수 있습니다.");
    }
    return actor.id();
  }

  /** 상위로 둘 일감 — 요청 본문의 번호라서 없으면 400 */
  private Issue requireParent(ProjectContext ctx, int number) {
    return issues.findLive(ctx.projectId(), number)
        .orElseThrow(() -> ApiException.badRequest("PARENT_NOT_FOUND", "이 프로젝트에 상위로 둘 일감이 없습니다: #" + number));
  }

  private Issue requireIssue(ProjectContext ctx, int number) {
    return issues.findLive(ctx.projectId(), number)
        .orElseThrow(() -> ApiException.notFound("ISSUE_NOT_FOUND", "일감을 찾을 수 없습니다: #" + number));
  }

  private Map<UUID, UserProfile> people(Collection<UUID> ids) {
    Set<UUID> wanted = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
    Map<UUID, UserProfile> map = new HashMap<>();
    users.findAllById(wanted).forEach(u -> map.put(u.getId(), u));
    return map;
  }

  private Map<UUID, Integer> numbersOf(Set<UUID> ids) {
    Map<UUID, Integer> map = new HashMap<>();
    issues.findAllById(ids).forEach(i -> map.put(i.getId(), i.getNumber()));
    return map;
  }

  private static IssueViews.Person person(Map<UUID, UserProfile> people, UUID id) {
    if (id == null) {
      return null;
    }
    UserProfile u = people.get(id);
    return u == null ? new IssueViews.Person(id, null, null)
        : new IssueViews.Person(u.getId(), u.getUsername(), u.getDisplayName());
  }

  private static IssueViews.Ref ref(Issue i) {
    return new IssueViews.Ref(i.getNumber(), i.getTitle(), i.getStatus(), i.getIssueType());
  }

  // ── 요청 값 읽기 ──────────────────────────────────────────────────

  private static String text(Map<String, Object> body, String key) {
    Object v = body.get(key);
    if (v == null || v instanceof String) {
      return (String) v;
    }
    throw ApiException.badRequest("INVALID_FIELD", key + " 는 글자여야 합니다.");
  }

  private static UUID uuid(Map<String, Object> body, String key) {
    String v = text(body, key);
    if (v == null || v.isBlank()) {
      return null;
    }
    try {
      return UUID.fromString(v);
    } catch (IllegalArgumentException e) {
      throw ApiException.badRequest("INVALID_FIELD", key + " 가 올바른 id 가 아닙니다.");
    }
  }

  private static LocalDate date(Map<String, Object> body, String key) {
    String v = text(body, key);
    if (v == null || v.isBlank()) {
      return null;
    }
    try {
      return LocalDate.parse(v);
    } catch (DateTimeParseException e) {
      throw ApiException.badRequest("INVALID_FIELD", key + " 는 yyyy-MM-dd 날짜여야 합니다.");
    }
  }

  private static BigDecimal hours(Map<String, Object> body, String key) {
    Object v = body.get(key);
    if (v == null) {
      return null;
    }
    if (!(v instanceof Number n)) {
      throw ApiException.badRequest("INVALID_FIELD", key + " 는 숫자(시간)여야 합니다.");
    }
    BigDecimal value = new BigDecimal(n.toString()).setScale(2, java.math.RoundingMode.HALF_UP);
    if (value.signum() < 0 || value.compareTo(MAX_ESTIMATE) > 0) {
      throw ApiException.badRequest("INVALID_ESTIMATE", "예상 작업 시간은 0 ~ 10000 시간입니다.");
    }
    return value;
  }

  private static Issue.Status enumValue(Map<String, Object> body, String key) {
    String v = text(body, key);
    if (v == null) {
      return null;
    }
    try {
      return Issue.Status.valueOf(v);
    } catch (IllegalArgumentException e) {
      throw ApiException.badRequest("INVALID_ISSUE_STATUS", "상태는 NEW · IN_PROGRESS · TESTING · DONE · CLOSED 가운데 하나입니다.");
    }
  }
}
