package com.moai.works.api.issue;

import java.time.LocalDate;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.moai.works.api.config.EndPointPaths;
import com.moai.works.api.security.access.WorkspaceApi;
import com.moai.works.api.security.access.WorkspaceContext;
import com.moai.works.api.workspace.Permission;

/**
 * 워크스페이스의 일감 — 읽을 수 있는 프로젝트 모두에 걸친 목록 · 찾기(Redmine 의 {@code /issues}, ADR-030).
 * 멤버면 부를 수 있고(workspace.read), 어느 프로젝트가 들어가는지는 프로젝트마다의 판정(독자 ∧ issue.read)으로 서비스가 정한다 —
 * 읽을 수 없는 프로젝트의 일감은 목록에도 전체 수에도 들어가지 않는다.
 */
@RestController
@RequestMapping(EndPointPaths.V1 + "/workspaces/{workspace}")
public class WorkspaceIssueController {

  private final IssueService issues;

  public WorkspaceIssueController(IssueService issues) {
    this.issues = issues;
  }

  /**
   * 워크스페이스 일감 목록 — 조건은 프로젝트 목록과 같고({@link IssueListParams}), project(주소 이름, 여러 번)로 프로젝트를 좁힌다.
   * 읽을 수 없거나 없는 project 는 빠진다(주소에 남은 옛 조건이 깨지지 않게). 응답의 일감마다 project {key, name}.
   */
  @GetMapping("/issues")
  @WorkspaceApi(Permission.WORKSPACE_READ)
  public IssueViews.Page list(WorkspaceContext ctx, @RequestParam(required = false) List<String> project,
      @RequestParam(required = false) List<String> status,
      @RequestParam(required = false) List<String> type, @RequestParam(required = false) String manager,
      @RequestParam(required = false) String assignee, @RequestParam(required = false) String parent,
      @RequestParam(required = false) String q, @RequestParam(required = false) LocalDate dueFrom,
      @RequestParam(required = false) LocalDate dueTo, @RequestParam(defaultValue = "false") boolean overdue,
      @RequestParam(required = false) String agentSession, @RequestParam(required = false) List<String> module,
      @RequestParam(required = false) String sort,
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
    return issues.listWorkspace(ctx, project, false, IssueListParams.query(ctx.userId(), status, type, manager, assignee,
        parent, q, dueFrom, dueTo, overdue, agentSession, module, sort, page, size));
  }

  /** 일감 유형 — 워크스페이스 공통(목록 거르기용) */
  @GetMapping("/issue-types")
  @WorkspaceApi(Permission.WORKSPACE_READ)
  public List<IssueViews.Type> types(WorkspaceContext ctx) {
    return issues.types();
  }

  /** 읽을 수 있는 프로젝트의 일감에 쓰인 에이전트 세션 이름들 — 이름순, 일감 수와 함께 */
  @GetMapping("/agent-sessions")
  @WorkspaceApi(Permission.WORKSPACE_READ)
  public List<IssueViews.AgentSession> agentSessions(WorkspaceContext ctx) {
    return issues.agentSessions(ctx);
  }

  /** 읽을 수 있는 프로젝트들의 모듈 key — 같은 key 는 하나로(목록 거르기용) */
  @GetMapping("/issue-modules")
  @WorkspaceApi(Permission.WORKSPACE_READ)
  public List<IssueViews.ModuleOption> modules(WorkspaceContext ctx) {
    return issues.moduleOptions(ctx);
  }

  /**
   * 옛 링크 풀기 — {@code /w/…/projects/<key>/issues/<번호>} 의 번호는 V23 전의 프로젝트 안 번호다. 그 일감의 지금 번호를 준다
   * (화면이 새 주소 {@code /w/…/issues/<번호>} 로 돌린다). 없거나 지금 읽을 수 없으면 404 ISSUE_NOT_FOUND.
   */
  @GetMapping("/projects/{projectKey}/issue-refs/{number}")
  @WorkspaceApi(Permission.WORKSPACE_READ)
  public IssueViews.IssueNumber legacy(WorkspaceContext ctx, @PathVariable String projectKey, @PathVariable int number) {
    return issues.legacyNumber(ctx, projectKey, number);
  }
}
