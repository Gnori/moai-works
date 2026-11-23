import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Chip from '@mui/material/Chip';
import Link from '@mui/material/Link';
import ListItemButton from '@mui/material/ListItemButton';
import MenuItem from '@mui/material/MenuItem';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import Tab from '@mui/material/Tab';
import Tabs from '@mui/material/Tabs';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useCallback, useRef, type ReactNode, type SyntheticEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { Link as RouterLink, useSearchParams } from 'react-router';

import { issuePath } from '@/app/routes';
import type { IssueChange, IssueDetail, IssueRevision, IssueRevisionDiff, IssueRevisionItem } from '@/shared/api/types';
import { date, dateTimeMs, fromNow } from '@/shared/lib/format';
import { EmptyState, ErrorState, LoadingState } from '@/shared/ui/States';
import { VisuallyHidden } from '@/shared/ui/VisuallyHidden';
import { layout } from '@/theme/tokens';

import { useFieldName, useValueText } from './change-text';
import { DiffView } from './DiffView';
import { personName, useIssueTypeName } from './issue-labels';
import { IssueStatusChip } from './IssueStatusChip';
import { readChange, type ChangeValue } from './journal';
import { MarkdownView } from './MarkdownView';
import { useIssueDiffQuery, useIssueRevisionQuery, useIssueRevisionsQuery } from './queries';
import { ViaChip } from './ViaChip';
import { compareCandidates, readRevisionSelection, writeRevisionSelection, type RevisionSelection, type RevisionView } from './revision-view';
import { formatHours } from './work-hours';

/** 이 너비(리비전 탭이 받은 너비)부터 타임라인과 차이를 나란히 둔다 — 주 메뉴를 접고 펴도 맞게 창이 아니라 탭 너비로 */
const SIDE_BY_SIDE = '@container (min-width: 760px)';
/** 리비전 탭의 최대 폭 — 일감 보기의 속성 · 설명 · 댓글과 같은 1200px */
const MAX_WIDTH = 1200;
/** 화면 위에 붙는 것(좁은 화면의 위 막대) 아래로 띄우는 거리 — 고른 차이로 내려 줄 때 · 타임라인을 붙여 둘 때 */
const STICK_TOP = { xs: `${layout.topbarHeight + 16}px`, md: '32px' } as const;

/**
 * 일감 보기의 "리비전" 탭 — 리비전 타임라인(새 리비전부터, 더 보기), 고른 리비전과 다른 리비전의 차이(필드 전 · 후 + 설명의 git 같은 줄 차이), 그 리비전 전체
 * (2026-11-23 사용자: "리비전을 첨부파일 옆에 탭으로 해서 추가하고 타임라인으로 볼 수 있게 해줘").
 * moai-wiki 의 페이지 이력(PageHistoryPage) 자리다 — 예전 따로 화면(issues/:number/history)은 이 탭(?tab=revisions)으로 돌린다.
 * 고른 리비전 · 견줄 리비전 · 보기는 주소에 남긴다(?tab=revisions&rev=5&from=2&view=snapshot).
 * 리비전은 독자면 누구나 읽는다. 바뀐 때는 밀리초까지 보인다.
 * 탭이 받은 너비가 넓으면 타임라인 왼쪽 · 차이 오른쪽, 좁으면(390px) 타임라인 위 · 차이 아래이고 리비전을 고르면 그 차이로 내려 준다.
 * 탭은 늘 그려 두되, 서버에서 읽는 것은 탭이 보일 때만(active) — 다른 탭을 보는 동안 리비전 목록 · 차이를 읽지 않는다.
 */
export function IssueRevisions({ slug, issue, active }: { slug: string; issue: IssueDetail; active: boolean }) {
  const { t } = useTranslation('issue');
  const [params, setParams] = useSearchParams();
  const timelineRef = useRef<HTMLDivElement>(null);
  const panelRef = useRef<HTMLDivElement>(null);
  const list = useIssueRevisionsQuery(slug, issue.number, active);
  const items = list.data?.pages.flatMap((page) => page.items) ?? [];
  /** 지금 리비전 — 리비전은 1부터 빠짐없이 쌓인다. 목록이 더 새로우면 목록을 믿는다 */
  const latest = Math.max(issue.revisionNo, items[0]?.no ?? 0);
  const selection = readRevisionSelection(params, latest);

  const choose = (next: RevisionSelection, reveal = false) => {
    // 탭(?tab=revisions) · 다른 주소 값은 그대로 두고 고른 것만 바꾼다
    setParams(writeRevisionSelection(params, next), { replace: true });
    const panel = panelRef.current;
    const timeline = timelineRef.current;
    // 좁은 화면(차이가 타임라인 아래)에서 리비전을 고르면 그 차이로 내려 준다
    if (reveal && panel && timeline && panel.getBoundingClientRect().top >= timeline.getBoundingClientRect().bottom - 1) {
      panel.scrollIntoView({ behavior: 'smooth', block: 'start' });
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
    <Box sx={{ containerType: 'inline-size', maxWidth: MAX_WIDTH }}>
      <Box
        sx={{
          display: 'grid',
          gap: 3,
          alignItems: 'start',
          gridTemplateColumns: 'minmax(0, 1fr)',
          [SIDE_BY_SIDE]: { gridTemplateColumns: '300px minmax(0, 1fr)' },
        }}
      >
        <Box ref={timelineRef} sx={{ minWidth: 0, top: STICK_TOP, [SIDE_BY_SIDE]: { position: 'sticky' } }}>
          <Timeline
            items={items}
            selected={selection.rev}
            onSelect={(no) => choose({ rev: no, from: undefined, view: selection.view }, true)}
            hasMore={list.hasNextPage}
            loadingMore={list.isFetchingNextPage}
            onMore={() => void list.fetchNextPage()}
          />
        </Box>
        <Box ref={panelRef} sx={{ minWidth: 0, scrollMarginTop: STICK_TOP }}>
          <RevisionPanel
            key={`${selection.rev}:${selection.from ?? ''}`}
            slug={slug}
            issue={issue}
            latest={latest}
            selection={selection}
            active={active}
            onChange={(next) => choose(next)}
          />
        </Box>
      </Box>
    </Box>
  );
}

/** 리비전 타임라인 — 새 리비전부터. 리비전 번호 · 누가 · 언제(밀리초까지) · 어느 길로(MCP) · 바꾼 필드 · 지금 리비전 */
function Timeline({ items, selected, onSelect, hasMore, loadingMore, onMore }: {
  items: IssueRevisionItem[];
  selected: number;
  onSelect: (no: number) => void;
  hasMore: boolean;
  loadingMore: boolean;
  onMore: () => void;
}) {
  const { t } = useTranslation('issue');
  return (
    <Paper
      variant="outlined"
      sx={{
        borderRadius: 2.5,
        overflowY: 'auto',
        maxHeight: 360,
        [SIDE_BY_SIDE]: { maxHeight: 'calc(100dvh - 96px)' },
      }}
    >
      <Box component="ol" aria-label={t('revisions.listLabel')} sx={{ listStyle: 'none', m: 0, p: 0, py: 0.5 }}>
        {items.map((item, index) => (
          <TimelineItem
            key={item.no}
            item={item}
            selected={item.no === selected}
            first={index === 0}
            last={index === items.length - 1 && !hasMore}
            onSelect={() => onSelect(item.no)}
          />
        ))}
      </Box>
      {hasMore && (
        <Box sx={{ p: 1, textAlign: 'center', borderTop: 1, borderColor: 'divider' }}>
          <Button size="small" color="inherit" onClick={onMore} loading={loadingMore}>
            {t('revisions.more')}
          </Button>
        </Box>
      )}
    </Paper>
  );
}

function TimelineItem({ item, selected, first, last, onSelect }: {
  item: IssueRevisionItem;
  selected: boolean;
  first: boolean;
  last: boolean;
  onSelect: () => void;
}) {
  const { t } = useTranslation('issue');
  const fieldName = useFieldName();
  const name = actorName(item, t('journal.service'), t('view.unknownPerson'));
  const chipSx = { height: 20, fontSize: 11 } as const;
  return (
    <Box component="li">
      <ListItemButton
        selected={selected}
        aria-current={selected ? 'true' : undefined}
        onClick={onSelect}
        sx={{ alignItems: 'stretch', gap: 1.25, py: 1.25, pl: 1.5, pr: 1.5 }}
      >
        {/* 타임라인의 줄과 점 — 지금 리비전은 채운 점, 고른 리비전은 주 색 테두리(뜻은 옆의 글 "지금 리비전" · 선택 표시가 전한다) */}
        <Box aria-hidden sx={{ position: 'relative', width: 14, flexShrink: 0 }}>
          <Box
            sx={{
              position: 'absolute',
              left: 6,
              width: 2,
              top: first ? 12 : -10,
              bottom: last ? 'calc(100% - 12px)' : -10,
              bgcolor: 'divider',
            }}
          />
          <Box
            sx={{
              position: 'absolute',
              left: 1,
              top: 6,
              width: 12,
              height: 12,
              borderRadius: '50%',
              border: 2,
              borderColor: selected || item.current ? 'primary.main' : 'text.disabled',
              bgcolor: item.current ? 'primary.main' : 'background.paper',
            }}
          />
        </Box>
        <Box sx={{ minWidth: 0, flex: 1 }}>
          <Stack direction="row" spacing={0.75} useFlexGap sx={{ alignItems: 'center', flexWrap: 'wrap' }}>
            <Typography variant="subtitle2" sx={{ fontVariantNumeric: 'tabular-nums' }}>
              {t('revisions.revisionNo', { no: item.no })}
            </Typography>
            {item.current && <Chip size="small" color="primary" label={t('revisions.current')} sx={chipSx} />}
            <ViaChip via={item.via} compact />
            {item.actorType === 'SERVICE' && <Chip size="small" variant="outlined" label={t('journal.service')} sx={chipSx} />}
          </Stack>
          <Typography variant="body2" sx={{ mt: 0.25, overflowWrap: 'anywhere' }}>
            {name}
          </Typography>
          <Typography
            variant="caption"
            component="time"
            dateTime={item.createdAt}
            sx={{ display: 'block', color: 'text.secondary', fontVariantNumeric: 'tabular-nums' }}
          >
            {dateTimeMs(item.createdAt)} · {fromNow(item.createdAt)}
          </Typography>
          <Stack
            direction="row"
            spacing={0.5}
            useFlexGap
            aria-label={t('revisions.changedFields')}
            sx={{ flexWrap: 'wrap', mt: 0.75 }}
          >
            {item.no === 1 ? (
              <Chip size="small" variant="outlined" color="success" label={t('revisions.created')} sx={chipSx} />
            ) : (
              item.changedFields.map((field) => <Chip key={field} size="small" variant="outlined" label={fieldName(field)} sx={chipSx} />)
            )}
          </Stack>
        </Box>
      </ListItemButton>
    </Box>
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
        <RevisionCard label={t('revisions.fromLabel')} info={data.fromInfo} />
        <RevisionCard label={t('revisions.toLabel')} info={data.toInfo} />
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
          <DiffView lines={data.descriptionLines} />
        ) : (
          <Typography variant="body2" sx={{ color: 'text.secondary' }}>
            {data.descriptionLines.length === 0 ? t('revisions.descriptionEmpty') : t('revisions.descriptionSame')}
          </Typography>
        )}
      </Box>
    </Stack>
  );
}

/** 견주는 리비전 하나 — 리비전 번호 · 누가 · 언제(밀리초까지) · MCP · 지금 리비전. info 가 없으면 빈 일감 */
function RevisionCard({ label, info }: { label: string; info: IssueRevisionItem | null }) {
  const { t } = useTranslation('issue');
  return (
    <Paper variant="outlined" sx={{ borderRadius: 2, px: 1.5, py: 1 }}>
      <Typography variant="overline" sx={{ color: 'text.secondary', lineHeight: 1.6, display: 'block' }}>
        {label}
      </Typography>
      {info ? <RevisionMeta info={info} /> : <Typography variant="subtitle2">{t('revisions.emptyIssue')}</Typography>}
    </Paper>
  );
}

function RevisionMeta({ info }: { info: Pick<IssueRevisionItem, 'no' | 'actorType' | 'actorName' | 'via' | 'createdAt' | 'current'> }) {
  const { t } = useTranslation('issue');
  const chipSx = { height: 20, fontSize: 11 } as const;
  return (
    <>
      <Stack direction="row" spacing={0.75} useFlexGap sx={{ alignItems: 'center', flexWrap: 'wrap' }}>
        <Typography variant="subtitle2" sx={{ fontVariantNumeric: 'tabular-nums' }}>
          {t('revisions.revisionNo', { no: info.no })}
        </Typography>
        {info.current && <Chip size="small" color="primary" label={t('revisions.current')} sx={chipSx} />}
        <ViaChip via={info.via} compact />
      </Stack>
      <Typography variant="body2" sx={{ overflowWrap: 'anywhere' }}>
        {actorName(info, t('journal.service'), t('view.unknownPerson'))}
      </Typography>
      <Typography variant="caption" component="time" dateTime={info.createdAt} sx={{ display: 'block', color: 'text.secondary', fontVariantNumeric: 'tabular-nums' }}>
        {dateTimeMs(info.createdAt)} · {fromNow(info.createdAt)}
      </Typography>
    </>
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
            <Box component="span" sx={{ ml: 0.75, color: 'text.secondary', fontFamily: 'monospace', fontSize: 12 }}>
              {revision.project.key}
            </Box>
          </SnapshotField>
        )}
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

/** 리비전을 남긴 사람 이름 — 없으면 서비스 · 알 수 없음 */
function actorName(info: Pick<IssueRevisionItem, 'actorType' | 'actorName'>, service: string, unknown: string): string {
  return info.actorName || (info.actorType === 'SERVICE' ? service : unknown);
}
