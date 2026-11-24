import Box from '@mui/material/Box';
import type { SxProps, Theme } from '@mui/material/styles';
import Typography from '@mui/material/Typography';
import type { TFunction } from 'i18next';
import { Component, lazy, Suspense, useMemo, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';

import { importWithReload } from '@/shared/lib/chunk-reload';

import { CHART_LANGUAGE } from './code-blocks';
import { parseChartSpec, type ChartError, type ChartSpec } from './chart-spec';

/** 차트 라이브러리(@mui/x-charts)는 크다 — 차트 블록이 있을 때만 그때 받는다 */
const ChartCanvas = lazy(() => importWithReload(() => import('./ChartCanvas')));

/** 틀린 까닭 → 화면 글 한 줄. 까닭에는 키 이름 · 번호 · 한도만 들어 있다(사용자가 쓴 글은 없다) */
export function chartReasonText(t: TFunction<'issue'>, error: ChartError): string {
  return t(`view.chartReason.${error.code}`, error.params ?? {});
}

/**
 * 본문의 ```chart 코드 블록을 차트로 그린다 — 꼴은 chart-spec.ts(JSON 하나), 그리기는 ChartCanvas(MUI X Charts).
 *
 * - 꼴이 틀리면 mermaid 실패와 같은 모양: 머리(chart · 그릴 수 없는 차트) · 적힌 글 · 까닭 한 줄.
 * - 글은 모두 React 가 글자로 그린다 — 본문의 글이 HTML 로 들어가는 길이 없다.
 */
export function ChartBlock({ code }: { code: string }) {
  const { t } = useTranslation('issue');
  const result = useMemo(() => parseChartSpec(code), [code]);
  if (!result.ok) {
    return <ChartFailure code={code} reason={chartReasonText(t, result.error)} />;
  }
  return <ChartView chart={result.chart} />;
}

/** 그릴 수 없는 차트 — MermaidDiagram 의 실패와 같은 모양(본문의 .code-block · .code-head 모양을 쓴다) */
export function ChartFailure({ code, reason }: { code: string; reason: string }) {
  const { t } = useTranslation('issue');
  return (
    <Box className="code-block diagram-failed">
      <Box className="code-head">
        <span>{CHART_LANGUAGE}</span>
        <Typography component="span" variant="caption" color="error.main">
          {t('view.chartFailed')}
        </Typography>
      </Box>
      <pre>
        <code>{code}</code>
      </pre>
      <Typography variant="caption" color="text.secondary" sx={{ display: 'block', px: 2, pb: 1.5 }}>
        {reason}
      </Typography>
    </Box>
  );
}

/**
 * 검사를 지난 차트 — 제목 · 그림. 접근성 이름은 제목(없으면 "차트").
 * animate = false 면 움직임 없이 그린다(편집기 미리보기 — 글자를 칠 때마다 새로 그린다).
 */
export function ChartView({ chart, animate = true }: { chart: ChartSpec; animate?: boolean }) {
  const { t } = useTranslation('issue');
  return (
    <Box className="chart" role="img" aria-label={chart.title || t('view.chart')} sx={chartSx}>
      {chart.title && (
        <Typography component="div" variant="subtitle2" className="chart-title">
          {chart.title}
        </Typography>
      )}
      <ChartBoundary
        chart={chart}
        fallback={
          <Typography variant="caption" color="error.main">
            {t('view.chartDrawFailed')}
          </Typography>
        }
      >
        <Suspense
          fallback={
            <Box className="chart-loading" sx={{ height: chart.height }}>
              <Typography variant="body2" color="text.secondary">
                {t('view.chartLoading')}
              </Typography>
            </Box>
          }
        >
          <ChartCanvas chart={chart} animate={animate} />
        </Suspense>
      </ChartBoundary>
    </Box>
  );
}

interface BoundaryProps {
  chart: ChartSpec;
  fallback: ReactNode;
  children: ReactNode;
}

/** 그리다 예외가 나도 본문 전체가 멈추지 않게 — 차트가 바뀌면(글을 고치면) 다시 그려 본다 */
class ChartBoundary extends Component<BoundaryProps, { chart: ChartSpec; failed: boolean }> {
  state = { chart: this.props.chart, failed: false };

  static getDerivedStateFromProps(props: BoundaryProps, state: { chart: ChartSpec; failed: boolean }) {
    return props.chart !== state.chart ? { chart: props.chart, failed: false } : null;
  }

  static getDerivedStateFromError() {
    return { failed: true };
  }

  render() {
    return this.state.failed ? this.props.fallback : this.props.children;
  }
}

/**
 * 차트 상자 — 다이어그램(.diagram)과 같은 테두리. 범례(MUI 의 ul · li)에 본문의 목록 간격이 들지 않게 되돌린다
 * (본문 규칙 `.본문 ul` 보다 앞서도록 클래스 둘로 쓴다).
 */
const chartSx: SxProps<Theme> = {
  my: 2,
  px: { xs: 1, sm: 2 },
  py: 1.5,
  border: 1,
  borderColor: 'divider',
  borderRadius: 1.5,
  minWidth: 0,
  '& .chart-title': { textAlign: 'center', mb: 0.5, overflowWrap: 'anywhere' },
  '& .chart-loading': { display: 'flex', alignItems: 'center', justifyContent: 'center' },
  '& .MuiChartsLegend-root': { pl: 0, mx: 1, my: 1 },
  '& .MuiChartsLegend-root li': { my: 0 },
};
