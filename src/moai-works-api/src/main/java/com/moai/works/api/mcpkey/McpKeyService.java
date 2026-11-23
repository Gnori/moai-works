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
import com.moai.works.api.common.ApiException;
import com.moai.works.api.config.McpEndpoint;
import com.moai.works.api.config.MoaiWorksProperties;
import com.moai.works.api.mcp.McpScope;
import com.moai.works.api.security.access.CurrentUser;
import com.moai.works.api.security.access.WorkspaceContext;
import com.moai.works.api.workspace.WorkspaceMember;
import com.moai.works.api.workspace.WorkspaceRepositories;

/**
 * MCP 키 발급 · 관리 · 검사 (2026-11-22 사용자 결정 — ADR-022 · ADR-024, 2026-11-23 키는 사용자의 것 — ADR-029).
 *
 * <ul>
 *   <li>키 하나 = 주인 계정 하나. 워크스페이스에 묶이지 않는다 — 주인이 멤버이고 지금 역할에 {@code mcp.key.issue.personal} 이 있는
 *       워크스페이스 모두에 닿고, 어느 곳인지는 도구를 부를 때마다 {@link McpScope} 가 정한다</li>
 *   <li>발급은 개인 설정에서(워크스페이스를 고르지 않는다). 사람 계정만(서비스 계정 · 시스템 관리자 계정은 안 됨)이고,
 *       {@code mcp.key.issue.personal} 이 있는 워크스페이스가 적어도 하나 있어야 한다(없으면 403 {@code MCP_KEY_ISSUE_NOT_ALLOWED})</li>
 *   <li>키 주인만 자기 키를 만료시킨다. 워크스페이스 관리자({@code mcp.key.manage})는 그 워크스페이스 멤버들의 키를 보기만 한다 —
 *       사용자 키는 주인의 다른 워크스페이스에도 닿으므로 만료시키지 못하고, 막을 때는 역할에서 MCP 권한을 뺀다</li>
 *   <li>키는 지우지 않는다 — 만료시킬 뿐이다. 발급 · 만료시키기는 사용자 단위 감사(워크스페이스 없음)로 IP · User-Agent 와 함께 남는다</li>
 *   <li>요청마다 캐시 없이 검사한다: 모양 → SHA-256 → 기간 → 주인 계정 활성(시스템 관리자 아님)</li>
 * </ul>
 */
@Service
public class McpKeyService {

  private static final Duration LAST_USED_GAP = Duration.ofMinutes(1);

  private final McpApiKeyRepository keys;
  private final WorkspaceRepositories.Members members;
  private final UserProfileRepository users;
  private final AuditService audit;
  private final McpScope scope;
  private final MoaiWorksProperties.Mcp mcp;
  private final TransactionTemplate tx;
  private final Clock clock;

  public McpKeyService(McpApiKeyRepository keys, WorkspaceRepositories.Members members, UserProfileRepository users,
      AuditService audit, McpScope scope, MoaiWorksProperties properties, PlatformTransactionManager txManager, Clock clock) {
    this.keys = keys;
    this.members = members;
    this.users = users;
    this.audit = audit;
    this.scope = scope;
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
   * @param issuedIp        발급한 IP · {@code issuedUserAgent} 발급한 프로그램 — 주인에게만(사용자 단위 정보라 워크스페이스 관리자에게는 null)
   */
  public record KeyView(UUID id, String name, String keyPrefix, String status, Person owner, Instant createdAt,
      Instant expiresAt, Instant lastUsedAt, String issuedIp, String issuedUserAgent, Person expiredBy,
      String expireReason, String expireNote, boolean mine) {
  }

  /** 발급 결과 — {@code key} 원문은 이 응답에만 있다(다시 볼 수 없다). */
  public record Issued(KeyView view, String key) {
  }

  // ── 발급 ──────────────────────────────────────────────────────────

  public Issued issue(CurrentUser user, String name, Integer days, String userAgent) {
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
    if (scope.usableWorkspaces(user.id()).isEmpty()) {
      throw ApiException.forbidden("MCP_KEY_ISSUE_NOT_ALLOWED",
          "MCP 키로 일할 수 있는 워크스페이스가 없습니다. 워크스페이스 역할에 'MCP 키로 이 워크스페이스에서 일하기'"
              + "(mcp.key.issue.personal) 권한이 있어야 키를 받을 수 있습니다.");
    }
    String token = McpKeyTokenCodec.generate();
    Instant now = Instant.now(clock);
    String ua = userAgent == null ? null : userAgent.substring(0, Math.min(userAgent.length(), 500));
    McpApiKey saved = tx.execute(s -> {
      McpApiKey key = keys.saveAndFlush(new McpApiKey(user.id(),
          user.displayName() == null ? user.username() : user.displayName(), keyName,
          McpKeyTokenCodec.displayPrefix(token), McpKeyTokenCodec.hash(token), now,
          now.plus(Duration.ofDays(validDays)), user.ip(), ua));
      Map<String, Object> details = new LinkedHashMap<>();
      details.put("keyId", key.getId().toString());
      details.put("keyPrefix", key.getKeyPrefix());
      details.put("name", keyName);
      details.put("owner", user.id().toString());
      details.put("expiresAt", key.getExpiresAt().toString());
      details.put("userAgent", ua == null ? "" : ua);
      // 사용자 단위 기록 — 키는 어느 한 워크스페이스의 것이 아니다(audit_log.workspace_id 없음)
      audit.record(user.actor(), null, "mcp.key.issue", "mcp_key", key.getId().toString(), details);
      return key;
    });
    return new Issued(view(saved, people(List.of(saved)), user.id()), token);
  }

  // ── 목록 ──────────────────────────────────────────────────────────

  /**
   * 워크스페이스 관리 화면({@code mcp.key.manage}) — 주인이 지금 그 워크스페이스의 멤버인 키(만료된 것까지 발급 이력).
   * 발급한 IP · 프로그램은 주인에게만 보인다.
   *
   * @param status ACTIVE · EXPIRED · null(전부)
   * @param owner  주인으로 거르기 — 그 워크스페이스 멤버가 아니면 빈 목록
   */
  @Transactional(readOnly = true)
  public List<KeyView> forWorkspace(WorkspaceContext ws, String status, UUID owner) {
    Set<UUID> memberIds = members.findByWorkspace(ws.workspaceId()).stream().map(WorkspaceMember::getUserId)
        .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    if (owner != null) {
      memberIds.retainAll(Set.of(owner));
    }
    List<McpApiKey> rows = memberIds.isEmpty() ? List.of() : keys.findByOwnerUserIdInOrderByCreatedAtDesc(memberIds);
    Map<UUID, UserProfile> people = people(rows);
    Instant now = Instant.now(clock);
    return rows.stream().filter(k -> matches(k, status, now)).map(k -> view(k, people, ws.userId())).toList();
  }

  /** 내 키 — 개인 설정 화면. 키 하나로 내가 MCP 로 일할 수 있는 모든 워크스페이스에 닿는다 */
  @Transactional(readOnly = true)
  public List<KeyView> mine(CurrentUser user, String status) {
    List<McpApiKey> rows = keys.findByOwnerUserIdOrderByCreatedAtDesc(user.id());
    Map<UUID, UserProfile> people = people(rows);
    Instant now = Instant.now(clock);
    return rows.stream().filter(k -> matches(k, status, now)).map(k -> view(k, people, user.id())).toList();
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

  /** 내 키를 지금 만료시킨다 — 남의 키는 없는 것과 같다(404). 키는 지우지 않는다 */
  public KeyView expire(CurrentUser user, UUID keyId, String note, String userAgent) {
    McpApiKey key = keys.findById(keyId).filter(k -> k.getOwnerUserId().equals(user.id()))
        .orElseThrow(() -> ApiException.notFound("MCP_KEY_NOT_FOUND", "MCP 키를 찾을 수 없습니다."));
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
      locked.expireNow(user.id(), McpApiKey.ExpireReason.MANUAL, text, now);
      Map<String, Object> details = new LinkedHashMap<>();
      details.put("keyId", locked.getId().toString());
      details.put("keyPrefix", locked.getKeyPrefix());
      details.put("owner", locked.getOwnerUserId().toString());
      details.put("reason", "MANUAL");
      details.put("note", text == null ? "" : text);
      details.put("userAgent", userAgent == null ? "" : userAgent.substring(0, Math.min(userAgent.length(), 500)));
      audit.record(user.actor(), null, "mcp.key.expire", "mcp_key", locked.getId().toString(), details);
      return locked;
    });
    return view(done, people(List.of(done)), user.id());
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
   * 순서: 모양 → 원문 SHA-256 으로 행 → 기간 → 주인 계정 활성(시스템 관리자 아님).
   * 키는 입구에도 워크스페이스에도 묶이지 않는다 — 어느 워크스페이스 · 프로젝트를 쓸 수 있는지는 도구마다 {@link McpScope} 가
   * 키 주인의 지금 멤버십 · 권한으로 판정한다({@code entry} 는 들어온 입구를 적을 뿐).
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
    if (!ownerActive(key)) {
      return Verdict.reject(403, "MCP_PRINCIPAL_INACTIVE", "키 주인 계정을 쓸 수 없는 상태입니다.");
    }
    keys.touchLastUsed(key.getId(), now, now.minus(LAST_USED_GAP));
    return Verdict.ok(new McpKeyPrincipal(key.getId(), key.getName(), entry, key.getOwnerUserId(), key.getOwnerName()));
  }

  /**
   * 키를 지금 쓸 수 있는가 — 첨부 티켓을 쓸 때 다시 본다(티켓을 받은 뒤 키를 만료시켰거나 주인 계정이 정지됐으면 티켓도 못 쓴다).
   * 검사는 {@link #authenticate} 와 같다: 기간 → 주인 계정 활성. 워크스페이스는 티켓을 쓰는 쪽이 {@link McpScope} 로 다시 본다.
   */
  @Transactional(readOnly = true)
  public boolean usable(UUID keyId) {
    McpApiKey key = keys.findById(keyId).orElse(null);
    return key != null && key.isActive(Instant.now(clock)) && ownerActive(key);
  }

  private boolean ownerActive(McpApiKey key) {
    UserProfile owner = users.findById(key.getOwnerUserId()).orElse(null);
    return owner != null && owner.isActive() && !owner.hasPlatformRole(PlatformRole.SYSTEM_ADMIN);
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

  /** 한 줄 — 발급한 IP · 프로그램은 주인에게만 */
  private KeyView view(McpApiKey k, Map<UUID, UserProfile> people, UUID viewer) {
    Instant now = Instant.now(clock);
    boolean mine = k.getOwnerUserId().equals(viewer);
    return new KeyView(k.getId(), k.getName(), k.getKeyPrefix(), k.isActive(now) ? "ACTIVE" : "EXPIRED",
        person(people, k.getOwnerUserId()), k.getCreatedAt(), k.getExpiresAt(), k.getLastUsedAt(),
        mine ? k.getIssuedIp() : null, mine ? k.getIssuedUserAgent() : null, person(people, k.getExpiredBy()),
        k.getExpireReason() == null ? null : k.getExpireReason().name(), k.getExpireNote(), mine);
  }
}
