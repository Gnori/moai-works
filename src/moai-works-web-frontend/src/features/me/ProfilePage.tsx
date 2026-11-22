import { zodResolver } from '@hookform/resolvers/zod';
import Avatar from '@mui/material/Avatar';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import CardHeader from '@mui/material/CardHeader';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useMemo, type ReactNode } from 'react';
import { useForm } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';
import { z } from 'zod';

import { isSystemAdmin } from '@/features/auth/guards';
import { useMe, useSession } from '@/features/auth/session';
import { meApi } from '@/shared/api/endpoints';
import { errorMessage } from '@/shared/api/http';
import { dateTimeMs, initials } from '@/shared/lib/format';
import { PageHeader } from '@/shared/ui/PageHeader';

/** 이름표 한 줄 (dt · dd) */
function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <Box sx={{ minWidth: 0 }}>
      <Typography component="dt" variant="caption" sx={{ color: 'text.secondary', display: 'block' }}>
        {label}
      </Typography>
      <Typography component="dd" variant="body2" sx={{ m: 0, overflowWrap: 'anywhere' }}>
        {children}
      </Typography>
    </Box>
  );
}

/**
 * 개인 설정 · 내 프로필 — 아이디 · 이메일 · 계정 종류 · 마지막 로그인, 그리고 표시 이름 바꾸기(PATCH /me).
 * 시스템 관리자 계정도 쓴다. 아이디 · 이메일은 바꿀 수 없다.
 */
export function ProfilePage() {
  const { t } = useTranslation();
  const me = useMe();
  const { refresh } = useSession();
  const systemAdmin = isSystemAdmin(me);

  const schema = useMemo(
    () =>
      z.object({
        displayName: z.string().trim().min(1, t('profile.displayNameRequired')).max(100, t('profile.displayNameTooLong')),
      }),
    [t],
  );
  type Form = z.infer<typeof schema>;
  const { register, handleSubmit, formState, reset } = useForm<Form>({
    resolver: zodResolver(schema),
    defaultValues: { displayName: me.displayName },
  });

  const onSubmit = handleSubmit(async (form) => {
    try {
      await meApi.rename(form.displayName);
      reset({ displayName: form.displayName });
      // 세션(내 정보)은 api 의 /me 를 다시 읽는다 — 사이드바의 이름도 함께 바뀐다
      await refresh();
      toast.success(t('profile.renamed'));
    } catch (e) {
      toast.error(errorMessage(e));
    }
  });

  return (
    <>
      <PageHeader eyebrow={t('nav.sectionPersonal')} title={t('profile.title')} description={t('profile.description')} />
      <Stack spacing={3}>
        <Card>
          <CardHeader
            avatar={
              <Avatar sx={{ width: 44, height: 44, fontWeight: 700, bgcolor: 'primary.main', color: 'primary.contrastText' }}>
                {initials(me.displayName)}
              </Avatar>
            }
            title={me.displayName}
            subheader={me.email}
            slotProps={{
              title: { variant: 'h6', sx: { overflowWrap: 'anywhere' } },
              subheader: { sx: { overflowWrap: 'anywhere' } },
            }}
          />
          <CardContent sx={{ pt: 0 }}>
            <Box
              component="dl"
              sx={{ m: 0, display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, minmax(0, 1fr))', lg: 'repeat(4, minmax(0, 1fr))' } }}
            >
              <Field label={t('profile.username')}>
                <Box component="span" sx={{ fontFamily: 'monospace' }}>
                  {me.username}
                </Box>
              </Field>
              <Field label={t('profile.email')}>{me.email}</Field>
              <Field label={t('profile.accountType')}>{systemAdmin ? t('profile.systemAdmin') : t('profile.user')}</Field>
              <Field label={t('profile.lastLogin')}>{dateTimeMs(me.lastLoginAt)}</Field>
              {!systemAdmin && <Field label={t('profile.workspaces')}>{t('profile.workspaceCount', { count: me.workspaces.length })}</Field>}
            </Box>
            <Typography variant="caption" sx={{ color: 'text.secondary', display: 'block', mt: 2 }}>
              {t('profile.fixedNote')}
            </Typography>
          </CardContent>
        </Card>

        <Card component="form" noValidate onSubmit={onSubmit}>
          <CardHeader title={t('profile.rename')} subheader={t('profile.renameHelp')} />
          <CardContent sx={{ pt: 0 }}>
            <Stack spacing={2.5} sx={{ maxWidth: 560 }}>
              <TextField
                label={t('profile.displayName')}
                autoComplete="name"
                {...register('displayName')}
                error={!!formState.errors.displayName}
                helperText={formState.errors.displayName?.message}
                slotProps={{ htmlInput: { maxLength: 100 } }}
              />
              <div>
                <Button type="submit" variant="contained" loading={formState.isSubmitting} disabled={!formState.isDirty}>
                  {t('actions.save')}
                </Button>
              </div>
            </Stack>
          </CardContent>
        </Card>
      </Stack>
    </>
  );
}
