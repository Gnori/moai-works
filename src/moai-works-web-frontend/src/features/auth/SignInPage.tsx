import { zodResolver } from '@hookform/resolvers/zod';
import VisibilityOffRounded from '@mui/icons-material/VisibilityOffRounded';
import VisibilityRounded from '@mui/icons-material/VisibilityRounded';
import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import IconButton from '@mui/material/IconButton';
import InputAdornment from '@mui/material/InputAdornment';
import Link from '@mui/material/Link';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useMemo, useState } from 'react';
import { useForm } from 'react-hook-form';
import { Trans, useTranslation } from 'react-i18next';
import { Link as RouterLink, useNavigate, useSearchParams } from 'react-router';
import { z } from 'zod';

import { ApiError } from '@/shared/api/http';
import { safeReturnPath, withReturnTo } from '@/shared/lib/post-login-path';

import { postLoginPath } from './guards';
import { useSession } from './session';

/** 로그인 — 아이디 또는 이메일 + 비밀번호. 토큰은 BFF 세션에만 남는다. */
export function SignInPage() {
  const { t } = useTranslation('auth');
  const { signIn } = useSession();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState<ApiError | null>(null);
  const returnTo = safeReturnPath(params.get('returnTo'));
  const schema = useMemo(
    () =>
      z.object({
        username: z.string().trim().min(1, t('signIn.usernameRequired')),
        password: z.string().min(1, t('signIn.passwordRequired')),
      }),
    [t],
  );
  const { register, handleSubmit, formState } = useForm<z.infer<typeof schema>>({ resolver: zodResolver(schema) });

  const onSubmit = handleSubmit(async ({ username, password }) => {
    setError(null);
    try {
      const me = await signIn(username, password);
      navigate(postLoginPath(me, returnTo), { replace: true });
    } catch (e) {
      setError(e instanceof ApiError ? e : new ApiError(0, 'UNKNOWN', t('signIn.fallbackError')));
    }
  });

  return (
    <Stack spacing={3} component="form" noValidate onSubmit={onSubmit}>
      <div>
        <Typography variant="h4" component="h1">
          {t('signIn.title')}
        </Typography>
        <Typography variant="body2" sx={{ color: 'text.secondary', mt: 0.75 }}>
          <Trans
            t={t}
            i18nKey="signIn.noAccount"
            components={{
              cta: <Link component={RouterLink} to={withReturnTo('/sign-up', returnTo)} underline="hover" sx={{ fontWeight: 600 }} />,
            }}
          />
        </Typography>
      </div>

      {returnTo?.startsWith('/invitations/') && <Alert severity="info">{t('signIn.invitationNotice')}</Alert>}

      {error && (
        <Alert severity={error.code === 'ACCOUNT_NOT_ACTIVE' ? 'warning' : 'error'}>
          {error.message}
          {error.code === 'ACCOUNT_NOT_ACTIVE' && (
            <>
              {' '}
              <Link component={RouterLink} to="/sign-up/verify" underline="always">
                {t('signIn.enterCode')}
              </Link>
            </>
          )}
        </Alert>
      )}

      <TextField
        label={t('signIn.username')}
        autoComplete="username"
        autoFocus
        {...register('username')}
        error={!!formState.errors.username}
        helperText={formState.errors.username?.message}
      />
      <TextField
        label={t('signIn.password')}
        type={showPassword ? 'text' : 'password'}
        autoComplete="current-password"
        {...register('password')}
        error={!!formState.errors.password}
        helperText={formState.errors.password?.message}
        slotProps={{
          input: {
            endAdornment: (
              <InputAdornment position="end">
                <IconButton
                  edge="end"
                  aria-label={showPassword ? t('signIn.hidePassword') : t('signIn.showPassword')}
                  onClick={() => setShowPassword((v) => !v)}
                >
                  {showPassword ? <VisibilityOffRounded /> : <VisibilityRounded />}
                </IconButton>
              </InputAdornment>
            ),
          },
        }}
      />
      <Button type="submit" size="large" variant="contained" loading={formState.isSubmitting}>
        {t('signIn.submit')}
      </Button>
    </Stack>
  );
}
