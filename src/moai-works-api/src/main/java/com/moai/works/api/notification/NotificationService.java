package com.moai.works.api.notification;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import com.moai.works.api.common.Actor;
import com.moai.works.api.common.ApiException;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * 알림 — 사람에게 알려야 할 일을 그 사람의 알림함에 쌓고, 알림함을 읽는다 (design/07, Flyway V6 {@code notification}).
 * moai-wiki 의 NotificationService 와 같은 모양이고, 사람마다 종류별로 끄는 설정({@code notification_preference})을 더했다.
 *
 * <ul>
 *   <li>{@link #notify} 는 그 일을 하는 트랜잭션 안에서 부른다 — 일이 되돌려지면 알림도 생기지 않는다</li>
 *   <li><b>자기가 한 일은 자기에게 알리지 않는다</b></li>
 *   <li>글에는 제목 · 이름 · 주소 같은 값만 넣는다. 본문은 넣지 않는다 — 눌러서 들어간 화면이 지금 권한으로 다시 판정한다</li>
 *   <li>같은 사람 · 같은 종류 · 같은 대상의 읽지 않은 알림이 있으면 그 알림에 묶는다(여러 번 고쳐도 알림은 한 줄)</li>
 *   <li>받는 사람이 그 종류를 껐으면 쌓지 않는다. 꼭 받아야 하는 종류({@link NotificationKind#mandatory()})는 끌 수 없다</li>
 * </ul>
 */
@Service
public class NotificationService {

  static final int PAGE_SIZE = 30;
  private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
  };

  private final NamedParameterJdbcTemplate jdbc;
  private final JsonMapper json;
  private final Clock clock;

  public NotificationService(NamedParameterJdbcTemplate jdbc, JsonMapper json, Clock clock) {
    this.jdbc = jdbc;
    this.json = json;
    this.clock = clock;
  }

  // ── 쌓기 ───────────────────────────────────────────────────────────

  /**
   * 한 사람에게 알린다. 받는 사람이 없거나 그 일을 한 사람 자신이면 아무것도 하지 않는다.
   *
   * @param workspaceId 워크스페이스에 속한 일이면 그 id
   * @param subjectId   무엇에 관한 것인가(페이지 · 실행 · 신청의 id). 같은 대상의 읽지 않은 알림에 묶는 열쇠다
   * @param params      화면이 문장과 링크를 만드는 값(주소 · 제목 · 이름 · 수). 본문은 넣지 않는다
   * @param actor       그 일을 한 주체
   */
  public void notify(UUID recipientId, UUID workspaceId, NotificationKind kind, UUID subjectId,
      Map<String, Object> params, Actor actor) {
    if (recipientId == null || (actor != null && recipientId.equals(actor.id())) || turnedOff(recipientId, kind)) {
      return;
    }
    Instant now = Instant.now(clock);
    UUID actorId = actor == null || actor.type() != Actor.Type.USER ? null : actor.id();
    String actorName = actor == null ? null : actor.name();
    String body = json.writeValueAsString(params == null ? Map.of() : params);
    MapSqlParameterSource p = new MapSqlParameterSource("recipient", recipientId).addValue("workspace", workspaceId)
        .addValue("kind", kind.code()).addValue("subjectType", kind.subjectType()).addValue("subject", subjectId)
        .addValue("params", body).addValue("actorId", actorId).addValue("actorName", actorName)
        .addValue("now", Timestamp.from(now)).addValue("id", UUID.randomUUID());
    // 읽지 않은 같은 알림이 있으면 거기에 묶는다 — 마지막 것의 값과 시각으로 바꾸고 수를 올린다
    int merged = subjectId == null ? 0 : jdbc.update("""
        UPDATE notification SET count = count + 1, params = CAST(:params AS jsonb), actor_id = :actorId,
               actor_name = :actorName, updated_at = :now
        WHERE id = (SELECT id FROM notification
                    WHERE recipient_id = :recipient AND kind = :kind AND subject_id = :subject AND read_at IS NULL
                    ORDER BY updated_at DESC LIMIT 1)
        """, p);
    if (merged == 0) {
      jdbc.update("""
          INSERT INTO notification (id, recipient_id, workspace_id, kind, subject_type, subject_id, params, actor_id,
                                    actor_name, count, created_at, updated_at)
          VALUES (:id, :recipient, :workspace, :kind, :subjectType, :subject, CAST(:params AS jsonb), :actorId,
                  :actorName, 1, :now, :now)
          """, p);
    }
  }

  /** 여러 사람에게 알린다(겹치는 사람은 한 번만). */
  public void notifyAll(Collection<UUID> recipientIds, UUID workspaceId, NotificationKind kind, UUID subjectId,
      Map<String, Object> params, Actor actor) {
    for (UUID recipient : new LinkedHashSet<>(recipientIds)) {
      notify(recipient, workspaceId, kind, subjectId, params, actor);
    }
  }

  /**
   * 일감에 관한 알림의 값 — 화면이 문장("…#12 「제목」…")과 링크(프로젝트 · 일감 번호)를 만든다.
   *
   * @param via WEB · MCP — MCP 로 한 일이면 화면이 "(MCP)"를 붙인다
   */
  public static Map<String, Object> aboutIssue(String projectKey, String projectName, int number, String title,
      String via) {
    Map<String, Object> out = new java.util.LinkedHashMap<>();
    out.put("project", projectKey);
    out.put("projectName", projectName);
    out.put("number", number);
    out.put("title", title);
    out.put("via", via);
    return out;
  }

  /** 그 사람이 그 종류를 껐는가 (꼭 받아야 하는 종류는 늘 받는다) */
  private boolean turnedOff(UUID userId, NotificationKind kind) {
    if (kind.mandatory()) {
      return false;
    }
    Integer off = jdbc.queryForObject(
        "SELECT count(*) FROM notification_preference WHERE user_id = :user AND kind = :kind AND NOT enabled",
        new MapSqlParameterSource("user", userId).addValue("kind", kind.code()), Integer.class);
    return off != null && off > 0;
  }

  /** 이미 보낸 알림인가 — 아침 알림(기한 · 키 만료)을 같은 대상 · 같은 값으로 두 번 보내지 않으려고 */
  public boolean alreadySent(UUID recipientId, NotificationKind kind, UUID subjectId, String paramKey, String value) {
    Integer n = jdbc.queryForObject("""
        SELECT count(*) FROM notification
        WHERE recipient_id = :recipient AND kind = :kind AND subject_id = :subject AND params ->> :key = :value
        """, new MapSqlParameterSource("recipient", recipientId).addValue("kind", kind.code())
        .addValue("subject", subjectId).addValue("key", paramKey).addValue("value", value), Integer.class);
    return n != null && n > 0;
  }

  // ── 받는 사람 찾기 ─────────────────────────────────────────────────

  /** 그 워크스페이스에서 그 권한을 가진 활성 멤버들 — "이 일을 처리할 수 있는 사람들"에게 알릴 때 */
  public Set<UUID> membersWith(UUID workspaceId, String permissionCode) {
    return new LinkedHashSet<>(jdbc.queryForList("""
        SELECT DISTINCT m.user_id
        FROM workspace_member m
        JOIN member_role mr ON mr.workspace_id = m.workspace_id AND mr.user_id = m.user_id
        JOIN workspace_role r ON r.workspace_id = mr.workspace_id AND r.role_key = mr.role_key
        JOIN role_permission rp ON rp.role_id = r.id
        WHERE m.workspace_id = :workspace AND m.status = 'ACTIVE' AND rp.permission_code = :permission
        """, new MapSqlParameterSource("workspace", workspaceId).addValue("permission", permissionCode), UUID.class));
  }

  /** 그 일감에 댓글을 단 적이 있는 사람들 — 새 댓글을 알릴 때(대화에 낀 사람). 지운 댓글은 세지 않는다 */
  public Set<UUID> commenters(UUID issueId) {
    return new LinkedHashSet<>(jdbc.queryForList(
        "SELECT DISTINCT actor_id FROM issue_journal WHERE issue_id = :issue AND notes IS NOT NULL"
            + " AND notes_deleted_at IS NULL AND actor_id IS NOT NULL",
        new MapSqlParameterSource("issue", issueId), UUID.class));
  }

  // ── 알림 받기 설정 ─────────────────────────────────────────────────

  /** 내 알림 받기 설정 — 모든 종류와 켬 · 끔 · 끌 수 있는지 */
  public List<NotificationViews.Preference> preferences(UUID userId) {
    Set<String> off = new java.util.HashSet<>(jdbc.queryForList(
        "SELECT kind FROM notification_preference WHERE user_id = :user AND NOT enabled",
        new MapSqlParameterSource("user", userId), String.class));
    return java.util.Arrays.stream(NotificationKind.values())
        .map(k -> new NotificationViews.Preference(k.code(), k.group().name(), k.mandatory() || !off.contains(k.code()),
            k.mandatory()))
        .toList();
  }

  /** 종류 하나를 켜거나 끈다. 꼭 받아야 하는 종류를 끄면 400, 모르는 종류면 400 */
  public List<NotificationViews.Preference> setPreference(UUID userId, String code, boolean enabled) {
    NotificationKind kind = NotificationKind.of(code)
        .orElseThrow(() -> ApiException.badRequest("UNKNOWN_NOTIFICATION_KIND", "모르는 알림 종류입니다: " + code));
    if (kind.mandatory() && !enabled) {
      throw ApiException.badRequest("NOTIFICATION_MANDATORY", "이 알림은 끌 수 없습니다: " + code);
    }
    jdbc.update("""
        INSERT INTO notification_preference (user_id, kind, enabled, updated_at) VALUES (:user, :kind, :enabled, :now)
        ON CONFLICT (user_id, kind) DO UPDATE SET enabled = EXCLUDED.enabled, updated_at = EXCLUDED.updated_at
        """, new MapSqlParameterSource("user", userId).addValue("kind", kind.code()).addValue("enabled", enabled)
        .addValue("now", Timestamp.from(Instant.now(clock))));
    return preferences(userId);
  }

  // ── 알림함 ─────────────────────────────────────────────────────────

  /**
   * 내 알림함 — 새 것부터.
   *
   * @param unreadOnly 읽지 않은 것만
   * @param before     앞 쪽의 next (그 뒤의 것부터)
   */
  public NotificationViews.Inbox inbox(UUID userId, boolean unreadOnly, String before, int limit) {
    int size = Math.min(Math.max(limit, 1), PAGE_SIZE);
    // 커서 = "마지막 시각|마지막 id" — 같은 밀리초에 묶인 알림을 건너뛰지 않으려고 시각과 id 를 함께 본다
    Instant cursor = null;
    UUID cursorId = null;
    if (before != null && !before.isBlank()) {
      try {
        String[] parts = before.strip().split("\\|", 2);
        cursor = Instant.parse(parts[0]);
        cursorId = parts.length > 1 ? UUID.fromString(parts[1]) : null;
      } catch (RuntimeException e) {
        throw ApiException.badRequest("INVALID_CURSOR", "before 값이 올바르지 않습니다.");
      }
    }
    List<NotificationViews.Item> rows = jdbc.query("""
        SELECT n.id, n.kind, n.subject_type, n.subject_id, n.params::text AS params, n.actor_id,
               coalesce(u.display_name, n.actor_name) AS actor_name, n.count, n.created_at, n.updated_at, n.read_at,
               w.slug AS workspace_slug, w.name AS workspace_name
        FROM notification n
        LEFT JOIN workspace w ON w.id = n.workspace_id
        LEFT JOIN user_profile u ON u.id = n.actor_id
        WHERE n.recipient_id = :user"""
        + (unreadOnly ? " AND n.read_at IS NULL" : "")
        + (cursor == null ? "" : cursorId == null ? " AND n.updated_at < :before"
            : " AND (n.updated_at, n.id) < (:before, :beforeId)")
        + " ORDER BY n.updated_at DESC, n.id DESC LIMIT :limit",
        new MapSqlParameterSource("user", userId).addValue("limit", size + 1)
            .addValue("before", cursor == null ? null : Timestamp.from(cursor)).addValue("beforeId", cursorId),
        (rs, i) -> new NotificationViews.Item(rs.getObject("id", UUID.class), rs.getString("kind"),
            rs.getString("subject_type"), rs.getObject("subject_id", UUID.class), map(rs.getString("params")),
            rs.getString("actor_name"), rs.getInt("count"), rs.getString("workspace_slug"),
            rs.getString("workspace_name"), rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant(),
            rs.getTimestamp("read_at") == null ? null : rs.getTimestamp("read_at").toInstant()));
    boolean more = rows.size() > size;
    List<NotificationViews.Item> items = more ? rows.subList(0, size) : rows;
    return new NotificationViews.Inbox(items, unreadCount(userId),
        more ? items.get(items.size() - 1).updatedAt() + "|" + items.get(items.size() - 1).id() : null);
  }

  public long unreadCount(UUID userId) {
    Long count = jdbc.queryForObject("SELECT count(*) FROM notification WHERE recipient_id = :user AND read_at IS NULL",
        new MapSqlParameterSource("user", userId), Long.class);
    return count == null ? 0 : count;
  }

  /** 알림 하나를 읽음으로 한다. 내 알림이 아니면 404 */
  public void read(UUID userId, UUID id) {
    Integer mine = jdbc.queryForObject("SELECT count(*) FROM notification WHERE id = :id AND recipient_id = :user",
        new MapSqlParameterSource("id", id).addValue("user", userId), Integer.class);
    if (mine == null || mine == 0) {
      throw ApiException.notFound("NOTIFICATION_NOT_FOUND", "알림을 찾을 수 없습니다.");
    }
    jdbc.update("UPDATE notification SET read_at = :now WHERE id = :id AND recipient_id = :user AND read_at IS NULL",
        new MapSqlParameterSource("id", id).addValue("user", userId)
            .addValue("now", Timestamp.from(Instant.now(clock))));
  }

  /** 내 알림을 모두 읽음으로 한다. @return 읽음으로 바꾼 수 */
  public int readAll(UUID userId) {
    return jdbc.update("UPDATE notification SET read_at = :now WHERE recipient_id = :user AND read_at IS NULL",
        new MapSqlParameterSource("user", userId).addValue("now", Timestamp.from(Instant.now(clock))));
  }

  private Map<String, Object> map(String value) {
    try {
      return value == null || value.isBlank() ? Map.of() : json.readValue(value, MAP);
    } catch (RuntimeException e) {
      return Map.of();
    }
  }
}
