import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import { afterEach, describe, expect, it, vi } from 'vitest';

import '@/shared/i18n/i18n';
import { WorkspaceLayout } from '@/features/workspace/WorkspaceLayout';
import { adminApi, workspaceApi } from '@/shared/api/endpoints';
import type * as Endpoints from '@/shared/api/endpoints';
import { ApiError } from '@/shared/api/http';
import type { AdminWorkspace } from '@/shared/api/types';
import { ConfirmProvider } from '@/shared/ui/ConfirmDialog';
import { AppearanceProvider } from '@/theme/AppearanceProvider';

import { WorkspacesPage } from './WorkspacesPage';

/**
 * 워크스페이스 관리(시스템 관리자) · 사용 중지 안내(멤버) 화면.
 * 목록은 이름 · 주소 · 소유자 · 멤버 수 · 상태까지, 사용 중지는 이유를 받고, 지우기는 영향을 보인 뒤 주소를 그대로 입력해야 한다.
 */

vi.mock('@/shared/api/endpoints', async (importOriginal) => {
  const actual = await importOriginal<typeof Endpoints>();
  return {
    ...actual,
    adminApi: {
      ...actual.adminApi,
      workspaces: vi.fn(),
      suspendWorkspace: vi.fn(),
      resumeWorkspace: vi.fn(),
      workspaceDeletionPreview: vi.fn(),
      deleteWorkspace: vi.fn(),
    },
    workspaceApi: { ...actual.workspaceApi, detail: vi.fn() },
  };
});

function ws(extra: Partial<AdminWorkspace> = {}): AdminWorkspace {
  return {
    id: 'w1',
    slug: 'workspace-abcdefghjkmn',
    name: '디자인 팀',
    kind: 'TEAM',
    visibility: 'PUBLIC',
    status: 'ACTIVE',
    owners: [
      { id: 'u1', displayName: '김철수', email: 'kim@example.com' },
      { id: 'u2', displayName: '이영희', email: 'lee@example.com' },
    ],
    memberCount: 7,
    createdAt: '2026-11-01T00:00:00Z',
    suspendedAt: null,
    suspendedReason: null,
    ...extra,
  };
}

function renderAt(path: string) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <AppearanceProvider>
      <QueryClientProvider client={client}>
        <ConfirmProvider>
          <MemoryRouter initialEntries={[path]}>
            <Routes>
              <Route path="/admin/workspaces" element={<WorkspacesPage />} />
              <Route path="/w/:slug" element={<WorkspaceLayout />}>
                <Route index element={<p>워크스페이스 홈</p>} />
              </Route>
            </Routes>
          </MemoryRouter>
        </ConfirmProvider>
      </QueryClientProvider>
    </AppearanceProvider>,
  );
}

afterEach(() => {
  vi.clearAllMocks();
});

describe('워크스페이스 관리 화면', () => {
  it('사용 중인 팀 워크스페이스를 이름 · 주소 · 소유자 · 멤버 수 · 상태로 보이고, 사용 중지는 이유를 받는다', async () => {
    vi.mocked(adminApi.workspaces).mockResolvedValue([ws()]);
    vi.mocked(adminApi.suspendWorkspace).mockResolvedValue(ws({ status: 'SUSPENDED' }));
    renderAt('/admin/workspaces');

    const table = await screen.findByRole('table');
    expect(adminApi.workspaces).toHaveBeenCalledWith({ kind: 'TEAM', status: 'ACTIVE', query: undefined });
    const row = within(table).getAllByRole('row')[1] as HTMLElement;
    expect(row).toHaveTextContent('디자인 팀');
    expect(row).toHaveTextContent('workspace-abcdefghjkmn');
    expect(row).toHaveTextContent('김철수');
    expect(row).toHaveTextContent('외 1명');
    expect(row).toHaveTextContent('7명');
    expect(row).toHaveTextContent('사용 중');
    // 사용 중인 것은 지울 수 없다 — 사용 중지만
    expect(within(row).queryByRole('button', { name: /지우기/ })).toBeNull();

    fireEvent.click(within(row).getByRole('button', { name: '사용 중지 — 디자인 팀' }));
    const dialog = await screen.findByRole('dialog');
    const confirm = within(dialog).getByRole('button', { name: '사용 중지' });
    expect(confirm).toBeDisabled();
    fireEvent.change(within(dialog).getByLabelText('이유'), { target: { value: '  계약 종료  ' } });
    expect(confirm).toBeEnabled();
    fireEvent.click(confirm);
    await waitFor(() => expect(adminApi.suspendWorkspace).toHaveBeenCalledWith('workspace-abcdefghjkmn', '계약 종료'));
  });

  it('사용 중지한 것은 다시 사용 · 지우기가 있고, 지우기는 영향을 보인 뒤 주소를 그대로 입력해야 한다', async () => {
    vi.mocked(adminApi.workspaces).mockResolvedValue([
      ws({ status: 'SUSPENDED', suspendedAt: '2026-11-20T00:00:00Z', suspendedReason: '계약 종료' }),
    ]);
    vi.mocked(adminApi.workspaceDeletionPreview).mockResolvedValue({
      slug: 'workspace-abcdefghjkmn',
      name: '디자인 팀',
      status: 'SUSPENDED',
      members: 7,
      projects: 3,
      issues: 120,
      attachments: 4,
      attachmentBytes: 2048,
    });
    vi.mocked(adminApi.deleteWorkspace).mockResolvedValue(undefined);
    renderAt('/admin/workspaces?tab=SUSPENDED');

    const table = await screen.findByRole('table');
    expect(adminApi.workspaces).toHaveBeenCalledWith({ kind: 'TEAM', status: 'SUSPENDED', query: undefined });
    const row = within(table).getAllByRole('row')[1] as HTMLElement;
    expect(row).toHaveTextContent('사용 중지');
    expect(row).toHaveTextContent('이유: 계약 종료');
    expect(within(row).getByRole('button', { name: '다시 사용 — 디자인 팀' })).toBeInTheDocument();

    fireEvent.click(within(row).getByRole('button', { name: '지우기 — 디자인 팀' }));
    const dialog = await screen.findByRole('dialog');
    await within(dialog).findByText('120');
    expect(dialog).toHaveTextContent('멤버');
    expect(dialog).toHaveTextContent('프로젝트');
    expect(dialog).toHaveTextContent('4개 · 2.0 KB');
    const confirm = within(dialog).getByRole('button', { name: '영구히 지우기' });
    const input = within(dialog).getByLabelText(/주소 workspace-abcdefghjkmn/);
    fireEvent.change(input, { target: { value: 'workspace-abcdefghjkm' } });
    expect(confirm).toBeDisabled();
    fireEvent.change(input, { target: { value: 'workspace-abcdefghjkmn' } });
    expect(confirm).toBeEnabled();
    fireEvent.click(confirm);
    await waitFor(() => expect(adminApi.deleteWorkspace).toHaveBeenCalledWith('workspace-abcdefghjkmn', 'workspace-abcdefghjkmn'));
  });

  it('개인 워크스페이스는 조치 없이 이유만 보인다', async () => {
    vi.mocked(adminApi.workspaces).mockResolvedValue([ws({ kind: 'PERSONAL', name: '김철수의 워크스페이스', owners: [] })]);
    renderAt('/admin/workspaces?tab=PERSONAL');

    const table = await screen.findByRole('table');
    expect(adminApi.workspaces).toHaveBeenCalledWith({ kind: 'PERSONAL', query: undefined });
    const row = within(table).getAllByRole('row')[1] as HTMLElement;
    expect(within(row).queryAllByRole('button')).toHaveLength(0);
    expect(row).toHaveTextContent('계정 정지로 막습니다');
    expect(row).toHaveTextContent('소유자 없음');
  });
});

describe('사용 중지 안내 (멤버)', () => {
  it('서버가 WORKSPACE_SUSPENDED 를 주면 찾을 수 없음 대신 사용 중지 안내를 보인다', async () => {
    vi.mocked(workspaceApi.detail).mockRejectedValue(new ApiError(403, 'WORKSPACE_SUSPENDED', '사용 중지'));
    renderAt('/w/workspace-abcdefghjkmn');

    expect(await screen.findByText('사용 중지된 워크스페이스입니다')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '다른 워크스페이스로' })).toHaveAttribute('href', '/');
    expect(screen.queryByText('워크스페이스 홈')).toBeNull();
  });

  it('멤버가 아니면(404) 그대로 찾을 수 없음이다', async () => {
    vi.mocked(workspaceApi.detail).mockRejectedValue(new ApiError(404, 'WORKSPACE_NOT_FOUND', '없음'));
    renderAt('/w/workspace-abcdefghjkmn');

    expect(await screen.findByText('워크스페이스를 찾을 수 없습니다')).toBeInTheDocument();
    expect(screen.queryByText('사용 중지된 워크스페이스입니다')).toBeNull();
  });
});
