import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import Typography from '@mui/material/Typography';
import { useQueryClient, type UseQueryResult } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';

import { workspaceKeys } from '@/features/workspace/queries';
import { useWorkspace } from '@/features/workspace/workspace-context';
import { workspaceApi } from '@/shared/api/endpoints';
import { errorMessage } from '@/shared/api/http';
import type { Invitation } from '@/shared/api/types';
import { dateTime, fromNow } from '@/shared/lib/format';
import { useConfirm } from '@/shared/ui/ConfirmDialog';
import { RoleChips } from '@/shared/ui/RoleChips';
import { EmptyState, ErrorState, LoadingState } from '@/shared/ui/States';

/** 이 워크스페이스가 보낸 대기 초대 — 취소할 수 있다(member.invite). 링크는 다시 보이지 않는다. */
export function SentInvitations({ query, onInvite }: { query: UseQueryResult<Invitation[]>; onInvite?: () => void }) {
  const { t } = useTranslation(['workspace', 'common']);
  const { workspace } = useWorkspace();
  const confirm = useConfirm();
  const queryClient = useQueryClient();

  const revoke = async (inv: Invitation) => {
    const ok = await confirm({
      title: t('sentInvitations.revokeTitle', { email: inv.email }),
      description: t('sentInvitations.revokeBody'),
      confirmLabel: t('sentInvitations.revoke'),
      destructive: true,
    });
    if (!ok) {
      return;
    }
    try {
      await workspaceApi.revokeInvitation(workspace.slug, inv.id);
      toast.success(t('sentInvitations.revoked'));
    } catch (e) {
      toast.error(errorMessage(e));
    } finally {
      void queryClient.invalidateQueries({ queryKey: workspaceKeys.invitations(workspace.slug) });
      void queryClient.invalidateQueries({ queryKey: workspaceKeys.audit(workspace.slug) });
    }
  };

  if (query.isPending) {
    return <LoadingState />;
  }
  if (query.isError) {
    return <ErrorState error={query.error} onRetry={() => query.refetch()} />;
  }
  if (query.data.length === 0) {
    return (
      <EmptyState
        title={t('sentInvitations.emptyTitle')}
        description={t('sentInvitations.emptyBody')}
        action={
          onInvite && (
            <Button variant="outlined" onClick={onInvite}>
              {t('members.invite')}
            </Button>
          )
        }
      />
    );
  }
  return (
    <TableContainer>
      <Table>
        <TableHead>
          <TableRow>
            <TableCell>{t('sentInvitations.colEmail')}</TableCell>
            <TableCell sx={{ display: { xs: 'none', sm: 'table-cell' } }}>{t('sentInvitations.colRole')}</TableCell>
            <TableCell sx={{ display: { xs: 'none', md: 'table-cell' } }}>{t('sentInvitations.colInvitedBy')}</TableCell>
            <TableCell sx={{ display: { xs: 'none', sm: 'table-cell' } }}>{t('sentInvitations.colExpires')}</TableCell>
            <TableCell align="right" />
          </TableRow>
        </TableHead>
        <TableBody>
          {query.data.map((inv) => (
            <TableRow key={inv.id} hover>
              <TableCell sx={{ maxWidth: { xs: 200, sm: 'none' } }}>
                <Typography variant="subtitle2" noWrap title={inv.email}>
                  {inv.email}
                </Typography>
                {/* 좁은 화면에서는 역할 · 만료를 이메일 아래에 */}
                <Box sx={{ display: { xs: 'block', sm: 'none' }, mt: 0.5 }}>
                  <RoleChips roles={[inv.roleKey]} customNames={{ [inv.roleKey]: inv.roleName }} />
                  <Typography variant="caption" sx={{ color: 'text.secondary', display: 'block', mt: 0.5 }}>
                    {t('sentInvitations.expires', { time: fromNow(inv.expiresAt) })}
                  </Typography>
                </Box>
                {inv.message && (
                  <Typography
                    variant="caption"
                    noWrap
                    sx={{ color: 'text.secondary', fontStyle: 'italic', display: { xs: 'none', sm: 'block' } }}
                  >
                    {inv.message}
                  </Typography>
                )}
              </TableCell>
              <TableCell sx={{ display: { xs: 'none', sm: 'table-cell' } }}>
                <RoleChips roles={[inv.roleKey]} customNames={{ [inv.roleKey]: inv.roleName }} />
              </TableCell>
              <TableCell sx={{ display: { xs: 'none', md: 'table-cell' }, color: 'text.secondary' }}>
                {t('sentInvitations.sentAgo', { name: inv.invitedByName ?? '—', time: fromNow(inv.createdAt) })}
              </TableCell>
              <TableCell
                sx={{ display: { xs: 'none', sm: 'table-cell' }, color: 'text.secondary', fontVariantNumeric: 'tabular-nums' }}
                title={dateTime(inv.expiresAt)}
              >
                {fromNow(inv.expiresAt)}
              </TableCell>
              <TableCell align="right">
                <Button size="small" color="error" onClick={() => revoke(inv)} sx={{ whiteSpace: 'nowrap' }}>
                  {t('sentInvitations.revoke')}
                </Button>
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </TableContainer>
  );
}
