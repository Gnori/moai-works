[홈](../README.md) › [WBS](00-overview.md) › 구현한 기능

# 구현한 기능

지금 코드에 있는 기능을 모듈별로 적는다. 2026-11-22 기준 — 모두 moai-wiki 에서 가져온 토대다([ADR-002](../design/00-key-decisions.md#adr-002)). 프로젝트 · 일감 기능은 아직 없다 — moai-wiki 의 위키 · 페이지 자리에 같은 구조로 만든다(2 · 3단계).

> **진행 중 (2026-11-22):** 기본 역할을 moai-wiki 와 같은 넷(큐레이터를 되살림)으로, 권한 코드를 25개(프로젝트 · 일감 코드 9개를 더함)로 맞추는 일을 [2.1](03-stage-2-project.md#w-2-1) 에서 하고 있다. 끝나지 않았으므로 아래 표는 지금 코드(기본 역할 셋 · 코드 16개) 그대로다.

**바로가기:** [api](#im-api) · [web-backend](#im-web-backend) · [web-frontend](#im-web-frontend) · [infra](#im-infra) · [가져오며 바꾼 것](#im-changed)

---

<a id="im-api"></a>
## 1. api (`src/moai-works-api`)

| 기능 | 요구사항 | 확인 |
|---|---|---|
| 가입 · 이메일 인증 코드(메일은 로그 모드) · 가입 정책(시스템 관리자) | [FR-WSP](../requirements/01-workspace.md) | 자동 테스트 |
| 계정 상태 · 시스템 관리자의 계정 승인 · 정지 · 팀 워크스페이스 허용 수 | FR-WSP | 자동 테스트 |
| **비밀번호 변경 · 찾기**(2026-11-24, [WBS 1.5](02-stage-1-foundation.md#w-1-5)): `POST /v1/me/password`(시스템 관리자도 — 지금 비밀번호를 Keycloak 패스워드 그랜트로 확인, 틀리면 400 · 사람마다 15분 5번, 정책 까닭, 다른 기기의 로그인 끊기 — V22 끊긴 로그인 표시 + Keycloak 세션 지우기, 이 로그인(sid)만 남김) · 공개 `POST /v1/password-reset/request · confirm`(늘 같은 202 · 뒤에서 메일, IP · 이메일 제한, 가입과 같은 코드 장치, 성공하면 모두 끊기 · 잠금 풀기) · 감사 `account.password.change · reset` · "비밀번호가 바뀌었습니다" 메일 | [FR-WSP-006](../requirements/01-workspace.md#fr-wsp-006) | 자동 테스트(PasswordTest 22건, 가짜 Keycloak). 실제 Keycloak 확인은 합친 뒤 |
| 시스템 관리자의 팀 워크스페이스 사용 중지 · 다시 사용 · 삭제(사용 중지한 뒤에만, 확인 문구) · 워크스페이스 목록(내용 없음) — Keycloak 조직 먼저, 멤버 REST 403 `WORKSPACE_SUSPENDED` · MCP 숨김, 감사 `admin.workspace.*` (2026-11-23, [WBS 1.8](02-stage-1-foundation.md#w-1-8)) | [FR-WSP-019](../requirements/01-workspace.md#fr-wsp-019) · [FR-WSP-020](../requirements/01-workspace.md#fr-wsp-020) | 자동 테스트(WorkspaceSuspendTest 8건, 가짜 Keycloak · 메모리 저장소). 실제 서비스 확인은 아직 |
| 워크스페이스 — 개인(가입 때 자동) · 팀(퍼블릭/프라이빗 · 승인/자동 가입) · 주소 `workspace-<12자>` · 설정 · 찾기 | FR-WSP | 자동 테스트 |
| 기본 역할 owner · admin · curator · member(moai-wiki 와 같은 넷, 2026-11-22 큐레이터 되살림) · 권한 코드 25개, 사용자 정의 역할, 권한 상승 방지, 마지막 소유자 보호 | FR-WSP | 자동 테스트 |
| 멤버십 · 가입 신청 · 초대(이메일에 묶임 · 토큰 SHA-256) | FR-WSP | 자동 테스트 |
| Keycloak 이중 쓰기 기록(멱등 · 이어서 끝내기) · 권한 카탈로그 동기화 · 카탈로그 바뀌면 역할 맞추기 | FR-WSP | 자동 테스트(가짜 Keycloak) |
| 감사(append-only, 워크스페이스별 조회) | FR-WSP | 자동 테스트 |
| REST JWT 직접 검증(JWKS · 발급자 · audience) · 접근 어노테이션 · 기동 검사 | FR-WSP | 자동 테스트 |
| MCP 키(개인 · 서비스 종류 표시, SHA-256 만 저장 — 서비스 종류는 계획에서 없앴다, [ADR-024](../design/00-key-decisions.md#adr-024)) · 고정 MCP 입구 `/mcp/project` · 골격 도구 `list_projects` (2026-11-23 지금: 기능별 입구 일감 `/mcp/issue` · 프로젝트 관리 `/mcp/project`, 키는 입구에 묶지 않음 — [ADR-028](../design/00-key-decisions.md#adr-028). 같은 날 키를 워크스페이스에서도 뗌 — 사용자 키, 개인 설정에서 받고 키 하나로 MCP 권한이 있는 모든 워크스페이스, 도구마다 선택 인자 `workspace` · `list_workspaces` · `PROJECT_AMBIGUOUS`, V13 — [ADR-029](../design/00-key-decisions.md#adr-029)) | [FR-MCP](../requirements/04-mcp.md) | 빌드만(자동 테스트의 스프링 컨텍스트에서 MCP 서버가 만들어진다). 키 발급 · 검사의 자동 테스트는 없고(moai-wiki 에도 없다), 서버를 띄워 실제 MCP 클라이언트로 붙여 보지도 않았다 |
| **프로젝트**(`project/` — moai-wiki 의 `wiki/` 자리): `@ProjectApi` · 판정 · 만들기 · 목록 · 상세 · 설정 · 보관 · 복원 · 삭제 · 내 프로젝트 | [FR-PRJ](../requirements/02-project.md) | 자동 테스트(ProjectTest 12건) · 시드 · 브라우저 |
| **프로젝트 역할** 넷 — Keycloak 조직 그룹 `/projects/<id>/<role>` 먼저 · 사본 판정 · 권한 상승 방지 · 나가면 사본 삭제 | FR-PRJ | 자동 테스트(ProjectRoleTest 7건, 가짜 Keycloak) · 시드(실제 Keycloak) |
| **일감**(`issue/` — moai-wiki 의 `page/` 자리): 유형 넷 · 프로젝트 안 순번 · 상태 넷과 완료일 · 종료일 자동 · 관리자 · 담당자 · 다섯 날짜 · 상위 · 하위(10단계) · 연결 넷 · 이력 · 댓글 · 지우기 · 목록 필터와 정렬 · 동시 수정 409 · 쓰기 여섯 가지 감사 | [FR-ISS](../requirements/03-issue.md) | 자동 테스트(IssueTest 14건) · 시드 · 브라우저 |
| 객체 저장소 추상(MinIO · S3) — 첨부용, 쓰는 곳 없음 | — (첨부는 [나중 후보](../01-overview.md#32-나중-후보)) | — |

자동 테스트 99건(`./mvnw -q package`, 2026-11-22): 토큰 · 접근 · 멤버십 · 역할 · 초대 · 가입 신청 · 팀 워크스페이스 규칙 · 권한 카탈로그 · 프로젝트 · 프로젝트 역할 · 일감.

<a id="im-web-backend"></a>
## 2. web-backend (`src/moai-works-web-backend`)

| 기능 | 확인 |
|---|---|
| 로그인(패스워드 그랜트) · 서버 세션(Spring Session Redis, `moai-works:` 아래) · 세션 id 교체 · 로그인 시도 제한 | 2026-11-22 moai-works 렐름에서 실제로 확인(ego-browser · 시도 제한은 스크립트) |
| CSRF(SPA 쿠키) · 토큰 갱신 · 로그아웃(Keycloak 세션도 끝냄) · 보안 헤더 · CSP | CSRF 거부 · 로그아웃은 2026-11-22 확인. 토큰 갱신은 실제로 기다려 보지 않았다 |
| `/api/v1/**` → api `/moai-works-api/v1/**` 1:1 전달(헤더 허용 목록) · 화면 정적 파일 | 2026-11-22 로그인 뒤 화면이 api 를 불러 워크스페이스 홈을 그리는 것까지 확인 |
| 비밀번호 찾기 공개 경로 `/api/v1/password-reset/**`(토큰 없이 · CSRF 는 봄) · api 가 401 이면 그 자리에서 세션을 끝냄(비밀번호 변경으로 끊긴 다른 기기의 로그인) — 2026-11-24 | 자동 테스트(ApiRelayTest 4건). 실제 호출은 합친 뒤 |

<a id="im-web-frontend"></a>
## 3. web-frontend (`src/moai-works-web-frontend`)

| 화면 | 확인 |
|---|---|
| 로그인 · 가입 · 인증 코드 | 로그인 · 로그아웃은 2026-11-22 브라우저로 확인. 가입 · 인증 코드는 빌드 · 자동 테스트만 |
| 비밀번호 — 개인 설정 › 비밀번호(지금 · 새 · 확인, 다른 기기의 로그인 끊기 기본 켬, 시스템 관리자도) · 계정 메뉴 "비밀번호 변경" · 로그인 화면 "비밀번호를 잊으셨나요?" → 이메일 → 코드 + 새 비밀번호 두 번 · 정책 까닭을 화면 언어로 (2026-11-24) | 자동 테스트 · 빌드. 브라우저 확인은 합친 뒤 |
| 워크스페이스 홈 · 전환 · 찾기 · 만들기 · 초대 링크 · 받은 초대 | 홈이 로그인 뒤 열리는 것만 확인. 나머지는 빌드 · 자동 테스트만 |
| 멤버 · 가입 신청 · 역할과 권한(사용자 정의 역할) · 설정 · 감사 | 빌드 · 자동 테스트만. 브라우저 확인은 아직 |
| 시스템 관리자 — 가입 정책 · 계정 | 같음 |
| 시스템 관리자 — 워크스페이스 관리(`/admin/workspaces` — 탭 사용 중 · 사용 중지 · 개인 · 전체, 찾기, 사용 중지(이유) · 다시 사용 · 지우기(영향 미리보기 + 주소 입력)) · 멤버의 사용 중지 안내 · 전환 메뉴의 "사용 중지" 표시 (2026-11-23) | 빌드 · 자동 테스트(workspaces-screens.test 5건). 브라우저 확인은 아직 |
| 다국어(ko · en) · 다크 · 스킨 · 반응형 틀 | 같음 |
| **프로젝트**(`features/project` — moai-wiki `features/wiki` 자리): 목록 · 카드 · 만들기 · 설정 대화 상자 · 보관함 · 지우기 대화 상자 · 프로젝트 홈 · 멤버(프로젝트 역할) · 사이드바 프로젝트 목록 · 워크스페이스 홈의 프로젝트 카드 | 2026-11-22 ego-browser — 프로젝트 홈 · 멤버 화면(데스크톱 · 390px · 다크 · 영어), 멤버에게 설정 · 보관 단추 없음. 만들기 · 보관 · 지우기 흐름은 아직 |
| **일감**(`features/issue` — moai-wiki `features/page` 자리): 목록(표 · 390px 카드 · 필터를 주소에) · 새 일감 · 보기 · 고치기(바뀐 필드만, 충돌 알림) · 상태 바꾸기 · 하위 · 연결 · 이력 · 댓글 · 지우기 · Markdown 보기 · 편집기 | 2026-11-22 ego-browser — 만들기 · 고치기 · 상태(완료일 자동) · 이력(만들기 첫 줄 · 차단됨) · 멤버에게 고치기 · 지우기 없음 · 390px · 다크 · 영어 |

자동 테스트 98건(`npm test`, 2026-11-22): 역할 규칙 사본 · 다국어 리소스 · 상태 표시 · 일감 필터 ↔ 주소 · 바뀐 필드만 보내기 · 이력 읽기 · 프로젝트 역할 규칙 · Markdown 플러그인 등.

<a id="im-infra"></a>
## 4. infra

| 것 | 상태 |
|---|---|
| `infra/keycloak` — 렐름 `moai-works` 정의 · `init-realm.sh` · `ensure-realm.py` · 권한 카탈로그 | 2026-11-22 같이 쓰는 MOAI-Keycloak 에 렐름을 만들었다(`init-realm.sh`). 클라이언트 비밀값 · 처음 시스템 관리자 비밀번호는 `infra/keycloak/.env` |
| `infra/tls/make-certs.sh` | moai-wiki 와 같은 mkcert 방식. 2026-11-22 인증서를 만들었다 |
| `infra/dev-seed` — 계정 moaiworks01~10 · 워크스페이스 A · B · C | 2026-11-22 돌렸다 — 계정 10개 · 각자의 개인 워크스페이스 · 팀 워크스페이스 A · B · C |
| `infra/minio/init-minio.sh` — 버킷 `moai-works-attachments` · api 사용자 | 파일만. 첨부를 만들 때 돌린다 |
| MOAI-DB 의 DB `moai_works` · 계정 `moai_works` | 2026-11-21 만들었다(비밀번호는 `src/moai-works-api/.env`) |

<a id="im-changed"></a>
## 5. 가져오며 바꾼 것

| moai-wiki | moai-works | 이유 |
|---|---|---|
| 모듈 여섯(api · agent · collector · refiner · web-backend · web-frontend) | 셋(api · web-backend · web-frontend) | AI 를 내장하지 않는다([ADR-003](../design/00-key-decisions.md#adr-003)) |
| 위키 · 페이지 · 자료실 · 정제 작업 코드와 화면 | 뺐다. 위키 · 페이지 자리에는 프로젝트 · 일감을 같은 구조로 다시 만든다(2 · 3단계). 자료실 · 정제는 없다 | moai-wiki 와 같고 위키 → 프로젝트, 페이지 → 일감([ADR-002](../design/00-key-decisions.md#adr-002)) |
| 기본 역할 넷(owner · admin · curator · member) | 지금 코드는 셋. 가져올 때 큐레이터를 뺐다가(2026-11-21) 넷으로 되돌린다(2026-11-22 — [2.1](03-stage-2-project.md#w-2-1) 진행 중) | [ADR-017](../design/00-key-decisions.md#adr-017) |
| 권한 코드 33개 | 지금 코드는 토대 16개. 위키 · 페이지 코드를 프로젝트 · 일감 코드 9개로 바꿔 25개로 맞춘다(내장 AI · 자료실 코드는 뺀다 — [2.1](03-stage-2-project.md#w-2-1) 진행 중) | 같음 |
| 개인 워크스페이스에 "내 위키" 자동 생성 | 지금 코드는 하지 않음. "내 프로젝트"(key `my`)로 바꿔 만든다([2.2](03-stage-2-project.md#w-2-2)) | 위키 자리가 프로젝트다 |
| MCP 입구 `/mcp/agent` · `/mcp/query`, 작업 헤더 `x-moai-wiki-job-*` | 기능별 입구 둘 — 일감 `/mcp/issue` · 프로젝트 관리 `/mcp/project`(2026-11-23), 키 하나로 둘 다, 작업 헤더 없음. 워크스페이스 엔드포인트 `/mcp/{ws}/{ep}` 는 만들지 않는다 — 키는 사용자의 것이고 워크스페이스는 도구 호출마다 고른다(2026-11-23 [ADR-029](../design/00-key-decisions.md#adr-029), [4.2](05-stage-4-mcp.md#w-4-2)) | 내장 agent 가 없다 |
| Flyway V1 ~ V10 | `V1__foundation.sql` 하나 | [ADR-015](../design/00-key-decisions.md#adr-015) |
| 포트 10001 · 10003 · 10004, 렐름 `moai-wiki`, 클라이언트 collector · refiner | 11001 · 11002 · 11003, 렐름 `moai-works`, 클라이언트 api · web-backend | [ADR-014](../design/00-key-decisions.md#adr-014) |
| 버킷 raw · data, MinIO 사용자 셋 | 버킷 `moai-works-attachments`, api 사용자 하나 | 첨부(나중 후보)만 쓴다 |

---

**관련 문서:** [WBS 한눈에](00-overview.md) · [1단계 토대](02-stage-1-foundation.md) · [요구사항 진행 현황](../requirements/06-progress.md) · [모듈 한눈에](../modules/00-overview.md)

[← 이전: WBS 한눈에](00-overview.md) | [다음: 1단계 — 토대 (moai-wiki 에서 가져옴) →](02-stage-1-foundation.md)
