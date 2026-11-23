import { useTranslation } from 'react-i18next';

import { useWorkspace } from '@/features/workspace/workspace-context';
import { PageHeader } from '@/shared/ui/PageHeader';

import { EffortReport } from './EffortReport';

/**
 * 워크스페이스의 공수 집계 — 내가 읽을 수 있는 프로젝트 모두의 작업 시간 기록을 모아 예상 공수와 견준다(FR-ISS-017).
 * 2026-11-22 사용자: "나중에 공수 정리 및 실제 투입 공수는 소요 시간으로 뽑아야 해". 어느 프로젝트가 들어가는지는 서버가 판정한다.
 */
export function EffortPage() {
  const { t } = useTranslation('effort');
  const { workspace } = useWorkspace();
  return (
    <>
      <PageHeader eyebrow={workspace.name} title={t('title')} description={t('description')} />
      <EffortReport />
    </>
  );
}
