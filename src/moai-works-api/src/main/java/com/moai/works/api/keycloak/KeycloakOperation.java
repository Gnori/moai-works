package com.moai.works.api.keycloak;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.ColumnTransformer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Keycloak 쓰기 한 건의 기록 (design/03 §6). 같은 {@link #idempotencyKey} 는 한 번만 실행한다.
 * 한 업무(예: 개인 워크스페이스 만들기)의 단계들은 같은 {@link #sagaId} 로 묶인다.
 */
@Entity
@Table(name = "keycloak_operation")
public class KeycloakOperation {

  public enum Status {
    PENDING,
    DONE,
    FAILED,
    COMPENSATED
  }

  @Id
  private UUID id;

  @Column(name = "saga_id", nullable = false)
  private UUID sagaId;

  @Column(nullable = false, length = 40)
  private String kind;

  @Column(name = "idempotency_key", nullable = false, unique = true, length = 200)
  private String idempotencyKey;

  @Column(nullable = false, columnDefinition = "jsonb")
  @ColumnTransformer(write = "?::jsonb")
  private String payload;

  @Column(columnDefinition = "jsonb")
  @ColumnTransformer(write = "?::jsonb")
  private String result;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private Status status;

  @Column(nullable = false)
  private int attempts;

  @Column(name = "last_error", length = 1000)
  private String lastError;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "done_at")
  private Instant doneAt;

  protected KeycloakOperation() {
  }

  KeycloakOperation(UUID sagaId, String kind, String idempotencyKey, String payload, Instant now) {
    this.id = UUID.randomUUID();
    this.sagaId = sagaId;
    this.kind = kind;
    this.idempotencyKey = idempotencyKey;
    this.payload = payload;
    this.status = Status.PENDING;
    this.createdAt = now;
  }

  void attempt() {
    this.attempts++;
  }

  void done(String result, Instant now) {
    this.status = Status.DONE;
    this.result = result;
    this.lastError = null;
    this.doneAt = now;
  }

  void failed(String error) {
    this.status = Status.FAILED;
    this.lastError = error == null ? null : error.substring(0, Math.min(error.length(), 1000));
  }

  public boolean isDone() {
    return status == Status.DONE;
  }

  public String getResult() {
    return result;
  }

  public String getIdempotencyKey() {
    return idempotencyKey;
  }
}
