-- 작업 시간 기록에 "사람이 한 시간" 표시 (2026-11-24 — moai-works 일감 #204, 써 보기 3회차)
--   Agent 가 MCP 로 적는 시간은 키 주인(사람) 이름으로 남는다. 그 가운데 Agent 가 제 일로 적은 시간과 사용자가 알려 준 사람의 시간을
--   가르는 길이 note 의 글("사용자가 말한 시간")뿐이라, 공수를 뽑을 때 셀 수 없었다. 기록은 고칠 수 없으니 적을 때 정해 둔다.
--   - 웹에서 적은 기록: 사람이 직접 적은 그 사람의 시간 → true
--   - MCP log_work: byHuman 인자(비우면 note 의 사람 표시로 정한다) — Agent 의 제 일은 false
ALTER TABLE issue_work_log ADD COLUMN by_human boolean NOT NULL DEFAULT false;

-- 옛 기록을 같은 규칙으로 채운다 — 웹 기록 · note 에 사람 표시(IssueGuide.HUMAN_MARKERS 와 같은 글, 대소문자 무시)가 있는 기록.
-- 보호 트리거는 지움 표시 말고는 막으므로 이 한 번만 끄고 채운 뒤 다시 켠다(다른 값은 건드리지 않는다)
ALTER TABLE issue_work_log DISABLE TRIGGER tr_issue_work_log_protect;
UPDATE issue_work_log SET by_human = true
 WHERE via = 'WEB' OR note ILIKE '%사용자가 말한%' OR note ILIKE '%사람이 한%' OR note ILIKE '%reported%';
ALTER TABLE issue_work_log ENABLE TRIGGER tr_issue_work_log_protect;

-- 보호 규칙에 by_human 을 더한다 — 한 번 정하면 바뀌지 않는다(V4 와 같고 by_human 줄만 더함)
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
        OR NEW.created_at IS DISTINCT FROM OLD.created_at
        OR NEW.deleted_at IS NULL OR NEW.deleted_by IS NULL THEN
        RAISE EXCEPTION 'issue_work_log 는 지움 표시(deleted_at · deleted_by) 말고는 바꿀 수 없다';
    END IF;
    RETURN NEW;
END $$;
