import AddRounded from '@mui/icons-material/AddRounded';
import ArrowForwardRounded from '@mui/icons-material/ArrowForwardRounded';
import FilterAltOffRounded from '@mui/icons-material/FilterAltOffRounded';
import SearchRounded from '@mui/icons-material/SearchRounded';
import TaskAltRounded from '@mui/icons-material/TaskAltRounded';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import Checkbox from '@mui/material/Checkbox';
import FormControlLabel from '@mui/material/FormControlLabel';
import InputAdornment from '@mui/material/InputAdornment';
import LinearProgress from '@mui/material/LinearProgress';
import Link from '@mui/material/Link';
import List from '@mui/material/List';
import ListItemButton from '@mui/material/ListItemButton';
import ListItemText from '@mui/material/ListItemText';
import MenuItem from '@mui/material/MenuItem';
import Pagination from '@mui/material/Pagination';
import Stack from '@mui/material/Stack';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import TableSortLabel from '@mui/material/TableSortLabel';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import useMediaQuery from '@mui/material/useMediaQuery';
import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { Link as RouterLink, useSearchParams } from 'react-router';

import { issuePath, newIssuePath, projectPath } from '@/app/routes';
import { useMe } from '@/features/auth/session';
import { useProjectModulesQuery, useProjectsQuery } from '@/features/project/queries';
import { issuePriorities, issueStatuses, type IssuePriority, type IssueSortKey, type IssueStatus, type IssueSummary, type ProjectDetail } from '@/shared/api/types';
import { date, dateTimeMs, fromNow } from '@/shared/lib/format';
import { useLabels } from '@/shared/lib/labels';
import { useDebounced } from '@/shared/lib/use-debounced';
import { EmptyState, ErrorState, LoadingState } from '@/shared/ui/States';

import { personName, useIssueTypeName } from './issue-labels';
import {
  hasIssueFilters,
  issueNumberQuery,
  issueSorts,
  readIssueFilters,
  sortDirection,
  toIssueListParams,
  toggleIssueSort,
  writeIssueFilters,
  type IssueFilters,
} from './issue-filters';
import { IssuePriorityChip } from './IssuePriorityChip';
import { IssueStatusChip } from './IssueStatusChip';
import { IssueTags } from './IssueTags';
import { useAgentSessionsQuery, useIssueListQuery, useIssueTypesQuery, useWorkspaceModulesQuery } from './queries';

/**
 * 일감 목록 — 찾기 · 상태 · 우선순위 · 유형 · 담당자 · 관리자 · 모듈 · 에이전트 세션 · 맨 위만 · 정렬 · 쪽. 조건은 모두 주소에 남는다(링크로 같은 목록을 나눈다).
 * project 를 주면 그 프로젝트의 목록, 주지 않으면 워크스페이스의 목록(내가 읽을 수 있는 프로젝트 모두 — Redmine 의 /issues, 2026-11-24)이다.
 * 워크스페이스 목록에는 프로젝트 칸과 프로젝트 거르기(여럿)가 더 있다. 찾는 글이 번호 하나(#123)면 그 일감으로 바로 가는 링크를 보인다.
 * 넓은 화면은 표(머리를 눌러 정렬), 좁은 화면(390px)은 카드 목록으로 그린다. 페이지 전체가 가로로 밀리지 않는다.
 */
export function IssueList({ slug, project }: { slug: string; project?: ProjectDetail }) {
  const { t } = useTranslation('issue');
  const me = useMe();
  const desktop = useMediaQuery((theme) => theme.breakpoints.up('md'));
  const [params, setParams] = useSearchParams();
  const workspaceScope = project === undefined;
  const filters = useMemo(() => readIssueFilters(params), [params]);
  const request = useMemo(() => toIssueListParams(filters, me.id, workspaceScope), [filters, me.id, workspaceScope]);
  const query = useIssueListQuery(slug, project?.key ?? null, request);
  const canCreate = project?.myActions.includes('createIssue') ?? false;
  const jumpNumber = issueNumberQuery(filters.q);

  /** 조건을 바꾼다 — 조건이 바뀌면 첫 쪽으로 간다(쪽만 바꿀 때는 page 를 함께 넘긴다) */
  const update = useCallback(
    (patch: Partial<IssueFilters>) =>
      setParams((current) => writeIssueFilters({ ...readIssueFilters(current), page: 1, ...patch }), { replace: true }),
    [setParams],
  );

  const data = query.data;
  const filtered = hasIssueFilters(filters);
  const pages = data ? Math.max(1, Math.ceil(data.total / data.size)) : 1;

  let body: ReactNode;
  if (query.isPending) {
    body = <LoadingState />;
  } else if (query.isError) {
    body = <ErrorState error={query.error} onRetry={() => query.refetch()} />;
  } else if (data && data.items.length === 0) {
    body = filtered ? (
      <EmptyState
        title={t('list.noMatchTitle')}
        description={t('list.noMatchBody')}
        action={
          <Button variant="outlined" startIcon={<FilterAltOffRounded />} onClick={() => setParams(new URLSearchParams(), { replace: true })}>
            {t('list.clear')}
          </Button>
        }
      />
    ) : (
      <EmptyState
        icon={<TaskAltRounded />}
        title={t('list.emptyTitle')}
        description={workspaceScope ? t('list.emptyBodyWorkspace') : canCreate ? t('list.emptyBody') : t('list.emptyBodyReader')}
        action={
          canCreate &&
          project && (
            <Button component={RouterLink} to={newIssuePath(slug, project.key)} variant="contained" startIcon={<AddRounded />}>
              {t('create.title')}
            </Button>
          )
        }
      />
    );
  } else if (data) {
    body = desktop ? (
      <IssueTable
        slug={slug}
        showProject={workspaceScope}
        items={data.items}
        sort={filters.sort}
        onSort={(key) => update({ sort: toggleIssueSort(filters.sort, key), page: filters.page })}
      />
    ) : (
      <IssueCards slug={slug} showProject={workspaceScope} items={data.items} />
    );
  }

  return (
    <Card>
      <FilterBar
        slug={slug}
        projectKey={project?.key ?? null}
        filters={filters}
        onChange={update}
        onClear={() => setParams(new URLSearchParams(), { replace: true })}
      />
      {/* 조건을 바꿔 다시 읽는 동안 앞 목록을 그대로 두고 막대로만 알린다 */}
      <Box sx={{ height: 2 }}>{query.isFetching && !query.isPending && <LinearProgress sx={{ height: 2 }} />}</Box>
      {jumpNumber !== null && (
        // 번호는 워크스페이스 안에서 하나다 — 찾는 글이 번호면 그 일감으로 바로 간다(읽을 수 없으면 그 화면이 "찾을 수 없음")
        <Stack direction="row" spacing={1} useFlexGap sx={{ alignItems: 'center', flexWrap: 'wrap', px: 2, py: 1.25, borderBottom: 1, borderColor: 'divider', bgcolor: 'action.hover' }}>
          <Button component={RouterLink} to={issuePath(slug, jumpNumber)} variant="outlined" size="small" endIcon={<ArrowForwardRounded />}>
            {t('list.jumpTo', { number: jumpNumber })}
          </Button>
          <Typography variant="caption" sx={{ color: 'text.secondary' }}>
            {t('list.jumpToHelp')}
          </Typography>
        </Stack>
      )}
      {body}
      {data && data.total > 0 && (
        <Stack
          direction={{ xs: 'column', sm: 'row' }}
          spacing={1.5}
          sx={{ alignItems: 'center', justifyContent: 'space-between', px: 2, py: 1.5, borderTop: 1, borderColor: 'divider' }}
        >
          <Typography variant="caption" role="status" sx={{ color: 'text.secondary', fontVariantNumeric: 'tabular-nums' }}>
            {t('list.total', { count: data.total })}
          </Typography>
          {pages > 1 && (
            <Pagination
              count={pages}
              page={Math.min(filters.page, pages)}
              onChange={(_, page) => update({ page })}
              size="small"
              siblingCount={desktop ? 1 : 0}
            />
          )}
        </Stack>
      )}
    </Card>
  );
}

/**
 * 조건 막대 — 칸은 창 너비에 따라 줄을 바꾼다. 찾는 글은 치고 잠깐 멈추면 반영한다.
 * projectKey 가 null 이면 워크스페이스 목록 — 프로젝트 거르기 칸이 있고, 유형 · 세션 · 모듈 후보는 읽을 수 있는 프로젝트 모두에서 온다.
 */
function FilterBar({ slug, projectKey, filters, onChange, onClear }: {
  slug: string;
  projectKey: string | null;
  filters: IssueFilters;
  onChange: (patch: Partial<IssueFilters>) => void;
  onClear: () => void;
}) {
  const { t } = useTranslation('issue');
  const labels = useLabels();
  const typeName = useIssueTypeName();
  const workspaceScope = projectKey === null;
  const types = useIssueTypesQuery(slug, projectKey);
  const sessions = useAgentSessionsQuery(slug, projectKey);
  // 모듈 — 쓰지 않는 모듈로도 거른다(옛 일감을 찾는다). 이름은 모듈 목록에서, 모르면 key 그대로.
  // 워크스페이스 목록은 읽을 수 있는 프로젝트들의 모듈 key(같은 key 는 하나로)
  const projectModules = useProjectModulesQuery(slug, projectKey ?? '', !workspaceScope);
  const workspaceModules = useWorkspaceModulesQuery(slug, workspaceScope);
  const moduleOptions: { key: string; name: string; active: boolean }[] = workspaceScope
    ? (workspaceModules.data ?? []).map((m) => ({ key: m.key, name: m.name, active: true }))
    : (projectModules.data ?? []);
  const moduleName = (key: string) => moduleOptions.find((m) => m.key === key)?.name ?? key;
  // 프로젝트 거르기(워크스페이스 목록만) — 내가 볼 수 있는 프로젝트. 이름은 목록에서, 모르면 key 그대로
  const projects = useProjectsQuery(workspaceScope ? slug : undefined);
  const projectName = (key: string) => projects.data?.find((p) => p.key === key)?.name ?? key;
  // 고를 세션 — 이 프로젝트에서 쓰인 이름들. 주소로 받은 이름이 목록에 없어도(지운 일감뿐 · 대소문자 다름) 고른 값으로 보인다
  const sessionNames = (sessions.data ?? []).map((s) => s.name);
  const sessionValue = filters.agentSession ?? '';
  const sessionOptions =
    sessionValue && sessionValue !== 'none' && !sessionNames.includes(sessionValue) ? [sessionValue, ...sessionNames] : sessionNames;

  // 찾는 글 — 치는 동안에는 이 칸에만 두고, 멈추면 주소로 보낸다. 주소가 밖에서 바뀌면(뒤로 가기 · 조건 지우기) 칸도 따라간다
  const [search, setSearch] = useState(filters.q);
  const [shownQ, setShownQ] = useState(filters.q);
  if (filters.q !== shownQ) {
    setShownQ(filters.q);
    setSearch(filters.q);
  }
  const settled = useDebounced(search, 300);
  useEffect(() => {
    // 칸의 글이 멈춘 뒤(settled === search)에만 — 주소가 밖에서 바뀐 직후의 옛 값은 보내지 않는다
    if (settled === search && settled.trim() !== filters.q) {
      onChange({ q: settled });
    }
  }, [settled, search, filters.q, onChange]);

  const assignee = filters.assignee === 'me' || filters.assignee === 'none' ? filters.assignee : '';
  const manager = filters.manager === 'me' ? 'me' : '';

  return (
    <Box
      sx={{
        p: 2,
        borderBottom: 1,
        borderColor: 'divider',
        display: 'grid',
        gap: 1.5,
        gridTemplateColumns: 'repeat(auto-fill, minmax(150px, 1fr))',
        alignItems: 'center',
      }}
    >
      <TextField
        size="small"
        type="search"
        value={search}
        onChange={(e) => setSearch(e.target.value)}
        placeholder={t('list.searchPlaceholder')}
        sx={{ gridColumn: { xs: '1 / -1', sm: 'span 2' } }}
        slotProps={{
          htmlInput: { 'aria-label': t('list.searchLabel'), maxLength: 200 },
          input: {
            startAdornment: (
              <InputAdornment position="start">
                <SearchRounded fontSize="small" />
              </InputAdornment>
            ),
          },
        }}
      />
      {workspaceScope && (
        <TextField
          select
          size="small"
          label={t('list.projectLabel')}
          value={filters.project}
          onChange={(e) => {
            const value = e.target.value as unknown as string[] | string;
            onChange({ project: typeof value === 'string' ? value.split(',').filter(Boolean) : value });
          }}
          slotProps={{
            select: {
              multiple: true,
              displayEmpty: true,
              renderValue: (selected) =>
                (selected as string[]).length === 0 ? t('list.projectAll') : (selected as string[]).map((key) => projectName(key)).join(', '),
            },
            inputLabel: { shrink: true },
          }}
        >
          {(projects.data ?? []).map((p) => (
            <MenuItem key={p.key} value={p.key} dense>
              <Checkbox size="small" checked={filters.project.includes(p.key)} sx={{ p: 0.5, mr: 1 }} />
              <ListItemText primary={p.name} />
            </MenuItem>
          ))}
        </TextField>
      )}
      <TextField
        select
        size="small"
        label={t('list.statusLabel')}
        value={filters.status}
        onChange={(e) => {
          const value = e.target.value as unknown as IssueStatus[] | string;
          onChange({ status: (typeof value === 'string' ? value.split(',') : value) as IssueStatus[] });
        }}
        slotProps={{
          select: {
            multiple: true,
            displayEmpty: true,
            renderValue: (selected) =>
              (selected as IssueStatus[]).length === 0
                ? t('list.statusAll')
                : (selected as IssueStatus[]).map((s) => labels.issueStatus(s)).join(', '),
          },
          inputLabel: { shrink: true },
        }}
      >
        {issueStatuses.map((status) => (
          <MenuItem key={status} value={status} dense>
            <Checkbox size="small" checked={filters.status.includes(status)} sx={{ p: 0.5, mr: 1 }} />
            <ListItemText primary={labels.issueStatus(status)} />
          </MenuItem>
        ))}
      </TextField>
      {/* 우선순위 — 상태와 같은 여럿 고르기(2026-11-26, 일감 #302) */}
      <TextField
        select
        size="small"
        label={t('list.priorityLabel')}
        value={filters.priority}
        onChange={(e) => {
          const value = e.target.value as unknown as IssuePriority[] | string;
          onChange({ priority: (typeof value === 'string' ? value.split(',') : value) as IssuePriority[] });
        }}
        slotProps={{
          select: {
            multiple: true,
            displayEmpty: true,
            renderValue: (selected) =>
              (selected as IssuePriority[]).length === 0
                ? t('list.priorityAll')
                : (selected as IssuePriority[]).map((p) => labels.issuePriority(p)).join(', '),
          },
          inputLabel: { shrink: true },
        }}
      >
        {issuePriorities.map((priority) => (
          <MenuItem key={priority} value={priority} dense>
            <Checkbox size="small" checked={filters.priority.includes(priority)} sx={{ p: 0.5, mr: 1 }} />
            <ListItemText primary={labels.issuePriority(priority)} />
          </MenuItem>
        ))}
      </TextField>
      <TextField
        select
        size="small"
        label={t('list.typeLabel')}
        value={filters.type}
        onChange={(e) => {
          const value = e.target.value as unknown as string[] | string;
          onChange({ type: typeof value === 'string' ? value.split(',') : value });
        }}
        slotProps={{
          select: {
            multiple: true,
            displayEmpty: true,
            renderValue: (selected) =>
              (selected as string[]).length === 0 ? t('list.typeAll') : (selected as string[]).map((key) => typeName(key)).join(', '),
          },
          inputLabel: { shrink: true },
        }}
      >
        {(types.data ?? []).map((type) => (
          <MenuItem key={type.key} value={type.key} dense>
            <Checkbox size="small" checked={filters.type.includes(type.key)} sx={{ p: 0.5, mr: 1 }} />
            <ListItemText primary={typeName(type.key, type.name)} />
          </MenuItem>
        ))}
      </TextField>
      <TextField
        select
        size="small"
        label={t('list.assigneeLabel')}
        value={assignee}
        onChange={(e) => onChange({ assignee: e.target.value || null })}
        slotProps={{ select: { displayEmpty: true }, inputLabel: { shrink: true } }}
      >
        <MenuItem value="">{t('list.anyone')}</MenuItem>
        <MenuItem value="me">{t('list.me')}</MenuItem>
        <MenuItem value="none">{t('list.unassigned')}</MenuItem>
      </TextField>
      <TextField
        select
        size="small"
        label={t('list.managerLabel')}
        value={manager}
        onChange={(e) => onChange({ manager: e.target.value || null })}
        slotProps={{ select: { displayEmpty: true }, inputLabel: { shrink: true } }}
      >
        <MenuItem value="">{t('list.anyone')}</MenuItem>
        <MenuItem value="me">{t('list.me')}</MenuItem>
      </TextField>
      {moduleOptions.length > 0 && (
        <TextField
          select
          size="small"
          label={t('list.moduleLabel')}
          value={filters.module}
          onChange={(e) => {
            const value = e.target.value as unknown as string[] | string;
            onChange({ module: typeof value === 'string' ? value.split(',').filter(Boolean) : value });
          }}
          slotProps={{
            select: {
              multiple: true,
              displayEmpty: true,
              renderValue: (selected) =>
                (selected as string[]).length === 0 ? t('list.moduleAll') : (selected as string[]).map((key) => moduleName(key)).join(', '),
            },
            inputLabel: { shrink: true },
          }}
        >
          {moduleOptions.map((m) => (
            <MenuItem key={m.key} value={m.key} dense>
              <Checkbox size="small" checked={filters.module.includes(m.key)} sx={{ p: 0.5, mr: 1 }} />
              <ListItemText primary={m.active ? m.name : t('modules.inactiveName', { name: m.name })} />
            </MenuItem>
          ))}
        </TextField>
      )}
      <TextField
        select
        size="small"
        label={t('list.agentSessionLabel')}
        value={sessionValue}
        onChange={(e) => onChange({ agentSession: e.target.value || null })}
        slotProps={{
          select: { displayEmpty: true, renderValue: (selected) => (selected === '' ? t('list.anySession') : selected === 'none' ? t('list.noSession') : String(selected)) },
          inputLabel: { shrink: true },
        }}
      >
        <MenuItem value="">{t('list.anySession')}</MenuItem>
        <MenuItem value="none">{t('list.noSession')}</MenuItem>
        {sessionOptions.map((name) => (
          <MenuItem key={name} value={name} sx={{ overflowWrap: 'anywhere' }}>
            {name}
          </MenuItem>
        ))}
      </TextField>
      <TextField
        select
        size="small"
        label={t('list.sortLabel')}
        value={filters.sort}
        onChange={(e) => onChange({ sort: e.target.value as IssueFilters['sort'] })}
        slotProps={{ inputLabel: { shrink: true } }}
      >
        {issueSorts.map((sort) => (
          <MenuItem key={sort} value={sort}>
            {t(`list.sort.${sort}`)}
          </MenuItem>
        ))}
      </TextField>
      <Stack direction="row" spacing={1} sx={{ alignItems: 'center', gridColumn: { xs: '1 / -1', sm: 'span 2' }, minWidth: 0 }}>
        <FormControlLabel
          control={<Checkbox size="small" checked={filters.top} onChange={(e) => onChange({ top: e.target.checked })} />}
          label={<Typography variant="body2">{t('list.topOnly')}</Typography>}
          sx={{ mr: 'auto' }}
        />
        {hasIssueFilters(filters) && (
          <Button
            size="small"
            color="inherit"
            startIcon={<FilterAltOffRounded />}
            onClick={() => {
              setSearch('');
              onClear();
            }}
            sx={{ color: 'text.secondary', flexShrink: 0 }}
          >
            {t('list.clear')}
          </Button>
        )}
      </Stack>
    </Box>
  );
}

/** 넓은 화면의 표 — 덜 중요한 열은 좁으면 숨긴다. 첫 열(번호 · 제목)이 이름 노릇을 한다. 워크스페이스 목록은 프로젝트 열을 더한다 */
function IssueTable({ slug, showProject, items, sort, onSort }: {
  slug: string;
  showProject: boolean;
  items: IssueSummary[];
  sort: IssueFilters['sort'];
  onSort: (key: IssueSortKey) => void;
}) {
  const { t } = useTranslation('issue');
  const typeName = useIssueTypeName();
  const wideCol = { display: { xs: 'none', lg: 'table-cell' } } as const;
  const sortable = (key: IssueSortKey, label: string, sx?: object) => {
    const direction = sortDirection(sort, key);
    return (
      <TableCell sx={{ whiteSpace: 'nowrap', ...sx }} sortDirection={direction}>
        <TableSortLabel active={direction !== false} direction={direction || 'asc'} onClick={() => onSort(key)}>
          {label}
        </TableSortLabel>
      </TableCell>
    );
  };
  return (
    <TableContainer>
      <Table size="small">
        <TableHead>
          <TableRow>
            {sortable('number', t('fields.number'), { width: 72 })}
            <TableCell>{t('fields.title')}</TableCell>
            {showProject && <TableCell sx={{ whiteSpace: 'nowrap' }}>{t('fields.project')}</TableCell>}
            <TableCell sx={{ whiteSpace: 'nowrap' }}>{t('fields.type')}</TableCell>
            <TableCell sx={{ whiteSpace: 'nowrap' }}>{t('fields.status')}</TableCell>
            {sortable('priority', t('fields.priority'))}
            <TableCell sx={{ whiteSpace: 'nowrap' }}>{t('fields.assignee')}</TableCell>
            <TableCell sx={{ whiteSpace: 'nowrap', ...wideCol }}>{t('fields.manager')}</TableCell>
            {sortable('due', t('fields.dueDate'))}
            {sortable('planned', t('fields.plannedEndDate'), wideCol)}
            {sortable('updated', t('fields.updatedAt'), wideCol)}
          </TableRow>
        </TableHead>
        <TableBody>
          {items.map((item) => {
            const to = issuePath(slug, item.number);
            return (
              <TableRow key={item.number} hover>
                <TableCell sx={{ fontFamily: 'monospace', color: 'text.secondary', whiteSpace: 'nowrap' }}>
                  <Link component={RouterLink} to={to} underline="hover" color="inherit">
                    #{item.number}
                  </Link>
                </TableCell>
                <TableCell sx={{ minWidth: 200 }}>
                  <Link component={RouterLink} to={to} underline="hover" color="text.primary" sx={{ fontWeight: 600, overflowWrap: 'anywhere' }}>
                    {item.title}
                  </Link>
                  {item.parent !== null && (
                    <Typography variant="caption" sx={{ display: 'block', color: 'text.secondary' }}>
                      {t('list.parentOf', { number: item.parent })}
                    </Typography>
                  )}
                  <IssueTags agentSession={item.agentSession} modules={item.modules} />
                </TableCell>
                {showProject && (
                  <TableCell sx={{ maxWidth: 180 }}>
                    <ProjectLink slug={slug} project={item.project} />
                  </TableCell>
                )}
                <TableCell sx={{ whiteSpace: 'nowrap', color: 'text.secondary' }}>{typeName(item.type)}</TableCell>
                <TableCell>
                  <IssueStatusChip status={item.status} />
                </TableCell>
                <TableCell sx={{ whiteSpace: 'nowrap' }}>
                  <IssuePriorityChip priority={item.priority} normal />
                </TableCell>
                <PersonCell name={personName(item.assignee)} />
                <PersonCell name={personName(item.manager)} sx={wideCol} />
                <DateCell value={item.dueDate} note={item.overdue ? t('list.overdue') : undefined} />
                <DateCell value={item.plannedEndDate} sx={wideCol} />
                <TableCell sx={{ whiteSpace: 'nowrap', color: 'text.secondary', fontVariantNumeric: 'tabular-nums', ...wideCol }}>
                  {fromNow(item.updatedAt)}
                  <Typography component="time" dateTime={item.updatedAt} variant="caption" sx={{ display: 'block', color: 'text.secondary' }}>
                    {dateTimeMs(item.updatedAt)}
                  </Typography>
                </TableCell>
              </TableRow>
            );
          })}
        </TableBody>
      </Table>
    </TableContainer>
  );
}

function PersonCell({ name, sx }: { name: string | null; sx?: object }) {
  const { t } = useTranslation('issue');
  return (
    <TableCell sx={{ whiteSpace: 'nowrap', maxWidth: 160, overflow: 'hidden', textOverflow: 'ellipsis', color: name ? 'text.primary' : 'text.secondary', ...sx }} title={name ?? undefined}>
      {name ?? t('form.nobody')}
    </TableCell>
  );
}

/** 날짜 칸. note(예: 기한 지남)는 색만이 아니라 글로도 보인다 */
function DateCell({ value, sx, note }: { value: string | null; sx?: object; note?: string }) {
  return (
    <TableCell sx={{ whiteSpace: 'nowrap', fontVariantNumeric: 'tabular-nums', color: value ? 'text.primary' : 'text.secondary', ...sx }}>
      {date(value)}
      {note && (
        <Typography component="span" variant="caption" sx={{ display: 'block', color: 'warning.main', fontWeight: 600 }}>
          {note}
        </Typography>
      )}
    </TableCell>
  );
}

/** 좁은 화면(390px)의 카드 목록 — 줄 전체가 그 일감으로 가는 링크다. 워크스페이스 목록은 번호 줄에 프로젝트 이름을 적는다 */
function IssueCards({ slug, showProject, items }: { slug: string; showProject: boolean; items: IssueSummary[] }) {
  const { t } = useTranslation('issue');
  const typeName = useIssueTypeName();
  return (
    <List disablePadding>
      {items.map((item, index) => {
        const assignee = personName(item.assignee);
        return (
          <ListItemButton
            key={item.number}
            component={RouterLink}
            to={issuePath(slug, item.number)}
            divider={index < items.length - 1}
            sx={{ display: 'block', py: 1.5, px: 2 }}
          >
            <Stack direction="row" spacing={1} useFlexGap sx={{ alignItems: 'center', flexWrap: 'wrap', mb: 0.5 }}>
              <Typography variant="caption" sx={{ fontFamily: 'monospace', color: 'text.secondary' }}>
                #{item.number}
              </Typography>
              {showProject && item.project && (
                <Typography variant="caption" sx={{ color: 'text.primary', fontWeight: 600, overflowWrap: 'anywhere' }}>
                  {item.project.name}
                </Typography>
              )}
              <Typography variant="caption" sx={{ color: 'text.secondary' }}>
                {typeName(item.type)}
              </Typography>
              <IssueStatusChip status={item.status} />
              {/* 좁은 카드에는 보통을 적지 않는다 — 낮음 · 높음 · 긴급만 */}
              <IssuePriorityChip priority={item.priority} />
            </Stack>
            <Typography sx={{ fontWeight: 600, overflowWrap: 'anywhere' }}>{item.title}</Typography>
            <Stack direction="row" spacing={1.5} useFlexGap sx={{ mt: 0.5, flexWrap: 'wrap' }}>
              <Typography variant="caption" sx={{ color: 'text.secondary' }}>
                {t('fields.assignee')} {assignee ?? t('form.nobody')}
              </Typography>
              {item.dueDate && (
                <Typography variant="caption" sx={{ color: 'text.secondary' }}>
                  {t('list.due', { date: item.dueDate })}
                  {item.overdue && ` · ${t('list.overdue')}`}
                </Typography>
              )}
              {item.parent !== null && (
                <Typography variant="caption" sx={{ color: 'text.secondary' }}>
                  {t('list.parentOf', { number: item.parent })}
                </Typography>
              )}
            </Stack>
            <IssueTags agentSession={item.agentSession} modules={item.modules} />
          </ListItemButton>
        );
      })}
    </List>
  );
}

/** 프로젝트 칸 — 이름(누르면 그 프로젝트의 일감 목록)과 주소 이름 */
function ProjectLink({ slug, project }: { slug: string; project: IssueSummary['project'] | undefined }) {
  if (!project) {
    return null;
  }
  return (
    <>
      <Link
        component={RouterLink}
        to={projectPath(slug, project.key)}
        underline="hover"
        color="text.primary"
        variant="body2"
        title={project.name}
        sx={{ display: 'block', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}
      >
        {project.name}
      </Link>
      {/* 이름이 key 와 같으면 key 를 다시 보이지 않는다 */}
      {project.key !== project.name && (
        <Typography variant="caption" component="div" noWrap sx={{ color: 'text.secondary', fontFamily: 'monospace' }}>
          {project.key}
        </Typography>
      )}
    </>
  );
}
