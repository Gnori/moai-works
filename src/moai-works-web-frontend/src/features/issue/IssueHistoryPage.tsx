import ArrowBackRounded from '@mui/icons-material/ArrowBackRounded';
import SearchOffRounded from '@mui/icons-material/SearchOffRounded';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Breadcrumbs from '@mui/material/Breadcrumbs';
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
import { Link as RouterLink, useParams, useSearchParams } from 'react-router';

import { issuePath, projectPath } from '@/app/routes';
import { useProject } from '@/features/project/project-context';
import { useWorkspace } from '@/features/workspace/workspace-context';
import { isApiError } from '@/shared/api/http';
import type { IssueChange, IssueDetail, IssueRevision, IssueRevisionDiff, IssueRevisionItem } from '@/shared/api/types';
import { date, dateTimeMs, fromNow } from '@/shared/lib/format';
import { EmptyState, ErrorState, LoadingState } from '@/shared/ui/States';
import { VisuallyHidden } from '@/shared/ui/VisuallyHidden';

import { IssueAttachmentsProvider } from './attachment-context';
import { useFieldName, useValueText } from './change-text';
import { DiffView } from './DiffView';
import { personName, useIssueTypeName } from './issue-labels';
import { IssueStatusChip } from './IssueStatusChip';
import { readChange, type ChangeValue } from './journal';
import { MarkdownView } from './MarkdownView';
import { parseIssueNumber, useIssueDiffQuery, useIssueQuery, useIssueRevisionQuery, useIssueRevisionsQuery } from './queries';
import { compareCandidates, readRevisionSelection, writeRevisionSelection, type RevisionSelection, type RevisionView } from './revision-view';
import { formatHours } from './work-hours';

/** 이 너비(리비전 화면 본문 기준)부터 타임라인과 차이를 나란히 둔다 — 곁 메뉴가 붙어 본문이 좁아져도 맞게 화면이 아니라 본문 너비로 */
const SIDE_BY_SIDE = '@container (min-width: 760px)';

/**
 * 일감의 리비전 · 차이 — 판 타임라인(새 판부터, 더 보기), 고른 판과 다른 판의 차이(필드 전 · 후 + 설명의 git 같은 줄 차이), 그 판 전체.
 * moai-wiki 의 페이지 이력(PageHistoryPage) 자리다. 고른 판 · 견줄 판 · 보기는 주소에 남긴다(?rev=5&from=2&view=snapshot).
 * 리비전은 독자면 누구나 읽는다. 바뀐 때는 밀리초까지 보인다.
 * 넓으면 타임라인 왼쪽 · 차이 오른쪽, 좁으면(390px) 타임라인 위 · 차이 아래.
 */
export function IssueHistoryPage() {
  const { t } = useTranslation('issue');
  const { workspace } = useWorkspace();
  const project = useProject();
  const number = parseIssueNumber(useParams().number);
  const query = useIssueQuery(workspace.slug, project.key, number);

  if (number === undefined || (query.isError && isApiError(query.error) && query.error.status === 404)) {
    return (
      <EmptyState
        icon={<SearchOffRounded />}
        title={t('view.notFoundTitle')}
        description={t('view.notFoundBody')}
        action={
          <Button component={RouterLink} to={projectPath(workspace.slug, project.key)} variant="outlined" startIcon={<ArrowBackRounded />}>
            {t('view.backToProject', { name: project.name })}
          </Button>
        }
      />
    );
  }
  if (query.isPending) {
    return <LoadingState />;
  }
  if (query.isError) {
    return <ErrorState error={query.error} onRetry={() => query.refetch()} />;
  }
  return (
    // 판 전체 보기의 설명 속 첨부 이미지 · 링크도 이 일감의 첨부로 그린다
    <IssueAttachmentsProvider slug={workspace.slug} projectKey={project.key} number={query.data.number}>
      <History slug={workspace.slug} issue={query.data} />
    </IssueAttachmentsProvider>
  );
}

function History({ slug, issue }: { slug: string; issue: IssueDetail }) {
  const { t } = useTranslation('issue');
  const project = useProject();
  const [params, setParams] = useSearchParams();
  const timelineRef = useRef<HTMLDivElement>(null);
  const panelRef = useRef<HTMLDivElement>(null);
  const list = useIssueRevisionsQuery(slug, project.key, issue.number);
  const items = list.data?.pages.flatMap((page) => page.items) ?? [];
  /** 지금 판 — 판은 1부터 빠짐없이 쌓인다. 목록이 더 새로우면 목록을 믿는다 */
  const latest = Math.max(issue.revisionNo, items[0]?.no ?? 0);
  const selection = readRevisionSelection(params, latest);
  const viewPath = issuePath(slug, project.key, issue.number);

  const choose = (next: RevisionSelection, reveal = false) => {
    setParams(writeRevisionSelection(params, next), { replace: true });
    const panel = panelRef.current;
    const timeline = timelineRef.current;
    // 좁은 화면(차이가 타임라인 아래)에서 판을 고르면 그 차이로 내려 준다
    if (reveal && panel && timeline && panel.getBoundingClientRect().top >= timeline.getBoundingClientRect().bottom - 1) {
      panel.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
  };

  return (
    <Box>
      <Breadcrumbs maxItems={4} itemsBeforeCollapse={1} itemsAfterCollapse={2} sx={{ mb: 1.5, fontSize: 13 }}>
        <Link component={RouterLink} to={projectPath(slug, project.key)} underline="hover" color="inherit">
          {project.name}
        </Link>
        <Link component={RouterLink} to={viewPath} underline="hover" color="inherit" sx={{ overflowWrap: 'anywhere' }}>
          #{issue.number} {issue.title}
        </Link>
      </Breadcrumbs>
      <Stack
        direction={{ xs: 'column', sm: 'row' }}
        spacing={1.5}
        sx={{ alignItems: { xs: 'flex-start', sm: 'center' }, justifyContent: 'space-between', mb: 2.5 }}
      >
        <Box sx={{ minWidth: 0 }}>
          <Typography variant="h4" component="h1">
            {t('revisions.title')}
          </Typography>
          <Typography variant="body2" sx={{ color: 'text.secondary', mt: 0.5, overflowWrap: 'anywhere' }}>
            #{issue.number} {issue.title}
          </Typography>
        </Box>
        <Button component={RouterLink} to={viewPath} color="inherit" startIcon={<ArrowBackRounded />} sx={{ flexShrink: 0 }}>
          {t('revisions.backToIssue')}
        </Button>
      </Stack>

      {list.isPending ? (
        <LoadingState />
      ) : list.isError ? (
        <ErrorState error={list.error} onRetry={() => list.refetch()} />
      ) : items.length === 0 || latest < 1 ? (
        <EmptyState title={t('revisions.emptyTitle')} description={t('revisions.emptyBody')} />
      ) : (
        <Box sx={{ containerType: 'inline-size' }}>
          <Box
            sx={{
              display: 'grid',
              gap: 3,
              alignItems: 'start',
              gridTemplateColumns: 'minmax(0, 1fr)',
              [SIDE_BY_SIDE]: { gridTemplateColumns: '300px minmax(0, 1fr)' },
            }}
          >
            <Box ref={timelineRef} sx={{ minWidth: 0, [SIDE_BY_SIDE]: { position: 'sticky', top: 32 } }}>
              <Timeline
                items={items}
                selected={selection.rev}
                onSelect={(no) => choose({ rev: no, from: undefined, view: selection.view }, true)}
                hasMore={list.hasNextPage}
                loadingMore={list.isFetchingNextPage}
                onMore={() => void list.fetchNextPage()}
              />
            </Box>
            <Box ref={panelRef} sx={{ minWidth: 0, scrollMarginTop: 16 }}>
              <RevisionPanel
                key={`${selection.rev}:${selection.from ?? ''}`}
                slug={slug}
                issue={issue}
                latest={latest}
                selection={selection}
                onChange={(next) => choose(next)}
              />
            </Box>
          </Box>
        </Box>
      )}
    </Box>
  );
}

/** 판 타임라인 — 새 판부터. 판 번호 · 누가 · 언제(밀리초까지) · 어느 길로(MCP) · 바꾼 필드 · 지금 판 */
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
        {/* 타임라인의 줄과 점 — 지금 판은 채운 점, 고른 판은 주 색 테두리(뜻은 옆의 글 "지금 판" · 선택 표시가 전한다) */}
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
            {item.via === 'MCP' && <Chip size="small" variant="outlined" label={t('journal.viaMcp')} title={t('journal.viaMcpHelp')} sx={chipSx} />}
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

/** 고른 판 — 다른 판과의 차이(기본) 또는 그 판 전체. 견줄 판은 아무 판이나(바로 앞 판이 기본, 0 = 빈 일감) */
function RevisionPanel({ slug, issue, latest, selection, onChange }: {
  slug: string;
  issue: IssueDetail;
  latest: number;
  selection: RevisionSelection;
  onChange: (next: RevisionSelection) => void;
}) {
  const { t } = useTranslation('issue');
  const project = useProject();
  const { rev, from, view } = selection;
  const diff = useIssueDiffQuery(slug, project.key, issue.number, rev, from, view === 'diff');
  const snapshot = useIssueRevisionQuery(slug, project.key, issue.number, rev, view === 'snapshot');
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

/** 두 판의 차이 — 누가 언제 남긴 판끼리인지, 바뀐 필드(전 → 후), 설명의 줄 차이 */
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

/** 견주는 판 하나 — 판 번호 · 누가 · 언제(밀리초까지) · MCP · 지금 판. info 가 없으면 빈 일감 */
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
        {info.via === 'MCP' && <Chip size="small" variant="outlined" label={t('journal.viaMcp')} title={t('journal.viaMcpHelp')} sx={chipSx} />}
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

/** 한 판의 일감 전체 — 그 판의 속성과 설명(Markdown) */
function Snapshot({ slug, issueNumber, revision }: { slug: string; issueNumber: number; revision: IssueRevision }) {
  const { t } = useTranslation('issue');
  const project = useProject();
  const typeName = useIssueTypeName();
  const toIssue = useCallback((n: number) => issuePath(slug, project.key, n), [slug, project.key]);
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

/** 판을 남긴 사람 이름 — 없으면 서비스 · 알 수 없음 */
function actorName(info: Pick<IssueRevisionItem, 'actorType' | 'actorName'>, service: string, unknown: string): string {
  return info.actorName || (info.actorType === 'SERVICE' ? service : unknown);
}
