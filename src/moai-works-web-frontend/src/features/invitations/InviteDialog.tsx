import { zodResolver } from '@hookform/resolvers/zod';
import ContentCopyRounded from '@mui/icons-material/ContentCopyRounded';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import IconButton from '@mui/material/IconButton';
import InputAdornment from '@mui/material/InputAdornment';
import MenuItem from '@mui/material/MenuItem';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useMemo, useRef, useState } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';
import { z } from 'zod';

import { useRoleText, workspaceKeys } from '@/features/workspace/queries';
import { canGrantRole } from '@/features/workspace/role-rules';
import { useWorkspace } from '@/features/workspace/workspace-context';
import { workspaceApi } from '@/shared/api/endpoints';
import { errorMessage } from '@/shared/api/http';
import type { InvitationCreated, Role } from '@/shared/api/types';

/** 만든 초대의 링크 — 지금 한 번만 보인다(서버는 토큰의 해시만 갖는다). 메일이 꺼져 있어 직접 전한다. */
function CreatedStep({ created, onAnother, onClose }: { created: InvitationCreated; onAnother: () => void; onClose: () => void }) {
  const { t } = useTranslation(['workspace', 'common']);
  const input = useRef<HTMLInputElement>(null);

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(created.inviteUrl);
      toast.success(t('invite.copied'));
    } catch {
      input.current?.select();
      toast.error(t('invite.copyFailed'));
    }
  };

  return (
    <>
      <DialogTitle sx={{ fontWeight: 700 }}>{t('invite.createdTitle')}</DialogTitle>
      <DialogContent>
        <Stack spacing={2}>
          <Typography variant="body2" sx={{ color: 'text.secondary' }}>
            {t('invite.createdBody', { email: created.invitation.email })}
          </Typography>
          <TextField
            label={t('invite.link')}
            value={created.inviteUrl}
            inputRef={input}
            onFocus={(e) => e.target.select()}
            slotProps={{
              htmlInput: { readOnly: true, spellCheck: false, sx: { fontFamily: 'monospace', fontSize: 13 } },
              input: {
                endAdornment: (
                  <InputAdornment position="end">
                    <IconButton edge="end" aria-label={t('invite.copy')} onClick={copy}>
                      <ContentCopyRounded fontSize="small" />
                    </IconButton>
                  </InputAdornment>
                ),
              },
            }}
          />
          <Alert severity="warning">{t('invite.linkOnce')}</Alert>
        </Stack>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2.5, flexWrap: 'wrap', gap: 1 }}>
        <Button color="inherit" onClick={onAnother}>
          {t('invite.another')}
        </Button>
        <Box sx={{ flex: 1 }} />
        <Button variant="outlined" startIcon={<ContentCopyRounded />} onClick={copy}>
          {t('invite.copy')}
        </Button>
        <Button variant="contained" onClick={onClose}>
          {t('invite.done')}
        </Button>
      </DialogActions>
    </>
  );
}

/**
 * 멤버 초대 — 이메일 · 줄 역할 · 남길 말. 줄 수 있는 역할만 고를 수 있다(내 권한 안의 역할 — api 와 같은 규칙).
 * 만들면 링크를 보여 주고 복사하게 한다.
 */
export function InviteDialog({ roles, onClose }: { roles: Role[]; onClose: () => void }) {
  const { t } = useTranslation(['workspace', 'common']);
  const { workspace } = useWorkspace();
  const roleText = useRoleText();
  const queryClient = useQueryClient();
  const [created, setCreated] = useState<InvitationCreated | null>(null);

  const grantable = useMemo(() => roles.filter((r) => canGrantRole(r, workspace.myPermissions)), [roles, workspace.myPermissions]);
  const defaultRole = grantable.find((r) => r.key === workspace.defaultRoleKey)?.key ?? grantable[0]?.key ?? '';

  const schema = useMemo(
    () =>
      z.object({
        email: z.email(t('invite.emailInvalid')).max(320),
        roleKey: z.string().min(1),
        message: z.string().max(500),
      }),
    [t],
  );
  type Form = z.infer<typeof schema>;
  const { register, control, handleSubmit, formState, reset } = useForm<Form>({
    resolver: zodResolver(schema),
    defaultValues: { email: '', roleKey: defaultRole, message: '' },
  });

  const invite = useMutation({
    mutationFn: (form: Form) =>
      workspaceApi.invite(workspace.slug, {
        email: form.email.trim(),
        roleKey: form.roleKey,
        message: form.message.trim() || undefined,
      }),
    onSuccess: (result) => {
      setCreated(result);
      void queryClient.invalidateQueries({ queryKey: workspaceKeys.invitations(workspace.slug) });
      void queryClient.invalidateQueries({ queryKey: workspaceKeys.audit(workspace.slug) });
    },
    onError: (e) => toast.error(errorMessage(e)),
  });

  const another = () => {
    reset({ email: '', roleKey: defaultRole, message: '' });
    setCreated(null);
  };

  return (
    <Dialog open onClose={onClose} maxWidth="sm" fullWidth>
      {created ? (
        <CreatedStep created={created} onAnother={another} onClose={onClose} />
      ) : (
        <Box component="form" noValidate onSubmit={handleSubmit((form) => invite.mutate(form))} sx={{ display: 'contents' }}>
          <DialogTitle sx={{ fontWeight: 700 }}>{t('invite.title')}</DialogTitle>
          <DialogContent>
            <Stack spacing={2.5} sx={{ pt: 1 }}>
              <Typography variant="body2" sx={{ color: 'text.secondary' }}>
                {t('invite.body')}
              </Typography>
              <TextField
                label={t('invite.email')}
                type="email"
                autoComplete="off"
                autoFocus
                {...register('email')}
                error={!!formState.errors.email}
                helperText={formState.errors.email?.message}
              />
              <Controller
                control={control}
                name="roleKey"
                render={({ field }) => (
                  <TextField select label={t('invite.role')} {...field} helperText={t('invite.roleHelp')}>
                    {grantable.map((role) => (
                      <MenuItem key={role.key} value={role.key}>
                        <Box sx={{ minWidth: 0 }}>
                          <Typography variant="body2" sx={{ fontWeight: 600 }}>
                            {roleText.name(role)}
                          </Typography>
                          <Typography variant="caption" sx={{ color: 'text.secondary', display: 'block', whiteSpace: 'normal' }}>
                            {roleText.description(role)}
                          </Typography>
                        </Box>
                      </MenuItem>
                    ))}
                  </TextField>
                )}
              />
              <TextField
                label={t('invite.message')}
                multiline
                minRows={2}
                {...register('message')}
                slotProps={{ htmlInput: { maxLength: 500 } }}
              />
            </Stack>
          </DialogContent>
          <DialogActions sx={{ px: 3, pb: 2.5 }}>
            <Button color="inherit" onClick={onClose}>
              {t('common:actions.cancel')}
            </Button>
            <Button type="submit" variant="contained" loading={invite.isPending} disabled={grantable.length === 0}>
              {t('invite.submit')}
            </Button>
          </DialogActions>
        </Box>
      )}
    </Dialog>
  );
}
