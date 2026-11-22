import AssignmentRounded from '@mui/icons-material/AssignmentRounded';
import GroupsRounded from '@mui/icons-material/GroupsRounded';
import LockRounded from '@mui/icons-material/LockRounded';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import CardActionArea from '@mui/material/CardActionArea';
import Chip from '@mui/material/Chip';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { useTranslation } from 'react-i18next';
import { Link as RouterLink } from 'react-router';

import { projectPath } from '@/app/routes';
import type { ProjectSummary } from '@/shared/api/types';
import { date } from '@/shared/lib/format';
import { useLabels } from '@/shared/lib/labels';

/** 읽을 수 있는 사람 표시 — 색만으로 전하지 않게 글과 함께 */
export function ProjectVisibilityChip({ visibility }: { visibility: ProjectSummary['visibility'] }) {
  const labels = useLabels();
  return (
    <Chip
      size="small"
      variant="outlined"
      icon={visibility === 'RESTRICTED' ? <LockRounded /> : <GroupsRounded />}
      label={labels.projectVisibility(visibility)}
    />
  );
}

/** 프로젝트 카드 묶음 — 카드 전체가 그 프로젝트로 가는 링크다. 창 너비에 따라 1~3열. */
export function ProjectCards({ slug, projects }: { slug: string; projects: ProjectSummary[] }) {
  const { t } = useTranslation('workspace');
  return (
    <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, minmax(0, 1fr))', lg: 'repeat(3, minmax(0, 1fr))' } }}>
      {projects.map((project) => (
        <Card key={project.id} sx={{ minWidth: 0 }}>
          <CardActionArea component={RouterLink} to={projectPath(slug, project.key)} sx={{ height: '100%', p: 2.5, display: 'block' }}>
            <Stack direction="row" spacing={1.5} sx={{ alignItems: 'flex-start' }}>
              <Box
                sx={{ width: 40, height: 40, borderRadius: 2, display: 'grid', placeItems: 'center', bgcolor: 'primary.light', color: 'primary.main', flexShrink: 0 }}
              >
                <AssignmentRounded />
              </Box>
              <Box sx={{ minWidth: 0, flex: 1 }}>
                <Typography variant="subtitle1" component="h3" noWrap title={project.name} sx={{ fontWeight: 700 }}>
                  {project.name}
                </Typography>
                <Typography variant="caption" noWrap title={project.key} sx={{ color: 'text.secondary', fontFamily: 'monospace', display: 'block' }}>
                  {project.key}
                </Typography>
              </Box>
            </Stack>
            {project.description && (
              <Typography
                variant="body2"
                sx={{ color: 'text.secondary', mt: 1.5, display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden', overflowWrap: 'anywhere' }}
              >
                {project.description}
              </Typography>
            )}
            <Stack direction="row" spacing={1} useFlexGap sx={{ mt: 2, alignItems: 'center', flexWrap: 'wrap' }}>
              <ProjectVisibilityChip visibility={project.visibility} />
              <Typography variant="caption" sx={{ color: 'text.secondary' }}>
                {t('projects.updated', { date: date(project.updatedAt) })}
              </Typography>
            </Stack>
          </CardActionArea>
        </Card>
      ))}
    </Box>
  );
}
