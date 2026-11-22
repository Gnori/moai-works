import { describe, expect, it } from 'vitest';

import roundtrip from './__fixtures__/roundtrip.json';
import { preserveSource, splitBlocks } from './source-blocks';

/**
 * 원문 보존 — moai-works 의 실제 글로 시험한다(source-blocks.test.ts 는 위키에서 가져온 시험).
 *
 * 시험 자료 __fixtures__/roundtrip.json = [{ source, original, editor }]
 *   original: moai-works · moai-wiki 프로젝트의 실제 일감 설명 · 댓글(MCP search_issues · get_issue 로 모음, corpus.json)과
 *             어려운 경우를 일부러 만든 글(synthetic.json — `*` 목록 · `1)` 번호 · 정렬한 표 · 밑줄 강조 · setext 제목 · ~~~ 코드 ·
 *             줄 끝 공백 줄바꿈 · `<` · HTML · 참조 링크 · 할 일 · 첨부 그림 · 앞뒤 빈 줄 · 끝 줄바꿈 없음 …)
 *   editor:   그 글을 일감 편집기(crepe-setup.ts 와 같은 설정)에 열자마자 꺼낸 글(getMarkdown) = baseline.
 *             대부분 원문과 글자가 다르다(목록 기호 · 표 칸 맞춤 · 번호 기호 …) — 보존 함수 없이 저장하면 모두 바뀐다.
 * 다시 만들기: 개발 서버(bin/startup.sh)의 https://localhost:11003/dev/editor-roundtrip.html 에서 window.roundTrip(원문)을
 *   corpus.json · synthetic.json 의 글마다 불러 짝으로 적는다(ego-browser 로 한 번에). 편집기 판(@milkdown/*)을 올리면 다시 만든다.
 */
interface Pair {
  source: 'real' | 'synthetic';
  original: string;
  editor: string;
}
const pairs = roundtrip as Pair[];

/** 자리로 짝지을 수 있다 — 덩어리 수가 같거나, 원문의 참조 링크 정의 줄(편집기가 버린다)을 빼면 같다 */
const pairable = (pair: Pair) => {
  const editor = splitBlocks(pair.editor).blocks.length;
  const source = splitBlocks(pair.original).blocks;
  return source.length === editor || source.filter((b) => b.type !== 'definition').length === editor;
};

describe('preserveSource — 실제 일감 글', () => {
  it('시험 자료가 충분하고, 보존 없이 저장하면 대부분 바뀐다', () => {
    expect(pairs.filter((p) => p.source === 'real').length).toBeGreaterThanOrEqual(30);
    expect(pairs.filter((p) => p.source === 'synthetic').length).toBeGreaterThanOrEqual(15);
    expect(pairs.filter((p) => p.editor !== p.original).length).toBeGreaterThan(pairs.length * 0.9);
  });

  it.each(pairs.map((p, i) => [i, p.source, p] as const))('%i (%s) 열자마자 저장하면 원문과 한 글자도 다르지 않다', (_i, _source, pair) => {
    expect(preserveSource(pair.original, pair.editor, pair.editor)).toBe(pair.original);
  });

  it('실제 글은 모두 자리로 짝지을 수 있다(편집기가 덩어리를 합치거나 쪼개지 않는다)', () => {
    expect(pairs.filter((p) => p.source === 'real' && !pairable(p))).toEqual([]);
  });

  it('어려운 경우도 모두 자리로 짝지을 수 있다 — 참조 링크는 편집기가 정의 줄([이름]: 주소)을 버리지만 빼고 세면 맞다', () => {
    expect(pairs.filter((p) => !pairable(p))).toEqual([]);
    const reference = pairs.find((p) => p.original.includes('[ref]: https://example.com/ref'));
    expect(reference && splitBlocks(reference.editor).blocks.length).toBe(splitBlocks(reference?.original ?? '').blocks.length - 1);
  });

  /** 끝에 글을 덧붙여도 덩어리 모양이 그대로인 것 — 표 · 코드 · HTML 은 끝에 붙이면 모양이 깨진다 */
  const appendable = (text: string) => !/^(\||```|~~~| {4}|<)/.test(text);
  const editable = pairs
    .map((p, i) => [i, p] as const)
    .filter(([, p]) => {
      const blocks = splitBlocks(p.editor).blocks;
      const last = blocks[blocks.length - 1];
      return pairable(p) && blocks.length >= 2 && !!last && appendable(last.text);
    });

  it('고쳐 볼 자료가 충분하다', () => {
    expect(editable.length).toBeGreaterThanOrEqual(25);
  });

  it.each(editable)('%i 마지막 덩어리 하나를 고치면 그 덩어리만 바뀐다(앞 덩어리 · 빈 줄 · 끝 줄바꿈은 원문 그대로)', (_i, pair) => {
    const base = splitBlocks(pair.editor);
    const last = base.blocks[base.blocks.length - 1];
    const source = splitBlocks(pair.original);
    // 원문의 마지막 내용 덩어리 — 그 뒤의 참조 링크 정의 줄은 그대로 남는다
    const lastIndex = source.blocks.findLastIndex((b) => b.type !== 'definition');
    const sourceLast = source.blocks[lastIndex];
    if (!last || !sourceLast) {
      throw new Error('no block');
    }
    const changed = `${last.text} 고침`;
    const current = pair.editor.slice(0, pair.editor.length - base.tail.length - last.text.length) + changed + base.tail;
    const start = source.blocks.slice(0, lastIndex).reduce((at, b) => at + b.before.length + b.text.length, 0) + sourceLast.before.length;
    const expected = pair.original.slice(0, start) + changed + pair.original.slice(start + sourceLast.text.length);
    expect(preserveSource(pair.original, pair.editor, current)).toBe(expected);
  });
});
