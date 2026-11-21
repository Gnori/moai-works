import type { PermissionCode, Role } from '@/shared/api/types';
import { ownerOnlyPermissions } from '@/shared/lib/labels';

/**
 * 역할을 다루는 규칙 — api 와 같은 규칙을 화면에도 적어, 못 하는 일은 처음부터 막아 보인다.
 * 표시만 한다. 실제 판정은 api 가 한다(MembershipService.requireCanGrant · RoleService.validate).
 *
 * mine 은 서버가 계산해 준 이 워크스페이스의 내 권한(workspace.myPermissions)이다.
 */

/** 이 역할을 남에게 줄 수 있는가 — 역할의 권한을 모두 가져야 하고, 소유자 역할은 소유자(소유권 이전 권한)만 */
export function canGrantRole(role: Pick<Role, 'key' | 'permissions'>, mine: readonly PermissionCode[]): boolean {
  if (role.key === 'owner' && !mine.includes('workspace.ownership.transfer')) {
    return false;
  }
  return role.permissions.every((p) => mine.includes(p));
}

/** 이 권한 코드를 사용자 정의 역할에 넣을 수 있는가 — 내가 가졌고, 소유자 전용이 아니어야 */
export function canIncludePermission(code: PermissionCode, mine: readonly PermissionCode[]): boolean {
  return !ownerOnlyPermissions.includes(code) && mine.includes(code);
}

/** 사용자 정의 역할을 고치거나 지울 수 있는가 — 그 역할의 지금 권한을 모두 가져야(더 큰 역할을 줄일 수 없다) */
export function canEditRole(role: Pick<Role, 'builtin' | 'permissions'>, mine: readonly PermissionCode[]): boolean {
  return !role.builtin && role.permissions.every((p) => mine.includes(p));
}
