# moai-works 개발 규칙

| 항목 | 내용 |
|---|---|
| 무엇 | 모든 모듈에 공통인 개발 규칙: 모듈 경계 · 버전 · 코드 · 설정과 비밀값 · 로컬 환경 · 검사 · 문서 현행화 · 커밋 |
| 언제 읽나 | 작업을 시작할 때(처음 한 번), 새 모듈 · 의존성 · 설정 키를 더할 때, 작업을 마치고 검사 · 문서 · 커밋할 때 |
| 따로 있는 규칙 | 웹 화면은 [WEB-DESIGN.md](WEB-DESIGN.md) · 인증 · 권한 · 비밀값은 [SECURITY.md](SECURITY.md) · 모듈마다의 구조와 명령은 [docs/modules](docs/modules/00-overview.md) |
| 형제 저장소 | `~/Documents/GitHub/moai-wiki` — 구조 · 의존성 · 인증 · 역할과 권한 · 워크스페이스가 같다(2026-11-21 사용자 지시). 토대 코드는 거기서 가져왔다. 토대를 고치면 양쪽에 같은 고침이 필요한지 본다 |
| 기준일 | 2026-11-21 |

## 1. 모듈 경계

| 모듈 | 언어 | 한다 | 하지 않는다 |
|---|---|---|---|
| `moai-works-api` | Java · Spring Boot | DB 정본, 계정 · 워크스페이스 · 프로젝트 · 권한 **판정**, 일감 · 워크플로 · 계획 · 시간 · 위키 등 업무 전부, **MCP 전부**, 메일 · 웹훅 · 검색 색인 같은 뒷일(api 안 작업 큐) | LLM 호출 |
| `moai-works-web-backend` | Java · Spring Boot | BFF — 로그인 세션 · 사용자 토큰 전달 · 화면 정적 파일 | 권한 판정 · 업무 로직 · DB |
| `moai-works-web-frontend` | TypeScript · React | 화면. 권한은 숨기기만 | 판정 · 토큰 보관 |

- **판정은 api 한 곳.** 다른 모듈에 권한 규칙을 복사하지 않는다(화면의 `role-rules.ts` 는 표시용 사본이고 api 와 같은 규칙이어야 한다).
- **AI Agent 는 바깥에 있다.** 시스템 안에 LLM · Agent 모듈을 두지 않는다. Agent 가 할 일은 api 의 MCP 도구로 열고, 도구는 REST 와 같은 서비스를 부른다([design/06](docs/design/06-mcp.md)).
- 워커 모듈을 따로 두지 않는다. 오래 걸리거나 바깥에 보내는 일(메일 · 웹훅 · 검색 색인 · 가져오기)은 api 안의 DB 작업 큐가 맡는다. 무거워지면 그때 모듈을 나눈다.
- 새 모듈을 만들면 이름은 `moai-works-<역할>`, Keycloak 클라이언트 이름도 같게, `docs/modules/` 에 한 쪽을 더한다.

## 2. 버전

- **moai-wiki 와 같은 판을 쓴다. 최신 안정(GA)만** — RC · M · SNAPSHOT · beta 금지. 올리기 전에 Maven Central · npm 에서 실제로 확인한다(기억에 기대지 않는다). 한쪽만 올리면 다른 쪽도 올릴지 본다.
- 메이저를 올리면 이름이 바뀐 설정이 없는지 확인한다(예: Boot 4 의 `spring.session.data.redis.*` — 옛 이름은 오류 없이 무시된다).

| 쪽 | 지금 |
|---|---|
| Java | 21 (이 Mac 은 Android Studio JBR 21 뿐) · Maven Wrapper |
| Spring | Boot 4.2.0 · Security 7.2.0 · Session 4.2.0 · Spring AI 2.1.0(MCP Java SDK 2.2.0) · Jackson 3 · Flyway · AWS SDK S3 2.59.7(첨부용, 아직 쓰는 곳 없음) |
| 웹 | Node 22 이상 · React 19.3 · React Router 8.5 · MUI 9.5 · TanStack Query 5 · i18next 27.2 · react-markdown 10.1(remark-gfm 4.0) · CodeMirror 6 · Vite 8 · TypeScript 6.0 · ESLint 10 · Vitest 5 |
| 인프라 | Keycloak 26.8.2 · Redis 8.10.2 · PostgreSQL · MinIO · OpenSearch 3.9.0(nori — 검색을 만들 때 씀) |

## 3. 코드

### 3.1 공통

- 주석 · 로그 · 오류 문구 · 문서는 한국어, 쉬운 말로. 코드 이름은 영어.
- moai-works 가 정의하는 HTTP 헤더는 소문자 `x-moai-works-` 접두어.
- 오류는 RFC 9457 problem+json 에 `code`(대문자 스네이크)를 싣는다. 화면과 MCP 도구는 `code` 로 분기한다.
- 비밀값 · 토큰 · 비밀번호 · MCP 키 원문을 로그에 찍지 않는다.

### 3.2 Java (api · web-backend)

- 패키지 `com.moai.works.api` · `com.moai.works.web`. 기능 단위 폴더(`account/` · `workspace/` · `project/` · `issue/` · `mcp/` …).
- 설정은 `moai-works.*` 속성(`@ConfigurationProperties` 레코드) ← 환경 변수 `MOAI_WORKS_*`. 기본값은 로컬 개발값, 비밀값은 기본값 없이.
- 스키마는 Flyway(`db/migration/V<n>__<이름>.sql`)가 정본, JPA 는 `ddl-auto: validate`. 이미 적용된 마이그레이션은 고치지 않고 새 파일을 더한다. 시각은 UTC. 토대 스키마는 `V1__foundation.sql`(moai-wiki 의 V1~V3 · V8 · V9 최종 모양을 합친 것)이다.
- api 의 REST 핸들러는 **접근 어노테이션 정확히 하나**(없으면 기동 실패) — [SECURITY.md §6](SECURITY.md#6-api-의-인가--무엇을-할-수-있는가). 프로젝트 안의 일(일감 포함)은 `@ProjectApi(권한)` 을 쓴다 — moai-wiki 의 `@WikiApi` 와 같다([design/04](docs/design/04-project.md)).
- MCP 도구는 서비스 메서드를 부르는 얇은 층이다. 권한 판정을 도구 안에서 따로 만들지 않는다.
- 업무 오류는 `ApiException`(상태 + `code` + 문구)으로 던진다.
- Keycloak 에 쓰는 일은 `KeycloakOperationLog` 를 거친다(Keycloak 먼저 → 기록 → DB 사본).

### 3.3 웹 화면

[WEB-DESIGN.md](WEB-DESIGN.md) 를 따른다. 화면 보안은 [SECURITY.md §9](SECURITY.md#9-화면web-frontend의-보안-규칙). 참고한 SeedSiteKeeper 화면은 유료 템플릿이라 **구조만 참고하고 코드는 가져오지 않는다.** Redmine 은 기능을 참고할 뿐 화면 · 코드를 가져오지 않는다.

## 4. 설정 · 비밀값

- 비밀값은 그 폴더의 `.env`(각 폴더의 `.gitignore` 가 막는다)에만. 설정 파일에는 `${MOAI_WORKS_…}` 자리만.
- 값의 출처: Keycloak 클라이언트 비밀값 · 시드 비밀번호는 `infra/keycloak/.env`(`init-realm.sh` 가 만든다), MinIO 키는 `infra/minio/.env`, api 의 DB 계정은 `src/moai-works-api/.env`(`MOAI_WORKS_DB_USERNAME` · `MOAI_WORKS_DB_PASSWORD`). SMTP 서버 · 계정(`MOAI_WORKS_SMTP_HOST` · `_PORT` · `_USERNAME` · `_PASSWORD` · `_TLS`)은 메일 smtp 모드(`MOAI_WORKS_MAIL_MODE=smtp`)를 켤 때만 `src/moai-works-api/.env` 에 넣는다 — SMTP 서버는 아직 정하지 않았다(WBS D-06), 기본은 로그 모드.
- 문서 · 커밋 · 대화 · 로그 · 화면에 비밀값을 적지 않는다. 문서에는 키 이름만.
- api 는 IntelliJ 에서 돌릴 때 **작업 폴더를 모듈 폴더로** 둔다(`.env` 를 읽는다).
- 새 키를 더하면: 모듈 README 의 표 + [SECURITY.md §10.1](SECURITY.md#101-비밀값)(비밀값이면).

## 5. 로컬 환경

| 서비스 | 주소 | 비고 |
|---|---|---|
| Keycloak 26.8.2 | http://localhost:8180, 렐름 `moai-works` | **같이 쓰는 컨테이너 `MOAI-Keycloak`**(moai-wiki 의 `infra/keycloak/compose.yaml` 로 띄운다). 렐름만 `infra/keycloak/init-realm.sh` → `ensure-realm.py`(멱등). `infra/keycloak/.env` 에 그 컨테이너의 관리자 계정을 먼저 넣는다 |
| PostgreSQL | MOAI-DB `:5432`, DB `moai_works`(계정 `moai_works`) | **같이 쓰는 컨테이너** — 우리 DB 만 다룬다. 2026-11-21 에 DB · 계정을 만들었다 |
| MinIO | MOAI-MinIO `:9000`(콘솔 `:9001`) | 같이 쓰는 컨테이너 — 버킷 `moai-works-attachments` 만(`infra/minio/init-minio.sh`, 첨부를 만들 때 돌린다) |
| Redis | MOAI-Redis `:6379` | 같이 쓰는 컨테이너 — 키는 `moai-works:` 아래만. `FLUSHDB` · `FLUSHALL` 금지 |
| OpenSearch 3.9.0 | http://localhost:9280 | 같이 쓰는 컨테이너 `MOAI-OpenSearch` — 색인은 `moai-works-*` 만(검색을 만들 때) |
| api | https://localhost:11001 | 포트 · HTTPS 는 §5.2 |
| web-backend | https://localhost:11002 | 화면 개발 중에는 https://localhost:11003(`npm run dev`, `/api` 를 11002 로) |

- **개발용 계정**: `python3 -I infra/dev-seed/seed_workspaces.py` — 시스템 관리자 `sysadmin`(렐름 설치 때 만든다, 워크스페이스 없음)과 moaiworks01~10. 워크스페이스 A(퍼블릭 · 승인 후 가입) = 01 소유자 · 02 관리자 · 03 큐레이터 · 04 · 05 멤버(04 는 보안 점검 프로젝트의 편집자), B(퍼블릭 · 자동 가입) = 06 소유자 · 07 큐레이터 · 08~10 멤버, C(프라이빗) = 06 소유자 · 07 관리자 · 08 큐레이터. **`infra/keycloak/.env` 의 `MOAI_WORKS_SYSADMIN_PASSWORD` · `MOAI_WORKS_SEED_PASSWORD` 는 처음 비밀번호다** — 렐름 설치 · 시드 스크립트는 계정이 없을 때만 그 값으로 만들고, 이미 있는 계정의 비밀번호는 바꾸지 않는다. 누가 비밀번호를 바꾸면 `.env` 의 값은 더 이상 맞지 않는다(그 파일을 고치지 않는다)
- ⚠️ **이 Mac 에서는 Docker 안에서 이미지를 받거나 빌드하지 않는다**(`docker pull` · `docker build` · compose 의 pull). Docker VM 네트워크가 멈춘 적이 있다. 이미지는 Mac 에서 `skopeo` 로 받아 `docker load`. Docker 를 다시 시작해야 하면 먼저 사람에게 묻는다.
- ⚠️ moai-wiki 가 같은 PC 에서 10001 · 10003 · 10004 로 돌고 있을 수 있다. 포트가 겹치지 않게 1100x 를 쓴다.

### 5.1 띄우고 끄기

모듈마다 `bin/startup.sh` · `bin/shutdown.sh` 가 있다(moai-wiki 와 같다). 어느 폴더에서 불러도 된다.

| 모듈 | `bin/startup.sh` 가 띄우는 것 | 로그 넘기기 |
|---|---|---|
| api · web-backend | `target/` 의 jar(없으면 `./mvnw -q package` 먼저). 응답할 때까지 기다린다 | 날짜가 바뀌거나 50MB 를 넘으면 `<모듈>.log.<날짜>.<번호>.gz`. 30일 · 합계 2GB 까지(`application.yaml` 의 `logging`) |
| web-frontend | vite 개발 서버(11003). 화면을 고칠 때만 쓴다 — 운영에서는 web-backend 가 빌드 결과를 내보내므로 띄울 것이 없다 | 날짜가 바뀌면 `<모듈>.log.<날짜>`. 30일(`bin/rolling-log.mjs`) |

- **파일**: `<모듈>/logs/<모듈>.log`(로그) · `.out`(표준 출력 — 로깅이 뜨기 전 오류만) · `.pid`. `logs/` 는 git 에서 뺀다.
- **HTTPS**: 스크립트는 뜬 것을 `https://localhost:<포트>` 로 확인한다(로컬 인증 기관으로 인증서를 본다). 인증서가 없으면 띄우지 않고 `infra/tls/make-certs.sh` 를 돌리라고 알려 준다(§5.2).
- **끄기**: `shutdown.sh` 는 `startup.sh` 로 띄운 프로세스만 끈다. 정상 종료(TERM)를 보내고 30초 안에 끝나지 않으면 강제로 끝낸다.
- **이 자리에서만 쓰는 값**(`JAVA_HOME` · `JAVA_OPTS` · `STARTUP_WAIT` · `SHUTDOWN_WAIT` …)은 `bin/setenv.sh` 에 둔다(git 제외).
- **시각**: api · web-backend 는 JVM 시간대를 UTC 로 고정한다. 로그 시각과 넘기는 날짜가 UTC 다(한국 시간 09:00 에 날짜가 바뀐다).
- **IDE · 테스트**에서는 파일 로그가 없다. `MOAI_WORKS_LOG_FILE` 이 비면 콘솔에만 쓴다(스크립트가 이 값을 넣는다).

### 5.2 포트 · HTTPS

2026-11-21: moai-wiki 와 같은 규칙(모든 서비스는 HTTPS)이다. 포트는 사람이 정한 1100x(api 11001 · web-backend 11002 · web-frontend 개발 서버 11003)를 쓴다. moai-wiki(1000x)와 겹치지 않고, 이 Mac 에서 비어 있는 것을 확인했다.

| 서비스 | 포트 | 주소 |
|---|---|---|
| moai-works-api | 11001 | https://localhost:11001 — REST `/moai-works-api/**` · MCP `/mcp/**`(직접 붙어도 된다 — 연결 안내는 web-backend 주소) |
| moai-works-web-backend | 11002 | https://localhost:11002 — 화면 + `/api` + **MCP `/mcp/**`**(바깥 MCP 클라이언트가 붙는 곳, api 로 중계 — 4단계) |
| moai-works-web-frontend 개발 서버 | 11003 | https://localhost:11003 — `/api` 는 11002 로 넘긴다 |

포트를 바꾸려면 `MOAI_WORKS_API_PORT` · `MOAI_WORKS_WEB_PORT` · `MOAI_WORKS_FRONTEND_PORT`.

- **인증서 만들기**: `infra/tls/make-certs.sh` → `infra/tls/certs/`(git 제외). `mkcert` 가 있어야 한다(`brew install mkcert`). `localhost.pem` · `localhost-key.pem`(서버 인증서와 열쇠) · `ca.pem`(로컬 인증 기관) · `ca-bundle.pem`(운영체제가 믿는 인증 기관 + `ca.pem`).
- **브라우저 경고**: 로컬 인증 기관을 이 PC 의 신뢰 목록에 넣는 것은 사람이 한다(Mac 비밀번호를 묻는다): `mkcert -install`.
- **서비스끼리 부를 때 인증서 확인을 끄지 않는다.** 부르는 쪽이 `ca.pem` 을 원래 믿는 인증 기관에 **더해** 믿는다.

  | 부르는 쪽 → 받는 쪽 | 믿는 방법 |
  |---|---|
  | web-backend → api | `moai-works-web.tls.ca-file`(`MOAI_WORKS_TLS_CA`) — `OutboundHttp` 가 JVM 기본 인증 기관에 더한다 |
  | vite 개발 서버 → web-backend | `vite.config.ts` 의 프록시가 `ca.pem` 으로 확인한다 |
  | 시드 스크립트 → api | `infra/dev-seed/seed_workspaces.py` 가 `ca.pem` 을 더한다 |
  | MCP 클라이언트(Claude Code 등) → api | 로컬에서는 클라이언트가 `ca.pem` 을 믿게 한다(예: Node 기반이면 `NODE_EXTRA_CA_CERTS`). 운영은 공인 인증서 |
  | `bin/startup.sh` 의 확인 | `curl --cacert ca.pem` |

- **설정**: `MOAI_WORKS_TLS_ENABLED`(기본 `true`) · `MOAI_WORKS_TLS_CERT` · `MOAI_WORKS_TLS_KEY` · `MOAI_WORKS_TLS_CA`. TLS 를 앞단 프록시가 끝내는 배치에서만 `MOAI_WORKS_TLS_ENABLED=false` 로 끈다.
- **세션 쿠키**는 `__Host-MOAI_WORKS_SESSION` · Secure 다([SECURITY.md §4.2](SECURITY.md#42-세션)).
- **HSTS 는 `localhost` 에 보내지 않는다**(`HstsRule`, api · web-backend). `localhost` 에 한 번 걸리면 이 PC 에서 HTTP 로 도는 다른 것(Keycloak 콘솔 8180 · MinIO 콘솔 9001 · 다른 프로젝트)이 브라우저에서 열리지 않는다. 운영 도메인에는 보낸다.
- 같이 쓰는 컨테이너(Keycloak · PostgreSQL · MinIO · Redis · OpenSearch)는 로컬에서 평문 그대로다. 운영에서는 TLS 로 붙는다([SECURITY.md §11](SECURITY.md#11-운영-전에-바꿀-것--아직-안-한-것)).
- 자동 테스트는 서버를 띄우지 않아서(MockMvc) 인증서 없이 돈다.

## 6. 검사 — 끝났다고 하기 전에

| 모듈 | 명령 (모듈 폴더에서) |
|---|---|
| api | `./mvnw -q package` — 자동 테스트 포함(MOAI-DB 가 떠 있어야 한다. 같은 DB 의 시험 전용 스키마 `moai_works_test` 를 매번 다시 만들고, Keycloak · MinIO 는 가짜를 쓴다 — [모듈 문서](docs/modules/01-api.md)). JDK 는 `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`. 2026-11-21 기준 66건 |
| web-backend | `./mvnw -q package` (자동 테스트는 중계 규칙 `ApiRelayTest` 뿐이다(2026-11-24, DB · Redis 없이 돈다) — 로그인 · 세션 흐름은 실제 호출로 확인한다) |
| web-frontend | `npm run lint && npm run typecheck && npm test && npm run build` (2026-11-21 기준 vitest 60건) |
| 문서 | 저장소 맨 위에서 `python3 -I tools/check_docs.py docs` → 문제 0건. 요구사항의 진행을 고쳤으면 먼저 `python3 -I tools/req_progress.py` 로 진행 현황을 다시 만든다 |

- **화면을 바꿨으면** 브라우저에서 데스크톱과 390px 를 모두 본다([WEB-DESIGN.md](WEB-DESIGN.md)). 브라우저 확인은 **ego-browser** 로 한다(Playwright 를 쓰지 않는다).
- **인증 · 권한을 바꿨으면** [SECURITY.md §12](SECURITY.md#12-바꿀-때-지킬-것) 의 항목을 실제 요청으로 다시 확인한다.
- **MCP 도구를 바꿨으면** 실제 MCP 클라이언트(Claude Code)로 붙여 `tools/list` 와 도구 호출을 한 번 해 본다. 권한 없는 키 · 범위 밖 프로젝트가 막히는지도 본다.
- 실패한 검사는 실패라고 보고한다. 건너뛴 검사는 건너뛰었다고 적는다.

## 7. 문서 — 읽을 때와 고칠 때

문서는 늘 다 읽을 필요는 없지만, **아래 시점에는 먼저 읽고, 바뀐 것은 같은 작업에서 고친다.** 코드와 문서가 어긋나면 코드를 확인해 문서를 맞춘다.

| 바뀌는 것 | 먼저 읽을 문서 | 함께 고칠 문서 |
|---|---|---|
| 로그인 · 세션 · 토큰 · CSRF · 헤더 · Keycloak | [SECURITY.md](SECURITY.md) | SECURITY.md · 해당 모듈 문서 |
| REST 엔드포인트 · 접근 어노테이션 · 권한 코드 · 역할 | SECURITY.md §6 · [design/03](docs/design/03-workspace-identity.md) · [design/04](docs/design/04-project.md) | SECURITY.md · [01-api](docs/modules/01-api.md) · design/03 · design/04 · `infra/keycloak/permission-catalog.json` |
| MCP 엔드포인트 · 키 · 도구 | [design/06](docs/design/06-mcp.md) | design/06 · [FR-MCP](docs/requirements/04-mcp.md) · 01-api |
| 화면 · 테마 · 스킨 · 글 · 배치 | [WEB-DESIGN.md](WEB-DESIGN.md) | WEB-DESIGN.md · [03-web-frontend](docs/modules/03-web-frontend.md) |
| 모듈 구조 · 명령 · 포트 · 설정 키 | 해당 [모듈 문서](docs/modules/00-overview.md) | 모듈 문서 · 모듈 README |
| 공통 규칙 · 버전 · 로컬 환경 | 이 문서 | 이 문서 |
| 설계 결정 | [design/00 ADR](docs/design/00-key-decisions.md) | ADR 추가(기존 것은 지우지 않고 `대체됨`) |
| 다음에 할 작업 고르기 | [WBS](docs/wbs/00-overview.md) · [정해야 할 것](docs/wbs/08-decisions.md) | 결정이 나면 wbs/08 에 날짜와 답 |
| 작업 하나를 끝냄 | [HANDOFF.md](HANDOFF.md) · [WBS](docs/wbs/00-overview.md) · [요구사항 진행 현황](docs/requirements/06-progress.md) | HANDOFF §9 작업 기록(날짜 · 한 일 · 검증) · 남은 일 · WBS 단계 문서의 상태 · [구현한 기능](docs/wbs/01-implemented.md) · **그 작업이 다룬 요구사항의 `진행` 줄과 수용 기준 체크**([방법](docs/requirements/00-overview.md#6-진행-관리)) → 사람에게 처리한 결과와 남은 요구사항을 알린다 |

**문서 형식** ([docs/README.md](docs/README.md) "위키 형식"):
- 한국어로 쓰고, 한 쪽에는 한 주제만 둔다.
- 첫 줄에 경로 `[홈](…) › [영역](…) › 쪽`, 끝에 `관련 문서`와 `[← 이전](…) | [다음 →](…)`를 둔다.
- ID 에는 앵커 `<a id="…">`를 단다.
- 이동 순서는 README → 01 → 02 → requirements → design → modules → plan → wbs → README 다.

## 8. 커밋

- **작업이 끝나고 검사가 통과하면 커밋한다. 푸시는 하지 않는다**(사람이 한다).
- **작성자 · 커밋터는 `JackyLim`**(전역 git 설정 그대로). `--author` · `-c user.*` · `GIT_*` 로 바꾸지 않는다. AI 이름 · `Co-Authored-By` 줄 · "Generated with" 줄을 넣지 않는다. 커밋 뒤 `git log -1 --format='%an | %cn'` 으로 확인한다.
- 모듈 단위로 나눈다. 메시지는 한국어로 `<모듈>: <요약>` 한 줄, 빈 줄, `- ` 로 시작하는 본문을 쓴다. 모듈은 `api` · `web-backend` · `web-frontend` · `infra` · `docs` 다.
- `.env` · `.DS_Store` · 빌드 결과(`static/` · `target/` · `node_modules/`)는 올리지 않는다. 올리기 전에 `git status` 와 `git diff --cached --stat` 으로 확인한다.

---

**관련 문서:** [CLAUDE.md](CLAUDE.md) · [웹 디자인 규칙](WEB-DESIGN.md) · [보안 · 인증 구조](SECURITY.md) · [모듈 한눈에](docs/modules/00-overview.md) · [HANDOFF.md](HANDOFF.md) · [설계 문서 홈](docs/README.md)
