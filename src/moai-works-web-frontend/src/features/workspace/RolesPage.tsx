import AddRounded from '@mui/icons-material/AddRounded';
import CheckRounded from '@mui/icons-material/CheckRounded';
import MoreHorizRounded from '@mui/icons-material/MoreHorizRounded';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import Chip from '@mui/material/Chip';
import IconButton from '@mui/material/IconButton';
import ListItemText from '@mui/material/ListItemText';
import Menu from '@mui/material/Menu';
import MenuItem from '@mui/material/MenuItem';
import Stack from '@mui/material/Stack';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import Typography from '@mui/material/Typography';
import { useQueryClient } from '@tanstack/react-query';
import { Fragment, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';

import { Can, useCan } from '@/features/auth/access';
import { workspaceApi } from '@/shared/api/endpoints';
import { errorMessage } from '@/shared/api/http';
import type { Role } from '@/shared/api/types';
import { ownerOnlyPermissions, permissionGroups, useLabels } from '@/shared/lib/labels';
import { useConfirm } from '@/shared/ui/ConfirmDialog';
import { PageHeader } from '@/shared/ui/PageHeader';
import { ErrorState, LoadingState } from '@/shared/ui/States';

import { useRoleText, useRolesQuery, workspaceKeys } from './queries';
import { RoleEditorDialog, type RoleEditorTarget } from './RoleEditorDialog';
import { canEditRole } from './role-rules';
import { useWorkspace } from './workspace-context';

/** 첫 열(권한 이름)은 가로로 밀어도 남는다 — 휴대폰에서 역할 열을 넘겨 볼 때 어느 권한인지 잃지 않게 */
const stickyCell = {
  position: 'sticky',
  left: 0,
  zIndex: 1,
  bgcolor: 'background.paper',
  borderRight: 1,
  borderRightColor: 'divider',
} as const;

/** 역할(열) × 권한 코드(행) 표 — 사용자 정의 역할도 그대로 열이 되고, 열 머리에 관리 메뉴가 붙는다. */
function RoleMatrix({ roles, myRoles, onManage }: {
  roles: Role[];
  myRoles: string[];
  onManage?: (anchor: HTMLElement, role: Role) => void;
}) {
  const { t } = useTranslation(['workspace', 'common']);
  const labels = useLabels();
  const roleText = useRoleText();
  return (
    <TableContainer>
      <Table size="small" sx={{ minWidth: 160 + roles.length * 128 }}>
        <TableHead>
          <TableRow>
            <TableCell sx={{ ...stickyCell, width: { sm: '36%' }, minWidth: 168 }}>{t('roles.permission')}</TableCell>
            {roles.map((role) => (
              <TableCell key={role.key} align="center" sx={{ verticalAlign: 'bottom', minWidth: 120 }}>
                <Stack spacing={0.25} sx={{ alignItems: 'center' }}>
                  {!role.builtin && (
                    <Chip size="small" variant="outlined" label={t('roles.custom')} sx={{ height: 20, fontSize: 11 }} />
                  )}
                  <Stack direction="row" spacing={0.25} sx={{ alignItems: 'center', maxWidth: '100%' }}>
                    <Typography variant="subtitle2" component="div" noWrap title={roleText.description(role)}>
                      {roleText.name(role)}
                    </Typography>
                    {onManage && !role.builtin && (
                      <IconButton
                        size="small"
                        aria-label={t('roles.manageRole', { name: roleText.name(role) })}
                        onClick={(e) => onManage(e.currentTarget, role)}
                        sx={{ mr: -1 }}
                      >
                        <MoreHorizRounded fontSize="small" />
                      </IconButton>
                    )}
                  </Stack>
                  <Typography variant="caption" sx={{ color: 'text.secondary' }}>
                    {t('roles.count', { count: role.permissions.length })}
                    {role.memberCount !== undefined && ` · ${t('roles.memberCount', { count: role.memberCount })}`}
                  </Typography>
                  {myRoles.includes(role.key) && (
                    <Typography variant="caption" sx={{ color: 'primary.main', fontWeight: 600 }}>
                      {t('roles.mine')}
                    </Typography>
                  )}
                </Stack>
              </TableCell>
            ))}
          </TableRow>
        </TableHead>
        <TableBody>
          {permissionGroups.map(({ group, codes }) => (
            <Fragment key={group}>
              <TableRow>
                <TableCell sx={{ ...stickyCell, bgcolor: 'background.subtle', py: 0.75 }}>
                  <Typography variant="overline" sx={{ color: 'text.secondary' }}>
                    {labels.permissionGroup(group)}
                  </Typography>
                </TableCell>
                <TableCell colSpan={roles.length} sx={{ bgcolor: 'background.subtle', py: 0.75 }} />
              </TableRow>
              {codes.map((code) => (
                <TableRow key={code} hover>
                  <TableCell sx={stickyCell}>
                    <Typography variant="body2">{labels.permission(code)}</Typography>
                    <Typography variant="caption" sx={{ color: 'text.secondary', fontFamily: 'monospace', wordBreak: 'break-all' }}>
                      {code}
                    </Typography>
                    {ownerOnlyPermissions.includes(code) && (
                      <Chip size="small" label={t('roles.ownerOnly')} sx={{ ml: 1 }} variant="outlined" />
                    )}
                  </TableCell>
                  {roles.map((role) => (
                    <TableCell key={role.key} align="center">
                      {role.permissions.includes(code) ? (
                        <CheckRounded fontSize="small" sx={{ color: 'success.main' }} aria-label={t('common:a11y.yes')} />
                      ) : (
                        <Box component="span" sx={{ color: 'text.disabled' }} aria-label={t('common:a11y.no')}>
                          —
                        </Box>
                      )}
                    </TableCell>
                  ))}
                </TableRow>
              ))}
            </Fragment>
          ))}
        </TableBody>
      </Table>
    </TableContainer>
  );
}

/** 메뉴 항목 — 못 하면 꺼 두고 이유를 함께 보인다(이유 글은 흐리게 하지 않는다) */
function ActionItem({ label, reason, onClick, destructive = false }: {
  label: string;
  reason?: string;
  onClick: () => void;
  destructive?: boolean;
}) {
  return (
    <MenuItem
      onClick={onClick}
      disabled={reason !== undefined}
      sx={{
        color: destructive ? 'error.main' : undefined,
        whiteSpace: 'normal',
        maxWidth: 320,
        '&.Mui-disabled': { opacity: 1, color: 'text.disabled' },
      }}
    >
      <ListItemText primary={label} secondary={reason} slotProps={{ secondary: { sx: { color: 'text.secondary' } } }} />
    </MenuItem>
  );
}

/**
 * 역할과 권한 — 표로 보고, role.manage 가 있으면 사용자 정의 역할을 만들고 · 고치고 · 지운다.
 * 기본 역할 4개는 고칠 수 없다. 못 하는 일은 메뉴에서 꺼 두고 이유를 보인다(판정은 api).
 */
export function RolesPage() {
  const { t } = useTranslation('workspace');
  const { workspace } = useWorkspace();
  const can = useCan();
  const roleText = useRoleText();
  const confirm = useConfirm();
  const queryClient = useQueryClient();
  const roles = useRolesQuery(workspace.slug);
  const [menu, setMenu] = useState<{ anchor: HTMLElement; role: Role } | null>(null);
  const [editor, setEditor] = useState<RoleEditorTarget | null>(null);
  const mine = workspace.myPermissions;

  const remove = async (role: Role) => {
    setMenu(null);
    const ok = await confirm({
      title: t('roles.deleteTitle', { name: roleText.name(role) }),
      description: t('roles.deleteBody'),
      confirmLabel: t('roles.delete'),
      destructive: true,
    });
    if (!ok) {
      return;
    }
    try {
      await workspaceApi.deleteRole(workspace.slug, role.key);
      toast.success(t('roles.deleted', { name: roleText.name(role) }));
    } catch (e) {
      toast.error(errorMessage(e));
    } finally {
      void queryClient.invalidateQueries({ queryKey: workspaceKeys.roles(workspace.slug) });
      void queryClient.invalidateQueries({ queryKey: workspaceKeys.audit(workspace.slug) });
    }
  };

  const target = menu?.role;
  const editBlocked = target && !canEditRole(target, mine) ? t('roles.cannotEdit') : undefined;
  const deleteBlocked = !target
    ? undefined
    : editBlocked ??
      ((target.memberCount ?? 0) > 0
        ? t('roles.inUse', { count: target.memberCount ?? 0 })
        : target.key === workspace.defaultRoleKey
          ? t('roles.isDefault')
          : undefined);

  return (
    <>
      <PageHeader
        eyebrow={workspace.name}
        title={t('roles.title')}
        description={t('roles.description')}
        actions={
          <Can permission="role.manage">
            <Button variant="contained" startIcon={<AddRounded />} onClick={() => setEditor({ mode: 'create' })} disabled={!roles.data}>
              {t('roles.create')}
            </Button>
          </Can>
        }
      />
      <Card>
        {roles.isPending ? (
          <LoadingState />
        ) : roles.isError ? (
          <ErrorState error={roles.error} onRetry={() => roles.refetch()} />
        ) : (
          <RoleMatrix
            roles={roles.data}
            myRoles={workspace.myRoles}
            onManage={can('role.manage') ? (anchor, role) => setMenu({ anchor, role }) : undefined}
          />
        )}
      </Card>
      {/* Menu 는 바로 아래 MenuItem 에 초점을 주므로 조건부 조각 대신 항목을 그대로 둔다 */}
      <Menu anchorEl={menu?.anchor} open={menu !== null} onClose={() => setMenu(null)}>
        <ActionItem
          label={t('roles.edit')}
          reason={editBlocked}
          onClick={() => {
            if (target) {
              setEditor({ mode: 'edit', role: target });
            }
            setMenu(null);
          }}
        />
        <ActionItem label={t('roles.delete')} reason={deleteBlocked} destructive onClick={() => target && remove(target)} />
      </Menu>
      {editor && roles.data && <RoleEditorDialog target={editor} roles={roles.data} onClose={() => setEditor(null)} />}
    </>
  );
}
