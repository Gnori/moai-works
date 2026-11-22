package com.moai.works.api.issue;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.moai.works.api.account.UserProfile;
import com.moai.works.api.account.UserProfileRepository;
import com.moai.works.api.audit.AuditService;
import com.moai.works.api.common.Actor;
import com.moai.works.api.common.ApiException;
import com.moai.works.api.security.access.ProjectContext;
import com.moai.works.api.security.access.WorkspaceContext;
import com.moai.works.api.workspace.Permission;

/**
 * 유형별 일감 템플릿 (2026-11-23 사용자: "이슈의 유형별로 작성 템플릿 넣어 주고 그걸 관리할 수 있게 해 줘. 또한 mcp 제공해서
 * 그에 맞게 이슈 작성 및 수정할 수 있게").
 *
 * <ul>
 *   <li>고르는 순서: 프로젝트 덮어쓰기 → 워크스페이스 기본 → 코드의 처음 템플릿({@link #BUILTIN}). 행을 지우면 한 단계 위로 돌아간다</li>
 *   <li>워크스페이스 기본은 {@code workspace.settings.manage}, 프로젝트 덮어쓰기는 {@code project.manage} 로 고친다. 고치기 · 되돌리기는 감사에 남는다</li>
 *   <li>템플릿의 제목(#)이 항목이다. 제목 끝에 "(선택)" 이 붙은 항목은 비워도 된다. 일감 설명에 빠진 필수 항목은 <b>알려만 준다</b>(막지 않는다 — 사용자 결정)</li>
 *   <li>지금 쓰는 유형(active)만 템플릿을 둔다</li>
 * </ul>
 */
@Service
public class IssueTemplateService {

  public static final int MAX_BODY = 30_000;

  /** 템플릿을 어디서 가져왔나 */
  public enum Source {
    BUILTIN, WORKSPACE, PROJECT
  }

  /** 템플릿 항목 — 제목과 비워도 되는가 */
  public record Section(String title, boolean optional) {
  }

  /**
   * 템플릿 하나.
   *
   * @param source     실제로 쓰이는 것의 출처
   * @param inherited  프로젝트에서 볼 때 위(워크스페이스 · 처음 템플릿)에서 물려받은 것인가
   * @param rowVersion 고칠 때 넣는 값 — 이 단계에 행이 없으면 null
   */
  public record TemplateView(String type, String typeName, String body, Source source, boolean inherited,
      List<Section> sections, IssueViews.Person updatedBy, Instant updatedAt, Integer rowVersion) {
  }

  /** 일감 설명이 템플릿을 따랐나 — missing 은 빠진 필수 항목 제목 */
  public record Check(String type, Source source, List<String> missing) {
  }

  /** 처음 템플릿 — 워크스페이스가 아직 정하지 않았을 때 쓴다 */
  static final Map<String, String> BUILTIN = Map.of(
      "feature", """
          ## 배경
          왜 필요한가 — 요청한 사람과 요청 원문

          ## 하려는 것
          무엇을 만들거나 바꾸는가

          ## 수용 기준
          - [ ] 확인할 수 있는 기준

          ## 범위 밖 (선택)
          이번에 하지 않는 것
          """,
      "bug", """
          ## 현상
          무엇이 잘못되는가

          ## 재현 순서
          1.

          ## 기대한 동작

          ## 실제 동작

          ## 환경 (선택)
          화면 · 브라우저 · 계정 · 시각

          ## 수용 기준
          - [ ] 고쳐졌음을 확인하는 방법
          """,
      "test", """
          ## 목적
          무엇을 확인하는가

          ## 범위
          대상 기능 · 화면 · API

          ## 시험 항목
          - [ ]

          ## 결과 (선택)
          """,
      "research", """
          ## 질문
          알아내야 할 것

          ## 배경
          왜 필요한가

          ## 조사할 것
          - [ ]

          ## 결론 (선택)
          결론과 근거 · 다음 할 일
          """);

  private static final Pattern HEADING = Pattern.compile("^ {0,3}#{1,6}[ \\t]+(.*?)[ \\t]*#*[ \\t]*$");
  private static final Pattern FENCE = Pattern.compile("^ {0,3}(```|~~~)");
  private static final Pattern OPTIONAL = Pattern.compile("\\s*\\((선택|optional)\\)\\s*$", Pattern.CASE_INSENSITIVE);

  private final IssueRepositories.Templates templates;
  private final IssueRepositories.Types types;
  private final UserProfileRepository users;
  private final AuditService audit;
  private final Clock clock;

  public IssueTemplateService(IssueRepositories.Templates templates, IssueRepositories.Types types,
      UserProfileRepository users, AuditService audit, Clock clock) {
    this.templates = templates;
    this.types = types;
    this.users = users;
    this.audit = audit;
    this.clock = clock;
  }

  // ── 보기 ──────────────────────────────────────────────────────────

  /** 워크스페이스 기본 템플릿(지금 쓰는 유형마다) — 정하지 않은 유형은 처음 템플릿 */
  @Transactional(readOnly = true)
  public List<TemplateView> forWorkspace(WorkspaceContext ws) {
    Map<String, IssueTemplate> rows = byType(templates.findWorkspaceDefaults(ws.workspaceId()));
    Map<UUID, IssueViews.Person> people = people(rows.values());
    List<TemplateView> out = new ArrayList<>();
    for (IssueType t : activeTypes()) {
      IssueTemplate row = rows.get(t.getKey());
      out.add(row != null ? view(t, row, Source.WORKSPACE, false, people)
          : view(t, BUILTIN.getOrDefault(t.getKey(), ""), Source.BUILTIN, false));
    }
    return out;
  }

  /** 프로젝트에서 실제로 쓰이는 템플릿(지금 쓰는 유형마다) — 프로젝트 → 워크스페이스 → 처음 템플릿 */
  @Transactional(readOnly = true)
  public List<TemplateView> forProject(ProjectContext ctx) {
    Map<String, IssueTemplate> project = byType(templates.findProjectOverrides(ctx.projectId()));
    Map<String, IssueTemplate> workspace = byType(templates.findWorkspaceDefaults(ctx.workspaceId()));
    List<IssueTemplate> all = new ArrayList<>(project.values());
    all.addAll(workspace.values());
    Map<UUID, IssueViews.Person> people = people(all);
    List<TemplateView> out = new ArrayList<>();
    for (IssueType t : activeTypes()) {
      out.add(effective(t, project.get(t.getKey()), workspace.get(t.getKey()), people));
    }
    return out;
  }

  /** 프로젝트에서 그 유형에 쓰이는 템플릿 하나 */
  @Transactional(readOnly = true)
  public TemplateView effective(ProjectContext ctx, String typeKey) {
    IssueType t = activeType(typeKey);
    IssueTemplate project = byType(templates.findProjectOverrides(ctx.projectId())).get(t.getKey());
    IssueTemplate workspace = byType(templates.findWorkspaceDefaults(ctx.workspaceId())).get(t.getKey());
    List<IssueTemplate> rows = new ArrayList<>();
    if (project != null) {
      rows.add(project);
    }
    if (workspace != null) {
      rows.add(workspace);
    }
    return effective(t, project, workspace, people(rows));
  }

  /**
   * 일감 설명이 그 유형의 템플릿을 따랐나 — 빠진 필수 항목만 알려 준다. 템플릿이 없는 유형(옛 유형)이면 빈 목록.
   */
  @Transactional(readOnly = true)
  public Check check(ProjectContext ctx, String typeKey, String description) {
    IssueType t = typeKey == null ? null : types.findById(typeKey).orElse(null);
    if (t == null || !t.isActive()) {
      return new Check(typeKey, null, List.of());
    }
    TemplateView v = effective(ctx, typeKey);
    return new Check(typeKey, v.source(), missing(v.body(), description));
  }

  // ── 고치기 · 되돌리기 ───────────────────────────────────────────────

  /** 워크스페이스 기본 템플릿을 정한다 — {@code workspace.settings.manage} */
  @Transactional
  public TemplateView setWorkspace(WorkspaceContext ws, String typeKey, String body, Integer rowVersion, Actor actor,
      String via) {
    require(ws.has(Permission.WORKSPACE_SETTINGS_MANAGE), Permission.WORKSPACE_SETTINGS_MANAGE);
    IssueType t = activeType(typeKey);
    String clean = cleanBody(body);
    IssueTemplate row = save(byType(templates.findWorkspaceDefaults(ws.workspaceId())).get(t.getKey()),
        () -> new IssueTemplate(ws.workspaceId(), null, t.getKey(), clean, actor.id(), now()), clean, rowVersion, actor);
    audit.record(actor, ws.workspaceId(), "issue_template.update", "issue_template", "workspace:" + t.getKey(),
        details("WORKSPACE", t.getKey(), null, clean.length(), via));
    return view(t, row, Source.WORKSPACE, false, people(List.of(row)));
  }

  /** 워크스페이스 기본을 지운다 — 처음 템플릿으로 돌아간다 */
  @Transactional
  public TemplateView resetWorkspace(WorkspaceContext ws, String typeKey, Actor actor, String via) {
    require(ws.has(Permission.WORKSPACE_SETTINGS_MANAGE), Permission.WORKSPACE_SETTINGS_MANAGE);
    IssueType t = activeType(typeKey);
    IssueTemplate row = byType(templates.findWorkspaceDefaults(ws.workspaceId())).get(t.getKey());
    if (row != null) {
      templates.delete(row);
      audit.record(actor, ws.workspaceId(), "issue_template.reset", "issue_template", "workspace:" + t.getKey(),
          details("WORKSPACE", t.getKey(), null, null, via));
    }
    return view(t, BUILTIN.getOrDefault(t.getKey(), ""), Source.BUILTIN, false);
  }

  /** 프로젝트에서 덮어쓴다 — {@code project.manage} */
  @Transactional
  public TemplateView setProject(ProjectContext ctx, String typeKey, String body, Integer rowVersion, Actor actor,
      String via) {
    require(ctx.has(Permission.PROJECT_MANAGE), Permission.PROJECT_MANAGE);
    IssueType t = activeType(typeKey);
    String clean = cleanBody(body);
    IssueTemplate row = save(byType(templates.findProjectOverrides(ctx.projectId())).get(t.getKey()),
        () -> new IssueTemplate(ctx.workspaceId(), ctx.projectId(), t.getKey(), clean, actor.id(), now()), clean,
        rowVersion, actor);
    audit.record(actor, ctx.workspaceId(), "issue_template.update", "issue_template",
        ctx.project().getKey() + ":" + t.getKey(), details("PROJECT", t.getKey(), ctx.project().getKey(), clean.length(), via));
    return view(t, row, Source.PROJECT, false, people(List.of(row)));
  }

  /** 프로젝트 덮어쓰기를 지운다 — 워크스페이스 기본(없으면 처음 템플릿)으로 돌아간다 */
  @Transactional
  public TemplateView resetProject(ProjectContext ctx, String typeKey, Actor actor, String via) {
    require(ctx.has(Permission.PROJECT_MANAGE), Permission.PROJECT_MANAGE);
    IssueType t = activeType(typeKey);
    IssueTemplate row = byType(templates.findProjectOverrides(ctx.projectId())).get(t.getKey());
    if (row != null) {
      templates.delete(row);
      templates.flush();
      audit.record(actor, ctx.workspaceId(), "issue_template.reset", "issue_template",
          ctx.project().getKey() + ":" + t.getKey(), details("PROJECT", t.getKey(), ctx.project().getKey(), null, via));
    }
    return effective(ctx, t.getKey());
  }

  // ── 항목 읽기 (화면도 같은 규칙: shared/lib 의 templateSections · missingSections) ─────────

  /** 템플릿의 항목 — 코드 블록 밖의 제목(#)마다 하나. 끝에 "(선택)" 이 있으면 비워도 되는 항목 */
  public static List<Section> sections(String body) {
    List<Section> out = new ArrayList<>();
    for (String title : headings(body)) {
      Matcher m = OPTIONAL.matcher(title);
      boolean optional = m.find();
      String plain = optional ? title.substring(0, m.start()).trim() : title.trim();
      if (!plain.isEmpty()) {
        out.add(new Section(plain, optional));
      }
    }
    return out;
  }

  /** 설명에 없는 필수 항목 제목 — 제목 수준(# 개수) · 대소문자 · 공백은 따지지 않는다 */
  public static List<String> missing(String templateBody, String description) {
    Set<String> present = new HashSet<>();
    for (String title : headings(description)) {
      present.add(norm(OPTIONAL.matcher(title).replaceFirst("")));
    }
    List<String> out = new ArrayList<>();
    for (Section s : sections(templateBody)) {
      if (!s.optional() && !present.contains(norm(s.title()))) {
        out.add(s.title());
      }
    }
    return out;
  }

  private static List<String> headings(String text) {
    List<String> out = new ArrayList<>();
    if (text == null || text.isBlank()) {
      return out;
    }
    boolean inFence = false;
    for (String line : text.split("\\R")) {
      if (FENCE.matcher(line).find()) {
        inFence = !inFence;
        continue;
      }
      if (inFence) {
        continue;
      }
      Matcher m = HEADING.matcher(line);
      if (m.matches()) {
        out.add(m.group(1));
      }
    }
    return out;
  }

  private static String norm(String title) {
    return title.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
  }

  // ── 도움 ──────────────────────────────────────────────────────────

  private IssueTemplate save(IssueTemplate existing, java.util.function.Supplier<IssueTemplate> create, String body,
      Integer rowVersion, Actor actor) {
    if (existing == null) {
      return templates.saveAndFlush(create.get());
    }
    if (rowVersion != null && rowVersion != existing.getRowVersion()) {
      throw ApiException.conflict("CONCURRENT_UPDATE", "그 사이 다른 사람이 템플릿을 고쳤습니다. 다시 읽고 고치십시오.");
    }
    existing.change(body, actor.id(), now());
    return templates.saveAndFlush(existing);
  }

  private TemplateView effective(IssueType t, IssueTemplate project, IssueTemplate workspace,
      Map<UUID, IssueViews.Person> people) {
    if (project != null) {
      return view(t, project, Source.PROJECT, false, people);
    }
    if (workspace != null) {
      return view(t, workspace, Source.WORKSPACE, true, people);
    }
    return view(t, BUILTIN.getOrDefault(t.getKey(), ""), Source.BUILTIN, true);
  }

  private static TemplateView view(IssueType t, IssueTemplate row, Source source, boolean inherited,
      Map<UUID, IssueViews.Person> people) {
    return new TemplateView(t.getKey(), t.getName(), row.getBody(), source, inherited, sections(row.getBody()),
        row.getUpdatedBy() == null ? null : people.get(row.getUpdatedBy()), row.getUpdatedAt(),
        source == Source.PROJECT || !inherited ? row.getRowVersion() : null);
  }

  private static TemplateView view(IssueType t, String body, Source source, boolean inherited) {
    return new TemplateView(t.getKey(), t.getName(), body, source, inherited, sections(body), null, null, null);
  }

  private List<IssueType> activeTypes() {
    return types.findAllByOrderByPositionAsc().stream().filter(IssueType::isActive).toList();
  }

  private IssueType activeType(String key) {
    IssueType t = key == null ? null : types.findById(key.trim()).orElse(null);
    if (t == null) {
      throw ApiException.badRequest("UNKNOWN_ISSUE_TYPE", "일감 유형이 없습니다: " + key);
    }
    if (!t.isActive()) {
      throw ApiException.badRequest("ISSUE_TYPE_RETIRED", "더 쓰지 않는 일감 유형에는 템플릿을 두지 않습니다: " + key);
    }
    return t;
  }

  private static String cleanBody(String body) {
    if (body == null) {
      throw ApiException.badRequest("TEMPLATE_BODY_REQUIRED", "템플릿 내용을 넣으십시오(비우려면 빈 글).");
    }
    if (body.length() > MAX_BODY) {
      throw ApiException.badRequest("TEMPLATE_TOO_LONG", "템플릿은 " + MAX_BODY + "자를 넘을 수 없습니다.");
    }
    return body;
  }

  private static void require(boolean ok, Permission permission) {
    if (!ok) {
      throw ApiException.forbidden("PERMISSION_REQUIRED", "권한 " + permission.code() + " 이(가) 필요합니다.");
    }
  }

  private static Map<String, Object> details(String scope, String type, String project, Integer length, String via) {
    Map<String, Object> d = new LinkedHashMap<>();
    d.put("scope", scope);
    d.put("type", type);
    if (project != null) {
      d.put("project", project);
    }
    if (length != null) {
      d.put("length", length);
    }
    d.put("via", via == null ? "WEB" : via);
    return d;
  }

  private static Map<String, IssueTemplate> byType(List<IssueTemplate> rows) {
    return rows.stream().collect(Collectors.toMap(IssueTemplate::getIssueType, Function.identity(), (a, b) -> a));
  }

  private Map<UUID, IssueViews.Person> people(java.util.Collection<IssueTemplate> rows) {
    Set<UUID> ids = rows.stream().map(IssueTemplate::getUpdatedBy).filter(java.util.Objects::nonNull)
        .collect(Collectors.toSet());
    Map<UUID, IssueViews.Person> out = new LinkedHashMap<>();
    for (UserProfile u : users.findAllById(ids)) {
      out.put(u.getId(), new IssueViews.Person(u.getId(), u.getUsername(), u.getDisplayName()));
    }
    return out;
  }

  private Instant now() {
    return Instant.now(clock);
  }
}
