[홈](../README.md) › [설계](00-key-decisions.md) › 일감

# 일감 — 유형 · 상태 · 날짜 · 관리자와 담당자 · 상위 · 하위 · 연결 · 이력

일감은 **워크스페이스 아래에 있는** 일 하나이고, 프로젝트는 일감의 속성이다(꼭 하나, 옮길 수 있다 — Redmine 모델, [ADR-030](00-key-decisions.md#adr-030), 2026-11-24). moai-wiki 의 **페이지** 자리이고, 권한은 그 일감의 지금 프로젝트의 것을 그대로 쓴다(일감마다 읽기를 좁히지 않는다 — [프로젝트 설계 §1](04-project.md#pj-principles)). 사람이 정한 일감 모델은 이것뿐이다 — 유형(예: 요구사항), 관리자 · 담당자, 날짜 다섯 개(완료 기한 · 시작일 · 완료 예정일 · 완료일 · 종료일), 상위 · 하위, 연결 일감([ADR-018](00-key-decisions.md#adr-018)). 여기에 상태 · 이력 · 댓글을 더한다. 표의 DDL 은 [데이터 모델 §5](02-data-model.md#dm-issue)에 있다.

> 요구사항: [FR-ISS-001](../requirements/03-issue.md#fr-iss-001) 만들기 · [002](../requirements/03-issue.md#fr-iss-002) 보기 · [003](../requirements/03-issue.md#fr-iss-003) 고치기(동시 수정) · [004](../requirements/03-issue.md#fr-iss-004) 유형 · [005](../requirements/03-issue.md#fr-iss-005) 상태 · [006](../requirements/03-issue.md#fr-iss-006) 관리자와 담당자 · [007](../requirements/03-issue.md#fr-iss-007) 날짜 · [008](../requirements/03-issue.md#fr-iss-008) 상위 · 하위 · [009](../requirements/03-issue.md#fr-iss-009) 연결 · [010](../requirements/03-issue.md#fr-iss-010) 이력 · [011](../requirements/03-issue.md#fr-iss-011) 댓글 · [012](../requirements/03-issue.md#fr-iss-012) 목록 · 필터 · 정렬 · [013](../requirements/03-issue.md#fr-iss-013) 지우기 · [014](../requirements/03-issue.md#fr-iss-014) 화면 · [019](../requirements/03-issue.md#fr-iss-019) 댓글 고치기 · 지우기 · [022](../requirements/03-issue.md#fr-iss-022) 에이전트 세션 · [023](../requirements/03-issue.md#fr-iss-023) 관련 모듈 · [024](../requirements/03-issue.md#fr-iss-024) 프로젝트 옮기기 · [025](../requirements/03-issue.md#fr-iss-025) 워크스페이스 일감 목록 · 찾기 · [026](../requirements/03-issue.md#fr-iss-026) 번호 다시 매기기(V23)

**바로가기:** [원칙](#is-principles) · [일감](#is-model) · [유형](#is-type) · [번호](#is-number) · [번호 다시 매기기](#is-renumber) · [상태](#is-status) · [날짜](#is-dates) · [관리자 · 담당자](#is-people) · [상위 · 하위](#is-tree) · [옮기기](#is-move) · [연결](#is-links) · [이력](#is-journal) · [댓글](#is-comments) · [에이전트 세션](#is-agent-session) · [관련 모듈](#is-modules) · [지우기](#is-delete) · [동시 수정](#is-lock) · [작업 시간](#is-worklog) · [공수 집계](#is-effort) · [목록](#is-list) · [워크스페이스 목록](#is-ws-list) · [행동별 규칙](#is-actions) · [REST](#is-api) · [오류](#is-errors) · [화면](#is-screens) · [시험](#is-tests)


> **구현 상태 (2026-11-22)** — 만든 것: 유형 · 순번 · 상태와 날짜 자동 채움 · 관리자 · 담당자 · 다섯 날짜 · 상위 · 하위 · 연결(이력에 방향) · 이력(만들기 첫 줄 · 설명 전 · 후 전체) · 댓글 · 지우기 · 목록(필터 별칭 · 정렬 · `childCount` · `overdue`) · 보기(`overdue`) · 쓰기 여섯 가지 감사 · 화면. **아직(계획):** 보기의 `myActions`(화면은 지금 프로젝트의 `myPermissions` 로 가린다) · 이력 100줄 넘을 때 이어 받기(`journal?cursor`) · 떠난 멤버 표시(`active`) · `issue.title` 의 "C" 정렬.
>
> **2026-11-24 바뀜 — 일감은 워크스페이스 아래([ADR-030](00-key-decisions.md#adr-030), V23, moai-works 일감 #29):** 번호는 워크스페이스 안에서 하나([§4](#is-number)), 정식 주소는 `/w/:slug/issues/:number` · REST `/workspaces/{ws}/issues/{number}`(`@IssueApi`), 프로젝트는 일감의 속성이라 옮길 수 있다([§7-1](#is-move)), 워크스페이스 일감 목록 · 찾기([§13.1](#is-ws-list)), 연결은 같은 워크스페이스의 다른 프로젝트와도([§8](#is-links)). 옛 데이터는 V23 이 워크스페이스 번호로 다시 매겼다([§4.1](#is-renumber)).
>
> **2026-11-23 더함 — 에이전트 세션([§10-4](#is-agent-session), V20) · 관련 모듈([§10-5](#is-modules), V21):** 일감마다 맡은 AI 세션 이름(없어도 됨)과 고치는 모듈(여럿, 프로젝트의 모듈 목록에서)을 붙인다. 둘 다 이력 · 판 · 감사 · 목록 거르기 · MCP 에 있다.
>
> **2026-11-23 더함 — 댓글 고치기 · 지우기([§10](#is-comments), V14):** 쓴 사람만 고치고(앞 글은 `issue_comment_revision` 에 쌓음 · 댓글 판으로 409), 쓴 사람 또는 `project.manage` 가 지운다("지운 댓글", 글은 더 내주지 않음). 이력 줄 보호 트리거 · 쪽 나눈 댓글 목록 · MCP 셋 · 화면(⋮ · 같은 편집기 · 확인 대화 상자).

---

<a id="is-principles"></a>
## 1. 원칙

| 원칙 | 내용 |
|---|---|
| 권한은 그 일감의 지금 프로젝트 | 일감은 워크스페이스 아래에 있지만 권한 경계는 그대로 프로젝트다. 번호로 가리키는 API 는 `@IssueApi(코드)`(그 일감의 지금 프로젝트로 `@ProjectApi` 와 같은 판정), 프로젝트의 목록 · 만들기는 `@ProjectApi(코드)`. 일감이 없거나 · 지웠거나 · 그 프로젝트를 볼 수 없으면 모두 404 `ISSUE_NOT_FOUND`(어느 프로젝트인지 숨긴다) |
| 고정으로 시작 | 유형 · 상태 · 연결 종류는 모든 프로젝트에 같다. 프로젝트마다 바꾸기 · 워크플로 · 사용자 정의 필드는 나중 후보다 |
| 상위 · 하위는 같은 프로젝트 | 상위 · 하위는 같은 프로젝트 안끼리만(DB 복합 외래 키). 연결은 같은 워크스페이스면 다른 프로젝트와도 된다(2026-11-24). 옮기면 하위 줄기가 함께 간다 |
| 이력과 감사 | 일감을 바꿀 때마다 이력 한 줄과 감사 한 줄을 남긴다. 누가(사람 — MCP 로 한 일은 키 주인) 어디로(웹 · MCP) 무엇을 바꿨는지가 남는다 |
| 같은 서비스 | 웹(REST)과 MCP 도구는 같은 `IssueService` 를 부른다([ADR-004](00-key-decisions.md#adr-004)) |
| 보관한 프로젝트 | 읽기 전용이다. 일감 쓰기는 409 `PROJECT_NOT_WRITABLE`([프로젝트 설계 §2.2](04-project.md#pj-status)) |

---

<a id="is-model"></a>
## 2. 일감

| 필드 (API) | 열 | 뜻 | 규칙 |
|---|---|---|---|
| `number` | `number` | 워크스페이스 안 번호 | [§4](#is-number). 바꾸지 않는다(옮겨도 그대로) |
| `project` (2026-11-24) | `project_id` | 지금 프로젝트 `{key, name}` — 권한 경계 | 꼭 하나. 고치기의 `project`(주소 이름)로 옮긴다([§7-1](#is-move)) |
| `type` | `issue_type` | 유형 key | [§3](#is-type). 필수 |
| `title` | `title` | 제목 | 앞뒤 공백을 뺀 1~200자 |
| `description` | `description` | 설명(Markdown) | 30,000자까지, 없으면 빈 글 |
| `status` | `status` | 상태 | [§5](#is-status). 만들 때 기본 `NEW` |
| `managerId` → `manager` | `manager_id` | 관리자 | [§6](#is-people). 필수, 기본 = 만든 사람 |
| `assigneeId` → `assignee` | `assignee_id` | 담당자 | [§6](#is-people). 없어도 된다 |
| `parent` | `parent_id` | 상위 일감 | [§7](#is-tree). API 는 번호로 주고받는다 |
| `dueDate` | `due_date` | 완료 기한 | [§5.1](#is-dates) |
| `startDate` | `start_date` | 시작일 | |
| `plannedEndDate` | `planned_end_date` | 완료 예정일 | |
| `completedOn` | `completed_on` | 완료일 | `DONE` 이 되면 자동 |
| `closedOn` | `closed_on` | 종료일 | `CLOSED` 가 되면 자동 |
| `createdBy` · `createdAt` · `updatedAt` | | 만든 사람 · 때 | |
| `agentSession` (2026-11-23) | `agent_session` | 에이전트 세션 — 이 일을 맡은 AI 세션 이름 | [§10-4](#is-agent-session). 없어도 된다(앞뒤 공백을 뺀 1~100자) |
| `modules` (2026-11-23) | `issue_module` 표 | 관련 모듈(여럿) | [§10-5](#is-modules). 요청은 key 목록, 응답은 `[{key, name, active}]`(모듈 순서) |
| `rowVersion` | `row_version` | 동시 수정 검사 | [§12](#is-lock) |

요청은 사람을 id(`managerId` · `assigneeId`)로 받고, 응답은 사람 꼴(`manager` · `assignee` = `{id, username, displayName, active}`)로 준다. `active` 가 거짓이면 "떠난 멤버"다([§6](#is-people)).

moai-wiki `page` 와 견주면 — `wiki_id` → `project_id`, `slug` → `number`, `page_type` → `issue_type`, `owner_id` → `manager_id`(+ `assignee_id`), `parent_id` 는 같다. 페이지의 리비전 · 작업본 · 섹션 · 이름 공간 · 편집 제한 · 보관 상태는 없다.

<a id="is-type"></a>
## 3. 일감 유형

| key | 이름 | 설명 | 순서 | 쓰는지 |
|---|---|---|---|---|
| `feature` | 새기능 | 새로 만들거나 늘리는 기능 | 1 | 씀 |
| `bug` | 버그 | 고쳐야 할 잘못된 동작 | 2 | 씀 |
| `test` | 테스트 | 동작을 확인 · 검증하는 일 | 3 | 씀 |
| `research` | 기술조사 | 기술을 알아보고 정리하는 일 | 4 | 씀 |
| `requirement` | 요구사항 | 만들어야 할 것 — 무엇을 왜 | 91 | 더 쓰지 않음 |
| `task` | 작업 | 그 밖의 할 일 | 92 | 더 쓰지 않음 |

2026-11-22 사용자: "유형 : 새기능, 버그, 테스트, 기술조사"(V8). 옛 요구사항 · 작업은 그 유형의 일감 · 판이 남아 지우지 않고 `active = false` 로 둔다 — 새로 고르면 400 `ISSUE_TYPE_RETIRED`, 이미 그 유형인 일감은 다른 것을 고칠 때 그대로 둘 수 있다. 화면은 `active` 인 것만 고르게 하고, 옛 유형 일감의 이름은 그대로 보인다.

- 공통 시드 표 `issue_type` 이다(moai-wiki `page_type` 처럼 V2 가 넣는다). 모든 프로젝트가 같다. 프로젝트마다 바꾸기는 나중 후보다([정해야 할 것](../wbs/08-decisions.md)). 유형을 더하거나 고치는 API 는 없다. 화면은 key 로 다국어 이름을 보인다.
- 유형은 고칠 수 있다(`issue.edit`, 이력에 남는다). 유형에 따라 달라지는 규칙은 없다.
- 없는 key 는 400 `UNKNOWN_ISSUE_TYPE`. 목록은 `GET …/issue-types`(`issue.read`, 순서대로).

<a id="is-number"></a>
## 4. 번호

- 일감 번호는 **워크스페이스 안 순번**이다(1, 2, 3 …, [ADR-030](00-key-decisions.md#adr-030) — 2026-11-24 사용자: "워크스페이스 단위로 유니크한 번호여야해"). 만드는 트랜잭션 안에서 워크스페이스 행의 순번을 잠가 올린다(`UPDATE workspace SET issue_seq = issue_seq + 1 WHERE id = ? RETURNING issue_seq` — 워크스페이스의 `row_version` · 고친 때는 건드리지 않는다). 같은 워크스페이스의 만들기는 어느 프로젝트든 줄을 선다. 만들기가 되돌려지면 번호도 되돌려진다.
- 지운 일감의 번호는 다시 쓰지 않는다(`UNIQUE (workspace_id, number)` 가 지운 행에도 걸린다). 옮겨도 번호는 그대로다.
- 정식 주소: 화면 `/w/{ws}/issues/{number}`(Redmine 의 `/issues/:id`), REST `/v1/workspaces/{ws}/issues/{number}`. 글에서는 `#12` — 그 워크스페이스의 12번이다(프로젝트가 달라도 같은 링크). 옛 화면 주소 `/w/{ws}/projects/{project}/issues/{number}`(· `/edit` · `/history`)는 [§4.1](#is-renumber) 의 기록으로 지금 번호를 찾아 정식 주소로 돌린다(`GET /workspaces/{ws}/projects/{key}/issue-refs/{number}` — 기록이 없으면 그 프로젝트의 그 번호, 읽을 수 없으면 404).
- 워크스페이스 번호는 순번이라 번호 사이가 비어 있으면 다른 프로젝트(읽을 수 없는 것 포함)에 일감이 있다는 것은 알 수 있다 — 내용 · 프로젝트는 알 수 없다(Redmine 과 같다).
- `project.issue_seq` 는 V23 이 지웠다.

<a id="is-renumber"></a>
### 4.1 번호 다시 매기기 (V23, 2026-11-24)

옛 데이터는 프로젝트 안 번호였다. V23(Java 마이그레이션 `db.migration.V23__workspace_issue_number`, 한 트랜잭션)이 프로젝트 소유자가 정한 대로 바꾼다 — [FR-ISS-026](../requirements/03-issue.md#fr-iss-026).

| 무엇 | 어떻게 |
|---|---|
| 새 번호 | 워크스페이스마다 만든 때(`created_at`) 순, 같으면 옛 번호 · 프로젝트 · id 순으로 1 부터(처음부터 워크스페이스 번호였던 것처럼). 지운 일감도 번호를 받는다. 돌 때의 데이터로 계산한다 |
| 가리키는 번호 | 같은 일감을 계속 가리키게 바꾼다: `issue_revision.parent_number`(상위는 같은 프로젝트였다) · `attachment_ticket.issue_number`(티켓의 프로젝트 + 옛 번호) · `mcp_access_log.issue_number` 와 `link_issues` 의 `target_ref`(그 줄의 워크스페이스 · 프로젝트 주소 이름 + 옛 번호가 한 일감일 때 — "어느 일감"을 찾는 식별자라서) · `issue_journal.changes` 의 상위 · 연결 번호 · `notification.params` 의 `number` · `childNumber` · `otherNumber`. 감사 기록의 글(`audit_log.details`)과 MCP 접근 기록의 `arguments`(받은 그대로의 인자)는 쓴 그대로 둔다 |
| 글 속 `#n` | 지금 설명(살아 있는 일감)과 지우지 않은 댓글에서, 그 글이 달린 일감의 프로젝트의 옛 번호로 읽어 새 번호로 바꾼다. 화면 ISSUE_REF(`markdown-plugins.ts`)와 같은 규칙이고, 펜스 · 들여쓴 코드 블록 · 인라인 코드 · 링크(글과 주소) · 꺾쇠 자동 링크 · 맨 주소 · 링크 정의 줄은 그대로 둔다. 그 프로젝트에 없던 번호 · 번호가 그대로인 것은 두고 세지 않는다. 옛 판 · 바뀌기 전 댓글 글(`issue_comment_revision`)은 역사라 그대로 |
| 건드리지 않는 것 | 일감 · 댓글의 `row_version` · `updated_at` · 댓글 판 · 고친 때. 알림을 보내지 않는다 |
| 기록 | `issue_number_change`(일감마다 한 줄: 워크스페이스 · 그때의 프로젝트 · 주소 이름 · 옛 번호 · 새 번호 · 설명과 댓글에서 바꾼 `#n` 수) · `issue_number_text_change`(바꾼 설명 · 댓글의 바꾸기 전 · 뒤 글). 번호나 설명이 바뀐 살아 있는 일감마다 시스템 이력 한 줄(`actor_type SERVICE` · 이름 `moai-works` · 경로 `SYSTEM`, `{field: number, before: {project, number}, after: {number}}` + 설명이 바뀌었으면 `description`) — 화면은 "번호가 바뀌었습니다: moai-works #12 → #57 (워크스페이스 단위 번호)". 설명이 바뀌었으면 그 모습을 새 판으로 남긴다(다음 사람이 고친 판의 차이에 섞이지 않게) |
| 마무리 | `UNIQUE (workspace_id, number)`, `workspace.issue_seq` = 그 워크스페이스의 가장 큰 번호, `project.issue_seq` 지움, 판에 `project_id`(옛 판은 그 일감의 프로젝트), 이력 · 판의 경로에 `SYSTEM`, 상위 FK 를 미룰 수 있게(`DEFERRABLE` — 옮기기), 첨부 보호 트리거가 프로젝트를 일감을 따라 바꾸는 것만 더 허용. 이력 · 판 · 접근 기록의 보호 트리거는 이 마이그레이션 안에서만 껐다 켠다 |

옛 → 새 번호 보기(합친 뒤 운영 DB 에서):

```sql
SELECT w.slug, c.project_key, c.old_number, c.new_number, c.description_refs, c.comment_refs
FROM issue_number_change c JOIN workspace w ON w.id = c.workspace_id
ORDER BY w.slug, c.project_key, c.old_number;
```

---

<a id="is-status"></a>
## 5. 상태

| 상태 | 이름 | 묶음 | 뜻 |
|---|---|---|---|
| `NEW` | 등록 | 열림 | 아직 손대지 않음 (2026-11-22 이름 신규 → 등록) |
| `IN_PROGRESS` | 진행 | 열림 | 하는 중 |
| `TESTING` | 테스트 | 열림 | 만들고 확인하는 중 — 날짜를 채우지 않는다 (2026-11-22 더함, V7) |
| `DONE` | 완료 | 닫힘 | 일을 끝냄 |
| `CLOSED` | 종료 | 닫힘 | 더 다루지 않음(끝냈든 접었든) |

- 2026-11-22 사용자: "상태 : 등록, 진행, 테스트, 완료, 종료". 고정 5개이고 **아무 상태로나 옮길 수 있다**(워크플로 사용자 정의는 나중, [ADR-020](00-key-decisions.md#adr-020)). 상태 바꾸기는 `issue.edit` 이다. 그 밖의 값은 400 `INVALID_ISSUE_STATUS`.
- 목록 필터는 묶음(`open` = 등록 · 진행 · 테스트, `closed` = 완료 · 종료)도 받는다. 정렬 순서는 등록 < 진행 < 테스트 < 완료 < 종료여야 한다(⚠️ 지금 api 의 `sort=status` 는 코드값 글자순이다 — 고칠 것).
- 상위 일감의 상태를 하위에서 계산하지 않는다(서로 따로 바꾼다).

<a id="is-dates"></a>
### 5.1 날짜

| 날짜 | 열 | 누가 넣나 |
|---|---|---|
| 완료 기한 | `due_date` | 사람(언제까지 끝내야 하나) |
| 시작일 | `start_date` | 사람 |
| 완료 예정일 | `planned_end_date` | 사람(언제 끝낼 셈인가) |
| 완료일 | `completed_on` | 상태가 `DONE` 이 되면 자동. `issue.edit` 이 있으면 직접 고친다 |
| 종료일 | `closed_on` | 상태가 `CLOSED` 가 되면 자동. `issue.edit` 이 있으면 직접 고친다 |

**자동 채움** — 한 요청(만들기 · 고치기)에서 요청의 값을 먼저 넣고, 상태가 바뀌었으면 아래를 한다. 같은 요청에서 완료일 · 종료일을 직접 줬으면 준 값을 쓴다(자동 채움은 그 날짜를 요청에 주지 않았을 때만). "오늘"은 api 의 기준 시간대 **Asia/Seoul**(지금은 고정 — 워크스페이스 시간대 설정은 나중)의 날짜다. 만들 때부터 `DONE` · `CLOSED` 면 그때도 채운다.

| 바뀐 상태 | 완료일 `completed_on` | 종료일 `closed_on` |
|---|---|---|
| → `DONE` | 비어 있으면 오늘 | 그대로 |
| → `CLOSED` | 그대로 | 비어 있으면 오늘 |
| → `NEW` · `IN_PROGRESS` · `TESTING` | 비운다 | 비운다 |

**검사** — 요청 값과 자동 채움을 모두 적용한 결과로 본다. 두 값이 모두 있을 때만 검사하고, 어기면 400 `INVALID_ISSUE_DATES`(어긴 필드 이름을 담는다).

| 검사 | |
|---|---|
| 시작일 ≤ 완료 예정일 | |
| 시작일 ≤ 완료 기한 | |
| 완료일 ≤ 종료일 | |

- 셋 다 DB `CHECK` 로도 막는다([데이터 모델 §5.4](02-data-model.md#dm-issue)). 상태와의 관계(자동 채움)는 서비스 규칙이다.
- 자동으로 채우거나 비운 날짜도 그 요청의 이력 줄에 전 · 후로 남는다.
- **기한 지남**: 열린 일감(`NEW` · `IN_PROGRESS` · `TESTING`)의 완료 기한이 오늘보다 앞이면 목록 · 보기에 "기한 지남"(`overdue: true`)으로 보인다. 닫힌 일감에는 보이지 않는다.
- 날짜를 다른 일감(상위 · 선행)에 맞춰 옮기지 않는다(나중 후보 — 간트 · 일정).

---

<a id="is-people"></a>
## 6. 관리자 · 담당자

| | 관리자 `manager_id` | 담당자 `assignee_id` |
|---|---|---|
| 뜻 | 그 일감을 책임지는 사람(예: 요구사항 관리자) | 그 일감을 하는 사람(예: 요구사항 담당자) |
| 수 | 한 명, **필수**. 비우려 하면 400 `ISSUE_MANAGER_REQUIRED` | 한 명 또는 없음. 비울 수 있다 |
| 처음 | 만든 사람. 다른 사람으로 정하려면 `issue.manager.assign` 도 있어야 한다(없으면 403 `PERMISSION_REQUIRED`). MCP 로 만들면 만든 사람 = 키 주인이다 | 만들 때 줄 수 있다(`issue.create` 로 충분) |
| 바꾸기 | `issue.edit` + `issue.manager.assign` (없으면 403, 같은 요청의 다른 필드도 바뀌지 않는다) | `issue.edit` |
| 자격 | 그 프로젝트의 **독자인 활성 멤버**. 아니면 400 `USER_NOT_PROJECT_READER`(워크스페이스 멤버가 아닌 사람도 같은 답) | 같다 |

- moai-wiki 의 페이지 소유자(`owner_id` · `page.owner.assign`)와 같은 자리다. 담당자는 moai-wiki 에 없어 더했다. 한 일감에 관리자 · 담당자를 여러 명 둘지는 [정해야 할 것](../wbs/08-decisions.md)에 있다(제안: 각각 한 명).
- 자격은 **새로 정할 때만** 본다(관리자 · 담당자가 바뀌는 요청). 정한 뒤 그 사람이 워크스페이스를 떠나거나 · 제거되거나 · 정지되거나 · 프로젝트 독자가 아니게 되면 값은 그대로 두고, 응답의 사람 꼴에 `active: false`("떠난 멤버")로 보인다. 그 사람을 새로 지정할 수는 없다. 저절로 다른 사람에게 넘기지 않는다 — 누가 맡을지 추측할 수 없다. 목록에서 그 사람으로 걸러 찾아 바꾼다. 그 일감의 다른 필드를 고치는 것은 막지 않는다.
- 관리자 · 담당자라고 권한이 더 생기지 않는다(권한은 코드가 정한다). "나에게 맡겨진 일감"은 목록 필터(`assignee=me` · `manager=me`)로 본다.
- 고를 수 있는 사람 목록은 `GET …/projects/{project}/people`([프로젝트 설계 §6](04-project.md#pj-api)).

---

<a id="is-tree"></a>
## 7. 상위 · 하위 일감

moai-wiki 의 페이지 트리(설계 14 §3)와 같은 인접 목록이다.

| 항목 | 규칙 | 어기면 |
|---|---|---|
| 상위 | `parent_id`. 비면 최상위다. **같은 프로젝트**의 지우지 않은 일감이어야 한다(DB 복합 외래 키 `(project_id, parent_id)` → `(project_id, id)` + 서비스) | 400 `PARENT_NOT_FOUND` |
| 순환 | 자기 자신이나 자기 하위를 상위로 둘 수 없다 | 400 `PARENT_CYCLE` |
| 깊이 | 최상위가 1이고 **10까지**다. 옮길 때는 새 상위의 깊이 + 옮길 묶음의 높이 ≤ 10 | 400 `PARENT_DEPTH_EXCEEDED` |
| 정하는 곳 | 만들 때(`parent`) · 고칠 때(`PATCH` 의 `parent`, `null` 이면 최상위로). `issue.edit` + `rowVersion` | |
| 동시 옮기기 | 두 사람이 A 를 B 아래로, B 를 A 아래로 동시에 옮기면 순환이 생길 수 있다 → 상위를 바꾸는 트랜잭션은 **프로젝트마다 하나씩** 한다(`pg_advisory_xact_lock(프로젝트 id 해시)`, moai-wiki 의 위키 단위 잠금과 같다) | |
| 하위 | 보기 응답에 하위 일감(번호 · 유형 · 제목 · 상태 · 담당자, 번호순)이 온다. 목록에서 `parent={번호}` 로도 거른다 | |
| 지우기 | 지우지 않은 하위가 있으면 지우지 못한다(먼저 옮기거나 지운다) | 409 `ISSUE_HAS_CHILDREN` |
| 이력 | 상위가 바뀌면 **그 일감**의 이력에 `parent` 변경(전 · 후 번호)을 남긴다 | |

```sql
-- 새 상위의 깊이 (경로 질의 — 줄 수가 깊이)
WITH RECURSIVE up AS (
    SELECT id, parent_id, 1 AS lvl FROM issue WHERE id = :new_parent_id
    UNION ALL
    SELECT i.id, i.parent_id, up.lvl + 1 FROM issue i JOIN up ON i.id = up.parent_id WHERE up.lvl < 11
)
SELECT max(lvl) AS depth FROM up;

-- 옮길 묶음의 순환 · 높이
WITH RECURSIVE sub AS (
    SELECT id, 1 AS h FROM issue WHERE id = :moving_id
    UNION ALL
    SELECT c.id, s.h + 1 FROM issue c JOIN sub s ON c.parent_id = s.id
    WHERE c.deleted_at IS NULL AND s.h < 11
)
SELECT bool_or(id = :new_parent_id) AS makes_cycle, max(h) AS height FROM sub;
-- makes_cycle 이 거짓이고, depth + height ≤ 10 이어야 한다
```

---

<a id="is-move"></a>
## 7-1. 프로젝트 옮기기 (2026-11-24)

프로젝트는 일감의 속성이라 바꿀 수 있다 — Redmine 의 Project 칸 · 옮기기와 같다([ADR-030](00-key-decisions.md#adr-030), [FR-ISS-024](../requirements/03-issue.md#fr-iss-024)). 고치기(`PATCH /workspaces/{ws}/issues/{number}`)의 `project` 에 옮길 프로젝트의 주소 이름을 넣는다. 같은 요청에 다른 필드(유형 · 상위 · 모듈 · 사람 …)를 함께 바꿀 수 있고, 그 값은 **옮길 프로젝트 기준**으로 본다. MCP 는 `update_issue` 의 `moveToProject`.

| 규칙 | 내용 | 어기면 |
|---|---|---|
| 권한 | 이 프로젝트의 `issue.edit`(입구가 본다) + 옮길 프로젝트의 `issue.create`(서비스가 본다) | 403 `PERMISSION_REQUIRED` |
| 옮길 프로젝트 | 같은 워크스페이스에서 내가 읽을 수 있는(독자 ∧ `issue.read`) 프로젝트. 없거나 읽을 수 없으면 같은 오류(어느 쪽인지 숨긴다). 보관한 프로젝트면 쓸 수 없다 | 400 `TARGET_PROJECT_NOT_FOUND` · 409 `PROJECT_NOT_WRITABLE` |
| 하위 일감 | 하위 줄기를 통째로 함께 옮긴다(지운 하위까지 — 상위 FK 가 같은 프로젝트를 요구한다). 상위 FK 는 그 트랜잭션에서 커밋 때 본다(`SET CONSTRAINTS fk_issue_parent_same_project DEFERRED`) | |
| 상위 | 옮기는 일감의 상위는 옛 프로젝트에 남으니 상위 연결을 끊는다(이력에 `parent` 옛 번호 → 없음). 같은 요청에 `parent` 로 옮길 프로젝트의 일감을 주면 그것이 새 상위다(순환 · 깊이 규칙 그대로) | 400 `PARENT_NOT_FOUND` |
| 유형 | 옮길 프로젝트에서 쓸 수 있어야 한다 — 유형은 아직 워크스페이스 공통이라 쓰는 유형(`active`)이면 된다(프로젝트별 유형이 생기면 그 프로젝트에 켠 유형). 옛 유형이면 같은 요청에서 `type` 을 바꾼다. 함께 옮길 하위 일감의 유형은 먼저 고친다(문구에 번호) | 400 `ISSUE_TYPE_NOT_ALLOWED` |
| 사람 | 관리자 · 담당자(하위 일감 것도)는 옮길 프로젝트를 읽을 수 있는 활성 멤버여야 한다 | 400 `USER_NOT_PROJECT_READER` |
| 모듈 | 모듈은 프로젝트마다다. 같은 key 가 옮길 프로젝트에 **쓰는 모듈**로 있으면 그것으로 잇고, 없으면 뺀다(이력 `modules` · 감사 `droppedModules`). 같은 요청에 `modules` 를 주면 옮길 프로젝트의 모듈로 그대로 | 400 `UNKNOWN_MODULE` · `MODULE_INACTIVE` |
| 따라가는 것 | 첨부 · 댓글 · 작업 시간 · 연결 · 이력 · 판은 일감 id 에 묶여 그대로 따라간다. 첨부 행의 `project_id` 도 바꾼다(V23 트리거가 그 일감의 지금 프로젝트로만 허용). 저장소의 객체 이름(`ws/…/p/<올릴 때의 프로젝트>/…`)은 그대로다 — 받기는 첨부 id · 일감으로 찾으므로 그대로 된다. 공수 집계는 옮긴 일감의 옛 작업 시간도 새 프로젝트로 센다 | |
| 번호 · 주소 | 그대로다(정식 주소에 프로젝트가 없다) | |
| 기록 | 옮긴 일감마다 이력 한 줄(`{field: project, before: {key, name}, after: {key, name}}` + 바뀐 상위 · 모듈 · 다른 필드)과 새 판(판에 `project_id`), 감사 `issue.move` 하나(`from` · `project` · `number` · `moved`(옮긴 번호들) · `parentCleared` · `droppedModules` · `via`) + 다른 필드도 바꿨으면 `issue.update`. 관리자 · 담당자에게 "내용이 바뀜" 알림(필드 `project`) | |

화면은 고치기 양식의 프로젝트 고르기(내가 일감을 만들 수 있는 프로젝트 — 프로젝트 목록의 `myActions` 에 `createIssue`)와, 바꿨으면 저장할 때 확인 창(하위도 함께 · 상위 끊김 · 모듈 key 로 · 번호 그대로)이다. 옮기는 동안 상위 · 모듈 고르기는 막고 서버가 정한다.

<a id="is-links"></a>
## 8. 연결 일감

| 종류 `type` | 원본(source)에서 보면 | 대상(target)에서 보면 |
|---|---|---|
| `RELATES` | 관련 | 관련 |
| `BLOCKS` | 차단함 — 이 일감이 끝나야 대상을 할 수 있다 | 차단됨 |
| `PRECEDES` | 선행함 — 이 일감이 대상보다 먼저다 | 후행함 |
| `DUPLICATES` | 중복함 — 이 일감이 대상의 중복이다 | 중복됨 |

| 규칙 | 내용 | 어기면 |
|---|---|---|
| 걸기 | `POST …/issues/{number}/links` `{target, type}` — 경로의 일감이 원본. `issue.edit` | |
| 같은 워크스페이스 (2026-11-24) | 대상은 같은 워크스페이스의 지우지 않은 일감(워크스페이스 번호로 준다) — 다른 프로젝트여도 된다(Redmine 의 관계처럼). 부른 사람이 지금 읽을 수 있어야 한다 — 읽을 수 없으면 없는 번호와 같은 오류. 대상 프로젝트가 보관이면 409 | 400 `LINK_TARGET_NOT_FOUND` · 409 `PROJECT_NOT_WRITABLE` |
| 읽을 수 없는 쪽 (2026-11-24) | 보기 · MCP 의 `links` 에는 지금 읽을 수 있는 프로젝트의 일감만 나온다 — 읽을 수 없는 쪽은 빠진다(있다는 것도 숨긴다, "독자가 아니면 404" 와 같은 뜻). 이력 줄의 `link` 값은 번호만 담는다(제목 없음). 다른 프로젝트 일감과 이은 알림에는 상대 제목을 싣지 않는다(번호만) | |
| 자기 자신 | 안 된다 | 400 `INVALID_ISSUE_LINK` |
| 한 쌍에 하나 | 같은 두 일감 사이에는 방향 · 종류와 상관없이 연결 하나(DB 고유 인덱스) | 409 `ISSUE_LINK_EXISTS` |
| 양쪽에 보인다 | 한 행이 두 일감 모두의 보기에 나온다 — 각자의 방향(`OUTGOING` = 이 일감이 원본, `INCOMING` = 대상)과 그 쪽 말로 | |
| 풀기 | `DELETE …/issues/{number}/links/{linkId}` — `issue.edit`. 그 연결이 경로의 일감에 걸려 있어야 한다(어느 쪽이든) | 404 `ISSUE_LINK_NOT_FOUND` |
| 잠금 | 연결은 일감의 필드가 아니므로 `rowVersion` 을 받지 않고 바꾸지도 않는다(다른 사람의 고치기와 부딪히지 않는다) | |
| 이력 · 감사 | 걸고 풀면 **두 일감 모두**의 이력에 `link` 변경을 남기고, 감사 `issue.link.add` · `issue.link.remove` 를 남긴다 | |
| 일정 | 첫 출시에는 연결이 일정 · 상태를 바꾸지 않는다(`BLOCKS` 가 닫기를 막지 않고, `PRECEDES` 가 날짜를 옮기지 않는다) | |
| 일감을 지우면 | 그 일감의 연결 행을 함께 지우고, 상대 일감 이력에 `link` 빼기를 남긴다 | |

<a id="is-journal"></a>
## 9. 이력

일감을 바꿀 때마다 `issue_journal` 에 한 줄을 더한다(쌓기만 한다). 만들기 · 고치기 · 상위 바꾸기 · 연결 걸기 · 풀기 · 댓글 · 댓글 고치기 · 지우기마다 한 줄이고, 한 요청은 한 줄이다(연결은 두 일감에 한 줄씩). 이력은 업무와 같은 트랜잭션에 저장한다. moai-wiki 의 리비전 자리이지만 본문 판이 아니라 **필드 변경 목록**이다.

| 열 | 내용 |
|---|---|
| `actor_type` | `USER`(웹 · MCP — MCP 로 한 일은 키 주인). V2 의 CHECK 는 `SERVICE` 도 받지만 MCP 는 쓰지 않는다(서비스 키 없음 — [ADR-024](00-key-decisions.md#adr-024)) |
| `actor_id` · `actor_name` | 사람 id · 그때의 표시 이름 |
| `via` | `WEB`(REST — 웹 화면) · `MCP` |
| `mcp_key_id` (4단계) | MCP 로 온 줄이면 그 키 id — 그 사람이 어느 키(엔드포인트)로 했는지 남긴다([데이터 모델 §6](02-data-model.md#dm-mcp)) |
| `changes` | `[{"field", "before", "after"}]` — 한 요청에서 바뀐 필드 전부. 만들 때는 처음 값을 `before: null` 로 |
| `notes` | 댓글(Markdown). 댓글만 달면 `changes` 가 빈 줄. 고치면서 댓글을 함께 달면 한 줄에 둘 다 |

**`changes` 의 필드 이름과 값 꼴**

| `field` | `before` · `after` 의 값 |
|---|---|
| `type` · `status` | key · 상태 값 (`"bug"` · `"DONE"`) |
| `title` | 글 |
| `description` | 글 전문(화면은 차이로 보인다) |
| `manager` · `assignee` | `{"id", "name"}` 또는 null — 이름은 그때의 표시 이름 |
| `parent` | 일감 번호 또는 null |
| `dueDate` · `startDate` · `plannedEndDate` · `completedOn` · `closedOn` | `"2026-11-28"` 또는 null. 자동 채움으로 바뀐 것도 남긴다 |
| `link` | `{"type", "direction", "number"}` 또는 null — 걸면 `before: null`, 풀면 `after: null`. `direction` 은 이 일감에서 본 방향(`OUTGOING` · `INCOMING`), `number` 는 상대 일감 |
| `workLog` · `attachment` | 작업 시간 기록 · 첨부 — 더하면 `before: null`, 빼면 `after: null` |
| `agentSession` (2026-11-23) | 세션 이름 글자 또는 null |
| `modules` (2026-11-23) | `[{"key", "name"}]`(모듈 순서, 그때의 이름) 또는 null(없음) — 묶음이 바뀔 때만(차례만 다르면 바뀐 것이 없다) |
| `comment` (2026-11-23) | `{"id", "author": {"id", "name"}}` — 댓글(이력 줄) id 와 쓴 사람(그때의 이름). 고치면 전 · 후 둘 다, 지우면 `after: null`. **글은 넣지 않는다**(지운 댓글의 글이 이력으로 새지 않게) |

```json
{ "id": "…", "actorType": "USER", "actorId": "…", "actorName": "표시 이름", "via": "MCP",
  "createdAt": "2026-11-22T03:10:00Z", "notes": null,
  "changes": [ { "field": "status", "before": "IN_PROGRESS", "after": "DONE" },
               { "field": "completedOn", "before": null, "after": "2026-11-22" } ] }
```

- 화면은 "상태를 진행 → 완료로 바꿈", "완료일을 2026-11-22 로 정함" 처럼 문장으로 보이고, `via = MCP` 면 "MCP" 칩을 붙인다(누르면 키 이름 · 엔드포인트). 이력을 전체 · 댓글만 · 변경만으로 거른다.
- 보기 응답에는 이력을 **오래된 것부터 100줄** 싣고, 더 있으면 이어 받기 커서(`journalCursor`)를 준다. 나머지는 `GET …/issues/{number}/journal?cursor=` 로 받는다.
- 이력 줄을 고치거나 지우는 API 는 없다. 댓글(`notes`)만 그 자리에서 고치고 지운다([§10](#is-comments)). DB 트리거(`issue_journal_protect`, V14)가 이력 줄 지우기 · 누가 · 경로 · 때 · `changes` 바꾸기를 막고, 판 번호는 처음 한 번만 적게 한다.
- **감사**: 일감의 쓰기는 감사에도 남긴다 — `issue.create`(프로젝트 · 번호 · 유형) · `issue.update`(바뀐 필드 이름) · `issue.comment` · `issue.comment.update` · `issue.comment.delete`(댓글 id · 쓴 사람) · `issue.link.add` · `issue.link.remove` · `issue.delete`(번호 · 제목). `details.via` 에 경로를, MCP 면 엔드포인트 id · 키 id · 도구 이름을 함께 넣는다([MCP 설계 §15](06-mcp.md#mc-audit)). 본문 · 댓글 원문은 감사에 넣지 않는다.

<a id="is-comments"></a>
## 10. 댓글

- `POST …/issues/{number}/comments` `{notes}` — `issue.comment`(기본 역할은 모두 가진다). 본문은 Markdown, 앞뒤 공백을 뺀 1~30,000자. 비면 400 `INVALID_COMMENT`, 넘으면 400 `COMMENT_TOO_LONG`.
- `notes` 만 있는 이력 한 줄이 된다. 일감 행은 바꾸지 않는다(`rowVersion` · `updated_at` 그대로 — 댓글이 다른 사람의 고치기와 부딪히지 않게). 감사 `issue.comment`.
- 닫힌 일감에도 달 수 있다. 보관한 프로젝트에는 달 수 없다(409 `PROJECT_NOT_WRITABLE`).
- 고치면서 함께 다는 댓글: `PATCH` 본문에 `notes` 를 넣으면 같은 이력 줄의 `notes` 가 된다(그때는 서비스가 `issue.comment` 도 본다).
- 멘션은 나중 후보다. 알림은 [알림 설계](07-notification.md) — 앞서 댓글을 단 사람을 셀 때 지운 댓글은 빼고, 고치기 · 지우기는 알리지 않는다.

**고치기 · 지우기 (2026-11-23 — [FR-ISS-019](../requirements/03-issue.md#fr-iss-019), V14)** — 사용자: "아이템은 덧글도 달 수 있어야 해" · MCP 로 "덧글, 첨부파일 포함" CRUD. moai-wiki 에는 없는 기능이라 첨부 · 작업 시간 기록의 지움 표시 방식을 따랐다.

| 항목 | 규칙 |
|---|---|
| 자리 | 댓글은 이력 줄(`issue_journal.notes`) 그대로이고 그 자리에서 고치고 지운다 — 이력 줄기 안의 제자리에 남는다. 댓글 id = 이력 줄 id. 일감을 고치면서 함께 단 댓글(같은 줄에 `changes` 가 있다)도 같다 — 지우면 댓글만 가려지고 바뀐 필드는 남는다 |
| 고치기 | **쓴 사람만**, 그리고 지금 `issue.comment` 가 있어야 한다(`@ProjectApi(issue.comment)` + 서비스가 쓴 사람인지 본다). `project.manage` 가 있어도 남의 댓글은 고치지 못한다(403 `PERMISSION_REQUIRED`). 글 검사는 달기와 같다(1~30,000자) |
| 앞 글 | 고칠 때마다 바뀌기 전 글을 `issue_comment_revision`(판 · 글 · 고친 사람 · 경로 · 밀리초)에 쌓는다 — 잃지 않는다. 트리거가 앞 글이 남은 것을 확인한 뒤에만 글을 바꾸게 하고, 쌓인 줄은 고치거나 지울 수 없다. 옛 글을 보는 화면 · API 는 아직 없다 |
| 동시 수정 | 댓글마다 판(`notes_row_version`, 처음 0). 고칠 때 읽은 `rowVersion` 을 보낸다 — 없으면 400 `ROW_VERSION_REQUIRED`, 다르면 409 `CONCURRENT_UPDATE`(응답에 지금 `rowVersion`). 같은 댓글을 동시에 바꾸지 못하게 줄을 잠근다. 같은 글이면 아무것도 하지 않는다. 일감의 `rowVersion` · `updated_at` · 판은 그대로다 |
| 고친 표시 | `editedAt`(마지막으로 고친 때, 밀리초) — 화면은 "고침 3분 전", 밀리초 시각은 도구 설명 |
| 지우기 | **쓴 사람, 또는 `project.manage`**. REST 는 독자면 부를 수 있게 `@ProjectApi(issue.read)` 로 열고 나머지는 서비스가 본다 — 지금 댓글 권한을 잃었어도 자기 댓글은 지운다. 지움 표시(`notes_deleted_at` · `notes_deleted_by`)만 하고 행 · 글은 DB 에 남는다. 이미 지운 댓글을 고치거나 다시 지우면 409 `COMMENT_DELETED` |
| 지운 댓글 | REST(보기 · 목록) · MCP 어디에서도 글을 내주지 않는다 — `deleted: true` · `notes: null` · 지운 사람 · 때. 화면은 제자리에 "지운 댓글"(누가 · 언제). 트리거가 지운 댓글의 글 · 표시를 더 바꾸지 못하게 막는다 |
| 첨부 | 댓글과 함께 올린 첨부는 일감의 첨부로 그대로 남는다(첨부 탭에서 따로 지운다) |
| 이력 · 감사 | 고치기 · 지우기는 이력에 한 줄(`field: comment`, 판은 아님 — 첨부 더하기 · 빼기와 같다)과 감사 `issue.comment.update` · `issue.comment.delete`(`via` WEB · MCP) |
| 목록 | `GET …/issues/{n}/comments?page&size` — 댓글만 오래된 것부터 쪽으로(크기 1~50, 기본 20). `{number, items, total, page, size, totalPages, hasNext}`. MCP `list_comments` 가 같은 서비스를 쓴다 |
| 보관 | 보관한 프로젝트에서는 고치기 · 지우기 모두 409 `PROJECT_NOT_WRITABLE` |
| MCP | `list_comments` · `update_comment` · `delete_comment`([MCP 설계 §8.20](06-mcp.md#mc-t-comments)) |

<a id="is-attachments"></a>
## 10-1. 첨부파일 · 본문 이미지 (2026-11-22)

> 사용자: "첨부파일도 넣을 수 있어야 하고 본문에도 이미지를 중간중간" · "내용, 덧글, 첨부파일 탭으로" · "파일 저장소 구조 잘 설계 … 너무 한곳에 다 모아 두면 문제" — [FR-ISS-018](../requirements/03-issue.md#fr-iss-018) · [ADR-027](00-key-decisions.md#adr-027)

| 항목 | 규칙 |
|---|---|
| 저장 | 파일 원문은 객체 저장소(MinIO · S3 호환, 버킷 `moai-works-attachments`, 버전 보관)에만. DB `issue_attachment`(V9)는 메타데이터 — 일감 · 원래 이름 · 형식 · 크기 · SHA-256 · 이미지인지 · 객체 이름 · 올린 사람 · 경로(WEB · MCP) · 밀리초 · 지움 표시 |
| 객체 이름 | `ws/<워크스페이스 id>/p/<프로젝트 id>/<연>/<월>/<첨부 id(UUID v7)>` — 워크스페이스 · 프로젝트 · 달로 나뉘어 한곳에 몰리지 않고, 워크스페이스 · 프로젝트 단위로 세고 · 옮기고 · 지우기 쉽다. 원래 이름은 객체 이름에 넣지 않는다(이름 충돌 · 경로 장난 막기) |
| 올리기 | `POST …/issues/{n}/attachments` multipart `file` — `issue.comment`. 받으면서 임시 파일 · SHA-256 · 크기(메모리에 올리지 않는다). 파일 하나 상한 `moai-works.storage.max-file-mb`(200) · 워크스페이스 총 용량 `max-workspace-gb`(20) → 413 `FILE_TOO_LARGE` · `WORKSPACE_STORAGE_FULL`. 빈 파일 400. 보관한 프로젝트 409 |
| 이미지 판별 | 첫 바이트로 png · jpeg · gif · webp 만 이미지. 이름 · 받은 형식은 믿지 않는다. SVG 는 이미지로 보지 않는다(스크립트를 품을 수 있다) |
| 받기 | `GET …/attachments/{id}/content` — `issue.read`(독자가 아니면 404). 이미지는 inline · 그 형식, 그 밖은 늘 `attachment` · `application/octet-stream`. `X-Content-Type-Options: nosniff` · `Content-Security-Policy: default-src 'none'; sandbox` · `Cache-Control: private` · `ETag` = SHA-256 |
| 미리보기 (2026-11-23, 일감 #19) | `GET …/attachments/{id}/preview` — 원본 받기와 **같은 판정**(`@ProjectApi(issue.read)`, 그 일감의 살아 있는 첨부 — 독자가 아니면 404)과 **같은 머리글**(inline · `nosniff` · `Content-Security-Policy: default-src 'none'; sandbox` · `Cache-Control: private` · `ETag` = SHA-256 + `-preview320`). 긴 변 320px PNG(작은 그림은 키우지 않는다 · 투명 · 글자 그대로, 원본의 메타데이터는 따라가지 않는다). 서버가 첫 바이트로 알아낸 **png · jpeg · gif(첫 장)** 만, 그 형식의 ImageIO 리더로 — **WebP 는 JDK 에 디코더가 없어(새 라이브러리 없이) 만들지 않고 화면이 원본을 쓴다**. **처음 받을 때 만든다**(올리기는 그대로 빠르고, 예전에 올린 그림도 처음 볼 때 생긴다) — 원본 옆 `ws/<워크스페이스>/p/<프로젝트>/<연>/<월>/<첨부 id>.preview` 에 두고 다음부터는 그것을 준다. 압축 폭탄 막기: 크기만 먼저 읽고 40메가픽셀을 넘으면 풀지 않는다, 건너 읽기(subsampling)로 목표의 두 배쯤만 메모리에, 원본은 임시 파일 캐시로 읽음, 동시에 둘까지(기다림 20초 넘으면 503 `PREVIEW_BUSY`). JPEG 는 EXIF 방향 표시대로 돌린다. 깨진 그림 · 너무 큰 그림은 빈 표시 객체(0바이트)를 남겨 다시 풀지 않고 404 `ATTACHMENT_PREVIEW_NONE` — 저장소에서 읽다 난 실패는 표시를 남기지 않는다(503 `STORAGE_READ_FAILED`, 다음에 다시). 목록의 `preview`(png · jpeg · gif 면 true)로 화면이 고른다. MCP 도구는 없다(`list_attachments` 결과에도 넣지 않고 티켓 받기는 늘 원본). 정리 작업이 원본과 함께 지운다. 워크스페이스 용량에는 세지 않는다(첨부 원본 크기만) |
| 본문에 넣기 | 올린 응답의 `markdown` — 이미지 `![이름](attachment:<id>)`, 파일 `[이름](attachment:<id>)`. 화면의 Markdown 은 `attachment:` 를 그 일감의 받기 주소로 바꾼다(그 밖의 스킴 규칙은 그대로) |
| 지우기 | `DELETE …/attachments/{id}` — 올린 사람 또는 `project.manage`. 지움 표시만(누가). DB 트리거가 지우기 · 값 바꾸기를 막는다. 객체는 보관 기간 동안 남는다(아래 정리) |
| 정리 (2026-11-23, 일감 #18) | 지운 지 `moai-works.storage.purge-after-days`(기본 30일, 0 이면 끔)가 지난 첨부를 정리 작업(`AttachmentPurger`, 매일 03:30 Asia/Seoul — `purge-cron`)이 저장소에서 지운다. 접두사 = 원본 객체 이름이라 원본 · 그 옆 미리보기 · **버전 보관분 · 지움 표시까지** 함께 지운다(첨부 id 가 UUID 라 다른 첨부는 걸리지 않는다). 기간 안의 것 · 지우지 않은 것은 그대로. DB 행은 남기고 정리한 때 `purged_at`(V17)만 한 번 — 보호 트리거가 "살아 있는 행의 지움 표시 한 번"과 "지운 행의 정리 표시 한 번"만 허락한다(moai-wiki `document_raw.purged_at` 과 같은 모양). 결과는 워크스페이스마다 감사 `issue.attachment.purge`(행위자 SYSTEM "첨부 정리", details = 첨부 수 · 바이트 · 객체 수 · 보관 날 수 · 기준 시각) 한 줄과 로그 한 줄. 표시와 감사는 한 트랜잭션이고, 객체를 지운 뒤 표시를 못 했으면 다음 차례에 다시 지운다(없으면 0개). 한 차례 1000개까지, 저장소 실패가 셋 이어지면 남은 것은 다음 차례. 여러 인스턴스는 DB 권고 잠금으로 한 곳만. 워크스페이스 용량은 지운 첨부를 이미 세지 않으므로 그대로다 |
| 이력 · 감사 | 더하고 뺀 것이 이력 한 줄(`field: attachment`, 판은 아님). 감사 `issue.attachment.add` · `issue.attachment.remove`(경로 포함) |
| 화면 | 일감 보기를 내용 · 댓글 · 첨부파일 탭으로(`?tab=`). 첨부 탭에서 올리기(끌어 놓기) · 받기 · 표시 복사 · 지우기. 줄마다 작은 그림 — `preview` 면 미리보기 주소(2026-11-23), 미리보기가 없는 이미지(WebP)는 원본, 파일은 파일 표시, 그림을 못 받으면(404 — 깨진 그림) 파일 표시로 바꾼다. 작은 그림 · 열기는 원본을 연다. 편집기에 이미지를 붙여 넣거나 끌어 놓으면 올리고 그 자리에 표시를 넣는다(일감을 만든 뒤부터) |
| MCP | 파일은 MCP 메시지에 싣지 않는다 — 일회용 티켓으로 REST([MCP 설계 — 첨부 티켓](06-mcp.md#mc-attach-ticket)) |

<a id="is-templates"></a>
## 10-2. 유형별 템플릿 (2026-11-23)

> 사용자(2026-11-23): "이슈의 유형별로 작성 템플릿 넣어 주고 그걸 관리할 수 있게 해 줘. 또한 mcp 제공해서 그에 맞게 이슈 작성 및 수정할 수 있게" — [FR-ISS-020](../requirements/03-issue.md#fr-iss-020). 결정: 워크스페이스 + 프로젝트 · MCP 관리 도구도 · 빠진 항목은 알려만 줌.

| 항목 | 규칙 |
|---|---|
| 저장 | `issue_template`(V12) — 워크스페이스 기본(`project_id` 없음) · 프로젝트 덮어쓰기. (워크스페이스, 유형) · (프로젝트, 유형)마다 하나, `row_version` |
| 고르는 순서 | 프로젝트 행 → 워크스페이스 행 → 코드의 처음 템플릿(`IssueTemplateService.BUILTIN`). 행을 지우면(되돌리기) 한 단계 위로 |
| 유형 | 지금 쓰는 유형(새기능 · 버그 · 테스트 · 기술조사)만. 옛 유형은 `ISSUE_TYPE_RETIRED`, 없는 유형은 `UNKNOWN_ISSUE_TYPE` |
| 권한 | 읽기 `workspace.read`(워크스페이스) · `issue.read`(프로젝트). 고치기 · 되돌리기 `workspace.settings.manage`(워크스페이스) · `project.manage`(프로젝트). MCP 도 같은 서비스에서 다시 본다 |
| 감사 | `issue_template.update` · `issue_template.reset`, details = 범위(WORKSPACE · PROJECT) · 유형 · 프로젝트 key · 길이 · 경로(WEB · MCP) |
| 동시 수정 | 이미 정한 템플릿을 고칠 때 읽은 `rowVersion` 을 보낸다. 다르면 409 `CONCURRENT_UPDATE`. 처음 정할 때는 비운다 |
| 항목 | 템플릿의 제목(# ~ ######, 코드 블록 밖)마다 하나. 끝이 "(선택)" · "(optional)" 이면 비워도 되는 항목 |
| 화면 | 워크스페이스 설정의 "일감 템플릿" 칸(`IssueTemplateManager`, 고치기 `workspace.settings.manage`)과 프로젝트의 일감 템플릿 화면(`…/projects/:key/templates`, 프로젝트 메뉴 한 줄의 "일감 템플릿"은 `project.manage` 가 있는 사람에게만 — 보기는 독자 누구나). 왼쪽에 유형 목록(세로 탭 — 유형이 늘어도 된다, 좁으면 고르기 상자), 출처 칩(처음 템플릿 · 워크스페이스 기본 · 이 프로젝트 · 물려받음), 고친 사람 · 밀리초 시각, 항목 목록, 편집기(그림 · 파일 올리기는 끈다 — 템플릿에는 첨부가 없다), "템플릿 저장"(처음 정할 때는 판을 보내지 않고, 409 면 고친 글을 남긴 채 다시 읽는다), 한 단계 위로 되돌리기(확인 대화 상자). 권한이 없으면 읽기만. 새 일감은 템플릿을 받으면 처음 유형의 템플릿으로 빈 설명을 채우고, 유형을 바꾸면 설명이 비었거나 앞 유형의 템플릿 그대로일 때만 새 템플릿으로 바꾼다(고치기 화면은 유형을 고를 때만). 빠진 필수 항목은 편집기 아래 경고(`shared/lib/issue-template.ts` — 서버와 같은 규칙) |
| 빠진 항목 | 설명에 같은 제목(수준 · 대소문자 · 공백 무시, 코드 블록 밖)이 없는 필수 항목. **알려만 준다** — MCP 결과의 `templateCheck` · 화면 경고. 저장은 막지 않는다. 2026-11-23 다시 정함(사용자: "왜 일감들이 유형별 템플릿 양식 규칙을 안 지킬까?" → "지금처럼 알리기만" · "열린 것만 고치기"): 템플릿 전에 옮겨 넣은 일감과 경고를 무시한 Agent 때문에 안 지켜졌다. 막지 않는 대신 Agent 는 등록 전에 `get_issue_template` 을 받고 `templateCheck.missing` 을 채운다(스킬 · 각 세션에 알림), 이미 있는 일감은 열린 것만 다시 쓴다 |

처음 템플릿:

| 유형 | 항목 |
|---|---|
| 새기능 `feature` | 배경 · 요구사항(체크 목록 — 모두 체크되면 완료) · 범위 밖(선택) · 처리 결과(선택) |
| 버그 `bug` | 현상 · 재현 절차 · 기대 결과 · 실제 결과 · 환경(선택) · 확인 방법(체크 목록) · 처리 결과(선택) |
| 테스트 `test` | 목적 · 대상 · 테스트 항목(체크 목록) · 테스트 결과(선택) |
| 기술조사 `research` | 조사 목적 · 배경 · 조사 항목(체크 목록) · 결론(선택) |

2026-11-23 다시 짬(사용자: "하려는 것, 수용 기준이 비슷한 것인가? 요구사항?" · "유형별 템플릿 다시 고민해 봐") — 같은 내용을 두 번 적던 새기능의 "하려는 것 · 수용 기준"을 요구사항 하나로 합치고, 흔히 쓰는 우리말(재현 절차 · 기대 결과 · 실제 결과 · 테스트 항목 · 조사 목적)로 맞췄다. 등록된 일감 본문도 같은 규칙으로 바꿨다.

처리 결과(시험은 결과, 기술조사는 결론)는 완료할 때 채운다 — 바뀐 것 · 커밋 · 확인한 것 · 화면 캡처 · 남은 것. 일감을 열면 최종 결과가 본문에서 바로 보이고, 댓글은 과정 기록이다(2026-11-23 사용자: "왜 본문에 처리한 최종 정리 화면이나 내용을 안 적어?").

<a id="is-editor"></a>
## 10-3. Markdown 편집기 (2026-11-23)

> 사용자(2026-11-23): "이렇게 md 편집기를 만들고 이슈의 본문이나 덧글은 모두 md 로 작성할 수 있게 해 줘"(VS Code Markdown Editor 화면) — [FR-ISS-021](../requirements/03-issue.md#fr-iss-021). 저장 형식은 그대로 Markdown 이다.

| 항목 | 규칙 |
|---|---|
| 편집기 | Milkdown Crepe(`@milkdown/crepe` · `@milkdown/kit` 7.23.0). 쓰면서 바로 그려진다. 화면 코드는 편집할 때 따로 받는다(lazy, `RichEditor`). Vditor 는 실행 중에 Lute 등을 CDN 에서 받아 화면 보안 정책(`script-src 'self'`)과 맞지 않아 뺐다. Milkdown 은 보기(react-markdown)와 같은 remark 계열이다 |
| 켜는 · 끄는 기능 | 켬: 할 일 목록 체크 칸 · 표 · 코드 블록(CodeMirror) · 링크 고치기 · 빈 칸 안내 · 커서. 끔: 블록 그림(저장할 때 이름 자리에 크기 비율을 적어 `![이름](attachment:…)` 를 망가뜨린다) · 수식 · AI(바깥 서비스) · 위 막대(TopBar) · 블록 메뉴 · 떠 있는 막대(영어 글 · 위 고정 막대와 겹침). `crepe-setup.ts` 한 곳에서 정한다 |
| 고치는 설정 | moai-wiki 가 같은 판으로 찾은 것과 같다: 새 목록 기호는 `-`(`remarkStringifyOptionsCtx` 의 `bullet` — 원문 보존이 손댄 덩어리만 편집기 글로 바꾸므로 새 목록이 기존 글과 같은 기호), 빈 문단을 `<br />` 로 적는 기능(`remarkPreserveEmptyLinePlugin`)은 만들기 전에 뺀다(`startCrepe` — 사람이 쓰지 않은 태그가 생긴다). 글자를 고르지 않고 굵게를 켜면 글이 바뀌지 않으므로 명령 직후 `storedMarks` 를 한 번 읽는다 |
| 글을 올리는 때 | moai-wiki 995b202 와 같다. 편집기의 알림(plugin-listener 의 `updated` · `markdownUpdated`)은 쓰지 않는다 — 둘 다 200ms 로 묶여 늦게 오고, 알림 안에서 보기 상태를 읽으면 만들어지는 중에는 예외로 create() 가 거절된다. 대신 ProseMirror 플러그인의 `view.update` 에서 글이 바뀌었는지(`doc.eq`) 직접 본다: ① "바뀜"은 그 순간 직렬화 없이 바로 알린다(`onPending` — 저장 단추 · "저장 전" 표시가 친 순간 켜진다) ② 글(`getMarkdown` → 원문 보존)은 마지막 입력 뒤에 한 번 올린다 — 쓰는 동안 문서 전체를 적지 않는다. 기다리는 시간은 글 크기에 맞춘다(`syncDelay`: 8천 자 아래 120ms · 2만 자 아래 300ms · 그 위 600ms, moai-wiki 1232b13 과 같다). 올릴 때 하는 일은 바뀐 곳만 한다(일감 #16, 2026-11-23): 편집기 쪽은 바뀐 맨 위 덩어리만 다시 적고(`block-markdown.ts` — 맨 위 마디 묶음마다 적은 글을 WeakMap 에 기억, 이어진 목록 · 목록 뒤 코드는 한 묶음, 맨 위 이음은 빈 줄 하나 · 글 끝 빈 문단은 적지 않음, 모르는 마디 · 규칙이 바뀐 것 같으면 통째로. 편집기가 준비될 때 덩어리로 적은 글이 통째로 적은 글과 다르면 그 편집기는 늘 통째로 적는다), 원문 보존 쪽은 원문 · baseline 을 한 번만 나누고(`createPreserver`, 한가할 때 미리) 지금 글은 바로 앞 글에서 바뀐 곳 둘레만 다시 나눈다(`resplit` — 이음매가 앞뒤에 따라 달리 읽힐 수 있으면 통째로). 저장되는 글은 통째로 적고 통째로 나눈 것과 한 글자도 다르지 않다(시험 자료 55건 · 고침 · 되돌림 · 무작위 고침 시험). 잰 값은 이 칸 끝에 ③ 바로 올리기(`flush`)는 편집기가 사라질 때(effect 정리, destroy 전) · 저장 · 원문 전환 · 유형 바꾸기 앞 · 편집 칸을 떠날 때. 저장하는 쪽은 `useMarkdownEditor().flush()` 가 돌려주는 글을 쓴다(그 순간 상태 값은 아직 옛 글일 수 있다) ④ 목차 · 눌림 표시는 같은 view.update 에서 다음 화면 그리기(requestAnimationFrame)에 읽고, 같으면 다시 알리지 않는다. 30000자 문서(목록 · 표 · 코드 섞임)에서 잰 값: 쓰는 동안 키 하나당 주 스레드 약 7–8ms 는 그대로, 멈춘 뒤 일은 약 220ms(긴 작업 2번) → 약 115ms(1번). 친 바로 다음(1–5ms 뒤) 원문 전환 · 저장 · 댓글 달기 · 템플릿 탭 옮기기에서 방금 친 글이 남는 것을 브라우저로 확인했다. 일감 #16 뒤(실제 일감 글을 이은 30366자, 가운데 문단에 20자, 같은 때 번갈아 잼): 멈춘 뒤 일 약 190–255ms(긴 작업 170–240ms) → 약 26–70ms(긴 작업 없음, 스크립트 12–26ms), 그 가운데 편집기 · 원문 보존은 약 5ms 이고 나머지는 값이 바뀐 양식을 다시 그리는 일이다. 키 하나당 6–9ms 는 그대로. 친 바로 다음 저장(요청은 가로채 보내지 않음)에 방금 친 글이 들어 있고 원문과 다른 곳은 그 글자뿐, 친 바로 다음 원문 전환에도 남는 것을 다시 확인했다 |
| 도구 막대 | 위에 고정(`EditorToolbar`). 목차 · 제목/문단 · 굵게 · 기울임 · 취소선 · 링크 | 글머리 · 번호 · 할 일 · 표 | 인용 · 코드 블록 · 줄 안 코드 · 가로줄 | 되돌리기 · 다시 | 이미지 · 파일 넣기 · Markdown 원문. 도구 설명에 단축키 |
| 목차 | 설명 편집기에서 편집 칸이 900px 이상이면 처음부터 연다(편집 칸의 받은 너비). 제목을 누르면 그 자리로. 댓글에는 없다 |
| 원문 보기 | "Markdown 원문"을 누르면 CodeMirror 로 원문을 고친다(`MarkdownSourceEditor`). 돌아오면 그 글로 편집기를 다시 만든다 |
| 원문 보존 | 편집기는 저장할 때 글 전체를 다시 적는다(목록 기호 · 표 칸 맞춤 · 번호 기호 …). 손대지 않은 덩어리는 원문 그대로 둔다 — `source-blocks.ts`(moai-wiki 와 같은 사본). 편집기를 만든 직후의 글을 baseline 으로 잡고, 바뀔 때마다 `preserveSource(원문, baseline, 지금)` 을 바깥 값으로 올린다: 원문 ↔ baseline 은 맨 위 덩어리를 자리로, baseline ↔ 지금은 글자로 견준다. 같은 덩어리는 원문 조각, 다른 덩어리만 편집기 글. 편집기는 참조 링크의 정의 줄(`[이름]: 주소`)을 버리고 링크를 풀어 적는데, 정의 줄을 빼고 세어 자리로 짝짓고 정의 줄은 원문 자리에 남긴다(위키 166bbd8). 그래도 덩어리 수가 다르면 고친 뒤에는 편집기 글을 그대로 쓴다. 원문 조각과 편집기 글을 이웃으로 붙일 때 다시 읽으면 합쳐지는 것은 편집기 글로 바꾼다(`untangle`, 일감 #16): 같은 표지의 목록이 이어짐(원문 `*` 목록 뒤 편집기가 `*` 로 적은 목록 · `1)` 뒤 `1)` — 이어진 같은 종류 목록 전체를 편집기 글로, 편집기는 기호를 번갈아 쓴다), 목록 · 각주 정의 · 들여 쓴 코드 바로 뒤의 들여 쓴 코드(→ ```), 목록 뒤 덩어리의 앞 들여쓰기(뗀다, 원문 조각인 목록은 편집기 글로). 원문에서 이미 이웃이던 두 조각은 그대로. 편집기 글에는 원문 자리의 앞 들여쓰기를 붙이지 않는다(들여 쓴 목록을 고치면 안쪽 항목이 형제가 되던 것). 이 고침은 moai-wiki 에 알린다. 한계: 덩어리 단위라 긴 목록 · 표의 한 칸을 고치면 그 덩어리 전체가 편집기 모양이 된다 |
| 원문 보존 시험 | 실제 일감 설명 · 댓글 39건(moai-works · moai-wiki, MCP 로 모음)과 어려운 경우 16건을 실제 편집기에 열자마자 꺼낸 글과 짝지어 둔다(`__fixtures__/roundtrip.json`, 개발 서버의 `dev/editor-roundtrip.html` 로 만든다). 55건 가운데 51건은 편집기가 글자를 바꾸지만, 보존 함수를 거치면 55건 모두 원문 그대로다. 55건 모두 자리로 짝지을 수 있고(참조 링크 포함), 마지막 덩어리를 고치면 그 덩어리만 바뀐다 |
| 첨부 그림 | 그림 마디가 HTML 로 적히는 모양(schema 의 toDOM · parseDOM)을 바꾼다(`safe-image.ts`, moai-wiki 와 같은 방식): `attachment:<id>` 이고 그 일감의 첨부로 풀릴 때만 같은 출처 주소의 `<img>`, 나머지는 설명 글 `<span data-image-*>`. 마디의 이름 · 주소 · 제목은 그대로라 저장되는 Markdown 이 바뀌지 않는다. nodeView 가 아니라 schema 를 바꾸는 까닭 — 글자로 붙여 넣기 · insert 는 마디를 DOMSerializer(schema 의 toDOM)로 적었다 읽어, 문서 밖 `<img src>` 도 불러오기를 시작한다([SECURITY.md §9](../../SECURITY.md#9-화면web-frontend의-보안-규칙)) |
| 올리기 | 붙여 넣기 · 끌어 놓기 · 단추 → 그 일감에 올리고 그 자리에 "업로드 중… 이름" 표시를 두었다가 받은 표시로 바꾼다(실패하면 지운다). 자리는 글이 바뀌어도 따라간다. Markdown 을 넣을 때 HTML DOM 을 거치지 않고 해석기로 바로 노드를 만든다 — Milkdown 의 insert(inline) · insertPos 는 DOM 을 거쳐 `<img src>` 가 문서 밖에서 받기를 시작하고 제목을 덧붙인다. 파일은 `handleDOMEvents`(paste · drop)에서 받는다(`editor-guard.ts`) — Crepe 에 늘 들어 있는 올리기 플러그인(@milkdown/plugin-upload)이 `handlePaste` · `handleDrop` 을 먼저 가져가 이미지는 `blob:` 그림으로 넣고 그 밖의 파일은 버리던 것(2026-11-23 고침). 그 플러그인은 만들기 전에 떼고(남기면 "파일 + 글자" 붙여넣기의 글자까지 사라진다), uploader 도 빈 결과만 내게 바꿔 `blob:` 그림이 생길 길을 없앤다(moai-wiki 와 같다). 붙여넣기를 파일로 볼지는 두 편집기가 같은 규칙(`pastedFiles`): 파일만 · 파일 + 그림 HTML(이미지 복사) · 파일 + 파일 이름뿐인 글자(탐색기 복사)는 올리고, 파일 + 서식 있는 글(표 · 문서) · 파일 + 실제 글자는 글로 붙인다 |
| 보안 | HTML 은 편집 중에도 글자로 보인다(Milkdown html 노드 = textContent). 편집 중에는 링크를 열지 않는다. 링크 넣기는 http · https · mailto · 이 화면 안의 경로 · 첨부 표시만(`link-rules.ts`). [SECURITY.md §9](../../SECURITY.md#9-화면web-frontend의-보안-규칙) |
| 모양 | 글 모양은 보기와 같다(`markdownBodySx` 를 `.milkdown .ProseMirror` 아래로). Crepe 의 색 변수는 화면 테마의 CSS 변수로 채워 다크 · 라이트를 따른다 |
| 시험 | 화면 시험(jsdom)은 시험용 편집기(`editor/__mocks__/RichEditor.tsx`, test-setup 의 vi.mock)로 바꾼다. 순수 함수(원문 보존 · 링크 규칙)는 그대로 시험한다. 덩어리로 적기(`block-markdown.test.ts`)는 실제 편집기(`crepe-setup`)를 jsdom 에서 띄워(IntersectionObserver 만 흉내) 통째로 적은 글과 견준다 — 시험 자료 55건 그대로 · 앞 · 가운데 · 끝 고침 · 지움 · 합침 · 되돌림, 이어진 목록 함정, 3만 자에서 한 글자 고치면 한 묶음만 적기. `resplit` 은 통째로 나눈 것과 무작위 고침 4천여 번을 견준다 |

<a id="is-agent-session"></a>
## 10-4. 에이전트 세션 (2026-11-23)

> 사용자(2026-11-23, 일감 #26): "이슈 아이템에 필수는 등록 내용은 아니야 agent session name 항목 추가해 줘" · "필터 조건에도 넣고" · "mcp 기능도 당연히 추가하고" · "스킬에는 꼭 추가해" · "여러 세션들이 한 프로젝트에서 작업하는데 어떤 세션의 작업인지 모르겠다" — [FR-ISS-022](../requirements/03-issue.md#fr-iss-022)

| 항목 | 규칙 |
|---|---|
| 뜻 | 이 일을 맡은 AI Agent 세션의 이름(예: moai-works · moai-wiki · echomind-world). 사람(관리자 · 담당자)과 따로 — MCP 키는 사람의 것이라 한 사람의 여러 세션을 이 이름으로 가른다 |
| 값 | `issue.agent_session`(V20) — 앞뒤 공백을 빼고 1~100자, 비면 없음(null). 제어 문자(줄바꿈 등)는 400 `INVALID_AGENT_SESSION`. DB CHECK 도 같은 규칙 |
| 정하기 | 만들기 · 고치기(`issue.create` · `issue.edit`)의 `agentSession` — null · 빈 글이면 비운다. 이력(`field: agentSession`) · 판 · 감사(바뀐 필드 이름)에 남는다 |
| 알림 | 세션만 바뀌면 관리자 · 담당자에게 "내용 바뀜"(`issue.updated`)을 보내지 않는다 — 누가 맡았는지 적는 꼬리표다 |
| 거르기 | 목록 `agentSession=이름`(대소문자 무시) · `none`(세션 없음). 고르기 칸의 값은 `GET …/agent-sessions` — 이 프로젝트의 지우지 않은 일감에 쓰인 이름들(이름순, 일감 수) |
| MCP | `create_issue` · `update_issue`(`clear` 에 `agentSession`) · `search_issues` 의 `agentSession` · `get_issue` · 찾기 결과([MCP 설계 §8.21](06-mcp.md#mc-t-modules)). Agent 는 만들 때와 진행으로 옮길 때 자기 세션 이름을 넣는다(스킬) |
| 화면 | 새 일감 · 수정 양식의 입력 칸(안내 "이 일을 맡은 AI 세션 이름 — 비워도 된다"), 목록 제목 아래 칩(로봇 아이콘 · `aria-label` "에이전트 세션: 이름"), 세션 거르기(모든 세션 · 세션 없음 · 쓰인 이름, 주소 `?agentSession=`), 보기 속성의 셋째 줄 |

<a id="is-modules"></a>
## 10-5. 관련 모듈 (2026-11-23)

> 사용자(2026-11-23, 일감 #27): "이슈 등록할 때 어떤 모듈을 수정 중인지도 알아야겠지?" · "이슈에 멀티로 관련 모듈을 등록할 수 있게 해 줘" · "등록 수정이겠지 당연히" — [FR-ISS-023](../requirements/03-issue.md#fr-iss-023). 모듈 목록 · 관리는 [프로젝트 설계 — 모듈](04-project.md#pj-modules).

| 항목 | 규칙 |
|---|---|
| 표 | `issue_module(project_id, issue_id, module_id)`(V21) — 일감 하나에 여럿. 복합 외래 키 `(project_id, issue_id)` → `issue` · `(project_id, module_id)` → `project_module` 이라 같은 프로젝트끼리만 |
| 정하기 | 만들기 · 고치기의 `modules` — 모듈 key 목록(대소문자 무시, 겹치면 하나). 고치기는 **통째로 바꾼다**(null · [] 이면 모두 뺀다). 같은 묶음을 다른 차례로 주면 바뀐 것이 없다(이력 · 판 없음) |
| 검사 | 이 프로젝트에 없는 key → 400 `UNKNOWN_MODULE`(문구 · `modules` 에 그 key 들). 쓰지 않는 모듈을 새로 붙이면 400 `MODULE_INACTIVE` — 이미 붙어 있던 것은 그대로 둘 수 있다(일감 유형의 `ISSUE_TYPE_RETIRED` 와 같은 규칙). 값의 꼴이 틀리면 400 `INVALID_FIELD` |
| 이력 · 판 · 감사 | 바뀌면 이력 `field: modules`(`[{key, name}]` 전 · 후, 비면 null) · 새 판(`issue_revision.modules`) · 감사 `issue.update` 의 `fields` 에 `modules`. 내용이 바뀐 것이라 "내용 바뀜" 알림이 간다 |
| 응답 | 보기 · 목록의 `modules: [{key, name, active}]` — 모듈 순서(position · key). 판 보기는 그때의 `[{key, name}]` |
| 거르기 | 목록 `module=` 를 여러 번 — 그 가운데 **하나라도** 붙은 일감(EXISTS). 쓰지 않는 모듈로도 거른다(옛 일감 찾기). 없는 key 는 400 `UNKNOWN_MODULE` |
| MCP | `list_modules` 로 key 를 보고, `create_issue` · `update_issue` 의 `modules`(배열, `clear` 에 `modules`) · `search_issues` 의 `module`(쉼표) · `get_issue` · 찾기 결과([MCP 설계 §8.21](06-mcp.md#mc-t-modules)) |
| 화면 | 양식의 "관련 모듈" 여럿 고르기(쓰는 모듈 + 이미 붙은 쓰지 않는 모듈, 칩으로 보임 — 모듈이 없으면 칸을 끄고 모듈 화면을 알림), 목록 칩(모듈 아이콘, 쓰지 않는 모듈은 점선 · "(쓰지 않음)"), 모듈 거르기(여럿, 주소 `?module=a&module=b`, 모듈이 있을 때만), 보기 속성 셋째 줄의 칩, 이력 문장 "관련 모듈: API, 문서 → …" |

<a id="is-delete"></a>
## 11. 지우기

| 항목 | 내용 |
|---|---|
| 권한 | `issue.delete` — 기본 역할로는 소유자 · 관리자, 프로젝트 역할로는 `project-admin`(큐레이터는 못 한다) |
| 방식 | 소프트 삭제(`deleted_at`). 지운 일감은 보기 · 목록 · 연결 · 하위 · MCP 어디에도 없다(404 `ISSUE_NOT_FOUND`) |
| 하위 | 지우지 않은 하위 일감이 있으면 거절한다 — 먼저 옮기거나 지운다(409 `ISSUE_HAS_CHILDREN`) |
| 연결 | 함께 지운다(행 삭제), 상대 일감 이력에 `link` 빼기를 남긴다 |
| 이력 | 그대로 남는다 |
| 번호 | 다시 쓰지 않는다 |
| 감사 | `issue.delete` — 대상 `issue` · id, `details` 에 프로젝트 key · 번호 · 제목 |
| 화면 | 번호 · 제목을 보이고 확인을 받는다 |
| MCP | 지우기 도구는 없다([MCP 설계 §12](06-mcp.md#mc-not-do)) |
| 되살리기 | 첫 출시에는 없다(데이터는 남는다) |

<a id="is-lock"></a>
## 12. 동시 수정

- `PATCH …/issues/{number}` 본문에 **`rowVersion` 이 필수**다(없으면 400 `ROW_VERSION_REQUIRED`). 일감의 지금 `row_version` 과 다르면 409 `CONCURRENT_UPDATE` 이고 아무것도 바꾸지 않는다. 같으면 바꾸고 `updated_at` 을 고치고 `row_version` 을 1 올려 응답에 새 값을 준다.
- 실제로 바뀐 값이 없으면 이력을 남기지 않고 `rowVersion` 도 그대로 두고 200 으로 답한다.
- 상위 바꾸기도 `PATCH` 이므로 `rowVersion` 을 받는다. 댓글 · 연결은 받지 않는다(일감의 필드가 아니다).
- 409 응답(problem+json)에는 지금 `rowVersion` 을 넣는다. 화면은 "다른 사람이 먼저 고쳤습니다" 를 알리고, 입력한 값을 지우지 않은 채 최신 값을 다시 불러 견주게 한다. MCP 는 지금 `rowVersion` 과 그 뒤 바뀐 필드 이름을 돌려준다(서버가 합치지 않는다).

---

<a id="is-revision"></a>
## 리비전 · 타임라인 · 차이 (2026-11-22)

> "프로젝트의 아이템들은 리비전을 모두 관리해야 한다. 리비전별로 변화도 타임라인으로 보여 줘야 하고 변화한 것도 git 처럼 diff 처럼" · "당연히 누가 수정했는지도" — [ADR-025](00-key-decisions.md#adr-025) · [FR-ISS-015](../requirements/03-issue.md#fr-iss-015). moai-wiki 의 페이지 리비전(`page_revision` · `LineDiff` · 판 목록 · 차이 API)과 같은 모양이다.

| 항목 | 규칙 |
|---|---|
| 판 | 만들면 1판, 필드(유형 · 제목 · 설명 · 상태 · 관리자 · 담당자 · 상위 · 다섯 날짜 · 예상 공수 · 에이전트 세션 · 관련 모듈 — 2026-11-23 둘 더함)가 바뀔 때마다 새 판. `issue.revision_no` 가 지금 판 |
| 담는 것 | `issue_revision` — 그 시점의 일감 전체(V20 `agent_session` · V21 `modules` — `[{key, name}]` 그때의 이름) + 누가(`actor_id` · `actor_name` · `actor_type`) · 어느 길로(`via` WEB · MCP) · 언제(`created_at`, 밀리초) · 그 판을 만든 이력 줄(`journal_id`) |
| 판이 아닌 것 | 댓글 · 연결 · 작업 시간 기록 — 이력(`issue_journal`)에만 남는다. 필드를 바꾼 이력 줄에는 `revision_no` 가 붙는다 |
| 덮어쓰지 않음 | DB 트리거가 `issue_revision` 의 UPDATE · DELETE 를 막는다 |
| 판 목록 | `GET …/issues/{n}/revisions?before=&limit=` — 새 판부터(최대 50), `{items:[{no, actorType, actorId, actorName, via, createdAt, changedFields, current}], next}` |
| 한 판 | `GET …/issues/{n}/revisions/{no}` — 그 판의 일감 전체 |
| 차이 | `GET …/issues/{n}/revisions/{no}/diff?from=` — `from` 이 비면 바로 앞 판, 1판은 "없음"과. 필드는 `{field, before, after}`(사람은 `{id, name}`), 설명은 `LineDiff`(앞뒤 같은 줄을 걷어 내고 가운데만 LCS, 너무 크면 통째로 지우고 넣음 = `coarse`) → `descriptionLines[{op SAME · ADD · DEL, text, oldLine, newLine}]` · `added` · `removed`. `SAME_REVISION` 400 · `REVISION_NOT_FOUND` 404 |
| 화면 | 일감 보기의 **리비전 탭**(첨부파일 옆, `issues/:number?tab=revisions`, 2026-11-23 사용자: "리비전을 첨부파일 옆에 탭으로 해서 추가하고 타임라인으로 볼 수 있게 해줘" · "위에 상태 옆에 있는데 첨부파일 옆 탭으로 구성해") — 리비전 타임라인(새 리비전부터, 누가 · 언제(밀리초) · MCP 표시 · 바뀐 필드), 고른 리비전의 앞 리비전과의 차이, 아무 두 리비전 견주기, 한 리비전 전체 보기. 탭 이름에 리비전 수. 차이는 git 처럼 줄 번호 · `−`/`+` · 빨강/초록(색만으로 전하지 않음). 고른 리비전은 주소(`?tab=revisions&rev=&from=&view=`)에 남는다. 머리(상태 옆)의 "현재 리비전 N" 칩 · 리비전 아이콘은 없앴다. 예전 따로 화면 주소 `issues/:number/history` 는 이 탭으로 돌린다(고른 리비전을 가져감) |
| 시각 | 변화 기록 시각은 **밀리초까지**(앱 시계가 밀리초 단위 — 2026-11-22 사용자 결정). 화면은 `YYYY-MM-DD HH:mm:ss.SSS` 로 보인다 |

<a id="is-worklog"></a>
## 작업 시간 — 예상 공수 · 소요 시간 기록 (2026-11-22)

> "아이템은 작업 시간 정보도 넣을 수 있어" · "초기에 예상 공수로 계획을 세운다면 일하면서 소요한 시간도 중간중간 추가 … 1건이 아닌 다건" · "꼭 담당자가 아니더라도 … 제한을 둘 필요는 없어" · "소요 시간 입력도 누가 했는지 다 관리" · "나중에 공수 정리 및 실제 투입 공수는 소요시간으로 뽑아야 해. 그래서 소요시간 입력은 매우 중요한 데이터" — [ADR-025](00-key-decisions.md#adr-025) · [FR-ISS-016](../requirements/03-issue.md#fr-iss-016) · [FR-ISS-017](../requirements/03-issue.md#fr-iss-017)

| 항목 | 규칙 |
|---|---|
| 예상 공수 | `issue.estimated_hours` numeric(7,2), 0 ~ 10000 시간. 만들기 · `PATCH` 의 `estimatedHours`. 판에 들어간다(바뀌면 새 판) |
| 기록 | `issue_work_log(issue_id, user_id, work_date, hours numeric(5,2), note, via, created_at, deleted_at, deleted_by)` — 한 일감에 여러 건. 일한 사람 = 부른 사람(남의 시간을 대신 적지 않음) |
| 누가 적나 | 그 일감을 고칠 수 있는 사람(`issue.edit`) 누구나 — 담당자로 제한하지 않는다 |
| 검사 | 0 < 시간 ≤ 24(`INVALID_WORK_HOURS`), 날짜는 오늘까지(`INVALID_WORK_DATE`, "오늘" = Asia/Seoul), 메모 1000자 |
| 지우기 | 자기 기록은 `issue.edit` 로, 남의 기록은 `project.manage` 가 있어야(403). 지움 표시(`deleted_at` · `deleted_by`)만 — **행은 지우지 않고 값도 바꾸지 않는다**(DB 트리거, V4). 고칠 일이 있으면 지우고 새로 적는다 |
| 이력 · 감사 | 적기 · 지우기는 이력 `workLog`(`{id, user{id,name}, date, hours, note}`)와 감사 `issue.work_log.add` · `issue.work_log.remove` 에 남는다. 판은 만들지 않는다 |
| 보이기 | 일감 보기 — 예상 공수 · 쓴 시간 합계 · 기록 목록(누가 · 날 · 시간 · 메모 · 적은 때(밀리초) · MCP 표시). 목록 — 예상 · 쓴 시간 |
| REST | `POST …/issues/{n}/work-logs` `{workDate, hours, note}` · `DELETE …/issues/{n}/work-logs/{id}` — 둘 다 `@ProjectApi(issue.edit)`, 결과는 일감 보기 |
| 집계 | 공수 정리 · 실제 투입 공수는 작업 시간 기록에서 뽑는다 — 사람 · 일감 · 프로젝트 · 유형 · 기간별 예상 대비 실제([아래 공수 집계](#is-effort) · [FR-ISS-017](../requirements/03-issue.md#fr-iss-017)). 그래서 기록은 지우지 않고 바꾸지 않는다 |

<a id="is-effort"></a>
## 공수 집계 — 예상 공수 대비 실제 투입 공수 (2026-11-23)

> "나중에 공수 정리 및 실제 투입 공수는 소요 시간으로 뽑아야 해" · "소요 시간 입력은 매우 중요한 데이터" — [FR-ISS-017](../requirements/03-issue.md#fr-iss-017) · moai-works 일감 #7. 실제 투입 공수는 [작업 시간 기록](#is-worklog)에서만 뽑는다.

| 항목 | 규칙 |
|---|---|
| 입구 | `GET /v1/workspaces/{ws}/effort` · `GET …/effort.csv` — 둘 다 `@WorkspaceApi(workspace.read)`(멤버 누구나). 여러 프로젝트를 모으므로 `@ProjectApi` 가 아니다. 프로젝트 화면의 공수는 같은 입구에 `project=<주소 이름>` 을 준 것이다 |
| 범위 (판정) | **들어가는 프로젝트는 서비스(`EffortService`)가 프로젝트 판정 그대로 정한다** — `ProjectAccessService.contextFor`(독자 · 보관은 관리하는 사람만, `@ProjectApi` 와 같은 규칙) + 그 프로젝트의 `issue.read`. 읽을 수 없는 프로젝트의 기록은 줄 · 합계 · 고르기 목록 어디에도 없다. 조건으로 주면 404 `PROJECT_NOT_FOUND`(있는지 숨김), 독자인데 `issue.read` 가 없으면 403. `usage.read` 로 범위를 넓히지 않는다(2026-11-23 소유자 결정) |
| 세는 기록 | 지우지 않은 일감의 지우지 않은 기록. 조건에 맞지만 지운 기록 수는 `removedLogs` 로 따로 준다("빼고 셌다"는 표시). 지운 일감의 기록은 일감이 어디에도 없으므로(§11) 세지도 표시하지도 않는다 |
| 조건 | `from` · `to`(yyyy-MM-dd, 포함 — 기록의 일한 날) · `project`(주소 이름) · `person`(사용자 id · `me`) · `type`(유형 key) — 셋은 여러 번 줄 수 있고 한 조건 안은 OR, 조건끼리는 AND. 날짜 모양 · 순서, 모르는 사람 모양 · 유형 · 기준은 400 `INVALID_FILTER`. 사람은 떠난 멤버도 고를 수 있다(기록이 남아 있다) |
| 기준 (`groupBy`) | `person` · `project`(기본) · `issue` · `type` · `day` · `week`(월요일 ~ 일요일) · `month`. 한 기준으로 한 줄씩 — 기간은 앞날부터, 그 밖은 실제 투입이 많은 것부터. 기록이 없는 기간 줄은 만들지 않는다 |
| 날 · 시간대 | 기록의 `work_date` 는 적을 때 Asia/Seoul 의 오늘까지로 받은 달력 날짜라 다시 바꾸지 않는다. 화면의 "오늘"(이번 주 · 이번 달 · 올해의 끝날)도 Asia/Seoul |
| 예상 | 일감에 하나(`issue.estimated_hours`, 지금 값)라서 **결과에 든 일감(조건에 맞는 기록이 하나라도 있는 일감)마다 한 번씩** 더한다. 줄마다의 예상은 일감 · 프로젝트 · 유형 기준일 때만(일감이 한 줄에만 속한다), 사람 · 기간 기준은 합계에만. 예상이 있는 일감이 없으면 null. 차이 = 실제 − 예상, 비율 = 실제 ÷ 예상 × 100 반올림(예상이 0 · 없음이면 null). 기간을 좁히면 그 기간에 기록이 있는 일감의 예상만 든다. 유형은 일감의 지금 유형 |
| 응답 | `{from, to, groupBy, rows:[{key, label, project, issue, person, type, period, periodEnd, estimatedHours, actualHours, diffHours, ratio, issueCount, logCount}], total:{estimatedHours, actualHours, diffHours, ratio, issueCount, logCount, personCount}, removedLogs, projects:[{key,name}], types:[{key,name}], people:[{id,username,displayName}]}` — 시간은 소수 둘째 자리. `projects` 는 읽을 수 있는 프로젝트, `people` 은 사람 조건을 뺀 범위에서 기록을 남긴 사람(고르기용) |
| CSV | 같은 집계를 그대로 — 머리 줄(`lang=ko`(기본) · `en`) · 줄마다 [기준 값, 이름, 예상, 실제, 차이, 비율, 일감 수, 기록 수] · 맨 끝 합계. UTF-8 BOM(엑셀 한글), RFC 4180 따옴표, 글자 칸이 `= + - @` · 탭 · CR 로 시작하면 앞에 `'`(수식 주입 막기). 첫 칸은 사람이면 아이디, 일감이면 `주소#번호`. 유형 이름은 서버의 이름(한국어)이다. `text/csv` · `attachment; filename=effort-<기준>-<시작|all>-<끝|all>.csv` · `no-store` · `nosniff`. 내려받으면 감사 `issue.work_log.export`(대상 워크스페이스, 조건 · 줄 수 · `via`) — 화면 보기(JSON)는 남기지 않는다 |
| 화면 | 워크스페이스 메뉴 "공수 집계"(`/w/:slug/effort`)와 프로젝트 메뉴 한 줄의 "공수"(`projects/:projectKey/effort`, 기본 기준 일감별 · 프로젝트 고르기 · 프로젝트별 기준 없음)가 같은 부품(`EffortReport`)이다. 조건 줄(기간: 이번 주 · 이번 달(기본) · 지난 달 · 올해 · 전체 · 직접 고름, 시작 · 끝 날짜, 기준, 프로젝트, 일한 사람, 유형)은 주소에 남는다(`?range=lastMonth&groupBy=person&person=…`). 합계 칸(실제 · 예상 · 차이 · 비율 · 일감 · 기록 · 사람 수 · 기간 · 지운 기록 표시 · 예상 세는 규칙)과 CSV 단추, 표(첫 열 붙임 · 상자 안에서만 옆으로 밀림 · 줄마다 실제 투입 막대(가장 큰 줄 = 100) · 예상 · 차이(기호 `+` · `−`) · 비율(넘기면 말로도) · 맨 끝 합계). 프로젝트 줄은 같은 조건의 그 프로젝트 공수로, 일감 줄은 일감 보기로 간다. 차트 라이브러리는 쓰지 않는다 |
| MCP | 이번에는 도구를 더하지 않는다(나중에 같은 `EffortService` 를 부르는 도구로) |

<a id="is-list"></a>
## 13. 목록 · 필터 · 정렬 · 쪽

`GET …/projects/{project}/issues` — `issue.read`. 경로의 프로젝트에서 지우지 않은 일감만. 서로 다른 필터는 모두 만족(AND), 한 필터의 여러 값은 하나라도(OR)다.

| 매개변수 | 값 | 뜻 |
|---|---|---|
| `status` | 여러 번(`NEW` · `IN_PROGRESS` · `TESTING` · `DONE` · `CLOSED`), 또는 `open` · `closed` | 비면 전부 |
| `type` | 여러 번(유형 key) | |
| `manager` | 사람 id · `me` | |
| `assignee` | 사람 id · `me` · `none`(담당자 없음) | |
| `parent` | 번호(그 일감의 바로 아래) · `none`(최상위만) | |
| `q` | 글 | 제목에 들어 있는가(대소문자 무시). 숫자만(또는 `#12`)이면 그 번호도 찾는다 |
| `agentSession` (2026-11-23) | 세션 이름 · `none` | 그 세션의 일감(대소문자 무시) · 세션이 없는 일감 |
| `module` (2026-11-23) | 여러 번(모듈 key) | 그 가운데 하나라도 붙은 일감. 없는 key 는 400 `UNKNOWN_MODULE` |
| `sort` | `number` · `created` · `updated` · `due` · `start` · `planned` · `status` · `title`, 앞에 `-` 면 내림차순 | API 기본 `-number`. 빈 날짜는 오름 · 내림 모두 뒤에. 같으면 번호로 정한다 |
| `page` · `size` | 0부터 · 1~100 | 기본 0 · 50. 100 을 넘게 달라고 하면 100 |

모르는 필터 값(없는 상태 · 유형, 숫자가 아닌 상위, 사람 id 모양이 아님)은 400 `INVALID_FILTER`, 모르는 정렬은 400 `INVALID_SORT`.

```json
{ "items": [ { "number": 12, "type": "requirement", "title": "…", "status": "IN_PROGRESS",
               "manager": { "id": "…", "username": "…", "displayName": "…", "active": true },
               "assignee": null, "parent": 3, "childCount": 2,
               "dueDate": "2026-12-01", "startDate": "2026-11-20", "plannedEndDate": "2026-11-28",
               "completedOn": null, "closedOn": null, "overdue": false, "updatedAt": "…" } ],
  "total": 37, "page": 0, "size": 50 }
```

- 질의는 `project_id` 와 `deleted_at IS NULL` 위에 필터를 더한다(색인 [데이터 모델 §5.4](02-data-model.md#dm-issue)). 권한 거름은 프로젝트 판정 한 번으로 끝난다(일감마다 거르지 않는다).
- 성능 목표는 [NFR-PERF-001](../requirements/05-non-functional.md#nfr-perf-001)(일감 10만 건 프로젝트에서 p95 800ms). 제목 찾기는 `lower(title) LIKE` 로 시작하고 규모 시험에서 느리면 색인을 더한다(공유 DB 에 확장을 더하지 않는다).
- MCP `search_issues` 는 같은 질의를 커서(불투명 값 안에 쪽 번호)로 연다.
- 저장한 질의 · 내보내기 · 칸반 보드는 나중 후보다. 여러 프로젝트 한꺼번에 보기는 [§13.1](#is-ws-list)(2026-11-24).
- 목록 한 줄에 `project {key, name}` 가 있다(2026-11-24).

<a id="is-ws-list"></a>
### 13.1 워크스페이스 일감 목록 · 찾기 (2026-11-24)

Redmine 의 `/issues` 자리다 — 2026-11-24 사용자: "전체 워크스페이스에서 이슈를 검색할 수도 있어야해"([FR-ISS-025](../requirements/03-issue.md#fr-iss-025)).

- `GET /workspaces/{ws}/issues` — `@WorkspaceApi(workspace.read)`. 들어가는 프로젝트는 서비스가 프로젝트마다 판정한다: 지우지도 보관하지도 않았고, 내가 독자이고 `issue.read` 가 있는 프로젝트의 **합**. 읽을 수 없는 프로젝트의 일감은 목록에도 `total` 에도 들어가지 않는다.
- 매개변수는 [§13](#is-list) 전부 + `project`(주소 이름, 여러 번 — 그 프로젝트들만). 읽을 수 없거나 없는 `project` 는 빠진다(주소에 남은 옛 조건이 깨지지 않게 — 모두 빠지면 빈 목록). `parent` 번호도 워크스페이스 번호이고 고른 프로젝트 안의 것이어야 한다. `module` 은 고른 프로젝트들에서 그 key 의 모듈 모두 — 어느 프로젝트에도 없는 key 만 400 `UNKNOWN_MODULE`.
- `q` 는 제목 · 설명에 든 말, 또는 `#123` · `123`(그 번호) — 번호는 워크스페이스에서 하나라 바로 그 일감이 나온다. 화면은 `#123` 꼴이면 "#123 바로 가기"를 따로 보인다(저절로 옮기지는 않는다).
- 거르기 칸: `GET /workspaces/{ws}/issue-types`(유형) · `GET /workspaces/{ws}/agent-sessions`(읽을 수 있는 프로젝트에서 쓰인 세션) · `GET /workspaces/{ws}/issue-modules`(읽을 수 있는 프로젝트의 모듈 key, 같은 key 는 하나) — 모두 `@WorkspaceApi(workspace.read)`.
- 색인 `ix_issue_ws_list (workspace_id, status, number DESC) WHERE deleted_at IS NULL`.
- MCP `search_issues` 는 `project` 를 비우면 이 목록(워크스페이스는 `workspace` 인자 — 여럿이면 `WORKSPACE_REQUIRED`), `project` 에 쉼표로 여럿 주면 그 프로젝트들(읽을 수 없는 key 는 `PROJECT_NOT_FOUND`)이다.

---

<a id="is-actions"></a>
## 14. 행동별 규칙

"쓸 수 있다"는 그 프로젝트에서 그 코드가 있고, 프로젝트가 `ACTIVE` 라는 뜻이다(보관이면 409 `PROJECT_NOT_WRITABLE`).

| 행동 | 코드 | 규칙 | 실패 |
|---|---|---|---|
| 유형 목록 · 일감 목록 · 보기(하위 · 연결 · 이력 포함) | `issue.read` | 프로젝트 독자 | 404 |
| 만들기 | `issue.create` | 유형 · 제목 필수. 관리자를 나 아닌 사람으로 정하면 `issue.manager.assign` 도. 담당자 · 상위 · 날짜 규칙 | 403 · 400 · 409 |
| 제목 · 설명 · 유형 · 상태 · 날짜 · 담당자 고치기 | `issue.edit` | `rowVersion` 같음. 날짜 · 자격 규칙 | 403 · 400 · 409 |
| 관리자 바꾸기 | `issue.edit` + `issue.manager.assign` | 같은 `PATCH` 안에서 서비스가 더 본다. 받는 사람의 자격 | 403 · 400 |
| 상위 바꾸기 | `issue.edit` | 같은 프로젝트 · 순환 · 깊이 | 400 · 409 |
| 연결 걸기 · 풀기 | `issue.edit` | 같은 워크스페이스 · 대상을 읽을 수 있음 · 한 쌍에 하나 | 400 · 404 · 409 |
| 프로젝트 옮기기 (2026-11-24) | `issue.edit` + 옮길 프로젝트의 `issue.create` | [§7-1](#is-move) — 하위 함께 · 상위 끊김 · 유형 · 사람 · 모듈 | 403 · 400 · 409 |
| 워크스페이스 일감 목록 (2026-11-24) | `workspace.read` + 프로젝트마다 `issue.read` | 읽을 수 있는 프로젝트의 합([§13.1](#is-ws-list)) | 404(워크스페이스) |
| 댓글 | `issue.comment` | 본문 1~30,000자 | 403 · 400 |
| 댓글 목록 | `issue.read` | 오래된 것부터 쪽으로, 지운 댓글은 글 없이 | 404 |
| 댓글 고치기 | `issue.comment` | 쓴 사람만 · 댓글 `rowVersion` 같음 · 지우지 않은 댓글 | 403 · 400 · 404 · 409 |
| 댓글 지우기 | `issue.read` | 쓴 사람 또는 `project.manage` · 지우지 않은 댓글 | 403 · 404 · 409 |
| 지우기 | `issue.delete` | 하위 없음 | 403 · 409 |

- 읽을 수 있는 사람에게는 403 을, 읽을 수 없는 사람에게는 404 를 준다.
- 보기 응답의 `myActions` 로 화면이 단추를 숨긴다: `edit` · `comment` · `changeManager` · `delete`(권한 코드 + 프로젝트 상태 + 하위 유무로 계산 — moai-wiki 페이지 응답의 `myActions` 와 같은 방식).

---

<a id="is-api"></a>
## 15. REST

2026-11-24 부터 두 갈래다([ADR-030](00-key-decisions.md#adr-030)):

- **일감 하나(번호)** — `/moai-works-api/v1/workspaces/{workspace}/issues/{number}` 아래, **모두 `@IssueApi(코드)`**(그 일감의 지금 프로젝트로 판정 — 일감이 없거나 독자가 아니면 404 `ISSUE_NOT_FOUND`, 코드가 없으면 403). 아래 표의 `/issues/{number}…` 는 이 경로다(첨부 `…/attachments…` 도 — [§10-1](#is-attachments)).
- **프로젝트의 일감** — `/moai-works-api/v1/workspaces/{workspace}/projects/{project}` 아래의 유형 · 템플릿 · 목록 · 쓰인 세션 · 만들기, `@ProjectApi(코드)`.
- **워크스페이스의 일감** — `/moai-works-api/v1/workspaces/{workspace}` 아래의 `GET /issues`(목록) · `GET /issue-types` · `GET /agent-sessions` · `GET /issue-modules` · `GET /projects/{projectKey}/issue-refs/{number}`(옛 링크 → `{number}`), `@WorkspaceApi(workspace.read)`([§13.1](#is-ws-list) · [§4](#is-number)).

| 일 | 메서드 · 경로 | 접근 어노테이션 | 응답 |
|---|---|---|---|
| 유형 목록 | `GET /issue-types` | `@ProjectApi(issue.read)` | `[{key, name, description}]` (순서대로) |
| 템플릿 (2026-11-23, [§10-2](#is-templates)) | `GET /issue-templates` · `GET /issue-templates/{type}` | `@ProjectApi(issue.read)` | 쓰이는 템플릿 `[{type, typeName, body, source, inherited, sections, updatedBy, updatedAt, rowVersion}]` |
| 템플릿 덮어쓰기 · 되돌리기 | `PUT /issue-templates/{type}` `{body, rowVersion}` · `DELETE /issue-templates/{type}` | `@ProjectApi(project.manage)` | 쓰이는 템플릿. 워크스페이스 기본은 `/workspaces/{ws}/issue-templates[/{type}]` — `@WorkspaceApi(workspace.read)` · `(workspace.settings.manage)` |
| 목록 | `GET /issues?status&type&manager&assignee&parent&q&agentSession&module&sort&page&size` | `@ProjectApi(issue.read)` | [§13](#is-list) |
| 쓰인 에이전트 세션 (2026-11-23) | `GET /agent-sessions` | `@ProjectApi(issue.read)` | `[{name, issueCount}]` — 지우지 않은 일감에 쓰인 이름(이름순). [§10-4](#is-agent-session) |
| 만들기 | `POST /issues` | `@ProjectApi(issue.create)` | 201 + 보기 |
| 보기 | `GET /issues/{number}` | `@IssueApi(issue.read)` | 보기(`project` · 상위 · 하위 · 연결(읽을 수 있는 것만) · 이력 · `myActions`) |
| 이력 이어 받기 | `GET /issues/{number}/journal?cursor=` | `@IssueApi(issue.read)` | `{journal: [...], journalCursor}` |
| 고치기 · 옮기기 | `PATCH /issues/{number}` | `@IssueApi(issue.edit)` | 보기. 관리자 바꾸기는 서비스가 `issue.manager.assign`, `notes` 는 `issue.comment`, `project`(옮기기)는 옮길 프로젝트의 `issue.create` 도 본다([§7-1](#is-move)) |
| 지우기 | `DELETE /issues/{number}` | `@IssueApi(issue.delete)` | 204 |
| 댓글 | `POST /issues/{number}/comments` | `@IssueApi(issue.comment)` | 보기 |
| 댓글 목록 (2026-11-23) | `GET /issues/{number}/comments?page&size` | `@IssueApi(issue.read)` | `{number, items: [{id, actorType, actorId, actorName, via, createdAt, notes, rowVersion, editedAt, deleted, deletedAt, deletedBy, withChanges}], total, page, size, totalPages, hasNext}` |
| 댓글 고치기 | `PATCH /issues/{number}/comments/{commentId}` `{notes, rowVersion}` | `@IssueApi(issue.comment)` | 보기. 쓴 사람인지는 서비스가 본다 |
| 댓글 지우기 | `DELETE /issues/{number}/comments/{commentId}` | `@IssueApi(issue.read)` | 보기. 쓴 사람 또는 `project.manage` 인지는 서비스가 본다 |
| 연결 걸기 | `POST /issues/{number}/links` | `@IssueApi(issue.edit)` | 보기 |
| 연결 풀기 | `DELETE /issues/{number}/links/{linkId}` | `@IssueApi(issue.edit)` | 보기 |

관리자 · 담당자 고르기 목록은 `GET …/projects/{project}/people`([프로젝트 설계 §6](04-project.md#pj-api)). 관련 모듈로 고를 목록은 `GET …/projects/{project}/modules`([프로젝트 설계 §6-1](04-project.md#pj-modules)). 여러 프로젝트를 모으는 공수 집계(`GET /workspaces/{ws}/effort` · `…/effort.csv`)는 이 경로 밖의 `@WorkspaceApi(workspace.read)` 이고, 들어가는 프로젝트를 서비스가 프로젝트 판정으로 거른다([공수 집계](#is-effort)).

**요청 꼴**

```java
record CreateRequest(@NotBlank String type, @NotBlank @Size(max = 200) String title,
    @Size(max = 30000) String description, Issue.Status status,   // 없으면 NEW
    UUID managerId,                                               // 없으면 나 (MCP 면 키 주인)
    UUID assigneeId, Integer parent,
    LocalDate dueDate, LocalDate startDate, LocalDate plannedEndDate, LocalDate completedOn, LocalDate closedOn,
    BigDecimal estimatedHours,
    String agentSession,                                          // 2026-11-23 — 없어도 된다
    List<String> modules) {}                                      // 2026-11-23 — 모듈 key, 없어도 된다

/*
 * 고치기 — JSON 객체. 넣은 필드만 바꾸고(null 이면 비운다), 넣지 않은 필드는 그대로다.
 * 받는 키: rowVersion(필수) · type · title · description · status · managerId · assigneeId · parent
 *         · dueDate · startDate · plannedEndDate · completedOn · closedOn · estimatedHours · agentSession · modules(통째로)
 *         · notes(함께 다는 댓글) · project(옮길 프로젝트의 주소 이름 — 2026-11-24, §7-1)
 * 모르는 키는 400 UNKNOWN_FIELD, 값의 꼴이 틀리면 400 INVALID_FIELD.
 */

record CommentRequest(@NotBlank @Size(max = 30000) String notes) {}

record CommentEditRequest(@NotBlank @Size(max = 30000) String notes, Integer rowVersion) {}   // rowVersion 은 그 댓글의 판(비면 400 ROW_VERSION_REQUIRED)

record LinkRequest(@NotNull Integer target, @NotNull IssueLink.Type type) {}   // 경로의 일감이 원본. target 은 워크스페이스 번호(다른 프로젝트도 된다)
```

**보기 응답 꼴**

```json
{ "number": 12, "project": { "key": "web", "name": "웹" }, "type": "requirement", "title": "…", "description": "…", "status": "IN_PROGRESS",
  "manager": { "id": "…", "username": "…", "displayName": "…", "active": true }, "assignee": null,
  "parent": { "number": 3, "project": { "key": "web", "name": "웹" }, "title": "…", "status": "NEW", "type": "feature" },
  "children": [ { "number": 15, "type": "task", "title": "…", "status": "DONE", "assignee": null } ],
  "links": [ { "id": "…", "type": "BLOCKS", "direction": "OUTGOING",
               "issue": { "number": 20, "project": { "key": "api", "name": "API" }, "title": "…", "status": "NEW", "type": "bug" } } ],
  "dueDate": null, "startDate": "2026-11-20", "plannedEndDate": "2026-11-28", "completedOn": null, "closedOn": null,
  "overdue": false, "createdBy": { "id": "…", "username": "…", "displayName": "…", "active": true },
  "createdAt": "…", "updatedAt": "…", "rowVersion": 4,
  "journal": [ … §9 … ], "journalCursor": null,
  "myActions": [ "edit", "comment", "changeManager" ] }
```

<a id="is-errors"></a>
### 15.1 오류

| code | HTTP | 뜻 |
|---|---|---|
| `PROJECT_NOT_FOUND` · `PERMISSION_REQUIRED` | 404 · 403 | 프로젝트 판정([프로젝트 설계 §3.2](04-project.md#pj-errors)) |
| `ISSUE_NOT_FOUND` | 404 | 그 워크스페이스에 그 번호의 일감이 없다 · 지웠다 · 그 일감의 프로젝트를 읽을 수 없다(구별하지 않는다) |
| `TARGET_PROJECT_NOT_FOUND` (2026-11-24) | 400 | 옮길 프로젝트가 없거나 읽을 수 없다(구별하지 않는다) |
| `ISSUE_TYPE_NOT_ALLOWED` (2026-11-24) | 400 | 옮길 프로젝트에서 쓸 수 없는 유형(뿌리 · 하위 일감) — 같은 요청에서 `type` 을 바꾼다 |
| `PROJECT_NOT_WRITABLE` | 409 | 보관한 프로젝트에 쓰기 |
| `INVALID_ISSUE_TITLE` | 400 | 제목이 비었거나 200자를 넘는다 |
| `ISSUE_DESCRIPTION_TOO_LONG` | 400 | 설명이 30,000자를 넘는다 |
| `UNKNOWN_ISSUE_TYPE` | 400 | 없는 유형 |
| `INVALID_ISSUE_STATUS` | 400 | 없는 상태 |
| `INVALID_ISSUE_DATES` | 400 | 날짜 순서([§5.1](#is-dates)) — 어긴 필드 이름을 담는다 |
| `ISSUE_MANAGER_REQUIRED` | 400 | 관리자를 비우려 했다 |
| `USER_NOT_PROJECT_READER` | 400 | 관리자 · 담당자가 그 프로젝트의 독자인 활성 멤버가 아니다 |
| `PARENT_NOT_FOUND` · `PARENT_CYCLE` · `PARENT_DEPTH_EXCEEDED` | 400 | 상위 규칙([§7](#is-tree)) |
| `ISSUE_HAS_CHILDREN` | 409 | 하위가 있어 지울 수 없다 |
| `LINK_TARGET_NOT_FOUND` · `INVALID_ISSUE_LINK` | 400 | 대상이 없거나 읽을 수 없다(다른 워크스페이스 포함) · 자기 자신 |
| `ISSUE_LINK_EXISTS` | 409 | 두 일감 사이에 이미 연결이 있다 |
| `ISSUE_LINK_NOT_FOUND` | 404 | 그 일감에 걸린 연결이 아니다 |
| `INVALID_COMMENT` · `COMMENT_TOO_LONG` | 400 | 댓글이 비었거나 30,000자를 넘는다 |
| `COMMENT_NOT_FOUND` | 404 | 그 일감에 그 id 의 댓글이 없다(댓글이 없는 이력 줄 · 다른 일감의 댓글도) |
| `COMMENT_DELETED` | 409 | 이미 지운 댓글을 고치거나 다시 지우려 했다 |
| `INVALID_FILTER` · `INVALID_SORT` | 400 | 목록 매개변수 |
| `INVALID_AGENT_SESSION` (2026-11-23) | 400 | 에이전트 세션이 100자를 넘거나 제어 문자가 있다 |
| `UNKNOWN_MODULE` · `MODULE_INACTIVE` (2026-11-23) | 400 | 이 프로젝트에 없는 모듈 key(목록 거르기도) · 쓰지 않는 모듈을 새로 붙이려 했다 |
| `ROW_VERSION_REQUIRED` · `UNKNOWN_FIELD` · `INVALID_FIELD` | 400 | 고치기 본문 |
| `CONCURRENT_UPDATE` | 409 | `rowVersion` 이 다르다(지금 `ApiExceptionHandler` 와 같은 code). 댓글 고치기면 그 댓글의 판이 다르고, 응답에 지금 `rowVersion` 을 싣는다 |

---

<a id="is-screens"></a>
## 16. 화면

프로젝트 틀(`ProjectFrame` — 프로젝트 이름 · 메뉴 한 줄 · 이 프로젝트의 내 권한, [프로젝트 설계 §8](04-project.md#pj-screens)) 안의 화면이다. moai-wiki 의 페이지 화면(`features/page/*`) 자리에 `features/issue/*` 를 둔다. 390px · 데스크톱 · ko/en · 다크 모드를 확인한다.

**2026-11-24 — 주소가 둘로([ADR-030](00-key-decisions.md#adr-030)):** 일감 하나는 정식 주소 `/w/:slug/issues/:number`(· `/edit` · `/history`)다. `IssueLayout` 이 일감을 읽어(읽을 수 없으면 404 — "일감을 찾을 수 없습니다" + 일감 목록으로) 그 일감의 지금 프로젝트로 프로젝트 틀을 그린다 — 옮기면 틀도 따라 바뀌고 사이드바는 그 프로젝트를 강조한다. 옛 `/w/:slug/projects/:projectKey/issues/:number`(· `/edit` · `/history`)는 `LegacyIssueRedirect` 가 서버에 옛 번호의 지금 번호를 물어(`issue-refs`) 정식 주소로 돌린다(`/edit` 그대로, `/history` → `?tab=revisions`, 주소 값 · 앵커 그대로). 프로젝트 주소에는 목록 · 새 일감만 남는다. 사이드바 워크스페이스 칸의 **"일감"**(`/w/:slug/issues`)이 워크스페이스 일감 목록이다([§13.1](#is-ws-list)) — 프로젝트 칸 · 프로젝트 거르기(여럿, 주소에 `project` 여러 번) · 목록의 거르기 전부 · `#123` 바로 가기.

| 주소 (`/w/:slug/projects/:projectKey/…` — 정식 주소는 2026-11-24 부터 `issues/:number…` 가 `/w/:slug/issues/:number…`) | 화면 | 내용 |
|---|---|---|
| `''` | 프로젝트 홈 = 일감 목록 (`ProjectHomePage` + `IssueFilters` · `IssueTable`) | 필터 줄(상태: 열림 · 닫힘 · 전체, 유형, 관리자, 담당자: 나 · 없음 포함, 모듈(여럿) · 에이전트 세션(2026-11-23), 상위, 찾기) · 제목 아래 모듈 · 세션 칩(`IssueTags`) · 표(번호 · 유형 · 제목 · 상태 · 관리자 · 담당자 · 완료 예정일 · 완료 기한 · 고친 때, 열 머리로 정렬, "기한 지남" 표시) · 쪽 나누기 · "새 일감" 단추(상세의 `myActions.createIssue`). 390px 에서는 표 대신 카드 목록(가로 스크롤 없음). 필터 · 정렬 · 쪽은 주소의 질의 문자열에 둔다(새로 고침 · 공유). 화면 기본은 열린 일감 · 고친 때 내림차순 |
| `issues/new?parent=12` | 일감 만들기 (`NewIssuePage` + `IssueForm`) | 유형 · 제목 · 설명([Markdown 편집기](#is-editor)) · 상태 · 관리자(기본 나, `issue.manager.assign` 이 없으면 잠김) · 담당자 · 상위 일감(번호 · 제목으로 찾기) · 예상 공수 · 관련 모듈(여럿) · 에이전트 세션(2026-11-23) · 날짜 다섯 개. 하위 일감 만들기로 오면 상위가 채워져 있다. 만들면 그 일감 화면으로 |
| `issues/:number` | 일감 보기 (`IssueViewPage`) | 머리(`#12` · 제목 · 유형 · 상태 — `issue.edit` 이면 상태를 바로 바꾼다) · 속성(관리자 · 담당자 — 떠난 멤버 표시 · 날짜 다섯 개 · 상위 · 만든 사람) · 설명(react-markdown + remark-gfm, 원시 HTML 을 그리지 않는다) · **하위 일감**(+ "하위 일감 만들기") · **연결 일감**(종류 · 방향별 + "연결" 대화상자 `LinkIssueDialog` — 같은 프로젝트에서 번호 · 제목으로 찾기, 풀기) · **이력 · 댓글**(`IssueTimeline` — 변경을 문장으로, MCP 표시, 전체 · 댓글 · 변경 거르기, 더 보기) · 댓글 칸(`CommentBox`). 메뉴: 고치기 · 지우기(`RemoveIssueDialog` — 번호 · 제목 확인) |
| `issues/:number/edit` | 일감 고치기 (`IssueEditPage` + `IssueForm`) | 만들기와 같은 양식 + 함께 다는 댓글. `rowVersion` 을 실어 보내고, 409 면 "다른 사람이 먼저 고쳤습니다" + 내 입력을 남긴 채 최신 값을 불러 견주기. **프로젝트 고르기(2026-11-24)** — 일감을 만들 수 있는 프로젝트(프로젝트 목록의 `myActions`) + 지금 프로젝트. 바꾸면 상위 · 모듈 고르기를 막고(서버가 정한다), 저장할 때 확인 창(하위 함께 · 상위 끊김 · 모듈 key 로 · 번호 그대로 · 두 프로젝트의 권한)을 거쳐 `project` 를 보낸다([§7-1](#is-move)) |
| `effort` (2026-11-23) | 프로젝트 공수 (`ProjectEffortPage` + `EffortReport`) | [공수 집계](#is-effort) — 이 프로젝트의 작업 시간 기록. 워크스페이스 화면 `/w/:slug/effort`(메뉴 "공수 집계")와 같은 부품 |

- 단추(새 일감 · 고치기 · 관리자 바꾸기 · 지우기 · 댓글)는 상세의 `myPermissions` · `myActions` 로 숨긴다. 판정은 api 가 한다.
- **관련 모듈 · 에이전트 세션(2026-11-23)** — 보기 속성의 셋째 줄(`IssueTagFields`, 좁은 화면에서도 접지 않음)에 모듈 칩 · 세션 칩, 없으면 "없음". 칩은 아이콘 + `aria-label`(색만으로 전하지 않음), 쓰지 않는 모듈은 점선 테두리 + "(쓰지 않음)".
- **댓글 고치기 · 지우기(2026-11-23)** — 댓글 탭의 이력 줄기에서, 댓글 머리 오른쪽 ⋮ 에 고치기 · 지우기를 둔다(`IssueHistory` 의 `CommentMenu`). 고치기는 쓴 사람이 나이고 `issue.comment` 가 있을 때, 지우기는 쓴 사람이 나이거나 `project.manage` 가 있을 때만 보인다(`comments.ts` 의 `commentActions` — 서버가 준 `myPermissions` 와 이력 줄의 `actorId` · 나로 가리기만 한다). 보관한 프로젝트 · 지운 댓글에는 없다. 고치기는 댓글 쓰기와 같은 Markdown 편집기를 그 자리에 열고(저장 · 취소, 이미지 · 파일 올리기도), 댓글의 `rowVersion` 을 함께 보낸다. 409 면 고치던 글을 남긴 채 최신 판을 다시 읽는다. 지우기는 확인 대화 상자(`ConfirmDialog`, 되돌릴 수 없음 · 첨부는 남음)를 거친다. 고친 댓글은 머리에 "고침 3분 전"(밀리초 시각은 도구 설명), 지운 댓글은 점선 상자에 "지운 댓글 · ○○ 님이 지움 · 언제". 이력 문장: "댓글을 고쳤습니다." · "○○ 님의 댓글을 지웠습니다." 댓글 탭의 수는 지운 댓글을 세지 않는다
- api 의 검증 오류(`code`)는 해당 입력 칸에 알린다. 날짜 규칙은 `issue-rules.ts` 로 미리 알리지만 판정은 api 다.
- 없거나 지운 일감(읽을 수 없는 일감도)은 "일감을 찾을 수 없습니다" 화면이다.
- **일감 보기의 프로젝트(2026-11-24)** — 속성 줄에 프로젝트(그 프로젝트의 일감 목록 링크). 위의 프로젝트 메뉴 한 줄(프로젝트 이름 · 일감 · 멤버 · 공수 …)도 그대로 둔다 — 일감은 그 프로젝트 화면 안에서 보이는 것이 자연스럽고(Redmine 도 일감 화면에 프로젝트 메뉴가 있다), 옮기면 메뉴가 새 프로젝트로 바뀌어 지금 프로젝트가 늘 보인다. 연결 일감이 다른 프로젝트면 그 프로젝트 이름을 붙인다. 이력의 "프로젝트: A → B" · "번호가 바뀌었습니다: … (워크스페이스 단위 번호)" · 경로 "시스템" 칩.
- **글 속 `#123`** 은 정식 주소 `/w/:slug/issues/123` 링크다(프로젝트가 달라도. 읽을 수 없으면 보통의 "찾을 수 없음").
- 사람 고르기 칸은 `GET …/people` 을 쓴다(입력하면 찾기). 키보드만으로 목록 · 보기 · 고치기를 할 수 있어야 한다.
- 다국어 리소스는 `issue` 이름공간(상태 · 유형 · 연결 종류의 이름, 이력 문장).
- 질의 키 · 무효화는 moai-wiki `features/page/queries.ts` 와 같은 꼴이고 워크스페이스 번호로 잡는다(2026-11-24 — `['issues', slug]` 아래 `list(scope, params)` · `detail(number)` …). 고치기 · 댓글 · 연결 · 옮기기 뒤에 그 워크스페이스의 일감 질의를 모두 다시 읽는다.

---

<a id="is-tests"></a>
## 17. 시험

| 묶음 | 꼭 넣을 경우 |
|---|---|
| 번호 (2026-11-24, `WorkspaceIssueTest`) | 한 워크스페이스의 여러 프로젝트가 한 순번(다른 워크스페이스는 따로 1부터) · 두 프로젝트에 동시에 8개 → 빠짐없이 한 번씩 · 지운 번호는 다시 나오지 않는다 · `project.issue_seq` 없음 |
| 정식 주소 · 목록 · 옮기기 · 연결 (2026-11-24, `WorkspaceIssueTest` · `McpIssueNumberTest`) | 번호만으로 보기 · 독자가 아니면 404 `ISSUE_NOT_FOUND`(없는 번호와 같다) · 옛 링크 풀기 · 워크스페이스 목록은 읽을 수 있는 프로젝트만 세고 project · 모듈 · 세션 · `#번호`로 거른다 · 다른 프로젝트 연결과 읽을 수 없는 쪽 숨김 · 옮기기의 두 권한 · 숨긴 대상 · 유형(하위 포함) · 하위 줄기 · 상위 끊김 · 모듈 key 잇기 · 이력 · 판 · 차이 · 첨부 받기 · 감사 `issue.move` · MCP 번호만 · `ISSUE_PROJECT_MISMATCH` · `ISSUE_AMBIGUOUS` · `search_issues` 워크스페이스 전체 · `moveToProject` |
| 번호 다시 매기기 (`WorkspaceIssueNumberMigrationTest` · `IssueRefRewriteTest`) | V21 까지 만든 시험 스키마에 두 프로젝트가 모두 #1 · #2 인 데이터 → V23: 만든 때 순서 · 같은 때는 옛 번호 순 · 다른 워크스페이스 따로 · 판 상위 번호 · 이력 상위 · 연결 번호 · 알림 · 접근 기록 · 첨부 티켓 · 설명 · 댓글의 `#n`(코드 · 링크 · 펜스 그대로, 지운 댓글 · 옛 판 그대로) · 기록 표 · 시스템 이력 · 새 판 · row_version 그대로 · 제약 · 보호 트리거 다시 켬. `#n` 바꾸기 규칙(화면 ISSUE_REF 와 같음) |
| 권한 | 멤버(코드 없음) 만들기 → 403. 멤버 + `project-editor` → 그 프로젝트에서만 된다. 큐레이터 지우기 → 403. `RESTRICTED` 프로젝트의 비독자 → 목록 · 보기 404 |
| 상태 · 날짜 | `DONE` 으로 → 완료일 = 오늘(Asia/Seoul). `CLOSED` 로 → 종료일 = 오늘, 완료일 그대로. `NEW` 로 → 둘 다 비움. 같은 요청에 완료일을 주고 `DONE` → 준 값. 시작일 > 완료 예정일 → 400. 자동 채운 날짜도 이력에 |
| 관리자 · 담당자 | 관리자 바꾸기에 `issue.manager.assign` 없음 → 403, 다른 필드도 그대로. 독자가 아닌 사람 → 400. 정한 뒤 그 사람이 떠나도 값은 남고 `active: false` |
| 상위 · 하위 | 다른 프로젝트의 일감을 상위로 → 400 `PARENT_NOT_FOUND`. 자기 하위 아래로 → 400 `PARENT_CYCLE`. 깊이 11 → 400. 동시에 A→B · B→A → 하나만 된다. 하위가 있는 일감 지우기 → 409 |
| 연결 | 같은 쌍에 두 번째(종류 · 방향이 달라도) → 409. 읽을 수 없는 대상 → 400(2026-11-24 부터 다른 프로젝트는 된다). 양쪽 보기에 각자 방향으로 보인다. 양쪽 이력 · 감사에 남는다. 일감을 지우면 연결이 사라지고 상대 이력에 남는다 |
| 동시 수정 | 같은 `rowVersion` 으로 동시에 두 번 → 하나만 성공. 바뀐 값이 없는 요청 → 이력 없음. 댓글 · 연결은 `rowVersion` 을 바꾸지 않는다 |
| 이력 · 감사 | 만들기 · 고치기 · 댓글 · 연결 · 상위 바꾸기 · 지우기마다 이력 · 감사가 맞다(`via` 포함). 설명 변경은 전 · 후 전문 |
| 댓글 고치기 · 지우기 (`CommentTest` · `IssueMcpToolsTest`, 2026-11-23) | 남이 고치면 403(`project.manage` 도) · 쓴 사람은 고치고 앞 글이 쌓인다 · 낡은 `rowVersion` 409 · 비면 400 · 지금 댓글 권한이 없으면 고치지 못하지만 지운다 · 남의 댓글은 `project.manage` 만 지운다 · 지운 댓글은 글 없이 남고 다시 고치거나 지우면 409 · 없는 댓글 404 · 보관 409 · 목록 쪽 나누기 · DB 트리거(이력 줄 지우기 · 지운 댓글 고치기 · 앞 글 없는 고치기 · 이력 줄 값 바꾸기 · 옛 글 바꾸기 막음) · 이력 줄 · 감사(WEB · MCP) · MCP 셋의 권한 · 워크스페이스 고르기(`PROJECT_AMBIGUOUS` · `WORKSPACE_NOT_FOUND`) |
| 보관 | 보관한 프로젝트에 만들기 · 고치기 · 댓글 · 연결 · 지우기 → 409 `PROJECT_NOT_WRITABLE` |
| 에이전트 세션 (`AgentSessionTest`, 2026-11-23) | 없어도 됨 · 앞뒤 공백 · 보기 · 목록 · 이력 · 판 · 차이 · 감사 · 101자 · 줄바꿈 400 · 권한 403 · 대소문자 무시 거르기 · `none` · 쓰인 이름 목록(지운 일감 빼고) · 세션만 바꾸면 알림 없음 · MCP 넣기 · 바꾸기 · `clear` · 찾기 |
| 관련 모듈 (`ProjectModuleTest`, 2026-11-23) | 여럿 붙이기(모듈 순서 · 겹침 하나 · 대소문자 무시) · 통째로 바꾸기 · 차례만 다르면 그대로 · 비우기 · 이력 · 판 · 차이 · 감사 · 꼴이 틀리면 400 · `UNKNOWN_MODULE` · `MODULE_INACTIVE`(이미 붙은 것은 남음) · 하나라도 거르기 · 쓰지 않는 모듈로 거르기 · 모듈마다 일감 수(지운 일감 빼고) · MCP |
| 목록 | 필터 조합 · 정렬(빈 날짜 뒤) · `q` 의 번호 맞추기 · `total` · 쪽 크기 상한 100 · 모르는 값 400 |
| 동등성 | 같은 일을 REST 와 MCP 로 하면 같은 결과 · 같은 오류 code([MCP 설계 §18](06-mcp.md#mc-tests)) |

---

**관련 문서:** [프로젝트](04-project.md) · [MCP](06-mcp.md) · [데이터 모델](02-data-model.md) · [워크스페이스 · 권한](03-workspace-identity.md) · [핵심 설계 결정](00-key-decisions.md) · [일감 요구사항](../requirements/03-issue.md)

[← 이전: 프로젝트](04-project.md) | [다음: MCP →](06-mcp.md)
