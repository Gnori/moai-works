[홈](../README.md) › [모듈](00-overview.md) › moai-works-api

# moai-works-api

moai-works 의 API 서버. 계정 · 가입 · 워크스페이스 · 역할 · 멤버십 · 초대 · 감사, **모든 권한 판정**, 그리고 **MCP 전부**를 맡는다. DB 는 이 모듈만 쓴다. 프로젝트 · 프로젝트 역할 · 일감 · 워크스페이스 MCP 엔드포인트도 모두 여기에 들어온다(계획) — moai-wiki api 의 `wiki/` · `page/` 자리에 같은 모양으로.

## 1. 하는 일 · 하지 않는 일

| 한다 | 하지 않는다 |
|---|---|
| Keycloak 토큰 확인, **모든 권한 판정**(엔드포인트마다 접근 어노테이션 하나) | 브라우저 세션 ([web-backend](02-web-backend.md) 몫) |
| 계정 사본 · 가입 · 이메일 인증 코드 · 시스템 관리자 API | LLM 호출 — 요약 · 분류 같은 AI 기능은 바깥 Agent 몫이다. MCP 서버 자체도 LLM 을 부르지 않는다 |
| 워크스페이스 · 역할 · 멤버십 · 가입 신청 · 초대 · 감사 | 본문 그리기 (화면 몫 — api 는 일감 설명 · 댓글의 Markdown 을 그대로 준다, 계획) |
| Keycloak 관리 API 로 계정 · 조직 · 그룹 쓰기 (Keycloak 이 정본) | 워커 모듈에 일 맡기기 — 나중에 비동기 일이 생기면 api 안의 작업 큐 · 스케줄러로 한다 |
| MCP 서버: 바깥 AI Agent 용 일감 `/mcp/issue` · 프로젝트 관리 `/mcp/project` | |
| 프로젝트 · 프로젝트 역할 · 일감(유형 · 상태 · 관리자 · 담당자 · 날짜 · 상위 · 하위 · 연결 · 이력 · 댓글 · 목록) · 워크스페이스 MCP 엔드포인트와 도구 (계획) | |

## 2. 한눈에

| 항목 | 값 |
|---|---|
| 스택 | Spring Boot 4.2.0 · Spring Security 7.2.0 (OAuth2 리소스 서버) · Spring AI 2.1.0 (MCP Java SDK 2.2.0, `spring-ai-starter-mcp-server-webmvc`) · Jackson 3 · Spring Data JPA · Flyway · PostgreSQL · AWS SDK for Java 2.59.7(S3 — HTTP 는 JDK 의 것만 쓴다) · Java 21 · Maven (`mvnw`) |
| 패키지 | `com.moai.works.api` |
| 포트 | 11001 (`MOAI_WORKS_API_PORT`), **HTTPS** — `server.ssl.*` ← `MOAI_WORKS_TLS_ENABLED`(기본 true) · `MOAI_WORKS_TLS_CERT` · `MOAI_WORKS_TLS_KEY`. 로컬 인증서는 `infra/tls/make-certs.sh`([DEVELOPE.md §5.2](../../DEVELOPE.md#52-포트--https)). `localhost` 에는 HSTS 를 보내지 않는다(`security/HstsRule`) |
| REST | `/moai-works-api/**`. Keycloak JWT 를 **JWKS 로 직접 검증**한다(서명 · 만료 · 발급자 · audience `moai-works-api`). 요청마다 Keycloak 에 묻지 않는다 |
| MCP | 일감 `/mcp/issue`(`moai-works.mcp.issue-endpoint`) · 프로젝트 관리 `/mcp/project`(`moai-works.mcp.project-endpoint`): Streamable HTTP, MCP 키 하나로 두 입구 모두(2026-11-23 — [ADR-028](../design/00-key-decisions.md#adr-028)). 키는 사용자의 것이라 키 하나로 MCP 권한이 있는 모든 워크스페이스에서 일하고, 워크스페이스는 도구 호출마다 고른다(`workspace` 인자 — 2026-11-23 [ADR-029](../design/00-key-decisions.md#adr-029)). 워크스페이스 MCP 엔드포인트는 만들지 않는다([MCP 설계](../design/06-mcp.md)) |
| Keycloak 클라이언트 | `moai-works-api`: 토큰 audience, 서비스 계정으로 관리 API(`realm-management` 역할 + `kc-admin-api` scope). 권한 코드 16개가 이 클라이언트의 클라이언트 역할이다([2.1](../wbs/03-stage-2-project.md#w-2-1) 에서 25개로) |
| 저장소 | PostgreSQL `moai_works` (스키마는 Flyway `db/migration` 이 정본, `ddl-auto: validate`, 시간대 UTC) · 객체 저장소(S3 호환, 로컬은 MOAI-MinIO) 버킷 `moai-works-attachments`(버전 보관) — 사용자 `moai-works-api`. 일감 첨부가 쓴다(올리기 · 받기 · 지운 첨부 정리) |
| 비밀값 (`.env`, git 제외) | `MOAI_WORKS_DB_URL` · `MOAI_WORKS_DB_USERNAME` · `MOAI_WORKS_DB_PASSWORD` · `MOAI_WORKS_KEYCLOAK_URL` · `MOAI_WORKS_KEYCLOAK_REALM` · `MOAI_WORKS_API_CLIENT_ID` · `MOAI_WORKS_API_CLIENT_SECRET` · `MOAI_WORKS_CODE_SECRET`(인증 코드 HMAC, 32자 이상 — 없으면 기동하지 않는다) · `MOAI_WORKS_API_S3_ACCESS_KEY` · `MOAI_WORKS_API_S3_SECRET_KEY`(객체 저장소 — `infra/minio/init-minio.sh` 가 넣어 준다. 비어 있어도 뜨고, 저장소를 처음 쓸 때 503 `STORAGE_NOT_CONFIGURED`) · `MOAI_WORKS_SMTP_USERNAME` · `MOAI_WORKS_SMTP_PASSWORD`(메일 smtp 모드를 켤 때만 — 2026-11-23 아직 아무 데서도 켜지 않았다) |
| 그 밖의 설정 | `MOAI_WORKS_WEB_BASE_URL`(초대 링크의 웹 주소, 기본 https://localhost:11002) · `MOAI_WORKS_MAIL_MODE`(기본 `log`: 메일을 보내지 않고 로그에 남김, `smtp`: 아래 서버로 보냄 — 서버가 비면 기동하지 않는다) · `MOAI_WORKS_MAIL_FROM` · `MOAI_WORKS_SMTP_HOST` · `MOAI_WORKS_SMTP_PORT`(기본 587) · `MOAI_WORKS_SMTP_TLS`(`starttls` 기본 · `ssl`, 평문 없음) · `MOAI_WORKS_MAIL_OUTBOX_CRON`(알림 메일 보낼 통을 살피는 주기, 기본 30초마다) · `MOAI_WORKS_S3_ENDPOINT`(기본 http://localhost:9000) · `MOAI_WORKS_S3_ATTACHMENT_BUCKET`(기본 `moai-works-attachments`) · `MOAI_WORKS_MAX_FILE_MB`(올리는 파일 하나의 상한, 기본 200 — 넘으면 413 `FILE_TOO_LARGE`) · `MOAI_WORKS_ATTACHMENT_PURGE_DAYS`(지운 첨부의 객체를 두는 날 수, 기본 30 — 0 이면 정리하지 않음) · `MOAI_WORKS_ATTACHMENT_PURGE_CRON`(정리 작업 때, 기본 `0 30 3 * * *` Asia/Seoul) · MCP 키 기간 `moai-works.mcp.key-default-days`(90) · `key-max-days`(365) · 로그 `MOAI_WORKS_LOG_FILE` · `MOAI_WORKS_LOG_CONSOLE` · `MOAI_WORKS_LOG_MAX_FILE_SIZE` · `MOAI_WORKS_LOG_MAX_HISTORY` · `MOAI_WORKS_LOG_TOTAL_SIZE_CAP`(`bin/startup.sh` 가 정한다 — [DEVELOPE.md §5.1](../../DEVELOPE.md#51-띄우고-끄기)) |
| 참고한 모듈 | moai-wiki `moai-wiki-api` 의 토대(그대로 옮김). 그 원본은 SeedSiteKeeper `seed-site-keeper-api` 의 좋은 점(MCP 키, 기본 거부)만 옮겼고, 권한 판정을 BFF 에 맡긴 구조는 따르지 않았다 |

## 3. 구조

```
com.moai.works.api
  account/    계정 사본(UserProfile) · 가입(SignupService) · 메일 코드(EmailVerification · VerificationCodes — 가입 인증 · 비밀번호 재설정이 같이 씀)
              · /v1/me(MeController · MeService) · 비밀번호 변경(PasswordService · 틀린 횟수 PasswordAttemptLimiter) · 비밀번호 찾기
              (PasswordResetController · PasswordResetService · 뒤에서 하는 일 BackgroundTasks) · 정책 오류 변환(PasswordPolicyError) · 비밀번호 메일(PasswordMails)
              · 공개 경로 시도 제한(SignupRateLimiter) · 메일(Mailer — log(기본) · smtp 모드) · 플랫폼 역할 이름(PlatformRole)
  platform/   가입 정책(platform_setting · SignupPolicy) · 시스템 관리자 API(/v1/admin — AdminController · AccountAdminService ·
              워크스페이스 사용 중지 · 다시 사용 · 삭제 WorkspaceAdminController · WorkspaceAdminService)
  workspace/  워크스페이스 · 역할 · 멤버십 · 가입 신청 · 초대 · 권한 카탈로그(Permission · BuiltinRole · PermissionCatalogController)
              · 접근 판정(WorkspaceAccessService) · 워크스페이스 만들기 사가(WorkspaceProvisioner) · 주소(WorkspaceSlugs)
              · 카탈로그 동기화(PermissionCatalogSync) · 카탈로그가 바뀌면 기존 역할을 맞춤(PermissionCatalogMigrator)
  keycloak/   관리 API 클라이언트(KeycloakAdminClient) · 쓰기 기록(KeycloakOperationLog, 멱등)
  audit/      감사 기록 (추가 전용, DB 트리거가 수정 · 삭제를 막는다)
  security/   JWT · MCP 키 필터 체인(SecurityConfiguration · McpKeyAuthenticationFilter) · KeycloakPrincipal
              · HstsRule(localhost 에는 HSTS 를 보내지 않음) · access/(접근 어노테이션 · 인터셉터 · 기동 검사)
  common/     ApiException → problem+json(code 포함, with() 로 화면이 쓸 값을 더한다) · Actor · Ids(UUID 버전 7)
  mcpkey/     MCP 키 발급 · 목록 · 만료시키기 · 검사(McpKeyService · McpKeyController · McpKeyTokenCodec) — 키는 사용자의 것(V13)
  mcp/        MCP 서버(McpServerConfiguration — 고정 입구 둘 /mcp/issue · /mcp/project) · 범위 판정(McpScope) · 도구(ProjectMcpTools —
              list_workspaces · list_projects, IssueMcpTools, TemplateMcpTools) · 호출 문맥(McpCallContext)
  mcplog/     MCP 접근 기록(2026-11-23, V19) — 요청 하나 = 한 줄. 재료(McpRequestInfo) · 필터(McpAccessLogFilter — 키 필터 앞, 거절 · 그 밖 신호를
              응답 뒤에) · 기록기(McpAccessRecorder — 도구 호출 · 도구 목록 · 연결 시작을 결과보다 먼저, 못 쓰면 요청 실패) · 세션 표(McpSessionRegistry)
              · 보기 · 거르기 · CSV(McpAccessLogService · McpAccessLogController) · 작업 종류(McpOperation) · 다듬기(McpSecrets — 본문 · 키 모양을 남기지 않음)
  notification/ 알림함 · 받기 설정(NotificationService · NotificationController) · 아침 알림(NotificationReminders)
              · 알림 메일 — 보낼 통을 살펴 다시 판정 · 보내기 · 다시 보내기(NotificationMailSender) · 메일 글(NotificationMailContent)
  storage/    객체 저장소(ObjectStore · S3ObjectStore — AWS SDK, JDK HTTP 클라이언트). 일감 첨부가 쓴다
  controller/ WhoAmI(/moai-works-api/me — 토큰 주인 확인용)
  config/     설정(MoaiWorksProperties) · 경로 상수(EndPointPaths) · MCP 입구(McpEndpoint — ISSUE · PROJECT, 호출이 들어온 입구)
resources/db/migration/
  V1__foundation.sql   계정(user_profile) · 가입 정책(platform_setting) · 인증 코드(email_verification) · workspace · permission
                       · workspace_role · role_permission · workspace_member · member_role · join_request · invitation
                       · keycloak_operation · audit_log(+ 추가 전용 트리거) · mcp_api_key.
                       moai-wiki 토대 스키마(V1~V3 · V8 · V9 의 최종 모양)를 한 파일로 옮겼다
  V2 ~ V12             프로젝트 · 일감 · 리비전 · 작업 시간 · MCP 키 묶음(V5) · 알림 · 유형 · 첨부 · 첨부 티켓 · 키를 입구에서 뗌(V11) · 일감 템플릿
  V13__mcp_key_user.sql  MCP 키를 워크스페이스에서 뗀다 — mcp_api_key.workspace_id 를 지우고 보호 함수를 그 열 없이 다시 만든다(ADR-029)
  V16__workspace_suspend.sql  workspace 에 suspended_at · suspended_reason · deleted_at — 시스템 관리자의 사용 중지 · 삭제(WBS 1.8)
  V17__issue_attachment_purge.sql  지운 첨부의 정리 표시 — issue_attachment.purged_at · 정리 차례 인덱스, 보호 함수가 지운 행의 정리 표시 한 번을 허락
  V18__notification_mail.sql  알림 메일 — notification_preference.email(종류별 메일 켬 · 끔, NULL = 기본값) · 보낼 통 notification_mail(FR-NOTI-006)
  V19__mcp_access_log.sql  MCP 접근 기록 mcp_access_log(추가 전용 — 고치기 · 지우기 · 비우기 트리거). V14 ~ V18 은 다른 작업의 것
  V20 · V21            일감의 에이전트 세션 · 프로젝트 모듈과 일감의 관련 모듈
java/db/migration/
  V23__workspace_issue_number.java  일감은 워크스페이스 아래(ADR-030) — issue.workspace_id · workspace.issue_seq · UNIQUE (workspace_id, number) ·
                       번호를 워크스페이스마다 만든 때 순서로 다시 매김 · 가리키는 번호 · 글 속 #번호 바꾸기 · 기록 표 issue_number_change ·
                       issue_number_text_change · 시스템 이력. Java 마이그레이션(Flyway 가 같은 위치 classpath:db/migration 에서 찾는다)

src/test/java/com/moai/works/api
  support/    @ApiTest · TestInfrastructure(시험 스키마 · 가짜 Keycloak · 시험용 토큰 · 메모리 객체 저장소 · 메일 받아 두기) · TestApi(요청 · 준비)
              · FakeKeycloak · TestTokens · TestUser · MemoryObjectStore · RecordingMailer(로그 모드 그대로 + 보낸 알림 메일 · 일부러 실패)
  security/ · workspace/   통합 테스트 (§7.1)
src/test/resources/application-test.yaml
```

프로젝트 · 일감 패키지와 그 Flyway 파일은 그 기능을 만들 때 더한다(계획). moai-wiki 의 `wiki/` · `page/` 와 같은 자리 · 같은 모양이다. 새 Flyway 파일은 `V2__` 부터 붙인다([ADR-015](../design/00-key-decisions.md#adr-015)). 테이블 초안은 [데이터 모델](../design/02-data-model.md) · [프로젝트 설계](../design/04-project.md) · [일감 설계](../design/05-issue.md) · [MCP 설계](../design/06-mcp.md).

```
com.moai.works.api            (계획)
  project/    프로젝트(워크스페이스 안의 일감 묶음 = 권한 경계) — 만들기 · 설정 · 보관 · 복원 · 삭제(ProjectService)
              · 프로젝트 접근 판정(ProjectAccessService: 독자 · 프로젝트 권한) · ProjectContext · @ProjectApi
              · 프로젝트 역할(ProjectRole 열거형 · project_role_binding · Keycloak 그룹 /projects/<id>/<role>)
  issue/      일감 — 유형(issue_type 공통 시드) · 일감(번호 = 워크스페이스 순번 workspace.issue_seq — ADR-030) · IssueLocator(번호 → 그 일감의
              지금 프로젝트 문맥, @IssueApi · MCP · 첨부 티켓이 같이 씀) · IssueController(정식 주소 /workspaces/{ws}/issues/{number}) ·
              ProjectIssueController(프로젝트의 목록 · 만들기) · WorkspaceIssueController(워크스페이스 목록 · 거르기 칸 · 옛 링크 풀기) ·
              IssueService(만들기 · 고치기 · 옮기기 · 지우기 ·
              상태와 날짜 자동 채움 · 관리자 · 담당자) · 상위 · 하위 · 연결(issue_link) · 이력과 댓글(issue_journal) · 목록 질의
              · 첨부(AttachmentService · 티켓 AttachmentTicketService · 미리보기 그림 AttachmentPreviews · 지운 첨부 정리 AttachmentPurger)
  mcp/        MCP 서버 · 도구(입구별 고정 서버 둘 /mcp/issue · /mcp/project) · McpScope(키로 쓸 수 있는 워크스페이스 · workspace 인자 · 프로젝트 찾기)
```

## 4. 권한 판정 요약

엔드포인트마다 접근 어노테이션이 정확히 하나 붙는다. 없으면 기동하지 않는다(기본 거부).

| 어노테이션 | 누가 | 실패 |
|---|---|---|
| `@PublicApi` | 누구나. `/v1/signup/**` · `/v1/password-reset/**` 안에서만 쓸 수 있다 | — |
| `@AuthenticatedApi` | 유효한 토큰(사람 · 서비스) | 401 |
| `@UserApi` | 활성 사람 계정 (처음 보는 Keycloak 계정이면 사본을 만든다). 비밀번호 변경 · 찾기로 끊긴 로그인의 토큰은 거절(2026-11-24) | 403 · 401 `SESSION_REVOKED` |
| `@ServiceApi(clients=…)` | 지정한 클라이언트의 서비스 계정. 지금 쓰는 엔드포인트는 없다 | 403 |
| `@PlatformApi(role=…)` | 렐름 역할(system-admin 등)을 가진 사람 | 403 |
| `@WorkspaceApi(권한)` | 경로 `{workspace}` 의 활성 멤버이면서 그 권한이 있는 사람 | 비멤버 **404**(숨김) · 권한 없음 **403** |
| `@ProjectApi(권한)` | 경로 `{workspace}` · `{project}` — 그 프로젝트의 독자이면서 그 권한(워크스페이스 역할 ∪ 프로젝트 역할)이 있는 사람. 프로젝트의 일감 목록 · 만들기 | 독자가 아님 · 없는 프로젝트 · 관리 권한 없이 보관된 프로젝트 **404** `PROJECT_NOT_FOUND`(숨김) · 권한 없음 **403** `PERMISSION_REQUIRED` |
| `@IssueApi(권한)` (2026-11-24) | 경로 `{workspace}` · `{number}` — 일감 하나의 정식 주소. `IssueLocator` 가 워크스페이스 번호로 일감을 찾아 그 일감의 지금 프로젝트로 `@ProjectApi` 와 같은 판정 | 일감이 없음 · 지움 · 독자가 아님 · 관리 권한 없이 보관 **404** `ISSUE_NOT_FOUND`(숨김) · 권한 없음 **403** `PERMISSION_REQUIRED` |

- `AccessContractVerifier` 가 기동 때 모든 REST 핸들러를 검사한다. 어노테이션이 0개이거나 2개 이상인 경우, `@PublicApi` 가 공개 경로 밖에 있는 경우, `@WorkspaceApi` 경로에 `{workspace}` 가 없는 경우 **기동하지 않는다**. `@ProjectApi` 경로에 `{workspace}` · `{project}`, `@IssueApi` 경로에 `{workspace}` · `{number}` 가 없을 때도 기동하지 않는다.
- 판정은 `AccessInterceptor` 한 곳에서 한다. 결과(`CurrentUser` · `WorkspaceContext`)는 컨트롤러가 인자로 받는다.
- 권한은 멤버 역할들의 권한 코드를 합한 것이다(`WorkspaceAccessService`). 권한 상승은 `MembershipService` · `RoleService` 가 막는다: 가진 권한 안에서만 역할을 줄 수 있고, 소유자 역할은 소유자만 다루며, 마지막 소유자는 보호한다.
- 지금 코드의 기본 역할은 `owner` · `admin` · `member` 3개다(`BuiltinRole`). moai-wiki 와 같은 4개(`curator` 큐레이터 — 모든 프로젝트에서 일감을 만들고 고친다)로 되돌리는 일을 [2.1](../wbs/03-stage-2-project.md#w-2-1) 에서 하고 있다. 프로젝트 안에서는 프로젝트 역할 4개(`project-admin` ⊃ `project-curator` ⊃ `project-editor` ⊃ `project-reader`, 고정)가 권한을 더한다(계획 — [프로젝트 설계](../design/04-project.md)).
- 토큰 검증, MCP 키, 계정 상태, 권한 상승 방지, 감사의 전체 구조는 [SECURITY.md](../../SECURITY.md)에 있다.

## 5. Keycloak 이 정본: 쓰는 순서

계정 · 멤버십 · 역할은 Keycloak 이 정본이다. 조직 = 워크스페이스, 조직 그룹 `/roles/<key>` = 역할, 클라이언트 역할 = 권한 코드. 프로젝트 역할은 조직 그룹 `/projects/<프로젝트 id>/<역할 키>` 로 둔다(계획). 프로젝트 역할이 고정이라 그 그룹에는 클라이언트 역할을 매핑하지 않고, 코드는 api 의 `ProjectRole` 이 갖는다.

1. api 가 **Keycloak 에 먼저 쓴다**(`KeycloakAdminClient`, 서비스 계정 토큰은 캐시해서 쓴다).
2. 단계마다 `keycloak_operation` 에 남긴다(`KeycloakOperationLog`, 멱등 키, 별도 트랜잭션). 같은 키가 이미 끝났으면 Keycloak 을 다시 부르지 않는다.
3. DB 사본을 고친다.

워크스페이스 만들기는 `WorkspaceProvisioner` 사가다. 중간에 끊겨도 다음 요청이 이어서 끝낸다(`PROVISIONING` 상태). 권한 판정은 Keycloak 이 아니라 DB 사본으로 한다. 토큰에는 조직 별칭만 있고, 역할 변경이 다음 요청부터 바로 반영돼야 하기 때문이다([워크스페이스·계정·권한 설계](../design/03-workspace-identity.md)).

## 6. 입구와 바깥 연결

### 6.1 REST (`/moai-works-api/v1`)

| 묶음 | 경로 (접근 어노테이션 · 권한) |
|---|---|
| 가입 (공개) | `GET /signup/policy` · `POST /signup` · `POST /signup/verify` · `POST /signup/resend` (`@PublicApi`, 속도 제한) |
| 비밀번호 찾기 (공개, 2026-11-24) | `POST /password-reset/request` `{email}` → 늘 202(계정이 있든 없든 같은 응답 · 같은 때 — 계정 찾기 · 코드 · 메일은 뒤에서, IP 시간당 10 · 이메일 시간당 5 넘으면 429) · `POST /password-reset/confirm` `{email, code, newPassword}` → 204(IP 시간당 30, 틀림 · 만료 · 다 씀은 모두 400 `INVALID_CODE`, 정책 400 `PASSWORD_POLICY`, 성공하면 그 계정의 로그인을 모두 끊음 · 감사 `account.password.reset`) (`@PublicApi` — [설계 §8.3](../design/03-workspace-identity.md#ws-password)) |
| 나 | `GET /me` · `POST /me/login` · `PATCH /me` · `GET /me/workspaces` · `PUT /me/last-workspace` · `GET /me/join-requests` · `DELETE /me/join-requests/{id}` (`@UserApi`) · **`POST /me/password`** `{currentPassword, newPassword, signOutOtherSessions}`(2026-11-24 — `@UserApi(systemAdmin = true)`, 현재 비밀번호를 Keycloak 패스워드 그랜트로 확인 · 틀리면 400 `CURRENT_PASSWORD_INVALID` + `attemptsLeft` · 사람마다 15분에 5번 넘으면 429 · 같으면 400 `PASSWORD_UNCHANGED` · 정책 400 `PASSWORD_POLICY`(`reason` · `value`) · Keycloak 잠금 423 `ACCOUNT_LOCKED` → `{otherSessionsEnded, currentSessionKept}`, 감사 `account.password.change` · 메일) |
| 워크스페이스 | `GET /workspaces?query=`(찾기) · `POST /workspaces`(주소는 서버가 `workspace-고유id` 로 정한다. 시스템 관리자가 허용한 계정만, 허용한 수까지 — 403 `TEAM_WORKSPACE_NOT_ALLOWED` · `TEAM_WORKSPACE_LIMIT`) (`@UserApi`) · `GET /workspaces/{workspace}`(`workspace.read`) · `PATCH /workspaces/{workspace}`(`workspace.settings.manage`) · `POST …/leave`(`workspace.read`) · `GET …/audit?limit=`(`audit.read`) |
| 멤버 | `GET …/members`(`member.read`) · `PUT …/members/{userId}/roles`(`member.role.assign`) · `DELETE …/members/{userId}`(`member.remove`) |
| 가입 신청 | `POST …/join-requests`(`@UserApi` — 멤버가 아닌 사람이 신청) · `GET …/join-requests` · `POST …/join-requests/{id}/approve·reject`(`member.request.review`) |
| 역할 | `GET …/roles`(`workspace.read`) · `POST …/roles` · `PATCH·DELETE …/roles/{key}`(`role.manage`): 사용자 정의 역할(`custom-고유id`). 기본 역할(지금 3개, [2.1](../wbs/03-stage-2-project.md#w-2-1) 에서 4개)은 바꿀 수 없다 |
| 초대 | 보내는 쪽: `POST·GET …/invitations` · `DELETE …/invitations/{id}`(`member.invite`). 받는 쪽(`@UserApi`): `GET /invitations/{token}` · `POST /invitations/{token}/accept·decline` · `GET /me/invitations` · `POST /me/invitations/{id}/accept·decline` |
| 카탈로그 | `GET /permission-catalog` (`@UserApi`) |
| 일감 템플릿 (2026-11-23) | 워크스페이스 기본 `GET /workspaces/{workspace}/issue-templates`(`workspace.read`) · `PUT·DELETE …/issue-templates/{type}`(`workspace.settings.manage`), 프로젝트 `GET …/projects/{project}/issue-templates[/{type}]`(`@ProjectApi(issue.read)`) · `PUT·DELETE …/issue-templates/{type}`(`@ProjectApi(project.manage)`) — [일감 설계 §10-2](../design/05-issue.md#is-templates) |
| 프로젝트 모듈 (2026-11-23) | `GET …/projects/{project}/modules`(`@ProjectApi(issue.read)` — 쓰지 않는 것 포함 · 붙은 일감 수) · `POST …/modules` · `PATCH …/modules/{key}` · `PUT …/modules/order`(`@ProjectApi(project.manage)`) — 지우기 없음(쓰지 않음), 감사 `project.module.*` — [프로젝트 설계 §6-1](../design/04-project.md#pj-modules) |
| 일감의 에이전트 세션 · 관련 모듈 (2026-11-23) | 만들기 · 고치기의 `agentSession` · `modules`, 목록 `?agentSession=`(이름 · `none`) · `?module=`(여러 번), 쓰인 세션 이름 `GET …/projects/{project}/agent-sessions`(`@ProjectApi(issue.read)`) — [일감 설계 §10-4 · §10-5](../design/05-issue.md#is-agent-session) |
| 첨부 (2026-11-22) | `…/workspaces/{workspace}/issues/{number}/attachments` 아래(2026-11-24 부터 — 그 전은 프로젝트 주소), 모두 `@IssueApi`: `GET`(목록, `issue.read` — 줄마다 `preview`) · `POST`(multipart `file`, `issue.comment`) · `GET /{id}/content`(원본, `issue.read`) · `GET /{id}/preview`(2026-11-23 — 긴 변 320px PNG 미리보기, `issue.read`, 원본과 같은 머리글, png · jpeg · gif 만, 없으면 404 `ATTACHMENT_PREVIEW_NONE`) · `DELETE /{id}`(`issue.comment` — 올린 사람 또는 `project.manage`) — [일감 설계 §10-1](../design/05-issue.md#is-attachments) |
| 공수 집계 (2026-11-23) | `GET /workspaces/{workspace}/effort?from&to&project&person&type&groupBy` · `GET …/effort.csv?…&lang=`(둘 다 `workspace.read`) — 들어가는 프로젝트는 `EffortService` 가 프로젝트 판정(`ProjectAccessService` + 그 프로젝트의 `issue.read`)으로 거른다(읽을 수 없는 프로젝트를 조건으로 주면 404 `PROJECT_NOT_FOUND`). CSV 는 감사 `issue.work_log.export` — [일감 설계 — 공수 집계](../design/05-issue.md#is-effort) |
| 시스템 관리자 | `@PlatformApi(system-admin)`: `GET·PUT /admin/settings/signup` · `GET /admin/accounts?status=&limit=` · `POST /admin/accounts/{id}/approve·reject·suspend·resume` · `PUT /admin/accounts/{id}/team-workspace-limit`(그 계정이 만들 수 있는 팀 워크스페이스 수 0 ~ 100 — 가입한 계정은 0) · `GET /admin/workspaces?kind=&status=&query=&limit=`(이름 · 주소 · 종류 · 소유자 · 멤버 수 · 상태 · 만든 날 · 사용 중지 이유 — 내용 없음) · `POST /admin/workspaces/{slug}/suspend` `{reason}` · `POST …/resume` · `GET …/deletion-preview`(멤버 · 프로젝트 · 일감 · 첨부 수) · `DELETE /admin/workspaces/{slug}?confirm=<주소>`(사용 중지한 팀 워크스페이스만 — 409 `WORKSPACE_NOT_SUSPENDED` · 400 `CONFIRM_MISMATCH` · 개인 409 `PERSONAL_WORKSPACE`, [워크스페이스 설계 §9.1](../design/03-workspace-identity.md#ws-suspend)). 사용 중지한 워크스페이스의 멤버는 모든 워크스페이스 · 프로젝트 입구에서 403 `WORKSPACE_SUSPENDED`, MCP 키에게는 보이지 않는다 |
| 프로젝트 **(계획)** | `GET·POST /workspaces/{workspace}/projects`(목록 `?archived=` · 만들기 `@WorkspaceApi(project.create)`) · `GET·PATCH …/projects/{project}` · `POST …/{project}/archive·restore` · `DELETE …/{project}?confirm=`(`@ProjectApi` — `project.manage` · `project.delete`) · `GET …/{project}/roles` · `PUT …/{project}/roles/{userId}`(`project.manage`). 경로와 권한은 [프로젝트 설계](../design/04-project.md) |
| 일감 | **프로젝트의 일감** `…/projects/{project}` 아래 `@ProjectApi`: `GET /issue-types` · `GET·POST /issues`(목록 · 만들기). **일감 하나**(2026-11-24 — 정식 주소, [ADR-030](../design/00-key-decisions.md#adr-030)) `…/workspaces/{workspace}/issues/{number}` 아래 `@IssueApi`: `GET·PATCH·DELETE`(고치기의 `project` = 옮기기 — 옮길 프로젝트의 `issue.create` 도) · `GET /revisions[/{no}[/diff]]` · `POST·DELETE /work-logs` · `GET·POST /comments` · `PATCH·DELETE /comments/{commentId}`(고치기는 `issue.comment` + 쓴 사람만, 지우기는 `issue.read` + 쓴 사람 또는 `project.manage` — [일감 설계 §10](../design/05-issue.md#is-comments)) · `POST /links`(대상은 같은 워크스페이스 · 읽을 수 있는 일감) · `DELETE /links/{linkId}` · 첨부. **워크스페이스의 일감** `…/workspaces/{workspace}` 아래 `@WorkspaceApi(workspace.read)`: `GET /issues`(읽을 수 있는 프로젝트의 합 · `project` 거르기) · `GET /issue-types` · `GET /agent-sessions` · `GET /issue-modules` · `GET /projects/{projectKey}/issue-refs/{number}`(옛 링크 → 지금 번호). 권한 코드 `issue.read` · `issue.create` · `issue.edit` · `issue.delete` · `issue.comment`, 관리자 바꾸기는 `issue.manager.assign`. [일감 설계](../design/05-issue.md) |

v1 밖에는 `GET /moai-works-api/me`(`@AuthenticatedApi`, 토큰 내용 그대로 — 연결 확인용)가 있다. MCP 키(2026-11-22 v1 안으로, 2026-11-23 사용자 키 — [ADR-029](../design/00-key-decisions.md#adr-029)): 내 키 `POST /v1/me/mcp-keys` `{name, days}`(`@UserApi` — 워크스페이스를 고르지 않는다, MCP 권한이 있는 워크스페이스가 없으면 403 `MCP_KEY_ISSUE_NOT_ALLOWED`, 원문은 응답에 한 번) · `GET /v1/me/mcp-keys?status=`(`@UserApi`) · `POST /v1/me/mcp-keys/{id}/expire`(`@UserApi`, 주인만 — 남의 키 404, 지우기 없음), 워크스페이스 멤버들의 키 `GET /v1/workspaces/{ws}/mcp-keys?status=&owner=`(`@WorkspaceApi(mcp.key.manage)`, 보기만 — 발급한 곳 · 호출 수는 주인에게만). MCP 접근 기록(2026-11-23 — [MCP 설계 — 접근 기록](../design/06-mcp.md#mc-access-log)): `GET /v1/workspaces/{ws}/mcp-access-log` · `….csv`(`@WorkspaceApi(audit.read)` — 그 워크스페이스에서 쓴 줄만), 내 키 `GET /v1/me/mcp-keys/{id}/access-log` · `….csv`(`@UserApi`, 주인만 — 남의 키 404). 거르기 `key` · `person` · `tool` · `operation` · `outcome`(쉼표로 여럿) · `client` · `project` · `session` · `ip` · `from` · `to`, 커서 `before` · `beforeId`, `limit` 최대 200. CSV 는 최대 10만 줄이고 보내기 전에 감사 `mcp.access_log.export`. 옛 `POST /v1/workspaces/{ws}/mcp-keys` · `…/{id}/expire` 는 없앴다. 키 발급 · 만료 감사는 사용자 단위(`workspace_id` 없음)라 워크스페이스 감사 목록에 나오지 않는다. 감사 목록 `GET /v1/workspaces/{ws}/audit` 은 `person` · `action`(묶음 앞부분) · `targetType` · `from` · `to` · `before` · `beforeId`(시각 + id 커서) 로 거르고 `actorId` · `ip` 를 준다. 알림(2026-11-22 — [알림 설계](../design/07-notification.md)): `notification/` — `GET /v1/me/notifications` · `GET …/unread-count` · `POST …/{id}/read` · `POST …/read-all` · `GET …/preferences` · `PUT …/preferences/{kind}` `{enabled?, email?}`(모두 `@UserApi`), `NotificationService.notify` 는 각 서비스의 트랜잭션 안에서, `NotificationReminders` 는 매일 09:00(Asia/Seoul) 기한 · 키 만료 알림. 알림 메일(2026-11-23 — [알림 설계 §8](../design/07-notification.md#nt-mail)): 메일을 켠 종류의 알림이 새로 생기면 같은 트랜잭션에서 보낼 통(`notification_mail`)에 한 줄, `NotificationMailSender` 가 30초마다 한 줄씩 잠그고 보낼 때 다시 판정해 보낸다. 첨부 정리(2026-11-23): `issue/AttachmentPurger` 가 매일 03:30(Asia/Seoul) 지운 지 30일이 지난 첨부의 객체를 지우고 워크스페이스마다 감사 `issue.attachment.purge` 를 남긴다([일감 설계 — 첨부](../design/05-issue.md#is-attachments)).

오류는 RFC 9457 problem+json 이고 `code` 가 있다(예: `WORKSPACE_NOT_FOUND`, `PERMISSION_REQUIRED`, `ESCALATION_DENIED`, `OWNER_ONLY`, `LAST_OWNER`, `ROLE_IN_USE`, `INVITATION_CLOSED`, `REJOIN_WAIT`, `TEAM_WORKSPACE_LIMIT`, 동시 수정 `CONCURRENT_UPDATE`. 계획: `PROJECT_NOT_FOUND` · `ISSUE_NOT_FOUND`). 화면은 `code` 로 분기한다.

### 6.2 MCP

| 입구 | 쓰는 쪽 | 도구 (지금) |
|---|---|---|
| `/mcp/issue` (일감, 서버 `moai-works-issue`) | 바깥 AI Agent (Claude Code 등 MCP 클라이언트) | 20개 — 워크스페이스 목록 · 프로젝트 목록 · 일감 찾기 · 읽기 · 만들기 · 고치기 · 지우기 · 댓글(달기 · 목록 · 고치기 · 지우기) · 작업 시간 · 연결 · 첨부 · 템플릿 읽기 · 모듈 보기(`list_modules`, 2026-11-23). 만들기 · 고치기 · 찾기는 에이전트 세션 · 관련 모듈도 다룬다. [MCP 설계 §8 도구](../design/06-mcp.md#mc-tools) |
| `/mcp/project` (프로젝트 관리, 서버 `moai-works-project`) | 같은 Agent · 같은 키 | 7개 — `list_workspaces` · `list_projects` · 일감 템플릿 관리(`list_issue_templates` · `set_issue_template` · `reset_issue_template`) · 모듈(`list_modules` · `set_module`, 2026-11-23). 프로젝트 만들기 · 지우기 같은 관리 도구의 자리([MCP 설계 §8 입구](../design/06-mcp.md#mc-entries)) |
| `/mcp/files/upload` (PUT) · `/mcp/files/download` (GET) | 같은 Agent — 첨부 파일 본문 | 도구가 준 일회용 티켓으로 올리고 받는다([MCP 설계 §8.15](../design/06-mcp.md#mc-attach-ticket)) |

- **키는 입구에 묶이지 않는다**(2026-11-23, V11 — `mcp_api_key.endpoint` 열을 지움). 같은 키로 두 입구 모두 붙고, 할 수 있는 일은 도구마다 키 주인의 권한으로 판정한다. 그 밖의 `/mcp/**`(티켓 경로 빼고)는 키를 보기 전에 404 `MCP_ENDPOINT_NOT_FOUND`. 검사는 요청마다 캐시 없이 한다([SECURITY.md](../../SECURITY.md) §5.2).
- **키는 워크스페이스에도 묶이지 않는다**(2026-11-23, V13 — [ADR-029](../design/00-key-decisions.md#adr-029)). 키로 쓸 수 있는 워크스페이스 = 주인이 활성 멤버이고 역할에 `mcp.key.issue.personal` 이 있는 활성 워크스페이스. 프로젝트를 받는 모든 도구에 선택 인자 `workspace` — 비우면 그 모든 워크스페이스에서 프로젝트를 찾고 여럿이면 400 `PROJECT_AMBIGUOUS`, 주었는데 쓸 수 없으면 404 `WORKSPACE_NOT_FOUND`, 프로젝트 없이 하는 일(워크스페이스 기본 템플릿)은 여럿이면 400 `WORKSPACE_REQUIRED`. 판정은 `McpScope` 한 곳.
- 도구의 범위는 모델이 넘긴 인자가 아니라 `McpCallContext`(키 주인)로 정한다. 도구는 REST 와 같은 서비스 · 같은 판정을 부른다.
- Spring AI 의 자동 MCP 서버와 `@McpTool` 스캐너는 끄고, `McpServerConfiguration` 이 입구별로 도구를 등록한다(`@Tool` + `MethodToolCallbackProvider`).
- moai-works 가 정의하는 HTTP 헤더는 소문자 `x-moai-works-` 접두어를 쓴다. 지금 정의한 것은 없다.
- **계획** — 워크스페이스 엔드포인트는 만들지 않는다(2026-11-23 [ADR-029](../design/00-key-decisions.md#adr-029)). 사용자 계정에 묶인 키(서비스 키 없음, [ADR-024](../design/00-key-decisions.md#adr-024) — 입구 · 워크스페이스에는 묶지 않는다, [ADR-028](../design/00-key-decisions.md#adr-028) · ADR-029), 처음 설계의 읽기 도구 `list_projects` · `get_project` · `list_issue_types` · `search_issues` · `get_issue`, 쓰기 도구(`AGENT` 만) `create_issue` · `update_issue` · `add_comment` · `link_issues` · `unlink_issues` · `set_parent`, 안전장치(`rowVersion` · 멱등 키 · 속도 제한 · 응답 크기), 이력 · 감사의 via = `MCP`. 도구 표는 [MCP 설계](../design/06-mcp.md).

### 6.3 api 가 부르는 것

| 대상 | 무엇 |
|---|---|
| Keycloak | JWKS(토큰 검증), 토큰 엔드포인트(서비스 계정 client_credentials), 관리 API(사용자 · 조직 · 그룹 · 역할) |
| PostgreSQL | 정본 데이터 |
| 객체 저장소 (S3 호환) | 일감 첨부 — 올리기 · 받기 · 미리보기 그림(처음 받을 때 원본 옆에 만든다), 지운 첨부 정리(`AttachmentPurger`, 매일 — 버전 보관분까지 지운다) |
| OpenSearch | 전문 검색 색인(나중 후보). 지금은 부르지 않는다 |
| 메일 | `log` 모드(기본 — 알림 메일은 가린 받는 사람 · 종류 · 제목 한 줄만 로그에 남긴다)와 `smtp` 모드(Spring `JavaMailSenderImpl`, `moai-works.mail.smtp.*` 로 직접 만든다 — `spring.mail.*` 자동 설정 · 메일 건강 검사는 켜지 않는다). SMTP 서버는 아직 미정(WBS D-06)이라 어디서도 smtp 모드를 켜지 않았다 |

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
| 메일 | `RecordingMailer` — 로그 모드 그대로 보내고 알림 메일 · 계정 메일(인증 코드 · 비밀번호 재설정 코드 · 바뀜 알림)을 받아 둔다(시험이 코드를 읽는다) |
| 뒤에서 할 일 | `BackgroundTasks`(비밀번호 재설정 메일)는 그 자리에서 한다 |
| 한 클래스만 | `./mvnw test -Dtest=WorkspaceAccessTest` |

2026-11-24 기준 267건(비밀번호 변경 · 찾기 `PasswordTest` 22 — 맞는 · 틀린 현재 비밀번호(400, 로그인 그대로) · 정책 까닭 · 같은 비밀번호 · 5번 틀리면 429 · Keycloak 잠금 423 · 다른 기기 끊기(이 로그인만 남음 · 끊긴 토큰 401 `SESSION_REVOKED` · Keycloak 세션) · 끊지 않기 · sid 없는 토큰 · 시스템 관리자 · 서비스 계정 403 · 있는 · 없는 이메일 같은 응답 · 받을 수 없는 계정 · 코드로 다시 정하면 모두 끊김 · 감사 · 메일 · HMAC 만 · 한 번만 · 5번 · 만료 · 새 코드 · 정책 뒤 다시 · IP · 이메일 속도 제한 · 가입 코드). 2026-11-23 기준 230건(MCP 접근 기록 `McpAccessLogTest` 15 — 요청마다 한 줄 · 거절도 · 저장 실패면 호출 실패 · 고치기 · 지우기 막음 · 키 원문 없음 · 볼 사람 · 거르기 · CSV · 첨부 정리 `AttachmentPurgeTest` 3 · 미리보기 `AttachmentPreviewTest` 4 · 공수 집계 `EffortTest` 6 — 합계 = 기록의 합 · 예상 대비 실제 · 독자가 아닌 프로젝트 제외 · 틀린 조건 400 · CSV = 화면 · 감사. 아래 표는 2026-11-21 의 66건 — 그 뒤 프로젝트 · 일감 · 알림 · MCP 시험이 더해졌다. 알림 메일은 `NotificationMailTest`(5 — 켠 종류만 · 기본값 · 보낼 때 다시 판정 · 한 번만(두 스레드) · 로그 모드 한 줄 · 영어 · 다시 보내기와 실패) · `NotificationMailContentTest`(3) · `MailerTest`(4 — smtp 설정 · 가리기). 시스템 관리자의 워크스페이스 사용 중지 · 삭제는 `WorkspaceSuspendTest`(8 — 멤버 REST 403 · 비멤버 404 · 다시 사용하면 그대로 · MCP 숨김과 복귀 · 사용 중지 안 한 것 지우기 409 · 확인 문구 400 · 개인 409 · 목록에 내용 없음 · 시스템 관리자 아니면 403 · 지우기가 지우는 것과 남기는 것 · `admin.workspace.*` 감사). MCP 키 · 도구는 `McpKeyRuleTest`(18 — 사용자 키 · 한 키로 두 워크스페이스 · `PROJECT_AMBIGUOUS` · 안 보이는 워크스페이스 · 주인만 만료 · 사용자 단위 감사) · `McpKeyUserMigrationTest`(1 — V12 키가 V13 뒤에 그대로) · `IssueMcpToolsTest` · `AttachmentTicketTest` · `IssueTemplateTest`. 2026-11-23 MCP 접근 기록 `McpAccessLogTest`(15 — 요청마다 한 줄 · 거절도 · 원문 없음 · 고치기 금지 · 저장 실패면 요청 실패 · 보는 범위 · 거르기 · 커서 · CSV) 를 더해 189건):

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

- 기본 역할 넷 · 권한 코드 25개([2.1](../wbs/03-stage-2-project.md#w-2-1) 진행 중).
- 업무 기능 전부: 프로젝트 · 프로젝트 역할(`@ProjectApi`), 일감 · 유형 · 상태 · 날짜 · 상위 · 하위 · 연결 · 이력 · 댓글 · 목록. 순서는 [로드맵](../plan/01-roadmap.md)과 [WBS](../wbs/00-overview.md). 첨부 · 알림 · 검색 · Redmine 이관 등은 나중 후보다.
- MCP 한도(속도 제한) · 프로젝트 관리 입구의 관리 도구 · 접근 기록의 남은 것(보존 정리 배치 · 쓰기 감사와 trace_id 로 잇기 · 사용량 화면)([MCP 설계](../design/06-mcp.md)). 워크스페이스 엔드포인트는 만들지 않는다(ADR-029). 지금 도구는 일감 입구 19개 · 프로젝트 관리 입구 5개다.
- Keycloak 대사(`keycloak_operation` FAILED 재시도 포함), SMTP.
- 공개 경로 시도 제한(`SignupRateLimiter` — 가입 · 비밀번호 찾기)과 비밀번호 변경의 틀린 횟수(`PasswordAttemptLimiter`)가 인스턴스 메모리에 있다. 여러 대로 늘리면 Redis 로 옮긴다.
- Keycloak 조직 1만 · 10만 개 부하 시험, api 서비스 계정 최소 권한 실측.

## 9. 바꿀 때 함께 고칠 문서

| 바꾼 것 | 함께 고칠 곳 |
|---|---|
| REST 경로 추가 · 변경 | 이 쪽 §6.1 표 · 설계의 입구 표([워크스페이스·계정·권한](../design/03-workspace-identity.md) · [프로젝트 설계](../design/04-project.md) · [일감 설계](../design/05-issue.md)) · web-frontend `shared/api/endpoints.ts` · `types.ts` |
| 접근 어노테이션 · 판정 규칙 · 토큰 검증 · MCP 키 | [SECURITY.md](../../SECURITY.md) · 이 쪽 §4 |
| 권한 코드 · 기본 역할 | `Permission`(정본) · `BuiltinRole` · `infra/keycloak/permission-catalog.json` → `python3 -I infra/keycloak/ensure-realm.py` 로 Keycloak 클라이언트 역할을 만든 뒤 api 를 띄운다. 기동 때 `PermissionCatalogSync` 가 DB `permission` 표를 맞추고, Keycloak 에 코드가 다 있으면 `PermissionCatalogMigrator` 가 **이미 있는 워크스페이스의 역할**(기본 역할 = 새 정의, 사용자 정의 역할 = 없어진 코드 빼기)을 Keycloak 먼저 · 사본 나중으로 맞춘다(카탈로그 지문이 바뀐 때에만). 코드 이름을 바꾸면 Flyway 로 `role_permission` 사본을 먼저 옮긴다. `PermissionCatalogTest` 가 설치 파일과 enum 이 같은지 본다 · [워크스페이스·계정·권한](../design/03-workspace-identity.md) · [프로젝트 설계](../design/04-project.md) · web-frontend 리소스의 `permissions.*` |
| 오류 `code` 추가 | 화면이 그 코드로 분기하면 web-frontend 와 [WEB-DESIGN.md](../../WEB-DESIGN.md) |
| DB 스키마 | 새 Flyway 파일(기존 파일은 고치지 않는다) · 이 쪽 §3 의 파일 목록 · 그 테이블의 설계([데이터 모델](../design/02-data-model.md)과 각 설계 쪽의 DDL 초안) |
| MCP 입구 · 도구 · `x-moai-works-` 헤더 | 이 쪽 §6.2 · [SECURITY.md](../../SECURITY.md) §5.2 · §8 · [MCP 설계](../design/06-mcp.md) §8(도구마다 설명 원문 · 입력 · 결과 · 오류) |
| 새 env 키 | 이 쪽 §2 · 모듈 README 표 · 여러 모듈이 쓰면 [DEVELOPE.md](../../DEVELOPE.md) |
| 버전 올림 | 이 쪽 §2 · [00-overview](00-overview.md) §1 |

---

**관련 문서:** [모듈 한눈에](00-overview.md) · [SECURITY.md](../../SECURITY.md) · [워크스페이스·계정·권한](../design/03-workspace-identity.md) · [프로젝트 설계](../design/04-project.md) · [일감 설계](../design/05-issue.md) · [MCP 설계](../design/06-mcp.md) · [데이터 모델](../design/02-data-model.md) · [DEVELOPE.md](../../DEVELOPE.md)

[← 이전: 모듈 한눈에](00-overview.md) | [다음: moai-works-web-backend →](02-web-backend.md)
