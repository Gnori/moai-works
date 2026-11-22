import AddRounded from '@mui/icons-material/AddRounded';
import CheckRounded from '@mui/icons-material/CheckRounded';
import ExploreRounded from '@mui/icons-material/ExploreRounded';
import UnfoldMoreRounded from '@mui/icons-material/UnfoldMoreRounded';
import Avatar from '@mui/material/Avatar';
import Badge from '@mui/material/Badge';
import Box from '@mui/material/Box';
import ButtonBase from '@mui/material/ButtonBase';
import Chip from '@mui/material/Chip';
import Divider from '@mui/material/Divider';
import ListItemIcon from '@mui/material/ListItemIcon';
import ListItemText from '@mui/material/ListItemText';
import ListSubheader from '@mui/material/ListSubheader';
import Menu from '@mui/material/Menu';
import MenuItem from '@mui/material/MenuItem';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router';

import { useMe } from '@/features/auth/session';
import { useMyInvitationsQuery } from '@/features/invitations/queries';
import type { WorkspaceSummary } from '@/shared/api/types';
import { initials } from '@/shared/lib/format';
import { useRoleName } from '@/shared/lib/labels';

function WorkspaceAvatar({ ws, size = 32 }: { ws: Pick<WorkspaceSummary, 'name' | 'kind'>; size?: number }) {
  return (
    <Avatar
      variant="rounded"
      sx={{
        width: size,
        height: size,
        fontSize: size * 0.45,
        fontWeight: 700,
        bgcolor: ws.kind === 'PERSONAL' ? 'secondary.light' : 'primary.light',
        color: ws.kind === 'PERSONAL' ? 'secondary.main' : 'primary.main',
      }}
    >
      {initials(ws.name)}
    </Avatar>
  );
}

/** 사이드바 맨 위 — 지금 워크스페이스와 전환 메뉴 (개인 → 팀 순). */
export function WorkspaceSwitcher({ currentSlug, onNavigate, compact = false }: {
  currentSlug: string | null;
  onNavigate?: () => void;
  /** 접은 사이드바 — 아바타만 보이고 이름은 도구 설명으로 */
  compact?: boolean;
}) {
  const { t } = useTranslation();
  const roleName = useRoleName();
  const me = useMe();
  const navigate = useNavigate();
  const [anchor, setAnchor] = useState<HTMLElement | null>(null);
  /** 받은 초대가 있으면 전환 단추에 점, 찾기 항목에 개수 — 로그인하면 자기 워크스페이스로 가므로 여기서 알려 준다 */
  const invitationCount = useMyInvitationsQuery().data?.length ?? 0;
  const current = me.workspaces.find((w) => w.slug === currentSlug);
  const personal = me.workspaces.filter((w) => w.kind === 'PERSONAL');
  const teams = me.workspaces.filter((w) => w.kind === 'TEAM');

  const go = (to: string) => {
    setAnchor(null);
    onNavigate?.();
    navigate(to);
  };

  const item = (ws: WorkspaceSummary) => (
    <MenuItem key={ws.slug} selected={ws.slug === currentSlug} onClick={() => go(`/w/${ws.slug}`)}>
      <ListItemIcon sx={{ mr: 1.5 }}>
        <WorkspaceAvatar ws={ws} size={28} />
      </ListItemIcon>
      <ListItemText
        primary={ws.name}
        secondary={`${ws.myRoles.map(roleName).join(' · ')} · ${t('switcher.memberCount', { count: ws.memberCount })}`}
        slotProps={{ primary: { noWrap: true, sx: { fontWeight: 600 } }, secondary: { noWrap: true } }}
      />
      {ws.slug === currentSlug && <CheckRounded fontSize="small" sx={{ ml: 1, color: 'primary.main' }} />}
    </MenuItem>
  );

  const label =
    invitationCount > 0 ? `${t('switcher.label')} · ${t('switcher.invitations', { count: invitationCount })}` : t('switcher.label');
  const avatar = (
    <Badge color="error" variant="dot" invisible={invitationCount === 0} overlap="rectangular">
      {current ? <WorkspaceAvatar ws={current} /> : <WorkspaceAvatar ws={{ name: '?', kind: 'TEAM' }} />}
    </Badge>
  );

  return (
    <>
      {compact ? (
        <Tooltip title={current ? `${current.name} · ${t('switcher.label')}` : t('switcher.label')} placement="right">
          <ButtonBase
            onClick={(e) => setAnchor(e.currentTarget)}
            aria-haspopup="menu"
            aria-label={current ? `${current.name} · ${label}` : label}
            sx={{
              p: 0.75,
              borderRadius: 2,
              border: 1,
              borderColor: 'divider',
              bgcolor: 'background.paper',
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
        aria-label={label}
        sx={{
          width: '100%',
          justifyContent: 'flex-start',
          gap: 1.25,
          p: 1,
          borderRadius: 2,
          border: 1,
          borderColor: 'divider',
          bgcolor: 'background.paper',
          textAlign: 'left',
          '&:hover': { bgcolor: 'action.hover' },
          '&:focus-visible': { outline: 2, outlineColor: 'primary.main', outlineStyle: 'solid' },
        }}
      >
        {avatar}
        <Box sx={{ minWidth: 0, flex: 1 }}>
          <Typography variant="subtitle2" noWrap>
            {current?.name ?? t('switcher.choose')}
          </Typography>
          <Typography variant="caption" noWrap sx={{ color: 'text.secondary', display: 'block' }}>
            {current ? (current.kind === 'PERSONAL' ? t('switcher.personalWorkspace') : t('switcher.teamWorkspace')) : t('switcher.chooseHint')}
          </Typography>
        </Box>
        <UnfoldMoreRounded fontSize="small" sx={{ color: 'text.secondary' }} />
      </ButtonBase>
      )}
      <Menu
        anchorEl={anchor}
        open={anchor !== null}
        onClose={() => setAnchor(null)}
        slotProps={{ paper: { sx: { width: 300, maxHeight: 480 } }, list: { dense: false } }}
      >
        {personal.length > 0 && <ListSubheader sx={{ lineHeight: '32px' }}>{t('switcher.personal')}</ListSubheader>}
        {personal.map(item)}
        {teams.length > 0 && <ListSubheader sx={{ lineHeight: '32px' }}>{t('switcher.team')}</ListSubheader>}
        {teams.map(item)}
        <Divider />
        <MenuItem onClick={() => go('/workspaces/discover')}>
          <ListItemIcon>
            <ExploreRounded fontSize="small" />
          </ListItemIcon>
          <Box component="span" sx={{ flex: 1 }}>
            {t('switcher.discover')}
          </Box>
          {invitationCount > 0 && (
            <Chip
              size="small"
              color="error"
              label={t('switcher.invitations', { count: invitationCount })}
              sx={{ ml: 1, height: 22, fontSize: 12 }}
            />
          )}
        </MenuItem>
        {/* 팀 워크스페이스는 시스템 관리자가 허용한 계정만 만든다 — 못 만드는 계정에게는 보이지 않는다 */}
        {me.teamWorkspaceLimit > 0 && (
          <MenuItem onClick={() => go('/workspaces/new')}>
            <ListItemIcon>
              <AddRounded fontSize="small" />
            </ListItemIcon>
            {t('switcher.create')}
          </MenuItem>
        )}
      </Menu>
    </>
  );
}
