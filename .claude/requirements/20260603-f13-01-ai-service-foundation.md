# F13-01 — AI 서비스 토대 (moai-works-ai · moai-agent wireup)

## 메타데이터
- **작성일**: 2026-06-03 · **작성자**: GnoRi · AI Assistant
- **상태**: 1단계 · **규모**: complex · **레벨**: L3 · **우선순위**: critical (Wave 4)
- **상위**: [F13 AI Agent 마스터](./20260603-f13-ai-agent-master.md) · **의존**: [F0-03 인증](./20260603-f0-03-authentication.md)(서비스 인증) · **참고: moai-echomind-api** `core/agent.py`·`core/brain.py`

## 1. 요구사항 분석
### 1.1 문제 정의
AI 계층은 **별도 Python 서비스 `moai-works-ai`** 로 둔다(코어는 Java). moai-agent(LangGraph)를
호스팅하고, Spring Boot 코어가 **내부 REST**로 호출한다. 본 spec 은 그 **서비스 골격 + moai-agent
wireup + api↔ai 계약**을 만든다.
### 1.2 목표
- `moai-works-ai`(Python + **FastAPI**) — moai-agent 로딩·Agent/Brain 구성(싱글톤)
- **api↔ai REST 계약**: `POST /agent/invoke`(스트리밍) — 입력(요청·컨텍스트)·출력(델타·tool 이벤트·결과)
- 헬스/구성·관측성 기반(F13-09) · 서비스 인증(F13-06)

### 1.3 제약/참고
- **echomind-api 패턴 채택**: `AgentLoader().load_agent()` → `agent_info.tools.extend(...)` →
  `Agent(agent_info, recursion_limit)`; `BrainClient(agent, memory, session_factory)` (둘 다 싱글톤)
- moai-agent·FastAPI · 모델 게이트웨이(F13-08)

## 2. 기능 정의
| ID | 기능 | 우선순위 |
|----|------|---------|
| F-001 | moai-works-ai FastAPI 서비스 골격 | Must |
| F-002 | moai-agent 로딩·Agent 구성(싱글톤) | Must |
| F-003 | Brain 래퍼(process 통합 진입·스트리밍) | Must |
| F-004 | api↔ai REST 계약(`/agent/invoke` 스트리밍) | Must |
| F-005 | 헬스/구성·기동 wireup | Should |

## 2.2 상세
- **F-002**: echomind `core/agent.py` 처럼 AgentLoader → agent_info → **도메인 Tool 바인딩**(F13-04) → Agent. recursion_limit 설정.
- **F-003**: `Brain.process(stimulus_type, message, context, ...)` → `agent.ainvoke_stream(...)` → `tool_start/tool_end/delta/result` 이벤트 (echomind brain.py 패턴).
- **F-004**: 코어 → ai `POST /agent/invoke{stimulus, message, context(tenant·user·roles), tools_scope}` → SSE/스트리밍 응답.

## 3. 기능 설계
### 3.1 스택/구조
```
moai-works-ai/
├─ api/        # FastAPI (/agent/invoke 등)
├─ agents/     # Main/도메인 Agent (moai-agent)
├─ tools/      # LangChain Tool (F13-04)
├─ core/       # agent/brain wireup (echomind 패턴), 모델 게이트웨이
└─ context/    # 컨텍스트 contextvar (F13-06)
```
### 3.2 흐름
```
코어(Java) → POST /agent/invoke(+컨텍스트) → [컨텍스트 set(F13-06)]
 → Brain.process → agent.ainvoke_stream(tool-call) → 스트리밍 응답
```

## 4. 개발 체크리스트
- [ ] F-001 FastAPI 골격 · [ ] F-002 Agent 구성 · [ ] F-003 Brain 래퍼 · [ ] F-004 invoke 계약 · [ ] F-005 헬스/기동
- 진행률 0%

## 5. 테스트
| ID | 케이스 | 기대 |
|----|--------|------|
| T-1 | /agent/invoke 호출 | 스트리밍 응답·tool 이벤트 |
| T-2 | Agent 싱글톤 기동 | 정상 로드 |
| T-3 | 서비스 인증 없는 호출 | 거부(F13-06) |
| T-4 | 모델 게이트웨이 연결 | 응답 생성 |

## 7. 최종 검토
- [ ] moai-works-ai 가동·moai-agent wireup·invoke 계약·스트리밍 그린 (echomind 패턴 준수)
