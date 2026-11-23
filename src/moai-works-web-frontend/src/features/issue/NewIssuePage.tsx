import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { Link as RouterLink, useNavigate, useSearchParams } from 'react-router';
import { toast } from 'sonner';

import { issuePath, projectPath } from '@/app/routes';
import { useProject } from '@/features/project/project-context';
import { useWorkspace } from '@/features/workspace/workspace-context';
import { issueApi } from '@/shared/api/endpoints';
import { errorMessage, isApiError } from '@/shared/api/http';
import type { IssueType } from '@/shared/api/types';
import { ForbiddenState, LoadingState } from '@/shared/ui/States';

import { buildIssueCreate, dateProblems, emptyIssueForm, estimateProblem, issueErrorField, type IssueFormValues } from './issue-form';
import { IssueForm, type IssueFormErrors } from './IssueForm';
import { parseIssueNumber, storeIssue, useIssueQuery, useIssueTypesQuery } from './queries';
import { useEditorUpload } from './use-attachment-upload';

/** 처음 고를 유형 — 작업(task)이 있으면 그것, 없으면 첫 유형 */
/** 새 일감의 처음 유형 — 쓰는 유형 가운데 첫째(새기능). 더 쓰지 않는 옛 유형은 고르지 않는다 */
function defaultType(types: IssueType[]): string {
  return types.find((type) => type.active)?.key ?? '';
}

/**
 * 새 일감 — 유형 · 제목 · 설명 · 상태 · 관리자 · 담당자 · 상위 일감 · 날짜를 정해 만든다(moai-wiki 의 새 페이지 자리).
 * ?parent=12 로 들어오면 그 일감 아래에 만든다. 만들 수 있는지는 서버가 준 이 프로젝트의 "할 수 있는 일"(createIssue)로만 가린다.
 */
export function NewIssuePage() {
  const { workspace } = useWorkspace();
  const project = useProject();
  const [params] = useSearchParams();
  const parentNumber = parseIssueNumber(params.get('parent') ?? undefined);
  const parent = useIssueQuery(workspace.slug, project.key, parentNumber);
  const types = useIssueTypesQuery(workspace.slug, project.key);

  if (!project.myActions.includes('createIssue')) {
    return <ForbiddenState />;
  }
  // 상위 일감 이름과 유형 목록을 받은 뒤에 양식을 연다(처음 값을 한 번에 채운다)
  if (types.isPending || (parentNumber !== undefined && parent.isPending)) {
    return <LoadingState />;
  }
  const parentRef = parent.data ? { number: parent.data.number, title: parent.data.title } : null;
  return <NewIssueForm types={types.data} initial={emptyIssueForm(defaultType(types.data ?? []), parentRef?.number ?? null)} parent={parentRef} />;
}

function NewIssueForm({ types, initial, parent }: {
  types: IssueType[] | undefined;
  initial: IssueFormValues;
  parent: { number: number; title: string } | null;
}) {
  const { t } = useTranslation(['issue', 'common']);
  const { workspace } = useWorkspace();
  const project = useProject();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [form, setForm] = useState<IssueFormValues>(initial);
  const [errors, setErrors] = useState<IssueFormErrors>({});
  const [busy, setBusy] = useState(false);
  // 새 일감에는 아직 올릴 곳이 없다 — 편집기 손잡이(방금 친 글 받기)만 쓴다
  const upload = useEditorUpload(null);

  const change = (patch: Partial<IssueFormValues>) => {
    setForm((before) => ({ ...before, ...patch }));
    setErrors((before) => {
      const next = { ...before };
      for (const key of Object.keys(patch) as (keyof IssueFormValues)[]) {
        delete next[key];
      }
      return next;
    });
  };

  const backTo = parent ? issuePath(workspace.slug, project.key, parent.number) : projectPath(workspace.slug, project.key);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    // 방금 친 설명은 아직 올라오지 않았을 수 있다 — 바로 받는다
    const description = upload.flush() ?? form.description;
    const found: IssueFormErrors = {};
    if (!form.title.trim()) {
      found.title = t('form.titleRequired');
    }
    for (const { field, problem } of dateProblems(form)) {
      found[field] = t(`form.${problem}`);
    }
    const estimate = estimateProblem(form.estimatedHours);
    if (estimate) {
      found.estimatedHours = t(`form.${estimate}`);
    }
    if (Object.keys(found).length > 0) {
      setErrors(found);
      return;
    }
    setBusy(true);
    try {
      const created = await issueApi.create(workspace.slug, project.key, buildIssueCreate({ ...form, description }));
      storeIssue(queryClient, workspace.slug, project.key, created);
      toast.success(t('create.created', { number: created.number }));
      void navigate(issuePath(workspace.slug, project.key, created.number), { replace: true });
    } catch (e) {
      const field = isApiError(e) ? (issueErrorField(e.code) ?? (e.code === 'ISSUE_NOT_FOUND' ? 'parent' : undefined)) : undefined;
      if (field && isApiError(e)) {
        setErrors({ [field]: e.message });
      } else {
        toast.error(errorMessage(e));
      }
    } finally {
      setBusy(false);
    }
  };

  return (
    <Box component="form" noValidate onSubmit={(event) => void submit(event)}>
      <Stack
        direction={{ xs: 'column', sm: 'row' }}
        spacing={1.5}
        sx={{ alignItems: { xs: 'stretch', sm: 'center' }, justifyContent: 'space-between', mb: 2 }}
      >
        <Box sx={{ minWidth: 0 }}>
          <Typography variant="overline" sx={{ color: 'text.secondary', display: 'block' }} noWrap>
            {[project.name, parent && `#${parent.number} ${parent.title}`].filter(Boolean).join(' › ')}
          </Typography>
          <Typography variant="h5" component="h1">
            {t('create.title')}
          </Typography>
          <Typography variant="body2" sx={{ color: 'text.secondary', mt: 0.5, maxWidth: 680 }}>
            {t('create.description')}
          </Typography>
        </Box>
        <Stack direction="row" spacing={1} sx={{ alignItems: 'center', justifyContent: 'flex-end', flexShrink: 0 }}>
          <Button component={RouterLink} to={backTo} color="inherit">
            {t('common:actions.cancel')}
          </Button>
          <Button type="submit" variant="contained" loading={busy}>
            {t('create.submit')}
          </Button>
        </Stack>
      </Stack>
      <IssueForm
        slug={workspace.slug}
        project={project}
        value={form}
        onChange={change}
        initialLabels={{ manager: null, assignee: null, parent }}
        types={types}
        errors={errors}
        managerHelp={t('form.managerHelp')}
        upload={upload}
        prefillOnMount
      />
    </Box>
  );
}
