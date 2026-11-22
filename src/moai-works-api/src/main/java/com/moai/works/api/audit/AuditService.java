package com.moai.works.api.audit;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
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
  private final NamedParameterJdbcTemplate jdbc;
  private final JsonMapper json;
  private final Clock clock;

  public AuditService(Repository logs, NamedParameterJdbcTemplate jdbc, JsonMapper json, Clock clock) {
    this.logs = logs;
    this.jdbc = jdbc;
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

  /**
   * 감사 기록 거르기 — 모두 비우면 최근 것부터.
   *
   * @param person     그 사람이 한 일 · 그 사람이 대상인 일 · 그 사람의 MCP 키에 일어난 일(details.owner)
   * @param action     행동 이름이나 묶음 앞부분(예: {@code mcp.key} 면 mcp.key.issue · mcp.key.expire)
   * @param targetType 대상 종류(예: mcp_key · issue · user)
   * @param from       이 시각부터 · {@code to} 이 시각 전까지 · {@code before} 더 보기 커서(이 시각보다 앞선 것)
   */
  public record Filter(UUID person, String action, String targetType, Instant from, Instant to, Instant before) {
  }

  @Transactional(readOnly = true)
  public List<AuditLog> search(UUID workspaceId, Filter f, int limit) {
    StringBuilder where = new StringBuilder("workspace_id = :ws");
    MapSqlParameterSource p = new MapSqlParameterSource("ws", workspaceId);
    if (f.person() != null) {
      where.append(" AND (actor_id = :person OR target_id = :personText OR details ->> 'owner' = :personText)");
      p.addValue("person", f.person()).addValue("personText", f.person().toString());
    }
    if (f.action() != null && !f.action().isBlank()) {
      where.append(" AND (action = :action OR action LIKE :actionPrefix)");
      String a = f.action().trim();
      p.addValue("action", a).addValue("actionPrefix", a.replace("%", "").replace("_", "\\_") + ".%");
    }
    if (f.targetType() != null && !f.targetType().isBlank()) {
      where.append(" AND target_type = :targetType");
      p.addValue("targetType", f.targetType().trim());
    }
    if (f.from() != null) {
      where.append(" AND occurred_at >= :from");
      p.addValue("from", java.sql.Timestamp.from(f.from()));
    }
    if (f.to() != null) {
      where.append(" AND occurred_at < :to");
      p.addValue("to", java.sql.Timestamp.from(f.to()));
    }
    if (f.before() != null) {
      where.append(" AND occurred_at < :before");
      p.addValue("before", java.sql.Timestamp.from(f.before()));
    }
    p.addValue("limit", Math.min(Math.max(limit, 1), 200));
    List<UUID> ids = jdbc.queryForList("SELECT id FROM audit_log WHERE " + where
        + " ORDER BY occurred_at DESC, id DESC LIMIT :limit", p, UUID.class);
    Map<UUID, AuditLog> byId = new java.util.HashMap<>();
    logs.findAllById(ids).forEach(a -> byId.put(a.getId(), a));
    return ids.stream().map(byId::get).filter(java.util.Objects::nonNull).toList();
  }

  public interface Repository extends JpaRepository<AuditLog, UUID> {
    List<AuditLog> findByWorkspaceIdOrderByOccurredAtDesc(UUID workspaceId, Pageable page);
  }
}
