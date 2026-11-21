import Box from '@mui/material/Box';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { useTranslation } from 'react-i18next';

/**
 * moai-works 표시 — 석상 실루엣 + 이름. 색은 theme primary.
 * inverse: 주 색 바탕 위(로그인 화면 왼쪽)에서 쓸 때 — 흰 바탕에 주 색 석상.
 */
export function BrandMark({ size = 32, inverse = false }: { size?: number; inverse?: boolean }) {
  return (
    <Box
      component="svg"
      viewBox="0 0 64 64"
      aria-hidden
      sx={{ width: size, height: size, flexShrink: 0, color: 'primary.main' }}
    >
      <rect width="64" height="64" rx="14" fill={inverse ? 'white' : 'currentColor'} />
      {/* 석상은 주 색 위 글자색(contrastText) — 다크에서 주 색이 밝아지면 석상이 어두워진다 */}
      <Box
        component="path"
        d="M22 50V24c0-6 4.5-11 10-11s10 5 10 11v26h-6V38h-8v12z"
        sx={{ fill: (theme) => (inverse ? 'currentColor' : (theme.vars ?? theme).palette.primary.contrastText) }}
      />
      <rect x="27" y="25" width="10" height="4" rx="2" fill={inverse ? 'white' : 'currentColor'} />
    </Box>
  );
}

export function Brand({ size = 32, caption, inverse = false }: { size?: number; caption?: string; inverse?: boolean }) {
  const { t } = useTranslation();
  return (
    <Stack direction="row" spacing={1.25} sx={{ alignItems: 'center', minWidth: 0 }}>
      <BrandMark size={size} inverse={inverse} />
      <Box sx={{ minWidth: 0 }}>
        <Typography variant="h6" sx={{ lineHeight: 1.2, fontWeight: 700, letterSpacing: '-0.02em' }}>
          {t('app.name')}
        </Typography>
        {caption && (
          <Typography variant="caption" sx={{ color: 'text.secondary', display: 'block', lineHeight: 1.3 }}>
            {caption}
          </Typography>
        )}
      </Box>
    </Stack>
  );
}
