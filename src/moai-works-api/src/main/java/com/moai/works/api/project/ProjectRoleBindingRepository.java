package com.moai.works.api.project;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ProjectRoleBindingRepository extends JpaRepository<ProjectRoleBinding, ProjectRoleBinding.Key> {

  List<ProjectRoleBinding> findByProjectIdAndUserId(UUID projectId, UUID userId);

  List<ProjectRoleBinding> findByProjectIdOrderByUserIdAscRoleAsc(UUID projectId);

  /** 워크스페이스를 나간 사람의 프로젝트 역할 사본을 지운다 (Keycloak 쪽은 조직에서 빠질 때 함께 빠진다) */
  @Modifying
  @Query("delete from ProjectRoleBinding b where b.userId = ?1"
      + " and b.projectId in (select p.id from Project p where p.workspaceId = ?2)")
  int deleteByUserInWorkspace(UUID userId, UUID workspaceId);
}
