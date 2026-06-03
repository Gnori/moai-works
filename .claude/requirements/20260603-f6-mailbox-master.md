# F6 — AI 메일박스 마스터

## 메타데이터
- **작성일**: 2026-06-03 · **작성자**: GnoRi · AI Assistant
- **상태**: 1단계 — 요구사항 분석 (L1 도메인 마스터) · **규모**: complex(마스터)
- **우선순위**: high (Wave 3) · **연관**: 의존 [F0-01 격리](./20260603-f0-01-tenant-isolation.md)·[F0-07 RBAC](./20260603-f0-07-rbac.md)·[F1-06 조직조회](./20260603-f1-06-org-query-view.md) · 연계 [F3 알림](./20260603-f3-notification-master.md)·[F4 파일](./20260603-f4-filedrive-master.md)·[F13 AI Agent](./20260603-f13-ai-agent-master.md)·[F7 지식](./20260603-f7-knowledge-master.md)·[F14 자동화](./20260603-f14-automation-master.md) · 인프라 **MOAI-DB/Redis**

## 1. 요구사항 분석
### 1.1 문제 정의
기업 구성원이 사용하는 **외부 이메일 계정(IMAP/SMTP·Microsoft Graph)** 을 그룹웨어에 연동,
조회·송신을 통합 인박스로 제공하고 **AI 가 메일을 요약·중요도 분류·답변필요 판정·답변초안**까지 수행.
받은 메일 폭주 속에서 "지금 봐야 할 것"과 "답해야 할 것"을 자동 선별하는 메일 비서.
### 1.2 목표
- 기업 메일 **계정 연동**(IMAP/SMTP·OAuth, Microsoft Graph) · 통합 인박스 **조회/송신/스레드**
- AI **요약·중요도 분류·답변필요 확인** · **답변초안 생성** · **예약(일/주간) 요약 다이제스트**
- 알림(중요 메일)=**F3 재사용** · 첨부 저장=**F4 재사용** · 답변초안/요약=**F13 Agent 연계**
### 1.3 제약
- 모든 엔티티 `tenant_id`(F0-01 RLS 격리) · 계정 자격증명/토큰 **암호화 저장**(F0-08 감사)
- 권한 F0-07(개인 메일박스는 소유자만, 공유 메일박스는 멤버) · 메일 본문/첨부 **테넌트 네임스페이스**

## 2. 기능 정의 — L3 분해
| L3 ID | spec | 규모 | 내용 |
|-------|------|------|------|
| **F6-01** | [메일 계정 연동](./20260603-f6-01-mail-account.md) | complex | IMAP/SMTP·Microsoft Graph·OAuth·자격증명 암호화·동기화 |
| **F6-02** | [메일 조회/송신](./20260603-f6-02-mail-view-send.md) | complex | 통합 인박스·스레드·검색·작성/송신·첨부(F4) |
| **F6-03** | [AI 요약·중요도 분류](./20260603-f6-03-ai-mail-triage.md) | standard | 본문 요약·중요/일반 분류·답변필요 판정(F13) |
| **F6-04** | [AI 답변 초안](./20260603-f6-04-ai-mail-draft.md) | standard | 톤·맥락 반영 답변초안 생성(F13·F7) |
| **F6-05** | [예약 요약 다이제스트](./20260603-f6-05-scheduled-mail-summary.md) | standard | 일/주간 미확인·중요 메일 요약 발송(F14·F3) |

## 3. 기능 설계 (토대)
### 3.1 데이터 모델
- `mail_accounts(id, tenant_id, owner_id, type: imap/graph, email, host, port, auth_enc, oauth_token_enc, sync_state, ...)` — 자격증명/토큰은 **암호화 컬럼**(앱 KMS)
- `mailboxes(id, tenant_id, account_id, kind: personal/shared, folder, ...)`
- `mail_messages(id, tenant_id, mailbox_id, thread_id, message_id, from, to, subject, body_ref, has_attach, received_at, flags, ...)`
- `mail_ai_meta(message_id, tenant_id, summary, importance: high/normal, needs_reply: bool, draft_ref, model_ver, status, ...)` — AI 산출물 분리 저장
- 첨부 = **F4 files**(storage_key 참조) · 본문 대용량은 F4/오브젝트 ref
### 3.2 통합점
- **F13 AI Agent**: F6-03 요약/분류, F6-04 답변초안 호출 (미완 시 §3.3 placeholder)
- **F7 지식**: 답변초안 시 사내 지식/이전 스레드 맥락 주입 (미완 시 빈 컨텍스트 fallback)
- **F3 알림**: 중요 메일 수신·다이제스트 발송 알림 (실시간 = F3-02 WS+Redis)
- **F14 자동화**: F6-05 예약 요약 스케줄러 트리거 (미완 시 단순 cron fallback)
- **F4 파일**: 첨부 업/다운·저장 재사용 · **F1-06 조직**: 발신자 사내 매핑
### 3.3 AI 미완 대비 (placeholder/fallback)
- **F13 미완**: F6-03 요약=본문 앞단 truncate, 중요도=발신자 화이트리스트/키워드 룰, 답변필요=`?`/요청 키워드 휴리스틱 · F6-04 초안=빈 템플릿. 모든 AI 산출물 `status=pending_ai`로 마킹, F13 완성 후 재처리 큐로 승격.
- **F7 미완**: 답변초안 컨텍스트 비움(메일 스레드만) · **F14 미완**: F6-05 = 앱 내 단순 스케줄러.

## 4. 개발 진행 (L3)
- [ ] F6-01 계정 연동 → [ ] F6-02 조회/송신 → [ ] F6-03 AI 요약/분류 → [ ] F6-04 AI 답변초안 → [ ] F6-05 예약 요약

## 9. 완료 기준 (마스터)
- [ ] IMAP/SMTP·Graph 계정 연동·동기화·통합 인박스 조회/송신·스레드·검색 동작
- [ ] AI 요약·중요도 분류·답변필요 판정·답변초안 (F13 연계 또는 fallback) 동작
- [ ] 예약 요약 다이제스트(F14·F3) 발송 · 첨부 F4 저장
- [ ] 격리(tenant_id RLS)·자격증명 암호화·권한(F0-07)·감사(F0-08) 충족
