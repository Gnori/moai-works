import Chip from '@mui/material/Chip';

import type { IssueStatus } from '@/shared/api/types';
import { issueStatusColor, useLabels } from '@/shared/lib/labels';

/** 일감 상태 — 색과 함께 늘 상태 이름을 적는다(색만으로 전하지 않는다). 종료는 테두리만 */
export function IssueStatusChip({ status, size = 'small' }: { status: IssueStatus; size?: 'small' | 'medium' }) {
  const labels = useLabels();
  const color = issueStatusColor(status);
  return (
    <Chip
      size={size}
      label={labels.issueStatus(status)}
      variant={color === 'default' ? 'outlined' : 'filled'}
      sx={color === 'default' ? { flexShrink: 0 } : { flexShrink: 0, bgcolor: `${color}.light`, color: `${color}.main` }}
    />
  );
}
