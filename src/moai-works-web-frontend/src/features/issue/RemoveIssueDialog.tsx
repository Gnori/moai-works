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
import { issueApi } from '@/shared/api/endpoints';
import { errorMessage } from '@/shared/api/http';
import type { IssueDetail } from '@/shared/api/types';

import { issueKeys, refreshIssues } from './queries';

/**
 * 일감 지우기 — 되돌릴 수 없어서 일감 번호를 다시 입력하게 한다(RemoveProjectDialog 와 같은 이중 확인).
 * 하위 일감이 있으면 지울 수 없다(api 도 409 ISSUE_HAS_CHILDREN 으로 막는다) — 그때는 이유를 보이고 단추를 꺼 둔다.
 */
export function RemoveIssueDialog({ slug, issue, onClose, onRemoved }: {
  slug: string;
  issue: Pick<IssueDetail, 'number' | 'title' | 'children'>;
  onClose: () => void;
  onRemoved: () => void;
}) {
  const { t } = useTranslation(['issue', 'common']);
  const queryClient = useQueryClient();
  const [typed, setTyped] = useState('');
  const blocked = issue.children.length > 0;

  const remove = useMutation({
    mutationFn: () => issueApi.remove(slug, issue.number),
    onSuccess: () => {
      queryClient.removeQueries({ queryKey: issueKeys.detail(slug, issue.number) });
      void refreshIssues(queryClient, slug);
      void queryClient.invalidateQueries({ queryKey: workspaceKeys.audit(slug) });
      toast.success(t('remove.done', { number: issue.number }));
      onRemoved();
    },
    onError: (e) => toast.error(errorMessage(e)),
  });

  return (
    <Dialog open onClose={onClose} maxWidth="xs" fullWidth>
      <DialogTitle sx={{ fontWeight: 700 }}>{t('remove.title')}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ pt: 0.5 }}>
          <Alert severity="error" sx={{ overflowWrap: 'anywhere' }}>
            {t('remove.body', { number: issue.number, title: issue.title })}
          </Alert>
          {blocked ? (
            <Alert severity="warning">{t('remove.hasChildren', { count: issue.children.length })}</Alert>
          ) : (
            <TextField
              label={t('remove.confirmLabel', { number: issue.number })}
              value={typed}
              onChange={(e) => setTyped(e.target.value)}
              autoFocus
              slotProps={{ htmlInput: { inputMode: 'numeric', autoComplete: 'off', sx: { fontFamily: 'monospace' } } }}
            />
          )}
        </Stack>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2.5 }}>
        <Button color="inherit" onClick={onClose}>
          {t('common:actions.cancel')}
        </Button>
        <Button
          variant="contained"
          color="error"
          disabled={blocked || typed.trim().replace(/^#/, '') !== String(issue.number)}
          loading={remove.isPending}
          onClick={() => remove.mutate()}
        >
          {t('remove.confirm')}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
