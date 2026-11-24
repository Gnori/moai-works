package com.moai.works.api.project;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.moai.works.api.issue.Issue;

/**
 * 프로젝트 에이전트 멤버 — 이름은 프로젝트 안에서 대소문자 없이 하나(V29 의 고유 색인 {@code lower(name)}). 이름 견주기는 늘 DB 의
 * {@code lower()} 로 한다(자바의 소문자와 DB 의 소문자가 ASCII 밖에서 다를 수 있다 — 색인 · 일감 거르기와 같은 규칙).
 */
public interface ProjectAgentRepository extends JpaRepository<ProjectAgent, UUID> {

  /** 이 프로젝트의 에이전트 전부 — 쓰는 것 먼저, 이름순(대소문자 없이) */
  @Query("select a from ProjectAgent a where a.projectId = ?1 order by a.active desc, lower(a.name), a.name")
  List<ProjectAgent> findOrdered(UUID projectId);

  /** 그 이름의 에이전트(대소문자 없이) */
  @Query("select a from ProjectAgent a where a.projectId = ?1 and lower(a.name) = lower(?2)")
  Optional<ProjectAgent> findByName(UUID projectId, String name);

  /**
   * 에이전트마다 그 이름의 일감(지운 것 빼고, 대소문자 없이) — [에이전트 id, 열린 일감 수, 모든 일감 수, 마지막으로 고친 때].
   * 일감이 없는 에이전트는 줄이 없다. 열린 = {@code openStatuses}(완료 · 종료 · 취소가 아닌 것 — 확인 넷 포함)
   */
  @Query("select a.id, sum(case when i.status in ?2 then 1 else 0 end), count(i), max(i.updatedAt) from ProjectAgent a, Issue i"
      + " where a.projectId = ?1 and i.projectId = ?1 and i.deletedAt is null and i.agentSession is not null"
      + " and lower(i.agentSession) = lower(a.name) group by a.id")
  List<Object[]> issueStats(UUID projectId, Collection<Issue.Status> openStatuses);

  /** 이 프로젝트의 에이전트 이름(DB 의 소문자) — [에이전트 id, 소문자 이름] */
  @Query("select a.id, lower(a.name) from ProjectAgent a where a.projectId = ?1")
  List<Object[]> lowerNames(UUID projectId);

  /** 이 프로젝트의 일감(지운 것 빼고)에 쓰인 세션 이름 — [소문자 이름(DB), 쓴 글자, 일감 수] */
  @Query("select lower(i.agentSession), i.agentSession, count(i) from Issue i where i.projectId = ?1 and i.deletedAt is null"
      + " and i.agentSession is not null group by i.agentSession")
  List<Object[]> usedSessions(UUID projectId);
}
