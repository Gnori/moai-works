import { useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { useSearchParams } from 'react-router';

import { issuePath } from '@/app/routes';
import { useCan } from '@/features/auth/access';
import { useMembersQuery } from '@/features/workspace/queries';
import { useWorkspace } from '@/features/workspace/workspace-context';
import { PageHeader } from '@/shared/ui/PageHeader';

import { readAccessFilters, writeAccessFilters } from './access-log';
import { AccessLogView } from './AccessLogView';

/**
 * 워크스페이스 › MCP 접근 기록 — MCP 키로 들어와 이 워크스페이스에서 쓴 요청 전부(읽기 · 만들기 · 고치기 · 지우기 · 일감 연결 · 거부까지).
 * audit.read 가 있어야 열린다(routes.tsx — 판정은 api 가 다시 한다). 조건은 주소에 남아 새로 고쳐도 · 링크를 나눠도 같은 기록이 열린다.
 * 연결 시작 · 도구 목록 · 거절처럼 워크스페이스를 정하지 않은 줄은 여기에 없고 키 주인의 "내 MCP 키"에서 본다.
 */
export function McpAccessLogPage() {
  const { t } = useTranslation('mcp');
  const { workspace } = useWorkspace();
  const can = useCan();
  const [params, setParams] = useSearchParams();
  const filters = useMemo(() => readAccessFilters(params), [params]);
  const members = useMembersQuery(workspace.slug, can('member.read'));

  return (
    <>
      <PageHeader eyebrow={workspace.name} title={t('access.title')} description={t('access.description')} />
      <AccessLogView
        scope={{ kind: 'workspace', slug: workspace.slug }}
        filters={filters}
        onChange={(next) => setParams(writeAccessFilters(next), { replace: true })}
        showOwner
        showWorkspace={false}
        members={members.data}
        membersLoading={members.isLoading}
        issueHref={(e) => (e.issueNumber !== null ? issuePath(workspace.slug, e.issueNumber) : null)}
      />
    </>
  );
}
