-- MCP 접근 기록 — MCP 키로 들어온 요청을 읽기까지 한 줄씩 남긴다 (2026-11-22 사용자 결정, ADR-022 · FR-MCP-006)
--   "MCP 키 발급을 해 주면 감사 데이터 모두 기록해야 해. 클라이언트 정보 자세하게. 읽기, 수정, 삭제, 생성 모두"
--   "필터 기능도 잘 만들고"
-- 연결 시작(initialize) · 도구 목록(tools/list) · 도구 호출(tools/call) · 그 밖의 MCP 신호(알림 · ping · 세션 끝)와
-- 키 검사에서 거절된 요청(키 없음 · 틀림 · 만료 · 주인 계정 정지 · 없는 입구)까지 모두 한 줄씩이다.
-- 도구 호출 · 도구 목록 · 연결 시작은 이 줄을 저장한 뒤에야 결과를 돌려준다 — 저장하지 못하면 그 요청도 실패한다.
--
-- 남기지 않는 것: 키 원문 · 해시, 일감 설명 · 댓글 같은 긴 글의 본문(arguments 에는 길이와 sha256 만), 응답 본문.
-- 키는 사용자의 것이라(ADR-029) 워크스페이스는 도구가 그 호출에서 정한 곳이다 — 정하지 않은 줄(연결 · 도구 목록 · 거절 ·
-- 여러 워크스페이스에 걸친 목록)은 workspace_id 가 비어 있고 키 주인만 본다.

CREATE TABLE mcp_access_log (
    id                  uuid          PRIMARY KEY,
    occurred_at         timestamptz   NOT NULL,          -- 받은 시각 (밀리초)
    duration_ms         integer       NOT NULL,          -- 받은 때부터 기록할 때까지
    key_id              uuid,                            -- 키를 찾지 못한 요청(없음 · 틀림)은 NULL
    key_prefix          varchar(16),                     -- 표시 앞부분(mai-wks-Ab12) — 원문이 아니다
    key_name            varchar(100),
    key_owner_id        uuid,                            -- 이 요청이 누구의 권한으로 왔나
    key_owner_name      varchar(200),
    entry               varchar(20)   NOT NULL,          -- 들어온 입구 ISSUE · PROJECT · UNKNOWN(없는 입구)
    request_path        varchar(200)  NOT NULL,
    workspace_id        uuid,                            -- 도구가 정한 워크스페이스 — 없으면 NULL
    mcp_method          varchar(60)   NOT NULL,          -- initialize · tools/list · tools/call · notifications/… · ping · session/end · session/stream
    tool                varchar(60),
    operation           varchar(10)   NOT NULL,          -- READ · CREATE · UPDATE · DELETE · LINK · LIST · CONNECT
    project_key         varchar(32),
    issue_number        integer,
    target_ref          varchar(100),                    -- 연결 대상 번호 · 첨부 id · 템플릿 유형
    arguments           jsonb,                           -- 도구 인자 요약 — 긴 글은 {length, sha256} 만, 8KB 상한
    outcome             varchar(10)   NOT NULL,          -- OK · DENIED · ERROR
    http_status         integer       NOT NULL,
    error_code          varchar(60),
    client_name         varchar(200),                    -- initialize 의 clientInfo.name (같은 세션의 모든 줄에)
    client_version      varchar(100),
    client_title        varchar(200),
    protocol_version    varchar(20),
    client_capabilities varchar(200),                    -- roots · sampling · elicitation …
    mcp_session_id      varchar(100),
    user_agent          varchar(500),
    client_ip           varchar(64),                     -- 믿는 중계(web-backend)의 X-Forwarded-For, 아니면 접속 주소
    trace_id            varchar(64),                     -- 중계가 붙인 X-Correlation-Id(없으면 새로 만든다)
    CONSTRAINT ck_mcp_access_operation CHECK (operation IN ('READ', 'CREATE', 'UPDATE', 'DELETE', 'LINK', 'LIST', 'CONNECT')),
    CONSTRAINT ck_mcp_access_outcome CHECK (outcome IN ('OK', 'DENIED', 'ERROR')),
    CONSTRAINT ck_mcp_access_entry CHECK (entry IN ('ISSUE', 'PROJECT', 'UNKNOWN'))
);

-- 워크스페이스 감사 화면(최신순 커서) · 키 하나의 기록 · 사람 · 세션으로 거르기
CREATE INDEX ix_mcp_access_ws_time ON mcp_access_log (workspace_id, occurred_at DESC, id DESC);
CREATE INDEX ix_mcp_access_key_time ON mcp_access_log (key_id, occurred_at DESC, id DESC);
CREATE INDEX ix_mcp_access_owner_time ON mcp_access_log (key_owner_id, occurred_at DESC);
CREATE INDEX ix_mcp_access_session ON mcp_access_log (mcp_session_id);

-- 추가 전용 — 고치기 · 지우기 · 비우기를 DB 가 막는다(audit_log 와 같다). 보존 기간이 지난 줄을 지우는 일은 운영 배치가 따로 한다.
CREATE FUNCTION mcp_access_log_append_only() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'mcp_access_log 는 추가만 할 수 있습니다';
END $$;

CREATE TRIGGER trg_mcp_access_log_append_only BEFORE UPDATE OR DELETE ON mcp_access_log
    FOR EACH ROW EXECUTE FUNCTION mcp_access_log_append_only();
CREATE TRIGGER trg_mcp_access_log_no_truncate BEFORE TRUNCATE ON mcp_access_log
    FOR EACH STATEMENT EXECUTE FUNCTION mcp_access_log_append_only();
