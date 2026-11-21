import type { Components, Theme } from '@mui/material/styles';

import { radius } from './tokens';

/** MUI 컴포넌트 재정의 — 스킨과 무관한 모양(둥글기 · 굵기 · 표면). 색은 theme 변수로만. */
export const components: Components<Theme> = {
  MuiCssBaseline: {
    styleOverrides: {
      body: { WebkitFontSmoothing: 'antialiased' },
      '@media (pointer: coarse)': { 'button, a, [role="button"]': { minHeight: 44 } },
      '@media (prefers-reduced-motion: reduce)': {
        '*, *::before, *::after': { animationDuration: '0.01ms !important', transitionDuration: '0.01ms !important' },
      },
    },
  },
  MuiButton: {
    defaultProps: { disableElevation: true },
    styleOverrides: {
      root: { borderRadius: radius.md, paddingInline: 14 },
      sizeLarge: { minHeight: 46, fontSize: '0.9375rem' },
    },
  },
  MuiIconButton: { styleOverrides: { root: { borderRadius: radius.md } } },
  MuiPaper: { defaultProps: { elevation: 0 }, styleOverrides: { rounded: { borderRadius: radius.lg } } },
  MuiCard: { defaultProps: { variant: 'outlined' }, styleOverrides: { root: { borderRadius: radius.lg } } },
  MuiCardHeader: {
    styleOverrides: {
      root: { padding: '18px 20px 0' },
      title: { fontSize: '1rem', fontWeight: 600 },
      subheader: { fontSize: '0.8125rem' },
    },
  },
  MuiCardContent: { styleOverrides: { root: { padding: 20, '&:last-child': { paddingBottom: 20 } } } },
  MuiTextField: { defaultProps: { fullWidth: true } },
  MuiOutlinedInput: { styleOverrides: { root: { borderRadius: radius.md } } },
  MuiChip: { styleOverrides: { root: { borderRadius: radius.sm, fontWeight: 600 }, sizeSmall: { height: 22 } } },
  MuiTableCell: {
    styleOverrides: {
      head: ({ theme }) => ({
        fontSize: '0.75rem',
        fontWeight: 600,
        color: (theme.vars ?? theme).palette.text.secondary,
        backgroundColor: (theme.vars ?? theme).palette.background.subtle,
        borderBottomColor: (theme.vars ?? theme).palette.divider,
      }),
      root: ({ theme }) => ({ borderBottomColor: (theme.vars ?? theme).palette.divider }),
    },
  },
  MuiDialog: { styleOverrides: { paper: { borderRadius: radius.lg } } },
  MuiTooltip: { defaultProps: { arrow: true } },
  MuiMenu: { styleOverrides: { paper: { borderRadius: radius.md } } },
  /**
   * 알림 상자(standard) — 바탕은 상태색 색조(light), 글은 본문색, 아이콘만 상태색.
   * MUI 기본은 글자색을 light 에서 만들어, 다크의 light(20% 반투명 색조)에서는 글자까지 반투명해진다 — 그래서 직접 정한다.
   * 대비는 contrast.test.ts 가 지킨다.
   */
  MuiAlert: {
    styleOverrides: {
      root: ({ theme, ownerState }) => {
        if ((ownerState.variant ?? 'standard') !== 'standard') {
          return { borderRadius: radius.md };
        }
        const palette = (theme.vars ?? theme).palette;
        const tone = palette[ownerState.color ?? ownerState.severity ?? 'success'];
        return {
          borderRadius: radius.md,
          backgroundColor: tone.light,
          color: palette.text.primary,
          '& .MuiAlert-icon': { color: tone.main },
        };
      },
    },
  },
  MuiToggleButton: { styleOverrides: { root: { textTransform: 'none', fontWeight: 600 } } },
};
