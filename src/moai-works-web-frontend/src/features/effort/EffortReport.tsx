import DownloadRounded from '@mui/icons-material/DownloadRounded';
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
import { useMemo, useState, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { Link as RouterLink, useSearchParams } from 'react-router';
import { toast } from 'sonner';

import { issuePath, projectEffortPath } from '@/app/routes';
import { useCan } from '@/features/auth/access';
import { useIssueTypeName, personName } from '@/features/issue/issue-labels';
import { formatHours } from '@/features/issue/work-hours';
import { PersonFilter, type PersonChoice } from '@/features/workspace/PersonFilter';
import { useMembersQuery } from '@/features/workspace/queries';
import { useWorkspace } from '@/features/workspace/workspace-context';
import { effortApi } from '@/shared/api/endpoints';
import { errorMessage } from '@/shared/api/http';
import type { EffortReport as Report, EffortRow, EffortTotals } from '@/shared/api/types';
import { EmptyState, ErrorState, LoadingState } from '@/shared/ui/States';
import { VisuallyHidden } from '@/shared/ui/VisuallyHidden';

import {
  barPercent,
  carriesEstimate,
  effortCsvName,
  effortGroupingsFor,
  effortRanges,
  hasEffortFilters,
  rangeDates,
  readEffortFilters,
  saveFile,
  seoulToday,
  signedHours,
  toEffortQuery,
  writeEffortFilters,
  type EffortFilters,
  type EffortRange,
  type EffortScope,
} from './effort';
import { useEffortReportQuery } from './queries';

/** 첫 열(기준) — 휴대폰에서 표를 옆으로 밀어도 어느 줄인지 잃지 않게 붙여 둔다(RolesPage 의 stickyCell 과 같다) */
const stickyCell = {
  position: 'sticky',
  left: 0,
  zIndex: 1,
  bgcolor: 'background.paper',
  borderRight: 1,
  borderRightColor: 'divider',
} as const;

const numberCell = { fontVariantNumeric: 'tabular-nums', whiteSpace: 'nowrap' } as const;

/**
 * 공수 집계 — 조건(기간 · 기준 · 프로젝트 · 사람 · 유형)과 표(줄마다 실제 투입 막대 · 예상 · 차이 · 비율)와 합계, CSV 내려받기.
 * 워크스페이스 화면(projectKey 없음)과 프로젝트 화면(그 프로젝트로 정해짐)이 같이 쓴다. 조건은 주소에 남긴다.
 * 어느 프로젝트의 기록이 들어가는지는 서버가 판정한다 — 화면은 받은 것을 그린다.
 */
export function EffortReport({ projectKey }: { projectKey?: string }) {
  const scope: EffortScope = projectKey ? 'project' : 'workspace';
  const { t, i18n } = useTranslation('effort');
  const typeName = useIssueTypeName();
  const { workspace } = useWorkspace();
  const can = useCan();
  const [params, setParams] = useSearchParams();
  const filters = useMemo(() => readEffortFilters(params, scope), [params, scope]);
  // 오늘(한국 시간)은 화면을 연 때로 정한다 — 이번 달 · 올해의 끝날
  const [today] = useState(seoulToday);
  const query = useMemo(() => toEffortQuery(filters, today, projectKey), [filters, today, projectKey]);
  const dates = rangeDates(filters, today);
  const report = useEffortReportQuery(workspace.slug, query);
  const members = useMembersQuery(workspace.slug, can('member.read'));
  const [downloading, setDownloading] = useState(false);
  const data = report.data;

  const change = (next: Partial<EffortFilters>) => setParams(writeEffortFilters({ ...filters, ...next }, scope), { replace: true });
  const changeRange = (range: EffortRange) =>
    change(range === 'custom' ? { range, from: dates.from, to: dates.to } : { range, from: null, to: null });

  /** 사람 고르기 — 멤버(볼 수 있으면)에 기록을 남긴 사람(떠난 멤버 포함)을 더한다 */
  const people = useMemo<PersonChoice[]>(
    () => (data?.people ?? []).map((p) => ({ id: p.id, name: personName(p) ?? p.id.slice(0, 8), username: p.username })),
    [data?.people],
  );

  const download = async () => {
    setDownloading(true);
    try {
      saveFile(await effortApi.csv(workspace.slug, query, i18n.language), effortCsvName(query));
      toast.success(t('downloaded'));
    } catch (e) {
      toast.error(errorMessage(e));
    } finally {
      setDownloading(false);
    }
  };

  return (
    <Card>
      <Box
        role="search"
        aria-label={t('filters')}
        sx={{ p: 2, borderBottom: 1, borderColor: 'divider', display: 'flex', flexWrap: 'wrap', gap: 1.5, alignItems: 'flex-start' }}
      >
        <TextField
          select
          size="small"
          label={t('range')}
          value={filters.range}
          onChange={(e) => changeRange(e.target.value as EffortRange)}
          sx={{ width: { xs: '100%', sm: 150 } }}
        >
          {effortRanges.map((r) => (
            <MenuItem key={r} value={r}>
              {t(`ranges.${r}`)}
            </MenuItem>
          ))}
        </TextField>
        <TextField
          type="date"
          size="small"
          label={t('from')}
          value={dates.from ?? ''}
          onChange={(e) => change({ range: 'custom', from: e.target.value || null, to: dates.to })}
          sx={{ width: { xs: 'calc(50% - 6px)', sm: 165 } }}
          slotProps={{ inputLabel: { shrink: true }, htmlInput: { max: dates.to ?? undefined } }}
        />
        <TextField
          type="date"
          size="small"
          label={t('to')}
          value={dates.to ?? ''}
          onChange={(e) => change({ range: 'custom', from: dates.from, to: e.target.value || null })}
          sx={{ width: { xs: 'calc(50% - 6px)', sm: 165 } }}
          slotProps={{ inputLabel: { shrink: true }, htmlInput: { min: dates.from ?? undefined } }}
        />
        <TextField
          select
          size="small"
          label={t('groupBy')}
          value={filters.groupBy}
          onChange={(e) => change({ groupBy: e.target.value as EffortFilters['groupBy'] })}
          sx={{ width: { xs: '100%', sm: 150 } }}
        >
          {effortGroupingsFor(scope).map((g) => (
            <MenuItem key={g} value={g}>
              {t(`groupings.${g}`)}
            </MenuItem>
          ))}
        </TextField>
        {scope === 'workspace' && (
          <TextField
            select
            size="small"
            label={t('project')}
            value={filters.project ?? ''}
            onChange={(e) => change({ project: e.target.value || null })}
            sx={{ width: { xs: '100%', sm: 200 } }}
            slotProps={{ inputLabel: { shrink: true }, select: { displayEmpty: true } }}
          >
            <MenuItem value="">{t('projectAll')}</MenuItem>
            {(data?.projects ?? []).map((p) => (
              <MenuItem key={p.key} value={p.key}>
                {p.name}
              </MenuItem>
            ))}
            {/* 주소에만 있고 목록에 없는 프로젝트도 고른 채로 보인다(서버가 404 로 알린다) */}
            {filters.project && !data?.projects.some((p) => p.key === filters.project) && (
              <MenuItem value={filters.project}>{filters.project}</MenuItem>
            )}
          </TextField>
        )}
        <PersonFilter
          label={t('person')}
          allLabel={t('personAll')}
          value={filters.person}
          onChange={(person) => change({ person })}
          members={members.data}
          extra={people}
          loading={members.isLoading}
        />
        <TextField
          select
          size="small"
          label={t('type')}
          value={filters.type ?? ''}
          onChange={(e) => change({ type: e.target.value || null })}
          sx={{ width: { xs: '100%', sm: 160 } }}
          slotProps={{ inputLabel: { shrink: true }, select: { displayEmpty: true } }}
        >
          <MenuItem value="">{t('typeAll')}</MenuItem>
          {(data?.types ?? []).map((ty) => (
            <MenuItem key={ty.key} value={ty.key}>
              {typeName(ty.key, ty.name)}
            </MenuItem>
          ))}
          {filters.type && !data?.types.some((ty) => ty.key === filters.type) && <MenuItem value={filters.type}>{filters.type}</MenuItem>}
        </TextField>
        {hasEffortFilters(filters, scope) && (
          <Button color="inherit" startIcon={<FilterAltOffRounded />} onClick={() => setParams(new URLSearchParams(), { replace: true })} sx={{ minHeight: 40 }}>
            {t('clear')}
          </Button>
        )}
      </Box>
      {report.isPending ? (
        <LoadingState />
      ) : report.isError || !data ? (
        <ErrorState error={report.error} onRetry={() => report.refetch()} />
      ) : (
        <Box sx={{ opacity: report.isPlaceholderData ? 0.6 : 1 }}>
          <Summary
            report={data}
            action={
              <Button variant="outlined" startIcon={<DownloadRounded />} onClick={() => void download()} loading={downloading} disabled={data.rows.length === 0}>
                {t('download')}
              </Button>
            }
          />
          {data.rows.length === 0 ? (
            <EmptyState title={t('empty')} description={t('emptyBody')} />
          ) : (
            <EffortTable report={data} scope={scope} filters={filters} />
          )}
        </Box>
      )}
    </Card>
  );
}

/** 합계 — 실제 · 예상 · 차이 · 비율과 일감 · 기록 · 사람 수, 기간, 지운 기록 표시 */
function Summary({ report, action }: { report: Report; action: ReactNode }) {
  const { t } = useTranslation('effort');
  const total = report.total;
  const hours = (value: number | null) => (value === null ? t('summary.none') : t('summary.hours', { hours: formatHours(value) }));
  const period =
    report.from && report.to
      ? t('summary.period', { from: report.from, to: report.to })
      : report.from
        ? t('summary.periodFrom', { from: report.from })
        : report.to
          ? t('summary.periodTo', { to: report.to })
          : t('summary.periodAll');
  const over = total.ratio !== null && total.ratio > 100;
  const stats: { label: string; value: string; tone?: 'over' }[] = [
    { label: t('summary.actual'), value: hours(total.actualHours) },
    { label: t('summary.estimate'), value: hours(total.estimatedHours) },
    { label: t('summary.diff'), value: total.diffHours === null ? t('summary.none') : t('summary.hours', { hours: signedHours(total.diffHours) }), tone: over ? 'over' : undefined },
    { label: t('summary.ratio'), value: total.ratio === null ? t('summary.none') : `${total.ratio}%`, tone: over ? 'over' : undefined },
  ];
  return (
    <Box component="section" aria-label={t('summary.label')} sx={{ p: 2, borderBottom: 1, borderColor: 'divider' }}>
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5} sx={{ justifyContent: 'space-between', alignItems: { xs: 'stretch', sm: 'flex-start' } }}>
        <Box sx={{ display: 'grid', gridTemplateColumns: { xs: 'repeat(2, minmax(0, 1fr))', md: 'repeat(4, minmax(0, auto))' }, columnGap: 4, rowGap: 1.5 }}>
          {stats.map((s) => (
            <Box key={s.label} sx={{ minWidth: 0 }}>
              <Typography variant="caption" component="div" sx={{ color: 'text.secondary' }}>
                {s.label}
              </Typography>
              <Typography variant="h6" component="div" sx={{ ...numberCell, color: s.tone === 'over' ? 'error.main' : 'text.primary' }}>
                {s.value}
                {s.tone === 'over' && <VisuallyHidden>{` · ${t('table.over')}`}</VisuallyHidden>}
              </Typography>
            </Box>
          ))}
        </Box>
        <Box sx={{ flexShrink: 0 }}>{action}</Box>
      </Stack>
      <Typography variant="body2" sx={{ color: 'text.secondary', mt: 1.5 }}>
        {period} · {t('summary.counts', { issues: total.issueCount, logs: total.logCount, people: total.personCount })}
      </Typography>
      <Typography variant="caption" component="p" sx={{ color: 'text.secondary', mt: 0.5, maxWidth: 760 }}>
        {t('summary.estimateRule')}
        {report.removedLogs > 0 && ` ${t('summary.removed', { count: report.removedLogs })}`}
      </Typography>
    </Box>
  );
}

/** 집계 표 — 상자 안에서만 옆으로 밀리고 첫 열은 붙어 있다. 맨 끝 줄은 합계 */
function EffortTable({ report, scope, filters }: { report: Report; scope: EffortScope; filters: EffortFilters }) {
  const { t } = useTranslation('effort');
  const withEstimate = carriesEstimate(report.groupBy);
  const withIssues = report.groupBy !== 'issue';
  const max = Math.max(0, ...report.rows.map((r) => r.actualHours));
  const groupLabel = t(`groupings.${report.groupBy}`);

  return (
    <TableContainer>
      <Table size="small" aria-label={t('table.label')} sx={{ '& .MuiTableCell-root': { px: { xs: 1, sm: 2 } } }}>
        <TableHead>
          <TableRow>
            <TableCell sx={{ ...stickyCell, minWidth: 160 }}>{groupLabel}</TableCell>
            <TableCell align="right" sx={{ minWidth: 150 }}>
              {t('table.actual')}
            </TableCell>
            {withEstimate && (
              <>
                <TableCell align="right">{t('table.estimate')}</TableCell>
                <TableCell align="right">{t('table.diff')}</TableCell>
                <TableCell align="right">{t('table.ratio')}</TableCell>
              </>
            )}
            {withIssues && <TableCell align="right">{t('table.issues')}</TableCell>}
            <TableCell align="right">{t('table.logs')}</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {report.rows.map((row) => {
            const pct = barPercent(row.actualHours, max);
            const over = row.ratio !== null && row.ratio > 100;
            return (
              <TableRow key={row.key} hover sx={{ verticalAlign: 'top' }}>
                <TableCell sx={{ ...stickyCell, maxWidth: { xs: 200, sm: 420 } }}>
                  <RowLabel row={row} groupBy={report.groupBy} scope={scope} filters={filters} />
                </TableCell>
                <TableCell align="right" sx={numberCell}>
                  {formatHours(row.actualHours)}
                  <Box
                    role="img"
                    aria-label={t('table.share', { percent: pct })}
                    sx={{ mt: 0.5, ml: 'auto', width: '100%', maxWidth: 220, height: 6, borderRadius: 3, bgcolor: 'action.hover', overflow: 'hidden' }}
                  >
                    <Box sx={{ width: `${pct}%`, height: '100%', borderRadius: 3, bgcolor: 'primary.main' }} />
                  </Box>
                </TableCell>
                {withEstimate && (
                  <>
                    <TableCell align="right" sx={numberCell}>
                      {row.estimatedHours === null ? '—' : formatHours(row.estimatedHours)}
                    </TableCell>
                    <TableCell align="right" sx={{ ...numberCell, color: over ? 'error.main' : undefined }}>
                      {row.diffHours === null ? '—' : signedHours(row.diffHours)}
                    </TableCell>
                    <TableCell align="right" sx={{ ...numberCell, color: over ? 'error.main' : undefined }}>
                      {row.ratio === null ? '—' : `${row.ratio}%`}
                      {over && <VisuallyHidden>{` · ${t('table.over')}`}</VisuallyHidden>}
                    </TableCell>
                  </>
                )}
                {withIssues && (
                  <TableCell align="right" sx={numberCell}>
                    {row.issueCount}
                  </TableCell>
                )}
                <TableCell align="right" sx={numberCell}>
                  {row.logCount}
                </TableCell>
              </TableRow>
            );
          })}
          <TotalRow total={report.total} withEstimate={withEstimate} withIssues={withIssues} />
        </TableBody>
      </Table>
    </TableContainer>
  );
}

function TotalRow({ total, withEstimate, withIssues }: { total: EffortTotals; withEstimate: boolean; withIssues: boolean }) {
  const { t } = useTranslation('effort');
  const cell = { ...numberCell, fontWeight: 700, bgcolor: 'background.subtle' } as const;
  return (
    <TableRow>
      <TableCell sx={{ ...stickyCell, fontWeight: 700, bgcolor: 'background.subtle' }}>{t('table.total')}</TableCell>
      <TableCell align="right" sx={cell}>
        {formatHours(total.actualHours)}
      </TableCell>
      {withEstimate && (
        <>
          <TableCell align="right" sx={cell}>
            {total.estimatedHours === null ? '—' : formatHours(total.estimatedHours)}
          </TableCell>
          <TableCell align="right" sx={cell}>
            {total.diffHours === null ? '—' : signedHours(total.diffHours)}
          </TableCell>
          <TableCell align="right" sx={cell}>
            {total.ratio === null ? '—' : `${total.ratio}%`}
          </TableCell>
        </>
      )}
      {withIssues && (
        <TableCell align="right" sx={cell}>
          {total.issueCount}
        </TableCell>
      )}
      <TableCell align="right" sx={cell}>
        {total.logCount}
      </TableCell>
    </TableRow>
  );
}

/** 줄의 이름 — 일감은 그 일감으로, 프로젝트는 같은 조건의 그 프로젝트 공수로 간다 */
function RowLabel({ row, groupBy, scope, filters }: { row: EffortRow; groupBy: Report['groupBy']; scope: EffortScope; filters: EffortFilters }) {
  const { t } = useTranslation('effort');
  const typeName = useIssueTypeName();
  const { workspace } = useWorkspace();
  const caption = (text: string) => (
    <Typography variant="caption" component="div" noWrap sx={{ color: 'text.secondary', fontFamily: 'monospace' }}>
      {text}
    </Typography>
  );

  if (groupBy === 'issue' && row.issue) {
    return (
      <>
        <Link
          component={RouterLink}
          to={issuePath(workspace.slug, row.issue.number)}
          underline="hover"
          variant="body2"
          title={row.issue.title}
          sx={{ display: 'block', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}
        >
          {`#${row.issue.number} ${row.issue.title}`}
        </Link>
        {caption(scope === 'workspace' ? `${row.issue.project} · ${typeName(row.issue.type)}` : typeName(row.issue.type))}
      </>
    );
  }
  if (groupBy === 'project' && row.project) {
    const search = writeEffortFilters({ ...filters, project: null, groupBy: 'issue' }, 'project').toString();
    return (
      <>
        <Link
          component={RouterLink}
          to={projectEffortPath(workspace.slug, row.project.key, search)}
          underline="hover"
          variant="body2"
          title={row.project.name}
          sx={{ display: 'block', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}
        >
          {row.project.name}
        </Link>
        {caption(row.project.key)}
      </>
    );
  }
  let text: string;
  let sub: string | null = null;
  switch (groupBy) {
    case 'person':
      text = (row.person && personName(row.person)) ?? t('table.unknownPerson');
      sub = row.person?.username && row.person.username !== text ? row.person.username : null;
      break;
    case 'type':
      text = row.type ? typeName(row.type, row.label) : row.label;
      break;
    case 'week':
      text = t('table.week', { from: row.period ?? '', to: row.periodEnd ?? '' });
      break;
    default:
      text = row.label;
  }
  return (
    <>
      <Typography variant="body2" noWrap title={text} sx={numberCell}>
        {text}
      </Typography>
      {sub && caption(sub)}
    </>
  );
}
