package com.moai.works.api.platform;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.moai.works.api.account.PlatformRole;
import com.moai.works.api.config.EndPointPaths;
import com.moai.works.api.security.access.CurrentUser;
import com.moai.works.api.security.access.PlatformApi;
import com.moai.works.api.workspace.Workspace;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 시스템 관리자의 워크스페이스 관리 — 목록 · 사용 중지 · 다시 사용 · 삭제(사용 중지한 뒤에만). 렐름 역할 system-admin 만.
 * 시스템 관리자는 워크스페이스 <b>내용</b>을 보지 않는다 — 이름 · 주소 · 소유자 · 멤버 수 · 상태와 삭제 영향의 수까지다.
 * 주소의 {@code {slug}} 는 워크스페이스 주소(workspace-…)다.
 */
@RestController
@RequestMapping(EndPointPaths.V1 + "/admin/workspaces")
@PlatformApi(role = PlatformRole.SYSTEM_ADMIN)
public class WorkspaceAdminController {

  private final WorkspaceAdminService workspaces;

  public WorkspaceAdminController(WorkspaceAdminService workspaces) {
    this.workspaces = workspaces;
  }

  @GetMapping
  public List<WorkspaceAdminService.WorkspaceView> list(@RequestParam(required = false) Workspace.Kind kind,
      @RequestParam(required = false) Workspace.Status status, @RequestParam(required = false) String query,
      @RequestParam(defaultValue = "200") int limit) {
    return workspaces.list(kind, status, query, limit);
  }

  /** 사용 중지 이유 — 시스템 관리 화면과 감사에 남는다(멤버에게는 보이지 않는다) */
  public record SuspendRequest(@NotBlank @Size(max = 500) String reason) {
  }

  @PostMapping("/{slug}/suspend")
  public WorkspaceAdminService.WorkspaceView suspend(CurrentUser user, @PathVariable String slug,
      @Valid @RequestBody SuspendRequest r) {
    return workspaces.suspend(slug, r.reason(), user);
  }

  @PostMapping("/{slug}/resume")
  public WorkspaceAdminService.WorkspaceView resume(CurrentUser user, @PathVariable String slug) {
    return workspaces.resume(slug, user);
  }

  /** 삭제의 영향 미리보기 — 멤버 · 프로젝트 · 일감 · 첨부의 수 */
  @GetMapping("/{slug}/deletion-preview")
  public WorkspaceAdminService.DeletionPreview deletionPreview(@PathVariable String slug) {
    return workspaces.deletionPreview(slug);
  }

  /** 삭제 — 사용 중지한 팀 워크스페이스만. confirm 에 주소를 다시 넣어야 한다(위험 작업 확인 — api 가 다시 본다) */
  @DeleteMapping("/{slug}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(CurrentUser user, @PathVariable String slug, @RequestParam(required = false) String confirm) {
    workspaces.delete(slug, confirm, user);
  }
}
