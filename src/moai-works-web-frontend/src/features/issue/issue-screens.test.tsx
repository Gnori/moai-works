import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import type { ReactNode } from 'react';
import { MemoryRouter, Route, Routes } from 'react-router';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';

import '@/shared/i18n/i18n';
import { ProjectProvider } from '@/features/project/project-context';
import { WorkspaceProvider } from '@/features/workspace/workspace-context';
import { attachmentApi, issueApi } from '@/shared/api/endpoints';
import type * as Endpoints from '@/shared/api/endpoints';
import type { IssueDetail, IssueRevisionDiff, IssueRevisionItem, PermissionCode, ProjectDetail, WorkspaceDetail } from '@/shared/api/types';
import { ConfirmProvider } from '@/shared/ui/ConfirmDialog';
import { AppearanceProvider } from '@/theme/AppearanceProvider';

import { IssueHistoryPage } from './IssueHistoryPage';
import { IssueViewPage } from './IssueViewPage';
import { IssueWorkLogs } from './IssueWorkLogs';

/**
 * 리비전 화면 · 작업 시간 칸을 실제로 그려 본다 — 서버 호출(issueApi)만 가짜로 두고 화면 · 주소 · 말은 그대로.
 * 시각은 시간대 표시 없이 줘서(이 컴퓨터의 시간대로 읽힘) 밀리초까지 보이는지 그대로 견준다.
 */

vi.mock('@/shared/api/endpoints', async (importOriginal) => {
  const actual = await importOriginal<typeof Endpoints>();
  return {
    ...actual,
    issueApi: {
      ...actual.issueApi,
      detail: vi.fn(),
      revisions: vi.fn(),
      revision: vi.fn(),
      diff: vi.fn(),
      logWork: vi.fn(),
      removeWork: vi.fn(),
    },
    attachmentApi: {
      ...actual.attachmentApi,
      list: vi.fn(),
    },
  };
});

const ME = 'aaaaaaaa-0000-0000-0000-000000000001';
const LEE = 'aaaaaaaa-0000-0000-0000-000000000002';

vi.mock('@/features/auth/session', () => ({
  useMe: () => ({ id: 'aaaaaaaa-0000-0000-0000-000000000001', username: 'kim', displayName: '김철수' }),
}));

const workspace = { slug: 'a', name: 'A', myPermissions: [] as PermissionCode[] } as unknown as WorkspaceDetail;

function project(permissions: PermissionCode[]): ProjectDetail {
  return {
    id: 'p1',
    key: 'web',
    name: '웹',
    description: null,
    visibility: 'WORKSPACE',
    status: 'ACTIVE',
    updatedAt: '2026-11-22T09:00:00',
    createdAt: '2026-11-01T09:00:00',
    archivedAt: null,
    issueCount: 1,
    myRoles: [],
    myPermissions: permissions,
    myActions: [],
  };
}

const issue: IssueDetail = {
  number: 7,
  type: 'bug',
  title: '로그인 실패',
  description: '하나\n둘 고침',
  status: 'IN_PROGRESS',
  manager: { id: ME, username: 'kim', displayName: '김철수' },
  assignee: null,
  parent: null,
  dueDate: null,
  startDate: null,
  plannedEndDate: null,
  completedOn: null,
  closedOn: null,
  createdBy: { id: ME, username: 'kim', displayName: '김철수' },
  createdAt: '2026-11-20T09:00:00.001',
  updatedAt: '2026-11-22T09:03:04.007',
  rowVersion: 3,
  overdue: false,
  revisionNo: 3,
  estimatedHours: 10,
  spentHours: 3.5,
  children: [],
  links: [],
  journal: [],
  workLogs: [
    {
      id: 'w-mine',
      user: { id: ME, username: 'kim', displayName: '김철수' },
      workDate: '2026-11-22',
      hours: 2,
      note: '설계',
      via: 'WEB',
      createdAt: '2026-11-22T10:00:00.005',
    },
    {
      id: 'w-lee',
      user: { id: LEE, username: 'lee', displayName: '이영희' },
      workDate: '2026-11-21',
      hours: 1.5,
      note: null,
      via: 'MCP',
      createdAt: '2026-11-21T18:30:00.120',
    },
  ],
};

function item(no: number, extra: Partial<IssueRevisionItem> = {}): IssueRevisionItem {
  return {
    no,
    actorType: 'USER',
    actorId: ME,
    actorName: '김철수',
    via: 'WEB',
    createdAt: `2026-11-2${no}T09:03:04.00${no}`,
    changedFields: [],
    current: no === 3,
    ...extra,
  };
}

const items = [item(3, { changedFields: ['status', 'estimatedHours'], via: 'MCP' }), item(2, { changedFields: ['description'], actorName: '이영희' }), item(1)];

function diff(to: number, from: number | null): IssueRevisionDiff {
  return {
    from,
    to,
    fromInfo: from === null ? null : item(from),
    toInfo: item(to),
    fields: [
      { field: 'status', before: 'NEW', after: 'IN_PROGRESS' },
      { field: 'estimatedHours', before: null, after: '10' },
    ],
    descriptionLines: [
      { op: 'SAME', text: '하나', oldLine: 1, newLine: 1 },
      { op: 'DEL', text: '둘', oldLine: 2, newLine: null },
      { op: 'ADD', text: '둘 고침', oldLine: null, newLine: 2 },
    ],
    added: 1,
    removed: 1,
    coarse: false,
  };
}

function Providers({ children, permissions, path }: { children: ReactNode; permissions: PermissionCode[]; path: string }) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return (
    <AppearanceProvider>
      <QueryClientProvider client={client}>
        <ConfirmProvider>
          <WorkspaceProvider workspace={workspace}>
            <ProjectProvider project={project(permissions)}>
              <MemoryRouter initialEntries={[path]}>
                <Routes>
                  <Route path="/w/:slug/projects/:projectKey/issues/:number/*" element={children} />
                </Routes>
              </MemoryRouter>
            </ProjectProvider>
          </WorkspaceProvider>
        </ConfirmProvider>
      </QueryClientProvider>
    </AppearanceProvider>
  );
}

beforeAll(() => {
  // jsdom 에는 없다 — 좁은 화면에서 고른 판의 차이로 내려 줄 때 부른다
  Element.prototype.scrollIntoView = vi.fn();
});

beforeEach(() => {
  vi.mocked(issueApi.detail).mockResolvedValue(issue);
  vi.mocked(issueApi.revisions).mockResolvedValue({ items, next: null });
  vi.mocked(issueApi.diff).mockImplementation(async (_s, _k, _n, no, from) => diff(no, from ?? (no > 1 ? no - 1 : null)));
  vi.mocked(attachmentApi.list).mockResolvedValue([]);
});

afterEach(() => {
  vi.clearAllMocks();
});

describe('리비전 · 차이 화면', () => {
  it('타임라인(판 · 누가 · 밀리초 시각 · MCP · 바꾼 필드 · 지금 판)과 주소의 두 판 차이를 그린다', async () => {
    render(
      <Providers permissions={['issue.read']} path="/w/a/projects/web/issues/7/history?rev=3&from=1">
        <IssueHistoryPage />
      </Providers>,
    );

    const timeline = await screen.findByRole('list', { name: '리비전 타임라인' });
    const entries = within(timeline).getAllByRole('listitem');
    expect(entries).toHaveLength(3);
    expect(within(entries[0] as HTMLElement).getByText('판 3')).toBeInTheDocument();
    expect(within(entries[0] as HTMLElement).getByText('지금 판')).toBeInTheDocument();
    expect(within(entries[0] as HTMLElement).getByText('MCP')).toBeInTheDocument();
    expect(within(entries[0] as HTMLElement).getByText('예상 공수')).toBeInTheDocument();
    expect(within(entries[0] as HTMLElement).getByText(/2026-11-23 09:03:04\.003/)).toBeInTheDocument();
    expect(within(entries[2] as HTMLElement).getByText('만듦')).toBeInTheDocument();

    // 주소의 ?rev=3&from=1 그대로 묻는다
    await waitFor(() => expect(issueApi.diff).toHaveBeenCalledWith('a', 'web', 7, 3, 1));
    expect(await screen.findByText('판 1 → 판 3 · 바뀐 필드 2 · 설명 넣은 줄 1 · 지운 줄 1')).toBeInTheDocument();

    // 필드: 상태는 번역해서 − 전 · + 후, 예상 공수는 "10시간"
    expect(screen.getByText('등록')).toBeInTheDocument();
    expect(screen.getByText('진행')).toBeInTheDocument();
    expect(screen.getByText('10시간')).toBeInTheDocument();

    // 설명: git 처럼 줄 번호 · − · + 와 함께
    const table = screen.getByRole('table', { name: '두 판의 설명 차이' });
    expect(within(table).getByLabelText('지운 줄')).toHaveTextContent('−');
    expect(within(table).getByLabelText('넣은 줄')).toHaveTextContent('+');
    expect(within(table).getByText('둘 고침')).toBeInTheDocument();
  });

  it('판을 고르면 바로 앞 판과 견주고, 이 판 전체를 볼 수 있다', async () => {
    vi.mocked(issueApi.revision).mockResolvedValue({
      ...item(2, { actorName: '이영희' }),
      type: 'bug',
      title: '로그인 실패 (옛 제목)',
      description: '하나\n둘',
      status: 'NEW',
      manager: { id: ME, username: 'kim', displayName: '김철수' },
      assignee: null,
      parent: null,
      dueDate: '2026-12-01',
      startDate: null,
      plannedEndDate: null,
      completedOn: null,
      closedOn: null,
      estimatedHours: null,
    });
    render(
      <Providers permissions={['issue.read']} path="/w/a/projects/web/issues/7/history">
        <IssueHistoryPage />
      </Providers>,
    );

    await waitFor(() => expect(issueApi.diff).toHaveBeenCalledWith('a', 'web', 7, 3, undefined));
    const timeline = await screen.findByRole('list', { name: '리비전 타임라인' });
    fireEvent.click(within(timeline).getByText('판 2'));
    await waitFor(() => expect(issueApi.diff).toHaveBeenCalledWith('a', 'web', 7, 2, undefined));

    fireEvent.click(screen.getByRole('tab', { name: '이 판 전체 보기' }));
    expect(await screen.findByText('로그인 실패 (옛 제목)')).toBeInTheDocument();
    expect(issueApi.revision).toHaveBeenCalledWith('a', 'web', 7, 2);
    // 날짜만 있는 값은 날짜만
    expect(screen.getByText('2026-12-01')).toBeInTheDocument();
  });
});

describe('작업 시간 칸', () => {
  it('기록(날 · 누가 · 시간 · 메모 · MCP · 적은 때 밀리초)과 합계를 보이고, 지우기는 내 기록에만', async () => {
    render(
      <Providers permissions={['issue.read', 'issue.edit']} path="/w/a/projects/web/issues/7">
        <IssueWorkLogs slug="a" projectKey="web" issue={issue} canLog canManage={false} />
      </Providers>,
    );
    expect(screen.getByText('합계 3.5시간 · 예상 10시간')).toBeInTheDocument();
    const rows = within(screen.getByRole('list', { name: '작업 시간 기록' })).getAllByRole('listitem');
    expect(rows[0]).toHaveTextContent('김철수 (나)');
    expect(rows[0]).toHaveTextContent('2026-11-22 10:00:00.005 적음');
    expect(rows[1]).toHaveTextContent('이영희');
    expect(within(rows[1] as HTMLElement).getByText('MCP')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /김철수 · 2시간 기록 지우기/ })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /이영희/ })).not.toBeInTheDocument();
  });

  it('project.manage 가 있으면 남의 기록도 지울 수 있고, 지우기 전에 묻는다', async () => {
    vi.mocked(issueApi.removeWork).mockResolvedValue({ ...issue, workLogs: [issue.workLogs[0] as IssueDetail['workLogs'][number]], spentHours: 2 });
    render(
      <Providers permissions={['issue.read', 'issue.edit', 'project.manage']} path="/w/a/projects/web/issues/7">
        <IssueWorkLogs slug="a" projectKey="web" issue={issue} canLog canManage />
      </Providers>,
    );
    fireEvent.click(screen.getByRole('button', { name: /이영희 · 1\.5시간 기록 지우기/ }));
    const dialog = await screen.findByRole('dialog');
    expect(dialog).toHaveTextContent('이영희 · 2026-11-21 · 1.5시간 기록을 지웁니다.');
    expect(issueApi.removeWork).not.toHaveBeenCalled();
    fireEvent.click(within(dialog).getByRole('button', { name: '지우기' }));
    await waitFor(() => expect(issueApi.removeWork).toHaveBeenCalledWith('a', 'web', 7, 'w-lee'));
  });

  it('적기 — 맞지 않는 시간은 칸에 알리고, 맞으면 보낸다', async () => {
    vi.mocked(issueApi.logWork).mockResolvedValue(issue);
    render(
      <Providers permissions={['issue.read', 'issue.edit']} path="/w/a/projects/web/issues/7">
        <IssueWorkLogs slug="a" projectKey="web" issue={issue} canLog canManage={false} />
      </Providers>,
    );
    // 적기 양식은 접혀 있다 — 단추를 눌러 펼친다
    expect(screen.queryByLabelText(/^시간/)).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '작업 시간 적기' }));
    const hours = screen.getByLabelText(/^시간/);
    fireEvent.change(hours, { target: { value: '25' } });
    fireEvent.click(screen.getByRole('button', { name: '적기' }));
    expect(await screen.findByText('0 보다 크고 24 이하인 시간을 적으십시오. 예: 1.5')).toBeInTheDocument();
    expect(issueApi.logWork).not.toHaveBeenCalled();

    fireEvent.change(hours, { target: { value: '1.25' } });
    fireEvent.change(screen.getByLabelText('한 일 (선택)'), { target: { value: ' 검토 ' } });
    fireEvent.click(screen.getByRole('button', { name: '적기' }));
    await waitFor(() =>
      expect(issueApi.logWork).toHaveBeenCalledWith('a', 'web', 7, expect.objectContaining({ hours: 1.25, note: '검토' })),
    );
  });

  it('고칠 수 없으면 적기 양식도 지우기 단추도 없다', () => {
    render(
      <Providers permissions={['issue.read']} path="/w/a/projects/web/issues/7">
        <IssueWorkLogs slug="a" projectKey="web" issue={issue} canLog={false} canManage={false} />
      </Providers>,
    );
    expect(screen.queryByRole('button', { name: '적기' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '작업 시간 적기' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /기록 지우기/ })).not.toBeInTheDocument();
  });
});

describe('일감 보기의 리비전 · 공수 표시', () => {
  it('예상 공수 · 쓴 시간(쓴 시간 / 예상 글) · 판 표 · 밀리초 시각을 보인다', async () => {
    vi.mocked(issueApi.detail).mockResolvedValue({
      ...issue,
      spentHours: 12.5,
      journal: [
        {
          id: 'j1',
          actorType: 'USER',
          actorId: ME,
          actorName: '김철수',
          via: 'WEB',
          createdAt: '2026-11-22T09:03:04.007',
          notes: null,
          changes: [{ field: 'estimatedHours', before: '8', after: '10' }],
          revisionNo: 3,
        },
        {
          id: 'j2',
          actorType: 'USER',
          actorId: LEE,
          actorName: '이영희',
          via: 'MCP',
          createdAt: '2026-11-22T09:10:00.120',
          notes: null,
          changes: [{ field: 'workLog', before: null, after: { id: 'w', user: { id: LEE, name: '이영희' }, date: '2026-11-21', hours: '1.5' } }],
          revisionNo: null,
        },
      ],
    });
    render(
      <Providers permissions={['issue.read']} path="/w/a/projects/web/issues/7">
        <IssueViewPage />
      </Providers>,
    );
    expect(await screen.findByRole('heading', { name: '로그인 실패' })).toBeInTheDocument();
    const fields = screen.getByRole('complementary', { name: '속성' });
    expect(within(fields).getByText('10시간')).toBeInTheDocument();
    expect(within(fields).getByText('예상의 125% 씀 (12.5 / 10시간)')).toBeInTheDocument();
    expect(within(fields).getByText('예상보다 2.5시간 더 썼습니다.')).toBeInTheDocument();
    // 속성은 탭 위에 있고, 머리에 이미 있는 상태 · 유형과 고친 때는 넣지 않는다(고친 때는 만든 때 옆)
    expect(fields.compareDocumentPosition(screen.getByRole('tablist')) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(within(fields).queryByText('상태')).not.toBeInTheDocument();
    expect(within(fields).queryByText('유형')).not.toBeInTheDocument();
    expect(within(fields).queryByText('2026-11-22 09:03:04.007')).not.toBeInTheDocument();
    expect(screen.getByText(/2026-11-20 09:00:00\.001 만듦/)).toHaveTextContent('2026-11-22 09:03:04.007 고침');
    // 내용 탭(기본) — 고칠 수 없으면 작업 시간 적기 양식은 없다
    expect(screen.queryByRole('button', { name: '적기' })).not.toBeInTheDocument();

    // 이력은 댓글 탭에 있다
    fireEvent.click(screen.getByRole('tab', { name: /댓글/ }));

    // 이력: 판을 만든 줄에만 "판 N" — 누르면 그 판의 차이
    expect(screen.getByRole('link', { name: '판 3' })).toHaveAttribute('href', '/w/a/projects/web/issues/7/history?rev=3');
    expect(screen.getByText('예상 공수: 8시간 → 10시간')).toBeInTheDocument();
    expect(screen.getByText('작업 시간 기록: 이영희 · 2026-11-21 · 1.5시간')).toBeInTheDocument();
    expect(screen.getByText(/2026-11-22 09:10:00\.120/)).toBeInTheDocument();
    // 독자도 리비전 화면으로 갈 수 있다 — 머리의 링크는 어느 탭에서든 그대로, 댓글 탭의 이력 머리에도 하나
    const revisionLinks = screen.getAllByRole('link', { name: '리비전 · 차이 보기' });
    expect(revisionLinks).toHaveLength(2);
    for (const link of revisionLinks) {
      expect(link).toHaveAttribute('href', '/w/a/projects/web/issues/7/history');
    }
  });
});
