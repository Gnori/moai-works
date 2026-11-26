import AudioFileOutlined from '@mui/icons-material/AudioFileOutlined';
import DownloadRounded from '@mui/icons-material/DownloadRounded';
import InsertDriveFileOutlined from '@mui/icons-material/InsertDriveFileOutlined';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Stack from '@mui/material/Stack';
import type { SxProps, Theme } from '@mui/material/styles';
import Typography from '@mui/material/Typography';
import { createLowlight } from 'lowlight';
import { lazy, Suspense, useCallback, useEffect, useMemo, useRef, useState, type CSSProperties, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';

import type { AttachmentView } from '@/shared/api/types';
import { fileSize } from '@/shared/lib/format';
import { ErrorState, LoadingState } from '@/shared/ui/States';

import { fetchAttachment, type Fetched } from './attachment-content';
import {
  HIGHLIGHT_LIMIT,
  TABLE_ROW_LIMIT,
  TEXT_LIMIT,
  decodeText,
  looksLikeText,
  parseDelimited,
  prettyJson,
  type ViewerType,
} from './attachment-kinds';
import type { CodeNode } from './code-blocks';
import { useHighlightLanguage } from './code-languages';
import { codeLines } from './code-lines';
import { MarkdownView, markdownBodySx } from './MarkdownView';

/**
 * 첨부 보기의 본문 — 형식(attachment-kinds)마다 하나. 상자는 저마다 자기 안에서 밀린다(scroll).
 * 파일 내용을 HTML 로 그리지 않는다 — Markdown 은 본문과 같은 MarkdownView(HTML 은 글자로), 코드는 색 입힌 마디를 React 요소로만 만든다.
 */

/** PDF 화면은 pdf.js 와 함께 PDF 를 열 때만 받는다 */
const PdfView = lazy(() => import('./PdfView'));

const MONO = "'JetBrains Mono', 'SFMono-Regular', Menlo, Consolas, monospace";

export interface ViewOptions {
  zoom: number;
  /** 이미지 — 창에 맞춤 */
  fit: boolean;
  /** Markdown · 표 · JSON — 원문 보기 */
  raw: boolean;
  /** 글 · 코드 — 긴 줄 바꿈 */
  wrap: boolean;
}

export function ViewerBody({ attachment, url, type, options, onFitScale }: {
  attachment: AttachmentView;
  /** 내용 주소 */
  url: string;
  type: ViewerType;
  options: ViewOptions;
  /** 이미지를 창에 맞췄을 때의 배율 — 맞춤에서 확대 · 축소할 때 여기서 시작한다 */
  onFitScale: (scale: number) => void;
}) {
  switch (type.kind) {
    case 'image':
      return <ImageView src={url} alt={attachment.fileName} zoom={options.zoom} fit={options.fit} onFitScale={onFitScale} />;
    case 'pdf':
      return (
        <Suspense fallback={<LoadingState />}>
          <PdfView url={url} zoom={options.zoom} />
        </Suspense>
      );
    case 'video':
    case 'audio':
      return <MediaView url={url} kind={type.kind} mime={type.mime ?? ''} name={attachment.fileName} />;
    case 'unsupported':
      return <Unsupported url={url} attachment={attachment} />;
    default:
      // 글로 보는 형식(markdown · code · json · csv · tsv · text · sniff)
      return <TextBody url={url} attachment={attachment} type={type} options={options} />;
  }
}

/** 내용을 받는다 — 끝나기 전에 바뀌거나 닫으면 끊는다. null 이면 받는 중 */
function useFetched(url: string, limit: number | undefined) {
  const [attempt, setAttempt] = useState(0);
  const key = `${url}#${limit ?? ''}#${attempt}`;
  const [result, setResult] = useState<{ key: string; data?: Fetched; error?: unknown } | null>(null);
  useEffect(() => {
    const controller = new AbortController();
    fetchAttachment(url, { limit, signal: controller.signal }).then(
      (data) => setResult({ key, data }),
      (error: unknown) => {
        if (!controller.signal.aborted) {
          setResult({ key, error });
        }
      },
    );
    return () => controller.abort();
  }, [key, url, limit]);
  const retry = useCallback(() => setAttempt((n) => n + 1), []);
  return { result: result?.key === key ? result : null, retry };
}

function Scroll({ children, sx, label }: { children: ReactNode; sx?: Record<string, unknown>; label?: string }) {
  // 키보드로도 밀 수 있게 초점을 받는다
  return (
    <Box tabIndex={0} role={label ? 'region' : undefined} aria-label={label} sx={{ flex: 1, minHeight: 0, overflow: 'auto', outline: 'none', ...sx }}>
      {children}
    </Box>
  );
}

/** 이미지 — 창에 맞춤(작은 그림은 키우지 않는다) 또는 배율대로. 내용 주소를 그대로 쓴다(서버가 inline 으로 준다) */
function ImageView({ src, alt, zoom, fit, onFitScale }: {
  src: string;
  alt: string;
  zoom: number;
  fit: boolean;
  onFitScale: (scale: number) => void;
}) {
  const { t } = useTranslation('issue');
  const ref = useRef<HTMLImageElement>(null);
  const [failed, setFailed] = useState(false);
  const [natural, setNatural] = useState<{ width: number; height: number } | null>(null);

  const measure = useCallback(() => {
    const image = ref.current;
    if (image && image.naturalWidth > 0 && image.clientWidth > 0) {
      onFitScale(Math.min(1, image.clientWidth / image.naturalWidth));
    }
  }, [onFitScale]);

  useEffect(() => {
    const image = ref.current;
    if (!fit || !image || typeof ResizeObserver === 'undefined') {
      return undefined;
    }
    const observer = new ResizeObserver(measure);
    observer.observe(image);
    return () => observer.disconnect();
  }, [fit, measure]);

  if (failed) {
    return <Notice severity="error">{t('viewer.imageFailed')}</Notice>;
  }
  const size: CSSProperties = fit || !natural
    ? { maxWidth: '100%', maxHeight: '100%' }
    : { maxWidth: 'none', maxHeight: 'none', width: natural.width * zoom, height: natural.height * zoom };
  return (
    <Scroll sx={{ display: 'flex', bgcolor: 'background.subtle', p: { xs: 1, sm: 2 } }}>
      <img
        ref={ref}
        src={src}
        alt={alt}
        decoding="async"
        onLoad={(event) => {
          const image = event.currentTarget;
          setNatural({ width: image.naturalWidth, height: image.naturalHeight });
          measure();
        }}
        onError={() => setFailed(true)}
        style={{ margin: 'auto', display: 'block', objectFit: 'contain', ...size }}
      />
    </Scroll>
  );
}

/** 동영상 · 소리 — 받은 바이트에 형식을 붙여 blob 주소로 재생한다(서버는 octet-stream 으로 준다). 닫으면 주소를 버린다 */
function MediaView({ url, kind, mime, name }: { url: string; kind: 'video' | 'audio'; mime: string; name: string }) {
  const { t } = useTranslation('issue');
  const [attempt, setAttempt] = useState(0);
  const key = `${url}#${attempt}`;
  const [result, setResult] = useState<{ key: string; src?: string; error?: unknown } | null>(null);
  const [broken, setBroken] = useState<string | null>(null);
  useEffect(() => {
    const controller = new AbortController();
    let made: string | null = null;
    fetchAttachment(url, { signal: controller.signal }).then(
      (data) => {
        made = URL.createObjectURL(new Blob([data.bytes], { type: mime }));
        setResult({ key, src: made });
      },
      (error: unknown) => {
        if (!controller.signal.aborted) {
          setResult({ key, error });
        }
      },
    );
    return () => {
      controller.abort();
      if (made) {
        URL.revokeObjectURL(made);
      }
    };
  }, [key, url, mime]);

  const current = result?.key === key ? result : null;
  if (!current) {
    return <LoadingState />;
  }
  if (current.error !== undefined || !current.src) {
    return <ErrorState error={current.error} onRetry={() => setAttempt((n) => n + 1)} />;
  }
  if (broken === current.src) {
    return <Notice severity="warning">{t('viewer.mediaFailed')}</Notice>;
  }
  return (
    <Scroll sx={{ display: 'flex', bgcolor: kind === 'video' ? 'common.black' : 'background.subtle', p: kind === 'video' ? 0 : 3 }}>
      {kind === 'video' ? (
        <video controls src={current.src} onError={() => setBroken(current.src ?? null)} style={{ margin: 'auto', display: 'block', maxWidth: '100%', maxHeight: '100%' }} />
      ) : (
        <Stack spacing={2} sx={{ m: 'auto', alignItems: 'center', width: '100%', maxWidth: 560 }}>
          <AudioFileOutlined aria-hidden sx={{ fontSize: 48, color: 'text.secondary' }} />
          <Typography variant="body2" sx={{ fontWeight: 600, overflowWrap: 'anywhere', textAlign: 'center' }}>
            {name}
          </Typography>
          <audio controls src={current.src} onError={() => setBroken(current.src ?? null)} style={{ width: '100%' }} />
        </Stack>
      )}
    </Scroll>
  );
}

/** 볼 수 없는 형식 — 까닭과 내려받기 */
function Unsupported({ url, attachment, message }: { url: string; attachment: AttachmentView; message?: string }) {
  const { t } = useTranslation('issue');
  return (
    <Scroll sx={{ display: 'flex' }}>
      <Stack spacing={1.5} sx={{ m: 'auto', alignItems: 'center', textAlign: 'center', px: 3, py: 6, maxWidth: 480 }}>
        <InsertDriveFileOutlined aria-hidden sx={{ fontSize: 48, color: 'text.secondary' }} />
        <Typography variant="subtitle1" component="p" sx={{ fontWeight: 700 }}>
          {message ?? t('viewer.unsupported')}
        </Typography>
        <Typography variant="body2" sx={{ color: 'text.secondary' }}>
          {t('viewer.unsupportedHelp')}
        </Typography>
        <Button variant="contained" component="a" href={url} download startIcon={<DownloadRounded />}>
          {t('viewer.download')} · {fileSize(attachment.size)}
        </Button>
      </Stack>
    </Scroll>
  );
}

function Notice({ severity, children }: { severity: 'info' | 'warning' | 'error'; children: ReactNode }) {
  return (
    <Alert severity={severity} sx={{ borderRadius: 0, flexShrink: 0, py: 0.25 }}>
      {children}
    </Alert>
  );
}

/** 글로 보는 형식 — 앞 2 MB 를 받아 글인지 보고 형식대로 그린다 */
function TextBody({ url, attachment, type, options }: { url: string; attachment: AttachmentView; type: ViewerType; options: ViewOptions }) {
  const { t } = useTranslation('issue');
  const { result, retry } = useFetched(url, TEXT_LIMIT);
  const text = useMemo(() => (result?.data && looksLikeText(result.data.bytes) ? decodeText(result.data.bytes) : null), [result]);
  if (!result) {
    return <LoadingState />;
  }
  if (!result.data) {
    return <ErrorState error={result.error} onRetry={retry} />;
  }
  if (result.data.bytes.length === 0) {
    return <Notice severity="info">{t('viewer.empty')}</Notice>;
  }
  if (text === null) {
    return <Unsupported url={url} attachment={attachment} />;
  }
  const notices: ReactNode[] = [];
  if (result.data.truncated) {
    notices.push(
      <Notice key="truncated" severity="info">
        {t('viewer.truncated', { size: fileSize(TEXT_LIMIT) })}
      </Notice>,
    );
  }
  return (
    <>
      {notices}
      <TextContent text={text} type={type} options={options} />
    </>
  );
}

function TextContent({ text, type, options }: { text: string; type: ViewerType; options: ViewOptions }) {
  const { t } = useTranslation('issue');
  const zoom = { zoom: options.zoom } as const;
  switch (type.kind) {
    case 'markdown':
      return options.raw ? (
        <Scroll>
          <PlainText text={text} wrap={options.wrap} zoom={options.zoom} />
        </Scroll>
      ) : (
        <Scroll>
          <Box sx={{ ...zoom, px: { xs: 2, sm: 4 }, py: { xs: 2, sm: 3 }, maxWidth: 980, mx: 'auto' }}>
            <MarkdownView content={text} />
          </Box>
        </Scroll>
      );
    case 'json': {
      const pretty = options.raw ? null : prettyJson(text);
      return (
        <>
          {!options.raw && pretty === null && <Notice severity="info">{t('viewer.jsonInvalid')}</Notice>}
          <CodeView text={pretty ?? text} language="json" wrap={options.wrap} zoom={options.zoom} />
        </>
      );
    }
    case 'csv':
    case 'tsv':
      return options.raw ? (
        <Scroll>
          <PlainText text={text} wrap={options.wrap} zoom={options.zoom} />
        </Scroll>
      ) : (
        <TableView text={text} delimiter={type.kind === 'csv' ? ',' : '\t'} zoom={options.zoom} />
      );
    case 'code':
      return <CodeView text={text} language={type.language ?? null} wrap={options.wrap} zoom={options.zoom} />;
    default:
      return (
        <Scroll>
          <PlainText text={text} wrap={options.wrap} zoom={options.zoom} />
        </Scroll>
      );
  }
}

/** 글 그대로 — 고정폭, 줄 바꿈 켜고 끄기 */
function PlainText({ text, wrap, zoom }: { text: string; wrap: boolean; zoom: number }) {
  return (
    <Box
      component="pre"
      sx={{
        zoom,
        m: 0,
        px: { xs: 2, sm: 3 },
        py: 2,
        fontFamily: MONO,
        fontSize: '0.8125rem',
        lineHeight: 1.6,
        color: 'text.primary',
        whiteSpace: wrap ? 'pre-wrap' : 'pre',
        overflowWrap: wrap ? 'anywhere' : 'normal',
        width: wrap ? 'auto' : 'max-content',
        minWidth: '100%',
        boxSizing: 'border-box',
      }}
    >
      {text}
    </Box>
  );
}

/**
 * 코드 — 본문 코드 상자와 같은 모양(줄 번호 · 색 · 줄 바꿈, MarkdownView 의 본문 모양). 색은 highlight.js(lowlight)가 만든 마디를
 * 줄로 나눠(code-lines) React 요소로 그린다. 512 KB 가 넘으면 색 없이 줄 번호만.
 */
function CodeView({ text, language, wrap, zoom }: { text: string; language: string | null; wrap: boolean; zoom: number }) {
  const { t } = useTranslation('issue');
  const languages = useHighlightLanguage(language);
  const lowlight = useMemo(() => createLowlight(languages), [languages]);
  const big = text.length > HIGHLIGHT_LIMIT;
  const lines = useMemo(() => {
    let children: CodeNode[] = [{ type: 'text', value: text }];
    if (!big && language && lowlight.registered(language)) {
      try {
        children = lowlight.highlight(language, text).children as unknown as CodeNode[];
      } catch {
        // 색을 못 입히면 글 그대로
      }
    }
    return codeLines(children, { diff: language === 'diff' });
  }, [text, language, lowlight, big]);
  const className = ['code-block', 'code-numbered', wrap && 'code-wrap'].filter(Boolean).join(' ');
  const style = { '--code-digits': String(lines.length).length } as CSSProperties;
  return (
    <>
      {big && <Notice severity="info">{t('viewer.plainCode')}</Notice>}
      <Scroll sx={{ bgcolor: 'background.subtle' }}>
        <Box sx={bodySx({ zoom, '& .code-block': { my: 0, border: 0, borderRadius: 0, overflow: 'visible' }, '& .code-block pre': { overflowX: 'visible' } })}>
          <div className={className} style={style}>
            <pre>
              <code className={language ? `hljs language-${language}` : 'hljs'}>{lines.map((line, index) => renderCode(line, index))}</code>
            </pre>
          </div>
        </Box>
      </Scroll>
    </>
  );
}

/** 본문 모양(MarkdownView — 코드 색 · 줄 번호 · 표)에 이 화면의 것을 겹친다. 같은 선택자는 안쪽 값을 합친다 */
function bodySx(overrides: Record<string, unknown>): SxProps<Theme> {
  const base = markdownBodySx(false) as (theme: Theme) => Record<string, unknown>;
  const sx = (theme: Theme) => {
    const merged: Record<string, unknown> = { ...base(theme) };
    for (const [key, value] of Object.entries(overrides)) {
      const before = merged[key];
      merged[key] = key.startsWith('&') && before && typeof before === 'object' ? { ...before, ...(value as object) } : value;
    }
    return merged;
  };
  return sx as SxProps<Theme>;
}

/** 색 입힌 마디 → React 요소. span 과 글자만 있다(lowlight · code-lines 가 만든 것) — 그 밖의 태그는 만들지 않는다 */
function renderCode(node: CodeNode, key: number): ReactNode {
  if (node.type === 'text') {
    return node.value ?? '';
  }
  if (node.type !== 'element') {
    return null;
  }
  const properties = node.properties ?? {};
  const className = Array.isArray(properties.className) ? properties.className.join(' ') : undefined;
  return (
    <span
      key={key}
      className={className}
      aria-hidden={properties.ariaHidden === 'true' ? true : undefined}
      data-line={typeof properties.dataLine === 'number' ? properties.dataLine : undefined}
    >
      {node.children?.map((child, index) => renderCode(child, index))}
    </span>
  );
}

/** CSV · TSV — 첫 줄을 머리로 하는 표. 2000줄까지 */
function TableView({ text, delimiter, zoom }: { text: string; delimiter: ',' | '\t'; zoom: number }) {
  const { t } = useTranslation('issue');
  const table = useMemo(() => parseDelimited(text, delimiter, TABLE_ROW_LIMIT + 1), [text, delimiter]);
  const [head = [], ...body] = table.rows;
  const width = table.rows.reduce((max, row) => Math.max(max, row.length), 0);
  const cells = (row: string[]) => Array.from({ length: width }, (_, index) => row[index] ?? '');
  return (
    <>
      {table.more && <Notice severity="info">{t('viewer.tableMore', { count: TABLE_ROW_LIMIT })}</Notice>}
      <Scroll>
        <Box sx={bodySx({ zoom, p: { xs: 1, sm: 2 }, '& table': { minWidth: 'auto' }, '& td': { whiteSpace: 'pre-wrap' } })}>
          <table>
            <thead>
              <tr>
                {cells(head).map((cell, index) => (
                  <th key={index} scope="col">
                    {cell}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {body.map((row, rowIndex) => (
                <tr key={rowIndex}>
                  {cells(row).map((cell, index) => (
                    <td key={index}>{cell}</td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </Box>
      </Scroll>
    </>
  );
}
