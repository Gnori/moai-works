import AdminPanelSettingsRounded from '@mui/icons-material/AdminPanelSettingsRounded';
import AssignmentRounded from '@mui/icons-material/AssignmentRounded';
import LockRounded from '@mui/icons-material/LockRounded';
import KeyboardDoubleArrowLeftRounded from '@mui/icons-material/KeyboardDoubleArrowLeftRounded';
import KeyboardDoubleArrowRightRounded from '@mui/icons-material/KeyboardDoubleArrowRightRounded';
import MenuRounded from '@mui/icons-material/MenuRounded';
import Avatar from '@mui/material/Avatar';
import Box from '@mui/material/Box';
import Divider from '@mui/material/Divider';
import Drawer from '@mui/material/Drawer';
import IconButton from '@mui/material/IconButton';
import List from '@mui/material/List';
import ListItemButton from '@mui/material/ListItemButton';
import ListItemIcon from '@mui/material/ListItemIcon';
import ListItemText from '@mui/material/ListItemText';
import ListSubheader from '@mui/material/ListSubheader';
import Stack from '@mui/material/Stack';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import useMediaQuery from '@mui/material/useMediaQuery';
import { useTheme } from '@mui/material/styles';
import { Suspense, useState, type ReactElement } from 'react';
import { useTranslation } from 'react-i18next';
import { NavLink, Outlet, useMatch } from 'react-router';

import {
  platformRoutes,
  projectPath,
  projectsPath,
  routeAllowed,
  workspacePath,
  workspaceRoutes,
  type NavMeta,
} from '@/app/routes';
import { isSystemAdmin } from '@/features/auth/guards';
import { useMe } from '@/features/auth/session';
import { PersonalSettingsDialog } from '@/features/me/PersonalSettingsDialog';
import { NotificationBell, NotificationNavItem } from '@/features/notifications/NotificationBell';
import { useProjectsQuery } from '@/features/project/queries';
import { useWorkspaceQuery } from '@/features/workspace/workspace-context';
import { readSetting, storageKeys, writeSetting } from '@/shared/lib/storage';
import { Brand, BrandMark } from '@/shared/ui/Brand';
import { LoadingState } from '@/shared/ui/States';
import { layout } from '@/theme/tokens';

import { AccountMenu } from './AccountMenu';
import { WorkspaceSwitcher } from './WorkspaceSwitcher';

interface NavItem {
  to: string;
  meta: NavMeta;
}

/** 메뉴 묶음의 머리 — 접은 사이드바에서는 이름 대신 가는 줄로 묶음을 나눈다 */
function SectionHeader({ title, compact }: { title: string; compact: boolean }) {
  if (compact) {
    return <Divider sx={{ mx: 1.5, my: 1 }} />;
  }
  return (
    <ListSubheader disableSticky sx={{ bgcolor: 'transparent', lineHeight: '28px', fontSize: 11, fontWeight: 700, letterSpacing: '0.06em' }}>
      {title}
    </ListSubheader>
  );
}

const navItemSx = (compact: boolean) => ({
  borderRadius: 2,
  mb: 0.25,
  color: 'text.secondary',
  justifyContent: compact ? 'center' : undefined,
  px: compact ? 1 : undefined,
  '&.active': { bgcolor: 'action.selected', color: 'primary.main', '& .MuiListItemIcon-root': { color: 'primary.main' } },
});

/** 접은 사이드바의 항목 — 아이콘만 보이고 이름은 도구 설명 · aria-label 로 */
function CompactTip({ compact, title, children }: { compact: boolean; title: string; children: ReactElement }) {
  return compact ? (
    <Tooltip title={title} placement="right">
      {children}
    </Tooltip>
  ) : (
    children
  );
}

function NavSection({ title, items, onNavigate, compact = false }: { title: string; items: NavItem[]; onNavigate?: () => void; compact?: boolean }) {
  const { t } = useTranslation();
  if (items.length === 0) {
    return null;
  }
  return (
    <List dense subheader={<SectionHeader title={title} compact={compact} />} sx={{ px: 1 }}>
      {items.map((item) => {
        const label = t(`nav.${item.meta.label}`);
        return (
          <CompactTip key={item.to} compact={compact} title={label}>
            <ListItemButton
              component={NavLink}
              to={item.to}
              end={item.meta.end}
              onClick={onNavigate}
              aria-label={compact ? label : undefined}
              sx={navItemSx(compact)}
            >
              <ListItemIcon sx={{ minWidth: compact ? 0 : 34, color: 'inherit' }}>{item.meta.icon}</ListItemIcon>
              {!compact && <ListItemText primary={label} slotProps={{ primary: { sx: { fontWeight: 600, fontSize: 14 } } }} />}
            </ListItemButton>
          </CompactTip>
        );
      })}
    </List>
  );
}

/** 사이드바에 바로 보이는 프로젝트 수 — 더 있으면 "프로젝트 모두 보기"로 간다 */
const SIDEBAR_PROJECTS = 8;

/** 이 워크스페이스에서 내가 읽을 수 있는 프로젝트로 바로 가는 목록. 프로젝트가 없으면 그리지 않는다 (moai-wiki 의 위키 목록 자리). */
function ProjectNavSection({ slug, onNavigate, compact = false }: { slug: string; onNavigate?: () => void; compact?: boolean }) {
  const { t } = useTranslation(['common', 'workspace']);
  const projects = useProjectsQuery(slug);
  if (!projects.data || projects.data.length === 0) {
    return null;
  }
  const itemSx = navItemSx(compact);
  return (
    <List dense subheader={<SectionHeader title={t('nav.sectionProjects')} compact={compact} />} sx={{ px: 1, mt: compact ? 0 : 1 }}>
      {projects.data.slice(0, SIDEBAR_PROJECTS).map((project) => (
        <CompactTip key={project.id} compact={compact} title={project.name}>
          <ListItemButton
            component={NavLink}
            to={projectPath(slug, project.key)}
            onClick={onNavigate}
            aria-label={compact ? project.name : undefined}
            sx={itemSx}
          >
            <ListItemIcon sx={{ minWidth: compact ? 0 : 34, color: 'inherit' }}>
              {compact ? (
                // 아이콘이 모두 같으면 접었을 때 구별이 안 된다 — 이름 첫 글자로 보인다
                <Avatar variant="rounded" sx={{ width: 24, height: 24, fontSize: 12, fontWeight: 700, bgcolor: 'action.selected', color: 'inherit' }}>
                  {Array.from(project.name.trim())[0]?.toUpperCase() ?? '?'}
                </Avatar>
              ) : project.visibility === 'RESTRICTED' ? (
                <LockRounded fontSize="small" />
              ) : (
                <AssignmentRounded fontSize="small" />
              )}
            </ListItemIcon>
            {!compact && (
              <ListItemText primary={project.name} slotProps={{ primary: { noWrap: true, title: project.name, sx: { fontWeight: 600, fontSize: 14 } } }} />
            )}
          </ListItemButton>
        </CompactTip>
      ))}
      {!compact && projects.data.length > SIDEBAR_PROJECTS && (
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
function SystemAreaLabel() {
  const { t } = useTranslation();
  return (
    <Stack
      direction="row"
      spacing={1.25}
      sx={{ alignItems: 'center', p: 1, borderRadius: 2, border: 1, borderColor: 'divider', bgcolor: 'background.paper' }}
    >
      <Box
        sx={{
          width: 32,
          height: 32,
          borderRadius: 1,
          display: 'grid',
          placeItems: 'center',
          bgcolor: 'action.selected',
          color: 'primary.main',
          flexShrink: 0,
        }}
      >
        <AdminPanelSettingsRounded fontSize="small" />
      </Box>
      <Box sx={{ minWidth: 0 }}>
        <Typography variant="subtitle2" noWrap>
          {t('nav.systemArea')}
        </Typography>
        <Typography variant="caption" noWrap sx={{ color: 'text.secondary', display: 'block' }}>
          {t('nav.systemAreaHint')}
        </Typography>
      </Box>
    </Stack>
  );
}

/**
 * 사이드바 안 — 넓은 화면에서는 접을 수 있다(compact: 아이콘만, 이름은 도구 설명).
 * @param onToggle 접기 · 펼치기 단추를 누를 때(넓은 화면에서만 준다)
 */
function SidebarContent({ onNavigate, compact = false, onToggle }: { onNavigate?: () => void; compact?: boolean; onToggle?: () => void }) {
  const { t } = useTranslation();
  const me = useMe();
  const match = useMatch('/w/:slug/*');
  // 시스템 관리자 계정에는 워크스페이스가 없다 — 워크스페이스 메뉴 · 전환 단추를 그리지 않고, 묻지도 않는다
  const systemAdmin = isSystemAdmin(me);
  const slug = systemAdmin ? null : (match?.params.slug ?? me.landingWorkspace);
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
      {compact ? (
        <Stack spacing={1} sx={{ alignItems: 'center', pt: 2, pb: 1 }}>
          <BrandMark size={30} />
          <Tooltip title={t('nav.expandMenu')} placement="right">
            <IconButton size="small" onClick={onToggle} aria-label={t('nav.expandMenu')} aria-expanded={false}>
              <KeyboardDoubleArrowRightRounded fontSize="small" />
            </IconButton>
          </Tooltip>
        </Stack>
      ) : (
        <Stack direction="row" sx={{ alignItems: 'center', pl: 2.5, pr: 1, pt: 2.5, pb: 2 }}>
          <Box sx={{ flex: 1, minWidth: 0 }}>
            <Brand size={30} />
          </Box>
          {onToggle && (
            <Tooltip title={t('nav.collapseMenu')}>
              <IconButton size="small" onClick={onToggle} aria-label={t('nav.collapseMenu')} aria-expanded>
                <KeyboardDoubleArrowLeftRounded fontSize="small" />
              </IconButton>
            </Tooltip>
          )}
        </Stack>
      )}
      <Box sx={{ px: 1.5, pb: 1, display: compact ? 'flex' : 'block', justifyContent: 'center' }}>
        {systemAdmin ? (
          compact ? (
            <Tooltip title={t('nav.systemArea')} placement="right">
              <Box sx={{ width: 40, height: 40, borderRadius: 1, display: 'grid', placeItems: 'center', bgcolor: 'action.selected', color: 'primary.main' }}>
                <AdminPanelSettingsRounded fontSize="small" />
              </Box>
            </Tooltip>
          ) : (
            <SystemAreaLabel />
          )
        ) : (
          // 메뉴가 가리키는 워크스페이스(주소 → 없으면 마지막에 쓴 곳)를 전환 단추에도 똑같이 보인다
          <WorkspaceSwitcher currentSlug={slug} onNavigate={onNavigate} compact={compact} />
        )}
      </Box>
      <Box component="nav" aria-label={t('nav.mainMenu')} sx={{ flex: 1, overflowY: 'auto', overflowX: 'hidden', py: 1 }}>
        <NavSection title={t('nav.sectionWorkspace')} items={workspaceItems} onNavigate={onNavigate} compact={compact} />
        {slug && permissions.includes('workspace.read') && <ProjectNavSection slug={slug} onNavigate={onNavigate} compact={compact} />}
        {platformItems.length > 0 && (
          <Box sx={{ mt: 1 }}>
            <NavSection title={t('nav.sectionPlatform')} items={platformItems} onNavigate={onNavigate} compact={compact} />
          </Box>
        )}
      </Box>
      <Box sx={{ p: compact ? 1 : 1.5, borderTop: 1, borderColor: 'divider', display: 'flex', flexDirection: 'column', alignItems: compact ? 'center' : 'stretch' }}>
        {/* 알림 — 계정 영역 바로 위. 시스템 관리자 계정에는 알림함이 없다(api 403) */}
        {!systemAdmin && <NotificationNavItem onNavigate={onNavigate} compact={compact} />}
        <AccountMenu onNavigate={onNavigate} compact={compact} />
      </Box>
    </Stack>
  );
}

/** 로그인 뒤 모든 화면의 틀 — 넓은 화면은 고정 사이드바, 좁은 화면은 위 막대 + 서랍. 본문은 창 너비를 모두 쓴다. */
export function AppShell() {
  const { t } = useTranslation();
  const me = useMe();
  const theme = useTheme();
  const desktop = useMediaQuery(theme.breakpoints.up('md'));
  const [open, setOpen] = useState(false);
  /** 넓은 화면에서 주 메뉴를 아이콘만 보이게 접었는가 — 브라우저에 기억한다(2026-11-23 사용자: "왼쪽 메인 메뉴도 닫았다 열었다 하고") */
  const [collapsed, setCollapsed] = useState(() => readSetting(storageKeys.sidebarCollapsed) === '1');
  const toggle = () =>
    setCollapsed((before) => {
      writeSetting(storageKeys.sidebarCollapsed, before ? '0' : '1');
      return !before;
    });

  return (
    <Box sx={{ display: 'flex', minHeight: '100dvh', bgcolor: 'background.default' }}>
      {desktop ? (
        <Box
          component="aside"
          sx={{
            width: collapsed ? layout.sidebarCompactWidth : layout.sidebarWidth,
            flexShrink: 0,
            position: 'sticky',
            top: 0,
            height: '100dvh',
            borderRight: 1,
            borderColor: 'divider',
            bgcolor: 'background.sidebar',
            transition: theme.transitions.create('width', { duration: theme.transitions.duration.shorter }),
            overflow: 'hidden',
          }}
        >
          <SidebarContent compact={collapsed} onToggle={toggle} />
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
            {/* 서랍을 열지 않아도 읽지 않은 알림 수가 보이게 — 시스템 관리자 계정에는 없다 */}
            {!isSystemAdmin(me) && (
              <Box sx={{ ml: 'auto' }}>
                <NotificationBell />
              </Box>
            )}
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
      {/* 개인 설정 — 주소의 ?settings=… 가 있으면 지금 화면 위에 연다(계정 메뉴의 "개인 설정") */}
      <PersonalSettingsDialog />
    </Box>
  );
}
