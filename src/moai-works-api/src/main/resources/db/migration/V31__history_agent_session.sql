-- 이력 줄 · 작업 시간 기록에 에이전트 세션 이름 (2026-11-26 사용자 결정, moai-works 일감 #304)
--   "이슈 댓글에서 사용자 계정도 쓰지만 moaiworks01 MCP 이렇게 되어 있잖아? 그 뒤에 에이전트 세션명도 추가해줘"
--   "현재까지 등록된것들은 본분에 에이전트 세션명이 있을거야 그걸로 맞추고" · "사람이 하면 사람이겠지?"
-- Agent 는 사람의 MCP 키로 일하므로 이력에는 키 주인 이름 + MCP 만 남았다 — 어느 세션이 한 일인지 줄마다 적어 둔다.
--   - MCP 로 쓴 줄: 그 호출이 준 세션 이름(add_comment · log_work · create_issue · update_issue 의 agentSession), 없으면 그 일감의
--     (그 호출로 바뀐 뒤의) agentSession. 둘 다 없으면 NULL
--   - 웹에서 쓴 줄(사람) · 시스템이 쓴 줄: NULL
-- 규칙은 일감의 agent_session(V20)과 같다 — 앞뒤 공백을 뺀 1~100자, 줄바꿈 같은 제어 문자 없음.
ALTER TABLE issue_journal
    ADD COLUMN agent_session varchar(100)
        CONSTRAINT ck_issue_journal_agent_session CHECK (agent_session IS NULL OR (char_length(agent_session) BETWEEN 1 AND 100
            AND agent_session = btrim(agent_session) AND agent_session !~ '[[:cntrl:]]'));
ALTER TABLE issue_work_log
    ADD COLUMN agent_session varchar(100)
        CONSTRAINT ck_issue_work_log_agent_session CHECK (agent_session IS NULL OR (char_length(agent_session) BETWEEN 1 AND 100
            AND agent_session = btrim(agent_session) AND agent_session !~ '[[:cntrl:]]'));

-- 옛 줄을 채운다 — MCP 로 쓴 줄만, 그 일감의 지금 agentSession 으로(줄마다 그때의 이름은 남아 있지 않다). 웹 줄은 그대로 NULL.
-- 보호 트리거는 이 열을 모르지만(아래에서 더한다) V27 과 같이 이 한 번만 끄고 채운 뒤 다시 켠다(다른 값은 건드리지 않는다)
ALTER TABLE issue_journal DISABLE TRIGGER tr_issue_journal_protect;
UPDATE issue_journal j SET agent_session = i.agent_session
  FROM issue i
 WHERE j.issue_id = i.id AND j.via = 'MCP' AND j.agent_session IS NULL AND i.agent_session IS NOT NULL;
ALTER TABLE issue_journal ENABLE TRIGGER tr_issue_journal_protect;

ALTER TABLE issue_work_log DISABLE TRIGGER tr_issue_work_log_protect;
UPDATE issue_work_log w SET agent_session = i.agent_session
  FROM issue i
 WHERE w.issue_id = i.id AND w.via = 'MCP' AND w.agent_session IS NULL AND i.agent_session IS NOT NULL;
ALTER TABLE issue_work_log ENABLE TRIGGER tr_issue_work_log_protect;

-- 보호 규칙에 agent_session 을 더한다 — 한 번 적으면 바뀌지 않는다(V14 와 같고 agent_session 만 더함)
CREATE OR REPLACE FUNCTION issue_journal_protect() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'issue_journal 은 지울 수 없다 — 댓글은 지움 표시만 한다';
    END IF;
    -- 이력 줄 자체(누가 · 어느 길로 · 어느 세션이 · 언제 · 바뀐 것)는 그대로다
    IF NEW.id IS DISTINCT FROM OLD.id OR NEW.issue_id IS DISTINCT FROM OLD.issue_id
        OR NEW.actor_id IS DISTINCT FROM OLD.actor_id OR NEW.actor_type IS DISTINCT FROM OLD.actor_type
        OR NEW.actor_name IS DISTINCT FROM OLD.actor_name OR NEW.via IS DISTINCT FROM OLD.via
        OR NEW.agent_session IS DISTINCT FROM OLD.agent_session
        OR NEW.created_at IS DISTINCT FROM OLD.created_at OR NEW.changes IS DISTINCT FROM OLD.changes THEN
        RAISE EXCEPTION 'issue_journal: 이력 줄은 바꿀 수 없다';
    END IF;
    -- 판 번호는 판을 만들 때 한 번만 적는다
    IF OLD.revision_no IS NOT NULL AND NEW.revision_no IS DISTINCT FROM OLD.revision_no THEN
        RAISE EXCEPTION 'issue_journal: 판 번호는 바꿀 수 없다';
    END IF;
    -- 댓글이 없던 줄에 댓글을 붙이거나 표시를 달지 않는다
    IF OLD.notes IS NULL AND (NEW.notes IS NOT NULL OR NEW.notes_row_version IS DISTINCT FROM OLD.notes_row_version
        OR NEW.notes_edited_at IS NOT NULL OR NEW.notes_deleted_at IS NOT NULL OR NEW.notes_deleted_by IS NOT NULL) THEN
        RAISE EXCEPTION 'issue_journal: 댓글이 없는 이력 줄에는 댓글을 붙일 수 없다';
    END IF;
    -- 지운 댓글은 더 바꾸지 않는다(글 · 판 · 고친 때 · 지움 표시 모두)
    IF OLD.notes_deleted_at IS NOT NULL AND (NEW.notes IS DISTINCT FROM OLD.notes
        OR NEW.notes_row_version IS DISTINCT FROM OLD.notes_row_version OR NEW.notes_edited_at IS DISTINCT FROM OLD.notes_edited_at
        OR NEW.notes_deleted_at IS DISTINCT FROM OLD.notes_deleted_at OR NEW.notes_deleted_by IS DISTINCT FROM OLD.notes_deleted_by) THEN
        RAISE EXCEPTION 'issue_journal: 지운 댓글은 바꿀 수 없다';
    END IF;
    -- 지움 표시는 누가 · 언제를 함께
    IF (NEW.notes_deleted_at IS NULL) <> (NEW.notes_deleted_by IS NULL) THEN
        RAISE EXCEPTION 'issue_journal: 댓글 지움 표시는 지운 때와 지운 사람을 함께 남긴다';
    END IF;
    IF NEW.notes IS DISTINCT FROM OLD.notes THEN
        -- 글을 고치면: 비우지 않고(지우기는 표시만), 판을 하나 올리고, 고친 때를 적고, 앞 글이 먼저 남아 있어야 한다
        IF NEW.notes IS NULL THEN
            RAISE EXCEPTION 'issue_journal: 댓글 글을 비울 수 없다 — 지움 표시만 한다';
        END IF;
        IF NEW.notes_row_version IS DISTINCT FROM OLD.notes_row_version + 1 OR NEW.notes_edited_at IS NULL THEN
            RAISE EXCEPTION 'issue_journal: 댓글을 고치면 판을 하나 올리고 고친 때를 적는다';
        END IF;
        IF NOT EXISTS (SELECT 1 FROM issue_comment_revision r
                       WHERE r.journal_id = OLD.id AND r.row_version = OLD.notes_row_version AND r.notes = OLD.notes) THEN
            RAISE EXCEPTION 'issue_journal: 고치기 전 댓글 글을 issue_comment_revision 에 먼저 남겨야 한다';
        END IF;
    ELSIF NEW.notes_row_version IS DISTINCT FROM OLD.notes_row_version OR NEW.notes_edited_at IS DISTINCT FROM OLD.notes_edited_at THEN
        RAISE EXCEPTION 'issue_journal: 댓글 판 · 고친 때는 글을 고칠 때만 바뀐다';
    END IF;
    RETURN NEW;
END $$;

-- (V27 과 같고 agent_session 줄만 더함)
CREATE OR REPLACE FUNCTION issue_work_log_protect() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'issue_work_log 는 지울 수 없다 — 지움 표시만 한다';
    END IF;
    IF OLD.deleted_at IS NOT NULL THEN
        RAISE EXCEPTION 'issue_work_log: 이미 지움 표시한 기록은 바꿀 수 없다';
    END IF;
    IF NEW.id IS DISTINCT FROM OLD.id OR NEW.issue_id IS DISTINCT FROM OLD.issue_id
        OR NEW.user_id IS DISTINCT FROM OLD.user_id OR NEW.work_date IS DISTINCT FROM OLD.work_date
        OR NEW.hours IS DISTINCT FROM OLD.hours OR NEW.note IS DISTINCT FROM OLD.note
        OR NEW.via IS DISTINCT FROM OLD.via OR NEW.by_human IS DISTINCT FROM OLD.by_human
        OR NEW.agent_session IS DISTINCT FROM OLD.agent_session
        OR NEW.created_at IS DISTINCT FROM OLD.created_at
        OR NEW.deleted_at IS NULL OR NEW.deleted_by IS NULL THEN
        RAISE EXCEPTION 'issue_work_log 는 지움 표시(deleted_at · deleted_by) 말고는 바꿀 수 없다';
    END IF;
    RETURN NEW;
END $$;
