# F6-02 — 이메일 조회/송신

## 메타데이터
- **작성일**: 2026-06-03 · **작성자**: GnoRi · AI Assistant
- **상태**: 1단계 · **규모**: standard · **레벨**: L3 · **우선순위**: high (Wave 3)
- **상위**: [F6 마스터](./20260603-f6-mailbox-master.md) · **의존**: [F6-01 계정연동](./20260603-f6-01-mail-account.md)·[F4 파일드라이브](./20260603-f4-filedrive-master.md)·[F0-01 격리](./20260603-f0-01-tenant-isolation.md)·[F0-07 RBAC](./20260603-f0-07-rbac.md)·[F3-02 실시간](./20260603-f3-02-realtime-delivery.md)

## 1. 요구사항 분석
### 1.1 문제 정의
연동 계정(F6-01)의 메일을 **그룹웨어 내부에서 조회·작성·송신**해야 한다. 외부 메일 클라이언트
전환 없이 받은편지함 확인, 답장/전달, 첨부(F4 연동), 폴더/라벨 정리를 처리한다.
### 1.2 목표
- **동기화 메일 목록**(받은편지함·폴더별·페이지네이션) · 본문/헤더 조회 · 읽음 표시
- **송신**(신규/답장/전달) · 첨부(F4 파일 → 메일 첨부) · 초안 저장
- **폴더/라벨** 관리(이동·라벨 부여) · 격리(tenant_id)·권한(F0-07)

## 2. 기능 정의
| ID | 기능 | 우선순위 |
|----|------|---------|
| F-001 | 메일 목록 조회(폴더별·페이징·검색필드) | Must |
| F-002 | 메일 본문/헤더 조회·읽음 처리 | Must |
| F-003 | 메일 송신(신규/답장/전달) | Must |
| F-004 | 첨부(F4 파일 연결 · 인라인 다운로드) | Must |
| F-005 | 초안 저장/수정 | Should |
| F-006 | 폴더 이동·라벨 부여/해제 | Should |
| F-007 | 신규 수신 실시간 알림(F3-02) | Should |

## 3. 기능 설계
### 3.1 데이터 모델
- `mail_message(id, tenant_id, account_id, folder, message_id, thread_id, from_addr, to_addrs, cc, subject, snippet, body_ref, is_read, has_attachment, received_at, raw_ref)`
- `mail_attachment(id, tenant_id, message_id, filename, content_type, size, file_id→F4, inline_cid)`
- `mail_label(id, tenant_id, account_id, name, color)` · `mail_message_label(message_id, label_id)`
- `mail_draft(id, tenant_id, account_id, to_addrs, cc, subject, body, attachment_ids, in_reply_to, updated_at)`
- 송신 이력은 `mail_message(folder='sent')` 로 통합 · 모든 엔티티 `tenant_id` RLS(F0-01)

### 3.2 흐름/API
- 목록: 동기화(F6-01)된 헤더 인덱스 조회 · 본문은 lazy fetch(`body_ref`) · 격리·권한 검사
- 송신: 초안→첨부(F4 file_id 확인)→IMAP/SMTP(또는 Graph/Gmail API) 전송→`sent` 저장→감사(F0-08)
- 첨부: 수신 첨부는 F4 공간에 저장 후 `file_id` 링크 · 송신 첨부는 F4 파일을 메일 part로 직렬화
- 수신 알림: 동기화 신규 메일 → F3-02 WS+Redis 재사용 푸시
- API: `GET /mail/{account}/messages?folder&page` · `GET /mail/messages/{id}` · `POST /mail/{account}/send` · `PATCH /mail/messages/{id}/read` · `POST /mail/{account}/drafts` · `POST /mail/messages/{id}/move` · `POST /mail/messages/{id}/labels`
- AI 분류/요약은 **F6-03 범위** · 본 spec 미포함(F8/F13 미완 시 placeholder는 F6-03에서 처리)

## 4. 개발 체크리스트
- [ ] F-001 목록 · [ ] F-002 본문/읽음 · [ ] F-003 송신 · [ ] F-004 첨부(F4) · [ ] F-005 초안 · [ ] F-006 폴더/라벨 · [ ] F-007 실시간 알림
- 진행률 0%

## 5. 테스트
| ID | 케이스 | 기대 |
|----|--------|------|
| T-1 | 목록 조회(폴더·페이징) | 정확·격리 |
| T-2 | 본문 조회/읽음 처리 | is_read 갱신 |
| T-3 | 송신(답장·thread 연결) | 발송·sent 저장 |
| T-4 | 첨부(F4 연결/다운로드) | file_id 매핑 정확 |
| T-5 | 초안 저장→송신 전환 | 정상 |
| T-6 | 폴더 이동/라벨 | 반영 정확 |
| T-7 | 타 테넌트 메일 접근 | 차단(격리) |

## 7. 최종 검토
- [ ] 목록·본문·송신·첨부·초안·폴더/라벨·격리·실시간 그린
