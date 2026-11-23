[홈](README.md) › 용어집

# 02. 용어집

모든 문서는 아래 용어를 따른다. `코드명`은 데이터 모델 · API · 코드에서 쓰는 이름이다. moai-works 는 moai-wiki 와 같은 구조에서 위키 → 프로젝트, 페이지 → 일감으로 바꾼 것이라, 프로젝트 · 일감 용어에는 moai-wiki 에서 그 자리에 있던 말을 함께 적는다.

**바로가기:** [시스템 · 모듈](#1-시스템--모듈) · [계정 · 워크스페이스 · 권한](#2-계정--워크스페이스--권한) · [프로젝트](#3-프로젝트) · [일감](#4-일감) · [MCP](#5-mcp) · [공통 · 기술](#6-공통--기술)

## 1. 시스템 · 모듈

| 용어 | 코드명 | 정의 |
|---|---|---|
| api | `moai-works-api` | DB 정본 · 모든 권한 판정 · REST · MCP 를 맡는 Spring Boot 서버 |
| BFF | `moai-works-web-backend` | 브라우저 로그인 세션을 들고 api 로 요청을 전달하는 Spring Boot 서버. 판정 · 업무 로직 · DB 가 없다 |
| 화면 | `moai-works-web-frontend` | React + MUI 웹 화면. 권한은 숨기기만 한다 |
| 형제 저장소 | moai-wiki | 구조 · 의존성 · 인증 · 워크스페이스 · 역할과 권한 · MCP · 흐름 · 웹이 같은 저장소. 토대 코드의 출처이고, 같은 기능을 만들 때 먼저 보는 본보기 |

## 2. 계정 · 워크스페이스 · 권한

| 용어 | 코드명 | 정의 |
|---|---|---|
| 계정 | `user_profile` | Keycloak 사용자의 api 사본. 로그인 ID · 비밀번호는 Keycloak 에만 |
| 시스템 관리자 | `system-admin` | 플랫폼 층의 역할(Keycloak 렐름 역할). 가입 정책 · 계정 관리. 워크스페이스 내용은 멤버가 아니면 못 본다 |
| 워크스페이스 | `workspace` | 멤버십 · 설정 · 데이터 격리의 단위. Keycloak Organization 과 1:1. 개인(`PERSONAL`)과 팀(`TEAM`)이 있다. 안에 프로젝트가 여러 개 있다 |
| 워크스페이스 주소 | `slug` | `workspace-` + 고유 id 12자. 서버가 정하고 바뀌지 않는다 |
| 퍼블릭 · 프라이빗 | `PUBLIC` · `PRIVATE` | 팀 워크스페이스의 공개 범위. 프라이빗은 찾기에 안 보이고 초대로만 들어온다 |
| 멤버 | `workspace_member` | 워크스페이스에 들어온 사람. 상태 `ACTIVE` · `SUSPENDED` |
| 워크스페이스 역할 | `workspace_role` | 권한 코드 묶음. 기본 역할 넷 + 사용자 정의 `custom-…`. Keycloak 조직 그룹 `/roles/<키>` 가 정본 |
| 기본 역할 | `owner` · `admin` · `curator` · `member` | moai-wiki 와 같은 넷. **소유자** = 전부, **관리자** = 소유자 전용 둘을 뺀 전부, **큐레이터** = 모든 프로젝트에서 일감 보기 · 만들기 · 고치기 · 댓글 · 관리자 바꾸기(일감 지우기 · 프로젝트 관리는 못 함), **멤버** = 읽고 댓글. 고치거나 지울 수 없다 |
| 권한 코드 | `permission` | `issue.edit` 처럼 할 수 있는 일 하나. Keycloak 클라이언트 `moai-works-api` 의 클라이언트 역할. 25개 — 토대 16개 + 프로젝트 · 일감 9개(`project.create` · `project.manage` · `project.delete` · `issue.read` · `issue.create` · `issue.edit` · `issue.comment` · `issue.manager.assign` · `issue.delete`) |
| 소유자 전용 코드 | `owner_only` | 소유자 역할만 가질 수 있는 코드(소유권 이전 · 워크스페이스 삭제) |
| 접근 어노테이션 | `@WorkspaceApi` · `@ProjectApi` 등 | REST 핸들러마다 정확히 하나 붙이는 접근 규칙. 없으면 api 가 뜨지 않는다 |
| 가입 신청 · 초대 | `join_request` · `invitation` | 워크스페이스에 들어오는 두 길 |
| 감사 | `audit_log` | 누가 · 언제 · 어디로 · 무엇을 했는지 쌓기만 하는 기록 |

## 3. 프로젝트

| 용어 | 코드명 | moai-wiki | 정의 |
|---|---|---|---|
| 프로젝트 | `project` | 위키 | 워크스페이스 안에 여러 개 있는 일감 묶음이자 **권한 경계**. 상태 `ACTIVE` · `ARCHIVED`(보관) · `DELETED`(삭제 — 소프트) |
| 프로젝트 key | `key` | 위키 key | 워크스페이스 안에서 고유한 주소 이름(소문자로 시작 · 소문자 · 숫자 · 하이픈 · 2 ~ 32자). 만든 뒤 바꾸지 않는다. 예약 key: new · archive · archived · settings. 지운 프로젝트의 key 는 바로 다시 쓸 수 있다 |
| 공개 범위 | `visibility` | 위키 공개 범위 | `WORKSPACE`(워크스페이스 활성 멤버 전원이 독자) · `RESTRICTED`(그 프로젝트의 프로젝트 역할을 받은 사람 + 소유자 · 관리자만) |
| 독자 | — | 위키 독자 | 그 프로젝트를 볼 수 있는 사람 = 워크스페이스 활성 멤버 ∧ (공개 범위 `WORKSPACE` ∨ 소유자 · 관리자 ∨ 프로젝트 역할을 가짐). 독자가 아니면 프로젝트가 있는지도 모른다(404 `PROJECT_NOT_FOUND`) |
| 프로젝트 권한 | — | 위키 권한 | 워크스페이스 역할의 권한 ∪ 그 프로젝트의 프로젝트 역할의 권한. 독자인데 코드가 없으면 403 `PERMISSION_REQUIRED` |
| 프로젝트 역할 | `project_role_binding` | 위키 역할 | 그 프로젝트 안에서 더해 주는 권한 묶음. 고정 넷: `project-reader`(보기 · 댓글) ⊂ `project-editor`(+ 만들기 · 고치기) ⊂ `project-curator`(+ 관리자 바꾸기) ⊂ `project-admin`(+ 일감 지우기 · 그 프로젝트의 설정과 역할 관리). 한 사람이 여럿 가지면 합친다. 정본은 Keycloak 조직 그룹 `/projects/<프로젝트 id>/<역할>` |
| 보관 · 복원 · 삭제 | `project.delete` | 위키 보관 | 보관하면 관리 권한이 없는 독자에게 404 가 되고 목록의 보관 탭으로 간다. 삭제는 보관한 것만, key 를 입력해 확인한다 |
| 내 프로젝트 | key `my` | 내 위키 | 개인 워크스페이스를 만들 때 함께 만드는 기본 프로젝트(`WORKSPACE`) |
| 프로젝트 판정 | `@ProjectApi` · `ProjectContext` · `ProjectAccessService` | `@WikiApi` · `WikiContext` · `WikiAccessService` | 경로의 `{workspace}` · `{project}` 로 독자 여부와 프로젝트 권한을 보는 api 의 한 곳. 일감 경로도 이것으로 판정한다 |

## 4. 일감

| 용어 | 코드명 | moai-wiki | 정의 |
|---|---|---|---|
| 일감 | `issue` | 페이지 | 프로젝트 안의 할 일 하나(요구사항 · 기능 · 버그 · 작업). 제목(200자) · 설명(Markdown, 30000자 이하) |
| 일감 번호 | `number` | — | 프로젝트 안 순번(`project.issue_seq` 를 잠그고 올린다). 주소 `issues/:number` |
| 일감 유형 | `issue_type` | 페이지 유형 | 일감의 종류. 공통 시드 넷: requirement 요구사항 · feature 기능 · bug 버그 · task 작업. 프로젝트마다 바꾸기는 나중 후보 |
| 상태 | `status` | — | 고정 넷: `NEW` 신규 · `IN_PROGRESS` 진행 · `DONE` 완료 · `CLOSED` 종료. 아무 상태로나 옮길 수 있다 |
| 관리자 (일감 관리자) | `manager_id` | 페이지 소유자 | 일감을 책임지는 사람. 꼭 있고, 기본은 만든 사람. 바꾸려면 `issue.manager.assign`. 워크스페이스 기본 역할 "관리자"(`admin`)와 헷갈릴 때는 "일감 관리자"라고 쓴다 |
| 담당자 | `assignee_id` | (없음) | 일감을 처리하는 사람. 없어도 된다. `issue.edit` 로 바꾼다. 관리자 · 담당자는 그 프로젝트의 독자인 활성 멤버만 |
| 완료 기한 | `due_date` | — | 언제까지 끝내야 하는지. 시작일 이후 |
| 시작일 | `start_date` | — | 일을 시작하는(시작한) 날 |
| 완료 예정일 | `planned_end_date` | — | 끝낼 것으로 보는 날. 시작일 이후 |
| 완료일 | `completed_on` | — | 실제로 끝낸 날. 상태가 `DONE` 이 될 때 비어 있으면 오늘로 채우고, `NEW` · `IN_PROGRESS` 로 되돌리면 비운다. 편집 권한이 있으면 직접 고칠 수 있다 |
| 종료일 | `closed_on` | — | 일감을 닫은 날. 상태가 `CLOSED` 가 될 때 비어 있으면 오늘로 채운다(완료일은 그대로). 완료일 이후 |
| 상위 · 하위 일감 | `parent_id` | 페이지 트리 | 일감 아래의 일감. 같은 프로젝트 안만, 순환 금지, 깊이 10 이하. 하위가 있는 일감은 지울 수 없다 |
| 연결 일감 | `issue_link` | 페이지 링크 | 두 일감 사이의 관계 하나: `RELATES` 관련 · `BLOCKS` 차단 · `PRECEDES` 선행 · `DUPLICATES` 중복. 같은 프로젝트 안만, 같은 두 일감 사이에 하나, 양쪽 일감에서 보인다 |
| 이력 | `issue_journal` | 페이지 리비전 | 일감을 고칠 때마다 쌓는 한 줄 — 바뀐 필드(전 · 후) · 행위자 · 행위자 종류 · 경로 · 때. 쌓기만 한다 |
| 댓글 | `issue_journal.notes` | 피드백 | 이력 가운데 notes(Markdown)만 있는 줄. `issue.comment` 로 단다 |
| 동시 수정 | `row_version` | 리비전 충돌 | 읽은 뒤 남이 고쳤으면 쓰기를 거절하는 판 번호. 다르면 409 `CONCURRENT_UPDATE` |

## 5. MCP

| 용어 | 코드명 | 정의 |
|---|---|---|
| MCP | — | Model Context Protocol. AI Agent 가 바깥 도구 · 데이터에 붙는 표준. moai-works 는 MCP **서버**다 |
| 바깥 AI Agent | — | moai-works 밖에서 도는 Agent(Claude Code 등). moai-works 는 Agent 를 내장하지 않는다 |
| MCP 키 | `mcp_api_key` | MCP 입구에 쓰는 Bearer 키(`mai-wks-…`). **사용자 계정 하나(주인)에 묶이고 그 계정의 권한 안에서만 일한다.** 종류는 하나다(구별해 부를 때는 "사용자 키"). 원문은 한 번만 보이고 SHA-256 만 저장, 기간이 있고 지우지 않고 만료시킨다(만료는 주인만). **키는 입구에 묶이지 않는다**(2026-11-23 — [ADR-028](design/00-key-decisions.md#adr-028)) — 키 하나로 어느 MCP 입구에든 붙는다. **키는 워크스페이스에도 묶이지 않는다**(2026-11-23 — [ADR-029](design/00-key-decisions.md#adr-029)) — 개인 설정 › 내 MCP 키에서 워크스페이스를 고르지 않고 받고, 키 하나로 주인이 MCP 로 일할 수 있는 모든 워크스페이스에서 일한다 |
| 키로 쓸 수 있는 워크스페이스 | — | 키 주인이 활성 멤버이고 지금 역할에 `mcp.key.issue.personal` 이 있는 활성 워크스페이스. 그 밖의 워크스페이스는 키에게 보이지 않는다(`WORKSPACE_NOT_FOUND`). `list_workspaces` 도구가 보여 준다(2026-11-23) |
| MCP 키로 이 워크스페이스에서 일하기 | `mcp.key.issue.personal` | 권한 코드의 화면 이름(2026-11-23 바꿈 — 옛 이름 "개인 MCP 키" · "자기 MCP 키 발급"). 자기 키를 받고 그 키로 이 워크스페이스에서 일한다. 워크스페이스 관리자는 역할에서 이 권한을 빼서 Agent 를 막는다. 기본 역할 넷 모두에 있다 |
| `workspace` 인자 · `PROJECT_AMBIGUOUS` | — | 프로젝트를 받는 MCP 도구의 선택 인자 `workspace`(워크스페이스 주소). 비우면 키로 쓸 수 있는 모든 워크스페이스에서 프로젝트 key 를 찾고, 같은 key 가 여럿이면 오류 `PROJECT_AMBIGUOUS`(문구에 주소들) — 그 주소를 `workspace` 로 넣어 다시 부른다. 프로젝트 없이 하는 일(워크스페이스 기본 템플릿)은 여럿이면 `WORKSPACE_REQUIRED`(2026-11-23) |
| MCP 입구 | — | 바깥 Agent 가 붙는 MCP 주소. **기능으로 나눈다**(2026-11-23 — [ADR-028](design/00-key-decisions.md#adr-028)): 일감 `/mcp/issue`(일감 도구) · 프로젝트 관리 `/mcp/project`(프로젝트 목록 · 유형별 일감 템플릿 관리, 프로젝트 만들기 · 지우기 같은 관리 기능 자리). 같은 키로 모두 붙는다 |
| MCP 엔드포인트 | `mcp_endpoint` | **만들지 않는다**(2026-11-23 — [ADR-029](design/00-key-decisions.md#adr-029)). 옛 계획: 워크스페이스가 여는 MCP 입구 `/mcp/{워크스페이스}/{엔드포인트}`(종류 `QUERY` · `AGENT`, 대상 프로젝트 · 도구 부분집합 · 상태 · 속도 제한). 지금은 기능별 고정 입구 둘이고 워크스페이스는 도구 호출마다 고른다 |
| 서비스 키 · 읽기 방식 | — | **없앴다(2026-11-22).** 옛 설계의 서비스 키(`SERVICE` — 워크스페이스 이름으로 사람 권한과 상관없이 일하던 키)와 그 키가 무엇으로 읽는지 정하던 엔드포인트의 읽기 방식(`USER` · `SERVICE`). 지금은 모든 MCP 키가 주인 계정의 권한 안에서만 일한다([ADR-024](design/00-key-decisions.md#adr-024)) |
| MCP 도구 | — | `search_issues` · `update_issue` 처럼 Agent 가 부르는 기능. REST 와 같은 서비스 · 같은 판정을 부르고 호출마다 다시 판정한다. 지우기 · 프로젝트 설정 · 역할 바꾸기는 도구로 열지 않는다 |
| 경로 | `via` | 쓰기가 어디로 들어왔는지 — `WEB` · `MCP`. 이력 · 감사에 남는다 |
| 행위자 종류 | `actor_type` | 쓰기를 한 주체 — `USER`(사람. MCP 키로 한 일도 키 주인으로 남는다) · `SERVICE`(바깥 자동화의 Keycloak 서비스 계정 — MCP 는 쓰지 않는다) · `SYSTEM`(예약 · 기동 작업, 감사에만) |
| 멱등 키 | — | 같은 쓰기 요청을 두 번 보내도 한 번만 처리되게 하는 키 |

## 6. 공통 · 기술

| 용어 | 정의 |
|---|---|
| 정본 · 사본 | 정본은 그 데이터의 원래 자리, 사본은 판정을 빠르게 하려고 api DB 에 둔 복사. 계정 · 멤버십 · 역할 · 프로젝트 역할의 정본은 Keycloak |
| 이중 쓰기 | Keycloak 에 먼저 쓰고 api DB 사본을 고치는 순서. 단계마다 기록해 중간 실패를 이어서 끝낸다 |
| 404 위장 | 볼 수 없는 것은 "없다"(404)로 답해 존재를 숨기는 규칙. 쓰기 거부는 403 |
| problem+json | RFC 9457 오류 형식. `code` 로 분기한다 |
| 진행 줄 | 요구사항 표의 `진행` 칸. `할 일` · `일부` · `완료` · `폐기` |
| 나중 후보 | 첫 출시 범위 밖이라 요구사항으로 쓰지 않고 목록만 둔 기능([개요 §3.2](01-overview.md#32-나중-후보)) |

---

**관련 문서:** [프로젝트 개요](01-overview.md) · [요구사항 전체 목록](requirements/00-overview.md) · [워크스페이스 · 권한 설계](design/03-workspace-identity.md) · [프로젝트 설계](design/04-project.md) · [일감 설계](design/05-issue.md) · [MCP 설계](design/06-mcp.md)

[← 이전: 프로젝트 개요](01-overview.md) | [다음: 요구사항 개요와 전체 목록 →](requirements/00-overview.md)
