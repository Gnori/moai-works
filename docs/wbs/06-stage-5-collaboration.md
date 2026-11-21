[홈](../README.md) › [WBS](00-overview.md) › 5단계 협업

# 5단계 — 협업 (위키 · 알림 · 활동 · 검색)

팀이 소식을 받고, 찾고, 기록을 남긴다. 알림 · 활동 · 검색은 2 ~ 4단계가 쌓아 둔 도메인 이벤트를 받는다([설계](../design/08-collaboration.md)).

**완료 기준:** 위키 · 화면 알림함 · 메일(로그 모드) · 활동 · 검색 · 내 페이지가 되고, 받는 사람 · 검색 결과에서도 권한 경계가 지켜진다.

| ID | 작업 | 모듈 | 내용 · 완료 기준 | 선행 | 규모 | 상태 | 요구사항 |
|---|---|---|---|---|---|---|---|
| <a id="w-5-1"></a>5.1 | 위키 | api · web-frontend | `wiki` · `wiki_page` · 판 · 차이 · 되돌리기 · 계층 · 이름 바꾸기 · 보호 · 관찰 · 절 고치기 | [2.1](03-stage-2-project-issue.md#w-2-1) | L | 할 일 | [FR-CNT-001](../requirements/08-content.md#fr-cnt-001) · [FR-CNT-002](../requirements/08-content.md#fr-cnt-002) · [FR-CNT-003](../requirements/08-content.md#fr-cnt-003) · [FR-CNT-004](../requirements/08-content.md#fr-cnt-004) · [FR-CNT-005](../requirements/08-content.md#fr-cnt-005) · [FR-CNT-006](../requirements/08-content.md#fr-cnt-006) · [FR-CNT-007](../requirements/08-content.md#fr-cnt-007) · [FR-CNT-015](../requirements/08-content.md#fr-cnt-015) |
| <a id="w-5-2"></a>5.2 | 알림 · 메일 · 화면 알림함 | api · web-frontend | 받는 사람 규칙과 권한 재판정 · 사용자 설정 · 화면 알림함(폴링) · 아웃박스 · `log` 모드 · 스레드 헤더 · 묶어 보내기. SMTP 는 [D-09](08-decisions.md#d-09) | [2.3](03-stage-2-project-issue.md#w-2-3) | L | 할 일 | [FR-NTF-001](../requirements/09-notification.md#fr-ntf-001) · [FR-NTF-002](../requirements/09-notification.md#fr-ntf-002) · [FR-NTF-003](../requirements/09-notification.md#fr-ntf-003) · [FR-NTF-004](../requirements/09-notification.md#fr-ntf-004) · [FR-NTF-005](../requirements/09-notification.md#fr-ntf-005) · [FR-NTF-006](../requirements/09-notification.md#fr-ntf-006) · [FR-NTF-007](../requirements/09-notification.md#fr-ntf-007) · [FR-NTF-008](../requirements/09-notification.md#fr-ntf-008) · [FR-NTF-009](../requirements/09-notification.md#fr-ntf-009) · [FR-NTF-010](../requirements/09-notification.md#fr-ntf-010) |
| <a id="w-5-3"></a>5.3 | 활동 | api · web-frontend | 프로젝트 · 워크스페이스 · 사람별 활동, 행위자로 거르기 | [2.3](03-stage-2-project-issue.md#w-2-3) | M | 할 일 | [FR-ACT-001](../requirements/10-activity-search.md#fr-act-001) · [FR-ACT-002](../requirements/10-activity-search.md#fr-act-002) · [FR-ACT-003](../requirements/10-activity-search.md#fr-act-003) · [FR-ACT-005](../requirements/10-activity-search.md#fr-act-005) |
| <a id="w-5-4"></a>5.4 | 검색 | api · infra | OpenSearch 색인 `moai-works-content` · nori · 색인 갱신(작업 큐) · 다시 만들기 · 권한 필터 · `#123` 바로 가기 | [2.3](03-stage-2-project-issue.md#w-2-3) | L | 할 일 | [FR-ACT-006](../requirements/10-activity-search.md#fr-act-006) · [FR-ACT-007](../requirements/10-activity-search.md#fr-act-007) · [FR-ACT-008](../requirements/10-activity-search.md#fr-act-008) · [FR-ACT-009](../requirements/10-activity-search.md#fr-act-009) |
| <a id="w-5-5"></a>5.5 | 내 페이지 | web-frontend · api | 기본 블록 · 꾸미기 | [5.3](06-stage-5-collaboration.md#w-5-3) | M | 할 일 | [FR-ACT-011](../requirements/10-activity-search.md#fr-act-011) · [FR-ACT-012](../requirements/10-activity-search.md#fr-act-012) |
| <a id="w-5-6"></a>5.6 | 뉴스 · 파일 | api · web-frontend | 뉴스 · 댓글 · 버전에 묶인 내려받기 파일 | [2.4](03-stage-2-project-issue.md#w-2-4) | M | 할 일 | [FR-CNT-010](../requirements/08-content.md#fr-cnt-010) · [FR-CNT-012](../requirements/08-content.md#fr-cnt-012) |
| <a id="w-5-7"></a>5.7 | MCP 도구 — 위키 · 활동 · 리소스 | api | `list_wiki_pages` · `get_wiki_page` · `create_wiki_page` · `update_wiki_page` · `get_activity` · 리소스 | [3.4](04-stage-3-mcp.md#w-3-4) · [5.1](06-stage-5-collaboration.md#w-5-1) · [5.3](06-stage-5-collaboration.md#w-5-3) | M | 할 일 | [FR-MCP-014](../requirements/12-mcp.md#fr-mcp-014) · [FR-MCP-015](../requirements/12-mcp.md#fr-mcp-015) · [FR-MCP-021](../requirements/12-mcp.md#fr-mcp-021) · [FR-MCP-022](../requirements/12-mcp.md#fr-mcp-022) · [FR-CNT-009](../requirements/08-content.md#fr-cnt-009) · [FR-ACT-010](../requirements/10-activity-search.md#fr-act-010) |

---

**관련 문서:** [WBS 한눈에](00-overview.md) · [요구사항 진행 현황](../requirements/14-progress.md) · [정해야 할 것](08-decisions.md) · [로드맵](../plan/01-roadmap.md)

[← 이전: 4단계 — 계획 · 시간 · 보드](05-stage-4-planning-time.md) | [다음: 6단계 — 연동 · 운영 →](07-stage-6-integration.md)
