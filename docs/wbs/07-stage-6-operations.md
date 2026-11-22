[홈](../README.md) › [WBS](00-overview.md) › 6단계 운영

# 6단계 — 운영

규모를 재고, 운영할 준비를 하고, Redmine 에서 옮겨 온다. 운영 보안 · 운영 준비는 단계 사이사이에 끼워 넣어도 된다(moai-wiki 의 6단계와 같다).

**완료 기준:** 규모 시험 보고서가 있고(목표 규모에서 목록 · 필터 · MCP 응답 · Keycloak 조직 그룹 수), 운영 배치에 맞춘 설정 · 배포 · 관측이 있고, 관리 기능 보안(MFA · 접속 위치 · 비밀값 교체 · 위험 작업 이중 확인)이 되고, Redmine 의 시험 프로젝트를 옮겨 왔다.

| ID | 작업 | 모듈 | 내용 · 완료 기준 | 선행 | 규모 | 상태 | 요구사항 |
|---|---|---|---|---|---|---|---|
| <a id="w-6-1"></a>6.1 | 규모 시험 | api · docs | 워크스페이스 1천 · 프로젝트 1만 · 일감 100만에서 일감 목록 · 필터 · 보기 · MCP `search_issues` 응답 시간을 잰다. 프로젝트 역할의 정본이 Keycloak 조직 그룹이라 그룹 수(프로젝트 × 역할)도 잰다([ADR-006](../design/00-key-decisions.md#adr-006)). 보고서를 남긴다 | [3.4](04-stage-3-issue.md#w-3-4) · [4.3](05-stage-4-mcp.md#w-4-3) | M | 할 일 | [NFR-SCL-001](../requirements/05-non-functional.md#nfr-scl-001) · [NFR-PERF-001](../requirements/05-non-functional.md#nfr-perf-001) · [NFR-PERF-002](../requirements/05-non-functional.md#nfr-perf-002) |
| <a id="w-6-2"></a>6.2 | 운영 준비 · 배포 | api · web-backend · infra | 관측(요청 로그 · MCP 호출 지표) · 여러 인스턴스(로그인 · 가입 시도 제한을 Redis 로) · 설정 · 배포 · 백업. 운영 배치는 [D-07](08-decisions.md#d-07) | [6.1](07-stage-6-operations.md#w-6-1) · [D-07](08-decisions.md#d-07) | M | 할 일 | [NFR-OBS-001](../requirements/05-non-functional.md#nfr-obs-001) · [NFR-AVL-001](../requirements/05-non-functional.md#nfr-avl-001) · [NFR-DEP-001](../requirements/05-non-functional.md#nfr-dep-001) |
| <a id="w-6-3"></a>6.3 | 운영 보안 | api · infra | 관리 기능 MFA · 접속 위치 · 전송 · 저장 암호화 · 비밀값 교체 · 위험 작업 이중 확인 · 탈퇴와 개인정보 | [1.3](02-stage-1-foundation.md#w-1-3) | M | 할 일 | [FR-WSP-025](../requirements/01-workspace.md#fr-wsp-025) · [FR-WSP-026](../requirements/01-workspace.md#fr-wsp-026) · [FR-WSP-027](../requirements/01-workspace.md#fr-wsp-027) · [FR-WSP-028](../requirements/01-workspace.md#fr-wsp-028) · [FR-WSP-029](../requirements/01-workspace.md#fr-wsp-029) |
| <a id="w-6-4"></a>6.4 | Redmine 이관 | api · web-frontend | Redmine REST · API 키로 프로젝트 · 일감 · 이력을 옮겨 온다: 트래커 → 일감 유형, 상태 → 상태 넷(닫힘 여부로), 담당자 → 담당자, 작성자 → 관리자, 시작일 · 완료 기한 → 같은 날짜, 상위 일감 · 관계 → 상위 · 연결 일감(같은 프로젝트 안). 사람은 이메일로 잇고, 번호 대응표를 남기고, 다시 돌려도 안전하게. 나중 후보라 시작 전에 요구사항을 쓴다 | [3.3](04-stage-3-issue.md#w-3-3) | L | 할 일 |  |

---

**관련 문서:** [WBS 한눈에](00-overview.md) · [비기능 요구사항](../requirements/05-non-functional.md) · [테스트 전략](../plan/02-test-strategy.md) · [정해야 할 것](08-decisions.md) · [로드맵](../plan/01-roadmap.md)

[← 이전: 5단계 — 협업 (나중 후보에서 고르기)](06-stage-5-collaboration.md) | [다음: 정해야 할 것 →](08-decisions.md)
