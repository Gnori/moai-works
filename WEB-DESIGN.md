# moai-works 웹 디자인 규칙

| 항목 | 내용 |
|---|---|
| 무엇 | 웹 화면(`src/moai-works-web-frontend`)의 디자인 · 화면 규칙: 테마 · 스킨 · 표면 · 반응형 · 다국어 · 경로와 메뉴 · 글. moai-wiki 와 같은 규칙이다 |
| 언제 읽나 | 화면을 새로 만들거나 고칠 때, 색 · 글꼴 · 간격 · 컴포넌트 모양을 바꿀 때, 글(언어 리소스)을 더할 때, 스킨을 더할 때 |
| 함께 볼 것 | 화면의 인증 · 보안 규칙은 [SECURITY.md §9](SECURITY.md#9-화면web-frontend의-보안-규칙) · 폴더 구조와 명령은 [moai-works-web-frontend 모듈 문서](docs/modules/03-web-frontend.md) |
| 기준일 | 2026-11-21 — 규칙을 바꾸면 이 문서도 같은 작업에서 고친다 |

아래 경로는 `src/moai-works-web-frontend/src/` 기준이다. 화면 뼈대는 SeedSiteKeeper 프론트의 구조(기능 폴더 · 경로 표 · 테마 층)를 참고했고 코드는 가져오지 않았다.

## 1. 원칙

1. **권한은 api 가 판정한다.** 화면은 메뉴 · 단추 · 화면을 숨기기만 한다. 모르면 숨긴다 — 기본값으로 권한을 주지 않는다([SECURITY.md §9](SECURITY.md#9-화면web-frontend의-보안-규칙)).
2. **모바일에서도 잘 된다.** 모든 화면을 휴대폰 너비(390px)와 데스크톱에서 함께 확인한다(§4).
3. **창을 꽉 채운다.** 앱 본문은 창 너비를 모두 쓴다. 글 · 양식만 자기 안에서 너비를 줄인다.
4. **서버 응답 모양은 `shared/api/types.ts` 한 곳.** 화면은 `shared/api/endpoints.ts` 함수만 부르고 경로를 직접 적지 않는다.
5. **오류는 하나의 모양(`ApiError {status, code, message}`).** 화면은 `code` 로 분기하고 `message` 를 보여 준다. 403 은 "볼 권한이 없습니다"(`ForbiddenState`), 비멤버는 404(존재를 숨김). 프로젝트도 독자가 아니면 404 를 받으므로 "찾을 수 없습니다"로 보인다(계획).
6. **화면에 글을 직접 쓰지 않는다.** 모든 글은 언어 리소스에 있다(§5).
7. **색 · 크기는 theme 이름으로만.** 값(#…, 임의 px 색)을 화면 코드에 쓰지 않는다(§2).

## 2. 테마 · 스킨

세 층으로 나눈다. 화면 코드는 맨 위 층(theme 이름)만 안다.

| 층 | 파일 | 하는 일 |
|---|---|---|
| 토큰 | `theme/tokens.ts` | 스킨과 상관없는 값 — 중립색(현무암 0–950), 상태색(성공 · 경고 · 오류 · 정보, 라이트/다크), 코드 글자색(`code`, 라이트/다크), 글꼴, 모서리(6 · 10 · 14px), 사이드바 너비(272px) · 위 막대 높이(56px) |
| 스킨 | `theme/skins.ts` | 주 색 묶음 — `ocean`(기본, 깊은 바다) · `lagoon`(석호) · `basalt`(현무암) · `ember`(노을). 스킨마다 라이트 · 다크 팔레트 |
| 테마 | `theme/create-theme.ts` · `theme/components.ts` | (스킨, 언어) → MUI theme. colorSchemes(light/dark) + CSS 변수, 컴포넌트 기본 모양, MUI 문구(koKR/enUS) |

- 밝기(라이트 · 다크 · 시스템)는 MUI `useColorScheme`, 스킨은 `AppearanceProvider` 가 정한다. 둘 다 계정 메뉴 → 화면 설정(로그인 화면은 오른쪽 위 단추)에서 바꾸고, 이 브라우저에만 기억한다.
- **색 값(#…)은 `src/theme` 밖에서 쓰지 않는다.** ESLint(`no-restricted-syntax`)가 막는다. `primary.main`, `text.secondary`, `background.subtle` 처럼 theme 이름만.
- 색조 규칙: `*.light` 는 연한 바탕(칩 · 아이콘), 그 위 글자는 `*.main`. 다크에서는 `main` 이 밝고 `contrastText` 가 어둡다.
- 다크의 `*.light` 는 `main` 을 20% 로 깐 **반투명** 색이다. MUI 기본 모양 가운데 `light` 로 글자색을 만드는 것(알림 상자 `Alert` 등)은 글자까지 반투명해지므로 `components.ts` 에서 직접 정한다 — 알림 상자는 바탕 `*.light` · 글 본문색 · 아이콘 `*.main`.
- **대비는 테스트로 지킨다**(`theme/contrast.test.ts`): 모든 스킨 · 상태색의 글자 · 색조 위 글자 · 단추 글자 · 알림 상자 글자, 그리고 코드 글자색(코드 상자 바탕 위 · 차이 표시의 넣은 줄 · 지운 줄)이 라이트 · 다크 모두 WCAG AA(4.5:1) 이상.
- 글꼴: Pretendard Variable(코드는 JetBrains Mono). 제목은 `h4`(화면 제목) · `h5` · `h6`, 본문은 `body2`, 보조는 `caption`.
- **새 스킨**: `theme/skins.ts` 에 라이트 · 다크 한 덩어리 + `common.preferences.skins` 에 이름(두 언어). 대비 테스트가 통과해야 한다.

## 3. 표면 · 컴포넌트

- 카드: 그림자 없이 테두리 1px, 둥글기 14px. 단추: 대문자 변환 없음, 그림자 없음.
- 화면 머리: `PageHeader`(작은 위 글씨 · 제목 · 설명(최대 680px) · 오른쪽 동작). 동작 단추는 권한이 있을 때만(`<Can>`).
- 상태: 불러오는 중 `LoadingState`, 비었을 때 `EmptyState`(무엇이 생기는지 + 다음 행동), 오류 `ErrorState`(403 이면 `ForbiddenState`). 셋 중 하나는 반드시 그린다 — 빈 화면을 두지 않는다.
- 되돌릴 수 없는 동작은 `useConfirm()`(빨간 단추). 결과는 토스트(sonner).
- **못 하는 동작은 꺼 두고 이유를 함께 보인다.** 메뉴 항목은 이유 글을 흐리게 하지 않는다(`RolesPage` 의 `ActionItem`). 단추를 아예 숨길지 끌지는: 권한이 없으면 숨기고, 권한은 있는데 지금 조건(쓰는 멤버가 있음 등) 때문에 못 하면 끄고 이유를 보인다.
- MUI `Menu` 안에서는 `<Can>` 대신 `useCan()` 조건으로 항목을 고른다(초점 처리 때문). 조건부 조각(`<>…</>`)으로 항목을 감싸지 않는다.
- 색 · 기호로만 전하는 뜻에는 `VisuallyHidden` 이나 `aria-label` 로 말을 보탠다(권한 있음/없음, 받은 초대 수).
- 양식: react-hook-form + zod. 검증 문구도 언어 리소스에서. 대화상자 안의 양식은 `display: contents` 로 감싸 `DialogContent` 스크롤을 깨지 않는다.

### 3.1 프로젝트 · 일감 화면의 규칙

프로젝트 · 일감 화면은 moai-wiki 의 위키 · 페이지 화면과 같은 구조로 만든다(위키 → 프로젝트, 페이지 → 일감). moai-wiki 에서 검증한 아래 규칙을 그대로 쓴다. 본문 형식 · 편집기 결정은 [일감 설계](docs/design/05-issue.md).

- **본문**(일감 설명 · 댓글)은 Markdown(GFM) 하나를 한 렌더러로만 그린다(보기와 편집기 미리보기가 같은 모양). 읽기 폭은 920px 까지. 넓은 표 · 긴 코드 · 큰 다이어그램은 그 상자 안에서만 가로로 밀린다. HTML 은 그리지 않는다([SECURITY.md §9](SECURITY.md#9-화면web-frontend의-보안-규칙)). `react-markdown` · `remark-gfm` · `rehype-highlight` · `mermaid` 를 쓴다.
- **코드 블록**은 머리에 언어 이름과 복사 단추를 두고, 적힌 언어에 맞춰 색을 입힌다(언어를 짐작하지 않는다). 글자 색은 테마의 `code.*` 이고 스킨과 상관없다. **다이어그램**(` ```mermaid `)은 다이어그램이 있는 화면에서만 그때 받아 오고, 밝기가 바뀌면 다시 그린다.
- **편집기**(CodeMirror 6 — 의존성에 있음)의 색은 값이 아니라 테마의 CSS 변수(`theme.vars`)로 적는다 — `theme.palette` 의 값은 라이트 것이라 그대로 쓰면 다크에서 글자가 배경에 묻힌다. Tab 은 다음 칸으로 넘어간다(키보드만 쓰는 사람이 편집 칸에 갇히지 않게).
- **본문 속 링크 표기**(`#123` 일감 · `@사용자`)는 상태를 색만으로 전하지 않는다 — 닫힌 일감은 취소선 + 상태 이름, 읽을 수 없는 대상은 "접근 권한이 없는 일감"처럼 글로 보인다. 보이는 글은 대상의 지금 제목이다.
- **이력 · 차이**는 지운 줄 빨강, 넣은 줄 초록에 줄 앞 `−` · `+` 를 함께 적는다(색만으로 전하지 않는다).
- **되돌릴 수 없는 동작**(일감 · 프로젝트 삭제)은 이유와 이름을 그대로 입력해야 단추가 통한다. 휴대폰에서는 이런 대화상자가 화면을 꽉 채운다.
- **AI Agent 가 한 일**은 사람과 구별해 보인다 — MCP 로 바꾼 이력에는 키 주인(사람) 이름 옆에 출처(MCP) 표시를 적는다(누르면 키 이름 · 엔드포인트). MCP 키는 늘 사람의 키다 — 서비스 키는 없다([MCP 설계](docs/design/06-mcp.md)).

## 4. 반응형 · 배치

| 너비 | 배치 |
|---|---|
| `md`(900px) 이상 | 왼쪽 고정 사이드바(272px, 창 높이) + 본문 |
| `md` 미만 | 위 막대(56px) + 서랍 메뉴. 메뉴 항목을 누르면 서랍이 닫힌다 |

- 본문 여백은 `xs 16px · sm 24px · md 40px`(`AppShell`). 본문은 창 너비를 모두 쓴다.
- 너비를 줄이는 것은 읽기 · 입력 덩어리만: 양식 560px, 검색 칸 420px, 단독 안내 화면(초대) 600px, 로그인 패널 420px.
- **가로 스크롤 금지.** 넓은 표는 `TableContainer` 안에서만 가로로 밀린다. 첫 열이 이름이면 붙여 둔다(`position: sticky`, `RolesPage` 의 `stickyCell`) — 휴대폰에서 열을 넘겨도 어느 줄인지 잃지 않게. 일감 목록 · 간트 · 칸반 보드처럼 넓은 화면(계획)도 같은 규칙이다: 보드는 칸 묶음 안에서만 가로로 밀리고, 390px 에서 페이지 전체가 밀리지 않는다.
- 좁은 화면에서 덜 중요한 열은 숨긴다(`display: { xs: 'none', md: 'table-cell' }`).
- 큰 대화상자(역할 편집처럼 항목이 많은 것)는 `sm` 미만에서 전체 화면(`fullScreen`)으로 연다.
- 누를 곳은 44px 이상 — 터치 기기(`pointer: coarse`)에서 단추 · 링크의 최소 높이를 44px 로 둔다(`components.ts`).
- 끌어서 옮기기(보드의 카드 · 순서 바꾸기, 계획)는 마우스 같은 정밀한 포인터에서만 켜고, 터치 화면과 키보드에는 같은 기능의 메뉴 · 대화상자를 둔다.
- 글이 넘칠 수 있는 곳(이름 · 설명 · 코드)은 `noWrap` + `title`, 또는 `overflowWrap: 'anywhere'`.

**확인 순서** (화면을 바꿀 때마다, 도구는 **ego-browser**): 데스크톱 너비와 390px 너비 → 라이트 · 다크 → 한국어 · 영어 → 권한이 많은 계정과 적은 계정. 390px 에서 페이지 전체가 가로로 밀리면 안 된다(`document.documentElement.scrollWidth` ≤ 창 너비).

## 5. 다국어

- i18next + react-i18next. 언어: 한국어(기본 · 대체), 영어. 리소스는 `shared/i18n/resources/<언어>/<묶음>.json`.
- 묶음: `common`(앱 · 단추 · 상태 · 오류 · 메뉴 · 역할 이름 · 화면 설정 · 워크스페이스 전환) · `auth`(로그인 · 가입 · 인증) · `workspace`(워크스페이스 화면 · 권한 · 역할 · 초대 · 가입 신청 · 감사 동작 이름) · `admin`(플랫폼 관리). 프로젝트 · 일감 화면을 만들면 그 묶음을 더한다(계획).
- 키는 타입으로 검사된다(`i18next.d.ts` — 한국어 리소스가 기준). 없는 키는 `tsc` 가 막는다.
- `i18n.test.ts` 가 두 언어의 키 · 자리표시자(`{{name}}`, `<cta>`)가 같고 빈 문장이 없는지 확인한다. **키를 더하면 두 언어 모두에.**
- 서버 코드값 → 말은 `shared/lib/labels.ts` 의 `useLabels()` · `useRoleName()` 한 곳. 권한 코드 `member.request.review` 는 리소스의 `permissions.member.request.review`. 권한 코드 묶음(`permissionGroups`)도 여기에 있다 — 프로젝트 · 일감 코드는 그 기능을 만들 때 묶음을 더한다. 서버가 새로 더한 감사 동작은 리소스에 없으면 코드 그대로 보인다.
- 언어를 바꾸면 함께 바뀌는 것: `<html lang>`, dayjs 날짜 말, MUI 문구, api 요청의 `Accept-Language`(지금 api 오류 문구는 한국어).
- 영어 수량 표현은 "Members: 3" 처럼 단 · 복수가 필요 없는 꼴로 쓴다(두 언어의 키를 같게 두기 위해).
- `<Trans>` 의 `components` 로만 글 안에 링크를 넣는다(`<cta>가입</cta>` → `components={{ cta: <Link …/> }}`). HTML 문자열을 그리지 않는다. 태그 이름에 HTML 빈 요소(`link` · `br` · `img` …)를 쓰지 않는다 — 여는 즉시 닫혀 안의 글이 링크 밖으로 빠진다(테스트가 막는다).

## 6. 경로 · 메뉴

- `app/routes.tsx` 가 정본이다. 경로 하나 = 한 줄(주소 · 화면 · 필요한 권한 또는 플랫폼 역할 · 메뉴 이름 키 · 아이콘).
  - `guestRoutes` — 로그인 없이(`GuestOnly`): `/sign-in` · `/sign-up` · `/sign-up/verify`
  - `workspaceRoutes` — `/w/:slug` 아래, `permission` 이 있으면 `RequireWorkspacePermission`: 홈 · `members`(`member.read`) · `requests`(`member.request.review`) · `roles`(`workspace.read`) · `settings`(`workspace.settings.manage`) · `audit`(`audit.read`)
  - `accountRoutes` — 로그인만 하면: `/workspaces/discover`(찾기) · `/workspaces/new`(만들기) · `/invitations/:token`(초대 링크)
  - `platformRoutes` — `role`(system-admin) 이 필요(`RequirePlatformRole`): `/admin/signup-policy` · `/admin/accounts`
- `app/router.tsx` 와 사이드바(`layouts/AppShell.tsx`)가 이 표에서 만들어진다. 메뉴는 내 권한(`myPermissions`) · 플랫폼 역할로 거른다. 권한을 여럿 적으면 그 가운데 하나만 있어도 된다(`routeAllowed`).
- 사이드바의 워크스페이스 전환 단추와 메뉴는 같은 워크스페이스를 가리킨다(주소의 `:slug`, 없으면 마지막에 쓴 곳).
- 한 화면 안의 탭은 주소에 남긴다(`?tab=invitations`) — 새로 고침 · 링크 공유에도 같은 탭이 열린다. 일감 목록의 필터 · 정렬도 주소에 남긴다(계획 — 링크로 같은 목록을 나눌 수 있게).
- 한 화면을 틀로 삼아 그 안에 화면을 그릴 때는 그 줄의 `children` 에 적는다(틀이 `Outlet` 을 둔다). 지금은 쓰는 줄이 없고, 프로젝트 하나를 틀로 삼아 그 아래에 일감 · 로드맵 · 설정 화면을 둘 때 쓴다(계획). 하위 화면 주소가 `new` 같은 화면 이름과 부딪히지 않게 앞에 고정 낱말을 둔다.
- **새 화면**: 표에 한 줄 + `common.nav` 에 이름(두 언어) → 라우터 · 메뉴 · 가드가 따라 생긴다. 화면은 `page(() => import('…'), (m) => m.화면)` 으로 넣는다(경로 단위로 나눠 받는다).

## 7. 글

- 사람이 알아보는 말로(권한 코드 대신 "가입 신청 승인 · 거절"). 코드는 보조로 작게(고정폭).
- 단추는 하는 일을 그대로("승인", "내보내기"). 오류는 무엇이 잘못됐고 어떻게 하면 되는지.
- 꺼 둔 항목의 이유는 짧은 한 문장("멤버 2명이 쓰는 역할입니다").

## 8. 바꿀 때 지킬 것

- 색 · 모서리 · 글꼴 · 배치 값을 바꾸면 `theme/` 한 곳에서. 이 문서 §2 · §4 의 값도 고친다.
- 새 공통 모양(상태 · 대화상자 · 표 형식)은 `shared/ui` 에 두고 이 문서 §3 에 한 줄.
- 화면을 바꾼 뒤 검사: `npm run lint && npm run typecheck && npm test && npm run build`, 그리고 §4 의 확인 순서.
- 인증 · 권한 · 저장소 · CSRF 에 닿는 화면 변경은 [SECURITY.md](SECURITY.md) §9 를 함께 고친다.

---

**관련 문서:** [CLAUDE.md](CLAUDE.md) · [개발 규칙](DEVELOPE.md) · [보안 · 인증 구조](SECURITY.md) · [moai-works-web-frontend](docs/modules/03-web-frontend.md) · [moai-works-web-backend](docs/modules/02-web-backend.md)
