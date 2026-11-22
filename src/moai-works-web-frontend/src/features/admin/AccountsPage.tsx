import Avatar from '@mui/material/Avatar';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import Chip from '@mui/material/Chip';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import Stack from '@mui/material/Stack';
import Tab from '@mui/material/Tab';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import Tabs from '@mui/material/Tabs';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import useMediaQuery from '@mui/material/useMediaQuery';
import { useTheme } from '@mui/material/styles';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';

import { useMe } from '@/features/auth/session';
import { adminApi } from '@/shared/api/endpoints';
import { errorMessage } from '@/shared/api/http';
import type { AccountStatus, AccountView } from '@/shared/api/types';
import { date, fromNow, initials } from '@/shared/lib/format';
import { useLabels } from '@/shared/lib/labels';
import { useConfirm } from '@/shared/ui/ConfirmDialog';
import { PageHeader } from '@/shared/ui/PageHeader';
import { EmptyState, ErrorState, LoadingState } from '@/shared/ui/States';

type Filter = 'ALL' | 'PENDING_APPROVAL' | 'ACTIVE' | 'SUSPENDED' | 'PENDING_VERIFICATION';
const filters: Filter[] = ['PENDING_APPROVAL', 'ACTIVE', 'SUSPENDED', 'PENDING_VERIFICATION', 'ALL'];

const statusColor: Record<AccountStatus, 'default' | 'success' | 'warning' | 'error' | 'info'> = {
  PENDING_VERIFICATION: 'info',
  PENDING_APPROVAL: 'warning',
  ACTIVE: 'success',
  REJECTED: 'default',
  SUSPENDED: 'error',
  DELETED: 'default',
};

type Action = 'approve' | 'reject' | 'suspend' | 'resume';
/** 되돌리기 어려운 동작 — 빨간 단추 */
const destructive: Record<Action, boolean> = { approve: false, reject: true, suspend: true, resume: false };

function actionsFor(a: AccountView): Action[] {
  switch (a.status) {
    case 'PENDING_APPROVAL':
      return ['approve', 'reject'];
    case 'ACTIVE':
      return ['suspend'];
    case 'SUSPENDED':
      return ['resume'];
    default:
      return [];
  }
}

/**
 * 계정 관리 (시스템 관리자) — 승인 · 거절 · 정지 · 복구, 그리고 팀 워크스페이스 만들기 허용(계정마다 개수).
 * 워크스페이스 내용은 여기서 볼 수 없다. 경로 가드가 system-admin 을 확인한다(표시만 — 판정은 api).
 */
export function AccountsPage() {
  const { t } = useTranslation('admin');
  const labels = useLabels();
  const me = useMe();
  const confirm = useConfirm();
  const queryClient = useQueryClient();
  const [filter, setFilter] = useState<Filter>('PENDING_APPROVAL');
  const [limitTarget, setLimitTarget] = useState<AccountView | null>(null);
  const accounts = useQuery({
    queryKey: ['admin', 'accounts', filter],
    queryFn: () => adminApi.accounts(filter === 'ALL' ? undefined : filter),
  });

  const run = async (a: AccountView, action: Action) => {
    const label = t(`accounts.actions.${action}.label`);
    const ok = await confirm({
      title: t(`accounts.actions.${action}.title`, { name: a.displayName }),
      description: t(`accounts.actions.${action}.body`),
      confirmLabel: label,
      destructive: destructive[action],
    });
    if (!ok) {
      return;
    }
    try {
      await adminApi.accountAction(a.id, action);
      toast.success(t('accounts.done', { name: a.displayName, action: label }));
      void queryClient.invalidateQueries({ queryKey: ['admin', 'accounts'] });
    } catch (e) {
      toast.error(errorMessage(e));
    }
  };

  return (
    <>
      <PageHeader eyebrow={t('eyebrow')} title={t('accounts.title')} description={t('accounts.description')} />
      <Card>
        <Tabs value={filter} onChange={(_, v: Filter) => setFilter(v)} variant="scrollable" sx={{ px: 1, borderBottom: 1, borderColor: 'divider' }}>
          {filters.map((f) => (
            <Tab key={f} value={f} label={t(`accounts.tabs.${f}`)} />
          ))}
        </Tabs>
        {accounts.isPending ? (
          <LoadingState />
        ) : accounts.isError ? (
          <ErrorState error={accounts.error} onRetry={() => accounts.refetch()} />
        ) : accounts.data.length === 0 ? (
          <EmptyState title={t('accounts.empty')} />
        ) : (
          <TableContainer>
            <Table>
              <TableHead>
                <TableRow>
                  <TableCell>{t('accounts.colAccount')}</TableCell>
                  <TableCell>{t('accounts.colStatus')}</TableCell>
                  <TableCell>{t('accounts.colTeams')}</TableCell>
                  <TableCell sx={{ display: { xs: 'none', md: 'table-cell' } }}>{t('accounts.colSignedUp')}</TableCell>
                  <TableCell sx={{ display: { xs: 'none', md: 'table-cell' } }}>{t('accounts.colLastLogin')}</TableCell>
                  <TableCell align="right" />
                </TableRow>
              </TableHead>
              <TableBody>
                {accounts.data.map((a) => (
                  <TableRow key={a.id} hover>
                    <TableCell>
                      <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center' }}>
                        <Avatar sx={{ width: 32, height: 32, fontSize: 13, fontWeight: 700 }}>{initials(a.displayName)}</Avatar>
                        <Box sx={{ minWidth: 0 }}>
                          <Typography variant="subtitle2" noWrap>
                            {a.displayName}
                            {a.platformRoles.includes('system-admin') && <Chip size="small" label={t('accounts.systemAdmin')} sx={{ ml: 1 }} variant="outlined" />}
                          </Typography>
                          <Typography variant="caption" noWrap sx={{ color: 'text.secondary', display: 'block' }}>
                            {a.username === a.email ? a.email : `${a.username} · ${a.email}`}
                          </Typography>
                        </Box>
                      </Stack>
                    </TableCell>
                    <TableCell>
                      <Chip size="small" color={statusColor[a.status]} variant="outlined" label={labels.accountStatus(a.status)} />
                    </TableCell>
                    <TableCell sx={{ whiteSpace: 'nowrap' }}>
                      {a.platformRoles.includes('system-admin') ? (
                        // 시스템 관리자 계정은 워크스페이스를 만들지도 들어가지도 못한다 — 허용할 것이 없다.
                        // 워크스페이스를 쓰던 계정에 역할을 준 경우에는 남아 있는 멤버십 수를 알린다(정리는 사람이 한다)
                        a.leftoverMemberships > 0 ? (
                          <Chip
                            size="small"
                            color="warning"
                            variant="outlined"
                            label={t('accounts.leftoverMemberships', { count: a.leftoverMemberships })}
                            title={t('accounts.leftoverMembershipsHint')}
                          />
                        ) : (
                          <Typography variant="body2" sx={{ color: 'text.secondary', px: 0.75 }} title={t('accounts.teamsSystemAdminHint')}>
                            {t('accounts.teamsSystemAdmin')}
                          </Typography>
                        )
                      ) : (
                        // 만들 수 있는 수를 누르면 바꾼다 — 활성 계정만
                        <Button
                          size="small"
                          color="inherit"
                          disabled={a.status !== 'ACTIVE'}
                          onClick={() => setLimitTarget(a)}
                          aria-label={`${t('accounts.teamLimit.button')} — ${a.displayName}`}
                          sx={{ color: a.teamWorkspaceLimit > 0 ? 'text.primary' : 'text.secondary' }}
                        >
                          {a.teamWorkspaceLimit > 0
                            ? t('accounts.teamsCount', { created: a.teamWorkspacesCreated, limit: a.teamWorkspaceLimit })
                            : t('accounts.teamsNone')}
                        </Button>
                      )}
                    </TableCell>
                    <TableCell sx={{ display: { xs: 'none', md: 'table-cell' }, color: 'text.secondary' }}>
                      {date(a.signedUpAt)}
                      <Typography variant="caption" sx={{ display: 'block' }}>
                        {a.origin === 'SIGNUP' ? t('accounts.originSignup') : t('accounts.originKeycloak')}
                      </Typography>
                    </TableCell>
                    <TableCell sx={{ display: { xs: 'none', md: 'table-cell' }, color: 'text.secondary' }}>{fromNow(a.lastLoginAt)}</TableCell>
                    <TableCell align="right">
                      <Stack direction="row" spacing={1} sx={{ justifyContent: 'flex-end' }}>
                        {actionsFor(a)
                          .filter((act) => !(act === 'suspend' && a.id === me.id))
                          .map((act) => (
                            <Button
                              key={act}
                              size="small"
                              variant={act === 'approve' || act === 'resume' ? 'contained' : 'outlined'}
                              color={destructive[act] ? 'error' : 'primary'}
                              onClick={() => run(a, act)}
                            >
                              {t(`accounts.actions.${act}.label`)}
                            </Button>
                          ))}
                      </Stack>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </Card>
      {limitTarget && <TeamLimitDialog key={limitTarget.id} account={limitTarget} onClose={() => setLimitTarget(null)} />}
    </>
  );
}

/** 그 계정이 만들 수 있는 팀 워크스페이스 수를 정한다. 지금 0 이면 가입 정책의 기본 개수를 먼저 넣어 둔다. */
function TeamLimitDialog({ account, onClose }: { account: AccountView; onClose: () => void }) {
  const { t } = useTranslation(['admin', 'common']);
  const queryClient = useQueryClient();
  const fullScreen = useMediaQuery(useTheme().breakpoints.down('sm'));
  const policy = useQuery({ queryKey: ['admin', 'signup-policy'], queryFn: adminApi.signupPolicy, staleTime: 60_000 });
  const [typed, setTyped] = useState<string | null>(null);
  const suggested = account.teamWorkspaceLimit > 0 ? account.teamWorkspaceLimit : (policy.data?.teamWorkspaceGrantDefault ?? 2);
  const value = typed ?? String(suggested);
  const limit = Number(value);
  const valid = value.trim() !== '' && Number.isInteger(limit) && limit >= 0 && limit <= 100;

  const save = useMutation({
    mutationFn: () => adminApi.setTeamWorkspaceLimit(account.id, limit),
    onSuccess: () => {
      toast.success(
        limit > 0
          ? t('accounts.teamLimit.saved', { name: account.displayName, limit })
          : t('accounts.teamLimit.savedNone', { name: account.displayName }),
      );
      void queryClient.invalidateQueries({ queryKey: ['admin', 'accounts'] });
      onClose();
    },
    onError: (e) => toast.error(errorMessage(e)),
  });

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="xs" fullScreen={fullScreen}>
      <DialogTitle sx={{ fontWeight: 700 }}>{t('accounts.teamLimit.title', { name: account.displayName })}</DialogTitle>
      <DialogContent>
        <Typography variant="body2" sx={{ color: 'text.secondary', mb: 2 }}>
          {t('accounts.teamLimit.body', { created: account.teamWorkspacesCreated })}
        </Typography>
        <TextField
          autoFocus
          fullWidth
          type="number"
          label={t('accounts.teamLimit.label')}
          value={value}
          onChange={(e) => setTyped(e.target.value)}
          error={!valid}
          helperText={t('accounts.teamLimit.help')}
          slotProps={{ htmlInput: { min: 0, max: 100, inputMode: 'numeric' } }}
        />
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button color="inherit" onClick={onClose}>
          {t('common:actions.cancel')}
        </Button>
        <Button variant="contained" disabled={!valid} loading={save.isPending} onClick={() => save.mutate()}>
          {t('accounts.teamLimit.save')}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
