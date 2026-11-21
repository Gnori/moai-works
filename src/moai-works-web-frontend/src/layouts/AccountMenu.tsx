import LogoutRounded from '@mui/icons-material/LogoutRounded';
import MoreVertRounded from '@mui/icons-material/MoreVertRounded';
import TuneRounded from '@mui/icons-material/TuneRounded';
import Avatar from '@mui/material/Avatar';
import Box from '@mui/material/Box';
import ButtonBase from '@mui/material/ButtonBase';
import Divider from '@mui/material/Divider';
import ListItemIcon from '@mui/material/ListItemIcon';
import Menu from '@mui/material/Menu';
import MenuItem from '@mui/material/MenuItem';
import Typography from '@mui/material/Typography';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router';

import { useMe, useSession } from '@/features/auth/session';
import { initials } from '@/shared/lib/format';

import { PreferencesDialog } from './PreferencesDialog';

/** 사이드바 맨 아래 — 내 계정 · 화면 설정(밝기 · 색 · 언어) · 로그아웃. */
export function AccountMenu() {
  const { t } = useTranslation();
  const me = useMe();
  const { signOut } = useSession();
  const navigate = useNavigate();
  const [anchor, setAnchor] = useState<HTMLElement | null>(null);
  const [preferences, setPreferences] = useState(false);

  const logout = async () => {
    setAnchor(null);
    await signOut();
    navigate('/sign-in', { replace: true });
  };

  return (
    <>
      <ButtonBase
        onClick={(e) => setAnchor(e.currentTarget)}
        aria-haspopup="menu"
        aria-label={t('account.menu')}
        sx={{
          width: '100%',
          justifyContent: 'flex-start',
          gap: 1.25,
          p: 1,
          borderRadius: 2,
          textAlign: 'left',
          '&:hover': { bgcolor: 'action.hover' },
          '&:focus-visible': { outline: 2, outlineColor: 'primary.main', outlineStyle: 'solid' },
        }}
      >
        <Avatar sx={{ width: 34, height: 34, fontSize: 14, fontWeight: 700, bgcolor: 'primary.main', color: 'primary.contrastText' }}>
          {initials(me.displayName)}
        </Avatar>
        <Box sx={{ minWidth: 0, flex: 1 }}>
          <Typography variant="subtitle2" noWrap>
            {me.displayName}
          </Typography>
          <Typography variant="caption" noWrap sx={{ color: 'text.secondary', display: 'block' }}>
            {me.email}
          </Typography>
        </Box>
        <MoreVertRounded fontSize="small" sx={{ color: 'text.secondary' }} />
      </ButtonBase>
      <Menu
        anchorEl={anchor}
        open={anchor !== null}
        onClose={() => setAnchor(null)}
        anchorOrigin={{ vertical: 'top', horizontal: 'left' }}
        transformOrigin={{ vertical: 'bottom', horizontal: 'left' }}
        slotProps={{ paper: { sx: { width: 248 } } }}
      >
        <MenuItem
          onClick={() => {
            setAnchor(null);
            setPreferences(true);
          }}
        >
          <ListItemIcon>
            <TuneRounded fontSize="small" />
          </ListItemIcon>
          {t('account.preferences')}
        </MenuItem>
        <Divider />
        <MenuItem onClick={logout}>
          <ListItemIcon>
            <LogoutRounded fontSize="small" />
          </ListItemIcon>
          {t('account.signOut')}
        </MenuItem>
      </Menu>
      <PreferencesDialog open={preferences} onClose={() => setPreferences(false)} />
    </>
  );
}
