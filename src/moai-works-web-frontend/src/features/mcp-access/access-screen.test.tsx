import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import type { ReactNode } from 'react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router';
import { afterEach, describe, expect, it, vi } from 'vitest';

import '@/shared/i18n/i18n';
import { mcpAccessApi, workspaceApi } from '@/shared/api/endpoints';
import type * as Endpoints from '@/shared/api/endpoints';
import type { McpAccessEntry, McpKey, PermissionCode, WorkspaceDetail } from '@/shared/api/types';
import { AppearanceProvider } from '@/theme/AppearanceProvider';
import { WorkspaceProvider } from '@/features/workspace/workspace-context';

import { KeyAccessLogDialog } from './KeyAccessLogDialog';
import { McpAccessLogPage } from './McpAccessLogPage';

/**
 * MCP 접근 기록 화면을 실제로 그려 본다 — 서버 호출만 가짜로 두고 화면 · 주소 · 말은 그대로.
 * 주소의 조건이 그대로 서버 요청 · CSV 주소가 되고, 줄을 열면 클라이언트 정보 · 인자 요약이 글자 그대로 보이는지 본다.
 */

vi.mock('@/shared/api/endpoints', async (importOriginal) => {
  const actual = await importOriginal<typeof Endpoints>();
  return {
    ...actual,
    mcpAccessApi: { ...actual.mcpAccessApi, workspace: vi.fn(), key: vi.fn() },
    workspaceApi: { ...actual.workspaceApi, members: vi.fn() },
  };
});

const LEE = 'aaaaaaaa-0000-0000-0000-000000000002';
const KEY = 'bbbbbbbb-0000-0000-0000-000000000001';
const SESSION = 'c0ffee00-1111-2222-3333-444444444444';

function entry(i: number, extra: Partial<McpAccessEntry> = {}): McpAccessEntry {
  return {
    id: `e${i}`,
    occurredAt: `2026-11-22T09:00:${String(59 - (i % 60)).padStart(2, '0')}.00${i % 10}`,
    durationMs: 12,
    keyId: KEY,
    keyPrefix: 'mai-wks-Ab12',
    keyName: '노트북 Claude',
    ownerId: LEE,
    ownerName: '이영희',
    entry: 'ISSUE',
    requestPath: '/mcp/issue',
    workspaceSlug: 'team-a',
    workspaceName: '팀 A',
    mcpMethod: 'tools/call',
    tool: 'create_issue',
    operation: 'CREATE',
    projectKey: 'web',
    issueNumber: 7,
    targetRef: null,
    arguments: JSON.stringify({ project: 'web', title: '<b>제목</b>', description: { length: 120, sha256: 'ab12' } }),
    outcome: 'DENIED',
    httpStatus: 403,
    errorCode: 'PERMISSION_REQUIRED',
    clientName: 'claude-code',
    clientVersion: '2.0.14',
    clientTitle: null,
    protocolVersion: '2025-06-18',
    clientCapabilities: 'roots',
    sessionId: SESSION,
    userAgent: 'claude-cli/2.0.14 (external, cli)',
    ip: '203.0.113.7',
    traceId: 'trace-1',
    ...extra,
  };
}

const workspace = { slug: 'team-a', name: '팀 A', myPermissions: ['audit.read'] as PermissionCode[] } as unknown as WorkspaceDetail;

/** 지금 주소를 글자로 보인다 — 조건이 주소에 남는지 본다 */
function Where() {
  const location = useLocation();
  return <div data-testid="where">{`${location.pathname}${location.search}`}</div>;
}

function Providers({ children, path }: { children: ReactNode; path: string }) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return (
    <AppearanceProvider>
      <QueryClientProvider client={client}>
        <WorkspaceProvider workspace={workspace}>
          <MemoryRouter initialEntries={[path]}>
            <Routes>
              <Route
                path="/w/:slug/mcp-access"
                element={
                  <>
                    {children}
                    <Where />
                  </>
                }
              />
              <Route path="/me" element={children} />
            </Routes>
          </MemoryRouter>
        </WorkspaceProvider>
      </QueryClientProvider>
    </AppearanceProvider>
  );
}

afterEach(() => {
  vi.clearAllMocks();
});

describe('워크스페이스 › MCP 접근 기록', () => {
  it('주소의 조건으로 묻고, 줄마다 누가 · 무엇을 · 대상 · 결과 · 클라이언트 · IP 를 보이며 CSV 주소에도 같은 조건이 실린다', async () => {
    vi.mocked(workspaceApi.members).mockResolvedValue([]);
    vi.mocked(mcpAccessApi.workspace).mockResolvedValue([entry(1)]);
    render(
      <Providers path={`/w/team-a/mcp-access?person=${LEE}&operation=CREATE&outcome=DENIED&tool=create_issue&from=2026-11-01&to=2026-11-22`}>
        <McpAccessLogPage />
      </Providers>,
    );

    const table = await screen.findByRole('table');
    expect(mcpAccessApi.workspace).toHaveBeenCalledWith('team-a', {
      limit: 50,
      person: LEE,
      tool: 'create_issue',
      operation: 'CREATE',
      outcome: 'DENIED',
      from: new Date(2026, 10, 1).toISOString(),
      to: new Date(2026, 10, 23).toISOString(),
      before: undefined,
      beforeId: undefined,
    });
    const row = within(table).getAllByRole('row')[1] as HTMLElement;
    expect(row).toHaveTextContent('2026-11-22 09:00:58.001');
    expect(row).toHaveTextContent('이영희');
    expect(row).toHaveTextContent('노트북 Claude (mai-wks-Ab12…)');
    expect(row).toHaveTextContent('만들기');
    expect(row).toHaveTextContent('create_issue');
    expect(row).toHaveTextContent('web #7');
    expect(row).toHaveTextContent('거부');
    expect(row).toHaveTextContent('PERMISSION_REQUIRED');
    expect(row).toHaveTextContent('claude-code 2.0.14');
    expect(row).toHaveTextContent('203.0.113.7');

    const csv = screen.getByRole('link', { name: 'CSV 내려받기' });
    const href = csv.getAttribute('href') ?? '';
    expect(href.startsWith('/api/v1/workspaces/team-a/mcp-access-log.csv?')).toBe(true);
    const params = new URLSearchParams(href.split('?')[1]);
    expect(params.get('person')).toBe(LEE);
    expect(params.get('operation')).toBe('CREATE');
    expect(params.get('outcome')).toBe('DENIED');
    expect(params.get('tool')).toBe('create_issue');
    expect(params.get('limit')).toBeNull();
    expect(params.get('before')).toBeNull();
    // 도구 조건이 걸려 있으니 더 보기가 펼쳐져 있다
    expect(screen.getByRole('button', { name: '조건 접기' })).toHaveAttribute('aria-expanded', 'true');
  });

  it('조건을 바꾸면 주소에 남고 그 조건으로 다시 묻는다 — 지우면 모두', async () => {
    vi.mocked(workspaceApi.members).mockResolvedValue([]);
    vi.mocked(mcpAccessApi.workspace).mockResolvedValue([entry(1)]);
    render(
      <Providers path="/w/team-a/mcp-access">
        <McpAccessLogPage />
      </Providers>,
    );
    await screen.findByRole('table');
    expect(screen.getByRole('button', { name: '조건 더 보기' })).toHaveAttribute('aria-expanded', 'false');

    fireEvent.mouseDown(screen.getByRole('combobox', { name: '결과' }));
    fireEvent.click(await screen.findByRole('option', { name: '오류' }));
    await waitFor(() => expect(screen.getByTestId('where')).toHaveTextContent('/w/team-a/mcp-access?outcome=ERROR'));
    await waitFor(() => expect(mcpAccessApi.workspace).toHaveBeenLastCalledWith('team-a', expect.objectContaining({ outcome: 'ERROR' })));

    fireEvent.click(screen.getByRole('button', { name: '조건 지우기' }));
    await waitFor(() => expect(screen.getByTestId('where')).toHaveTextContent(/^\/w\/team-a\/mcp-access$/));
  });

  it('줄을 열면 모든 칸이 글자 그대로 보이고, 같은 세션 · 이 키의 기록으로 건다', async () => {
    vi.mocked(workspaceApi.members).mockResolvedValue([]);
    vi.mocked(mcpAccessApi.workspace).mockResolvedValue([entry(1)]);
    render(
      <Providers path="/w/team-a/mcp-access">
        <McpAccessLogPage />
      </Providers>,
    );
    fireEvent.click(await screen.findByRole('button', { name: '2026-11-22 09:00:58.001 기록 자세히 보기' }));
    const dialog = await screen.findByRole('dialog', { name: /접근 기록 한 줄/ });
    expect(dialog).toHaveTextContent('claude-cli/2.0.14 (external, cli)');
    expect(dialog).toHaveTextContent('2025-06-18');
    expect(dialog).toHaveTextContent(SESSION);
    expect(dialog).toHaveTextContent('trace-1');
    expect(dialog).toHaveTextContent('거부 · HTTP 403');
    expect(dialog).toHaveTextContent('팀 A (team-a)');
    // 인자 요약은 글자로만 — HTML 로 해석하지 않는다
    expect(dialog).toHaveTextContent('"title": "<b>제목</b>"');
    expect(dialog.querySelector('b')).toBeNull();
    expect(within(dialog).getByRole('link', { name: '일감 열기' })).toHaveAttribute('href', '/w/team-a/projects/web/issues/7');

    fireEvent.click(within(dialog).getByRole('button', { name: '같은 세션의 기록' }));
    await waitFor(() => expect(screen.getByTestId('where')).toHaveTextContent(`session=${SESSION}`));
    await waitFor(() => expect(mcpAccessApi.workspace).toHaveBeenLastCalledWith('team-a', expect.objectContaining({ session: SESSION })));
  });

  it('기록이 없으면 무엇이 남는지 알린다', async () => {
    vi.mocked(workspaceApi.members).mockResolvedValue([]);
    vi.mocked(mcpAccessApi.workspace).mockResolvedValue([]);
    render(
      <Providers path="/w/team-a/mcp-access">
        <McpAccessLogPage />
      </Providers>,
    );
    expect(await screen.findByText('기록이 없습니다')).toBeInTheDocument();
  });
});

describe('내 MCP 키 › 접근 기록', () => {
  const target = { id: KEY, name: '노트북 Claude' } as McpKey;

  it('그 키의 줄을 모두 — 워크스페이스가 없는 줄(연결)까지, 키 주인 조건 없이 워크스페이스 이름을 함께 보인다', async () => {
    vi.mocked(mcpAccessApi.key).mockResolvedValue([
      entry(1),
      entry(2, { mcpMethod: 'initialize', tool: null, operation: 'CONNECT', workspaceSlug: null, workspaceName: null, projectKey: null, issueNumber: null, outcome: 'OK', httpStatus: 200, errorCode: null }),
    ]);
    render(
      <Providers path="/me">
        <KeyAccessLogDialog target={target} onClose={() => undefined} />
      </Providers>,
    );
    const dialog = await screen.findByRole('dialog', { name: '노트북 Claude — 접근 기록' });
    const table = await within(dialog).findByRole('table');
    expect(mcpAccessApi.key).toHaveBeenCalledWith(KEY, { limit: 50, before: undefined, beforeId: undefined });
    const rows = within(table).getAllByRole('row');
    expect(rows).toHaveLength(3);
    expect(rows[1]).toHaveTextContent('팀 A · web #7');
    expect(rows[2]).toHaveTextContent('initialize');
    expect(rows[2]).toHaveTextContent('접속');
    expect(within(dialog).queryByRole('combobox', { name: '키 주인' })).not.toBeInTheDocument();
    expect(within(dialog).getByRole('link', { name: 'CSV 내려받기' })).toHaveAttribute('href', `/api/v1/me/mcp-keys/${KEY}/access-log.csv`);
  });
});
