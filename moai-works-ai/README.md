# moai-works-ai

MOAI Works **AI Agent 계층** — Python + [`moai-agent`](../../moai-agent/) 기반 서비스.

> 🌱 골격만 구성됨. 아직 의존성·소스 없음.

## 역할

그룹웨어 코어(`moai-works-api`, Spring Boot)의 AI 요청을 받아 처리하는 **별도 Python 서비스**.
moai-agent(LangGraph) 로 Agent·Tool·RAG 를 실행한다 (moai-echomind 의 Brain/Agent 패턴 재사용).

- Main/Sub Agent 오케스트레이션
- 도메인 Agent: 지식·문서작성·메신저·이메일·일정·전자결재·온보딩·예약작업·레거시
- 지능형 RAG · LLM Wiki · 문서 지식화 · OCR/문서분석
- 예약·자동화 (스케줄·조건 기반 Agent 실행)

## 구조 (예정)

```
moai-works-ai/
├─ src/                 # agents / rag / tools / api(FastAPI 등)
├─ pyproject.toml       # ← 초기화 시 생성 (moai-agent 의존)
└─ README.md
```

## 초기화 (착수 시)

- Python 3.12 + `moai-agent` 의존 추가
- API 레이어(FastAPI 등)로 `moai-works-api` ↔ 내부 REST 연동
- echomind-api 의 agent/brain/tool wireup 참고
