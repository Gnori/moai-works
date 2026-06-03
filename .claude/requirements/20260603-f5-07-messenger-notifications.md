# F5-07 — 메신저 알림

## 메타데이터
- **작성일**: 2026-06-03 · **작성자**: GnoRi · AI Assistant
- **상태**: 1단계 · **규모**: standard · **레벨**: L3 · **우선순위**: high (Wave 2)
- **상위**: [F5 마스터](./20260603-f5-messenger-master.md) · **의존**: [F3 알림](./20260603-f3-notification-master.md)·[F3-02 실시간전송](./20260603-f3-02-realtime-delivery.md)·[F5-01 채널](./20260603-f5-01-channels.md)·[F5-03 답글/멘션](./20260603-f5-03-message-features.md)·[F0-07 RBAC](./20260603-f0-07-rbac.md)

## 1. 요구사항 분석
### 1.1 문제 정의
메신저는 트래픽이 많아 **모든 메시지를 알리면 피로**가 크다. 멘션·DM·키워드 등
**관심 이벤트만** 선별해 모바일/PC 푸시로 전달하고, 채널별 설정·방해금지(DND)로
사용자가 알림량을 통제할 수 있어야 한다.
### 1.2 목표
- 알림 트리거: **멘션(@user/@channel/@here)·DM·키워드·답글** → F3 위임
- 채널별 알림 레벨(전체/멘션만/꺼짐) · 글로벌 DND(시간대) · 격리

## 2. 기능 정의
| ID | 기능 | 우선순위 |
|----|------|---------|
| F-001 | 알림 트리거 평가(멘션/DM/키워드/답글) | Must |
| F-002 | F3 위임 전송(모바일/PC 푸시·인앱) | Must |
| F-003 | 채널별 알림 레벨(전체/멘션만/꺼짐) | Must |
| F-004 | 글로벌 DND(시간대·요일) | Should |
| F-005 | 키워드 알림 등록(개인) | Should |
| F-006 | 멘션됨/스레드 구독 알림 | Should |

## 3. 기능 설계
### 3.1 데이터 모델
- `channel_notify_pref(tenant_id, channel_id, user_id, level=all/mention/none, muted_until)`
- `user_notify_keyword(tenant_id, user_id, keyword)`
- `user_dnd(tenant_id, user_id, enabled, start, end, days[], tz)`
- `thread_subscription(tenant_id, thread_id, user_id)` — 참여/멘션 시 자동 구독
- 전송 본체는 보관 안 함 → **F3 알림 엔티티 재사용**(메신저는 트리거·설정만 소유)

### 3.2 트리거 흐름
1. 메시지 생성(F5-02/F5-03) → **이벤트** 발행(mention_ids·keywords·dm·thread)
2. 알림 평가: 수신후보 = 멘션 + DM 상대 + 키워드 매칭 + 스레드 구독자
3. 각 후보별 필터: 채널 레벨(none→제외, mention→멘션만) → DND(시간대면 보류/인앱만) → 본인 발신 제외 → 격리
4. 통과분 → **F3-02 WS+Redis** 실시간 + 모바일/PC 푸시 위임
### 3.3 API (`/api/v1/messenger`)
- `GET/PUT /channels/{id}/notify-pref` — 채널 알림 레벨·뮤트
- `GET/PUT /me/dnd` — DND 설정
- `GET/POST/DELETE /me/keywords` — 키워드 알림
- 모든 응답 `tenant_id` 격리 · 권한 F0-07 (채널 멤버만)

## 4. 개발 체크리스트
- [ ] F-001 트리거평가 · [ ] F-002 F3위임 · [ ] F-003 채널레벨 · [ ] F-004 DND · [ ] F-005 키워드 · [ ] F-006 스레드구독
- 진행률 0%

## 5. 테스트
| ID | 케이스 | 기대 |
|----|--------|------|
| T-1 | 멘션 → 푸시 | F3 전송 |
| T-2 | 채널 레벨=mention, 일반 메시지 | 알림 없음 |
| T-3 | 채널 레벨=none | 알림 없음 |
| T-4 | DND 시간대 | 보류/인앱만 |
| T-5 | 키워드 매칭 | 알림 발생 |
| T-6 | 본인 발신 | 알림 제외 |
| T-7 | 타 테넌트 | 차단 |

## 7. 최종 검토
- [ ] 트리거·F3위임·채널레벨·DND·키워드·격리 그린
