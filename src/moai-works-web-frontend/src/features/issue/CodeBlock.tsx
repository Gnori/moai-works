import CheckRounded from '@mui/icons-material/CheckRounded';
import ContentCopyRounded from '@mui/icons-material/ContentCopyRounded';
import Box from '@mui/material/Box';
import IconButton from '@mui/material/IconButton';
import Tooltip from '@mui/material/Tooltip';
import { useEffect, useState, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';

interface Props {
  /** 적힌 언어. 없으면 머리에 이름을 보이지 않는다 */
  language: string | null;
  /** 복사할 글자 그대로 */
  text: string;
  /** 강조가 입혀진 <code> */
  children: ReactNode;
}

/** 본문의 코드 블록 — 언어 이름과 복사 단추를 머리에 둔다. 긴 줄은 이 상자 안에서만 가로로 밀린다. */
export function CodeBlock({ language, text, children }: Props) {
  const { t } = useTranslation('issue');
  const [copied, setCopied] = useState(false);

  useEffect(() => {
    if (!copied) {
      return undefined;
    }
    const timer = window.setTimeout(() => setCopied(false), 2000);
    return () => window.clearTimeout(timer);
  }, [copied]);

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(text);
      setCopied(true);
    } catch {
      // 브라우저가 막으면(권한 · 보안 문맥) 아무 일도 하지 않는다 — 글자는 골라서 복사할 수 있다
    }
  };

  const label = copied ? t('view.copied') : t('view.copyCode');
  return (
    <Box className="code-block">
      <Box className="code-head">
        <span>{language ?? ''}</span>
        <Tooltip title={label}>
          <IconButton size="small" aria-label={label} onClick={() => void copy()}>
            {copied ? <CheckRounded fontSize="inherit" /> : <ContentCopyRounded fontSize="inherit" />}
          </IconButton>
        </Tooltip>
      </Box>
      <pre>{children}</pre>
    </Box>
  );
}
