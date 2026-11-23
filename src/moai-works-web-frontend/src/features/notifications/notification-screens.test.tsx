import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import type { ReactNode } from 'react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router';
import { afterEach, describe, expect, it, vi } from 'vitest';

import '@/shared/i18n/i18n';
import { notificationApi } from '@/shared/api/endpoints';
import type * as Endpoints from '@/shared/api/endpoints';
import type { NotificationItem, NotificationPreference } from '@/shared/api/types';
import { AppearanceProvider } from '@/theme/AppearanceProvider';

import { NotificationSettingsSection } from './NotificationSettingsSection';
import { NotificationsPage } from './NotificationsPage';
import { notificationKeys } from './queries';

/** 알림함 · 알림 설정 화면을 실제로 그려 본다 — 서버 호출만 가짜로 */

vi.mock('@/shared/api/endpoints', async (importOriginal) => {
  const actual = await importOriginal<typeof Endpoints>();
  return {
    ...actual,
    notificationApi: { inbox: vi.fn(), unreadCount: vi.fn(), read: vi.fn(), readAll: vi.fn(), preferences: vi.fn(), setPreference: vi.fn() },
  };
});

const issueParams = { project: 'web', projectName: '웹', number: 12, title: '로그인 실패', via: 'WEB' };

function item(id: string, kind: string, extra: Partial<NotificationItem> = {}): NotificationItem {
  return {
    id,
    kind,
    subjectType: 'issue',
    subjectId: null,
    params: issueParams,
    actorName: '김철수',
    count: 1,
    workspaceSlug: 'team-a',
    workspaceName: '팀 A',
    createdAt: '2026-11-22T09:00:00.001',
    updatedAt: '2026-11-22T09:03:04.005',
    readAt: null,
    ...extra,
  };
}

/** 지금 주소 — 개인 설정이 지금 화면 위에 열리는지(?settings=…) 본다 */
function LocationProbe() {
  const location = useLocation();
  return <output aria-label="주소">{location.pathname + location.search}</output>;
}

function renderAt(path: string, page: ReactNode, client = new QueryClient({ defaultOptions: { queries: { retry: false } } })) {
  render(
    <AppearanceProvider>
      <QueryClientProvider client={client}>
        <MemoryRouter initialEntries={[path]}>
          <Routes>
            <Route path="/notifications" element={page} />
            <Route path="/w/:slug/issues/:number" element={<div>일감 화면</div>} />
          </Routes>
          <LocationProbe />
        </MemoryRouter>
      </QueryClientProvider>
    </AppearanceProvider>,
  );
  return client;
}

afterEach(() => {
  vi.clearAllMocks();
});

describe('알림함', () => {
  it('한 줄 — 문장 · 워크스페이스 · 묶인 수 · 밀리초 시각 · 읽지 않음 표시', async () => {
    vi.mocked(notificationApi.inbox).mockResolvedValue({
      items: [item('n1', 'issue.assigned', { count: 3 }), item('n2', 'issue.commented', { readAt: '2026-11-22T10:00:00Z' })],
      unread: 1,
      next: null,
    });
    renderAt('/notifications', <NotificationsPage />);
    const list = await screen.findByRole('list', { name: '알림 목록' });
    const [first, second] = within(list).getAllByRole('listitem') as [HTMLElement, HTMLElement];
    expect(first).toHaveTextContent('김철수 님이 나를 웹 #12 「로그인 실패」의 담당자로 정했습니다');
    expect(first).toHaveTextContent('×3');
    expect(first).toHaveTextContent('팀 A');
    expect(first).toHaveTextContent('2026-11-22 09:03:04.005');
    expect(first).toHaveTextContent('읽지 않음:');
    expect(second).not.toHaveTextContent('읽지 않음:');
    expect(screen.getByRole('tab', { name: '읽지 않음 1' })).toBeInTheDocument();
    expect(notificationApi.inbox).toHaveBeenCalledWith({ unread: undefined, before: undefined, limit: 30 });
  });

  it('더 보기는 앞 쪽의 next 를 그대로 before 로 넘긴다', async () => {
    vi.mocked(notificationApi.inbox)
      .mockResolvedValueOnce({ items: [item('n1', 'issue.assigned')], unread: 2, next: '2026-11-22T09:03:04.005Z|n1' })
      .mockResolvedValueOnce({ items: [item('n0', 'issue.commented')], unread: 2, next: null });
    renderAt('/notifications', <NotificationsPage />);
    fireEvent.click(await screen.findByRole('button', { name: '더 보기' }));
    await waitFor(() =>
      expect(notificationApi.inbox).toHaveBeenLastCalledWith({ unread: undefined, before: '2026-11-22T09:03:04.005Z|n1', limit: 30 }),
    );
    expect(await screen.findByText(/댓글을 남겼습니다/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '더 보기' })).not.toBeInTheDocument();
  });

  it('누르면 읽음으로 하고(종 모양의 수를 바꾼다) 그 일감으로 간다', async () => {
    vi.mocked(notificationApi.inbox).mockResolvedValue({ items: [item('n1', 'issue.assigned')], unread: 4, next: null });
    vi.mocked(notificationApi.read).mockResolvedValue(3);
    const client = renderAt('/notifications', <NotificationsPage />);
    fireEvent.click(await screen.findByRole('button', { name: /담당자로 정했습니다/ }));
    await waitFor(() => expect(notificationApi.read).toHaveBeenCalledWith('n1'));
    expect(await screen.findByText('일감 화면')).toBeInTheDocument();
    expect(client.getQueryData(notificationKeys.unread)).toBe(3);
  });

  it('갈 곳이 없는 알림은 읽음으로만 하고, 이미 읽었으면 누를 것이 없다', async () => {
    vi.mocked(notificationApi.inbox).mockResolvedValue({
      items: [item('n1', 'issue.deleted'), item('n2', 'member.removed', { params: {}, readAt: '2026-11-22T10:00:00Z' })],
      unread: 1,
      next: null,
    });
    vi.mocked(notificationApi.read).mockResolvedValue(0);
    renderAt('/notifications', <NotificationsPage />);
    fireEvent.click(await screen.findByRole('button', { name: /지웠습니다/ }));
    await waitFor(() => expect(notificationApi.read).toHaveBeenCalledWith('n1'));
    expect(screen.queryByText('일감 화면')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /내보내졌습니다/ })).not.toBeInTheDocument();
    expect(screen.getByText('팀 A에서 내보내졌습니다')).toBeInTheDocument();
  });

  it('MCP 키 알림은 읽음으로 하고 이 화면 위에 개인 설정의 내 MCP 키를 연다', async () => {
    vi.mocked(notificationApi.inbox).mockResolvedValue({
      items: [item('n1', 'mcp_key.expired', { subjectType: 'mcp_key', params: { name: '노트북', keyPrefix: 'mai-wks-Ab12', note: '' } })],
      unread: 1,
      next: null,
    });
    vi.mocked(notificationApi.read).mockResolvedValue(0);
    renderAt('/notifications?tab=unread', <NotificationsPage />);
    fireEvent.click(await screen.findByRole('button', { name: /MCP 키 「노트북」/ }));
    await waitFor(() => expect(notificationApi.read).toHaveBeenCalledWith('n1'));
    await waitFor(() => expect(screen.getByRole('status', { name: '주소' })).toHaveTextContent('/notifications?tab=unread&settings=mcp-keys'));
  });

  it('읽지 않음 탭은 주소에 남고 읽지 않은 것만 묻는다 · 모두 읽음', async () => {
    vi.mocked(notificationApi.inbox).mockResolvedValue({ items: [item('n1', 'issue.assigned')], unread: 1, next: null });
    vi.mocked(notificationApi.readAll).mockResolvedValue(1);
    const client = renderAt('/notifications?tab=unread', <NotificationsPage />);
    await screen.findByRole('list', { name: '알림 목록' });
    expect(notificationApi.inbox).toHaveBeenCalledWith({ unread: true, before: undefined, limit: 30 });
    expect(screen.getByRole('tab', { name: '읽지 않음 1' })).toHaveAttribute('aria-selected', 'true');
    fireEvent.click(screen.getByRole('button', { name: '모두 읽음' }));
    await waitFor(() => expect(notificationApi.readAll).toHaveBeenCalled());
    await waitFor(() => expect(client.getQueryData(notificationKeys.unread)).toBe(0));
  });
});

describe('알림 설정', () => {
  const prefs: NotificationPreference[] = [
    { kind: 'join.requested', group: 'WORKSPACE', enabled: true, mandatory: false, email: false },
    { kind: 'member.removed', group: 'WORKSPACE', enabled: true, mandatory: true, email: false },
    { kind: 'issue.assigned', group: 'ISSUE', enabled: true, mandatory: false, email: false },
    { kind: 'issue.commented', group: 'ISSUE', enabled: false, mandatory: false, email: true },
    { kind: 'issue.due_soon', group: 'ISSUE', enabled: true, mandatory: false, email: true },
    { kind: 'mcp_key.expired', group: 'SECURITY', enabled: true, mandatory: true, email: false },
  ];

  it('묶음별로 보이고, 끌 수 없는 종류는 잠긴 채 까닭을 보인다', async () => {
    vi.mocked(notificationApi.preferences).mockResolvedValue(prefs);
    renderAt('/notifications?settings=notifications', <NotificationSettingsSection />);
    expect(await screen.findByRole('heading', { name: '워크스페이스' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 3 })).toHaveTextContent('알림 설정');
    expect(screen.getAllByRole('heading', { level: 4 }).map((h) => h.textContent)).toEqual(['워크스페이스', '일감', '보안 · MCP 키']);
    const locked = screen.getByRole('switch', { name: '워크스페이스에서 내보내짐' });
    expect(locked).toBeChecked();
    expect(locked).toBeDisabled();
    expect(locked).toHaveAccessibleDescription('알림함은 끌 수 없음 — 접근이 사라지거나 보안에 닿는 일입니다');
    expect(screen.getByRole('switch', { name: '다른 사람이 내 MCP 키를 만료시킴' })).toBeDisabled();
    expect(screen.getByRole('switch', { name: '나를 담당자로 정함' })).toBeEnabled();
  });

  it('바꾸면 바로 저장하고 응답의 목록으로 다시 그린다', async () => {
    vi.mocked(notificationApi.preferences).mockResolvedValue(prefs);
    vi.mocked(notificationApi.setPreference).mockResolvedValue(prefs.map((p) => (p.kind === 'issue.assigned' ? { ...p, enabled: false } : p)));
    const client = renderAt('/notifications?settings=notifications', <NotificationSettingsSection />);
    fireEvent.click(await screen.findByRole('switch', { name: '나를 담당자로 정함' }));
    await waitFor(() => expect(notificationApi.setPreference).toHaveBeenCalledWith('issue.assigned', { enabled: false }));
    await waitFor(() => expect(screen.getByRole('switch', { name: '나를 담당자로 정함' })).not.toBeChecked());
    expect(screen.getByRole('switch', { name: '나를 담당자로 정함' })).toBeEnabled();
    expect(client.getQueryData<NotificationPreference[]>(notificationKeys.preferences)?.find((p) => p.kind === 'issue.assigned')?.enabled).toBe(false);
    // 서버에 다시 묻지 않고 응답으로 바꿨다
    expect(notificationApi.preferences).toHaveBeenCalledTimes(1);
  });

  it('종류마다 메일 열이 있고, 끌 수 없는 종류도 메일은 고르며, 알림함을 끈 종류는 메일을 꺼 두고 까닭을 보인다', async () => {
    vi.mocked(notificationApi.preferences).mockResolvedValue(prefs);
    renderAt('/notifications?settings=notifications', <NotificationSettingsSection />);
    const dueSoon = await screen.findByRole('switch', { name: '완료 기한이 내일 — 메일' });
    expect(dueSoon).toBeChecked();
    expect(dueSoon).toBeEnabled();
    expect(screen.getByRole('switch', { name: '나를 담당자로 정함 — 메일' })).not.toBeChecked();
    // 알림함을 끌 수 없는 종류도 메일은 고른다
    expect(screen.getByRole('switch', { name: '워크스페이스에서 내보내짐 — 메일' })).toBeEnabled();
    // 알림함을 끈 종류 — 메일 설정이 켜져 있어도 꺼져 보이고 고를 수 없다
    const commented = screen.getByRole('switch', { name: '내 일감에 댓글이 달림 — 메일' });
    expect(commented).not.toBeChecked();
    expect(commented).toBeDisabled();
    expect(commented).toHaveAccessibleDescription('알림함에서 끈 종류는 메일도 가지 않습니다');
    expect(screen.getAllByText('메일')).toHaveLength(3);
  });

  it('메일을 켜면 메일만 바꿔 저장한다', async () => {
    vi.mocked(notificationApi.preferences).mockResolvedValue(prefs);
    vi.mocked(notificationApi.setPreference).mockResolvedValue(prefs.map((p) => (p.kind === 'issue.assigned' ? { ...p, email: true } : p)));
    renderAt('/notifications?settings=notifications', <NotificationSettingsSection />);
    fireEvent.click(await screen.findByRole('switch', { name: '나를 담당자로 정함 — 메일' }));
    await waitFor(() => expect(notificationApi.setPreference).toHaveBeenCalledWith('issue.assigned', { email: true }));
    await waitFor(() => expect(screen.getByRole('switch', { name: '나를 담당자로 정함 — 메일' })).toBeChecked());
    // 알림함 스위치는 그대로
    expect(screen.getByRole('switch', { name: '나를 담당자로 정함' })).toBeChecked();
  });
});
