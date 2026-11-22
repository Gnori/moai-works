[홈](../README.md) › [모듈](00-overview.md) › moai-works-web-frontend

# moai-works-web-frontend

moai-works 의 웹 화면(React). 빌드 결과는 `../moai-works-web-backend/src/main/resources/static/` 으로 들어가고, [web-backend](02-web-backend.md) 가 정적 파일과 `/api` 를 함께 서비스한다. 화면 규칙(테마 · 스킨 · 다국어 · 반응형)은 [WEB-DESIGN.md](../../WEB-DESIGN.md), 화면 보안은 [SECURITY.md](../../SECURITY.md) §9 에 있다. moai-wiki 의 화면 뼈대를 가져와 위키 · 자료실 화면을 걷어 냈다. 위키 · 페이지 화면 자리에는 프로젝트 · 일감 화면이 같은 구조로 들어온다(계획).

## 1. 하는 일 · 하지 않는 일

| 한다 | 하지 않는다 |
|---|---|
| 로그인 · 가입 · 인증 코드 · 워크스페이스(홈 · 찾기 · 만들기 · 설정) · 멤버 · 가입 신청 · 역할과 권한 · 초대 · 감사 · 시스템 관리(가입 정책 · 계정) 화면 | 권한 판정. 가드 · `<Can>` · 메뉴 숨김은 **표시만** 하고, 판정은 api 가 요청마다 한다 |
| 서버가 계산해 준 `myPermissions` · `platformRoles` 로 메뉴와 단추를 거른다 | 토큰 다루기. 브라우저에는 HttpOnly 세션 쿠키만 있다 |
| 같은 출처의 `/api` 만 부른다 | 개인 정보나 토큰을 브라우저 저장소에 두기 |
| 프로젝트 · 프로젝트 역할 · 일감 화면 (계획 — moai-wiki 의 위키 · 페이지 화면과 같은 구조) | MCP 키 화면 — 아직 없다(워크스페이스 MCP 엔드포인트와 함께 만든다, 계획) |

## 2. 한눈에

| 항목 | 값 |
|---|---|
| 스택 | React 19.3 · React Router 8.5 · MUI 9.5(자체 테마, 스킨 4종) · TanStack Query 5 · axios · react-hook-form + zod 4 · i18next 27.2 + react-i18next 18.0(한국어 · 영어) · sonner · dayjs · Pretendard |
| 들어 있지만 아직 쓰지 않는 의존성 | react-markdown 10.1 + remark-gfm 4.0 · rehype-highlight 7.0 · mermaid 12.3 · CodeMirror 6(state · view · commands · autocomplete · language · lang-markdown · lint) — 일감 설명 · 댓글의 보기와 편집기에 쓴다(계획 — [3.5](../wbs/04-stage-3-issue.md#w-3-5), [WEB-DESIGN.md](../../WEB-DESIGN.md) §3.1) |
| 도구 | Vite 8 · TypeScript 6.0 (typescript-eslint 가 6.0 까지 지원해서 7 이 아니라 6) · ESLint 10 · Vitest 5 (jsdom) |
| Node | 22 이상 |
| 개발 서버 | https://localhost:11003(**HTTPS**, 인증서는 `infra/tls/certs/` — 없으면 뜨지 않고 `infra/tls/make-certs.sh` 를 돌리라고 알려 준다). `/api` 는 web-backend(https 11002)로 넘기고, 넘길 때 로컬 인증 기관으로 인증서를 확인한다. 세션 · XSRF 쿠키가 localhost 에 붙으므로 그대로 동작한다 |
| 빌드 | `tsc -b && vite build` → web-backend `static/` (매번 비우고 다시 쓴다, 소스맵 없음). **화면은 경로 단위로 나눠 받는다**(§5.1) |
| 비밀값 | 없다. 화면에는 비밀값을 두지 않는다 |
| 참고 | SeedSiteKeeper 프론트는 유료 Minimal UI 템플릿이다. 구조만 참고했고 코드는 가져오지 않았다 |

## 3. 구조 (기능 단위)

```
src/
  main.tsx    언어 리소스를 먼저 올리고 App 을 그린다
  app/        App(공급자: 화면 설정 → 쿼리 → 세션 → 확인 대화상자 · 토스트, 401 처리)
              routes(경로 · 권한 · 메뉴의 정본, 화면은 page() 로 나눠 받는다) · router(routes 에서 만든 라우터)
  theme/      tokens(중립색 · 상태색 · 코드 글자색 · 글꼴 · 모서리) · skins(ocean · lagoon · basalt · ember, 라이트/다크)
              create-theme(스킨 × 언어 → MUI theme) · components(컴포넌트 모양) · AppearanceProvider(스킨 · 밝기)
              contrast(+test: 모든 스킨 WCAG AA)
  shared/
    i18n/     i18n(초기화 · 언어 바꾸기 · <html lang> · dayjs) · i18next.d.ts(키 타입) · i18n.test(두 언어 키 같음)
              resources/{ko,en}/{common,auth,workspace,admin,mcp}.json
    api/      http(axios · CSRF · Accept-Language · ApiError — details 에 서버가 준 그 밖의 값) · types · endpoints
              (authApi · signupApi · meApi · workspaceApi · invitationApi · adminApi)
    ui/       PageHeader · States(Loading · Empty · Error · Forbidden) · ConfirmDialog · RoleChips · Brand · VisuallyHidden
    lib/      labels(코드값 → 말 · 권한 코드 묶음) · format(날짜 · 파일 크기 · 머리글자) · storage(localStorage try/catch)
              · post-login-path(+test: 안전한 returnTo · withReturnTo) · use-debounced(치는 동안 서버를 부르지 않게)
  layouts/    AuthLayout(좌우 분할 · 화면 설정 단추) · AppShell(routes 로 만든 사이드바 — 넓은 화면은 아이콘 막대로 접기 · 모바일 서랍)
              WorkspaceSwitcher(받은 초대 수 표시) · AccountMenu(화면 설정 · 로그아웃) · PreferencesDialog(밝기 · 색 · 언어)
  features/
    auth/        session(세션 · 탭 간 동기화 · signedOutByUser) · guards(RequireAuth · GuestOnly · RequirePlatformRole)
                 access(useCan · <Can> · RequireWorkspacePermission — 권한을 여럿 적으면 하나만 있어도 된다) · 로그인 · 가입 · 인증 코드
    workspace/   WorkspaceLayout(/w/:slug 의 틀 — 워크스페이스를 읽어 내려주고 마지막에 쓴 곳으로 기억) · workspace-context
                 queries(쿼리 키 · 역할 이름) · role-rules(+test: 줄 수 있는 역할 · 넣을 수 있는 권한 — api 와 같은 규칙)
                 홈 · 멤버(멤버 / 보낸 초대 탭) · 가입 신청 · 역할과 권한(+RoleEditorDialog: 사용자 정의 역할) · 설정 · 감사 · 찾기 · 만들기
    invitations/ 보내는 쪽: InviteDialog(링크 한 번 보이기 · 복사) · SentInvitations(취소)
                 받는 쪽: InvitationPage(/invitations/:token) · ReceivedInvitations(찾기 화면) · use-invitation-actions(수락 · 거절) · queries
    admin/       가입 정책 · 계정 관리 (system-admin)
    me/          개인 설정 대화 상자 — PersonalSettingsDialog(왼쪽 메뉴) · use-settings-dialog(?settings= 주소) · ProfileSection ·
                 PreferencesSection(PreferencesFields 를 로그인 화면 대화 상자와 함께 씀) · SettingsSectionHeader
    notifications/ describe(+test: 종류 21 → 문장 · 링크) · preferences(+test: 묶음 · 잠금) · queries · NotificationBell(왼쪽 아래 · 휴대폰 위쪽)
                 · NotificationsPage(/notifications) · NotificationSettingsSection(개인 설정 › 알림 설정)
    mcp-key/     mcp-key(+test: 상태 · 만료 이유 이름 · 연결 명령) · queries · McpKeyList(카드 줄) · IssueKeyDialog(연결 안내 — 일감 · 프로젝트 관리 주소와 `claude mcp add --scope user` 명령 둘. 원문은 대화 상자의
                 상태에만 — 쿼리 · 변경 캐시에 두지 않는다) · ExpireKeyDialog(이유) · MyMcpKeysSection(개인 설정 › 내 MCP 키) · WorkspaceMcpKeysPage(/w/:slug/mcp-keys)
    workspace/   (더함) audit-log(+test: 거르기 ↔ 주소 · 날짜 → 순간 · 시각 + id 커서) · PersonFilter · AuditPage 거르기
  pages/      StatusPages — / 이동 · 404 · 403
```

프로젝트 · 일감 기능 폴더는 그 기능을 만들 때 더한다(계획). moai-wiki 의 `features/wiki` · `features/page` 를 본뜨고, 화면 이름도 위키 → 프로젝트로 맞춘다. 일감 화면의 자세한 이름은 만들 때 moai-wiki 의 페이지 화면을 따라 정한다.

```
features/                 (계획)
  project/   queries(쿼리 키 · projectCan) · project-key(+test: key 규칙 · 제안 — api 와 같은 규칙)
             ProjectsPage(카드 목록 · 보관 탭) · ProjectCards
             ProjectLayout(/w/:slug/projects/:projectKey 의 틀 — 프로젝트를 읽어 내려주고, 프로젝트 메뉴를 본문 위 한 줄로) · project-context(useProject)
             ProjectHomePage(프로젝트 홈 = 일감 목록) · ProjectDialog(만들기 · 설정) · RemoveProjectDialog(key 를 입력해 확인)
             use-project-actions(보관 · 복원) · 프로젝트 역할 화면(members)
  issue/     queries · issue-labels(유형 · 상태 이름) · 일감 목록(필터 · 정렬 · 쪽)
             (2026-11-22 더함) issue-view-tabs(내용 · 댓글 · 첨부파일 ?tab=) · IssueAttachments(끌어 놓기 · 여러 개 차례로 · 표시 복사 · 지우기)
             · AttachmentControls · attachment-context(일감 문맥) · attachments(+test: 크기 · 표시) · markdown-urls(+test: attachment: → 받기 주소, 그 밖 차단)
             · use-attachment-upload(붙여 넣기 · 끌어 놓기 → 올리고 자리표시 바꾸기)
             일감 보기(설명 · 하위 일감 · 연결 일감 · 이력과 댓글) · 새 일감 · 일감 고치기(Markdown 편집기 · 관리자 · 담당자 · 날짜)
             상위 고르기 · 연결 넣고 빼기 · MarkdownView · MarkdownEditor(편집기 — 아래 editor/)
             editor/: RichEditor(Milkdown Crepe, 따로 받는 코드) · crepe-setup(켜고 끄는 기능 · 첨부 그림 보기 · 붙여 넣기 · 링크 막기) ·
                      EditorToolbar(위 고정 도구 막대) · MarkdownSourceEditor(원문 — CodeMirror) · source-blocks(원문 보존, moai-wiki 사본) ·
                      link-rules · __mocks__/RichEditor(시험용) · __fixtures__(원문 보존 시험 자료). 시험 자료는 dev/editor-roundtrip.html(개발 서버 전용)로 만든다
  workspace/ (더함) MCP 엔드포인트 · MCP 키 · 연결 안내 화면
```

## 4. 입구와 바깥 연결

| 화면 주소 | 묶음 (`app/routes.tsx`) | 조건 |
|---|---|---|
| `/sign-in` · `/sign-up` · `/sign-up/verify` | `guestRoutes` | 로그인하지 않았을 때 (`GuestOnly`) |
| `/workspaces/discover` · `/workspaces/new` · `/invitations/:token` | `accountRoutes` | 로그인만 하면 |
| `/w/:slug`(홈) · `…/members`(`member.read`) · `…/requests`(`member.request.review`) · `…/roles`(`workspace.read`) · `…/settings`(`workspace.settings.manage`) · `…/audit`(`audit.read`) | `workspaceRoutes` | 줄마다 필요한 권한(`RequireWorkspacePermission`). 멤버가 아니면 api 가 404 로 답하고 화면(`WorkspaceLayout`)은 "워크스페이스를 찾을 수 없습니다"와 찾기 단추를 보인다 |
| `/admin/signup-policy` · `/admin/accounts` | `platformRoutes` | 렐름 역할 system-admin (`RequirePlatformRole`) |
| `/w/:slug/projects`(목록 · 보관 탭) · `…/projects/:projectKey`(프로젝트 홈 = 일감 목록) · `…/:projectKey/issues/new` · `…/:projectKey/issues/:number` · `…/:projectKey/issues/:number/edit` · `…/:projectKey/members` **(계획)** | `workspaceRoutes` (프로젝트 하나는 `ProjectLayout` 의 `children`) | 프로젝트 독자가 아니면 api 가 404 로 답하고 화면은 "프로젝트를 찾을 수 없습니다"를 보인다. 단추는 서버가 준 `myPermissions` · `myActions` 로만 보인다 |

- 서버 응답 모양은 `shared/api/types.ts` 한 곳에, 부르는 함수는 `shared/api/endpoints.ts` 에 둔다. 화면은 경로를 직접 적지 않는다.
- 상태를 바꾸는 요청에는 web-backend 가 준 `XSRF-TOKEN` 쿠키를 `X-XSRF-TOKEN` 헤더로 싣는다(`shared/api/http.ts`).
- 401 이면 이 사람의 캐시를 모두 지우고 로그인으로 보낸다. 직접 로그아웃한 경우에는 `returnTo` 를 달지 않는다. 자세한 내용은 [WEB-DESIGN.md](../../WEB-DESIGN.md) · [SECURITY.md](../../SECURITY.md) §9.

## 5. 실행 · 검사

```bash
npm install
npm run dev        # https://localhost:11003 — /api 는 web-backend(https 11002)로 넘긴다
bin/startup.sh     # 같은 개발 서버를 백그라운드로. 끄기는 bin/shutdown.sh, 출력은 logs/ 에 날짜별로
npm run build      # 타입 검사 + web-backend static 으로 빌드
npm run lint && npm run typecheck && npm test
```

- `bin/startup.sh` 는 화면을 고칠 때만 쓴다. 운영에서는 `npm run build` 결과를 web-backend 가 내보내므로 이 모듈은 띄울 것이 없다([DEVELOPE.md §5.1](../../DEVELOPE.md#51-띄우고-끄기)).
- 마치기 전 검사: `npm run lint` · `npm run typecheck` · `npm test` · `npm run build` 가 모두 통과해야 한다.
- 자동 테스트(Vitest)는 지금 4개 파일 60건(2026-11-21): 모든 스킨 · 상태색 · 코드 글자색의 대비(WCAG AA, `theme/contrast.test.ts`), 두 언어의 키 · 자리표시자 · 빈 문장 · `<Trans>` 태그 이름(`shared/i18n/i18n.test.ts`, 묶음마다), `returnTo` 안전 검사(`shared/lib/post-login-path.test.ts`), 역할 규칙(`features/workspace/role-rules.test.ts` — api 와 같은 규칙).
- 화면을 바꿨으면 브라우저(ego-browser)에서 **데스크톱과 휴대폰 너비(390px)** 를 모두 확인한다. 가로 스크롤이 없어야 하고, 본문은 창을 꽉 채워야 한다.

### 5.1 코드 분할

- 화면은 `app/routes.tsx` 에서 `page(() => import('…'), (m) => m.화면)` 으로 넣는다. 그 화면에 들어갈 때 코드를 내려받는다. 화면을 그냥 import 해서 넣지 않는다.
- 받는 동안에는 배치(`AppShell` · `WorkspaceLayout` · `AuthLayout`)의 `Suspense` 가 "불러오는 중"을 보인다. 사이드바와 워크스페이스 틀은 그대로 남는다.
- 라이브러리는 React 묶음(`react` · `react-dom` · `react-router` · `scheduler`)만 이름을 정해 따로 묶고(`vite.config.ts` 의 `codeSplitting`), 나머지는 번들러가 쓰는 곳에 따라 나눈다. 그래서 한 화면만 쓰는 라이브러리는 그 화면과 함께 받는다. 무거운 것(Markdown 렌더러 · 편집기 · mermaid)을 들일 때도 그 화면에서만 import 하면 첫 화면이 커지지 않는다 — 지금은 아무 화면도 import 하지 않아 빌드에 들어가지 않는다.
- 가장 큰 조각이 600 kB 를 넘으면 경고한다(`chunkSizeWarningLimit`).

## 6. 새로 더할 때

| 더할 것 | 하는 일 |
|---|---|
| 화면 | `app/routes.tsx` 에 한 줄(주소 · `page(…)` 로 감싼 화면 · 권한 또는 플랫폼 역할 · 메뉴 이름 키 · 아이콘) + `common.nav` 에 메뉴 이름(두 언어). 라우터 · 메뉴 · 가드가 따라 생긴다 |
| 글 | `resources/ko` · `resources/en` 의 같은 자리에 키를 더한다. 어긋나면 `npm test` 가 잡는다. 새 기능 묶음(예: 일감)은 두 언어에 같은 이름의 파일로 |
| 스킨 | `theme/skins.ts` 에 라이트 · 다크 한 덩어리 + `common.preferences.skins` 에 이름. 대비 테스트를 통과해야 한다 |
| api 호출 | `shared/api/types.ts` 에 응답 모양, `endpoints.ts` 에 함수, 기능 폴더의 `queries.ts` 에 쿼리 키 |
| 권한 코드 | `shared/api/types.ts` 의 `PermissionCode` · `shared/lib/labels.ts` 의 `permissionGroups` · 리소스 `workspace:permissions.*`(두 언어) |

## 7. 아직 안 한 것

- 업무 화면 전부: 프로젝트(목록 · 보관 탭 · 만들기 · 설정 · 프로젝트 역할) · 일감(목록 · 필터 · 정렬 · 보기 · 이력 · 댓글 · 만들기 · 고치기 · 상위 · 하위 · 연결). 순서는 [로드맵](../plan/01-roadmap.md).
- 기본 역할 넷 · 새 권한 코드 9개의 화면 쪽(`PermissionCode` · `permissionGroups` · 리소스 — [2.1](../wbs/03-stage-2-project.md#w-2-1)).
- MCP 엔드포인트 · MCP 키 관리 화면과 연결 안내([MCP 설계](../design/06-mcp.md)).
- 본문 보기 · 편집기 — 의존성은 있지만 쓰는 화면이 없다(§2).
- 서버 오류 문구는 지금 한국어뿐이다. `Accept-Language` 는 이미 보내므로 api 가 언어를 따르면 그대로 바뀐다.

## 8. 바꿀 때 함께 고칠 문서

| 바꾼 것 | 함께 고칠 곳 |
|---|---|
| 테마 · 스킨 · 다국어 · 반응형 · 화면 규칙 | [WEB-DESIGN.md](../../WEB-DESIGN.md) |
| 가드 · 세션 처리 · CSRF · `returnTo` · 저장소 사용 | [WEB-DESIGN.md](../../WEB-DESIGN.md) · [SECURITY.md](../../SECURITY.md) §9 |
| 역할 규칙(`role-rules.ts`) | api 의 `MembershipService` · `RoleService` 와 같게 맞춘다 · [SECURITY.md](../../SECURITY.md) §6.5 |
| 새 화면 · 기능 폴더 | 이 쪽 §3 · §4 · [WEB-DESIGN.md](../../WEB-DESIGN.md) §6 |
| 의존성 버전 | 이 쪽 §2 · [00-overview](00-overview.md) §1 ([DEVELOPE.md](../../DEVELOPE.md)의 버전 원칙대로) |


> **2026-11-23 더함:** 왼쪽 주 메뉴 접기(`AppShell` — 68px 아이콘 막대, `moai-works.sidebar-collapsed`), 프로젝트 메뉴를 본문 위 한 줄로(`ProjectLayout`), 일감 보기 배치(속성 칸은 받은 너비로 — `shared/lib/use-element-width.ts`), Markdown 편집기(`features/issue/editor/*` — [일감 설계 — 편집기](../design/05-issue.md#is-editor)). jsdom 에서는 편집기 대신 시험용 편집기를 쓴다(`src/test-setup.ts` 의 vi.mock).

> **2026-11-22 더함:** 일감 리비전 · 차이 화면 `issues/:number/history`(`features/issue/IssueHistoryPage.tsx` · `DiffView.tsx` · `diff-lines.ts` — moai-wiki 페이지 이력 화면을 옮김), 작업 시간(`IssueWorkLogs.tsx` · `work-hours.ts`), 예상 공수 필드, 변화 시각을 밀리초까지 보이는 `shared/lib/format.ts` 의 시각 형식(`YYYY-MM-DD HH:mm:ss.SSS`).

---

**관련 문서:** [모듈 한눈에](00-overview.md) · [WEB-DESIGN.md](../../WEB-DESIGN.md) · [SECURITY.md](../../SECURITY.md) · [moai-works-web-backend](02-web-backend.md) · [워크스페이스 요구사항](../requirements/01-workspace.md) · [DEVELOPE.md](../../DEVELOPE.md)

[← 이전: moai-works-web-backend](02-web-backend.md) | [다음: 로드맵 →](../plan/01-roadmap.md)
