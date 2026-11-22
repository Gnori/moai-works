import LockRounded from '@mui/icons-material/LockRounded';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardHeader from '@mui/material/CardHeader';
import Stack from '@mui/material/Stack';
import Switch from '@mui/material/Switch';
import Typography from '@mui/material/Typography';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';

import { SettingsSectionHeader } from '@/features/me/SettingsSectionHeader';
import { notificationApi } from '@/shared/api/endpoints';
import { errorMessage } from '@/shared/api/http';
import type { NotificationPreference } from '@/shared/api/types';
import { ErrorState, LoadingState } from '@/shared/ui/States';

import { isKnownKind, notificationGroupHelp, notificationGroupLabel, notificationKindLabel } from './describe';
import { groupPreferences, preferenceChecked, preferenceLocked } from './preferences';
import { notificationKeys, useNotificationPreferencesQuery } from './queries';

/** 종류 한 줄 — 이름 · (끌 수 없으면 잠금과 까닭) · 스위치 */
function PreferenceRow({
  pref,
  pending,
  onToggle,
}: {
  pref: NotificationPreference;
  /** 저장 중이면 보낸 값 — 그동안 스위치는 그 값으로 보이고 꺼져 있다 */
  pending: boolean | null;
  onToggle: (enabled: boolean) => void;
}) {
  const { t } = useTranslation(['common', 'issue', 'workspace']);
  const locked = preferenceLocked(pref);
  const labelId = `notification-pref-${pref.kind}`;
  const reasonId = `${labelId}-reason`;
  return (
    <Box component="li" sx={{ display: 'flex', alignItems: 'center', gap: 2, px: { xs: 2, sm: 2.5 }, py: 1.25, borderTop: 1, borderColor: 'divider' }}>
      <Box sx={{ flex: 1, minWidth: 0 }}>
        <Typography id={labelId} variant="body2" sx={{ fontWeight: 600, overflowWrap: 'anywhere' }}>
          {notificationKindLabel(t, pref.kind)}
        </Typography>
        {!isKnownKind(pref.kind) && (
          <Typography variant="caption" sx={{ fontFamily: 'monospace', color: 'text.secondary' }}>
            {pref.kind}
          </Typography>
        )}
        {locked && (
          <Stack direction="row" spacing={0.5} sx={{ alignItems: 'center', color: 'text.secondary', mt: 0.25 }}>
            <LockRounded sx={{ fontSize: 14 }} aria-hidden />
            <Typography id={reasonId} variant="caption">
              {t('notifications.settings.mandatory')}
            </Typography>
          </Stack>
        )}
      </Box>
      <Switch
        checked={pending ?? preferenceChecked(pref)}
        disabled={locked || pending !== null}
        onChange={(_, checked) => onToggle(checked)}
        slotProps={{ input: { 'aria-labelledby': labelId, 'aria-describedby': locked ? reasonId : undefined } }}
      />
    </Box>
  );
}

/**
 * 개인 설정 › 알림 설정 — 종류마다 켬 · 끔을 묶음(워크스페이스 · 프로젝트 · 일감 · 작업 시간 · 보안)별로.
 * 개인 설정 대화상자 안의 칸이다(?settings=notifications).
 * 바꾸면 바로 저장하고(PUT) 응답의 목록으로 다시 그린다. 끌 수 없는 종류는 서버가 정하고(mandatory), 화면은 잠가 둔다.
 * 시스템 관리자 계정에는 알림이 없어 이 칸도 없다(대화상자가 가린다, api 도 403).
 */
export function NotificationSettingsSection() {
  const { t } = useTranslation(['common', 'issue', 'workspace']);
  const queryClient = useQueryClient();
  const prefs = useNotificationPreferencesQuery();

  const save = useMutation({
    mutationFn: ({ kind, enabled }: { kind: string; enabled: boolean }) => notificationApi.setPreference(kind, enabled),
    onSuccess: (list, { kind, enabled }) => {
      queryClient.setQueryData(notificationKeys.preferences, list);
      const name = notificationKindLabel(t, kind);
      toast.success(enabled ? t('notifications.settings.turnedOn', { kind: name }) : t('notifications.settings.turnedOff', { kind: name }));
    },
    onError: (e) => toast.error(errorMessage(e)),
  });
  const pendingKind = save.isPending ? save.variables?.kind : undefined;

  return (
    <>
      <SettingsSectionHeader title={t('notifications.settings.title')} description={t('notifications.settings.description')} />
      {prefs.isPending ? (
        <LoadingState />
      ) : prefs.isError ? (
        <Card>
          <ErrorState error={prefs.error} onRetry={() => void prefs.refetch()} />
        </Card>
      ) : (
        <Stack spacing={2}>
          {groupPreferences(prefs.data).map(({ group, items }) => (
            <Card key={group} component="section" aria-labelledby={`notification-group-${group}`}>
              <CardHeader
                title={notificationGroupLabel(t, group)}
                subheader={notificationGroupHelp(t, group)}
                slotProps={{ title: { id: `notification-group-${group}`, variant: 'subtitle1', component: 'h4', sx: { fontWeight: 700 } } }}
                sx={{ pb: 1.5 }}
              />
              <Box component="ul" sx={{ listStyle: 'none', m: 0, p: 0 }}>
                {items.map((pref) => (
                  <PreferenceRow
                    key={pref.kind}
                    pref={pref}
                    pending={pendingKind === pref.kind ? (save.variables?.enabled ?? null) : null}
                    onToggle={(enabled) => save.mutate({ kind: pref.kind, enabled })}
                  />
                ))}
              </Box>
            </Card>
          ))}
        </Stack>
      )}
    </>
  );
}
