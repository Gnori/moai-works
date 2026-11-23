-- 댓글 고치기 · 지우기 (FR-ISS-019, moai-works 일감 #4)
--   사용자: "아이템은 덧글도 달 수 있어야 해" · MCP 로 "덧글, 첨부파일 포함" CRUD
-- 댓글은 지금처럼 이력 줄(issue_journal)의 notes 다. 그 자리에서 고치고 지운다(댓글이 이력 줄기 안의 제자리에 남는다).
--   고치기: 쓴 사람만. 바뀌기 전 글은 issue_comment_revision 에 쌓기만 한다(잃지 않는다 — 옛 글을 보는 화면은 아직 없다).
--           동시 수정은 notes_row_version 으로 본다(일감의 row_version 과 같은 방식, 409 CONCURRENT_UPDATE).
--   지우기: 쓴 사람 또는 project.manage. 지움 표시(누가 · 언제)만 — 글은 DB 에 남지만 REST · MCP 는 더 내주지 않는다("지운 댓글").
-- 고치기 · 지우기는 일감 이력에 한 줄(field: comment, 판은 아님)과 감사(issue.comment.update · issue.comment.delete)를 남긴다.

ALTER TABLE issue_journal
    ADD COLUMN notes_row_version integer     NOT NULL DEFAULT 0,          -- 댓글 글의 판(0 = 처음 단 글). 고칠 때마다 1 올린다
    ADD COLUMN notes_edited_at   timestamptz,                             -- 마지막으로 고친 때
    ADD COLUMN notes_deleted_at  timestamptz,                             -- 지운 때
    ADD COLUMN notes_deleted_by  uuid        REFERENCES user_profile (id); -- 지운 사람

-- ── 바뀌기 전 댓글 글 — 고칠 때마다 앞 글을 한 줄씩. 쌓기만 한다 ─────────────────────────
CREATE TABLE issue_comment_revision (
    id            uuid          PRIMARY KEY,                               -- 시간순 UUID(v7)
    journal_id    uuid          NOT NULL REFERENCES issue_journal (id),    -- 댓글(이력 줄)
    row_version   integer       NOT NULL,                                  -- 그 글이 쓰이던 판(notes_row_version)
    notes         text          NOT NULL CHECK (char_length(notes) <= 30000),   -- 바뀌기 전 글 그대로
    replaced_by   uuid          NOT NULL REFERENCES user_profile (id),     -- 고친 사람
    replaced_via  varchar(10)   NOT NULL CHECK (replaced_via IN ('WEB', 'MCP')),
    replaced_at   timestamptz   NOT NULL,                                  -- 고친 때(밀리초)
    UNIQUE (journal_id, row_version)
);

-- 옛 글은 덮어쓰지 않는다 — 고치기 · 지우기를 DB 가 막는다 (issue_revision 과 같은 방식)
CREATE FUNCTION issue_comment_revision_append_only() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'issue_comment_revision 은 추가만 할 수 있다';
END $$;
CREATE TRIGGER tr_issue_comment_revision_append_only BEFORE UPDATE OR DELETE ON issue_comment_revision
    FOR EACH ROW EXECUTE FUNCTION issue_comment_revision_append_only();

-- ── 이력 줄 보호 — 지우지 않는다. 바꿀 수 있는 것은 판 번호를 처음 적는 것 · 댓글 글 고치기 · 댓글 지움 표시뿐 ──────
-- (작업 시간 · 첨부의 보호 트리거와 같은 방식이다)
CREATE FUNCTION issue_journal_protect() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'issue_journal 은 지울 수 없다 — 댓글은 지움 표시만 한다';
    END IF;
    -- 이력 줄 자체(누가 · 어느 길로 · 언제 · 바뀐 것)는 그대로다
    IF NEW.id IS DISTINCT FROM OLD.id OR NEW.issue_id IS DISTINCT FROM OLD.issue_id
        OR NEW.actor_id IS DISTINCT FROM OLD.actor_id OR NEW.actor_type IS DISTINCT FROM OLD.actor_type
        OR NEW.actor_name IS DISTINCT FROM OLD.actor_name OR NEW.via IS DISTINCT FROM OLD.via
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
CREATE TRIGGER tr_issue_journal_protect BEFORE UPDATE OR DELETE ON issue_journal
    FOR EACH ROW EXECUTE FUNCTION issue_journal_protect();

-- 쪽 나눈 댓글 목록(MCP list_comments · REST) — 그 일감의 댓글만 오래된 것부터
CREATE INDEX ix_issue_journal_comments ON issue_journal (issue_id, created_at, id) WHERE notes IS NOT NULL;
