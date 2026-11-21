[홈](../README.md) › [설계](01-architecture.md) › 계획 · 시간

# 계획 · 시간 설계 — 버전 · 로드맵 · 간트 · 캘린더 · 시간 기록 · 보고서

일감을 **언제까지 무엇을 내보낼지**(버전 · 로드맵 · 간트 · 캘린더)와 **얼마나 들었는지**(시간 기록 · 보고서)를 정한다. Redmine 의 동작을 기준으로 하되, 워크스페이스 · 권한 세 층 · MCP 동등성 · Agent 계정 같은 moai-works 결정을 따른다. 일감 모델(번호 · 이력 · 하위 일감 롤업 · 진척률 규칙)은 [일감 설계](05-issue-tracking.md), 프로젝트 · 모듈 · 프로젝트 역할은 [프로젝트 권한 설계](04-project-permissions.md)에 있다.

요구사항: [FR-PLN](../requirements/06-planning.md) · [FR-TIM](../requirements/07-time.md) · 결정 기록: [핵심 설계 결정](00-key-decisions.md)

**바로가기:** [원칙](#pt-principles) · [권한 · 모듈](#pt-permissions) · [버전](#pt-version) · [공유 범위](#pt-sharing) · [로드맵](#pt-roadmap) · [진척 계산](#pt-progress) · [간트](#pt-gantt) · [캘린더](#pt-calendar) · [시간 기록](#pt-time) · [시간 입력](#pt-time-input) · [보고서](#pt-report) · [이벤트](#pt-events) · [테이블](#pt-tables) · [REST](#pt-api) · [MCP](#pt-mcp) · [시험](#pt-tests) · [정해야 할 것](#pt-decisions)

---

<a id="pt-principles"></a>
## 1. 원칙

| 원칙 | 내용 |
|---|---|
| Redmine 과 같은 뜻 | 버전 상태(열림 · 잠김 · 닫힘), 공유 범위 다섯 가지, 로드맵 진척(예상 시간 가중), 간트 막대 규칙, 시간 기록 필드를 Redmine 과 같은 뜻으로 둔다. Redmine 에서 옮겨 와도 숫자가 같아야 한다. 다른 곳은 [§5.5](#pt-redmine-diff)에 모았다 |
| 보는 사람 기준으로 센다 | 로드맵 진척 · 간트 · 캘린더 · 시간 합계는 **보는 사람이 읽을 수 있는 일감 · 시간 기록만** 센다(Redmine 도 `visible_fixed_issues` 로 센다). 그래서 사람마다 숫자가 다를 수 있고, 미리 계산해 두지 않는다 |
| 판정은 api 한 곳 | 화면은 숨기기만 한다. 간트 · 캘린더 · 보고서도 api 가 거른 데이터만 받는다. 읽기 거부 404, 쓰기 거부 403 |
| 계산은 PostgreSQL | 진척 · 합계 · 보고서는 SQL 집계로 요청마다 계산한다. 검색 색인(OpenSearch)은 쓰지 않는다 |
| 시간은 사람의 것 | 시간 기록의 "누구의 시간"은 사람 멤버만 될 수 있다. Agent 계정은 사람 대신 기록하는 "기록한 사람"이 될 수 있다([§8.5](#pt-time-others)) |
| 같은 서비스 | REST · MCP(`list_versions` · `get_roadmap` · `list_time_entries` · `log_time`)는 같은 서비스 · 같은 판정을 부른다 |

---

<a id="pt-permissions"></a>
## 2. 권한 · 모듈

판정은 [프로젝트 권한 설계](04-project-permissions.md)의 규칙 그대로다: **프로젝트 독자인가 ∧ 모듈이 켜져 있음 ∧ (코드 ∈ 워크스페이스 권한 ∨ 코드 ∈ 그 프로젝트 역할 권한)**. 아래 모듈 키는 04 의 모듈 목록(`issue_tracking` · `time_tracking` · `gantt` · `calendar`)과 같다. `gantt` · `calendar` 는 `issue_tracking` 없이 켤 수 없다.

| 할 일 | 권한 코드 | 모듈 | 어노테이션 |
|---|---|---|---|
| 버전 목록 · 버전 쪽 · 로드맵 보기 | `issue.read` | `issue_tracking` | `@ProjectApi(ISSUE_READ)` |
| 버전 만들기 · 고치기 · 상태 바꾸기 · 지우기 | `version.manage` | `issue_tracking` | `@ProjectApi(VERSION_MANAGE)` |
| 공유 범위를 `HIERARCHY` · `TREE` 로 | 루트 프로젝트에서 `version.manage` | | 서비스에서 한 번 더 판정 |
| 공유 범위를 `WORKSPACE` 로 | **워크스페이스 역할**로 `version.manage`(기본 owner · admin) | | 서비스에서 한 번 더 판정 |
| 기본 버전 정하기 | `project.manage` | | 프로젝트 설정 |
| 간트 보기 | `issue.read` | `gantt` | `@ProjectApi(value = ISSUE_READ, module = GANTT)` |
| 캘린더 보기 | `issue.read` | `calendar` | `@ProjectApi(value = ISSUE_READ, module = CALENDAR)` |
| 시간 기록 목록 · 보고서 · 일감의 소요 시간 보기 | `time.read` | `time_tracking` | `@ProjectApi(TIME_READ)` |
| 시간 기록하기(내 시간) | `time.log` | `time_tracking` | `@ProjectApi(TIME_LOG)` · `@IssueApi(TIME_LOG)` |
| 내 기록 고치기 · 지우기 | `time.edit.own` | `time_tracking` | 서비스에서 작성자 확인 |
| 모든 기록 고치기 · 지우기 · **남 대신 기록** | `time.edit.all` | `time_tracking` | `@ProjectApi(TIME_EDIT_ALL)` |
| 프로젝트 작업 분류 켜고 끄기 | `project.manage` | `time_tracking` | 프로젝트 설정 |

- 프로젝트 역할 → 코드(정본은 [프로젝트 권한 설계](04-project-permissions.md)의 역할별 표): 독자 · 보고자 = `issue.read` `time.read` · 개발자 = + `time.log` `time.edit.own` · 관리자 = + `version.manage` `time.edit.all`. **보고자는 시간을 기록하지 않는다**(Redmine 기본과 같다, [PT-Q1](#pt-decisions) — 04 가 같은 답으로 확정).
- 워크스페이스 층에서 코드를 가진 사람(기본 owner · admin)은 그 사람이 독자인 모든 프로젝트에서 위 일을 다 한다. Agent 계정은 프로젝트 역할을 받아야 한다(워크스페이스 권한 없음).
- **닫힌 프로젝트**(`CLOSED`)에서는 버전 · 시간 기록의 쓰기가 409 `PROJECT_READ_ONLY` 다(권한이 아니라 상태 — 04 의 규칙). 보기는 된다.
- 모듈이 꺼지면 그 화면 · API · MCP 도구는 404 다. 데이터는 지우지 않는다(다시 켜면 그대로 보인다). `time_tracking` 이 꺼지면 일감 쪽의 "소요 시간" 칸 · 시간 기록 칸도 숨긴다.

---

<a id="pt-version"></a>
## 3. 버전

### 3.1 필드

| 필드 | 규칙 | Redmine |
|---|---|---|
| 이름 | 프로젝트 안에서 겹치지 않는다(대소문자 무시). 60자 | `name` |
| 설명 | 1000자. 한 줄 설명이고 긴 글은 위키 쪽에 쓴다 | `description` |
| 기한 | 날짜(선택). 로드맵 정렬 · 늦음 판정의 기준 | `effective_date` |
| 상태 | `OPEN` · `LOCKED` · `CLOSED` ([§3.2](#pt-version-status)) | `status` |
| 공유 범위 | `NONE` · `DESCENDANTS` · `HIERARCHY` · `TREE` · `WORKSPACE` ([§4](#pt-sharing)) | `sharing`(`system` → `WORKSPACE`) |
| 위키 쪽 | 같은 프로젝트 위키의 쪽 이름(선택). 버전 쪽에 그 쪽 본문을 함께 보인다. 쪽이 없으면 "쪽 만들기" 링크 | `wiki_page_title` |
| 사용자 정의 필드 | 대상이 "버전"인 필드. 값은 `custom_values` JSONB ([일감 설계](05-issue-tracking.md)) | 버전 사용자 정의 필드 |
| 기본 버전 | 프로젝트 설정 `default_version_id`. 새 일감에 대상 버전을 주지 않으면 이 값을 넣는다(열린 버전일 때만) | `default_version_id` |
| 텍스트 링크 | `version:"1.0"` · `version:3`(id) · `프로젝트:version:"1.0"`. 이 쪽이 [일감 설계](05-issue-tracking.md)의 `TextReferenceResolver` 에 버전 풀이를 꽂는다. 보는 사람이 그 버전을 쓸 수 있는 프로젝트를 못 읽으면 `{"visible": false}` | `version:` 링크 |

계산 값(저장하지 않는다):

| 값 | 계산 |
|---|---|
| 시작일 | 그 버전에 묶인 (보이는) 일감의 가장 이른 시작일 |
| 늦음(overdue) | 기한 < 오늘 ∧ 열린 일감이 있음 |
| 일정보다 늦음(behind schedule) | 완료율 < 100 ∧ 시작일 · 기한이 있음 ∧ `시작일 + (기한 − 시작일 + 1) × 완료율 / 100 < 오늘` |
| 완료됨(completed) | 상태 = `CLOSED` ∨ (기한 < 오늘 ∧ 열린 일감이 없음) |
| 예상 합계 · 소요 합계 | [§5.4](#pt-version-page) |

"오늘"은 보는 사람의 시간대 날짜다(사용자 설정 → 워크스페이스 설정 → `Asia/Seoul` 순).

<a id="pt-version-status"></a>
### 3.2 상태

| 상태 | 새 일감을 이 버전에 넣기 | 이미 넣은 일감 | 대상 버전 고르기 목록 | 로드맵 |
|---|---|---|---|---|
| `OPEN` 열림 | 된다 | 그대로 | 나온다 | 나온다 |
| `LOCKED` 잠김 | **안 된다** | 그대로 두고, 고칠 수 있다(빼기는 된다) | 지금 값일 때만 나온다 | 나온다 |
| `CLOSED` 닫힘 | **안 된다** | 그대로 | 지금 값일 때만 나온다 | "완료 버전 보기"를 켜야 나온다 |

- 잠김 · 닫힘 버전에 넣으려 하면 일감 저장이 422 다(필드 오류 `fixed_version`). 이미 그 버전인 일감을 다른 필드만 고칠 때는 막지 않는다(Redmine 과 같다).
- 닫을 때 열린 일감이 남아 있으면 **막지 않고 경고**한다: "열린 일감 N개가 남아 있다 — 다음 버전으로 옮기겠나?" 옮기기는 일괄 수정 화면으로 넘긴다([일감 요구사항](../requirements/03-issue.md)). (정해야 할 것 [PT-Q8](#pt-decisions))
- 상태 바꾸기는 버전 고치기와 같은 `version.manage` 다. 감사와 이벤트 `version.updated`(바뀐 필드 `status`)를 남긴다.

<a id="pt-version-delete"></a>
### 3.3 지우기

- **일감이 하나라도 묶여 있으면 지울 수 없다**(409, "일감 N개가 이 버전을 쓴다"). Redmine 과 같다. 다른 프로젝트의 공유받은 일감도 센다 — 보는 사람이 그 일감을 읽을 수 없어도 센다(수만 알린다).
- 묶인 내려받기 파일([공동 작업 설계](08-collaboration.md#co-file))이 있어도 지울 수 없다. 파일을 먼저 옮기거나 지운다.
- 프로젝트의 기본 버전이면 지우면서 기본 버전을 비운다.
- 지우기는 되돌릴 수 없다. 감사에 이름 · 기한 · 상태를 남긴다. 대신 닫기를 권한다.

---

<a id="pt-sharing"></a>
## 4. 공유 범위

### 4.1 다섯 가지

버전 V 가 프로젝트 P 에 있을 때, V 를 **대상 버전으로 쓸 수 있는 프로젝트**:

| 값 | 화면 이름 | 쓸 수 있는 프로젝트 | 바꿀 수 있는 사람 |
|---|---|---|---|
| `NONE` | 공유 안 함 | P | P 에서 `version.manage` |
| `DESCENDANTS` | 하위 프로젝트 | P 와 그 아래 모든 하위 | P 에서 `version.manage` |
| `HIERARCHY` | 계층 | P 의 상위들 + P + P 의 모든 하위 | **루트 프로젝트**에서 `version.manage` |
| `TREE` | 트리 | P 가 속한 루트 프로젝트 아래 트리 전체 | **루트 프로젝트**에서 `version.manage` |
| `WORKSPACE` | 워크스페이스 | 워크스페이스의 모든 프로젝트 | **워크스페이스 역할**로 `version.manage` |

- Redmine 의 `system`(시스템 전체)을 moai-works 는 `WORKSPACE` 로 바꿨다. 워크스페이스를 넘어 공유하는 일은 없다.
- 지금 값과 같은 값은 누구든 다시 저장할 수 있다(Redmine `allowed_sharings` 와 같다 — 다른 필드만 고칠 때 막히지 않게).
- 보관한 프로젝트는 공유 대상에서 빠진다.

### 4.2 보이는 것

- 공유받은 프로젝트 Q 의 독자는 V 의 **이름 · 기한 · 상태 · 설명**을 본다. 고르기 목록 · 로드맵 · 버전 쪽에 나온다.
- V 의 원래 프로젝트 P 를 읽을 수 없는 사람에게는 P 의 이름 대신 "다른 프로젝트"로 보인다(존재를 숨긴다는 원칙, 정해야 할 것 [PT-Q7](#pt-decisions)).
- 진척 · 일감 목록은 늘 **보는 사람이 읽을 수 있는 일감**만으로 계산한다. Q 의 독자가 P 의 일감을 읽을 수 없으면 Q 쪽 일감만 센다.
- 버전 쪽(`/versions/{id}`)은 V 를 쓸 수 있는 프로젝트 가운데 하나라도 읽을 수 있으면 열린다. 아니면 404.

<a id="pt-assignable"></a>
### 4.3 대상 버전으로 고를 수 있는 버전

일감 I(프로젝트 Q)에 고를 수 있는 버전 = **Q 에 공유된 버전 중 상태 `OPEN`** + I 의 지금 값(잠김 · 닫힘이어도). 일감 저장 때 서비스가 다시 검사한다(화면 목록만 믿지 않는다).

```sql
-- :q = 프로젝트 Q, :q_root = Q 의 root_id, :q_ancestors = Q 의 ancestor_ids (맨 위부터 부모까지)
SELECT v.* FROM version v JOIN project p ON p.id = v.project_id
 WHERE v.workspace_id = :ws AND p.status IN ('ACTIVE', 'CLOSED')
   AND (   v.project_id = :q
        OR (v.sharing = 'WORKSPACE')
        OR (v.sharing = 'TREE'        AND p.root_id = :q_root)
        OR (v.sharing IN ('DESCENDANTS','HIERARCHY') AND v.project_id = ANY(:q_ancestors))   -- 상위의 버전이 아래로
        OR (v.sharing = 'HIERARCHY'   AND :q = ANY(p.ancestor_ids))                          -- 하위의 버전이 위로
       );
```

프로젝트 트리 열 `root_id` · `ancestor_ids`(GIN 색인)는 [프로젝트 권한 설계](04-project-permissions.md)의 `project` 표 그대로다. 보관 · 지운 프로젝트의 버전은 고를 수 없다.

### 4.4 공유 범위 · 프로젝트 트리가 바뀔 때

공유 범위를 좁히거나, 프로젝트를 다른 상위 아래로 옮기거나, 프로젝트를 보관하면 **더는 쓸 수 없는 버전을 가진 일감의 대상 버전을 비운다**(Redmine `update_versions_from_sharing_change` · `update_versions_from_hierarchy_change` 와 같다).

| 단계 | 내용 |
|---|---|
| 1 | 바뀐 버전(또는 옮긴 프로젝트 트리)에 걸린 일감 중 "그 일감의 프로젝트에 이제 공유되지 않는" 것을 찾는다 |
| 2 | 같은 트랜잭션에서 그 일감들의 `fixed_version_id` 를 비운다 |
| 3 | 일감마다 이력(저널) 한 줄 — 행위자는 바꾼 사람, 원인 `cause = VERSION_SHARING`([일감 설계](05-issue-tracking.md)의 저널 원인), 상세 줄 `ATTR fixed_version` |
| 4 | 이벤트 `issue.updated` 를 일감마다 낸다. 한 번의 변경에 같은 `bulkId` 를 붙여 알림은 사람마다 한 통으로 묶는다([공동 작업 설계](08-collaboration.md#co-notify)) |

- 화면은 저장 전에 "일감 N개의 대상 버전이 비워진다"를 미리 보여 주고 확인을 받는다. 1,000개가 넘으면 작업 큐로 나눠 처리한다.

---

<a id="pt-roadmap"></a>
## 5. 로드맵

### 5.1 무엇이 나오나

| 항목 | 규칙 |
|---|---|
| 버전 | 프로젝트에 공유된 버전 전부(자기 것 + 공유받은 것). "하위 프로젝트 포함"을 켜면 하위 프로젝트의 버전도 |
| 완료 버전 | 기본으로 숨긴다. "완료 버전 보기"를 켜면 [완료됨](#pt-version) 버전도 나온다 |
| 정렬 | 기한 오름차순, 기한 없는 버전은 뒤로, 같으면 이름순(Redmine `Version#<=>` 와 같다) |
| 트래커 거르기 | 트래커 설정 "로드맵에 보이기"(`is_in_roadmap`)가 켜진 트래커가 기본. 사이드바에서 골라 바꾼다. **진척 계산에도 고른 트래커만** 쓴다 |
| 버전마다 | 이름 · 기한(남은 날 · 늦음 표시) · 설명 · 진척 막대([§5.2](#pt-progress)) · "일감 N개 (닫힘 M — 열림 K)" · 고른 트래커의 일감 목록(트래커 · 번호 · 제목, 닫힌 일감은 줄 긋기) · 위키 쪽 링크 |
| 사이드바 | 트래커 체크 · 완료 버전 보기 · 하위 프로젝트 포함 · 버전 목록(바로가기) |

<a id="pt-progress"></a>
### 5.2 진척 계산

Redmine 의 `Version#completed_percent` · `closed_percent` 와 같은 식이다. 대상 일감 집합 S = 그 버전에 묶인 일감 중 **보는 사람이 읽을 수 있고, 고른 트래커이고, 하위 일감이 없는 일감(잎)**.

```
n            = |S|
예상_i        = 일감 i 의 예상 시간 (없거나 0 이면 비어 있음)
평균예상      = 예상이 있는 일감들의 예상 평균. 예상이 있는 일감이 없으면 1
가중_i        = 예상_i 가 있으면 예상_i, 없으면 평균예상
진척_i        = 일감 i 가 닫힌 상태면 100, 아니면 진척률(0~100)
닫힘 비율(%)   = Σ_{i 닫힘} 가중_i × 100 / Σ 가중_i
완료율(%)      = Σ 가중_i × 진척_i / Σ 가중_i
특례: n = 0 이면 둘 다 0. 열린 일감이 없으면 완료율 = 100
```

- `Σ 가중_i = 평균예상 × n` 이 성립한다(예상 없는 일감에 평균을 주므로). 그래서 Redmine 의 분모 `estimated_average × count` 와 같다.
- 진척률은 일감의 `done_ratio` 다. 워크스페이스 설정이 "상태로 정함"이면 상태의 기본 진척률을 쓴다. 이 규칙은 [일감 설계](05-issue-tracking.md)가 정하고, 여기서는 받은 값을 쓴다.
- 진척 막대는 두 겹: **닫힘 비율**(진한 색) 위에 **완료율**(옅은 색). 글자로 "완료율 62%".
- 계산은 SQL 한 번(버전 id 묶음으로 GROUP BY). 로드맵 한 화면의 버전이 많아도 질의 수는 일정하다.

보기 — 일감 4개, 예상 8 · 4 · 없음 · 없음(평균예상 6):

| 일감 | 예상 | 가중 | 상태 | 진척 | 가중 × 진척 |
|---|---|---|---|---|---|
| #10 | 8 | 8 | 닫힘 | 100 | 800 |
| #11 | 4 | 4 | 진행 | 50 | 200 |
| #12 | — | 6 | 새로 | 0 | 0 |
| #13 | — | 6 | 닫힘 | 100 | 600 |
| 합 | | 24 | | | 1600 |

닫힘 비율 = (8 + 6) × 100 / 24 = 58.3%, 완료율 = 1600 / 24 = 66.7%.

### 5.3 일감 수 · 상태 묶음

- "일감 N개 (닫힘 M — 열림 K)": S 기준(잎만이 아니라 **보이는 모든 묶인 일감**을 센다 — 사람이 목록에서 보는 수와 맞추려고). 진척 계산만 잎을 쓴다.
- 버전 쪽에서는 상태 · 트래커 · 우선순위 · 카테고리 · 담당자로 묶은 수를 막대로 보인다. 막대를 누르면 그 조건의 일감 목록(질의)으로 간다.

<a id="pt-version-page"></a>
### 5.4 버전 쪽

| 칸 | 내용 |
|---|---|
| 머리 | 이름 · 상태 · 기한(늦음 · 일정보다 늦음 표시) · 공유 범위 · 원래 프로젝트 |
| 진척 | §5.2 막대 + 닫힘/열림 수 |
| 시간 | **예상 합계** = S(잎)의 예상 합. **소요 합계** = 그 버전에 묶인 보이는 일감(상위 포함)에 붙은 시간 기록의 합. 소요 합계는 `time.read` 가 있는 프로젝트의 기록만 센다. `time_tracking` 모듈이 꺼진 프로젝트는 뺀다 |
| 묶음 | 상태 · 트래커 · 우선순위 · 카테고리 · 담당자 중 하나로 묶은 수 |
| 일감 목록 | 고른 트래커의 일감(로드맵과 같은 목록) |
| 위키 | 연결한 위키 쪽 본문(그 쪽을 읽을 수 있을 때만 — `wiki.read` · `wiki` 모듈) |
| 사용자 정의 필드 | 값 |

<a id="pt-redmine-diff"></a>
### 5.5 Redmine 과 다른 점

| 항목 | Redmine | moai-works | 이유 |
|---|---|---|---|
| 진척 가중 대상 | 묶인 일감 전부(상위 일감은 하위 예상까지 더한 값으로) | 잎 일감만 | 상위와 하위가 같은 버전에 있으면 두 번 세는 것을 막는다 |
| 공유 `system` | 시스템 전체, 관리자만 | `WORKSPACE`, 워크스페이스 역할의 `version.manage` | 멀티 워크스페이스 |
| 공유 버전의 원래 프로젝트 | 이름이 보인다 | 읽을 수 없으면 "다른 프로젝트" | 존재 숨기기 원칙 |
| 닫을 때 열린 일감 | 아무 말 없음 | 경고 + 옮기기 안내 | 실수 줄이기 |

---

<a id="pt-gantt"></a>
## 6. 간트

### 6.1 화면

- 왼쪽: 줄 목록(프로젝트 → 버전 → 일감 트리) + 고른 열(제목 · 상태 · 우선순위 · 담당자 · 진척률 · 시작 · 기한 · 예상 — Redmine 4.1 의 "선택 열 보이기"). 오른쪽: 기간 머리(년 · 월 · 주 · 일)와 막대.
- 거르기는 일감 질의와 같다([질의 요구사항](../requirements/05-query.md)). 저장한 질의를 간트로 열 수 있다.
- 기간: 시작 월 + 개월 수(1~24, 기본 6). 이전 · 다음 이동. 확대 4단계(월 · 주 · 일 · 일+요일).
- 선택: 관계 화살표 그리기(기본 켬) · 진척선(progress line) 그리기(기본 끔) · 선택 열 보이기.
- 오늘 선(빨간 세로선). 쉬는 요일 칸은 옅게(05 의 `issue_tracking_setting.non_working_weekdays`, 기본 토 · 일).

### 6.2 줄과 막대

| 줄 | 막대 시작 | 막대 끝 | 진척 | 표시 |
|---|---|---|---|---|
| 프로젝트 | 안에 보이는 일감의 가장 이른 시작 | 가장 늦은 끝 | 없음 | 굵은 줄, 이름 |
| 버전 | 버전 시작일(§3.1) | 기한(없으면 가장 늦은 일감 끝) | 버전 완료율 | 다이아몬드 + "이름 62%" |
| 상위 일감 | 하위의 가장 이른 시작 | 하위의 가장 늦은 끝 | 롤업 진척률 | 양 끝 꺾쇠 막대 |
| 일감 | 시작일 | 기한 → 없으면 대상 버전 기한(Redmine `due_before`) | 진척률 | 막대 + "상태 진척%" |

| 경우 | 처리 |
|---|---|
| 시작일만 있고 끝이 없음 | 시작일 하루짜리 막대 |
| 끝만 있고 시작일이 없음 | 끝 하루짜리 막대 |
| 둘 다 없음 | 왼쪽 목록에만 나오고 막대는 없다 |
| 기간 밖 | 기간과 겹치는 일감만 줄에 넣는다. 막대는 기간 끝에서 잘리고 화살표 표시 |

진척 칠하기(Redmine 과 같다):

```
기간일수 = 끝 − 시작 + 1
완료점   = 시작 + 기간일수 × 진척률 / 100
완료 칸  = [시작, 완료점)                         → 초록
늦음 칸  = [완료점, min(오늘, 끝 + 1))  (오늘 > 완료점일 때) → 빨강
나머지   = 회색
닫힌 일감은 진척 100 으로 본다.
```

진척선: 일감마다 "완료점"을 이은 꺾은선. 오늘 선보다 왼쪽에 있으면 늦은 것이다.

### 6.3 관계 화살표

| 관계([일감 설계](05-issue-tracking.md)) | 화살표 |
|---|---|
| 선행(precedes) / 후행(follows) | 앞 일감 막대 끝 → 뒤 일감 막대 시작, 파란 선 |
| 막음(blocks) / 막힘(blocked by) | 막는 일감 → 막힌 일감, 빨간 선 |
| 관련 · 중복 · 복사 | 그리지 않는다 |

양쪽 일감이 모두 지금 줄 목록에 있을 때만 그린다. 다른 쪽 일감이 보이지 않으면(권한 · 거르기) 그리지 않는다.

### 6.4 데이터와 한도

- `GET …/projects/{project}/gantt?query=…&from=2026-11&months=6` → `{ range, rows: [{kind, id, parentId, depth, start, end, doneRatio, closed, label, columns}], relations: [{from, to, type, delay}] }`. 그림은 브라우저가 SVG 로 그린다.
- 한도: 줄 500개(설정 `gantt_max_items`, Redmine `gantt_items_limit` 와 같은 뜻). 넘으면 앞 500개만 주고 "더 있다 — 거르기를 좁혀라"를 보인다. 개월 수 상한 24(`gantt_max_months`, Redmine `gantt_months_limit`).
- 질의 한 번 + 관계 질의 한 번 + 버전 질의 한 번. 일감마다 질의하지 않는다.

<a id="pt-gantt-export"></a>
### 6.5 내보내기 (PDF · PNG)

| 형식 | 방식(제안) | 우선 |
|---|---|---|
| PNG | 브라우저가 그린 SVG 를 canvas 로 옮겨 내려받는다. 서버 일이 없다 | C |
| PDF | 첫 단계는 인쇄용 CSS(가로 · 쪽 나눔) + 브라우저 인쇄. 서버에서 만드는 PDF 는 나중 | C |

서버 렌더링(헤드리스 브라우저)을 둘지는 [PT-Q6](#pt-decisions). 내보낸 파일에도 보는 사람이 읽을 수 있는 것만 들어간다(같은 데이터에서 그리므로 자연히 그렇다).

<a id="pt-gantt-drag"></a>
### 6.6 날짜 끌어 바꾸기 (C)

Redmine 기본 기능은 아니다. 나중에 막대를 끌어 시작 · 기한을 바꾸게 하면, 일감 고치기 API(`lock_version` 필수)를 그대로 부르고 워크플로의 필드 권한(읽기 전용 필드)을 지킨다. 선행 관계가 있으면 뒤 일감 날짜 밀기는 [일감 설계](05-issue-tracking.md)의 규칙을 따른다.

---

<a id="pt-calendar"></a>
## 7. 캘린더

| 항목 | 규칙 |
|---|---|
| 보기 | **월**(기본) · **주**. 이전 · 다음 · 오늘 이동 |
| 나오는 것 | 일감 시작일(▶ "시작") · 일감 기한(◀ "기한") · 시작과 기한이 같은 날(◆) · 버전 기한(깃발, 버전 이름) |
| 거르기 | 일감 질의와 같다. 저장한 질의를 캘린더로 연다 |
| 주 시작 요일 | 사용자 설정 → 워크스페이스 설정 `issue_tracking_setting.start_of_week`(05, 기본 1 = 월요일). Redmine `start_of_week` 와 같은 뜻 |
| 칸 넘침 | 한 날짜에 10개를 넘으면 "+N 더 보기" |
| 일감 표시 | 트래커 · 번호 · 제목. 닫힌 일감은 줄 긋기. 마우스를 올리면 상태 · 담당 · 진척 |
| 한도 | 한 화면 1,000개. 넘으면 거르기를 좁히라고 알린다 |
| 데이터 | `GET …/calendar?query=…&from=2026-11-01&to=2026-12-06` → `{ items: [{kind: ISSUE_START·ISSUE_DUE·ISSUE_BOTH·VERSION_DUE, date, id, label, closed}] }` |

- 버전 기한은 그 프로젝트에 공유된 버전 중 보이는 것만 나온다.
- "내 페이지" 캘린더 블록은 같은 API 를 "내가 담당 · 관찰하는 일감"으로 부른다([공동 작업 설계](08-collaboration.md#co-mypage)).

---

<a id="pt-time"></a>
## 8. 시간 기록

### 8.1 필드

| 필드 | 규칙 | Redmine |
|---|---|---|
| 프로젝트 | 필수. 일감이 있으면 **일감의 프로젝트**로 정해진다(따로 고를 수 없다) | `project_id` |
| 일감 | 선택(워크스페이스 설정으로 필수로 만들 수 있다). 기록하는 사람이 읽을 수 있어야 한다 | `issue_id` |
| 누구의 시간 | 사람 멤버. 기본은 나(개인 MCP 키면 키 주인). 남을 고르려면 `time.edit.all`([§8.5](#pt-time-others)) | `user_id` |
| 기록한 사람 | 실제로 기록한 주체(사람 또는 Agent 계정) + 경로(웹 · REST · MCP · 메일 · 커밋) | `author_id`(4.1+) |
| 날짜 | 일한 날. 미래 날짜는 설정으로 막는다 | `spent_on` |
| 시간 | 0 초과 24 시간 이하(한 기록은 하루를 넘지 않는다). Redmine 에서 가져오는 옛 기록만 1000 시간 미만까지 받는다. 저장은 **정수 분**([§8.4](#pt-time-input)) | `hours`(실수, 1000 미만) |
| 작업 분류 | 필수. 그 프로젝트에서 켜진 분류([§8.6](#pt-activity-enum)) | `activity_id` |
| 메모 | 1024자. 일반 글(Markdown 아님) | `comments` |
| 사용자 정의 필드 | 대상이 "시간 기록"인 필드 | 시간 기록 사용자 정의 필드 |

<a id="pt-time-where"></a>
### 8.2 어디에 붙나

- **일감에**: 일감 쪽의 "시간 기록" · 일감 고치기 화면의 시간 칸(상태 바꾸기 · 메모와 한 트랜잭션, Redmine 과 같다) · MCP `log_time(issue=…)`.
- **프로젝트에**: 일감 없이 회의 · 지원 같은 시간. 프로젝트의 "소요 시간" 화면에서.
- **커밋 메시지로**: `refs #123 @2h` 처럼 커밋에 시간을 적으면 연동 쪽이 같은 시간 서비스를 불러 남긴다(경로 `SCM`, 작업 분류는 저장소 설정의 기본값, [연동 요구사항](../requirements/11-integration.md)).
- 일감을 다른 프로젝트로 옮기면 그 일감의 시간 기록도 **함께 옮긴다**(`project_id` 갱신, Redmine 과 같다). 옮긴 프로젝트에서 작업 분류가 꺼져 있어도 기록은 그대로 둔다(분류 이름은 보인다).
- 일감 소요 시간 = 그 일감 기록 합. 일감 합계 소요 = 자기 + 모든 하위 일감 기록 합(롤업). 둘 다 `time.read` 가 있을 때만 보인다.

<a id="pt-time-rules"></a>
### 8.3 기록 규칙 · 워크스페이스 설정

| 설정(열) | 기본 | 뜻 | Redmine |
|---|---|---|---|
| `time_required_fields` | `{}` | `ISSUE` · `COMMENT` 를 필수로 | `timelog_required_fields` |
| `time_accept_zero_hours` | `false` | 0 시간 기록 허용 | `timelog_accept_0_hours` |
| `time_accept_future_dates` | `true` | 미래 날짜 허용 | `timelog_accept_future_dates` |
| `time_max_minutes_per_day` | `0`(없음) | 한 사람이 하루에 기록할 수 있는 합계 상한 | `timelog_max_hours_per_day` |
| `time_accept_closed_issues` | `true` | 닫힌 일감에 기록 허용 | — |
| `time_display_format` | `DECIMAL` | `DECIMAL`(1.50) · `HOURS_MINUTES`(1:30). 사용자가 덮어쓸 수 있다 | `timespan_format` |
| `time_locked_before` | 없음 | 이 날짜 이전 기록은 만들기 · 고치기 · 지우기 금지(마감, C) | — (플러그인) |

설정은 [일감 설계](05-issue-tracking.md)의 워크스페이스 한 행 설정 표 `issue_tracking_setting` 에 열로 더한다([§11.2](#pt-ddl)). 바꾸기는 `config.manage`. 검사 순서: 형식 → 필수 → 0 · 미래 · 닫힌 일감 → 하루 상한 → 마감. 하루 상한은 `pg_advisory_xact_lock(그 사람, 그 날짜)` 를 잡고 기존 합 + 새 값을 다시 읽어 넘으면 422 다(동시에 두 건이 들어와도 넘지 않게).

<a id="pt-time-input"></a>
### 8.4 시간 입력 형식

사람이 치는 글을 분으로 바꾼다(Redmine `String#to_hours` 와 같은 형식 + 한국어).

| 입력 | 분 | 비고 |
|---|---|---|
| `1.5` · `1,5` | 90 | 소수점 · 쉼표 |
| `1:30` | 90 | 시:분 (분 0~59) |
| `1h30m` · `1h 30m` · `1h30` | 90 | |
| `2h` · `2` | 120 | |
| `45m` · `45min` | 45 | |
| `1시간 30분` · `1시간` · `30분` | 90 · 60 · 30 | 한국어 |
| `0.33` | 20 | 분으로 반올림 |
| `-1` · `abc` · `1:75` · `25` | 오류 | 422, "시간 형식이 맞지 않다" · "한 기록은 24 시간 이하" |

- 정규식(공백 제거 뒤): `^\d+([.,]\d+)?h?$` · `^\d+:[0-5]\d$` · `^(\d+)\s*(h|시간)?\s*((\d+)\s*(m|min|분))?$`.
- API · MCP 는 숫자 `hours`(소수) 또는 글 `duration`("1:30") 중 하나를 받는다. 저장은 `minutes`, 응답은 둘 다(`hours: 1.5`, `minutes: 90`).
- 분으로 저장하는 까닭: 합계가 정확하고(실수 오차 없음) 보고서 합이 칸 합과 늘 같다. Redmine 에서 옮겨 올 때 `hours × 60` 을 반올림한다(차이는 기록마다 30초 미만).

<a id="pt-time-others"></a>
### 8.5 남 대신 기록 · Agent 계정

| 경우 | 누구의 시간 | 기록한 사람 | 필요한 권한 |
|---|---|---|---|
| 내가 내 시간을 웹에서 | 나 | 나 | `time.log` |
| 내 개인 MCP 키로 Agent 가 | 나 | 나 (경로 MCP · 키 id) | `time.log` ∩ 엔드포인트 범위 |
| 관리자가 다른 사람 시간을 | 그 사람 | 관리자 | `time.edit.all` + **그 사람이 그 프로젝트에서 `time.log` 를 가짐** |
| 관리자가 개인 MCP 키로 남의 시간을(`log_time` 의 `user`) | 그 사람 | 관리자 (경로 MCP) | 위와 같음 ∩ 엔드포인트 범위 |
| Agent 계정(서비스 키)이 사람 시간을(`log_time` 의 `user`) | 그 사람 | Agent 계정 | Agent 계정에 `time.edit.all` + 그 사람의 `time.log` |
| Agent 계정 자신의 시간 | **받지 않는다** — `AGENT_TIME_NOT_ALLOWED` | — | [PT-Q3](#pt-decisions) |

- Redmine 4.1 은 남 대신 기록에 `log_time_for_other_users` 권한을 따로 둔다. moai-works 는 첫 출시에 `time.edit.all` 로 갈음한다([PT-Q2](#pt-decisions)).
- 남 대신 기록의 대상은 사람 멤버만이다.
- 목록 · 보고서에서 "누구의 시간"과 "기록한 사람"이 다르면 "관리자 Y 가 기록" · "Agent X 가 기록"(`Agent` 표)을 함께 보인다. 기록한 사람의 종류(사람 · Agent)로 거르고 묶을 수 있다.

<a id="pt-activity-enum"></a>
### 8.6 작업 분류

- 목록 자체(이름 · 순서 · 기본값 · 켜짐)는 워크스페이스 열거형이다([워크플로 요구사항](../requirements/04-workflow.md)). 예: 설계 · 개발 · 시험 · 회의 · 지원 · 문서.
- 프로젝트마다 **켜고 끄기만** 덮어쓴다(`project_time_activity`). 덮어쓴 행이 없으면 워크스페이스 값(켜짐)을 따른다. Redmine 은 프로젝트마다 자식 열거형 행을 만들지만, 여기서는 덮어쓰기 표 하나로 한다.
- 꺼진 분류로는 새 기록을 만들 수 없다. 이미 있는 기록은 그대로 보이고, 고칠 때 분류를 바꾸지 않으면 저장된다.
- 기본 분류: 워크스페이스 기본값이 그 프로젝트에서 켜져 있으면 그것, 아니면 비워 두고 고르게 한다.
- 열거형을 지울 때 기록이 있으면 다른 분류로 옮기라고 한다(04 의 규칙).

<a id="pt-time-issue"></a>
### 8.7 일감을 지울 때

시간 기록이 붙은 일감을 지우면 고르게 한다(Redmine 과 같다):

| 선택 | 동작 | 조건 |
|---|---|---|
| 시간 기록도 지우기 | 기록 삭제 | 지우는 사람이 그 기록들을 고칠 수 있음(`time.edit.all`, 또는 모두 자기 것이고 `time.edit.own`) |
| 프로젝트에 남기기 | `issue_id` 를 비우고 프로젝트 기록으로 | 늘 된다(기본) |
| 다른 일감으로 옮기기 | 고른 일감으로 `issue_id` · `project_id` 변경 | 대상 일감을 읽을 수 있고, 대상 프로젝트에서 위 고치기 권한 |

MCP 에는 일감 지우기가 없다(사람만 웹에서). 일감 삭제 흐름은 [일감 설계](05-issue-tracking.md)에 있고, 이 선택은 그 흐름의 한 단계다.

<a id="pt-time-list"></a>
### 8.8 목록

- 시간 기록 질의([질의 요구사항](../requirements/05-query.md)의 질의 엔진을 쓴다). 거르기: 날짜(오늘 · 어제 · 이번 주 · 지난 주 · 이번 달 · 지난 달 · 기간) · 누구의 시간 · 기록한 사람 · 작업 분류 · 프로젝트(하위 포함) · 일감(하위 포함, `~ #123`) · 일감의 트래커 · 상태 · 대상 버전 · 카테고리 · 메모 · 시간 · 사용자 정의 필드.
- 열: 날짜 · 누구 · 작업 분류 · 일감 · 메모 · 시간 · 프로젝트 · 기록한 사람 · 사용자 정의 필드. 묶기 · 합계(시간). CSV.
- 비공개 일감에 붙은 기록: 시간 수는 합계에 넣고, 그 일감을 볼 수 없는 사람에게는 일감 칸을 "비공개 일감"으로, 메모를 "(가림)"으로 보인다([PT-Q4](#pt-decisions)).

---

<a id="pt-report"></a>
## 9. 소요 시간 보고서

### 9.1 모양

```
             2026-09   2026-10   2026-11    합계
사용자 김가
  개발         12.00     30.50      8.00    50.50
  회의          2.00      4.00         —     6.00
사용자 이나
  개발             —     16.00     20.00    36.00
합계           14.00     50.50     28.00    92.50
```

- **행**: 기준을 1~3개 고르면 그 차례로 중첩한다. **열**: 기간 단위 `DAY` · `WEEK`(ISO 주, `2026-W47`) · `MONTH` · `YEAR`. 맨 오른쪽 · 맨 아래 합계.
- 거르기는 [시간 기록 목록](#pt-time)과 같다(같은 질의 객체). 보고서 설정(기준 · 기간 단위 · 거르기)은 질의로 저장할 수 있다.
- 시간 표시는 `time_display_format`(워크스페이스) · 사용자 설정을 따른다.

### 9.2 기준

| 기준 키 | 무엇으로 묶나 | 출처 |
|---|---|---|
| `project` | 프로젝트 | `time_entry.project_id` |
| `user` | 누구의 시간 | `time_entry.user_id` |
| `author` | 기록한 사람(사람 · Agent) | `time_entry.author_*` |
| `activity` | 작업 분류 | `time_entry.activity_id` |
| `issue` | 일감 | `time_entry.issue_id` (없으면 "일감 없음") |
| `tracker` · `status` · `version` · `category` | 일감의 **지금** 트래커 · 상태 · 대상 버전 · 카테고리 | `issue` 조인(Redmine 과 같이 기록 때 값이 아니라 지금 값) |
| `cf_<id>` | 사용자 정의 필드 — 일감 · 프로젝트 · 버전 · 시간 기록 · 사용자 필드 중 형식이 목록 · 키-값 목록 · 사용자 · 버전 · 예/아니요 · 문자인 것 | 각 엔티티 `custom_values ->> '<key>'` |

여러 값을 갖는 필드(다중 목록)는 값마다 한 번씩 센다(행 합이 전체 합보다 클 수 있음을 화면에 적는다 — Redmine 과 같다).

### 9.3 계산

```sql
SELECT <기준 열들>, date_trunc(:unit, te.spent_on) AS period, sum(te.minutes) AS minutes
  FROM time_entry te LEFT JOIN issue i ON i.id = te.issue_id
 WHERE te.workspace_id = :ws
   AND te.project_id = ANY(:projects_with_time_read)   -- api 가 계산한 "time.read ∧ time_tracking 켜짐" 프로젝트
   AND <거르기>
 GROUP BY <기준 열들>, period;
-- 행 합 · 열 합 · 전체 합은 서버가 이 결과를 접어서 계산한다(ROLLUP 을 쓰지 않는다 — 다중 값 필드 때문에)
```

- 한도: 행 조합 2,000개 · 열 기간 366개. 넘으면 기간 단위를 키우라고 알린다.
- CSV: UTF-8(BOM), 구분자 쉼표(사용자 언어가 쉼표 소수점이면 세미콜론 — Redmine 과 같다), 첫 줄 머리, 마지막 줄 합계, 시간은 소수 둘째 자리.
- 색인: `time_entry (workspace_id, project_id, spent_on)` · `(workspace_id, user_id, spent_on)` · `(issue_id)`.

<a id="pt-estimate"></a>
### 9.4 예상 대 실적

| 값 | 어디에 | 계산 |
|---|---|---|
| 일감 예상 · 합계 예상 | 일감 쪽 · 목록 열 | [일감 설계](05-issue-tracking.md)(하위 롤업) |
| 일감 소요 · 합계 소요 | 일감 쪽 · 목록 열 | §8.2 |
| 남은 예상 | 일감 목록 열 · 버전 쪽 | `예상 × (100 − 진척률) / 100` (Redmine 5.1 `estimated_remaining_hours` 와 같다) |
| 버전 예상 · 소요 | 버전 쪽 · 로드맵 | §5.4 |
| 프로젝트 소요 | 프로젝트 개요 | 그 프로젝트(+ 하위) 기록 합, `time.read` 일 때 |

---

<a id="pt-events"></a>
## 10. 이벤트

이벤트는 `대상.동작` 꼴이고, 쓰는 트랜잭션 안에서 작업 큐에 들어간다([아키텍처](01-architecture.md)). 봉투(이벤트 id · 워크스페이스 · 프로젝트 · 행위자 종류와 id · 경로 · 시각 · 바뀐 필드)는 [일감 설계](05-issue-tracking.md)의 이벤트 목록과 맞춘다.

**내는 이벤트**

| 이벤트 | 언제 | 받는 곳 |
|---|---|---|
| `version.created` | 버전 만들기 | 활동(C) · 웹훅 |
| `version.updated` | 고치기 · 상태 · 공유 범위 바꾸기(바뀐 필드 포함) | 활동(C) · 웹훅 · 공유 범위 축소면 §4.4 |
| `version.deleted` | 지우기 | 웹훅 |
| `time_entry.created` | 시간 기록 | 활동(종류 "소요 시간") · 웹훅 |
| `time_entry.updated` | 고치기(옮기기 포함) | 웹훅 |
| `time_entry.deleted` | 지우기 | 활동에서 그 항목 지움 · 웹훅 |

- 시간 기록 · 버전은 **메일 알림을 보내지 않는다**(Redmine 과 같다). 화면 알림도 없다.
- 시간 기록은 검색 색인에 넣지 않는다. 버전은 이름으로 찾을 일이 적어 색인하지 않는다(C).

**받는 이벤트**

| 이벤트 | 하는 일 |
|---|---|
| `issue.updated`(`projectChanged`) | 할 일 없음 — 시간 기록 `project_id` 는 일감 서비스가 옮기기 트랜잭션 안에서 시간 서비스를 직접 불러 이미 옮겼다([일감 설계](05-issue-tracking.md) §17.3) |
| `issue.deleted` | §8.7 의 선택은 삭제 트랜잭션 안에서 이미 끝난다. 이벤트는 받지 않는다 |
| `project.updated`(상위가 바뀜) · `project.archived` · `project.deleted` | §4.4 버전 정리(지우면 버전 · 시간 기록이 프로젝트와 함께 지워진다) |

---

<a id="pt-tables"></a>
## 11. 테이블

### 11.1 목록

| 테이블 | 한 줄 | 정본 / 파생 |
|---|---|---|
| `version` | 버전 | 정본 |
| `time_entry` | 시간 기록 | 정본 |
| `project_time_activity` | 프로젝트별 작업 분류 켜고 끄기 | 정본 |
| (`project.default_version_id`) | 기본 버전 — 프로젝트 표에 열 하나 | 정본. 프로젝트 표는 [프로젝트 권한 설계](04-project-permissions.md) |
| (`issue.fixed_version_id`) | 대상 버전 — 일감 표에 열 하나 | 정본. 일감 표는 [일감 설계](05-issue-tracking.md) |

<a id="pt-ddl"></a>
### 11.2 SQL 초안

토대 스키마(V1)의 표기를 따른다: uuid id · `timestamptz` · 상태 값 대문자 · 낙관적 잠금 `row_version`. 프로젝트 참조는 04 와 같이 `(workspace_id, project_id)` 복합 FK 로 워크스페이스를 넘는 참조를 DB 가 막는다. 주체 열(`*_type` + `*_id`)과 경로 열(`via`)의 값 목록은 [일감 설계](05-issue-tracking.md)의 저널 표기와 맞춘다. 참조하는 `project` · `issue` · `enumeration`(작업 분류 열거형) 표 이름은 04 · 05 를 따른다.

```sql
-- ── 버전 ──────────────────────────────────────────────────────────────
CREATE TABLE version (
    id               uuid          PRIMARY KEY,
    workspace_id     uuid          NOT NULL REFERENCES workspace (id),
    project_id       uuid          NOT NULL,
    name             varchar(60)   NOT NULL,
    description      varchar(1000),
    status           varchar(10)   NOT NULL DEFAULT 'OPEN'
                         CHECK (status IN ('OPEN', 'LOCKED', 'CLOSED')),
    sharing          varchar(20)   NOT NULL DEFAULT 'NONE'
                         CHECK (sharing IN ('NONE', 'DESCENDANTS', 'HIERARCHY', 'TREE', 'WORKSPACE')),
    due_date         date,                                   -- Redmine effective_date
    wiki_page_title  varchar(255),                           -- 같은 프로젝트 위키의 쪽 이름 (없어도 된다)
    custom_values    jsonb         NOT NULL DEFAULT '{}',
    created_by_type  varchar(10)   NOT NULL CHECK (created_by_type IN ('USER', 'AGENT', 'SYSTEM')),
    created_by_id    uuid,
    created_at       timestamptz   NOT NULL,
    updated_at       timestamptz   NOT NULL,
    row_version      integer       NOT NULL DEFAULT 0,
    UNIQUE (workspace_id, id),                                -- 복합 FK 대상 (issue.fixed_version_id 등)
    FOREIGN KEY (workspace_id, project_id) REFERENCES project (workspace_id, id) ON DELETE CASCADE
);
CREATE UNIQUE INDEX ux_version_name ON version (project_id, lower(name));
CREATE INDEX ix_version_project ON version (project_id, status, due_date);
CREATE INDEX ix_version_shared ON version (workspace_id, sharing) WHERE sharing <> 'NONE';
CREATE INDEX ix_version_cf ON version USING gin (custom_values jsonb_path_ops);

-- 프로젝트 기본 버전 (프로젝트 표는 04 — "version 표와 함께 더한다"고 적어 두었다)
ALTER TABLE project ADD COLUMN default_version_id uuid REFERENCES version (id) ON DELETE SET NULL;
-- 일감 대상 버전 (일감 표는 05 — 열과 색인 ix_issue_fixed_version 은 이미 있다). version 표를 만들 때 FK 를 건다.
--   지우기 막기는 RESTRICT 로 (§3.3)
ALTER TABLE issue ADD CONSTRAINT fk_issue_fixed_version
    FOREIGN KEY (workspace_id, fixed_version_id) REFERENCES version (workspace_id, id) ON DELETE RESTRICT;

-- ── 시간 기록 ──────────────────────────────────────────────────────────
CREATE TABLE time_entry (
    id              uuid          PRIMARY KEY,
    workspace_id    uuid          NOT NULL REFERENCES workspace (id),
    project_id      uuid          NOT NULL,
    issue_id        uuid,
    user_id         uuid          NOT NULL REFERENCES user_profile (id),      -- 누구의 시간 (사람만, PT-Q3)
    author_type     varchar(10)   NOT NULL CHECK (author_type IN ('USER', 'AGENT', 'SYSTEM')),
    author_id       uuid,                                                      -- 기록한 사람 · Agent 계정
    via             varchar(10)   NOT NULL CHECK (via IN ('WEB', 'REST', 'MCP', 'EMAIL', 'SCM', 'IMPORT', 'SYSTEM')),
    mcp_endpoint_id uuid,                                                      -- via = MCP 일 때
    mcp_key_id      uuid,
    spent_on        date          NOT NULL,
    minutes         integer       NOT NULL CHECK (minutes >= 0 AND minutes < 60000),  -- 서비스가 24시간(1440분) 이하로 막는다. 가져오기만 1000 시간 미만
    activity_id     uuid          NOT NULL,                                   -- 작업 분류 (enumeration kind = TIME_ACTIVITY)
    comment         varchar(1024),
    custom_values   jsonb         NOT NULL DEFAULT '{}',
    created_at      timestamptz   NOT NULL,
    updated_at      timestamptz   NOT NULL,
    row_version     integer       NOT NULL DEFAULT 0,
    FOREIGN KEY (workspace_id, project_id)  REFERENCES project (workspace_id, id) ON DELETE CASCADE,
    FOREIGN KEY (workspace_id, issue_id)    REFERENCES issue (workspace_id, id),          -- 지울 때는 §8.7 로 먼저 정리
    FOREIGN KEY (workspace_id, activity_id) REFERENCES enumeration (workspace_id, id)     -- 쓰는 분류는 못 지운다
);
CREATE INDEX ix_time_entry_project ON time_entry (workspace_id, project_id, spent_on);
CREATE INDEX ix_time_entry_user ON time_entry (workspace_id, user_id, spent_on);
CREATE INDEX ix_time_entry_issue ON time_entry (issue_id) WHERE issue_id IS NOT NULL;
CREATE INDEX ix_time_entry_cf ON time_entry USING gin (custom_values jsonb_path_ops);

-- ── 프로젝트별 작업 분류 켜고 끄기 (행이 없으면 워크스페이스 값) ─────────────
CREATE TABLE project_time_activity (
    workspace_id  uuid        NOT NULL,
    project_id    uuid        NOT NULL,
    activity_id   uuid        NOT NULL,
    active        boolean     NOT NULL,
    updated_at    timestamptz NOT NULL,
    PRIMARY KEY (project_id, activity_id),
    FOREIGN KEY (workspace_id, project_id)  REFERENCES project (workspace_id, id) ON DELETE CASCADE,
    FOREIGN KEY (workspace_id, activity_id) REFERENCES enumeration (workspace_id, id) ON DELETE CASCADE
);

-- ── 워크스페이스 설정 (05 의 issue_tracking_setting 에 더하는 열) ───────────────
ALTER TABLE issue_tracking_setting
    ADD COLUMN time_required_fields       text[]       NOT NULL DEFAULT '{}',        -- ISSUE · COMMENT
    ADD COLUMN time_accept_zero_hours     boolean      NOT NULL DEFAULT false,
    ADD COLUMN time_accept_future_dates   boolean      NOT NULL DEFAULT true,
    ADD COLUMN time_max_minutes_per_day   integer      NOT NULL DEFAULT 0 CHECK (time_max_minutes_per_day >= 0),
    ADD COLUMN time_accept_closed_issues  boolean      NOT NULL DEFAULT true,
    ADD COLUMN time_display_format        varchar(15)  NOT NULL DEFAULT 'DECIMAL' CHECK (time_display_format IN ('DECIMAL', 'HOURS_MINUTES')),
    ADD COLUMN time_locked_before         date,
    ADD COLUMN gantt_max_items            integer      NOT NULL DEFAULT 500 CHECK (gantt_max_items BETWEEN 50 AND 5000),
    ADD COLUMN gantt_max_months           integer      NOT NULL DEFAULT 24  CHECK (gantt_max_months BETWEEN 1 AND 60);
```

- 시간 기록의 고치기 · 지우기는 감사(`audit_log`)에 전과 후 값을 남긴다. Redmine 은 시간 기록 이력을 두지 않지만, 여기서는 감사로 남긴다(따로 이력 표는 두지 않는다).
- 버전의 이름 · 기한 · 상태 변경도 감사에 남긴다.

---

<a id="pt-api"></a>
## 12. REST API

경로 앞부분 `/moai-works-api/v1/workspaces/{workspace}` 는 줄였다. 규약(오류 모양 · 페이지 · `If-Match`)은 [연동 설계](09-integrations.md).

| 메서드 · 경로 | 하는 일 | 권한 |
|---|---|---|
| `GET /projects/{project}/versions?status=&includeShared=` | 버전 목록(공유받은 것 포함) | `issue.read` |
| `POST /projects/{project}/versions` | 만들기 | `version.manage` |
| `GET /versions/{id}` | 버전 쪽(진척 · 시간 · 묶음) | 쓸 수 있는 프로젝트 하나의 독자 + `issue.read` |
| `PATCH /versions/{id}` · `DELETE /versions/{id}` | 고치기(상태 · 공유 포함) · 지우기 | 원래 프로젝트의 `version.manage`(+ §4.1) |
| `GET /versions/{id}/sharing-impact?sharing=` | 공유 범위를 바꾸면 비워질 일감 수 | `version.manage` |
| `GET /projects/{project}/roadmap?trackers=&completed=&subprojects=` | 로드맵 | `issue.read` |
| `GET /projects/{project}/gantt?…` · `GET /gantt?…`(워크스페이스 전체) | 간트 데이터 | `issue.read` + `gantt` |
| `GET /projects/{project}/calendar?…` · `GET /calendar?…` | 캘린더 데이터 | `issue.read` + `calendar` |
| `GET /projects/{project}/time-entries?…` · `GET /time-entries?…` | 시간 기록 목록(질의) · CSV | `time.read` |
| `POST /projects/{project}/time-entries` · `POST /issues/{number}/time-entries` | 기록 | `time.log`(남이면 `time.edit.all`) |
| `PATCH /time-entries/{id}` · `DELETE /time-entries/{id}` | 고치기 · 지우기 | `time.edit.own`(자기 것) · `time.edit.all` |
| `GET /projects/{project}/time-report?criteria=&period=&…` · `.csv` | 보고서 | `time.read` |
| `GET/PUT /projects/{project}/time-activities` | 프로젝트 작업 분류 켜고 끄기 | `project.manage` |

워크스페이스 전체 경로(`/gantt` · `/calendar` · `/time-entries`)는 `@WorkspaceApi(WORKSPACE_READ)` 로 들어오고, 서비스가 "읽을 수 있고 모듈이 켜진 프로젝트" 목록으로 거른다.

---

<a id="pt-mcp"></a>
## 13. MCP

도구 카탈로그 · 공통 안전장치(멱등 키 · 속도 제한 · 응답 크기 · 사용자 데이터 표시)는 [MCP · Agent 설계](07-mcp-agents.md)가 정본이다. 여기서는 이 영역 도구의 뜻만 적는다.

| 도구 | 입력(요지) | 출력(요지) | REST 짝 |
|---|---|---|---|
| `list_versions` | `project` · `status`(open/locked/closed, 여럿) · `include_shared` · `cursor` · `limit` | 버전마다 id · 이름 · 원래 프로젝트 · 상태 · 공유 범위 · 기한 · 닫힘 비율 · 완료율 · 일감 수 | `GET /projects/{p}/versions` |
| `get_roadmap` | `project` · `version`(선택) · `trackers` · `include_completed` · `include_subprojects` · `issues_per_version`(기본 20, 최대 100) | 버전 목록(§5.1 정렬) + 버전마다 닫힘 비율 · 완료율 · 늦음 · 트래커별 수 · 예상 / 소요 시간(소요는 `time.read` 일 때) · 남은 일감 목록(번호 · 제목 · 상태) | `GET /projects/{p}/roadmap` |
| `list_time_entries` | `project` 또는 `issue` · `from` · `to` · `user` · `activity` · `cursor` · `limit` | 기록 목록 + 합계 분 · 시간 | `GET /time-entries` |
| `log_time` | `issue`(번호) 또는 `project` · `hours`(0 초과 24 이하) 또는 `duration`("1:30") · `spent_on`(기본 오늘, 워크스페이스 시간대) · `activity`(이름 · id, 기본 분류) · `comment` · `user`(남 대신 기록할 사람, 선택 — `time.edit.all`) · `idempotency_key`(필수) | 만든 기록(`user` · `logged_by`) + 그 일감의 소요 합계 | `POST …/time-entries` |

- `log_time` 은 같은 검사(§8.3 규칙 · §8.5 권한)를 거친다. `user` 가 없으면 키 주인의 시간이다. 서비스 키(Agent 계정)는 `user` 로 사람을 정해 대신 기록할 때만 쓸 수 있고, `user` 없이 부르면 `AGENT_TIME_NOT_ALLOWED`(kind `FORBIDDEN`) 다(PT-Q3). 결과 기록에 "Agent X 가 기록"(`logged_by`)이 남는다. 입력 · 결과 표는 [MCP · Agent 설계](07-mcp-agents.md)의 `log_time` 절과 같다.
- 진척 · 합계는 키 주체(사람 또는 Agent 계정)가 읽을 수 있는 일감만으로 계산하고, 엔드포인트의 대상 프로젝트로 한 번 더 좁힌다.
- 리소스는 두지 않는다(로드맵은 도구로 충분하다).

---

<a id="pt-tests"></a>
## 14. 시험

| 시험 | 확인할 것 |
|---|---|
| 진척 계산 | §5.2 보기 표의 값(58.3 · 66.7). 예상이 하나도 없을 때 평균 1. 일감 0개 → 0. 열린 일감 없음 → 100. 상위 일감은 가중에서 빠진다 |
| 보는 사람 기준 | 비공개 일감이 있는 버전 — 볼 수 있는 사람과 없는 사람의 일감 수 · 진척이 다르다 |
| 상태 | 잠김 · 닫힘 버전에 새 일감 넣기 422, 이미 묶인 일감의 다른 필드 고치기는 된다 |
| 공유 범위 | 다섯 값마다 고르기 목록이 맞다. `TREE` · `HIERARCHY` 를 루트 권한 없이 → 403. `WORKSPACE` 를 프로젝트 관리자만으로 → 403 |
| 공유 축소 | 범위를 좁히면 바깥 프로젝트 일감의 대상 버전이 비고 저널이 남는다. 프로젝트를 옮겨도 같다 |
| 지우기 | 일감이 묶인 버전 지우기 409 |
| 간트 막대 | 시작 · 끝이 비었을 때 네 경우. 늦음 칸 계산. 관계 화살표는 양쪽이 보일 때만 |
| 시간 입력 | §8.4 표의 모든 줄 |
| 기록 규칙 | 필수 필드 · 0 시간 · 미래 · 닫힌 일감 · 하루 상한 · 마감 각각 켜고 끔 |
| 남 대신 | `time.edit.all` 없이 `user` 지정 → 403. 대상 사람이 `time.log` 가 없으면 422. 대상으로 Agent 계정 → 422. 서비스 키로 `user` 없이 → `AGENT_TIME_NOT_ALLOWED` |
| 일감 옮기기 · 지우기 | 시간 기록이 따라간다. 지울 때 세 선택 |
| 보고서 | 칸 합 = 행 합 = 전체 합(다중 값 필드 제외). ISO 주 경계(2026-12-31 → 2026-W53). CSV 모양 |
| 권한 | 독자 아님 404 · 코드 없음 403 · 모듈 꺼짐 404 — 화면 · REST · MCP 셋 다 같은 결과 |
| 감사 | 버전 · 시간 기록의 모든 쓰기에 행위자(사람 · Agent) · 경로가 남는다 |

---

<a id="pt-decisions"></a>
## 15. 정해야 할 것

| ID | 결정 | 제안 | 막는 것 |
|---|---|---|---|
| PT-Q1 | 프로젝트 역할 "보고자"에게 `time.log` 를 줄까 | **주지 않는다** — Redmine 기본(보고자는 시간 기록 없음)과 같게. [프로젝트 권한 설계](04-project-permissions.md)의 역할별 표가 같은 답으로 확정했다 | 역할 시드 |
| PT-Q2 | 남 대신 기록에 코드를 따로 둘까(`time.log.others`) | 첫 출시는 `time.edit.all` 로 갈음. 사용자 정의 프로젝트 역할이 생기면 나눈다 | FR-TIM-004 |
| PT-Q3 | Agent 계정 자신의 시간을 기록할까 | **받지 않는다.** 시간은 사람의 일이고, Agent 시간이 섞이면 예상 대 실적 · 버전 소요 합계가 흐려진다. Agent 는 사람 대신 기록하는 "기록한 사람"으로만. [MCP · Agent 설계](07-mcp-agents.md)의 `log_time` 표가 같다. [MCP 요구사항](../requirements/12-mcp.md) FR-MCP-020 의 "Agent 계정의 시간 기록 · 주체 자신의 시간만" 문구는 이 결정과 맞춰야 한다 | `log_time` |
| PT-Q4 | 비공개 일감에 붙은 시간 기록을 그 일감을 못 보는 사람에게 어떻게 보일까 | 시간 수는 합계에 넣고 일감 · 메모는 가린다 | 목록 · 보고서 |
| PT-Q5 | 진척 가중에서 상위 일감을 뺄까(Redmine 과 다름) | **뺀다**(잎만). 두 번 세기 방지 | 로드맵 |
| PT-Q6 | 간트 PDF · PNG 를 서버에서 만들까 | 첫 단계는 브라우저(SVG → PNG, 인쇄 CSS → PDF). 서버 렌더링은 수요가 있을 때 | FR-PLN-011 |
| PT-Q7 | 공유 버전의 원래 프로젝트를 못 읽는 사람에게 프로젝트 이름을 보일까 | "다른 프로젝트"로 가린다 | 버전 목록 |
| PT-Q8 | 열린 일감이 남은 버전을 닫을 때 막을까 | 막지 않고 경고 + 옮기기 안내 | 버전 고치기 |
| PT-Q9 | 캘린더 · 주 보고서의 주 시작 요일 기본값 | 월요일(ISO 주와 맞춘다) — 05 의 `issue_tracking_setting.start_of_week` 기본값 1 과 같다. 사용자가 일요일로 바꿀 수 있다 | 캘린더 |

---

**관련 문서:** [버전 · 로드맵 · 간트 · 캘린더 요구사항](../requirements/06-planning.md) · [시간 기록 요구사항](../requirements/07-time.md) · [일감 설계](05-issue-tracking.md) · [프로젝트 권한 설계](04-project-permissions.md) · [MCP · Agent 설계](07-mcp-agents.md) · [공동 작업 설계](08-collaboration.md) · [데이터 모델](02-data-model.md)

[← 이전: 일감 추적 — 모델 · 번호 · 이력 · 관계 · 워크플로 · 사용자 정의 필드 · 질의 · 보드](05-issue-tracking.md) | [다음: MCP · AI Agent 설계 →](07-mcp-agents.md)
