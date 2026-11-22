import { useQuery } from '@tanstack/react-query';

import { projectApi } from '@/shared/api/endpoints';
import type { PermissionCode, ProjectDetail } from '@/shared/api/types';

/** 프로젝트 화면이 쓰는 서버 상태의 키 — 한 곳에서 정해, 무효화할 때 철자가 어긋나지 않게 한다. */
export const projectKeys = {
  /** 이 워크스페이스의 프로젝트 목록 전부(쓰는 것 · 보관한 것) — 무효화할 때 쓴다 */
  lists: (slug: string) => ['projects', slug] as const,
  list: (slug: string, archived: boolean) => ['projects', slug, archived ? 'with-archived' : 'active'] as const,
  detail: (slug: string, key: string) => ['project', slug, key] as const,
  roles: (slug: string, key: string) => ['project-roles', slug, key] as const,
};

/**
 * 내가 볼 수 있는 프로젝트 (이름순).
 *
 * @param archived 보관한 프로젝트도 — 프로젝트를 관리할 수 있는 사람에게만 실제로 온다
 */
export function useProjectsQuery(slug: string | undefined, archived = false) {
  return useQuery({
    queryKey: projectKeys.list(slug ?? '', archived),
    queryFn: () => projectApi.list(slug ?? '', archived),
    enabled: !!slug,
    staleTime: 30_000,
  });
}

/** 프로젝트 하나 (내 권한 포함). 독자가 아니면 서버가 404. */
export function useProjectQuery(slug: string, key: string | undefined) {
  return useQuery({
    queryKey: projectKeys.detail(slug, key ?? ''),
    queryFn: () => projectApi.detail(slug, key ?? ''),
    enabled: !!key,
    staleTime: 30_000,
    retry: false,
  });
}

/** 프로젝트 역할을 받은 사람 (이름순) */
export function useProjectRolesQuery(slug: string, key: string) {
  return useQuery({
    queryKey: projectKeys.roles(slug, key),
    queryFn: () => projectApi.roles(slug, key),
    staleTime: 15_000,
  });
}

/** 이 프로젝트에서 내가 할 수 있는 일 — 서버가 준 myPermissions 만 본다(표시만, 판정은 api) */
export function projectCan(project: Pick<ProjectDetail, 'myPermissions'> | undefined, permission: PermissionCode): boolean {
  return project?.myPermissions.includes(permission) ?? false;
}
