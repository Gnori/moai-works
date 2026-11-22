import AssignmentRounded from '@mui/icons-material/AssignmentRounded';
import LockRounded from '@mui/icons-material/LockRounded';
import MenuRounded from '@mui/icons-material/MenuRounded';
import Box from '@mui/material/Box';
import Drawer from '@mui/material/Drawer';
import IconButton from '@mui/material/IconButton';
import List from '@mui/material/List';
import ListItemButton from '@mui/material/ListItemButton';
import ListItemIcon from '@mui/material/ListItemIcon';
import ListItemText from '@mui/material/ListItemText';
import ListSubheader from '@mui/material/ListSubheader';
import Stack from '@mui/material/Stack';
import useMediaQuery from '@mui/material/useMediaQuery';
import { useTheme } from '@mui/material/styles';
import { Suspense, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { NavLink, Outlet, useMatch } from 'react-router';

import { platformRoutes, projectPath, projectsPath, routeAllowed, workspacePath, workspaceRoutes, type NavMeta } from '@/app/routes';
import { useMe } from '@/features/auth/session';
import { useProjectsQuery } from '@/features/project/queries';
import { useWorkspaceQuery } from '@/features/workspace/workspace-context';
import { Brand } from '@/shared/ui/Brand';
import { LoadingState } from '@/shared/ui/States';
import { layout } from '@/theme/tokens';

import { AccountMenu } from './AccountMenu';
import { WorkspaceSwitcher } from './WorkspaceSwitcher';

interface NavItem {
  to: string;
  meta: NavMeta;
}

function NavSection({ title, items, onNavigate }: { title: string; items: NavItem[]; onNavigate?: () => void }) {
  const { t } = useTranslation();
  if (items.length === 0) {
    return null;
  }
  return (
    <List
      dense
      subheader={
        <ListSubheader
          disableSticky
          sx={{ bgcolor: 'transparent', lineHeight: '28px', fontSize: 11, fontWeight: 700, letterSpacing: '0.06em' }}
        >
          {title}
        </ListSubheader>
      }
      sx={{ px: 1 }}
    >
      {items.map((item) => (
        <ListItemButton
          key={item.to}
          component={NavLink}
          to={item.to}
          end={item.meta.end}
          onClick={onNavigate}
          sx={{
            borderRadius: 2,
            mb: 0.25,
            color: 'text.secondary',
            '&.active': {
              bgcolor: 'action.selected',
              color: 'primary.main',
              '& .MuiListItemIcon-root': { color: 'primary.main' },
            },
          }}
        >
          <ListItemIcon sx={{ minWidth: 34, color: 'inherit' }}>{item.meta.icon}</ListItemIcon>
          <ListItemText primary={t(`nav.${item.meta.label}`)} slotProps={{ primary: { sx: { fontWeight: 600, fontSize: 14 } } }} />
        </ListItemButton>
      ))}
    </List>
  );
}

/** 사이드바에 바로 보이는 프로젝트 수 — 더 있으면 "프로젝트 모두 보기"로 간다 */
const SIDEBAR_PROJECTS = 8;

/** 이 워크스페이스에서 내가 읽을 수 있는 프로젝트로 바로 가는 목록. 프로젝트가 없으면 그리지 않는다 (moai-wiki 의 위키 목록 자리). */
function ProjectNavSection({ slug, onNavigate }: { slug: string; onNavigate?: () => void }) {
  const { t } = useTranslation(['common', 'workspace']);
  const projects = useProjectsQuery(slug);
  if (!projects.data || projects.data.length === 0) {
    return null;
  }
  const itemSx = {
    borderRadius: 2,
    mb: 0.25,
    color: 'text.secondary',
    '&.active': { bgcolor: 'action.selected', color: 'primary.main', '& .MuiListItemIcon-root': { color: 'primary.main' } },
  };
  return (
    <List
      dense
      subheader={
        <ListSubheader
          disableSticky
          sx={{ bgcolor: 'transparent', lineHeight: '28px', fontSize: 11, fontWeight: 700, letterSpacing: '0.06em' }}
        >
          {t('nav.sectionProjects')}
        </ListSubheader>
      }
      sx={{ px: 1, mt: 1 }}
    >
      {projects.data.slice(0, SIDEBAR_PROJECTS).map((project) => (
        <ListItemButton key={project.id} component={NavLink} to={projectPath(slug, project.key)} onClick={onNavigate} sx={itemSx}>
          <ListItemIcon sx={{ minWidth: 34, color: 'inherit' }}>
            {project.visibility === 'RESTRICTED' ? <LockRounded fontSize="small" /> : <AssignmentRounded fontSize="small" />}
          </ListItemIcon>
          <ListItemText
            primary={project.name}
            slotProps={{ primary: { noWrap: true, title: project.name, sx: { fontWeight: 600, fontSize: 14 } } }}
          />
        </ListItemButton>
      ))}
      {projects.data.length > SIDEBAR_PROJECTS && (
        <ListItemButton component={NavLink} to={projectsPath(slug)} end onClick={onNavigate} sx={itemSx}>
          <ListItemText
            primary={t('workspace:projects.sidebarAll')}
            slotProps={{ primary: { sx: { fontSize: 13, pl: '34px' } } }}
          />
        </ListItemButton>
      )}
    </List>
  );
}

/** 사이드바 — 메뉴는 routes.tsx 표에서, 보이는 항목은 서버가 준 내 권한 · 플랫폼 역할로 거른다(표시만). */
function SidebarContent({ onNavigate }: { onNavigate?: () => void }) {
  const { t } = useTranslation();
  const me = useMe();
  const match = useMatch('/w/:slug/*');
  const slug = match?.params.slug ?? me.landingWorkspace;
  const workspace = useWorkspaceQuery(slug ?? undefined);
  const permissions = workspace.data?.myPermissions ?? [];

  const workspaceItems: NavItem[] = slug
    ? workspaceRoutes
        .filter((r) => r.nav && routeAllowed(r, permissions))
        .map((r) => ({ to: workspacePath(slug, r), meta: r.nav as NavMeta }))
    : [];
  const platformItems: NavItem[] = platformRoutes
    .filter((r) => me.platformRoles.includes(r.role))
    .map((r) => ({ to: r.path, meta: r.nav }));

  return (
    <Stack sx={{ height: '100%' }}>
      <Box sx={{ px: 2.5, pt: 2.5, pb: 2 }}>
        <Brand size={30} />
      </Box>
      <Box sx={{ px: 1.5, pb: 1 }}>
        {/* 메뉴가 가리키는 워크스페이스(주소 → 없으면 마지막에 쓴 곳)를 전환 단추에도 똑같이 보인다 */}
        <WorkspaceSwitcher currentSlug={slug} onNavigate={onNavigate} />
      </Box>
      <Box component="nav" aria-label={t('nav.mainMenu')} sx={{ flex: 1, overflowY: 'auto', py: 1 }}>
        <NavSection title={t('nav.sectionWorkspace')} items={workspaceItems} onNavigate={onNavigate} />
        {slug && permissions.includes('workspace.read') && <ProjectNavSection slug={slug} onNavigate={onNavigate} />}
        {platformItems.length > 0 && (
          <Box sx={{ mt: 1 }}>
            <NavSection title={t('nav.sectionPlatform')} items={platformItems} onNavigate={onNavigate} />
          </Box>
        )}
      </Box>
      <Box sx={{ p: 1.5, borderTop: 1, borderColor: 'divider' }}>
        <AccountMenu />
      </Box>
    </Stack>
  );
}

/** 로그인 뒤 모든 화면의 틀 — 넓은 화면은 고정 사이드바, 좁은 화면은 위 막대 + 서랍. 본문은 창 너비를 모두 쓴다. */
export function AppShell() {
  const { t } = useTranslation();
  const theme = useTheme();
  const desktop = useMediaQuery(theme.breakpoints.up('md'));
  const [open, setOpen] = useState(false);

  return (
    <Box sx={{ display: 'flex', minHeight: '100dvh', bgcolor: 'background.default' }}>
      {desktop ? (
        <Box
          component="aside"
          sx={{
            width: layout.sidebarWidth,
            flexShrink: 0,
            position: 'sticky',
            top: 0,
            height: '100dvh',
            borderRight: 1,
            borderColor: 'divider',
            bgcolor: 'background.sidebar',
          }}
        >
          <SidebarContent />
        </Box>
      ) : (
        <>
          <Box
            component="header"
            sx={{
              position: 'fixed',
              insetInline: 0,
              top: 0,
              zIndex: theme.zIndex.appBar,
              height: layout.topbarHeight,
              display: 'flex',
              alignItems: 'center',
              gap: 1,
              px: 1,
              bgcolor: 'background.paper',
              borderBottom: 1,
              borderColor: 'divider',
            }}
          >
            <IconButton aria-label={t('nav.openMenu')} onClick={() => setOpen(true)}>
              <MenuRounded />
            </IconButton>
            <Brand size={26} />
          </Box>
          <Drawer
            open={open}
            onClose={() => setOpen(false)}
            slotProps={{ paper: { sx: { width: layout.sidebarWidth, bgcolor: 'background.sidebar' } } }}
          >
            <SidebarContent onNavigate={() => setOpen(false)} />
          </Drawer>
        </>
      )}
      <Box
        component="main"
        sx={{
          flex: 1,
          minWidth: 0,
          pt: desktop ? 4 : `${layout.topbarHeight + 24}px`,
          pb: 6,
          px: { xs: 2, sm: 3, md: 5 },
        }}
      >
        {/* 화면 코드를 받는 동안 사이드바 · 위쪽 막대는 그대로 둔다 */}
        <Suspense fallback={<LoadingState />}>
          <Outlet />
        </Suspense>
      </Box>
    </Box>
  );
}
