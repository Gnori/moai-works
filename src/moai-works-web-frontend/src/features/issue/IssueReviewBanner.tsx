import HourglassTopRounded from '@mui/icons-material/HourglassTopRounded';
import TaskAltRounded from '@mui/icons-material/TaskAltRounded';
import UndoRounded from '@mui/icons-material/UndoRounded';
import Alert from '@mui/material/Alert';
import AlertTitle from '@mui/material/AlertTitle';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Link from '@mui/material/Link';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { useTranslation } from 'react-i18next';

import type { IssueDetail, IssueJournal, IssueStatus } from '@/shared/api/types';
import { dateTimeMs, fromNow } from '@/shared/lib/format';
import { useLabels } from '@/shared/lib/labels';

import { confirmationOf, rejectTargetOf } from './issue-form';
import { personName } from './issue-labels';

/** 지금 확인요청 상태가 된 이력 줄 — 상태를 그 값으로 바꾼 마지막 줄(까닭 댓글이 같은 줄에 있다). 없으면 null */
export function reviewRequestOf(issue: Pick<IssueDetail, 'status' | 'journal'>): IssueJournal | null {
  return issue.journal.findLast((line) => line.changes.some((c) => c.field === 'status' && c.after === issue.status)) ?? null;
}

/**
 * 관리자 확인 띠(2026-11-25, 일감 #272) — 일감이 확인요청(요구사항 확인요청 · 확인요청)에 있을 때 일감 보기 머리 아래에 보인다.
 * 관리자(또는 project.manage)에게는 "확인 완료"(짝인 확인완료로) · "반려"(까닭을 받아 요구사항 확인요청 → 등록, 확인요청 → 진행) 단추를,
 * 그 밖의 사람에게는 누구의 확인을 기다리는지만 보인다. 요청한 사람 · 때 · 무엇을 확인할지(까닭 댓글)를 함께 — 댓글은 사용자가 쓴 글이라
 * 글자 그대로(마크다운을 그리지 않는다) 여섯 줄까지 보이고 "댓글에서 보기"로 이력을 연다. 판정은 api 다(아니면 403 CONFIRM_BY_MANAGER_ONLY).
 * 단추는 좁은 화면에서 아래로 접힌다(390px).
 */
export function IssueReviewBanner({ issue, canConfirm, canEdit, busy, onConfirm, onReject, onShowComments }: {
  issue: IssueDetail;
  /** 확인완료로 옮길 수 있는 사람인가 — 관리자 · project.manage (issue-form 의 canConfirmIssue) */
  canConfirm: boolean;
  /** 고칠 수 있나(issue.edit · 활성 프로젝트) — 없으면 단추를 보이지 않는다 */
  canEdit: boolean;
  busy: boolean;
  onConfirm: (to: IssueStatus) => void;
  onReject: (to: IssueStatus) => void;
  onShowComments: () => void;
}) {
  const { t } = useTranslation('issue');
  const labels = useLabels();
  const confirmed = confirmationOf(issue.status);
  const back = rejectTargetOf(issue.status);
  if (!confirmed || !back || (issue.status !== 'REQUIREMENT_REVIEW' && issue.status !== 'REVIEW')) {
    return null;
  }
  const request = reviewRequestOf(issue);
  const deciding = canConfirm && canEdit;
  const manager = personName(issue.manager) ?? t('view.unknownPerson');

  return (
    <Alert
      severity="info"
      icon={<HourglassTopRounded fontSize="inherit" />}
      role="status"
      data-testid="review-banner"
      sx={{ mb: 2.5, '& .MuiAlert-message': { width: '100%', minWidth: 0 } }}
    >
      <AlertTitle sx={{ fontWeight: 700 }}>
        {deciding ? t(`review.requestedTitle.${issue.status}`) : t(`review.waitingTitle.${issue.status}`)}
      </AlertTitle>
      <Typography variant="body2">
        {deciding
          ? t('review.requestedBody', { confirmed: labels.issueStatus(confirmed), back: labels.issueStatus(back) })
          : t('review.waitingBody', { name: manager })}
      </Typography>
      {request && (
        <Box
          sx={{ mt: 1.5, p: 1.5, borderRadius: 1.5, bgcolor: 'background.paper', border: 1, borderColor: 'divider', color: 'text.primary' }}
        >
          <Typography variant="caption" component="p" sx={{ color: 'text.secondary', fontVariantNumeric: 'tabular-nums' }}>
            <time dateTime={request.createdAt} title={dateTimeMs(request.createdAt)}>
              {t('review.requestBy', { name: request.actorName, when: fromNow(request.createdAt) })}
            </time>
          </Typography>
          {request.notes && (
            <Typography
              variant="body2"
              sx={{
                mt: 0.5,
                whiteSpace: 'pre-wrap',
                overflowWrap: 'anywhere',
                display: '-webkit-box',
                WebkitLineClamp: 6,
                WebkitBoxOrient: 'vertical',
                overflow: 'hidden',
              }}
            >
              {request.notes}
            </Typography>
          )}
          <Link component="button" type="button" variant="body2" onClick={onShowComments} sx={{ mt: 0.5 }}>
            {t('review.showComments')}
          </Link>
        </Box>
      )}
      {deciding && (
        <Stack direction="row" spacing={1} useFlexGap sx={{ mt: 1.5, flexWrap: 'wrap' }}>
          <Button variant="contained" color="success" startIcon={<TaskAltRounded />} onClick={() => onConfirm(confirmed)} loading={busy}>
            {t('review.confirm')}
          </Button>
          <Button variant="outlined" color="inherit" startIcon={<UndoRounded />} onClick={() => onReject(back)} disabled={busy}>
            {t('review.reject')}
          </Button>
        </Stack>
      )}
    </Alert>
  );
}
