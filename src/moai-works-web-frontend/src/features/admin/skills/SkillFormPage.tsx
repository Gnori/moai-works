import SearchOffRounded from '@mui/icons-material/SearchOffRounded';
import Alert from '@mui/material/Alert';
import AlertTitle from '@mui/material/AlertTitle';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useQueryClient } from '@tanstack/react-query';
import { useEffect, useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { Link as RouterLink, useNavigate, useParams } from 'react-router';
import { toast } from 'sonner';

import { skillPath, skillsPath } from '@/app/routes';
import { MarkdownEditor } from '@/features/issue/MarkdownEditor';
import { useMarkdownEditor } from '@/features/issue/use-markdown-editor';
import { skillApi } from '@/shared/api/endpoints';
import { errorMessage, isApiError } from '@/shared/api/http';
import type { SkillDetail } from '@/shared/api/types';
import { EmptyState, ErrorState, LoadingState } from '@/shared/ui/States';

import { storeSkill, useSkillQuery } from './queries';
import {
  descriptionLength,
  normalizeBody,
  normalizeDescription,
  SKILL_DESCRIPTION_MAX,
  SKILL_NAME_MAX,
  SKILL_NOTE_MAX,
  SKILL_NOTE_MIN,
  skillErrorField,
  skillNameProblem,
  skillNoteProblem,
} from './skill-rules';

type Field = 'name' | 'description' | 'body' | 'changeNote';
type Errors = Partial<Record<Field, string>>;

interface Values {
  name: string;
  description: string;
  body: string;
  changeNote: string;
}

/** 새 스킬 — 이름(만든 뒤 바꾸지 않는다) · 설명 · 본문 · 바뀐 점 요약(비우면 "처음 만듦"). 저장하면 1리비전 */
export function NewSkillPage() {
  return <SkillForm skill={null} onReload={async () => undefined} />;
}

/**
 * 스킬 수정 — 처음 읽은 리비전(baseRevisionNo)과 함께 보낸다. 그 사이 다른 사람이 고쳤으면 서버가 409 SKILL_CHANGED 로 답하고, 이 화면은 "다시 불러오기"를
 * 알린다(고친 내용을 덮어쓰지 않는다 — 일감 수정과 같다). 지운 스킬은 고칠 수 없다(되살린 뒤).
 * 바뀐 점 요약(Agent 에게 보인다)은 꼭 넣는다(일감 #183 — 서버도 400 CHANGE_NOTE_REQUIRED).
 */
export function EditSkillPage() {
  const { t } = useTranslation('admin');
  const name = useParams().name;
  const query = useSkillQuery(name);
  /** 다시 불러오면 양식을 새로 만든다 */
  const [generation, setGeneration] = useState(0);

  if (query.isError && isApiError(query.error) && query.error.status === 404) {
    return <NotFound />;
  }
  if (query.isPending) {
    return <LoadingState />;
  }
  if (query.isError) {
    return <ErrorState error={query.error} onRetry={() => query.refetch()} />;
  }
  if (query.data.deleted) {
    return (
      <EmptyState
        title={t('skills.form.deletedTitle')}
        description={t('skills.form.deletedBody')}
        action={
          <Button variant="outlined" component={RouterLink} to={skillPath(query.data.name)}>
            {t('skills.actions.view')}
          </Button>
        }
      />
    );
  }
  return (
    <SkillForm
      key={`${query.data.name}:${generation}`}
      skill={query.data}
      onReload={async () => {
        await query.refetch();
        setGeneration((n) => n + 1);
      }}
    />
  );
}

function NotFound() {
  const { t } = useTranslation('admin');
  return (
    <EmptyState
      icon={<SearchOffRounded />}
      title={t('skills.notFoundTitle')}
      description={t('skills.notFoundBody')}
      action={
        <Button variant="outlined" component={RouterLink} to={skillsPath()}>
          {t('skills.backToList')}
        </Button>
      }
    />
  );
}

function SkillForm({ skill, onReload }: { skill: SkillDetail | null; onReload: () => Promise<void> }) {
  const { t } = useTranslation(['admin', 'common']);
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { editorRef, pending, onPending, flush } = useMarkdownEditor();
  const creating = skill === null;
  const [values, setValues] = useState<Values>(() => ({
    name: skill?.name ?? '',
    description: skill?.description ?? '',
    body: skill?.body ?? '',
    changeNote: '',
  }));
  const [errors, setErrors] = useState<Errors>({});
  const [busy, setBusy] = useState(false);
  /** 다른 사람이 먼저 고쳤다 — 이 양식으로는 저장할 수 없다 */
  const [conflict, setConflict] = useState(false);
  const [reloading, setReloading] = useState(false);

  const changed = creating
    ? values.name.trim() !== '' || values.description.trim() !== '' || values.body.trim() !== ''
    : normalizeDescription(values.description) !== skill.description || normalizeBody(values.body) !== skill.body;
  // 본문은 마지막 입력 뒤 조금 늦게 올라온다 — 친 순간부터 "저장 전"이다(pending)
  const dirty = changed || pending;
  const backTo = creating ? skillsPath() : skillPath(skill.name);
  const descLength = descriptionLength(values.description);

  // 창을 닫거나 새로 고칠 때 저장 전이면 한 번 묻는다
  useEffect(() => {
    if (!dirty || busy) {
      return;
    }
    const warn = (event: BeforeUnloadEvent) => event.preventDefault();
    window.addEventListener('beforeunload', warn);
    return () => window.removeEventListener('beforeunload', warn);
  }, [dirty, busy]);

  const change = (next: Partial<Values>) => {
    setValues((before) => ({ ...before, ...next }));
    setErrors((before) => {
      const rest = { ...before };
      for (const key of Object.keys(next) as Field[]) {
        delete rest[key];
      }
      return rest;
    });
  };

  const save = async (event: FormEvent) => {
    event.preventDefault();
    // 방금 친 본문까지 넣는다
    const body = flush() ?? values.body;
    const found: Errors = {};
    if (creating) {
      const problem = skillNameProblem(values.name);
      if (problem) {
        found.name = t(`skills.form.name_${problem}`);
      }
    }
    if (descLength === 0) {
      found.description = t('skills.form.descriptionRequired');
    } else if (descLength > SKILL_DESCRIPTION_MAX) {
      found.description = t('skills.form.descriptionTooLong', { max: SKILL_DESCRIPTION_MAX });
    }
    if (!body.trim()) {
      found.body = t('skills.form.bodyRequired');
    }
    if (Object.keys(found).length > 0) {
      setErrors(found);
      return;
    }
    if (!creating && normalizeDescription(values.description) === skill.description && normalizeBody(body) === skill.body) {
      toast.info(t('skills.form.noChanges'));
      return;
    }
    // 바뀐 점 요약 — 고칠 때는 꼭, 만들 때는 비우면 서버가 "처음 만듦"으로 남긴다
    const noteIssue = skillNoteProblem(values.changeNote, creating);
    if (noteIssue) {
      setErrors({ changeNote: t(`skills.form.note_${noteIssue}`, { min: SKILL_NOTE_MIN, max: SKILL_NOTE_MAX }) });
      return;
    }
    setBusy(true);
    const changeNote = values.changeNote.trim();
    try {
      const saved = creating
        ? await skillApi.create({ name: values.name.trim(), description: values.description, body, changeNote: changeNote || undefined })
        : await skillApi.update(skill.name, { description: values.description, body, baseRevisionNo: skill.revisionNo, changeNote });
      storeSkill(queryClient, saved);
      toast.success(creating ? t('skills.form.created', { name: saved.name }) : t('skills.form.saved', { no: saved.revisionNo }));
      void navigate(skillPath(saved.name), { replace: !creating });
    } catch (e) {
      setBusy(false);
      if (isApiError(e, 'SKILL_CHANGED')) {
        setConflict(true);
        window.scrollTo({ top: 0, behavior: 'smooth' });
        return;
      }
      const field = isApiError(e) ? skillErrorField(e.code) : undefined;
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

  return (
    <Box component="form" noValidate onSubmit={(event) => void save(event)}>
      <Stack
        direction={{ xs: 'column', sm: 'row' }}
        spacing={1.5}
        sx={{ alignItems: { xs: 'stretch', sm: 'center' }, justifyContent: 'space-between', mb: 2 }}
      >
        <Box sx={{ minWidth: 0 }}>
          <Typography variant="overline" sx={{ color: 'text.secondary', display: 'block' }} noWrap>
            {creating ? `${t('eyebrow')} › ${t('skills.title')}` : `${t('skills.title')} › ${skill.name} · ${t('skills.revisionNo', { no: skill.revisionNo })}`}
          </Typography>
          <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
            <Typography variant="h5" component="h1">
              {creating ? t('skills.form.newHeading') : t('skills.form.editHeading')}
            </Typography>
            {dirty && (
              <Typography variant="caption" role="status" sx={{ color: 'text.secondary' }}>
                {t('skills.form.unsaved')}
              </Typography>
            )}
          </Stack>
        </Box>
        <Stack direction="row" spacing={1} sx={{ alignItems: 'center', justifyContent: 'flex-end', flexShrink: 0 }}>
          <Button component={RouterLink} to={backTo} color="inherit">
            {t('skills.form.close')}
          </Button>
          <Button type="submit" variant="contained" loading={busy} disabled={conflict}>
            {t('common:actions.save')}
          </Button>
        </Stack>
      </Stack>

      {conflict && (
        <Alert
          severity="warning"
          sx={{ mb: 2 }}
          action={
            <Button color="inherit" size="small" onClick={() => void reload()} loading={reloading}>
              {t('skills.form.reload')}
            </Button>
          }
        >
          <AlertTitle>{t('skills.form.conflictTitle')}</AlertTitle>
          {t('skills.form.conflictBody')}
        </Alert>
      )}

      <Stack spacing={2.5}>
        {creating ? (
          <TextField
            label={t('skills.form.nameLabel')}
            value={values.name}
            onChange={(event) => change({ name: event.target.value })}
            error={!!errors.name}
            helperText={errors.name ?? t('skills.form.nameHelp')}
            required
            autoFocus
            sx={{ maxWidth: 560 }}
            slotProps={{ htmlInput: { maxLength: SKILL_NAME_MAX + 8, spellCheck: false, autoCapitalize: 'none', sx: { fontFamily: 'monospace' } } }}
          />
        ) : (
          <Box>
            <Typography variant="caption" sx={{ color: 'text.secondary', display: 'block' }}>
              {t('skills.form.nameLabel')}
            </Typography>
            <Typography sx={{ fontFamily: 'monospace', fontWeight: 600, overflowWrap: 'anywhere' }}>{skill.name}</Typography>
            <Typography variant="caption" sx={{ color: 'text.secondary' }}>
              {t('skills.form.nameFixed')}
            </Typography>
          </Box>
        )}
        <TextField
          label={t('skills.form.descriptionLabel')}
          value={values.description}
          onChange={(event) => change({ description: event.target.value })}
          error={!!errors.description || descLength > SKILL_DESCRIPTION_MAX}
          helperText={
            <Box component="span" sx={{ display: 'flex', gap: 2, justifyContent: 'space-between' }}>
              <span>{errors.description ?? t('skills.form.descriptionHelp')}</span>
              <Box component="span" aria-live="polite" sx={{ flexShrink: 0, fontVariantNumeric: 'tabular-nums' }}>
                {t('skills.form.counter', { length: descLength, max: SKILL_DESCRIPTION_MAX })}
              </Box>
            </Box>
          }
          required
          multiline
          minRows={3}
          maxRows={10}
        />
        <Box>
          <Typography variant="subtitle2" component="h2" sx={{ mb: 0.75 }}>
            {t('skills.form.bodyLabel')}
          </Typography>
          <Paper
            variant="outlined"
            sx={{
              height: { xs: 420, md: 'max(460px, calc(100dvh - 420px))' },
              borderRadius: 2.5,
              overflow: 'hidden',
              borderColor: errors.body ? 'error.main' : 'divider',
            }}
          >
            {/* 첨부 올리기는 일감의 것이라 두지 않는다(onFiles 없음) — 스킬 본문은 글만 */}
            <MarkdownEditor
              ref={editorRef}
              onPending={onPending}
              value={values.body}
              onChange={(body) => change({ body })}
              placeholder={t('skills.form.bodyPlaceholder')}
              ariaLabel={t('skills.form.bodyLabel')}
              variant="document"
            />
          </Paper>
          <Typography variant="caption" color={errors.body ? 'error' : 'text.secondary'} sx={{ display: 'block', mt: 0.75 }}>
            {errors.body ?? t('skills.form.bodyHelp')}
          </Typography>
        </Box>
        <TextField
          label={t('skills.form.noteLabel')}
          value={values.changeNote}
          onChange={(event) => change({ changeNote: event.target.value })}
          error={!!errors.changeNote}
          helperText={
            errors.changeNote ??
            (creating ? t('skills.form.noteHelpCreate') : t('skills.form.noteHelp', { min: SKILL_NOTE_MIN, max: SKILL_NOTE_MAX }))
          }
          placeholder={creating ? t('skills.form.notePlaceholderCreate') : t('skills.form.notePlaceholder')}
          required={!creating}
          multiline
          minRows={1}
          maxRows={4}
          slotProps={{ htmlInput: { maxLength: SKILL_NOTE_MAX } }}
        />
      </Stack>
    </Box>
  );
}
