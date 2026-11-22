import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Box from '@mui/material/Box';
import { useTranslation } from 'react-i18next';

import { PreferencesFields } from '@/layouts/PreferencesDialog';
import { PageHeader } from '@/shared/ui/PageHeader';

/** 개인 설정 · 화면 설정 — 밝기 · 색 · 언어. 고르면 바로 바뀌고 이 브라우저에만 기억한다(서버에 보내지 않는다). */
export function PreferencesPage() {
  const { t } = useTranslation();
  return (
    <>
      <PageHeader eyebrow={t('nav.sectionPersonal')} title={t('preferences.title')} description={t('preferences.pageDescription')} />
      <Card>
        <CardContent>
          <Box sx={{ maxWidth: 560 }}>
            <PreferencesFields />
          </Box>
        </CardContent>
      </Card>
    </>
  );
}
