import { keepPreviousData, useQuery, type QueryClient } from '@tanstack/react-query';

import { issueApi, workspaceApi } from '@/shared/api/endpoints';
import type { IssueDetail, IssueListParams } from '@/shared/api/types';
import { workspaceKeys } from '@/features/workspace/queries';

/** 일감 화면이 쓰는 서버 상태의 키 — 한 곳에서 정해, 무효화할 때 철자가 어긋나지 않게 한다. */
export const issueKeys = {
  /** 이 프로젝트의 일감에 관한 것 전부 — 일감을 만들거나 고치거나 지운 뒤 한 번에 다시 읽는다 */
  project: (slug: string, projectKey: string) => ['issues', slug, projectKey] as const,
  list: (slug: string, projectKey: string, params: IssueListParams) => ['issues', slug, projectKey, 'list', params] as const,
  detail: (slug: string, projectKey: string, number: number) => ['issues', slug, projectKey, 'detail', number] as const,
  types: (slug: string, projectKey: string) => ['issue-types', slug, projectKey] as const,
};

/** 일감을 만들거나 고치거나 지운 뒤 — 목록 · 상세(상위 · 하위 · 연결 쪽도)를 다시 읽는다 */
export function refreshIssues(queryClient: QueryClient, slug: string, projectKey: string) {
  return queryClient.invalidateQueries({ queryKey: issueKeys.project(slug, projectKey) });
}

/** 받은 일감 상세를 그대로 넣고, 그 밖의 것(목록 · 다른 일감의 하위 · 연결)은 다시 읽게 한다 */
export function storeIssue(queryClient: QueryClient, slug: string, projectKey: string, issue: IssueDetail) {
  void queryClient.invalidateQueries({ queryKey: issueKeys.project(slug, projectKey) });
  queryClient.setQueryData(issueKeys.detail(slug, projectKey, issue.number), issue);
}

/** 일감 목록 — 조건을 바꾸는 동안에는 앞 목록을 그대로 보인다(깜빡이지 않게) */
export function useIssueListQuery(slug: string, projectKey: string, params: IssueListParams, enabled = true) {
  return useQuery({
    queryKey: issueKeys.list(slug, projectKey, params),
    queryFn: () => issueApi.list(slug, projectKey, params),
    enabled,
    staleTime: 15_000,
    placeholderData: keepPreviousData,
  });
}

/** 일감 하나 (하위 · 연결 · 이력 포함). 없거나 지웠으면 서버가 404. */
export function useIssueQuery(slug: string, projectKey: string, number: number | undefined) {
  return useQuery({
    queryKey: issueKeys.detail(slug, projectKey, number ?? 0),
    queryFn: () => issueApi.detail(slug, projectKey, number ?? 0),
    enabled: number !== undefined && Number.isInteger(number) && number > 0,
    staleTime: 15_000,
    retry: false,
  });
}

export function useIssueTypesQuery(slug: string, projectKey: string) {
  return useQuery({
    queryKey: issueKeys.types(slug, projectKey),
    queryFn: () => issueApi.types(slug, projectKey),
    staleTime: 10 * 60_000,
  });
}

/**
 * 관리자 · 담당자로 고를 사람 — 워크스페이스의 활성 멤버(member.read 가 있어야 온다).
 * 그 가운데 이 프로젝트를 읽을 수 없는 사람을 고르면 api 가 거절한다(판정은 api — 화면이 독자 규칙을 다시 계산하지 않는다).
 */
export function usePeopleQuery(slug: string, enabled = true) {
  return useQuery({
    queryKey: workspaceKeys.members(slug),
    queryFn: () => workspaceApi.members(slug),
    enabled,
    staleTime: 60_000,
    retry: false,
  });
}

/** 주소의 일감 번호 — 숫자가 아니면 undefined */
export function parseIssueNumber(value: string | undefined): number | undefined {
  if (!value || !/^\d{1,9}$/.test(value)) {
    return undefined;
  }
  const n = Number(value);
  return n > 0 ? n : undefined;
}
