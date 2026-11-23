import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import type { ReactNode } from 'react';
import { MemoryRouter, Route, Routes } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ProjectProvider } from '@/features/project/project-context';
import { ProjectTemplatesPage } from '@/features/project/ProjectTemplatesPage';
import { WorkspaceProvider } from '@/features/workspace/workspace-context';
import { issueTemplateApi } from '@/shared/api/endpoints';
import type * as Endpoints from '@/shared/api/endpoints';
import { ApiError } from '@/shared/api/http';
import type { IssueTemplate, PermissionCode, ProjectDetail, WorkspaceDetail } from '@/shared/api/types';
import { ConfirmProvider } from '@/shared/ui/ConfirmDialog';
import { AppearanceProvider } from '@/theme/AppearanceProvider';

import { emptyIssueForm, type IssueFormValues } from './issue-form';
import { IssueForm } from './IssueForm';
import { IssueTemplateManager } from './IssueTemplateManager';
import { useWorkspaceTemplatesQuery } from './queries';

/**
 * 유형별 일감 템플릿 화면(FR-ISS-020) — 워크스페이스 설정의 템플릿 칸 · 프로젝트의 템플릿 화면 · 새 일감 / 고치기의 채우기 · 빠진 항목 경고.
 * 서버 호출(issueTemplateApi)만 가짜로 두고 화면 · 말은 그대로. 편집기는 시험용 편집기(editor/__mocks__)다.
 */
vi.mock('@/shared/api/endpoints', async (importOriginal) => {
  const actual = await importOriginal<typeof Endpoints>();
  return {
    ...actual,
    issueTemplateApi: { workspace: vi.fn(), setWorkspace: vi.fn(), resetWorkspace: vi.fn(), project: vi.fn(), setProject: vi.fn(), resetProject: vi.fn() },
  };
});

const FEATURE = '## 배경\n\n## 하려는 것\n\n## 수용 기준\n- [ ] \n\n## 범위 밖 (선택)\n';
const BUG = '## 현상\n\n## 재현 순서\n1. \n\n## 기대한 동작\n';
const sections = (pairs: [string, boolean][]) => pairs.map(([title, optional]) => ({ title, optional }));

const feature = (over: Partial<IssueTemplate> = {}): IssueTemplate => ({
  type: 'feature',
  typeName: '새기능',
  body: FEATURE,
  source: 'BUILTIN',
  inherited: false,
  sections: sections([['배경', false], ['하려는 것', false], ['수용 기준', false], ['범위 밖', true]]),
  updatedBy: null,
  updatedAt: null,
  rowVersion: null,
  ...over,
});
const bug = (over: Partial<IssueTemplate> = {}): IssueTemplate => ({
  type: 'bug',
  typeName: '버그',
  body: BUG,
  source: 'WORKSPACE',
  inherited: false,
  sections: sections([['현상', false], ['재현 순서', false], ['기대한 동작', false]]),
  updatedBy: { id: 'u1', username: 'kim', displayName: '김철수' },
  updatedAt: '2026-11-23T08:20:11.123',
  rowVersion: 3,
  ...over,
});

const workspace = { slug: 'a', name: 'A', myPermissions: ['workspace.settings.manage'] as PermissionCode[] } as unknown as WorkspaceDetail;

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

function Providers({ children, permissions = ['issue.read'], path = '/w/a/projects/web/templates' }: {
  children: ReactNode;
  permissions?: PermissionCode[];
  path?: string;
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
                  <Route path="/w/:slug/projects/:projectKey/*" element={children} />
                </Routes>
              </MemoryRouter>
            </ProjectProvider>
          </WorkspaceProvider>
        </ConfirmProvider>
      </QueryClientProvider>
    </AppearanceProvider>
  );
}

/** 워크스페이스 설정의 템플릿 칸과 같은 연결 */
function WorkspaceTemplatesHarness({ canManage = true }: { canManage?: boolean }) {
  const query = useWorkspaceTemplatesQuery('a');
  return (
    <IssueTemplateManager
      scope="workspace"
      query={query}
      canManage={canManage}
      onSave={(type, body, rowVersion) => issueTemplateApi.setWorkspace('a', type, body, rowVersion)}
      onReset={(type) => issueTemplateApi.resetWorkspace('a', type)}
    />
  );
}

/** 편집 칸에 글을 붙여넣는다 (시험용 편집기는 붙여넣은 글을 끝에 붙인다) */
function pasteText(target: Element, text: string) {
  const event = new Event('paste', { bubbles: true, cancelable: true });
  Object.defineProperty(event, 'clipboardData', { value: { files: [], types: ['text/plain'], getData: () => text } });
  act(() => {
    target.dispatchEvent(event);
  });
}

beforeEach(() => {
  vi.mocked(issueTemplateApi.workspace).mockResolvedValue([feature(), bug()]);
  vi.mocked(issueTemplateApi.project).mockResolvedValue([
    feature({ source: 'WORKSPACE', inherited: true }),
    bug({ source: 'PROJECT', inherited: false, rowVersion: 1 }),
  ]);
});

afterEach(() => {
  vi.clearAllMocks();
});

describe('워크스페이스 기본 템플릿', () => {
  it('유형마다 탭 · 출처 · 항목을 보이고, 처음 템플릿은 되돌릴 것이 없다', async () => {
    render(
      <Providers>
        <WorkspaceTemplatesHarness />
      </Providers>,
    );
    const tabs = await screen.findByRole('tablist', { name: '유형' });
    expect(within(tabs).getAllByRole('tab').map((tab) => tab.textContent)).toEqual(['새기능', '버그']);
    expect(screen.getByText('처음 템플릿')).toBeInTheDocument();
    expect(screen.getByText('항목: 배경 · 하려는 것 · 수용 기준 · 범위 밖(선택)')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '처음 템플릿으로 되돌리기' })).toBeDisabled();
    // 고치지 않았으면 저장할 것이 없다
    expect(screen.getByRole('button', { name: '템플릿 저장' })).toBeDisabled();
  });

  it('고치면 저장할 수 있고, 처음 정할 때는 판을 보내지 않는다', async () => {
    vi.mocked(issueTemplateApi.setWorkspace).mockResolvedValue(feature({ source: 'WORKSPACE', body: `${FEATURE}더함`, rowVersion: 0 }));
    render(
      <Providers>
        <WorkspaceTemplatesHarness />
      </Providers>,
    );
    pasteText(await screen.findByTestId('rich-editor'), '더함');
    // 친 순간 저장 단추가 켜진다(글은 마지막 입력 뒤 조금 늦게 올라온다)
    expect(screen.getByRole('button', { name: '템플릿 저장' })).toBeEnabled();
    // 글이 올라오면 탭에 고치는 중 표시
    expect(await screen.findByRole('tab', { name: '새기능 •' })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '템플릿 저장' }));
    await waitFor(() => expect(issueTemplateApi.setWorkspace).toHaveBeenCalledWith('a', 'feature', expect.stringContaining('더함'), null));
  });

  it('친 바로 다음(기다리지 않고) 탭을 옮겨도 고친 글이 남는다 — 편집기가 사라질 때 남은 글을 올린다', async () => {
    render(
      <Providers>
        <WorkspaceTemplatesHarness />
      </Providers>,
    );
    pasteText(await screen.findByTestId('rich-editor'), '방금 친 글');
    fireEvent.click(screen.getByRole('tab', { name: '버그' }));
    expect(screen.getByRole('tab', { name: '새기능 •' })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('tab', { name: '새기능 •' }));
    expect(await screen.findByTestId('rich-editor')).toHaveTextContent('방금 친 글');
    expect(issueTemplateApi.setWorkspace).not.toHaveBeenCalled();
  });

  it('워크스페이스에서 고친 템플릿은 고친 사람 · 밀리초 시각을 보이고, 묻고 나서 처음 템플릿으로 되돌린다', async () => {
    vi.mocked(issueTemplateApi.resetWorkspace).mockResolvedValue(bug({ source: 'BUILTIN', updatedBy: null, updatedAt: null, rowVersion: null }));
    render(
      <Providers>
        <WorkspaceTemplatesHarness />
      </Providers>,
    );
    fireEvent.click(await screen.findByRole('tab', { name: '버그' }));
    expect(screen.getByText('워크스페이스 기본')).toBeInTheDocument();
    expect(screen.getByText('김철수 · 2026-11-23 08:20:11.123 고침')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '처음 템플릿으로 되돌리기' }));
    const dialog = await screen.findByRole('dialog');
    expect(dialog).toHaveTextContent('버그 템플릿을 처음 템플릿으로 되돌립니까?');
    expect(issueTemplateApi.resetWorkspace).not.toHaveBeenCalled();
    fireEvent.click(within(dialog).getByRole('button', { name: '처음 템플릿으로 되돌리기' }));
    await waitFor(() => expect(issueTemplateApi.resetWorkspace).toHaveBeenCalledWith('a', 'bug'));
  });

  it('이미 정한 템플릿을 고치면 읽은 판을 보내고, 남이 먼저 고쳤으면(409) 다시 읽되 고친 글은 남긴다', async () => {
    vi.mocked(issueTemplateApi.setWorkspace).mockRejectedValue(new ApiError(409, 'CONCURRENT_UPDATE', '다른 사람이 먼저 고쳤습니다.'));
    render(
      <Providers>
        <WorkspaceTemplatesHarness />
      </Providers>,
    );
    fireEvent.click(await screen.findByRole('tab', { name: '버그' }));
    pasteText(await screen.findByTestId('rich-editor'), '고침');
    fireEvent.click(screen.getByRole('button', { name: '템플릿 저장' }));
    await waitFor(() => expect(issueTemplateApi.setWorkspace).toHaveBeenCalledWith('a', 'bug', expect.stringContaining('고침'), 3));
    await waitFor(() => expect(issueTemplateApi.workspace).toHaveBeenCalledTimes(2));
    expect(screen.getByTestId('rich-editor')).toHaveTextContent('고침');
    expect(screen.getByRole('button', { name: '템플릿 저장' })).toBeEnabled();
  });

  it('고칠 권한이 없으면 읽기만 — 편집기 · 저장 · 되돌리기가 없다', async () => {
    render(
      <Providers>
        <WorkspaceTemplatesHarness canManage={false} />
      </Providers>,
    );
    expect(await screen.findByRole('heading', { name: '배경' })).toBeInTheDocument();
    expect(screen.queryByTestId('rich-editor')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '템플릿 저장' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '처음 템플릿으로 되돌리기' })).not.toBeInTheDocument();
    expect(screen.getByText('고칠 권한이 없어 읽기만 합니다.')).toBeInTheDocument();
  });
});

describe('프로젝트의 일감 템플릿', () => {
  it('실제로 쓰이는 템플릿과 출처 — 물려받은 것은 되돌릴 것이 없고, 이 프로젝트에서 덮어쓴 것은 워크스페이스 기본으로 되돌린다', async () => {
    vi.mocked(issueTemplateApi.resetProject).mockResolvedValue(bug({ source: 'WORKSPACE', inherited: true }));
    render(
      <Providers permissions={['issue.read', 'project.manage']}>
        <ProjectTemplatesPage />
      </Providers>,
    );
    expect(await screen.findByText('워크스페이스에서 물려받음')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '워크스페이스 기본으로 되돌리기' })).toBeDisabled();
    fireEvent.click(screen.getByRole('tab', { name: '버그' }));
    expect(screen.getByText('이 프로젝트')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '워크스페이스 기본으로 되돌리기' }));
    fireEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: '워크스페이스 기본으로 되돌리기' }));
    await waitFor(() => expect(issueTemplateApi.resetProject).toHaveBeenCalledWith('a', 'web', 'bug'));
  });

  it('덮어쓰면 프로젝트 템플릿으로 저장한다(물려받은 것을 처음 덮어쓸 때는 판을 보내지 않는다)', async () => {
    vi.mocked(issueTemplateApi.setProject).mockResolvedValue(feature({ source: 'PROJECT', rowVersion: 0 }));
    render(
      <Providers permissions={['issue.read', 'project.manage']}>
        <ProjectTemplatesPage />
      </Providers>,
    );
    pasteText(await screen.findByTestId('rich-editor'), '프로젝트 것');
    fireEvent.click(screen.getByRole('button', { name: '템플릿 저장' }));
    await waitFor(() => expect(issueTemplateApi.setProject).toHaveBeenCalledWith('a', 'web', 'feature', expect.stringContaining('프로젝트 것'), null));
  });

  it('project.manage 가 없으면 읽기만 보인다', async () => {
    render(
      <Providers permissions={['issue.read']}>
        <ProjectTemplatesPage />
      </Providers>,
    );
    expect(await screen.findByText('워크스페이스에서 물려받음')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '템플릿 저장' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '워크스페이스 기본으로 되돌리기' })).not.toBeInTheDocument();
  });
});

describe('새 일감 · 고치기 — 템플릿 채우기와 빠진 항목', () => {
  const types = [
    { key: 'feature', name: '새기능', description: null, active: true },
    { key: 'bug', name: '버그', description: null, active: true },
  ];

  function renderForm(value: IssueFormValues, onChange = vi.fn(), prefillOnMount = false) {
    render(
      <Providers permissions={['issue.read', 'issue.create', 'issue.edit']} path="/w/a/projects/web/issues/new">
        <IssueForm
          slug="a"
          project={project(['issue.read', 'issue.create', 'issue.edit'])}
          value={value}
          onChange={onChange}
          initialLabels={{ manager: null, assignee: null, parent: null }}
          types={types}
          errors={{}}
          prefillOnMount={prefillOnMount}
        />
      </Providers>,
    );
    return onChange;
  }

  /** 유형 고르기(MUI 선택 칸) */
  async function chooseType(name: string) {
    fireEvent.mouseDown(screen.getByRole('combobox', { name: /유형/ }));
    fireEvent.click(await screen.findByRole('option', { name }));
  }

  it('새 일감은 템플릿을 받으면 빈 설명을 처음 유형의 템플릿으로 채운다', async () => {
    const onChange = renderForm(emptyIssueForm('feature', null), vi.fn(), true);
    await waitFor(() => expect(onChange).toHaveBeenCalledWith({ description: FEATURE }));
  });

  it('유형을 바꾸면 손대지 않은 템플릿은 새 유형의 템플릿으로 바꾸고, 사람이 고친 설명은 그대로 둔다', async () => {
    const untouched = renderForm({ ...emptyIssueForm('feature', null), description: FEATURE });
    await waitFor(() => expect(issueTemplateApi.project).toHaveBeenCalled());
    await chooseType('버그');
    await waitFor(() => expect(untouched).toHaveBeenCalledWith({ type: 'bug', description: BUG }));
  });

  it('고친 설명은 유형을 바꿔도 덮지 않는다', async () => {
    const edited = renderForm({ ...emptyIssueForm('feature', null), description: `${FEATURE}내가 쓴 글` });
    await waitFor(() => expect(issueTemplateApi.project).toHaveBeenCalled());
    await chooseType('버그');
    await waitFor(() => expect(edited).toHaveBeenCalledWith({ type: 'bug' }));
  });

  it('빠진 필수 항목을 경고로 알린다(막지 않는다) — (선택) 항목 · 제목 수준 · 대소문자는 따지지 않는다', async () => {
    renderForm({ ...emptyIssueForm('feature', null), description: '# 배경\n\n내용\n\n### 수용 기준\n- [ ] 된다' });
    expect(await screen.findByText('템플릿의 필수 항목이 빠졌습니다: 하려는 것. 저장은 할 수 있습니다.')).toBeInTheDocument();
  });
});
