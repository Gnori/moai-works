# F5-03 — 메시지 기능 (답글·멘션·리액션·편집/삭제)

## 메타데이터
- **작성일**: 2026-06-03 · **작성자**: GnoRi · AI Assistant
- **상태**: 1단계 · **규모**: standard · **레벨**: L3 · **우선순위**: high (Wave 2)
- **상위**: [F5 메신저 마스터](./20260603-f5-messenger-master.md) · **의존**: [F5-01 채널](./20260603-f5-01-channels.md)·[F5-02 실시간](./20260603-f5-02-realtime-messaging.md)·[F1-06 조직조회](./20260603-f1-06-org-query-view.md)·[F3 알림](./20260603-f3-notification-master.md)·[F0-01 격리](./20260603-f0-01-tenant-isolation.md)·[F0-07 RBAC](./20260603-f0-07-rbac.md)·[F0-08 감사](./20260603-f0-08-audit-log.md)

## 1. 요구사항 분석
### 1.1 문제 정의
채널 메시지에 **풍부한 상호작용**이 필요하다. 단순 텍스트를 넘어 답글(스레드)로 맥락을 묶고,
@멘션으로 특정 사용자를 호출하며, 이모지 리액션으로 가벼운 반응을 표현하고,
오타·실수는 편집/삭제로 정정한다. 코드·링크는 가독성 있게 렌더링한다.
### 1.2 목표
- **답글 스레드**: 부모 메시지에 스레드 구성, 답글 수·참여자 집계
- **@멘션**: F1-06 사용자/부서/@channel·@here, 멘션 시 F3 알림
- **리액션**: 이모지 토글, 집계·리액터 목록
- **편집/삭제**: 작성자 본인(또는 manage 권한), 편집 이력·tombstone, 감사 기록
- **마크다운**: 코드 블록(언어 하이라이트)·인라인 코드·링크 미리보기
- 모든 엔티티 tenant_id 격리

## 2. 기능 정의
| ID | 기능 | 우선순위 |
|----|------|---------|
| F-001 | 메시지 모델 확장(parent_id·content_type·멘션·블록) | Must |
| F-002 | 답글 스레드(스레드 생성·답글 목록·집계·실시간 F5-02) | Must |
| F-003 | @멘션 파싱(user/dept/channel/here)·F3 알림 | Must |
| F-004 | 이모지 리액션(토글·집계·리액터 목록) | Must |
| F-005 | 메시지 편집(본인/manage·이력·실시간 갱신) | Must |
| F-006 | 메시지 삭제(soft delete·tombstone·감사) | Must |
| F-007 | 마크다운 렌더(코드 하이라이트·인라인 코드·링크 unfurl) | Should |
| F-008 | 멘션 자동완성 검색(F1-06) | Should |

## 3. 기능 설계
### 3.1 데이터 모델
- `messages(id, tenant_id, channel_id, sender_id, parent_id NULL, content_type=text/code/system, body, blocks_json, edited_at, deleted_at, created_at)` — parent_id로 스레드, deleted_at=tombstone
- `message_mentions(id, tenant_id, message_id, target_type=user/dept/channel/here, target_id)`
- `message_reactions(id, tenant_id, message_id, user_id, emoji, created_at)` — UNIQUE(message_id,user_id,emoji) 토글
- `message_edits(id, tenant_id, message_id, prev_body, edited_at, editor_id)` — 편집 이력
- `thread_stats(message_id, reply_count, last_reply_at, participant_ids_json)` — 스레드 집계(머티리얼라이즈)
- 모든 테이블 RLS tenant_id 격리(F0-01)
### 3.2 흐름 / API
- **전송 시**: body 파싱 → 멘션 추출(`@user`/`@dept`/`@channel`/`@here`) → message_mentions 저장 → F3 알림 발행(실시간 F3-02 WS+Redis), @channel/@here는 채널 멤버 대상
- **답글**: `POST /channels/{id}/messages {parent_id}` → thread_stats 증분 → F5-02로 스레드 구독자에 푸시
- **리액션**: `PUT /messages/{id}/reactions/{emoji}` 토글 → 실시간 reaction 이벤트 브로드캐스트
- **편집**: `PATCH /messages/{id}` → 작성자 본인 또는 채널 manage(F0-07) → message_edits 적재·edited_at 갱신 → 감사(F0-08)
- **삭제**: `DELETE /messages/{id}` → deleted_at 설정(body 마스킹)·리액션/멘션 유지·tombstone 표시 → 감사
- **API**: `GET /messages/{id}/thread` 답글 목록 · `GET /messages/{id}/reactions` 리액터 · `GET /mentions/suggest?q=` 자동완성(F1-06)
- **AI 연계(placeholder)**: 스레드 요약(F5-08)·멘션 기반 할일 추출(F5-09)은 [F13 AI Agent](./20260603-f13-ai-agent-master.md) 미완 시 **비활성/no-op fallback**, 완성 후 스레드·멘션 메타데이터 제공

## 4. 개발 체크리스트
- [ ] F-001 모델확장 · [ ] F-002 스레드 · [ ] F-003 멘션·알림 · [ ] F-004 리액션
- [ ] F-005 편집·이력 · [ ] F-006 삭제·tombstone · [ ] F-007 마크다운 · [ ] F-008 자동완성
- 진행률 0%

## 5. 테스트
| ID | 케이스 | 기대 |
|----|--------|------|
| T-1 | 답글 스레드·집계 | reply_count·참여자 정확 |
| T-2 | @user/@dept/@channel/@here 파싱·알림 | F3 알림 발행 |
| T-3 | 리액션 토글(중복) | UNIQUE·증감 정확 |
| T-4 | 편집(본인/타인) | 본인 OK·타인 403·이력 적재 |
| T-5 | 삭제·tombstone | body 마스킹·감사 기록 |
| T-6 | 마크다운 코드/링크 | 하이라이트·unfurl |
| T-7 | 타 테넌트 메시지 접근 | 격리 차단(404) |

## 7. 최종 검토
- [ ] 스레드·멘션·리액션·편집/삭제·마크다운·격리·감사 그린
- [ ] F13 미완 시 AI 요약/추출 fallback 동작 확인
