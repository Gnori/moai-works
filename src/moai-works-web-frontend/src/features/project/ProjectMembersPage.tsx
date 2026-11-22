import MoreHorizRounded from '@mui/icons-material/MoreHorizRounded';
import PersonAddAlt1Rounded from '@mui/icons-material/PersonAddAlt1Rounded';
import Alert from '@mui/material/Alert';
import Autocomplete from '@mui/material/Autocomplete';
import Avatar from '@mui/material/Avatar';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Card from '@mui/material/Card';
import Checkbox from '@mui/material/Checkbox';
import Chip from '@mui/material/Chip';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import FormControlLabel from '@mui/material/FormControlLabel';
import IconButton from '@mui/material/IconButton';
import Menu from '@mui/material/Menu';
import MenuItem from '@mui/material/MenuItem';
import Stack from '@mui/material/Stack';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableContainer from '@mui/material/TableContainer';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import useMediaQuery from '@mui/material/useMediaQuery';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';

import { useCan } from '@/features/auth/access';
import { useMe } from '@/features/auth/session';
import { usePeopleQuery } from '@/features/issue/queries';
import { workspaceKeys } from '@/features/workspace/queries';
import { useWorkspace } from '@/features/workspace/workspace-context';
import { projectApi } from '@/shared/api/endpoints';
import { errorMessage } from '@/shared/api/http';
import { projectRoleKeys, type Member, type ProjectDetail, type ProjectRoleHolder } from '@/shared/api/types';
import { initials } from '@/shared/lib/format';
import { useConfirm } from '@/shared/ui/ConfirmDialog';
import { PageHeader } from '@/shared/ui/PageHeader';
import { EmptyState, ErrorState, LoadingState } from '@/shared/ui/States';

import { useProject } from './project-context';
import { canGrantProjectRole, isProjectRole, sortProjectRoles } from './project-roles';
import { projectCan, projectKeys, useProjectRolesQuery } from './queries';

/** 프로젝트 역할 칩 — 이름을 글로 적는다. 프로젝트 관리자만 주 색으로 */
function ProjectRoleChips({ roles }: { roles: string[] }) {
  const { t } = useTranslation('workspace');
  return (
    <Stack direction="row" spacing={0.5} useFlexGap sx={{ flexWrap: 'wrap' }}>
      {sortProjectRoles(roles).map((role) => (
        <Chip
          key={role}
          size="small"
          label={isProjectRole(role) ? t(`projects.roles.${role}`) : role}
          variant={role === 'project-admin' ? 'filled' : 'outlined'}
          sx={role === 'project-admin' ? { bgcolor: 'primary.light', color: 'primary.main' } : undefined}
        />
      ))}
    </Stack>
  );
}

/** 역할 고르기 칸 — 내 권한 밖의 역할은 꺼 두고 이유를 함께 보인다(api 도 같은 규칙으로 막는다) */
function RoleCheckboxes({ project, selected, onToggle }: {
  project: ProjectDetail;
  /** 고른 역할 — 내 권한 밖의 역할을 이미 가졌으면 꺼진 채 그대로 남는다(손대지 않으므로 api 도 받아 준다) */
  selected: string[];
  onToggle: (role: string) => void;
}) {
  const { t } = useTranslation('workspace');
  return (
    <Stack>
      {projectRoleKeys.map((role) => {
        const allowed = canGrantProjectRole(role, project.myPermissions);
        return (
          <FormControlLabel
            key={role}
            disabled={!allowed}
            control={<Checkbox checked={selected.includes(role)} onChange={() => onToggle(role)} />}
            label={
              <Box>
                <Typography variant="subtitle2">{t(`projects.roles.${role}`)}</Typography>
                <Typography variant="caption" sx={{ color: 'text.secondary', display: 'block' }}>
                  {t(`projects.roleDescriptions.${role}`)}
                  {!allowed && ` · ${t('projects.members.roleBeyond')}`}
                </Typography>
              </Box>
            }
            sx={{
              alignItems: 'flex-start',
              py: 0.75,
              '& .MuiCheckbox-root': { pt: 0.5 },
              '& .MuiFormControlLabel-label.Mui-disabled': { color: 'text.secondary' },
            }}
          />
        );
      })}
    </Stack>
  );
}

/** 한 사람의 프로젝트 역할 바꾸기 · 처음 주기 — 사람을 정하지 않았으면(추가) 워크스페이스 멤버에서 고른다 */
function RolesDialog({ project, holder, holders, onClose }: {
  project: ProjectDetail;
  /** 있으면 그 사람의 역할 바꾸기, 없으면 멤버 추가 */
  holder?: ProjectRoleHolder;
  holders: ProjectRoleHolder[];
  onClose: () => void;
}) {
  const { t } = useTranslation(['workspace', 'common']);
  const { workspace } = useWorkspace();
  const can = useCan();
  const queryClient = useQueryClient();
  const fullScreen = useMediaQuery((theme) => theme.breakpoints.down('sm'));
  const adding = !holder;
  const people = usePeopleQuery(workspace.slug, adding && can('member.read'));
  const [picked, setPicked] = useState<Member | null>(null);
  const [selected, setSelected] = useState<string[]>(holder?.roles ?? []);
  const name = holder ? (holder.displayName ?? holder.username) : (picked?.displayName ?? '');

  const candidates = useMemo(
    () => (people.data ?? []).filter((m) => m.status === 'ACTIVE' && !holders.some((h) => h.userId === m.userId)),
    [people.data, holders],
  );

  const mutation = useMutation({
    mutationFn: () => projectApi.setRoles(workspace.slug, project.key, holder?.userId ?? picked?.userId ?? '', selected),
    onSuccess: () => {
      toast.success(t('projects.members.saved', { name }));
      void queryClient.invalidateQueries({ queryKey: projectKeys.roles(workspace.slug, project.key) });
      void queryClient.invalidateQueries({ queryKey: workspaceKeys.audit(workspace.slug) });
      onClose();
    },
    onError: (e) => toast.error(errorMessage(e)),
  });
  const toggle = (key: string) => setSelected((s) => (s.includes(key) ? s.filter((k) => k !== key) : [...s, key]));
  const unchanged = holder && [...selected].sort().join() === [...holder.roles].sort().join();

  return (
    <Dialog open onClose={onClose} maxWidth="xs" fullWidth fullScreen={fullScreen}>
      <DialogTitle sx={{ fontWeight: 700 }}>
        {adding ? t('projects.members.addTitle') : t('projects.members.rolesDialogTitle', { name })}
      </DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ pt: 0.5 }}>
          <Typography variant="body2" sx={{ color: 'text.secondary' }}>
            {adding ? t('projects.members.addBody') : t('projects.members.rolesDialogBody')}
          </Typography>
          {adding &&
            (people.isError || !can('member.read') ? (
              <Alert severity="warning">{t('projects.members.membersUnavailable')}</Alert>
            ) : (
              <Autocomplete
                value={picked}
                onChange={(_, next) => setPicked(next)}
                options={candidates}
                loading={people.isPending}
                getOptionLabel={(m) => m.displayName || m.username}
                getOptionKey={(m) => m.userId}
                isOptionEqualToValue={(a, b) => a.userId === b.userId}
                filterOptions={(list, state) => {
                  const q = state.inputValue.trim().toLowerCase();
                  return q
                    ? list.filter((m) => [m.displayName, m.username, m.email].some((v) => v.toLowerCase().includes(q)))
                    : list;
                }}
                noOptionsText={t('projects.members.pickNoOptions')}
                renderOption={(props, m) => {
                  const { key, ...rest } = props;
                  return (
                    <Box component="li" key={key} {...rest} sx={{ display: 'block !important' }}>
                      <Typography variant="body2" sx={{ fontWeight: 600 }}>
                        {m.displayName}
                      </Typography>
                      <Typography variant="caption" sx={{ color: 'text.secondary' }}>
                        {m.email}
                      </Typography>
                    </Box>
                  );
                }}
                renderInput={(params) => (
                  <TextField
                    {...params}
                    label={t('projects.members.pickLabel')}
                    placeholder={t('projects.members.pickPlaceholder')}
                    autoFocus
                    slotProps={{ ...params.slotProps, inputLabel: { ...params.slotProps.inputLabel, shrink: true } }}
                  />
                )}
              />
            ))}
          <RoleCheckboxes project={project} selected={selected} onToggle={toggle} />
        </Stack>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2.5 }}>
        <Button onClick={onClose} color="inherit">
          {t('common:actions.cancel')}
        </Button>
        <Button
          variant="contained"
          onClick={() => mutation.mutate()}
          loading={mutation.isPending}
          disabled={adding ? !picked || selected.length === 0 : unchanged || selected.length === 0}
        >
          {t('common:actions.save')}
        </Button>
      </DialogActions>
    </Dialog>
  );
}

/**
 * 프로젝트 역할(멤버) — 이 프로젝트에서 프로젝트 역할을 받은 사람. 보기는 독자 누구나,
 * 주고 바꾸고 빼기는 project.manage 가 있을 때만 단추가 보인다(표시만 — 판정은 api, 권한 밖의 역할은 api 가 막는다).
 */
export function ProjectMembersPage() {
  const { t } = useTranslation(['workspace', 'common']);
  const { workspace } = useWorkspace();
  const project = useProject();
  const me = useMe();
  const confirm = useConfirm();
  const queryClient = useQueryClient();
  const holders = useProjectRolesQuery(workspace.slug, project.key);
  const [menu, setMenu] = useState<{ anchor: HTMLElement; holder: ProjectRoleHolder } | null>(null);
  const [dialog, setDialog] = useState<{ holder?: ProjectRoleHolder } | null>(null);

  const canManage = projectCan(project, 'project.manage') && project.status === 'ACTIVE';
  /** 이 사람의 역할 가운데 내가 손댈 수 없는 것이 있으면 모두 빼기는 못 한다 */
  const canRemoveAll = (h: ProjectRoleHolder) =>
    h.roles.every((role) => !isProjectRole(role) || canGrantProjectRole(role, project.myPermissions));

  const remove = async (h: ProjectRoleHolder) => {
    setMenu(null);
    const name = h.displayName ?? h.username;
    const ok = await confirm({
      title: t('projects.members.removeTitle', { name }),
      description: t('projects.members.removeBody'),
      confirmLabel: t('projects.members.removeAll'),
      destructive: true,
    });
    if (!ok) {
      return;
    }
    try {
      await projectApi.setRoles(workspace.slug, project.key, h.userId, []);
      toast.success(t('projects.members.removed', { name }));
      void queryClient.invalidateQueries({ queryKey: projectKeys.roles(workspace.slug, project.key) });
      void queryClient.invalidateQueries({ queryKey: workspaceKeys.audit(workspace.slug) });
    } catch (e) {
      toast.error(errorMessage(e));
    }
  };

  return (
    <>
      <PageHeader
        eyebrow={project.name}
        title={t('projects.members.title')}
        description={t('projects.members.description')}
        actions={
          canManage && (
            <Button variant="contained" startIcon={<PersonAddAlt1Rounded />} onClick={() => setDialog({})} disabled={!holders.data}>
              {t('projects.members.add')}
            </Button>
          )
        }
      />
      <Alert severity="info" sx={{ mb: 2.5 }}>
        {project.visibility === 'RESTRICTED' ? t('projects.members.noteRestricted') : t('projects.members.noteWorkspace')}
      </Alert>
      <Card>
        {holders.isPending ? (
          <LoadingState />
        ) : holders.isError ? (
          <ErrorState error={holders.error} onRetry={() => holders.refetch()} />
        ) : holders.data.length === 0 ? (
          <EmptyState
            title={t('projects.members.emptyTitle')}
            description={canManage ? t('projects.members.emptyBodyManager') : t('projects.members.emptyBody')}
            action={
              canManage && (
                <Button variant="outlined" startIcon={<PersonAddAlt1Rounded />} onClick={() => setDialog({})}>
                  {t('projects.members.add')}
                </Button>
              )
            }
          />
        ) : (
          <TableContainer>
            <Table>
              <TableHead>
                <TableRow>
                  <TableCell>{t('projects.members.colMember')}</TableCell>
                  <TableCell>{t('projects.members.colRoles')}</TableCell>
                  <TableCell align="right" width={56} />
                </TableRow>
              </TableHead>
              <TableBody>
                {holders.data.map((h) => {
                  const name = h.displayName ?? h.username;
                  return (
                    <TableRow key={h.userId} hover>
                      <TableCell sx={{ maxWidth: { xs: 160, sm: 'none' } }}>
                        <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center', minWidth: 0 }}>
                          <Avatar sx={{ width: 32, height: 32, fontSize: 13, fontWeight: 700 }}>{initials(name)}</Avatar>
                          <Box sx={{ minWidth: 0 }}>
                            <Typography variant="subtitle2" noWrap title={name}>
                              {name}
                              {h.userId === me.id && (
                                <Typography component="span" variant="caption" sx={{ color: 'text.secondary', ml: 0.75 }}>
                                  {t('projects.members.me')}
                                </Typography>
                              )}
                            </Typography>
                            <Typography variant="caption" noWrap sx={{ color: 'text.secondary', display: 'block', fontFamily: 'monospace' }}>
                              {h.username}
                            </Typography>
                            {h.workspaceOwner && (
                              <Typography variant="caption" sx={{ color: 'text.secondary', display: 'block' }} title={t('projects.members.workspaceOwnerHint')}>
                                {t('projects.members.workspaceOwner')}
                              </Typography>
                            )}
                          </Box>
                        </Stack>
                      </TableCell>
                      <TableCell>
                        <ProjectRoleChips roles={h.roles} />
                      </TableCell>
                      <TableCell align="right">
                        {canManage && !h.workspaceOwner && (
                          <IconButton aria-label={t('projects.members.manage', { name })} onClick={(e) => setMenu({ anchor: e.currentTarget, holder: h })}>
                            <MoreHorizRounded />
                          </IconButton>
                        )}
                      </TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </Card>
      {holders.data && holders.data.length > 0 && (
        <Typography variant="caption" sx={{ color: 'text.secondary', display: 'block', mt: 1.5 }}>
          {t('projects.members.summary', { count: holders.data.length })}
        </Typography>
      )}
      {/* Menu 는 바로 아래 MenuItem 에 초점을 주므로 조건부 조각으로 감싸지 않는다 */}
      <Menu anchorEl={menu?.anchor} open={menu !== null} onClose={() => setMenu(null)}>
        <MenuItem
          onClick={() => {
            setDialog({ holder: menu?.holder });
            setMenu(null);
          }}
        >
          {t('projects.members.changeRoles')}
        </MenuItem>
        {menu && canRemoveAll(menu.holder) && (
          <MenuItem onClick={() => void remove(menu.holder)} sx={{ color: 'error.main' }}>
            {t('projects.members.removeAll')}
          </MenuItem>
        )}
      </Menu>
      {dialog && holders.data && (
        <RolesDialog project={project} holder={dialog.holder} holders={holders.data} onClose={() => setDialog(null)} />
      )}
    </>
  );
}
