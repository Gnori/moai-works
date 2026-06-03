# F5-10 — AI 답변 초안·회의 제안

## 메타데이터
- **작성일**: 2026-06-03 · **작성자**: GnoRi · AI Assistant
- **상태**: 1단계 · **규모**: standard · **레벨**: L3 · **우선순위**: medium (Wave 3)
- **상위**: [F5 마스터](./20260603-f5-messenger-master.md) · **의존**: [F5-02 실시간](./20260603-f5-02-realtime-messaging.md)·[F5-08 대화요약](./20260603-f5-08-ai-summary.md)·[F13 AI Agent](./20260603-f13-ai-agent-master.md)·[F1-06 조직](./20260603-f1-06-org-query-view.md)·[F0-07 RBAC](./20260603-f0-07-rbac.md)

## 1. 요구사항 분석
### 1.1 문제 정의
채널 대화 흐름 속에서 사용자는 **적절한 답변을 쓰는 데 시간**을 쓰고, 회의 잡기는
**참석자 일정 조율** 때문에 반복적 핑퐁이 된다. AI가 맥락 기반 **답변 초안**과
**회의 일정 후보**(참석자 가용시간 고려)를 제안해 작성·조율 비용을 줄인다.
### 1.2 목표
- 메시지/스레드 맥락 → **답변 초안 3안** 생성(톤 선택: 격식/간결/친근)
- 대화에서 **회의 의도 감지** → 참석자·기간 후보 추출
- 참석자 **일정 가용성**(일정 모듈) 결합 → **회의 시간 후보 제안**
- 초안은 **전송 전 편집 필수**(자동 전송 금지) · 격리 · F13 미완 시 fallback

## 2. 기능 정의
| ID | 기능 | 우선순위 |
|----|------|---------|
| F-001 | 답변 초안 생성(맥락 N메시지, 톤 옵션, 3안) | Must |
| F-002 | 초안 채택→입력창 주입(편집 후 사용자가 전송) | Must |
| F-003 | 회의 의도 감지·참석자/기간 후보 추출 | Should |
| F-004 | 참석자 일정 가용성 조회→회의 시간 후보 N개 | Should |
| F-005 | 회의 후보 채택→일정 모듈 초안 생성(forward-ref) | Could |
| F-006 | F13/일정 미완 placeholder·fallback·격리 | Must |

## 3. 기능 설계
### 3.1 데이터 모델
- `ai_reply_draft(id, tenant_id, channel_id, anchor_message_id, requester_id, tone, drafts_json, status=generated/adopted/discarded, created_at)`
- `ai_meeting_suggestion(id, tenant_id, channel_id, source_message_id, attendee_ids_json, window_start, window_end, slot_candidates_json, status, created_at)`
- 모든 엔티티 `tenant_id` RLS(F0-01) · 요청자 채널 멤버십·F0-07 검사 · 감사 F0-08
### 3.2 흐름/API
- `POST /api/v1/channels/{id}/ai/reply-draft` `{anchor_message_id, tone, context_size}` → 3안
  - 채널 최근 N메시지 + 스레드 맥락 수집 → F13 호출 → 초안 반환(전송 안 함)
- `POST /api/v1/ai/reply-draft/{id}/adopt` → 입력창 주입용 텍스트 반환(F5-02 전송은 사용자 액션)
- `POST /api/v1/channels/{id}/ai/meeting-suggest` `{message_id?, attendee_ids, window}` → 후보 슬롯
  - 참석자 = 명시 or 대화 멘션(F1-06로 사번/부서 해석) · 일정 가용성 결합 → 빈 슬롯 랭킹
- `POST /api/v1/ai/meeting-suggest/{id}/adopt` → 일정 모듈 회의 초안 생성(미완 시 placeholder 응답)
- **F13/지식 fallback**: F13 미완 시 룰베이스 템플릿 답변 + 일정은 단순 빈슬롯 계산(휴리스틱), `degraded=true` 플래그 반환
- **일정 모듈 forward-ref**: 가용성 조회는 인터페이스(`CalendarAvailabilityPort`)로 추상화, 미연동 시 mock(근무시간 09–18 기준) 반환

## 4. 개발 체크리스트
- [ ] F-001 답변초안 · [ ] F-002 채택/주입 · [ ] F-003 회의의도 · [ ] F-004 가용성/슬롯 · [ ] F-005 일정초안 · [ ] F-006 fallback/격리
- 진행률 0%

## 5. 테스트
| ID | 케이스 | 기대 |
|----|--------|------|
| T-1 | 초안 3안 생성(톤별) | 톤 반영·자동전송 없음 |
| T-2 | 채택→주입 | 입력창 텍스트 반환, 미전송 |
| T-3 | 회의 의도·참석자 추출 | 멘션 사번 해석 정확 |
| T-4 | 가용성 결합 슬롯 | 충돌 제외·랭킹 정확 |
| T-5 | F13 미완 fallback | 룰베이스+degraded |
| T-6 | 비멤버 호출(403)·격리 | 차단 |

## 7. 최종 검토
- [ ] 초안·채택·회의제안·가용성·fallback·격리 그린
