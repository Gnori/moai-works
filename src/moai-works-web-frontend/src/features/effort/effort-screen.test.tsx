import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import '@/shared/i18n/i18n';
import { ProjectProvider } from '@/features/project/project-context';
import { WorkspaceProvider } from '@/features/workspace/workspace-context';
import { effortApi } from '@/shared/api/endpoints';
import type * as Endpoints from '@/shared/api/endpoints';
import type { EffortReport, EffortRow, PermissionCode, ProjectDetail, WorkspaceDetail } from '@/shared/api/types';
import { AppearanceProvider } from '@/theme/AppearanceProvider';

import { EffortPage } from './EffortPage';
import { ProjectEffortPage } from './ProjectEffortPage';

/** 공수 집계 화면 — 주소의 조건이 그대로 서버 요청이 되고, 받은 줄 · 합계 · 막대가 보이며, CSV 를 같은 조건으로 받는다 */

vi.mock('@/shared/api/endpoints', async (importOriginal) => {
  const actual = await importOriginal<typeof Endpoints>();
  return {
    ...actual,
    effortApi: { report: vi.fn(), csv: vi.fn() },
    workspaceApi: { ...actual.workspaceApi, members: vi.fn().mockResolvedValue([]) },
  };
});

const ALICE = 'aaaaaaaa-0000-0000-0000-000000000001';
const workspace = { slug: 'team-a', name: '팀 A', myPermissions: ['workspace.read', 'member.read'] as PermissionCode[] } as unknown as WorkspaceDetail;
const project = { key: 'ea', name: '에이', status: 'ACTIVE', myPermissions: ['issue.read'], myActions: [] } as unknown as ProjectDetail;

function row(extra: Partial<EffortRow>): EffortRow {
  return {
    key: 'x', label: 'x', project: null, issue: null, person: null, type: null, period: null, periodEnd: null,
    estimatedHours: null, actualHours: 0, diffHours: null, ratio: null, issueCount: 1, logCount: 1, ...extra,
  };
}

function report(extra: Partial<EffortReport> = {}): EffortReport {
  return {
    from: '2026-11-01',
    to: '2026-11-26',
    groupBy: 'project',
    rows: [
      row({ key: 'ea', label: '에이', project: { key: 'ea', name: '에이' }, estimatedHours: 10, actualHours: 12.5, diffHours: 2.5, ratio: 125, issueCount: 2, logCount: 4 }),
      row({ key: 'eb', label: '비', project: { key: 'eb', name: '비' }, estimatedHours: 8, actualHours: 5, diffHours: -3, ratio: 63, logCount: 2 }),
    ],
    total: { estimatedHours: 18, actualHours: 17.5, diffHours: -0.5, ratio: 97, issueCount: 3, logCount: 6, personCount: 2 },
    removedLogs: 1,
    projects: [{ key: 'ea', name: '에이' }, { key: 'eb', name: '비' }],
    types: [{ key: 'feature', name: '새기능' }, { key: 'bug', name: '버그' }],
    people: [{ id: ALICE, username: 'alice', displayName: '앨리스' }],
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
              <Route path="/w/:slug/effort" element={<EffortPage />} />
              <Route
                path="/w/:slug/projects/:projectKey/effort"
                element={
                  <ProjectProvider project={project}>
                    <ProjectEffortPage />
                  </ProjectProvider>
                }
              />
            </Routes>
          </MemoryRouter>
        </WorkspaceProvider>
      </QueryClientProvider>
    </AppearanceProvider>,
  );
}

beforeEach(() => {
  // 오늘 = 한국 시간 2026-11-26 (UTC 로는 전날 밤)
  vi.useFakeTimers({ toFake: ['Date'] });
  vi.setSystemTime(new Date('2026-11-25T16:00:00Z'));
});

afterEach(() => {
  vi.useRealTimers();
  vi.clearAllMocks();
});

describe('공수 집계 화면', () => {
  it('기본은 이번 달(한국 시간) · 프로젝트별 — 줄마다 실제 · 예상 · 차이 · 비율, 맨 끝은 합계', async () => {
    vi.mocked(effortApi.report).mockResolvedValue(report());
    renderAt('/w/team-a/effort');

    const table = await screen.findByRole('table', { name: '공수 집계 표' });
    expect(effortApi.report).toHaveBeenCalledWith('team-a', { groupBy: 'project', from: '2026-11-01', to: '2026-11-26' });
    const rows = within(table).getAllByRole('row');
    expect(rows).toHaveLength(4);
    const first = rows[1] as HTMLElement;
    expect(within(first).getByRole('link', { name: '에이' })).toHaveAttribute('href', '/w/team-a/projects/ea/effort');
    expect(first).toHaveTextContent('12.5');
    expect(first).toHaveTextContent('+2.5');
    expect(first).toHaveTextContent('125%');
    // 예상을 넘긴 것은 색만이 아니라 말로도
    expect(within(first).getByText(/예상을 넘김/)).toBeInTheDocument();
    // 막대 — 가장 큰 줄 100%, 다음 줄은 그 비율
    expect(within(first).getByRole('img', { name: '가장 많은 줄 대비 100%' })).toBeInTheDocument();
    expect(within(rows[2] as HTMLElement).getByRole('img', { name: '가장 많은 줄 대비 40%' })).toBeInTheDocument();
    expect(rows[2]).toHaveTextContent('−3');
    expect(rows[3]).toHaveTextContent('합계');
    expect(rows[3]).toHaveTextContent('17.5');
    expect(rows[3]).toHaveTextContent('97%');
    // 합계 칸 · 지운 기록 표시 · 기간
    expect(screen.getByText(/지운 작업 시간 기록 1건은 빼고 셌습니다/)).toBeInTheDocument();
    expect(screen.getByText(/2026-11-01 ~ 2026-11-26 · 일감 3개 · 기록 6건 · 사람 2명/)).toBeInTheDocument();
    expect(screen.getByLabelText('시작 날짜')).toHaveValue('2026-11-01');
    expect(screen.getByLabelText('끝 날짜')).toHaveValue('2026-11-26');
    expect(screen.queryByRole('button', { name: '조건 지우기' })).not.toBeInTheDocument();
  });

  it('주소의 조건(지난 달 · 사람별 · 사람 · 유형)으로 묻고, 사람 · 기간으로 모으면 줄에 예상 열이 없다', async () => {
    vi.mocked(effortApi.report).mockResolvedValue(
      report({
        groupBy: 'person',
        rows: [row({ key: ALICE, label: '앨리스', person: { id: ALICE, username: 'alice', displayName: '앨리스' }, actualHours: 7, issueCount: 2, logCount: 3 })],
      }),
    );
    renderAt(`/w/team-a/effort?range=lastMonth&groupBy=person&person=${ALICE}&type=bug`);

    const table = await screen.findByRole('table', { name: '공수 집계 표' });
    expect(effortApi.report).toHaveBeenCalledWith('team-a', { groupBy: 'person', from: '2026-10-01', to: '2026-10-31', person: [ALICE], type: ['bug'] });
    expect(within(table).queryByRole('columnheader', { name: '예상(시간)' })).not.toBeInTheDocument();
    expect(within(table).getByRole('columnheader', { name: '사람별' })).toBeInTheDocument();
    expect(within(table).getAllByRole('row')[1]).toHaveTextContent('앨리스');
    expect(screen.getByRole('button', { name: '조건 지우기' })).toBeInTheDocument();
  });

  it('날짜를 고치면 직접 고름이 되고 그 기간으로 다시 묻는다', async () => {
    vi.mocked(effortApi.report).mockResolvedValue(report());
    renderAt('/w/team-a/effort');
    await screen.findByRole('table', { name: '공수 집계 표' });
    fireEvent.change(screen.getByLabelText('시작 날짜'), { target: { value: '2026-10-15' } });
    await waitFor(() =>
      expect(effortApi.report).toHaveBeenLastCalledWith('team-a', { groupBy: 'project', from: '2026-10-15', to: '2026-11-26' }),
    );
  });

  it('기록이 없으면 빈 화면 안내, CSV 단추는 꺼진다', async () => {
    vi.mocked(effortApi.report).mockResolvedValue(
      report({ rows: [], removedLogs: 0, total: { estimatedHours: null, actualHours: 0, diffHours: null, ratio: null, issueCount: 0, logCount: 0, personCount: 0 } }),
    );
    renderAt('/w/team-a/effort');
    expect(await screen.findByText('이 조건에 맞는 작업 시간 기록이 없습니다.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'CSV 내려받기' })).toBeDisabled();
  });

  it('CSV 는 화면과 같은 조건 · 화면 언어로 받아 저장한다', async () => {
    vi.mocked(effortApi.report).mockResolvedValue(report());
    vi.mocked(effortApi.csv).mockResolvedValue(new Blob(['a,b'], { type: 'text/csv' }));
    const createObjectURL = vi.fn(() => 'blob:effort');
    const revokeObjectURL = vi.fn();
    Object.assign(URL, { createObjectURL, revokeObjectURL });
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
    renderAt('/w/team-a/effort?groupBy=issue');

    fireEvent.click(await screen.findByRole('button', { name: 'CSV 내려받기' }));
    await waitFor(() => expect(click).toHaveBeenCalled());
    expect(effortApi.csv).toHaveBeenCalledWith('team-a', { groupBy: 'issue', from: '2026-11-01', to: '2026-11-26' }, 'ko');
    expect(createObjectURL).toHaveBeenCalled();
    click.mockRestore();
  });

  it('프로젝트 화면 — 그 프로젝트로 정해 일감별로 묻고, 프로젝트 고르기가 없다', async () => {
    vi.mocked(effortApi.report).mockResolvedValue(
      report({
        groupBy: 'issue',
        rows: [
          row({
            key: 'ea#7', label: '공수 집계', project: { key: 'ea', name: '에이' }, issue: { project: 'ea', number: 7, title: '공수 집계', type: 'feature' },
            type: 'feature', estimatedHours: 4, actualHours: 3, diffHours: -1, ratio: 75,
          }),
        ],
      }),
    );
    renderAt('/w/team-a/projects/ea/effort');

    const table = await screen.findByRole('table', { name: '공수 집계 표' });
    expect(effortApi.report).toHaveBeenCalledWith('team-a', { groupBy: 'issue', from: '2026-11-01', to: '2026-11-26', project: ['ea'] });
    expect(within(table).getByRole('link', { name: '#7 공수 집계' })).toHaveAttribute('href', '/w/team-a/projects/ea/issues/7');
    expect(screen.queryByLabelText('프로젝트')).not.toBeInTheDocument();
    expect(screen.getByRole('heading', { name: '공수 집계' })).toBeInTheDocument();
  });
});
