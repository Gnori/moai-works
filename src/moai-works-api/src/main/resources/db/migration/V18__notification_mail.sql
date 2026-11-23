-- 알림 메일 (FR-NOTI-006, design/07 §8) — 메일을 켠 종류의 알림이 생기면 메일을 한 통 보낸다.
-- SMTP 가 정해질 때까지는 로그 모드(moai-works.mail.mode=log)다.

-- 알림 받기 설정에 메일 열 — NULL 이면 그 종류의 기본값(NotificationKind.emailDefault: 아침 알림 셋만 켬)
ALTER TABLE notification_preference ADD COLUMN email boolean;

-- 보낼 통(outbox) — 알림을 쌓는 트랜잭션 안에서 함께 넣는다(일이 되돌려지면 메일도 없다).
-- 정해진 주기의 보내는 일(NotificationMailSender)이 한 줄씩 잠그고(FOR UPDATE SKIP LOCKED) 보낸 뒤 같은 트랜잭션에서 상태를 적는다.
-- 알림 하나에 메일 하나(notification_id UNIQUE) — 읽지 않은 알림에 묶인 다음 일은 메일을 더 만들지 않는다.
-- 주소 · 제목 · 본문은 두지 않는다 — 보낼 때 받는 사람의 권한을 다시 판정하고 그때의 값으로 만든다.
CREATE TABLE notification_mail (
    id               uuid          PRIMARY KEY,                       -- 메일의 Message-ID 에도 쓴다(다시 보내도 같은 메일)
    notification_id  uuid          NOT NULL UNIQUE REFERENCES notification (id),
    recipient_id     uuid          NOT NULL REFERENCES user_profile (id),
    kind             varchar(40)   NOT NULL,
    status           varchar(10)   NOT NULL,                          -- PENDING(보낼 것) · SENT · FAILED(다시 보내기를 다 씀) · SKIPPED(보낼 때 판정에서 빠짐)
    attempts         integer       NOT NULL DEFAULT 0,
    next_attempt_at  timestamptz   NOT NULL,
    last_error       varchar(300),                                    -- 실패 · 건너뜀 까닭(코드 · 예외 이름). 주소 · 본문 · 비밀값은 넣지 않는다
    created_at       timestamptz   NOT NULL,
    updated_at       timestamptz   NOT NULL,
    sent_at          timestamptz,
    CONSTRAINT ck_notification_mail_status CHECK (status IN ('PENDING', 'SENT', 'FAILED', 'SKIPPED'))
);
CREATE INDEX ix_notification_mail_due ON notification_mail (next_attempt_at) WHERE status = 'PENDING';
