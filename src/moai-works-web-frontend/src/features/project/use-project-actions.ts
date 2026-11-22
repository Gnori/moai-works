import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';

import { workspaceKeys } from '@/features/workspace/queries';
import { projectApi } from '@/shared/api/endpoints';
import { errorMessage } from '@/shared/api/http';
import type { ProjectSummary } from '@/shared/api/types';
import { useConfirm } from '@/shared/ui/ConfirmDialog';

import { projectKeys } from './queries';

/** 프로젝트 보관 · 복원 — 보관은 되돌릴 수 있지만 독자에게서 사라지므로 한 번 묻는다. */
export function useProjectActions(slug: string) {
  const { t } = useTranslation('workspace');
  const confirm = useConfirm();
  const queryClient = useQueryClient();

  const refresh = (key: string) => {
    void queryClient.invalidateQueries({ queryKey: projectKeys.lists(slug) });
    void queryClient.invalidateQueries({ queryKey: projectKeys.detail(slug, key) });
    void queryClient.invalidateQueries({ queryKey: workspaceKeys.audit(slug) });
  };

  const archiveMutation = useMutation({
    mutationFn: (key: string) => projectApi.archive(slug, key),
    onSuccess: (project) => {
      queryClient.setQueryData(projectKeys.detail(slug, project.key), project);
      refresh(project.key);
      toast.success(t('projects.archive.done'));
    },
    onError: (e) => toast.error(errorMessage(e)),
  });

  const restoreMutation = useMutation({
    mutationFn: (key: string) => projectApi.restore(slug, key),
    onSuccess: (project) => {
      queryClient.setQueryData(projectKeys.detail(slug, project.key), project);
      refresh(project.key);
      toast.success(t('projects.archive.restored'));
    },
    onError: (e) => toast.error(errorMessage(e)),
  });

  return {
    archive: async (project: Pick<ProjectSummary, 'key' | 'name'>) => {
      const ok = await confirm({
        title: t('projects.archive.confirmTitle'),
        description: t('projects.archive.confirmBody', { name: project.name }),
        confirmLabel: t('projects.archive.confirm'),
      });
      if (ok) {
        archiveMutation.mutate(project.key);
      }
    },
    restore: (project: Pick<ProjectSummary, 'key'>) => restoreMutation.mutate(project.key),
    busy: archiveMutation.isPending || restoreMutation.isPending,
  };
}
