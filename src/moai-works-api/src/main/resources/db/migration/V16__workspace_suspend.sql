-- 팀 워크스페이스 사용 중지 · 다시 사용 · 삭제 — 시스템 관리자 (FR-WSP-019 · FR-WSP-020, WBS 1.8)
-- 2026-11-22 사용자: "팀 워크스페이스는 시스템 어드민이 사용 못 하게 막을 수도 있어" · "다만 삭제는 사용 중지 후에만 가능해"
--
-- 상태는 V1 의 status 열(ACTIVE · SUSPENDED · DELETED)을 그대로 쓴다. 여기서는 언제 · 왜를 더한다.
--   suspended_at · suspended_reason — 사용 중지한 때와 이유. 이유는 시스템 관리자가 적고 시스템 관리 화면 · 감사에만 보인다.
--                                    다시 사용하게 하면 둘 다 비운다(이유는 감사 admin.workspace.suspend 에 남아 있다)
--   deleted_at                     — 지운 때. 지운 워크스페이스의 행은 남긴다(주소를 다시 쓰지 않고, 감사 · 일감 이력이 가리키는 곳이
--                                    사라지지 않게). 멤버십 · 역할 사본 · 초대 · 가입 신청 · 템플릿 · 알림은 지우고, 첨부 파일은
--                                    객체 저장소에서 지운다 — 무엇을 지우고 남기는지는 WorkspaceAdminService.delete
ALTER TABLE workspace
    ADD COLUMN suspended_at      timestamptz,
    ADD COLUMN suspended_reason  varchar(500),
    ADD COLUMN deleted_at        timestamptz;
