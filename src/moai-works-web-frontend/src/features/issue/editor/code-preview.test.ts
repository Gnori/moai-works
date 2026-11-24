import DOMPurify from 'dompurify';
import { afterEach, describe, expect, it, vi } from 'vitest';

import type { ChartSpec } from '../chart-spec';

import { ChartPreviews, renderCodePreview, type ApplyPreview, type CodePreviewOptions, type LiveChart } from './code-preview';

/**
 * 편집기 코드 블록 미리보기 — 진짜 편집기(Milkdown)는 jsdom 에서 돌지 않아(test-setup 의 vi.mock) 미리보기 함수와 자리 지켜보기만 따로 시험한다.
 * Crepe 의 미리보기 칸이 하는 일(받은 값을 DOMPurify 로 걸러 innerHTML 로 넣기)은 place() 가 흉내 낸다.
 */

const chart = '{"type":"bar","labels":["월"],"series":[{"name":"완료","data":[1]}]}';

function setup() {
  const changes: LiveChart[][] = [];
  const charts = new ChartPreviews((list) => changes.push(list));
  const draw = vi.fn<CodePreviewOptions['drawMermaid']>();
  const options: CodePreviewOptions = {
    dark: () => true,
    text: () => ({
      diagramFailed: '그릴 수 없는 다이어그램',
      diagramInvalid: '문법이 틀림',
      chartFailed: '그릴 수 없는 차트',
      reason: (error) => `까닭:${error.code}`,
    }),
    charts,
    drawMermaid: draw,
  };
  // 편집기 뿌리 > 코드 블록 > 미리보기 칸
  const root = document.createElement('div');
  const block = document.createElement('div');
  block.className = 'milkdown-code-block';
  const preview = document.createElement('div');
  preview.className = 'preview';
  block.append(preview);
  root.append(block);
  document.body.append(root);
  /** Crepe 의 preview-panel 과 같이 — 걸러서 글로 넣는다 */
  const place = (value: string | HTMLElement | null | undefined) => {
    preview.innerHTML = value ? DOMPurify.sanitize(value) : '';
  };
  return { changes, charts, draw, options, root, block, preview, place };
}

/** MutationObserver 알림이 지나가도록 */
const settle = () => new Promise((resolve) => setTimeout(resolve, 0));

afterEach(() => {
  document.body.innerHTML = '';
});

describe('renderCodePreview', () => {
  it('mermaid · chart 가 아니거나 빈 글이면 미리보기 없음(null)', () => {
    const { options, draw } = setup();
    const apply = vi.fn<ApplyPreview>();
    expect(renderCodePreview('js', 'const a = 1', apply, options)).toBeNull();
    expect(renderCodePreview('', chart, apply, options)).toBeNull();
    expect(renderCodePreview('chart', '  \n', apply, options)).toBeNull();
    expect(renderCodePreview('mermaid', '', apply, options)).toBeNull();
    expect(draw).not.toHaveBeenCalled();
    expect(apply).not.toHaveBeenCalled();
  });

  it('mermaid — 보기 화면과 같은 함수로 그려(지금 다크로) SVG 글을 나중에 넣는다. 대소문자는 가리지 않는다', async () => {
    const { options, draw } = setup();
    draw.mockResolvedValue({ svg: '<svg><text>A</text></svg>' });
    const apply = vi.fn<ApplyPreview>();
    expect(renderCodePreview('Mermaid', 'flowchart TD\nA-->B', apply, options)).toBeUndefined();
    expect(draw).toHaveBeenCalledWith('flowchart TD\nA-->B', true, '문법이 틀림');
    await settle();
    expect(apply).toHaveBeenCalledWith('<svg><text>A</text></svg>');
  });

  it('mermaid 를 그릴 수 없으면 까닭을 요소의 글로만 넣는다', async () => {
    const { options, draw, place, preview } = setup();
    draw.mockResolvedValue({ error: 'Parse error <img src=x onerror=alert(1)>' });
    const apply = vi.fn<ApplyPreview>();
    renderCodePreview('mermaid', 'flowchart TD\nA--', apply, options);
    await settle();
    const failed = apply.mock.calls[0]?.[0] as HTMLElement;
    expect(failed.className).toBe('preview-error');
    expect(failed.textContent).toBe('그릴 수 없는 다이어그램 — Parse error <img src=x onerror=alert(1)>');
    expect(failed.children).toHaveLength(0);
    place(failed);
    expect(preview.querySelector('img')).toBeNull();
    expect(preview.textContent).toContain('<img src=x onerror=alert(1)>');
  });

  it('chart 가 틀리면 까닭 한 줄을 바로(요소의 글로) — 적힌 글은 HTML 로 읽히지 않는다', () => {
    const { options, place, preview, charts } = setup();
    const failed = renderCodePreview('CHART', '{"type":"<img src=x onerror=alert(1)>"}', vi.fn(), options) as HTMLElement;
    expect(failed.textContent).toBe('그릴 수 없는 차트 — 까닭:typeUnknown');
    place(failed);
    expect(preview.querySelector('img')).toBeNull();
    expect(charts.pendingCount).toBe(0);
  });

  it('chart 가 맞으면 표시만 단 빈 상자를 바로 — 걸러 넣어도 표시는 남는다', () => {
    const { options, place, preview } = setup();
    const box = renderCodePreview('chart', chart, vi.fn(), options) as HTMLElement;
    expect(box.getAttribute('data-chart-preview')).toBe('1');
    expect(box.childNodes).toHaveLength(0);
    place(box);
    expect(preview.querySelector('[data-chart-preview="1"]')).not.toBeNull();
  });
});

describe('ChartPreviews — 편집기 안에 들어온 차트 자리', () => {
  it('상자가 들어오면 알리고, 새 상자로 바뀌면 옛 자리를 내리고 새 자리를 알린다', async () => {
    const { options, place, preview, root, charts, changes } = setup();
    charts.attach(root);
    place(renderCodePreview('chart', chart, vi.fn(), options));
    await settle();
    expect(changes).toHaveLength(1);
    const first = changes[0]?.[0];
    expect(first?.element).toBe(preview.firstElementChild);
    expect((first?.chart as ChartSpec).type).toBe('bar');

    // 글자를 고침 → Crepe 가 새 상자로 바꿔 넣는다
    place(renderCodePreview('chart', chart.replace('[1]', '[2]'), vi.fn(), options));
    await settle();
    expect(changes).toHaveLength(2);
    const second = changes[1] ?? [];
    expect(second).toHaveLength(1);
    expect(second[0]?.key).not.toBe(first?.key);
    expect(second[0]?.element).toBe(preview.firstElementChild);
    expect(second[0]?.chart.type === 'bar' && second[0].chart.series[0]?.data).toEqual([2]);
    // 내려간 옛 꼴은 들고 있지 않는다
    expect(charts.pendingCount).toBe(1);
  });

  it('같은 꼴의 상자가 다시 들어와도(칸을 다시 그림) 새로 그린다', async () => {
    const { options, place, root, charts, changes } = setup();
    charts.attach(root);
    const box = renderCodePreview('chart', chart, vi.fn(), options) as HTMLElement;
    place(box);
    await settle();
    place(box);
    await settle();
    expect(changes).toHaveLength(2);
    expect(changes[1]).toHaveLength(1);
    expect(changes[1]?.[0]?.key).not.toBe(changes[0]?.[0]?.key);
  });

  it('코드 블록을 지우거나 미리보기가 없어지면 자리를 내린다', async () => {
    const { options, place, root, block, charts, changes } = setup();
    charts.attach(root);
    place(renderCodePreview('chart', chart, vi.fn(), options));
    await settle();
    block.remove();
    await settle();
    expect(changes.at(-1)).toEqual([]);
    expect(charts.pendingCount).toBe(0);
  });

  it('차트 안에서 일어난 바뀜(React · MUI 가 그리는 것)에는 다시 맞추지 않는다', async () => {
    const { options, place, preview, root, charts, changes } = setup();
    charts.attach(root);
    place(renderCodePreview('chart', chart, vi.fn(), options));
    await settle();
    const sweep = vi.spyOn(charts, 'sweep');
    const live = preview.firstElementChild as HTMLElement;
    live.append(document.createElement('svg'));
    live.firstElementChild?.append(document.createElement('g'));
    await settle();
    expect(sweep).not.toHaveBeenCalled();
    // 편집기의 다른 곳이 바뀌면 다시 맞춘다
    root.append(document.createElement('p'));
    await settle();
    expect(sweep).toHaveBeenCalledTimes(1);
    expect(changes).toHaveLength(1);
  });

  it('편집기를 닫으면(detach) 모든 자리를 내리고 더는 지켜보지 않는다', async () => {
    const { options, place, root, charts, changes } = setup();
    charts.attach(root);
    place(renderCodePreview('chart', chart, vi.fn(), options));
    await settle();
    charts.detach();
    expect(changes.at(-1)).toEqual([]);
    place(renderCodePreview('chart', chart, vi.fn(), options));
    await settle();
    expect(changes).toHaveLength(2);
  });

  it('넘겼지만 한 번도 들어오지 않은 꼴은 오래 지나면 버린다', async () => {
    let now = 0;
    const charts = new ChartPreviews(() => undefined, () => now);
    const root = document.createElement('div');
    document.body.append(root);
    charts.attach(root);
    const options = { ...setup().options, charts };
    renderCodePreview('chart', chart, vi.fn(), options);
    renderCodePreview('chart', chart, vi.fn(), options);
    expect(charts.pendingCount).toBe(2);
    root.append(document.createElement('p'));
    await settle();
    expect(charts.pendingCount).toBe(2);
    now = 10_001;
    root.append(document.createElement('p'));
    await settle();
    expect(charts.pendingCount).toBe(0);
  });
});
