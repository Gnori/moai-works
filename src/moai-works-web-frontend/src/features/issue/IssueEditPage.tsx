import SearchOffRounded from '@mui/icons-material/SearchOffRounded';
import Alert from '@mui/material/Alert';
import AlertTitle from '@mui/material/AlertTitle';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useQueryClient } from '@tanstack/react-query';
import { useEffect, useMemo, useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { Link as RouterLink, useNavigate, useParams } from 'react-router';
import { toast } from 'sonner';

import { issuePath, projectPath } from '@/app/routes';
import { useProject } from '@/features/project/project-context';
import { projectCan } from '@/features/project/queries';
import { useWorkspace } from '@/features/workspace/workspace-context';
import { issueApi } from '@/shared/api/endpoints';
import { errorMessage, isApiError } from '@/shared/api/http';
import type { IssueDetail, IssueType } from '@/shared/api/types';
import { EmptyState, ErrorState, ForbiddenState, LoadingState } from '@/shared/ui/States';

import { buildIssuePatch, dateProblems, estimateProblem, formFromIssue, issueErrorField, type IssueFormValues } from './issue-form';
import { personName } from './issue-labels';
import { IssueForm, type IssueFormErrors } from './IssueForm';
import { parseIssueNumber, storeIssue, useIssueQuery, useIssueTypesQuery } from './queries';

/**
 * 일감 고치기 — 처음 읽은 일감과 달라진 필드만 rowVersion 과 함께 보낸다(moai-wiki 의 페이지 편집 자리).
 * 그 사이 다른 사람이 고쳤으면 서버가 409 CONCURRENT_UPDATE 로 답한다 — 그때는 "다시 불러오기"를 알린다(고친 내용은 덮어쓰지 않는다).
 */
export function IssueEditPage() {
  const { t } = useTranslation('issue');
  const { workspace } = useWorkspace();
  const project = useProject();
  const number = parseIssueNumber(useParams().number);
  const query = useIssueQuery(workspace.slug, project.key, number);
  const types = useIssueTypesQuery(workspace.slug, project.key);
  /** 다시 불러오면 양식을 새로 만든다 */
  const [generation, setGeneration] = useState(0);

  if (!projectCan(project, 'issue.edit') || project.status !== 'ACTIVE') {
    return <ForbiddenState />;
  }
  if (number === undefined || (query.isError && isApiError(query.error) && query.error.status === 404)) {
    return (
      <EmptyState
        icon={<SearchOffRounded />}
        title={t('view.notFoundTitle')}
        description={t('view.notFoundBody')}
        action={
          <Button component={RouterLink} to={projectPath(workspace.slug, project.key)} variant="outlined">
            {t('view.backToProject', { name: project.name })}
          </Button>
        }
      />
    );
  }
  if (query.isPending || types.isPending) {
    return <LoadingState />;
  }
  if (query.isError) {
    return <ErrorState error={query.error} onRetry={() => query.refetch()} />;
  }
  return (
    <Editor
      key={`${query.data.number}:${generation}`}
      issue={query.data}
      types={types.data}
      onReload={async () => {
        await query.refetch();
        setGeneration((n) => n + 1);
      }}
    />
  );
}

function Editor({ issue, types, onReload }: { issue: IssueDetail; types: IssueType[] | undefined; onReload: () => Promise<void> }) {
  const { t } = useTranslation(['issue', 'common']);
  const { workspace } = useWorkspace();
  const project = useProject();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [form, setForm] = useState<IssueFormValues>(() => formFromIssue(issue));
  const [notes, setNotes] = useState('');
  const [errors, setErrors] = useState<IssueFormErrors>({});
  const [busy, setBusy] = useState(false);
  /** 다른 사람이 먼저 고쳤다 — 이 양식으로는 저장할 수 없다 */
  const [conflict, setConflict] = useState(false);
  const [reloading, setReloading] = useState(false);

  const viewPath = issuePath(workspace.slug, project.key, issue.number);
  const patch = useMemo(() => buildIssuePatch(issue, form, notes), [issue, form, notes]);
  const dirty = patch !== null;

  // 창을 닫거나 새로 고칠 때 저장 전이면 한 번 묻는다
  useEffect(() => {
    if (!dirty || busy) {
      return;
    }
    const warn = (event: BeforeUnloadEvent) => event.preventDefault();
    window.addEventListener('beforeunload', warn);
    return () => window.removeEventListener('beforeunload', warn);
  }, [dirty, busy]);

  const change = (next: Partial<IssueFormValues>) => {
    setForm((before) => ({ ...before, ...next }));
    setErrors((before) => {
      const rest = { ...before };
      for (const key of Object.keys(next) as (keyof IssueFormValues)[]) {
        delete rest[key];
      }
      return rest;
    });
  };

  const save = async (event: FormEvent) => {
    event.preventDefault();
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
    if (!patch) {
      toast.info(t('edit.noChanges'));
      return;
    }
    setBusy(true);
    try {
      const saved = await issueApi.update(workspace.slug, project.key, issue.number, patch);
      storeIssue(queryClient, workspace.slug, project.key, saved);
      toast.success(t('edit.saved'));
      void navigate(viewPath, { replace: true });
    } catch (e) {
      setBusy(false);
      if (isApiError(e, 'CONCURRENT_UPDATE')) {
        setConflict(true);
        window.scrollTo({ top: 0, behavior: 'smooth' });
        return;
      }
      const field = isApiError(e)
        ? (issueErrorField(e.code) ?? (e.code === 'ISSUE_NOT_FOUND' && 'parent' in patch ? 'parent' : undefined))
        : undefined;
      if (field && isApiError(e)) {
        setErrors({ [field]: e.message });
      } else {
        toast.error(errorMessage(e));
      }
    }
  };

  const reload = async () => {
    setReloading(true);
    try {
      await onReload();
    } catch (e) {
      toast.error(errorMessage(e));
      setReloading(false);
    }
  };

  const person = (p: IssueDetail['manager']) => (p ? { id: p.id, name: personName(p) ?? p.id, username: p.username } : null);

  return (
    <Box component="form" noValidate onSubmit={(event) => void save(event)}>
      <Stack
        direction={{ xs: 'column', sm: 'row' }}
        spacing={1.5}
        sx={{ alignItems: { xs: 'stretch', sm: 'center' }, justifyContent: 'space-between', mb: 2 }}
      >
        <Box sx={{ minWidth: 0 }}>
          <Typography variant="overline" sx={{ color: 'text.secondary', display: 'block' }} noWrap>
            {`${project.name} › #${issue.number}`}
          </Typography>
          <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
            <Typography variant="h5" component="h1">
              {t('edit.heading')}
            </Typography>
            {dirty && (
              <Typography variant="caption" role="status" sx={{ color: 'text.secondary' }}>
                {t('edit.unsaved')}
              </Typography>
            )}
          </Stack>
        </Box>
        <Stack direction="row" spacing={1} sx={{ alignItems: 'center', justifyContent: 'flex-end', flexShrink: 0 }}>
          <Button component={RouterLink} to={viewPath} color="inherit">
            {t('edit.close')}
          </Button>
          <Button type="submit" variant="contained" loading={busy} disabled={conflict}>
            {t('edit.save')}
          </Button>
        </Stack>
      </Stack>

      {conflict && (
        <Alert
          severity="warning"
          sx={{ mb: 2 }}
          action={
            <Button color="inherit" size="small" onClick={() => void reload()} loading={reloading}>
              {t('edit.reload')}
            </Button>
          }
        >
          <AlertTitle>{t('edit.conflictTitle')}</AlertTitle>
          {t('edit.conflictBody')}
        </Alert>
      )}

      <IssueForm
        slug={workspace.slug}
        project={project}
        value={form}
        onChange={change}
        initialLabels={{
          manager: person(issue.manager),
          assignee: person(issue.assignee),
          parent: issue.parent ? { number: issue.parent.number, title: issue.parent.title } : null,
        }}
        types={types}
        errors={errors}
        selfNumber={issue.number}
        extra={
          <TextField
            label={t('edit.notesLabel')}
            value={notes}
            onChange={(event) => setNotes(event.target.value)}
            multiline
            minRows={2}
            helperText={t('edit.notesHelp')}
            slotProps={{ htmlInput: { maxLength: 30000 } }}
            sx={{ mt: 2.5 }}
          />
        }
      />
    </Box>
  );
}
