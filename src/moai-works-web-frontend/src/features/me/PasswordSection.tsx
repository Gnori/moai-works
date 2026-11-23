import { zodResolver } from '@hookform/resolvers/zod';
import VisibilityOffRounded from '@mui/icons-material/VisibilityOffRounded';
import VisibilityRounded from '@mui/icons-material/VisibilityRounded';
import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import Checkbox from '@mui/material/Checkbox';
import FormControlLabel from '@mui/material/FormControlLabel';
import FormHelperText from '@mui/material/FormHelperText';
import IconButton from '@mui/material/IconButton';
import InputAdornment from '@mui/material/InputAdornment';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useMemo, useState } from 'react';
import { useForm } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router';
import { toast } from 'sonner';
import { z } from 'zod';

import { useMe, useSession } from '@/features/auth/session';
import { meApi } from '@/shared/api/endpoints';
import { errorMessage, isApiError } from '@/shared/api/http';
import { passwordPolicyMessage } from '@/shared/lib/password';

import { SettingsSectionHeader } from './SettingsSectionHeader';

/**
 * 개인 설정 › 비밀번호 (?settings=password, FR-WSP-006) — 현재 비밀번호 · 새 비밀번호 · 확인, "다른 기기의 로그인 끊기"(기본 켬).
 * 시스템 관리자 계정도 쓴다. 현재 비밀번호는 서버(api)가 Keycloak 으로 확인한다 — 틀려도 로그아웃되지 않는다(400).
 * 이 브라우저의 로그인은 그대로 두고, 서버가 이 로그인을 가려내지 못했으면(currentSessionKept=false) 로그아웃시켜 다시 로그인하게 한다.
 * 화면의 8자 · 같은지 확인은 표시일 뿐이고 정책은 서버(Keycloak)가 본다.
 */
export function PasswordSection() {
  const { t } = useTranslation();
  const me = useMe();
  const { signOut } = useSession();
  const navigate = useNavigate();
  const [show, setShow] = useState(false);
  const [blocked, setBlocked] = useState<string | null>(null);

  const schema = useMemo(
    () =>
      z
        .object({
          currentPassword: z.string().min(1, t('passwordSettings.currentRequired')).max(128),
          newPassword: z.string().min(8, t('passwordSettings.nextMin')).max(128),
          confirmPassword: z.string(),
          signOutOtherSessions: z.boolean(),
        })
        .refine((v) => v.newPassword === v.confirmPassword, { path: ['confirmPassword'], message: t('passwordSettings.mismatch') }),
    [t],
  );
  type Form = z.infer<typeof schema>;
  const { register, handleSubmit, formState, reset, setError } = useForm<Form>({
    resolver: zodResolver(schema),
    defaultValues: { currentPassword: '', newPassword: '', confirmPassword: '', signOutOtherSessions: true },
  });

  const onSubmit = handleSubmit(async (form) => {
    setBlocked(null);
    try {
      const result = await meApi.changePassword({
        currentPassword: form.currentPassword,
        newPassword: form.newPassword,
        signOutOtherSessions: form.signOutOtherSessions,
      });
      reset({ currentPassword: '', newPassword: '', confirmPassword: '', signOutOtherSessions: form.signOutOtherSessions });
      if (!result.currentSessionKept) {
        // 서버가 이 로그인까지 끝냈다 — 다시 로그인하게 한다
        toast.info(t('passwordSettings.signInAgain'));
        await signOut();
        navigate('/sign-in', { replace: true });
        return;
      }
      toast.success(result.otherSessionsEnded ? t('passwordSettings.changedOthersEnded') : t('passwordSettings.changed'));
    } catch (e) {
      if (isApiError(e, 'CURRENT_PASSWORD_INVALID')) {
        const left = typeof e.details.attemptsLeft === 'number' ? e.details.attemptsLeft : null;
        setError('currentPassword', {
          message: left === null ? t('passwordSettings.currentInvalid') : t('passwordSettings.currentInvalidLeft', { left }),
        });
      } else if (isApiError(e, 'PASSWORD_UNCHANGED')) {
        setError('newPassword', { message: t('passwordSettings.same') });
      } else if (isApiError(e, 'PASSWORD_POLICY')) {
        setError('newPassword', { message: passwordPolicyMessage(e) });
      } else if (isApiError(e, 'RATE_LIMITED')) {
        setBlocked(t('passwordSettings.tooManyAttempts'));
      } else if (isApiError(e, 'ACCOUNT_LOCKED')) {
        setBlocked(t('passwordSettings.locked'));
      } else {
        toast.error(errorMessage(e));
      }
    }
  });

  const visibility = (
    <InputAdornment position="end">
      <IconButton
        edge="end"
        aria-label={show ? t('passwordSettings.hidePasswords') : t('passwordSettings.showPasswords')}
        onClick={() => setShow((v) => !v)}
      >
        {show ? <VisibilityOffRounded /> : <VisibilityRounded />}
      </IconButton>
    </InputAdornment>
  );

  return (
    <>
      <SettingsSectionHeader title={t('passwordSettings.title')} description={t('passwordSettings.description')} />
      <Card component="form" noValidate onSubmit={onSubmit}>
        {/* 칸 제목(외곽선 위로 떠 있는 라벨)이 카드 위 테두리와 겹치지 않게 조금 띄운다 */}
        <CardContent sx={{ pt: 3 }}>
          <Stack spacing={2.5} sx={{ maxWidth: 480 }}>
            {blocked && <Alert severity="warning">{blocked}</Alert>}
            {/* 비밀번호 관리자가 어느 계정의 비밀번호인지 알게 아이디를 숨겨 둔다 */}
            <input type="text" name="username" autoComplete="username" value={me.username} readOnly hidden />
            <TextField
              label={t('passwordSettings.current')}
              type={show ? 'text' : 'password'}
              autoComplete="current-password"
              {...register('currentPassword')}
              error={!!formState.errors.currentPassword}
              helperText={formState.errors.currentPassword?.message}
              slotProps={{ input: { endAdornment: visibility }, htmlInput: { maxLength: 128 } }}
            />
            <TextField
              label={t('passwordSettings.next')}
              type={show ? 'text' : 'password'}
              autoComplete="new-password"
              {...register('newPassword')}
              error={!!formState.errors.newPassword}
              helperText={formState.errors.newPassword?.message ?? t('passwordSettings.nextHelp')}
              slotProps={{ htmlInput: { maxLength: 128 } }}
            />
            <TextField
              label={t('passwordSettings.confirm')}
              type={show ? 'text' : 'password'}
              autoComplete="new-password"
              {...register('confirmPassword')}
              error={!!formState.errors.confirmPassword}
              helperText={formState.errors.confirmPassword?.message}
              slotProps={{ htmlInput: { maxLength: 128 } }}
            />
            <div>
              <FormControlLabel
                control={<Checkbox defaultChecked {...register('signOutOtherSessions')} />}
                label={t('passwordSettings.signOutOthers')}
              />
              <FormHelperText sx={{ mt: 0, ml: 4 }}>{t('passwordSettings.signOutOthersHelp')}</FormHelperText>
            </div>
            <div>
              <Button type="submit" variant="contained" loading={formState.isSubmitting}>
                {t('passwordSettings.submit')}
              </Button>
            </div>
            <Typography variant="caption" sx={{ color: 'text.secondary' }}>
              {t('passwordSettings.forgotHint')}
            </Typography>
          </Stack>
        </CardContent>
      </Card>
    </>
  );
}
