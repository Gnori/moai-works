[홈](../README.md) › [WBS](00-overview.md) › 3단계 MCP

# 3단계 — MCP (바깥 AI Agent 연결)

바깥 AI Agent 가 MCP 로 붙어 일감을 찾고 · 읽고 · 만들고 · 고치고 · 메모한다. 이 제품의 이유라 2단계의 서비스가 서자마자 한다([ADR-003](../design/00-key-decisions.md#adr-003)). 도구는 2단계 서비스를 부르는 얇은 층이다([설계](../design/07-mcp-agents.md)).

**완료 기준:** 관리자가 엔드포인트 · Agent 계정 · 서비스 키를 만들고, Claude Code 를 붙여 [UC-05 · UC-06](../01-overview.md#6-핵심-시나리오)을 시연한다. 웹과 MCP 의 동등성 · 범위 · 키 · 안전장치 테스트가 돈다.

| ID | 작업 | 모듈 | 내용 · 완료 기준 | 선행 | 규모 | 상태 | 요구사항 |
|---|---|---|---|---|---|---|---|
| <a id="w-3-1"></a>3.1 | Agent 계정 | api · web-frontend | `agent_account` · 만들기 · 책임자 · 정지 · 프로젝트 역할(사본 표의 `AGENT` 줄) · 일감 담당 · 작성 · 관찰 가능 · 화면 표시. 시작 전에 [D-06 · D-08](08-decisions.md#d-06) | [2.1](03-stage-2-project-issue.md#w-2-1) | M | 할 일 | [FR-MCP-005](../requirements/12-mcp.md#fr-mcp-005) · [FR-MCP-006](../requirements/12-mcp.md#fr-mcp-006) · [FR-PRJ-008](../requirements/02-project.md#fr-prj-008) |
| <a id="w-3-2"></a>3.2 | 워크스페이스 MCP 엔드포인트 | api | `mcp_endpoint` · 대상 프로젝트 · 켤 도구 · 한도 · 상태. 라우터 `/mcp/{ws}/{ep}` 와 엔드포인트별 서버 캐시 · 무효화. 고정 입구 `/mcp/project` 를 옮긴다 | [2.1](03-stage-2-project-issue.md#w-2-1) | M | 할 일 | [FR-MCP-001](../requirements/12-mcp.md#fr-mcp-001) · [FR-MCP-033](../requirements/12-mcp.md#fr-mcp-033) |
| <a id="w-3-3"></a>3.3 | MCP 키를 엔드포인트 · Agent 계정에 묶기 | api | 개인 키(사람) · 서비스 키(Agent 계정), 발급을 `@WorkspaceApi(MCP_KEY_ISSUE_*)` 로, 발급 · 폐기 감사, 멤버가 나가면 개인 키 무효. 지금 코드의 발급 API(`@AuthenticatedApi`, 워크스페이스에 안 묶임)를 고친다 | [3.1](04-stage-3-mcp.md#w-3-1) · [3.2](04-stage-3-mcp.md#w-3-2) | M | 할 일 | [FR-MCP-002](../requirements/12-mcp.md#fr-mcp-002) · [FR-MCP-003](../requirements/12-mcp.md#fr-mcp-003) · [FR-MCP-004](../requirements/12-mcp.md#fr-mcp-004) |
| <a id="w-3-4"></a>3.4 | 도구 판정 · 주체 해석 | api | 키 → 주체(사람 · Agent) → 같은 서비스 · 같은 판정 ∩ 엔드포인트 범위. 모델이 넘긴 프로젝트도 다시 판정 | [3.3](04-stage-3-mcp.md#w-3-3) | M | 할 일 | [FR-MCP-007](../requirements/12-mcp.md#fr-mcp-007) |
| <a id="w-3-5"></a>3.5 | 읽기 도구 | api | `whoami` · `list_projects` · `get_project` · `search_issues` · `get_issue` · `list_issue_metadata` | [3.4](04-stage-3-mcp.md#w-3-4) · [2.5](03-stage-2-project-issue.md#w-2-5) | M | 할 일 | [FR-MCP-008](../requirements/12-mcp.md#fr-mcp-008) · [FR-MCP-009](../requirements/12-mcp.md#fr-mcp-009) · [FR-MCP-010](../requirements/12-mcp.md#fr-mcp-010) · [FR-MCP-011](../requirements/12-mcp.md#fr-mcp-011) · [FR-MCP-012](../requirements/12-mcp.md#fr-mcp-012) |
| <a id="w-3-6"></a>3.6 | 쓰기 도구 | api | `create_issue` · `update_issue` · `add_issue_note` · `add_issue_relation` · `remove_issue_relation` | [3.5](04-stage-3-mcp.md#w-3-5) | M | 할 일 | [FR-MCP-016](../requirements/12-mcp.md#fr-mcp-016) · [FR-MCP-017](../requirements/12-mcp.md#fr-mcp-017) · [FR-MCP-018](../requirements/12-mcp.md#fr-mcp-018) · [FR-MCP-019](../requirements/12-mcp.md#fr-mcp-019) |
| <a id="w-3-7"></a>3.7 | 안전장치 | api | 낙관적 잠금 · 멱등 키(`idempotency_record`) · 속도 제한 · 일 한도 · 응답 크기 · 사용자 데이터 표시 · 하지 않는 일(삭제 · 일괄 · 설정 도구 없음) | [3.6](04-stage-3-mcp.md#w-3-6) | M | 할 일 | [FR-MCP-024](../requirements/12-mcp.md#fr-mcp-024) · [FR-MCP-025](../requirements/12-mcp.md#fr-mcp-025) · [FR-MCP-026](../requirements/12-mcp.md#fr-mcp-026) · [FR-MCP-027](../requirements/12-mcp.md#fr-mcp-027) · [FR-MCP-028](../requirements/12-mcp.md#fr-mcp-028) · [FR-MCP-029](../requirements/12-mcp.md#fr-mcp-029) · [NFR-SEC-002](../requirements/13-non-functional.md#nfr-sec-002) |
| <a id="w-3-8"></a>3.8 | instructions · 연결 안내 화면 | api · web-frontend | 서버 instructions(한국어 · 영어), 엔드포인트 화면의 Claude Code 연결 안내(키 값은 예시에 넣지 않는다) | [3.2](04-stage-3-mcp.md#w-3-2) | S | 할 일 | [FR-MCP-023](../requirements/12-mcp.md#fr-mcp-023) · [FR-MCP-032](../requirements/12-mcp.md#fr-mcp-032) |
| <a id="w-3-9"></a>3.9 | 감사 · 호출 기록 · 사용량 | api · web-frontend | `mcp_call_log` · `mcp_usage_daily` · 사용량 화면(`usage.read`) | [3.6](04-stage-3-mcp.md#w-3-6) | M | 할 일 | [FR-MCP-030](../requirements/12-mcp.md#fr-mcp-030) · [FR-MCP-031](../requirements/12-mcp.md#fr-mcp-031) |
| <a id="w-3-10"></a>3.10 | 동등성 시험 · 시연 | api · docs | 웹과 MCP 의 같은 일 → 같은 결과 · 이력 테스트, Claude Code 로 UC-05 · UC-06 시연, 응답 시간 측정 | [3.7](04-stage-3-mcp.md#w-3-7) · [3.8](04-stage-3-mcp.md#w-3-8) | M | 할 일 | [NFR-PERF-002](../requirements/13-non-functional.md#nfr-perf-002) |

---

**관련 문서:** [WBS 한눈에](00-overview.md) · [요구사항 진행 현황](../requirements/14-progress.md) · [정해야 할 것](08-decisions.md) · [로드맵](../plan/01-roadmap.md)

[← 이전: 2단계 — 프로젝트 · 일감 코어](03-stage-2-project-issue.md) | [다음: 4단계 — 계획 · 시간 · 보드 →](05-stage-4-planning-time.md)
