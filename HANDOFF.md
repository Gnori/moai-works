# 세션 핸드오프 — moai-works

| 항목 | 내용 |
|---|---|
| 마지막 작업일 | 2026-11-22 |
| 단계 | **moai-wiki 와 같은 구조로 다시 정리.** 사용자가 "moai-wiki 와 다를 게 없고 위키냐 프로젝트냐 차이만"이라고 정했다. 토대(계정 · 워크스페이스 · 권한 · 로그인)는 렐름까지 만들어 실제 로그인을 확인했다. 프로젝트 · 프로젝트 역할 · 일감을 api(자동 테스트 99건)와 화면(98건)으로 만들어 브라우저로 확인했다. 다음은 MCP |
| 다음 세션 시작 방법 | "HANDOFF.md 읽고 이어서 하자"라고 요청. 다음 작업은 `docs/wbs/00-overview.md`. 규칙은 `CLAUDE.md` → `DEVELOPE.md` · `SECURITY.md` · `WEB-DESIGN.md` · `docs/modules/` |

---

## 1. 프로젝트 한 줄 요약

moai-wiki 와 같은 구조의 **프로젝트 관리 시스템**(멀티 워크스페이스 SaaS) — 위키 대신 프로젝트, 페이지 대신 일감. 사람은 웹 화면으로, **바깥 AI Agent(Claude Code 등)는 MCP 로** 같은 api 의 같은 권한 판정을 거쳐 프로젝트 · 일감을 다룬다. AI 는 내장하지 않는다.

## 2. 사용자가 확정한 사항

| 결정 (날짜) | 내용 | 문서 |
|---|---|---|
| 피봇 (11-21) | "이 프로젝트를 레드마인 같은 프로젝트 관리 시스템으로 전면 정리" — 그룹웨어 명세(143개)를 지웠다. git 이력 `5ba3a92` 에 남아 있다 | ADR-001 |
| AI (11-21) | "AI Agent 자체는 내장할 계획 없음. AI Agent 가 프로젝트를 관리할 수 있게 MCP 로 연결" · "moai-works-ai 는 제거" | ADR-003 |
| 구조 (11-21) | "moai-works-web-backend · moai-works-web-frontend 로 만들고 moai-wiki 디렉터리 구조로. src 아래 모듈, 디펜던시 · 인증 · 역할 권한 · 워크스페이스도 같게" | ADR-002 |
| 포트 (11-21) | "moai-works-api 11001 · moai-works-web-backend 11002 · moai-works-web-frontend 11003(개발)" | ADR-014 · `DEVELOPE.md §5.2` |
| **moai-wiki 와 같게 (11-22)** | "moai-works 를 wiki 모듈 구조랑 거의 비슷한 거 아니냐" · "다를 게 없다는 것" · "위키냐? 프로젝트냐 차이만 있다는 거야" · "구현도 비슷하게" · "인증 체계도 · 흐름도 · MCP 체계도 같게" · "웹도 moai-wiki 와 동일한 구조로". → 같은 기능은 moai-wiki 코드를 먼저 보고 같은 모양으로 만든다. 기본 역할은 moai-wiki 와 같은 넷(큐레이터 되살림), Agent 계정 안은 폐기 | ADR-002 · ADR-017 · ADR-018 |
| **일감 모델 (11-22)** | "워크스페이스에 여러 프로젝트 — 위키처럼" · "프로젝트에는 여러 일감 아이템" · "아이템에는 다양한 타입(예: 요구사항)" · "요구사항 관리자, 요구사항 담당자" · "완료 기한, 시작일, 완료 예정일, 완료일, 종료일" · "요구사항끼리 상하위 관계" · "연결 일감도 있어" · "위키랑 다른 부분이 이런 부분들" | ADR-018 · `docs/design/05-issue.md` |
| **MCP 는 web-backend 가 중계 (11-22)** | "MCP 는 api 로 직접 서비스하지 말고 moai-works-web-backend 가 프록시로 … web-backend 포트로 연결" · "moai-wiki 도 같다" — 바깥 클라이언트는 11002 `/mcp/**` | ADR-021 · FR-MCP-009 |
| **MCP 접근 전부 기록 · 키는 만료만 (11-22)** | "MCP 키 발급을 해 주면 감사 데이터 모두 기록 … 클라이언트 정보 자세하게 … 읽기, 수정, 삭제, 생성 모두" · "필터 기능도 잘 만들고 워크스페이스의 권한이 있는 사용자는 그 이력을 볼 수 있어야" · "키 발급 이력도 워크스페이스 관리자가 확인 … 익스파이어도 시킬 수 있어야. 키는 삭제 개념은 없어" | ADR-022 · FR-MCP-001 · 006 · 010 |
| **시스템 관리자는 시스템만 (11-22)** | "moai-works 에도 시스템 어드민 계정도 있어야 해" · "시스템 어드민 계정은 워크스페이스 생성할 수 없어" · "시스템만 관리할 수 있어야 해서 그 영역도 구분" · "팀 워크스페이스는 시스템 어드민이 사용 못 하게 막을 수도 있어" · "다만 삭제는 사용 중지 후에만" | ADR-023 · FR-WSP-019 · 020 |
| **MCP 키는 계정에 · 앞부분 mai-wks- (11-22)** | "MCP 키는 당연히 사용자 계정하고 연결되고, 키 이름은 moai-works MCP 키 앞에 프리픽스는 mai-wks-xxxxxx 이런 식" — 모든 키(개인 · 서비스)에 주인 계정, 원문은 `mai-wks-` + 43자 | FR-MCP-001 · `McpKeyTokenCodec` |
| **moai-wiki 와 보안 소통 (11-22)** | "moai-wiki 에게도 이 부분은 동일하게 알려줘" · "둘은 이제부터 이런 보안적인 소통하면서 해야 해" — 보안 결정은 moai-wiki 세션에 SendMessage 로 알린다 | CLAUDE.md |
| 말하기 (moai-wiki 11-20) | 사용자에게 답할 때 약어 · 코드(ADR-003, FR-MCP-001 같은)를 그대로 쓰지 않는다. 무엇인지 말로 풀어 쓴다 | — |

## 3. 지금 상태

| 영역 | 상태 |
|---|---|
| 코드 — api | 토대(moai-wiki 에서 가져옴) + **프로젝트**(`project/` — moai-wiki 의 `wiki/` 자리: `@ProjectApi` · `ProjectContext` · `ProjectAccessService` · 만들기 · 설정 · 보관 · 복원 · 삭제 · 내 프로젝트) + **프로젝트 역할**(넷, Keycloak 조직 그룹 `/projects/<id>/<role>` 먼저 · 사본 판정 · 권한 상승 방지) + **일감**(`issue/` — 유형 시드 넷 · 프로젝트 안 순번 · 상태 넷과 완료일 · 종료일 자동 · 관리자 · 담당자 · 다섯 날짜 · 상위 · 하위 10단계 · 연결 넷 · 이력(`issue_journal`) · 댓글 · 지우기 · 목록 필터와 정렬 · 동시 수정 409 · 쓰기 여섯 가지 감사). 권한 코드 25개, 기본 역할 owner · admin · curator · member. Flyway V1 · V2 |
| 코드 — web-frontend | 토대 화면 + **프로젝트**(`features/project` — moai-wiki `features/wiki` 자리: 목록 · 만들기 · 설정 · 보관함 · 지우기 · 프로젝트 홈 · 멤버) + **일감**(`features/issue` — `features/page` 자리: 목록 · 새 일감 · 보기 · 고치기 · 상태 · 하위 · 연결 · 이력 · 댓글 · 지우기 · Markdown) |
| 코드 — MCP | 고정 입구 `/mcp/project` 와 골격 도구 하나뿐. 4단계에서 moai-wiki 체계(워크스페이스 엔드포인트 · 키)로 |
| 검사 | api `./mvnw -q -o package` 99건 · web-frontend lint · typecheck · test 98건 · build 통과 · 문서 검사 0건(2026-11-22). ego-browser 로 프로젝트 · 일감 화면을 데스크톱 · 390px · 다크 · 영어로 확인 |
| 로컬 인프라 | MOAI-DB `moai_works`(V2 적용) · MOAI-Keycloak 렐름 `moai-works`(권한 코드 25개) · 인증서 · 시드(계정 10 · 워크스페이스 A · B · C · 프로젝트 · A/web 에 일감 넷). api · web-backend 가 11001 · 11002 에 떠 있다 |
| 문서 | 새 구성으로 다시 썼다 — 요구사항 74개(`docs/requirements/00~06`), 설계 `docs/design/00~06`, WBS 6단계 28작업(`docs/wbs/`). 진행: 완료 1 · 일부 46 · 할 일 27 |

## 4. 문서 지도

| 알고 싶은 것 | 문서 |
|---|---|
| 무엇을 왜 (moai-wiki → moai-works 대응표) | `docs/01-overview.md` |
| 결정 기록 | `docs/design/00-key-decisions.md` |
| 워크스페이스 · 계정 · 역할(토대) | `docs/design/03-workspace-identity.md` · `SECURITY.md` |
| 프로젝트 · 프로젝트 역할 · 판정 | `docs/design/04-project.md` |
| 일감(유형 · 상태 · 날짜 · 관리자 · 담당자 · 상하위 · 연결 · 이력) | `docs/design/05-issue.md` |
| MCP(키 · 워크스페이스 엔드포인트 · 도구) | `docs/design/06-mcp.md` · `docs/requirements/04-mcp.md` |
| 요구사항이 어디까지 됐나 | `docs/requirements/06-progress.md` |
| 다음 할 일 · 막힌 결정 | `docs/wbs/00-overview.md` · `docs/wbs/08-decisions.md` |

## 5. 로컬 환경 요약

- 포트: api **11001** · web-backend **11002** · web-frontend 개발 서버 **11003** (moai-wiki 는 10001 · 10003 · 10004 — 같은 PC 에서 함께 돈다).
- 같이 쓰는 컨테이너: MOAI-DB(`moai_works`) · MOAI-Redis(`moai-works:`) · MOAI-MinIO(`moai-works-attachments`) · MOAI-OpenSearch(`moai-works-*`) · MOAI-Keycloak(렐름 `moai-works`). 우리 것만 다룬다.
- 비밀값: `infra/keycloak/.env`(관리자 · 클라이언트 비밀값 · 처음 시스템 관리자 · 시드 비밀번호), `src/moai-works-api/.env`(DB · Keycloak · 코드 비밀값), `src/moai-works-web-backend/.env`(Keycloak). 모두 git 제외.
- 개발 데이터 다시 만들기: `python3 -I infra/dev-seed/reset_workspaces.py --yes` → `python3 -I infra/dev-seed/seed_workspaces.py`.
- JDK: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`. Maven 은 오프라인(`-o`)으로도 빌드된다.
- 상세: `DEVELOPE.md §5`.

## 6. 미결정 사항

`docs/wbs/08-decisions.md` 가 정본이다. 제안대로 진행 중인 것: 기본 역할 이름 "큐레이터" 유지 · 연결 일감은 같은 프로젝트 안만 · 상태와 일감 유형은 고정 · 관리자와 담당자는 각각 한 명.

## 7. 다음 작업

1. **2 · 3단계 남은 확인** — 브라우저로 프로젝트 만들기 · 보관 · 복원 · 지우기, 멤버 화면의 역할 주기, 목록 새로 고침, 두 창 충돌 알림. 그리고 설계의 "아직(계획)" 항목(`GET …/people` · 이력 이어 받기 · 보기의 `myActions` · 프로젝트를 지울 때 Keycloak 그룹 정리 · 떠난 멤버 표시).
2. **남은 api 시험** — 요구사항 진행 줄의 "남은 것"(동시 만들기 · 실패 주입 · 대사 등).
3. **4단계 MCP** — 키를 워크스페이스에 묶기 · 권한 · 감사(키는 만료만, 지우기 없음), 워크스페이스 엔드포인트, web-backend 중계(바깥은 11002), 읽기까지 모든 접근 기록 · 보기 · 거르기, 읽기 · 쓰기 도구 → Claude Code 로 시연.
3-1. **1.8 팀 워크스페이스 사용 중지 · 삭제(시스템 관리자)** — 삭제는 사용 중지한 뒤에만.
4. 1단계 나머지 화면 확인(가입 · 초대 · 가입 신청 · 역할 · 시스템 관리).

## 8. 알아 둘 점

- **같은 기능은 moai-wiki 코드를 먼저 본다.** 프로젝트 = moai-wiki 의 위키(`wiki/`), 일감 = 페이지(`page/`), 일감 유형 = 페이지 유형, 관리자 = 페이지 소유자, 상하위 = 페이지 트리, 연결 = 페이지 링크, 이력 = 리비전, 댓글 = 피드백.
- 토대 코드는 moai-wiki 와 같다. 토대를 고치면 moai-wiki 에도 같은 고침이 필요한지 보고 작업 기록에 적는다.
- **moai-wiki 에 아직 없는 것을 moai-works 가 먼저 만들었다:** 프로젝트 역할(= moai-wiki 설계의 위키 역할 — Keycloak 그룹, 사본 `project_role_binding`, 그룹에 클라이언트 역할 매핑 없음, 권한 상승 방지). moai-wiki 가 위키 역할을 만들 때 이 모양을 그대로 쓰면 된다.
- 기존 워크스페이스에는 2026-11-22 이전의 기본 역할 셋만 있었다 — 큐레이터를 되살리며 개발 데이터를 지우고 다시 만들었다(카탈로그 맞추기는 기본 역할을 새로 만들지 않는다).
- ⚠️ MCP 키 발급 API(`/moai-works-api/mcp-keys`)는 워크스페이스에 묶이지 않고, 발급 권한을 보지 않고, 감사에 남지 않는다(moai-wiki 와 같다). 4단계에서 고친다(`docs/requirements/04-mcp.md`).
- "오늘"(완료일 · 종료일 자동 채움)은 Asia/Seoul 고정이다(`IssueService.ZONE`).
- 화면 빌드 결과는 `src/moai-works-web-backend/src/main/resources/static/` 에 생긴다(git 제외). web-backend `.env` 의 `MOAI_WORKS_WEB_STATIC=file:src/main/resources/static/` 이라 다시 빌드하면 바로 반영된다.

## 9. 작업 기록

| 날짜 | 한 일 | 검증 |
|---|---|---|
| 2026-11-21 | **피봇.** 옛 구조(`moai-works-api` · `moai-works-web` · `moai-works-ai` 빈 골격, `docs/architecture.md` · `requirements-roadmap.md`, `.claude/requirements` 명세 143개 · `.claude/skills`)를 지웠다. moai-wiki 의 api · web-backend · web-frontend · infra(keycloak · tls · dev-seed · minio) · tools 를 가져와 이름을 바꿨다(`moai-wiki`→`moai-works`, 패키지 · 설정 키 · 헤더 · 쿠키 · DB · 렐름 · 버킷 · 포트 1001x). 위키 · 페이지 · 자료실 · 정제 · 내장 agent 코드와 화면을 걷어 냈다. 기본 역할에서 curator 를 뺐다. 권한 카탈로그를 토대 16개로 줄였다. MCP 입구를 `/mcp/project` 하나로. Flyway 를 `V1__foundation.sql` 하나로 합쳤다. MOAI-DB 에 DB · 계정 `moai_works` 를 만들었다. 문서를 moai-wiki 형식으로 새로 썼다. moai-wiki 에 고칠 것: 없음(가져오기만 했다) | api `./mvnw -q -o package` 66건 통과 · web-backend 빌드 · web-frontend lint · typecheck · test 60건 · build 통과 · 문서 검사 0건(요구사항 254개 · 진행 줄 문제 0). Keycloak 렐름 · 실제 로그인 · 화면 · MCP 클라이언트 연결은 확인하지 않았다 |
| 2026-11-21 | **포트 변경.** 사람이 정한 대로 api 11001 · web-backend 11002 · web-frontend 개발 서버 11003 으로 바꿨다(설정 기본값 · `bin/startup.sh` · `bin/shutdown.sh` · vite 설정 · 초대 링크 기본 주소 · 시드 · 문서). 새 포트가 이 Mac 에서 비어 있는 것을 확인했다. Keycloak 렐름에는 포트가 들어간 주소가 없다(패스워드 그랜트). moai-wiki 에 고칠 것: 없음 | api 66건 통과 · web-backend 빌드 · web-frontend lint · typecheck · test 60건 · build 통과 · 문서 검사 0건. 서비스를 띄워 새 포트로 접속해 보지는 않았다 |
| 2026-11-22 | **렐름 · 로그인 확인.** MOAI-Keycloak 관리자 계정을 moai-wiki 의 `infra/keycloak/.env` 에서 두 줄만 옮겨 `init-realm.sh` 로 렐름 `moai-works` 를 만들었다(클라이언트 비밀값 · 처음 시스템 관리자는 `.env`). 인증서를 만들고, api · web-backend 의 `.env` 에 Keycloak 연결값 · 코드 비밀값을 넣어 띄웠다(Flyway V1 적용, 권한 코드 16개가 Keycloak 과 일치). 시드를 돌렸다. ego-browser 로 로그인을 확인해 로그인 요구사항을 완료로 바꿨다. moai-wiki 에 고칠 것: 없음 | 세션 쿠키 HttpOnly · Secure · 로그인 때 id 교체 · 응답 · 저장소에 토큰 없음 · CSRF 없는 쓰기 403 · 로그아웃 뒤 Keycloak 세션 종료와 401 · Keycloak 10번 실패 잠금(시험한 두 계정은 풀었다) · web-backend 11번째 시도 429 · `account.login` 감사. 토큰 만료 갱신은 기다려 보지 않았다 |
| 2026-11-22 | **moai-wiki 와 같은 구조로 다시 정리 · 프로젝트 · 일감 개발.** 사용자 지시("위키냐 프로젝트냐 차이만")대로 요구사항(254 → 74) · 설계 · WBS 를 다시 썼다. api: 권한 코드 9개 · 큐레이터 되살림 · `project/`(moai-wiki `wiki/` 와 같은 규칙) · 프로젝트 역할(Keycloak 그룹 먼저) · `issue/`(유형 · 순번 · 상태와 날짜 자동 · 관리자 · 담당자 · 다섯 날짜 · 상하위 · 연결(이력에 방향) · 이력 · 댓글 · 지우기 · 목록 · 쓰기 감사) · `V2__project_issue.sql`. 화면: `features/project` · `features/issue`(moai-wiki 의 위키 · 페이지 화면 구조). 실제 렐름에 권한 코드 9개를 넣고 개발 데이터를 다시 만들었다(시드에 프로젝트 · 일감 · 프로젝트 역할 추가, 지우기 스크립트가 새 표를 지운다). moai-wiki 에 고칠 것: 위키 역할을 만들 때 moai-works 의 프로젝트 역할 모양(그룹 · 사본 · 매핑 없음 · 권한 상승 방지)을 쓰면 된다 | api 99건 · 화면 98건 · lint · typecheck · build · 문서 검사 0건. ego-browser: 홈 · 프로젝트 홈 · 일감 보기 · 새 일감 · 고치기 · 상태 완료(완료일 자동) · 이력(만들기 첫 줄 · 차단됨) · 멤버 화면 · 멤버의 단추 숨김 · 390px · 다크 · 영어. 확인 안 함: 화면의 프로젝트 만들기 · 보관 · 지우기 흐름, 두 창 충돌 |
| 2026-11-22 | **보안 결정 반영 · 시스템 관리자 분리.** 사용자 결정 넷(MCP 는 web-backend 중계 · MCP 접근은 읽기까지 전부 기록하고 감사 권한자가 걸러 봄 · 키는 지우지 않고 만료만 · 시스템 관리자는 시스템만)을 요구사항(76개 — MCP 중계 · 접근 기록 보기 둘 추가) · 설계(ADR-021 ~ 023, MCP 설계에 중계 · 접근 기록 절) · SECURITY · WBS(1.7 · 1.8 · 4.6 ~ 4.8)에 반영. 코드: 접근 판정(`AccessInterceptor` — `@UserApi` 기본 막기 · 워크스페이스 404) — 시스템 관리자는 로그인 때 개인 워크스페이스를 만들지 않고 팀 만들기 · 가입 신청 · 초대 수락 403, 화면은 시스템 관리 화면으로. 개발 데이터를 다시 만들어 시스템 관리자의 워크스페이스를 없앴다. **moai-wiki 세션에 두 번 알림**(중계 결정 · 감사 · 키 · 시스템 관리자 결정) | api 102건(SystemAdminRuleTest 3건 추가) · 화면 98건 · lint · typecheck · build · 문서 검사 0건. ego-browser: 시스템 관리자 로그인 → 계정 관리 화면, 워크스페이스 메뉴 없음 |
| 2026-11-22 | **moai-wiki 회신에 맞춤.** moai-wiki 가 같은 결정을 더 단단한 모양으로 넣어 그대로 따랐다: 시스템 관리자 판정을 접근 판정 한 곳으로(`@UserApi` 기본 막기 403 `SYSTEM_ADMIN_NOT_ALLOWED` · 내 정보 · 로그인 · 이름만 `systemAdmin = true` · 워크스페이스 · 프로젝트 입구 404 · 허용 수 409 `SYSTEM_ADMIN_ACCOUNT` · MCP 키 발급 403 · 계정 관리의 남은 멤버십 수), 화면(돌아갈 주소 무시 · 워크스페이스 주소 안내 · "시스템 관리" 이름 · 남은 멤버십 표시). 앞서 넣은 입구별 검사는 되돌렸다. MCP 키 열 이름을 moai-wiki 와 맞췄다(`expired_by` · `expire_reason`). moai-wiki 에 회신(내부 워커 없음 · 보안 담당 역할 분리는 같이 나중에 · 이름 같음) | api 104건(SystemAdminRuleTest 5건 — moai-wiki 시험을 프로젝트로 옮김) · 화면 98건 · lint · typecheck · build · 문서 검사 0건. ego-browser: 시스템 관리자 로그인(워크스페이스로 돌아갈 주소 무시) → 계정 관리, 워크스페이스 주소 → 안내 |
| 2026-11-22 | **MCP 키 앞부분 · 주인 · api 직접 MCP.** 키 원문 앞부분을 `mwk_mcp_` → `mai-wks-` 로 바꿨다(`McpKeyTokenCodec`, 시험 2건). 모든 키가 사용자 계정에 묶인다는 결정과, "MCP 는 api 에서도 서비스 가능 — web-backend 는 프록시일 뿐" 정정을 문서에 반영. moai-wiki 에 정정 · 키 결정을 알림 | api 106건 · 문서 검사 0건 |
| 2026-11-22 | **MCP 키 = 주인 권한 안에서만 · 서비스 키 없앰.** 사용자: "MCP 키는 사용자 계정과 연결되어 있으며 사용자 권한 범위만 접근 가능해야 한다". ADR-024 를 더하고 요구사항 · 설계 · 데이터 모델 · SECURITY · 계획을 고쳤다(키 한 가지, 범위 = 주인의 권한 ∩ 엔드포인트, 읽기 방식 없음, `mcp.key.issue.service` 는 쓰지 않음 — MCP 단계에서 moai-wiki 와 함께 뺀다). 코드: 서비스 계정은 키의 주인이 될 수 없다(403 `USER_ONLY`, moai-wiki 가 알려 준 구멍). moai-wiki 와 회신을 주고받음(키 앞부분 moai-wiki `mai-wi-`, X-Forwarded-For 는 중계를 만들 때 함께 고침) | api 109건(McpKeyRuleTest 3건) · 문서 검사 0건 |
