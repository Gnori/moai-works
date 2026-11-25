[홈](../README.md) › [모듈](00-overview.md) › moai-works-web-frontend

# moai-works-web-frontend

moai-works 의 웹 화면(React). 빌드 결과는 `../moai-works-web-backend/src/main/resources/static/` 으로 들어가고, [web-backend](02-web-backend.md) 가 정적 파일과 `/api` 를 함께 서비스한다. 화면 규칙(테마 · 스킨 · 다국어 · 반응형)은 [WEB-DESIGN.md](../../WEB-DESIGN.md), 화면 보안은 [SECURITY.md](../../SECURITY.md) §9 에 있다. moai-wiki 의 화면 뼈대를 가져와 위키 · 자료실 화면을 걷어 냈다. 위키 · 페이지 화면 자리에는 프로젝트 · 일감 화면이 같은 구조로 들어온다(계획).

## 1. 하는 일 · 하지 않는 일

| 한다 | 하지 않는다 |
|---|---|
| 로그인 · 가입 · 인증 코드 · 워크스페이스(홈 · 찾기 · 만들기 · 설정) · 멤버 · 가입 신청 · 역할과 권한 · 초대 · 감사 · 시스템 관리(가입 정책 · 계정) 화면 | 권한 판정. 가드 · `<Can>` · 메뉴 숨김은 **표시만** 하고, 판정은 api 가 요청마다 한다 |
| 서버가 계산해 준 `myPermissions` · `platformRoles` 로 메뉴와 단추를 거른다 | 토큰 다루기. 브라우저에는 HttpOnly 세션 쿠키만 있다 |
| 같은 출처의 `/api` 만 부른다 | 개인 정보나 토큰을 브라우저 저장소에 두기 |
| 프로젝트 · 프로젝트 역할 · 일감 화면 (계획 — moai-wiki 의 위키 · 페이지 화면과 같은 구조) · MCP 키 화면(개인 설정 › 내 MCP 키 · 워크스페이스 › MCP 키 — 키는 사용자의 것, 2026-11-23) | |

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
                 · 비밀번호 찾기(PasswordResetPage — 이메일 → 메일 코드 + 새 비밀번호 두 번, 2026-11-24. 시스템 관리자 계정은
                   Keycloak 관리자가 재설정한다는 안내 글 — 일감 #157)
    workspace/   WorkspaceLayout(/w/:slug 의 틀 — 워크스페이스를 읽어 내려주고 마지막에 쓴 곳으로 기억) · workspace-context
                 queries(쿼리 키 · 역할 이름) · role-rules(+test: 줄 수 있는 역할 · 넣을 수 있는 권한 — api 와 같은 규칙)
                 홈 · 멤버(멤버 / 보낸 초대 탭) · 가입 신청 · 역할과 권한(+RoleEditorDialog: 사용자 정의 역할) · 설정 · 감사 · 찾기 · 만들기
    invitations/ 보내는 쪽: InviteDialog(링크 한 번 보이기 · 복사) · SentInvitations(취소)
                 받는 쪽: InvitationPage(/invitations/:token) · ReceivedInvitations(찾기 화면) · use-invitation-actions(수락 · 거절) · queries
    admin/       가입 정책 · 계정 관리 · 워크스페이스 관리(WorkspacesPage — 사용 중지 · 다시 사용 · 지우기, 2026-11-23) (system-admin)
    me/          개인 설정 대화 상자 — PersonalSettingsDialog(왼쪽 메뉴) · use-settings-dialog(?settings= 주소) · ProfileSection ·
                 PasswordSection(?settings=password — 현재 비밀번호 확인 · 다른 기기의 로그인 끊기, 시스템 관리자 계정도, 2026-11-24) ·
                 PreferencesSection(PreferencesFields 를 로그인 화면 대화 상자와 함께 씀) · SettingsSectionHeader
    notifications/ describe(+test: 종류 21 → 문장 · 링크) · preferences(+test: 묶음 · 잠금) · queries · NotificationBell(왼쪽 아래 · 휴대폰 위쪽)
                 · NotificationsPage(/notifications) · NotificationSettingsSection(개인 설정 › 알림 설정 — 종류마다 알림함 · 메일 두 열)
    mcp-key/     mcp-key(+test: 상태 · 만료 이유 이름 · 연결 명령) · queries · McpKeyList(카드 줄) · IssueKeyDialog(이름 · 기간만 — 워크스페이스 칸 없음.
                 연결 안내 — 일감 · 프로젝트 관리 주소와 `claude mcp add --scope user` 명령 둘, "키 하나로 내가 속한(역할에 MCP 권한이 있는) 모든 워크스페이스".
                 원문은 대화 상자의 상태에만 — 쿼리 · 변경 캐시에 두지 않는다. 403 MCP_KEY_ISSUE_NOT_ALLOWED 면 안내) · ExpireKeyDialog(이유 — 내 키만)
                 · MyMcpKeysSection(개인 설정 › 내 MCP 키, 워크스페이스 칸 없음) · WorkspaceMcpKeysPage(/w/:slug/mcp-keys — mcp.key.manage, 멤버들의 키를
                 보기만, 막는 법은 역할에서 MCP 권한 빼기 안내) — 2026-11-23 키는 사용자의 것(ADR-029). 2026-11-23 키마다 호출 수(주인에게만) ·
                 "접근 기록"(내 키는 KeyAccessLogDialog, 워크스페이스 키는 audit.read 가 있으면 /w/:slug/mcp-access?key= 로)
    mcp-access/  (2026-11-23, FR-MCP-010) access-log(+test: 거르기 ↔ 주소 · 날짜 → 순간 · 시각 + id 커서 · 대상 · 인자 요약) · queries(워크스페이스 · 내 키,
                 CSV 주소) · AccessLogView(조건 줄 — 키 주인 · 작업 · 결과 · 날짜, "조건 더 보기"로 키 · 도구 · 클라이언트 · 프로젝트 · 세션 · CSV 단추,
                 표 — 좁으면 덜 중요한 칸을 숨기고 "무엇을" 칸에 쌓음, 50줄씩 더 보기) · AccessEntryDialog(한 줄의 모든 칸 · 같은 세션 · 이 키 · 일감 열기,
                 휴대폰은 꽉 채움) · McpAccessLogPage(/w/:slug/mcp-access — audit.read, 조건은 주소에) · KeyAccessLogDialog(내 키 하나 — 조건은 창의 상태)
                 (+access-screen.test)
    workspace/   (더함) audit-log(+test: 거르기 ↔ 주소 · 날짜 → 순간 · 시각 + id 커서) · PersonFilter · AuditPage 거르기
    effort/      공수 집계(2026-11-23) — effort(+test: 조건 ↔ 주소 · 기간(한국 시간) · 요청 값 · 막대 · 차이 기호) · queries · EffortReport(조건 · 합계 · 표 · CSV —
                 워크스페이스 · 프로젝트 화면이 같이 씀) · EffortPage(/w/:slug/effort) · ProjectEffortPage(…/projects/:projectKey/effort) · effort-screen.test
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
             (2026-11-22 더함) issue-view-tabs(내용 · 댓글 · 첨부파일 · 리비전 ?tab=) · IssueAttachments(끌어 놓기 · 여러 개 차례로 · 표시 복사 · 지우기 · 2026-11-23 작은 그림은 서버의 미리보기 그림 `previewUrl`, WebP 는 원본, 못 받으면 파일 표시 — `thumbnailSource`)
             · AttachmentControls · attachment-context(일감 문맥) · attachments(+test: 크기 · 표시) · markdown-urls(+test: attachment: → 받기 주소, 그 밖 차단)
             · use-attachment-upload(붙여 넣기 · 끌어 놓기 → 올리고 자리표시 바꾸기)
             · use-markdown-editor(편집기 손잡이 — 친 순간의 "바뀜" · 저장 앞에서 방금 친 글 받기)
             일감 보기(설명 · 하위 일감 · 연결 일감 · 이력과 댓글) · 새 일감 · 일감 고치기(Markdown 편집기 · 관리자 · 담당자 · 날짜)
             상위 고르기 · 연결 넣고 빼기 · MarkdownView · MarkdownEditor(편집기 — 아래 editor/)
             editor/: RichEditor(Milkdown Crepe, 따로 받는 코드) · crepe-setup(켜고 끄는 기능 · 첨부 그림 보기 · 붙여 넣기 · 링크 막기) ·
                      EditorToolbar(위 고정 도구 막대) · MarkdownSourceEditor(원문 — CodeMirror) · source-blocks(원문 보존, moai-wiki 사본 — 바뀐 곳만 다시 나눔) ·
                      block-markdown(바뀐 맨 위 덩어리만 다시 적기, 일감 #16) · sync-delay(글을 올리기까지 기다리는 시간) ·
                      link-rules · __mocks__/RichEditor(시험용) · __fixtures__(원문 보존 시험 자료). 시험 자료는 dev/editor-roundtrip.html(개발 서버 전용)로 만든다
  workspace/ (더함) MCP 사용량 화면(워크스페이스 엔드포인트 화면은 만들지 않는다 — ADR-029)
```

## 4. 입구와 바깥 연결

| 화면 주소 | 묶음 (`app/routes.tsx`) | 조건 |
|---|---|---|
| `/sign-in` · `/sign-up` · `/sign-up/verify` · `/password-reset` · `/password-reset/confirm?email=`(비밀번호 찾기, 2026-11-24) | `guestRoutes` | 로그인하지 않았을 때 (`GuestOnly`). 비밀번호 찾기 화면은 돌아갈 주소(`returnTo`)로 받지 않는다 |
| `/workspaces/discover` · `/workspaces/new` · `/invitations/:token` | `accountRoutes` | 로그인만 하면 |
| `/w/:slug`(홈) · `…/members`(`member.read`) · `…/requests`(`member.request.review`) · `…/roles`(`workspace.read`) · `…/settings`(`workspace.settings.manage`) · `…/audit`(`audit.read`) · `…/effort`(`workspace.read` — 공수 집계, 2026-11-23) · `…/mcp-access`(`audit.read` — MCP 접근 기록, 2026-11-23) | `workspaceRoutes` | 줄마다 필요한 권한(`RequireWorkspacePermission`). 멤버가 아니면 api 가 404 로 답하고 화면(`WorkspaceLayout`)은 "워크스페이스를 찾을 수 없습니다"와 찾기 단추를 보인다. 시스템 관리자가 사용 중지한 워크스페이스면 api 가 멤버에게 403 `WORKSPACE_SUSPENDED` 를 주고, 화면은 그 아래 어느 주소든 "사용 중지된 워크스페이스입니다"와 "다른 워크스페이스로"(들어갈 곳)를 보이며 내 정보를 다시 받는다(전환 메뉴에 "사용 중지" 표시) |
| `/admin/signup-policy` · `/admin/accounts` · `/admin/workspaces`(탭 `?tab=SUSPENDED` · `PERSONAL` · `ALL`, 기본은 사용 중인 팀) · `/admin/skills`(`?deleted=1`) · `/admin/skills/new` · `/admin/skills/view/:name`(`?tab=revisions&rev=&from=&view=snapshot`) · `/admin/skills/edit/:name`(2026-11-24 — 메뉴에는 목록 하나, `nav` 가 없는 줄은 메뉴에 두지 않는다) | `platformRoutes` | 렐름 역할 system-admin (`RequirePlatformRole`) |
| `/w/:slug/projects`(목록 · 보관 탭) · `…/projects/:projectKey`(프로젝트 홈 = 일감 목록) · `…/:projectKey/issues/new` · `…/:projectKey/members` · `…/:projectKey/effort`(프로젝트 공수, 2026-11-23) · **일감 하나의 정식 주소(2026-11-24)** `/w/:slug/issues/:number`(탭 `?tab=comments` · `attachments` · `revisions`, 리비전 탭은 `&rev=&from=&view=snapshot`) · `…/issues/:number/edit` · `…/issues/:number/history`(리비전 탭으로 돌림) · **워크스페이스 일감 목록** `/w/:slug/issues`(사이드바 "일감") · **옛 주소** `…/projects/:projectKey/issues/:number`(· `/edit` · `/history`) | `workspaceRoutes` (프로젝트 하나는 `ProjectLayout` 의 `children`, 일감 하나는 `IssueLayout` 의 `children`, 옛 주소는 `LegacyIssueRedirect`) | 프로젝트 독자가 아니면 api 가 404 로 답하고 화면은 "프로젝트를 찾을 수 없습니다"를, 일감을 읽을 수 없으면 "일감을 찾을 수 없습니다"를 보인다. `IssueLayout` 은 일감을 읽어 그 일감의 지금 프로젝트로 `ProjectFrame`(프로젝트 메뉴 한 줄 · 내 권한)을 그리고, 사이드바는 그 프로젝트를 강조한다. `LegacyIssueRedirect` 는 `issue-refs` 로 옛 번호의 지금 번호를 받아 정식 주소로 돌린다. 단추는 서버가 준 `myPermissions` · `myActions` 로만 보인다 |

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
- **새 판이 나오면 옛 조각이 없어진다**(빌드가 static/ 을 비우고 조각 이름의 해시가 바뀐다). 먼저 열어 둔 탭이 아직 받지 않은 화면으로 가면 "Failed to fetch dynamically imported module" 이 났다(2026-11-23 사용자: "이 에러는 왜 자꾸 나는 거야?"). 동적 import 는 `importWithReload`(`shared/lib/chunk-reload.ts` — `page()` · 편집기 · mermaid)로 감싸고 Vite 의 `vite:preloadError` 도 듣는다: 조각을 못 받으면 한 번 새로 고친다(index.html 은 no-cache 라 새 판의 이름을 안다). 10초 안에 또 실패하면 되풀이하지 않고 `RouteErrorPage`(라우터 `errorElement`, 기본 "Unexpected Application Error!" 대신)가 새로 고침 단추를 보인다.

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
- MCP 사용량 화면([MCP 설계](../design/06-mcp.md)). MCP 키 화면(개인 설정 › 내 MCP 키 · 워크스페이스 › MCP 키) · 연결 안내 · MCP 접근 기록(2026-11-23)은 됐다. 워크스페이스 엔드포인트 화면은 만들지 않는다(2026-11-23 [ADR-029](../design/00-key-decisions.md#adr-029)).
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


> **2026-11-24 더함(일감 상태 보류 · 취소 — 일감 #152, [일감 설계 §5](../design/05-issue.md#is-status)):** `shared/api/types.ts` 의 `issueStatuses`(일곱, 차례 그대로) · `reasonStatuses`(보류 · 취소), `features/issue/issue-form.ts` 의 `needsStatusReason` · `creatableStatuses`(새 일감은 다섯), `features/issue/StatusReasonDialog.tsx`(까닭 필수 대화상자 — 닫는 단추는 "닫기"), `IssueViewPage` 의 상태 바꾸기 메뉴(상태마다 뜻 한 줄 `labels.issueStatusHelp`, 보류 · 취소는 대화상자를 거쳐 `status` + `notes`), `IssueEditPage` 의 바꾼 이유 필수(서버의 `STATUS_REASON_REQUIRED` 도 그 칸), `IssueForm` 의 `statusOptions` · `statusNote`, `IssueStatusChip` 의 모양(`labels.ts` 의 `issueStatusTone` — 보류 점선 · 멈춤 아이콘, 취소 취소선). 시험 `issue-status-screens.test.tsx`.

> **2026-11-25 더함(관리자 확인 — 일감 #272, [일감 설계 §5](../design/05-issue.md#is-status)):** `issueStatuses` 열하나(등록 · 요구사항 확인요청 · 요구사항 확인완료 · 진행 · 테스트 · 확인요청 · 확인완료 · 보류 · 완료 · 종료 · 취소) · `reviewStatuses` · `confirmStatuses` · `reasonStatuses`(보류 · 취소 · 확인요청 둘), `issue-form.ts` 의 `confirmationOf` · `rejectTargetOf` · `returnsReview` · `canConfirmIssue`(관리자 = 나 또는 `project.manage` — 가리기만) · `needsStatusReason`(확인요청을 되돌릴 때도), `StatusReasonDialog` 의 글 묶음(보류 · 취소 · 확인요청 둘 · 되돌리기 `RETURN`), `IssueReviewBanner`(확인요청에 있을 때 일감 보기 머리 아래 — 관리자에게 확인 완료 · 반려, 그 밖에는 누구를 기다리는지), 상태 바꾸기 메뉴와 `IssueForm` 의 `statusDisabledReason`(관리자가 아니면 확인완료 둘을 꺼 두고 까닭을 적음), 칩 모양 `review`(정보색 테두리 · 모래시계) · `confirmed`(성공색 테두리 · 확인 표시), 알림 셋(`issue.review_requested` · `review_confirmed` · `review_returned`). 시험 `issue-status-screens` · `workspace-issues-screens` · `issue-form` · `describe` · `contrast`.

> **2026-11-26 더함(우선순위 — 일감 #302):** `shared/api/types.ts` 의 `issuePriorities`(긴급 · 높음 · 보통 · 낮음, 이 차례) · `IssuePriority`(목록 줄 · 일감 · 리비전에 늘 옴, 만들기는 빼면 보통, 고치기는 바뀐 때만) · 정렬 `priority`(긴급부터) · `-priority`, `features/issue/IssuePriorityChip.tsx`(긴급 오류색 채움 · 높음 경고색 테두리 · 낮음 흐린 테두리, 화살표와 이름 — 보통은 그리지 않고 `normal` 이면 흐린 글자 "보통"), `IssueForm` 의 우선순위 고르기(상태 다음, 새 일감은 보통), `IssueViewPage` 의 머리 칩(보통이 아닐 때) · 속성 셋째 줄 맨 앞 칸(`IssueTagFields` 의 `priority`), `IssueList` 의 우선순위 거르기(여럿, 주소 `?priority=` — 모르는 값은 버림) · 표의 우선순위 열(머리로 정렬) · 카드의 칩(보통 빼고) · 정렬 "우선순위순" · "우선순위 낮은 순", `journal.ts` · `change-text.ts` 의 `priority`(이력 "우선순위: 보통 → 긴급", 리비전 차이 · 전체 보기도 이름으로), `labels.issuePriority`, 알림 `issue.priority_urgent`(앞 우선순위가 있으면 "… → 긴급", 처음부터 긴급이면 만든 문장 `priority_urgent_new`). 시험 `issue-fields-screens`(우선순위 6건) · `issue-filters` · `issue-form` · `journal` · `issue-screens` · `describe` · `contrast`.

> **2026-11-23 더함(공수 집계 — [일감 설계](../design/05-issue.md#is-effort)):** `features/effort/*`(워크스페이스 메뉴 "공수 집계" · 프로젝트 메뉴 한 줄의 "공수"), `shared/api` 의 `effortApi`(report · csv — CSV 는 blob 으로 받고 오류 본문도 code · 문장으로 읽는다) · `Effort*` 형, 언어 묶음 `effort`(ko · en), 감사 동작 이름 `issue.work_log.export`. 막대는 Box 로 그린다(차트 라이브러리 없음). 시험 `effort.test.ts` 13건 · `effort-screen.test.tsx` 6건 · `routes.test.ts` 2건.

> **2026-11-23 더함(에이전트 세션 · 모듈 — 일감 #26 · #27):** `features/project/ProjectModulesPage.tsx`(`…/modules` — 목록 · 더하기 · 수정 · 위/아래 순서 · 쓰지 않음 · 다시 쓰기, 메뉴는 `project.manage`) · `ModuleDialog.tsx`, `features/issue/IssueTags.tsx`(세션 · 모듈 칩, 보기 속성 셋째 줄 `IssueTagFields`), `IssueForm` 의 관련 모듈 여럿 고르기 · 에이전트 세션 입력 칸, `IssueList` 의 모듈 · 세션 거르기(주소 `?module=` · `?agentSession=`) · 칩, `journal.ts` · `change-text.ts` 의 `agentSession` · `modules`, `shared/api` 의 `projectApi.modules` · `createModule` · `updateModule` · `reorderModules` · `issueApi.agentSessions` · `ProjectModule` · `IssueModuleRef`. 시험 `issue-fields-screens.test.tsx`.

> **2026-11-25 더함(에이전트 멤버 — 일감 #285):** `features/project/ProjectMembersPage.tsx` 를 탭 둘(사람 · 에이전트, 주소 `?tab=agents`)로 나눴다. `ProjectAgentsPanel.tsx`(에이전트 줄 — 이름 칩 · 주인 · 설명 · 열린 일감 링크(세션 + 열린 상태) · 마지막 활동 · 쓰지 않음 · 자동 등록, 관리하는 사람에게 수정 · 쓰지 않음 · 다시 쓰기) · `AgentDialog.tsx`(추가 · 수정 — 이름 · 주인 · 설명), `IssueForm` 의 세션 칸은 글을 그대로 적는 고르기 칸(쓰는 에이전트 먼저), `shared/api` 의 `projectApi.agents` · `createAgent` · `updateAgent` · `ProjectAgent` · `openIssueStatuses`, `projectKeys.agents` · `useProjectAgentsQuery`. 시험 `project-members-screens.test.tsx`.

> **2026-11-24 더함(Agent 스킬 — 일감 #30, [Agent 스킬 설계](../design/08-skill.md)):** `features/admin/skills/*` — `SkillsPage`(목록 · 삭제한 스킬도 보기 · ⋮ 보기 · 수정 · 내려받기 · 삭제 확인 · 되살리기) · `SkillFormPage`(`NewSkillPage` · `EditSkillPage` — 일감과 같은 `MarkdownEditor`, 첨부 올리기 없음, 읽은 리비전 `baseRevisionNo` · 409 `SKILL_CHANGED` 면 다시 불러오기) · `SkillViewPage`(내용 · 리비전 탭) · `SkillRevisions`(타임라인 · 차이 · 전체 · 되돌리기 확인) · `ImportSkillDialog`(파일 · 붙여 넣기, 있던 스킬이면 묻고 그 리비전으로) · `skill-rules.ts`(서버와 같은 한도 — 안내만) · `queries.ts`, `shared/api` 의 `skillApi` · `Skill*` · `RevisionInfo` · `DiffLine`, 언어 `admin:skills.*` · `common:revisions.*` · `common:nav.skills` · 감사 이름 `admin.skill.*`. **리비전 화면의 공통 조각을 `shared/ui/revisions/` 로 옮겼다** — `RevisionTimeline` · `RevisionSplit`(+ `useRevisionReveal`) · `RevisionMeta` · `RevisionCard` · `DiffView` · `diff-lines` · `revision-view`(일감 리비전 탭 `IssueRevisions` 도 이것을 쓴다, 필드 차이 · 전체 보기만 일감 것). 시험 `skills-screens.test.tsx` 8건 · `routes.test.ts` 2건.

> **2026-11-23 더함(일감 템플릿):** `features/issue/IssueTemplateManager.tsx`(유형별 템플릿 관리 — 워크스페이스 설정 칸 · 프로젝트 화면이 같이 씀), `features/project/ProjectTemplatesPage.tsx`(`…/templates`), `features/workspace/SettingsPage.tsx` 의 템플릿 칸, `shared/lib/issue-template.ts`(항목 · 빠진 항목 · 채워도 되는지 — 서버와 같은 규칙), `shared/api` 의 `issueTemplateApi` · `IssueTemplate`, `IssueForm` 의 템플릿 채우기 · 빠진 항목 경고. 시험 `template-screens.test.tsx` 12건.

> **2026-11-23 더함:** 왼쪽 주 메뉴 접기(`AppShell` — 68px 아이콘 막대, `moai-works.sidebar-collapsed`), 프로젝트 메뉴를 본문 위 한 줄로(`ProjectLayout`), 일감 보기 배치(속성 칸은 받은 너비로 — `shared/lib/use-element-width.ts`), Markdown 편집기(`features/issue/editor/*` — [일감 설계 — 편집기](../design/05-issue.md#is-editor)). jsdom 에서는 편집기 대신 시험용 편집기를 쓴다(`src/test-setup.ts` 의 vi.mock).

> **2026-11-24 바꿈(일감은 워크스페이스 아래, 일감 #29 — [ADR-030](../design/00-key-decisions.md#adr-030)):** 일감 번호는 워크스페이스에서 하나라 일감 하나의 주소가 `/w/:slug/issues/:number` 다(`issuePath(slug, number)` · `issueEditPath` · `issueRevisionsPath` — 프로젝트 key 없음, `newIssuePath` 만 프로젝트). `features/issue/IssueLayout.tsx`(일감을 읽어 `ProjectFrame` — `features/project/ProjectLayout.tsx` 에서 떼어 낸 프로젝트 틀) · `LegacyIssueRedirect.tsx`(옛 주소 → 정식) · `WorkspaceIssuesPage.tsx`(사이드바 "일감" — `IssueList` 의 워크스페이스 범위: 프로젝트 칸 · 프로젝트 거르기 여럿 · `#123` 바로 가기) · `ViaChip.tsx`(경로 MCP · 시스템). 일감 질의 키는 `['issues', slug]` 아래 워크스페이스 번호로(`queries.ts`). 고치기 양식의 프로젝트 고르기 + 옮기기 확인 창, 이력의 프로젝트 옮김 · 번호 바뀜 문장, 글 속 `#n` · 알림 · 공수 · MCP 접근 기록의 일감 링크는 정식 주소. 시험 `workspace-issues-screens.test.tsx` 13건 등.
>
> **2026-11-23 바꿈(리비전 탭, 일감 #25):** 리비전 · 차이를 따로 화면에서 일감 보기의 넷째 탭으로 옮겼다 — `features/issue/IssueRevisions.tsx`(옛 `IssueHistoryPage.tsx` 의 타임라인 · 차이 · 전체 보기를 그대로, 탭이 보일 때만 읽음), `IssueHistoryRedirect.tsx`(예전 `issues/:number/history` → `?tab=revisions`), `app/routes.tsx` 의 `issueRevisionsPath`(옛 `issueHistoryPath`). 시험 `issue-view-tabs.test.ts` 3건 · `issue-screens.test.tsx` 의 리비전 탭 3건.

> **2026-11-22 더함:** 일감 리비전 · 차이 화면 `issues/:number/history`(`features/issue/IssueHistoryPage.tsx` · `DiffView.tsx` · `diff-lines.ts` — moai-wiki 페이지 이력 화면을 옮김), 작업 시간(`IssueWorkLogs.tsx` · `work-hours.ts`), 예상 공수 필드, 변화 시각을 밀리초까지 보이는 `shared/lib/format.ts` 의 시각 형식(`YYYY-MM-DD HH:mm:ss.SSS`).

---

**관련 문서:** [모듈 한눈에](00-overview.md) · [WEB-DESIGN.md](../../WEB-DESIGN.md) · [SECURITY.md](../../SECURITY.md) · [moai-works-web-backend](02-web-backend.md) · [워크스페이스 요구사항](../requirements/01-workspace.md) · [DEVELOPE.md](../../DEVELOPE.md)

[← 이전: moai-works-web-backend](02-web-backend.md) | [다음: 로드맵 →](../plan/01-roadmap.md)
