import SearchRounded from '@mui/icons-material/SearchRounded';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import CardHeader from '@mui/material/CardHeader';
import Chip from '@mui/material/Chip';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import Divider from '@mui/material/Divider';
import InputAdornment from '@mui/material/InputAdornment';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Fragment, useDeferredValue, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link as RouterLink } from 'react-router';
import { toast } from 'sonner';

import { useMe, useSession } from '@/features/auth/session';
import { ReceivedInvitations } from '@/features/invitations/ReceivedInvitations';
import { meApi, workspaceApi } from '@/shared/api/endpoints';
import { errorMessage } from '@/shared/api/http';
import type { Discoverable } from '@/shared/api/types';
import { fromNow } from '@/shared/lib/format';
import { useLabels } from '@/shared/lib/labels';
import { PageHeader } from '@/shared/ui/PageHeader';
import { EmptyState, ErrorState, LoadingState } from '@/shared/ui/States';

import { workspaceKeys } from './queries';

/** 가입 신청 대화상자 — 관리자에게 남길 말(선택) */
function ApplyDialog({ target, onClose }: { target: Discoverable | null; onClose: () => void }) {
  const { t } = useTranslation(['workspace', 'common']);
  const queryClient = useQueryClient();
  const { refresh } = useSession();
  const [message, setMessage] = useState('');

  const apply = useMutation({
    mutationFn: (ws: Discoverable) => workspaceApi.apply(ws.slug, message),
    onSuccess: async (request) => {
      onClose();
      if (request.status === 'APPROVED') {
        toast.success(t('discover.joined', { name: request.workspaceName }));
        await refresh();
      } else {
        toast.success(t('discover.applied'));
      }
      void queryClient.invalidateQueries({ queryKey: workspaceKeys.discoverAll });
      void queryClient.invalidateQueries({ queryKey: workspaceKeys.myJoinRequests });
    },
    onError: (e) => toast.error(errorMessage(e)),
  });

  return (
    <Dialog
      open={target !== null}
      onClose={onClose}
      maxWidth="xs"
      fullWidth
      slotProps={{ transition: { onEnter: () => setMessage('') } }}
    >
      {target && (
        <>
          <DialogTitle sx={{ fontWeight: 700 }}>{t('discover.applyTitle', { name: target.name })}</DialogTitle>
          <DialogContent>
            <TextField
              label={t('discover.message')}
              value={message}
              onChange={(e) => setMessage(e.target.value)}
              multiline
              minRows={3}
              sx={{ mt: 1 }}
              slotProps={{ htmlInput: { maxLength: 500 } }}
            />
          </DialogContent>
          <DialogActions sx={{ px: 3, pb: 2.5 }}>
            <Button color="inherit" onClick={onClose}>
              {t('common:actions.cancel')}
            </Button>
            <Button variant="contained" loading={apply.isPending} onClick={() => apply.mutate(target)}>
              {t('discover.submit')}
            </Button>
          </DialogActions>
        </>
      )}
    </Dialog>
  );
}

/** 승인을 기다리는 내 가입 신청 — 철회할 수 있다. */
function MyPendingRequests() {
  const { t } = useTranslation('workspace');
  const labels = useLabels();
  const queryClient = useQueryClient();
  const mine = useQuery({ queryKey: workspaceKeys.myJoinRequests, queryFn: meApi.joinRequests });
  const pending = (mine.data ?? []).filter((r) => r.status === 'PENDING');

  const cancel = async (id: string) => {
    try {
      await meApi.cancelJoinRequest(id);
      toast.success(t('discover.withdrawn'));
      void queryClient.invalidateQueries({ queryKey: workspaceKeys.discoverAll });
      void queryClient.invalidateQueries({ queryKey: workspaceKeys.myJoinRequests });
    } catch (e) {
      toast.error(errorMessage(e));
    }
  };

  if (pending.length === 0) {
    return null;
  }
  return (
    <Card sx={{ mb: 2 }}>
      <CardHeader title={t('discover.myRequests')} subheader={t('discover.myRequestsSubtitle')} />
      <CardContent sx={{ pt: 1 }}>
        {pending.map((r, i) => (
          <Fragment key={r.id}>
            {i > 0 && <Divider sx={{ my: 1 }} />}
            <Stack direction="row" spacing={2} sx={{ alignItems: 'center' }}>
              <Box sx={{ flex: 1, minWidth: 0 }}>
                <Typography variant="subtitle2" noWrap>
                  {r.workspaceName}
                </Typography>
                <Typography variant="caption" sx={{ color: 'text.secondary' }}>
                  {t('discover.requestedAgo', { status: labels.joinRequestStatus(r.status), time: fromNow(r.requestedAt) })}
                </Typography>
              </Box>
              <Button size="small" color="inherit" variant="outlined" onClick={() => cancel(r.id)}>
                {t('discover.withdraw')}
              </Button>
            </Stack>
          </Fragment>
        ))}
      </CardContent>
    </Card>
  );
}

/** 워크스페이스 찾기 — 받은 초대, 내 가입 신청(철회), 공개 팀 워크스페이스 찾기 · 가입 신청. */
export function DiscoverPage() {
  const { t } = useTranslation('workspace');
  const labels = useLabels();
  const [search, setSearch] = useState('');
  const deferred = useDeferredValue(search);
  const [applying, setApplying] = useState<Discoverable | null>(null);
  // 팀 워크스페이스는 시스템 관리자가 허용한 계정만 만든다
  const canCreate = useMe().teamWorkspaceLimit > 0;

  const list = useQuery({ queryKey: workspaceKeys.discover(deferred), queryFn: () => workspaceApi.discover(deferred) });

  return (
    <>
      <PageHeader
        title={t('discover.title')}
        description={t('discover.description')}
        actions={
          canCreate ? (
            <Button component={RouterLink} to="/workspaces/new" variant="contained">
              {t('discover.create')}
            </Button>
          ) : undefined
        }
      />

      <ReceivedInvitations />
      <MyPendingRequests />

      <TextField
        placeholder={t('discover.search')}
        value={search}
        onChange={(e) => setSearch(e.target.value)}
        sx={{ mb: 2, width: '100%', maxWidth: 420 }}
        slotProps={{
          htmlInput: { 'aria-label': t('discover.search') },
          input: { startAdornment: <InputAdornment position="start"><SearchRounded fontSize="small" /></InputAdornment> },
        }}
      />

      {list.isPending ? (
        <LoadingState />
      ) : list.isError ? (
        <ErrorState error={list.error} onRetry={() => list.refetch()} />
      ) : list.data.length === 0 ? (
        <Card>
          <EmptyState title={t('discover.emptyTitle')} description={t('discover.emptyBody')} />
        </Card>
      ) : (
        <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', md: 'repeat(2, 1fr)' } }}>
          {list.data.map((ws) => (
            <Card key={ws.slug}>
              <CardContent>
                <Stack spacing={1.5}>
                  <Stack direction="row" spacing={1} sx={{ alignItems: 'flex-start' }}>
                    <Box sx={{ flex: 1, minWidth: 0 }}>
                      <Typography variant="h6" noWrap>
                        {ws.name}
                      </Typography>
                      <Typography variant="caption" sx={{ color: 'text.secondary' }}>
                        {t('discover.meta', { slug: ws.slug, count: ws.memberCount })}
                      </Typography>
                    </Box>
                    {ws.myStatus === 'MEMBER' && <Chip size="small" color="success" label={t('discover.member')} />}
                    {ws.myStatus === 'PENDING' && <Chip size="small" color="warning" label={t('discover.pending')} />}
                  </Stack>
                  <Typography
                    variant="body2"
                    sx={{ color: ws.description ? 'text.primary' : 'text.disabled', minHeight: 40, overflowWrap: 'anywhere' }}
                  >
                    {ws.description ?? t('discover.noDescription')}
                  </Typography>
                  <Stack direction="row" spacing={1} sx={{ alignItems: 'center', justifyContent: 'space-between' }}>
                    <Typography variant="caption" sx={{ color: 'text.secondary' }}>
                      {labels.joinPolicy(ws.joinPolicy)}
                    </Typography>
                    {ws.myStatus === 'MEMBER' ? (
                      <Button component={RouterLink} to={`/w/${ws.slug}`} size="small">
                        {t('discover.enter')}
                      </Button>
                    ) : ws.myStatus === 'NONE' ? (
                      <Button size="small" variant="contained" onClick={() => setApplying(ws)}>
                        {ws.joinPolicy === 'AUTO' ? t('discover.joinNow') : t('discover.apply')}
                      </Button>
                    ) : null}
                  </Stack>
                </Stack>
              </CardContent>
            </Card>
          ))}
        </Box>
      )}

      <ApplyDialog target={applying} onClose={() => setApplying(null)} />
    </>
  );
}
