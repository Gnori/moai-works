import { keepPreviousData, useInfiniteQuery } from '@tanstack/react-query';

import { mcpAccessApi } from '@/shared/api/endpoints';

import { nextAccessCursor, toAccessQuery, toExportQuery, type AccessCursor, type AccessFilters } from './access-log';

/**
 * 어느 기록을 보나 — 워크스페이스(audit.read, 그 워크스페이스에서 쓴 줄) 또는 내 키 하나(그 키의 줄 전부, 워크스페이스가 없는 줄까지).
 * 판정은 api 가 한다.
 */
export type AccessScope = { kind: 'workspace'; slug: string } | { kind: 'key'; keyId: string };

export const mcpAccessKeys = {
  all: ['mcp-access'] as const,
  list: (scope: AccessScope, filters: AccessFilters) =>
    ['mcp-access', scope.kind, scope.kind === 'workspace' ? scope.slug : scope.keyId, filters] as const,
};

/** 새 것부터 50줄씩, "더 보기"는 마지막 줄의 시각 · id 보다 앞의 줄 */
export function useAccessLogQuery(scope: AccessScope, filters: AccessFilters) {
  return useInfiniteQuery({
    queryKey: mcpAccessKeys.list(scope, filters),
    queryFn: ({ pageParam }) =>
      scope.kind === 'workspace'
        ? mcpAccessApi.workspace(scope.slug, toAccessQuery(filters, pageParam))
        : mcpAccessApi.key(scope.keyId, toAccessQuery(filters, pageParam)),
    initialPageParam: undefined as AccessCursor | undefined,
    getNextPageParam: (last) => nextAccessCursor(last),
    // 조건을 바꾸는 동안 앞 목록을 그대로 둔다(깜빡이지 않게)
    placeholderData: keepPreviousData,
  });
}

/** 거른 결과의 CSV 주소 — 링크로 받는다(같은 출처라 세션 쿠키가 함께 간다). 내려받기는 api 가 감사에 남긴다 */
export function accessCsvUrl(scope: AccessScope, filters: AccessFilters): string {
  const query = toExportQuery(filters);
  return scope.kind === 'workspace' ? mcpAccessApi.workspaceCsvUrl(scope.slug, query) : mcpAccessApi.keyCsvUrl(scope.keyId, query);
}
