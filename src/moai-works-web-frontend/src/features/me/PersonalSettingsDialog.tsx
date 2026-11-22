import CloseRounded from '@mui/icons-material/CloseRounded';
import Box from '@mui/material/Box';
import Dialog from '@mui/material/Dialog';
import IconButton from '@mui/material/IconButton';
import List from '@mui/material/List';
import ListItemButton from '@mui/material/ListItemButton';
import ListItemIcon from '@mui/material/ListItemIcon';
import ListItemText from '@mui/material/ListItemText';
import Tab from '@mui/material/Tab';
import Tabs from '@mui/material/Tabs';
import Typography from '@mui/material/Typography';
import useMediaQuery from '@mui/material/useMediaQuery';
import { useTheme } from '@mui/material/styles';
import { Suspense } from 'react';
import { useTranslation } from 'react-i18next';

import { settingsSectionAllowed, settingsSections, type SettingsSection } from '@/app/routes';
import { isSystemAdmin } from '@/features/auth/guards';
import { useMe } from '@/features/auth/session';
import { LoadingState } from '@/shared/ui/States';

import { useSettingsDialog } from './use-settings-dialog';

const TITLE_ID = 'personal-settings-title';

/** 고른 칸의 내용 — 칸의 코드는 처음 열 때 내려받는다(받는 동안 "불러오는 중") */
function SectionContent({ section }: { section: SettingsSection }) {
  return <Suspense fallback={<LoadingState />}>{section.element}</Suspense>;
}

function CloseButton({ onClose }: { onClose: () => void }) {
  const { t } = useTranslation();
  return (
    <IconButton aria-label={t('settingsDialog.close')} onClick={onClose}>
      <CloseRounded />
    </IconButton>
  );
}

/** 넓은 화면 — 왼쪽 세로 메뉴 · 오른쪽 내용 */
function WideLayout({ sections, current, onSelect, onClose }: LayoutProps) {
  const { t } = useTranslation();
  return (
    <Box sx={{ display: 'flex', height: '100%', minHeight: 0 }}>
      <Box
        component="nav"
        aria-label={t('settingsDialog.menu')}
        sx={{ width: 232, flexShrink: 0, borderRight: 1, borderColor: 'divider', bgcolor: 'background.sidebar', py: 2.5, overflowY: 'auto' }}
      >
        <Typography id={TITLE_ID} variant="h6" component="h2" sx={{ px: 2.5, pb: 1.5, fontWeight: 700 }}>
          {t('settingsDialog.title')}
        </Typography>
        <List dense sx={{ px: 1 }}>
          {sections.map((s) => {
            const selected = s.key === current.key;
            return (
              <ListItemButton
                key={s.key}
                selected={selected}
                aria-current={selected ? 'page' : undefined}
                onClick={() => onSelect(s)}
                sx={{
                  borderRadius: 2,
                  mb: 0.25,
                  color: 'text.secondary',
                  '&.Mui-selected': { color: 'primary.main', '& .MuiListItemIcon-root': { color: 'primary.main' } },
                }}
              >
                <ListItemIcon sx={{ minWidth: 34, color: 'inherit' }}>{s.nav.icon}</ListItemIcon>
                <ListItemText primary={t(`nav.${s.nav.label}`)} slotProps={{ primary: { sx: { fontWeight: 600, fontSize: 14 } } }} />
              </ListItemButton>
            );
          })}
        </List>
      </Box>
      <Box sx={{ flex: 1, minWidth: 0, display: 'flex', flexDirection: 'column' }}>
        <Box sx={{ display: 'flex', justifyContent: 'flex-end', px: 1, pt: 1 }}>
          <CloseButton onClose={onClose} />
        </Box>
        <Box sx={{ flex: 1, minHeight: 0, overflowY: 'auto', px: 4, pb: 4 }}>
          <SectionContent section={current} />
        </Box>
      </Box>
    </Box>
  );
}

/** 휴대폰 — 화면을 꽉 채우고, 위에 제목 · 닫기, 그 아래 가로로 밀리는 칸 탭, 그 아래 내용 */
function PhoneLayout({ sections, current, onSelect, onClose }: LayoutProps) {
  const { t } = useTranslation();
  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', height: '100%', minHeight: 0 }}>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, pl: 2, pr: 1, py: 0.5 }}>
        <Typography id={TITLE_ID} variant="h6" component="h2" sx={{ flex: 1, fontWeight: 700 }}>
          {t('settingsDialog.title')}
        </Typography>
        <CloseButton onClose={onClose} />
      </Box>
      <Tabs
        value={current.key}
        onChange={(_, key: string) => {
          const next = sections.find((s) => s.key === key);
          if (next) {
            onSelect(next);
          }
        }}
        variant="scrollable"
        allowScrollButtonsMobile
        aria-label={t('settingsDialog.menu')}
        sx={{ borderBottom: 1, borderColor: 'divider', px: 1, flexShrink: 0 }}
      >
        {sections.map((s) => (
          <Tab key={s.key} value={s.key} label={t(`nav.${s.nav.label}`)} />
        ))}
      </Tabs>
      <Box sx={{ flex: 1, minHeight: 0, overflowY: 'auto', px: 2, py: 2.5 }}>
        <SectionContent section={current} />
      </Box>
    </Box>
  );
}

interface LayoutProps {
  sections: SettingsSection[];
  current: SettingsSection;
  onSelect: (section: SettingsSection) => void;
  onClose: () => void;
}

/**
 * 개인 설정 — 계정 메뉴의 "개인 설정"이 여는 큰 대화상자(왼쪽 메뉴 · 오른쪽 내용). 앱 틀(AppShell)이 늘 품고 있고,
 * 주소의 ?settings=<칸> 이 있으면 지금 화면 위에 열린다(새로 고침 · 뒤로 가기 · 링크에도 같은 칸).
 * 칸: 내 프로필 · 화면 설정 · 알림 설정 · 내 MCP 키 — 시스템 관리자 계정에는 앞의 둘만(서버가 준 플랫폼 역할로 숨기기만).
 * 그 계정에 없는 칸을 주소로 열면 첫 칸을 보인다. 휴대폰에서는 화면을 꽉 채우고 칸을 위의 탭으로 고른다.
 */
export function PersonalSettingsDialog() {
  const me = useMe();
  const theme = useTheme();
  const phone = useMediaQuery(theme.breakpoints.down('sm'));
  const { section, select, close } = useSettingsDialog();
  const sections = settingsSections.filter((s) => settingsSectionAllowed(s, isSystemAdmin(me)));
  const current = sections.find((s) => s.key === section) ?? sections[0];
  const open = section !== null && current !== undefined;
  const props = current && { sections, current, onSelect: (s: SettingsSection) => select(s.key), onClose: close };

  return (
    <Dialog
      open={open}
      onClose={close}
      fullScreen={phone}
      maxWidth={false}
      aria-labelledby={TITLE_ID}
      slotProps={{
        paper: {
          sx: phone ? undefined : { width: 960, maxWidth: 'calc(100% - 64px)', height: 640, maxHeight: 'calc(100% - 64px)', overflow: 'hidden' },
        },
      }}
    >
      {open && props && (phone ? <PhoneLayout {...props} /> : <WideLayout {...props} />)}
    </Dialog>
  );
}
