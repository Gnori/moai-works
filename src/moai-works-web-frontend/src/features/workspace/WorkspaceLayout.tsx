import SearchOffRounded from '@mui/icons-material/SearchOffRounded';
import Button from '@mui/material/Button';
import { Suspense, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { Link as RouterLink, Outlet, useParams } from 'react-router';

import { meApi } from '@/shared/api/endpoints';
import { isApiError } from '@/shared/api/http';
import { EmptyState, ErrorState, LoadingState } from '@/shared/ui/States';

import { useWorkspaceQuery, WorkspaceProvider } from './workspace-context';

/**
 * /w/:slug 아래 화면의 틀 — 워크스페이스를 읽고(멤버가 아니면 서버가 404) 문맥으로 내려준다.
 * 들어온 워크스페이스를 기억해 두어 다음 로그인 때 이리로 들어온다.
 */
export function WorkspaceLayout() {
  const { t } = useTranslation('workspace');
  const { slug } = useParams();
  const query = useWorkspaceQuery(slug);

  useEffect(() => {
    if (slug && query.isSuccess) {
      meApi.rememberWorkspace(slug).catch(() => undefined);
    }
  }, [slug, query.isSuccess]);

  if (query.isPending) {
    return <LoadingState />;
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
