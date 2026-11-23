import { useQuery } from '@tanstack/react-query';
import { Navigate, useLocation, useParams } from 'react-router';

import { issuePath } from '@/app/routes';
import { useWorkspace } from '@/features/workspace/workspace-context';
import { issueApi } from '@/shared/api/endpoints';
import { isApiError } from '@/shared/api/http';
import { ErrorState, LoadingState } from '@/shared/ui/States';

import { withIssueViewTab } from './issue-view-tabs';
import { IssueNotFound } from './IssueLayout';
import { parseIssueNumber } from './queries';

/**
 * 예전 일감 주소 → 정본 주소. 번호가 프로젝트 안 순번이던 때(2026-11-24 전)의 링크(projects/:projectKey/issues/:number · /edit · /history)는
 * 그 프로젝트의 옛 번호다 — 서버에 지금 번호를 묻고(마이그레이션이 남긴 옛 번호 → 새 번호 표) /w/:slug/issues/:number 로 돌린다.
 * 그 뒤의 /edit 은 그대로, /history 는 리비전 탭(?tab=revisions)으로. 주소 값(?tab= · ?rev= …) · #앵커는 그대로 가져간다.
 * 없거나 읽을 수 없으면 서버가 404 — "찾을 수 없습니다"로 보인다.
 */
export function LegacyIssueRedirect() {
  const { workspace } = useWorkspace();
  const { projectKey } = useParams();
  const number = parseIssueNumber(useParams().number);
  const { pathname, search, hash } = useLocation();
  const query = useQuery({
    queryKey: ['issue-legacy-ref', workspace.slug, projectKey ?? '', number ?? 0],
    queryFn: () => issueApi.resolveLegacy(workspace.slug, projectKey ?? '', number ?? 0),
    enabled: !!projectKey && number !== undefined,
    staleTime: Infinity,
    retry: false,
  });

  if (!projectKey || number === undefined || (query.isError && isApiError(query.error) && query.error.status === 404)) {
    return <IssueNotFound />;
  }
  if (query.isPending) {
    return <LoadingState />;
  }
  if (query.isError) {
    return <ErrorState error={query.error} onRetry={() => query.refetch()} />;
  }
  return <Navigate replace to={legacyTarget(workspace.slug, query.data.number, pathname, search, hash)} />;
}

/** 예전 주소의 꼬리(/edit · /history)와 주소 값을 정본 주소로 옮긴다 */
export function legacyTarget(slug: string, number: number, pathname: string, search: string, hash: string): string {
  const base = issuePath(slug, number);
  if (/\/history\/?$/.test(pathname)) {
    const params = withIssueViewTab(new URLSearchParams(search), 'revisions');
    return `${base}?${params.toString()}${hash}`;
  }
  const suffix = /\/edit\/?$/.test(pathname) ? '/edit' : '';
  return `${base}${suffix}${search}${hash}`;
}
