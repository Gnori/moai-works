package com.moai.works.api.notification;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 아침 알림 — 매일 09:00(Asia/Seoul) 한 번 (design/07).
 *
 * <ul>
 *   <li>완료 기한이 내일인 열린 일감(등록 · 진행 · 테스트) → 담당자 · 관리자 ({@code issue.due_soon})</li>
 *   <li>완료 기한이 지난 열린 일감(지난 7일 안) → 담당자 · 관리자 ({@code issue.overdue}) — 기한마다 한 번</li>
 *   <li>7일 안에 만료되는 MCP 키 → 키 주인 ({@code mcp_key.expiring}) — 키마다 한 번</li>
 * </ul>
 *
 * <p>같은 대상 · 같은 값(기한 날짜 · 만료 시각)으로는 두 번 보내지 않는다. 여러 인스턴스가 함께 돌아도
 * DB 권고 잠금으로 한 곳만 돈다. 사용 중지된 워크스페이스 · 보관한 프로젝트 · 지운 일감은 건너뛴다.
 */
@Component
public class NotificationReminders {

  private static final Logger log = LoggerFactory.getLogger(NotificationReminders.class);
  static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
  private static final int OVERDUE_LOOKBACK_DAYS = 7;
  private static final Duration KEY_EXPIRING = Duration.ofDays(7);

  private final NamedParameterJdbcTemplate jdbc;
  private final NotificationService notifications;
  private final Clock clock;

  public NotificationReminders(NamedParameterJdbcTemplate jdbc, NotificationService notifications, Clock clock) {
    this.jdbc = jdbc;
    this.notifications = notifications;
    this.clock = clock;
  }

  @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Seoul")
  public void morning() {
    try {
      Result r = run();
      log.info("아침 알림: 기한 임박 {} · 기한 지남 {} · MCP 키 만료 예정 {}", r.dueSoon(), r.overdue(), r.keysExpiring());
    } catch (RuntimeException e) {
      log.error("아침 알림 실패: {}", e.getMessage(), e);
    }
  }

  /** 보낸 수 */
  public record Result(int dueSoon, int overdue, int keysExpiring) {
  }

  /** 한 번 돈다(시험에서도 부른다). 다른 인스턴스가 돌고 있으면 아무것도 하지 않는다 */
  @Transactional
  public Result run() {
    Boolean locked = jdbc.queryForObject("SELECT pg_try_advisory_xact_lock(hashtext('moai-works:notification-reminders'))",
        new MapSqlParameterSource(), Boolean.class);
    if (!Boolean.TRUE.equals(locked)) {
      return new Result(0, 0, 0);
    }
    LocalDate today = LocalDate.now(clock.withZone(ZONE));
    int dueSoon = issues(NotificationKind.ISSUE_DUE_SOON, "i.due_date = :tomorrow", today);
    int overdue = issues(NotificationKind.ISSUE_OVERDUE, "i.due_date < :today AND i.due_date >= :lookback", today);
    return new Result(dueSoon, overdue, keys());
  }

  private int issues(NotificationKind kind, String condition, LocalDate today) {
    MapSqlParameterSource p = new MapSqlParameterSource("today", Date.valueOf(today))
        .addValue("tomorrow", Date.valueOf(today.plusDays(1)))
        .addValue("lookback", Date.valueOf(today.minusDays(OVERDUE_LOOKBACK_DAYS)));
    int[] sent = {0};
    jdbc.query("""
        SELECT i.id, i.number, i.title, i.due_date, i.manager_id, i.assignee_id, p.key, p.name, p.workspace_id
        FROM issue i
        JOIN project p ON p.id = i.project_id
        JOIN workspace w ON w.id = p.workspace_id
        WHERE i.deleted_at IS NULL AND i.status IN ('NEW', 'IN_PROGRESS', 'TESTING') AND p.status = 'ACTIVE' AND w.status = 'ACTIVE'
        """ + " AND " + condition, p, rs -> {
      UUID issueId = rs.getObject("id", UUID.class);
      String due = rs.getDate("due_date").toLocalDate().toString();
      Map<String, Object> params = NotificationService.aboutIssue(rs.getString("key"), rs.getString("name"),
          rs.getInt("number"), rs.getString("title"), "SYSTEM");
      params.put("dueDate", due);
      Set<UUID> to = new LinkedHashSet<>();
      UUID assignee = rs.getObject("assignee_id", UUID.class);
      if (assignee != null) {
        to.add(assignee);
      }
      to.add(rs.getObject("manager_id", UUID.class));
      for (UUID recipient : to) {
        if (!notifications.alreadySent(recipient, kind, issueId, "dueDate", due)) {
          notifications.notify(recipient, rs.getObject("workspace_id", UUID.class), kind, issueId, params, null);
          sent[0]++;
        }
      }
    });
    return sent[0];
  }

  private int keys() {
    Instant now = Instant.now(clock);
    int[] sent = {0};
    jdbc.query("""
        SELECT k.id, k.name, k.key_prefix, k.expires_at, k.owner_user_id
        FROM mcp_api_key k
        WHERE k.expire_reason IS NULL AND k.expires_at > :now AND k.expires_at <= :until
        """, new MapSqlParameterSource("now", Timestamp.from(now)).addValue("until", Timestamp.from(now.plus(KEY_EXPIRING))),
        rs -> {
          UUID keyId = rs.getObject("id", UUID.class);
          UUID owner = rs.getObject("owner_user_id", UUID.class);
          String expiresAt = rs.getTimestamp("expires_at").toInstant().toString();
          if (notifications.alreadySent(owner, NotificationKind.MCP_KEY_EXPIRING, keyId, "expiresAt", expiresAt)) {
            return;
          }
          Map<String, Object> params = new LinkedHashMap<>();
          params.put("name", rs.getString("name"));
          params.put("keyPrefix", rs.getString("key_prefix"));
          params.put("expiresAt", expiresAt);
          // 키는 사용자의 것이라(ADR-029) 워크스페이스에 속하지 않는 알림이다
          notifications.notify(owner, null, NotificationKind.MCP_KEY_EXPIRING, keyId, params, null);
          sent[0]++;
        });
    return sent[0];
  }
}
