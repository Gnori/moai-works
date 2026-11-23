import RestoreRounded from '@mui/icons-material/RestoreRounded';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Chip from '@mui/material/Chip';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogContentText from '@mui/material/DialogContentText';
import DialogTitle from '@mui/material/DialogTitle';
import MenuItem from '@mui/material/MenuItem';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import Tab from '@mui/material/Tab';
import Tabs from '@mui/material/Tabs';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useQueryClient } from '@tanstack/react-query';
import { useState, type SyntheticEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { useSearchParams } from 'react-router';
import { toast } from 'sonner';

import { skillApi } from '@/shared/api/endpoints';
import { errorMessage, isApiError } from '@/shared/api/http';
import type { SkillDetail, SkillRevision, SkillRevisionDiff, SkillRevisionItem } from '@/shared/api/types';
import { DiffView } from '@/shared/ui/revisions/DiffView';
import { RevisionCard, RevisionMeta, RevisionSplit, RevisionTimeline, revisionChipSx, useRevisionReveal } from '@/shared/ui/revisions/RevisionTimeline';
import { compareCandidates, readRevisionSelection, writeRevisionSelection, type RevisionSelection, type RevisionView } from '@/shared/ui/revisions/revision-view';
import { EmptyState, ErrorState, LoadingState } from '@/shared/ui/States';
import { VisuallyHidden } from '@/shared/ui/VisuallyHidden';
import { MarkdownView } from '@/features/issue/MarkdownView';

import { skillKeys, storeSkill, useSkillDiffQuery, useSkillRevisionQuery, useSkillRevisionsQuery } from './queries';
import { SKILL_NOTE_MAX } from './skill-rules';

/**
 * 스킬의 "리비전" 탭 — 일감 리비전 탭과 같은 모양(shared/ui/revisions): 타임라인(새 리비전부터 — 누가 · 밀리초 시각 · 길 · 한 일 · 바꾼 것 · 바꾼 까닭),
 * 고른 리비전과 다른 리비전의 차이(설명은 전 · 후, 본문은 줄 차이) · 그 리비전 전체, 그리고 "이 리비전으로 되돌리기"(확인 — 그 내용으로 새 리비전).
 * 고른 것은 주소에 남는다(?tab=revisions&rev=3&from=1&view=snapshot).
 */
export function SkillRevisions({ skill }: { skill: SkillDetail }) {
  const { t } = useTranslation('admin');
  const [params, setParams] = useSearchParams();
  const { timelineRef, panelRef, reveal } = useRevisionReveal();
  const list = useSkillRevisionsQuery(skill.name);
  const items = list.data?.pages.flatMap((page) => page.items) ?? [];
  /** 지금 리비전 — 리비전은 1부터 빠짐없이 쌓인다. 목록이 더 새로우면 목록을 믿는다 */
  const latest = Math.max(skill.revisionNo, items[0]?.no ?? 0);
  const selection = readRevisionSelection(params, latest);

  const choose = (next: RevisionSelection, scroll = false) => {
    setParams(writeRevisionSelection(params, next), { replace: true });
    if (scroll) {
      reveal();
    }
  };

  if (list.isPending) {
    return <LoadingState />;
  }
  if (list.isError) {
    return <ErrorState error={list.error} onRetry={() => list.refetch()} />;
  }
  if (items.length === 0 || latest < 1) {
    return <EmptyState title={t('skills.revisions.emptyTitle')} description={t('skills.revisions.emptyBody')} />;
  }
  return (
    <RevisionSplit
      timelineRef={timelineRef}
      panelRef={panelRef}
      timeline={
        <RevisionTimeline
          items={items}
          selected={selection.rev}
          onSelect={(no) => choose({ rev: no, from: undefined, view: selection.view }, true)}
          hasMore={list.hasNextPage}
          loadingMore={list.isFetchingNextPage}
          onMore={() => void list.fetchNextPage()}
          tags={(item) => <RevisionTags item={item} />}
          extra={(item) => (item.changeNote ? t('skills.revisions.note', { note: item.changeNote }) : null)}
        />
      }
    >
      <RevisionPanel
        key={`${selection.rev}:${selection.from ?? ''}`}
        skill={skill}
        latest={latest}
        selection={selection}
        onChange={(next) => choose(next)}
      />
    </RevisionSplit>
  );
}

/** 한 일(만듦 · 수정 · 가져옴 · 되돌림)과 바꾼 것(설명 · 본문) */
function RevisionTags({ item }: { item: SkillRevisionItem }) {
  const { t } = useTranslation('admin');
  const kind =
    item.kind === 'RESTORE' && item.restoredFrom !== null
      ? t('skills.revisions.kindRestoredFrom', { no: item.restoredFrom })
      : t(`skills.revisions.kind.${item.kind}`);
  return (
    <>
      <Chip
        size="small"
        variant="outlined"
        color={item.kind === 'CREATE' ? 'success' : item.kind === 'RESTORE' ? 'warning' : 'default'}
        label={kind}
        sx={revisionChipSx}
      />
      {item.no > 1 &&
        item.changedFields.map((field) => (
          <Chip key={field} size="small" variant="outlined" label={t(`skills.revisions.field.${field}`)} sx={revisionChipSx} />
        ))}
    </>
  );
}

/** 고른 리비전 — 다른 리비전과의 차이(기본) 또는 그 리비전 전체, 그리고 되돌리기 */
function RevisionPanel({ skill, latest, selection, onChange }: {
  skill: SkillDetail;
  latest: number;
  selection: RevisionSelection;
  onChange: (next: RevisionSelection) => void;
}) {
  const { t } = useTranslation('admin');
  const { rev, from, view } = selection;
  const diff = useSkillDiffQuery(skill.name, rev, from, view === 'diff');
  const snapshot = useSkillRevisionQuery(skill.name, rev, view === 'snapshot');
  const base = from ?? rev - 1;
  const candidates = compareCandidates(latest, rev);
  const [restoring, setRestoring] = useState(false);
  const canRestore = !skill.deleted && rev !== skill.revisionNo;

  return (
    <Box sx={{ minWidth: 0 }}>
      <Stack
        direction={{ xs: 'column', sm: 'row' }}
        spacing={1.5}
        useFlexGap
        sx={{ alignItems: { xs: 'stretch', sm: 'center' }, justifyContent: 'space-between', flexWrap: 'wrap', mb: 1.5 }}
      >
        <Tabs
          value={view}
          onChange={(_: SyntheticEvent, next: RevisionView) => onChange({ ...selection, view: next })}
          aria-label={t('skills.revisions.viewLabel')}
          variant="scrollable"
          scrollButtons={false}
          sx={{ minHeight: 40 }}
        >
          <Tab value="diff" label={t('skills.revisions.tabDiff')} sx={{ minHeight: 40 }} />
          <Tab value="snapshot" label={t('skills.revisions.tabSnapshot')} sx={{ minHeight: 40 }} />
        </Tabs>
        <Stack direction="row" spacing={1} useFlexGap sx={{ alignItems: 'center', flexWrap: 'wrap' }}>
          {view === 'diff' && latest > 1 && (
            <TextField
              select
              size="small"
              label={t('skills.revisions.compareWith')}
              value={base}
              onChange={(event) => onChange({ ...selection, from: Number(event.target.value) })}
              sx={{ minWidth: 200 }}
            >
              {candidates.map((n) => (
                <MenuItem key={n} value={n}>
                  {n === 0
                    ? t('skills.revisions.nothingOption')
                    : n === rev - 1
                      ? t('skills.revisions.previousOption', { no: n })
                      : t('skills.revisionNo', { no: n })}
                </MenuItem>
              ))}
            </TextField>
          )}
          {canRestore && (
            <Button variant="outlined" color="warning" startIcon={<RestoreRounded />} onClick={() => setRestoring(true)}>
              {t('skills.revisions.restore')}
            </Button>
          )}
        </Stack>
      </Stack>

      {view === 'diff' ? (
        diff.isPending ? (
          <LoadingState />
        ) : diff.isError ? (
          <ErrorState error={diff.error} onRetry={() => diff.refetch()} />
        ) : (
          <DiffBody data={diff.data} />
        )
      ) : snapshot.isPending ? (
        <LoadingState />
      ) : snapshot.isError ? (
        <ErrorState error={snapshot.error} onRetry={() => snapshot.refetch()} />
      ) : (
        <Snapshot revision={snapshot.data} />
      )}
      {restoring && <RestoreDialog skill={skill} no={rev} onClose={() => setRestoring(false)} onDone={(next) => onChange({ rev: next, from: undefined, view: 'diff' })} />}
    </Box>
  );
}

/** 두 리비전의 차이 — 누가 언제 남긴 리비전끼리인지, 이름 · 설명(전 → 후), 본문의 줄 차이 */
function DiffBody({ data }: { data: SkillRevisionDiff }) {
  const { t } = useTranslation('admin');
  const summary =
    data.from === null
      ? t('skills.revisions.diffFromNothing', { to: data.to, added: data.added })
      : t('skills.revisions.diffSummary', { from: data.from, to: data.to, fields: data.fields.length, added: data.added, removed: data.removed });
  const bodyChanged = data.added + data.removed > 0;
  return (
    <Stack spacing={2}>
      <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1, '& > *': { flex: '1 1 220px', minWidth: 0 } }}>
        <RevisionCard label={t('skills.revisions.fromLabel')} info={data.fromInfo} empty={t('skills.revisions.nothing')} />
        <RevisionCard label={t('skills.revisions.toLabel')} info={data.toInfo} empty={t('skills.revisions.nothing')} />
      </Box>
      <Typography variant="body2" sx={{ color: 'text.secondary' }}>
        {summary}
      </Typography>
      {data.from !== null && data.from > data.to && (
        <Alert severity="info">{t('skills.revisions.reversed', { from: data.from, to: data.to })}</Alert>
      )}

      <Box component="section">
        <Typography variant="subtitle1" component="h2" sx={{ fontWeight: 700, mb: 1 }}>
          {t('skills.revisions.fieldsTitle')}
        </Typography>
        {data.fields.length === 0 ? (
          <Typography variant="body2" sx={{ color: 'text.secondary' }}>
            {t('skills.revisions.fieldsEmpty')}
          </Typography>
        ) : (
          <Paper variant="outlined" sx={{ borderRadius: 2.5, overflow: 'hidden' }}>
            <Box component="dl" sx={{ m: 0 }}>
              {data.fields.map((change, index) => (
                <Box
                  key={change.field}
                  sx={{
                    display: 'grid',
                    gridTemplateColumns: { xs: 'minmax(0, 1fr)', sm: '120px minmax(0, 1fr)' },
                    borderTop: index > 0 ? 1 : 0,
                    borderColor: 'divider',
                  }}
                >
                  <Typography component="dt" variant="body2" sx={{ px: 1.5, py: 0.75, fontWeight: 600, bgcolor: 'background.subtle' }}>
                    {t(`skills.revisions.field.${change.field}`)}
                  </Typography>
                  <Box component="dd" sx={{ m: 0, minWidth: 0 }}>
                    {data.from !== null && <ValueLine op="del" text={change.before ?? ''} />}
                    <ValueLine op="add" text={change.after ?? ''} />
                  </Box>
                </Box>
              ))}
            </Box>
          </Paper>
        )}
      </Box>

      <Box component="section">
        <Stack direction="row" spacing={1} useFlexGap sx={{ alignItems: 'center', flexWrap: 'wrap', mb: 1 }}>
          <Typography variant="subtitle1" component="h2" sx={{ fontWeight: 700 }}>
            {t('skills.revisions.bodyTitle')}
          </Typography>
          {bodyChanged && (
            <>
              <Chip
                size="small"
                label={`+${data.added}`}
                aria-label={`${t('skills.revisions.added')} ${data.added}`}
                sx={{ bgcolor: 'success.light', color: 'success.main', fontWeight: 700, fontVariantNumeric: 'tabular-nums' }}
              />
              <Chip
                size="small"
                label={`−${data.removed}`}
                aria-label={`${t('skills.revisions.removed')} ${data.removed}`}
                sx={{ bgcolor: 'error.light', color: 'error.main', fontWeight: 700, fontVariantNumeric: 'tabular-nums' }}
              />
            </>
          )}
        </Stack>
        {data.coarse && (
          <Alert severity="info" sx={{ mb: 1 }}>
            {t('skills.revisions.coarse')}
          </Alert>
        )}
        {bodyChanged ? (
          <DiffView lines={data.bodyLines} label={t('skills.revisions.diffLabel')} />
        ) : (
          <Typography variant="body2" sx={{ color: 'text.secondary' }}>
            {t('skills.revisions.bodySame')}
          </Typography>
        )}
      </Box>
    </Stack>
  );
}

/** 바뀐 값 한 줄 — 지운 값(−, 빨강) · 넣은 값(+, 초록). 색만으로 전하지 않게 − · + 와 화면 읽기용 "이전 값 · 새 값"을 함께 적는다 */
function ValueLine({ op, text }: { op: 'add' | 'del'; text: string }) {
  const { t } = useTranslation('admin');
  const tone = op === 'add' ? 'success' : 'error';
  return (
    <Box sx={{ display: 'grid', gridTemplateColumns: '22px minmax(0, 1fr)', bgcolor: `${tone}.light`, py: 0.5, pr: 1.5 }}>
      <Box aria-hidden sx={{ textAlign: 'center', fontWeight: 700, color: `${tone}.main`, fontFamily: "'JetBrains Mono', monospace", userSelect: 'none' }}>
        {op === 'add' ? '+' : '−'}
      </Box>
      <Typography variant="body2" component="span" sx={{ overflowWrap: 'anywhere', whiteSpace: 'pre-wrap', color: text ? 'text.primary' : 'text.secondary' }}>
        <VisuallyHidden>{`${op === 'add' ? t('skills.revisions.after') : t('skills.revisions.before')}: `}</VisuallyHidden>
        {text || t('skills.revisions.emptyValue')}
      </Typography>
    </Box>
  );
}

/** 한 리비전의 스킬 전체 — 그 리비전의 머리 · 바꾼 까닭 · 이름 · 설명 · 본문(그린 것) */
function Snapshot({ revision }: { revision: SkillRevision }) {
  const { t } = useTranslation('admin');
  return (
    <Paper
      variant="outlined"
      component="section"
      aria-label={t('skills.revisions.snapshotLabel', { no: revision.no })}
      sx={{ borderRadius: 2.5, p: { xs: 2, sm: 3 } }}
    >
      <Box sx={{ mb: 2 }}>
        <RevisionMeta info={revision} />
        {revision.changeNote && (
          <Typography variant="body2" sx={{ mt: 0.5, color: 'text.secondary', overflowWrap: 'anywhere' }}>
            {t('skills.revisions.note', { note: revision.changeNote })}
          </Typography>
        )}
      </Box>
      <Typography variant="h5" component="h2" sx={{ fontFamily: 'monospace', overflowWrap: 'anywhere', mb: 1 }}>
        {revision.name}
      </Typography>
      <Typography variant="overline" sx={{ color: 'text.secondary', display: 'block', lineHeight: 1.6 }}>
        {t('skills.form.descriptionLabel')}
      </Typography>
      <Typography variant="body2" sx={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere', mb: 2.5 }}>
        {revision.description}
      </Typography>
      <Box sx={{ maxWidth: 920 }}>
        <MarkdownView content={revision.body} />
      </Box>
    </Paper>
  );
}

/**
 * 이 리비전으로 되돌리기(restore) — 확인 대화 상자(바꾼 까닭을 받을 수 있다). 옛 리비전을 지우지 않고 그 내용으로 새 리비전을 만든다.
 * 지금 내용과 같으면 서버가 리비전 없이 그대로 돌려준다. 그 사이 누가 고쳤으면(409 SKILL_CHANGED) 스킬을 다시 읽고 다시 해 보라고 알린다.
 */
function RestoreDialog({ skill, no, onClose, onDone }: {
  skill: SkillDetail;
  no: number;
  onClose: () => void;
  onDone: (next: number) => void;
}) {
  const { t } = useTranslation(['admin', 'common']);
  const queryClient = useQueryClient();
  const [note, setNote] = useState('');
  const [busy, setBusy] = useState(false);

  const submit = async () => {
    setBusy(true);
    try {
      const saved = await skillApi.restoreRevision(skill.name, no, skill.revisionNo, note.trim() || undefined);
      storeSkill(queryClient, saved);
      if (saved.revisionNo === skill.revisionNo) {
        toast.info(t('skills.restoreRevision.unchanged'));
      } else {
        toast.success(t('skills.restoreRevision.done', { from: no, no: saved.revisionNo }));
      }
      onClose();
      onDone(saved.revisionNo);
    } catch (e) {
      setBusy(false);
      if (isApiError(e, 'SKILL_CHANGED')) {
        void queryClient.invalidateQueries({ queryKey: skillKeys.all });
        toast.error(t('skills.restoreRevision.conflict'));
        onClose();
        return;
      }
      toast.error(errorMessage(e));
    }
  };

  return (
    <Dialog open onClose={busy ? undefined : onClose} maxWidth="xs" fullWidth>
      <DialogTitle sx={{ fontWeight: 700 }}>{t('skills.restoreRevision.title', { no })}</DialogTitle>
      <DialogContent>
        <DialogContentText sx={{ mb: 2 }}>{t('skills.restoreRevision.body', { no, next: skill.revisionNo + 1, name: skill.name })}</DialogContentText>
        <TextField
          fullWidth
          label={t('skills.form.noteLabel')}
          value={note}
          onChange={(event) => setNote(event.target.value)}
          placeholder={t('skills.restoreRevision.notePlaceholder', { no })}
          slotProps={{ htmlInput: { maxLength: SKILL_NOTE_MAX } }}
        />
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2.5 }}>
        <Button onClick={onClose} color="inherit" disabled={busy}>
          {t('common:actions.cancel')}
        </Button>
        <Button variant="contained" color="warning" onClick={() => void submit()} loading={busy} autoFocus>
          {t('skills.restoreRevision.confirm')}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
