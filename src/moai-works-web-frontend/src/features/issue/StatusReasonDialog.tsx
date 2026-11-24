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

import { reasonStatuses, type IssueStatus } from '@/shared/api/types';
import { useLabels } from '@/shared/lib/labels';

/** 대화상자의 글 묶음 — 까닭을 받는 상태(보류 · 취소 · 확인요청 둘), 그 밖에는 확인요청을 되돌리기(반려 · 거둠) */
type ReasonKind = 'ON_HOLD' | 'CANCELLED' | 'REQUIREMENT_REVIEW' | 'REVIEW' | 'RETURN';

/** 옮길 상태 → 대화상자의 글 묶음. 까닭을 받는 상태가 아니면 확인요청을 되돌리는 것이다(issue-form 의 returnsReview) */
function reasonKindOf(status: IssueStatus): ReasonKind {
  return reasonStatuses.includes(status) ? (status as ReasonKind) : 'RETURN';
}

/**
 * 까닭을 받아 상태를 바꾼다 — 보류 · 취소(2026-11-24, 일감 #152), 확인요청 둘(무엇을 확인할지) · 확인요청 되돌리기(반려 · 거둠, 2026-11-25, 일감 #272).
 * 까닭은 상태와 함께 보내 같은 이력 줄의 댓글로 남는다. 비우고 보내면 칸에 알린다(api 도 400 STATUS_REASON_REQUIRED 로 막는다).
 * 보내는 일 · 오류 처리는 부르는 쪽(onSubmit)이 한다 — 칸에 보일 오류(서버가 까닭이 없다고 답함 등)는 error 로 돌려준다.
 * 닫는 단추는 "취소" 가 아니라 "닫기"다 — 취소 상태로 바꾸는 대화상자에서 "취소" 단추는 헷갈린다.
 */
export function StatusReasonDialog({ status, from, busy, error, onSubmit, onClose }: {
  /** 옮길 상태 */
  status: IssueStatus;
  /** 지금 상태 — 되돌리기 글("확인요청 되돌리기")에 쓴다 */
  from: IssueStatus;
  busy: boolean;
  /** 칸 아래에 보일 오류(서버) */
  error?: string;
  onSubmit: (reason: string) => void;
  onClose: () => void;
}) {
  const { t } = useTranslation('issue');
  const labels = useLabels();
  const [reason, setReason] = useState('');
  const [missing, setMissing] = useState(false);
  const kind = reasonKindOf(status);
  const names = { from: labels.issueStatus(from), to: labels.issueStatus(status) };
  const titleId = `status-reason-${status}`;

  const submit = (event: FormEvent) => {
    event.preventDefault();
    if (!reason.trim()) {
      setMissing(true);
      return;
    }
    onSubmit(reason.trim());
  };

  const shownError = missing ? t(`statusReason.${kind}.required`) : error;

  return (
    <Dialog open onClose={busy ? undefined : onClose} maxWidth="sm" fullWidth aria-labelledby={titleId}>
      <form noValidate onSubmit={submit} style={{ display: 'contents' }}>
        <DialogTitle id={titleId} sx={{ fontWeight: 700 }}>
          {t(`statusReason.${kind}.title`, names)}
        </DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ pt: 0.5 }}>
            <Typography variant="body2" sx={{ color: 'text.secondary' }}>
              {t(`statusReason.${kind}.body`, names)}
            </Typography>
            <TextField
              label={t(`statusReason.${kind}.label`)}
              value={reason}
              onChange={(event) => {
                setReason(event.target.value);
                setMissing(false);
              }}
              placeholder={t(`statusReason.${kind}.placeholder`)}
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
            {t(`statusReason.${kind}.confirm`)}
          </Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}
