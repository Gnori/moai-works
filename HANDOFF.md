# 세션 핸드오프 — moai-works

| 항목 | 내용 |
|---|---|
| 마지막 작업일 | 2026-11-21 |
| 단계 | **피봇 직후.** 그룹웨어 명세를 지우고 Redmine 같은 프로젝트 관리 시스템으로 다시 정리했다. AI Agent 는 내장하지 않고 MCP 로 연다. moai-wiki 의 토대(계정 · 워크스페이스 · 권한 · 로그인 · 화면 뼈대)를 가져와 api 66건 · 화면 60건 테스트가 통과한다. 요구사항 · 설계 · WBS 를 새로 썼다. 프로젝트 관리 기능 코드는 아직 없다 |
| 다음 세션 시작 방법 | "HANDOFF.md 읽고 이어서 하자"라고 요청. 다음 작업은 `docs/wbs/00-overview.md`. 규칙은 `CLAUDE.md` → `DEVELOPE.md` · `SECURITY.md` · `WEB-DESIGN.md` · `docs/modules/` |

---

## 1. 프로젝트 한 줄 요약

Redmine 같은 **프로젝트 관리 시스템**(멀티 워크스페이스 SaaS). 사람은 웹 화면으로, **바깥 AI Agent(Claude Code 등)는 MCP 로** 같은 api 의 같은 권한 판정을 거쳐 프로젝트 · 일감 · 계획 · 시간 · 위키를 다룬다.

## 2. 사용자가 확정한 사항

| 결정 (날짜) | 내용 | 문서 |
|---|---|---|
| 피봇 (11-21) | "이 프로젝트를 레드마인 같은 프로젝트 관리 시스템으로 전면 정리" — 그룹웨어 명세(143개)를 지웠다. git 이력 `5ba3a92` 에 남아 있다 | ADR-001 |
| AI (11-21) | "AI Agent 자체는 내장할 계획 없음. AI Agent 가 프로젝트를 관리할 수 있게 MCP 로 연결" · "moai-works-ai 는 제거" | ADR-003 |
| 구조 (11-21) | "moai-works-web-backend · moai-works-web-frontend 로 만들고 moai-wiki 디렉터리 구조로. src 아래 모듈, 디펜던시 · 인증 · 역할 권한 · 워크스페이스도 같게" | ADR-002 |
| moai-wiki 에서 이어받은 것 | 권한 판정은 api 한 곳(접근 어노테이션 하나, 없으면 기동 실패) · 계정 · 멤버십 · 역할의 정본은 Keycloak Organizations · 토큰은 브라우저에 없음 · 모든 서비스 HTTPS · 최신 GA · 반응형(390px) · ego-browser · Docker 안 pull 금지 · 요구사항으로 일하기(진행 줄 · 진행 현황) · 커밋은 JackyLim 으로, AI 이름 · Co-Authored-By 없이, 푸시는 사람이 | CLAUDE.md |
| 포트 (11-21) | "moai-works-api 11001 · moai-works-web-backend 11002 · moai-works-web-frontend 11003(개발)" — 처음 정한 1001x 를 바꿨다 | ADR-014 · `DEVELOPE.md §5.2` |
| 말하기 (moai-wiki 11-20) | 사용자에게 답할 때 약어 · 코드(ADR-003, FR-MCP-001 같은)를 그대로 쓰지 않는다. 무엇인지 말로 풀어 쓴다 | — |

## 3. 지금 상태

| 영역 | 상태 |
|---|---|
| 코드 | moai-wiki 토대를 옮김: api(계정 · 가입 · 워크스페이스 · 역할 · 멤버십 · 초대 · 가입 신청 · 감사 · Keycloak 쓰기 기록 · 권한 카탈로그 · 접근 어노테이션 · MCP 키 · 고정 MCP 입구 `/mcp/project` · 객체 저장소 추상), web-backend(BFF), web-frontend(로그인 · 워크스페이스 · 멤버 · 역할 · 설정 · 감사 · 시스템 관리 화면). 위키 · 페이지 · 자료실 · 워커는 뺐다. 기본 역할은 owner · admin · member |
| 검사 | api `./mvnw -q package` 66건 통과 · web-backend 빌드 통과 · web-frontend lint · typecheck · test 60건 · build 통과(2026-11-21) |
| 로컬 인프라 | MOAI-DB 에 DB · 계정 `moai_works` 를 만들었다(비밀번호는 `src/moai-works-api/.env`). **Keycloak 렐름 `moai-works` 는 아직 만들지 않았다**(같이 쓰는 MOAI-Keycloak, `infra/keycloak/.env` 에 관리자 계정을 넣고 `init-realm.sh`). 인증서 · 시드도 아직 |
| 문서 | moai-wiki 와 같은 위키 형식으로 새로 썼다 — 규칙 문서 · 개요 · 용어집 · 요구사항 15쪽 · 설계 10쪽 · 모듈 4쪽 · 계획 2쪽 · WBS 9쪽(docs 55쪽). **요구사항 254개**(M 125 · S 100 · C 29) — 진행: 일부 24(토대 · 품질 · 언어) · 할 일 230, 수용 기준 918개 중 60개 확인. `python3 -I tools/check_docs.py docs` 0건 |

## 4. 문서 지도

| 알고 싶은 것 | 문서 |
|---|---|
| 무엇을 왜 | `docs/01-overview.md` (Redmine 대응표 포함) |
| 결정 기록 | `docs/design/00-key-decisions.md` |
| 구성 · 흐름 | `docs/design/01-architecture.md` |
| 워크스페이스 · 계정 · 역할(토대) | `docs/design/03-workspace-identity.md` · `SECURITY.md` |
| 프로젝트 권한(프로젝트 역할 4종 · 판정 규칙 · 계획한 권한 코드) | `docs/design/04-project-permissions.md` |
| 일감 · 워크플로 · 사용자 정의 필드 · 질의 | `docs/design/05-issue-tracking.md` |
| **MCP · AI Agent(엔드포인트 · 키 · Agent 계정 · 도구)** | `docs/design/07-mcp-agents.md` · `docs/requirements/12-mcp.md` |
| 요구사항이 어디까지 됐나 | `docs/requirements/14-progress.md` |
| 다음 할 일 · 막힌 결정 | `docs/wbs/00-overview.md` · `docs/wbs/08-decisions.md` |

## 5. 로컬 환경 요약

- 포트: api **11001** · web-backend **11002** · web-frontend 개발 서버 **11003** (moai-wiki 는 10001 · 10003 · 10004 — 같은 PC 에서 함께 돈다).
- 같이 쓰는 컨테이너: MOAI-DB(`moai_works`) · MOAI-Redis(`moai-works:`) · MOAI-MinIO(`moai-works-attachments`) · MOAI-OpenSearch(`moai-works-*`) · MOAI-Keycloak(렐름 `moai-works`). 우리 것만 다룬다.
- JDK: `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`. Maven 은 오프라인(`-o`)으로도 빌드된다(의존성이 로컬 저장소에 있다).
- 상세: `DEVELOPE.md §5`.

## 6. 미결정 사항

`docs/wbs/08-decisions.md` 가 정본이다. 2단계 전에 정하면 좋은 것: 기본 멤버에게 프로젝트 만들기를 줄지 · 보고자에게 시간 기록을 줄지 · 프로젝트 역할 정본을 Keycloak 조직 그룹에 둘지(규모) · Agent 계정을 시트로 셀지.

## 7. 다음 작업

1. **1단계 마무리** — `infra/keycloak/.env` 에 MOAI-Keycloak 관리자 계정 → `init-realm.sh` → `infra/tls/make-certs.sh` → api · web-backend 띄우기 → 시드 → 화면에서 로그인 · 워크스페이스 · 초대 · 역할 확인 → FR-WSP 진행 줄의 남은 체크.
2. **2단계** — 시작 전에 `docs/wbs/08-decisions.md` 의 D-02 ~ D-05(멤버의 프로젝트 만들기 · 보고자의 시간 기록 · 프로젝트 역할 정본 · 공개 프로젝트의 기본 역할)를 사람에게 확인. 그다음 프로젝트(표 · `@ProjectApi` · 프로젝트 역할) → 트래커 · 상태 · 워크플로 시드 → 일감 · 이력 · 메모 · 첨부 → 질의 목록 → 화면.
3. **3단계** — 워크스페이스 MCP 엔드포인트 · Agent 계정 · 핵심 도구 → Claude Code 로 시연.

## 8. 알아 둘 점

- 토대 코드는 moai-wiki 와 같다. 토대(account · workspace · security · keycloak · audit · BFF · 화면 틀)를 고치면 moai-wiki 에도 같은 고침이 필요한지 보고 작업 기록에 적는다.
- `src/moai-works-api/src/main/java/com/moai/works/api/storage` 의 객체 저장소는 아직 쓰는 곳이 없다(첨부를 만들 때 쓴다). MinIO 열쇠가 없어도 api 는 뜬다.
- MCP 입구는 지금 고정 `/mcp/project`(도구 `list_projects` 골격 하나)다. 3단계에서 워크스페이스 엔드포인트 `/mcp/{ws}/{ep}` 로 옮긴다.
- ⚠️ 지금 MCP 키 발급 API(`/moai-works-api/mcp-keys`)는 `@AuthenticatedApi` 라 워크스페이스에 묶이지 않고, 발급 권한을 보지 않고, 발급 · 폐기가 감사에 남지 않는다(moai-wiki 와 같다). 3단계 3.3 에서 고친다(`SECURITY.md §5.2 · §11`, `docs/design/07-mcp-agents.md §28`).
- 설계 쪽은 다섯 갈래로 나눠 썼다(워크스페이스 · 프로젝트/일감 · 계획/협업 · MCP/연동 · 규칙/모듈). 서로 맞춘 것: 오류 `code` 는 대문자 스네이크(잠금 충돌 `CONCURRENT_UPDATE`), 도메인 이벤트 이름은 `design/05 §18` 이 정본, 주체 열 `*_type` + `via`, Agent 정지 상태 `SUSPENDED`, Agent 는 첫 출시에 REST 를 쓰지 않음(MCP 로만).
- 화면 빌드 결과는 `src/moai-works-web-backend/src/main/resources/static/` 에 생긴다(git 제외).

## 9. 작업 기록

| 날짜 | 한 일 | 검증 |
|---|---|---|
| 2026-11-21 | **피봇.** 옛 구조(`moai-works-api` · `moai-works-web` · `moai-works-ai` 빈 골격, `docs/architecture.md` · `requirements-roadmap.md`, `.claude/requirements` 명세 143개 · `.claude/skills`)를 지웠다. moai-wiki 의 api · web-backend · web-frontend · infra(keycloak · tls · dev-seed · minio) · tools 를 가져와 이름을 바꿨다(`moai-wiki`→`moai-works`, 패키지 · 설정 키 · 헤더 · 쿠키 · DB · 렐름 · 버킷 · 포트 1001x). 위키 · 페이지 · 자료실 · 정제 · 내장 agent 코드와 화면을 걷어 냈다. 기본 역할에서 curator 를 뺐다. 권한 카탈로그를 토대 16개로 줄였다. MCP 입구를 `/mcp/project` 하나로. Flyway 를 `V1__foundation.sql` 하나로 합쳤다. MOAI-DB 에 DB · 계정 `moai_works` 를 만들었다. 문서를 moai-wiki 형식으로 새로 썼다. moai-wiki 에 고칠 것: 없음(가져오기만 했다) | api `./mvnw -q -o package` 66건 통과 · web-backend 빌드 · web-frontend lint · typecheck · test 60건 · build 통과 · 문서 검사 0건(요구사항 254개 · 진행 줄 문제 0). Keycloak 렐름 · 실제 로그인 · 화면 · MCP 클라이언트 연결은 확인하지 않았다 |
| 2026-11-21 | **포트 변경.** 사람이 정한 대로 api 11001 · web-backend 11002 · web-frontend 개발 서버 11003 으로 바꿨다(설정 기본값 · `bin/startup.sh` · `bin/shutdown.sh` · vite 설정 · 초대 링크 기본 주소 · 시드 · 문서). 새 포트가 이 Mac 에서 비어 있는 것을 확인했다. Keycloak 렐름에는 포트가 들어간 주소가 없다(패스워드 그랜트). moai-wiki 에 고칠 것: 없음 | api 66건 통과 · web-backend 빌드 · web-frontend lint · typecheck · test 60건 · build 통과 · 문서 검사 0건. 서비스를 띄워 새 포트로 접속해 보지는 않았다 |
