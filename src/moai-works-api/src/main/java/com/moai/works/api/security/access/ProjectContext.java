package com.moai.works.api.security.access;

import java.util.Set;
import java.util.UUID;

import com.moai.works.api.project.Project;
import com.moai.works.api.workspace.Permission;

/**
 * {@link ProjectApi} 를 통과한 요청의 프로젝트 문맥 — 요청마다 한 번만 계산한다 (moai-wiki 의 WikiContext 와 같다).
 *
 * @param workspace   워크스페이스 문맥 (호출자는 활성 멤버)
 * @param project     대상 프로젝트 (호출자는 독자)
 * @param permissions 이 프로젝트에서 호출자가 가진 권한 = 워크스페이스 역할 ∪ 프로젝트 역할
 */
public record ProjectContext(WorkspaceContext workspace, Project project, Set<Permission> permissions) {

  public boolean has(Permission permission) {
    return permissions.contains(permission);
  }

  public UUID userId() {
    return workspace.userId();
  }

  public UUID workspaceId() {
    return workspace.workspaceId();
  }

  public UUID projectId() {
    return project.getId();
  }
}
