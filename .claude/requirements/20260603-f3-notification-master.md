# F3 — 알림 마스터 (실시간 · 멀티채널)

## 메타데이터
- **작성일**: 2026-06-03 · **작성자**: GnoRi · AI Assistant
- **상태**: 1단계 — 요구사항 분석 (L1 도메인 마스터) · **규모**: complex(마스터)
- **우선순위**: high (Wave 1 · cross-cutting) · **연관**: 의존 [F0 토대](./20260603-f0-saas-foundation-master.md)·[F1 조직](./20260603-f1-organization-master.md) · 발행처: F2 게시판·F5 메신저·F10 결재·F1 발령·일정 등 전 모듈

## 1. 요구사항 분석
### 1.1 문제 정의
모든 모듈이 사용자에게 **알림**(새 글·댓글·멘션·결재·일정·발령…)을 보낸다. 이를 **공통 알림
토대**(생성·실시간 전달·멀티채널·구독설정)로 일원화한다.
### 1.2 목표
- 표준 **알림 이벤트 모델** + 발행 API(전 모듈 호출)
- **실시간 전달**(WebSocket + Redis pub/sub) · 읽음 처리
- **멀티채널**: 웹 · 모바일/PC 푸시 · 메일 + **사용자 구독/설정**
- 대상 resolve(F1 조직: 부서·역할 팬아웃)

### 1.3 제약/인프라
- F0-01 격리 · **MOAI-Redis** 실시간 pub/sub · WebSocket(STOMP)
- 모바일/PC 푸시(웹푸시/FCM/APNs) · 메일(F6/SMTP)

## 2. 기능 정의 — L3 분해
| L3 ID | spec | 규모 | 내용 |
|-------|------|------|------|
| **F3-01** | 알림 코어(이벤트 모델·저장·읽음) | standard | 알림 생성/조회/읽음/안읽음 카운트 |
| **F3-02** | 실시간 전달(WebSocket + Redis) | standard | WS 허브·Redis pub/sub·연결관리 |
| **F3-03** | 채널·구독·설정(웹/푸시/메일) | standard | 채널별 발송·사용자 알림 설정·DND |
| **F3-04** | 디스패치/팬아웃(발행 API) | standard | 이벤트→대상(F1)→채널 라우팅·공통 발행 |

## 3. 기능 설계 (토대)
### 3.1 데이터 모델
`notifications(id, tenant_id, recipient_id, type, title, body, link, is_read, created_at, meta jsonb)` · `notification_settings(user_id, type, channels)` · 푸시 토큰
### 3.2 통합점
- 발행: `NotificationService.publish(event, targets, channels)` (전 모듈)
- 대상: F1 조직(부서/역할 팬아웃) · 실시간: Redis→WS · 메일: F6/SMTP

## 4. 개발 진행 (L3)
- [ ] F3-01 코어 → [ ] F3-04 디스패치 → [ ] F3-02 실시간 → [ ] F3-03 채널/설정

## 9. 완료 기준 (마스터)
- [ ] 전 모듈이 공통 발행 API 로 알림 발송 · 실시간 수신·읽음
- [ ] 멀티채널(웹/푸시/메일)·사용자 설정·격리·조직 팬아웃
