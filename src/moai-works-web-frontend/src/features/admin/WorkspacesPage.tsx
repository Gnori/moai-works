import SearchRounded from '@mui/icons-material/SearchRounded';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import Chip from '@mui/material/Chip';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import InputAdornment from '@mui/material/InputAdornment';
import Stack from '@mui/material/Stack';
import Tab from '@mui/material/Tab';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import Tabs from '@mui/material/Tabs';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import useMediaQuery from '@mui/material/useMediaQuery';
import { useTheme } from '@mui/material/styles';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useSearchParams } from 'react-router';
import { toast } from 'sonner';

import { adminApi } from '@/shared/api/endpoints';
import { errorMessage, isApiError } from '@/shared/api/http';
import type { AdminWorkspace, WorkspaceStatus } from '@/shared/api/types';
import { date, fileSize } from '@/shared/lib/format';
import { useDebounced } from '@/shared/lib/use-debounced';
import { useConfirm } from '@/shared/ui/ConfirmDialog';
import { PageHeader } from '@/shared/ui/PageHeader';
import { EmptyState, ErrorState, LoadingState } from '@/shared/ui/States';

/** 탭 — 사용 중 · 사용 중지는 팀 워크스페이스만(조치를 하는 곳), 개인은 보기만, 전체는 둘 다 */
const tabs = ['ACTIVE', 'SUSPENDED', 'PERSONAL', 'ALL'] as const;
type TabKey = (typeof tabs)[number];

function tabOf(value: string | null): TabKey {
  return tabs.find((t) => t === value) ?? 'ACTIVE';
}

function filterOf(tab: TabKey): Parameters<typeof adminApi.workspaces>[0] {
  switch (tab) {
    case 'ACTIVE':
      return { kind: 'TEAM', status: 'ACTIVE' };
    case 'SUSPENDED':
      return { kind: 'TEAM', status: 'SUSPENDED' };
    case 'PERSONAL':
      return { kind: 'PERSONAL' };
    default:
      return {};
  }
}

const statusColor: Record<WorkspaceStatus, 'default' | 'success' | 'warning' | 'error' | 'info'> = {
  PROVISIONING: 'info',
  ACTIVE: 'success',
  SUSPENDED: 'error',
  DELETION_PENDING: 'warning',
  DELETED: 'default',
};

/** 휴대폰에서 열을 넘겨도 어느 줄인지 잃지 않게 이름 칸을 붙여 둔다 (WEB-DESIGN §4) */
const stickyCell = {
  position: 'sticky',
  left: 0,
  zIndex: 1,
  bgcolor: 'background.paper',
  borderRight: { xs: 1, md: 0 },
  borderRightColor: 'divider',
} as const;

const adminWorkspacesKey = ['admin', 'workspaces'] as const;

/**
 * 워크스페이스 관리 (시스템 관리자) — 이름 · 주소 · 소유자 · 멤버 수 · 상태까지. 내용은 볼 수 없다.
 * 팀 워크스페이스는 사용 중지(이유를 받는다) · 다시 사용, 지우기는 사용 중지한 것만(영향 미리보기 + 주소 입력 — api 도 다시 본다).
 * 개인 워크스페이스는 보기만 한다 — 그 사람을 막으려면 계정을 정지한다. 고른 탭은 주소에 남는다(?tab=SUSPENDED).
 * 경로 가드가 system-admin 을 확인한다(표시만 — 판정은 api).
 */
export function WorkspacesPage() {
  const { t } = useTranslation('admin');
  const confirm = useConfirm();
  const queryClient = useQueryClient();
  const [params, setParams] = useSearchParams();
  const tab = tabOf(params.get('tab'));
  const [typed, setTyped] = useState('');
  const query = useDebounced(typed.trim(), 250);
  const [suspendTarget, setSuspendTarget] = useState<AdminWorkspace | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<AdminWorkspace | null>(null);
  const list = useQuery({
    queryKey: [...adminWorkspacesKey, tab, query],
    queryFn: () => adminApi.workspaces({ ...filterOf(tab), query: query || undefined }),
  });

  const changeTab = (next: TabKey) => {
    const copy = new URLSearchParams(params);
    if (next === 'ACTIVE') {
      copy.delete('tab');
    } else {
      copy.set('tab', next);
    }
    setParams(copy, { replace: true });
  };

  const resume = async (ws: AdminWorkspace) => {
    const ok = await confirm({
      title: t('workspaces.resume.title', { name: ws.name }),
      description: t('workspaces.resume.body'),
      confirmLabel: t('workspaces.actions.resume'),
    });
    if (!ok) {
      return;
    }
    try {
      await adminApi.resumeWorkspace(ws.slug);
      toast.success(t('workspaces.resume.done', { name: ws.name }));
      void queryClient.invalidateQueries({ queryKey: adminWorkspacesKey });
    } catch (e) {
      toast.error(errorMessage(e));
    }
  };

  return (
    <>
      <PageHeader eyebrow={t('eyebrow')} title={t('workspaces.title')} description={t('workspaces.description')} />
      <Card>
        <Tabs value={tab} onChange={(_, v: TabKey) => changeTab(v)} variant="scrollable" sx={{ px: 1, borderBottom: 1, borderColor: 'divider' }}>
          {tabs.map((k) => (
            <Tab key={k} value={k} label={t(`workspaces.tabs.${k}`)} />
          ))}
        </Tabs>
        <Box sx={{ px: 2, pt: 2, pb: 1 }}>
          <TextField
            size="small"
            fullWidth
            value={typed}
            onChange={(e) => setTyped(e.target.value)}
            placeholder={t('workspaces.search')}
            sx={{ maxWidth: 420 }}
            slotProps={{
              htmlInput: { 'aria-label': t('workspaces.search'), spellCheck: false },
              input: {
                startAdornment: (
                  <InputAdornment position="start">
                    <SearchRounded fontSize="small" />
                  </InputAdornment>
                ),
              },
            }}
          />
        </Box>
        {list.isPending ? (
          <LoadingState />
        ) : list.isError ? (
          <ErrorState error={list.error} onRetry={() => list.refetch()} />
        ) : list.data.length === 0 ? (
          <EmptyState title={query ? t('workspaces.emptySearch') : t('workspaces.empty')} />
        ) : (
          <TableContainer>
            <Table>
              <TableHead>
                <TableRow>
                  <TableCell sx={{ ...stickyCell, minWidth: 180 }}>{t('workspaces.colWorkspace')}</TableCell>
                  <TableCell sx={{ minWidth: 150 }}>{t('workspaces.colOwner')}</TableCell>
                  <TableCell align="right">{t('workspaces.colMembers')}</TableCell>
                  <TableCell>{t('workspaces.colStatus')}</TableCell>
                  <TableCell sx={{ display: { xs: 'none', md: 'table-cell' } }}>{t('workspaces.colCreated')}</TableCell>
                  <TableCell align="right" />
                </TableRow>
              </TableHead>
              <TableBody>
                {list.data.map((ws) => (
                  <WorkspaceRow
                    key={ws.id}
                    ws={ws}
                    showKind={tab === 'ALL'}
                    onSuspend={() => setSuspendTarget(ws)}
                    onResume={() => resume(ws)}
                    onDelete={() => setDeleteTarget(ws)}
                  />
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </Card>
      {suspendTarget && <SuspendDialog key={suspendTarget.id} workspace={suspendTarget} onClose={() => setSuspendTarget(null)} />}
      {deleteTarget && <DeleteDialog key={deleteTarget.id} workspace={deleteTarget} onClose={() => setDeleteTarget(null)} />}
    </>
  );
}

function WorkspaceRow({ ws, showKind, onSuspend, onResume, onDelete }: {
  ws: AdminWorkspace;
  showKind: boolean;
  onSuspend: () => void;
  onResume: () => void;
  onDelete: () => void;
}) {
  const { t } = useTranslation('admin');
  const owner = ws.owners[0];
  const team = ws.kind === 'TEAM';
  return (
    <TableRow hover>
      <TableCell sx={stickyCell}>
        <Typography variant="subtitle2" noWrap title={ws.name} sx={{ maxWidth: { xs: 180, md: 360 } }}>
          {ws.name}
          {showKind && <Chip size="small" variant="outlined" label={t(`workspaces.kind.${ws.kind}`)} sx={{ ml: 1, height: 20, fontSize: 11 }} />}
        </Typography>
        <Typography variant="caption" noWrap sx={{ color: 'text.secondary', display: 'block', fontFamily: 'monospace' }}>
          {ws.slug}
        </Typography>
      </TableCell>
      <TableCell>
        {owner ? (
          <Box sx={{ minWidth: 0 }}>
            <Typography variant="body2" noWrap title={owner.email}>
              {owner.displayName}
              {ws.owners.length > 1 && (
                <Typography component="span" variant="caption" sx={{ color: 'text.secondary', ml: 0.75 }}>
                  {t('workspaces.moreOwners', { count: ws.owners.length - 1 })}
                </Typography>
              )}
            </Typography>
            <Typography variant="caption" noWrap sx={{ color: 'text.secondary', display: 'block' }}>
              {owner.email}
            </Typography>
          </Box>
        ) : (
          <Typography variant="body2" sx={{ color: 'text.secondary' }}>
            {t('workspaces.noOwner')}
          </Typography>
        )}
      </TableCell>
      <TableCell align="right" sx={{ fontVariantNumeric: 'tabular-nums', whiteSpace: 'nowrap' }}>
        {t('workspaces.memberCount', { count: ws.memberCount })}
      </TableCell>
      <TableCell>
        <Chip size="small" color={statusColor[ws.status]} variant="outlined" label={t(`workspaces.status.${ws.status}`)} />
        {ws.status === 'SUSPENDED' && ws.suspendedAt && (
          <Typography variant="caption" sx={{ color: 'text.secondary', display: 'block', mt: 0.5, whiteSpace: 'nowrap' }}>
            {t('workspaces.suspendedSince', { date: date(ws.suspendedAt) })}
          </Typography>
        )}
        {ws.status === 'SUSPENDED' && ws.suspendedReason && (
          <Typography
            variant="caption"
            title={ws.suspendedReason}
            sx={{ color: 'text.secondary', display: 'block', maxWidth: 240, overflowWrap: 'anywhere' }}
          >
            {t('workspaces.suspendedReason', { reason: ws.suspendedReason })}
          </Typography>
        )}
      </TableCell>
      <TableCell sx={{ display: { xs: 'none', md: 'table-cell' }, color: 'text.secondary', whiteSpace: 'nowrap' }}>{date(ws.createdAt)}</TableCell>
      <TableCell align="right">
        {team ? (
          <Stack direction="row" spacing={1} sx={{ justifyContent: 'flex-end' }}>
            {ws.status === 'ACTIVE' && (
              <Button size="small" variant="outlined" color="error" onClick={onSuspend} aria-label={`${t('workspaces.actions.suspend')} — ${ws.name}`}>
                {t('workspaces.actions.suspend')}
              </Button>
            )}
            {ws.status === 'SUSPENDED' && (
              <>
                <Button size="small" variant="contained" onClick={onResume} aria-label={`${t('workspaces.actions.resume')} — ${ws.name}`}>
                  {t('workspaces.actions.resume')}
                </Button>
                <Button size="small" variant="outlined" color="error" onClick={onDelete} aria-label={`${t('workspaces.actions.delete')} — ${ws.name}`}>
                  {t('workspaces.actions.delete')}
                </Button>
              </>
            )}
          </Stack>
        ) : (
          // 개인 워크스페이스는 조치가 없다 — 이유를 함께 보인다(WEB-DESIGN §3)
          <Typography variant="caption" sx={{ color: 'text.secondary', whiteSpace: 'nowrap' }} title={t('workspaces.personalHintLong')}>
            {t('workspaces.personalHint')}
          </Typography>
        )}
      </TableCell>
    </TableRow>
  );
}

/** 사용 중지 — 이유를 받는다(시스템 관리 화면 · 감사에만 남는다) */
function SuspendDialog({ workspace, onClose }: { workspace: AdminWorkspace; onClose: () => void }) {
  const { t } = useTranslation(['admin', 'common']);
  const queryClient = useQueryClient();
  const fullScreen = useMediaQuery(useTheme().breakpoints.down('sm'));
  const [reason, setReason] = useState('');
  const valid = reason.trim().length > 0 && reason.length <= 500;

  const suspend = useMutation({
    mutationFn: () => adminApi.suspendWorkspace(workspace.slug, reason.trim()),
    onSuccess: () => {
      toast.success(t('workspaces.suspend.done', { name: workspace.name }));
      void queryClient.invalidateQueries({ queryKey: adminWorkspacesKey });
      onClose();
    },
    onError: (e) => toast.error(errorMessage(e)),
  });

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="sm" fullScreen={fullScreen}>
      <DialogTitle sx={{ fontWeight: 700, overflowWrap: 'anywhere' }}>{t('workspaces.suspend.title', { name: workspace.name })}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ pt: 0.5 }}>
          <Alert severity="warning">{t('workspaces.suspend.body')}</Alert>
          <TextField
            autoFocus
            fullWidth
            multiline
            minRows={3}
            label={t('workspaces.suspend.reason')}
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            error={reason.length > 500}
            helperText={`${t('workspaces.suspend.reasonHelp')} ${reason.length}/500`}
            slotProps={{ htmlInput: { maxLength: 500 } }}
          />
        </Stack>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2.5 }}>
        <Button color="inherit" onClick={onClose}>
          {t('common:actions.cancel')}
        </Button>
        <Button variant="contained" color="error" disabled={!valid} loading={suspend.isPending} onClick={() => suspend.mutate()}>
          {t('workspaces.suspend.confirm')}
        </Button>
      </DialogActions>
    </Dialog>
  );
}

/**
 * 지우기 — 되돌릴 수 없어서 영향(수)을 먼저 보이고 주소를 그대로 입력해야 단추가 통한다(위험 작업 확인 — api 도 같은 값을 다시 본다).
 * 사용 중지한 워크스페이스만 지울 수 있다(아니면 api 가 409 WORKSPACE_NOT_SUSPENDED).
 */
function DeleteDialog({ workspace, onClose }: { workspace: AdminWorkspace; onClose: () => void }) {
  const { t } = useTranslation(['admin', 'common']);
  const queryClient = useQueryClient();
  const fullScreen = useMediaQuery(useTheme().breakpoints.down('sm'));
  const [typed, setTyped] = useState('');
  const preview = useQuery({
    queryKey: [...adminWorkspacesKey, 'deletion-preview', workspace.slug],
    queryFn: () => adminApi.workspaceDeletionPreview(workspace.slug),
    staleTime: 0,
  });

  const remove = useMutation({
    mutationFn: () => adminApi.deleteWorkspace(workspace.slug, typed.trim()),
    onSuccess: () => {
      toast.success(t('workspaces.delete.done', { name: workspace.name }));
      void queryClient.invalidateQueries({ queryKey: adminWorkspacesKey });
      onClose();
    },
    onError: (e) =>
      toast.error(isApiError(e, 'WORKSPACE_NOT_SUSPENDED') ? t('workspaces.delete.notSuspended') : errorMessage(e)),
  });

  const rows = preview.data
    ? [
        { label: t('workspaces.delete.members'), value: t('workspaces.delete.count', { count: preview.data.members }) },
        { label: t('workspaces.delete.projects'), value: t('workspaces.delete.count', { count: preview.data.projects }) },
        { label: t('workspaces.delete.issues'), value: t('workspaces.delete.count', { count: preview.data.issues }) },
        {
          label: t('workspaces.delete.attachments'),
          value: t('workspaces.delete.attachmentSize', { count: preview.data.attachments, size: fileSize(preview.data.attachmentBytes) }),
        },
      ]
    : [];

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="sm" fullScreen={fullScreen}>
      <DialogTitle sx={{ fontWeight: 700, overflowWrap: 'anywhere' }}>{t('workspaces.delete.title', { name: workspace.name })}</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ pt: 0.5 }}>
          <Alert severity="error">{t('workspaces.delete.warning')}</Alert>
          {preview.isPending ? (
            <LoadingState />
          ) : preview.isError ? (
            <ErrorState error={preview.error} onRetry={() => preview.refetch()} />
          ) : (
            <Box>
              <Typography variant="subtitle2" sx={{ mb: 1 }}>
                {t('workspaces.delete.impact')}
              </Typography>
              <Box
                component="dl"
                sx={{ m: 0, display: 'grid', gridTemplateColumns: { xs: 'repeat(2, 1fr)', sm: 'repeat(4, 1fr)' }, gap: 1.5 }}
              >
                {rows.map((row) => (
                  <Box key={row.label} sx={{ p: 1.5, borderRadius: 2, bgcolor: 'background.subtle', minWidth: 0 }}>
                    <Typography component="dt" variant="caption" sx={{ color: 'text.secondary' }}>
                      {row.label}
                    </Typography>
                    <Typography component="dd" variant="subtitle1" sx={{ m: 0, fontVariantNumeric: 'tabular-nums', overflowWrap: 'anywhere' }}>
                      {row.value}
                    </Typography>
                  </Box>
                ))}
              </Box>
            </Box>
          )}
          <TextField
            label={t('workspaces.delete.confirmLabel', { slug: workspace.slug })}
            value={typed}
            onChange={(e) => setTyped(e.target.value)}
            disabled={!preview.isSuccess}
            slotProps={{ htmlInput: { spellCheck: false, autoCapitalize: 'none', autoCorrect: 'off', sx: { fontFamily: 'monospace' } } }}
          />
        </Stack>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2.5 }}>
        <Button color="inherit" onClick={onClose}>
          {t('common:actions.cancel')}
        </Button>
        <Button
          variant="contained"
          color="error"
          disabled={!preview.isSuccess || typed.trim() !== workspace.slug}
          loading={remove.isPending}
          onClick={() => remove.mutate()}
        >
          {t('workspaces.delete.confirm')}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
