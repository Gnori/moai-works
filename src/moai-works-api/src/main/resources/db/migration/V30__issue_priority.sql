-- 일감 우선순위 (2026-11-26 사용자 결정, moai-works 일감 #302)
--   "이슈 아이템에 우선순위를 추가하가 어떤 항목이 있으면 좋겠어? 일단, 보통, 긴급, 그리고 또 뭐?" → 넷으로 정함 → "그래 니가 제안하는대로 하자"
--   낮음 LOW · 보통 NORMAL(기본) · 높음 HIGH · 긴급 URGENT. 비우면 보통이다 — 이미 있는 일감은 모두 보통(기본값으로 채운다).
--   목록의 우선순위순(sort=priority)은 긴급 → 높음 → 보통 → 낮음, 같으면 번호 내림차순(최신 먼저).
-- 상수 기본값의 열 더하기는 표를 다시 쓰지 않는다(PostgreSQL 11+) — 행 트리거도 돌지 않는다.
ALTER TABLE issue
    ADD COLUMN priority varchar(10) NOT NULL DEFAULT 'NORMAL'
        CONSTRAINT issue_priority_check CHECK (priority IN ('LOW', 'NORMAL', 'HIGH', 'URGENT'));

-- 판에도 그때의 값을 남긴다 (issue_revision 은 추가만 하는 표 — 열 더하기는 추가만 트리거와 상관없다).
-- 이 이전의 판은 우선순위가 없던 때라 보통이다. 그때의 값을 그대로 남기는 열이라 제약은 두지 않는다(status 와 같다)
ALTER TABLE issue_revision ADD COLUMN priority varchar(10) NOT NULL DEFAULT 'NORMAL';

-- 색인은 두지 않는다 — 목록은 워크스페이스 · 프로젝트 · 상태로 먼저 좁히고(ix_issue_list), 우선순위 거르기 · 정렬은 그 안에서 한다.
-- 값이 넷뿐이라 우선순위만의 색인은 고르는 힘이 작다.
