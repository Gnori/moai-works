package com.moai.works.api.project;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
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
import com.moai.works.api.common.Ids;
import com.moai.works.api.issue.Issue;
import com.moai.works.api.security.access.ProjectContext;
import com.moai.works.api.workspace.Permission;
import com.moai.works.api.workspace.WorkspaceAccessService;
import com.moai.works.api.workspace.WorkspaceMember;
import com.moai.works.api.workspace.WorkspaceRepositories;

/**
 * 프로젝트의 에이전트 멤버 — 목록 · 손으로 더하기 · 고치기(설명 · 주인 · 쓰는지) · 일감에서 자동 등록 (2026-11-25 사용자, 일감 #285:
 * "프로젝트 멤버에 에이전트 세션네임도 추가해줘" · "멤버는 사람, 에이전트 2가지로 나눠 지는거지").
 *
 * <ul>
 *   <li><b>에이전트 멤버는 이름표다</b> — 세션 이름과 주인(그 에이전트를 돌리는 MCP 키의 주인). 자기 권한이 없고 판정에 쓰이지 않는다.
 *       에이전트는 주인의 MCP 키로, 주인의 권한으로만 일한다(ADR-024 — 키는 모두 사람의 것). 이 서비스는 판정을 바꾸지 않는다</li>
 *   <li>보기는 프로젝트 독자 누구나(프로젝트 역할 목록과 같은 {@code issue.read}). 더하기 · 고치기는 {@code project.manage} — 여기서도 다시 본다</li>
 *   <li>자동 등록({@link #register}) — 일감에 그 이름이 들어올 때(만들기 · 고치기 · 옮기기, 웹 · MCP 모두) 그 프로젝트에 없으면 AUTO 로,
 *       주인 = 그 일을 한 사람. 같은 이름(대소문자 없이)이 이미 있으면 아무것도 하지 않는다 — 고유 색인 + {@code INSERT … ON CONFLICT DO NOTHING}
 *       이라 동시에 와도 하나만 생긴다. 쓰지 않는 에이전트의 이름이어도 막지 않는다(부르는 쪽에 INACTIVE 를 알린다)</li>
 *   <li>지우지 않는다 — 쓰지 않음으로 둔다. 이름은 바꾸지 않는다(일감의 agentSession 과 잇는 열쇠)</li>
 *   <li>모두 감사에 남는다 — {@code project.agent.create}(source AUTO · MANUAL) · {@code project.agent.update}(경로 WEB · MCP)</li>
 *   <li>보관한 프로젝트는 손으로 바꾸지 못한다(409 {@code PROJECT_NOT_WRITABLE}) — 보관한 프로젝트의 일감은 고칠 수 없으니 자동 등록도 없다</li>
 * </ul>
 */
@Service
public class ProjectAgentService {

  private static final Set<String> UPDATE_FIELDS = Set.of("description", "active", "owner", "rowVersion");

  /** 일감에 이름이 들어왔을 때 그 이름의 에이전트 — 이번에 새로 등록했나, 쓰는 에이전트인가, 쓰지 않는 에이전트인가 */
  public enum Registration {
    /** 이번에 새로 등록했다(AUTO, 주인 = 그 일을 한 사람) */
    REGISTERED,
    /** 이미 있는 쓰는 에이전트 */
    ACTIVE,
    /** 이미 있는 쓰지 않는 에이전트 — 막지 않는다 */
    INACTIVE
  }

  private final ProjectAgentRepository agents;
  private final UserProfileRepository users;
  private final WorkspaceRepositories.Members members;
  private final WorkspaceAccessService workspaceAccess;
  private final ProjectAccessService projectAccess;
  private final AuditService audit;
  private final NamedParameterJdbcTemplate jdbc;
  private final TransactionTemplate tx;
  private final Clock clock;

  public ProjectAgentService(ProjectAgentRepository agents, UserProfileRepository users, WorkspaceRepositories.Members members,
      WorkspaceAccessService workspaceAccess, ProjectAccessService projectAccess, AuditService audit, NamedParameterJdbcTemplate jdbc,
      PlatformTransactionManager txManager, Clock clock) {
    this.agents = agents;
    this.users = users;
    this.members = members;
    this.workspaceAccess = workspaceAccess;
    this.projectAccess = projectAccess;
    this.audit = audit;
    this.jdbc = jdbc;
    this.tx = new TransactionTemplate(txManager);
    this.clock = clock;
  }

  // ── 보기 ──────────────────────────────────────────────────────────

  /** 이 프로젝트의 에이전트 전부(쓰지 않는 것 포함) — 쓰는 것 먼저, 이름순. 일감 수 · 열린 일감 수 · 마지막으로 고친 때와 함께 */
  @Transactional(readOnly = true)
  public List<ProjectViews.Agent> list(ProjectContext ctx) {
    List<ProjectAgent> rows = agents.findOrdered(ctx.projectId());
    Map<UUID, Object[]> stats = stats(ctx.projectId());
    Map<UUID, UserProfile> owners = owners(rows);
    return rows.stream().map(a -> view(a, owners, stats)).toList();
  }

  // ── 관리 (project.manage) ───────────────────────────────────────────

  /**
   * 손으로 더한다(MANUAL) — 아직 일감에 쓰지 않은 에이전트를 미리 두거나 주인을 처음부터 정할 때.
   *
   * @param owner 주인의 아이디(username) — 비우면 부른 사람. 이 워크스페이스의 활성 멤버이고 이 프로젝트를 읽을 수 있어야 한다
   */
  public ProjectViews.Agent create(ProjectContext ctx, String name, String owner, String description, Actor actor, String via) {
    requireManage(ctx);
    String n = ProjectAgent.normalizeName(name);
    if (n == null) {
      throw ApiException.badRequest("INVALID_AGENT_SESSION", "에이전트 세션 이름을 넣으십시오(100자까지).");
    }
    String d = description(description);
    UserProfile ownerProfile = owner == null || owner.isBlank() ? self(ctx) : person(ctx, owner);
    ProjectAgent saved;
    try {
      saved = tx.execute(s -> {
        requireOpen(ctx);
        if (agents.findByName(ctx.projectId(), n).isPresent()) {
          throw taken(n);
        }
        ProjectAgent a = agents.saveAndFlush(new ProjectAgent(ctx.projectId(), n, ownerProfile.getId(), d, ProjectAgent.Source.MANUAL,
            actor.id(), Instant.now(clock)));
        Map<String, Object> details = details(ctx.project(), n, via);
        details.put("source", ProjectAgent.Source.MANUAL.name());
        details.put("owner", ownerProfile.getUsername());
        audit.record(actor, ctx.workspaceId(), "project.agent.create", "project_agent", a.getId().toString(), details);
        return a;
      });
    } catch (DataIntegrityViolationException e) {
      // 같은 이름을 동시에 더했다(자동 등록과 겹친 것도) — 고유 색인이 막았다
      throw taken(n);
    }
    return one(ctx, saved.getId());
  }

  /**
   * 고친다 — 준 것만(description · active · owner). description 에 null · 빈 글을 주면 지운다. owner 는 아이디(username) — 비울 수 없다.
   * rowVersion 을 주면 지금 판과 견준다(다르면 409 CONCURRENT_UPDATE). 바뀐 것이 없으면 아무것도 하지 않는다. 이름은 바꾸지 않는다.
   */
  public ProjectViews.Agent update(ProjectContext ctx, UUID id, Map<String, Object> body, Actor actor, String via) {
    requireManage(ctx);
    for (String field : body.keySet()) {
      if (!UPDATE_FIELDS.contains(field)) {
        throw ApiException.badRequest("UNKNOWN_FIELD", "고칠 수 없는 항목입니다: " + field);
      }
    }
    Integer rowVersion = null;
    if (body.get("rowVersion") != null) {
      if (!(body.get("rowVersion") instanceof Number n)) {
        throw ApiException.badRequest("INVALID_FIELD", "rowVersion 은 숫자여야 합니다.");
      }
      rowVersion = n.intValue();
    }
    boolean descriptionGiven = body.containsKey("description");
    String description = descriptionGiven ? description(text(body, "description")) : null;
    Boolean active = null;
    if (body.containsKey("active")) {
      if (!(body.get("active") instanceof Boolean b)) {
        throw ApiException.badRequest("INVALID_FIELD", "active 는 true · false 여야 합니다.");
      }
      active = b;
    }
    UserProfile owner = null;
    if (body.containsKey("owner")) {
      String username = text(body, "owner");
      if (username == null || username.isBlank()) {
        throw ApiException.badRequest("PERSON_NOT_FOUND", "주인을 비울 수 없습니다 — 그 에이전트를 돌리는 MCP 키 주인의 아이디를 넣으십시오.");
      }
      owner = person(ctx, username);
    }
    Integer expected = rowVersion;
    Boolean wantActive = active;
    UserProfile wantOwner = owner;
    tx.executeWithoutResult(s -> {
      requireOpen(ctx);
      ProjectAgent a = agents.findById(id).filter(x -> x.getProjectId().equals(ctx.projectId())).orElseThrow(ProjectAgentService::notFound);
      if (expected != null && a.getRowVersion() != expected) {
        throw ApiException.conflict("CONCURRENT_UPDATE", "다른 곳에서 먼저 고친 에이전트입니다. 다시 읽고 고치십시오.")
            .with("rowVersion", a.getRowVersion());
      }
      List<String> fields = new ArrayList<>();
      Map<String, Object> details = details(ctx.project(), a.getName(), via);
      if (descriptionGiven && !Objects.equals(description, a.getDescription())) {
        a.describe(description);
        fields.add("description");
      }
      if (wantActive != null && wantActive != a.isActive()) {
        a.activate(wantActive);
        details.put("active", wantActive);
        fields.add("active");
      }
      if (wantOwner != null && !wantOwner.getId().equals(a.getOwnerUserId())) {
        details.put("ownerBefore", a.getOwnerUserId() == null ? null
            : users.findById(a.getOwnerUserId()).map(UserProfile::getUsername).orElse(a.getOwnerUserId().toString()));
        details.put("owner", wantOwner.getUsername());
        a.ownBy(wantOwner.getId());
        fields.add("owner");
      }
      if (fields.isEmpty()) {
        return;
      }
      a.touch(actor.id(), Instant.now(clock));
      details.put("fields", fields);
      audit.record(actor, ctx.workspaceId(), "project.agent.update", "project_agent", a.getId().toString(), details);
    });
    return one(ctx, id);
  }

  // ── 일감에서 자동 등록 ───────────────────────────────────────────────

  /**
   * 일감에 그 이름이 들어왔다 — 이 프로젝트에 없으면 AUTO 로 등록한다(주인 = {@code actor}). 부르는 쪽(일감 서비스)의 트랜잭션 안에서 부른다 —
   * 일감 쓰기가 되돌려지면 등록도 되돌려진다. 같은 이름(대소문자 없이)이 이미 있으면 그대로 두고 그 상태를 알린다. 동시에 와도 하나만 생긴다
   * (고유 색인 {@code ux_project_agent_name} + {@code ON CONFLICT DO NOTHING}).
   *
   * @param name        다듬은 세션 이름({@link ProjectAgent#normalizeName} 을 거친 것)
   * @param issueNumber 그 이름이 들어온 일감 번호 — 감사에 남긴다
   */
  public Registration register(UUID workspaceId, Project project, String name, Actor actor, String via, int issueNumber) {
    Instant now = Instant.now(clock);
    UUID id = Ids.next();
    UUID by = actor == null ? null : actor.id();
    MapSqlParameterSource params = new MapSqlParameterSource("id", id).addValue("project", project.getId()).addValue("name", name)
        .addValue("owner", by).addValue("now", Timestamp.from(now));
    List<UUID> inserted = jdbc.queryForList("INSERT INTO project_agent (id, project_id, name, owner_user_id, active, source,"
        + " created_at, created_by, updated_at, updated_by, row_version)"
        + " VALUES (:id, :project, :name, :owner, true, 'AUTO', :now, :owner, :now, :owner, 0)"
        + " ON CONFLICT (project_id, lower(name)) DO NOTHING RETURNING id", params, UUID.class);
    if (!inserted.isEmpty()) {
      Map<String, Object> details = details(project, name, via);
      details.put("source", ProjectAgent.Source.AUTO.name());
      if (by != null) {
        details.put("owner", users.findById(by).map(UserProfile::getUsername).orElse(by.toString()));
      }
      details.put("number", issueNumber);
      audit.record(actor, workspaceId, "project.agent.create", "project_agent", id.toString(), details);
      return Registration.REGISTERED;
    }
    // 이미 있다 — 충돌한 행은 이미 커밋됐다(ON CONFLICT 가 기다렸다)
    return agents.findByName(project.getId(), name).map(a -> a.isActive() ? Registration.ACTIVE : Registration.INACTIVE)
        .orElse(Registration.ACTIVE);
  }

  // ── 안 ────────────────────────────────────────────────────────────

  private ProjectViews.Agent one(ProjectContext ctx, UUID id) {
    return list(ctx).stream().filter(a -> a.id().equals(id)).findFirst().orElseThrow(ProjectAgentService::notFound);
  }

  /** 에이전트 id → [id, 열린 일감 수, 모든 일감 수, 마지막으로 고친 때] */
  private Map<UUID, Object[]> stats(UUID projectId) {
    Map<UUID, Object[]> out = new HashMap<>();
    agents.issueStats(projectId, Issue.Status.open()).forEach(row -> out.put((UUID) row[0], row));
    return out;
  }

  private Map<UUID, UserProfile> owners(List<ProjectAgent> rows) {
    Set<UUID> ids = rows.stream().map(ProjectAgent::getOwnerUserId).filter(Objects::nonNull).collect(Collectors.toSet());
    return ids.isEmpty() ? Map.of() : users.findAllById(ids).stream().collect(Collectors.toMap(UserProfile::getId, u -> u));
  }

  private static ProjectViews.Agent view(ProjectAgent a, Map<UUID, UserProfile> owners, Map<UUID, Object[]> stats) {
    UserProfile o = a.getOwnerUserId() == null ? null : owners.get(a.getOwnerUserId());
    ProjectViews.Person owner = o == null ? null : new ProjectViews.Person(o.getId(), o.getUsername(), o.getDisplayName());
    Object[] s = stats.get(a.getId());
    long open = s == null || s[1] == null ? 0 : ((Number) s[1]).longValue();
    long total = s == null ? 0 : ((Number) s[2]).longValue();
    Instant last = s == null ? null : (Instant) s[3];
    return new ProjectViews.Agent(a.getId(), a.getName(), owner, a.getDescription(), a.isActive(), a.getSource(), a.getCreatedAt(),
        open, total, last, a.getRowVersion());
  }

  /** 부른 사람 자신 — 이 프로젝트의 독자다(문맥이 있으니) */
  private UserProfile self(ProjectContext ctx) {
    return users.findById(ctx.userId()).orElseThrow(() -> ApiException.badRequest("PERSON_NOT_FOUND", "계정을 찾을 수 없습니다."));
  }

  /**
   * 주인 — 아이디(username, 대소문자 없이)로 이 워크스페이스의 활성 멤버를 찾는다. 없으면 400 {@code PERSON_NOT_FOUND}.
   * 이 프로젝트를 읽을 수 있어야 한다(에이전트는 주인의 권한으로 일한다 — 읽지 못하는 사람의 키로는 이 프로젝트에서 일할 수 없다).
   * 아니면 400 {@code USER_NOT_PROJECT_READER}
   */
  private UserProfile person(ProjectContext ctx, String username) {
    String v = username.trim();
    List<UUID> active = members.findByWorkspace(ctx.workspaceId()).stream().filter(WorkspaceMember::isActive)
        .map(WorkspaceMember::getUserId).toList();
    UserProfile u = users.findAllById(active).stream().filter(p -> v.equalsIgnoreCase(p.getUsername())).findFirst()
        .orElseThrow(() -> ApiException.badRequest("PERSON_NOT_FOUND", "이 워크스페이스의 멤버가 아닙니다: " + v));
    boolean reader = workspaceAccess.contextFor(ctx.workspace().workspace(), u.getId())
        .flatMap(ws -> projectAccess.contextFor(ws, ctx.project())).filter(p -> p.has(Permission.ISSUE_READ)).isPresent();
    if (!reader) {
      throw ApiException.badRequest("USER_NOT_PROJECT_READER", "주인이 이 프로젝트를 볼 수 없습니다: " + v
          + " — 에이전트는 주인의 권한으로 일하므로 이 프로젝트의 독자여야 합니다.");
    }
    return u;
  }

  private static Map<String, Object> details(Project project, String name, String via) {
    Map<String, Object> d = new LinkedHashMap<>();
    d.put("project", project.getKey());
    d.put("agent", name);
    d.put("via", via);
    return d;
  }

  private static void requireManage(ProjectContext ctx) {
    if (!ctx.has(Permission.PROJECT_MANAGE)) {
      throw ApiException.forbidden("PERMISSION_REQUIRED", "권한 " + Permission.PROJECT_MANAGE.code() + " 이(가) 필요합니다.");
    }
    requireOpen(ctx);
  }

  private static void requireOpen(ProjectContext ctx) {
    if (!ctx.project().isOpenToReaders()) {
      throw ApiException.conflict("PROJECT_NOT_WRITABLE", "보관한 프로젝트의 에이전트는 바꿀 수 없습니다. 먼저 복원하십시오.");
    }
  }

  private static String description(String value) {
    String d = value == null ? "" : value.trim();
    if (d.length() > ProjectAgent.MAX_DESCRIPTION) {
      throw ApiException.badRequest("AGENT_DESCRIPTION_TOO_LONG", "에이전트 설명은 " + ProjectAgent.MAX_DESCRIPTION + "자를 넘을 수 없습니다.");
    }
    return d.isEmpty() ? null : d;
  }

  private static String text(Map<String, Object> body, String key) {
    Object v = body.get(key);
    if (v == null || v instanceof String) {
      return (String) v;
    }
    throw ApiException.badRequest("INVALID_FIELD", key + " 는 글자여야 합니다.");
  }

  private static ApiException taken(String name) {
    return ApiException.conflict("AGENT_NAME_TAKEN", "이 프로젝트에 같은 이름(대소문자 없이)의 에이전트가 있습니다: " + name);
  }

  private static ApiException notFound() {
    return ApiException.notFound("AGENT_NOT_FOUND", "에이전트를 찾을 수 없습니다.");
  }

  /**
   * 일감 양식 · 목록 거르기가 고를 세션 이름 하나.
   *
   * @param registered 이 프로젝트의 에이전트 멤버인가
   * @param active     쓰는 에이전트인가(멤버가 아니면 false)
   */
  public record SessionOption(String name, long issueCount, boolean registered, boolean active) {
  }

  /**
   * 고를 세션 이름 — 쓰는 에이전트 멤버(일감이 없어도) 먼저, 그다음 일감이 있는 쓰지 않는 에이전트, 그다음 멤버가 아닌 쓰인 이름(대소문자 없이
   * 하나 — 가장 많이 쓴 글자). 각각 이름순. 일감 수는 지운 일감을 빼고 대소문자 없이 센다.
   */
  @Transactional(readOnly = true)
  public List<SessionOption> sessionOptions(UUID projectId) {
    Map<String, Long> countByLower = new HashMap<>();
    Map<String, Object[]> spellingByLower = new HashMap<>();
    for (Object[] row : agents.usedSessions(projectId)) {
      String lowerName = (String) row[0];
      long n = ((Number) row[2]).longValue();
      countByLower.merge(lowerName, n, Long::sum);
      Object[] best = spellingByLower.get(lowerName);
      if (best == null || n > ((Number) best[2]).longValue()
          || (n == ((Number) best[2]).longValue() && ((String) row[1]).compareTo((String) best[1]) < 0)) {
        spellingByLower.put(lowerName, row);
      }
    }
    Map<UUID, String> lowerById = new HashMap<>();
    agents.lowerNames(projectId).forEach(row -> lowerById.put((UUID) row[0], (String) row[1]));
    List<SessionOption> active = new ArrayList<>();
    List<SessionOption> inactive = new ArrayList<>();
    Set<String> registered = new java.util.HashSet<>();
    for (ProjectAgent a : agents.findOrdered(projectId)) {
      String key = lowerById.get(a.getId());
      registered.add(key);
      long count = countByLower.getOrDefault(key, 0L);
      if (a.isActive()) {
        active.add(new SessionOption(a.getName(), count, true, true));
      } else if (count > 0) {
        inactive.add(new SessionOption(a.getName(), count, true, false));
      }
    }
    List<SessionOption> others = spellingByLower.entrySet().stream().filter(e -> !registered.contains(e.getKey()))
        .map(e -> new SessionOption((String) e.getValue()[1], countByLower.get(e.getKey()), false, false))
        .sorted(java.util.Comparator.comparing((SessionOption o) -> o.name().toLowerCase(Locale.ROOT)).thenComparing(SessionOption::name))
        .toList();
    List<SessionOption> out = new ArrayList<>(active);
    out.addAll(inactive);
    out.addAll(others);
    return out;
  }
}
