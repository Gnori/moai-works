-- MCP 키를 입구에 묶지 않는다 (2026-11-23 사용자 결정)
--   "moai-works 의 issue 의 mcp 엔드포인트가 따로 있어야 해. 즉 다른 관리 기능과 이슈 관리 기능을 서로 구분하고 싶은 거야"
--   "키는 권한만 있으면 어떤 mcp 엔드포인트든 연결 가능해야 해"
-- 입구는 기능으로 나눈다 — 일감 /mcp/issue · 관리 /mcp/manage. 키 하나(워크스페이스 하나 · 주인 하나)로 모든 입구에 붙고,
-- 할 수 있는 일은 도구를 부를 때마다 키 주인의 지금 권한으로 판정한다. 그래서 키의 입구(endpoint) 열은 지운다.
-- 지금 키는 그대로 쓴다(주소만 /mcp/project → /mcp/issue).

-- 보호 함수가 endpoint 를 보므로 먼저 endpoint 없이 다시 만든다(규칙은 V5 와 같다)
CREATE OR REPLACE FUNCTION mcp_api_key_protect() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'mcp_api_key 는 지울 수 없다 — 만료시킬 뿐이다';
    END IF;
    IF NEW.id IS DISTINCT FROM OLD.id OR NEW.name IS DISTINCT FROM OLD.name
        OR NEW.key_prefix IS DISTINCT FROM OLD.key_prefix OR NEW.key_hash IS DISTINCT FROM OLD.key_hash
        OR NEW.owner_name IS DISTINCT FROM OLD.owner_name
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

ALTER TABLE mcp_api_key DROP COLUMN endpoint;
