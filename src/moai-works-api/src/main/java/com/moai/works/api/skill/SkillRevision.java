package com.moai.works.api.skill;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import com.moai.works.api.common.Ids;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 스킬의 한 리비전 — 그때의 전체(이름 · 설명 · 본문)와 누가 · 어느 길로 · 왜 바꿨는지. 추가만 한다(DB 트리거가 고치기 · 지우기를 막는다).
 * 일감 리비전(issue_revision)과 같은 모양이다.
 */
@Entity
@Table(name = "skill_revision")
public class SkillRevision {

  /** 리비전을 만든 일 */
  public enum Kind {
    /** 새로 만듦 */
    CREATE,
    /** 화면에서 고침 */
    UPDATE,
    /** SKILL.md 를 가져와 만들거나 고침 */
    IMPORT,
    /** 옛 리비전의 내용으로 되돌림(그 내용으로 새 리비전) */
    RESTORE
  }

  /** 바꾼 길 — REST(화면) · MCP · 바깥 자동화 */
  public enum Via {
    WEB,
    MCP,
    API
  }

  @Id
  private UUID id;

  @Column(name = "skill_id", nullable = false)
  private UUID skillId;

  @Column(name = "revision_no", nullable = false)
  private int revisionNo;

  @Column(nullable = false, length = 64)
  private String name;

  @Column(nullable = false, length = 1024)
  private String description;

  @Column(nullable = false, columnDefinition = "text")
  private String body;

  @Column(nullable = false, length = 10)
  private String kind;

  @Column(name = "restored_from")
  private Integer restoredFrom;

  @Column(name = "changed_fields", nullable = false, length = 100)
  private String changedFields;

  @Column(name = "change_note", length = 500)
  private String changeNote;

  @Column(name = "actor_id")
  private UUID actorId;

  @Column(name = "actor_type", nullable = false, length = 20)
  private String actorType;

  @Column(name = "actor_name", nullable = false, length = 200)
  private String actorName;

  @Column(nullable = false, length = 10)
  private String via;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected SkillRevision() {
  }

  /** 지금 스킬의 모습을 한 리비전으로 담는다 */
  static SkillRevision of(Skill s, Kind kind, Integer restoredFrom, List<String> changedFields, String changeNote,
      UUID actorId, String actorType, String actorName, Via via, Instant now) {
    SkillRevision r = new SkillRevision();
    r.id = Ids.next();
    r.skillId = s.getId();
    r.revisionNo = s.getRevisionNo();
    r.name = s.getName();
    r.description = s.getDescription();
    r.body = s.getBody();
    r.kind = kind.name();
    r.restoredFrom = restoredFrom;
    r.changedFields = String.join(",", changedFields);
    r.changeNote = changeNote;
    r.actorId = actorId;
    r.actorType = actorType;
    r.actorName = actorName;
    r.via = via.name();
    r.createdAt = now;
    return r;
  }

  public UUID getId() {
    return id;
  }

  public UUID getSkillId() {
    return skillId;
  }

  public int getRevisionNo() {
    return revisionNo;
  }

  public String getName() {
    return name;
  }

  public String getDescription() {
    return description;
  }

  public String getBody() {
    return body;
  }

  public String getKind() {
    return kind;
  }

  /** RESTORE 면 되돌린 리비전 번호 */
  public Integer getRestoredFrom() {
    return restoredFrom;
  }

  /** 바로 앞 리비전과 견줘 바뀐 것 — description · body (1리비전은 둘 다) */
  public List<String> getChangedFields() {
    return changedFields == null || changedFields.isBlank() ? List.of() : Arrays.asList(changedFields.split(","));
  }

  public String getChangeNote() {
    return changeNote;
  }

  public UUID getActorId() {
    return actorId;
  }

  public String getActorType() {
    return actorType;
  }

  public String getActorName() {
    return actorName;
  }

  public String getVia() {
    return via;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
