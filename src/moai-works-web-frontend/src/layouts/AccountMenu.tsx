import LockRounded from '@mui/icons-material/LockRounded';
import LogoutRounded from '@mui/icons-material/LogoutRounded';
import MoreVertRounded from '@mui/icons-material/MoreVertRounded';
import SettingsRounded from '@mui/icons-material/SettingsRounded';
import Avatar from '@mui/material/Avatar';
import Box from '@mui/material/Box';
import ButtonBase from '@mui/material/ButtonBase';
import Divider from '@mui/material/Divider';
import ListItemIcon from '@mui/material/ListItemIcon';
import Menu from '@mui/material/Menu';
import MenuItem from '@mui/material/MenuItem';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router';

import { useMe, useSession } from '@/features/auth/session';
import { useSettingsDialog } from '@/features/me/use-settings-dialog';
import { initials } from '@/shared/lib/format';

/**
 * 사이드바 맨 아래 — 내 계정 · 개인 설정 · 비밀번호 변경 · 로그아웃.
 * 개인 설정은 지금 화면 위에 대화상자로 연다(?settings=profile — 내 프로필 · 비밀번호 · 화면 설정 · 알림 설정 · 내 MCP 키).
 * 비밀번호 변경은 같은 대화상자의 비밀번호 칸을 바로 연다(?settings=password) — 시스템 관리자 계정도 같은 길로 바꾼다.
 * 로그인 전 화면의 화면 설정은 AuthLayout 의 대화상자를 그대로 쓴다.
 *
 * @param onNavigate 화면을 옮길 때 — 좁은 화면에서 서랍을 닫는다
 */
export function AccountMenu({ onNavigate, compact = false }: { onNavigate?: () => void; compact?: boolean }) {
  const { t } = useTranslation();
  const me = useMe();
  const { signOut } = useSession();
  const navigate = useNavigate();
  const [anchor, setAnchor] = useState<HTMLElement | null>(null);
  const settings = useSettingsDialog();

  const logout = async () => {
    setAnchor(null);
    await signOut();
    navigate('/sign-in', { replace: true });
  };

  const avatar = (
    <Avatar sx={{ width: 34, height: 34, fontSize: 14, fontWeight: 700, bgcolor: 'primary.main', color: 'primary.contrastText' }}>
      {initials(me.displayName)}
    </Avatar>
  );

  return (
    <>
      {compact ? (
        <Tooltip title={`${me.displayName} · ${t('account.menu')}`} placement="right">
          <ButtonBase
            onClick={(e) => setAnchor(e.currentTarget)}
            aria-haspopup="menu"
            aria-label={`${me.displayName} · ${t('account.menu')}`}
            sx={{
              p: 0.5,
              borderRadius: '50%',
              '&:hover': { bgcolor: 'action.hover' },
              '&:focus-visible': { outline: 2, outlineColor: 'primary.main', outlineStyle: 'solid' },
            }}
          >
            {avatar}
          </ButtonBase>
        </Tooltip>
      ) : (
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
        {avatar}
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
      )}
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
            onNavigate?.();
            settings.open('profile');
          }}
        >
          <ListItemIcon>
            <SettingsRounded fontSize="small" />
          </ListItemIcon>
          {t('account.personalSettings')}
        </MenuItem>
        <MenuItem
          onClick={() => {
            setAnchor(null);
            onNavigate?.();
            settings.open('password');
          }}
        >
          <ListItemIcon>
            <LockRounded fontSize="small" />
          </ListItemIcon>
          {t('account.changePassword')}
        </MenuItem>
        <Divider />
        <MenuItem onClick={logout}>
          <ListItemIcon>
            <LogoutRounded fontSize="small" />
          </ListItemIcon>
          {t('account.signOut')}
        </MenuItem>
      </Menu>
    </>
  );
}
