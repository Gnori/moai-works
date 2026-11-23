import DownloadRounded from '@mui/icons-material/DownloadRounded';
import FilterAltOffRounded from '@mui/icons-material/FilterAltOffRounded';
import TuneRounded from '@mui/icons-material/TuneRounded';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import Chip from '@mui/material/Chip';
import Collapse from '@mui/material/Collapse';
import Link from '@mui/material/Link';
import MenuItem from '@mui/material/MenuItem';
import Stack from '@mui/material/Stack';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import { useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';

import { PersonFilter, type PersonChoice } from '@/features/workspace/PersonFilter';
import { errorMessage } from '@/shared/api/http';
import { mcpAccessOperations, mcpAccessOutcomes, type McpAccessEntry, type Member } from '@/shared/api/types';
import { dateTimeMs } from '@/shared/lib/format';
import { useDebounced } from '@/shared/lib/use-debounced';
import { summarizeUserAgent } from '@/shared/lib/user-agent';
import { EmptyState, ErrorState, LoadingState } from '@/shared/ui/States';

import { AccessEntryDialog } from './AccessEntryDialog';
import {
  accessAction,
  accessClient,
  accessTarget,
  emptyAccessFilters,
  hasAccessFilters,
  hasAdvancedFilters,
  knownMcpTools,
  outcomeColor,
  type AccessFilters,
} from './access-log';
import { accessCsvUrl, useAccessLogQuery, type AccessScope } from './queries';

/** 글자 조건 — 칠 때마다 묻지 않고 잠깐 멈추면 건다. 밖(주소)에서 바뀌면 따라간다 */
function TextFilter({ label, value, onChange, placeholder, width, mono = false }: {
  label: string;
  value: string | null;
  onChange: (next: string | null) => void;
  placeholder?: string;
  width: number;
  mono?: boolean;
}) {
  const [text, setText] = useState(value ?? '');
  const [prev, setPrev] = useState(value);
  if (prev !== value) {
    setPrev(value);
    setText(value ?? '');
  }
  const settled = useDebounced(text, 400);
  useEffect(() => {
    const next = settled.trim() || null;
    if (next !== value) {
      onChange(next);
    }
    // 멈춘 글자만 본다 — value 가 밖에서 바뀌면 위에서 text 를 맞춘다
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [settled]);
  return (
    <TextField
      size="small"
      label={label}
      value={text}
      placeholder={placeholder}
      onChange={(e) => setText(e.target.value)}
      sx={{ width: { xs: '100%', sm: width } }}
      slotProps={{ inputLabel: { shrink: true }, htmlInput: { style: mono ? { fontFamily: 'monospace' } : undefined, maxLength: 200 } }}
    />
  );
}

interface Props {
  scope: AccessScope;
  filters: AccessFilters;
  onChange: (next: AccessFilters) => void;
  /** 키 주인 · 키 조건과 칸을 보인다(워크스페이스 화면). 내 키 하나를 볼 때는 끈다 */
  showOwner: boolean;
  /** 워크스페이스 칸을 보인다(내 키 — 여러 워크스페이스에 걸친다) */
  showWorkspace: boolean;
  /** 사람 조건의 목록 — 워크스페이스 멤버(볼 권한이 없으면 undefined) */
  members?: Member[];
  membersLoading?: boolean;
  /** 그 일감 화면 주소 */
  issueHref?: (entry: McpAccessEntry) => string | null;
}

/**
 * MCP 접근 기록 — 조건(키 주인 · 작업 · 결과 · 날짜, 더 보기: 키 · 도구 · 클라이언트 · 프로젝트 · 세션)으로 좁히고, 줄을 누르면 모든 칸을 본다.
 * 넓은 화면은 표, 좁은 화면은 한 칸에 여러 줄로 쌓는다(가로로 밀리지 않게). 새 것부터 50줄씩, 거른 결과는 CSV 로 받는다.
 * 워크스페이스 화면과 내 키 대화상자가 함께 쓴다 — 조건을 어디에 두는지(주소 · 화면 상태)는 부른 쪽이 정한다.
 */
export function AccessLogView({ scope, filters, onChange, showOwner, showWorkspace, members, membersLoading, issueHref }: Props) {
  const { t } = useTranslation('mcp');
  const log = useAccessLogQuery(scope, filters);
  const rows = useMemo(() => log.data?.pages.flat() ?? [], [log.data]);
  const [open, setOpen] = useState<McpAccessEntry | null>(null);
  const [advanced, setAdvanced] = useState(() => hasAdvancedFilters(filters));
  const showAdvanced = advanced || hasAdvancedFilters(filters);
  const change = (next: Partial<AccessFilters>) => onChange({ ...filters, ...next });
  const filtered = hasAccessFilters(filters);

  /** 받은 줄에서 모은 사람 · 키 · 도구 — 목록 밖(떠난 멤버 · 새 도구)도 고를 수 있게 */
  const seen = useMemo(() => {
    const people = new Map<string, PersonChoice>();
    const keys = new Map<string, string>();
    const tools = new Set<string>(knownMcpTools);
    for (const r of rows) {
      if (r.ownerId && r.ownerName && !people.has(r.ownerId)) {
        people.set(r.ownerId, { id: r.ownerId, name: r.ownerName });
      }
      if (r.keyId && !keys.has(r.keyId)) {
        keys.set(r.keyId, `${r.keyName ?? ''} (${r.keyPrefix ?? ''}…)`);
      }
      if (r.tool) {
        tools.add(r.tool);
      }
    }
    if (filters.key && !keys.has(filters.key)) {
      keys.set(filters.key, `${filters.key.slice(0, 8)}…`);
    }
    if (filters.tool) {
      tools.add(filters.tool);
    }
    return { people: [...people.values()], keys: [...keys.entries()], tools: [...tools].sort() };
  }, [rows, filters.key, filters.tool]);

  const selectProps = { inputLabel: { shrink: true }, select: { displayEmpty: true } } as const;

  return (
    <>
      <Card>
        <Box role="search" aria-label={t('access.filters')} sx={{ p: 2, borderBottom: 1, borderColor: 'divider' }}>
          <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1.5, alignItems: 'flex-start' }}>
            {showOwner && (
              <PersonFilter
                label={t('access.person')}
                allLabel={t('access.personAll')}
                value={filters.person}
                onChange={(person) => change({ person })}
                members={members}
                extra={seen.people}
                loading={membersLoading}
              />
            )}
            <TextField
              select
              size="small"
              label={t('access.operation')}
              value={filters.operation ?? ''}
              onChange={(e) => change({ operation: (e.target.value || null) as AccessFilters['operation'] })}
              sx={{ width: { xs: 'calc(50% - 6px)', sm: 160 } }}
              slotProps={selectProps}
            >
              <MenuItem value="">{t('access.operationAll')}</MenuItem>
              {mcpAccessOperations.map((op) => (
                <MenuItem key={op} value={op}>
                  {t(`access.operations.${op}`)}
                </MenuItem>
              ))}
            </TextField>
            <TextField
              select
              size="small"
              label={t('access.outcome')}
              value={filters.outcome ?? ''}
              onChange={(e) => change({ outcome: (e.target.value || null) as AccessFilters['outcome'] })}
              sx={{ width: { xs: 'calc(50% - 6px)', sm: 140 } }}
              slotProps={selectProps}
            >
              <MenuItem value="">{t('access.outcomeAll')}</MenuItem>
              {mcpAccessOutcomes.map((o) => (
                <MenuItem key={o} value={o}>
                  {t(`access.outcomes.${o}`)}
                </MenuItem>
              ))}
            </TextField>
            <TextField
              type="date"
              size="small"
              label={t('access.from')}
              value={filters.from ?? ''}
              onChange={(e) => change({ from: e.target.value || null })}
              sx={{ width: { xs: 'calc(50% - 6px)', sm: 160 } }}
              slotProps={{ inputLabel: { shrink: true }, htmlInput: { max: filters.to ?? undefined } }}
            />
            <TextField
              type="date"
              size="small"
              label={t('access.to')}
              value={filters.to ?? ''}
              onChange={(e) => change({ to: e.target.value || null })}
              sx={{ width: { xs: 'calc(50% - 6px)', sm: 160 } }}
              slotProps={{ inputLabel: { shrink: true }, htmlInput: { min: filters.from ?? undefined } }}
            />
            <Button
              color="inherit"
              startIcon={<TuneRounded />}
              onClick={() => setAdvanced(!showAdvanced)}
              aria-expanded={showAdvanced}
              aria-controls="mcp-access-advanced"
              sx={{ minHeight: 40 }}
            >
              {showAdvanced ? t('access.fewerFilters') : t('access.moreFilters')}
            </Button>
            {filtered && (
              <Button color="inherit" startIcon={<FilterAltOffRounded />} onClick={() => onChange(emptyAccessFilters)} sx={{ minHeight: 40 }}>
                {t('access.clear')}
              </Button>
            )}
            <Button
              component="a"
              href={accessCsvUrl(scope, filters)}
              download
              variant="outlined"
              startIcon={<DownloadRounded />}
              title={t('access.csvHint')}
              sx={{ minHeight: 40, ml: { sm: 'auto' } }}
            >
              {t('access.csv')}
            </Button>
          </Box>
          <Collapse in={showAdvanced} id="mcp-access-advanced">
            <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1.5, alignItems: 'flex-start', pt: 1.5 }}>
              {showOwner && (
                <TextField
                  select
                  size="small"
                  label={t('access.key')}
                  value={filters.key ?? ''}
                  onChange={(e) => change({ key: e.target.value || null })}
                  sx={{ width: { xs: '100%', sm: 240 } }}
                  slotProps={selectProps}
                >
                  <MenuItem value="">{t('access.keyAll')}</MenuItem>
                  {seen.keys.map(([id, label]) => (
                    <MenuItem key={id} value={id}>
                      {label}
                    </MenuItem>
                  ))}
                </TextField>
              )}
              <TextField
                select
                size="small"
                label={t('access.tool')}
                value={filters.tool ?? ''}
                onChange={(e) => change({ tool: e.target.value || null })}
                sx={{ width: { xs: '100%', sm: 240 } }}
                slotProps={selectProps}
              >
                <MenuItem value="">{t('access.toolAll')}</MenuItem>
                {seen.tools.map((tool) => (
                  <MenuItem key={tool} value={tool} sx={{ fontFamily: 'monospace', fontSize: 13 }}>
                    {tool}
                  </MenuItem>
                ))}
              </TextField>
              <TextFilter
                label={t('access.client')}
                placeholder={t('access.clientPlaceholder')}
                value={filters.client}
                onChange={(client) => change({ client })}
                width={180}
              />
              <TextFilter label={t('access.project')} value={filters.project} onChange={(project) => change({ project })} width={140} mono />
              <TextFilter label={t('access.session')} value={filters.session} onChange={(session) => change({ session })} width={300} mono />
            </Box>
          </Collapse>
        </Box>
        {log.isPending ? (
          <LoadingState />
        ) : log.isError && rows.length === 0 ? (
          <ErrorState error={log.error} onRetry={() => log.refetch()} />
        ) : rows.length === 0 ? (
          filtered ? (
            <EmptyState title={t('access.noMatchTitle')} description={t('access.noMatchBody')} />
          ) : (
            <EmptyState title={t('access.empty')} description={t('access.emptyBody')} />
          )
        ) : (
          <TableContainer sx={{ opacity: log.isPlaceholderData ? 0.6 : 1 }}>
            <Table size="small" sx={{ '& .MuiTableCell-root': { px: { xs: 1, sm: 2 } } }}>
              <TableHead>
                <TableRow>
                  <TableCell>{t('access.colTime')}</TableCell>
                  {showOwner && <TableCell sx={{ display: { xs: 'none', md: 'table-cell' } }}>{t('access.colWho')}</TableCell>}
                  <TableCell>{t('access.colAction')}</TableCell>
                  <TableCell sx={{ display: { xs: 'none', md: 'table-cell' } }}>{t('access.colTarget')}</TableCell>
                  <TableCell sx={{ display: { xs: 'none', md: 'table-cell' } }}>{t('access.colOutcome')}</TableCell>
                  <TableCell sx={{ display: { xs: 'none', lg: 'table-cell' } }}>{t('access.colClient')}</TableCell>
                  <TableCell sx={{ display: { xs: 'none', lg: 'table-cell' } }}>{t('access.colIp')}</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {rows.map((r) => (
                  <AccessRow key={r.id} entry={r} showOwner={showOwner} showWorkspace={showWorkspace} onOpen={() => setOpen(r)} />
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </Card>
      {rows.length > 0 && (
        <Stack direction="row" spacing={2} sx={{ alignItems: 'center', justifyContent: 'space-between', mt: 1.5, flexWrap: 'wrap', rowGap: 1 }}>
          <Typography variant="caption" sx={{ color: log.isFetchNextPageError ? 'error.main' : 'text.secondary' }} role="status">
            {log.isFetchNextPageError ? errorMessage(log.error) : t('access.shown', { count: rows.length })}
          </Typography>
          {log.hasNextPage && (
            <Button variant="outlined" onClick={() => void log.fetchNextPage()} loading={log.isFetchingNextPage}>
              {t('access.more')}
            </Button>
          )}
        </Stack>
      )}
      {open && (
        <AccessEntryDialog
          entry={open}
          onClose={() => setOpen(null)}
          onSameSession={(session) => {
            setOpen(null);
            setAdvanced(true);
            onChange({ ...emptyAccessFilters, session });
          }}
          onSameKey={
            showOwner
              ? (key) => {
                  setOpen(null);
                  setAdvanced(true);
                  change({ key });
                }
              : undefined
          }
          issueHref={issueHref?.(open) ?? null}
        />
      )}
    </>
  );
}

/** 한 줄 — 넓으면 칸마다, 좁으면 "무엇을" 칸에 누가 · 대상 · 결과 · 클라이언트를 한 줄씩 쌓는다 */
function AccessRow({ entry: r, showOwner, showWorkspace, onOpen }: { entry: McpAccessEntry; showOwner: boolean; showWorkspace: boolean; onOpen: () => void }) {
  const { t } = useTranslation('mcp');
  const time = dateTimeMs(r.occurredAt);
  const target = [showWorkspace && r.workspaceSlug ? r.workspaceName ?? r.workspaceSlug : null, accessTarget(r) || null].filter(Boolean).join(' · ');
  const client = accessClient(r);
  const program = summarizeUserAgent(r.userAgent);
  const who = r.ownerName ?? t('access.noKey');
  const keyLabel = r.keyId ? `${r.keyName ?? ''} (${r.keyPrefix ?? ''}…)` : null;
  const outcome = (
    <Stack direction="row" spacing={0.75} sx={{ alignItems: 'center', flexWrap: 'wrap', rowGap: 0.5 }}>
      <Chip size="small" color={outcomeColor(r.outcome)} label={t(`access.outcomes.${r.outcome}`)} />
      {r.errorCode && (
        <Typography variant="caption" sx={{ fontFamily: 'monospace', color: 'text.secondary', overflowWrap: 'anywhere' }}>
          {r.errorCode}
        </Typography>
      )}
    </Stack>
  );
  return (
    <TableRow hover onClick={onOpen} sx={{ verticalAlign: 'top', cursor: 'pointer' }}>
      <TableCell sx={{ whiteSpace: { xs: 'normal', md: 'nowrap' }, fontVariantNumeric: 'tabular-nums', minWidth: { xs: 96, md: 'auto' } }}>
        <Link
          component="button"
          type="button"
          variant="body2"
          underline="hover"
          color="text.secondary"
          onClick={(e) => {
            e.stopPropagation();
            onOpen();
          }}
          aria-label={t('access.openDetail', { time })}
          sx={{ textAlign: 'left', fontVariantNumeric: 'tabular-nums' }}
        >
          {time}
        </Link>
      </TableCell>
      {showOwner && (
        <TableCell sx={{ display: { xs: 'none', md: 'table-cell' }, minWidth: 140 }}>
          <Typography variant="body2" sx={{ whiteSpace: 'nowrap' }}>
            {who}
          </Typography>
          {keyLabel && (
            <Typography variant="caption" sx={{ color: 'text.secondary', display: 'block', overflowWrap: 'anywhere' }}>
              {keyLabel}
            </Typography>
          )}
        </TableCell>
      )}
      <TableCell sx={{ minWidth: { xs: 140, md: 180 } }}>
        <Stack direction="row" spacing={0.75} sx={{ alignItems: 'center', flexWrap: 'wrap', rowGap: 0.5 }}>
          <Chip size="small" variant="outlined" label={t(`access.operations.${r.operation}`)} />
          <Typography variant="body2" sx={{ fontFamily: 'monospace', fontSize: 13, overflowWrap: 'anywhere' }}>
            {accessAction(r)}
          </Typography>
        </Stack>
        {/* 좁은 화면 — 숨긴 칸을 여기에 한 줄씩 */}
        <Box sx={{ display: { xs: 'block', md: 'none' }, mt: 0.75 }}>
          {showOwner && (
            <Typography variant="body2" sx={{ color: 'text.secondary', overflowWrap: 'anywhere' }}>
              {keyLabel ? `${who} · ${keyLabel}` : who}
            </Typography>
          )}
          {target && (
            <Typography variant="body2" sx={{ color: 'text.secondary', overflowWrap: 'anywhere' }}>
              {target}
            </Typography>
          )}
          <Box sx={{ mt: 0.5 }}>{outcome}</Box>
        </Box>
        <Box sx={{ display: { xs: 'block', lg: 'none' }, mt: 0.5 }}>
          <Typography variant="caption" sx={{ color: 'text.secondary', display: 'block', overflowWrap: 'anywhere' }}>
            {[client || null, program, r.ip, t('access.duration', { ms: r.durationMs })].filter(Boolean).join(' · ')}
          </Typography>
        </Box>
      </TableCell>
      <TableCell sx={{ display: { xs: 'none', md: 'table-cell' }, minWidth: 120 }}>
        <Typography variant="body2" sx={{ overflowWrap: 'anywhere' }}>
          {target || '—'}
        </Typography>
      </TableCell>
      <TableCell sx={{ display: { xs: 'none', md: 'table-cell' }, minWidth: 110 }}>{outcome}</TableCell>
      <TableCell sx={{ display: { xs: 'none', lg: 'table-cell' }, minWidth: 140 }}>
        <Typography variant="body2" sx={{ overflowWrap: 'anywhere' }}>
          {client || '—'}
        </Typography>
        {program && (
          <Typography variant="caption" title={r.userAgent ?? undefined} sx={{ color: 'text.secondary', display: 'block', overflowWrap: 'anywhere' }}>
            {program}
          </Typography>
        )}
      </TableCell>
      <TableCell sx={{ display: { xs: 'none', lg: 'table-cell' }, whiteSpace: 'nowrap' }}>
        <Typography variant="body2" sx={{ fontFamily: 'monospace', fontSize: 12, color: 'text.secondary' }}>
          {r.ip ?? '—'}
        </Typography>
        <Typography variant="caption" sx={{ color: 'text.secondary' }}>
          {t('access.duration', { ms: r.durationMs })}
        </Typography>
      </TableCell>
    </TableRow>
  );
}
