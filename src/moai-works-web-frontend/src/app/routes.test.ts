import { describe, expect, it } from 'vitest';

import { isSystemAdmin } from '@/features/auth/guards';
import type { PermissionCode } from '@/shared/api/types';

import { auditPath, personalRouteAllowed, personalRoutes, routeAllowed, workspaceRoutes } from './routes';

/**
 * 경로 표의 접근 조건 — 화면은 서버가 준 myPermissions · platformRoles 로 숨기기만 한다(판정은 api).
 */

const member: PermissionCode[] = ['workspace.read', 'member.read', 'issue.read', 'issue.comment', 'mcp.key.issue.personal'];
const admin: PermissionCode[] = [...member, 'mcp.key.manage', 'audit.read'];

describe('워크스페이스 MCP 키 화면', () => {
  const route = workspaceRoutes.find((r) => r.path === 'mcp-keys');

  it('mcp.key.manage 가 있어야 들어가고 메뉴에 보인다', () => {
    expect(route?.permission).toBe('mcp.key.manage');
    expect(route?.nav?.label).toBe('mcpKeys');
    expect(routeAllowed(route ?? {}, member)).toBe(false);
    expect(routeAllowed(route ?? {}, admin)).toBe(true);
  });

  it('개인 MCP 키 권한만으로는 관리 화면이 보이지 않는다', () => {
    expect(routeAllowed(route ?? {}, ['mcp.key.issue.personal'])).toBe(false);
  });

  it('감사 기록 링크 — 한 사람의 MCP 키 기록', () => {
    const owner = '11111111-2222-3333-4444-555555555555';
    expect(auditPath('team-a', { person: owner, action: 'mcp.key' })).toBe(`/w/team-a/audit?person=${owner}&action=mcp.key`);
    expect(auditPath('team-a')).toBe('/w/team-a/audit');
  });
});

describe('개인 설정 화면', () => {
  const systemAdmin = isSystemAdmin({ platformRoles: ['system-admin'] });
  const user = isSystemAdmin({ platformRoles: [] });
  const visible = (admin: boolean) => personalRoutes.filter((r) => personalRouteAllowed(r, admin)).map((r) => r.path);

  it('사용자에게는 내 프로필 · 화면 설정 · 내 MCP 키', () => {
    expect(visible(user)).toEqual(['/me/profile', '/me/preferences', '/me/mcp-keys']);
  });

  it('시스템 관리자 계정에는 내 MCP 키가 없다 (워크스페이스가 없다)', () => {
    expect(systemAdmin).toBe(true);
    expect(visible(systemAdmin)).toEqual(['/me/profile', '/me/preferences']);
    expect(personalRoutes.find((r) => r.path === '/me/mcp-keys')?.workspaceAccountOnly).toBe(true);
  });

  it('모든 개인 설정 화면에 메뉴 이름이 있다', () => {
    expect(personalRoutes.map((r) => r.nav.label)).toEqual(['profile', 'preferences', 'myMcpKeys']);
  });
});
