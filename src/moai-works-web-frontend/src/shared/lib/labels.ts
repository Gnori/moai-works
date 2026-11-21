import { useCallback, useMemo } from 'react';
import { useTranslation } from 'react-i18next';

import {
  builtinRoleKeys,
  type AccountStatus,
  type BuiltinRoleKey,
  type InvitationStatus,
  type JoinPolicy,
  type JoinRequestStatus,
  type Member,
  type PermissionCode,
  type WorkspaceVisibility,
} from '@/shared/api/types';

/**
 * 서버 코드값 → 화면 말. 말은 모두 i18n 리소스에 있고, 여기는 "어느 키인가"만 정한다(타입으로 검사됨).
 */

export type PermissionGroupKey = 'workspace' | 'member' | 'mcp';

/** 권한 코드 묶음 (design/03 §4) — 역할 표 · 홈 화면이 이 순서로 그린다. 프로젝트 · 일감 묶음은 그 기능을 만들 때 더한다 */
export const permissionGroups: { group: PermissionGroupKey; codes: PermissionCode[] }[] = [
  { group: 'workspace', codes: ['workspace.read', 'workspace.settings.manage', 'workspace.ownership.transfer', 'workspace.delete'] },
  {
    group: 'member',
    codes: ['member.read', 'member.invite', 'member.request.review', 'member.remove', 'member.role.assign', 'role.manage'],
  },
  {
    group: 'mcp',
    codes: ['mcp.endpoint.manage', 'mcp.key.issue.personal', 'mcp.key.issue.service', 'mcp.key.manage', 'audit.read', 'usage.read'],
  },
];

/** 소유자 역할만 가질 수 있는 권한 (사용자 정의 역할에 넣을 수 없다) */
export const ownerOnlyPermissions: readonly PermissionCode[] = ['workspace.ownership.transfer', 'workspace.delete'];

export function isBuiltinRole(key: string): key is BuiltinRoleKey {
  return (builtinRoleKeys as readonly string[]).includes(key);
}

/** 역할 칩 색 (theme palette 이름) — 사용자 정의 역할은 기본색 */
export function roleColor(key: string): 'primary' | 'secondary' | 'info' | 'default' {
  switch (key) {
    case 'owner':
      return 'primary';
    case 'admin':
      return 'info';
    default:
      return 'default';
  }
}

/** 역할 이름 — 기본 역할은 번역, 사용자 정의 역할은 서버가 준 이름(customNames), 없으면 키 */
export function useRoleName(customNames?: Record<string, string>) {
  const { t } = useTranslation();
  return useCallback(
    (key: string) => (isBuiltinRole(key) ? t(`roles.${key}`) : (customNames?.[key] ?? key)),
    [t, customNames],
  );
}

/** 코드값 표시 이름 묶음 */
export function useLabels() {
  const { t, i18n } = useTranslation(['workspace', 'admin']);
  return useMemo(
    () => ({
      permission: (code: PermissionCode) => t(`workspace:permissions.${code}`),
      permissionGroup: (group: PermissionGroupKey) => t(`workspace:permissionGroups.${group}`),
      joinPolicy: (policy: JoinPolicy) => t(`workspace:joinPolicy.${policy}`),
      joinPolicyShort: (policy: JoinPolicy) => t(`workspace:joinPolicyShort.${policy}`),
      visibility: (visibility: WorkspaceVisibility) => t(`workspace:visibility.${visibility}`),
      visibilityHelp: (visibility: WorkspaceVisibility) => t(`workspace:visibilityHelp.${visibility}`),
      joinRequestStatus: (status: JoinRequestStatus) => t(`workspace:joinRequestStatus.${status}`),
      invitationStatus: (status: InvitationStatus) => t(`workspace:invitationStatus.${status}`),
      joinedVia: (via: Member['joinedVia']) => t(`workspace:joinedVia.${via}`),
      accountStatus: (status: AccountStatus) => t(`admin:accountStatus.${status}`),
      /** 감사 동작은 서버가 새로 더할 수 있다 — 리소스에 없으면 코드 그대로 */
      auditAction: (action: string) => {
        const key = `workspace:auditActions.${action}`;
        // 서버 값이라 타입으로 좁힐 수 없다 — 있는지 확인한 뒤에만 번역한다
        return i18n.exists(key) ? (i18n.t as (k: string) => string)(key) : action;
      },
    }),
    [t, i18n],
  );
}
