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

    @Query("select i from Issue i where i.projectId = ?1 and i.number = ?2 and i.deletedAt is null")
    Optional<Issue> findLive(UUID projectId, int number);

    @Query("select i from Issue i where i.parentId = ?1 and i.deletedAt is null order by i.number")
    List<Issue> findChildren(UUID parentId);

    @Query("select count(i) from Issue i where i.parentId = ?1 and i.deletedAt is null")
    long countChildren(UUID parentId);

    /** 여러 일감의 하위 수 — [상위 id, 수] */
    @Query("select i.parentId, count(i) from Issue i where i.parentId in ?1 and i.deletedAt is null group by i.parentId")
    List<Object[]> countChildrenOf(java.util.Collection<UUID> parentIds);

    @Query("select count(i) from Issue i where i.projectId = ?1 and i.deletedAt is null")
    long countLive(UUID projectId);
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

    /** 여러 일감의 쓴 시간 합 — [일감 id, 합] */
    @Query("select w.issueId, sum(w.hours) from IssueWorkLog w where w.issueId in ?1 and w.deletedAt is null group by w.issueId")
    List<Object[]> spentOf(java.util.Collection<UUID> issueIds);
  }
}
