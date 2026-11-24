# moai-works 설계 문서

moai-wiki 와 같은 구조의 프로젝트 관리 시스템 `moai-works` 의 요구사항 · 설계 · 계획 문서 모음이다.

> **moai-works 란?** 워크스페이스마다 프로젝트를 여러 개 두고, 프로젝트마다 일감을 관리하는 시스템이다. 형제 저장소 moai-wiki 와 모듈 구조 · 인증 · 워크스페이스 · 역할과 권한 · MCP · 흐름 · 웹이 모두 같고, **위키 대신 프로젝트, 페이지 대신 일감**이다. 일감에는 유형(요구사항 · 기능 · 버그 · 작업) · 관리자와 담당자 · 다섯 날짜(완료 기한 · 시작일 · 완료 예정일 · 완료일 · 종료일) · 상위 · 하위 일감 · 연결 일감 · 이력과 댓글이 있다. **AI Agent 를 내장하지 않는다.** Claude Code 같은 바깥 AI Agent 가 **MCP** 로 붙어 사람과 같은 권한 규칙으로 일감을 다룬다. Redmine 의 나머지 기능(워크플로 · 버전 · 시간 기록 · 위키 · 알림 …)은 [나중 후보](01-overview.md#32-나중-후보)다.

```
 사람 (브라우저) ──▶ web-frontend ──▶ web-backend (BFF · 세션 → Redis) ──REST──┐
                                                                              ▼
 바깥 AI Agent (Claude Code …) ──MCP(Streamable HTTP · MCP 키)──▶  moai-works-api ──▶ PostgreSQL
                                                                              │
                                                Keycloak (계정 · 워크스페이스 · 역할 · 프로젝트 역할의 정본)
 ────────────────────────────────────────────────────────────────────────────────────────────
 같은 서비스 · 같은 권한 판정: 웹 화면이든 MCP 도구든 api 의 같은 코드가 판정한다
```

---

## 문서 구성

### 개요

| 문서 | 내용 |
|---|---|
| [01-overview.md](01-overview.md) | 배경 · 목표 · 범위와 나중 후보 · 사용자 · moai-wiki 대응 · 핵심 시나리오 · 제약 |
| [02-glossary.md](02-glossary.md) | 용어 정의 (모든 문서는 이 용어를 따름) |
| [worklog/](worklog/README.md) | 작업 기록 — 날마다 한 문서(날짜 · 한 일 · 검증) |

### 요구사항 (`requirements/`) — 무엇을 만들어야 하는가

| 문서 | 요구사항 ID | 내용 |
|---|---|---|
| [00-overview.md](requirements/00-overview.md) | — | ID 체계 · 우선순위 · 작성 형식 · **전체 요구사항 목록** · 진행 관리 |
| [01-workspace.md](requirements/01-workspace.md) | FR-WSP | 워크스페이스 · 계정 · 멤버십 · 역할 · 감사 · 보안 원칙 (moai-wiki 와 같은 토대) |
| [02-project.md](requirements/02-project.md) | FR-PRJ | 프로젝트 · 공개 범위와 독자 판정 · 프로젝트 역할 · 기본 프로젝트 · 화면 · 감사 |
| [03-issue.md](requirements/03-issue.md) | FR-ISS | 일감 · 유형 · 상태 · 관리자와 담당자 · 날짜 · 상위 · 하위 · 연결 · 이력 · 댓글 · 목록 · 화면 |
| [04-mcp.md](requirements/04-mcp.md) | FR-MCP | **MCP** — 사용자 MCP 키 · 기능별 입구 · 워크스페이스 고르기 · 판정 · 읽기 · 쓰기 도구 · 안전장치 · 연결 안내 |
| [05-non-functional.md](requirements/05-non-functional.md) | NFR-* | 성능 · 규모 · 보안 · 품질 · 운영 · 언어 · 사용성 |
| [06-progress.md](requirements/06-progress.md) | — | 요구사항 진행 현황 (도구가 만든다) |
| [07-notification.md](requirements/07-notification.md) | FR-NOTI | **알림** — 알림함 · 무엇을 누구에게 · 아침 알림 · 받기 설정 · 화면 · 메일 |

### 설계 (`design/`) — 어떻게 만들 것인가

| 문서 | 내용 |
|---|---|
| [00-key-decisions.md](design/00-key-decisions.md) | 핵심 설계 결정 기록 (ADR) |
| [01-architecture.md](design/01-architecture.md) | 전체 구성 · 모듈 · 호출 흐름 · 저장소 · 기술 스택 |
| [02-data-model.md](design/02-data-model.md) | 데이터 모델 한눈에 (표 목록 · 관계 · 정본 위치) |
| [03-workspace-identity.md](design/03-workspace-identity.md) | 워크스페이스 · 계정 · 권한 (Keycloak 정본, 가입 · 초대 · 기본 역할 넷 · 권한 코드) — moai-wiki 와 같은 토대 |
| [04-project.md](design/04-project.md) | 프로젝트 — 공개 범위 · 독자 판정 · 프로젝트 역할 · Keycloak 그룹 · REST · 화면 (moai-wiki 의 위키 자리) |
| [05-issue.md](design/05-issue.md) | 일감 — 유형 · 상태와 날짜 · 관리자 · 담당자 · 상위 · 하위 · 연결 · 이력 · 댓글 · 목록 · REST · 화면 (moai-wiki 의 페이지 자리) |
| [06-mcp.md](design/06-mcp.md) | **MCP** — 사용자 MCP 키(워크스페이스에 묶지 않음 — ADR-029) · 기능별 입구 · 워크스페이스 고르기 · 판정 · **도구 목록(§8 — 지금 도구의 설명 · 입력 · 결과 · 오류, 일감 입구 22 · 프로젝트 관리 입구 7)** · 안전장치 |
| [07-notification.md](design/07-notification.md) | **알림** — 알림함 · 종류(무엇을 누구에게) · 아침 알림 · 받기 설정 · 화면 (moai-wiki 알림함 + 일감 · 작업 시간 · MCP 키) |
| [08-skill.md](design/08-skill.md) | **Agent 스킬** — 시스템 관리자가 등록 · 수정 · 삭제 · 리비전(되돌리기), 누구나 읽기 · MCP `list_skills` · `get_skill` · SKILL.md 내보내기 · 가져오기 · 받기 도구(`tools/sync_skills.py`) · 프롬프트 주입 경계 (2026-11-24) |

### 모듈 (`modules/`) — 지금 코드가 어떻게 나뉘어 있는가

| 문서 | 내용 |
|---|---|
| [00-overview.md](modules/00-overview.md) | 모듈 한눈에 — 세 모듈의 경계 · Keycloak 클라이언트 · 로컬 서비스 · `infra/` |
| [01-api.md](modules/01-api.md) | `moai-works-api` — DB 정본 · 권한 판정 · REST · MCP |
| [02-web-backend.md](modules/02-web-backend.md) | `moai-works-web-backend` — 로그인 세션 BFF |
| [03-web-frontend.md](modules/03-web-frontend.md) | `moai-works-web-frontend` — 웹 화면 |

### 규칙 문서 (저장소 맨 위) — 개발할 때 따르는 것

| 문서 | 내용 |
|---|---|
| [CLAUDE.md](../CLAUDE.md) | 핵심 규칙과 "언제 무엇을 읽는가" |
| [DEVELOPE.md](../DEVELOPE.md) | 개발 규칙 — 모듈 경계 · 버전 · 코드 · 설정과 비밀값 · 로컬 환경 · 검사 · 문서 현행화 · 커밋 |
| [SECURITY.md](../SECURITY.md) | 보안 · 인증 구조 — Keycloak · 로그인 세션 · CSRF · JWT · MCP 키 · 접근 어노테이션 · 권한 상승 방지 · 비밀값 · 감사 (구현 기준) |
| [WEB-DESIGN.md](../WEB-DESIGN.md) | 웹 디자인 규칙 — 테마 · 스킨 · 표면 · 반응형 · 다국어 · 경로와 메뉴 · 글 |
| [HANDOFF.md](../HANDOFF.md) | 세션 인계 — 현재 상태 · 남은 일 · 작업 기록 |

### 계획 (`plan/`) — 어떤 순서로 만들 것인가

| 문서 | 내용 |
|---|---|
| [01-roadmap.md](plan/01-roadmap.md) | 단계 · 왜 이 순서인가 · 완료 기준 · 첫 출시(MVP) 범위 · 미루는 것 |
| [02-test-strategy.md](plan/02-test-strategy.md) | 테스트 층 · 꼭 있어야 하는 시험 · MCP 시험 · 수용 기준 체크 |

### WBS (`wbs/`) — 지금 무엇을 했고 다음에 무엇을 하는가

| 문서 | 내용 |
|---|---|
| [00-overview.md](wbs/00-overview.md) | WBS 한눈에 — 지금 위치 · 단계 · 작업 하나의 순서 · 표기 |
| [01-implemented.md](wbs/01-implemented.md) | 구현한 기능 — 모듈별 · 요구사항 연결 |
| [02-stage-1-foundation.md](wbs/02-stage-1-foundation.md) | 1단계 토대 — moai-wiki 에서 가져온 계정 · 워크스페이스 · 권한 (코드 끝남, 화면 흐름 확인 남음) |
| [03-stage-2-project.md](wbs/03-stage-2-project.md) | 2단계 프로젝트 — 권한 코드 · 큐레이터 · 프로젝트 · 프로젝트 역할 (**다음 순서**) |
| [04-stage-3-issue.md](wbs/04-stage-3-issue.md) | 3단계 일감 — 유형 · 번호 · 만들기 · 고치기 · 이력 · 댓글 · 상위 · 하위 · 연결 · 목록 · 화면 |
| [05-stage-4-mcp.md](wbs/05-stage-4-mcp.md) | 4단계 MCP — 사용자 키 · 입구 · 워크스페이스 고르기 · 읽기 · 쓰기 도구 · Claude Code 시연 |
| [06-stage-5-collaboration.md](wbs/06-stage-5-collaboration.md) | 5단계 협업 — 나중 후보에서 고르기 (첨부 · 알림 · 활동 · 검색) |
| [07-stage-6-operations.md](wbs/07-stage-6-operations.md) | 6단계 운영 — 규모 시험 · 운영 준비 · 운영 보안 · Redmine 이관 |
| [08-decisions.md](wbs/08-decisions.md) | 정해야 할 것 |

---

## 읽는 순서

| 독자 | 추천 순서 |
|---|---|
| 처음 보는 사람 · 의사결정자 | 01-overview → design/00-key-decisions → design/01-architecture → plan/01-roadmap → wbs/08-decisions |
| 기획 · PM | 01-overview → requirements/00-overview → 각 요구사항 문서 → plan/* → wbs/* |
| 개발자 | CLAUDE.md → DEVELOPE.md → wbs/00-overview → modules/00-overview → 담당 모듈 문서 → 02-glossary → design/01 · 02 → 담당 설계 문서 (권한은 design/03 · 04 + SECURITY.md, 일감은 design/05, MCP 는 design/06, 화면은 WEB-DESIGN.md) → 같은 기능의 moai-wiki 코드 |
| AI Agent 를 붙이려는 사람 | requirements/04-mcp → design/06-mcp → SECURITY.md 의 MCP 키 절 |
| 보안 담당 | SECURITY.md(구현) → requirements/01-workspace → design/03 → design/04 → design/06 |

## 문서 작성 규칙

### 위키 형식

이 문서 모음은 위키처럼 페이지끼리 링크로 연결한다(moai-wiki 와 같은 형식).

- **한 페이지 = 한 주제.** 페이지가 길어지면 하위 페이지로 나누고 상위 페이지에서 링크한다.
- **상단 경로:** 모든 페이지 첫 줄에 `[홈](...) › [영역](...) › 현재 페이지` 형식의 경로를 둔다.
- **하단 이동:** 모든 페이지 끝에 `관련 문서`와 `← 이전 | 다음 →` 링크를 둔다. 순서는 README → 01 → 02 → requirements → design → modules → plan → wbs → README 다.
- **ID 바로가기:** 요구사항 · 결정 기록 앞에는 `<a id="fr-iss-001"></a>`처럼 소문자 ID 앵커를 둔다.
- **본문 링크:** 다른 페이지의 개념 · 요구사항 · 설계를 언급하면 해당 페이지(또는 앵커)로 링크한다. 용어는 [용어집](02-glossary.md)으로 링크한다.

### 내용 규칙

- **용어:** [02-glossary.md](02-glossary.md)의 용어를 쓴다. 새 용어가 생기면 용어집에 먼저 더한다.
- **요구사항 ID:** `FR-<영역>-<번호>`(기능), `NFR-<분류>-<번호>`(비기능). 한 번 부여한 ID 는 바꾸지 않고, 없앨 때는 진행 줄을 `폐기`로 한다.
- **우선순위:** `M`(첫 출시에 꼭) · `S`(곧) · `C`(나중) · `W`(이번 범위 밖). 상세는 [requirements/00-overview.md](requirements/00-overview.md). 첫 출시 범위 밖의 기능은 요구사항으로 쓰지 않고 [나중 후보](01-overview.md#32-나중-후보) 목록에만 둔다.
- **진행:** 요구사항마다 `진행` 줄 하나와 수용 기준 체크. 모아 보기는 도구가 만든다([요구사항 개요 §6](requirements/00-overview.md#6-진행-관리)).
- **추적성:** 설계 문서는 `관련 요구사항`에 ID 를 적고, 요구사항 문서는 `관련 설계`에 설계 문서 위치를 적는다. WBS 작업은 `요구사항` 칸에 ID 를 적는다.
- **결정 기록:** 설계를 바꾸는 결정은 [design/00-key-decisions.md](design/00-key-decisions.md)에 ADR 로 남긴다.
- **다이어그램:** 개요는 텍스트 그림, 흐름 · 상태는 Mermaid.
- **사람 · 비밀값:** 사람 이름 · 비밀값은 적지 않는다(사용자는 "사람" · "사용자").
- **검사:** 저장소 맨 위에서 `python3 -I tools/check_docs.py docs` → 0건.

## 문서 상태

| 항목 | 값 |
|---|---|
| 버전 | v0.2 — moai-wiki 와 같게 다시 정리했다(위키 → 프로젝트, 페이지 → 일감, 일감 모델만 더함) |
| 작성일 | 2026-11-22 |
| 이전 | v0.1(2026-11-21)은 Redmine 을 그대로 옮긴 큰 범위(요구사항 254개 · 여섯 단계 · "Agent 계정")였다. 그 앞의 2026-06 "AI-native 그룹웨어" 명세(143개)는 git 이력 `5ba3a92` 에 있다. v0.1 도 git 이력에 남아 있다 |
