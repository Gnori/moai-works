# F5-05 — 파일 공유 · 문서 공동 검토

## 메타데이터
- **작성일**: 2026-06-03 · **작성자**: GnoRi · AI Assistant
- **상태**: 1단계 · **규모**: standard · **레벨**: L3 · **우선순위**: high (Wave 2)
- **상위**: [F5 마스터](./20260603-f5-messenger-master.md) · **의존**: [F5-01 채널](./20260603-f5-01-channels.md)·[F5-02 실시간](./20260603-f5-02-realtime-messaging.md)·[F4 파일드라이브](./20260603-f4-filedrive-master.md)·[F0-07 RBAC](./20260603-f0-07-rbac.md)·[F0-01 격리](./20260603-f0-01-tenant-isolation.md)

## 1. 요구사항 분석
### 1.1 문제 정의
메신저 대화 중 **파일을 첨부·공유**하고 미리보기로 확인하며, 문서는 채널에서 바로
**공동 검토**(코멘트·승인)로 이어져야 한다. 파일 저장은 **F4 파일드라이브를 재사용**하고
메신저는 메시지↔파일 링크와 미리보기·검토 흐름만 담당한다.
### 1.2 목표
- 메시지 첨부 업/다운로드 — **F4 스토리지(MinIO) 재사용**, 중복 업로드 방지
- 이미지/PDF/오피스 **인라인 미리보기**(썸네일·뷰어)
- 문서 **공동 검토**(F4/F8 문서 링크 → 채널 코멘트·검토 요청·승인)
- 채널 멤버십 기반 **접근 권한**(F5-01 멤버 = 첨부 열람) · 테넌트 격리

## 2. 기능 정의
| ID | 기능 | 우선순위 |
|----|------|---------|
| F-001 | 메시지 파일 첨부 업로드(F4 재사용·다중·드래그앤드롭) | Must |
| F-002 | 첨부 다운로드 · presigned URL(만료) | Must |
| F-003 | 인라인 미리보기(이미지 썸네일·PDF/오피스 뷰어) | Should |
| F-004 | F4/F8 문서 링크 공유(파일ID 참조, 사본 미생성) | Must |
| F-005 | 문서 공동 검토(검토 요청·코멘트 스레드·승인/반려) | Should |
| F-006 | 첨부 접근 권한(채널 멤버 + F4 ACL 결합) · 격리 | Must |

## 3. 기능 설계
### 3.1 데이터 모델
- `message_attachment(id, tenant_id, message_id, channel_id, file_id→F4, kind=upload/link, name, mime, size, thumb_key, created_at)`
- `doc_review(id, tenant_id, channel_id, message_id, file_id→F4, status=requested/in_review/approved/rejected, requester_id, created_at)`
- `doc_review_comment(id, tenant_id, review_id, author_id, body, anchor, created_at)`
- 모든 엔티티 `tenant_id`(F0-01 RLS) · 파일 실체는 F4 소유, 메신저는 `file_id` 참조만

### 3.2 흐름 / API
- 업로드: 클라 → F4 업로드(또는 기존 file_id 선택) → `message_attachment` 생성 → 메시지 전송(F5-02 WS 브로드캐스트)
- 다운로드: `GET /attachments/{id}/url` → F4 presigned URL(만료) 반환 → 권한 검사 후 발급
- 미리보기: 이미지 썸네일 F4 파생, PDF/오피스는 F4 뷰어 임베드(미지원 타입은 다운로드 fallback)
- 검토: `POST /reviews` 요청 → 코멘트 스레드 → `PATCH /reviews/{id}` 승인/반려 → F3 알림
- 주요 API: `POST /channels/{id}/attachments` · `GET /attachments/{id}/url` · `POST /attachments/link`(F4 file_id) · `POST /reviews` · `POST /reviews/{id}/comments` · `PATCH /reviews/{id}`
- 권한: 채널 멤버십(F5-01) AND F4 파일 ACL(F0-07) 동시 충족 시 열람/다운로드

### 3.3 placeholder / fallback
- **F8(AI 문서) 미완**: 문서 공동검토는 F4 일반 파일 링크로만 동작(자동 요약·AI 코멘트 없음). F8 완성 후 검토 패널에 AI 요약·차이 비교 연계.
- **F7(지식) 미완**: 검토 완료 문서의 지식 베이스 적재는 placeholder(승인 이벤트만 기록), F7 연계 시 자동 색인.

## 4. 개발 체크리스트
- [ ] F-001 첨부 업로드(F4) · [ ] F-002 다운로드 presigned · [ ] F-003 미리보기 · [ ] F-004 문서 링크
- [ ] F-005 공동 검토 · [ ] F-006 권한·격리 · [ ] F3 검토 알림 연동
- 진행률 0%

## 5. 테스트
| ID | 케이스 | 기대 |
|----|--------|------|
| T-1 | 첨부 업로드(F4 재사용)/메시지 링크 | 정상·중복저장 없음 |
| T-2 | 다운로드 presigned 만료 | 만료 후 401/차단 |
| T-3 | 비멤버 첨부 접근(403) | 차단 |
| T-4 | 미리보기(이미지/PDF) · 미지원 타입 | 뷰어/다운로드 fallback |
| T-5 | 문서 검토 요청→승인 | 상태 전이·F3 알림 |
| T-6 | 타 테넌트 file_id 참조(404) | 차단(격리) |

## 7. 최종 검토
- [ ] 업/다운·미리보기·문서링크·공동검토·권한·격리 그린
- [ ] F4 재사용(사본 미생성) 검증 · F8/F7 fallback 명시 확인
