package com.moai.works.api.mcpkey;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.moai.works.api.account.PlatformRole;
import com.moai.works.api.account.UserProfile;
import com.moai.works.api.account.UserProfileRepository;
import com.moai.works.api.audit.AuditService;
import com.moai.works.api.common.Actor;
import com.moai.works.api.common.ApiException;
import com.moai.works.api.config.McpEndpoint;
import com.moai.works.api.config.MoaiWorksProperties;
import com.moai.works.api.security.access.CurrentUser;
import com.moai.works.api.security.access.WorkspaceContext;
import com.moai.works.api.workspace.Permission;
import com.moai.works.api.workspace.Workspace;
import com.moai.works.api.workspace.WorkspaceMember;
import com.moai.works.api.workspace.WorkspaceRepositories;

/**
 * MCP 키 발급 · 관리 · 검사 (2026-11-22 사용자 결정 — ADR-022 · ADR-024).
 *
 * <ul>
 *   <li>키 하나 = 워크스페이스 하나 · 주인 계정 하나. 주인의 권한 안에서만 닿는다(키 종류 없음)</li>
 *   <li>발급은 {@code mcp.key.issue.personal}. 사람 계정만(서비스 계정 · 시스템 관리자 계정은 안 됨)</li>
 *   <li>누구나 자기 키를 보고 만료시킨다. {@code mcp.key.manage}(기본 역할로는 소유자 · 관리자)는 워크스페이스의 모든 키 —
 *       만료된 것까지 발급 이력 전체를 보고 어떤 키든 만료시킨다</li>
 *   <li>키는 지우지 않는다 — 만료시킬 뿐이다. 발급 · 만료시키기는 IP · User-Agent 와 함께 감사에 남는다</li>
 *   <li>요청마다 캐시 없이 검사한다: 모양 → SHA-256 → 기간 → 입구 → 워크스페이스 활성 → 주인 계정 활성 · 활성 멤버</li>
 * </ul>
 */
@Service
public class McpKeyService {

  private static final Duration LAST_USED_GAP = Duration.ofMinutes(1);

  private final McpApiKeyRepository keys;
  private final WorkspaceRepositories.Workspaces workspaces;
  private final WorkspaceRepositories.Members members;
  private final UserProfileRepository users;
  private final AuditService audit;
  private final MoaiWorksProperties.Mcp mcp;
  private final TransactionTemplate tx;
  private final Clock clock;

  public McpKeyService(McpApiKeyRepository keys, WorkspaceRepositories.Workspaces workspaces,
      WorkspaceRepositories.Members members, UserProfileRepository users, AuditService audit,
      MoaiWorksProperties properties, PlatformTransactionManager txManager, Clock clock) {
    this.keys = keys;
    this.workspaces = workspaces;
    this.members = members;
    this.users = users;
    this.audit = audit;
    this.mcp = properties.mcp();
    this.tx = new TransactionTemplate(txManager);
    this.clock = clock;
  }

  // ── 보기 모양 ──────────────────────────────────────────────────────

  public record Person(UUID id, String username, String displayName) {
  }

  /**
   * 키 한 줄 — 원문은 없다.
   *
   * @param status          ACTIVE · EXPIRED (기간이 지났거나 일찍 만료시킴)
   * @param issuedIp        발급한 IP · {@code issuedUserAgent} 발급한 프로그램 — 관리하는 사람과 주인에게만
   */
  public record KeyView(UUID id, String name, String keyPrefix, String status, String workspace, String workspaceName,
      Person owner, Instant createdAt, Instant expiresAt, Instant lastUsedAt, String issuedIp, String issuedUserAgent,
      Person expiredBy, String expireReason, String expireNote, boolean mine) {
  }

  /** 발급 결과 — {@code key} 원문은 이 응답에만 있다(다시 볼 수 없다). */
  public record Issued(KeyView view, String key) {
  }

  // ── 발급 ──────────────────────────────────────────────────────────

  public Issued issue(WorkspaceContext ws, CurrentUser user, String name, Integer days, String userAgent) {
    if (user.systemAdmin()) {
      throw ApiException.forbidden("SYSTEM_ADMIN_NOT_ALLOWED", "시스템 관리자 계정은 MCP 키를 발급할 수 없습니다.");
    }
    String keyName = name == null ? "" : name.trim();
    if (keyName.isEmpty() || keyName.length() > 100) {
      throw ApiException.badRequest("INVALID_KEY_NAME", "키 이름은 1~100자입니다.");
    }
    int validDays = days == null ? mcp.keyDefaultDays() : days;
    if (validDays < 1 || validDays > mcp.keyMaxDays()) {
      throw ApiException.badRequest("INVALID_KEY_DAYS", "기간은 1~" + mcp.keyMaxDays() + "일입니다.");
    }
    String token = McpKeyTokenCodec.generate();
    Instant now = Instant.now(clock);
    String ua = userAgent == null ? null : userAgent.substring(0, Math.min(userAgent.length(), 500));
    McpApiKey saved = tx.execute(s -> {
      McpApiKey key = keys.saveAndFlush(new McpApiKey(ws.workspaceId(), user.id(),
          user.displayName() == null ? user.username() : user.displayName(), keyName,
          McpKeyTokenCodec.displayPrefix(token), McpKeyTokenCodec.hash(token), McpEndpoint.PROJECT, now,
          now.plus(Duration.ofDays(validDays)), user.ip(), ua));
      Map<String, Object> details = new LinkedHashMap<>();
      details.put("keyId", key.getId().toString());
      details.put("keyPrefix", key.getKeyPrefix());
      details.put("name", keyName);
      details.put("owner", user.id().toString());
      details.put("expiresAt", key.getExpiresAt().toString());
      details.put("userAgent", ua == null ? "" : ua);
      audit.record(user.actor(), ws.workspaceId(), "mcp.key.issue", "mcp_key", key.getId().toString(), details);
      return key;
    });
    return new Issued(view(saved, ws.workspace(), people(List.of(saved)), true, true), token);
  }

  // ── 목록 ──────────────────────────────────────────────────────────

  /**
   * 워크스페이스의 키 — {@code mcp.key.manage} 가 있으면 모두(발급 이력), 없으면 내 키만.
   *
   * @param status ACTIVE · EXPIRED · null(전부)
   * @param owner  주인으로 거르기 (관리하는 사람만 뜻이 있다)
   */
  @Transactional(readOnly = true)
  public List<KeyView> list(WorkspaceContext ws, String status, UUID owner) {
    boolean manager = ws.has(Permission.MCP_KEY_MANAGE);
    List<McpApiKey> rows = manager
        ? (owner == null ? keys.findByWorkspaceIdOrderByCreatedAtDesc(ws.workspaceId())
            : keys.findByWorkspaceIdAndOwnerUserIdOrderByCreatedAtDesc(ws.workspaceId(), owner))
        : keys.findByWorkspaceIdAndOwnerUserIdOrderByCreatedAtDesc(ws.workspaceId(), ws.userId());
    Map<UUID, UserProfile> people = people(rows);
    Instant now = Instant.now(clock);
    return rows.stream().filter(k -> matches(k, status, now))
        .map(k -> view(k, ws.workspace(), people, manager || k.getOwnerUserId().equals(ws.userId()),
            k.getOwnerUserId().equals(ws.userId())))
        .toList();
  }

  /** 내 키 — 모든 워크스페이스 (개인 설정 화면) */
  @Transactional(readOnly = true)
  public List<KeyView> mine(CurrentUser user, String status) {
    List<McpApiKey> rows = keys.findByOwnerUserIdOrderByCreatedAtDesc(user.id());
    Map<UUID, Workspace> spaces = new HashMap<>();
    workspaces.findAllById(rows.stream().map(McpApiKey::getWorkspaceId).collect(Collectors.toSet()))
        .forEach(w -> spaces.put(w.getId(), w));
    Map<UUID, UserProfile> people = people(rows);
    Instant now = Instant.now(clock);
    return rows.stream().filter(k -> matches(k, status, now))
        .map(k -> view(k, spaces.get(k.getWorkspaceId()), people, true, true)).toList();
  }

  private static boolean matches(McpApiKey k, String status, Instant now) {
    if (status == null || status.isBlank() || "ALL".equals(status)) {
      return true;
    }
    return switch (status) {
      case "ACTIVE" -> k.isActive(now);
      case "EXPIRED" -> !k.isActive(now);
      default -> throw ApiException.badRequest("INVALID_FILTER", "상태는 ACTIVE · EXPIRED · ALL 가운데 하나입니다.");
    };
  }

  // ── 만료시키기 ────────────────────────────────────────────────────

  /** 지금 만료시킨다 — 자기 키, 또는 mcp.key.manage 가 있으면 어떤 키든. 키는 지우지 않는다 */
  public KeyView expire(WorkspaceContext ws, UUID keyId, String note, Actor actor, String userAgent) {
    McpApiKey key = keys.findById(keyId).filter(k -> k.getWorkspaceId().equals(ws.workspaceId()))
        .orElseThrow(() -> ApiException.notFound("MCP_KEY_NOT_FOUND", "MCP 키를 찾을 수 없습니다."));
    boolean mine = key.getOwnerUserId().equals(ws.userId());
    if (!mine && !ws.has(Permission.MCP_KEY_MANAGE)) {
      throw ApiException.forbidden("PERMISSION_REQUIRED", "남의 MCP 키를 만료시키려면 권한 mcp.key.manage 이(가) 필요합니다.");
    }
    Instant now = Instant.now(clock);
    if (!key.isActive(now)) {
      throw ApiException.conflict("MCP_KEY_ALREADY_EXPIRED", "이미 만료된 MCP 키입니다.");
    }
    String text = note == null || note.isBlank() ? null : note.trim();
    if (text != null && text.length() > 500) {
      throw ApiException.badRequest("TEXT_TOO_LONG", "이유는 500자를 넘을 수 없습니다.");
    }
    McpApiKey done = tx.execute(s -> {
      McpApiKey locked = keys.findById(key.getId()).orElseThrow();
      locked.expireNow(ws.userId(), McpApiKey.ExpireReason.MANUAL, text, now);
      Map<String, Object> details = new LinkedHashMap<>();
      details.put("keyId", locked.getId().toString());
      details.put("keyPrefix", locked.getKeyPrefix());
      details.put("owner", locked.getOwnerUserId().toString());
      details.put("reason", "MANUAL");
      details.put("note", text == null ? "" : text);
      details.put("userAgent", userAgent == null ? "" : userAgent.substring(0, Math.min(userAgent.length(), 500)));
      audit.record(actor, ws.workspaceId(), "mcp.key.expire", "mcp_key", locked.getId().toString(), details);
      return locked;
    });
    return view(done, ws.workspace(), people(List.of(done)), true, mine);
  }

  /** 주인이 워크스페이스를 떠나면 그 사람의 그 워크스페이스 키를 모두 만료시킨다 (MEMBER_LEFT) — 호출자의 트랜잭션 안에서 */
  public void expireForLeaving(UUID workspaceId, UUID userId, Actor actor) {
    Instant now = Instant.now(clock);
    for (McpApiKey key : keys.findActive(workspaceId, userId, now)) {
      key.expireNow(actor.id(), McpApiKey.ExpireReason.MEMBER_LEFT, null, now);
      audit.record(actor, workspaceId, "mcp.key.expire", "mcp_key", key.getId().toString(),
          Map.of("keyId", key.getId().toString(), "keyPrefix", key.getKeyPrefix(), "owner", userId.toString(),
              "reason", "MEMBER_LEFT"));
    }
  }

  // ── 검사 ──────────────────────────────────────────────────────────

  /** 검증 결과 — 성공이면 principal, 실패면 HTTP 상태와 코드. */
  public record Verdict(McpKeyPrincipal principal, int status, String code, String message) {
    static Verdict ok(McpKeyPrincipal principal) {
      return new Verdict(principal, 200, null, null);
    }

    static Verdict reject(int status, String code, String message) {
      return new Verdict(null, status, code, message);
    }
  }

  /**
   * 요청마다 검사한다(캐시 없음) — 만료가 다음 요청에 바로 반영된다.
   * 순서: 모양 → 원문 SHA-256 으로 행 → 기간 → 입구 → 워크스페이스 활성 → 주인 계정 활성 · 그 워크스페이스의 활성 멤버.
   */
  @Transactional
  public Verdict authenticate(String token, McpEndpoint entry) {
    McpApiKey key = McpKeyTokenCodec.wellFormed(token) ? keys.findByKeyHash(McpKeyTokenCodec.hash(token)).orElse(null)
        : null;
    if (key == null) {
      return Verdict.reject(401, "MCP_KEY_INVALID", "MCP 키가 올바르지 않습니다.");
    }
    Instant now = Instant.now(clock);
    if (!key.isActive(now)) {
      return Verdict.reject(401, "MCP_KEY_EXPIRED", "만료된 MCP 키입니다.");
    }
    if (key.getEndpoint() != entry) {
      return Verdict.reject(403, "MCP_KEY_ENDPOINT_DENIED", "이 MCP 키로는 이 입구를 쓸 수 없습니다.");
    }
    boolean workspaceActive = workspaces.findById(key.getWorkspaceId()).map(Workspace::isActive).orElse(false);
    if (!workspaceActive) {
      return Verdict.reject(403, "MCP_WORKSPACE_INACTIVE", "쓸 수 없는 워크스페이스의 MCP 키입니다.");
    }
    UserProfile owner = users.findById(key.getOwnerUserId()).orElse(null);
    boolean ownerActive = owner != null && owner.isActive()
        && !owner.hasPlatformRole(PlatformRole.SYSTEM_ADMIN)
        && members.find(key.getWorkspaceId(), key.getOwnerUserId()).map(WorkspaceMember::isActive).orElse(false);
    if (!ownerActive) {
      return Verdict.reject(403, "MCP_PRINCIPAL_INACTIVE", "키 주인이 이 워크스페이스를 쓸 수 없는 상태입니다.");
    }
    keys.touchLastUsed(key.getId(), now, now.minus(LAST_USED_GAP));
    return Verdict.ok(new McpKeyPrincipal(key.getId(), key.getName(), key.getEndpoint(), key.getWorkspaceId(),
        key.getOwnerUserId(), key.getOwnerName()));
  }

  // ── 도움 ──────────────────────────────────────────────────────────

  private Map<UUID, UserProfile> people(List<McpApiKey> rows) {
    Set<UUID> ids = new java.util.HashSet<>();
    rows.forEach(k -> {
      ids.add(k.getOwnerUserId());
      if (k.getExpiredBy() != null) {
        ids.add(k.getExpiredBy());
      }
    });
    Map<UUID, UserProfile> map = new HashMap<>();
    users.findAllById(ids).forEach(u -> map.put(u.getId(), u));
    return map;
  }

  private static Person person(Map<UUID, UserProfile> people, UUID id) {
    if (id == null) {
      return null;
    }
    UserProfile u = people.get(id);
    return u == null ? new Person(id, null, null) : new Person(u.getId(), u.getUsername(), u.getDisplayName());
  }

  private KeyView view(McpApiKey k, Workspace ws, Map<UUID, UserProfile> people, boolean showIssuedFrom, boolean mine) {
    Instant now = Instant.now(clock);
    return new KeyView(k.getId(), k.getName(), k.getKeyPrefix(), k.isActive(now) ? "ACTIVE" : "EXPIRED",
        ws == null ? null : ws.getSlug(), ws == null ? null : ws.getName(), person(people, k.getOwnerUserId()),
        k.getCreatedAt(), k.getExpiresAt(), k.getLastUsedAt(), showIssuedFrom ? k.getIssuedIp() : null,
        showIssuedFrom ? k.getIssuedUserAgent() : null, person(people, k.getExpiredBy()),
        k.getExpireReason() == null ? null : k.getExpireReason().name(), k.getExpireNote(), mine);
  }
}
