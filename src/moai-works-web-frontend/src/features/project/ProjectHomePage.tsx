import AddRounded from '@mui/icons-material/AddRounded';
import Inventory2Rounded from '@mui/icons-material/Inventory2Rounded';
import SettingsRounded from '@mui/icons-material/SettingsRounded';
import Alert from '@mui/material/Alert';
import Button from '@mui/material/Button';
import Chip from '@mui/material/Chip';
import Stack from '@mui/material/Stack';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link as RouterLink, useNavigate } from 'react-router';

import { newIssuePath, projectsPath } from '@/app/routes';
import { IssueList } from '@/features/issue/IssueList';
import { useWorkspace } from '@/features/workspace/workspace-context';
import { PageHeader } from '@/shared/ui/PageHeader';

import { ProjectVisibilityChip } from './ProjectCards';
import { ProjectDialog } from './ProjectDialog';
import { useProject } from './project-context';
import { projectCan } from './queries';
import { RemoveProjectDialog } from './RemoveProjectDialog';
import { useProjectActions } from './use-project-actions';

/**
 * 프로젝트 하나의 홈 — 이름 · 읽을 수 있는 사람 · 설정 · 보관, 그리고 일감 목록(찾기 · 조건 · 정렬 · 쪽).
 * 단추는 서버가 준 이 프로젝트의 내 권한(myPermissions) · 할 수 있는 일(myActions)로 보인다(표시만 — 판정은 api).
 * 프로젝트는 틀(ProjectLayout)이 읽어 내려준다 — 독자가 아닌 프로젝트는 거기서 "찾을 수 없음"으로 끝난다.
 */
export function ProjectHomePage() {
  const { t } = useTranslation(['workspace', 'issue']);
  const { workspace } = useWorkspace();
  const project = useProject();
  const navigate = useNavigate();
  const actions = useProjectActions(workspace.slug);
  const [editing, setEditing] = useState(false);
  const [removing, setRemoving] = useState(false);

  const archived = project.status === 'ARCHIVED';
  const canCreate = project.myActions.includes('createIssue');
  const canManage = projectCan(project, 'project.manage');
  const canDelete = projectCan(project, 'project.delete');

  return (
    <>
      <PageHeader
        eyebrow={`${workspace.name} · ${t('projects.title')}`}
        title={project.name}
        description={project.description ?? undefined}
        actions={
          <Stack direction="row" spacing={1} useFlexGap sx={{ flexWrap: 'wrap' }}>
            {canCreate && (
              <Button component={RouterLink} to={newIssuePath(workspace.slug, project.key)} variant="contained" startIcon={<AddRounded />}>
                {t('issue:create.title')}
              </Button>
            )}
            {canManage && (
              <Button variant="outlined" startIcon={<SettingsRounded />} onClick={() => setEditing(true)}>
                {t('projects.home.settings')}
              </Button>
            )}
            {canDelete && !archived && (
              <Button color="inherit" startIcon={<Inventory2Rounded />} onClick={() => void actions.archive(project)} disabled={actions.busy}>
                {t('projects.home.archive')}
              </Button>
            )}
          </Stack>
        }
      />
      <Stack direction="row" spacing={1} useFlexGap sx={{ flexWrap: 'wrap', mb: 2.5, mt: -1 }}>
        <ProjectVisibilityChip visibility={project.visibility} />
        <Chip size="small" variant="outlined" label={t('projects.home.address', { key: project.key })} sx={{ fontFamily: 'monospace' }} />
      </Stack>
      {archived && (
        <Alert
          severity="warning"
          sx={{ mb: 2.5, alignItems: 'center' }}
          action={
            canDelete && (
              <Stack direction="row" spacing={1}>
                <Button color="inherit" size="small" onClick={() => actions.restore(project)} disabled={actions.busy}>
                  {t('projects.home.restore')}
                </Button>
                <Button color="error" size="small" onClick={() => setRemoving(true)}>
                  {t('projects.home.delete')}
                </Button>
              </Stack>
            )
          }
        >
          {t('projects.home.archivedBanner')}
        </Alert>
      )}
      <IssueList slug={workspace.slug} project={project} />
      {editing && <ProjectDialog slug={workspace.slug} project={project} onClose={() => setEditing(false)} />}
      {removing && (
        <RemoveProjectDialog
          slug={workspace.slug}
          project={project}
          onClose={() => setRemoving(false)}
          onRemoved={() => void navigate(`${projectsPath(workspace.slug)}?tab=archived`, { replace: true })}
        />
      )}
    </>
  );
}
