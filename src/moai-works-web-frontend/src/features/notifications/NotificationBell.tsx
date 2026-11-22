import NotificationsRounded from '@mui/icons-material/NotificationsRounded';
import Badge from '@mui/material/Badge';
import Chip from '@mui/material/Chip';
import IconButton from '@mui/material/IconButton';
import ListItemButton from '@mui/material/ListItemButton';
import ListItemIcon from '@mui/material/ListItemIcon';
import ListItemText from '@mui/material/ListItemText';
import Tooltip from '@mui/material/Tooltip';
import { useTranslation } from 'react-i18next';
import { NavLink, Link as RouterLink } from 'react-router';

import { notificationsPath } from '@/app/routes';

import { useUnreadCountQuery } from './queries';

/** 종 모양의 이름 — 읽지 않은 것이 있으면 수를 함께 읽어 준다 */
function useBellLabel() {
  const { t } = useTranslation();
  const count = useUnreadCountQuery().data ?? 0;
  return { count, label: count > 0 ? t('notifications.openUnread', { count }) : t('notifications.open') };
}

/**
 * 사이드바의 알림 — 계정 영역 바로 위. 종 모양 · "알림" · 읽지 않은 수. 누르면 알림함.
 * 수는 1분마다, 그리고 창으로 돌아올 때 다시 읽는다. 시스템 관리자 계정에는 그리지 않는다(부르는 쪽이 가린다).
 */
export function NotificationNavItem({ onNavigate }: { onNavigate?: () => void }) {
  const { t } = useTranslation();
  const { count, label } = useBellLabel();
  return (
    <ListItemButton
      component={NavLink}
      to={notificationsPath}
      onClick={onNavigate}
      aria-label={label}
      sx={{
        borderRadius: 2,
        mb: 1,
        color: 'text.secondary',
        '&.active': { bgcolor: 'action.selected', color: 'primary.main', '& .MuiListItemIcon-root': { color: 'primary.main' } },
      }}
    >
      <ListItemIcon sx={{ minWidth: 34, color: 'inherit' }}>
        <Badge variant="dot" color="error" invisible={count === 0} overlap="circular">
          <NotificationsRounded fontSize="small" />
        </Badge>
      </ListItemIcon>
      <ListItemText primary={t('notifications.title')} slotProps={{ primary: { sx: { fontWeight: 600, fontSize: 14 } } }} />
      {count > 0 && (
        <Chip
          size="small"
          color="error"
          label={count > 99 ? '99+' : count}
          aria-hidden
          sx={{ height: 20, fontSize: 12, fontWeight: 700, fontVariantNumeric: 'tabular-nums' }}
        />
      )}
    </ListItemButton>
  );
}

/** 좁은 화면의 위 막대에 두는 종 모양 — 서랍을 열지 않아도 읽지 않은 수가 보인다 */
export function NotificationBell() {
  const { count, label } = useBellLabel();
  return (
    <Tooltip title={label}>
      <IconButton component={RouterLink} to={notificationsPath} aria-label={label}>
        <Badge badgeContent={count} color="error" max={99} overlap="circular">
          <NotificationsRounded />
        </Badge>
      </IconButton>
    </Tooltip>
  );
}
