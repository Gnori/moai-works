import ChevronLeftRounded from '@mui/icons-material/ChevronLeftRounded';
import ChevronRightRounded from '@mui/icons-material/ChevronRightRounded';
import CloseRounded from '@mui/icons-material/CloseRounded';
import CodeRounded from '@mui/icons-material/CodeRounded';
import DownloadRounded from '@mui/icons-material/DownloadRounded';
import FitScreenRounded from '@mui/icons-material/FitScreenRounded';
import FullscreenExitRounded from '@mui/icons-material/FullscreenExitRounded';
import FullscreenRounded from '@mui/icons-material/FullscreenRounded';
import WrapTextRounded from '@mui/icons-material/WrapTextRounded';
import ZoomInRounded from '@mui/icons-material/ZoomInRounded';
import ZoomOutRounded from '@mui/icons-material/ZoomOutRounded';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Dialog from '@mui/material/Dialog';
import DialogContent from '@mui/material/DialogContent';
import Divider from '@mui/material/Divider';
import IconButton from '@mui/material/IconButton';
import Stack from '@mui/material/Stack';
import { useTheme } from '@mui/material/styles';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import useMediaQuery from '@mui/material/useMediaQuery';
import { useCallback, useId, useMemo, useState, type KeyboardEvent, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';

import { attachmentApi } from '@/shared/api/endpoints';
import type { AttachmentView } from '@/shared/api/types';
import { fileSize } from '@/shared/lib/format';

import { MAX_ZOOM, MIN_ZOOM, isZoomable, viewerType, zoomIn, zoomOut } from './attachment-kinds';
import { ViewerBody, type ViewOptions } from './attachment-views';
import { isTyping } from './attachments';

interface Props {
  slug: string;
  number: number;
  /** 이 일감의 첨부(목록 순서) — 이전 · 다음이 이 순서를 따른다 */
  items: readonly AttachmentView[];
  /** 연 첨부 id — null 이면 닫혀 있다 */
  openId: string | null;
  onOpen: (id: string) => void;
  onClose: () => void;
}

interface ViewState extends Omit<ViewOptions, 'wrap'> {
  id: string | null;
  /** 줄 바꿈 — 고르기 전(null)에는 형식에 따라: 글은 켜고(휴대폰에서 긴 줄이 잘리지 않게) 코드 · JSON 은 끈다(들여쓰기가 보이게) */
  wrap: boolean | null;
}

const initialView = (id: string | null): ViewState => ({ id, zoom: 1, fit: true, raw: false, wrap: null });

/**
 * 첨부 보기 — 첨부파일 탭과 본문 · 댓글의 첨부 표시가 여는 팝업(일감 #310). 내려받지 않고 이미지 · PDF · Markdown · 코드 · JSON ·
 * CSV/TSV · 글 · 동영상 · 소리를 본다(형식은 attachment-kinds). 그 밖은 "미리 볼 수 없습니다"와 내려받기.
 *
 * 머리: 파일 이름 · 크기 · n / 전체, 이전 · 다음(← / →), 닫기(Esc). 도구: 축소 · 배율(누르면 100%) · 확대(+ / - / 0), 이미지는 화면에 맞춤,
 * Markdown · 표 · JSON 은 원문 보기, 글 · 코드는 줄 바꿈(글은 켜고 시작), 전체 화면, 내려받기. 휴대폰 폭에서는 전체 화면으로 연다.
 * 모양은 moai-wiki 의 페이지 팝업(PagePreviewDialog)과 같다.
 */
export function AttachmentViewer({ slug, number, items, openId, onOpen, onClose }: Props) {
  const { t } = useTranslation('issue');
  const theme = useTheme();
  const phone = useMediaQuery(theme.breakpoints.down('sm'), { noSsr: true });
  const titleId = useId();
  const [fullChoice, setFullChoice] = useState<boolean | null>(null);
  const full = fullChoice ?? phone;

  const index = openId ? items.findIndex((a) => a.id === openId) : -1;
  const open = index >= 0 ? items[index] : undefined;
  // 닫히는 동안(사라지는 움직임)에도 내용을 그대로 보인다
  const [last, setLast] = useState<AttachmentView | undefined>(open);
  if (open && open !== last) {
    setLast(open);
  }
  const attachment = open ?? last;

  // 다른 첨부로 넘어가면 배율 · 보기를 처음으로
  const [view, setView] = useState<ViewState>(() => initialView(openId));
  if (view.id !== openId && openId) {
    setView(initialView(openId));
  }
  const [fitScale, setFitScale] = useState(1);

  const type = useMemo(() => (attachment ? viewerType(attachment) : null), [attachment]);
  const kind = type?.kind ?? 'unsupported';
  const zoomable = isZoomable(kind);
  const fitting = kind === 'image' && view.fit;
  const scale = fitting ? fitScale : view.zoom;

  const prev = index > 0 ? items[index - 1] : undefined;
  const next = index >= 0 && index < items.length - 1 ? items[index + 1] : undefined;

  const zoomTo = useCallback(
    (value: number) => setView((v) => ({ ...v, fit: false, zoom: Math.min(MAX_ZOOM, Math.max(MIN_ZOOM, value)) })),
    [],
  );
  const bigger = () => zoomTo(zoomIn(scale));
  const smaller = () => zoomTo(zoomOut(scale));
  const reset = () => zoomTo(1);

  const onKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
    if (event.altKey || event.ctrlKey || event.metaKey || isTyping(event.target as HTMLElement)) {
      return;
    }
    let handled = true;
    if (event.key === 'ArrowLeft' && prev) {
      onOpen(prev.id);
    } else if (event.key === 'ArrowRight' && next) {
      onOpen(next.id);
    } else if (zoomable && (event.key === '+' || event.key === '=')) {
      bigger();
    } else if (zoomable && (event.key === '-' || event.key === '_')) {
      smaller();
    } else if (zoomable && event.key === '0') {
      reset();
    } else {
      handled = false;
    }
    if (handled) {
      event.preventDefault();
    }
  };

  const url = attachment ? attachmentApi.contentUrl(slug, number, attachment.id) : '';
  const rawable = kind === 'markdown' || kind === 'csv' || kind === 'tsv' || kind === 'json';
  const wrappable = kind === 'code' || kind === 'json' || kind === 'text' || kind === 'sniff' || (view.raw && rawable);
  const percent = Math.round(scale * 100);
  const wrap = view.wrap ?? (kind === 'text' || kind === 'sniff');

  const stepButton = (side: 'prev' | 'next') => {
    const to = side === 'prev' ? prev : next;
    const label = to ? t(`viewer.${side}`, { name: to.fileName }) : t(`viewer.${side}None`);
    return (
      <Tooltip title={label}>
        {/* 누를 수 없는 단추에도 도구 설명이 나오게 span 으로 감싼다 */}
        <span>
          <IconButton onClick={() => to && onOpen(to.id)} disabled={!to} aria-label={label}>
            {side === 'prev' ? <ChevronLeftRounded /> : <ChevronRightRounded />}
          </IconButton>
        </span>
      </Tooltip>
    );
  };

  return (
    <Dialog
      open={!!open}
      onClose={onClose}
      onKeyDown={onKeyDown}
      fullWidth
      maxWidth="lg"
      fullScreen={full}
      aria-labelledby={titleId}
      slotProps={{ paper: { sx: full ? undefined : { height: 'min(90vh, 1100px)' } } }}
    >
      <Stack direction="row" sx={{ alignItems: 'flex-start', gap: 1, pl: { xs: 2, sm: 3 }, pr: 1, py: 1 }}>
        <Box sx={{ flex: 1, minWidth: 0, pt: 0.5 }}>
          <Typography id={titleId} component="h2" sx={{ fontSize: { xs: '1rem', sm: '1.125rem' }, fontWeight: 700, overflowWrap: 'anywhere' }}>
            {attachment?.fileName ?? t('viewer.label')}
          </Typography>
          {attachment && (
            <Typography variant="caption" sx={{ color: 'text.secondary', fontVariantNumeric: 'tabular-nums' }}>
              {fileSize(attachment.size)} · {t('viewer.position', { index: index + 1 || 1, total: items.length || 1 })}
            </Typography>
          )}
        </Box>
        <Stack direction="row" sx={{ flexShrink: 0 }}>
          {stepButton('prev')}
          {stepButton('next')}
        </Stack>
        <Tooltip title={t('viewer.close')}>
          <IconButton onClick={onClose} aria-label={t('viewer.close')}>
            <CloseRounded />
          </IconButton>
        </Tooltip>
      </Stack>

      <Stack
        direction="row"
        role="toolbar"
        aria-label={t('viewer.toolbar')}
        sx={{ alignItems: 'center', flexWrap: 'wrap', columnGap: 0.25, rowGap: 0.5, px: { xs: 1, sm: 2 }, pb: 1 }}
      >
        {zoomable && (
          <>
            <ToolButton label={t('viewer.zoomOut')} onClick={smaller} disabled={scale <= MIN_ZOOM + 0.001}>
              <ZoomOutRounded fontSize="small" />
            </ToolButton>
            {/* 지금 배율 — 누르면 실제 크기(100%)로. 배율과 되돌리기를 한 칸에 둔다(따로 두면 "100%" 가 둘로 보였다) */}
            <Tooltip title={t('viewer.zoomReset')}>
              <Button
                size="small"
                color="inherit"
                onClick={reset}
                aria-live="polite"
                aria-label={`${t('viewer.zoomLevel', { percent })} — ${t('viewer.zoomReset')}`}
                sx={{ minWidth: 56, px: 0.75, fontWeight: 400, fontVariantNumeric: 'tabular-nums', color: 'text.secondary' }}
              >
                {fitting ? `${t('viewer.fitShort')} ${percent}%` : `${percent}%`}
              </Button>
            </Tooltip>
            <ToolButton label={t('viewer.zoomIn')} onClick={bigger} disabled={scale >= MAX_ZOOM - 0.001}>
              <ZoomInRounded fontSize="small" />
            </ToolButton>
            {kind === 'image' && (
              <ToolButton label={t('viewer.fit')} pressed={view.fit} onClick={() => setView((v) => ({ ...v, fit: !v.fit, zoom: v.fit ? 1 : v.zoom }))}>
                <FitScreenRounded fontSize="small" />
              </ToolButton>
            )}
          </>
        )}
        {(rawable || wrappable) && zoomable && <Divider orientation="vertical" flexItem sx={{ mx: 0.5, my: 0.75 }} />}
        {rawable && (
          <ToolButton label={t('viewer.raw')} pressed={view.raw} onClick={() => setView((v) => ({ ...v, raw: !v.raw }))}>
            <CodeRounded fontSize="small" />
          </ToolButton>
        )}
        {wrappable && (
          <ToolButton label={t('viewer.wrap')} pressed={wrap} onClick={() => setView((v) => ({ ...v, wrap: !wrap }))}>
            <WrapTextRounded fontSize="small" />
          </ToolButton>
        )}
        <Box sx={{ flex: 1 }} />
        <ToolButton label={full ? t('viewer.exitFullscreen') : t('viewer.fullscreen')} pressed={full} onClick={() => setFullChoice(!full)}>
          {full ? <FullscreenExitRounded fontSize="small" /> : <FullscreenRounded fontSize="small" />}
        </ToolButton>
        {attachment && (
          <Tooltip title={t('viewer.download')}>
            <IconButton size="small" component="a" href={url} download aria-label={t('attachments.download', { name: attachment.fileName })}>
              <DownloadRounded fontSize="small" />
            </IconButton>
          </Tooltip>
        )}
      </Stack>

      <DialogContent dividers sx={{ p: 0, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
        {attachment && type && (
          <ViewerBody
            key={attachment.id}
            attachment={attachment}
            url={url}
            type={type}
            options={{ ...view, zoom: scale, wrap }}
            onFitScale={setFitScale}
          />
        )}
      </DialogContent>
    </Dialog>
  );
}

function ToolButton({ label, onClick, disabled = false, pressed, children }: {
  label: string;
  onClick: () => void;
  disabled?: boolean;
  /** 켜고 끄는 단추 — aria-pressed */
  pressed?: boolean;
  children: ReactNode;
}) {
  return (
    <Tooltip title={label}>
      <span>
        <IconButton
          size="small"
          onClick={onClick}
          disabled={disabled}
          aria-label={label}
          aria-pressed={pressed}
          sx={pressed ? { color: 'primary.main', bgcolor: 'action.selected' } : undefined}
        >
          {children}
        </IconButton>
      </span>
    </Tooltip>
  );
}

export default AttachmentViewer;
