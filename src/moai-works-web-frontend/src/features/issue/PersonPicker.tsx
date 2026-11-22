import Autocomplete from '@mui/material/Autocomplete';
import Box from '@mui/material/Box';
import TextField from '@mui/material/TextField';
import { useMemo } from 'react';
import { useTranslation } from 'react-i18next';

import type { Member } from '@/shared/api/types';

export interface PersonOption {
  id: string;
  name: string;
  /** 아이디 — 이름이 같은 사람을 가린다 */
  username: string | null;
}

interface Props {
  label: string;
  value: PersonOption | null;
  onChange: (value: PersonOption | null) => void;
  /** 고를 수 있는 사람 (워크스페이스 활성 멤버). 모르면 undefined — 지금 값만 보인다 */
  members: Member[] | undefined;
  loading?: boolean;
  disabled?: boolean;
  /** 비워 둘 때의 글 (예: "없음" · "비워 두면 내가 관리자") */
  placeholder?: string;
  helperText?: string;
  error?: string;
}

/** 관리자 · 담당자 고르기 — 이름 · 아이디로 찾는다. 비우면 null. */
export function PersonPicker({ label, value, onChange, members, loading, disabled, placeholder, helperText, error }: Props) {
  const { t } = useTranslation('issue');
  const options = useMemo(() => {
    const list: PersonOption[] = (members ?? [])
      .filter((m) => m.status === 'ACTIVE')
      .map((m) => ({ id: m.userId, name: m.displayName || m.username, username: m.username }));
    // 지금 값이 목록에 없어도(정지된 멤버 · 목록을 못 봄) 고른 채로 보이게 둔다
    if (value && !list.some((o) => o.id === value.id)) {
      list.unshift(value);
    }
    return list;
  }, [members, value]);

  return (
    <Autocomplete
      value={value}
      onChange={(_, next) => onChange(next)}
      options={options}
      getOptionLabel={(option) => option.name}
      getOptionKey={(option) => option.id}
      isOptionEqualToValue={(option, selected) => option.id === selected.id}
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
      disabled={disabled}
      noOptionsText={t('form.peopleNoOptions')}
      loadingText={t('form.loading')}
      renderInput={(params) => (
        <TextField
          {...params}
          label={label}
          placeholder={placeholder ?? t('form.peoplePlaceholder')}
          error={!!error}
          helperText={error ?? helperText}
          slotProps={{ ...params.slotProps, inputLabel: { ...params.slotProps.inputLabel, shrink: true } }}
        />
      )}
    />
  );
}
