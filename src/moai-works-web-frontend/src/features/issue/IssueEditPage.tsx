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

import { issuePath } from '@/app/routes';
import { useProject } from '@/features/project/project-context';
import { projectCan, useProjectsQuery } from '@/features/project/queries';
import { useWorkspace } from '@/features/workspace/workspace-context';
import { issueApi } from '@/shared/api/endpoints';
import { errorMessage, isApiError } from '@/shared/api/http';
import type { IssueDetail, IssueType, ProjectRef } from '@/shared/api/types';
import { useConfirm } from '@/shared/ui/ConfirmDialog';
import { ErrorState, ForbiddenState, LoadingState } from '@/shared/ui/States';

import { IssueAttachmentsProvider } from './attachment-context';
import { canUploadAttachment } from './attachments';
import {
  buildIssuePatch,
  dateProblems,
  estimateProblem,
  formFromIssue,
  isMoveError,
  isMoving,
  issueErrorField,
  type IssueFormValues,
} from './issue-form';
import { personName } from './issue-labels';
import { IssueForm, type IssueFormErrors } from './IssueForm';
import { IssueNotFound } from './IssueLayout';
import { parseIssueNumber, storeIssue, useIssueQuery, useIssueTypesQuery } from './queries';
import { useEditorUpload } from './use-attachment-upload';

/**
 * 일감 고치기 — 처음 읽은 일감과 달라진 필드만 rowVersion 과 함께 보낸다(moai-wiki 의 페이지 편집 자리).
 * 그 사이 다른 사람이 고쳤으면 서버가 409 CONCURRENT_UPDATE 로 답한다 — 그때는 "다시 불러오기"를 알린다(고친 내용은 덮어쓰지 않는다).
 * 설명에는 이미지 · 파일을 붙여넣기 · 끌어다 놓기 · 단추로 넣는다 — 이 일감에 바로 올리고 커서 자리에 표시를 넣는다(저장해야 설명에 남는다).
 * 프로젝트 칸에서 다른 프로젝트를 고르면 옮긴다(2026-11-24, Redmine 의 Project 칸) — 저장 전에 무엇이 함께 바뀌는지 확인을 받는다.
 */
export function IssueEditPage() {
  const { workspace } = useWorkspace();
  const project = useProject();
  const number = parseIssueNumber(useParams().number);
  const query = useIssueQuery(workspace.slug, number);
  const types = useIssueTypesQuery(workspace.slug, project.key);
  /** 다시 불러오면 양식을 새로 만든다 */
  const [generation, setGeneration] = useState(0);

  if (!projectCan(project, 'issue.edit') || project.status !== 'ACTIVE') {
    return <ForbiddenState />;
  }
  if (number === undefined || (query.isError && isApiError(query.error) && query.error.status === 404)) {
    return <IssueNotFound />;
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
  const confirm = useConfirm();
  // 옮길 수 있는 프로젝트 — 지금 프로젝트와 내가 일감을 만들 수 있는 프로젝트(서버가 준 myActions 로 가리기만 — 판정은 api)
  const projects = useProjectsQuery(workspace.slug);
  const projectOptions = useMemo<ProjectRef[]>(() => {
    const others = (projects.data ?? [])
      .filter((p) => p.key !== project.key && (p.myActions ?? []).includes('createIssue'))
      .map((p) => ({ key: p.key, name: p.name }));
    return [{ key: project.key, name: project.name }, ...others];
  }, [projects.data, project.key, project.name]);
  const [form, setForm] = useState<IssueFormValues>(() => formFromIssue(issue));
  const [notes, setNotes] = useState('');
  const [errors, setErrors] = useState<IssueFormErrors>({});
  const [busy, setBusy] = useState(false);
  /** 다른 사람이 먼저 고쳤다 — 이 양식으로는 저장할 수 없다 */
  const [conflict, setConflict] = useState(false);
  const [reloading, setReloading] = useState(false);

  const viewPath = issuePath(workspace.slug, issue.number);
  const patch = useMemo(() => buildIssuePatch(issue, form, notes), [issue, form, notes]);
  // 올려도 일감 상세는 다시 읽지 않는다 — 고치는 동안 바탕 일감이 바뀌면 남이 고친 것을 모르고 덮어쓸 수 있다
  const upload = useEditorUpload(
    canUploadAttachment(project) ? { slug: workspace.slug, number: issue.number, refreshIssue: false } : null,
  );
  // 설명은 마지막 입력 뒤 조금 늦게 올라온다 — 친 순간부터 "저장 전"이다(pending)
  const dirty = patch !== null || upload.pending;
  const uploading = upload.progress !== null;

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
    // 방금 친 설명까지 넣어 바뀐 것을 다시 잡는다
    const description = upload.flush() ?? form.description;
    const patch = buildIssuePatch(issue, { ...form, description }, notes);
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
    const moving = isMoving(issue, form);
    const target = projectOptions.find((p) => p.key === form.project)?.name ?? form.project;
    if (moving && !(await confirmMove(target))) {
      return;
    }
    setBusy(true);
    try {
      const saved = await issueApi.update(workspace.slug, issue.number, patch);
      storeIssue(queryClient, workspace.slug, saved);
      toast.success(moving ? t('move.moved', { number: saved.number, to: saved.project?.name ?? target }) : t('edit.saved'));
      void navigate(viewPath, { replace: true });
    } catch (e) {
      setBusy(false);
      if (isApiError(e, 'CONCURRENT_UPDATE')) {
        setConflict(true);
        window.scrollTo({ top: 0, behavior: 'smooth' });
        return;
      }
      const field = isApiError(e)
        ? (issueErrorField(e.code) ??
          (moving && isMoveError(e.code) ? 'project' : undefined) ??
          (e.code === 'ISSUE_NOT_FOUND' && 'parent' in patch ? 'parent' : undefined))
        : undefined;
      if (field && isApiError(e)) {
        setErrors({ [field]: e.message });
      } else {
        toast.error(errorMessage(e));
      }
    }
  };

  /**
   * 옮기기 확인 — 함께 바뀌는 것을 미리 알린다(하위 일감도 옮겨짐 · 상위가 남으면 끊김 · 모듈은 같은 key 만 남음 · 번호와 기록은 그대로).
   * 판정(두 프로젝트의 권한 · 유형 · 사람)은 api 가 한다
   */
  const confirmMove = (target: string) =>
    confirm({
      title: t('move.title'),
      confirmLabel: t('move.confirm'),
      description: (
        <Stack spacing={1}>
          <Typography variant="body2">{t('move.body', { number: issue.number, from: project.name, to: target })}</Typography>
          <Box component="ul" sx={{ m: 0, pl: 2.5, '& li': { py: 0.25 } }}>
            <Typography component="li" variant="body2">
              {issue.children.length > 0 ? t('move.children', { count: issue.children.length }) : t('move.noChildren')}
            </Typography>
            {issue.parent && (
              <Typography component="li" variant="body2">
                {t('move.parent', { number: issue.parent.number, from: project.name })}
              </Typography>
            )}
            {issue.modules.length > 0 && (
              <Typography component="li" variant="body2">
                {t('move.modules', { to: target })}
              </Typography>
            )}
            <Typography component="li" variant="body2">
              {t('move.kept', { number: issue.number })}
            </Typography>
            <Typography component="li" variant="body2">
              {t('move.permission', { from: project.name, to: target })}
            </Typography>
          </Box>
        </Stack>
      ),
    });

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
          {/* 올리는 동안에는 저장하지 않는다 — 설명에 "업로드 중…" 자리가 그대로 남는다 */}
          <Button type="submit" variant="contained" loading={busy} disabled={conflict || uploading}>
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

      <IssueAttachmentsProvider slug={workspace.slug} number={issue.number}>
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
          upload={upload}
          // 프로젝트 목록을 받은 뒤에 프로젝트 칸을 보인다(받기 전에 "옮길 곳 없음"으로 꺼 보이지 않게)
          projectOptions={projects.isSuccess ? projectOptions : undefined}
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
      </IssueAttachmentsProvider>
    </Box>
  );
}
