import CheckRounded from '@mui/icons-material/CheckRounded';
import ContentCopyRounded from '@mui/icons-material/ContentCopyRounded';
import ExpandLessRounded from '@mui/icons-material/ExpandLessRounded';
import ExpandMoreRounded from '@mui/icons-material/ExpandMoreRounded';
import FormatListNumberedRounded from '@mui/icons-material/FormatListNumberedRounded';
import InsertDriveFileOutlined from '@mui/icons-material/InsertDriveFileOutlined';
import WrapTextRounded from '@mui/icons-material/WrapTextRounded';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import IconButton from '@mui/material/IconButton';
import Tooltip from '@mui/material/Tooltip';
import { useEffect, useId, useState, type CSSProperties, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';

import { languageLabel } from './code-languages';
import { COLLAPSE_OVER, COLLAPSED_LINES, LINE_NUMBERS_FROM, countLines } from './code-lines';

interface Props {
  /** 적힌 언어. 없으면 머리에 이름을 보이지 않는다 */
  language: string | null;
  /** 여는 줄의 title="…" — 머리에 파일 이름으로 보인다 */
  title?: string | null;
  /** 복사할 글자 그대로 */
  text: string;
  /** 줄로 나뉘고(rehypeCodeLines) 강조가 입혀진 <code> */
  children: ReactNode;
}

/**
 * 본문의 코드 블록 — 머리에 파일 이름 · 언어 이름과 단추(긴 줄 바꿈 · 줄 번호 · 복사)를 둔다.
 *
 * - 줄 번호는 4줄 이상이면 처음부터 보인다. 번호는 글자가 아니라 복사 · 화면 읽기에 들지 않는다.
 * - 긴 줄은 이 상자 안에서만 가로로 밀린다. 줄 바꿈을 켜면 상자 폭에서 접히고, 줄마다 한 칸이라 번호가 그 줄 첫머리에 맞는다.
 * - 30줄이 넘으면 20줄만 보이고 "N줄 더 보기"로 편다.
 * - 켜고 끄는 것은 상자의 class 만 바꾼다(모양은 MarkdownView 의 본문 모양). 복사는 언제나 글 전체다.
 */
export function CodeBlock({ language, title = null, text, children }: Props) {
  const { t } = useTranslation('issue');
  const codeId = useId();
  const [copied, setCopied] = useState(false);
  const [wrap, setWrap] = useState(false);
  // 고르기 전에는 줄 수로 정한다 — 미리보기에서 글이 늘어도 따라간다
  const [numbersChoice, setNumbersChoice] = useState<boolean | null>(null);
  const [expanded, setExpanded] = useState(false);

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

  const lines = countLines(text);
  const numbers = numbersChoice ?? lines >= LINE_NUMBERS_FROM;
  const collapsible = lines > COLLAPSE_OVER;
  const collapsed = collapsible && !expanded;
  const label = languageLabel(language);
  const copyLabel = copied ? t('view.copied') : t('view.copyCode');
  const className = ['code-block', numbers && 'code-numbered', wrap && 'code-wrap', collapsed && 'code-collapsed']
    .filter(Boolean)
    .join(' ');
  // 줄 번호 칸의 너비 — 가장 긴 번호의 자릿수
  const style = { '--code-digits': String(lines).length } as CSSProperties;

  return (
    <Box className={className} style={style}>
      <Box className="code-head">
        <span className="code-name">
          {title && (
            <>
              <InsertDriveFileOutlined aria-hidden className="code-file-icon" />
              <span className="code-title" title={title}>
                {title}
              </span>
            </>
          )}
          {label && <span className="code-lang">{label}</span>}
        </span>
        <span className="code-tools">
          <Tooltip title={t('view.codeWrap')}>
            <IconButton size="small" aria-label={t('view.codeWrap')} aria-pressed={wrap} onClick={() => setWrap(!wrap)}>
              <WrapTextRounded fontSize="inherit" />
            </IconButton>
          </Tooltip>
          <Tooltip title={t('view.codeLineNumbers')}>
            <IconButton
              size="small"
              aria-label={t('view.codeLineNumbers')}
              aria-pressed={numbers}
              onClick={() => setNumbersChoice(!numbers)}
            >
              <FormatListNumberedRounded fontSize="inherit" />
            </IconButton>
          </Tooltip>
          <Tooltip title={copyLabel}>
            <IconButton size="small" aria-label={copyLabel} onClick={() => void copy()}>
              {copied ? <CheckRounded fontSize="inherit" /> : <ContentCopyRounded fontSize="inherit" />}
            </IconButton>
          </Tooltip>
        </span>
      </Box>
      <pre id={codeId}>{children}</pre>
      {collapsible && (
        <Button
          className="code-more"
          size="small"
          color="inherit"
          fullWidth
          aria-controls={codeId}
          aria-expanded={!collapsed}
          startIcon={collapsed ? <ExpandMoreRounded /> : <ExpandLessRounded />}
          onClick={() => setExpanded(!expanded)}
        >
          {collapsed ? t('view.codeShowMore', { count: lines - COLLAPSED_LINES }) : t('view.codeShowLess')}
        </Button>
      )}
    </Box>
  );
}
