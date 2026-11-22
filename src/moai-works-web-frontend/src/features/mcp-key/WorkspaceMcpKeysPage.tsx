import FilterAltOffRounded from '@mui/icons-material/FilterAltOffRounded';
import KeyRounded from '@mui/icons-material/KeyRounded';
import ReceiptLongRounded from '@mui/icons-material/ReceiptLongRounded';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import Typography from '@mui/material/Typography';
import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link as RouterLink, useSearchParams } from 'react-router';

import { auditPath } from '@/app/routes';
import { useCan } from '@/features/auth/access';
import { PersonFilter, type PersonChoice } from '@/features/workspace/PersonFilter';
import { useMembersQuery } from '@/features/workspace/queries';
import { useWorkspace } from '@/features/workspace/workspace-context';
import type { McpKey, McpKeyStatusFilter } from '@/shared/api/types';
import { PageHeader } from '@/shared/ui/PageHeader';
import { EmptyState, ErrorState, LoadingState } from '@/shared/ui/States';

import { ExpireKeyDialog } from './ExpireKeyDialog';
import { personName, readKeyFilters, writeKeyFilters, type KeyFilters } from './mcp-key';
import { KeyStatusFilter, McpKeyList } from './McpKeyList';
import { useWorkspaceMcpKeysQuery } from './queries';

/** 관리 화면의 기본 상태 — 발급 이력을 모두(만료된 것까지) */
const DEFAULT_STATUS: McpKeyStatusFilter = 'ALL';

/**
 * 워크스페이스 · MCP 키 — 이 워크스페이스에서 발급된 모든 키(발급 이력). 상태 · 주인으로 좁히고(주소 ?status=ACTIVE&owner=…),
 * 사용 중인 키는 누구 것이든 만료시킨다(이유를 적을 수 있다). 키 주인의 MCP 키 감사 기록으로 바로 간다.
 * 이 화면은 mcp.key.manage 가 있어야 열린다(routes.tsx) — 판정은 api 가 요청마다 다시 한다.
 */
export function WorkspaceMcpKeysPage() {
  const { t } = useTranslation(['mcp', 'common']);
  const { workspace } = useWorkspace();
  const can = useCan();
  const [params, setParams] = useSearchParams();
  const filters = readKeyFilters(params, DEFAULT_STATUS);
  const keys = useWorkspaceMcpKeysQuery(workspace.slug, filters.status, filters.owner);
  const members = useMembersQuery(workspace.slug, can('member.read'));
  const [expiring, setExpiring] = useState<McpKey | null>(null);
  const canAudit = can('audit.read');

  /** 떠난 멤버의 키도 주인으로 고를 수 있게 — 받은 키의 주인을 더한다 */
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
      <PageHeader
        eyebrow={workspace.name}
        title={t('admin.title')}
        description={t('admin.description')}
        actions={
          canAudit && (
            <Button variant="outlined" color="inherit" startIcon={<ReceiptLongRounded />} component={RouterLink} to={auditPath(workspace.slug, { action: 'mcp.key' })}>
              {t('admin.auditAll')}
            </Button>
          )
        }
      />
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
              canExpire={() => can('mcp.key.manage')}
              onExpire={setExpiring}
              auditHref={canAudit ? (key) => auditPath(workspace.slug, { person: key.owner.id, action: 'mcp.key' }) : undefined}
            />
          </Box>
        )}
      </Card>
      {keys.data && keys.data.length > 0 && (
        <Typography variant="caption" sx={{ color: 'text.secondary', display: 'block', mt: 1.5 }}>
          {t('admin.summary', { count: keys.data.length, active: activeCount })}
        </Typography>
      )}
      {expiring && <ExpireKeyDialog target={expiring} onClose={() => setExpiring(null)} />}
    </>
  );
}
