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

- 일감 번호는 **프로젝트 안 순번**이다(1, 2, 3 …, [ADR-019](00-key-decisions.md#adr-019)). 만드는 트랜잭션 안에서 프로젝트 행을 잠그고(`SELECT … FOR UPDATE`) `issue_seq` 를 1 올린다. 같은 프로젝트의 만들기는 줄을 선다. 만들기가 되돌려지면 번호도 되돌려진다(빈 번호가 생기지 않는다).
- 지운 일감의 번호는 다시 쓰지 않는다(`UNIQUE (project_id, number)` 가 지운 행에도 걸린다).
- 화면 주소 `/w/{ws}/projects/{project}/issues/{number}`, 글에서는 `#12`, 프로젝트 밖(MCP 결과 등)에서는 `{project}#12` 로 쓴다.

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
- 멘션 · 댓글 고치기 · 지우기는 나중 후보다([FR-ISS-019](../requirements/03-issue.md#fr-iss-019)). 알림은 [알림 설계](07-notification.md).

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
| 본문에 넣기 | 올린 응답의 `markdown` — 이미지 `![이름](attachment:<id>)`, 파일 `[이름](attachment:<id>)`. 화면의 Markdown 은 `attachment:` 를 그 일감의 받기 주소로 바꾼다(그 밖의 스킴 규칙은 그대로) |
| 지우기 | `DELETE …/attachments/{id}` — 올린 사람 또는 `project.manage`. 지움 표시만(누가). DB 트리거가 지우기 · 값 바꾸기를 막는다. 객체는 남는다(나중에 정리 작업이 일정 기간 뒤 지운다) |
| 이력 · 감사 | 더하고 뺀 것이 이력 한 줄(`field: attachment`, 판은 아님). 감사 `issue.attachment.add` · `issue.attachment.remove`(경로 포함) |
| 화면 | 일감 보기를 내용 · 댓글 · 첨부파일 탭으로(`?tab=`). 첨부 탭에서 올리기(끌어 놓기) · 받기 · 표시 복사 · 지우기. 편집기에 이미지를 붙여 넣거나 끌어 놓으면 올리고 그 자리에 표시를 넣는다(일감을 만든 뒤부터) |
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
| 빠진 항목 | 설명에 같은 제목(수준 · 대소문자 · 공백 무시, 코드 블록 밖)이 없는 필수 항목. **알려만 준다** — MCP 결과의 `templateCheck` · 화면 경고. 저장은 막지 않는다 |

처음 템플릿:

| 유형 | 항목 |
|---|---|
| 새기능 `feature` | 배경 · 하려는 것 · 수용 기준 · 범위 밖(선택) |
| 버그 `bug` | 현상 · 재현 순서 · 기대한 동작 · 실제 동작 · 환경(선택) · 수용 기준 |
| 테스트 `test` | 목적 · 범위 · 시험 항목 · 결과(선택) |
| 기술조사 `research` | 질문 · 배경 · 조사할 것 · 결론(선택) |

<a id="is-editor"></a>
## 10-3. Markdown 편집기 (2026-11-23)

> 사용자(2026-11-23): "이렇게 md 편집기를 만들고 이슈의 본문이나 덧글은 모두 md 로 작성할 수 있게 해 줘"(VS Code Markdown Editor 화면) — [FR-ISS-021](../requirements/03-issue.md#fr-iss-021). 저장 형식은 그대로 Markdown 이다.

| 항목 | 규칙 |
|---|---|
| 편집기 | Milkdown Crepe(`@milkdown/crepe` · `@milkdown/kit` 7.23.0). 쓰면서 바로 그려진다. 화면 코드는 편집할 때 따로 받는다(lazy, `RichEditor`). Vditor 는 실행 중에 Lute 등을 CDN 에서 받아 화면 보안 정책(`script-src 'self'`)과 맞지 않아 뺐다. Milkdown 은 보기(react-markdown)와 같은 remark 계열이다 |
| 켜는 · 끄는 기능 | 켬: 할 일 목록 체크 칸 · 표 · 코드 블록(CodeMirror) · 링크 고치기 · 빈 칸 안내 · 커서. 끔: 블록 그림(저장할 때 이름 자리에 크기 비율을 적어 `![이름](attachment:…)` 를 망가뜨린다) · 수식 · AI(바깥 서비스) · 위 막대(TopBar) · 블록 메뉴 · 떠 있는 막대(영어 글 · 위 고정 막대와 겹침). `crepe-setup.ts` 한 곳에서 정한다 |
| 도구 막대 | 위에 고정(`EditorToolbar`). 목차 · 제목/문단 · 굵게 · 기울임 · 취소선 · 링크 | 글머리 · 번호 · 할 일 · 표 | 인용 · 코드 블록 · 줄 안 코드 · 가로줄 | 되돌리기 · 다시 | 이미지 · 파일 넣기 · Markdown 원문. 도구 설명에 단축키 |
| 목차 | 설명 편집기에서 편집 칸이 900px 이상이면 처음부터 연다(편집 칸의 받은 너비). 제목을 누르면 그 자리로. 댓글에는 없다 |
| 원문 보기 | "Markdown 원문"을 누르면 CodeMirror 로 원문을 고친다(`MarkdownSourceEditor`). 돌아오면 그 글로 편집기를 다시 만든다 |
| 원문 보존 | 편집기는 저장할 때 글 전체를 다시 적는다(목록 기호 · 표 칸 맞춤 · 번호 기호 …). 손대지 않은 덩어리는 원문 그대로 둔다 — `source-blocks.ts`(moai-wiki 와 같은 사본). 편집기를 만든 직후의 글을 baseline 으로 잡고, 바뀔 때마다 `preserveSource(원문, baseline, 지금)` 을 바깥 값으로 올린다: 원문 ↔ baseline 은 맨 위 덩어리를 자리로, baseline ↔ 지금은 글자로 견준다. 같은 덩어리는 원문 조각, 다른 덩어리만 편집기 글. 덩어리 수가 다르면(참조 링크의 정의 줄을 편집기가 버리는 경우) 고친 뒤에는 편집기 글을 그대로 쓴다. 한계: 덩어리 단위라 긴 목록 · 표의 한 칸을 고치면 그 덩어리 전체가 편집기 모양이 된다 |
| 원문 보존 시험 | 실제 일감 설명 · 댓글 39건(moai-works · moai-wiki, MCP 로 모음)과 어려운 경우 16건을 실제 편집기에 열자마자 꺼낸 글과 짝지어 둔다(`__fixtures__/roundtrip.json`, 개발 서버의 `dev/editor-roundtrip.html` 로 만든다). 55건 가운데 52건은 편집기가 글자를 바꾸지만, 보존 함수를 거치면 55건 모두 원문 그대로다. 마지막 덩어리를 고치면 그 덩어리만 바뀐다 |
| 첨부 그림 | 줄 안 그림 노드의 보기만 바꾼다(`imageView`): `attachment:<id>` 는 그 일감의 첨부 내용 주소로 보이고, 바깥 주소는 받지 않고 이름만. 노드의 이름 · 주소 · 제목은 그대로라 저장되는 Markdown 이 바뀌지 않는다 |
| 올리기 | 붙여 넣기 · 끌어 놓기 · 단추 → 그 일감에 올리고 그 자리에 "업로드 중… 이름" 표시를 두었다가 받은 표시로 바꾼다(실패하면 지운다). 자리는 글이 바뀌어도 따라간다. Markdown 을 넣을 때 HTML DOM 을 거치지 않고 해석기로 바로 노드를 만든다 — Milkdown 의 insert(inline) · insertPos 는 DOM 을 거쳐 `<img src>` 가 문서 밖에서 받기를 시작하고 제목을 덧붙인다 |
| 보안 | HTML 은 편집 중에도 글자로 보인다(Milkdown html 노드 = textContent). 편집 중에는 링크를 열지 않는다. 링크 넣기는 http · https · mailto · 이 화면 안의 경로 · 첨부 표시만(`link-rules.ts`). [SECURITY.md §9](../../SECURITY.md#9-화면web-frontend의-보안-규칙) |
| 모양 | 글 모양은 보기와 같다(`markdownBodySx` 를 `.milkdown .ProseMirror` 아래로). Crepe 의 색 변수는 화면 테마의 CSS 변수로 채워 다크 · 라이트를 따른다 |
| 시험 | jsdom 에서는 Milkdown 이 돌지 않아 시험용 편집기(`editor/__mocks__/RichEditor.tsx`, test-setup 의 vi.mock)로 바꾼다. 순수 함수(원문 보존 · 링크 규칙)는 그대로 시험한다 |

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
| 판 | 만들면 1판, 필드(유형 · 제목 · 설명 · 상태 · 관리자 · 담당자 · 상위 · 다섯 날짜 · 예상 공수)가 바뀔 때마다 새 판. `issue.revision_no` 가 지금 판 |
| 담는 것 | `issue_revision` — 그 시점의 일감 전체 + 누가(`actor_id` · `actor_name` · `actor_type`) · 어느 길로(`via` WEB · MCP) · 언제(`created_at`, 밀리초) · 그 판을 만든 이력 줄(`journal_id`) |
| 판이 아닌 것 | 댓글 · 연결 · 작업 시간 기록 — 이력(`issue_journal`)에만 남는다. 필드를 바꾼 이력 줄에는 `revision_no` 가 붙는다 |
| 덮어쓰지 않음 | DB 트리거가 `issue_revision` 의 UPDATE · DELETE 를 막는다 |
| 판 목록 | `GET …/issues/{n}/revisions?before=&limit=` — 새 판부터(최대 50), `{items:[{no, actorType, actorId, actorName, via, createdAt, changedFields, current}], next}` |
| 한 판 | `GET …/issues/{n}/revisions/{no}` — 그 판의 일감 전체 |
| 차이 | `GET …/issues/{n}/revisions/{no}/diff?from=` — `from` 이 비면 바로 앞 판, 1판은 "없음"과. 필드는 `{field, before, after}`(사람은 `{id, name}`), 설명은 `LineDiff`(앞뒤 같은 줄을 걷어 내고 가운데만 LCS, 너무 크면 통째로 지우고 넣음 = `coarse`) → `descriptionLines[{op SAME · ADD · DEL, text, oldLine, newLine}]` · `added` · `removed`. `SAME_REVISION` 400 · `REVISION_NOT_FOUND` 404 |
| 화면 | `issues/:number/history` — 판 타임라인(누가 · 언제(밀리초) · MCP 표시 · 바뀐 필드), 고른 판의 앞 판과의 차이, 아무 두 판 견주기, 한 판 전체 보기. 차이는 git 처럼 줄 번호 · `−`/`+` · 빨강/초록(색만으로 전하지 않음). 고른 판은 주소(`?rev=&from=`)에 남는다 |
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
| 집계(나중) | 공수 정리 · 실제 투입 공수는 작업 시간 기록에서 뽑는다 — 사람 · 일감 · 프로젝트 · 기간별 예상 대비 실제([FR-ISS-017](../requirements/03-issue.md#fr-iss-017)). 그래서 기록은 지우지 않고 바꾸지 않는다 |

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
| 템플릿 (2026-11-23, [§10-2](#is-templates)) | `GET /issue-templates` · `GET /issue-templates/{type}` | `@ProjectApi(issue.read)` | 쓰이는 템플릿 `[{type, typeName, body, source, inherited, sections, updatedBy, updatedAt, rowVersion}]` |
| 템플릿 덮어쓰기 · 되돌리기 | `PUT /issue-templates/{type}` `{body, rowVersion}` · `DELETE /issue-templates/{type}` | `@ProjectApi(project.manage)` | 쓰이는 템플릿. 워크스페이스 기본은 `/workspaces/{ws}/issue-templates[/{type}]` — `@WorkspaceApi(workspace.read)` · `(workspace.settings.manage)` |
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
| `issues/new?parent=12` | 일감 만들기 (`NewIssuePage` + `IssueForm`) | 유형 · 제목 · 설명([Markdown 편집기](#is-editor)) · 상태 · 관리자(기본 나, `issue.manager.assign` 이 없으면 잠김) · 담당자 · 상위 일감(번호 · 제목으로 찾기) · 날짜 다섯 개. 하위 일감 만들기로 오면 상위가 채워져 있다. 만들면 그 일감 화면으로 |
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
