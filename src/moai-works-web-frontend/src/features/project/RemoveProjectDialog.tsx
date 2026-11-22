import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';

import { workspaceKeys } from '@/features/workspace/queries';
import { projectApi } from '@/shared/api/endpoints';
import { errorMessage } from '@/shared/api/http';
import type { ProjectSummary } from '@/shared/api/types';

import { projectKeys } from './queries';

/**
 * 프로젝트 지우기 — 되돌릴 수 없어서 주소 이름을 다시 입력하게 한다(이중 확인, api 도 같은 값을 확인한다).
 * 보관한 프로젝트만 지울 수 있다.
 */
export function RemoveProjectDialog({ slug, project, onClose, onRemoved }: {
  slug: string;
  project: Pick<ProjectSummary, 'key' | 'name'>;
  onClose: () => void;
  onRemoved?: () => void;
}) {
  const { t } = useTranslation(['workspace', 'common']);
  const queryClient = useQueryClient();
  const [typed, setTyped] = useState('');

  const remove = useMutation({
    mutationFn: () => projectApi.remove(slug, project.key),
    onSuccess: () => {
      queryClient.removeQueries({ queryKey: projectKeys.detail(slug, project.key) });
      void queryClient.invalidateQueries({ queryKey: projectKeys.lists(slug) });
      void queryClient.invalidateQueries({ queryKey: workspaceKeys.audit(slug) });
      toast.success(t('projects.remove.done'));
      onRemoved?.();
      onClose();
    },
    onError: (e) => toast.error(errorMessage(e)),
  });

  return (
    <Dialog open onClose={onClose} maxWidth="xs" fullWidth>
      <DialogTitle sx={{ fontWeight: 700 }}>{t('projects.remove.title')}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ pt: 0.5 }}>
          <Alert severity="error" sx={{ overflowWrap: 'anywhere' }}>{t('projects.remove.body', { name: project.name })}</Alert>
          <TextField
            label={t('projects.remove.confirmLabel', { key: project.key })}
            value={typed}
            onChange={(e) => setTyped(e.target.value)}
            autoFocus
            slotProps={{ htmlInput: { spellCheck: false, autoCapitalize: 'none', autoCorrect: 'off', sx: { fontFamily: 'monospace' } } }}
          />
        </Stack>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2.5 }}>
        <Button color="inherit" onClick={onClose}>
          {t('common:actions.cancel')}
        </Button>
        <Button variant="contained" color="error" disabled={typed !== project.key} loading={remove.isPending} onClick={() => remove.mutate()}>
          {t('projects.remove.confirm')}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
