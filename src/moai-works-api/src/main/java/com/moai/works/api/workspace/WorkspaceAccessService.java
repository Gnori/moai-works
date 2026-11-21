package com.moai.works.api.workspace;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.moai.works.api.security.access.WorkspaceContext;

/**
 * 워크스페이스 권한 판정 — 판정은 이 한 곳에서만 한다 (design/03 §3.4).
 *
 * <pre>
 * 권한(사람, 워크스페이스) = ⋃ 그 사람의 역할들의 권한 코드
 *                         (멤버가 아니거나, 멤버십이 정지됐거나, 워크스페이스가 활성이 아니면 ∅ → 404)
 * </pre>
 * Keycloak 이 아니라 DB 사본으로 판정한다 — 토큰에는 조직 별칭만 있고, 역할 변경이 다음 요청부터 바로 반영돼야 하기 때문이다.
 * 판정은 요청마다 한 번(인덱스 조회 3회)이다. 여러 인스턴스 캐시가 필요해지면 (사람, 워크스페이스, 권한 버전) 키로 둔다.
 */
@Service
public class WorkspaceAccessService {

  private final WorkspaceRepositories.Workspaces workspaces;
  private final WorkspaceRepositories.Members members;
  private final WorkspaceRepositories.MemberRoles memberRoles;
  private final WorkspaceRepositories.Roles roles;

  public WorkspaceAccessService(WorkspaceRepositories.Workspaces workspaces, WorkspaceRepositories.Members members,
      WorkspaceRepositories.MemberRoles memberRoles, WorkspaceRepositories.Roles roles) {
    this.workspaces = workspaces;
    this.members = members;
    this.memberRoles = memberRoles;
    this.roles = roles;
  }

  /** 활성 멤버면 문맥, 아니면 empty (호출자는 404 로 숨긴다). */
  @Transactional(readOnly = true)
  public Optional<WorkspaceContext> contextFor(String slug, UUID userId) {
    return workspaces.findBySlug(slug).filter(Workspace::isActive).flatMap(ws -> contextFor(ws, userId));
  }

  @Transactional(readOnly = true)
  public Optional<WorkspaceContext> contextFor(Workspace ws, UUID userId) {
    Optional<WorkspaceMember> member = members.find(ws.getId(), userId).filter(WorkspaceMember::isActive);
    if (member.isEmpty()) {
      return Optional.empty();
    }
    Set<String> roleKeys = memberRoles.find(ws.getId(), userId).stream().map(MemberRole::getRoleKey)
        .collect(Collectors.toUnmodifiableSet());
    return Optional.of(new WorkspaceContext(ws, userId, roleKeys, permissionsOf(ws.getId(), roleKeys)));
  }

  /** 역할 키들의 권한 합집합 */
  @Transactional(readOnly = true)
  public Set<Permission> permissionsOf(UUID workspaceId, Set<String> roleKeys) {
    EnumSet<Permission> result = EnumSet.noneOf(Permission.class);
    List<WorkspaceRole> all = roles.findByWorkspaceIdOrderByBuiltinDescRoleKeyAsc(workspaceId);
    all.stream().filter(r -> roleKeys.contains(r.getRoleKey()))
        .flatMap(r -> r.getPermissions().stream())
        .map(Permission::ofCode)
        .flatMap(Optional::stream)
        .forEach(result::add);
    return result;
  }
}
