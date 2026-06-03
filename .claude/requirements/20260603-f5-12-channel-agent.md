# F5-12 — 채널 Agent 호출

## 메타데이터
- **작성일**: 2026-06-03 · **작성자**: GnoRi · AI Assistant
- **상태**: 1단계 · **규모**: standard · **레벨**: L3 · **우선순위**: medium (Wave 4)
- **상위**: [F5 마스터](./20260603-f5-messenger-master.md) · **의존**: [F5-02 실시간](./20260603-f5-02-realtime-messaging.md)·[F5-03 멘션](./20260603-f5-03-message-features.md)·[F13 AI Agent](./20260603-f13-ai-agent-master.md)·[F0-07 RBAC](./20260603-f0-07-rbac.md)·[F0-08 감사](./20260603-f0-08-audit-log.md)

## 1. 요구사항 분석
### 1.1 문제 정의
채널 대화 중 사용자가 **`@Agent` 멘션으로 AI Agent를 호출**해 질문/작업을 요청하고,
Agent가 채널 맥락을 이해해 응답·도구 실행(지식 검색·문서 초안·일정)을 수행해야 한다.
Agent는 채널의 일원처럼 메시지 스트림에 응답을 게시한다.
### 1.2 목표
- **`@Agent` 멘션 트리거** → F13 Agent 호출 · **컨텍스트=채널**(최근 N개 메시지 + 채널 메타)
- 도구 실행: 지식(F7)·문서(F8)·일정(F11) · 응답을 채널에 게시(F5-02 WS)
- F13 미완 시 **placeholder/fallback** · 권한(F0-07)·감사(F0-08)·격리

## 2. 기능 정의
| ID | 기능 | 우선순위 |
|----|------|---------|
| F-001 | `@Agent` 멘션 감지(F5-03)→ 호출 트리거 | Must |
| F-002 | 채널 컨텍스트 수집(최근 N msg + 멤버/채널명) | Must |
| F-003 | F13 Agent 호출(질의·컨텍스트 전달)·응답 수신 | Must |
| F-004 | Agent 응답 메시지 게시(sender=agent, F5-02 브로드캐스트) | Must |
| F-005 | 도구 실행 연계(지식 F7·문서 F8·일정 F11) | Should |
| F-006 | "입력 중…" 표시(typing)·스트리밍 응답 | Should |
| F-007 | F13 미완 fallback(안내 메시지·큐잉) | Must |
| F-008 | 채널별 Agent 활성/도구 허용범위 설정 | Should |

## 3. 기능 설계
### 3.1 데이터 모델
- `channel_agent_config(id, tenant_id, channel_id, agent_id, enabled, allowed_tools[], created_by)`
- `agent_invocation(id, tenant_id, channel_id, message_id, requester_id, agent_id, query, context_ref, status=queued/running/done/failed, tools_used[], response_message_id, created_at)`
- 모든 엔티티 `tenant_id` (F0-01 RLS) · Agent 응답은 일반 `message`(sender_type=agent)로 저장(F5-02)
### 3.2 흐름
1. 메시지 작성 → F5-03 멘션 파서가 `@Agent` 감지 → `agent_invocation(queued)` 생성
2. 채널 컨텍스트 수집(최근 N msg·채널 메타) → **F13 Agent 호출**(질의+컨텍스트+`allowed_tools`)
3. Agent 도구 실행(F7 지식검색·F8 문서초안·F11 할일) → 결과 종합
4. 응답을 `message(sender_type=agent)`로 저장 → F5-02 WS 브로드캐스트 → invocation `done`
### 3.3 API
- `POST /channels/{id}/agent/invoke` 명시적 호출(멘션 외 직접 호출)
- `GET /channels/{id}/agent/config` · `PUT …/config` (활성·도구범위, manage 권한)
- `GET /agent/invocations/{id}` 상태 조회
### 3.4 권한·격리·Fallback
- 호출 권한 F0-07(채널 멤버 + Agent 사용 role) · 도구별 권한은 F13/각 도메인 위임
- **F13 미완**: invoke 시 `agent_invocation(queued)` 저장 후 "AI Agent 준비 중" 안내 메시지 게시(F13 완성 후 큐 처리). 도구(F7/F8/F11 미완)는 해당 항목만 비활성 표시
- 호출·도구 실행은 F0-08 감사 기록 · 응답 메시지에 "AI 생성" 배지

## 4. 개발 체크리스트
- [ ] F-001 멘션 트리거 · [ ] F-002 컨텍스트 · [ ] F-003 F13 호출 · [ ] F-004 응답 게시
- [ ] F-005 도구연계 · [ ] F-006 typing/스트리밍 · [ ] F-007 fallback · [ ] F-008 채널 설정
- 진행률 0%

## 5. 테스트
| ID | 케이스 | 기대 |
|----|--------|------|
| T-1 | `@Agent` 멘션 호출 | invocation 생성·응답 게시 |
| T-2 | 채널 컨텍스트 전달 | 최근 N msg 포함 |
| T-3 | 도구 실행(지식/문서/일정) | 결과 종합 응답 |
| T-4 | F13 미완 fallback | 안내·큐잉 |
| T-5 | 비멤버/권한없음 호출 | 403 차단 |
| T-6 | 도구 비활성(allowed_tools) | 해당 도구 미실행 |
| T-7 | 격리(타 테넌트 채널) | 차단 |

## 7. 최종 검토
- [ ] 멘션 트리거·컨텍스트·F13 호출·응답 게시·도구연계·fallback·권한·격리 그린
