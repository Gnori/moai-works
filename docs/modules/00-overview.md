[홈](../README.md) › [모듈](00-overview.md) › 모듈 한눈에

# 모듈 한눈에

코드는 `src/` 아래의 모듈 세 개로 나뉜다. 이 영역에는 모듈마다 한 쪽이 있고, 각 쪽에 그 모듈이 하는 일과 하지 않는 일, 구조, 입구, 실행과 검사, 고칠 때 함께 고칠 문서를 적는다. 모듈 폴더의 `README.md` 에는 빠른 실행만 남기고 자세한 내용은 이 영역에 둔다.

저장소 구조 · 의존성 · 인증 · 워크스페이스 · 역할과 권한 · MCP 체계 · 웹 구조는 형제 저장소 moai-wiki 와 같고, **위키 대신 프로젝트, 페이지 대신 일감**이다. moai-wiki 의 토대 코드(계정 · 워크스페이스 · 권한 · BFF 로그인 · 화면 뼈대)를 가져와 이름만 바꾸고 위키 · 자료실과 워커 모듈(agent · collector · refiner)은 걷어 냈다. 위키 · 페이지 자리에는 프로젝트 · 일감이 같은 구조로 들어온다(계획 — api `project/` · `issue/`, 화면 `features/project` · `features/issue`). **AI Agent 는 시스템 안에 두지 않는다** — 바깥 AI Agent 가 api 의 MCP 로 붙는다.

저장소 전체의 규칙은 맨 위 문서에 있다.

| 문서 | 내용 |
|---|---|
| [CLAUDE.md](../../CLAUDE.md) | 핵심 규칙, 그리고 어떤 작업에서 어느 문서를 읽고 고칠지 |
| [DEVELOPE.md](../../DEVELOPE.md) | 개발 규칙: 버전, 비밀값, 검사, 커밋, 로컬 환경 |
| [SECURITY.md](../../SECURITY.md) | 인증 구조와 보안 규칙 |
| [WEB-DESIGN.md](../../WEB-DESIGN.md) | 웹 화면 규칙: 테마, 스킨, 다국어, 반응형 |
| [HANDOFF.md](../../HANDOFF.md) | 지금 상태, 결정 사항, 작업 기록 |

## 1. 모듈

| 모듈 | 언어 · 스택 | 포트 | Keycloak 클라이언트 | 맡는 것 | 하지 않는 것 |
|---|---|---|---|---|---|
| [moai-works-api](01-api.md) | Java 21 · Spring Boot 4.2.0 · Spring Security 7.2.0 · Spring AI 2.1.0 (MCP Java SDK 2.2.0) | 11001 (https) | `moai-works-api` | DB 정본, 계정 · 가입 · 워크스페이스 · 역할 · 멤버십 · 초대 · 감사, **모든 권한 판정**, **MCP 전부**. 앞으로 프로젝트 · 프로젝트 역할 · 일감 · 워크스페이스 MCP 엔드포인트와 도구(계획) | LLM 호출(요약 · 분류는 바깥 Agent 몫), 브라우저 세션 |
| [moai-works-web-backend](02-web-backend.md) | Java 21 · Spring Boot 4.2.0 · Spring Security 7.2.0 · Spring Session 4.2.0 (Redis) | 11002 (https) | `moai-works-web-backend` | 웹 BFF: 로그인 세션, 사용자 토큰 전달(파일 올리기 · 내려받기도 그대로 흘려보냄), React 정적 파일 | 권한 판정, 업무 로직, DB |
| [moai-works-web-frontend](03-web-frontend.md) | React 19.3 · MUI 9.5 · Vite 8 · TypeScript 6.0 | 11003 (개발, https) | 없음 (브라우저에는 토큰이 없다) | 웹 화면: 로그인 · 가입 · 워크스페이스 · 멤버 · 역할 · 초대 · 감사 · 시스템 관리. 프로젝트 · 일감 · MCP 엔드포인트 화면(계획) | 권한 판정 (표시만 한다) |

- **Keycloak 클라이언트 이름 = 모듈 이름.** 새 모듈이 Keycloak 을 쓰면 클라이언트 이름을 모듈 이름과 같게 짓는다.
- **권한 판정은 api 한 곳에서만 한다.** web-backend 와 web-frontend 가 무엇을 확인했든 api 가 요청마다 다시 판정한다. 바깥 AI Agent 도 MCP 로 api 의 같은 판정을 지난다. 자세한 내용은 [SECURITY.md](../../SECURITY.md).
- **DB 는 api 만 쓴다.** 다른 모듈은 api REST 로만 데이터를 주고받는다.
- **워커 모듈은 만들지 않는다.** 나중에 비동기 일(알림 · 메일 · 색인 — [나중 후보](../01-overview.md#32-나중-후보))이 생기면 api 안의 DB 작업 큐(아웃박스)와 스케줄러로 한다.
- 무엇이 어디의 정본인지(Keycloak, PostgreSQL, MinIO, OpenSearch)는 [워크스페이스·계정·권한 설계](../design/03-workspace-identity.md)와 [아키텍처](../design/01-architecture.md)에 있다.

## 2. 흐름

```
 브라우저 ──▶ web-frontend(정적 파일) ──/api──▶ web-backend(BFF, 세션) ──Bearer 사용자 토큰──▶ api ──▶ PostgreSQL
                                                     │                                        │
                                                     └──── 패스워드 그랜트 ──▶ Keycloak ◀──── 관리 API(계정 · 조직 · 그룹)

 바깥 AI Agent (Claude Code 등) ──MCP 키──▶ web-backend /mcp/** ─중계─▶ api /mcp/issue · /mcp/project   (키 하나로 두 입구)
```

- **지금 끝까지 이어진 길**(2026-11-21 코드, 2026-11-22 로그인 흐름 확인): 로그인 → 개인 워크스페이스 → 팀 워크스페이스 만들기 · 찾기 · 가입 신청 · 초대 · 역할 · 감사 → 시스템 관리자의 가입 정책 · 계정 관리. 바깥 AI Agent 는 MCP 키 하나로 일감 입구 `/mcp/issue`(도구 19개) · 프로젝트 관리 입구 `/mcp/project`(워크스페이스 · 프로젝트 목록 · 일감 템플릿 관리)에 붙는다(2026-11-23). 키는 사용자의 것이라 키 하나로 MCP 권한이 있는 모든 워크스페이스에서 일한다(2026-11-23 [ADR-029](../design/00-key-decisions.md#adr-029)).
- 프로젝트 · 프로젝트 역할 · 일감 · 워크스페이스 MCP 엔드포인트는 아직 없다(계획 — [로드맵](../plan/01-roadmap.md)). 첨부 · 알림 · 검색 등은 나중 후보다.

## 3. MinIO 버킷과 키

버킷 하나, 버전 보관을 켠다(`infra/minio/init-minio.sh`). 아직 쓰는 기능이 없고, 첨부(나중 후보)를 만들 때 스크립트를 돌린다.

| 버킷 | api 키(사용자 `moai-works-api`) |
|---|---|
| `moai-works-attachments` (첨부 — 일감, 나중 후보) | 쓰기 · 읽기 · 목록 · **삭제** |

객체를 쓰고 지우는 것은 api 뿐이다. 객체 키는 api 가 정한다(첨부 기능에서 규칙을 정한다 — 워크스페이스 id 를 맨 앞에, 사람이 정한 이름은 넣지 않는다). 저장소 코드(`storage/ObjectStore` · `S3ObjectStore`)는 moai-wiki 에서 가져와 두었다.

## 4. 로컬 서비스

| 서비스 | 주소 | 비고 |
|---|---|---|
| Keycloak 26.8.2 | http://localhost:8180 (렐름 `moai-works`) | 다른 프로젝트와 같이 쓰는 `MOAI-Keycloak`. 띄우는 정의는 moai-wiki 저장소의 `infra/keycloak/compose.yaml`, moai-works 는 `infra/keycloak/init-realm.sh` 로 렐름만 만든다 |
| PostgreSQL | MOAI-DB :5432, DB `moai_works`, 계정 `moai_works` | 다른 프로젝트와 같은 컨테이너이고 DB 만 나눠 쓴다. 자동 테스트는 같은 DB 의 스키마 `moai_works_test` |
| Redis 8.10.2 | MOAI-Redis :6379 | 다른 프로젝트와 같이 쓴다. web-backend 세션만 저장하고, 키 이름표는 `moai-works:web-backend:session` |
| MinIO | MOAI-MinIO :9000 · 콘솔 :9001 | 다른 프로젝트와 같이 쓴다. 버킷 `moai-works-attachments` 와 사용자 `moai-works-api` (첨부 기능부터 — 나중 후보) |
| OpenSearch 3.9.0 | MOAI-OpenSearch :9280 | 전문 검색(나중 후보)을 만들 때 쓴다 — 색인 이름 `moai-works-*`, nori. 지금 코드는 부르지 않는다 |
| moai-works-api | https://localhost:11001 | REST `/moai-works-api/**` · MCP `/mcp/issue`(일감) · `/mcp/project`(프로젝트 관리) |
| moai-works-web-backend | https://localhost:11002 | 화면과 `/api` |
| web-frontend 개발 서버 | https://localhost:11003 | `npm run dev`, `/api` 는 11002 로 넘긴다 |

우리 서비스는 모두 HTTPS 다. 로컬 인증서는 `infra/tls/make-certs.sh` 가 만든다([DEVELOPE.md §5.2](../../DEVELOPE.md#52-포트--https)). 포트는 사람이 정한 1100x 로, moai-wiki(10001 · 10003 · 10004)와 겹치지 않는다.

세 모듈은 모듈 폴더의 `bin/startup.sh` · `bin/shutdown.sh` 로 띄우고 끈다. 웹에서 일하려면 api · web-backend 를 띄운다. 로그는 `<모듈>/logs/` 에 날짜별로 쌓인다([DEVELOPE.md §5.1](../../DEVELOPE.md#51-띄우고-끄기)).

계정과 비밀번호를 어디서 찾는지, 그리고 Docker 주의 사항은 [DEVELOPE.md](../../DEVELOPE.md)에 있다.

## 5. `infra/`

| 폴더 | 내용 |
|---|---|
| `infra/keycloak/` | `realm-moai-works.json`(비밀값 없음) · `init-realm.sh`(렐름 만들기 · 새 클라이언트 부분 가져오기 · 클라이언트 비밀값을 `.env` 에) → `ensure-realm.py`(조직 켜기 · 무차별 대입 잠금 · 렐름 역할 · 권한 코드 · api 서비스 계정 역할 · 처음 시스템 관리자) · `permission-catalog.json`(권한 코드 16개 · 렐름 역할 2개 — [2.1](../wbs/03-stage-2-project.md#w-2-1) 에서 25개로) · `.env`(관리자 계정 · 비밀값과 시드 비밀번호, git 제외). compose 정의는 없다(같이 쓰는 컨테이너) |
| `infra/dev-seed/` | `seed_workspaces.py`: 실제 API 흐름으로 moaiworks01~10 계정과 팀 워크스페이스 셋 — A(퍼블릭 · 승인 뒤 가입: 01 소유 · 02 관리자 · 03 ~ 05 멤버) · B(퍼블릭 · 자동 가입: 06 소유 · 07 ~ 10 멤버) · C(프라이빗 · 초대로만: 06 소유 · 07 관리자 · 08 멤버) — 를 만든다. 팀 워크스페이스를 만들 수 있는 계정은 01 · 06 뿐이다(시스템 관리자로 2개씩 허용). 프로젝트 · 일감 시드는 아직 없다([2.2](../wbs/03-stage-2-project.md#w-2-2) · [3.2](../wbs/04-stage-3-issue.md#w-3-2) 에서 더한다). 다시 실행해도 안전하다. `reset_workspaces.py --yes` 는 워크스페이스만 지운다 |
| `infra/minio/` | `init-minio.sh`: 버킷 `moai-works-attachments` · 사용자 `moai-works-api` · 최소 권한 정책을 맞추고, api 폴더 `.env` 에 저장소 값을 옮겨 적는다. 기존 MOAI-MinIO 를 쓴다. `.env` 는 git 제외 |
| `infra/tls/` | `make-certs.sh`: mkcert 로 `localhost` 인증서 · 로컬 인증 기관 · 인증 기관 묶음을 `certs/` 에 만든다(git 제외). 우리 서비스가 서로를 부를 때 이 인증 기관을 믿는다([DEVELOPE.md §5.2](../../DEVELOPE.md#52-포트--https)) |

## 6. 읽는 순서

| 하려는 일 | 읽을 쪽 |
|---|---|
| API · 권한 | [moai-works-api](01-api.md) → [SECURITY.md](../../SECURITY.md) → [워크스페이스·계정·권한 설계](../design/03-workspace-identity.md) |
| 프로젝트 · 일감 (계획) | [moai-works-api](01-api.md) · [moai-works-web-frontend](03-web-frontend.md) → [프로젝트 설계](../design/04-project.md) · [일감 설계](../design/05-issue.md) → 같은 기능의 moai-wiki 코드(`wiki/` · `page/`) |
| MCP · AI Agent 연결 | [moai-works-api](01-api.md) §6.2 → [SECURITY.md](../../SECURITY.md) §5.2 · §8.3 → [MCP 설계](../design/06-mcp.md) |
| 로그인 · 세션 | [moai-works-web-backend](02-web-backend.md) → [SECURITY.md](../../SECURITY.md) |
| 화면 | [moai-works-web-frontend](03-web-frontend.md) → [WEB-DESIGN.md](../../WEB-DESIGN.md) |

---

**관련 문서:** [아키텍처](../design/01-architecture.md) · [핵심 설계 결정](../design/00-key-decisions.md) · [워크스페이스·계정·권한](../design/03-workspace-identity.md) · [구현한 기능](../wbs/01-implemented.md) · [HANDOFF.md](../../HANDOFF.md)

[← 이전: 알림 설계](../design/07-notification.md) | [다음: moai-works-api →](01-api.md)
