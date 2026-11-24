import { zodResolver } from '@hookform/resolvers/zod';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import useMediaQuery from '@mui/material/useMediaQuery';
import { useMemo, useState } from 'react';
import { useForm } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';
import { z } from 'zod';

import { useCan } from '@/features/auth/access';
import { useMe } from '@/features/auth/session';
import { MAX_AGENT_SESSION } from '@/features/issue/issue-form';
import { PersonPicker, type PersonOption } from '@/features/issue/PersonPicker';
import { usePeopleQuery } from '@/features/issue/queries';
import { projectApi } from '@/shared/api/endpoints';
import { errorMessage, isApiError } from '@/shared/api/http';
import type { Member, ProjectAgent, ProjectAgentPatch, ProjectRoleHolder } from '@/shared/api/types';

/** 에이전트 설명 상한 — api 의 ProjectAgent 설명(200자)과 같다 */
export const MAX_AGENT_DESCRIPTION = 200;

/** 세션 이름에 넣을 수 없는 제어 문자 — api 의 agentSession 규칙과 같다 */
// eslint-disable-next-line no-control-regex -- 제어 문자를 막는 검사
const CONTROL = /[\u0000-\u001f\u007f-\u009f]/;

interface Props {
  slug: string;
  projectKey: string;
  /** 있으면 고치기(설명 · 주인), 없으면 더하기 */
  agent?: ProjectAgent;
  /** 워크스페이스 멤버 목록을 볼 수 없을 때 주인 후보 — 이 프로젝트의 역할을 받은 사람 */
  holders: ProjectRoleHolder[] | undefined;
  onClose: () => void;
  onSaved: (agent: ProjectAgent) => void;
}

/** 프로젝트 역할을 받은 사람 → 사람 고르기 칸의 후보 모양(활성 멤버만 역할을 받는다) */
function holderAsMember(h: ProjectRoleHolder): Member {
  return {
    userId: h.userId,
    username: h.username,
    displayName: h.displayName ?? h.username,
    email: '',
    status: 'ACTIVE',
    joinedVia: 'ADMIN',
    joinedAt: '',
    roles: [],
  };
}

/**
 * 에이전트 멤버 더하기 · 고치기(일감 #285) — 세션 이름(더할 때만, 바꿀 수 없다) · 주인(그 에이전트가 쓰는 MCP 키의 사람, 처음 값은 나) · 설명.
 * 규칙은 미리 알려 주고 판정은 api 가 한다. 고칠 때는 바뀐 것만 읽은 rowVersion 과 함께 보낸다 — 그 사이 다른 사람이 고쳤으면 409 를 알린다.
 */
export function AgentDialog({ slug, projectKey, agent, holders, onClose, onSaved }: Props) {
  const { t } = useTranslation(['workspace', 'common']);
  const fullScreen = useMediaQuery((theme) => theme.breakpoints.down('sm'));
  const can = useCan();
  const me = useMe();
  const editing = agent !== undefined;
  const people = usePeopleQuery(slug, can('member.read'));
  const fromRoles = !can('member.read') || people.isError;
  const candidates = fromRoles ? holders?.map(holderAsMember) : people.data;

  const initialOwner: PersonOption | null = editing
    ? agent.owner
      ? { id: agent.owner.id, name: agent.owner.displayName ?? agent.owner.username ?? '', username: agent.owner.username }
      : null
    : { id: me.id, name: me.displayName || me.username, username: me.username };
  const [owner, setOwner] = useState<PersonOption | null>(initialOwner);
  const [ownerError, setOwnerError] = useState<string | undefined>();

  const schema = useMemo(
    () =>
      z.object({
        name: z.string().superRefine((value, ctx) => {
          if (editing) {
            return;
          }
          const v = value.trim();
          if (!v) {
            ctx.addIssue({ code: 'custom', message: t('projects.agents.nameRequired') });
          } else if (CONTROL.test(v)) {
            ctx.addIssue({ code: 'custom', message: t('projects.agents.nameInvalid') });
          }
        }),
        description: z.string().max(MAX_AGENT_DESCRIPTION, t('projects.agents.descriptionTooLong')),
      }),
    [t, editing],
  );
  type Form = z.infer<typeof schema>;

  const { register, handleSubmit, formState, setError } = useForm<Form>({
    resolver: zodResolver(schema),
    defaultValues: { name: agent?.name ?? '', description: agent?.description ?? '' },
  });

  const ownerChanged = (owner?.id ?? null) !== (initialOwner?.id ?? null);

  const onSubmit = handleSubmit(async (form) => {
    // 주인은 비울 수 없다 — 옛 일감에서 채운 주인 모름(null)은 고치지 않으면 그대로 둔다
    if ((!editing || ownerChanged) && !owner?.username) {
      setOwnerError(t('projects.agents.ownerRequired'));
      return;
    }
    try {
      let saved: ProjectAgent;
      if (editing) {
        const patch: ProjectAgentPatch = { rowVersion: agent.rowVersion };
        const description = form.description.trim() || null;
        if (description !== (agent.description ?? null)) {
          patch.description = description;
        }
        if (ownerChanged && owner?.username) {
          patch.owner = owner.username;
        }
        saved = await projectApi.updateAgent(slug, projectKey, agent.id, patch);
      } else {
        saved = await projectApi.createAgent(slug, projectKey, {
          name: form.name.trim(),
          owner: owner?.username ?? undefined,
          description: form.description.trim() || undefined,
        });
      }
      toast.success(editing ? t('projects.agents.saved') : t('projects.agents.created', { name: saved.name }));
      onSaved(saved);
      onClose();
    } catch (e) {
      if (isApiError(e, 'AGENT_NAME_TAKEN')) {
        setError('name', { message: t('projects.agents.nameTaken') }, { shouldFocus: true });
      } else if (isApiError(e, 'INVALID_AGENT_SESSION')) {
        setError('name', { message: e.message }, { shouldFocus: true });
      } else if (isApiError(e, 'AGENT_DESCRIPTION_TOO_LONG')) {
        setError('description', { message: e.message }, { shouldFocus: true });
      } else if (isApiError(e, 'PERSON_NOT_FOUND') || isApiError(e, 'USER_NOT_PROJECT_READER')) {
        setOwnerError(e.message);
      } else if (isApiError(e, 'CONCURRENT_UPDATE')) {
        toast.error(t('projects.agents.conflict'));
      } else {
        toast.error(errorMessage(e));
      }
    }
  });

  return (
    <Dialog open onClose={onClose} maxWidth="sm" fullWidth fullScreen={fullScreen}>
      <Box component="form" noValidate onSubmit={onSubmit} sx={{ display: 'contents' }}>
        <DialogTitle sx={{ fontWeight: 700 }}>{editing ? t('projects.agents.editTitle') : t('projects.agents.createTitle')}</DialogTitle>
        <DialogContent>
          <Stack spacing={2.5} sx={{ pt: 1 }}>
            <TextField
              label={t('projects.agents.nameLabel')}
              placeholder={t('projects.agents.namePlaceholder')}
              autoFocus={!editing}
              {...register('name')}
              disabled={editing}
              error={!!formState.errors.name}
              helperText={formState.errors.name?.message ?? (editing ? t('projects.agents.nameFixed') : t('projects.agents.nameHelp'))}
              slotProps={{
                htmlInput: { spellCheck: false, autoCapitalize: 'none', autoCorrect: 'off', autoComplete: 'off', maxLength: MAX_AGENT_SESSION, sx: { fontFamily: 'monospace' } },
                inputLabel: { shrink: true },
              }}
            />
            <PersonPicker
              label={t('projects.agents.ownerLabel')}
              value={owner}
              onChange={(next) => {
                setOwner(next);
                setOwnerError(undefined);
              }}
              members={candidates}
              loading={!fromRoles && people.isPending}
              error={ownerError}
              helperText={fromRoles ? t('projects.agents.ownerFromRoles') : t('projects.agents.ownerHelp')}
            />
            <TextField
              label={t('projects.agents.descriptionLabel')}
              multiline
              minRows={2}
              {...register('description')}
              error={!!formState.errors.description}
              helperText={formState.errors.description?.message ?? t('projects.agents.descriptionHelp')}
              slotProps={{ htmlInput: { maxLength: MAX_AGENT_DESCRIPTION }, inputLabel: { shrink: true } }}
            />
          </Stack>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2.5 }}>
          <Button color="inherit" onClick={onClose}>
            {t('common:actions.cancel')}
          </Button>
          <Button
            type="submit"
            variant="contained"
            loading={formState.isSubmitting}
            disabled={editing && !formState.isDirty && !ownerChanged}
          >
            {editing ? t('common:actions.save') : t('projects.agents.submitCreate')}
          </Button>
        </DialogActions>
      </Box>
    </Dialog>
  );
}
