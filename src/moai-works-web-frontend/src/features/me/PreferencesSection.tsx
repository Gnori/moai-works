import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Box from '@mui/material/Box';
import { useTranslation } from 'react-i18next';

import { PreferencesFields } from '@/layouts/PreferencesDialog';

import { SettingsSectionHeader } from './SettingsSectionHeader';

/**
 * 개인 설정 › 화면 설정 — 밝기 · 색 · 언어. 고르면 바로 바뀌고 이 브라우저에만 기억한다(서버에 보내지 않는다).
 * 개인 설정 대화상자 안의 칸이다(?settings=preferences). 로그인 전 화면은 같은 칸(PreferencesFields)을 대화상자로 연다.
 */
export function PreferencesSection() {
  const { t } = useTranslation();
  return (
    <>
      <SettingsSectionHeader title={t('preferences.title')} description={t('preferences.pageDescription')} />
      <Card>
        <CardContent>
          <Box sx={{ maxWidth: 480 }}>
            <PreferencesFields />
          </Box>
        </CardContent>
      </Card>
    </>
  );
}
