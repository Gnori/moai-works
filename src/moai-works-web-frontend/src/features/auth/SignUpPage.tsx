import { zodResolver } from '@hookform/resolvers/zod';
import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import Checkbox from '@mui/material/Checkbox';
import FormControlLabel from '@mui/material/FormControlLabel';
import FormHelperText from '@mui/material/FormHelperText';
import Link from '@mui/material/Link';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useQuery } from '@tanstack/react-query';
import { useMemo, useState } from 'react';
import { useForm } from 'react-hook-form';
import { Trans, useTranslation } from 'react-i18next';
import { Link as RouterLink, useNavigate, useSearchParams } from 'react-router';
import { z } from 'zod';

import { signupApi } from '@/shared/api/endpoints';
import { errorMessage } from '@/shared/api/http';
import { withReturnTo } from '@/shared/lib/post-login-path';
import { LoadingState } from '@/shared/ui/States';

/** 회원가입 — 가입 정책(도메인 제한 · 인증 · 승인)을 먼저 보여 주고, 결과 상태에 따라 다음 화면으로. */
export function SignUpPage() {
  const { t } = useTranslation('auth');
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const returnTo = params.get('returnTo');
  const [error, setError] = useState<string | null>(null);
  const [pendingApproval, setPendingApproval] = useState(false);
  const policy = useQuery({ queryKey: ['signup-policy'], queryFn: signupApi.policy });
  const schema = useMemo(
    () =>
      z
        .object({
          email: z.email(t('signUp.invalidEmail')),
          displayName: z.string().trim().min(1, t('signUp.nameRequired')).max(100),
          password: z.string().min(8, t('signUp.passwordMin')).max(128),
          passwordConfirm: z.string(),
          agreeTerms: z.boolean().refine((v) => v, t('signUp.agreeRequired')),
        })
        .refine((v) => v.password === v.passwordConfirm, { path: ['passwordConfirm'], message: t('signUp.passwordMismatch') }),
    [t],
  );
  const { register, handleSubmit, formState } = useForm<z.infer<typeof schema>>({
    resolver: zodResolver(schema),
    defaultValues: { agreeTerms: false },
  });

  const onSubmit = handleSubmit(async (form) => {
    setError(null);
    try {
      const result = await signupApi.signUp({
        email: form.email,
        password: form.password,
        displayName: form.displayName,
        agreeTerms: form.agreeTerms,
      });
      if (result.status === 'PENDING_VERIFICATION') {
        navigate(withReturnTo(`/sign-up/verify?email=${encodeURIComponent(form.email)}`, returnTo));
      } else if (result.status === 'PENDING_APPROVAL') {
        setPendingApproval(true);
      } else {
        navigate(withReturnTo('/sign-in', returnTo), { replace: true });
      }
    } catch (e) {
      setError(errorMessage(e));
    }
  });

  if (policy.isPending) {
    return <LoadingState />;
  }

  if (pendingApproval) {
    return (
      <Stack spacing={2}>
        <Typography variant="h4" component="h1">
          {t('signUp.pendingTitle')}
        </Typography>
        <Alert severity="info">{t('signUp.pendingBody')}</Alert>
        <Button component={RouterLink} to={withReturnTo('/sign-in', returnTo)} variant="outlined">
          {t('signUp.toSignIn')}
        </Button>
      </Stack>
    );
  }

  const p = policy.data;
  return (
    <Stack spacing={3} component="form" noValidate onSubmit={onSubmit}>
      <div>
        <Typography variant="h4" component="h1">
          {t('signUp.title')}
        </Typography>
        <Typography variant="body2" sx={{ color: 'text.secondary', mt: 0.75 }}>
          <Trans
            t={t}
            i18nKey="signUp.haveAccount"
            components={{
              cta: <Link component={RouterLink} to={withReturnTo('/sign-in', returnTo)} underline="hover" sx={{ fontWeight: 600 }} />,
            }}
          />
        </Typography>
      </div>

      {p && !p.enabled && <Alert severity="warning">{t('signUp.disabled')}</Alert>}
      {p && p.enabled && p.allowedEmailDomains.length > 0 && (
        <Alert severity="info">{t('signUp.domainsOnly', { domains: p.allowedEmailDomains.map((d) => `@${d}`).join(', ') })}</Alert>
      )}
      {error && <Alert severity="error">{error}</Alert>}

      <TextField
        label={t('signUp.email')}
        type="email"
        autoComplete="email"
        autoFocus
        {...register('email')}
        error={!!formState.errors.email}
        helperText={formState.errors.email?.message ?? (p?.requireEmailVerification ? t('signUp.emailHelp') : undefined)}
      />
      <TextField
        label={t('signUp.displayName')}
        autoComplete="name"
        {...register('displayName')}
        error={!!formState.errors.displayName}
        helperText={formState.errors.displayName?.message ?? t('signUp.displayNameHelp')}
      />
      <TextField
        label={t('signUp.password')}
        type="password"
        autoComplete="new-password"
        {...register('password')}
        error={!!formState.errors.password}
        helperText={formState.errors.password?.message ?? t('signUp.passwordHelp')}
      />
      <TextField
        label={t('signUp.passwordConfirm')}
        type="password"
        autoComplete="new-password"
        {...register('passwordConfirm')}
        error={!!formState.errors.passwordConfirm}
        helperText={formState.errors.passwordConfirm?.message}
      />
      <div>
        <FormControlLabel control={<Checkbox {...register('agreeTerms')} />} label={t('signUp.agree')} />
        {formState.errors.agreeTerms && <FormHelperText error>{formState.errors.agreeTerms.message}</FormHelperText>}
      </div>
      <Button type="submit" size="large" variant="contained" loading={formState.isSubmitting} disabled={p && !p.enabled}>
        {t('signUp.submit')}
      </Button>
    </Stack>
  );
}
