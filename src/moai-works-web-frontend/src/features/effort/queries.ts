import { keepPreviousData, useQuery } from '@tanstack/react-query';

import { effortApi } from '@/shared/api/endpoints';
import type { EffortQuery } from '@/shared/api/types';

/** 공수 집계의 서버 상태 키 */
export const effortKeys = {
  /** 이 워크스페이스의 공수 집계 전부 */
  all: (slug: string) => ['effort', slug] as const,
  report: (slug: string, query: EffortQuery) => ['effort', slug, query] as const,
};

/** 조건 하나의 집계 — 조건을 바꾸는 동안 앞 결과를 그대로 둔다(깜빡이지 않게) */
export function useEffortReportQuery(slug: string, query: EffortQuery) {
  return useQuery({
    queryKey: effortKeys.report(slug, query),
    queryFn: () => effortApi.report(slug, query),
    placeholderData: keepPreviousData,
    retry: false,
  });
}
