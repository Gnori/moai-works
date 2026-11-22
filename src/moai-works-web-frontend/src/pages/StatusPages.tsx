import BlockRounded from '@mui/icons-material/BlockRounded';
import SearchOffRounded from '@mui/icons-material/SearchOffRounded';
import Button from '@mui/material/Button';
import { useTranslation } from 'react-i18next';
import { Link as RouterLink, Navigate } from 'react-router';

import { landingPath } from '@/features/auth/guards';
import { useMe } from '@/features/auth/session';
import { EmptyState } from '@/shared/ui/States';

/** / → 로그인 뒤 들어갈 워크스페이스 (마지막에 쓴 곳 → 팀 → 개인) */
export function RootRedirect() {
  const me = useMe();
  return <Navigate to={landingPath(me.landingWorkspace, me.platformRoles)} replace />;
}

export function NotFoundPage() {
  const { t } = useTranslation();
  return (
    <EmptyState
      icon={<SearchOffRounded />}
      title={t('states.notFoundTitle')}
      description={t('states.notFoundBody')}
      action={
        <Button component={RouterLink} to="/" variant="contained">
          {t('actions.home')}
        </Button>
      }
    />
  );
}

export function ForbiddenPage() {
  const { t } = useTranslation();
  return (
    <EmptyState
      icon={<BlockRounded />}
      title={t('states.forbiddenPageTitle')}
      description={t('states.forbiddenPageBody')}
      action={
        <Button component={RouterLink} to="/" variant="contained">
          {t('actions.home')}
        </Button>
      }
    />
  );
}
