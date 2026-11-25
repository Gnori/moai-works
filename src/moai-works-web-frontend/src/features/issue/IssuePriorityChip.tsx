import KeyboardArrowDownRounded from '@mui/icons-material/KeyboardArrowDownRounded';
import KeyboardArrowUpRounded from '@mui/icons-material/KeyboardArrowUpRounded';
import KeyboardDoubleArrowUpRounded from '@mui/icons-material/KeyboardDoubleArrowUpRounded';
import Chip from '@mui/material/Chip';
import Typography from '@mui/material/Typography';

import type { IssuePriority } from '@/shared/api/types';
import { useLabels } from '@/shared/lib/labels';

/**
 * 일감 우선순위(2026-11-26, 일감 #302) — 낮음은 흐린 테두리 + 아래 화살표, 높음은 경고색 테두리 + 위 화살표,
 * 긴급은 오류색 채움 + 겹 위 화살표. 색만으로 전하지 않게 늘 이름과 화살표를 함께 적는다(WEB-DESIGN §3.1).
 * 보통은 표시하지 않는다 — normal 을 주면 흐린 글자 "보통"만(보기 속성 · 목록 칸처럼 빈자리가 어색한 곳)
 */
export function IssuePriorityChip({ priority, size = 'small', normal = false }: { priority: IssuePriority; size?: 'small' | 'medium'; normal?: boolean }) {
  const labels = useLabels();
  const label = labels.issuePriority(priority);
  switch (priority) {
    case 'NORMAL':
      return normal ? (
        <Typography component="span" variant="body2" color="text.secondary" data-priority={priority} sx={{ flexShrink: 0 }}>
          {label}
        </Typography>
      ) : null;
    case 'LOW':
      return (
        <Chip
          size={size}
          label={label}
          variant="outlined"
          data-priority={priority}
          icon={<KeyboardArrowDownRounded aria-hidden />}
          sx={{ flexShrink: 0, color: 'text.secondary', borderColor: 'divider', '& .MuiChip-icon': { color: 'inherit' } }}
        />
      );
    case 'HIGH':
      return (
        <Chip
          size={size}
          label={label}
          variant="outlined"
          data-priority={priority}
          icon={<KeyboardArrowUpRounded aria-hidden />}
          sx={{ flexShrink: 0, color: 'warning.main', borderColor: 'warning.main', '& .MuiChip-icon': { color: 'inherit' } }}
        />
      );
    case 'URGENT':
      return (
        <Chip
          size={size}
          label={label}
          data-priority={priority}
          icon={<KeyboardDoubleArrowUpRounded aria-hidden />}
          sx={{ flexShrink: 0, bgcolor: 'error.light', color: 'error.main', fontWeight: 600, '& .MuiChip-icon': { color: 'inherit' } }}
        />
      );
  }
}
