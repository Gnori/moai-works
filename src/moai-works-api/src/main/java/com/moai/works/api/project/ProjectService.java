package com.moai.works.api.project;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.moai.works.api.audit.AuditService;
import com.moai.works.api.common.Actor;
import com.moai.works.api.common.ApiException;
import com.moai.works.api.security.access.ProjectContext;
import com.moai.works.api.security.access.WorkspaceContext;
import com.moai.works.api.workspace.Permission;

/**
 * 프로젝트 만들기 · 설정 · 보관 · 복원 · 삭제 (moai-wiki 의 WikiService 와 같다). 프로젝트는 api DB 가 정본이다(Keycloak 에는
 * 프로젝트 역할의 그룹만 둔다 — design/04). 누가 부를 수 있는지는 컨트롤러의 접근 어노테이션에서 이미 판정이 끝났다.
 * 여기서는 상태 규칙만 본다.
 */
@Service
public class ProjectService {

  /** 개인 워크스페이스에 처음 만들어 두는 프로젝트 */
  static final String DEFAULT_KEY = "my";
  static final String DEFAULT_NAME = "내 프로젝트";
  /** 화면 주소와 부딪히는 이름 */
  private static final Set<String> RESERVED_KEYS = Set.of("new", "archive", "archived", "settings");

  private final ProjectRepository projects;
  private final ProjectAccessService access;
  private final AuditService audit;
  private final TransactionTemplate tx;
  private final Clock clock;

  public ProjectService(ProjectRepository projects, ProjectAccessService access, AuditService audit,
      PlatformTransactionManager txManager, Clock clock) {
    this.projects = projects;
    this.access = access;
    this.audit = audit;
    this.tx = new TransactionTemplate(txManager);
    this.clock = clock;
  }

  /**
   * 내가 볼 수 있는 프로젝트 목록 (이름순).
   *
   * @param includeArchived 보관한 프로젝트도 — 프로젝트를 관리할 수 있는 사람에게만 돌려준다
   */
  @Transactional(readOnly = true)
  public List<ProjectViews.Summary> list(WorkspaceContext workspace, boolean includeArchived) {
    return projects.findLiveByWorkspace(workspace.workspaceId()).stream()
        .filter(p -> includeArchived || p.isOpenToReaders())
        .map(p -> access.contextFor(workspace, p).map(c -> ProjectViews.Summary.of(p, myActions(c))))
        .flatMap(java.util.Optional::stream).toList();
  }

  public ProjectViews.Detail create(WorkspaceContext workspace, String key, String name, String description,
      Project.Visibility visibility, Actor actor) {
    String projectKey = key.trim();
    if (!Project.KEY_PATTERN.matcher(projectKey).matches()) {
      throw ApiException.badRequest("INVALID_PROJECT_KEY",
          "주소 이름은 영문 소문자로 시작하는 2~32자(소문자 · 숫자 · 하이픈)이고 하이픈으로 끝날 수 없습니다.");
    }
    if (RESERVED_KEYS.contains(projectKey)) {
      throw ApiException.badRequest("RESERVED_PROJECT_KEY", "쓸 수 없는 주소 이름입니다: " + projectKey);
    }
    if (projects.findLive(workspace.workspaceId(), projectKey).isPresent()) {
      throw ApiException.conflict("PROJECT_KEY_TAKEN", "이 워크스페이스에 같은 주소 이름의 프로젝트가 있습니다: " + projectKey);
    }
    Project saved;
    try {
      saved = tx.execute(s -> {
        Project project = projects.saveAndFlush(new Project(workspace.workspaceId(), projectKey, name.trim(),
            blankToNull(description), visibility, actor.id(), Instant.now(clock)));
        audit.record(actor, workspace.workspaceId(), "project.create", "project", project.getId().toString(),
            Map.of("key", projectKey, "name", project.getName(), "visibility", visibility.name()));
        return project;
      });
    } catch (DataIntegrityViolationException e) {
      // 두 사람이 같은 주소 이름으로 동시에 만든 경우 — DB 의 고유 인덱스가 하나만 통과시킨다
      throw ApiException.conflict("PROJECT_KEY_TAKEN", "이 워크스페이스에 같은 주소 이름의 프로젝트가 있습니다: " + projectKey);
    }
    return detail(access.contextFor(workspace, saved).orElseThrow());
  }

  /** 개인 워크스페이스를 만들 때 한 번 — 프로젝트가 하나도 없었으면 "내 프로젝트"를 만든다. */
  public void ensureDefault(UUID workspaceId, UUID ownerId, Actor actor) {
    tx.executeWithoutResult(s -> {
      if (projects.countEver(workspaceId) > 0) {
        return;
      }
      Project project = projects.saveAndFlush(new Project(workspaceId, DEFAULT_KEY, DEFAULT_NAME, null,
          Project.Visibility.WORKSPACE, ownerId, Instant.now(clock)));
      audit.record(actor, workspaceId, "project.create", "project", project.getId().toString(),
          Map.of("key", DEFAULT_KEY, "name", DEFAULT_NAME, "visibility", "WORKSPACE", "default", true));
    });
  }

  @Transactional(readOnly = true)
  public ProjectViews.Detail detail(ProjectContext context) {
    Project p = context.project();
    List<String> roles = access.rolesOf(p, context.workspace()).stream().map(ProjectRole::key).sorted().toList();
    return new ProjectViews.Detail(p.getId(), p.getKey(), p.getName(), p.getDescription(), p.getVisibility(),
        p.getStatus(), p.getCreatedAt(), p.getUpdatedAt(), p.getArchivedAt(), projects.countLiveIssues(p.getId()), roles,
        context.permissions().stream().map(Permission::code).sorted().toList(), myActions(context));
  }

  /** 일감을 만들 수 있는가 — 프로젝트가 열려 있고 issue.create 가 있어야 한다 */
  private static List<String> myActions(ProjectContext context) {
    boolean createIssue = context.project().isOpenToReaders() && context.has(Permission.ISSUE_CREATE);
    return createIssue ? List.of("createIssue") : List.of();
  }

  public ProjectViews.Detail update(ProjectContext context, String name, String description,
      Project.Visibility visibility, Actor actor) {
    Project updated = change(context, "project.update", actor, (project, details) -> {
      details.put("before", Map.of("name", project.getName(), "visibility", project.getVisibility().name()));
      project.updateSettings(name.trim(), blankToNull(description), visibility, Instant.now(clock));
      details.put("after", Map.of("name", project.getName(), "visibility", visibility.name()));
    });
    // 지정 멤버 프로젝트로 바꾸면 고친 사람도 독자가 아닐 수 있다 — 그때는 고친 결과를 돌려주되 다음 요청부터 404 다
    return detail(new ProjectContext(context.workspace(), updated, context.permissions()));
  }

  public ProjectViews.Detail archive(ProjectContext context, String reason, Actor actor) {
    if (context.project().getStatus() == Project.Status.ARCHIVED) {
      throw ApiException.conflict("PROJECT_ALREADY_ARCHIVED", "이미 보관한 프로젝트입니다.");
    }
    Project updated = change(context, "project.archive", actor, (project, details) -> {
      project.archive(Instant.now(clock));
      details.put("reason", reason == null ? "" : reason.trim());
    });
    return detail(new ProjectContext(context.workspace(), updated, context.permissions()));
  }

  public ProjectViews.Detail restore(ProjectContext context, Actor actor) {
    if (context.project().getStatus() != Project.Status.ARCHIVED) {
      throw ApiException.conflict("PROJECT_NOT_ARCHIVED", "보관한 프로젝트만 복원할 수 있습니다.");
    }
    Project updated = change(context, "project.restore", actor,
        (project, details) -> project.restore(Instant.now(clock)));
    return detail(new ProjectContext(context.workspace(), updated, context.permissions()));
  }

  /**
   * 지운다 — 보관한 프로젝트만, 주소 이름을 다시 입력해 확인한다(이중 확인).
   * 행은 바로 지우지 않고 지움 상태로 둔다. 보존 기간 뒤의 영구 삭제는 따로 한다.
   */
  public void delete(ProjectContext context, String confirmKey, Actor actor) {
    if (context.project().getStatus() != Project.Status.ARCHIVED) {
      throw ApiException.conflict("PROJECT_NOT_ARCHIVED", "보관한 프로젝트만 지울 수 있습니다. 먼저 보관하십시오.");
    }
    if (!context.project().getKey().equals(confirmKey)) {
      throw ApiException.badRequest("CONFIRM_MISMATCH", "확인용 주소 이름이 맞지 않습니다.");
    }
    change(context, "project.delete", actor, (project, details) -> project.delete(Instant.now(clock)));
  }

  private interface Change {
    void apply(Project project, Map<String, Object> auditDetails);
  }

  private Project change(ProjectContext context, String action, Actor actor, Change change) {
    return tx.execute(s -> {
      Project project = projects.findById(context.projectId()).orElseThrow();
      Map<String, Object> details = new LinkedHashMap<>();
      details.put("key", project.getKey());
      change.apply(project, details);
      audit.record(actor, project.getWorkspaceId(), action, "project", project.getId().toString(), details);
      return project;
    });
  }

  private static String blankToNull(String s) {
    return s == null || s.isBlank() ? null : s.trim();
  }
}
