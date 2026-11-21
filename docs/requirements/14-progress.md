[홈](../README.md) › [요구사항](00-overview.md) › 요구사항 진행 현황

# 요구사항 진행 현황

요구사항마다 어디까지 됐는지를 한 쪽에 모은다. **이 쪽은 `python3 -I tools/req_progress.py` 가 만든다 — 손으로 고치지 않는다.** 정본은 각 요구사항의 표에 있는 **진행** 줄과 수용 기준의 체크(`- [x]`)다. 고치는 방법은 [요구사항 개요 §6](00-overview.md#6-진행-관리)에 있다.

**바로가기:** [한눈에](#1-한눈에) · [하고 있는 요구사항](#2-하고-있는-요구사항) · [끝난 요구사항](#3-끝난-요구사항) · [아직 시작하지 않은 요구사항](#4-아직-시작하지-않은-요구사항)

---

## 1. 한눈에

요구사항 254개 가운데 **끝난 것 0개 · 하고 있는 것 24개 · 시작하지 않은 것 230개**다. 수용 기준은 918개 가운데 60개를 확인했다.

| 영역 | 전체 | 완료 | 일부 | 할 일 | 확인한 수용 기준 |
|---|---|---|---|---|---|
| 워크스페이스 · 계정 (`FR-WSP`) | 29 | 0 | 22 | 7 | 58 / 144 |
| 프로젝트 (`FR-PRJ`) | 21 | 0 | 0 | 21 | 0 / 84 |
| 일감 (`FR-ISS`) | 25 | 0 | 0 | 25 | 0 / 99 |
| 트래커 · 워크플로 · 필드 (`FR-WFL`) | 19 | 0 | 0 | 19 | 0 / 68 |
| 질의 · 목록 · 보드 (`FR-QRY`) | 21 | 0 | 0 | 21 | 0 / 75 |
| 버전 · 로드맵 · 간트 (`FR-PLN`) | 16 | 0 | 0 | 16 | 0 / 53 |
| 시간 기록 (`FR-TIM`) | 13 | 0 | 0 | 13 | 0 / 43 |
| 위키 · 뉴스 · 문서 · 포럼 (`FR-CNT`) | 16 | 0 | 0 | 16 | 0 / 47 |
| 알림 · 메일 (`FR-NTF`) | 15 | 0 | 0 | 15 | 0 / 46 |
| 활동 · 검색 (`FR-ACT`) | 13 | 0 | 0 | 13 | 0 / 37 |
| 연동 (`FR-INT`) | 19 | 0 | 0 | 19 | 0 / 66 |
| MCP · AI Agent (`FR-MCP`) | 35 | 0 | 0 | 35 | 0 / 134 |
| 비기능 (`NFR`) | 12 | 0 | 2 | 10 | 2 / 22 |
| **합계** | **254** | **0** | **24** | **230** | **60 / 918** |

| 우선순위 | 전체 | 완료 | 일부 | 할 일 |
|---|---|---|---|---|
| M — 꼭 필요 | 125 | 0 | 22 | 103 |
| S — 있으면 좋음 | 100 | 0 | 2 | 98 |
| C — 나중 | 29 | 0 | 0 | 29 |

- **완료**: 세부 요구사항을 모두 만들었고 수용 기준을 모두 확인했다. **일부**: 만든 것이 있고 남은 것이 있다. **할 일**: 아직 손대지 않았다.
- 수용 기준은 자동 테스트나 실제 화면 · 흐름으로 확인한 것만 체크한다. 만들었지만 확인하지 못한 것은 체크하지 않는다.
- "작업"은 그 요구사항을 다루는 [WBS](../wbs/00-overview.md) 작업과 그 상태다.

## 2. 하고 있는 요구사항

| 요구사항 | 우선 | 한 것 | 남은 것 | 수용 기준 | 작업 |
|---|---|---|---|---|---|
| [FR-WSP-001](01-workspace.md#fr-wsp-001) 회원가입 | M | 코드(moai-wiki 에서 가져옴 — api 가 Keycloak 에 사용자를 만들고 사본을 저장, 비밀번호 정책 검사, 같은 이메일 거절, 저장 실패 때 Keycloak 사용자 삭제(보상), IP별 속도 제한, 감사) | 가입 흐름의 자동 시험(정상 · 중간 실패 주입 · 정책 위반 비밀번호 · 속도 제한) · moai-works 렐름에서 가입 화면 → 계정 생성 흐름 확인 | 0 / 6 | [1.3](../wbs/02-stage-1-foundation.md#w-1-3) 할 일 |
| [FR-WSP-002](01-workspace.md#fr-wsp-002) 가입 정책 설정 (시스템 관리자) | M | 코드(moai-wiki 에서 가져옴 — 가입 받기 · 허용 도메인 · 이메일 인증 · 관리자 승인 · 속도 제한 · 팀 워크스페이스 기본 허용 수 설정, 시스템 관리자만 바꾸고 감사에 남김) | 정책 API 의 자동 시험(권한 · 도메인 밖 가입 거부 · 세 스위치 8가지 조합) · moai-works 렐름에서 시스템 관리자 화면 확인 | 0 / 4 | [1.3](../wbs/02-stage-1-foundation.md#w-1-3) 할 일 |
| [FR-WSP-003](01-workspace.md#fr-wsp-003) 이메일 인증 | M | 코드(moai-wiki 에서 가져옴 — 6자리 코드 · 10분 · 5회 · HMAC 만 저장 · 확인하면 Keycloak 인증 표시 · 재발송 속도 제한) | 인증 대기 7일 뒤 계정 삭제(예약 작업이 없다) · 메일 실제 발송(지금은 로그 모드, SMTP 미정) · 자동 시험(만료 · 초과 코드 · 원문 미저장) · moai-works 렐름에서 흐름 확인 | 0 / 4 | [1.3](../wbs/02-stage-1-foundation.md#w-1-3) 할 일 |
| [FR-WSP-004](01-workspace.md#fr-wsp-004) 가입 승인 (선택) | M | 코드(moai-wiki 에서 가져옴 — 승인 대기 목록 · 승인 · 거절 API 와 화면, 승인 전 로그인 불가, 승인하면 개인 워크스페이스 생성, 감사) | 결과를 메일로 알리기 · 승인 대기 30일 뒤 계정 삭제 · 자동 시험 · moai-works 렐름에서 흐름 확인 | 0 / 3 | [1.3](../wbs/02-stage-1-foundation.md#w-1-3) 할 일 |
| [FR-WSP-005](01-workspace.md#fr-wsp-005) 로그인 · 로그아웃 · 세션 | M | 코드(moai-wiki 에서 가져옴 — web-backend 가 패스워드 그랜트로 토큰을 받아 서버 세션(Spring Session, Redis)에 두고 브라우저에는 HttpOnly 세션 쿠키만 준다. 세션 id 교체 · CSRF · 만료 전 갱신 · 로그아웃 때 Keycloak 세션 종료 · 로그인 시도 제한, 무차별 대입 잠금은 렐름 설치 스크립트가 켬) | moai-works 렐름에서 실제 로그인 흐름 확인(브라우저에 토큰 없음 · 잠금 · 로그아웃 · CSRF) · web-backend 자동 시험 | 0 / 5 | [1.3](../wbs/02-stage-1-foundation.md#w-1-3) 할 일 |
| [FR-WSP-007](01-workspace.md#fr-wsp-007) 개인 워크스페이스 자동 생성 | M | 코드(moai-wiki 에서 가져옴, 기본 위키 단계는 뺌 — 계정이 활성화되거나 처음 로그인하면 단계별로 만든다, 멱등, 끊기면 다음 요청이 이어서 끝냄) · 자동 테스트(첫 로그인 뒤 생성 · 남의 개인 워크스페이스 404 · 나가기와 가입 신청 거부 확인) | 단계마다 실패를 주입하는 자동 시험 · 주인이 모든 권한 코드를 갖는지 시험 · 감사 시험 | 3 / 6 | [1.3](../wbs/02-stage-1-foundation.md#w-1-3) 할 일 |
| [FR-WSP-008](01-workspace.md#fr-wsp-008) 팀 워크스페이스 만들기 | S | 코드(moai-wiki 에서 가져옴) · 자동 테스트(허용받지 않은 계정 거부 · 허용 수 한도 · 한도 올리기 · 0 으로 내려도 기존 유지 · 만든 사람 = 소유자 확인) | 주소 고유 시험 · 감사 시험 · moai-works 렐름에서 화면 확인(만들 수 없는 계정에 만들기 입구가 없고 이유가 보이는지) | 4 / 7 | [1.3](../wbs/02-stage-1-foundation.md#w-1-3) 할 일 |
| [FR-WSP-009](01-workspace.md#fr-wsp-009) 워크스페이스 설정 | M | 코드(moai-wiki 에서 가져옴) · 자동 테스트(프라이빗은 찾기 · 신청에서 숨김 · 프라이빗의 가입 방식 정리 · 권한 없는 멤버 403 · 멤버 아님 404 · 소유자 역할은 기본 역할 불가 확인) | 설정 변경 감사 시험 · moai-works 렐름에서 설정 화면 확인 | 4 / 5 | [1.3](../wbs/02-stage-1-foundation.md#w-1-3) 할 일 |
| [FR-WSP-010](01-workspace.md#fr-wsp-010) 가입 신청 · 철회 | M | 코드(moai-wiki 에서 가져옴) · 자동 테스트(대기 신청 하나 · 본인만 철회 · 거절 뒤 재신청 대기 · 허용 도메인 · 자동 가입 · 이미 멤버 거부 확인) | 대기 신청 30일 뒤 만료(예약 작업이 없다) · 감사 시험 · moai-works 렐름에서 찾기 · 신청 화면 확인 | 6 / 8 | [1.3](../wbs/02-stage-1-foundation.md#w-1-3) 할 일 |
| [FR-WSP-011](01-workspace.md#fr-wsp-011) 가입 신청 승인 · 거절 | M | 코드(moai-wiki 에서 가져옴) · 자동 테스트(승인 즉시 접근 · 승인자가 줄 수 있는 역할만 · 기본 역할 · 다시 처리 금지 · 다른 워크스페이스 신청 404 · 권한 없으면 403 · Keycloak 과 사본 일치 확인) | 신청자 · 관리자 알림(알림 영역) · 감사 시험 · moai-works 렐름에서 화면 확인 | 6 / 8 | [1.3](../wbs/02-stage-1-foundation.md#w-1-3) 할 일 |
| [FR-WSP-012](01-workspace.md#fr-wsp-012) 초대 | S | 코드(moai-wiki 에서 가져옴) · 자동 테스트(링크로 수락 · 쓴 링크 재사용 금지 · 초대받은 이메일만 수락 · 초대 권한 · 줄 수 있는 역할만 · 대기 초대 하나 · 취소한 초대 거부 · 받은 초대 거절 확인) | 만료된 초대 시험 · 초대 메일 실제 발송(지금은 로그 모드) · 감사 시험 · moai-works 렐름에서 초대 링크 화면(로그인 · 가입 뒤 돌아오기) 확인 | 5 / 8 | [1.3](../wbs/02-stage-1-foundation.md#w-1-3) 할 일 |
| [FR-WSP-013](01-workspace.md#fr-wsp-013) 멤버 제거(가입 취소) · 정지 · 탈퇴 | M | 코드(moai-wiki 에서 가져옴 — 내보내기 · 나가기) · 자동 테스트(내보내기 · 나가기 뒤 바로 404 · Keycloak 과 사본에서 함께 빠짐 · 소유자 보호 · 마지막 소유자 보호 · 권한 없으면 403 확인) | 멤버 정지 · 복구 · 그 워크스페이스의 개인 MCP 키 폐기(MCP 영역) · 프로젝트 역할에서 빼기(프로젝트 영역) · 감사 시험 | 5 / 8 | [1.3](../wbs/02-stage-1-foundation.md#w-1-3) 할 일 |
| [FR-WSP-015](01-workspace.md#fr-wsp-015) 권한 코드 카탈로그와 기본 역할 | M | 코드(moai-wiki 에서 가져옴, 위키 · 자료실 코드와 curator 역할을 뺌 — 권한 코드 16개, 기본 역할 3개, 기동 때 DB 표 맞추기와 Keycloak 대조, 카탈로그가 바뀌면 기존 워크스페이스 역할 맞추기) · 자동 테스트(설치 파일과 코드 일치 · 소유자 전용 코드 · 기본 역할 권한 · 카탈로그 이행 · 내 권한 = 역할 합집합 · 기본 역할 고치기 금지 확인) | 역할 × API 전체 조합 시험 · moai-works 렐름에서 기동 대조 확인 · 계획한 권한 코드는 그 기능을 만들 때 더한다 | 5 / 7 | [1.1](../wbs/02-stage-1-foundation.md#w-1-1) 완료 |
| [FR-WSP-016](01-workspace.md#fr-wsp-016) 사용자 정의 역할 | M | 코드(moai-wiki 에서 가져옴) · 자동 테스트(만들기 · 권한 상승 거부 · 소유자 전용과 없는 코드 거부 · 자기보다 큰 역할 고치기 거부 · 쓰는 역할과 기본 역할로 지정된 역할 삭제 거부 · 권한 없으면 403 확인) | 역할의 권한을 고친 뒤 다음 요청부터 적용되는지 시험 · 감사 시험 · moai-works 렐름에서 역할 화면 확인 | 5 / 7 | [1.1](../wbs/02-stage-1-foundation.md#w-1-1) 완료 |
| [FR-WSP-017](01-workspace.md#fr-wsp-017) Keycloak 정본과 api 사본의 동기화 · 대사 | M | 코드(moai-wiki 에서 가져옴 — Keycloak 쓰기는 api 만 하고, 쓰기 기록 · 멱등 재시도 · 보상으로 이중 쓰기를 맞춘다) · 자동 테스트(역할 변경 · 멤버 제거 때 Keycloak 그룹과 사본이 같이 바뀜 · 카탈로그 이행이 Keycloak 먼저 확인) | 15분마다 대사 · 실패한 쓰기 다시 시도 작업 · 중간 실패 주입 시험 · Keycloak 장애 때 조회가 계속되는지 확인 | 1 / 4 | [1.6](../wbs/02-stage-1-foundation.md#w-1-6) 할 일 |
| [FR-WSP-018](01-workspace.md#fr-wsp-018) 워크스페이스 감사 · 사용량 조회 | M | 코드(moai-wiki 에서 가져옴 — 감사 기록에 워크스페이스를 남기고 그 워크스페이스 기록만 보인다) · 자동 테스트(`audit.read` 만 · 멤버 아님 404 · 시스템 관리자도 멤버 아니면 404 확인) | 다른 워크스페이스 기록이 섞이지 않는지 시험 · 주체 종류 · 경로로 거르기 · 사용량 조회 | 1 / 4 | [1.4](../wbs/02-stage-1-foundation.md#w-1-4) 할 일 |
| [FR-WSP-019](01-workspace.md#fr-wsp-019) 시스템 관리자 기능 | M | 코드(moai-wiki 에서 가져옴 — 가입 정책 · 가입 승인 · 계정 정지 · 복구 · 팀 워크스페이스 허용 수, 시스템 관리자만) · 자동 테스트(플랫폼 역할 없으면 403 · 멤버 아닌 워크스페이스 404 · 계정 정지 즉시 반영과 복구 · 자기 정지 금지 · 허용 수 범위 확인) | 워크스페이스 정지 · 복구 · 소유자 재지정 · 계정 삭제 · 플랫폼 감사 화면 · 직무 분리 경고 · 조치 감사 시험 · moai-works 렐름에서 화면 확인 | 5 / 8 | [1.3](../wbs/02-stage-1-foundation.md#w-1-3) 할 일 |
| [FR-WSP-022](01-workspace.md#fr-wsp-022) 인증 — 모든 입구는 인증을 거친다 | M | 코드(moai-wiki 에서 가져옴 — REST 는 Keycloak JWT 를 JWKS 로 api 가 직접 검증, 공개 경로는 가입뿐, 서비스 계정 토큰은 사람용 API 를 못 씀) · 자동 테스트(토큰 없음 · 만료 · 다른 audience · 다른 발급자 · 다른 키 서명 401, 서비스 계정 403, 가입 정책은 토큰 없이 확인) | moai-works 렐름이 발급한 실제 토큰으로 확인 | 4 / 5 | [1.3](../wbs/02-stage-1-foundation.md#w-1-3) 할 일 |
| [FR-WSP-023](01-workspace.md#fr-wsp-023) 권한 판정은 api 한 곳에서 | M | 코드(moai-wiki 에서 가져옴 — REST 핸들러마다 접근 어노테이션 정확히 하나, 없으면 기동 실패, 워크스페이스 판정은 한 서비스에서 DB 사본으로) · 자동 테스트(모든 워크스페이스 경로의 404 · 403, 없는 것과 남의 것 구별 불가, 역할 변경이 다음 요청부터, 모든 테스트가 기동 검사를 지남) | 어노테이션이 빠졌을 때 기동이 멈추는지 부정 시험 · 프로젝트 · 일감 경로 판정(프로젝트 영역) · MCP 도구가 같은 판정을 쓰는지(MCP 영역) | 4 / 6 | [1.1](../wbs/02-stage-1-foundation.md#w-1-1) 완료 |
| [FR-WSP-024](01-workspace.md#fr-wsp-024) 감사 기록 | M | 코드(moai-wiki 에서 가져옴 — `audit_log` 추가 전용(DB 트리거가 고치기 · 지우기를 막음), 업무와 같은 트랜잭션에 저장, 계정 · 가입 정책 · 워크스페이스 · 멤버 · 가입 신청 · 초대 · 역할 행동 기록, 비밀값은 넣지 않음) | 주체 종류 Agent 와 경로(웹 · REST · MCP, 엔드포인트 · 키 id) 기록 · MCP 키 발급 · 폐기 기록 · trace_id 채우기 · 보존 · 변조 감지 · 자동 시험 | 0 / 5 | [1.4](../wbs/02-stage-1-foundation.md#w-1-4) 할 일 · [2.6](../wbs/03-stage-2-project-issue.md#w-2-6) 할 일 |
| [FR-WSP-026](01-workspace.md#fr-wsp-026) 전송 · 저장 암호화 | M | 코드(moai-wiki 에서 가져옴 — api · web-backend 는 기본으로 HTTPS 로만 연다, localhost 가 아닌 호스트에만 HSTS, 세션 쿠키 `__Host-` · Secure · HttpOnly) | moai-works 서비스를 띄워 평문 HTTP 거부 확인 · HSTS 규칙 시험 · 운영 저장소 암호화 점검(PostgreSQL · MinIO · OpenSearch · 백업) | 0 / 3 | [6.8](../wbs/07-stage-6-integration.md#w-6-8) 할 일 |
| [FR-WSP-027](01-workspace.md#fr-wsp-027) 비밀값 · 자격 증명 관리 | M | 코드(moai-wiki 에서 가져옴 — 비밀값은 git 에서 뺀 `.env` 와 환경 변수로만 넣고 설정 파일에는 키 이름만, DB 에는 해시만(MCP 키 · 초대 토큰 SHA-256, 인증 코드 HMAC), 감사에 비밀값을 넣지 않음, 코드 HMAC 비밀값이 없거나 짧으면 기동하지 않음) | 로그 · DB 전수 검사 · CI 비밀 검사 · 교체 주기 알림 · 운영 비밀 저장소 | 0 / 3 | [6.8](../wbs/07-stage-6-integration.md#w-6-8) 할 일 |
| [NFR-QLT-001](13-non-functional.md#nfr-qlt-001) 시험 | M | 토대의 api 자동 테스트 66건 · 화면 60건이 돈다 | 프로젝트 · 일감 · MCP 를 만들 때 경계 · 동등성 시험을 같은 작업에서 더하기 | 1 / 2 | [1.2](../wbs/02-stage-1-foundation.md#w-1-2) 완료 |
| [NFR-I18N-001](13-non-functional.md#nfr-i18n-001) 언어 | M | 토대 화면이 한국어 · 영어로 나온다(i18next 리소스) | 프로젝트 관리 화면 · 메일 · MCP 도구 설명문의 영어 | 1 / 2 | — |

## 3. 끝난 요구사항

| 요구사항 | 우선 | 끝난 날 | 메모 | 작업 |
|---|---|---|---|---|
| — | | | | |

## 4. 아직 시작하지 않은 요구사항

### 워크스페이스 · 계정

| 요구사항 | 우선 | 작업 |
|---|---|---|
| [FR-WSP-006](01-workspace.md#fr-wsp-006) 비밀번호 재설정 · 변경 | M | [1.5](../wbs/02-stage-1-foundation.md#w-1-5) 할 일 |
| [FR-WSP-014](01-workspace.md#fr-wsp-014) 소유권 이전 | S | — |
| [FR-WSP-020](01-workspace.md#fr-wsp-020) 워크스페이스 삭제 | S | — |
| [FR-WSP-021](01-workspace.md#fr-wsp-021) 가입 · 사용 한도 (남용 방지) | S | — |
| [FR-WSP-025](01-workspace.md#fr-wsp-025) 관리 기능 접근 통제 (MFA · 접속 위치) | S | [6.8](../wbs/07-stage-6-integration.md#w-6-8) 할 일 |
| [FR-WSP-028](01-workspace.md#fr-wsp-028) 위험 작업 이중 확인 | S | [6.8](../wbs/07-stage-6-integration.md#w-6-8) 할 일 |
| [FR-WSP-029](01-workspace.md#fr-wsp-029) 계정 탈퇴와 개인정보 | S | [6.8](../wbs/07-stage-6-integration.md#w-6-8) 할 일 |

### 프로젝트

| 요구사항 | 우선 | 작업 |
|---|---|---|
| [FR-PRJ-001](02-project.md#fr-prj-001) 프로젝트 만들기 | M | [2.1](../wbs/03-stage-2-project-issue.md#w-2-1) 할 일 · [2.7](../wbs/03-stage-2-project-issue.md#w-2-7) 할 일 |
| [FR-PRJ-002](02-project.md#fr-prj-002) 프로젝트 식별자 | M | [2.1](../wbs/03-stage-2-project-issue.md#w-2-1) 할 일 |
| [FR-PRJ-003](02-project.md#fr-prj-003) 프로젝트 정보 고치기 | M | [2.1](../wbs/03-stage-2-project-issue.md#w-2-1) 할 일 · [2.7](../wbs/03-stage-2-project-issue.md#w-2-7) 할 일 |
| [FR-PRJ-004](02-project.md#fr-prj-004) 공개 범위 | M | [2.1](../wbs/03-stage-2-project-issue.md#w-2-1) 할 일 · [2.7](../wbs/03-stage-2-project-issue.md#w-2-7) 할 일 |
| [FR-PRJ-005](02-project.md#fr-prj-005) 워크스페이스 공개 프로젝트의 기본 역할 | M | [2.1](../wbs/03-stage-2-project-issue.md#w-2-1) 할 일 |
| [FR-PRJ-006](02-project.md#fr-prj-006) 프로젝트 역할 4종 | M | [2.1](../wbs/03-stage-2-project-issue.md#w-2-1) 할 일 |
| [FR-PRJ-007](02-project.md#fr-prj-007) 멤버 관리 (사람) | M | [2.1](../wbs/03-stage-2-project-issue.md#w-2-1) 할 일 · [2.7](../wbs/03-stage-2-project-issue.md#w-2-7) 할 일 |
| [FR-PRJ-008](02-project.md#fr-prj-008) Agent 계정 멤버 | M | [3.1](../wbs/04-stage-3-mcp.md#w-3-1) 할 일 |
| [FR-PRJ-009](02-project.md#fr-prj-009) 모듈 켜고 끄기 | M | [2.1](../wbs/03-stage-2-project-issue.md#w-2-1) 할 일 · [2.7](../wbs/03-stage-2-project-issue.md#w-2-7) 할 일 |
| [FR-PRJ-010](02-project.md#fr-prj-010) 일감 추적 설정 (트래커 · 기본 담당 · 기본 버전) | M | [2.2](../wbs/03-stage-2-project-issue.md#w-2-2) 할 일 |
| [FR-PRJ-011](02-project.md#fr-prj-011) 사용자 정의 필드 고르기 | S | [4.6](../wbs/05-stage-4-planning-time.md#w-4-6) 할 일 |
| [FR-PRJ-012](02-project.md#fr-prj-012) 프로젝트 목록 · 트리 · 찾기 | M | [2.1](../wbs/03-stage-2-project-issue.md#w-2-1) 할 일 · [2.7](../wbs/03-stage-2-project-issue.md#w-2-7) 할 일 |
| [FR-PRJ-013](02-project.md#fr-prj-013) 프로젝트 개요 쪽 | M | [2.1](../wbs/03-stage-2-project-issue.md#w-2-1) 할 일 · [2.7](../wbs/03-stage-2-project-issue.md#w-2-7) 할 일 |
| [FR-PRJ-014](02-project.md#fr-prj-014) 하위 프로젝트 | S | [4.11](../wbs/05-stage-4-planning-time.md#w-4-11) 할 일 |
| [FR-PRJ-015](02-project.md#fr-prj-015) 상위 프로젝트 바꾸기 | S | [4.11](../wbs/05-stage-4-planning-time.md#w-4-11) 할 일 |
| [FR-PRJ-016](02-project.md#fr-prj-016) 멤버 이어받기 | S | [4.11](../wbs/05-stage-4-planning-time.md#w-4-11) 할 일 |
| [FR-PRJ-017](02-project.md#fr-prj-017) 프로젝트 닫기 · 다시 열기 | S | [4.11](../wbs/05-stage-4-planning-time.md#w-4-11) 할 일 |
| [FR-PRJ-018](02-project.md#fr-prj-018) 프로젝트 보관 · 보관 풀기 | S | [4.11](../wbs/05-stage-4-planning-time.md#w-4-11) 할 일 |
| [FR-PRJ-019](02-project.md#fr-prj-019) 프로젝트 지우기 | S | [4.11](../wbs/05-stage-4-planning-time.md#w-4-11) 할 일 |
| [FR-PRJ-020](02-project.md#fr-prj-020) 새 프로젝트 기본값 | S | [4.11](../wbs/05-stage-4-planning-time.md#w-4-11) 할 일 |
| [FR-PRJ-021](02-project.md#fr-prj-021) 프로젝트 복사 · 틀 | C | — |

### 일감

| 요구사항 | 우선 | 작업 |
|---|---|---|
| [FR-ISS-001](03-issue.md#fr-iss-001) 일감 번호 | M | [2.3](../wbs/03-stage-2-project-issue.md#w-2-3) 할 일 |
| [FR-ISS-002](03-issue.md#fr-iss-002) 일감 만들기 | M | [2.3](../wbs/03-stage-2-project-issue.md#w-2-3) 할 일 · [2.8](../wbs/03-stage-2-project-issue.md#w-2-8) 할 일 |
| [FR-ISS-003](03-issue.md#fr-iss-003) 일감 필드와 검증 | M | [2.3](../wbs/03-stage-2-project-issue.md#w-2-3) 할 일 · [2.8](../wbs/03-stage-2-project-issue.md#w-2-8) 할 일 |
| [FR-ISS-004](03-issue.md#fr-iss-004) 일감 보기 | M | [2.3](../wbs/03-stage-2-project-issue.md#w-2-3) 할 일 · [2.8](../wbs/03-stage-2-project-issue.md#w-2-8) 할 일 |
| [FR-ISS-005](03-issue.md#fr-iss-005) 일감 고치기와 낙관적 잠금 | M | [2.3](../wbs/03-stage-2-project-issue.md#w-2-3) 할 일 · [2.8](../wbs/03-stage-2-project-issue.md#w-2-8) 할 일 |
| [FR-ISS-006](03-issue.md#fr-iss-006) 일감 지우기 | M | [2.3](../wbs/03-stage-2-project-issue.md#w-2-3) 할 일 · [2.8](../wbs/03-stage-2-project-issue.md#w-2-8) 할 일 |
| [FR-ISS-007](03-issue.md#fr-iss-007) 변경 이력 (저널) | M | [2.3](../wbs/03-stage-2-project-issue.md#w-2-3) 할 일 · [2.8](../wbs/03-stage-2-project-issue.md#w-2-8) 할 일 |
| [FR-ISS-008](03-issue.md#fr-iss-008) 메모 | M | [2.3](../wbs/03-stage-2-project-issue.md#w-2-3) 할 일 · [2.8](../wbs/03-stage-2-project-issue.md#w-2-8) 할 일 |
| [FR-ISS-009](03-issue.md#fr-iss-009) 행위자와 경로 표시 | M | [2.3](../wbs/03-stage-2-project-issue.md#w-2-3) 할 일 · [2.6](../wbs/03-stage-2-project-issue.md#w-2-6) 할 일 |
| [FR-ISS-010](03-issue.md#fr-iss-010) 첨부 올리기 | M | [2.4](../wbs/03-stage-2-project-issue.md#w-2-4) 할 일 · [2.8](../wbs/03-stage-2-project-issue.md#w-2-8) 할 일 |
| [FR-ISS-011](03-issue.md#fr-iss-011) 첨부 내려받기와 권한 | M | [2.4](../wbs/03-stage-2-project-issue.md#w-2-4) 할 일 |
| [FR-ISS-012](03-issue.md#fr-iss-012) 텍스트 링크 | M | [2.3](../wbs/03-stage-2-project-issue.md#w-2-3) 할 일 |
| [FR-ISS-013](03-issue.md#fr-iss-013) 비공개 메모 | S | [4.4](../wbs/05-stage-4-planning-time.md#w-4-4) 할 일 |
| [FR-ISS-014](03-issue.md#fr-iss-014) 메모 고치기와 고친 이력 | S | [4.4](../wbs/05-stage-4-planning-time.md#w-4-4) 할 일 |
| [FR-ISS-015](03-issue.md#fr-iss-015) 하위 일감 | S | [4.3](../wbs/05-stage-4-planning-time.md#w-4-3) 할 일 |
| [FR-ISS-016](03-issue.md#fr-iss-016) 상위 일감 값 계산 (롤업) | S | [4.3](../wbs/05-stage-4-planning-time.md#w-4-3) 할 일 |
| [FR-ISS-017](03-issue.md#fr-iss-017) 일감 관계 | S | [4.3](../wbs/05-stage-4-planning-time.md#w-4-3) 할 일 |
| [FR-ISS-018](03-issue.md#fr-iss-018) 선행 · 후행 일정 자동 이동 | S | [4.3](../wbs/05-stage-4-planning-time.md#w-4-3) 할 일 |
| [FR-ISS-019](03-issue.md#fr-iss-019) 관찰자 | S | [4.4](../wbs/05-stage-4-planning-time.md#w-4-4) 할 일 |
| [FR-ISS-020](03-issue.md#fr-iss-020) 멘션 | S | [4.4](../wbs/05-stage-4-planning-time.md#w-4-4) 할 일 |
| [FR-ISS-021](03-issue.md#fr-iss-021) 비공개 일감과 보기 범위 | S | [4.4](../wbs/05-stage-4-planning-time.md#w-4-4) 할 일 |
| [FR-ISS-022](03-issue.md#fr-iss-022) 일괄 편집 | S | [4.5](../wbs/05-stage-4-planning-time.md#w-4-5) 할 일 |
| [FR-ISS-023](03-issue.md#fr-iss-023) 일감 복사 | S | [4.5](../wbs/05-stage-4-planning-time.md#w-4-5) 할 일 |
| [FR-ISS-024](03-issue.md#fr-iss-024) 일감 옮기기 | S | [4.5](../wbs/05-stage-4-planning-time.md#w-4-5) 할 일 |
| [FR-ISS-025](03-issue.md#fr-iss-025) "내가 만들거나 맡은 것만" 보기 범위 | C | — |

### 트래커 · 워크플로 · 필드

| 요구사항 | 우선 | 작업 |
|---|---|---|
| [FR-WFL-001](04-workflow.md#fr-wfl-001) 트래커 | M | [2.2](../wbs/03-stage-2-project-issue.md#w-2-2) 할 일 · [2.9](../wbs/03-stage-2-project-issue.md#w-2-9) 할 일 |
| [FR-WFL-002](04-workflow.md#fr-wfl-002) 상태 | M | [2.2](../wbs/03-stage-2-project-issue.md#w-2-2) 할 일 · [2.9](../wbs/03-stage-2-project-issue.md#w-2-9) 할 일 |
| [FR-WFL-003](04-workflow.md#fr-wfl-003) 열거형 (우선순위 · 작업 분류 · 문서 분류) | M | [2.2](../wbs/03-stage-2-project-issue.md#w-2-2) 할 일 · [2.9](../wbs/03-stage-2-project-issue.md#w-2-9) 할 일 |
| [FR-WFL-004](04-workflow.md#fr-wfl-004) 워크플로 상태 전이 표 | M | [2.2](../wbs/03-stage-2-project-issue.md#w-2-2) 할 일 · [2.9](../wbs/03-stage-2-project-issue.md#w-2-9) 할 일 |
| [FR-WFL-005](04-workflow.md#fr-wfl-005) 워크플로 판정 | M | [2.2](../wbs/03-stage-2-project-issue.md#w-2-2) 할 일 · [2.9](../wbs/03-stage-2-project-issue.md#w-2-9) 할 일 |
| [FR-WFL-006](04-workflow.md#fr-wfl-006) 워크스페이스 시드 | M | [2.2](../wbs/03-stage-2-project-issue.md#w-2-2) 할 일 |
| [FR-WFL-007](04-workflow.md#fr-wfl-007) 작성자 · 담당자 추가 전이 | S | [4.6](../wbs/05-stage-4-planning-time.md#w-4-6) 할 일 |
| [FR-WFL-008](04-workflow.md#fr-wfl-008) 워크플로 복사 | S | [4.6](../wbs/05-stage-4-planning-time.md#w-4-6) 할 일 |
| [FR-WFL-009](04-workflow.md#fr-wfl-009) 필드 권한 (읽기전용 · 필수) | S | [4.6](../wbs/05-stage-4-planning-time.md#w-4-6) 할 일 |
| [FR-WFL-010](04-workflow.md#fr-wfl-010) 진척률 계산 방식 | S | [4.6](../wbs/05-stage-4-planning-time.md#w-4-6) 할 일 |
| [FR-WFL-011](04-workflow.md#fr-wfl-011) 일감 추적 설정 | S | [4.6](../wbs/05-stage-4-planning-time.md#w-4-6) 할 일 |
| [FR-WFL-012](04-workflow.md#fr-wfl-012) 카테고리 | S | [4.6](../wbs/05-stage-4-planning-time.md#w-4-6) 할 일 |
| [FR-WFL-013](04-workflow.md#fr-wfl-013) 사용자 정의 필드 정의 | S | [4.6](../wbs/05-stage-4-planning-time.md#w-4-6) 할 일 |
| [FR-WFL-014](04-workflow.md#fr-wfl-014) 사용자 정의 필드 형식과 검증 | S | [4.6](../wbs/05-stage-4-planning-time.md#w-4-6) 할 일 |
| [FR-WFL-015](04-workflow.md#fr-wfl-015) 사용자 정의 필드 연결 | S | [4.6](../wbs/05-stage-4-planning-time.md#w-4-6) 할 일 |
| [FR-WFL-016](04-workflow.md#fr-wfl-016) 사용자 정의 필드의 보이는 역할 | S | [4.6](../wbs/05-stage-4-planning-time.md#w-4-6) 할 일 |
| [FR-WFL-017](04-workflow.md#fr-wfl-017) 사용자 정의 필드 찾기 · 필터 | S | [4.6](../wbs/05-stage-4-planning-time.md#w-4-6) 할 일 |
| [FR-WFL-018](04-workflow.md#fr-wfl-018) 트래커별 설명 틀 | C | — |
| [FR-WFL-019](04-workflow.md#fr-wfl-019) 설정 묶음 내보내기 · 가져오기 | C | — |

### 질의 · 목록 · 보드

| 요구사항 | 우선 | 작업 |
|---|---|---|
| [FR-QRY-001](05-query.md#fr-qry-001) 필터 대상 필드 | M | [2.5](../wbs/03-stage-2-project-issue.md#w-2-5) 할 일 · [2.8](../wbs/03-stage-2-project-issue.md#w-2-8) 할 일 |
| [FR-QRY-002](05-query.md#fr-qry-002) 필터 연산자와 값 | M | [2.5](../wbs/03-stage-2-project-issue.md#w-2-5) 할 일 · [2.8](../wbs/03-stage-2-project-issue.md#w-2-8) 할 일 |
| [FR-QRY-003](05-query.md#fr-qry-003) 권한 · 비공개 조건 | M | [2.5](../wbs/03-stage-2-project-issue.md#w-2-5) 할 일 · [2.8](../wbs/03-stage-2-project-issue.md#w-2-8) 할 일 |
| [FR-QRY-004](05-query.md#fr-qry-004) 목록 열 | M | [2.5](../wbs/03-stage-2-project-issue.md#w-2-5) 할 일 · [2.8](../wbs/03-stage-2-project-issue.md#w-2-8) 할 일 |
| [FR-QRY-005](05-query.md#fr-qry-005) 정렬 | M | [2.5](../wbs/03-stage-2-project-issue.md#w-2-5) 할 일 · [2.8](../wbs/03-stage-2-project-issue.md#w-2-8) 할 일 |
| [FR-QRY-006](05-query.md#fr-qry-006) 쪽 나누기 | M | [2.5](../wbs/03-stage-2-project-issue.md#w-2-5) 할 일 · [2.8](../wbs/03-stage-2-project-issue.md#w-2-8) 할 일 |
| [FR-QRY-007](05-query.md#fr-qry-007) 여러 프로젝트 · 하위 프로젝트 목록 | M | [2.5](../wbs/03-stage-2-project-issue.md#w-2-5) 할 일 |
| [FR-QRY-008](05-query.md#fr-qry-008) 화면 · REST · MCP 같은 엔진 | M | [2.5](../wbs/03-stage-2-project-issue.md#w-2-5) 할 일 |
| [FR-QRY-009](05-query.md#fr-qry-009) 사용자 정의 필드 필터 · 열 | S | [4.6](../wbs/05-stage-4-planning-time.md#w-4-6) 할 일 |
| [FR-QRY-010](05-query.md#fr-qry-010) 묶기 | S | [4.7](../wbs/05-stage-4-planning-time.md#w-4-7) 할 일 |
| [FR-QRY-011](05-query.md#fr-qry-011) 합계 | S | [4.7](../wbs/05-stage-4-planning-time.md#w-4-7) 할 일 |
| [FR-QRY-012](05-query.md#fr-qry-012) 저장한 질의 (개인) | S | [4.7](../wbs/05-stage-4-planning-time.md#w-4-7) 할 일 |
| [FR-QRY-013](05-query.md#fr-qry-013) 공개 · 역할별 질의 | S | [4.7](../wbs/05-stage-4-planning-time.md#w-4-7) 할 일 |
| [FR-QRY-014](05-query.md#fr-qry-014) CSV 내보내기 | S | [4.7](../wbs/05-stage-4-planning-time.md#w-4-7) 할 일 |
| [FR-QRY-015](05-query.md#fr-qry-015) 칸반 보드 | S | [4.8](../wbs/05-stage-4-planning-time.md#w-4-8) 할 일 |
| [FR-QRY-016](05-query.md#fr-qry-016) 보드 끌어 놓기와 워크플로 | S | [4.8](../wbs/05-stage-4-planning-time.md#w-4-8) 할 일 |
| [FR-QRY-017](05-query.md#fr-qry-017) 보드 묶음 줄 | S | [4.8](../wbs/05-stage-4-planning-time.md#w-4-8) 할 일 |
| [FR-QRY-018](05-query.md#fr-qry-018) 기본 질의 | C | — |
| [FR-QRY-019](05-query.md#fr-qry-019) PDF 내보내기 | C | — |
| [FR-QRY-020](05-query.md#fr-qry-020) Atom 피드 | C | — |
| [FR-QRY-021](05-query.md#fr-qry-021) 보드 WIP 한도 · 손으로 정한 순서 | C | — |

### 버전 · 로드맵 · 간트

| 요구사항 | 우선 | 작업 |
|---|---|---|
| [FR-PLN-001](06-planning.md#fr-pln-001) 버전 만들기 · 고치기 | M | [4.1](../wbs/05-stage-4-planning-time.md#w-4-1) 할 일 |
| [FR-PLN-002](06-planning.md#fr-pln-002) 버전 상태 (열림 · 잠김 · 닫힘) | M | [4.1](../wbs/05-stage-4-planning-time.md#w-4-1) 할 일 |
| [FR-PLN-003](06-planning.md#fr-pln-003) 버전 공유 범위 | S | [4.1](../wbs/05-stage-4-planning-time.md#w-4-1) 할 일 |
| [FR-PLN-004](06-planning.md#fr-pln-004) 대상 버전 고르기 규칙 | M | [4.1](../wbs/05-stage-4-planning-time.md#w-4-1) 할 일 |
| [FR-PLN-005](06-planning.md#fr-pln-005) 기본 버전 | S | [4.1](../wbs/05-stage-4-planning-time.md#w-4-1) 할 일 |
| [FR-PLN-006](06-planning.md#fr-pln-006) 버전 지우기 | M | [4.1](../wbs/05-stage-4-planning-time.md#w-4-1) 할 일 |
| [FR-PLN-007](06-planning.md#fr-pln-007) 로드맵 화면 | M | [4.1](../wbs/05-stage-4-planning-time.md#w-4-1) 할 일 |
| [FR-PLN-008](06-planning.md#fr-pln-008) 로드맵 진척 계산 | M | [4.1](../wbs/05-stage-4-planning-time.md#w-4-1) 할 일 |
| [FR-PLN-009](06-planning.md#fr-pln-009) 버전 쪽 | M | [4.1](../wbs/05-stage-4-planning-time.md#w-4-1) 할 일 |
| [FR-PLN-010](06-planning.md#fr-pln-010) 간트 차트 | S | [4.9](../wbs/05-stage-4-planning-time.md#w-4-9) 할 일 |
| [FR-PLN-011](06-planning.md#fr-pln-011) 간트 관계 화살표 · 진척선 | S | [4.9](../wbs/05-stage-4-planning-time.md#w-4-9) 할 일 |
| [FR-PLN-012](06-planning.md#fr-pln-012) 간트 내보내기 (PDF · PNG) | C | — |
| [FR-PLN-013](06-planning.md#fr-pln-013) 캘린더 (월 · 주) | S | [4.9](../wbs/05-stage-4-planning-time.md#w-4-9) 할 일 |
| [FR-PLN-014](06-planning.md#fr-pln-014) MCP 로 버전 · 로드맵 보기 | S | [4.10](../wbs/05-stage-4-planning-time.md#w-4-10) 할 일 |
| [FR-PLN-015](06-planning.md#fr-pln-015) 간트에서 날짜 끌어 바꾸기 | C | — |
| [FR-PLN-016](06-planning.md#fr-pln-016) 워크스페이스 전체 간트 · 캘린더 | C | — |

### 시간 기록

| 요구사항 | 우선 | 작업 |
|---|---|---|
| [FR-TIM-001](07-time.md#fr-tim-001) 시간 기록하기 | M | [4.2](../wbs/05-stage-4-planning-time.md#w-4-2) 할 일 |
| [FR-TIM-002](07-time.md#fr-tim-002) 시간 입력 형식 | M | [4.2](../wbs/05-stage-4-planning-time.md#w-4-2) 할 일 |
| [FR-TIM-003](07-time.md#fr-tim-003) 일감 고치면서 시간 기록 | S | [4.2](../wbs/05-stage-4-planning-time.md#w-4-2) 할 일 |
| [FR-TIM-004](07-time.md#fr-tim-004) 남 대신 기록 | S | [4.2](../wbs/05-stage-4-planning-time.md#w-4-2) 할 일 |
| [FR-TIM-005](07-time.md#fr-tim-005) 기록 고치기 · 지우기 | M | [4.2](../wbs/05-stage-4-planning-time.md#w-4-2) 할 일 |
| [FR-TIM-006](07-time.md#fr-tim-006) 프로젝트별 작업 분류 | S | [4.2](../wbs/05-stage-4-planning-time.md#w-4-2) 할 일 |
| [FR-TIM-007](07-time.md#fr-tim-007) 기록 규칙 설정 | S | [4.2](../wbs/05-stage-4-planning-time.md#w-4-2) 할 일 |
| [FR-TIM-008](07-time.md#fr-tim-008) 시간 기록 목록 · CSV | M | [4.2](../wbs/05-stage-4-planning-time.md#w-4-2) 할 일 |
| [FR-TIM-009](07-time.md#fr-tim-009) 예상 대 실적 | M | [4.2](../wbs/05-stage-4-planning-time.md#w-4-2) 할 일 |
| [FR-TIM-010](07-time.md#fr-tim-010) 소요 시간 보고서 | S | [4.2](../wbs/05-stage-4-planning-time.md#w-4-2) 할 일 |
| [FR-TIM-011](07-time.md#fr-tim-011) 일감 옮기기 · 지우기와 시간 기록 | M | [4.2](../wbs/05-stage-4-planning-time.md#w-4-2) 할 일 |
| [FR-TIM-012](07-time.md#fr-tim-012) MCP 로 시간 기록 · 보기 | S | [4.10](../wbs/05-stage-4-planning-time.md#w-4-10) 할 일 |
| [FR-TIM-013](07-time.md#fr-tim-013) 시간 기록 마감 | C | — |

### 위키 · 뉴스 · 문서 · 포럼

| 요구사항 | 우선 | 작업 |
|---|---|---|
| [FR-CNT-001](08-content.md#fr-cnt-001) 프로젝트 위키 · 시작 쪽 · 링크 | M | [5.1](../wbs/06-stage-5-collaboration.md#w-5-1) 할 일 |
| [FR-CNT-002](08-content.md#fr-cnt-002) 위키 쪽 만들기 · 고치기 | M | [5.1](../wbs/06-stage-5-collaboration.md#w-5-1) 할 일 |
| [FR-CNT-003](08-content.md#fr-cnt-003) 쪽 계층 · 색인 | M | [5.1](../wbs/06-stage-5-collaboration.md#w-5-1) 할 일 |
| [FR-CNT-004](08-content.md#fr-cnt-004) 판 · 차이 · 되돌리기 | M | [5.1](../wbs/06-stage-5-collaboration.md#w-5-1) 할 일 |
| [FR-CNT-005](08-content.md#fr-cnt-005) 이름 바꾸기 · 옛 이름 넘기기 · 옮기기 | S | [5.1](../wbs/06-stage-5-collaboration.md#w-5-1) 할 일 |
| [FR-CNT-006](08-content.md#fr-cnt-006) 쪽 보호 · 지우기 | S | [5.1](../wbs/06-stage-5-collaboration.md#w-5-1) 할 일 |
| [FR-CNT-007](08-content.md#fr-cnt-007) 위키 관찰 | S | [5.1](../wbs/06-stage-5-collaboration.md#w-5-1) 할 일 |
| [FR-CNT-008](08-content.md#fr-cnt-008) 위키 내보내기 | C | — |
| [FR-CNT-009](08-content.md#fr-cnt-009) MCP 로 위키 읽고 쓰기 | S | [5.7](../wbs/06-stage-5-collaboration.md#w-5-7) 할 일 |
| [FR-CNT-010](08-content.md#fr-cnt-010) 뉴스 · 댓글 | S | [5.6](../wbs/06-stage-5-collaboration.md#w-5-6) 할 일 |
| [FR-CNT-011](08-content.md#fr-cnt-011) 문서 | C | — |
| [FR-CNT-012](08-content.md#fr-cnt-012) 파일 (내려받기) | S | [5.6](../wbs/06-stage-5-collaboration.md#w-5-6) 할 일 |
| [FR-CNT-013](08-content.md#fr-cnt-013) 포럼 — 게시판 · 글 · 답글 | C | — |
| [FR-CNT-014](08-content.md#fr-cnt-014) 포럼 관리 — 잠금 · 고정 · 관찰 | C | — |
| [FR-CNT-015](08-content.md#fr-cnt-015) 위키 절 고치기 | S | [5.1](../wbs/06-stage-5-collaboration.md#w-5-1) 할 일 |
| [FR-CNT-016](08-content.md#fr-cnt-016) 위키 매크로 · 사이드바 | C | — |

### 알림 · 메일

| 요구사항 | 우선 | 작업 |
|---|---|---|
| [FR-NTF-001](09-notification.md#fr-ntf-001) 알림 종류 · 워크스페이스 메일 설정 | M | [5.2](../wbs/06-stage-5-collaboration.md#w-5-2) 할 일 |
| [FR-NTF-002](09-notification.md#fr-ntf-002) 받는 사람 규칙 | M | [5.2](../wbs/06-stage-5-collaboration.md#w-5-2) 할 일 |
| [FR-NTF-003](09-notification.md#fr-ntf-003) 받는 사람 권한 재판정 | M | [5.2](../wbs/06-stage-5-collaboration.md#w-5-2) 할 일 |
| [FR-NTF-004](09-notification.md#fr-ntf-004) 사용자 알림 설정 | M | [5.2](../wbs/06-stage-5-collaboration.md#w-5-2) 할 일 |
| [FR-NTF-005](09-notification.md#fr-ntf-005) 화면 알림함 | M | [5.2](../wbs/06-stage-5-collaboration.md#w-5-2) 할 일 |
| [FR-NTF-006](09-notification.md#fr-ntf-006) 메일 보내기 — 아웃박스 · `log` 모드 | M | [5.2](../wbs/06-stage-5-collaboration.md#w-5-2) 할 일 |
| [FR-NTF-007](09-notification.md#fr-ntf-007) 메일 내용 · 스레드 헤더 | M | [5.2](../wbs/06-stage-5-collaboration.md#w-5-2) 할 일 |
| [FR-NTF-008](09-notification.md#fr-ntf-008) 멘션 | S | [5.2](../wbs/06-stage-5-collaboration.md#w-5-2) 할 일 |
| [FR-NTF-009](09-notification.md#fr-ntf-009) 묶어 보내기 — 일괄 변경 · Agent 상한 | S | [5.2](../wbs/06-stage-5-collaboration.md#w-5-2) 할 일 |
| [FR-NTF-010](09-notification.md#fr-ntf-010) Agent 계정과 알림 | S | [5.2](../wbs/06-stage-5-collaboration.md#w-5-2) 할 일 |
| [FR-NTF-011](09-notification.md#fr-ntf-011) 메일로 일감 만들기 | C | — |
| [FR-NTF-012](09-notification.md#fr-ntf-012) 답장으로 메모 더하기 | C | — |
| [FR-NTF-013](09-notification.md#fr-ntf-013) 받은 메일 안전장치 | C | — |
| [FR-NTF-014](09-notification.md#fr-ntf-014) 화면 알림 실시간 (SSE) | C | — |
| [FR-NTF-015](09-notification.md#fr-ntf-015) 하루 요약 메일 · 받지 않기 링크 | C | — |

### 활동 · 검색

| 요구사항 | 우선 | 작업 |
|---|---|---|
| [FR-ACT-001](10-activity-search.md#fr-act-001) 프로젝트 활동 | M | [5.3](../wbs/06-stage-5-collaboration.md#w-5-3) 할 일 |
| [FR-ACT-002](10-activity-search.md#fr-act-002) 워크스페이스 전체 활동 | M | [5.3](../wbs/06-stage-5-collaboration.md#w-5-3) 할 일 |
| [FR-ACT-003](10-activity-search.md#fr-act-003) 사람 쪽 · 사람별 활동 | S | [5.3](../wbs/06-stage-5-collaboration.md#w-5-3) 할 일 |
| [FR-ACT-004](10-activity-search.md#fr-act-004) 활동 Atom 피드 | C | — |
| [FR-ACT-005](10-activity-search.md#fr-act-005) 행위자 표시 · 거르기 | M | [5.3](../wbs/06-stage-5-collaboration.md#w-5-3) 할 일 |
| [FR-ACT-006](10-activity-search.md#fr-act-006) 통합 검색 | M | [5.4](../wbs/06-stage-5-collaboration.md#w-5-4) 할 일 |
| [FR-ACT-007](10-activity-search.md#fr-act-007) `#123` 바로 가기 | M | [5.4](../wbs/06-stage-5-collaboration.md#w-5-4) 할 일 |
| [FR-ACT-008](10-activity-search.md#fr-act-008) 검색 권한 필터 | M | [5.4](../wbs/06-stage-5-collaboration.md#w-5-4) 할 일 |
| [FR-ACT-009](10-activity-search.md#fr-act-009) 색인 갱신 · 다시 만들기 | M | [5.4](../wbs/06-stage-5-collaboration.md#w-5-4) 할 일 |
| [FR-ACT-010](10-activity-search.md#fr-act-010) MCP 로 활동 보기 | S | [5.7](../wbs/06-stage-5-collaboration.md#w-5-7) 할 일 |
| [FR-ACT-011](10-activity-search.md#fr-act-011) 내 페이지 기본 블록 | M | [5.5](../wbs/06-stage-5-collaboration.md#w-5-5) 할 일 |
| [FR-ACT-012](10-activity-search.md#fr-act-012) 내 페이지 꾸미기 | S | [5.5](../wbs/06-stage-5-collaboration.md#w-5-5) 할 일 |
| [FR-ACT-013](10-activity-search.md#fr-act-013) 첨부 내용 검색 | C | — |

### 연동

| 요구사항 | 우선 | 작업 |
|---|---|---|
| [FR-INT-001](11-integration.md#fr-int-001) REST API 문서 (OpenAPI) | S | [6.1](../wbs/07-stage-6-integration.md#w-6-1) 할 일 |
| [FR-INT-002](11-integration.md#fr-int-002) REST API 공통 규약 | M | [6.1](../wbs/07-stage-6-integration.md#w-6-1) 할 일 |
| [FR-INT-003](11-integration.md#fr-int-003) 바깥 자동화 인증 — Keycloak 서비스 계정 | S | [6.1](../wbs/07-stage-6-integration.md#w-6-1) 할 일 |
| [FR-INT-004](11-integration.md#fr-int-004) 개인 API 토큰 | C | — |
| [FR-INT-005](11-integration.md#fr-int-005) 웹훅 구독 | S | [6.2](../wbs/07-stage-6-integration.md#w-6-2) 할 일 |
| [FR-INT-006](11-integration.md#fr-int-006) 웹훅 전달과 HMAC 서명 | S | [6.2](../wbs/07-stage-6-integration.md#w-6-2) 할 일 |
| [FR-INT-007](11-integration.md#fr-int-007) 웹훅 재시도 · 실패 기록 · 자동 끄기 | S | [6.2](../wbs/07-stage-6-integration.md#w-6-2) 할 일 |
| [FR-INT-008](11-integration.md#fr-int-008) Git 저장소 연결 | S | [6.3](../wbs/07-stage-6-integration.md#w-6-3) 할 일 |
| [FR-INT-009](11-integration.md#fr-int-009) Git 웹훅 받기 · 서명 검증 · 커밋 저장 | S | [6.3](../wbs/07-stage-6-integration.md#w-6-3) 할 일 |
| [FR-INT-010](11-integration.md#fr-int-010) 커밋을 일감에 잇기 · 사람 대응 | S | [6.3](../wbs/07-stage-6-integration.md#w-6-3) 할 일 |
| [FR-INT-011](11-integration.md#fr-int-011) 고치기 키워드로 상태 · 진척률 바꾸기 | S | [6.3](../wbs/07-stage-6-integration.md#w-6-3) 할 일 |
| [FR-INT-012](11-integration.md#fr-int-012) 커밋 메시지로 시간 기록 | S | [6.3](../wbs/07-stage-6-integration.md#w-6-3) 할 일 |
| [FR-INT-013](11-integration.md#fr-int-013) PR · MR 잇기 | S | [6.3](../wbs/07-stage-6-integration.md#w-6-3) 할 일 |
| [FR-INT-014](11-integration.md#fr-int-014) 저장소 브라우저 | C | — |
| [FR-INT-015](11-integration.md#fr-int-015) CSV 일감 가져오기 | S | [6.4](../wbs/07-stage-6-integration.md#w-6-4) 할 일 |
| [FR-INT-016](11-integration.md#fr-int-016) Redmine 이관 — 연결과 대응 설정 | S | [6.5](../wbs/07-stage-6-integration.md#w-6-5) 할 일 |
| [FR-INT-017](11-integration.md#fr-int-017) Redmine 이관 — 데이터 옮기기 | S | [6.5](../wbs/07-stage-6-integration.md#w-6-5) 할 일 |
| [FR-INT-018](11-integration.md#fr-int-018) Redmine 이관 — 번호 대응표 · 다시 돌려도 안전 · 결과 | S | [6.5](../wbs/07-stage-6-integration.md#w-6-5) 할 일 |
| [FR-INT-019](11-integration.md#fr-int-019) 워크스페이스 데이터 내보내기 | C | — |

### MCP · AI Agent

| 요구사항 | 우선 | 작업 |
|---|---|---|
| [FR-MCP-001](12-mcp.md#fr-mcp-001) 워크스페이스 MCP 엔드포인트 | M | [3.2](../wbs/04-stage-3-mcp.md#w-3-2) 할 일 |
| [FR-MCP-002](12-mcp.md#fr-mcp-002) 개인 MCP 키 | M | [3.3](../wbs/04-stage-3-mcp.md#w-3-3) 할 일 |
| [FR-MCP-003](12-mcp.md#fr-mcp-003) 서비스 MCP 키 | M | [3.3](../wbs/04-stage-3-mcp.md#w-3-3) 할 일 |
| [FR-MCP-004](12-mcp.md#fr-mcp-004) 키 검사 · 만료 · 폐기 | M | [3.3](../wbs/04-stage-3-mcp.md#w-3-3) 할 일 |
| [FR-MCP-005](12-mcp.md#fr-mcp-005) Agent 계정 만들기 · 책임자 · 정지 · 표시 | M | [3.1](../wbs/04-stage-3-mcp.md#w-3-1) 할 일 |
| [FR-MCP-006](12-mcp.md#fr-mcp-006) Agent 계정의 프로젝트 역할과 일감 참여 | M | [3.1](../wbs/04-stage-3-mcp.md#w-3-1) 할 일 |
| [FR-MCP-007](12-mcp.md#fr-mcp-007) 도구 권한 판정 — 주체 권한 ∩ 엔드포인트 범위 | M | [3.4](../wbs/04-stage-3-mcp.md#w-3-4) 할 일 |
| [FR-MCP-008](12-mcp.md#fr-mcp-008) `whoami` — 누구로 일하는지 알기 | M | [3.5](../wbs/04-stage-3-mcp.md#w-3-5) 할 일 |
| [FR-MCP-009](12-mcp.md#fr-mcp-009) `list_projects` · `get_project` — 프로젝트 읽기 | M | [3.5](../wbs/04-stage-3-mcp.md#w-3-5) 할 일 |
| [FR-MCP-010](12-mcp.md#fr-mcp-010) `search_issues` — 일감 찾기 | M | [3.5](../wbs/04-stage-3-mcp.md#w-3-5) 할 일 |
| [FR-MCP-011](12-mcp.md#fr-mcp-011) `get_issue` — 일감 읽기 | M | [3.5](../wbs/04-stage-3-mcp.md#w-3-5) 할 일 |
| [FR-MCP-012](12-mcp.md#fr-mcp-012) `list_issue_metadata` — 일감 입력에 쓸 값 | M | [3.5](../wbs/04-stage-3-mcp.md#w-3-5) 할 일 |
| [FR-MCP-013](12-mcp.md#fr-mcp-013) `list_versions` · `get_roadmap` — 버전과 진척 읽기 | S | [4.10](../wbs/05-stage-4-planning-time.md#w-4-10) 할 일 |
| [FR-MCP-014](12-mcp.md#fr-mcp-014) `get_activity` — 최근 활동 읽기 | S | [5.7](../wbs/06-stage-5-collaboration.md#w-5-7) 할 일 |
| [FR-MCP-015](12-mcp.md#fr-mcp-015) `list_wiki_pages` · `get_wiki_page` — 위키 읽기 | S | [5.7](../wbs/06-stage-5-collaboration.md#w-5-7) 할 일 |
| [FR-MCP-016](12-mcp.md#fr-mcp-016) `create_issue` — 일감 만들기 | M | [3.6](../wbs/04-stage-3-mcp.md#w-3-6) 할 일 |
| [FR-MCP-017](12-mcp.md#fr-mcp-017) `update_issue` — 일감 고치기 · 상태 전이 · 담당 | M | [3.6](../wbs/04-stage-3-mcp.md#w-3-6) 할 일 |
| [FR-MCP-018](12-mcp.md#fr-mcp-018) `add_issue_note` — 메모 달기 | M | [3.6](../wbs/04-stage-3-mcp.md#w-3-6) 할 일 |
| [FR-MCP-019](12-mcp.md#fr-mcp-019) `add_issue_relation` · `remove_issue_relation` — 관계 걸고 풀기 | M | [3.6](../wbs/04-stage-3-mcp.md#w-3-6) 할 일 |
| [FR-MCP-020](12-mcp.md#fr-mcp-020) `list_time_entries` · `log_time` — 시간 기록 | S | [4.10](../wbs/05-stage-4-planning-time.md#w-4-10) 할 일 |
| [FR-MCP-021](12-mcp.md#fr-mcp-021) `create_wiki_page` · `update_wiki_page` — 위키 쓰기 | S | [5.7](../wbs/06-stage-5-collaboration.md#w-5-7) 할 일 |
| [FR-MCP-022](12-mcp.md#fr-mcp-022) 리소스 | S | [5.7](../wbs/06-stage-5-collaboration.md#w-5-7) 할 일 |
| [FR-MCP-023](12-mcp.md#fr-mcp-023) 서버 instructions | M | [3.8](../wbs/04-stage-3-mcp.md#w-3-8) 할 일 |
| [FR-MCP-024](12-mcp.md#fr-mcp-024) 낙관적 잠금 | M | [3.7](../wbs/04-stage-3-mcp.md#w-3-7) 할 일 |
| [FR-MCP-025](12-mcp.md#fr-mcp-025) 멱등 키 | M | [3.7](../wbs/04-stage-3-mcp.md#w-3-7) 할 일 |
| [FR-MCP-026](12-mcp.md#fr-mcp-026) 속도 제한 · 일 한도 | M | [3.7](../wbs/04-stage-3-mcp.md#w-3-7) 할 일 |
| [FR-MCP-027](12-mcp.md#fr-mcp-027) 응답 크기 · 페이지 | M | [3.7](../wbs/04-stage-3-mcp.md#w-3-7) 할 일 |
| [FR-MCP-028](12-mcp.md#fr-mcp-028) 사용자 데이터 표시 · 출처 표시 | M | [3.7](../wbs/04-stage-3-mcp.md#w-3-7) 할 일 |
| [FR-MCP-029](12-mcp.md#fr-mcp-029) MCP 로 하지 않는 일 | M | [3.7](../wbs/04-stage-3-mcp.md#w-3-7) 할 일 |
| [FR-MCP-030](12-mcp.md#fr-mcp-030) 감사 · 호출 기록 | M | [3.9](../wbs/04-stage-3-mcp.md#w-3-9) 할 일 |
| [FR-MCP-031](12-mcp.md#fr-mcp-031) 사용량 보기 | S | [3.9](../wbs/04-stage-3-mcp.md#w-3-9) 할 일 |
| [FR-MCP-032](12-mcp.md#fr-mcp-032) 연결 안내 화면 | M | [3.8](../wbs/04-stage-3-mcp.md#w-3-8) 할 일 |
| [FR-MCP-033](12-mcp.md#fr-mcp-033) 고정 입구 `/mcp/project` 에서 옮기기 | M | [3.2](../wbs/04-stage-3-mcp.md#w-3-2) 할 일 |
| [FR-MCP-034](12-mcp.md#fr-mcp-034) OAuth 2.1 로 붙기 | C | — |
| [FR-MCP-035](12-mcp.md#fr-mcp-035) 프롬프트 | C | — |

### 비기능

| 요구사항 | 우선 | 작업 |
|---|---|---|
| [NFR-PERF-001](13-non-functional.md#nfr-perf-001) 화면 · REST 응답 시간 | M | [6.6](../wbs/07-stage-6-integration.md#w-6-6) 할 일 |
| [NFR-PERF-002](13-non-functional.md#nfr-perf-002) MCP 도구 응답 시간 | M | [3.10](../wbs/04-stage-3-mcp.md#w-3-10) 할 일 |
| [NFR-SCL-001](13-non-functional.md#nfr-scl-001) 규모 | S | [6.6](../wbs/07-stage-6-integration.md#w-6-6) 할 일 |
| [NFR-SEC-001](13-non-functional.md#nfr-sec-001) 경계 누출 0 | M | — |
| [NFR-SEC-002](13-non-functional.md#nfr-sec-002) Agent 의 한계 | M | [3.7](../wbs/04-stage-3-mcp.md#w-3-7) 할 일 |
| [NFR-AVL-001](13-non-functional.md#nfr-avl-001) 가용성 | S | [6.7](../wbs/07-stage-6-integration.md#w-6-7) 할 일 |
| [NFR-OBS-001](13-non-functional.md#nfr-obs-001) 관측 | S | [6.7](../wbs/07-stage-6-integration.md#w-6-7) 할 일 |
| [NFR-MNT-001](13-non-functional.md#nfr-mnt-001) 형제 저장소와 맞추기 | S | — |
| [NFR-DEP-001](13-non-functional.md#nfr-dep-001) 배포와 설정 | S | [6.7](../wbs/07-stage-6-integration.md#w-6-7) 할 일 |
| [NFR-USA-001](13-non-functional.md#nfr-usa-001) 반응형 · 접근성 | M | [1.3](../wbs/02-stage-1-foundation.md#w-1-3) 할 일 |

---

**관련 문서:** [요구사항 전체 목록](00-overview.md) · [WBS 한눈에](../wbs/00-overview.md) · [구현한 기능](../wbs/01-implemented.md)

[← 이전: 비기능 요구사항](13-non-functional.md) | [다음: 핵심 설계 결정 →](../design/00-key-decisions.md)
