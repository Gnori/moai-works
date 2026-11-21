import { enUS, koKR } from '@mui/material/locale';
import { alpha, createTheme, type Theme } from '@mui/material/styles';

import type { Language } from '@/shared/i18n/i18n';

import { components } from './components';
import { skins, type SkinId } from './skins';
import { code, neutral as n, radius, status, typography as t } from './tokens';

/** 코드 강조의 글자 색 — 화면은 `code.keyword` 같은 이름으로 쓴다 */
type CodePalette = Record<keyof typeof code.light, string>;

declare module '@mui/material/styles' {
  interface TypeBackground {
    subtle: string;
    sidebar: string;
  }
  interface Palette {
    code: CodePalette;
  }
  interface PaletteOptions {
    code?: CodePalette;
  }
}

const muiLocales = { ko: koKR, en: enUS } as const;

/**
 * theme 만들기 = (스킨, 언어). 라이트 · 다크는 CSS 변수로 함께 들어가고, 고르는 것은 useColorScheme(mode).
 * 화면은 theme 이름만 쓴다 — 스킨을 바꾸면 모든 화면이 따라 바뀐다.
 */
export function createAppTheme(skinId: SkinId, language: Language): Theme {
  const skin = skins[skinId];
  return createTheme(
    {
      cssVariables: { colorSchemeSelector: 'data-color-scheme' },
      colorSchemes: {
        light: {
          palette: {
            primary: skin.light.primary,
            secondary: skin.light.secondary,
            success: status.light.success,
            warning: status.light.warning,
            error: status.light.error,
            info: status.light.info,
            grey: n,
            text: { primary: n[900], secondary: n[600], disabled: n[400] },
            divider: n[200],
            background: { default: n[50], paper: n[0], subtle: n[100], sidebar: n[0] },
            code: code.light,
            action: { hover: alpha(n[500], 0.08), selected: alpha(skin.light.primary.main, 0.1) },
          },
        },
        dark: {
          palette: {
            primary: { ...skin.dark.primary, light: alpha(skin.dark.primary.main, 0.2) },
            secondary: { ...skin.dark.secondary, light: alpha(skin.dark.secondary.main, 0.2) },
            success: { ...status.dark.success, light: alpha(status.dark.success.main, 0.2) },
            warning: { ...status.dark.warning, light: alpha(status.dark.warning.main, 0.2) },
            error: { ...status.dark.error, light: alpha(status.dark.error.main, 0.2) },
            info: { ...status.dark.info, light: alpha(status.dark.info.main, 0.2) },
            grey: n,
            text: { primary: n[50], secondary: n[400], disabled: n[600] },
            divider: alpha(n[400], 0.18),
            background: { default: n[950], paper: n[900], subtle: n[850], sidebar: n[900] },
            code: code.dark,
            action: { hover: alpha(n[300], 0.08), selected: alpha(skin.dark.primary.main, 0.16) },
          },
        },
      },
      shape: { borderRadius: radius.md },
      typography: {
        fontFamily: t.fontFamily,
        fontSize: 14,
        h1: { fontSize: '2rem', fontWeight: 700, letterSpacing: '-0.02em', lineHeight: 1.3 },
        h2: { fontSize: '1.75rem', fontWeight: 700, letterSpacing: '-0.02em', lineHeight: 1.3 },
        h3: { fontSize: '1.5rem', fontWeight: 700, letterSpacing: '-0.015em', lineHeight: 1.35 },
        h4: { fontSize: '1.375rem', fontWeight: 700, letterSpacing: '-0.015em', lineHeight: 1.4 },
        h5: { fontSize: '1.125rem', fontWeight: 700, letterSpacing: '-0.01em', lineHeight: 1.45 },
        h6: { fontSize: '1rem', fontWeight: 600, letterSpacing: '-0.005em', lineHeight: 1.5 },
        subtitle1: { fontSize: '0.9375rem', fontWeight: 600, lineHeight: 1.5 },
        subtitle2: { fontSize: '0.8125rem', fontWeight: 600, lineHeight: 1.5 },
        body1: { fontSize: '0.9375rem', lineHeight: 1.65 },
        body2: { fontSize: '0.875rem', lineHeight: 1.6 },
        caption: { fontSize: '0.75rem', lineHeight: 1.5 },
        overline: { fontSize: '0.6875rem', fontWeight: 700, letterSpacing: '0.08em', lineHeight: 1.6 },
        button: { textTransform: 'none', fontWeight: 600, letterSpacing: 0 },
      },
      components,
    },
    muiLocales[language],
  );
}
