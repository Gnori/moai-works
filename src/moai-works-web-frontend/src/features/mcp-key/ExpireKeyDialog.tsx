import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';

import { mcpKeyApi } from '@/shared/api/endpoints';
import { errorMessage, isApiError } from '@/shared/api/http';
import type { McpKey } from '@/shared/api/types';

import { EXPIRE_NOTE_MAX, personName } from './mcp-key';
import { refreshMcpKeys } from './queries';

/**
 * 키 만료시키기 — 되돌릴 수 없으므로 묻고(빨간 단추), 이유를 적을 수 있다(감사 기록에 남는다).
 * 내 키, 또는 mcp.key.manage 가 있으면 남의 키도. 판정은 api 가 한다(남의 키인데 권한이 없으면 403).
 */
export function ExpireKeyDialog({ target, onClose }: { target: McpKey; onClose: () => void }) {
  const { t } = useTranslation(['mcp', 'common']);
  const queryClient = useQueryClient();
  const [note, setNote] = useState('');

  const expire = useMutation({
    mutationFn: () => mcpKeyApi.expire(target.workspace, target.id, note.trim() || undefined),
    onSuccess: () => {
      toast.success(t('expire.done', { name: target.name }));
      refreshMcpKeys(queryClient, target.workspace);
      onClose();
    },
    onError: (e) => {
      if (isApiError(e, 'MCP_KEY_ALREADY_EXPIRED')) {
        toast.info(t('expire.already'));
        refreshMcpKeys(queryClient, target.workspace);
        onClose();
        return;
      }
      toast.error(errorMessage(e));
    },
  });

  return (
    <Dialog open onClose={expire.isPending ? undefined : onClose} maxWidth="xs" fullWidth>
      <Box
        component="form"
        noValidate
        onSubmit={(e) => {
          e.preventDefault();
          expire.mutate();
        }}
        sx={{ display: 'contents' }}
      >
        <DialogTitle sx={{ fontWeight: 700, overflowWrap: 'anywhere' }}>{t('expire.title', { name: target.name })}</DialogTitle>
        <DialogContent>
          <Stack spacing={2}>
            <Typography variant="body2" sx={{ color: 'text.secondary' }}>
              {t('expire.body')}
            </Typography>
            {!target.mine && (
              <Typography variant="body2" sx={{ color: 'text.secondary' }}>
                {t('expire.others', { owner: personName(target.owner) })}
              </Typography>
            )}
            <Typography variant="caption" sx={{ fontFamily: 'monospace', color: 'text.secondary' }}>
              {target.keyPrefix}… · {target.workspaceName}
            </Typography>
            <TextField
              label={t('expire.note')}
              value={note}
              onChange={(e) => setNote(e.target.value)}
              multiline
              minRows={2}
              helperText={t('expire.noteHelp')}
              slotProps={{ htmlInput: { maxLength: EXPIRE_NOTE_MAX } }}
            />
          </Stack>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2.5 }}>
          <Button color="inherit" onClick={onClose} disabled={expire.isPending}>
            {t('common:actions.cancel')}
          </Button>
          <Button type="submit" variant="contained" color="error" loading={expire.isPending}>
            {t('expire.confirm')}
          </Button>
        </DialogActions>
      </Box>
    </Dialog>
  );
}
