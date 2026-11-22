package com.moai.works.api.project;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.moai.works.api.config.EndPointPaths;
import com.moai.works.api.security.access.CurrentUser;
import com.moai.works.api.security.access.ProjectApi;
import com.moai.works.api.security.access.ProjectContext;
import com.moai.works.api.security.access.WorkspaceApi;
import com.moai.works.api.security.access.WorkspaceContext;
import com.moai.works.api.workspace.Permission;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 프로젝트 — 목록 · 만들기는 워크스페이스 권한으로, 프로젝트 하나를 다루는 일은 {@link ProjectApi} 로 판정한다.
 * 독자가 아니면 404(존재를 숨김), 독자인데 권한이 없으면 403. moai-wiki 의 WikiController 와 같다.
 */
@RestController
@RequestMapping(EndPointPaths.V1 + "/workspaces/{workspace}/projects")
public class ProjectController {

  private final ProjectService projects;

  public ProjectController(ProjectService projects) {
    this.projects = projects;
  }

  /** 내가 볼 수 있는 프로젝트. archived=true 면 보관한 것도(관리할 수 있는 사람에게만 실제로 나온다). */
  @GetMapping
  @WorkspaceApi(Permission.WORKSPACE_READ)
  public List<ProjectViews.Summary> list(WorkspaceContext ctx, @RequestParam(defaultValue = "false") boolean archived) {
    return projects.list(ctx, archived);
  }

  /** @param key 주소 이름 — 만든 뒤에는 바꿀 수 없다 */
  public record CreateRequest(@NotBlank @Size(min = 2, max = 32) String key, @NotBlank @Size(max = 100) String name,
      @Size(max = 1000) String description, @NotNull Project.Visibility visibility) {
  }

  @PostMapping
  @WorkspaceApi(Permission.PROJECT_CREATE)
  @ResponseStatus(HttpStatus.CREATED)
  public ProjectViews.Detail create(WorkspaceContext ctx, CurrentUser user, @Valid @RequestBody CreateRequest r) {
    return projects.create(ctx, r.key(), r.name(), r.description(), r.visibility(), user.actor());
  }

  @GetMapping("/{project}")
  @ProjectApi(Permission.ISSUE_READ)
  public ProjectViews.Detail detail(ProjectContext ctx) {
    return projects.detail(ctx);
  }

  public record SettingsRequest(@NotBlank @Size(max = 100) String name, @Size(max = 1000) String description,
      @NotNull Project.Visibility visibility) {
  }

  @PatchMapping("/{project}")
  @ProjectApi(Permission.PROJECT_MANAGE)
  public ProjectViews.Detail update(ProjectContext ctx, CurrentUser user, @Valid @RequestBody SettingsRequest r) {
    return projects.update(ctx, r.name(), r.description(), r.visibility(), user.actor());
  }

  public record ArchiveRequest(@Size(max = 500) String reason) {
  }

  /** 보관 — 독자에게 보이지 않게 한다. 되돌릴 수 있다 */
  @PostMapping("/{project}/archive")
  @ProjectApi(Permission.PROJECT_DELETE)
  public ProjectViews.Detail archive(ProjectContext ctx, CurrentUser user,
      @Valid @RequestBody(required = false) ArchiveRequest r) {
    return projects.archive(ctx, r == null ? null : r.reason(), user.actor());
  }

  @PostMapping("/{project}/restore")
  @ProjectApi(Permission.PROJECT_DELETE)
  public ProjectViews.Detail restore(ProjectContext ctx, CurrentUser user) {
    return projects.restore(ctx, user.actor());
  }

  /** 삭제 — 보관한 프로젝트만. confirm 에 주소 이름을 다시 넣어야 한다(이중 확인) */
  @DeleteMapping("/{project}")
  @ProjectApi(Permission.PROJECT_DELETE)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(ProjectContext ctx, CurrentUser user, @RequestParam String confirm) {
    projects.delete(ctx, confirm, user.actor());
  }
}
