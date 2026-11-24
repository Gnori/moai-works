import Button from '@mui/material/Button';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';

/** 까닭을 받는 상태 — 보류 · 취소 (types.ts 의 reasonStatuses) */
export type ReasonStatus = 'ON_HOLD' | 'CANCELLED';

/**
 * 보류 · 취소로 바꾸기 — 까닭(필수)을 받아 상태와 함께 보낸다. 까닭은 같은 이력 줄의 댓글로 남는다(2026-11-24, 일감 #152).
 * 비우고 보내면 칸에 알린다(api 도 400 STATUS_REASON_REQUIRED 로 막는다). 보내는 일 · 오류 처리는 부르는 쪽(onSubmit)이 한다 —
 * 칸에 보일 오류(서버가 까닭이 없다고 답함 등)는 error 로 돌려준다.
 * 닫는 단추는 "취소" 가 아니라 "닫기"다 — 취소 상태로 바꾸는 대화상자에서 "취소" 단추는 헷갈린다.
 */
export function StatusReasonDialog({ status, busy, error, onSubmit, onClose }: {
  status: ReasonStatus;
  busy: boolean;
  /** 칸 아래에 보일 오류(서버) */
  error?: string;
  onSubmit: (reason: string) => void;
  onClose: () => void;
}) {
  const { t } = useTranslation('issue');
  const [reason, setReason] = useState('');
  const [missing, setMissing] = useState(false);
  const titleId = `status-reason-${status}`;

  const submit = (event: FormEvent) => {
    event.preventDefault();
    if (!reason.trim()) {
      setMissing(true);
      return;
    }
    onSubmit(reason.trim());
  };

  const shownError = missing ? t('statusReason.required') : error;

  return (
    <Dialog open onClose={busy ? undefined : onClose} maxWidth="sm" fullWidth aria-labelledby={titleId}>
      <form noValidate onSubmit={submit} style={{ display: 'contents' }}>
        <DialogTitle id={titleId} sx={{ fontWeight: 700 }}>
          {t(`statusReason.${status}.title`)}
        </DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ pt: 0.5 }}>
            <Typography variant="body2" sx={{ color: 'text.secondary' }}>
              {t(`statusReason.${status}.body`)}
            </Typography>
            <TextField
              label={t('statusReason.label')}
              value={reason}
              onChange={(event) => {
                setReason(event.target.value);
                setMissing(false);
              }}
              placeholder={t(`statusReason.${status}.placeholder`)}
              required
              autoFocus
              multiline
              minRows={3}
              error={!!shownError}
              helperText={shownError ?? t('statusReason.help')}
              slotProps={{ htmlInput: { maxLength: 30000 } }}
            />
          </Stack>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2.5 }}>
          <Button color="inherit" onClick={onClose} disabled={busy}>
            {t('statusReason.close')}
          </Button>
          <Button type="submit" variant="contained" loading={busy}>
            {t(`statusReason.${status}.confirm`)}
          </Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}
