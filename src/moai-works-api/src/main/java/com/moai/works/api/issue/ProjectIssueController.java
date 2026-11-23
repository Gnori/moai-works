package com.moai.works.api.issue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
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
import com.moai.works.api.workspace.Permission;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 프로젝트의 일감 — 그 프로젝트의 목록 · 만들기 · 유형 · 쓰인 에이전트 세션. {@link ProjectApi} 로 판정한다(독자가 아니면 404,
 * 코드가 없으면 403). 일감 하나(번호)를 다루는 일은 워크스페이스 주소의 {@link IssueController} 가 한다(번호는 워크스페이스에서 하나 — ADR-030).
 */
@RestController
@RequestMapping(EndPointPaths.V1 + "/workspaces/{workspace}/projects/{project}")
public class ProjectIssueController {

  private final IssueService issues;

  public ProjectIssueController(IssueService issues) {
    this.issues = issues;
  }

  @GetMapping("/issue-types")
  @ProjectApi(Permission.ISSUE_READ)
  public List<IssueViews.Type> types(ProjectContext ctx) {
    return issues.types();
  }

  /** 이 프로젝트의 일감 목록 — 조건은 {@link IssueListParams}. 응답에 전체 수 · 전체 쪽 수 · 다음 쪽이 있는지(hasNext) */
  @GetMapping("/issues")
  @ProjectApi(Permission.ISSUE_READ)
  public IssueViews.Page list(ProjectContext ctx, @RequestParam(required = false) List<String> status,
      @RequestParam(required = false) List<String> type, @RequestParam(required = false) String manager,
      @RequestParam(required = false) String assignee, @RequestParam(required = false) String parent,
      @RequestParam(required = false) String q, @RequestParam(required = false) LocalDate dueFrom,
      @RequestParam(required = false) LocalDate dueTo, @RequestParam(defaultValue = "false") boolean overdue,
      @RequestParam(required = false) String agentSession, @RequestParam(required = false) List<String> module,
      @RequestParam(required = false) String sort,
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
    return issues.list(ctx, IssueListParams.query(ctx.userId(), status, type, manager, assignee, parent, q, dueFrom, dueTo,
        overdue, agentSession, module, sort, page, size));
  }

  /** 이 프로젝트의 일감에 쓰인 에이전트 세션 이름들 — 이름순, 일감 수와 함께(목록 거르기 고르기용) */
  @GetMapping("/agent-sessions")
  @ProjectApi(Permission.ISSUE_READ)
  public List<IssueViews.AgentSession> agentSessions(ProjectContext ctx) {
    return issues.agentSessions(ctx);
  }

  public record CreateRequest(@NotBlank String type, @NotBlank @Size(max = 200) String title,
      @Size(max = 30000) String description, Issue.Status status, UUID managerId, UUID assigneeId, Integer parent,
      LocalDate dueDate, LocalDate startDate, LocalDate plannedEndDate, LocalDate completedOn, LocalDate closedOn,
      BigDecimal estimatedHours, String agentSession, List<String> modules) {
  }

  /** 만들기 — 번호는 워크스페이스 순번에서 받는다(어느 프로젝트든 겹치지 않는다) */
  @PostMapping("/issues")
  @ProjectApi(Permission.ISSUE_CREATE)
  @ResponseStatus(HttpStatus.CREATED)
  public IssueViews.Detail create(ProjectContext ctx, CurrentUser user, @Valid @RequestBody CreateRequest r) {
    return issues.create(ctx, new IssueService.CreateCommand(r.type(), r.title(), r.description(), r.status(),
        r.managerId(), r.assigneeId(), r.parent(), r.dueDate(), r.startDate(), r.plannedEndDate(), r.completedOn(),
        r.closedOn(), r.estimatedHours(), r.agentSession(), r.modules()), user.actor(), IssueJournal.Via.WEB);
  }
}
