import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import CardHeader from '@mui/material/CardHeader';
import Divider from '@mui/material/Divider';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { Fragment } from 'react';
import { useTranslation } from 'react-i18next';

import { fromNow } from '@/shared/lib/format';

import { useInvitationRoleName, useMyInvitationsQuery } from './queries';
import { useInvitationActions } from './use-invitation-actions';

/** 받은 초대 — 링크를 받지 못했어도 내 이메일로 온 초대는 여기서 수락 · 거절한다. 없으면 그리지 않는다. */
export function ReceivedInvitations() {
  const { t } = useTranslation('workspace');
  const mine = useMyInvitationsQuery();
  const roleName = useInvitationRoleName();
  const { accept, decline, busyId } = useInvitationActions();
  const list = mine.data ?? [];

  if (list.length === 0) {
    return null;
  }
  return (
    <Card sx={{ mb: 2, borderColor: 'primary.main' }}>
      <CardHeader title={t('receivedInvitations.title')} subheader={t('receivedInvitations.subtitle')} />
      <CardContent sx={{ pt: 1 }}>
        {list.map((inv, i) => (
          <Fragment key={inv.id}>
            {i > 0 && <Divider sx={{ my: 1.5 }} />}
            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={{ xs: 1.25, sm: 2 }} sx={{ alignItems: { sm: 'center' } }}>
              <Box sx={{ flex: 1, minWidth: 0 }}>
                <Typography variant="subtitle2" noWrap>
                  {inv.workspaceName}
                </Typography>
                <Typography variant="caption" sx={{ color: 'text.secondary', display: 'block' }}>
                  {t('receivedInvitations.from', {
                    name: inv.invitedByName ?? t('receivedInvitations.someone'),
                    role: roleName(inv),
                    time: fromNow(inv.expiresAt),
                  })}
                </Typography>
                {inv.message && (
                  <Typography variant="body2" sx={{ mt: 0.5, fontStyle: 'italic', overflowWrap: 'anywhere' }}>
                    {inv.message}
                  </Typography>
                )}
              </Box>
              <Stack direction="row" spacing={1} sx={{ flexShrink: 0 }}>
                <Button size="small" color="inherit" variant="outlined" disabled={busyId !== undefined} onClick={() => decline(inv)}>
                  {t('receivedInvitations.decline')}
                </Button>
                <Button size="small" variant="contained" loading={busyId === inv.id} disabled={busyId !== undefined && busyId !== inv.id} onClick={() => accept(inv)}>
                  {t('receivedInvitations.accept')}
                </Button>
              </Stack>
            </Stack>
          </Fragment>
        ))}
      </CardContent>
    </Card>
  );
}
