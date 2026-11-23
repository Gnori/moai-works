[홈](../README.md) › [설계](00-key-decisions.md) › 알림

# 알림 — 알림함 · 무엇을 누구에게 · 아침 알림 · 받기 설정 · 메일

사람에게 알려야 할 일을 그 사람의 알림함에 쌓는다. 사용자 지시(2026-11-22): "알림 시스템도 만들어야 해. 알림해야 할 게 뭔지 도출해 보고 만들어". 알림함의 표 · API · 규칙은 moai-wiki 와 같다(moai-wiki V15 `notification` · `notification/*` — 같은 날 moai-wiki 가 먼저 만들었다). moai-works 는 알릴 일(일감 · 작업 시간 · 프로젝트 역할 · MCP 키)과 아침 알림 · 받기 설정 · 메일(2026-11-23)을 더했다([ADR-026](00-key-decisions.md#adr-026)).

> 요구사항: [FR-NOTI-001](../requirements/07-notification.md#fr-noti-001) 알림함 · [002](../requirements/07-notification.md#fr-noti-002) 무엇을 누구에게 · [003](../requirements/07-notification.md#fr-noti-003) 아침 알림 · [004](../requirements/07-notification.md#fr-noti-004) 받기 설정 · [005](../requirements/07-notification.md#fr-noti-005) 화면 · [006](../requirements/07-notification.md#fr-noti-006) 메일

**바로가기:** [원칙](#nt-principles) · [종류](#nt-kinds) · [아침 알림](#nt-reminders) · [알림함 API](#nt-inbox) · [받기 설정](#nt-preferences) · [화면](#nt-screen) · [moai-wiki 와 다른 것](#nt-diff) · [메일](#nt-mail) · [나중](#nt-later)

---

<a id="nt-principles"></a>
## 1. 원칙

| 원칙 | 내용 |
|---|---|
| 내 것만 | 알림 API 는 `/v1/me/notifications/**`(`@UserApi`) 뿐이다. 받는 사람 = 로그인한 사람. 남의 알림 id 는 404 |
| 본문 없음 | `params` 에는 화면이 문장 · 링크를 만드는 값(프로젝트 key · 이름 · 일감 번호 · 제목 · 수 · 경로)만 넣는다. 설명 · 댓글 본문은 넣지 않는다. 눌러서 들어간 화면이 지금 권한으로 다시 판정한다 — 권한을 잃은 사람에게는 알림의 제목만 남는다 |
| 자기 일은 빼기 | `NotificationService.notify` 가 받는 사람 = 한 사람이면 쌓지 않는다 |
| 같은 트랜잭션 | 그 일을 하는 서비스가 자기 트랜잭션 안에서 부른다 — 일이 되돌려지면 알림도 없다. 알림을 쌓지 못하면 그 일도 실패한다 |
| 묶기 | 같은 사람 · 같은 종류 · 같은 대상(`subject_id`)의 읽지 않은 알림이 있으면 `count` 를 올리고 마지막 값 · 시각 · 한 사람으로 바꾼다 |
| 경로 | MCP 로 한 일도 같이 알린다(한 사람 = 키 주인). `params.via` 가 `WEB` · `MCP` · `SYSTEM`(아침 알림) |
| 시스템 관리자 | 알림함이 없다(`@UserApi` 기본 403) — 워크스페이스가 없어서 받을 일이 없다 |

<a id="nt-kinds"></a>
## 2. 종류 — 무엇을 누구에게 (2026-11-22 도출)

기준: **그 사람이 다음에 무엇을 해야 하거나, 그 사람의 일 · 권한이 바뀌었는가.** 받는 사람은 일에 묶인 사람(관리자 · 담당자 · 대화에 낀 사람 · 키 주인)과 처리할 권한이 있는 사람이다. 지켜보는 사람(관찰자)은 아직 없다(WBS 5.2 의 관찰자).

| 묶음 | 코드 | 언제 | 받는 사람 | 대상 | 끌 수 |
|---|---|---|---|---|---|
| 워크스페이스 | `join.requested` | 가입 신청(승인 가입) | `member.request.review` 가 있는 활성 멤버 | 신청 | 있음 |
| | `join.approved` · `join.rejected` | 사람이 신청을 처리 | 신청한 사람 | 워크스페이스 | 있음 |
| | `member.roles_changed` | 워크스페이스 역할 바뀜 | 그 멤버 | 워크스페이스 | 있음 |
| | `member.removed` | 내보내짐(스스로 나간 것은 빼기) | 그 사람 | 워크스페이스 | **없음** |
| 프로젝트 | `project.roles_changed` | 프로젝트 역할 받음 · 빠짐 | 그 멤버 | 프로젝트 | 있음 |
| 일감 | `issue.assigned` | 담당자로 정함(만들 때 포함) | 새 담당자 | 일감 | 있음 |
| | `issue.unassigned` | 담당자에서 빠짐 | 옛 담당자 | 일감 | 있음 |
| | `issue.manager_assigned` | 관리자로 정함(만들 때 포함) | 새 관리자 | 일감 | 있음 |
| | `issue.status_changed` | 상태 바뀜(`from` · `to`) | 관리자 · 담당자 − 같은 변경으로 담당 · 관리 알림을 받은 사람 | 일감 | 있음 |
| | `issue.updated` | 제목 · 설명 · 날짜 · 유형 · 상위 · 예상 공수 바뀜(`fields`) | 위와 같다 | 일감 | 있음 |
| | `issue.commented` | 댓글(고치며 단 댓글 포함) | 관리자 · 담당자 · 앞서 댓글을 단 사람 | 일감 | 있음 |
| | `issue.child_added` | 하위 일감이 생김 · 옮겨 옴(`childNumber`) | 상위의 관리자 · 담당자 | 상위 일감 | 있음 |
| | `issue.linked` | 연결(`otherNumber` · `linkType` · `direction`) | 그 일감의 관리자 · 담당자 — 양쪽 일감 모두 | 일감 | 있음 |
| | `issue.deleted` | 지워짐 | 관리자 · 담당자 | 일감 | 있음 |
| | `issue.due_soon` | 아침 — 기한이 내일(§3) | 담당자 · 관리자 | 일감 | 있음 |
| | `issue.overdue` | 아침 — 기한이 지남(§3) | 담당자 · 관리자 | 일감 | 있음 |
| 작업 시간 | `issue.work_logged` | 작업 시간 기록(`hours` · `workDate` · `spent` · `estimated`) | 관리자 | 일감 | 있음 |
| | `issue.over_estimate` | 쓴 시간이 예상 공수를 넘는 순간 한 번 | 관리자 · 담당자 | 일감 | 있음 |
| MCP 키 | `mcp_key.expired` | 다른 사람이 내 키를 만료시킴(`note`). **2026-11-23 부터 생기지 않는다** — 키는 주인만 만료시킨다([ADR-029](00-key-decisions.md#adr-029)). 옛 알림 · 설정 표시용으로 종류는 남긴다 | 키 주인 | 키 | **없음** |
| | `mcp_key.expiring` | 아침 — 7일 안에 만료(§3). 워크스페이스 없는 알림(2026-11-23 — 키는 사용자의 것) | 키 주인 | 키 | **없음** |

**알리지 않기로 한 것:** 연결 풀기 · 작업 시간 지우기(되돌림이라 알림 가치가 낮다), MCP 키 발급(주인 본인이 한 일 — 관리자는 멤버들의 키 화면에서 본다), 감사 기록 그 자체(감사 화면이 있다), 일감 보기(읽기).

<a id="nt-reminders"></a>
## 3. 아침 알림 (`NotificationReminders`)

매일 09:00(Asia/Seoul, `@Scheduled`) 한 번. 트랜잭션 안에서 `pg_try_advisory_xact_lock(hashtext('moai-works:notification-reminders'))` 을 잡은 인스턴스만 돈다.

| 종류 | 고르는 조건 | 두 번 보내지 않기 |
|---|---|---|
| `issue.due_soon` | 열린 일감(`NEW` · `IN_PROGRESS`) ∧ `due_date` = 내일 ∧ 프로젝트 `ACTIVE` ∧ 워크스페이스 `ACTIVE` ∧ 지우지 않음 | 같은 사람 · 일감 · `params.dueDate` 의 알림이 이미 있으면 건너뜀 |
| `issue.overdue` | 위와 같고 `due_date` 가 지난 7일 안(어제 ~ 7일 전) — 하루 서버가 멈춰도 놓치지 않게 | 같은 사람 · 일감 · `params.dueDate` |
| `mcp_key.expiring` | 일찍 만료시키지 않은 키 ∧ 지금 < `expires_at` ≤ 7일 뒤(2026-11-23 부터 워크스페이스 조건은 없다 — 키가 워크스페이스에 묶이지 않는다, 알림의 `workspace_id` 도 비어 있다) | 같은 사람 · 키 · `params.expiresAt` |

한 사람은 없다(`actor_id` NULL). 기한을 바꾸면(`dueDate` 가 달라지면) 다시 알린다.

<a id="nt-inbox"></a>
## 4. 표 · 알림함 API

```sql
-- V6 (moai-wiki V15 와 같은 표 + notification_preference)
notification (id, recipient_id → user_profile, workspace_id → workspace, kind, subject_type, subject_id,
              params jsonb, actor_id, actor_name, count, created_at, updated_at, read_at)
  ix_notification_inbox  (recipient_id, updated_at DESC, id)
  ix_notification_unread (recipient_id, kind, subject_id) WHERE read_at IS NULL
notification_preference (user_id, kind, enabled, updated_at, PK (user_id, kind))   -- 끈 것만 남긴다
-- V18 (2026-11-23, §8 메일)
notification_preference.email boolean                                              -- NULL = 종류의 기본값
notification_mail (id, notification_id UNIQUE → notification, recipient_id → user_profile, kind, status, attempts,
                   next_attempt_at, last_error, created_at, updated_at, sent_at)
  ix_notification_mail_due (next_attempt_at) WHERE status = 'PENDING'
```

| 일 | 메서드 · 경로 | 비고 |
|---|---|---|
| 알림함 | `GET /v1/me/notifications?unread=&before=&limit=` | 새 것부터 최대 30. `next` = `"<마지막 updated_at>|<마지막 id>"` — `(updated_at, id) < (…)` 로 자르므로 같은 밀리초의 줄을 건너뛰지 않는다(2026-11-22, moai-wiki 가 짚은 "시간만으로 줄 세우기" 점검에서 찾음). 시각만 넘겨도 받는다(moai-wiki 와 맞추려고) |
| 읽지 않은 수 | `GET /v1/me/notifications/unread-count` | 화면이 1분마다 |
| 하나 읽음 | `POST /v1/me/notifications/{id}/read` | 남의 것 404 `NOTIFICATION_NOT_FOUND`. 응답은 읽지 않은 수 |
| 모두 읽음 | `POST /v1/me/notifications/read-all` | 응답은 바꾼 수 |
| 받기 설정 | `GET /v1/me/notifications/preferences` · `PUT …/preferences/{kind}` `{enabled?, email?}` | §5 · 준 쪽만 바뀐다, 둘 다 없으면 400 `INVALID_PREFERENCE` |

모두 `@UserApi`(시스템 관리자 403). 알림을 지우는 API 는 없다(읽음으로만). 오래된 읽은 알림을 지우는 정리는 [나중](#nt-later)이다.

<a id="nt-preferences"></a>
## 5. 받기 설정

- 기본은 모두 받는다. `notification_preference` 에 `enabled = false` 인 종류만 남긴다. `notify` 가 쌓기 전에 본다.
- 꼭 받아야 하는 종류(`NotificationKind.mandatory`): `member.removed` · `mcp_key.expired` · `mcp_key.expiring` — 접근이 사라지거나 보안에 닿는 일. 끄면 400 `NOTIFICATION_MANDATORY`.
- 응답은 모든 종류의 `{kind, group, enabled, mandatory, email}`. 화면은 `group`(WORKSPACE · PROJECT · ISSUE · WORK · SECURITY)으로 묶어 보인다.
- **메일 열**(2026-11-23, §8): `email` 은 그 종류를 메일로도 받기로 했는가다. 고른 적이 없으면(`NULL`) 종류의 기본값(`NotificationKind.emailDefault`) — **아침 알림 셋(`issue.due_soon` · `issue.overdue` · `mcp_key.expiring`)만 켜고 나머지는 끈다.** 하루 한 번 오는 것만 기본으로 메일까지 보내고, 일하는 동안 자주 생기는 것은 사람이 골라 켠다. 메일은 알림함을 끌 수 없는 종류도 끌 수 있다. 알림함을 끈 종류는 알림이 생기지 않으므로 메일도 가지 않는다(메일 설정은 남는다).
- 개인 설정(왼쪽 메뉴의 "개인 설정")의 "알림 설정" 화면에서 고친다 — 종류마다 알림함 · 메일 스위치 두 열.

<a id="nt-screen"></a>
## 6. 화면

| 곳 | 내용 |
|---|---|
| 왼쪽 메뉴 | 계정 영역 위에 종 모양 + 읽지 않은 수(1분마다 · 창으로 돌아올 때 다시 읽기). 누르면 알림함 |
| 알림함 `/notifications` | 전체 · 읽지 않음 탭, 모두 읽음, 더 보기. 한 줄 = 문장(누가 · 무엇을 · `#번호 「제목」`, MCP 면 "(MCP)") · 워크스페이스 · 묶인 수 · 시각(밀리초). 누르면 읽음 + 그 곳으로 |
| 갈 곳 | 일감 종류 → 일감 보기, `join.requested` → 가입 신청, `join.approved` · 역할 → 워크스페이스 홈, `project.roles_changed` → 프로젝트, `mcp_key.*` → 내 MCP 키, `member.removed` · `join.rejected` · `issue.deleted` → 없음(읽음만) |
| 개인 설정 › 알림 설정 `?settings=notifications` | 묶음별로 종류마다 알림함 · 메일 두 열(머리 줄에 열 이름, 스위치마다 이름 — 메일은 "… — 메일"). 알림함을 끌 수 없는 것은 잠금 표시, 알림함을 끈 종류의 메일은 꺼 두고 까닭을 보인다 |
| 문장 | `features/notifications/describe.ts` — 종류 · 값 → 문장 · 링크(moai-wiki 와 같은 자리). 모르는 종류는 일반 문장 |

<a id="nt-diff"></a>
## 7. moai-wiki 와 다른 것

| 항목 | moai-wiki | moai-works |
|---|---|---|
| 종류 | 워크스페이스 5 · 페이지 7 · 자료실 2 | 워크스페이스 5(같다) · 프로젝트 1 · 일감 11 · 작업 시간 2 · MCP 키 2 |
| 받기 설정 | 없음 | 종류별 끄기(`notification_preference`) · 끌 수 없는 종류 |
| 아침 알림 | 없음 | 기한 임박 · 기한 지남 · MCP 키 만료 예정 |
| 메일 | 없음 | 종류별 메일 켬 · 끔, 보낼 통 + 정해진 주기의 보내기(§8) |
| 커서 | `updated_at` 만 | `updated_at` + id(같은 밀리초 건너뛰기 막기). moai-wiki 에 알렸다 |
| 받는 사람 | 페이지 소유자 · 작업본 주인 | 일감 관리자 · 담당자 · 댓글 단 사람 · 상위의 관리자 · 담당자 · 키 주인 |

<a id="nt-mail"></a>
## 8. 메일 (2026-11-23 — [FR-NOTI-006](../requirements/07-notification.md#fr-noti-006), 일감 #10)

메일을 켠 종류(§5)의 알림이 **새로 생기면 메일 한 통**을 보낸다. SMTP 서버는 아직 정하지 않았다(WBS 결정 [D-06](../wbs/08-decisions.md#d-06)) — 그때까지 로그 모드다.

| 항목 | 내용 |
|---|---|
| 보낼 통 | `NotificationService.notify` 가 알림을 **새로 넣을 때**(묶이지 않을 때) 같은 트랜잭션에서 `notification_mail` 에 `PENDING` 한 줄을 넣는다 — 일이 되돌려지면 메일도 없다. 알림 하나에 메일 하나(`notification_id UNIQUE`): 읽지 않은 알림에 묶인 다음 일은 메일을 더 만들지 않는다(같은 일감을 열 번 고쳐도 읽기 전까지 메일 한 통). 통에는 주소 · 제목 · 본문을 두지 않는다 |
| 보내기 | `NotificationMailSender` — `moai-works.mail.outbox-cron`(기본 30초마다)에 돈다. 한 번에 50줄까지, **한 줄 = 한 트랜잭션**: `PENDING` ∧ 때가 된 줄 하나를 `FOR UPDATE OF m SKIP LOCKED` 로 잠그고 → 다시 판정 → 메일 만들기 → 보내기 → `SENT` 를 적고 끝낸다. 여러 인스턴스 · 스레드가 함께 돌아도 한 줄은 한 곳만 집는다 |
| 한 번만 | 보내기 전에 죽으면 줄은 `PENDING` 그대로라 다음에 보낸다(잃지 않음). 보낸 줄은 `SENT` 라 다시 집지 않는다. 메일 서버가 받은 뒤 `SENT` 를 적기 전의 아주 짧은 틈에 죽으면 한 번 더 갈 수 있다 — SMTP 와 DB 를 한 번에 묶을 방법은 없어서, Message-ID 를 줄 id(`<id>.notification@<보내는 주소의 도메인>`)로 정해 받는 쪽이 같은 메일로 보게 했다 |
| 다시 보내기 | 보내지 못하면(메일 서버 오류 · 예외) `attempts` + 1, 1분 · 5분 · 30분 · 2시간 뒤에 다시. 5번째에도 못 하면 `FAILED` 로 두고 더 집지 않는다. `last_error` 에는 예외 이름과 짧은 글만(주소는 `***` 로 가린다) |
| 보낼 때 다시 판정 | 알림함 · REST 와 같은 판정(`WorkspaceAccessService` · `ProjectAccessService`)을 보낼 때 다시 한다. 아니면 `SKIPPED` + 까닭: 계정이 활성이 아님(`ACCOUNT_INACTIVE`) · 시스템 관리자(`SYSTEM_ADMIN`) · 지금은 그 종류의 메일이나 알림함을 끔(`EMAIL_OFF`) · 워크스페이스가 활성이 아니거나 활성 멤버가 아님(`NOT_MEMBER`) · 대상을 볼 수 없음(`NO_ACCESS` — 가입 신청은 `member.request.review`, 프로젝트 역할은 그 프로젝트의 독자, 일감은 그 프로젝트의 독자 ∧ `issue.read`, MCP 키는 지금도 그 사람의 키) · 대상이 없어짐(`GONE` — 지운 일감, `issue.deleted` 만 빼고) · 만료 예정이던 키가 이미 끝남(`OBSOLETE`). `member.removed` · `join.rejected` 는 멤버가 아니게 된 일 자체를 알리므로 계정만 본다 |
| 글 | 글자만(plain text, `NotificationMailContent`). 제목 `[moai-works] <무슨 일인지>`(한 줄 · 200자까지 · 제어 문자는 띄어쓰기). 본문은 무슨 일인지 · 워크스페이스 · 그 화면 링크(`moai-works.web.base-url` + 화면 경로 — 일감 보기 · 프로젝트 · 가입 신청 · 워크스페이스 홈 · 내 MCP 키 `?settings=mcp-keys`, 지운 일감 · 내보내짐 · 가입 거절은 링크 없음) · 받는 까닭 · 알림 설정 링크. **일감 설명 · 댓글 본문은 싣지 않는다** — 링크를 열면 화면이 지금 권한으로 다시 판정한다. 값은 보낼 때 읽은 지금 값(일감 번호 · 제목 · 프로젝트 · 워크스페이스 이름)과 알림의 값이다 |
| 언어 | 받는 사람의 `user_profile.locale` 이 `en` 이면 영어, 아니면 한국어. 화면 언어는 지금 브라우저에만 있어 이 열을 바꾸는 곳이 아직 없다 — 사실상 한국어 |
| 받는 주소 | 계정 사본의 이메일(`user_profile.email`, Keycloak 계정 이메일) |
| 로그 모드 (`log`, 기본) | 보내지 않고 `[메일 · 로그 모드] 알림 메일 받는 사람=a***@example.com 종류=issue.assigned 제목=[moai-works] …` 한 줄만 남긴다 — 주소는 가리고 본문은 남기지 않는다. 계정 메일(인증 코드 · 초대)의 로그 모드는 로컬 개발용으로 본문까지 남기던 그대로다 |
| SMTP 모드 (`smtp`) | `Mailer` 가 `moai-works.mail.smtp.*`(env `MOAI_WORKS_SMTP_HOST` · `_PORT` · `_USERNAME` · `_PASSWORD` · `_TLS`)로 Spring `JavaMailSenderImpl` 을 직접 만든다. `spring.mail.*` 은 쓰지 않는다 — 자동 설정이 켜지면 메일 건강 검사가 api 건강 상태에 붙어 메일 서버 장애가 api 장애로 보인다. STARTTLS 필수(587, 기본) 또는 SSL(465), 서버 인증서 확인, 연결 · 읽기 · 쓰기 10초 제한. 평문 SMTP 는 없다. 서버가 비었거나 모르는 모드면 기동하지 않는다. 아직 어디서도 켜지 않았다 |

**하지 않은 것:** 보낸 지 오래된 통의 줄 정리, 메일 묶어 보내기(하루 요약), 수신 거부 링크(지금은 알림 설정 링크), HTML 메일.

<a id="nt-later"></a>
## 9. 나중

- SMTP 서버 · 보내는 주소를 정하면(WBS 결정 [D-06](../wbs/08-decisions.md#d-06)) `MOAI_WORKS_MAIL_MODE=smtp` 와 서버 값을 `.env` 에 넣는다(§8). 보낸 지 90일이 지난 `notification_mail` 줄 정리.
- 관찰자(지켜보기) — 일감을 지켜보는 사람에게도 알린다(WBS 5.2).
- 멘션(`@사용자`) — 댓글에서 부른 사람에게.
- 읽은 지 90일이 지난 알림 정리.
- 실시간(SSE) — 지금은 1분 간격 읽기.

---

**관련 문서:** [알림 요구사항](../requirements/07-notification.md) · [일감](05-issue.md) · [MCP](06-mcp.md) · [워크스페이스 · 권한](03-workspace-identity.md) · [데이터 모델](02-data-model.md) · [핵심 설계 결정](00-key-decisions.md)

[← 이전: MCP](06-mcp.md) | [다음: 모듈 한눈에 →](../modules/00-overview.md)
