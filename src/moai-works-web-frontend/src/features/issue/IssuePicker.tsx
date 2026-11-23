import Autocomplete from '@mui/material/Autocomplete';
import Box from '@mui/material/Box';
import TextField from '@mui/material/TextField';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';

import { useDebounced } from '@/shared/lib/use-debounced';

import { useIssueListQuery } from './queries';

export interface IssueOption {
  number: number;
  title: string;
  /** 일감의 프로젝트 key — 워크스페이스 전체에서 고를 때 다른 프로젝트의 일감을 알아보게 적는다 */
  project?: string;
}

interface Props {
  slug: string;
  /** 이 프로젝트의 일감만(상위 일감 — 같은 프로젝트여야 한다). null 이면 워크스페이스에서 내가 읽을 수 있는 일감 모두(연결 일감) */
  projectKey: string | null;
  label: string;
  value: IssueOption | null;
  onChange: (value: IssueOption | null) => void;
  /** 목록에서 뺄 일감 (자기 자신) */
  exclude?: number;
  placeholder?: string;
  helperText?: string;
  error?: string;
  autoFocus?: boolean;
  disabled?: boolean;
}

/**
 * 일감 고르기 — 제목이나 번호(#12 · 12)로 찾는다. 상위 일감(같은 프로젝트)과 연결 일감(워크스페이스의 다른 프로젝트도 — 2026-11-24)을 고를 때 쓴다.
 * 워크스페이스에서 고를 때는 후보마다 프로젝트 key 를 함께 보인다(번호는 워크스페이스 안에서 하나라 번호만으로도 가린다).
 * 치는 글로 서버에서 다시 찾고(20개까지), 응답을 기다리는 동안에는 지금 목록을 화면에서 먼저 거른다(moai-wiki ParentPicker 와 같다).
 */
export function IssuePicker({ slug, projectKey, label, value, onChange, exclude, placeholder, helperText, error, autoFocus, disabled = false }: Props) {
  const { t } = useTranslation('issue');
  const [input, setInput] = useState('');
  // 고른 값의 글이 입력 칸에 그대로 들어오므로, 그때는 찾는 글로 쓰지 않는다
  const typed = value && input === optionLabel(value) ? '' : input.trim();
  const q = useDebounced(typed, 250);
  const list = useIssueListQuery(slug, projectKey, { q: q || undefined, size: 20, sort: '-updated' }, !disabled);
  const options = (list.data?.items ?? [])
    .filter((item) => item.number !== exclude)
    .map(({ number, title, project }) => ({ number, title, project: project?.key }));

  return (
    <Autocomplete
      value={value}
      disabled={disabled}
      onChange={(_, next) => onChange(next)}
      inputValue={input}
      onInputChange={(_, next) => setInput(next)}
      options={options}
      getOptionLabel={optionLabel}
      getOptionKey={(option) => option.number}
      renderOption={(props, option) => {
        const { key, ...rest } = props as typeof props & { key: string };
        return (
          <Box component="li" key={key} {...rest} sx={{ display: 'flex', gap: 1, alignItems: 'baseline' }}>
            <Box component="span" sx={{ minWidth: 0, overflowWrap: 'anywhere' }}>
              {optionLabel(option)}
            </Box>
            {projectKey === null && option.project && (
              <Box component="span" sx={{ ml: 'auto', flexShrink: 0, color: 'text.secondary', fontFamily: 'monospace', fontSize: 12 }}>
                {option.project}
              </Box>
            )}
          </Box>
        );
      }}
      isOptionEqualToValue={(option, selected) => option.number === selected.number}
      // 서버가 이미 골랐다(번호로 맞은 것은 제목에 그 글이 없다)
      filterOptions={(all) => all}
      loading={list.isFetching}
      noOptionsText={t('form.issueNoOptions')}
      loadingText={t('form.loading')}
      renderInput={(params) => (
        <TextField
          {...params}
          label={label}
          placeholder={placeholder ?? t('form.parentPlaceholder')}
          error={!!error}
          helperText={error ?? helperText}
          autoFocus={autoFocus}
          slotProps={{ ...params.slotProps, inputLabel: { ...params.slotProps.inputLabel, shrink: true } }}
        />
      )}
    />
  );
}

function optionLabel(option: IssueOption): string {
  return `#${option.number} ${option.title}`;
}
