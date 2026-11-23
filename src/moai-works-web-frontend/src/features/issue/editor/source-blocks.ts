// 사본 — moai-wiki 의 src/moai-wiki-web-frontend/src/features/page/editor/source-blocks.ts (위키 커밋 166bbd8 — 참조 링크 정의 줄 보존). 두 앱이 같은 원문 보존 구조를 쓴다.
// 고칠 때는 위키와 함께 고친다(moai-wiki 저장소는 읽기만 한다).
// moai-works 가 먼저 고친 것(일감 #16, 위키에 알린다): createPreserver(원문 · baseline 을 한 번만 나눔) · resplit(지금 글은 바뀐 곳 둘레만 다시 나눔) ·
// untangle(붙어 적히는 이웃 — 같은 표지의 목록 · 목록 뒤 들여 쓴 코드).
import remarkGfm from 'remark-gfm';
import remarkParse from 'remark-parse';
import { unified } from 'unified';

/**
 * 원문 보존 — 쓰면서 바로 그려지는 편집기는 저장할 때 글 전체를 자기 방식으로 다시 적는다
 * (목록 기호 · 표의 칸 맞춤 · 강조 기호 · 빈 줄 · `\` 처리). 그대로 저장하면 한 글자만 고쳐도
 * 리비전 비교에 글 전체가 바뀐 것으로 남고, 섹션 단위 병합이 늘 부딪힌다.
 *
 * 그래서 **손대지 않은 덩어리는 원문 그대로 둔다.** 글을 맨 위 덩어리(문단 · 제목 · 목록 · 표 · 코드 …)로 나누고,
 * 편집기가 처음 내놓은 모양(baseline)과 지금 내놓은 모양(current)을 덩어리끼리 견줘서
 * 같은 덩어리에는 원문 조각을, 달라진 덩어리에만 편집기가 적은 글을 쓴다.
 *
 * 순수 함수다 — 편집기 없이 시험한다. moai-works 의 편집기와 같은 구조다.
 */

/** 맨 위 덩어리 하나 */
export interface SourceBlock {
  /** 덩어리의 종류(mdast) — paragraph · heading · list · table · code · definition … */
  type: string;
  /** 덩어리의 글(앞뒤 빈 줄 없이) */
  text: string;
  /** 이 덩어리 앞에 있던 글 — 앞 덩어리와의 사이(빈 줄), 첫 덩어리면 글 머리의 빈 줄 */
  before: string;
}

export interface SourceSplit {
  blocks: SourceBlock[];
  /** 마지막 덩어리 뒤의 글(끝의 줄바꿈) */
  tail: string;
}

interface Positioned {
  type: string;
  position?: { start: { offset?: number }; end: { offset?: number } };
}

const parser = unified().use(remarkParse).use(remarkGfm);

/** 글을 맨 위 덩어리로 나눈다. 덩어리와 사이 글을 차례로 이으면 원래 글이 된다. */
export function splitBlocks(markdown: string): SourceSplit {
  const root = parser.parse(markdown) as { children: Positioned[] };
  const blocks: SourceBlock[] = [];
  let cursor = 0;
  for (const child of root.children) {
    const start = child.position?.start.offset;
    const end = child.position?.end.offset;
    if (start === undefined || end === undefined || start < cursor) {
      // 자리를 모르는 덩어리가 있으면 나누지 않는다 — 글 전체를 한 덩어리로 본다
      return { blocks: markdown ? [{ type: 'unknown', text: markdown, before: '' }] : [], tail: '' };
    }
    blocks.push({ type: child.type, text: markdown.slice(start, end), before: markdown.slice(cursor, start) });
    cursor = end;
  }
  return { blocks, tail: markdown.slice(cursor) };
}

/** 나눈 것을 다시 잇는다 */
export function joinBlocks(split: SourceSplit): string {
  return split.blocks.map((block) => block.before + block.text).join('') + split.tail;
}

/** 끝나지 않은 HTML 덩어리(1–5 종류: `<script` · `<pre` · `<style` · `<textarea` · `<!--` · `<?` · `<!X` · `<![CDATA[`) — 빈 줄을 넘어 뒤 글을 삼킨다 */
function openHtml(text: string): boolean {
  const html = text.replace(/^ {0,3}/, '');
  if (/^<(?:script|pre|style|textarea)(?:[\s>]|$)/i.test(html)) {
    return !/<\/(?:script|pre|style|textarea)>/i.test(html);
  }
  if (html.startsWith('<!--')) {
    return !html.slice(4).includes('-->');
  }
  if (html.startsWith('<?')) {
    return !html.slice(2).includes('?>');
  }
  if (html.startsWith('<![CDATA[')) {
    return !html.slice(9).includes(']]>');
  }
  if (/^<![A-Za-z]/.test(html)) {
    return !html.slice(2).includes('>');
  }
  return false;
}

/** 닫히지 않은 울타리 코드(``` · ~~~) — 글 끝까지 이어진다 */
function openFence(text: string): boolean {
  const fence = /^ {0,3}(`{3,}|~{3,})/.exec(text)?.[1];
  if (!fence) {
    return false;
  }
  const lines = text.split('\n');
  const last = (lines[lines.length - 1] ?? '').trim();
  return lines.length < 2 || last.length < fence.length || [...last].some((c) => c !== fence[0]);
}

/** 빈 줄이 든 사이 글 */
const hasBlankLine = (gap: string) => /\n[ \t]*\n/.test(gap);

/**
 * 고친 글을 다시 나눈다 — 고치기 전 글을 나눈 것을 다시 쓰고, 바뀐 곳 둘레의 덩어리만 다시 해석한다(moai-works 일감 #16: 3만 자 글을
 * 통째로 해석하면 브라우저에서 수십 ms). 결과는 splitBlocks(next) 와 같다 — 이음매가 앞뒤 글에 따라 달리 읽힐 수 있으면 통째로 나눈다.
 *
 * 바뀐 곳에 닿는 덩어리와 그 앞뒤 하나씩을 다시 해석하고, 그 앞 · 뒤 덩어리는 그대로 둔다. 이음매가 안전한 조건:
 *   - 이음매의 사이 글에 빈 줄이 있다(문단 · 인용 · 표의 이어짐, setext 제목, 표 머리 줄 알아보기가 빈 줄을 넘지 못한다)
 *   - 이음매 앞 덩어리가 닫혀 있다(끝나지 않은 HTML 1–5 종류 · 닫히지 않은 울타리 코드는 빈 줄을 넘어 이어진다)
 *   - 목록 뒤 목록이 아니다(같은 표지면 빈 줄이 있어도 한 목록으로 합쳐진다)
 *   - 이음매 뒤 덩어리가 들여 쓰지 않았다(들여 쓴 줄은 앞 목록 · 각주 정의의 이어짐이나 앞 코드의 이어짐이 된다)
 */
export function resplit(previous: string, split: SourceSplit, next: string): SourceSplit {
  if (previous === next) {
    return split;
  }
  const { blocks } = split;
  const count = blocks.length;
  if (count < 3 || blocks.some((block) => block.type === 'unknown')) {
    return splitBlocks(next);
  }
  // 덩어리마다 [시작, 끝)
  const starts: number[] = [];
  const ends: number[] = [];
  let at = 0;
  for (const block of blocks) {
    at += block.before.length;
    starts.push(at);
    at += block.text.length;
    ends.push(at);
  }
  // 같은 앞 · 같은 뒤 — 바뀐 곳은 고치기 전 글의 [head, previous.length - tail]
  const limit = Math.min(previous.length, next.length);
  let head = 0;
  while (head < limit && previous.charCodeAt(head) === next.charCodeAt(head)) {
    head++;
  }
  let tail = 0;
  while (tail < limit - head && previous.charCodeAt(previous.length - 1 - tail) === next.charCodeAt(next.length - 1 - tail)) {
    tail++;
  }
  const changeEnd = previous.length - tail;
  // 바뀐 곳에 닿는 덩어리 [first, last] 와 그 앞뒤 하나씩
  let first = 0;
  while (first < count && (ends[first] ?? 0) < head) {
    first++;
  }
  let last = count - 1;
  while (last >= 0 && (starts[last] ?? 0) > changeEnd) {
    last--;
  }
  const from = Math.max(0, Math.min(first, last) - 1);
  const to = Math.min(count - 1, Math.max(first, last) + 1);
  const regionStart = from === 0 ? 0 : (starts[from] ?? 0);
  const regionEnd = (to === count - 1 ? previous.length : (ends[to] ?? 0)) + next.length - previous.length;
  const middle = splitBlocks(next.slice(regionStart, regionEnd));
  const firstNew = middle.blocks[0];
  const lastNew = middle.blocks[middle.blocks.length - 1];
  if (!firstNew || !lastNew || middle.blocks.some((block) => block.type === 'unknown')) {
    return splitBlocks(next);
  }
  const stitched = blocks.slice(0, from);
  if (from > 0) {
    const before = blocks[from - 1];
    const gap = blocks[from]?.before ?? '';
    if (!before || firstNew.before !== '' || /^[ \t]/.test(firstNew.text) || !hasBlankLine(gap) || (before.type === 'list' && firstNew.type === 'list')) {
      return splitBlocks(next);
    }
    stitched.push({ ...firstNew, before: gap });
  } else {
    stitched.push(firstNew);
  }
  stitched.push(...middle.blocks.slice(1));
  if (to === count - 1) {
    return { blocks: stitched, tail: middle.tail };
  }
  const after = blocks[to + 1];
  if (!after || !hasBlankLine(after.before) || /[ \t]$/.test(after.before) || /^[ \t]/.test(after.text) || openHtml(lastNew.text) || openFence(lastNew.text) || (lastNew.type === 'list' && after.type === 'list')) {
    return splitBlocks(next);
  }
  stitched.push({ ...after, before: middle.tail + after.before }, ...blocks.slice(to + 2));
  return { blocks: stitched, tail: split.tail };
}

/**
 * 두 덩어리 줄에서 순서를 지키며 가장 많이 겹치는 짝을 찾는다(LCS). 돌려주는 것은 `b` 의 각 덩어리가
 * `a` 의 몇 번째와 같은지 — 짝이 없으면 -1.
 */
export function matchBlocks(a: string[], b: string[]): number[] {
  const result = new Array<number>(b.length).fill(-1);
  // 앞뒤의 같은 부분을 먼저 떼어 낸다 — 보통은 가운데 한두 덩어리만 다르다
  let head = 0;
  while (head < a.length && head < b.length && a[head] === b[head]) {
    result[head] = head;
    head++;
  }
  let tailA = a.length;
  let tailB = b.length;
  while (tailA > head && tailB > head && a[tailA - 1] === b[tailB - 1]) {
    tailA--;
    tailB--;
    result[tailB] = tailA;
  }
  const n = tailA - head;
  const m = tailB - head;
  if (n === 0 || m === 0) {
    return result;
  }
  // 가운데만 표로 푼다 — 한 줄로 편 표(가로 m+1 칸)
  const width = m + 1;
  const table = new Uint32Array((n + 1) * width);
  const same = (i: number, j: number) => a[head + i] === b[head + j];
  for (let i = n - 1; i >= 0; i--) {
    for (let j = m - 1; j >= 0; j--) {
      const down = table[(i + 1) * width + j] ?? 0;
      const right = table[i * width + j + 1] ?? 0;
      const diagonal = table[(i + 1) * width + j + 1] ?? 0;
      table[i * width + j] = same(i, j) ? diagonal + 1 : Math.max(down, right);
    }
  }
  let i = 0;
  let j = 0;
  while (i < n && j < m) {
    if (same(i, j)) {
      result[head + j] = head + i;
      i++;
      j++;
    } else if ((table[(i + 1) * width + j] ?? 0) >= (table[i * width + j + 1] ?? 0)) {
      i++;
    } else {
      j++;
    }
  }
  return result;
}

/**
 * 저장할 글을 만든다.
 *
 * @param original 저장돼 있던 원문
 * @param baseline 그 원문을 편집기에 넣자마자 편집기가 내놓은 글
 * @param current  편집기가 지금 내놓은 글
 * @returns 손대지 않은 덩어리는 원문 그대로, 달라진 덩어리만 편집기가 적은 글
 */
export function preserveSource(original: string, baseline: string, current: string): string {
  // 아무것도 고치지 않았다 — 원문과 한 글자도 다르지 않아야 한다
  if (current === baseline) {
    return original;
  }
  const paired = pairUp(original, baseline);
  // 편집기가 덩어리를 합치거나 쪼갰다 — 편집기가 적은 글을 그대로 쓴다. 내용은 맞고, 원문 모양만 지키지 못한다
  return paired ? preserveWith(paired, splitBlocks(current)) : current;
}

/** 한 편집기에서 거듭 쓰는 원문 보존 함수 — preserveSource(original, baseline, current) 와 같은 글을 낸다 */
export interface Preserver {
  (current: string): string;
  /** 원문 · baseline 을 미리 나눠 둔다(편집기가 준비된 뒤 한가할 때) — 부르지 않으면 처음 고친 글이 올 때 나눈다 */
  prepare: () => void;
}

/**
 * {@link preserveSource} 를 한 편집기에서 거듭 부를 때(moai-works 일감 #16 — 3만 자 글을 remark 로 한 번 나누는 데 브라우저에서 수십 ms,
 * 고칠 때마다 원문 · baseline · 지금 글 셋을 통째로 나누던 것이 쓰다 멈춘 뒤 일의 대부분이었다):
 *   - 원문 · baseline 은 편집기를 다시 만들 때까지 같으므로 한 번만 나눈다
 *   - 지금 글은 바로 앞 글(처음에는 baseline)을 나눈 것에서 바뀐 곳 둘레만 다시 나눈다({@link resplit})
 */
export function createPreserver(original: string, baseline: string): Preserver {
  /** null 이면 짝을 알 수 없다 */
  let pairing: Pairing | null | undefined;
  /** 바로 앞에 나눈 글 */
  let last: { text: string; split: SourceSplit } | null = null;
  const paired = () => {
    if (pairing === undefined) {
      pairing = pairUp(original, baseline);
    }
    return pairing;
  };
  const preserve = (current: string) => {
    // 아무것도 고치지 않았다 — 원문과 한 글자도 다르지 않아야 한다
    if (current === baseline) {
      return original;
    }
    const pair = paired();
    if (!pair) {
      return current;
    }
    const previous = last ?? { text: baseline, split: pair.base };
    const now = resplit(previous.text, previous.split, current);
    last = { text: current, split: now };
    return preserveWith(pair, now);
  };
  return Object.assign(preserve, {
    prepare: () => {
      paired();
    },
  });
}

/** 원문의 덩어리와 편집기가 처음 내놓은 덩어리의 짝 */
interface Pairing {
  source: SourceSplit;
  /** baseline 을 나눈 것 */
  base: SourceSplit;
  /** 원문의 내용 덩어리(참조 링크 정의 줄을 뺀 것) — baseline 의 덩어리와 차례로 짝이 된다 */
  content: SourceBlock[];
  /** 참조 링크의 정의 줄들 — 열쇠는 바로 앞 내용 덩어리의 자리(글 머리에 있으면 -1) */
  definitions: Map<number, SourceBlock[]>;
  /** baseline 덩어리들의 글 */
  baseTexts: string[];
}

/** 원문의 덩어리와 편집기가 처음 내놓은 덩어리는 자리로 짝짓는다. 짝을 알 수 없으면 null */
function pairUp(original: string, baseline: string): Pairing | null {
  const source = splitBlocks(original);
  const base = splitBlocks(baseline);
  let content = source.blocks;
  const definitions = new Map<number, SourceBlock[]>();
  if (source.blocks.length !== base.blocks.length) {
    // 편집기는 참조 링크의 정의 줄(`[이름]: 주소`)을 버리고 링크를 제자리에 풀어 적는다. 정의 줄을 빼고 수가 맞으면
    // 나머지를 자리로 짝짓고, 정의 줄은 원문에 있던 자리에 그대로 남긴다(손대지 않은 덩어리가 아직 그 이름을 쓴다)
    content = source.blocks.filter((block) => block.type !== 'definition');
    if (content.length !== base.blocks.length) {
      // 그래도 수가 다르면(편집기가 덩어리를 합치거나 쪼갰다) 짝을 알 수 없다
      return null;
    }
    let owner = -1;
    for (const block of source.blocks) {
      if (block.type === 'definition') {
        definitions.set(owner, [...(definitions.get(owner) ?? []), block]);
      } else {
        owner++;
      }
    }
  }
  return { source, base, content, definitions, baseTexts: base.blocks.map((block) => block.text) };
}

/** 저장할 덩어리 하나 — 원문 조각(kept)이거나 편집기 글 */
interface Chosen {
  type: string;
  text: string;
  before: string;
  /** 원문 조각이다 — 그때 원문의 몇 번째 내용 덩어리인지가 from */
  kept: boolean;
  from: number;
  /** 원문의 자리(-1 이면 모른다) */
  slot: number;
}

function preserveWith({ source, content, definitions: owned, baseTexts }: Pairing, now: SourceSplit): string {
  if (now.blocks.length === 0) {
    return '';
  }
  const match = matchBlocks(
    baseTexts,
    now.blocks.map((block) => block.text),
  );
  // 덩어리마다 원문의 어느 자리인지 — 같은 덩어리는 그 자리, 고친 덩어리는 앞뒤의 같은 덩어리 사이에
  // 원문 덩어리가 같은 수만큼 있었으면 차례대로 그 자리로 본다(그 자리의 빈 줄 수를 지키려고). 알 수 없으면 -1
  const slots = slotsOf(match, content.length);
  // 같은 덩어리는 원문 조각을, 새로 쓰거나 고친 덩어리는 편집기가 적은 글을
  const chosen: Chosen[] = now.blocks.map((block, index) => {
    const slot = slots[index] ?? -1;
    const from = match[index] ?? -1;
    const kept = from === -1 ? undefined : content[from];
    const inPlace = slot !== -1 && (index === 0 ? slot === 0 : slots[index - 1] === slot - 1);
    const before = inPlace ? (content[slot]?.before ?? '') : index === 0 ? '' : '\n\n';
    // 편집기 글은 0번째 칸에서 시작하게 적힌 글이다 — 원문 자리의 앞 들여쓰기는 원문 조각에만 붙인다(들여 쓴 목록을 고치면 안쪽 항목이 형제가 되던 것)
    return kept ? { type: kept.type, text: kept.text, before, kept: true, from, slot } : { type: block.type, text: block.text, before: before.replace(/[ \t]+$/, ''), kept: false, from: -1, slot };
  });
  // 앞 덩어리 뒤에 정의 줄이 남으면 둘은 이웃이 아니다
  untangle(chosen, now.blocks, (index) => {
    const slot = chosen[index - 1]?.slot ?? -1;
    return slot !== -1 && (owned.get(slot)?.length ?? 0) > 0;
  });
  const definitions = new Map(owned);
  const join = (blocks: SourceBlock[]) => blocks.map((block) => block.before + block.text).join('');
  let out = '';
  chosen.forEach((block, index) => {
    if (index === 0 && block.slot === 0) {
      // 글 머리에 있던 정의 줄
      out += join(definitions.get(-1) ?? []);
      definitions.delete(-1);
    }
    out += block.before + block.text;
    if (block.slot !== -1) {
      // 이 덩어리 뒤에 있던 정의 줄
      out += join(definitions.get(block.slot) ?? []);
      definitions.delete(block.slot);
    }
  });
  // 있던 자리가 없어진 정의 줄은 글 끝에 모은다 — 남은 덩어리가 쓰는 이름이 끊기지 않게
  const orphans = [...definitions.values()].flat();
  if (orphans.length > 0) {
    return `${out}\n\n${orphans.map((block) => block.text).join('\n')}\n`;
  }
  return out + (slots[now.blocks.length - 1] === content.length - 1 ? source.tail : '\n');
}

/** 목록의 표지 — 글머리 기호(`-` `*` `+`) 또는 번호 뒤 구분 기호(`.` `)`) */
function listMarker(text: string): string | undefined {
  const match = /^[ \t]*(?:([-*+])|\d{1,9}([.)]))/.exec(text);
  return match?.[1] ?? match?.[2];
}

/** 들여 써서 만든 코드 덩어리(``` · ~~~ 로 감싸지 않은 것) */
function indentedCode(block: Chosen): boolean {
  return block.type === 'code' && /^(?: {0,3}\t| {4})/.test(block.text);
}

/**
 * 붙어 적히는 이웃 풀기 — 원문 조각과 편집기 글을, 또는 원문에서 떨어져 있던 원문 조각끼리를 빈 줄만 두고 이웃으로 붙이면
 * 다시 읽을 때 앞 덩어리와 합쳐질 수 있다(moai-works 일감 #16 수용 기준). 그런 이웃은 편집기 글(now — 편집기가 이 이웃을 함께
 * 적은 모양이라 다시 읽어도 따로다)로 바꾼다:
 *   - 같은 표지의 목록이 이어짐(`*` 원문 뒤 편집기가 `*` 로 적은 목록 · `1)` 뒤 `1)` …) — 이어진 같은 종류 목록 전체를 편집기 글로.
 *     편집기는 이어진 목록의 기호를 번갈아 쓴다(`-` 다음 `*`, `1.` 다음 `1)`)
 *   - 목록 · 각주 정의 · 들여 쓴 코드 바로 뒤의 들여 쓴 코드(원문 조각) — 앞 목록 안으로 들어가거나 앞 코드와 합쳐진다. 그 코드를 편집기 글(```)로
 *   - 목록 · 각주 정의 바로 뒤 덩어리의 앞 들여쓰기 — 앞 목록의 이어짐으로 읽힐 수 있다. 들여쓰기를 뗀다(맨 위 덩어리 앞의 세 칸까지는 뜻이 없다).
 *     원문 조각인 목록 · 각주 정의는 안쪽 줄이 그 들여쓰기에 맞춰 있어 편집기 글로
 * 원문에서 이미 이웃이던 원문 조각 둘은 그대로 둔다(원문을 다시 읽어도 따로였다). 정의 줄이 사이에 남으면 이웃이 아니다.
 */
function untangle(chosen: Chosen[], now: SourceBlock[], definitionsBefore: (index: number) => boolean) {
  const adjacent = (index: number) => index > 0 && !definitionsBefore(index);
  const sourceNeighbours = (index: number) => {
    const previous = chosen[index - 1];
    const block = chosen[index];
    return !!previous && !!block && previous.kept && block.kept && block.from === previous.from + 1;
  };
  const toEditorText = (index: number) => {
    const block = chosen[index];
    const written = now[index];
    if (block?.kept && written) {
      chosen[index] = { ...block, type: written.type, text: written.text, before: block.before.replace(/[ \t]+$/, ''), kept: false, from: -1 };
    }
  };
  // 이어진 같은 종류(글머리 · 번호)의 목록 묶음에 표지가 겹치는 이웃이 있으면 → 묶음 전체를 편집기 글로.
  // 글머리 목록과 번호 목록은 붙어도 합쳐지지 않으니 묶음을 거기서 끊는다(고치지 않은 다른 종류 목록은 원문 그대로)
  const kind = (block: Chosen | undefined) => (block?.type === 'list' ? (/[.)]/.test(listMarker(block.text) ?? '') ? 'ordered' : 'bullet') : null);
  let start = 0;
  while (start < chosen.length) {
    let end = start + 1;
    const runKind = kind(chosen[start]);
    if (runKind) {
      while (end < chosen.length && kind(chosen[end]) === runKind && adjacent(end)) {
        end++;
      }
      let clash = false;
      for (let index = start + 1; index < end; index++) {
        const marker = listMarker(chosen[index]?.text ?? '');
        clash ||= marker !== undefined && marker === listMarker(chosen[index - 1]?.text ?? '');
      }
      if (clash) {
        for (let index = start; index < end; index++) {
          toEditorText(index);
        }
      }
    }
    start = end;
  }
  // 목록 · 각주 정의 · 들여 쓴 코드 뒤
  for (let index = 1; index < chosen.length; index++) {
    const previous = chosen[index - 1];
    const block = chosen[index];
    if (!previous || !block || !adjacent(index) || sourceNeighbours(index)) {
      continue;
    }
    const container = previous.type === 'list' || previous.type === 'footnoteDefinition';
    if (block.kept && indentedCode(block) && (container || indentedCode(previous))) {
      toEditorText(index);
    } else if (container && /[ \t]$/.test(block.before)) {
      if (block.kept && (block.type === 'list' || block.type === 'footnoteDefinition')) {
        // 원문 조각의 안쪽 줄은 그 들여쓰기에 맞춰 있다 — 들여쓰기만 떼면 안쪽 모양이 바뀔 수 있으니 편집기 글로
        toEditorText(index);
      } else {
        block.before = block.before.replace(/[ \t]+$/, '');
      }
    }
  }
}

/** 지금의 덩어리마다 원문의 자리를 정한다 — {@link preserveSource} 의 설명을 본다 */
function slotsOf(match: number[], sourceLength: number): number[] {
  const slots = match.slice();
  let index = 0;
  while (index < slots.length) {
    if (slots[index] !== -1) {
      index++;
      continue;
    }
    // 고친 덩어리가 이어진 구간 [index, end)
    let end = index;
    while (end < slots.length && slots[end] === -1) {
      end++;
    }
    const previous = index > 0 ? (slots[index - 1] ?? -1) : -1;
    const next = end < slots.length ? (match[end] ?? sourceLength) : sourceLength;
    // 그 사이에 원문 덩어리가 같은 수만큼 있었다 — 제자리에서 고친 것으로 본다
    if ((index === 0 || previous !== -1) && next - previous - 1 === end - index) {
      for (let k = index; k < end; k++) {
        slots[k] = previous + 1 + (k - index);
      }
    }
    index = end;
  }
  return slots;
}
