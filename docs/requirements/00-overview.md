[홈](../README.md) › 요구사항

# 요구사항 개요와 전체 목록

이 페이지는 요구사항 문서의 규칙과 **전체 요구사항 목록**을 담는다. ID 를 누르면 상세 내용으로 이동한다. 진행 상태는 [요구사항 진행 현황](06-progress.md)에서 본다.

> **2026-11-22 재정리.** 사람의 결정으로 moai-works 를 형제 저장소 moai-wiki 와 **같은 구조**(모듈 · 인증 · 워크스페이스 · 역할과 권한 · MCP · 흐름 · 웹)로 다시 정리했다. 다른 점은 "위키 대신 프로젝트, 페이지 대신 일감"과 사람이 정한 일감 모델(유형 · 관리자 · 담당자 · 날짜 다섯 개 · 상위 · 하위 · 연결)뿐이다. 그 전의 Redmine 전체를 옮긴 요구사항(254개 — 워크플로 · 사용자 정의 필드 · 버전 · 간트 · 시간 · 위키 · 알림 · Git · 이관 · Agent 계정 …)은 지웠고, 범위 밖은 [§7 나중 후보](#7-나중-후보)에 목록으로만 둔다. 결정 기록은 [핵심 설계 결정](../design/00-key-decisions.md)에 있다.

**바로가기:** [ID 체계](#1-id-체계) · [우선순위](#2-우선순위) · [작성 형식](#3-작성-형식) · [전체 목록](#4-전체-요구사항-목록) · [추적성](#5-추적성) · [진행 관리](#6-진행-관리) · [나중 후보](#7-나중-후보) · **[진행 현황](06-progress.md)**

---

## 1. ID 체계

| 형식 | 의미 | moai-wiki 대응 | 상세 문서 |
|---|---|---|---|
| `FR-WSP-nnn` | 워크스페이스 · 계정 · 멤버십 · 역할과 권한 코드 · 감사 · 보안 원칙 | FR-WSP · FR-SEC 일부 | [01-workspace.md](01-workspace.md) |
| `FR-PRJ-nnn` | 프로젝트 — 만들기 · 목록 · 설정 · 보관 · 삭제 · 독자 판정 · 프로젝트 역할 · 기본 프로젝트 · 화면 · 감사 | 위키(FR-WIKI 의 위키 부분) | [02-project.md](02-project.md) |
| `FR-ISS-nnn` | 일감 — 만들기 · 보기 · 고치기 · 옮기기 · 유형 · 상태 · 관리자와 담당자 · 날짜 · 상위 · 하위 · 연결 · 이력 · 댓글 · 목록(프로젝트 · 워크스페이스) · 지우기 · 화면 | 페이지(FR-WIKI 의 페이지 부분) | [03-issue.md](03-issue.md) |
| `FR-MCP-nnn` | MCP — 키 · 워크스페이스 엔드포인트 · 판정 · 읽기 · 쓰기 도구 · 기록 · 안전장치 · 연결 안내 | 워크스페이스 MCP 엔드포인트 · 키 | [04-mcp.md](04-mcp.md) |
| `FR-NOTI-nnn` | 알림 — 알림함 · 무엇을 누구에게 · 아침 알림 · 받기 설정 · 화면 · 메일 (2026-11-22 더함. 옛 `FR-NTF` 와 다른 영역이다) | 알림(moai-wiki V15 `notification`) | [07-notification.md](07-notification.md) |
| `NFR-<분류>-nnn` | 비기능 요구사항 (`PERF` 성능 · `SCL` 규모 · `SEC` 보안 · `AVL` 가용성 · `OBS` 관측 · `QLT` 품질 · `MNT` 유지보수 · `DEP` 배포 · `I18N` 언어 · `USA` 사용성) | NFR | [05-non-functional.md](05-non-functional.md) |

- 번호는 영역 안에서 순서대로 부여하고, 한 번 부여한 번호는 다시 쓰지 않는다.
- **예외 한 번:** 2026-11-22 재정리 때 `FR-PRJ` · `FR-ISS` · `FR-MCP` 는 번호를 처음부터 다시 매겼다(옛 번호와 뜻이 다르다). 옛 `FR-WFL` · `FR-QRY` · `FR-PLN` · `FR-TIM` · `FR-CNT` · `FR-NTF` · `FR-ACT` · `FR-INT` 는 없앴다. `FR-WSP` 와 `NFR` 는 번호 · 진행을 그대로 뒀다. 앞으로는 위 규칙을 지킨다.
- 요구사항을 없앨 때는 지우지 않고 진행 줄을 `폐기`로 하고 사유를 남긴다.
- 앵커는 ID 를 소문자로 쓴다. 예: `03-issue.md#fr-iss-001`

## 2. 우선순위

| 등급 | 의미 | 단계 |
|---|---|---|
| **M** (Must) | 첫 출시에 꼭 필요. 없으면 "사람과 바깥 AI Agent 가 같은 규칙으로 같은 프로젝트의 일감을 관리한다"를 보여 줄 수 없다 | [로드맵](../plan/01-roadmap.md) 1 ~ 4단계(토대 · 프로젝트 · 일감 · MCP) |
| **S** (Should) | 곧 필요. 첫 출시에는 단순한 형태로 대신할 수 있다 | 1 ~ 4단계 안에서 여유가 있을 때, 늦어도 6단계(운영) |
| **C** (Could) | 나중에 고도화 | 그 뒤 |
| **W** (Won't) | 쓰지 않는다 — 범위 밖은 요구사항으로 쓰지 않고 [§7 나중 후보](#7-나중-후보)에 목록으로만 둔다 | — |

## 3. 작성 형식

각 요구사항은 아래 형식으로 쓴다(moai-wiki 와 같다).

```markdown
<a id="fr-iss-001"></a>
### FR-ISS-001 일감 만들기

| 항목 | 내용 |
|---|---|
| 우선순위 | M |
| 진행 | 할 일 |
| 관련 설계 | [일감 설계](../design/05-issue.md) |

**설명** — 무엇이 왜 필요한지 2~4문장.

**세부 요구사항**
1. 시스템은 ~해야 한다.

**수용 기준**
- [ ] 확인 가능한 조건 (테스트로 판정할 수 있어야 함)
```

- "~해야 한다"로 쓴다. 모호한 표현("적절히", "빠르게") 대신 수치나 조건을 쓴다. 오류는 HTTP 상태와 `code` 를 함께 적는다.
- 수용 기준은 테스트로 판정할 수 있어야 한다. 권한(독자 아님 404 · 권한 없음 403) · 이력 · 감사 · MCP 동등성을 늘 함께 본다([테스트 전략](../plan/02-test-strategy.md)).
- 비밀값과 사람 이름은 쓰지 않는다(사용자 = "사람" · "사용자").

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
| [FR-WSP-030](01-workspace.md#fr-wsp-030) | Agent 스킬 관리 (시스템 관리자) | M |

### 4.2 프로젝트 — FR-PRJ ([상세](02-project.md))

| ID | 요구사항 | 우선순위 |
|---|---|---|
| [FR-PRJ-001](02-project.md#fr-prj-001) | 프로젝트 만들기 | M |
| [FR-PRJ-002](02-project.md#fr-prj-002) | 프로젝트 목록 | M |
| [FR-PRJ-003](02-project.md#fr-prj-003) | 프로젝트 설정 | M |
| [FR-PRJ-004](02-project.md#fr-prj-004) | 보관 · 복원 | M |
| [FR-PRJ-005](02-project.md#fr-prj-005) | 삭제 | M |
| [FR-PRJ-006](02-project.md#fr-prj-006) | 공개 범위와 독자 판정 | M |
| [FR-PRJ-007](02-project.md#fr-prj-007) | 프로젝트 역할 | M |
| [FR-PRJ-008](02-project.md#fr-prj-008) | 프로젝트 역할의 정본 (Keycloak 그룹) | M |
| [FR-PRJ-009](02-project.md#fr-prj-009) | 기본 프로젝트 (내 프로젝트) | M |
| [FR-PRJ-010](02-project.md#fr-prj-010) | 프로젝트 화면 | M |
| [FR-PRJ-011](02-project.md#fr-prj-011) | 프로젝트 감사 | M |
| [FR-PRJ-012](02-project.md#fr-prj-012) | 프로젝트 모듈 | M |
| [FR-PRJ-013](02-project.md#fr-prj-013) | 프로젝트의 에이전트 멤버 | M |

### 4.3 일감 — FR-ISS ([상세](03-issue.md))

| ID | 요구사항 | 우선순위 |
|---|---|---|
| [FR-ISS-001](03-issue.md#fr-iss-001) | 일감 만들기 | M |
| [FR-ISS-002](03-issue.md#fr-iss-002) | 일감 보기 | M |
| [FR-ISS-003](03-issue.md#fr-iss-003) | 일감 고치기 (동시 수정 포함) | M |
| [FR-ISS-004](03-issue.md#fr-iss-004) | 일감 유형 | M |
| [FR-ISS-005](03-issue.md#fr-iss-005) | 상태 | M |
| [FR-ISS-006](03-issue.md#fr-iss-006) | 관리자와 담당자 | M |
| [FR-ISS-007](03-issue.md#fr-iss-007) | 날짜 (완료 기한 · 시작일 · 완료 예정일 · 완료일 · 종료일) | M |
| [FR-ISS-008](03-issue.md#fr-iss-008) | 상위 · 하위 일감 | M |
| [FR-ISS-009](03-issue.md#fr-iss-009) | 연결 일감 | M |
| [FR-ISS-010](03-issue.md#fr-iss-010) | 이력 | M |
| [FR-ISS-011](03-issue.md#fr-iss-011) | 댓글 | M |
| [FR-ISS-012](03-issue.md#fr-iss-012) | 일감 목록 · 필터 · 정렬 | M |
| [FR-ISS-013](03-issue.md#fr-iss-013) | 일감 지우기 | M |
| [FR-ISS-014](03-issue.md#fr-iss-014) | 일감 화면 | M |
| [FR-ISS-015](03-issue.md#fr-iss-015) | 리비전 · 타임라인 · 차이(diff) | M |
| [FR-ISS-016](03-issue.md#fr-iss-016) | 작업 시간 (예상 공수 · 소요 시간 기록) | M |
| [FR-ISS-017](03-issue.md#fr-iss-017) | 공수 집계 (예상 대비 실제 투입 공수) | S |
| [FR-ISS-018](03-issue.md#fr-iss-018) | 첨부파일 · 본문 이미지 · 내용/댓글/첨부 탭 | M |
| [FR-ISS-019](03-issue.md#fr-iss-019) | 댓글 고치기 · 지우기 | S |
| [FR-ISS-020](03-issue.md#fr-iss-020) | 유형별 일감 템플릿 (워크스페이스 기본 · 프로젝트 덮어쓰기 · MCP) | M |
| [FR-ISS-021](03-issue.md#fr-iss-021) | Markdown 편집기 (쓰면서 바로 그려지는 편집 · 도구 막대 · 목차 · 원문) | M |
| [FR-ISS-022](03-issue.md#fr-iss-022) | 에이전트 세션 (이 일을 맡은 AI 세션 이름) | M |
| [FR-ISS-023](03-issue.md#fr-iss-023) | 일감의 관련 모듈 (여럿) | M |
| [FR-ISS-024](03-issue.md#fr-iss-024) | 일감 옮기기 (다른 프로젝트로) | M |
| [FR-ISS-025](03-issue.md#fr-iss-025) | 워크스페이스 일감 목록 · 찾기 | M |
| [FR-ISS-026](03-issue.md#fr-iss-026) | 번호 다시 매기기 (V23) | M |
| [FR-ISS-027](03-issue.md#fr-iss-027) | 본문 · 댓글의 차트 · 흐름도 · 코드 보기 | M |

### 4.4 MCP — FR-MCP ([상세](04-mcp.md))

| ID | 요구사항 | 우선순위 |
|---|---|---|
| [FR-MCP-001](04-mcp.md#fr-mcp-001) | MCP 키 (워크스페이스에 묶기 · 권한 · 감사) | M |
| [FR-MCP-002](04-mcp.md#fr-mcp-002) | 워크스페이스 MCP 엔드포인트 | M |
| [FR-MCP-003](04-mcp.md#fr-mcp-003) | MCP 판정 | M |
| [FR-MCP-004](04-mcp.md#fr-mcp-004) | 읽기 도구 | M |
| [FR-MCP-005](04-mcp.md#fr-mcp-005) | 쓰기 도구 | M |
| [FR-MCP-006](04-mcp.md#fr-mcp-006) | MCP 접근 기록 (읽기 · 생성 · 수정 · 삭제 전부) | M |
| [FR-MCP-007](04-mcp.md#fr-mcp-007) | 안전장치 (속도 제한 · 응답 크기 · 멱등) | M |
| [FR-MCP-008](04-mcp.md#fr-mcp-008) | 연결 안내 화면 | M |
| [FR-MCP-009](04-mcp.md#fr-mcp-009) | MCP 는 web-backend 가 중계한다 | M |
| [FR-MCP-010](04-mcp.md#fr-mcp-010) | MCP 접근 기록 보기 · 거르기 | M |
| [FR-MCP-011](04-mcp.md#fr-mcp-011) | MCP 첨부 — 일회용 티켓으로 REST 올리기 · 받기 | M |
| [FR-MCP-012](04-mcp.md#fr-mcp-012) | 도구만 보고 일감을 바르게 — 결과의 길잡이 · 연결 안내 핵심 규칙 | M |

### 4.5 알림 — FR-NOTI ([상세](07-notification.md))

| ID | 요구사항 | 우선순위 |
|---|---|---|
| [FR-NOTI-001](07-notification.md#fr-noti-001) | 알림함 | M |
| [FR-NOTI-002](07-notification.md#fr-noti-002) | 무엇을 누구에게 알리는가 | M |
| [FR-NOTI-003](07-notification.md#fr-noti-003) | 아침 알림 (기한 · 키 만료) | M |
| [FR-NOTI-004](07-notification.md#fr-noti-004) | 알림 받기 설정 | S |
| [FR-NOTI-005](07-notification.md#fr-noti-005) | 알림 화면 | M |
| [FR-NOTI-006](07-notification.md#fr-noti-006) | 메일 알림 | C |

### 4.5 비기능 — NFR ([상세](05-non-functional.md))

| ID | 요구사항 | 우선순위 |
|---|---|---|
| [NFR-PERF-001](05-non-functional.md#nfr-perf-001) | 화면 · REST 응답 시간 | M |
| [NFR-PERF-002](05-non-functional.md#nfr-perf-002) | MCP 도구 응답 시간 | M |
| [NFR-SCL-001](05-non-functional.md#nfr-scl-001) | 규모 | S |
| [NFR-SEC-001](05-non-functional.md#nfr-sec-001) | 경계 누출 0 | M |
| [NFR-SEC-002](05-non-functional.md#nfr-sec-002) | Agent 의 한계 | M |
| [NFR-AVL-001](05-non-functional.md#nfr-avl-001) | 가용성 | S |
| [NFR-OBS-001](05-non-functional.md#nfr-obs-001) | 관측 | S |
| [NFR-QLT-001](05-non-functional.md#nfr-qlt-001) | 시험 | M |
| [NFR-MNT-001](05-non-functional.md#nfr-mnt-001) | 형제 저장소와 맞추기 | S |
| [NFR-DEP-001](05-non-functional.md#nfr-dep-001) | 배포와 설정 | S |
| [NFR-I18N-001](05-non-functional.md#nfr-i18n-001) | 언어 | M |
| [NFR-USA-001](05-non-functional.md#nfr-usa-001) | 반응형 · 접근성 | M |

### 4.6 영역별 개수

| 영역 | M | S | C | 합계 |
|---|---|---|---|---|
| 워크스페이스 · 계정 (`FR-WSP`) | 22 | 8 | 0 | 30 |
| 프로젝트 (`FR-PRJ`) | 13 | 0 | 0 | 13 |
| 일감 (`FR-ISS`) | 24 | 2 | 0 | 26 |
| MCP (`FR-MCP`) | 12 | 0 | 0 | 12 |
| 알림 (`FR-NOTI`) | 4 | 1 | 1 | 6 |
| 비기능 (`NFR`) | 7 | 5 | 0 | 12 |
| **합계** | **82** | **16** | **1** | **99** |

## 5. 추적성

- 요구사항 → 설계: 각 요구사항의 `관련 설계`. 영역별 설계는 [워크스페이스 · 계정 · 권한](../design/03-workspace-identity.md) · [프로젝트](../design/04-project.md) · [일감](../design/05-issue.md) · [MCP](../design/06-mcp.md)다.
- 요구사항 → 작업: [WBS](../wbs/00-overview.md) 단계 문서의 `요구사항` 칸. [진행 현황](06-progress.md)의 "작업" 칸이 이것을 모아 보인다.
- 요구사항 → 시험: 수용 기준을 확인한 자동 테스트 이름이나 실제 흐름을 진행 줄의 "한 것"에 적는다.

## 6. 진행 관리

요구사항이 어디까지 됐는지는 **요구사항 문서 안에** 적는다. 한 쪽으로 모은 것이 [요구사항 진행 현황](06-progress.md)이고, 그 쪽은 도구가 만든다(moai-wiki 와 같은 방식).

| 무엇 | 어디에 | 어떻게 |
|---|---|---|
| 요구사항의 상태 | 그 요구사항 표의 **진행** 줄 | `할 일` · `일부 — 한 것: …. 남은 것: ….` · `완료 (날짜) — 메모` · `폐기 — 사유` |
| 확인한 수용 기준 | 그 요구사항의 **수용 기준** | 자동 테스트나 실제 화면 · 흐름으로 확인한 것만 `- [x]` 로 바꾼다. 만들었지만 확인하지 못한 것은 그대로 둔다 |
| 모아 보기 | [06-progress.md](06-progress.md) | `python3 -I tools/req_progress.py` 로 다시 만든다. 손으로 고치지 않는다 |

**상태의 뜻**

- `할 일`: 아직 손대지 않았다. 체크한 수용 기준이 없다.
- `일부`: 만든 것이 있고 남은 것이 있다. **남은 것을 반드시 적는다** — 세부 요구사항 가운데 안 만든 것, 확인하지 못한 수용 기준.
- `완료`: 세부 요구사항을 모두 만들었고 수용 기준을 모두 확인했다. 요구사항과 다르게 만든 곳이 있으면 메모에 적는다.
- `폐기`: 하지 않기로 했다. 사유를 적는다.

**일하는 순서**

1. 작업을 시작할 때 그 작업이 다루는 요구사항을 [진행 현황](06-progress.md)에서 확인한다(WBS 작업의 요구사항 칸).
2. 작업을 끝내면 같은 작업에서 그 요구사항의 진행 줄과 수용 기준 체크를 고치고, 현황 쪽을 다시 만든다.
3. **요구사항을 하나 끝내거나 진행이 바뀔 때마다 사람에게 알린다**: 처리한 결과(무엇이 됐고 무엇으로 확인했나)와 남은 요구사항(그 요구사항의 남은 것, 전체에서 남은 수).
4. `python3 -I tools/check_docs.py docs` 가 진행 줄의 규칙과 현황 쪽이 최신인지도 본다.

## 7. 나중 후보

첫 출시 범위 밖이다. **요구사항으로 쓰지 않고 목록만 둔다.** 고를 때(WBS 5단계 협업 · 6단계 운영) 요구사항으로 옮기고 새 번호를 준다. 무엇을 먼저 할지는 [정해야 할 것](../wbs/08-decisions.md)에서 정한다.

- 사용자 정의 워크플로 · 상태
- 사용자 정의 필드
- 프로젝트별 일감 유형
- 버전과 로드맵
- 간트
- 캘린더
- 시간 기록
- 첨부
- 관찰자
- 알림과 메일
- 활동
- 전문 검색(OpenSearch)
- 프로젝트 위키
- 뉴스
- 포럼
- 하위 프로젝트
- 모듈 켜고 끄기
- 저장한 질의
- 칸반 보드
- 내보내기
- Git 연동
- 웹훅
- REST API 키
- Redmine 이관 (6단계 운영에서 다시 본다)
- 다른 프로젝트와의 연결 일감

요구사항 본문에서 "첫 출시에 없다"고 적은 것(일감 되살리기, 상위 일감 값 계산, 멘션)도 같은 성격이다. 댓글 고치기 · 지우기는 2026-11-23 에 만들었다([FR-ISS-019](03-issue.md#fr-iss-019)). "Agent 계정"(사람이 아닌 멤버로 Agent 를 두는 안)은 나중 후보가 아니라 **폐기**했다. 워크스페이스 이름으로 일하던 서비스 MCP 키도 폐기했다 — 모든 MCP 키는 사용자 계정에 묶이고 그 사람의 권한 안에서만 일한다(2026-11-22, [ADR-024](../design/00-key-decisions.md#adr-024)).

---

**관련 문서:** [요구사항 진행 현황](06-progress.md) · [프로젝트 개요](../01-overview.md) · [용어집](../02-glossary.md) · [로드맵](../plan/01-roadmap.md) · [테스트 전략](../plan/02-test-strategy.md)

[← 이전: 용어집](../02-glossary.md) | [다음: 워크스페이스 · 계정 요구사항 →](01-workspace.md)
