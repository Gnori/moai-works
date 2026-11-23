-- 일감의 에이전트 세션 (2026-11-23 사용자 결정, moai-works 일감 #26)
--   "이슈 아이템에 필수는 등록 내용은 아니야 agent session name 항목 추가해 줘" · "필터 조건에도 넣고" · "mcp 기능도 당연히 추가하고"
--   "여러 세션들이 한 프로젝트에서 작업하는데 어떤 세션의 작업인지 모르겠다"
-- 그 일을 맡은 AI Agent 세션의 이름(예: moai-works · moai-wiki · echomind-world). 없어도 된다(비면 NULL).
-- 앞뒤 공백을 뺀 1~100자이고 줄바꿈 같은 제어 문자는 없다 — 서버가 다듬어 넣고, DB 도 같은 규칙으로 막는다.
ALTER TABLE issue
    ADD COLUMN agent_session varchar(100)
        CONSTRAINT ck_issue_agent_session CHECK (agent_session IS NULL OR (char_length(agent_session) BETWEEN 1 AND 100
            AND agent_session = btrim(agent_session) AND agent_session !~ '[[:cntrl:]]'));

-- 판에도 그때의 값을 남긴다 (issue_revision 은 추가만 하는 표 — 열을 더하는 것은 트리거와 상관없다)
ALTER TABLE issue_revision ADD COLUMN agent_session varchar(100);

-- 목록 거르기(대소문자 무시) · 이 프로젝트에서 쓰인 세션 이름 목록
CREATE INDEX ix_issue_agent_session ON issue (project_id, lower(agent_session))
    WHERE deleted_at IS NULL AND agent_session IS NOT NULL;
