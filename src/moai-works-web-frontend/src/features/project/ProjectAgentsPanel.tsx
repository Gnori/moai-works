import EditRounded from '@mui/icons-material/EditRounded';
import SmartToyRounded from '@mui/icons-material/SmartToyRounded';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Chip from '@mui/material/Chip';
import IconButton from '@mui/material/IconButton';
import Link from '@mui/material/Link';
import List from '@mui/material/List';
import ListItem from '@mui/material/ListItem';
import Stack from '@mui/material/Stack';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import { useQueryClient, type UseQueryResult } from '@tanstack/react-query';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link as RouterLink } from 'react-router';
import { toast } from 'sonner';

import { projectPath } from '@/app/routes';
import { issueKeys } from '@/features/issue/queries';
import { workspaceKeys } from '@/features/workspace/queries';
import { projectApi } from '@/shared/api/endpoints';
import { errorMessage, isApiError } from '@/shared/api/http';
import { openIssueStatuses, type ProjectAgent } from '@/shared/api/types';
import { fromNow } from '@/shared/lib/format';
import { EmptyState, ErrorState, LoadingState } from '@/shared/ui/States';

import { projectKeys } from './queries';

/** 그 에이전트의 열린 일감 목록 주소 조건 — 세션 이름 + 열린 상태 모두(일감 목록의 주소 조건과 같은 모양) */
export function agentOpenIssuesSearch(name: string): string {
  const params = new URLSearchParams();
  openIssueStatuses.forEach((s) => params.append('status', s));
  params.set('agentSession', name);
  return params.toString();
}

/** 에이전트 멤버가 바뀐 뒤 — 목록 · 일감 양식의 세션 고르기 · 감사 화면이 다시 읽게 한다 */
export function useRefreshAgents(slug: string, projectKey: string) {
  const queryClient = useQueryClient();
  return () => {
    void queryClient.invalidateQueries({ queryKey: projectKeys.agents(slug, projectKey) });
    // 일감 양식 · 목록 거르기의 세션 고르기(이 프로젝트 · 워크스페이스 전체 모두 — 앞부분이 같은 키)
    void queryClient.invalidateQueries({ queryKey: issueKeys.agentSessions(slug, projectKey).slice(0, 3) });
    void queryClient.invalidateQueries({ queryKey: workspaceKeys.audit(slug) });
  };
}

interface Props {
  slug: string;
  projectKey: string;
  query: UseQueryResult<ProjectAgent[]>;
  /** project.manage 가 있고 프로젝트가 열려 있다 — 수정 · 쓰지 않음 · 다시 쓰기 단추를 보인다(표시만, 판정은 api) */
  canManage: boolean;
  onEdit: (agent: ProjectAgent) => void;
}

/**
 * 프로젝트 멤버 › 에이전트(일감 #285) — AI Agent 세션의 이름표. 계정이 아니고 주인(그 MCP 키의 사람)의 권한으로 일한다.
 * 줄마다 이름(고정폭 칩) · 주인 · 설명 · 열린 일감(그 세션 + 열린 상태로 거른 일감 목록 링크) · 모두 · 마지막 활동 · 쓰지 않음 · 자동 등록.
 * 관리하는 사람에게만 수정 · 쓰지 않음 · 다시 쓰기. 지우기는 없다. 좁은 화면에서는 단추가 아래 줄로 내려간다.
 */
export function ProjectAgentsPanel({ slug, projectKey, query, canManage, onEdit }: Props) {
  const { t } = useTranslation(['workspace', 'common']);
  const refresh = useRefreshAgents(slug, projectKey);
  const [busy, setBusy] = useState<string | null>(null);

  const toggle = async (agent: ProjectAgent) => {
    setBusy(agent.id);
    try {
      await projectApi.updateAgent(slug, projectKey, agent.id, { active: !agent.active, rowVersion: agent.rowVersion });
      toast.success(
        agent.active ? t('projects.agents.deactivated', { name: agent.name }) : t('projects.agents.activated', { name: agent.name }),
      );
    } catch (e) {
      toast.error(isApiError(e, 'CONCURRENT_UPDATE') ? t('projects.agents.conflict') : errorMessage(e));
    } finally {
      refresh();
      setBusy(null);
    }
  };

  if (query.isPending) {
    return <LoadingState />;
  }
  if (query.isError) {
    return <ErrorState error={query.error} onRetry={() => query.refetch()} />;
  }
  const agents = query.data;
  if (agents.length === 0) {
    return (
      <EmptyState
        icon={<SmartToyRounded />}
        title={t('projects.agents.emptyTitle')}
        description={canManage ? t('projects.agents.emptyBodyManager') : t('projects.agents.emptyBody')}
      />
    );
  }

  return (
    <List disablePadding aria-label={t('projects.agents.listLabel')}>
      {agents.map((a, index) => {
        const owner = a.owner ?? null;
        const ownerName = owner ? (owner.displayName ?? owner.username ?? '') : null;
        return (
          <ListItem
            key={a.id}
            divider={index < agents.length - 1}
            sx={{ alignItems: 'flex-start', gap: 1.5, py: 1.5, px: { xs: 1.5, sm: 2 }, flexWrap: { xs: 'wrap', sm: 'nowrap' } }}
          >
            <Box sx={{ minWidth: 0, flex: 1 }}>
              <Stack direction="row" spacing={1} useFlexGap sx={{ alignItems: 'center', flexWrap: 'wrap' }}>
                <Chip
                  size="small"
                  variant="outlined"
                  icon={<SmartToyRounded />}
                  label={a.name}
                  title={a.name}
                  sx={{ fontFamily: 'monospace', maxWidth: '100%', height: 24, '& .MuiChip-icon': { fontSize: 16, ml: 0.75 } }}
                />
                {!a.active && <Chip size="small" label={t('projects.agents.inactive')} sx={{ height: 22, bgcolor: 'action.selected' }} />}
                {a.source === 'AUTO' && (
                  <Tooltip title={t('projects.agents.autoHint')} enterTouchDelay={0}>
                    <Typography variant="caption" tabIndex={0} sx={{ color: 'text.secondary', cursor: 'help', borderBottom: 1, borderColor: 'divider', borderBottomStyle: 'dotted' }}>
                      {t('projects.agents.auto')}
                    </Typography>
                  </Tooltip>
                )}
              </Stack>
              <Typography variant="body2" sx={{ mt: 0.75, overflowWrap: 'anywhere' }}>
                <Box component="span" sx={{ color: 'text.secondary', mr: 0.75 }}>
                  {t('projects.agents.ownerLabel')}
                </Box>
                {owner ? (
                  <>
                    <Box component="span" sx={{ fontWeight: 600 }}>
                      {ownerName}
                    </Box>
                    {owner.username && owner.username !== ownerName && (
                      <Box component="span" sx={{ color: 'text.secondary', fontFamily: 'monospace', fontSize: '0.75rem', ml: 0.75 }}>
                        {owner.username}
                      </Box>
                    )}
                  </>
                ) : (
                  <Box component="span" sx={{ color: 'text.secondary', fontStyle: 'italic' }}>
                    {t('projects.agents.ownerUnknown')}
                  </Box>
                )}
              </Typography>
              {a.description && (
                <Typography variant="body2" sx={{ color: 'text.secondary', mt: 0.5, overflowWrap: 'anywhere' }}>
                  {a.description}
                </Typography>
              )}
              <Stack
                direction="row"
                spacing={1}
                useFlexGap
                divider={<Box component="span" aria-hidden sx={{ color: 'text.disabled' }}>·</Box>}
                sx={{ mt: 0.5, flexWrap: 'wrap', alignItems: 'baseline' }}
              >
                <Link
                  component={RouterLink}
                  to={projectPath(slug, projectKey, agentOpenIssuesSearch(a.name))}
                  variant="caption"
                  underline="hover"
                  sx={{ fontWeight: 600 }}
                >
                  {t('projects.agents.openIssues', { count: a.openIssues })}
                </Link>
                <Typography variant="caption" sx={{ color: 'text.secondary' }}>
                  {t('projects.agents.totalIssues', { count: a.totalIssues })}
                </Typography>
                <Typography variant="caption" sx={{ color: 'text.secondary' }}>
                  {a.lastActivityAt ? t('projects.agents.lastActivity', { time: fromNow(a.lastActivityAt) }) : t('projects.agents.noActivity')}
                </Typography>
              </Stack>
            </Box>
            {canManage && (
              <Stack direction="row" spacing={0.5} sx={{ flexShrink: 0, alignItems: 'center', ml: { xs: 'auto', sm: 0 } }}>
                <Tooltip title={t('projects.agents.edit')}>
                  <IconButton size="small" aria-label={t('projects.agents.editNamed', { name: a.name })} onClick={() => onEdit(a)} disabled={busy !== null}>
                    <EditRounded fontSize="small" />
                  </IconButton>
                </Tooltip>
                <Button
                  size="small"
                  color="inherit"
                  onClick={() => void toggle(a)}
                  disabled={busy !== null}
                  sx={{ color: 'text.secondary', whiteSpace: 'nowrap' }}
                >
                  {a.active ? t('projects.agents.deactivate') : t('projects.agents.activate')}
                </Button>
              </Stack>
            )}
          </ListItem>
        );
      })}
    </List>
  );
}
