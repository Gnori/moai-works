import AccountCircleRounded from '@mui/icons-material/AccountCircleRounded';
import AssignmentRounded from '@mui/icons-material/AssignmentRounded';
import DashboardRounded from '@mui/icons-material/DashboardRounded';
import GroupsRounded from '@mui/icons-material/GroupsRounded';
import HowToRegRounded from '@mui/icons-material/HowToRegRounded';
import KeyRounded from '@mui/icons-material/KeyRounded';
import ManageAccountsRounded from '@mui/icons-material/ManageAccountsRounded';
import PolicyRounded from '@mui/icons-material/PolicyRounded';
import ReceiptLongRounded from '@mui/icons-material/ReceiptLongRounded';
import SettingsRounded from '@mui/icons-material/SettingsRounded';
import ShieldRounded from '@mui/icons-material/ShieldRounded';
import TuneRounded from '@mui/icons-material/TuneRounded';
import VpnKeyRounded from '@mui/icons-material/VpnKeyRounded';
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

/** 개인 설정 — 워크스페이스와 상관없이 로그인한 사람 본인의 화면 (사이드바의 "개인 설정") */
export interface PersonalRoute {
  path: string;
  element: ReactNode;
  nav: NavMeta;
  /**
   * 워크스페이스를 쓰는 계정만 — 시스템 관리자 계정(워크스페이스가 없다)에는 메뉴를 숨기고, 주소로 들어와도 안내를 보인다.
   * MCP 키는 워크스페이스에서 받으므로 시스템 관리자 계정에는 없다(api 도 막는다).
   */
  workspaceAccountOnly?: boolean;
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
    path: 'projects',
    element: page(() => import('@/features/project/ProjectsPage'), (m) => m.ProjectsPage),
    permission: 'workspace.read',
    // 프로젝트 하나(projects/:projectKey)를 볼 때는 사이드바의 그 프로젝트가 강조된다 — 목록 메뉴는 정확히 이 주소일 때만
    nav: { label: 'projects', icon: <AssignmentRounded fontSize="small" />, end: true },
  },
  /**
   * 프로젝트 하나 — 독자가 아니면 api 가 404 로 숨긴다. ProjectLayout 이 프로젝트를 읽어 내려주고 프로젝트 메뉴를 곁에 둔다.
   * 일감 주소 앞에 issues/ 를 둬서 일감 번호가 members 같은 화면 이름과 부딪히지 않는다.
   * 일감을 만들고 고칠 수 있는지는 서버가 준 이 프로젝트의 내 권한 · 할 수 있는 일로 단추만 가린다(판정은 api).
   */
  {
    path: 'projects/:projectKey',
    element: page(() => import('@/features/project/ProjectLayout'), (m) => m.ProjectLayout),
    permission: 'workspace.read',
    children: [
      { path: '', element: page(() => import('@/features/project/ProjectHomePage'), (m) => m.ProjectHomePage) },
      { path: 'issues/new', element: page(() => import('@/features/issue/NewIssuePage'), (m) => m.NewIssuePage) },
      { path: 'issues/:number', element: page(() => import('@/features/issue/IssueViewPage'), (m) => m.IssueViewPage) },
      { path: 'issues/:number/edit', element: page(() => import('@/features/issue/IssueEditPage'), (m) => m.IssueEditPage) },
      // 리비전 타임라인 · 두 판의 차이 — 독자 누구나(moai-wiki 의 p/:pageSlug/history 자리). 고른 판은 주소에(?rev=5&from=2)
      { path: 'issues/:number/history', element: page(() => import('@/features/issue/IssueHistoryPage'), (m) => m.IssueHistoryPage) },
      // 프로젝트 역할 — 보기는 독자 누구나, 주고 빼기는 project.manage(화면이 이 프로젝트의 내 권한으로 단추를 가린다)
      { path: 'members', element: page(() => import('@/features/project/ProjectMembersPage'), (m) => m.ProjectMembersPage) },
    ],
  },
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
  /**
   * MCP 키 관리 — 이 워크스페이스의 모든 키(만료된 것까지 발급 이력)와 만료시키기. 기본 역할로는 소유자 · 관리자.
   * 내 키 받기는 개인 설정의 "내 MCP 키"에서 한다.
   */
  {
    path: 'mcp-keys',
    element: page(() => import('@/features/mcp-key/WorkspaceMcpKeysPage'), (m) => m.WorkspaceMcpKeysPage),
    permission: 'mcp.key.manage',
    nav: { label: 'mcpKeys', icon: <KeyRounded fontSize="small" /> },
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

/** 개인 설정 — 내 프로필 · 화면 설정은 누구나, 내 MCP 키는 워크스페이스를 쓰는 계정만 */
export const personalRoutes: PersonalRoute[] = [
  {
    path: '/me/profile',
    element: page(() => import('@/features/me/ProfilePage'), (m) => m.ProfilePage),
    nav: { label: 'profile', icon: <AccountCircleRounded fontSize="small" /> },
  },
  {
    path: '/me/preferences',
    element: page(() => import('@/features/me/PreferencesPage'), (m) => m.PreferencesPage),
    nav: { label: 'preferences', icon: <TuneRounded fontSize="small" /> },
  },
  {
    path: '/me/mcp-keys',
    element: page(() => import('@/features/mcp-key/MyMcpKeysPage'), (m) => m.MyMcpKeysPage),
    nav: { label: 'myMcpKeys', icon: <VpnKeyRounded fontSize="small" /> },
    workspaceAccountOnly: true,
  },
];

/** 화면 설정 (계정 메뉴의 "화면 설정"이 여기로 간다) */
export const preferencesPath = '/me/preferences';

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

/** 시스템 관리자 계정이 들어가는 첫 화면 */
export const systemHomePath = '/admin/accounts';

export function workspacePath(slug: string, route: Pick<WorkspaceRoute, 'path'>): string {
  return route.path ? `/w/${slug}/${route.path}` : `/w/${slug}`;
}

/** 프로젝트 목록 */
export function projectsPath(slug: string): string {
  return `/w/${slug}/projects`;
}

/** 프로젝트 하나의 홈 (일감 목록). search 를 주면 그 목록 조건(?status=…)으로 연다 */
export function projectPath(slug: string, projectKey: string, search?: string): string {
  return `/w/${slug}/projects/${encodeURIComponent(projectKey)}${search ? `?${search}` : ''}`;
}

/** 프로젝트 역할(멤버) */
export function projectMembersPath(slug: string, projectKey: string): string {
  return `${projectPath(slug, projectKey)}/members`;
}

/** 일감 보기 */
export function issuePath(slug: string, projectKey: string, number: number): string {
  return `${projectPath(slug, projectKey)}/issues/${number}`;
}

/** 일감 고치기 */
export function issueEditPath(slug: string, projectKey: string, number: number): string {
  return `${issuePath(slug, projectKey, number)}/edit`;
}

/**
 * 일감의 리비전 · 차이. rev 를 주면 그 판을, from 도 주면 그 두 판의 차이를 연다(from 이 없으면 바로 앞 판과).
 * view 가 'snapshot' 이면 그 판 전체를 연다.
 */
export function issueHistoryPath(
  slug: string,
  projectKey: string,
  number: number,
  options: { rev?: number; from?: number; view?: 'snapshot' } = {},
): string {
  const params = new URLSearchParams();
  if (options.rev !== undefined) {
    params.set('rev', String(options.rev));
  }
  if (options.from !== undefined) {
    params.set('from', String(options.from));
  }
  if (options.view) {
    params.set('view', options.view);
  }
  const query = params.toString();
  return `${issuePath(slug, projectKey, number)}/history${query ? `?${query}` : ''}`;
}

/** 새 일감. parent 를 주면 그 일감 아래에 만든다 */
export function newIssuePath(slug: string, projectKey: string, parent?: number | null): string {
  return `${projectPath(slug, projectKey)}/issues/new${parent ? `?parent=${parent}` : ''}`;
}

/**
 * 감사 기록 — 조건(사람 · 동작 이름 또는 묶음)을 주소에 담아 연다. 예: 한 사람의 MCP 키 기록
 * auditPath(slug, { person: ownerId, action: 'mcp.key' }) → /w/{slug}/audit?person=…&action=mcp.key
 */
export function auditPath(slug: string, filters: { person?: string; action?: string } = {}): string {
  const params = new URLSearchParams();
  if (filters.person) {
    params.set('person', filters.person);
  }
  if (filters.action) {
    params.set('action', filters.action);
  }
  const query = params.toString();
  return `/w/${slug}/audit${query ? `?${query}` : ''}`;
}

/**
 * 개인 설정 화면이 이 계정에 보이는가. systemAdmin 은 서버가 준 플랫폼 역할로 정한 값(isSystemAdmin(me)) —
 * 화면이 권한 규칙을 다시 계산하지 않는다.
 */
export function personalRouteAllowed(route: Pick<PersonalRoute, 'workspaceAccountOnly'>, systemAdmin: boolean): boolean {
  return !(route.workspaceAccountOnly && systemAdmin);
}

/** 이 화면에 들어갈 수 있는가 — 권한 조건이 여럿이면 하나만 있어도 된다 */
export function routeAllowed(route: Pick<WorkspaceRoute, 'permission'>, permissions: readonly PermissionCode[]): boolean {
  if (!route.permission) {
    return true;
  }
  const needed = Array.isArray(route.permission) ? route.permission : [route.permission];
  return needed.some((p) => permissions.includes(p));
}
