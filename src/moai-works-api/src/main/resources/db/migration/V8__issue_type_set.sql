-- 일감 유형을 새기능 · 버그 · 테스트 · 기술조사로 (2026-11-22 사용자: "유형 : 새기능, 버그, 테스트, 기술조사")
-- 옛 유형(요구사항 · 작업)은 그 유형의 일감 · 판이 남아 있어 지우지 않는다 — "더 쓰지 않음"으로 두어 새로 고를 수 없게 한다.
ALTER TABLE issue_type ADD COLUMN active boolean NOT NULL DEFAULT true;

UPDATE issue_type SET name = '새기능', description = '새로 만들거나 늘리는 기능', position = 1 WHERE key = 'feature';
UPDATE issue_type SET name = '버그', description = '고쳐야 할 잘못된 동작', position = 2 WHERE key = 'bug';
INSERT INTO issue_type (key, name, description, position, active) VALUES
    ('test',     '테스트',   '동작을 확인 · 검증하는 일', 3, true),
    ('research', '기술조사', '기술을 알아보고 정리하는 일', 4, true);
UPDATE issue_type SET active = false, position = 91 WHERE key = 'requirement';
UPDATE issue_type SET active = false, position = 92 WHERE key = 'task';
