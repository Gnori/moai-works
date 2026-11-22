import AddRounded from '@mui/icons-material/AddRounded';
import ArrowBackRounded from '@mui/icons-material/ArrowBackRounded';
import FormatListBulletedRounded from '@mui/icons-material/FormatListBulletedRounded';
import GroupsRounded from '@mui/icons-material/GroupsRounded';
import SearchOffRounded from '@mui/icons-material/SearchOffRounded';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Divider from '@mui/material/Divider';
import IconButton from '@mui/material/IconButton';
import Stack from '@mui/material/Stack';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import { Suspense, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { Link as RouterLink, Outlet, useMatch, useParams } from 'react-router';

import { newIssuePath, projectMembersPath, projectPath, projectsPath } from '@/app/routes';
import { useWorkspace } from '@/features/workspace/workspace-context';
import { isApiError } from '@/shared/api/http';
import { EmptyState, ErrorState, LoadingState } from '@/shared/ui/States';

import { ProjectProvider } from './project-context';
import { useProjectQuery } from './queries';

type Section = 'issues' | 'members';

/**
 * 프로젝트 화면의 틀 — 프로젝트를 읽어(독자가 아니면 서버가 404) 문맥으로 내려주고, 프로젝트 메뉴(일감 · 멤버 · 새 일감)를 본문 위 한 줄로 둔다.
 * 메뉴를 본문 곁의 한 열로 두지 않는다 — 일감 보기의 가운데(제목 · 내용 · 댓글 · 첨부)가 가장 중요한 정보라 가로 폭을 그쪽에 준다
 * (2026-11-23 사용자: "컨텐츠 화면이 너무 비좁아. 숨길 건 숨기고 보일 건 보이고 가운데 이 부분이 넓게 잘 나와야지").
 * 프로젝트 첫 화면은 큰 제목에 이름 · 새 일감 단추가 있어서 한 줄 메뉴에는 탭만 둔다. 쓰는 화면(새 일감 · 고치기)에는 메뉴를 두지 않는다.
 */
export function ProjectLayout() {
  const { t } = useTranslation('workspace');
  const { workspace } = useWorkspace();
  const { projectKey } = useParams();
  const query = useProjectQuery(workspace.slug, projectKey);

  const base = '/w/:slug/projects/:projectKey';
  const editing = useMatch(`${base}/issues/:number/edit`);
  const creating = useMatch(`${base}/issues/new`);
  const members = useMatch(`${base}/members`);
  const home = useMatch(base);

  if (query.isPending) {
    return <LoadingState />;
  }
  if (query.isError) {
    if (isApiError(query.error) && query.error.status === 404) {
      return (
        <EmptyState
          icon={<SearchOffRounded />}
          title={t('projects.home.notFoundTitle')}
          description={t('projects.home.notFoundBody')}
          action={
            <Button component={RouterLink} to={projectsPath(workspace.slug)} variant="outlined" startIcon={<ArrowBackRounded />}>
              {t('projects.home.backToList')}
            </Button>
          }
        />
      );
    }
    return <ErrorState error={query.error} onRetry={() => query.refetch()} />;
  }

  const project = query.data;
  const section: Section = members ? 'members' : 'issues';
  const writing = !!editing || !!creating;
  const onHome = !!home;

  return (
    <ProjectProvider project={project}>
      {!writing && (
        <Stack
          component="nav"
          aria-label={t('projects.nav.label')}
          direction="row"
          spacing={0.5}
          useFlexGap
          sx={{ mb: 2, flexWrap: 'wrap', alignItems: 'center', minWidth: 0 }}
        >
          {!onHome && (
            <>
              <Typography
                component={RouterLink}
                to={projectPath(workspace.slug, project.key)}
                variant="subtitle2"
                noWrap
                title={project.name}
                sx={{ maxWidth: { xs: 200, sm: 320 }, color: 'text.primary', textDecoration: 'none', fontWeight: 700, '&:hover': { color: 'primary.main' } }}
              >
                {project.name}
              </Typography>
              <Divider orientation="vertical" flexItem sx={{ mx: 0.75, my: 0.5 }} />
            </>
          )}
          <InlineLink to={projectPath(workspace.slug, project.key)} active={section === 'issues'} icon={<FormatListBulletedRounded />}>
            {t('projects.nav.issues')}
          </InlineLink>
          <InlineLink to={projectMembersPath(workspace.slug, project.key)} active={section === 'members'} icon={<GroupsRounded />}>
            {t('projects.nav.members')}
          </InlineLink>
          {!onHome && project.myActions.includes('createIssue') && (
            <Tooltip title={t('projects.nav.newIssue')}>
              <IconButton size="small" component={RouterLink} to={newIssuePath(workspace.slug, project.key)} aria-label={t('projects.nav.newIssue')} sx={{ ml: 'auto' }}>
                <AddRounded fontSize="small" />
              </IconButton>
            </Tooltip>
          )}
        </Stack>
      )}
      {/* 화면 코드를 받는 동안 메뉴는 그대로 둔다 */}
      <Box sx={{ minWidth: 0 }}>
        <Suspense fallback={<LoadingState />}>
          <Outlet />
        </Suspense>
      </Box>
    </ProjectProvider>
  );
}

/** 본문 위 한 줄 메뉴의 항목 — 지금 화면은 주 색과 aria-current 로 알린다(색만으로 전하지 않는다) */
function InlineLink({ to, active, icon, children }: { to: string; active: boolean; icon: ReactNode; children: ReactNode }) {
  return (
    <Button
      size="small"
      color="inherit"
      component={RouterLink}
      to={to}
      startIcon={icon}
      aria-current={active ? 'page' : undefined}
      sx={{ color: active ? 'primary.main' : 'text.secondary', bgcolor: active ? 'action.selected' : undefined, fontWeight: active ? 700 : 600 }}
    >
      {children}
    </Button>
  );
}
