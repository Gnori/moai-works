import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardContent from '@mui/material/CardContent';
import CardHeader from '@mui/material/CardHeader';
import Checkbox from '@mui/material/Checkbox';
import Chip from '@mui/material/Chip';
import ListItemText from '@mui/material/ListItemText';
import MenuItem from '@mui/material/MenuItem';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useEffect, useRef, useState, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';

import { useCan } from '@/features/auth/access';
import { useProjectModulesQuery } from '@/features/project/queries';
import { useWorkspace } from '@/features/workspace/workspace-context';
import { issueDateFields, issueStatuses, type IssueType, type ProjectDetail, type ProjectRef } from '@/shared/api/types';
import { canPrefill, missingSections } from '@/shared/lib/issue-template';
import { useLabels } from '@/shared/lib/labels';

import { UploadProgressLine } from './AttachmentControls';
import { canUploadAttachment } from './attachments';
import { MAX_AGENT_SESSION, type IssueFormValues } from './issue-form';
import { useIssueTypeName } from './issue-labels';
import { IssuePicker, type IssueOption } from './IssuePicker';
import { MarkdownEditor } from './MarkdownEditor';
import { PersonPicker, type PersonOption } from './PersonPicker';
import { usePeopleQuery, useProjectTemplatesQuery } from './queries';
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
   * 설명 편집기 손잡이와 이미지 · 파일 넣기 (useEditorUpload — 고치기는 그 일감에 올린다). addFiles 가 없으면(새 일감) 일감을 만든 뒤에
   * 넣을 수 있다고 알린다. 저장하는 쪽은 flush() 로 방금 친 글까지 받는다.
   */
  upload?: EditorUpload;
  /** 새 일감 — 템플릿을 받으면 처음 유형의 템플릿으로 빈 설명을 채운다(고치기는 유형을 고를 때만 채운다) */
  prefillOnMount?: boolean;
  /**
   * 고치기 — 옮길 수 있는 프로젝트(지금 프로젝트 + 내가 일감을 만들 수 있는 프로젝트). 주면 프로젝트 칸을 보인다(2026-11-24, Redmine 의 Project 칸).
   * 다른 프로젝트를 고르면 상위 일감 · 관련 모듈 칸을 꺼 둔다 — 옮기면서 서버가 정한다(상위가 남으면 끊고, 모듈은 같은 key 만 남긴다)
   */
  projectOptions?: ProjectRef[];
}

/**
 * 일감 양식 — 제목 · 설명(Markdown 편집기 — 쓰면서 바로 그려지고, 원문으로도 고친다) · 속성(유형 · 상태 · 관리자 · 담당자 · 상위 일감 · 예상 공수 · 날짜 다섯).
 * 새 일감 · 고치기 화면이 같이 쓴다. 넓은 화면에서는 설명 오른쪽에 속성을, 좁은 화면에서는 제목 바로 아래에 속성을 둔다.
 * 관리자는 issue.manager.assign 이 없으면 꺼 둔다(서버도 같은 규칙으로 막는다).
 */
export function IssueForm({ slug, project, value, onChange, initialLabels, types, errors, selfNumber, managerHelp, extra, upload, prefillOnMount = false, projectOptions }: Props) {
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

  // 유형별 템플릿(FR-ISS-020) — 이 프로젝트에서 실제로 쓰이는 것
  const templates = useProjectTemplatesQuery(slug, project.key);
  const templateOf = (type: string) => templates.data?.find((template) => template.type === type)?.body;
  const prefilled = useRef(false);
  useEffect(() => {
    // 새 일감: 템플릿을 받으면 처음 유형의 템플릿으로 빈 설명을 채운다(사람이 쓴 글은 덮지 않는다)
    if (!prefillOnMount || prefilled.current || !templates.data) {
      return;
    }
    prefilled.current = true;
    const body = templates.data.find((template) => template.type === value.type)?.body;
    if (body && canPrefill(value.description)) {
      onChange({ description: body });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps -- 템플릿을 처음 받았을 때 한 번
  }, [templates.data]);
  /**
   * 유형을 바꾼다 — 설명이 비었거나(canPrefill) 앞 유형의 템플릿 그대로면(손대지 않았으면) 새 유형의 템플릿으로 바꾼다.
   * 사람이 고친 설명은 그대로 둔다.
   */
  const changeType = (type: string) => {
    const next = templateOf(type);
    const before = templateOf(value.type);
    // 방금 친 글이 아직 올라오지 않았을 수 있다 — 바로 받아서 본다
    const description = upload?.flush() ?? value.description;
    const untouched = canPrefill(description) || (before !== undefined && description === before);
    onChange(next !== undefined && untouched ? { type, description: next } : { type });
  };
  // 관련 모듈(일감 #27) — 쓰는 모듈만 고른다. 이미 붙은 쓰지 않는 모듈은 함께 두어 빼거나 그대로 둘 수 있게 한다
  const modules = useProjectModulesQuery(slug, project.key);
  const moduleOptions = (modules.data ?? []).filter((m) => m.active || value.modules.includes(m.key));
  const moduleName = (key: string) => modules.data?.find((m) => m.key === key)?.name ?? key;
  const noModules = modules.isSuccess && moduleOptions.length === 0;

  /** 다른 프로젝트로 옮기려 한다 — 상위 일감 · 관련 모듈은 옮기면서 서버가 정한다 */
  const moving = projectOptions !== undefined && value.project !== '' && value.project !== project.key;
  const targetName = projectOptions?.find((p) => p.key === value.project)?.name ?? value.project;

  /** 템플릿의 빠진 필수 항목 — 알려만 준다(저장은 막지 않는다, 2026-11-23 사용자 결정) */
  const missing = templates.data ? missingSections(templateOf(value.type), value.description) : [];

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
              onPending={upload?.onPending}
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
          {missing.length > 0 && (
            <Alert severity="warning" sx={{ mt: 1.25 }}>
              {t('templates.missing', { sections: missing.join(' · ') })}
            </Alert>
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
              {projectOptions && (
                <TextField
                  select
                  label={t('fields.project')}
                  value={value.project}
                  onChange={(event) => onChange({ project: event.target.value })}
                  error={!!errors.project}
                  helperText={
                    errors.project ??
                    (moving ? t('form.projectMoving', { name: targetName }) : projectOptions.length > 1 ? t('form.projectHelp') : t('form.projectNoTarget'))
                  }
                  disabled={projectOptions.length <= 1}
                >
                  {projectOptions.map((option) => (
                    <MenuItem key={option.key} value={option.key}>
                      <ListItemText
                        primary={option.name}
                        secondary={option.key !== option.name ? option.key : undefined}
                        slotProps={{ secondary: { sx: { fontFamily: 'monospace' } } }}
                      />
                    </MenuItem>
                  ))}
                </TextField>
              )}
              <TextField
                select
                label={t('fields.type')}
                value={value.type}
                onChange={(event) => changeType(event.target.value)}
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
                helperText={moving ? t('form.parentMoving') : t('form.parentHelp')}
                disabled={moving}
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
              {/* 관련 모듈 — 이 일에서 수정하는 모듈(여럿, 없어도 된다 — 2026-11-23 일감 #27) */}
              <TextField
                select
                label={t('fields.modules')}
                value={value.modules}
                onChange={(event) => {
                  const next = event.target.value as unknown as string[] | string;
                  onChange({ modules: typeof next === 'string' ? next.split(',').filter(Boolean) : next });
                }}
                disabled={noModules || moving}
                error={!!errors.modules}
                helperText={errors.modules ?? (moving ? t('form.modulesMoving', { name: targetName }) : noModules ? t('form.modulesEmpty') : t('form.modulesHelp'))}
                slotProps={{
                  select: {
                    multiple: true,
                    displayEmpty: true,
                    renderValue: (selected) => {
                      const keys = selected as string[];
                      return keys.length === 0 ? (
                        <Box component="span" sx={{ color: 'text.secondary' }}>
                          {t('form.modulesNone')}
                        </Box>
                      ) : (
                        <Stack direction="row" spacing={0.5} useFlexGap sx={{ flexWrap: 'wrap' }}>
                          {keys.map((key) => (
                            <Chip key={key} size="small" label={moduleName(key)} sx={{ maxWidth: '100%' }} />
                          ))}
                        </Stack>
                      );
                    },
                  },
                  inputLabel: { shrink: true },
                }}
              >
                {moduleOptions.map((m) => (
                  <MenuItem key={m.key} value={m.key} dense>
                    <Checkbox size="small" checked={value.modules.includes(m.key)} sx={{ p: 0.5, mr: 1 }} />
                    <ListItemText
                      primary={m.name}
                      secondary={m.active ? (m.key !== m.name ? m.key : undefined) : t('modules.inactiveKey', { key: m.key })} /* 이름이 key 와 같으면 한 번만 */
                      slotProps={{ secondary: { sx: { fontFamily: 'monospace' } } }}
                    />
                  </MenuItem>
                ))}
              </TextField>
              {/* 에이전트 세션 — 여러 AI 세션이 한 프로젝트에서 일할 때 누가 맡았는지(없어도 된다, 2026-11-23 일감 #26) */}
              <TextField
                label={t('fields.agentSession')}
                value={value.agentSession}
                onChange={(event) => onChange({ agentSession: event.target.value })}
                error={!!errors.agentSession}
                helperText={errors.agentSession ?? t('form.agentSessionHelp')}
                placeholder={t('form.agentSessionPlaceholder')}
                slotProps={{ htmlInput: { maxLength: MAX_AGENT_SESSION, autoComplete: 'off', spellCheck: false }, inputLabel: { shrink: true } }}
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
