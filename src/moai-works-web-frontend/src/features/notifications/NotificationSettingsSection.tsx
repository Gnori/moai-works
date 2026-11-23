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
import { emailChecked, emailLocked, groupPreferences, preferenceChecked, preferenceLocked } from './preferences';
import { notificationKeys, useNotificationPreferencesQuery } from './queries';

/** 스위치 한 열의 너비 — 머리 줄(알림함 · 메일)과 줄마다의 스위치가 같은 자리에 선다 */
const COLUMN = 64;
const rowSx = { display: 'flex', alignItems: 'center', gap: { xs: 1, sm: 2 }, px: { xs: 2, sm: 2.5 } } as const;
const columnSx = { width: COLUMN, flexShrink: 0, display: 'flex', justifyContent: 'center' } as const;

/** 무엇을 바꾸나 — 알림함(enabled) 또는 메일(email) */
type Field = 'enabled' | 'email';
interface Change {
  kind: string;
  field: Field;
  value: boolean;
}

/** 종류 한 줄 — 이름 · (끌 수 없으면 잠금과 까닭 · 메일을 고를 수 없으면 까닭) · 알림함 스위치 · 메일 스위치 */
function PreferenceRow({
  pref,
  pending,
  onToggle,
}: {
  pref: NotificationPreference;
  /** 이 종류를 저장 중이면 보낸 값 — 그동안 바꾸는 스위치는 그 값으로 보이고, 두 스위치 모두 꺼져 있다 */
  pending: Change | null;
  onToggle: (field: Field, value: boolean) => void;
}) {
  const { t } = useTranslation(['common', 'issue', 'workspace']);
  const locked = preferenceLocked(pref);
  const mailLocked = emailLocked(pref);
  const label = notificationKindLabel(t, pref.kind);
  const labelId = `notification-pref-${pref.kind}`;
  const reasonId = `${labelId}-reason`;
  const mailReasonId = `${labelId}-email-reason`;
  return (
    <Box component="li" sx={{ ...rowSx, py: 1.25, borderTop: 1, borderColor: 'divider' }}>
      <Box sx={{ flex: 1, minWidth: 0 }}>
        <Typography id={labelId} variant="body2" sx={{ fontWeight: 600, overflowWrap: 'anywhere' }}>
          {label}
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
        {mailLocked && (
          <Typography id={mailReasonId} variant="caption" component="p" sx={{ color: 'text.secondary', mt: 0.25 }}>
            {t('notifications.settings.emailNeedsInbox')}
          </Typography>
        )}
      </Box>
      <Box sx={columnSx}>
        <Switch
          checked={pending?.field === 'enabled' ? pending.value : preferenceChecked(pref)}
          disabled={locked || pending !== null}
          onChange={(_, checked) => onToggle('enabled', checked)}
          slotProps={{ input: { 'aria-labelledby': labelId, 'aria-describedby': locked ? reasonId : undefined } }}
        />
      </Box>
      <Box sx={columnSx}>
        <Switch
          checked={pending?.field === 'email' ? pending.value : emailChecked(pref)}
          disabled={mailLocked || pending !== null}
          onChange={(_, checked) => onToggle('email', checked)}
          slotProps={{
            input: {
              'aria-label': t('notifications.settings.emailSwitch', { kind: label }),
              'aria-describedby': mailLocked ? mailReasonId : undefined,
            },
          }}
        />
      </Box>
    </Box>
  );
}

/**
 * 개인 설정 › 알림 설정 — 종류마다 알림함 · 메일 켬 · 끔을 묶음(워크스페이스 · 프로젝트 · 일감 · 작업 시간 · 보안)별로.
 * 개인 설정 대화상자 안의 칸이다(?settings=notifications).
 * 바꾸면 바로 저장하고(PUT) 응답의 목록으로 다시 그린다. 알림함을 끌 수 없는 종류는 서버가 정하고(mandatory), 화면은 잠가 둔다.
 * 메일은 알림함에 쌓이는 종류만 간다 — 알림함을 끈 종류의 메일 스위치는 꺼 두고 까닭을 보인다(FR-NOTI-006).
 * 시스템 관리자 계정에는 알림이 없어 이 칸도 없다(대화상자가 가린다, api 도 403).
 */
export function NotificationSettingsSection() {
  const { t } = useTranslation(['common', 'issue', 'workspace']);
  const queryClient = useQueryClient();
  const prefs = useNotificationPreferencesQuery();

  const save = useMutation({
    mutationFn: ({ kind, field, value }: Change) =>
      notificationApi.setPreference(kind, field === 'email' ? { email: value } : { enabled: value }),
    onSuccess: (list, { kind, field, value }) => {
      queryClient.setQueryData(notificationKeys.preferences, list);
      const name = notificationKindLabel(t, kind);
      if (field === 'email') {
        toast.success(value ? t('notifications.settings.emailOn', { kind: name }) : t('notifications.settings.emailOff', { kind: name }));
      } else {
        toast.success(value ? t('notifications.settings.turnedOn', { kind: name }) : t('notifications.settings.turnedOff', { kind: name }));
      }
    },
    onError: (e) => toast.error(errorMessage(e)),
  });
  const pending = save.isPending ? (save.variables ?? null) : null;

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
                sx={{ pb: 1 }}
              />
              {/* 열 이름 — 스위치마다 이름(종류 · "— 메일")이 따로 있어 화면 읽기에는 숨긴다 */}
              <Box aria-hidden sx={{ ...rowSx, pb: 0.75 }}>
                <Box sx={{ flex: 1 }} />
                {(['inbox', 'email'] as const).map((column) => (
                  <Typography key={column} variant="caption" sx={{ ...columnSx, color: 'text.secondary', fontWeight: 600 }}>
                    {t(`notifications.settings.columns.${column}`)}
                  </Typography>
                ))}
              </Box>
              <Box component="ul" sx={{ listStyle: 'none', m: 0, p: 0 }}>
                {items.map((pref) => (
                  <PreferenceRow
                    key={pref.kind}
                    pref={pref}
                    pending={pending?.kind === pref.kind ? pending : null}
                    onToggle={(field, value) => save.mutate({ kind: pref.kind, field, value })}
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
