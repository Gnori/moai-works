package com.moai.works.api.project;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.moai.works.api.config.EndPointPaths;
import com.moai.works.api.security.access.CurrentUser;
import com.moai.works.api.security.access.ProjectApi;
import com.moai.works.api.security.access.ProjectContext;
import com.moai.works.api.workspace.Permission;

/**
 * 프로젝트 모듈 (2026-11-23, 일감 #27) — 보기는 독자 누구나, 더하기 · 고치기 · 순서는 {@code project.manage}.
 * 지우기는 없다(쓰지 않음으로 둔다). 판정은 {@link ProjectApi} 와 서비스가 한다(MCP 도 같은 서비스).
 */
@RestController
@RequestMapping(EndPointPaths.V1 + "/workspaces/{workspace}/projects/{project}/modules")
public class ProjectModuleController {

  private final ProjectModuleService modules;

  public ProjectModuleController(ProjectModuleService modules) {
    this.modules = modules;
  }

  /** 모듈 전부(쓰지 않는 것 포함) — 순서대로, 붙은 일감 수와 함께 */
  @GetMapping
  @ProjectApi(Permission.ISSUE_READ)
  public List<ProjectViews.Module> list(ProjectContext ctx) {
    return modules.list(ctx);
  }

  /** @param key 영문 소문자로 시작하는 1~32자(소문자 · 숫자 · 하이픈) — 만든 뒤에는 바꿀 수 없다 */
  public record CreateRequest(String key, String name, String description) {
  }

  @PostMapping
  @ProjectApi(Permission.PROJECT_MANAGE)
  @ResponseStatus(HttpStatus.CREATED)
  public ProjectViews.Module create(ProjectContext ctx, CurrentUser user, @RequestBody CreateRequest r) {
    return modules.create(ctx, r.key(), r.name(), r.description(), user.actor(), "WEB");
  }

  /** 고치기 — 준 것만(name · description · active, rowVersion 은 선택). active=false 면 쓰지 않음 */
  @PatchMapping("/{key}")
  @ProjectApi(Permission.PROJECT_MANAGE)
  public ProjectViews.Module update(ProjectContext ctx, CurrentUser user, @PathVariable String key,
      @RequestBody Map<String, Object> body) {
    return modules.update(ctx, key, body, user.actor(), "WEB");
  }

  /** @param keys 이 프로젝트의 모듈 key 전부를 새 순서대로 */
  public record OrderRequest(List<String> keys) {
  }

  /** 순서 — 결과는 새 순서의 모듈 전부 */
  @PutMapping("/order")
  @ProjectApi(Permission.PROJECT_MANAGE)
  public List<ProjectViews.Module> reorder(ProjectContext ctx, CurrentUser user, @RequestBody OrderRequest r) {
    return modules.reorder(ctx, r.keys(), user.actor(), "WEB");
  }
}
