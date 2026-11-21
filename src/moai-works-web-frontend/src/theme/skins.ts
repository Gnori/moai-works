import { neutral } from './tokens';

/**
 * 스킨 = 주 색(primary)과 보조 색(secondary)의 묶음. 중립색 · 상태색은 모든 스킨이 같다(tokens.ts).
 *
 * 규칙 (contrast.test.ts 가 확인한다 — WCAG AA 4.5:1)
 * - 라이트: light = 아주 연한 색조(칩 · 아이콘 바탕), main = 글자 · 단추 바탕, contrastText = 흰색
 * - 다크: main 이 밝은 색, contrastText = 짙은 색. light(색조)는 main 을 20% 로 깐 것(create-theme 이 만든다)
 * - main 은 바탕(종이색)과도, 자기 색조(light) 위 글자로도 4.5:1 이상
 */
export interface Tone {
  light: string;
  main: string;
  dark: string;
  contrastText: string;
}

export interface Skin {
  id: SkinId;
  /** 고르는 화면의 견본 색 */
  swatch: string;
  light: { primary: Tone; secondary: Tone };
  dark: { primary: Omit<Tone, 'light'>; secondary: Omit<Tone, 'light'> };
}

export const skinIds = ['ocean', 'lagoon', 'basalt', 'ember'] as const;
export type SkinId = (typeof skinIds)[number];
export const defaultSkin: SkinId = 'ocean';

const ocean = {
  light: { light: '#DCE6FB', main: '#2B4FB6', dark: '#243F93', contrastText: '#FFFFFF' },
  dark: { main: '#8AA9EC', dark: '#5C84DE', contrastText: neutral[950] },
};
const lagoon = {
  light: { light: '#DDF3EF', main: '#0F6F66', dark: '#0B5650', contrastText: '#FFFFFF' },
  dark: { main: '#4FD1C0', dark: '#2BB3A3', contrastText: neutral[950] },
};
const basalt = {
  light: { light: '#E3E8F0', main: '#3B4559', dark: '#262E3D', contrastText: '#FFFFFF' },
  dark: { main: '#C5CDD9', dark: '#A3AEBF', contrastText: neutral[950] },
};
const ember = {
  light: { light: '#FDEEDC', main: '#A8480C', dark: '#843809', contrastText: '#FFFFFF' },
  dark: { main: '#F5A65B', dark: '#EE8B2E', contrastText: neutral[950] },
};

export const skins: Record<SkinId, Skin> = {
  ocean: {
    id: 'ocean',
    swatch: ocean.light.main,
    light: { primary: ocean.light, secondary: lagoon.light },
    dark: { primary: ocean.dark, secondary: lagoon.dark },
  },
  lagoon: {
    id: 'lagoon',
    swatch: lagoon.light.main,
    light: { primary: lagoon.light, secondary: ocean.light },
    dark: { primary: lagoon.dark, secondary: ocean.dark },
  },
  basalt: {
    id: 'basalt',
    swatch: basalt.light.main,
    light: { primary: basalt.light, secondary: ocean.light },
    dark: { primary: basalt.dark, secondary: ocean.dark },
  },
  ember: {
    id: 'ember',
    swatch: ember.light.main,
    light: { primary: ember.light, secondary: lagoon.light },
    dark: { primary: ember.dark, secondary: lagoon.dark },
  },
};

export function isSkinId(value: string | null | undefined): value is SkinId {
  return !!value && (skinIds as readonly string[]).includes(value);
}
