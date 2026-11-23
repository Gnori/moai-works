package com.moai.works.api.issue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.moai.works.api.common.ApiException;
import com.moai.works.api.config.EndPointPaths;
import com.moai.works.api.security.access.CurrentUser;
import com.moai.works.api.security.access.ProjectApi;
import com.moai.works.api.security.access.ProjectContext;
import com.moai.works.api.workspace.Permission;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 일감 — 모두 프로젝트 하나 안의 일이라 {@link ProjectApi} 로 판정한다(독자가 아니면 404, 코드가 없으면 403).
 * moai-wiki 의 PageController 자리다. 일감마다의 규칙은 {@link IssueService} 가 본다.
 */
@RestController
@RequestMapping(EndPointPaths.V1 + "/workspaces/{workspace}/projects/{project}")
public class IssueController {

  private final IssueService issues;

  public IssueController(IssueService issues) {
    this.issues = issues;
  }

  @GetMapping("/issue-types")
  @ProjectApi(Permission.ISSUE_READ)
  public List<IssueViews.Type> types(ProjectContext ctx) {
    return issues.types();
  }

  /**
   * 목록 — status · type 은 여러 번 줄 수 있다.
   * <ul>
   *   <li>status: NEW · IN_PROGRESS · TESTING · DONE · CLOSED, 또는 open(등록 · 진행 · 테스트) · closed(완료 · 종료)</li>
   *   <li>assignee · manager: 사람 id, 또는 me(나). assignee=none 이면 담당자가 없는 것만</li>
   *   <li>parent: 일감 번호(그 바로 아래), 또는 top · none(맨 위 일감만)</li>
   *   <li>q: 제목 · 설명에 든 말, 또는 #번호 · dueFrom · dueTo: 완료 기한 범위(yyyy-MM-dd) · overdue=true: 기한 지난 열린 일감만</li>
   *   <li>agentSession: 에이전트 세션 이름(대소문자 무시), 또는 none(세션이 비어 있는 것만)</li>
   *   <li>sort: number · updated · due · start · planned · created · status · title (앞에 - 면 내림차순)</li>
   * </ul>
   * 응답에 전체 수 · 전체 쪽 수 · 다음 쪽이 있는지(hasNext).
   * 알 수 없는 값은 400 INVALID_FILTER.
   */
  @GetMapping("/issues")
  @ProjectApi(Permission.ISSUE_READ)
  public IssueViews.Page list(ProjectContext ctx, @RequestParam(required = false) List<String> status,
      @RequestParam(required = false) List<String> type, @RequestParam(required = false) String manager,
      @RequestParam(required = false) String assignee, @RequestParam(required = false) String parent,
      @RequestParam(required = false) String q, @RequestParam(required = false) LocalDate dueFrom,
      @RequestParam(required = false) LocalDate dueTo, @RequestParam(defaultValue = "false") boolean overdue,
      @RequestParam(required = false) String agentSession, @RequestParam(required = false) String sort,
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
    boolean noAssignee = "none".equals(assignee);
    boolean top = "top".equals(parent) || "none".equals(parent);
    Integer parentNumber = null;
    if (!top && parent != null && !parent.isBlank()) {
      try {
        parentNumber = Integer.valueOf(parent);
      } catch (NumberFormatException e) {
        throw ApiException.badRequest("INVALID_FILTER", "parent 는 일감 번호 · top · none 가운데 하나입니다.");
      }
    }
    boolean noSession = "none".equals(agentSession);
    return issues.list(ctx, new IssueService.ListQuery(statuses(status), type, person(ctx, manager, "manager"),
        noAssignee ? null : person(ctx, assignee, "assignee"), noAssignee, parentNumber, top, q, dueFrom, dueTo, overdue,
        noSession ? null : agentSession, noSession, sort, page, size));
  }

  /** 이 프로젝트의 일감에 쓰인 에이전트 세션 이름들 — 이름순, 일감 수와 함께(목록 거르기 고르기용) */
  @GetMapping("/agent-sessions")
  @ProjectApi(Permission.ISSUE_READ)
  public List<IssueViews.AgentSession> agentSessions(ProjectContext ctx) {
    return issues.agentSessions(ctx);
  }

  private static Set<Issue.Status> statuses(List<String> values) {
    Set<Issue.Status> result = EnumSet.noneOf(Issue.Status.class);
    if (values == null) {
      return result;
    }
    for (String v : values) {
      switch (v) {
        case "open" -> result.addAll(List.of(Issue.Status.NEW, Issue.Status.IN_PROGRESS, Issue.Status.TESTING));
        case "closed" -> result.addAll(List.of(Issue.Status.DONE, Issue.Status.CLOSED));
        default -> {
          try {
            result.add(Issue.Status.valueOf(v));
          } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("INVALID_FILTER", "알 수 없는 상태입니다: " + v);
          }
        }
      }
    }
    return result;
  }

  private static UUID person(ProjectContext ctx, String value, String name) {
    if (value == null || value.isBlank()) {
      return null;
    }
    if ("me".equals(value)) {
      return ctx.userId();
    }
    try {
      return UUID.fromString(value);
    } catch (IllegalArgumentException e) {
      throw ApiException.badRequest("INVALID_FILTER", name + " 는 사람 id 나 me 여야 합니다.");
    }
  }

  public record CreateRequest(@NotBlank String type, @NotBlank @Size(max = 200) String title,
      @Size(max = 30000) String description, Issue.Status status, UUID managerId, UUID assigneeId, Integer parent,
      LocalDate dueDate, LocalDate startDate, LocalDate plannedEndDate, LocalDate completedOn, LocalDate closedOn,
      BigDecimal estimatedHours, String agentSession) {
  }

  @PostMapping("/issues")
  @ProjectApi(Permission.ISSUE_CREATE)
  @ResponseStatus(HttpStatus.CREATED)
  public IssueViews.Detail create(ProjectContext ctx, CurrentUser user, @Valid @RequestBody CreateRequest r) {
    return issues.create(ctx, new IssueService.CreateCommand(r.type(), r.title(), r.description(), r.status(),
        r.managerId(), r.assigneeId(), r.parent(), r.dueDate(), r.startDate(), r.plannedEndDate(), r.completedOn(),
        r.closedOn(), r.estimatedHours(), r.agentSession()), user.actor(), IssueJournal.Via.WEB);
  }

  @GetMapping("/issues/{number}")
  @ProjectApi(Permission.ISSUE_READ)
  public IssueViews.Detail detail(ProjectContext ctx, @PathVariable int number) {
    return issues.detail(ctx, number);
  }

  /** 고치기 — 넣은 필드만 바꾼다(null 이면 비운다). rowVersion 필수. 관리자 바꾸기는 issue.manager.assign 도 있어야 한다 */
  @PatchMapping("/issues/{number}")
  @ProjectApi(Permission.ISSUE_EDIT)
  public IssueViews.Detail update(ProjectContext ctx, CurrentUser user, @PathVariable int number,
      @RequestBody Map<String, Object> body) {
    return issues.update(ctx, number, body, user.actor(), IssueJournal.Via.WEB);
  }

  @DeleteMapping("/issues/{number}")
  @ProjectApi(Permission.ISSUE_DELETE)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(ProjectContext ctx, CurrentUser user, @PathVariable int number) {
    issues.delete(ctx, number, user.actor());
  }

  // ── 리비전 · 차이 (moai-wiki 의 페이지 리비전 API 와 같은 모양) ──

  /** 판들 — 새 판부터. before 는 앞 쪽 응답의 next */
  @GetMapping("/issues/{number}/revisions")
  @ProjectApi(Permission.ISSUE_READ)
  public IssueViews.RevisionList revisions(ProjectContext ctx, @PathVariable int number,
      @RequestParam(required = false) Integer before, @RequestParam(defaultValue = "50") int limit) {
    return issues.revisions(ctx, number, before, limit);
  }

  @GetMapping("/issues/{number}/revisions/{no}")
  @ProjectApi(Permission.ISSUE_READ)
  public IssueViews.Revision revision(ProjectContext ctx, @PathVariable int number, @PathVariable int no) {
    return issues.revision(ctx, number, no);
  }

  /** 두 판의 차이 — from 이 비면 바로 앞 판과 견준다 */
  @GetMapping("/issues/{number}/revisions/{no}/diff")
  @ProjectApi(Permission.ISSUE_READ)
  public IssueViews.RevisionDiff diff(ProjectContext ctx, @PathVariable int number, @PathVariable int no,
      @RequestParam(required = false) Integer from) {
    return issues.diff(ctx, number, no, from);
  }

  // ── 작업 시간 ──

  public record WorkLogRequest(@NotNull LocalDate workDate, @NotNull BigDecimal hours, @Size(max = 1000) String note) {
  }

  /** 작업 시간 기록 — 한 일감에 여러 번. 담당자가 아니어도 일감을 고칠 수 있는 사람이면 자기 시간을 적는다 */
  @PostMapping("/issues/{number}/work-logs")
  @ProjectApi(Permission.ISSUE_EDIT)
  public IssueViews.Detail logWork(ProjectContext ctx, CurrentUser user, @PathVariable int number,
      @Valid @RequestBody WorkLogRequest r) {
    return issues.logWork(ctx, number, r.workDate(), r.hours(), r.note(), user.actor(), IssueJournal.Via.WEB);
  }

  /** 지우기 — 자기 기록은 그대로, 남의 기록은 project.manage 가 있어야. 지움 표시만 한다 */
  @DeleteMapping("/issues/{number}/work-logs/{logId}")
  @ProjectApi(Permission.ISSUE_EDIT)
  public IssueViews.Detail removeWork(ProjectContext ctx, CurrentUser user, @PathVariable int number,
      @PathVariable UUID logId) {
    return issues.removeWork(ctx, number, logId, user.actor(), IssueJournal.Via.WEB);
  }

  public record CommentRequest(@NotBlank @Size(max = 30000) String notes) {
  }

  @PostMapping("/issues/{number}/comments")
  @ProjectApi(Permission.ISSUE_COMMENT)
  public IssueViews.Detail comment(ProjectContext ctx, CurrentUser user, @PathVariable int number,
      @Valid @RequestBody CommentRequest r) {
    return issues.comment(ctx, number, r.notes(), user.actor(), IssueJournal.Via.WEB);
  }

  /** 댓글 목록 — 오래된 것부터 한 쪽씩(지운 댓글은 글 없이 deleted). 응답에 전체 수 · 다음 쪽이 있는지(hasNext) */
  @GetMapping("/issues/{number}/comments")
  @ProjectApi(Permission.ISSUE_READ)
  public IssueViews.CommentPage comments(ProjectContext ctx, @PathVariable int number,
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
    return issues.comments(ctx, number, page, size);
  }

  /** @param rowVersion 댓글을 읽을 때 받은 판 — 비면 400 ROW_VERSION_REQUIRED, 다르면 409 CONCURRENT_UPDATE */
  public record CommentEditRequest(@NotBlank @Size(max = 30000) String notes, Integer rowVersion) {
  }

  /** 댓글 고치기 — 쓴 사람만(지금 issue.comment 도 있어야). 앞 글은 쌓아 두고, 이력 · 감사에 남긴다 */
  @PatchMapping("/issues/{number}/comments/{commentId}")
  @ProjectApi(Permission.ISSUE_COMMENT)
  public IssueViews.Detail editComment(ProjectContext ctx, CurrentUser user, @PathVariable int number,
      @PathVariable UUID commentId, @Valid @RequestBody CommentEditRequest r) {
    return issues.editComment(ctx, number, commentId, r.notes(), r.rowVersion(), user.actor(), IssueJournal.Via.WEB);
  }

  /**
   * 댓글 지우기 — 쓴 사람, 또는 project.manage(서비스가 본다). 지움 표시만 하고 "지운 댓글"로 제자리에 남는다.
   * 독자면 부를 수 있게 issue.read 로 열고(지금 댓글 권한이 없어도 자기 댓글은 지운다), 나머지는 서비스가 판정한다.
   */
  @DeleteMapping("/issues/{number}/comments/{commentId}")
  @ProjectApi(Permission.ISSUE_READ)
  public IssueViews.Detail deleteComment(ProjectContext ctx, CurrentUser user, @PathVariable int number,
      @PathVariable UUID commentId) {
    return issues.deleteComment(ctx, number, commentId, user.actor(), IssueJournal.Via.WEB);
  }

  public record LinkRequest(@NotNull Integer target, @NotNull IssueLink.Type type) {
  }

  @PostMapping("/issues/{number}/links")
  @ProjectApi(Permission.ISSUE_EDIT)
  public IssueViews.Detail link(ProjectContext ctx, CurrentUser user, @PathVariable int number,
      @Valid @RequestBody LinkRequest r) {
    return issues.link(ctx, number, r.target(), r.type(), user.actor(), IssueJournal.Via.WEB);
  }

  @DeleteMapping("/issues/{number}/links/{linkId}")
  @ProjectApi(Permission.ISSUE_EDIT)
  public IssueViews.Detail unlink(ProjectContext ctx, CurrentUser user, @PathVariable int number,
      @PathVariable UUID linkId) {
    return issues.unlink(ctx, number, linkId, user.actor(), IssueJournal.Via.WEB);
  }
}
