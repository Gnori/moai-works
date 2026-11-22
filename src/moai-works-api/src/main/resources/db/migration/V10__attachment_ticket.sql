-- 첨부 일회용 티켓 (2026-11-22 사용자: "첨부파일은 REST API 로 구현해야 하지? MCP 로는 안 될 거야? 그래서 아마 임시 티켓을 받아 쓰고
--   쓰면 바로 익스파이어 되는 티켓 시스템으로 하는 게 좋을 거야")
-- 바깥 AI Agent 가 MCP 도구로 티켓을 받고, 파일은 web-backend 의 /mcp/files/upload · download 로 직접 보낸다(MCP 메시지에 싣지 않는다).
-- 티켓은 원문을 두지 않고 SHA-256 만, 몇 분만 유효, 한 번 쓰면 끝(used_at). 키 주인 · 키 · 워크스페이스 · 프로젝트 · 일감 · 이름 · 크기에 묶인다.
CREATE TABLE attachment_ticket (
    id             uuid          PRIMARY KEY,
    token_hash     char(64)      NOT NULL UNIQUE,
    kind           varchar(10)   NOT NULL CHECK (kind IN ('UPLOAD', 'DOWNLOAD')),
    mcp_key_id     uuid          NOT NULL REFERENCES mcp_api_key (id),
    owner_user_id  uuid          NOT NULL REFERENCES user_profile (id),
    workspace_id   uuid          NOT NULL REFERENCES workspace (id),
    project_id     uuid          NOT NULL REFERENCES project (id),
    issue_number   integer       NOT NULL,
    attachment_id  uuid          REFERENCES issue_attachment (id),     -- 받기 티켓만
    file_name      varchar(255),                                       -- 올리기 티켓만
    content_type   varchar(100),
    max_bytes      bigint,                                             -- 올리기 티켓만 — 알린 크기를 넘으면 거절
    created_at     timestamptz   NOT NULL,
    expires_at     timestamptz   NOT NULL,
    used_at        timestamptz
);
CREATE INDEX ix_attachment_ticket_expires ON attachment_ticket (expires_at) WHERE used_at IS NULL;
