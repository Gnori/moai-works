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
 * 보류 · 취소 (2026-11-24 사용자: "이슈 상태에 취소, 보류도 만들어" — 일감 #152) · 관리자 확인 (2026-11-25 — 일감 #272) —
 * 일감 보기의 "상태 바꾸기" 메뉴 · 까닭을 받는 대화상자 · 관리자 확인 띠 · 상태 칩.
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

function renderView() {
  return render(
    <Providers>
      <IssueViewPage />
    </Providers>,
  );
}

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
  it('열한 상태를 차례대로 뜻과 함께 보인다 — 종료는 "완료한 일을 사람이 확인해 닫음", 보류 · 취소는 까닭을 적는다', async () => {
    const menu = await openStatusMenu();
    const items = within(menu).getAllByRole('menuitem');
    expect(items.map((item) => item.querySelector('.MuiListItemText-primary')?.textContent)).toEqual([
      '등록',
      '요구사항 확인요청',
      '요구사항 확인완료',
      '진행',
      '테스트',
      '확인요청',
      '확인완료',
      '보류',
      '완료',
      '종료',
      '취소',
    ]);
    expect(items[9]).toHaveTextContent('완료한 일을 사람이 확인해 닫음');
    expect(items[7]).toHaveTextContent('까닭을 적음');
    expect(items[10]).toHaveTextContent('하지 않기로 함 · 중복');
    expect(items[5]).toHaveTextContent('관리자가 결과를 확인해 주기를 기다림');
    // 지금 상태(진행)에 표시 · 관리자(나)는 확인완료도 고를 수 있다
    expect(items[3]).toHaveClass('Mui-selected');
    expect(items[6]).not.toHaveClass('Mui-disabled');
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
      REQUIREMENT_REVIEW: '요구사항 확인요청',
      REQUIREMENT_CONFIRMED: '요구사항 확인완료',
      IN_PROGRESS: '진행',
      TESTING: '테스트',
      REVIEW: '확인요청',
      CONFIRMED: '확인완료',
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

  it('확인요청 둘은 모래시계, 확인완료 둘은 확인 표시다 — 채운 등록 · 완료와 다르게 테두리만(일감 #272)', () => {
    for (const status of ['REQUIREMENT_REVIEW', 'REVIEW'] as const) {
      const c = chip(status);
      expect(c.el.querySelector('[data-testid="HourglassTopRoundedIcon"]')).not.toBeNull();
      expect(c.el).toHaveClass('MuiChip-outlined');
      c.unmount();
    }
    for (const status of ['REQUIREMENT_CONFIRMED', 'CONFIRMED'] as const) {
      const c = chip(status);
      expect(c.el.querySelector('[data-testid="TaskAltRoundedIcon"]')).not.toBeNull();
      expect(c.el).toHaveClass('MuiChip-outlined');
      c.unmount();
    }
    const done = chip('DONE');
    expect(done.el).not.toHaveClass('MuiChip-outlined');
    expect(done.el.querySelector('[data-testid="TaskAltRoundedIcon"]')).toBeNull();
    done.unmount();
  });
});

describe('관리자 확인 (일감 #272)', () => {
  const OTHER = { id: 'bbbbbbbb-0000-0000-0000-000000000002', username: 'lee', displayName: '이영희' };
  /** 담당자(이영희)가 확인요청으로 옮긴 이력 줄 — 같은 줄의 댓글이 무엇을 확인할지다 */
  const requested = (status: 'REQUIREMENT_REVIEW' | 'REVIEW', notes: string): IssueDetail => ({
    ...issue,
    status,
    assignee: OTHER,
    journal: [
      {
        id: 'j1',
        actorType: 'USER',
        actorId: OTHER.id,
        actorName: '이영희',
        via: 'MCP',
        createdAt: '2026-11-25T09:00:00.001Z',
        notes,
        changes: [{ field: 'status', before: 'TESTING', after: status }],
        revisionNo: 4,
        rowVersion: 1,
        editedAt: null,
        deleted: false,
        deletedAt: null,
        deletedBy: null,
      },
    ],
  });

  it('관리자(나)에게는 확인 요청 띠에 요청한 사람 · 확인할 것 · 확인 완료 단추 — 누르면 짝인 확인완료로 바꾼다', async () => {
    vi.mocked(issueApi.detail).mockResolvedValue(requested('REVIEW', '잠금을 만들었음 — 다섯 번 틀리면 잠긴다'));
    vi.mocked(issueApi.update).mockResolvedValue({ ...issue, status: 'CONFIRMED', rowVersion: 4 });
    renderView();
    const banner = await screen.findByTestId('review-banner');
    expect(within(banner).getByText('결과 확인을 요청받았습니다')).toBeInTheDocument();
    expect(within(banner).getByText('잠금을 만들었음 — 다섯 번 틀리면 잠긴다')).toBeInTheDocument();
    expect(within(banner).getByText(/이영희 님의 요청/)).toBeInTheDocument();
    fireEvent.click(within(banner).getByRole('button', { name: '확인 완료' }));
    await waitFor(() => expect(issueApi.update).toHaveBeenCalledWith('a', 7, { rowVersion: 3, status: 'CONFIRMED' }));
  });

  it('반려는 까닭을 받아 확인요청 → 진행으로 되돌린다(요구사항 확인요청 → 등록)', async () => {
    vi.mocked(issueApi.detail).mockResolvedValue(requested('REQUIREMENT_REVIEW', '범위가 맞나요?'));
    vi.mocked(issueApi.update).mockResolvedValue({ ...issue, status: 'NEW', rowVersion: 4 });
    renderView();
    const banner = await screen.findByTestId('review-banner');
    expect(within(banner).getByText('요구사항 확인을 요청받았습니다')).toBeInTheDocument();
    fireEvent.click(within(banner).getByRole('button', { name: '반려' }));
    const dialog = await screen.findByRole('dialog', { name: '요구사항 확인요청 되돌리기' });
    expect(within(dialog).getByText(/요구사항 확인요청을\(를\) 등록\(으\)로 되돌립니다/)).toBeInTheDocument();
    fireEvent.click(within(dialog).getByRole('button', { name: '되돌리기' }));
    expect(await within(dialog).findByText('까닭을 적어 주십시오.')).toBeInTheDocument();
    expect(issueApi.update).not.toHaveBeenCalled();
    fireEvent.change(within(dialog).getByRole('textbox', { name: /까닭/ }), { target: { value: '관리자 계정은 빼야 한다' } });
    fireEvent.click(within(dialog).getByRole('button', { name: '되돌리기' }));
    await waitFor(() =>
      expect(issueApi.update).toHaveBeenCalledWith('a', 7, { rowVersion: 3, status: 'NEW', notes: '관리자 계정은 빼야 한다' }),
    );
  });

  it('관리자가 아니면 띠는 누구를 기다리는지만 보이고, 메뉴의 확인완료 둘은 꺼 두고 까닭을 적는다', async () => {
    vi.mocked(issueApi.detail).mockResolvedValue({ ...requested('REVIEW', '확인해 주십시오'), manager: OTHER });
    renderView();
    const banner = await screen.findByTestId('review-banner');
    expect(within(banner).getByText('관리자의 결과 확인을 기다립니다')).toBeInTheDocument();
    expect(within(banner).getByText('관리자 이영희 님이 웹에서 확인하거나 반려합니다.')).toBeInTheDocument();
    expect(within(banner).queryByRole('button', { name: '확인 완료' })).not.toBeInTheDocument();
    expect(within(banner).queryByRole('button', { name: '반려' })).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '상태 바꾸기' }));
    const menu = await screen.findByRole('menu');
    for (const name of [/^요구사항 확인완료/, /^확인완료/]) {
      const item = within(menu).getByRole('menuitem', { name });
      expect(item).toHaveClass('Mui-disabled');
      expect(item).toHaveTextContent('관리자(이영희) 또는 프로젝트 관리자만 고를 수 있습니다');
    }
  });

  it('확인요청 둘을 고르면 무엇을 확인할지 받는 대화상자를 연다', async () => {
    vi.mocked(issueApi.update).mockResolvedValue({ ...issue, status: 'REVIEW', rowVersion: 4 });
    const menu = await openStatusMenu();
    fireEvent.click(within(menu).getByRole('menuitem', { name: /^확인요청/ }));
    const dialog = await screen.findByRole('dialog', { name: '결과 확인 요청하기' });
    expect(within(dialog).getByText(/한 것 · 확인 방법 · 결과 · 남은 것/)).toBeInTheDocument();
    fireEvent.click(within(dialog).getByRole('button', { name: '확인 요청하기' }));
    expect(await within(dialog).findByText('관리자가 무엇을 확인할지 적어 주십시오.')).toBeInTheDocument();
    fireEvent.change(within(dialog).getByRole('textbox', { name: /확인할 것/ }), { target: { value: '잠금 확인' } });
    fireEvent.click(within(dialog).getByRole('button', { name: '확인 요청하기' }));
    await waitFor(() =>
      expect(issueApi.update).toHaveBeenCalledWith('a', 7, { rowVersion: 3, status: 'REVIEW', notes: '잠금 확인' }),
    );
  });

  it('확인요청이 아니면 띠가 없다', async () => {
    renderView();
    await screen.findByRole('button', { name: '상태 바꾸기' });
    expect(screen.queryByTestId('review-banner')).not.toBeInTheDocument();
  });
});
