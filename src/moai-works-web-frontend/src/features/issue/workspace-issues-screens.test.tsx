import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import type { ReactNode } from 'react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import '@/shared/i18n/i18n';
import { ProjectProvider } from '@/features/project/project-context';
import { WorkspaceProvider } from '@/features/workspace/workspace-context';
import { issueApi, issueTemplateApi, projectApi } from '@/shared/api/endpoints';
import type * as Endpoints from '@/shared/api/endpoints';
import { ApiError } from '@/shared/api/http';
import type { IssueDetail, IssueJournal, IssueSummary, PermissionCode, ProjectDetail, ProjectSummary, WorkspaceDetail } from '@/shared/api/types';
import { ConfirmProvider } from '@/shared/ui/ConfirmDialog';
import { AppearanceProvider } from '@/theme/AppearanceProvider';

import { IssueEditPage } from './IssueEditPage';
import { IssueHistory } from './IssueHistory';
import { IssueLayout } from './IssueLayout';
import { IssueList } from './IssueList';
import { LegacyIssueRedirect, legacyTarget } from './LegacyIssueRedirect';

/**
 * 워크스페이스 단위 일감 번호(2026-11-24 사용자: "워크스페이스 단위로 유니크한 번호여야해" · "레드마인을 생각하면 돼") —
 * 정본 주소 /w/:slug/issues/:number 의 틀 · 예전 프로젝트 주소 돌리기 · 워크스페이스 일감 목록 · 다른 프로젝트로 옮기기 · 이력의 말.
 * 서버 호출만 가짜로 두고 화면 · 주소 · 말은 그대로.
 */
vi.mock('@/shared/api/endpoints', async (importOriginal) => {
  const actual = await importOriginal<typeof Endpoints>();
  return {
    ...actual,
    issueApi: {
      ...actual.issueApi,
      detail: vi.fn(),
      update: vi.fn(),
      types: vi.fn(),
      resolveLegacy: vi.fn(),
      workspaceList: vi.fn(),
      workspaceTypes: vi.fn(),
      workspaceAgentSessions: vi.fn(),
      workspaceModules: vi.fn(),
      list: vi.fn(),
    },
    issueTemplateApi: { ...actual.issueTemplateApi, project: vi.fn() },
    projectApi: { ...actual.projectApi, detail: vi.fn(), list: vi.fn(), modules: vi.fn() },
  };
});

vi.mock('@/features/auth/session', () => ({
  useMe: () => ({ id: 'aaaaaaaa-0000-0000-0000-000000000001', username: 'kim', displayName: '김철수' }),
}));

const ME = 'aaaaaaaa-0000-0000-0000-000000000001';
const workspace = { slug: 'a', name: 'A', myPermissions: ['workspace.read'] as PermissionCode[] } as unknown as WorkspaceDetail;

function projectDetail(key: string, name: string, permissions: PermissionCode[] = ['issue.read', 'issue.edit', 'issue.create']): ProjectDetail {
  return {
    id: `p-${key}`,
    key,
    name,
    description: null,
    visibility: 'WORKSPACE',
    status: 'ACTIVE',
    updatedAt: '2026-11-22T09:00:00',
    createdAt: '2026-11-01T09:00:00',
    archivedAt: null,
    issueCount: 1,
    myRoles: [],
    myPermissions: permissions,
    myActions: ['createIssue'],
  };
}

function projectSummary(key: string, name: string, canCreate: boolean): ProjectSummary {
  return {
    id: `p-${key}`,
    key,
    name,
    description: null,
    visibility: 'WORKSPACE',
    status: 'ACTIVE',
    updatedAt: '2026-11-22T09:00:00',
    myActions: canCreate ? ['createIssue'] : [],
  };
}

const issue: IssueDetail = {
  number: 57,
  project: { key: 'moai-works', name: '모아이 웍스' },
  type: 'bug',
  title: '번호가 프로젝트마다 겹친다',
  description: '같은 워크스페이스에 #1 이 여럿',
  status: 'IN_PROGRESS',
  manager: { id: ME, username: 'kim', displayName: '김철수' },
  assignee: null,
  parent: { number: 40, title: '일감 모델', status: 'IN_PROGRESS', type: 'feature', project: { key: 'moai-works', name: '모아이 웍스' } },
  dueDate: null,
  startDate: null,
  plannedEndDate: null,
  completedOn: null,
  closedOn: null,
  createdBy: { id: ME, username: 'kim', displayName: '김철수' },
  createdAt: '2026-11-24T09:00:00.001',
  updatedAt: '2026-11-24T09:00:00.001',
  rowVersion: 2,
  overdue: false,
  revisionNo: 2,
  estimatedHours: null,
  spentHours: 0,
  agentSession: null,
  modules: [{ key: 'api', name: 'API', active: true }],
  children: [{ number: 58, title: '이전', status: 'NEW', type: 'feature', project: { key: 'moai-works', name: '모아이 웍스' } }],
  links: [],
  journal: [],
  workLogs: [],
};

function summary(number: number, project: { key: string; name: string }): IssueSummary {
  return {
    number,
    project,
    type: 'bug',
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
    updatedAt: '2026-11-24T09:00:00.000',
    childCount: 0,
    overdue: false,
    estimatedHours: null,
    spentHours: 0,
    agentSession: null,
    modules: [],
  };
}

/** 지금 주소 — 돌린 곳 · 조건이 주소에 남는지 본다 */
function Where() {
  const { pathname, search, hash } = useLocation();
  return <output data-testid="where">{`${pathname}${search}${hash}`}</output>;
}

function Shell({ path, children, project }: { path: string; children: ReactNode; project?: ProjectDetail }) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const body = (
    <MemoryRouter initialEntries={[path]}>
      {children}
      <Where />
    </MemoryRouter>
  );
  return (
    <AppearanceProvider>
      <QueryClientProvider client={client}>
        <ConfirmProvider>
          <WorkspaceProvider workspace={workspace}>{project ? <ProjectProvider project={project}>{body}</ProjectProvider> : body}</WorkspaceProvider>
        </ConfirmProvider>
      </QueryClientProvider>
    </AppearanceProvider>
  );
}

beforeEach(() => {
  vi.mocked(issueApi.detail).mockResolvedValue(issue);
  vi.mocked(issueApi.types).mockResolvedValue([
    { key: 'bug', name: '버그', description: null, active: true },
    { key: 'feature', name: '새기능', description: null, active: true },
  ]);
  vi.mocked(issueApi.workspaceTypes).mockResolvedValue([{ key: 'bug', name: '버그', description: null, active: true }]);
  vi.mocked(issueApi.workspaceAgentSessions).mockResolvedValue([]);
  vi.mocked(issueApi.workspaceModules).mockResolvedValue([{ key: 'api', name: 'API' }]);
  vi.mocked(issueTemplateApi.project).mockResolvedValue([]);
  vi.mocked(projectApi.detail).mockImplementation(async (_slug, key) =>
    key === 'moai-wiki' ? projectDetail('moai-wiki', '모아이 위키') : projectDetail('moai-works', '모아이 웍스'),
  );
  vi.mocked(projectApi.list).mockResolvedValue([
    projectSummary('moai-works', '모아이 웍스', true),
    projectSummary('moai-wiki', '모아이 위키', true),
    projectSummary('sec', '보안 점검', false),
  ]);
  vi.mocked(projectApi.modules).mockResolvedValue([]);
});

afterEach(() => {
  vi.clearAllMocks();
});

describe('정본 주소 /w/:slug/issues/:number', () => {
  it('일감을 읽어 그 일감의 지금 프로젝트로 프로젝트 틀(메뉴 한 줄)을 그린다', async () => {
    render(
      <Shell path="/w/a/issues/57">
        <Routes>
          <Route path="/w/:slug/issues/:number" element={<IssueLayout />}>
            <Route index element={<div>일감 보기 자리</div>} />
          </Route>
        </Routes>
      </Shell>,
    );
    expect(await screen.findByText('일감 보기 자리')).toBeInTheDocument();
    expect(issueApi.detail).toHaveBeenCalledWith('a', 57);
    expect(projectApi.detail).toHaveBeenCalledWith('a', 'moai-works');
    const nav = screen.getByRole('navigation', { name: '프로젝트 메뉴' });
    expect(within(nav).getByRole('link', { name: '모아이 웍스' })).toHaveAttribute('href', '/w/a/projects/moai-works');
  });

  it('없거나 읽을 수 없는 일감(404)은 "찾을 수 없습니다"와 워크스페이스 일감 목록으로 가는 단추', async () => {
    vi.mocked(issueApi.detail).mockRejectedValue(new ApiError(404, 'ISSUE_NOT_FOUND', '일감을 찾을 수 없습니다: #999'));
    render(
      <Shell path="/w/a/issues/999">
        <Routes>
          <Route path="/w/:slug/issues/:number" element={<IssueLayout />} />
        </Routes>
      </Shell>,
    );
    expect(await screen.findByText('일감을 찾을 수 없습니다')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '일감 목록으로' })).toHaveAttribute('href', '/w/a/issues');
    expect(projectApi.detail).not.toHaveBeenCalled();
  });
});

describe('예전 프로젝트 주소 → 정본 주소', () => {
  it('꼬리(/edit · /history)와 주소 값을 옮긴다', () => {
    expect(legacyTarget('a', 57, '/w/a/projects/moai-works/issues/12', '?tab=comments', '#c1')).toBe('/w/a/issues/57?tab=comments#c1');
    expect(legacyTarget('a', 57, '/w/a/projects/moai-works/issues/12/edit', '', '')).toBe('/w/a/issues/57/edit');
    expect(legacyTarget('a', 57, '/w/a/projects/moai-works/issues/12/history', '?rev=2&from=0', '')).toBe('/w/a/issues/57?rev=2&from=0&tab=revisions');
  });

  it('서버에 옛 번호의 지금 번호를 묻고 정본 주소로 돌린다', async () => {
    vi.mocked(issueApi.resolveLegacy).mockResolvedValue({ number: 57 });
    render(
      <Shell path="/w/a/projects/moai-works/issues/12/edit?x=1">
        <Routes>
          <Route path="/w/:slug/projects/:projectKey/issues/:number/*" element={<LegacyIssueRedirect />} />
          <Route path="/w/:slug/issues/:number/*" element={<div>정본</div>} />
        </Routes>
      </Shell>,
    );
    expect(await screen.findByText('정본')).toBeInTheDocument();
    expect(issueApi.resolveLegacy).toHaveBeenCalledWith('a', 'moai-works', 12);
    expect(screen.getByTestId('where')).toHaveTextContent('/w/a/issues/57/edit?x=1');
  });

  it('옛 번호를 찾지 못하면(404) "찾을 수 없습니다"', async () => {
    vi.mocked(issueApi.resolveLegacy).mockRejectedValue(new ApiError(404, 'ISSUE_NOT_FOUND', '일감을 찾을 수 없습니다'));
    render(
      <Shell path="/w/a/projects/moai-works/issues/12">
        <Routes>
          <Route path="/w/:slug/projects/:projectKey/issues/:number/*" element={<LegacyIssueRedirect />} />
        </Routes>
      </Shell>,
    );
    expect(await screen.findByText('일감을 찾을 수 없습니다')).toBeInTheDocument();
  });
});

describe('워크스페이스 일감 목록', () => {
  beforeEach(() => {
    vi.mocked(issueApi.workspaceList).mockResolvedValue({
      items: [summary(57, { key: 'moai-works', name: '모아이 웍스' }), summary(12, { key: 'moai-wiki', name: '모아이 위키' })],
      total: 2,
      page: 0,
      size: 50,
      totalPages: 1,
      hasNext: false,
    });
  });

  it('읽을 수 있는 프로젝트 모두에서 묻고(프로젝트 조건은 주소 그대로), 줄마다 프로젝트를 보인다', async () => {
    render(
      <Shell path="/w/a/issues?project=moai-works&project=moai-wiki">
        <Routes>
          <Route path="/w/:slug/issues" element={<IssueList slug="a" />} />
        </Routes>
      </Shell>,
    );
    expect(await screen.findByText('일감 57')).toBeInTheDocument();
    expect(issueApi.workspaceList).toHaveBeenCalledWith('a', expect.objectContaining({ project: ['moai-works', 'moai-wiki'] }));
    expect(issueApi.list).not.toHaveBeenCalled();
    expect(screen.getAllByText('모아이 위키').length).toBeGreaterThan(0);
    // 프로젝트 거르기 칸 — 고른 프로젝트 이름을 보인다
    expect(screen.getByRole('combobox', { name: '프로젝트' })).toHaveTextContent('모아이 웍스, 모아이 위키');
    // 일감 링크는 정본 주소
    expect(screen.getByRole('link', { name: /일감 12/ })).toHaveAttribute('href', '/w/a/issues/12');
  });

  it('프로젝트 거르기를 바꾸면 주소에 남기고 api 로 보낸다', async () => {
    render(
      <Shell path="/w/a/issues">
        <Routes>
          <Route path="/w/:slug/issues" element={<IssueList slug="a" />} />
        </Routes>
      </Shell>,
    );
    fireEvent.mouseDown(await screen.findByRole('combobox', { name: '프로젝트' }));
    const listbox = await screen.findByRole('listbox');
    fireEvent.click(within(listbox).getByRole('option', { name: '모아이 위키' }));
    await waitFor(() => expect(screen.getByTestId('where')).toHaveTextContent('/w/a/issues?project=moai-wiki'));
    await waitFor(() => expect(issueApi.workspaceList).toHaveBeenLastCalledWith('a', expect.objectContaining({ project: ['moai-wiki'] })));
  });

  it('찾는 글이 #번호면 그 일감으로 바로 가는 링크를 보인다', async () => {
    render(
      <Shell path="/w/a/issues?q=%23123">
        <Routes>
          <Route path="/w/:slug/issues" element={<IssueList slug="a" />} />
        </Routes>
      </Shell>,
    );
    const jump = await screen.findByRole('link', { name: '#123 바로 가기' });
    expect(jump).toHaveAttribute('href', '/w/a/issues/123');
    expect(issueApi.workspaceList).toHaveBeenCalledWith('a', expect.objectContaining({ q: `#${123}` }));
  });
});

describe('다른 프로젝트로 옮기기 (수정 화면의 프로젝트 칸)', () => {
  function renderEdit() {
    return render(
      <Shell path="/w/a/issues/57/edit" project={projectDetail('moai-works', '모아이 웍스')}>
        <Routes>
          <Route path="/w/:slug/issues/:number/edit" element={<IssueEditPage />} />
          <Route path="/w/:slug/issues/:number" element={<div>보기</div>} />
        </Routes>
      </Shell>,
    );
  }

  it('일감을 만들 수 있는 프로젝트만 고를 수 있고, 고르면 확인을 받은 뒤 project 를 보낸다', async () => {
    vi.mocked(issueApi.update).mockResolvedValue({ ...issue, project: { key: 'moai-wiki', name: '모아이 위키' }, parent: null, modules: [] });
    renderEdit();
    const select = await screen.findByRole('combobox', { name: '프로젝트' });
    expect(select).toHaveTextContent('모아이 웍스');
    fireEvent.mouseDown(select);
    const listbox = await screen.findByRole('listbox');
    // 만들기 권한이 없는 프로젝트(보안 점검)는 없다
    expect(within(listbox).queryByRole('option', { name: /보안 점검/ })).not.toBeInTheDocument();
    fireEvent.click(within(listbox).getByRole('option', { name: /모아이 위키/ }));

    // 옮기는 동안 상위 일감 · 관련 모듈은 꺼 두고 이유를 보인다
    expect(await screen.findByText('저장하면 모아이 위키(으)로 옮깁니다. 번호는 그대로입니다.')).toBeInTheDocument();
    expect(screen.getByRole('combobox', { name: '관련 모듈' })).toHaveAttribute('aria-disabled', 'true');

    fireEvent.click(screen.getByRole('button', { name: '저장' }));
    const dialog = await screen.findByRole('dialog', { name: '다른 프로젝트로 옮길까요?' });
    expect(within(dialog).getByText('#57 일감을 모아이 웍스에서 모아이 위키(으)로 옮깁니다.')).toBeInTheDocument();
    expect(within(dialog).getByText('하위 일감 1개(그 아래 일감까지)도 함께 옮겨집니다.')).toBeInTheDocument();
    expect(within(dialog).getByText(/상위 일감 #40/)).toBeInTheDocument();
    expect(within(dialog).getByText(/같은 key 로 쓰는 모듈/)).toBeInTheDocument();
    expect(issueApi.update).not.toHaveBeenCalled();

    fireEvent.click(within(dialog).getByRole('button', { name: '옮기고 저장' }));
    await waitFor(() => expect(issueApi.update).toHaveBeenCalledWith('a', 57, { rowVersion: 2, project: 'moai-wiki' }));
    expect(await screen.findByText('보기')).toBeInTheDocument();
  });

  it('옮길 수 있는 다른 프로젝트가 없으면 프로젝트 칸을 꺼 두고 이유를 보인다', async () => {
    vi.mocked(projectApi.list).mockResolvedValue([projectSummary('moai-works', '모아이 웍스', true), projectSummary('sec', '보안 점검', false)]);
    renderEdit();
    await waitFor(() => expect(screen.getByText(/옮길 수 있는 다른 프로젝트가 없습니다/)).toBeInTheDocument());
    expect(screen.getByRole('combobox', { name: '프로젝트' })).toHaveAttribute('aria-disabled', 'true');
  });

  it('확인에서 그만두면 보내지 않는다', async () => {
    renderEdit();
    fireEvent.mouseDown(await screen.findByRole('combobox', { name: '프로젝트' }));
    fireEvent.click(within(await screen.findByRole('listbox')).getByRole('option', { name: /모아이 위키/ }));
    fireEvent.click(screen.getByRole('button', { name: '저장' }));
    const dialog = await screen.findByRole('dialog', { name: '다른 프로젝트로 옮길까요?' });
    fireEvent.click(within(dialog).getByRole('button', { name: '취소' }));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(issueApi.update).not.toHaveBeenCalled();
  });

  it('옮길 프로젝트에 권한이 없으면(403) 프로젝트 칸에 서버의 말을 보인다', async () => {
    vi.mocked(issueApi.update).mockRejectedValue(new ApiError(403, 'PERMISSION_REQUIRED', '옮길 프로젝트에 일감을 만들 권한(issue.create)이 필요합니다.'));
    renderEdit();
    fireEvent.mouseDown(await screen.findByRole('combobox', { name: '프로젝트' }));
    fireEvent.click(within(await screen.findByRole('listbox')).getByRole('option', { name: /모아이 위키/ }));
    fireEvent.click(screen.getByRole('button', { name: '저장' }));
    fireEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: '옮기고 저장' }));
    expect(await screen.findByText('옮길 프로젝트에 일감을 만들 권한(issue.create)이 필요합니다.')).toBeInTheDocument();
  });
});

describe('이력의 옮기기 · 번호 다시 매기기', () => {
  function line(over: Partial<IssueJournal>): IssueJournal {
    return {
      id: 'j',
      actorType: 'USER',
      actorId: ME,
      actorName: '김철수',
      via: 'WEB',
      createdAt: '2026-11-24T09:00:00.000',
      notes: null,
      changes: [],
      revisionNo: null,
      rowVersion: null,
      editedAt: null,
      deleted: false,
      deletedAt: null,
      deletedBy: null,
      ...over,
    };
  }

  it('옮긴 줄은 "프로젝트: A → B", 번호를 다시 매긴 줄은 서버(시스템)가 한 일로 보인다', () => {
    const journal = [
      line({
        id: 'renumber',
        actorType: 'SERVICE',
        actorId: null,
        actorName: 'moai-works',
        via: 'SYSTEM',
        changes: [{ field: 'number', before: { project: 'moai-works', number: 12 }, after: { number: 57 } }],
      }),
      line({
        id: 'move',
        revisionNo: 3,
        changes: [
          { field: 'project', before: { key: 'moai-works', name: '모아이 웍스' }, after: { key: 'moai-wiki', name: '모아이 위키' } },
          { field: 'parent', before: 40, after: null },
        ],
      }),
    ];
    render(
      <Shell path="/w/a/issues/57">
        <IssueHistory issue={{ ...issue, journal }} issuePath={(n) => `/w/a/issues/${n}`} revisionPath={(no) => `/w/a/issues/57?tab=revisions&rev=${no}`} />
      </Shell>,
    );
    expect(screen.getByText('번호가 바뀌었습니다: moai-works #12 → #57 (워크스페이스 단위 번호)')).toBeInTheDocument();
    expect(screen.getByText('시스템')).toBeInTheDocument();
    expect(screen.getByText('프로젝트: 모아이 웍스 → 모아이 위키')).toBeInTheDocument();
    expect(screen.getByText('상위 일감: #40 → 없음')).toBeInTheDocument();
  });
});

describe('수정 화면의 상태 칸 — 보류 · 취소는 바꾼 이유가 필수 (일감 #152)', () => {
  function renderEdit() {
    return render(
      <Shell path="/w/a/issues/57/edit" project={projectDetail('moai-works', '모아이 웍스')}>
        <Routes>
          <Route path="/w/:slug/issues/:number/edit" element={<IssueEditPage />} />
          <Route path="/w/:slug/issues/:number" element={<div>보기</div>} />
        </Routes>
      </Shell>,
    );
  }

  async function pickStatus(name: string) {
    fireEvent.mouseDown(await screen.findByRole('combobox', { name: '상태' }));
    const listbox = await screen.findByRole('listbox');
    fireEvent.click(within(listbox).getByRole('option', { name }));
  }

  it('상태는 등록 · 요구사항 확인요청 · 요구사항 확인완료 · 진행 · 테스트 · 확인요청 · 확인완료 · 보류 · 완료 · 종료 · 취소 차례이고, 고른 상태의 뜻을 칸 아래에 보인다', async () => {
    renderEdit();
    const select = await screen.findByRole('combobox', { name: '상태' });
    // 지금 상태(진행)의 뜻
    expect(screen.getByText('하는 중')).toBeInTheDocument();
    fireEvent.mouseDown(select);
    const options = within(await screen.findByRole('listbox')).getAllByRole('option').map((o) => o.textContent);
    expect(options).toEqual(['등록', '요구사항 확인요청', '요구사항 확인완료', '진행', '테스트', '확인요청', '확인완료', '보류', '완료', '종료', '취소']);
    fireEvent.click(within(screen.getByRole('listbox')).getByRole('option', { name: '종료' }));
    expect(await screen.findByText('완료한 일을 사람이 확인해 닫음')).toBeInTheDocument();
  });

  it('보류로 바꾸면 바꾼 이유가 필수가 되고, 비우면 보내지 않으며, 적으면 상태와 함께 notes 로 보낸다', async () => {
    vi.mocked(issueApi.update).mockResolvedValue({ ...issue, status: 'ON_HOLD' });
    renderEdit();
    const notes = await screen.findByRole('textbox', { name: /바꾼 이유 \(선택\)/ });
    expect(notes).not.toBeRequired();
    await pickStatus('보류');
    expect(await screen.findByText('이 상태로 바꾸려면 아래 "바꾼 이유"에 까닭(확인요청이면 무엇을 확인할지)을 적어야 합니다.')).toBeInTheDocument();
    expect(screen.getByRole('textbox', { name: /바꾼 이유 \(필수\)/ })).toBeRequired();

    fireEvent.click(screen.getByRole('button', { name: '저장' }));
    expect(await screen.findByText('바꾸는 까닭(확인요청이면 무엇을 확인할지)을 적어 주십시오.')).toBeInTheDocument();
    expect(issueApi.update).not.toHaveBeenCalled();

    fireEvent.change(screen.getByRole('textbox', { name: /바꾼 이유/ }), { target: { value: '디자인 결정을 기다림' } });
    fireEvent.click(screen.getByRole('button', { name: '저장' }));
    await waitFor(() =>
      expect(issueApi.update).toHaveBeenCalledWith('a', 57, { rowVersion: 2, status: 'ON_HOLD', notes: '디자인 결정을 기다림' }),
    );
    expect(await screen.findByText('보기')).toBeInTheDocument();
  });

  it('서버가 까닭이 없다고 답하면(STATUS_REASON_REQUIRED) 바꾼 이유 칸에 보인다 · 다른 상태로 되돌리면 다시 선택 칸이다', async () => {
    vi.mocked(issueApi.update).mockRejectedValue(new ApiError(400, 'STATUS_REASON_REQUIRED', '취소로 바꾸려면 까닭을 넣으십시오.'));
    renderEdit();
    await pickStatus('취소');
    fireEvent.change(await screen.findByRole('textbox', { name: /바꾼 이유 \(필수\)/ }), { target: { value: '중복' } });
    fireEvent.click(screen.getByRole('button', { name: '저장' }));
    expect(await screen.findByText('취소로 바꾸려면 까닭을 넣으십시오.')).toBeInTheDocument();

    await pickStatus('완료');
    expect(await screen.findByRole('textbox', { name: /바꾼 이유 \(선택\)/ })).not.toBeRequired();
    expect(screen.queryByText('취소로 바꾸려면 까닭을 넣으십시오.')).not.toBeInTheDocument();
  });
});

describe('수정 화면의 상태 칸 — 관리자 확인 (일감 #272)', () => {
  function renderEdit(project: ProjectDetail) {
    return render(
      <Shell path="/w/a/issues/57/edit" project={project}>
        <Routes>
          <Route path="/w/:slug/issues/:number/edit" element={<IssueEditPage />} />
          <Route path="/w/:slug/issues/:number" element={<div>보기</div>} />
        </Routes>
      </Shell>,
    );
  }

  const someoneElse = { id: 'bbbbbbbb-0000-0000-0000-000000000002', username: 'lee', displayName: '이영희' };

  it('관리자가 아니면 확인완료 둘을 꺼 두고 누가 고를 수 있는지 적는다 — 확인요청은 고를 수 있고 바꾼 이유가 필수다', async () => {
    vi.mocked(issueApi.detail).mockResolvedValue({ ...issue, status: 'TESTING', manager: someoneElse });
    vi.mocked(issueApi.update).mockResolvedValue({ ...issue, status: 'REVIEW' });
    renderEdit(projectDetail('moai-works', '모아이 웍스'));
    fireEvent.mouseDown(await screen.findByRole('combobox', { name: '상태' }));
    const listbox = await screen.findByRole('listbox');
    for (const name of [/^요구사항 확인완료/, /^확인완료/]) {
      const option = within(listbox).getByRole('option', { name });
      expect(option).toHaveAttribute('aria-disabled', 'true');
      expect(option).toHaveTextContent('관리자(이영희) 또는 프로젝트 관리자만 고를 수 있습니다');
    }
    expect(within(listbox).getByRole('option', { name: '확인요청' })).not.toHaveAttribute('aria-disabled', 'true');
    fireEvent.click(within(listbox).getByRole('option', { name: '확인요청' }));
    // 고른 값은 이름만 보인다
    expect(await screen.findByRole('combobox', { name: '상태' })).toHaveTextContent(/^확인요청$/);
    expect(screen.getByRole('textbox', { name: /바꾼 이유 \(필수\)/ })).toBeRequired();
    fireEvent.change(screen.getByRole('textbox', { name: /바꾼 이유/ }), { target: { value: '잠금을 확인해 주십시오' } });
    fireEvent.click(screen.getByRole('button', { name: '저장' }));
    await waitFor(() =>
      expect(issueApi.update).toHaveBeenCalledWith('a', 57, { rowVersion: 2, status: 'REVIEW', notes: '잠금을 확인해 주십시오' }),
    );
  });

  it('관리자 · project.manage 는 확인완료를 고른다(짝인 확인완료는 까닭 없이)', async () => {
    vi.mocked(issueApi.detail).mockResolvedValue({ ...issue, status: 'REVIEW', manager: someoneElse });
    vi.mocked(issueApi.update).mockResolvedValue({ ...issue, status: 'CONFIRMED' });
    renderEdit(projectDetail('moai-works', '모아이 웍스', ['issue.read', 'issue.edit', 'issue.create', 'project.manage']));
    fireEvent.mouseDown(await screen.findByRole('combobox', { name: '상태' }));
    const listbox = await screen.findByRole('listbox');
    expect(within(listbox).getByRole('option', { name: '확인완료' })).not.toHaveAttribute('aria-disabled', 'true');
    fireEvent.click(within(listbox).getByRole('option', { name: '확인완료' }));
    expect(await screen.findByRole('textbox', { name: /바꾼 이유 \(선택\)/ })).not.toBeRequired();
    fireEvent.click(screen.getByRole('button', { name: '저장' }));
    await waitFor(() => expect(issueApi.update).toHaveBeenCalledWith('a', 57, { rowVersion: 2, status: 'CONFIRMED' }));
  });
});
