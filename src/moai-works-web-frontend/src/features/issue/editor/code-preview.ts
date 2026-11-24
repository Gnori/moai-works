import { parseChartSpec, type ChartError, type ChartSpec } from '../chart-spec';
import { CHART_LANGUAGE, DIAGRAM_LANGUAGE, isLanguage } from '../code-blocks';
import type { Drawing } from '../mermaid';

/**
 * 편집기(Crepe)의 코드 블록 미리보기 — ```mermaid 는 다이어그램, ```chart 는 차트를 코드 아래에 그린다(moai-wiki 와 같은 길, 차트는 더함).
 *
 * Crepe 의 미리보기 칸은 받은 것을 DOMPurify 로 거른 HTML 글로 바꿔 넣는다(preview-panel: innerHTML = sanitize(값)).
 * 그래서 넘긴 요소가 그대로 들어가지 않는다 — 살아 있는 React 차트를 넘길 수 없다.
 *   - mermaid: 보기 화면과 같은 함수(drawMermaid, strict)로 그린 SVG 글을 넘긴다.
 *   - chart: 표시(data-chart-preview="번호")만 단 빈 상자를 넘기고, 그 상자가 편집기 안에 실제로 들어오면(ChartPreviews 가 지켜봄)
 *     RichEditor 가 그 자리에 보기 화면과 같은 ChartView 를 React portal 로 그린다 — 화면 테마 · 다크 · 언어를 그대로 받고,
 *     상자가 사라지면(글을 고쳐 새 상자로 바뀜 · 코드 블록을 지움 · 화면 밖으로 나가 Crepe 가 치움 · 편집기를 닫음) portal 도 내린다.
 *   - 그릴 수 없으면 까닭을 요소의 글(textContent)로만 넣는다 — HTML 로 읽히지 않는다.
 */

export type ApplyPreview = (value: null | string | HTMLElement) => void;

export interface PreviewText {
  diagramFailed: string;
  diagramInvalid: string;
  chartFailed: string;
  reason: (error: ChartError) => string;
}

export interface CodePreviewOptions {
  /** 지금 다크인가 — 그릴 때마다 읽는다 */
  dark: () => boolean;
  /** 화면 글 — 그릴 때마다 읽는다(언어를 바꾸면 다음 그림부터) */
  text: () => PreviewText;
  charts: ChartPreviews;
  drawMermaid: (code: string, dark: boolean, invalidMessage: string) => Promise<Drawing>;
}

/** 그릴 수 없는 까닭 — 글자로만 */
export function previewFailure(message: string): HTMLElement {
  const failed = document.createElement('div');
  failed.className = 'preview-error';
  failed.textContent = message;
  return failed;
}

/**
 * Crepe CodeMirror 의 renderPreview. 돌려주는 값: null = 미리보기 없음, undefined = 나중에 apply 로, 요소 = 바로 이것.
 */
export function renderCodePreview(language: string, content: string, apply: ApplyPreview, options: CodePreviewOptions): null | undefined | HTMLElement {
  if (!content.trim()) {
    return null;
  }
  if (isLanguage(language, DIAGRAM_LANGUAGE)) {
    const text = options.text();
    void options.drawMermaid(content, options.dark(), text.diagramInvalid).then((drawing) => {
      apply('svg' in drawing ? drawing.svg : previewFailure(`${text.diagramFailed} — ${drawing.error}`));
    });
    return undefined;
  }
  if (isLanguage(language, CHART_LANGUAGE)) {
    const result = parseChartSpec(content);
    if (!result.ok) {
      const text = options.text();
      return previewFailure(`${text.chartFailed} — ${text.reason(result.error)}`);
    }
    return options.charts.placeholder(result.chart);
  }
  return null;
}

/** 편집기 안에 들어온 차트 자리 하나 — RichEditor 가 여기에 portal 로 그린다 */
export interface LiveChart {
  /** portal 의 key — 같은 번호의 상자가 다시 들어와도 새로 그린다 */
  key: string;
  element: HTMLElement;
  chart: ChartSpec;
}

const ATTRIBUTE = 'data-chart-preview';
/** 넘겼지만 편집기에 한 번도 들어오지 않은 꼴은 이만큼 지나면 버린다(바로 다음 글자로 바뀐 것 등) */
const UNSEEN_TTL_MS = 10_000;

interface Pending {
  chart: ChartSpec;
  seen: boolean;
  created: number;
}

/**
 * 차트 미리보기 자리를 지켜본다 — 편집기 뿌리(root)를 MutationObserver 로 보다가, 표시 단 상자가 들어오면 · 나가면 onChange 로 알린다.
 * 차트 안에서 일어난 바뀜(React · MUI 가 그리는 것)은 보지 않는다.
 */
export class ChartPreviews {
  private next = 0;
  private mounts = 0;
  private readonly pending = new Map<string, Pending>();
  private readonly live = new Map<HTMLElement, LiveChart & { id: string }>();
  private observer: MutationObserver | null = null;
  private root: HTMLElement | null = null;

  constructor(
    private readonly onChange: (charts: LiveChart[]) => void,
    private readonly now: () => number = () => Date.now(),
  ) {}

  /** 미리보기 칸에 넘길 빈 상자 — Crepe 가 글로 바꿔 넣어도 남는 표시(번호)만 단다 */
  placeholder(chart: ChartSpec): HTMLElement {
    this.next += 1;
    const id = String(this.next);
    this.pending.set(id, { chart, seen: false, created: this.now() });
    const element = document.createElement('div');
    element.className = 'chart-preview';
    element.setAttribute(ATTRIBUTE, id);
    return element;
  }

  attach(root: HTMLElement): void {
    this.observer?.disconnect();
    this.root = root;
    this.observer = new MutationObserver((records) => {
      if (records.some((record) => !insideChart(record.target))) {
        this.sweep();
      }
    });
    this.observer.observe(root, { childList: true, subtree: true });
    this.sweep();
  }

  /** 편집기를 닫을 때 — 지켜보기를 멈추고 모든 자리를 내린다 */
  detach(): void {
    this.observer?.disconnect();
    this.observer = null;
    this.root = null;
    this.pending.clear();
    if (this.live.size > 0) {
      this.live.clear();
      this.onChange([]);
    }
  }

  /** 지금 편집기 안의 자리와 맞춘다 */
  sweep(): void {
    const root = this.root;
    if (!root || (this.pending.size === 0 && this.live.size === 0)) {
      return;
    }
    let changed = false;
    for (const element of [...this.live.keys()]) {
      if (!root.contains(element)) {
        this.live.delete(element);
        changed = true;
      }
    }
    for (const element of root.querySelectorAll<HTMLElement>(`[${ATTRIBUTE}]`)) {
      const id = element.getAttribute(ATTRIBUTE) ?? '';
      const entry = this.pending.get(id);
      if (this.live.has(element) || !entry) {
        continue;
      }
      entry.seen = true;
      this.mounts += 1;
      this.live.set(element, { key: `${id}-${this.mounts}`, id, element, chart: entry.chart });
      changed = true;
    }
    // 들어왔다가 나간 꼴 · 오래도록 들어오지 않은 꼴은 버린다
    const liveIds = new Set([...this.live.values()].map((chart) => chart.id));
    const now = this.now();
    for (const [id, entry] of [...this.pending]) {
      if (!liveIds.has(id) && (entry.seen || now - entry.created > UNSEEN_TTL_MS)) {
        this.pending.delete(id);
      }
    }
    if (changed) {
      this.onChange([...this.live.values()].map(({ key, element, chart }) => ({ key, element, chart })));
    }
  }

  /** 시험용 — 아직 들고 있는 꼴의 수 */
  get pendingCount(): number {
    return this.pending.size;
  }
}

/** 차트 자리 안(React · MUI 가 그리는 곳)에서 일어난 바뀜인가 */
function insideChart(target: Node): boolean {
  const element = target instanceof Element ? target : target.parentElement;
  return !!element?.closest(`[${ATTRIBUTE}]`);
}
