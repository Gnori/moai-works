package com.moai.works.api.project;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

public interface ProjectRepository extends JpaRepository<Project, UUID> {

  /** 지우지 않은 프로젝트를 주소 이름으로 찾는다 */
  @Query("select p from Project p where p.workspaceId = ?1 and p.key = ?2"
      + " and p.status <> com.moai.works.api.project.Project.Status.DELETED")
  Optional<Project> findLive(UUID workspaceId, String key);

  /** 지우지 않은 프로젝트 전부 (이름순 — 대소문자를 가리지 않는다. 한글이 가나다순이 되게 name 열은 "C" 정렬이다) */
  @Query("select p from Project p where p.workspaceId = ?1"
      + " and p.status <> com.moai.works.api.project.Project.Status.DELETED order by lower(p.name), p.key")
  List<Project> findLiveByWorkspace(UUID workspaceId);

  @Query("select count(p) from Project p where p.workspaceId = ?1")
  long countEver(UUID workspaceId);

  /** 지우지 않은 일감 수 */
  @Query("select count(i) from Issue i where i.projectId = ?1 and i.deletedAt is null")
  long countLiveIssues(UUID projectId);

  /** 일감 번호를 줄 때 — 같은 프로젝트에 동시에 만들어도 번호가 겹치지 않게 행을 잠근다 */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select p from Project p where p.id = ?1")
  Optional<Project> lockById(UUID id);
}
