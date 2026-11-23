import { keepPreviousData, useInfiniteQuery, useQuery, type QueryClient } from '@tanstack/react-query';

import { attachmentApi, issueApi, issueTemplateApi, workspaceApi } from '@/shared/api/endpoints';
import type { IssueDetail, IssueListParams } from '@/shared/api/types';
import { workspaceKeys } from '@/features/workspace/queries';

/** 일감 화면이 쓰는 서버 상태의 키 — 한 곳에서 정해, 무효화할 때 철자가 어긋나지 않게 한다. */
export const issueKeys = {
  /** 이 프로젝트의 일감에 관한 것 전부 — 일감을 만들거나 고치거나 지운 뒤 한 번에 다시 읽는다 */
  project: (slug: string, projectKey: string) => ['issues', slug, projectKey] as const,
  list: (slug: string, projectKey: string, params: IssueListParams) => ['issues', slug, projectKey, 'list', params] as const,
  detail: (slug: string, projectKey: string, number: number) => ['issues', slug, projectKey, 'detail', number] as const,
  /** 판 목록 · 판 하나 · 두 판의 차이 — 프로젝트 키 아래라 일감을 고치면 함께 다시 읽는다(지금 판 표시가 바뀐다) */
  revisions: (slug: string, projectKey: string, number: number) => ['issues', slug, projectKey, 'revisions', number] as const,
  revision: (slug: string, projectKey: string, number: number, no: number) =>
    ['issues', slug, projectKey, 'revision', number, no] as const,
  diff: (slug: string, projectKey: string, number: number, no: number, from: number | undefined) =>
    ['issues', slug, projectKey, 'diff', number, no, from ?? 'previous'] as const,
  /** 일감의 첨부 목록 — 프로젝트 키 아래라 일감을 고치면 함께 다시 읽는다 */
  attachments: (slug: string, projectKey: string, number: number) => ['issues', slug, projectKey, 'attachments', number] as const,
  types: (slug: string, projectKey: string) => ['issue-types', slug, projectKey] as const,
  /** 쓰인 에이전트 세션 이름들 — 프로젝트 키 아래라 일감을 만들거나 고치면 함께 다시 읽는다 */
  agentSessions: (slug: string, projectKey: string) => ['issues', slug, projectKey, 'agent-sessions'] as const,
  /** 유형별 템플릿 — 워크스페이스 기본 · 프로젝트에서 실제로 쓰이는 것. 워크스페이스 기본을 고치면 모든 프로젝트 것을 다시 읽는다 */
  workspaceTemplates: (slug: string) => ['issue-templates', slug] as const,
  projectTemplates: (slug: string, projectKey: string) => ['issue-templates', slug, projectKey] as const,
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

/** 판 목록 — 새 판부터 한 쪽씩(더 보기). 다음 쪽은 앞 쪽의 next 를 before 로. enabled 가 false 면(리비전 탭을 아직 열지 않음) 읽지 않는다 */
export function useIssueRevisionsQuery(slug: string, projectKey: string, number: number, enabled = true) {
  return useInfiniteQuery({
    queryKey: issueKeys.revisions(slug, projectKey, number),
    queryFn: ({ pageParam }) => issueApi.revisions(slug, projectKey, number, pageParam),
    initialPageParam: undefined as number | undefined,
    getNextPageParam: (last) => last.next ?? undefined,
    enabled,
    staleTime: 15_000,
  });
}

/** 두 판의 차이 — from 을 비우면 바로 앞 판과. 남긴 판은 바뀌지 않는다(지금 판 표시만 일감을 고칠 때 다시 읽는다) */
export function useIssueDiffQuery(
  slug: string,
  projectKey: string,
  number: number,
  no: number,
  from: number | undefined,
  enabled = true,
) {
  return useQuery({
    queryKey: issueKeys.diff(slug, projectKey, number, no, from),
    queryFn: () => issueApi.diff(slug, projectKey, number, no, from),
    enabled,
    staleTime: Infinity,
    retry: false,
  });
}

/** 한 판의 일감 전체 — 보일 때만 읽는다 */
export function useIssueRevisionQuery(slug: string, projectKey: string, number: number, no: number, enabled = true) {
  return useQuery({
    queryKey: issueKeys.revision(slug, projectKey, number, no),
    queryFn: () => issueApi.revision(slug, projectKey, number, no),
    enabled,
    staleTime: Infinity,
    retry: false,
  });
}

/** 일감의 첨부 목록 (오래된 것부터, 지운 것 빼고) */
export function useAttachmentsQuery(slug: string, projectKey: string, number: number) {
  return useQuery({
    queryKey: issueKeys.attachments(slug, projectKey, number),
    queryFn: () => attachmentApi.list(slug, projectKey, number),
    staleTime: 15_000,
  });
}

export function useIssueTypesQuery(slug: string, projectKey: string) {
  return useQuery({
    queryKey: issueKeys.types(slug, projectKey),
    queryFn: () => issueApi.types(slug, projectKey),
    staleTime: 10 * 60_000,
  });
}

/** 이 프로젝트의 일감에 쓰인 에이전트 세션 이름들(이름순) — 목록 거르기의 고르기 칸 */
export function useAgentSessionsQuery(slug: string, projectKey: string) {
  return useQuery({
    queryKey: issueKeys.agentSessions(slug, projectKey),
    queryFn: () => issueApi.agentSessions(slug, projectKey),
    staleTime: 60_000,
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

/** 워크스페이스 기본 템플릿(유형마다) */
export function useWorkspaceTemplatesQuery(slug: string, enabled = true) {
  return useQuery({
    queryKey: issueKeys.workspaceTemplates(slug),
    queryFn: () => issueTemplateApi.workspace(slug),
    enabled,
    staleTime: 60_000,
  });
}

/** 이 프로젝트에서 실제로 쓰이는 템플릿(유형마다, 출처 함께) */
export function useProjectTemplatesQuery(slug: string, projectKey: string, enabled = true) {
  return useQuery({
    queryKey: issueKeys.projectTemplates(slug, projectKey),
    queryFn: () => issueTemplateApi.project(slug, projectKey),
    enabled,
    staleTime: 60_000,
  });
}
