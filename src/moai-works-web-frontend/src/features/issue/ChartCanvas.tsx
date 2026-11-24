import { useColorScheme, useTheme, type Theme } from '@mui/material/styles';
import { BarChart, BarPlot } from '@mui/x-charts/BarChart';
import { ChartsAxis } from '@mui/x-charts/ChartsAxis';
import { ChartsAxisHighlight } from '@mui/x-charts/ChartsAxisHighlight';
import { ChartsDataProvider } from '@mui/x-charts/ChartsDataProvider';
import { ChartsGrid } from '@mui/x-charts/ChartsGrid';
import { ChartsLegend } from '@mui/x-charts/ChartsLegend';
import { ChartsSurface } from '@mui/x-charts/ChartsSurface';
import { ChartsTooltip } from '@mui/x-charts/ChartsTooltip';
import { ChartsWrapper } from '@mui/x-charts/ChartsWrapper';
import { rainbowSurgePalette } from '@mui/x-charts/colorPalettes';
import { Gauge, gaugeClasses } from '@mui/x-charts/Gauge';
import { LineChart, LinePlot, MarkPlot } from '@mui/x-charts/LineChart';
import type { Position } from '@mui/x-charts/models';
import { PieChart } from '@mui/x-charts/PieChart';
import { RadarChart } from '@mui/x-charts/RadarChart';
import { ScatterChart } from '@mui/x-charts/ScatterChart';
import { SparkLineChart } from '@mui/x-charts/SparkLineChart';
import { useMemo } from 'react';
import { useTranslation } from 'react-i18next';

import type { CartesianSeries, CartesianSpec, ChartSpec, GaugeSpec, PieSpec, RadarSpec, ScatterSpec, SparklineSpec } from './chart-spec';

/**
 * 차트 그리기 — MUI X Charts(MIT). 이 파일만 @mui/x-charts 를 불러온다: ChartBlock 이 차트가 있을 때만 따로 받는다(lazy).
 *
 * 종류 → 부품: bar · line · area → BarChart · LineChart(가로 막대 = layout horizontal, 쌓기 = stack, 영역 = area),
 * bar 와 line 을 섞으면 ChartsDataProvider 로 막대 · 선을 한 판에(조합 API), pie · donut → PieChart(donut = innerRadius),
 * scatter → ScatterChart, radar → RadarChart, gauge → Gauge, sparkline → SparkLineChart.
 *
 * - 글은 React 가 글자로 그린다(HTML 해석 없음). 바깥에서 받아 오는 것이 없다.
 * - 너비는 감싼 상자를 따르고(크기를 재며 다시 그린다), 높이는 spec 의 height.
 * - 색: 적은 series 색 > 테마의 주 색 + 라이트 · 다크에 맞춘 차트 색 모음. 밝기는 useColorScheme 으로 읽는다 — 화면 테마는 CSS 변수
 *   테마라 theme.palette 가 밝기를 따라 바뀌지 않는다(다크에서도 라이트 값). SVG 의 fill 에는 CSS 변수를 쓸 수 없어 그 밝기의 값을 넣는다.
 * - 단위(unit)는 툴팁 값 뒤와 값 축 이름에 붙인다.
 */
export default function ChartCanvas({ chart, animate }: { chart: ChartSpec; animate: boolean }) {
  const theme = useTheme();
  const { mode, systemMode } = useColorScheme();
  const scheme = (mode === 'system' ? systemMode : mode) === 'dark' ? 'dark' : 'light';
  const { i18n } = useTranslation();
  const primary = (theme as SchemedTheme).colorSchemes?.[scheme]?.palette.primary.main ?? theme.palette.primary.main;
  const palette = useMemo(() => [primary, ...rainbowSurgePalette(scheme).slice(1)], [primary, scheme]);
  const format = useMemo(() => valueFormat(i18n.language, chart.unit), [i18n.language, chart.unit]);
  const look: Look = { palette, format, primary, skipAnimation: !animate };
  switch (chart.type) {
    case 'bar':
    case 'line':
    case 'area':
      return <Cartesian chart={chart} look={look} />;
    case 'pie':
    case 'donut':
      return <Pie chart={chart} look={look} />;
    case 'scatter':
      return <Scatter chart={chart} look={look} />;
    case 'radar':
      return <Radar chart={chart} look={look} />;
    case 'gauge':
      return <GaugeChart chart={chart} look={look} />;
    case 'sparkline':
      return <Sparkline chart={chart} look={look} />;
  }
}

/** CSS 변수 테마의 밝기별 값 — 이 저장소는 CssThemeVariables 형 확장을 켜지 않아 Theme 형에 없다(create-theme.ts 의 colorSchemes) */
type SchemedTheme = Theme & { colorSchemes?: Partial<Record<'light' | 'dark', { palette: Theme['palette'] }>> };

interface Format {
  /** 수 — 화면 언어의 자리 나눔 */
  number: (value: number) => string;
  /** 수 + 단위 */
  value: (value: number | null | undefined) => string;
}

interface Look {
  palette: string[];
  format: Format;
  primary: string;
  skipAnimation: boolean;
}

/** 범례는 아래 가운데 — 좁은 화면(390px)에서도 그림 폭을 줄이지 않고 줄을 바꾼다 */
const legendPosition: Position = { vertical: 'bottom', horizontal: 'center' };
const legendSlot = { legend: { position: legendPosition, direction: 'horizontal' as const } };

function valueFormat(language: string, unit: string | undefined): Format {
  const numbers = new Intl.NumberFormat(language, { maximumFractionDigits: 2 });
  // 글자로 시작하는 단위(ms · h · kg)는 띄우고, 기호 · 한글 단위(% · 건 · 원)는 붙인다
  const suffix = unit ? `${/^[A-Za-z]/.test(unit) ? ' ' : ''}${unit}` : '';
  return {
    number: (value) => numbers.format(value),
    value: (value) => (value === null || value === undefined ? '' : `${numbers.format(value)}${suffix}`),
  };
}

/** 값 축 이름 — 적은 이름 뒤에 (단위) */
function valueAxisLabel(label: string | undefined, unit: string | undefined): string | undefined {
  const parts = [label, unit ? `(${unit})` : undefined].filter(Boolean);
  return parts.length ? parts.join(' ') : undefined;
}

function colorOf<T extends { color?: string }>(item: T): { color?: string } {
  return item.color ? { color: item.color } : {};
}

function Cartesian({ chart, look }: { chart: CartesianSpec; look: Look }) {
  const mixed = chart.type !== 'area' && chart.series.some((series) => series.plot !== chart.series[0]?.plot);
  const valueLabel = valueAxisLabel(chart.yLabel, chart.unit);
  const formatter = (value: number | null) => look.format.value(value);
  const tickFormatter = (value: number) => look.format.number(value);
  const showMark = chart.labels.length <= 24;
  const common = (series: CartesianSeries, index: number) => ({
    id: `s${index}`,
    label: series.name,
    data: series.data,
    valueFormatter: formatter,
    ...colorOf(series),
  });

  if (mixed) {
    // 막대 + 선 — 가로 막대는 선과 섞을 수 없어 세로로 그린다. 쌓기는 막대끼리 · 선끼리
    const series = chart.series.map((entry, index) =>
      entry.plot === 'bar'
        ? { ...common(entry, index), type: 'bar' as const, ...(chart.stacked ? { stack: 'bar' } : {}) }
        : { ...common(entry, index), type: 'line' as const, showMark, ...(chart.stacked ? { stack: 'line' } : {}) },
    );
    return (
      <ChartsDataProvider
        series={series}
        xAxis={[{ id: 'x', scaleType: 'band', data: chart.labels, label: chart.xLabel }]}
        yAxis={[{ id: 'y', label: valueLabel, width: 'auto', valueFormatter: tickFormatter }]}
        height={chart.height}
        colors={look.palette}
        skipAnimation={look.skipAnimation}
      >
        <ChartsWrapper legendPosition={legendPosition} legendDirection="horizontal">
          <ChartsLegend direction="horizontal" />
          <ChartsSurface>
            <ChartsGrid horizontal />
            <BarPlot />
            <LinePlot />
            <ChartsAxisHighlight x="band" />
            <ChartsAxis />
            <MarkPlot />
          </ChartsSurface>
          <ChartsTooltip />
        </ChartsWrapper>
      </ChartsDataProvider>
    );
  }

  if (chart.type === 'bar') {
    const horizontal = chart.horizontal;
    const labelsAxis = { id: 'labels', scaleType: 'band' as const, data: chart.labels, label: chart.xLabel };
    return (
      <BarChart
        height={chart.height}
        layout={horizontal ? 'horizontal' : 'vertical'}
        series={chart.series.map((entry, index) => ({ ...common(entry, index), ...(chart.stacked ? { stack: 'total' } : {}) }))}
        xAxis={horizontal ? [{ id: 'values', label: valueLabel, valueFormatter: tickFormatter }] : [labelsAxis]}
        yAxis={horizontal ? [{ ...labelsAxis, width: 'auto' }] : [{ id: 'values', label: valueLabel, width: 'auto', valueFormatter: tickFormatter }]}
        grid={horizontal ? { vertical: true } : { horizontal: true }}
        colors={look.palette}
        skipAnimation={look.skipAnimation}
        slotProps={legendSlot}
      />
    );
  }

  return (
    <LineChart
      height={chart.height}
      series={chart.series.map((entry, index) => ({
        ...common(entry, index),
        showMark,
        area: chart.type === 'area',
        ...(chart.stacked ? { stack: 'total' } : {}),
      }))}
      xAxis={[{ id: 'labels', scaleType: 'point', data: chart.labels, label: chart.xLabel }]}
      yAxis={[{ id: 'values', label: valueLabel, width: 'auto', valueFormatter: tickFormatter }]}
      grid={{ horizontal: true }}
      colors={look.palette}
      skipAnimation={look.skipAnimation}
      slotProps={legendSlot}
    />
  );
}

function Pie({ chart, look }: { chart: PieSpec; look: Look }) {
  const total = chart.slices.reduce((sum, slice) => sum + slice.value, 0);
  const donut = chart.type === 'donut';
  return (
    <PieChart
      height={chart.height}
      series={[
        {
          data: chart.slices.map((slice, index) => ({ id: index, value: slice.value, label: slice.label, ...colorOf(slice) })),
          innerRadius: donut ? '55%' : 0,
          paddingAngle: donut ? 1 : 0,
          cornerRadius: donut ? 3 : 0,
          highlightScope: { fade: 'global', highlight: 'item' },
          // 툴팁 — 값(단위)과 비율
          valueFormatter: (item) => `${look.format.value(item.value)} (${look.format.number(total > 0 ? (item.value / total) * 100 : 0)}%)`,
        },
      ]}
      colors={look.palette}
      skipAnimation={look.skipAnimation}
      slotProps={legendSlot}
    />
  );
}

function Scatter({ chart, look }: { chart: ScatterSpec; look: Look }) {
  const tickFormatter = (value: number) => look.format.number(value);
  return (
    <ScatterChart
      height={chart.height}
      series={chart.series.map((entry, index) => ({
        id: `s${index}`,
        label: entry.name,
        data: entry.points.map(([x, y], n) => ({ x, y, id: n })),
        valueFormatter: (point) => (point ? `(${look.format.number(point.x)}, ${look.format.value(point.y)})` : ''),
        ...colorOf(entry),
      }))}
      xAxis={[{ id: 'x', label: chart.xLabel, valueFormatter: tickFormatter }]}
      yAxis={[{ id: 'y', label: valueAxisLabel(chart.yLabel, chart.unit), width: 'auto', valueFormatter: tickFormatter }]}
      grid={{ horizontal: true, vertical: true }}
      colors={look.palette}
      skipAnimation={look.skipAnimation}
      slotProps={legendSlot}
    />
  );
}

function Radar({ chart, look }: { chart: RadarSpec; look: Look }) {
  return (
    <RadarChart
      height={chart.height}
      series={chart.series.map((entry, index) => ({
        id: `s${index}`,
        label: entry.name,
        data: entry.data,
        fillArea: true,
        valueFormatter: (value: number | null) => look.format.value(value),
        ...colorOf(entry),
      }))}
      radar={{ metrics: chart.labels, ...(chart.max !== undefined ? { max: chart.max } : {}) }}
      colors={look.palette}
      skipAnimation={look.skipAnimation}
      slotProps={legendSlot}
    />
  );
}

function GaugeChart({ chart, look }: { chart: GaugeSpec; look: Look }) {
  return (
    <Gauge
      height={chart.height}
      value={chart.value}
      valueMin={chart.min}
      valueMax={chart.max}
      startAngle={-110}
      endAngle={110}
      innerRadius="72%"
      cornerRadius="50%"
      text={() => look.format.value(chart.value)}
      skipAnimation={look.skipAnimation}
      sx={{
        [`& .${gaugeClasses.valueText}`]: { fontSize: 24, fontWeight: 600 },
        [`& .${gaugeClasses.valueText} text`]: { fill: 'currentColor' },
        [`& .${gaugeClasses.valueArc}`]: { fill: look.primary },
        [`& .${gaugeClasses.referenceArc}`]: { fill: (theme) => (theme.vars ?? theme).palette.action.selected },
      }}
    />
  );
}

function Sparkline({ chart, look }: { chart: SparklineSpec; look: Look }) {
  const positions = chart.labels ?? chart.data.map((_, index) => String(index + 1));
  return (
    <SparkLineChart
      height={chart.height}
      data={chart.data}
      plotType={chart.plot}
      xAxis={{ scaleType: chart.plot === 'bar' ? 'band' : 'point', data: positions }}
      valueFormatter={(value) => look.format.value(value)}
      showTooltip
      showHighlight
      color={look.primary}
      skipAnimation={look.skipAnimation}
    />
  );
}
