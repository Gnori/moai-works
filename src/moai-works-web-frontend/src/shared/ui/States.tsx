import BlockRounded from '@mui/icons-material/BlockRounded';
import ErrorOutlineRounded from '@mui/icons-material/ErrorOutlineRounded';
import InboxRounded from '@mui/icons-material/InboxRounded';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import CircularProgress from '@mui/material/CircularProgress';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';

import { errorMessage, isApiError } from '@/shared/api/http';

/** 비어 있을 때 — 무엇이 여기 생기는지와 다음 행동을 알려 준다. */
export function EmptyState({ title, description, action, icon }: {
  title: string;
  description?: ReactNode;
  action?: ReactNode;
  icon?: ReactNode;
}) {
  return (
    <Stack spacing={1.5} sx={{ alignItems: 'center', textAlign: 'center', py: 6, px: 3 }}>
      <Box
        sx={{
          width: 48,
          height: 48,
          borderRadius: '50%',
          display: 'grid',
          placeItems: 'center',
          bgcolor: 'background.subtle',
          color: 'text.secondary',
        }}
      >
        {icon ?? <InboxRounded />}
      </Box>
      <Typography variant="subtitle1">{title}</Typography>
      {description && (
        <Typography variant="body2" sx={{ color: 'text.secondary', maxWidth: 420 }}>
          {description}
        </Typography>
      )}
      {action}
    </Stack>
  );
}

/** 불러오는 중 */
export function LoadingState({ label, fullScreen = false }: { label?: string; fullScreen?: boolean }) {
  const { t } = useTranslation();
  return (
    <Stack
      spacing={2}
      role="status"
      aria-live="polite"
      sx={{ alignItems: 'center', justifyContent: 'center', py: 8, minHeight: fullScreen ? '100dvh' : undefined }}
    >
      <CircularProgress size={28} />
      <Typography variant="body2" sx={{ color: 'text.secondary' }}>
        {label ?? t('states.loading')}
      </Typography>
    </Stack>
  );
}

/** 권한 없음 — 다시 시도해도 같으므로 단추 없이 안내만 */
export function ForbiddenState() {
  const { t } = useTranslation();
  return <EmptyState icon={<BlockRounded />} title={t('states.forbiddenTitle')} description={t('states.forbiddenBody')} />;
}

/** 오류 — 무엇이 잘못됐는지와 다시 시도. 403 은 권한 없음 안내. */
export function ErrorState({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  const { t } = useTranslation();
  if (isApiError(error) && error.status === 403) {
    return <ForbiddenState />;
  }
  return (
    <EmptyState
      icon={<ErrorOutlineRounded color="error" />}
      title={t('states.loadFailed')}
      description={errorMessage(error)}
      action={
        onRetry && (
          <Button variant="outlined" onClick={onRetry}>
            {t('actions.retry')}
          </Button>
        )
      }
    />
  );
}
