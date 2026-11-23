import { zodResolver } from '@hookform/resolvers/zod';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import useMediaQuery from '@mui/material/useMediaQuery';
import { useMemo } from 'react';
import { useForm } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';
import { z } from 'zod';

import { projectApi } from '@/shared/api/endpoints';
import { errorMessage, isApiError } from '@/shared/api/http';
import type { ProjectModule } from '@/shared/api/types';

/** 모듈 key 모양 — api 의 ProjectModule.KEY_PATTERN 과 같다(영문 소문자로 시작하는 1~32자, 소문자 · 숫자 · 하이픈, 하이픈으로 끝나지 않음) */
export const MODULE_KEY_PATTERN = /^[a-z]([a-z0-9-]{0,30}[a-z0-9])?$/;

interface Props {
  slug: string;
  projectKey: string;
  /** 있으면 고치기(이름 · 설명), 없으면 더하기 */
  module?: ProjectModule;
  onClose: () => void;
  onSaved: (module: ProjectModule) => void;
}

/**
 * 모듈 더하기 · 고치기 — key(더할 때만, 바꿀 수 없다) · 이름 · 설명. 규칙은 미리 알려 주고 판정은 api 가 한다.
 * 고칠 때는 읽은 rowVersion 을 함께 보낸다 — 그 사이 다른 사람이 고쳤으면 409 를 알린다.
 */
export function ModuleDialog({ slug, projectKey, module, onClose, onSaved }: Props) {
  const { t } = useTranslation(['workspace', 'common']);
  const fullScreen = useMediaQuery((theme) => theme.breakpoints.down('sm'));
  const editing = module !== undefined;

  const schema = useMemo(
    () =>
      z.object({
        key: z.string().superRefine((value, ctx) => {
          if (!editing && !MODULE_KEY_PATTERN.test(value.trim())) {
            ctx.addIssue({ code: 'custom', message: t('projects.modules.keyInvalid') });
          }
        }),
        name: z.string().trim().min(1, t('projects.modules.nameRequired')).max(100),
        description: z.string().max(500, t('projects.modules.descriptionTooLong')),
      }),
    [t, editing],
  );
  type Form = z.infer<typeof schema>;

  const { register, handleSubmit, formState, setError } = useForm<Form>({
    resolver: zodResolver(schema),
    defaultValues: { key: module?.key ?? '', name: module?.name ?? '', description: module?.description ?? '' },
  });

  const onSubmit = handleSubmit(async (form) => {
    try {
      const saved = editing
        ? await projectApi.updateModule(slug, projectKey, module.key, {
            name: form.name.trim(),
            description: form.description.trim() || null,
            rowVersion: module.rowVersion,
          })
        : await projectApi.createModule(slug, projectKey, {
            key: form.key.trim(),
            name: form.name.trim(),
            description: form.description.trim() || undefined,
          });
      toast.success(editing ? t('projects.modules.saved') : t('projects.modules.created', { name: saved.name }));
      onSaved(saved);
      onClose();
    } catch (e) {
      if (isApiError(e, 'MODULE_KEY_TAKEN')) {
        setError('key', { message: t('projects.modules.keyTaken') }, { shouldFocus: true });
      } else if (isApiError(e, 'INVALID_MODULE_KEY')) {
        setError('key', { message: e.message }, { shouldFocus: true });
      } else if (isApiError(e, 'INVALID_MODULE_NAME')) {
        setError('name', { message: e.message }, { shouldFocus: true });
      } else if (isApiError(e, 'MODULE_DESCRIPTION_TOO_LONG')) {
        setError('description', { message: e.message }, { shouldFocus: true });
      } else if (isApiError(e, 'CONCURRENT_UPDATE')) {
        toast.error(t('projects.modules.conflict'));
      } else {
        toast.error(errorMessage(e));
      }
    }
  });

  return (
    <Dialog open onClose={onClose} maxWidth="sm" fullWidth fullScreen={fullScreen}>
      <Box component="form" noValidate onSubmit={onSubmit} sx={{ display: 'contents' }}>
        <DialogTitle sx={{ fontWeight: 700 }}>{editing ? t('projects.modules.editTitle') : t('projects.modules.createTitle')}</DialogTitle>
        <DialogContent>
          <Stack spacing={2.5} sx={{ pt: 1 }}>
            <TextField
              label={t('projects.modules.keyLabel')}
              placeholder="web-frontend"
              autoFocus={!editing}
              {...register('key')}
              disabled={editing}
              error={!!formState.errors.key}
              helperText={formState.errors.key?.message ?? (editing ? t('projects.modules.keyFixed') : t('projects.modules.keyHelp'))}
              slotProps={{
                htmlInput: { spellCheck: false, autoCapitalize: 'none', autoCorrect: 'off', maxLength: 32, sx: { fontFamily: 'monospace' } },
                inputLabel: { shrink: true },
              }}
            />
            <TextField
              label={t('projects.modules.nameLabel')}
              placeholder={t('projects.modules.namePlaceholder')}
              autoFocus={editing}
              {...register('name')}
              error={!!formState.errors.name}
              helperText={formState.errors.name?.message}
              slotProps={{ htmlInput: { maxLength: 100 }, inputLabel: { shrink: true } }}
            />
            <TextField
              label={t('projects.modules.descriptionLabel')}
              multiline
              minRows={2}
              {...register('description')}
              error={!!formState.errors.description}
              helperText={formState.errors.description?.message ?? t('projects.modules.descriptionHelp')}
              slotProps={{ htmlInput: { maxLength: 500 }, inputLabel: { shrink: true } }}
            />
          </Stack>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2.5 }}>
          <Button color="inherit" onClick={onClose}>
            {t('common:actions.cancel')}
          </Button>
          <Button type="submit" variant="contained" loading={formState.isSubmitting} disabled={editing && !formState.isDirty}>
            {editing ? t('common:actions.save') : t('projects.modules.submitCreate')}
          </Button>
        </DialogActions>
      </Box>
    </Dialog>
  );
}
