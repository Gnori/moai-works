import AddRounded from '@mui/icons-material/AddRounded';
import AssignmentRounded from '@mui/icons-material/AssignmentRounded';
import Inventory2Rounded from '@mui/icons-material/Inventory2Rounded';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import Divider from '@mui/material/Divider';
import Link from '@mui/material/Link';
import Stack from '@mui/material/Stack';
import Tab from '@mui/material/Tab';
import Tabs from '@mui/material/Tabs';
import Typography from '@mui/material/Typography';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link as RouterLink, useNavigate, useSearchParams } from 'react-router';

import { projectPath } from '@/app/routes';
import { useCan } from '@/features/auth/access';
import { useWorkspace } from '@/features/workspace/workspace-context';
import type { ProjectSummary } from '@/shared/api/types';
import { date } from '@/shared/lib/format';
import { PageHeader } from '@/shared/ui/PageHeader';
import { EmptyState, ErrorState, LoadingState } from '@/shared/ui/States';

import { ProjectCards, ProjectVisibilityChip } from './ProjectCards';
import { ProjectDialog } from './ProjectDialog';
import { useProjectsQuery } from './queries';
import { RemoveProjectDialog } from './RemoveProjectDialog';
import { useProjectActions } from './use-project-actions';

type ProjectsTab = 'active' | 'archived';

/** 보관함 — 보관한 프로젝트를 복원하거나 지운다. 프로젝트를 관리할 수 있는 사람에게만 탭이 보인다. */
function ArchivedProjects({ slug, onRemove }: { slug: string; onRemove: (project: ProjectSummary) => void }) {
  const { t } = useTranslation('workspace');
  const can = useCan();
  const actions = useProjectActions(slug);
  const all = useProjectsQuery(slug, true);

  if (all.isPending) {
    return <LoadingState />;
  }
  if (all.isError) {
    return <ErrorState error={all.error} onRetry={() => all.refetch()} />;
  }
  const archived = all.data.filter((p) => p.status === 'ARCHIVED');
  if (archived.length === 0) {
    return <EmptyState icon={<Inventory2Rounded />} title={t('projects.archivedEmptyTitle')} description={t('projects.archivedEmptyBody')} />;
  }
  return (
    <Stack divider={<Divider />}>
      {archived.map((project) => (
        <Stack
          key={project.id}
          direction={{ xs: 'column', sm: 'row' }}
          spacing={1.5}
          sx={{ px: 2.5, py: 2, alignItems: { xs: 'flex-start', sm: 'center' } }}
        >
          <Box sx={{ minWidth: 0, flex: 1, width: '100%' }}>
            <Link component={RouterLink} to={projectPath(slug, project.key)} underline="hover" variant="subtitle2" sx={{ fontWeight: 700, overflowWrap: 'anywhere' }}>
              {project.name}
            </Link>
            <Stack direction="row" spacing={1} useFlexGap sx={{ mt: 0.5, alignItems: 'center', flexWrap: 'wrap' }}>
              <Typography variant="caption" sx={{ color: 'text.secondary', fontFamily: 'monospace' }}>
                {project.key}
              </Typography>
              <ProjectVisibilityChip visibility={project.visibility} />
              <Typography variant="caption" sx={{ color: 'text.secondary' }}>
                {t('projects.archivedOn', { date: date(project.updatedAt) })}
              </Typography>
            </Stack>
          </Box>
          {can('project.delete') && (
            <Stack direction="row" spacing={1} sx={{ flexShrink: 0 }}>
              <Button variant="outlined" onClick={() => actions.restore(project)} disabled={actions.busy}>
                {t('projects.home.restore')}
              </Button>
              <Button color="error" onClick={() => onRemove(project)}>
                {t('projects.home.delete')}
              </Button>
            </Stack>
          )}
        </Stack>
      ))}
    </Stack>
  );
}

/**
 * 프로젝트 목록 — 내가 읽을 수 있는 프로젝트. 만들기 단추와 보관함 탭은 권한이 있을 때만 보인다(표시만 — 판정은 api).
 * 탭은 주소(?tab=archived)에 남아 새로 고쳐도 그대로다.
 */
export function ProjectsPage() {
  const { t } = useTranslation('workspace');
  const { workspace } = useWorkspace();
  const can = useCan();
  const navigate = useNavigate();
  const canCreate = can('project.create');
  const canManage = can('project.manage') || can('project.delete');
  const [params, setParams] = useSearchParams();
  const tab: ProjectsTab = canManage && params.get('tab') === 'archived' ? 'archived' : 'active';
  const [creating, setCreating] = useState(false);
  const [removing, setRemoving] = useState<ProjectSummary | null>(null);
  const projects = useProjectsQuery(workspace.slug);

  const changeTab = (next: ProjectsTab) =>
    setParams(
      (p) => {
        if (next === 'active') {
          p.delete('tab');
        } else {
          p.set('tab', next);
        }
        return p;
      },
      { replace: true },
    );

  const createButton = canCreate && (
    <Button variant="contained" startIcon={<AddRounded />} onClick={() => setCreating(true)}>
      {t('projects.create')}
    </Button>
  );

  return (
    <>
      <PageHeader eyebrow={workspace.name} title={t('projects.title')} description={t('projects.description')} actions={createButton} />
      {canManage && (
        <Tabs value={tab} onChange={(_, next: ProjectsTab) => changeTab(next)} sx={{ mb: 2.5, borderBottom: 1, borderColor: 'divider' }}>
          <Tab value="active" label={t('projects.tabActive')} />
          <Tab value="archived" label={t('projects.tabArchived')} />
        </Tabs>
      )}
      {tab === 'archived' ? (
        <Card>
          <ArchivedProjects slug={workspace.slug} onRemove={setRemoving} />
        </Card>
      ) : projects.isPending ? (
        <LoadingState />
      ) : projects.isError ? (
        <ErrorState error={projects.error} onRetry={() => projects.refetch()} />
      ) : projects.data.length === 0 ? (
        <Card>
          <EmptyState
            icon={<AssignmentRounded />}
            title={t('projects.emptyTitle')}
            description={canCreate ? t('projects.emptyBody') : t('projects.emptyBodyReader')}
            action={createButton}
          />
        </Card>
      ) : (
        <ProjectCards slug={workspace.slug} projects={projects.data} />
      )}
      {creating && (
        <ProjectDialog slug={workspace.slug} onClose={() => setCreating(false)} onSaved={(project) => void navigate(projectPath(workspace.slug, project.key))} />
      )}
      {removing && <RemoveProjectDialog slug={workspace.slug} project={removing} onClose={() => setRemoving(null)} />}
    </>
  );
}
