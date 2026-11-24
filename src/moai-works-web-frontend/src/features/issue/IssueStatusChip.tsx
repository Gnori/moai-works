import PauseRounded from '@mui/icons-material/PauseRounded';
import Chip from '@mui/material/Chip';

import type { IssueStatus } from '@/shared/api/types';
import { issueStatusTone, useLabels } from '@/shared/lib/labels';

/**
 * 일감 상태 — 색과 함께 늘 상태 이름을 적는다(색만으로 전하지 않는다). 종료는 테두리만,
 * 보류는 중립 바탕 · 점선 테두리 · 멈춤 아이콘(진행의 주황과 다르게), 취소는 흐린 글자 · 취소선이다(2026-11-24, 일감 #152)
 */
export function IssueStatusChip({ status, size = 'small' }: { status: IssueStatus; size?: 'small' | 'medium' }) {
  const labels = useLabels();
  const tone = issueStatusTone(status);
  const label = labels.issueStatus(status);
  switch (tone) {
    case 'outlined':
      return <Chip size={size} label={label} variant="outlined" data-status={status} sx={{ flexShrink: 0 }} />;
    case 'paused':
      return (
        <Chip
          size={size}
          label={label}
          variant="outlined"
          data-status={status}
          icon={<PauseRounded aria-hidden />}
          sx={{
            flexShrink: 0,
            bgcolor: 'background.subtle',
            color: 'text.secondary',
            borderStyle: 'dashed',
            borderColor: 'text.disabled',
            '& .MuiChip-icon': { color: 'inherit' },
          }}
        />
      );
    case 'muted':
      return (
        <Chip
          size={size}
          label={label}
          variant="outlined"
          data-status={status}
          sx={{ flexShrink: 0, color: 'text.secondary', borderColor: 'divider', '& .MuiChip-label': { textDecoration: 'line-through' } }}
        />
      );
    default:
      return <Chip size={size} label={label} data-status={status} sx={{ flexShrink: 0, bgcolor: `${tone}.light`, color: `${tone}.main` }} />;
  }
}
