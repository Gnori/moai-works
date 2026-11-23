import { useTranslation } from 'react-i18next';

import { useWorkspace } from '@/features/workspace/workspace-context';
import { PageHeader } from '@/shared/ui/PageHeader';

import { IssueList } from './IssueList';

/**
 * 워크스페이스의 일감 — 내가 읽을 수 있는 프로젝트 모두의 일감을 한 목록에서 찾는다(Redmine 의 /issues 자리).
 * 2026-11-24 사용자: "전체 워크스페이스에서 이슈를 검색할 수도 있어야해" · "레드마인을 생각하면 돼".
 * 어느 프로젝트가 들어가는지는 서버가 프로젝트 판정으로 정한다(읽을 수 없는 프로젝트의 일감은 수에도 들어가지 않는다).
 * 일감은 프로젝트 화면에서 만든다(만들 프로젝트를 정해야 한다).
 */
export function WorkspaceIssuesPage() {
  const { t } = useTranslation('issue');
  const { workspace } = useWorkspace();
  return (
    <>
      <PageHeader eyebrow={workspace.name} title={t('workspaceList.title')} description={t('workspaceList.description')} />
      <IssueList slug={workspace.slug} />
    </>
  );
}
