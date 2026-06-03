# F13 — AI Agent 플랫폼 마스터 (moai-agent 기반)

## 메타데이터
- **작성일**: 2026-06-03 · **작성자**: GnoRi · AI Assistant
- **상태**: 1단계 — 요구사항 분석 (L1 도메인 마스터) · **규모**: complex(마스터)
- **우선순위**: **critical** (Wave 4 — AI 계층의 심장. F5/F6/F8/F9/H가 모두 수렴)
- **연관**: 의존 [F0 토대](./20260603-f0-saas-foundation-master.md)·[F1 조직](./20260603-f1-organization-master.md) · 연계 [F7 지식](./20260603-f7-knowledge-master.md)·[F8 문서](./20260603-f8-ai-document-master.md)·[F5 메신저](./20260603-f5-messenger-master.md)·[F6 메일](./20260603-f6-mailbox-master.md)·[F14 자동화](./20260603-f14-automation-master.md)
- **참고 구현 패턴**: 형제 프로젝트 [moai-echomind](../../../moai-echomind/) 의 Brain/Agent/Tool (LangGraph tool-calling·Tool 레지스트리·컨텍스트 전파)

## 1. 요구사항 분석
### 1.1 문제 정의
제품의 AI 기능(메신저 요약·할일추출·문서작성·온보딩·메일·일정·결재 보조)은 흩어진 기능이
아니라, **moai-agent(LangGraph) 기반 Agent 계층**으로 통합돼야 한다. Main Agent가 의도를
파악해 도메인 Agent/도구로 위임하고, 모든 호출은 **테넌트·사용자·권한 컨텍스트**를 안전하게
전파한다.

### 1.2 목표
- **moai-works-ai**(Python + moai-agent) 서비스 — 그룹웨어 코어(Spring Boot)와 REST 분리
- **Main / Sub Agent 오케스트레이션** + **도메인 Agent**(지식·문서·메신저·이메일·일정·결재·온보딩·예약·레거시)
- **Tool 레지스트리**(LangChain Tool 바인딩) — 각 도메인 기능을 Agent 가 tool-call
- **컨텍스트/권한 전파**: 테넌트 격리(F0-01)·RBAC(F0-07)를 AI 호출 전 구간에 강제
- 모델 추상화(온프레미스/클라우드)·관측성·미터링(F0-09)·안전 가드레일

### 1.3 제약/결정 (architecture §6)
- AI = **별도 Python 서비스**(moai-works-ai). Spring Boot 코어 ↔ ai = 내부 REST(+ 서비스 인증)
- moai-echomind 의 agent/brain/tool wireup 패턴 재사용 · 모델: LLM 게이트웨이(공급자 추상)
- **테넌트 경계는 AI 도구에도 강제** (echomind 의 cross-account 가드 교훈 반영)

## 2. 기능 정의 — L3 분해
| L3 ID | spec | 규모 | 내용 |
|-------|------|------|------|
| **F13-01** | AI 서비스 토대(moai-works-ai) | complex | moai-agent wireup·FastAPI 게이트웨이·api↔ai 계약 |
| **F13-02** | Main Agent 오케스트레이션 | complex | 의도 분류·라우팅·도메인 Agent 위임 |
| **F13-03** | Sub Agent(작업 분할) | standard | 복합작업 하위 단계 분할·합성 |
| **F13-04** | Tool 레지스트리/플러그인 | complex | LangChain Tool 바인딩·도메인 도구 등록·tool-call |
| **F13-05** | 도메인 Agent 세트 | complex | 지식·문서·메신저·이메일·일정·결재·온보딩 Agent |
| **F13-06** | 컨텍스트/권한 전파 | complex | 테넌트·사용자·RBAC → AI 호출 강제·서비스 인증 |
| **F13-07** | 대화/세션 메모리 | standard | 세션·단기/장기 메모리·컨텍스트 조립 |
| **F13-08** | 모델/프로바이더 추상화 | standard | LLM 게이트웨이·온프레미스/클라우드·라우팅 |
| **F13-09** | 관측성/미터링 | standard | 토큰·비용 미터(F0-09)·trace(Jaeger)·로깅 |
| **F13-10** | 안전 / 가드레일 | standard | 테넌트 격리·권한 강제·콘텐츠 안전·도구 남용 방지 |

## 3. 기능 설계 (토대)
### 3.1 아키텍처
```
[Spring Boot 코어] ──내부 REST(서비스 JWT)──▶ [moai-works-ai (Python/moai-agent)]
   (요청 + 테넌트·사용자·권한 컨텍스트)            ├─ Main Agent (의도→라우팅)
                                                  ├─ 도메인 Agent (지식/문서/메신저/메일/일정/결재/온보딩)
                                                  ├─ Tool 레지스트리 (LangChain Tool)
                                                  │    └─ 도구는 코어 API/F7 RAG/F4 문서 호출(권한 검증)
                                                  └─ 모델 게이트웨이(LLM) · 메모리 · 관측성
```
### 3.2 핵심 패턴 (moai-echomind-api 연동 방식 참고 — 실증된 레퍼런스)
moai-echomind-api 가 moai-agent 를 어떻게 붙였는지가 **moai-works-ai 의 직접 청사진**이다
(차이: echomind-api 는 Python(FastAPI)이 직접 호스팅, **moai-works 는 코어가 Java라
moai-works-ai 를 별도 Python 서비스로 두고 REST 로 호출**):

| echomind-api 메커니즘 (파일) | moai-works-ai 적용 |
|------------------------------|--------------------|
| `core/agent.py` `AgentLoader().load_agent()` → `agent_info.tools.extend(create_recall_tools())` → `Agent(agent_info, recursion_limit)` (싱글톤) | moai-works-ai 에서 Agent 로드 + **도메인 Tool 바인딩** |
| `core/brain.py` `BrainClient(agent, memory, session_factory, persist_callback)` (싱글톤) | Brain 래퍼 + 세션/메모리 |
| `Brain.process(stimulus_type, ...)` → `agent.ainvoke_stream(message, message_history, instruction, images)` → `tool_start/tool_end/delta/result` | 통합 진입점·스트리밍 |
| `agent_tools.py` LangChain `BaseTool` 래퍼 + **`set_recall_context(ToolContext)` contextvar** per-turn | **도메인 Tool + 컨텍스트(tenant·user·권한) contextvar 주입** |
| SNS action tools 의 **cross-account/컨텍스트 가드**(없으면 도구 거부) | **테넌트 경계 가드**(컨텍스트 없으면 거부) |

- 즉 **Tool 레지스트리 + LangChain Tool + per-turn 컨텍스트 contextvar + 가드** 패턴을 그대로 채택.
- 차이의 핵심: **api↔ai REST 경계**에서 **서비스 인증 + 컨텍스트(tenant/user/roles) 전달**(F13-06).
### 3.3 통합점
- F7 지식(RAG 도구) · F8 문서(생성 도구) · F5/F6(메신저·메일 도구) · F11 일정/업무 · F10 결재 · F14 예약

## 4. 개발 진행 (L3)
- [ ] F13-01 서비스토대 → F13-06 컨텍스트/권한 → F13-04 Tool레지스트리 → F13-02 Main → F13-05 도메인Agent → F13-03 Sub → F13-07 메모리 → F13-08 모델 → F13-09 관측성 → F13-10 안전

## 9. 완료 기준 (마스터)
- [ ] moai-works-ai 가동 · Main/도메인 Agent·Tool 레지스트리 동작
- [ ] **모든 AI 도구가 테넌트·권한 컨텍스트 강제**(격리·RBAC) · 미준수 시 거부
- [ ] F5/F6/F8/F9/H 의 AI 기능이 본 플랫폼 위에서 동작 · 관측성·미터링·안전
