import { zodResolver } from '@hookform/resolvers/zod';
import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import CardHeader from '@mui/material/CardHeader';
import MenuItem from '@mui/material/MenuItem';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import { useQueryClient } from '@tanstack/react-query';
import { useMemo } from 'react';
import { Controller, useForm, useWatch } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';
import { z } from 'zod';

import { useSession } from '@/features/auth/session';
import { workspaceApi } from '@/shared/api/endpoints';
import { errorMessage } from '@/shared/api/http';
import { useLabels } from '@/shared/lib/labels';
import { PageHeader } from '@/shared/ui/PageHeader';

import { useRoleText, useRolesQuery, workspaceKeys } from './queries';
import { useWorkspace } from './workspace-context';

const visibilities = ['PUBLIC', 'PRIVATE'] as const;
const joinPolicies = ['APPROVAL', 'AUTO'] as const;

/** 워크스페이스 설정 — 이름 · 설명 · 공개 범위 · 가입 방식 · 기본 역할. 주소(workspace-…)는 서버가 정하고 바꿀 수 없다. */
export function SettingsPage() {
  const { t } = useTranslation(['workspace', 'common']);
  const labels = useLabels();
  const roleText = useRoleText();
  const { workspace } = useWorkspace();
  const { refresh } = useSession();
  const queryClient = useQueryClient();
  const personal = workspace.kind === 'PERSONAL';
  const roles = useRolesQuery(workspace.slug);

  const schema = useMemo(
    () =>
      z.object({
        name: z.string().trim().min(1, t('settings.nameRequired')).max(100),
        description: z.string().max(1000),
        visibility: z.enum(visibilities),
        joinPolicy: z.enum(joinPolicies),
        domains: z.string(),
        defaultRoleKey: z.string().min(1),
      }),
    [t],
  );
  type Form = z.infer<typeof schema>;

  const { register, control, handleSubmit, formState, reset } = useForm<Form>({
    resolver: zodResolver(schema),
    defaultValues: {
      name: workspace.name,
      description: workspace.description ?? '',
      visibility: workspace.visibility,
      joinPolicy: workspace.joinPolicy,
      domains: workspace.allowedJoinDomains.join(', '),
      defaultRoleKey: workspace.defaultRoleKey,
    },
  });

  // 가입 방식 · 가입 도메인은 퍼블릭에만 뜻이 있다 — 프라이빗은 초대로만 들어온다
  const isPublic = useWatch({ control, name: 'visibility' }) === 'PUBLIC';

  const onSubmit = handleSubmit(async (form) => {
    try {
      const updated = await workspaceApi.updateSettings(workspace.slug, {
        name: form.name,
        description: form.description.trim() || null,
        visibility: form.visibility,
        joinPolicy: form.joinPolicy,
        allowedJoinDomains: form.domains
          .split(/[,\s]+/)
          .map((d) => d.trim().replace(/^@/, ''))
          .filter(Boolean),
        defaultRoleKey: form.defaultRoleKey,
      });
      queryClient.setQueryData(workspaceKeys.detail(workspace.slug), updated);
      reset(form);
      await refresh();
      toast.success(t('settings.saved'));
    } catch (e) {
      toast.error(errorMessage(e));
    }
  });

  return (
    <>
      <PageHeader eyebrow={workspace.name} title={t('settings.title')} />
      <Card component="form" noValidate onSubmit={onSubmit}>
        <CardHeader title={t('settings.basics')} />
        <CardContent>
          <Stack spacing={2.5} sx={{ maxWidth: 560 }}>
            <TextField
              label={t('settings.address')}
              value={workspace.slug}
              helperText={t('settings.addressHelp')}
              slotProps={{ htmlInput: { readOnly: true, sx: { fontFamily: 'monospace' } } }}
            />
            <TextField
              label={t('settings.name')}
              {...register('name')}
              error={!!formState.errors.name}
              helperText={formState.errors.name?.message}
            />
            <TextField label={t('settings.description')} multiline minRows={2} {...register('description')} />
            {personal && <Alert severity="info">{t('settings.personalNotice')}</Alert>}
            <Controller
              control={control}
              name="visibility"
              render={({ field }) => (
                <TextField select label={t('settings.visibility')} {...field} disabled={personal} helperText={personal ? undefined : labels.visibilityHelp(field.value)}>
                  {visibilities.map((v) => (
                    <MenuItem key={v} value={v}>
                      {labels.visibility(v)}
                    </MenuItem>
                  ))}
                </TextField>
              )}
            />
            <Controller
              control={control}
              name="joinPolicy"
              render={({ field }) => (
                <TextField select label={t('settings.joinPolicy')} {...field} disabled={personal || !isPublic} helperText={isPublic ? undefined : t('settings.joinPolicyPrivate')}>
                  {joinPolicies.map((v) => (
                    <MenuItem key={v} value={v}>
                      {labels.joinPolicy(v)}
                    </MenuItem>
                  ))}
                </TextField>
              )}
            />
            <TextField
              label={t('settings.domains')}
              placeholder={t('settings.domainsPlaceholder')}
              {...register('domains')}
              disabled={personal || !isPublic}
              helperText={t('settings.domainsHelp')}
            />
            <Controller
              control={control}
              name="defaultRoleKey"
              render={({ field }) => (
                <TextField select label={t('settings.defaultRole')} {...field}>
                  {(roles.data ?? [])
                    .filter((r) => r.key !== 'owner')
                    .map((r) => (
                      <MenuItem key={r.key} value={r.key}>
                        {roleText.name(r)}
                      </MenuItem>
                    ))}
                  {!roles.data && <MenuItem value={field.value}>{field.value}</MenuItem>}
                </TextField>
              )}
            />
            <div>
              <Button type="submit" variant="contained" loading={formState.isSubmitting} disabled={!formState.isDirty}>
                {t('common:actions.save')}
              </Button>
            </div>
          </Stack>
        </CardContent>
      </Card>
    </>
  );
}
