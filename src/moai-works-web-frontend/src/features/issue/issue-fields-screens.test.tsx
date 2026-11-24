import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { useState, type ReactNode } from 'react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import '@/shared/i18n/i18n';
import { ProjectModulesPage } from '@/features/project/ProjectModulesPage';
import { ProjectProvider } from '@/features/project/project-context';
import { WorkspaceProvider } from '@/features/workspace/workspace-context';
import { attachmentApi, issueApi, issueTemplateApi, projectApi } from '@/shared/api/endpoints';
import type * as Endpoints from '@/shared/api/endpoints';
import type { IssueDetail, IssueSummary, PermissionCode, ProjectDetail, ProjectModule, WorkspaceDetail } from '@/shared/api/types';
import { ConfirmProvider } from '@/shared/ui/ConfirmDialog';
import { AppearanceProvider } from '@/theme/AppearanceProvider';

import { emptyIssueForm, type IssueFormValues } from './issue-form';
import { IssueForm } from './IssueForm';
import { IssueList } from './IssueList';
import { IssueViewPage } from './IssueViewPage';

/**
 * 일감의 에이전트 세션(일감 #26)과 관련 모듈 · 프로젝트 모듈 화면(일감 #27) — 목록의 칩 · 거르기(주소에 남음) · 양식의 입력 칸 ·
 * 보기의 속성 · 모듈 관리(더하기 · 수정 · 순서 · 쓰지 않음). 서버 호출만 가짜로 두고 화면 · 주소 · 말은 그대로.
 */
vi.mock('@/shared/api/endpoints', async (importOriginal) => {
  const actual = await importOriginal<typeof Endpoints>();
  return {
    ...actual,
    issueApi: { ...actual.issueApi, list: vi.fn(), types: vi.fn(), agentSessions: vi.fn(), detail: vi.fn() },
    issueTemplateApi: { ...actual.issueTemplateApi, project: vi.fn() },
    attachmentApi: { ...actual.attachmentApi, list: vi.fn() },
    projectApi: { ...actual.projectApi, modules: vi.fn(), createModule: vi.fn(), updateModule: vi.fn(), reorderModules: vi.fn() },
  };
});

function mod(key: string, name: string, position: number, over: Partial<ProjectModule> = {}): ProjectModule {
  return {
    key,
    name,
    description: null,
    position,
    active: true,
    issueCount: 0,
    createdAt: '2026-11-23T09:00:00.000',
    updatedAt: '2026-11-23T09:00:00.000',
    rowVersion: 0,
    ...over,
  };
}

const moduleList: ProjectModule[] = [
  mod('api', 'API', 0, { issueCount: 3, description: 'REST · MCP' }),
  mod('web-frontend', '웹 프런트', 1),
  mod('infra', '인프라', 2, { active: false }),
];

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
    project: { key: 'web', name: '웹' },
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
    modules: [],
    ...over,
  };
}

const issue: IssueDetail = {
  number: 7,
  project: { key: 'web', name: '웹' },
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
  modules: [],
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
  vi.mocked(projectApi.modules).mockResolvedValue(moduleList);
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
    const input = await screen.findByRole('combobox', { name: '에이전트 세션' });
    expect(screen.getByText('이 일을 맡은 AI 세션 이름 — 비워도 된다')).toBeInTheDocument();
    expect(input).toHaveAttribute('maxLength', '100');
    // 후보에 없는 새 이름도 그대로 양식 값이 된다
    fireEvent.change(input, { target: { value: 'new-session' } });
    expect(onValue).toHaveBeenLastCalledWith(expect.objectContaining({ agentSession: 'new-session' }));
  });

  it('고르기 후보는 이 프로젝트의 에이전트 멤버 — 쓰는 것 먼저(서버 차례), 쓰지 않음 · 등록 안 됨은 작은 글로 알리고 고르면 값이 된다', async () => {
    vi.mocked(issueApi.agentSessions).mockResolvedValue([
      { name: 'zeta-agent', issueCount: 0, registered: true, active: true },
      { name: 'moai-works', issueCount: 2, registered: true, active: true },
      { name: 'old-agent', issueCount: 1, registered: true, active: false },
      { name: 'Stray', issueCount: 1, registered: false, active: false },
    ]);
    const onValue = vi.fn();
    render(
      <Providers path="/w/a/projects/web/issues/new">
        <FormHarness onValue={onValue} />
      </Providers>,
    );
    const input = await screen.findByRole('combobox', { name: '에이전트 세션' });
    await waitFor(() => expect(issueApi.agentSessions).toHaveBeenCalledWith('a', 'web'));
    fireEvent.mouseDown(input);
    const listbox = await screen.findByRole('listbox');
    const options = within(listbox).getAllByRole('option');
    // 이름순이 아니라 서버 차례 — 등록된 쓰는 에이전트(0건도) → 쓰지 않음 → 등록 안 됨
    expect(options.map((o) => o.textContent)).toEqual([
      'zeta-agent일감 0건',
      'moai-works일감 2건',
      'old-agent쓰지 않음 · 일감 1건',
      'Stray에이전트 멤버 아님 · 일감 1건',
    ]);
    fireEvent.click(within(listbox).getByRole('option', { name: /^moai-works/ }));
    expect(onValue).toHaveBeenLastCalledWith(expect.objectContaining({ agentSession: 'moai-works' }));
    expect(input).toHaveValue('moai-works');
    // 친 글로 후보를 좁힌다(대소문자 무시)
    fireEvent.change(input, { target: { value: 'OLD' } });
    const narrowed = await screen.findByRole('listbox');
    expect(within(narrowed).getAllByRole('option').map((o) => o.textContent)).toEqual(['old-agent쓰지 않음 · 일감 1건']);
    expect(onValue).toHaveBeenLastCalledWith(expect.objectContaining({ agentSession: 'OLD' }));
  });
});

describe('일감 보기의 에이전트 세션', () => {
  it('속성에 세션 칩을, 없으면 "없음"을 보인다', async () => {
    render(
      <Providers path="/w/a/issues/7" route="/w/:slug/issues/:number/*">
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
      <Providers path="/w/a/issues/7" route="/w/:slug/issues/:number/*">
        <IssueViewPage />
      </Providers>,
    );
    const properties = await screen.findByRole('complementary', { name: '속성' });
    expect(within(properties).queryByLabelText(/^에이전트 세션: /)).not.toBeInTheDocument();
    const field = within(properties).getByText('에이전트 세션').parentElement as HTMLElement;
    expect(within(field).getByText('없음')).toBeInTheDocument();
  });
});

describe('일감 목록 · 보기의 관련 모듈', () => {
  it('붙은 모듈을 칩으로 보이고(쓰지 않는 모듈은 말로도), 모듈 여럿으로 거르면 주소에 남긴다', async () => {
    vi.mocked(issueApi.list).mockResolvedValue({
      items: [
        summary(2, {
          modules: [
            { key: 'api', name: 'API', active: true },
            { key: 'infra', name: '인프라', active: false },
          ],
        }),
      ],
      total: 1,
      page: 0,
      size: 50,
      totalPages: 1,
      hasNext: false,
    });
    render(
      <Providers path="/w/a/projects/web">
        <IssueList slug="a" project={project(['issue.read'])} />
      </Providers>,
    );
    expect(await screen.findByLabelText('모듈: API')).toBeInTheDocument();
    expect(screen.getByLabelText('모듈: 인프라 (쓰지 않음)')).toBeInTheDocument();

    fireEvent.mouseDown(await screen.findByRole('combobox', { name: '모듈' }));
    const listbox = await screen.findByRole('listbox');
    // 쓰지 않는 모듈로도 거른다
    expect(within(listbox).getByRole('option', { name: '인프라 (쓰지 않음)' })).toBeInTheDocument();
    fireEvent.click(within(listbox).getByRole('option', { name: 'API' }));
    fireEvent.click(within(listbox).getByRole('option', { name: '웹 프런트' }));
    await waitFor(() => expect(screen.getByTestId('search')).toHaveTextContent('module=api&module=web-frontend'));
    await waitFor(() =>
      expect(vi.mocked(issueApi.list)).toHaveBeenLastCalledWith('a', 'web', expect.objectContaining({ module: ['api', 'web-frontend'] })),
    );
  });

  it('보기 속성에 모듈 칩을, 없으면 "없음"을 보인다', async () => {
    vi.mocked(issueApi.detail).mockResolvedValue({ ...issue, modules: [{ key: 'web-frontend', name: '웹 프런트', active: true }] });
    render(
      <Providers path="/w/a/issues/7" route="/w/:slug/issues/:number/*">
        <IssueViewPage />
      </Providers>,
    );
    const properties = await screen.findByRole('complementary', { name: '속성' });
    expect(within(properties).getByText('관련 모듈')).toBeInTheDocument();
    expect(within(properties).getByLabelText('모듈: 웹 프런트')).toBeInTheDocument();
  });
});

describe('일감 양식의 관련 모듈', () => {
  it('쓰는 모듈만 여럿 고르고, 쓰지 않는 모듈은 목록에 없다', async () => {
    const onValue = vi.fn();
    render(
      <Providers path="/w/a/projects/web/issues/new">
        <FormHarness onValue={onValue} />
      </Providers>,
    );
    expect(await screen.findByText('이 일에서 수정하는 모듈 — 여럿 고를 수 있고, 비워도 된다')).toBeInTheDocument();
    fireEvent.mouseDown(screen.getByRole('combobox', { name: '관련 모듈' }));
    const listbox = await screen.findByRole('listbox');
    expect(within(listbox).queryByText('인프라')).not.toBeInTheDocument();
    fireEvent.click(within(listbox).getByRole('option', { name: /웹 프런트/ }));
    fireEvent.click(within(listbox).getByRole('option', { name: /API/ }));
    expect(onValue).toHaveBeenLastCalledWith(expect.objectContaining({ modules: ['web-frontend', 'api'] }));
  });

  it('프로젝트에 모듈이 없으면 칸을 끄고 어디서 더하는지 알린다', async () => {
    vi.mocked(projectApi.modules).mockResolvedValue([]);
    render(
      <Providers path="/w/a/projects/web/issues/new">
        <FormHarness onValue={vi.fn()} />
      </Providers>,
    );
    expect(await screen.findByText('이 프로젝트에 모듈이 없습니다 — 프로젝트의 모듈 화면에서 더합니다.')).toBeInTheDocument();
    expect(screen.getByRole('combobox', { name: '관련 모듈' })).toHaveAttribute('aria-disabled', 'true');
  });
});

describe('프로젝트 모듈 화면', () => {
  it('독자에게는 목록만 — 더하기 · 수정 · 순서 단추가 없다', async () => {
    render(
      <Providers path="/w/a/projects/web/modules">
        <ProjectModulesPage />
      </Providers>,
    );
    const list = await screen.findByRole('list', { name: '모듈 목록' });
    expect(within(list).getByText('API')).toBeInTheDocument();
    expect(within(list).getByText('REST · MCP')).toBeInTheDocument();
    expect(within(list).getByText('쓰지 않음')).toBeInTheDocument();
    expect(within(list).getByRole('link', { name: '일감 3건' })).toHaveAttribute('href', '/w/a/projects/web?module=api');
    expect(screen.queryByRole('button', { name: '모듈 추가' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'API 위로' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'API 수정' })).not.toBeInTheDocument();
  });

  it('관리하는 사람은 더하고 · 순서를 바꾸고 · 쓰지 않음으로 두고 · 다시 쓴다', async () => {
    vi.mocked(projectApi.createModule).mockResolvedValue(mod('docs', '문서', 3));
    vi.mocked(projectApi.reorderModules).mockResolvedValue(moduleList);
    vi.mocked(projectApi.updateModule).mockResolvedValue(mod('api', 'API', 0, { active: false }));
    render(
      <Providers path="/w/a/projects/web/modules" permissions={['issue.read', 'project.manage']}>
        <ProjectModulesPage />
      </Providers>,
    );
    await screen.findByRole('list', { name: '모듈 목록' });
    expect(screen.getByRole('button', { name: 'API 위로' })).toBeDisabled();

    // 더하기 — key 모양을 먼저 알린다
    fireEvent.click(screen.getByRole('button', { name: '모듈 추가' }));
    const dialog = await screen.findByRole('dialog');
    fireEvent.change(within(dialog).getByRole('textbox', { name: 'key' }), { target: { value: 'Docs' } });
    fireEvent.change(within(dialog).getByRole('textbox', { name: '이름' }), { target: { value: '문서' } });
    fireEvent.click(within(dialog).getByRole('button', { name: '추가' }));
    expect(await within(dialog).findByText('영문 소문자로 시작하는 1~32자(소문자 · 숫자 · 하이픈)이고 하이픈으로 끝날 수 없습니다.')).toBeInTheDocument();
    expect(projectApi.createModule).not.toHaveBeenCalled();
    fireEvent.change(within(dialog).getByRole('textbox', { name: 'key' }), { target: { value: 'docs' } });
    fireEvent.click(within(dialog).getByRole('button', { name: '추가' }));
    await waitFor(() => expect(projectApi.createModule).toHaveBeenCalledWith('a', 'web', { key: 'docs', name: '문서', description: undefined }));

    // 순서 — 위 · 아래 단추(터치 · 키보드에서도)
    fireEvent.click(screen.getByRole('button', { name: '웹 프런트 위로' }));
    await waitFor(() => expect(projectApi.reorderModules).toHaveBeenCalledWith('a', 'web', ['web-frontend', 'api', 'infra']));

    // 쓰지 않음 · 다시 쓰기 — 읽은 판과 함께
    const apiRow = screen.getByText('REST · MCP').closest('li') as HTMLElement;
    fireEvent.click(within(apiRow).getByRole('button', { name: '쓰지 않음' }));
    await waitFor(() => expect(projectApi.updateModule).toHaveBeenCalledWith('a', 'web', 'api', { active: false, rowVersion: 0 }));
    const infraRow = screen.getByText('인프라').closest('li') as HTMLElement;
    fireEvent.click(within(infraRow).getByRole('button', { name: '다시 쓰기' }));
    await waitFor(() => expect(projectApi.updateModule).toHaveBeenCalledWith('a', 'web', 'infra', { active: true, rowVersion: 0 }));
  });

  it('수정 대화 상자는 key 를 바꾸지 못하게 끄고 이름 · 설명만 보낸다', async () => {
    vi.mocked(projectApi.updateModule).mockResolvedValue(mod('api', 'API 서버', 0));
    render(
      <Providers path="/w/a/projects/web/modules" permissions={['issue.read', 'project.manage']}>
        <ProjectModulesPage />
      </Providers>,
    );
    fireEvent.click(await screen.findByRole('button', { name: 'API 수정' }));
    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByRole('textbox', { name: 'key' })).toBeDisabled();
    fireEvent.change(within(dialog).getByRole('textbox', { name: '이름' }), { target: { value: 'API 서버' } });
    fireEvent.click(within(dialog).getByRole('button', { name: '저장' }));
    await waitFor(() =>
      expect(projectApi.updateModule).toHaveBeenCalledWith('a', 'web', 'api', { name: 'API 서버', description: 'REST · MCP', rowVersion: 0 }),
    );
  });
});
