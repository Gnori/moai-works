import { describe, expect, it } from 'vitest';

import type { PermissionCode } from '@/shared/api/types';

import { canEditRole, canGrantRole, canIncludePermission } from './role-rules';

const curator: PermissionCode[] = ['workspace.read', 'member.read', 'issue.read', 'issue.edit'];
const admin: PermissionCode[] = [...curator, 'member.invite', 'member.role.assign', 'role.manage'];

describe('역할 규칙 (api 와 같아야 한다)', () => {
  it('내 권한 안의 역할만 줄 수 있다', () => {
    expect(canGrantRole({ key: 'curator', permissions: curator }, admin)).toBe(true);
    expect(canGrantRole({ key: 'admin', permissions: admin }, curator)).toBe(false);
  });

  it('소유자 역할은 소유권 이전 권한이 있어야 줄 수 있다', () => {
    expect(canGrantRole({ key: 'owner', permissions: [] }, admin)).toBe(false);
    expect(canGrantRole({ key: 'owner', permissions: [] }, [...admin, 'workspace.ownership.transfer'])).toBe(true);
  });

  it('소유자 전용 권한과 내게 없는 권한은 역할에 넣을 수 없다', () => {
    const owner: PermissionCode[] = [...admin, 'workspace.ownership.transfer', 'workspace.delete'];
    expect(canIncludePermission('workspace.delete', owner)).toBe(false);
    expect(canIncludePermission('workspace.ownership.transfer', owner)).toBe(false);
    expect(canIncludePermission('role.manage', curator)).toBe(false);
    expect(canIncludePermission('issue.edit', curator)).toBe(true);
  });

  it('기본 역할과 내게 없는 권한이 든 역할은 고칠 수 없다', () => {
    expect(canEditRole({ builtin: true, permissions: [] }, admin)).toBe(false);
    expect(canEditRole({ builtin: false, permissions: admin }, curator)).toBe(false);
    expect(canEditRole({ builtin: false, permissions: curator }, admin)).toBe(true);
  });
});
