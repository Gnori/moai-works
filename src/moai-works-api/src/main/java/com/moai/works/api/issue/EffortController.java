package com.moai.works.api.issue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.moai.works.api.config.EndPointPaths;
import com.moai.works.api.security.access.CurrentUser;
import com.moai.works.api.security.access.WorkspaceApi;
import com.moai.works.api.security.access.WorkspaceContext;
import com.moai.works.api.workspace.Permission;

import jakarta.servlet.http.HttpServletResponse;

/**
 * 공수 집계 — 예상 공수 대비 실제 투입 공수(작업 시간 기록) (FR-ISS-017).
 *
 * <p>여러 프로젝트를 한꺼번에 보므로 입구는 워크스페이스 멤버({@code workspace.read})이고, <b>어느 프로젝트의 기록이 들어가는지는
 * {@link EffortService} 가 프로젝트 판정({@code ProjectAccessService} — {@code @ProjectApi} 와 같은 규칙)으로 정한다</b>.
 * 읽을 수 없는 프로젝트의 기록은 들어가지 않고, 그 프로젝트를 조건으로 주면 404 {@code PROJECT_NOT_FOUND}.
 * 프로젝트 화면의 공수는 같은 입구에 {@code project=<주소 이름>} 을 준 것이다.
 *
 * <p>조건: {@code from} · {@code to}(yyyy-MM-dd, 포함) · {@code project}(주소 이름, 여러 번) · {@code person}(사용자 id 또는 me,
 * 여러 번) · {@code type}(일감 유형 key, 여러 번) · {@code groupBy}(person · project · issue · type · day · week · month, 기본 project).
 * 틀린 값은 400 {@code INVALID_FILTER}.
 */
@RestController
@RequestMapping(EndPointPaths.V1 + "/workspaces/{workspace}")
public class EffortController {

  private final EffortService effort;

  public EffortController(EffortService effort) {
    this.effort = effort;
  }

  @GetMapping("/effort")
  @WorkspaceApi(Permission.WORKSPACE_READ)
  public EffortService.Report report(WorkspaceContext ctx, @RequestParam(required = false) String from,
      @RequestParam(required = false) String to, @RequestParam(required = false) List<String> project,
      @RequestParam(required = false) List<String> person, @RequestParam(required = false) List<String> type,
      @RequestParam(required = false) String groupBy) {
    return effort.report(ctx, EffortService.parse(ctx.userId(), from, to, project, person, type, groupBy));
  }

  /**
   * 같은 집계를 CSV 로 — 줄 · 값이 {@link #report} 와 같다. 내려받으면 감사 {@code issue.work_log.export}(조건 · 줄 수)에 남는다.
   *
   * @param lang 머리 줄의 언어(ko · en)
   */
  @GetMapping("/effort.csv")
  @WorkspaceApi(Permission.WORKSPACE_READ)
  public void csv(WorkspaceContext ctx, CurrentUser user, @RequestParam(required = false) String from,
      @RequestParam(required = false) String to, @RequestParam(required = false) List<String> project,
      @RequestParam(required = false) List<String> person, @RequestParam(required = false) List<String> type,
      @RequestParam(required = false) String groupBy, @RequestParam(defaultValue = "ko") String lang,
      HttpServletResponse response) throws IOException {
    EffortService.Query q = EffortService.parse(ctx.userId(), from, to, project, person, type, groupBy);
    byte[] body = effort.exportCsv(ctx, q, lang, user.actor()).getBytes(StandardCharsets.UTF_8);
    String name = "effort-" + q.groupBy().code() + "-" + (q.from() == null ? "all" : q.from()) + "-"
        + (q.to() == null ? "all" : q.to()) + ".csv";
    response.setContentType("text/csv;charset=UTF-8");
    response.setContentLength(body.length);
    response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
        ContentDisposition.attachment().filename(name, StandardCharsets.UTF_8).build().toString());
    response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
    response.setHeader("X-Content-Type-Options", "nosniff");
    response.getOutputStream().write(body);
  }
}
