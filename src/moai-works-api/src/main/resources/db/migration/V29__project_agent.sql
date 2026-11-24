-- 프로젝트 멤버의 둘째 종류 — 에이전트 (2026-11-25 사용자 결정, moai-works 일감 #285)
--   "프로젝트 멤버에 에이전트 세션네임도 추가해줘" · "멤버는 사람, 에이전트 2가지로 나눠 지는거지"
-- 에이전트 멤버는 계정이 아니라 이름표다 — 일감의 agentSession(V20) 이름 하나와 그 주인(그 에이전트를 돌리는 MCP 키의 주인)이다.
-- 자기 권한이 없다: 에이전트는 주인의 MCP 키로, 주인의 권한으로만 일한다(ADR-024 — 키는 모두 사람의 것, "에이전트 계정"은 두지 않는다).
-- 판정 · 범위는 하나도 바뀌지 않는다. 이 표는 누가 · 어떤 이름으로 이 프로젝트에서 일하는지 보이는 목록이다.
--
--   name           세션 이름 — 일감의 agent_session 과 같은 규칙(앞뒤 공백 없는 1~100자, 제어 문자 없음). 프로젝트 안에서 대소문자 없이 하나
--   owner_user_id  주인 — 그 에이전트가 쓰는 MCP 키의 주인. 옛 데이터에서 알 수 없으면 NULL
--   description    설명(선택, 200자까지)
--   active         false = 쓰지 않음(목록에 남고, 그 이름을 일감에 넣는 것은 막지 않는다 — MCP 결과에 AGENT_INACTIVE 경고)
--   source         AUTO = 일감에 그 이름이 처음 들어올 때 서버가 등록 · MANUAL = 프로젝트 관리자가 손으로 더함
-- 지우지 않는다(일감이 그 이름을 가리킨다) — 쓰지 않음으로 둔다. 이름 · 프로젝트는 바꾸지 않는다(일감의 agentSession 값과 잇는 열쇠다).

CREATE TABLE project_agent (
    id             uuid          PRIMARY KEY,
    project_id     uuid          NOT NULL REFERENCES project (id),
    name           varchar(100)  NOT NULL
        CONSTRAINT ck_project_agent_name CHECK (char_length(name) BETWEEN 1 AND 100 AND name = btrim(name)
            AND name !~ '[[:cntrl:]]'),
    owner_user_id  uuid          REFERENCES user_profile (id),
    description    varchar(200),
    active         boolean       NOT NULL DEFAULT true,
    source         varchar(10)   NOT NULL CONSTRAINT ck_project_agent_source CHECK (source IN ('AUTO', 'MANUAL')),
    created_at     timestamptz   NOT NULL,
    created_by     uuid          REFERENCES user_profile (id),      -- NULL = 이 이전(V29 채우기)
    updated_at     timestamptz   NOT NULL,
    updated_by     uuid          REFERENCES user_profile (id),
    row_version    integer       NOT NULL DEFAULT 0
);
-- 프로젝트 안에서 이름은 대소문자 없이 하나 — 자동 등록은 이 색인에 기대 INSERT … ON CONFLICT DO NOTHING 으로 한 번만 넣는다(동시에 와도)
CREATE UNIQUE INDEX ux_project_agent_name ON project_agent (project_id, lower(name));

-- 지우지 않는다. 이름 · 프로젝트도 바꾸지 않는다
CREATE FUNCTION project_agent_protect() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'project_agent 는 지울 수 없습니다 — 쓰지 않음(active = false)으로 두십시오';
    END IF;
    IF NEW.name <> OLD.name OR NEW.project_id <> OLD.project_id THEN
        RAISE EXCEPTION 'project_agent 의 이름 · 프로젝트는 바꿀 수 없습니다';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER tr_project_agent_protect BEFORE UPDATE OR DELETE ON project_agent
    FOR EACH ROW EXECUTE FUNCTION project_agent_protect();

-- ── 채우기 — 지금 일감(지우지 않은 것)에 쓰인 세션 이름을 프로젝트마다 하나씩 AUTO 로 ─────────────────────────────
-- 이름의 글자: 같은 이름(대소문자 없이)을 여러 글자로 썼으면 가장 많은 일감이 쓴 것, 같으면 가장 최근에 고친 일감의 것.
-- 주인:      1) 그 세션 이름을 가진 일감에 MCP 로 마지막으로 손댄 사람(이력 issue_journal 의 via = 'MCP') — 에이전트는 주인의 MCP 키로
--               일하므로 MCP 이력의 한 사람이 곧 키 주인이다. 같은 일감에 다른 사람의 에이전트가 댓글을 달았을 수도 있지만 마지막 손이 가장
--               최근의 주인이다
--            2) MCP 이력이 없으면(사람이 웹에서 세션을 적었다) 그 이름을 가진 일감 가운데 가장 최근에 만든 일감의 만든 사람
--            3) 그도 없으면 NULL(주인 모름 — 프로젝트 관리자가 웹에서 정한다)
-- 만든 때:    그 이름을 가진 일감 가운데 가장 먼저 만든 때(그 에이전트가 처음 보인 때). 만든 사람은 NULL(이 채우기)
WITH used AS (
    SELECT i.project_id, i.agent_session AS name, count(*) AS issues, max(i.updated_at) AS last_at, min(i.created_at) AS first_at
    FROM issue i
    WHERE i.deleted_at IS NULL AND i.agent_session IS NOT NULL
    GROUP BY i.project_id, i.agent_session
), spelling AS (
    SELECT DISTINCT ON (project_id, lower(name)) project_id, lower(name) AS lname, name
    FROM used
    ORDER BY project_id, lower(name), issues DESC, last_at DESC, name
), first_seen AS (
    SELECT project_id, lower(name) AS lname, min(first_at) AS first_at
    FROM used
    GROUP BY project_id, lower(name)
), by_mcp AS (
    SELECT DISTINCT ON (i.project_id, lower(i.agent_session)) i.project_id, lower(i.agent_session) AS lname, j.actor_id
    FROM issue i
    JOIN issue_journal j ON j.issue_id = i.id
    WHERE i.deleted_at IS NULL AND i.agent_session IS NOT NULL AND j.via = 'MCP' AND j.actor_id IS NOT NULL
    ORDER BY i.project_id, lower(i.agent_session), j.created_at DESC, j.id DESC
), by_creator AS (
    SELECT DISTINCT ON (i.project_id, lower(i.agent_session)) i.project_id, lower(i.agent_session) AS lname, i.created_by
    FROM issue i
    WHERE i.deleted_at IS NULL AND i.agent_session IS NOT NULL
    ORDER BY i.project_id, lower(i.agent_session), i.created_at DESC, i.id DESC
)
INSERT INTO project_agent (id, project_id, name, owner_user_id, description, active, source, created_at, created_by, updated_at,
                           updated_by, row_version)
SELECT gen_random_uuid(), s.project_id, s.name, coalesce(m.actor_id, c.created_by), NULL, true, 'AUTO', f.first_at, NULL, now(),
       NULL, 0
FROM spelling s
JOIN first_seen f ON f.project_id = s.project_id AND f.lname = s.lname
LEFT JOIN by_mcp m ON m.project_id = s.project_id AND m.lname = s.lname
LEFT JOIN by_creator c ON c.project_id = s.project_id AND c.lname = s.lname;
