import Autocomplete from '@mui/material/Autocomplete';
import TextField from '@mui/material/TextField';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';

import { useDebounced } from '@/shared/lib/use-debounced';

import { useIssueListQuery } from './queries';

export interface IssueOption {
  number: number;
  title: string;
}

interface Props {
  slug: string;
  projectKey: string;
  label: string;
  value: IssueOption | null;
  onChange: (value: IssueOption | null) => void;
  /** 목록에서 뺄 일감 (자기 자신) */
  exclude?: number;
  placeholder?: string;
  helperText?: string;
  error?: string;
  autoFocus?: boolean;
}

/**
 * 같은 프로젝트의 일감 고르기 — 제목이나 번호(#12 · 12)로 찾는다. 상위 일감 · 연결 일감을 고를 때 쓴다.
 * 치는 글로 서버에서 다시 찾고(20개까지), 응답을 기다리는 동안에는 지금 목록을 화면에서 먼저 거른다(moai-wiki ParentPicker 와 같다).
 */
export function IssuePicker({ slug, projectKey, label, value, onChange, exclude, placeholder, helperText, error, autoFocus }: Props) {
  const { t } = useTranslation('issue');
  const [input, setInput] = useState('');
  // 고른 값의 글이 입력 칸에 그대로 들어오므로, 그때는 찾는 글로 쓰지 않는다
  const typed = value && input === optionLabel(value) ? '' : input.trim();
  const q = useDebounced(typed, 250);
  const list = useIssueListQuery(slug, projectKey, { q: q || undefined, size: 20, sort: '-updated' });
  const options = (list.data?.items ?? [])
    .filter((item) => item.number !== exclude)
    .map(({ number, title }) => ({ number, title }));

  return (
    <Autocomplete
      value={value}
      onChange={(_, next) => onChange(next)}
      inputValue={input}
      onInputChange={(_, next) => setInput(next)}
      options={options}
      getOptionLabel={optionLabel}
      getOptionKey={(option) => option.number}
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
