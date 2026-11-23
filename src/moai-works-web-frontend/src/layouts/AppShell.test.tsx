import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';

import '@/shared/i18n/i18n';
import { mcpKeyApi, invitationApi, notificationApi, projectApi, workspaceApi } from '@/shared/api/endpoints';
import type * as Endpoints from '@/shared/api/endpoints';
import type { Me, PermissionCode, WorkspaceDetail } from '@/shared/api/types';
import { SettingsRedirect } from '@/pages/StatusPages';
import { ConfirmProvider } from '@/shared/ui/ConfirmDialog';
import { AppearanceProvider } from '@/theme/AppearanceProvider';

import { AppShell } from './AppShell';

/**
 * 앱 틀 — 사이드바 메뉴는 서버가 준 내 권한(myPermissions) · 플랫폼 역할로만 거른다.
 * 개인 설정은 사이드바가 아니라 계정 메뉴가 여는 대화상자다(주소 ?settings=…). 알림 종 모양은 계정 영역 위에.
 * 시스템 관리자 계정에는 알림 · 알림 설정 · 내 MCP 키가 없다.
 */

vi.mock('@/shared/api/endpoints', async (importOriginal) => {
  const actual = await importOriginal<typeof Endpoints>();
  return {
    ...actual,
    workspaceApi: { ...actual.workspaceApi, detail: vi.fn() },
    projectApi: { ...actual.projectApi, list: vi.fn() },
    invitationApi: { ...actual.invitationApi, mine: vi.fn() },
    notificationApi: { ...actual.notificationApi, unreadCount: vi.fn(), preferences: vi.fn() },
    mcpKeyApi: { ...actual.mcpKeyApi, mine: vi.fn() },
  };
});

const session = vi.hoisted(() => ({ me: null as unknown }));
vi.mock('@/features/auth/session', () => ({
  useMe: () => session.me,
  useSession: () => ({ signOut: vi.fn(), refresh: vi.fn() }),
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

/** 지금 주소 — 개인 설정 대화상자가 주소(?settings=…)로 열리고 닫히는지 본다 */
function LocationProbe() {
  const location = useLocation();
  return <output aria-label="주소">{location.pathname + location.search}</output>;
}

function renderAt(path: string) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <AppearanceProvider>
      <QueryClientProvider client={client}>
        <ConfirmProvider>
          <MemoryRouter initialEntries={[path]}>
            <Routes>
              <Route path="/me/mcp-keys" element={<SettingsRedirect section="mcp-keys" />} />
              <Route element={<AppShell />}>
                <Route path="*" element={<LocationProbe />} />
              </Route>
            </Routes>
          </MemoryRouter>
        </ConfirmProvider>
      </QueryClientProvider>
    </AppearanceProvider>,
  );
}

/** 대화상자가 열리면 뒤 화면은 화면 읽기에서 숨는다(aria-hidden) — 그래도 주소는 본다 */
const address = () => screen.getByRole('status', { name: '주소', hidden: true });

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
  vi.mocked(notificationApi.unreadCount).mockResolvedValue(0);
  vi.mocked(notificationApi.preferences).mockResolvedValue([]);
  vi.mocked(mcpKeyApi.mine).mockResolvedValue([]);
});

afterEach(() => {
  vi.clearAllMocks();
});

describe('사이드바 메뉴', () => {
  it('mcp.key.manage 가 있으면 워크스페이스 MCP 키 — 개인 설정은 사이드바에 없다', async () => {
    session.me = me([]);
    vi.mocked(workspaceApi.detail).mockResolvedValue(workspace(['workspace.read', 'mcp.key.issue.personal', 'mcp.key.manage', 'audit.read']));
    renderAt('/w/team-a');
    const nav = screen.getByRole('navigation', { name: '주 메뉴' });
    expect(await within(nav).findByRole('link', { name: 'MCP 키' })).toHaveAttribute('href', '/w/team-a/mcp-keys');
    expect(within(nav).getByRole('link', { name: '감사 기록' })).toHaveAttribute('href', '/w/team-a/audit');
    expect(within(nav).queryByText('개인 설정')).not.toBeInTheDocument();
    for (const name of ['내 프로필', '화면 설정', '알림 설정', '내 MCP 키']) {
      expect(within(nav).queryByRole('link', { name })).not.toBeInTheDocument();
    }
  });

  it('mcp.key.manage 가 없으면 워크스페이스 MCP 키 메뉴가 없다', async () => {
    session.me = me([]);
    vi.mocked(workspaceApi.detail).mockResolvedValue(workspace(['workspace.read', 'member.read', 'mcp.key.issue.personal']));
    renderAt('/w/team-a');
    const nav = screen.getByRole('navigation', { name: '주 메뉴' });
    expect(await within(nav).findByRole('link', { name: '멤버' })).toBeInTheDocument();
    expect(within(nav).queryByRole('link', { name: 'MCP 키' })).not.toBeInTheDocument();
  });

  it('시스템 관리자 계정은 시스템 관리 메뉴만', () => {
    session.me = me(['system-admin']);
    renderAt('/admin/accounts');
    const nav = screen.getByRole('navigation', { name: '주 메뉴' });
    expect(within(nav).getByRole('link', { name: '계정 관리' })).toBeInTheDocument();
    expect(within(nav).queryByRole('link', { name: 'MCP 키' })).not.toBeInTheDocument();
    expect(workspaceApi.detail).not.toHaveBeenCalled();
  });
});

describe('알림 종 모양', () => {
  it('계정 영역 위에 읽지 않은 수와 함께 — 누르면 알림함', async () => {
    session.me = me([]);
    vi.mocked(workspaceApi.detail).mockResolvedValue(workspace(['workspace.read']));
    vi.mocked(notificationApi.unreadCount).mockResolvedValue(3);
    renderAt('/w/team-a');
    const bell = await screen.findByRole('link', { name: '알림 — 읽지 않은 것 3개' });
    expect(bell).toHaveAttribute('href', '/notifications');
    expect(bell).toHaveTextContent('3');
    // 주 메뉴가 아니라 계정 영역에 있다
    const nav = screen.getByRole('navigation', { name: '주 메뉴' });
    expect(within(nav).queryByRole('link', { name: /읽지 않은 것/ })).not.toBeInTheDocument();
    expect(notificationApi.unreadCount).toHaveBeenCalled();
  });

  it('시스템 관리자 계정에는 종 모양이 없고 읽지 않은 수를 묻지도 않는다', () => {
    session.me = me(['system-admin']);
    renderAt('/admin/accounts');
    expect(screen.queryByRole('link', { name: /^알림/ })).not.toBeInTheDocument();
    expect(notificationApi.unreadCount).not.toHaveBeenCalled();
  });

  it('좁은 화면에서는 위 막대에 종 모양이 있다 (시스템 관리자 계정에는 없다)', async () => {
    const wide = window.matchMedia;
    window.matchMedia = (query: string) => ({ ...wide(query), matches: false });
    try {
      session.me = me([]);
      vi.mocked(workspaceApi.detail).mockResolvedValue(workspace(['workspace.read']));
      vi.mocked(notificationApi.unreadCount).mockResolvedValue(5);
      const { unmount } = renderAt('/w/team-a');
      const header = screen.getByRole('banner');
      expect(await within(header).findByRole('link', { name: '알림 — 읽지 않은 것 5개' })).toHaveAttribute('href', '/notifications');
      unmount();

      session.me = me(['system-admin']);
      renderAt('/admin/accounts');
      expect(within(screen.getByRole('banner')).queryByRole('link')).not.toBeInTheDocument();
    } finally {
      window.matchMedia = wide;
    }
  });
});

describe('개인 설정 대화상자', () => {
  it('계정 메뉴의 개인 설정이 지금 화면 위에 연다 — 칸을 바꾸면 주소가 바뀌고, 닫으면 연 화면으로 돌아온다', async () => {
    session.me = me([]);
    vi.mocked(workspaceApi.detail).mockResolvedValue(workspace(['workspace.read']));
    renderAt('/w/team-a/members?tab=invitations');
    fireEvent.click(screen.getByRole('button', { name: '내 계정 메뉴' }));
    // 화면 설정은 따로 있지 않고 대화상자의 한 칸이다
    expect(screen.queryByRole('menuitem', { name: '화면 설정' })).not.toBeInTheDocument();
    fireEvent.click(await screen.findByRole('menuitem', { name: '개인 설정' }));

    const dialog = await screen.findByRole('dialog', { name: '개인 설정' });
    expect(address()).toHaveTextContent('/w/team-a/members?tab=invitations&settings=profile');
    const menu = within(dialog).getByRole('navigation', { name: '개인 설정 메뉴' });
    expect(within(menu).getAllByRole('button').map((b) => b.textContent)).toEqual(['내 프로필', '화면 설정', '알림 설정', '내 MCP 키']);
    expect(await within(dialog).findByRole('heading', { name: '내 프로필', level: 3 })).toBeInTheDocument();

    fireEvent.click(within(menu).getByRole('button', { name: '화면 설정' }));
    await waitFor(() => expect(address()).toHaveTextContent('/w/team-a/members?tab=invitations&settings=preferences'));
    expect(await within(dialog).findByRole('heading', { name: '화면 설정', level: 3 })).toBeInTheDocument();
    expect(within(menu).getByRole('button', { name: '화면 설정' })).toHaveAttribute('aria-current', 'page');

    fireEvent.click(within(dialog).getByRole('button', { name: '개인 설정 닫기' }));
    await waitFor(() => expect(address()).toHaveTextContent(/^\/w\/team-a\/members\?tab=invitations$/));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  });

  it('주소로 바로 열면 그 칸이 열리고, 닫으면 settings 만 뺀다', async () => {
    session.me = me([]);
    vi.mocked(workspaceApi.detail).mockResolvedValue(workspace(['workspace.read']));
    vi.mocked(notificationApi.preferences).mockResolvedValue([{ kind: 'issue.assigned', group: 'ISSUE', enabled: true, mandatory: false, email: false }]);
    renderAt('/w/team-a?settings=notifications');
    const dialog = await screen.findByRole('dialog', { name: '개인 설정' });
    expect(await within(dialog).findByRole('switch', { name: '나를 담당자로 정함' })).toBeChecked();
    fireEvent.click(within(dialog).getByRole('button', { name: '개인 설정 닫기' }));
    await waitFor(() => expect(address()).toHaveTextContent(/^\/w\/team-a$/));
  });

  it('시스템 관리자 계정에는 내 프로필 · 화면 설정만 — 없는 칸을 주소로 열면 첫 칸', async () => {
    session.me = me(['system-admin']);
    renderAt('/admin/accounts?settings=mcp-keys');
    const dialog = await screen.findByRole('dialog', { name: '개인 설정' });
    const menu = within(dialog).getByRole('navigation', { name: '개인 설정 메뉴' });
    expect(within(menu).getAllByRole('button').map((b) => b.textContent)).toEqual(['내 프로필', '화면 설정']);
    expect(await within(dialog).findByRole('heading', { name: '내 프로필', level: 3 })).toBeInTheDocument();
    expect(mcpKeyApi.mine).not.toHaveBeenCalled();
  });

  it('휴대폰에서는 화면을 꽉 채우고 칸을 위의 탭으로 고른다', async () => {
    const wide = window.matchMedia;
    // 좁은 화면 — max-width 쿼리만 맞는다
    window.matchMedia = (query: string) => ({ ...wide(query), matches: query.includes('max-width') });
    try {
      session.me = me([]);
      vi.mocked(workspaceApi.detail).mockResolvedValue(workspace(['workspace.read']));
      renderAt('/w/team-a?settings=preferences');
      const dialog = await screen.findByRole('dialog', { name: '개인 설정' });
      const tabs = within(dialog).getByRole('tablist', { name: '개인 설정 메뉴' });
      expect(within(tabs).getAllByRole('tab').map((tab) => tab.textContent)).toEqual(['내 프로필', '화면 설정', '알림 설정', '내 MCP 키']);
      expect(within(tabs).getByRole('tab', { name: '화면 설정' })).toHaveAttribute('aria-selected', 'true');
      expect(within(dialog).getByRole('button', { name: '개인 설정 닫기' })).toBeInTheDocument();
      fireEvent.click(within(tabs).getByRole('tab', { name: '내 MCP 키' }));
      await waitFor(() => expect(address()).toHaveTextContent('/w/team-a?settings=mcp-keys'));
    } finally {
      window.matchMedia = wide;
    }
  });

  it('예전 주소 /me/mcp-keys 는 들어갈 곳 위에 내 MCP 키 칸을 연다', async () => {
    session.me = me([]);
    vi.mocked(workspaceApi.detail).mockResolvedValue(workspace(['workspace.read']));
    renderAt('/me/mcp-keys');
    await waitFor(() => expect(address()).toHaveTextContent('/w/team-a?settings=mcp-keys'));
    const dialog = await screen.findByRole('dialog', { name: '개인 설정' });
    expect(await within(dialog).findByRole('heading', { name: '내 MCP 키', level: 3 })).toBeInTheDocument();
    await waitFor(() => expect(mcpKeyApi.mine).toHaveBeenCalledWith('ACTIVE'));
  });
});
