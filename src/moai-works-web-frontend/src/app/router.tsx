import { createBrowserRouter, type RouteObject } from 'react-router';

import { RequireWorkspacePermission } from '@/features/auth/access';
import { GuestOnly, RequireAuth, RequirePlatformRole, WorkspaceAreaOnly } from '@/features/auth/guards';
import { WorkspaceLayout } from '@/features/workspace/WorkspaceLayout';
import { AppShell } from '@/layouts/AppShell';
import { AuthLayout } from '@/layouts/AuthLayout';
import { ForbiddenPage, NotFoundPage, RootRedirect, SystemAdminNoticePage } from '@/pages/StatusPages';

import { accountRoutes, guestRoutes, platformRoutes, workspaceRoutes, type WorkspaceRoute } from './routes';

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
 * 층: 손님(GuestOnly) / 로그인(RequireAuth) → 앱 틀(AppShell) → 워크스페이스 영역(WorkspaceAreaOnly: 워크스페이스 · 찾기 · 만들기 · 초대) · 시스템 관리.
 * 시스템 관리자 계정은 시스템 관리 쪽만 쓴다.
 */
export const router = createBrowserRouter([
  {
    element: <GuestOnly />,
    children: [{ element: <AuthLayout />, children: guestRoutes.map((r) => ({ path: r.path, element: r.element })) }],
  },
  {
    element: <RequireAuth />,
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
