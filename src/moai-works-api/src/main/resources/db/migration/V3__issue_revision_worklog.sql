-- 일감 리비전 · 예상 작업 시간 · 작업 시간 기록 (design/05, 2026-11-22 사용자 결정)
--   "프로젝트의 아이템들은 리비전을 모두 관리해야 한다. 리비전별로 변화도 타임라인으로 보여 줘야 하고, 변화한 것도 git 처럼 diff 처럼"
--   "당연히 누가 수정했는지도" · "아이템은 작업 시간 정보도 넣을 수 있어"
-- moai-wiki 의 page_revision 자리다 — 바뀔 때마다 그 시점의 일감 전체를 한 판으로 남기고, 덮어쓰지 않는다.

-- ── 일감: 예상 작업 시간 · 지금 판 번호 ────────────────────────────────
ALTER TABLE issue
    ADD COLUMN estimated_hours numeric(7, 2) CHECK (estimated_hours IS NULL OR (estimated_hours >= 0 AND estimated_hours <= 10000)),
    ADD COLUMN revision_no     integer       NOT NULL DEFAULT 0;

-- ── 리비전 — 만들 때 1판, 필드가 바뀔 때마다 한 판. 누가 · 어느 길로(웹 · MCP) 바꿨는지 함께 ──────
CREATE TABLE issue_revision (
    id                uuid          PRIMARY KEY,
    issue_id          uuid          NOT NULL REFERENCES issue (id),
    revision_no       integer       NOT NULL,
    journal_id        uuid          REFERENCES issue_journal (id),        -- 그 판을 만든 이력 줄
    issue_type        varchar(30)   NOT NULL,
    title             varchar(200)  NOT NULL,
    description       text          NOT NULL DEFAULT '',
    status            varchar(20)   NOT NULL,
    manager_id        uuid          NOT NULL,
    assignee_id       uuid,
    parent_number     integer,                                           -- 그때의 상위 일감 번호
    due_date          date,
    start_date        date,
    planned_end_date  date,
    completed_on      date,
    closed_on         date,
    estimated_hours   numeric(7, 2),
    actor_id          uuid          REFERENCES user_profile (id),
    actor_type        varchar(20)   NOT NULL,
    actor_name        varchar(200)  NOT NULL,
    via               varchar(10)   NOT NULL CHECK (via IN ('WEB', 'MCP')),
    created_at        timestamptz   NOT NULL,
    UNIQUE (issue_id, revision_no)
);
CREATE INDEX ix_issue_revision_issue ON issue_revision (issue_id, revision_no DESC);

-- 리비전은 덮어쓰지 않는다 — 고치기 · 지우기를 DB 가 막는다 (감사 기록과 같은 방식)
CREATE FUNCTION issue_revision_append_only() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'issue_revision 은 추가만 할 수 있다';
END $$;
CREATE TRIGGER tr_issue_revision_append_only BEFORE UPDATE OR DELETE ON issue_revision
    FOR EACH ROW EXECUTE FUNCTION issue_revision_append_only();

-- 이력 줄이 어느 판을 만들었는지 (댓글 · 연결만 있는 줄은 NULL)
ALTER TABLE issue_journal ADD COLUMN revision_no integer;

-- 이미 있는 일감은 지금 모습을 1판으로 남긴다 (만든 사람 · 만든 때)
INSERT INTO issue_revision (id, issue_id, revision_no, journal_id, issue_type, title, description, status, manager_id,
                            assignee_id, parent_number, due_date, start_date, planned_end_date, completed_on, closed_on,
                            estimated_hours, actor_id, actor_type, actor_name, via, created_at)
SELECT gen_random_uuid(), i.id, 1, NULL, i.issue_type, i.title, i.description, i.status, i.manager_id, i.assignee_id,
       p.number, i.due_date, i.start_date, i.planned_end_date, i.completed_on, i.closed_on, NULL,
       i.created_by, 'USER', coalesce(u.display_name, u.username), 'WEB', i.created_at
FROM issue i
LEFT JOIN issue p ON p.id = i.parent_id
JOIN user_profile u ON u.id = i.created_by;
UPDATE issue SET revision_no = 1;

-- ── 작업 시간 기록 — 누가 · 어느 날 · 몇 시간 · 무엇을 ─────────────────────────────
CREATE TABLE issue_work_log (
    id          uuid          PRIMARY KEY,
    issue_id    uuid          NOT NULL REFERENCES issue (id),
    user_id     uuid          NOT NULL REFERENCES user_profile (id),     -- 일한 사람 (기록한 사람)
    work_date   date          NOT NULL,
    hours       numeric(5, 2) NOT NULL CHECK (hours > 0 AND hours <= 24),
    note        varchar(1000),
    via         varchar(10)   NOT NULL CHECK (via IN ('WEB', 'MCP')),
    created_at  timestamptz   NOT NULL,
    deleted_at  timestamptz,
    deleted_by  uuid          REFERENCES user_profile (id)
);
CREATE INDEX ix_issue_work_log_issue ON issue_work_log (issue_id, work_date DESC) WHERE deleted_at IS NULL;
CREATE INDEX ix_issue_work_log_user  ON issue_work_log (user_id, work_date DESC) WHERE deleted_at IS NULL;
