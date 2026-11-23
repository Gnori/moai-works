package com.moai.works.api.platform;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.moai.works.api.audit.AuditService;
import com.moai.works.api.common.ApiException;
import com.moai.works.api.keycloak.KeycloakAdminClient;
import com.moai.works.api.keycloak.KeycloakOperationLog;
import com.moai.works.api.security.access.CurrentUser;
import com.moai.works.api.storage.ObjectStore;
import com.moai.works.api.workspace.BuiltinRole;
import com.moai.works.api.workspace.Workspace;
import com.moai.works.api.workspace.WorkspaceRepositories;

/**
 * 시스템 관리자의 팀 워크스페이스 관리 — 목록 · 사용 중지 · 다시 사용 · 삭제 (FR-WSP-019 · FR-WSP-020, WBS 1.8).
 * 2026-11-22 사용자: "팀 워크스페이스는 시스템 어드민이 사용 못 하게 막을 수도 있어" · "다만 삭제는 사용 중지 후에만 가능해".
 *
 * <ul>
 *   <li><b>시스템 관리자는 내용을 보지 않는다</b> — 목록은 이름 · 주소 · 종류 · 소유자 · 멤버 수 · 상태 · 만든 날까지, 삭제 미리보기는 수만</li>
 *   <li><b>팀 워크스페이스만</b> — 개인 워크스페이스는 계정에 묶여 따로 막거나 지우지 않는다(409 {@code PERSONAL_WORKSPACE}).
 *       한 사람을 막으려면 계정을 정지한다(moai-wiki 설계 13 §9 — "개인: 이전·삭제 불가, 계정을 지우면 함께 처리")</li>
 *   <li><b>Keycloak 먼저, 사본은 그다음</b> — 사용 중지는 조직을 끄고(enabled=false), 다시 사용은 켠다. 삭제는 조직을 지운다.
 *       Keycloak 쓰기는 {@link KeycloakOperationLog} 로 남긴다</li>
 *   <li>사용 중지하면 그 워크스페이스는 활성이 아니다 → 멤버의 화면 · REST(403 {@code WORKSPACE_SUSPENDED}) · MCP(보이지 않음)가 모두 막힌다.
 *       판정은 바꾸지 않는다 — 접근 판정이 이미 "활성 워크스페이스" 만 연다(WorkspaceAccessService · McpScope)</li>
 *   <li>삭제는 사용 중지한 워크스페이스에만(409 {@code WORKSPACE_NOT_SUSPENDED}), 확인 문구(주소)를 api 가 다시 본다(400 {@code CONFIRM_MISMATCH})</li>
 *   <li>감사 {@code admin.workspace.suspend} · {@code resume} · {@code delete} — 그 워크스페이스의 감사에도 보이게 workspace_id 를 남긴다</li>
 * </ul>
 */
@Service
public class WorkspaceAdminService {

  private final WorkspaceRepositories.Workspaces workspaces;
  private final KeycloakAdminClient keycloak;
  private final KeycloakOperationLog operations;
  private final ObjectStore objects;
  private final AuditService audit;
  private final NamedParameterJdbcTemplate jdbc;
  private final TransactionTemplate tx;
  private final Clock clock;

  public WorkspaceAdminService(WorkspaceRepositories.Workspaces workspaces, KeycloakAdminClient keycloak,
      KeycloakOperationLog operations, ObjectStore objects, AuditService audit, NamedParameterJdbcTemplate jdbc,
      PlatformTransactionManager txManager, Clock clock) {
    this.workspaces = workspaces;
    this.keycloak = keycloak;
    this.operations = operations;
    this.objects = objects;
    this.audit = audit;
    this.jdbc = jdbc;
    this.tx = new TransactionTemplate(txManager);
    this.clock = clock;
  }

  /** 소유자 — 소유자 역할을 가진 활성 멤버 */
  public record Owner(UUID id, String displayName, String email) {
  }

  /**
   * 시스템 관리자가 보는 워크스페이스 한 줄 — 내용(설명 · 프로젝트 · 일감)은 넣지 않는다.
   *
   * @param suspendedReason 사용 중지한 이유(시스템 관리자가 적은 것) — 멤버에게는 보이지 않는다
   */
  public record WorkspaceView(UUID id, String slug, String name, Workspace.Kind kind, Workspace.Visibility visibility,
      Workspace.Status status, List<Owner> owners, long memberCount, Instant createdAt, Instant suspendedAt,
      String suspendedReason) {
  }

  /** 삭제의 영향 미리보기(FR-WSP-028) — 지워지거나 닿지 못하게 되는 것의 수만 */
  public record DeletionPreview(String slug, String name, Workspace.Status status, long members, long projects,
      long issues, long attachments, long attachmentBytes) {
  }

  // ── 목록 ───────────────────────────────────────────────────────────

  /**
   * 워크스페이스 목록 — 지운 것은 빼고 이름 순.
   *
   * @param kind   종류(비우면 둘 다)
   * @param status 상태(비우면 지운 것 말고 모두)
   * @param query  이름 · 주소 부분 일치
   */
  public List<WorkspaceView> list(Workspace.Kind kind, Workspace.Status status, String query, int limit) {
    StringBuilder where = new StringBuilder("w.status <> 'DELETED'");
    MapSqlParameterSource p = new MapSqlParameterSource("limit", Math.min(Math.max(limit, 1), 500));
    if (kind != null) {
      where.append(" AND w.kind = :kind");
      p.addValue("kind", kind.name());
    }
    if (status != null) {
      where.append(" AND w.status = :status");
      p.addValue("status", status.name());
    }
    String q = query == null ? "" : query.trim().toLowerCase();
    if (!q.isEmpty()) {
      where.append(" AND (lower(w.name) LIKE :q ESCAPE '\\' OR w.slug LIKE :q ESCAPE '\\')");
      p.addValue("q", "%" + q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%");
    }
    return views(where.toString(), p);
  }

  /** 한 워크스페이스의 지금 줄 (사용 중지 · 다시 사용의 응답) */
  private WorkspaceView view(UUID id) {
    return views("w.id = :id", new MapSqlParameterSource("id", id).addValue("limit", 1)).get(0);
  }

  private record Row(UUID id, String slug, String name, Workspace.Kind kind, Workspace.Visibility visibility,
      Workspace.Status status, long memberCount, Instant createdAt, Instant suspendedAt, String suspendedReason) {
  }

  private List<WorkspaceView> views(String where, MapSqlParameterSource p) {
    List<Row> rows = jdbc.query("""
        SELECT w.id, w.slug, w.name, w.kind, w.visibility, w.status, w.created_at, w.suspended_at, w.suspended_reason,
               (SELECT count(*) FROM workspace_member m WHERE m.workspace_id = w.id) AS member_count
        FROM workspace w
        """ + "WHERE " + where + " ORDER BY lower(w.name), w.slug LIMIT :limit", p,
        (rs, i) -> new Row(rs.getObject("id", UUID.class), rs.getString("slug"), rs.getString("name"),
            Workspace.Kind.valueOf(rs.getString("kind")), Workspace.Visibility.valueOf(rs.getString("visibility")),
            Workspace.Status.valueOf(rs.getString("status")), rs.getLong("member_count"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("suspended_at") == null ? null : rs.getTimestamp("suspended_at").toInstant(),
            rs.getString("suspended_reason")));
    if (rows.isEmpty()) {
      return List.of();
    }
    Map<UUID, List<Owner>> owners = new LinkedHashMap<>();
    jdbc.query("""
        SELECT r.workspace_id, u.id, coalesce(u.display_name, u.username) AS display_name, u.email
        FROM member_role r
        JOIN workspace_member m ON m.workspace_id = r.workspace_id AND m.user_id = r.user_id AND m.status = 'ACTIVE'
        JOIN user_profile u ON u.id = r.user_id
        WHERE r.role_key = :owner AND r.workspace_id IN (:ids)
        ORDER BY m.joined_at, u.id
        """, new MapSqlParameterSource("owner", BuiltinRole.OWNER.key()).addValue("ids", rows.stream().map(Row::id).toList()),
        rs -> {
          owners.computeIfAbsent(rs.getObject("workspace_id", UUID.class), k -> new ArrayList<>())
              .add(new Owner(rs.getObject("id", UUID.class), rs.getString("display_name"), rs.getString("email")));
        });
    return rows.stream().map(r -> new WorkspaceView(r.id(), r.slug(), r.name(), r.kind(), r.visibility(), r.status(),
        List.copyOf(owners.getOrDefault(r.id(), List.of())), r.memberCount(), r.createdAt(), r.suspendedAt(),
        r.suspendedReason())).toList();
  }

  // ── 사용 중지 · 다시 사용 ─────────────────────────────────────────────

  /**
   * 사용 중지 — Keycloak 조직을 끄고, 사본을 SUSPENDED 로. 다음 요청부터 멤버의 REST 는 403 {@code WORKSPACE_SUSPENDED},
   * MCP 키에게는 보이지 않는다. 데이터 · 멤버십 · 역할은 그대로다.
   */
  public WorkspaceView suspend(String slug, String reason, CurrentUser by) {
    Workspace ws = team(slug);
    if (!ws.isActive()) {
      throw ApiException.conflict("WORKSPACE_NOT_ACTIVE", "사용 중인 워크스페이스만 사용 중지할 수 있습니다: " + ws.getStatus());
    }
    String why = reason.trim();
    UUID saga = UUID.randomUUID();
    if (ws.getKeycloakOrgId() != null) {
      operations.run(saga, "DISABLE_ORG", "ws:" + ws.getId() + ":suspend:" + saga, "{}",
          () -> keycloak.setOrganizationEnabled(ws.getKeycloakOrgId(), false));
    }
    tx.executeWithoutResult(s -> {
      Workspace locked = workspaces.lockById(ws.getId()).orElseThrow();
      if (!locked.isActive()) {
        throw ApiException.conflict("WORKSPACE_NOT_ACTIVE", "사용 중인 워크스페이스만 사용 중지할 수 있습니다: " + locked.getStatus());
      }
      locked.suspend(why, Instant.now(clock));
      audit.record(by.actor(), ws.getId(), "admin.workspace.suspend", "workspace", ws.getId().toString(),
          Map.of("slug", ws.getSlug(), "name", ws.getName(), "reason", why, "before", "ACTIVE", "after", "SUSPENDED"));
    });
    return view(ws.getId());
  }

  /** 다시 사용 — Keycloak 조직을 켜고, 사본을 ACTIVE 로. 사용 중지 전과 같다(지운 것이 없다) */
  public WorkspaceView resume(String slug, CurrentUser by) {
    Workspace ws = team(slug);
    if (!ws.isSuspended()) {
      throw ApiException.conflict("WORKSPACE_NOT_SUSPENDED", "사용 중지한 워크스페이스가 아닙니다: " + ws.getStatus());
    }
    UUID saga = UUID.randomUUID();
    if (ws.getKeycloakOrgId() != null) {
      operations.run(saga, "ENABLE_ORG", "ws:" + ws.getId() + ":resume:" + saga, "{}",
          () -> keycloak.setOrganizationEnabled(ws.getKeycloakOrgId(), true));
    }
    tx.executeWithoutResult(s -> {
      Workspace locked = workspaces.lockById(ws.getId()).orElseThrow();
      if (!locked.isSuspended()) {
        throw ApiException.conflict("WORKSPACE_NOT_SUSPENDED", "사용 중지한 워크스페이스가 아닙니다: " + locked.getStatus());
      }
      Map<String, Object> details = new LinkedHashMap<>();
      details.put("slug", ws.getSlug());
      details.put("name", ws.getName());
      details.put("suspendedAt", String.valueOf(locked.getSuspendedAt()));
      details.put("before", "SUSPENDED");
      details.put("after", "ACTIVE");
      locked.resume(Instant.now(clock));
      audit.record(by.actor(), ws.getId(), "admin.workspace.resume", "workspace", ws.getId().toString(), details);
    });
    return view(ws.getId());
  }

  // ── 삭제 ───────────────────────────────────────────────────────────

  /** 삭제의 영향 미리보기 — 화면이 확인 문구를 받기 전에 보인다. 내용은 없고 수만 */
  public DeletionPreview deletionPreview(String slug) {
    Workspace ws = team(slug);
    Counts c = counts(ws.getId());
    return new DeletionPreview(ws.getSlug(), ws.getName(), ws.getStatus(), c.members(), c.projects(), c.issues(),
        c.attachments(), c.attachmentBytes());
  }

  private record Counts(long members, long projects, long issues, long attachments, long attachmentBytes) {
  }

  private Counts counts(UUID workspaceId) {
    return jdbc.queryForObject("""
        SELECT (SELECT count(*) FROM workspace_member WHERE workspace_id = :ws) AS members,
               (SELECT count(*) FROM project WHERE workspace_id = :ws AND status <> 'DELETED') AS projects,
               (SELECT count(*) FROM issue i JOIN project p ON p.id = i.project_id
                 WHERE p.workspace_id = :ws AND p.status <> 'DELETED' AND i.deleted_at IS NULL) AS issues,
               (SELECT count(*) FROM issue_attachment WHERE workspace_id = :ws AND deleted_at IS NULL) AS attachments,
               (SELECT coalesce(sum(size_bytes), 0) FROM issue_attachment
                 WHERE workspace_id = :ws AND deleted_at IS NULL) AS attachment_bytes
        """, new MapSqlParameterSource("ws", workspaceId),
        (rs, i) -> new Counts(rs.getLong("members"), rs.getLong("projects"), rs.getLong("issues"),
            rs.getLong("attachments"), rs.getLong("attachment_bytes")));
  }

  /**
   * 워크스페이스 삭제 — 사용 중지한 팀 워크스페이스만, 확인 문구로 주소를 다시 넣어야 한다(위험 작업 확인, FR-WSP-028).
   *
   * <p>순서는 Keycloak → 객체 저장소 → DB 다. 앞 단계는 몇 번 다시 해도 같은 결과라(없는 조직 · 빈 접두사는 아무 일도 없다),
   * 중간에 끊기면 같은 요청을 다시 하면 이어서 끝난다. DB 가 마지막이라 끝나기 전에는 여전히 "사용 중지" 로 보인다.
   *
   * <p><b>지우는 것</b>
   * <ul>
   *   <li>Keycloak 조직 — 조직 멤버십 · 역할 그룹 · 프로젝트 역할 그룹이 함께 사라진다</li>
   *   <li>객체 저장소의 첨부 파일 전부 — {@code ws/<워크스페이스 id>/} 아래(버전 보관분까지). 첨부 기록에는 지움 표시를 한다</li>
   *   <li>DB 의 사본 · 설정 — 멤버십 · 역할 · 역할 권한 · 프로젝트 역할 · 가입 신청 · 초대 · 일감 템플릿 · 이 워크스페이스의 알림 ·
   *       첨부 일회용 티켓, 누군가의 "마지막에 쓴 워크스페이스"</li>
   * </ul>
   * <p><b>남기는 것</b>(보존 규칙 — 영구 삭제는 나중의 보존 작업이 한다. 프로젝트 삭제와 같다)
   * <ul>
   *   <li>워크스페이스 행(상태 DELETED) — 주소를 다시 쓰지 않고, 감사가 가리키는 곳이 남는다</li>
   *   <li>프로젝트(DELETED 로) · 일감 · 이력 · 리비전 · 작업 시간 · 연결 · 첨부 기록 — 추가 전용이라 지우지 않는다. 아무에게도 보이지 않는다</li>
   *   <li>감사 · MCP 접근 기록 — 추가 전용(보존 3년). MCP 키는 사용자의 것이라 그대로이고, 다음 호출부터 이 워크스페이스가 보이지 않는다</li>
   * </ul>
   */
  public void delete(String slug, String confirm, CurrentUser by) {
    Workspace ws = team(slug);
    if (!ws.isSuspended()) {
      throw ApiException.conflict("WORKSPACE_NOT_SUSPENDED", "사용 중지한 워크스페이스만 지울 수 있습니다. 먼저 사용 중지하십시오.");
    }
    if (confirm == null || !ws.getSlug().equals(confirm.trim())) {
      throw ApiException.badRequest("CONFIRM_MISMATCH", "확인용 주소가 맞지 않습니다.");
    }
    Counts before = counts(ws.getId());
    UUID saga = UUID.randomUUID();
    if (ws.getKeycloakOrgId() != null) {
      // 멱등 키는 워크스페이스마다 하나 — 끊긴 삭제를 다시 하면 이미 지운 조직을 다시 지우지 않는다
      operations.run(saga, "DELETE_ORG", "ws:" + ws.getId() + ":delete-org", "{\"alias\":\"" + ws.getSlug() + "\"}",
          () -> keycloak.deleteOrganization(ws.getKeycloakOrgId()));
    }
    int storageObjects = objects.deletePrefix(ObjectStore.Zone.ATTACHMENT, "ws/" + ws.getId() + "/");
    tx.executeWithoutResult(s -> {
      Workspace locked = workspaces.lockById(ws.getId()).orElseThrow();
      if (!locked.isSuspended()) {
        throw ApiException.conflict("WORKSPACE_NOT_SUSPENDED", "사용 중지한 워크스페이스만 지울 수 있습니다.");
      }
      Instant now = Instant.now(clock);
      MapSqlParameterSource p = new MapSqlParameterSource("ws", ws.getId()).addValue("now", Timestamp.from(now))
          .addValue("by", by.id());
      // 파일은 이미 지웠다 — 기록에는 지움 표시만(추가 전용 표: 지움 표시를 한 번 남기는 것만 된다)
      jdbc.update("UPDATE issue_attachment SET deleted_at = :now, deleted_by = :by WHERE workspace_id = :ws AND deleted_at IS NULL", p);
      jdbc.update("DELETE FROM attachment_ticket WHERE workspace_id = :ws", p);
      jdbc.update("DELETE FROM notification WHERE workspace_id = :ws", p);
      jdbc.update("DELETE FROM project_role_binding WHERE project_id IN (SELECT id FROM project WHERE workspace_id = :ws)", p);
      jdbc.update("""
          UPDATE project SET status = 'DELETED', deleted_at = :now, updated_at = :now, row_version = row_version + 1
          WHERE workspace_id = :ws AND status <> 'DELETED'
          """, p);
      jdbc.update("DELETE FROM issue_template WHERE workspace_id = :ws", p);
      // 멤버십 · 역할 사본 — 정본(Keycloak 조직)은 이미 지웠다. 역할을 가리키는 초대 · 역할 부여를 먼저 지운다
      jdbc.update("DELETE FROM member_role WHERE workspace_id = :ws", p);
      jdbc.update("DELETE FROM invitation WHERE workspace_id = :ws", p);
      jdbc.update("DELETE FROM join_request WHERE workspace_id = :ws", p);
      jdbc.update("DELETE FROM workspace_member WHERE workspace_id = :ws", p);
      jdbc.update("DELETE FROM workspace_role WHERE workspace_id = :ws", p);   // role_permission 은 함께 지워진다
      jdbc.update("UPDATE user_profile SET last_workspace_id = NULL WHERE last_workspace_id = :ws", p);
      Map<String, Object> details = new LinkedHashMap<>();
      details.put("slug", ws.getSlug());
      details.put("name", ws.getName());
      details.put("suspendedReason", String.valueOf(locked.getSuspendedReason()));
      details.put("members", before.members());
      details.put("projects", before.projects());
      details.put("issues", before.issues());
      details.put("attachments", before.attachments());
      details.put("storageObjects", storageObjects);
      details.put("before", "SUSPENDED");
      details.put("after", "DELETED");
      locked.markDeleted(now);
      audit.record(by.actor(), ws.getId(), "admin.workspace.delete", "workspace", ws.getId().toString(), details);
    });
  }

  // ── 찾기 ───────────────────────────────────────────────────────────

  /** 주소의 팀 워크스페이스 — 없거나 지웠으면 404, 개인 워크스페이스면 409 */
  private Workspace team(String slug) {
    Workspace ws = workspaces.findBySlug(slug).filter(w -> w.getStatus() != Workspace.Status.DELETED)
        .orElseThrow(() -> ApiException.notFound("WORKSPACE_NOT_FOUND", "워크스페이스를 찾을 수 없습니다."));
    if (ws.getKind() != Workspace.Kind.TEAM) {
      throw ApiException.conflict("PERSONAL_WORKSPACE",
          "개인 워크스페이스는 사용 중지하거나 지울 수 없습니다. 그 사람을 막으려면 계정을 정지하십시오.");
    }
    return ws;
  }
}
