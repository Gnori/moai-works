[홈](../README.md) › [요구사항](00-overview.md) › 요구사항 진행 현황

# 요구사항 진행 현황

요구사항마다 어디까지 됐는지를 한 쪽에 모은다. **이 쪽은 `python3 -I tools/req_progress.py` 가 만든다 — 손으로 고치지 않는다.** 정본은 각 요구사항의 표에 있는 **진행** 줄과 수용 기준의 체크(`- [x]`)다. 고치는 방법은 [요구사항 개요 §6](00-overview.md#6-진행-관리)에 있다.

**바로가기:** [한눈에](#1-한눈에) · [하고 있는 요구사항](#2-하고-있는-요구사항) · [끝난 요구사항](#3-끝난-요구사항) · [아직 시작하지 않은 요구사항](#4-아직-시작하지-않은-요구사항)

---

## 1. 한눈에

요구사항 90개 가운데 **끝난 것 4개 · 하고 있는 것 65개 · 시작하지 않은 것 21개**다. 수용 기준은 466개 가운데 248개를 확인했다.

| 영역 | 전체 | 완료 | 일부 | 할 일 | 확인한 수용 기준 |
|---|---|---|---|---|---|
| 워크스페이스 · 계정 (`FR-WSP`) | 29 | 1 | 21 | 7 | 67 / 150 |
| 프로젝트 (`FR-PRJ`) | 11 | 0 | 11 | 0 | 30 / 61 |
| 일감 (`FR-ISS`) | 21 | 3 | 18 | 0 | 101 / 141 |
| MCP (`FR-MCP`) | 11 | 0 | 7 | 4 | 30 / 71 |
| 알림 (`FR-NOTI`) | 6 | 0 | 5 | 1 | 17 / 20 |
| 비기능 (`NFR`) | 12 | 0 | 3 | 9 | 3 / 23 |
| **합계** | **90** | **4** | **65** | **21** | **248 / 466** |

| 우선순위 | 전체 | 완료 | 일부 | 할 일 |
|---|---|---|---|---|
| M — 꼭 필요 | 73 | 3 | 61 | 9 |
| S — 있으면 좋음 | 16 | 1 | 4 | 11 |
| C — 나중 | 1 | 0 | 0 | 1 |

- **완료**: 세부 요구사항을 모두 만들었고 수용 기준을 모두 확인했다. **일부**: 만든 것이 있고 남은 것이 있다. **할 일**: 아직 손대지 않았다.
- 수용 기준은 자동 테스트나 실제 화면 · 흐름으로 확인한 것만 체크한다. 만들었지만 확인하지 못한 것은 체크하지 않는다.
- "작업"은 그 요구사항을 다루는 [WBS](../wbs/00-overview.md) 작업과 그 상태다.

## 2. 하고 있는 요구사항

| 요구사항 | 우선 | 한 것 | 남은 것 | 수용 기준 | 작업 |
|---|---|---|---|---|---|
| [FR-WSP-001](01-workspace.md#fr-wsp-001) 회원가입 | M | 코드(moai-wiki 에서 가져옴 — api 가 Keycloak 에 사용자를 만들고 사본을 저장, 비밀번호 정책 검사, 같은 이메일 거절, 저장 실패 때 Keycloak 사용자 삭제(보상), IP별 속도 제한, 감사) | 가입 흐름의 자동 시험(정상 · 중간 실패 주입 · 정책 위반 비밀번호 · 속도 제한) · moai-works 렐름에서 가입 화면 → 계정 생성 흐름 확인 | 0 / 6 | [1.3](../wbs/02-stage-1-foundation.md#w-1-3) 진행 |
| [FR-WSP-002](01-workspace.md#fr-wsp-002) 가입 정책 설정 (시스템 관리자) | M | 코드(moai-wiki 에서 가져옴 — 가입 받기 · 허용 도메인 · 이메일 인증 · 관리자 승인 · 속도 제한 · 팀 워크스페이스 기본 허용 수 설정, 시스템 관리자만 바꾸고 감사에 남김) | 정책 API 의 자동 시험(권한 · 도메인 밖 가입 거부 · 세 스위치 8가지 조합) · moai-works 렐름에서 시스템 관리자 화면 확인 | 0 / 4 | [1.3](../wbs/02-stage-1-foundation.md#w-1-3) 진행 |
| [FR-WSP-003](01-workspace.md#fr-wsp-003) 이메일 인증 | M | 코드(moai-wiki 에서 가져옴 — 6자리 코드 · 10분 · 5회 · HMAC 만 저장 · 확인하면 Keycloak 인증 표시 · 재발송 속도 제한) | 인증 대기 7일 뒤 계정 삭제(예약 작업이 없다) · 메일 실제 발송(지금은 로그 모드, SMTP 미정) · 자동 시험(만료 · 초과 코드 · 원문 미저장) · moai-works 렐름에서 흐름 확인 | 0 / 4 | [1.3](../wbs/02-stage-1-foundation.md#w-1-3) 진행 |
| [FR-WSP-004](01-workspace.md#fr-wsp-004) 가입 승인 (선택) | M | 코드(moai-wiki 에서 가져옴 — 승인 대기 목록 · 승인 · 거절 API 와 화면, 승인 전 로그인 불가, 승인하면 개인 워크스페이스 생성, 감사) | 결과를 메일로 알리기 · 승인 대기 30일 뒤 계정 삭제 · 자동 시험 · moai-works 렐름에서 흐름 확인 | 0 / 3 | [1.3](../wbs/02-stage-1-foundation.md#w-1-3) 진행 |
| [FR-WSP-007](01-workspace.md#fr-wsp-007) 개인 워크스페이스 자동 생성 | M | 코드(moai-wiki 에서 가져옴, 기본 위키 단계는 뺌 — 계정이 활성화되거나 처음 로그인하면 단계별로 만든다, 멱등, 끊기면 다음 요청이 이어서 끝냄) · 자동 테스트(첫 로그인 뒤 생성 · 남의 개인 워크스페이스 404 · 나가기와 가입 신청 거부 확인) | 단계마다 실패를 주입하는 자동 시험 · 주인이 모든 권한 코드를 갖는지 시험 · 감사 시험 | 3 / 6 | [1.3](../wbs/02-stage-1-foundation.md#w-1-3) 진행 · [1.7](../wbs/02-stage-1-foundation.md#w-1-7) 완료 |
| [FR-WSP-008](01-workspace.md#fr-wsp-008) 팀 워크스페이스 만들기 | S | 코드(moai-wiki 에서 가져옴) · 자동 테스트(허용받지 않은 계정 거부 · 허용 수 한도 · 한도 올리기 · 0 으로 내려도 기존 유지 · 만든 사람 = 소유자 확인) | 주소 고유 시험 · 감사 시험 · moai-works 렐름에서 화면 확인(만들 수 없는 계정에 만들기 입구가 없고 이유가 보이는지) | 4 / 7 | [1.3](../wbs/02-stage-1-foundation.md#w-1-3) 진행 · [1.7](../wbs/02-stage-1-foundation.md#w-1-7) 완료 |
| [FR-WSP-009](01-workspace.md#fr-wsp-009) 워크스페이스 설정 | M | 코드(moai-wiki 에서 가져옴) · 자동 테스트(프라이빗은 찾기 · 신청에서 숨김 · 프라이빗의 가입 방식 정리 · 권한 없는 멤버 403 · 멤버 아님 404 · 소유자 역할은 기본 역할 불가 확인) | 설정 변경 감사 시험 · moai-works 렐름에서 설정 화면 확인 | 4 / 5 | [1.3](../wbs/02-stage-1-foundation.md#w-1-3) 진행 |
| [FR-WSP-010](01-workspace.md#fr-wsp-010) 가입 신청 · 철회 | M | 코드(moai-wiki 에서 가져옴) · 자동 테스트(대기 신청 하나 · 본인만 철회 · 거절 뒤 재신청 대기 · 허용 도메인 · 자동 가입 · 이미 멤버 거부 확인) | 대기 신청 30일 뒤 만료(예약 작업이 없다) · 감사 시험 · moai-works 렐름에서 찾기 · 신청 화면 확인 | 6 / 8 | [1.3](../wbs/02-stage-1-foundation.md#w-1-3) 진행 |
| [FR-WSP-011](01-workspace.md#fr-wsp-011) 가입 신청 승인 · 거절 | M | 코드(moai-wiki 에서 가져옴) · 자동 테스트(승인 즉시 접근 · 승인자가 줄 수 있는 역할만 · 기본 역할 · 다시 처리 금지 · 다른 워크스페이스 신청 404 · 권한 없으면 403 · Keycloak 과 사본 일치 확인) | 신청자 · 관리자 알림(알림 영역) · 감사 시험 · moai-works 렐름에서 화면 확인 | 6 / 8 | [1.3](../wbs/02-stage-1-foundation.md#w-1-3) 진행 |
| [FR-WSP-012](01-workspace.md#fr-wsp-012) 초대 | S | 코드(moai-wiki 에서 가져옴) · 자동 테스트(링크로 수락 · 쓴 링크 재사용 금지 · 초대받은 이메일만 수락 · 초대 권한 · 줄 수 있는 역할만 · 대기 초대 하나 · 취소한 초대 거부 · 받은 초대 거절 확인) | 만료된 초대 시험 · 초대 메일 실제 발송(지금은 로그 모드) · 감사 시험 · moai-works 렐름에서 초대 링크 화면(로그인 · 가입 뒤 돌아오기) 확인 | 5 / 8 | [1.3](../wbs/02-stage-1-foundation.md#w-1-3) 진행 |
| [FR-WSP-013](01-workspace.md#fr-wsp-013) 멤버 제거(가입 취소) · 정지 · 탈퇴 | M | 코드(moai-wiki 에서 가져옴 — 내보내기 · 나가기) · 자동 테스트(내보내기 · 나가기 뒤 바로 404 · Keycloak 과 사본에서 함께 빠짐 · 소유자 보호 · 마지막 소유자 보호 · 권한 없으면 403 확인) | 멤버 정지 · 복구 · 프로젝트 역할에서 빼기(프로젝트 영역) · 감사 시험. (MCP 키는 2026-11-23 부터 만료시키지 않는다 — 내보낸 다음 호출부터 그 워크스페이스가 키에게 보이지 않는 것을 `McpKeyRuleTest` 가 본다, [ADR-029](../design/00-key-decisions.md#adr-029)) | 6 / 8 | [1.3](../wbs/02-stage-1-foundation.md#w-1-3) 진행 |
| [FR-WSP-015](01-workspace.md#fr-wsp-015) 권한 코드 카탈로그와 기본 역할 | M | 코드(moai-wiki 에서 가져옴, 위키 · 자료실 코드와 curator 역할을 뺌 — 권한 코드 16개, 기본 역할 3개, 기동 때 DB 표 맞추기와 Keycloak 대조, 카탈로그가 바뀌면 기존 워크스페이스 역할 맞추기) · 자동 테스트(설치 파일과 코드 일치 · 소유자 전용 코드 · 기본 역할 권한 · 카탈로그 이행 · 내 권한 = 역할 합집합 · 기본 역할 고치기 금지 확인) | 역할 × API 전체 조합 시험 · moai-works 렐름에서 기동 대조 확인 · 계획한 권한 코드는 그 기능을 만들 때 더한다 | 5 / 8 | [1.1](../wbs/02-stage-1-foundation.md#w-1-1) 완료 · [2.1](../wbs/03-stage-2-project.md#w-2-1) 완료 |
| [FR-WSP-016](01-workspace.md#fr-wsp-016) 사용자 정의 역할 | M | 코드(moai-wiki 에서 가져옴) · 자동 테스트(만들기 · 권한 상승 거부 · 소유자 전용과 없는 코드 거부 · 자기보다 큰 역할 고치기 거부 · 쓰는 역할과 기본 역할로 지정된 역할 삭제 거부 · 권한 없으면 403 확인) | 역할의 권한을 고친 뒤 다음 요청부터 적용되는지 시험 · 감사 시험 · moai-works 렐름에서 역할 화면 확인 | 5 / 7 | [1.1](../wbs/02-stage-1-foundation.md#w-1-1) 완료 · [2.1](../wbs/03-stage-2-project.md#w-2-1) 완료 |
| [FR-WSP-017](01-workspace.md#fr-wsp-017) Keycloak 정본과 api 사본의 동기화 · 대사 | M | 코드(moai-wiki 에서 가져옴 — Keycloak 쓰기는 api 만 하고, 쓰기 기록 · 멱등 재시도 · 보상으로 이중 쓰기를 맞춘다) · 자동 테스트(역할 변경 · 멤버 제거 때 Keycloak 그룹과 사본이 같이 바뀜 · 카탈로그 이행이 Keycloak 먼저 확인) | 15분마다 대사 · 실패한 쓰기 다시 시도 작업 · 중간 실패 주입 시험 · Keycloak 장애 때 조회가 계속되는지 확인 | 1 / 4 | [1.6](../wbs/02-stage-1-foundation.md#w-1-6) 할 일 |
| [FR-WSP-018](01-workspace.md#fr-wsp-018) 워크스페이스 감사 · 사용량 조회 | M | 코드(moai-wiki 에서 가져옴 — 감사 기록에 워크스페이스를 남기고 그 워크스페이스 기록만 보인다) · 자동 테스트(`audit.read` 만 · 멤버 아님 404 · 시스템 관리자도 멤버 아니면 404 확인) | 다른 워크스페이스 기록이 섞이지 않는지 시험 · 주체 종류 · 경로로 거르기 · 사용량 조회 | 1 / 4 | [1.4](../wbs/02-stage-1-foundation.md#w-1-4) 할 일 |
| [FR-WSP-019](01-workspace.md#fr-wsp-019) 시스템 관리자 기능 | M | 코드(moai-wiki 에서 가져옴 — 가입 정책 · 가입 승인 · 계정 정지 · 복구 · 팀 워크스페이스 허용 수, 시스템 관리자만; 2026-11-22 시스템 관리자는 워크스페이스가 없고 만들거나 들어갈 수 없음 · 로그인하면 시스템 관리 화면) · 자동 테스트(플랫폼 역할 없으면 403 · 멤버 아닌 워크스페이스 404 · 계정 정지 즉시 반영과 복구 · 자기 정지 금지 · 허용 수 범위 확인) | 팀 워크스페이스 사용 중지 · 다시 사용 · 사용 중지 뒤에만 삭제 · 소유자 재지정 · 계정 삭제 · 플랫폼 감사 화면 · 직무 분리 경고 · 조치 감사 시험 · moai-works 렐름에서 화면 확인 | 8 / 12 | [1.3](../wbs/02-stage-1-foundation.md#w-1-3) 진행 · [1.7](../wbs/02-stage-1-foundation.md#w-1-7) 완료 · [1.8](../wbs/02-stage-1-foundation.md#w-1-8) 할 일 |
| [FR-WSP-022](01-workspace.md#fr-wsp-022) 인증 — 모든 입구는 인증을 거친다 | M | 코드(moai-wiki 에서 가져옴 — REST 는 Keycloak JWT 를 JWKS 로 api 가 직접 검증, 공개 경로는 가입뿐, 서비스 계정 토큰은 사람용 API 를 못 씀) · 자동 테스트(토큰 없음 · 만료 · 다른 audience · 다른 발급자 · 다른 키 서명 401, 서비스 계정 403, 가입 정책은 토큰 없이 확인) | moai-works 렐름이 발급한 실제 토큰으로 확인 | 4 / 5 | [1.3](../wbs/02-stage-1-foundation.md#w-1-3) 진행 |
| [FR-WSP-023](01-workspace.md#fr-wsp-023) 권한 판정은 api 한 곳에서 | M | 코드(moai-wiki 에서 가져옴 — REST 핸들러마다 접근 어노테이션 정확히 하나, 없으면 기동 실패, 워크스페이스 판정은 한 서비스에서 DB 사본으로) · 자동 테스트(모든 워크스페이스 경로의 404 · 403, 없는 것과 남의 것 구별 불가, 역할 변경이 다음 요청부터, 모든 테스트가 기동 검사를 지남) | 어노테이션이 빠졌을 때 기동이 멈추는지 부정 시험 · 프로젝트 · 일감 경로 판정(프로젝트 영역) · MCP 도구가 같은 판정을 쓰는지(MCP 영역) | 4 / 6 | [1.1](../wbs/02-stage-1-foundation.md#w-1-1) 완료 · [2.2](../wbs/03-stage-2-project.md#w-2-2) 완료 |
| [FR-WSP-024](01-workspace.md#fr-wsp-024) 감사 기록 | M | 코드(moai-wiki 에서 가져옴 — `audit_log` 추가 전용(DB 트리거가 고치기 · 지우기를 막음), 업무와 같은 트랜잭션에 저장, 계정 · 가입 정책 · 워크스페이스 · 멤버 · 가입 신청 · 초대 · 역할 행동 기록, 비밀값은 넣지 않음) | 주체 종류 서비스(Keycloak 서비스 계정)와 경로(웹 · REST · MCP, 엔드포인트 · 키 id) 기록 · MCP 키 발급 · 만료시키기 기록 · trace_id 채우기 · 보존 · 변조 감지 · 자동 시험 | 0 / 6 | [1.4](../wbs/02-stage-1-foundation.md#w-1-4) 할 일 · [4.8](../wbs/05-stage-4-mcp.md#w-4-8) 할 일 |
| [FR-WSP-026](01-workspace.md#fr-wsp-026) 전송 · 저장 암호화 | M | 코드(moai-wiki 에서 가져옴 — api · web-backend 는 기본으로 HTTPS 로만 연다, localhost 가 아닌 호스트에만 HSTS, 세션 쿠키 `__Host-` · Secure · HttpOnly) | moai-works 서비스를 띄워 평문 HTTP 거부 확인 · HSTS 규칙 시험 · 운영 저장소 암호화 점검(PostgreSQL · MinIO · OpenSearch · 백업) | 0 / 3 | [6.3](../wbs/07-stage-6-operations.md#w-6-3) 할 일 |
| [FR-WSP-027](01-workspace.md#fr-wsp-027) 비밀값 · 자격 증명 관리 | M | 코드(moai-wiki 에서 가져옴 — 비밀값은 git 에서 뺀 `.env` 와 환경 변수로만 넣고 설정 파일에는 키 이름만, DB 에는 해시만(MCP 키 · 초대 토큰 SHA-256, 인증 코드 HMAC), 감사에 비밀값을 넣지 않음, 코드 HMAC 비밀값이 없거나 짧으면 기동하지 않음) | 로그 · DB 전수 검사 · CI 비밀 검사 · 교체 주기 알림 · 운영 비밀 저장소 | 0 / 3 | [6.3](../wbs/07-stage-6-operations.md#w-6-3) 할 일 |
| [FR-PRJ-001](02-project.md#fr-prj-001) 프로젝트 만들기 | M | api(`POST …/projects`, key 규칙 · 예약어 · 중복, 감사 `project.create`) · 자동 테스트 | 같은 key 동시 만들기 시험 · 화면 | 6 / 7 | [2.2](../wbs/03-stage-2-project.md#w-2-2) 완료 · [2.3](../wbs/03-stage-2-project.md#w-2-3) 진행 |
| [FR-PRJ-002](02-project.md#fr-prj-002) 프로젝트 목록 | M | api(볼 수 있는 프로젝트만 · 보관은 `?archived=true` 로 관리하는 사람에게만) · 자동 테스트 | 다른 워크스페이스 프로젝트가 섞이지 않는지 목록 시험 · 화면 | 3 / 4 | [2.2](../wbs/03-stage-2-project.md#w-2-2) 완료 · [2.3](../wbs/03-stage-2-project.md#w-2-3) 진행 |
| [FR-PRJ-003](02-project.md#fr-prj-003) 프로젝트 설정 | M | api(`PATCH`, project.manage, key 는 바뀌지 않음, 감사 전 · 후) · 자동 테스트 | project-editor 403 시험 · 감사 값 시험 · 화면 | 3 / 5 | [2.2](../wbs/03-stage-2-project.md#w-2-2) 완료 · [2.3](../wbs/03-stage-2-project.md#w-2-3) 진행 |
| [FR-PRJ-004](02-project.md#fr-prj-004) 보관 · 복원 | M | api(보관 · 복원은 project.delete, 보관한 프로젝트의 일감 쓰기 409 `PROJECT_NOT_WRITABLE`) · 자동 테스트 | 고치기 409 · 복원 뒤 번호 이어짐 · 감사 시험 · 화면 | 2 / 5 | [2.2](../wbs/03-stage-2-project.md#w-2-2) 완료 · [2.3](../wbs/03-stage-2-project.md#w-2-3) 진행 |
| [FR-PRJ-005](02-project.md#fr-prj-005) 삭제 | M | api(보관한 것만 · `?confirm=key` · 소프트 삭제 · key 다시 쓰기) · 자동 테스트 | 지운 뒤 Keycloak 그룹 · 역할 사본 정리 · MCP 엔드포인트 끄기(4단계) · 감사 시험 · 화면 | 2 / 6 | [2.2](../wbs/03-stage-2-project.md#w-2-2) 완료 · [2.3](../wbs/03-stage-2-project.md#w-2-3) 진행 |
| [FR-PRJ-006](02-project.md#fr-prj-006) 공개 범위와 독자 판정 | M | api(`@ProjectApi` · `ProjectAccessService` — moai-wiki 의 위키 판정과 같은 식, 기동 검사) · 자동 테스트 | 정지한 멤버 404 시험 | 5 / 6 | [2.2](../wbs/03-stage-2-project.md#w-2-2) 완료 · [2.4](../wbs/03-stage-2-project.md#w-2-4) 진행 |
| [FR-PRJ-007](02-project.md#fr-prj-007) 프로젝트 역할 | M | api(역할 넷 · 여러 개 합치기 · 권한 상승 방지 `ESCALATION_DENIED` · 감사 `project.roles.update`) · 자동 테스트 | 프로젝트 역할별 일감 쓰기 시험(편집자 관리자 바꾸기 · 지우기 403, 큐레이터 관리자 바꾸기) · 화면 | 7 / 9 | [2.4](../wbs/03-stage-2-project.md#w-2-4) 진행 |
| [FR-PRJ-008](02-project.md#fr-prj-008) 프로젝트 역할의 정본 (Keycloak 그룹) | M | api(Keycloak 조직 그룹 `/projects/<id>/<role>` 에 먼저 쓰고 사본, 쓰기 기록으로 이어서 끝냄, 워크스페이스를 나가면 사본 삭제) · 자동 테스트(가짜 Keycloak 그룹 소속) | 사본의 그룹 id · 매핑 없음 시험 · 실패 주입 시험 · 대사(15분) · 나간 사람의 Keycloak 그룹 시험 | 0 / 5 | [2.4](../wbs/03-stage-2-project.md#w-2-4) 진행 |
| [FR-PRJ-009](02-project.md#fr-prj-009) 기본 프로젝트 (내 프로젝트) | M | api(개인 워크스페이스를 만들 때 "내 프로젝트", 프로젝트가 한 번도 없었을 때만) · 자동 테스트 | 두 번 돌려도 하나 · 지운 뒤 다시 안 생김 · 주인의 일감 만들기 · 감사 시험 | 1 / 5 | [2.2](../wbs/03-stage-2-project.md#w-2-2) 완료 |
| [FR-PRJ-010](02-project.md#fr-prj-010) 프로젝트 화면 | M | 화면(프로젝트 목록 · 카드 · 만들기 · 설정 대화 상자 · 보관함 · 지우기 대화 상자 · 프로젝트 홈 · 멤버 화면 — moai-wiki 의 위키 화면 구조) · 화면 자동 테스트 · 브라우저 확인(프로젝트 홈 · 멤버 화면, 데스크톱 · 390px · 다크 · 영어, 멤버에게 설정 · 보관 단추가 없음) | 브라우저에서 만들기 · 보관 · 복원 · 지우기 흐름과 멤버 화면의 역할 주기 확인 | 1 / 6 | [2.3](../wbs/03-stage-2-project.md#w-2-3) 진행 · [2.4](../wbs/03-stage-2-project.md#w-2-4) 진행 |
| [FR-PRJ-011](02-project.md#fr-prj-011) 프로젝트 감사 | M | api(만들기 · 설정 · 보관 · 복원 · 삭제 · 역할 바꾸기를 업무와 같은 트랜잭션으로 감사) | 행동별 값 시험 · 실패 시 되돌림 시험 · audit.read 시험 | 0 / 3 | [2.2](../wbs/03-stage-2-project.md#w-2-2) 완료 · [2.4](../wbs/03-stage-2-project.md#w-2-4) 진행 |
| [FR-ISS-001](03-issue.md#fr-iss-001) 일감 만들기 | M | api(프로젝트 행을 잠가 순번, 관리자 기본 = 만든 사람, 이력 첫 줄 · 감사 `issue.create`) · 자동 테스트 | 동시 20개 번호 · 지운 번호 재사용 안 함 · 다른 관리자 지정 403 · 길이 상한 시험 · 화면 | 4 / 8 | [3.1](../wbs/04-stage-3-issue.md#w-3-1) 완료 · [3.2](../wbs/04-stage-3-issue.md#w-3-2) 완료 |
| [FR-ISS-002](03-issue.md#fr-iss-002) 일감 보기 | M | api(하위 · 연결(양쪽) · 이력을 함께) · 자동 테스트 | 이력 100줄 넘을 때 커서 · 화면의 HTML 막기 | 3 / 5 | [3.2](../wbs/04-stage-3-issue.md#w-3-2) 완료 · [3.5](../wbs/04-stage-3-issue.md#w-3-5) 진행 |
| [FR-ISS-003](03-issue.md#fr-iss-003) 일감 고치기 (동시 수정 포함) | M | api(넣은 필드만 · rowVersion 필수 · 409 `CONCURRENT_UPDATE` · 이력 한 줄 · 감사 `issue.update`) · 자동 테스트 | 동시 요청 시험 · 바뀐 값 없음 시험 · 403 때 다른 필드 그대로 시험 · 다른 프로젝트 편집자 시험 | 3 / 7 | [3.2](../wbs/04-stage-3-issue.md#w-3-2) 완료 · [3.5](../wbs/04-stage-3-issue.md#w-3-5) 진행 |
| [FR-ISS-004](03-issue.md#fr-iss-004) 일감 유형 | M | api(쓰는 유형 넷 · 옛 유형 둘은 더 쓰지 않음 · 순서 · 없는 유형 400 · 옛 유형 400 `ISSUE_TYPE_RETIRED`) · 화면(새 일감 · 고치기에서 쓰는 유형만) · 자동 테스트 · ego-browser | 유형 바꾸기 이력 시험 | 4 / 5 | [3.1](../wbs/04-stage-3-issue.md#w-3-1) 완료 |
| [FR-ISS-005](03-issue.md#fr-iss-005) 상태 | M | api(고정 다섯 — 2026-11-22 테스트 더함, 완료일 · 종료일 자동 채움과 비움, 직접 넣은 날짜는 그대로) · 화면(상태 바꾸기 메뉴 다섯) · 자동 테스트 · ego-browser | 자동 날짜의 이력 시험 · 상태 정렬 순서(지금 api 는 코드값 글자순) | 7 / 8 | [3.2](../wbs/04-stage-3-issue.md#w-3-2) 완료 |
| [FR-ISS-006](03-issue.md#fr-iss-006) 관리자와 담당자 | M | api(관리자 필수 · 바꾸기는 issue.manager.assign · 관리자 · 담당자는 프로젝트 독자만 `USER_NOT_PROJECT_READER` · 담당자 비우기) · 자동 테스트 | 지정 멤버 프로젝트의 비독자 멤버 시험 · 관리자 비우기 400 시험 · 떠난 멤버 표시(화면) | 2 / 5 | [3.2](../wbs/04-stage-3-issue.md#w-3-2) 완료 |
| [FR-ISS-007](03-issue.md#fr-iss-007) 날짜 (완료 기한 · 시작일 · 완료 예정일 · 완료일 · 종료일) | M | api(다섯 날짜, 값이 둘 다 있을 때만 순서 검사 `INVALID_ISSUE_DATES`) · 자동 테스트 | 한쪽만 있을 때 시험 · "기한 지남" 표시(화면) | 3 / 5 | [3.2](../wbs/04-stage-3-issue.md#w-3-2) 완료 |
| [FR-ISS-008](03-issue.md#fr-iss-008) 상위 · 하위 일감 | M | api(같은 프로젝트 FK · 순환 `PARENT_CYCLE` · 10단계 `PARENT_DEPTH_EXCEEDED` · 없는 상위 `PARENT_NOT_FOUND`) · 자동 테스트 | 동시 옮기기 순환 시험 · 상위 빼기 이력 시험 | 3 / 5 | [3.3](../wbs/04-stage-3-issue.md#w-3-3) 완료 · [3.5](../wbs/04-stage-3-issue.md#w-3-5) 진행 |
| [FR-ISS-009](03-issue.md#fr-iss-009) 연결 일감 | M | api(네 종류 · 한 쌍에 하나 · 양쪽 이력 · 감사 `issue.link.add` · `issue.link.remove`) · 자동 테스트 | 다른 일감의 연결 id 404 시험 · 양쪽 이력 시험 · 화면 문구 | 4 / 6 | [3.3](../wbs/04-stage-3-issue.md#w-3-3) 완료 · [3.5](../wbs/04-stage-3-issue.md#w-3-5) 진행 |
| [FR-ISS-010](03-issue.md#fr-iss-010) 이력 | M | api(`issue_journal` — 고칠 때마다 한 줄, 주체 · 경로, 고치는 경로 없음, 쓰기 여섯 가지 감사) · 자동 테스트 | MCP 경로 표시(4단계) · 저장 실패 되돌림 시험 | 3 / 5 | [3.2](../wbs/04-stage-3-issue.md#w-3-2) 완료 · [3.5](../wbs/04-stage-3-issue.md#w-3-5) 진행 |
| [FR-ISS-011](03-issue.md#fr-iss-011) 댓글 | M | api(`issue.comment`, notes 만 있는 이력 줄, 감사) · 자동 테스트 | 30001자 · issue.read 만 있는 역할 403 · 닫힌 일감 댓글 시험 | 2 / 5 | [3.2](../wbs/04-stage-3-issue.md#w-3-2) 완료 · [3.5](../wbs/04-stage-3-issue.md#w-3-5) 진행 |
| [FR-ISS-012](03-issue.md#fr-iss-012) 일감 목록 · 필터 · 정렬 | M | api(상태(open · closed 별칭) · 유형 · 관리자 · 담당자(me · none) · 상위(top · none) · 검색어 · 정렬 · 쪽) · 자동 테스트 | 쪽 크기 상한 · 지운 일감 제외 시험 · MCP 와 같은 결과(4단계) | 5 / 8 | [3.4](../wbs/04-stage-3-issue.md#w-3-4) 완료 · [3.5](../wbs/04-stage-3-issue.md#w-3-5) 진행 |
| [FR-ISS-013](03-issue.md#fr-iss-013) 일감 지우기 | M | api(소프트 삭제 · 하위가 있으면 409 · 연결 함께 지우고 상대 이력에 남김 · 감사) · 자동 테스트 | 상대 이력 시험 · 프로젝트 역할별 지우기 시험 · MCP 에 지우기 도구 없음(4단계) | 3 / 6 | [3.2](../wbs/04-stage-3-issue.md#w-3-2) 완료 |
| [FR-ISS-014](03-issue.md#fr-iss-014) 일감 화면 | M | 화면(목록 — 데스크톱 표 · 390px 카드, 필터를 주소에 둠 · 새 일감 · 보기 · 고치기 · 상태 바꾸기 · 하위 · 연결 · 이력 · 댓글 · 지우기 대화 상자) · 화면 자동 테스트(필터 ↔ 주소 · 바뀐 필드만 보내기 · 이력 읽기) · 브라우저 확인. 2026-11-23: 일감 보기 배치를 가운데 중심으로(프로젝트 메뉴 한 줄 · 제목 한 줄 · 동작 정리 · 속성 칸은 받은 너비로 오른쪽/요약 · 본문 1200px 까지 · 빈 칸 한 줄 · 작업 시간 양식 접기) | 새로 고침 뒤 같은 목록 · 두 창 충돌 알림을 브라우저로 확인 · MCP 표시(4단계) | 4 / 7 | [3.5](../wbs/04-stage-3-issue.md#w-3-5) 진행 |
| [FR-ISS-015](03-issue.md#fr-iss-015) 리비전 · 타임라인 · 차이(diff) | M | api(`issue_revision` 추가 전용 · 만들 때 1판 · 필드가 바뀔 때마다 새 판 · 누가 · 웹/MCP · 판 목록 · 한 판 · 두 판의 차이 — 설명은 줄 단위) · 자동 테스트. · 화면(`issues/:number/history` — 타임라인 · 앞 판과의 차이 · 두 판 견주기 · 한 판 전체, 밀리초 시각, moai-wiki 페이지 이력 화면 구조) · 화면 자동 테스트 · 브라우저 확인(데스크톱 · 390px · 다크) | 영어 화면 확인 · MCP 로 고친 판 표시 확인 · MCP 읽기 도구(4단계) | 7 / 9 | [3.6](../wbs/04-stage-3-issue.md#w-3-6) 진행 |
| [FR-ISS-016](03-issue.md#fr-iss-016) 작업 시간 (예상 공수 · 소요 시간 기록) | M | api(예상 공수 필드 — 판에 들어감 · 작업 시간 기록 여러 건 · 누가 · 날 · 시간 · 메모 · 웹/MCP · 지운 사람 · 쓴 시간 합계 · 목록에 예상 · 쓴 시간 · 이력 · 감사) · 자동 테스트. · 화면(일감 보기의 예상 공수 · 쓴 시간 · 진행 비율 · 작업 시간 목록과 적기 · 지우기, 새 일감 · 고치기의 예상 공수, 목록의 예상 · 쓴 시간) · 화면 자동 테스트 · 브라우저 확인(보기) | 브라우저에서 적기 · 지우기 흐름과 영어 확인 · MCP 도구(`log_work`, 4단계) | 7 / 9 | [3.7](../wbs/04-stage-3-issue.md#w-3-7) 진행 |
| [FR-ISS-017](03-issue.md#fr-iss-017) 공수 집계 (예상 대비 실제 투입 공수) | S | api(`GET /v1/workspaces/{ws}/effort` · `…/effort.csv`, 읽을 수 있는 프로젝트만 · 기간 · 사람 · 프로젝트 · 유형 조건 · 사람 · 프로젝트 · 일감 · 유형 · 날 · 주 · 달로 모으기 · 예상 대비 실제(합계 · 차이 · 비율) · 지운 기록 수 · CSV 감사 `issue.work_log.export`) · 자동 테스트(EffortTest 6건) · 화면(워크스페이스 메뉴 "공수 집계" · 프로젝트 메뉴 "공수" — 조건 · 표 · 막대 · 합계 · CSV 내려받기) · 화면 자동 테스트(effort 19건 · 경로 2건) | ego-browser 확인(데스크톱 · 390px · 다크 · 영어)과 web-backend(11002) 중계로 실제 CSV 내려받기 | 4 / 5 | [3.8](../wbs/04-stage-3-issue.md#w-3-8) 진행 |
| [FR-ISS-021](03-issue.md#fr-iss-021) Markdown 편집기 (쓰면서 바로 그려지는 편집 · 도구 막대 · 목차 · 원문) | M | 2026-11-23: Milkdown(Crepe) 편집기 · 위 고정 도구 막대 · 목차 · Markdown 원문 보기, 일감 설명 · 댓글 모두, 원문 보존(moai-wiki 와 같은 source-blocks — 실제 글 55건 열자마자 저장 = 원문), 첨부 그림 보이기 · 붙여 넣기 · 끌어 놓기 · 단추로 올리기, CSP 확인, 화면 자동 테스트 · ego-browser. 2026-11-23 보안: 편집기 schema 의 그림 toDOM 은 `attachment:` 만 `<img>`(글자로 붙여 넣기 · insert · 끌어 놓기에서 바깥 `<img>` 0회 확인). 2026-11-23: 파일 붙여 넣기 · 끌어 놓기를 기본 올리기 플러그인보다 먼저 받음(전에는 blob: 그림이 들어가거나 파일을 버림), 영어 화면 확인. 2026-11-23 큰 글(일감 #16): 바뀐 맨 위 덩어리만 다시 적고 원문 보존도 바뀐 곳 둘레만 다시 나눔(저장 글은 통째로 한 것과 같음) — 3만 자에서 멈춘 뒤 일 약 190–255ms → 26–70ms(긴 작업 없음), 이웃한 목록 하나만 고쳐도 다시 읽으면 목록 수가 그대로(원문 보존이 원문 `*` 뒤 편집기 `*` 를 붙여 합쳐지던 것 · 목록 뒤 들여 쓴 코드 고침) | 표 칸 단위 고치기 보존(덩어리 단위의 한계) | 9 / 9 | — |
| [FR-MCP-001](04-mcp.md#fr-mcp-001) MCP 키 (사용자 키 · 권한 · 감사) | M | 2026-11-22 발급 · 목록 · 만료시키기 · 감사(IP · User-Agent), DB 트리거로 지우기 · 발급 정보 변경 · 기간 늘리기 금지, 요청마다 캐시 없이 검사. 2026-11-23 키를 입구에 묶지 않음(V11). 2026-11-23 키는 사용자의 것([ADR-029](../design/00-key-decisions.md#adr-029)) — V13 이 `workspace_id` 를 지움(지금 키는 그대로, `McpKeyUserMigrationTest`), 발급 · 목록 · 만료는 개인 설정 `/v1/me/mcp-keys`(`@UserApi`, 만료는 주인만, MCP 권한이 있는 워크스페이스가 없으면 `MCP_KEY_ISSUE_NOT_ALLOWED`), 워크스페이스 관리 화면은 멤버들의 키를 보기만(`mcp.key.manage`), 떠나도 키를 만료시키지 않음(그 워크스페이스만 보이지 않음), 키 발급 · 만료 감사는 사용자 단위, 키 검사는 주인 계정만(`MCP_WORKSPACE_INACTIVE` 없앰), 자동 테스트(`McpKeyRuleTest` 18건 · `McpKeyUserMigrationTest`) | 키 접근 기록과 사람별 접근 기록 거르기([FR-MCP-006](04-mcp.md#fr-mcp-006)) · 옛 경로가 없음을 시험으로 확인 | 14 / 16 | [4.8](../wbs/05-stage-4-mcp.md#w-4-8) 할 일 |
| [FR-MCP-002](04-mcp.md#fr-mcp-002) MCP 입구 · 워크스페이스 고르기 | M | 2026-11-23 입구를 기능으로 나눔 — 고정 입구 일감 `/mcp/issue` · 프로젝트 관리 `/mcp/project`(입구마다 도구 묶음, [ADR-028](../design/00-key-decisions.md#adr-028)). 2026-11-23 워크스페이스별 엔드포인트 계획을 버리고 워크스페이스를 호출마다 고르게 함([ADR-029](../design/00-key-decisions.md#adr-029)) — 프로젝트를 받는 모든 도구에 선택 인자 `workspace`, 비우면 키로 쓸 수 있는 모든 워크스페이스에서 찾기 · 여럿이면 `PROJECT_AMBIGUOUS`, 워크스페이스 기본 일은 `WORKSPACE_REQUIRED`, `list_workspaces`(두 입구) · `list_projects` 가 모든 워크스페이스에 걸침, 판정은 `McpScope` 한 곳, 첨부 티켓도 고른 워크스페이스에서 다시 판정, 자동 테스트(`McpKeyRuleTest` · `AttachmentTicketTest` · `IssueTemplateTest`) | 초기화 안내문(`instructions`)에 쓸 수 있는 워크스페이스와 규칙 담기 · 실제 MCP 클라이언트로 `list_workspaces` · `list_projects` 확인 | 6 / 7 | [4.2](../wbs/05-stage-4-mcp.md#w-4-2) 진행 |
| [FR-MCP-004](04-mcp.md#fr-mcp-004) 읽기 도구 | M | 2026-11-22: `list_projects` · `list_issue_types` · `search_issues`(제목 · 설명 · 번호 · 상태 묶음 · 유형 · 사람 · 상위 · 기한 범위 · 기한 지남 · 정렬, totalPages · hasNext · nextPage) · `get_issue` · `list_attachments`, REST 와 같은 서비스 · 권한, 자동 테스트 | 엔드포인트 범위(FR-MCP-002) · 응답 크기 상한 · 사용자 데이터 표시 | 0 / 5 | — |
| [FR-MCP-005](04-mcp.md#fr-mcp-005) 쓰기 도구 | M | 2026-11-22: `create_issue` · `update_issue`(rowVersion · clear · 함께 댓글) · `delete_issue`(사용자 결정 — issue.delete + 이유 필수) · `add_comment` · `log_work` · `link_issues` · 첨부 티켓 도구, 한 사람 = 키 주인 · 경로 MCP(이력 · 판 · 감사 · 알림), 자동 테스트 | 멱등 키 · `unlink_issues` · `set_parent` | 4 / 6 | — |
| [FR-MCP-008](04-mcp.md#fr-mcp-008) 연결 안내 화면 | M | 2026-11-22: 엔드포인트 없이 키 화면만 — 내 MCP 키(개인 설정) · 발급 대화 상자(원문 한 번 · 복사 · 다시 볼 수 없음 알림 · 연결 주소 · Claude Code 명령) · 만료시키기 · 워크스페이스 MCP 키(관리자 — 모든 키 · 주인 거르기 · 감사로 가기). ego-browser 데스크톱 · 390px 다크. 2026-11-23: 연결 안내가 두 주소(일감 `/mcp/issue` · 프로젝트 관리 `/mcp/project`)와 두 명령(`claude mcp add --scope user …` — 전역 등록)을 보인다, 화면 시험(브라우저 확인은 아직) | 엔드포인트 화면 · 일반 MCP 설정 JSON · 영어 확인 · Claude Code 로 붙여 보기 2026-11-23: 발급 창 연결 안내를 데스크톱 · 390px 로 확인, 프로젝트 관리 입구 안내 문구를 템플릿 도구에 맞춤, 키 원문이 보이는 동안 휴대폰을 돌리면 창이 닫히던 것 고침 | 1 / 4 | [4.5](../wbs/05-stage-4-mcp.md#w-4-5) 할 일 |
| [FR-MCP-009](04-mcp.md#fr-mcp-009) MCP 는 web-backend 가 중계한다 | M | 2026-11-22: web-backend `/mcp/**` 중계(`McpRelay` — 세션 · CSRF 밖 보안 체인, 헤더 허용 목록, X-Forwarded-For 새로 씀, 요청 id, 조각마다 흘려보냄, IP 별 분당 600), api 는 믿는 중계(`trusted-proxies`)의 X-Forwarded-For 만 씀(`ClientIp`). 11002 로 initialize · 도구 호출(SSE) · 만료 뒤 401 확인 | Claude Code 로 붙여 보기 · 접근 기록의 IP(접근 기록이 생기면) | 1 / 5 | — |
| [FR-MCP-011](04-mcp.md#fr-mcp-011) MCP 첨부 — 일회용 티켓으로 REST 올리기 · 받기 | M | 2026-11-22: V10 `attachment_ticket` · MCP 도구 넷(`list_attachments` · `request_attachment_upload` · `request_attachment_download` · `delete_attachment`) · `/mcp/files/upload` · `download`(web-backend 중계) · 자동 테스트 4건 · 실제 11002 로 올리고 받기 | 지난 티켓 정리 | 4 / 4 | — |
| [NFR-QLT-001](05-non-functional.md#nfr-qlt-001) 시험 | M | 토대의 api 자동 테스트 66건 · 화면 60건이 돈다 | 프로젝트 · 일감 · MCP 를 만들 때 경계 · 동등성 시험을 같은 작업에서 더하기 | 1 / 2 | [1.2](../wbs/02-stage-1-foundation.md#w-1-2) 완료 |
| [NFR-I18N-001](05-non-functional.md#nfr-i18n-001) 언어 | M | 토대 화면이 한국어 · 영어로 나온다(i18next 리소스) | 프로젝트 관리 화면 · 메일 · MCP 도구 설명문의 영어 | 1 / 2 | [2.3](../wbs/03-stage-2-project.md#w-2-3) 진행 |
| [NFR-USA-001](05-non-functional.md#nfr-usa-001) 반응형 · 접근성 | M | 2026-11-23: 왼쪽 주 메뉴 접기(68px 아이콘 막대 · 기억 · lg 아래 처음엔 접음), 곁 칸은 받은 너비로, 반응형 검사 스크립트(화면 15곳 × 1440 · 1280 · 1024 · 768 · 390 × 주 메뉴 편 것/접은 것 = 150) 가로 넘침 · 눌린 글자 0건 | 화면마다 다크 · 영어 · 키보드 확인 | 1 / 2 | [1.3](../wbs/02-stage-1-foundation.md#w-1-3) 진행 · [2.3](../wbs/03-stage-2-project.md#w-2-3) 진행 · [3.5](../wbs/04-stage-3-issue.md#w-3-5) 진행 |
| [FR-NOTI-001](07-notification.md#fr-noti-001) 알림함 | M | 2026-11-22: api(V6 · 알림함 · 읽지 않은 수 · 읽음 · 모두 읽음 · 묶기 · 시각 + id 커서) · 화면(왼쪽 아래 종 · 수, 알림함 /notifications) · 자동 테스트 · ego-browser(데스크톱) | 390px · 다크 · 영어 확인 | 5 / 5 | — |
| [FR-NOTI-002](07-notification.md#fr-noti-002) 무엇을 누구에게 알리는가 | M | 2026-11-22: 종류 21개(워크스페이스 5 · 프로젝트 1 · 일감 11 · 작업 시간 2 · MCP 키 2 — 이 가운데 셋은 아침 알림 — FR-NOTI-003), 각 기능의 트랜잭션에 연결, 자동 테스트 | 화면 문장 · 링크 | 6 / 7 | — |
| [FR-NOTI-003](07-notification.md#fr-noti-003) 아침 알림 (기한 · 키 만료) | M | 2026-11-22: `NotificationReminders`(매일 09:00 Asia/Seoul, DB 권고 잠금, 같은 값으로 두 번 보내지 않음), 자동 테스트 | 운영에서 하루 지켜보기 | 2 / 2 | — |
| [FR-NOTI-004](07-notification.md#fr-noti-004) 알림 받기 설정 | S | 2026-11-22: api · 화면(계정 메뉴 › 개인 설정 › 알림 설정 — 묶음별 켬 · 끔, 끌 수 없는 것은 잠금) · 자동 테스트 · ego-browser | 영어 확인 | 3 / 3 | — |
| [FR-NOTI-005](07-notification.md#fr-noti-005) 알림 화면 | M | 2026-11-22: 화면(종 · 읽지 않은 수 1분마다 · 창으로 돌아올 때, 알림함 탭 · 모두 읽음 · 더 보기, 누르면 읽음 + 그 화면, 종류 21 문장 · 링크, MCP 키 알림은 개인 설정 대화 상자로) · 화면 테스트 · ego-browser(데스크톱 — 알림을 눌러 프로젝트로 감) | 390px · 다크 · 영어 | 1 / 2 | — |

## 3. 끝난 요구사항

| 요구사항 | 우선 | 끝난 날 | 메모 | 작업 |
|---|---|---|---|---|
| [FR-WSP-005](01-workspace.md#fr-wsp-005) 로그인 · 로그아웃 · 세션 | M | 2026-11-22 | moai-wiki 에서 가져온 코드를 moai-works 렐름에서 실제로 확인했다: 세션 쿠키 `__Host-MOAI_WORKS_SESSION` 은 HttpOnly · Secure, 다시 로그인하면 세션 id 가 바뀐다, 수용 기준 다섯 개(아래 괄호에 확인 방법). 만료 때 갱신 흐름은 실제로 기다려 보지 않았고, web-backend 자동 시험은 없다(moai-wiki 와 같다). | [1.3](../wbs/02-stage-1-foundation.md#w-1-3) 진행 |
| [FR-ISS-018](03-issue.md#fr-iss-018) 첨부파일 · 본문 이미지 · 내용/댓글/첨부 탭 | M | 2026-11-23 | api · MCP 티켓 · 화면(내용 · 댓글 · 첨부파일 탭, 끌어 놓기 · 여러 개 올리기, 편집기 붙여 넣기 · 끌어 놓기로 본문 이미지, attachment: 주소 바꾸기) · 화면 테스트 · ego-browser(올리기 · 받기 · 지우기 · 썸네일 · 본문 · 댓글 그림 · 이미지 붙여 넣기 · PDF 끌어 놓기 · 390px 다크 · 영어). 2026-11-23 편집기가 파일을 첨부로 올리지 않고 blob: 그림을 넣던 것 고침(기본 올리기 플러그인이 먼저 가져가던 것). 2026-11-23 지운 객체 정리 작업(일감 #18 — 지운 지 30일(설정)이 지난 첨부의 객체를 버전 보관분 · 미리보기까지 지우고 행에 정리 표시, 워크스페이스 감사 · 로그, AttachmentPurgeTest). 2026-11-23 미리보기 그림(일감 #19 — png · jpeg · gif 는 처음 받을 때 긴 변 320px PNG 를 원본 옆에 만들어 두고 첨부 목록이 쓴다, WebP 는 원본, 원본과 같은 판정 · 머리글, AttachmentPreviewTest · 화면 테스트). 브라우저 · 실제 MinIO 확인은 아직 | [5.1](../wbs/06-stage-5-collaboration.md#w-5-1) 완료 |
| [FR-ISS-019](03-issue.md#fr-iss-019) 댓글 고치기 · 지우기 | S | 2026-11-23 | api: V14(이력 줄에 댓글 판 · 고친 때 · 지움 표시, 앞 글을 쌓는 `issue_comment_revision`, 이력 줄 보호 트리거) · REST 댓글 목록(쪽) · 고치기(쓴 사람만 + 지금 issue.comment, 댓글 rowVersion 409) · 지우기(쓴 사람 또는 project.manage, 지움 표시만) · 이력 한 줄(판 아님) · 감사 `issue.comment.update` · `issue.comment.delete`(WEB · MCP) · MCP `list_comments` · `update_comment` · `delete_comment`(일감 입구 19개) · 자동 테스트(CommentTest 6 · IssueMcpToolsTest 1 더함). 화면: 내 댓글의 ⋮ — 같은 편집기로 그 자리 고치기(저장 · 취소, 409 면 글을 남기고 다시 읽음) · 확인 대화 상자로 지우기, "고침" 표시(밀리초 도구 설명) · "지운 댓글" · 이력 문장 · ko/en · 화면 시험 5건 · ego-browser(web #4, 데스크톱 · 390px · 다크 · 영어) · 실제 MCP(web-backend 중계) | — |
| [FR-ISS-020](03-issue.md#fr-iss-020) 유형별 일감 템플릿 (워크스페이스 기본 · 프로젝트 덮어쓰기 · MCP) | M | 2026-11-23 | 서버: V12 · REST(워크스페이스 기본 · 프로젝트 덮어쓰기 · 되돌리기, 권한 · 감사 · 409) · 빠진 항목 알림 · MCP(일감 입구 `get_issue_template` · 결과의 `templateCheck`, 프로젝트 관리 입구 `list_issue_templates` · `set_issue_template` · `reset_issue_template`) · 자동 테스트 7건. 화면: 워크스페이스 설정의 "일감 템플릿" 칸 · 프로젝트의 일감 템플릿 화면(`…/templates`, 프로젝트 메뉴 한 줄의 "일감 템플릿"은 project.manage 가 있는 사람에게만) — 유형 탭 · 출처 · 고친 사람 · 밀리초 시각 · 항목 · 편집기(올리기 끔) · 저장 · 되돌리기(확인), 새 일감은 처음 유형 템플릿으로 빈 설명을 채우고 유형을 바꾸면 손대지 않은 템플릿을 새 것으로 바꿈(고치기 화면은 유형을 고를 때만), 빠진 필수 항목 경고(막지 않음). 화면 시험 12건 · ego-browser(저장 · 되돌리기 · 채우기 · 경고, 반응형 검사 160건) | — |

## 4. 아직 시작하지 않은 요구사항

### 워크스페이스 · 계정

| 요구사항 | 우선 | 작업 |
|---|---|---|
| [FR-WSP-006](01-workspace.md#fr-wsp-006) 비밀번호 재설정 · 변경 | M | [1.5](../wbs/02-stage-1-foundation.md#w-1-5) 할 일 |
| [FR-WSP-014](01-workspace.md#fr-wsp-014) 소유권 이전 | S | — |
| [FR-WSP-020](01-workspace.md#fr-wsp-020) 워크스페이스 삭제 | S | [1.8](../wbs/02-stage-1-foundation.md#w-1-8) 할 일 |
| [FR-WSP-021](01-workspace.md#fr-wsp-021) 가입 · 사용 한도 (남용 방지) | S | — |
| [FR-WSP-025](01-workspace.md#fr-wsp-025) 관리 기능 접근 통제 (MFA · 접속 위치) | S | [6.3](../wbs/07-stage-6-operations.md#w-6-3) 할 일 |
| [FR-WSP-028](01-workspace.md#fr-wsp-028) 위험 작업 이중 확인 | S | [6.3](../wbs/07-stage-6-operations.md#w-6-3) 할 일 |
| [FR-WSP-029](01-workspace.md#fr-wsp-029) 계정 탈퇴와 개인정보 | S | [6.3](../wbs/07-stage-6-operations.md#w-6-3) 할 일 |

### MCP

| 요구사항 | 우선 | 작업 |
|---|---|---|
| [FR-MCP-003](04-mcp.md#fr-mcp-003) MCP 판정 | M | [4.2](../wbs/05-stage-4-mcp.md#w-4-2) 진행 |
| [FR-MCP-006](04-mcp.md#fr-mcp-006) MCP 접근 기록 (읽기 · 생성 · 수정 · 삭제 전부) | M | [4.7](../wbs/05-stage-4-mcp.md#w-4-7) 할 일 |
| [FR-MCP-007](04-mcp.md#fr-mcp-007) 안전장치 (속도 제한 · 응답 크기 · 멱등) | M | [4.2](../wbs/05-stage-4-mcp.md#w-4-2) 진행 |
| [FR-MCP-010](04-mcp.md#fr-mcp-010) MCP 접근 기록 보기 · 거르기 | M | [4.8](../wbs/05-stage-4-mcp.md#w-4-8) 할 일 |

### 알림

| 요구사항 | 우선 | 작업 |
|---|---|---|
| [FR-NOTI-006](07-notification.md#fr-noti-006) 메일 알림 | C | — |

### 비기능

| 요구사항 | 우선 | 작업 |
|---|---|---|
| [NFR-PERF-001](05-non-functional.md#nfr-perf-001) 화면 · REST 응답 시간 | M | [3.4](../wbs/04-stage-3-issue.md#w-3-4) 완료 · [6.1](../wbs/07-stage-6-operations.md#w-6-1) 할 일 |
| [NFR-PERF-002](05-non-functional.md#nfr-perf-002) MCP 도구 응답 시간 | M | [4.5](../wbs/05-stage-4-mcp.md#w-4-5) 할 일 · [6.1](../wbs/07-stage-6-operations.md#w-6-1) 할 일 |
| [NFR-SCL-001](05-non-functional.md#nfr-scl-001) 규모 | S | [6.1](../wbs/07-stage-6-operations.md#w-6-1) 할 일 |
| [NFR-SEC-001](05-non-functional.md#nfr-sec-001) 경계 누출 0 | M | [2.2](../wbs/03-stage-2-project.md#w-2-2) 완료 · [3.2](../wbs/04-stage-3-issue.md#w-3-2) 완료 |
| [NFR-SEC-002](05-non-functional.md#nfr-sec-002) Agent 의 한계 | M | — |
| [NFR-AVL-001](05-non-functional.md#nfr-avl-001) 가용성 | S | [6.2](../wbs/07-stage-6-operations.md#w-6-2) 할 일 |
| [NFR-OBS-001](05-non-functional.md#nfr-obs-001) 관측 | S | [6.2](../wbs/07-stage-6-operations.md#w-6-2) 할 일 |
| [NFR-MNT-001](05-non-functional.md#nfr-mnt-001) 형제 저장소와 맞추기 | S | [2.1](../wbs/03-stage-2-project.md#w-2-1) 완료 |
| [NFR-DEP-001](05-non-functional.md#nfr-dep-001) 배포와 설정 | S | [6.2](../wbs/07-stage-6-operations.md#w-6-2) 할 일 |

---

**관련 문서:** [요구사항 전체 목록](00-overview.md) · [WBS 한눈에](../wbs/00-overview.md) · [구현한 기능](../wbs/01-implemented.md)

[← 이전: 비기능 요구사항](05-non-functional.md) | [다음: 알림 요구사항 →](07-notification.md)
