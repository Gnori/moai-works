-- 첨부에 에이전트 세션 이름 (2026-11-26 사용자 결정, moai-works 일감 #312)
--   "첨부파일도 에이전트 세션네임" · "추가 해줘"
-- 이력 줄 · 작업 시간 기록(V31, 일감 #304)과 같이 — Agent 는 사람의 MCP 키로 올리므로 첨부에는 키 주인 이름 + MCP 만 남았다.
-- 어느 세션이 올린 첨부인지 행마다 적어 둔다.
--   - MCP 로 올린 첨부: 올리기 티켓을 받을 때 준 세션 이름(request_attachment_upload 의 agentSession), 없으면 올릴(PUT) 때의 그 일감의
--     agentSession. 둘 다 없으면 NULL
--   - 웹에서 올린 첨부(사람): NULL
-- 규칙은 일감의 agent_session(V20) · 이력 줄(V31)과 같다 — 앞뒤 공백을 뺀 1~100자, 줄바꿈 같은 제어 문자 없음.
ALTER TABLE issue_attachment
    ADD COLUMN agent_session varchar(100)
        CONSTRAINT ck_issue_attachment_agent_session CHECK (agent_session IS NULL OR (char_length(agent_session) BETWEEN 1 AND 100
            AND agent_session = btrim(agent_session) AND agent_session !~ '[[:cntrl:]]'));
-- 올리기 티켓이 준 세션 이름을 올릴 때까지 들고 있는다(받기 티켓은 늘 NULL)
ALTER TABLE attachment_ticket
    ADD COLUMN agent_session varchar(100)
        CONSTRAINT ck_attachment_ticket_agent_session CHECK (agent_session IS NULL OR (char_length(agent_session) BETWEEN 1 AND 100
            AND agent_session = btrim(agent_session) AND agent_session !~ '[[:cntrl:]]'));

-- 옛 첨부를 채운다 — MCP 로 올린 것만, 그 일감의 지금 agentSession 으로(올릴 때의 이름은 남아 있지 않다). 웹 첨부는 그대로 NULL.
-- 지운 첨부 · 정리한 첨부도 채운다. 보호 트리거는 이 열을 모르지만(아래에서 더한다) V31 과 같이 이 한 번만 끄고 채운 뒤 다시 켠다
-- (다른 값은 건드리지 않는다)
ALTER TABLE issue_attachment DISABLE TRIGGER tr_issue_attachment_protect;
UPDATE issue_attachment a SET agent_session = i.agent_session
  FROM issue i
 WHERE a.issue_id = i.id AND a.via = 'MCP' AND a.agent_session IS NULL AND i.agent_session IS NOT NULL;
ALTER TABLE issue_attachment ENABLE TRIGGER tr_issue_attachment_protect;

-- 보호 규칙에 agent_session 을 더한다 — 한 번 적으면 바뀌지 않는다(V23 과 같고 agent_session 만 더함).
-- 바뀌어도 되는 것은 그대로 셋뿐이다: ① 지움 표시 한 번 ② 지운 행의 정리 표시 한 번 ③ 일감을 옮길 때 프로젝트
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
               NEW.image, NEW.storage_key, NEW.uploaded_by, NEW.via, NEW.agent_session, NEW.created_at, NEW.deleted_at,
               NEW.deleted_by, NEW.purged_at)
           IS DISTINCT FROM
           ROW(OLD.id, OLD.workspace_id, OLD.issue_id, OLD.file_name, OLD.content_type, OLD.size_bytes, OLD.sha256,
               OLD.image, OLD.storage_key, OLD.uploaded_by, OLD.via, OLD.agent_session, OLD.created_at, OLD.deleted_at,
               OLD.deleted_by, OLD.purged_at) THEN
            RAISE EXCEPTION 'issue_attachment 는 프로젝트를 바꿀 때 다른 값을 함께 바꿀 수 없다';
        END IF;
        RETURN NEW;
    END IF;
    -- 첨부 자체(무엇을 · 누가 · 어느 길로 · 어느 세션이 · 언제)는 그대로다
    IF NEW.id IS DISTINCT FROM OLD.id OR NEW.workspace_id IS DISTINCT FROM OLD.workspace_id
        OR NEW.issue_id IS DISTINCT FROM OLD.issue_id
        OR NEW.file_name IS DISTINCT FROM OLD.file_name OR NEW.content_type IS DISTINCT FROM OLD.content_type
        OR NEW.size_bytes IS DISTINCT FROM OLD.size_bytes OR NEW.sha256 IS DISTINCT FROM OLD.sha256
        OR NEW.image IS DISTINCT FROM OLD.image OR NEW.storage_key IS DISTINCT FROM OLD.storage_key
        OR NEW.uploaded_by IS DISTINCT FROM OLD.uploaded_by OR NEW.via IS DISTINCT FROM OLD.via
        OR NEW.agent_session IS DISTINCT FROM OLD.agent_session
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
END $$;
