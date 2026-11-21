[홈](../README.md) › [계획](01-roadmap.md) › 테스트 전략

# 테스트 전략

무엇을 어떻게 시험하고, 요구사항의 수용 기준을 언제 "확인함"으로 체크하는지 정한다.

**바로가기:** [층](#ts-layers) · [꼭 있어야 하는 시험](#ts-must) · [MCP 시험](#ts-mcp) · [수용 기준 체크](#ts-acceptance) · [데이터](#ts-data)

---

<a id="ts-layers"></a>
## 1. 층

| 층 | 도구 | 무엇을 | 어디서 |
|---|---|---|---|
| api 자동 테스트 | JUnit 5 · Spring Boot Test · MockMvc(`@ApiTest` · `TestApi`) | 접근 규칙 · 권한 · 업무 규칙 · 이력 · 감사. Keycloak 은 가짜(`FakeKeycloak`), 토큰은 시험용 키, 저장소는 메모리(`MemoryObjectStore`) | `./mvnw -q package` — MOAI-DB 의 시험 스키마 `moai_works_test` 를 매번 다시 만든다 |
| 화면 자동 테스트 | Vitest · Testing Library | 표시용 규칙 사본(`role-rules.ts`) · 상태 표시 · 다국어 리소스 | `npm test` |
| 실제 흐름 | ego-browser(화면) · curl · Claude Code(MCP) | 로그인 · 세션 · CSRF · 화면 반응형(390px · 다크 · 영어) · MCP 연결 | 서비스를 띄워서 |

2026-11-21 기준 api 66건(토큰 · 접근 · 멤버십 · 역할 · 초대 · 가입 신청 · 팀 워크스페이스 · 권한 카탈로그) · 화면 60건이 돈다.

<a id="ts-must"></a>
## 2. 꼭 있어야 하는 시험

새 엔드포인트 · 권한 규칙을 만들면 **같은 작업에서** 아래를 더한다(moai-wiki 와 같은 규칙).

| 종류 | 확인 |
|---|---|
| 워크스페이스 경계 | 다른 워크스페이스의 멤버 · 비멤버가 부르면 404 |
| 프로젝트 경계 | `RESTRICTED` 프로젝트의 독자가 아니면 404(존재를 숨김), 독자인데 권한이 없으면 403 |
| 모듈 꺼짐 | 꺼진 모듈의 경로는 막힘(화면 · REST · MCP 모두) |
| 권한 상승 | 자기 권한 밖의 역할 · 코드를 줄 수 없음 |
| 워크플로 | 허용되지 않은 상태 전이 · 읽기 전용 필드 · 빠진 필수 필드는 거절 |
| 비공개 | 비공개 일감 · 비공개 메모는 권한 없는 사람에게 목록 · 검색 · 알림 · MCP 결과 어디에도 안 나옴 |
| 이력 · 감사 | 쓰기마다 저널 · 감사가 남고 행위자(사람 · Agent) · 경로(웹 · REST · MCP)가 맞음 |
| 낙관적 잠금 | 옛 `lock_version` 으로 쓰면 409 |

<a id="ts-mcp"></a>
## 3. MCP 시험

| 종류 | 확인 | 어떻게 |
|---|---|---|
| 동등성 | 같은 주체가 같은 일을 웹(REST)과 MCP 도구로 했을 때 결과 · 판정 · 이력이 같다 | api 자동 테스트에서 MCP 서버를 직접 부른다(서비스 계층 같은 길) |
| 범위 | 엔드포인트 대상 밖의 프로젝트 · 꺼진 도구 · 다른 엔드포인트의 키는 거절 | 자동 테스트 |
| 키 | 폐기 · 만료 · 멤버가 나간 개인 키 · 정지한 Agent 계정은 거절 | 자동 테스트 |
| 안전장치 | 멱등 키 같은 요청 두 번 → 한 번만 처리, 속도 제한, 응답 크기 | 자동 테스트 |
| 실물 | Claude Code 로 붙여 `tools/list` → 시나리오 UC-05 · UC-06([개요 §6](../01-overview.md#6-핵심-시나리오)) | 사람이 띄워서 |

<a id="ts-acceptance"></a>
## 4. 수용 기준 체크

- 요구사항의 수용 기준은 **자동 테스트나 실제 화면 · 흐름으로 확인한 것만** `- [x]` 로 바꾼다([요구사항 개요 §6](../requirements/00-overview.md#6-진행-관리)).
- 토대(FR-WSP)는 moai-wiki 에서 확인한 코드지만, moai-works 에서는 자동 테스트가 다루는 기준만 체크했다. 나머지는 moai-works 렐름에서 실제 흐름으로 확인한 뒤 체크한다.
- 실패한 검사는 실패라고, 건너뛴 검사는 건너뛰었다고 적는다.

<a id="ts-data"></a>
## 5. 데이터

- 개발 계정 · 워크스페이스: `infra/dev-seed/seed_workspaces.py`(다시 돌려도 안전). 프로젝트 · 일감 시드는 2단계에서 더한다.
- 규모 시험(6단계): 워크스페이스 1천 · 프로젝트 1만 · 일감 100만에서 목록 · 필터 · 검색 · MCP `search_issues` 응답([NFR](../requirements/13-non-functional.md)). Keycloak 조직 그룹 수(프로젝트 × 역할)도 잰다([ADR-006](../design/00-key-decisions.md#adr-006)).

---

**관련 문서:** [로드맵](01-roadmap.md) · [요구사항 개요](../requirements/00-overview.md) · [api 모듈](../modules/01-api.md) · [SECURITY.md](../../SECURITY.md)

[← 이전: 로드맵](01-roadmap.md) | [다음: WBS 한눈에 →](../wbs/00-overview.md)
