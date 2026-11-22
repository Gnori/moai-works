-- 유형별 일감 템플릿 (2026-11-23 사용자 결정)
--   "이슈의 유형별로 작성 템플릿 넣어 주고 그걸 관리할 수 있게 해 줘. 또한 mcp 제공해서 그에 맞게 이슈 작성 및 수정할 수 있게"
--   결정: 워크스페이스 + 프로젝트(워크스페이스가 기본, 프로젝트가 덮어씀) · MCP 관리 도구도 · 빠진 항목은 알려만 줌
-- 고르는 순서: 프로젝트 행 → 워크스페이스 행(project_id 없음) → 코드의 처음 템플릿. 행을 지우면 한 단계 위로 돌아간다.
CREATE TABLE issue_template (
    id            uuid          PRIMARY KEY,
    workspace_id  uuid          NOT NULL REFERENCES workspace (id),
    project_id    uuid          REFERENCES project (id),            -- 없으면 워크스페이스 기본
    issue_type    varchar(30)   NOT NULL REFERENCES issue_type (key),
    body          text          NOT NULL CHECK (char_length(body) <= 30000),
    updated_by    uuid          REFERENCES user_profile (id),
    updated_at    timestamptz   NOT NULL,
    row_version   integer       NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX ux_issue_template_workspace ON issue_template (workspace_id, issue_type) WHERE project_id IS NULL;
CREATE UNIQUE INDEX ux_issue_template_project   ON issue_template (project_id, issue_type) WHERE project_id IS NOT NULL;
