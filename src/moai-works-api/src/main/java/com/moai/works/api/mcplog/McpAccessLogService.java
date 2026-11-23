package com.moai.works.api.mcplog;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import javax.sql.DataSource;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.moai.works.api.common.ApiException;

import tools.jackson.databind.json.JsonMapper;

/**
 * MCP 접근 기록 — 쓰기(추가만) · 거르기 · 내려받기 · 키별 호출 수 (FR-MCP-006 · FR-MCP-010).
 *
 * <ul>
 *   <li>쓰기는 지금 트랜잭션에 함께 들어간다 — 도구 호출은 도구의 쓰기와 이 줄이 같이 저장되거나 같이 되돌려진다.
 *       저장하지 못하면 {@link WriteFailed} 를 던지고, 부른 쪽이 그 요청을 실패시킨다</li>
 *   <li>보는 범위({@link Scope})는 부른 쪽이 정한다 — 워크스페이스({@code audit.read})면 그 워크스페이스 줄만, 키면 그 키의 줄 전부</li>
 *   <li>최신순, 커서는 앞 쪽 마지막 줄의 시각과 id(감사 기록과 같다) — 같은 밀리초의 줄도 건너뛰거나 겹치지 않는다</li>
 * </ul>
 */
@Service
public class McpAccessLogService {

  /** 한 번에 받는 줄 수 상한 */
  public static final int MAX_PAGE = 200;
  /** 내려받기 상한 */
  public static final int MAX_EXPORT = 100_000;

  private static final Set<String> OPERATIONS = Set.of("READ", "CREATE", "UPDATE", "DELETE", "LINK", "LIST", "CONNECT");
  private static final Set<String> OUTCOMES = Set.of("OK", "DENIED", "ERROR");
  private static final Pattern TOOL = Pattern.compile("[a-z][a-z0-9_]{0,59}");

  private static final String INSERT = """
      INSERT INTO mcp_access_log (id, occurred_at, duration_ms, key_id, key_prefix, key_name, key_owner_id, key_owner_name,
          entry, request_path, workspace_id, mcp_method, tool, operation, project_key, issue_number, target_ref, arguments,
          outcome, http_status, error_code, client_name, client_version, client_title, protocol_version, client_capabilities,
          mcp_session_id, user_agent, client_ip, trace_id)
      VALUES (:id, :occurredAt, :durationMs, :keyId, :keyPrefix, :keyName, :keyOwnerId, :keyOwnerName,
          :entry, :requestPath, :workspaceId, :mcpMethod, :tool, :operation, :projectKey, :issueNumber, :targetRef,
          CAST(:arguments AS jsonb), :outcome, :httpStatus, :errorCode, :clientName, :clientVersion, :clientTitle,
          :protocolVersion, :clientCapabilities, :sessionId, :userAgent, :clientIp, :traceId)
      """;

  private static final String COLUMNS = """
      l.id, l.occurred_at, l.duration_ms, l.key_id, l.key_prefix, l.key_name, l.key_owner_id, l.key_owner_name, l.entry,
      l.request_path, w.slug AS workspace_slug, w.name AS workspace_name, l.mcp_method, l.tool, l.operation, l.project_key,
      l.issue_number, l.target_ref, l.arguments::text AS arguments, l.outcome, l.http_status, l.error_code, l.client_name,
      l.client_version, l.client_title, l.protocol_version, l.client_capabilities, l.mcp_session_id, l.user_agent,
      l.client_ip, l.trace_id
      """;

  private final NamedParameterJdbcTemplate jdbc;
  private final NamedParameterJdbcTemplate streaming;
  private final TransactionTemplate readOnly;
  private final JsonMapper json;

  public McpAccessLogService(NamedParameterJdbcTemplate jdbc, DataSource dataSource, PlatformTransactionManager txManager,
      JsonMapper json) {
    this.jdbc = jdbc;
    JdbcTemplate fetching = new JdbcTemplate(dataSource);
    fetching.setFetchSize(1000);
    this.streaming = new NamedParameterJdbcTemplate(fetching);
    this.readOnly = new TransactionTemplate(txManager);
    this.readOnly.setReadOnly(true);
    this.json = json;
  }

  // ── 쓰기 ──────────────────────────────────────────────────────────

  /** 접근 기록을 저장하지 못했다 — 그 요청도 실패시킨다(남기지 못한 호출이 없게) */
  public static final class WriteFailed extends RuntimeException {
    WriteFailed(Throwable cause) {
      super("MCP 접근 기록을 저장하지 못했습니다", cause);
    }
  }

  /** 한 줄 추가 — 지금 트랜잭션이 있으면 그 안에서 */
  void insert(McpAccessRow r) {
    MapSqlParameterSource p = new MapSqlParameterSource()
        .addValue("id", r.id)
        .addValue("occurredAt", Timestamp.from(r.occurredAt))
        .addValue("durationMs", r.durationMs)
        .addValue("keyId", r.keyId)
        .addValue("keyPrefix", McpSecrets.cut(r.keyPrefix, 16))
        .addValue("keyName", McpSecrets.cut(r.keyName, 100))
        .addValue("keyOwnerId", r.keyOwnerId)
        .addValue("keyOwnerName", McpSecrets.cut(r.keyOwnerName, 200))
        .addValue("entry", r.entry)
        .addValue("requestPath", McpSecrets.cut(r.requestPath == null ? "" : r.requestPath, 200))
        .addValue("workspaceId", r.workspaceId)
        .addValue("mcpMethod", McpSecrets.cut(r.mcpMethod == null ? "unknown" : r.mcpMethod, 60))
        .addValue("tool", McpSecrets.cut(r.tool, 60))
        .addValue("operation", r.operation.name())
        .addValue("projectKey", McpSecrets.cut(r.projectKey, 32))
        .addValue("issueNumber", r.issueNumber)
        .addValue("targetRef", McpSecrets.cut(r.targetRef, 100))
        .addValue("arguments", r.arguments == null ? null : json.writeValueAsString(r.arguments))
        .addValue("outcome", r.outcome.name())
        .addValue("httpStatus", r.httpStatus)
        .addValue("errorCode", McpSecrets.cut(r.errorCode, 60))
        .addValue("clientName", r.client.name())
        .addValue("clientVersion", r.client.version())
        .addValue("clientTitle", r.client.title())
        .addValue("protocolVersion", r.client.protocolVersion())
        .addValue("clientCapabilities", r.client.capabilities())
        .addValue("sessionId", McpSecrets.cut(r.sessionId, 100))
        .addValue("userAgent", McpSecrets.cut(McpSecrets.mask(r.userAgent), 500))
        .addValue("clientIp", McpSecrets.cut(r.clientIp, 64))
        .addValue("traceId", McpSecrets.cut(r.traceId, 64));
    try {
      jdbc.update(INSERT, p);
    } catch (DataAccessException e) {
      throw new WriteFailed(e);
    }
  }

  // ── 보기 ──────────────────────────────────────────────────────────

  /** 보는 범위 — 워크스페이스 하나(그 워크스페이스에서 쓴 줄) 또는 키 하나(그 키의 줄 전부, 워크스페이스가 없는 줄까지) */
  public record Scope(UUID workspaceId, UUID keyId) {
    public static Scope workspace(UUID workspaceId) {
      return new Scope(workspaceId, null);
    }

    public static Scope key(UUID keyId) {
      return new Scope(null, keyId);
    }
  }

  /**
   * 거르기 — 모두 함께(AND). 비운 값은 조건이 아니다.
   *
   * @param key        키 id
   * @param person     키 주인(사용자 id)
   * @param tool       도구 이름
   * @param operation  작업 종류 — 여럿이면 그 가운데 하나(READ · CREATE · UPDATE · DELETE · LINK · LIST · CONNECT)
   * @param outcome    결과 — 여럿이면 그 가운데 하나(OK · DENIED · ERROR)
   * @param client     클라이언트 이름에 든 말(대소문자 무시)
   * @param project    프로젝트 key
   * @param session    MCP 세션 id
   * @param ip         클라이언트 IP
   * @param from       이 시각부터 · {@code to} 이 시각 전까지
   * @param before     더 보기 커서 — 앞 쪽 마지막 줄의 시각. {@code beforeId}(그 줄의 id)를 함께 준다
   */
  public record Filter(UUID key, UUID person, String tool, List<String> operation, List<String> outcome, String client,
      String project, String session, String ip, Instant from, Instant to, Instant before, UUID beforeId) {

    public Filter {
      operation = upper(operation, OPERATIONS, "작업 종류는 READ · CREATE · UPDATE · DELETE · LINK · LIST · CONNECT 가운데 하나입니다.");
      outcome = upper(outcome, OUTCOMES, "결과는 OK · DENIED · ERROR 가운데 하나입니다.");
      tool = blankToNull(tool);
      if (tool != null && !TOOL.matcher(tool).matches()) {
        throw ApiException.badRequest("INVALID_FILTER", "도구 이름이 올바르지 않습니다.");
      }
      client = limited(client, 200, "클라이언트");
      project = limited(project, 32, "프로젝트");
      session = limited(session, 100, "세션 id");
      ip = limited(ip, 64, "IP");
    }

    /** 내려받기 감사에 남길 조건 — 비운 것은 뺀다(커서는 넣지 않는다) */
    Map<String, Object> describe() {
      Map<String, Object> m = new java.util.LinkedHashMap<>();
      putIf(m, "key", key);
      putIf(m, "person", person);
      putIf(m, "tool", tool);
      putIf(m, "operation", operation.isEmpty() ? null : String.join(",", operation));
      putIf(m, "outcome", outcome.isEmpty() ? null : String.join(",", outcome));
      putIf(m, "client", client);
      putIf(m, "project", project);
      putIf(m, "session", session);
      putIf(m, "ip", ip);
      putIf(m, "from", from);
      putIf(m, "to", to);
      return m;
    }

    private static void putIf(Map<String, Object> m, String name, Object value) {
      if (value != null) {
        m.put(name, value.toString());
      }
    }

    private static List<String> upper(List<String> values, Set<String> allowed, String message) {
      if (values == null) {
        return List.of();
      }
      List<String> out = new ArrayList<>();
      for (String v : values) {
        if (v == null || v.isBlank()) {
          continue;
        }
        for (String part : v.split(",")) {
          String s = part.trim().toUpperCase(Locale.ROOT);
          if (s.isEmpty()) {
            continue;
          }
          if (!allowed.contains(s)) {
            throw ApiException.badRequest("INVALID_FILTER", message);
          }
          if (!out.contains(s)) {
            out.add(s);
          }
        }
      }
      return List.copyOf(out);
    }

    private static String limited(String value, int max, String label) {
      String v = blankToNull(value);
      if (v != null && v.length() > max) {
        throw ApiException.badRequest("INVALID_FILTER", label + " 조건은 " + max + "자를 넘을 수 없습니다.");
      }
      return v;
    }

    private static String blankToNull(String value) {
      return value == null || value.isBlank() ? null : value.trim();
    }
  }

  /**
   * 기록 한 줄 — 키 원문 · 본문은 없다. {@code arguments} 는 도구 인자 요약(JSON 글자, 긴 글은 길이 · sha256 만).
   *
   * @param workspaceSlug 도구가 정한 워크스페이스 — 없으면 null(연결 · 도구 목록 · 거절 · 여러 워크스페이스에 걸친 목록)
   */
  public record Entry(UUID id, Instant occurredAt, int durationMs, UUID keyId, String keyPrefix, String keyName,
      UUID ownerId, String ownerName, String entry, String requestPath, String workspaceSlug, String workspaceName,
      String mcpMethod, String tool, String operation, String projectKey, Integer issueNumber, String targetRef,
      String arguments, String outcome, int httpStatus, String errorCode, String clientName, String clientVersion,
      String clientTitle, String protocolVersion, String clientCapabilities, String sessionId, String userAgent,
      String ip, String traceId) {
  }

  private static final RowMapper<Entry> ENTRY = McpAccessLogService::entry;

  /** 최신순 한 쪽 — limit 은 1~200 */
  public List<Entry> search(Scope scope, Filter f, int limit) {
    MapSqlParameterSource p = new MapSqlParameterSource();
    String where = where(scope, f, p, true);
    p.addValue("limit", Math.min(Math.max(limit, 1), MAX_PAGE));
    return readOnly.execute(s -> jdbc.query("SELECT " + COLUMNS + " FROM mcp_access_log l LEFT JOIN workspace w ON w.id = l.workspace_id WHERE "
        + where + " ORDER BY l.occurred_at DESC, l.id DESC LIMIT :limit", p, ENTRY));
  }

  /** 거른 줄 수(커서 없이) — 내려받기 감사의 줄 수. 상한에서 멈춘다 */
  public int count(Scope scope, Filter f) {
    MapSqlParameterSource p = new MapSqlParameterSource();
    String where = where(scope, f, p, false);
    p.addValue("limit", MAX_EXPORT);
    Integer n = readOnly.execute(s -> jdbc.queryForObject(
        "SELECT count(*) FROM (SELECT 1 FROM mcp_access_log l WHERE " + where + " LIMIT :limit) c", p, Integer.class));
    return n == null ? 0 : n;
  }

  /** CSV 머리 줄 */
  static final List<String> CSV_COLUMNS = List.of("occurredAt", "durationMs", "outcome", "httpStatus", "errorCode",
      "operation", "mcpMethod", "tool", "workspace", "projectKey", "issueNumber", "targetRef", "keyPrefix", "keyName",
      "keyId", "ownerName", "ownerId", "entry", "requestPath", "clientName", "clientVersion", "clientTitle",
      "protocolVersion", "clientCapabilities", "sessionId", "userAgent", "ip", "traceId", "arguments");

  /**
   * 거른 결과를 CSV 로(최신순, 최대 {@value #MAX_EXPORT}줄). 머리 줄에는 UTF-8 BOM 을 붙인다(엑셀이 한글을 바로 읽게).
   * 셀이 수식 글자(= + - @ 탭 · 줄바꿈)로 시작하면 앞에 ' 를 붙인다(CSV 수식 주입 막기).
   *
   * @return 쓴 줄 수(머리 줄 빼고)
   */
  public int export(Scope scope, Filter f, Writer out) {
    MapSqlParameterSource p = new MapSqlParameterSource();
    String where = where(scope, f, p, false);
    p.addValue("limit", MAX_EXPORT);
    int[] rows = {0};
    try {
      out.write('﻿');
      out.write(String.join(",", CSV_COLUMNS));
      out.write("\r\n");
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    readOnly.executeWithoutResult(s -> streaming.query("SELECT " + COLUMNS
        + " FROM mcp_access_log l LEFT JOIN workspace w ON w.id = l.workspace_id WHERE " + where
        + " ORDER BY l.occurred_at DESC, l.id DESC LIMIT :limit", p, rs -> {
          Entry e = entry(rs, rows[0]);
          try {
            out.write(csvLine(e));
          } catch (IOException ex) {
            throw new UncheckedIOException(ex);
          }
          rows[0]++;
        }));
    return rows[0];
  }

  static String csvLine(Entry e) {
    List<Object> cells = List.of(e.occurredAt(), e.durationMs(), e.outcome(), e.httpStatus(), nz(e.errorCode()),
        e.operation(), e.mcpMethod(), nz(e.tool()), nz(e.workspaceSlug()), nz(e.projectKey()), nz(e.issueNumber()),
        nz(e.targetRef()), nz(e.keyPrefix()), nz(e.keyName()), nz(e.keyId()), nz(e.ownerName()), nz(e.ownerId()),
        e.entry(), nz(e.requestPath()), nz(e.clientName()), nz(e.clientVersion()), nz(e.clientTitle()),
        nz(e.protocolVersion()), nz(e.clientCapabilities()), nz(e.sessionId()), nz(e.userAgent()), nz(e.ip()),
        nz(e.traceId()), nz(e.arguments()));
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < cells.size(); i++) {
      if (i > 0) {
        sb.append(',');
      }
      sb.append(csvCell(String.valueOf(cells.get(i))));
    }
    return sb.append("\r\n").toString();
  }

  static String csvCell(String value) {
    String v = value;
    if (!v.isEmpty() && "=+-@\t\r\n".indexOf(v.charAt(0)) >= 0) {
      v = "'" + v;
    }
    if (v.indexOf(',') >= 0 || v.indexOf('"') >= 0 || v.indexOf('\n') >= 0 || v.indexOf('\r') >= 0) {
      v = "\"" + v.replace("\"", "\"\"") + "\"";
    }
    return v;
  }

  private static Object nz(Object value) {
    return value == null ? "" : value;
  }

  // ── 키별 호출 수 ──────────────────────────────────────────────────

  /** 키마다 접근 기록 줄 수 — 내 키 목록의 "호출 수"(사용량은 접근 기록에서 센다, FR-MCP-006) */
  public Map<UUID, Long> countByKeys(Collection<UUID> keyIds) {
    Map<UUID, Long> out = new HashMap<>();
    if (keyIds == null || keyIds.isEmpty()) {
      return out;
    }
    readOnly.executeWithoutResult(s -> jdbc.query(
        "SELECT key_id, count(*) AS n FROM mcp_access_log WHERE key_id IN (:ids) GROUP BY key_id",
        new MapSqlParameterSource("ids", List.copyOf(keyIds)),
        rs -> {
          out.put(rs.getObject("key_id", UUID.class), rs.getLong("n"));
        }));
    return out;
  }

  // ── 질의 ──────────────────────────────────────────────────────────

  private static String where(Scope scope, Filter f, MapSqlParameterSource p, boolean cursor) {
    StringBuilder w = new StringBuilder();
    if (scope.workspaceId() != null) {
      w.append("l.workspace_id = :scopeWs");
      p.addValue("scopeWs", scope.workspaceId());
    } else if (scope.keyId() != null) {
      w.append("l.key_id = :scopeKey");
      p.addValue("scopeKey", scope.keyId());
    } else {
      throw new IllegalArgumentException("보는 범위가 없습니다");
    }
    if (f.key() != null) {
      w.append(" AND l.key_id = :key");
      p.addValue("key", f.key());
    }
    if (f.person() != null) {
      w.append(" AND l.key_owner_id = :person");
      p.addValue("person", f.person());
    }
    if (f.tool() != null) {
      w.append(" AND l.tool = :tool");
      p.addValue("tool", f.tool());
    }
    if (!f.operation().isEmpty()) {
      w.append(" AND l.operation IN (:operations)");
      p.addValue("operations", f.operation());
    }
    if (!f.outcome().isEmpty()) {
      w.append(" AND l.outcome IN (:outcomes)");
      p.addValue("outcomes", f.outcome());
    }
    if (f.client() != null) {
      w.append(" AND l.client_name ILIKE :client ESCAPE '\\'");
      p.addValue("client", "%" + f.client().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%");
    }
    if (f.project() != null) {
      w.append(" AND l.project_key = :project");
      p.addValue("project", f.project());
    }
    if (f.session() != null) {
      w.append(" AND l.mcp_session_id = :session");
      p.addValue("session", f.session());
    }
    if (f.ip() != null) {
      w.append(" AND l.client_ip = :ip");
      p.addValue("ip", f.ip());
    }
    if (f.from() != null) {
      w.append(" AND l.occurred_at >= :from");
      p.addValue("from", Timestamp.from(f.from()));
    }
    if (f.to() != null) {
      w.append(" AND l.occurred_at < :to");
      p.addValue("to", Timestamp.from(f.to()));
    }
    if (cursor && f.before() != null) {
      if (f.beforeId() == null) {
        w.append(" AND l.occurred_at < :before");
      } else {
        w.append(" AND (l.occurred_at, l.id) < (:before, :beforeId)");
        p.addValue("beforeId", f.beforeId());
      }
      p.addValue("before", Timestamp.from(f.before()));
    }
    return w.toString();
  }

  private static Entry entry(ResultSet rs, int rowNum) throws SQLException {
    Timestamp at = rs.getTimestamp("occurred_at");
    return new Entry(rs.getObject("id", UUID.class), at == null ? null : at.toInstant(), rs.getInt("duration_ms"),
        rs.getObject("key_id", UUID.class), rs.getString("key_prefix"), rs.getString("key_name"),
        rs.getObject("key_owner_id", UUID.class), rs.getString("key_owner_name"), rs.getString("entry"),
        rs.getString("request_path"), rs.getString("workspace_slug"), rs.getString("workspace_name"),
        rs.getString("mcp_method"), rs.getString("tool"), rs.getString("operation"), rs.getString("project_key"),
        (Integer) rs.getObject("issue_number"), rs.getString("target_ref"), rs.getString("arguments"),
        rs.getString("outcome"), rs.getInt("http_status"), rs.getString("error_code"), rs.getString("client_name"),
        rs.getString("client_version"), rs.getString("client_title"), rs.getString("protocol_version"),
        rs.getString("client_capabilities"), rs.getString("mcp_session_id"), rs.getString("user_agent"),
        rs.getString("client_ip"), rs.getString("trace_id"));
  }
}
