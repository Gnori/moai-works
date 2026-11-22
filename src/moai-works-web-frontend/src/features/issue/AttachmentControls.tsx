import AddPhotoAlternateOutlined from '@mui/icons-material/AddPhotoAlternateOutlined';
import Box from '@mui/material/Box';
import Button, { type ButtonProps } from '@mui/material/Button';
import LinearProgress from '@mui/material/LinearProgress';
import Typography from '@mui/material/Typography';
import { useRef, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';

import type { UploadProgress } from './use-attachment-upload';

/**
 * 파일 고르기 단추 — 숨긴 <input type="file" multiple> 을 연다. 고른 파일을 넘기고 칸을 비운다(같은 파일을 다시 고를 수 있게).
 */
export function AttachFilesButton({ onFiles, children, icon, ...button }: Omit<ButtonProps, 'onClick' | 'children'> & {
  onFiles: (files: File[]) => void;
  children?: ReactNode;
  icon?: ReactNode;
}) {
  const { t } = useTranslation('issue');
  const input = useRef<HTMLInputElement>(null);
  return (
    <>
      <Button startIcon={icon ?? <AddPhotoAlternateOutlined />} {...button} onClick={() => input.current?.click()}>
        {children ?? t('attachments.insert')}
      </Button>
      <input
        ref={input}
        type="file"
        multiple
        hidden
        tabIndex={-1}
        aria-hidden
        onChange={(event) => {
          const files = Array.from(event.target.files ?? []);
          event.target.value = '';
          if (files.length > 0) {
            onFiles(files);
          }
        }}
      />
    </>
  );
}

/** 올리는 중 — "올리는 중 1/3 · 화면.png 45%" 와 막대. 글이 뜻을 전하고 막대는 보탬이다 */
export function UploadProgressLine({ progress }: { progress: UploadProgress | null }) {
  const { t } = useTranslation('issue');
  if (!progress) {
    return null;
  }
  const percent = Math.round(progress.fraction * 100);
  return (
    <Box role="status" sx={{ minWidth: 0 }}>
      <Typography
        variant="caption"
        component="p"
        noWrap
        title={progress.name}
        sx={{ color: 'text.secondary', m: 0, mb: 0.5, fontVariantNumeric: 'tabular-nums' }}
      >
        {t('attachments.uploading', { index: progress.index, total: progress.total, name: progress.name, percent })}
      </Typography>
      <LinearProgress
        aria-hidden
        variant={percent > 0 && percent < 100 ? 'determinate' : 'indeterminate'}
        value={percent}
        sx={{ height: 4, borderRadius: 2 }}
      />
    </Box>
  );
}
