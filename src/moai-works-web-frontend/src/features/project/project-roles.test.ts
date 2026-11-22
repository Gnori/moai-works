import { describe, expect, it } from 'vitest';

import type { PermissionCode } from '@/shared/api/types';

import { canGrantProjectRole, projectRolePermissions, sortProjectRoles } from './project-roles';

describe('프로젝트 역할 (api ProjectRole 과 같아야 한다)', () => {
  it('위 역할이 아래 역할의 권한을 모두 담는다', () => {
    const order = ['project-reader', 'project-editor', 'project-curator', 'project-admin'] as const;
    for (let i = 1; i < order.length; i += 1) {
      const lower = projectRolePermissions[order[i - 1] ?? 'project-reader'];
      const upper = projectRolePermissions[order[i] ?? 'project-admin'];
      expect(lower.every((p) => upper.includes(p))).toBe(true);
    }
  });

  it('내 권한 안의 역할만 주거나 뺄 수 있다', () => {
    const workspaceAdmin: PermissionCode[] = [...projectRolePermissions['project-admin'], 'project.create', 'project.delete'];
    const projectCurator: PermissionCode[] = [...projectRolePermissions['project-curator'], 'workspace.read'];
    expect(canGrantProjectRole('project-admin', workspaceAdmin)).toBe(true);
    expect(canGrantProjectRole('project-curator', projectCurator)).toBe(true);
    expect(canGrantProjectRole('project-admin', projectCurator)).toBe(false);
    expect(canGrantProjectRole('project-reader', ['issue.read'])).toBe(false);
  });

  it('역할을 독자 → 관리자 순으로 놓는다', () => {
    expect(sortProjectRoles(['project-admin', 'unknown', 'project-reader', 'project-editor'])).toEqual([
      'project-reader',
      'project-editor',
      'project-admin',
      'unknown',
    ]);
  });
});
