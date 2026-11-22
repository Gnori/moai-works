[홈](../README.md) › [요구사항](00-overview.md) › 프로젝트 요구사항

# 프로젝트 요구사항 (FR-PRJ)

프로젝트 만들기 · 목록 · 설정 · 보관 · 삭제, 공개 범위와 독자 판정, 프로젝트 역할과 그 정본(Keycloak 그룹), 개인 워크스페이스의 기본 프로젝트, 프로젝트 화면 · 감사의 요구사항이다. **프로젝트는 moai-wiki 의 위키 자리다** — 워크스페이스 안에 여러 개 있고, 일감의 묶음이자 **권한 경계**다(2026-11-22 결정: "위키냐 프로젝트냐 차이만"). 만들기 · 판정 · 화면 · 역할의 모양은 moai-wiki 의 위키와 같게 한다. 설계는 [프로젝트 설계](../design/04-project.md)에 있다.

| 우선순위 | 개수 | 요구사항 |
|---|---|---|
| M | 11 | [001](#fr-prj-001) ~ [011](#fr-prj-011) |
| 합계 | 11 | |

| 공통 | 내용 |
|---|---|
| moai-wiki 대응 | 위키 → 프로젝트. `@WikiApi` → `@ProjectApi(권한)` · `WikiContext` → `ProjectContext` · `WikiAccessService` → `ProjectAccessService` · 위키 역할 → 프로젝트 역할 · "내 위키" → "내 프로젝트" · `WIKI_NOT_FOUND` → `PROJECT_NOT_FOUND` |
| 정본 | 프로젝트 자체는 api DB(`project`). 프로젝트 역할은 Keycloak 조직 그룹 `/projects/<project id>/<role>` 이 정본이고 api 는 사본(`project_role_binding`)으로 판정한다 |
| 권한 경계 | 프로젝트. 독자가 아니거나 없거나 볼 수 없는 상태면 **404** `PROJECT_NOT_FOUND`, 독자인데 권한 코드가 없으면 **403** `PERMISSION_REQUIRED`, 보관한 프로젝트에 쓰면 **409** `PROJECT_NOT_WRITABLE` ([FR-PRJ-006](#fr-prj-006)) |
| 권한 코드 | `project.create` · `project.manage` · `project.delete` 와 일감 코드 6개. 카탈로그 25개와 기본 역할 넷은 [FR-WSP-015](01-workspace.md#fr-wsp-015) |
| REST | `/moai-works-api/v1/workspaces/{workspace}/projects` 아래. `{project}` 는 프로젝트 key 다 |
| 감사 | 모든 쓰기는 감사(`audit_log`)에 남는다 ([FR-PRJ-011](#fr-prj-011)) |
| MCP | 프로젝트는 읽기 도구(`list_projects` · `get_project`)로만 연다. 만들기 · 설정 · 보관 · 삭제 · 역할은 MCP 에 없다(사람만 웹에서) — [MCP 요구사항](04-mcp.md) |
| 화면 | ko/en, 390px 와 데스크톱, 다크 모드 |

**바로가기:** [만들기 · 목록 · 설정](#1-만들기--목록--설정) · [보관 · 삭제](#2-보관--삭제) · [독자 판정과 프로젝트 역할](#3-독자-판정과-프로젝트-역할) · [기본 프로젝트 · 화면 · 감사](#4-기본-프로젝트--화면--감사)

---

## 1. 만들기 · 목록 · 설정

<a id="fr-prj-001"></a>
### FR-PRJ-001 프로젝트 만들기

| 항목 | 내용 |
|---|---|
| 우선순위 | M |
| 진행 | 일부 — 한 것: api(`POST …/projects`, key 규칙 · 예약어 · 중복, 감사 `project.create`) · 자동 테스트. 남은 것: 같은 key 동시 만들기 시험 · 화면. |
| 관련 설계 | [프로젝트 설계](../design/04-project.md) · [데이터 모델](../design/02-data-model.md) |

**설명** — 워크스페이스 안에 프로젝트를 만든다. 프로젝트는 일감의 묶음이자 권한 경계이므로, 만들 때 정하는 key 와 공개 범위가 곧 그 프로젝트의 주소와 보안 설정이다. moai-wiki 의 위키 만들기와 같은 규칙을 쓴다.

**세부 요구사항**
1. `POST /workspaces/{workspace}/projects` 는 `@WorkspaceApi(project.create)` 여야 한다. 기본 역할로는 소유자 · 관리자만 만든다. 사용자 정의 역할에 `project.create` 를 넣으면 그 역할도 만들 수 있다.
2. 입력은 key(필수) · 이름(필수, 앞뒤 공백을 뺀 1~100자) · 설명(0~1000자) · 공개 범위(`WORKSPACE` 기본 · `RESTRICTED`)다.
3. key 는 `^[a-z][a-z0-9-]{0,30}[a-z0-9]$`(영문 소문자로 시작하는 2~32자, 소문자 · 숫자 · 하이픈, 하이픈으로 끝나지 않음)에 맞아야 한다. 어기면 400 `INVALID_PROJECT_KEY` 다.
4. 예약 key(`new` · `archive` · `archived` · `settings` — 화면 주소와 부딪힌다)는 400 `RESERVED_PROJECT_KEY` 다.
5. key 는 한 워크스페이스에서 지우지 않은(상태가 `DELETED` 가 아닌) 프로젝트끼리 고유해야 한다. 같은 key 가 있으면 409 `PROJECT_KEY_TAKEN` 이다. 두 사람이 같은 key 로 동시에 만들면 DB 부분 고유 인덱스(`WHERE status <> 'DELETED'`)가 하나만 통과시키고, 나머지도 409 `PROJECT_KEY_TAKEN` 으로 답한다. 지운 프로젝트의 key 는 바로 다시 쓸 수 있다.
6. key 는 만든 뒤 바꿀 수 없어야 한다(화면 주소 · MCP 도구 입력에 쓰인다).
7. 새 프로젝트는 상태 `ACTIVE`, 일감 번호 순번(`issue_seq`) 0 으로 만든다. 만든 사람을 `created_by` 에 남긴다.
8. 만든 사람에게 프로젝트 역할을 자동으로 주지 않는다 — moai-wiki 의 위키와 같다. 사용자 정의 역할로 `RESTRICTED` 프로젝트를 만든 사람이 그 프로젝트를 보려면 소유자 · 관리자가 프로젝트 역할을 준다.
9. 응답은 201 과 프로젝트 상세(`myPermissions` · `myActions` 포함, [FR-PRJ-003](#fr-prj-003))다.
10. 만들기를 감사(`project.create` — key · 이름 · 공개 범위)에 남긴다.

**수용 기준**
- [x] 소유자 · 관리자가 만들면 201 이고, 만든 프로젝트가 목록에 보인다 (ProjectTest)
- [x] 큐레이터 · 멤버가 만들면 403 `PERMISSION_REQUIRED`, 워크스페이스 멤버가 아니면 404 `WORKSPACE_NOT_FOUND` 다 (ProjectTest)
- [x] 규칙에 맞지 않는 key(대문자 · 1자 · 33자 · 하이픈으로 끝남 · 숫자로 시작)는 400 `INVALID_PROJECT_KEY`, 예약 key 는 400 `RESERVED_PROJECT_KEY` 다 (ProjectTest)
- [ ] 같은 key 로 두 번 만들면 409 `PROJECT_KEY_TAKEN` 이고, 같은 key 로 동시에 보낸 두 요청 가운데 하나만 성공한다
- [x] 지운 프로젝트와 같은 key 로 새 프로젝트를 만들 수 있다 (ProjectTest)
- [x] `project.create` 가 든 사용자 정의 역할의 멤버가 만들 수 있고, 설정을 고칠 권한은 따로 없다(403) (ProjectTest)
- [x] 만들기가 감사에 남는다 (ProjectTest)

<a id="fr-prj-002"></a>
### FR-PRJ-002 프로젝트 목록

| 항목 | 내용 |
|---|---|
| 우선순위 | M |
| 진행 | 일부 — 한 것: api(볼 수 있는 프로젝트만 · 보관은 `?archived=true` 로 관리하는 사람에게만) · 자동 테스트. 남은 것: 다른 워크스페이스 프로젝트가 섞이지 않는지 목록 시험 · 화면. |
| 관련 설계 | [프로젝트 설계](../design/04-project.md) |

**설명** — 멤버는 자기가 볼 수 있는 프로젝트의 목록을 본다. 보관한 프로젝트는 그것을 관리할 수 있는 사람에게만 따로 보인다(moai-wiki 위키 목록의 보관 탭과 같다).

**세부 요구사항**
1. `GET /workspaces/{workspace}/projects` 는 `@WorkspaceApi(workspace.read)` 여야 한다.
2. 결과는 부르는 사람이 독자인([FR-PRJ-006](#fr-prj-006)) `ACTIVE` 프로젝트여야 한다. 이름순(바이트 순, `COLLATE "C"`)으로 준다.
3. `?archived=true` 면 보관한 프로젝트도 함께 주되, 그 프로젝트에서 `project.manage` 나 `project.delete` 를 가진 경우만 준다.
4. `DELETED` 프로젝트는 어떤 경우에도 목록에 없어야 한다.
5. 각 줄은 key · 이름 · 설명 · 공개 범위 · 상태 · 만든 때 · 보관한 때를 준다.

**수용 기준**
- [x] `RESTRICTED` 프로젝트는 그 프로젝트의 역할이 없는 멤버의 목록에 없고, 소유자 · 관리자의 목록에는 있다 (ProjectTest)
- [x] `WORKSPACE` 프로젝트는 모든 활성 멤버의 목록에 있다 (ProjectTest)
- [x] 보관한 프로젝트는 기본 목록에 없고, `?archived=true` 일 때 관리 권한이 있는 사람에게만 나온다 (ProjectTest)
- [ ] 지운 프로젝트와 다른 워크스페이스의 프로젝트는 어떤 경우에도 나오지 않는다

<a id="fr-prj-003"></a>
### FR-PRJ-003 프로젝트 설정

| 항목 | 내용 |
|---|---|
| 우선순위 | M |
| 진행 | 일부 — 한 것: api(`PATCH`, project.manage, key 는 바뀌지 않음, 감사 전 · 후) · 자동 테스트. 남은 것: project-editor 403 시험 · 감사 값 시험 · 화면. |
| 관련 설계 | [프로젝트 설계](../design/04-project.md) |

**설명** — 프로젝트의 상세를 보고, 관리 권한이 있는 사람은 이름 · 설명 · 공개 범위를 고친다. 상세에는 화면이 버튼을 숨기는 데 쓸 "내 권한"이 함께 온다.

**세부 요구사항**
1. `GET /projects/{project}` 는 `@ProjectApi(issue.read)` 여야 한다. 응답은 key · 이름 · 설명 · 공개 범위 · 상태 · 만든 때 · 고친 때 · 보관한 때와 `myPermissions`(그 프로젝트에서 가진 권한 코드, 정렬) · `myActions`(지금 상태에서 할 수 있는 일, 예: 보관한 프로젝트에서는 일감 만들기가 빠진다)다.
2. `PATCH /projects/{project}` 는 `@ProjectApi(project.manage)` 여야 한다. 기본 역할로는 소유자 · 관리자, 프로젝트 역할로는 그 프로젝트의 `project-admin` 이 고친다.
3. 고칠 수 있는 것은 이름 · 설명 · 공개 범위다. key 는 고칠 수 없다(요청 본문에 없다). 이름 · 설명의 길이 규칙은 [FR-PRJ-001](#fr-prj-001)과 같다.
4. `WORKSPACE` → `RESTRICTED` 로 바꾸면 그 프로젝트의 역할이 없는 멤버는 다음 요청부터 404 여야 한다. 반대로 바꾸면 모든 활성 멤버가 다음 요청부터 독자가 된다.
5. 보관한 프로젝트도 관리 권한이 있으면 설정을 고칠 수 있다(복원 전에 이름을 정리하는 경우).
6. 설정(`settings` jsonb)은 나중 후보 기능(예: 프로젝트별 일감 유형)을 위해 비워 둔다. 첫 출시에는 화면 · API 로 고치지 않는다.
7. 바꾼 값을 감사(`project.update` — 전 · 후)에 남긴다.

**수용 기준**
- [ ] 그 프로젝트의 `project-admin` 은 설정을 고칠 수 있고, `project-editor` 는 403 `PERMISSION_REQUIRED`, 독자가 아니면 404 `PROJECT_NOT_FOUND` 다
- [x] `RESTRICTED` 로 바꾸면 프로젝트 역할이 없는 멤버의 다음 요청이 404 다 (ProjectTest)
- [x] 상세의 `myPermissions` 가 그 사람의 실제 판정 결과(워크스페이스 역할 ∪ 프로젝트 역할)와 같다 (ProjectRoleTest)
- [x] key 는 어떤 요청으로도 바뀌지 않는다 (ProjectTest)
- [ ] 바꾼 값(전 · 후)과 바꾼 사람이 감사에 남는다

---

## 2. 보관 · 삭제

<a id="fr-prj-004"></a>
### FR-PRJ-004 보관 · 복원

| 항목 | 내용 |
|---|---|
| 우선순위 | M |
| 진행 | 일부 — 한 것: api(보관 · 복원은 project.delete, 보관한 프로젝트의 일감 쓰기 409 `PROJECT_NOT_WRITABLE`) · 자동 테스트. 남은 것: 고치기 409 · 복원 뒤 번호 이어짐 · 감사 시험 · 화면. |
| 관련 설계 | [프로젝트 설계](../design/04-project.md) |

**설명** — 끝난 프로젝트는 보관해 목록과 독자에게서 숨기고, 필요하면 복원한다. 보관한 프로젝트는 읽기 전용이고 일감 · 번호 · 이력은 그대로 남는다.

**세부 요구사항**
1. `POST /projects/{project}/archive`(이유, 선택) · `POST /projects/{project}/restore` 는 `@ProjectApi(project.delete)` 여야 한다. 기본 역할로는 소유자 · 관리자만 한다(`project-admin` 은 `project.delete` 가 없다).
2. 이미 보관한 프로젝트를 보관하면 409 `PROJECT_ALREADY_ARCHIVED`, 보관하지 않은 프로젝트를 복원하면 409 `PROJECT_NOT_ARCHIVED` 다.
3. 보관한 프로젝트는 `project.manage` · `project.delete` 가 없는 독자에게 404 `PROJECT_NOT_FOUND` 여야 한다(없는 프로젝트와 같다). 관리 권한이 있는 사람은 목록의 보관 탭에서 보고 열 수 있다.
4. 보관한 프로젝트의 일감 쓰기(만들기 · 고치기 · 댓글 · 연결 · 지우기)는 409 `PROJECT_NOT_WRITABLE` 이어야 한다.
5. MCP 도구는 보관한 프로젝트를 다루지 않는다([FR-MCP-003](04-mcp.md#fr-mcp-003)).
6. 복원하면 상태 · 일감 · 번호 순번 · 이력 · 프로젝트 역할이 보관 전과 같아야 한다.
7. 보관(`project.archive` — 이유) · 복원(`project.restore`)을 감사에 남긴다.

**수용 기준**
- [x] 보관하면 관리 권한이 없는 독자의 다음 요청이 404 이고, 관리 권한이 있는 사람은 보관 탭에서 연다 (ProjectTest)
- [ ] 보관한 프로젝트에 일감을 만들거나 고치면 409 `PROJECT_NOT_WRITABLE` 이다
- [ ] 복원하면 일감과 번호가 그대로이고, 다음에 만든 일감의 번호가 보관 전 순번에 이어진다
- [x] `project-admin` 이 보관하면 403 `PERMISSION_REQUIRED` 다 (ProjectRoleTest)
- [ ] 보관 · 복원이 감사에 남는다

<a id="fr-prj-005"></a>
### FR-PRJ-005 삭제

| 항목 | 내용 |
|---|---|
| 우선순위 | M |
| 진행 | 일부 — 한 것: api(보관한 것만 · `?confirm=key` · 소프트 삭제 · key 다시 쓰기) · 자동 테스트. 남은 것: 지운 뒤 Keycloak 그룹 · 역할 사본 정리 · MCP 엔드포인트 끄기(4단계) · 감사 시험 · 화면. |
| 관련 설계 | [프로젝트 설계](../design/04-project.md) |

**설명** — 보관한 프로젝트만 지운다. 실수를 막으려고 key 를 다시 입력해 확인한다(위험 작업 이중 확인, [FR-WSP-028](01-workspace.md#fr-wsp-028)). 지운 프로젝트는 아무에게도 보이지 않고 key 는 바로 다시 쓸 수 있다.

**세부 요구사항**
1. `DELETE /projects/{project}?confirm=<key>` 는 `@ProjectApi(project.delete)` 여야 한다.
2. 보관한(`ARCHIVED`) 프로젝트만 지울 수 있다. 아니면 409 `PROJECT_NOT_ARCHIVED` 다.
3. `confirm` 이 그 프로젝트의 key 와 다르면 400 `CONFIRM_MISMATCH` 다. api 가 다시 검사한다(화면만 막지 않는다).
4. 소프트 삭제다 — 상태를 `DELETED`, `deleted_at` 을 채운다. 일감 · 이력 · 연결은 DB 에 남지만 어떤 화면 · API · MCP 로도 열 수 없다. 영구 삭제(보존 기간 뒤)는 따로 한다.
5. 지우면 그 프로젝트의 Keycloak 그룹(`/projects/<project id>/*`)과 사본(`project_role_binding`)을 지워야 한다(멱등, [FR-PRJ-008](#fr-prj-008)).
6. 지운 프로젝트는 MCP 엔드포인트의 대상에서 빠져야 한다. 대상이 하나도 남지 않은 엔드포인트는 `DISABLED` 로 바꾼다([FR-MCP-002](04-mcp.md#fr-mcp-002)).
7. 화면은 지우기 전에 영향(일감 수)을 보여 주고 key 입력을 받는다.
8. 삭제를 감사(`project.delete` — key · 이름 · 일감 수)에 남긴다.

**수용 기준**
- [x] `ACTIVE` 프로젝트를 지우면 409 `PROJECT_NOT_ARCHIVED` 이고, `confirm` 이 틀리면 400 `CONFIRM_MISMATCH` 다 (ProjectTest)
- [x] 지운 뒤에는 소유자를 포함해 누구에게나 404 이고, 보관 목록에도 없다 (ProjectTest)
- [ ] 지운 프로젝트의 key 로 새 프로젝트를 만들 수 있고, 새 프로젝트의 일감 번호는 1 부터다
- [ ] 지운 뒤 그 프로젝트의 Keycloak 그룹과 역할 사본이 없다
- [ ] 지운 프로젝트만 대상이던 MCP 엔드포인트가 `DISABLED` 가 된다
- [ ] 삭제가 감사에 남는다

---

## 3. 독자 판정과 프로젝트 역할

<a id="fr-prj-006"></a>
### FR-PRJ-006 공개 범위와 독자 판정

| 항목 | 내용 |
|---|---|
| 우선순위 | M |
| 진행 | 일부 — 한 것: api(`@ProjectApi` · `ProjectAccessService` — moai-wiki 의 위키 판정과 같은 식, 기동 검사) · 자동 테스트. 남은 것: 정지한 멤버 404 시험. |
| 관련 설계 | [프로젝트 설계](../design/04-project.md) · [워크스페이스 설계](../design/03-workspace-identity.md) |

**설명** — 누가 프로젝트를 보고 무엇을 할 수 있는지는 api 의 `ProjectAccessService` 한 곳에서 정한다. moai-wiki 의 `WikiAccessService` 와 같은 식이다. 화면 · REST · MCP 가 모두 이 판정을 거친다.

**세부 요구사항**
1. 프로젝트 안의 모든 REST 핸들러는 `@ProjectApi(권한 코드)` 를 정확히 하나 가져야 한다(없거나 둘이면 기동하지 않는다, [FR-WSP-023](01-workspace.md#fr-wsp-023)). 판정이 끝나면 핸들러는 `ProjectContext`(워크스페이스 · 프로젝트 · 그 프로젝트에서의 권한)를 받는다.
2. 판정 규칙은 다음과 같아야 한다.

   | 판정 | 규칙 |
   |---|---|
   | 독자 | 워크스페이스 활성 멤버 ∧ (공개 범위 `WORKSPACE` ∨ 역할 key `owner` · `admin` ∨ 그 프로젝트의 프로젝트 역할을 가짐) |
   | 프로젝트 권한 | 워크스페이스 역할의 권한 ∪ 그 프로젝트의 프로젝트 역할의 권한 |
   | 보이는가 | 독자 ∧ (`ACTIVE` ∨ `project.manage` ∨ `project.delete`) |

3. 워크스페이스 멤버가 아니면 404 `WORKSPACE_NOT_FOUND`, 독자가 아니거나 · 프로젝트가 없거나 · 지웠거나 · 보관했는데 관리 권한이 없으면 404 `PROJECT_NOT_FOUND`, 독자인데 핸들러의 권한 코드가 없으면 403 `PERMISSION_REQUIRED` 여야 한다.
4. 없는 프로젝트와 볼 수 없는 프로젝트의 응답(상태 코드 · `code` · 본문)이 같아야 한다.
5. 판정은 요청마다 api DB 사본(`member_role` · `project_role_binding`)으로 해야 한다. 역할 · 정지 · 공개 범위 변경은 다음 요청부터 반영된다.
6. 프로젝트 역할의 권한은 그 프로젝트 안에서만 쓴다. 한 프로젝트의 `project-editor` 가 다른 프로젝트에서 일감을 만들 수는 없다.
7. MCP 도구도 같은 서비스를 부른다([FR-MCP-003](04-mcp.md#fr-mcp-003)).

**수용 기준**
- [x] `RESTRICTED` 프로젝트를 역할 없는 멤버가 열면 404 `PROJECT_NOT_FOUND` 이고, 없는 key 를 열었을 때와 응답이 같다 (ProjectTest)
- [x] 소유자 · 관리자는 프로젝트 역할이 없어도 모든 `RESTRICTED` 프로젝트의 독자다 (ProjectTest)
- [x] 멤버에게 프로젝트 역할을 주면 토큰을 다시 받지 않아도 다음 요청부터 그 `RESTRICTED` 프로젝트가 열린다 (ProjectRoleTest)
- [x] 기본 역할 멤버(읽기 · 댓글)가 일감을 만들면 403 이고, 같은 사람에게 그 프로젝트의 `project-editor` 를 주면 그 프로젝트에서만 만들어진다 (IssueTest)
- [ ] 정지한 멤버의 요청은 404 다
- [x] 프로젝트 안의 모든 REST 핸들러가 `@ProjectApi` 를 정확히 하나 가진다(기동 검사) (ProjectTest)

<a id="fr-prj-007"></a>
### FR-PRJ-007 프로젝트 역할

| 항목 | 내용 |
|---|---|
| 우선순위 | M |
| 진행 | 일부 — 한 것: api(역할 넷 · 여러 개 합치기 · 권한 상승 방지 `ESCALATION_DENIED` · 감사 `project.roles.update`) · 자동 테스트. 남은 것: 프로젝트 역할별 일감 쓰기 시험(편집자 관리자 바꾸기 · 지우기 403, 큐레이터 관리자 바꾸기) · 화면. |
| 관련 설계 | [프로젝트 설계](../design/04-project.md) |

**설명** — 프로젝트마다 사람에게 프로젝트 역할을 준다. 역할은 moai-wiki 의 위키 역할과 같은 모양의 고정 4개다. 기본 역할이 멤버(읽기 · 댓글)인 사람도 특정 프로젝트에서 일감을 만들고 고치려면 그 프로젝트의 편집자 이상을 받는다.

**세부 요구사항**
1. 프로젝트 역할은 다음 4개로 고정이어야 한다(사용자 정의 없음). 위 역할은 아래 역할의 권한을 모두 가진다.

   | 역할 | 이름 | 그 프로젝트 안의 권한 |
   |---|---|---|
   | `project-reader` | 프로젝트 독자 | `issue.read` · `issue.comment` |
   | `project-editor` | 프로젝트 편집자 | 독자 + `issue.create` · `issue.edit` |
   | `project-curator` | 프로젝트 큐레이터 | 편집자 + `issue.manager.assign` |
   | `project-admin` | 프로젝트 관리자 | 큐레이터 + `issue.delete` · `project.manage`(그 프로젝트의 설정 · 프로젝트 역할 관리) |

2. 한 사람이 한 프로젝트에서 역할을 여러 개 가질 수 있어야 한다(키 `(project_id, user_id, role)`). 권한은 합친다.
3. `GET /projects/{project}/roles` 는 `@ProjectApi(issue.read)` 로, 그 프로젝트의 역할 바인딩(사람 · 표시 이름 · 역할 · 준 사람 · 준 때)을 준다.
4. `PUT /projects/{project}/roles/{userId}` `{roles:[...]}` 는 `@ProjectApi(project.manage)` 로, 그 사람의 그 프로젝트 역할을 주어진 목록으로 바꿔야 한다. 빈 목록이면 모두 뺀다.
5. 받는 사람은 그 워크스페이스의 활성 멤버여야 한다. 아니면 404 `MEMBER_NOT_FOUND` 다. 없는 역할 key 는 400 `UNKNOWN_PROJECT_ROLE` 이다.
6. 바꾸는 사람은 주거나 빼는 역할의 권한을 그 프로젝트에서 모두 가지고 있어야 한다(권한 상승 방지). 아니면 403 `ESCALATION_DENIED` 다.
7. 바뀐 역할은 다음 요청부터 판정에 반영된다. 워크스페이스에서 나가거나 제거된 사람의 프로젝트 역할은 모두 빠진다([FR-WSP-013](01-workspace.md#fr-wsp-013)).
8. 바꾸기를 감사(`project.roles.update` — 사람 · 전 · 후)에 남긴다.

**수용 기준**
- [ ] `project-editor` 는 일감을 만들고 고치지만 관리자를 바꾸거나 지우면 403 이다
- [ ] `project-curator` 는 일감의 관리자를 바꾼다
- [x] `project-admin` 은 프로젝트 설정을 고치고 다른 사람에게 프로젝트 역할을 준다. 프로젝트를 보관하면 403 이다 (ProjectRoleTest)
- [x] 역할 두 개를 가진 사람의 권한이 두 역할의 합집합이다 (ProjectRoleTest)
- [x] 자기에게 없는 권한이 든 역할을 주려 하면 403 `ESCALATION_DENIED` 다 (ProjectRoleTest)
- [x] 워크스페이스 멤버가 아닌 사람에게 주면 404 `MEMBER_NOT_FOUND` 다 (ProjectRoleTest)
- [x] 빈 목록으로 바꾸면 그 사람이 다음 요청부터 그 `RESTRICTED` 프로젝트에서 404 다 (ProjectRoleTest)
- [x] 바꾸기가 감사에 남는다 (ProjectRoleTest)

<a id="fr-prj-008"></a>
### FR-PRJ-008 프로젝트 역할의 정본 (Keycloak 그룹)

| 항목 | 내용 |
|---|---|
| 우선순위 | M |
| 진행 | 일부 — 한 것: api(Keycloak 조직 그룹 `/projects/<id>/<role>` 에 먼저 쓰고 사본, 쓰기 기록으로 이어서 끝냄, 워크스페이스를 나가면 사본 삭제) · 자동 테스트(가짜 Keycloak 그룹 소속). 남은 것: 사본의 그룹 id · 매핑 없음 시험 · 실패 주입 시험 · 대사(15분) · 나간 사람의 Keycloak 그룹 시험. |
| 관련 설계 | [프로젝트 설계](../design/04-project.md) · [워크스페이스 설계 §6](../design/03-workspace-identity.md#ws-sor) |

**설명** — 계정 · 멤버십 · 워크스페이스 역할처럼, 프로젝트 역할의 정본도 Keycloak Organizations 다. api 는 Keycloak 에 먼저 쓰고 DB 사본으로 판정한다. 역할이 고정이라 그룹에는 클라이언트 역할을 매핑하지 않는다.

**세부 요구사항**
1. 프로젝트 역할은 Keycloak 조직 그룹 `/projects/<project id>/<role>` 에 사람을 넣어 둬야 한다. 그룹은 그 역할을 처음 줄 때 만든다(빈 그룹을 미리 만들지 않는다).
2. 사본은 `project_role_binding(project_id, user_id, role, keycloak_group_id, granted_by, granted_at)` 이다.
3. 이중 쓰기는 기존 `KeycloakOperationLog`(쓰기 기록 · 멱등 · 이어서 끝내기)로 해야 한다. Keycloak 먼저, 그다음 사본이다.
4. 그룹에는 클라이언트 역할을 매핑하지 않는다. 역할 → 권한 코드는 api 의 `ProjectRole` 열거형이 갖는다. moai-wiki 가 위키 역할을 만들 때도 같게 하도록 설계에 적어 둔다.
5. 15분 대사([FR-WSP-017](01-workspace.md#fr-wsp-017))는 프로젝트 그룹도 Keycloak 기준으로 맞춘다.
6. 판정은 사본으로 하므로 Keycloak 이 멈춰도 프로젝트 판정은 계속돼야 한다. 그동안 역할 바꾸기는 실패로 답한다.

**수용 기준**
- [ ] 역할을 주면 그 사람이 Keycloak 그룹 `/projects/<project id>/<role>` 에 있고, 사본의 `keycloak_group_id` 가 그 그룹이다
- [ ] 프로젝트 그룹에는 클라이언트 역할 매핑이 없다
- [ ] Keycloak 쓰기 중간에 실패를 넣고 같은 요청을 다시 하면 이어서 끝나고 그룹 · 사본이 중복되지 않는다
- [ ] Keycloak 콘솔에서 그룹의 사람을 빼면 대사 뒤 사본에서도 빠지고 감사에 남는다
- [ ] 워크스페이스에서 멤버를 제거하면 그 사람이 모든 프로젝트 그룹과 사본에서 빠진다

---

## 4. 기본 프로젝트 · 화면 · 감사

<a id="fr-prj-009"></a>
### FR-PRJ-009 기본 프로젝트 (내 프로젝트)

| 항목 | 내용 |
|---|---|
| 우선순위 | M |
| 진행 | 일부 — 한 것: api(개인 워크스페이스를 만들 때 "내 프로젝트", 프로젝트가 한 번도 없었을 때만) · 자동 테스트. 남은 것: 두 번 돌려도 하나 · 지운 뒤 다시 안 생김 · 주인의 일감 만들기 · 감사 시험. |
| 관련 설계 | [프로젝트 설계](../design/04-project.md) · [워크스페이스 설계 §8.2](../design/03-workspace-identity.md#ws-login) |

**설명** — 개인 워크스페이스를 만들면 바로 일감을 적을 수 있게 기본 프로젝트 "내 프로젝트"를 함께 만든다. moai-wiki 가 개인 워크스페이스에 "내 위키"를 만드는 것과 같다.

**세부 요구사항**
1. 개인 워크스페이스를 만드는 `WorkspaceProvisioner` 의 마지막 단계(소유자 그룹 가입 뒤)에서 프로젝트 key `my`, 이름 "내 프로젝트", 공개 범위 `WORKSPACE` 를 만들어야 한다([FR-WSP-007](01-workspace.md#fr-wsp-007)).
2. 멱등해야 한다 — 그 워크스페이스에 프로젝트가 한 번도 없었을 때만(지운 것 포함) 만든다. 그래서 다시 실행해도 둘이 생기지 않고, 사람이 "내 프로젝트"를 지운 뒤에는 다시 만들지 않는다.
3. 팀 워크스페이스에는 기본 프로젝트를 만들지 않는다.
4. 이 기능을 넣기 전에 만든 개인 워크스페이스에도 한 번 채운다(이행 — 프로젝트가 한 번도 없었던 곳만).
5. 이름은 사람이 나중에 고칠 수 있다. key 는 다른 프로젝트처럼 바꿀 수 없다.
6. 만들기를 감사(`project.create`, 기본 프로젝트 표시)에 남긴다.

**수용 기준**
- [ ] 새 계정의 첫 로그인 뒤 개인 워크스페이스에 key `my` 프로젝트가 있고, 주인이 일감을 만들 수 있다
- [ ] 만드는 단계를 두 번 돌려도 프로젝트가 하나다
- [ ] "내 프로젝트"를 지운 뒤 다시 로그인해도 새로 생기지 않는다
- [x] 팀 워크스페이스를 만들면 프로젝트가 없다 (ProjectTest)
- [ ] 만들기가 감사에 남는다

<a id="fr-prj-010"></a>
### FR-PRJ-010 프로젝트 화면

| 항목 | 내용 |
|---|---|
| 우선순위 | M |
| 진행 | 일부 — 한 것: 화면(프로젝트 목록 · 카드 · 만들기 · 설정 대화 상자 · 보관함 · 지우기 대화 상자 · 프로젝트 홈 · 멤버 화면 — moai-wiki 의 위키 화면 구조) · 화면 자동 테스트 · 브라우저 확인(프로젝트 홈 · 멤버 화면, 데스크톱 · 390px · 다크 · 영어, 멤버에게 설정 · 보관 단추가 없음). 남은 것: 브라우저에서 만들기 · 보관 · 복원 · 지우기 흐름과 멤버 화면의 역할 주기 확인. |
| 관련 설계 | [프로젝트 설계](../design/04-project.md) · [WEB-DESIGN.md](../../WEB-DESIGN.md) |

**설명** — 웹 화면은 moai-wiki 의 위키 화면 구조를 그대로 쓴다. 위키 목록 · 위키 만들기 · 위키 안 화면이 프로젝트 목록 · 프로젝트 만들기 · 프로젝트 안 화면이 된다.

**세부 요구사항**
1. 경로와 구성은 다음과 같아야 한다.

   | 경로 | 화면 | moai-wiki 대응 |
   |---|---|---|
   | `/w/:slug/projects` | `ProjectsPage` — 프로젝트 목록(이름 · key · 공개 범위), 보관 탭(관리 권한이 있는 사람에게만), 만들기 버튼 | `WikisPage` |
   | (대화 상자) | `ProjectDialog` — 만들기 · 설정(key · 이름 · 설명 · 공개 범위, 설정에서는 key 읽기 전용, key 규칙을 입력하는 자리에서 알림) | `WikiDialog` |
   | (대화 상자) | `RemoveProjectDialog` — 영향(일감 수)을 보이고 key 를 입력받아 지우기 | `RemoveWikiDialog` |
   | `/w/:slug/projects/:projectKey` | `ProjectLayout` — 머리(이름 · key · 공개 범위 · 보관 알림) · 메뉴(일감 · 멤버 · 설정). 홈 `''` 은 `ProjectHomePage`(일감 목록, [FR-ISS-014](03-issue.md#fr-iss-014)) | `WikiLayout` · `WikiHomePage` |
   | `/w/:slug/projects/:projectKey/members` | 프로젝트 역할 — 바인딩 목록, 사람 더하기(활성 멤버에서 고름), 역할 체크 | 위키 역할 화면 |

2. 보관 · 복원 · 지우기 · 설정 동작은 `use-project-actions` 한 곳에 둔다(moai-wiki `use-wiki-actions` 와 같다).
3. 버튼은 상세의 `myPermissions` · `myActions` 로 숨긴다. 숨기는 것은 편의이고 판정은 api 가 한다.
4. 없거나 볼 수 없는 프로젝트는 같은 "프로젝트를 찾을 수 없습니다" 화면이어야 한다.
5. 워크스페이스 메뉴에 "프로젝트"를 둔다. 개인 워크스페이스에서는 "내 프로젝트"로 바로 간다.
6. 390px 와 데스크톱에서 가로 스크롤 없이 쓴다. ko/en, 다크 모드.

**수용 기준**
- [ ] 화면에서 프로젝트를 만들면 목록에 나오고 그 프로젝트의 일감 목록으로 간다
- [ ] 기본 역할 멤버에게는 만들기 · 설정 · 보관 · 지우기 버튼이 보이지 않는다
- [ ] 보관 탭은 관리 권한이 있는 사람에게만 보이고, 거기서 복원 · 지우기를 한다
- [ ] 지우기 대화 상자는 key 를 맞게 입력해야 실행된다
- [ ] 멤버 화면에서 역할을 주면 바로 목록에 반영된다
- [x] ego-browser 로 390px · 데스크톱 · 다크 · 영어를 확인했다 (ego-browser 2026-11-22 — 프로젝트 홈 · 일감 화면)

<a id="fr-prj-011"></a>
### FR-PRJ-011 프로젝트 감사

| 항목 | 내용 |
|---|---|
| 우선순위 | M |
| 진행 | 일부 — 한 것: api(만들기 · 설정 · 보관 · 복원 · 삭제 · 역할 바꾸기를 업무와 같은 트랜잭션으로 감사). 남은 것: 행동별 값 시험 · 실패 시 되돌림 시험 · audit.read 시험. |
| 관련 설계 | [프로젝트 설계](../design/04-project.md) · [워크스페이스 설계 §15](../design/03-workspace-identity.md#ws-audit) |

**설명** — 프로젝트의 모든 쓰기를 워크스페이스 감사([FR-WSP-024](01-workspace.md#fr-wsp-024))에 남겨, 누가 언제 프로젝트를 만들고 고치고 숨기고 지웠는지, 누구에게 어떤 프로젝트 역할을 줬는지 추적한다.

**세부 요구사항**
1. 다음 행동을 감사에 남겨야 한다.

   | `action` | 남기는 값 |
   |---|---|
   | `project.create` | key · 이름 · 공개 범위 · 기본 프로젝트 여부 |
   | `project.update` | 바뀐 값의 전 · 후 |
   | `project.archive` | 이유 |
   | `project.restore` | — |
   | `project.delete` | key · 이름 · 일감 수 |
   | `project.roles.update` | 받는 사람 · 역할 전 · 후 |

2. 대상 종류는 `project`, 대상 id 는 프로젝트 id, 그리고 워크스페이스 · 주체 · 경로 · IP 를 함께 남긴다.
3. 업무와 같은 트랜잭션이어야 한다. 감사 저장이 실패하면 그 쓰기도 되돌린다.
4. 워크스페이스 감사 화면에서 대상 종류 `project` 로 거를 수 있어야 한다(`audit.read`).

**수용 기준**
- [ ] 표의 모든 행동이 시험 시나리오에서 감사에 남고 값이 맞다
- [ ] 감사 저장을 실패시키면 프로젝트 쓰기도 되돌려진다
- [ ] `audit.read` 가 없는 `project-admin` 은 감사를 볼 수 없다(403)

---

**관련 문서:** [프로젝트 설계](../design/04-project.md) · [워크스페이스 · 계정 요구사항](01-workspace.md) · [일감 요구사항](03-issue.md) · [MCP 요구사항](04-mcp.md) · [요구사항 전체 목록](00-overview.md)

[← 이전: 워크스페이스 · 계정 요구사항](01-workspace.md) | [다음: 일감 요구사항 →](03-issue.md)
