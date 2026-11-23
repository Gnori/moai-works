import { describe, expect, it } from 'vitest';

import { isSystemAdmin } from '@/features/auth/guards';
import type { PermissionCode } from '@/shared/api/types';

import {
  accountRoutes,
  auditPath,
  guestRoutes,
  issueEditPath,
  issuePath,
  issueRevisionsPath,
  newIssuePath,
  workspaceIssuesPath,
  mcpAccessPath,
  notificationsPath,
  platformRoutes,
  projectEffortPath,
  projectModulesPath,
  readSettingsSection,
  routeAllowed,
  settingsSectionAllowed,
  settingsSections,
  skillEditPath,
  skillPath,
  skillsPath,
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

describe('프로젝트 모듈 화면', () => {
  it('프로젝트 화면 아래 modules — 보기는 독자 누구나(메뉴는 화면이 project.manage 로 가린다)', () => {
    const projectRoute = workspaceRoutes.find((r) => r.path === 'projects/:projectKey');
    expect(projectRoute?.children?.map((c) => c.path)).toContain('modules');
    expect(projectModulesPath('team-a', 'moai-works')).toBe('/w/team-a/projects/moai-works/modules');
  });
});

describe('Agent 스킬 화면 (시스템 관리)', () => {
  const skillRoutes = platformRoutes.filter((r) => r.path.startsWith('/admin/skills'));

  it('모두 system-admin 이고, 메뉴에는 목록 하나만 있다', () => {
    expect(skillRoutes.map((r) => r.path)).toEqual(['/admin/skills', '/admin/skills/new', '/admin/skills/view/:name', '/admin/skills/edit/:name']);
    expect(skillRoutes.every((r) => r.role === 'system-admin')).toBe(true);
    expect(skillRoutes.filter((r) => r.nav).map((r) => r.nav?.label)).toEqual(['skills']);
  });

  it('하위 화면 주소는 이름 앞에 고정 낱말이 있어 이름이 new 여도 부딪히지 않는다', () => {
    expect(skillPath('new')).toBe('/admin/skills/view/new');
    expect(skillEditPath('new')).toBe('/admin/skills/edit/new');
    expect(skillPath('moai-works-issues', { tab: 'revisions', rev: 3, from: 1 })).toBe('/admin/skills/view/moai-works-issues?tab=revisions&rev=3&from=1');
    expect(skillsPath({ deleted: true })).toBe('/admin/skills?deleted=1');
  });
});

describe('개인 설정 (대화상자의 칸)', () => {
  const systemAdmin = isSystemAdmin({ platformRoles: ['system-admin'] });
  const user = isSystemAdmin({ platformRoles: [] });
  const visible = (admin: boolean) => settingsSections.filter((s) => settingsSectionAllowed(s, admin)).map((s) => s.key);

  it('사용자에게는 내 프로필 · 비밀번호 · 화면 설정 · 알림 설정 · 내 MCP 키 (이 순서로 왼쪽 메뉴에)', () => {
    expect(visible(user)).toEqual(['profile', 'password', 'preferences', 'notifications', 'mcp-keys']);
    expect(settingsSections.map((s) => s.nav.label)).toEqual(['profile', 'password', 'preferences', 'notificationSettings', 'myMcpKeys']);
  });

  it('시스템 관리자 계정에는 알림 설정 · 내 MCP 키가 없다 (워크스페이스 · 알림함이 없다) — 비밀번호는 있다', () => {
    expect(systemAdmin).toBe(true);
    expect(visible(systemAdmin)).toEqual(['profile', 'password', 'preferences']);
  });

  it('예전 주소(/me/…)는 그 칸으로 보낸다 — 비밀번호 칸은 처음부터 대화상자라 예전 주소가 없다', () => {
    expect(settingsSections.filter((s) => s.legacyPath).map((s) => [s.legacyPath, s.key])).toEqual([
      ['/me/profile', 'profile'],
      ['/me/preferences', 'preferences'],
      ['/me/notifications', 'notifications'],
      ['/me/mcp-keys', 'mcp-keys'],
    ]);
  });

  it('열린 칸은 주소의 ?settings= — 없으면 닫힘, 모르는 값이면 첫 칸', () => {
    expect(readSettingsSection(new URLSearchParams())).toBeNull();
    expect(readSettingsSection(new URLSearchParams('settings=mcp-keys'))).toBe('mcp-keys');
    expect(readSettingsSection(new URLSearchParams('settings=password'))).toBe('password');
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

describe('비밀번호 찾기 화면', () => {
  it('로그인 없이 보는 화면이다 — 이메일 칸과 코드 · 새 비밀번호 칸', () => {
    expect(guestRoutes.map((r) => r.path)).toEqual(expect.arrayContaining(['/password-reset', '/password-reset/confirm']));
  });
});

describe('알림함', () => {
  it('알림함은 워크스페이스 영역의 계정 화면이다 (시스템 관리자 계정은 안내를 본다)', () => {
    expect(accountRoutes.map((r) => r.path)).toContain(notificationsPath);
    expect(notificationsPath).toBe('/notifications');
  });
});

describe('MCP 접근 기록 화면', () => {
  const route = workspaceRoutes.find((r) => r.path === 'mcp-access');

  it('audit.read 가 있어야 들어가고 메뉴에 감사 기록 바로 뒤로 보인다', () => {
    expect(route?.permission).toBe('audit.read');
    expect(route?.nav?.label).toBe('mcpAccess');
    expect(routeAllowed(route ?? {}, member)).toBe(false);
    expect(routeAllowed(route ?? {}, admin)).toBe(true);
    const order = workspaceRoutes.map((r) => r.path);
    expect(order.indexOf('mcp-access')).toBe(order.indexOf('audit') + 1);
  });

  it('키 화면의 링크 — 한 키의 접근 기록', () => {
    const key = '11111111-2222-3333-4444-555555555555';
    expect(mcpAccessPath('team-a', { key })).toBe(`/w/team-a/mcp-access?key=${key}`);
    expect(mcpAccessPath('team-a')).toBe('/w/team-a/mcp-access');
  });
});

describe('일감 — 워크스페이스 단위 번호 · 정본 주소 (2026-11-24, Redmine 의 /issues · /issues/:id)', () => {
  it('워크스페이스 일감 목록은 멤버 누구나(workspace.read) — 메뉴에서 프로젝트 바로 뒤', () => {
    const route = workspaceRoutes.find((r) => r.path === 'issues');
    expect(route?.permission).toBe('workspace.read');
    expect(route?.nav?.label).toBe('issues');
    expect(route?.nav?.end).toBe(true);
    expect(routeAllowed(route ?? {}, member)).toBe(true);
    expect(routeAllowed(route ?? {}, [])).toBe(false);
    const nav = workspaceRoutes.filter((r) => r.nav).map((r) => r.path);
    expect(nav.indexOf('issues')).toBe(nav.indexOf('projects') + 1);
    expect(workspaceIssuesPath('team-a')).toBe('/w/team-a/issues');
    expect(workspaceIssuesPath('team-a', 'project=web&q=%2312')).toBe('/w/team-a/issues?project=web&q=%2312');
  });

  it('일감 하나는 프로젝트 없이 번호로 — 보기 · 수정 · 예전 리비전 주소가 그 아래에 있다', () => {
    const route = workspaceRoutes.find((r) => r.path === 'issues/:number');
    expect(route?.nav).toBeUndefined();
    expect(route?.children?.map((c) => c.path)).toEqual(['', 'edit', 'history']);
    expect(issuePath('team-a', 57)).toBe('/w/team-a/issues/57');
    expect(issueEditPath('team-a', 57)).toBe('/w/team-a/issues/57/edit');
    expect(issueRevisionsPath('team-a', 57, { rev: 3 })).toBe('/w/team-a/issues/57?tab=revisions&rev=3');
    // 새 일감은 만들 프로젝트를 정해야 해서 프로젝트 주소에 남는다
    expect(newIssuePath('team-a', 'web', 57)).toBe('/w/team-a/projects/web/issues/new?parent=57');
  });

  it('예전 프로젝트 주소(projects/:projectKey/issues/:number · /edit · /history)는 정본 주소로 돌리는 화면이 받는다', () => {
    const legacy = workspaceRoutes.filter((r) => r.path.startsWith('projects/:projectKey/issues/:number')).map((r) => r.path);
    expect(legacy).toEqual(['projects/:projectKey/issues/:number', 'projects/:projectKey/issues/:number/edit', 'projects/:projectKey/issues/:number/history']);
    const projectRoute = workspaceRoutes.find((r) => r.path === 'projects/:projectKey');
    expect(projectRoute?.children?.map((c) => c.path)).toContain('issues/new');
    expect(projectRoute?.children?.map((c) => c.path)).not.toContain('issues/:number');
  });
});
