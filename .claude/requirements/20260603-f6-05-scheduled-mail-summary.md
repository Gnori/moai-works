# F6-05 — 예약 이메일 요약 (다이제스트)

## 메타데이터
- **작성일**: 2026-06-03 · **작성자**: GnoRi · AI Assistant
- **상태**: 1단계 · **규모**: standard · **레벨**: L3 · **우선순위**: medium (Wave 3)
- **상위**: [F6 마스터](./20260603-f6-mailbox-master.md) · **의존**: [F6-03 AI 분류/요약](./20260603-f6-03-ai-mail-triage.md)·[F6-02 조회/송신](./20260603-f6-02-mail-view-send.md)·[F14 자동화](./20260603-f14-automation-master.md)·[F3 알림](./20260603-f3-notification-master.md)
- **연계(미완)**: [F13 AI Agent](./20260603-f13-ai-agent-master.md)·[F7 지식](./20260603-f7-knowledge-master.md)

## 1. 요구사항 분석
### 1.1 문제 정의
사용자는 매일 쌓이는 메일을 일일이 확인하기 어렵다. **예약 시각(아침 등)에 자동 생성된 메일
다이제스트**를 알림·대시보드로 받아, 하루를 한눈에 파악하고자 한다. 요약 생성은 **F14 스케줄러**가
트리거하고, 결과는 **F3 알림 + 대시보드 위젯(H)** 으로 전달한다.
### 1.2 목표
- **예약 다이제스트**: 사용자별 시각/요일/타임존 설정 → F14 cron 트리거 → 미열람 메일 요약
- 다이제스트 구성: 중요 메일 Top-N · 카테고리별 건수 · 액션 필요 항목 · 요약문
- **전달 채널**: F3 알림(인앱/이메일) + 대시보드 위젯(H 홈) · 격리(F0-01)
- AI 요약 미완 시 **fallback**(건수·제목 목록만) 제공

## 2. 기능 정의
| ID | 기능 | 우선순위 |
|----|------|---------|
| F-001 | 다이제스트 스케줄 설정(시각·요일·타임존·on/off) | Must |
| F-002 | F14 트리거 → 미열람 메일 수집·요약 생성 | Must |
| F-003 | 다이제스트 렌더(Top-N·카테고리 집계·액션 항목) | Must |
| F-004 | F3 알림 발송(인앱/이메일) | Must |
| F-005 | 대시보드 위젯(H) 다이제스트 카드 | Should |
| F-006 | AI 미완 시 fallback(제목·건수만) | Must |

## 3. 기능 설계
### 3.1 데이터 모델
- `mail_digest_schedule(id, tenant_id, user_id, account_id, send_at_local, days_mask, timezone, enabled, channels[])`
- `mail_digest_run(id, tenant_id, user_id, scheduled_for, status=pending/done/failed, mail_count, summary_json, generated_by=ai/fallback, created_at)`
- 모든 엔티티 `tenant_id` (F0-01 RLS). 권한 F0-07. 감사 F0-08(스케줄 변경·발송 기록).
### 3.2 흐름
1. 사용자가 스케줄 등록 → **F14 자동화**에 cron 잡 등록(타임존 기준 다음 발화 시각 계산)
2. F14 발화 → `mail_digest_run(pending)` 생성 → 미열람 메일 조회(F6-02 저장소)
3. **F6-03 요약 엔진** 호출 → 중요도/카테고리 집계·요약문 생성 (미완 시 fallback)
4. `summary_json` 저장 → **F3-02 WS+Redis** 인앱 알림 + 대시보드 위젯 갱신
### 3.3 API (handler + 통합테스트 필수)
- `GET/PUT /api/v1/mail/digest/schedule` — 스케줄 조회/설정
- `POST /api/v1/mail/digest/preview` — 즉시 미리보기 생성
- `GET /api/v1/mail/digest/runs?limit=` — 최근 다이제스트 이력
- `GET /api/v1/mail/digest/runs/{id}` — 단건 상세
### 3.4 AI placeholder/fallback
F6-03(요약)·F13(Agent) 미완 시 `generated_by=fallback` 으로 **카테고리별 건수 + 제목 리스트**만 렌더.
완성 후 요약문·액션 추출·우선순위 스코어 연계(F13 라우팅, F7 지식 컨텍스트).

## 4. 개발 체크리스트
- [ ] F-001 스케줄 설정 · [ ] F-002 F14 트리거·수집 · [ ] F-003 다이제스트 렌더
- [ ] F-004 F3 알림 · [ ] F-005 대시보드 위젯 · [ ] F-006 fallback
- 진행률 0%

## 5. 테스트
| ID | 케이스 | 기대 |
|----|--------|------|
| T-1 | 스케줄 설정/타임존 환산 | 정확 발화 시각 |
| T-2 | F14 트리거 → run 생성·요약 | done·summary 채움 |
| T-3 | 미열람 0건 | 발송 스킵(또는 빈 다이제스트) |
| T-4 | AI 미완 → fallback | 제목·건수만 렌더 |
| T-5 | F3 알림/위젯 전달 | 인앱+위젯 갱신 |
| T-6 | 테넌트 격리 | 타 테넌트 메일 미포함 |
| T-7 | 권한/비활성 스케줄 | 401/403·미발송 |

## 7. 최종 검토
- [ ] 스케줄·F14 트리거·다이제스트·F3 알림·위젯·fallback·격리 그린
- [ ] 핸들러/서비스/리포 커버리지 ≥ 85% · 7케이스 통과
