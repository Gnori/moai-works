-- 프로젝트 · 프로젝트 역할 · 일감 (design/04 · design/05 · design/02)
-- moai-wiki 의 위키(V5__wiki.sql) · 페이지(V6__page.sql) 자리다: 위키 → 프로젝트, 페이지 → 일감.
-- 권한 코드(project.* · issue.*)는 표에 넣지 않는다 — Permission enum 이 정본이고 api 가 기동 때 permission 표를 맞춘다.

-- ── 프로젝트 — 워크스페이스 안의 일감 묶음이자 읽기 권한의 경계 ─────────────────────────
CREATE TABLE project (
    id            uuid          PRIMARY KEY,
    workspace_id  uuid          NOT NULL REFERENCES workspace (id),
    key           varchar(32)   NOT NULL,                  -- 주소 이름. 바꾸지 않는다
    name          varchar(100)  NOT NULL COLLATE "C",      -- 한글 가나다순 정렬 (moai-wiki V7 과 같다)
    description   varchar(1000),
    visibility    varchar(20)   NOT NULL CHECK (visibility IN ('WORKSPACE', 'RESTRICTED')),
    status        varchar(20)   NOT NULL CHECK (status IN ('ACTIVE', 'ARCHIVED', 'DELETED')),
    issue_seq     integer       NOT NULL DEFAULT 0,        -- 마지막으로 준 일감 번호 (잠그고 올린다)
    settings      jsonb         NOT NULL DEFAULT '{}',
    created_by    uuid          NOT NULL REFERENCES user_profile (id),
    created_at    timestamptz   NOT NULL,
    updated_at    timestamptz   NOT NULL,
    archived_at   timestamptz,
    deleted_at    timestamptz,
    row_version   integer       NOT NULL DEFAULT 0,
    CHECK (key ~ '^[a-z][a-z0-9-]{0,30}[a-z0-9]$')         -- 2~32자 (FR-PRJ-001)
);
-- 지운 프로젝트의 key 는 다시 쓸 수 있다
CREATE UNIQUE INDEX ux_project_key_live ON project (workspace_id, key) WHERE status <> 'DELETED';
CREATE INDEX ix_project_workspace ON project (workspace_id, status, name);

-- ── 프로젝트 역할 — 정본은 Keycloak 조직 그룹 /projects/<project id>/<role>, 이 표는 판정용 사본 ──
-- 한 사람이 한 프로젝트에서 역할을 여러 개 가질 수 있다(권한은 합친다) — moai-wiki 의 wiki_role_binding 설계와 같다
CREATE TABLE project_role_binding (
    project_id         uuid          NOT NULL REFERENCES project (id),
    user_id            uuid          NOT NULL REFERENCES user_profile (id),
    role               varchar(30)   NOT NULL
                           CHECK (role IN ('project-admin', 'project-curator', 'project-editor', 'project-reader')),
    keycloak_group_id  varchar(64),                         -- 그 역할의 조직 그룹 id
    granted_by         uuid          REFERENCES user_profile (id),
    granted_at         timestamptz   NOT NULL,
    PRIMARY KEY (project_id, user_id, role)
);
CREATE INDEX ix_project_role_binding_user ON project_role_binding (user_id);

-- ── 일감 유형 — 공통 시드 (moai-wiki 의 page_type 처럼). 프로젝트마다 바꾸기는 나중 ─────────
CREATE TABLE issue_type (
    key          varchar(30)   PRIMARY KEY,
    name         varchar(100)  NOT NULL,
    description  varchar(300)  NOT NULL DEFAULT '',
    position     integer       NOT NULL
);
INSERT INTO issue_type (key, name, description, position) VALUES
    ('requirement', '요구사항', '만들어야 할 것 — 무엇을 왜', 1),
    ('feature',     '기능',     '새로 만들거나 늘리는 기능', 2),
    ('bug',         '버그',     '고쳐야 할 잘못된 동작', 3),
    ('task',        '작업',     '그 밖의 할 일', 4);

-- ── 일감 ──────────────────────────────────────────────────────────────
CREATE TABLE issue (
    id                uuid          PRIMARY KEY,
    project_id        uuid          NOT NULL REFERENCES project (id),
    number            integer       NOT NULL,                -- 프로젝트 안 순번 (#1, #2 …)
    issue_type        varchar(30)   NOT NULL REFERENCES issue_type (key),
    title             varchar(200)  NOT NULL,
    description       text          NOT NULL DEFAULT '' CHECK (char_length(description) <= 30000),   -- Markdown
    status            varchar(20)   NOT NULL CHECK (status IN ('NEW', 'IN_PROGRESS', 'DONE', 'CLOSED')),
    manager_id        uuid          NOT NULL REFERENCES user_profile (id),   -- 관리자
    assignee_id       uuid          REFERENCES user_profile (id),            -- 담당자 (없어도 된다)
    parent_id         uuid,                                                  -- 상위 일감 (같은 프로젝트)
    due_date          date,                                                  -- 완료 기한
    start_date        date,                                                  -- 시작일
    planned_end_date  date,                                                  -- 완료 예정일
    completed_on      date,                                                  -- 완료일 (DONE 이 되면 채운다)
    closed_on         date,                                                  -- 종료일 (CLOSED 가 되면 채운다)
    created_by        uuid          NOT NULL REFERENCES user_profile (id),
    created_at        timestamptz   NOT NULL,
    updated_at        timestamptz   NOT NULL,
    deleted_at        timestamptz,
    row_version       integer       NOT NULL DEFAULT 0,
    UNIQUE (project_id, number),
    UNIQUE (project_id, id),                                                 -- 같은 프로젝트 상위 FK 용
    CHECK (parent_id IS NULL OR parent_id <> id),
    CHECK (start_date IS NULL OR planned_end_date IS NULL OR start_date <= planned_end_date),
    CHECK (start_date IS NULL OR due_date IS NULL OR start_date <= due_date),
    CHECK (completed_on IS NULL OR closed_on IS NULL OR completed_on <= closed_on)
);
-- 상위 일감은 같은 프로젝트여야 한다 (moai-wiki 의 fk_page_parent_same_wiki 와 같다)
ALTER TABLE issue ADD CONSTRAINT fk_issue_parent_same_project
    FOREIGN KEY (project_id, parent_id) REFERENCES issue (project_id, id);
CREATE INDEX ix_issue_list     ON issue (project_id, status, number DESC) WHERE deleted_at IS NULL;
CREATE INDEX ix_issue_children ON issue (project_id, parent_id) WHERE deleted_at IS NULL;
CREATE INDEX ix_issue_assignee ON issue (assignee_id) WHERE assignee_id IS NOT NULL AND deleted_at IS NULL;
CREATE INDEX ix_issue_manager  ON issue (manager_id) WHERE deleted_at IS NULL;

-- ── 연결 일감 — 같은 두 일감 사이에 하나. 양쪽 일감에서 보인다 ─────────────────────────
CREATE TABLE issue_link (
    id               uuid          PRIMARY KEY,
    source_issue_id  uuid          NOT NULL REFERENCES issue (id),
    target_issue_id  uuid          NOT NULL REFERENCES issue (id),
    link_type        varchar(20)   NOT NULL CHECK (link_type IN ('RELATES', 'BLOCKS', 'PRECEDES', 'DUPLICATES')),
    created_by       uuid          NOT NULL REFERENCES user_profile (id),
    created_at       timestamptz   NOT NULL,
    CHECK (source_issue_id <> target_issue_id)
);
-- 방향과 상관없이 한 쌍에 하나
CREATE UNIQUE INDEX ux_issue_link_pair ON issue_link (LEAST(source_issue_id, target_issue_id),
    GREATEST(source_issue_id, target_issue_id));
CREATE INDEX ix_issue_link_target ON issue_link (target_issue_id);

-- ── 이력 — 고칠 때마다 한 줄(필드 변경), 댓글은 notes 만 있는 줄. 덮어쓰지 않는다 ─────────────
CREATE TABLE issue_journal (
    id          uuid          PRIMARY KEY,
    issue_id    uuid          NOT NULL REFERENCES issue (id),
    actor_id    uuid          REFERENCES user_profile (id),   -- NULL = 서비스(MCP 서비스 키)
    actor_type  varchar(20)   NOT NULL CHECK (actor_type IN ('USER', 'SERVICE')),
    actor_name  varchar(200)  NOT NULL,
    via         varchar(10)   NOT NULL CHECK (via IN ('WEB', 'MCP')),
    created_at  timestamptz   NOT NULL,
    notes       text          CHECK (notes IS NULL OR char_length(notes) <= 30000),   -- 댓글 (Markdown)
    changes     jsonb         NOT NULL DEFAULT '[]'          -- [{"field","before","after"}]
);
CREATE INDEX ix_issue_journal_issue ON issue_journal (issue_id, created_at);
