import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import Link from '@mui/material/Link';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useEffect, useState, type SubmitEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { Link as RouterLink, useNavigate, useSearchParams } from 'react-router';
import { toast } from 'sonner';

import { signupApi } from '@/shared/api/endpoints';
import { errorMessage } from '@/shared/api/http';
import { withReturnTo } from '@/shared/lib/post-login-path';

const RESEND_COOLDOWN = 60;

/** 이메일 인증 코드 — 6자리. 다시 보내기는 60초 간격. */
export function VerifyPage() {
  const { t } = useTranslation('auth');
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const [email, setEmail] = useState(params.get('email') ?? '');
  const [code, setCode] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [cooldown, setCooldown] = useState(params.get('email') ? RESEND_COOLDOWN : 0);
  const [approvalPending, setApprovalPending] = useState(false);

  useEffect(() => {
    if (cooldown <= 0) {
      return;
    }
    const timer = window.setTimeout(() => setCooldown((s) => s - 1), 1000);
    return () => window.clearTimeout(timer);
  }, [cooldown]);

  const submit = async (event: SubmitEvent<HTMLFormElement>) => {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      const result = await signupApi.verify(email.trim(), code);
      if (result.status === 'PENDING_APPROVAL') {
        setApprovalPending(true);
      } else {
        toast.success(t('verify.verified'));
        navigate(withReturnTo('/sign-in', params.get('returnTo')), { replace: true });
      }
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setSubmitting(false);
    }
  };

  const resend = async () => {
    try {
      await signupApi.resend(email.trim());
      toast.success(t('verify.resent'));
      setCooldown(RESEND_COOLDOWN);
    } catch (e) {
      toast.error(errorMessage(e));
    }
  };

  if (approvalPending) {
    return (
      <Stack spacing={2}>
        <Typography variant="h4" component="h1">
          {t('verify.approvalTitle')}
        </Typography>
        <Alert severity="info">{t('verify.approvalBody')}</Alert>
        <Button component={RouterLink} to={withReturnTo('/sign-in', params.get('returnTo'))} variant="outlined">
          {t('signUp.toSignIn')}
        </Button>
      </Stack>
    );
  }

  return (
    <Stack spacing={3} component="form" noValidate onSubmit={submit}>
      <div>
        <Typography variant="h4" component="h1">
          {t('verify.title')}
        </Typography>
        <Typography variant="body2" sx={{ color: 'text.secondary', mt: 0.75 }}>
          {t('verify.description')}
        </Typography>
      </div>
      {error && <Alert severity="error">{error}</Alert>}
      <TextField label={t('verify.email')} type="email" value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="email" />
      <TextField
        label={t('verify.code')}
        value={code}
        onChange={(e) => setCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
        autoFocus
        autoComplete="one-time-code"
        slotProps={{
          htmlInput: {
            inputMode: 'numeric',
            pattern: '\\d{6}',
            maxLength: 6,
            style: { letterSpacing: '0.5em', fontSize: '1.25rem', fontWeight: 600, textAlign: 'center' },
          },
        }}
      />
      <Button type="submit" size="large" variant="contained" loading={submitting} disabled={code.length !== 6 || !email}>
        {t('verify.submit')}
      </Button>
      <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center' }}>
        <Button onClick={resend} disabled={cooldown > 0 || !email} size="small">
          {cooldown > 0 ? t('verify.resendIn', { seconds: cooldown }) : t('verify.resend')}
        </Button>
        <Link component={RouterLink} to={withReturnTo('/sign-in', params.get('returnTo'))} underline="hover" variant="body2">
          {t('verify.toSignIn')}
        </Link>
      </Stack>
    </Stack>
  );
}
