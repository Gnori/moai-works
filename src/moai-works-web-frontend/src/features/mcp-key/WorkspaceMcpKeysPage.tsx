import FilterAltOffRounded from '@mui/icons-material/FilterAltOffRounded';
import KeyRounded from '@mui/icons-material/KeyRounded';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import Typography from '@mui/material/Typography';
import { useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { Link as RouterLink, useSearchParams } from 'react-router';

import { auditPath, mcpAccessPath, workspacePath } from '@/app/routes';
import { useCan } from '@/features/auth/access';
import { PersonFilter, type PersonChoice } from '@/features/workspace/PersonFilter';
import { useMembersQuery } from '@/features/workspace/queries';
import { useWorkspace } from '@/features/workspace/workspace-context';
import type { McpKeyStatusFilter } from '@/shared/api/types';
import { PageHeader } from '@/shared/ui/PageHeader';
import { EmptyState, ErrorState, LoadingState } from '@/shared/ui/States';

import { personName, readKeyFilters, writeKeyFilters, type KeyFilters } from './mcp-key';
import { KeyStatusFilter, McpKeyList } from './McpKeyList';
import { useWorkspaceMcpKeysQuery } from './queries';

/** 관리 화면의 기본 상태 — 발급 이력을 모두(만료된 것까지) */
const DEFAULT_STATUS: McpKeyStatusFilter = 'ALL';

/**
 * 워크스페이스 · MCP 키 — 이 워크스페이스 멤버들의 키(만료된 것까지 발급 이력). 상태 · 주인으로 좁힌다(주소 ?status=ACTIVE&owner=…).
 * <b>보기만 한다</b> — 키는 사용자의 것이고 주인의 다른 워크스페이스에도 닿으므로 주인만 만료시킨다(ADR-029). 이 워크스페이스에서 Agent 를
 * 막으려면 역할에서 그 멤버의 "MCP 키로 이 워크스페이스에서 일하기"(mcp.key.issue.personal)를 뺀다 — 다음 호출부터 이 워크스페이스가 키에게 보이지 않는다.
 * 키 주인의 이 워크스페이스 감사 기록으로 바로 간다. 이 화면은 mcp.key.manage 가 있어야 열린다(routes.tsx) — 판정은 api 가 요청마다 다시 한다.
 */
export function WorkspaceMcpKeysPage() {
  const { t } = useTranslation(['mcp', 'common']);
  const { workspace } = useWorkspace();
  const can = useCan();
  const [params, setParams] = useSearchParams();
  const filters = readKeyFilters(params, DEFAULT_STATUS);
  const keys = useWorkspaceMcpKeysQuery(workspace.slug, filters.status, filters.owner);
  const members = useMembersQuery(workspace.slug, can('member.read'));
  const canAudit = can('audit.read');

  /** 받은 키의 주인도 고를 수 있게 더한다(멤버 목록을 볼 수 없을 때) */
  const owners = useMemo(() => {
    const seen = new Map<string, PersonChoice>();
    for (const key of keys.data ?? []) {
      if (!seen.has(key.owner.id)) {
        seen.set(key.owner.id, { id: key.owner.id, name: personName(key.owner), username: key.owner.username });
      }
    }
    return [...seen.values()];
  }, [keys.data]);

  const change = (next: Partial<KeyFilters>) => setParams(writeKeyFilters({ ...filters, ...next }, DEFAULT_STATUS), { replace: true });
  const filtered = filters.status !== DEFAULT_STATUS || filters.owner !== null;
  const activeCount = keys.data?.filter((k) => k.status === 'ACTIVE').length ?? 0;

  return (
    <>
      <PageHeader eyebrow={workspace.name} title={t('admin.title')} description={t('admin.description')} />
      <Alert
        severity="info"
        sx={{ mb: 2, '& .MuiAlert-message': { minWidth: 0, overflowWrap: 'anywhere' } }}
        action={
          <Button color="inherit" size="small" component={RouterLink} to={workspacePath(workspace.slug, { path: 'roles' })} sx={{ flexShrink: 0 }}>
            {t('admin.rolesLink')}
          </Button>
        }
      >
        {t('admin.blockNote')}
      </Alert>
      <Card>
        <Box sx={{ p: 2, borderBottom: 1, borderColor: 'divider', display: 'flex', flexWrap: 'wrap', gap: 1.5, alignItems: 'center' }}>
          <KeyStatusFilter value={filters.status} onChange={(status) => change({ status })} />
          <PersonFilter
            label={t('admin.owner')}
            allLabel={t('admin.ownerAll')}
            value={filters.owner}
            onChange={(owner) => change({ owner })}
            members={members.data}
            extra={owners}
            loading={members.isLoading}
          />
          {filtered && (
            <Button color="inherit" startIcon={<FilterAltOffRounded />} onClick={() => setParams(new URLSearchParams(), { replace: true })}>
              {t('admin.clear')}
            </Button>
          )}
        </Box>
        {keys.isPending ? (
          <LoadingState />
        ) : keys.isError ? (
          <ErrorState error={keys.error} onRetry={() => keys.refetch()} />
        ) : keys.data.length === 0 ? (
          <EmptyState
            icon={<KeyRounded />}
            title={t(`admin.empty.${filters.status}`)}
            description={filtered ? t('admin.emptyFiltered') : t('admin.emptyBody')}
          />
        ) : (
          <Box sx={{ opacity: keys.isPlaceholderData ? 0.6 : 1 }}>
            <McpKeyList
              keys={keys.data}
              showOwner
              auditHref={canAudit ? (key) => auditPath(workspace.slug, { person: key.owner.id }) : undefined}
              accessHref={canAudit ? (key) => mcpAccessPath(workspace.slug, { key: key.id }) : undefined}
            />
          </Box>
        )}
      </Card>
      {keys.data && keys.data.length > 0 && (
        <Typography variant="caption" sx={{ color: 'text.secondary', display: 'block', mt: 1.5 }}>
          {t('admin.summary', { count: keys.data.length, active: activeCount })}
        </Typography>
      )}
    </>
  );
}
