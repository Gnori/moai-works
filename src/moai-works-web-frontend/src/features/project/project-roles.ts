import { projectRoleKeys, type PermissionCode, type ProjectRoleKey } from '@/shared/api/types';

/**
 * 프로젝트 역할 4개가 담는 권한 — api 의 ProjectRole 열거형과 같다(역할이 고정이라 서버도 코드가 정한다).
 * 화면은 "내 권한 밖의 역할은 주거나 뺄 수 없다"를 미리 꺼 두는 데만 쓴다. 판정은 api(ProjectRoleService)가 한다.
 */
export const projectRolePermissions: Record<ProjectRoleKey, readonly PermissionCode[]> = {
  'project-reader': ['issue.read', 'issue.comment'],
  'project-editor': ['issue.read', 'issue.comment', 'issue.create', 'issue.edit'],
  'project-curator': ['issue.read', 'issue.comment', 'issue.create', 'issue.edit', 'issue.manager.assign'],
  'project-admin': [
    'issue.read',
    'issue.comment',
    'issue.create',
    'issue.edit',
    'issue.manager.assign',
    'issue.delete',
    'project.manage',
  ],
};

export function isProjectRole(key: string): key is ProjectRoleKey {
  return (projectRoleKeys as readonly string[]).includes(key);
}

/** 이 프로젝트 역할을 주거나 뺄 수 있는가 — 그 역할의 권한을 이 프로젝트에서 모두 가져야 한다(권한 상승 방지) */
export function canGrantProjectRole(role: ProjectRoleKey, mine: readonly PermissionCode[]): boolean {
  return projectRolePermissions[role].every((p) => mine.includes(p));
}

/** 역할 목록을 고정 순서(독자 → 관리자)로 — 모르는 key 는 뒤에 그대로 */
export function sortProjectRoles(roles: readonly string[]): string[] {
  const rank = (key: string) => (isProjectRole(key) ? projectRoleKeys.indexOf(key) : projectRoleKeys.length);
  return [...roles].sort((a, b) => rank(a) - rank(b) || a.localeCompare(b));
}
