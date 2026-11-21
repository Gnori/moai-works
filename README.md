# moai-works

Redmine 같은 **프로젝트 관리 시스템**(멀티 워크스페이스 SaaS). 프로젝트 · 일감 · 워크플로 · 버전과 로드맵 · 간트 · 시간 기록 · 위키 · 알림을 다룬다.

**AI Agent 를 내장하지 않는다.** Claude Code 같은 바깥 AI Agent 가 **MCP** 로 붙어, 사람과 같은 권한 규칙으로 일감을 찾고 · 만들고 · 고치고 · 시간을 기록한다. Agent 는 워크스페이스의 "Agent 계정"으로 일감 담당자가 될 수 있다.

| 모듈 | 하는 일 | 주소(로컬) |
|---|---|---|
| [`src/moai-works-api`](src/moai-works-api/README.md) | Spring Boot — DB 정본 · 권한 판정 · REST · MCP | https://localhost:11001 |
| [`src/moai-works-web-backend`](src/moai-works-web-backend/README.md) | Spring Boot BFF — 로그인 세션 · 토큰 전달 · 화면 파일 | https://localhost:11002 |
| [`src/moai-works-web-frontend`](src/moai-works-web-frontend/README.md) | React + MUI 화면 | https://localhost:11003 (개발 서버) |

- 구조 · 의존성 · 인증(Keycloak Organizations) · 역할과 권한 · 워크스페이스는 형제 저장소 moai-wiki 와 같다. 토대 코드는 거기서 가져왔다(2026-11-21).
- 지금 상태: 토대(계정 · 가입 · 워크스페이스 · 역할 · 멤버십 · 초대 · 감사 · MCP 키 · 로그인)가 돈다. 프로젝트 관리 기능은 [WBS](docs/wbs/00-overview.md) 2단계부터 만든다.

| 읽을 것 | |
|---|---|
| 일하는 규칙 | [CLAUDE.md](CLAUDE.md) · [DEVELOPE.md](DEVELOPE.md) · [SECURITY.md](SECURITY.md) · [WEB-DESIGN.md](WEB-DESIGN.md) |
| 이어서 일하기 | [HANDOFF.md](HANDOFF.md) |
| 요구사항 · 설계 · 계획 | [docs/README.md](docs/README.md) |
