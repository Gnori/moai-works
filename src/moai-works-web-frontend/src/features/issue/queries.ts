import { keepPreviousData, useInfiniteQuery, useQuery, type QueryClient } from '@tanstack/react-query';

import { attachmentApi, issueApi, issueTemplateApi, workspaceApi } from '@/shared/api/endpoints';
import type { IssueDetail, IssueListParams } from '@/shared/api/types';
import { workspaceKeys } from '@/features/workspace/queries';

/**
 * 일감 화면이 쓰는 서버 상태의 키 — 한 곳에서 정해, 무효화할 때 철자가 어긋나지 않게 한다.
 * 일감 번호는 워크스페이스 안에서 하나라(2026-11-24) 일감 하나에 관한 것은 번호로만 가리킨다(프로젝트를 옮겨도 키가 그대로다).
 * 목록은 범위(프로젝트 key, 워크스페이스 전체면 '*')와 조건으로 가른다.
 */
export const issueKeys = {
  /** 이 워크스페이스의 일감에 관한 것 전부 — 일감을 만들거나 고치거나 옮기거나 지운 뒤 한 번에 다시 읽는다(다른 프로젝트의 연결 일감 · 워크스페이스 목록도) */
  all: (slug: string) => ['issues', slug] as const,
  list: (slug: string, scope: string, params: IssueListParams) => ['issues', slug, 'list', scope, params] as const,
  detail: (slug: string, number: number) => ['issues', slug, 'detail', number] as const,
  /** 판 목록 · 판 하나 · 두 판의 차이 — 워크스페이스 키 아래라 일감을 고치면 함께 다시 읽는다(지금 판 표시가 바뀐다) */
  revisions: (slug: string, number: number) => ['issues', slug, 'revisions', number] as const,
  revision: (slug: string, number: number, no: number) => ['issues', slug, 'revision', number, no] as const,
  diff: (slug: string, number: number, no: number, from: number | undefined) =>
    ['issues', slug, 'diff', number, no, from ?? 'previous'] as const,
  /** 일감의 첨부 목록 — 워크스페이스 키 아래라 일감을 고치면 함께 다시 읽는다 */
  attachments: (slug: string, number: number) => ['issues', slug, 'attachments', number] as const,
  /** 일감 유형 — 프로젝트 key, 워크스페이스 전체면 '*' */
  types: (slug: string, scope: string) => ['issue-types', slug, scope] as const,
  /** 쓰인 에이전트 세션 이름들 — 워크스페이스 키 아래라 일감을 만들거나 고치면 함께 다시 읽는다. 범위는 프로젝트 key 또는 '*' */
  agentSessions: (slug: string, scope: string) => ['issues', slug, 'agent-sessions', scope] as const,
  /** 워크스페이스 목록의 모듈 거르기 칸(읽을 수 있는 프로젝트의 모듈 key) */
  workspaceModules: (slug: string) => ['issues', slug, 'modules'] as const,
  /** 유형별 템플릿 — 워크스페이스 기본 · 프로젝트에서 실제로 쓰이는 것. 워크스페이스 기본을 고치면 모든 프로젝트 것을 다시 읽는다 */
  workspaceTemplates: (slug: string) => ['issue-templates', slug] as const,
  projectTemplates: (slug: string, projectKey: string) => ['issue-templates', slug, projectKey] as const,
};

/** 워크스페이스 전체 목록의 범위 이름 (issueKeys.list · types · agentSessions) */
export const WORKSPACE_SCOPE = '*';

/** 일감을 만들거나 고치거나 옮기거나 지운 뒤 — 이 워크스페이스의 목록 · 상세(상위 · 하위 · 연결 쪽도)를 다시 읽는다 */
export function refreshIssues(queryClient: QueryClient, slug: string) {
  return queryClient.invalidateQueries({ queryKey: issueKeys.all(slug) });
}

/** 받은 일감 상세를 그대로 넣고, 그 밖의 것(목록 · 다른 일감의 하위 · 연결)은 다시 읽게 한다 */
export function storeIssue(queryClient: QueryClient, slug: string, issue: IssueDetail) {
  void queryClient.invalidateQueries({ queryKey: issueKeys.all(slug) });
  queryClient.setQueryData(issueKeys.detail(slug, issue.number), issue);
}

/**
 * 일감 목록 — 조건을 바꾸는 동안에는 앞 목록을 그대로 보인다(깜빡이지 않게).
 *
 * @param projectKey 프로젝트 하나의 목록이면 그 key, 워크스페이스 전체(읽을 수 있는 프로젝트 모두)면 null
 */
export function useIssueListQuery(slug: string, projectKey: string | null, params: IssueListParams, enabled = true) {
  return useQuery({
    queryKey: issueKeys.list(slug, projectKey ?? WORKSPACE_SCOPE, params),
    queryFn: () => (projectKey === null ? issueApi.workspaceList(slug, params) : issueApi.list(slug, projectKey, params)),
    enabled,
    staleTime: 15_000,
    placeholderData: keepPreviousData,
  });
}

/** 일감 하나 (하위 · 연결 · 이력 포함) — 워크스페이스 번호로. 없거나 지웠거나 읽을 수 없으면 서버가 404. */
export function useIssueQuery(slug: string, number: number | undefined) {
  return useQuery({
    queryKey: issueKeys.detail(slug, number ?? 0),
    queryFn: () => issueApi.detail(slug, number ?? 0),
    enabled: number !== undefined && Number.isInteger(number) && number > 0,
    staleTime: 15_000,
    retry: false,
  });
}

/** 판 목록 — 새 판부터 한 쪽씩(더 보기). 다음 쪽은 앞 쪽의 next 를 before 로. enabled 가 false 면(리비전 탭을 아직 열지 않음) 읽지 않는다 */
export function useIssueRevisionsQuery(slug: string, number: number, enabled = true) {
  return useInfiniteQuery({
    queryKey: issueKeys.revisions(slug, number),
    queryFn: ({ pageParam }) => issueApi.revisions(slug, number, pageParam),
    initialPageParam: undefined as number | undefined,
    getNextPageParam: (last) => last.next ?? undefined,
    enabled,
    staleTime: 15_000,
  });
}

/** 두 판의 차이 — from 을 비우면 바로 앞 판과. 남긴 판은 바뀌지 않는다(지금 판 표시만 일감을 고칠 때 다시 읽는다) */
export function useIssueDiffQuery(slug: string, number: number, no: number, from: number | undefined, enabled = true) {
  return useQuery({
    queryKey: issueKeys.diff(slug, number, no, from),
    queryFn: () => issueApi.diff(slug, number, no, from),
    enabled,
    staleTime: Infinity,
    retry: false,
  });
}

/** 한 판의 일감 전체 — 보일 때만 읽는다 */
export function useIssueRevisionQuery(slug: string, number: number, no: number, enabled = true) {
  return useQuery({
    queryKey: issueKeys.revision(slug, number, no),
    queryFn: () => issueApi.revision(slug, number, no),
    enabled,
    staleTime: Infinity,
    retry: false,
  });
}

/** 일감의 첨부 목록 (오래된 것부터, 지운 것 빼고) */
export function useAttachmentsQuery(slug: string, number: number) {
  return useQuery({
    queryKey: issueKeys.attachments(slug, number),
    queryFn: () => attachmentApi.list(slug, number),
    staleTime: 15_000,
  });
}

/** 일감 유형 — 프로젝트 key 를 주면 그 프로젝트의 것, null 이면 워크스페이스의 것(워크스페이스 일감 목록) */
export function useIssueTypesQuery(slug: string, projectKey: string | null) {
  return useQuery({
    queryKey: issueKeys.types(slug, projectKey ?? WORKSPACE_SCOPE),
    queryFn: () => (projectKey === null ? issueApi.workspaceTypes(slug) : issueApi.types(slug, projectKey)),
    staleTime: 10 * 60_000,
  });
}

/** 일감에 쓰인 에이전트 세션 이름들(이름순) — 목록 거르기의 고르기 칸. projectKey 가 null 이면 읽을 수 있는 프로젝트 모두에서 */
export function useAgentSessionsQuery(slug: string, projectKey: string | null) {
  return useQuery({
    queryKey: issueKeys.agentSessions(slug, projectKey ?? WORKSPACE_SCOPE),
    queryFn: () => (projectKey === null ? issueApi.workspaceAgentSessions(slug) : issueApi.agentSessions(slug, projectKey)),
    staleTime: 60_000,
  });
}

/** 워크스페이스 일감 목록의 모듈 거르기 칸 — 읽을 수 있는 프로젝트들의 모듈 key */
export function useWorkspaceModulesQuery(slug: string, enabled = true) {
  return useQuery({
    queryKey: issueKeys.workspaceModules(slug),
    queryFn: () => issueApi.workspaceModules(slug),
    enabled,
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
