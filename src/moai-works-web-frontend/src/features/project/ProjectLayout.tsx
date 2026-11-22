import AddRounded from '@mui/icons-material/AddRounded';
import ArrowBackRounded from '@mui/icons-material/ArrowBackRounded';
import FormatListBulletedRounded from '@mui/icons-material/FormatListBulletedRounded';
import GroupsRounded from '@mui/icons-material/GroupsRounded';
import KeyboardDoubleArrowLeftRounded from '@mui/icons-material/KeyboardDoubleArrowLeftRounded';
import KeyboardDoubleArrowRightRounded from '@mui/icons-material/KeyboardDoubleArrowRightRounded';
import SearchOffRounded from '@mui/icons-material/SearchOffRounded';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import IconButton from '@mui/material/IconButton';
import List from '@mui/material/List';
import ListItemButton from '@mui/material/ListItemButton';
import ListItemIcon from '@mui/material/ListItemIcon';
import ListItemText from '@mui/material/ListItemText';
import Stack from '@mui/material/Stack';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import useMediaQuery from '@mui/material/useMediaQuery';
import { Suspense, useState, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { Link as RouterLink, Outlet, useMatch, useParams } from 'react-router';

import { newIssuePath, projectMembersPath, projectPath, projectsPath } from '@/app/routes';
import { useWorkspace } from '@/features/workspace/workspace-context';
import { isApiError } from '@/shared/api/http';
import type { ProjectDetail } from '@/shared/api/types';
import { readSetting, storageKeys, writeSetting } from '@/shared/lib/storage';
import { EmptyState, ErrorState, LoadingState } from '@/shared/ui/States';

import { ProjectVisibilityChip } from './ProjectCards';
import { ProjectProvider } from './project-context';
import { useProjectQuery } from './queries';

/** 이 너비부터 프로젝트 메뉴를 본문 곁에 붙여 둔다. 더 좁으면 본문 위에 한 줄로 둔다 */
const SIDE_BY_SIDE = '(min-width:1280px)';
const NAV_WIDTH = 240;

type Section = 'issues' | 'members';

/**
 * 프로젝트 화면의 틀 — 프로젝트를 읽어(독자가 아니면 서버가 404) 문맥으로 내려주고, 프로젝트 메뉴(일감 · 멤버 · 새 일감)를 곁에 둔다.
 * moai-wiki 의 WikiLayout 자리다: 넓은 화면에서는 메뉴가 본문 왼쪽에 붙고(접을 수 있다), 좁은 화면에서는 본문 위에 한 줄로 둔다.
 * 쓰는 화면(새 일감 · 고치기)에서는 본문이 넓어야 해서 메뉴를 붙이지 않는다.
 */
export function ProjectLayout() {
  const { t } = useTranslation('workspace');
  const { workspace } = useWorkspace();
  const { projectKey } = useParams();
  const query = useProjectQuery(workspace.slug, projectKey);
  const wide = useMediaQuery(SIDE_BY_SIDE);
  const [collapsed, setCollapsed] = useState(() => readSetting(storageKeys.projectNavCollapsed) === '1');

  const base = '/w/:slug/projects/:projectKey';
  const editing = useMatch(`${base}/issues/:number/edit`);
  const creating = useMatch(`${base}/issues/new`);
  const members = useMatch(`${base}/members`);

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
  const docked = wide && !collapsed && !writing;
  const collapse = (value: boolean) => {
    setCollapsed(value);
    writeSetting(storageKeys.projectNavCollapsed, value ? '1' : '0');
  };

  return (
    <ProjectProvider project={project}>
      <Box sx={{ display: 'flex', alignItems: 'flex-start', gap: 4 }}>
        {docked && (
          <Box
            component="aside"
            aria-label={t('projects.nav.label')}
            sx={{
              width: NAV_WIDTH,
              flexShrink: 0,
              position: 'sticky',
              top: 32,
              maxHeight: 'calc(100dvh - 64px)',
              overflowY: 'auto',
              bgcolor: 'background.paper',
              border: 1,
              borderColor: 'divider',
              borderRadius: 2.5,
            }}
          >
            <NavPanel slug={workspace.slug} project={project} section={section} onCollapse={() => collapse(true)} />
          </Box>
        )}
        <Box sx={{ flex: 1, minWidth: 0 }}>
          {!docked && !writing && (
            <Stack
              component="nav"
              aria-label={t('projects.nav.label')}
              direction="row"
              spacing={0.5}
              useFlexGap
              sx={{ mb: 1.5, flexWrap: 'wrap', alignItems: 'center' }}
            >
              {wide && (
                <Tooltip title={t('projects.nav.open')}>
                  <IconButton size="small" onClick={() => collapse(false)} aria-label={t('projects.nav.open')}>
                    <KeyboardDoubleArrowRightRounded fontSize="small" />
                  </IconButton>
                </Tooltip>
              )}
              <InlineLink to={projectPath(workspace.slug, project.key)} active={section === 'issues'} icon={<FormatListBulletedRounded />}>
                {t('projects.nav.issues')}
              </InlineLink>
              <InlineLink to={projectMembersPath(workspace.slug, project.key)} active={section === 'members'} icon={<GroupsRounded />}>
                {t('projects.nav.members')}
              </InlineLink>
            </Stack>
          )}
          {/* 화면 코드를 받는 동안 메뉴는 그대로 둔다 */}
          <Suspense fallback={<LoadingState />}>
            <Outlet />
          </Suspense>
        </Box>
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

function NavPanel({ slug, project, section, onCollapse }: {
  slug: string;
  project: ProjectDetail;
  section: Section;
  /** 곁에 붙은 메뉴를 접는다 */
  onCollapse: () => void;
}) {
  const { t } = useTranslation('workspace');
  const itemSx = {
    borderRadius: 2,
    mb: 0.25,
    color: 'text.secondary',
    '&.Mui-selected': { bgcolor: 'action.selected', color: 'primary.main', '& .MuiListItemIcon-root': { color: 'primary.main' } },
  };
  return (
    <Box>
      <Stack
        direction="row"
        sx={{ alignItems: 'center', gap: 0.5, pl: 2, pr: 1, py: 1, borderBottom: 1, borderColor: 'divider', position: 'sticky', top: 0, bgcolor: 'background.paper', zIndex: 1 }}
      >
        <Typography
          component={RouterLink}
          to={projectPath(slug, project.key)}
          variant="subtitle2"
          noWrap
          title={project.name}
          sx={{ flex: 1, minWidth: 0, color: 'text.primary', textDecoration: 'none', '&:hover': { color: 'primary.main' } }}
        >
          {project.name}
        </Typography>
        {project.myActions.includes('createIssue') && (
          <Tooltip title={t('projects.nav.newIssue')}>
            <IconButton size="small" component={RouterLink} to={newIssuePath(slug, project.key)} aria-label={t('projects.nav.newIssue')}>
              <AddRounded fontSize="small" />
            </IconButton>
          </Tooltip>
        )}
        <Tooltip title={t('projects.nav.hide')}>
          <IconButton size="small" onClick={onCollapse} aria-label={t('projects.nav.hide')}>
            <KeyboardDoubleArrowLeftRounded fontSize="small" />
          </IconButton>
        </Tooltip>
      </Stack>
      <List dense sx={{ p: 0.75 }}>
        <ListItemButton
          component={RouterLink}
          to={projectPath(slug, project.key)}
          selected={section === 'issues'}
          aria-current={section === 'issues' ? 'page' : undefined}
          sx={itemSx}
        >
          <ListItemIcon sx={{ minWidth: 34, color: 'inherit' }}>
            <FormatListBulletedRounded fontSize="small" />
          </ListItemIcon>
          <ListItemText primary={t('projects.nav.issues')} slotProps={{ primary: { sx: { fontWeight: 600, fontSize: 14 } } }} />
        </ListItemButton>
        <ListItemButton
          component={RouterLink}
          to={projectMembersPath(slug, project.key)}
          selected={section === 'members'}
          aria-current={section === 'members' ? 'page' : undefined}
          sx={itemSx}
        >
          <ListItemIcon sx={{ minWidth: 34, color: 'inherit' }}>
            <GroupsRounded fontSize="small" />
          </ListItemIcon>
          <ListItemText primary={t('projects.nav.members')} slotProps={{ primary: { sx: { fontWeight: 600, fontSize: 14 } } }} />
        </ListItemButton>
      </List>
      <Box sx={{ px: 2, pb: 1.5 }}>
        <ProjectVisibilityChip visibility={project.visibility} />
      </Box>
    </Box>
  );
}
