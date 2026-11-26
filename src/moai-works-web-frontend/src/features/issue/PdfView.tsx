import Box from '@mui/material/Box';
import Chip from '@mui/material/Chip';
import Typography from '@mui/material/Typography';
import type { PDFDocumentLoadingTask, PDFDocumentProxy, RenderTask } from 'pdfjs-dist';
import { useEffect, useLayoutEffect, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';

import { ApiError } from '@/shared/api/http';
import { ErrorState, LoadingState } from '@/shared/ui/States';

import { fetchAttachment } from './attachment-content';
import { openPdf } from './pdf';

interface PageSize {
  width: number;
  height: number;
}

/**
 * 첨부 보기의 PDF — 바이트를 받아(서버는 PDF 를 내려받기 · sandbox 로 주어 브라우저의 PDF 보기를 쓸 수 없다) pdf.js 로 쪽마다 canvas 에 그린다.
 * 쪽은 세로로 쌓고, 보이는 쪽(앞뒤 반 화면까지)만 그리고 멀어지면 비운다(쪽이 많아도 메모리가 늘지 않게). 배율을 바꾸면 그 배율로 다시 그린다
 * (화면 밀도까지 곱해 흐리지 않게). 위에 지금 쪽 "쪽 n / N".
 */
export default function PdfView({ url, zoom }: { url: string; zoom: number }) {
  const { t } = useTranslation('issue');
  const [attempt, setAttempt] = useState(0);
  const key = `${url}#${attempt}`;
  const [result, setResult] = useState<{ key: string; doc?: PDFDocumentProxy; sizes?: PageSize[]; error?: unknown } | null>(null);
  const [root, setRoot] = useState<HTMLDivElement | null>(null);
  const [page, setPage] = useState(1);
  const lastZoom = useRef(zoom);
  const rootRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    let task: PDFDocumentLoadingTask | null = null;
    void (async () => {
      try {
        const { bytes } = await fetchAttachment(url, { signal: controller.signal });
        if (controller.signal.aborted) {
          return;
        }
        task = openPdf(bytes);
        const doc = await task.promise;
        const sizes = await Promise.all(
          Array.from({ length: doc.numPages }, async (_, index) => {
            const viewport = (await doc.getPage(index + 1)).getViewport({ scale: 1 });
            return { width: viewport.width, height: viewport.height };
          }),
        );
        if (!controller.signal.aborted) {
          setResult({ key, doc, sizes });
        }
      } catch (error) {
        if (!controller.signal.aborted) {
          setResult({ key, error });
        }
      }
    })();
    return () => {
      controller.abort();
      void task?.destroy();
    };
  }, [key, url]);

  // 배율을 바꾸면 읽던 자리를 지킨다
  useLayoutEffect(() => {
    const box = rootRef.current;
    if (box && lastZoom.current !== zoom) {
      box.scrollTop = box.scrollTop * (zoom / lastZoom.current);
    }
    lastZoom.current = zoom;
  }, [zoom]);

  const current = result?.key === key ? result : null;
  if (!current) {
    return <LoadingState />;
  }
  if (!current.doc || !current.sizes) {
    return current.error instanceof ApiError ? (
      <ErrorState error={current.error} onRetry={() => setAttempt((n) => n + 1)} />
    ) : (
      <Typography role="alert" sx={{ m: 'auto', p: 3, color: 'text.secondary', textAlign: 'center' }}>
        {t('viewer.pdfFailed')}
      </Typography>
    );
  }
  const { doc, sizes } = current;

  const onScroll = () => {
    if (!root) {
      return;
    }
    const line = root.scrollTop + root.clientHeight / 3;
    let at = 1;
    root.querySelectorAll<HTMLElement>('[data-page]').forEach((box) => {
      if (box.offsetTop <= line) {
        at = Number(box.dataset.page);
      }
    });
    setPage(at);
  };

  return (
    <Box
      ref={(element: HTMLDivElement | null) => {
        rootRef.current = element;
        setRoot(element);
      }}
      onScroll={onScroll}
      tabIndex={0}
      sx={{ position: 'relative', flex: 1, minHeight: 0, overflow: 'auto', outline: 'none', bgcolor: 'background.subtle' }}
    >
      <Box sx={{ position: 'sticky', top: 8, zIndex: 1, display: 'flex', justifyContent: 'center', height: 0, overflow: 'visible' }}>
        <Chip
          size="small"
          label={t('viewer.pdfPage', { page, total: doc.numPages })}
          aria-live="polite"
          sx={{ bgcolor: 'background.paper', boxShadow: 1, fontVariantNumeric: 'tabular-nums' }}
        />
      </Box>
      <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 2, py: 5, px: 2, width: 'max-content', minWidth: '100%', boxSizing: 'border-box' }}>
        {sizes.map((size, index) => (
          <PdfPage key={index} doc={doc} number={index + 1} size={size} zoom={zoom} root={root} />
        ))}
      </Box>
    </Box>
  );
}

function PdfPage({ doc, number, size, zoom, root }: {
  doc: PDFDocumentProxy;
  number: number;
  size: PageSize;
  zoom: number;
  root: HTMLElement | null;
}) {
  const { t } = useTranslation('issue');
  const box = useRef<HTMLDivElement>(null);
  const canvas = useRef<HTMLCanvasElement>(null);
  const [visible, setVisible] = useState(typeof IntersectionObserver === 'undefined');

  useEffect(() => {
    const target = box.current;
    if (!target || !root || typeof IntersectionObserver === 'undefined') {
      return undefined;
    }
    const observer = new IntersectionObserver((entries) => setVisible(entries.some((entry) => entry.isIntersecting)), {
      root,
      rootMargin: '50% 0px',
    });
    observer.observe(target);
    return () => observer.disconnect();
  }, [root]);

  useEffect(() => {
    const target = canvas.current;
    if (!target) {
      return undefined;
    }
    if (!visible) {
      // 멀어진 쪽은 비운다 — 다시 보이면 그린다
      target.width = 0;
      target.height = 0;
      return undefined;
    }
    let cancelled = false;
    let task: RenderTask | null = null;
    void doc.getPage(number).then((pdfPage) => {
      if (cancelled) {
        return;
      }
      const ratio = window.devicePixelRatio || 1;
      const viewport = pdfPage.getViewport({ scale: zoom * ratio });
      target.width = Math.floor(viewport.width);
      target.height = Math.floor(viewport.height);
      task = pdfPage.render({ canvas: target, viewport });
      task.promise.catch(() => undefined);
    }, () => undefined);
    return () => {
      cancelled = true;
      task?.cancel();
    };
  }, [doc, number, zoom, visible]);

  return (
    <Box
      ref={box}
      data-page={number}
      sx={{ flexShrink: 0, width: size.width * zoom, height: size.height * zoom, bgcolor: 'common.white', boxShadow: 2 }}
    >
      <canvas ref={canvas} role="img" aria-label={t('viewer.pdfPageLabel', { page: number })} style={{ display: 'block', width: '100%', height: '100%' }} />
    </Box>
  );
}
