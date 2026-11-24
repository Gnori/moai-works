package com.moai.works.api.skill;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

/** 스킬 · 스킬 리비전 저장소 */
public final class SkillRepositories {

  private SkillRepositories() {
  }

  public interface Skills extends JpaRepository<Skill, UUID> {

    Optional<Skill> findByName(String name);

    /** 쓰기 — 행을 잠근다(같은 스킬을 동시에 고치면 차례로, 뒤의 것은 baseRevisionNo 가 낡아 409 SKILL_CHANGED) */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Skill s where s.name = ?1")
    Optional<Skill> findForUpdate(String name);

    /** 쓰는 스킬 — 이름 순 */
    @Query("select s from Skill s where s.deletedAt is null order by s.name")
    List<Skill> findLive();

    /** 지운 것까지 — 이름 순 */
    @Query("select s from Skill s order by s.name")
    List<Skill> findAllByName();
  }

  public interface Revisions extends JpaRepository<SkillRevision, UUID> {

    Optional<SkillRevision> findBySkillIdAndRevisionNo(UUID skillId, int revisionNo);

    /** 새 리비전부터 — before 보다 앞선 것만 */
    @Query("select r from SkillRevision r where r.skillId = ?1 and r.revisionNo < ?2 order by r.revisionNo desc")
    List<SkillRevision> findBefore(UUID skillId, int before, Pageable page);

    /** 바뀐 점 — after 보다 크고 upTo 까지, 새 것부터(본문은 읽지 않는다) */
    @Query("select r.skillId as skillId, r.revisionNo as revisionNo, r.createdAt as createdAt, r.actorName as actorName,"
        + " r.kind as kind, r.restoredFrom as restoredFrom, r.changeNote as changeNote from SkillRevision r"
        + " where r.skillId = ?1 and r.revisionNo > ?2 and r.revisionNo <= ?3 order by r.revisionNo desc")
    List<ChangeRow> findChanges(UUID skillId, int after, int upTo, Pageable page);

    /** 스킬마다 지금 리비전의 바뀐 점(본문은 읽지 않는다) */
    @Query("select r.skillId as skillId, r.revisionNo as revisionNo, r.createdAt as createdAt, r.actorName as actorName,"
        + " r.kind as kind, r.restoredFrom as restoredFrom, r.changeNote as changeNote from SkillRevision r, Skill s"
        + " where s.id = r.skillId and r.revisionNo = s.revisionNo and s.id in ?1")
    List<ChangeRow> findLatestChanges(Collection<UUID> skillIds);
  }

  /** 리비전의 바뀐 점만 — 목록 · 바뀐 점에서 본문(10만 자까지)을 읽지 않으려고 */
  public interface ChangeRow {

    UUID getSkillId();

    int getRevisionNo();

    Instant getCreatedAt();

    String getActorName();

    String getKind();

    Integer getRestoredFrom();

    String getChangeNote();
  }
}
