import Box from '@mui/material/Box';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import type { ReactNode } from 'react';

/**
 * 개인 설정 대화상자 안의 칸 머리 — 제목 · 설명 · 오른쪽 동작. 화면 머리(PageHeader)와 같은 모양이고,
 * 대화상자 제목(h2) 아래라 제목은 h3 이다.
 */
export function SettingsSectionHeader({ title, description, actions }: { title: string; description?: ReactNode; actions?: ReactNode }) {
  return (
    <Stack
      direction={{ xs: 'column', sm: 'row' }}
      spacing={2}
      sx={{ alignItems: { xs: 'flex-start', sm: 'flex-end' }, justifyContent: 'space-between', mb: 2.5 }}
    >
      <Box sx={{ minWidth: 0 }}>
        <Typography variant="h5" component="h3" sx={{ fontWeight: 700 }}>
          {title}
        </Typography>
        {description && (
          <Typography variant="body2" sx={{ color: 'text.secondary', mt: 0.75, maxWidth: 640 }}>
            {description}
          </Typography>
        )}
      </Box>
      {actions && (
        <Stack direction="row" spacing={1} sx={{ flexShrink: 0 }}>
          {actions}
        </Stack>
      )}
    </Stack>
  );
}
