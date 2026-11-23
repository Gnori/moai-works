import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Chip from '@mui/material/Chip';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import Typography from '@mui/material/Typography';
import useMediaQuery from '@mui/material/useMediaQuery';
import { useTheme } from '@mui/material/styles';
import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { Link as RouterLink } from 'react-router';

import type { McpAccessEntry } from '@/shared/api/types';
import { dateTimeMs } from '@/shared/lib/format';

import { accessTarget, outcomeColor, prettyArguments } from './access-log';

/** 이름표 한 칸 (dt · dd). wide 면 한 줄을 다 쓴다 */
function Field({ label, children, wide = false, mono = false }: { label: string; children: ReactNode; wide?: boolean; mono?: boolean }) {
  return (
    <Box sx={{ minWidth: 0, gridColumn: wide ? '1 / -1' : undefined }}>
      <Typography component="dt" variant="caption" sx={{ color: 'text.secondary', display: 'block' }}>
        {label}
      </Typography>
      <Typography
        component="dd"
        variant="body2"
        sx={{ m: 0, overflowWrap: 'anywhere', fontVariantNumeric: 'tabular-nums', fontFamily: mono ? 'monospace' : undefined, fontSize: mono ? 13 : undefined }}
      >
        {children}
      </Typography>
    </Box>
  );
}

interface Props {
  entry: McpAccessEntry;
  onClose: () => void;
  /** 같은 세션의 기록으로 — 세션 id 가 있을 때만 단추가 나온다 */
  onSameSession?: (session: string) => void;
  /** 이 키의 기록으로 — 키 하나를 보는 곳(내 키 대화상자)에서는 주지 않는다 */
  onSameKey?: (key: string) => void;
  /** 그 일감 화면 주소 — 워크스페이스 · 프로젝트 · 번호를 아는 줄만 */
  issueHref?: string | null;
}

/**
 * 접근 기록 한 줄의 모든 칸 — 클라이언트 정보 · 인자 요약 · 결과. 글은 모두 바깥 클라이언트 · 서버가 준 값이라 글자로만 보인다(HTML 로 해석하지 않는다).
 * 휴대폰에서는 화면을 꽉 채운다.
 */
export function AccessEntryDialog({ entry, onClose, onSameSession, onSameKey, issueHref }: Props) {
  const { t } = useTranslation('mcp');
  const theme = useTheme();
  const phone = useMediaQuery(theme.breakpoints.down('sm'));
  const args = prettyArguments(entry.arguments);
  const dash = '—';
  return (
    <Dialog open onClose={onClose} maxWidth="md" fullWidth fullScreen={phone} aria-labelledby="mcp-access-entry-title">
      <DialogTitle id="mcp-access-entry-title" sx={{ fontWeight: 700, display: 'flex', alignItems: 'center', gap: 1, flexWrap: 'wrap' }}>
        {t('access.detail.title')}
        <Chip size="small" color={outcomeColor(entry.outcome)} label={t(`access.outcomes.${entry.outcome}`)} />
        <Chip size="small" variant="outlined" label={t(`access.operations.${entry.operation}`)} />
      </DialogTitle>
      <DialogContent dividers>
        <Box
          component="dl"
          sx={{ m: 0, display: 'grid', columnGap: 2, rowGap: 1.5, gridTemplateColumns: { xs: 'repeat(2, minmax(0, 1fr))', md: 'repeat(3, minmax(0, 1fr))' } }}
        >
          <Field label={t('access.detail.time')}>{dateTimeMs(entry.occurredAt)}</Field>
          <Field label={t('access.detail.duration')}>{t('access.duration', { ms: entry.durationMs })}</Field>
          <Field label={t('access.detail.outcome')}>
            {t('access.detail.outcomeValue', { outcome: t(`access.outcomes.${entry.outcome}`), status: entry.httpStatus })}
          </Field>
          <Field label={t('access.detail.errorCode')} mono>
            {entry.errorCode ?? dash}
          </Field>
          <Field label={t('access.detail.method')} mono>
            {entry.mcpMethod}
          </Field>
          <Field label={t('access.detail.tool')} mono>
            {entry.tool ?? dash}
          </Field>
          <Field label={t('access.detail.workspace')}>
            {entry.workspaceSlug ? `${entry.workspaceName ?? entry.workspaceSlug} (${entry.workspaceSlug})` : t('access.detail.noWorkspace')}
          </Field>
          <Field label={t('access.detail.target')}>{accessTarget(entry) || dash}</Field>
          <Field label={t('access.detail.entry')} mono>
            {`${entry.entry} · ${entry.requestPath}`}
          </Field>
          <Field label={t('access.detail.key')}>
            {entry.keyId ? `${entry.keyName ?? ''} (${entry.keyPrefix ?? ''}…)` : t('access.noKey')}
          </Field>
          <Field label={t('access.detail.owner')}>{entry.ownerName ?? dash}</Field>
          <Field label={t('access.detail.ip')} mono>
            {entry.ip ?? dash}
          </Field>
          <Field label={t('access.detail.client')}>{[entry.clientName, entry.clientVersion].filter(Boolean).join(' ') || dash}</Field>
          <Field label={t('access.detail.clientTitle')}>{entry.clientTitle ?? dash}</Field>
          <Field label={t('access.detail.protocol')} mono>
            {entry.protocolVersion ?? dash}
          </Field>
          <Field label={t('access.detail.capabilities')} mono>
            {entry.clientCapabilities ?? dash}
          </Field>
          <Field label={t('access.detail.session')} mono wide>
            {entry.sessionId ?? dash}
          </Field>
          <Field label={t('access.detail.userAgent')} mono wide>
            {entry.userAgent ?? dash}
          </Field>
          <Field label={t('access.detail.traceId')} mono wide>
            {entry.traceId ?? dash}
          </Field>
          <Box sx={{ gridColumn: '1 / -1', minWidth: 0 }}>
            <Typography component="dt" variant="caption" sx={{ color: 'text.secondary', display: 'block' }}>
              {t('access.detail.arguments')}
            </Typography>
            <Box
              component="dd"
              sx={{ m: 0, mt: 0.5, p: 1.5, bgcolor: 'background.subtle', border: 1, borderColor: 'divider', borderRadius: 1.5, overflowX: 'auto' }}
            >
              <Typography component="pre" variant="body2" sx={{ m: 0, fontFamily: 'monospace', fontSize: 13, whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>
                {args || t('access.detail.noArguments')}
              </Typography>
            </Box>
          </Box>
        </Box>
      </DialogContent>
      <DialogActions sx={{ px: 3, py: 1.5, flexWrap: 'wrap', gap: 1 }}>
        {issueHref && (
          <Button component={RouterLink} to={issueHref} color="inherit">
            {t('access.detail.openIssue')}
          </Button>
        )}
        {onSameKey && entry.keyId && (
          <Button color="inherit" onClick={() => onSameKey(entry.keyId as string)}>
            {t('access.detail.sameKey')}
          </Button>
        )}
        {onSameSession && entry.sessionId && (
          <Button color="inherit" onClick={() => onSameSession(entry.sessionId as string)}>
            {t('access.detail.sameSession')}
          </Button>
        )}
        <Button variant="contained" onClick={onClose}>
          {t('access.detail.close')}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
