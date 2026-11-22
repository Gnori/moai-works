import { keepPreviousData, useQuery, type QueryClient } from '@tanstack/react-query';

import { workspaceKeys } from '@/features/workspace/queries';
import { mcpKeyApi } from '@/shared/api/endpoints';
import type { McpKeyStatusFilter } from '@/shared/api/types';

/**
 * MCP 키 화면이 쓰는 서버 상태의 키. 캐시에는 키 목록(원문 없음)만 들어간다 —
 * 발급 응답의 원문은 발급 창의 상태에만 두고 쿼리 캐시에 넣지 않는다.
 */
export const mcpKeyKeys = {
  /** 키에 관한 것 전부 — 받거나 만료시킨 뒤 내 키 · 워크스페이스 키를 한 번에 다시 읽는다 */
  all: ['mcp-keys'] as const,
  mine: (status: McpKeyStatusFilter) => ['mcp-keys', 'mine', status] as const,
  workspace: (slug: string, status: McpKeyStatusFilter, owner: string | null) =>
    ['mcp-keys', 'workspace', slug, status, owner ?? 'all'] as const,
};

/** 모든 워크스페이스의 내 키 — 새 것부터 */
export function useMyMcpKeysQuery(status: McpKeyStatusFilter) {
  return useQuery({
    queryKey: mcpKeyKeys.mine(status),
    queryFn: () => mcpKeyApi.mine(status),
    staleTime: 15_000,
    placeholderData: keepPreviousData,
  });
}

/** 이 워크스페이스의 키 — mcp.key.manage 가 있으면 모두(만료된 것까지), 없으면 내 키만 온다(서버가 정한다) */
export function useWorkspaceMcpKeysQuery(slug: string, status: McpKeyStatusFilter, owner: string | null) {
  return useQuery({
    queryKey: mcpKeyKeys.workspace(slug, status, owner),
    queryFn: () => mcpKeyApi.list(slug, { status, owner: owner ?? undefined }),
    staleTime: 15_000,
    placeholderData: keepPreviousData,
  });
}

/** 키를 받거나 만료시킨 뒤 — 키 목록과 그 워크스페이스의 감사 기록을 다시 읽는다 */
export function refreshMcpKeys(queryClient: QueryClient, slug: string) {
  void queryClient.invalidateQueries({ queryKey: mcpKeyKeys.all });
  void queryClient.invalidateQueries({ queryKey: workspaceKeys.audit(slug) });
}
