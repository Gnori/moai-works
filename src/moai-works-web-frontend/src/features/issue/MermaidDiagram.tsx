import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';
import { useColorScheme } from '@mui/material/styles';
import { useEffect, useId, useRef, useState } from 'react';
import { useTranslation } from 'react-i18next';

interface Drawn {
  /** 마지막으로 그리는 데 성공한 그림 */
  svg?: string;
  /** 지금 글로는 그릴 수 없는 이유 */
  error?: string;
}

/**
 * 본문의 ```mermaid 코드 블록을 그림으로 그린다 (흐름도 · 순서도 · 상태 · 관계 …).
 *
 * - mermaid 는 크다 — 다이어그램이 있는 글에서만 그때 받아 온다(동적 import).
 * - 본문은 남이 쓴 글이다 — securityLevel 'strict' 로 그린다(글자 안의 HTML · 누르면 실행되는 것을 막는다).
 * - 문법이 틀리면 그림 대신 적힌 글과 이유를 보인다. 편집 중에는 마지막으로 그려진 그림을 남겨 깜빡이지 않게 한다.
 * - 밝기(라이트 · 다크)가 바뀌면 다시 그린다.
 */
export function MermaidDiagram({ code }: { code: string }) {
  const { t } = useTranslation('issue');
  const { mode, systemMode } = useColorScheme();
  const dark = (mode === 'system' ? systemMode : mode) === 'dark';
  const baseId = useId().replace(/[^a-zA-Z0-9]/g, '');
  const round = useRef(0);
  const [drawn, setDrawn] = useState<Drawn>({});

  useEffect(() => {
    let cancelled = false;
    // 미리보기에서는 글자를 칠 때마다 바뀐다 — 잠깐 기다렸다가 그린다
    const timer = window.setTimeout(() => {
      void (async () => {
        try {
          const mermaid = (await import('mermaid')).default;
          mermaid.initialize({
            startOnLoad: false,
            securityLevel: 'strict',
            theme: dark ? 'dark' : 'default',
            fontFamily: window.getComputedStyle(document.body).fontFamily,
          });
          const valid = await mermaid.parse(code, { suppressErrors: false });
          if (!valid) {
            throw new Error(t('view.diagramInvalid'));
          }
          round.current += 1;
          const { svg } = await mermaid.render(`diagram-${baseId}-${round.current}`, code);
          if (!cancelled) {
            setDrawn({ svg });
          }
        } catch (e) {
          if (!cancelled) {
            const reason = e instanceof Error ? e.message.split('\n')[0] : String(e);
            setDrawn((before) => ({ svg: before.svg, error: reason || t('view.diagramInvalid') }));
          }
        }
      })();
    }, 250);
    return () => {
      cancelled = true;
      window.clearTimeout(timer);
    };
  }, [code, dark, baseId, t]);

  if (drawn.error && !drawn.svg) {
    return (
      <Box className="code-block diagram-failed">
        <Box className="code-head">
          <span>mermaid</span>
          <Typography component="span" variant="caption" color="error.main">
            {t('view.diagramFailed')}
          </Typography>
        </Box>
        <pre>
          <code>{code}</code>
        </pre>
        <Typography variant="caption" color="text.secondary" sx={{ display: 'block', px: 2, pb: 1.5 }}>
          {drawn.error}
        </Typography>
      </Box>
    );
  }
  return (
    <Box className="diagram" role="img" aria-label={t('view.diagram')} aria-busy={!drawn.svg}>
      {drawn.svg ? (
        // mermaid 가 strict 로 만든 SVG 다(안에서 DOMPurify 로 거른다) — 본문의 글이 그대로 HTML 로 들어가지 않는다
        <Box className="diagram-canvas" dangerouslySetInnerHTML={{ __html: drawn.svg }} />
      ) : (
        <Typography variant="body2" color="text.secondary">
          {t('view.diagramLoading')}
        </Typography>
      )}
      {drawn.error && (
        <Typography variant="caption" color="error.main" sx={{ display: 'block', mt: 1 }}>
          {t('view.diagramStale')}
        </Typography>
      )}
    </Box>
  );
}
