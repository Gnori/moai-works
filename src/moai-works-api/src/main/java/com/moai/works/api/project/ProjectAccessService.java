package com.moai.works.api.project;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.moai.works.api.security.access.ProjectContext;
import com.moai.works.api.security.access.WorkspaceContext;
import com.moai.works.api.workspace.BuiltinRole;
import com.moai.works.api.workspace.Permission;

/**
 * 프로젝트 접근 판정 — 판정은 이 한 곳에서만 한다 (design/04, moai-wiki 의 WikiAccessService 와 같은 식).
 *
 * <pre>
 * 독자(사람, 프로젝트)       = 워크스페이스 활성 멤버
 *                            ∧ (프로젝트.공개 범위 = WORKSPACE ∨ 소유자 · 관리자 ∨ 그 프로젝트의 프로젝트 역할을 가짐)
 * 프로젝트 권한(사람, 프로젝트) = 워크스페이스 역할의 권한 ∪ 프로젝트 역할의 권한
 * 보이는가(사람, 프로젝트)    = 독자 ∧ (프로젝트가 열려 있음 ∨ 프로젝트를 관리할 수 있음)
 * </pre>
 */
@Service
public class ProjectAccessService {

  private final ProjectRepository projects;
  private final ProjectRoleBindingRepository bindings;

  public ProjectAccessService(ProjectRepository projects, ProjectRoleBindingRepository bindings) {
    this.projects = projects;
    this.bindings = bindings;
  }

  /** 볼 수 있으면 문맥, 아니면 empty (호출자는 404 로 숨긴다). */
  @Transactional(readOnly = true)
  public Optional<ProjectContext> contextFor(WorkspaceContext workspace, String key) {
    return projects.findLive(workspace.workspaceId(), key).flatMap(project -> contextFor(workspace, project));
  }

  @Transactional(readOnly = true)
  public Optional<ProjectContext> contextFor(WorkspaceContext workspace, Project project) {
    if (!project.getWorkspaceId().equals(workspace.workspaceId()) || project.getStatus() == Project.Status.DELETED) {
      return Optional.empty();
    }
    Set<ProjectRole> roles = rolesOf(project, workspace);
    if (!isReader(workspace, project, roles)) {
      return Optional.empty();
    }
    ProjectContext context = new ProjectContext(workspace, project, permissionsIn(workspace, roles));
    return project.isOpenToReaders() || canManage(context) ? Optional.of(context) : Optional.empty();
  }

  /**
   * 이 사람이 그 프로젝트에서 가진 프로젝트 역할. 워크스페이스 소유자는 모든 프로젝트의 프로젝트 관리자다 — 받은 역할이 없어도
   * 늘 들어 있다(2026-11-22 사용자: "오너는 프로젝트에 무조건 멤버로 추가해").
   */
  public Set<ProjectRole> rolesOf(Project project, WorkspaceContext workspace) {
    List<ProjectRoleBinding> mine = bindings.findByProjectIdAndUserId(project.getId(), workspace.userId());
    Set<ProjectRole> roles = EnumSet.noneOf(ProjectRole.class);
    mine.forEach(b -> ProjectRole.ofKey(b.getRole()).ifPresent(roles::add));
    if (workspace.roleKeys().contains(BuiltinRole.OWNER.key())) {
      roles.add(ProjectRole.ADMIN);
    }
    return roles;
  }

  /** 이 사람이 그 프로젝트의 독자인가 */
  public static boolean isReader(WorkspaceContext workspace, Project project, Set<ProjectRole> roles) {
    return project.getVisibility() == Project.Visibility.WORKSPACE
        || workspace.roleKeys().contains(BuiltinRole.OWNER.key())
        || workspace.roleKeys().contains(BuiltinRole.ADMIN.key())
        || !roles.isEmpty();
  }

  /** 이 프로젝트에서 가진 권한 = 워크스페이스 역할의 권한 ∪ 프로젝트 역할의 권한 */
  public static Set<Permission> permissionsIn(WorkspaceContext workspace, Set<ProjectRole> roles) {
    Set<Permission> all = EnumSet.noneOf(Permission.class);
    all.addAll(workspace.permissions());
    roles.forEach(r -> all.addAll(r.permissions()));
    return all;
  }

  /** 보관된 프로젝트를 볼 수 있는 사람 — 설정을 고치거나 보관 · 복원 · 삭제할 수 있는 사람 */
  public static boolean canManage(ProjectContext context) {
    return context.has(Permission.PROJECT_MANAGE) || context.has(Permission.PROJECT_DELETE);
  }
}
