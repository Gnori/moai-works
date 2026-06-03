# F13-06 — 컨텍스트 / 권한 전파 (테넌트 · 사용자 · RBAC)

## 메타데이터
- **작성일**: 2026-06-03 · **작성자**: GnoRi · AI Assistant
- **상태**: 1단계 · **규모**: complex · **레벨**: L3 · **우선순위**: **critical** (Wave 4 · 보안 핵심)
- **상위**: [F13 마스터](./20260603-f13-ai-agent-master.md) · **의존**: [F13-01 서비스토대](./20260603-f13-01-ai-service-foundation.md)·[F0-01 격리](./20260603-f0-01-tenant-isolation.md)·[F0-07 RBAC](./20260603-f0-07-rbac.md)·[F0-03 인증](./20260603-f0-03-authentication.md) · **참고: echomind** `set_recall_context` + SNS tools 가드

## 1. 요구사항 분석
### 1.1 문제 정의
AI 도구가 코어 데이터(문서·메시지·결재…)에 접근하므로, **테넌트 격리·사용자 권한이 AI 호출
전 구간에 강제**돼야 한다. 컨텍스트가 없거나 경계를 벗어나면 **도구는 거부**한다(echomind 의
cross-account 누출 교훈).
### 1.2 목표
- 코어(Java) → ai REST 시 **서비스 인증(서비스 JWT)** + **컨텍스트(tenant·user·roles) 전달**
- ai 내부에서 **per-turn 컨텍스트 contextvar 주입**(echomind `set_recall_context` 패턴)
- 모든 Tool `_arun` 진입부에서 **컨텍스트/권한 가드** — 없거나 권한 부족이면 거부
- 도구가 코어 API 재호출 시 사용자 토큰/권한 위임(on-behalf)

### 1.3 제약
- 기본 deny · 테넌트 경계는 코어(F0-01 RLS)와 ai(가드) 이중 · 감사(F0-08)

## 2. 기능 정의
| ID | 기능 | 우선순위 |
|----|------|---------|
| F-001 | api↔ai 서비스 인증(서비스 JWT) | Must |
| F-002 | 컨텍스트 전달(tenant·user·roles) | Must |
| F-003 | ai 내부 per-turn 컨텍스트 contextvar | Must |
| F-004 | Tool 가드(컨텍스트/권한 없으면 거부) | Must |
| F-005 | 도구→코어 재호출 권한 위임(on-behalf) | Must |

## 2.2 상세
- **F-002/003**: `/agent/invoke` 의 컨텍스트 → contextvar(`AgentContext{tenant_id, user_id, roles, request_id}`). echomind `set_recall_context(ToolContext)` 와 동형.
- **F-004**: 각 Tool 진입부 `ctx = current_agent_context(); if ctx is None or not allowed(ctx, perm): return 거부`. (echomind SNS tools 의 `_guard`/proactive 차단 패턴)
- **F-005**: 도구가 코어 API 호출 시 사용자 컨텍스트의 토큰/권한으로 — 코어가 다시 F0-01/07 검증(이중).

## 3. 기능 설계
### 3.1 흐름
```
코어 → POST /agent/invoke (서비스JWT + context{tenant,user,roles})
 → [검증] 서비스 인증·컨텍스트 set(contextvar)
 → Agent/Tool 실행 — Tool 진입 가드(컨텍스트·권한)
 → 도구→코어 API(on-behalf, 코어가 F0-01/07 재검증)
 → 감사(F0-08)
```

## 4. 개발 체크리스트
- [ ] F-001 서비스 인증 · [ ] F-002 컨텍스트 전달 · [ ] F-003 contextvar · [ ] F-004 Tool 가드 · [ ] F-005 on-behalf
- 진행률 0%

## 5. 테스트
| ID | 케이스 | 기대 |
|----|--------|------|
| T-1 | 정상 컨텍스트 | 도구 실행 |
| T-2 | 컨텍스트 없음 | **도구 거부** |
| T-3 | 권한 부족 | 거부(403) |
| T-4 | 타 테넌트 데이터 접근 시도 | 차단(이중) |
| T-5 | 서비스 인증 없는 호출 | 거부 |
| T-6 | 동시 요청 컨텍스트 격리 | 누수 없음 |

## 7. 최종 검토
- [ ] 서비스 인증·컨텍스트 전파·Tool 가드·on-behalf·이중 격리 그린 (echomind 교훈 반영)
