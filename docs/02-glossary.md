[홈](README.md) › 용어집

# 02. 용어집

모든 문서는 아래 용어를 따른다. `코드명`은 데이터 모델 · API · 코드에서 쓰는 이름이다. Redmine 을 아는 사람을 위해 Redmine 의 말을 함께 적는다.

**바로가기:** [시스템 · 모듈](#1-시스템--모듈) · [계정 · 워크스페이스 · 권한](#2-계정--워크스페이스--권한) · [프로젝트 · 일감](#3-프로젝트--일감) · [계획 · 시간](#4-계획--시간) · [협업](#5-협업) · [MCP · AI Agent](#6-mcp--ai-agent) · [공통 · 기술](#7-공통--기술)

## 1. 시스템 · 모듈

| 용어 | 코드명 | 정의 |
|---|---|---|
| api | `moai-works-api` | DB 정본 · 모든 권한 판정 · REST · MCP 를 맡는 Spring Boot 서버 |
| BFF | `moai-works-web-backend` | 브라우저 로그인 세션을 들고 api 로 요청을 전달하는 Spring Boot 서버. 판정 · 업무 로직 · DB 가 없다 |
| 화면 | `moai-works-web-frontend` | React + MUI 웹 화면. 권한은 숨기기만 한다 |
| 형제 저장소 | moai-wiki | 구조 · 의존성 · 인증 · 역할과 권한 · 워크스페이스가 같은 저장소. 토대 코드의 출처 |
| 작업 큐 | `job_queue`(계획) | 메일 · 웹훅 · 검색 색인 같은 뒷일을 api 안에서 나중에 처리하려고 DB 에 쌓는 줄 |

## 2. 계정 · 워크스페이스 · 권한

| 용어 | 코드명 | 정의 |
|---|---|---|
| 계정 | `user_profile` | Keycloak 사용자의 api 사본. 로그인 ID · 비밀번호는 Keycloak 에만 |
| 시스템 관리자 | `system-admin` | 플랫폼 층의 역할(Keycloak 렐름 역할). 가입 정책 · 계정 관리. 워크스페이스 내용은 멤버가 아니면 못 본다 |
| 워크스페이스 | `workspace` | 멤버십 · 설정 · 데이터 격리의 단위. Keycloak Organization 과 1:1. 개인(`PERSONAL`)과 팀(`TEAM`)이 있다. Redmine 에는 없다(한 설치 = 한 워크스페이스) |
| 워크스페이스 주소 | `slug` | `workspace-` + 고유 id 12자. 서버가 정하고 바뀌지 않는다 |
| 퍼블릭 · 프라이빗 | `PUBLIC` · `PRIVATE` | 팀 워크스페이스의 공개 범위. 프라이빗은 찾기에 안 보이고 초대로만 들어온다 |
| 멤버 | `workspace_member` | 워크스페이스에 들어온 사람. 상태 `ACTIVE` · `SUSPENDED` |
| 워크스페이스 역할 | `workspace_role` | 권한 코드 묶음. 기본 `owner` · `admin` · `member` + 사용자 정의 `custom-…`. Keycloak 조직 그룹 `/roles/<키>` 가 정본 |
| 권한 코드 | `permission` | `issue.edit` 처럼 할 수 있는 일 하나. Keycloak 클라이언트 `moai-works-api` 의 클라이언트 역할. 플랫폼이 정하고 워크스페이스는 골라 쓴다 |
| 소유자 전용 코드 | `owner_only` | 소유자 역할만 가질 수 있는 코드(소유권 이전 · 워크스페이스 삭제) |
| 접근 어노테이션 | `@WorkspaceApi` 등 | REST 핸들러마다 정확히 하나 붙이는 접근 규칙. 없으면 api 가 뜨지 않는다 |
| 가입 신청 · 초대 | `join_request` · `invitation` | 워크스페이스에 들어오는 두 길 |
| 감사 | `audit_log` | 누가 · 언제 · 무엇을 했는지 쌓기만 하는 기록 |

## 3. 프로젝트 · 일감

| 용어 | 코드명 | Redmine | 정의 |
|---|---|---|---|
| 프로젝트 | `project` | Project | 일감 · 버전 · 위키 등을 묶는 단위이자 **권한 경계** |
| 프로젝트 식별자 | `identifier` | Identifier | 워크스페이스 안에서 고유한 주소 이름(소문자 · 숫자 · 하이픈). 만든 뒤 바꾸지 않는다 |
| 하위 프로젝트 | `parent_id` | Subproject | 프로젝트 아래의 프로젝트. 멤버를 이어받을 수 있다 |
| 공개 범위 | `visibility` | Public | `WORKSPACE`(워크스페이스 멤버 전원이 독자) · `RESTRICTED`(프로젝트 역할을 받은 사람 + 소유자 · 관리자만) |
| 프로젝트 독자 | — | — | 그 프로젝트를 볼 수 있는 사람. 독자가 아니면 프로젝트가 있는지도 모른다(404) |
| 모듈 | `project_module` | Module | 프로젝트에서 켜고 끄는 기능(일감 · 시간 · 위키 · 뉴스 · 문서 · 파일 · 포럼 · 저장소 · 캘린더 · 간트 · 보드) |
| 프로젝트 역할 | `project_role_binding` | Role | 그 프로젝트에서의 권한 묶음. 관리자 `project-manager` · 개발자 `project-developer` · 보고자 `project-reporter` · 독자 `project-viewer` |
| 일감 | `issue` | Issue | 할 일 · 버그 · 요청 하나 |
| 일감 번호 | `number` | Issue ID | 워크스페이스 안 순번. 글 속 `#123` 은 그 일감으로 링크된다 |
| 트래커 | `tracker` | Tracker | 일감의 종류(버그 · 기능 · 지원). 트래커마다 워크플로가 다르다 |
| 상태 | `issue_status` | Status | 신규 · 진행 · 해결 · 닫힘 등. `닫힘` 상태인지 표시가 있다 |
| 우선순위 | `enumeration`(PRIORITY) | Priority | 낮음 · 보통 · 높음 · 긴급 · 즉시 |
| 워크플로 | `workflow_transition` | Workflow | 프로젝트 역할 × 트래커 × 현재 상태 → 바꿀 수 있는 다음 상태 |
| 필드 권한 | `workflow_field_rule` | Field permissions | 역할 × 트래커 × 상태 → 필드가 읽기 전용인지 · 꼭 채워야 하는지 |
| 사용자 정의 필드 | `custom_field` | Custom field | 워크스페이스가 더하는 필드. 값은 엔티티의 `custom_values`(JSONB) |
| 카테고리 | `issue_category` | Category | 프로젝트 안 일감 분류. 기본 담당자를 둘 수 있다 |
| 이력 · 메모 | `issue_journal` | Journal · Notes | 일감의 모든 변경과 메모. 쌓기만 한다. 비공개 메모가 있다 |
| 하위 일감 | `parent_id` | Subtask | 일감 아래의 일감. 시작 · 기한 · 진척률 · 예상 시간을 위로 모은다(롤업) |
| 관계 | `issue_relation` | Relation | 관련 · 중복 · 차단 · 선행/후행 · 복사 |
| 관찰자 | `issue_watcher` | Watcher | 변경 알림을 받는 사람(또는 Agent 계정) |
| 비공개 일감 | `is_private` | Private issue | 권한이 있는 사람 · 작성자 · 담당자만 보는 일감 |
| 질의 | `saved_query` | Query | 필터 · 열 · 정렬 · 묶기를 저장한 것. 개인 · 공개 · 역할별 |
| 보드 | — | (없음) | 상태를 열로 놓은 칸반 화면. 끌어 놓기도 워크플로 판정을 지킨다 |

## 4. 계획 · 시간

| 용어 | 코드명 | Redmine | 정의 |
|---|---|---|---|
| 버전 | `version` | Version · Milestone | 일감을 담는 목표 시점. 상태 열림 · 잠김 · 닫힘, 공유 범위가 있다 |
| 로드맵 | — | Roadmap | 버전별 진척(닫힌 일감 · 예상 시간 가중) |
| 간트 | — | Gantt | 일감 · 버전을 시간 막대로 보는 화면 |
| 캘린더 | — | Calendar | 시작 · 기한 · 버전 기한을 달력으로 |
| 시간 기록 | `time_entry` | Time entry | 날짜 · 시간 · 작업 분류 · 메모. 일감 또는 프로젝트에 단다 |
| 작업 분류 | `enumeration`(TIME_ACTIVITY) | Activity | 시간 기록의 종류(설계 · 개발 · 회의 …) |

## 5. 협업

| 용어 | 코드명 | Redmine | 정의 |
|---|---|---|---|
| 위키 | `wiki_page` | Wiki | 프로젝트마다 하나. 판 · 차이 · 되돌리기 · 계층 |
| 뉴스 · 문서 · 파일 · 포럼 | `news` · `document` · `release_file` · `forum` | News · Documents · Files · Forums | 프로젝트 안의 공지 · 문서 · 내려받기 파일 · 게시판 |
| 알림 | `notification` | Email notification | 화면 알림함과 메일로 가는 알림 |
| 활동 | — | Activity | 프로젝트 · 사람 · 전체의 최근 변경 흐름 |
| 내 페이지 | — | My page | 내 일감 · 관찰 일감 · 시간 등을 블록으로 모은 첫 화면 |

## 6. MCP · AI Agent

| 용어 | 코드명 | 정의 |
|---|---|---|
| MCP | — | Model Context Protocol. AI Agent 가 바깥 도구 · 데이터에 붙는 표준. moai-works 는 MCP **서버**다 |
| 바깥 AI Agent | — | moai-works 밖에서 도는 Agent(Claude Code 등). moai-works 는 Agent 를 내장하지 않는다 |
| MCP 엔드포인트 | `mcp_endpoint` | 워크스페이스가 여는 MCP 입구. 주소 `/mcp/{워크스페이스}/{엔드포인트}`. 대상 프로젝트 · 켤 도구 · 한도를 정한다(계획 — 지금은 고정 입구 `/mcp/project`) |
| MCP 키 | `mcp_api_key` | MCP 입구에 쓰는 Bearer 키. 개인 키(그 사람으로 동작) · 서비스 키(Agent 계정으로 동작). 원문은 한 번만 보이고 SHA-256 만 저장 |
| Agent 계정 | `agent_account` | 워크스페이스의 사람 아닌 멤버. 프로젝트 역할을 받고 일감 담당자가 될 수 있다. 로그인은 못 하고 서비스 키로만 일한다 |
| MCP 도구 | — | `search_issues` · `update_issue` 처럼 Agent 가 부르는 기능. REST 와 같은 서비스 · 같은 판정 |
| 멱등 키 | `idempotency_key` | 같은 쓰기 요청을 두 번 보내도 한 번만 처리되게 하는 키 |
| 낙관적 잠금 | `lock_version` | 읽은 뒤 남이 고쳤으면 쓰기를 거절하는 판 번호 |

## 7. 공통 · 기술

| 용어 | 정의 |
|---|---|
| 정본 · 사본 | 정본은 그 데이터의 원래 자리, 사본은 판정을 빠르게 하려고 api DB 에 둔 복사. 계정 · 멤버십 · 역할의 정본은 Keycloak |
| 이중 쓰기 | Keycloak 에 먼저 쓰고 api DB 사본을 고치는 순서. 단계마다 기록해 중간 실패를 이어서 끝낸다 |
| 404 위장 | 볼 수 없는 것은 "없다"(404)로 답해 존재를 숨기는 규칙. 쓰기 거부는 403 |
| problem+json | RFC 9457 오류 형식. `code` 로 분기한다 |
| 진행 줄 | 요구사항 표의 `진행` 칸. `할 일` · `일부` · `완료` · `폐기` |

---

**관련 문서:** [프로젝트 개요](01-overview.md) · [요구사항 전체 목록](requirements/00-overview.md) · [워크스페이스 · 권한 설계](design/03-workspace-identity.md) · [프로젝트 권한 설계](design/04-project-permissions.md) · [MCP 설계](design/07-mcp-agents.md)

[← 이전: 프로젝트 개요](01-overview.md) | [다음: 요구사항 개요와 전체 목록 →](requirements/00-overview.md)
