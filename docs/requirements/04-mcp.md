[홈](../README.md) › [요구사항](00-overview.md) › MCP 요구사항

# MCP 요구사항 (FR-MCP)

바깥 AI Agent(Claude Code 등)가 **MCP** 로 붙어 사람과 같은 규칙으로 프로젝트 · 일감을 다루게 하는 요구사항이다. moai-works 는 AI 를 내장하지 않는다. LLM 은 바깥에 있고, moai-works 는 **키** · **워크스페이스 엔드포인트** · **판정** · **도구** · **안전장치** · **기록**을 맡는다. MCP 체계는 moai-wiki 와 같다 — 엔드포인트의 대상이 위키 대신 프로젝트일 뿐이다. 설계는 [MCP 설계](../design/06-mcp.md)에 있다.

| 우선순위 | 개수 | 요구사항 |
|---|---|---|
| M | 7 | [001](#fr-mcp-001) · [002](#fr-mcp-002) · [003](#fr-mcp-003) · [004](#fr-mcp-004) · [005](#fr-mcp-005) · [007](#fr-mcp-007) · [008](#fr-mcp-008) |
| S | 1 | [006](#fr-mcp-006) |
| 합계 | 8 | |

| 공통 | 내용 |
|---|---|
| 키 | 개인 키(`PERSONAL` — 키 주인의 권한 ∩ 엔드포인트) · 서비스 키(`SERVICE` — 워크스페이스 이름으로 일한다). SHA-256 만 저장, 기간, 폐기. 키는 엔드포인트 하나에 묶인다 |
| 입구 | 워크스페이스 MCP 엔드포인트 `/mcp/{ws}/{ep}`(Streamable HTTP). 종류 `QUERY`(읽기) · `AGENT`(읽기 + 쓰기), 대상 프로젝트, 켠 도구, 상태, 속도 제한 |
| 같은 서비스 · 같은 판정 | 도구는 REST 핸들러와 같은 업무 서비스 · 같은 판정([FR-PRJ-006](02-project.md#fr-prj-006))을 부르는 얇은 어댑터다. MCP 전용 판정을 만들지 않는다. 범위는 요청 문맥(키 · 엔드포인트)에서만 오고, 모델이 넘긴 프로젝트 · 일감 · 사람은 호출마다 다시 판정한다 |
| 오류 | 도구 오류의 `code` 는 같은 일을 REST 로 했을 때와 같다(`PROJECT_NOT_FOUND` · `ISSUE_NOT_FOUND` · `PERMISSION_REQUIRED` · `CONCURRENT_UPDATE` · 검증 코드) |
| MCP 로 하지 않는 일 | 일감 지우기 · 프로젝트 만들기 · 설정 · 보관 · 삭제 · 프로젝트 역할 · 워크스페이스 · 멤버 · 역할 · 키 · 엔드포인트 관리. 사람만 웹에서 한다 |
| 폐기한 것 | "Agent 계정"(사람이 아닌 멤버로 Agent 를 두는 안)은 moai-wiki 에 없는 것이라 뺐다(2026-11-22). 서비스 키는 워크스페이스 이름으로 일한다 |
| 지금 코드 | 고정 입구 `/mcp/project`(골격 도구 `list_projects` 하나)와 키 발급 API `/moai-works-api/v1/mcp-keys`(`@AuthenticatedApi`)가 있다. 키가 워크스페이스에 묶이지 않고 권한 검사 · 감사가 없다(moai-wiki 와 같은 상태) — [FR-MCP-001](#fr-mcp-001) · [FR-MCP-002](#fr-mcp-002)에서 고친다 |

**바로가기:** [키와 엔드포인트](#1-키와-엔드포인트) · [판정](#2-판정) · [도구](#3-도구) · [기록 · 안전장치 · 연결 안내](#4-기록--안전장치--연결-안내)

---

## 1. 키와 엔드포인트

<a id="fr-mcp-001"></a>
### FR-MCP-001 MCP 키 (워크스페이스에 묶기 · 권한 · 감사)

| 항목 | 내용 |
|---|---|
| 우선순위 | M |
| 진행 | 할 일 |
| 관련 설계 | [MCP 설계](../design/06-mcp.md) · [워크스페이스 설계 §11](../design/03-workspace-identity.md#ws-mcp) |

**설명** — 바깥 Agent 는 MCP 키로 들어온다. 키는 워크스페이스의 엔드포인트 하나에 묶이고, 발급 · 폐기는 권한 코드로 판정하고 감사에 남긴다. 지금 코드의 키 발급 API 는 워크스페이스에 묶이지 않고 권한 · 감사가 없으므로 이 요구사항으로 고친다.

**세부 요구사항**
1. 키는 `POST /workspaces/{workspace}/mcp-endpoints/{endpoint}/keys` 로 발급해야 한다. 개인 키는 `mcp.key.issue.personal`, 서비스 키는 `mcp.key.issue.service` 가 있어야 한다. 없으면 403 `PERMISSION_REQUIRED`, 워크스페이스 멤버가 아니면 404 `WORKSPACE_NOT_FOUND` 다.
2. 종류는 개인(`PERSONAL` — 키 주인으로 일한다)과 서비스(`SERVICE` — 사람이 아니라 워크스페이스 이름으로 일한다) 둘이다.
3. 키 원문(`mwk_mcp_` + 32바이트 난수)은 발급 응답에 한 번만 나가야 한다. DB 에는 SHA-256 과 표시용 앞부분만 둔다.
4. 이름과 유효 기간(기본 90일, 최대 365일 — 플랫폼 설정)을 정한다. 범위 밖이면 400 이다.
5. 키는 그 엔드포인트에만 쓸 수 있어야 한다. 다른 엔드포인트에 쓰면 403 `MCP_KEY_ENDPOINT_DENIED` 다.
6. 요청마다 캐시 없이 검사해야 한다: 모양 → SHA-256 으로 행 찾기 → 폐기 → 만료 → 경로의 엔드포인트 = 키의 엔드포인트 → 엔드포인트 `ACTIVE` → 워크스페이스 활성 → 개인 키면 키 주인이 활성 멤버. 실패는 401(`MCP_KEY_REQUIRED` · `MCP_KEY_INVALID` · `MCP_KEY_REVOKED` · `MCP_KEY_EXPIRED`, `WWW-Authenticate: Bearer`) 또는 403(`MCP_KEY_ENDPOINT_DENIED` · `MCP_ENDPOINT_DISABLED` · `MCP_PRINCIPAL_INACTIVE`)이다.
7. 키 목록은 이름 · 앞부분 · 종류 · 엔드포인트 · 만든 사람 · 만든 때 · 만료 · 마지막 사용 시각을 준다. 누구나 자기 키를 보고 폐기한다. `mcp.key.manage` 가 있으면 워크스페이스의 모든 키를 보고 폐기한다. 마지막 사용 시각은 1분에 한 번만 쓴다.
8. 멤버가 나가거나 제거되면 그 워크스페이스의 그 사람 개인 키는 바로 무효여야 하고, 정리 작업이 폐기 표시(`MEMBER_LEFT`)를 한다([FR-WSP-013](01-workspace.md#fr-wsp-013)).
9. 엔드포인트로 옮기면 지금의 `/moai-works-api/v1/mcp-keys` 와 고정 입구 `/mcp/project` 를 없앤다.
10. 발급(`mcp.key.issue` — 종류 · 엔드포인트 · 이름 · 만료) · 폐기(`mcp.key.revoke` — 폐기한 사람 · 이유)를 감사에 남긴다. 원문은 남기지 않는다.

**수용 기준**
- [ ] 키 원문이 DB · 로그 · 감사 어디에도 없다
- [ ] 기본 역할 멤버는 개인 키를 받고, 서비스 키를 받으려 하면 403 이다. 워크스페이스 멤버가 아니면 404 다
- [ ] 다른 엔드포인트 주소로 부르면 403 `MCP_KEY_ENDPOINT_DENIED` 다
- [ ] 폐기한 키의 바로 다음 요청은 401 `MCP_KEY_REVOKED`, 기간이 지난 키는 401 `MCP_KEY_EXPIRED` 다
- [ ] 멤버를 제거한 직후 그 사람의 개인 키는 403 `MCP_PRINCIPAL_INACTIVE` 다
- [ ] `mcp.key.manage` 없이 남의 키를 폐기하면 403 이다
- [ ] 발급 · 폐기가 감사에 남는다
- [ ] 옛 경로 `/moai-works-api/v1/mcp-keys` 와 `/mcp/project` 가 없다

<a id="fr-mcp-002"></a>
### FR-MCP-002 워크스페이스 MCP 엔드포인트

| 항목 | 내용 |
|---|---|
| 우선순위 | M |
| 진행 | 할 일 |
| 관련 설계 | [MCP 설계](../design/06-mcp.md) · [워크스페이스 설계 §11](../design/03-workspace-identity.md#ws-mcp) |

**설명** — `mcp.endpoint.manage` 권한자는 워크스페이스에 MCP 엔드포인트를 여러 개 만든다. 엔드포인트는 바깥 Agent 가 붙는 입구이고, **어떤 프로젝트를 · 어떤 도구로 · 얼마나 자주** 쓸 수 있는지의 상한을 정한다. 권한을 더 주지는 않고 좁히기만 한다.

**세부 요구사항**
1. 주소는 `/mcp/{워크스페이스 주소}/{엔드포인트 주소}` 여야 한다. 엔드포인트 주소는 프로젝트 key 와 같은 규칙(2~32자)이고 워크스페이스 안에서 고유하며 만든 뒤 바꾸지 않는다.
2. 관리 API 는 `GET · POST /workspaces/{workspace}/mcp-endpoints` · `PATCH · DELETE …/{endpoint}` 다. 만들기 · 고치기 · 지우기는 `@WorkspaceApi(mcp.endpoint.manage)`, 목록 보기는 `@WorkspaceApi(workspace.read)`(키를 받으려면 엔드포인트를 골라야 한다)다.
3. 설정은 다음과 같아야 한다.

   | 설정 | 값 |
   |---|---|
   | 이름 · 설명 | 화면 표시용 |
   | 종류 | `QUERY`(읽기 도구만) · `AGENT`(읽기 + 쓰기 도구) |
   | 대상 프로젝트 | 이 워크스페이스의 `ACTIVE` 프로젝트 하나 이상 |
   | 도구 | 종류가 허용하는 도구에서 골라 켠다(기본 전부). `QUERY` 에서는 쓰기 도구를 켤 수 없다 |
   | 서비스 키 읽기 방식 | `USER`(기본) · `SERVICE` — [FR-MCP-003](#fr-mcp-003). `SERVICE` 면 `RESTRICTED` 프로젝트를 대상으로 넣을 수 없다(400 `RESTRICTED_PROJECT_NOT_ALLOWED`) |
   | 속도 제한 | 분당 · 일 호출 수 (플랫폼 상한 안) |
   | 상태 | `ACTIVE` · `DISABLED` |

4. `tools/list` 에는 켠 도구만 보여야 하고, 켜지 않은 도구를 부르면 없는 도구로 거절해야 한다.
5. 엔드포인트마다 MCP 서버 하나를 만들어 캐시한다. 설정을 바꾸거나 끄면 모든 api 인스턴스에서 다음 요청부터 반영돼야 한다(변경 이벤트로 캐시를 지운다).
6. `DISABLED` 엔드포인트는 키가 유효해도 403 `MCP_ENDPOINT_DISABLED` 로 거절하고 키는 지우지 않는다. 엔드포인트를 지우면 거기 묶인 키를 모두 폐기한다.
7. 대상 프로젝트를 보관하면 보관한 동안 도구에서 보이지 않고, 지우면 대상에서 빠진다. 대상이 하나도 남지 않으면 `DISABLED` 로 바꾼다([FR-PRJ-005](02-project.md#fr-prj-005)).
8. 초기화 응답의 안내문(`instructions`)에 이 엔드포인트의 대상 프로젝트 · 쓰기 가능 여부 · 규칙(`rowVersion` · 멱등 키 · 하지 않는 일)을 담는다.
9. 만들기 화면은 "이 엔드포인트로 나간 데이터는 키를 가진 쪽의 LLM 으로 간다"를 알리고 확인을 받는다. `SERVICE` 읽기 방식을 고르면 "사람 권한과 상관없이 읽는다"는 경고를 보인다.
10. 만들기 · 고치기 · 지우기를 감사(`mcp.endpoint.create` · `mcp.endpoint.update` · `mcp.endpoint.delete` — 전 · 후)에 남긴다.

**수용 기준**
- [ ] 대상 프로젝트가 다른 두 엔드포인트에 같은 사람의 키로 `search_issues` 를 부르면 결과가 다르다
- [ ] 켜지 않은 도구는 `tools/list` 에 없고 불러도 실행되지 않는다. `QUERY` 엔드포인트에는 쓰기 도구가 없다
- [ ] `DISABLED` 엔드포인트의 호출은 유효한 키로도 403 `MCP_ENDPOINT_DISABLED` 다
- [ ] 설정을 바꾼 직후 다른 api 인스턴스로 간 요청에도 새 설정이 적용된다
- [ ] `SERVICE` 읽기 방식 엔드포인트에 `RESTRICTED` 프로젝트를 넣으면 400 이다
- [ ] `mcp.endpoint.manage` 가 없는 멤버의 만들기 · 고치기는 403, 멤버가 아니면 404 다
- [ ] 만들기 · 고치기 · 지우기가 감사에 남는다

---

## 2. 판정

<a id="fr-mcp-003"></a>
### FR-MCP-003 MCP 판정

| 항목 | 내용 |
|---|---|
| 우선순위 | M |
| 진행 | 할 일 |
| 관련 설계 | [MCP 설계](../design/06-mcp.md) · [프로젝트 설계](../design/04-project.md) |

**설명** — 도구가 무엇을 볼 수 있고 할 수 있는지는 키 · 엔드포인트가 정한 범위와 사람 판정의 교집합이다. 모델이 넘긴 값은 믿지 않고 호출마다 다시 판정한다.

**세부 요구사항**
1. 범위(워크스페이스 · 대상 프로젝트 · 켠 도구 · 종류 · 읽기 방식)는 요청 문맥(키 → 엔드포인트)에서만 와야 한다. 도구 입력의 프로젝트 key · 일감 번호 · 사람 id 는 매번 다시 판정한다.
2. 실제 권한은 다음과 같아야 한다.

   | 키 | 실제 권한 |
   |---|---|
   | 개인 키 | 키 주인의 프로젝트 권한([FR-PRJ-006](02-project.md#fr-prj-006) — 워크스페이스 역할 ∪ 프로젝트 역할) ∩ 대상 프로젝트 ∩ 켠 도구 |
   | 서비스 키, 읽기 방식 `USER` | 사람이 함께 와야 한다(대리 호출 — 사람 토큰을 넘기는 방식은 moai-wiki 와 함께 정한다). 결과는 그 사람 ∩ 엔드포인트. 서비스 키만 오면 401 `MCP_PERSON_REQUIRED` 다 |
   | 서비스 키, 읽기 방식 `SERVICE` | 사람 없이 엔드포인트 범위(대상 프로젝트 ∩ 켠 도구) 그대로. `RESTRICTED` 프로젝트는 대상이 될 수 없다. 쓰기 주체는 서비스(워크스페이스)다 |

3. 도구는 REST 와 같은 서비스(`ProjectAccessService` · 일감 서비스 · 질의 서비스)를 불러야 한다. 호출마다 다시 판정한다 — 역할 · 정지 · 공개 범위 변경이 다음 호출부터 반영된다.
4. 대상 밖 · 볼 수 없는 · 없는 프로젝트는 모두 `PROJECT_NOT_FOUND` 로 같게 답해야 한다. 일감은 `ISSUE_NOT_FOUND` 다.
5. MCP 도구는 `ACTIVE` 프로젝트만 다룬다. 보관한 프로젝트는 관리 권한이 있어도 `PROJECT_NOT_FOUND` 다.
6. 워크스페이스가 정지되면 모든 호출을 거절한다.

**수용 기준**
- [ ] 개인 키의 `search_issues` · `list_projects` 결과에 키 주인이 독자가 아닌 프로젝트가 없다(대상에 넣었어도)
- [ ] 모델이 대상 밖 프로젝트 key 를 넘기면 `PROJECT_NOT_FOUND` 이고 없는 key 와 답이 같다
- [ ] 키 주인의 프로젝트 역할을 빼면 다음 호출부터 거절된다
- [ ] 기본 역할 멤버의 개인 키로 `AGENT` 엔드포인트의 `create_issue` 를 부르면 `PERMISSION_REQUIRED` 다
- [ ] `USER` 읽기 방식 엔드포인트에 서비스 키만으로 부르면 401 `MCP_PERSON_REQUIRED` 다
- [ ] 같은 일을 REST 와 MCP 로 하면 결과와 오류 `code` 가 같다(일감 만들기 · 고치기 · 댓글 · 연결 · 상위 바꾸기 · 찾기 · 보기를 매개변수 시험으로)

---

## 3. 도구

<a id="fr-mcp-004"></a>
### FR-MCP-004 읽기 도구

| 항목 | 내용 |
|---|---|
| 우선순위 | M |
| 진행 | 할 일 |
| 관련 설계 | [MCP 설계](../design/06-mcp.md) |

**설명** — Agent 가 프로젝트와 일감을 읽는 도구다. `QUERY` · `AGENT` 엔드포인트 모두에서 켤 수 있다.

**세부 요구사항**
1. 첫 출시의 읽기 도구는 다음 5개여야 한다.

   | 도구 | 입력 | 결과 |
   |---|---|---|
   | `list_projects` | — | 대상 프로젝트 가운데 볼 수 있는 `ACTIVE` 프로젝트(key · 이름 · 설명) |
   | `get_project` | 프로젝트 key | 프로젝트 상세와 이 엔드포인트에서의 실제 권한(무엇을 할 수 있는지) |
   | `list_issue_types` | 프로젝트 key | 일감 유형(key · 이름 · 설명) |
   | `search_issues` | 프로젝트 key, [FR-ISS-012](03-issue.md#fr-iss-012)의 필터 · 정렬, 쪽 | 일감 요약 줄과 다음 쪽 커서 |
   | `get_issue` | 프로젝트 key, 번호 | [FR-ISS-002](03-issue.md#fr-iss-002)의 상세(하위 · 연결 · 이력 포함, 이력은 최근 것부터 나눠 줌) |

2. `search_issues` 는 REST 목록과 같은 질의 서비스를 써야 한다. 한 번에 최대 50건이다.
3. 결과는 구조화한 JSON 과 사람이 읽을 글을 함께 준다.
4. 결과의 사람이 쓴 글(제목 · 설명 · 댓글)은 "사용자 데이터"로 감싸 보내야 한다. Agent 가 그 안의 문장을 지시로 오해하지 않게 한다(프롬프트 주입 대비).
5. 도구 설명 · 입력 설명은 한국어 · 영어 리소스로 둔다([NFR-I18N-001](05-non-functional.md#nfr-i18n-001)). 엔드포인트의 언어를 따른다.

**수용 기준**
- [ ] `list_projects` 가 대상 ∩ 볼 수 있음 ∩ `ACTIVE` 프로젝트만 준다
- [ ] `search_issues` 가 같은 필터의 REST 목록과 같은 결과를 준다
- [ ] `get_issue` 가 하위 · 연결 · 이력을 준다
- [ ] 결과의 제목 · 설명 · 댓글이 사용자 데이터 표시 안에 있다
- [ ] 다섯 도구가 `QUERY` 엔드포인트와 `AGENT` 엔드포인트 모두에서 동작한다

<a id="fr-mcp-005"></a>
### FR-MCP-005 쓰기 도구

| 항목 | 내용 |
|---|---|
| 우선순위 | M |
| 진행 | 할 일 |
| 관련 설계 | [MCP 설계](../design/06-mcp.md) |

**설명** — Agent 가 일감을 만들고 고치는 도구다. 사람과 Agent 가 같은 프로젝트를 관리한다는 이 제품의 핵심이라 첫 출시에 넣는다. `AGENT` 엔드포인트에서만 켤 수 있다.

**세부 요구사항**
1. 첫 출시의 쓰기 도구는 다음 6개여야 한다. 권한 코드는 같은 일을 REST 로 할 때와 같다.

   | 도구 | 입력 | 같은 REST · 권한 |
   |---|---|---|
   | `create_issue` | 프로젝트 key, 유형 · 제목 · 설명 · 상태 · 관리자 · 담당자 · 상위 · 날짜, 멱등 키 | `POST …/issues` · `issue.create` (관리자를 남으로 정하면 `issue.manager.assign` 도) |
   | `update_issue` | 프로젝트 key, 번호, `rowVersion`, 바꿀 필드, 댓글(선택) | `PATCH …/issues/{number}` · `issue.edit` (관리자 바꾸기는 `issue.manager.assign` 도) |
   | `add_comment` | 프로젝트 key, 번호, 댓글, 멱등 키 | `POST …/comments` · `issue.comment` |
   | `link_issues` | 프로젝트 key, 번호, 대상 번호, 연결 종류, 멱등 키 | `POST …/links` · `issue.edit` |
   | `unlink_issues` | 프로젝트 key, 번호, 연결 id | `DELETE …/links/{linkId}` · `issue.edit` |
   | `set_parent` | 프로젝트 key, 번호, `rowVersion`, 상위 번호(비우면 최상위) | `PATCH …/issues/{number}` · `issue.edit` |

2. 검증 · 상태의 날짜 자동 채움 · 상하위 규칙 · 연결 규칙은 REST 와 같아야 하고, 어기면 같은 오류 코드를 준다.
3. `update_issue` · `set_parent` 는 `rowVersion` 이 필수다. 다르면 `CONCURRENT_UPDATE` 다.
4. 서비스 키(사람 없음)로 `create_issue` 를 부를 때는 관리자를 꼭 넘겨야 한다. 없으면 `ISSUE_MANAGER_REQUIRED` 다([FR-ISS-006](03-issue.md#fr-iss-006)).
5. 결과는 바뀐 일감의 번호 · `rowVersion` · 상태를 준다(다음 호출에 이어 쓰게).
6. 일감 지우기 · 여러 일감 한꺼번에 고치기 · 프로젝트 설정 · 역할 바꾸기 도구는 두지 않는다.
7. 이력의 경로는 `MCP`, 주체는 개인 키면 키 주인 · 서비스 키면 서비스다([FR-ISS-010](03-issue.md#fr-iss-010)).

**수용 기준**
- [ ] `create_issue` 로 만든 일감의 번호가 웹에서 만든 일감과 같은 순번을 잇는다
- [ ] MCP 로 만든 · 고친 일감의 이력에 경로 `MCP` 와 주체가 보인다
- [ ] 옛 `rowVersion` 으로 `update_issue` 를 부르면 `CONCURRENT_UPDATE` 다
- [ ] 상태를 `DONE` 으로 바꾸면 웹과 같게 완료일이 채워진다
- [ ] 서비스 키로 관리자 없이 `create_issue` 를 부르면 `ISSUE_MANAGER_REQUIRED` 다
- [ ] 어떤 엔드포인트의 `tools/list` 에도 지우기 · 일괄 · 설정 도구가 없다

---

## 4. 기록 · 안전장치 · 연결 안내

<a id="fr-mcp-006"></a>
### FR-MCP-006 MCP 이력 · 감사 · 사용량

| 항목 | 내용 |
|---|---|
| 우선순위 | S |
| 진행 | 할 일 |
| 관련 설계 | [MCP 설계](../design/06-mcp.md) · [워크스페이스 설계 §15](../design/03-workspace-identity.md#ws-audit) |

**설명** — Agent 가 한 일은 사람이 찾아 되돌릴 수 있어야 하고, 관리자는 어느 키가 얼마나 부르는지 봐야 한다. 이력 · 감사의 경로 표시는 [FR-ISS-010](03-issue.md#fr-iss-010) · [FR-WSP-024](01-workspace.md#fr-wsp-024)가 정하고, 여기서는 MCP 쪽에서 더 남길 것과 사용량을 정한다.

**세부 요구사항**
1. MCP 로 한 쓰기는 이력에 경로 `MCP` · 주체 종류(`USER` · `SERVICE`)를, 감사에 경로 `MCP` · 엔드포인트 id · 키 id · 도구 이름을 남겨야 한다.
2. 도구 호출마다 호출 기록(시각 · 엔드포인트 · 키 · 도구 · 결과 코드 · 걸린 시간)을 남기고 30일 보관한다. 도구의 입력 · 결과 본문과 키 원문은 남기지 않는다.
3. 워크스페이스 사용량(`usage.read`, [FR-WSP-018](01-workspace.md#fr-wsp-018))에 엔드포인트 · 키 · 도구별 하루 호출 수(성공 · 실패)를 보인다.
4. 일감 이력 · 감사에서 경로 `MCP` 로 거를 수 있다.

**수용 기준**
- [ ] MCP 로 고친 일감의 감사 기록에 엔드포인트 id · 키 id · 도구 이름이 있다
- [ ] 사용량의 호출 수가 실제로 부른 수와 같다
- [ ] 호출 기록에 키 원문 · 일감 본문이 없다
- [ ] `usage.read` 가 없으면 사용량은 403 이다

<a id="fr-mcp-007"></a>
### FR-MCP-007 안전장치 (속도 제한 · 응답 크기 · 멱등)

| 항목 | 내용 |
|---|---|
| 우선순위 | M |
| 진행 | 할 일 |
| 관련 설계 | [MCP 설계](../design/06-mcp.md) |

**설명** — Agent 는 같은 호출을 되풀이하거나, 너무 자주 부르거나, 큰 응답에 막힐 수 있다. 피해를 작게 두는 장치를 둔다.

**세부 요구사항**
1. 속도 제한은 키마다 · 엔드포인트마다 분당 · 일 호출 수로 걸어야 한다(엔드포인트 설정, 플랫폼 기본값과 상한). 넘으면 HTTP 429 와 `Retry-After` 로 답한다.
2. 도구 결과 하나는 64KB 를 넘지 않아야 한다. 넘는 설명 · 이력은 잘라서 `truncated` 표시와 이어 받기 커서를 준다. `search_issues` 는 한 번에 최대 50건이다.
3. `create_issue` · `add_comment` · `link_issues` 는 멱등 키(클라이언트가 정한 64자 이하)를 받아야 한다. 같은 키(MCP 키 기준)로 24시간 안에 다시 부르면 다시 만들지 않고 처음 결과를 준다. 같은 멱등 키를 다른 입력으로 쓰면 `IDEMPOTENCY_KEY_REUSED` 다.
4. `update_issue` · `set_parent` 는 `rowVersion` 으로 겹침을 막으므로 멱등 키를 받지 않는다.
5. 한 호출은 일감 하나만 바꾼다(일괄 도구 없음).

**수용 기준**
- [ ] 분당 한도를 넘으면 429 와 `Retry-After` 가 오고, 다른 키는 영향받지 않는다
- [ ] 설명이 아주 큰 일감을 읽으면 잘린 결과와 커서가 오고, 커서로 나머지를 받는다
- [ ] 같은 멱등 키로 `create_issue` 를 두 번 부르면 일감은 하나이고 같은 번호를 돌려준다
- [ ] 같은 멱등 키를 다른 입력으로 쓰면 `IDEMPOTENCY_KEY_REUSED` 다

<a id="fr-mcp-008"></a>
### FR-MCP-008 연결 안내 화면

| 항목 | 내용 |
|---|---|
| 우선순위 | M |
| 진행 | 할 일 |
| 관련 설계 | [MCP 설계](../design/06-mcp.md) · [WEB-DESIGN.md](../../WEB-DESIGN.md) |

**설명** — 사람이 엔드포인트를 만들고 키를 받아 자기 Claude Code 에 붙이기까지를 웹 화면에서 끝낸다. moai-wiki 의 MCP 화면과 같은 구조다.

**세부 요구사항**
1. 워크스페이스 설정 아래 MCP 화면에 다음을 둬야 한다.
   - 엔드포인트 목록 · 만들기 · 고치기 · 끄기 · 지우기(`mcp.endpoint.manage` 가 있을 때만)
   - 키 발급 대화 상자 — 원문을 한 번만 보이고 복사 버튼과 "다시 볼 수 없다"는 알림을 단다
   - 내 키 목록과 폐기, `mcp.key.manage` 가 있으면 워크스페이스의 모든 키
2. 연결 안내는 엔드포인트 주소와 붙이는 방법을 보인다: Claude Code 명령(`claude mcp add --transport http <이름> <주소> --header "Authorization: Bearer <키>"`)과 일반 MCP 설정 JSON. 키 자리는 발급 직후에만 원문으로 채우고, 그 뒤에는 자리표시자다.
3. 키 목록의 마지막 사용 시각으로 붙었는지 확인하게 한다.
4. 390px 와 데스크톱, ko/en, 다크 모드.

**수용 기준**
- [ ] 화면에서 엔드포인트를 만들고 키를 받는다. 대화 상자를 닫으면 원문을 다시 볼 수 없다
- [ ] 기본 역할 멤버는 자기 키만 보고, `mcp.key.manage` 가 있으면 모든 키를 본다
- [ ] 안내대로 Claude Code 를 붙여 `search_issues` 와 `create_issue` 를 부르고, 만든 일감의 이력에 MCP 표시가 보인다
- [ ] ego-browser 로 390px · 데스크톱 · 다크 · 영어를 확인했다

---

**관련 문서:** [MCP 설계](../design/06-mcp.md) · [일감 요구사항](03-issue.md) · [프로젝트 요구사항](02-project.md) · [워크스페이스 · 계정 요구사항](01-workspace.md) · [요구사항 전체 목록](00-overview.md)

[← 이전: 일감 요구사항](03-issue.md) | [다음: 비기능 요구사항 →](05-non-functional.md)
