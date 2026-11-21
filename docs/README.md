# moai-works 설계 문서

Redmine 같은 프로젝트 관리 시스템 `moai-works` 의 요구사항 · 설계 · 계획 문서 모음이다.

> **moai-works 란?** 워크스페이스마다 프로젝트 · 일감 · 워크플로 · 버전과 로드맵 · 간트 · 시간 기록 · 위키 · 알림을 다루는 프로젝트 관리 시스템이다. **AI Agent 를 내장하지 않는다.** Claude Code 같은 바깥 AI Agent 가 **MCP** 로 붙어 사람과 같은 권한 규칙으로 일한다. 구조 · 의존성 · 인증 · 역할과 권한 · 워크스페이스는 형제 저장소 moai-wiki 와 같다.

```
 사람 (브라우저) ──▶ web-frontend ──▶ web-backend (BFF · 세션) ──REST──┐
                                                                      ▼
 바깥 AI Agent (Claude Code …) ──MCP(Streamable HTTP · MCP 키)──▶  moai-works-api ──▶ PostgreSQL · MinIO · OpenSearch · Redis
                                                                      │
                                                Keycloak (계정 · 워크스페이스 · 역할의 정본)
 ────────────────────────────────────────────────────────────────────────────────────────────
 같은 서비스 · 같은 권한 판정: 웹 화면이든 MCP 도구든 api 의 같은 코드가 판정한다
```

---

## 문서 구성

### 개요

| 문서 | 내용 |
|---|---|
| [01-overview.md](01-overview.md) | 배경 · 목표 · 범위 · 사용자 · 핵심 시나리오 · Redmine 대응 · 제약 |
| [02-glossary.md](02-glossary.md) | 용어 정의 (모든 문서는 이 용어를 따름) |

### 요구사항 (`requirements/`) — 무엇을 만들어야 하는가

| 문서 | 요구사항 ID | 내용 |
|---|---|---|
| [00-overview.md](requirements/00-overview.md) | — | ID 체계 · 우선순위 · 작성 형식 · **전체 요구사항 목록** · 진행 관리 |
| [01-workspace.md](requirements/01-workspace.md) | FR-WSP | 워크스페이스 · 계정 · 멤버십 · 역할 · 감사 · 보안 원칙 |
| [02-project.md](requirements/02-project.md) | FR-PRJ | 프로젝트 · 하위 프로젝트 · 모듈 · 멤버 · 프로젝트 역할 |
| [03-issue.md](requirements/03-issue.md) | FR-ISS | 일감 · 이력과 메모 · 하위 일감 · 관계 · 관찰자 · 첨부 · 비공개 · 일괄 편집 |
| [04-workflow.md](requirements/04-workflow.md) | FR-WFL | 트래커 · 상태 · 열거형 · 워크플로 · 필드 권한 · 사용자 정의 필드 · 카테고리 |
| [05-query.md](requirements/05-query.md) | FR-QRY | 필터 · 저장한 질의 · 목록 · 내보내기 · 칸반 보드 |
| [06-planning.md](requirements/06-planning.md) | FR-PLN | 버전 · 로드맵 · 간트 · 캘린더 |
| [07-time.md](requirements/07-time.md) | FR-TIM | 시간 기록 · 작업 분류 · 보고서 |
| [08-content.md](requirements/08-content.md) | FR-CNT | 위키 · 뉴스 · 문서 · 파일 · 포럼 |
| [09-notification.md](requirements/09-notification.md) | FR-NTF | 알림 · 메일 보내기 · 메일로 일감 · 화면 알림 |
| [10-activity-search.md](requirements/10-activity-search.md) | FR-ACT | 활동 · 검색 · 내 페이지 |
| [11-integration.md](requirements/11-integration.md) | FR-INT | REST API · 웹훅 · Git 저장소 연동 · 가져오기 · Redmine 이관 |
| [12-mcp.md](requirements/12-mcp.md) | FR-MCP | **MCP · AI Agent** — 엔드포인트 · 키 · Agent 계정 · 도구 · 안전장치 |
| [13-non-functional.md](requirements/13-non-functional.md) | NFR-* | 성능 · 규모 · 보안 · 품질 · 운영 |
| [14-progress.md](requirements/14-progress.md) | — | 요구사항 진행 현황 (도구가 만든다) |

### 설계 (`design/`) — 어떻게 만들 것인가

| 문서 | 내용 |
|---|---|
| [00-key-decisions.md](design/00-key-decisions.md) | 핵심 설계 결정 기록 (ADR) |
| [01-architecture.md](design/01-architecture.md) | 전체 구성 · 모듈 · 호출 흐름 · 저장소 · 기술 스택 |
| [02-data-model.md](design/02-data-model.md) | 데이터 모델 한눈에 (표 목록 · 관계 · 정본 위치) |
| [03-workspace-identity.md](design/03-workspace-identity.md) | 워크스페이스 · 계정 · 권한 (Keycloak 정본, 가입 · 초대 · 역할) — moai-wiki 와 같은 토대 |
| [04-project-permissions.md](design/04-project-permissions.md) | 프로젝트 · 공개 범위 · 프로젝트 역할 · 권한 코드 · 판정 규칙 |
| [05-issue-tracking.md](design/05-issue-tracking.md) | 일감 · 이력 · 관계 · 워크플로 엔진 · 사용자 정의 필드 · 질의 엔진 · 보드 |
| [06-planning-time.md](design/06-planning-time.md) | 버전 · 로드맵 · 간트 · 캘린더 · 시간 기록 · 보고서 |
| [07-mcp-agents.md](design/07-mcp-agents.md) | **MCP · AI Agent** — 엔드포인트 · 키 · Agent 계정 · 도구 카탈로그 · 안전장치 |
| [08-collaboration.md](design/08-collaboration.md) | 위키 · 뉴스 · 문서 · 파일 · 포럼 · 알림 · 메일 · 활동 · 검색 |
| [09-integrations.md](design/09-integrations.md) | REST API 규약 · 웹훅 · Git 연동 · 가져오기 · Redmine 이관 |

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
| [01-roadmap.md](plan/01-roadmap.md) | 단계 · 범위 · 완료 기준 · 첫 출시(MVP) 범위 |
| [02-test-strategy.md](plan/02-test-strategy.md) | 테스트 · 인수 기준 · MCP 시험 |

### WBS (`wbs/`) — 지금 무엇을 했고 다음에 무엇을 하는가

| 문서 | 내용 |
|---|---|
| [00-overview.md](wbs/00-overview.md) | WBS 한눈에 — 지금 위치 · 단계 · 작업 하나의 순서 · 표기 |
| [01-implemented.md](wbs/01-implemented.md) | 구현한 기능 — 모듈별 · 요구사항 연결 |
| [02-stage-1-foundation.md](wbs/02-stage-1-foundation.md) | 1단계 토대 — moai-wiki 에서 가져온 계정 · 워크스페이스 · 권한 (끝남, 실제 흐름 확인 남음) |
| [03-stage-2-project-issue.md](wbs/03-stage-2-project-issue.md) | 2단계 프로젝트 · 일감 코어 (**다음 순서**) |
| [04-stage-3-mcp.md](wbs/04-stage-3-mcp.md) | 3단계 MCP — 바깥 AI Agent 연결 |
| [05-stage-4-planning-time.md](wbs/05-stage-4-planning-time.md) | 4단계 계획 · 시간 · 보드 |
| [06-stage-5-collaboration.md](wbs/06-stage-5-collaboration.md) | 5단계 협업 — 위키 · 알림 · 활동 · 검색 |
| [07-stage-6-integration.md](wbs/07-stage-6-integration.md) | 6단계 연동 · 운영 — Git · 웹훅 · Redmine 이관 |
| [08-decisions.md](wbs/08-decisions.md) | 정해야 할 것 |

---

## 읽는 순서

| 독자 | 추천 순서 |
|---|---|
| 처음 보는 사람 · 의사결정자 | 01-overview → design/00-key-decisions → design/01-architecture → plan/01-roadmap → wbs/08-decisions |
| 기획 · PM | 01-overview → requirements/00-overview → 각 요구사항 문서 → plan/* → wbs/* |
| 개발자 | CLAUDE.md → DEVELOPE.md → wbs/00-overview → modules/00-overview → 담당 모듈 문서 → 02-glossary → design/01 · 02 → 담당 설계 문서 (권한은 design/03 · 04 + SECURITY.md, 일감은 design/05, MCP 는 design/07, 화면은 WEB-DESIGN.md) |
| AI Agent 를 붙이려는 사람 | requirements/12-mcp → design/07-mcp-agents → SECURITY.md 의 MCP 키 절 |
| 보안 담당 | SECURITY.md(구현) → requirements/01-workspace → design/03 → design/04 → design/07 |

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
- **우선순위:** `M`(첫 출시에 꼭) · `S`(곧) · `C`(나중) · `W`(이번 범위 밖). 상세는 [requirements/00-overview.md](requirements/00-overview.md).
- **진행:** 요구사항마다 `진행` 줄 하나와 수용 기준 체크. 모아 보기는 도구가 만든다([요구사항 개요 §6](requirements/00-overview.md#6-진행-관리)).
- **추적성:** 설계 문서는 `관련 요구사항`에 ID 를 적고, 요구사항 문서는 `관련 설계`에 설계 문서 위치를 적는다. WBS 작업은 `요구사항` 칸에 ID 를 적는다.
- **결정 기록:** 설계를 바꾸는 결정은 [design/00-key-decisions.md](design/00-key-decisions.md)에 ADR 로 남긴다.
- **다이어그램:** 개요는 텍스트 그림, 흐름 · 상태는 Mermaid.
- **검사:** 저장소 맨 위에서 `python3 -I tools/check_docs.py docs` → 0건.

## 문서 상태

| 항목 | 값 |
|---|---|
| 버전 | v0.1 — 프로젝트 관리 시스템으로 피봇한 첫 판 |
| 작성일 | 2026-11-21 |
| 이전 | 2026-06 의 "AI-native 그룹웨어" 명세(143개)는 지웠다. git 이력 `5ba3a92` 에 남아 있다 |
