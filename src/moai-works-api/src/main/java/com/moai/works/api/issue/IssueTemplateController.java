package com.moai.works.api.issue;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.moai.works.api.config.EndPointPaths;
import com.moai.works.api.security.access.CurrentUser;
import com.moai.works.api.security.access.ProjectApi;
import com.moai.works.api.security.access.ProjectContext;
import com.moai.works.api.security.access.WorkspaceApi;
import com.moai.works.api.security.access.WorkspaceContext;
import com.moai.works.api.workspace.Permission;

import jakarta.validation.constraints.NotNull;

/**
 * 유형별 일감 템플릿 REST — 워크스페이스 기본 · 프로젝트 덮어쓰기 · 되돌리기 ({@link IssueTemplateService}).
 * MCP 의 같은 일(프로젝트 관리 입구의 템플릿 도구)도 같은 서비스를 부른다.
 */
@RestController
public class IssueTemplateController {

  private final IssueTemplateService templates;

  public IssueTemplateController(IssueTemplateService templates) {
    this.templates = templates;
  }

  /** @param rowVersion 이미 정한 템플릿을 고칠 때 읽은 값(그 사이 바뀌었으면 409). 처음 정할 때는 비운다 */
  public record TemplateRequest(@NotNull String body, Integer rowVersion) {
  }

  // ── 워크스페이스 기본 ───────────────────────────────────────────────

  @GetMapping(EndPointPaths.V1 + "/workspaces/{workspace}/issue-templates")
  @WorkspaceApi(Permission.WORKSPACE_READ)
  public List<IssueTemplateService.TemplateView> workspaceTemplates(WorkspaceContext ws) {
    return templates.forWorkspace(ws);
  }

  @PutMapping(EndPointPaths.V1 + "/workspaces/{workspace}/issue-templates/{type}")
  @WorkspaceApi(Permission.WORKSPACE_SETTINGS_MANAGE)
  public IssueTemplateService.TemplateView setWorkspaceTemplate(WorkspaceContext ws, CurrentUser user,
      @PathVariable String type, @RequestBody TemplateRequest r) {
    return templates.setWorkspace(ws, type, r == null ? null : r.body(), r == null ? null : r.rowVersion(), user.actor(),
        "WEB");
  }

  @DeleteMapping(EndPointPaths.V1 + "/workspaces/{workspace}/issue-templates/{type}")
  @WorkspaceApi(Permission.WORKSPACE_SETTINGS_MANAGE)
  public IssueTemplateService.TemplateView resetWorkspaceTemplate(WorkspaceContext ws, CurrentUser user,
      @PathVariable String type) {
    return templates.resetWorkspace(ws, type, user.actor(), "WEB");
  }

  // ── 프로젝트 ──────────────────────────────────────────────────────

  /** 이 프로젝트에서 실제로 쓰이는 템플릿(유형마다) — 출처(PROJECT · WORKSPACE · BUILTIN)와 함께 */
  @GetMapping(EndPointPaths.V1 + "/workspaces/{workspace}/projects/{project}/issue-templates")
  @ProjectApi(Permission.ISSUE_READ)
  public List<IssueTemplateService.TemplateView> projectTemplates(ProjectContext ctx) {
    return templates.forProject(ctx);
  }

  @GetMapping(EndPointPaths.V1 + "/workspaces/{workspace}/projects/{project}/issue-templates/{type}")
  @ProjectApi(Permission.ISSUE_READ)
  public IssueTemplateService.TemplateView projectTemplate(ProjectContext ctx, @PathVariable String type) {
    return templates.effective(ctx, type);
  }

  @PutMapping(EndPointPaths.V1 + "/workspaces/{workspace}/projects/{project}/issue-templates/{type}")
  @ProjectApi(Permission.PROJECT_MANAGE)
  public IssueTemplateService.TemplateView setProjectTemplate(ProjectContext ctx, CurrentUser user,
      @PathVariable String type, @RequestBody TemplateRequest r) {
    return templates.setProject(ctx, type, r == null ? null : r.body(), r == null ? null : r.rowVersion(), user.actor(),
        "WEB");
  }

  @DeleteMapping(EndPointPaths.V1 + "/workspaces/{workspace}/projects/{project}/issue-templates/{type}")
  @ProjectApi(Permission.PROJECT_MANAGE)
  public IssueTemplateService.TemplateView resetProjectTemplate(ProjectContext ctx, CurrentUser user,
      @PathVariable String type) {
    return templates.resetProject(ctx, type, user.actor(), "WEB");
  }
}
