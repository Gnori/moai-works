[홈](README.md) › 프로젝트 개요

# 01. 프로젝트 개요

moai-works 가 무엇을 왜 만드는지, 누가 쓰는지, 무엇을 하고 무엇을 하지 않는지를 정한다.

**바로가기:** [배경](#1-배경) · [목표](#2-목표) · [범위](#3-범위) · [나중 후보](#32-나중-후보) · [사용자](#4-사용자) · [moai-wiki 대응](#5-moai-wiki-대응) · [핵심 시나리오](#6-핵심-시나리오) · [성공 지표](#7-성공-지표) · [제약 · 가정](#8-제약--가정)

---

## 1. 배경

- 팀은 Redmine 같은 도구로 일감을 관리해 왔다. 기능은 넓지만 여러 팀 · 회사를 한 설치에서 나누어 쓰기 어렵고(워크스페이스가 없다), **AI Agent 가 사람처럼 일감을 다루게 할 표준 입구가 없다.**
- AI 코딩 Agent(Claude Code 등)는 이제 일감을 읽고 · 쪼개고 · 상태를 바꾸고 · 댓글을 다는 일을 할 수 있다. 필요한 것은 Agent 를 시스템 **안에** 넣는 것이 아니라, 바깥 Agent 가 **안전하게 붙을 입구(MCP)** 와 **사람과 같은 권한 규칙**이다.
- 형제 저장소 moai-wiki 가 이미 그 구조를 갖고 있다 — 멀티 워크스페이스, Keycloak Organizations 정본의 역할과 권한, BFF 로그인, 워크스페이스 안의 여러 위키(권한 경계), MCP 키와 워크스페이스 MCP 엔드포인트.
- 이 저장소는 2026-06 에 "AI-native 그룹웨어" 명세로 시작했고, 2026-11-21 프로젝트 관리 시스템으로 피봇했다([ADR-001](design/00-key-decisions.md#adr-001)). 그날의 명세는 Redmine 을 그대로 옮긴 큰 범위였다. **2026-11-22 사람이 "moai-wiki 와 다를 게 없다 — 위키냐 프로젝트냐 차이만 있다"고 정해**, moai-wiki 와 같은 구조에서 위키 → 프로젝트, 페이지 → 일감으로 옮기고 일감 모델만 더하는 범위로 다시 정리했다([ADR-002](design/00-key-decisions.md#adr-002) · [ADR-018](design/00-key-decisions.md#adr-018)).

## 2. 목표

| # | 목표 | 확인 방법 |
|---|---|---|
| G1 | 워크스페이스마다 프로젝트를 여러 개 두고, 프로젝트마다 일감(유형 · 관리자 · 담당자 · 날짜 · 상위 · 하위 · 연결 · 이력 · 댓글)을 관리한다 | [프로젝트](requirements/02-project.md) · [일감](requirements/03-issue.md) 요구사항 M 항목의 수용 기준 |
| G2 | 바깥 AI Agent 가 MCP 로 붙어 일감을 찾고 · 읽고 · 만들고 · 고치고 · 댓글을 단다 | [MCP 요구사항](requirements/04-mcp.md) M 항목, 실제 Claude Code 연결 시험 |
| G3 | Agent 는 사람과 **같은 권한 규칙** 안에서만 움직이고, 한 일은 이력 · 감사에 경로(MCP)와 함께 남는다 | 권한 회귀 테스트 · 웹과 MCP 의 동등성 테스트 · 이력 · 감사 기록 |
| G4 | 한 설치에서 여러 팀 · 회사가 워크스페이스로 나뉘고, 워크스페이스 안에서는 프로젝트가 권한 경계다 | 경계 테스트(다른 워크스페이스 · 독자가 아닌 프로젝트는 404) |
| G5 | moai-wiki 와 같은 구조 · 같은 코드 모양을 유지해 토대 고침을 두 저장소에 함께 적용할 수 있다 | [NFR-MNT-001](requirements/05-non-functional.md#nfr-mnt-001) |

## 3. 범위

### 3.1 한다 · 하지 않는다

moai-wiki 와 같은 것은 그대로 두고, 위키 · 페이지 자리를 프로젝트 · 일감으로 바꾸고, 일감 모델을 더한다.

| 한다 (첫 출시) | 하지 않는다 |
|---|---|
| **moai-wiki 와 같은 토대** — 가입 · 로그인(BFF 세션) · 워크스페이스(개인/팀 · 퍼블릭/프라이빗 · 승인/자동 가입) · 멤버십 · 초대 · 가입 신청 · 기본 역할 넷(소유자 · 관리자 · 큐레이터 · 멤버) · 사용자 정의 역할 · 감사 | 시스템 안의 LLM · AI 기능(요약 · 분류 · 자동 배정) — 바깥 Agent 몫([ADR-003](design/00-key-decisions.md#adr-003)) |
| **프로젝트** (위키 자리) — 워크스페이스 안 여러 개 · 공개 범위(`WORKSPACE` · `RESTRICTED`) · 독자 판정 · 보관 · 복원 · 삭제 · 프로젝트 역할 넷 · 기본 프로젝트("내 프로젝트") | moai-wiki 의 내장 agent · collector · refiner · 자료실 · 보안등급 · 변경안 검토 |
| **일감** (페이지 자리) — 유형(요구사항 · 기능 · 버그 · 작업) · 상태 넷 · 관리자 · 담당자 · 완료 기한 · 시작일 · 완료 예정일 · 완료일 · 종료일 · 상위 · 하위 일감 · 연결 일감 · 이력 · 댓글 · 목록 · 필터 · 정렬 | 그룹웨어 기능(결재 · 근태 · 메신저 · 메일함) — 피봇 전 범위 |
| **MCP** (moai-wiki MCP 체계) — 사용자 계정에 묶인 MCP 키(주인의 권한 안에서만) · 워크스페이스 MCP 엔드포인트 · 읽기 · 쓰기 도구 · 안전장치 · 연결 안내 | "Agent 계정"(사람 아닌 멤버) — moai-wiki 에 없어 뺐다([ADR-007](design/00-key-decisions.md#adr-007) 폐기). 워크스페이스 이름으로 사람 권한을 넘어 일하는 서비스 키도 없앴다([ADR-024](design/00-key-decisions.md#adr-024)) |
| | Redmine 플러그인 호환 · 저장소 코드 호스팅 · 회계 · 청구 · 모바일 앱(반응형 웹으로 대신) |

### 3.2 나중 후보

첫 출시 범위 밖이다. **요구사항으로 쓰지 않고 목록만 둔다.** 무엇을 먼저 할지는 [정해야 할 것 D-05](wbs/08-decisions.md#d-05)에서 정하고, 고른 것은 요구사항부터 쓴 뒤 [5단계](wbs/06-stage-5-collaboration.md)(Redmine 이관은 [6단계](wbs/07-stage-6-operations.md))에서 만든다.

| 묶음 | 후보 |
|---|---|
| 일감 모델 넓히기 | 사용자 정의 워크플로 · 상태 · 사용자 정의 필드 · 프로젝트별 일감 유형 · 다른 프로젝트와의 연결 일감 |
| 계획 | 버전과 로드맵 · 간트 · 캘린더 · 시간 기록 |
| 협업 | 첨부 · 관찰자 · 알림과 메일 · 활동 · 전문 검색(OpenSearch) |
| 프로젝트 넓히기 | 프로젝트 위키 · 뉴스 · 포럼 · 하위 프로젝트 · 모듈 켜고 끄기 |
| 목록 · 보기 | 저장한 질의 · 칸반 보드 · 내보내기 |
| 연동 | Git 연동 · 웹훅 · REST API 키 · Redmine 이관 |

## 4. 사용자

| 사용자 | 하는 일 | 주로 쓰는 곳 |
|---|---|---|
| 시스템 관리자 | 가입 정책 · 계정 승인 · 팀 워크스페이스 만들기 허용 | 플랫폼 관리 화면 |
| 워크스페이스 소유자 · 관리자 | 멤버 · 역할 · 프로젝트 만들기 · 프로젝트 설정과 역할 · MCP 엔드포인트와 키 관리 | 워크스페이스 설정 · 프로젝트 목록 |
| 큐레이터 | 모든 프로젝트에서 일감을 만들고 고치고 관리자를 바꾼다(일감 지우기 · 프로젝트 관리는 못 함) | 일감 목록 · 일감 화면 |
| 프로젝트 관리자(`project-admin`) | 그 프로젝트의 설정 · 프로젝트 역할 · 일감 지우기 | 프로젝트 설정 · 멤버 |
| 멤버 · 프로젝트 편집자 | 멤버는 읽고 댓글을 단다. 프로젝트 역할(편집자 이상)을 받으면 그 프로젝트에서 일감을 만들고 고친다 | 일감 목록 · 일감 화면 |
| **바깥 AI Agent** | MCP 로 프로젝트 · 일감 찾기 · 읽기 · 만들기 · 고치기 · 댓글 · 상위 · 연결 — 키 주인의 권한 안에서만(엔드포인트가 범위를 더 좁힌다) | 워크스페이스 MCP 엔드포인트 |

## 5. moai-wiki 대응

| moai-wiki | moai-works |
|---|---|
| 위키 `wiki` (워크스페이스 안 여러 개, 권한 경계) | 프로젝트 `project` |
| 페이지 `page` (위키 안 여러 개) | 일감 `issue` |
| 페이지 유형 `page_type` (공통 시드 표) | 일감 유형 `issue_type` (공통 시드 표) |
| 페이지 트리 `page.parent_id` (같은 위키) | 상위 · 하위 일감 `issue.parent_id` (같은 프로젝트) |
| 페이지 소유자 `page.owner_id` · `page.owner.assign` | 일감 관리자 `issue.manager_id` · `issue.manager.assign` |
| (없음) | 일감 담당자 `issue.assignee_id` |
| 페이지 링크 | 연결 일감 `issue_link` |
| 페이지 리비전(이력) | 일감 이력 `issue_journal` (필드 변경 + 댓글) |
| 피드백 `feedback.create` | 댓글 `issue.comment` |
| 위키 역할 wiki-admin ⊃ wiki-curator ⊃ wiki-editor ⊃ wiki-reader | 프로젝트 역할 project-admin ⊃ project-curator ⊃ project-editor ⊃ project-reader |
| Keycloak 조직 그룹 `/wikis/<id>/<role>` · 사본 `wiki_role_binding` | `/projects/<id>/<role>` · 사본 `project_role_binding` |
| `@WikiApi(Permission)` · `WikiContext` · `WikiAccessService` | `@ProjectApi(Permission)` · `ProjectContext` · `ProjectAccessService` |
| 오류 `WIKI_NOT_FOUND`(404) · `PERMISSION_REQUIRED`(403) | `PROJECT_NOT_FOUND`(404) · `PERMISSION_REQUIRED`(403) · 일감은 `ISSUE_NOT_FOUND`(404) |
| 개인 워크스페이스의 "내 위키"(key `my`) | "내 프로젝트"(key `my`) |
| 화면 `/w/:slug/wikis` · `/w/:slug/wikis/:wikiKey` · `WikisPage` · `WikiDialog` · `RemoveWikiDialog` · `WikiLayout` · `WikiHomePage` · `use-wiki-actions` | `/w/:slug/projects` · `/w/:slug/projects/:projectKey` · `ProjectsPage` · `ProjectDialog` · `RemoveProjectDialog` · `ProjectLayout` · `ProjectHomePage` · `use-project-actions` |
| 페이지 화면 `p/:pageSlug` · `new` · `edit` · `history` | 일감 화면 `issues` · `issues/new` · `issues/:number` · `issues/:number/edit` |
| MCP — MCP 키(SHA-256), 워크스페이스 엔드포인트 `/mcp/{ws}/{ep}`, 도구는 같은 서비스 · 매 호출 재판정 | 키(SHA-256) · 같은 서비스 · 매 호출 재판정은 같다. 키는 늘 주인 계정의 권한 안에서만 일한다 — moai-wiki 설계에 있던 서비스 키 · 서비스 읽기 방식은 없앴다([ADR-024](design/00-key-decisions.md#adr-024)). 입구는 기능별 고정 둘(`/mcp/issue` · `/mcp/project`)이고, **키는 사용자의 것이라 워크스페이스 엔드포인트를 두지 않는다** — 키 하나로 MCP 권한이 있는 모든 워크스페이스에서 일하고 워크스페이스는 도구 호출마다 고른다(2026-11-23 [ADR-028](design/00-key-decisions.md#adr-028) · [ADR-029](design/00-key-decisions.md#adr-029)) |
| 내장 agent · collector · refiner · 자료실 · 보안등급 · 변경안 검토 | 없다 (AI 를 내장하지 않는다) |

**바꾸지 않는 것:** 모듈 구조(`src/` 아래 api · web-backend · web-frontend) · 의존성 판 · 인증(Keycloak 렐름 · BFF 패스워드 그랜트 · 서버 세션 · api 의 JWT 직접 검증) · 워크스페이스 · 역할과 권한(Keycloak Organizations 정본 · DB 사본 판정 · 사용자 정의 역할) · 흐름(가입 → 로그인 → 개인 워크스페이스 → 팀 워크스페이스 → 초대 · 가입 신청) · 감사 · 화면 뼈대 · 문서 형식.

**Redmine 에서 가져온 개념:** 일감이라는 말과 그 모델 일부 — 일감 유형(Redmine 의 트래커 자리, 다만 공통 고정 넷), 프로젝트 안 일감 번호, 담당자, 시작일 · 완료 기한, 상위 · 하위 일감, 일감 관계(관련 · 차단 · 선행 · 중복), 필드 변경과 메모를 한 줄기로 쌓는 이력(저널). 워크플로 · 사용자 정의 필드 · 버전 · 시간 기록 · 위키 · 알림 같은 나머지는 [나중 후보](#32-나중-후보)이고, Redmine 에서 옮겨 오는 이관도 나중 후보(6단계)다.

## 6. 핵심 시나리오

| ID | 시나리오 | 흐름 |
|---|---|---|
| UC-01 | 팀이 워크스페이스를 연다 | 가입 → 시스템 관리자가 팀 워크스페이스 허용 → 팀 워크스페이스 만들기 → 멤버 초대 · 가입 승인 → 역할(관리자 · 큐레이터 · 멤버) 주기 |
| UC-02 | 프로젝트를 만들고 사람을 붙인다 | 소유자 · 관리자가 프로젝트 만들기(key · 이름 · 공개 범위) → `RESTRICTED` 면 프로젝트 역할(독자 · 편집자 · 큐레이터 · 관리자)을 준다 → 다 끝난 프로젝트는 보관 → 필요 없으면 key 를 입력해 삭제 |
| UC-03 | 일감을 만들고 따라간다 | 편집자가 요구사항 유형의 일감을 만든다(관리자 = 나, 담당자 · 시작일 · 완료 예정일 · 완료 기한) → 하위 일감으로 기능 · 작업을 나눈다 → 다른 일감과 "차단"으로 잇는다 → 담당자가 진행으로 바꾸고 댓글을 단다 → 완료로 바꾸면 완료일이 채워지고, 관리자가 확인한 뒤 종료로 바꾸면 종료일이 채워진다 → 이력에 모든 변경과 댓글이 한 줄기로 남는다 |
| UC-04 | 일감을 찾는다 | 프로젝트 홈의 일감 목록에서 상태 · 유형 · 관리자 · 담당자 · 상위 · 제목으로 거르고 정렬한다 |
| UC-05 | **AI Agent 가 일감을 처리한다 (작업 엔드포인트)** | 관리자가 AGENT 엔드포인트(대상 프로젝트 · 쓰기 도구)를 만든다 → 그 일을 맡은 멤버가 그 엔드포인트의 자기 MCP 키를 발급 → Claude Code 에 등록 → Agent 가 `search_issues` 로 일감을 찾고 `get_issue` 로 읽고 · 작업 뒤 `add_comment` · `update_issue`(상태 · 날짜) · `create_issue`(하위 일감) · `link_issues` → 이력 · 감사에 "그 사람 (MCP 로)"와 엔드포인트 · 키가 남는다 |
| UC-06 | **키는 주인의 권한을 넘지 못한다** | 멤버가 엔드포인트의 MCP 키를 발급 → 엔드포인트 대상이라도 그 사람이 볼 수 없는 프로젝트는 Agent 에게도 보이지 않고, 그 사람에게 없는 권한의 도구는 거절된다 → 그 사람의 프로젝트 역할을 빼면 다음 도구 호출부터 거절된다 |

## 7. 성공 지표

| 지표 | 목표 |
|---|---|
| 첫 출시 범위(M 요구사항) 수용 기준 | 모두 확인 |
| 워크스페이스 · 프로젝트 경계 누출 | 0건(회귀 테스트) |
| MCP 도구 호출이 웹과 다른 결과를 내는 경우 | 0건(같은 서비스 · 같은 판정 — 동등성 테스트) |
| 이력 · 감사에 행위자 · 경로가 남지 않은 쓰기 | 0건 |
| 일감 목록(필터 · 1만 건 프로젝트) 응답 | [NFR](requirements/05-non-functional.md) 기준 |

## 8. 제약 · 가정

- **moai-wiki 와 같게 (2026-11-21 · 2026-11-22 사람 지시)**: 구조(`src/` 아래 모듈) · 의존성 판 · 인증(Keycloak Organizations, BFF 세션) · 워크스페이스(개인/팀, 퍼블릭/프라이빗) · 역할과 권한(권한 코드 = Keycloak 클라이언트 역할, 접근 어노테이션, 기본 역할 넷) · MCP 체계 · 흐름 · 웹 구조. 같은 기능은 moai-wiki 코드를 먼저 보고 같은 모양으로 만든다.
- 권한 코드는 25개다 — 토대 16개 + 프로젝트 · 일감 9개(moai-wiki 의 위키 · 페이지 코드 자리). 내장 AI · 자료실 코드는 없다.
- 로컬 인프라는 다른 프로젝트와 같이 쓰는 컨테이너(MOAI-DB · MOAI-Redis · MOAI-MinIO · MOAI-OpenSearch · MOAI-Keycloak)를 쓴다. 우리 것만 다룬다.
- 메일은 SMTP 가 정해질 때까지 로그 모드다.
- 운영 배치(클라우드/사내)와 규모는 아직 정하지 않았다([정해야 할 것](wbs/08-decisions.md)).

---

**관련 문서:** [용어집](02-glossary.md) · [요구사항 전체 목록](requirements/00-overview.md) · [핵심 설계 결정](design/00-key-decisions.md) · [아키텍처](design/01-architecture.md) · [로드맵](plan/01-roadmap.md)

[← 이전: 설계 문서](README.md) | [다음: 용어집 →](02-glossary.md)
