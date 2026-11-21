import { zodResolver } from '@hookform/resolvers/zod';
import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import MenuItem from '@mui/material/MenuItem';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import { useMemo, useState } from 'react';
import { Controller, useForm, useWatch } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router';
import { toast } from 'sonner';
import { z } from 'zod';

import { useMe, useSession } from '@/features/auth/session';
import { workspaceApi } from '@/shared/api/endpoints';
import { errorMessage } from '@/shared/api/http';
import { useLabels } from '@/shared/lib/labels';
import { PageHeader } from '@/shared/ui/PageHeader';

const visibilities = ['PUBLIC', 'PRIVATE'] as const;
const joinPolicies = ['APPROVAL', 'AUTO'] as const;

/**
 * 새 팀 워크스페이스 — 만든 사람이 소유자가 된다.
 * 시스템 관리자가 허용한 계정만, 허용한 수까지 만든다. 못 만드는 계정에게는 이유를 보이고 칸을 꺼 둔다(판정은 api).
 * 퍼블릭은 찾기에 보이고 가입할 수 있다(자동 가입 · 승인 후 가입). 프라이빗은 보이지 않고 초대로만 들어온다.
 * 주소는 받지 않는다: 서버가 workspace-<고유 id> 로 정해 돌려주고, 그 주소로 들어간다.
 */
export function CreateWorkspacePage() {
  const { t } = useTranslation(['workspace', 'common']);
  const labels = useLabels();
  const navigate = useNavigate();
  const { refresh } = useSession();
  const me = useMe();
  const [error, setError] = useState<string | null>(null);
  const remaining = Math.max(me.teamWorkspaceLimit - me.teamWorkspacesCreated, 0);
  const blocked = remaining === 0;

  const schema = useMemo(
    () =>
      z.object({
        name: z.string().trim().min(1, t('create.nameRequired')).max(100),
        description: z.string().max(1000),
        visibility: z.enum(visibilities),
        joinPolicy: z.enum(joinPolicies),
      }),
    [t],
  );
  type Form = z.infer<typeof schema>;

  const { register, control, handleSubmit, formState } = useForm<Form>({
    resolver: zodResolver(schema),
    defaultValues: { name: '', description: '', visibility: 'PUBLIC', joinPolicy: 'APPROVAL' },
  });
  const visibility = useWatch({ control, name: 'visibility' });

  const onSubmit = handleSubmit(async (form) => {
    setError(null);
    try {
      const ws = await workspaceApi.create({
        name: form.name,
        description: form.description.trim() || undefined,
        visibility: form.visibility,
        joinPolicy: form.joinPolicy,
      });
      await refresh();
      toast.success(t('create.created', { name: ws.name }));
      navigate(`/w/${encodeURIComponent(ws.slug)}`);
    } catch (e) {
      setError(errorMessage(e));
    }
  });

  return (
    <>
      <PageHeader title={t('create.title')} description={t('create.description')} />
      <Card component="form" noValidate onSubmit={onSubmit}>
        <CardContent>
          <Stack spacing={2.5} sx={{ maxWidth: 560 }}>
            {error && <Alert severity="error">{error}</Alert>}
            {me.teamWorkspaceLimit === 0 ? (
              <Alert severity="info">{t('create.notAllowed')}</Alert>
            ) : blocked ? (
              <Alert severity="warning">{t('create.limitReached', { limit: me.teamWorkspaceLimit })}</Alert>
            ) : (
              <Alert severity="info">
                {t('create.allowance', { limit: me.teamWorkspaceLimit, created: me.teamWorkspacesCreated, remaining })}
              </Alert>
            )}
            <TextField
              label={t('create.name')}
              autoFocus
              disabled={blocked}
              {...register('name')}
              error={!!formState.errors.name}
              helperText={formState.errors.name?.message}
            />
            <TextField label={t('create.description2')} multiline minRows={2} disabled={blocked} {...register('description')} />
            <Controller
              control={control}
              name="visibility"
              render={({ field }) => (
                <TextField select label={t('create.visibility')} disabled={blocked} helperText={labels.visibilityHelp(field.value)} {...field}>
                  {visibilities.map((v) => (
                    <MenuItem key={v} value={v}>
                      {labels.visibility(v)}
                    </MenuItem>
                  ))}
                </TextField>
              )}
            />
            {visibility === 'PUBLIC' && (
              <Controller
                control={control}
                name="joinPolicy"
                render={({ field }) => (
                  <TextField select label={t('create.joinPolicy')} disabled={blocked} {...field}>
                    {joinPolicies.map((v) => (
                      <MenuItem key={v} value={v}>
                        {labels.joinPolicy(v)}
                      </MenuItem>
                    ))}
                  </TextField>
                )}
              />
            )}
            <Stack direction="row" spacing={1}>
              <Button type="submit" variant="contained" loading={formState.isSubmitting} disabled={blocked}>
                {t('create.submit')}
              </Button>
              <Button color="inherit" onClick={() => navigate(-1)}>
                {t('common:actions.cancel')}
              </Button>
            </Stack>
          </Stack>
        </CardContent>
      </Card>
    </>
  );
}
