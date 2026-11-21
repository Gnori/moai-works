import HowToRegRounded from '@mui/icons-material/HowToRegRounded';
import Avatar from '@mui/material/Avatar';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import Divider from '@mui/material/Divider';
import MenuItem from '@mui/material/MenuItem';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Fragment, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';

import { workspaceApi } from '@/shared/api/endpoints';
import { errorMessage } from '@/shared/api/http';
import type { JoinRequest } from '@/shared/api/types';
import { dateTime, fromNow, initials } from '@/shared/lib/format';
import { PageHeader } from '@/shared/ui/PageHeader';
import { EmptyState, ErrorState, LoadingState } from '@/shared/ui/States';

import { useRoleText, useRolesQuery, workspaceKeys } from './queries';
import { useWorkspace } from './workspace-context';

type Decision = { request: JoinRequest; kind: 'approve' | 'reject' };

/** 승인(줄 역할 고르기) · 거절(사유) 대화상자 */
function DecisionDialog({ decision, onClose }: { decision: Decision | null; onClose: () => void }) {
  const { t } = useTranslation(['workspace', 'common']);
  const { workspace } = useWorkspace();
  const roles = useRolesQuery(workspace.slug);
  const roleText = useRoleText();
  const queryClient = useQueryClient();
  const [roleKey, setRoleKey] = useState(workspace.defaultRoleKey);
  const [note, setNote] = useState('');

  const mutation = useMutation({
    mutationFn: (d: Decision) =>
      d.kind === 'approve'
        ? workspaceApi.approve(workspace.slug, d.request.id, roleKey, note || undefined)
        : workspaceApi.reject(workspace.slug, d.request.id, note || undefined),
    onSuccess: (_, d) => {
      toast.success(d.kind === 'approve' ? t('requests.approved', { name: d.request.displayName }) : t('requests.rejected'));
      void queryClient.invalidateQueries({ queryKey: workspaceKeys.joinRequests(workspace.slug) });
      void queryClient.invalidateQueries({ queryKey: workspaceKeys.members(workspace.slug) });
      void queryClient.invalidateQueries({ queryKey: workspaceKeys.detail(workspace.slug) });
      onClose();
    },
    onError: (e) => toast.error(errorMessage(e)),
  });

  const approve = decision?.kind === 'approve';
  return (
    <Dialog
      open={decision !== null}
      onClose={onClose}
      maxWidth="xs"
      fullWidth
      slotProps={{
        transition: {
          onEnter: () => {
            setRoleKey(workspace.defaultRoleKey);
            setNote('');
          },
        },
      }}
    >
      {decision && (
        <>
          <DialogTitle sx={{ fontWeight: 700 }}>
            {approve
              ? t('requests.approveTitle', { name: decision.request.displayName })
              : t('requests.rejectTitle', { name: decision.request.displayName })}
          </DialogTitle>
          <DialogContent>
            <Stack spacing={2} sx={{ pt: 1 }}>
              {approve && (
                <TextField select label={t('requests.role')} value={roleKey} onChange={(e) => setRoleKey(e.target.value)}>
                  {(roles.data ?? [])
                    .filter((role) => role.key !== 'owner')
                    .map((role) => (
                      <MenuItem key={role.key} value={role.key}>
                        {roleText.name(role)}
                      </MenuItem>
                    ))}
                </TextField>
              )}
              <TextField
                label={approve ? t('requests.note') : t('requests.reason')}
                value={note}
                onChange={(e) => setNote(e.target.value)}
                multiline
                minRows={2}
                slotProps={{ htmlInput: { maxLength: 500 } }}
                helperText={approve ? undefined : t('requests.rejectHelp')}
              />
            </Stack>
          </DialogContent>
          <DialogActions sx={{ px: 3, pb: 2.5 }}>
            <Button color="inherit" onClick={onClose}>
              {t('common:actions.cancel')}
            </Button>
            <Button
              variant="contained"
              color={approve ? 'primary' : 'error'}
              loading={mutation.isPending}
              onClick={() => mutation.mutate(decision)}
            >
              {approve ? t('requests.approve') : t('requests.reject')}
            </Button>
          </DialogActions>
        </>
      )}
    </Dialog>
  );
}

/** 가입 신청 — 대기 중인 신청 목록. 경로 가드가 member.request.review 를 확인한다(표시만 — 판정은 api). */
export function JoinRequestsPage() {
  const { t } = useTranslation('workspace');
  const { workspace } = useWorkspace();
  const [decision, setDecision] = useState<Decision | null>(null);

  const requests = useQuery({
    queryKey: workspaceKeys.joinRequests(workspace.slug),
    queryFn: () => workspaceApi.pendingRequests(workspace.slug),
  });

  return (
    <>
      <PageHeader eyebrow={workspace.name} title={t('requests.title')} description={t('requests.description')} />
      <Card>
        {requests.isPending ? (
          <LoadingState />
        ) : requests.isError ? (
          <ErrorState error={requests.error} onRetry={() => requests.refetch()} />
        ) : requests.data.length === 0 ? (
          <EmptyState
            icon={<HowToRegRounded />}
            title={t('requests.emptyTitle')}
            description={workspace.visibility === 'PUBLIC' ? t('requests.emptyPublic') : t('requests.emptyPrivate')}
          />
        ) : (
          requests.data.map((r, i) => (
            <Fragment key={r.id}>
              {i > 0 && <Divider />}
              <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ p: 2.5, alignItems: { sm: 'center' } }}>
                <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center', flex: 1, minWidth: 0 }}>
                  <Avatar sx={{ width: 36, height: 36, fontWeight: 700 }}>{initials(r.displayName)}</Avatar>
                  <Box sx={{ minWidth: 0 }}>
                    <Typography variant="subtitle2" sx={{ overflowWrap: 'anywhere' }}>
                      {r.displayName}{' '}
                      <Typography component="span" variant="caption" sx={{ color: 'text.secondary' }}>
                        {r.email}
                      </Typography>
                    </Typography>
                    <Typography variant="body2" sx={{ color: r.message ? 'text.primary' : 'text.disabled', overflowWrap: 'anywhere' }}>
                      {r.message ?? t('requests.noMessage')}
                    </Typography>
                    <Typography variant="caption" sx={{ color: 'text.secondary' }} title={dateTime(r.requestedAt)}>
                      {t('requests.requestedAgo', { time: fromNow(r.requestedAt) })}
                    </Typography>
                  </Box>
                </Stack>
                <Stack direction="row" spacing={1} sx={{ justifyContent: 'flex-end' }}>
                  <Button color="inherit" variant="outlined" onClick={() => setDecision({ request: r, kind: 'reject' })}>
                    {t('requests.reject')}
                  </Button>
                  <Button variant="contained" onClick={() => setDecision({ request: r, kind: 'approve' })}>
                    {t('requests.approve')}
                  </Button>
                </Stack>
              </Stack>
            </Fragment>
          ))
        )}
      </Card>
      <DecisionDialog decision={decision} onClose={() => setDecision(null)} />
    </>
  );
}
