import Card from '@mui/material/Card';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import Typography from '@mui/material/Typography';
import { useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';

import { workspaceApi } from '@/shared/api/endpoints';
import { dateTime } from '@/shared/lib/format';
import { useLabels } from '@/shared/lib/labels';
import { PageHeader } from '@/shared/ui/PageHeader';
import { EmptyState, ErrorState, LoadingState } from '@/shared/ui/States';

import { workspaceKeys } from './queries';
import { useWorkspace } from './workspace-context';

/** 감사 내용(JSON 문자열)을 한 줄로 — 글자로만 보이고 HTML 로 해석하지 않는다. */
function summarize(details: string): string {
  try {
    const value = JSON.parse(details) as Record<string, unknown>;
    return Object.entries(value)
      .map(([k, v]) => `${k}: ${typeof v === 'object' ? JSON.stringify(v) : String(v)}`)
      .join(' · ');
  } catch {
    return details;
  }
}

/** 감사 기록 — 누가 언제 무엇을 바꿨나 (최근 100건). 기록은 지우거나 고칠 수 없다. */
export function AuditPage() {
  const { t } = useTranslation('workspace');
  const labels = useLabels();
  const { workspace } = useWorkspace();
  const audit = useQuery({ queryKey: workspaceKeys.audit(workspace.slug), queryFn: () => workspaceApi.audit(workspace.slug) });

  return (
    <>
      <PageHeader eyebrow={workspace.name} title={t('audit.title')} description={t('audit.description')} />
      <Card>
        {audit.isPending ? (
          <LoadingState />
        ) : audit.isError ? (
          <ErrorState error={audit.error} onRetry={() => audit.refetch()} />
        ) : audit.data.length === 0 ? (
          <EmptyState title={t('audit.empty')} />
        ) : (
          <TableContainer>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell sx={{ whiteSpace: 'nowrap' }}>{t('audit.colTime')}</TableCell>
                  <TableCell>{t('audit.colActor')}</TableCell>
                  <TableCell>{t('audit.colAction')}</TableCell>
                  <TableCell sx={{ display: { xs: 'none', md: 'table-cell' } }}>{t('audit.colDetails')}</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {audit.data.map((a) => (
                  <TableRow key={a.id} hover>
                    <TableCell sx={{ whiteSpace: 'nowrap', fontVariantNumeric: 'tabular-nums', color: 'text.secondary' }}>
                      {dateTime(a.occurredAt)}
                    </TableCell>
                    <TableCell sx={{ overflowWrap: 'anywhere' }}>{a.actorName ?? a.actorType}</TableCell>
                    <TableCell>
                      <Typography variant="body2" title={a.action}>
                        {labels.auditAction(a.action)}
                      </Typography>
                    </TableCell>
                    <TableCell sx={{ display: { xs: 'none', md: 'table-cell' }, color: 'text.secondary', maxWidth: 420 }}>
                      <Typography variant="caption" sx={{ wordBreak: 'break-all' }}>
                        {summarize(a.details)}
                      </Typography>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </Card>
    </>
  );
}
