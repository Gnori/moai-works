import type { ReactNode } from 'react';
import { Navigate, Outlet, useLocation } from 'react-router';

import { safeReturnPath } from '@/shared/lib/post-login-path';
import { LoadingState } from '@/shared/ui/States';

import { useSession } from './session';

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
    return <Navigate to={returnTo ?? landingPath(me.landingWorkspace)} replace />;
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

export function landingPath(landing: string | null): string {
  return landing ? `/w/${landing}` : '/workspaces/discover';
}
