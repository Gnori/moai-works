-- 일감 상태에 관리자 확인 넷을 더한다 (2026-11-25 사용자: "이슈 상태에 확인요청, 확인완료 상태 추가하자 — 확인요청은 요구사항 관리자에게
--   확인해 달라고 요청하는 것이고, 확인완료는 요구사항 관리자가 확인 완료했으니 완료 처리해라" · "요구사항 확인요청, 요구사항 확인완료 2가지 건도
--   더 추가해" — moai-works 일감 #272)
--   차례: 등록 NEW · 요구사항 확인요청 REQUIREMENT_REVIEW(새로) · 요구사항 확인완료 REQUIREMENT_CONFIRMED(새로) · 진행 IN_PROGRESS ·
--         테스트 TESTING · 확인요청 REVIEW(새로) · 확인완료 CONFIRMED(새로) · 보류 ON_HOLD · 완료 DONE · 종료 CLOSED · 취소 CANCELLED
--   넷 모두 열린 상태다 — 날짜를 채우지 않고, 기한 지남 · 아침 알림은 그대로 센다. 확인 단계는 고를 수 있다(등록 → 진행 · 테스트 → 완료도 그대로 된다).
--   확인요청 둘로 옮길 때는 무엇을 확인할지(같은 이력 줄의 댓글)가 있어야 하고, 확인요청에서 확인완료가 아닌 곳으로 되돌릴 때(반려 · 거둠)도 까닭이
--   있어야 한다 — 서비스 규칙(IssueService, 400 STATUS_REASON_REQUIRED).
--   확인완료 둘로는 그 일감의 관리자(또는 project.manage)만 웹(REST)에서 옮긴다 — MCP 로는 키 주인이 관리자여도 안 된다(403 CONFIRM_BY_MANAGER_ONLY).
-- 'REQUIREMENT_CONFIRMED' 는 21자라 상태 열(varchar(20))을 넓힌다 — issue 와 그때의 값을 남기는 issue_revision 모두.
-- 길이만 늘리는 varchar 는 표를 다시 쓰지 않는다(색인 · 리비전 보호 트리거 그대로). issue_revision.status 는 제약이 없다.
ALTER TABLE issue ALTER COLUMN status TYPE varchar(30);
ALTER TABLE issue_revision ALTER COLUMN status TYPE varchar(30);
ALTER TABLE issue DROP CONSTRAINT issue_status_check;
ALTER TABLE issue ADD CONSTRAINT issue_status_check
    CHECK (status IN ('NEW', 'REQUIREMENT_REVIEW', 'REQUIREMENT_CONFIRMED', 'IN_PROGRESS', 'TESTING', 'REVIEW', 'CONFIRMED',
                      'ON_HOLD', 'DONE', 'CLOSED', 'CANCELLED'));
