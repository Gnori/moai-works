[홈](../README.md) › 요구사항

# 요구사항 개요와 전체 목록

이 페이지는 요구사항 문서의 규칙과 **전체 요구사항 목록**을 담는다. ID 를 누르면 상세 내용으로 이동한다. 진행 상태는 [요구사항 진행 현황](14-progress.md)에서 본다.

**바로가기:** [ID 체계](#1-id-체계) · [우선순위](#2-우선순위) · [작성 형식](#3-작성-형식) · [전체 목록](#4-전체-요구사항-목록) · [추적성](#5-추적성) · [진행 관리](#6-진행-관리)

---

## 1. ID 체계

| 형식 | 의미 | 상세 문서 |
|---|---|---|
| `FR-WSP-nnn` | 워크스페이스 · 계정 · 멤버십 · 역할 · 감사 · 보안 원칙 | [01-workspace.md](01-workspace.md) |
| `FR-PRJ-nnn` | 프로젝트 · 하위 프로젝트 · 모듈 · 멤버 · 프로젝트 역할 | [02-project.md](02-project.md) |
| `FR-ISS-nnn` | 일감 · 이력과 메모 · 하위 일감 · 관계 · 관찰자 · 첨부 · 비공개 · 일괄 편집 | [03-issue.md](03-issue.md) |
| `FR-WFL-nnn` | 트래커 · 상태 · 열거형 · 워크플로 · 필드 권한 · 사용자 정의 필드 · 카테고리 | [04-workflow.md](04-workflow.md) |
| `FR-QRY-nnn` | 필터 · 저장한 질의 · 목록 · 내보내기 · 칸반 보드 | [05-query.md](05-query.md) |
| `FR-PLN-nnn` | 버전 · 로드맵 · 간트 · 캘린더 | [06-planning.md](06-planning.md) |
| `FR-TIM-nnn` | 시간 기록 · 작업 분류 · 보고서 | [07-time.md](07-time.md) |
| `FR-CNT-nnn` | 위키 · 뉴스 · 문서 · 파일 · 포럼 | [08-content.md](08-content.md) |
| `FR-NTF-nnn` | 알림 · 메일 보내기 · 메일로 일감 · 화면 알림 | [09-notification.md](09-notification.md) |
| `FR-ACT-nnn` | 활동 · 검색 · 내 페이지 | [10-activity-search.md](10-activity-search.md) |
| `FR-INT-nnn` | REST API · 웹훅 · Git 저장소 연동 · 가져오기 · Redmine 이관 | [11-integration.md](11-integration.md) |
| `FR-MCP-nnn` | MCP · AI Agent — 엔드포인트 · 키 · Agent 계정 · 도구 · 안전장치 | [12-mcp.md](12-mcp.md) |
| `NFR-<분류>-nnn` | 비기능 요구사항 (`PERF` 성능 · `SCL` 규모 · `SEC` 보안 · `AVL` 가용성 · `OBS` 관측 · `QLT` 품질 · `MNT` 유지보수 · `DEP` 배포 · `I18N` 언어 · `USA` 사용성) | [13-non-functional.md](13-non-functional.md) |

- 번호는 영역 안에서 순서대로 부여하고, 한 번 부여한 번호는 다시 쓰지 않는다.
- 요구사항을 없앨 때는 지우지 않고 진행 줄을 `폐기`로 하고 사유를 남긴다.
- 앵커는 ID 를 소문자로 쓴다. 예: `03-issue.md#fr-iss-001`

## 2. 우선순위

| 등급 | 의미 | 단계 |
|---|---|---|
| **M** (Must) | 첫 출시에 꼭 필요. 없으면 "사람과 Agent 가 같은 프로젝트를 관리한다"를 보여 줄 수 없다 | [로드맵 1 ~ 3단계](../plan/01-roadmap.md#rm-stages) |
| **S** (Should) | 곧 필요. 첫 출시에는 단순한 형태로 대신할 수 있다 | 4 ~ 6단계 |
| **C** (Could) | 나중에 고도화 | 그 뒤 |
| **W** (Won't) | 이번 범위에서 뺌 (기록용) | — |

## 3. 작성 형식

각 요구사항은 아래 형식으로 쓴다(moai-wiki 와 같다).

```markdown
<a id="fr-iss-001"></a>
### FR-ISS-001 일감 만들기

| 항목 | 내용 |
|---|---|
| 우선순위 | M |
| 진행 | 할 일 |
| 관련 설계 | [일감 설계](../design/05-issue-tracking.md) |

**설명** — 무엇이 왜 필요한지 2~4문장.

**세부 요구사항**
1. 시스템은 ~해야 한다.

**수용 기준**
- [ ] 확인 가능한 조건 (테스트로 판정할 수 있어야 함)
```

- "~해야 한다"로 쓴다. 모호한 표현("적절히", "빠르게") 대신 수치나 조건을 쓴다.
- 수용 기준은 테스트로 판정할 수 있어야 한다. 권한(독자 아님 404 · 권한 없음 403) · 이력 · 감사 · MCP 동등성을 늘 함께 본다([테스트 전략](../plan/02-test-strategy.md)).

---

## 4. 전체 요구사항 목록

<!-- 각 요구사항 쪽에서 만든 목록이다. 요구사항을 더하면 이 표에도 한 줄을 더한다. -->

### 4.1 워크스페이스 · 계정 — FR-WSP ([상세](01-workspace.md))

| ID | 요구사항 | 우선순위 |
|---|---|---|
| [FR-WSP-001](01-workspace.md#fr-wsp-001) | 회원가입 | M |
| [FR-WSP-002](01-workspace.md#fr-wsp-002) | 가입 정책 설정 (시스템 관리자) | M |
| [FR-WSP-003](01-workspace.md#fr-wsp-003) | 이메일 인증 | M |
| [FR-WSP-004](01-workspace.md#fr-wsp-004) | 가입 승인 (선택) | M |
| [FR-WSP-005](01-workspace.md#fr-wsp-005) | 로그인 · 로그아웃 · 세션 | M |
| [FR-WSP-006](01-workspace.md#fr-wsp-006) | 비밀번호 재설정 · 변경 | M |
| [FR-WSP-007](01-workspace.md#fr-wsp-007) | 개인 워크스페이스 자동 생성 | M |
| [FR-WSP-008](01-workspace.md#fr-wsp-008) | 팀 워크스페이스 만들기 | S |
| [FR-WSP-009](01-workspace.md#fr-wsp-009) | 워크스페이스 설정 | M |
| [FR-WSP-010](01-workspace.md#fr-wsp-010) | 가입 신청 · 철회 | M |
| [FR-WSP-011](01-workspace.md#fr-wsp-011) | 가입 신청 승인 · 거절 | M |
| [FR-WSP-012](01-workspace.md#fr-wsp-012) | 초대 | S |
| [FR-WSP-013](01-workspace.md#fr-wsp-013) | 멤버 제거(가입 취소) · 정지 · 탈퇴 | M |
| [FR-WSP-014](01-workspace.md#fr-wsp-014) | 소유권 이전 | S |
| [FR-WSP-015](01-workspace.md#fr-wsp-015) | 권한 코드 카탈로그와 기본 역할 | M |
| [FR-WSP-016](01-workspace.md#fr-wsp-016) | 사용자 정의 역할 | M |
| [FR-WSP-017](01-workspace.md#fr-wsp-017) | Keycloak 정본과 api 사본의 동기화 · 대사 | M |
| [FR-WSP-018](01-workspace.md#fr-wsp-018) | 워크스페이스 감사 · 사용량 조회 | M |
| [FR-WSP-019](01-workspace.md#fr-wsp-019) | 시스템 관리자 기능 | M |
| [FR-WSP-020](01-workspace.md#fr-wsp-020) | 워크스페이스 삭제 | S |
| [FR-WSP-021](01-workspace.md#fr-wsp-021) | 가입 · 사용 한도 (남용 방지) | S |
| [FR-WSP-022](01-workspace.md#fr-wsp-022) | 인증 — 모든 입구는 인증을 거친다 | M |
| [FR-WSP-023](01-workspace.md#fr-wsp-023) | 권한 판정은 api 한 곳에서 | M |
| [FR-WSP-024](01-workspace.md#fr-wsp-024) | 감사 기록 | M |
| [FR-WSP-025](01-workspace.md#fr-wsp-025) | 관리 기능 접근 통제 (MFA · 접속 위치) | S |
| [FR-WSP-026](01-workspace.md#fr-wsp-026) | 전송 · 저장 암호화 | M |
| [FR-WSP-027](01-workspace.md#fr-wsp-027) | 비밀값 · 자격 증명 관리 | M |
| [FR-WSP-028](01-workspace.md#fr-wsp-028) | 위험 작업 이중 확인 | S |
| [FR-WSP-029](01-workspace.md#fr-wsp-029) | 계정 탈퇴와 개인정보 | S |

### 4.2 프로젝트 — FR-PRJ ([상세](02-project.md))

| ID | 요구사항 | 우선순위 |
|---|---|---|
| [FR-PRJ-001](02-project.md#fr-prj-001) | 프로젝트 만들기 | M |
| [FR-PRJ-002](02-project.md#fr-prj-002) | 프로젝트 식별자 | M |
| [FR-PRJ-003](02-project.md#fr-prj-003) | 프로젝트 정보 고치기 | M |
| [FR-PRJ-004](02-project.md#fr-prj-004) | 공개 범위 | M |
| [FR-PRJ-005](02-project.md#fr-prj-005) | 워크스페이스 공개 프로젝트의 기본 역할 | M |
| [FR-PRJ-006](02-project.md#fr-prj-006) | 프로젝트 역할 4종 | M |
| [FR-PRJ-007](02-project.md#fr-prj-007) | 멤버 관리 (사람) | M |
| [FR-PRJ-008](02-project.md#fr-prj-008) | Agent 계정 멤버 | M |
| [FR-PRJ-009](02-project.md#fr-prj-009) | 모듈 켜고 끄기 | M |
| [FR-PRJ-010](02-project.md#fr-prj-010) | 일감 추적 설정 (트래커 · 기본 담당 · 기본 버전) | M |
| [FR-PRJ-011](02-project.md#fr-prj-011) | 사용자 정의 필드 고르기 | S |
| [FR-PRJ-012](02-project.md#fr-prj-012) | 프로젝트 목록 · 트리 · 찾기 | M |
| [FR-PRJ-013](02-project.md#fr-prj-013) | 프로젝트 개요 쪽 | M |
| [FR-PRJ-014](02-project.md#fr-prj-014) | 하위 프로젝트 | S |
| [FR-PRJ-015](02-project.md#fr-prj-015) | 상위 프로젝트 바꾸기 | S |
| [FR-PRJ-016](02-project.md#fr-prj-016) | 멤버 이어받기 | S |
| [FR-PRJ-017](02-project.md#fr-prj-017) | 프로젝트 닫기 · 다시 열기 | S |
| [FR-PRJ-018](02-project.md#fr-prj-018) | 프로젝트 보관 · 보관 풀기 | S |
| [FR-PRJ-019](02-project.md#fr-prj-019) | 프로젝트 지우기 | S |
| [FR-PRJ-020](02-project.md#fr-prj-020) | 새 프로젝트 기본값 | S |
| [FR-PRJ-021](02-project.md#fr-prj-021) | 프로젝트 복사 · 틀 | C |

### 4.3 일감 — FR-ISS ([상세](03-issue.md))

| ID | 요구사항 | 우선순위 |
|---|---|---|
| [FR-ISS-001](03-issue.md#fr-iss-001) | 일감 번호 | M |
| [FR-ISS-002](03-issue.md#fr-iss-002) | 일감 만들기 | M |
| [FR-ISS-003](03-issue.md#fr-iss-003) | 일감 필드와 검증 | M |
| [FR-ISS-004](03-issue.md#fr-iss-004) | 일감 보기 | M |
| [FR-ISS-005](03-issue.md#fr-iss-005) | 일감 고치기와 낙관적 잠금 | M |
| [FR-ISS-006](03-issue.md#fr-iss-006) | 일감 지우기 | M |
| [FR-ISS-007](03-issue.md#fr-iss-007) | 변경 이력 (저널) | M |
| [FR-ISS-008](03-issue.md#fr-iss-008) | 메모 | M |
| [FR-ISS-009](03-issue.md#fr-iss-009) | 행위자와 경로 표시 | M |
| [FR-ISS-010](03-issue.md#fr-iss-010) | 첨부 올리기 | M |
| [FR-ISS-011](03-issue.md#fr-iss-011) | 첨부 내려받기와 권한 | M |
| [FR-ISS-012](03-issue.md#fr-iss-012) | 텍스트 링크 | M |
| [FR-ISS-013](03-issue.md#fr-iss-013) | 비공개 메모 | S |
| [FR-ISS-014](03-issue.md#fr-iss-014) | 메모 고치기와 고친 이력 | S |
| [FR-ISS-015](03-issue.md#fr-iss-015) | 하위 일감 | S |
| [FR-ISS-016](03-issue.md#fr-iss-016) | 상위 일감 값 계산 (롤업) | S |
| [FR-ISS-017](03-issue.md#fr-iss-017) | 일감 관계 | S |
| [FR-ISS-018](03-issue.md#fr-iss-018) | 선행 · 후행 일정 자동 이동 | S |
| [FR-ISS-019](03-issue.md#fr-iss-019) | 관찰자 | S |
| [FR-ISS-020](03-issue.md#fr-iss-020) | 멘션 | S |
| [FR-ISS-021](03-issue.md#fr-iss-021) | 비공개 일감과 보기 범위 | S |
| [FR-ISS-022](03-issue.md#fr-iss-022) | 일괄 편집 | S |
| [FR-ISS-023](03-issue.md#fr-iss-023) | 일감 복사 | S |
| [FR-ISS-024](03-issue.md#fr-iss-024) | 일감 옮기기 | S |
| [FR-ISS-025](03-issue.md#fr-iss-025) | "내가 만들거나 맡은 것만" 보기 범위 | C |

### 4.4 트래커 · 워크플로 · 필드 — FR-WFL ([상세](04-workflow.md))

| ID | 요구사항 | 우선순위 |
|---|---|---|
| [FR-WFL-001](04-workflow.md#fr-wfl-001) | 트래커 | M |
| [FR-WFL-002](04-workflow.md#fr-wfl-002) | 상태 | M |
| [FR-WFL-003](04-workflow.md#fr-wfl-003) | 열거형 (우선순위 · 작업 분류 · 문서 분류) | M |
| [FR-WFL-004](04-workflow.md#fr-wfl-004) | 워크플로 상태 전이 표 | M |
| [FR-WFL-005](04-workflow.md#fr-wfl-005) | 워크플로 판정 | M |
| [FR-WFL-006](04-workflow.md#fr-wfl-006) | 워크스페이스 시드 | M |
| [FR-WFL-007](04-workflow.md#fr-wfl-007) | 작성자 · 담당자 추가 전이 | S |
| [FR-WFL-008](04-workflow.md#fr-wfl-008) | 워크플로 복사 | S |
| [FR-WFL-009](04-workflow.md#fr-wfl-009) | 필드 권한 (읽기전용 · 필수) | S |
| [FR-WFL-010](04-workflow.md#fr-wfl-010) | 진척률 계산 방식 | S |
| [FR-WFL-011](04-workflow.md#fr-wfl-011) | 일감 추적 설정 | S |
| [FR-WFL-012](04-workflow.md#fr-wfl-012) | 카테고리 | S |
| [FR-WFL-013](04-workflow.md#fr-wfl-013) | 사용자 정의 필드 정의 | S |
| [FR-WFL-014](04-workflow.md#fr-wfl-014) | 사용자 정의 필드 형식과 검증 | S |
| [FR-WFL-015](04-workflow.md#fr-wfl-015) | 사용자 정의 필드 연결 | S |
| [FR-WFL-016](04-workflow.md#fr-wfl-016) | 사용자 정의 필드의 보이는 역할 | S |
| [FR-WFL-017](04-workflow.md#fr-wfl-017) | 사용자 정의 필드 찾기 · 필터 | S |
| [FR-WFL-018](04-workflow.md#fr-wfl-018) | 트래커별 설명 틀 | C |
| [FR-WFL-019](04-workflow.md#fr-wfl-019) | 설정 묶음 내보내기 · 가져오기 | C |

### 4.5 질의 · 목록 · 보드 — FR-QRY ([상세](05-query.md))

| ID | 요구사항 | 우선순위 |
|---|---|---|
| [FR-QRY-001](05-query.md#fr-qry-001) | 필터 대상 필드 | M |
| [FR-QRY-002](05-query.md#fr-qry-002) | 필터 연산자와 값 | M |
| [FR-QRY-003](05-query.md#fr-qry-003) | 권한 · 비공개 조건 | M |
| [FR-QRY-004](05-query.md#fr-qry-004) | 목록 열 | M |
| [FR-QRY-005](05-query.md#fr-qry-005) | 정렬 | M |
| [FR-QRY-006](05-query.md#fr-qry-006) | 쪽 나누기 | M |
| [FR-QRY-007](05-query.md#fr-qry-007) | 여러 프로젝트 · 하위 프로젝트 목록 | M |
| [FR-QRY-008](05-query.md#fr-qry-008) | 화면 · REST · MCP 같은 엔진 | M |
| [FR-QRY-009](05-query.md#fr-qry-009) | 사용자 정의 필드 필터 · 열 | S |
| [FR-QRY-010](05-query.md#fr-qry-010) | 묶기 | S |
| [FR-QRY-011](05-query.md#fr-qry-011) | 합계 | S |
| [FR-QRY-012](05-query.md#fr-qry-012) | 저장한 질의 (개인) | S |
| [FR-QRY-013](05-query.md#fr-qry-013) | 공개 · 역할별 질의 | S |
| [FR-QRY-014](05-query.md#fr-qry-014) | CSV 내보내기 | S |
| [FR-QRY-015](05-query.md#fr-qry-015) | 칸반 보드 | S |
| [FR-QRY-016](05-query.md#fr-qry-016) | 보드 끌어 놓기와 워크플로 | S |
| [FR-QRY-017](05-query.md#fr-qry-017) | 보드 묶음 줄 | S |
| [FR-QRY-018](05-query.md#fr-qry-018) | 기본 질의 | C |
| [FR-QRY-019](05-query.md#fr-qry-019) | PDF 내보내기 | C |
| [FR-QRY-020](05-query.md#fr-qry-020) | Atom 피드 | C |
| [FR-QRY-021](05-query.md#fr-qry-021) | 보드 WIP 한도 · 손으로 정한 순서 | C |

### 4.6 버전 · 로드맵 · 간트 — FR-PLN ([상세](06-planning.md))

| ID | 요구사항 | 우선순위 |
|---|---|---|
| [FR-PLN-001](06-planning.md#fr-pln-001) | 버전 만들기 · 고치기 | M |
| [FR-PLN-002](06-planning.md#fr-pln-002) | 버전 상태 (열림 · 잠김 · 닫힘) | M |
| [FR-PLN-003](06-planning.md#fr-pln-003) | 버전 공유 범위 | S |
| [FR-PLN-004](06-planning.md#fr-pln-004) | 대상 버전 고르기 규칙 | M |
| [FR-PLN-005](06-planning.md#fr-pln-005) | 기본 버전 | S |
| [FR-PLN-006](06-planning.md#fr-pln-006) | 버전 지우기 | M |
| [FR-PLN-007](06-planning.md#fr-pln-007) | 로드맵 화면 | M |
| [FR-PLN-008](06-planning.md#fr-pln-008) | 로드맵 진척 계산 | M |
| [FR-PLN-009](06-planning.md#fr-pln-009) | 버전 쪽 | M |
| [FR-PLN-010](06-planning.md#fr-pln-010) | 간트 차트 | S |
| [FR-PLN-011](06-planning.md#fr-pln-011) | 간트 관계 화살표 · 진척선 | S |
| [FR-PLN-012](06-planning.md#fr-pln-012) | 간트 내보내기 (PDF · PNG) | C |
| [FR-PLN-013](06-planning.md#fr-pln-013) | 캘린더 (월 · 주) | S |
| [FR-PLN-014](06-planning.md#fr-pln-014) | MCP 로 버전 · 로드맵 보기 | S |
| [FR-PLN-015](06-planning.md#fr-pln-015) | 간트에서 날짜 끌어 바꾸기 | C |
| [FR-PLN-016](06-planning.md#fr-pln-016) | 워크스페이스 전체 간트 · 캘린더 | C |

### 4.7 시간 기록 — FR-TIM ([상세](07-time.md))

| ID | 요구사항 | 우선순위 |
|---|---|---|
| [FR-TIM-001](07-time.md#fr-tim-001) | 시간 기록하기 | M |
| [FR-TIM-002](07-time.md#fr-tim-002) | 시간 입력 형식 | M |
| [FR-TIM-003](07-time.md#fr-tim-003) | 일감 고치면서 시간 기록 | S |
| [FR-TIM-004](07-time.md#fr-tim-004) | 남 대신 기록 | S |
| [FR-TIM-005](07-time.md#fr-tim-005) | 기록 고치기 · 지우기 | M |
| [FR-TIM-006](07-time.md#fr-tim-006) | 프로젝트별 작업 분류 | S |
| [FR-TIM-007](07-time.md#fr-tim-007) | 기록 규칙 설정 | S |
| [FR-TIM-008](07-time.md#fr-tim-008) | 시간 기록 목록 · CSV | M |
| [FR-TIM-009](07-time.md#fr-tim-009) | 예상 대 실적 | M |
| [FR-TIM-010](07-time.md#fr-tim-010) | 소요 시간 보고서 | S |
| [FR-TIM-011](07-time.md#fr-tim-011) | 일감 옮기기 · 지우기와 시간 기록 | M |
| [FR-TIM-012](07-time.md#fr-tim-012) | MCP 로 시간 기록 · 보기 | S |
| [FR-TIM-013](07-time.md#fr-tim-013) | 시간 기록 마감 | C |

### 4.8 위키 · 뉴스 · 문서 · 포럼 — FR-CNT ([상세](08-content.md))

| ID | 요구사항 | 우선순위 |
|---|---|---|
| [FR-CNT-001](08-content.md#fr-cnt-001) | 프로젝트 위키 · 시작 쪽 · 링크 | M |
| [FR-CNT-002](08-content.md#fr-cnt-002) | 위키 쪽 만들기 · 고치기 | M |
| [FR-CNT-003](08-content.md#fr-cnt-003) | 쪽 계층 · 색인 | M |
| [FR-CNT-004](08-content.md#fr-cnt-004) | 판 · 차이 · 되돌리기 | M |
| [FR-CNT-005](08-content.md#fr-cnt-005) | 이름 바꾸기 · 옛 이름 넘기기 · 옮기기 | S |
| [FR-CNT-006](08-content.md#fr-cnt-006) | 쪽 보호 · 지우기 | S |
| [FR-CNT-007](08-content.md#fr-cnt-007) | 위키 관찰 | S |
| [FR-CNT-008](08-content.md#fr-cnt-008) | 위키 내보내기 | C |
| [FR-CNT-009](08-content.md#fr-cnt-009) | MCP 로 위키 읽고 쓰기 | S |
| [FR-CNT-010](08-content.md#fr-cnt-010) | 뉴스 · 댓글 | S |
| [FR-CNT-011](08-content.md#fr-cnt-011) | 문서 | C |
| [FR-CNT-012](08-content.md#fr-cnt-012) | 파일 (내려받기) | S |
| [FR-CNT-013](08-content.md#fr-cnt-013) | 포럼 — 게시판 · 글 · 답글 | C |
| [FR-CNT-014](08-content.md#fr-cnt-014) | 포럼 관리 — 잠금 · 고정 · 관찰 | C |
| [FR-CNT-015](08-content.md#fr-cnt-015) | 위키 절 고치기 | S |
| [FR-CNT-016](08-content.md#fr-cnt-016) | 위키 매크로 · 사이드바 | C |

### 4.9 알림 · 메일 — FR-NTF ([상세](09-notification.md))

| ID | 요구사항 | 우선순위 |
|---|---|---|
| [FR-NTF-001](09-notification.md#fr-ntf-001) | 알림 종류 · 워크스페이스 메일 설정 | M |
| [FR-NTF-002](09-notification.md#fr-ntf-002) | 받는 사람 규칙 | M |
| [FR-NTF-003](09-notification.md#fr-ntf-003) | 받는 사람 권한 재판정 | M |
| [FR-NTF-004](09-notification.md#fr-ntf-004) | 사용자 알림 설정 | M |
| [FR-NTF-005](09-notification.md#fr-ntf-005) | 화면 알림함 | M |
| [FR-NTF-006](09-notification.md#fr-ntf-006) | 메일 보내기 — 아웃박스 · `log` 모드 | M |
| [FR-NTF-007](09-notification.md#fr-ntf-007) | 메일 내용 · 스레드 헤더 | M |
| [FR-NTF-008](09-notification.md#fr-ntf-008) | 멘션 | S |
| [FR-NTF-009](09-notification.md#fr-ntf-009) | 묶어 보내기 — 일괄 변경 · Agent 상한 | S |
| [FR-NTF-010](09-notification.md#fr-ntf-010) | Agent 계정과 알림 | S |
| [FR-NTF-011](09-notification.md#fr-ntf-011) | 메일로 일감 만들기 | C |
| [FR-NTF-012](09-notification.md#fr-ntf-012) | 답장으로 메모 더하기 | C |
| [FR-NTF-013](09-notification.md#fr-ntf-013) | 받은 메일 안전장치 | C |
| [FR-NTF-014](09-notification.md#fr-ntf-014) | 화면 알림 실시간 (SSE) | C |
| [FR-NTF-015](09-notification.md#fr-ntf-015) | 하루 요약 메일 · 받지 않기 링크 | C |

### 4.10 활동 · 검색 — FR-ACT ([상세](10-activity-search.md))

| ID | 요구사항 | 우선순위 |
|---|---|---|
| [FR-ACT-001](10-activity-search.md#fr-act-001) | 프로젝트 활동 | M |
| [FR-ACT-002](10-activity-search.md#fr-act-002) | 워크스페이스 전체 활동 | M |
| [FR-ACT-003](10-activity-search.md#fr-act-003) | 사람 쪽 · 사람별 활동 | S |
| [FR-ACT-004](10-activity-search.md#fr-act-004) | 활동 Atom 피드 | C |
| [FR-ACT-005](10-activity-search.md#fr-act-005) | 행위자 표시 · 거르기 | M |
| [FR-ACT-006](10-activity-search.md#fr-act-006) | 통합 검색 | M |
| [FR-ACT-007](10-activity-search.md#fr-act-007) | `#123` 바로 가기 | M |
| [FR-ACT-008](10-activity-search.md#fr-act-008) | 검색 권한 필터 | M |
| [FR-ACT-009](10-activity-search.md#fr-act-009) | 색인 갱신 · 다시 만들기 | M |
| [FR-ACT-010](10-activity-search.md#fr-act-010) | MCP 로 활동 보기 | S |
| [FR-ACT-011](10-activity-search.md#fr-act-011) | 내 페이지 기본 블록 | M |
| [FR-ACT-012](10-activity-search.md#fr-act-012) | 내 페이지 꾸미기 | S |
| [FR-ACT-013](10-activity-search.md#fr-act-013) | 첨부 내용 검색 | C |

### 4.11 연동 — FR-INT ([상세](11-integration.md))

| ID | 요구사항 | 우선순위 |
|---|---|---|
| [FR-INT-001](11-integration.md#fr-int-001) | REST API 문서 (OpenAPI) | S |
| [FR-INT-002](11-integration.md#fr-int-002) | REST API 공통 규약 | M |
| [FR-INT-003](11-integration.md#fr-int-003) | 바깥 자동화 인증 — Keycloak 서비스 계정 | S |
| [FR-INT-004](11-integration.md#fr-int-004) | 개인 API 토큰 | C |
| [FR-INT-005](11-integration.md#fr-int-005) | 웹훅 구독 | S |
| [FR-INT-006](11-integration.md#fr-int-006) | 웹훅 전달과 HMAC 서명 | S |
| [FR-INT-007](11-integration.md#fr-int-007) | 웹훅 재시도 · 실패 기록 · 자동 끄기 | S |
| [FR-INT-008](11-integration.md#fr-int-008) | Git 저장소 연결 | S |
| [FR-INT-009](11-integration.md#fr-int-009) | Git 웹훅 받기 · 서명 검증 · 커밋 저장 | S |
| [FR-INT-010](11-integration.md#fr-int-010) | 커밋을 일감에 잇기 · 사람 대응 | S |
| [FR-INT-011](11-integration.md#fr-int-011) | 고치기 키워드로 상태 · 진척률 바꾸기 | S |
| [FR-INT-012](11-integration.md#fr-int-012) | 커밋 메시지로 시간 기록 | S |
| [FR-INT-013](11-integration.md#fr-int-013) | PR · MR 잇기 | S |
| [FR-INT-014](11-integration.md#fr-int-014) | 저장소 브라우저 | C |
| [FR-INT-015](11-integration.md#fr-int-015) | CSV 일감 가져오기 | S |
| [FR-INT-016](11-integration.md#fr-int-016) | Redmine 이관 — 연결과 대응 설정 | S |
| [FR-INT-017](11-integration.md#fr-int-017) | Redmine 이관 — 데이터 옮기기 | S |
| [FR-INT-018](11-integration.md#fr-int-018) | Redmine 이관 — 번호 대응표 · 다시 돌려도 안전 · 결과 | S |
| [FR-INT-019](11-integration.md#fr-int-019) | 워크스페이스 데이터 내보내기 | C |

### 4.12 MCP · AI Agent — FR-MCP ([상세](12-mcp.md))

| ID | 요구사항 | 우선순위 |
|---|---|---|
| [FR-MCP-001](12-mcp.md#fr-mcp-001) | 워크스페이스 MCP 엔드포인트 | M |
| [FR-MCP-002](12-mcp.md#fr-mcp-002) | 개인 MCP 키 | M |
| [FR-MCP-003](12-mcp.md#fr-mcp-003) | 서비스 MCP 키 | M |
| [FR-MCP-004](12-mcp.md#fr-mcp-004) | 키 검사 · 만료 · 폐기 | M |
| [FR-MCP-005](12-mcp.md#fr-mcp-005) | Agent 계정 만들기 · 책임자 · 정지 · 표시 | M |
| [FR-MCP-006](12-mcp.md#fr-mcp-006) | Agent 계정의 프로젝트 역할과 일감 참여 | M |
| [FR-MCP-007](12-mcp.md#fr-mcp-007) | 도구 권한 판정 — 주체 권한 ∩ 엔드포인트 범위 | M |
| [FR-MCP-008](12-mcp.md#fr-mcp-008) | `whoami` — 누구로 일하는지 알기 | M |
| [FR-MCP-009](12-mcp.md#fr-mcp-009) | `list_projects` · `get_project` — 프로젝트 읽기 | M |
| [FR-MCP-010](12-mcp.md#fr-mcp-010) | `search_issues` — 일감 찾기 | M |
| [FR-MCP-011](12-mcp.md#fr-mcp-011) | `get_issue` — 일감 읽기 | M |
| [FR-MCP-012](12-mcp.md#fr-mcp-012) | `list_issue_metadata` — 일감 입력에 쓸 값 | M |
| [FR-MCP-013](12-mcp.md#fr-mcp-013) | `list_versions` · `get_roadmap` — 버전과 진척 읽기 | S |
| [FR-MCP-014](12-mcp.md#fr-mcp-014) | `get_activity` — 최근 활동 읽기 | S |
| [FR-MCP-015](12-mcp.md#fr-mcp-015) | `list_wiki_pages` · `get_wiki_page` — 위키 읽기 | S |
| [FR-MCP-016](12-mcp.md#fr-mcp-016) | `create_issue` — 일감 만들기 | M |
| [FR-MCP-017](12-mcp.md#fr-mcp-017) | `update_issue` — 일감 고치기 · 상태 전이 · 담당 | M |
| [FR-MCP-018](12-mcp.md#fr-mcp-018) | `add_issue_note` — 메모 달기 | M |
| [FR-MCP-019](12-mcp.md#fr-mcp-019) | `add_issue_relation` · `remove_issue_relation` — 관계 걸고 풀기 | M |
| [FR-MCP-020](12-mcp.md#fr-mcp-020) | `list_time_entries` · `log_time` — 시간 기록 | S |
| [FR-MCP-021](12-mcp.md#fr-mcp-021) | `create_wiki_page` · `update_wiki_page` — 위키 쓰기 | S |
| [FR-MCP-022](12-mcp.md#fr-mcp-022) | 리소스 | S |
| [FR-MCP-023](12-mcp.md#fr-mcp-023) | 서버 instructions | M |
| [FR-MCP-024](12-mcp.md#fr-mcp-024) | 낙관적 잠금 | M |
| [FR-MCP-025](12-mcp.md#fr-mcp-025) | 멱등 키 | M |
| [FR-MCP-026](12-mcp.md#fr-mcp-026) | 속도 제한 · 일 한도 | M |
| [FR-MCP-027](12-mcp.md#fr-mcp-027) | 응답 크기 · 페이지 | M |
| [FR-MCP-028](12-mcp.md#fr-mcp-028) | 사용자 데이터 표시 · 출처 표시 | M |
| [FR-MCP-029](12-mcp.md#fr-mcp-029) | MCP 로 하지 않는 일 | M |
| [FR-MCP-030](12-mcp.md#fr-mcp-030) | 감사 · 호출 기록 | M |
| [FR-MCP-031](12-mcp.md#fr-mcp-031) | 사용량 보기 | S |
| [FR-MCP-032](12-mcp.md#fr-mcp-032) | 연결 안내 화면 | M |
| [FR-MCP-033](12-mcp.md#fr-mcp-033) | 고정 입구 `/mcp/project` 에서 옮기기 | M |
| [FR-MCP-034](12-mcp.md#fr-mcp-034) | OAuth 2.1 로 붙기 | C |
| [FR-MCP-035](12-mcp.md#fr-mcp-035) | 프롬프트 | C |

### 4.13 비기능 — NFR ([상세](13-non-functional.md))

| ID | 요구사항 | 우선순위 |
|---|---|---|
| [NFR-PERF-001](13-non-functional.md#nfr-perf-001) | 화면 · REST 응답 시간 | M |
| [NFR-PERF-002](13-non-functional.md#nfr-perf-002) | MCP 도구 응답 시간 | M |
| [NFR-SCL-001](13-non-functional.md#nfr-scl-001) | 규모 | S |
| [NFR-SEC-001](13-non-functional.md#nfr-sec-001) | 경계 누출 0 | M |
| [NFR-SEC-002](13-non-functional.md#nfr-sec-002) | Agent 의 한계 | M |
| [NFR-AVL-001](13-non-functional.md#nfr-avl-001) | 가용성 | S |
| [NFR-OBS-001](13-non-functional.md#nfr-obs-001) | 관측 | S |
| [NFR-QLT-001](13-non-functional.md#nfr-qlt-001) | 시험 | M |
| [NFR-MNT-001](13-non-functional.md#nfr-mnt-001) | 형제 저장소와 맞추기 | S |
| [NFR-DEP-001](13-non-functional.md#nfr-dep-001) | 배포와 설정 | S |
| [NFR-I18N-001](13-non-functional.md#nfr-i18n-001) | 언어 | M |
| [NFR-USA-001](13-non-functional.md#nfr-usa-001) | 반응형 · 접근성 | M |

### 4.14 영역별 개수

| 영역 | M | S | C | 합계 |
|---|---|---|---|---|
| 워크스페이스 · 계정 (`FR-WSP`) | 21 | 8 | 0 | 29 |
| 프로젝트 (`FR-PRJ`) | 12 | 8 | 1 | 21 |
| 일감 (`FR-ISS`) | 12 | 12 | 1 | 25 |
| 트래커 · 워크플로 · 필드 (`FR-WFL`) | 6 | 11 | 2 | 19 |
| 질의 · 목록 · 보드 (`FR-QRY`) | 8 | 9 | 4 | 21 |
| 버전 · 로드맵 · 간트 (`FR-PLN`) | 7 | 6 | 3 | 16 |
| 시간 기록 (`FR-TIM`) | 6 | 6 | 1 | 13 |
| 위키 · 뉴스 · 문서 · 포럼 (`FR-CNT`) | 4 | 7 | 5 | 16 |
| 알림 · 메일 (`FR-NTF`) | 7 | 3 | 5 | 15 |
| 활동 · 검색 (`FR-ACT`) | 8 | 3 | 2 | 13 |
| 연동 (`FR-INT`) | 1 | 15 | 3 | 19 |
| MCP · AI Agent (`FR-MCP`) | 26 | 7 | 2 | 35 |
| 비기능 (`NFR`) | 7 | 5 | 0 | 12 |
| **합계** | **125** | **100** | **29** | **254** |

## 5. 추적성

- 요구사항 → 설계: 각 요구사항의 `관련 설계`.
- 요구사항 → 작업: [WBS](../wbs/00-overview.md) 단계 문서의 `요구사항` 칸. [진행 현황](14-progress.md)의 "작업" 칸이 이것을 모아 보인다.
- 요구사항 → 시험: 수용 기준을 확인한 자동 테스트 이름이나 실제 흐름을 진행 줄의 "한 것"에 적는다.

## 6. 진행 관리

요구사항이 어디까지 됐는지는 **요구사항 문서 안에** 적는다. 한 쪽으로 모은 것이 [요구사항 진행 현황](14-progress.md)이고, 그 쪽은 도구가 만든다(moai-wiki 와 같은 방식).

| 무엇 | 어디에 | 어떻게 |
|---|---|---|
| 요구사항의 상태 | 그 요구사항 표의 **진행** 줄 | `할 일` · `일부 — 한 것: …. 남은 것: ….` · `완료 (날짜) — 메모` · `폐기 — 사유` |
| 확인한 수용 기준 | 그 요구사항의 **수용 기준** | 자동 테스트나 실제 화면 · 흐름으로 확인한 것만 `- [x]` 로 바꾼다. 만들었지만 확인하지 못한 것은 그대로 둔다 |
| 모아 보기 | [14-progress.md](14-progress.md) | `python3 -I tools/req_progress.py` 로 다시 만든다. 손으로 고치지 않는다 |

**상태의 뜻**

- `할 일`: 아직 손대지 않았다. 체크한 수용 기준이 없다.
- `일부`: 만든 것이 있고 남은 것이 있다. **남은 것을 반드시 적는다** — 세부 요구사항 가운데 안 만든 것, 확인하지 못한 수용 기준.
- `완료`: 세부 요구사항을 모두 만들었고 수용 기준을 모두 확인했다. 요구사항과 다르게 만든 곳이 있으면 메모에 적는다.
- `폐기`: 하지 않기로 했다. 사유를 적는다.

**일하는 순서**

1. 작업을 시작할 때 그 작업이 다루는 요구사항을 [진행 현황](14-progress.md)에서 확인한다(WBS 작업의 요구사항 칸).
2. 작업을 끝내면 같은 작업에서 그 요구사항의 진행 줄과 수용 기준 체크를 고치고, 현황 쪽을 다시 만든다.
3. **요구사항을 하나 끝내거나 진행이 바뀔 때마다 사람에게 알린다**: 처리한 결과(무엇이 됐고 무엇으로 확인했나)와 남은 요구사항(그 요구사항의 남은 것, 전체에서 남은 수).
4. `python3 -I tools/check_docs.py docs` 가 진행 줄의 규칙과 현황 쪽이 최신인지도 본다.

---

**관련 문서:** [요구사항 진행 현황](14-progress.md) · [프로젝트 개요](../01-overview.md) · [용어집](../02-glossary.md) · [로드맵](../plan/01-roadmap.md) · [테스트 전략](../plan/02-test-strategy.md)

[← 이전: 용어집](../02-glossary.md) | [다음: 워크스페이스 · 계정 요구사항 →](01-workspace.md)
