package com.moai.works.api.issue;

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
   *   <li>status: NEW · IN_PROGRESS · DONE · CLOSED, 또는 open(신규 · 진행) · closed(완료 · 종료)</li>
   *   <li>assignee · manager: 사람 id, 또는 me(나). assignee=none 이면 담당자가 없는 것만</li>
   *   <li>parent: 일감 번호(그 바로 아래), 또는 top · none(맨 위 일감만)</li>
   * </ul>
   * 알 수 없는 값은 400 INVALID_FILTER.
   */
  @GetMapping("/issues")
  @ProjectApi(Permission.ISSUE_READ)
  public IssueViews.Page list(ProjectContext ctx, @RequestParam(required = false) List<String> status,
      @RequestParam(required = false) List<String> type, @RequestParam(required = false) String manager,
      @RequestParam(required = false) String assignee, @RequestParam(required = false) String parent,
      @RequestParam(required = false) String q, @RequestParam(required = false) String sort,
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
    return issues.list(ctx, new IssueService.ListQuery(statuses(status), type, person(ctx, manager, "manager"),
        noAssignee ? null : person(ctx, assignee, "assignee"), noAssignee, parentNumber, top, q, sort, page, size));
  }

  private static Set<Issue.Status> statuses(List<String> values) {
    Set<Issue.Status> result = EnumSet.noneOf(Issue.Status.class);
    if (values == null) {
      return result;
    }
    for (String v : values) {
      switch (v) {
        case "open" -> result.addAll(List.of(Issue.Status.NEW, Issue.Status.IN_PROGRESS));
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
      LocalDate dueDate, LocalDate startDate, LocalDate plannedEndDate, LocalDate completedOn, LocalDate closedOn) {
  }

  @PostMapping("/issues")
  @ProjectApi(Permission.ISSUE_CREATE)
  @ResponseStatus(HttpStatus.CREATED)
  public IssueViews.Detail create(ProjectContext ctx, CurrentUser user, @Valid @RequestBody CreateRequest r) {
    return issues.create(ctx, new IssueService.CreateCommand(r.type(), r.title(), r.description(), r.status(),
        r.managerId(), r.assigneeId(), r.parent(), r.dueDate(), r.startDate(), r.plannedEndDate(), r.completedOn(),
        r.closedOn()), user.actor(), IssueJournal.Via.WEB);
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

  public record CommentRequest(@NotBlank @Size(max = 30000) String notes) {
  }

  @PostMapping("/issues/{number}/comments")
  @ProjectApi(Permission.ISSUE_COMMENT)
  public IssueViews.Detail comment(ProjectContext ctx, CurrentUser user, @PathVariable int number,
      @Valid @RequestBody CommentRequest r) {
    return issues.comment(ctx, number, r.notes(), user.actor(), IssueJournal.Via.WEB);
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
