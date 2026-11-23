package com.moai.works.api.workspace;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

/** 워크스페이스 저장소 묶음. */
public final class WorkspaceRepositories {

  private WorkspaceRepositories() {
  }

  public interface Workspaces extends JpaRepository<Workspace, UUID> {

    Optional<Workspace> findBySlug(String slug);

    boolean existsBySlug(String slug);

    @Query("select count(w) from Workspace w where w.createdBy = ?1 and w.kind = com.moai.works.api.workspace.Workspace.Kind.TEAM"
        + " and w.status <> com.moai.works.api.workspace.Workspace.Status.DELETED")
    long countTeamWorkspacesCreatedBy(UUID userId);

    @Query("select w from Workspace w where w.status = com.moai.works.api.workspace.Workspace.Status.PROVISIONING")
    List<Workspace> findProvisioning();

    /** 상태를 바꿀 때(사용 중지 · 다시 사용 · 삭제) — 같은 워크스페이스의 동시 요청이 서로 덮지 않게 행을 잠근다 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Workspace w where w.id = ?1")
    Optional<Workspace> lockById(UUID id);
  }

  public interface Roles extends JpaRepository<WorkspaceRole, UUID> {

    List<WorkspaceRole> findByWorkspaceIdOrderByBuiltinDescRoleKeyAsc(UUID workspaceId);

    Optional<WorkspaceRole> findByWorkspaceIdAndRoleKey(UUID workspaceId, String roleKey);
  }

  public interface Members extends JpaRepository<WorkspaceMember, WorkspaceMember.Id> {

    @Query("select m from WorkspaceMember m where m.id.workspaceId = ?1 and m.id.userId = ?2")
    Optional<WorkspaceMember> find(UUID workspaceId, UUID userId);

    @Query("select m from WorkspaceMember m where m.id.userId = ?1")
    List<WorkspaceMember> findByUser(UUID userId);

    @Query("select m from WorkspaceMember m where m.id.workspaceId = ?1 order by m.joinedAt")
    List<WorkspaceMember> findByWorkspace(UUID workspaceId);

    @Query("select count(m) from WorkspaceMember m where m.id.workspaceId = ?1")
    long countByWorkspace(UUID workspaceId);
  }

  public interface MemberRoles extends JpaRepository<MemberRole, MemberRole.Id> {

    @Query("select r from MemberRole r where r.id.workspaceId = ?1 and r.id.userId = ?2")
    List<MemberRole> find(UUID workspaceId, UUID userId);

    @Query("select r from MemberRole r where r.id.workspaceId = ?1")
    List<MemberRole> findByWorkspace(UUID workspaceId);

    @Query("select r from MemberRole r where r.id.userId = ?1")
    List<MemberRole> findByUser(UUID userId);

    @Query("select count(r) from MemberRole r where r.id.workspaceId = ?1 and r.id.roleKey = ?2")
    long countWithRole(UUID workspaceId, String roleKey);

    @Query("select count(r) from MemberRole r join WorkspaceMember m on m.id.workspaceId = r.id.workspaceId"
        + " and m.id.userId = r.id.userId where r.id.workspaceId = ?1 and r.id.roleKey = ?2"
        + " and m.status = com.moai.works.api.workspace.WorkspaceMember.Status.ACTIVE")
    long countActiveWithRole(UUID workspaceId, String roleKey);
  }

  public interface JoinRequests extends JpaRepository<JoinRequest, UUID> {

    @Query("select j from JoinRequest j where j.workspaceId = ?1 and j.userId = ?2"
        + " and j.status = com.moai.works.api.workspace.JoinRequest.Status.PENDING")
    Optional<JoinRequest> findPending(UUID workspaceId, UUID userId);

    List<JoinRequest> findByWorkspaceIdAndStatusOrderByRequestedAtAsc(UUID workspaceId, JoinRequest.Status status);

    List<JoinRequest> findByUserIdOrderByRequestedAtDesc(UUID userId);

    @Query("select j from JoinRequest j where j.workspaceId = ?1 and j.userId = ?2"
        + " and j.status = com.moai.works.api.workspace.JoinRequest.Status.REJECTED order by j.decidedAt desc limit 1")
    Optional<JoinRequest> findLastRejected(UUID workspaceId, UUID userId);
  }
}
