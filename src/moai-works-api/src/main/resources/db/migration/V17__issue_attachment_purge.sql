-- 지운 첨부의 객체 정리 (moai-works 일감 #18 — FR-ISS-018, 일감 설계 "객체는 남는다(나중에 정리 작업이 일정 기간 뒤 지운다)")
-- 지움 표시 뒤 보관 기간(moai-works.storage.purge-after-days, 기본 30일)이 지나면 정리 작업(AttachmentPurger)이
--   저장소에서 그 첨부의 객체(원본 · 미리보기, 버전 보관분까지)를 지우고 행에 정리한 때(purged_at)를 남긴다.
-- 행은 지우지 않는다 — 이력 · 감사가 가리키는 첨부가 무엇이었는지(이름 · 크기 · 해시 · 누가 언제 지웠나)는 남는다.
-- moai-wiki 의 document_raw.purged_at(보존 기간이 지나 파일을 지운 때. 행은 남는다)과 같은 모양이다.
ALTER TABLE issue_attachment ADD COLUMN purged_at timestamptz;
ALTER TABLE issue_attachment ADD CONSTRAINT ck_issue_attachment_purged CHECK (purged_at IS NULL OR deleted_at IS NOT NULL);
-- 정리할 차례 — 지웠고 아직 정리하지 않은 것을 지운 때 순서로
CREATE INDEX ix_issue_attachment_purge ON issue_attachment (deleted_at) WHERE deleted_at IS NOT NULL AND purged_at IS NULL;

-- 보호 트리거를 넓힌다 — 바뀌어도 되는 것은 둘뿐이다:
--   ① 살아 있는 행에 지움 표시(deleted_at · deleted_by)를 한 번 남기기 (V9 와 같다, purged_at 은 비운 채)
--   ② 지운 행에 정리한 때(purged_at)를 한 번 남기기 — 다른 열(지움 표시 포함)은 그대로
-- 지우기 · 그 밖의 값 바꾸기 · 정리 표시 다시 쓰기 · 지우지 않은 행의 정리 표시는 막는다.
CREATE OR REPLACE FUNCTION issue_attachment_protect() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'issue_attachment 는 지울 수 없다 — 지움 표시만 한다';
    END IF;
    IF NEW.id IS DISTINCT FROM OLD.id OR NEW.workspace_id IS DISTINCT FROM OLD.workspace_id
        OR NEW.project_id IS DISTINCT FROM OLD.project_id OR NEW.issue_id IS DISTINCT FROM OLD.issue_id
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
END $$;
