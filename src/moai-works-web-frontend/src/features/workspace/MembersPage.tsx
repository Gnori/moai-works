import LogoutRounded from '@mui/icons-material/LogoutRounded';
import MoreHorizRounded from '@mui/icons-material/MoreHorizRounded';
import PersonAddAlt1Rounded from '@mui/icons-material/PersonAddAlt1Rounded';
import SearchRounded from '@mui/icons-material/SearchRounded';
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
import InputAdornment from '@mui/material/InputAdornment';
import Menu from '@mui/material/Menu';
import MenuItem from '@mui/material/MenuItem';
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
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate, useSearchParams } from 'react-router';
import { toast } from 'sonner';

import { useCan } from '@/features/auth/access';
import { InviteDialog } from '@/features/invitations/InviteDialog';
import { SentInvitations } from '@/features/invitations/SentInvitations';
import { useMe, useSession } from '@/features/auth/session';
import { workspaceApi } from '@/shared/api/endpoints';
import { errorMessage } from '@/shared/api/http';
import type { Member, Role } from '@/shared/api/types';
import { date, initials } from '@/shared/lib/format';
import { useLabels } from '@/shared/lib/labels';
import { useConfirm } from '@/shared/ui/ConfirmDialog';
import { PageHeader } from '@/shared/ui/PageHeader';
import { RoleChips } from '@/shared/ui/RoleChips';
import { EmptyState, ErrorState, LoadingState } from '@/shared/ui/States';

import { customRoleNames, useRoleText, useRolesQuery, workspaceKeys } from './queries';
import { canGrantRole } from './role-rules';
import { useWorkspace } from './workspace-context';

/**
 * 멤버의 역할 고르기 — 여러 개를 고르면 권한이 합쳐진다.
 * 내 권한보다 큰 역할은 주지도 빼지도 못하므로 꺼 둔다(api 도 같은 규칙으로 막는다).
 */
function MemberRolesDialog({ member, roles, onClose }: { member: Member; roles: Role[]; onClose: () => void }) {
  const { t } = useTranslation(['workspace', 'common']);
  const { workspace } = useWorkspace();
  const roleText = useRoleText();
  const queryClient = useQueryClient();
  const [selected, setSelected] = useState<string[]>(member.roles);
  const mutation = useMutation({
    mutationFn: () => workspaceApi.setRoles(workspace.slug, member.userId, selected),
    onSuccess: () => {
      toast.success(t('members.rolesSaved', { name: member.displayName }));
      void queryClient.invalidateQueries({ queryKey: workspaceKeys.members(workspace.slug) });
      onClose();
    },
    onError: (e) => toast.error(errorMessage(e)),
  });
  const toggle = (key: string) => setSelected((s) => (s.includes(key) ? s.filter((k) => k !== key) : [...s, key]));

  return (
    <Dialog open onClose={onClose} maxWidth="xs" fullWidth>
      <DialogTitle sx={{ fontWeight: 700 }}>{t('members.rolesDialogTitle', { name: member.displayName })}</DialogTitle>
      <DialogContent>
        <Typography variant="body2" sx={{ color: 'text.secondary', mb: 1.5 }}>
          {t('members.rolesDialogBody')}
        </Typography>
        <Stack>
          {roles.map((role) => (
            <FormControlLabel
              key={role.key}
              disabled={!canGrantRole(role, workspace.myPermissions)}
              control={<Checkbox checked={selected.includes(role.key)} onChange={() => toggle(role.key)} />}
              label={
                <Box>
                  <Typography variant="subtitle2">{roleText.name(role)}</Typography>
                  <Typography variant="caption" sx={{ color: 'text.secondary' }}>
                    {roleText.description(role)} · {t('members.rolePermissionCount', { count: role.permissions.length })}
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
          ))}
        </Stack>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2.5 }}>
        <Button onClick={onClose} color="inherit">
          {t('common:actions.cancel')}
        </Button>
        <Button variant="contained" onClick={() => mutation.mutate()} loading={mutation.isPending} disabled={selected.length === 0}>
          {t('common:actions.save')}
        </Button>
      </DialogActions>
    </Dialog>
  );
}

type MembersTab = 'members' | 'invitations';

/** 탭 이름 + 개수 */
function TabLabel({ text, count }: { text: string; count?: number }) {
  return (
    <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
      <span>{text}</span>
      {count !== undefined && <Chip size="small" label={count} sx={{ height: 20, fontSize: 12, fontVariantNumeric: 'tabular-nums' }} />}
    </Stack>
  );
}

/**
 * 멤버 — 목록 · 검색 · 역할 바꾸기 · 내보내기 · 나가기, 그리고 초대(member.invite: 보내기 · 보낸 초대 취소).
 * 단추는 권한이 있을 때만 보인다(표시만 — 판정은 api). 탭은 주소(?tab=invitations)에 남아 새로 고쳐도 그대로다.
 */
export function MembersPage() {
  const { t } = useTranslation(['workspace', 'common']);
  const { workspace } = useWorkspace();
  const can = useCan();
  const labels = useLabels();
  const me = useMe();
  const { refresh } = useSession();
  const navigate = useNavigate();
  const confirm = useConfirm();
  const queryClient = useQueryClient();
  const [search, setSearch] = useState('');
  const [menu, setMenu] = useState<{ anchor: HTMLElement; member: Member } | null>(null);
  const [editing, setEditing] = useState<Member | null>(null);
  const [inviting, setInviting] = useState(false);
  const [params, setParams] = useSearchParams();

  /** 개인 워크스페이스는 혼자 쓴다 — 초대는 팀 워크스페이스에서만 */
  const canInvite = workspace.kind === 'TEAM' && can('member.invite');
  const tab: MembersTab = canInvite && params.get('tab') === 'invitations' ? 'invitations' : 'members';
  const changeTab = (next: MembersTab) =>
    setParams(
      (p) => {
        if (next === 'members') {
          p.delete('tab');
        } else {
          p.set('tab', next);
        }
        return p;
      },
      { replace: true },
    );

  const members = useQuery({ queryKey: workspaceKeys.members(workspace.slug), queryFn: () => workspaceApi.members(workspace.slug) });
  const invitations = useQuery({
    queryKey: workspaceKeys.invitations(workspace.slug),
    queryFn: () => workspaceApi.invitations(workspace.slug),
    enabled: canInvite,
  });
  const roles = useRolesQuery(workspace.slug);
  const customNames = useMemo(() => customRoleNames(roles.data), [roles.data]);

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase();
    const list = members.data ?? [];
    return q ? list.filter((m) => [m.displayName, m.email, m.username].some((v) => v.toLowerCase().includes(q))) : list;
  }, [members.data, search]);

  const iAmOwner = workspace.myRoles.includes('owner');
  /** 나 자신과 (소유자가 아니면) 소유자는 관리 대상이 아니다 — 서버도 같은 규칙으로 막는다 */
  const manageable = (m: Member) => m.userId !== me.id && (iAmOwner || !m.roles.includes('owner'));

  const remove = async (m: Member) => {
    setMenu(null);
    const ok = await confirm({
      title: t('members.removeTitle', { name: m.displayName }),
      description: t('members.removeBody'),
      confirmLabel: t('members.remove'),
      destructive: true,
    });
    if (!ok) {
      return;
    }
    try {
      await workspaceApi.removeMember(workspace.slug, m.userId);
      toast.success(t('members.removed', { name: m.displayName }));
      void queryClient.invalidateQueries({ queryKey: workspaceKeys.members(workspace.slug) });
      void queryClient.invalidateQueries({ queryKey: workspaceKeys.detail(workspace.slug) });
    } catch (e) {
      toast.error(errorMessage(e));
    }
  };

  const leave = async () => {
    const ok = await confirm({
      title: t('members.leaveTitle', { name: workspace.name }),
      description: t('members.leaveBody'),
      confirmLabel: t('members.leave'),
      destructive: true,
    });
    if (!ok) {
      return;
    }
    try {
      await workspaceApi.leave(workspace.slug);
      toast.success(t('members.left'));
      await refresh();
      navigate('/', { replace: true });
    } catch (e) {
      toast.error(errorMessage(e));
    }
  };

  return (
    <>
      <PageHeader
        eyebrow={workspace.name}
        title={t('members.title')}
        description={t('members.description')}
        actions={
          workspace.kind === 'TEAM' && (
            <>
              <Button variant="outlined" color="inherit" startIcon={<LogoutRounded />} onClick={leave}>
                {t('members.leave')}
              </Button>
              {canInvite && (
                <Button variant="contained" startIcon={<PersonAddAlt1Rounded />} onClick={() => setInviting(true)} disabled={!roles.data}>
                  {t('members.invite')}
                </Button>
              )}
            </>
          )
        }
      />
      <Card>
        {canInvite && (
          <Tabs
            value={tab}
            onChange={(_e, next: MembersTab) => changeTab(next)}
            variant="scrollable"
            allowScrollButtonsMobile
            sx={{ px: 1, borderBottom: 1, borderColor: 'divider' }}
          >
            <Tab value="members" label={<TabLabel text={t('members.tabMembers')} count={members.data?.length} />} />
            <Tab value="invitations" label={<TabLabel text={t('members.tabInvitations')} count={invitations.data?.length} />} />
          </Tabs>
        )}
        {tab === 'invitations' ? (
          <SentInvitations query={invitations} onInvite={() => setInviting(true)} />
        ) : (
          <>
            <Box sx={{ p: 2, borderBottom: 1, borderColor: 'divider' }}>
              <TextField
                size="small"
                placeholder={t('members.search')}
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                sx={{ width: '100%', maxWidth: 320 }}
                slotProps={{
                  htmlInput: { 'aria-label': t('members.search') },
                  input: { startAdornment: <InputAdornment position="start"><SearchRounded fontSize="small" /></InputAdornment> },
                }}
              />
            </Box>
            {members.isPending ? (
              <LoadingState />
            ) : members.isError ? (
              <ErrorState error={members.error} onRetry={() => members.refetch()} />
            ) : filtered.length === 0 ? (
              <EmptyState title={t('members.noMatchTitle')} description={t('members.noMatchBody')} />
            ) : (
              <TableContainer>
                <Table>
                  <TableHead>
                    <TableRow>
                      <TableCell>{t('members.colMember')}</TableCell>
                      <TableCell>{t('members.colRoles')}</TableCell>
                      <TableCell sx={{ display: { xs: 'none', md: 'table-cell' } }}>{t('members.colJoinedVia')}</TableCell>
                      <TableCell sx={{ display: { xs: 'none', sm: 'table-cell' } }}>{t('members.colJoinedAt')}</TableCell>
                      <TableCell align="right" width={56} />
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {filtered.map((m) => {
                      const hasActions = manageable(m) && (can('member.role.assign') || can('member.remove'));
                      return (
                        <TableRow key={m.userId} hover>
                          <TableCell sx={{ maxWidth: { xs: 160, sm: 'none' } }}>
                            <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center', minWidth: 0 }}>
                              <Avatar sx={{ width: 32, height: 32, fontSize: 13, fontWeight: 700 }}>{initials(m.displayName)}</Avatar>
                              <Box sx={{ minWidth: 0 }}>
                                <Typography variant="subtitle2" noWrap>
                                  {m.displayName}
                                  {m.userId === me.id && (
                                    <Typography component="span" variant="caption" sx={{ color: 'text.secondary', ml: 0.75 }}>
                                      {t('members.me')}
                                    </Typography>
                                  )}
                                </Typography>
                                <Typography variant="caption" noWrap sx={{ color: 'text.secondary', display: 'block' }}>
                                  {m.email}
                                </Typography>
                              </Box>
                            </Stack>
                          </TableCell>
                          <TableCell>
                            <RoleChips roles={m.roles} customNames={customNames} />
                          </TableCell>
                          <TableCell sx={{ display: { xs: 'none', md: 'table-cell' }, color: 'text.secondary' }}>
                            {labels.joinedVia(m.joinedVia)}
                          </TableCell>
                          <TableCell sx={{ display: { xs: 'none', sm: 'table-cell' }, color: 'text.secondary', fontVariantNumeric: 'tabular-nums' }}>
                            {date(m.joinedAt)}
                          </TableCell>
                          <TableCell align="right">
                            {hasActions && (
                              <IconButton
                                aria-label={t('members.manage', { name: m.displayName })}
                                onClick={(e) => setMenu({ anchor: e.currentTarget, member: m })}
                              >
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
          </>
        )}
      </Card>
      {/* Menu 는 바로 아래 MenuItem 에 초점을 주므로 <Can> 으로 감싸지 않고 조건으로 그린다 */}
      <Menu anchorEl={menu?.anchor} open={menu !== null} onClose={() => setMenu(null)}>
        {can('member.role.assign') && (
          <MenuItem
            onClick={() => {
              setEditing(menu?.member ?? null);
              setMenu(null);
            }}
          >
            {t('members.changeRoles')}
          </MenuItem>
        )}
        {can('member.remove') && (
          <MenuItem onClick={() => menu && remove(menu.member)} sx={{ color: 'error.main' }}>
            {t('members.remove')}
          </MenuItem>
        )}
      </Menu>
      {editing && roles.data && <MemberRolesDialog member={editing} roles={roles.data} onClose={() => setEditing(null)} />}
      {inviting && roles.data && (
        <InviteDialog
          roles={roles.data}
          onClose={() => {
            setInviting(false);
            if (canInvite) {
              changeTab('invitations');
            }
          }}
        />
      )}
      {tab === 'members' && members.data && (
        <Typography variant="caption" sx={{ color: 'text.secondary', display: 'block', mt: 1.5 }}>
          {t('members.summary', {
            total: members.data.length,
            owners: members.data.filter((m) => m.roles.includes('owner')).length,
            members: members.data.filter((m) => m.roles.length === 1 && m.roles[0] === 'member').length,
          })}
        </Typography>
      )}
    </>
  );
}
