import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router';
import { afterEach, describe, expect, it, vi } from 'vitest';

import '@/shared/i18n/i18n';
import { passwordResetApi } from '@/shared/api/endpoints';
import type * as Endpoints from '@/shared/api/endpoints';
import { ApiError } from '@/shared/api/http';
import { AppearanceProvider } from '@/theme/AppearanceProvider';

import { PasswordResetConfirmPage, PasswordResetRequestPage } from './PasswordResetPage';
import { SignInPage } from './SignInPage';

/**
 * 비밀번호 찾기 (FR-WSP-006) — 로그인 화면의 "비밀번호를 잊으셨나요?" → 이메일 → 메일 코드 + 새 비밀번호(두 번) → 로그인 화면.
 * 화면은 계정이 있는지 말하지 않는다("계정이 있으면 보냈습니다").
 */

vi.mock('@/shared/api/endpoints', async (importOriginal) => {
  const actual = await importOriginal<typeof Endpoints>();
  return { ...actual, passwordResetApi: { request: vi.fn(), confirm: vi.fn() } };
});

vi.mock('@/features/auth/session', () => ({
  useSession: () => ({ signIn: vi.fn() }),
}));

function Where() {
  const location = useLocation();
  return <output aria-label="주소">{location.pathname + location.search}</output>;
}

function renderAt(path: string) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <AppearanceProvider>
      <QueryClientProvider client={client}>
        <MemoryRouter initialEntries={[path]}>
          <Routes>
            <Route path="/sign-in" element={<SignInPage />} />
            <Route path="/password-reset" element={<PasswordResetRequestPage />} />
            <Route path="/password-reset/confirm" element={<PasswordResetConfirmPage />} />
          </Routes>
          <Where />
        </MemoryRouter>
      </QueryClientProvider>
    </AppearanceProvider>,
  );
}

const address = () => screen.getByRole('status', { name: '주소' });

afterEach(() => {
  vi.clearAllMocks();
});

describe('비밀번호 찾기', () => {
  it('로그인 화면의 "비밀번호를 잊으셨나요?" 가 이메일 칸으로 간다', async () => {
    renderAt('/sign-in');
    fireEvent.click(screen.getByRole('link', { name: '비밀번호를 잊으셨나요?' }));
    await waitFor(() => expect(address()).toHaveTextContent('/password-reset'));
    expect(screen.getByRole('heading', { name: '비밀번호 찾기', level: 1 })).toBeInTheDocument();
  });

  it('시스템 관리자 계정은 Keycloak 관리자가 재설정한다고 알린다 — 응답은 다른 계정과 같다', () => {
    renderAt('/password-reset');
    expect(screen.getByText('시스템 관리자 계정은 여기서 다시 정할 수 없습니다 — Keycloak 관리자가 재설정합니다.')).toBeInTheDocument();
  });

  it('이메일을 보내면 계정이 있든 없든 코드 칸으로 간다', async () => {
    vi.mocked(passwordResetApi.request).mockResolvedValue(undefined);
    renderAt('/password-reset');
    fireEvent.change(screen.getByLabelText('이메일'), { target: { value: 'kim@example.com' } });
    fireEvent.click(screen.getByRole('button', { name: '코드 보내기' }));
    await waitFor(() => expect(passwordResetApi.request).toHaveBeenCalledWith('kim@example.com'));
    await waitFor(() => expect(address()).toHaveTextContent('/password-reset/confirm?email=kim%40example.com'));
    expect(screen.getByText(/kim@example.com 의 계정이 있으면 6자리 코드를 보냈습니다/)).toBeInTheDocument();
  });

  it('이메일 형식이 아니면 보내지 않는다', async () => {
    renderAt('/password-reset');
    fireEvent.change(screen.getByLabelText('이메일'), { target: { value: 'not-an-email' } });
    fireEvent.click(screen.getByRole('button', { name: '코드 보내기' }));
    expect(await screen.findByText('이메일 형식이 아닙니다.')).toBeInTheDocument();
    expect(passwordResetApi.request).not.toHaveBeenCalled();
  });

  it('코드와 새 비밀번호(두 번)로 다시 정하면 로그인 화면으로', async () => {
    vi.mocked(passwordResetApi.confirm).mockResolvedValue(undefined);
    renderAt('/password-reset/confirm?email=kim%40example.com');
    fireEvent.change(screen.getByLabelText('코드'), { target: { value: '12a3456' } });
    expect(screen.getByLabelText('코드')).toHaveValue('123456');
    fireEvent.change(screen.getByLabelText('새 비밀번호'), { target: { value: 'reset-password-3' } });
    fireEvent.change(screen.getByLabelText('새 비밀번호 확인'), { target: { value: 'reset-password-3' } });
    fireEvent.click(screen.getByRole('button', { name: '비밀번호 다시 정하기' }));
    await waitFor(() =>
      expect(passwordResetApi.confirm).toHaveBeenCalledWith({ email: 'kim@example.com', code: '123456', newPassword: 'reset-password-3' }),
    );
    await waitFor(() => expect(address()).toHaveTextContent(/^\/sign-in$/));
  });

  it('두 칸이 다르면 보내지 않는다', async () => {
    renderAt('/password-reset/confirm?email=kim%40example.com');
    fireEvent.change(screen.getByLabelText('코드'), { target: { value: '123456' } });
    fireEvent.change(screen.getByLabelText('새 비밀번호'), { target: { value: 'reset-password-3' } });
    fireEvent.change(screen.getByLabelText('새 비밀번호 확인'), { target: { value: 'reset-password-4' } });
    fireEvent.click(screen.getByRole('button', { name: '비밀번호 다시 정하기' }));
    expect(await screen.findByText('새 비밀번호가 같지 않습니다.')).toBeInTheDocument();
    expect(passwordResetApi.confirm).not.toHaveBeenCalled();
  });

  it('코드가 틀리거나 만료되면 코드 칸 아래에, 정책 오류는 새 비밀번호 칸 아래에', async () => {
    vi.mocked(passwordResetApi.confirm)
      .mockRejectedValueOnce(new ApiError(400, 'INVALID_CODE', '코드가 맞지 않거나 만료됐습니다.'))
      .mockRejectedValueOnce(new ApiError(400, 'PASSWORD_POLICY', '...', [], { reason: 'HISTORY' }));
    renderAt('/password-reset/confirm?email=kim%40example.com');
    fireEvent.change(screen.getByLabelText('코드'), { target: { value: '000000' } });
    fireEvent.change(screen.getByLabelText('새 비밀번호'), { target: { value: 'reset-password-3' } });
    fireEvent.change(screen.getByLabelText('새 비밀번호 확인'), { target: { value: 'reset-password-3' } });
    fireEvent.click(screen.getByRole('button', { name: '비밀번호 다시 정하기' }));
    expect(await screen.findByText(/코드가 맞지 않거나 만료됐습니다. 코드를 확인하거나/)).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '비밀번호 다시 정하기' }));
    expect(await screen.findByText('최근에 쓴 비밀번호는 다시 쓸 수 없습니다.')).toBeInTheDocument();
    expect(address()).toHaveTextContent('/password-reset/confirm');
  });

  it('코드 다시 받기는 60초 뒤에 — 처음에는 꺼져 있다', () => {
    renderAt('/password-reset/confirm?email=kim%40example.com');
    expect(screen.getByRole('button', { name: /코드 다시 받기 \(60초\)/ })).toBeDisabled();
    expect(screen.getByRole('link', { name: '다른 이메일로' })).toHaveAttribute('href', '/password-reset?email=kim%40example.com');
  });
});
