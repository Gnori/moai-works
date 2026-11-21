import MailOutlineRounded from '@mui/icons-material/MailOutlineRounded';
import Alert from '@mui/material/Alert';
import AlertTitle from '@mui/material/AlertTitle';
import Avatar from '@mui/material/Avatar';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Divider from '@mui/material/Divider';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { useQuery } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { Link as RouterLink, useParams } from 'react-router';

import { useMe, useSession } from '@/features/auth/session';
import { workspaceKeys } from '@/features/workspace/queries';
import { invitationApi } from '@/shared/api/endpoints';
import { isApiError } from '@/shared/api/http';
import type { Invitation } from '@/shared/api/types';
import { dateTime, fromNow, initials } from '@/shared/lib/format';
import { useLabels } from '@/shared/lib/labels';
import { EmptyState, ErrorState, LoadingState } from '@/shared/ui/States';

import { useInvitationRoleName } from './queries';
import { useInvitationActions } from './use-invitation-actions';

function Fact({ label, children }: { label: string; children: ReactNode }) {
  return (
    <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '120px 1fr' }, gap: { xs: 0.25, sm: 2 }, py: 1 }}>
      <Typography variant="body2" sx={{ color: 'text.secondary' }}>
        {label}
      </Typography>
      <Box sx={{ minWidth: 0, overflowWrap: 'anywhere' }}>{children}</Box>
    </Box>
  );
}

/** 초대를 열었는데 쓸 수 없을 때 — 수락됨(이미 멤버면 들어가기) · 거절 · 취소 · 만료 */
function ClosedNotice({ invitation }: { invitation: Invitation }) {
  const { t } = useTranslation('workspace');
  const labels = useLabels();
  const me = useMe();
  const isMember = me.workspaces.some((w) => w.slug === invitation.workspaceSlug);
  if (invitation.status === 'ACCEPTED' && isMember) {
    return (
      <Alert
        severity="success"
        action={
          <Button component={RouterLink} to={`/w/${invitation.workspaceSlug}`} color="inherit" size="small">
            {t('invitation.enter')}
          </Button>
        }
      >
        {t('invitation.acceptedTitle')}
      </Alert>
    );
  }
  return (
    <Alert severity="warning">
      <AlertTitle>{t('invitation.closedTitle')}</AlertTitle>
      {t('invitation.closedBody', { status: labels.invitationStatus(invitation.status) })}
    </Alert>
  );
}

/**
 * 초대 링크(/invitations/:token) — 로그인해야 열린다(안 했으면 로그인 뒤 여기로 돌아온다).
 * 받는 이메일이 지금 계정과 다르면 수락을 막고 다른 계정으로 로그인하게 안내한다(api 도 같은 규칙으로 막는다).
 */
export function InvitationPage() {
  const { t } = useTranslation(['workspace', 'common']);
  const { token = '' } = useParams();
  const me = useMe();
  const { signOut } = useSession();
  const roleName = useInvitationRoleName();
  const { accept, decline, busyId } = useInvitationActions();
  const query = useQuery({
    queryKey: workspaceKeys.invitation(token),
    queryFn: () => invitationApi.preview(token),
    retry: false,
    enabled: token.length > 0,
  });

  let body: ReactNode;
  if (query.isPending) {
    body = <LoadingState />;
  } else if (query.isError) {
    body =
      isApiError(query.error) && query.error.status === 404 ? (
        <EmptyState
          icon={<MailOutlineRounded />}
          title={t('invitation.notFoundTitle')}
          description={t('invitation.notFoundBody')}
          action={
            <Button component={RouterLink} to="/workspaces/discover" variant="outlined">
              {t('invitation.toDiscover')}
            </Button>
          }
        />
      ) : (
        <ErrorState error={query.error} onRetry={() => query.refetch()} />
      );
  } else {
    const inv = query.data;
    const open = inv.status === 'PENDING';
    const mismatch = inv.email.toLowerCase() !== me.email.toLowerCase();
    const isMember = me.workspaces.some((w) => w.slug === inv.workspaceSlug);
    body = (
      <CardContent sx={{ p: { xs: 2.5, sm: 4 } }}>
        <Stack spacing={3}>
          <Stack direction="row" spacing={2} sx={{ alignItems: 'center' }}>
            <Avatar variant="rounded" sx={{ width: 48, height: 48, fontWeight: 700, bgcolor: 'primary.light', color: 'primary.main' }}>
              {initials(inv.workspaceName)}
            </Avatar>
            <Box sx={{ minWidth: 0 }}>
              <Typography variant="overline" sx={{ color: 'text.secondary', display: 'block' }}>
                {t('invitation.eyebrow')}
              </Typography>
              <Typography variant="h5" component="h1" sx={{ textWrap: 'balance', overflowWrap: 'anywhere' }}>
                {t('invitation.title', { name: inv.workspaceName })}
              </Typography>
            </Box>
          </Stack>

          <Box>
            <Fact label={t('invitation.invitedBy')}>{inv.invitedByName ?? t('receivedInvitations.someone')}</Fact>
            <Fact label={t('invitation.role')}>
              <Typography variant="body2" sx={{ fontWeight: 600 }}>
                {roleName(inv)}
              </Typography>
            </Fact>
            <Fact label={t('invitation.email')}>{inv.email}</Fact>
            <Fact label={t('invitation.expires')}>
              {dateTime(inv.expiresAt)}
              <Typography component="span" variant="caption" sx={{ color: 'text.secondary', ml: 1 }}>
                {fromNow(inv.expiresAt)}
              </Typography>
            </Fact>
            {inv.message && (
              <>
                <Divider sx={{ my: 1 }} />
                <Fact label={t('invitation.message')}>
                  <Typography variant="body2" sx={{ whiteSpace: 'pre-wrap' }}>
                    {inv.message}
                  </Typography>
                </Fact>
              </>
            )}
          </Box>

          {!open ? (
            <ClosedNotice invitation={inv} />
          ) : mismatch ? (
            <Alert
              severity="warning"
              action={
                <Button color="inherit" size="small" onClick={() => void signOut()} sx={{ whiteSpace: 'nowrap' }}>
                  {t('invitation.switchAccount')}
                </Button>
              }
              sx={{ flexWrap: { xs: 'wrap', sm: 'nowrap' }, '& .MuiAlert-action': { ml: { xs: 0, sm: 'auto' }, pl: { xs: 0, sm: 2 } } }}
            >
              <AlertTitle>{t('invitation.mismatchTitle')}</AlertTitle>
              {t('invitation.mismatchBody', { invited: inv.email, current: me.email })}
            </Alert>
          ) : (
            isMember && <Alert severity="info">{t('invitation.alreadyMember')}</Alert>
          )}

          {open && (
            <Stack direction={{ xs: 'column-reverse', sm: 'row' }} spacing={1.5} sx={{ justifyContent: 'flex-end' }}>
              <Button
                variant="outlined"
                color="inherit"
                size="large"
                disabled={mismatch || busyId !== undefined}
                onClick={() => decline(inv, token)}
              >
                {t('invitation.decline')}
              </Button>
              <Button
                variant="contained"
                size="large"
                disabled={mismatch || (busyId !== undefined && busyId !== inv.id)}
                loading={busyId === inv.id}
                onClick={() => accept(inv, token)}
              >
                {t('invitation.accept')}
              </Button>
            </Stack>
          )}
        </Stack>
      </CardContent>
    );
  }

  return (
    <Box sx={{ maxWidth: 600, mx: 'auto', pt: { xs: 0, sm: 4 } }}>
      <Card>{body}</Card>
    </Box>
  );
}
