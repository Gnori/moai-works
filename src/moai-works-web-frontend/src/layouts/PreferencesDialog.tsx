import CheckRounded from '@mui/icons-material/CheckRounded';
import DarkModeRounded from '@mui/icons-material/DarkModeRounded';
import LightModeRounded from '@mui/icons-material/LightModeRounded';
import SettingsBrightnessRounded from '@mui/icons-material/SettingsBrightnessRounded';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import ButtonBase from '@mui/material/ButtonBase';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import Stack from '@mui/material/Stack';
import ToggleButton from '@mui/material/ToggleButton';
import ToggleButtonGroup from '@mui/material/ToggleButtonGroup';
import Typography from '@mui/material/Typography';
import { useColorScheme } from '@mui/material/styles';
import { useTranslation } from 'react-i18next';

import { changeLanguage, currentLanguage, languages, type Language } from '@/shared/i18n/i18n';
import { useAppearance } from '@/theme/AppearanceProvider';
import { skinIds, skins } from '@/theme/skins';

type Mode = 'light' | 'dark' | 'system';

/** 화면 설정 — 밝기 · 색(스킨) · 언어. 이 브라우저에만 기억한다. 로그인 전 화면에서도 연다. */
export function PreferencesDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  const { t } = useTranslation();
  const { mode, setMode } = useColorScheme();
  const { skin, setSkin } = useAppearance();
  const language = currentLanguage();

  const modes: { value: Mode; label: string; icon: React.ReactNode }[] = [
    { value: 'light', label: t('preferences.modeLight'), icon: <LightModeRounded fontSize="small" /> },
    { value: 'dark', label: t('preferences.modeDark'), icon: <DarkModeRounded fontSize="small" /> },
    { value: 'system', label: t('preferences.modeSystem'), icon: <SettingsBrightnessRounded fontSize="small" /> },
  ];

  return (
    <Dialog open={open} onClose={onClose} maxWidth="xs" fullWidth>
      <DialogTitle sx={{ fontWeight: 700 }}>{t('preferences.title')}</DialogTitle>
      <DialogContent>
        <Stack spacing={3} sx={{ pt: 0.5 }}>
          <Box>
            <Typography variant="subtitle2" sx={{ mb: 1 }} id="pref-mode">
              {t('preferences.mode')}
            </Typography>
            <ToggleButtonGroup
              exclusive
              fullWidth
              size="small"
              value={mode ?? 'system'}
              onChange={(_, value: Mode | null) => value && setMode(value)}
              aria-labelledby="pref-mode"
            >
              {modes.map((m) => (
                <ToggleButton key={m.value} value={m.value} sx={{ gap: 0.75 }}>
                  {m.icon}
                  {m.label}
                </ToggleButton>
              ))}
            </ToggleButtonGroup>
          </Box>

          <Box>
            <Typography variant="subtitle2" sx={{ mb: 1 }} id="pref-skin">
              {t('preferences.skin')}
            </Typography>
            <Box role="radiogroup" aria-labelledby="pref-skin" sx={{ display: 'grid', gap: 1, gridTemplateColumns: 'repeat(4, 1fr)' }}>
              {skinIds.map((id) => {
                const selected = skin === id;
                return (
                  <ButtonBase
                    key={id}
                    role="radio"
                    aria-checked={selected}
                    onClick={() => setSkin(id)}
                    sx={{
                      flexDirection: 'column',
                      gap: 0.75,
                      py: 1.25,
                      borderRadius: 2,
                      border: 2,
                      borderColor: selected ? 'primary.main' : 'divider',
                      '&:focus-visible': { outline: 2, outlineStyle: 'solid', outlineColor: 'primary.main' },
                    }}
                  >
                    <Box
                      sx={{
                        width: 28,
                        height: 28,
                        borderRadius: '50%',
                        bgcolor: skins[id].swatch,
                        display: 'grid',
                        placeItems: 'center',
                        color: 'common.white',
                      }}
                    >
                      {selected && <CheckRounded sx={{ fontSize: 18 }} />}
                    </Box>
                    <Typography variant="caption" sx={{ fontWeight: selected ? 700 : 500 }}>
                      {t(`preferences.skins.${id}`)}
                    </Typography>
                  </ButtonBase>
                );
              })}
            </Box>
          </Box>

          <Box>
            <Typography variant="subtitle2" sx={{ mb: 1 }} id="pref-language">
              {t('preferences.language')}
            </Typography>
            <ToggleButtonGroup
              exclusive
              fullWidth
              size="small"
              value={language}
              onChange={(_, value: Language | null) => value && void changeLanguage(value)}
              aria-labelledby="pref-language"
            >
              {languages.map((lng) => (
                <ToggleButton key={lng} value={lng} lang={lng}>
                  {t(`languages.${lng}`)}
                </ToggleButton>
              ))}
            </ToggleButtonGroup>
          </Box>
        </Stack>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2.5 }}>
        <Button onClick={onClose} variant="contained">
          {t('actions.close')}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
