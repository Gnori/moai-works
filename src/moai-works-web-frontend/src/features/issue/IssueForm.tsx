import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import CardHeader from '@mui/material/CardHeader';
import MenuItem from '@mui/material/MenuItem';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import Tab from '@mui/material/Tab';
import Tabs from '@mui/material/Tabs';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useDeferredValue, useState, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';

import { useCan } from '@/features/auth/access';
import { useWorkspace } from '@/features/workspace/workspace-context';
import { issueDateFields, issueStatuses, type IssueType, type ProjectDetail } from '@/shared/api/types';
import { useLabels } from '@/shared/lib/labels';

import type { IssueFormValues } from './issue-form';
import { useIssueTypeName } from './issue-labels';
import { IssuePicker, type IssueOption } from './IssuePicker';
import { MarkdownEditor } from './MarkdownEditor';
import { MarkdownView } from './MarkdownView';
import { PersonPicker, type PersonOption } from './PersonPicker';
import { usePeopleQuery } from './queries';

/** 칸마다의 오류 글 */
export type IssueFormErrors = Partial<Record<keyof IssueFormValues, string>>;

/** 고른 사람 · 상위 일감의 보이는 이름 — 양식 값은 id · 번호만 갖는다 */
export interface IssueFormLabels {
  manager: PersonOption | null;
  assignee: PersonOption | null;
  parent: IssueOption | null;
}

interface Props {
  slug: string;
  project: ProjectDetail;
  value: IssueFormValues;
  onChange: (patch: Partial<IssueFormValues>) => void;
  /** 처음 보일 사람 · 상위 일감 이름 */
  initialLabels: IssueFormLabels;
  types: IssueType[] | undefined;
  errors: IssueFormErrors;
  /** 고치는 일감 자신 — 상위 일감 후보에서 뺀다 */
  selfNumber?: number;
  /** 관리자 칸 아래 글 (만들 때: 비우면 내가 관리자) */
  managerHelp?: string;
  /** 설명 아래에 더 둘 칸 (고치기: 바꾼 이유) */
  extra?: ReactNode;
}

/**
 * 일감 양식 — 제목 · 설명(Markdown, 쓰기/미리보기) · 속성(유형 · 상태 · 관리자 · 담당자 · 상위 일감 · 날짜 다섯).
 * 새 일감 · 고치기 화면이 같이 쓴다. 넓은 화면에서는 설명 오른쪽에 속성을, 좁은 화면에서는 제목 바로 아래에 속성을 둔다.
 * 관리자는 issue.manager.assign 이 없으면 꺼 둔다(서버도 같은 규칙으로 막는다).
 */
export function IssueForm({ slug, project, value, onChange, initialLabels, types, errors, selfNumber, managerHelp, extra }: Props) {
  const { t } = useTranslation('issue');
  const labels = useLabels();
  const typeName = useIssueTypeName();
  const { workspace } = useWorkspace();
  const can = useCan();
  const canSeeMembers = can('member.read');
  const people = usePeopleQuery(workspace.slug, canSeeMembers);
  const managerLocked = !project.myPermissions.includes('issue.manager.assign');
  const [manager, setManager] = useState(initialLabels.manager);
  const [assignee, setAssignee] = useState(initialLabels.assignee);
  const [parent, setParent] = useState(initialLabels.parent);
  const [tab, setTab] = useState<'write' | 'preview'>('write');
  const preview = useDeferredValue(value.description);
  const peopleNote = !canSeeMembers || people.isError ? t('form.peopleUnavailable') : undefined;

  const typeOptions = types ?? (value.type ? [{ key: value.type, name: value.type, description: null }] : []);
  const typeHelp = types?.find((type) => type.key === value.type)?.description ?? undefined;

  return (
    <>
      <TextField
        variant="standard"
        fullWidth
        placeholder={t('form.titlePlaceholder')}
        value={value.title}
        onChange={(event) => onChange({ title: event.target.value })}
        error={!!errors.title}
        helperText={errors.title}
        slotProps={{
          htmlInput: { maxLength: 200, 'aria-label': t('form.titleLabel'), sx: { fontSize: '1.625rem', fontWeight: 700, py: 0.75 } },
        }}
        sx={{ mb: 2.5 }}
      />
      <Box sx={{ display: 'grid', gap: 2.5, gridTemplateColumns: { xs: 'minmax(0, 1fr)', lg: 'minmax(0, 1fr) 340px' }, alignItems: 'start' }}>
        <Box sx={{ order: { xs: 1, lg: 0 }, minWidth: 0 }}>
          <Tabs value={tab} onChange={(_, next: 'write' | 'preview') => setTab(next)} sx={{ mb: 1.5, minHeight: 40 }}>
            <Tab value="write" label={t('form.tabWrite')} sx={{ minHeight: 40 }} />
            <Tab value="preview" label={t('form.tabPreview')} sx={{ minHeight: 40 }} />
          </Tabs>
          <Paper
            variant="outlined"
            sx={{
              display: tab === 'write' ? 'block' : 'none',
              height: { xs: 320, md: 'max(360px, calc(100dvh - 420px))' },
              borderRadius: 2.5,
              overflow: 'hidden',
              borderColor: errors.description ? 'error.main' : 'divider',
            }}
          >
            <MarkdownEditor
              value={value.description}
              onChange={(description) => onChange({ description })}
              placeholder={t('form.descriptionPlaceholder')}
              ariaLabel={t('form.descriptionLabel')}
            />
          </Paper>
          {tab === 'preview' && (
            <Paper
              variant="outlined"
              aria-label={t('form.tabPreview')}
              sx={{ height: { xs: 320, md: 'max(360px, calc(100dvh - 420px))' }, borderRadius: 2.5, overflow: 'auto', p: { xs: 2, sm: 3 } }}
            >
              {preview.trim() ? (
                <MarkdownView content={preview} />
              ) : (
                <Typography variant="body2" sx={{ color: 'text.secondary' }}>
                  {t('form.previewEmpty')}
                </Typography>
              )}
            </Paper>
          )}
          {errors.description && (
            <Typography variant="caption" color="error" sx={{ display: 'block', mt: 0.75 }}>
              {errors.description}
            </Typography>
          )}
          {extra}
        </Box>
        <Card sx={{ order: { xs: 0, lg: 1 }, minWidth: 0 }}>
          <CardHeader title={t('form.fieldsTitle')} />
          <CardContent>
            <Stack spacing={2.25}>
              <TextField
                select
                label={t('fields.type')}
                value={value.type}
                onChange={(event) => onChange({ type: event.target.value })}
                error={!!errors.type}
                helperText={errors.type ?? typeHelp}
              >
                {typeOptions.map((type) => (
                  <MenuItem key={type.key} value={type.key}>
                    {typeName(type.key, type.name)}
                  </MenuItem>
                ))}
              </TextField>
              <TextField
                select
                label={t('fields.status')}
                value={value.status}
                onChange={(event) => onChange({ status: event.target.value as IssueFormValues['status'] })}
              >
                {issueStatuses.map((status) => (
                  <MenuItem key={status} value={status}>
                    {labels.issueStatus(status)}
                  </MenuItem>
                ))}
              </TextField>
              <PersonPicker
                label={t('fields.manager')}
                value={manager}
                onChange={(next) => {
                  setManager(next);
                  onChange({ managerId: next?.id ?? null });
                }}
                members={people.data}
                loading={people.isFetching}
                disabled={managerLocked}
                error={errors.managerId}
                helperText={managerLocked ? t('form.managerLocked') : (peopleNote ?? managerHelp)}
              />
              <PersonPicker
                label={t('fields.assignee')}
                value={assignee}
                onChange={(next) => {
                  setAssignee(next);
                  onChange({ assigneeId: next?.id ?? null });
                }}
                members={people.data}
                loading={people.isFetching}
                placeholder={t('form.nobody')}
                error={errors.assigneeId}
                helperText={peopleNote}
              />
              <IssuePicker
                slug={slug}
                projectKey={project.key}
                label={t('fields.parent')}
                value={parent}
                onChange={(next) => {
                  setParent(next);
                  onChange({ parent: next?.number ?? null });
                }}
                exclude={selfNumber}
                error={errors.parent}
                helperText={t('form.parentHelp')}
              />
              <Typography variant="overline" sx={{ color: 'text.secondary', pt: 0.5 }}>
                {t('form.datesTitle')}
              </Typography>
              <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, minmax(0, 1fr))', lg: '1fr' } }}>
                {issueDateFields.map((field) => (
                  <TextField
                    key={field}
                    type="date"
                    label={t(`fields.${field}`)}
                    value={value[field]}
                    onChange={(event) => onChange({ [field]: event.target.value })}
                    error={!!errors[field]}
                    helperText={errors[field]}
                    slotProps={{ inputLabel: { shrink: true } }}
                  />
                ))}
              </Box>
              <Typography variant="caption" sx={{ color: 'text.secondary' }}>
                {t('form.statusDatesHelp')}
              </Typography>
            </Stack>
          </CardContent>
        </Card>
      </Box>
    </>
  );
}
