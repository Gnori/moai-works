import { useQueryClient } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';

import { IssueTemplateManager } from '@/features/issue/IssueTemplateManager';
import { issueKeys, useProjectTemplatesQuery } from '@/features/issue/queries';
import { useWorkspace } from '@/features/workspace/workspace-context';
import { issueTemplateApi } from '@/shared/api/endpoints';
import { PageHeader } from '@/shared/ui/PageHeader';

import { useProject } from './project-context';
import { projectCan } from './queries';

/**
 * 프로젝트의 일감 템플릿 — 유형마다 이 프로젝트에서 실제로 쓰이는 템플릿과 출처(이 프로젝트 · 워크스페이스에서 물려받음 · 처음 템플릿).
 * project.manage 가 있으면 덮어쓰고 "워크스페이스 기본으로 되돌리기"를 한다(메뉴도 그 사람에게만 보인다). 보기는 독자 누구나.
 * 새 일감 · 일감 고치기에서 유형을 고르면 여기의 템플릿으로 설명을 채운다(FR-ISS-020).
 */
export function ProjectTemplatesPage() {
  const { t } = useTranslation(['issue']);
  const { workspace } = useWorkspace();
  const project = useProject();
  const queryClient = useQueryClient();
  const query = useProjectTemplatesQuery(workspace.slug, project.key);
  const canManage = project.status === 'ACTIVE' && projectCan(project, 'project.manage');
  const refresh = () => queryClient.invalidateQueries({ queryKey: issueKeys.projectTemplates(workspace.slug, project.key) });

  return (
    <>
      <PageHeader eyebrow={project.name} title={t('templates.projectTitle')} description={t('templates.projectDescription')} />
      <IssueTemplateManager
        scope="project"
        query={query}
        canManage={canManage}
        onSave={async (type, body, rowVersion) => {
          const saved = await issueTemplateApi.setProject(workspace.slug, project.key, type, body, rowVersion);
          await refresh();
          return saved;
        }}
        onReset={async (type) => {
          const after = await issueTemplateApi.resetProject(workspace.slug, project.key, type);
          await refresh();
          return after;
        }}
      />
    </>
  );
}
