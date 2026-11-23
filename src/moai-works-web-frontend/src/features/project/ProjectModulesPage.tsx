import AddRounded from '@mui/icons-material/AddRounded';
import ArrowDownwardRounded from '@mui/icons-material/ArrowDownwardRounded';
import ArrowUpwardRounded from '@mui/icons-material/ArrowUpwardRounded';
import EditRounded from '@mui/icons-material/EditRounded';
import ViewModuleRounded from '@mui/icons-material/ViewModuleRounded';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import Chip from '@mui/material/Chip';
import IconButton from '@mui/material/IconButton';
import Link from '@mui/material/Link';
import List from '@mui/material/List';
import ListItem from '@mui/material/ListItem';
import Stack from '@mui/material/Stack';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import { useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link as RouterLink } from 'react-router';
import { toast } from 'sonner';

import { projectPath } from '@/app/routes';
import { issueKeys } from '@/features/issue/queries';
import { useWorkspace } from '@/features/workspace/workspace-context';
import { workspaceKeys } from '@/features/workspace/queries';
import { projectApi } from '@/shared/api/endpoints';
import { errorMessage } from '@/shared/api/http';
import type { ProjectModule } from '@/shared/api/types';
import { PageHeader } from '@/shared/ui/PageHeader';
import { EmptyState, ErrorState, LoadingState } from '@/shared/ui/States';

import { ModuleDialog } from './ModuleDialog';
import { useProject } from './project-context';
import { projectCan, projectKeys, useProjectModulesQuery } from './queries';

/**
 * 프로젝트 모듈 — 일감의 관련 모듈로 고르는 목록(예: api · web-backend · web-frontend · infra · docs, 2026-11-23 일감 #27).
 * 보기는 독자 누구나(메뉴는 project.manage 가 있는 사람에게만 — 일감 템플릿과 같다). project.manage 가 있으면 더하고 · 이름 · 설명을 고치고 ·
 * 순서를 바꾸고(위 · 아래 단추 — 터치 · 키보드에서도 된다) · 쓰지 않음으로 두거나 다시 쓴다. 지우기는 없다(쓰지 않는 모듈은 새 일감에서
 * 고를 수 없고 이미 붙은 일감에는 남는다). 판정은 api 가 한다 — 화면은 단추를 숨기기만 한다.
 */
export function ProjectModulesPage() {
  const { t } = useTranslation(['workspace', 'common']);
  const { workspace } = useWorkspace();
  const project = useProject();
  const queryClient = useQueryClient();
  const query = useProjectModulesQuery(workspace.slug, project.key);
  const canManage = project.status === 'ACTIVE' && projectCan(project, 'project.manage');
  const [editing, setEditing] = useState<ProjectModule | 'new' | null>(null);
  const [busy, setBusy] = useState(false);

  /** 모듈이 바뀌면 — 목록과 일감 화면(양식 · 거르기 · 칩)이 다시 읽게 하고 감사 화면도 */
  const refresh = (modules?: ProjectModule[]) => {
    if (modules) {
      queryClient.setQueryData(projectKeys.modules(workspace.slug, project.key), modules);
    }
    void queryClient.invalidateQueries({ queryKey: projectKeys.modules(workspace.slug, project.key) });
    void queryClient.invalidateQueries({ queryKey: issueKeys.project(workspace.slug, project.key) });
    void queryClient.invalidateQueries({ queryKey: workspaceKeys.audit(workspace.slug) });
  };

  const run = async (action: () => Promise<ProjectModule[] | void>, done?: string) => {
    setBusy(true);
    try {
      const result = await action();
      refresh(result ?? undefined);
      if (done) {
        toast.success(done);
      }
    } catch (e) {
      toast.error(errorMessage(e));
      refresh();
    } finally {
      setBusy(false);
    }
  };

  const move = (modules: ProjectModule[], index: number, delta: -1 | 1) => {
    const keys = modules.map((m) => m.key);
    const [key] = keys.splice(index, 1);
    keys.splice(index + delta, 0, key as string);
    void run(() => projectApi.reorderModules(workspace.slug, project.key, keys));
  };

  const toggle = (module: ProjectModule) =>
    void run(
      async () => {
        await projectApi.updateModule(workspace.slug, project.key, module.key, { active: !module.active, rowVersion: module.rowVersion });
      },
      module.active ? t('projects.modules.deactivated', { name: module.name }) : t('projects.modules.activated', { name: module.name }),
    );

  let body;
  if (query.isPending) {
    body = <LoadingState />;
  } else if (query.isError) {
    body = <ErrorState error={query.error} onRetry={() => query.refetch()} />;
  } else if (query.data.length === 0) {
    body = (
      <EmptyState
        icon={<ViewModuleRounded />}
        title={t('projects.modules.emptyTitle')}
        description={canManage ? t('projects.modules.emptyBody') : t('projects.modules.emptyBodyReader')}
        action={
          canManage && (
            <Button variant="contained" startIcon={<AddRounded />} onClick={() => setEditing('new')}>
              {t('projects.modules.add')}
            </Button>
          )
        }
      />
    );
  } else {
    const modules = query.data;
    body = (
      <List disablePadding aria-label={t('projects.modules.listLabel')}>
        {modules.map((m, index) => (
          <ListItem
            key={m.key}
            divider={index < modules.length - 1}
            sx={{ alignItems: 'flex-start', gap: 1.5, py: 1.5, px: { xs: 1.5, sm: 2 }, flexWrap: { xs: 'wrap', sm: 'nowrap' } }}
          >
            {canManage && (
              <Stack sx={{ flexShrink: 0, mt: -0.5 }}>
                <IconButton
                  size="small"
                  aria-label={t('projects.modules.moveUp', { name: m.name })}
                  disabled={busy || index === 0}
                  onClick={() => move(modules, index, -1)}
                >
                  <ArrowUpwardRounded fontSize="small" />
                </IconButton>
                <IconButton
                  size="small"
                  aria-label={t('projects.modules.moveDown', { name: m.name })}
                  disabled={busy || index === modules.length - 1}
                  onClick={() => move(modules, index, 1)}
                >
                  <ArrowDownwardRounded fontSize="small" />
                </IconButton>
              </Stack>
            )}
            <Box sx={{ minWidth: 0, flex: 1 }}>
              <Stack direction="row" spacing={1} useFlexGap sx={{ alignItems: 'center', flexWrap: 'wrap' }}>
                <Typography variant="subtitle2" sx={{ fontWeight: 700, overflowWrap: 'anywhere' }}>
                  {m.name}
                </Typography>
                {/* 이름이 key 와 같으면 key 를 다시 보이지 않는다 */}
                {m.key !== m.name && <Chip size="small" variant="outlined" label={m.key} sx={{ fontFamily: 'monospace', height: 22 }} />}
                {!m.active && <Chip size="small" label={t('projects.modules.inactive')} sx={{ height: 22, bgcolor: 'action.selected' }} />}
              </Stack>
              {m.description && (
                <Typography variant="body2" sx={{ color: 'text.secondary', mt: 0.5, overflowWrap: 'anywhere' }}>
                  {m.description}
                </Typography>
              )}
              <Link
                component={RouterLink}
                to={projectPath(workspace.slug, project.key, `module=${encodeURIComponent(m.key)}`)}
                variant="caption"
                underline="hover"
                sx={{ display: 'inline-block', mt: 0.5, color: 'text.secondary' }}
              >
                {t('projects.modules.issueCount', { count: m.issueCount })}
              </Link>
            </Box>
            {canManage && (
              <Stack direction="row" spacing={0.5} sx={{ flexShrink: 0, alignItems: 'center', ml: { xs: 'auto', sm: 0 } }}>
                <Tooltip title={t('projects.modules.edit')}>
                  <IconButton size="small" aria-label={t('projects.modules.editNamed', { name: m.name })} onClick={() => setEditing(m)} disabled={busy}>
                    <EditRounded fontSize="small" />
                  </IconButton>
                </Tooltip>
                <Button size="small" color="inherit" onClick={() => toggle(m)} disabled={busy} sx={{ color: 'text.secondary', whiteSpace: 'nowrap' }}>
                  {m.active ? t('projects.modules.deactivate') : t('projects.modules.activate')}
                </Button>
              </Stack>
            )}
          </ListItem>
        ))}
      </List>
    );
  }

  return (
    <>
      <PageHeader
        eyebrow={project.name}
        title={t('projects.modules.title')}
        description={t('projects.modules.description')}
        actions={
          canManage &&
          (query.data?.length ?? 0) > 0 && (
            <Button variant="contained" startIcon={<AddRounded />} onClick={() => setEditing('new')}>
              {t('projects.modules.add')}
            </Button>
          )
        }
      />
      <Card>{body}</Card>
      {editing && (
        <ModuleDialog
          slug={workspace.slug}
          projectKey={project.key}
          module={editing === 'new' ? undefined : editing}
          onClose={() => setEditing(null)}
          onSaved={() => refresh()}
        />
      )}
    </>
  );
}
