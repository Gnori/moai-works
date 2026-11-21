-- moai-works 토대: 계정 · 가입 정책 · 워크스페이스 · 역할 · 멤버십 · 가입 신청 · 초대 · Keycloak 쓰기 기록 · 감사 · MCP 키
-- moai-wiki 의 토대 스키마(V1~V3 · V8 · V9 의 최종 모양)를 한 파일로 옮겼다(2026-11-21). 위키 · 페이지 · 자료실 테이블은 없다.
-- 설계: docs/design/03-workspace-identity.md · docs/design/02-data-model.md
--
-- 정본 구분 (design/03 §6)
--   Keycloak 이 정본: 계정(사용자), 멤버십, 역할(조직 그룹)과 권한 코드 매핑 → 여기 테이블은 판정용 사본
--   DB 가 정본    : 가입 정책, 워크스페이스 설정, 가입 신청, 초대, Keycloak 쓰기 기록, 감사, MCP 키

-- ── 계정 ─────────────────────────────────────────────────────────────
CREATE TABLE user_profile (
    id                    uuid         PRIMARY KEY,          -- Keycloak 사용자 id (토큰 sub)
    username              varchar(255) NOT NULL UNIQUE,      -- Keycloak username (가입 = 이메일)
    email                 varchar(320) NOT NULL,
    display_name          varchar(100) NOT NULL,
    account_status        varchar(30)  NOT NULL CHECK (account_status IN
                              ('PENDING_VERIFICATION', 'PENDING_APPROVAL', 'ACTIVE', 'REJECTED', 'SUSPENDED', 'DELETED')),
    origin                varchar(20)  NOT NULL CHECK (origin IN ('SIGNUP', 'KEYCLOAK')),  -- KEYCLOAK = Keycloak 에서 만든 계정의 첫 로그인
    platform_roles        text[]       NOT NULL DEFAULT '{}',  -- 사본: system-admin · security-officer
    personal_workspace_id uuid,
    last_workspace_id     uuid,                               -- 로그인 뒤 들어갈 워크스페이스
    locale                varchar(10)  NOT NULL DEFAULT 'ko',
    signed_up_at          timestamptz  NOT NULL,
    activated_at          timestamptz,
    last_login_at         timestamptz,
    team_workspace_limit  integer      NOT NULL DEFAULT 0 CHECK (team_workspace_limit BETWEEN 0 AND 100),  -- 만들 수 있는 팀 워크스페이스 수 (시스템 관리자가 정한다)
    row_version           integer      NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX ux_user_profile_email ON user_profile (lower(email));
CREATE INDEX ix_user_profile_status ON user_profile (account_status, signed_up_at);

-- ── 가입 정책 (시스템 관리자만) ──────────────────────────────────────
CREATE TABLE platform_setting (
    key          varchar(100) PRIMARY KEY,
    value        jsonb        NOT NULL,
    updated_by   uuid,
    updated_at   timestamptz  NOT NULL,
    row_version  integer      NOT NULL DEFAULT 0
);
INSERT INTO platform_setting (key, value, updated_at) VALUES
    ('signup.enabled',                           'true',  now()),
    ('signup.allowed_email_domains',             '[]',    now()),
    ('signup.require_email_verification',        'true',  now()),
    ('signup.require_admin_approval',            'false', now()),
    ('signup.rate_limit_per_ip_hour',            '10',    now()),
    ('workspace.team_workspace_grant_default',   '2',     now());

-- ── 이메일 인증 코드 (원문 저장 안 함, HMAC 만) ───────────────────────
CREATE TABLE email_verification (
    id           uuid        PRIMARY KEY,
    user_id      uuid        NOT NULL REFERENCES user_profile (id) ON DELETE CASCADE,
    purpose      varchar(20) NOT NULL CHECK (purpose IN ('SIGNUP', 'PASSWORD_RESET', 'EMAIL_CHANGE')),
    code_hmac    varchar(64) NOT NULL,
    expires_at   timestamptz NOT NULL,
    attempts     integer     NOT NULL DEFAULT 0,
    consumed_at  timestamptz,
    created_at   timestamptz NOT NULL
);
CREATE INDEX ix_email_verification_user ON email_verification (user_id, purpose, created_at DESC);

-- ── 워크스페이스 ──────────────────────────────────────────────────────
CREATE TABLE workspace (
    id                     uuid         PRIMARY KEY,
    slug                   varchar(63)  NOT NULL UNIQUE,     -- 바꾸지 않는다 (MCP 주소, Keycloak 조직 alias)
    name                   varchar(100) NOT NULL,
    description            varchar(1000),
    kind                   varchar(20)  NOT NULL CHECK (kind IN ('PERSONAL', 'TEAM')),
    visibility             varchar(20)  NOT NULL CHECK (visibility IN ('PUBLIC', 'PRIVATE')),
    join_policy            varchar(20)  NOT NULL CHECK (join_policy IN ('APPROVAL', 'AUTO')),   -- 퍼블릭만 쓴다. 프라이빗은 초대로만
    allowed_join_domains   text[]       NOT NULL DEFAULT '{}',
    default_role_key       varchar(63)  NOT NULL DEFAULT 'member',
    rejoin_wait_days       integer      NOT NULL DEFAULT 7,
    keycloak_org_id        varchar(64)  UNIQUE,
    status                 varchar(30)  NOT NULL CHECK (status IN
                               ('PROVISIONING', 'ACTIVE', 'SUSPENDED', 'DELETION_PENDING', 'DELETED')),
    created_by             uuid         NOT NULL REFERENCES user_profile (id),
    created_at             timestamptz  NOT NULL,
    updated_at             timestamptz  NOT NULL,
    deletion_requested_at  timestamptz,
    row_version            integer      NOT NULL DEFAULT 0,
    -- workspace- + 고유 id 12자 (순번 아님, 서버가 정함 — WorkspaceSlugs)
    CONSTRAINT workspace_slug_format CHECK (slug ~ '^workspace-[a-hj-km-np-tv-z2-9]{12}$')
);
-- 개인 워크스페이스는 계정당 정확히 1개
CREATE UNIQUE INDEX ux_workspace_personal ON workspace (created_by) WHERE kind = 'PERSONAL' AND status <> 'DELETED';

ALTER TABLE user_profile
    ADD CONSTRAINT fk_user_profile_personal_ws FOREIGN KEY (personal_workspace_id) REFERENCES workspace (id),
    ADD CONSTRAINT fk_user_profile_last_ws FOREIGN KEY (last_workspace_id) REFERENCES workspace (id) ON DELETE SET NULL;

-- ── 권한 코드 카탈로그 (코드의 Permission enum 이 정본 — 기동 때 이 표를 맞춘다) ──
CREATE TABLE permission (
    code         varchar(100) PRIMARY KEY,               -- = Keycloak 클라이언트 moai-works-api 의 클라이언트 역할
    description  varchar(300) NOT NULL,
    owner_only   boolean      NOT NULL DEFAULT false,    -- 소유자 역할만 가질 수 있다
    retired_at   timestamptz                              -- 카탈로그에서 빠진 코드 (행은 남긴다)
);

-- ── 역할 (사본: 정본은 Keycloak 조직 그룹 /roles/<role_key> + 클라이언트 역할 매핑) ─
CREATE TABLE workspace_role (
    id                 uuid          PRIMARY KEY,
    workspace_id       uuid          NOT NULL REFERENCES workspace (id) ON DELETE CASCADE,
    role_key           varchar(63)   NOT NULL,             -- owner · admin · member · custom-<…>
    name               varchar(100)  NOT NULL,
    description        varchar(500),
    builtin            boolean       NOT NULL,
    keycloak_group_id  varchar(64),
    created_at         timestamptz   NOT NULL,
    updated_at         timestamptz   NOT NULL,
    row_version        integer       NOT NULL DEFAULT 0,
    UNIQUE (workspace_id, role_key)
);

-- 역할이 담는 권한 코드 (사본: 정본은 조직 그룹에 매핑한 클라이언트 역할). JSON 컬럼 대신 표 — 없는 코드는 넣을 수 없다.
CREATE TABLE role_permission (
    role_id          uuid         NOT NULL REFERENCES workspace_role (id) ON DELETE CASCADE,
    permission_code  varchar(100) NOT NULL REFERENCES permission (code),
    PRIMARY KEY (role_id, permission_code)
);

-- ── 멤버십 (사본: 정본은 Keycloak 조직 멤버 · 그룹 가입) ───────────────
CREATE TABLE workspace_member (
    workspace_id  uuid         NOT NULL REFERENCES workspace (id) ON DELETE CASCADE,
    user_id       uuid         NOT NULL REFERENCES user_profile (id),
    status        varchar(20)  NOT NULL CHECK (status IN ('ACTIVE', 'SUSPENDED')),
    joined_via    varchar(20)  NOT NULL CHECK (joined_via IN ('CREATOR', 'REQUEST', 'AUTO', 'INVITE', 'ADMIN')),
    joined_at     timestamptz  NOT NULL,
    row_version   integer      NOT NULL DEFAULT 0,
    PRIMARY KEY (workspace_id, user_id)
);
CREATE INDEX ix_workspace_member_user ON workspace_member (user_id);

CREATE TABLE member_role (
    workspace_id  uuid        NOT NULL,
    user_id       uuid        NOT NULL,
    role_key      varchar(63) NOT NULL,
    granted_by    uuid,
    granted_at    timestamptz NOT NULL,
    PRIMARY KEY (workspace_id, user_id, role_key),
    FOREIGN KEY (workspace_id, user_id) REFERENCES workspace_member (workspace_id, user_id) ON DELETE CASCADE,
    FOREIGN KEY (workspace_id, role_key) REFERENCES workspace_role (workspace_id, role_key)
);

-- ── 가입 신청 (정본) ──────────────────────────────────────────────────
CREATE TABLE join_request (
    id                uuid          PRIMARY KEY,
    workspace_id      uuid          NOT NULL REFERENCES workspace (id) ON DELETE CASCADE,
    user_id           uuid          NOT NULL REFERENCES user_profile (id),
    message           varchar(500),
    status            varchar(20)   NOT NULL CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELED', 'EXPIRED')),
    granted_role_key  varchar(63),
    decided_by        uuid,
    decided_at        timestamptz,
    decision_note     varchar(500),
    requested_at      timestamptz   NOT NULL,
    expires_at        timestamptz   NOT NULL,
    row_version       integer       NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX ux_join_request_pending ON join_request (workspace_id, user_id) WHERE status = 'PENDING';
CREATE INDEX ix_join_request_ws ON join_request (workspace_id, status, requested_at DESC);

-- ── 초대 (정본) ──────────────────────────────────────────────────────
CREATE TABLE invitation (
    id                uuid          PRIMARY KEY,
    workspace_id      uuid          NOT NULL REFERENCES workspace (id) ON DELETE CASCADE,
    email             varchar(320)  NOT NULL,              -- 받는 사람 (소문자). 이 이메일의 계정만 수락할 수 있다
    role_key          varchar(63)   NOT NULL,
    message           varchar(500),
    invited_by        uuid          NOT NULL REFERENCES user_profile (id),
    token_hash        varchar(64)   NOT NULL UNIQUE,       -- 초대 링크 토큰의 SHA-256 (원문은 저장하지 않는다)
    status            varchar(20)   NOT NULL CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED', 'REVOKED', 'EXPIRED')),
    accepted_user_id  uuid          REFERENCES user_profile (id),
    created_at        timestamptz   NOT NULL,
    expires_at        timestamptz   NOT NULL,
    decided_at        timestamptz,
    row_version       integer       NOT NULL DEFAULT 0,
    FOREIGN KEY (workspace_id, role_key) REFERENCES workspace_role (workspace_id, role_key)
);
-- 한 워크스페이스에 같은 이메일의 대기 초대는 하나뿐
CREATE UNIQUE INDEX ux_invitation_pending ON invitation (workspace_id, lower(email)) WHERE status = 'PENDING';
CREATE INDEX ix_invitation_email ON invitation (lower(email), status);

-- ── Keycloak 쓰기 기록 (Keycloak 먼저 쓰고, 단계마다 남겨 중간 실패를 이어서 끝낸다) ─
CREATE TABLE keycloak_operation (
    id               uuid          PRIMARY KEY,
    saga_id          uuid          NOT NULL,
    kind             varchar(40)   NOT NULL,
    idempotency_key  varchar(200)  NOT NULL UNIQUE,
    payload          jsonb         NOT NULL DEFAULT '{}',
    result           jsonb,
    status           varchar(20)   NOT NULL CHECK (status IN ('PENDING', 'DONE', 'FAILED', 'COMPENSATED')),
    attempts         integer       NOT NULL DEFAULT 0,
    last_error       varchar(1000),
    created_at       timestamptz   NOT NULL,
    done_at          timestamptz
);
CREATE INDEX ix_keycloak_operation_saga ON keycloak_operation (saga_id, created_at);
CREATE INDEX ix_keycloak_operation_open ON keycloak_operation (status, created_at) WHERE status IN ('PENDING', 'FAILED');

-- ── 감사 (추가 전용) ──────────────────────────────────────────────────
CREATE TABLE audit_log (
    id            uuid          PRIMARY KEY,
    occurred_at   timestamptz   NOT NULL,
    actor_type    varchar(20)   NOT NULL CHECK (actor_type IN ('USER', 'SERVICE', 'SYSTEM')),
    actor_id      uuid,
    actor_name    varchar(255),
    workspace_id  uuid,
    action        varchar(100)  NOT NULL,
    target_type   varchar(50),
    target_id     varchar(100),
    details       jsonb         NOT NULL DEFAULT '{}',
    ip            varchar(64),
    trace_id      varchar(64)
);
CREATE INDEX ix_audit_log_ws ON audit_log (workspace_id, occurred_at DESC);
CREATE INDEX ix_audit_log_actor ON audit_log (actor_id, occurred_at DESC);
CREATE INDEX ix_audit_log_time ON audit_log (occurred_at DESC);

CREATE FUNCTION audit_log_append_only() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'audit_log 는 추가만 할 수 있습니다';
END;
$$;
CREATE TRIGGER trg_audit_log_append_only BEFORE UPDATE OR DELETE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION audit_log_append_only();

-- ── MCP 키 (정본) — 원문은 저장하지 않는다(SHA-256 만). seed-site-keeper-api mcp_api_key 와 같은 원칙.
CREATE TABLE mcp_api_key (
    id            uuid         PRIMARY KEY,
    name          varchar(100) NOT NULL,
    key_prefix    varchar(16)  NOT NULL,
    key_hash      varchar(64)  NOT NULL UNIQUE,
    key_type      varchar(20)  NOT NULL CHECK (key_type IN ('PERSONAL', 'SERVICE')),
    endpoint      varchar(20)  NOT NULL CHECK (endpoint IN ('PROJECT')),
    owner_subject varchar(255) NOT NULL,
    owner_name    varchar(255) NOT NULL,
    created_at    timestamptz  NOT NULL,
    expires_at    timestamptz  NOT NULL,
    revoked_at    timestamptz,
    last_used_at  timestamptz
);

CREATE INDEX ix_mcp_api_key_owner ON mcp_api_key (owner_subject, created_at DESC);
