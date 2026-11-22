-- 알림 — 사람에게 알려야 할 일을 그 사람의 알림함에 쌓는다 (design/07, 2026-11-22 사용자: "알림 시스템도 만들어야 해. 알림해야 할 게 뭔지 도출해 보고 만들어")
-- moai-wiki 의 V15 notification 과 같은 표다. 알림은 그 일을 한 트랜잭션 안에서 함께 쌓는다.
-- 글에는 제목 · 이름 · 번호 같은 값만 넣고 본문은 넣지 않는다 — 받은 사람이 눌러서 들어가면 그 화면이 지금 권한으로 다시 판정한다.
-- 같은 사람 · 같은 종류 · 같은 대상의 읽지 않은 알림이 있으면 새로 만들지 않고 그 알림에 묶는다(count).

CREATE TABLE notification (
    id            uuid          PRIMARY KEY,
    recipient_id  uuid          NOT NULL REFERENCES user_profile (id),
    workspace_id  uuid          REFERENCES workspace (id),          -- 워크스페이스에 속하지 않는 알림이면 NULL
    kind          varchar(40)   NOT NULL,                           -- NotificationKind 의 코드 (예: issue.assigned)
    subject_type  varchar(20)   NOT NULL,                           -- issue · project · mcp_key · join_request · workspace
    subject_id    uuid,
    params        jsonb         NOT NULL DEFAULT '{}',              -- 화면이 문장과 링크를 만드는 값. 본문은 넣지 않는다
    actor_id      uuid,                                             -- 그 일을 한 사람 (시스템이 한 일이면 NULL)
    actor_name    varchar(255),
    count         integer       NOT NULL DEFAULT 1,                 -- 묶인 수
    created_at    timestamptz   NOT NULL,                           -- 처음 생긴 때
    updated_at    timestamptz   NOT NULL,                           -- 마지막으로 묶인 때 (알림함의 순서)
    read_at       timestamptz
);
CREATE INDEX ix_notification_inbox ON notification (recipient_id, updated_at DESC, id);
CREATE INDEX ix_notification_unread ON notification (recipient_id, kind, subject_id) WHERE read_at IS NULL;

-- 알림 받기 설정 — 사람마다 종류별로 끈 것만 남긴다(없으면 받는다). 꼭 받아야 하는 종류(보안 · 내보내짐)는 끌 수 없다(서비스가 막는다)
CREATE TABLE notification_preference (
    user_id     uuid         NOT NULL REFERENCES user_profile (id),
    kind        varchar(40)  NOT NULL,
    enabled     boolean      NOT NULL,
    updated_at  timestamptz  NOT NULL,
    PRIMARY KEY (user_id, kind)
);
