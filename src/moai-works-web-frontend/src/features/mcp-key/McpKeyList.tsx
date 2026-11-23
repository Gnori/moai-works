import ManageSearchRounded from '@mui/icons-material/ManageSearchRounded';
import ReceiptLongRounded from '@mui/icons-material/ReceiptLongRounded';
import TimerOffRounded from '@mui/icons-material/TimerOffRounded';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Chip from '@mui/material/Chip';
import Stack from '@mui/material/Stack';
import ToggleButton from '@mui/material/ToggleButton';
import ToggleButtonGroup from '@mui/material/ToggleButtonGroup';
import Typography from '@mui/material/Typography';
import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { Link as RouterLink } from 'react-router';

import type { McpKey, McpKeyStatusFilter } from '@/shared/api/types';
import { dateTimeMs } from '@/shared/lib/format';
import { useLabels } from '@/shared/lib/labels';
import { summarizeUserAgent } from '@/shared/lib/user-agent';

import { keyEndReason, keyStatusFilters, personName } from './mcp-key';

/** 상태 조건 — 전체 · 사용 중 · 만료 */
export function KeyStatusFilter({ value, onChange }: { value: McpKeyStatusFilter; onChange: (next: McpKeyStatusFilter) => void }) {
  const { t } = useTranslation('mcp');
  const labels = useLabels();
  return (
    <ToggleButtonGroup
      exclusive
      size="small"
      value={value}
      onChange={(_, next: McpKeyStatusFilter | null) => next && onChange(next)}
      aria-label={t('statusFilter.label')}
      sx={{ flexShrink: 0 }}
    >
      {keyStatusFilters.map((s) => (
        <ToggleButton key={s} value={s} sx={{ px: 2 }}>
          {labels.mcpKeyStatusFilter(s)}
        </ToggleButton>
      ))}
    </ToggleButtonGroup>
  );
}

/** 이름표 한 칸 (dt · dd). wide 면 한 줄을 다 쓴다 */
function Field({ label, children, title, wide = false }: { label: string; children: ReactNode; title?: string; wide?: boolean }) {
  return (
    <Box sx={{ minWidth: 0, gridColumn: wide ? '1 / -1' : undefined }}>
      <Typography component="dt" variant="caption" sx={{ color: 'text.secondary', display: 'block' }}>
        {label}
      </Typography>
      <Typography component="dd" variant="body2" title={title} sx={{ m: 0, overflowWrap: 'anywhere', fontVariantNumeric: 'tabular-nums' }}>
        {children}
      </Typography>
    </Box>
  );
}

interface Props {
  keys: McpKey[];
  /** 주인을 보인다 (워크스페이스 키 관리) */
  showOwner?: boolean;
  /** 이 키를 만료시킬 수 있나 — 사용 중인 내 키에만 단추가 나온다(키는 주인만 만료시킨다). 판정은 api 가 다시 한다 */
  canExpire?: (key: McpKey) => boolean;
  onExpire?: (key: McpKey) => void;
  /** 그 키 주인의 이 워크스페이스 감사 기록 주소 — 없으면 링크를 그리지 않는다 */
  auditHref?: (key: McpKey) => string | null;
  /** 그 키의 이 워크스페이스 MCP 접근 기록 주소(audit.read) — 없으면 링크를 그리지 않는다 */
  accessHref?: (key: McpKey) => string | null;
  /** 그 키의 접근 기록 전부를 연다(내 키 — 대화상자) */
  onAccessLog?: (key: McpKey) => void;
  /** 넓은 화면에서 한 줄에 놓는 칸 수 — 대화상자 안처럼 좁은 곳은 줄인다 */
  wideColumns?: 3 | 4;
}

/**
 * MCP 키 목록 — 한 키가 한 덩어리(이름 · 상태 · 앞부분 · 동작, 아래에 발급 · 만료 · 마지막 사용 · 발급한 곳 · 만료 처리).
 * 표 대신 덩어리로 그려 390px 에서도 가로로 밀리지 않고, 넓은 화면에서는 칸이 한 줄에 넷까지 늘어선다.
 */
export function McpKeyList({ keys, showOwner = false, canExpire = () => false, onExpire, auditHref, accessHref, onAccessLog, wideColumns = 4 }: Props) {
  const { t } = useTranslation('mcp');
  const labels = useLabels();
  return (
    <Box component="ul" aria-label={t('list.label')} sx={{ listStyle: 'none', m: 0, p: 0 }}>
      {keys.map((key, index) => {
        const ended = keyEndReason(key);
        const program = summarizeUserAgent(key.issuedUserAgent);
        const issuedFrom = [key.issuedIp, program].filter(Boolean).join(' · ');
        const audit = auditHref?.(key) ?? null;
        const access = accessHref?.(key) ?? null;
        const ownerName = personName(key.owner);
        return (
          <Box
            component="li"
            key={key.id}
            aria-labelledby={`mcp-key-${key.id}`}
            sx={{ px: { xs: 2, md: 2.5 }, py: 2, borderTop: index === 0 ? 0 : 1, borderColor: 'divider' }}
          >
            <Stack
              direction={{ xs: 'column', sm: 'row' }}
              spacing={1}
              sx={{ alignItems: { xs: 'stretch', sm: 'flex-start' }, justifyContent: 'space-between' }}
            >
              <Box sx={{ minWidth: 0 }}>
                <Stack direction="row" spacing={1} sx={{ alignItems: 'center', flexWrap: 'wrap', rowGap: 0.5 }}>
                  <Typography id={`mcp-key-${key.id}`} variant="subtitle1" component="h3" sx={{ fontWeight: 700, overflowWrap: 'anywhere' }}>
                    {key.name}
                  </Typography>
                  <Chip
                    size="small"
                    color={key.status === 'ACTIVE' ? 'success' : 'default'}
                    variant={key.status === 'ACTIVE' ? 'filled' : 'outlined'}
                    label={labels.mcpKeyStatus(key.status)}
                  />
                </Stack>
                <Typography
                  variant="caption"
                  title={t('list.prefix', { prefix: key.keyPrefix })}
                  sx={{ fontFamily: 'monospace', color: 'text.secondary', display: 'block', overflowWrap: 'anywhere' }}
                >
                  {key.keyPrefix}…
                </Typography>
              </Box>
              {(audit || access || onAccessLog || (key.status === 'ACTIVE' && canExpire(key))) && (
                <Stack direction="row" spacing={1} sx={{ flexShrink: 0, flexWrap: 'wrap', rowGap: 1 }}>
                  {access && (
                    <Button
                      size="small"
                      color="inherit"
                      component={RouterLink}
                      to={access}
                      startIcon={<ManageSearchRounded />}
                      aria-label={t('list.accessAria', { name: key.name })}
                    >
                      {t('list.accessLog')}
                    </Button>
                  )}
                  {onAccessLog && (
                    <Button
                      size="small"
                      color="inherit"
                      onClick={() => onAccessLog(key)}
                      startIcon={<ManageSearchRounded />}
                      aria-label={t('list.accessAria', { name: key.name })}
                    >
                      {t('list.accessLog')}
                    </Button>
                  )}
                  {audit && (
                    <Button
                      size="small"
                      color="inherit"
                      component={RouterLink}
                      to={audit}
                      startIcon={<ReceiptLongRounded />}
                      aria-label={t('list.auditAria', { name: ownerName })}
                    >
                      {t('list.auditLink')}
                    </Button>
                  )}
                  {key.status === 'ACTIVE' && canExpire(key) && (
                    <Button
                      size="small"
                      color="error"
                      variant="outlined"
                      startIcon={<TimerOffRounded />}
                      onClick={() => onExpire?.(key)}
                      aria-label={t('list.expireAria', { name: key.name })}
                    >
                      {t('list.expire')}
                    </Button>
                  )}
                </Stack>
              )}
            </Stack>
            <Box
              component="dl"
              sx={{
                m: 0,
                mt: 1.5,
                display: 'grid',
                columnGap: 2,
                rowGap: 1.25,
                gridTemplateColumns: { xs: 'repeat(2, minmax(0, 1fr))', lg: `repeat(${wideColumns}, minmax(0, 1fr))` },
              }}
            >
              {showOwner && (
                <Field label={t('fields.owner')}>
                  {ownerName}
                  {key.mine && (
                    <Typography component="span" variant="caption" sx={{ color: 'text.secondary', ml: 0.5 }}>
                      {t('list.me')}
                    </Typography>
                  )}
                </Field>
              )}
              <Field label={t('fields.createdAt')}>{dateTimeMs(key.createdAt)}</Field>
              <Field label={key.status === 'ACTIVE' ? t('fields.expiresAt') : t('fields.expiredAt')}>{dateTimeMs(key.expiresAt)}</Field>
              <Field label={t('fields.lastUsedAt')}>{key.lastUsedAt ? dateTimeMs(key.lastUsedAt) : t('fields.neverUsed')}</Field>
              {key.callCount !== null && key.callCount !== undefined && (
                <Field label={t('fields.callCount')}>{t('fields.calls', { count: key.callCount })}</Field>
              )}
              <Field label={t('fields.issuedFrom')} title={key.issuedUserAgent ?? undefined}>
                {issuedFrom || '—'}
              </Field>
              {ended && (
                <Field label={t('fields.expiry')} wide>
                  {key.expiredBy
                    ? t('fields.expiredBy', { reason: labels.mcpKeyEndReason(ended), name: personName(key.expiredBy) })
                    : labels.mcpKeyEndReason(ended)}
                  {key.expireNote && (
                    <Typography component="span" variant="body2" sx={{ display: 'block', color: 'text.secondary' }}>
                      {t('fields.note', { note: key.expireNote })}
                    </Typography>
                  )}
                </Field>
              )}
            </Box>
          </Box>
        );
      })}
    </Box>
  );
}
