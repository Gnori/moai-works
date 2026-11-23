package com.moai.works.api.issue;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.moai.works.api.account.UserProfile;
import com.moai.works.api.account.UserProfileRepository;
import com.moai.works.api.audit.AuditService;
import com.moai.works.api.common.Actor;
import com.moai.works.api.common.ApiException;
import com.moai.works.api.project.Project;
import com.moai.works.api.project.ProjectAccessService;
import com.moai.works.api.project.ProjectRepository;
import com.moai.works.api.security.access.ProjectContext;
import com.moai.works.api.security.access.WorkspaceContext;
import com.moai.works.api.workspace.Permission;

/**
 * 공수 집계 — 예상 공수 대비 실제 투입 공수 (FR-ISS-017, design/05 "공수 집계").
 * 실제 투입 공수는 작업 시간 기록에서만 뽑는다(2026-11-22 사용자: "나중에 공수 정리 및 실제 투입 공수는 소요시간으로 뽑아야 해").
 *
 * <ul>
 *   <li><b>범위</b> — 부른 사람이 읽을 수 있는 프로젝트(판정은 {@link ProjectAccessService} 그대로, 그 프로젝트에서
 *       {@code issue.read})의 기록만 센다. 읽을 수 없는 프로젝트를 조건으로 주면 404 {@code PROJECT_NOT_FOUND}(있는지 숨김).</li>
 *   <li><b>기록</b> — 지우지 않은 일감의 지우지 않은 기록. 지운 기록 수는 따로 알린다(빼고 셌다는 표시).</li>
 *   <li><b>날</b> — 기록의 일한 날({@code work_date}). 적을 때 Asia/Seoul 의 오늘까지로 받은 달력 날짜라 시간대를 다시 바꾸지 않는다.
 *       주는 월요일부터 일요일, 달은 1일부터 말일.</li>
 *   <li><b>예상</b> — 결과에 든 일감(조건에 맞는 기록이 하나라도 있는 일감)의 지금 예상 공수를 일감마다 한 번씩 더한다.
 *       예상은 일감에 하나라서 일감 · 프로젝트 · 유형으로 모을 때만 줄마다 보이고, 사람 · 기간으로 모으면 합계에만 있다.</li>
 * </ul>
 */
@Service
public class EffortService {

  /** 모으는 기준 */
  public enum GroupBy {
    PERSON, PROJECT, ISSUE, TYPE, DAY, WEEK, MONTH;

    /** 줄마다 예상 공수가 뜻이 있는가 — 일감이 한 줄에만 속하는 기준 */
    public boolean carriesEstimate() {
      return this == PROJECT || this == ISSUE || this == TYPE;
    }

    public boolean isPeriod() {
      return this == DAY || this == WEEK || this == MONTH;
    }

    public String code() {
      return name().toLowerCase(Locale.ROOT);
    }

    static Optional<GroupBy> of(String value) {
      for (GroupBy g : values()) {
        if (g.code().equals(value)) {
          return Optional.of(g);
        }
      }
      return Optional.empty();
    }
  }

  /**
   * 조건 — 비운 것은 거르지 않는다. 한 조건의 여러 값은 하나라도(OR), 서로 다른 조건은 모두(AND).
   *
   * @param from     이날부터(포함)
   * @param to       이날까지(포함)
   * @param projects 프로젝트 주소 이름
   * @param people   일한 사람(사용자 id)
   * @param types    일감 유형 key
   */
  public record Query(LocalDate from, LocalDate to, List<String> projects, List<UUID> people, List<String> types,
      GroupBy groupBy) {
  }

  public record ProjectRef(String key, String name) {
  }

  public record IssueRef(String project, int number, String title, String type) {
  }

  public record TypeRef(String key, String name) {
  }

  /**
   * 한 줄 — 모은 기준에 맞는 칸(project · issue · person · type · period)만 차 있다.
   *
   * @param key            기준의 값(사람 id · 프로젝트 주소 이름 · {@code 주소#번호} · 유형 key · 기간의 첫날, 달은 yyyy-MM)
   * @param label          사람이 읽는 이름(이름 · 프로젝트 이름 · 일감 제목 · 유형 이름 · 기간)
   * @param estimatedHours 예상 공수 — 기준이 예상을 가르지 못하거나(사람 · 기간) 예상이 있는 일감이 없으면 null
   * @param diffHours      실제 − 예상 (예상이 없으면 null)
   * @param ratio          실제 ÷ 예상 × 100, 반올림한 백분율 (예상이 없거나 0 이면 null)
   */
  public record Row(String key, String label, ProjectRef project, IssueRef issue, IssueViews.Person person, String type,
      LocalDate period, LocalDate periodEnd, BigDecimal estimatedHours, BigDecimal actualHours, BigDecimal diffHours,
      Integer ratio, int issueCount, int logCount) {
  }

  /** 합계 — 예상은 결과에 든 일감마다 한 번씩 */
  public record Totals(BigDecimal estimatedHours, BigDecimal actualHours, BigDecimal diffHours, Integer ratio,
      int issueCount, int logCount, int personCount) {
  }

  /**
   * @param removedLogs 조건에 맞지만 지운 기록 수 (집계에서 뺐다)
   * @param projects    부른 사람이 읽을 수 있는 프로젝트 (조건 고르기용)
   * @param types       일감 유형 (조건 고르기용)
   * @param people      사람 조건을 빼고 본 범위에서 기록을 남긴 사람 (조건 고르기용 — 떠난 멤버 포함)
   */
  public record Report(LocalDate from, LocalDate to, String groupBy, List<Row> rows, Totals total, int removedLogs,
      List<ProjectRef> projects, List<TypeRef> types, List<IssueViews.Person> people) {
  }

  private final NamedParameterJdbcTemplate jdbc;
  private final ProjectRepository projects;
  private final ProjectAccessService access;
  private final IssueRepositories.Issues issues;
  private final IssueRepositories.Types types;
  private final UserProfileRepository users;
  private final AuditService audit;

  public EffortService(NamedParameterJdbcTemplate jdbc, ProjectRepository projects, ProjectAccessService access,
      IssueRepositories.Issues issues, IssueRepositories.Types types, UserProfileRepository users, AuditService audit) {
    this.jdbc = jdbc;
    this.projects = projects;
    this.access = access;
    this.issues = issues;
    this.types = types;
    this.users = users;
    this.audit = audit;
  }

  // ── 조건 읽기 (REST 매개변수 → Query) ──────────────────────────────────

  /**
   * 주소의 조건을 읽는다 — 날짜 모양(yyyy-MM-dd) · 사람(id 또는 me) · 기준이 틀리면 400 {@code INVALID_FILTER}.
   * 유형 · 프로젝트가 있는지는 {@link #report} 가 본다.
   */
  public static Query parse(UUID me, String from, String to, List<String> projects, List<String> people,
      List<String> types, String groupBy) {
    LocalDate start = date(from, "from");
    LocalDate end = date(to, "to");
    if (start != null && end != null && start.isAfter(end)) {
      throw ApiException.badRequest("INVALID_FILTER", "시작 날짜가 끝 날짜보다 뒤입니다.");
    }
    List<UUID> who = new ArrayList<>();
    for (String p : clean(people)) {
      if ("me".equals(p)) {
        who.add(me);
        continue;
      }
      try {
        who.add(UUID.fromString(p));
      } catch (IllegalArgumentException e) {
        throw ApiException.badRequest("INVALID_FILTER", "사람은 사용자 id 나 me 로 적습니다: " + p);
      }
    }
    GroupBy by = groupBy == null || groupBy.isBlank() ? GroupBy.PROJECT
        : GroupBy.of(groupBy.trim()).orElseThrow(() -> ApiException.badRequest("INVALID_FILTER",
            "모으는 기준은 person · project · issue · type · day · week · month 가운데 하나입니다."));
    return new Query(start, end, clean(projects), List.copyOf(new LinkedHashSet<>(who)), clean(types), by);
  }

  private static LocalDate date(String value, String name) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return LocalDate.parse(value.trim());
    } catch (DateTimeParseException e) {
      throw ApiException.badRequest("INVALID_FILTER", name + " 는 yyyy-MM-dd 날짜입니다.");
    }
  }

  private static List<String> clean(List<String> values) {
    if (values == null) {
      return List.of();
    }
    return values.stream().filter(Objects::nonNull).map(String::trim).filter(s -> !s.isEmpty()).distinct().toList();
  }

  // ── 집계 ─────────────────────────────────────────────────────────────

  @Transactional(readOnly = true)
  public Report report(WorkspaceContext ws, Query q) {
    // 읽을 수 있는 프로젝트 — 판정은 프로젝트 판정 한 곳(@ProjectApi 와 같은 규칙)
    Map<UUID, Project> readable = new LinkedHashMap<>();
    Map<String, Boolean> visibleKeys = new HashMap<>();
    for (Project p : projects.findLiveByWorkspace(ws.workspaceId())) {
      Optional<ProjectContext> ctx = access.contextFor(ws, p);
      ctx.ifPresent(c -> visibleKeys.put(p.getKey(), c.has(Permission.ISSUE_READ)));
      ctx.filter(c -> c.has(Permission.ISSUE_READ)).ifPresent(c -> readable.put(p.getId(), p));
    }
    Set<UUID> scope = new LinkedHashSet<>();
    if (q.projects().isEmpty()) {
      scope.addAll(readable.keySet());
    } else {
      for (String key : q.projects()) {
        Boolean canRead = visibleKeys.get(key);
        if (canRead == null) {
          throw ApiException.notFound("PROJECT_NOT_FOUND", "프로젝트를 찾을 수 없습니다.");
        }
        if (!canRead) {
          throw ApiException.forbidden("PERMISSION_REQUIRED", "권한 " + Permission.ISSUE_READ.code() + " 이(가) 필요합니다.");
        }
        readable.values().stream().filter(p -> p.getKey().equals(key)).findFirst().ifPresent(p -> scope.add(p.getId()));
      }
    }

    Map<String, IssueType> typeByKey = new LinkedHashMap<>();
    types.findAllByOrderByPositionAsc().forEach(t -> typeByKey.put(t.getKey(), t));
    for (String t : q.types()) {
      if (!typeByKey.containsKey(t)) {
        throw ApiException.badRequest("INVALID_FILTER", "없는 일감 유형입니다: " + t);
      }
    }

    List<ProjectRef> projectRefs = readable.values().stream().map(p -> new ProjectRef(p.getKey(), p.getName())).toList();
    List<TypeRef> typeRefs = typeByKey.values().stream().map(t -> new TypeRef(t.getKey(), t.getName())).toList();
    if (scope.isEmpty()) {
      return new Report(q.from(), q.to(), q.groupBy().code(), List.of(),
          new Totals(null, money(BigDecimal.ZERO), null, null, 0, 0, 0), 0, projectRefs, typeRefs, List.of());
    }

    // 기록을 (일감, 사람, 날) 로 먼저 모은다 — 사람 조건은 고르기 목록(people)을 만든 뒤에 건다
    List<Cell> all = cells(scope, q);
    Set<UUID> peopleFilter = new HashSet<>(q.people());
    List<Cell> cells = peopleFilter.isEmpty() ? all : all.stream().filter(c -> peopleFilter.contains(c.user())).toList();
    int removed = removedCount(scope, q);

    Map<UUID, Issue> issueById = new HashMap<>();
    issues.findAllById(cells.stream().map(Cell::issue).collect(java.util.stream.Collectors.toSet()))
        .forEach(i -> issueById.put(i.getId(), i));
    Map<UUID, UserProfile> profiles = new HashMap<>();
    Set<UUID> userIds = new HashSet<>();
    all.forEach(c -> userIds.add(c.user()));
    users.findAllById(userIds).forEach(u -> profiles.put(u.getId(), u));

    // 기준마다 한 줄
    Map<String, Group> groups = new LinkedHashMap<>();
    for (Cell c : cells) {
      Issue issue = issueById.get(c.issue());
      if (issue == null) {
        continue;
      }
      Project project = readable.get(issue.getProjectId());
      String key = keyOf(q.groupBy(), c, issue, project);
      Group g = groups.computeIfAbsent(key, k -> new Group(k, c, issue, project));
      g.actual = g.actual.add(c.hours());
      g.logs += c.logs();
      g.issues.add(issue.getId());
      g.users.add(c.user());
    }

    List<Row> rows = new ArrayList<>();
    for (Group g : groups.values()) {
      BigDecimal estimate = q.groupBy().carriesEstimate() ? estimateOf(g.issues, issueById) : null;
      rows.add(row(q.groupBy(), g, estimate, profiles, typeByKey));
    }
    rows.sort(order(q.groupBy()));

    Set<UUID> allIssues = new HashSet<>();
    Set<UUID> allUsers = new HashSet<>();
    BigDecimal actual = BigDecimal.ZERO;
    int logs = 0;
    for (Group g : groups.values()) {
      allIssues.addAll(g.issues);
      allUsers.addAll(g.users);
      actual = actual.add(g.actual);
      logs += g.logs;
    }
    BigDecimal estimate = estimateOf(allIssues, issueById);
    Totals total = new Totals(money(estimate), money(actual), diff(actual, estimate), ratio(actual, estimate),
        allIssues.size(), logs, allUsers.size());

    List<IssueViews.Person> people = userIds.stream().map(id -> person(profiles, id))
        .sorted(Comparator.comparing(EffortService::personLabel)).toList();
    return new Report(q.from(), q.to(), q.groupBy().code(), rows, total, removed, projectRefs, typeRefs, people);
  }

  /**
   * CSV 로 내려받기 — 화면과 같은 집계({@link #report})를 그대로 적고, 내려받은 일을 감사에 남긴다
   * ({@code issue.work_log.export} — 조건 · 줄 수).
   */
  @Transactional
  public String exportCsv(WorkspaceContext ws, Query q, String lang, Actor actor) {
    Report report = report(ws, q);
    Map<String, Object> details = new LinkedHashMap<>();
    details.put("groupBy", report.groupBy());
    details.put("from", q.from() == null ? null : q.from().toString());
    details.put("to", q.to() == null ? null : q.to().toString());
    details.put("projects", q.projects());
    details.put("people", q.people().stream().map(UUID::toString).toList());
    details.put("types", q.types());
    details.put("rows", report.rows().size());
    details.put("via", IssueJournal.Via.WEB.name());
    audit.record(actor, ws.workspaceId(), "issue.work_log.export", "workspace", ws.workspaceId().toString(), details);
    return EffortCsv.write(report, lang);
  }

  // ── 조각 ─────────────────────────────────────────────────────────────

  /** (일감, 사람, 날) 하나의 합 */
  private record Cell(UUID issue, UUID user, LocalDate date, BigDecimal hours, int logs) {
  }

  private List<Cell> cells(Collection<UUID> scope, Query q) {
    MapSqlParameterSource p = new MapSqlParameterSource();
    String where = where(scope, q, false, p);
    return jdbc.query("SELECT w.issue_id, w.user_id, w.work_date, sum(w.hours) AS hours, count(*) AS logs"
        + " FROM issue_work_log w JOIN issue i ON i.id = w.issue_id WHERE " + where
        + " AND w.deleted_at IS NULL GROUP BY w.issue_id, w.user_id, w.work_date", p,
        (rs, n) -> new Cell(rs.getObject("issue_id", UUID.class), rs.getObject("user_id", UUID.class),
            rs.getObject("work_date", LocalDate.class), rs.getBigDecimal("hours"), rs.getInt("logs")));
  }

  private int removedCount(Collection<UUID> scope, Query q) {
    MapSqlParameterSource p = new MapSqlParameterSource();
    String where = where(scope, q, true, p);
    Integer n = jdbc.queryForObject("SELECT count(*) FROM issue_work_log w JOIN issue i ON i.id = w.issue_id WHERE "
        + where + " AND w.deleted_at IS NOT NULL", p, Integer.class);
    return n == null ? 0 : n;
  }

  /** 공통 조건 — 범위의 프로젝트 · 지우지 않은 일감 · 기간 · 유형 (withPeople 이면 사람도) */
  private static String where(Collection<UUID> scope, Query q, boolean withPeople, MapSqlParameterSource p) {
    StringBuilder w = new StringBuilder("i.project_id IN (:projects) AND i.deleted_at IS NULL");
    p.addValue("projects", scope);
    if (q.from() != null) {
      w.append(" AND w.work_date >= :from");
      p.addValue("from", Date.valueOf(q.from()));
    }
    if (q.to() != null) {
      w.append(" AND w.work_date <= :to");
      p.addValue("to", Date.valueOf(q.to()));
    }
    if (!q.types().isEmpty()) {
      w.append(" AND i.issue_type IN (:types)");
      p.addValue("types", q.types());
    }
    if (withPeople && !q.people().isEmpty()) {
      w.append(" AND w.user_id IN (:people)");
      p.addValue("people", q.people());
    }
    return w.toString();
  }

  /** 한 줄로 모이는 동안의 합 */
  private static final class Group {
    final String key;
    final Cell first;
    final Issue issue;
    final Project project;
    BigDecimal actual = BigDecimal.ZERO;
    int logs;
    final Set<UUID> issues = new LinkedHashSet<>();
    final Set<UUID> users = new LinkedHashSet<>();

    Group(String key, Cell first, Issue issue, Project project) {
      this.key = key;
      this.first = first;
      this.issue = issue;
      this.project = project;
    }
  }

  private static String keyOf(GroupBy by, Cell c, Issue issue, Project project) {
    return switch (by) {
      case PERSON -> c.user().toString();
      case PROJECT -> project.getKey();
      case ISSUE -> project.getKey() + "#" + issue.getNumber();
      case TYPE -> issue.getIssueType();
      case DAY -> c.date().toString();
      case WEEK -> weekStart(c.date()).toString();
      case MONTH -> c.date().withDayOfMonth(1).toString().substring(0, 7);
    };
  }

  /** 그 주의 월요일 */
  static LocalDate weekStart(LocalDate day) {
    return day.minusDays(day.getDayOfWeek().getValue() - DayOfWeek.MONDAY.getValue());
  }

  private static Row row(GroupBy by, Group g, BigDecimal estimate, Map<UUID, UserProfile> profiles,
      Map<String, IssueType> typeByKey) {
    ProjectRef project = null;
    IssueRef issue = null;
    IssueViews.Person person = null;
    String type = null;
    LocalDate period = null;
    LocalDate periodEnd = null;
    String label;
    switch (by) {
      case PERSON -> {
        person = person(profiles, g.first.user());
        label = personLabel(person);
      }
      case PROJECT -> {
        project = new ProjectRef(g.project.getKey(), g.project.getName());
        label = g.project.getName();
      }
      case ISSUE -> {
        project = new ProjectRef(g.project.getKey(), g.project.getName());
        issue = new IssueRef(g.project.getKey(), g.issue.getNumber(), g.issue.getTitle(), g.issue.getIssueType());
        type = g.issue.getIssueType();
        label = g.issue.getTitle();
      }
      case TYPE -> {
        type = g.issue.getIssueType();
        IssueType t = typeByKey.get(type);
        label = t == null ? type : t.getName();
      }
      case DAY -> {
        period = g.first.date();
        periodEnd = period;
        label = period.toString();
      }
      case WEEK -> {
        period = weekStart(g.first.date());
        periodEnd = period.plusDays(6);
        label = period + " ~ " + periodEnd;
      }
      default -> {
        period = g.first.date().withDayOfMonth(1);
        periodEnd = period.withDayOfMonth(period.lengthOfMonth());
        label = g.key;
      }
    }
    return new Row(g.key, label, project, issue, person, type, period, periodEnd, money(estimate), money(g.actual),
        diff(g.actual, estimate), ratio(g.actual, estimate), g.issues.size(), g.logs);
  }

  /** 기간은 앞날부터, 그 밖은 실제 투입이 많은 것부터(같으면 이름 · 기준 값 순) */
  private static Comparator<Row> order(GroupBy by) {
    if (by.isPeriod()) {
      return Comparator.comparing(Row::period);
    }
    return Comparator.comparing(Row::actualHours).reversed().thenComparing(Row::label).thenComparing(Row::key);
  }

  /** 일감마다 한 번씩 더한 예상 — 예상이 있는 일감이 없으면 null */
  private static BigDecimal estimateOf(Collection<UUID> issueIds, Map<UUID, Issue> issueById) {
    BigDecimal sum = null;
    for (UUID id : issueIds) {
      Issue issue = issueById.get(id);
      if (issue != null && issue.getEstimatedHours() != null) {
        sum = (sum == null ? BigDecimal.ZERO : sum).add(issue.getEstimatedHours());
      }
    }
    return sum;
  }

  private static BigDecimal diff(BigDecimal actual, BigDecimal estimate) {
    return estimate == null ? null : money(actual.subtract(estimate));
  }

  private static Integer ratio(BigDecimal actual, BigDecimal estimate) {
    if (estimate == null || estimate.signum() == 0) {
      return null;
    }
    return actual.multiply(BigDecimal.valueOf(100)).divide(estimate, 0, RoundingMode.HALF_UP).intValue();
  }

  /** 시간은 소수 둘째 자리까지(기록 · 예상과 같은 자릿수) */
  private static BigDecimal money(BigDecimal value) {
    return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
  }

  private static IssueViews.Person person(Map<UUID, UserProfile> profiles, UUID id) {
    UserProfile u = profiles.get(id);
    return u == null ? new IssueViews.Person(id, null, null) : new IssueViews.Person(id, u.getUsername(), u.getDisplayName());
  }

  /** 사람의 보이는 이름 — 이름, 없으면 아이디, 그것도 없으면 id 앞부분 */
  static String personLabel(IssueViews.Person p) {
    if (p.displayName() != null && !p.displayName().isBlank()) {
      return p.displayName();
    }
    if (p.username() != null && !p.username().isBlank()) {
      return p.username();
    }
    return p.id().toString().substring(0, 8);
  }
}
