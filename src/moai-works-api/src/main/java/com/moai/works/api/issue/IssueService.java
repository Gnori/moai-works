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
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
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
import com.moai.works.api.project.ProjectModule;
import com.moai.works.api.project.ProjectModuleService;
import com.moai.works.api.project.ProjectRepository;
import com.moai.works.api.notification.NotificationKind;
import com.moai.works.api.notification.NotificationService;
import com.moai.works.api.security.access.ProjectContext;
import com.moai.works.api.security.access.WorkspaceContext;
import com.moai.works.api.workspace.Permission;
import com.moai.works.api.workspace.WorkspaceAccessService;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * 일감 — 만들기 · 보기 · 고치기(옮기기 포함) · 지우기 · 댓글 · 연결 · 목록 (design/05). moai-wiki 의 페이지 서비스 자리다.
 *
 * <p>일감은 워크스페이스 아래에 있고 번호는 워크스페이스 안에서 하나다. 프로젝트는 일감의 속성이라 옮길 수 있다(Redmine 모델, ADR-030).
 * 권한 경계는 그대로 그 일감의 지금 프로젝트다 — 누가 부를 수 있는지(그 프로젝트의 독자인가 · 코드가 있나)는 {@code @ProjectApi} ·
 * {@code @IssueApi} 가 이미 판정했다. 여기서는 일감마다의 규칙(관리자 바꾸기는 issue.manager.assign, 보관한 프로젝트는 쓰기 금지,
 * 상위 일감 순환 · 깊이, 날짜 순서, 옮기기는 옮길 프로젝트의 issue.create)을 본다. MCP 도구도 이 서비스를 그대로 부른다(같은 판정 — design/06).
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
      "notes", "estimatedHours", "agentSession", "modules", "project");
  static final BigDecimal MAX_ESTIMATE = new BigDecimal("10000");
  static final BigDecimal MAX_WORK_HOURS = new BigDecimal("24");
  static final int REVISION_PAGE = 50;
  /** 댓글 목록 한 쪽 상한 — 댓글 하나가 3만 자까지라 너무 크게 받지 않는다 */
  static final int MAX_COMMENT_PAGE = 50;
  /** 에이전트 세션 이름 상한 (V20 의 CHECK 와 같다) */
  static final int MAX_AGENT_SESSION = 100;

  private final IssueRepositories.Issues issues;
  private final IssueRepositories.Types types;
  private final IssueRepositories.Links links;
  private final IssueRepositories.Journals journals;
  private final IssueRepositories.CommentRevisions commentRevisions;
  private final NotificationService notifications;
  private final IssueRepositories.Revisions revisions;
  private final IssueRepositories.WorkLogs workLogs;
  private final IssueRepositories.IssueModules issueModules;
  private final ProjectModuleService moduleService;
  private final ProjectRepository projects;
  private final ProjectAccessService projectAccess;
  private final WorkspaceAccessService workspaceAccess;
  private final UserProfileRepository users;
  private final AuditService audit;
  private final JsonMapper json;
  private final NamedParameterJdbcTemplate jdbc;
  private final TransactionTemplate tx;
  private final Clock clock;

  public IssueService(IssueRepositories.Issues issues, IssueRepositories.Types types, IssueRepositories.Links links,
      IssueRepositories.Journals journals, IssueRepositories.CommentRevisions commentRevisions,
      IssueRepositories.Revisions revisions, IssueRepositories.WorkLogs workLogs, IssueRepositories.IssueModules issueModules,
      ProjectModuleService moduleService, ProjectRepository projects, ProjectAccessService projectAccess,
      WorkspaceAccessService workspaceAccess, UserProfileRepository users, AuditService audit, JsonMapper json,
      NotificationService notifications, NamedParameterJdbcTemplate jdbc, PlatformTransactionManager txManager,
      Clock clock) {
    this.issues = issues;
    this.types = types;
    this.links = links;
    this.journals = journals;
    this.commentRevisions = commentRevisions;
    this.revisions = revisions;
    this.workLogs = workLogs;
    this.issueModules = issueModules;
    this.moduleService = moduleService;
    this.projects = projects;
    this.projectAccess = projectAccess;
    this.workspaceAccess = workspaceAccess;
    this.users = users;
    this.audit = audit;
    this.json = json;
    this.notifications = notifications;
    this.jdbc = jdbc;
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
   * @param agentSession 이 에이전트 세션의 일감만(대소문자 무시) · {@code noAgentSession} 세션이 비어 있는 것만
   * @param modules  이 모듈 key 가운데 하나라도 붙은 일감만(비면 전부). 이 프로젝트에 없는 key 는 400 UNKNOWN_MODULE
   */
  public record ListQuery(Collection<Issue.Status> statuses, Collection<String> types, UUID manager, UUID assignee,
      boolean noAssignee, Integer parent, boolean topLevel, String q, LocalDate dueFrom, LocalDate dueTo,
      boolean overdue, String agentSession, boolean noAgentSession, Collection<String> modules, String sort, int page,
      int size) {
  }

  /** 프로젝트 하나의 일감 목록 */
  @Transactional(readOnly = true)
  public IssueViews.Page list(ProjectContext ctx, ListQuery query) {
    return listIn(ctx.workspace(), List.of(ctx), query, true);
  }

  /**
   * 워크스페이스 일감 목록 — 내가 읽을 수 있는 프로젝트(열려 있고 issue.read 가 있는 것) 모두의 일감(Redmine 의 {@code /issues}).
   * 읽을 수 없는 프로젝트의 일감은 목록에도 · 전체 수에도 들어가지 않는다. 판정은 프로젝트마다 {@link ProjectAccessService} 그대로다.
   *
   * @param projectKeys 이 프로젝트들만(비면 읽을 수 있는 모두)
   * @param strict      true 면 읽을 수 없거나 없는 key 를 404 PROJECT_NOT_FOUND 로 알린다(MCP). false 면 그 key 는 빠진다(화면 — 주소에 남은 옛 조건)
   */
  @Transactional(readOnly = true)
  public IssueViews.Page listWorkspace(WorkspaceContext ws, Collection<String> projectKeys, boolean strict,
      ListQuery query) {
    List<ProjectContext> readable = readableProjects(ws);
    List<ProjectContext> chosen = readable;
    if (projectKeys != null && !projectKeys.isEmpty()) {
      Set<String> keys = projectKeys.stream().filter(Objects::nonNull).map(String::trim).filter(k -> !k.isEmpty())
          .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
      chosen = readable.stream().filter(p -> keys.contains(p.project().getKey())).toList();
      if (strict) {
        Set<String> found = chosen.stream().map(p -> p.project().getKey()).collect(Collectors.toSet());
        List<String> missing = keys.stream().filter(k -> !found.contains(k)).toList();
        if (!missing.isEmpty()) {
          throw ApiException.notFound("PROJECT_NOT_FOUND", "프로젝트를 찾을 수 없습니다: " + String.join(", ", missing));
        }
      }
    }
    return listIn(ws, chosen, query, chosen.size() == 1 && projectKeys != null && !projectKeys.isEmpty());
  }

  /** 이 사람이 일감을 읽을 수 있는 프로젝트들 — 열려 있고(보관 · 지움 아님) 독자이고 issue.read 가 있는 것, 이름순 */
  public List<ProjectContext> readableProjects(WorkspaceContext ws) {
    return projects.findLiveByWorkspace(ws.workspaceId()).stream().filter(Project::isOpenToReaders)
        .map(p -> projectAccess.contextFor(ws, p)).flatMap(java.util.Optional::stream)
        .filter(c -> c.has(Permission.ISSUE_READ)).toList();
  }

  /** 워크스페이스에서 쓰인 에이전트 세션 이름들 — 읽을 수 있는 프로젝트의 일감에서만 */
  @Transactional(readOnly = true)
  public List<IssueViews.AgentSession> agentSessions(WorkspaceContext ws) {
    List<UUID> ids = readableProjects(ws).stream().map(ProjectContext::projectId).toList();
    return ids.isEmpty() ? List.of() : agentSessionsOf(ids);
  }

  /** 워크스페이스 일감 목록의 모듈 거르기 칸 — 읽을 수 있는 프로젝트들의 모듈 key(같은 key 는 처음 것의 이름으로 하나) */
  @Transactional(readOnly = true)
  public List<IssueViews.ModuleOption> moduleOptions(WorkspaceContext ws) {
    Map<String, IssueViews.ModuleOption> byKey = new java.util.TreeMap<>();
    for (ProjectContext p : readableProjects(ws)) {
      moduleService.byProject(p.projectId()).forEach(m -> byKey.putIfAbsent(m.getKey(),
          new IssueViews.ModuleOption(m.getKey(), m.getName())));
    }
    return List.copyOf(byKey.values());
  }

  /**
   * 목록 — 이 프로젝트들의 일감. 프로젝트가 없으면 빈 쪽.
   *
   * @param single 프로젝트 하나를 골라서 부른 것인가 — 모듈 거르기의 없는 key 를 그 프로젝트 기준으로 알린다
   */
  private IssueViews.Page listIn(WorkspaceContext ws, List<ProjectContext> scope, ListQuery query, boolean single) {
    int size = Math.clamp(query.size() <= 0 ? 50 : query.size(), 1, 100);
    int page = Math.max(0, query.page());
    sortOf(query.sort());   // 잘못된 정렬은 프로젝트가 없어도 알린다
    if (scope.isEmpty()) {
      return new IssueViews.Page(List.of(), 0, page, size, 0, false);
    }
    Map<UUID, Project> projectById = scope.stream().collect(Collectors.toMap(ProjectContext::projectId,
        ProjectContext::project, (a, b) -> a));
    Set<UUID> projectIds = projectById.keySet();
    UUID workspaceId = ws.workspaceId();
    Specification<Issue> spec = (root, cq, cb) -> cb.and(cb.equal(root.get("workspaceId"), workspaceId),
        root.get("projectId").in(projectIds), cb.isNull(root.get("deletedAt")));
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
      // 상위 번호도 워크스페이스 번호 — 고른 프로젝트 밖(읽을 수 없는 것 포함)이면 아무것도 나오지 않는다
      UUID parentId = issues.findLiveInWorkspace(workspaceId, query.parent())
          .filter(i -> projectIds.contains(i.getProjectId())).map(Issue::getId).orElse(null);
      spec = spec.and((root, cq, cb) -> parentId == null ? cb.disjunction() : cb.equal(root.get("parentId"), parentId));
    }
    String q = query.q() == null ? "" : query.q().trim();
    if (!q.isEmpty()) {
      Integer number = parseNumber(q);
      String like = "%" + q.toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
      // 제목 · 설명 어디에 있든 찾는다. 번호 꼴(#12 · 12)이면 그 번호도 — 번호는 워크스페이스에서 하나다
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
    if (query.noAgentSession()) {
      spec = spec.and((root, cq, cb) -> cb.isNull(root.get("agentSession")));
    } else if (query.agentSession() != null && !query.agentSession().isBlank()) {
      String session = query.agentSession().trim().toLowerCase(java.util.Locale.ROOT);
      spec = spec.and((root, cq, cb) -> cb.equal(cb.lower(root.get("agentSession")), session));
    }
    if (query.modules() != null && !query.modules().isEmpty()) {
      // 거르기는 쓰지 않는 모듈도 된다(옛 일감을 찾는다) — 없는 key 만 거절한다. 여러 프로젝트면 같은 key 의 모듈 모두
      List<UUID> moduleIds = (single ? moduleService.resolveForFilter(scope.get(0).projectId(), query.modules())
          : moduleService.resolveForFilterIn(projectIds, query.modules())).stream().map(ProjectModule::getId).toList();
      spec = spec.and((root, cq, cb) -> {
        jakarta.persistence.criteria.Subquery<UUID> sub = cq.subquery(UUID.class);
        jakarta.persistence.criteria.Root<IssueModule> link = sub.from(IssueModule.class);
        sub.select(link.get("issueId")).where(cb.equal(link.get("issueId"), root.get("id")), link.get("moduleId").in(moduleIds));
        return cb.exists(sub);
      });
    }
    if (query.overdue()) {
      LocalDate todayForOverdue = today();
      spec = spec.and((root, cq, cb) -> cb.and(cb.lessThan(root.get("dueDate"), todayForOverdue),
          root.get("status").in(Issue.Status.open())));
    }
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
    Map<UUID, List<IssueViews.ModuleRef>> moduleRefs = moduleRefsOf(result.getContent().stream().map(Issue::getId).toList());
    Map<UUID, Instant> statusSince = statusChangedAtOf(result.getContent().stream().map(Issue::getId).toList());
    LocalDate today = today();
    List<IssueViews.Summary> items = result.getContent().stream().map(i -> new IssueViews.Summary(i.getNumber(),
        projectRef(projectById.get(i.getProjectId())), i.getIssueType(), i.getTitle(), i.getStatus(),
        person(people, i.getManagerId()), person(people, i.getAssigneeId()),
        i.getParentId() == null ? null : parentNumbers.get(i.getParentId()),
        i.getDueDate(), i.getStartDate(), i.getPlannedEndDate(), i.getCompletedOn(), i.getClosedOn(),
        i.getUpdatedAt(), childCounts.getOrDefault(i.getId(), 0L).intValue(), overdue(i, today), i.getEstimatedHours(),
        spentById.getOrDefault(i.getId(), BigDecimal.ZERO), i.getAgentSession(),
        moduleRefs.getOrDefault(i.getId(), List.of()), i.getRowVersion(),
        statusSince.getOrDefault(i.getId(), i.getCreatedAt()))).toList();
    return new IssueViews.Page(items, result.getTotalElements(), page, size, result.getTotalPages(), result.hasNext());
  }

  /**
   * 한 쪽 일감들의 지금 상태가 된 때 — 이력에서 상태가 지금 값으로 바뀐 마지막 줄의 시각(질의 하나, {@link #statusChangedAt} 와 같은 규칙).
   * 그런 줄이 없는 일감(옛 데이터)은 빠진다 — 부르는 쪽이 만든 때로 채운다(일감 #199)
   */
  private Map<UUID, Instant> statusChangedAtOf(List<UUID> issueIds) {
    Map<UUID, Instant> out = new HashMap<>();
    if (issueIds.isEmpty()) {
      return out;
    }
    jdbc.query("SELECT j.issue_id, max(j.created_at) AS changed_at FROM issue_journal j JOIN issue i ON i.id = j.issue_id "
        + "WHERE j.issue_id IN (:ids) AND j.changes @> jsonb_build_array(jsonb_build_object('field', 'status', 'after', i.status)) "
        + "GROUP BY j.issue_id", new MapSqlParameterSource("ids", issueIds),
        (org.springframework.jdbc.core.RowCallbackHandler) rs ->
            out.put(rs.getObject(1, UUID.class), rs.getObject(2, java.time.OffsetDateTime.class).toInstant()));
    return out;
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
      case "status" -> "statusRank";   // 등록 < 진행 < 테스트 < 보류 < 완료 < 종료 < 취소 (코드값 글자순이 아니라)
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

  /**
   * 만들 때 넣는 값. 비운 값은 기본값(상태 NEW · 관리자 = 만든 사람).
   *
   * @param agentSession 이 일을 맡은 AI Agent 세션 이름 — 없어도 된다(앞뒤 공백을 빼고 비면 없음)
   * @param modules      관련 모듈 key 들 — 없어도 된다. 쓰는 모듈만(없는 key 400 UNKNOWN_MODULE · 쓰지 않는 모듈 400 MODULE_INACTIVE)
   */
  public record CreateCommand(String type, String title, String description, Issue.Status status, UUID managerId,
      UUID assigneeId, Integer parent, LocalDate dueDate, LocalDate startDate, LocalDate plannedEndDate,
      LocalDate completedOn, LocalDate closedOn, BigDecimal estimatedHours, String agentSession, List<String> modules) {
  }

  public IssueViews.Detail create(ProjectContext ctx, CreateCommand c, Actor actor, IssueJournal.Via via) {
    requireOpen(ctx);
    UUID creator = requirePerson(actor);
    Values v = new Values();
    v.type = c.type();
    v.title = c.title();
    v.description = c.description() == null ? "" : c.description();
    v.status = c.status() == null ? Issue.Status.NEW : c.status();
    if (v.status.needsReason()) {
      // 보류 · 취소는 까닭(댓글)과 함께 옮기는 상태다 — 만들기에는 댓글이 없으니 만든 뒤 까닭과 함께 옮긴다
      throw ApiException.badRequest("STATUS_REASON_REQUIRED",
          "보류 · 취소로는 만들 수 없습니다. 일감을 만든 뒤 상태를 바꾸면서 까닭(댓글)을 함께 넣으십시오.");
    }
    v.managerId = c.managerId() == null ? creator : c.managerId();
    v.assigneeId = c.assigneeId();
    v.parentId = c.parent() == null ? null : requireParent(ctx, c.parent()).getId();
    v.dueDate = c.dueDate();
    v.startDate = c.startDate();
    v.plannedEndDate = c.plannedEndDate();
    v.completedOn = c.completedOn();
    v.closedOn = c.closedOn();
    v.estimatedHours = c.estimatedHours();
    v.agentSession = agentSession(c.agentSession());
    v.moduleIds = moduleIds(moduleService.resolve(ctx.projectId(), c.modules(), Set.of()));
    // 만들 때부터 완료 · 종료면 그날을 채운다
    fillStatusDates(v, null, c.completedOn() != null, c.closedOn() != null);
    if (!v.managerId.equals(creator) && !ctx.has(Permission.ISSUE_MANAGER_ASSIGN)) {
      throw ApiException.forbidden("PERMISSION_REQUIRED", "다른 사람을 관리자로 정하려면 권한 issue.manager.assign 이(가) 필요합니다.");
    }
    validate(ctx, v, null, true, true);

    Issue saved = tx.execute(s -> {
      Project project = ctx.project();
      int number = nextNumber(ctx.workspaceId());
      Instant now = Instant.now(clock);
      Issue issue = new Issue(ctx.workspaceId(), project.getId(), number, v.type, v.title.trim(), v.description,
          v.managerId, creator, now);
      copy(v, issue);
      issue = issues.saveAndFlush(issue);
      syncModules(issue, List.of(), v.moduleIds);
      // 만들기도 이력의 첫 줄이자 1판 — 처음 값을 전 값 없이 담는다(누가 · 웹 · MCP 어느 길로 만들었는지 보인다)
      IssueJournal first = journal(issue.getId(), actor, via, null, diff(new Values(), v));
      revise(issue, first, v.moduleIds, actor, via);
      audit.record(actor, ctx.workspaceId(), "issue.create", "issue", issue.getId().toString(),
          Map.of("project", project.getKey(), "number", number, "title", issue.getTitle(), "via", via.name()));
      notifyCreated(ctx, issue, actor, via);
      return issue;
    });
    return detail(ctx, saved.getNumber());
  }

  /**
   * 워크스페이스의 다음 일감 번호 — 워크스페이스 행을 잠그고(UPDATE … RETURNING) 순번을 1 올린다. 같은 워크스페이스의 만들기는
   * 어느 프로젝트든 줄을 서고, 만들기가 되돌려지면 번호도 되돌려진다. 지운 일감의 번호는 다시 쓰지 않는다(순번만 오른다).
   * 워크스페이스 행의 row_version · 고친 때는 건드리지 않는다(워크스페이스 설정 고치기와 부딪히지 않게). 이 트랜잭션 안에서 부른다
   */
  private int nextNumber(UUID workspaceId) {
    Integer next = jdbc.queryForObject("UPDATE workspace SET issue_seq = issue_seq + 1 WHERE id = :id RETURNING issue_seq",
        new MapSqlParameterSource("id", workspaceId), Integer.class);
    if (next == null) {
      throw new IllegalStateException("워크스페이스가 없습니다: " + workspaceId);
    }
    return next;
  }

  /**
   * 옛 링크가 가리키는 일감의 지금 번호 — 옛 주소 {@code /w/…/projects/<key>/issues/<번호>} 의 번호는 V23 전의 프로젝트 안 번호다.
   * 번호 바꾼 기록(issue_number_change)으로 그 일감을 찾고, 기록이 없으면(V23 뒤의 일감) 그 프로젝트의 그 번호 일감을 본다.
   * 그 일감이 없거나 지금 읽을 수 없으면 404 ISSUE_NOT_FOUND(어느 쪽인지 숨긴다).
   */
  @Transactional(readOnly = true)
  public IssueViews.IssueNumber legacyNumber(WorkspaceContext ws, String projectKey, int number) {
    ApiException notFound = ApiException.notFound("ISSUE_NOT_FOUND", "일감을 찾을 수 없습니다.");
    Project project = projects.findLive(ws.workspaceId(), projectKey == null ? "" : projectKey.trim())
        .orElseThrow(() -> notFound);
    List<UUID> mapped = jdbc.queryForList("SELECT issue_id FROM issue_number_change WHERE project_id = :p AND old_number = :n",
        new MapSqlParameterSource("p", project.getId()).addValue("n", number), UUID.class);
    java.util.Optional<Issue> issue = mapped.isEmpty()
        ? issues.findLiveInWorkspace(ws.workspaceId(), number).filter(i -> i.getProjectId().equals(project.getId()))
        : issues.findById(mapped.get(0)).filter(i -> !i.isDeleted());
    return issue.filter(i -> readable(ws, i.getProjectId()).isPresent()).map(i -> new IssueViews.IssueNumber(i.getNumber()))
        .orElseThrow(() -> notFound);
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
    // 연결은 다른 프로젝트의 일감일 수 있다 — 지금 읽을 수 있는 프로젝트의 것만 내준다(읽을 수 없는 것은 있다는 것도 숨긴다)
    Map<UUID, java.util.Optional<ProjectContext>> readableById = new HashMap<>();
    readableById.put(ctx.projectId(), java.util.Optional.of(ctx));
    List<IssueViews.Link> linkViews = new ArrayList<>();
    for (IssueLink l : touching) {
      boolean outgoing = l.getSourceIssueId().equals(issue.getId());
      Issue other = others.get(outgoing ? l.getTargetIssueId() : l.getSourceIssueId());
      if (other == null) {
        continue;
      }
      java.util.Optional<ProjectContext> otherProject = readableById.computeIfAbsent(other.getProjectId(),
          id -> readable(ctx.workspace(), id));
      if (otherProject.isPresent()) {
        linkViews.add(new IssueViews.Link(l.getId(), l.getType(), outgoing ? "OUTGOING" : "INCOMING",
            ref(other, otherProject.get().project())));
      }
    }
    List<IssueJournal> lines = journals.findByIssueIdOrderByCreatedAtAscIdAsc(issue.getId());
    Map<UUID, UserProfile> deleters = people(lines.stream().map(IssueJournal::getNotesDeletedBy).toList());
    List<IssueViews.Journal> journal = lines.stream().map(j -> journalView(j, deleters)).toList();
    List<IssueWorkLog> logs = workLogs.findLive(issue.getId());
    List<IssueViews.WorkLog> logViews = workLogViews(logs);
    BigDecimal spent = logs.stream().map(IssueWorkLog::getHours).reduce(BigDecimal.ZERO, BigDecimal::add);
    Issue parent = issue.getParentId() == null ? null : others.get(issue.getParentId());
    return new IssueViews.Detail(issue.getNumber(), projectRef(ctx.project()), issue.getIssueType(), issue.getTitle(),
        issue.getDescription(),
        issue.getStatus(), person(people, issue.getManagerId()), person(people, issue.getAssigneeId()),
        parent == null ? null : ref(parent, ctx.project()), issue.getDueDate(), issue.getStartDate(), issue.getPlannedEndDate(),
        issue.getCompletedOn(), issue.getClosedOn(), person(people, issue.getCreatedBy()), issue.getCreatedAt(),
        issue.getUpdatedAt(), statusChangedAt(journal, issue), issue.getRowVersion(), overdue(issue, today()),
        issue.getRevisionNo(),
        issue.getEstimatedHours(), spent, issue.getAgentSession(),
        moduleRefsOf(List.of(issue.getId())).getOrDefault(issue.getId(), List.of()),
        children.stream().map(c -> ref(c, ctx.project())).toList(), linkViews, journal, logViews);
  }

  /**
   * 지금 상태가 된 때 — 이력에서 상태가 지금 값으로 바뀐 마지막 줄(만들 때 줄도 null → 상태로 적힌다). 그런 줄이 없으면(옛 데이터) 만든 때.
   * 되돌아간 상태(진행 → 테스트 → 진행)는 마지막으로 그 상태가 된 때다.
   */
  private static Instant statusChangedAt(List<IssueViews.Journal> journal, Issue issue) {
    String now = issue.getStatus().name();
    for (int i = journal.size() - 1; i >= 0; i--) {
      IssueViews.Journal j = journal.get(i);
      if (j.changes() != null && j.changes().stream()
          .anyMatch(c -> "status".equals(c.get("field")) && now.equals(c.get("after")))) {
        return j.createdAt();
      }
    }
    return issue.getCreatedAt();
  }

  /** 지금 상태와 그 상태가 된 때 */
  public record StatusSince(Issue.Status status, Instant since) {
  }

  /**
   * 지금 상태와 그 상태가 된 때({@link IssueViews.Detail#statusChangedAt} 와 같은 규칙) — MCP update_issue 가 상태를 옮기기 전에 지난 구간의
   * 시작을 알아 둔다(옮긴 뒤 그 구간에 작업 시간이 적혔는지 알리려고 — 일감 #182). 상세 전체를 읽지 않는다.
   */
  @Transactional(readOnly = true)
  public StatusSince statusSince(ProjectContext ctx, int number) {
    Issue issue = requireIssue(ctx, number);
    String now = issue.getStatus().name();
    List<IssueJournal> lines = journals.findByIssueIdOrderByCreatedAtAscIdAsc(issue.getId());
    for (int i = lines.size() - 1; i >= 0; i--) {
      IssueJournal j = lines.get(i);
      if (j.getChanges() != null && j.getChanges().contains("\"status\"") && changesOf(j).stream()
          .anyMatch(c -> "status".equals(c.get("field")) && now.equals(c.get("after")))) {
        return new StatusSince(issue.getStatus(), j.getCreatedAt());
      }
    }
    return new StatusSince(issue.getStatus(), issue.getCreatedAt());
  }

  private List<IssueViews.WorkLog> workLogViews(List<IssueWorkLog> logs) {
    List<UUID> ids = new ArrayList<>(logs.stream().map(IssueWorkLog::getUserId).toList());
    logs.stream().map(IssueWorkLog::getDeletedBy).filter(Objects::nonNull).forEach(ids::add);
    Map<UUID, UserProfile> users = people(ids);
    return logs.stream().map(w -> new IssueViews.WorkLog(w.getId(), person(users, w.getUserId()), w.getWorkDate(),
        w.getHours(), w.getNote(), w.getVia(), w.getCreatedAt(), w.isDeleted(), w.getDeletedAt(),
        person(users, w.getDeletedBy()))).toList();
  }

  /**
   * 작업 시간 기록 — 지운 것까지(지움 표시 · 지운 때 · 지운 사람). MCP get_issue 가 고친 흔적을 보인다(2026-11-24, 일감 #176).
   * 합계는 {@link #detail} 의 spentHours(지운 것 빼고)다.
   */
  @Transactional(readOnly = true)
  public List<IssueViews.WorkLog> workLogHistory(ProjectContext ctx, int number) {
    return workLogViews(workLogs.findAllOf(requireIssue(ctx, number).getId()));
  }

  // ── 에이전트 세션 ──────────────────────────────────────────────────

  /** 이 프로젝트의 일감에 쓰인 에이전트 세션 이름들(지운 일감 빼고) — 이름순, 일감 수와 함께. 목록 거르기 고르기용 */
  @Transactional(readOnly = true)
  public List<IssueViews.AgentSession> agentSessions(ProjectContext ctx) {
    return agentSessionsOf(List.of(ctx.projectId()));
  }

  private List<IssueViews.AgentSession> agentSessionsOf(Collection<UUID> projectIds) {
    return issues.agentSessions(projectIds).stream()
        .map(row -> new IssueViews.AgentSession((String) row[0], ((Number) row[1]).longValue())).toList();
  }

  // ── 고치기 ────────────────────────────────────────────────────────

  /**
   * 고친다 — 요청에 든 필드만 바꾼다(null 이면 비운다). rowVersion 이 지금과 다르면 409 CONCURRENT_UPDATE.
   * notes 를 함께 넣으면 같은 이력 줄에 댓글로 남는다. 상태를 보류(ON_HOLD) · 취소(CANCELLED)로 옮길 때는 notes(까닭)가 있어야 한다
   * — 없으면 400 STATUS_REASON_REQUIRED(댓글이니 issue.comment 도 있어야 한다).
   *
   * <p>{@code project} 에 다른 프로젝트 주소 이름을 넣으면 <b>옮긴다</b>(Redmine 의 Project 칸 — ADR-030). 판정 · 규칙:
   * <ul>
   *   <li>이 프로젝트의 issue.edit(부르는 입구가 이미 봤다) + 옮길 프로젝트의 issue.create. 옮길 프로젝트를 읽을 수 없거나 없으면
   *       400 TARGET_PROJECT_NOT_FOUND(어느 쪽인지 숨긴다), 보관한 프로젝트면 409 PROJECT_NOT_WRITABLE, 권한이 없으면 403</li>
   *   <li>하위 일감은 통째로 함께 옮긴다(상위 · 하위는 같은 프로젝트). 상위는 남으니 상위 연결은 끊고 이력에 남긴다
   *       — 같은 요청에서 옮길 프로젝트의 일감을 parent 로 주면 그것이 새 상위다</li>
   *   <li>유형은 옮길 프로젝트에서 쓸 수 있어야 한다(지금은 쓰는 유형 = 쓸 수 있음). 아니면 400 ISSUE_TYPE_NOT_ALLOWED — 같은 요청에서
   *       type 을 바꾸면 된다(하위 일감의 유형은 먼저 고쳐야 한다)</li>
   *   <li>관리자 · 담당자는 옮길 프로젝트의 독자여야 한다(하위 일감 것도) — 아니면 400 USER_NOT_PROJECT_READER</li>
   *   <li>모듈은 프로젝트마다다 — 같은 key 가 옮길 프로젝트에 쓰는 모듈로 있으면 그것으로 잇고, 없으면 빼고 이력에 남긴다
   *       (같은 요청에 modules 를 주면 옮길 프로젝트의 모듈로 그대로)</li>
   *   <li>첨부 · 댓글 · 작업 시간 · 연결 · 이력은 일감을 따라간다(일감 id 에 묶여 있다). 첨부의 저장소 객체 이름은 그대로다</li>
   *   <li>옮긴 일감마다 이력 한 줄(field project)과 새 판, 감사 issue.move, 관리자 · 담당자에게 알림</li>
   * </ul>
   */
  public IssueViews.Detail update(ProjectContext ctx, int number, Map<String, Object> body, Actor actor,
      IssueJournal.Via via) {
    return update(ctx, number, body, null, actor, via).detail();
  }

  /** 고친 일감과 설명 고치기의 보고(설명 고치기를 주지 않았으면 null) */
  public record Updated(IssueViews.Detail detail, DescriptionEdit.Report descriptionEdit) {
  }

  /**
   * {@link #update(ProjectContext, int, Map, Actor, IssueJournal.Via)} 에 설명 고치기(절 채우기 · 체크 — MCP update_issue 의
   * setSection · checkItems · uncheckItems, 2026-11-24 일감 #178)를 더한다. rowVersion 을 본 뒤, {@code description} 을 줬으면 그것에,
   * 아니면 지금 설명에 고치기를 한다 — 바뀐 설명은 다른 필드와 같이 이력 · 판 · 감사(fields 에 description)에 남는다.
   * 체크 글이 맞지 않으면 400 CHECK_ITEM_NOT_FOUND 이고 아무것도 바꾸지 않는다.
   */
  public Updated update(ProjectContext ctx, int number, Map<String, Object> body, DescriptionEdit edit, Actor actor,
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
      throw ApiException.conflict("CONCURRENT_UPDATE", "다른 사람이 먼저 수정했습니다. 다시 읽고 고치십시오.");
    }
    // 옮길 프로젝트 — 같은 프로젝트를 주면 옮기지 않는다. 아래 값(상위 · 모듈 · 사람)은 옮길 프로젝트 기준으로 본다
    ProjectContext target = body.containsKey("project") ? moveTarget(ctx, text(body, "project")) : ctx;
    boolean moving = !target.projectId().equals(ctx.projectId());
    Values before = Values.of(current);
    Values v = Values.of(current);
    before.moduleIds = currentModuleIds(current.getId());
    v.moduleIds = before.moduleIds;
    v.projectId = target.projectId();
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
    DescriptionEdit.Report editReport = null;
    if (edit != null && !edit.moveTargets().isEmpty()) {
      requireMoveTargets(ctx, current, edit.moveTargets());
    }
    if (edit != null && !edit.isEmpty()) {
      DescriptionEdit.Result edited = edit.apply(v.description);
      v.description = edited.description();
      editReport = edited.report();
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
        v.parentId = requireParent(target, n.intValue()).getId();
      } else {
        throw ApiException.badRequest("INVALID_FIELD", "parent 는 일감 번호여야 합니다.");
      }
    } else if (moving) {
      // 상위는 옛 프로젝트에 남는다 — 상위 · 하위는 같은 프로젝트라 연결을 끊는다(이력에 parent → 없음으로 남는다)
      v.parentId = null;
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
    if (body.containsKey("agentSession")) {
      v.agentSession = agentSession(text(body, "agentSession"));
    }
    if (body.containsKey("modules")) {
      // 통째로 바꾼다 — 이미 붙어 있던 모듈은 쓰지 않게 됐어도 그대로 둘 수 있다(옮기면 옮길 프로젝트의 모듈 — 이미 붙은 것이 없다)
      v.moduleIds = moduleIds(moduleService.resolve(target.projectId(), moduleKeys(body),
          moving ? Set.of() : Set.copyOf(before.moduleIds)));
    } else if (moving) {
      v.moduleIds = mapModules(before.moduleIds, target.projectId());
    }
    String notes = body.containsKey("notes") ? text(body, "notes") : null;
    if (notes != null && notes.isBlank()) {
      notes = null;
    }
    // 보류 · 취소로 옮길 때는 까닭이 있어야 한다 — 같은 이력 줄의 댓글로 남는다(2026-11-24, 일감 #152). 다른 옮김은 그대로
    if (v.status != before.status && v.status != null && v.status.needsReason() && notes == null) {
      throw ApiException.badRequest("STATUS_REASON_REQUIRED", (v.status == Issue.Status.ON_HOLD ? "보류" : "취소")
          + "로 바꾸려면 까닭을 적으십시오. 이력에 댓글로 남습니다(MCP 는 comment, REST 는 notes).");
    }
    if (notes != null && !ctx.has(Permission.ISSUE_COMMENT)) {
      throw ApiException.forbidden("PERMISSION_REQUIRED", "댓글을 함께 남기려면 권한 issue.comment 이(가) 필요합니다.");
    }
    if (v.status != before.status && v.status != null) {   // 비운 상태는 아래 validate 가 400 STATUS_REQUIRED 로 막는다
      fillStatusDates(v, before.status, body.containsKey("completedOn"), body.containsKey("closedOn"));
    }
    if (!Objects.equals(v.managerId, before.managerId) && !ctx.has(Permission.ISSUE_MANAGER_ASSIGN)) {
      throw ApiException.forbidden("PERMISSION_REQUIRED", "관리자를 바꾸려면 권한 issue.manager.assign 이(가) 필요합니다.");
    }
    if (moving && !typeAllowedIn(target, v.type)) {
      throw ApiException.badRequest("ISSUE_TYPE_NOT_ALLOWED", "옮길 프로젝트 " + target.project().getKey()
          + " 에서 쓸 수 없는 일감 유형입니다: " + v.type + " — type 을 함께 바꾸십시오.");
    }
    // 옮기면 관리자 · 담당자가 옮길 프로젝트를 읽을 수 있어야 한다(바꾸지 않았어도)
    validate(target, v, current, moving || !Objects.equals(v.managerId, before.managerId),
        moving || !Objects.equals(v.assigneeId, before.assigneeId));
    if (!Objects.equals(v.parentId, before.parentId) && v.parentId != null) {
      checkParent(current.getId(), v.parentId);
    }
    if (notes != null && notes.length() > MAX_TEXT) {
      throw ApiException.badRequest("COMMENT_TOO_LONG", "댓글은 " + MAX_TEXT + "자를 넘을 수 없습니다.");
    }
    // 함께 옮길 하위 줄기(지운 하위 포함 — 상위 FK 가 같은 프로젝트를 요구한다)
    List<Issue> subtree = moving ? descendants(current.getId()) : List.of();
    if (moving) {
      checkSubtreeFits(target, subtree);
    }

    List<Map<String, Object>> changes = diff(before, v);
    if (changes.isEmpty() && notes == null) {
      return new Updated(detail(ctx, number), editReport);
    }
    String finalNotes = notes;
    ProjectContext finalTarget = target;
    tx.executeWithoutResult(s -> {
      Issue issue = issues.findById(current.getId()).orElseThrow();
      if (issue.getRowVersion() != rowVersion.intValue()) {
        throw ApiException.conflict("CONCURRENT_UPDATE", "다른 사람이 먼저 수정했습니다. 다시 읽고 고치십시오.");
      }
      Instant now = Instant.now(clock);
      List<Moved> moved = List.of();
      if (moving) {
        moved = moveRows(issue, subtree, finalTarget, now);
      }
      if (!changes.isEmpty()) {
        copy(v, issue);
        if (moving) {
          issues.flush();   // 일감의 프로젝트를 먼저 바꿔야 새 프로젝트 모듈을 붙일 수 있다(일감 · 모듈 같은 프로젝트 FK)
          syncModules(issue, List.of(), v.moduleIds);
        } else {
          syncModules(issue, before.moduleIds, v.moduleIds);
        }
        issue.touch(now);
        List<Object> fields = changes.stream().map(c -> c.get("field")).filter(f -> !"project".equals(f)).toList();
        if (!fields.isEmpty()) {
          audit.record(actor, ctx.workspaceId(), "issue.update", "issue", issue.getId().toString(),
              Map.of("project", finalTarget.project().getKey(), "number", number, "via", via.name(), "fields", fields));
        }
      }
      IssueJournal line = journal(issue.getId(), actor, via, finalNotes, changes);
      if (!changes.isEmpty()) {
        // 필드가 바뀌었으면 새 판 — 그 시점의 일감 전체를 남긴다(댓글만 있는 줄은 판이 아니다)
        revise(issue, line, v.moduleIds, actor, via);
      }
      if (moving) {
        finishMove(ctx, finalTarget, issue, before, moved, actor, via, now);
      }
      if (finalNotes != null) {
        audit.record(actor, ctx.workspaceId(), "issue.comment", "issue", issue.getId().toString(),
            Map.of("project", finalTarget.project().getKey(), "number", number, "via", via.name()));
      }
      if (!changes.isEmpty()) {
        notifyUpdated(finalTarget, issue, before.managerId, before.assigneeId, before.status, before.parentId, changes,
            actor, via);
      }
      if (finalNotes != null) {
        notifyCommented(finalTarget, issue, actor, via);
      }
    });
    return new Updated(detail(target, number), editReport);
  }

  /**
   * 체크 목록 줄을 넘겨받을 일감들 — 같은 워크스페이스의 지우지 않은 일감이고 이 사람이 읽을 수 있어야 한다(연결 대상과 같은 판정 —
   * 없거나 읽을 수 없으면 400 MOVE_TARGET_NOT_FOUND, 어느 쪽인지 숨긴다). 자기 자신은 400 INVALID_ARGUMENT(일감 #199)
   */
  private void requireMoveTargets(ProjectContext ctx, Issue current, List<Integer> targets) {
    for (Integer to : targets) {
      if (to == current.getNumber()) {
        throw ApiException.badRequest("INVALID_ARGUMENT", "moveItems 의 to 가 이 일감(#" + to
            + ")입니다 — 넘겨받을 다른 일감(create_issue 로 새로 만든 번호)을 넣으십시오.").with("field", "moveItems");
      }
      Issue other = issues.findLiveInWorkspace(ctx.workspaceId(), to).orElse(null);
      if (other == null || readable(ctx.workspace(), other.getProjectId()).isEmpty()) {
        throw ApiException.badRequest("MOVE_TARGET_NOT_FOUND", "넘겨받을 일감 #" + to + " 이(가) 이 워크스페이스에 없거나 읽을 수 "
            + "없습니다 — create_issue 로 먼저 만들고 그 번호를 to 에 넣으십시오. 아무것도 바꾸지 않았습니다.")
            .with("field", "moveItems").with("to", to);
      }
    }
  }

  // ── 옮기기 (ADR-030) ───────────────────────────────────────────────

  /**
   * 옮길 프로젝트의 문맥 — 같은 워크스페이스에서 이 사람이 읽을 수 있는 프로젝트. 없거나 읽을 수 없으면 400 TARGET_PROJECT_NOT_FOUND
   * (어느 쪽인지 숨긴다), 보관한 프로젝트면 409 PROJECT_NOT_WRITABLE, issue.create 가 없으면 403. 같은 프로젝트면 그 문맥 그대로
   */
  private ProjectContext moveTarget(ProjectContext ctx, String key) {
    String k = key == null ? "" : key.trim();
    if (k.isEmpty()) {
      throw ApiException.badRequest("INVALID_FIELD", "project 는 옮길 프로젝트의 주소 이름입니다.");
    }
    if (k.equals(ctx.project().getKey())) {
      return ctx;
    }
    ProjectContext target = projectAccess.contextFor(ctx.workspace(), k).filter(c -> c.has(Permission.ISSUE_READ))
        .orElseThrow(() -> ApiException.badRequest("TARGET_PROJECT_NOT_FOUND", "옮길 프로젝트를 찾을 수 없습니다: " + k));
    requireOpen(target);
    if (!target.has(Permission.ISSUE_CREATE)) {
      throw ApiException.forbidden("PERMISSION_REQUIRED", "옮길 프로젝트 " + k + " 에 일감을 만들 권한 issue.create 이(가) 필요합니다.");
    }
    return target;
  }

  /** 옮길 프로젝트에서 쓸 수 있는 유형인가 — 유형은 아직 워크스페이스 공통이라 쓰는 유형(active)이면 된다. 프로젝트별 유형이 생기면 여기서 좁힌다 */
  private boolean typeAllowedIn(ProjectContext target, String typeKey) {
    return typeKey != null && types.findById(typeKey).map(IssueType::isActive).orElse(false);
  }

  /** 하위 줄기 전부(지운 것 포함) — 위에서 아래로 */
  private List<Issue> descendants(UUID rootId) {
    List<Issue> out = new ArrayList<>();
    Deque<UUID> queue = new ArrayDeque<>(List.of(rootId));
    Set<UUID> seen = new HashSet<>(List.of(rootId));
    while (!queue.isEmpty()) {
      for (Issue child : issues.findChildrenAnyState(queue.poll())) {
        if (seen.add(child.getId())) {
          out.add(child);
          queue.add(child.getId());
        }
      }
    }
    return out;
  }

  /** 함께 옮길 살아 있는 하위 일감도 옮길 프로젝트에 맞아야 한다 — 유형 · 관리자 · 담당자(독자) */
  private void checkSubtreeFits(ProjectContext target, List<Issue> subtree) {
    List<String> badTypes = new ArrayList<>();
    List<String> badPeople = new ArrayList<>();
    Map<UUID, Boolean> readerCache = new HashMap<>();
    for (Issue d : subtree) {
      if (d.isDeleted()) {
        continue;
      }
      if (!typeAllowedIn(target, d.getIssueType())) {
        badTypes.add("#" + d.getNumber() + "(" + d.getIssueType() + ")");
      }
      for (UUID person : java.util.Arrays.asList(d.getManagerId(), d.getAssigneeId())) {
        if (person != null && !readerCache.computeIfAbsent(person, id -> isReader(target, id))) {
          badPeople.add("#" + d.getNumber());
          break;
        }
      }
    }
    if (!badTypes.isEmpty()) {
      throw ApiException.badRequest("ISSUE_TYPE_NOT_ALLOWED", "함께 옮길 하위 일감의 유형을 옮길 프로젝트 "
          + target.project().getKey() + " 에서 쓸 수 없습니다: " + String.join(", ", badTypes) + " — 먼저 그 일감의 유형을 바꾸십시오.");
    }
    if (!badPeople.isEmpty()) {
      throw ApiException.badRequest("USER_NOT_PROJECT_READER", "함께 옮길 하위 일감의 관리자 · 담당자가 옮길 프로젝트 "
          + target.project().getKey() + " 를 볼 수 없습니다: " + String.join(", ", badPeople) + " — 먼저 그 일감의 사람을 바꾸십시오.");
    }
  }

  /** 하위 일감 하나를 옮긴 것 — 옮기기 전 모듈 · 옮긴 뒤 모듈(같은 key 로 이은 것) */
  private record Moved(Issue issue, List<UUID> modulesBefore, List<UUID> modulesAfter) {
  }

  /**
   * 행을 옮긴다 — 상위 FK(같은 프로젝트)는 커밋할 때 보게 미루고, 옮길 일감들의 모듈 연결을 먼저 빼고, 프로젝트를 바꾼다.
   * 뿌리 일감의 값 · 모듈은 부르는 쪽이 바꾼다. 살아 있는 하위 일감은 같은 key 모듈로 다시 잇는다. 첨부의 프로젝트도 따라간다
   */
  private List<Moved> moveRows(Issue root, List<Issue> subtree, ProjectContext target, Instant now) {
    jdbc.getJdbcTemplate().execute("SET CONSTRAINTS fk_issue_parent_same_project DEFERRED");
    List<UUID> ids = new ArrayList<>(List.of(root.getId()));
    subtree.forEach(d -> ids.add(d.getId()));
    Map<UUID, List<UUID>> modulesBefore = new HashMap<>();
    for (Issue d : subtree) {
      if (!d.isDeleted()) {
        modulesBefore.put(d.getId(), currentModuleIds(d.getId()));
      }
    }
    issueModules.deleteAllOf(ids);
    root.setProjectId(target.projectId());
    List<Issue> managed = new ArrayList<>();
    for (Issue d : subtree) {
      Issue m = issues.findById(d.getId()).orElseThrow();
      m.setProjectId(target.projectId());
      if (!m.isDeleted()) {
        m.touch(now);
      }
      managed.add(m);
    }
    issues.flush();
    List<Moved> moved = new ArrayList<>();
    for (Issue m : managed) {
      if (m.isDeleted()) {
        continue;
      }
      List<UUID> before = modulesBefore.getOrDefault(m.getId(), List.of());
      List<UUID> after = mapModules(before, target.projectId());
      after.forEach(id -> issueModules.save(new IssueModule(target.projectId(), m.getId(), id)));
      moved.add(new Moved(m, before, after));
    }
    issueModules.flush();
    // 첨부의 프로젝트도 일감을 따라간다(V23 트리거가 그 일감의 지금 프로젝트로만 허용). 저장소의 객체 이름(storage_key)은 그대로라 받기는 그대로다
    jdbc.update("UPDATE issue_attachment SET project_id = :p WHERE issue_id IN (:ids) AND project_id <> :p",
        new MapSqlParameterSource("p", target.projectId()).addValue("ids", ids));
    return moved;
  }

  /** 옮긴 뒤 — 하위 일감마다 이력 · 판 · 알림, 감사 issue.move 하나 */
  private void finishMove(ProjectContext source, ProjectContext target, Issue root, Values rootBefore, List<Moved> moved,
      Actor actor, IssueJournal.Via via, Instant now) {
    Map<String, Object> dropped = new LinkedHashMap<>();
    List<String> rootDropped = droppedKeys(rootBefore.moduleIds, currentModuleIds(root.getId()));
    if (!rootDropped.isEmpty()) {
      dropped.put(String.valueOf(root.getNumber()), rootDropped);
    }
    for (Moved m : moved) {
      List<Map<String, Object>> changes = new ArrayList<>();
      changes.add(change("project", projectValue(source.projectId()), projectValue(target.projectId())));
      if (!Set.copyOf(m.modulesBefore()).equals(Set.copyOf(m.modulesAfter()))) {
        changes.add(change("modules", moduleValue(m.modulesBefore()), moduleValue(m.modulesAfter())));
        List<String> lost = droppedKeys(m.modulesBefore(), m.modulesAfter());
        if (!lost.isEmpty()) {
          dropped.put(String.valueOf(m.issue().getNumber()), lost);
        }
      }
      IssueJournal line = journal(m.issue().getId(), actor, via, null, changes);
      revise(m.issue(), line, m.modulesAfter(), actor, via);
      notifyUpdated(target, m.issue(), m.issue().getManagerId(), m.issue().getAssigneeId(), m.issue().getStatus(),
          m.issue().getParentId(), changes, actor, via);
    }
    Map<String, Object> details = new LinkedHashMap<>();
    details.put("from", source.project().getKey());
    details.put("project", target.project().getKey());
    details.put("number", root.getNumber());
    details.put("moved", java.util.stream.Stream.concat(java.util.stream.Stream.of(root.getNumber()),
        moved.stream().map(m -> m.issue().getNumber())).toList());
    if (rootBefore.parentId != null && root.getParentId() == null) {
      details.put("parentCleared", numberOf(rootBefore.parentId));
    }
    if (!dropped.isEmpty()) {
      details.put("droppedModules", dropped);
    }
    details.put("via", via.name());
    audit.record(actor, source.workspaceId(), "issue.move", "issue", root.getId().toString(), details);
  }

  /** 옛 프로젝트 모듈 → 옮길 프로젝트의 같은 key 모듈(쓰는 것만), 모듈 순서. 없는 key 는 빠진다 */
  private List<UUID> mapModules(List<UUID> moduleIds, UUID targetProjectId) {
    if (moduleIds.isEmpty()) {
      return List.of();
    }
    List<String> keys = moduleService.byIds(moduleIds).values().stream().map(ProjectModule::getKey).toList();
    return moduleService.byProject(targetProjectId).stream().filter(m -> m.isActive() && keys.contains(m.getKey()))
        .sorted(ProjectModuleService.ORDER).map(ProjectModule::getId).toList();
  }

  /** 옮기며 빠진 모듈 key */
  private List<String> droppedKeys(List<UUID> before, List<UUID> after) {
    Set<String> kept = moduleService.byIds(after).values().stream().map(ProjectModule::getKey).collect(Collectors.toSet());
    return moduleService.byIds(before).values().stream().map(ProjectModule::getKey).filter(k -> !kept.contains(k)).sorted()
        .toList();
  }

  /** 이력 · 판의 프로젝트 값 — {key, name}(그때의 이름) */
  private Map<String, Object> projectValue(UUID projectId) {
    if (projectId == null) {
      return null;
    }
    Map<String, Object> m = new LinkedHashMap<>();
    projects.findById(projectId).ifPresentOrElse(p -> {
      m.put("key", p.getKey());
      m.put("name", p.getName());
    }, () -> m.put("key", projectId.toString()));
    return m;
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
        r.getCreatedAt(), projectRef(projects.findById(r.getProjectId()).orElse(null)), r.getIssueType(), r.getTitle(), r.getDescription(), r.getStatus(),
        person(people, r.getManagerId()), person(people, r.getAssigneeId()), r.getParentNumber(), r.getDueDate(),
        r.getStartDate(), r.getPlannedEndDate(), r.getCompletedOn(), r.getClosedOn(), r.getEstimatedHours(),
        r.getAgentSession(), revisionModules(r), r.getRevisionNo() == issue.getRevisionNo());
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
    fieldChange(fields, "project", base == null ? null : projectValue(base.getProjectId()), projectValue(to.getProjectId()));
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
    fieldChange(fields, "agentSession", base == null ? null : base.getAgentSession(), to.getAgentSession());
    fieldChange(fields, "modules", base == null ? null : moduleList(base), moduleList(to));
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
    return logWorkEntry(ctx, number, workDate, hours, note, actor, via).detail();
  }

  /** 적은 기록의 id 와 일감 — MCP log_work 가 id 를 돌려준다(고칠 때 delete_work_log 에 넣는다) */
  public record LoggedWork(UUID workLogId, IssueViews.Detail detail) {
  }

  /** {@link #logWork} 와 같다 — 적은 기록의 id 도 돌려준다 */
  public LoggedWork logWorkEntry(ProjectContext ctx, int number, LocalDate workDate, BigDecimal hours, String note,
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
    UUID logId = tx.execute(s -> {
      BigDecimal spentBefore = workLogs.spent(issue.getId());
      IssueWorkLog log = workLogs.save(new IssueWorkLog(issue.getId(), worker, workDate, h, text, via,
          Instant.now(clock)));
      journal(issue.getId(), actor, via, null, List.of(change("workLog", null, workLogValue(log))));
      audit.record(actor, ctx.workspaceId(), "issue.work_log.add", "issue", issue.getId().toString(),
          Map.of("project", ctx.project().getKey(), "number", number, "workLog", log.getId().toString(),
              "date", workDate.toString(), "hours", plain(h), "via", via.name()));
      notifyWorkLogged(ctx, issue, workDate, h, spentBefore, actor, via);
      return log.getId();
    });
    return new LoggedWork(logId, detail(ctx, number));
  }

  /** 작업 시간 기록을 지운다 — 자기 기록은 그대로, 남의 기록은 project.manage 가 있어야. 지움 표시만 하고 누가 지웠는지 남긴다 */
  public IssueViews.Detail removeWork(ProjectContext ctx, int number, UUID logId, Actor actor, IssueJournal.Via via) {
    return removeWork(ctx, number, logId, null, false, actor, via);
  }

  /**
   * 작업 시간 기록을 지운다(이유 · 범위와 함께). 기록은 쌓기만 하고 DB 트리거가 지움 표시 말고는 막는다 — 고치려면 지우고 다시 적는다.
   *
   * @param reason  이유 — 이력의 그 줄({@code reason})과 감사에 남는다. MCP 는 필수(도구가 본다), 웹은 없어도 된다
   * @param ownOnly 부른 사람이 적은 기록만 — MCP {@code delete_work_log}(2026-11-24, 일감 #176). 남의 기록은 project.manage 가 있어도
   *                403 {@code PERMISSION_REQUIRED}(웹에서 한다)
   */
  public IssueViews.Detail removeWork(ProjectContext ctx, int number, UUID logId, String reason, boolean ownOnly,
      Actor actor, IssueJournal.Via via) {
    requireOpen(ctx);
    UUID by = requirePerson(actor);
    Issue issue = requireIssue(ctx, number);
    IssueWorkLog log = workLogs.findById(logId)
        .filter(w -> w.getIssueId().equals(issue.getId()) && !w.isDeleted())
        .orElseThrow(() -> ApiException.notFound("WORK_LOG_NOT_FOUND", "작업 시간 기록을 찾을 수 없습니다."));
    if (!log.getUserId().equals(by) && ownOnly) {
      throw ApiException.forbidden("PERMISSION_REQUIRED", "MCP 로는 내가 적은 작업 시간만 지울 수 있습니다 — 남의 기록은 웹에서 지웁니다(project.manage).");
    }
    if (!log.getUserId().equals(by) && !ctx.has(Permission.PROJECT_MANAGE)) {
      throw ApiException.forbidden("PERMISSION_REQUIRED", "남의 작업 시간을 지우려면 권한 project.manage 이(가) 필요합니다.");
    }
    String why = reason == null || reason.isBlank() ? null : reason.trim();
    tx.executeWithoutResult(s -> {
      IssueWorkLog locked = workLogs.findById(log.getId()).orElseThrow();
      locked.markDeleted(by, Instant.now(clock));
      Map<String, Object> removed = change("workLog", workLogValue(locked), null);
      Map<String, Object> details = new LinkedHashMap<>();
      details.put("project", ctx.project().getKey());
      details.put("number", number);
      details.put("workLog", locked.getId().toString());
      details.put("worker", locked.getUserId().toString());
      details.put("via", via.name());
      if (why != null) {
        removed.put("reason", why);
        details.put("reason", why);
      }
      journal(issue.getId(), actor, via, null, List.of(removed));
      audit.record(actor, ctx.workspaceId(), "issue.work_log.remove", "issue", issue.getId().toString(), details);
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
    // 대상은 같은 워크스페이스의 일감 — 다른 프로젝트여도 된다(Redmine 의 관계처럼). 지금 읽을 수 없으면 없는 것과 같은 400
    Issue other = issues.findLiveInWorkspace(ctx.workspaceId(), target).orElse(null);
    ProjectContext otherCtx = other == null ? null : readable(ctx.workspace(), other.getProjectId()).orElse(null);
    if (otherCtx == null) {
      throw ApiException.badRequest("LINK_TARGET_NOT_FOUND", "연결할 일감이 없습니다: #" + target);
    }
    if (!otherCtx.project().isOpenToReaders()) {
      throw ApiException.conflict("PROJECT_NOT_WRITABLE", "보관한 프로젝트의 일감과는 연결할 수 없습니다: #" + target);
    }
    if (links.existsPair(source.getId(), other.getId())) {
      throw ApiException.conflict("ISSUE_LINK_EXISTS", "이미 연결된 일감입니다: #" + target);
    }
    tx.executeWithoutResult(s -> {
      links.saveAndFlush(new IssueLink(source.getId(), other.getId(), type, requirePerson(actor), Instant.now(clock)));
      journal(source.getId(), actor, via, null, List.of(change("link", null, linkValue(type, "OUTGOING", other))));
      journal(other.getId(), actor, via, null, List.of(change("link", null, linkValue(type, "INCOMING", source))));
      Map<String, Object> details = new LinkedHashMap<>();
      details.put("project", ctx.project().getKey());
      details.put("number", number);
      details.put("target", target);
      if (!otherCtx.projectId().equals(ctx.projectId())) {
        details.put("targetProject", otherCtx.project().getKey());
      }
      details.put("type", type.name());
      details.put("via", via.name());
      audit.record(actor, ctx.workspaceId(), "issue.link.add", "issue", source.getId().toString(), details);
      notifyLinked(ctx.workspaceId(), ctx.project(), source, other, otherCtx.project(), type, "OUTGOING", actor, via);
      notifyLinked(ctx.workspaceId(), otherCtx.project(), other, source, ctx.project(), type, "INCOMING", actor, via);
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
  /** 알리지 않는 필드 — 누가(어느 AI 세션이) 맡았는지 적는 꼬리표라 관리자 · 담당자에게 "내용이 바뀜"으로 알릴 일이 아니다 */
  private static final Set<String> QUIET_FIELDS = Set.of("agentSession");

  private Map<String, Object> about(ProjectContext ctx, Issue issue, IssueJournal.Via via) {
    return about(ctx.project(), issue, via);
  }

  private static Map<String, Object> about(Project project, Issue issue, IssueJournal.Via via) {
    return NotificationService.aboutIssue(project.getKey(), project.getName(), issue.getNumber(), issue.getTitle(),
        via.name());
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
    List<Object> fields = changes.stream().map(c -> c.get("field"))
        .filter(f -> !STATE_FIELDS.contains(f) && !QUIET_FIELDS.contains(f)).toList();
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

  /**
   * 연결 — 그 일감의 관리자 · 담당자에게, 상대 일감과 함께. 상대가 다른 프로젝트면 번호만 싣는다 — 받는 사람이 그 프로젝트를 읽을 수
   * 없을 수 있어 제목을 알림으로 흘리지 않는다(눌러 들어가면 그 화면이 지금 권한으로 다시 판정한다)
   */
  private void notifyLinked(UUID workspaceId, Project project, Issue issue, Issue other, Project otherProject,
      IssueLink.Type type, String direction, Actor actor, IssueJournal.Via via) {
    Map<String, Object> params = about(project, issue, via);
    params.put("linkType", type.name());
    params.put("direction", direction);
    params.put("otherNumber", other.getNumber());
    if (otherProject.getId().equals(project.getId())) {
      params.put("otherTitle", other.getTitle());
    }
    notifications.notifyAll(watchers(issue), workspaceId, NotificationKind.ISSUE_LINKED, issue.getId(), params, actor);
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
    /** 프로젝트 — 옮기면 바뀐다 */
    UUID projectId;
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
    String agentSession;
    /** 관련 모듈 id — 모듈 순서. Values.of 는 채우지 않는다(일감 행 밖의 표라 부르는 쪽이 넣는다) */
    List<UUID> moduleIds = List.of();

    static Values of(Issue i) {
      Values v = new Values();
      v.projectId = i.getProjectId();
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
      v.agentSession = i.getAgentSession();
      return v;
    }
  }

  /**
   * 상태가 바뀌면 날짜를 채우거나 비운다 (design/05). 요청이 그 날짜를 직접 넣었으면 건드리지 않는다.
   * <ul>
   *   <li>완료(DONE) — 완료일이 비어 있으면 오늘 (종료일은 그대로 — 단 취소에서 오면 취소한 날이라 비운다)</li>
   *   <li>종료(CLOSED) — 종료일이 비어 있으면 오늘 (완료일은 그대로)</li>
   *   <li>취소(CANCELLED) — 종료일이 비어 있으면 오늘, 완료일은 비운다(취소는 끝낸 것이 아니다)</li>
   *   <li>등록 · 진행 · 테스트 · 보류(열린 상태)로 되돌리면 — 완료일 · 종료일을 비운다</li>
   * </ul>
   */
  private void fillStatusDates(Values v, Issue.Status from, boolean completedGiven, boolean closedGiven) {
    LocalDate today = today();
    switch (v.status) {
      case DONE -> {
        if (!completedGiven && v.completedOn == null) {
          v.completedOn = today;
        }
        if (from == Issue.Status.CANCELLED && !closedGiven) {
          // 취소했던 일을 결국 끝냈다 — 취소한 날(종료일)은 더 뜻이 없고, 두면 완료일 > 종료일로 막힌다
          v.closedOn = null;
        }
      }
      case CLOSED -> {
        if (!closedGiven && v.closedOn == null) {
          v.closedOn = today;
        }
      }
      case CANCELLED -> {
        if (!closedGiven && v.closedOn == null) {
          v.closedOn = today;
        }
        if (!completedGiven) {
          v.completedOn = null;
        }
      }
      case NEW, IN_PROGRESS, TESTING, ON_HOLD -> {
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

  /** 기한 지남 — 열린 일감(등록 · 진행 · 테스트 · 보류)이고 완료 기한이 오늘보다 앞이다 */
  private static boolean overdue(Issue i, LocalDate today) {
    return i.getStatus().isOpen() && i.getDueDate() != null && i.getDueDate().isBefore(today);
  }

  private static boolean before(LocalDate a, LocalDate b) {
    return a != null && b != null && a.isBefore(b);
  }

  /** 관리자 · 담당자는 그 프로젝트의 독자인 활성 멤버여야 한다 — 판정은 REST 와 같은 서비스로 한다 */
  private void requireReader(ProjectContext ctx, UUID userId, String code, String what) {
    if (!isReader(ctx, userId)) {
      throw ApiException.badRequest(code, what + "는 이 프로젝트를 볼 수 있는 워크스페이스 멤버여야 합니다.");
    }
  }

  /** 그 사람이 그 프로젝트의 일감을 읽을 수 있는 활성 멤버인가 */
  private boolean isReader(ProjectContext ctx, UUID userId) {
    return workspaceAccess.contextFor(ctx.workspace().workspace(), userId)
        .flatMap(ws -> projectAccess.contextFor(ws, ctx.project()))
        .filter(p -> p.has(Permission.ISSUE_READ)).isPresent();
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
    issue.setAgentSession(v.agentSession);
  }

  /** 바뀐 필드 — 이력에 남긴다. 사람은 {id, name}, 상위 일감은 번호, 설명은 전 · 후 글 전체 */
  private List<Map<String, Object>> diff(Values a, Values b) {
    List<Map<String, Object>> changes = new ArrayList<>();
    if (a.projectId != null && b.projectId != null && !a.projectId.equals(b.projectId)) {
      changes.add(change("project", projectValue(a.projectId), projectValue(b.projectId)));
    }
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
    addIfChanged(changes, "agentSession", a.agentSession, b.agentSession);
    if (!Set.copyOf(a.moduleIds).equals(Set.copyOf(b.moduleIds))) {
      changes.add(change("modules", moduleValue(a.moduleIds), moduleValue(b.moduleIds)));
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
  private void revise(Issue issue, IssueJournal line, List<UUID> moduleIds, Actor actor, IssueJournal.Via via) {
    int no = issue.nextRevision();
    String actorType = actor.type() == Actor.Type.USER ? "USER" : "SERVICE";
    List<Map<String, Object>> moduleValue = moduleValue(moduleIds);
    revisions.save(IssueRevision.of(issue, no, line.getId(), numberOf(issue.getParentId()),
        moduleValue == null ? null : json.writeValueAsString(moduleValue), actor.type() == Actor.Type.USER ? actor.id() : null,
        actorType,
        actor.name() == null ? actorType : actor.name(), via, Instant.now(clock)));
    line.setRevisionNo(no);
  }

  // ── 관련 모듈 (2026-11-23, 일감 #27) ─────────────────────────────────

  private static List<UUID> moduleIds(List<ProjectModule> modules) {
    return modules.stream().map(ProjectModule::getId).toList();
  }

  /** 지금 붙은 모듈 id — 모듈 순서 */
  private List<UUID> currentModuleIds(UUID issueId) {
    List<UUID> ids = issueModules.findByIssue(issueId).stream().map(IssueModule::getModuleId).toList();
    return moduleService.byIds(ids).values().stream().sorted(ProjectModuleService.ORDER).map(ProjectModule::getId).toList();
  }

  /** 붙은 모듈을 바꾼다 — 빠진 것은 지우고 새 것은 넣는다 */
  private void syncModules(Issue issue, List<UUID> before, List<UUID> after) {
    Set<UUID> removed = new HashSet<>(before);
    removed.removeAll(after);
    if (!removed.isEmpty()) {
      issueModules.deleteLinks(issue.getId(), removed);
    }
    for (UUID id : after) {
      if (!before.contains(id)) {
        issueModules.save(new IssueModule(issue.getProjectId(), issue.getId(), id));
      }
    }
  }

  /** 이력 · 판의 모듈 값 — [{"key","name"}] 모듈 순서, 없으면 null. 이름은 그때의 이름 */
  private List<Map<String, Object>> moduleValue(List<UUID> ids) {
    if (ids == null || ids.isEmpty()) {
      return null;
    }
    return moduleService.byIds(ids).values().stream().sorted(ProjectModuleService.ORDER).map(m -> {
      Map<String, Object> v = new LinkedHashMap<>();
      v.put("key", m.getKey());
      v.put("name", m.getName());
      return v;
    }).toList();
  }

  /** 일감마다 붙은 모듈 — 모듈 순서 */
  private Map<UUID, List<IssueViews.ModuleRef>> moduleRefsOf(List<UUID> issueIds) {
    if (issueIds.isEmpty()) {
      return Map.of();
    }
    List<IssueModule> links = issueModules.findByIssues(issueIds);
    Map<UUID, ProjectModule> byId = moduleService.byIds(links.stream().map(IssueModule::getModuleId).collect(Collectors.toSet()));
    Map<UUID, List<IssueViews.ModuleRef>> out = new HashMap<>();
    links.stream().collect(Collectors.groupingBy(IssueModule::getIssueId)).forEach((issueId, rows) -> out.put(issueId,
        rows.stream().map(r -> byId.get(r.getModuleId())).filter(Objects::nonNull).sorted(ProjectModuleService.ORDER)
            .map(m -> new IssueViews.ModuleRef(m.getKey(), m.getName(), m.isActive())).toList()));
    return out;
  }

  /** 판의 모듈 — [{"key","name"}] 그대로(그때의 이름), 없으면 빈 목록 */
  private List<Map<String, Object>> revisionModules(IssueRevision r) {
    List<Map<String, Object>> list = moduleList(r);
    return list == null ? List.of() : list;
  }

  private List<Map<String, Object>> moduleList(IssueRevision r) {
    return r.getModules() == null ? null : json.readValue(r.getModules(), new TypeReference<List<Map<String, Object>>>() {
    });
  }

  /** 요청의 modules — 모듈 key 글자 목록. null 이면 비운다 */
  private static List<String> moduleKeys(Map<String, Object> body) {
    Object v = body.get("modules");
    if (v == null) {
      return List.of();
    }
    if (!(v instanceof Collection<?> c) || c.stream().anyMatch(x -> !(x instanceof String))) {
      throw ApiException.badRequest("INVALID_FIELD", "modules 는 모듈 key(글자)의 목록이어야 합니다.");
    }
    return c.stream().map(String.class::cast).toList();
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

  /** 상위로 둘 일감 — 같은 프로젝트의 일감만(상위 · 하위는 같은 프로젝트). 요청 본문의 번호라서 없으면 400 */
  private Issue requireParent(ProjectContext ctx, int number) {
    return issues.findLiveInWorkspace(ctx.workspaceId(), number).filter(i -> i.getProjectId().equals(ctx.projectId()))
        .orElseThrow(() -> ApiException.badRequest("PARENT_NOT_FOUND", "이 프로젝트에 상위로 둘 일감이 없습니다: #" + number));
  }

  /** 이 프로젝트의 그 번호 일감 — 번호는 워크스페이스에서 하나라 다른 프로젝트의 일감이면 없는 것과 같다(404) */
  Issue requireIssue(ProjectContext ctx, int number) {
    return issues.findLiveInWorkspace(ctx.workspaceId(), number).filter(i -> i.getProjectId().equals(ctx.projectId()))
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

  private static IssueViews.Ref ref(Issue i, Project project) {
    return new IssueViews.Ref(i.getNumber(), projectRef(project), i.getTitle(), i.getStatus(), i.getIssueType());
  }

  static IssueViews.ProjectRef projectRef(Project project) {
    return project == null ? null : new IssueViews.ProjectRef(project.getKey(), project.getName());
  }

  /** 이 사람이 그 프로젝트의 일감을 읽을 수 있으면 그 문맥(독자 ∧ issue.read). 판정은 ProjectAccessService 그대로 */
  private java.util.Optional<ProjectContext> readable(WorkspaceContext ws, UUID projectId) {
    return projects.findById(projectId).flatMap(p -> projectAccess.contextFor(ws, p))
        .filter(c -> c.has(Permission.ISSUE_READ));
  }

  // ── 요청 값 읽기 ──────────────────────────────────────────────────

  /**
   * 에이전트 세션 이름을 다듬는다 — 앞뒤 공백을 빼고 비면 없음(null). 100자를 넘거나 줄바꿈 같은 제어 문자가 있으면
   * 400 INVALID_AGENT_SESSION (REST · MCP 가 같이 쓴다).
   */
  static String agentSession(String value) {
    if (value == null) {
      return null;
    }
    String v = value.trim();
    if (v.isEmpty()) {
      return null;
    }
    if (v.length() > MAX_AGENT_SESSION) {
      throw ApiException.badRequest("INVALID_AGENT_SESSION", "에이전트 세션 이름은 " + MAX_AGENT_SESSION + "자를 넘을 수 없습니다.");
    }
    if (v.chars().anyMatch(Character::isISOControl)) {
      throw ApiException.badRequest("INVALID_AGENT_SESSION", "에이전트 세션 이름에 줄바꿈 같은 제어 문자를 넣을 수 없습니다.");
    }
    return v;
  }

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
      throw ApiException.badRequest("INVALID_ISSUE_STATUS", "상태는 " + Issue.Status.CODES + " 가운데 하나입니다.");
    }
  }
}
