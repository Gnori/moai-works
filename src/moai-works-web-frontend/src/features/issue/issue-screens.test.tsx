import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import type { ReactNode } from 'react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';

import '@/shared/i18n/i18n';
import { ProjectProvider } from '@/features/project/project-context';
import { WorkspaceProvider } from '@/features/workspace/workspace-context';
import { attachmentApi, issueApi } from '@/shared/api/endpoints';
import type * as Endpoints from '@/shared/api/endpoints';
import type { IssueDetail, IssueRevision, IssueRevisionDiff, IssueRevisionItem, PermissionCode, ProjectDetail, WorkspaceDetail } from '@/shared/api/types';
import { ConfirmProvider } from '@/shared/ui/ConfirmDialog';
import { AppearanceProvider } from '@/theme/AppearanceProvider';

import { IssueHistoryRedirect } from './IssueHistoryRedirect';
import { IssueViewPage } from './IssueViewPage';
import { IssueWorkLogs } from './IssueWorkLogs';

/**
 * 일감 보기의 리비전 탭 · 예전 리비전 주소 · 작업 시간 칸을 실제로 그려 본다 — 서버 호출(issueApi)만 가짜로 두고 화면 · 주소 · 말은 그대로.
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
  project: { key: 'web', name: '웹' },
  type: 'bug',
  title: '로그인 실패',
  description: '하나\n둘 고침',
  status: 'IN_PROGRESS',
  priority: 'NORMAL',
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
  agentSession: null,
  modules: [],
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
      { field: 'priority', before: 'LOW', after: 'HIGH' },
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

/** 지금 주소(경로 + ?…) — 탭 · 고른 리비전이 주소에 남는지, 예전 주소가 어디로 가는지 본다 */
function LocationProbe() {
  const { pathname, search } = useLocation();
  return <div data-testid="location">{`${pathname}${search}`}</div>;
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
                  <Route
                    path="/w/:slug/issues/:number"
                    element={
                      <>
                        {children}
                        <LocationProbe />
                      </>
                    }
                  />
                  {/* 예전 리비전 화면 주소 — 앱의 경로 표와 같이 리비전 탭으로 돌린다 */}
                  <Route path="/w/:slug/issues/:number/history" element={<IssueHistoryRedirect />} />
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
  vi.mocked(issueApi.diff).mockImplementation(async (_s, _n, no, from) => diff(no, from ?? (no > 1 ? no - 1 : null)));
  vi.mocked(attachmentApi.list).mockResolvedValue([]);
});

afterEach(() => {
  vi.clearAllMocks();
});

/** 보이는 리비전 탭의 내용 — 숨은 탭(내용 · 댓글 · 첨부파일)은 찾지 않는다 */
const revisionsPanel = () => screen.getByRole('tabpanel', { name: /리비전/ });

const revisionTwo: IssueRevision = {
  ...item(2, { actorName: '이영희' }),
  type: 'bug',
  title: '로그인 실패 (옛 제목)',
  description: '하나\n둘',
  status: 'NEW',
  priority: 'NORMAL',
  manager: { id: ME, username: 'kim', displayName: '김철수' },
  assignee: null,
  parent: null,
  dueDate: '2026-12-01',
  startDate: null,
  plannedEndDate: null,
  completedOn: null,
  closedOn: null,
  estimatedHours: null,
  agentSession: null,
  modules: [],
  project: { key: 'web', name: '웹' },
};

describe('일감 보기의 리비전 탭', () => {
  it('주소(?tab=revisions&rev=3&from=1)로 열면 타임라인(리비전 · 누가 · 밀리초 시각 · MCP · 바꾼 필드 · 현재 리비전)과 두 리비전의 차이를 그린다', async () => {
    render(
      <Providers permissions={['issue.read']} path="/w/a/issues/7?tab=revisions&rev=3&from=1">
        <IssueViewPage />
      </Providers>,
    );

    // 첨부파일 옆 넷째 탭 — 이름에 리비전 수(지금 리비전 번호)
    const tab = await screen.findByRole('tab', { name: '리비전 3' });
    expect(tab).toHaveAttribute('aria-selected', 'true');
    const issueTabs = screen.getByRole('tablist', { name: '일감 보기' });
    await waitFor(() => expect(within(issueTabs).getAllByRole('tab').map((t) => t.textContent)).toEqual(['내용', '댓글0', '첨부파일0', '리비전3']));

    const timeline = await within(revisionsPanel()).findByRole('list', { name: '리비전 타임라인' });
    const entries = within(timeline).getAllByRole('listitem');
    expect(entries).toHaveLength(3);
    expect(within(entries[0] as HTMLElement).getByText('리비전 3')).toBeInTheDocument();
    expect(within(entries[0] as HTMLElement).getByText('현재 리비전')).toBeInTheDocument();
    expect(within(entries[0] as HTMLElement).getByText('MCP')).toBeInTheDocument();
    expect(within(entries[0] as HTMLElement).getByText('예상 공수')).toBeInTheDocument();
    expect(within(entries[0] as HTMLElement).getByText(/2026-11-23 09:03:04\.003/)).toBeInTheDocument();
    expect(within(entries[1] as HTMLElement).getByText('이영희')).toBeInTheDocument();
    expect(within(entries[2] as HTMLElement).getByText('만듦')).toBeInTheDocument();
    // 주소에 고른 리비전이 있다 — 그 줄이 골라져 있다
    expect(within(entries[0] as HTMLElement).getByRole('button')).toHaveAttribute('aria-current', 'true');

    // 주소의 ?rev=3&from=1 그대로 묻는다
    await waitFor(() => expect(issueApi.diff).toHaveBeenCalledWith('a', 7, 3, 1));
    const panel = revisionsPanel();
    expect(await within(panel).findByText('리비전 1 → 리비전 3 · 바뀐 필드 3 · 설명 넣은 줄 1 · 지운 줄 1')).toBeInTheDocument();

    // 필드: 상태는 번역해서 − 전 · + 후, 예상 공수는 "10시간"
    expect(within(panel).getByText('등록')).toBeInTheDocument();
    expect(within(panel).getByText('진행')).toBeInTheDocument();
    expect(within(panel).getByText('10시간')).toBeInTheDocument();
    expect(within(panel).getByText('낮음')).toBeInTheDocument();
    expect(within(panel).getByText('높음')).toBeInTheDocument();

    // 설명: git 처럼 줄 번호 · − · + 와 함께
    const table = within(panel).getByRole('table', { name: '두 리비전의 설명 차이' });
    expect(within(table).getByLabelText('지운 줄')).toHaveTextContent('−');
    expect(within(table).getByLabelText('넣은 줄')).toHaveTextContent('+');
    expect(within(table).getByText('둘 고침')).toBeInTheDocument();
  });

  it('탭을 고르면 주소가 ?tab=revisions 가 되고, 리비전을 고르면 그 차이 · 전체를 보이며, 탭을 오가도 보던 리비전이 남는다', async () => {
    vi.mocked(issueApi.revision).mockResolvedValue(revisionTwo);
    render(
      <Providers permissions={['issue.read']} path="/w/a/issues/7?settings=profile">
        <IssueViewPage />
      </Providers>,
    );
    const tab = await screen.findByRole('tab', { name: '리비전 3' });
    // 다른 탭을 보는 동안에는 리비전을 읽지 않는다
    expect(tab).toHaveAttribute('aria-selected', 'false');
    expect(issueApi.revisions).not.toHaveBeenCalled();
    expect(issueApi.diff).not.toHaveBeenCalled();

    fireEvent.click(tab);
    expect(screen.getByTestId('location')).toHaveTextContent('/w/a/issues/7?settings=profile&tab=revisions');
    expect(tab).toHaveAttribute('aria-selected', 'true');
    // 고른 리비전이 없으면 현재 리비전을 바로 앞 리비전과
    await waitFor(() => expect(issueApi.diff).toHaveBeenCalledWith('a', 7, 3, undefined));
    expect(await within(revisionsPanel()).findByText('리비전 2 → 리비전 3 · 바뀐 필드 3 · 설명 넣은 줄 1 · 지운 줄 1')).toBeInTheDocument();

    // 리비전 2 를 고르면 그 차이(바로 앞 리비전 1 과) — 주소에 남고, 좁은 화면이면 차이로 내려 준다
    const timeline = within(revisionsPanel()).getByRole('list', { name: '리비전 타임라인' });
    fireEvent.click(within(timeline).getByText('리비전 2'));
    await waitFor(() => expect(issueApi.diff).toHaveBeenCalledWith('a', 7, 2, undefined));
    expect(screen.getByTestId('location')).toHaveTextContent('/w/a/issues/7?settings=profile&tab=revisions&rev=2');
    expect(await within(revisionsPanel()).findByText('리비전 1 → 리비전 2 · 바뀐 필드 3 · 설명 넣은 줄 1 · 지운 줄 1')).toBeInTheDocument();
    expect(Element.prototype.scrollIntoView).toHaveBeenCalled();

    // 이 리비전 전체 보기
    fireEvent.click(within(revisionsPanel()).getByRole('tab', { name: '이 리비전 전체 보기' }));
    expect(await within(revisionsPanel()).findByText('로그인 실패 (옛 제목)')).toBeInTheDocument();
    expect(issueApi.revision).toHaveBeenCalledWith('a', 7, 2);
    // 날짜만 있는 값은 날짜만
    expect(within(revisionsPanel()).getByText('2026-12-01')).toBeInTheDocument();
    // 그때의 우선순위 — 보통은 흐린 글
    expect(within(revisionsPanel()).getByText('우선순위')).toBeInTheDocument();
    expect(within(revisionsPanel()).getByText('보통')).toBeInTheDocument();
    expect(screen.getByTestId('location')).toHaveTextContent('tab=revisions&rev=2&view=snapshot');

    // 내용 탭에 갔다 돌아와도 보던 리비전 2 전체가 그대로
    fireEvent.click(screen.getByRole('tab', { name: /내용/ }));
    expect(screen.getByTestId('location')).toHaveTextContent('/w/a/issues/7?settings=profile&rev=2&view=snapshot');
    // 리비전 탭은 늘 그려 두고 숨기기만 한다
    expect(screen.getByText('로그인 실패 (옛 제목)')).not.toBeVisible();
    fireEvent.click(screen.getByRole('tab', { name: '리비전 3' }));
    expect(within(revisionsPanel()).getByText('로그인 실패 (옛 제목)')).toBeVisible();
  });

  it('예전 리비전 화면 주소(issues/:number/history)는 리비전 탭으로 돌리고 고른 리비전을 그대로 가져간다', async () => {
    render(
      <Providers permissions={['issue.read']} path="/w/a/issues/7/history?rev=2&from=0">
        <IssueViewPage />
      </Providers>,
    );
    expect(await screen.findByRole('tab', { name: '리비전 3' })).toHaveAttribute('aria-selected', 'true');
    expect(screen.getByTestId('location')).toHaveTextContent('/w/a/issues/7?rev=2&from=0&tab=revisions');
    await waitFor(() => expect(issueApi.diff).toHaveBeenCalledWith('a', 7, 2, 0));
  });
});

describe('작업 시간 칸', () => {
  it('기록(날 · 누가 · 시간 · 메모 · MCP · 적은 때 밀리초)과 합계를 보이고, 지우기는 내 기록에만', async () => {
    render(
      <Providers permissions={['issue.read', 'issue.edit']} path="/w/a/issues/7">
        <IssueWorkLogs slug="a" issue={issue} canLog canManage={false} />
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
      <Providers permissions={['issue.read', 'issue.edit', 'project.manage']} path="/w/a/issues/7">
        <IssueWorkLogs slug="a" issue={issue} canLog canManage />
      </Providers>,
    );
    fireEvent.click(screen.getByRole('button', { name: /이영희 · 1\.5시간 기록 지우기/ }));
    const dialog = await screen.findByRole('dialog');
    expect(dialog).toHaveTextContent('이영희 · 2026-11-21 · 1.5시간 기록을 지웁니다.');
    expect(issueApi.removeWork).not.toHaveBeenCalled();
    fireEvent.click(within(dialog).getByRole('button', { name: '지우기' }));
    await waitFor(() => expect(issueApi.removeWork).toHaveBeenCalledWith('a', 7, 'w-lee'));
  });

  it('적기 — 맞지 않는 시간은 칸에 알리고, 맞으면 보낸다', async () => {
    vi.mocked(issueApi.logWork).mockResolvedValue(issue);
    render(
      <Providers permissions={['issue.read', 'issue.edit']} path="/w/a/issues/7">
        <IssueWorkLogs slug="a" issue={issue} canLog canManage={false} />
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
      expect(issueApi.logWork).toHaveBeenCalledWith('a', 7, expect.objectContaining({ hours: 1.25, note: '검토' })),
    );
  });

  it('고칠 수 없으면 적기 양식도 지우기 단추도 없다', () => {
    render(
      <Providers permissions={['issue.read']} path="/w/a/issues/7">
        <IssueWorkLogs slug="a" issue={issue} canLog={false} canManage={false} />
      </Providers>,
    );
    expect(screen.queryByRole('button', { name: '적기' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '작업 시간 적기' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /기록 지우기/ })).not.toBeInTheDocument();
  });
});

describe('일감 보기의 리비전 · 공수 표시', () => {
  it('예상 공수 · 쓴 시간(쓴 시간 / 예상 글) · 리비전 표 · 밀리초 시각을 보인다', async () => {
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
          changes: [
            { field: 'estimatedHours', before: '8', after: '10' },
            { field: 'priority', before: 'NORMAL', after: 'URGENT' },
          ],
          revisionNo: 3,
          rowVersion: null,
          editedAt: null,
          deleted: false,
          deletedAt: null,
          deletedBy: null,
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
          rowVersion: null,
          editedAt: null,
          deleted: false,
          deletedAt: null,
          deletedBy: null,
        },
      ],
    });
    render(
      <Providers permissions={['issue.read']} path="/w/a/issues/7">
        <IssueViewPage />
      </Providers>,
    );
    expect(await screen.findByRole('heading', { name: '로그인 실패' })).toBeInTheDocument();
    const fields = screen.getByRole('complementary', { name: '속성' });
    expect(within(fields).getByText('10시간')).toBeInTheDocument();
    expect(within(fields).getByText('예상의 125% 소요 (12.5 / 10시간)')).toBeInTheDocument();
    expect(within(fields).getByText('예상보다 2.5시간 더 걸렸습니다.')).toBeInTheDocument();
    // 속성은 탭 위에 있고, 머리에 이미 있는 상태 · 유형과 고친 때는 넣지 않는다(고친 때는 만든 때 옆)
    expect(fields.compareDocumentPosition(screen.getByRole('tablist')) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(within(fields).queryByText('상태')).not.toBeInTheDocument();
    expect(within(fields).queryByText('유형')).not.toBeInTheDocument();
    expect(within(fields).queryByText('2026-11-22 09:03:04.007')).not.toBeInTheDocument();
    expect(screen.getByText(/2026-11-20 09:00:00\.001 만듦/)).toHaveTextContent('2026-11-22 09:03:04.007 수정');
    // 내용 탭(기본) — 고칠 수 없으면 작업 시간 적기 양식은 없다
    expect(screen.queryByRole('button', { name: '적기' })).not.toBeInTheDocument();

    // 리비전은 머리(상태 옆)가 아니라 첨부파일 옆 탭에 있다 — 머리의 "현재 리비전 N" · 리비전 단추는 없다
    expect(screen.queryByText('현재 리비전 3')).not.toBeInTheDocument();
    expect(screen.queryByRole('link', { name: /리비전/ })).not.toBeInTheDocument();
    expect(screen.getByRole('tab', { name: '리비전 3' })).toBeInTheDocument();

    // 이력은 댓글 탭에 있다
    fireEvent.click(screen.getByRole('tab', { name: /댓글/ }));

    // 이력: 리비전을 만든 줄에만 "리비전 N" — 누르면 리비전 탭에서 그 리비전의 차이
    const revisionLink = screen.getByRole('link', { name: '리비전 3' });
    expect(revisionLink).toHaveAttribute('href', '/w/a/issues/7?tab=revisions&rev=3');
    expect(screen.getByText('예상 공수: 8시간 → 10시간')).toBeInTheDocument();
    // 우선순위는 코드가 아니라 이름으로(2026-11-26, 일감 #302)
    expect(screen.getByText('우선순위: 보통 → 긴급')).toBeInTheDocument();
    expect(screen.getByText('작업 시간 기록: 이영희 · 2026-11-21 · 1.5시간')).toBeInTheDocument();
    expect(screen.getByText(/2026-11-22 09:10:00\.120/)).toBeInTheDocument();
    // 댓글 탭의 이력 머리에도 따로 리비전 화면 링크는 없다(탭이 대신한다)
    expect(screen.getAllByRole('link', { name: /리비전/ })).toEqual([revisionLink]);

    fireEvent.click(revisionLink);
    expect(screen.getByRole('tab', { name: '리비전 3' })).toHaveAttribute('aria-selected', 'true');
    await waitFor(() => expect(issueApi.diff).toHaveBeenCalledWith('a', 7, 3, undefined));
  });
});
