import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router';
import { toast } from 'sonner';

import { useSession } from '@/features/auth/session';
import { workspaceKeys } from '@/features/workspace/queries';
import { invitationApi } from '@/shared/api/endpoints';
import { errorMessage } from '@/shared/api/http';
import type { Invitation } from '@/shared/api/types';
import { useConfirm } from '@/shared/ui/ConfirmDialog';

/** 링크로 연 초대는 token 으로, 내 초대 목록에서는 id 로 다룬다 */
export interface InvitationRef {
  id: string;
  token?: string;
}

/**
 * 받은 초대에 답하기 — 수락하면 내 정보(워크스페이스 목록)를 다시 받고 그 워크스페이스로 간다.
 * 거절은 확인을 한 번 받는다. 성공 · 실패 모두 관련 목록을 새로 받는다(그사이 바뀌었을 수 있다).
 */
export function useInvitationActions() {
  const { t } = useTranslation('workspace');
  const queryClient = useQueryClient();
  const { refresh } = useSession();
  const navigate = useNavigate();
  const confirm = useConfirm();

  const settle = (ref?: InvitationRef) => {
    void queryClient.invalidateQueries({ queryKey: workspaceKeys.myInvitations });
    void queryClient.invalidateQueries({ queryKey: workspaceKeys.discoverAll });
    void queryClient.invalidateQueries({ queryKey: workspaceKeys.myJoinRequests });
    if (ref?.token) {
      void queryClient.invalidateQueries({ queryKey: workspaceKeys.invitation(ref.token) });
    }
  };

  const accept = useMutation({
    mutationFn: (ref: InvitationRef) => (ref.token ? invitationApi.acceptByToken(ref.token) : invitationApi.accept(ref.id)),
    onSuccess: async (done) => {
      await refresh();
      settle();
      toast.success(t('receivedInvitations.accepted', { name: done.workspaceName }));
      navigate(`/w/${done.workspaceSlug}`, { replace: true });
    },
    onError: (e, ref) => {
      toast.error(errorMessage(e));
      settle(ref);
    },
  });

  const declineMutation = useMutation({
    mutationFn: (ref: InvitationRef) => (ref.token ? invitationApi.declineByToken(ref.token) : invitationApi.decline(ref.id)),
    onSuccess: (_done, ref) => {
      toast.success(t('receivedInvitations.declined'));
      settle(ref);
    },
    onError: (e, ref) => {
      toast.error(errorMessage(e));
      settle(ref);
    },
  });

  const decline = async (invitation: Invitation, token?: string) => {
    const ok = await confirm({
      title: t('receivedInvitations.declineTitle', { name: invitation.workspaceName }),
      description: t('receivedInvitations.declineBody'),
      confirmLabel: t('receivedInvitations.decline'),
      destructive: true,
    });
    if (ok) {
      declineMutation.mutate({ id: invitation.id, token });
    }
  };

  return {
    accept: (invitation: Invitation, token?: string) => accept.mutate({ id: invitation.id, token }),
    decline,
    /** 지금 답하는 중인 초대 id (단추 로딩 표시) */
    busyId: accept.isPending ? accept.variables?.id : declineMutation.isPending ? declineMutation.variables?.id : undefined,
  };
}
