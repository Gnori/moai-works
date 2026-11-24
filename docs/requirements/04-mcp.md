[홈](../README.md) › [요구사항](00-overview.md) › MCP 요구사항

# MCP 요구사항 (FR-MCP)

바깥 AI Agent(Claude Code 등)가 **MCP** 로 붙어 사람과 같은 규칙으로 프로젝트 · 일감을 다루게 하는 요구사항이다. moai-works 는 AI 를 내장하지 않는다. LLM 은 바깥에 있고, moai-works 는 **키** · **입구** · **판정** · **도구** · **안전장치** · **기록**을 맡는다. MCP 체계는 moai-wiki 와 같은 뿌리에서 시작했다. 2026-11-23 부터 키는 사용자의 것이고 워크스페이스별 엔드포인트는 두지 않는다([ADR-029](../design/00-key-decisions.md#adr-029)). 설계는 [MCP 설계](../design/06-mcp.md)에 있다.

| 우선순위 | 개수 | 요구사항 |
|---|---|---|
| M | 12 | [001](#fr-mcp-001) · [002](#fr-mcp-002) · [003](#fr-mcp-003) · [004](#fr-mcp-004) · [005](#fr-mcp-005) · [006](#fr-mcp-006) · [007](#fr-mcp-007) · [008](#fr-mcp-008) · [009](#fr-mcp-009) · [010](#fr-mcp-010) · [011](#fr-mcp-011) · [012](#fr-mcp-012) |
| 합계 | 12 | |

| 공통 | 내용 |
|---|---|
| 키 | MCP 키는 한 종류다 — 사용자 계정 하나(주인)에 묶이고 그 계정의 권한 안에서만 일한다. SHA-256 만 저장, 기간, 만료시키기(지우지 않는다). 키는 입구에 묶이지 않는다 — 권한만 있으면 어느 입구에든 붙는다(2026-11-23, [ADR-028](../design/00-key-decisions.md#adr-028)). **키는 워크스페이스에도 묶이지 않는다** — 주인이 멤버이고 역할에 `mcp.key.issue.personal`("MCP 키로 이 워크스페이스에서 일하기")이 있는 워크스페이스 모두에 닿는다(2026-11-23, [ADR-029](../design/00-key-decisions.md#adr-029)) |
| 입구 | 기능별 고정 입구 둘 — 일감 `/mcp/issue` · 프로젝트 관리 `/mcp/project`(Streamable HTTP). 워크스페이스는 도구를 부를 때마다 정한다(선택 인자 `workspace`). ~~워크스페이스 MCP 엔드포인트 `/mcp/{ws}/{ep}`~~ 는 만들지 않는다(2026-11-23 [ADR-029](../design/00-key-decisions.md#adr-029)) |
| 같은 서비스 · 같은 판정 | 도구는 REST 핸들러와 같은 업무 서비스 · 같은 판정([FR-PRJ-006](02-project.md#fr-prj-006))을 부르는 얇은 어댑터다. MCP 전용 판정을 만들지 않는다. 범위는 요청 문맥(키 주인)에서만 오고, 모델이 넘긴 워크스페이스 · 프로젝트 · 일감 · 사람은 호출마다 다시 판정한다 |
| 오류 | 도구 오류의 `code` 는 같은 일을 REST 로 했을 때와 같다(`PROJECT_NOT_FOUND` · `ISSUE_NOT_FOUND` · `PERMISSION_REQUIRED` · `CONCURRENT_UPDATE` · 검증 코드) |
| MCP 로 하지 않는 일 | 일감 지우기 · 프로젝트 만들기 · 설정 · 보관 · 삭제 · 프로젝트 역할 · 워크스페이스 · 멤버 · 역할 · 키 · 엔드포인트 관리. 사람만 웹에서 한다 |
| 폐기한 것 | "Agent 계정"(사람이 아닌 멤버로 Agent 를 두는 안)은 moai-wiki 에 없는 것이라 뺐다(2026-11-22). 워크스페이스 이름으로 사람 권한과 상관없이 일하던 **서비스 키**와 엔드포인트의 서비스 읽기 방식도 없앴다(2026-11-22 사용자 결정 — [ADR-024](../design/00-key-decisions.md#adr-024)). 권한 코드 `mcp.key.issue.service` 는 쓰지 않는다(MCP 단계에서 moai-wiki 와 함께 카탈로그에서 뺀다) |
| 지금 코드 | 기능별 고정 입구 둘 — 일감 `/mcp/issue`(도구 19개 — 2026-11-23 댓글 목록 · 고치기 · 지우기를 더함, [FR-ISS-019](03-issue.md#fr-iss-019)) · 프로젝트 관리 `/mcp/project`(`list_workspaces` · `list_projects` · 일감 템플릿 관리 셋 — [FR-ISS-020](03-issue.md#fr-iss-020)), 키 하나로 두 입구 모두(2026-11-23 — [ADR-028](../design/00-key-decisions.md#adr-028) · [MCP 설계 §8 도구](../design/06-mcp.md#mc-tools)). 키는 사용자의 것(2026-11-23 V13 — [ADR-029](../design/00-key-decisions.md#adr-029)) — 개인 설정에서 받고, 키 하나로 MCP 권한이 있는 모든 워크스페이스에서 일한다 — [FR-MCP-001](#fr-mcp-001) · [FR-MCP-002](#fr-mcp-002) |

**바로가기:** [키와 입구](#1-키와-입구) · [판정](#2-판정) · [도구](#3-도구) · [기록 · 안전장치 · 연결 안내](#4-기록--안전장치--연결-안내)

---

## 1. 키와 입구

<a id="fr-mcp-001"></a>
### FR-MCP-001 MCP 키 (사용자 키 · 권한 · 감사)

| 항목 | 내용 |
|---|---|
| 우선순위 | M |
| 진행 | 일부 — 한 것: 2026-11-22 발급 · 목록 · 만료시키기 · 감사(IP · User-Agent), DB 트리거로 지우기 · 발급 정보 변경 · 기간 늘리기 금지, 요청마다 캐시 없이 검사. 2026-11-23 키를 입구에 묶지 않음(V11). 2026-11-23 키는 사용자의 것([ADR-029](../design/00-key-decisions.md#adr-029)) — V13 이 `workspace_id` 를 지움(지금 키는 그대로, `McpKeyUserMigrationTest`), 발급 · 목록 · 만료는 개인 설정 `/v1/me/mcp-keys`(`@UserApi`, 만료는 주인만, MCP 권한이 있는 워크스페이스가 없으면 `MCP_KEY_ISSUE_NOT_ALLOWED`), 워크스페이스 관리 화면은 멤버들의 키를 보기만(`mcp.key.manage`), 떠나도 키를 만료시키지 않음(그 워크스페이스만 보이지 않음), 키 발급 · 만료 감사는 사용자 단위, 키 검사는 주인 계정만(`MCP_WORKSPACE_INACTIVE` 없앰), 자동 테스트(`McpKeyRuleTest` 18건 · `McpKeyUserMigrationTest`). 2026-11-23 키로 들어온 요청은 모두 접근 기록에 남고 키 주인은 내 MCP 키에서 키마다 호출 수 · 접근 기록을 본다([FR-MCP-006](04-mcp.md#fr-mcp-006) · [FR-MCP-010](04-mcp.md#fr-mcp-010)). 남은 것: 옛 경로가 없음을 시험으로 확인 |
| 관련 설계 | [MCP 설계](../design/06-mcp.md) · [워크스페이스 설계 §11](../design/03-workspace-identity.md#ws-mcp) |

**설명** — 바깥 Agent 는 MCP 키로 들어온다. **키는 주인 계정 하나에만 묶인다** — 입구(2026-11-23 [ADR-028](../design/00-key-decisions.md#adr-028))에도 워크스페이스(2026-11-23 [ADR-029](../design/00-key-decisions.md#adr-029))에도 묶이지 않는다. 사용자 결정(2026-11-23): "키는 워크스페이스별로 받는 게 아니라 사용자 기준으로 받는 거야" · "키는 로그인한 사용자 기준으로 받는 것이고 그 사용자가 소속된 워크스페이스, 소속된 프로젝트는 모두 접근 가능하고 권한에 따라 하는 거야". 발급 · 만료시키기는 감사에 남긴다. 키는 지우지 않는다. (2026-11-22 ~ 23 에는 키 하나가 워크스페이스 하나에 묶였다 — V5.)

**세부 요구사항**
1. 키는 개인 설정 › 내 MCP 키에서 `POST /v1/me/mcp-keys` `{name, days}`(`@UserApi`)로 받아야 한다. 워크스페이스를 고르지 않는다. 받을 수 있는 사람은 워크스페이스 계정이고, 역할에 `mcp.key.issue.personal` 이 있는 워크스페이스가 적어도 하나 있어야 한다 — 없으면 403 `MCP_KEY_ISSUE_NOT_ALLOWED`. 시스템 관리자 계정은 403 `SYSTEM_ADMIN_NOT_ALLOWED`, 서비스 계정은 403 `USER_ONLY`. 키 주인은 늘 발급한 사람 자신이다 — 키 종류나 다른 주인을 고르는 입력은 없다.
2. **MCP 키는 사용자 계정 하나에 묶이고, 그 계정이 접근할 수 있는 범위 안에서만 일해야 한다**(2026-11-22 사용자 결정: "MCP 키는 당연히 사용자 계정하고 연결되고" · "MCP 키는 사용자 계정과 연결되어 있으며 사용자 권한 범위만 접근 가능해야 한다"). 키로 쓸 수 있는 워크스페이스는 활성 워크스페이스 가운데 주인이 활성 멤버이고 주인의 지금 역할에 `mcp.key.issue.personal` 이 있는 곳 모두다. 그 안에서 할 수 있는 일은 주인의 지금 권한으로 호출마다 다시 판정한다([FR-MCP-002](#fr-mcp-002) · [FR-MCP-003](#fr-mcp-003)). 워크스페이스 이름으로 일하거나 사람 권한을 넘는 키(서비스 키)는 없다. 주인 계정이 정지되면 그 키는 바로 쓸 수 없다.
3. 키 원문(`mai-wks-` + 32바이트 난수 — 앞부분 `mai-wks-` 는 moai-works 의 MCP 키라는 표시, 2026-11-22 사용자 결정)은 발급 응답에 한 번만 나가야 한다. DB 에는 SHA-256 과 표시용 앞부분만 둔다.
4. 이름과 유효 기간(기본 90일, 최대 365일 — 플랫폼 설정)을 정한다. 범위 밖이면 400 이다.
5. **키는 권한만 있으면 어느 입구(엔드포인트)에든 붙을 수 있어야 한다**(2026-11-23 사용자 결정: "키는 권한만 있으면 어떤 mcp 엔드포인트든 연결 가능해야 해" — [ADR-028](../design/00-key-decisions.md#adr-028)). 할 수 있는 일은 도구마다 키 주인의 지금 권한으로 판정한다. 입구가 아닌 `/mcp/**` 경로(첨부 티켓 `/mcp/files/**` 빼고)는 키를 보기 전에 404 `MCP_ENDPOINT_NOT_FOUND` 이고 맞는 입구를 알려 준다. (옛 규칙 "키는 그 엔드포인트에만 · 403 `MCP_KEY_ENDPOINT_DENIED`" 는 없앴다.)
6. 요청마다 캐시 없이 검사해야 한다: 경로가 입구인가 → 모양 → SHA-256 으로 행 찾기 → 만료(기간이 지났거나 만료시킨 키) → 주인 계정 활성(시스템 관리자 아님). 실패는 404(`MCP_ENDPOINT_NOT_FOUND`) · 401(`MCP_KEY_REQUIRED` · `MCP_KEY_INVALID` · `MCP_KEY_EXPIRED`) 또는 403(`MCP_PRINCIPAL_INACTIVE`)이다. 워크스페이스는 키 검사에서 보지 않고 도구가 본다([FR-MCP-002](#fr-mcp-002)) — `MCP_WORKSPACE_INACTIVE` 는 2026-11-23 없앴다.
7-0. 사용자마다 자기 키를 따로 받는다(2026-11-22 사용자: "사용자별로 MCP 키 발급 받고 또 권한에 따라서 그걸 다 볼 수 있어야 하잖아? 감사 기록에서"). 누가 무엇을 보는지는 권한을 따른다 — 주인은 자기 키를 모두(만료된 것까지) 보고, `mcp.key.manage`(기본 역할로는 소유자 · 관리자)는 그 워크스페이스 멤버들의 키를 본다. `audit.read` 는 그 워크스페이스에서 키로 한 일을 본다([FR-MCP-006](#fr-mcp-006)).
7. 내 키 목록(`GET /v1/me/mcp-keys?status=`)은 이름 · 앞부분 · 만든 때 · 만료 · 마지막 사용 시각 · 발급한 곳 · 만료시킨 사람 · 이유 · 메모를 준다(워크스페이스 칸은 없다). 워크스페이스 관리 목록(`GET /v1/workspaces/{workspace}/mcp-keys?status=&owner=`, `@WorkspaceApi(mcp.key.manage)`)은 주인이 지금 그 워크스페이스 멤버인 키를 만료된 것까지 주고, 주인 · 상태로 거른다. 발급한 IP · 프로그램은 주인에게만 보인다. 마지막 사용 시각은 1분에 한 번만 쓴다.
7-1. **키는 지우지 않는다 — 삭제 개념이 없다**(2026-11-22 사용자 결정: "익스파이어도 시킬 수 있어야 겠지. 삭제는 아니고 키는 삭제 개념은 없어"). **키 주인이** `POST /v1/me/mcp-keys/{id}/expire` `{note}` 로 바로 만료(`expires_at` = 지금)시키고 만료시킨 사람 · 이유를 남긴다. 남의 키는 관리자라도 없는 것과 같다(404 `MCP_KEY_NOT_FOUND`) — 사용자 키는 주인의 다른 워크스페이스에도 닿기 때문이다(2026-11-23). 이미 만료면 409. 만료된 키로 오면 401 `MCP_KEY_EXPIRED` 다. 만료된 키도 목록 · 발급 이력에 영원히 남는다.
8. 주인이 워크스페이스를 떠나거나 내보내지거나, 그 워크스페이스 역할에서 `mcp.key.issue.personal` 이 빠지면 다음 호출부터 그 워크스페이스가 키에게 보이지 않아야 한다. 키는 만료시키지 않는다(다른 워크스페이스에서 계속 쓴다). 워크스페이스 관리자가 Agent 를 막는 길은 역할에서 이 권한을 빼는 것이다(2026-11-23 — 예전에는 떠날 때 `MEMBER_LEFT` 로 만료시켰다, [FR-WSP-013](01-workspace.md#fr-wsp-013)).
9. 입구는 기능으로 나눈다(2026-11-23 사용자 결정: "다른 관리 기능과 이슈 관리 기능을 서로 구분하고 싶은 거야" · "프로젝트 생성이나 삭제 등등은 관리 기능") — 일감 `/mcp/issue` · 프로젝트 관리 `/mcp/project`. 옛 키 API `/moai-works-api/v1/mcp-keys` 는 2026-11-22, `POST /v1/workspaces/{workspace}/mcp-keys` · `…/{id}/expire` 는 2026-11-23 없앴다.
10. 발급(`mcp.key.issue`) · 만료시키기(`mcp.key.expire`)를 **사용자 단위 감사**(`audit_log.workspace_id` 없음)에 남긴다 — 한 사람 · 키 id · 앞부분 · 이름 · 주인 · 만료 · 만료시킨 이유 · 메모와 요청 정보(IP · User-Agent)를 함께 남기고, 원문은 남기지 않는다. 그 키로 워크스페이스 안에서 한 일은 그 워크스페이스에 남고, 키로 들어온 요청은 [FR-MCP-006](#fr-mcp-006)이 모두 남긴다.

**수용 기준**
- [x] 키 원문이 DB · 로그 · 감사 어디에도 없다 (DB 는 SHA-256, 감사는 앞부분만 — 2026-11-22)
- [x] 발급한 키는 `mai-wks-` 로 시작하고, 다른 앞부분의 문자열은 DB 를 조회하지 않고 401 `MCP_KEY_INVALID` 다
- [x] 모든 키에 주인 계정이 있고, 주인 계정을 정지하면 그 키가 바로 거절된다 (`McpKeyRuleTest.주인_계정을_쓸_수_없으면_키도_막힌다` 2026-11-23)
- [x] 어떤 키도 주인 계정이 볼 수 없는 프로젝트 · 일감에 닿지 못한다 (`McpKeyRuleTest.list_projects_는_키_주인이_읽을_수_있는_열린_프로젝트만_준다` · `IssueMcpToolsTest.권한과_범위는_REST_와_같다` 2026-11-23)
- [x] 키는 개인 설정에서 워크스페이스를 고르지 않고 받는다. 본문은 이름 · 기간뿐이라 키 종류나 다른 주인을 정할 수 없다. MCP 권한이 있는 워크스페이스가 없으면 403 `MCP_KEY_ISSUE_NOT_ALLOWED`, 시스템 관리자는 403 (`McpKeyRuleTest` · `SystemAdminRuleTest` 2026-11-23)
- [x] 같은 키로 일감 입구 · 프로젝트 관리 입구에 모두 붙고, 입구마다 도구가 다르다 (`McpKeyRuleTest.키_하나로_일감_입구와_프로젝트_관리_입구에_모두_붙고_입구마다_도구가_다르다` · 11002 로 확인 2026-11-23)
- [x] 입구가 아닌 `/mcp/**` 경로는 키가 있어도 없어도 404 `MCP_ENDPOINT_NOT_FOUND` 이고 맞는 입구를 알려 준다 (`McpKeyRuleTest.없는_MCP_입구는_404_와_입구_안내` 2026-11-23)
- [x] 만료시킨 키의 바로 다음 요청과 기간이 지난 키는 401 `MCP_KEY_EXPIRED` 다
- [x] 멤버를 내보내거나 떠나거나 역할에서 MCP 권한을 빼면 다음 호출부터 그 워크스페이스가 키에게 보이지 않고, 키는 만료되지 않는다 (`McpKeyRuleTest.멤버가_아니거나_MCP_권한이_없는_워크스페이스는_키에게_보이지_않는다` · `워크스페이스를_떠나도_키는_만료되지_않는다` 2026-11-23)
- [x] 남의 키는 관리자라도 만료시킬 수 없다(404) — 주인만 만료시킨다 (`McpKeyRuleTest.키는_지우지_않고_주인만_만료시킨다` 2026-11-23)
- [x] 워크스페이스 관리자(`mcp.key.manage`)는 그 워크스페이스 멤버들의 키를 만료된 것까지 보기만 하고(발급한 곳은 주인에게만), 그 권한이 없으면 목록을 볼 수 없다 (`McpKeyRuleTest.관리자는_멤버들의_키를_보기만_하고_멤버는_워크스페이스_키_목록을_못_본다` 2026-11-23)
- [ ] 주인은 자기 키 이력(발급 · 만료)을 개인 설정에서 보고, `audit.read` 가 있는 사람은 그 워크스페이스에서 키로 한 일의 접근 기록을 사람별로 걸러 본다 (키 이력은 됨 — 접근 기록은 [FR-MCP-006](#fr-mcp-006))
- [x] 키를 지우는 API 가 없고, 만료된 키도 목록 · 발급 이력에 남는다 (DB 트리거도 지우기를 막는다)
- [x] 발급 · 만료시키기 감사(사용자 단위 — 워크스페이스 없음)에 한 사람 · 키 id · 앞부분 · 주인 · 만료 · IP · User-Agent 가 있다 (`McpKeyRuleTest.발급과_만료는_사용자_단위_감사로_남고_워크스페이스_감사는_그대로_돈다` 2026-11-23)
- [x] V13 전에 받은 키는 그대로 쓴다 — 행이 그대로 남고 워크스페이스 열만 없어진다 (`McpKeyUserMigrationTest` · `McpKeyRuleTest.V13_전에_받은_키도_그대로_쓴다` 2026-11-23)
- [ ] 옛 경로 `/moai-works-api/v1/mcp-keys` · `POST /v1/workspaces/{workspace}/mcp-keys` 가 없다 (`/mcp/project` 는 2026-11-23 프로젝트 관리 입구로 다시 쓴다)

<a id="fr-mcp-002"></a>
### FR-MCP-002 MCP 입구 · 워크스페이스 고르기

| 항목 | 내용 |
|---|---|
| 우선순위 | M |
| 진행 | 일부 — 한 것: 2026-11-23 입구를 기능으로 나눔 — 고정 입구 일감 `/mcp/issue` · 프로젝트 관리 `/mcp/project`(입구마다 도구 묶음, [ADR-028](../design/00-key-decisions.md#adr-028)). 2026-11-23 워크스페이스별 엔드포인트 계획을 버리고 워크스페이스를 호출마다 고르게 함([ADR-029](../design/00-key-decisions.md#adr-029)) — 프로젝트를 받는 모든 도구에 선택 인자 `workspace`, 비우면 키로 쓸 수 있는 모든 워크스페이스에서 찾기 · 여럿이면 `PROJECT_AMBIGUOUS`, 워크스페이스 기본 일은 `WORKSPACE_REQUIRED`, `list_workspaces`(두 입구) · `list_projects` 가 모든 워크스페이스에 걸침, 판정은 `McpScope` 한 곳, 첨부 티켓도 고른 워크스페이스에서 다시 판정, 자동 테스트(`McpKeyRuleTest` · `AttachmentTicketTest` · `IssueTemplateTest`). 남은 것: 초기화 안내문(`instructions`)에 쓸 수 있는 워크스페이스 담기(규칙은 2026-11-24 일감 #182 의 핵심 규칙 — [FR-MCP-012](04-mcp.md#fr-mcp-012)) · 실제 MCP 클라이언트로 `list_workspaces` · `list_projects` 확인 |
| 관련 설계 | [MCP 설계 §7 판정](../design/06-mcp.md#mc-authz) · [§8 도구](../design/06-mcp.md#mc-tools) · [워크스페이스 설계 §11](../design/03-workspace-identity.md#ws-mcp) |

> **2026-11-23 사용자 결정**([ADR-029](../design/00-key-decisions.md#adr-029)): "워크스페이스별로 mcp 엔드포인트 만드는 건 안 되지. 사용자가 접근 가능한 곳이면 다 되는 거지". 이 요구사항은 처음에 **워크스페이스 MCP 엔드포인트**(`/mcp/{워크스페이스 주소}/{엔드포인트 주소}`, 종류 `QUERY` · `AGENT` · 대상 프로젝트 · 켠 도구 · 속도 제한 · 끄기 · 엔드포인트별 서버 캐시 · `mcp.endpoint.manage` 관리 API · 감사)였다. 그 계획은 대체됐다 — 엔드포인트를 만들지 않고, 키를 프로젝트 목록 · 읽기 전용으로 좁히지도 않는다. 속도 제한은 [FR-MCP-007](#fr-mcp-007) 의 안전장치로 남는다.

**설명** — 바깥 Agent 는 기능별 고정 입구 둘에 키 하나로 붙는다. 어느 워크스페이스에서 일하는지는 도구를 부를 때마다 정하고, 키 주인의 지금 멤버십 · 권한으로 판정한다. 워크스페이스 관리자는 역할로 Agent 를 막는다.

**세부 요구사항**
1. 입구는 일감 `/mcp/issue`(일감 도구 · 템플릿 읽기)와 프로젝트 관리 `/mcp/project`(템플릿 관리 · 앞으로의 관리 도구)뿐이어야 한다. 두 입구 모두 `list_workspaces` · `list_projects` 가 있다. 그 밖의 `/mcp/**`(첨부 티켓 빼고)는 404 `MCP_ENDPOINT_NOT_FOUND` 다.
2. **키로 쓸 수 있는 워크스페이스** = 활성 워크스페이스 ∧ 키 주인이 활성 멤버 ∧ 주인의 지금 역할에 `mcp.key.issue.personal`(화면 이름 "MCP 키로 이 워크스페이스에서 일하기"). 그 밖의 워크스페이스는 키에게 보이지 않아야 한다.
3. 프로젝트를 받는 모든 도구는 선택 인자 `workspace`(워크스페이스 주소)를 받아야 한다. 주면 그곳이 키로 쓸 수 있는 곳이어야 하고, 아니면 404 `WORKSPACE_NOT_FOUND`(없음 · 비멤버 · 권한 없음을 가리지 않는다)다.
4. `workspace` 를 비우면 키로 쓸 수 있는 모든 워크스페이스에서 그 프로젝트 key(주인이 읽을 수 있는 것)를 찾는다 — 하나면 그것, 없으면 404 `PROJECT_NOT_FOUND`, 여럿이면 400 `PROJECT_AMBIGUOUS` 이고 문구에 워크스페이스 주소들을 담아 Agent 가 `workspace` 를 넣어 다시 부르게 한다.
5. 프로젝트 없이 워크스페이스에서 하는 일(워크스페이스 기본 템플릿)은 `workspace` 를 비우면 쓸 수 있는 곳이 하나일 때만 그곳이고, 없으면 `WORKSPACE_NOT_FOUND`, 여럿이면 400 `WORKSPACE_REQUIRED`(문구에 주소들)다.
6. `list_workspaces` 는 키로 쓸 수 있는 워크스페이스의 주소 · 이름 · 종류 · 키 주인의 역할 이름을 준다. `list_projects` 는 그 모든 워크스페이스에 걸치고 줄마다 `workspace {slug, name}` 를 담으며, 선택 인자 `workspace` 로 거른다.
7. 판정은 `McpScope` 한 곳에서 호출마다 DB 사본으로 한다 — 멤버 내보내기 · 멤버십 정지 · 역할 변경이 다음 호출부터 반영된다. 도구는 REST 와 같은 서비스 · 같은 권한 판정을 부른다.
8. 첨부 티켓은 도구가 정한 프로젝트의 워크스페이스에 묶이고, 쓸 때 그 워크스페이스를 아직 키로 쓸 수 있어야 한다(아니면 404 `WORKSPACE_NOT_FOUND`)([FR-MCP-011](#fr-mcp-011)).
9. 초기화 응답의 안내문(`instructions`)에 쓸 수 있는 워크스페이스와 규칙(`workspace` 인자 · `rowVersion` · 하지 않는 일)을 담는다.

**수용 기준**
- [x] 입구는 기능별 고정 둘이고 입구마다 도구 묶음이 다르다(일감 16 · 프로젝트 관리 5) (`McpKeyRuleTest.키_하나로_일감_입구와_프로젝트_관리_입구에_모두_붙고_입구마다_도구가_다르다` 2026-11-23)
- [x] 키 하나로 두 워크스페이스의 프로젝트를 다룬다 — `list_workspaces` · `list_projects` 가 걸치고, 워크스페이스마다 그곳의 권한으로 판정한다 (`McpKeyRuleTest.키_하나로_두_워크스페이스에_닿는다` 2026-11-23)
- [x] 같은 프로젝트 key 가 두 워크스페이스에 있으면 `PROJECT_AMBIGUOUS`(문구에 주소들)이고, `workspace` 를 넣으면 된다. 워크스페이스 기본 템플릿은 `WORKSPACE_REQUIRED` (`McpKeyRuleTest.같은_프로젝트_key_가_두_워크스페이스에_있으면_PROJECT_AMBIGUOUS_이고_workspace_를_넣으면_된다` · `IssueTemplateTest` 2026-11-23)
- [x] 멤버가 아니거나 역할에 MCP 권한이 없는 워크스페이스는 `list_workspaces` 에 없고, `workspace` 로 주면 `WORKSPACE_NOT_FOUND`, 그 프로젝트는 `PROJECT_NOT_FOUND` 다. 권한을 돌려주면 다음 호출부터 다시 보인다 (`McpKeyRuleTest.멤버가_아니거나_MCP_권한이_없는_워크스페이스는_키에게_보이지_않는다` 2026-11-23)
- [x] 첨부 티켓은 `workspace` 로 고른 일감에 올라가고, 그 사이 MCP 권한을 잃으면 티켓도 404 `WORKSPACE_NOT_FOUND` 다 (`AttachmentTicketTest.티켓은_고른_워크스페이스의_일감에_묶이고_MCP_권한을_잃으면_못_쓴다` 2026-11-23)
- [x] 실제 MCP 클라이언트(Claude Code)로 붙어 `tools/list`(일감 16개)와 `list_workspaces` · `list_projects` 를 불러 본다 — 2026-11-23 기존 키(V13 전에 받은 키)로 web-backend 중계를 거쳐 확인: 일감 입구 16개(`workspace` 인자가 있는 도구 15) · 프로젝트 관리 입구 5개, `list_workspaces` 가 개인 · 워크스페이스 A 둘(역할 소유자), `list_projects` 6개(줄마다 워크스페이스), 없는 워크스페이스는 `WORKSPACE_NOT_FOUND`
- [ ] 초기화 안내문에 쓸 수 있는 워크스페이스와 규칙이 담긴다

---

## 2. 판정

<a id="fr-mcp-003"></a>
### FR-MCP-003 MCP 판정

| 항목 | 내용 |
|---|---|
| 우선순위 | M |
| 진행 | 할 일 |
| 관련 설계 | [MCP 설계](../design/06-mcp.md) · [프로젝트 설계](../design/04-project.md) |

**설명** — 도구가 무엇을 볼 수 있고 할 수 있는지는 키 주인의 권한과 엔드포인트가 정한 범위의 교집합이다. 모델이 넘긴 값은 믿지 않고 호출마다 다시 판정한다.

> 2026-11-23 [ADR-029](../design/00-key-decisions.md#adr-029): 엔드포인트를 두지 않는다. 아래의 "엔드포인트 범위 · 대상 프로젝트 · 켠 도구"는 "키로 쓸 수 있는 워크스페이스(멤버 ∧ `mcp.key.issue.personal`)"로 읽는다 — 워크스페이스 고르기는 [FR-MCP-002](#fr-mcp-002).

**세부 요구사항**
1. 범위(워크스페이스 · 키 주인 · 대상 프로젝트 · 켠 도구 · 종류)는 요청 문맥(키 → 엔드포인트)에서만 와야 한다. 도구 입력의 프로젝트 key · 일감 번호 · 사람 id 는 매번 다시 판정한다.
2. 실제 권한은 **키 주인의 프로젝트 권한([FR-PRJ-006](02-project.md#fr-prj-006) — 워크스페이스 역할 ∪ 프로젝트 역할) ∩ 대상 프로젝트 ∩ 켠 도구(엔드포인트 종류가 허용하는 것)** 여야 한다. 볼 수 있는 프로젝트는 대상 가운데 키 주인이 독자인 것뿐이다. 엔드포인트는 좁히기만 하고, 주인에게 없는 권한을 키에 주는 길(서비스 키 · 서비스 읽기 방식)은 없다(2026-11-22 사용자 결정 — [FR-MCP-001](#fr-mcp-001)). 쓰기 주체는 늘 키 주인이다.

3. 도구는 REST 와 같은 서비스(`ProjectAccessService` · 일감 서비스 · 질의 서비스)를 불러야 한다. 호출마다 다시 판정한다 — 역할 · 정지 · 공개 범위 변경이 다음 호출부터 반영된다.
4. 대상 밖 · 볼 수 없는 · 없는 프로젝트는 모두 `PROJECT_NOT_FOUND` 로 같게 답해야 한다. 일감은 `ISSUE_NOT_FOUND` 다.
5. MCP 도구는 `ACTIVE` 프로젝트만 다룬다. 보관한 프로젝트는 관리 권한이 있어도 `PROJECT_NOT_FOUND` 다.
6. 워크스페이스가 정지되면 모든 호출을 거절한다.

**수용 기준**
- [ ] 키의 `search_issues` · `list_projects` 결과에 키 주인이 독자가 아닌 프로젝트가 없다(대상에 넣었어도)
- [ ] 모델이 대상 밖 프로젝트 key 를 넘기면 `PROJECT_NOT_FOUND` 이고 없는 key 와 답이 같다
- [ ] 키 주인의 프로젝트 역할을 빼면 다음 호출부터 거절된다
- [ ] 기본 역할 멤버의 키로 `AGENT` 엔드포인트의 `create_issue` 를 부르면 `PERMISSION_REQUIRED` 다(도구가 켜 있어도)
- [ ] 대상 프로젝트를 지정 멤버(`RESTRICTED`)로 바꾸면, 그 프로젝트의 독자가 아닌 주인의 키에서는 다음 호출부터 빠지고 독자인 주인의 키에서는 보인다
- [ ] 같은 일을 REST 와 MCP 로 하면 결과와 오류 `code` 가 같다(일감 만들기 · 고치기 · 댓글 · 연결 · 상위 바꾸기 · 찾기 · 보기를 매개변수 시험으로)

---

## 3. 도구

<a id="fr-mcp-004"></a>
### FR-MCP-004 읽기 도구

| 항목 | 내용 |
|---|---|
| 우선순위 | M |
| 진행 | 일부 — 2026-11-22: `list_projects` · `list_issue_types` · `search_issues`(제목 · 설명 · 번호 · 상태 묶음 · 유형 · 사람 · 상위 · 기한 범위 · 기한 지남 · 정렬, totalPages · hasNext · nextPage) · `get_issue` · `list_attachments`, REST 와 같은 서비스 · 권한, 자동 테스트. 2026-11-24: 번호는 워크스페이스에서 하나라 번호로 가리키는 도구의 `project` 는 선택(다르면 `ISSUE_PROJECT_MISMATCH`, 번호가 여러 워크스페이스에 있으면 `ISSUE_AMBIGUOUS`), `search_issues` 는 `project` 없이 워크스페이스 전체 · 쉼표로 여럿, 결과마다 `project {key, name}`(McpIssueNumberTest). 2026-11-24(일감 #177): 모든 도구에서 인자 꼴 오류도 같은 JSON 오류 모양(400 `INVALID_ARGUMENT` · 한국어 문구 · `field`) — SDK 의 영어 평문 대신, 같은 뜻의 인자는 같은 꼴(일감 번호 정수 · 숫자 글, 여러 값 쉼표 글 · 글 배열 — McpArgumentFormTest · McpToolArgumentsTest). 2026-11-24(일감 #157): 오류에 서비스가 붙인 값(REST problem+json 과 같은 값 — 댓글 겹침의 `rowVersion` · `unmatched` · 고를 `workspaces`)도 싣고 세 기본 값은 덮지 못함(McpErrorDetailsTest — moai-wiki 와 같다). 2026-11-24(일감 #176): `get_issue` 에 구간을 셈할 근거 `statusChangedAt` · `lastWorkLogAt` · `serverTime`, 작업 시간 기록의 `id` · `deleted`(지운 것도 보임 — WorkLogMcpTest). 남은 것: 엔드포인트 범위(FR-MCP-002) · 응답 크기 상한 · 사용자 데이터 표시 |
| 관련 설계 | [MCP 설계](../design/06-mcp.md) |

**설명** — Agent 가 프로젝트와 일감을 읽는 도구다. `QUERY` · `AGENT` 엔드포인트 모두에서 켤 수 있다.

**세부 요구사항**
1. 첫 출시의 읽기 도구는 다음 5개여야 한다.

   | 도구 | 입력 | 결과 |
   |---|---|---|
   | `list_projects` | — | 대상 프로젝트 가운데 볼 수 있는 `ACTIVE` 프로젝트(key · 이름 · 설명) |
   | `get_project` | 프로젝트 key | 프로젝트 상세와 이 엔드포인트에서의 실제 권한(무엇을 할 수 있는지) |
   | `list_issue_types` | 프로젝트 key | 일감 유형(key · 이름 · 설명) |
   | `search_issues` | 프로젝트 key(선택 — 비우면 워크스페이스 전체, 쉼표로 여럿, [FR-ISS-025](03-issue.md#fr-iss-025)), [FR-ISS-012](03-issue.md#fr-iss-012)의 필터 · 정렬, 쪽 | 일감 요약 줄(`project` 포함)과 다음 쪽 커서 |
   | `get_issue` | 번호(워크스페이스 번호), 프로젝트 key(선택 — 주면 그 프로젝트의 일감인지 확인) | [FR-ISS-002](03-issue.md#fr-iss-002)의 상세(지금 프로젝트 · 하위 · 연결 · 이력 · 예상 공수 · 쓴 시간 포함, 이력은 최근 것부터 나눠 줌) |
   | `list_revisions` | 프로젝트 key, 번호, before | [FR-ISS-015](03-issue.md#fr-iss-015)의 판 목록(누가 · 경로 · 언제 · 바뀐 필드) |
   | `get_revision_diff` | 프로젝트 key, 번호, 판, from | 두 판의 차이(필드 전 · 후 + 설명 줄 단위) |

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
| 진행 | 일부 — 2026-11-22: `create_issue` · `update_issue`(rowVersion · clear · 함께 댓글) · `delete_issue`(사용자 결정 — issue.delete + 이유 필수) · `add_comment` · `log_work` · `link_issues` · 첨부 티켓 도구, 한 사람 = 키 주인 · 경로 MCP(이력 · 판 · 감사 · 알림), 자동 테스트. 2026-11-24: 번호로 가리키는 쓰기 도구의 `project` 는 선택, `update_issue` 의 `moveToProject`(다른 프로젝트로 옮기기 — [FR-ISS-024](03-issue.md#fr-iss-024)), `link_issues` 대상은 같은 워크스페이스의 다른 프로젝트 일감도(McpIssueNumberTest). 2026-11-24(일감 #176): `delete_work_log`(키 주인이 적은 기록만 · 이유 필수 · REST 와 같은 서비스 · 이력 · 감사), `log_work` 결과에 `workLogId` · `serverTime`(WorkLogMcpTest). 2026-11-24(일감 #178): `update_issue` 의 `setSection`(절 하나 채우기 — 없으면 끝에 붙임) · `checkItems` · `uncheckItems`(체크 목록 줄을 글로, `all`) — 설명을 통째로 보내지 않고 끝내기, 상태 · 댓글과 한 번에, 맞지 않는 글은 `CHECK_ITEM_NOT_FOUND`(아무것도 안 바뀜), 이력 · 판 · `templateCheck` 그대로(DescriptionEditTest · McpCompletionTest). 2026-11-24(일감 #182): `update_issue` 의 `reason`(보류 · 취소의 까닭 — `comment` 의 다른 이름), `create_issue` · `update_issue` · `log_work` 결과의 길잡이와 설명 · 이력 빼기([FR-MCP-012](04-mcp.md#fr-mcp-012) — McpGuideTest). 2026-11-24(일감 #199): `update_issue` 의 `moveItems`(확인 못 한 체크 항목을 다른 일감으로 넘기기 — [FR-MCP-012](04-mcp.md#fr-mcp-012)). 2026-11-25(일감 #237): `setSection.mode`(replace 기본 · append · prepend), replace 가 지운 것(`removed` — 새 글에 없는 줄 · 소절 · 언어별 코드 블록)과 옛 본문(`previous`, 4000자까지), 크게 지우면 `SECTION_REPLACED_LARGE`(WARN, 막지 않음 — DescriptionEditTest · IssueGuideSectionTest · McpCompletionTest). 남은 것: 멱등 키 · `unlink_issues` · `set_parent` |
| 관련 설계 | [MCP 설계](../design/06-mcp.md) |

**설명** — Agent 가 일감을 만들고 고치는 도구다. 사람과 Agent 가 같은 프로젝트를 관리한다는 이 제품의 핵심이라 첫 출시에 넣는다. `AGENT` 엔드포인트에서만 켤 수 있다.

**세부 요구사항**
1. 첫 출시의 쓰기 도구는 다음 6개여야 한다. 권한 코드는 같은 일을 REST 로 할 때와 같다.

   | 도구 | 입력 | 같은 REST · 권한 |
   |---|---|---|
   | `create_issue` | 프로젝트 key(필수 — 만들 프로젝트), 유형 · 제목 · 설명 · 상태 · 관리자 · 담당자 · 상위 · 날짜, 멱등 키 | `POST …/projects/{project}/issues` · `issue.create` (관리자를 남으로 정하면 `issue.manager.assign` 도). 번호는 워크스페이스 순번 |
   | `update_issue` | 번호, 프로젝트 key(선택), `rowVersion`, 바꿀 필드, 댓글(선택), `moveToProject`(선택 — 옮길 프로젝트) | `PATCH …/workspaces/{ws}/issues/{number}` · `issue.edit` (관리자 바꾸기는 `issue.manager.assign` 도, 옮기기는 옮길 프로젝트의 `issue.create` 도) |
   | `add_comment` | 번호, 프로젝트 key(선택), 댓글, 멱등 키 | `POST …/comments` · `issue.comment` |
   | `log_work` | 번호, 프로젝트 key(선택), 날짜, 시간, 메모, 멱등 키 | `POST …/work-logs` · `issue.edit` — 일한 사람 = 키 주인([FR-ISS-016](03-issue.md#fr-iss-016)) |
   | `link_issues` | 번호, 프로젝트 key(선택), 대상 번호(같은 워크스페이스), 연결 종류, 멱등 키 | `POST …/links` · `issue.edit` |
   | `unlink_issues` | 프로젝트 key, 번호, 연결 id | `DELETE …/links/{linkId}` · `issue.edit` |
   | `set_parent` | 프로젝트 key, 번호, `rowVersion`, 상위 번호(비우면 최상위) | `PATCH …/issues/{number}` · `issue.edit` |

2. 검증 · 상태의 날짜 자동 채움 · 상하위 규칙 · 연결 규칙은 REST 와 같아야 하고, 어기면 같은 오류 코드를 준다.
3. `update_issue` · `set_parent` 는 `rowVersion` 이 필수다. 다르면 `CONCURRENT_UPDATE` 다.
4. `create_issue` 에 관리자를 넘기지 않으면 키 주인이 관리자다(웹과 같다). 다른 사람을 관리자로 정하려면 키 주인에게 `issue.manager.assign` 이 있어야 한다([FR-ISS-006](03-issue.md#fr-iss-006)).
5. 결과는 바뀐 일감의 번호 · `rowVersion` · 상태를 준다(다음 호출에 이어 쓰게).
6. 일감 지우기 · 여러 일감 한꺼번에 고치기 · 프로젝트 설정 · 역할 바꾸기 도구는 두지 않는다.
7. 이력의 경로는 `MCP`, 주체는 늘 키 주인(`USER`)이다. 이력 · 감사에 엔드포인트 id · 키 id 를 함께 남긴다([FR-ISS-010](03-issue.md#fr-iss-010)).

**수용 기준**
- [x] `create_issue` 로 만든 일감의 번호가 웹에서 만든 일감과 같은 순번을 잇는다 (같은 IssueService.create)
- [x] MCP 로 만든 · 고친 일감의 이력에 경로 `MCP` 와 주체가 보인다 (IssueMcpToolsTest)
- [x] 옛 `rowVersion` 으로 `update_issue` 를 부르면 `CONCURRENT_UPDATE` 다 (IssueMcpToolsTest)
- [ ] 상태를 `DONE` 으로 바꾸면 웹과 같게 완료일이 채워진다
- [ ] 관리자 없이 `create_issue` 를 부르면 키 주인이 관리자가 되고, 이력 · 감사의 주체가 키 주인(`USER`)이다
- [x] ~~어떤 엔드포인트의 `tools/list` 에도 지우기 · 일괄 · 설정 도구가 없다~~ → 2026-11-22 사용자 결정("MCP 가장 먼저 필요한 건 … 아이템 관련 CRUD")으로 일감 지우기는 연다: `delete_issue` 는 issue.delete + 이유 필수 · 지움 표시만 · 감사에 경로와 이유. 일괄 · 설정 · 역할 · 키 도구는 여전히 없다

---

## 4. 기록 · 안전장치 · 연결 안내

<a id="fr-mcp-006"></a>
### FR-MCP-006 MCP 접근 기록 (읽기 · 생성 · 수정 · 삭제 전부)

| 항목 | 내용 |
|---|---|
| 우선순위 | M |
| 진행 | 일부 — 한 것: 2026-11-23 V19 `mcp_access_log`(추가 전용 — 고치기 · 지우기 · 비우기를 트리거가 막음). MCP 요청 하나 = 한 줄 — 연결 시작 · 알림 · 도구 목록 · 도구 호출 · 세션 끝 · 키 검사 거절(키 없음 · 틀림 · 만료 · 주인 계정 정지 · 없는 입구). 줄마다 키 id · 앞부분 · 이름 · 주인 · 입구 · 워크스페이스(도구가 그 호출에서 정한 곳, 없으면 비움) · MCP 메서드 · 도구 · 작업 종류(`READ` · `CREATE` · `UPDATE` · `DELETE` · `LINK` · `LIST` · `CONNECT`) · 대상(프로젝트 · 일감 번호 · 연결 대상 · 첨부) · 인자 요약(설명 · 댓글 같은 글과 200자 넘는 글은 길이 · sha256 만, 키 모양은 가림) · 결과(`OK` · `DENIED` · `ERROR` + HTTP 상태 · code) · 걸린 시간 · `clientInfo`(이름 · 버전 · 제목) · 프로토콜 · 능력 · 세션 id · User-Agent · IP · trace_id. 도구 호출 · 도구 목록 · 연결 시작은 줄을 저장한 뒤에 결과를 돌려준다 — 못 쓰면 그 요청이 실패하고 도구가 한 쓰기도 되돌린다. 내 키의 호출 수 = 그 키의 줄 수. 자동 테스트 `McpAccessLogTest`. 2026-11-25 MCP 서버가 세션을 두지 않게 되어(무상태 — [MCP 설계 §5.1](../design/06-mcp.md#mc-stateless)) 세부 3 의 "같은 세션의 모든 줄"은 같은 키 · 같은 User-Agent 의 줄로 바꿨다 — 연결 시작 줄에 `clientInfo` 를 모두, 다른 줄에는 그 키 · User-Agent 가 마지막 연결 시작에서 밝힌 것(메모리, 다시 띄우면 다음 연결 시작까지 비움)을 붙이고, 줄마다 User-Agent · `MCP-Protocol-Version` 머리글을 남긴다. 세션 id 칸은 클라이언트가 보낸 값이다(`McpStatelessTest`). 남은 것: 쓰기의 감사 · 일감 이력 줄에 trace_id 를 넣어 잇기(세부 5) · 돌려준 개수 · 바꾼 필드 이름 · 멱등 키(세부 2 — 멱등 키는 아직 없음) · 보존 3년 정리 배치(세부 7) · 사용량 화면(세부 8, [FR-WSP-018](01-workspace.md#fr-wsp-018)) · Claude Code 로 실제로 붙여 확인. (세부 1 의 "다른 엔드포인트 · 꺼진 엔드포인트"는 엔드포인트가 없어져 "없는 입구"로 남긴다 — [ADR-029](../design/00-key-decisions.md#adr-029)) |
| 관련 설계 | [MCP 설계 — 접근 기록](../design/06-mcp.md#mc-access-log) · [워크스페이스 설계 §15](../design/03-workspace-identity.md#ws-audit) |

**설명** — 사용자 결정(2026-11-22): "MCP 키 발급을 해 주면 감사 데이터를 모두 기록해야 해. 클라이언트 정보 자세하게. 그리고 읽기, 수정, 삭제, 생성 모두 기록해야 해." MCP 키로 들어온 요청은 쓰기뿐 아니라 읽기까지 하나도 빠짐없이 남긴다. 사람이 Agent 가 무엇을 보고 무엇을 바꿨는지 모두 되짚을 수 있어야 한다.

**세부 요구사항**
1. MCP 키로 들어온 요청마다 한 줄씩 접근 기록(`mcp_access_log` — 추가 전용, DB 가 고치기 · 지우기를 막는다)에 남겨야 한다. 연결 시작(`initialize`) · 도구 목록(`tools/list`) · 도구 호출(`tools/call`) · 세션 끝 · 인증 실패(키 없음 · 틀림 · 만료 · 다른 엔드포인트 · 꺼진 엔드포인트)까지 모두다.
2. 남기는 것: 언제(받은 시각 · 걸린 시간) · 어느 키(키 id · 앞부분 · 이름 · 키 주인) · 어느 워크스페이스 · 엔드포인트 · MCP 메서드 · 도구 이름 · 작업 종류(`READ` · `CREATE` · `UPDATE` · `DELETE` · `SESSION`) · 대상(프로젝트 key · 일감 번호 · 연결 id) · 입력 요약(도구 인자 — 설명 · 댓글처럼 긴 글은 길이와 해시만, 8KB 상한) · 결과(성공 · 거부 · 오류, HTTP 상태, 오류 코드, 돌려준 개수 · 바꾼 필드 이름) · 멱등 키.
3. 클라이언트 정보를 자세히 남겨야 한다: MCP `initialize` 의 `clientInfo`(이름 · 버전 · 제목) · 프로토콜 버전 · 클라이언트 능력(capabilities) 요약 · MCP 세션 id · User-Agent · 클라이언트 IP(web-backend 가 넘긴 값 — [FR-MCP-009](#fr-mcp-009)) · 요청 id(trace_id). 같은 세션의 모든 줄에 그 세션의 `clientInfo` 를 붙인다.
4. 키 원문 · 일감 본문 전체 · 응답 본문은 남기지 않는다.
5. 쓰기(생성 · 수정 · 삭제 · 연결)는 지금처럼 감사(`audit_log` — 경로 MCP · 엔드포인트 · 키 id · 도구)와 일감 이력에도 남기고, 접근 기록 줄과 trace_id 로 이어진다.
6. 접근 기록을 저장하지 못하면 그 요청도 실패해야 한다 — 남기지 못한 호출이 없어야 한다.
7. 보존 기간은 감사와 같게 기본 3년(플랫폼 설정)이다.
8. 사용량(`usage.read`, [FR-WSP-018](01-workspace.md#fr-wsp-018))은 접근 기록에서 엔드포인트 · 키 · 도구 · 클라이언트별 하루 호출 수(성공 · 거부 · 오류)를 센다.

**수용 기준**
- [x] 읽기 도구를 한 번 부르면 접근 기록이 한 줄 생기고 작업 종류가 `READ` 다 (McpAccessLogTest 2026-11-23)
- [x] 생성 · 수정 · 삭제 · 연결 · 도구 목록 · 연결 시작이 각각 한 줄씩 남는다 (McpAccessLogTest — 알림 · 세션 끝도 한 줄)
- [x] 줄마다 키 id · 키 주인 · 엔드포인트 · 도구 · 대상 · 결과 · 걸린 시간 · `clientInfo` 이름과 버전 · User-Agent · IP · 세션 id 가 있다 (McpAccessLogTest — 엔드포인트는 들어온 입구. 2026-11-25 무상태부터 세션 id 는 클라이언트가 보냈을 때만, `clientInfo` 는 같은 키 · User-Agent 로 이어 붙인다 — McpStatelessTest)
- [x] 만료된 키 · 틀린 키 · 다른 엔드포인트로 온 요청도 결과 "거부"로 남는다 (McpAccessLogTest — 키 없음 · 주인 계정 정지 · 없는 입구까지)
- [x] 키 원문 · 설명 본문 전체가 어디에도 없다 (McpAccessLogTest — 접근 기록 · 감사 · 화면 응답 · CSV, 키를 인자 · User-Agent 에 넣어도)
- [x] 접근 기록을 고치거나 지우면 DB 가 거부한다 (McpAccessLogTest — 비우기도)
- [x] 접근 기록 저장을 실패시키면 그 도구 호출도 실패한다 (McpAccessLogTest — 쓰기는 되돌리고, 읽기 · 도구 목록 · 연결 시작도 실패)
- [x] 사용량의 호출 수가 접근 기록의 줄 수와 같다 (McpAccessLogTest — 내 키의 호출 수. 사용량 화면이 생기면 그 수도 접근 기록에서 센다)

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
| 진행 | 일부 — 2026-11-22: 엔드포인트 없이 키 화면만 — 내 MCP 키(개인 설정) · 발급 대화 상자(원문 한 번 · 복사 · 다시 볼 수 없음 알림 · 연결 주소 · Claude Code 명령) · 만료시키기 · 워크스페이스 MCP 키(관리자 — 모든 키 · 주인 거르기 · 감사로 가기). ego-browser 데스크톱 · 390px 다크. 2026-11-23: 연결 안내가 두 주소(일감 `/mcp/issue` · 프로젝트 관리 `/mcp/project`)와 두 명령(`claude mcp add --scope user …` — 전역 등록)을 보인다, 화면 시험(브라우저 확인은 아직). 남은 것: 엔드포인트 화면 · 일반 MCP 설정 JSON · 영어 확인 · Claude Code 로 붙여 보기 2026-11-23: 발급 창 연결 안내를 데스크톱 · 390px 로 확인, 프로젝트 관리 입구 안내 문구를 템플릿 도구에 맞춤, 키 원문이 보이는 동안 휴대폰을 돌리면 창이 닫히던 것 고침 |
| 관련 설계 | [MCP 설계](../design/06-mcp.md) · [WEB-DESIGN.md](../../WEB-DESIGN.md) |

**설명** — 사람이 엔드포인트를 만들고 키를 받아 자기 Claude Code 에 붙이기까지를 웹 화면에서 끝낸다. moai-wiki 의 MCP 화면과 같은 구조다.

**세부 요구사항**
1. 워크스페이스 설정 아래 MCP 화면에 다음을 둬야 한다.
   - 엔드포인트 목록 · 만들기 · 고치기 · 끄기 · 지우기(`mcp.endpoint.manage` 가 있을 때만)
   - 키 발급 대화 상자 — 원문을 한 번만 보이고 복사 버튼과 "다시 볼 수 없다"는 알림을 단다
   - 내 키 목록과 만료시키기, `mcp.key.manage` 가 있으면 워크스페이스의 모든 키(만료된 것 포함 발급 이력)
2. 연결 안내는 입구 주소와 붙이는 방법을 보인다: 지금은 일감 `/mcp/issue` · 프로젝트 관리 `/mcp/project` 두 주소와 Claude Code 명령 둘(`claude mcp add --scope user --transport http moai-works-issue <주소> --header "Authorization: Bearer <키>"` · `moai-works-project` 같은 꼴), 그리고 일반 MCP 설정 JSON. **`--scope user`(전역)로 등록한다**(2026-11-23 사용자: "전역으로 등록해야 할 것 같은데") — 범위를 빼면 명령을 실행한 폴더에만 등록된다. 키 자리는 발급 직후에만 원문으로 채우고, 그 뒤에는 자리표시자다.
3. 키 목록의 마지막 사용 시각으로 붙었는지 확인하게 한다.
4. 390px 와 데스크톱, ko/en, 다크 모드.

**수용 기준**
- [ ] 화면에서 엔드포인트를 만들고 키를 받는다. 대화 상자를 닫으면 원문을 다시 볼 수 없다
- [x] 기본 역할 멤버는 자기 키만 보고, `mcp.key.manage` 가 있으면 모든 키를 본다 (api 시험 · 화면 시험 · ego-browser 2026-11-22)
- [ ] 안내대로 Claude Code 를 붙여 `search_issues` 와 `create_issue` 를 부르고, 만든 일감의 이력에 MCP 표시가 보인다
- [ ] ego-browser 로 390px · 데스크톱 · 다크 · 영어를 확인했다


<a id="fr-mcp-009"></a>
### FR-MCP-009 MCP 는 web-backend 가 중계한다

| 항목 | 내용 |
|---|---|
| 우선순위 | M |
| 진행 | 일부 — 2026-11-22: web-backend `/mcp/**` 중계(`McpRelay` — 세션 · CSRF 밖 보안 체인, 헤더 허용 목록, X-Forwarded-For 새로 씀, 요청 id, 조각마다 흘려보냄, IP 별 분당 600), api 는 믿는 중계(`trusted-proxies`)의 X-Forwarded-For 만 씀(`ClientIp`). 11002 로 initialize · 도구 호출(SSE) · 만료 뒤 401 확인. 2026-11-23 접근 기록이 그 IP 를 남긴다(믿는 중계의 X-Forwarded-For 만, 직접 온 요청이 꾸민 값은 버림 — `McpAccessLogTest`). 2026-11-24(일감 #136): IP 별 셈의 자리 수에 끝(1만 개를 넘으면 지난 1분에 요청이 없던 IP 를 치움, 1분에 한 번까지), 접속 주소를 전달 머리글로 바꾸지 않게 박아 둠, 중계 시험 `McpRelayTest` 6건(허용 헤더 · 꾸민 X-Forwarded-For · 401 의 WWW-Authenticate 와 본문 · 403 · 429 와 풀림 · 자리 치우기 · 502 — 운영과 같은 JDK HttpClient). 남은 것: Claude Code 로 붙여 보기 · 실제 11002 중계로 접근 기록의 IP · 401 의 WWW-Authenticate 확인 |
| 관련 설계 | [MCP 설계 — 중계](../design/06-mcp.md#mc-relay) · [web-backend 모듈](../modules/02-web-backend.md) |

**설명** — 사용자 결정(2026-11-22): "MCP 는 api 로 직접 서비스하지 말고 moai-works-web-backend 가 프록시로 해야 하니까 moai-works-web-backend 포트로 연결되어야" · "이건 moai-wiki 도 같다". 바깥 MCP 클라이언트(Claude Code 등)의 연결 주소는 web-backend 포트다 — 로컬 `https://localhost:11002/mcp/{워크스페이스}/{엔드포인트}`. 이어서 사용자: "MCP 는 api 에서도 서비스는 가능해. 안 되는 게 아니야" — api 도 `/mcp/**` 를 그대로 서비스하고 직접 붙어도 된다.

**세부 요구사항**
1. web-backend 가 `/mcp/**` 를 api `/mcp/**` 로 1:1 중계해야 한다(경로 표 없이). 키 검사 · 판정 · 범위는 api 한 곳이다 — web-backend 는 판정하지 않는다.
2. 이 경로는 로그인 세션 · CSRF · 쿠키를 쓰지 않는다. 요청의 `Authorization`(MCP 키)을 그대로 넘기고, 세션의 사용자 토큰을 붙이지 않는다.
3. Streamable HTTP 라서 POST 응답(JSON · SSE)과 GET SSE 스트림을 버퍼링 없이 흘려야 한다. 긴 연결에 맞춘 타임아웃을 둔다.
4. 넘기는 헤더는 허용 목록으로 정한다 — 요청: `Authorization` · `Content-Type` · `Accept` · `Mcp-Session-Id` · `MCP-Protocol-Version` · `Last-Event-ID` · `User-Agent`, 응답: `Content-Type` · `Mcp-Session-Id` · `WWW-Authenticate` · `Retry-After`.
5. 클라이언트 IP(`X-Forwarded-For` — 클라이언트가 보낸 값은 버리고 web-backend 가 새로 쓴다)와 요청 id 를 붙여 api 의 접근 기록([FR-MCP-006](#fr-mcp-006))이 쓰게 한다.
6. web-backend 도 `/mcp` 에 IP 별 속도 제한을 둔다(키 검사 전에 마구 들어오는 요청을 막는다). 키 · 도구별 제한은 api 가 한다([FR-MCP-007](#fr-mcp-007)).
7. 엔드포인트 화면의 연결 안내([FR-MCP-008](#fr-mcp-008))는 web-backend 주소를 보인다. api 의 `/mcp/**` 도 그대로 서비스한다(직접 붙어도 된다) — 두 길 모두 키 검사 · 판정 · 접근 기록은 api 한 곳이고, 직접 붙은 요청의 IP 는 접속 주소다(그 요청이 보낸 `X-Forwarded-For` 는 믿지 않는다 — 믿을 수 있는 중계에서 온 것만 쓴다).

**수용 기준**
- [ ] Claude Code 를 `https://localhost:11002/mcp/…` 로 붙여 도구 목록과 도구 호출이 된다
- [ ] 스트림 응답이 버퍼링 없이 바로바로 온다
- [x] `/mcp` 요청에 CSRF 토큰 · 세션 쿠키가 필요 없고, 세션 쿠키를 보내도 쓰지 않는다 (2026-11-22 — 응답에 Set-Cookie 없음)
- [ ] 접근 기록의 IP 가 실제 클라이언트 IP 다(클라이언트가 꾸민 `X-Forwarded-For` 는 무시된다) — 중계 · api 쪽은 됨(`ClientIp` 시험), 2026-11-23 접근 기록도 됨(`McpAccessLogTest`). 실제 11002 로 확인이 남음
- [ ] 키가 없으면 401 과 `WWW-Authenticate` 가 그대로 클라이언트에 간다 — api 는 MCP 키 거절에 `WWW-Authenticate: Bearer` 를 보내고(`McpKeyRuleTest`), 중계는 401 의 상태 · 머리글 · 본문을 그대로 넘긴다(`McpRelayTest`, 2026-11-24). 실제 11002 로 확인이 남음

<a id="fr-mcp-010"></a>
### FR-MCP-010 MCP 접근 기록 보기 · 거르기

| 항목 | 내용 |
|---|---|
| 우선순위 | M |
| 진행 | 일부 — 한 것: 2026-11-23 REST `GET /v1/workspaces/{ws}/mcp-access-log`(`@WorkspaceApi(audit.read)` — 그 워크스페이스에서 쓴 줄만) · `…/mcp-access-log.csv`, 내 키 `GET /v1/me/mcp-keys/{id}/access-log` · `….csv`(`@UserApi`, 주인만 — 워크스페이스가 없는 줄까지). 거르기: 키 · 키 주인 · 도구 · 작업 종류(여럿) · 결과(여럿) · 클라이언트 이름 · 프로젝트 · 세션 id · IP · 기간, 모두 함께. 최신순, 커서는 시각 · id(감사 기록과 같다), 한 번에 최대 200줄. CSV 는 거른 결과 최대 10만 줄 · 수식 주입 막기, 보내기 전에 감사 `mcp.access_log.export`(조건 · 줄 수 — 워크스페이스는 그 워크스페이스에, 내 키는 사용자 단위). 화면: 워크스페이스 › MCP 접근 기록(`/w/{ws}/mcp-access`, 감사 기록 바로 아래 메뉴, `audit.read`) — 조건은 주소에, 데스크톱 표 · 좁은 화면은 한 칸에 쌓기, 줄을 열면 모든 칸(클라이언트 정보 · 인자 요약) · 같은 세션 · 이 키 · 일감 열기, CSV. 워크스페이스 › MCP 키에서 키마다 그 키의 접근 기록으로. 개인 설정 › 내 MCP 키에서 키마다 호출 수 · 접근 기록 대화상자. 자동 테스트(api `McpAccessLogTest`, 화면 `access-log.test.ts` · `access-screen.test.tsx`). 남은 것: ego-browser 로 데스크톱 · 390px · 다크 · 영어 확인 · 거르기 클라이언트 판 · 오류 코드 · trace_id(api 에 없다 — 줄 상세에서 본다) · 같은 trace_id 의 감사 · 일감 이력으로 건너가기. (세부 5 의 "감사 화면의 탭" 대신 감사 기록 옆 메뉴 하나로 뒀다 — 2026-11-23 브리프) |
| 관련 설계 | [MCP 설계 — 접근 기록](../design/06-mcp.md#mc-access-log) |

**설명** — 사용자 결정(2026-11-22): "필터 기능도 잘 만들고, 당연히 워크스페이스의 권한이 있는 사용자는 그 이력을 볼 수 있어야". 워크스페이스에서 감사 권한이 있는 사람은 그 워크스페이스의 MCP 접근 기록을 모두 보고 잘 걸러 찾는다.

**세부 요구사항**
1. `GET /workspaces/{workspace}/mcp-access-log` 는 `@WorkspaceApi(audit.read)` 여야 한다 — 그 워크스페이스의 기록만. 키 주인은 자기 키의 기록을 볼 수 있다(`…/mcp-keys/{id}/access-log`, 자기 키만).
2. 거르기: 기간(시작 · 끝) · 키(id · 이름 · 앞부분) · 키 주인 · 엔드포인트 · 클라이언트(이름 · 버전) · IP · 작업 종류(읽기 · 생성 · 수정 · 삭제 · 세션) · 도구 · 프로젝트 · 일감 번호 · 결과(성공 · 거부 · 오류) · 오류 코드 · 세션 id · trace_id. 여러 조건을 함께 건다. 화면은 거른 조건을 주소에 남겨 새로 고쳐도 같은 결과다.
3. 최신순, 커서로 이어 받는다(한 번에 최대 200줄). 한 줄을 열면 모든 필드(클라이언트 정보 · 입력 요약 · 결과)를 본다. 같은 세션 · 같은 trace_id 의 다른 줄과 그 쓰기의 감사 · 일감 이력으로 건너간다.
4. 거른 결과를 CSV 로 내려받는다(최대 10만 줄, `audit.read`). 내려받기도 감사(`mcp.access_log.export` — 조건 · 줄 수)에 남긴다.
5. 화면은 워크스페이스 감사 화면의 "MCP 접근" 탭이다(데스크톱 표 · 390px 카드). 키 화면에서 그 키의 기록으로 바로 간다.
6. 다른 워크스페이스 기록은 섞이지 않는다. `audit.read` 가 없으면 403(자기 키 기록은 예외), 멤버가 아니면 404 다.

**수용 기준**
- [x] `audit.read` 가 있는 사람은 그 워크스페이스의 모든 MCP 접근 기록을 보고, 없으면 403 이다(자기 키 기록은 본다) (McpAccessLogTest 2026-11-23 — 비멤버 404, 남의 키 404)
- [x] 거르기 조건마다 맞는 줄만 나오고 여러 조건을 함께 걸 수 있다 (McpAccessLogTest — 커서로 이어 받아도 겹치거나 빠지지 않음)
- [x] 거른 주소를 새로 고쳐도 같은 결과다 (access-screen.test · access-log.test — 주소의 조건이 그대로 요청 · CSV 주소가 된다)
- [x] CSV 가 거른 결과와 같고, 내려받기가 감사에 남는다 (McpAccessLogTest)
- [x] 다른 워크스페이스 기록이 나오지 않는다 (McpAccessLogTest — 두 워크스페이스에서 같은 키)
- [ ] ego-browser 로 데스크톱 · 390px · 다크 · 영어를 확인했다


<a id="fr-mcp-011"></a>
### FR-MCP-011 MCP 첨부 — 일회용 티켓으로 REST 올리기 · 받기

| 항목 | 내용 |
|---|---|
| 우선순위 | M |
| 진행 | 일부 — 2026-11-22: V10 `attachment_ticket` · MCP 도구 넷(`list_attachments` · `request_attachment_upload` · `request_attachment_download` · `delete_attachment`) · `/mcp/files/upload` · `download`(web-backend 중계) · 자동 테스트 4건 · 실제 11002 로 올리고 받기. 남은 것: 지난 티켓 정리 |
| 관련 설계 | [MCP 설계 — 첨부 티켓](../design/06-mcp.md#mc-attach-ticket) |

**설명** — 사용자(2026-11-22): "첨부파일은 REST API 로 구현해야 하지? MCP 로는 안 될 거야? 그래서 아마 임시 티켓을 받아 쓰고 쓰면 바로 익스파이어 되는 티켓 시스템으로 하는 게 좋을 거야" · "덧글, 첨부파일 포함". 파일 내용은 MCP 메시지에 싣지 않는다.

**세부 요구사항**
1. MCP 도구가 일회용 티켓을 준다 — 원문 `mwt_<43자>` 는 응답에만, DB 에는 SHA-256 만. 10분, 한 번만(원자적).
2. 티켓은 키 주인 · 키 · 워크스페이스 · 프로젝트 · 일감에 묶이고, 올리기는 파일 이름 · 크기(알린 크기를 넘으면 413), 받기는 첨부 id 에 묶인다.
3. 파일은 web-backend `/mcp/files/upload`(PUT, 본문 = 파일) · `/mcp/files/download`(GET)로, `Authorization: Bearer <티켓>` 만으로 보낸다.
4. 쓸 때 다시 판정한다 — 키가 아직 쓸 수 있는가(아니면 403 `MCP_KEY_NOT_USABLE`), 키 주인이 지금 그 권한(올리기 `issue.comment` · 받기 `issue.read`)을 가졌는가. 쓴 · 지난 · 틀린 · 종류가 다른 티켓은 401 `TICKET_INVALID`.
5. 올린 첨부의 경로는 `MCP`, 한 사람은 키 주인이다.

**수용 기준**
- [x] 같은 티켓으로 두 번 올리면 두 번째는 401 이다 (AttachmentTicketTest 2026-11-22 · 실제 11002)
- [x] 알린 크기를 넘는 본문은 413, 올리기 티켓으로 받을 수 없다 (AttachmentTicketTest)
- [x] 티켓을 받은 뒤 키를 만료시키면 티켓도 못 쓴다 (AttachmentTicketTest)
- [x] 받기 티켓으로 받은 바이트가 올린 것과 같다 (실제 11002 · MinIO)


<a id="fr-mcp-012"></a>
### FR-MCP-012 도구만 보고 일감을 바르게 — 결과의 길잡이 · 연결 안내 핵심 규칙

| 항목 | 내용 |
|---|---|
| 우선순위 | M |
| 진행 | 완료 (2026-11-24) — 2026-11-24(일감 #182): `create_issue` · `update_issue` · `get_issue` 결과 맨 위 `summary`(핵심 값) · `checks`(빠진 것 — 막지 않는다, 열 가지) · `next`(다음 할 일 1~3줄), `create_issue` · `update_issue` 는 설명 · 이력을 빼고 `omitted`(`include` 로 받는다), `log_work` 의 `checks`(`NOTE_TIME_IN_FUTURE` · `HOURS_OVER_ELAPSED`) · `next` · `status` · `rowVersion`, `update_issue` 의 `reason`(보류 · 취소의 까닭 — `comment` 의 다른 이름), 도구 설명에 상태 일곱의 뜻(완료 · 종료, 취소 · 지우기의 차이) · agentSession 뜻 · 단계별 날짜 · 결과 모양, 연결 안내에 핵심 규칙(2,000자 안). 자동 테스트(McpGuideTest 9 · McpInstructionsTest 5 · SkillTest). 2026-11-24(일감 #199, 써 보기 2회차 — 스킬 없이 MCP 만 받은 Agent 가 요청 여덟 가지를 바르게 해냄): `search_issues` 줄마다 `rowVersion` · `statusChangedAt`, `update_issue` 의 `moveItems`(확인 못 한 체크 항목을 새 일감으로 넘기기 — 줄 끝 ` → #번호`, 넘긴 줄은 `UNCHECKED_ITEMS` 에서 빠짐, `MOVE_TARGET_NOT_FOUND`), `HOURS_OVER_ELAPSED` 를 구간 합(`statusChangedAt` 부터)으로 셈 · 사람이 한 시간 표시와 맞는 근거 시각이면 INFO, `STARTED_WITHOUT_IN_PROGRESS`(등록 일감에 시간을 적거나 등록에서 바로 테스트 · 완료), "정리해 줘" 는 취소라는 줄(`delete_issue` · `update_issue` 설명). 자동 테스트(IssueGuideLogTest 8 · DescriptionEditTest +3 · McpGuideTest +4). 2026-11-24(일감 #204, 써 보기 3회차 — 스킬 없이 MCP 만 받은 새 Agent 가 모두 바르게 해내 남은 `checks` 가 없었다 → 마지막 수용 기준 확인): 그 Agent 가 짚은 넷 — `log_work` 의 `workDate` 를 선택으로(비우면 오늘, 한국 시간), MCP 결과 · `checks` 메시지의 시각을 모두 `serverTime` 과 같은 한국 시간 ISO(`+09:00`)로(REST 는 UTC 그대로), `UNCHECKED_ITEMS` · `next` 가 떼어 낼 일감 만들기(`create_issue(project, type, parent=<이 번호>) → update_issue(moveItems)`)를 한 줄로, 사람의 시간 칸 `byHuman`(V27 — 웹 기록은 늘 사람, MCP 는 인자 · 비우면 note 의 표시, `summary.spentHoursHuman`, 구간 합에서 뺌). 자동 테스트(McpGuideTest +3 · IssueGuideLogTest +2 · IssueRevisionTest). 2026-11-25(일감 #275, 써 보며 찾은 넷): `WORK_LOG_MISSING` 은 36초(0.01 시간)보다 짧은 구간에는 없고, 그 구간의 시간을 바로 아래 하위 일감에 적었으면 대신 `WORK_LOG_ON_CHILD`(INFO — 하위 번호 · 합만), `NOTE_TIME_IN_FUTURE` 는 분 단위(지금의 시:분보다 늦은 분 — 같은 분은 괜찮고 여유는 없앰, 자정을 넘는 구간은 지금에 따라 시작이 어제거나 끝이 내일)이고 근거 시각의 끝도 같은 규칙, 테스트의 `next` 에 확인요청(`REVIEW`) 길. 자동 테스트(IssueGuideTimeTest 6 · McpGuideTest +1). 2026-11-25(일감 #282, 결과 · 도구 정의를 작게): 빈 값(null · 빈 목록 · false)은 싣지 않고(남기는 칸 — `checks` · `next` · `items` · `hasNext` · `active` · `truncated` · 목록 도구의 목록 · 답인 참거짓), 사람은 아이디, 일감 본문은 `summary` 와 겹치는 칸 · `templateCheck`(→ `TEMPLATE_SECTIONS_MISSING`)를 다시 싣지 않고, 이력의 설명 바뀜은 글자 수(`include=["journalDescription"]` 로 글) · `get_issue` 는 최근 20줄(`journalCount` · `include=["journal"]`), 찾기 줄의 프로젝트 · 모듈은 key, 절 바꾸기의 옛 본문은 크게 지웠을 때만, 상태의 뜻은 `update_issue` 한 곳 — tools/list 38,872 → 26,064자(−33%) · 바쁜 일감 `get_issue` 59,679 → 8,607자 · 찾기 30줄 22,028 → 12,351자. 자동 테스트(McpResultsTest 6 · McpResultShapeTest 4 · McpResultSizeTest 1). |
| 관련 설계 | [MCP 설계 §8.25 결과의 길잡이](../design/06-mcp.md#mc-t-guide) · [§8.24 연결 안내](../design/06-mcp.md#mc-instructions) |

**설명** — 사용자(2026-11-24): "에이전트가 mcp 툴 스키마, 설명, 툴명 보고 이슈 잘 관리할 수 있는 구조인지 딥하게 고민하고, 도저히 안 되는 건 스킬로 보완하는 거지 그치?" · "그렇다면 스킬도 그에 맞게 작성되어야 하고". 스킬 없이 도구만 받은 Agent 로 써 본 결과(세션 이름에 프로젝트 key · 결과가 커서 핵심 값을 찾기 어려움 · 완료할 때 남은 체크를 모름 · 까닭 인자 이름이 도구마다 다름 · 완료 · 종료와 취소 · 지우기의 차이가 짧음)를 고친다. 원칙: 서버가 알 수 있는 것은 서버가 알리고(막지 않는다), 설명할 수 있는 것은 도구 설명 · 연결 안내가 설명하고, 남은 일하는 습관만 스킬에 둔다.

**세부 요구사항**
1. `create_issue` · `update_issue` 결과는 `summary` → `checks` → `next` → (`descriptionEdit`) → 일감의 나머지 값(`summary` 에 있는 칸은 빼고) 차례여야 한다. 설명 · 작업 시간 · 이력은 기본으로 빼고 `omitted` 에 적고, `include=["description","workLogs","journal","journalDescription"]` 으로 준다(모르는 값은 400 `INVALID_ARGUMENT`). `get_issue` 는 맨 앞에 같은 세 칸이 있고 설명 · 작업 시간 · 최근 이력 20줄(`journalCount` 가 전체 수 — 모두는 `include=["journal"]`)을 준다. 빠진 템플릿 항목은 `checks` 의 `TEMPLATE_SECTIONS_MISSING` 하나로 알린다(2026-11-25, 일감 #282 — 전에는 `templateCheck` 로도 되풀이했다).
2. `checks` 는 `{code, level, message, items?}` 이고 쓰기를 막지 않아야 한다. 채울 것은 열린 일감 · 완료에만 묻는다(종료 · 취소에는 묻지 않는다). 사용자 글(체크 목록 줄 · 템플릿 제목)은 `message` 가 아니라 `items` 에 한 줄로 펴서 자른다.
3. 이번 호출이 진행 · 테스트에서 다른 상태로 옮겼는데 키 주인이 그 구간에 작업 시간을 적지 않았으면 `WORK_LOG_MISSING` 이 그 구간의 시작 · 끝(한국 시간)을 알려야 한다. 구간이 36초(0.01 시간)보다 짧으면 알리지 않고, 키 주인이 그 구간에 바로 아래 하위 일감에 적었으면 WARN 대신 `WORK_LOG_ON_CHILD`(INFO)가 하위 번호와 시간의 합만 알려야 한다. 테스트의 `next` 는 관리자의 확인이 필요하면 확인요청(`REVIEW`, 까닭 = 무엇을 확인할지)으로 옮기고 기다리라고 알려야 한다(2026-11-25, 일감 #275).
4. `log_work` 는 오늘 기록의 note 에 지금의 시:분보다 늦은 시각이 있으면(같은 분은 괜찮다 — 2026-11-25 일감 #275 부터 분 단위) `NOTE_TIME_IN_FUTURE`, 키 주인이 오늘 날짜로 이 구간(`statusChangedAt` 뒤)에 적은 시간의 합이 `statusChangedAt` 부터 지금보다 길면 `HOURS_OVER_ELAPSED` 를 알려야 한다 — 앞 기록을 새 구간의 시작으로 보지 않고, 스스로 근거가 있는 앞 기록(사람 표시 · 맞는 근거 시각)은 합에서 뺀다. 이번 기록의 note 에 사람이 한 시간이라는 표시(`사용자가 말한` · `사람이 한` · `reported`)나 지금 앞의 근거 시각(`HH:MM → HH:MM`, 합이 적은 시간 이상)이 있으면 WARN 이 아니라 INFO 다(2026-11-24, 일감 #199).
5. `update_issue` 는 보류 · 취소의 까닭을 `reason` 으로도 받아야 한다(`comment` 와 같다 — 둘 다 주면 이어 붙인다).
6. 일감 입구의 연결 안내는 핵심 규칙(상태마다 넣을 것 · 까닭 · 구간마다 작업 시간 · 끝내기 · 종료는 사람)을 2,000자 안에 담아야 한다. REST 결과는 바뀌지 않는다.
7. `search_issues` 결과 줄마다 `rowVersion` · `statusChangedAt` 이 있어야 한다 — `rowVersion` 을 얻으려고 `get_issue` 를 부르지 않게(2026-11-24, 일감 #199).
8. 체크하지 않은 체크 목록 줄의 글이 넘김 표시(`→ #N` · `-> #N` · `(#N 로 넘김)` · `(moved to #N)`)로 끝나면 남은 줄(`UNCHECKED_ITEMS`)로 세지 않아야 한다. `update_issue` 의 `moveItems=[{item, to}]` 는 맞는 체크하지 않은 줄 끝에 ` → #to` 를 붙이고(맞추기는 `checkItems` 와 같다, 없으면 `CHECK_ITEM_NOT_FOUND`), `to` 는 같은 워크스페이스의 읽을 수 있는 일감이어야 한다(아니면 `MOVE_TARGET_NOT_FOUND`). `UNCHECKED_ITEMS` 의 메시지와 `next` 가 이 길을 알린다.
9. 등록(`NEW`) 일감에 `log_work` 를 하거나 `update_issue` 가 등록에서 바로 테스트 · 완료로 옮기면 `STARTED_WITHOUT_IN_PROGRESS`(WARN)가 일을 시작할 때 진행 + 시작일로 옮기라고 알려야 한다. 등록에서 보류 · 취소는 알리지 않는다. (2026-11-25 일감 #272: 손대기 전 상태는 등록 · 요구사항 확인요청 · 요구사항 확인완료, 건너뛴 곳은 테스트 · 확인요청 · 완료 — 관리자 확인 상태와 `next` 는 [FR-ISS-005](03-issue.md#fr-iss-005).)
10. `log_work` 설명은 사용자가 알려 준 사람의 시간을 note 에 "사용자가 말한 시간" 으로 밝혀 적으라고, `delete_issue` · `update_issue` 설명은 "정리해 줘" · "치워 줘" 가 취소(+ 까닭)이고 지우기는 사용자가 분명히 시킬 때만이라고 알려야 한다.
11. `log_work` 의 `workDate` 는 선택이어야 한다 — 비우면 오늘(한국 시간), 앞날은 400 `INVALID_WORK_DATE`. 입력 틀의 `required` 와 설명이 이를 알린다(2026-11-24, 일감 #204).
12. MCP 결과의 시각(`summary` · `get_issue` · `search_issues` 줄 · `log_work` · 댓글 · 첨부 · 프로젝트 · 스킬 등)과 `checks` 메시지 안의 시각은 모두 `serverTime` 과 같은 한국 시간 ISO(`yyyy-MM-ddTHH:mm:ss.SSS+09:00`)여야 한다. REST 는 UTC 그대로다(일감 #204).
13. `UNCHECKED_ITEMS` 메시지와 `next`(완료에서 남은 체크가 있을 때 · 테스트)는 떼어 낼 일감을 만드는 법 — 같은 프로젝트 · 맞는 유형 · `parent=<이 번호>` 로 `create_issue`, 그다음 `moveItems=[{item, to}]` — 을 한 줄로 알려야 한다(일감 #204).
14. 작업 시간 기록마다 사람이 한 시간인지(`byHuman`)가 남아야 한다 — 웹 기록은 늘 사람, MCP `log_work` 는 `byHuman` 인자(비우면 note 의 사람 표시). 한 번 정하면 바뀌지 않고(보호 트리거), `get_issue` 의 `workLogs[].byHuman` · `summary.spentHoursHuman`(지우지 않은 사람의 시간 합)으로 보이며, 사람의 시간은 `HOURS_OVER_ELAPSED` 의 구간 합에 넣지 않는다(일감 #204).

**수용 기준**
- [x] 결과 맨 위가 `summary` 이고 `checks` · `next` 가 이어지며, 설명 · 이력은 `include` 로만 온다 · `get_issue` 는 전체 (`McpGuideTest` 2026-11-24)
- [x] 검사 열 가지(세션 · 기한 · 시작일 · 예정일 · 예상 공수 · 모듈 · 작업 시간 · 결과 절 · 남은 체크 · 템플릿 항목)가 빠지면 나오고 채우면 사라진다 · 종료 · 취소에는 없다 (`McpGuideTest`)
- [x] `log_work` 의 앞날 시각 · 구간보다 긴 시간, `next` · `rowVersion` (`McpGuideTest`)
- [x] `reason` 만 · `comment` 와 같은 글 · 다른 글 · 둘 다 없으면 `STATUS_REASON_REQUIRED` (`McpGuideTest`)
- [x] 연결 안내에 핵심 규칙이 있고 2,000자를 넘지 않으며 프로젝트 관리 입구에는 없다 (`McpInstructionsTest` · `SkillTest`)
- [x] `search_issues` 줄의 `rowVersion` · `statusChangedAt` 이 `get_issue` 와 같고 그 `rowVersion` 으로 바로 고친다 (`McpGuideTest` 2026-11-24, 일감 #199)
- [x] `moveItems` 가 줄 끝에 ` → #번호` 를 붙이고 넘긴 줄은 `UNCHECKED_ITEMS` · `checkItems: "all"` 에서 빠진다 · 없는 · 읽을 수 없는 · 자기 일감과 체크한 줄은 오류이고 아무것도 바꾸지 않는다 (`DescriptionEditTest` · `McpGuideTest`)
- [x] 한 구간을 여러 줄로 나눠 적어도 `HOURS_OVER_ELAPSED` 가 잘못 나오지 않고, 사람 표시 · 맞는 근거 시각이면 INFO (`IssueGuideLogTest` · `McpGuideTest`)
- [x] 등록 일감의 `log_work` · 등록에서 바로 테스트 · 완료는 `STARTED_WITHOUT_IN_PROGRESS`, 보류 · 취소 · 진행은 아니다 (`IssueGuideLogTest` · `McpGuideTest`)
- [x] 스킬 없이 도구만 받은 Agent 가 등록 → 진행 → 보류 → 완료 → 시험 일감 취소를 `checks` 없이 끝낸다(써 보기 3회차 2026-11-24, 일감 #204 — 남은 `checks` 없음)
- [x] `log_work` 는 `workDate` 없이 오늘로 적고 `null` 도 같으며 앞날 · 틀린 꼴은 그대로 오류, 입력 틀의 `required` 에 `workDate` 가 없다 (`McpGuideTest` 2026-11-24, 일감 #204)
- [x] get_issue · summary · log_work · search_issues 줄 · 댓글 · 프로젝트의 시각과 checks 메시지의 시각이 한국 시간 ISO 이고 글로 견줘도 차례가 맞으며 REST 는 UTC 의 같은 때 (`McpGuideTest` · `IssueGuideLogTest` · `WorkLogMcpTest`)
- [x] `UNCHECKED_ITEMS` 메시지 · 완료와 테스트의 `next` 에 `create_issue(project, type, parent) → update_issue(moveItems)` 한 줄 (`McpGuideTest`)
- [x] `byHuman` 인자 · note 표시 · 준 `false` · 웹 기록이 `workLogs[].byHuman` · `spentHoursHuman` 에 맞게 나오고, 지운 기록은 빠지며, 저장된 값이 note 보다 앞서고, 트리거가 바꾸지 못하게 한다 (`McpGuideTest` · `IssueGuideLogTest` · `IssueRevisionTest`)
- [x] 00:29:07 에 note 의 00:29 는 괜찮고 00:30 은 `NOTE_TIME_IN_FUTURE` · 자정을 넘는 구간(00:29 의 23:40 → 00:29 는 괜찮고 23:59 의 23:30 → 00:00 은 앞날) · 근거 시각의 끝도 같은 규칙 (`IssueGuideTimeTest` 2026-11-25, 일감 #275)
- [x] 36초보다 짧은 구간은 `WORK_LOG_MISSING` 이 없고 36초부터는 있다 · 그 구간에 하위 일감에 적었으면 `WORK_LOG_ON_CHILD`(INFO, 하위 번호 · 합만, 남의 · 구간 앞 기록은 빼고) · 테스트의 `next` 에 확인요청 (`IssueGuideTimeTest` · `McpGuideTest` 2026-11-25, 일감 #275)

---

**관련 문서:** [MCP 설계](../design/06-mcp.md) · [일감 요구사항](03-issue.md) · [프로젝트 요구사항](02-project.md) · [워크스페이스 · 계정 요구사항](01-workspace.md) · [요구사항 전체 목록](00-overview.md)

[← 이전: 일감 요구사항](03-issue.md) | [다음: 비기능 요구사항 →](05-non-functional.md)
