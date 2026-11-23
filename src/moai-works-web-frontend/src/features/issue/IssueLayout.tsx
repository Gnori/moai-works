import ArrowBackRounded from '@mui/icons-material/ArrowBackRounded';
import SearchOffRounded from '@mui/icons-material/SearchOffRounded';
import Button from '@mui/material/Button';
import { useTranslation } from 'react-i18next';
import { Link as RouterLink, useParams } from 'react-router';

import { workspaceIssuesPath } from '@/app/routes';
import { ProjectFrame } from '@/features/project/ProjectLayout';
import { useWorkspace } from '@/features/workspace/workspace-context';
import { isApiError } from '@/shared/api/http';
import { EmptyState, ErrorState, LoadingState } from '@/shared/ui/States';

import { parseIssueNumber, useIssueQuery } from './queries';

/**
 * 일감 하나의 틀 — 정본 주소 /w/:slug/issues/:number (번호는 워크스페이스 안에서 하나, 2026-11-24 사용자: "워크스페이스 단위로 유니크한 번호여야해").
 * 일감을 읽어 그 일감의 지금 프로젝트로 프로젝트 틀(ProjectFrame — 프로젝트 메뉴 한 줄 · 이 프로젝트의 내 권한)을 그린다.
 * 프로젝트는 일감의 속성이라 다른 프로젝트로 옮기면 받은 상세의 프로젝트가 바뀌고 틀도 따라 바뀐다(주소는 그대로다).
 * 없는 일감 · 지운 일감 · 읽을 수 없는 프로젝트의 일감은 서버가 모두 같은 404 로 답한다 — "찾을 수 없습니다"로 보인다.
 */
export function IssueLayout() {
  const { workspace } = useWorkspace();
  const number = parseIssueNumber(useParams().number);
  const query = useIssueQuery(workspace.slug, number);

  if (number === undefined || (query.isError && isApiError(query.error) && query.error.status === 404)) {
    return <IssueNotFound />;
  }
  if (query.isPending) {
    return <LoadingState />;
  }
  if (query.isError) {
    return <ErrorState error={query.error} onRetry={() => query.refetch()} />;
  }
  return <ProjectFrame projectKey={query.data.project.key} />;
}

/** 일감을 찾을 수 없다 — 워크스페이스의 일감 목록으로 돌아간다 */
export function IssueNotFound() {
  const { t } = useTranslation('issue');
  const { workspace } = useWorkspace();
  return (
    <EmptyState
      icon={<SearchOffRounded />}
      title={t('view.notFoundTitle')}
      description={t('view.notFoundBody')}
      action={
        <Button component={RouterLink} to={workspaceIssuesPath(workspace.slug)} variant="outlined" startIcon={<ArrowBackRounded />}>
          {t('view.backToIssues')}
        </Button>
      }
    />
  );
}
