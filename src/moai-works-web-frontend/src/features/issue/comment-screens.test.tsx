import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import type { ReactNode } from 'react';
import { MemoryRouter, Route, Routes } from 'react-router';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';

import '@/shared/i18n/i18n';
import { ProjectProvider } from '@/features/project/project-context';
import { WorkspaceProvider } from '@/features/workspace/workspace-context';
import { attachmentApi, issueApi } from '@/shared/api/endpoints';
import type * as Endpoints from '@/shared/api/endpoints';
import { ApiError } from '@/shared/api/http';
import type { IssueDetail, IssueJournal, PermissionCode, ProjectDetail, ProjectStatus, WorkspaceDetail } from '@/shared/api/types';
import { ConfirmProvider } from '@/shared/ui/ConfirmDialog';
import { AppearanceProvider } from '@/theme/AppearanceProvider';

import { IssueViewPage } from './IssueViewPage';

/**
 * 댓글 고치기 · 지우기 (FR-ISS-019) — 내 댓글의 ⋮ 에서 같은 편집기로 고치고(댓글 판을 함께) 확인을 받아 지운다.
 * 남의 댓글은 고칠 수 없고 project.manage 가 있으면 지우기만, 고친 표시 · 지운 댓글 · 이력의 말. 서버 호출만 가짜.
 */

vi.mock('@/shared/api/endpoints', async (importOriginal) => {
  const actual = await importOriginal<typeof Endpoints>();
  return {
    ...actual,
    issueApi: { ...actual.issueApi, detail: vi.fn(), editComment: vi.fn(), deleteComment: vi.fn() },
    attachmentApi: { ...actual.attachmentApi, list: vi.fn() },
  };
});

const ME = 'aaaaaaaa-0000-0000-0000-000000000001';
const LEE = 'aaaaaaaa-0000-0000-0000-000000000002';

vi.mock('@/features/auth/session', () => ({
  useMe: () => ({ id: 'aaaaaaaa-0000-0000-0000-000000000001', username: 'kim', displayName: '김철수' }),
}));

const workspace = { slug: 'a', name: 'A', myPermissions: [] as PermissionCode[] } as unknown as WorkspaceDetail;

function project(permissions: PermissionCode[], status: ProjectStatus): ProjectDetail {
  return {
    id: 'p1',
    key: 'web',
    name: '웹',
    description: null,
    visibility: 'WORKSPACE',
    status,
    updatedAt: '2026-11-22T09:00:00',
    createdAt: '2026-11-01T09:00:00',
    archivedAt: null,
    issueCount: 1,
    myRoles: [],
    myPermissions: permissions,
    myActions: [],
  };
}

function line(id: string, extra: Partial<IssueJournal>): IssueJournal {
  return {
    id,
    actorType: 'USER',
    actorId: ME,
    actorName: '김철수',
    via: 'WEB',
    createdAt: '2026-11-22T10:00:00.000',
    notes: null,
    changes: [],
    revisionNo: null,
    rowVersion: null,
    editedAt: null,
    deleted: false,
    deletedAt: null,
    deletedBy: null,
    ...extra,
  };
}

const lee = { id: LEE, username: 'lee', displayName: '이영희' };

const issue: IssueDetail = {
  number: 7,
  type: 'bug',
  title: '로그인 실패',
  description: '설명',
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
  children: [],
  links: [],
  journal: [
    line('c-mine', { notes: '내 글', rowVersion: 2, editedAt: '2026-11-22T11:00:00.123' }),
    line('c-lee', { actorId: LEE, actorName: '이영희', via: 'MCP', notes: '남의 글', rowVersion: 0 }),
    line('c-gone', { actorId: LEE, actorName: '이영희', rowVersion: 1, deleted: true, deletedAt: '2026-11-22T12:00:00.456', deletedBy: lee }),
    line('h-1', {
      actorId: LEE,
      actorName: '이영희',
      createdAt: '2026-11-22T12:00:00.456',
      changes: [{ field: 'comment', before: { id: 'c-gone', author: { id: LEE, name: '이영희' } }, after: null }],
    }),
  ],
  workLogs: [],
};

function Providers({ children, permissions, status = 'ACTIVE' }: { children: ReactNode; permissions: PermissionCode[]; status?: ProjectStatus }) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return (
    <AppearanceProvider>
      <QueryClientProvider client={client}>
        <ConfirmProvider>
          <WorkspaceProvider workspace={workspace}>
            <ProjectProvider project={project(permissions, status)}>
              <MemoryRouter initialEntries={['/w/a/projects/web/issues/7?tab=comments']}>
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

function renderView(permissions: PermissionCode[], status: ProjectStatus = 'ACTIVE') {
  return render(
    <Providers permissions={permissions} status={status}>
      <IssueViewPage />
    </Providers>,
  );
}

/** 시험용 편집기에 글을 붙여 넣는다(글은 끝에 붙는다) */
function paste(target: Element, text: string) {
  const event = new Event('paste', { bubbles: true, cancelable: true });
  Object.defineProperty(event, 'clipboardData', { value: { files: [], types: ['text/plain'], getData: () => text } });
  act(() => {
    target.dispatchEvent(event);
  });
}

/** 그 댓글이 든 이력 줄(li) */
function entryOf(text: string): HTMLElement {
  return screen.getByText(text).closest('li') as HTMLElement;
}

beforeAll(() => {
  const rects = () => Object.assign([], { item: () => null }) as unknown as DOMRectList;
  Range.prototype.getClientRects = rects;
  Range.prototype.getBoundingClientRect = () => new DOMRect();
  Element.prototype.scrollIntoView = vi.fn();
});

beforeEach(() => {
  vi.mocked(issueApi.detail).mockResolvedValue(issue);
  vi.mocked(attachmentApi.list).mockResolvedValue([]);
});

afterEach(() => {
  vi.clearAllMocks();
});

describe('댓글 고치기 · 지우기', () => {
  it('고친 표시 · 지운 댓글 · 이력의 말을 보이고, 내 댓글에만 ⋮ 가 있다', async () => {
    const { container } = renderView(['issue.read', 'issue.comment']);
    expect(await screen.findByText('내 글')).toBeInTheDocument();
    // 고친 댓글 — "고침 …" 과 밀리초 시각(도구 설명 · time 의 dateTime)
    expect(within(entryOf('내 글')).getByText(/고침/)).toBeInTheDocument();
    expect(container.querySelector('time[datetime="2026-11-22T11:00:00.123"]')).not.toBeNull();
    // 지운 댓글은 제자리에 — 글 없이 누가 지웠는지
    expect(screen.getByText('지운 댓글')).toBeInTheDocument();
    expect(screen.getByText(/이영희 님이 지움/)).toBeInTheDocument();
    expect(screen.getByText('이영희 님의 댓글을 지웠습니다.')).toBeInTheDocument();
    // ⋮ 는 내 댓글 하나뿐(남의 댓글 · 지운 댓글에는 없다)
    expect(screen.getAllByRole('button', { name: '댓글 동작' })).toHaveLength(1);
    expect(within(entryOf('남의 글')).queryByRole('button', { name: '댓글 동작' })).not.toBeInTheDocument();
  });

  it('고치기는 같은 편집기를 그 자리에 열고, 댓글 판(rowVersion)과 함께 저장한다', async () => {
    const saved = { ...issue, journal: issue.journal.map((j) => (j.id === 'c-mine' ? { ...j, notes: '내 글 고침', rowVersion: 3 } : j)) };
    vi.mocked(issueApi.editComment).mockResolvedValue(saved);
    renderView(['issue.read', 'issue.comment']);
    await screen.findByText('내 글');
    fireEvent.click(within(entryOf('내 글')).getByRole('button', { name: '댓글 동작' }));
    fireEvent.click(await screen.findByRole('menuitem', { name: '고치기' }));

    const editor = await screen.findByRole('textbox', { name: '댓글 고치기 (Markdown)' });
    expect(editor).toHaveTextContent('내 글');
    paste(editor, ' 고침');
    // 저장 뒤 다시 읽으면 서버에는 고친 글이 있다
    vi.mocked(issueApi.detail).mockResolvedValue(saved);
    fireEvent.click(screen.getByRole('button', { name: '저장' }));
    await waitFor(() =>
      expect(issueApi.editComment).toHaveBeenCalledWith('a', 'web', 7, 'c-mine', expect.stringContaining('고침'), 2),
    );
    // 저장하면 편집기를 닫고 받은 글을 보인다
    await waitFor(() => expect(screen.queryByRole('textbox', { name: '댓글 고치기 (Markdown)' })).not.toBeInTheDocument());
    expect(screen.getByText('내 글 고침')).toBeInTheDocument();
  });

  it('그 사이 다른 곳에서 고쳤으면(409) 고치던 글을 남긴 채 다시 읽고, 취소하면 닫는다', async () => {
    vi.mocked(issueApi.editComment).mockRejectedValue(new ApiError(409, 'CONCURRENT_UPDATE', '먼저 고침'));
    renderView(['issue.read', 'issue.comment']);
    await screen.findByText('내 글');
    fireEvent.click(within(entryOf('내 글')).getByRole('button', { name: '댓글 동작' }));
    fireEvent.click(await screen.findByRole('menuitem', { name: '고치기' }));
    const editor = await screen.findByRole('textbox', { name: '댓글 고치기 (Markdown)' });
    paste(editor, ' 늦은 고침');
    const reads = vi.mocked(issueApi.detail).mock.calls.length;
    fireEvent.click(screen.getByRole('button', { name: '저장' }));
    await waitFor(() => expect(vi.mocked(issueApi.detail).mock.calls.length).toBeGreaterThan(reads));
    expect(screen.getByRole('textbox', { name: '댓글 고치기 (Markdown)' })).toHaveTextContent('늦은 고침');

    fireEvent.click(screen.getByRole('button', { name: '취소' }));
    expect(screen.queryByRole('textbox', { name: '댓글 고치기 (Markdown)' })).not.toBeInTheDocument();
  });

  it('지우기는 확인을 받고, project.manage 가 있으면 남의 댓글은 지우기만 된다', async () => {
    vi.mocked(issueApi.deleteComment).mockResolvedValue(issue);
    renderView(['issue.read', 'issue.comment', 'project.manage']);
    await screen.findByText('남의 글');
    expect(screen.getAllByRole('button', { name: '댓글 동작' })).toHaveLength(2);

    fireEvent.click(within(entryOf('남의 글')).getByRole('button', { name: '댓글 동작' }));
    expect(await screen.findByRole('menuitem', { name: '지우기' })).toBeInTheDocument();
    expect(screen.queryByRole('menuitem', { name: '고치기' })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('menuitem', { name: '지우기' }));

    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByText(/되돌릴 수 없습니다/)).toBeInTheDocument();
    fireEvent.click(within(dialog).getByRole('button', { name: '지우기' }));
    await waitFor(() => expect(issueApi.deleteComment).toHaveBeenCalledWith('a', 'web', 7, 'c-lee'));
  });

  it('지금 댓글 권한이 없으면 내 댓글은 지우기만, 보관한 프로젝트에는 단추가 없다', async () => {
    const first = renderView(['issue.read']);
    await screen.findByText('내 글');
    fireEvent.click(within(entryOf('내 글')).getByRole('button', { name: '댓글 동작' }));
    expect(await screen.findByRole('menuitem', { name: '지우기' })).toBeInTheDocument();
    expect(screen.queryByRole('menuitem', { name: '고치기' })).not.toBeInTheDocument();
    first.unmount();

    renderView(['issue.read', 'issue.comment', 'project.manage'], 'ARCHIVED');
    await screen.findByText('내 글');
    expect(screen.queryByRole('button', { name: '댓글 동작' })).not.toBeInTheDocument();
  });
});
