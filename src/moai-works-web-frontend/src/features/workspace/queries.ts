import { useQuery } from '@tanstack/react-query';
import { useMemo } from 'react';
import { useTranslation } from 'react-i18next';

import { workspaceApi } from '@/shared/api/endpoints';
import type { Role } from '@/shared/api/types';
import { isBuiltinRole } from '@/shared/lib/labels';

import type { AuditFilters } from './audit-log';

/**
 * 워크스페이스 화면이 쓰는 서버 상태의 키 — 한 곳에서 정해, 무효화할 때 철자가 어긋나지 않게 한다.
 */
export const workspaceKeys = {
  detail: (slug: string) => ['workspace', slug] as const,
  members: (slug: string) => ['members', slug] as const,
  roles: (slug: string) => ['roles', slug] as const,
  joinRequests: (slug: string) => ['join-requests', slug] as const,
  /** 이 워크스페이스의 감사 기록 전부 — 무엇을 바꾼 뒤 이 키로 무효화하면 조건마다의 목록이 모두 다시 읽힌다 */
  audit: (slug: string) => ['audit', slug] as const,
  /** 조건 하나의 감사 기록 (더 보기로 쪽을 이어 받는다) */
  auditList: (slug: string, filters: AuditFilters) => ['audit', slug, filters] as const,
  discoverAll: ['discover'] as const,
  discover: (query: string) => ['discover', query] as const,
  myJoinRequests: ['my-join-requests'] as const,
  /** 이 워크스페이스가 보낸 대기 초대 */
  invitations: (slug: string) => ['invitations', slug] as const,
  /** 내가 받은 대기 초대 */
  myInvitations: ['my-invitations'] as const,
  /** 링크로 연 초대 하나 */
  invitation: (token: string) => ['invitation', token] as const,
};

/**
 * 이 워크스페이스의 멤버 — 사람 고르기(감사 기록 · MCP 키의 사람 조건)에 쓴다. member.read 가 있어야 온다.
 * 권한이 없으면 enabled 를 끄고, 화면은 목록 없이(이미 받은 기록 속 사람만으로) 고르게 한다.
 */
export function useMembersQuery(slug: string, enabled = true) {
  return useQuery({
    queryKey: workspaceKeys.members(slug),
    queryFn: () => workspaceApi.members(slug),
    enabled,
    staleTime: 60_000,
    retry: false,
  });
}

/** 이 워크스페이스의 역할 (기본 4개 + 사용자 정의) */
export function useRolesQuery(slug: string) {
  return useQuery({ queryKey: workspaceKeys.roles(slug), queryFn: () => workspaceApi.roles(slug), staleTime: 30_000 });
}

/** 역할의 화면 이름 · 설명 — 기본 역할은 번역, 사용자 정의 역할은 서버가 준 이름 · 설명 */
export function useRoleText() {
  const { t } = useTranslation();
  return useMemo(
    () => ({
      name: (role: Pick<Role, 'key' | 'name'>) => (isBuiltinRole(role.key) ? t(`roles.${role.key}`) : role.name),
      description: (role: Pick<Role, 'key' | 'description'>) =>
        isBuiltinRole(role.key) ? t(`roleDescriptions.${role.key}`) : (role.description ?? ''),
    }),
    [t],
  );
}

/** 사용자 정의 역할 키 → 이름 (RoleChips 의 customNames 로 넘긴다) */
export function customRoleNames(roles: Role[] | undefined): Record<string, string> | undefined {
  if (!roles) {
    return undefined;
  }
  return Object.fromEntries(roles.filter((r) => !isBuiltinRole(r.key)).map((r) => [r.key, r.name]));
}
