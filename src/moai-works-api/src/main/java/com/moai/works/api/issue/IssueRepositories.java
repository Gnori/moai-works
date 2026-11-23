package com.moai.works.api.issue;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

/** 일감 쪽 저장소 묶음 */
public final class IssueRepositories {

  private IssueRepositories() {
  }

  public interface Types extends JpaRepository<IssueType, String> {
    List<IssueType> findAllByOrderByPositionAsc();
  }

  public interface Issues extends JpaRepository<Issue, UUID>, JpaSpecificationExecutor<Issue> {

    /** 워크스페이스 안 번호로 — 번호는 워크스페이스에서 하나다(V23). 지운 일감은 없는 것과 같다 */
    @Query("select i from Issue i where i.workspaceId = ?1 and i.number = ?2 and i.deletedAt is null")
    Optional<Issue> findLiveInWorkspace(UUID workspaceId, int number);

    /** 그 번호를 준 적이 있는가(지운 일감 포함) — MCP 가 워크스페이스를 정하지 않았을 때 번호가 여러 워크스페이스에 있는지 본다 */
    @Query("select count(i) > 0 from Issue i where i.workspaceId = ?1 and i.number = ?2")
    boolean numberUsed(UUID workspaceId, int number);

    /** 바로 아래 하위 — 지운 것까지(옮기기는 하위 줄기를 통째로 옮긴다. 지운 하위도 상위 FK 가 같은 프로젝트를 요구한다) */
    @Query("select i from Issue i where i.parentId = ?1 order by i.number")
    List<Issue> findChildrenAnyState(UUID parentId);

    @Query("select i from Issue i where i.parentId = ?1 and i.deletedAt is null order by i.number")
    List<Issue> findChildren(UUID parentId);

    @Query("select count(i) from Issue i where i.parentId = ?1 and i.deletedAt is null")
    long countChildren(UUID parentId);

    /** 여러 일감의 하위 수 — [상위 id, 수] */
    @Query("select i.parentId, count(i) from Issue i where i.parentId in ?1 and i.deletedAt is null group by i.parentId")
    List<Object[]> countChildrenOf(java.util.Collection<UUID> parentIds);

    @Query("select count(i) from Issue i where i.projectId = ?1 and i.deletedAt is null")
    long countLive(UUID projectId);

    /** 이 프로젝트들에서 쓰인 에이전트 세션 이름과 일감 수 — [이름, 수] (지운 일감 빼고, 이름순) */
    @Query("select i.agentSession, count(i) from Issue i where i.projectId in ?1 and i.deletedAt is null"
        + " and i.agentSession is not null group by i.agentSession order by lower(i.agentSession), i.agentSession")
    List<Object[]> agentSessions(java.util.Collection<UUID> projectIds);
  }

  public interface Links extends JpaRepository<IssueLink, UUID> {

    @Query("select l from IssueLink l where l.sourceIssueId = ?1 or l.targetIssueId = ?1 order by l.createdAt")
    List<IssueLink> findTouching(UUID issueId);

    @Query("select count(l) > 0 from IssueLink l where (l.sourceIssueId = ?1 and l.targetIssueId = ?2)"
        + " or (l.sourceIssueId = ?2 and l.targetIssueId = ?1)")
    boolean existsPair(UUID a, UUID b);
  }

  public interface Journals extends JpaRepository<IssueJournal, UUID> {
    List<IssueJournal> findByIssueIdOrderByCreatedAtAscIdAsc(UUID issueId);

    /** 그 일감의 댓글(지운 것 포함) — 오래된 것부터 한 쪽씩 */
    @Query("select j from IssueJournal j where j.issueId = ?1 and j.notes is not null order by j.createdAt asc, j.id asc")
    org.springframework.data.domain.Page<IssueJournal> findComments(UUID issueId, org.springframework.data.domain.Pageable page);

    /** 댓글을 고치거나 지울 때 — 같은 댓글을 동시에 바꾸지 못하게 줄을 잠근다 */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from IssueJournal j where j.id = ?1")
    Optional<IssueJournal> lockById(UUID id);
  }

  /** 일감의 관련 모듈 (2026-11-23, 일감 #27) */
  public interface IssueModules extends JpaRepository<IssueModule, IssueModule.Key> {

    @Query("select m from IssueModule m where m.issueId = ?1")
    List<IssueModule> findByIssue(UUID issueId);

    @Query("select m from IssueModule m where m.issueId in ?1")
    List<IssueModule> findByIssues(java.util.Collection<UUID> issueIds);

    @org.springframework.data.jpa.repository.Modifying
    @Query("delete from IssueModule m where m.issueId = ?1 and m.moduleId in ?2")
    int deleteLinks(UUID issueId, java.util.Collection<UUID> moduleIds);

    /** 옮기기 — 옮길 일감들의 모듈 연결을 먼저 모두 뺀다(일감 · 모듈이 같은 프로젝트여야 하는 FK). 옮긴 뒤 새 프로젝트 모듈로 다시 넣는다 */
    @org.springframework.data.jpa.repository.Modifying
    @Query("delete from IssueModule m where m.issueId in ?1")
    int deleteAllOf(java.util.Collection<UUID> issueIds);
  }

  /** 바뀌기 전 댓글 글 — 쌓기만 한다 */
  public interface CommentRevisions extends JpaRepository<IssueCommentRevision, UUID> {
    List<IssueCommentRevision> findByJournalIdOrderByRowVersionAsc(UUID journalId);
  }

  public interface Revisions extends JpaRepository<IssueRevision, UUID> {

    java.util.Optional<IssueRevision> findByIssueIdAndRevisionNo(UUID issueId, int revisionNo);

    /** 새 판부터 — before 가 있으면 그 번호보다 앞선 판부터 */
    @Query("select r from IssueRevision r where r.issueId = ?1 and r.revisionNo < ?2 order by r.revisionNo desc")
    List<IssueRevision> findBefore(UUID issueId, int before, org.springframework.data.domain.Pageable page);
  }

  public interface WorkLogs extends JpaRepository<IssueWorkLog, UUID> {

    @Query("select w from IssueWorkLog w where w.issueId = ?1 and w.deletedAt is null order by w.workDate desc, w.createdAt desc")
    List<IssueWorkLog> findLive(UUID issueId);

    @Query("select coalesce(sum(w.hours), 0) from IssueWorkLog w where w.issueId = ?1 and w.deletedAt is null")
    java.math.BigDecimal spent(UUID issueId);

    /** 여러 일감의 소요 시간 합 — [일감 id, 합] */
    @Query("select w.issueId, sum(w.hours) from IssueWorkLog w where w.issueId in ?1 and w.deletedAt is null group by w.issueId")
    List<Object[]> spentOf(java.util.Collection<UUID> issueIds);
  }

  /** 첨부파일 — 지운 것은 목록에서 뺀다 */
  /** 유형별 일감 템플릿 — 워크스페이스 기본(project_id 없음) · 프로젝트 덮어쓰기 */
  public interface Templates extends JpaRepository<IssueTemplate, UUID> {
    @Query("select t from IssueTemplate t where t.workspaceId = ?1 and t.projectId is null")
    List<IssueTemplate> findWorkspaceDefaults(UUID workspaceId);

    @Query("select t from IssueTemplate t where t.projectId = ?1")
    List<IssueTemplate> findProjectOverrides(UUID projectId);
  }

  public interface Attachments extends JpaRepository<IssueAttachment, UUID> {

    @Query("select a from IssueAttachment a where a.issueId = ?1 and a.deletedAt is null order by a.createdAt asc, a.id asc")
    List<IssueAttachment> findLive(UUID issueId);

    /** 워크스페이스가 지금 쓰는 첨부 용량(지운 것 제외) */
    @Query("select coalesce(sum(a.sizeBytes), 0) from IssueAttachment a where a.workspaceId = ?1 and a.deletedAt is null")
    long usedBytes(UUID workspaceId);
  }
}
