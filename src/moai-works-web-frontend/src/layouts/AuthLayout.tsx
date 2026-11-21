import AccountTreeRounded from '@mui/icons-material/AccountTreeRounded';
import LockRounded from '@mui/icons-material/LockRounded';
import TravelExploreRounded from '@mui/icons-material/TravelExploreRounded';
import TuneRounded from '@mui/icons-material/TuneRounded';
import Box from '@mui/material/Box';
import IconButton from '@mui/material/IconButton';
import Stack from '@mui/material/Stack';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import { Suspense, useState, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { Outlet } from 'react-router';

import { Brand } from '@/shared/ui/Brand';
import { LoadingState } from '@/shared/ui/States';

import { PreferencesDialog } from './PreferencesDialog';

/** 로그인 · 가입 화면 — 왼쪽 소개(넓은 화면에서만), 오른쪽 양식. 오른쪽 위에서 화면 설정(밝기 · 색 · 언어). */
export function AuthLayout() {
  const { t } = useTranslation(['auth', 'common']);
  const [preferences, setPreferences] = useState(false);

  const points: { icon: ReactNode; title: string; body: string }[] = [
    { icon: <AccountTreeRounded fontSize="small" />, title: t('layout.pointGrowTitle'), body: t('layout.pointGrowBody') },
    { icon: <TravelExploreRounded fontSize="small" />, title: t('layout.pointCiteTitle'), body: t('layout.pointCiteBody') },
    { icon: <LockRounded fontSize="small" />, title: t('layout.pointSplitTitle'), body: t('layout.pointSplitBody') },
  ];

  return (
    <Box
      sx={{
        minHeight: '100dvh',
        display: 'grid',
        gridTemplateColumns: { xs: '1fr', md: 'minmax(360px, 5fr) 7fr' },
        bgcolor: 'background.default',
      }}
    >
      <Box
        component="aside"
        sx={{
          display: { xs: 'none', md: 'flex' },
          flexDirection: 'column',
          justifyContent: 'space-between',
          p: 6,
          bgcolor: 'primary.dark',
          color: 'primary.contrastText',
          backgroundImage: (theme) =>
            `radial-gradient(120% 80% at 0% 0%, ${(theme.vars ?? theme).palette.primary.main} 0%, transparent 60%)`,
        }}
      >
        <Box sx={{ '& h6': { color: 'inherit' }, '& .MuiTypography-caption': { color: 'inherit', opacity: 0.75 } }}>
          <Brand caption={t('common:app.tagline')} inverse />
        </Box>
        <Stack spacing={3.5} sx={{ maxWidth: 420 }}>
          <Typography variant="h3" sx={{ color: 'inherit', textWrap: 'balance' }}>
            {t('layout.headline')}
          </Typography>
          {points.map((point) => (
            <Stack key={point.title} direction="row" spacing={1.5}>
              <Box
                sx={{
                  width: 32,
                  height: 32,
                  borderRadius: 1,
                  display: 'grid',
                  placeItems: 'center',
                  bgcolor: 'rgba(255,255,255,0.14)',
                  flexShrink: 0,
                }}
              >
                {point.icon}
              </Box>
              <Box>
                <Typography variant="subtitle1" sx={{ color: 'inherit' }}>
                  {point.title}
                </Typography>
                <Typography variant="body2" sx={{ color: 'inherit', opacity: 0.85 }}>
                  {point.body}
                </Typography>
              </Box>
            </Stack>
          ))}
        </Stack>
        <Typography variant="caption" sx={{ opacity: 0.7 }}>
          {t('layout.copyright')}
        </Typography>
      </Box>

      <Box
        component="main"
        sx={{
          position: 'relative',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          px: 2,
          py: { xs: 4, md: 6 },
        }}
      >
        <Tooltip title={t('common:preferences.open')}>
          <IconButton
            aria-label={t('common:preferences.open')}
            onClick={() => setPreferences(true)}
            sx={{ position: 'absolute', top: 12, right: 12 }}
          >
            <TuneRounded />
          </IconButton>
        </Tooltip>
        <Box sx={{ width: '100%', maxWidth: 420 }}>
          <Box sx={{ display: { xs: 'block', md: 'none' }, mb: 4 }}>
            <Brand caption={t('common:app.tagline')} />
          </Box>
          <Suspense fallback={<LoadingState />}>
            <Outlet />
          </Suspense>
        </Box>
      </Box>
      <PreferencesDialog open={preferences} onClose={() => setPreferences(false)} />
    </Box>
  );
}
