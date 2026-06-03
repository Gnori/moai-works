# F6-01 — 메일 계정 연동

## 메타데이터
- **작성일**: 2026-06-03 · **작성자**: GnoRi · AI Assistant
- **상태**: 1단계 · **규모**: standard · **레벨**: L3 · **우선순위**: high (Wave 3)
- **상위**: [F6 마스터](./20260603-f6-mailbox-master.md) · **의존**: [F0-01 격리](./20260603-f0-01-tenant-isolation.md)·[F0-07 RBAC](./20260603-f0-07-rbac.md)·[F0-08 감사](./20260603-f0-08-audit-log.md)·[F1-06 조직](./20260603-f1-06-org-query-view.md)

## 1. 요구사항 분석
### 1.1 문제 정의
메일함(F6) 기능을 쓰려면 **기업 메일 서버와 사용자 계정**을 안전하게 연동해야 한다.
IMAP/SMTP 또는 MS Graph/OAuth 방식을 지원하고, **시크릿을 암호화 보관**하며 주기적으로
**동기화**한다. 자격증명 노출·테넌트 간 메일 누출이 가장 큰 위험이다.
### 1.2 목표
- 연동 방식: **IMAP/SMTP**(비번·앱 비번) · **MS Graph/OAuth2**(Microsoft 365) · (확장) Gmail OAuth
- 계정 등록: 테넌트별 메일 서버 프로파일 + 사용자별 계정 매핑
- **시크릿 보관**: 암호·토큰 암호화(KMS/Envelope) · 평문 미저장 · 토큰 자동 갱신
- 동기화: 폴더/메시지 헤더 증분 동기화 · 연결 상태 모니터링 · 격리(tenant_id)

## 2. 기능 정의
| ID | 기능 | 우선순위 |
|----|------|---------|
| F-001 | 테넌트 메일 서버 프로파일 등록(IMAP/SMTP host·port·TLS / MS Graph tenant) | Must |
| F-002 | 사용자 계정 연동(IMAP 자격증명 / OAuth 인증 플로우) | Must |
| F-003 | 시크릿 암호화 보관·토큰 자동 갱신 | Must |
| F-004 | 연결 테스트(verify) · 상태(active/error/expired) 표시 | Must |
| F-005 | 증분 동기화(폴더 목록·UID 기반 헤더) · 스케줄 | Should |
| F-006 | 계정 해제(연동 삭제 시 토큰 폐기·로컬 캐시 정리) | Should |

## 3. 기능 설계
### 3.1 데이터 모델
- `mail_server_profile(id, tenant_id, type=imap_smtp/ms_graph/gmail, imap_host, imap_port, smtp_host, smtp_port, tls, ms_tenant_id, oauth_client_ref, created_by)`
- `mail_account(id, tenant_id, user_id, profile_id, address, auth_type=password/oauth, status, last_sync_at, sync_cursor)`
- `mail_secret(account_id, tenant_id, enc_payload, kms_key_id, token_expires_at)` — 암호/refresh_token Envelope 암호화, 평문 미보관
- `mail_sync_state(account_id, folder, uid_validity, last_uid, updated_at)`
모든 테이블 **tenant_id RLS**(F0-01). 프로파일 등록·해제는 F0-07 RBAC(`mail.admin`), 본인 계정 연동은 `mail.account.connect`.
### 3.2 흐름 / API
- `POST /mail/profiles` 서버 프로파일 등록(관리자) · `GET /mail/profiles`
- `POST /mail/accounts` 계정 연동 시작 — IMAP: 자격증명 검증 후 저장 / OAuth: authorize URL 반환
- `GET /mail/accounts/oauth/callback` OAuth 콜백 → 토큰 교환·암호화 저장
- `POST /mail/accounts/{id}/verify` 연결 테스트 · `POST /mail/accounts/{id}/sync` 수동 동기화
- `DELETE /mail/accounts/{id}` 해제(토큰 revoke·시크릿/캐시 삭제)
- **동기화 워커**: 스케줄러가 active 계정 폴링(IMAP IDLE 또는 주기 폴링 / Graph delta) → 헤더 적재는 [F6-02 조회/송신](./20260603-f6-02-mail-view-send.md)이 소비
- 연동/해제/실패 이벤트 → **F0-08 감사 로그**. 동기화 오류 알림은 [F3 알림](./20260603-f3-notification-master.md)(실시간 [F3-02](./20260603-f3-02-realtime-delivery.md))로 사용자 통지
### 3.3 AI 연계(placeholder)
본 spec은 연동/동기화만 담당. 요약·분류([F6-03](./20260603-f6-03-ai-mail-triage.md)) 등 AI 기능은
**[F13 AI Agent](./20260603-f13-ai-agent-master.md)·[F8 문서](./20260603-f8-ai-document-master.md) 미완 시 동기화된 메타만 노출(fallback)**, 완성 후 연계.

## 4. 개발 체크리스트
- [ ] F-001 프로파일 등록 · [ ] F-002 계정 연동(IMAP/OAuth) · [ ] F-003 시크릿 암호화·토큰 갱신
- [ ] F-004 연결 테스트·상태 · [ ] F-005 증분 동기화·스케줄 · [ ] F-006 해제·정리
- 진행률 0%

## 5. 테스트
| ID | 케이스 | 기대 |
|----|--------|------|
| T-1 | IMAP/SMTP 자격증명 검증 | 정상 연동·잘못된 비번 차단 |
| T-2 | MS Graph OAuth 플로우·토큰 교환 | 토큰 암호화 저장 |
| T-3 | 시크릿 저장(평문 미보관)·토큰 만료 갱신 | 자동 갱신 |
| T-4 | 연결 테스트/상태 전이(active/error/expired) | 정확 |
| T-5 | 증분 동기화(UID validity 변경) | 재동기화·중복 없음 |
| T-6 | 계정 해제 시 토큰 revoke·캐시 정리 | 잔존 없음 |
| T-7 | 테넌트 격리(타 테넌트 계정/시크릿 접근) | 차단 |

## 7. 최종 검토
- [ ] 프로파일·연동(IMAP/OAuth)·시크릿 암호화·동기화·해제·격리 그린
- [ ] 감사(F0-08)·RBAC(F0-07)·알림(F3) 연계 확인 · AI fallback 명시
