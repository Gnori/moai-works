package com.moai.works.api.issue;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.moai.works.api.audit.AuditService;
import com.moai.works.api.common.Actor;
import com.moai.works.api.config.MoaiWorksProperties;
import com.moai.works.api.storage.ObjectStore;

/**
 * 지운 첨부의 객체 정리 (moai-works 일감 #18 — 일감 설계 "객체는 남는다(나중에 정리 작업이 일정 기간 뒤 지운다)").
 *
 * <ul>
 *   <li>지움 표시 뒤 {@code moai-works.storage.purge-after-days}(기본 30일)가 지난 첨부만 — 기간 안의 것 · 지우지 않은 것은 그대로</li>
 *   <li>저장소에서 그 첨부의 객체를 <b>버전 보관분 · 지움 표시까지</b> 지운다. 접두사 = 원본 객체 이름이라 원본과 그 옆의
 *       미리보기({@code <원본>.preview})가 함께 지워진다. 첨부 id 가 UUID 라 다른 첨부의 객체는 이 접두사에 걸리지 않는다</li>
 *   <li>DB 행은 지우지 않고 정리한 때(purged_at)를 한 번 남긴다(V17 보호 트리거가 그것만 허락한다). 워크스페이스 용량은 지운 첨부를
 *       이미 세지 않으므로 그대로다</li>
 *   <li>결과는 워크스페이스마다 감사 한 줄({@code issue.attachment.purge}, 행위자 SYSTEM "첨부 정리")과 로그 한 줄로 남긴다.
 *       표시와 감사는 같은 트랜잭션이다 — 객체를 지운 뒤 표시를 못 했으면 다음 차례에 다시 지우고(이미 없으면 0개) 표시한다</li>
 *   <li>저장소가 응답하지 않으면 남은 것은 다음 차례로 미룬다. 여러 인스턴스가 함께 돌아도 DB 권고 잠금으로 한 곳만 돈다</li>
 * </ul>
 */
@Component
public class AttachmentPurger {

  private static final Logger log = LoggerFactory.getLogger(AttachmentPurger.class);
  /** 한 번에 정리하는 첨부 수 — 남은 것은 다음 차례에 */
  static final int BATCH = 1000;
  /** 저장소 실패가 이만큼 이어지면 이번 차례를 멈춘다(저장소가 내려간 것으로 본다) */
  private static final int MAX_FAILURES_IN_ROW = 3;
  public static final String ACTION = "issue.attachment.purge";
  private static final Actor SYSTEM = Actor.system("첨부 정리");

  private final NamedParameterJdbcTemplate jdbc;
  private final ObjectStore store;
  private final AuditService audit;
  /** 잠금을 잡고 차례를 고르는 바깥 트랜잭션 */
  private final TransactionTemplate outer;
  /** 워크스페이스마다 따로 커밋하는 표시 · 감사 */
  private final TransactionTemplate each;
  private final int purgeAfterDays;
  private final Clock clock;

  public AttachmentPurger(NamedParameterJdbcTemplate jdbc, ObjectStore store, AuditService audit,
      PlatformTransactionManager txManager, MoaiWorksProperties properties, Clock clock) {
    this.jdbc = jdbc;
    this.store = store;
    this.audit = audit;
    this.outer = new TransactionTemplate(txManager);
    this.each = new TransactionTemplate(txManager);
    this.each.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    this.purgeAfterDays = properties.storage() == null ? 0 : properties.storage().purgeAfterDays();
    this.clock = clock;
  }

  /** 정리한 결과 — more 는 이번 차례에 다 못 해 남은 것이 있다 */
  public record Result(int attachments, int objects, long bytes, int workspaces, int failed, boolean more) {
    static final Result NONE = new Result(0, 0, 0, 0, 0, false);
  }

  private record Row(UUID id, UUID workspaceId, String storageKey, long size) {
  }

  @Scheduled(cron = "${moai-works.storage.purge-cron:0 30 3 * * *}", zone = "Asia/Seoul")
  public void nightly() {
    if (purgeAfterDays <= 0) {
      return;
    }
    try {
      Result r = run(Instant.now(clock));
      log.info("첨부 정리: 첨부 {} · 객체 {} · {} 바이트 · 워크스페이스 {} · 실패 {}{} (지운 지 {}일 지난 것)", r.attachments(),
          r.objects(), r.bytes(), r.workspaces(), r.failed(), r.more() ? " · 남은 것은 다음 차례" : "", purgeAfterDays);
    } catch (RuntimeException e) {
      log.error("첨부 정리 실패: {}", e.getMessage(), e);
    }
  }

  /**
   * 한 번 돈다 — {@code now} 에서 보관 기간을 뺀 때보다 먼저 지운 것만(시험은 앞날을 넘겨 부른다).
   * 다른 인스턴스가 돌고 있거나 정리를 껐으면 아무것도 하지 않는다.
   */
  public Result run(Instant now) {
    if (purgeAfterDays <= 0) {
      return Result.NONE;
    }
    Instant cutoff = now.minus(Duration.ofDays(purgeAfterDays));
    return outer.execute(status -> {
      Boolean locked = jdbc.queryForObject("SELECT pg_try_advisory_xact_lock(hashtext('moai-works:attachment-purge'))",
          new MapSqlParameterSource(), Boolean.class);
      if (!Boolean.TRUE.equals(locked)) {
        return Result.NONE;
      }
      List<Row> rows = jdbc.query("""
          SELECT id, workspace_id, storage_key, size_bytes FROM issue_attachment
          WHERE deleted_at IS NOT NULL AND purged_at IS NULL AND deleted_at < :cutoff
          ORDER BY deleted_at, id
          LIMIT :limit
          """, new MapSqlParameterSource("cutoff", Timestamp.from(cutoff)).addValue("limit", BATCH + 1),
          (rs, i) -> new Row(rs.getObject("id", UUID.class), rs.getObject("workspace_id", UUID.class),
              rs.getString("storage_key"), rs.getLong("size_bytes")));
      boolean more = rows.size() > BATCH;
      Map<UUID, List<Row>> byWorkspace = new LinkedHashMap<>();
      rows.stream().limit(BATCH).forEach(r -> byWorkspace.computeIfAbsent(r.workspaceId(), k -> new ArrayList<>()).add(r));

      int attachments = 0;
      int objects = 0;
      long bytes = 0;
      int workspaces = 0;
      int failed = 0;
      int failuresInRow = 0;
      outer:
      for (Map.Entry<UUID, List<Row>> group : byWorkspace.entrySet()) {
        List<Row> done = new ArrayList<>();
        int groupObjects = 0;
        for (Row row : group.getValue()) {
          if (failuresInRow >= MAX_FAILURES_IN_ROW) {
            more = true;
            break;
          }
          try {
            groupObjects += store.deletePrefix(ObjectStore.Zone.ATTACHMENT, row.storageKey());
            done.add(row);
            failuresInRow = 0;
          } catch (RuntimeException e) {
            failed++;
            failuresInRow++;
            log.warn("첨부 정리: 객체를 지우지 못함 — 첨부 {} (다음 차례에 다시): {}", row.id(), e.getMessage());
          }
        }
        if (!done.isEmpty()) {
          int marked = mark(group.getKey(), done, groupObjects, cutoff, now);
          attachments += marked;
          objects += groupObjects;
          bytes += done.stream().mapToLong(Row::size).sum();
          workspaces++;
        }
        if (failuresInRow >= MAX_FAILURES_IN_ROW) {
          break outer;
        }
      }
      return new Result(attachments, objects, bytes, workspaces, failed, more);
    });
  }

  /** 정리한 때를 남기고 그 워크스페이스의 감사 한 줄 — 한 트랜잭션 */
  private int mark(UUID workspaceId, List<Row> done, int objects, Instant cutoff, Instant now) {
    Integer marked = each.execute(s -> {
      int n = jdbc.update("""
          UPDATE issue_attachment SET purged_at = :now
          WHERE id IN (:ids) AND deleted_at IS NOT NULL AND purged_at IS NULL
          """, new MapSqlParameterSource("now", Timestamp.from(Instant.now(clock)))
          .addValue("ids", done.stream().map(Row::id).toList()));
      Map<String, Object> details = new LinkedHashMap<>();
      details.put("attachments", n);
      details.put("bytes", done.stream().mapToLong(Row::size).sum());
      details.put("objects", objects);
      details.put("retentionDays", purgeAfterDays);
      details.put("deletedBefore", cutoff.toString());
      audit.record(SYSTEM, workspaceId, ACTION, "workspace", workspaceId.toString(), details);
      return n;
    });
    return marked == null ? 0 : marked;
  }
}
