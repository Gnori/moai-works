import { useQuery } from '@tanstack/react-query';
import { useCallback } from 'react';
import { useTranslation } from 'react-i18next';

import { workspaceKeys } from '@/features/workspace/queries';
import { invitationApi } from '@/shared/api/endpoints';
import type { Invitation } from '@/shared/api/types';
import { isBuiltinRole } from '@/shared/lib/labels';

/** 내가 받은 대기 초대 — 찾기 화면과 워크스페이스 전환 메뉴(알림 표시)가 함께 쓴다 */
export function useMyInvitationsQuery() {
  return useQuery({ queryKey: workspaceKeys.myInvitations, queryFn: invitationApi.mine, staleTime: 60_000 });
}

/** 초대에 적힌 역할 이름 — 기본 역할은 번역, 사용자 정의 역할은 서버가 준 이름 */
export function useInvitationRoleName() {
  const { t } = useTranslation();
  return useCallback(
    (inv: Pick<Invitation, 'roleKey' | 'roleName'>) => (isBuiltinRole(inv.roleKey) ? t(`roles.${inv.roleKey}`) : inv.roleName),
    [t],
  );
}
