package com.moai.works.api.mcp;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.moai.works.api.account.UserProfile;
import com.moai.works.api.issue.IssueViews;

/**
 * MCP 일감 결과의 모양(일감 #282) — REST 의 {@link IssueViews} 를 Agent 가 읽기 좋게 줄인다. 빈 값 빼기 · 사람 보기 줄이기는 결과를 보내기
 * 전에 {@link McpResults} 가 모든 도구에 한다. REST 응답은 그대로다.
 *
 * <ul>
 *   <li>일감 본문(get_issue · create_issue · update_issue): 맨 위 {@code summary} 에 있는 칸({@link #IN_SUMMARY})은 다시 싣지 않는다.
 *       상위 · 하위 · 연결의 {@code project} 는 key, 연결의 id 는 뺀다(MCP 에 연결 id 를 받는 도구가 없다)</li>
 *   <li>이력(journal): 설명이 바뀐 줄은 전 · 후 글 대신 글자 수 {@code chars: {before, after}} — 설명을 고칠 때마다 전 · 후 글 전체가
 *       이력에 쌓여 바쁜 일감의 get_issue 가 3~4만 자였다. 글은 include=["journalDescription"] 로 받는다. 댓글 글(notes)은 그대로.
 *       한 사람은 {@code actor}(아이디, 표시 이름이 다르면 {username, name}), 줄 id 는 댓글 줄에만(update_comment · delete_comment 의 commentId).
 *       get_issue 는 최근 {@value #JOURNAL_RECENT}줄(journalCount 가 전체 수) — 모두는 include=["journal"]</li>
 *   <li>찾기 줄: {@code project} 는 key(이름은 결과 맨 위 {@code projects} 에 한 번), 모듈은 key, 하위가 없으면 {@code childCount} 를 뺀다</li>
 * </ul>
 */
final class IssueShapes {

  /** get_issue 가 기본으로 싣는 최근 이력 줄 수 */
  static final int JOURNAL_RECENT = 20;

  /** summary 에 있어 일감 본문에서 다시 싣지 않는 칸 — summary 는 이 칸들(project 는 key, modules 는 key 목록)을 늘 싣는다 */
  static final List<String> IN_SUMMARY = List.of("number", "project", "type", "title", "status", "priority", "rowVersion", "dueDate",
      "startDate", "plannedEndDate", "completedOn", "closedOn", "estimatedHours", "spentHours", "agentSession", "modules",
      "statusChangedAt");

  private IssueShapes() {
  }

  // ── 일감 본문 ──────────────────────────────────────────────────────

  /** 일감 본문 — summary 에 있는 칸 · 이력 · 작업 시간은 빼고(도구가 따로 싣는다), 상위 · 하위 · 연결을 줄인다. 받은 맵을 고친다 */
  static Map<String, Object> detail(Map<String, Object> issue) {
    IN_SUMMARY.forEach(issue::remove);
    issue.remove("journal");
    issue.remove("workLogs");
    issue.computeIfPresent("parent", (k, v) -> ref(v));
    issue.computeIfPresent("children", (k, v) -> v instanceof List<?> l ? l.stream().map(IssueShapes::ref).toList() : v);
    issue.computeIfPresent("links", (k, v) -> v instanceof List<?> l ? l.stream().map(IssueShapes::link).toList() : v);
    return issue;
  }

  /** 다른 일감을 가리키는 값 {number, project{key, name}, title, status, type} — project 는 key */
  private static Object ref(Object v) {
    if (!(v instanceof Map<?, ?> m)) {
      return v;
    }
    Map<String, Object> out = copy(m);
    out.computeIfPresent("project", (k, p) -> key(p));
    return out;
  }

  /** 연결 하나 — id 는 빼고 상대 일감은 {@link #ref} */
  private static Object link(Object v) {
    if (!(v instanceof Map<?, ?> m)) {
      return v;
    }
    Map<String, Object> out = copy(m);
    out.remove("id");
    out.computeIfPresent("issue", (k, i) -> ref(i));
    return out;
  }

  // ── 찾기 줄 ────────────────────────────────────────────────────────

  /** search_issues 의 한 줄 — project 는 key, 모듈은 key, 하위가 없으면 childCount 를 뺀다. 받은 맵을 고친다 */
  static Map<String, Object> row(Map<String, Object> row) {
    row.computeIfPresent("project", (k, p) -> key(p));
    row.computeIfPresent("modules", (k, v) -> keys(v));
    if (row.get("childCount") instanceof Number c && c.intValue() == 0) {
      row.remove("childCount");
    }
    return row;
  }

  /** 찾은 줄들의 프로젝트 key → 이름(결과 맨 위에 한 번) */
  static Map<String, String> projectNames(Collection<IssueViews.Summary> rows) {
    Map<String, String> out = new LinkedHashMap<>();
    for (IssueViews.Summary s : rows) {
      if (s.project() != null) {
        out.putIfAbsent(s.project().key(), s.project().name());
      }
    }
    return out;
  }

  // ── 댓글 목록 ──────────────────────────────────────────────────────

  /** list_comments 의 한 줄 — actorId · actorName 대신 actor, 사람이 쓴 줄이면 actorType 을 뺀다. 받은 맵을 고친다 */
  static Map<String, Object> comment(Map<String, Object> c, Map<UUID, UserProfile> users) {
    Map<String, Object> out = new LinkedHashMap<>();
    for (Map.Entry<String, Object> e : c.entrySet()) {
      switch (e.getKey()) {
        case "actorId", "actorName" -> {
          if (!out.containsKey("actor")) {
            out.put("actor", actor(c.get("actorType"), uuid(c.get("actorId")), c.get("actorName"), users));
          }
        }
        case "actorType" -> {
          if (!"USER".equals(e.getValue())) {
            out.put("actorType", e.getValue());
          }
        }
        default -> out.put(e.getKey(), e.getValue());
      }
    }
    return out;
  }

  // ── 이력 ──────────────────────────────────────────────────────────

  /** 최근 {@value #JOURNAL_RECENT}줄 — 오래된 것부터 */
  static List<IssueViews.Journal> recent(List<IssueViews.Journal> lines) {
    return lines.size() <= JOURNAL_RECENT ? lines : lines.subList(lines.size() - JOURNAL_RECENT, lines.size());
  }

  /** 이력 줄들이 가리키는 사람 — 한 사람 · 바뀐 관리자 · 담당자(아이디를 찾으려고) */
  static Set<UUID> people(List<IssueViews.Journal> lines) {
    Set<UUID> ids = new LinkedHashSet<>();
    for (IssueViews.Journal j : lines) {
      if (j.actorId() != null) {
        ids.add(j.actorId());
      }
      for (Map<String, Object> c : j.changes() == null ? List.<Map<String, Object>>of() : j.changes()) {
        if (PERSON_FIELDS.contains(String.valueOf(c.get("field")))) {
          addId(ids, c.get("before"));
          addId(ids, c.get("after"));
        }
      }
    }
    return ids;
  }

  /** 바뀐 값이 사람 {id, name} 인 필드 */
  private static final Set<String> PERSON_FIELDS = Set.of("manager", "assignee");

  /**
   * 이력 줄들 — 한 줄: id(댓글 줄만) · actor · actorType(사람이 아닐 때만) · via · createdAt · notes · changes · revisionNo · 댓글의
   * rowVersion · editedAt · deleted · deletedAt · deletedBy. 빈 값은 {@link McpResults} 가 뺀다.
   *
   * @param descriptionText true 면 설명이 바뀐 줄에 전 · 후 글 전체(include=["journalDescription"]), 아니면 글자 수만
   */
  static List<Map<String, Object>> journal(List<IssueViews.Journal> lines, Map<UUID, UserProfile> users, boolean descriptionText) {
    List<Map<String, Object>> out = new ArrayList<>(lines.size());
    for (IssueViews.Journal j : lines) {
      Map<String, Object> m = new LinkedHashMap<>();
      // 줄 id 는 댓글 줄에만 — 댓글을 고치거나 지울 때의 commentId(댓글 줄만 rowVersion 이 있다)
      if (j.rowVersion() != null) {
        m.put("id", j.id().toString());
      }
      m.put("actor", actor(j.actorType(), j.actorId(), j.actorName(), users));
      if (!"USER".equals(j.actorType())) {
        m.put("actorType", j.actorType());
      }
      m.put("via", j.via());
      m.put("createdAt", McpTime.format(j.createdAt()));
      m.put("notes", j.notes());
      m.put("changes", changes(j.changes(), users, descriptionText));
      m.put("revisionNo", j.revisionNo());
      m.put("rowVersion", j.rowVersion());
      m.put("editedAt", McpTime.format(j.editedAt()));
      m.put("deleted", j.deleted());
      m.put("deletedAt", McpTime.format(j.deletedAt()));
      m.put("deletedBy", j.deletedBy() == null ? null
          : McpResults.person(j.deletedBy().id(), j.deletedBy().username(), j.deletedBy().displayName()));
      out.add(m);
    }
    return out;
  }

  /**
   * 바뀐 것 [{field, before, after}] — 설명은 글자 수 {@code chars: {before, after}}(descriptionText 면 글 그대로), 관리자 · 담당자는 사람,
   * 모듈은 key 목록, 프로젝트는 key, 작업 시간은 {id, hours}(나머지는 workLogs), 댓글 고치기 · 지우기는 댓글 id. 그 밖은 그대로
   */
  private static List<Map<String, Object>> changes(List<Map<String, Object>> changes, Map<UUID, UserProfile> users,
      boolean descriptionText) {
    if (changes == null) {
      return null;
    }
    List<Map<String, Object>> out = new ArrayList<>(changes.size());
    for (Map<String, Object> c : changes) {
      String field = String.valueOf(c.get("field"));
      Object before = c.get("before");
      Object after = c.get("after");
      // field · before · after 밖의 칸(예: 작업 시간 지우기의 reason)은 그대로
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("field", field);
      if ("description".equals(field) && !descriptionText) {
        Map<String, Object> chars = new LinkedHashMap<>();
        chars.put("before", chars(before));
        chars.put("after", chars(after));
        m.put("chars", chars);
      } else {
        m.put("before", value(field, before, users));
        m.put("after", value(field, after, users));
      }
      c.forEach((k, v) -> {
        if (!"field".equals(k) && !"before".equals(k) && !"after".equals(k)) {
          m.put(k, v);
        }
      });
      out.add(m);
    }
    return out;
  }

  private static Object value(String field, Object v, Map<UUID, UserProfile> users) {
    if (v == null) {
      return null;
    }
    return switch (field) {
      case "manager", "assignee" -> v instanceof Map<?, ?> m ? user(uuid(m.get("id")), m.get("name"), users) : v;
      case "modules" -> keys(v);
      case "project" -> key(v);
      case "workLog" -> {
        if (!(v instanceof Map<?, ?> m)) {
          yield v;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", m.get("id"));
        out.put("hours", m.get("hours"));
        yield out;
      }
      case "comment" -> v instanceof Map<?, ?> m ? m.get("id") : v;
      default -> v;
    };
  }

  /** 한 사람 — 사람(USER)이면 계정 사본의 아이디로, 서비스면 이름 */
  private static Object actor(Object actorType, UUID actorId, Object actorName, Map<UUID, UserProfile> users) {
    if (!"USER".equals(actorType) || actorId == null) {
      return actorName;
    }
    return user(actorId, actorName, users);
  }

  /** id · 이력에 남은 이름 → 아이디(표시 이름이 다르면 {username, name}). 계정 사본이 없으면 이력의 이름 */
  private static Object user(UUID id, Object name, Map<UUID, UserProfile> users) {
    UserProfile u = id == null ? null : users.get(id);
    if (u == null) {
      return name != null ? name : id == null ? null : id.toString();
    }
    return McpResults.person(u.getId(), u.getUsername(), u.getDisplayName());
  }

  // ── 값 ────────────────────────────────────────────────────────────

  /** {key, name} → key */
  private static Object key(Object v) {
    return v instanceof Map<?, ?> m && m.containsKey("key") ? m.get("key") : v;
  }

  /** [{key, …}] → [key] */
  private static Object keys(Object v) {
    return v instanceof List<?> l ? l.stream().map(IssueShapes::key).toList() : v;
  }

  /** 글자 수(유니코드 글자) — 없으면 0 */
  private static int chars(Object v) {
    if (v == null) {
      return 0;
    }
    String s = v.toString();
    return s.codePointCount(0, s.length());
  }

  private static void addId(Set<UUID> ids, Object v) {
    if (v instanceof Map<?, ?> m) {
      UUID id = uuid(m.get("id"));
      if (id != null) {
        ids.add(id);
      }
    }
  }

  private static UUID uuid(Object v) {
    if (v instanceof UUID u) {
      return u;
    }
    if (v == null) {
      return null;
    }
    try {
      return UUID.fromString(v.toString());
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private static Map<String, Object> copy(Map<?, ?> m) {
    Map<String, Object> out = new LinkedHashMap<>();
    m.forEach((k, v) -> out.put(String.valueOf(k), v));
    return out;
  }
}
