import { zodResolver } from '@hookform/resolvers/zod';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Checkbox from '@mui/material/Checkbox';
import Chip from '@mui/material/Chip';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import FormControlLabel from '@mui/material/FormControlLabel';
import FormHelperText from '@mui/material/FormHelperText';
import MenuItem from '@mui/material/MenuItem';
import Stack from '@mui/material/Stack';
import { useTheme } from '@mui/material/styles';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import useMediaQuery from '@mui/material/useMediaQuery';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useMemo, useState } from 'react';
import { useForm, useWatch } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';
import { z } from 'zod';

import { workspaceApi } from '@/shared/api/endpoints';
import { errorMessage } from '@/shared/api/http';
import type { PermissionCode, Role } from '@/shared/api/types';
import { isBuiltinRole, ownerOnlyPermissions, permissionGroups, useLabels } from '@/shared/lib/labels';

import { useRoleText, workspaceKeys } from './queries';
import { canIncludePermission } from './role-rules';
import { useWorkspace } from './workspace-context';

export type RoleEditorTarget = { mode: 'create' } | { mode: 'edit'; role: Role };

/**
 * 사용자 정의 역할 만들기 · 고치기 — 이름 · 설명 · 권한 묶음.
 * 넣을 수 없는 권한(내게 없는 것 · 소유자 전용)은 처음부터 꺼 둔다. api 도 같은 규칙으로 막는다(ESCALATION_DENIED · OWNER_ONLY_PERMISSION).
 */
export function RoleEditorDialog({ target, roles, onClose }: { target: RoleEditorTarget; roles: Role[]; onClose: () => void }) {
  const { t } = useTranslation(['workspace', 'common']);
  const { workspace } = useWorkspace();
  const labels = useLabels();
  const roleText = useRoleText();
  const queryClient = useQueryClient();
  const theme = useTheme();
  const fullScreen = useMediaQuery(theme.breakpoints.down('sm'));
  const mine = workspace.myPermissions;
  const editing = target.mode === 'edit' ? target.role : null;
  const [template, setTemplate] = useState('');

  const schema = useMemo(
    () =>
      z.object({
        name: z.string().trim().min(1, t('roleEditor.nameRequired')).max(100),
        description: z.string().trim().max(500),
        permissions: z.array(z.string()).min(1, t('roleEditor.permissionRequired')),
      }),
    [t],
  );
  type Form = z.infer<typeof schema>;
  const { register, handleSubmit, control, setValue, formState } = useForm<Form>({
    resolver: zodResolver(schema),
    defaultValues: {
      name: editing?.name ?? '',
      description: editing?.description ?? '',
      permissions: editing?.permissions ?? [],
    },
  });
  const selected = useWatch({ control, name: 'permissions' });
  const setSelected = (next: string[]) => setValue('permissions', next, { shouldValidate: formState.isSubmitted, shouldDirty: true });

  const toggle = (code: PermissionCode) =>
    setSelected(selected.includes(code) ? selected.filter((c) => c !== code) : [...selected, code]);
  const toggleGroup = (codes: PermissionCode[]) => {
    const pickable = codes.filter((c) => canIncludePermission(c, mine));
    const allOn = pickable.every((c) => selected.includes(c));
    setSelected(allOn ? selected.filter((c) => !pickable.includes(c as PermissionCode)) : [...new Set([...selected, ...pickable])]);
  };
  /** 기본 역할에서 시작 — 그 역할의 권한 가운데 넣을 수 있는 것만 가져온다 */
  const startFrom = (key: string) => {
    setTemplate(key);
    const base = roles.find((r) => r.key === key);
    setSelected(base ? base.permissions.filter((c) => canIncludePermission(c, mine)) : []);
  };
  const templates = roles.filter((r) => isBuiltinRole(r.key) && r.key !== 'owner');

  const save = useMutation({
    mutationFn: (form: Form) => {
      const body = {
        name: form.name.trim(),
        description: form.description.trim() || undefined,
        permissions: form.permissions as PermissionCode[],
      };
      return editing ? workspaceApi.updateRole(workspace.slug, editing.key, body) : workspaceApi.createRole(workspace.slug, body);
    },
    onSuccess: (role) => {
      toast.success(editing ? t('roleEditor.saved', { name: role.name }) : t('roleEditor.created', { name: role.name }));
      void queryClient.invalidateQueries({ queryKey: workspaceKeys.roles(workspace.slug) });
      void queryClient.invalidateQueries({ queryKey: workspaceKeys.audit(workspace.slug) });
      // 내가 가진 역할을 고쳤다면 내 권한도 바뀐다
      void queryClient.invalidateQueries({ queryKey: workspaceKeys.detail(workspace.slug) });
      onClose();
    },
    onError: (e) => toast.error(errorMessage(e)),
  });

  return (
    <Dialog open onClose={onClose} maxWidth="md" fullWidth fullScreen={fullScreen} scroll="paper">
      <Box component="form" noValidate onSubmit={handleSubmit((form) => save.mutate(form))} sx={{ display: 'contents' }}>
        <DialogTitle sx={{ fontWeight: 700 }}>
          {editing ? t('roleEditor.editTitle', { name: roleText.name(editing) }) : t('roleEditor.createTitle')}
        </DialogTitle>
        <DialogContent dividers>
          <Stack spacing={2.5}>
            <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' } }}>
              <TextField
                label={t('roleEditor.name')}
                autoFocus
                {...register('name')}
                error={!!formState.errors.name}
                helperText={formState.errors.name?.message}
                slotProps={{ htmlInput: { maxLength: 100 } }}
              />
              {!editing && (
                <TextField select label={t('roleEditor.startFrom')} value={template} onChange={(e) => startFrom(e.target.value)}>
                  <MenuItem value="">{t('roleEditor.startFromNone')}</MenuItem>
                  {templates.map((r) => (
                    <MenuItem key={r.key} value={r.key}>
                      {roleText.name(r)}
                    </MenuItem>
                  ))}
                </TextField>
              )}
              <TextField
                label={t('roleEditor.description')}
                multiline
                minRows={1}
                {...register('description')}
                slotProps={{ htmlInput: { maxLength: 500 } }}
                sx={{ gridColumn: { sm: editing ? 'span 1' : '1 / -1' } }}
              />
            </Box>

            <Box>
              <Stack direction="row" spacing={1} sx={{ alignItems: 'baseline', justifyContent: 'space-between', mb: 0.5 }}>
                <Typography variant="subtitle1" component="h2">
                  {t('roleEditor.permissions')}
                </Typography>
                <Typography variant="body2" sx={{ color: 'text.secondary', fontVariantNumeric: 'tabular-nums' }} aria-live="polite">
                  {t('roleEditor.selected', { count: selected.length })}
                </Typography>
              </Stack>
              <Typography variant="body2" sx={{ color: 'text.secondary' }}>
                {t('roleEditor.rule')}
              </Typography>
              {formState.errors.permissions && <FormHelperText error>{formState.errors.permissions.message}</FormHelperText>}
            </Box>

            {permissionGroups.map(({ group, codes }) => {
              const pickable = codes.filter((c) => canIncludePermission(c, mine));
              const on = pickable.filter((c) => selected.includes(c)).length;
              return (
                <Box key={group} component="fieldset" sx={{ border: 0, p: 0, m: 0, minWidth: 0 }}>
                  <Box
                    component="legend"
                    sx={{ p: 0, width: '100%', borderBottom: 1, borderColor: 'divider', mb: 0.5 }}
                  >
                    <FormControlLabel
                      control={
                        <Checkbox
                          size="small"
                          checked={pickable.length > 0 && on === pickable.length}
                          indeterminate={on > 0 && on < pickable.length}
                          disabled={pickable.length === 0}
                          onChange={() => toggleGroup(codes)}
                          slotProps={{ input: { 'aria-label': t('roleEditor.selectGroup', { group: labels.permissionGroup(group) }) } }}
                        />
                      }
                      label={
                        <Typography variant="overline" sx={{ color: 'text.secondary' }}>
                          {labels.permissionGroup(group)}
                        </Typography>
                      }
                    />
                  </Box>
                  <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '1fr 1fr' }, columnGap: 2 }}>
                    {codes.map((code) => {
                      const allowed = canIncludePermission(code, mine);
                      const ownerOnly = ownerOnlyPermissions.includes(code);
                      return (
                        <FormControlLabel
                          key={code}
                          disabled={!allowed}
                          control={<Checkbox checked={selected.includes(code)} onChange={() => toggle(code)} />}
                          label={
                            <Box sx={{ minWidth: 0 }}>
                              <Typography variant="body2" component="span" sx={{ display: 'block' }}>
                                {labels.permission(code)}
                              </Typography>
                              <Stack direction="row" spacing={0.75} useFlexGap sx={{ alignItems: 'center', flexWrap: 'wrap' }}>
                                <Typography variant="caption" sx={{ color: 'text.secondary', fontFamily: 'monospace' }}>
                                  {code}
                                </Typography>
                                {!allowed && (
                                  <Chip
                                    size="small"
                                    variant="outlined"
                                    label={ownerOnly ? t('roles.ownerOnly') : t('roleEditor.notMine')}
                                    sx={{ height: 20, fontSize: 11 }}
                                  />
                                )}
                              </Stack>
                            </Box>
                          }
                          sx={{
                            alignItems: 'flex-start',
                            mr: 0,
                            py: 0.5,
                            '& .MuiCheckbox-root': { pt: 0.25 },
                            // 꺼진 항목도 이유는 읽혀야 한다 — 글자는 보조색으로만 낮춘다
                            '& .MuiFormControlLabel-label.Mui-disabled': { color: 'text.secondary' },
                          }}
                        />
                      );
                    })}
                  </Box>
                </Box>
              );
            })}
          </Stack>
        </DialogContent>
        <DialogActions sx={{ px: 3, py: 2 }}>
          <Button color="inherit" onClick={onClose}>
            {t('common:actions.cancel')}
          </Button>
          <Button type="submit" variant="contained" loading={save.isPending}>
            {editing ? t('common:actions.save') : t('roleEditor.submitCreate')}
          </Button>
        </DialogActions>
      </Box>
    </Dialog>
  );
}
