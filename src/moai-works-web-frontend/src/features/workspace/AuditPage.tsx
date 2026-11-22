import FilterAltOffRounded from '@mui/icons-material/FilterAltOffRounded';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
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
import { keepPreviousData, useInfiniteQuery } from '@tanstack/react-query';
import { useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { useSearchParams } from 'react-router';

import { useCan } from '@/features/auth/access';
import { workspaceApi } from '@/shared/api/endpoints';
import { errorMessage } from '@/shared/api/http';
import type { AuditEntry } from '@/shared/api/types';
import { dateTimeMs } from '@/shared/lib/format';
import { useLabels } from '@/shared/lib/labels';
import { summarizeUserAgent } from '@/shared/lib/user-agent';
import { PageHeader } from '@/shared/ui/PageHeader';
import { EmptyState, ErrorState, LoadingState } from '@/shared/ui/States';

import {
  auditActionGroups,
  hasAuditFilters,
  nextAuditCursor,
  parseAuditDetails,
  readAuditFilters,
  toAuditQuery,
  type AuditCursor,
  writeAuditFilters,
  type AuditFilters,
} from './audit-log';
import { PersonFilter, type PersonChoice } from './PersonFilter';
import { useMembersQuery, workspaceKeys } from './queries';
import { useWorkspace } from './workspace-context';

/** IP · 프로그램 · 내용 — 글자로만 보이고 HTML 로 해석하지 않는다 */
function EntryDetails({ entry, showIp }: { entry: AuditEntry; showIp: boolean }) {
  const { t } = useTranslation('workspace');
  const { text, userAgent } = parseAuditDetails(entry.details);
  const program = summarizeUserAgent(userAgent);
  const lines = [showIp && entry.ip ? `IP ${entry.ip}` : null, program ? t('audit.program', { name: program }) : null].filter(Boolean);
  return (
    <>
      {lines.length > 0 && (
        <Typography variant="caption" component="div" title={userAgent ?? undefined} sx={{ color: 'text.secondary', overflowWrap: 'anywhere' }}>
          {lines.join(' · ')}
        </Typography>
      )}
      {text && (
        <Typography variant="caption" component="div" sx={{ color: 'text.secondary', wordBreak: 'break-all' }}>
          {text}
        </Typography>
      )}
    </>
  );
}

/**
 * 한 사람 — 이름을 누르면 그 사람의 기록만(사람 조건과 같다). 이미 그 사람으로 거른 줄이면 글자로만.
 * 이름은 줄을 바꾸지 않는다(한 글자씩 꺾이지 않게) — 아주 긴 이름만 단어 경계에서 넘긴다.
 */
function ActorName({ entry, person, onPick }: { entry: AuditEntry; person: string | null; onPick: (id: string) => void }) {
  const { t } = useTranslation('workspace');
  const actor = entry.actorName ?? entry.actorType;
  const id = entry.actorId;
  if (!id || id === person) {
    return <>{actor}</>;
  }
  return (
    <Link
      component="button"
      type="button"
      variant="body2"
      underline="hover"
      color="inherit"
      onClick={() => onPick(id)}
      aria-label={t('audit.onlyPerson', { name: actor })}
      sx={{ textAlign: 'left', verticalAlign: 'baseline', overflowWrap: 'break-word' }}
    >
      {actor}
    </Link>
  );
}

/**
 * 감사 기록 — 누가 언제 무엇을 바꿨나. 사람 · 동작 묶음 · 날짜로 좁히고, 조건은 주소에 남긴다(MCP 키 화면의 링크가 그대로 열린다).
 * 새 것부터 50건씩, "더 보기"는 마지막 줄의 시각보다 앞의 기록을 받는다. 기록은 지우거나 고칠 수 없다.
 */
export function AuditPage() {
  const { t } = useTranslation(['workspace', 'common']);
  const labels = useLabels();
  const { workspace } = useWorkspace();
  const can = useCan();
  const [params, setParams] = useSearchParams();
  const filters = useMemo(() => readAuditFilters(params), [params]);
  const members = useMembersQuery(workspace.slug, can('member.read'));

  const audit = useInfiniteQuery({
    queryKey: workspaceKeys.auditList(workspace.slug, filters),
    queryFn: ({ pageParam }) => workspaceApi.audit(workspace.slug, toAuditQuery(filters, pageParam)),
    initialPageParam: undefined as AuditCursor | undefined,
    getNextPageParam: (last) => nextAuditCursor(last),
    // 조건을 바꾸는 동안 앞 목록을 그대로 둔다(깜빡이지 않게)
    placeholderData: keepPreviousData,
  });
  const rows = useMemo(() => audit.data?.pages.flat() ?? [], [audit.data]);

  /** 멤버 목록 밖의 사람(떠난 멤버 · 목록을 볼 권한이 없음)도 고를 수 있게 — 받은 기록의 사람을 더한다 */
  const people = useMemo(() => {
    const seen = new Map<string, PersonChoice>();
    for (const row of rows) {
      if (row.actorId && row.actorName && !seen.has(row.actorId)) {
        seen.set(row.actorId, { id: row.actorId, name: row.actorName });
      }
    }
    return [...seen.values()];
  }, [rows]);

  const change = (next: Partial<AuditFilters>) => setParams(writeAuditFilters({ ...filters, ...next }), { replace: true });
  const knownGroup = auditActionGroups.some((g) => g.value === filters.action);
  const filtered = hasAuditFilters(filters);

  return (
    <>
      <PageHeader eyebrow={workspace.name} title={t('audit.title')} description={t('audit.description')} />
      <Card>
        <Box
          role="search"
          aria-label={t('audit.filters')}
          sx={{ p: 2, borderBottom: 1, borderColor: 'divider', display: 'flex', flexWrap: 'wrap', gap: 1.5, alignItems: 'flex-start' }}
        >
          <PersonFilter
            label={t('audit.person')}
            allLabel={t('audit.personAll')}
            value={filters.person}
            onChange={(person) => change({ person })}
            members={members.data}
            extra={people}
            loading={members.isLoading}
          />
          <TextField
            select
            size="small"
            label={t('audit.action')}
            value={filters.action ?? ''}
            onChange={(e) => change({ action: e.target.value || null })}
            sx={{ width: { xs: '100%', sm: 200 } }}
            slotProps={{ inputLabel: { shrink: true }, select: { displayEmpty: true } }}
          >
            <MenuItem value="">{t('audit.actionAll')}</MenuItem>
            {auditActionGroups.map((g) => (
              <MenuItem key={g.value} value={g.value}>
                {t(`audit.groups.${g.label}`)}
              </MenuItem>
            ))}
            {/* 주소에 동작 이름 하나가 직접 적혀 있으면(묶음이 아니면) 그 이름도 고른 채로 보인다 */}
            {filters.action && !knownGroup && <MenuItem value={filters.action}>{labels.auditAction(filters.action)}</MenuItem>}
          </TextField>
          <TextField
            type="date"
            size="small"
            label={t('audit.from')}
            value={filters.from ?? ''}
            onChange={(e) => change({ from: e.target.value || null })}
            sx={{ width: { xs: 'calc(50% - 6px)', sm: 170 } }}
            slotProps={{ inputLabel: { shrink: true }, htmlInput: { max: filters.to ?? undefined } }}
          />
          <TextField
            type="date"
            size="small"
            label={t('audit.to')}
            value={filters.to ?? ''}
            onChange={(e) => change({ to: e.target.value || null })}
            sx={{ width: { xs: 'calc(50% - 6px)', sm: 170 } }}
            slotProps={{ inputLabel: { shrink: true }, htmlInput: { min: filters.from ?? undefined } }}
          />
          {filtered && (
            <Button color="inherit" startIcon={<FilterAltOffRounded />} onClick={() => setParams(new URLSearchParams(), { replace: true })} sx={{ minHeight: 40 }}>
              {t('audit.clear')}
            </Button>
          )}
        </Box>
        {audit.isPending ? (
          <LoadingState />
        ) : audit.isError && rows.length === 0 ? (
          <ErrorState error={audit.error} onRetry={() => audit.refetch()} />
        ) : rows.length === 0 ? (
          filtered ? <EmptyState title={t('audit.noMatchTitle')} description={t('audit.noMatchBody')} /> : <EmptyState title={t('audit.empty')} />
        ) : (
          <TableContainer sx={{ opacity: audit.isPlaceholderData ? 0.6 : 1 }}>
            {/* 좁은 화면에서는 칸 여백을 줄여 표가 상자 안에 들어오게 한다 */}
            <Table size="small" sx={{ '& .MuiTableCell-root': { px: { xs: 1, sm: 2 } } }}>
              <TableHead>
                <TableRow>
                  <TableCell>{t('audit.colTime')}</TableCell>
                  <TableCell sx={{ display: { xs: 'none', md: 'table-cell' } }}>{t('audit.colActor')}</TableCell>
                  <TableCell>
                    {t('audit.colAction')}
                    {/* 좁은 화면에서는 누가를 이 칸에 함께 적는다 */}
                    <Box component="span" sx={{ display: { xs: 'inline', md: 'none' } }}>
                      {` · ${t('audit.colActor')}`}
                    </Box>
                  </TableCell>
                  <TableCell sx={{ display: { xs: 'none', md: 'table-cell' } }}>{t('audit.colIp')}</TableCell>
                  <TableCell sx={{ display: { xs: 'none', md: 'table-cell' } }}>{t('audit.colDetails')}</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {rows.map((a) => {
                  const pick = (person: string) => change({ person });
                  return (
                    <TableRow key={a.id} hover sx={{ verticalAlign: 'top' }}>
                      <TableCell
                        sx={{ whiteSpace: { xs: 'normal', md: 'nowrap' }, fontVariantNumeric: 'tabular-nums', color: 'text.secondary', minWidth: { xs: 96, md: 'auto' } }}
                      >
                        {dateTimeMs(a.occurredAt)}
                      </TableCell>
                      {/* 넓은 화면의 누가 — 이름을 한 줄로 */}
                      <TableCell sx={{ display: { xs: 'none', md: 'table-cell' }, whiteSpace: 'nowrap', minWidth: 120 }}>
                        <ActorName entry={a} person={filters.person} onPick={pick} />
                      </TableCell>
                      <TableCell sx={{ minWidth: { xs: 120, md: 160 } }}>
                        <Typography variant="body2" title={a.action}>
                          {labels.auditAction(a.action)}
                        </Typography>
                        {/* 좁은 화면에서는 누가 · IP · 내용 열을 숨기고 여기에 한 줄씩 적는다 */}
                        <Box sx={{ display: { xs: 'block', md: 'none' }, mt: 0.25 }}>
                          <Typography variant="body2" component="div" sx={{ color: 'text.secondary', overflowWrap: 'break-word' }}>
                            <ActorName entry={a} person={filters.person} onPick={pick} />
                          </Typography>
                          <EntryDetails entry={a} showIp />
                        </Box>
                      </TableCell>
                      <TableCell
                        sx={{ display: { xs: 'none', md: 'table-cell' }, color: 'text.secondary', fontFamily: 'monospace', fontSize: 12, whiteSpace: 'nowrap' }}
                      >
                        {a.ip ?? '—'}
                      </TableCell>
                      <TableCell sx={{ display: { xs: 'none', md: 'table-cell' }, maxWidth: 480 }}>
                        <EntryDetails entry={a} showIp={false} />
                      </TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </Card>
      {rows.length > 0 && (
        <Stack direction="row" spacing={2} sx={{ alignItems: 'center', justifyContent: 'space-between', mt: 1.5, flexWrap: 'wrap', rowGap: 1 }}>
          <Typography variant="caption" sx={{ color: audit.isFetchNextPageError ? 'error.main' : 'text.secondary' }} role="status">
            {audit.isFetchNextPageError ? errorMessage(audit.error) : t('audit.shown', { count: rows.length })}
          </Typography>
          {audit.hasNextPage && (
            <Button variant="outlined" onClick={() => void audit.fetchNextPage()} loading={audit.isFetchingNextPage}>
              {t('audit.more')}
            </Button>
          )}
        </Stack>
      )}
    </>
  );
}
