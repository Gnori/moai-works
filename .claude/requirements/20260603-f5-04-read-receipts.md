# F5-04 — 읽음 확인

## 메타데이터
- **작성일**: 2026-06-03 · **작성자**: GnoRi · AI Assistant
- **상태**: 1단계 · **규모**: standard · **레벨**: L3 · **우선순위**: high (Wave 2)
- **상위**: [F5 마스터](./20260603-f5-messenger-master.md) · **의존**: [F5-01 채널](./20260603-f5-01-channels.md)·[F5-02 실시간](./20260603-f5-02-realtime-messaging.md)·[F3-02 실시간전송](./20260603-f3-02-realtime-delivery.md)·[F0-01 격리](./20260603-f0-01-tenant-isolation.md)·[F0-07 RBAC](./20260603-f0-07-rbac.md)

## 1. 요구사항 분석
### 1.1 문제 정의
메신저에서 **상대가 내 메시지를 봤는지**, 채널에 **안 읽은 메시지가 몇 개**인지가
드러나야 한다. 멤버별 **마지막 읽은 위치**를 추적해 안읽음 카운트·"N명 읽음"을 계산한다.
### 1.2 목표
- 멤버별 **마지막 읽은 메시지(last_read)** 추적 · 채널 **안읽음 카운트**
- 메시지별 **"N명 읽음"**(DM=읽음/안읽음, 그룹=명수) · **읽음 위치 동기화**(F3-02 WS)
- 실시간 갱신 · 멘션 안읽음 별도 카운트 · 격리

## 2. 기능 정의
| ID | 기능 | 우선순위 |
|----|------|---------|
| F-001 | 읽음 마킹(채널 진입·스크롤 시 last_read 갱신) | Must |
| F-002 | 채널별 안읽음 카운트(전체·멘션) | Must |
| F-003 | 메시지별 "N명 읽음" 집계(DM=상태, 그룹=명수) | Must |
| F-004 | 읽음 상태 실시간 브로드캐스트(F3-02 WS) | Must |
| F-005 | 멀티 디바이스 last_read 동기화(max 병합) | Should |

## 3. 기능 설계
### 3.1 데이터 모델
- `channel_read_state(tenant_id, channel_id, user_id, last_read_message_id, last_read_at, unread_count, unread_mention_count)` — PK(channel_id, user_id)
- "N명 읽음"은 `last_read_message_id ≥ 대상 message_id` 인 멤버 수로 **on-demand 집계**(별도 per-message 레코드 미보존, 그룹 대용량 대비)
- 카운트 캐시: Redis `unread:{tenant}:{channel}:{user}` (DB는 source of truth)
### 3.2 흐름/API
- `POST /channels/{id}/read` `{last_read_message_id}` → last_read 갱신·unread 0·**WS read 이벤트** 송신(자신 디바이스 + 채널 멤버에 read marker)
- `GET /channels/unread` → 채널별 `{unread_count, unread_mention_count}` 배치
- `GET /messages/{id}/read-by` → 해당 메시지 이후 위치인 멤버 목록·수("N명 읽음")
- 신규 메시지 수신(F5-02) 시 발신자 제외 멤버 unread_count++ · 멘션 시 mention++ · WS push
- last_read 갱신은 **단조 증가**(과거 message_id 무시) → 멀티 디바이스 race 방지
- 모든 쿼리 tenant_id 필터(F0-01) · 채널 멤버십 검사(F0-07)

## 4. 개발 체크리스트
- [ ] F-001 읽음 마킹 · [ ] F-002 안읽음 카운트 · [ ] F-003 N명 읽음 집계
- [ ] F-004 WS 브로드캐스트 · [ ] F-005 멀티 디바이스 동기화
- 진행률 0%

## 5. 테스트
| ID | 케이스 | 기대 |
|----|--------|------|
| T-1 | 읽음 마킹 → unread 0 | 정확 |
| T-2 | 신규 메시지 → unread++ · 멘션++ | 정확 |
| T-3 | "N명 읽음" 집계 | 멤버 수 정확 |
| T-4 | 멀티 디바이스(과거 위치) | 무시(단조 증가) |
| T-5 | WS read 이벤트 | 멤버 수신 |
| T-6 | 타 테넌트/비멤버 접근 | 차단 |

## 7. 최종 검토
- [ ] 마킹·카운트·N명읽음·WS·멀티디바이스·격리 그린
