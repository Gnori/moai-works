import ArrowForwardRounded from '@mui/icons-material/ArrowForwardRounded';
import CheckCircleRounded from '@mui/icons-material/CheckCircleRounded';
import GroupsRounded from '@mui/icons-material/GroupsRounded';
import HowToRegRounded from '@mui/icons-material/HowToRegRounded';
import LockRounded from '@mui/icons-material/LockRounded';
import PublicRounded from '@mui/icons-material/PublicRounded';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import CardHeader from '@mui/material/CardHeader';
import Chip from '@mui/material/Chip';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { useQuery } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { Link as RouterLink } from 'react-router';

import { useCan } from '@/features/auth/access';
import { useMe } from '@/features/auth/session';
import { workspaceApi } from '@/shared/api/endpoints';
import { date } from '@/shared/lib/format';
import { permissionGroups, useLabels } from '@/shared/lib/labels';
import { PageHeader } from '@/shared/ui/PageHeader';
import { RoleChips } from '@/shared/ui/RoleChips';
import { VisuallyHidden } from '@/shared/ui/VisuallyHidden';

import { customRoleNames, useRolesQuery, workspaceKeys } from './queries';
import { useWorkspace } from './workspace-context';

function Stat({ icon, label, value, to }: { icon: ReactNode; label: string; value: ReactNode; to?: string }) {
  const { t } = useTranslation();
  return (
    <Card sx={{ height: '100%' }}>
      <CardContent>
        <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center' }}>
          <Box sx={{ width: 40, height: 40, borderRadius: 2, display: 'grid', placeItems: 'center', bgcolor: 'primary.light', color: 'primary.main', flexShrink: 0 }}>
            {icon}
          </Box>
          <Box sx={{ minWidth: 0, flex: 1 }}>
            <Typography variant="caption" sx={{ color: 'text.secondary' }}>
              {label}
            </Typography>
            <Typography variant="h5" sx={{ fontVariantNumeric: 'tabular-nums' }}>
              {value}
            </Typography>
          </Box>
          {to && (
            <Button component={RouterLink} to={to} size="small" endIcon={<ArrowForwardRounded />}>
              {t('actions.view')}
            </Button>
          )}
        </Stack>
      </CardContent>
    </Card>
  );
}

/** 내 권한을 묶음별로 — 서버가 계산한 myPermissions 를 그대로 보인다. */
function MyPermissions() {
  const { t } = useTranslation();
  const labels = useLabels();
  const can = useCan();
  return (
    <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, 1fr)', lg: 'repeat(3, 1fr)' } }}>
      {permissionGroups.map(({ group, codes }) => {
        const granted = codes.filter((code) => can(code));
        return (
          <Box key={group} sx={{ p: 2, borderRadius: 2, bgcolor: 'background.subtle' }}>
            <Typography variant="subtitle2" sx={{ mb: 1 }}>
              {labels.permissionGroup(group)}
              <Typography component="span" variant="caption" sx={{ color: 'text.secondary', ml: 0.75 }}>
                {granted.length}/{codes.length}
              </Typography>
            </Typography>
            <Stack spacing={0.5}>
              {codes.map((code) => {
                const ok = can(code);
                return (
                  <Typography
                    key={code}
                    variant="body2"
                    sx={{ color: ok ? 'text.primary' : 'text.disabled', display: 'flex', gap: 0.75, alignItems: 'center' }}
                  >
                    <Box component="span" aria-hidden sx={{ width: 6, height: 6, borderRadius: '50%', bgcolor: ok ? 'success.main' : 'divider', flexShrink: 0 }} />
                    {labels.permission(code)}
                    <VisuallyHidden>{ok ? t('a11y.granted') : t('a11y.denied')}</VisuallyHidden>
                  </Typography>
                );
              })}
            </Stack>
          </Box>
        );
      })}
    </Box>
  );
}

/** 워크스페이스 홈 — 내 역할과 할 수 있는 일, 멤버 · 가입 신청 요약. 프로젝트 목록은 프로젝트 기능을 만들 때 더한다. */
export function WorkspaceHomePage() {
  const { t } = useTranslation('workspace');
  const { workspace } = useWorkspace();
  const can = useCan();
  const labels = useLabels();
  const me = useMe();
  const canReview = can('member.request.review');
  const pending = useQuery({
    queryKey: workspaceKeys.joinRequests(workspace.slug),
    queryFn: () => workspaceApi.pendingRequests(workspace.slug),
    enabled: canReview,
  });
  const roles = useRolesQuery(workspace.slug);
  const personal = workspace.kind === 'PERSONAL';
  const isPublic = workspace.visibility === 'PUBLIC';

  return (
    <>
      <PageHeader
        eyebrow={personal ? t('home.personalEyebrow') : t('home.teamEyebrow')}
        title={workspace.name}
        description={workspace.description ?? (personal ? t('home.personalDescription', { name: me.displayName }) : undefined)}
        actions={
          <Stack direction="row" spacing={1} useFlexGap sx={{ flexWrap: 'wrap' }}>
            <Chip
              icon={isPublic ? <PublicRounded /> : <LockRounded />}
              label={isPublic ? t('home.public') : t('home.private')}
              variant="outlined"
            />
            {isPublic && <Chip label={labels.joinPolicyShort(workspace.joinPolicy)} variant="outlined" />}
            <Chip label={t('home.address', { slug: workspace.slug })} variant="outlined" sx={{ fontFamily: 'monospace' }} />
          </Stack>
        }
      />

      <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', md: 'repeat(3, 1fr)' }, mb: 2 }}>
        <Stat
          icon={<GroupsRounded />}
          label={t('home.members')}
          value={t('home.memberCount', { count: workspace.memberCount })}
          to={can('member.read') ? 'members' : undefined}
        />
        {canReview ? (
          <Stat
            icon={<HowToRegRounded />}
            label={t('home.pendingRequests')}
            value={pending.data ? t('home.requestCount', { count: pending.data.length }) : '—'}
            to="requests"
          />
        ) : (
          <Stat icon={<HowToRegRounded />} label={t('home.joinPolicy')} value={labels.joinPolicyShort(workspace.joinPolicy)} />
        )}
        <Stat icon={<CheckCircleRounded />} label={t('home.createdAt')} value={date(workspace.createdAt)} />
      </Box>

      <Card>
        <CardHeader title={t('home.myRolesTitle')} subheader={t('home.myRolesSubtitle')} />
        <CardContent>
          <Stack spacing={2.5}>
            <RoleChips roles={workspace.myRoles} customNames={customRoleNames(roles.data)} />
            <MyPermissions />
          </Stack>
        </CardContent>
      </Card>
    </>
  );
}
