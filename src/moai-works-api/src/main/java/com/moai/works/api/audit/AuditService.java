package com.moai.works.api.audit;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.moai.works.api.common.Actor;

import tools.jackson.databind.json.JsonMapper;

/**
 * 감사 기록. 업무 트랜잭션 안에서 함께 저장한다 — 업무가 되돌려지면 기록도 남지 않는다(일어나지 않은 일).
 * 비밀번호 · 토큰 · 인증 코드 같은 비밀값은 details 에 넣지 않는다.
 */
@Service
public class AuditService {

  private final Repository logs;
  private final JsonMapper json;
  private final Clock clock;

  public AuditService(Repository logs, JsonMapper json, Clock clock) {
    this.logs = logs;
    this.json = json;
    this.clock = clock;
  }

  @Transactional(propagation = Propagation.REQUIRED)
  public void record(Actor actor, UUID workspaceId, String action, String targetType, String targetId,
      Map<String, ?> details) {
    logs.save(new AuditLog(Instant.now(clock), actor.type().name(), actor.id(), actor.name(), workspaceId, action,
        targetType, targetId, json.writeValueAsString(details == null ? Map.of() : details), actor.ip()));
  }

  @Transactional(readOnly = true)
  public List<AuditLog> forWorkspace(UUID workspaceId, Pageable page) {
    return logs.findByWorkspaceIdOrderByOccurredAtDesc(workspaceId, page);
  }

  public interface Repository extends JpaRepository<AuditLog, UUID> {
    List<AuditLog> findByWorkspaceIdOrderByOccurredAtDesc(UUID workspaceId, Pageable page);
  }
}
