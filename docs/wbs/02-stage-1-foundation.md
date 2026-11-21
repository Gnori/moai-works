[홈](../README.md) › [WBS](00-overview.md) › 1단계 토대

# 1단계 — 토대 (moai-wiki 에서 가져옴)

moai-wiki 의 계정 · 가입 · 워크스페이스 · 역할 · 멤버십 · 초대 · 감사 · 로그인 · 화면 뼈대를 가져와 이름을 바꾸고 위키 부분을 걷어 냈다([ADR-002](../design/00-key-decisions.md#adr-002)). 코드와 자동 테스트는 끝났고, moai-works 렐름에서 실제 흐름을 확인하는 일이 남았다.

**완료 기준:** moai-works 렐름이 있고, 시드 계정으로 가입 → 개인 워크스페이스 → 팀 워크스페이스 → 초대 · 가입 신청 → 역할 부여를 화면에서 확인했다. 워크스페이스 요구사항의 진행 줄에서 "실제 흐름 확인"이 남은 것이 없다.

| ID | 작업 | 모듈 | 내용 · 완료 기준 | 선행 | 규모 | 상태 | 요구사항 |
|---|---|---|---|---|---|---|---|
| <a id="w-1-1"></a>1.1 | 토대 코드 가져오기 | api · web-backend · web-frontend · infra | 2026-11-21 완료: moai-wiki 의 세 모듈과 infra(keycloak · tls · dev-seed · minio) · tools 를 옮겨 이름을 바꿨다(패키지 · 설정 키 · 헤더 · 쿠키 · DB · 렐름 · 버킷 · 포트 — 처음 1001x, 같은 날 사람이 1100x 로 바꿈). 위키 · 페이지 · 자료실 · 내장 agent 를 걷어 냈다. 기본 역할 owner · admin · member, 권한 코드 16개, MCP 입구 `/mcp/project`, Flyway `V1__foundation.sql` 하나 | — | M | 완료 | [FR-WSP-015](../requirements/01-workspace.md#fr-wsp-015) · [FR-WSP-016](../requirements/01-workspace.md#fr-wsp-016) · [FR-WSP-023](../requirements/01-workspace.md#fr-wsp-023) |
| <a id="w-1-2"></a>1.2 | 자동 테스트 옮기기 | api · web-frontend | 2026-11-21 완료: api 66건(토큰 · 접근 · 멤버십 · 역할 · 초대 · 가입 신청 · 팀 워크스페이스 · 권한 카탈로그) · 화면 60건이 통과한다. MOAI-DB 에 DB · 계정 `moai_works` 를 만들고 시험 스키마 `moai_works_test` 를 쓴다 | [1.1](02-stage-1-foundation.md#w-1-1) | S | 완료 | [NFR-QLT-001](../requirements/13-non-functional.md#nfr-qlt-001) |
| <a id="w-1-3"></a>1.3 | 렐름 만들기 · 실제 흐름 확인 | infra · docs | `infra/keycloak/.env` 에 MOAI-Keycloak 관리자 계정 → `init-realm.sh` → 인증서 → api · web-backend 띄우기 → 시드 → ego-browser 로 가입 · 인증 코드 · 로그인 · 워크스페이스 · 초대 · 가입 신청 · 역할 · 시스템 관리자 화면(데스크톱 · 390px · 다크 · 영어). 확인한 수용 기준을 체크한다 | [1.2](02-stage-1-foundation.md#w-1-2) | S | 할 일 | [FR-WSP-001](../requirements/01-workspace.md#fr-wsp-001) · [FR-WSP-002](../requirements/01-workspace.md#fr-wsp-002) · [FR-WSP-003](../requirements/01-workspace.md#fr-wsp-003) · [FR-WSP-004](../requirements/01-workspace.md#fr-wsp-004) · [FR-WSP-005](../requirements/01-workspace.md#fr-wsp-005) · [FR-WSP-007](../requirements/01-workspace.md#fr-wsp-007) · [FR-WSP-008](../requirements/01-workspace.md#fr-wsp-008) · [FR-WSP-009](../requirements/01-workspace.md#fr-wsp-009) · [FR-WSP-010](../requirements/01-workspace.md#fr-wsp-010) · [FR-WSP-011](../requirements/01-workspace.md#fr-wsp-011) · [FR-WSP-012](../requirements/01-workspace.md#fr-wsp-012) · [FR-WSP-013](../requirements/01-workspace.md#fr-wsp-013) · [FR-WSP-019](../requirements/01-workspace.md#fr-wsp-019) · [FR-WSP-022](../requirements/01-workspace.md#fr-wsp-022) · [NFR-USA-001](../requirements/13-non-functional.md#nfr-usa-001) |
| <a id="w-1-4"></a>1.4 | 감사 기록 테스트 | api | 쓰기마다 감사가 남는지(행위자 · 대상 · 워크스페이스) 보는 테스트를 더한다. 지금은 감사 내용을 보는 자동 테스트가 없다 | [1.2](02-stage-1-foundation.md#w-1-2) | S | 할 일 | [FR-WSP-018](../requirements/01-workspace.md#fr-wsp-018) · [FR-WSP-024](../requirements/01-workspace.md#fr-wsp-024) |
| <a id="w-1-5"></a>1.5 | 비밀번호 재설정 · 변경 | api · web-frontend | moai-wiki 에서도 아직 없다. 만들면 moai-wiki 에도 같은 고침이 필요한지 본다 | [1.3](02-stage-1-foundation.md#w-1-3) | M | 할 일 | [FR-WSP-006](../requirements/01-workspace.md#fr-wsp-006) |
| <a id="w-1-6"></a>1.6 | Keycloak 대사(15분) | api | Keycloak 조직 · 그룹 · 멤버와 사본을 비교해 Keycloak 기준으로 고치고 감사에 남긴다(moai-wiki 에서도 아직) | [1.3](02-stage-1-foundation.md#w-1-3) | M | 할 일 | [FR-WSP-017](../requirements/01-workspace.md#fr-wsp-017) |

---

**관련 문서:** [WBS 한눈에](00-overview.md) · [요구사항 진행 현황](../requirements/14-progress.md) · [정해야 할 것](08-decisions.md) · [로드맵](../plan/01-roadmap.md)

[← 이전: 구현한 기능](01-implemented.md) | [다음: 2단계 — 프로젝트 · 일감 코어 →](03-stage-2-project-issue.md)
