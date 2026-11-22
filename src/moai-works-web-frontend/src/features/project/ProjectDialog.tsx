import { zodResolver } from '@hookform/resolvers/zod';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import MenuItem from '@mui/material/MenuItem';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import useMediaQuery from '@mui/material/useMediaQuery';
import { useQueryClient } from '@tanstack/react-query';
import { useMemo, useRef } from 'react';
import { Controller, useForm, useWatch } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';
import { z } from 'zod';

import { workspaceKeys } from '@/features/workspace/queries';
import { projectApi } from '@/shared/api/endpoints';
import { errorMessage, isApiError } from '@/shared/api/http';
import type { ProjectDetail } from '@/shared/api/types';
import { useLabels } from '@/shared/lib/labels';

import { checkProjectKey, suggestProjectKey } from './project-key';
import { projectKeys } from './queries';

const visibilities = ['WORKSPACE', 'RESTRICTED'] as const;

interface Props {
  slug: string;
  /** 있으면 설정 고치기, 없으면 새로 만들기 */
  project?: ProjectDetail;
  onClose: () => void;
  /** 만들거나 고친 뒤 */
  onSaved?: (project: ProjectDetail) => void;
}

/**
 * 프로젝트 만들기 · 설정 — 이름 · 주소 이름 · 설명 · 읽을 수 있는 사람.
 * 주소 이름은 만들 때만 정하고 바꿀 수 없다. 규칙은 미리 알려 주고, 판정은 api 가 한다.
 */
export function ProjectDialog({ slug, project, onClose, onSaved }: Props) {
  const { t } = useTranslation(['workspace', 'common']);
  const labels = useLabels();
  const queryClient = useQueryClient();
  const fullScreen = useMediaQuery((theme) => theme.breakpoints.down('sm'));
  const editing = project !== undefined;
  /** 사람이 주소 이름을 직접 고쳤으면 이름에서 제안하는 것을 멈춘다 */
  const keyTouched = useRef(editing);

  const schema = useMemo(
    () =>
      z.object({
        name: z.string().trim().min(1, t('projects.dialog.nameRequired')).max(100),
        key: z.string().superRefine((value, ctx) => {
          if (editing) {
            return;
          }
          const problem = checkProjectKey(value);
          if (problem) {
            ctx.addIssue({
              code: 'custom',
              message: problem === 'reserved' ? t('projects.dialog.keyReserved') : t('projects.dialog.keyInvalid'),
            });
          }
        }),
        description: z.string().max(1000),
        visibility: z.enum(visibilities),
      }),
    [t, editing],
  );
  type Form = z.infer<typeof schema>;

  const { register, control, handleSubmit, formState, setValue, setError } = useForm<Form>({
    resolver: zodResolver(schema),
    defaultValues: {
      name: project?.name ?? '',
      key: project?.key ?? '',
      description: project?.description ?? '',
      visibility: project?.visibility ?? 'WORKSPACE',
    },
  });
  const visibility = useWatch({ control, name: 'visibility' });
  const nameField = register('name');
  const keyField = register('key');

  const onSubmit = handleSubmit(async (form) => {
    try {
      const saved = editing
        ? await projectApi.update(slug, project.key, {
            name: form.name,
            description: form.description.trim() || null,
            visibility: form.visibility,
          })
        : await projectApi.create(slug, {
            key: form.key,
            name: form.name,
            description: form.description.trim() || undefined,
            visibility: form.visibility,
          });
      queryClient.setQueryData(projectKeys.detail(slug, saved.key), saved);
      void queryClient.invalidateQueries({ queryKey: projectKeys.lists(slug) });
      void queryClient.invalidateQueries({ queryKey: workspaceKeys.audit(slug) });
      toast.success(editing ? t('projects.dialog.saved') : t('projects.dialog.created'));
      onSaved?.(saved);
      onClose();
    } catch (e) {
      if (isApiError(e, 'PROJECT_KEY_TAKEN')) {
        setError('key', { message: t('projects.dialog.keyTaken') }, { shouldFocus: true });
      } else if (isApiError(e, 'RESERVED_PROJECT_KEY') || isApiError(e, 'INVALID_PROJECT_KEY')) {
        setError('key', { message: e.message }, { shouldFocus: true });
      } else {
        toast.error(errorMessage(e));
      }
    }
  });

  return (
    <Dialog open onClose={onClose} maxWidth="sm" fullWidth fullScreen={fullScreen}>
      <Box component="form" noValidate onSubmit={onSubmit} sx={{ display: 'contents' }}>
        <DialogTitle sx={{ fontWeight: 700 }}>{editing ? t('projects.dialog.editTitle') : t('projects.dialog.createTitle')}</DialogTitle>
        <DialogContent>
          <Stack spacing={2.5} sx={{ pt: 1 }}>
            <TextField
              label={t('projects.dialog.nameLabel')}
              placeholder={t('projects.dialog.namePlaceholder')}
              autoFocus
              {...nameField}
              onChange={(e) => {
                void nameField.onChange(e);
                if (!keyTouched.current) {
                  setValue('key', suggestProjectKey(e.target.value), { shouldValidate: formState.isSubmitted });
                }
              }}
              error={!!formState.errors.name}
              helperText={formState.errors.name?.message}
            />
            <TextField
              label={t('projects.dialog.keyLabel')}
              {...keyField}
              onChange={(e) => {
                keyTouched.current = true;
                void keyField.onChange(e);
              }}
              disabled={editing}
              error={!!formState.errors.key}
              helperText={formState.errors.key?.message ?? (editing ? t('projects.dialog.keyFixed') : t('projects.dialog.keyHelp'))}
              slotProps={{
                htmlInput: { spellCheck: false, autoCapitalize: 'none', autoCorrect: 'off', maxLength: 32, sx: { fontFamily: 'monospace' } },
                inputLabel: { shrink: true },
              }}
            />
            <TextField label={t('projects.dialog.descriptionLabel')} multiline minRows={2} {...register('description')} />
            <Controller
              control={control}
              name="visibility"
              render={({ field }) => (
                <TextField select label={t('projects.dialog.visibilityLabel')} {...field}>
                  {visibilities.map((v) => (
                    <MenuItem key={v} value={v}>
                      <Box sx={{ minWidth: 0 }}>
                        <Typography variant="body2" sx={{ fontWeight: 600 }}>
                          {labels.projectVisibility(v)}
                        </Typography>
                        <Typography variant="caption" sx={{ color: 'text.secondary', display: 'block', whiteSpace: 'normal' }}>
                          {labels.projectVisibilityHelp(v)}
                        </Typography>
                      </Box>
                    </MenuItem>
                  ))}
                </TextField>
              )}
            />
            {visibility === 'RESTRICTED' && <Alert severity="info">{t('projects.restrictedNote')}</Alert>}
          </Stack>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2.5 }}>
          <Button color="inherit" onClick={onClose}>
            {t('common:actions.cancel')}
          </Button>
          <Button type="submit" variant="contained" loading={formState.isSubmitting} disabled={editing && !formState.isDirty}>
            {editing ? t('common:actions.save') : t('projects.dialog.submitCreate')}
          </Button>
        </DialogActions>
      </Box>
    </Dialog>
  );
}
