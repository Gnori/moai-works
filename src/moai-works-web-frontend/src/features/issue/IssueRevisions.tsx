import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Chip from '@mui/material/Chip';
import Link from '@mui/material/Link';
import MenuItem from '@mui/material/MenuItem';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import Tab from '@mui/material/Tab';
import Tabs from '@mui/material/Tabs';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useCallback, type ReactNode, type SyntheticEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { Link as RouterLink, useSearchParams } from 'react-router';

import { issuePath } from '@/app/routes';
import type { IssueChange, IssueDetail, IssueRevision, IssueRevisionDiff } from '@/shared/api/types';
import { date } from '@/shared/lib/format';
import { DiffView } from '@/shared/ui/revisions/DiffView';
import { RevisionCard, RevisionMeta, RevisionSplit, RevisionTimeline, revisionChipSx, useRevisionReveal } from '@/shared/ui/revisions/RevisionTimeline';
import { compareCandidates, readRevisionSelection, writeRevisionSelection, type RevisionSelection, type RevisionView } from '@/shared/ui/revisions/revision-view';
import { EmptyState, ErrorState, LoadingState } from '@/shared/ui/States';
import { VisuallyHidden } from '@/shared/ui/VisuallyHidden';

import { useFieldName, useValueText } from './change-text';
import { personName, useIssueTypeName } from './issue-labels';
import { IssuePriorityChip } from './IssuePriorityChip';
import { IssueStatusChip } from './IssueStatusChip';
import { readChange, type ChangeValue } from './journal';
import { MarkdownView } from './MarkdownView';
import { useIssueDiffQuery, useIssueRevisionQuery, useIssueRevisionsQuery } from './queries';
import { formatHours } from './work-hours';

/**
 * 일감 보기의 "리비전" 탭 — 리비전 타임라인(새 리비전부터, 더 보기), 고른 리비전과 다른 리비전의 차이(필드 전 · 후 + 설명의 git 같은 줄 차이), 그 리비전 전체
 * (2026-11-23 사용자: "리비전을 첨부파일 옆에 탭으로 해서 추가하고 타임라인으로 볼 수 있게 해줘").
 * moai-wiki 의 페이지 이력(PageHistoryPage) 자리다 — 예전 따로 화면(issues/:number/history)은 이 탭(?tab=revisions)으로 돌린다.
 * 고른 리비전 · 견줄 리비전 · 보기는 주소에 남긴다(?tab=revisions&rev=5&from=2&view=snapshot).
 * 리비전은 독자면 누구나 읽는다. 바뀐 때는 밀리초까지 보인다.
 * 탭이 받은 너비가 넓으면 타임라인 왼쪽 · 차이 오른쪽, 좁으면(390px) 타임라인 위 · 차이 아래이고 리비전을 고르면 그 차이로 내려 준다.
 * 탭은 늘 그려 두되, 서버에서 읽는 것은 탭이 보일 때만(active) — 다른 탭을 보는 동안 리비전 목록 · 차이를 읽지 않는다.
 * 타임라인 · 배치 · 리비전 머리 · 줄 차이는 스킬 리비전과 함께 쓰는 shared/ui/revisions 의 것이다.
 */
export function IssueRevisions({ slug, issue, active }: { slug: string; issue: IssueDetail; active: boolean }) {
  const { t } = useTranslation('issue');
  const [params, setParams] = useSearchParams();
  const { timelineRef, panelRef, reveal } = useRevisionReveal();
  const fieldName = useFieldName();
  const list = useIssueRevisionsQuery(slug, issue.number, active);
  const items = list.data?.pages.flatMap((page) => page.items) ?? [];
  /** 지금 리비전 — 리비전은 1부터 빠짐없이 쌓인다. 목록이 더 새로우면 목록을 믿는다 */
  const latest = Math.max(issue.revisionNo, items[0]?.no ?? 0);
  const selection = readRevisionSelection(params, latest);

  const choose = (next: RevisionSelection, scroll = false) => {
    // 탭(?tab=revisions) · 다른 주소 값은 그대로 두고 고른 것만 바꾼다
    setParams(writeRevisionSelection(params, next), { replace: true });
    // 좁은 화면(차이가 타임라인 아래)에서 리비전을 고르면 그 차이로 내려 준다
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
    return <EmptyState title={t('revisions.emptyTitle')} description={t('revisions.emptyBody')} />;
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
          tags={(item) =>
            item.no === 1 ? (
              <Chip size="small" variant="outlined" color="success" label={t('revisions.created')} sx={revisionChipSx} />
            ) : (
              item.changedFields.map((field) => <Chip key={field} size="small" variant="outlined" label={fieldName(field)} sx={revisionChipSx} />)
            )
          }
        />
      }
    >
      <RevisionPanel
        key={`${selection.rev}:${selection.from ?? ''}`}
        slug={slug}
        issue={issue}
        latest={latest}
        selection={selection}
        active={active}
        onChange={(next) => choose(next)}
      />
    </RevisionSplit>
  );
}

/** 고른 리비전 — 다른 리비전과의 차이(기본) 또는 그 리비전 전체. 견줄 리비전은 아무 리비전이나(바로 앞 리비전이 기본, 0 = 빈 일감) */
function RevisionPanel({ slug, issue, latest, selection, active, onChange }: {
  slug: string;
  issue: IssueDetail;
  latest: number;
  selection: RevisionSelection;
  /** 탭이 보일 때만 읽는다 — 이미 읽은 것은 탭을 옮겨도 그대로 보인다 */
  active: boolean;
  onChange: (next: RevisionSelection) => void;
}) {
  const { t } = useTranslation('issue');
  const { rev, from, view } = selection;
  const diff = useIssueDiffQuery(slug, issue.number, rev, from, active && view === 'diff');
  const snapshot = useIssueRevisionQuery(slug, issue.number, rev, active && view === 'snapshot');
  const base = from ?? rev - 1;
  const candidates = compareCandidates(latest, rev);

  return (
    <Box sx={{ minWidth: 0 }}>
      <Stack
        direction={{ xs: 'column', sm: 'row' }}
        spacing={1.5}
        sx={{ alignItems: { xs: 'stretch', sm: 'center' }, justifyContent: 'space-between', mb: 1.5 }}
      >
        <Tabs
          value={view}
          onChange={(_: SyntheticEvent, next: RevisionView) => onChange({ ...selection, view: next })}
          aria-label={t('revisions.viewLabel')}
          variant="scrollable"
          scrollButtons={false}
          sx={{ minHeight: 40 }}
        >
          <Tab value="diff" label={t('revisions.tabDiff')} sx={{ minHeight: 40 }} />
          <Tab value="snapshot" label={t('revisions.tabSnapshot')} sx={{ minHeight: 40 }} />
        </Tabs>
        {view === 'diff' && latest > 1 && (
          <TextField
            select
            size="small"
            label={t('revisions.compareWith')}
            value={base}
            onChange={(event) => onChange({ ...selection, from: Number(event.target.value) })}
            sx={{ minWidth: 220 }}
          >
            {candidates.map((n) => (
              <MenuItem key={n} value={n}>
                {n === 0
                  ? t('revisions.nothingOption')
                  : n === rev - 1
                    ? t('revisions.previousOption', { no: n })
                    : t('revisions.revisionNo', { no: n })}
              </MenuItem>
            ))}
          </TextField>
        )}
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
        <Snapshot slug={slug} issueNumber={issue.number} revision={snapshot.data} />
      )}
    </Box>
  );
}

/** 두 리비전의 차이 — 누가 언제 남긴 리비전끼리인지, 바뀐 필드(전 → 후), 설명의 줄 차이 */
function DiffBody({ data }: { data: IssueRevisionDiff }) {
  const { t } = useTranslation('issue');
  const fields = data.fields.length;
  const summary =
    data.from === null
      ? data.to === 1
        ? t('revisions.diffFirst', { to: data.to, fields, added: data.added })
        : t('revisions.diffFromNothing', { to: data.to, fields, added: data.added })
      : t('revisions.diffSummary', { from: data.from, to: data.to, fields, added: data.added, removed: data.removed });
  const descriptionChanged = data.added + data.removed > 0;

  return (
    <Stack spacing={2}>
      <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1, '& > *': { flex: '1 1 220px', minWidth: 0 } }}>
        <RevisionCard label={t('revisions.fromLabel')} info={data.fromInfo} empty={t('revisions.emptyIssue')} />
        <RevisionCard label={t('revisions.toLabel')} info={data.toInfo} empty={t('revisions.emptyIssue')} />
      </Box>
      <Typography variant="body2" sx={{ color: 'text.secondary' }}>
        {summary}
      </Typography>
      {data.from !== null && data.from > data.to && (
        <Alert severity="info">{t('revisions.reversed', { from: data.from, to: data.to })}</Alert>
      )}

      <Box component="section">
        <Typography variant="subtitle1" component="h2" sx={{ fontWeight: 700, mb: 1 }}>
          {t('revisions.fieldsTitle')}
        </Typography>
        {fields === 0 ? (
          <Typography variant="body2" sx={{ color: 'text.secondary' }}>
            {t('revisions.fieldsEmpty')}
          </Typography>
        ) : (
          <FieldChanges changes={data.fields} showBefore={data.from !== null} />
        )}
      </Box>

      <Box component="section">
        <Stack direction="row" spacing={1} useFlexGap sx={{ alignItems: 'center', flexWrap: 'wrap', mb: 1 }}>
          <Typography variant="subtitle1" component="h2" sx={{ fontWeight: 700 }}>
            {t('revisions.descriptionTitle')}
          </Typography>
          {descriptionChanged && (
            <>
              <Chip
                size="small"
                label={`+${data.added}`}
                aria-label={`${t('revisions.added')} ${data.added}`}
                sx={{ bgcolor: 'success.light', color: 'success.main', fontWeight: 700, fontVariantNumeric: 'tabular-nums' }}
              />
              <Chip
                size="small"
                label={`−${data.removed}`}
                aria-label={`${t('revisions.removed')} ${data.removed}`}
                sx={{ bgcolor: 'error.light', color: 'error.main', fontWeight: 700, fontVariantNumeric: 'tabular-nums' }}
              />
            </>
          )}
        </Stack>
        {data.coarse && (
          <Alert severity="info" sx={{ mb: 1 }}>
            {t('revisions.coarse')}
          </Alert>
        )}
        {descriptionChanged ? (
          <DiffView lines={data.descriptionLines} label={t('revisions.diffLabel')} />
        ) : (
          <Typography variant="body2" sx={{ color: 'text.secondary' }}>
            {data.descriptionLines.length === 0 ? t('revisions.descriptionEmpty') : t('revisions.descriptionSame')}
          </Typography>
        )}
      </Box>
    </Stack>
  );
}

/**
 * 바뀐 필드 — 필드마다 지운 값(−, 빨강)과 넣은 값(+, 초록). 색만으로 전하지 않게 − · + 와 화면 읽기용 "이전 값 · 새 값"을 함께 적는다.
 * 사람은 이름, 상태 · 유형은 번역, 날짜는 날짜만, 예상 공수는 "8시간".
 */
function FieldChanges({ changes, showBefore }: { changes: IssueChange[]; showBefore: boolean }) {
  const fieldName = useFieldName();
  const valueText = useValueText();
  return (
    <Paper variant="outlined" sx={{ borderRadius: 2.5, overflow: 'hidden' }}>
      <Box component="dl" sx={{ m: 0 }}>
        {changes.map((change, index) => {
          const readable = readChange(change);
          const pair: { before: ChangeValue; after: ChangeValue } | null =
            readable.kind === 'field' || readable.kind === 'unknown' ? { before: readable.before, after: readable.after } : null;
          return (
            <Box
              key={`${change.field}:${index}`}
              sx={{
                display: 'grid',
                gridTemplateColumns: { xs: 'minmax(0, 1fr)', sm: '140px minmax(0, 1fr)' },
                borderTop: index > 0 ? 1 : 0,
                borderColor: 'divider',
              }}
            >
              <Typography component="dt" variant="body2" sx={{ px: 1.5, py: 0.75, fontWeight: 600, bgcolor: 'background.subtle' }}>
                {fieldName(change.field)}
              </Typography>
              <Box component="dd" sx={{ m: 0, minWidth: 0 }}>
                {pair ? (
                  <>
                    {showBefore && <ValueLine op="del" value={pair.before} text={valueText(pair.before)} />}
                    <ValueLine op="add" value={pair.after} text={valueText(pair.after)} />
                  </>
                ) : (
                  <Typography variant="body2" sx={{ px: 1.5, py: 0.75 }}>
                    {String(change.after ?? change.before ?? '')}
                  </Typography>
                )}
              </Box>
            </Box>
          );
        })}
      </Box>
    </Paper>
  );
}

function ValueLine({ op, value, text }: { op: 'add' | 'del'; value: ChangeValue; text: string }) {
  const { t } = useTranslation('issue');
  const tone = op === 'add' ? 'success' : 'error';
  return (
    <Box sx={{ display: 'grid', gridTemplateColumns: '22px minmax(0, 1fr)', bgcolor: `${tone}.light`, py: 0.5, pr: 1.5 }}>
      <Box aria-hidden sx={{ textAlign: 'center', fontWeight: 700, color: `${tone}.main`, fontFamily: "'JetBrains Mono', monospace", userSelect: 'none' }}>
        {op === 'add' ? '+' : '−'}
      </Box>
      <Typography
        variant="body2"
        component="span"
        sx={{ overflowWrap: 'anywhere', whiteSpace: 'pre-wrap', color: value.kind === 'empty' ? 'text.secondary' : 'text.primary' }}
      >
        <VisuallyHidden>{`${op === 'add' ? t('revisions.after') : t('revisions.before')}: `}</VisuallyHidden>
        {text}
      </Typography>
    </Box>
  );
}

/** 한 리비전의 일감 전체 — 그 리비전의 속성과 설명(Markdown) */
function Snapshot({ slug, issueNumber, revision }: { slug: string; issueNumber: number; revision: IssueRevision }) {
  const { t } = useTranslation('issue');
  const typeName = useIssueTypeName();
  const toIssue = useCallback((n: number) => issuePath(slug, n), [slug]);
  const nobody = <Muted>{t('form.nobody')}</Muted>;
  const day = (value: string | null) => (value ? date(value) : <Muted>—</Muted>);
  return (
    <Paper
      variant="outlined"
      component="section"
      aria-label={t('revisions.snapshotLabel', { no: revision.no })}
      sx={{ borderRadius: 2.5, p: { xs: 2, sm: 3 } }}
    >
      <Box sx={{ mb: 2 }}>
        <RevisionMeta info={revision} />
      </Box>
      <Stack direction="row" spacing={1} useFlexGap sx={{ alignItems: 'center', flexWrap: 'wrap', mb: 0.75 }}>
        <Typography variant="subtitle2" sx={{ fontFamily: 'monospace', color: 'text.secondary' }}>
          #{issueNumber}
        </Typography>
        <Chip size="small" variant="outlined" label={typeName(revision.type)} />
        <IssueStatusChip status={revision.status} />
      </Stack>
      <Typography variant="h5" component="h2" sx={{ overflowWrap: 'anywhere', mb: 2 }}>
        {revision.title}
      </Typography>
      <Box
        component="dl"
        sx={{ m: 0, mb: 2.5, display: 'grid', gridTemplateColumns: 'auto minmax(0, 1fr)', columnGap: 2, rowGap: 0.75, maxWidth: 560 }}
      >
        {/* 그때의 프로젝트 — 일감을 옮기기 전의 리비전이면 옮기기 전 프로젝트(2026-11-24) */}
        {revision.project && (
          <SnapshotField label={t('fields.project')}>
            {revision.project.name}
            {revision.project.key !== revision.project.name && (
              <Box component="span" sx={{ ml: 0.75, color: 'text.secondary', fontFamily: 'monospace', fontSize: 12 }}>
                {revision.project.key}
              </Box>
            )}
          </SnapshotField>
        )}
        <SnapshotField label={t('fields.priority')}>
          <IssuePriorityChip priority={revision.priority} normal />
        </SnapshotField>
        <SnapshotField label={t('fields.manager')}>{personName(revision.manager) ?? nobody}</SnapshotField>
        <SnapshotField label={t('fields.assignee')}>{personName(revision.assignee) ?? nobody}</SnapshotField>
        <SnapshotField label={t('fields.parent')}>
          {revision.parent !== null ? (
            <Link component={RouterLink} to={toIssue(revision.parent)} underline="hover">
              #{revision.parent}
            </Link>
          ) : (
            nobody
          )}
        </SnapshotField>
        <SnapshotField label={t('fields.estimatedHours')}>
          {revision.estimatedHours !== null ? t('hours.value', { hours: formatHours(revision.estimatedHours) }) : <Muted>—</Muted>}
        </SnapshotField>
        <SnapshotField label={t('fields.dueDate')}>{day(revision.dueDate)}</SnapshotField>
        <SnapshotField label={t('fields.startDate')}>{day(revision.startDate)}</SnapshotField>
        <SnapshotField label={t('fields.plannedEndDate')}>{day(revision.plannedEndDate)}</SnapshotField>
        <SnapshotField label={t('fields.completedOn')}>{day(revision.completedOn)}</SnapshotField>
        <SnapshotField label={t('fields.closedOn')}>{day(revision.closedOn)}</SnapshotField>
      </Box>
      <Typography variant="subtitle1" component="h3" sx={{ fontWeight: 700, mb: 1 }}>
        {t('fields.description')}
      </Typography>
      <Box sx={{ maxWidth: 920 }}>
        {revision.description.trim() ? (
          <MarkdownView content={revision.description} issuePath={toIssue} />
        ) : (
          <Typography variant="body2" sx={{ color: 'text.secondary' }}>
            {t('view.emptyDescription')}
          </Typography>
        )}
      </Box>
    </Paper>
  );
}

function SnapshotField({ label, children }: { label: string; children: ReactNode }) {
  return (
    <>
      <Typography component="dt" variant="body2" sx={{ color: 'text.secondary', whiteSpace: 'nowrap' }}>
        {label}
      </Typography>
      <Typography component="dd" variant="body2" sx={{ m: 0, minWidth: 0, overflowWrap: 'anywhere', fontVariantNumeric: 'tabular-nums' }}>
        {children}
      </Typography>
    </>
  );
}

function Muted({ children }: { children: ReactNode }) {
  return (
    <Box component="span" sx={{ color: 'text.secondary' }}>
      {children}
    </Box>
  );
}
