-- 작업 시간 기록 보호 (2026-11-22 사용자 결정)
--   "나중에 공수 정리 및 실제 투입 공수는 소요시간으로 뽑아야 해" · "그래서 소요시간 입력은 매우 중요한 데이터야"
-- 작업 시간 기록은 지우지 않는다(행 삭제 금지). 고칠 수 있는 것은 지움 표시(deleted_at · deleted_by)를 한 번 넣는 것뿐이다.
-- 그 밖의 값(누가 · 날 · 시간 · 메모 · 경로 · 적은 때)은 한 번 들어가면 바뀌지 않는다.
CREATE FUNCTION issue_work_log_protect() RETURNS trigger LANGUAGE plpgsql AS $$
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
        OR NEW.via IS DISTINCT FROM OLD.via OR NEW.created_at IS DISTINCT FROM OLD.created_at
        OR NEW.deleted_at IS NULL OR NEW.deleted_by IS NULL THEN
        RAISE EXCEPTION 'issue_work_log 는 지움 표시(deleted_at · deleted_by) 말고는 바꿀 수 없다';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER tr_issue_work_log_protect BEFORE UPDATE OR DELETE ON issue_work_log
    FOR EACH ROW EXECUTE FUNCTION issue_work_log_protect();
