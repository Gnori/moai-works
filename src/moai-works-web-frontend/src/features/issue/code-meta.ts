/**
 * 코드 블록 여는 줄의 언어 뒤 글(meta) 읽기 — ```ts title="src/app.ts" {1,3-5}
 *
 *   title="…" · title='…' · title=이름  → 머리에 보일 파일 이름(120자까지, 제어 문자는 뺀다)
 *   {1,3-5}                            → 강조할 줄(1부터, 범위 20개 · 줄 번호 100000 까지)
 *
 * meta 는 remark 가 넘겨준 글 그대로다(mdast `code.meta` → hast `code.data.meta`). 남이 쓴 글이므로 길이 · 개수를 자르고,
 * 모르는 말은 버린다. 보일 때는 글자로만 쓴다(HTML 로 넣지 않는다).
 */
import type { CodeNode } from './code-blocks';

export interface LineRange {
  from: number;
  to: number;
}

export interface CodeMeta {
  /** 머리에 보일 파일 이름. 없으면 null */
  title: string | null;
  /** 강조할 줄 범위(1부터, 양 끝 포함) */
  marked: LineRange[];
}

export const MAX_TITLE = 120;
export const MAX_RANGES = 20;
export const MAX_LINE = 100_000;
/** 이보다 긴 meta 는 앞부분만 읽는다 */
const MAX_META = 1_000;

const TITLE = /(?:^|\s)title=(?:"([^"]*)"|'([^']*)'|([^\s"'{}]+))/;
const RANGES = /\{([^{}]*)\}/;
const RANGE = /^(\d{1,7})(?:\s*-\s*(\d{1,7}))?$/;
// eslint-disable-next-line no-control-regex -- 제목에서 제어 문자를 지운다
const CONTROL = /[\u0000-\u001f\u007f-\u009f]/g;

const EMPTY: CodeMeta = { title: null, marked: [] };

export function parseCodeMeta(meta: unknown): CodeMeta {
  if (typeof meta !== 'string' || !meta.trim()) {
    return EMPTY;
  }
  let rest = meta.slice(0, MAX_META);
  let title: string | null = null;
  const titleMatch = TITLE.exec(rest);
  if (titleMatch) {
    title = cleanTitle(titleMatch[1] ?? titleMatch[2] ?? titleMatch[3] ?? '');
    // 제목 안의 {…} 를 줄 범위로 읽지 않게 떼어 낸다
    rest = rest.slice(0, titleMatch.index) + ' ' + rest.slice(titleMatch.index + titleMatch[0].length);
  }
  const rangeMatch = RANGES.exec(rest);
  return { title, marked: rangeMatch ? parseRanges(rangeMatch[1] ?? '') : [] };
}

function cleanTitle(raw: string): string | null {
  const text = raw.replace(CONTROL, ' ').replace(/\s+/g, ' ').trim();
  if (!text) {
    return null;
  }
  // 글자(코드 포인트) 단위로 자른다 — 이모지 · 한글 조합이 반으로 갈라지지 않게
  return Array.from(text).slice(0, MAX_TITLE).join('');
}

function parseRanges(body: string): LineRange[] {
  const ranges: LineRange[] = [];
  for (const part of body.split(',')) {
    if (ranges.length >= MAX_RANGES) {
      break;
    }
    const match = RANGE.exec(part.trim());
    if (!match) {
      continue;
    }
    const from = Number(match[1]);
    const to = match[2] === undefined ? from : Number(match[2]);
    if (from < 1 || to < from || from > MAX_LINE) {
      continue;
    }
    ranges.push({ from, to: Math.min(to, MAX_LINE) });
  }
  return ranges;
}

/** `<pre>` 마디에서 코드 블록의 meta 를 읽어 푼다 */
export function codeMeta(pre: CodeNode | undefined): CodeMeta {
  const code = pre?.children?.find((child) => child.type === 'element' && child.tagName === 'code');
  return parseCodeMeta(code?.data?.meta);
}

/** 이 줄(1부터)을 강조하나 */
export function isMarked(line: number, marked: readonly LineRange[]): boolean {
  return marked.some((range) => line >= range.from && line <= range.to);
}
