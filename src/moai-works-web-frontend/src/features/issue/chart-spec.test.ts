import { CHART_LIMITS, parseChartSpec, type ChartError, type ChartSpec } from './chart-spec';

/** 객체 → ```chart 블록의 글 */
const block = (spec: unknown) => JSON.stringify(spec);

function ok(spec: unknown): ChartSpec {
  const result = parseChartSpec(typeof spec === 'string' ? spec : block(spec));
  if (!result.ok) {
    throw new Error(`틀렸다고 함: ${JSON.stringify(result.error)}`);
  }
  return result.chart;
}

function error(spec: unknown): ChartError {
  const result = parseChartSpec(typeof spec === 'string' ? spec : block(spec));
  if (result.ok) {
    throw new Error('맞다고 함');
  }
  return result.error;
}

const range = (n: number) => Array.from({ length: n }, (_, i) => i);
const names = (n: number) => range(n).map((i) => `항목${i}`);
const bar = { type: 'bar', labels: ['월', '화', '수'], series: [{ name: '완료', data: [3, 5, 2] }] };

describe('```chart 블록 — 공통', () => {
  it('JSON 이 아니면 · 객체가 아니면 · type 이 없거나 모르면', () => {
    expect(error('{ type: bar }')).toEqual({ code: 'notJson' });
    expect(error('{"type":"bar",}')).toEqual({ code: 'notJson' });
    expect(error('')).toEqual({ code: 'notJson' });
    expect(error('[1,2]')).toEqual({ code: 'rootNotObject' });
    expect(error('null')).toEqual({ code: 'rootNotObject' });
    expect(error('3')).toEqual({ code: 'rootNotObject' });
    expect(error({ labels: [] })).toEqual({ code: 'typeMissing' });
    expect(error({ type: null })).toEqual({ code: 'typeMissing' });
    expect(error({ type: 'heatmap' })).toEqual({ code: 'typeUnknown' });
    expect(error({ type: 'Bar' })).toEqual({ code: 'typeUnknown' });
    expect(error({ type: 3 })).toEqual({ code: 'typeUnknown' });
  });

  it('까닭에는 사용자가 쓴 글이 들어가지 않는다 — 키 이름 · 번호 · 한도만', () => {
    const evil = '<img src=x onerror=alert(1)>';
    const reasons = [
      error({ type: evil }),
      error({ ...bar, title: { evil } }),
      error({ ...bar, series: [{ name: evil, data: [1, 2] }] }),
      error({ ...bar, series: [{ name: 'a', data: [1, 2, 3], color: evil }] }),
      error({ ...bar, series: [{ name: 'a', data: [1, 2, 3], type: evil }] }),
      error({ type: 'sparkline', data: [1, 2], plot: evil }),
    ];
    for (const reason of reasons) {
      expect(JSON.stringify(reason)).not.toContain('img');
    }
  });

  it('블록은 20,000자까지 — 글자(code point)로 센다', () => {
    const padded = (size: number) => {
      const base = block({ ...bar, title: '' });
      return base.replace('"title":""', `"title":""${' '.repeat(size - base.length)}`);
    };
    expect(padded(CHART_LIMITS.source).length).toBe(CHART_LIMITS.source);
    expect(ok(padded(CHART_LIMITS.source)).type).toBe('bar');
    expect(error(padded(CHART_LIMITS.source + 1))).toEqual({ code: 'tooLong', params: { max: 20000 } });
    // 이모지는 한 글자(UTF-16 으로는 둘)
    expect(ok({ ...bar, title: '🚀'.repeat(100) }).title).toHaveLength(200);
    expect(error({ ...bar, title: '🚀'.repeat(101) })).toEqual({ code: 'textTooLong', params: { key: 'title', max: 100 } });
  });

  it('title · unit · height — 꼴과 한도, 종류별 기본 높이', () => {
    expect(ok(bar)).toMatchObject({ height: 300 });
    expect(ok(bar)).not.toHaveProperty('title');
    expect(ok({ ...bar, title: '주간', unit: '건', height: 160 })).toMatchObject({ title: '주간', unit: '건', height: 160 });
    expect(ok({ ...bar, height: 600 }).height).toBe(600);
    expect(error({ ...bar, height: 159 })).toEqual({ code: 'outOfRange', params: { key: 'height', min: 160, max: 600 } });
    expect(error({ ...bar, height: 601 })).toEqual({ code: 'outOfRange', params: { key: 'height', min: 160, max: 600 } });
    expect(error({ ...bar, height: '300' })).toEqual({ code: 'notNumber', params: { key: 'height' } });
    expect(error({ ...bar, title: 3 })).toEqual({ code: 'notText', params: { key: 'title' } });
    expect(error({ ...bar, unit: 'x'.repeat(21) })).toEqual({ code: 'textTooLong', params: { key: 'unit', max: 20 } });
    expect(ok({ type: 'gauge', value: 1 }).height).toBe(200);
    expect(ok({ type: 'sparkline', data: [1, 2] }).height).toBe(80);
  });

  it('선택 키의 null 은 없는 것으로 본다', () => {
    expect(ok({ ...bar, title: null, unit: null, height: null, stacked: null })).toMatchObject({ height: 300, stacked: false });
  });

  it('모르는 키는 무시한다 — 다른 종류의 키도, 물려받은 이름도', () => {
    const chart = ok({ ...bar, theme: 'dark', image: 'https://evil.example/x.png', max: 'x', data: 'x', constructor: 1 });
    expect(chart).toEqual({ type: 'bar', height: 300, labels: ['월', '화', '수'], series: [{ name: '완료', data: [3, 5, 2], plot: 'bar' }], stacked: false, horizontal: false });
    // series 안의 모르는 키도
    expect(ok({ ...bar, series: [{ name: '완료', data: [3, 5, 2], dash: [4, 2], label: 3 }] })).toEqual(ok(bar));
    // 파이에 막대의 키가 와도 무시
    expect(ok({ type: 'pie', data: [{ label: 'a', value: 1 }], labels: 3, series: 'x', stacked: 'x', xLabel: 3 }).type).toBe('pie');
    // __proto__ 를 키로 써도 type 을 물려받지 않는다
    expect(error('{"__proto__":{"type":"bar"}}')).toEqual({ code: 'typeMissing' });
  });

  it('JSON 의 1e999(Infinity)는 수가 아니다', () => {
    expect(error('{"type":"gauge","value":1e999}')).toEqual({ code: 'notNumber', params: { key: 'value' } });
  });
});

describe('bar · line · area', () => {
  it('막대 — 기본은 세로 막대 · 쌓지 않음', () => {
    expect(ok({ ...bar, xLabel: '요일', yLabel: '처리' })).toEqual({
      type: 'bar',
      height: 300,
      labels: ['월', '화', '수'],
      series: [{ name: '완료', data: [3, 5, 2], plot: 'bar' }],
      stacked: false,
      horizontal: false,
      xLabel: '요일',
      yLabel: '처리',
    });
    expect(ok({ ...bar, stacked: true, horizontal: true })).toMatchObject({ stacked: true, horizontal: true });
  });

  it('섞기 — bar · line 차트에서 series 마다 type, area 에서는 무시', () => {
    const mixed = ok({ ...bar, series: [{ name: '완료', data: [3, 5, 2] }, { name: '새로', data: [1, 2, 3], type: 'line' }] });
    expect(mixed.type === 'bar' && mixed.series.map((s) => s.plot)).toEqual(['bar', 'line']);
    const line = ok({ ...bar, type: 'line', series: [{ name: 'a', data: [1, 2, 3], type: 'bar' }, { name: 'b', data: [1, 2, 3] }] });
    expect(line.type === 'line' && line.series.map((s) => s.plot)).toEqual(['bar', 'line']);
    const area = ok({ ...bar, type: 'area', series: [{ name: 'a', data: [1, 2, 3], type: 'bar' }] });
    expect(area.type === 'area' && area.series[0]?.plot).toBe('line');
    expect(ok({ ...bar, type: 'area', series: [{ name: 'a', data: [1, 2, 3], type: 'pie' }] }).type).toBe('area');
    expect(error({ ...bar, series: [{ name: 'a', data: [1, 2, 3], type: 'area' }] })).toEqual({
      code: 'badChoice',
      params: { key: 'series[0].type', choices: 'bar · line' },
    });
  });

  it('horizontal 은 bar 에서만 — line 에서는 모르는 키', () => {
    expect(ok({ ...bar, type: 'line', horizontal: true })).toMatchObject({ horizontal: false });
    expect(ok({ ...bar, type: 'line', horizontal: 'yes' })).toMatchObject({ horizontal: false });
    expect(error({ ...bar, horizontal: 'yes' })).toEqual({ code: 'notBoolean', params: { key: 'horizontal' } });
    expect(error({ ...bar, stacked: 1 })).toEqual({ code: 'notBoolean', params: { key: 'stacked' } });
  });

  it('null 은 빈칸', () => {
    const chart = ok({ ...bar, type: 'line', series: [{ name: 'a', data: [1, null, 3] }] });
    expect(chart.type === 'line' && chart.series[0]?.data).toEqual([1, null, 3]);
  });

  it('labels — 없음 · 배열 아님 · 개수 · 글자', () => {
    expect(error({ type: 'bar', series: bar.series })).toEqual({ code: 'missing', params: { key: 'labels' } });
    expect(error({ ...bar, labels: '월,화' })).toEqual({ code: 'notList', params: { key: 'labels' } });
    expect(error({ ...bar, labels: [] })).toEqual({ code: 'count', params: { key: 'labels', min: 1, max: 200 } });
    expect(error({ ...bar, labels: names(201) })).toEqual({ code: 'count', params: { key: 'labels', min: 1, max: 200 } });
    expect(error({ ...bar, labels: ['월', 2024, '수'] })).toEqual({ code: 'notText', params: { key: 'labels[1]' } });
    expect(error({ ...bar, labels: ['월', 'x'.repeat(61), '수'] })).toEqual({ code: 'textTooLong', params: { key: 'labels[1]', max: 60 } });
  });

  it('series — 없음 · 개수 · 객체 · 이름 · data 길이 · 수 · 색', () => {
    expect(error({ type: 'bar', labels: ['a'] })).toEqual({ code: 'missing', params: { key: 'series' } });
    expect(error({ ...bar, series: [] })).toEqual({ code: 'count', params: { key: 'series', min: 1, max: 12 } });
    expect(error({ ...bar, series: range(13).map((i) => ({ name: `s${i}`, data: [1, 2, 3] })) })).toEqual({
      code: 'count',
      params: { key: 'series', min: 1, max: 12 },
    });
    expect(error({ ...bar, series: [[1, 2, 3]] })).toEqual({ code: 'notObject', params: { key: 'series[0]' } });
    expect(error({ ...bar, series: [{ data: [1, 2, 3] }] })).toEqual({ code: 'missing', params: { key: 'series[0].name' } });
    expect(error({ ...bar, series: [{ name: 'x'.repeat(61), data: [1, 2, 3] }] })).toEqual({
      code: 'textTooLong',
      params: { key: 'series[0].name', max: 60 },
    });
    expect(error({ ...bar, series: [{ name: 'a' }] })).toEqual({ code: 'missing', params: { key: 'series[0].data' } });
    expect(error({ ...bar, series: [{ name: 'a', data: 3 }] })).toEqual({ code: 'notList', params: { key: 'series[0].data' } });
    expect(error({ ...bar, series: [{ name: 'a', data: [1, 2, 3] }, { name: 'b', data: [1, 2] }] })).toEqual({
      code: 'lengthMismatch',
      params: { key: 'series[1].data', other: 'labels' },
    });
    expect(error({ ...bar, series: [{ name: 'a', data: [1, '2', 3] }] })).toEqual({ code: 'notNumber', params: { key: 'series[0].data[1]' } });
  });

  it('점 개수 합계 2,000 까지(series × labels)', () => {
    const series = (count: number, length: number) => range(count).map((i) => ({ name: `s${i}`, data: range(length) }));
    expect(ok({ type: 'line', labels: names(200), series: series(10, 200) }).type).toBe('line');
    expect(error({ type: 'line', labels: names(200), series: series(11, 200) })).toEqual({ code: 'tooManyPoints', params: { max: 2000 } });
  });

  it('xLabel · yLabel 은 40자까지', () => {
    expect(error({ ...bar, xLabel: 'x'.repeat(41) })).toEqual({ code: 'textTooLong', params: { key: 'xLabel', max: 40 } });
    expect(error({ ...bar, yLabel: 3 })).toEqual({ code: 'notText', params: { key: 'yLabel' } });
  });
});

describe('색 — hex 만', () => {
  it('#rgb · #rrggbb 만 받는다', () => {
    // 화면 코드에는 색 값을 바로 적지 않는다(lint) — 시험 자료의 색은 hex() 로 만든다
    const hex = (digits: string) => `#${digits}`;
    const colored = (color: unknown) => ({ ...bar, series: [{ name: 'a', data: [1, 2, 3], color }] });
    const first = (spec: ChartSpec) => (spec.type === 'bar' ? spec.series[0]?.color : undefined);
    expect(first(ok(colored(hex('1a2B3c'))))).toBe(hex('1a2B3c'));
    expect(first(ok(colored(hex('abc'))))).toBe(hex('abc'));
    expect(first(ok(colored(null)))).toBeUndefined();
    for (const bad of ['red', 'url(#x)', hex('abcd'), hex('12345678'), 'rgb(0,0,0)', '#ggg', ` ${hex('abc')}`, `${hex('abc')};`, 'var(--x)', 3]) {
      expect(error(colored(bad))).toEqual({ code: 'badColor', params: { key: 'series[0].color' } });
    }
    expect(error({ type: 'pie', data: [{ label: 'a', value: 1, color: 'blue' }] })).toEqual({ code: 'badColor', params: { key: 'data[0].color' } });
  });
});

describe('pie · donut', () => {
  it('조각 — label · value(0 이상) · color', () => {
    expect(ok({ type: 'donut', title: '유형별', data: [{ label: '버그', value: 12 }, { label: '새기능', value: 0 }] })).toEqual({
      type: 'donut',
      title: '유형별',
      height: 300,
      slices: [
        { label: '버그', value: 12 },
        { label: '새기능', value: 0 },
      ],
    });
    expect(error({ type: 'pie' })).toEqual({ code: 'missing', params: { key: 'data' } });
    expect(error({ type: 'pie', data: [] })).toEqual({ code: 'count', params: { key: 'data', min: 1, max: 50 } });
    expect(error({ type: 'pie', data: range(51).map((i) => ({ label: `${i}`, value: 1 })) })).toEqual({
      code: 'count',
      params: { key: 'data', min: 1, max: 50 },
    });
    expect(error({ type: 'pie', data: [3] })).toEqual({ code: 'notObject', params: { key: 'data[0]' } });
    expect(error({ type: 'pie', data: [{ value: 3 }] })).toEqual({ code: 'missing', params: { key: 'data[0].label' } });
    expect(error({ type: 'pie', data: [{ label: 'a' }] })).toEqual({ code: 'missing', params: { key: 'data[0].value' } });
    expect(error({ type: 'pie', data: [{ label: 'a', value: -1 }] })).toEqual({ code: 'negative', params: { key: 'data[0].value' } });
    expect(error({ type: 'pie', data: [{ label: 'a', value: '1' }] })).toEqual({ code: 'notNumber', params: { key: 'data[0].value' } });
  });
});

describe('scatter', () => {
  it('series 마다 [x, y] 점', () => {
    expect(ok({ type: 'scatter', series: [{ name: '일감', data: [[1, 2], [3.5, -4]] }], xLabel: '공수', yLabel: '소요' })).toEqual({
      type: 'scatter',
      height: 300,
      series: [{ name: '일감', points: [[1, 2], [3.5, -4]] }],
      xLabel: '공수',
      yLabel: '소요',
    });
    expect(error({ type: 'scatter', series: [{ name: 'a', data: [] }] })).toEqual({ code: 'count', params: { key: 'series[0].data', min: 1, max: 1000 } });
    expect(error({ type: 'scatter', series: [{ name: 'a', data: [[1, 2, 3]] }] })).toEqual({ code: 'notPoint', params: { key: 'series[0].data[0]' } });
    expect(error({ type: 'scatter', series: [{ name: 'a', data: [{ x: 1, y: 2 }] }] })).toEqual({ code: 'notPoint', params: { key: 'series[0].data[0]' } });
    expect(error({ type: 'scatter', series: [{ name: 'a', data: [[1, null]] }] })).toEqual({ code: 'notNumber', params: { key: 'series[0].data[0]' } });
  });

  it('점은 series 마다 1,000개, 모두 합쳐 2,000개까지', () => {
    const points = (n: number) => range(n).map((i) => [i, i]);
    expect(error({ type: 'scatter', series: [{ name: 'a', data: points(1001) }] })).toEqual({
      code: 'count',
      params: { key: 'series[0].data', min: 1, max: 1000 },
    });
    expect(ok({ type: 'scatter', series: [{ name: 'a', data: points(1000) }, { name: 'b', data: points(1000) }] }).type).toBe('scatter');
    expect(error({ type: 'scatter', series: [{ name: 'a', data: points(1000) }, { name: 'b', data: points(1000) }, { name: 'c', data: points(1) }] })).toEqual({
      code: 'tooManyPoints',
      params: { max: 2000 },
    });
  });
});

describe('radar', () => {
  const radar = { type: 'radar', labels: ['속도', '품질', '비용'], series: [{ name: '이번 달', data: [3, 4, 5] }] };

  it('labels 3~20 · series 1~8 · 빈칸 없음 · max', () => {
    expect(ok({ ...radar, max: 10 })).toEqual({ ...radar, height: 300, series: [{ name: '이번 달', data: [3, 4, 5] }], max: 10 });
    expect(error({ ...radar, labels: ['a', 'b'], series: [{ name: 'a', data: [1, 2] }] })).toEqual({
      code: 'count',
      params: { key: 'labels', min: 3, max: 20 },
    });
    expect(error({ ...radar, labels: names(21) })).toEqual({ code: 'count', params: { key: 'labels', min: 3, max: 20 } });
    expect(error({ ...radar, series: range(9).map((i) => ({ name: `s${i}`, data: [1, 2, 3] })) })).toEqual({
      code: 'count',
      params: { key: 'series', min: 1, max: 8 },
    });
    expect(error({ ...radar, series: [{ name: 'a', data: [1, null, 3] }] })).toEqual({ code: 'notNumber', params: { key: 'series[0].data[1]' } });
    expect(error({ ...radar, series: [{ name: 'a', data: [1, 2] }] })).toEqual({ code: 'lengthMismatch', params: { key: 'series[0].data', other: 'labels' } });
    expect(error({ ...radar, max: 'ten' })).toEqual({ code: 'notNumber', params: { key: 'max' } });
  });
});

describe('gauge', () => {
  it('value 필수 · min < max · min ≤ value ≤ max (기본 0 · 100)', () => {
    expect(ok({ type: 'gauge', title: '진행', value: 63, max: 100, unit: '%' })).toEqual({ type: 'gauge', title: '진행', unit: '%', height: 200, value: 63, min: 0, max: 100 });
    expect(ok({ type: 'gauge', value: -5, min: -10, max: 0 })).toMatchObject({ value: -5, min: -10, max: 0 });
    expect(ok({ type: 'gauge', value: 100 })).toMatchObject({ value: 100 });
    expect(error({ type: 'gauge' })).toEqual({ code: 'missing', params: { key: 'value' } });
    expect(error({ type: 'gauge', value: '63' })).toEqual({ code: 'notNumber', params: { key: 'value' } });
    expect(error({ type: 'gauge', value: 5, min: 10, max: 10 })).toEqual({ code: 'minNotBelowMax' });
    expect(error({ type: 'gauge', value: 101 })).toEqual({ code: 'valueOutOfRange' });
    expect(error({ type: 'gauge', value: -1 })).toEqual({ code: 'valueOutOfRange' });
  });
});

describe('sparkline', () => {
  it('수 2~500개 · plot · labels 는 data 와 같은 길이', () => {
    expect(ok({ type: 'sparkline', data: [1, 4, 2] })).toEqual({ type: 'sparkline', height: 80, data: [1, 4, 2], plot: 'line' });
    expect(ok({ type: 'sparkline', data: [1, 4], plot: 'bar', labels: ['a', 'b'], height: 160 })).toEqual({
      type: 'sparkline',
      height: 160,
      data: [1, 4],
      plot: 'bar',
      labels: ['a', 'b'],
    });
    expect(error({ type: 'sparkline', data: [1] })).toEqual({ code: 'count', params: { key: 'data', min: 2, max: 500 } });
    expect(error({ type: 'sparkline', data: range(501) })).toEqual({ code: 'count', params: { key: 'data', min: 2, max: 500 } });
    expect(error({ type: 'sparkline', data: [1, null] })).toEqual({ code: 'notNumber', params: { key: 'data[1]' } });
    expect(error({ type: 'sparkline', data: [1, 2], plot: 'area' })).toEqual({ code: 'badChoice', params: { key: 'plot', choices: 'line · bar' } });
    expect(error({ type: 'sparkline', data: [1, 2], labels: ['a'] })).toEqual({ code: 'lengthMismatch', params: { key: 'labels', other: 'data' } });
    expect(error({ type: 'sparkline', data: [1, 2], labels: 'a' })).toEqual({ code: 'notList', params: { key: 'labels' } });
    // sparkline 에 높이를 적으면 다른 종류와 같은 한도(기본 80 은 한도 밖이지만 적은 값은 160 부터)
    expect(error({ type: 'sparkline', data: [1, 2], height: 80 })).toEqual({ code: 'outOfRange', params: { key: 'height', min: 160, max: 600 } });
  });
});

describe('설계 문서의 예', () => {
  it('세 예가 모두 그려진다', () => {
    expect(
      ok(
        '{"type":"bar","title":"주간 처리 일감","labels":["월","화","수","목","금"],"series":[{"name":"완료","data":[3,5,2,6,4]},{"name":"새로 등록","data":[4,2,3,3,5],"type":"line"}],"unit":"건"}',
      ).type,
    ).toBe('bar');
    expect(ok('{"type":"donut","title":"유형별","data":[{"label":"버그","value":12},{"label":"새기능","value":20},{"label":"테스트","value":7}]}').type).toBe('donut');
    expect(ok('{"type":"gauge","title":"수용 기준 진행","value":63,"max":100,"unit":"%"}').type).toBe('gauge');
  });
});
