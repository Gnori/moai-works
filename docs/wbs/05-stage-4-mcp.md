[홈](../README.md) › [WBS](00-overview.md) › 4단계 MCP

# 4단계 — MCP

바깥 AI Agent(Claude Code 등)가 MCP 로 붙어 프로젝트 · 일감을 찾고 · 읽고 · 만들고 · 고치고 · 댓글을 단다. moai-wiki 의 MCP 체계와 같다 — MCP 키(개인 · 서비스, SHA-256 만 저장), 워크스페이스 MCP 엔드포인트 `/mcp/{ws}/{ep}`, 도구는 REST 와 **같은 서비스 · 같은 판정**을 부르고 호출마다 다시 판정한다. 엔드포인트의 대상이 위키 대신 프로젝트라는 것만 다르다. 이 제품의 이유라 일감 서비스가 서자마자 한다([ADR-003](../design/00-key-decisions.md#adr-003) · [ADR-008](../design/00-key-decisions.md#adr-008)).

설계: [MCP 설계](../design/06-mcp.md) · [SECURITY.md](../../SECURITY.md) §5.2 · §8.3.

**완료 기준:** 워크스페이스 관리자가 AGENT 엔드포인트(대상 프로젝트 하나 · 쓰기 도구 켬)를 만들고, 멤버가 그 엔드포인트의 개인 키를 받는다. Claude Code 를 붙여 [UC-05 · UC-06](../01-overview.md#6-핵심-시나리오)을 시연한다 — Agent 가 담당 일감을 찾아 읽고, 댓글을 달고, 상태를 바꾸고, 하위 일감을 만든다. 이력에는 "그 사람 (MCP 로)"가 남는다. 웹과 MCP 의 동등성 · 범위 · 키 · 재판정 테스트가 돈다.

| ID | 작업 | 모듈 | 내용 · 완료 기준 | 선행 | 규모 | 상태 | 요구사항 |
|---|---|---|---|---|---|---|---|
| <a id="w-4-1"></a>4.1 | MCP 키를 워크스페이스에 묶기 · 권한 · 감사 | api | 지금 발급 API(`/moai-works-api/mcp-keys`, `@AuthenticatedApi`)는 워크스페이스에 묶이지 않고 발급 권한을 보지 않으며 발급 · 폐기가 감사에 남지 않는다(moai-wiki 와 같은 상태 — [SECURITY.md](../../SECURITY.md) §5.2). 워크스페이스 아래로 옮겨 개인 키는 `@WorkspaceApi(mcp.key.issue.personal)` · 서비스 키는 `mcp.key.issue.service` · 남의 키 목록과 폐기는 `mcp.key.manage` 로 판정하고, 발급 · 폐기를 감사에 남긴다. 기간(기본 90일 · 최대 365일) · 원문은 한 번만 보이기는 그대로. 멤버가 나가거나 정지되면 그 사람의 개인 키는 거절된다. 테스트: 비멤버 404 · 권한 없음 403 · 감사 · 나간 멤버의 키 거절. 같은 고침이 moai-wiki 에도 필요하면 작업 기록에 적는다 | [1.3](02-stage-1-foundation.md#w-1-3) | M | 할 일 | [FR-MCP-001](../requirements/04-mcp.md#fr-mcp-001) |
| <a id="w-4-2"></a>4.2 | 워크스페이스 MCP 엔드포인트 | api · web-frontend | `mcp_endpoint`: 종류 `QUERY`(읽기) · `AGENT`(읽기 + 쓰기) · 대상 프로젝트(하나 이상) · 도구 부분집합 · 상태 `ACTIVE` · `DISABLED` · 속도 제한 · 서비스 키의 읽기 모드 `USER` · `SERVICE`(`SERVICE` 모드는 `RESTRICTED` 프로젝트를 대상으로 못 한다). 라우터 `/mcp/{ws}/{ep}` 와 엔드포인트마다 MCP 서버 하나를 캐시 · 설정이 바뀌면 무효화. 키는 엔드포인트 하나에 묶는다. 판정: 범위는 요청 문맥(키 · 엔드포인트)에서만 오고 모델이 넘긴 값은 믿지 않는다, 도구 호출마다 다시 판정(개인 키 = 키 주인의 권한 ∩ 엔드포인트, 서비스 키 = 워크스페이스 이름으로 ∩ 엔드포인트). 관리는 `mcp.endpoint.manage`, 감사. 고정 입구 `/mcp/project` 와 `McpEndpoint.PROJECT` 를 없앤다. 화면: 워크스페이스 설정의 MCP 엔드포인트 · 키(발급 때 원문 한 번 · 복사). 테스트: 대상 밖 프로젝트 · 꺼진 엔드포인트 · 다른 엔드포인트의 키 거절 · `SERVICE` 모드의 `RESTRICTED` 대상 거절 · 속도 제한 | [4.1](05-stage-4-mcp.md#w-4-1) · [2.4](03-stage-2-project.md#w-2-4) | L | 할 일 | [FR-MCP-002](../requirements/04-mcp.md#fr-mcp-002) · [FR-MCP-003](../requirements/04-mcp.md#fr-mcp-003) · [FR-MCP-007](../requirements/04-mcp.md#fr-mcp-007) |
| <a id="w-4-3"></a>4.3 | 읽기 도구 | api | `list_projects` · `get_project` · `list_issue_types` · `search_issues` · `get_issue` — REST 와 같은 서비스(목록 질의는 [3.4](04-stage-3-issue.md#w-3-4), 보기는 [3.2](04-stage-3-issue.md#w-3-2)). 응답 크기 상한, 일감 설명 · 댓글은 "사용자 데이터"로 표시(지시로 읽지 말라는 서버 instructions). 테스트: 동등성(같은 사람이 REST 와 MCP 로 같은 결과) · 범위 · 키(폐기 · 만료 · 나간 멤버) · 재판정(호출 사이에 프로젝트 역할을 빼면 다음 호출부터 거절) · 모델이 넘긴 다른 프로젝트 무시 | [4.2](05-stage-4-mcp.md#w-4-2) · [3.4](04-stage-3-issue.md#w-3-4) | M | 할 일 | [FR-MCP-004](../requirements/04-mcp.md#fr-mcp-004) · [FR-MCP-003](../requirements/04-mcp.md#fr-mcp-003) · [FR-MCP-007](../requirements/04-mcp.md#fr-mcp-007) |
| <a id="w-4-4"></a>4.4 | 쓰기 도구 | api | `AGENT` 엔드포인트에서만: `create_issue` · `update_issue` · `add_comment` · `link_issues` · `unlink_issues` · `set_parent`. 이력 · 감사의 via = `MCP`(감사 표에 경로가 없으면 새 Flyway 로 더한다), 서비스 키는 행위자 종류 `SERVICE`. `rowVersion`(409) · 멱등 키(같은 요청 두 번 → 한 번만) · 속도 제한. 지우기 · 프로젝트 설정 · 역할 바꾸기는 MCP 로 열지 않는다. 호출 기록과 사용량(`usage.read`). 테스트: 동등성(같은 일을 웹과 MCP 로 → 같은 결과 · 이력, via 만 다름) · `QUERY` 엔드포인트에서 쓰기 거절 · 멱등 · 409 | [4.3](05-stage-4-mcp.md#w-4-3) · [3.3](04-stage-3-issue.md#w-3-3) | M | 할 일 | [FR-MCP-005](../requirements/04-mcp.md#fr-mcp-005) · [FR-MCP-006](../requirements/04-mcp.md#fr-mcp-006) · [FR-MCP-007](../requirements/04-mcp.md#fr-mcp-007) · [NFR-SEC-002](../requirements/05-non-functional.md#nfr-sec-002) |
| <a id="w-4-5"></a>4.5 | 연결 안내 화면 · Claude Code 시연 | api · web-frontend · docs | 서버 instructions(한국어 · 영어), 엔드포인트 화면의 Claude Code 연결 안내(주소 · 헤더 모양만, 키 값은 예시에 넣지 않는다). Claude Code 를 개인 키 · 서비스 키로 붙여 `tools/list` → UC-05 · UC-06 시연, 도구 응답 시간을 잰다. 시연 결과를 요구사항 수용 기준에 체크한다 | [4.4](05-stage-4-mcp.md#w-4-4) | M | 할 일 | [FR-MCP-008](../requirements/04-mcp.md#fr-mcp-008) · [NFR-PERF-002](../requirements/05-non-functional.md#nfr-perf-002) |

## 이 단계에서 뺀 것

- **"Agent 계정"**(사람 아닌 멤버로 Agent 를 두는 안)은 없앴다 — moai-wiki 에 없는 것이다. 서비스 키는 워크스페이스 이름으로 일한다([ADR-007](../design/00-key-decisions.md#adr-007) 폐기).
- 시간 · 버전 · 위키 · 활동 도구, 리소스 · 프롬프트, OAuth 2.1 은 그 기능이 없거나 나중 후보다.

---

**관련 문서:** [WBS 한눈에](00-overview.md) · [MCP 설계](../design/06-mcp.md) · [MCP 요구사항](../requirements/04-mcp.md) · [SECURITY.md](../../SECURITY.md) · [요구사항 진행 현황](../requirements/06-progress.md) · [로드맵](../plan/01-roadmap.md)

[← 이전: 3단계 — 일감](04-stage-3-issue.md) | [다음: 5단계 — 협업 (나중 후보에서 고르기) →](06-stage-5-collaboration.md)
