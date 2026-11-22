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
| **일감 리비전 · 작업 시간 (11-22)** | "아이템들은 리비전을 모두 관리 … 타임라인 … git 처럼 diff" · "누가 수정했는지도" · "작업 시간 정보 … 예상 공수로 계획 … 소요 시간도 중간중간 추가 … 다건" · "담당자가 아니더라도" · "소요 시간 입력도 누가 했는지" · (moai-wiki 세션에 잘못 적어 전달받음) "변화에 대한 건 밀리세컨드까지" · "완료 기한 · 시작일 · 완료 예정일 · 완료일 · 종료일은 날짜까지만" · "공수 정리 및 실제 투입 공수는 소요시간으로 … 매우 중요한 데이터" | ADR-025 · FR-ISS-015 ~ 017 |
| **moai-wiki 와 보안 소통 (11-22)** | "moai-wiki 에게도 이 부분은 동일하게 알려줘" · "둘은 이제부터 이런 보안적인 소통하면서 해야 해" — 보안 결정은 moai-wiki 세션에 SendMessage 로 알린다 | CLAUDE.md |
| 말하기 (moai-wiki 11-20) | 사용자에게 답할 때 약어 · 코드(ADR-003, FR-MCP-001 같은)를 그대로 쓰지 않는다. 무엇인지 말로 풀어 쓴다 | — |

## 3. 지금 상태

| 영역 | 상태 |
|---|---|
| 코드 — api | 토대(moai-wiki 에서 가져옴) + **프로젝트**(`project/` — moai-wiki 의 `wiki/` 자리: `@ProjectApi` · `ProjectContext` · `ProjectAccessService` · 만들기 · 설정 · 보관 · 복원 · 삭제 · 내 프로젝트) + **프로젝트 역할**(넷, Keycloak 조직 그룹 `/projects/<id>/<role>` 먼저 · 사본 판정 · 권한 상승 방지) + **일감**(`issue/` — 유형 시드 넷 · 프로젝트 안 순번 · 상태 넷과 완료일 · 종료일 자동 · 관리자 · 담당자 · 다섯 날짜 · 상위 · 하위 10단계 · 연결 넷 · 이력(`issue_journal`) · 댓글 · 지우기 · 목록 필터와 정렬 · 동시 수정 409 · 쓰기 여섯 가지 감사). 권한 코드 25개, 기본 역할 owner · admin · curator · member. Flyway V1 · V2 |
| 코드 — web-frontend | 토대 화면 + **프로젝트**(`features/project` — moai-wiki `features/wiki` 자리: 목록 · 만들기 · 설정 · 보관함 · 지우기 · 프로젝트 홈 · 멤버) + **일감**(`features/issue` — `features/page` 자리: 목록 · 새 일감 · 보기 · 고치기 · 상태 · 하위 · 연결 · 이력 · 댓글 · 지우기 · Markdown) |
| 코드 — MCP | 기능별 고정 입구 둘(2026-11-23) — 일감 `/mcp/issue`(도구 15개: 찾기 · CRUD · 댓글 · 작업 시간 · 연결 · 첨부 티켓 · 템플릿 읽기) · 프로젝트 관리 `/mcp/project`(`list_projects` · 일감 템플릿 관리 셋). 키 하나로 두 입구 모두(키는 입구에 묶지 않음). 워크스페이스 엔드포인트 · 접근 기록은 아직 |
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
- 비밀값: `infra/keycloak/.env`(관리자 · 클라이언트 비밀값 · 시스템 관리자와 시드 계정의 **처음** 비밀번호 — 스크립트는 없는 계정만 만들고 이미 있는 계정의 비밀번호는 바꾸지 않는다, 2026-11-22 사용자 확인), `src/moai-works-api/.env`(DB · Keycloak · 코드 비밀값), `src/moai-works-web-backend/.env`(Keycloak). 모두 git 제외.
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
- MCP 키는 2026-11-22 워크스페이스 · 주인 계정에 묶었다(V5) — 발급 · 관리자 전체 보기 · 만료시키기 · 감사 · 떠날 때 만료 · 지우기 금지. 2026-11-23 입구를 기능으로 나눴다 — 일감 `/mcp/issue` · 프로젝트 관리 `/mcp/project`, 키는 입구에 묶지 않는다(V11). 개인 등록은 `claude mcp add --scope user …`(전역 — 범위를 빼면 명령을 실행한 폴더에만). 이 Mac 의 등록은 사용자 범위 `moai-works-issue` · `moai-works-project` 다. 엔드포인트 표는 4.2.
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
| 2026-11-22 | **일감 리비전 · 차이 · 작업 시간 · 밀리초.** V3(`issue_revision` 추가 전용 · 예상 공수 · 판 번호 · `issue_work_log`) · V4(작업 시간 기록 행 삭제 · 값 변경 금지). 판 목록 · 한 판 · 차이 API(moai-wiki `LineDiff` 옮김), 작업 시간 적기 · 지우기(누가 · 지운 사람 · 이력 · 감사), 앱 시계를 밀리초 단위로. 요구사항 FR-ISS-015 ~ 017 · 설계 · ADR-025 · WBS 3.6 ~ 3.8. 화면은 만드는 중. moai-wiki 가 전해 준 사용자 지시(밀리초 · 날짜만 · 소요 시간 중요)를 반영 | api 121건(IssueRevisionTest 7건 · LineDiffTest) · 문서 검사 0건 |
| 2026-11-22 | **리비전 · 작업 시간 화면.** 일감 리비전 · 차이 화면(타임라인 · git 같은 차이 · 두 판 견주기 · 한 판 전체, moai-wiki 페이지 이력 화면 구조), 일감 보기의 예상 공수 · 쓴 시간 · 작업 시간 목록 · 적기 · 지우기, 이력의 판 번호, 변화 시각 밀리초 표시. 개발 데이터 리셋 스크립트는 리비전 · 작업 시간의 지우기 보호를 잠시 끄고 지운다 | 화면 140건 · lint · typecheck · build · api 121건 · ego-browser(데스크톱 리비전 · 차이 · 일감 보기, 390px 다크). 확인 안 함: 영어, 브라우저에서 작업 시간 적기 · 지우기 |
| 2026-11-22 | **MCP 키 발급 · 관리 + MCP 중계.** 사용자: "일단 키 발급 및 관리 시스템 만들고 관리자는 그걸 모두 보고 관리할 수 있어야". V5 — 키를 워크스페이스 · 주인에 묶고 지우기 금지 트리거, 발급 · 목록(관리자는 모두) · 만료시키기 · 내 키 API, 발급 · 만료 감사(IP · User-Agent), 떠나면 만료, 감사 사람별 거르기, web-backend `/mcp/**` 중계, 믿는 중계만 X-Forwarded-For. moai-wiki 에 알림 | api 131건 · 11002 로 MCP 연결 · 도구 호출 · 만료 뒤 401 |
| 2026-11-22 | **알림.** 사용자: "알림 시스템도 만들어야 해. 알림해야 할 게 뭔지 도출해 보고 만들어". moai-wiki 알림함과 같은 표 · API(V6) + 종류 21(일감 · 작업 시간 · 프로젝트 역할 · MCP 키) · 아침 알림(기한 임박 · 지남 · 키 만료 예정, 매일 09:00, 권고 잠금) · 종류별 받기 설정(끌 수 없는 셋). moai-wiki 가 짚은 '시간만으로 줄 세우기'를 보다가 감사 · 알림함 더 보기가 같은 밀리초 줄을 건너뛰는 버그를 찾아 시각 + id 커서로 고침 | api 시험 NotificationTest 11건. 화면은 아직 |
| 2026-11-22 | **상태 · 유형 · 소유자 · 개인 설정 대화 상자 · 알림 화면.** 상태 등록 · 진행 · 테스트 · 완료 · 종료(V7), 유형 새기능 · 버그 · 테스트 · 기술조사(V8, 옛 둘은 더 쓰지 않음), 워크스페이스 소유자는 모든 프로젝트의 관리자(고정), 계정 메뉴 › 개인 설정 대화 상자, 알림 종 · 알림함 · 알림 설정 | api 143→ · 화면 241 · ego-browser |
| 2026-11-22 | **MCP 일감 CRUD · 첨부 · 티켓 · dogfood.** MCP 도구 14개(찾기 · CRUD · 지우기 이유 필수 · 댓글 · 작업 시간 · 연결 · 첨부 넷), 찾기 탄탄(설명 · 기한 · 정렬 · hasNext), 상태 정렬 차례, 첨부(V9 · 저장소 구조 · 이미지 판별 · 받기 머리글), 일회용 티켓(V10 · /mcp/files/*), MinIO 첨부 버킷 준비. 사용자 지시로 이 세션도 moai-works 의 `moai-works` 프로젝트에 일감을 등록하고 진행한다(MCP, 키 moaiworks02) — moai-wiki · echomind-world 세션도 같은 도구 사용 | api 159 · 실제 11002 로 올리고 받기 · MinIO 객체 이름 확인. 첨부 화면은 만드는 중 |
| 2026-11-23 | **MCP 도구 목록 문서.** 사용자: "mcp 툴 목록 문서로 정리하고 알려줘". [MCP 설계 §8 도구](docs/design/06-mcp.md#mc-tools)를 처음 설계안(11개)에서 지금 도구 14개로 다시 씀 — 도구마다 서버가 보내는 설명 원문 · 입력(필수 여부) · 결과 · 권한, 첨부 티켓 흐름, 지금 오류 모양 · 자주 나는 code, 아직 없는 도구(옛 설계 get_project · unlink_issues · set_parent 는 남김). 따로 만들었던 도구 목록 문서는 사용자 지시("8. 도구 여기에")로 §8 에 합침. "도구는 list_projects 하나"로 남아 있던 SECURITY · api 모듈 · MCP 요구사항 줄을 고침 | 돌아가는 서버의 tools/list 로 14개 · 필수 입력이 문서와 같은지 확인 · check_docs 0건 |
| 2026-11-23 | **MCP 입구 나누기.** 사용자: "moai-works 의 issue 의 mcp 엔드포인트가 따로 있어야 해. 즉 다른 관리 기능과 이슈 관리 기능을 서로 구분하고 싶은 거야" · "프로젝트 생성이나 삭제 등등은 관리 기능" · "키는 권한만 있으면 어떤 mcp 엔드포인트든 연결 가능해야 해" · "전역으로 등록해야 할 것 같은데". 입구를 일감 `/mcp/issue`(도구 14개) · 프로젝트 관리 `/mcp/project`(`list_projects` 만)로 나눔, V11 — 키의 `endpoint` 열을 지우고 보호 트리거를 다시 만듦(키 하나로 어느 입구든, 판정은 도구마다 키 주인 권한), 없는 입구는 키보다 먼저 404 `MCP_ENDPOINT_NOT_FOUND`(두 입구 안내), 키 발급 대화 상자가 두 주소 · `claude mcp add --scope user` 명령 둘을 보임, 이 Mac 의 등록을 홈 폴더(로컬 범위)에서 사용자 범위로 옮김. [ADR-028](docs/design/00-key-decisions.md#adr-028) | api 161(새 시험 2) · 화면 275 · lint · typecheck · build · 11002 로 issue 14 · project 1 · /mcp/manage 404 · moai-works 폴더에서 `claude mcp list` 둘 다 연결됨. 확인 안 함: 브라우저에서 새 연결 안내 |
| 2026-11-23 | **Claude 스킬 `moai-works-issues`.** 사용자: "스킬도 만들어 줘. 이 프로젝트에부터 스킬 만들어서 추가해 줘" · "이슈의 경우에는 처리 프로세스를 잘 기술해 줘". `.claude/skills/moai-works-issues/SKILL.md` — 입구(`/mcp/issue` · 전역 등록), 처리 순서(등록 → 진행 → 테스트 → 완료 → 종료)마다 언제 옮기고 무엇을 남기는지, 기록 규칙(rowVersion · 설명 통째 바꾸기 · 작업 시간), 찾기 · 쪽, 첨부 티켓, 하지 않는 것, 오류, 예. CLAUDE.md "필요할 때 읽을 문서"에 한 줄 | skill-creator 형식 검사 통과 · 이 스킬의 일감(#12)을 스킬대로 처리. 사용자 지시("스킬도 전역으로 넣어 줘")로 `~/.claude/skills/moai-works-issues` 를 이 저장소 폴더로 링크했다 — 원본은 저장소 하나, 모든 프로젝트 세션에서 보인다 |
| 2026-11-23 | **유형별 일감 템플릿(서버 · MCP).** 사용자: "이슈의 유형별로 작성 템플릿 넣어 주고 그걸 관리할 수 있게 해 줘. 또한 mcp 제공해서 그에 맞게 이슈 작성 및 수정할 수 있게". 결정: 워크스페이스 + 프로젝트 · MCP 관리 도구도 · 빠진 항목은 알려만. V12 `issue_template`(프로젝트 → 워크스페이스 → 처음 템플릿), REST(워크스페이스 기본 · 프로젝트 덮어쓰기 · 되돌리기, `workspace.settings.manage` · `project.manage`, 409, 감사), 빠진 필수 항목 계산(제목 = 항목, "(선택)"), MCP 일감 입구 `get_issue_template` + 결과 `templateCheck`, 프로젝트 관리 입구 `list/set/reset_issue_template`, `McpScope`(MCP 범위 판정 한 곳). 스킬 · CLAUDE.md 에 템플릿을 따르기 · 일감 관리 규칙 | api 168건 · 11002 로 두 입구 도구 수(15 · 4) · 템플릿 읽기 확인. 화면(템플릿 관리 · 채우기 · 경고)은 편집기 작업 뒤 |
| 2026-11-23 | **가운데 내용을 넓게 · 주 메뉴 접기.** 사용자: "컨텐츠 화면이 너무 비좁아. 숨길 건 숨기고 보일 건 보이고 가운데 이 부분이 넓게" · "왼쪽 메인 메뉴도 닫았다 열었다 하고, 모든 화면은 반응형으로". 주 메뉴 접기(68px 아이콘 막대 · 기억 · lg 아래 처음엔 접음, 수치는 moai-wiki 와 같다), 프로젝트 메뉴를 본문 위 한 줄로, 일감 보기 — [칩 … 동작] 아래 제목 한 줄 · 속성은 받은 너비 1024px 이상이면 오른쪽 272px 아니면 제목 아래 요약 · 본문 1200px 까지 · 빈 하위/연결 한 줄 · 작업 시간 양식 접기, 본문 여백 16 · 24 · 32 · 40 | 화면 275 → · ego-browser 1440 · 1280 · 768 · 390 · 다크 · 영어, moai-wiki 의 반응형 검사 스크립트로 화면 15곳 × 5 너비 × 메뉴 편/접 = 150건 가로 넘침 · 눌린 글자 0 |
| 2026-11-23 | **Markdown 편집기.** 사용자: "이렇게 md 편집기를 만들고 이슈의 본문이나 덧글은 모두 md 로 작성할 수 있게 해 줘". Milkdown Crepe 7.23.0(따로 받는 코드) + 위 고정 도구 막대 · 목차 · Markdown 원문(CodeMirror), 일감 설명 · 댓글 모두. 원문 보존은 moai-wiki 의 source-blocks 사본(baseline · preserveSource). 블록 그림(이름 자리에 비율을 적음) · 수식 · AI · 위 막대 · 블록 메뉴 · 떠 있는 막대는 끔. 첨부 그림 보기 · 붙여 넣기 · 끌어 놓기 · 단추 올리기, Markdown 을 넣을 때 DOM 을 거치지 않음(문서 밖 `<img>` 받기 · 제목 덧붙임 문제), 편집 중 링크 열지 않음 · 링크 주소 규칙. 쓰기/미리보기 탭은 없앰 | 화면 396건(원문 보존 — 실제 글 39 + 어려운 경우 16 열자마자 저장 = 원문, 고친 덩어리만 바뀜) · lint · 타입 · 빌드. ego-browser(11002 CSP): 위반 0, #2 · #5 · #11 · #12 원문 보기 = 저장된 글, 도구 막대 · 할 일 · 링크(javascript: 거절) · 그림 올리기, mermaid 12.3.0 도 CSP 에서 그려짐, 다크 · 390px. 확인 안 함: 영어 화면의 편집기 |
