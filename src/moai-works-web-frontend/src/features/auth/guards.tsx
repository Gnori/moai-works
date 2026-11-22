import type { ReactNode } from 'react';
import { Navigate, Outlet, useLocation } from 'react-router';

import { systemHomePath } from '@/app/routes';
import type { Me } from '@/shared/api/types';
import { safeReturnPath } from '@/shared/lib/post-login-path';
import { LoadingState } from '@/shared/ui/States';

import { useMe, useSession } from './session';

/**
 * 경로 가드 — 로그인 여부와 플랫폼 역할. 워크스페이스 권한 가드는 access.tsx.
 * 모든 가드는 화면 표시만 막는다. 실제 판정은 api 가 요청마다 한다(가드를 우회해도 api 가 401 · 403 · 404).
 */

/**
 * 로그인해야 보이는 화면. 안 됐으면 로그인으로 보내고, 끝나면 이 주소로 돌아온다(같은 사이트 경로만).
 * 단, 사람이 직접 로그아웃했으면 돌아올 주소를 달지 않는다 — 다음 사람이 앞 사람의 화면으로 가지 않게.
 */
export function RequireAuth() {
  const { status, signedOutByUser } = useSession();
  const location = useLocation();
  if (status === 'loading') {
    return <LoadingState fullScreen />;
  }
  if (status === 'unauthenticated') {
    if (signedOutByUser) {
      return <Navigate to="/sign-in" replace />;
    }
    const returnTo = encodeURIComponent(location.pathname + location.search);
    return <Navigate to={`/sign-in?returnTo=${returnTo}`} replace />;
  }
  return <Outlet />;
}

/** 로그인 · 가입 화면 — 이미 로그인했으면 들어갈 곳으로. */
export function GuestOnly() {
  const { status, me } = useSession();
  const location = useLocation();
  if (status === 'loading') {
    return <LoadingState fullScreen />;
  }
  if (status === 'authenticated' && me) {
    const returnTo = safeReturnPath(new URLSearchParams(location.search).get('returnTo'));
    return <Navigate to={postLoginPath(me, returnTo)} replace />;
  }
  return <Outlet />;
}

/**
 * 워크스페이스 영역(워크스페이스 화면 · 찾기 · 만들기 · 초대 링크)의 문.
 * 시스템 관리자 계정은 시스템만 관리한다 — 이 영역 대신 안내(fallback)를 보인다. api 도 같은 계정을 막는다.
 */
export function WorkspaceAreaOnly({ fallback }: { fallback: ReactNode }) {
  const me = useMe();
  if (isSystemAdmin(me)) {
    return <>{fallback}</>;
  }
  return <Outlet />;
}

/** 플랫폼 역할(system-admin …)이 있어야 보이는 화면. */
export function RequirePlatformRole({ role, children }: { role: string; children: ReactNode }) {
  const { hasPlatformRole } = useSession();
  if (!hasPlatformRole(role)) {
    return <Navigate to="/forbidden" replace />;
  }
  return <>{children}</>;
}

/** 시스템 관리자 계정인가 — 화면 표시용. 판정은 api 가 한다 */
export function isSystemAdmin(me: Pick<Me, 'platformRoles'>): boolean {
  return me.platformRoles.includes('system-admin');
}

/** 로그인한 사람이 들어갈 곳 — 시스템 관리자는 시스템 관리 화면, 그 밖에는 워크스페이스(없으면 찾기) */
export function landingPath(me: Pick<Me, 'landingWorkspace' | 'platformRoles'>): string {
  if (isSystemAdmin(me)) {
    return systemHomePath;
  }
  return me.landingWorkspace ? `/w/${me.landingWorkspace}` : '/workspaces/discover';
}

/**
 * 로그인 직후 갈 곳 — 돌아갈 주소(returnTo)가 있으면 그리로.
 * 시스템 관리자는 시스템 관리 화면의 주소일 때만 돌아가고, 워크스페이스 쪽 주소면 시스템 관리 화면으로 간다.
 */
export function postLoginPath(me: Pick<Me, 'landingWorkspace' | 'platformRoles'>, returnTo: string | null): string {
  if (isSystemAdmin(me)) {
    return returnTo?.startsWith('/admin/') ? returnTo : systemHomePath;
  }
  return returnTo ?? landingPath(me);
}
