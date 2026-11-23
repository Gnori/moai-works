import Chip from '@mui/material/Chip';
import Tooltip from '@mui/material/Tooltip';
import { useTranslation } from 'react-i18next';

import type { IssueVia } from '@/shared/api/types';

/**
 * 들어온 길 표시 — 웹은 적지 않고, MCP(AI Agent 가 키 주인의 권한으로 한 일)와 시스템(서버가 스스로 한 일 — 워크스페이스 단위로 번호를 다시 매긴 것 같은)만
 * 칩으로 보인다. 뜻은 도구 설명에 글로도 적는다(색만으로 전하지 않는다). 이력 · 리비전 · 작업 시간 · 첨부가 같이 쓴다.
 */
export function ViaChip({ via, compact = false }: { via: IssueVia; compact?: boolean }) {
  const { t } = useTranslation('issue');
  if (via !== 'MCP' && via !== 'SYSTEM') {
    return null;
  }
  const mcp = via === 'MCP';
  return (
    <Tooltip title={mcp ? t('journal.viaMcpHelp') : t('journal.viaSystemHelp')}>
      <Chip
        size="small"
        variant="outlined"
        label={mcp ? t('journal.viaMcp') : t('journal.viaSystem')}
        sx={compact ? { height: 20, fontSize: 11 } : undefined}
      />
    </Tooltip>
  );
}
