package com.moai.works.api.issue;

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
      "notes");

  private final IssueRepositories.Issues issues;
  private final IssueRepositories.Types types;
  private final IssueRepositories.Links links;
  private final IssueRepositories.Journals journals;
  private final ProjectRepository projects;
  private final ProjectAccessService projectAccess;
  private final WorkspaceAccessService workspaceAccess;
  private final UserProfileRepository users;
  private final AuditService audit;
  private final JsonMapper json;
  private final TransactionTemplate tx;
  private final Clock clock;

  public IssueService(IssueRepositories.Issues issues, IssueRepositories.Types types, IssueRepositories.Links links,
      IssueRepositories.Journals journals, ProjectRepository projects, ProjectAccessService projectAccess,
      WorkspaceAccessService workspaceAccess, UserProfileRepository users, AuditService audit, JsonMapper json,
      PlatformTransactionManager txManager, Clock clock) {
    this.issues = issues;
    this.types = types;
    this.links = links;
    this.journals = journals;
    this.projects = projects;
    this.projectAccess = projectAccess;
    this.workspaceAccess = workspaceAccess;
    this.users = users;
    this.audit = audit;
    this.json = json;
    this.tx = new TransactionTemplate(txManager);
    this.clock = clock;
  }

  // ── 유형 ──────────────────────────────────────────────────────────

  @Transactional(readOnly = true)
  public List<IssueViews.Type> types() {
    return types.findAllByOrderByPositionAsc().stream()
        .map(t -> new IssueViews.Type(t.getKey(), t.getName(), t.getDescription())).toList();
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
  public record ListQuery(Collection<Issue.Status> statuses, Collection<String> types, UUID manager, UUID assignee,
      boolean noAssignee, Integer parent, boolean topLevel, String q, String sort, int page, int size) {
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
      spec = spec.and((root, cq, cb) -> number != null
          ? cb.or(cb.equal(root.get("number"), number), cb.like(cb.lower(root.get("title")), like, '\\'))
          : cb.like(cb.lower(root.get("title")), like, '\\'));
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
    LocalDate today = today();
    List<IssueViews.Summary> items = result.getContent().stream().map(i -> new IssueViews.Summary(i.getNumber(),
        i.getIssueType(), i.getTitle(), i.getStatus(), person(people, i.getManagerId()),
        person(people, i.getAssigneeId()), i.getParentId() == null ? null : parentNumbers.get(i.getParentId()),
        i.getDueDate(), i.getStartDate(), i.getPlannedEndDate(), i.getCompletedOn(), i.getClosedOn(),
        i.getUpdatedAt(), childCounts.getOrDefault(i.getId(), 0L).intValue(), overdue(i, today))).toList();
    return new IssueViews.Page(items, result.getTotalElements(), page, size);
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
      case "status" -> "status";
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
      LocalDate completedOn, LocalDate closedOn) {
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
      // 만들기도 이력의 첫 줄 — 처음 값을 전 값 없이 담는다(웹 · MCP 어느 길로 만들었는지 보인다)
      journal(issue.getId(), actor, via, null, diff(new Values(), v));
      audit.record(actor, ctx.workspaceId(), "issue.create", "issue", issue.getId().toString(),
          Map.of("project", project.getKey(), "number", number, "title", issue.getTitle(), "via", via.name()));
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
    List<IssueViews.Journal> journal = journals.findByIssueIdOrderByCreatedAtAscIdAsc(issue.getId()).stream()
        .map(j -> new IssueViews.Journal(j.getId(), j.getActorType(), j.getActorId(), j.getActorName(), j.getVia(),
            j.getCreatedAt(), j.getNotes(), json.readValue(j.getChanges(), new TypeReference<List<Map<String, Object>>>() {
            })))
        .toList();
    Issue parent = issue.getParentId() == null ? null : others.get(issue.getParentId());
    return new IssueViews.Detail(issue.getNumber(), issue.getIssueType(), issue.getTitle(), issue.getDescription(),
        issue.getStatus(), person(people, issue.getManagerId()), person(people, issue.getAssigneeId()),
        parent == null ? null : ref(parent), issue.getDueDate(), issue.getStartDate(), issue.getPlannedEndDate(),
        issue.getCompletedOn(), issue.getClosedOn(), person(people, issue.getCreatedBy()), issue.getCreatedAt(),
        issue.getUpdatedAt(), issue.getRowVersion(), overdue(issue, today()),
        children.stream().map(IssueService::ref).toList(), linkViews, journal);
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
      journal(issue.getId(), actor, via, finalNotes, changes);
      if (finalNotes != null) {
        audit.record(actor, ctx.workspaceId(), "issue.comment", "issue", issue.getId().toString(),
            Map.of("project", ctx.project().getKey(), "number", number, "via", via.name()));
      }
    });
    return detail(ctx, number);
  }

  // ── 댓글 ──────────────────────────────────────────────────────────

  public IssueViews.Detail comment(ProjectContext ctx, int number, String notes, Actor actor, IssueJournal.Via via) {
    requireOpen(ctx);
    if (notes == null || notes.isBlank()) {
      throw ApiException.badRequest("INVALID_COMMENT", "댓글 내용을 넣으십시오.");
    }
    if (notes.length() > MAX_TEXT) {
      throw ApiException.badRequest("COMMENT_TOO_LONG", "댓글은 " + MAX_TEXT + "자를 넘을 수 없습니다.");
    }
    Issue issue = requireIssue(ctx, number);
    tx.executeWithoutResult(s -> {
      journal(issue.getId(), actor, via, notes, List.of());
      audit.record(actor, ctx.workspaceId(), "issue.comment", "issue", issue.getId().toString(),
          Map.of("project", ctx.project().getKey(), "number", number, "via", via.name()));
    });
    return detail(ctx, number);
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
            IssueJournal.Via.WEB, null, List.of(change("link", linkValue(link.getType(), otherDirection, locked), null))));
      }
      locked.markDeleted(Instant.now(clock));
      audit.record(actor, ctx.workspaceId(), "issue.delete", "issue", locked.getId().toString(),
          Map.of("project", ctx.project().getKey(), "number", locked.getNumber(), "title", locked.getTitle()));
    });
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
      case NEW, IN_PROGRESS -> {
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
    if (v.type == null || types.findById(v.type).isEmpty()) {
      throw ApiException.badRequest("UNKNOWN_ISSUE_TYPE", "일감 유형이 없습니다: " + v.type);
    }
    if (v.status == null) {
      throw ApiException.badRequest("STATUS_REQUIRED", "상태를 넣으십시오.");
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

  private void journal(UUID issueId, Actor actor, IssueJournal.Via via, String notes,
      List<Map<String, Object>> changes) {
    String actorType = actor.type() == Actor.Type.USER ? "USER" : "SERVICE";
    journals.save(new IssueJournal(issueId, actor.type() == Actor.Type.USER ? actor.id() : null, actorType,
        actor.name() == null ? actorType : actor.name(), via, Instant.now(clock), notes,
        json.writeValueAsString(changes)));
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

  private static Issue.Status enumValue(Map<String, Object> body, String key) {
    String v = text(body, key);
    if (v == null) {
      return null;
    }
    try {
      return Issue.Status.valueOf(v);
    } catch (IllegalArgumentException e) {
      throw ApiException.badRequest("INVALID_ISSUE_STATUS", "상태는 NEW · IN_PROGRESS · DONE · CLOSED 가운데 하나입니다.");
    }
  }
}
