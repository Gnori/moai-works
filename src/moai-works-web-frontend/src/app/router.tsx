import { createBrowserRouter, type RouteObject } from 'react-router';

import { RequireWorkspacePermission } from '@/features/auth/access';
import { GuestOnly, RequireAuth, RequirePlatformRole, WorkspaceAreaOnly } from '@/features/auth/guards';
import { WorkspaceLayout } from '@/features/workspace/WorkspaceLayout';
import { AppShell } from '@/layouts/AppShell';
import { AuthLayout } from '@/layouts/AuthLayout';
import { ForbiddenPage, NotFoundPage, RootRedirect, RouteErrorPage, SettingsRedirect, SystemAdminNoticePage } from '@/pages/StatusPages';

import { accountRoutes, guestRoutes, platformRoutes, settingsSections, workspaceRoutes, type WorkspaceRoute } from './routes';

/** 워크스페이스 화면 — 권한 조건이 있으면 가드로 감싼다 */
function workspaceChild(route: WorkspaceRoute): RouteObject {
  const element = route.permission ? (
    <RequireWorkspacePermission permission={route.permission}>{route.element}</RequireWorkspacePermission>
  ) : (
    route.element
  );
  if (!route.path) {
    return { index: true, element };
  }
  return { path: route.path, element, children: route.children?.map(workspaceChild) };
}

/**
 * 라우터 — routes.tsx 표로 만든다. 주소를 직접 열어도 된다(BFF 가 /api 가 아닌 주소를 index.html 로 돌려준다).
 * 층: 손님(GuestOnly) / 로그인(RequireAuth) → 앱 틀(AppShell) → 워크스페이스 영역(WorkspaceAreaOnly: 워크스페이스 · 찾기 · 만들기 · 초대 ·
 * 알림함) · 시스템 관리. 개인 설정은 화면이 아니라 앱 틀이 여는 대화상자다(?settings=… — 예전 /me/… 주소는 그리로 보낸다).
 * 시스템 관리자 계정은 시스템 관리 쪽과 개인 설정의 내 프로필 · 화면 설정만 쓴다.
 * 그리다 난 오류는 RouteErrorPage 가 받는다 — 새 판 때문에 조각을 못 받았으면 한 번 새로 고친다.
 */
export const router = createBrowserRouter([
  {
    element: <GuestOnly />,
    errorElement: <RouteErrorPage />,
    children: [{ element: <AuthLayout />, children: guestRoutes.map((r) => ({ path: r.path, element: r.element })) }],
  },
  {
    element: <RequireAuth />,
    errorElement: <RouteErrorPage />,
    children: [
      {
        element: <AppShell />,
        children: [
          { path: '/', element: <RootRedirect /> },
          {
            // 워크스페이스 영역 — 시스템 관리자 계정은 들어가지 못한다(안내를 보인다)
            element: <WorkspaceAreaOnly fallback={<SystemAdminNoticePage />} />,
            children: [
              { path: '/w/:slug', element: <WorkspaceLayout />, children: workspaceRoutes.map(workspaceChild) },
              ...accountRoutes.map((r) => ({ path: r.path, element: r.element })),
            ],
          },
          // 예전 개인 설정 주소(/me/profile …) — 그 칸을 연 개인 설정 대화상자로 보낸다
          ...settingsSections.map((s) => ({ path: s.legacyPath, element: <SettingsRedirect section={s.key} /> })),
          ...platformRoutes.map((r) => ({
            path: r.path,
            element: <RequirePlatformRole role={r.role}>{r.element}</RequirePlatformRole>,
          })),
          { path: '/forbidden', element: <ForbiddenPage /> },
          { path: '*', element: <NotFoundPage /> },
        ],
      },
    ],
  },
]);
