import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import { afterEach, describe, expect, it, vi } from 'vitest';

import '@/shared/i18n/i18n';
import { meApi } from '@/shared/api/endpoints';
import type * as Endpoints from '@/shared/api/endpoints';
import { ApiError } from '@/shared/api/http';
import { AppearanceProvider } from '@/theme/AppearanceProvider';

import { PasswordSection } from './PasswordSection';

/**
 * 개인 설정 › 비밀번호 (FR-WSP-006) — 지금 비밀번호 · 새 비밀번호 · 확인, "다른 기기의 로그인 끊기"(기본 켬).
 * 틀린 지금 비밀번호 · 정책 오류 · 같은 비밀번호는 칸 아래에, 서버가 이 로그인까지 끝냈으면 로그아웃해 다시 로그인하게 한다.
 */

vi.mock('@/shared/api/endpoints', async (importOriginal) => {
  const actual = await importOriginal<typeof Endpoints>();
  return { ...actual, meApi: { ...actual.meApi, changePassword: vi.fn() } };
});

const signOut = vi.hoisted(() => vi.fn());
vi.mock('@/features/auth/session', () => ({
  useMe: () => ({ username: 'kim', email: 'kim@example.com', displayName: '김철수', platformRoles: [] }),
  useSession: () => ({ signOut }),
}));

function renderSection() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <AppearanceProvider>
      <QueryClientProvider client={client}>
        <MemoryRouter initialEntries={['/w/team-a?settings=password']}>
          <Routes>
            <Route path="/sign-in" element={<p>로그인 화면</p>} />
            <Route path="*" element={<PasswordSection />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>
    </AppearanceProvider>,
  );
}

function fill(current: string, next: string, confirm = next) {
  fireEvent.change(screen.getByLabelText('지금 비밀번호'), { target: { value: current } });
  fireEvent.change(screen.getByLabelText('새 비밀번호'), { target: { value: next } });
  fireEvent.change(screen.getByLabelText('새 비밀번호 확인'), { target: { value: confirm } });
}

const submit = () => fireEvent.click(screen.getByRole('button', { name: '비밀번호 변경' }));

afterEach(() => {
  vi.clearAllMocks();
});

describe('비밀번호 칸', () => {
  it('다른 기기의 로그인 끊기는 기본으로 켜져 있고, 그대로 보내면 끊는다', async () => {
    vi.mocked(meApi.changePassword).mockResolvedValue({ otherSessionsEnded: true, currentSessionKept: true });
    renderSection();
    expect(screen.getByRole('heading', { name: '비밀번호', level: 3 })).toBeInTheDocument();
    expect(screen.getByRole('checkbox', { name: '다른 기기의 로그인 끊기' })).toBeChecked();
    fill('old-password-1', 'new-password-2');
    submit();
    await waitFor(() =>
      expect(meApi.changePassword).toHaveBeenCalledWith({
        currentPassword: 'old-password-1',
        newPassword: 'new-password-2',
        signOutOtherSessions: true,
      }),
    );
    // 다 바꾸면 칸을 비운다(비밀번호를 화면에 남기지 않는다)
    await waitFor(() => expect(screen.getByLabelText('지금 비밀번호')).toHaveValue(''));
    expect(signOut).not.toHaveBeenCalled();
  });

  it('끊기를 끄면 다른 기기의 로그인을 그대로 둔다', async () => {
    vi.mocked(meApi.changePassword).mockResolvedValue({ otherSessionsEnded: false, currentSessionKept: true });
    renderSection();
    fireEvent.click(screen.getByRole('checkbox', { name: '다른 기기의 로그인 끊기' }));
    fill('old-password-1', 'new-password-2');
    submit();
    await waitFor(() => expect(meApi.changePassword).toHaveBeenCalledWith(expect.objectContaining({ signOutOtherSessions: false })));
  });

  it('새 비밀번호 두 칸이 다르거나 8자보다 짧으면 서버를 부르지 않는다', async () => {
    renderSection();
    fill('old-password-1', 'new-password-2', 'new-password-3');
    submit();
    expect(await screen.findByText('새 비밀번호가 같지 않습니다.')).toBeInTheDocument();
    fill('old-password-1', 'short');
    submit();
    expect(await screen.findByText('8자 이상이어야 합니다.')).toBeInTheDocument();
    expect(meApi.changePassword).not.toHaveBeenCalled();
  });

  it('지금 비밀번호가 틀리면 그 칸 아래에 남은 횟수와 함께 — 로그아웃되지 않는다', async () => {
    vi.mocked(meApi.changePassword).mockRejectedValue(
      new ApiError(400, 'CURRENT_PASSWORD_INVALID', '현재 비밀번호가 맞지 않습니다.', [], { attemptsLeft: 3 }),
    );
    renderSection();
    fill('wrong-password', 'new-password-2');
    submit();
    expect(await screen.findByText(/지금 비밀번호가 맞지 않습니다. 남은 횟수: 3/)).toBeInTheDocument();
    expect(signOut).not.toHaveBeenCalled();
  });

  it('비밀번호 정책 오류는 까닭으로 화면 언어의 문장을 만든다', async () => {
    vi.mocked(meApi.changePassword).mockRejectedValue(
      new ApiError(400, 'PASSWORD_POLICY', '비밀번호는 12자 이상이어야 합니다.', [], { reason: 'MIN_LENGTH', value: 12 }),
    );
    renderSection();
    fill('old-password-1', 'eleven-char');
    submit();
    expect(await screen.findByText('비밀번호는 12자 이상이어야 합니다.')).toBeInTheDocument();
  });

  it('여러 번 틀려 막히면 위에 안내를 보인다', async () => {
    vi.mocked(meApi.changePassword).mockRejectedValue(new ApiError(429, 'RATE_LIMITED', '...'));
    renderSection();
    fill('old-password-1', 'new-password-2');
    submit();
    expect(await screen.findByRole('alert')).toHaveTextContent('지금 비밀번호를 여러 번 틀렸습니다');
  });

  it('서버가 이 로그인까지 끝냈으면 로그아웃하고 로그인 화면으로', async () => {
    vi.mocked(meApi.changePassword).mockResolvedValue({ otherSessionsEnded: true, currentSessionKept: false });
    renderSection();
    fill('old-password-1', 'new-password-2');
    submit();
    expect(await screen.findByText('로그인 화면')).toBeInTheDocument();
    expect(signOut).toHaveBeenCalled();
  });
});
