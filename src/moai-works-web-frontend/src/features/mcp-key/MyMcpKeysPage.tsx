import AddRounded from '@mui/icons-material/AddRounded';
import VpnKeyRounded from '@mui/icons-material/VpnKeyRounded';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import Typography from '@mui/material/Typography';
import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useSearchParams } from 'react-router';

import { useMe } from '@/features/auth/session';
import type { McpKey, McpKeyStatusFilter } from '@/shared/api/types';
import { PageHeader } from '@/shared/ui/PageHeader';
import { EmptyState, ErrorState, LoadingState } from '@/shared/ui/States';

import { ExpireKeyDialog } from './ExpireKeyDialog';
import { IssueKeyDialog } from './IssueKeyDialog';
import { issuableWorkspaces, readKeyFilters, writeKeyFilters } from './mcp-key';
import { KeyStatusFilter, McpKeyList } from './McpKeyList';
import { useMyMcpKeysQuery } from './queries';

/** 내 키 목록의 기본 상태 — 지금 쓰는 키부터 보인다. 만료된 키는 전체 · 만료에서 */
const DEFAULT_STATUS: McpKeyStatusFilter = 'ACTIVE';

/**
 * 개인 설정 · 내 MCP 키 — 모든 워크스페이스의 내 키, 발급(원문은 한 번만), 만료시키기(이유를 적을 수 있다).
 * 키는 지우지 않는다. 시스템 관리자 계정에는 이 화면이 없다(워크스페이스가 없다 — 라우터 · 메뉴가 가린다, api 도 403).
 * 상태 조건은 주소(?status=ALL)에 남긴다.
 */
export function MyMcpKeysPage() {
  const { t } = useTranslation(['mcp', 'common']);
  const me = useMe();
  const [params, setParams] = useSearchParams();
  const { status } = readKeyFilters(params, DEFAULT_STATUS);
  const keys = useMyMcpKeysQuery(status);
  const [issuing, setIssuing] = useState(false);
  const [expiring, setExpiring] = useState<McpKey | null>(null);
  const workspaces = useMemo(() => issuableWorkspaces(me.workspaces), [me.workspaces]);

  const changeStatus = (next: McpKeyStatusFilter) =>
    setParams(writeKeyFilters({ status: next, owner: null }, DEFAULT_STATUS), { replace: true });

  const issueButton = (
    <Button variant="contained" startIcon={<AddRounded />} onClick={() => setIssuing(true)} disabled={workspaces.length === 0}>
      {t('issue.open')}
    </Button>
  );

  return (
    <>
      <PageHeader
        eyebrow={t('common:nav.sectionPersonal')}
        title={t('mine.title')}
        description={t('mine.description')}
        actions={issueButton}
      />
      {workspaces.length === 0 && (
        <Alert severity="info" sx={{ mb: 2 }}>
          {t('issue.noWorkspace')}
        </Alert>
      )}
      <Card>
        <Box sx={{ p: 2, borderBottom: 1, borderColor: 'divider', overflowX: 'auto' }}>
          <KeyStatusFilter value={status} onChange={changeStatus} />
        </Box>
        {keys.isPending ? (
          <LoadingState />
        ) : keys.isError ? (
          <ErrorState error={keys.error} onRetry={() => keys.refetch()} />
        ) : keys.data.length === 0 ? (
          <EmptyState
            icon={<VpnKeyRounded />}
            title={t(`mine.empty.${status}`)}
            description={t(`mine.emptyBody.${status}`)}
            action={status !== 'EXPIRED' && workspaces.length > 0 ? issueButton : undefined}
          />
        ) : (
          <Box sx={{ opacity: keys.isPlaceholderData ? 0.6 : 1 }}>
            <McpKeyList keys={keys.data} showWorkspace canExpire={(k) => k.mine} onExpire={setExpiring} />
          </Box>
        )}
      </Card>
      {keys.data && keys.data.length > 0 && (
        <Typography variant="caption" sx={{ color: 'text.secondary', display: 'block', mt: 1.5 }}>
          {t('mine.summary', { count: keys.data.length })}
        </Typography>
      )}
      {issuing && <IssueKeyDialog preferredWorkspace={me.landingWorkspace} onClose={() => setIssuing(false)} />}
      {expiring && <ExpireKeyDialog target={expiring} onClose={() => setExpiring(null)} />}
    </>
  );
}
