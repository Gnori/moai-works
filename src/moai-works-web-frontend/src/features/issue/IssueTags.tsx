import SmartToyRounded from '@mui/icons-material/SmartToyRounded';
import ViewModuleRounded from '@mui/icons-material/ViewModuleRounded';
import Box from '@mui/material/Box';
import Chip from '@mui/material/Chip';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';

import type { IssueModuleRef } from '@/shared/api/types';

/**
 * 일감에 붙는 꼬리표 — 에이전트 세션(이 일을 맡은 AI 세션) · 관련 모듈. 목록 · 보기 속성이 같이 쓴다.
 * 칩은 색만으로 뜻을 전하지 않는다 — 아이콘과 함께 aria-label 에 "에이전트 세션: 이름"을 적는다. 긴 이름은 칩 안에서 줄인다(title 로 전체).
 */
export function AgentSessionChip({ session }: { session: string }) {
  const { t } = useTranslation('issue');
  return (
    <Chip
      size="small"
      variant="outlined"
      icon={<SmartToyRounded />}
      label={session}
      title={session}
      aria-label={t('tags.agentSession', { name: session })}
      sx={{ maxWidth: 220, height: 22, fontSize: '0.75rem', '& .MuiChip-label': { px: 0.75 }, '& .MuiChip-icon': { fontSize: 15, ml: 0.5 } }}
    />
  );
}

/**
 * 관련 모듈 칩 하나 — 이름을 보이고 key 는 도구 설명으로. 쓰지 않는 모듈은 점선 테두리에 "쓰지 않음"을 말로도 붙인다(색만으로 전하지 않는다).
 */
export function ModuleChip({ module }: { module: IssueModuleRef }) {
  const { t } = useTranslation('issue');
  const label = module.active ? module.name : t('modules.inactiveName', { name: module.name });
  return (
    <Chip
      size="small"
      variant="outlined"
      color={module.active ? 'primary' : 'default'}
      icon={<ViewModuleRounded />}
      label={label}
      title={module.key !== module.name ? `${label} (${module.key})` : label}
      aria-label={t('tags.module', { name: label })}
      sx={{
        maxWidth: 220,
        height: 22,
        fontSize: '0.75rem',
        borderStyle: module.active ? 'solid' : 'dashed',
        '& .MuiChip-label': { px: 0.75 },
        '& .MuiChip-icon': { fontSize: 15, ml: 0.5 },
      }}
    />
  );
}

/** 목록 한 줄의 꼬리표 묶음(모듈 · 세션) — 없으면 아무것도 그리지 않는다 */
export function IssueTags({ agentSession, modules }: { agentSession: string | null; modules: IssueModuleRef[] }) {
  if (!agentSession && modules.length === 0) {
    return null;
  }
  return (
    <Stack direction="row" spacing={0.5} useFlexGap sx={{ flexWrap: 'wrap', mt: 0.5, minWidth: 0 }}>
      {modules.map((m) => (
        <ModuleChip key={m.key} module={m} />
      ))}
      {agentSession && <AgentSessionChip session={agentSession} />}
    </Stack>
  );
}

/**
 * 일감 보기 속성의 꼬리표 줄 — 관련 모듈 · 에이전트 세션. 이름(dt)을 값(dd) 위에 둔다(속성 칸과 같은 모양).
 * 속성 칸의 셋째 줄이고, 좁은 화면에서도 접지 않는다(무엇을 고치는지 · 누가 맡았는지가 이 칸의 쓰임이다).
 */
export function IssueTagFields({ agentSession, modules }: { agentSession: string | null; modules: IssueModuleRef[] }) {
  const { t } = useTranslation('issue');
  return (
    <Box
      component="dl"
      sx={{ m: 0, display: 'grid', columnGap: 3, rowGap: 1.5, gridTemplateColumns: 'minmax(0, 1fr)', '@container (min-width: 560px)': { gridTemplateColumns: 'minmax(0, 2fr) minmax(0, 1fr)' } }}
    >
      <TagField label={t('fields.modules')}>
        {modules.length > 0 ? (
          <Stack direction="row" spacing={0.5} useFlexGap sx={{ flexWrap: 'wrap' }}>
            {modules.map((m) => (
              <ModuleChip key={m.key} module={m} />
            ))}
          </Stack>
        ) : (
          <Box component="span" sx={{ color: 'text.secondary' }}>
            {t('view.noModules')}
          </Box>
        )}
      </TagField>
      <TagField label={t('fields.agentSession')}>
        {agentSession ? <AgentSessionChip session={agentSession} /> : <Box component="span" sx={{ color: 'text.secondary' }}>{t('view.noAgentSession')}</Box>}
      </TagField>
    </Box>
  );
}

function TagField({ label, children }: { label: string; children: ReactNode }) {
  return (
    <Box sx={{ minWidth: 0 }}>
      <Typography component="dt" variant="caption" sx={{ color: 'text.secondary', whiteSpace: 'nowrap', display: 'block' }}>
        {label}
      </Typography>
      <Box component="dd" sx={{ m: 0, mt: 0.25, minWidth: 0, typography: 'body2' }}>
        {children}
      </Box>
    </Box>
  );
}
