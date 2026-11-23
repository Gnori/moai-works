-- MCP 키를 워크스페이스에 묶지 않는다 — 키는 사용자의 것이다 (2026-11-23 사용자 결정, ADR-029)
--   "워크스페이스별로 mcp 엔드포인트 만드는 건 안 되지. 사용자가 접근 가능한 곳이면 다 되는 거지"
--   "키는 워크스페이스별로 받는 게 아니라 사용자 기준으로 받는 거야"
--   "키는 로그인한 사용자 기준으로 받는 것이고 그 사용자가 소속된 워크스페이스, 소속된 프로젝트는 모두 접근 가능하고 권한에 따라 하는 거야"
-- 키 하나 = 주인 계정 하나. 키로 닿는 곳은 주인이 멤버이고 지금 역할에 mcp.key.issue.personal 이 있는 워크스페이스 전부이고,
-- 어느 워크스페이스 · 프로젝트인지는 도구를 부를 때마다 주인의 지금 권한으로 정한다(McpScope).
-- 지금 키는 그대로 쓴다 — 묶음(workspace_id)만 없앤다.

-- 보호 함수가 workspace_id 를 보므로 먼저 그 열 없이 다시 만든다(규칙은 V5 · V11 과 같다)
CREATE OR REPLACE FUNCTION mcp_api_key_protect() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'mcp_api_key 는 지울 수 없다 — 만료시킬 뿐이다';
    END IF;
    IF NEW.id IS DISTINCT FROM OLD.id OR NEW.name IS DISTINCT FROM OLD.name
        OR NEW.key_prefix IS DISTINCT FROM OLD.key_prefix OR NEW.key_hash IS DISTINCT FROM OLD.key_hash
        OR NEW.owner_name IS DISTINCT FROM OLD.owner_name OR NEW.owner_user_id IS DISTINCT FROM OLD.owner_user_id
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

DROP INDEX IF EXISTS ix_mcp_api_key_ws;
ALTER TABLE mcp_api_key DROP COLUMN workspace_id;
