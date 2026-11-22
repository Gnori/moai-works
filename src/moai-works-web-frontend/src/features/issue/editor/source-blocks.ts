// 사본 — moai-wiki 의 src/moai-wiki-web-frontend/src/features/page/editor/source-blocks.ts (위키 커밋 0c04259). 두 앱이 같은 원문 보존 구조를 쓴다.
// 고칠 때는 위키와 함께 고친다(moai-wiki 저장소는 읽기만 한다).
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
      return { blocks: markdown ? [{ text: markdown, before: '' }] : [], tail: '' };
    }
    blocks.push({ text: markdown.slice(start, end), before: markdown.slice(cursor, start) });
    cursor = end;
  }
  return { blocks, tail: markdown.slice(cursor) };
}

/** 나눈 것을 다시 잇는다 */
export function joinBlocks(split: SourceSplit): string {
  return split.blocks.map((block) => block.before + block.text).join('') + split.tail;
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
  const source = splitBlocks(original);
  const base = splitBlocks(baseline);
  const now = splitBlocks(current);
  // 원문의 덩어리와 편집기가 처음 내놓은 덩어리는 자리로 짝짓는다. 수가 다르면(편집기가 덩어리를 합치거나 쪼갰다)
  // 짝을 알 수 없으므로 편집기가 적은 글을 그대로 쓴다 — 내용은 맞고, 원문 모양만 지키지 못한다
  if (source.blocks.length !== base.blocks.length) {
    return current;
  }
  const match = matchBlocks(
    base.blocks.map((block) => block.text),
    now.blocks.map((block) => block.text),
  );
  // 덩어리마다 원문의 어느 자리인지 — 같은 덩어리는 그 자리, 고친 덩어리는 앞뒤의 같은 덩어리 사이에
  // 원문 덩어리가 같은 수만큼 있었으면 차례대로 그 자리로 본다(그 자리의 빈 줄 수를 지키려고). 알 수 없으면 -1
  const slots = slotsOf(match, source.blocks.length);
  let out = '';
  now.blocks.forEach((block, index) => {
    const slot = slots[index] ?? -1;
    const from = match[index] ?? -1;
    const kept = from === -1 ? undefined : source.blocks[from];
    const inPlace = slot !== -1 && (index === 0 ? slot === 0 : slots[index - 1] === slot - 1);
    const before = inPlace ? (source.blocks[slot]?.before ?? '') : index === 0 ? '' : '\n\n';
    // 같은 덩어리는 원문 조각을, 새로 쓰거나 고친 덩어리는 편집기가 적은 글을
    out += before + (kept ? kept.text : block.text);
  });
  if (now.blocks.length === 0) {
    return '';
  }
  return out + (slots[now.blocks.length - 1] === source.blocks.length - 1 ? source.tail : '\n');
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
