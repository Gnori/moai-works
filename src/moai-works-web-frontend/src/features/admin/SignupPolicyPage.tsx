import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import CardHeader from '@mui/material/CardHeader';
import Divider from '@mui/material/Divider';
import Stack from '@mui/material/Stack';
import Switch from '@mui/material/Switch';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';

import { adminApi } from '@/shared/api/endpoints';
import { errorMessage } from '@/shared/api/http';
import type { SignupPolicy } from '@/shared/api/types';
import { PageHeader } from '@/shared/ui/PageHeader';
import { ErrorState, LoadingState } from '@/shared/ui/States';

function SwitchRow({ title, description, checked, onChange, disabled }: {
  title: string;
  description: ReactNode;
  checked: boolean;
  onChange: (v: boolean) => void;
  disabled?: boolean;
}) {
  return (
    <Stack direction="row" spacing={2} sx={{ alignItems: 'center', py: 1.5 }}>
      <Box sx={{ flex: 1 }}>
        <Typography variant="subtitle2">{title}</Typography>
        <Typography variant="body2" sx={{ color: 'text.secondary' }}>
          {description}
        </Typography>
      </Box>
      <Switch checked={checked} onChange={(e) => onChange(e.target.checked)} disabled={disabled} slotProps={{ input: { 'aria-label': title } }} />
    </Stack>
  );
}

const policyKey = ['admin', 'signup-policy'] as const;

/** 가입 정책 (시스템 관리자) — 가입 받기 · 도메인 제한 · 이메일 인증 · 관리자 승인은 서로 독립이다. 경로 가드가 system-admin 을 확인한다(표시만 — 판정은 api). */
export function SignupPolicyPage() {
  const query = useQuery({ queryKey: policyKey, queryFn: adminApi.signupPolicy });
  if (query.isPending) {
    return <LoadingState />;
  }
  if (query.isError) {
    return <ErrorState error={query.error} onRetry={() => query.refetch()} />;
  }
  // 저장한 값이 바뀌면 양식을 새로 만든다(key)
  return <SignupPolicyForm key={JSON.stringify(query.data)} initial={query.data} />;
}

function SignupPolicyForm({ initial }: { initial: SignupPolicy }) {
  const { t } = useTranslation(['admin', 'common']);
  const queryClient = useQueryClient();
  const [draft, setDraft] = useState<SignupPolicy>(initial);
  const [domains, setDomains] = useState(initial.allowedEmailDomains.join(', '));
  const [saving, setSaving] = useState(false);

  const set = <K extends keyof SignupPolicy>(key: K, value: SignupPolicy[K]) => setDraft({ ...draft, [key]: value });

  const save = async () => {
    setSaving(true);
    try {
      const saved = await adminApi.updateSignupPolicy({
        ...draft,
        allowedEmailDomains: domains.split(/[,\s]+/).map((d) => d.trim().replace(/^@/, '').toLowerCase()).filter(Boolean),
      });
      queryClient.setQueryData(policyKey, saved);
      toast.success(t('policy.saved'));
    } catch (e) {
      toast.error(errorMessage(e));
    } finally {
      setSaving(false);
    }
  };

  return (
    <>
      <PageHeader
        eyebrow={t('eyebrow')}
        title={t('policy.title')}
        description={t('policy.description')}
        actions={
          <Button variant="contained" onClick={save} loading={saving}>
            {t('common:actions.save')}
          </Button>
        }
      />
      <Stack spacing={2}>
        <Card>
          <CardHeader title={t('policy.acceptTitle')} />
          <CardContent sx={{ pt: 1 }}>
            <SwitchRow
              title={t('policy.enabled')}
              description={t('policy.enabledBody')}
              checked={draft.enabled}
              onChange={(v) => set('enabled', v)}
            />
            <Divider />
            <Box sx={{ py: 1.5 }}>
              <Typography variant="subtitle2">{t('policy.domains')}</Typography>
              <Typography variant="body2" sx={{ color: 'text.secondary', mb: 1.5 }}>
                {t('policy.domainsBody')}
              </Typography>
              <TextField
                size="small"
                placeholder={t('policy.domainsPlaceholder')}
                value={domains}
                onChange={(e) => setDomains(e.target.value)}
                sx={{ width: '100%', maxWidth: 480 }}
                slotProps={{ htmlInput: { 'aria-label': t('policy.domains') } }}
              />
            </Box>
          </CardContent>
        </Card>
        <Card>
          <CardHeader title={t('policy.activationTitle')} subheader={t('policy.activationSubtitle')} />
          <CardContent sx={{ pt: 1 }}>
            <SwitchRow
              title={t('policy.verify')}
              description={t('policy.verifyBody')}
              checked={draft.requireEmailVerification}
              onChange={(v) => set('requireEmailVerification', v)}
            />
            <Divider />
            <SwitchRow
              title={t('policy.approval')}
              description={t('policy.approvalBody')}
              checked={draft.requireAdminApproval}
              onChange={(v) => set('requireAdminApproval', v)}
            />
            {!draft.requireEmailVerification && !draft.requireAdminApproval && draft.enabled && (
              <Alert severity="warning" sx={{ mt: 1 }}>
                {t('policy.noGateWarning')}
              </Alert>
            )}
          </CardContent>
        </Card>
        <Card>
          <CardHeader title={t('policy.limitsTitle')} />
          <CardContent>
            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ maxWidth: 560 }}>
              <TextField
                type="number"
                label={t('policy.ratePerIp')}
                value={draft.rateLimitPerIpHour}
                onChange={(e) => set('rateLimitPerIpHour', Number(e.target.value))}
                slotProps={{ htmlInput: { min: 1, max: 10000 } }}
              />
              <TextField
                type="number"
                label={t('policy.teamGrantDefault')}
                helperText={t('policy.teamGrantDefaultHelp')}
                value={draft.teamWorkspaceGrantDefault}
                onChange={(e) => set('teamWorkspaceGrantDefault', Number(e.target.value))}
                slotProps={{ htmlInput: { min: 1, max: 100 } }}
              />
            </Stack>
          </CardContent>
        </Card>
      </Stack>
    </>
  );
}
