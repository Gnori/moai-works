# F13-09 — 관측성 / 미터링

## 메타데이터
- **작성일**: 2026-06-03 · **작성자**: GnoRi · AI Assistant
- **상태**: 1단계 · **규모**: standard · **레벨**: L3 · **우선순위**: high (Wave 4)
- **상위**: [F13 마스터](./20260603-f13-ai-agent-master.md) · **의존**: [F13-01](./20260603-f13-01-ai-service-foundation.md)·[F0-09 구독/quota](./20260603-f0-09-subscription-quota.md) · 인프라 Prometheus/Grafana/Jaeger(재사용)

## 1. 요구사항 분석
### 1.1 문제 정의
AI 호출은 **비용(토큰)**과 **추적(trace)**이 필수다. Agent/도구 호출의 토큰·비용을 미터링하고
(F0-09 quota 연동), 분산 추적·로깅으로 가시화한다.
### 1.2 목표
- **토큰·비용 미터링**(테넌트/사용자/기능별) → F0-09 quota 차감·한도
- **분산 추적**(Jaeger/OTel) · 도구 호출 로그 · 에러/지연 지표(Prometheus/Grafana 재사용)

## 2. 기능 정의
| ID | 기능 | 우선순위 |
|----|------|---------|
| F-001 | 토큰·비용 미터(기능/테넌트별) → F0-09 | Must |
| F-002 | trace(요청→Agent→도구→LLM) | Must |
| F-003 | 도구/에러/지연 지표 | Should |
| F-004 | AI quota 초과 차단 | Must |

## 3. 기능 설계
### 3.1 설계
미터 수집(LLM/도구별) → usage_records(F0-09) · OTel trace(Jaeger) · Prometheus 지표 · (echomind 미터링 패턴 참고)

## 4. 개발 체크리스트
- [ ] F-001 미터 · [ ] F-002 trace · [ ] F-003 지표 · [ ] F-004 quota 차단
- 진행률 0%

## 5. 테스트
| ID | 케이스 | 기대 |
|----|--------|------|
| T-1 | 호출 미터링 | 토큰·비용 집계 |
| T-2 | quota 초과 | AI 호출 차단 |
| T-3 | trace | 요청 체인 가시화 |

## 7. 최종 검토
- [ ] 미터링·trace·지표·quota 차단 그린 (관측성 스택 재사용)
