[홈](../README.md) › [모듈](00-overview.md) › moai-works-api

# moai-works-api

moai-works 의 API 서버. 계정 · 가입 · 워크스페이스 · 역할 · 멤버십 · 초대 · 감사, **모든 권한 판정**, 그리고 **MCP 전부**를 맡는다. DB 는 이 모듈만 쓴다. 프로젝트 · 일감 같은 업무 기능도 모두 여기에 들어온다(계획).

## 1. 하는 일 · 하지 않는 일

| 한다 | 하지 않는다 |
|---|---|
| Keycloak 토큰 확인, **모든 권한 판정**(엔드포인트마다 접근 어노테이션 하나) | 브라우저 세션 ([web-backend](02-web-backend.md) 몫) |
| 계정 사본 · 가입 · 이메일 인증 코드 · 시스템 관리자 API | LLM 호출 — 요약 · 분류 같은 AI 기능은 바깥 Agent 몫이다. MCP 서버 자체도 LLM 을 부르지 않는다 |
| 워크스페이스 · 역할 · 멤버십 · 가입 신청 · 초대 · 감사 | 본문 그리기 (화면 몫 — api 는 Markdown 을 그대로 준다, 계획) |
| Keycloak 관리 API 로 계정 · 조직 · 그룹 쓰기 (Keycloak 이 정본) | 워커 모듈에 일 맡기기 — 비동기 일은 api 안의 작업 큐 · 스케줄러로 한다(계획) |
| MCP 서버: 바깥 AI Agent 용 `/mcp/project` (지금은 골격) | |
| 프로젝트 · 일감 · 워크플로 · 질의 · 버전 · 시간 · 위키 · 알림 · 검색 · 첨부 (계획) | |

## 2. 한눈에

| 항목 | 값 |
|---|---|
| 스택 | Spring Boot 4.2.0 · Spring Security 7.2.0 (OAuth2 리소스 서버) · Spring AI 2.1.0 (MCP Java SDK 2.2.0, `spring-ai-starter-mcp-server-webmvc`) · Jackson 3 · Spring Data JPA · Flyway · PostgreSQL · AWS SDK for Java 2.59.7(S3 — HTTP 는 JDK 의 것만 쓴다) · Java 21 · Maven (`mvnw`) |
| 패키지 | `com.moai.works.api` |
| 포트 | 10011 (`MOAI_WORKS_API_PORT`), **HTTPS** — `server.ssl.*` ← `MOAI_WORKS_TLS_ENABLED`(기본 true) · `MOAI_WORKS_TLS_CERT` · `MOAI_WORKS_TLS_KEY`. 로컬 인증서는 `infra/tls/make-certs.sh`([DEVELOPE.md §5.2](../../DEVELOPE.md#52-포트--https)). `localhost` 에는 HSTS 를 보내지 않는다(`security/HstsRule`) |
| REST | `/moai-works-api/**`. Keycloak JWT 를 **JWKS 로 직접 검증**한다(서명 · 만료 · 발급자 · audience `moai-works-api`). 요청마다 Keycloak 에 묻지 않는다 |
| MCP | `/mcp/project`(`moai-works.mcp.project-endpoint`): Streamable HTTP, MCP 키. 워크스페이스 MCP 엔드포인트 `/mcp/{워크스페이스}/{엔드포인트}` 로 옮길 예정이다([MCP · AI Agent 설계](../design/07-mcp-agents.md)) |
| Keycloak 클라이언트 | `moai-works-api`: 토큰 audience, 서비스 계정으로 관리 API(`realm-management` 역할 + `kc-admin-api` scope). 권한 코드 16개가 이 클라이언트의 클라이언트 역할이다 |
| 저장소 | PostgreSQL `moai_works` (스키마는 Flyway `db/migration` 이 정본, `ddl-auto: validate`, 시간대 UTC) · 객체 저장소(S3 호환, 로컬은 MOAI-MinIO) 버킷 `moai-works-attachments` — 사용자 `moai-works-api`. 저장소 코드는 있지만 쓰는 기능은 아직 없다(첨부, 계획) |
| 비밀값 (`.env`, git 제외) | `MOAI_WORKS_DB_URL` · `MOAI_WORKS_DB_USERNAME` · `MOAI_WORKS_DB_PASSWORD` · `MOAI_WORKS_KEYCLOAK_URL` · `MOAI_WORKS_KEYCLOAK_REALM` · `MOAI_WORKS_API_CLIENT_ID` · `MOAI_WORKS_API_CLIENT_SECRET` · `MOAI_WORKS_CODE_SECRET`(인증 코드 HMAC, 32자 이상 — 없으면 기동하지 않는다) · `MOAI_WORKS_API_S3_ACCESS_KEY` · `MOAI_WORKS_API_S3_SECRET_KEY`(객체 저장소 — `infra/minio/init-minio.sh` 가 넣어 준다. 비어 있어도 뜨고, 저장소를 처음 쓸 때 503 `STORAGE_NOT_CONFIGURED`) |
| 그 밖의 설정 | `MOAI_WORKS_WEB_BASE_URL`(초대 링크의 웹 주소, 기본 https://localhost:10013) · `MOAI_WORKS_MAIL_MODE`(기본 `log`: 메일을 보내지 않고 로그에 남김) · `MOAI_WORKS_MAIL_FROM` · `MOAI_WORKS_S3_ENDPOINT`(기본 http://localhost:9000) · `MOAI_WORKS_S3_ATTACHMENT_BUCKET`(기본 `moai-works-attachments`) · `MOAI_WORKS_MAX_FILE_MB`(올리는 파일 하나의 상한, 기본 200 — 넘으면 413 `FILE_TOO_LARGE`) · MCP 키 기간 `moai-works.mcp.key-default-days`(90) · `key-max-days`(365) · 로그 `MOAI_WORKS_LOG_FILE` · `MOAI_WORKS_LOG_CONSOLE` · `MOAI_WORKS_LOG_MAX_FILE_SIZE` · `MOAI_WORKS_LOG_MAX_HISTORY` · `MOAI_WORKS_LOG_TOTAL_SIZE_CAP`(`bin/startup.sh` 가 정한다 — [DEVELOPE.md §5.1](../../DEVELOPE.md#51-띄우고-끄기)) |
| 참고한 모듈 | moai-wiki `moai-wiki-api` 의 토대(그대로 옮김). 그 원본은 SeedSiteKeeper `seed-site-keeper-api` 의 좋은 점(MCP 키, 기본 거부)만 옮겼고, 권한 판정을 BFF 에 맡긴 구조는 따르지 않았다 |

## 3. 구조

```
com.moai.works.api
  account/    계정 사본(UserProfile) · 가입(SignupService) · 이메일 인증 코드(EmailVerification) · /v1/me(MeController · MeService)
              · 가입 시도 제한(SignupRateLimiter) · 메일(Mailer, 지금은 log 모드) · 플랫폼 역할 이름(PlatformRole)
  platform/   가입 정책(platform_setting · SignupPolicy) · 시스템 관리자 API(/v1/admin — AdminController · AccountAdminService)
  workspace/  워크스페이스 · 역할 · 멤버십 · 가입 신청 · 초대 · 권한 카탈로그(Permission · BuiltinRole · PermissionCatalogController)
              · 접근 판정(WorkspaceAccessService) · 워크스페이스 만들기 사가(WorkspaceProvisioner) · 주소(WorkspaceSlugs)
              · 카탈로그 동기화(PermissionCatalogSync) · 카탈로그가 바뀌면 기존 역할을 맞춤(PermissionCatalogMigrator)
  keycloak/   관리 API 클라이언트(KeycloakAdminClient) · 쓰기 기록(KeycloakOperationLog, 멱등)
  audit/      감사 기록 (추가 전용, DB 트리거가 수정 · 삭제를 막는다)
  security/   JWT · MCP 키 필터 체인(SecurityConfiguration · McpKeyAuthenticationFilter) · KeycloakPrincipal
              · HstsRule(localhost 에는 HSTS 를 보내지 않음) · access/(접근 어노테이션 · 인터셉터 · 기동 검사)
  common/     ApiException → problem+json(code 포함, with() 로 화면이 쓸 값을 더한다) · Actor · Ids(UUID 버전 7)
  mcpkey/     MCP 키 발급 · 목록 · 폐기 · 검증(McpKeyService · McpKeyTokenCodec)
  mcp/        MCP 서버(McpServerConfiguration — 고정 입구 1개) · 도구(ProjectMcpTools, 골격) · 호출 문맥(McpCallContext)
  storage/    객체 저장소(ObjectStore · S3ObjectStore — AWS SDK, JDK HTTP 클라이언트). 쓰는 곳은 아직 없다
  controller/ WhoAmI(/moai-works-api/me — 토큰 주인 확인용)
  config/     설정(MoaiWorksProperties) · 경로 상수(EndPointPaths) · MCP 입구(McpEndpoint)
resources/db/migration/
  V1__foundation.sql   계정(user_profile) · 가입 정책(platform_setting) · 인증 코드(email_verification) · workspace · permission
                       · workspace_role · role_permission · workspace_member · member_role · join_request · invitation
                       · keycloak_operation · audit_log(+ 추가 전용 트리거) · mcp_api_key.
                       moai-wiki 토대 스키마(V1~V3 · V8 · V9 의 최종 모양)를 한 파일로 옮겼다

src/test/java/com/moai/works/api
  support/    @ApiTest · TestInfrastructure(시험 스키마 · 가짜 Keycloak · 시험용 토큰 · 메모리 객체 저장소) · TestApi(요청 · 준비)
              · FakeKeycloak · TestTokens · TestUser · MemoryObjectStore
  security/ · workspace/   통합 테스트 (§7.1)
src/test/resources/application-test.yaml
```

프로젝트 · 일감 패키지(`project/` · `issue/` · `workflow/` …)와 그 Flyway 파일은 그 기능을 만들 때 더한다(계획 — 테이블 초안은 [데이터 모델](../design/02-data-model.md)과 각 설계 쪽).

## 4. 권한 판정 요약

엔드포인트마다 접근 어노테이션이 정확히 하나 붙는다. 없으면 기동하지 않는다(기본 거부).

| 어노테이션 | 누가 | 실패 |
|---|---|---|
| `@PublicApi` | 누구나. `/v1/signup/**` 안에서만 쓸 수 있다 | — |
| `@AuthenticatedApi` | 유효한 토큰(사람 · 서비스) | 401 |
| `@UserApi` | 활성 사람 계정 (처음 보는 Keycloak 계정이면 사본을 만든다) | 403 |
| `@ServiceApi(clients=…)` | 지정한 클라이언트의 서비스 계정. 지금 쓰는 엔드포인트는 없다 | 403 |
| `@PlatformApi(role=…)` | 렐름 역할(system-admin 등)을 가진 사람 | 403 |
| `@WorkspaceApi(권한)` | 경로 `{workspace}` 의 활성 멤버이면서 그 권한이 있는 사람 | 비멤버 **404**(숨김) · 권한 없음 **403** |
| `@ProjectApi(권한)` **(계획)** | 경로 `{workspace}` · `{project}` — 그 프로젝트의 독자이면서 그 권한이 있는 사람 | 독자가 아님 · 없는 프로젝트 · 보관된 프로젝트 **404**(숨김) · 권한 없음 **403** |
| `@IssueApi(권한)` **(계획)** | 경로 `{workspace}` · `{number}` — 번호로 일감의 프로젝트를 찾아 같은 판정 | 〃 |

- `AccessContractVerifier` 가 기동 때 모든 REST 핸들러를 검사한다. 어노테이션이 0개이거나 2개 이상인 경우, `@PublicApi` 가 공개 경로 밖에 있는 경우, `@WorkspaceApi` 경로에 `{workspace}` 가 없는 경우 **기동하지 않는다**. 프로젝트 어노테이션을 만들면 같은 검사를 더한다(계획).
- 판정은 `AccessInterceptor` 한 곳에서 한다. 결과(`CurrentUser` · `WorkspaceContext`)는 컨트롤러가 인자로 받는다.
- 권한은 멤버 역할들의 권한 코드를 합한 것이다(`WorkspaceAccessService`). 권한 상승은 `MembershipService` · `RoleService` 가 막는다: 가진 권한 안에서만 역할을 줄 수 있고, 소유자 역할은 소유자만 다루며, 마지막 소유자는 보호한다.
- 기본 역할은 `owner` · `admin` · `member` 3개다(`BuiltinRole`). 프로젝트 안에서 할 일은 프로젝트 역할이 정한다(계획 — [프로젝트 권한 설계](../design/04-project-permissions.md)).
- 토큰 검증, MCP 키, 계정 상태, 권한 상승 방지, 감사의 전체 구조는 [SECURITY.md](../../SECURITY.md)에 있다.

## 5. Keycloak 이 정본: 쓰는 순서

계정 · 멤버십 · 역할은 Keycloak 이 정본이다. 조직 = 워크스페이스, 조직 그룹 `/roles/<key>` = 역할, 클라이언트 역할 = 권한 코드. 프로젝트 역할은 조직 그룹 `/projects/<프로젝트 id>/<역할 키>` 로 둔다(계획).

1. api 가 **Keycloak 에 먼저 쓴다**(`KeycloakAdminClient`, 서비스 계정 토큰은 캐시해서 쓴다).
2. 단계마다 `keycloak_operation` 에 남긴다(`KeycloakOperationLog`, 멱등 키, 별도 트랜잭션). 같은 키가 이미 끝났으면 Keycloak 을 다시 부르지 않는다.
3. DB 사본을 고친다.

워크스페이스 만들기는 `WorkspaceProvisioner` 사가다. 중간에 끊겨도 다음 요청이 이어서 끝낸다(`PROVISIONING` 상태). 권한 판정은 Keycloak 이 아니라 DB 사본으로 한다. 토큰에는 조직 별칭만 있고, 역할 변경이 다음 요청부터 바로 반영돼야 하기 때문이다([워크스페이스·계정·권한 설계](../design/03-workspace-identity.md)).

## 6. 입구와 바깥 연결

### 6.1 REST (`/moai-works-api/v1`)

| 묶음 | 경로 (접근 어노테이션 · 권한) |
|---|---|
| 가입 (공개) | `GET /signup/policy` · `POST /signup` · `POST /signup/verify` · `POST /signup/resend` (`@PublicApi`, 속도 제한) |
| 나 | `GET /me` · `POST /me/login` · `PATCH /me` · `GET /me/workspaces` · `PUT /me/last-workspace` · `GET /me/join-requests` · `DELETE /me/join-requests/{id}` (`@UserApi`) |
| 워크스페이스 | `GET /workspaces?query=`(찾기) · `POST /workspaces`(주소는 서버가 `workspace-고유id` 로 정한다. 시스템 관리자가 허용한 계정만, 허용한 수까지 — 403 `TEAM_WORKSPACE_NOT_ALLOWED` · `TEAM_WORKSPACE_LIMIT`) (`@UserApi`) · `GET /workspaces/{workspace}`(`workspace.read`) · `PATCH /workspaces/{workspace}`(`workspace.settings.manage`) · `POST …/leave`(`workspace.read`) · `GET …/audit?limit=`(`audit.read`) |
| 멤버 | `GET …/members`(`member.read`) · `PUT …/members/{userId}/roles`(`member.role.assign`) · `DELETE …/members/{userId}`(`member.remove`) |
| 가입 신청 | `POST …/join-requests`(`@UserApi` — 멤버가 아닌 사람이 신청) · `GET …/join-requests` · `POST …/join-requests/{id}/approve·reject`(`member.request.review`) |
| 역할 | `GET …/roles`(`workspace.read`) · `POST …/roles` · `PATCH·DELETE …/roles/{key}`(`role.manage`): 사용자 정의 역할(`custom-고유id`). 기본 역할 3개는 바꿀 수 없다 |
| 초대 | 보내는 쪽: `POST·GET …/invitations` · `DELETE …/invitations/{id}`(`member.invite`). 받는 쪽(`@UserApi`): `GET /invitations/{token}` · `POST /invitations/{token}/accept·decline` · `GET /me/invitations` · `POST /me/invitations/{id}/accept·decline` |
| 카탈로그 | `GET /permission-catalog` (`@UserApi`) |
| 시스템 관리자 | `@PlatformApi(system-admin)`: `GET·PUT /admin/settings/signup` · `GET /admin/accounts?status=&limit=` · `POST /admin/accounts/{id}/approve·reject·suspend·resume` · `PUT /admin/accounts/{id}/team-workspace-limit`(그 계정이 만들 수 있는 팀 워크스페이스 수 0 ~ 100 — 가입한 계정은 0) |
| 프로젝트 · 일감 **(계획)** | `/workspaces/{workspace}/projects/{project}/…`(`@ProjectApi`) · 일감은 번호로 바로 `/workspaces/{workspace}/issues/{number}`(`@IssueApi`). 규약은 [바깥 연동 설계](../design/09-integrations.md), 경로와 권한은 [프로젝트 권한](../design/04-project-permissions.md) · [일감 설계](../design/05-issue-tracking.md) |

v1 밖에는 `GET /moai-works-api/me`(`@AuthenticatedApi`, 토큰 내용 그대로 — 연결 확인용)와 `/moai-works-api/mcp-keys`(`POST` 발급 · `GET` 목록 · `DELETE /{id}` 폐기, `@AuthenticatedApi`. 워크스페이스 MCP 엔드포인트로 옮기면 `@WorkspaceApi(MCP_KEY_ISSUE_*)` 로 바뀐다)가 있다.

오류는 RFC 9457 problem+json 이고 `code` 가 있다(예: `WORKSPACE_NOT_FOUND`, `PERMISSION_REQUIRED`, `ESCALATION_DENIED`, `OWNER_ONLY`, `LAST_OWNER`, `ROLE_IN_USE`, `INVITATION_CLOSED`, `REJOIN_WAIT`, `TEAM_WORKSPACE_LIMIT`, 동시 수정 `CONCURRENT_UPDATE`). 화면은 `code` 로 분기한다.

### 6.2 MCP

| 입구 | 쓰는 쪽 | 도구 (지금) |
|---|---|---|
| `/mcp/project` | 바깥 AI Agent (Claude Code 등 MCP 클라이언트) | `list_projects` (골격 — 빈 목록과 키 주인 이름만 준다) |

- 키는 입구에 묶인다(`McpEndpoint.PROJECT`). 다른 입구의 키로는 403 이다. 검사는 요청마다 캐시 없이 한다([SECURITY.md](../../SECURITY.md) §5.2).
- 도구의 범위는 모델이 넘긴 인자가 아니라 `McpCallContext`(키 주인)로 정한다. 도구는 REST 와 같은 서비스 · 같은 판정을 부른다.
- Spring AI 의 자동 MCP 서버와 `@McpTool` 스캐너는 끄고, `McpServerConfiguration` 이 입구별로 도구를 등록한다(`@Tool` + `MethodToolCallbackProvider`).
- moai-works 가 정의하는 HTTP 헤더는 소문자 `x-moai-works-` 접두어를 쓴다. 지금 정의한 것은 없다.
- **계획** — 워크스페이스마다 엔드포인트 여럿(`/mcp/{워크스페이스}/{엔드포인트}`), 엔드포인트에 묶인 키(PERSONAL · SERVICE = Agent 계정), 읽기 도구(`whoami` · `search_issues` · `get_issue` …)와 쓰기 도구(`create_issue` · `update_issue` · `add_issue_note` · `log_time` …), 안전장치(`lock_version` · `idempotency_key` · 속도 제한). 도구 표는 [MCP · AI Agent 설계](../design/07-mcp-agents.md).

### 6.3 api 가 부르는 것

| 대상 | 무엇 |
|---|---|
| Keycloak | JWKS(토큰 검증), 토큰 엔드포인트(서비스 계정 client_credentials), 관리 API(사용자 · 조직 · 그룹 · 역할) |
| PostgreSQL | 정본 데이터 |
| 객체 저장소 (S3 호환) | 첨부 파일(계획). 지금은 부르지 않는다 |
| OpenSearch | 검색 색인(계획). 지금은 부르지 않는다 |
| 메일 | 지금은 `log` 모드만 있다. SMTP 는 미정이다 |

## 7. 실행 · 검사

1. Keycloak(`MOAI-Keycloak`, 렐름은 `infra/keycloak/init-realm.sh` → `ensure-realm.py`)과 MOAI-DB(Postgres)가 떠 있어야 한다.
2. 모듈 폴더의 `.env`(git 제외)에 위 비밀값을 넣는다. Keycloak 클라이언트 비밀값의 출처는 `infra/keycloak/.env` 다.
3. `bin/startup.sh` 로 띄우고 `bin/shutdown.sh` 로 끈다(`./mvnw -q package` 로 만든 jar 를 쓴다). 로그는 `logs/moai-works-api.log` 에 쌓이고 날짜 · 50MB 마다 넘어간다([DEVELOPE.md §5.1](../../DEVELOPE.md#51-띄우고-끄기)). IntelliJ 에서 `MoaiWorksApiApplication` 을 띄워도 된다 — **작업 폴더를 이 모듈 폴더로** 둬야 `.env` 를 읽는다. 아니면 `./mvnw spring-boot:run`.
4. 개발용 계정 · 워크스페이스: `python3 -I infra/dev-seed/seed_workspaces.py` (moaiworks01~10, 워크스페이스 A · B · C — 퍼블릭 승인 가입 · 퍼블릭 자동 가입 · 프라이빗). 처음부터 다시 하려면 `reset_workspaces.py --yes` 를 돌린 뒤 시드한다.

검사: `./mvnw package` — 컴파일 + 자동 테스트 + 기동 검사. 기동 때 `AccessContractVerifier` 가 접근 규칙을 검사하고(어기면 기동 실패), `PermissionCatalogSync` 가 권한 카탈로그를 맞춘다.

### 7.1 자동 테스트 (`src/test`)

애플리케이션 전체를 띄우고 HTTP 요청(MockMvc)으로 시험한다. 보안 필터 체인 · 접근 어노테이션 판정 · 서비스 · DB 까지 진짜 코드가 돌고, 가짜는 Keycloak 관리 API 하나다.

| 무엇 | 어떻게 |
|---|---|
| 켜기 | 테스트 클래스에 `@ApiTest`(`support/`). `test` 프로파일 + 아래 공통 설정을 불러온다 |
| DB | 개발 DB `moai_works` 안의 **시험 전용 스키마 `moai_works_test`**(`MOAI_WORKS_TEST_DB_URL` 로 바꿀 수 있다). 실행할 때마다 비우고 Flyway 로 다시 만든다. 스키마 이름이 다르면 시작하지 않는다(개발 데이터가 있는 `public` 을 지우는 사고 방지). 계정 · 비밀번호는 모듈 폴더 `.env` 의 `MOAI_WORKS_DB_*` 를 그대로 쓴다. DB 계정에 데이터베이스를 만들 권한이 없어서 스키마로 나눴다 |
| 객체 저장소 | `MemoryObjectStore`(메모리). MinIO 를 부르지 않는다 |
| Keycloak 관리 API | `FakeKeycloak`(메모리). 진짜 Keycloak 을 부르지 않는다. 조직 멤버 · 그룹 상태를 테스트가 볼 수 있어 "정본과 DB 사본이 같은가"를 확인한다 |
| 토큰 | `TestTokens` 가 실행할 때마다 만드는 시험용 키로 서명한다. 운영과 같은 검사(`SecurityConfiguration.tokenValidator`: 만료 · 발급자 · audience)를 지난다 |
| 준비 | `TestApi` — 사람은 로그인(`POST /me/login`), 멤버는 가입 신청 → 승인처럼 **진짜 API 로** 만든다. 테스트끼리 겹치지 않게 사람 · 워크스페이스를 매번 새로 만든다(스프링 문맥과 DB 초기화는 한 번의 실행에 한 번) |
| 한 클래스만 | `./mvnw test -Dtest=WorkspaceAccessTest` |

지금 66건(2026-11-21):

| 클래스 | 건수 | 지키는 것 |
|---|---|---|
| `TokenAuthenticationTest` | 8 | 토큰 없음 · 만료 · audience 없음 · 발급자 다름 · 다른 키 서명 → 401, 유효한 토큰 통과, 가입 정책은 토큰 없이, 서비스 계정은 사람용 API 를 못 씀 |
| `WorkspaceAccessTest` | 12 | 비멤버는 모든 경로 404 · 없는 것과 남의 것을 구별할 수 없음 · 시스템 관리자도 멤버가 아니면 404 · 남의 개인 워크스페이스 404 · 멤버는 관리 경로 403 · 내 권한 = 역할 권한의 합 · 역할 변경과 내보내기가 다음 요청부터 반영 · 플랫폼 역할 없으면 관리자 API 403 · 정지 계정은 토큰이 살아 있어도 403 · 자기 계정 정지 불가 |
| `MembershipRulesTest` | 12 | 권한 상승 방지 · 소유자 보호 · 마지막 소유자 · 나가기와 내보내기 · 개인 워크스페이스 · Keycloak 그룹과 DB 사본 일치 |
| `RoleRulesTest` | 8 | 사용자 정의 역할 만들기 · 소유자 전용 · 없는 코드 거절 · 가진 권한 안에서만 · 기본 역할 보호 · 쓰는 역할 · 기본 역할로 지정된 역할 삭제 불가 · 없는 역할 404 |
| `InvitationTest` | 9 | 링크로 수락 · 이메일 불일치 거절 · 초대 권한 · 줄 수 없는 역할 · 대기 초대 하나 · 이미 멤버 · 취소한 초대 · 거절 · 다른 워크스페이스의 초대 · 없는 링크 404 |
| `JoinRequestTest` | 9 | 신청 → 승인 · 기본 역할 · 대기 신청 하나와 철회 · 거절 뒤 대기 기간 · 줄 수 없는 역할로 승인 불가 · 다른 워크스페이스 · 프라이빗 · 이메일 도메인 · 자동 가입 |
| `TeamWorkspaceRuleTest` | 4 | 가입한 계정은 못 만듦 · 허용한 수까지 · 한도는 시스템 관리자만 · 퍼블릭은 찾기에 보이고 가입 방식대로 |
| `PermissionCatalogTest` | 4 | Keycloak 설치 파일과 enum 이 같음 · 소유자 전용 코드 · 기본 역할 정의 · 카탈로그가 바뀌면 기존 역할을 Keycloak 먼저 맞춤 |

**새 기능을 만들면 같은 작업에서 테스트를 더한다.** 권한이 걸린 엔드포인트는 적어도 비멤버(또는 독자 아님) 404 · 권한 없음 403 · 허용 200 을 넣는다. 테스트 방침은 [테스트 전략](../plan/02-test-strategy.md).

## 8. 아직 안 한 것

- 업무 기능 전부: 프로젝트 · 하위 프로젝트 · 프로젝트 역할(`@ProjectApi`), 일감 · 이력 · 워크플로(`@IssueApi`), 질의 · 버전 · 시간 기록 · 위키 · 알림 · 검색 · 첨부 · 웹훅 · Redmine 이관. 순서는 [로드맵](../plan/01-roadmap.md)과 [WBS](../wbs/00-overview.md).
- MCP 의 실제 도구와 워크스페이스 MCP 엔드포인트 `/mcp/{워크스페이스}/{엔드포인트}`, Agent 계정, 엔드포인트별 키 · 한도([MCP · AI Agent 설계](../design/07-mcp-agents.md)). 지금 입구에는 골격 도구만 있다.
- MCP 키 발급에 역할 권한 검사와 감사(`McpKeyService` 의 골격 주석). 지금은 `@AuthenticatedApi` 다.
- 비동기 일을 위한 DB 작업 큐(아웃박스)와 스케줄러.
- Keycloak 대사(`keycloak_operation` FAILED 재시도 포함), SMTP.
- 가입 시도 제한(`SignupRateLimiter`)이 인스턴스 메모리에 있다. 여러 대로 늘리면 Redis 로 옮긴다.
- Keycloak 조직 1만 · 10만 개 부하 시험, api 서비스 계정 최소 권한 실측.

## 9. 바꿀 때 함께 고칠 문서

| 바꾼 것 | 함께 고칠 곳 |
|---|---|
| REST 경로 추가 · 변경 | 이 쪽 §6.1 표 · 설계의 입구 표([워크스페이스·계정·권한](../design/03-workspace-identity.md) · [바깥 연동 설계](../design/09-integrations.md)) · web-frontend `shared/api/endpoints.ts` · `types.ts` |
| 접근 어노테이션 · 판정 규칙 · 토큰 검증 · MCP 키 | [SECURITY.md](../../SECURITY.md) · 이 쪽 §4 |
| 권한 코드 · 기본 역할 | `Permission`(정본) · `BuiltinRole` · `infra/keycloak/permission-catalog.json` → `python3 -I infra/keycloak/ensure-realm.py` 로 Keycloak 클라이언트 역할을 만든 뒤 api 를 띄운다. 기동 때 `PermissionCatalogSync` 가 DB `permission` 표를 맞추고, Keycloak 에 코드가 다 있으면 `PermissionCatalogMigrator` 가 **이미 있는 워크스페이스의 역할**(기본 역할 = 새 정의, 사용자 정의 역할 = 없어진 코드 빼기)을 Keycloak 먼저 · 사본 나중으로 맞춘다(카탈로그 지문이 바뀐 때에만). 코드 이름을 바꾸면 Flyway 로 `role_permission` 사본을 먼저 옮긴다. `PermissionCatalogTest` 가 설치 파일과 enum 이 같은지 본다 · [워크스페이스·계정·권한](../design/03-workspace-identity.md) · [프로젝트 권한](../design/04-project-permissions.md) · web-frontend 리소스의 `permissions.*` |
| 오류 `code` 추가 | 화면이 그 코드로 분기하면 web-frontend 와 [WEB-DESIGN.md](../../WEB-DESIGN.md) |
| DB 스키마 | 새 Flyway 파일(기존 파일은 고치지 않는다) · 이 쪽 §3 의 파일 목록 · 그 테이블의 설계([데이터 모델](../design/02-data-model.md)과 각 설계 쪽의 DDL 초안) |
| MCP 입구 · 도구 · `x-moai-works-` 헤더 | 이 쪽 §6.2 · [SECURITY.md](../../SECURITY.md) §5.2 · §8 · [MCP · AI Agent 설계](../design/07-mcp-agents.md) |
| 새 env 키 | 이 쪽 §2 · 모듈 README 표 · 여러 모듈이 쓰면 [DEVELOPE.md](../../DEVELOPE.md) |
| 버전 올림 | 이 쪽 §2 · [00-overview](00-overview.md) §1 |

---

**관련 문서:** [모듈 한눈에](00-overview.md) · [SECURITY.md](../../SECURITY.md) · [워크스페이스·계정·권한](../design/03-workspace-identity.md) · [프로젝트 권한](../design/04-project-permissions.md) · [MCP · AI Agent](../design/07-mcp-agents.md) · [데이터 모델](../design/02-data-model.md) · [DEVELOPE.md](../../DEVELOPE.md)

[← 이전: 모듈 한눈에](00-overview.md) | [다음: moai-works-web-backend →](02-web-backend.md)
