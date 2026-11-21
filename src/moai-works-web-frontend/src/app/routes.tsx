import DashboardRounded from '@mui/icons-material/DashboardRounded';
import GroupsRounded from '@mui/icons-material/GroupsRounded';
import HowToRegRounded from '@mui/icons-material/HowToRegRounded';
import ManageAccountsRounded from '@mui/icons-material/ManageAccountsRounded';
import PolicyRounded from '@mui/icons-material/PolicyRounded';
import ReceiptLongRounded from '@mui/icons-material/ReceiptLongRounded';
import SettingsRounded from '@mui/icons-material/SettingsRounded';
import ShieldRounded from '@mui/icons-material/ShieldRounded';
import { lazy, type ComponentType, type ReactNode } from 'react';

import type { PermissionCode } from '@/shared/api/types';
import type koCommon from '@/shared/i18n/resources/ko/common.json';

/**
 * 화면 주소의 정본 — 라우터(router.tsx)와 사이드바 메뉴(AppShell)가 모두 이 표에서 만들어진다.
 * 접근 조건(access)도 여기 한 곳에 적는다. 화면 가드는 표시만 막고, 실제 판정은 api 가 한다.
 *
 * 새 화면을 더할 때: 이 표에 한 줄 + 리소스(nav 이름) — 라우터 · 메뉴 · 가드는 따라 생긴다.
 * 화면은 {@link page} 로 넣는다 — 그 화면에 들어갈 때 코드를 내려받는다(경로 단위 코드 분할).
 */

/**
 * 화면 하나를 따로 내려받는 조각으로 만든다. 받는 동안에는 배치(AppShell · WorkspaceLayout · AuthLayout)의
 * Suspense 가 "불러오는 중"을 보인다.
 *
 * @param load 화면 모듈을 가져오는 함수 — 반드시 `() => import('…')` 꼴로 적는다(번들러가 여기서 조각을 나눈다)
 * @param pick 모듈에서 화면 컴포넌트를 고른다
 */
function page<M>(load: () => Promise<M>, pick: (module: M) => ComponentType): ReactNode {
  const Page = lazy(async () => ({ default: pick(await load()) }));
  return <Page />;
}

export type NavLabel = keyof typeof koCommon.nav;

export interface NavMeta {
  label: NavLabel;
  icon: ReactNode;
  /** 정확히 이 주소일 때만 강조 (홈) */
  end?: boolean;
}

/** /w/:slug 아래 화면 — path '' 는 워크스페이스 홈 */
export interface WorkspaceRoute {
  path: string;
  element: ReactNode;
  /**
   * 이 워크스페이스에서 필요한 권한 — 없으면 메뉴에서 숨기고, 주소로 들어와도 "볼 권한이 없습니다".
   * 여럿을 적으면 그 가운데 하나만 있어도 된다
   */
  permission?: PermissionCode | PermissionCode[];
  nav?: NavMeta;
  /** 이 화면을 틀로 삼아 그 안에 그리는 화면들 (틀이 Outlet 을 둔다). path '' 는 틀의 첫 화면 */
  children?: WorkspaceRoute[];
}

/** 플랫폼 관리 화면 — 렐름 역할(system-admin 등)이 필요하다 */
export interface PlatformRoute {
  path: string;
  element: ReactNode;
  role: 'system-admin' | 'security-officer';
  nav: NavMeta;
}

/** 로그인 없이 보는 화면 (로그인한 사람은 들어갈 곳으로 보낸다) */
export interface GuestRoute {
  path: string;
  element: ReactNode;
}

export const guestRoutes: GuestRoute[] = [
  { path: '/sign-in', element: page(() => import('@/features/auth/SignInPage'), (m) => m.SignInPage) },
  { path: '/sign-up', element: page(() => import('@/features/auth/SignUpPage'), (m) => m.SignUpPage) },
  { path: '/sign-up/verify', element: page(() => import('@/features/auth/VerifyPage'), (m) => m.VerifyPage) },
];

export const workspaceRoutes: WorkspaceRoute[] = [
  { path: '', element: page(() => import('@/features/workspace/WorkspaceHomePage'), (m) => m.WorkspaceHomePage), nav: { label: 'home', icon: <DashboardRounded fontSize="small" />, end: true } },
  {
    path: 'members',
    element: page(() => import('@/features/workspace/MembersPage'), (m) => m.MembersPage),
    permission: 'member.read',
    nav: { label: 'members', icon: <GroupsRounded fontSize="small" /> },
  },
  {
    path: 'requests',
    element: page(() => import('@/features/workspace/JoinRequestsPage'), (m) => m.JoinRequestsPage),
    permission: 'member.request.review',
    nav: { label: 'requests', icon: <HowToRegRounded fontSize="small" /> },
  },
  {
    path: 'roles',
    element: page(() => import('@/features/workspace/RolesPage'), (m) => m.RolesPage),
    permission: 'workspace.read',
    nav: { label: 'roles', icon: <ShieldRounded fontSize="small" /> },
  },
  {
    path: 'settings',
    element: page(() => import('@/features/workspace/SettingsPage'), (m) => m.SettingsPage),
    permission: 'workspace.settings.manage',
    nav: { label: 'settings', icon: <SettingsRounded fontSize="small" /> },
  },
  {
    path: 'audit',
    element: page(() => import('@/features/workspace/AuditPage'), (m) => m.AuditPage),
    permission: 'audit.read',
    nav: { label: 'audit', icon: <ReceiptLongRounded fontSize="small" /> },
  },
];

/** 로그인한 사람이면 누구나 (워크스페이스와 상관없음) */
export const accountRoutes: GuestRoute[] = [
  { path: '/workspaces/discover', element: page(() => import('@/features/workspace/DiscoverPage'), (m) => m.DiscoverPage) },
  { path: '/workspaces/new', element: page(() => import('@/features/workspace/CreateWorkspacePage'), (m) => m.CreateWorkspacePage) },
  /** 초대 링크 — 로그인 안 했으면 로그인(또는 가입) 뒤 여기로 돌아온다 */
  { path: '/invitations/:token', element: page(() => import('@/features/invitations/InvitationPage'), (m) => m.InvitationPage) },
];

export const platformRoutes: PlatformRoute[] = [
  {
    path: '/admin/signup-policy',
    element: page(() => import('@/features/admin/SignupPolicyPage'), (m) => m.SignupPolicyPage),
    role: 'system-admin',
    nav: { label: 'signupPolicy', icon: <PolicyRounded fontSize="small" /> },
  },
  {
    path: '/admin/accounts',
    element: page(() => import('@/features/admin/AccountsPage'), (m) => m.AccountsPage),
    role: 'system-admin',
    nav: { label: 'accounts', icon: <ManageAccountsRounded fontSize="small" /> },
  },
];

export function workspacePath(slug: string, route: Pick<WorkspaceRoute, 'path'>): string {
  return route.path ? `/w/${slug}/${route.path}` : `/w/${slug}`;
}

/** 이 화면에 들어갈 수 있는가 — 권한 조건이 여럿이면 하나만 있어도 된다 */
export function routeAllowed(route: Pick<WorkspaceRoute, 'permission'>, permissions: readonly PermissionCode[]): boolean {
  if (!route.permission) {
    return true;
  }
  const needed = Array.isArray(route.permission) ? route.permission : [route.permission];
  return needed.some((p) => permissions.includes(p));
}
