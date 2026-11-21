import Chip from '@mui/material/Chip';
import Stack from '@mui/material/Stack';

import { roleColor, useRoleName } from '@/shared/lib/labels';

/**
 * 역할 칩 묶음 — 소유자 · 관리자 · 멤버는 색이 다르고, 사용자 정의 역할은 테두리만.
 * @param customNames 사용자 정의 역할의 이름 (역할 키 → 이름). 없으면 키를 보인다
 */
export function RoleChips({ roles, customNames }: { roles: string[]; customNames?: Record<string, string> }) {
  const roleName = useRoleName(customNames);
  return (
    <Stack direction="row" spacing={0.5} useFlexGap sx={{ flexWrap: 'wrap' }}>
      {roles.map((role) => {
        const color = roleColor(role);
        return (
          <Chip
            key={role}
            size="small"
            label={roleName(role)}
            color={color === 'default' ? 'default' : color}
            variant={color === 'default' ? 'outlined' : 'filled'}
            sx={color === 'default' ? undefined : { bgcolor: `${color}.light`, color: `${color}.main` }}
          />
        );
      })}
    </Stack>
  );
}
