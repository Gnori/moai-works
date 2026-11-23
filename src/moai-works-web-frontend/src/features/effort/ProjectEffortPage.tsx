import { useTranslation } from 'react-i18next';

import { useProject } from '@/features/project/project-context';
import { PageHeader } from '@/shared/ui/PageHeader';

import { EffortReport } from './EffortReport';

/** 프로젝트 하나의 공수 집계 — 워크스페이스 공수 집계와 같은 부품을 이 프로젝트로 정해 그린다(기본 기준은 일감별) */
export function ProjectEffortPage() {
  const { t } = useTranslation('effort');
  const project = useProject();
  return (
    <>
      <PageHeader eyebrow={project.name} title={t('title')} description={t('projectDescription')} />
      <EffortReport projectKey={project.key} />
    </>
  );
}
