# MOAI Works

**MOAI Agent 기반 AI 업무 플랫폼** (AI-native Groupware).
그룹웨어(조직·게시판·결재…) + 메신저·메일·파일드라이브·지식관리를, **AI Agent 계층**이
가로질러 자동화·지능화하는 사내 업무 플랫폼.

> 상태: 🌱 **초기 골격** (2026-06-03) — 디렉터리 구조·설계 문서만. 실제 코드/의존성 미설치.

---

## 기술 스택 (3 컴포넌트)

| 컴포넌트 | 스택 | 책임 |
|----------|------|------|
| `moai-works-web` | **React 19 + TS + Vite** | 대시보드·메신저·문서 UI |
| `moai-works-api` | **Spring Boot** (Java/Kotlin) | 그룹웨어 도메인·권한·외부연계 |
| `moai-works-ai` | **Python + [moai-agent](../moai-agent/)** | AI Agent·RAG·문서작성·지식화·자동화 |

- DB: PostgreSQL(권장) + 벡터DB(RAG) + 오브젝트스토리지(문서) + Redis
- AI Agent 는 모노레포의 **`moai-agent`** 프레임워크 기반 (moai-echomind 와 동일 기반)

## 디렉터리 구조

```
moai-works/
├─ moai-works-api/        # Spring Boot — 그룹웨어 코어
│  └─ src/{main/java, main/resources, test/java}
├─ moai-works-web/        # React + TS — 프런트엔드
│  └─ {src, public}
├─ moai-works-ai/         # Python(moai-agent) — AI Agent 계층
│  └─ src/
├─ docs/
│  └─ architecture.md     # 아키텍처 & 전체 기능 맵
├─ .claude/               # /dev 요구사항 관리 + 스킬
│  ├─ skills/             # dev · dev-req · dev-project-auto-doc
│  └─ requirements/       # README · templates · active_development
├─ CLAUDE.md
└─ README.md
```

## 다음 단계 (골격 → 실제 프로젝트)

1. **Backend 초기화**: `moai-works-api` 에서 Spring Initializr (Web + JPA + Security…)
2. **Frontend 초기화**: `moai-works-web` 에서 `npm create vite@latest . -- --template react-ts`
3. **AI 서비스 초기화**: `moai-works-ai` 를 `moai-agent` 기반으로 구성 (echomind-api 패턴 참고)
4. **요구사항 명세**: `/dev req <name> --scale=complex` 로 단계별(P0~P5) 명세 작성

## 핵심 기능 (요약)

Home 대시보드 · **AI 메신저**(채널·요약·할일추출·Agent호출) · Mail-box · **AI 파일드라이브**
· **지식관리(RAG/Wiki)** · **AI 문서작성** · **AI 온보딩** · 그룹웨어 업무모듈(조직도·게시판·일정·
전자결재·근태·감사로그) · **AI Agent**(Main/Sub + 도메인) · 예약·자동화 · 외부연계(ERP·MCP·레거시).

→ 전체 기능 맵·레이어 구조·Agent 아키텍처·로드맵은 **[docs/architecture.md](./docs/architecture.md)**.
