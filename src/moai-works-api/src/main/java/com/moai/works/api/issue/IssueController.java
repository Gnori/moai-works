package com.moai.works.api.issue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
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

import com.moai.works.api.config.EndPointPaths;
import com.moai.works.api.security.access.CurrentUser;
import com.moai.works.api.security.access.IssueApi;
import com.moai.works.api.security.access.ProjectContext;
import com.moai.works.api.workspace.Permission;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 일감 하나 — 정식 주소는 워크스페이스 아래 번호다(Redmine 의 {@code /issues/:id}, ADR-030): {@code /workspaces/{workspace}/issues/{number}}.
 * {@link IssueApi} 가 그 일감의 지금 프로젝트로 판정한다(일감이 없거나 독자가 아니면 404, 코드가 없으면 403) — 권한 경계는 그대로 프로젝트다.
 * 프로젝트를 옮기는 것은 고치기(PATCH)의 {@code project} 다. moai-wiki 의 PageController 자리다.
 */
@RestController
@RequestMapping(EndPointPaths.V1 + "/workspaces/{workspace}/issues/{number}")
public class IssueController {

  private final IssueService issues;

  public IssueController(IssueService issues) {
    this.issues = issues;
  }

  @GetMapping
  @IssueApi(Permission.ISSUE_READ)
  public IssueViews.Detail detail(ProjectContext ctx, @PathVariable int number) {
    return issues.detail(ctx, number);
  }

  /**
   * 고치기 — 넣은 필드만 바꾼다(null 이면 비운다). rowVersion 필수. 관리자 바꾸기는 issue.manager.assign 도 있어야 한다.
   * project 에 다른 프로젝트 주소 이름을 넣으면 하위 일감과 함께 옮긴다 — 옮길 프로젝트의 issue.create 도 있어야 한다(서비스가 본다)
   */
  @PatchMapping
  @IssueApi(Permission.ISSUE_EDIT)
  public IssueViews.Detail update(ProjectContext ctx, CurrentUser user, @PathVariable int number,
      @RequestBody Map<String, Object> body) {
    return issues.update(ctx, number, body, user.actor(), IssueJournal.Via.WEB);
  }

  @DeleteMapping
  @IssueApi(Permission.ISSUE_DELETE)
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(ProjectContext ctx, CurrentUser user, @PathVariable int number) {
    issues.delete(ctx, number, user.actor());
  }

  // ── 리비전 · 차이 (moai-wiki 의 페이지 리비전 API 와 같은 모양) ──

  /** 판들 — 새 판부터. before 는 앞 쪽 응답의 next */
  @GetMapping("/revisions")
  @IssueApi(Permission.ISSUE_READ)
  public IssueViews.RevisionList revisions(ProjectContext ctx, @PathVariable int number,
      @RequestParam(required = false) Integer before, @RequestParam(defaultValue = "50") int limit) {
    return issues.revisions(ctx, number, before, limit);
  }

  @GetMapping("/revisions/{no}")
  @IssueApi(Permission.ISSUE_READ)
  public IssueViews.Revision revision(ProjectContext ctx, @PathVariable int number, @PathVariable int no) {
    return issues.revision(ctx, number, no);
  }

  /** 두 판의 차이 — from 이 비면 바로 앞 판과 견준다 */
  @GetMapping("/revisions/{no}/diff")
  @IssueApi(Permission.ISSUE_READ)
  public IssueViews.RevisionDiff diff(ProjectContext ctx, @PathVariable int number, @PathVariable int no,
      @RequestParam(required = false) Integer from) {
    return issues.diff(ctx, number, no, from);
  }

  // ── 작업 시간 ──

  public record WorkLogRequest(@NotNull LocalDate workDate, @NotNull BigDecimal hours, @Size(max = 1000) String note) {
  }

  /** 작업 시간 기록 — 한 일감에 여러 번. 담당자가 아니어도 일감을 고칠 수 있는 사람이면 자기 시간을 적는다 */
  @PostMapping("/work-logs")
  @IssueApi(Permission.ISSUE_EDIT)
  public IssueViews.Detail logWork(ProjectContext ctx, CurrentUser user, @PathVariable int number,
      @Valid @RequestBody WorkLogRequest r) {
    return issues.logWork(ctx, number, r.workDate(), r.hours(), r.note(), user.actor(), IssueJournal.Via.WEB);
  }

  /** 지우기 — 자기 기록은 그대로, 남의 기록은 project.manage 가 있어야. 지움 표시만 한다 */
  @DeleteMapping("/work-logs/{logId}")
  @IssueApi(Permission.ISSUE_EDIT)
  public IssueViews.Detail removeWork(ProjectContext ctx, CurrentUser user, @PathVariable int number,
      @PathVariable UUID logId) {
    return issues.removeWork(ctx, number, logId, user.actor(), IssueJournal.Via.WEB);
  }

  public record CommentRequest(@NotBlank @Size(max = 30000) String notes) {
  }

  @PostMapping("/comments")
  @IssueApi(Permission.ISSUE_COMMENT)
  public IssueViews.Detail comment(ProjectContext ctx, CurrentUser user, @PathVariable int number,
      @Valid @RequestBody CommentRequest r) {
    return issues.comment(ctx, number, r.notes(), user.actor(), IssueJournal.Via.WEB);
  }

  /** 댓글 목록 — 오래된 것부터 한 쪽씩(지운 댓글은 글 없이 deleted). 응답에 전체 수 · 다음 쪽이 있는지(hasNext) */
  @GetMapping("/comments")
  @IssueApi(Permission.ISSUE_READ)
  public IssueViews.CommentPage comments(ProjectContext ctx, @PathVariable int number,
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
    return issues.comments(ctx, number, page, size);
  }

  /** @param rowVersion 댓글을 읽을 때 받은 판 — 비면 400 ROW_VERSION_REQUIRED, 다르면 409 CONCURRENT_UPDATE */
  public record CommentEditRequest(@NotBlank @Size(max = 30000) String notes, Integer rowVersion) {
  }

  /** 댓글 고치기 — 쓴 사람만(지금 issue.comment 도 있어야). 앞 글은 쌓아 두고, 이력 · 감사에 남긴다 */
  @PatchMapping("/comments/{commentId}")
  @IssueApi(Permission.ISSUE_COMMENT)
  public IssueViews.Detail editComment(ProjectContext ctx, CurrentUser user, @PathVariable int number,
      @PathVariable UUID commentId, @Valid @RequestBody CommentEditRequest r) {
    return issues.editComment(ctx, number, commentId, r.notes(), r.rowVersion(), user.actor(), IssueJournal.Via.WEB);
  }

  /**
   * 댓글 지우기 — 쓴 사람, 또는 project.manage(서비스가 본다). 지움 표시만 하고 "지운 댓글"로 제자리에 남는다.
   * 독자면 부를 수 있게 issue.read 로 열고(지금 댓글 권한이 없어도 자기 댓글은 지운다), 나머지는 서비스가 판정한다.
   */
  @DeleteMapping("/comments/{commentId}")
  @IssueApi(Permission.ISSUE_READ)
  public IssueViews.Detail deleteComment(ProjectContext ctx, CurrentUser user, @PathVariable int number,
      @PathVariable UUID commentId) {
    return issues.deleteComment(ctx, number, commentId, user.actor(), IssueJournal.Via.WEB);
  }

  /** @param target 연결할 일감 번호 — 워크스페이스 번호라 다른 프로젝트의 일감도 된다(지금 읽을 수 있는 것만, 아니면 400 LINK_TARGET_NOT_FOUND) */
  public record LinkRequest(@NotNull Integer target, @NotNull IssueLink.Type type) {
  }

  @PostMapping("/links")
  @IssueApi(Permission.ISSUE_EDIT)
  public IssueViews.Detail link(ProjectContext ctx, CurrentUser user, @PathVariable int number,
      @Valid @RequestBody LinkRequest r) {
    return issues.link(ctx, number, r.target(), r.type(), user.actor(), IssueJournal.Via.WEB);
  }

  @DeleteMapping("/links/{linkId}")
  @IssueApi(Permission.ISSUE_EDIT)
  public IssueViews.Detail unlink(ProjectContext ctx, CurrentUser user, @PathVariable int number,
      @PathVariable UUID linkId) {
    return issues.unlink(ctx, number, linkId, user.actor(), IssueJournal.Via.WEB);
  }
}
