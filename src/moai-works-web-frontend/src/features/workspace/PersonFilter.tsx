import Autocomplete from '@mui/material/Autocomplete';
import Box from '@mui/material/Box';
import TextField from '@mui/material/TextField';
import { useMemo } from 'react';

import type { Member } from '@/shared/api/types';

/** 고를 수 있는 사람 한 명 */
export interface PersonChoice {
  id: string;
  name: string;
  /** 아이디 — 이름이 같은 사람을 가린다 */
  username?: string | null;
}

interface Props {
  label: string;
  /** 아무도 고르지 않았을 때의 글 ("모든 사람") */
  allLabel: string;
  /** 고른 사람의 사용자 id — null 이면 모두 */
  value: string | null;
  onChange: (id: string | null) => void;
  /** 워크스페이스 멤버 — 볼 권한이 없으면 undefined */
  members: Member[] | undefined;
  /** 멤버 목록 밖의 사람(떠난 멤버 등) — 이미 받은 기록 · 키에서 모은다 */
  extra?: PersonChoice[];
  loading?: boolean;
  helperText?: string;
  noOptionsText?: string;
}

/**
 * 사람 조건 — 감사 기록 · MCP 키 목록을 한 사람으로 좁힌다. 이름 · 아이디로 찾고, 지우면 모두.
 * 주소에만 있고 목록에 없는 사람(떠난 멤버)도 고른 채로 보인다.
 */
export function PersonFilter({ label, allLabel, value, onChange, members, extra, loading, helperText, noOptionsText }: Props) {
  const options = useMemo(() => {
    const byId = new Map<string, PersonChoice>();
    for (const m of members ?? []) {
      byId.set(m.userId, { id: m.userId, name: m.displayName || m.username, username: m.username });
    }
    for (const p of extra ?? []) {
      if (!byId.has(p.id)) {
        byId.set(p.id, p);
      }
    }
    if (value && !byId.has(value)) {
      byId.set(value, { id: value, name: value.slice(0, 8) });
    }
    return [...byId.values()].sort((a, b) => a.name.localeCompare(b.name));
  }, [members, extra, value]);
  const selected = value ? (options.find((o) => o.id === value) ?? null) : null;

  return (
    <Autocomplete
      size="small"
      value={selected}
      onChange={(_, next) => onChange(next?.id ?? null)}
      options={options}
      getOptionLabel={(option) => option.name}
      getOptionKey={(option) => option.id}
      isOptionEqualToValue={(option, chosen) => option.id === chosen.id}
      filterOptions={(list, state) => {
        const q = state.inputValue.trim().toLowerCase();
        return q ? list.filter((o) => o.name.toLowerCase().includes(q) || (o.username ?? '').toLowerCase().includes(q)) : list;
      }}
      renderOption={(props, option) => {
        const { key, ...rest } = props;
        return (
          <Box component="li" key={key} {...rest} sx={{ gap: 1 }}>
            <span>{option.name}</span>
            {option.username && option.username !== option.name && (
              <Box component="span" sx={{ color: 'text.secondary', fontSize: 12, fontFamily: 'monospace' }}>
                {option.username}
              </Box>
            )}
          </Box>
        );
      }}
      loading={loading}
      noOptionsText={noOptionsText}
      sx={{ width: { xs: '100%', sm: 260 } }}
      renderInput={(params) => (
        <TextField
          {...params}
          label={label}
          placeholder={allLabel}
          helperText={helperText}
          slotProps={{ ...params.slotProps, inputLabel: { ...params.slotProps.inputLabel, shrink: true } }}
        />
      )}
    />
  );
}
