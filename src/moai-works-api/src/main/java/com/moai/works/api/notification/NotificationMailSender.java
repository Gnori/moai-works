package com.moai.works.api.notification;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.moai.works.api.account.Mailer;
import com.moai.works.api.config.MoaiWorksProperties;
import com.moai.works.api.project.Project;
import com.moai.works.api.project.ProjectAccessService;
import com.moai.works.api.project.ProjectRepository;
import com.moai.works.api.security.access.WorkspaceContext;
import com.moai.works.api.workspace.Permission;
import com.moai.works.api.workspace.Workspace;
import com.moai.works.api.workspace.WorkspaceAccessService;
import com.moai.works.api.workspace.WorkspaceRepositories;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * 알림 메일 보내기 (FR-NOTI-006) — 보낼 통({@code notification_mail}, Flyway V18)을 정해진 주기로 살펴 한 줄씩 보낸다.
 *
 * <ul>
 *   <li><b>한 번만</b> — 줄 하나를 {@code FOR UPDATE SKIP LOCKED} 로 잠그고, 다시 판정 → 메일 → 상태 적기를 한 트랜잭션에서 한다.
 *       여러 인스턴스 · 스레드가 함께 돌아도 한 줄은 한 곳만 보낸다. 보내기 전에 죽으면 줄은 PENDING 그대로라 다음에 보낸다(잃지 않음).
 *       메일 서버가 받은 뒤 상태를 적기 전의 아주 짧은 틈에 죽으면 한 번 더 갈 수 있다 — 그래서 Message-ID 를 줄 id 로 정해 받는 쪽이
 *       같은 메일로 보게 한다</li>
 *   <li><b>보낼 때 다시 판정</b> — 받는 사람이 지금도 활성 계정 · 활성 멤버이고 그 대상을 볼 수 있어야 한다(알림함 · REST 와 같은
 *       {@link WorkspaceAccessService} · {@link ProjectAccessService}). 메일 · 알림함 설정도 지금 값을 본다. 아니면 SKIPPED</li>
 *   <li><b>다시 보내기</b> — 보내지 못하면 1분 · 5분 · 30분 · 2시간 뒤에 다시, {@value #MAX_ATTEMPTS}번째에도 못 하면 FAILED</li>
 *   <li>주소 · 제목 · 본문은 통에 두지 않는다 — 보낼 때의 값으로 만든다({@link NotificationMailContent}). 일감 설명 · 댓글은 싣지 않는다</li>
 * </ul>
 */
@Component
public class NotificationMailSender {

  private static final Logger log = LoggerFactory.getLogger(NotificationMailSender.class);
  static final int MAX_ATTEMPTS = 5;
  /** 한 번 돌 때 보내는 최대 수 — 남은 것은 다음 주기에 */
  static final int BATCH = 50;
  private static final List<Duration> BACKOFF = List.of(Duration.ofMinutes(1), Duration.ofMinutes(5),
      Duration.ofMinutes(30), Duration.ofHours(2));
  private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
  };

  private final NamedParameterJdbcTemplate jdbc;
  private final TransactionTemplate tx;
  private final NotificationService notifications;
  private final WorkspaceAccessService workspaceAccess;
  private final ProjectAccessService projectAccess;
  private final WorkspaceRepositories.Workspaces workspaces;
  private final ProjectRepository projects;
  private final Mailer mailer;
  private final JsonMapper json;
  private final Clock clock;
  private final String baseUrl;

  public NotificationMailSender(NamedParameterJdbcTemplate jdbc, PlatformTransactionManager txManager,
      NotificationService notifications, WorkspaceAccessService workspaceAccess, ProjectAccessService projectAccess,
      WorkspaceRepositories.Workspaces workspaces, ProjectRepository projects, Mailer mailer, JsonMapper json, Clock clock,
      MoaiWorksProperties properties) {
    this.jdbc = jdbc;
    this.tx = new TransactionTemplate(txManager);
    this.notifications = notifications;
    this.workspaceAccess = workspaceAccess;
    this.projectAccess = projectAccess;
    this.workspaces = workspaces;
    this.projects = projects;
    this.mailer = mailer;
    this.json = json;
    this.clock = clock;
    this.baseUrl = properties.web().baseUrl();
  }

  @Scheduled(cron = "${moai-works.mail.outbox-cron:*/30 * * * * *}")
  public void scheduled() {
    try {
      Result r = sendDue();
      if (r.sent() + r.skipped() + r.retrying() + r.failed() > 0) {
        log.info("알림 메일: 보냄 {} · 건너뜀 {} · 다시 보낼 것 {} · 실패 {}", r.sent(), r.skipped(), r.retrying(), r.failed());
      }
    } catch (RuntimeException e) {
      log.error("알림 메일 보내기 실패: {}", e.getMessage(), e);
    }
  }

  /** 한 번 돈 결과 */
  public record Result(int sent, int skipped, int retrying, int failed) {
  }

  private enum Outcome {
    SENT, SKIPPED, RETRYING, FAILED
  }

  /** 보낼 때가 된 메일을 한 줄씩(한 줄 = 한 트랜잭션) 보낸다. 시험에서도 부른다 */
  public Result sendDue() {
    int[] counts = new int[Outcome.values().length];
    for (int i = 0; i < BATCH; i++) {
      Outcome outcome;
      try {
        outcome = tx.execute(status -> sendOne());
      } catch (RuntimeException e) {
        // DB 오류 같은 것 — 그 줄은 PENDING 그대로 남는다. 같은 줄을 이번에 계속 다시 집지 않게 여기서 멈추고 다음 주기에
        log.error("알림 메일 한 줄을 처리하지 못했습니다: {}", e.getClass().getSimpleName(), e);
        break;
      }
      if (outcome == null) {
        break;
      }
      counts[outcome.ordinal()]++;
    }
    return new Result(counts[0], counts[1], counts[2], counts[3]);
  }

  /** 보낼 통에서 꺼낸 한 줄 + 알림 + 받는 사람 */
  private record Row(UUID id, int attempts, UUID recipientId, UUID workspaceId, String kind, UUID subjectId,
      Map<String, Object> params, String actorName, String email, boolean english, String accountStatus,
      boolean systemAdmin) {
  }

  /** 줄 하나 — 없으면 null */
  private Outcome sendOne() {
    Instant now = Instant.now(clock);
    List<Row> rows = jdbc.query("""
        SELECT m.id, m.attempts, m.recipient_id, n.workspace_id, n.kind, n.subject_id, n.params::text AS params,
               coalesce(a.display_name, n.actor_name) AS actor_name, u.email, u.locale, u.account_status,
               'system-admin' = ANY (u.platform_roles) AS system_admin
        FROM notification_mail m
        JOIN notification n ON n.id = m.notification_id
        JOIN user_profile u ON u.id = m.recipient_id
        LEFT JOIN user_profile a ON a.id = n.actor_id
        WHERE m.status = 'PENDING' AND m.next_attempt_at <= :now
        ORDER BY m.next_attempt_at, m.id
        LIMIT 1
        FOR UPDATE OF m SKIP LOCKED
        """, new MapSqlParameterSource("now", Timestamp.from(now)),
        (rs, i) -> new Row(rs.getObject("id", UUID.class), rs.getInt("attempts"), rs.getObject("recipient_id", UUID.class),
            rs.getObject("workspace_id", UUID.class), rs.getString("kind"), rs.getObject("subject_id", UUID.class),
            map(rs.getString("params")), rs.getString("actor_name"), rs.getString("email"),
            rs.getString("locale") != null && rs.getString("locale").toLowerCase(java.util.Locale.ROOT).startsWith("en"),
            rs.getString("account_status"), rs.getBoolean("system_admin")));
    if (rows.isEmpty()) {
      return null;
    }
    Row r = rows.get(0);
    NotificationMailContent.Mail mail;
    try {
      Target target = NotificationKind.of(r.kind()).map(k -> target(r, k, now)).orElse(Target.skip("UNKNOWN_KIND"));
      if (target.denied() != null) {
        finish(r, "SKIPPED", target.denied(), now);
        return Outcome.SKIPPED;
      }
      mail = NotificationMailContent.render(target.input(), baseUrl);
      mailer.sendNotification(r.email(), r.kind(), mail.subject(), mail.body(), r.id());
    } catch (RuntimeException e) {
      return failed(r, e, now);
    }
    jdbc.update("""
        UPDATE notification_mail SET status = 'SENT', attempts = attempts + 1, sent_at = :now, updated_at = :now,
               last_error = NULL
        WHERE id = :id
        """, new MapSqlParameterSource("id", r.id()).addValue("now", Timestamp.from(now)));
    return Outcome.SENT;
  }

  /** 보낼 때의 판정 결과 — 보내면 메일을 만들 값, 아니면 건너뛰는 까닭 */
  private record Target(String denied, NotificationMailContent.Input input) {

    static Target skip(String reason) {
      return new Target(reason, null);
    }
  }

  /**
   * 다시 판정한다 — 알림함 · REST 와 같은 규칙. 활성 계정(시스템 관리자 아님) ∧ 지금도 그 종류를 메일로 받음 ∧ 활성 멤버 ∧ 그 대상을 볼 수 있음.
   * 내보내짐 · 가입 거절은 멤버가 아니게 된 일 자체를 알리므로 계정만 본다. MCP 키는 지금도 그 사람의 키여야 한다.
   */
  private Target target(Row r, NotificationKind kind, Instant now) {
    if (!"ACTIVE".equals(r.accountStatus())) {
      return Target.skip("ACCOUNT_INACTIVE");
    }
    if (r.systemAdmin()) {
      return Target.skip("SYSTEM_ADMIN");
    }
    if (!notifications.choice(r.recipientId(), kind).mail()) {
      return Target.skip("EMAIL_OFF");
    }
    switch (kind) {
      case MEMBER_REMOVED, JOIN_REJECTED -> {
        String name = r.workspaceId() == null ? null : workspaces.findById(r.workspaceId()).map(Workspace::getName).orElse(null);
        return input(r, kind, name, null, null, null, null, null);
      }
      case MCP_KEY_EXPIRED, MCP_KEY_EXPIRING -> {
        return keyTarget(r, kind, now);
      }
      default -> {
      }
    }
    Optional<WorkspaceContext> found = r.workspaceId() == null ? Optional.empty()
        : workspaces.findById(r.workspaceId()).filter(Workspace::isActive)
            .flatMap(w -> workspaceAccess.contextFor(w, r.recipientId()));
    if (found.isEmpty()) {
      return Target.skip("NOT_MEMBER");
    }
    WorkspaceContext ws = found.get();
    String wsName = ws.workspace().getName();
    String slug = ws.workspace().getSlug();
    switch (kind) {
      case JOIN_REQUESTED -> {
        return ws.has(Permission.MEMBER_REQUEST_REVIEW) ? input(r, kind, wsName, slug, null, null, null, null)
            : Target.skip("NO_ACCESS");
      }
      case JOIN_APPROVED, MEMBER_ROLES_CHANGED -> {
        return input(r, kind, wsName, slug, null, null, null, null);
      }
      case PROJECT_ROLES_CHANGED -> {
        Optional<Project> project = r.subjectId() == null ? Optional.empty() : projects.findById(r.subjectId());
        return project.flatMap(p -> projectAccess.contextFor(ws, p)).isPresent()
            ? input(r, kind, wsName, slug, project.get().getKey(), project.get().getName(), null, null)
            : Target.skip("NO_ACCESS");
      }
      default -> {
        return issueTarget(r, kind, ws, wsName, slug);
      }
    }
  }

  /** 일감 알림 — 그 프로젝트의 독자이고 일감을 읽을 수 있어야 한다(일감 화면과 같은 판정). 지운 일감은 "지워짐" 알림만 */
  private record IssueRow(UUID projectId, int number, String title, boolean deleted) {
  }

  private Target issueTarget(Row r, NotificationKind kind, WorkspaceContext ws, String wsName, String slug) {
    List<IssueRow> issue = r.subjectId() == null ? List.of() : jdbc.query(
        "SELECT project_id, number, title, deleted_at IS NOT NULL AS deleted FROM issue WHERE id = :id",
        new MapSqlParameterSource("id", r.subjectId()), (rs, i) -> new IssueRow(rs.getObject("project_id", UUID.class),
            rs.getInt("number"), rs.getString("title"), rs.getBoolean("deleted")));
    if (issue.isEmpty()) {
      return Target.skip("GONE");
    }
    IssueRow row = issue.get(0);
    Optional<Project> project = projects.findById(row.projectId());
    boolean reader = project.flatMap(p -> projectAccess.contextFor(ws, p))
        .filter(p -> p.has(Permission.ISSUE_READ)).isPresent();
    if (!reader) {
      return Target.skip("NO_ACCESS");
    }
    if (row.deleted() && kind != NotificationKind.ISSUE_DELETED) {
      return Target.skip("GONE");
    }
    return input(r, kind, wsName, slug, project.get().getKey(), project.get().getName(), row.number(), row.title());
  }

  /** MCP 키 알림 — 지금도 그 사람의 키여야 하고, 만료 예정 알림은 아직 쓰는 키일 때만 */
  private record KeyRow(UUID owner, boolean usable) {
  }

  private Target keyTarget(Row r, NotificationKind kind, Instant now) {
    List<KeyRow> key = r.subjectId() == null ? List.of() : jdbc.query(
        "SELECT owner_user_id, expire_reason IS NULL AND expires_at > :now AS usable FROM mcp_api_key WHERE id = :id",
        new MapSqlParameterSource("id", r.subjectId()).addValue("now", Timestamp.from(now)),
        (rs, i) -> new KeyRow(rs.getObject("owner_user_id", UUID.class), rs.getBoolean("usable")));
    if (key.isEmpty()) {
      return Target.skip("GONE");
    }
    if (!r.recipientId().equals(key.get(0).owner())) {
      return Target.skip("NO_ACCESS");
    }
    if (kind == NotificationKind.MCP_KEY_EXPIRING && !key.get(0).usable()) {
      return Target.skip("OBSOLETE");
    }
    return input(r, kind, null, null, null, null, null, null);
  }

  private static Target input(Row r, NotificationKind kind, String workspaceName, String slug, String projectKey,
      String projectName, Integer number, String title) {
    return new Target(null, new NotificationMailContent.Input(kind, r.english(), r.params(), r.actorName(), workspaceName,
        slug, projectKey, projectName, number, title));
  }

  private void finish(Row r, String status, String reason, Instant now) {
    jdbc.update("UPDATE notification_mail SET status = :status, last_error = :reason, updated_at = :now WHERE id = :id",
        new MapSqlParameterSource("id", r.id()).addValue("status", status).addValue("reason", reason)
            .addValue("now", Timestamp.from(now)));
  }

  /** 보내지 못함 — 다음에 다시, 다 썼으면 FAILED. 까닭에는 예외 이름과 (주소를 가린) 짧은 글만 */
  private Outcome failed(Row r, RuntimeException e, Instant now) {
    int attempts = r.attempts() + 1;
    boolean giveUp = attempts >= MAX_ATTEMPTS;
    Instant next = now.plus(BACKOFF.get(Math.min(attempts, BACKOFF.size()) - 1));
    jdbc.update("""
        UPDATE notification_mail SET status = :status, attempts = :attempts, next_attempt_at = :next, last_error = :error,
               updated_at = :now
        WHERE id = :id
        """, new MapSqlParameterSource("id", r.id()).addValue("status", giveUp ? "FAILED" : "PENDING")
        .addValue("attempts", attempts).addValue("next", Timestamp.from(next)).addValue("error", reason(e))
        .addValue("now", Timestamp.from(now)));
    log.warn("알림 메일을 보내지 못했습니다(id={}, {}번째{}): {}", r.id(), attempts, giveUp ? " — 그만 보냄" : "",
        e.getClass().getSimpleName());
    return giveUp ? Outcome.FAILED : Outcome.RETRYING;
  }

  static String reason(RuntimeException e) {
    String message = e.getMessage() == null ? "" : ": " + e.getMessage().replaceAll("[^\\s<>@]+@[^\\s<>]+", "***");
    String text = (e.getClass().getSimpleName() + message).replaceAll("\\p{Cntrl}+", " ");
    return text.length() > 300 ? text.substring(0, 300) : text;
  }

  private Map<String, Object> map(String value) {
    try {
      return value == null || value.isBlank() ? Map.of() : json.readValue(value, MAP);
    } catch (RuntimeException e) {
      return Map.of();
    }
  }
}
