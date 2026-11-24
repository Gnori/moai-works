import { describe, expect, it } from 'vitest';

import type { CodeNode } from './code-blocks';
import { MAX_LINE, MAX_RANGES, MAX_TITLE, codeMeta, isMarked, parseCodeMeta } from './code-meta';

describe('코드 블록 meta 읽기', () => {
  it('파일 이름과 강조할 줄 — ```ts title="src/app.ts" {1,3-5}', () => {
    expect(parseCodeMeta('title="src/app.ts" {1,3-5}')).toEqual({
      title: 'src/app.ts',
      marked: [
        { from: 1, to: 1 },
        { from: 3, to: 5 },
      ],
    });
  });

  it('따옴표는 둘 다 · 없어도 되고, 순서는 상관없다', () => {
    expect(parseCodeMeta("{2} title='설정 파일.yml'")).toEqual({ title: '설정 파일.yml', marked: [{ from: 2, to: 2 }] });
    expect(parseCodeMeta('title=Dockerfile')).toEqual({ title: 'Dockerfile', marked: [] });
    expect(parseCodeMeta('{ 4 - 6 , 8 }').marked).toEqual([
      { from: 4, to: 6 },
      { from: 8, to: 8 },
    ]);
  });

  it('제목 안의 {…} 는 줄 범위가 아니다', () => {
    expect(parseCodeMeta('title="a{1}.ts"')).toEqual({ title: 'a{1}.ts', marked: [] });
    expect(parseCodeMeta('title="a{1}.ts" {3}').marked).toEqual([{ from: 3, to: 3 }]);
  });

  it('없거나 모르는 말은 버린다', () => {
    for (const meta of [undefined, null, 42, '', '   ', 'showLineNumbers', 'mytitle="x"', 'title=""', '{}', '{a,0,-1,3-2,1-}']) {
      expect(parseCodeMeta(meta), String(meta)).toEqual({ title: null, marked: [] });
    }
  });

  it('제목은 제어 문자를 빼고 120자까지 — 글자(코드 포인트)째로 자른다', () => {
    expect(parseCodeMeta('title="a\u0000b\u001bc\u007f d"').title).toBe('a b c d');
    const long = `${'가'.repeat(MAX_TITLE - 1)}😀😀`;
    const title = parseCodeMeta(`title="${long}"`).title ?? '';
    expect(Array.from(title)).toHaveLength(MAX_TITLE);
    expect(title.endsWith('😀')).toBe(true);
  });

  it('범위는 20개 · 줄 번호는 100000 까지', () => {
    const many = Array.from({ length: 50 }, (_, i) => String(i + 1)).join(',');
    expect(parseCodeMeta(`{${many}}`).marked).toHaveLength(MAX_RANGES);
    expect(parseCodeMeta('{5-9999999}').marked).toEqual([{ from: 5, to: MAX_LINE }]);
    expect(parseCodeMeta('{200000}').marked).toEqual([]);
    expect(parseCodeMeta('{99999999}').marked).toEqual([]);
  });

  it('강조할 줄인지', () => {
    const { marked } = parseCodeMeta('{1,3-5}');
    expect([1, 2, 3, 4, 5, 6].map((line) => isMarked(line, marked))).toEqual([true, false, true, true, true, false]);
  });

  it('<pre> 마디의 code 에 붙은 data.meta 를 읽는다', () => {
    const pre: CodeNode = {
      type: 'element',
      tagName: 'pre',
      children: [{ type: 'element', tagName: 'code', children: [], data: { meta: 'title="a.ts" {2}' } }],
    };
    expect(codeMeta(pre)).toEqual({ title: 'a.ts', marked: [{ from: 2, to: 2 }] });
    expect(codeMeta(undefined)).toEqual({ title: null, marked: [] });
  });
});
