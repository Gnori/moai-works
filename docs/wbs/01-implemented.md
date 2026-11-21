[홈](../README.md) › [WBS](00-overview.md) › 구현한 기능

# 구현한 기능

지금 코드에 있는 기능을 모듈별로 적는다. 2026-11-21 기준 — 모두 moai-wiki 에서 가져온 토대다([ADR-002](../design/00-key-decisions.md#adr-002)). 프로젝트 관리 기능은 아직 없다.

**바로가기:** [api](#im-api) · [web-backend](#im-web-backend) · [web-frontend](#im-web-frontend) · [infra](#im-infra) · [가져오며 바꾼 것](#im-changed)

---

<a id="im-api"></a>
## 1. api (`src/moai-works-api`)

| 기능 | 요구사항 | 확인 |
|---|---|---|
| 가입 · 이메일 인증 코드(메일은 로그 모드) · 가입 정책(시스템 관리자) | [FR-WSP](../requirements/01-workspace.md) | 자동 테스트 |
| 계정 상태 · 시스템 관리자의 계정 승인 · 정지 · 팀 워크스페이스 허용 수 | FR-WSP | 자동 테스트 |
| 워크스페이스 — 개인(가입 때 자동) · 팀(퍼블릭/프라이빗 · 승인/자동 가입) · 주소 `workspace-<12자>` · 설정 · 찾기 | FR-WSP | 자동 테스트 |
| 기본 역할 owner · admin · member, 사용자 정의 역할, 권한 상승 방지, 마지막 소유자 보호 | FR-WSP | 자동 테스트 |
| 멤버십 · 가입 신청 · 초대(이메일에 묶임 · 토큰 SHA-256) | FR-WSP | 자동 테스트 |
| Keycloak 이중 쓰기 기록(멱등 · 이어서 끝내기) · 권한 카탈로그 동기화 · 카탈로그 바뀌면 역할 맞추기 | FR-WSP | 자동 테스트(가짜 Keycloak) |
| 감사(append-only, 워크스페이스별 조회) | FR-WSP | 자동 테스트 |
| REST JWT 직접 검증(JWKS · 발급자 · audience) · 접근 어노테이션 · 기동 검사 | FR-WSP | 자동 테스트 |
| MCP 키(개인 · 서비스, SHA-256 만 저장) · 고정 MCP 입구 `/mcp/project` · 골격 도구 `list_projects` | [FR-MCP](../requirements/12-mcp.md) | 빌드만(자동 테스트의 스프링 컨텍스트에서 MCP 서버가 만들어진다). 키 발급 · 검사의 자동 테스트는 없고(moai-wiki 에도 없다), 서버를 띄워 실제 MCP 클라이언트로 붙여 보지도 않았다 |
| 객체 저장소 추상(MinIO · S3) — 첨부용, 쓰는 곳 없음 | [FR-ISS](../requirements/03-issue.md) | — |

자동 테스트 66건(`./mvnw -q package`): 토큰 · 접근 · 멤버십 · 역할 · 초대 · 가입 신청 · 팀 워크스페이스 규칙 · 권한 카탈로그.

<a id="im-web-backend"></a>
## 2. web-backend (`src/moai-works-web-backend`)

| 기능 | 확인 |
|---|---|
| 로그인(패스워드 그랜트) · 서버 세션(Spring Session Redis, `moai-works:` 아래) · 세션 id 교체 · 로그인 시도 제한 | 빌드만. moai-works 렐름에서 실제 흐름 확인은 아직 |
| CSRF(SPA 쿠키) · 토큰 갱신 · 로그아웃(Keycloak 세션도 끝냄) · 보안 헤더 · CSP | 같음 |
| `/api/v1/**` → api `/moai-works-api/v1/**` 1:1 전달(헤더 허용 목록) · 화면 정적 파일 | 같음 |

<a id="im-web-frontend"></a>
## 3. web-frontend (`src/moai-works-web-frontend`)

| 화면 | 확인 |
|---|---|
| 로그인 · 가입 · 인증 코드 | 빌드 · 자동 테스트. 브라우저 확인은 아직 |
| 워크스페이스 홈 · 전환 · 찾기 · 만들기 · 초대 링크 · 받은 초대 | 같음 |
| 멤버 · 가입 신청 · 역할과 권한(사용자 정의 역할) · 설정 · 감사 | 같음 |
| 시스템 관리자 — 가입 정책 · 계정 | 같음 |
| 다국어(ko · en) · 다크 · 스킨 · 반응형 틀 | 같음 |

자동 테스트 60건(`npm test`): 역할 규칙 사본 · 다국어 리소스 · 상태 표시 등.

<a id="im-infra"></a>
## 4. infra

| 것 | 상태 |
|---|---|
| `infra/keycloak` — 렐름 `moai-works` 정의 · `init-realm.sh` · `ensure-realm.py` · 권한 카탈로그 | 파일만. 같이 쓰는 MOAI-Keycloak 에 렐름을 아직 만들지 않았다 |
| `infra/tls/make-certs.sh` | 그대로(moai-wiki 와 같은 mkcert 방식) |
| `infra/dev-seed` — 계정 moaiworks01~10 · 워크스페이스 A · B · C | 파일만. 렐름이 생기면 돌린다 |
| `infra/minio/init-minio.sh` — 버킷 `moai-works-attachments` · api 사용자 | 파일만. 첨부를 만들 때 돌린다 |
| MOAI-DB 의 DB `moai_works` · 계정 `moai_works` | 2026-11-21 만들었다(비밀번호는 `src/moai-works-api/.env`) |

<a id="im-changed"></a>
## 5. 가져오며 바꾼 것

| moai-wiki | moai-works | 이유 |
|---|---|---|
| 모듈 여섯(api · agent · collector · refiner · web-backend · web-frontend) | 셋(api · web-backend · web-frontend) | AI 를 내장하지 않는다([ADR-003](../design/00-key-decisions.md#adr-003)) |
| 위키 · 페이지 · 자료실 · 정제 작업 코드와 화면 | 뺐다 | 다른 제품 |
| 기본 역할 넷(owner · admin · curator · member) | 셋(curator 뺌) | [ADR-016](../design/00-key-decisions.md#adr-016) |
| 권한 코드 33개 | 토대 16개(위키 · 페이지 · 자료 코드 뺌). 프로젝트 · 일감 코드는 그 기능을 만들 때 | 같음 |
| 개인 워크스페이스에 "내 위키" 자동 생성 | 하지 않음 | 위키가 없다 |
| MCP 입구 `/mcp/agent` · `/mcp/query`, 작업 헤더 `x-moai-wiki-job-*` | `/mcp/project` 하나, 작업 헤더 없음 | 내장 agent 가 없다 |
| Flyway V1 ~ V10 | `V1__foundation.sql` 하나 | [ADR-015](../design/00-key-decisions.md#adr-015) |
| 포트 10001 · 10003 · 10004, 렐름 `moai-wiki`, 클라이언트 collector · refiner | 10011 · 10013 · 10014, 렐름 `moai-works`, 클라이언트 api · web-backend | [ADR-014](../design/00-key-decisions.md#adr-014) |
| 버킷 raw · data, MinIO 사용자 셋 | 버킷 `moai-works-attachments`, api 사용자 하나 | 첨부만 있다 |

---

**관련 문서:** [WBS 한눈에](00-overview.md) · [1단계 토대](02-stage-1-foundation.md) · [요구사항 진행 현황](../requirements/14-progress.md) · [모듈 한눈에](../modules/00-overview.md)

[← 이전: WBS 한눈에](00-overview.md) | [다음: 1단계 — 토대 (moai-wiki 에서 가져옴) →](02-stage-1-foundation.md)
