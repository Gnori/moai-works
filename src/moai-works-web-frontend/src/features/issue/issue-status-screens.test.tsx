import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import type { ReactNode } from 'react';
import { MemoryRouter, Route, Routes } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import '@/shared/i18n/i18n';
import { ProjectProvider } from '@/features/project/project-context';
import { WorkspaceProvider } from '@/features/workspace/workspace-context';
import { attachmentApi, issueApi } from '@/shared/api/endpoints';
import type * as Endpoints from '@/shared/api/endpoints';
import { ApiError } from '@/shared/api/http';
import { issueStatuses, type IssueDetail, type PermissionCode, type ProjectDetail, type WorkspaceDetail } from '@/shared/api/types';
import { ConfirmProvider } from '@/shared/ui/ConfirmDialog';
import { AppearanceProvider } from '@/theme/AppearanceProvider';

import { IssueStatusChip } from './IssueStatusChip';
import { IssueViewPage } from './IssueViewPage';

/**
 * 보류 · 취소 (2026-11-24 사용자: "이슈 상태에 취소, 보류도 만들어" — 일감 #152) — 일감 보기의 "상태 바꾸기" 메뉴 · 까닭을 받는 대화상자 · 상태 칩.
 * 서버 호출(issueApi)만 가짜로 두고 화면 · 말은 그대로.
 */
vi.mock('@/shared/api/endpoints', async (importOriginal) => {
  const actual = await importOriginal<typeof Endpoints>();
  return {
    ...actual,
    issueApi: { ...actual.issueApi, detail: vi.fn(), update: vi.fn() },
    attachmentApi: { ...actual.attachmentApi, list: vi.fn() },
  };
});

const ME = 'aaaaaaaa-0000-0000-0000-000000000001';

vi.mock('@/features/auth/session', () => ({
  useMe: () => ({ id: 'aaaaaaaa-0000-0000-0000-000000000001', username: 'kim', displayName: '김철수' }),
}));

const workspace = { slug: 'a', name: 'A', myPermissions: [] as PermissionCode[] } as unknown as WorkspaceDetail;

const project: ProjectDetail = {
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
  myPermissions: ['issue.read', 'issue.edit', 'issue.comment'],
  myActions: [],
};

const issue: IssueDetail = {
  number: 7,
  project: { key: 'web', name: '웹' },
  type: 'bug',
  title: '로그인 실패',
  description: '',
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
  agentSession: null,
  modules: [],
  children: [],
  links: [],
  journal: [],
  workLogs: [],
};

function Providers({ children }: { children: ReactNode }) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return (
    <AppearanceProvider>
      <QueryClientProvider client={client}>
        <ConfirmProvider>
          <WorkspaceProvider workspace={workspace}>
            <ProjectProvider project={project}>
              <MemoryRouter initialEntries={['/w/a/issues/7']}>
                <Routes>
                  <Route path="/w/:slug/issues/:number" element={children} />
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
  vi.mocked(issueApi.detail).mockResolvedValue(issue);
  vi.mocked(attachmentApi.list).mockResolvedValue([]);
});

afterEach(() => {
  vi.clearAllMocks();
});

async function openStatusMenu() {
  render(
    <Providers>
      <IssueViewPage />
    </Providers>,
  );
  fireEvent.click(await screen.findByRole('button', { name: '상태 바꾸기' }));
  return screen.findByRole('menu');
}

describe('상태 바꾸기 메뉴', () => {
  it('일곱 상태를 차례대로 뜻과 함께 보인다 — 종료는 "완료한 일을 사람이 확인해 닫음", 보류 · 취소는 까닭을 적는다', async () => {
    const menu = await openStatusMenu();
    const items = within(menu).getAllByRole('menuitem');
    expect(items.map((item) => item.querySelector('.MuiListItemText-primary')?.textContent)).toEqual([
      '등록',
      '진행',
      '테스트',
      '보류',
      '완료',
      '종료',
      '취소',
    ]);
    expect(items[5]).toHaveTextContent('완료한 일을 사람이 확인해 닫음');
    expect(items[3]).toHaveTextContent('까닭을 적음');
    expect(items[6]).toHaveTextContent('하지 않기로 함 · 중복');
    // 지금 상태(진행)에 표시
    expect(items[1]).toHaveClass('Mui-selected');
  });

  it('보류 · 취소가 아니면 바로 바꾼다(까닭 없이)', async () => {
    vi.mocked(issueApi.update).mockResolvedValue({ ...issue, status: 'TESTING', rowVersion: 4 });
    const menu = await openStatusMenu();
    fireEvent.click(within(menu).getByRole('menuitem', { name: /^테스트/ }));
    await waitFor(() => expect(issueApi.update).toHaveBeenCalledWith('a', 7, { rowVersion: 3, status: 'TESTING' }));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('보류를 고르면 까닭을 받는 대화상자가 열리고, 비우면 보내지 않으며, 적으면 상태와 까닭(notes)을 함께 보낸다', async () => {
    vi.mocked(issueApi.update).mockResolvedValue({ ...issue, status: 'ON_HOLD', rowVersion: 4 });
    const menu = await openStatusMenu();
    fireEvent.click(within(menu).getByRole('menuitem', { name: /^보류/ }));
    const dialog = await screen.findByRole('dialog', { name: '보류로 바꾸기' });
    expect(within(dialog).getByText(/열린 일감으로 남고 완료 기한도 그대로 셉니다/)).toBeInTheDocument();
    const reason = within(dialog).getByRole('textbox', { name: /까닭/ });
    expect(reason).toBeRequired();

    fireEvent.click(within(dialog).getByRole('button', { name: '보류로 바꾸기' }));
    expect(await within(dialog).findByText('까닭을 적어 주십시오.')).toBeInTheDocument();
    expect(issueApi.update).not.toHaveBeenCalled();

    fireEvent.change(reason, { target: { value: '  디자인 결정을 기다림  ' } });
    fireEvent.click(within(dialog).getByRole('button', { name: '보류로 바꾸기' }));
    await waitFor(() =>
      expect(issueApi.update).toHaveBeenCalledWith('a', 7, { rowVersion: 3, status: 'ON_HOLD', notes: '디자인 결정을 기다림' }),
    );
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  });

  it('취소 대화상자 — 닫는 단추는 "닫기"이고 보내지 않는다 · 서버가 까닭이 없다고 답하면 칸에 보인다', async () => {
    const menu = await openStatusMenu();
    fireEvent.click(within(menu).getByRole('menuitem', { name: /^취소/ }));
    let dialog = await screen.findByRole('dialog', { name: '취소로 바꾸기' });
    expect(within(dialog).getByText(/종료일이 비어 있으면 오늘로 채우며 완료일은 비웁니다/)).toBeInTheDocument();
    // 취소 상태로 바꾸는 대화상자에 "취소" 단추를 두지 않는다(헷갈린다)
    expect(within(dialog).queryByRole('button', { name: '취소' })).not.toBeInTheDocument();
    fireEvent.click(within(dialog).getByRole('button', { name: '닫기' }));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(issueApi.update).not.toHaveBeenCalled();

    vi.mocked(issueApi.update).mockRejectedValue(new ApiError(400, 'STATUS_REASON_REQUIRED', '취소로 바꾸려면 까닭을 넣으십시오.'));
    fireEvent.click(screen.getByRole('button', { name: '상태 바꾸기' }));
    fireEvent.click(within(await screen.findByRole('menu')).getByRole('menuitem', { name: /^취소/ }));
    dialog = await screen.findByRole('dialog', { name: '취소로 바꾸기' });
    fireEvent.change(within(dialog).getByRole('textbox', { name: /까닭/ }), { target: { value: '중복' } });
    fireEvent.click(within(dialog).getByRole('button', { name: '취소로 바꾸기' }));
    expect(await within(dialog).findByText('취소로 바꾸려면 까닭을 넣으십시오.')).toBeInTheDocument();
  });
});

describe('상태 칩', () => {
  function chip(status: (typeof issueStatuses)[number]) {
    const { container, unmount } = render(
      <AppearanceProvider>
        <IssueStatusChip status={status} />
      </AppearanceProvider>,
    );
    const el = container.querySelector(`[data-status="${status}"]`) as HTMLElement;
    return { el, unmount };
  }

  it('모든 상태가 이름을 적는다 — 색만으로 전하지 않는다', () => {
    const names: Record<(typeof issueStatuses)[number], string> = {
      NEW: '등록',
      IN_PROGRESS: '진행',
      TESTING: '테스트',
      ON_HOLD: '보류',
      DONE: '완료',
      CLOSED: '종료',
      CANCELLED: '취소',
    };
    issueStatuses.forEach((status) => {
      const { el, unmount } = chip(status);
      expect(el).toHaveTextContent(names[status]);
      unmount();
    });
  });

  it('보류는 멈춤 아이콘 · 점선 테두리, 취소는 취소선이다(진행 · 종료와 모양이 다르다)', () => {
    const hold = chip('ON_HOLD');
    expect(hold.el.querySelector('[data-testid="PauseRoundedIcon"]')).not.toBeNull();
    expect(hold.el).toHaveStyle({ borderStyle: 'dashed' });
    hold.unmount();

    const cancelled = chip('CANCELLED');
    expect(cancelled.el.querySelector('.MuiChip-label')).toHaveStyle({ textDecoration: 'line-through' });
    expect(cancelled.el.querySelector('[data-testid="PauseRoundedIcon"]')).toBeNull();
    cancelled.unmount();

    const closed = chip('CLOSED');
    expect(closed.el.querySelector('.MuiChip-label')).not.toHaveStyle({ textDecoration: 'line-through' });
    closed.unmount();
  });
});
