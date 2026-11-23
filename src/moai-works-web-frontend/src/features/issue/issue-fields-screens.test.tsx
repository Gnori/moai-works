import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { useState, type ReactNode } from 'react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import '@/shared/i18n/i18n';
import { ProjectProvider } from '@/features/project/project-context';
import { WorkspaceProvider } from '@/features/workspace/workspace-context';
import { attachmentApi, issueApi, issueTemplateApi } from '@/shared/api/endpoints';
import type * as Endpoints from '@/shared/api/endpoints';
import type { IssueDetail, IssueSummary, PermissionCode, ProjectDetail, WorkspaceDetail } from '@/shared/api/types';
import { ConfirmProvider } from '@/shared/ui/ConfirmDialog';
import { AppearanceProvider } from '@/theme/AppearanceProvider';

import { emptyIssueForm, type IssueFormValues } from './issue-form';
import { IssueForm } from './IssueForm';
import { IssueList } from './IssueList';
import { IssueViewPage } from './IssueViewPage';

/**
 * 일감의 에이전트 세션(일감 #26) — 목록의 칩 · 거르기(주소에 남음) · 양식의 입력 칸 · 보기의 속성.
 * 서버 호출만 가짜로 두고 화면 · 주소 · 말은 그대로.
 */
vi.mock('@/shared/api/endpoints', async (importOriginal) => {
  const actual = await importOriginal<typeof Endpoints>();
  return {
    ...actual,
    issueApi: { ...actual.issueApi, list: vi.fn(), types: vi.fn(), agentSessions: vi.fn(), detail: vi.fn() },
    issueTemplateApi: { ...actual.issueTemplateApi, project: vi.fn() },
    attachmentApi: { ...actual.attachmentApi, list: vi.fn() },
  };
});

const ME = 'aaaaaaaa-0000-0000-0000-000000000001';

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
    issueCount: 2,
    myRoles: [],
    myPermissions: permissions,
    myActions: [],
  };
}

function summary(number: number, over: Partial<IssueSummary> = {}): IssueSummary {
  return {
    number,
    type: 'feature',
    title: `일감 ${number}`,
    status: 'NEW',
    manager: { id: ME, username: 'kim', displayName: '김철수' },
    assignee: null,
    parent: null,
    dueDate: null,
    startDate: null,
    plannedEndDate: null,
    completedOn: null,
    closedOn: null,
    updatedAt: '2026-11-22T09:00:00.000',
    childCount: 0,
    overdue: false,
    estimatedHours: null,
    spentHours: 0,
    agentSession: null,
    ...over,
  };
}

const issue: IssueDetail = {
  number: 7,
  type: 'feature',
  title: '세션 일감',
  description: '본문',
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
  estimatedHours: null,
  spentHours: 0,
  agentSession: 'moai-works',
  children: [],
  links: [],
  journal: [],
  workLogs: [],
};

/** 지금 주소의 검색 조건 — 거르기가 주소에 남는지 본다 */
function Search() {
  return <output data-testid="search">{useLocation().search}</output>;
}

function Providers({ children, permissions = ['issue.read'], path, route = '/w/:slug/projects/:projectKey/*' }: {
  children: ReactNode;
  permissions?: PermissionCode[];
  path: string;
  route?: string;
}) {
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
                    path={route}
                    element={
                      <>
                        {children}
                        <Search />
                      </>
                    }
                  />
                </Routes>
              </MemoryRouter>
            </ProjectProvider>
          </WorkspaceProvider>
        </ConfirmProvider>
      </QueryClientProvider>
    </AppearanceProvider>
  );
}

/** 양식을 실제 상태와 이어 그린다 — 바뀐 값을 밖에서 본다 */
function FormHarness({ onValue }: { onValue: (value: IssueFormValues) => void }) {
  const [value, setValue] = useState<IssueFormValues>(emptyIssueForm('feature'));
  return (
    <IssueForm
      slug="a"
      project={project(['issue.read', 'issue.create'])}
      value={value}
      onChange={(patch) =>
        setValue((before) => {
          const next = { ...before, ...patch };
          onValue(next);
          return next;
        })
      }
      initialLabels={{ manager: null, assignee: null, parent: null }}
      types={[{ key: 'feature', name: '새기능', description: null, active: true }]}
      errors={{}}
    />
  );
}

beforeEach(() => {
  vi.mocked(issueApi.list).mockResolvedValue({
    items: [summary(2, { agentSession: 'moai-works' }), summary(1)],
    total: 2,
    page: 0,
    size: 50,
    totalPages: 1,
    hasNext: false,
  });
  vi.mocked(issueApi.types).mockResolvedValue([{ key: 'feature', name: '새기능', description: null, active: true }]);
  vi.mocked(issueApi.agentSessions).mockResolvedValue([
    { name: 'moai-wiki', issueCount: 3 },
    { name: 'moai-works', issueCount: 1 },
  ]);
  vi.mocked(issueApi.detail).mockResolvedValue(issue);
  vi.mocked(issueTemplateApi.project).mockResolvedValue([]);
  vi.mocked(attachmentApi.list).mockResolvedValue([]);
});

afterEach(() => {
  vi.clearAllMocks();
});

describe('일감 목록의 에이전트 세션', () => {
  it('세션이 있는 일감에 칩을 보이고, 세션으로 거르면 주소에 남기고 api 로 보낸다', async () => {
    render(
      <Providers path="/w/a/projects/web">
        <IssueList slug="a" project={project(['issue.read'])} />
      </Providers>,
    );
    expect(await screen.findByLabelText('에이전트 세션: moai-works')).toBeInTheDocument();
    // 세션이 없는 일감에는 칩이 없다
    expect(screen.getAllByLabelText(/^에이전트 세션: /)).toHaveLength(1);

    fireEvent.mouseDown(screen.getByRole('combobox', { name: '에이전트 세션' }));
    const listbox = await screen.findByRole('listbox');
    expect(within(listbox).getByRole('option', { name: '모든 세션' })).toBeInTheDocument();
    expect(within(listbox).getByRole('option', { name: '세션 없음' })).toBeInTheDocument();
    fireEvent.click(within(listbox).getByRole('option', { name: 'moai-wiki' }));

    await waitFor(() => expect(screen.getByTestId('search')).toHaveTextContent('agentSession=moai-wiki'));
    await waitFor(() =>
      expect(vi.mocked(issueApi.list)).toHaveBeenLastCalledWith('a', 'web', expect.objectContaining({ agentSession: 'moai-wiki' })),
    );
  });

  it('주소의 "세션 없음" 조건으로 연다', async () => {
    render(
      <Providers path="/w/a/projects/web?agentSession=none">
        <IssueList slug="a" project={project(['issue.read'])} />
      </Providers>,
    );
    await waitFor(() => expect(vi.mocked(issueApi.list)).toHaveBeenCalledWith('a', 'web', expect.objectContaining({ agentSession: 'none' })));
    expect(await screen.findByRole('combobox', { name: '에이전트 세션' })).toHaveTextContent('세션 없음');
  });
});

describe('일감 양식의 에이전트 세션', () => {
  it('비워도 되는 입력 칸과 안내 글이 있고, 친 값이 양식 값이 된다', async () => {
    const onValue = vi.fn();
    render(
      <Providers path="/w/a/projects/web/issues/new">
        <FormHarness onValue={onValue} />
      </Providers>,
    );
    const input = await screen.findByRole('textbox', { name: '에이전트 세션' });
    expect(screen.getByText('이 일을 맡은 AI 세션 이름 — 비워도 된다')).toBeInTheDocument();
    expect(input).toHaveAttribute('maxLength', '100');
    fireEvent.change(input, { target: { value: 'moai-works' } });
    expect(onValue).toHaveBeenLastCalledWith(expect.objectContaining({ agentSession: 'moai-works' }));
  });
});

describe('일감 보기의 에이전트 세션', () => {
  it('속성에 세션 칩을, 없으면 "없음"을 보인다', async () => {
    render(
      <Providers path="/w/a/projects/web/issues/7" route="/w/:slug/projects/:projectKey/issues/:number/*">
        <IssueViewPage />
      </Providers>,
    );
    const properties = await screen.findByRole('complementary', { name: '속성' });
    expect(within(properties).getByText('에이전트 세션')).toBeInTheDocument();
    expect(within(properties).getByLabelText('에이전트 세션: moai-works')).toBeInTheDocument();
  });

  it('세션이 없으면 "없음"', async () => {
    vi.mocked(issueApi.detail).mockResolvedValue({ ...issue, agentSession: null });
    render(
      <Providers path="/w/a/projects/web/issues/7" route="/w/:slug/projects/:projectKey/issues/:number/*">
        <IssueViewPage />
      </Providers>,
    );
    const properties = await screen.findByRole('complementary', { name: '속성' });
    expect(within(properties).queryByLabelText(/^에이전트 세션: /)).not.toBeInTheDocument();
    const field = within(properties).getByText('에이전트 세션').parentElement as HTMLElement;
    expect(within(field).getByText('없음')).toBeInTheDocument();
  });
});
