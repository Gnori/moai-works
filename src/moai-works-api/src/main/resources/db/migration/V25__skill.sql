-- Agent 스킬 — 시스템 관리자가 등록 · 수정 · 삭제하고 리비전으로 관리한다 (2026-11-24 사용자 결정, 일감 #30)
--   "moai-works 스킬은 시스템 관리자가 등록, 수정 삭제 할 수 있게 하고 역시 리비전 관리 해줘"
--   "스킬은 mcp로 내려받을 수 있게 해줘"
-- 스킬은 시스템 전체의 것이다(워크스페이스에 묶이지 않는다). 바깥 Agent(Claude Code 등)가 지시로 읽는 글이라
-- 쓰기는 시스템 관리자만, 읽기는 로그인한 사람 누구나 · MCP 키 누구나(지운 스킬은 숨긴다). 판정은 api 한 곳.
--
-- 이름(name)은 Claude Code 스킬 이름 규칙 — 영문 소문자 · 숫자 · 하이픈(앞뒤 · 연달아 금지), 64자까지. 만든 뒤 바꾸지 않는다.
-- 지우기는 지움 표시만 한다(deleted_at · deleted_by) — 목록 · MCP 에서 숨고, 되살릴 수 있고, 리비전은 그대로 남는다.
-- 지운 스킬의 이름도 그대로 차지한다(같은 이름으로 새로 만들지 않고 되살린다).
CREATE TABLE skill (
    id           uuid           PRIMARY KEY,
    name         varchar(64)    NOT NULL,
    description  varchar(1024)  NOT NULL,
    body         text           NOT NULL,
    revision_no  integer        NOT NULL,                              -- 지금 리비전(1부터 빠짐없이)
    row_version  integer        NOT NULL DEFAULT 0,
    created_by   uuid           REFERENCES user_profile (id),
    created_at   timestamptz    NOT NULL,
    updated_by   uuid           REFERENCES user_profile (id),
    updated_at   timestamptz    NOT NULL,
    deleted_at   timestamptz,
    deleted_by   uuid           REFERENCES user_profile (id),
    CONSTRAINT ux_skill_name UNIQUE (name),
    CONSTRAINT ck_skill_name CHECK (name ~ '^[a-z0-9]+(-[a-z0-9]+)*$' AND char_length(name) <= 64),
    CONSTRAINT ck_skill_description CHECK (char_length(btrim(description)) > 0),
    CONSTRAINT ck_skill_body CHECK (char_length(btrim(body)) > 0 AND char_length(body) <= 100000),
    CONSTRAINT ck_skill_deleted CHECK ((deleted_at IS NULL) = (deleted_by IS NULL))
);

-- 이름은 만든 뒤 바꾸지 않고, 행은 지우지 않는다(지움 표시만) — 화면 · api 의 규칙을 DB 도 지킨다
CREATE FUNCTION skill_protect() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'skill 은 지울 수 없다 — 지움 표시(deleted_at)만 한다';
    END IF;
    IF NEW.id IS DISTINCT FROM OLD.id OR NEW.name IS DISTINCT FROM OLD.name
        OR NEW.created_by IS DISTINCT FROM OLD.created_by OR NEW.created_at IS DISTINCT FROM OLD.created_at THEN
        RAISE EXCEPTION 'skill: 이름 · 만든 사람 · 만든 때는 바꿀 수 없다';
    END IF;
    IF NEW.revision_no < OLD.revision_no THEN
        RAISE EXCEPTION 'skill: 리비전 번호는 줄지 않는다';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER tr_skill_protect BEFORE UPDATE OR DELETE ON skill
    FOR EACH ROW EXECUTE FUNCTION skill_protect();

-- 스킬의 리비전 — 만들기 · 수정 · 가져오기 · 옛 리비전으로 되돌리기(restore)마다 그때의 전체(이름 · 설명 · 본문)를 한 줄로 남긴다.
-- 내용이 그대로인 저장은 리비전을 만들지 않는다. 삭제 · 되살리기(undelete)는 내용이 바뀌지 않아 리비전이 아니다(감사에 남는다).
-- moai-wiki 의 스킬 저장소와 같은 모양이다(2026-11-24 — 이름 name · 바꾼 까닭 · 되돌린 리비전).
CREATE TABLE skill_revision (
    id              uuid           PRIMARY KEY,
    skill_id        uuid           NOT NULL REFERENCES skill (id),
    revision_no     integer        NOT NULL,
    name            varchar(64)    NOT NULL,
    description     varchar(1024)  NOT NULL,
    body            text           NOT NULL,
    kind            varchar(10)    NOT NULL,                           -- CREATE · UPDATE · IMPORT(SKILL.md 가져오기) · RESTORE(옛 리비전으로 되돌림)
    restored_from   integer,                                           -- RESTORE 면 되돌린 리비전 번호
    changed_fields  varchar(100)   NOT NULL DEFAULT '',                -- 바로 앞 리비전과 견줘 바뀐 것(쉼표로): description · body
    change_note     varchar(500),                                      -- 바꾼 까닭(고르면)
    actor_id        uuid           REFERENCES user_profile (id),
    actor_type      varchar(20)    NOT NULL,
    actor_name      varchar(200)   NOT NULL,
    via             varchar(10)    NOT NULL,
    created_at      timestamptz    NOT NULL,                           -- 밀리초까지 보인다
    CONSTRAINT ux_skill_revision UNIQUE (skill_id, revision_no),
    CONSTRAINT ck_skill_revision_kind CHECK (kind IN ('CREATE', 'UPDATE', 'IMPORT', 'RESTORE')),
    CONSTRAINT ck_skill_revision_via CHECK (via IN ('WEB', 'MCP', 'API')),
    CONSTRAINT ck_skill_revision_restored CHECK ((kind = 'RESTORE') = (restored_from IS NOT NULL))
);
CREATE INDEX ix_skill_revision_skill ON skill_revision (skill_id, revision_no DESC);

-- 리비전은 추가만 한다 — 고치기 · 지우기 · 비우기를 DB 가 막는다(issue_revision · 감사 기록과 같다)
CREATE FUNCTION skill_revision_append_only() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'skill_revision 은 추가만 할 수 있다';
END $$;
CREATE TRIGGER tr_skill_revision_append_only BEFORE UPDATE OR DELETE ON skill_revision
    FOR EACH ROW EXECUTE FUNCTION skill_revision_append_only();
CREATE TRIGGER tr_skill_revision_no_truncate BEFORE TRUNCATE ON skill_revision
    FOR EACH STATEMENT EXECUTE FUNCTION skill_revision_append_only();
