package db.migration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.IntUnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * V23 — 일감은 워크스페이스 아래에 있고, 번호는 워크스페이스 안에서 하나다(2026-11-24 사용자 결정, ADR-034 · moai-works 일감 #29).
 *
 * <blockquote>"이슈 넘버가 프로젝트 별로 하는데 아니야 워크스페이스 별로 해야해" · "워크스페이스 단위로 유니크한 번호여야해" ·
 * "이슈는 프로젝트 단위에 포함이 되지만 기본적으로 워크스페이스 아래 있는것이고 이슈는 프로젝트는 속성처럼 가지고 있어야해" ·
 * "레드마인을 생각하면 돼"</blockquote>
 *
 * <p>한 트랜잭션(Flyway 의 이 마이그레이션 하나)에서 모두 한다 — 하나라도 실패하면 아무것도 바뀌지 않는다.
 * <ol>
 *   <li>표 — {@code issue.workspace_id}(프로젝트와 같은 워크스페이스여야 한다: 복합 외래 키), {@code workspace.issue_seq}(워크스페이스
 *       번호 순번), {@code issue_revision.project_id}(그 판의 프로젝트 — 옮기기가 판에 남는다), 이력 · 판의 경로에 {@code SYSTEM}(이 일처럼
 *       사람이 아닌 시스템이 한 일), 번호 바꾼 기록 {@code issue_number_change} · 바꾼 글의 원문 {@code issue_number_text_change}</li>
 *   <li>번호 — 워크스페이스마다 만든 때(created_at) 순, 같으면 옛 번호 · 프로젝트 · id 순으로 1 부터 다시 매긴다(처음부터 워크스페이스
 *       번호였던 것처럼). 지운 일감도 번호를 받는다(번호는 다시 쓰지 않는다). 돌 때의 데이터로 계산한다(그 사이 만든 일감도 들어간다)</li>
 *   <li>가리키는 번호 — 같은 일감을 계속 가리키게 옛 번호를 새 번호로: 판의 상위 번호({@code issue_revision.parent_number}),
 *       첨부 티켓({@code attachment_ticket.issue_number}), MCP 접근 기록({@code mcp_access_log.issue_number} — "어느 일감"을 찾는
 *       식별자라서 · link_issues 의 대상 번호 {@code target_ref} 도), 이력 줄의 상위 · 연결 번호({@code issue_journal.changes}),
 *       알림의 번호({@code notification.params} 의 number · childNumber · otherNumber). 감사 기록의 글은 쓴 그대로 둔다</li>
 *   <li>글 속 {@code #번호} — 지금 일감 설명과 댓글에서, 그 글이 달린 일감의 프로젝트 번호로 읽어 새 번호로 바꾼다(화면의
 *       ISSUE_REF 규칙과 같다. 코드 블록 · 인라인 코드 · 링크 · 주소는 건드리지 않는다). 옛 판 · 옛 댓글 글은 역사라 그대로 둔다.
 *       row_version · updated_at 은 올리지 않고 알림도 보내지 않는다. 바꾸기 전 글은 {@code issue_number_text_change} 에 남는다</li>
 *   <li>이력 — 번호나 설명이 바뀐 살아 있는 일감마다 시스템 이력 한 줄("번호가 바뀌었습니다: 프로젝트 #옛 → #새")과, 설명이 바뀌었으면 새 판</li>
 *   <li>마무리 — UNIQUE (workspace_id, number), 워크스페이스 순번 = 가장 큰 번호, {@code project.issue_seq} 는 지운다(쓰지 않는다),
 *       상위 일감 외래 키는 미룰 수 있게(옮기기가 하위까지 한꺼번에 옮긴다), 첨부의 프로젝트는 일감을 따라 바꿀 수 있게</li>
 * </ol>
 * 옛 번호 → 새 번호는 {@code SELECT … FROM issue_number_change} 로 본다(docs/design/05-issue.md "번호 다시 매기기").
 */
public class V23__workspace_issue_number extends BaseJavaMigration {

  /** 시스템 이력 줄 · 판의 한 사람 이름 */
  static final String SYSTEM_ACTOR = "moai-works";

  private static final JsonMapper JSON = JsonMapper.builder().build();

  @Override
  public void migrate(Context context) throws Exception {
    Connection c = context.getConnection();
    Timestamp at = Timestamp.from(Instant.now());
    prepareSchema(c);

    // ── 2. 새 번호 ──
    List<IssueRow> rows = new ArrayList<>();
    try (Statement st = c.createStatement();
        ResultSet rs = st.executeQuery("""
            SELECT i.id, i.workspace_id, i.project_id, p.key, i.number, i.deleted_at IS NOT NULL AS deleted
            FROM issue i JOIN project p ON p.id = i.project_id
            ORDER BY i.workspace_id, i.created_at, i.number, i.project_id, i.id""")) {
      UUID workspace = null;
      int next = 0;
      while (rs.next()) {
        UUID ws = rs.getObject(2, UUID.class);
        if (!ws.equals(workspace)) {
          workspace = ws;
          next = 0;
        }
        rows.add(new IssueRow(rs.getObject(1, UUID.class), ws, rs.getObject(3, UUID.class), rs.getString(4), rs.getInt(5),
            ++next, rs.getBoolean(6)));
      }
    }
    Map<UUID, IssueRow> byId = new HashMap<>();
    Map<UUID, Map<Integer, Integer>> byProject = new HashMap<>();
    for (IssueRow r : rows) {
      byId.put(r.id, r);
      byProject.computeIfAbsent(r.projectId, k -> new HashMap<>()).put(r.oldNumber, r.newNumber);
    }

    // ── 3. 번호를 쓰고 바꾼 기록을 남긴다 (UNIQUE (project_id, number) 는 이미 뺐다 — 바꾸는 동안 겹쳐도 된다) ──
    try (PreparedStatement number = c.prepareStatement("UPDATE issue SET number = ? WHERE id = ?");
        PreparedStatement record = c.prepareStatement("""
            INSERT INTO issue_number_change (issue_id, workspace_id, project_id, project_key, old_number, new_number, changed_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)""")) {
      for (IssueRow r : rows) {
        if (r.oldNumber != r.newNumber) {
          number.setInt(1, r.newNumber);
          number.setObject(2, r.id);
          number.addBatch();
        }
        record.setObject(1, r.id);
        record.setObject(2, r.workspaceId);
        record.setObject(3, r.projectId);
        record.setString(4, r.projectKey);
        record.setInt(5, r.oldNumber);
        record.setInt(6, r.newNumber);
        record.setTimestamp(7, at);
        record.addBatch();
      }
      number.executeBatch();
      record.executeBatch();
    }

    // 이력 · 판 · 접근 기록은 추가만 하는 표다 — 이 마이그레이션 안에서만 보호 트리거를 끄고 가리키는 번호를 고친다(끝에서 다시 켠다)
    try (Statement st = c.createStatement()) {
      st.execute("ALTER TABLE issue_revision DISABLE TRIGGER tr_issue_revision_append_only");
      st.execute("ALTER TABLE issue_journal DISABLE TRIGGER tr_issue_journal_protect");
      st.execute("ALTER TABLE mcp_access_log DISABLE TRIGGER trg_mcp_access_log_append_only");
    }
    remapPointers(c);
    remapJournalChanges(c, byProject);

    // ── 4 · 5. 글 속 #번호 · 시스템 이력 ──
    Map<UUID, Integer> commentRefs = rewriteComments(c, byProject, at);
    rewriteDescriptionsAndJournal(c, rows, byProject, commentRefs, at);

    try (Statement st = c.createStatement()) {
      st.execute("ALTER TABLE issue_revision ENABLE TRIGGER tr_issue_revision_append_only");
      st.execute("ALTER TABLE issue_journal ENABLE TRIGGER tr_issue_journal_protect");
      st.execute("ALTER TABLE mcp_access_log ENABLE TRIGGER trg_mcp_access_log_append_only");
    }
    finishSchema(c);
  }

  /** 옛 번호 · 새 번호 */
  record IssueRow(UUID id, UUID workspaceId, UUID projectId, String projectKey, int oldNumber, int newNumber,
      boolean deleted) {
  }

  // ── 1. 표 ──────────────────────────────────────────────────────────

  private static void prepareSchema(Connection c) throws Exception {
    try (Statement st = c.createStatement()) {
      // 워크스페이스의 일감 번호 순번 — 일감을 만들 때 이 행을 잠그고 1 올린다(UPDATE … RETURNING)
      st.execute("ALTER TABLE workspace ADD COLUMN issue_seq integer NOT NULL DEFAULT 0");
      // 일감은 워크스페이스 아래에 있다 — 프로젝트는 일감의 속성(옮길 수 있다). 프로젝트와 같은 워크스페이스여야 한다
      st.execute("ALTER TABLE issue ADD COLUMN workspace_id uuid");
      st.execute("UPDATE issue i SET workspace_id = p.workspace_id FROM project p WHERE p.id = i.project_id");
      st.execute("ALTER TABLE issue ALTER COLUMN workspace_id SET NOT NULL");
      st.execute("ALTER TABLE project ADD CONSTRAINT ux_project_id_workspace UNIQUE (id, workspace_id)");
      st.execute("ALTER TABLE issue ADD CONSTRAINT fk_issue_project_same_workspace FOREIGN KEY (project_id, workspace_id)"
          + " REFERENCES project (id, workspace_id)");
      // 프로젝트 안 번호의 고유 조건은 뺀다 — 다시 매긴 뒤 (workspace_id, number) 로 건다
      for (String name : constraints(c, "issue", "u", "UNIQUE (project_id, number)")) {
        st.execute("ALTER TABLE issue DROP CONSTRAINT " + name);
      }
      // 판에 그때의 프로젝트 — 옛 판은 아래에서 그 일감의 프로젝트로 채운다(옮긴 적이 없었다)
      st.execute("ALTER TABLE issue_revision ADD COLUMN project_id uuid REFERENCES project (id)");
      // 이력 · 판의 경로에 SYSTEM — 사람이 아니라 시스템(이 마이그레이션)이 한 일
      for (String table : List.of("issue_journal", "issue_revision")) {
        for (String name : constraints(c, table, "c", "via")) {
          st.execute("ALTER TABLE " + table + " DROP CONSTRAINT " + name);
        }
        st.execute("ALTER TABLE " + table + " ADD CONSTRAINT ck_" + table + "_via CHECK (via IN ('WEB', 'MCP', 'SYSTEM'))");
      }
      // 번호 바꾼 기록 — 일감마다 한 줄(바뀌지 않은 것도). 옛 링크(프로젝트 주소 + 옛 번호)를 새 번호로 돌리는 데도 쓴다
      st.execute("""
          CREATE TABLE issue_number_change (
              issue_id          uuid          PRIMARY KEY REFERENCES issue (id),
              workspace_id      uuid          NOT NULL REFERENCES workspace (id),
              project_id        uuid          NOT NULL REFERENCES project (id),   -- 그때의 프로젝트
              project_key       varchar(32)   NOT NULL,                         -- 그때의 프로젝트 주소 이름
              old_number        integer       NOT NULL,                         -- 프로젝트 안 옛 번호
              new_number        integer       NOT NULL,                         -- 워크스페이스 안 새 번호
              description_refs  integer       NOT NULL DEFAULT 0,               -- 설명에서 바꾼 #번호 수
              comment_refs      integer       NOT NULL DEFAULT 0,               -- 댓글들에서 바꾼 #번호 수
              changed_at        timestamptz   NOT NULL,
              CONSTRAINT ux_issue_number_change_old UNIQUE (project_id, old_number)
          )""");
      st.execute("CREATE INDEX ix_issue_number_change_ws ON issue_number_change (workspace_id, new_number)");
      // 글 속 #번호를 바꾼 설명 · 댓글의 원문 — 되돌려야 할 때를 위해 바꾸기 전 · 뒤 글을 그대로 둔다
      st.execute("""
          CREATE TABLE issue_number_text_change (
              id          uuid          PRIMARY KEY,
              issue_id    uuid          NOT NULL REFERENCES issue (id),
              journal_id  uuid          REFERENCES issue_journal (id),            -- 댓글이면 그 이력 줄
              kind        varchar(20)   NOT NULL CHECK (kind IN ('DESCRIPTION', 'COMMENT')),
              refs        integer       NOT NULL,                                 -- 바꾼 #번호 수
              before      text          NOT NULL,
              after       text          NOT NULL,
              changed_at  timestamptz   NOT NULL
          )""");
    }
  }

  private static void finishSchema(Connection c) throws Exception {
    try (Statement st = c.createStatement()) {
      st.execute("ALTER TABLE issue ADD CONSTRAINT ux_issue_workspace_number UNIQUE (workspace_id, number)");
      st.execute("UPDATE workspace w SET issue_seq = coalesce((SELECT max(i.number) FROM issue i WHERE i.workspace_id = w.id), 0)");
      // 프로젝트 안 순번은 더 쓰지 않는다
      st.execute("ALTER TABLE project DROP COLUMN issue_seq");
      st.execute("ALTER TABLE issue_revision ALTER COLUMN project_id SET NOT NULL");
      // 워크스페이스 일감 목록(읽을 수 있는 프로젝트 모두)
      st.execute("CREATE INDEX ix_issue_ws_list ON issue (workspace_id, status, number DESC) WHERE deleted_at IS NULL");
      // 옮기기는 하위까지 한 트랜잭션에서 옮긴다 — 상위 · 하위가 같은 프로젝트인지는 커밋할 때 본다(SET CONSTRAINTS … DEFERRED)
      st.execute("ALTER TABLE issue ALTER CONSTRAINT fk_issue_parent_same_project DEFERRABLE INITIALLY IMMEDIATE");
      // 첨부의 프로젝트는 일감을 따라 바꿀 수 있다 — 그 일감의 지금 프로젝트로만. 저장소의 객체 이름(storage_key)은 그대로다
      st.execute("""
          CREATE OR REPLACE FUNCTION issue_attachment_protect() RETURNS trigger LANGUAGE plpgsql AS $$
          BEGIN
              IF TG_OP = 'DELETE' THEN
                  RAISE EXCEPTION 'issue_attachment 는 지울 수 없다 — 지움 표시만 한다';
              END IF;
              -- ③ 일감을 옮기면 첨부의 프로젝트도 따라간다(V23) — 다른 값은 그대로여야 한다
              IF NEW.project_id IS DISTINCT FROM OLD.project_id THEN
                  IF NEW.project_id IS DISTINCT FROM (SELECT i.project_id FROM issue i WHERE i.id = OLD.issue_id) THEN
                      RAISE EXCEPTION 'issue_attachment 의 프로젝트는 그 일감의 지금 프로젝트로만 바꿀 수 있다';
                  END IF;
                  IF ROW(NEW.id, NEW.workspace_id, NEW.issue_id, NEW.file_name, NEW.content_type, NEW.size_bytes, NEW.sha256,
                         NEW.image, NEW.storage_key, NEW.uploaded_by, NEW.via, NEW.created_at, NEW.deleted_at, NEW.deleted_by,
                         NEW.purged_at)
                     IS DISTINCT FROM
                     ROW(OLD.id, OLD.workspace_id, OLD.issue_id, OLD.file_name, OLD.content_type, OLD.size_bytes, OLD.sha256,
                         OLD.image, OLD.storage_key, OLD.uploaded_by, OLD.via, OLD.created_at, OLD.deleted_at, OLD.deleted_by,
                         OLD.purged_at) THEN
                      RAISE EXCEPTION 'issue_attachment 는 프로젝트를 바꿀 때 다른 값을 함께 바꿀 수 없다';
                  END IF;
                  RETURN NEW;
              END IF;
              IF NEW.id IS DISTINCT FROM OLD.id OR NEW.workspace_id IS DISTINCT FROM OLD.workspace_id
                  OR NEW.issue_id IS DISTINCT FROM OLD.issue_id
                  OR NEW.file_name IS DISTINCT FROM OLD.file_name OR NEW.content_type IS DISTINCT FROM OLD.content_type
                  OR NEW.size_bytes IS DISTINCT FROM OLD.size_bytes OR NEW.sha256 IS DISTINCT FROM OLD.sha256
                  OR NEW.image IS DISTINCT FROM OLD.image OR NEW.storage_key IS DISTINCT FROM OLD.storage_key
                  OR NEW.uploaded_by IS DISTINCT FROM OLD.uploaded_by OR NEW.via IS DISTINCT FROM OLD.via
                  OR NEW.created_at IS DISTINCT FROM OLD.created_at THEN
                  RAISE EXCEPTION 'issue_attachment 는 지움 표시만 한 번 할 수 있다';
              END IF;
              IF OLD.deleted_at IS NULL THEN
                  -- ① 지움 표시 — 정리 표시는 지운 뒤에만
                  IF NEW.purged_at IS NOT NULL THEN
                      RAISE EXCEPTION 'issue_attachment 는 지운 뒤에만 정리 표시를 한다';
                  END IF;
                  RETURN NEW;
              END IF;
              -- ② 지운 행 — 정리 표시를 한 번 남기는 것만
              IF OLD.purged_at IS NULL AND NEW.purged_at IS NOT NULL
                  AND NEW.deleted_at IS NOT DISTINCT FROM OLD.deleted_at AND NEW.deleted_by IS NOT DISTINCT FROM OLD.deleted_by THEN
                  RETURN NEW;
              END IF;
              RAISE EXCEPTION 'issue_attachment 는 지움 표시만 한 번 할 수 있다 — 지운 뒤에는 정리 표시(purged_at)를 한 번 남기는 것만 된다';
          END $$""");
    }
  }

  /** 그 표의 제약 이름들 — 정의(pg_get_constraintdef)에 그 글이 든 것. 이름을 손으로 적지 않는다(만들 때 PostgreSQL 이 지었다) */
  private static List<String> constraints(Connection c, String table, String type, String contains) throws Exception {
    List<String> names = new ArrayList<>();
    try (PreparedStatement ps = c.prepareStatement("SELECT conname, pg_get_constraintdef(oid) FROM pg_constraint"
        + " WHERE conrelid = ?::regclass AND contype = ?")) {
      ps.setString(1, table);
      ps.setString(2, type);
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          if (rs.getString(2).contains(contains)) {
            names.add(rs.getString(1));
          }
        }
      }
    }
    return names;
  }

  // ── 3. 가리키는 번호 ────────────────────────────────────────────────

  private static void remapPointers(Connection c) throws Exception {
    try (Statement st = c.createStatement()) {
      // 옛 판의 프로젝트 — 지금까지 옮긴 적이 없었으니 그 일감의 프로젝트다
      st.execute("UPDATE issue_revision r SET project_id = i.project_id FROM issue i WHERE i.id = r.issue_id");
      // 판의 상위 번호 — 상위는 늘 같은 프로젝트였다
      st.execute("""
          UPDATE issue_revision r SET parent_number = m.new_number
          FROM issue i JOIN issue_number_change m ON m.project_id = i.project_id
          WHERE r.issue_id = i.id AND r.parent_number = m.old_number""");
      // 첨부 티켓 — 티켓의 프로젝트 + 옛 번호
      st.execute("""
          UPDATE attachment_ticket t SET issue_number = m.new_number
          FROM issue_number_change m WHERE m.project_id = t.project_id AND m.old_number = t.issue_number""");
      // MCP 접근 기록 — 그 줄의 워크스페이스 · 프로젝트 주소 이름 + 옛 번호가 한 일감만 가리킬 때(지운 프로젝트의 주소 이름을 다시 쓴 경우는 둔다)
      st.execute("""
          UPDATE mcp_access_log l SET issue_number = m.new_number
          FROM issue_number_change m
          WHERE l.workspace_id = m.workspace_id AND l.project_key = m.project_key AND l.issue_number = m.old_number
            AND (SELECT count(*) FROM issue_number_change m2 WHERE m2.workspace_id = m.workspace_id
                   AND m2.project_key = m.project_key AND m2.old_number = m.old_number) = 1""");
      st.execute("""
          UPDATE mcp_access_log l SET target_ref = m.new_number::text
          FROM issue_number_change m
          WHERE l.tool = 'link_issues' AND l.target_ref ~ '^[0-9]{1,9}$'
            AND l.workspace_id = m.workspace_id AND l.project_key = m.project_key AND l.target_ref::int = m.old_number
            AND (SELECT count(*) FROM issue_number_change m2 WHERE m2.workspace_id = m.workspace_id
                   AND m2.project_key = m.project_key AND m2.old_number = m.old_number) = 1""");
      // 알림 — 그 알림의 일감(subject)의 번호, 하위 · 연결 상대 번호(같은 프로젝트였다 — params.project)
      st.execute("""
          UPDATE notification n SET params = jsonb_set(n.params, '{number}', to_jsonb(m.new_number))
          FROM issue_number_change m
          WHERE n.subject_type = 'issue' AND n.subject_id = m.issue_id AND n.params -> 'number' = to_jsonb(m.old_number)""");
      for (String field : List.of("childNumber", "otherNumber")) {
        st.execute("""
            UPDATE notification n SET params = jsonb_set(n.params, '{%1$s}', to_jsonb(m.new_number))
            FROM issue_number_change m
            WHERE n.subject_type = 'issue' AND n.workspace_id = m.workspace_id AND n.params ->> 'project' = m.project_key
              AND n.params -> '%1$s' = to_jsonb(m.old_number)
              AND (SELECT count(*) FROM issue_number_change m2 WHERE m2.workspace_id = m.workspace_id
                     AND m2.project_key = m.project_key AND m2.old_number = m.old_number) = 1""".formatted(field));
      }
    }
  }

  /** 이력 줄의 상위 번호(parent before · after) · 연결 상대 번호(link {number}) — 그 일감의 프로젝트 번호였다 */
  private static void remapJournalChanges(Connection c, Map<UUID, Map<Integer, Integer>> byProject) throws Exception {
    Map<UUID, String> updates = new LinkedHashMap<>();
    try (Statement st = c.createStatement();
        ResultSet rs = st.executeQuery("""
            SELECT j.id, i.project_id, j.changes::text FROM issue_journal j JOIN issue i ON i.id = j.issue_id
            WHERE j.changes::text LIKE '%"parent"%' OR j.changes::text LIKE '%"link"%'""")) {
      while (rs.next()) {
        Map<Integer, Integer> map = byProject.getOrDefault(rs.getObject(2, UUID.class), Map.of());
        JsonNode changes = JSON.readTree(rs.getString(3));
        if (!(changes instanceof ArrayNode list)) {
          continue;
        }
        boolean changed = false;
        for (JsonNode change : list) {
          if (!(change instanceof ObjectNode o)) {
            continue;
          }
          String field = o.path("field").asString("");
          for (String side : List.of("before", "after")) {
            JsonNode v = o.get(side);
            if ("parent".equals(field) && v != null && v.isIntegralNumber() && map.containsKey(v.asInt())) {
              o.put(side, map.get(v.asInt()));
              changed = true;
            } else if ("link".equals(field) && v instanceof ObjectNode link && link.path("number").isIntegralNumber()
                && map.containsKey(link.get("number").asInt())) {
              link.put("number", map.get(link.get("number").asInt()));
              changed = true;
            }
          }
        }
        if (changed) {
          updates.put(rs.getObject(1, UUID.class), JSON.writeValueAsString(list));
        }
      }
    }
    try (PreparedStatement ps = c.prepareStatement("UPDATE issue_journal SET changes = ?::jsonb WHERE id = ?")) {
      for (Map.Entry<UUID, String> e : updates.entrySet()) {
        ps.setString(1, e.getValue());
        ps.setObject(2, e.getKey());
        ps.addBatch();
      }
      ps.executeBatch();
    }
  }

  // ── 4. 글 속 #번호 ──────────────────────────────────────────────────

  /** 살아 있는 일감의 지우지 않은 댓글 — 글만 바꾼다(댓글 판 · 고친 때는 그대로). @return 일감마다 바꾼 #번호 수 */
  private static Map<UUID, Integer> rewriteComments(Connection c, Map<UUID, Map<Integer, Integer>> byProject, Timestamp at)
      throws Exception {
    Map<UUID, Integer> refsByIssue = new HashMap<>();
    List<Object[]> updates = new ArrayList<>();
    try (Statement st = c.createStatement();
        ResultSet rs = st.executeQuery("""
            SELECT j.id, j.issue_id, i.project_id, j.notes FROM issue_journal j JOIN issue i ON i.id = j.issue_id
            WHERE j.notes IS NOT NULL AND j.notes_deleted_at IS NULL AND i.deleted_at IS NULL AND j.notes LIKE '%#%'""")) {
      while (rs.next()) {
        Map<Integer, Integer> map = byProject.getOrDefault(rs.getObject(3, UUID.class), Map.of());
        String before = rs.getString(4);
        Rewrite r = rewriteRefs(before, n -> map.getOrDefault(n, -1));
        if (r.count() > 0) {
          UUID issueId = rs.getObject(2, UUID.class);
          refsByIssue.merge(issueId, r.count(), Integer::sum);
          updates.add(new Object[] {rs.getObject(1, UUID.class), issueId, before, r.text(), r.count()});
        }
      }
    }
    try (PreparedStatement update = c.prepareStatement("UPDATE issue_journal SET notes = ? WHERE id = ?");
        PreparedStatement backup = c.prepareStatement("""
            INSERT INTO issue_number_text_change (id, issue_id, journal_id, kind, refs, before, after, changed_at)
            VALUES (?, ?, ?, 'COMMENT', ?, ?, ?, ?)""")) {
      for (Object[] u : updates) {
        update.setString(1, (String) u[3]);
        update.setObject(2, u[0]);
        update.addBatch();
        backup.setObject(1, UUID.randomUUID());
        backup.setObject(2, u[1]);
        backup.setObject(3, u[0]);
        backup.setInt(4, (Integer) u[4]);
        backup.setString(5, (String) u[2]);
        backup.setString(6, (String) u[3]);
        backup.setTimestamp(7, at);
        backup.addBatch();
      }
      update.executeBatch();
      backup.executeBatch();
    }
    try (PreparedStatement ps = c.prepareStatement("UPDATE issue_number_change SET comment_refs = ? WHERE issue_id = ?")) {
      for (Map.Entry<UUID, Integer> e : refsByIssue.entrySet()) {
        ps.setInt(1, e.getValue());
        ps.setObject(2, e.getKey());
        ps.addBatch();
      }
      ps.executeBatch();
    }
    return refsByIssue;
  }

  /**
   * 살아 있는 일감의 설명 속 #번호를 바꾸고, 번호나 설명이 바뀐 일감마다 시스템 이력 한 줄을 남긴다(설명이 바뀌었으면 새 판도).
   * 일감의 row_version · updated_at 은 그대로다(판 번호만 오른다).
   */
  private static void rewriteDescriptionsAndJournal(Connection c, List<IssueRow> rows,
      Map<UUID, Map<Integer, Integer>> byProject, Map<UUID, Integer> commentRefs, Timestamp at) throws Exception {
    Map<UUID, String> descriptions = new HashMap<>();
    try (Statement st = c.createStatement();
        ResultSet rs = st.executeQuery("SELECT id, description FROM issue WHERE deleted_at IS NULL")) {
      while (rs.next()) {
        descriptions.put(rs.getObject(1, UUID.class), rs.getString(2));
      }
    }
    try (PreparedStatement description = c.prepareStatement("UPDATE issue SET description = ? WHERE id = ?");
        PreparedStatement backup = c.prepareStatement("""
            INSERT INTO issue_number_text_change (id, issue_id, journal_id, kind, refs, before, after, changed_at)
            VALUES (?, ?, NULL, 'DESCRIPTION', ?, ?, ?, ?)""");
        PreparedStatement counts = c.prepareStatement("UPDATE issue_number_change SET description_refs = ? WHERE issue_id = ?");
        PreparedStatement journal = c.prepareStatement("""
            INSERT INTO issue_journal (id, issue_id, actor_id, actor_type, actor_name, via, created_at, notes, changes, revision_no)
            VALUES (?, ?, NULL, 'SERVICE', ?, 'SYSTEM', ?, NULL, ?::jsonb, ?)""");
        PreparedStatement revision = c.prepareStatement("""
            INSERT INTO issue_revision (id, issue_id, revision_no, journal_id, issue_type, title, description, status, manager_id,
                                        assignee_id, parent_number, due_date, start_date, planned_end_date, completed_on,
                                        closed_on, estimated_hours, agent_session, modules, project_id, actor_id, actor_type,
                                        actor_name, via, created_at)
            SELECT ?, i.id, i.revision_no + 1, ?, i.issue_type, i.title, i.description, i.status, i.manager_id, i.assignee_id,
                   p.number, i.due_date, i.start_date, i.planned_end_date, i.completed_on, i.closed_on, i.estimated_hours,
                   i.agent_session,
                   (SELECT jsonb_agg(jsonb_build_object('key', m.module_key, 'name', m.name) ORDER BY m.position, m.module_key)
                    FROM issue_module im JOIN project_module m ON m.id = im.module_id WHERE im.issue_id = i.id),
                   i.project_id, NULL, 'SERVICE', ?, 'SYSTEM', ?
            FROM issue i LEFT JOIN issue p ON p.id = i.parent_id WHERE i.id = ?""");
        PreparedStatement bump = c.prepareStatement("UPDATE issue SET revision_no = revision_no + 1 WHERE id = ?");
        PreparedStatement revisionNo = c.prepareStatement("SELECT revision_no FROM issue WHERE id = ?")) {
      for (IssueRow r : rows) {
        if (r.deleted()) {
          continue;
        }
        String before = descriptions.getOrDefault(r.id(), "");
        Map<Integer, Integer> map = byProject.getOrDefault(r.projectId(), Map.of());
        Rewrite rewrite = rewriteRefs(before, n -> map.getOrDefault(n, -1));
        boolean descriptionChanged = rewrite.count() > 0;
        boolean numberChanged = r.oldNumber() != r.newNumber();
        if (descriptionChanged) {
          description.setString(1, rewrite.text());
          description.setObject(2, r.id());
          description.executeUpdate();
          backup.setObject(1, UUID.randomUUID());
          backup.setObject(2, r.id());
          backup.setInt(3, rewrite.count());
          backup.setString(4, before);
          backup.setString(5, rewrite.text());
          backup.setTimestamp(6, at);
          backup.executeUpdate();
          counts.setInt(1, rewrite.count());
          counts.setObject(2, r.id());
          counts.executeUpdate();
        }
        if (!numberChanged && !descriptionChanged) {
          continue;
        }
        ArrayNode changes = JSON.createArrayNode();
        if (numberChanged) {
          ObjectNode change = changes.addObject();
          change.put("field", "number");
          ObjectNode old = change.putObject("before");
          old.put("project", r.projectKey());
          old.put("number", r.oldNumber());
          change.putObject("after").put("number", r.newNumber());
        }
        if (descriptionChanged) {
          ObjectNode change = changes.addObject();
          change.put("field", "description");
          if (before.isEmpty()) {
            change.putNull("before");
          } else {
            change.put("before", before);
          }
          change.put("after", rewrite.text());
        }
        UUID journalId = UUID.randomUUID();
        Integer newRevision = null;
        if (descriptionChanged) {
          revisionNo.setObject(1, r.id());
          try (ResultSet rs = revisionNo.executeQuery()) {
            rs.next();
            newRevision = rs.getInt(1) + 1;
          }
        }
        journal.setObject(1, journalId);
        journal.setObject(2, r.id());
        journal.setString(3, SYSTEM_ACTOR);
        journal.setTimestamp(4, at);
        journal.setString(5, JSON.writeValueAsString(changes));
        if (newRevision == null) {
          journal.setNull(6, java.sql.Types.INTEGER);
        } else {
          journal.setInt(6, newRevision);
        }
        journal.executeUpdate();
        if (descriptionChanged) {
          // 설명이 바뀌었으면 그 모습을 새 판으로 — 다음 사람이 고친 판의 차이에 이 바꿈이 섞이지 않게
          revision.setObject(1, UUID.randomUUID());
          revision.setObject(2, journalId);
          revision.setString(3, SYSTEM_ACTOR);
          revision.setTimestamp(4, at);
          revision.setObject(5, r.id());
          revision.executeUpdate();
          bump.setObject(1, r.id());
          bump.executeUpdate();
        }
      }
    }
  }

  // ── 글 속 #번호 바꾸기 ───────────────────────────────────────────────

  /** 바꾼 글과 바꾼 #번호 수 */
  public record Rewrite(String text, int count) {
  }

  /**
   * 화면의 ISSUE_REF(web-frontend {@code markdown-plugins.ts})와 같은 규칙: 글자 · 숫자 · &amp; · # · / 바로 뒤가 아니고, 뒤에
   * 글자 · 숫자가 붙지 않은 {@code #1~9자리}. 앞 글자를 잡는 묶음 때문에 바로 이어진 두 번째 번호는 그 앞 번호의 숫자 뒤라 어차피 아니다.
   */
  static final Pattern ISSUE_REF = Pattern.compile("(^|[^\\p{L}\\p{N}&#/])#(\\d{1,9})(?![\\p{L}\\p{N}])");
  private static final Pattern FENCE_OPEN = Pattern.compile("^ {0,3}(`{3,}|~{3,})(.*)$");
  private static final Pattern LIST_ITEM = Pattern.compile("^ {0,3}([-+*]|\\d{1,9}[.)])([ \\t]|$)");
  private static final Pattern LINK_DEFINITION = Pattern.compile("^ {0,3}\\[[^\\]]+\\]:\\s");
  private static final Pattern AUTOLINK = Pattern.compile("<[A-Za-z][A-Za-z0-9+.-]{1,31}:[^<>\\s]*>|<[^<>\\s@]+@[^<>\\s]+>");
  private static final Pattern BARE_URL = Pattern.compile("(?i)(https?://|www\\.)[^\\s<]*");

  /**
   * 글 속 {@code #번호} 를 바꾼다 — 화면이 일감 링크로 그리는 곳만. 펜스 코드 블록(``` · ~~~) · 들여쓴 코드 블록 · 인라인 코드 ·
   * 링크(글과 주소 모두 — 화면도 링크 안은 건드리지 않는다) · 꺾쇠 자동 링크 · 맨 주소(https://… · www.…) · 링크 정의 줄은 그대로 둔다.
   *
   * @param map 옛 번호 → 새 번호. 모르는 번호(그 프로젝트에 없던 번호)는 음수 — 그 자리는 그대로 둔다
   */
  public static Rewrite rewriteRefs(String text, IntUnaryOperator map) {
    if (text == null || text.isEmpty() || text.indexOf('#') < 0) {
      return new Rewrite(text == null ? "" : text, 0);
    }
    StringBuilder out = new StringBuilder(text.length());
    int[] count = {0};
    String fence = null;
    boolean indentedCode = false;
    boolean previousBlank = true;
    boolean inList = false;
    int start = 0;
    while (start < text.length()) {
      int end = text.indexOf('\n', start);
      int next = end < 0 ? text.length() : end + 1;
      String line = text.substring(start, end < 0 ? text.length() : end);
      String eol = text.substring(start + line.length(), next);
      start = next;
      String body = line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
      String cr = line.substring(body.length());
      boolean blank = body.isBlank();
      if (fence != null) {
        // 닫는 펜스 — 같은 글자로 여는 것 이상 길이, 뒤에는 공백만
        String trimmed = body.stripLeading();
        char mark = fence.charAt(0);
        if (body.length() - trimmed.length() <= 3 && trimmed.startsWith(fence) && trimmed.strip().chars().allMatch(ch -> ch == mark)) {
          fence = null;
        }
        out.append(line).append(eol);
        previousBlank = false;
        continue;
      }
      Matcher open = FENCE_OPEN.matcher(body);
      if (open.matches() && !(open.group(1).charAt(0) == '`' && open.group(2).contains("`"))) {
        fence = open.group(1);
        out.append(line).append(eol);
        previousBlank = false;
        indentedCode = false;
        continue;
      }
      int indent = indentOf(body);
      if (!blank && indent >= 4 && (indentedCode || (previousBlank && !inList))) {
        indentedCode = true;
        out.append(line).append(eol);
        previousBlank = false;
        continue;
      }
      if (!blank && indent < 4) {
        indentedCode = false;
      }
      if (blank) {
        out.append(line).append(eol);
        previousBlank = true;
        continue;
      }
      if (LIST_ITEM.matcher(body).find()) {
        inList = true;
      } else if (previousBlank && indent < 2) {
        inList = false;
      }
      previousBlank = false;
      if (LINK_DEFINITION.matcher(body).find()) {
        out.append(line).append(eol);
        continue;
      }
      out.append(rewriteInline(body, map, count)).append(cr).append(eol);
    }
    return new Rewrite(out.toString(), count[0]);
  }

  private static int indentOf(String line) {
    int n = 0;
    for (int i = 0; i < line.length(); i++) {
      char ch = line.charAt(i);
      if (ch == ' ') {
        n++;
      } else if (ch == '\t') {
        n += 4 - (n % 4);
      } else {
        break;
      }
    }
    return n;
  }

  /** 한 줄 — 인라인 코드 · 링크 · 자동 링크 · 맨 주소는 그대로 두고 나머지 글의 #번호만 바꾼다 */
  private static String rewriteInline(String line, IntUnaryOperator map, int[] count) {
    StringBuilder out = new StringBuilder(line.length());
    StringBuilder plain = new StringBuilder();
    int i = 0;
    while (i < line.length()) {
      char ch = line.charAt(i);
      int skip = -1;
      if (ch == '`') {
        int run = run(line, i, '`');
        int close = closingRun(line, i + run, run);
        skip = close < 0 ? i + run : close + run;
        if (close < 0) {
          // 닫히지 않은 백틱은 글자다
          plain.append(line, i, skip);
          i = skip;
          continue;
        }
      } else if (ch == '[' || (ch == '!' && i + 1 < line.length() && line.charAt(i + 1) == '[')) {
        skip = linkEnd(line, ch == '!' ? i + 1 : i);
      } else if (ch == '<') {
        Matcher m = AUTOLINK.matcher(line).region(i, line.length());
        if (m.lookingAt()) {
          skip = m.end();
        }
      } else if ((ch == 'h' || ch == 'H' || ch == 'w' || ch == 'W')
          && (i == 0 || !Character.isLetterOrDigit(line.charAt(i - 1)))) {
        Matcher m = BARE_URL.matcher(line).region(i, line.length());
        if (m.lookingAt()) {
          skip = m.end();
        }
      }
      if (skip > i) {
        flush(out, plain, map, count);
        out.append(line, i, skip);
        i = skip;
      } else {
        plain.append(ch);
        i++;
      }
    }
    flush(out, plain, map, count);
    return out.toString();
  }

  private static void flush(StringBuilder out, StringBuilder plain, IntUnaryOperator map, int[] count) {
    if (plain.isEmpty()) {
      return;
    }
    Matcher m = ISSUE_REF.matcher(plain);
    StringBuilder sb = new StringBuilder();
    while (m.find()) {
      int from = Integer.parseInt(m.group(2));
      int to = map.applyAsInt(from);
      // 모르는 번호, 또는 번호가 그대로인 것(맨 앞자리 0 도 그대로 둔다)은 바꾸지도 세지도 않는다
      if (to < 0 || to == from) {
        m.appendReplacement(sb, Matcher.quoteReplacement(m.group()));
      } else {
        m.appendReplacement(sb, Matcher.quoteReplacement(m.group(1) + "#" + to));
        count[0]++;
      }
    }
    m.appendTail(sb);
    out.append(sb);
    plain.setLength(0);
  }

  private static int run(String s, int from, char ch) {
    int n = 0;
    while (from + n < s.length() && s.charAt(from + n) == ch) {
      n++;
    }
    return n;
  }

  /** 같은 길이의 백틱 묶음이 닫는 자리 — 없으면 -1 */
  private static int closingRun(String s, int from, int length) {
    int i = from;
    while (i < s.length()) {
      if (s.charAt(i) == '`') {
        int n = run(s, i, '`');
        if (n == length) {
          return i;
        }
        i += n;
      } else {
        i++;
      }
    }
    return -1;
  }

  /** {@code [글](주소)} · {@code [글][참조]} · {@code [글][]} 의 끝 — 링크가 아니면 -1 */
  private static int linkEnd(String s, int open) {
    int depth = 0;
    int i = open;
    int textEnd = -1;
    while (i < s.length()) {
      char ch = s.charAt(i);
      if (ch == '\\') {
        i += 2;
        continue;
      }
      if (ch == '[') {
        depth++;
      } else if (ch == ']') {
        depth--;
        if (depth == 0) {
          textEnd = i;
          break;
        }
      }
      i++;
    }
    if (textEnd < 0 || textEnd + 1 >= s.length()) {
      return -1;
    }
    char after = s.charAt(textEnd + 1);
    char close = after == '(' ? ')' : after == '[' ? ']' : 0;
    if (close == 0) {
      return -1;
    }
    int nest = 0;
    for (int j = textEnd + 1; j < s.length(); j++) {
      char ch = s.charAt(j);
      if (ch == '\\') {
        j++;
        continue;
      }
      if (ch == after) {
        nest++;
      } else if (ch == close) {
        nest--;
        if (nest == 0) {
          return j + 1;
        }
      }
    }
    return -1;
  }
}
