import Box from '@mui/material/Box';
import type { ReactNode } from 'react';

/** 화면에는 안 보이고 화면 읽기 프로그램만 읽는 글 (색 · 기호로만 전하는 뜻을 말로 보탤 때) */
export function VisuallyHidden({ children }: { children: ReactNode }) {
  return (
    <Box
      component="span"
      sx={{
        position: 'absolute',
        width: '1px',
        height: '1px',
        p: 0,
        m: '-1px',
        overflow: 'hidden',
        clip: 'rect(0 0 0 0)',
        whiteSpace: 'nowrap',
        border: 0,
      }}
    >
      {children}
    </Box>
  );
}
