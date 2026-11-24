-- 일감 상태에 "보류" · "취소"를 더한다 (2026-11-24 사용자: "이슈 상태에 취소, 보류도 만들어" — moai-works 일감 #152)
--   차례: 등록 NEW · 진행 IN_PROGRESS · 테스트 TESTING · 보류 ON_HOLD(새로) · 완료 DONE · 종료 CLOSED · 취소 CANCELLED(새로)
--   보류는 열린 상태다 — 날짜를 채우지 않고, 기한 지남 · 아침 알림은 그대로 센다(기한은 여전히 뜻이 있다).
--   취소는 닫힌 상태다 — 종료일을 채우고 완료일은 비운다(취소는 끝낸 것이 아니다).
--   둘로 옮길 때는 까닭(같은 이력 줄의 댓글)이 있어야 한다 — 서비스 규칙(IssueService, 400 STATUS_REASON_REQUIRED).
--   이미 종료(CLOSED)인 일감은 옮기지 않는다 — 종료는 이제 "완료한 일을 사람이 확인해 닫음"이고, 하지 않을 일 · 중복은 앞으로 취소를 쓴다.
-- issue_revision.status 는 그때의 값을 그대로 남기는 열이라 제약이 없다.
ALTER TABLE issue DROP CONSTRAINT issue_status_check;
ALTER TABLE issue ADD CONSTRAINT issue_status_check
    CHECK (status IN ('NEW', 'IN_PROGRESS', 'TESTING', 'ON_HOLD', 'DONE', 'CLOSED', 'CANCELLED'));
