import { zodResolver } from '@hookform/resolvers/zod';
import ContentCopyRounded from '@mui/icons-material/ContentCopyRounded';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import Divider from '@mui/material/Divider';
import IconButton from '@mui/material/IconButton';
import InputAdornment from '@mui/material/InputAdornment';
import MenuItem from '@mui/material/MenuItem';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import ToggleButton from '@mui/material/ToggleButton';
import ToggleButtonGroup from '@mui/material/ToggleButtonGroup';
import Typography from '@mui/material/Typography';
import useMediaQuery from '@mui/material/useMediaQuery';
import { useTheme } from '@mui/material/styles';
import { useQueryClient } from '@tanstack/react-query';
import dayjs from 'dayjs';
import { useMemo, useRef, useState } from 'react';
import { Controller, useForm, useWatch } from 'react-hook-form';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';
import { z } from 'zod';

import { useMe } from '@/features/auth/session';
import { useWorkspaceQuery } from '@/features/workspace/workspace-context';
import { mcpKeyApi } from '@/shared/api/endpoints';
import { errorMessage, isApiError } from '@/shared/api/http';
import type { McpKeyIssued, WorkspaceSummary } from '@/shared/api/types';
import { date, dateTimeMs } from '@/shared/lib/format';

import {
  claudeAddCommand,
  DEFAULT_KEY_DAYS,
  defaultIssueWorkspace,
  issuableWorkspaces,
  KEY_NAME_MAX,
  keyPeriodPresets,
  mcpUrl,
} from './mcp-key';
import { refreshMcpKeys } from './queries';

/** 글을 복사한다 — 못 하면(권한 · 안전하지 않은 출처) 글을 골라 두고 직접 복사하라고 알린다 */
function useCopy() {
  const { t } = useTranslation('mcp');
  return async (text: string, select?: () => void) => {
    try {
      await navigator.clipboard.writeText(text);
      toast.success(t('issue.copied'));
    } catch {
      select?.();
      toast.error(t('issue.copyFailed'));
    }
  };
}

/** 읽기만 하는 한 줄 칸 + 복사 단추 */
function CopyField({ label, value, copyLabel }: { label: string; value: string; copyLabel: string }) {
  const copy = useCopy();
  const input = useRef<HTMLInputElement>(null);
  return (
    <TextField
      label={label}
      value={value}
      inputRef={input}
      autoComplete="off"
      onFocus={(e) => e.target.select()}
      slotProps={{
        htmlInput: { readOnly: true, spellCheck: false, sx: { fontFamily: 'monospace', fontSize: 13 } },
        input: {
          endAdornment: (
            <InputAdornment position="end">
              <IconButton edge="end" aria-label={copyLabel} onClick={() => copy(value, () => input.current?.select())}>
                <ContentCopyRounded fontSize="small" />
              </IconButton>
            </InputAdornment>
          ),
        },
      }}
    />
  );
}

/** 명령 상자 — 긴 명령은 상자 안에서 줄을 바꾼다(가로로 밀지 않는다) */
function CommandBlock({ value, copyLabel, labelledBy }: { value: string; copyLabel: string; labelledBy: string }) {
  const copy = useCopy();
  const block = useRef<HTMLElement>(null);
  const select = () => {
    if (block.current) {
      window.getSelection()?.selectAllChildren(block.current);
    }
  };
  return (
    <Box sx={{ position: 'relative' }}>
      <Box
        component="pre"
        ref={block}
        aria-labelledby={labelledBy}
        sx={{
          m: 0,
          p: 1.5,
          pr: 6,
          fontFamily: 'monospace',
          fontSize: 13,
          lineHeight: 1.6,
          whiteSpace: 'pre-wrap',
          overflowWrap: 'anywhere',
          wordBreak: 'break-all',
          bgcolor: 'background.subtle',
          border: 1,
          borderColor: 'divider',
          borderRadius: 1.5,
        }}
      >
        <code>{value}</code>
      </Box>
      <IconButton size="small" aria-label={copyLabel} onClick={() => copy(value, select)} sx={{ position: 'absolute', top: 6, right: 6 }}>
        <ContentCopyRounded fontSize="small" />
      </IconButton>
    </Box>
  );
}

/**
 * 받은 키 — 원문은 지금 한 번만 보인다(서버는 해시만 갖는다). 복사 단추와 연결 안내(MCP 주소 · Claude Code 명령)를 함께 둔다.
 * 원문은 이 화면의 상태에만 있고, 쿼리 캐시 · 브라우저 저장소에 넣지 않는다.
 */
function IssuedStep({ issued, onClose }: { issued: McpKeyIssued; onClose: () => void }) {
  const { t } = useTranslation('mcp');
  const copy = useCopy();
  const url = mcpUrl(window.location.origin);
  const command = claudeAddCommand(url, issued.key);
  return (
    <>
      <DialogTitle sx={{ fontWeight: 700 }}>{t('issue.createdTitle')}</DialogTitle>
      <DialogContent>
        <Stack spacing={2.5} sx={{ pt: 0.5 }}>
          <Alert severity="warning">{t('issue.once')}</Alert>
          <Typography variant="body2" sx={{ color: 'text.secondary', overflowWrap: 'anywhere' }}>
            {t('issue.summary', { name: issued.view.name, workspace: issued.view.workspaceName, date: dateTimeMs(issued.view.expiresAt) })}
          </Typography>
          <CopyField label={t('issue.key')} value={issued.key} copyLabel={t('issue.copyKey')} />
          <Divider />
          <Typography variant="subtitle1" component="h3" sx={{ fontWeight: 700 }}>
            {t('issue.connectTitle')}
          </Typography>
          <CopyField label={t('issue.url')} value={url} copyLabel={t('issue.copyUrl')} />
          <Stack spacing={1}>
            <Typography variant="subtitle2" id="mcp-claude-command">
              {t('issue.claudeCode')}
            </Typography>
            <CommandBlock value={command} copyLabel={t('issue.copyCommand')} labelledBy="mcp-claude-command" />
            <Typography variant="caption" sx={{ color: 'text.secondary' }}>
              {t('issue.commandNote')}
            </Typography>
          </Stack>
          <Typography variant="caption" sx={{ color: 'text.secondary' }}>
            {t('issue.otherClients')}
          </Typography>
        </Stack>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2.5, flexWrap: 'wrap', gap: 1 }}>
        <Button variant="outlined" startIcon={<ContentCopyRounded />} onClick={() => copy(issued.key)}>
          {t('issue.copyKey')}
        </Button>
        <Button variant="contained" onClick={onClose}>
          {t('issue.done')}
        </Button>
      </DialogActions>
    </>
  );
}

/** 발급 양식 — 워크스페이스 · 이름 · 기간. 고른 워크스페이스의 내 권한(서버가 준 myPermissions)으로만 단추를 켠다 */
function IssueForm({
  workspaces,
  preferred,
  onIssued,
  onCancel,
}: {
  workspaces: WorkspaceSummary[];
  preferred: string | null | undefined;
  onIssued: (issued: McpKeyIssued) => void;
  onCancel: () => void;
}) {
  const { t } = useTranslation(['mcp', 'common']);
  const schema = useMemo(
    () =>
      z.object({
        workspace: z.string().min(1),
        name: z.string().trim().min(1, t('issue.nameRequired')).max(KEY_NAME_MAX, t('issue.nameTooLong')),
        days: z.number().int().min(1).max(365),
      }),
    [t],
  );
  type Form = z.infer<typeof schema>;
  const { register, control, handleSubmit, formState, setError } = useForm<Form>({
    resolver: zodResolver(schema),
    defaultValues: { workspace: defaultIssueWorkspace(workspaces, preferred), name: '', days: DEFAULT_KEY_DAYS },
  });
  const slug = useWatch({ control, name: 'workspace' });
  const days = useWatch({ control, name: 'days' });
  const workspace = useWorkspaceQuery(slug || undefined);
  const allowed = workspace.data?.myPermissions.includes('mcp.key.issue.personal') ?? false;

  const onSubmit = handleSubmit(async (form) => {
    try {
      // 원문이 든 응답 — useMutation 을 쓰지 않는다(뮤테이션 캐시에도 남기지 않는다)
      onIssued(await mcpKeyApi.issue(form.workspace, { name: form.name, days: form.days }));
    } catch (e) {
      if (isApiError(e, 'INVALID_KEY_NAME')) {
        setError('name', { message: e.message });
        return;
      }
      toast.error(errorMessage(e));
    }
  });

  return (
    <Box component="form" noValidate onSubmit={onSubmit} sx={{ display: 'contents' }}>
      <DialogTitle sx={{ fontWeight: 700 }}>{t('issue.title')}</DialogTitle>
      <DialogContent>
        <Stack spacing={2.5} sx={{ pt: 1 }}>
          <Typography variant="body2" sx={{ color: 'text.secondary' }}>
            {t('issue.body')}
          </Typography>
          <Controller
            control={control}
            name="workspace"
            render={({ field }) => (
              <TextField select label={t('issue.workspace')} {...field} helperText={t('issue.workspaceHelp')}>
                {workspaces.map((w) => (
                  <MenuItem key={w.slug} value={w.slug}>
                    <Box sx={{ minWidth: 0 }}>
                      <Typography variant="body2" noWrap sx={{ fontWeight: 600 }}>
                        {w.name}
                      </Typography>
                      <Typography variant="caption" noWrap sx={{ color: 'text.secondary', display: 'block' }}>
                        {w.kind === 'PERSONAL' ? t('common:switcher.personalWorkspace') : t('common:switcher.teamWorkspace')}
                      </Typography>
                    </Box>
                  </MenuItem>
                ))}
              </TextField>
            )}
          />
          {workspace.isError ? (
            <Alert severity="error">{errorMessage(workspace.error)}</Alert>
          ) : workspace.isPending ? (
            <Typography variant="caption" sx={{ color: 'text.secondary' }} role="status">
              {t('issue.checking')}
            </Typography>
          ) : (
            !allowed && <Alert severity="warning">{t('issue.notAllowed')}</Alert>
          )}
          <TextField
            label={t('issue.name')}
            placeholder={t('issue.namePlaceholder')}
            autoComplete="off"
            autoFocus
            {...register('name')}
            error={!!formState.errors.name}
            helperText={formState.errors.name?.message ?? t('issue.nameHelp')}
            slotProps={{ htmlInput: { maxLength: KEY_NAME_MAX } }}
          />
          <Box>
            <Typography variant="subtitle2" id="mcp-key-period" sx={{ mb: 1 }}>
              {t('issue.period')}
            </Typography>
            <Controller
              control={control}
              name="days"
              render={({ field }) => (
                <ToggleButtonGroup
                  exclusive
                  fullWidth
                  size="small"
                  value={field.value}
                  onChange={(_, next: number | null) => next && field.onChange(next)}
                  aria-labelledby="mcp-key-period"
                >
                  {keyPeriodPresets.map((d) => (
                    <ToggleButton key={d} value={d}>
                      {t('issue.days', { count: d })}
                    </ToggleButton>
                  ))}
                </ToggleButtonGroup>
              )}
            />
            <Typography variant="caption" sx={{ color: 'text.secondary', display: 'block', mt: 1 }}>
              {t('issue.periodHelp', { date: date(dayjs().add(days, 'day').toISOString()) })}
            </Typography>
          </Box>
        </Stack>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2.5 }}>
        <Button color="inherit" onClick={onCancel}>
          {t('common:actions.cancel')}
        </Button>
        <Button type="submit" variant="contained" loading={formState.isSubmitting} disabled={!allowed}>
          {t('issue.submit')}
        </Button>
      </DialogActions>
    </Box>
  );
}

/**
 * MCP 키 발급 — 워크스페이스(내가 들어간 곳, 처음 값은 지금 · 마지막에 쓴 곳) · 이름 · 기간(30 · 90 · 180 · 365일, 기본 90일).
 * 받으면 원문을 한 번만 보이고, 창을 닫으면 원문도 함께 사라진다(이 컴포넌트의 상태에만 있다).
 * 바깥을 눌러서는 닫히지 않게 한다 — 원문을 복사하기 전에 잃지 않게.
 */
export function IssueKeyDialog({ preferredWorkspace, onClose }: { preferredWorkspace?: string | null; onClose: () => void }) {
  const me = useMe();
  const theme = useTheme();
  const phone = useMediaQuery(theme.breakpoints.down('sm'));
  const queryClient = useQueryClient();
  const workspaces = useMemo(() => issuableWorkspaces(me.workspaces), [me.workspaces]);
  const [issued, setIssued] = useState<McpKeyIssued | null>(null);

  const close = () => {
    setIssued(null);
    onClose();
  };

  return (
    <Dialog
      open
      onClose={(_, reason) => {
        if (issued && reason === 'backdropClick') {
          return;
        }
        close();
      }}
      maxWidth="sm"
      fullWidth
      fullScreen={phone}
    >
      {issued ? (
        <IssuedStep issued={issued} onClose={close} />
      ) : (
        <IssueForm
          workspaces={workspaces}
          preferred={preferredWorkspace}
          onIssued={(result) => {
            setIssued(result);
            // 목록(원문 없음)과 감사 기록만 다시 읽는다
            refreshMcpKeys(queryClient, result.view.workspace);
          }}
          onCancel={close}
        />
      )}
    </Dialog>
  );
}
