import DoneAllRounded from '@mui/icons-material/DoneAllRounded';
import NotificationsNoneRounded from '@mui/icons-material/NotificationsNoneRounded';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import Stack from '@mui/material/Stack';
import Tab from '@mui/material/Tab';
import Tabs from '@mui/material/Tabs';
import Typography from '@mui/material/Typography';
import { useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate, useSearchParams } from 'react-router';
import { toast } from 'sonner';

import { useSettingsDialog } from '@/features/me/use-settings-dialog';
import { notificationApi } from '@/shared/api/endpoints';
import { errorMessage } from '@/shared/api/http';
import type { NotificationItem } from '@/shared/api/types';
import { dateTimeMs, fromNow } from '@/shared/lib/format';
import { PageHeader } from '@/shared/ui/PageHeader';
import { EmptyState, ErrorState, LoadingState } from '@/shared/ui/States';
import { VisuallyHidden } from '@/shared/ui/VisuallyHidden';

import { describeNotification, type NotificationText } from './describe';
import { notificationKeys, useInboxQuery } from './queries';

type TabKey = 'ALL' | 'UNREAD';

/** 알림 한 줄 — 문장 · 묶인 수 · 워크스페이스 · 시각. 갈 곳이 있거나 읽지 않았으면 누를 수 있다 */
function NotificationRow({ item, onOpen }: { item: NotificationItem; onOpen: (item: NotificationItem, target: NotificationText) => void }) {
  const { t } = useTranslation(['common', 'issue', 'workspace']);
  const described = describeNotification(t, item);
  const { text } = described;
  const unread = !item.readAt;
  const clickable = !!described.to || !!described.settings || unread;
  const content = (
    <>
      <Box
        aria-hidden
        sx={{ mt: 0.9, width: 8, height: 8, borderRadius: '50%', flexShrink: 0, bgcolor: unread ? 'primary.main' : 'transparent' }}
      />
      <Box sx={{ flex: 1, minWidth: 0 }}>
        <Typography variant="body2" sx={{ fontWeight: unread ? 600 : 400, overflowWrap: 'anywhere' }}>
          {unread && <VisuallyHidden>{`${t('notifications.unread')}: `}</VisuallyHidden>}
          {text}
          {item.count > 1 && (
            <Box
              component="span"
              title={t('notifications.timesHelp', { count: item.count })}
              sx={{ color: 'text.secondary', fontWeight: 600, ml: 0.75, whiteSpace: 'nowrap', fontVariantNumeric: 'tabular-nums' }}
            >
              {t('notifications.times', { count: item.count })}
            </Box>
          )}
        </Typography>
        <Stack direction="row" spacing={1} useFlexGap sx={{ flexWrap: 'wrap', mt: 0.25, color: 'text.secondary' }}>
          {item.workspaceName && (
            <Typography variant="caption" sx={{ overflowWrap: 'anywhere' }}>
              {item.workspaceName}
            </Typography>
          )}
          <Typography variant="caption">{fromNow(item.updatedAt)}</Typography>
          <Typography variant="caption" sx={{ fontVariantNumeric: 'tabular-nums' }}>
            {dateTimeMs(item.updatedAt)}
          </Typography>
        </Stack>
      </Box>
    </>
  );
  const rowSx = {
    display: 'flex',
    gap: 1.5,
    alignItems: 'flex-start',
    width: '100%',
    px: { xs: 2, sm: 2.5 },
    py: 1.5,
    bgcolor: unread ? 'action.hover' : undefined,
  };
  if (!clickable) {
    return <Box sx={rowSx}>{content}</Box>;
  }
  return (
    <Box
      component="button"
      type="button"
      onClick={() => onOpen(item, described)}
      sx={{
        all: 'unset',
        boxSizing: 'border-box',
        cursor: 'pointer',
        ...rowSx,
        '&:hover': { bgcolor: 'action.selected' },
        '&:focus-visible': { outline: 2, outlineStyle: 'solid', outlineColor: 'primary.main', outlineOffset: -2 },
      }}
    >
      {content}
    </Box>
  );
}

/**
 * 내 알림함 — 알려야 할 일이 새 것부터 쌓인다(전체 · 읽지 않음 탭은 주소 ?tab=unread 에 남는다).
 * 누르면 읽음으로 하고 그 화면으로 간다 — 그 화면이 지금 권한으로 다시 판정한다. MCP 키 알림은 이 화면 위에 개인 설정의
 * 내 MCP 키를 연다(?settings=mcp-keys). 갈 곳이 없으면 읽음으로만.
 * 알림에는 본문이 없다 — 제목 · 이름 · 번호로 문장을 만든다(describe.ts). 시스템 관리자 계정에는 이 화면이 없다.
 */
export function NotificationsPage() {
  const { t } = useTranslation();
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const [params, setParams] = useSearchParams();
  const tab: TabKey = params.get('tab') === 'unread' ? 'UNREAD' : 'ALL';
  const [busy, setBusy] = useState(false);
  const settings = useSettingsDialog();
  const inbox = useInboxQuery(tab === 'UNREAD');
  const unread = inbox.data?.pages[0]?.unread ?? 0;
  const items = inbox.data?.pages.flatMap((page) => page.items) ?? [];

  const changeTab = (next: TabKey) => setParams(next === 'UNREAD' ? { tab: 'unread' } : {}, { replace: true });

  const open = async (item: NotificationItem, { to, settings: section }: NotificationText) => {
    if (!item.readAt) {
      try {
        // 남은 읽지 않은 수를 받아 종 모양에 바로 넣고, 알림함은 다시 읽는다
        queryClient.setQueryData(notificationKeys.unread, await notificationApi.read(item.id));
        void queryClient.invalidateQueries({ queryKey: notificationKeys.inboxAll });
      } catch (e) {
        toast.error(errorMessage(e));
      }
    }
    if (to) {
      void navigate(to);
    } else if (section) {
      settings.open(section);
    }
  };

  const readAll = async () => {
    setBusy(true);
    try {
      await notificationApi.readAll();
      queryClient.setQueryData(notificationKeys.unread, 0);
      await queryClient.invalidateQueries({ queryKey: notificationKeys.inboxAll });
      toast.success(t('notifications.readAllDone'));
    } catch (e) {
      toast.error(errorMessage(e));
    } finally {
      setBusy(false);
    }
  };

  return (
    <>
      <PageHeader
        title={t('notifications.title')}
        description={t('notifications.description')}
        actions={
          <Button variant="outlined" startIcon={<DoneAllRounded />} onClick={() => void readAll()} loading={busy} disabled={unread === 0}>
            {t('notifications.readAll')}
          </Button>
        }
      />
      <Card>
        <Tabs
          value={tab}
          onChange={(_, value: TabKey) => changeTab(value)}
          aria-label={t('notifications.tabsLabel')}
          variant="scrollable"
          allowScrollButtonsMobile
          sx={{ borderBottom: 1, borderColor: 'divider', px: 1 }}
        >
          <Tab value="ALL" label={t('notifications.tabs.ALL')} />
          <Tab value="UNREAD" label={t('notifications.tabs.UNREAD', { count: unread })} />
        </Tabs>
        {inbox.isPending ? (
          <LoadingState />
        ) : inbox.isError ? (
          <ErrorState error={inbox.error} onRetry={() => void inbox.refetch()} />
        ) : items.length === 0 ? (
          <EmptyState
            icon={<NotificationsNoneRounded />}
            title={tab === 'UNREAD' ? t('notifications.emptyUnread') : t('notifications.empty')}
            description={tab === 'ALL' ? t('notifications.emptyBody') : undefined}
          />
        ) : (
          <Box component="ul" aria-label={t('notifications.listLabel')} sx={{ listStyle: 'none', m: 0, p: 0 }}>
            {items.map((item, index) => (
              <Box key={item.id} component="li" sx={{ borderTop: index === 0 ? 0 : 1, borderColor: 'divider' }}>
                <NotificationRow item={item} onOpen={(n, target) => void open(n, target)} />
              </Box>
            ))}
          </Box>
        )}
        {inbox.hasNextPage && (
          <Box sx={{ p: 1.5, textAlign: 'center', borderTop: 1, borderColor: 'divider' }}>
            <Button onClick={() => void inbox.fetchNextPage()} loading={inbox.isFetchingNextPage}>
              {t('notifications.more')}
            </Button>
          </Box>
        )}
      </Card>
    </>
  );
}
