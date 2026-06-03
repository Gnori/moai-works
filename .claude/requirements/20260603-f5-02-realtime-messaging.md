# F5-02 — 실시간 메시지 송수신

## 메타데이터
- **작성일**: 2026-06-03 · **작성자**: GnoRi · AI Assistant
- **상태**: 1단계 · **규모**: standard · **레벨**: L3 · **우선순위**: critical (Wave 2)
- **상위**: [F5 마스터](./20260603-f5-messenger-master.md) · **의존**: [F5-01 채널](./20260603-f5-01-channels.md)·[F3-02 실시간 전달](./20260603-f3-02-realtime-delivery.md)·[F0-01 테넌트격리](./20260603-f0-01-tenant-isolation.md)·[F0-07 RBAC](./20260603-f0-07-rbac.md)

## 1. 요구사항 분석
### 1.1 문제 정의
채널 메시지는 **수백 ms 내** 모든 참여자에게 도달하고, **순서가 보장**되며,
순간 끊김·재접속에도 **유실 없이** 전달돼야 한다. 타이핑 표시로 대화 현장감을 준다.
### 1.2 목표
- WebSocket 양방향 송수신 · **F3-02의 WS 게이트웨이+Redis Pub/Sub 인프라 재사용**
- 채널 내 **단조증가 seq** 순서 보장 · ack 기반 전달보장 · 재접속 시 누락 동기화
- 타이핑 표시(typing) · 멀티디바이스 fan-out · 격리(채널 멤버만 수신)

## 2. 기능 정의
| ID | 기능 | 우선순위 |
|----|------|---------|
| F-001 | WS 연결/구독(채널별 join, 멤버십·격리 검증) | Must |
| F-002 | 메시지 전송(클라→서버, 멱등 client_msg_id) | Must |
| F-003 | Redis Pub/Sub fan-out(멀티노드·멀티디바이스) | Must |
| F-004 | 채널 seq 순서 보장(단조증가 발번) | Must |
| F-005 | ack/재전송·재접속 누락 동기화(since_seq) | Must |
| F-006 | 타이핑 표시(typing start/stop, ephemeral) | Should |
| F-007 | 전송 상태(sending/sent/delivered) 통지 | Should |

## 3. 기능 설계
### 3.1 데이터 모델
- `messages(id, tenant_id, channel_id, seq BIGINT, sender_id, body, client_msg_id, created_at)` — `UNIQUE(channel_id, seq)`·`UNIQUE(channel_id, client_msg_id)`(멱등)
- `channel_seq(channel_id, last_seq)` — 채널별 seq 발번(원자적 증가, Redis `INCR` 캐시 + DB 동기화)
- typing/상태는 **비영속**(Redis TTL `typing:{channel}:{user}` 5s)
### 3.2 흐름
1. 클라 WS 연결 → F3-02 게이트웨이 인증(Keycloak JWT) → `join {channel_id}` → 멤버십·격리 검증 후 구독
2. 전송: 클라 `send {channel_id, body, client_msg_id}` → 멱등 확인 → seq 발번 → DB 저장 → Redis `publish msg:{tenant}:{channel}` → 전 노드 fan-out → 멤버 WS push + ack
3. 순서: 수신측은 seq로 정렬·gap 감지 시 `sync since_seq` 요청 → 누락분 재전송
4. 재접속: 마지막 seq 전달 → 그 이후 메시지 일괄 동기화
5. 타이핑: `typing {channel_id, state}` → Redis 게시 → 멤버에 ephemeral 브로드캐스트(저장 안 함)
### 3.3 API/WS
- `WS /ws/messenger` — 인증·세션(F3-02 재사용)
- C→S: `join`·`leave`·`send`·`ack`·`sync`·`typing`
- S→C: `message`·`ack`·`typing`·`presence`·`error`
- `GET /api/channels/{id}/messages?since_seq=&limit=` — 재접속/스크롤 동기화(REST 폴백)
- 알림(언급·오프라인 멤버)은 [F3 알림](./20260603-f3-notification-master.md)·[F5-07 알림](./20260603-f5-07-messenger-notifications.md)으로 위임 — **본 spec은 실시간 전달만 담당**

## 4. 개발 체크리스트
- [ ] F-001 WS join/격리 · [ ] F-002 멱등 전송 · [ ] F-003 Redis fan-out · [ ] F-004 seq 순서
- [ ] F-005 ack/동기화 · [ ] F-006 타이핑 · [ ] F-007 전송상태
- 진행률 0%

## 5. 테스트
| ID | 케이스 | 기대 |
|----|--------|------|
| T-1 | 전송→타 디바이스 수신 | <500ms 도달 |
| T-2 | 동일 client_msg_id 재전송 | 중복 저장 안 됨(멱등) |
| T-3 | 동시 다발 전송 | seq 단조증가·유실 없음 |
| T-4 | 재접속 since_seq 동기화 | 누락분 정확 재전송 |
| T-5 | 비멤버 join/수신 시도 | 403 차단 |
| T-6 | 타 테넌트 채널 구독 | 격리 차단 |
| T-7 | 타이핑 게시 | ephemeral, DB 미저장 |

## 7. 최종 검토
- [ ] 송수신·순서·전달보장·재접속동기화·타이핑·격리 그린
- [ ] F3-02 인프라 재사용(중복 게이트웨이 미구축) 확인
