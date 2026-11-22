import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import type { ReactNode } from 'react';
import { MemoryRouter, Route, Routes } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import '@/shared/i18n/i18n';
import { WorkspaceProvider } from '@/features/workspace/workspace-context';
import { mcpKeyApi, workspaceApi } from '@/shared/api/endpoints';
import type * as Endpoints from '@/shared/api/endpoints';
import type { McpKey, PermissionCode, WorkspaceDetail } from '@/shared/api/types';
import { ConfirmProvider } from '@/shared/ui/ConfirmDialog';
import { AppearanceProvider } from '@/theme/AppearanceProvider';

import { MyMcpKeysPage } from './MyMcpKeysPage';
import { WorkspaceMcpKeysPage } from './WorkspaceMcpKeysPage';

/**
 * MCP 키 화면을 실제로 그려 본다 — 서버 호출만 가짜로 두고 화면 · 주소 · 말은 그대로.
 * 발급한 키의 원문이 한 번만 보이고, 창을 닫으면 사라지며, 쿼리 캐시에 남지 않는지 본다.
 */

vi.mock('@/shared/api/endpoints', async (importOriginal) => {
  const actual = await importOriginal<typeof Endpoints>();
  return {
    ...actual,
    mcpKeyApi: { list: vi.fn(), issue: vi.fn(), expire: vi.fn(), mine: vi.fn() },
    workspaceApi: { ...actual.workspaceApi, detail: vi.fn(), members: vi.fn(), audit: vi.fn() },
  };
});

const people = vi.hoisted(() => ({
  ME: 'aaaaaaaa-0000-0000-0000-000000000001',
  LEE: 'aaaaaaaa-0000-0000-0000-000000000002',
}));
const { ME, LEE } = people;

vi.mock('@/features/auth/session', () => ({
  useMe: () => ({
    id: people.ME,
    username: 'kim',
    displayName: '김철수',
    email: 'kim@example.com',
    platformRoles: [],
    landingWorkspace: 'team-a',
    personalWorkspace: 'mine',
    workspaces: [
      { slug: 'mine', name: '내 워크스페이스', kind: 'PERSONAL', status: 'ACTIVE', myRoles: ['owner'], memberCount: 1 },
      { slug: 'team-a', name: '팀 A', kind: 'TEAM', status: 'ACTIVE', myRoles: ['admin'], memberCount: 3 },
    ],
  }),
  useSession: () => ({ refresh: vi.fn() }),
}));

const RAW_KEY = 'mai-wks-Ab12SECRETvalue0123456789';
const CHROME = 'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36';

function key(extra: Partial<McpKey> = {}): McpKey {
  return {
    id: 'k1',
    name: '노트북',
    keyPrefix: 'mai-wks-Ab12',
    status: 'ACTIVE',
    workspace: 'team-a',
    workspaceName: '팀 A',
    owner: { id: ME, username: 'kim', displayName: '김철수' },
    createdAt: '2026-11-20T09:00:00.001',
    expiresAt: '2027-02-18T09:00:00.001',
    lastUsedAt: null,
    issuedIp: '10.0.0.7',
    issuedUserAgent: CHROME,
    expiredBy: null,
    expireReason: null,
    expireNote: null,
    mine: true,
    ...extra,
  };
}

const expired = key({
  id: 'k0',
  name: '옛 키',
  keyPrefix: 'mai-wks-Zz99',
  status: 'EXPIRED',
  expiresAt: '2026-11-21T10:00:00.120',
  lastUsedAt: '2026-11-21T09:59:00.005',
  expiredBy: { id: LEE, username: 'lee', displayName: '이영희' },
  expireReason: 'MANUAL',
  expireNote: '노트북 분실',
});

function workspace(permissions: PermissionCode[]): WorkspaceDetail {
  return { slug: 'team-a', name: '팀 A', myPermissions: permissions } as unknown as WorkspaceDetail;
}

function Providers({ children, path, route, client, permissions }: {
  children: ReactNode;
  path: string;
  route: string;
  client: QueryClient;
  permissions?: PermissionCode[];
}) {
  const routed = (
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path={route} element={children} />
        <Route path="/w/:slug/audit" element={<div>감사 기록 화면</div>} />
      </Routes>
    </MemoryRouter>
  );
  return (
    <AppearanceProvider>
      <QueryClientProvider client={client}>
        <ConfirmProvider>{permissions ? <WorkspaceProvider workspace={workspace(permissions)}>{routed}</WorkspaceProvider> : routed}</ConfirmProvider>
      </QueryClientProvider>
    </AppearanceProvider>
  );
}

function newClient() {
  return new QueryClient({ defaultOptions: { queries: { retry: false } } });
}

beforeEach(() => {
  vi.mocked(mcpKeyApi.mine).mockImplementation(async (status) => (status === 'EXPIRED' ? [expired] : status === 'ALL' ? [key(), expired] : [key()]));
  vi.mocked(workspaceApi.detail).mockResolvedValue(workspace(['workspace.read', 'mcp.key.issue.personal']));
  vi.mocked(workspaceApi.members).mockResolvedValue([]);
});

afterEach(() => {
  vi.clearAllMocks();
});

describe('내 MCP 키', () => {
  it('사용 중인 키부터 — 이름 · 앞부분 · 워크스페이스 · 밀리초 시각 · 발급한 곳, 만료는 누가 · 왜', async () => {
    render(
      <Providers path="/me/mcp-keys" route="/me/mcp-keys" client={newClient()}>
        <MyMcpKeysPage />
      </Providers>,
    );
    const list = await screen.findByRole('list', { name: 'MCP 키 목록' });
    expect(mcpKeyApi.mine).toHaveBeenCalledWith('ACTIVE');
    const item = within(list).getByRole('listitem', { name: '노트북' });
    expect(item).toHaveTextContent('mai-wks-Ab12…');
    expect(item).toHaveTextContent('사용 중');
    expect(item).toHaveTextContent('팀 A');
    expect(item).toHaveTextContent('2026-11-20 09:00:00.001');
    expect(item).toHaveTextContent('아직 쓰지 않음');
    expect(item).toHaveTextContent('10.0.0.7 · Chrome 131 · macOS');

    fireEvent.click(screen.getByRole('button', { name: '만료' }));
    await waitFor(() => expect(mcpKeyApi.mine).toHaveBeenCalledWith('EXPIRED'));
    const old = await screen.findByRole('listitem', { name: '옛 키' });
    expect(old).toHaveTextContent('직접 만료시킴 · 이영희');
    expect(old).toHaveTextContent('이유: 노트북 분실');
    expect(old).toHaveTextContent('2026-11-21 09:59:00.005');
    // 만료된 키에는 만료시키기 단추가 없다
    expect(within(old).queryByRole('button', { name: /만료시키기/ })).not.toBeInTheDocument();
  });

  it('발급 — 원문은 한 번만 보이고 연결 안내가 함께 나온다. 캐시에 남지 않고, 닫으면 사라진다', async () => {
    const client = newClient();
    vi.mocked(mcpKeyApi.issue).mockResolvedValue({ view: key({ id: 'k2', name: '서버' }), key: RAW_KEY });
    render(
      <Providers path="/me/mcp-keys" route="/me/mcp-keys" client={client}>
        <MyMcpKeysPage />
      </Providers>,
    );
    await screen.findByRole('list', { name: 'MCP 키 목록' });
    fireEvent.click(screen.getByRole('button', { name: '키 발급' }));
    const dialog = await screen.findByRole('dialog');
    // 처음 워크스페이스는 마지막에 쓴 곳 — 그 워크스페이스의 내 권한으로 단추를 켠다
    await waitFor(() => expect(workspaceApi.detail).toHaveBeenCalledWith('team-a'));
    const submit = within(dialog).getByRole('button', { name: '발급' });
    await waitFor(() => expect(submit).toBeEnabled());

    fireEvent.click(submit);
    expect(await within(dialog).findByText('키 이름을 적으십시오.')).toBeInTheDocument();
    expect(mcpKeyApi.issue).not.toHaveBeenCalled();

    fireEvent.change(within(dialog).getByLabelText(/^키 이름/), { target: { value: ' 서버 ' } });
    expect(within(dialog).getByRole('button', { name: '90일' })).toHaveAttribute('aria-pressed', 'true');
    fireEvent.click(within(dialog).getByRole('button', { name: '180일' }));
    fireEvent.click(submit);
    await waitFor(() => expect(mcpKeyApi.issue).toHaveBeenCalledWith('team-a', { name: '서버', days: 180 }));

    expect(await within(dialog).findByDisplayValue(RAW_KEY)).toBeInTheDocument();
    expect(dialog).toHaveTextContent('키 원문은 지금 한 번만 보입니다.');
    const url = `${window.location.origin}/mcp/project`;
    expect(within(dialog).getByDisplayValue(url)).toBeInTheDocument();
    expect(dialog).toHaveTextContent(`claude mcp add --transport http moai-works ${url} --header "Authorization: Bearer ${RAW_KEY}"`);

    // 원문은 쿼리 · 뮤테이션 캐시 어디에도 없다
    const cached = JSON.stringify([
      client.getQueryCache().getAll().map((q) => q.state.data),
      client.getMutationCache().getAll().map((m) => m.state.data),
    ]);
    expect(cached).not.toContain(RAW_KEY);

    fireEvent.click(within(dialog).getByRole('button', { name: '다 했습니다' }));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(screen.queryByDisplayValue(RAW_KEY)).not.toBeInTheDocument();
    expect(document.body.textContent).not.toContain(RAW_KEY);
  });

  it('고른 워크스페이스에서 키를 받을 권한이 없으면 알리고 단추를 끈다', async () => {
    vi.mocked(workspaceApi.detail).mockResolvedValue(workspace(['workspace.read']));
    render(
      <Providers path="/me/mcp-keys" route="/me/mcp-keys" client={newClient()}>
        <MyMcpKeysPage />
      </Providers>,
    );
    await screen.findByRole('list', { name: 'MCP 키 목록' });
    fireEvent.click(screen.getByRole('button', { name: '키 발급' }));
    const dialog = await screen.findByRole('dialog');
    expect(await within(dialog).findByText(/MCP 키를 받을 권한이 내 역할에 없습니다/)).toBeInTheDocument();
    expect(within(dialog).getByRole('button', { name: '발급' })).toBeDisabled();
  });

  it('만료시키기 — 묻고, 적은 이유와 함께 보낸다', async () => {
    vi.mocked(mcpKeyApi.expire).mockResolvedValue({ ...key(), status: 'EXPIRED', expireReason: 'MANUAL' });
    render(
      <Providers path="/me/mcp-keys" route="/me/mcp-keys" client={newClient()}>
        <MyMcpKeysPage />
      </Providers>,
    );
    fireEvent.click(await screen.findByRole('button', { name: '노트북 키 만료시키기' }));
    const dialog = await screen.findByRole('dialog');
    expect(dialog).toHaveTextContent('노트북 키를 만료시킬까요?');
    expect(mcpKeyApi.expire).not.toHaveBeenCalled();
    fireEvent.change(within(dialog).getByLabelText(/^이유/), { target: { value: ' 노트북 분실 ' } });
    fireEvent.click(within(dialog).getByRole('button', { name: '만료시키기' }));
    await waitFor(() => expect(mcpKeyApi.expire).toHaveBeenCalledWith('team-a', 'k1', '노트북 분실'));
  });
});

describe('워크스페이스 MCP 키 (관리)', () => {
  const others = key({ id: 'k3', name: '이영희 키', mine: false, owner: { id: LEE, username: 'lee', displayName: '이영희' } });

  it('주소의 조건(주인)으로 묻고, 주인 · 만료시키기 · 그 사람의 MCP 키 감사 기록 링크를 보인다', async () => {
    vi.mocked(mcpKeyApi.list).mockResolvedValue([others]);
    render(
      <Providers
        path={`/w/team-a/mcp-keys?owner=${LEE}`}
        route="/w/:slug/mcp-keys"
        client={newClient()}
        permissions={['workspace.read', 'member.read', 'mcp.key.manage', 'audit.read']}
      >
        <WorkspaceMcpKeysPage />
      </Providers>,
    );
    const item = await screen.findByRole('listitem', { name: '이영희 키' });
    expect(mcpKeyApi.list).toHaveBeenCalledWith('team-a', { status: 'ALL', owner: LEE });
    expect(item).toHaveTextContent('주인이영희');
    expect(within(item).getByRole('button', { name: '이영희 키 키 만료시키기' })).toBeInTheDocument();
    expect(within(item).getByRole('link', { name: '이영희님의 MCP 키 감사 기록 보기' })).toHaveAttribute(
      'href',
      `/w/team-a/audit?person=${LEE}&action=mcp.key`,
    );
    expect(screen.getByRole('link', { name: 'MCP 키 감사 기록' })).toHaveAttribute('href', '/w/team-a/audit?action=mcp.key');

    fireEvent.click(screen.getByRole('button', { name: '사용 중' }));
    await waitFor(() => expect(mcpKeyApi.list).toHaveBeenCalledWith('team-a', { status: 'ACTIVE', owner: LEE }));
  });

  it('감사 기록 권한이 없으면 감사 기록 링크를 그리지 않는다', async () => {
    vi.mocked(mcpKeyApi.list).mockResolvedValue([others]);
    render(
      <Providers path="/w/team-a/mcp-keys" route="/w/:slug/mcp-keys" client={newClient()} permissions={['workspace.read', 'mcp.key.manage']}>
        <WorkspaceMcpKeysPage />
      </Providers>,
    );
    await screen.findByRole('listitem', { name: '이영희 키' });
    expect(screen.queryByRole('link', { name: /감사 기록/ })).not.toBeInTheDocument();
    expect(mcpKeyApi.list).toHaveBeenCalledWith('team-a', { status: 'ALL', owner: undefined });
  });
});
