-- 프로젝트 모듈 · 일감의 관련 모듈 (2026-11-23 사용자 결정, moai-works 일감 #27)
--   "프로젝트에서 모듈을 등록해서 항목을 관리하도록 하자" · "이슈 등록할 때 어떤 모듈을 수정 중인지도 알아야겠지?"
--   "이슈에 멀티로 관련 모듈을 등록할 수 있게 해 줘" · "등록 수정이겠지 당연히"
-- 모듈은 프로젝트마다의 목록이다(예: moai-works — api · web-backend · web-frontend · infra · docs). 일감 유형처럼 지우지 않고
-- 쓰지 않음(active = false)으로만 둔다 — 쓰지 않는 모듈은 새로 고를 수 없고, 이미 그 모듈이 붙은 일감에는 그대로 남는다.

CREATE TABLE project_module (
    id           uuid          PRIMARY KEY,
    project_id   uuid          NOT NULL REFERENCES project (id),
    module_key   varchar(32)   NOT NULL                       -- 영문 소문자로 시작하는 1~32자(소문자 · 숫자 · 하이픈), 하이픈으로 끝나지 않음
        CONSTRAINT ck_project_module_key CHECK (module_key ~ '^[a-z]([a-z0-9-]{0,30}[a-z0-9])?$'),
    name         varchar(100)  NOT NULL
        CONSTRAINT ck_project_module_name CHECK (char_length(btrim(name)) >= 1),
    description  varchar(500),
    position     integer       NOT NULL,                      -- 목록 순서(작은 것부터)
    active       boolean       NOT NULL DEFAULT true,         -- false = 쓰지 않음(새로 고를 수 없음)
    created_by   uuid          REFERENCES user_profile (id),
    created_at   timestamptz   NOT NULL,
    updated_by   uuid          REFERENCES user_profile (id),
    updated_at   timestamptz   NOT NULL,
    row_version  integer       NOT NULL DEFAULT 0,
    CONSTRAINT ux_project_module_key UNIQUE (project_id, module_key),
    CONSTRAINT ux_project_module_project_id UNIQUE (project_id, id)        -- 같은 프로젝트 FK 용
);
CREATE INDEX ix_project_module_order ON project_module (project_id, position, module_key);

-- 지우지 않는다(일감 · 판 · 이력이 그 모듈을 가리킨다). key · 프로젝트도 바꾸지 않는다(MCP · 목록 거르기 값이다)
CREATE FUNCTION project_module_protect() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'project_module 은 지울 수 없습니다 — 쓰지 않음(active = false)으로 두십시오';
    END IF;
    IF NEW.module_key <> OLD.module_key OR NEW.project_id <> OLD.project_id THEN
        RAISE EXCEPTION 'project_module 의 key · 프로젝트는 바꿀 수 없습니다';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER tr_project_module_protect BEFORE UPDATE OR DELETE ON project_module
    FOR EACH ROW EXECUTE FUNCTION project_module_protect();

-- 일감의 관련 모듈 (여럿 — 없어도 된다). 일감과 모듈은 같은 프로젝트여야 한다(복합 외래 키)
CREATE TABLE issue_module (
    project_id  uuid  NOT NULL,
    issue_id    uuid  NOT NULL,
    module_id   uuid  NOT NULL,
    PRIMARY KEY (issue_id, module_id),
    CONSTRAINT fk_issue_module_issue FOREIGN KEY (project_id, issue_id) REFERENCES issue (project_id, id),
    CONSTRAINT fk_issue_module_module FOREIGN KEY (project_id, module_id) REFERENCES project_module (project_id, id)
);
-- 모듈로 거르기(그 모듈 가운데 하나라도) · 모듈마다 일감 수
CREATE INDEX ix_issue_module_module ON issue_module (module_id, issue_id);

-- 판에도 그때의 관련 모듈을 남긴다 — [{"key","name"}] (모듈 순서), 없으면 NULL
ALTER TABLE issue_revision ADD COLUMN modules jsonb;
