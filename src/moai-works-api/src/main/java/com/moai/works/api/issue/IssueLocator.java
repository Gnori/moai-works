package com.moai.works.api.issue;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.moai.works.api.project.ProjectAccessService;
import com.moai.works.api.project.ProjectRepository;
import com.moai.works.api.security.access.ProjectContext;
import com.moai.works.api.security.access.WorkspaceContext;

/**
 * 워크스페이스 안 번호 → 그 일감의 지금 프로젝트 문맥 (ADR-034). REST({@code @IssueApi}) · MCP({@code McpScope}) · 첨부 티켓이
 * 모두 이 한 곳으로 일감을 찾는다 — 판정은 {@link ProjectAccessService} 그대로(그 일감의 지금 프로젝트의 독자인가).
 */
@Service
public class IssueLocator {

  private final IssueRepositories.Issues issues;
  private final ProjectRepository projects;
  private final ProjectAccessService projectAccess;

  public IssueLocator(IssueRepositories.Issues issues, ProjectRepository projects, ProjectAccessService projectAccess) {
    this.issues = issues;
    this.projects = projects;
    this.projectAccess = projectAccess;
  }

  /** 살아 있는 일감이고 그 프로젝트의 독자이면 그 프로젝트의 문맥 — 없거나 · 지웠거나 · 독자가 아니면 empty(부르는 쪽이 404 로 숨긴다) */
  @Transactional(readOnly = true)
  public Optional<ProjectContext> contextFor(WorkspaceContext workspace, int number) {
    if (number <= 0) {
      return Optional.empty();
    }
    return issues.findLiveInWorkspace(workspace.workspaceId(), number)
        .flatMap(i -> projects.findById(i.getProjectId()))
        .flatMap(p -> projectAccess.contextFor(workspace, p));
  }

  /** 그 워크스페이스에서 이 번호를 준 적이 있는가(지운 · 읽을 수 없는 일감 포함 — 번호는 순번이라 감출 것이 아니다) */
  @Transactional(readOnly = true)
  public boolean numberUsed(UUID workspaceId, int number) {
    return number > 0 && issues.numberUsed(workspaceId, number);
  }
}
