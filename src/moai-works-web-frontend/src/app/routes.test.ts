import { describe, expect, it } from 'vitest';

import { isSystemAdmin } from '@/features/auth/guards';
import type { PermissionCode } from '@/shared/api/types';

import {
  accountRoutes,
  auditPath,
  notificationsPath,
  projectEffortPath,
  readSettingsSection,
  routeAllowed,
  settingsSectionAllowed,
  settingsSections,
  withSettings,
  workspaceRoutes,
} from './routes';

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

describe('공수 집계 화면', () => {
  const route = workspaceRoutes.find((r) => r.path === 'effort');

  it('멤버 누구나(workspace.read) 메뉴에서 연다 — 어느 프로젝트가 들어가는지는 api 가 정한다', () => {
    expect(route?.permission).toBe('workspace.read');
    expect(route?.nav?.label).toBe('effort');
    expect(routeAllowed(route ?? {}, member)).toBe(true);
    expect(routeAllowed(route ?? {}, [])).toBe(false);
  });

  it('프로젝트 하나의 공수 — 프로젝트 화면 아래, 조건을 이어 연다', () => {
    const projectRoute = workspaceRoutes.find((r) => r.path === 'projects/:projectKey');
    expect(projectRoute?.children?.map((c) => c.path)).toContain('effort');
    expect(projectEffortPath('team-a', 'ea')).toBe('/w/team-a/projects/ea/effort');
    expect(projectEffortPath('team-a', 'ea', 'range=all')).toBe('/w/team-a/projects/ea/effort?range=all');
  });
});

describe('개인 설정 (대화상자의 칸)', () => {
  const systemAdmin = isSystemAdmin({ platformRoles: ['system-admin'] });
  const user = isSystemAdmin({ platformRoles: [] });
  const visible = (admin: boolean) => settingsSections.filter((s) => settingsSectionAllowed(s, admin)).map((s) => s.key);

  it('사용자에게는 내 프로필 · 화면 설정 · 알림 설정 · 내 MCP 키 (이 순서로 왼쪽 메뉴에)', () => {
    expect(visible(user)).toEqual(['profile', 'preferences', 'notifications', 'mcp-keys']);
    expect(settingsSections.map((s) => s.nav.label)).toEqual(['profile', 'preferences', 'notificationSettings', 'myMcpKeys']);
  });

  it('시스템 관리자 계정에는 알림 설정 · 내 MCP 키가 없다 (워크스페이스 · 알림함이 없다)', () => {
    expect(systemAdmin).toBe(true);
    expect(visible(systemAdmin)).toEqual(['profile', 'preferences']);
  });

  it('예전 주소(/me/…)는 그 칸으로 보낸다', () => {
    expect(settingsSections.map((s) => [s.legacyPath, s.key])).toEqual([
      ['/me/profile', 'profile'],
      ['/me/preferences', 'preferences'],
      ['/me/notifications', 'notifications'],
      ['/me/mcp-keys', 'mcp-keys'],
    ]);
  });

  it('열린 칸은 주소의 ?settings= — 없으면 닫힘, 모르는 값이면 첫 칸', () => {
    expect(readSettingsSection(new URLSearchParams())).toBeNull();
    expect(readSettingsSection(new URLSearchParams('settings=mcp-keys'))).toBe('mcp-keys');
    expect(readSettingsSection(new URLSearchParams('settings=nope'))).toBe('profile');
    expect(readSettingsSection(new URLSearchParams('settings='))).toBe('profile');
  });

  it('열고 · 칸을 바꾸고 · 닫아도 다른 조건은 그대로 둔다', () => {
    expect(withSettings('', 'profile')).toBe('?settings=profile');
    expect(withSettings('?tab=unread', 'mcp-keys')).toBe('?tab=unread&settings=mcp-keys');
    expect(withSettings('?tab=unread&settings=mcp-keys', 'preferences')).toBe('?tab=unread&settings=preferences');
    expect(withSettings('?tab=unread&settings=mcp-keys', null)).toBe('?tab=unread');
    expect(withSettings('?settings=profile', null)).toBe('');
  });
});

describe('알림함', () => {
  it('알림함은 워크스페이스 영역의 계정 화면이다 (시스템 관리자 계정은 안내를 본다)', () => {
    expect(accountRoutes.map((r) => r.path)).toContain(notificationsPath);
    expect(notificationsPath).toBe('/notifications');
  });
});
