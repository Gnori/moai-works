package com.moai.works.api.project;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.moai.works.api.audit.AuditService;
import com.moai.works.api.common.Actor;
import com.moai.works.api.common.ApiException;
import com.moai.works.api.security.access.ProjectContext;
import com.moai.works.api.workspace.Permission;

/**
 * 프로젝트 모듈 — 목록 · 더하기 · 이름 바꾸기 · 설명 · 순서 · 쓰지 않음 (2026-11-23 사용자, 일감 #27:
 * "프로젝트에서 모듈을 등록해서 항목을 관리하도록 하자" · "이슈에 멀티로 관련 모듈을 등록할 수 있게 해 줘").
 *
 * <ul>
 *   <li>보기는 독자 누구나({@code issue.read} — REST {@code @ProjectApi} · MCP 가 본다). 관리는 {@code project.manage} —
 *       여기서도 다시 본다(MCP 도 같은 판정)</li>
 *   <li>지우지 않는다 — 쓰지 않음으로 두면 새로 고를 수 없고 이미 붙은 일감에는 남는다(일감 유형과 같다). key 는 바꾸지 않는다</li>
 *   <li>관리는 감사에 남는다 — {@code project.module.create} · {@code project.module.update} · {@code project.module.reorder}
 *       (경로 WEB · MCP)</li>
 *   <li>보관한 프로젝트는 읽기 전용(409 {@code PROJECT_NOT_WRITABLE})</li>
 * </ul>
 * 일감에 붙일 모듈을 key 로 찾는 일({@link #resolve})도 여기서 한다 — 일감 서비스가 REST · MCP 모두에서 부른다.
 */
@Service
public class ProjectModuleService {

  /** 한 프로젝트의 모듈 상한 */
  public static final int MAX_MODULES = 100;
  static final int MAX_NAME = 100;
  static final int MAX_DESCRIPTION = 500;
  private static final Set<String> UPDATE_FIELDS = Set.of("name", "description", "active", "rowVersion");

  private final ProjectModuleRepository modules;
  private final AuditService audit;
  private final TransactionTemplate tx;
  private final Clock clock;

  public ProjectModuleService(ProjectModuleRepository modules, AuditService audit, PlatformTransactionManager txManager,
      Clock clock) {
    this.modules = modules;
    this.audit = audit;
    this.tx = new TransactionTemplate(txManager);
    this.clock = clock;
  }

  // ── 보기 ──────────────────────────────────────────────────────────

  /** 이 프로젝트의 모듈 전부(쓰지 않는 것 포함) — 순서대로, 붙은 일감 수와 함께 */
  @Transactional(readOnly = true)
  public List<ProjectViews.Module> list(ProjectContext ctx) {
    Map<UUID, Long> counts = new HashMap<>();
    modules.issueCounts(ctx.projectId()).forEach(row -> counts.put((UUID) row[0], ((Number) row[1]).longValue()));
    return modules.findOrdered(ctx.projectId()).stream().map(m -> view(m, counts.getOrDefault(m.getId(), 0L))).toList();
  }

  /** 이 프로젝트에서 쓰는 모듈 key — 순서대로(MCP 결과의 checks 가 관련 모듈이 빠졌는지 알릴 때 — 일감 #182) */
  @Transactional(readOnly = true)
  public List<String> activeKeys(ProjectContext ctx) {
    return modules.findOrdered(ctx.projectId()).stream().filter(ProjectModule::isActive).map(ProjectModule::getKey).toList();
  }

  // ── 관리 (project.manage) ───────────────────────────────────────────

  /** 모듈을 더한다 — 목록 끝에 놓인다 */
  public ProjectViews.Module create(ProjectContext ctx, String key, String name, String description, Actor actor,
      String via) {
    requireManage(ctx);
    String k = key(key);
    String n = name(name);
    String d = description(description);
    ProjectModule saved;
    try {
      saved = tx.execute(s -> {
        if (modules.countIn(ctx.projectId()) >= MAX_MODULES) {
          throw ApiException.conflict("MODULE_LIMIT_REACHED", "한 프로젝트의 모듈은 " + MAX_MODULES + "개까지입니다.");
        }
        if (modules.findByKey(ctx.projectId(), k).isPresent()) {
          throw taken(k);
        }
        Instant now = Instant.now(clock);
        ProjectModule m = modules.saveAndFlush(new ProjectModule(ctx.projectId(), k, n, d,
            modules.maxPosition(ctx.projectId()) + 1, actor.id(), now));
        Map<String, Object> details = details(ctx, k, via);
        details.put("name", n);
        audit.record(actor, ctx.workspaceId(), "project.module.create", "project_module", m.getId().toString(), details);
        return m;
      });
    } catch (DataIntegrityViolationException e) {
      // 같은 key 를 동시에 더했다 — 고유 색인이 막았다
      throw taken(k);
    }
    return view(saved, 0L);
  }

  /**
   * 모듈을 고친다 — 준 것만(name · description · active). description 에 null · 빈 글을 주면 지운다.
   * rowVersion 을 주면 지금 판과 견준다(다르면 409 CONCURRENT_UPDATE). 바뀐 것이 없으면 아무것도 하지 않는다.
   */
  public ProjectViews.Module update(ProjectContext ctx, String key, Map<String, Object> body, Actor actor, String via) {
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
    String name = null;
    if (body.containsKey("name")) {
      name = name(text(body, "name"));
    }
    String description = body.containsKey("description") ? description(text(body, "description")) : null;
    Boolean active = null;
    if (body.containsKey("active")) {
      if (!(body.get("active") instanceof Boolean b)) {
        throw ApiException.badRequest("INVALID_FIELD", "active 는 true · false 여야 합니다.");
      }
      active = b;
    }
    return change(ctx, key, name, body.containsKey("description"), description, active, rowVersion, actor, via);
  }

  /** 순서를 정한다 — keys 는 이 프로젝트의 모듈 key 전부(빠짐 · 겹침 · 모르는 key 없이). 앞에서부터 0, 1, 2 … */
  public List<ProjectViews.Module> reorder(ProjectContext ctx, List<String> keys, Actor actor, String via) {
    requireManage(ctx);
    if (keys == null) {
      throw ApiException.badRequest("INVALID_MODULE_ORDER", "keys 에 모듈 key 를 모두 순서대로 넣으십시오.");
    }
    List<String> wanted = keys.stream().map(k -> k == null ? "" : k.trim().toLowerCase(Locale.ROOT)).toList();
    tx.executeWithoutResult(s -> {
      requireOpen(ctx);
      List<ProjectModule> all = modules.findOrdered(ctx.projectId());
      Set<String> existing = all.stream().map(ProjectModule::getKey).collect(Collectors.toSet());
      if (wanted.size() != all.size() || !new LinkedHashSet<>(wanted).equals(new LinkedHashSet<>(existing))
          || new LinkedHashSet<>(wanted).size() != wanted.size()) {
        throw ApiException.badRequest("INVALID_MODULE_ORDER", "keys 에는 이 프로젝트의 모듈 key 가 모두 한 번씩 있어야 합니다.");
      }
      applyOrder(ctx, all, wanted, actor, via);
    });
    return list(ctx);
  }

  /**
   * 있으면 고치고 없으면 더한다 — MCP {@code set_module} 이 쓴다. 준 것만 바꾼다(name · description · active · position).
   * 새로 더할 때는 name 이 있어야 한다.
   *
   * @param position 목록에서의 자리(0 부터, 끝을 넘으면 끝). 비우면 그대로(새 모듈은 끝)
   * @return 결과 모듈과 새로 만들었는지
   */
  public SetResult set(ProjectContext ctx, String key, String name, String description, Boolean active, Integer position,
      Actor actor, String via) {
    requireManage(ctx);
    String k = key(key);
    boolean created = modules.findByKey(ctx.projectId(), k).isEmpty();
    if (created) {
      create(ctx, k, name, description, actor, via);
      if (Boolean.FALSE.equals(active)) {
        change(ctx, k, null, false, null, false, null, actor, via);
      }
    } else {
      change(ctx, k, name == null ? null : name(name), description != null, description(description), active, null,
          actor, via);
    }
    if (position != null) {
      move(ctx, k, position, actor, via);
    }
    ProjectViews.Module module = list(ctx).stream().filter(m -> m.key().equals(k)).findFirst().orElseThrow();
    return new SetResult(module, created);
  }

  /** set 의 결과 */
  public record SetResult(ProjectViews.Module module, boolean created) {
  }

  // ── 일감에 붙일 모듈 ─────────────────────────────────────────────────

  /**
   * 일감에 붙일 모듈들 — key(대소문자 무시)로 찾고 순서대로 돌려준다. 겹친 key 는 하나로.
   * 이 프로젝트에 없는 key 는 400 {@code UNKNOWN_MODULE}, 쓰지 않는 모듈은 400 {@code MODULE_INACTIVE} —
   * 단 {@code keep} 에 든 것(이미 그 일감에 붙어 있던 것)은 쓰지 않아도 그대로 둘 수 있다.
   */
  @Transactional(readOnly = true)
  public List<ProjectModule> resolve(UUID projectId, Collection<String> keys, Set<UUID> keep) {
    return resolve(projectId, keys, keep, false);
  }

  /** 목록 거르기의 모듈들 — 쓰지 않는 모듈도 된다(옛 일감을 찾는다). 없는 key 만 400 {@code UNKNOWN_MODULE} */
  @Transactional(readOnly = true)
  public List<ProjectModule> resolveForFilter(UUID projectId, Collection<String> keys) {
    return resolve(projectId, keys, Set.of(), true);
  }

  private List<ProjectModule> resolve(UUID projectId, Collection<String> keys, Set<UUID> keep, boolean anyState) {
    if (keys == null || keys.isEmpty()) {
      return List.of();
    }
    Set<String> wanted = new LinkedHashSet<>();
    for (String k : keys) {
      if (k == null || k.isBlank()) {
        throw ApiException.badRequest("UNKNOWN_MODULE", "모듈 key 를 비울 수 없습니다.");
      }
      wanted.add(k.trim().toLowerCase(Locale.ROOT));
    }
    List<ProjectModule> found = modules.findByKeys(projectId, wanted);
    Set<String> foundKeys = found.stream().map(ProjectModule::getKey).collect(Collectors.toSet());
    List<String> unknown = wanted.stream().filter(k -> !foundKeys.contains(k)).toList();
    if (!unknown.isEmpty()) {
      throw ApiException.badRequest("UNKNOWN_MODULE", "이 프로젝트에 없는 모듈입니다: " + String.join(", ", unknown)
          + " (list_modules 로 key 를 확인하십시오)").with("modules", unknown);
    }
    List<String> inactive = found.stream().filter(m -> !anyState && !m.isActive() && !keep.contains(m.getId()))
        .map(ProjectModule::getKey).toList();
    if (!inactive.isEmpty()) {
      throw ApiException.badRequest("MODULE_INACTIVE", "쓰지 않는 모듈은 새로 붙일 수 없습니다: " + String.join(", ", inactive))
          .with("modules", inactive);
    }
    return found.stream().sorted(ORDER).toList();
  }

  /**
   * 여러 프로젝트에 걸친 목록 거르기(워크스페이스 일감 목록) — 그 key 의 모듈을 프로젝트마다 모두. 어느 프로젝트에도 없는 key 만
   * 400 {@code UNKNOWN_MODULE}(쓰지 않는 모듈도 된다)
   */
  @Transactional(readOnly = true)
  public List<ProjectModule> resolveForFilterIn(Collection<UUID> projectIds, Collection<String> keys) {
    if (keys == null || keys.isEmpty() || projectIds.isEmpty()) {
      return List.of();
    }
    Set<String> wanted = new LinkedHashSet<>();
    for (String k : keys) {
      if (k == null || k.isBlank()) {
        throw ApiException.badRequest("UNKNOWN_MODULE", "모듈 key 를 비울 수 없습니다.");
      }
      wanted.add(k.trim().toLowerCase(Locale.ROOT));
    }
    List<ProjectModule> found = modules.findByKeysIn(projectIds, wanted);
    Set<String> foundKeys = found.stream().map(ProjectModule::getKey).collect(Collectors.toSet());
    List<String> unknown = wanted.stream().filter(k -> !foundKeys.contains(k)).toList();
    if (!unknown.isEmpty()) {
      throw ApiException.badRequest("UNKNOWN_MODULE", "고른 프로젝트들에 없는 모듈입니다: " + String.join(", ", unknown))
          .with("modules", unknown);
    }
    return found;
  }

  /** 그 프로젝트의 모듈 행 전부(쓰지 않는 것 포함) — 순서 · key 순 */
  @Transactional(readOnly = true)
  public List<ProjectModule> byProject(UUID projectId) {
    return modules.findOrdered(projectId);
  }

  /** 모듈 행들 — id 로 (이미 붙은 모듈을 보일 때) */
  @Transactional(readOnly = true)
  public Map<UUID, ProjectModule> byIds(Collection<UUID> ids) {
    if (ids.isEmpty()) {
      return Map.of();
    }
    return modules.findAllById(ids).stream().collect(Collectors.toMap(ProjectModule::getId, m -> m));
  }

  /** 모듈의 차례 — 순서 · key */
  public static final Comparator<ProjectModule> ORDER = Comparator.comparingInt(ProjectModule::getPosition)
      .thenComparing(ProjectModule::getKey);

  // ── 안 ────────────────────────────────────────────────────────────

  private ProjectViews.Module change(ProjectContext ctx, String key, String name, boolean descriptionGiven,
      String description, Boolean active, Integer rowVersion, Actor actor, String via) {
    String k = key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
    tx.executeWithoutResult(s -> {
      requireOpen(ctx);
      ProjectModule m = modules.findByKey(ctx.projectId(), k).orElseThrow(() -> notFound(k));
      if (rowVersion != null && m.getRowVersion() != rowVersion) {
        throw ApiException.conflict("CONCURRENT_UPDATE", "다른 곳에서 먼저 고친 모듈입니다. 다시 읽고 고치십시오.")
            .with("rowVersion", m.getRowVersion());
      }
      List<String> fields = new ArrayList<>();
      Map<String, Object> details = details(ctx, k, via);
      if (name != null && !name.equals(m.getName())) {
        details.put("nameBefore", m.getName());
        details.put("name", name);
        m.rename(name);
        fields.add("name");
      }
      if (descriptionGiven && !Objects.equals(description, m.getDescription())) {
        m.describe(description);
        fields.add("description");
      }
      if (active != null && active != m.isActive()) {
        m.activate(active);
        details.put("active", active);
        fields.add("active");
      }
      if (fields.isEmpty()) {
        return;
      }
      m.touch(actor.id(), Instant.now(clock));
      details.put("fields", fields);
      audit.record(actor, ctx.workspaceId(), "project.module.update", "project_module", m.getId().toString(), details);
    });
    return list(ctx).stream().filter(v -> v.key().equals(k)).findFirst().orElseThrow();
  }

  /** 한 모듈을 그 자리(0 부터)로 옮긴다 — 나머지는 차례를 지킨다 */
  private void move(ProjectContext ctx, String key, int position, Actor actor, String via) {
    tx.executeWithoutResult(s -> {
      requireOpen(ctx);
      List<ProjectModule> all = modules.findOrdered(ctx.projectId());
      List<String> keys = new ArrayList<>(all.stream().map(ProjectModule::getKey).toList());
      if (!keys.remove(key)) {
        throw notFound(key);
      }
      keys.add(Math.clamp(position, 0, keys.size()), key);
      applyOrder(ctx, all, keys, actor, via);
    });
  }

  /** 새 차례를 적는다 — 실제로 바뀐 것이 있을 때만 감사 한 줄 */
  private void applyOrder(ProjectContext ctx, List<ProjectModule> all, List<String> keys, Actor actor, String via) {
    List<String> before = all.stream().map(ProjectModule::getKey).toList();
    boolean renumber = false;
    Map<String, ProjectModule> byKey = all.stream().collect(Collectors.toMap(ProjectModule::getKey, m -> m));
    for (int i = 0; i < keys.size(); i++) {
      if (byKey.get(keys.get(i)).getPosition() != i) {
        renumber = true;
      }
    }
    if (!renumber) {
      return;
    }
    Instant now = Instant.now(clock);
    for (int i = 0; i < keys.size(); i++) {
      ProjectModule m = byKey.get(keys.get(i));
      if (m.getPosition() != i) {
        m.moveTo(i);
        m.touch(actor.id(), now);
      }
    }
    if (!before.equals(keys)) {
      Map<String, Object> details = details(ctx, null, via);
      details.put("keys", keys);
      audit.record(actor, ctx.workspaceId(), "project.module.reorder", "project", ctx.projectId().toString(), details);
    }
  }

  private static ProjectViews.Module view(ProjectModule m, long issueCount) {
    return new ProjectViews.Module(m.getKey(), m.getName(), m.getDescription(), m.getPosition(), m.isActive(), issueCount,
        m.getCreatedAt(), m.getUpdatedAt(), m.getRowVersion());
  }

  private static Map<String, Object> details(ProjectContext ctx, String key, String via) {
    Map<String, Object> d = new LinkedHashMap<>();
    d.put("project", ctx.project().getKey());
    if (key != null) {
      d.put("module", key);
    }
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
      throw ApiException.conflict("PROJECT_NOT_WRITABLE", "보관한 프로젝트의 모듈은 바꿀 수 없습니다. 먼저 복원하십시오.");
    }
  }

  private static String key(String value) {
    String k = value == null ? "" : value.trim();
    if (!ProjectModule.KEY_PATTERN.matcher(k).matches()) {
      throw ApiException.badRequest("INVALID_MODULE_KEY",
          "모듈 key 는 영문 소문자로 시작하는 1~32자(소문자 · 숫자 · 하이픈)이고 하이픈으로 끝날 수 없습니다: " + k);
    }
    return k;
  }

  private static String name(String value) {
    String n = value == null ? "" : value.trim();
    if (n.isEmpty() || n.length() > MAX_NAME) {
      throw ApiException.badRequest("INVALID_MODULE_NAME", "모듈 이름은 1~" + MAX_NAME + "자입니다.");
    }
    return n;
  }

  private static String description(String value) {
    String d = value == null ? "" : value.trim();
    if (d.length() > MAX_DESCRIPTION) {
      throw ApiException.badRequest("MODULE_DESCRIPTION_TOO_LONG", "모듈 설명은 " + MAX_DESCRIPTION + "자를 넘을 수 없습니다.");
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

  private static ApiException taken(String key) {
    return ApiException.conflict("MODULE_KEY_TAKEN", "이 프로젝트에 같은 key 의 모듈이 있습니다: " + key);
  }

  private static ApiException notFound(String key) {
    return ApiException.notFound("MODULE_NOT_FOUND", "모듈을 찾을 수 없습니다: " + key);
  }
}
