import RestartAltRounded from '@mui/icons-material/RestartAltRounded';
import SaveRounded from '@mui/icons-material/SaveRounded';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Chip from '@mui/material/Chip';
import MenuItem from '@mui/material/MenuItem';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import Tab from '@mui/material/Tab';
import Tabs from '@mui/material/Tabs';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import type { UseQueryResult } from '@tanstack/react-query';
import { useId, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';

import { errorMessage, isApiError } from '@/shared/api/http';
import type { IssueTemplate } from '@/shared/api/types';
import { dateTimeMs } from '@/shared/lib/format';
import { templateSections } from '@/shared/lib/issue-template';
import { useElementWidth } from '@/shared/lib/use-element-width';
import { useConfirm } from '@/shared/ui/ConfirmDialog';
import { ErrorState, LoadingState } from '@/shared/ui/States';

import { personName } from './issue-labels';
import { MarkdownEditor } from './MarkdownEditor';
import { useMarkdownEditor } from './use-markdown-editor';
import { MarkdownView } from './MarkdownView';

/** 템플릿의 최대 길이 — api 와 같다(TEMPLATE_TOO_LONG) */
export const TEMPLATE_MAX = 30_000;
/** 이 너비(이 화면이 받은 너비)보다 좁으면 왼쪽 유형 목록 대신 고르기 상자 */
const LIST_FROM = 720;

interface Props {
  /** workspace = 워크스페이스 기본, project = 프로젝트 덮어쓰기 */
  scope: 'workspace' | 'project';
  query: UseQueryResult<IssueTemplate[]>;
  /** 고칠 수 있다 — 서버가 준 내 권한(workspace.settings.manage · project.manage)으로 가리기만 한다(판정은 api) */
  canManage: boolean;
  onSave: (type: string, body: string, rowVersion: number | null) => Promise<IssueTemplate>;
  onReset: (type: string) => Promise<IssueTemplate>;
}

/**
 * 유형별 일감 템플릿 관리 — 왼쪽에 유형 목록(이름 · 출처), 오른쪽에 고른 유형: 출처 표시(처음 템플릿 · 워크스페이스 · 이 프로젝트 · 물려받음),
 * 고친 사람 · 시각(밀리초), 저장 · 되돌리기 단추, 항목(제목 · "(선택)"), Markdown 편집기.
 * 유형은 늘어날 수 있어 탭이 아니라 세로 목록이다(2026-11-23 사용자: "앞으로 새 유형 나오면 어쩌려고 탭으로 했어. 왼쪽에 유형 목록 나오게 해").
 * 받은 너비가 720px 보다 좁으면 목록 대신 유형 고르기 상자.
 * 편집기는 화면 높이에 맞춰 길게 쓰고(일감 고치기와 같다), 단추는 편집기 위에 두어 늘 보인다(사용자: "편집기 화면이 화면에 맞춰져서 길게 늘어나도 문제 없지 않아?").
 * (2026-11-23 사용자: "이슈의 유형별로 작성 템플릿 넣어 주고 그걸 관리할 수 있게 해 줘")
 * 워크스페이스 설정과 프로젝트의 일감 템플릿 화면이 같이 쓴다. 고칠 수 없으면 읽기만 보인다.
 * 템플릿에는 일감 첨부가 없으므로 편집기의 그림 · 파일 올리기를 끈다.
 */
export function IssueTemplateManager({ scope, query, canManage, onSave, onReset }: Props) {
  const { t } = useTranslation(['issue', 'common']);
  const confirm = useConfirm();
  const tabsId = useId();
  const [selected, setSelected] = useState<string | null>(null);
  /** 유형마다 고치는 중인 글 — 탭을 옮겨도 남는다. 저장 · 되돌리기 뒤에는 지운다 */
  const [drafts, setDrafts] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);
  const { editorRef, pending, onPending, flush } = useMarkdownEditor();
  const [rootRef, rootWidth] = useElementWidth<HTMLDivElement>();
  const narrow = rootWidth !== null && rootWidth < LIST_FROM;

  if (query.isPending) {
    return <LoadingState />;
  }
  if (query.isError) {
    return <ErrorState error={query.error} onRetry={() => query.refetch()} />;
  }
  const templates = query.data;
  const current = templates.find((template) => template.type === selected) ?? templates[0];
  if (!current) {
    return null;
  }
  const draft = drafts[current.type] ?? current.body;
  const dirty = draft !== current.body;
  const tooLong = draft.length > TEMPLATE_MAX;
  const empty = !draft.trim();
  const sections = templateSections(draft);
  /** 이 범위에서 정한 템플릿인가 — 그래야 되돌릴 것이 있다 */
  const own = scope === 'workspace' ? current.source === 'WORKSPACE' : current.source === 'PROJECT';

  const forget = (type: string) =>
    setDrafts((before) => {
      const next = { ...before };
      delete next[type];
      return next;
    });

  const save = async () => {
    // 방금 친 글까지 받아서 다시 본다(아직 올라오지 않았을 수 있다)
    const body = flush() ?? draft;
    if (body === current.body || body.length > TEMPLATE_MAX || !body.trim()) {
      return;
    }
    setBusy(true);
    try {
      await onSave(current.type, body, current.rowVersion);
      forget(current.type);
      toast.success(t('templates.saved', { type: current.typeName }));
    } catch (e) {
      if (isApiError(e, 'CONCURRENT_UPDATE')) {
        // 고친 글은 남겨 두고 지금 내용을 다시 읽는다 — 견주어 보고 다시 저장한다
        toast.warning(t('templates.conflict'));
        void query.refetch();
      } else {
        toast.error(errorMessage(e));
      }
    } finally {
      setBusy(false);
    }
  };

  const reset = async () => {
    const ok = await confirm({
      title: t(scope === 'workspace' ? 'templates.resetWorkspaceTitle' : 'templates.resetProjectTitle', { type: current.typeName }),
      description: t(scope === 'workspace' ? 'templates.resetWorkspaceBody' : 'templates.resetProjectBody'),
      confirmLabel: t(scope === 'workspace' ? 'templates.resetWorkspace' : 'templates.resetProject'),
      destructive: true,
    });
    if (!ok) {
      return;
    }
    setBusy(true);
    try {
      await onReset(current.type);
      forget(current.type);
      toast.success(t('templates.resetDone', { type: current.typeName }));
    } catch (e) {
      toast.error(errorMessage(e));
    } finally {
      setBusy(false);
    }
  };

  const updater = personName(current.updatedBy);
  /** 출처 표시 — 프로젝트에서 물려받은 것은 "물려받음"을 붙인다 */
  const sourceLabel = (template: IssueTemplate) => {
    const key: IssueTemplate['source'] | `inherited${IssueTemplate['source']}` =
      scope === 'project' && template.inherited ? `inherited${template.source}` : template.source;
    return t(`templates.source.${key}`);
  };
  /** 목록의 이름 — 고치는 중이면 • 를 붙인다 */
  const typeLabel = (template: IssueTemplate) =>
    drafts[template.type] !== undefined && drafts[template.type] !== template.body ? `${template.typeName} •` : template.typeName;

  const actions = canManage && (
    <Stack direction="row" spacing={1} useFlexGap sx={{ flexWrap: 'wrap', ml: { sm: 'auto' } }}>
      <Button variant="contained" startIcon={<SaveRounded />} onClick={() => void save()} loading={busy} disabled={!(pending || (dirty && !tooLong && !empty))}>
        {t('templates.save')}
      </Button>
      {dirty && (
        <Button color="inherit" onClick={() => forget(current.type)} disabled={busy}>
          {t('templates.discard')}
        </Button>
      )}
      <Button color="inherit" startIcon={<RestartAltRounded />} onClick={() => void reset()} disabled={!own || busy}>
        {t(scope === 'workspace' ? 'templates.resetWorkspace' : 'templates.resetProject')}
      </Button>
    </Stack>
  );

  return (
    <Box
      ref={rootRef}
      sx={{ display: 'grid', gap: { xs: 2, md: 3 }, gridTemplateColumns: narrow ? 'minmax(0, 1fr)' : '220px minmax(0, 1fr)', alignItems: 'start' }}
    >
      {narrow ? (
        <TextField select size="small" label={t('templates.typesLabel')} value={current.type} onChange={(event) => setSelected(event.target.value)}>
          {templates.map((template) => (
            <MenuItem key={template.type} value={template.type}>
              {typeLabel(template)}
            </MenuItem>
          ))}
        </TextField>
      ) : (
        <Paper variant="outlined" sx={{ borderRadius: 2.5, py: 1 }}>
          <Typography variant="overline" component="h2" sx={{ display: 'block', px: 2, color: 'text.secondary', lineHeight: 2.25 }}>
            {t('templates.typesLabel')} · {templates.length}
          </Typography>
          <Tabs
            orientation="vertical"
            variant="scrollable"
            value={current.type}
            onChange={(_, next: string) => setSelected(next)}
            aria-label={t('templates.typesLabel')}
            sx={{
              maxHeight: 'calc(100dvh - 240px)',
              '& .MuiTabs-indicator': { left: 0, right: 'auto', width: 3, borderRadius: 2 },
              '& .MuiTab-root': { alignItems: 'stretch', textAlign: 'left', minHeight: 0, maxWidth: 'none', px: 2, py: 1, textTransform: 'none' },
              '& .MuiTab-root.Mui-selected': { bgcolor: 'action.selected' },
            }}
          >
            {templates.map((template) => (
              <Tab
                key={template.type}
                value={template.type}
                id={`${tabsId}-${template.type}`}
                aria-controls={`${tabsId}-${template.type}-panel`}
                aria-label={typeLabel(template)}
                label={
                  <Box sx={{ minWidth: 0 }}>
                    <Typography variant="body2" sx={{ fontWeight: 600, color: 'text.primary', overflowWrap: 'anywhere' }}>
                      {typeLabel(template)}
                    </Typography>
                    <Typography variant="caption" sx={{ display: 'block', color: 'text.secondary' }}>
                      {sourceLabel(template)}
                    </Typography>
                  </Box>
                }
              />
            ))}
          </Tabs>
        </Paper>
      )}

      <Box
        sx={{ minWidth: 0 }}
        {...(narrow
          ? { role: 'region', 'aria-label': current.typeName }
          : { role: 'tabpanel', id: `${tabsId}-${current.type}-panel`, 'aria-labelledby': `${tabsId}-${current.type}` })}
      >
        <Stack direction={{ xs: 'column', sm: 'row' }} useFlexGap sx={{ alignItems: { sm: 'center' }, columnGap: 1.5, rowGap: 1, mb: 1.25 }}>
          <Stack direction="row" useFlexGap sx={{ alignItems: 'center', flexWrap: 'wrap', columnGap: 1.5, rowGap: 0.5, minWidth: 0 }}>
            {/* 좁으면 위의 고르기 상자가 이름을 보인다 */}
            {!narrow && (
              <Typography variant="h6" component="h3">
                {current.typeName}
              </Typography>
            )}
            <Chip size="small" color={own ? 'primary' : 'default'} variant={own ? 'filled' : 'outlined'} label={sourceLabel(current)} />
            {current.updatedAt && (
              <Typography variant="caption" sx={{ color: 'text.secondary', fontVariantNumeric: 'tabular-nums' }}>
                {t('templates.updated', { name: updater ?? t('view.unknownPerson'), time: dateTimeMs(current.updatedAt) })}
              </Typography>
            )}
          </Stack>
          {actions}
        </Stack>
        <Typography variant="body2" sx={{ color: 'text.secondary', mb: 1.5, overflowWrap: 'anywhere' }}>
          {sections.length > 0
            ? t('templates.sections', {
                sections: sections.map((s) => (s.optional ? t('templates.optionalSection', { title: s.title }) : s.title)).join(' · '),
              })
            : t('templates.noSections')}
        </Typography>

        {canManage ? (
          <>
            <Paper
              variant="outlined"
              sx={{
                // 화면 높이에 맞춰 길게 — 일감 고치기 편집기와 같은 방식
                height: { xs: 420, sm: 'max(420px, calc(100dvh - 400px))', md: 'max(460px, calc(100dvh - 330px))' },
                borderRadius: 2.5,
                overflow: 'hidden',
                borderColor: tooLong ? 'error.main' : 'divider',
              }}
            >
              <MarkdownEditor
                key={current.type}
                ref={editorRef}
                onPending={onPending}
                value={draft}
                onChange={(body) => setDrafts((before) => ({ ...before, [current.type]: body }))}
                ariaLabel={t('templates.editorLabel', { type: current.typeName })}
                placeholder={t('templates.placeholder')}
                variant="document"
              />
            </Paper>
            <Typography variant="caption" sx={{ display: 'block', mt: 0.75, color: tooLong || empty ? 'error.main' : 'text.secondary' }}>
              {tooLong ? t('templates.tooLong', { max: TEMPLATE_MAX.toLocaleString() }) : empty ? t('templates.empty') : t('templates.help')}
            </Typography>
          </>
        ) : (
          <>
            <Paper variant="outlined" sx={{ borderRadius: 2.5, p: { xs: 2, sm: 3 } }}>
              <MarkdownView content={current.body} />
            </Paper>
            <Typography variant="caption" sx={{ display: 'block', mt: 0.75, color: 'text.secondary' }}>
              {t('templates.readOnly')}
            </Typography>
          </>
        )}
      </Box>
    </Box>
  );
}
