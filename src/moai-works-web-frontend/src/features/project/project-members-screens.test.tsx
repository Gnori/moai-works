import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import dayjs from 'dayjs';
import type { ReactNode } from 'react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import '@/shared/i18n/i18n';
import { WorkspaceProvider } from '@/features/workspace/workspace-context';
import { projectApi, workspaceApi } from '@/shared/api/endpoints';
import type * as Endpoints from '@/shared/api/endpoints';
import type { Member, PermissionCode, ProjectAgent, ProjectDetail, ProjectRoleHolder, WorkspaceDetail } from '@/shared/api/types';
import { ConfirmProvider } from '@/shared/ui/ConfirmDialog';
import { AppearanceProvider } from '@/theme/AppearanceProvider';

import { readIssueFilters } from '../issue/issue-filters';
import { ProjectProvider } from './project-context';
import { ProjectMembersPage } from './ProjectMembersPage';

/**
 * 프로젝트 멤버 화면의 두 가지 — 사람 · 에이전트 탭(수, 주소의 ?tab=agents)과 에이전트 줄(이름 칩 · 주인 · 설명 · 열린 일감 링크 · 모두 ·
 * 마지막 활동 · 쓰지 않음 · 자동 등록), 관리하는 사람의 더하기 · 수정 · 쓰지 않음 · 다시 쓰기(일감 #285). 서버 호출만 가짜로 둔다.
 */
vi.mock('@/shared/api/endpoints', async (importOriginal) => {
  const actual = await importOriginal<typeof Endpoints>();
  return {
    ...actual,
    projectApi: { ...actual.projectApi, roles: vi.fn(), agents: vi.fn(), createAgent: vi.fn(), updateAgent: vi.fn() },
    workspaceApi: { ...actual.workspaceApi, members: vi.fn() },
  };
});

const ME = 'aaaaaaaa-0000-0000-0000-000000000001';
const LEE = 'aaaaaaaa-0000-0000-0000-000000000002';

vi.mock('@/features/auth/session', () => ({
  useMe: () => ({ id: 'aaaaaaaa-0000-0000-0000-000000000001', username: 'kim', displayName: '김철수' }),
}));

function workspace(permissions: PermissionCode[] = ['member.read']): WorkspaceDetail {
  return { slug: 'a', name: 'A', myPermissions: permissions, myRoles: [], kind: 'TEAM' } as unknown as WorkspaceDetail;
}

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

const holders: ProjectRoleHolder[] = [
  { userId: ME, username: 'kim', displayName: '김철수', roles: ['project-admin'], workspaceOwner: true },
  { userId: LEE, username: 'lee', displayName: '이영희', roles: ['project-editor'], workspaceOwner: false },
];

const members: Member[] = [
  { userId: ME, username: 'kim', displayName: '김철수', email: 'kim@a.com', status: 'ACTIVE', joinedVia: 'CREATOR', joinedAt: '2026-11-01T09:00:00', roles: ['owner'] },
  { userId: LEE, username: 'lee', displayName: '이영희', email: 'lee@a.com', status: 'ACTIVE', joinedVia: 'INVITE', joinedAt: '2026-11-02T09:00:00', roles: ['member'] },
];

function agent(id: string, name: string, over: Partial<ProjectAgent> = {}): ProjectAgent {
  return {
    id,
    name,
    owner: { id: ME, username: 'kim', displayName: '김철수' },
    description: null,
    active: true,
    source: 'MANUAL',
    createdAt: '2026-11-20T09:00:00.000',
    openIssues: 0,
    totalIssues: 0,
    lastActivityAt: null,
    rowVersion: 0,
    ...over,
  };
}

const agentList: ProjectAgent[] = [
  agent('g1', 'moai-works', {
    source: 'AUTO',
    description: 'moai-works 저장소에서 일하는 세션',
    openIssues: 3,
    totalIssues: 7,
    lastActivityAt: dayjs().subtract(3, 'hour').toISOString(),
    rowVersion: 2,
  }),
  // REST 는 빈 값을 싣지 않는다 — 주인 · 마지막 활동이 없는 옛 에이전트
  { id: 'g2', name: 'old-session', active: false, source: 'AUTO', createdAt: '2026-11-01T09:00:00.000', openIssues: 0, totalIssues: 1, rowVersion: 4 },
];

/** 지금 주소의 검색 조건 — 탭이 주소에 남는지 본다 */
function Search() {
  return <output data-testid="search">{useLocation().search}</output>;
}

function Providers({ children, permissions = ['issue.read'], path = '/w/a/projects/web/members', wsPermissions }: {
  children: ReactNode;
  permissions?: PermissionCode[];
  path?: string;
  wsPermissions?: PermissionCode[];
}) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return (
    <AppearanceProvider>
      <QueryClientProvider client={client}>
        <ConfirmProvider>
          <WorkspaceProvider workspace={workspace(wsPermissions)}>
            <ProjectProvider project={project(permissions)}>
              <MemoryRouter initialEntries={[path]}>
                <Routes>
                  <Route
                    path="/w/:slug/projects/:projectKey/*"
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

beforeEach(() => {
  vi.mocked(projectApi.roles).mockResolvedValue(holders);
  vi.mocked(projectApi.agents).mockResolvedValue(agentList);
  vi.mocked(workspaceApi.members).mockResolvedValue(members);
});

afterEach(() => {
  vi.clearAllMocks();
});

describe('프로젝트 멤버 — 사람 · 에이전트 두 탭', () => {
  it('탭마다 수를 보이고, 에이전트 탭은 주소(?tab=agents)에 남는다', async () => {
    render(
      <Providers>
        <ProjectMembersPage />
      </Providers>,
    );
    const people = await screen.findByRole('tab', { name: '사람 2' });
    expect(people).toHaveAttribute('aria-selected', 'true');
    // 사람 탭은 오늘의 표 그대로
    expect(await screen.findByText('이영희')).toBeInTheDocument();
    const agents = await screen.findByRole('tab', { name: '에이전트 2' });
    fireEvent.click(agents);
    expect(screen.getByTestId('search')).toHaveTextContent('?tab=agents');
    expect(await screen.findByRole('list', { name: '에이전트 목록' })).toBeInTheDocument();
    // 에이전트는 주인의 권한으로 일한다는 안내
    expect(screen.getByText(/주인의 권한대로 일합니다/)).toBeInTheDocument();
    fireEvent.click(screen.getByRole('tab', { name: '사람 2' }));
    expect(screen.getByTestId('search')).toBeEmptyDOMElement();
  });

  it('에이전트 줄 — 이름 칩 · 주인 · 설명 · 열린 일감 링크(세션 + 열린 상태) · 모두 · 마지막 활동 · 쓰지 않음 · 자동 등록', async () => {
    render(
      <Providers path="/w/a/projects/web/members?tab=agents">
        <ProjectMembersPage />
      </Providers>,
    );
    const list = await screen.findByRole('list', { name: '에이전트 목록' });
    const [works, old] = within(list).getAllByRole('listitem') as [HTMLElement, HTMLElement];
    expect(within(works).getByText('moai-works')).toBeInTheDocument();
    expect(within(works).getByText('김철수')).toBeInTheDocument();
    expect(within(works).getByText('kim')).toBeInTheDocument();
    expect(within(works).getByText('moai-works 저장소에서 일하는 세션')).toBeInTheDocument();
    expect(within(works).getByText('자동 등록')).toBeInTheDocument();
    expect(within(works).queryByText('쓰지 않음', { selector: '.MuiChip-label' })).not.toBeInTheDocument();
    expect(within(works).getByText('모두 7건')).toBeInTheDocument();
    expect(within(works).getByText('마지막 활동 3시간 전')).toBeInTheDocument();
    const link = within(works).getByRole('link', { name: '열린 일감 3건' });
    const href = link.getAttribute('href') ?? '';
    expect(href.startsWith('/w/a/projects/web?')).toBe(true);
    // 일감 목록이 그 조건을 그대로 읽는다 — 세션 이름과 열린 상태 여덟(완료 · 종료 · 취소 빼고)
    const filters = readIssueFilters(new URLSearchParams(href.split('?')[1]));
    expect(filters.agentSession).toBe('moai-works');
    expect(filters.status).toEqual(['NEW', 'REQUIREMENT_REVIEW', 'REQUIREMENT_CONFIRMED', 'IN_PROGRESS', 'TESTING', 'REVIEW', 'CONFIRMED', 'ON_HOLD']);

    expect(within(old).getByText('old-session')).toBeInTheDocument();
    expect(within(old).getByText('쓰지 않음', { selector: '.MuiChip-label' })).toBeInTheDocument();
    expect(within(old).getByText('주인 모름')).toBeInTheDocument();
    expect(within(old).getByText('아직 일감이 없습니다')).toBeInTheDocument();
    expect(within(old).getByRole('link', { name: '열린 일감 0건' })).toBeInTheDocument();
    expect(screen.getByText('에이전트 2개 · 쓰는 것 1개')).toBeInTheDocument();
  });

  it('독자에게는 보기만 — 에이전트 추가 · 수정 · 쓰지 않음 단추가 없다', async () => {
    render(
      <Providers path="/w/a/projects/web/members?tab=agents">
        <ProjectMembersPage />
      </Providers>,
    );
    await screen.findByRole('list', { name: '에이전트 목록' });
    expect(screen.queryByRole('button', { name: '에이전트 추가' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'moai-works 수정' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '쓰지 않음' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '다시 쓰기' })).not.toBeInTheDocument();
  });

  it('비었으면 MCP 로 일하면 저절로 나타난다고 알린다', async () => {
    vi.mocked(projectApi.agents).mockResolvedValue([]);
    render(
      <Providers path="/w/a/projects/web/members?tab=agents">
        <ProjectMembersPage />
      </Providers>,
    );
    expect(await screen.findByText('아직 에이전트가 없습니다')).toBeInTheDocument();
    expect(screen.getByText(/MCP 로 이 프로젝트의 일감에 세션 이름\(agentSession\)을 넣고 일하면 여기에 저절로 나타납니다\.$/)).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: '에이전트 0' })).toBeInTheDocument();
  });

  it('관리하는 사람은 더한다 — 주인은 처음에 나, 이름 · 설명을 보낸다', async () => {
    vi.mocked(projectApi.createAgent).mockResolvedValue(agent('g3', 'moai-wiki'));
    render(
      <Providers path="/w/a/projects/web/members?tab=agents" permissions={['issue.read', 'project.manage']}>
        <ProjectMembersPage />
      </Providers>,
    );
    await screen.findByRole('list', { name: '에이전트 목록' });
    // 머리의 단추는 탭에 맞춘다 — 에이전트 탭에서는 사람 추가가 아니다
    expect(screen.queryByRole('button', { name: '멤버 추가' })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '에이전트 추가' }));
    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByRole('combobox', { name: '주인' })).toHaveValue('김철수');
    // 이름이 비면 막는다
    fireEvent.click(within(dialog).getByRole('button', { name: '추가' }));
    expect(await within(dialog).findByText('세션 이름을 적으십시오.')).toBeInTheDocument();
    expect(projectApi.createAgent).not.toHaveBeenCalled();
    fireEvent.change(within(dialog).getByRole('textbox', { name: '세션 이름' }), { target: { value: ' moai-wiki ' } });
    fireEvent.change(within(dialog).getByRole('textbox', { name: '설명 (선택)' }), { target: { value: '위키 세션' } });
    fireEvent.click(within(dialog).getByRole('button', { name: '추가' }));
    await waitFor(() =>
      expect(projectApi.createAgent).toHaveBeenCalledWith('a', 'web', { name: 'moai-wiki', owner: 'kim', description: '위키 세션' }),
    );
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  });

  it('같은 이름이면 이름 칸에 알린다', async () => {
    const { ApiError } = await import('@/shared/api/http');
    vi.mocked(projectApi.createAgent).mockRejectedValue(new ApiError(409, 'AGENT_NAME_TAKEN', '같은 이름'));
    render(
      <Providers path="/w/a/projects/web/members?tab=agents" permissions={['issue.read', 'project.manage']}>
        <ProjectMembersPage />
      </Providers>,
    );
    await screen.findByRole('list', { name: '에이전트 목록' });
    fireEvent.click(screen.getByRole('button', { name: '에이전트 추가' }));
    const dialog = await screen.findByRole('dialog');
    fireEvent.change(within(dialog).getByRole('textbox', { name: '세션 이름' }), { target: { value: 'MOAI-WORKS' } });
    fireEvent.click(within(dialog).getByRole('button', { name: '추가' }));
    expect(await within(dialog).findByText('이 프로젝트에 같은 이름(대소문자 무시)의 에이전트가 있습니다.')).toBeInTheDocument();
  });

  it('수정은 이름을 잠그고 바뀐 설명 · 주인만 읽은 판과 함께 보낸다', async () => {
    vi.mocked(projectApi.updateAgent).mockResolvedValue(agent('g1', 'moai-works'));
    render(
      <Providers path="/w/a/projects/web/members?tab=agents" permissions={['issue.read', 'project.manage']}>
        <ProjectMembersPage />
      </Providers>,
    );
    await screen.findByRole('list', { name: '에이전트 목록' });
    fireEvent.click(screen.getByRole('button', { name: 'moai-works 수정' }));
    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByRole('textbox', { name: '세션 이름' })).toBeDisabled();
    expect(within(dialog).getByRole('button', { name: '저장' })).toBeDisabled();
    fireEvent.change(within(dialog).getByRole('textbox', { name: '설명 (선택)' }), { target: { value: '' } });
    // 주인을 이영희로
    await waitFor(() => expect(workspaceApi.members).toHaveBeenCalled());
    fireEvent.mouseDown(within(dialog).getByRole('combobox', { name: '주인' }));
    fireEvent.click(await screen.findByRole('option', { name: /이영희/ }));
    fireEvent.click(within(dialog).getByRole('button', { name: '저장' }));
    await waitFor(() =>
      expect(projectApi.updateAgent).toHaveBeenCalledWith('a', 'web', 'g1', { rowVersion: 2, description: null, owner: 'lee' }),
    );
  });

  it('쓰지 않음 · 다시 쓰기는 읽은 판과 함께 active 만 보낸다', async () => {
    vi.mocked(projectApi.updateAgent).mockResolvedValue(agent('g1', 'moai-works', { active: false }));
    render(
      <Providers path="/w/a/projects/web/members?tab=agents" permissions={['issue.read', 'project.manage']}>
        <ProjectMembersPage />
      </Providers>,
    );
    const list = await screen.findByRole('list', { name: '에이전트 목록' });
    const [works, old] = within(list).getAllByRole('listitem') as [HTMLElement, HTMLElement];
    fireEvent.click(within(works).getByRole('button', { name: '쓰지 않음' }));
    await waitFor(() => expect(projectApi.updateAgent).toHaveBeenCalledWith('a', 'web', 'g1', { active: false, rowVersion: 2 }));
    await waitFor(() => expect(within(old).getByRole('button', { name: '다시 쓰기' })).toBeEnabled());
    fireEvent.click(within(old).getByRole('button', { name: '다시 쓰기' }));
    await waitFor(() => expect(projectApi.updateAgent).toHaveBeenCalledWith('a', 'web', 'g2', { active: true, rowVersion: 4 }));
  });

  it('워크스페이스 멤버를 볼 수 없으면 주인은 프로젝트 역할을 받은 사람 가운데서 고른다', async () => {
    render(
      <Providers path="/w/a/projects/web/members?tab=agents" permissions={['issue.read', 'project.manage']} wsPermissions={[]}>
        <ProjectMembersPage />
      </Providers>,
    );
    await screen.findByRole('list', { name: '에이전트 목록' });
    fireEvent.click(screen.getByRole('button', { name: '에이전트 추가' }));
    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByText('워크스페이스 멤버 목록을 볼 수 없어, 이 프로젝트의 역할을 받은 사람 가운데서 고릅니다.')).toBeInTheDocument();
    fireEvent.mouseDown(within(dialog).getByRole('combobox', { name: '주인' }));
    expect(await screen.findByRole('option', { name: /이영희/ })).toBeInTheDocument();
    expect(workspaceApi.members).not.toHaveBeenCalled();
  });
});
