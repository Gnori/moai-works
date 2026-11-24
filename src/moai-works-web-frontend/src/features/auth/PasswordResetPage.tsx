import { zodResolver } from '@hookform/resolvers/zod';
import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import Link from '@mui/material/Link';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useEffect, useMemo, useState } from 'react';
import { useForm } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import { Link as RouterLink, useNavigate, useSearchParams } from 'react-router';
import { toast } from 'sonner';
import { z } from 'zod';

import { passwordResetApi } from '@/shared/api/endpoints';
import { errorMessage, isApiError } from '@/shared/api/http';
import { passwordPolicyMessage } from '@/shared/lib/password';

const RESEND_COOLDOWN = 60;

/** 코드를 받은 뒤 화면 — 이메일은 주소(?email=)로 넘긴다(새로 고쳐도 그대로) */
export function passwordResetConfirmPath(email: string): string {
  return `/password-reset/confirm?email=${encodeURIComponent(email)}`;
}

/**
 * 비밀번호 찾기 1 — 이메일을 받아 코드를 보내 달라고 한다(FR-WSP-006).
 * 서버는 계정이 있든 없든 같은 응답을 준다. 화면도 "계정이 있으면 보냈다"고만 말하고 다음 칸으로 간다 — 계정이 있는지 드러내지 않는다.
 */
export function PasswordResetRequestPage() {
  const { t } = useTranslation('auth');
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const [error, setError] = useState<string | null>(null);
  const schema = useMemo(
    () => z.object({ email: z.string().trim().min(1, t('passwordReset.emailRequired')).pipe(z.email(t('passwordReset.invalidEmail'))) }),
    [t],
  );
  const { register, handleSubmit, formState } = useForm<z.infer<typeof schema>>({
    resolver: zodResolver(schema),
    defaultValues: { email: params.get('email') ?? '' },
  });

  const onSubmit = handleSubmit(async ({ email }) => {
    setError(null);
    try {
      await passwordResetApi.request(email);
      navigate(passwordResetConfirmPath(email));
    } catch (e) {
      setError(errorMessage(e));
    }
  });

  return (
    <Stack spacing={3} component="form" noValidate onSubmit={onSubmit}>
      <div>
        <Typography variant="h4" component="h1">
          {t('passwordReset.title')}
        </Typography>
        <Typography variant="body2" sx={{ color: 'text.secondary', mt: 0.75 }}>
          {t('passwordReset.description')}
        </Typography>
        {/* 시스템 관리자 계정에는 코드를 보내지 않는다(응답은 같은 202 — 계정이 있는지 드러내지 않는다). 다시 정하는 길만 알린다 */}
        <Typography variant="body2" sx={{ color: 'text.secondary', mt: 0.5 }}>
          {t('passwordReset.systemAdminNote')}
        </Typography>
      </div>
      {error && <Alert severity="error">{error}</Alert>}
      <TextField
        label={t('passwordReset.email')}
        type="email"
        autoComplete="email"
        autoFocus
        {...register('email')}
        error={!!formState.errors.email}
        helperText={formState.errors.email?.message}
      />
      <Button type="submit" size="large" variant="contained" loading={formState.isSubmitting}>
        {t('passwordReset.send')}
      </Button>
      <Link component={RouterLink} to="/sign-in" underline="hover" variant="body2" sx={{ alignSelf: 'flex-start' }}>
        {t('passwordReset.toSignIn')}
      </Link>
    </Stack>
  );
}

/**
 * 비밀번호 찾기 2 — 메일로 받은 6자리 코드 + 새 비밀번호(두 번). 성공하면 그 계정의 로그인이 모두 끝나고 로그인 화면으로 간다.
 * 코드를 다시 받기는 60초 간격(가입 인증 화면과 같다). 코드가 틀리거나 만료되면 같은 문장(INVALID_CODE).
 */
export function PasswordResetConfirmPage() {
  const { t } = useTranslation('auth');
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const email = params.get('email') ?? '';
  const [error, setError] = useState<string | null>(null);
  const [cooldown, setCooldown] = useState(email ? RESEND_COOLDOWN : 0);

  useEffect(() => {
    if (cooldown <= 0) {
      return;
    }
    const timer = window.setTimeout(() => setCooldown((s) => s - 1), 1000);
    return () => window.clearTimeout(timer);
  }, [cooldown]);

  const schema = useMemo(
    () =>
      z
        .object({
          code: z.string().regex(/^\d{6}$/, t('passwordReset.codeRequired')),
          newPassword: z.string().min(8, t('passwordReset.passwordMin')).max(128),
          confirmPassword: z.string(),
        })
        .refine((v) => v.newPassword === v.confirmPassword, { path: ['confirmPassword'], message: t('passwordReset.mismatch') }),
    [t],
  );
  type Form = z.infer<typeof schema>;
  const { register, handleSubmit, formState, setError: setFieldError, setValue } = useForm<Form>({
    resolver: zodResolver(schema),
    defaultValues: { code: '', newPassword: '', confirmPassword: '' },
  });

  const onSubmit = handleSubmit(async (form) => {
    setError(null);
    try {
      await passwordResetApi.confirm({ email, code: form.code, newPassword: form.newPassword });
      toast.success(t('passwordReset.done'));
      navigate('/sign-in', { replace: true });
    } catch (e) {
      if (isApiError(e, 'INVALID_CODE')) {
        setFieldError('code', { message: t('passwordReset.invalidCode') });
      } else if (isApiError(e, 'PASSWORD_POLICY')) {
        setFieldError('newPassword', { message: passwordPolicyMessage(e) });
      } else {
        setError(errorMessage(e));
      }
    }
  });

  const resend = async () => {
    try {
      await passwordResetApi.request(email);
      toast.success(t('passwordReset.resent'));
      setCooldown(RESEND_COOLDOWN);
    } catch (e) {
      toast.error(errorMessage(e));
    }
  };

  if (!email) {
    // 이메일 없이 들어왔으면 첫 칸부터
    return (
      <Stack spacing={2}>
        <Typography variant="h4" component="h1">
          {t('passwordReset.confirmTitle')}
        </Typography>
        <Button component={RouterLink} to="/password-reset" variant="outlined">
          {t('passwordReset.title')}
        </Button>
      </Stack>
    );
  }

  const code = register('code');
  return (
    <Stack spacing={3} component="form" noValidate onSubmit={onSubmit}>
      <div>
        <Typography variant="h4" component="h1">
          {t('passwordReset.confirmTitle')}
        </Typography>
        <Typography variant="body2" sx={{ color: 'text.secondary', mt: 0.75, overflowWrap: 'anywhere' }}>
          {t('passwordReset.confirmDescription', { email })}
        </Typography>
      </div>
      {error && <Alert severity="error">{error}</Alert>}
      {/* 비밀번호 관리자가 새 비밀번호를 이 계정에 저장하게 아이디(이메일)를 숨겨 둔다 */}
      <input type="email" name="username" autoComplete="username" value={email} readOnly hidden />
      <TextField
        label={t('passwordReset.code')}
        autoFocus
        autoComplete="one-time-code"
        {...code}
        onChange={(e) => {
          setValue('code', e.target.value.replace(/\D/g, '').slice(0, 6), { shouldValidate: formState.isSubmitted });
        }}
        error={!!formState.errors.code}
        helperText={formState.errors.code?.message}
        slotProps={{
          htmlInput: {
            inputMode: 'numeric',
            maxLength: 6,
            style: { letterSpacing: '0.5em', fontSize: '1.25rem', fontWeight: 600, textAlign: 'center' },
          },
        }}
      />
      <TextField
        label={t('passwordReset.newPassword')}
        type="password"
        autoComplete="new-password"
        {...register('newPassword')}
        error={!!formState.errors.newPassword}
        helperText={formState.errors.newPassword?.message ?? t('passwordReset.newPasswordHelp')}
        slotProps={{ htmlInput: { maxLength: 128 } }}
      />
      <TextField
        label={t('passwordReset.confirmPassword')}
        type="password"
        autoComplete="new-password"
        {...register('confirmPassword')}
        error={!!formState.errors.confirmPassword}
        helperText={formState.errors.confirmPassword?.message}
        slotProps={{ htmlInput: { maxLength: 128 } }}
      />
      <Button type="submit" size="large" variant="contained" loading={formState.isSubmitting}>
        {t('passwordReset.submit')}
      </Button>
      <Typography variant="caption" sx={{ color: 'text.secondary' }}>
        {t('passwordReset.noMail')}
      </Typography>
      <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 1 }}>
        <Button onClick={resend} disabled={cooldown > 0} size="small">
          {cooldown > 0 ? t('passwordReset.resendIn', { seconds: cooldown }) : t('passwordReset.resend')}
        </Button>
        <Stack direction="row" spacing={2}>
          <Link component={RouterLink} to={`/password-reset?email=${encodeURIComponent(email)}`} underline="hover" variant="body2">
            {t('passwordReset.otherEmail')}
          </Link>
          <Link component={RouterLink} to="/sign-in" underline="hover" variant="body2">
            {t('passwordReset.toSignIn')}
          </Link>
        </Stack>
      </Stack>
    </Stack>
  );
}
