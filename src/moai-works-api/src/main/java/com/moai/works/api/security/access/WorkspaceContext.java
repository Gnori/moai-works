package com.moai.works.api.security.access;

import java.util.Set;
import java.util.UUID;

import com.moai.works.api.workspace.Permission;
import com.moai.works.api.workspace.Workspace;

/**
 * {@link WorkspaceApi} 를 통과한 요청의 워크스페이스 문맥 — 요청마다 한 번만 계산한다.
 *
 * @param workspace   대상 워크스페이스 (활성)
 * @param userId      호출자 (활성 멤버)
 * @param roleKeys    호출자의 역할 키들
 * @param permissions 역할들의 권한 합집합
 */
public record WorkspaceContext(Workspace workspace, UUID userId, Set<String> roleKeys, Set<Permission> permissions) {

  public boolean has(Permission permission) {
    return permissions.contains(permission);
  }

  public boolean isOwner() {
    return roleKeys.contains("owner");
  }

  public UUID workspaceId() {
    return workspace.getId();
  }
}
