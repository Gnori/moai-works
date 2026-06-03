# F6-03 — AI 메일 분류/요약 (Triage)

## 메타데이터
- **작성일**: 2026-06-03 · **작성자**: GnoRi · AI Assistant
- **상태**: 1단계 · **규모**: standard · **레벨**: L3 · **우선순위**: high (Wave 3)
- **상위**: [F6 마스터](./20260603-f6-mailbox-master.md) · **의존**: [F6-02 조회/송신](./20260603-f6-02-mail-view-send.md)·[F13 AI Agent](./20260603-f13-ai-agent-master.md)·[F7 지식](./20260603-f7-knowledge-master.md)·[F0-07 RBAC](./20260603-f0-07-rbac.md)·[F0-08 감사](./20260603-f0-08-audit-log.md)·[F3 알림](./20260603-f3-notification-master.md)

## 1. 요구사항 분석
### 1.1 문제 정의
사용자는 매일 다수의 메일을 받지만 **무엇이 중요하고 무엇에 답해야 하는지** 일일이 읽어야 안다.
받은 메일을 AI가 **요약·중요도 분류·답변필요 여부**로 자동 정리하여 인박스 부담을 줄인다.
### 1.2 목표
- 개별 메일 **3줄 요약** + 핵심 액션 추출
- **중요 메일 분류**(high/normal/low) — 발신자·키워드·조직(F1-06) 컨텍스트 결합
- **답변필요(needs_reply) 판정** + 마감/긴급 신호 추출
- F13 Agent 기반 · **미완 시 placeholder/fallback**(규칙 기반 휴리스틱) · 테넌트 격리

## 2. 기능 정의
| ID | 기능 | 우선순위 |
|----|------|---------|
| F-001 | 메일 요약(3줄 + 핵심 액션) | Must |
| F-002 | 중요도 분류(high/normal/low) | Must |
| F-003 | 답변필요 판정(needs_reply + 마감/긴급) | Must |
| F-004 | 분류 결과 인박스 라벨/뱃지 노출 | Must |
| F-005 | 일괄 트리아지(미처리 메일 배치 처리) | Should |
| F-006 | F13 미완 시 규칙 기반 fallback | Must |
| F-007 | 사용자 피드백(분류 수정→재학습 신호) | Could |

## 3. 기능 설계
### 3.1 데이터 모델
- `mail_triage(id, tenant_id, message_id FK, summary TEXT, action_items JSONB, importance ENUM(high/normal/low), needs_reply BOOL, urgency ENUM(none/soon/urgent), due_hint TIMESTAMP NULL, source ENUM(agent/fallback), confidence NUMERIC, created_at)`
- `mail_triage_feedback(id, tenant_id, triage_id FK, user_id, corrected_importance, corrected_needs_reply, created_at)`
- 모든 테이블 `tenant_id` + RLS(F0-01). 1 메시지 : 1 triage(최신 갱신).
### 3.2 처리 흐름
1. 신규 메일 수신(F6-02 동기화) → triage 큐 적재
2. **F13 Agent 호출**: 메일 본문/메타 + 발신자 조직 컨텍스트(F1-06) + 지식 컨텍스트(F7) → 요약·중요도·needs_reply·urgency 산출
3. **F13/F7 미완 시 fallback**: 규칙 휴리스틱(발신자 화이트리스트·키워드 사전·"?"/마감 표현 감지·미회신 스레드) → `source=fallback`, confidence 보수값
4. 결과 `mail_triage` upsert → 인박스 라벨/뱃지 갱신 → high 또는 urgent 시 F3 알림(F3-02 WS+Redis)
5. 사용자 수정 시 feedback 기록(추후 Agent 튜닝 신호), 감사 로그(F0-08)
### 3.3 API (REST, `/api/v1/mail`)
| 메서드 | 경로 | 설명 |
|--------|------|------|
| POST | `/messages/{id}/triage` | 단건 트리아지(재요청) |
| GET | `/messages/{id}/triage` | triage 결과 조회 |
| POST | `/triage/batch` | 미처리 일괄 트리아지 |
| GET | `/inbox/triage?importance=&needs_reply=` | 분류 필터 조회 |
| POST | `/triage/{id}/feedback` | 분류 수정 피드백 |
- 권한 F0-07: 본인 계정 메일만(F6-01 소유). 격리 위반·교차 테넌트 404.

## 4. 개발 체크리스트
- [ ] F-001 요약 · [ ] F-002 중요도 · [ ] F-003 답변필요 · [ ] F-004 라벨노출
- [ ] F-005 배치 · [ ] F-006 fallback · [ ] F-007 피드백 · [ ] F13/F7 연계 토글
- 진행률 0%

## 5. 테스트
| ID | 케이스 | 기대 |
|----|--------|------|
| T-1 | 요약/액션 추출 | 3줄·액션 정확 |
| T-2 | 중요도 분류 | high/normal/low 적정 |
| T-3 | needs_reply 판정 | 질문/마감 메일 검출 |
| T-4 | F13 미완 fallback | 규칙 동작·source=fallback |
| T-5 | high/urgent 알림 | F3-02 발송 |
| T-6 | 피드백 기록 | feedback 저장·감사 |
| T-7 | 교차 테넌트 접근 | 404 차단 |

## 7. 최종 검토
- [ ] 요약·중요도·답변필요·라벨·배치·fallback·피드백·격리 그린
- [ ] F13/F7 완성 후 Agent 경로 전환 검증
