package com.moai.works.api.project;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** 프로젝트 모듈 — 목록은 순서(position) · key 순 */
public interface ProjectModuleRepository extends JpaRepository<ProjectModule, UUID> {

  @Query("select m from ProjectModule m where m.projectId = ?1 order by m.position, m.key")
  List<ProjectModule> findOrdered(UUID projectId);

  @Query("select m from ProjectModule m where m.projectId = ?1 and m.key = ?2")
  Optional<ProjectModule> findByKey(UUID projectId, String key);

  @Query("select m from ProjectModule m where m.projectId = ?1 and m.key in ?2")
  List<ProjectModule> findByKeys(UUID projectId, Collection<String> keys);

  /** 여러 프로젝트의 그 key 모듈들 — 워크스페이스 일감 목록의 모듈 거르기 */
  @Query("select m from ProjectModule m where m.projectId in ?1 and m.key in ?2")
  List<ProjectModule> findByKeysIn(Collection<UUID> projectIds, Collection<String> keys);

  /** 여러 프로젝트의 모듈 전부 — 순서 · key 순 */
  @Query("select m from ProjectModule m where m.projectId in ?1 order by m.position, m.key")
  List<ProjectModule> findAllIn(Collection<UUID> projectIds);

  @Query("select count(m) from ProjectModule m where m.projectId = ?1")
  long countIn(UUID projectId);

  @Query("select coalesce(max(m.position), -1) from ProjectModule m where m.projectId = ?1")
  int maxPosition(UUID projectId);

  /** 모듈마다 붙은 일감 수(지운 일감 빼고) — [모듈 id, 수] */
  @Query("select im.moduleId, count(im) from IssueModule im, Issue i where i.id = im.issueId and i.deletedAt is null"
      + " and im.projectId = ?1 group by im.moduleId")
  List<Object[]> issueCounts(UUID projectId);
}
