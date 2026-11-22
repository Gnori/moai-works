import AddRounded from '@mui/icons-material/AddRounded';
import ArrowBackRounded from '@mui/icons-material/ArrowBackRounded';
import DeleteOutlineRounded from '@mui/icons-material/DeleteOutlineRounded';
import EditRounded from '@mui/icons-material/EditRounded';
import ExpandMoreRounded from '@mui/icons-material/ExpandMoreRounded';
import MoreVertRounded from '@mui/icons-material/MoreVertRounded';
import SearchOffRounded from '@mui/icons-material/SearchOffRounded';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Breadcrumbs from '@mui/material/Breadcrumbs';
import Button from '@mui/material/Button';
import Chip from '@mui/material/Chip';
import IconButton from '@mui/material/IconButton';
import Link from '@mui/material/Link';
import ListItemIcon from '@mui/material/ListItemIcon';
import Menu from '@mui/material/Menu';
import MenuItem from '@mui/material/MenuItem';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { useQueryClient } from '@tanstack/react-query';
import { useCallback, useState, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { Link as RouterLink, useNavigate, useParams } from 'react-router';
import { toast } from 'sonner';

import { issueEditPath, issuePath, newIssuePath, projectPath } from '@/app/routes';
import { useProject } from '@/features/project/project-context';
import { projectCan } from '@/features/project/queries';
import { useWorkspace } from '@/features/workspace/workspace-context';
import { issueApi } from '@/shared/api/endpoints';
import { errorMessage, isApiError } from '@/shared/api/http';
import { issueStatuses, type IssueDetail, type IssueStatus } from '@/shared/api/types';
import { date, dateTime, fromNow } from '@/shared/lib/format';
import { useLabels } from '@/shared/lib/labels';
import { EmptyState, ErrorState, LoadingState } from '@/shared/ui/States';

import { personName, useIssueTypeName } from './issue-labels';
import { CommentBox, IssueHistory } from './IssueHistory';
import { IssueLinks, IssueRefRow } from './IssueLinks';
import { IssueStatusChip } from './IssueStatusChip';
import { MarkdownView } from './MarkdownView';
import { parseIssueNumber, storeIssue, useIssueQuery } from './queries';
import { RemoveIssueDialog } from './RemoveIssueDialog';

/**
 * 일감 보기 — 머리(#번호 · 유형 · 상태 · 제목), 속성(관리자 · 담당자 · 날짜 다섯 · 상위 일감), 설명, 하위 일감, 연결 일감, 이력 · 댓글.
 * moai-wiki 의 페이지 보기 자리다. 단추는 서버가 준 이 프로젝트의 내 권한(myPermissions)으로 보인다(표시만 — 판정은 api).
 * 없는 일감 · 지운 일감은 서버가 404 로 답한다.
 */
export function IssueViewPage() {
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
  return <IssueView slug={workspace.slug} issue={query.data} onReload={() => void query.refetch()} />;
}

function IssueView({ slug, issue, onReload }: { slug: string; issue: IssueDetail; onReload: () => void }) {
  const { t } = useTranslation(['issue', 'common']);
  const project = useProject();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const labels = useLabels();
  const typeName = useIssueTypeName();
  const [menu, setMenu] = useState<HTMLElement | null>(null);
  const [statusMenu, setStatusMenu] = useState<HTMLElement | null>(null);
  const [removing, setRemoving] = useState(false);
  const [changing, setChanging] = useState(false);

  const open = project.status === 'ACTIVE';
  const canEdit = open && projectCan(project, 'issue.edit');
  const canComment = open && projectCan(project, 'issue.comment');
  const canDelete = open && projectCan(project, 'issue.delete');
  const canCreate = project.myActions.includes('createIssue');
  const toIssue = useCallback((n: number) => issuePath(slug, project.key, n), [slug, project.key]);

  /** 상태만 바로 바꾼다 — 다른 사람이 먼저 고쳤으면 지금 내용을 다시 읽어 보인다 */
  const changeStatus = async (status: IssueStatus) => {
    setStatusMenu(null);
    if (status === issue.status) {
      return;
    }
    setChanging(true);
    try {
      const saved = await issueApi.update(slug, project.key, issue.number, { rowVersion: issue.rowVersion, status });
      storeIssue(queryClient, slug, project.key, saved);
      toast.success(t('view.statusChanged'));
    } catch (e) {
      if (isApiError(e, 'CONCURRENT_UPDATE')) {
        toast.warning(t('view.conflict'));
        onReload();
      } else {
        toast.error(errorMessage(e));
      }
    } finally {
      setChanging(false);
    }
  };

  const creator = personName(issue.createdBy) ?? t('view.unknownPerson');

  return (
    <Box component="article">
      <Breadcrumbs maxItems={4} itemsBeforeCollapse={1} itemsAfterCollapse={2} sx={{ mb: 1.5, fontSize: 13 }}>
        <Link component={RouterLink} to={projectPath(slug, project.key)} underline="hover" color="inherit">
          {project.name}
        </Link>
        {issue.parent && (
          <Link component={RouterLink} to={toIssue(issue.parent.number)} underline="hover" color="inherit">
            #{issue.parent.number} {issue.parent.title}
          </Link>
        )}
      </Breadcrumbs>

      <Stack
        direction={{ xs: 'column', md: 'row' }}
        spacing={2}
        sx={{ alignItems: 'flex-start', justifyContent: 'space-between', mb: 2.5 }}
      >
        <Box sx={{ minWidth: 0 }}>
          <Stack direction="row" spacing={1} useFlexGap sx={{ alignItems: 'center', flexWrap: 'wrap', mb: 0.75 }}>
            <Typography variant="subtitle2" sx={{ fontFamily: 'monospace', color: 'text.secondary' }}>
              #{issue.number}
            </Typography>
            <Chip size="small" variant="outlined" label={typeName(issue.type)} />
            <IssueStatusChip status={issue.status} />
          </Stack>
          <Typography variant="h4" component="h1" sx={{ overflowWrap: 'anywhere', textWrap: 'balance' }}>
            {issue.title}
          </Typography>
          <Typography variant="caption" sx={{ color: 'text.secondary', display: 'block', mt: 0.75 }} title={dateTime(issue.createdAt)}>
            {t('view.createdMeta', { name: creator, when: fromNow(issue.createdAt) })}
          </Typography>
        </Box>
        <Stack direction="row" spacing={1} useFlexGap sx={{ flexShrink: 0, alignItems: 'center', flexWrap: 'wrap' }}>
          {canEdit && (
            <Button component={RouterLink} to={issueEditPath(slug, project.key, issue.number)} variant="contained" startIcon={<EditRounded />}>
              {t('view.edit')}
            </Button>
          )}
          {canEdit && (
            <Button
              variant="outlined"
              endIcon={<ExpandMoreRounded />}
              onClick={(event) => setStatusMenu(event.currentTarget)}
              loading={changing}
              aria-haspopup="menu"
            >
              {t('view.changeStatus')}
            </Button>
          )}
          {canCreate && (
            <Button component={RouterLink} to={newIssuePath(slug, project.key, issue.number)} color="inherit" startIcon={<AddRounded />}>
              {t('view.addChild')}
            </Button>
          )}
          {canDelete && (
            <>
              <IconButton aria-label={t('view.more')} onClick={(event) => setMenu(event.currentTarget)}>
                <MoreVertRounded />
              </IconButton>
              <Menu anchorEl={menu} open={!!menu} onClose={() => setMenu(null)}>
                <MenuItem
                  onClick={() => {
                    setMenu(null);
                    setRemoving(true);
                  }}
                  sx={{ color: 'error.main' }}
                >
                  <ListItemIcon sx={{ color: 'inherit' }}>
                    <DeleteOutlineRounded fontSize="small" />
                  </ListItemIcon>
                  {t('view.delete')}
                </MenuItem>
              </Menu>
            </>
          )}
          <Menu anchorEl={statusMenu} open={!!statusMenu} onClose={() => setStatusMenu(null)}>
            {issueStatuses.map((status) => (
              <MenuItem key={status} selected={status === issue.status} onClick={() => void changeStatus(status)}>
                {labels.issueStatus(status)}
              </MenuItem>
            ))}
          </Menu>
        </Stack>
      </Stack>

      {!open && (
        <Alert severity="warning" sx={{ mb: 2.5 }}>
          {t('view.archivedBanner')}
        </Alert>
      )}

      <Box sx={{ display: 'grid', gap: { xs: 3, lg: 4 }, gridTemplateColumns: { xs: 'minmax(0, 1fr)', lg: 'minmax(0, 1fr) 300px' }, alignItems: 'start' }}>
        <Box sx={{ order: { xs: 1, lg: 0 }, minWidth: 0 }}>
          <Box component="section" aria-label={t('fields.description')} sx={{ maxWidth: 920 }}>
            {issue.description.trim() ? (
              <MarkdownView content={issue.description} issuePath={toIssue} />
            ) : (
              <Typography variant="body2" sx={{ color: 'text.secondary', py: 1 }}>
                {t('view.emptyDescription')}
              </Typography>
            )}
          </Box>

          <Box component="section" sx={{ mt: 4 }}>
            <Stack direction="row" sx={{ alignItems: 'center', justifyContent: 'space-between', gap: 2, mb: 1 }}>
              <Typography variant="h6" component="h2">
                {t('fields.children')}
              </Typography>
              {canCreate && (
                <Button size="small" component={RouterLink} to={newIssuePath(slug, project.key, issue.number)} startIcon={<AddRounded />}>
                  {t('view.addChild')}
                </Button>
              )}
            </Stack>
            {issue.children.length === 0 ? (
              <Typography variant="body2" sx={{ color: 'text.secondary' }}>
                {t('view.childrenEmpty')}
              </Typography>
            ) : (
              <Paper variant="outlined" sx={{ borderRadius: 2.5, overflow: 'hidden' }}>
                <Box component="ul" sx={{ listStyle: 'none', m: 0, p: 0 }}>
                  {issue.children.map((child, index) => (
                    <IssueRefRow key={child.number} slug={slug} projectKey={project.key} issue={child} divider={index < issue.children.length - 1} />
                  ))}
                </Box>
              </Paper>
            )}
          </Box>

          <IssueLinks slug={slug} projectKey={project.key} issue={issue} canEdit={canEdit} />

          <Box component="section" sx={{ mt: 4 }}>
            <Typography variant="h6" component="h2" sx={{ mb: 1 }}>
              {t('view.history')}
            </Typography>
            <IssueHistory issue={issue} issuePath={toIssue} />
            {canComment && <CommentBox slug={slug} projectKey={project.key} issue={issue} issuePath={toIssue} />}
          </Box>
        </Box>

        <Paper
          variant="outlined"
          component="aside"
          aria-label={t('form.fieldsTitle')}
          sx={{ order: { xs: 0, lg: 1 }, borderRadius: 2.5, p: 2, position: { lg: 'sticky' }, top: { lg: 32 } }}
        >
          <Box component="dl" sx={{ m: 0, display: 'grid', gridTemplateColumns: { xs: 'auto minmax(0, 1fr)' }, columnGap: 2, rowGap: 1.25 }}>
            <Field label={t('fields.status')}>
              <IssueStatusChip status={issue.status} />
            </Field>
            <Field label={t('fields.type')}>{typeName(issue.type)}</Field>
            <Field label={t('fields.manager')}>{personName(issue.manager) ?? <Muted>{t('form.nobody')}</Muted>}</Field>
            <Field label={t('fields.assignee')}>{personName(issue.assignee) ?? <Muted>{t('form.nobody')}</Muted>}</Field>
            <Field label={t('fields.parent')}>
              {issue.parent ? (
                <Link component={RouterLink} to={toIssue(issue.parent.number)} underline="hover" sx={{ overflowWrap: 'anywhere' }}>
                  #{issue.parent.number} {issue.parent.title}
                </Link>
              ) : (
                <Muted>{t('form.nobody')}</Muted>
              )}
            </Field>
            <Field label={t('fields.dueDate')}>
              <DateValue value={issue.dueDate} />
              {issue.overdue && (
                <Typography component="span" variant="caption" sx={{ ml: 1, color: 'warning.main', fontWeight: 600 }}>
                  {t('list.overdue')}
                </Typography>
              )}
            </Field>
            <Field label={t('fields.startDate')}>
              <DateValue value={issue.startDate} />
            </Field>
            <Field label={t('fields.plannedEndDate')}>
              <DateValue value={issue.plannedEndDate} />
            </Field>
            <Field label={t('fields.completedOn')}>
              <DateValue value={issue.completedOn} />
            </Field>
            <Field label={t('fields.closedOn')}>
              <DateValue value={issue.closedOn} />
            </Field>
            <Field label={t('fields.updatedAt')}>
              <span title={dateTime(issue.updatedAt)}>{fromNow(issue.updatedAt)}</span>
            </Field>
          </Box>
        </Paper>
      </Box>

      {removing && (
        <RemoveIssueDialog
          slug={slug}
          projectKey={project.key}
          issue={issue}
          onClose={() => setRemoving(false)}
          onRemoved={() =>
            void navigate(issue.parent ? toIssue(issue.parent.number) : projectPath(slug, project.key), { replace: true })
          }
        />
      )}
    </Box>
  );
}

/** 속성 한 줄 — 이름(dt)과 값(dd) */
function Field({ label, children }: { label: string; children: ReactNode }) {
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

function DateValue({ value }: { value: string | null }) {
  return value ? <>{date(value)}</> : <Muted>—</Muted>;
}
