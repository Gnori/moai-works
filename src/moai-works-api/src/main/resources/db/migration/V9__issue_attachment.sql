-- 일감 첨부파일 (2026-11-22 사용자: "프로젝트의 아이템들의 이제 첨부파일도 넣을 수 있어야 하고 본문에도 이미지를 중간중간 추가해야 해"
--   · "파일 저장소 구조 잘 설계 해야 할 거야. 너무 한곳에 다 모아 두면 문제잖아")
-- 파일 원문은 객체 저장소(MinIO · S3 호환, 버킷 moai-works-attachments)에만 두고 여기에는 메타데이터만 둔다.
-- 객체 이름 = ws/<워크스페이스 id>/p/<프로젝트 id>/<연>/<월>/<첨부 id> — 워크스페이스 · 프로젝트 · 달로 나뉘어 한곳에 몰리지 않고,
--   워크스페이스 · 프로젝트 단위로 세고 · 옮기고 · 지우기 쉽다. 원래 파일 이름은 객체 이름에 넣지 않는다(이름 충돌 · 경로 장난 막기).
-- 지우기는 지움 표시만(누가) — 객체는 남는다(나중에 정리 작업이 일정 기간 뒤 지운다).
CREATE TABLE issue_attachment (
    id            uuid          PRIMARY KEY,                               -- 시간순 UUID(v7)
    workspace_id  uuid          NOT NULL REFERENCES workspace (id),
    project_id    uuid          NOT NULL REFERENCES project (id),
    issue_id      uuid          NOT NULL REFERENCES issue (id),
    file_name     varchar(255)  NOT NULL,                                  -- 올린 이름(경로 · 제어 문자를 뺀 것)
    content_type  varchar(100)  NOT NULL,                                  -- 이미지는 내용으로 알아낸 형식, 그 밖은 받은 값
    size_bytes    bigint        NOT NULL CHECK (size_bytes >= 0),
    sha256        char(64)      NOT NULL,
    image         boolean       NOT NULL,                                  -- 본문에 그릴 수 있는 이미지(png · jpeg · gif · webp)
    storage_key   varchar(300)  NOT NULL UNIQUE,
    uploaded_by   uuid          NOT NULL REFERENCES user_profile (id),
    via           varchar(10)   NOT NULL CHECK (via IN ('WEB', 'MCP')),
    created_at    timestamptz   NOT NULL,
    deleted_at    timestamptz,
    deleted_by    uuid          REFERENCES user_profile (id)
);
CREATE INDEX ix_issue_attachment_issue ON issue_attachment (issue_id, created_at) WHERE deleted_at IS NULL;
CREATE INDEX ix_issue_attachment_ws ON issue_attachment (workspace_id) WHERE deleted_at IS NULL;   -- 워크스페이스 용량 합계

-- 첨부 기록은 지우거나 바꿀 수 없다 — 지움 표시(deleted_at · deleted_by)를 한 번 남기는 것만 된다 (작업 시간 기록과 같다)
CREATE FUNCTION issue_attachment_protect() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'issue_attachment 는 지울 수 없다 — 지움 표시만 한다';
    END IF;
    IF OLD.deleted_at IS NOT NULL
        OR NEW.id IS DISTINCT FROM OLD.id OR NEW.workspace_id IS DISTINCT FROM OLD.workspace_id
        OR NEW.project_id IS DISTINCT FROM OLD.project_id OR NEW.issue_id IS DISTINCT FROM OLD.issue_id
        OR NEW.file_name IS DISTINCT FROM OLD.file_name OR NEW.content_type IS DISTINCT FROM OLD.content_type
        OR NEW.size_bytes IS DISTINCT FROM OLD.size_bytes OR NEW.sha256 IS DISTINCT FROM OLD.sha256
        OR NEW.image IS DISTINCT FROM OLD.image OR NEW.storage_key IS DISTINCT FROM OLD.storage_key
        OR NEW.uploaded_by IS DISTINCT FROM OLD.uploaded_by OR NEW.via IS DISTINCT FROM OLD.via
        OR NEW.created_at IS DISTINCT FROM OLD.created_at THEN
        RAISE EXCEPTION 'issue_attachment 는 지움 표시만 한 번 할 수 있다';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER tr_issue_attachment_protect BEFORE UPDATE OR DELETE ON issue_attachment
    FOR EACH ROW EXECUTE FUNCTION issue_attachment_protect();
