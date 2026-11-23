package com.moai.works.api.issue;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.moai.works.api.common.ApiException;
import com.moai.works.api.common.Ids;
import com.moai.works.api.mcp.McpScope;
import com.moai.works.api.mcpkey.McpKeyPrincipal;
import com.moai.works.api.mcpkey.McpKeyService;
import com.moai.works.api.security.access.ProjectContext;
import com.moai.works.api.security.access.WorkspaceContext;
import com.moai.works.api.workspace.Permission;
import com.moai.works.api.workspace.WorkspaceRepositories;

/**
 * 첨부 일회용 티켓 (V10) — 바깥 AI Agent 가 파일을 MCP 메시지에 싣지 않고 REST 로 보내게 한다(2026-11-22 사용자 제안).
 *
 * <ul>
 *   <li>MCP 도구가 티켓을 준다 — 원문 {@code mwt_<43자>} 는 그 응답에만, DB 에는 SHA-256 만</li>
 *   <li>몇 분({@link #TTL})만 유효하고 <b>한 번 쓰면 끝</b>(원자적으로 used_at 을 채운 쪽만 통과 — 두 번째는 401)</li>
 *   <li>키 주인 · 키 · 워크스페이스 · 프로젝트 · 일감(올리기는 이름 · 크기, 받기는 첨부)에 묶인다</li>
 *   <li>쓸 때 다시 판정한다: 키가 아직 쓸 수 있는가, 그 워크스페이스가 아직 키로 쓸 수 있는 곳인가(멤버 ∧ MCP 권한 — {@link McpScope}),
 *       키 주인이 지금 그 일감의 프로젝트(워크스페이스 번호로 다시 찾는다 — 옮겼으면 옮긴 곳)에서 그 권한을 가졌는가. 키는 사용자의 것이고(ADR-029) 티켓은 도구가 정한 프로젝트의 워크스페이스에 묶인다</li>
 * </ul>
 */
@Service
public class AttachmentTicketService {

  public static final Duration TTL = Duration.ofMinutes(10);
  private static final String PREFIX = "mwt_";
  private static final SecureRandom RANDOM = new SecureRandom();

  public enum Kind {
    UPLOAD, DOWNLOAD
  }

  /** 쓴 티켓 — 다시 판정한 프로젝트 문맥과 묶인 값 */
  public record Use(ProjectContext ctx, McpKeyPrincipal principal, int issueNumber, UUID attachmentId, String fileName,
      String contentType, long maxBytes) {
  }

  /** 준 티켓 — token 원문은 이 값에만 */
  public record Issued(String token, Instant expiresAt) {
  }

  private final NamedParameterJdbcTemplate jdbc;
  private final McpKeyService keys;
  private final WorkspaceRepositories.Workspaces workspaces;
  private final McpScope scope;
  private final IssueLocator locator;
  private final com.moai.works.api.account.UserProfileRepository users;
  private final Clock clock;

  public AttachmentTicketService(NamedParameterJdbcTemplate jdbc, McpKeyService keys,
      WorkspaceRepositories.Workspaces workspaces, McpScope scope, IssueLocator locator,
      com.moai.works.api.account.UserProfileRepository users, Clock clock) {
    this.jdbc = jdbc;
    this.keys = keys;
    this.workspaces = workspaces;
    this.scope = scope;
    this.locator = locator;
    this.users = users;
    this.clock = clock;
  }

  /** 티켓을 남긴다 — 판정(권한 · 일감 · 크기)은 부르는 쪽(MCP 도구)이 이미 했다 */
  @Transactional
  public Issued issue(Kind kind, ProjectContext ctx, McpKeyPrincipal p, int issueNumber, UUID attachmentId, String fileName,
      String contentType, Long maxBytes) {
    byte[] raw = new byte[32];
    RANDOM.nextBytes(raw);
    String token = PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    Instant now = Instant.now(clock);
    Instant expires = now.plus(TTL);
    jdbc.update("""
        INSERT INTO attachment_ticket (id, token_hash, kind, mcp_key_id, owner_user_id, workspace_id, project_id, issue_number,
                                       attachment_id, file_name, content_type, max_bytes, created_at, expires_at)
        VALUES (:id, :hash, :kind, :key, :owner, :ws, :project, :number, :attachment, :name, :type, :max, :now, :expires)
        """, new MapSqlParameterSource("id", Ids.next()).addValue("hash", hash(token)).addValue("kind", kind.name())
        .addValue("key", p.keyId()).addValue("owner", p.ownerUserId()).addValue("ws", ctx.workspaceId())
        .addValue("project", ctx.projectId()).addValue("number", issueNumber).addValue("attachment", attachmentId)
        .addValue("name", fileName).addValue("type", contentType).addValue("max", maxBytes)
        .addValue("now", Timestamp.from(now)).addValue("expires", Timestamp.from(expires)));
    return new Issued(token, expires);
  }

  /**
   * 티켓을 쓴다 — 원자적으로 한 번만. 없거나 · 쓴 것이거나 · 기간이 지났거나 · 종류가 다르면 401 TICKET_INVALID.
   * 그 뒤 키 · 키 주인의 지금 권한을 다시 본다(못 쓰면 403).
   */
  @Transactional
  public Use use(String token, Kind kind) {
    if (token == null || !token.startsWith(PREFIX) || token.length() != PREFIX.length() + 43) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "TICKET_INVALID", "첨부 티켓이 올바르지 않습니다.");
    }
    Instant now = Instant.now(clock);
    List<Row> rows = jdbc.query("""
        UPDATE attachment_ticket SET used_at = :now
        WHERE token_hash = :hash AND kind = :kind AND used_at IS NULL AND expires_at > :now
        RETURNING mcp_key_id, owner_user_id, workspace_id, project_id, issue_number, attachment_id, file_name, content_type,
                  max_bytes
        """, new MapSqlParameterSource("now", Timestamp.from(now)).addValue("hash", hash(token)).addValue("kind", kind.name()),
        (rs, i) -> new Row(rs.getObject("mcp_key_id", UUID.class), rs.getObject("owner_user_id", UUID.class),
            rs.getObject("workspace_id", UUID.class), rs.getObject("project_id", UUID.class), rs.getInt("issue_number"),
            rs.getObject("attachment_id", UUID.class), rs.getString("file_name"), rs.getString("content_type"),
            rs.getObject("max_bytes") == null ? 0L : rs.getLong("max_bytes")));
    if (rows.isEmpty()) {
      throw new ApiException(HttpStatus.UNAUTHORIZED, "TICKET_INVALID", "첨부 티켓이 없거나, 이미 썼거나, 기간이 지났습니다.");
    }
    Row r = rows.get(0);
    if (!keys.usable(r.keyId())) {
      throw ApiException.forbidden("MCP_KEY_NOT_USABLE", "티켓을 받은 MCP 키를 더 쓸 수 없습니다.");
    }
    // 그 사이 워크스페이스를 쓸 수 없게 됐거나 · 떠났거나 · MCP 권한을 잃었으면 키에게 보이지 않는다(도구와 같은 404)
    WorkspaceContext wctx = workspaces.findById(r.workspaceId()).flatMap(w -> scope.usable(w, r.ownerId()))
        .orElseThrow(() -> ApiException.notFound("WORKSPACE_NOT_FOUND", "워크스페이스를 찾을 수 없습니다."));
    // 일감은 워크스페이스 번호로 다시 찾는다 — 그 사이 다른 프로젝트로 옮겼으면 지금 프로젝트로 판정한다(독자가 아니면 404)
    ProjectContext ctx = locator.contextFor(wctx, r.issueNumber())
        .orElseThrow(() -> ApiException.notFound("ISSUE_NOT_FOUND", "일감을 찾을 수 없습니다."));
    Permission needed = kind == Kind.UPLOAD ? Permission.ISSUE_COMMENT : Permission.ISSUE_READ;
    if (!ctx.has(needed)) {
      throw ApiException.forbidden("PERMISSION_REQUIRED", "권한 " + needed.code() + " 이(가) 필요합니다.");
    }
    String ownerName = users.findById(r.ownerId()).map(u -> u.getDisplayName() == null ? u.getUsername() : u.getDisplayName())
        .orElse(null);
    McpKeyPrincipal principal = new McpKeyPrincipal(r.keyId(), null, null, r.ownerId(), ownerName);
    return new Use(ctx, principal, r.issueNumber(), r.attachmentId(), r.fileName(), r.contentType(), r.maxBytes());
  }

  private record Row(UUID keyId, UUID ownerId, UUID workspaceId, UUID projectId, int issueNumber, UUID attachmentId,
      String fileName, String contentType, long maxBytes) {
  }

  private static String hash(String token) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
