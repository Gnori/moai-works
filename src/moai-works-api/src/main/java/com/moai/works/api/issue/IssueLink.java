package com.moai.works.api.issue;

import java.time.Instant;
import java.util.UUID;

import com.moai.works.api.common.Ids;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 연결 일감 — 같은 두 일감 사이에 하나(방향과 상관없이). 양쪽 일감에서 보인다 (design/05) */
@Entity
@Table(name = "issue_link")
public class IssueLink {

  public enum Type {
    /** 관련 — 방향이 없다 */
    RELATES,
    /** 차단 — source 가 끝나야 target 을 할 수 있다 */
    BLOCKS,
    /** 선행 — source 다음에 target */
    PRECEDES,
    /** 중복 — source 가 target 과 같은 일이다 */
    DUPLICATES
  }

  @Id
  private UUID id;

  @Column(name = "source_issue_id", nullable = false)
  private UUID sourceIssueId;

  @Column(name = "target_issue_id", nullable = false)
  private UUID targetIssueId;

  @Enumerated(EnumType.STRING)
  @Column(name = "link_type", nullable = false, length = 20)
  private Type type;

  @Column(name = "created_by", nullable = false)
  private UUID createdBy;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected IssueLink() {
  }

  public IssueLink(UUID sourceIssueId, UUID targetIssueId, Type type, UUID createdBy, Instant now) {
    this.id = Ids.next();
    this.sourceIssueId = sourceIssueId;
    this.targetIssueId = targetIssueId;
    this.type = type;
    this.createdBy = createdBy;
    this.createdAt = now;
  }

  public UUID getId() {
    return id;
  }

  public UUID getSourceIssueId() {
    return sourceIssueId;
  }

  public UUID getTargetIssueId() {
    return targetIssueId;
  }

  public Type getType() {
    return type;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
