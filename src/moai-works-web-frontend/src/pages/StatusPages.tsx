import AdminPanelSettingsRounded from '@mui/icons-material/AdminPanelSettingsRounded';
import BlockRounded from '@mui/icons-material/BlockRounded';
import SearchOffRounded from '@mui/icons-material/SearchOffRounded';
import Button from '@mui/material/Button';
import { useTranslation } from 'react-i18next';
import { Link as RouterLink, Navigate } from 'react-router';

import { systemHomePath, withSettings, type SettingsSectionKey } from '@/app/routes';
import { landingPath } from '@/features/auth/guards';
import { useMe } from '@/features/auth/session';
import { EmptyState } from '@/shared/ui/States';

/** / → 로그인 뒤 들어갈 워크스페이스 (마지막에 쓴 곳 → 팀 → 개인). 시스템 관리자는 시스템 관리 화면 */
export function RootRedirect() {
  const me = useMe();
  return <Navigate to={landingPath(me)} replace />;
}

/**
 * 예전 개인 설정 주소(/me/…) → 들어갈 곳 위에 그 칸을 연 개인 설정 대화상자.
 * 시스템 관리자 계정에 없는 칸(알림 설정 · 내 MCP 키)이면 대화상자가 첫 칸(내 프로필)을 보인다.
 */
export function SettingsRedirect({ section }: { section: SettingsSectionKey }) {
  const me = useMe();
  return <Navigate to={`${landingPath(me)}${withSettings('', section)}`} replace />;
}

/** 시스템 관리자 계정이 워크스페이스 쪽 주소(워크스페이스 · 찾기 · 만들기 · 초대 링크)를 열었을 때 */
export function SystemAdminNoticePage() {
  const { t } = useTranslation();
  return (
    <EmptyState
      icon={<AdminPanelSettingsRounded />}
      title={t('states.systemAdminTitle')}
      description={t('states.systemAdminBody')}
      action={
        <Button component={RouterLink} to={systemHomePath} variant="contained">
          {t('actions.systemHome')}
        </Button>
      }
    />
  );
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
