import PauseCircleRounded from '@mui/icons-material/PauseCircleRounded';
import SearchOffRounded from '@mui/icons-material/SearchOffRounded';
import Button from '@mui/material/Button';
import { useQueryClient } from '@tanstack/react-query';
import { Suspense, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { Link as RouterLink, Outlet, useParams } from 'react-router';

import { SESSION_KEY } from '@/features/auth/session';
import { meApi } from '@/shared/api/endpoints';
import { isApiError } from '@/shared/api/http';
import { EmptyState, ErrorState, LoadingState } from '@/shared/ui/States';

import { useWorkspaceQuery, WorkspaceProvider } from './workspace-context';

/**
 * /w/:slug 아래 화면의 틀 — 워크스페이스를 읽고(멤버가 아니면 서버가 404) 문맥으로 내려준다.
 * 들어온 워크스페이스를 기억해 두어 다음 로그인 때 이리로 들어온다.
 * 시스템 관리자가 사용 중지한 워크스페이스면 서버가 멤버에게 403 WORKSPACE_SUSPENDED 를 준다 — 그 아래 어느 화면이든 사용 중지 안내를 보인다.
 */
export function WorkspaceLayout() {
  const { t } = useTranslation('workspace');
  const { slug } = useParams();
  const query = useWorkspaceQuery(slug);
  const queryClient = useQueryClient();
  const suspended = query.isError && isApiError(query.error, 'WORKSPACE_SUSPENDED');

  useEffect(() => {
    if (slug && query.isSuccess) {
      meApi.rememberWorkspace(slug).catch(() => undefined);
    }
  }, [slug, query.isSuccess]);

  // 보고 있는 동안 사용 중지됐을 수 있다 — 내 정보(워크스페이스 전환의 "사용 중지" 표시 · 들어갈 곳)를 다시 받는다
  useEffect(() => {
    if (suspended) {
      void queryClient.invalidateQueries({ queryKey: SESSION_KEY });
    }
  }, [suspended, queryClient]);

  if (query.isPending) {
    return <LoadingState />;
  }
  if (suspended) {
    return (
      <EmptyState
        icon={<PauseCircleRounded />}
        title={t('layout.suspendedTitle')}
        description={t('layout.suspendedBody')}
        action={
          // 들어갈 곳(마지막에 쓴 곳 → 팀 → 개인)은 사용 중지한 워크스페이스를 고르지 않는다
          <Button component={RouterLink} to="/" variant="contained">
            {t('layout.suspendedAction')}
          </Button>
        }
      />
    );
  }
  if (query.isError) {
    if (isApiError(query.error) && query.error.status === 404) {
      return (
        <EmptyState
          icon={<SearchOffRounded />}
          title={t('layout.notFoundTitle')}
          description={t('layout.notFoundBody')}
          action={
            <Button component={RouterLink} to="/workspaces/discover" variant="contained">
              {t('layout.discover')}
            </Button>
          }
        />
      );
    }
    return <ErrorState error={query.error} onRetry={() => query.refetch()} />;
  }
  return (
    <WorkspaceProvider workspace={query.data}>
      {/* 화면 코드는 경로 단위로 나눠 받는다(routes.tsx 의 page) — 받는 동안 워크스페이스 틀은 그대로 둔다 */}
      <Suspense fallback={<LoadingState />}>
        <Outlet />
      </Suspense>
    </WorkspaceProvider>
  );
}
