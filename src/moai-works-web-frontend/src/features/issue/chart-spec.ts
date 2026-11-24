/**
 * 본문의 ```chart 블록 읽기 — JSON 객체 하나를 검사하고 그리기 좋은 꼴(ChartSpec)로 바꾼다. 순수 함수만 있다.
 *
 * 서버(api · MCP)의 검사와 같은 규칙이다 — 규칙은 docs/design/05-issue.md 의 "본문 차트 · 흐름도"에 있다. 고치면 둘 다 고친다.
 * - 모르는 키(그 종류의 표에 없는 키)는 오류가 아니라 무시한다 — 앞으로 키를 더해도 옛 화면이 깨지지 않게.
 * - 선택 키의 값이 null 이면 그 키가 없는 것으로 본다. 필수 키가 null 이면 "없음".
 * - 글자 수는 유니코드 글자(code point)로 센다. 번호(series[1])는 JSON 배열 번호(0부터)다.
 * - 틀리면 맨 처음 찾은 까닭 하나를 돌려준다(종류 코드 + 키 이름 · 번호 · 한도). 사용자가 쓴 글은 까닭에 넣지 않는다.
 */

export const CHART_TYPES = ['bar', 'line', 'area', 'pie', 'donut', 'scatter', 'radar', 'gauge', 'sparkline'] as const;
export type ChartType = (typeof CHART_TYPES)[number];

export const CHART_LIMITS = {
  /** 블록 글자 수 */
  source: 20_000,
  title: 100,
  unit: 20,
  /** labels 의 항목 · 파이 조각 이름 · series 이름 */
  label: 60,
  axisLabel: 40,
  height: { min: 160, max: 600 },
  labels: { min: 1, max: 200 },
  series: { min: 1, max: 12 },
  slices: { min: 1, max: 50 },
  scatterPoints: { min: 1, max: 1000 },
  radarLabels: { min: 3, max: 20 },
  radarSeries: { min: 1, max: 8 },
  sparkline: { min: 2, max: 500 },
  /** 점 개수 합계(막대 · 선 · 영역은 series × labels, scatter 는 series 의 점 합계) */
  points: 2000,
} as const;

/** 높이를 적지 않았을 때 */
export const DEFAULT_CHART_HEIGHT: Record<ChartType, number> = {
  bar: 300,
  line: 300,
  area: 300,
  pie: 300,
  donut: 300,
  scatter: 300,
  radar: 300,
  gauge: 200,
  sparkline: 80,
};

interface ChartCommon {
  title?: string;
  unit?: string;
  /** 그림 높이(px). 너비는 본문 너비 */
  height: number;
}

export interface CartesianSeries {
  name: string;
  /** null = 빈칸 */
  data: (number | null)[];
  /** 막대로 · 선으로. area 는 늘 선(채움) */
  plot: 'bar' | 'line';
  color?: string;
}

/** bar · line · area — x 축(가로 막대면 y 축)이 labels 인 그림 */
export interface CartesianSpec extends ChartCommon {
  type: 'bar' | 'line' | 'area';
  labels: string[];
  series: CartesianSeries[];
  stacked: boolean;
  /** bar 에서만 참일 수 있다 */
  horizontal: boolean;
  /** labels 쪽 축 이름 */
  xLabel?: string;
  /** 값 쪽 축 이름 */
  yLabel?: string;
}

export interface PieSlice {
  label: string;
  value: number;
  color?: string;
}

export interface PieSpec extends ChartCommon {
  type: 'pie' | 'donut';
  slices: PieSlice[];
}

export interface ScatterSeries {
  name: string;
  points: [number, number][];
  color?: string;
}

export interface ScatterSpec extends ChartCommon {
  type: 'scatter';
  series: ScatterSeries[];
  xLabel?: string;
  yLabel?: string;
}

export interface RadarSeries {
  name: string;
  data: number[];
  color?: string;
}

export interface RadarSpec extends ChartCommon {
  type: 'radar';
  labels: string[];
  series: RadarSeries[];
  max?: number;
}

export interface GaugeSpec extends ChartCommon {
  type: 'gauge';
  value: number;
  min: number;
  max: number;
}

export interface SparklineSpec extends ChartCommon {
  type: 'sparkline';
  data: number[];
  plot: 'line' | 'bar';
  labels?: string[];
}

export type ChartSpec = CartesianSpec | PieSpec | ScatterSpec | RadarSpec | GaugeSpec | SparklineSpec;

/**
 * 틀린 까닭의 종류 — 화면 글은 issue 묶음의 `view.chartReason.<code>`. params 는 키 이름(`series[1].data`) · 한도 수만.
 */
export type ChartErrorCode =
  | 'tooLong'
  | 'notJson'
  | 'rootNotObject'
  | 'typeMissing'
  | 'typeUnknown'
  | 'missing'
  | 'notObject'
  | 'notText'
  | 'textTooLong'
  | 'notNumber'
  | 'notBoolean'
  | 'notList'
  | 'count'
  | 'lengthMismatch'
  | 'outOfRange'
  | 'negative'
  | 'badColor'
  | 'badChoice'
  | 'notPoint'
  | 'minNotBelowMax'
  | 'valueOutOfRange'
  | 'tooManyPoints';

export interface ChartError {
  code: ChartErrorCode;
  params?: Record<string, string | number>;
}

export type ChartParseResult = { ok: true; chart: ChartSpec } | { ok: false; error: ChartError };

/** 검사 중 처음 찾은 까닭을 던진다 — parseChartSpec 가 받아 결과로 바꾼다 */
class Invalid extends Error {
  constructor(readonly reason: ChartError) {
    super(reason.code);
  }
}

function fail(code: ChartErrorCode, params?: ChartError['params']): never {
  throw new Invalid(params ? { code, params } : { code });
}

type Json = Record<string, unknown>;

const HEX_COLOR = /^#(?:[0-9a-fA-F]{3}|[0-9a-fA-F]{6})$/;

function isObject(value: unknown): value is Json {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

/** 그 객체가 직접 가진 키의 값만 — constructor · __proto__ 같은 물려받은 이름을 읽지 않는다. null 은 없는 것 */
function own(object: Json, key: string): unknown {
  return Object.hasOwn(object, key) && object[key] !== null ? object[key] : undefined;
}

/** 글자 수 — 유니코드 글자(code point) */
function length(text: string): number {
  return Array.from(text).length;
}

function text(value: unknown, key: string, max: number): string {
  if (typeof value !== 'string') {
    fail('notText', { key });
  }
  if (length(value) > max) {
    fail('textTooLong', { key, max });
  }
  return value;
}

function optionalText(object: Json, key: string, max: number): string | undefined {
  const value = own(object, key);
  return value === undefined ? undefined : text(value, key, max);
}

function requiredText(object: Json, key: string, max: number, path = key): string {
  const value = own(object, key);
  if (value === undefined) {
    fail('missing', { key: path });
  }
  return text(value, path, max);
}

function number(value: unknown, key: string): number {
  // JSON 의 1e999 는 Infinity 로 읽힌다 — 유한한 수만
  if (typeof value !== 'number' || !Number.isFinite(value)) {
    fail('notNumber', { key });
  }
  return value;
}

function optionalNumber(object: Json, key: string): number | undefined {
  const value = own(object, key);
  return value === undefined ? undefined : number(value, key);
}

function optionalBoolean(object: Json, key: string): boolean {
  const value = own(object, key);
  if (value === undefined) {
    return false;
  }
  if (typeof value !== 'boolean') {
    fail('notBoolean', { key });
  }
  return value;
}

function optionalColor(object: Json, key: string, path: string): string | undefined {
  const value = own(object, key);
  if (value === undefined) {
    return undefined;
  }
  if (typeof value !== 'string' || !HEX_COLOR.test(value)) {
    fail('badColor', { key: path });
  }
  return value;
}

/** 필수 배열 — 개수가 [min, max] 안 */
function list(object: Json, key: string, range: { min: number; max: number }, path = key): unknown[] {
  const value = own(object, key);
  if (value === undefined) {
    fail('missing', { key: path });
  }
  if (!Array.isArray(value)) {
    fail('notList', { key: path });
  }
  if (value.length < range.min || value.length > range.max) {
    fail('count', { key: path, min: range.min, max: range.max });
  }
  return value;
}

function item(value: unknown, key: string): Json {
  if (!isObject(value)) {
    fail('notObject', { key });
  }
  return value;
}

function labelList(object: Json, range: { min: number; max: number }): string[] {
  return list(object, 'labels', range).map((label, index) => text(label, `labels[${index}]`, CHART_LIMITS.label));
}

function height(object: Json, type: ChartType): number {
  const value = optionalNumber(object, 'height');
  if (value === undefined) {
    return DEFAULT_CHART_HEIGHT[type];
  }
  const { min, max } = CHART_LIMITS.height;
  if (value < min || value > max) {
    fail('outOfRange', { key: 'height', min, max });
  }
  return value;
}

function cartesian(object: Json, type: 'bar' | 'line' | 'area', common: ChartCommon): CartesianSpec {
  const labels = labelList(object, CHART_LIMITS.labels);
  const rawSeries = list(object, 'series', CHART_LIMITS.series);
  const series = rawSeries.map((raw, index): CartesianSeries => {
    const at = `series[${index}]`;
    const entry = item(raw, at);
    const name = requiredText(entry, 'name', CHART_LIMITS.label, `${at}.name`);
    const rawData = own(entry, 'data');
    if (rawData === undefined) {
      fail('missing', { key: `${at}.data` });
    }
    if (!Array.isArray(rawData)) {
      fail('notList', { key: `${at}.data` });
    }
    if (rawData.length !== labels.length) {
      fail('lengthMismatch', { key: `${at}.data`, other: 'labels' });
    }
    const data = rawData.map((value, point) => (value === null ? null : number(value, `${at}.data[${point}]`)));
    let plot: 'bar' | 'line' = type === 'bar' ? 'bar' : 'line';
    // 섞기는 bar · line 차트에서만 — area 의 series.type 은 모르는 키처럼 둔다
    if (type !== 'area') {
      const kind = own(entry, 'type');
      if (kind !== undefined) {
        if (kind !== 'bar' && kind !== 'line') {
          fail('badChoice', { key: `${at}.type`, choices: 'bar · line' });
        }
        plot = kind;
      }
    }
    const color = optionalColor(entry, 'color', `${at}.color`);
    return { name, data, plot, ...(color ? { color } : {}) };
  });
  if (labels.length * series.length > CHART_LIMITS.points) {
    fail('tooManyPoints', { max: CHART_LIMITS.points });
  }
  const stacked = optionalBoolean(object, 'stacked');
  const horizontal = type === 'bar' ? optionalBoolean(object, 'horizontal') : false;
  const xLabel = optionalText(object, 'xLabel', CHART_LIMITS.axisLabel);
  const yLabel = optionalText(object, 'yLabel', CHART_LIMITS.axisLabel);
  return { ...common, type, labels, series, stacked, horizontal, ...(xLabel !== undefined ? { xLabel } : {}), ...(yLabel !== undefined ? { yLabel } : {}) };
}

function pie(object: Json, type: 'pie' | 'donut', common: ChartCommon): PieSpec {
  const slices = list(object, 'data', CHART_LIMITS.slices).map((raw, index): PieSlice => {
    const at = `data[${index}]`;
    const entry = item(raw, at);
    const label = requiredText(entry, 'label', CHART_LIMITS.label, `${at}.label`);
    const rawValue = own(entry, 'value');
    if (rawValue === undefined) {
      fail('missing', { key: `${at}.value` });
    }
    const value = number(rawValue, `${at}.value`);
    if (value < 0) {
      fail('negative', { key: `${at}.value` });
    }
    const color = optionalColor(entry, 'color', `${at}.color`);
    return { label, value, ...(color ? { color } : {}) };
  });
  return { ...common, type, slices };
}

function scatter(object: Json, common: ChartCommon): ScatterSpec {
  let total = 0;
  const series = list(object, 'series', CHART_LIMITS.series).map((raw, index): ScatterSeries => {
    const at = `series[${index}]`;
    const entry = item(raw, at);
    const name = requiredText(entry, 'name', CHART_LIMITS.label, `${at}.name`);
    const points = list(entry, 'data', CHART_LIMITS.scatterPoints, `${at}.data`).map((point, n): [number, number] => {
      const key = `${at}.data[${n}]`;
      if (!Array.isArray(point) || point.length !== 2) {
        fail('notPoint', { key });
      }
      return [number(point[0], key), number(point[1], key)];
    });
    total += points.length;
    const color = optionalColor(entry, 'color', `${at}.color`);
    return { name, points, ...(color ? { color } : {}) };
  });
  if (total > CHART_LIMITS.points) {
    fail('tooManyPoints', { max: CHART_LIMITS.points });
  }
  const xLabel = optionalText(object, 'xLabel', CHART_LIMITS.axisLabel);
  const yLabel = optionalText(object, 'yLabel', CHART_LIMITS.axisLabel);
  return { ...common, type: 'scatter', series, ...(xLabel !== undefined ? { xLabel } : {}), ...(yLabel !== undefined ? { yLabel } : {}) };
}

function radar(object: Json, common: ChartCommon): RadarSpec {
  const labels = labelList(object, CHART_LIMITS.radarLabels);
  const series = list(object, 'series', CHART_LIMITS.radarSeries).map((raw, index): RadarSeries => {
    const at = `series[${index}]`;
    const entry = item(raw, at);
    const name = requiredText(entry, 'name', CHART_LIMITS.label, `${at}.name`);
    const rawData = own(entry, 'data');
    if (rawData === undefined) {
      fail('missing', { key: `${at}.data` });
    }
    if (!Array.isArray(rawData)) {
      fail('notList', { key: `${at}.data` });
    }
    if (rawData.length !== labels.length) {
      fail('lengthMismatch', { key: `${at}.data`, other: 'labels' });
    }
    // 레이더에는 빈칸(null)이 없다
    const data = rawData.map((value, point) => number(value, `${at}.data[${point}]`));
    const color = optionalColor(entry, 'color', `${at}.color`);
    return { name, data, ...(color ? { color } : {}) };
  });
  const max = optionalNumber(object, 'max');
  return { ...common, type: 'radar', labels, series, ...(max !== undefined ? { max } : {}) };
}

function gauge(object: Json, common: ChartCommon): GaugeSpec {
  const rawValue = own(object, 'value');
  if (rawValue === undefined) {
    fail('missing', { key: 'value' });
  }
  const value = number(rawValue, 'value');
  const min = optionalNumber(object, 'min') ?? 0;
  const max = optionalNumber(object, 'max') ?? 100;
  if (min >= max) {
    fail('minNotBelowMax');
  }
  if (value < min || value > max) {
    fail('valueOutOfRange');
  }
  return { ...common, type: 'gauge', value, min, max };
}

function sparkline(object: Json, common: ChartCommon): SparklineSpec {
  const data = list(object, 'data', CHART_LIMITS.sparkline).map((value, index) => number(value, `data[${index}]`));
  const rawPlot = own(object, 'plot');
  if (rawPlot !== undefined && rawPlot !== 'line' && rawPlot !== 'bar') {
    fail('badChoice', { key: 'plot', choices: 'line · bar' });
  }
  const plot = rawPlot === 'bar' ? 'bar' : 'line';
  let labels: string[] | undefined;
  const rawLabels = own(object, 'labels');
  if (rawLabels !== undefined) {
    if (!Array.isArray(rawLabels)) {
      fail('notList', { key: 'labels' });
    }
    if (rawLabels.length !== data.length) {
      fail('lengthMismatch', { key: 'labels', other: 'data' });
    }
    labels = rawLabels.map((label, index) => text(label, `labels[${index}]`, CHART_LIMITS.label));
  }
  return { ...common, type: 'sparkline', data, plot, ...(labels ? { labels } : {}) };
}

/** ```chart 블록의 글 → 그릴 꼴 또는 틀린 까닭 하나 */
export function parseChartSpec(source: string): ChartParseResult {
  try {
    if (length(source) > CHART_LIMITS.source) {
      fail('tooLong', { max: CHART_LIMITS.source });
    }
    let parsed: unknown;
    try {
      parsed = JSON.parse(source);
    } catch {
      fail('notJson');
    }
    if (!isObject(parsed)) {
      fail('rootNotObject');
    }
    const rawType = own(parsed, 'type');
    if (rawType === undefined) {
      fail('typeMissing');
    }
    if (typeof rawType !== 'string' || !(CHART_TYPES as readonly string[]).includes(rawType)) {
      fail('typeUnknown');
    }
    const type = rawType as ChartType;
    const title = optionalText(parsed, 'title', CHART_LIMITS.title);
    const chartHeight = height(parsed, type);
    const unit = optionalText(parsed, 'unit', CHART_LIMITS.unit);
    const common: ChartCommon = { height: chartHeight, ...(title !== undefined ? { title } : {}), ...(unit !== undefined ? { unit } : {}) };
    switch (type) {
      case 'bar':
      case 'line':
      case 'area':
        return { ok: true, chart: cartesian(parsed, type, common) };
      case 'pie':
      case 'donut':
        return { ok: true, chart: pie(parsed, type, common) };
      case 'scatter':
        return { ok: true, chart: scatter(parsed, common) };
      case 'radar':
        return { ok: true, chart: radar(parsed, common) };
      case 'gauge':
        return { ok: true, chart: gauge(parsed, common) };
      case 'sparkline':
        return { ok: true, chart: sparkline(parsed, common) };
    }
  } catch (error) {
    if (error instanceof Invalid) {
      return { ok: false, error: error.reason };
    }
    throw error;
  }
}
