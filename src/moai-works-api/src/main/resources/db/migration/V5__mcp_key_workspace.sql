-- MCP 키를 워크스페이스 · 주인 계정에 묶는다 (2026-11-22 사용자 결정 — ADR-022 · ADR-024)
--   "MCP 키는 사용자 계정과 연결되어 있으며 사용자 권한 범위만 접근 가능해야 한다"
--   "키 발급 이력도 워크스페이스 관리자가 확인 가능해야 하고 익스파이어도 시킬 수 있어야. 삭제는 아니고 키는 삭제 개념은 없어"
--   "일단 키 발급 및 관리 시스템 만들고 관리자는 그걸 모두 보고 관리할 수 있어야"
-- 키 종류(개인 · 서비스)는 없앴다 — 모든 키는 주인 계정의 권한 안에서만 닿는다. "폐기"도 없다 — 일찍 만료시킬 뿐이다.
DELETE FROM mcp_api_key;                                   -- 옛 골격 키(워크스페이스 없음)는 쓸 곳이 없다 — 개발 데이터뿐
DROP INDEX IF EXISTS ix_mcp_api_key_owner;
ALTER TABLE mcp_api_key
    DROP COLUMN key_type,
    DROP COLUMN revoked_at,
    DROP COLUMN owner_subject,
    ADD COLUMN workspace_id       uuid          NOT NULL REFERENCES workspace (id),
    ADD COLUMN owner_user_id      uuid          NOT NULL REFERENCES user_profile (id),   -- 주인 = 발급한 사람
    ADD COLUMN issued_ip          varchar(64),
    ADD COLUMN issued_user_agent  varchar(500),
    ADD COLUMN expired_by         uuid          REFERENCES user_profile (id),            -- 일찍 만료시킨 사람
    ADD COLUMN expire_reason      varchar(20)   CHECK (expire_reason IN ('MANUAL', 'MEMBER_LEFT', 'ENDPOINT_DELETED')),
    ADD COLUMN expire_note        varchar(500);
CREATE INDEX ix_mcp_api_key_ws    ON mcp_api_key (workspace_id, created_at DESC);
CREATE INDEX ix_mcp_api_key_owner ON mcp_api_key (owner_user_id, created_at DESC);

-- 키는 지우지 않는다. 바꿀 수 있는 것은 마지막 사용 시각과, 한 번의 "일찍 만료시키기"(expires_at 을 앞당기고 누가 · 왜)뿐이다
CREATE FUNCTION mcp_api_key_protect() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'mcp_api_key 는 지울 수 없다 — 만료시킬 뿐이다';
    END IF;
    IF NEW.id IS DISTINCT FROM OLD.id OR NEW.name IS DISTINCT FROM OLD.name
        OR NEW.key_prefix IS DISTINCT FROM OLD.key_prefix OR NEW.key_hash IS DISTINCT FROM OLD.key_hash
        OR NEW.endpoint IS DISTINCT FROM OLD.endpoint OR NEW.owner_name IS DISTINCT FROM OLD.owner_name
        OR NEW.workspace_id IS DISTINCT FROM OLD.workspace_id OR NEW.owner_user_id IS DISTINCT FROM OLD.owner_user_id
        OR NEW.created_at IS DISTINCT FROM OLD.created_at OR NEW.issued_ip IS DISTINCT FROM OLD.issued_ip
        OR NEW.issued_user_agent IS DISTINCT FROM OLD.issued_user_agent THEN
        RAISE EXCEPTION 'mcp_api_key 의 발급 정보는 바꿀 수 없다';
    END IF;
    IF NEW.expires_at > OLD.expires_at THEN
        RAISE EXCEPTION 'mcp_api_key 의 기간은 늘릴 수 없다 — 새로 발급한다';
    END IF;
    IF OLD.expire_reason IS NOT NULL AND (NEW.expire_reason IS DISTINCT FROM OLD.expire_reason
        OR NEW.expired_by IS DISTINCT FROM OLD.expired_by OR NEW.expires_at IS DISTINCT FROM OLD.expires_at) THEN
        RAISE EXCEPTION 'mcp_api_key: 이미 만료시킨 키는 다시 바꿀 수 없다';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER tr_mcp_api_key_protect BEFORE UPDATE OR DELETE ON mcp_api_key
    FOR EACH ROW EXECUTE FUNCTION mcp_api_key_protect();
