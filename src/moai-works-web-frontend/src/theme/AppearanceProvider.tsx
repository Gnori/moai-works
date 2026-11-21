import CssBaseline from '@mui/material/CssBaseline';
import { ThemeProvider } from '@mui/material/styles';
import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';

import { isLanguage, defaultLanguage } from '@/shared/i18n/i18n';
import { readSetting, storageKeys, writeSetting } from '@/shared/lib/storage';

import { createAppTheme } from './create-theme';
import { defaultSkin, isSkinId, type SkinId } from './skins';

interface AppearanceValue {
  skin: SkinId;
  setSkin: (skin: SkinId) => void;
}

const AppearanceContext = createContext<AppearanceValue | null>(null);

/**
 * 화면 설정 — 스킨(색)은 여기서, 밝기(라이트 · 다크 · 시스템)는 MUI useColorScheme 이 정한다.
 * 둘 다 이 브라우저에만 기억한다(localStorage). theme 은 (스킨, 언어)가 바뀔 때만 새로 만든다.
 */
export function AppearanceProvider({ children }: { children: ReactNode }) {
  const { i18n } = useTranslation();
  const [skin, setSkinState] = useState<SkinId>(() => {
    const saved = readSetting(storageKeys.skin);
    return isSkinId(saved) ? saved : defaultSkin;
  });
  const language = isLanguage(i18n.language) ? i18n.language : defaultLanguage;
  const theme = useMemo(() => createAppTheme(skin, language), [skin, language]);

  const setSkin = useCallback((next: SkinId) => {
    writeSetting(storageKeys.skin, next);
    setSkinState(next);
  }, []);

  const value = useMemo(() => ({ skin, setSkin }), [skin, setSkin]);
  return (
    <AppearanceContext.Provider value={value}>
      <ThemeProvider theme={theme} defaultMode="system" noSsr>
        <CssBaseline enableColorScheme />
        {children}
      </ThemeProvider>
    </AppearanceContext.Provider>
  );
}

export function useAppearance(): AppearanceValue {
  const value = useContext(AppearanceContext);
  if (!value) {
    throw new Error('useAppearance 는 AppearanceProvider 안에서만 쓸 수 있습니다');
  }
  return value;
}
