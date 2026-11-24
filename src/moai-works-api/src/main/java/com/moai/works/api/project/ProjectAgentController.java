package com.moai.works.api.project;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
 * 프로젝트의 에이전트 멤버 (2026-11-25, 일감 #285) — 보기는 독자 누구나(프로젝트 역할 목록과 같다), 더하기 · 고치기는 {@code project.manage}.
 * 지우기는 없다(쓰지 않음으로 둔다). 에이전트 멤버는 이름표일 뿐 권한이 없다 — 주인의 MCP 키 · 주인의 권한으로 일한다(ADR-024).
 * 판정은 {@link ProjectApi} 와 서비스가 한다. 일감에 새 이름이 들어오면 일감 서비스가 자동으로 등록한다(웹 · MCP 모두).
 */
@RestController
@RequestMapping(EndPointPaths.V1 + "/workspaces/{workspace}/projects/{project}/agents")
public class ProjectAgentController {

  private final ProjectAgentService agents;

  public ProjectAgentController(ProjectAgentService agents) {
    this.agents = agents;
  }

  /** 에이전트 전부(쓰지 않는 것 포함) — 쓰는 것 먼저, 이름순. 열린 일감 수 · 일감 수 · 마지막으로 고친 때와 함께 */
  @GetMapping
  @ProjectApi(Permission.ISSUE_READ)
  public List<ProjectViews.Agent> list(ProjectContext ctx) {
    return agents.list(ctx);
  }

  /**
   * @param name        세션 이름(앞뒤 공백 없는 1~100자, 제어 문자 없음) — 만든 뒤에는 바꿀 수 없다
   * @param owner       주인의 아이디(username) — 비우면 부른 사람
   * @param description 설명(200자까지)
   */
  public record CreateRequest(String name, String owner, String description) {
  }

  @PostMapping
  @ProjectApi(Permission.PROJECT_MANAGE)
  @ResponseStatus(HttpStatus.CREATED)
  public ProjectViews.Agent create(ProjectContext ctx, CurrentUser user, @RequestBody CreateRequest r) {
    return agents.create(ctx, r.name(), r.owner(), r.description(), user.actor(), "WEB");
  }

  /** 고치기 — 준 것만(description · active · owner(아이디), rowVersion 은 선택). active=false 면 쓰지 않음 */
  @PatchMapping("/{id}")
  @ProjectApi(Permission.PROJECT_MANAGE)
  public ProjectViews.Agent update(ProjectContext ctx, CurrentUser user, @PathVariable UUID id, @RequestBody Map<String, Object> body) {
    return agents.update(ctx, id, body, user.actor(), "WEB");
  }
}
