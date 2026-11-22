import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import { afterEach, describe, expect, it, vi } from 'vitest';

import '@/shared/i18n/i18n';
import { workspaceApi } from '@/shared/api/endpoints';
import type * as Endpoints from '@/shared/api/endpoints';
import type { AuditEntry, PermissionCode, WorkspaceDetail } from '@/shared/api/types';
import { AppearanceProvider } from '@/theme/AppearanceProvider';

import { AuditPage } from './AuditPage';
import { WorkspaceProvider } from './workspace-context';

/** 감사 기록 화면 — 주소의 조건이 그대로 서버 요청이 되고, IP · 프로그램 · 내용이 글자로 보이는지 */

vi.mock('@/shared/api/endpoints', async (importOriginal) => {
  const actual = await importOriginal<typeof Endpoints>();
  return { ...actual, workspaceApi: { ...actual.workspaceApi, audit: vi.fn(), members: vi.fn() } };
});

const LEE = 'aaaaaaaa-0000-0000-0000-000000000002';
const workspace = { slug: 'team-a', name: '팀 A', myPermissions: ['audit.read'] as PermissionCode[] } as unknown as WorkspaceDetail;

function entry(i: number, extra: Partial<AuditEntry> = {}): AuditEntry {
  return {
    id: `a${i}`,
    occurredAt: `2026-11-22T09:00:${String(59 - (i % 60)).padStart(2, '0')}.00${i % 10}`,
    actorType: 'USER',
    actorId: LEE,
    actorName: '이영희',
    ip: '10.0.0.9',
    action: 'mcp.key.issue',
    targetType: 'mcp_key',
    targetId: `k${i}`,
    details: JSON.stringify({ keyPrefix: 'mai-wks-Ab12', name: '<b>서버</b>', userAgent: 'curl/8.4.0' }),
    ...extra,
  };
}

function renderAt(path: string) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <AppearanceProvider>
      <QueryClientProvider client={client}>
        <WorkspaceProvider workspace={workspace}>
          <MemoryRouter initialEntries={[path]}>
            <Routes>
              <Route path="/w/:slug/audit" element={<AuditPage />} />
            </Routes>
          </MemoryRouter>
        </WorkspaceProvider>
      </QueryClientProvider>
    </AppearanceProvider>,
  );
}

afterEach(() => {
  vi.clearAllMocks();
});

describe('감사 기록 화면', () => {
  it('주소의 사람 · 동작 묶음 · 날짜로 묻고, IP · 프로그램 · 내용(글자 그대로)을 보인다', async () => {
    vi.mocked(workspaceApi.audit).mockResolvedValue([entry(1)]);
    renderAt(`/w/team-a/audit?person=${LEE}&action=mcp.key&from=2026-11-01&to=2026-11-22`);

    const table = await screen.findByRole('table');
    expect(workspaceApi.audit).toHaveBeenCalledWith('team-a', {
      limit: 50,
      person: LEE,
      action: 'mcp.key',
      from: new Date(2026, 10, 1).toISOString(),
      to: new Date(2026, 10, 23).toISOString(),
      before: undefined,
    });
    const row = within(table).getAllByRole('row')[1] as HTMLElement;
    expect(row).toHaveTextContent('2026-11-22 09:00:58.001');
    expect(row).toHaveTextContent('이영희');
    expect(row).toHaveTextContent('MCP 키 발급');
    expect(row).toHaveTextContent('10.0.0.9');
    expect(within(row).getAllByText('프로그램 curl 8.4.0').length).toBeGreaterThan(0);
    // HTML 은 그리지 않고 글자 그대로
    expect(within(row).getAllByText('keyPrefix: mai-wks-Ab12 · name: <b>서버</b>').length).toBeGreaterThan(0);
    expect(row.querySelector('b')).toBeNull();
    // 고른 조건이 칸에 보인다
    expect(screen.getByLabelText('시작 날')).toHaveValue('2026-11-01');
    expect(screen.getByLabelText('끝 날')).toHaveValue('2026-11-22');
    expect(screen.getByRole('button', { name: '조건 지우기' })).toBeInTheDocument();
  });

  it('한 쪽(50건)을 다 채우면 더 보기 — 마지막 줄의 시각보다 앞의 기록을 묻는다', async () => {
    const first = Array.from({ length: 50 }, (_, i) => entry(i));
    vi.mocked(workspaceApi.audit).mockResolvedValueOnce(first).mockResolvedValueOnce([entry(60, { action: 'member.add' })]);
    renderAt('/w/team-a/audit');

    fireEvent.click(await screen.findByRole('button', { name: '더 보기' }));
    await waitFor(() =>
      expect(workspaceApi.audit).toHaveBeenLastCalledWith('team-a', expect.objectContaining({ limit: 50, before: first[49]?.occurredAt })),
    );
    expect(await screen.findByText('멤버 추가')).toBeInTheDocument();
    expect(screen.getByText('51건 표시')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '더 보기' })).not.toBeInTheDocument();
  });

  it('사람 이름을 누르면 그 사람의 기록만 묻는다', async () => {
    vi.mocked(workspaceApi.audit).mockResolvedValue([entry(1)]);
    renderAt('/w/team-a/audit');
    fireEvent.click(await screen.findByRole('button', { name: '이영희님의 기록만 보기' }));
    await waitFor(() => expect(workspaceApi.audit).toHaveBeenLastCalledWith('team-a', expect.objectContaining({ person: LEE })));
  });

  it('누가 — 넓은 화면은 한 줄로 끊지 않는 열, 좁은 화면은 무엇을 칸 안의 한 줄(둘 다 눌러서 거른다)', async () => {
    // jsdom 은 넓은 화면의 미디어 쿼리를 고른다 — 좁은 화면의 줄은 숨은 채로 DOM 에 있다(hidden: true 로 찾는다)
    vi.mocked(workspaceApi.audit).mockResolvedValue([entry(1, { actorName: 'moaiworks02' })]);
    renderAt('/w/team-a/audit');
    const table = await screen.findByRole('table');
    const cells = within(within(table).getAllByRole('row', { hidden: true })[1] as HTMLElement).getAllByRole('cell', { hidden: true });
    const [, actorCell, actionCell] = cells as [HTMLElement, HTMLElement, HTMLElement];
    const name = { name: 'moaiworks02님의 기록만 보기', hidden: true };
    // 넓은 화면의 누가 열 — 이름이 한 글자씩 꺾이지 않게 줄을 바꾸지 않는다
    expect(within(actorCell).getByRole('button', name)).toHaveTextContent('moaiworks02');
    expect(getComputedStyle(actorCell).whiteSpace).toBe('nowrap');
    // 좁은 화면 — 무엇을 칸 안에 동작 · 한 사람 · IP 가 한 줄씩
    expect(actionCell).toHaveTextContent('MCP 키 발급');
    fireEvent.click(within(actionCell).getByRole('button', name));
    await waitFor(() => expect(workspaceApi.audit).toHaveBeenLastCalledWith('team-a', expect.objectContaining({ person: LEE })));
  });
});
