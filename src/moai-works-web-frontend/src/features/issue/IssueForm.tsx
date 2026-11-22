import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import CardHeader from '@mui/material/CardHeader';
import MenuItem from '@mui/material/MenuItem';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useState, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';

import { useCan } from '@/features/auth/access';
import { useWorkspace } from '@/features/workspace/workspace-context';
import { issueDateFields, issueStatuses, type IssueType, type ProjectDetail } from '@/shared/api/types';
import { useLabels } from '@/shared/lib/labels';

import { UploadProgressLine } from './AttachmentControls';
import { canUploadAttachment } from './attachments';
import type { IssueFormValues } from './issue-form';
import { useIssueTypeName } from './issue-labels';
import { IssuePicker, type IssueOption } from './IssuePicker';
import { MarkdownEditor } from './MarkdownEditor';
import { PersonPicker, type PersonOption } from './PersonPicker';
import { usePeopleQuery } from './queries';
import type { EditorUpload } from './use-attachment-upload';

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
  /**
   * 설명에 이미지 · 파일 넣기 (고치기 — 그 일감에 올린다, useEditorUpload). 없으면(새 일감) 일감을 만든 뒤에 넣을 수 있다고 알린다.
   */
  upload?: EditorUpload;
}

/**
 * 일감 양식 — 제목 · 설명(Markdown 편집기 — 쓰면서 바로 그려지고, 원문으로도 고친다) · 속성(유형 · 상태 · 관리자 · 담당자 · 상위 일감 · 예상 공수 · 날짜 다섯).
 * 새 일감 · 고치기 화면이 같이 쓴다. 넓은 화면에서는 설명 오른쪽에 속성을, 좁은 화면에서는 제목 바로 아래에 속성을 둔다.
 * 관리자는 issue.manager.assign 이 없으면 꺼 둔다(서버도 같은 규칙으로 막는다).
 */
export function IssueForm({ slug, project, value, onChange, initialLabels, types, errors, selfNumber, managerHelp, extra, upload }: Props) {
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
  const peopleNote = !canSeeMembers || people.isError ? t('form.peopleUnavailable') : undefined;
  const addFiles = upload?.addFiles;
  /** 새 일감 — 올릴 수 있는 사람에게만 "만든 뒤에 넣을 수 있다"고 알린다 */
  const attachLater = !addFiles && canUploadAttachment(project);

  // 고를 수 있는 것은 쓰는 유형뿐 — 고치는 일감이 이미 옛 유형이면 그 하나만 함께 둔다(바꾸지 않고 저장할 수 있게)
  const typeOptions = types
    ? types.filter((type) => type.active || type.key === value.type)
    : value.type
      ? [{ key: value.type, name: value.type, description: null, active: true }]
      : [];
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
          <Paper
            variant="outlined"
            sx={{
              height: { xs: 420, md: 'max(460px, calc(100dvh - 360px))' },
              borderRadius: 2.5,
              overflow: 'hidden',
              borderColor: errors.description ? 'error.main' : 'divider',
            }}
          >
            <MarkdownEditor
              ref={upload?.editorRef}
              value={value.description}
              onChange={(description) => onChange({ description })}
              placeholder={t('form.descriptionPlaceholder')}
              ariaLabel={t('form.descriptionLabel')}
              onFiles={addFiles}
              variant="document"
            />
          </Paper>
          {errors.description && (
            <Typography variant="caption" color="error" sx={{ display: 'block', mt: 0.75 }}>
              {errors.description}
            </Typography>
          )}
          {upload?.progress && (
            <Box sx={{ mt: 1 }}>
              <UploadProgressLine progress={upload.progress} />
            </Box>
          )}
          {attachLater && (
            <Typography variant="caption" sx={{ display: 'block', mt: 0.75, color: 'text.secondary' }}>
              {t('attachments.afterCreate')}
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
              <TextField
                type="number"
                label={t('form.estimateLabel')}
                value={value.estimatedHours}
                onChange={(event) => onChange({ estimatedHours: event.target.value })}
                error={!!errors.estimatedHours}
                helperText={errors.estimatedHours ?? t('form.estimateHelp')}
                slotProps={{ htmlInput: { min: 0, max: 10000, step: 0.25, inputMode: 'decimal' }, inputLabel: { shrink: true } }}
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
