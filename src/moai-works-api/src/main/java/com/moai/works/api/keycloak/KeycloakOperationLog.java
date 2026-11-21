package com.moai.works.api.keycloak;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Keycloak 쓰기를 기록하며 실행한다 — "Keycloak 먼저, 사본은 그다음" 규칙(design/03 §6)의 바탕.
 *
 * <p>{@link #run} 은 같은 멱등 키가 이미 DONE 이면 Keycloak 을 다시 부르지 않고 저장된 결과를 돌려준다.
 * 실패하면 FAILED 로 남기고 예외를 그대로 던진다 — 다음 시도(같은 키)가 이어서 한다.
 * 기록은 매번 별도 트랜잭션(REQUIRES_NEW)으로 남긴다: 바깥 업무 트랜잭션이 되돌려져도 "Keycloak 에 무엇을 썼는지"는 남아야 한다.
 */
@Component
public class KeycloakOperationLog {

  private final Repository operations;
  private final TransactionTemplate newTx;
  private final Clock clock;

  public KeycloakOperationLog(Repository operations, PlatformTransactionManager tx, Clock clock) {
    this.operations = operations;
    this.newTx = new TransactionTemplate(tx);
    this.newTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    this.clock = clock;
  }

  /**
   * @param sagaId 업무 묶음 id
   * @param kind   종류 (CREATE_ORG, JOIN_GROUP …)
   * @param key    멱등 키 — 같은 키는 한 번만 성공한다
   * @param payload 기록용 JSON (비밀값 넣지 말 것)
   * @param action Keycloak 호출. 결과 문자열(예: 만든 id)을 돌려준다
   * @return 결과 문자열 (이미 끝났으면 저장된 값)
   */
  public String run(UUID sagaId, String kind, String key, String payload, Supplier<String> action) {
    Optional<String> already = newTx.execute(s -> {
      KeycloakOperation op = operations.findByIdempotencyKey(key)
          .orElseGet(() -> operations.save(new KeycloakOperation(sagaId, kind, key, payload, Instant.now(clock))));
      if (op.isDone()) {
        return Optional.ofNullable(unquote(op.getResult()));
      }
      op.attempt();
      return Optional.<String>empty();
    });
    if (already != null && already.isPresent()) {
      return already.get();
    }
    try {
      String result = action.get();
      newTx.executeWithoutResult(s -> operations.findByIdempotencyKey(key)
          .ifPresent(op -> op.done(quote(result), Instant.now(clock))));
      return result;
    } catch (RuntimeException e) {
      newTx.executeWithoutResult(s -> operations.findByIdempotencyKey(key).ifPresent(op -> op.failed(e.getMessage())));
      throw e;
    }
  }

  /** 결과가 필요 없는 쓰기 */
  public void run(UUID sagaId, String kind, String key, String payload, Runnable action) {
    run(sagaId, kind, key, payload, () -> {
      action.run();
      return "";
    });
  }

  private static String quote(String value) {
    return value == null ? null : "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
  }

  private static String unquote(String json) {
    if (json == null || json.length() < 2) {
      return json;
    }
    return json.substring(1, json.length() - 1).replace("\\\"", "\"").replace("\\\\", "\\");
  }

  public interface Repository extends JpaRepository<KeycloakOperation, UUID> {
    Optional<KeycloakOperation> findByIdempotencyKey(String key);
  }
}
