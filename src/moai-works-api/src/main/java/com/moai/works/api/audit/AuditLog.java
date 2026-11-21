package com.moai.works.api.audit;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.ColumnTransformer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 감사 기록 한 줄 — 추가만 한다(DB 트리거가 UPDATE · DELETE 를 막는다). */
@Entity
@Table(name = "audit_log")
public class AuditLog {

  @Id
  private UUID id;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;

  @Column(name = "actor_type", nullable = false, length = 20)
  private String actorType;

  @Column(name = "actor_id")
  private UUID actorId;

  @Column(name = "actor_name")
  private String actorName;

  @Column(name = "workspace_id")
  private UUID workspaceId;

  @Column(nullable = false, length = 100)
  private String action;

  @Column(name = "target_type", length = 50)
  private String targetType;

  @Column(name = "target_id", length = 100)
  private String targetId;

  @Column(nullable = false, columnDefinition = "jsonb")
  @ColumnTransformer(write = "?::jsonb")
  private String details;

  @Column(length = 64)
  private String ip;

  @Column(name = "trace_id", length = 64)
  private String traceId;

  protected AuditLog() {
  }

  AuditLog(Instant at, String actorType, UUID actorId, String actorName, UUID workspaceId, String action,
      String targetType, String targetId, String details, String ip) {
    this.id = UUID.randomUUID();
    this.occurredAt = at;
    this.actorType = actorType;
    this.actorId = actorId;
    this.actorName = actorName;
    this.workspaceId = workspaceId;
    this.action = action;
    this.targetType = targetType;
    this.targetId = targetId;
    this.details = details;
    this.ip = ip;
  }

  public UUID getId() {
    return id;
  }

  public Instant getOccurredAt() {
    return occurredAt;
  }

  public String getActorType() {
    return actorType;
  }

  public UUID getActorId() {
    return actorId;
  }

  public String getActorName() {
    return actorName;
  }

  public UUID getWorkspaceId() {
    return workspaceId;
  }

  public String getAction() {
    return action;
  }

  public String getTargetType() {
    return targetType;
  }

  public String getTargetId() {
    return targetId;
  }

  public String getDetails() {
    return details;
  }
}
