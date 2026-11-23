import { useInfiniteQuery, useQuery, type QueryClient } from '@tanstack/react-query';

import { skillApi } from '@/shared/api/endpoints';
import type { SkillDetail } from '@/shared/api/types';

/** 스킬 화면이 쓰는 서버 상태의 키 — 모두 'skills' 아래라 쓰고 나면 한 번에 다시 읽는다 */
export const skillKeys = {
  all: ['skills'] as const,
  list: (includeDeleted: boolean) => ['skills', 'list', includeDeleted] as const,
  detail: (name: string) => ['skills', 'detail', name] as const,
  /** 리비전 목록 — 고치면 "현재 리비전" 표시가 바뀌어 다시 읽는다 */
  revisions: (name: string) => ['skills', 'revisions', name] as const,
  revision: (name: string, no: number) => ['skills', 'revision', name, no] as const,
  diff: (name: string, no: number, from: number | undefined) => ['skills', 'diff', name, no, from ?? 'previous'] as const,
};

/** 받은 스킬을 그대로 넣고, 목록 · 리비전은 다시 읽게 한다 */
export function storeSkill(queryClient: QueryClient, skill: SkillDetail) {
  void queryClient.invalidateQueries({ queryKey: skillKeys.all });
  queryClient.setQueryData(skillKeys.detail(skill.name), skill);
}

/** 스킬 목록 — 이름 순. includeDeleted 는 시스템 관리자에게만 서버가 듣는다 */
export function useSkillsQuery(includeDeleted: boolean) {
  return useQuery({ queryKey: skillKeys.list(includeDeleted), queryFn: () => skillApi.list(includeDeleted), staleTime: 15_000 });
}

/** 스킬 하나. 없거나(지운 것을 볼 수 없는 사람에게도) 404 SKILL_NOT_FOUND */
export function useSkillQuery(name: string | undefined) {
  return useQuery({
    queryKey: skillKeys.detail(name ?? ''),
    queryFn: () => skillApi.detail(name ?? ''),
    enabled: !!name,
    staleTime: 15_000,
    retry: false,
  });
}

/** 리비전 목록 — 새 리비전부터, 더 보기 */
export function useSkillRevisionsQuery(name: string, enabled = true) {
  return useInfiniteQuery({
    queryKey: skillKeys.revisions(name),
    queryFn: ({ pageParam }) => skillApi.revisions(name, pageParam),
    initialPageParam: undefined as number | undefined,
    getNextPageParam: (last) => last.next ?? undefined,
    enabled,
    staleTime: 15_000,
  });
}

/** 두 리비전의 차이 — 남긴 리비전은 바뀌지 않는다(지금 리비전 표시만 스킬을 고칠 때 다시 읽는다) */
export function useSkillDiffQuery(name: string, no: number, from: number | undefined, enabled = true) {
  return useQuery({
    queryKey: skillKeys.diff(name, no, from),
    queryFn: () => skillApi.diff(name, no, from),
    enabled,
    staleTime: Infinity,
    retry: false,
  });
}

/** 한 리비전의 전체 — 보일 때만 읽는다 */
export function useSkillRevisionQuery(name: string, no: number, enabled = true) {
  return useQuery({
    queryKey: skillKeys.revision(name, no),
    queryFn: () => skillApi.revision(name, no),
    enabled,
    staleTime: Infinity,
    retry: false,
  });
}
