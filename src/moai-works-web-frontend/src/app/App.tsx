import { MutationCache, QueryCache, QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { RouterProvider } from 'react-router/dom';
import { Toaster } from 'sonner';

import { resetUserData, SessionProvider } from '@/features/auth/session';
import { isApiError } from '@/shared/api/http';
import { ConfirmProvider } from '@/shared/ui/ConfirmDialog';
import { AppearanceProvider } from '@/theme/AppearanceProvider';

import { router } from './router';

/**
 * 세션이 끝났으면(401) 이 사람의 캐시를 비우고 로그인 상태를 null 로 → RequireAuth 가 로그인 화면으로 보낸다
 * (지금 주소를 returnTo 로 달고, 로그인 뒤에는 safeReturnPath 를 통과한 같은 사이트 경로로만 돌아온다).
 * 로그인 실패(INVALID_CREDENTIALS)는 세션과 상관없으니 건드리지 않는다.
 */
function onUnauthenticated(error: unknown) {
  if (isApiError(error) && error.status === 401 && error.code !== 'INVALID_CREDENTIALS') {
    resetUserData(queryClient, null);
  }
}

const queryClient = new QueryClient({
  queryCache: new QueryCache({ onError: onUnauthenticated }),
  mutationCache: new MutationCache({ onError: onUnauthenticated }),
  defaultOptions: {
    queries: {
      retry: (count, error) => !(isApiError(error) && error.status >= 400 && error.status < 500) && count < 2,
      refetchOnWindowFocus: false,
    },
  },
});

/** 공급자 순서: 화면(테마 · 스킨 · 언어별 MUI 문구) → 서버 상태 → 세션 → 확인 창 → 경로 */
export function App() {
  return (
    <AppearanceProvider>
      <QueryClientProvider client={queryClient}>
        <SessionProvider>
          <ConfirmProvider>
            <RouterProvider router={router} />
          </ConfirmProvider>
        </SessionProvider>
      </QueryClientProvider>
      <Toaster position="top-center" richColors closeButton duration={4000} />
    </AppearanceProvider>
  );
}
