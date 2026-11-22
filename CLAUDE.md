# moai-works

moai-wiki 와 같은 구조의 프로젝트 관리 시스템(멀티 워크스페이스 SaaS) — **위키 대신 프로젝트, 페이지 대신 일감**. 일감에는 유형 · 관리자와 담당자 · 날짜(완료 기한 · 시작일 · 완료 예정일 · 완료일 · 종료일) · 상위 · 하위 · 연결 일감 · 이력 · 댓글이 있다. **AI Agent 를 내장하지 않는다** — 바깥 AI Agent(Claude Code 등)가 **MCP** 로 붙어 사람과 같은 권한 규칙으로 프로젝트 · 일감을 다룬다. 저장소 구조 · 의존성 · 인증 · 워크스페이스 · 역할과 권한 · MCP 체계 · 흐름 · 웹 구조는 형제 저장소 [moai-wiki](../moai-wiki/CLAUDE.md) 와 같다 — 같은 기능은 moai-wiki 코드를 먼저 보고 같은 모양으로 만든다. Redmine 의 나머지 기능은 나중 후보다. 한국어로 일한다(문서 · 주석 · 커밋 메시지).

## 모듈 (`src/`)

| 모듈 | 한 줄 |
|---|---|
| `moai-works-api` | Spring Boot. DB 정본 · **모든 권한 판정** · **MCP 전부** (https :11001) |
| `moai-works-web-backend` | Spring Boot BFF. 로그인 세션(Redis) · 토큰 전달 · 정적 파일만 (https :11002) |
| `moai-works-web-frontend` | React + MUI SPA. 권한은 숨기기만 (개발 서버 https :11003) |

## 꼭 지킬 것

- **AI 를 api 안에 넣지 않는다.** LLM 호출 · 요약 · 분류는 바깥 Agent 몫이다. Agent 가 할 일은 MCP 도구로 연다 — 도구는 REST 와 **같은 서비스 · 같은 판정**을 부른다(모델이 넘긴 프로젝트도 다시 판정).
- **권한 판정은 api 한 곳.** REST 핸들러마다 접근 어노테이션 정확히 하나(없으면 기동 실패). BFF · 화면은 판정하지 않는다. 권한 경계는 **프로젝트**다(moai-wiki 의 위키 자리 — 독자가 아니면 404, 독자인데 권한이 없으면 403).
- **계정 · 멤버십 · 역할의 정본은 Keycloak Organizations.** api 는 Keycloak 에 먼저 쓰고 DB 사본으로 판정한다. Keycloak 클라이언트 이름 = 모듈 이름. Keycloak 은 다른 프로젝트와 같이 쓰는 `MOAI-Keycloak`, 렐름은 `moai-works`.
- **토큰은 브라우저에 없다.** web-backend 세션에만.
- **MCP 는 web-backend 가 중계한다.** 바깥 MCP 클라이언트는 web-backend 포트(11002)로 붙고, api 는 MCP 를 바깥에 열지 않는다. MCP 키로 들어온 요청은 읽기까지 모두 접근 기록에 남기고(클라이언트 정보 포함), 감사 권한(`audit.read`)이 있는 사람이 걸러 본다. 키는 지우지 않고 만료시킨다.
- **시스템 관리자는 시스템만 관리한다.** 워크스페이스가 없고 만들거나 들어갈 수 없다. 팀 워크스페이스 사용 중지 · 다시 사용, 삭제는 사용 중지한 뒤에만.
- **보안 결정은 moai-wiki 세션과 서로 알린다.** 보안 · 인증 · 권한 · MCP · 감사 결정을 하면 moai-wiki 세션에 결정 원문과 적용 방법을 보낸다(사용자 지시 "둘은 이제부터 이런 보안적인 소통하면서 해야 해").
- **비밀값은 git 제외 `.env` 에만.** 문서 · 커밋 · 로그 · 대화에 값을 적지 않는다.
- **모든 서비스는 HTTPS.** 포트는 api 11001 · web-backend 11002 · web-frontend 개발 서버 11003(사람이 정함. moai-wiki 의 1000x 와 겹치지 않는다). 로컬 인증서는 `infra/tls/make-certs.sh`. 인증서 확인을 끄는 설정을 두지 않고, `localhost` 에는 HSTS 를 보내지 않는다([DEVELOPE.md §5.2](DEVELOPE.md#52-포트--https)).
- **버전은 moai-wiki 와 같게, 최신 GA 만**(RC · M · SNAPSHOT 금지). 레지스트리에서 확인하고 올린다.
- **웹 화면은 항상 반응형**(390px 와 데스크톱 모두 확인)이고 창을 꽉 채운다. **브라우저 확인은 ego-browser 로 한다**(Playwright 아님).
- **이 Mac 에서 Docker 안 이미지 pull · build 금지.** skopeo + `docker load`. Docker 재시작은 먼저 묻는다. 같이 쓰는 컨테이너(MOAI-DB · MOAI-Redis · MOAI-MinIO · MOAI-OpenSearch · MOAI-Keycloak)에서는 우리 것(`moai_works` · `moai-works:` · `moai-works-*`)만 다룬다.
- **요구사항으로 일한다.** 작업이 다루는 요구사항의 진행(요구사항 문서의 `진행` 줄 · 수용 기준 체크)을 같은 작업에서 고치고, 요구사항을 끝내거나 진행이 바뀔 때마다 **처리한 결과와 남은 요구사항을 알린다**([요구사항 진행 현황](docs/requirements/06-progress.md) · 방법은 [요구사항 개요 §6](docs/requirements/00-overview.md#6-진행-관리)).
- **끝나면 검사 → 문서 현행화 → 커밋.** 커밋은 내가 하고 푸시는 사람이 한다. 작성자 · 커밋터는 `JackyLim`. AI 이름이나 `Co-Authored-By` 줄은 넣지 않는다.

## 필요할 때 읽을 문서

늘 다 읽지 않아도 되지만, **아래 일을 할 때는 먼저 읽고, 바뀐 것은 같은 작업에서 고친다.**

| 이럴 때 | 읽을 문서 |
|---|---|
| 작업을 이어서 시작할 때 | [HANDOFF.md](HANDOFF.md) — 현재 상태 · 남은 일 · 작업 기록(§9) |
| 다음에 무엇을 할지 · 진척 · 막힌 결정 | [docs/wbs/](docs/wbs/00-overview.md) — 구현한 기능 · 단계별 작업 · 정해야 할 것. 작업을 끝내면 상태를 고친다 |
| 요구사항이 어디까지 됐나 · 무엇이 남았나 | [docs/requirements/06-progress.md](docs/requirements/06-progress.md) — 도구가 만든다. 고칠 곳은 각 요구사항의 `진행` 줄과 수용 기준, 그 뒤 `python3 -I tools/req_progress.py` |
| 공통 규칙 · 로컬 환경 · 포트 · HTTPS · 띄우고 끄기 · 검사 명령 · 커밋 방법 | [DEVELOPE.md](DEVELOPE.md) |
| 로그인 · 세션 · 토큰 · 권한 · Keycloak · 비밀값 · 헤더 · 새 엔드포인트 | [SECURITY.md](SECURITY.md) |
| 화면 · 테마 · 스킨 · 다국어 · 배치 | [WEB-DESIGN.md](WEB-DESIGN.md) |
| 한 모듈의 구조 · 입구 · 명령 | [docs/modules/](docs/modules/00-overview.md) |
| 설계 의도 · 요구사항 · 결정 기록 | [docs/README.md](docs/README.md) (워크스페이스 · 권한은 `docs/design/03`, 프로젝트 · 프로젝트 역할은 `docs/design/04`, 일감은 `docs/design/05`, MCP 는 `docs/design/06`, ADR 은 `docs/design/00`) |

문서를 고쳤으면 `python3 -I tools/check_docs.py docs` → 0건(링크 · 이동 연결 · 요구사항 진행 줄 · 진행 현황이 최신인지).
