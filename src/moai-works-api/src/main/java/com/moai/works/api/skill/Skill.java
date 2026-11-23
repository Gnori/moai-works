package com.moai.works.api.skill;

import java.time.Instant;
import java.util.UUID;

import com.moai.works.api.common.Ids;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Agent 스킬 하나 — 시스템 전체의 것(워크스페이스에 묶이지 않는다). 바깥 Agent 가 지시로 읽는 글이라 시스템 관리자만 쓴다
 * (2026-11-24 사용자: "moai-works 스킬은 시스템 관리자가 등록, 수정 삭제 할 수 있게 하고 역시 리비전 관리 해줘").
 * 이름은 만든 뒤 바꾸지 않고(DB 트리거도 막는다), 지우기는 지움 표시만 한다.
 */
@Entity
@Table(name = "skill")
public class Skill {

  @Id
  private UUID id;

  @Column(nullable = false, length = 64, updatable = false)
  private String name;

  @Column(nullable = false, length = 1024)
  private String description;

  @Column(nullable = false, columnDefinition = "text")
  private String body;

  @Column(name = "revision_no", nullable = false)
  private int revisionNo;

  @Version
  @Column(name = "row_version", nullable = false)
  private int rowVersion;

  @Column(name = "created_by", updatable = false)
  private UUID createdBy;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_by")
  private UUID updatedBy;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "deleted_at")
  private Instant deletedAt;

  @Column(name = "deleted_by")
  private UUID deletedBy;

  protected Skill() {
  }

  Skill(String name, String description, String body, UUID by, Instant now) {
    this.id = Ids.next();
    this.name = name;
    this.description = description;
    this.body = body;
    this.revisionNo = 0;
    this.createdBy = by;
    this.createdAt = now;
    this.updatedBy = by;
    this.updatedAt = now;
  }

  /** 내용을 바꾸고 다음 리비전 번호를 돌려준다 */
  int change(String description, String body, UUID by, Instant now) {
    this.description = description;
    this.body = body;
    this.updatedBy = by;
    this.updatedAt = now;
    this.revisionNo += 1;
    return revisionNo;
  }

  void markDeleted(UUID by, Instant now) {
    this.deletedAt = now;
    this.deletedBy = by;
  }

  void restore() {
    this.deletedAt = null;
    this.deletedBy = null;
  }

  public boolean isDeleted() {
    return deletedAt != null;
  }

  public UUID getId() {
    return id;
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

  public int getRevisionNo() {
    return revisionNo;
  }

  public int getRowVersion() {
    return rowVersion;
  }

  public UUID getCreatedBy() {
    return createdBy;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public UUID getUpdatedBy() {
    return updatedBy;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public Instant getDeletedAt() {
    return deletedAt;
  }

  public UUID getDeletedBy() {
    return deletedBy;
  }
}
