-- 일감 상태에 "테스트"를 더한다 (2026-11-22 사용자: "상태 : 등록, 진행, 테스트, 완료, 종료")
--   등록 = NEW(코드값은 그대로, 화면 이름만 "신규" → "등록") · 진행 = IN_PROGRESS · 테스트 = TESTING(새로) · 완료 = DONE · 종료 = CLOSED
--   테스트는 열린 상태다 — 날짜를 채우지 않고, 완료 · 종료에서 테스트로 되돌리면 완료일 · 종료일을 비운다(진행과 같다).
-- issue_revision.status 는 그때의 값을 그대로 남기는 열이라 제약이 없다.
ALTER TABLE issue DROP CONSTRAINT issue_status_check;
ALTER TABLE issue ADD CONSTRAINT issue_status_check
    CHECK (status IN ('NEW', 'IN_PROGRESS', 'TESTING', 'DONE', 'CLOSED'));
