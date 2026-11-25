import AddRounded from '@mui/icons-material/AddRounded';
import DeleteOutlineRounded from '@mui/icons-material/DeleteOutlineRounded';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Chip from '@mui/material/Chip';
import IconButton from '@mui/material/IconButton';
import Paper from '@mui/material/Paper';
import Stack from '@mui/material/Stack';
import TextField from '@mui/material/TextField';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import { useQueryClient } from '@tanstack/react-query';
import { useId, useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { toast } from 'sonner';

import { useMe } from '@/features/auth/session';
import { issueApi } from '@/shared/api/endpoints';
import { errorMessage, isApiError } from '@/shared/api/http';
import type { IssueDetail, IssueWorkLog } from '@/shared/api/types';
import { date, dateTimeMs } from '@/shared/lib/format';
import { useConfirm } from '@/shared/ui/ConfirmDialog';

import { personName } from './issue-labels';
import { AgentSessionChip } from './IssueTags';
import { refreshIssues, storeIssue } from './queries';
import {
  MAX_WORK_HOURS,
  MAX_WORK_NOTE,
  buildWorkLog,
  canRemoveWorkLog,
  formatHours,
  todayIso,
  workLogErrorField,
  workLogProblems,
  type WorkLogFormValues,
} from './work-hours';

interface Props {
  slug: string;
  issue: IssueDetail;
  /** 작업 시간을 적을 수 있다 — 프로젝트가 열려 있고 issue.edit 이 있다(담당자가 아니어도) */
  canLog: boolean;
  /** 남의 기록도 지울 수 있다 — project.manage */
  canManage: boolean;
}

/**
 * 작업 시간 — 한 일감에 여러 건(일하면서 그때그때 적는다). 누가 · 어느 날 · 몇 시간 · 무엇을 · 어느 길로(MCP) · 언제 적었는지.
 * 적기는 일감을 고칠 수 있는 사람 누구나(자기 시간만 — 일한 사람 = 적은 사람). 지우기는 자기 기록, 또는 project.manage 가 있으면 남의 기록도.
 * 단추는 서버가 준 이 프로젝트의 내 권한으로 가리기만 한다(판정은 api).
 */
export function IssueWorkLogs({ slug, issue, canLog, canManage }: Props) {
  const { t } = useTranslation('issue');
  const me = useMe();
  const queryClient = useQueryClient();
  const confirm = useConfirm();
  const titleId = useId();
  const [removing, setRemoving] = useState<string | null>(null);
  /** 적기 양식은 단추를 눌렀을 때만 펼친다 — 가운데 본문을 늘 길게 차지하지 않게 */
  const [formOpen, setFormOpen] = useState(false);

  const remove = async (log: IssueWorkLog) => {
    const name = personName(log.user) ?? t('view.unknownPerson');
    const ok = await confirm({
      title: t('workLog.removeTitle'),
      description: t('workLog.removeBody', { name, date: date(log.workDate), hours: formatHours(log.hours) }),
      confirmLabel: t('workLog.removeConfirm'),
      destructive: true,
    });
    if (!ok) {
      return;
    }
    setRemoving(log.id);
    try {
      const saved = await issueApi.removeWork(slug, issue.number, log.id);
      storeIssue(queryClient, slug, saved);
      toast.success(t('workLog.removed'));
    } catch (e) {
      toast.error(errorMessage(e));
      if (isApiError(e, 'WORK_LOG_NOT_FOUND')) {
        // 그 사이 누군가 지웠다 — 지금 기록을 다시 읽는다
        void refreshIssues(queryClient, slug);
      }
    } finally {
      setRemoving(null);
    }
  };

  const total = formatHours(issue.spentHours);
  return (
    <Box component="section" aria-labelledby={titleId} sx={{ mt: 3.5 }}>
      <Stack direction="row" sx={{ alignItems: 'center', justifyContent: 'space-between', flexWrap: 'wrap', columnGap: 2, rowGap: 0.5, mb: 1 }}>
        <Stack direction="row" useFlexGap sx={{ alignItems: 'baseline', flexWrap: 'wrap', columnGap: 1.5, minWidth: 0 }}>
          <Typography variant="h6" component="h2" id={titleId}>
            {t('workLog.title')}
          </Typography>
          <Typography variant="body2" sx={{ color: 'text.secondary', fontVariantNumeric: 'tabular-nums' }}>
            {issue.estimatedHours !== null
              ? t('workLog.totalOfEstimate', { spent: total, estimate: formatHours(issue.estimatedHours) })
              : t('workLog.total', { hours: total })}
          </Typography>
        </Stack>
        {canLog && !formOpen && (
          <Button size="small" startIcon={<AddRounded />} onClick={() => setFormOpen(true)}>
            {t('workLog.formTitle')}
          </Button>
        )}
      </Stack>

      {issue.workLogs.length === 0 ? (
        <Typography variant="body2" sx={{ color: 'text.secondary' }}>
          {t('workLog.empty')}
        </Typography>
      ) : (
        <Paper variant="outlined" sx={{ borderRadius: 2.5, overflow: 'hidden' }}>
          <Box component="ul" aria-label={t('workLog.listLabel')} sx={{ listStyle: 'none', m: 0, p: 0 }}>
            {issue.workLogs.map((log, index) => (
              <WorkLogRow
                key={log.id}
                log={log}
                divider={index > 0}
                mine={log.user?.id === me.id}
                removable={canLog && canRemoveWorkLog(log, me.id, canManage)}
                busy={removing === log.id}
                onRemove={() => void remove(log)}
              />
            ))}
          </Box>
        </Paper>
      )}

      {canLog && formOpen && <WorkLogForm slug={slug} issue={issue} onClose={() => setFormOpen(false)} />}
    </Box>
  );
}

function WorkLogRow({ log, divider, mine, removable, busy, onRemove }: {
  log: IssueWorkLog;
  divider: boolean;
  mine: boolean;
  removable: boolean;
  busy: boolean;
  onRemove: () => void;
}) {
  const { t } = useTranslation('issue');
  const name = personName(log.user) ?? t('view.unknownPerson');
  const hours = formatHours(log.hours);
  return (
    <Box
      component="li"
      sx={{
        display: 'grid',
        gridTemplateColumns: 'minmax(0, 1fr) auto',
        columnGap: 1.5,
        alignItems: 'start',
        px: 2,
        py: 1.25,
        borderTop: divider ? 1 : 0,
        borderColor: 'divider',
      }}
    >
      <Box sx={{ minWidth: 0 }}>
        <Stack direction="row" spacing={1} useFlexGap sx={{ alignItems: 'center', flexWrap: 'wrap' }}>
          <Typography variant="body2" sx={{ fontWeight: 600, fontVariantNumeric: 'tabular-nums' }}>
            {date(log.workDate)}
          </Typography>
          <Typography variant="body2" sx={{ overflowWrap: 'anywhere' }}>
            {mine ? `${name} (${t('workLog.me')})` : name}
          </Typography>
          {log.via === 'MCP' && (
            <Tooltip title={t('journal.viaMcpHelp')}>
              <Chip size="small" variant="outlined" label={t('journal.viaMcp')} sx={{ height: 20, fontSize: 11 }} />
            </Tooltip>
          )}
          {/* MCP 로 적은 기록의 에이전트 세션 — 사람이 웹에서 적은 기록은 없다(일감 #304) */}
          {log.agentSession && <AgentSessionChip session={log.agentSession} compact />}
        </Stack>
        {log.note && (
          <Typography variant="body2" sx={{ color: 'text.secondary', mt: 0.25, overflowWrap: 'anywhere', whiteSpace: 'pre-wrap' }}>
            {log.note}
          </Typography>
        )}
        <Typography
          variant="caption"
          component="time"
          dateTime={log.createdAt}
          sx={{ color: 'text.secondary', display: 'block', mt: 0.25, fontVariantNumeric: 'tabular-nums' }}
        >
          {t('workLog.enteredAt', { time: dateTimeMs(log.createdAt) })}
        </Typography>
      </Box>
      <Stack direction="row" spacing={0.5} sx={{ alignItems: 'center' }}>
        <Typography variant="subtitle2" sx={{ whiteSpace: 'nowrap', fontVariantNumeric: 'tabular-nums' }}>
          {t('hours.value', { hours })}
        </Typography>
        {removable && (
          <IconButton
            size="small"
            onClick={onRemove}
            loading={busy}
            aria-label={t('workLog.remove', { date: date(log.workDate), name, hours })}
          >
            <DeleteOutlineRounded fontSize="small" />
          </IconButton>
        )}
      </Stack>
    </Box>
  );
}

type WorkLogErrors = Partial<Record<keyof WorkLogFormValues, string>>;

/** 작업 시간 적기 — 일한 날(기본 오늘, 앞으로의 날은 못 고름) · 시간 · 한 일. 적은 뒤에는 시간 · 메모만 비우고 날은 그대로 둔다 */
function WorkLogForm({ slug, issue, onClose }: { slug: string; issue: IssueDetail; onClose: () => void }) {
  const { t } = useTranslation(['issue', 'common']);
  const queryClient = useQueryClient();
  const titleId = useId();
  const today = todayIso();
  const [form, setForm] = useState<WorkLogFormValues>(() => ({ workDate: todayIso(), hours: '', note: '' }));
  const [errors, setErrors] = useState<WorkLogErrors>({});
  const [busy, setBusy] = useState(false);

  const change = (patch: Partial<WorkLogFormValues>) => {
    setForm((before) => ({ ...before, ...patch }));
    setErrors((before) => {
      const next = { ...before };
      for (const key of Object.keys(patch) as (keyof WorkLogFormValues)[]) {
        delete next[key];
      }
      return next;
    });
  };

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    const problems = workLogProblems(form, todayIso());
    const found: WorkLogErrors = {};
    for (const [field, problem] of Object.entries(problems) as [keyof WorkLogFormValues, NonNullable<(typeof problems)[keyof WorkLogFormValues]>][]) {
      found[field] = t(`workLog.${problem}`);
    }
    if (Object.keys(found).length > 0) {
      setErrors(found);
      return;
    }
    setBusy(true);
    try {
      const saved = await issueApi.logWork(slug, issue.number, buildWorkLog(form));
      storeIssue(queryClient, slug, saved);
      setForm((before) => ({ ...before, hours: '', note: '' }));
      setErrors({});
      toast.success(t('workLog.added'));
    } catch (e) {
      const field = isApiError(e) ? workLogErrorField(e.code) : undefined;
      if (field && isApiError(e)) {
        setErrors({ [field]: e.message });
      } else {
        toast.error(errorMessage(e));
      }
    } finally {
      setBusy(false);
    }
  };

  return (
    <Paper variant="outlined" sx={{ borderRadius: 2.5, p: 2, mt: 1.5, containerType: 'inline-size' }}>
      <Box component="form" noValidate aria-labelledby={titleId} onSubmit={(event) => void submit(event)}>
        <Typography variant="subtitle2" component="h3" id={titleId}>
          {t('workLog.formTitle')}
        </Typography>
        <Typography variant="caption" sx={{ color: 'text.secondary', display: 'block', mt: 0.25, mb: 1.5 }}>
          {t('workLog.help')}
        </Typography>
        <Box
          sx={{
            display: 'grid',
            gap: 1.5,
            alignItems: 'start',
            gridTemplateColumns: 'repeat(2, minmax(0, 1fr))',
            '& > .wide': { gridColumn: '1 / -1' },
            // 칸이 넉넉하면(이 상자 너비 기준 — 곁 메뉴 · 속성 칸이 있어도 맞게) 한 줄로
            '@container (min-width: 600px)': {
              gridTemplateColumns: '170px 120px minmax(0, 1fr) auto',
              '& > .wide': { gridColumn: 'auto' },
            },
          }}
        >
          <TextField
            type="date"
            size="small"
            required
            label={t('workLog.dateLabel')}
            value={form.workDate}
            onChange={(event) => change({ workDate: event.target.value })}
            error={!!errors.workDate}
            helperText={errors.workDate}
            slotProps={{ inputLabel: { shrink: true }, htmlInput: { max: today } }}
          />
          <TextField
            type="number"
            size="small"
            required
            label={t('workLog.hoursLabel')}
            autoFocus
            value={form.hours}
            onChange={(event) => change({ hours: event.target.value })}
            error={!!errors.hours}
            helperText={errors.hours}
            slotProps={{ inputLabel: { shrink: true }, htmlInput: { min: 0.25, max: MAX_WORK_HOURS, step: 0.25, inputMode: 'decimal' } }}
          />
          <TextField
            className="wide"
            size="small"
            label={t('workLog.noteLabel')}
            placeholder={t('workLog.notePlaceholder')}
            value={form.note}
            onChange={(event) => change({ note: event.target.value })}
            error={!!errors.note}
            helperText={errors.note}
            slotProps={{ inputLabel: { shrink: true }, htmlInput: { maxLength: MAX_WORK_NOTE } }}
          />
          <Stack className="wide" direction="row" spacing={1} sx={{ alignItems: 'stretch' }}>
            <Button type="submit" variant="contained" loading={busy} sx={{ minHeight: 40, flex: 1 }}>
              {t('workLog.submit')}
            </Button>
            <Button color="inherit" onClick={onClose} disabled={busy} sx={{ minHeight: 40 }}>
              {t('common:actions.close')}
            </Button>
          </Stack>
        </Box>
      </Box>
    </Paper>
  );
}
