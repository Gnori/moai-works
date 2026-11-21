import type { ReactNode } from 'react';

import { useOptionalWorkspace } from '@/features/workspace/workspace-context';
import type { PermissionCode } from '@/shared/api/types';
import { ForbiddenState } from '@/shared/ui/States';

/**
 * 워크스페이스 권한으로 화면을 가린다 — <b>표시만</b>. 판정은 api 가 요청마다 다시 한다.
 * 권한 목록은 서버가 계산해 준 것(워크스페이스 상세의 myPermissions)만 쓰고, 화면이 추측하지 않는다.
 * 모르면 숨긴다(워크스페이스 밖이거나 아직 못 불러왔으면 false).
 */

/** `const can = useCan(); can('member.invite')` */
export function useCan(): (permission: PermissionCode) => boolean {
  const ws = useOptionalWorkspace();
  return (permission) => ws?.workspace.myPermissions.includes(permission) ?? false;
}

/** 권한이 있을 때만 children, 없으면 fallback(기본 없음) */
export function Can({ permission, children, fallback = null }: {
  permission: PermissionCode;
  children: ReactNode;
  fallback?: ReactNode;
}) {
  const can = useCan();
  return <>{can(permission) ? children : fallback}</>;
}

/** 경로 가드 — 이 워크스페이스에서 권한이 없으면 화면 대신 "볼 권한이 없습니다". routes.tsx 의 access.permission 으로 붙는다. */
export function RequireWorkspacePermission({ permission, children }: {
  /** 여럿이면 그 가운데 하나만 있어도 된다 */
  permission: PermissionCode | PermissionCode[];
  children: ReactNode;
}) {
  const can = useCan();
  const allowed = Array.isArray(permission) ? permission.some(can) : can(permission);
  return <>{allowed ? children : <ForbiddenState />}</>;
}
