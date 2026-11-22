import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';

import '@/shared/i18n/i18n';
import { invitationApi, projectApi, workspaceApi } from '@/shared/api/endpoints';
import type * as Endpoints from '@/shared/api/endpoints';
import type { Me, PermissionCode, WorkspaceDetail } from '@/shared/api/types';
import { ConfirmProvider } from '@/shared/ui/ConfirmDialog';
import { AppearanceProvider } from '@/theme/AppearanceProvider';

import { AppShell } from './AppShell';

/**
 * 사이드바 — 메뉴는 서버가 준 내 권한(myPermissions) · 플랫폼 역할로만 거른다.
 * 워크스페이스 MCP 키는 mcp.key.manage 가 있을 때만, 개인 설정의 내 MCP 키는 시스템 관리자 계정에 없다.
 */

vi.mock('@/shared/api/endpoints', async (importOriginal) => {
  const actual = await importOriginal<typeof Endpoints>();
  return {
    ...actual,
    workspaceApi: { ...actual.workspaceApi, detail: vi.fn() },
    projectApi: { ...actual.projectApi, list: vi.fn() },
    invitationApi: { ...actual.invitationApi, mine: vi.fn() },
  };
});

const session = vi.hoisted(() => ({ me: null as unknown }));
vi.mock('@/features/auth/session', () => ({
  useMe: () => session.me,
  useSession: () => ({ signOut: vi.fn() }),
}));

function me(platformRoles: string[]): Me {
  const admin = platformRoles.includes('system-admin');
  return {
    id: 'aaaaaaaa-0000-0000-0000-000000000001',
    username: 'kim',
    email: 'kim@example.com',
    displayName: '김철수',
    status: 'ACTIVE',
    platformRoles,
    personalWorkspace: admin ? null : 'mine',
    landingWorkspace: admin ? null : 'team-a',
    workspaces: admin ? [] : [{ slug: 'team-a', name: '팀 A', kind: 'TEAM', status: 'ACTIVE', myRoles: ['admin'], memberCount: 3 }],
    lastLoginAt: null,
    teamWorkspaceLimit: 0,
    teamWorkspacesCreated: 0,
  };
}

function workspace(permissions: PermissionCode[]): WorkspaceDetail {
  return { slug: 'team-a', name: '팀 A', kind: 'TEAM', myRoles: ['admin'], myPermissions: permissions } as unknown as WorkspaceDetail;
}

function renderAt(path: string) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <AppearanceProvider>
      <QueryClientProvider client={client}>
        <ConfirmProvider>
          <MemoryRouter initialEntries={[path]}>
            <Routes>
              <Route element={<AppShell />}>
                <Route path="/me/preferences" element={<div>화면 설정 화면</div>} />
                <Route path="*" element={<div>본문</div>} />
              </Route>
            </Routes>
          </MemoryRouter>
        </ConfirmProvider>
      </QueryClientProvider>
    </AppearanceProvider>,
  );
}

beforeAll(() => {
  // jsdom 에는 없다 — 넓은 화면(고정 사이드바)으로 그린다
  window.matchMedia = (query: string) =>
    ({
      matches: query.includes('min-width'),
      media: query,
      onchange: null,
      addEventListener: () => undefined,
      removeEventListener: () => undefined,
      addListener: () => undefined,
      removeListener: () => undefined,
      dispatchEvent: () => false,
    }) as MediaQueryList;
});

beforeEach(() => {
  vi.mocked(projectApi.list).mockResolvedValue([]);
  vi.mocked(invitationApi.mine).mockResolvedValue([]);
});

afterEach(() => {
  vi.clearAllMocks();
});

describe('사이드바 메뉴', () => {
  it('mcp.key.manage 가 있으면 워크스페이스 MCP 키, 개인 설정에는 내 프로필 · 화면 설정 · 내 MCP 키', async () => {
    session.me = me([]);
    vi.mocked(workspaceApi.detail).mockResolvedValue(workspace(['workspace.read', 'mcp.key.issue.personal', 'mcp.key.manage', 'audit.read']));
    renderAt('/w/team-a');
    const nav = screen.getByRole('navigation', { name: '주 메뉴' });
    expect(await within(nav).findByRole('link', { name: 'MCP 키' })).toHaveAttribute('href', '/w/team-a/mcp-keys');
    expect(within(nav).getByRole('link', { name: '감사 기록' })).toHaveAttribute('href', '/w/team-a/audit');
    expect(within(nav).getByText('개인 설정')).toBeInTheDocument();
    expect(within(nav).getByRole('link', { name: '내 프로필' })).toHaveAttribute('href', '/me/profile');
    expect(within(nav).getByRole('link', { name: '화면 설정' })).toHaveAttribute('href', '/me/preferences');
    expect(within(nav).getByRole('link', { name: '내 MCP 키' })).toHaveAttribute('href', '/me/mcp-keys');
  });

  it('mcp.key.manage 가 없으면 워크스페이스 MCP 키 메뉴가 없다 (내 MCP 키는 있다)', async () => {
    session.me = me([]);
    vi.mocked(workspaceApi.detail).mockResolvedValue(workspace(['workspace.read', 'member.read', 'mcp.key.issue.personal']));
    renderAt('/w/team-a');
    const nav = screen.getByRole('navigation', { name: '주 메뉴' });
    expect(await within(nav).findByRole('link', { name: '멤버' })).toBeInTheDocument();
    expect(within(nav).queryByRole('link', { name: 'MCP 키' })).not.toBeInTheDocument();
    expect(within(nav).getByRole('link', { name: '내 MCP 키' })).toBeInTheDocument();
  });

  it('시스템 관리자 계정은 시스템 관리 · 내 프로필 · 화면 설정만 (내 MCP 키 없음)', () => {
    session.me = me(['system-admin']);
    renderAt('/admin/accounts');
    const nav = screen.getByRole('navigation', { name: '주 메뉴' });
    expect(within(nav).getByRole('link', { name: '계정 관리' })).toBeInTheDocument();
    expect(within(nav).getByRole('link', { name: '내 프로필' })).toBeInTheDocument();
    expect(within(nav).getByRole('link', { name: '화면 설정' })).toBeInTheDocument();
    expect(within(nav).queryByRole('link', { name: '내 MCP 키' })).not.toBeInTheDocument();
    expect(within(nav).queryByRole('link', { name: 'MCP 키' })).not.toBeInTheDocument();
    expect(workspaceApi.detail).not.toHaveBeenCalled();
  });

  it('계정 메뉴의 화면 설정은 개인 설정의 화면 설정 화면으로 간다', async () => {
    session.me = me(['system-admin']);
    renderAt('/admin/accounts');
    fireEvent.click(screen.getByRole('button', { name: '내 계정 메뉴' }));
    fireEvent.click(await screen.findByRole('menuitem', { name: '화면 설정' }));
    expect(await screen.findByText('화면 설정 화면')).toBeInTheDocument();
  });
});
