import Button from '@mui/material/Button';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogTitle from '@mui/material/DialogTitle';
import Typography from '@mui/material/Typography';
import useMediaQuery from '@mui/material/useMediaQuery';
import { useTheme } from '@mui/material/styles';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';

import { issuePath } from '@/app/routes';
import type { McpKey } from '@/shared/api/types';

import { emptyAccessFilters, type AccessFilters } from './access-log';
import { AccessLogView } from './AccessLogView';

/**
 * 내 키 하나의 접근 기록 — 그 키로 들어온 요청 전부(모든 워크스페이스, 워크스페이스를 정하지 않은 연결 · 도구 목록 · 거절까지).
 * 개인 설정 대화상자 위에 겹쳐 열고, 휴대폰에서는 화면을 꽉 채운다. 조건은 이 창의 상태로 둔다(주소의 settings 는 개인 설정 대화상자의 몫).
 * 키 주인만 볼 수 있다 — 판정은 api(남의 키는 404).
 */
export function KeyAccessLogDialog({ target, onClose }: { target: McpKey; onClose: () => void }) {
  const { t } = useTranslation('mcp');
  const theme = useTheme();
  const phone = useMediaQuery(theme.breakpoints.down('sm'));
  const [filters, setFilters] = useState<AccessFilters>(emptyAccessFilters);
  return (
    <Dialog open onClose={onClose} maxWidth="lg" fullWidth fullScreen={phone} aria-labelledby="mcp-key-access-title">
      <DialogTitle id="mcp-key-access-title" sx={{ fontWeight: 700, overflowWrap: 'anywhere' }}>
        {t('access.keyDialog.title', { name: target.name })}
      </DialogTitle>
      <DialogContent>
        <Typography variant="body2" sx={{ color: 'text.secondary', mb: 2 }}>
          {t('access.keyDialog.description')}
        </Typography>
        <AccessLogView
          scope={{ kind: 'key', keyId: target.id }}
          filters={filters}
          onChange={setFilters}
          showOwner={false}
          showWorkspace
          // 번호는 워크스페이스 안에서 하나라 워크스페이스와 번호만으로 정본 주소가 된다(일감을 옮겨도 그대로)
          issueHref={(e) => (e.workspaceSlug && e.issueNumber !== null ? issuePath(e.workspaceSlug, e.issueNumber) : null)}
        />
      </DialogContent>
      <DialogActions sx={{ px: 3, py: 1.5 }}>
        <Button onClick={onClose}>{t('access.keyDialog.close')}</Button>
      </DialogActions>
    </Dialog>
  );
}
