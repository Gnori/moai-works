[홈](../README.md) › [설계](00-key-decisions.md) › 일감

# 일감 — 유형 · 상태 · 날짜 · 관리자와 담당자 · 상위 · 하위 · 연결 · 이력

일감은 프로젝트 안에 여러 개 있는 일 하나다. moai-wiki 의 **페이지** 자리이고, 권한은 프로젝트의 것을 그대로 쓴다(일감마다 읽기를 좁히지 않는다 — [프로젝트 설계 §1](04-project.md#pj-principles)). 사람이 정한 일감 모델은 이것뿐이다 — 유형(예: 요구사항), 관리자 · 담당자, 날짜 다섯 개(완료 기한 · 시작일 · 완료 예정일 · 완료일 · 종료일), 상위 · 하위, 연결 일감([ADR-018](00-key-decisions.md#adr-018)). 여기에 상태 · 이력 · 댓글을 더한다. 표의 DDL 은 [데이터 모델 §5](02-data-model.md#dm-issue)에 있다.

> 요구사항: [FR-ISS-001](../requirements/03-issue.md#fr-iss-001) 만들기 · [002](../requirements/03-issue.md#fr-iss-002) 보기 · [003](../requirements/03-issue.md#fr-iss-003) 고치기(동시 수정) · [004](../requirements/03-issue.md#fr-iss-004) 유형 · [005](../requirements/03-issue.md#fr-iss-005) 상태 · [006](../requirements/03-issue.md#fr-iss-006) 관리자와 담당자 · [007](../requirements/03-issue.md#fr-iss-007) 날짜 · [008](../requirements/03-issue.md#fr-iss-008) 상위 · 하위 · [009](../requirements/03-issue.md#fr-iss-009) 연결 · [010](../requirements/03-issue.md#fr-iss-010) 이력 · [011](../requirements/03-issue.md#fr-iss-011) 댓글 · [012](../requirements/03-issue.md#fr-iss-012) 목록 · 필터 · 정렬 · [013](../requirements/03-issue.md#fr-iss-013) 지우기 · [014](../requirements/03-issue.md#fr-iss-014) 화면

**바로가기:** [원칙](#is-principles) · [일감](#is-model) · [유형](#is-type) · [번호](#is-number) · [상태](#is-status) · [날짜](#is-dates) · [관리자 · 담당자](#is-people) · [상위 · 하위](#is-tree) · [연결](#is-links) · [이력](#is-journal) · [댓글](#is-comments) · [지우기](#is-delete) · [동시 수정](#is-lock) · [목록](#is-list) · [행동별 규칙](#is-actions) · [REST](#is-api) · [오류](#is-errors) · [화면](#is-screens) · [시험](#is-tests)


> **구현 상태 (2026-11-22)** — 만든 것: 유형 · 순번 · 상태와 날짜 자동 채움 · 관리자 · 담당자 · 다섯 날짜 · 상위 · 하위 · 연결(이력에 방향) · 이력(만들기 첫 줄 · 설명 전 · 후 전체) · 댓글 · 지우기 · 목록(필터 별칭 · 정렬 · `childCount` · `overdue`) · 보기(`overdue`) · 쓰기 여섯 가지 감사 · 화면. **아직(계획):** 보기의 `myActions`(화면은 지금 프로젝트의 `myPermissions` 로 가린다) · 이력 100줄 넘을 때 이어 받기(`journal?cursor`) · 떠난 멤버 표시(`active`) · `issue.title` 의 "C" 정렬.

---

<a id="is-principles"></a>
## 1. 원칙

| 원칙 | 내용 |
|---|---|
| 권한은 프로젝트 | 모든 일감 API 는 `@ProjectApi(코드)` 다. 프로젝트를 볼 수 없으면 일감도 404, 일감 번호가 없거나 지웠으면 404 `ISSUE_NOT_FOUND` |
| 고정으로 시작 | 유형 · 상태 · 연결 종류는 모든 프로젝트에 같다. 프로젝트마다 바꾸기 · 워크플로 · 사용자 정의 필드는 나중 후보다 |
| 같은 프로젝트 안 | 상위 · 하위와 연결은 같은 프로젝트 안끼리만. 상위는 DB 복합 외래 키가, 연결은 서비스가 막는다 |
| 이력과 감사 | 일감을 바꿀 때마다 이력 한 줄과 감사 한 줄을 남긴다. 누가(사람 — MCP 로 한 일은 키 주인) 어디로(웹 · MCP) 무엇을 바꿨는지가 남는다 |
| 같은 서비스 | 웹(REST)과 MCP 도구는 같은 `IssueService` 를 부른다([ADR-004](00-key-decisions.md#adr-004)) |
| 보관한 프로젝트 | 읽기 전용이다. 일감 쓰기는 409 `PROJECT_NOT_WRITABLE`([프로젝트 설계 §2.2](04-project.md#pj-status)) |

---

<a id="is-model"></a>
## 2. 일감

| 필드 (API) | 열 | 뜻 | 규칙 |
|---|---|---|---|
| `number` | `number` | 프로젝트 안 번호 | [§4](#is-number). 바꾸지 않는다 |
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
| `rowVersion` | `row_version` | 동시 수정 검사 | [§12](#is-lock) |

요청은 사람을 id(`managerId` · `assigneeId`)로 받고, 응답은 사람 꼴(`manager` · `assignee` = `{id, username, displayName, active}`)로 준다. `active` 가 거짓이면 "떠난 멤버"다([§6](#is-people)).

moai-wiki `page` 와 견주면 — `wiki_id` → `project_id`, `slug` → `number`, `page_type` → `issue_type`, `owner_id` → `manager_id`(+ `assignee_id`), `parent_id` 는 같다. 페이지의 리비전 · 작업본 · 섹션 · 이름 공간 · 편집 제한 · 보관 상태는 없다.

<a id="is-type"></a>
## 3. 일감 유형

| key | 이름 | 설명 | 순서 |
|---|---|---|---|
| `requirement` | 요구사항 | 만들어야 할 것 — 무엇을 왜 | 1 |
| `feature` | 기능 | 새로 만들거나 늘리는 기능 | 2 |
| `bug` | 버그 | 고쳐야 할 잘못된 동작 | 3 |
| `task` | 작업 | 그 밖의 할 일 | 4 |

- 공통 시드 표 `issue_type` 이다(moai-wiki `page_type` 처럼 V2 가 넣는다). 모든 프로젝트가 같다. 프로젝트마다 바꾸기는 나중 후보다([정해야 할 것](../wbs/08-decisions.md)). 유형을 더하거나 고치는 API 는 없다. 화면은 key 로 다국어 이름을 보인다.
- 유형은 고칠 수 있다(`issue.edit`, 이력에 남는다). 유형에 따라 달라지는 규칙은 없다.
- 없는 key 는 400 `UNKNOWN_ISSUE_TYPE`. 목록은 `GET …/issue-types`(`issue.read`, 순서대로).

<a id="is-number"></a>
## 4. 번호

- 일감 번호는 **프로젝트 안 순번**이다(1, 2, 3 …, [ADR-019](00-key-decisions.md#adr-019)). 만드는 트랜잭션 안에서 프로젝트 행을 잠그고(`SELECT … FOR UPDATE`) `issue_seq` 를 1 올린다. 같은 프로젝트의 만들기는 줄을 선다. 만들기가 되돌려지면 번호도 되돌려진다(빈 번호가 생기지 않는다).
- 지운 일감의 번호는 다시 쓰지 않는다(`UNIQUE (project_id, number)` 가 지운 행에도 걸린다).
- 화면 주소 `/w/{ws}/projects/{project}/issues/{number}`, 글에서는 `#12`, 프로젝트 밖(MCP 결과 등)에서는 `{project}#12` 로 쓴다.

---

<a id="is-status"></a>
## 5. 상태

| 상태 | 이름 | 묶음 | 뜻 |
|---|---|---|---|
| `NEW` | 신규 | 열림 | 아직 손대지 않음 |
| `IN_PROGRESS` | 진행 | 열림 | 하는 중 |
| `DONE` | 완료 | 닫힘 | 일을 끝냄 |
| `CLOSED` | 종료 | 닫힘 | 더 다루지 않음(끝냈든 접었든) |

- 고정 4개이고 **아무 상태로나 옮길 수 있다**(워크플로 사용자 정의는 나중, [ADR-020](00-key-decisions.md#adr-020)). 상태 바꾸기는 `issue.edit` 이다. 그 밖의 값은 400 `INVALID_ISSUE_STATUS`.
- 목록 필터는 묶음(`open` = 신규 · 진행, `closed` = 완료 · 종료)도 받는다. 정렬 순서는 신규 < 진행 < 완료 < 종료.
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
| → `NEW` · `IN_PROGRESS` | 비운다 | 비운다 |

**검사** — 요청 값과 자동 채움을 모두 적용한 결과로 본다. 두 값이 모두 있을 때만 검사하고, 어기면 400 `INVALID_ISSUE_DATES`(어긴 필드 이름을 담는다).

| 검사 | |
|---|---|
| 시작일 ≤ 완료 예정일 | |
| 시작일 ≤ 완료 기한 | |
| 완료일 ≤ 종료일 | |

- 셋 다 DB `CHECK` 로도 막는다([데이터 모델 §5.4](02-data-model.md#dm-issue)). 상태와의 관계(자동 채움)는 서비스 규칙이다.
- 자동으로 채우거나 비운 날짜도 그 요청의 이력 줄에 전 · 후로 남는다.
- **기한 지남**: 열린 일감(`NEW` · `IN_PROGRESS`)의 완료 기한이 오늘보다 앞이면 목록 · 보기에 "기한 지남"(`overdue: true`)으로 보인다. 닫힌 일감에는 보이지 않는다.
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
| 같은 프로젝트 | 대상은 같은 프로젝트의 지우지 않은 일감(번호로 준다). 다른 프로젝트와의 연결은 나중 후보다([정해야 할 것](../wbs/08-decisions.md)) | 400 `LINK_TARGET_NOT_FOUND` |
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

일감을 바꿀 때마다 `issue_journal` 에 한 줄을 더한다(쌓기만 한다). 만들기 · 고치기 · 상위 바꾸기 · 연결 걸기 · 풀기 · 댓글마다 한 줄이고, 한 요청은 한 줄이다(연결은 두 일감에 한 줄씩). 이력은 업무와 같은 트랜잭션에 저장한다. moai-wiki 의 리비전 자리이지만 본문 판이 아니라 **필드 변경 목록**이다.

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

```json
{ "id": "…", "actorType": "USER", "actorId": "…", "actorName": "표시 이름", "via": "MCP",
  "createdAt": "2026-11-22T03:10:00Z", "notes": null,
  "changes": [ { "field": "status", "before": "IN_PROGRESS", "after": "DONE" },
               { "field": "completedOn", "before": null, "after": "2026-11-22" } ] }
```

- 화면은 "상태를 진행 → 완료로 바꿈", "완료일을 2026-11-22 로 정함" 처럼 문장으로 보이고, `via = MCP` 면 "MCP" 칩을 붙인다(누르면 키 이름 · 엔드포인트). 이력을 전체 · 댓글만 · 변경만으로 거른다.
- 보기 응답에는 이력을 **오래된 것부터 100줄** 싣고, 더 있으면 이어 받기 커서(`journalCursor`)를 준다. 나머지는 `GET …/issues/{number}/journal?cursor=` 로 받는다.
- 이력 줄을 고치거나 지우는 API 는 없다. 댓글 고치기 · 지우기도 첫 출시에 없다.
- **감사**: 일감의 쓰기는 감사에도 남긴다 — `issue.create`(프로젝트 · 번호 · 유형) · `issue.update`(바뀐 필드 이름) · `issue.comment` · `issue.link.add` · `issue.link.remove` · `issue.delete`(번호 · 제목). `details.via` 에 경로를, MCP 면 엔드포인트 id · 키 id · 도구 이름을 함께 넣는다([MCP 설계 §15](06-mcp.md#mc-audit)). 본문 · 댓글 원문은 감사에 넣지 않는다.

<a id="is-comments"></a>
## 10. 댓글

- `POST …/issues/{number}/comments` `{notes}` — `issue.comment`(기본 역할은 모두 가진다). 본문은 Markdown, 앞뒤 공백을 뺀 1~30,000자. 비면 400 `INVALID_COMMENT`, 넘으면 400 `COMMENT_TOO_LONG`.
- `notes` 만 있는 이력 한 줄이 된다. 일감 행은 바꾸지 않는다(`rowVersion` · `updated_at` 그대로 — 댓글이 다른 사람의 고치기와 부딪히지 않게). 감사 `issue.comment`.
- 닫힌 일감에도 달 수 있다. 보관한 프로젝트에는 달 수 없다(409 `PROJECT_NOT_WRITABLE`).
- 고치면서 함께 다는 댓글: `PATCH` 본문에 `notes` 를 넣으면 같은 이력 줄의 `notes` 가 된다(그때는 서비스가 `issue.comment` 도 본다).
- 멘션 · 알림 · 댓글 고치기 · 지우기는 나중 후보다.

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

<a id="is-list"></a>
## 13. 목록 · 필터 · 정렬 · 쪽

`GET …/projects/{project}/issues` — `issue.read`. 경로의 프로젝트에서 지우지 않은 일감만. 서로 다른 필터는 모두 만족(AND), 한 필터의 여러 값은 하나라도(OR)다.

| 매개변수 | 값 | 뜻 |
|---|---|---|
| `status` | 여러 번(`NEW` · `IN_PROGRESS` · `DONE` · `CLOSED`), 또는 `open` · `closed` | 비면 전부 |
| `type` | 여러 번(유형 key) | |
| `manager` | 사람 id · `me` | |
| `assignee` | 사람 id · `me` · `none`(담당자 없음) | |
| `parent` | 번호(그 일감의 바로 아래) · `none`(최상위만) | |
| `q` | 글 | 제목에 들어 있는가(대소문자 무시). 숫자만(또는 `#12`)이면 그 번호도 찾는다 |
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
- 저장한 질의 · 여러 프로젝트 한꺼번에 보기 · 내보내기 · 칸반 보드는 나중 후보다.

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
| 연결 걸기 · 풀기 | `issue.edit` | 같은 프로젝트 · 한 쌍에 하나 | 400 · 404 · 409 |
| 댓글 | `issue.comment` | 본문 1~30,000자 | 403 · 400 |
| 지우기 | `issue.delete` | 하위 없음 | 403 · 409 |

- 읽을 수 있는 사람에게는 403 을, 읽을 수 없는 사람에게는 404 를 준다.
- 보기 응답의 `myActions` 로 화면이 단추를 숨긴다: `edit` · `comment` · `changeManager` · `delete`(권한 코드 + 프로젝트 상태 + 하위 유무로 계산 — moai-wiki 페이지 응답의 `myActions` 와 같은 방식).

---

<a id="is-api"></a>
## 15. REST

경로는 `/moai-works-api/v1/workspaces/{workspace}/projects/{project}` 아래이고 **모두 `@ProjectApi`** 다.

| 일 | 메서드 · 경로 | 접근 어노테이션 | 응답 |
|---|---|---|---|
| 유형 목록 | `GET /issue-types` | `@ProjectApi(issue.read)` | `[{key, name, description}]` (순서대로) |
| 목록 | `GET /issues?status&type&manager&assignee&parent&q&sort&page&size` | `@ProjectApi(issue.read)` | [§13](#is-list) |
| 만들기 | `POST /issues` | `@ProjectApi(issue.create)` | 201 + 보기 |
| 보기 | `GET /issues/{number}` | `@ProjectApi(issue.read)` | 보기(상위 · 하위 · 연결 · 이력 100줄 · `myActions`) |
| 이력 이어 받기 | `GET /issues/{number}/journal?cursor=` | `@ProjectApi(issue.read)` | `{journal: [...], journalCursor}` |
| 고치기 | `PATCH /issues/{number}` | `@ProjectApi(issue.edit)` | 보기. 관리자 바꾸기는 서비스가 `issue.manager.assign`, `notes` 는 `issue.comment` 도 본다 |
| 지우기 | `DELETE /issues/{number}` | `@ProjectApi(issue.delete)` | 204 |
| 댓글 | `POST /issues/{number}/comments` | `@ProjectApi(issue.comment)` | 보기 |
| 연결 걸기 | `POST /issues/{number}/links` | `@ProjectApi(issue.edit)` | 보기 |
| 연결 풀기 | `DELETE /issues/{number}/links/{linkId}` | `@ProjectApi(issue.edit)` | 보기 |

관리자 · 담당자 고르기 목록은 `GET …/projects/{project}/people`([프로젝트 설계 §6](04-project.md#pj-api)).

**요청 꼴**

```java
record CreateRequest(@NotBlank String type, @NotBlank @Size(max = 200) String title,
    @Size(max = 30000) String description, Issue.Status status,   // 없으면 NEW
    UUID managerId,                                               // 없으면 나 (MCP 면 키 주인)
    UUID assigneeId, Integer parent,
    LocalDate dueDate, LocalDate startDate, LocalDate plannedEndDate, LocalDate completedOn, LocalDate closedOn) {}

/*
 * 고치기 — JSON 객체. 넣은 필드만 바꾸고(null 이면 비운다), 넣지 않은 필드는 그대로다.
 * 받는 키: rowVersion(필수) · type · title · description · status · managerId · assigneeId · parent
 *         · dueDate · startDate · plannedEndDate · completedOn · closedOn · notes(함께 다는 댓글)
 * 모르는 키는 400 UNKNOWN_FIELD, 값의 꼴이 틀리면 400 INVALID_FIELD.
 */

record CommentRequest(@NotBlank @Size(max = 30000) String notes) {}

record LinkRequest(@NotNull Integer target, @NotNull IssueLink.Type type) {}   // 경로의 일감이 원본
```

**보기 응답 꼴**

```json
{ "number": 12, "type": "requirement", "title": "…", "description": "…", "status": "IN_PROGRESS",
  "manager": { "id": "…", "username": "…", "displayName": "…", "active": true }, "assignee": null,
  "parent": { "number": 3, "title": "…", "status": "NEW", "type": "feature" },
  "children": [ { "number": 15, "type": "task", "title": "…", "status": "DONE", "assignee": null } ],
  "links": [ { "id": "…", "type": "BLOCKS", "direction": "OUTGOING",
               "issue": { "number": 20, "title": "…", "status": "NEW", "type": "bug" } } ],
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
| `ISSUE_NOT_FOUND` | 404 | 그 프로젝트에 그 번호의 일감이 없다 · 지웠다 |
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
| `LINK_TARGET_NOT_FOUND` · `INVALID_ISSUE_LINK` | 400 | 대상이 없거나 다른 프로젝트 · 자기 자신 |
| `ISSUE_LINK_EXISTS` | 409 | 두 일감 사이에 이미 연결이 있다 |
| `ISSUE_LINK_NOT_FOUND` | 404 | 그 일감에 걸린 연결이 아니다 |
| `INVALID_COMMENT` · `COMMENT_TOO_LONG` | 400 | 댓글이 비었거나 30,000자를 넘는다 |
| `INVALID_FILTER` · `INVALID_SORT` | 400 | 목록 매개변수 |
| `ROW_VERSION_REQUIRED` · `UNKNOWN_FIELD` · `INVALID_FIELD` | 400 | 고치기 본문 |
| `CONCURRENT_UPDATE` | 409 | `rowVersion` 이 다르다(지금 `ApiExceptionHandler` 와 같은 code) |

---

<a id="is-screens"></a>
## 16. 화면

`ProjectLayout`([프로젝트 설계 §8](04-project.md#pj-screens)) 안의 화면이다. moai-wiki 의 페이지 화면(`features/page/*`) 자리에 `features/issue/*` 를 둔다. 390px · 데스크톱 · ko/en · 다크 모드를 확인한다.

| 주소 (`/w/:slug/projects/:projectKey/…`) | 화면 | 내용 |
|---|---|---|
| `''` | 프로젝트 홈 = 일감 목록 (`ProjectHomePage` + `IssueFilters` · `IssueTable`) | 필터 줄(상태: 열림 · 닫힘 · 전체, 유형, 관리자, 담당자: 나 · 없음 포함, 상위, 찾기) · 표(번호 · 유형 · 제목 · 상태 · 관리자 · 담당자 · 완료 예정일 · 완료 기한 · 고친 때, 열 머리로 정렬, "기한 지남" 표시) · 쪽 나누기 · "새 일감" 단추(상세의 `myActions.createIssue`). 390px 에서는 표 대신 카드 목록(가로 스크롤 없음). 필터 · 정렬 · 쪽은 주소의 질의 문자열에 둔다(새로 고침 · 공유). 화면 기본은 열린 일감 · 고친 때 내림차순 |
| `issues/new?parent=12` | 일감 만들기 (`NewIssuePage` + `IssueForm`) | 유형 · 제목 · 설명(CodeMirror + 미리보기) · 상태 · 관리자(기본 나, `issue.manager.assign` 이 없으면 잠김) · 담당자 · 상위 일감(번호 · 제목으로 찾기) · 날짜 다섯 개. 하위 일감 만들기로 오면 상위가 채워져 있다. 만들면 그 일감 화면으로 |
| `issues/:number` | 일감 보기 (`IssueViewPage`) | 머리(`#12` · 제목 · 유형 · 상태 — `issue.edit` 이면 상태를 바로 바꾼다) · 속성(관리자 · 담당자 — 떠난 멤버 표시 · 날짜 다섯 개 · 상위 · 만든 사람) · 설명(react-markdown + remark-gfm, 원시 HTML 을 그리지 않는다) · **하위 일감**(+ "하위 일감 만들기") · **연결 일감**(종류 · 방향별 + "연결" 대화상자 `LinkIssueDialog` — 같은 프로젝트에서 번호 · 제목으로 찾기, 풀기) · **이력 · 댓글**(`IssueTimeline` — 변경을 문장으로, MCP 표시, 전체 · 댓글 · 변경 거르기, 더 보기) · 댓글 칸(`CommentBox`). 메뉴: 고치기 · 지우기(`RemoveIssueDialog` — 번호 · 제목 확인) |
| `issues/:number/edit` | 일감 고치기 (`IssueEditPage` + `IssueForm`) | 만들기와 같은 양식 + 함께 다는 댓글. `rowVersion` 을 실어 보내고, 409 면 "다른 사람이 먼저 고쳤습니다" + 내 입력을 남긴 채 최신 값을 불러 견주기 |

- 단추(새 일감 · 고치기 · 관리자 바꾸기 · 지우기 · 댓글)는 상세의 `myPermissions` · `myActions` 로 숨긴다. 판정은 api 가 한다.
- api 의 검증 오류(`code`)는 해당 입력 칸에 알린다. 날짜 규칙은 `issue-rules.ts` 로 미리 알리지만 판정은 api 다.
- 없거나 지운 일감은 "일감을 찾을 수 없습니다" 화면이다.
- 사람 고르기 칸은 `GET …/people` 을 쓴다(입력하면 찾기). 키보드만으로 목록 · 보기 · 고치기를 할 수 있어야 한다.
- 다국어 리소스는 `issue` 이름공간(상태 · 유형 · 연결 종류의 이름, 이력 문장).
- 질의 키 · 무효화는 moai-wiki `features/page/queries.ts` 와 같은 꼴(`issueKeys.list(slug, projectKey, filters)` · `issueKeys.detail(slug, projectKey, number)`). 고치기 · 댓글 · 연결 뒤에 보기와 목록을 고친다.

---

<a id="is-tests"></a>
## 17. 시험

| 묶음 | 꼭 넣을 경우 |
|---|---|
| 번호 | 같은 프로젝트에 동시에 20개 만들기 → 1~20 이 빠짐없이 한 번씩. 다른 프로젝트는 따로 1부터. 지운 번호는 다시 나오지 않는다 |
| 권한 | 멤버(코드 없음) 만들기 → 403. 멤버 + `project-editor` → 그 프로젝트에서만 된다. 큐레이터 지우기 → 403. `RESTRICTED` 프로젝트의 비독자 → 목록 · 보기 404 |
| 상태 · 날짜 | `DONE` 으로 → 완료일 = 오늘(Asia/Seoul). `CLOSED` 로 → 종료일 = 오늘, 완료일 그대로. `NEW` 로 → 둘 다 비움. 같은 요청에 완료일을 주고 `DONE` → 준 값. 시작일 > 완료 예정일 → 400. 자동 채운 날짜도 이력에 |
| 관리자 · 담당자 | 관리자 바꾸기에 `issue.manager.assign` 없음 → 403, 다른 필드도 그대로. 독자가 아닌 사람 → 400. 정한 뒤 그 사람이 떠나도 값은 남고 `active: false` |
| 상위 · 하위 | 다른 프로젝트의 일감을 상위로 → 400. 자기 하위 아래로 → 400 `PARENT_CYCLE`. 깊이 11 → 400. 동시에 A→B · B→A → 하나만 된다. 하위가 있는 일감 지우기 → 409 |
| 연결 | 같은 쌍에 두 번째(종류 · 방향이 달라도) → 409. 다른 프로젝트 → 400. 양쪽 보기에 각자 방향으로 보인다. 양쪽 이력 · 감사에 남는다. 일감을 지우면 연결이 사라지고 상대 이력에 남는다 |
| 동시 수정 | 같은 `rowVersion` 으로 동시에 두 번 → 하나만 성공. 바뀐 값이 없는 요청 → 이력 없음. 댓글 · 연결은 `rowVersion` 을 바꾸지 않는다 |
| 이력 · 감사 | 만들기 · 고치기 · 댓글 · 연결 · 상위 바꾸기 · 지우기마다 이력 · 감사가 맞다(`via` 포함). 설명 변경은 전 · 후 전문 |
| 보관 | 보관한 프로젝트에 만들기 · 고치기 · 댓글 · 연결 · 지우기 → 409 `PROJECT_NOT_WRITABLE` |
| 목록 | 필터 조합 · 정렬(빈 날짜 뒤) · `q` 의 번호 맞추기 · `total` · 쪽 크기 상한 100 · 모르는 값 400 |
| 동등성 | 같은 일을 REST 와 MCP 로 하면 같은 결과 · 같은 오류 code([MCP 설계 §18](06-mcp.md#mc-tests)) |

---

**관련 문서:** [프로젝트](04-project.md) · [MCP](06-mcp.md) · [데이터 모델](02-data-model.md) · [워크스페이스 · 권한](03-workspace-identity.md) · [핵심 설계 결정](00-key-decisions.md) · [일감 요구사항](../requirements/03-issue.md)

[← 이전: 프로젝트](04-project.md) | [다음: MCP →](06-mcp.md)
