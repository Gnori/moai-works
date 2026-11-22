package com.moai.works.api.project;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.moai.works.api.config.EndPointPaths;
import com.moai.works.api.security.access.CurrentUser;
import com.moai.works.api.security.access.ProjectApi;
import com.moai.works.api.security.access.ProjectContext;
import com.moai.works.api.workspace.Permission;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 프로젝트 역할 — 누가 어떤 역할을 받았나 보기(독자), 주고 빼기(project.manage). design/04 */
@RestController
@RequestMapping(EndPointPaths.V1 + "/workspaces/{workspace}/projects/{project}/roles")
public class ProjectRoleController {

  private final ProjectRoleService roles;

  public ProjectRoleController(ProjectRoleService roles) {
    this.roles = roles;
  }

  @GetMapping
  @ProjectApi(Permission.ISSUE_READ)
  public List<ProjectRoleService.Holder> list(ProjectContext ctx) {
    return roles.list(ctx);
  }

  /** @param roles 원하는 프로젝트 역할 전부 — 빈 목록이면 모두 뺀다 */
  public record SetRolesRequest(@NotNull @Size(max = 4) Set<String> roles) {
  }

  public record SetRolesResponse(UUID userId, List<String> roles) {
  }

  @PutMapping("/{userId}")
  @ProjectApi(Permission.PROJECT_MANAGE)
  public SetRolesResponse set(ProjectContext ctx, CurrentUser user, @PathVariable UUID userId,
      @Valid @RequestBody SetRolesRequest r) {
    return new SetRolesResponse(userId, roles.setRoles(ctx, userId, r.roles(), user.actor()));
  }
}
