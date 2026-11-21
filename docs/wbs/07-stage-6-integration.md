[홈](../README.md) › [WBS](00-overview.md) › 6단계 연동 · 운영

# 6단계 — 연동 · 운영

다른 도구와 잇고, Redmine 에서 옮겨 오고, 운영할 준비를 한다([설계](../design/09-integrations.md)).

**완료 기준:** Git 웹훅으로 커밋이 일감에 이어지고, 웹훅이 서명과 함께 나가고, Redmine 의 시험 프로젝트를 옮겨 왔다. 규모 시험 보고서가 있다.

| ID | 작업 | 모듈 | 내용 · 완료 기준 | 선행 | 규모 | 상태 | 요구사항 |
|---|---|---|---|---|---|---|---|
| <a id="w-6-1"></a>6.1 | REST 규약 · 문서 · 서비스 계정 | api | problem+json · 쪽 · 정렬 · 잠금 규약 정리, OpenAPI 생성, 바깥 자동화용 Keycloak 서비스 계정(`@ServiceApi`) | [2.3](03-stage-2-project-issue.md#w-2-3) | M | 할 일 | [FR-INT-001](../requirements/11-integration.md#fr-int-001) · [FR-INT-002](../requirements/11-integration.md#fr-int-002) · [FR-INT-003](../requirements/11-integration.md#fr-int-003) |
| <a id="w-6-2"></a>6.2 | 웹훅 | api · web-frontend | 구독 · 이벤트 고르기 · HMAC 서명 · 재시도 · 실패 기록 · 자동 끄기 | [2.3](03-stage-2-project-issue.md#w-2-3) | M | 할 일 | [FR-INT-005](../requirements/11-integration.md#fr-int-005) · [FR-INT-006](../requirements/11-integration.md#fr-int-006) · [FR-INT-007](../requirements/11-integration.md#fr-int-007) |
| <a id="w-6-3"></a>6.3 | Git 저장소 연동 | api · web-frontend | GitHub · GitLab · Gitea 웹훅 받기 · 서명 검증 · 커밋 저장 · `refs #123` · `fixes #123` · `@2h` · PR/MR 잇기 | [2.3](03-stage-2-project-issue.md#w-2-3) · [4.2](05-stage-4-planning-time.md#w-4-2) | L | 할 일 | [FR-INT-008](../requirements/11-integration.md#fr-int-008) · [FR-INT-009](../requirements/11-integration.md#fr-int-009) · [FR-INT-010](../requirements/11-integration.md#fr-int-010) · [FR-INT-011](../requirements/11-integration.md#fr-int-011) · [FR-INT-012](../requirements/11-integration.md#fr-int-012) · [FR-INT-013](../requirements/11-integration.md#fr-int-013) |
| <a id="w-6-4"></a>6.4 | CSV 일감 가져오기 | api · web-frontend | 열 맞추기 · 미리보기 · 오류 줄 | [2.3](03-stage-2-project-issue.md#w-2-3) | M | 할 일 | [FR-INT-015](../requirements/11-integration.md#fr-int-015) |
| <a id="w-6-5"></a>6.5 | Redmine 이관 | api · web-frontend | Redmine REST · API 키 · 대응 설정 · 데이터 옮기기 · 번호 대응표 · 다시 돌려도 안전 | [4.2](05-stage-4-planning-time.md#w-4-2) · [5.1](06-stage-5-collaboration.md#w-5-1) | L | 할 일 | [FR-INT-016](../requirements/11-integration.md#fr-int-016) · [FR-INT-017](../requirements/11-integration.md#fr-int-017) · [FR-INT-018](../requirements/11-integration.md#fr-int-018) |
| <a id="w-6-6"></a>6.6 | 규모 시험 | api · docs | 워크스페이스 1천 · 프로젝트 1만 · 일감 100만에서 목록 · 필터 · 검색 · MCP 응답, Keycloak 조직 그룹 수 | [2.5](03-stage-2-project-issue.md#w-2-5) · [3.5](04-stage-3-mcp.md#w-3-5) | M | 할 일 | [NFR-SCL-001](../requirements/13-non-functional.md#nfr-scl-001) · [NFR-PERF-001](../requirements/13-non-functional.md#nfr-perf-001) |
| <a id="w-6-7"></a>6.7 | 운영 준비 | api · web-backend · infra | 관측(요청 로그 · 작업 큐 · MCP 지표) · 여러 인스턴스 · 설정 · 배포. 운영 배치는 [D-10](08-decisions.md#d-10) | [6.6](07-stage-6-integration.md#w-6-6) | M | 할 일 | [NFR-OBS-001](../requirements/13-non-functional.md#nfr-obs-001) · [NFR-AVL-001](../requirements/13-non-functional.md#nfr-avl-001) · [NFR-DEP-001](../requirements/13-non-functional.md#nfr-dep-001) |
| <a id="w-6-8"></a>6.8 | 운영 보안 | api · infra | 관리 기능 MFA · 접속 위치 · 전송 · 저장 암호화 · 비밀값 교체 · 위험 작업 이중 확인 · 탈퇴와 개인정보 | [1.3](02-stage-1-foundation.md#w-1-3) | M | 할 일 | [FR-WSP-025](../requirements/01-workspace.md#fr-wsp-025) · [FR-WSP-026](../requirements/01-workspace.md#fr-wsp-026) · [FR-WSP-027](../requirements/01-workspace.md#fr-wsp-027) · [FR-WSP-028](../requirements/01-workspace.md#fr-wsp-028) · [FR-WSP-029](../requirements/01-workspace.md#fr-wsp-029) |

---

**관련 문서:** [WBS 한눈에](00-overview.md) · [요구사항 진행 현황](../requirements/14-progress.md) · [정해야 할 것](08-decisions.md) · [로드맵](../plan/01-roadmap.md)

[← 이전: 5단계 — 협업 (위키 · 알림 · 활동 · 검색)](06-stage-5-collaboration.md) | [다음: 정해야 할 것 →](08-decisions.md)
