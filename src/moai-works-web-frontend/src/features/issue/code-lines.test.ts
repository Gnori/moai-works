import { createLowlight } from 'lowlight';
import { describe, expect, it } from 'vitest';

import { codeText, type CodeNode } from './code-blocks';
import { baseLanguages, highlightAliases } from './code-languages';
import { codeLines, countLines, diffChange, rehypeCodeLines, splitCodeLines } from './code-lines';

const text = (value: string): CodeNode => ({ type: 'text', value });
const span = (className: string, children: CodeNode[]): CodeNode => ({
  type: 'element',
  tagName: 'span',
  properties: { className: [className] },
  children,
});

/** 줄 마디의 글자 */
const textOf = (nodes: readonly CodeNode[]): string =>
  nodes.map((node) => (node.type === 'text' ? (node.value ?? '') : textOf(node.children ?? []))).join('');

/** 줄 마디를 [class, 글] 꼴로 — 겹은 그대로 */
const shape = (nodes: readonly CodeNode[]): unknown[] =>
  nodes.map((node) =>
    node.type === 'text' ? node.value : { [String((node.properties?.className as string[])[0])]: shape(node.children ?? []) },
  );

const lowlight = createLowlight(baseLanguages);
lowlight.registerAlias(highlightAliases);
/** 실제 강조 결과(Markdown 의 코드 글처럼 맨 끝에 줄바꿈 하나) */
const highlighted = (language: string, code: string) => lowlight.highlight(language, `${code}\n`).children as CodeNode[];

describe('코드 줄 나누기 (splitCodeLines)', () => {
  it('줄바꿈마다 나누고, 맨 끝 줄바꿈 뒤의 빈 줄은 세지 않는다', () => {
    expect(splitCodeLines([text('a\nb\n')])).toEqual({ lines: [[text('a')], [text('b')]], endsWithBreak: true });
    expect(splitCodeLines([text('a\nb')])).toEqual({ lines: [[text('a')], [text('b')]], endsWithBreak: false });
  });

  it('비어 있는 마지막 줄(글 끝의 빈 줄)은 한 줄로 남는다', () => {
    expect(splitCodeLines([text('a\n\n')])).toEqual({ lines: [[text('a')], []], endsWithBreak: true });
    expect(splitCodeLines([text('\n')])).toEqual({ lines: [[]], endsWithBreak: true });
    expect(splitCodeLines([text('')])).toEqual({ lines: [[]], endsWithBreak: false });
  });

  it('CRLF · CR 도 줄바꿈 하나로 센다', () => {
    const { lines, endsWithBreak } = splitCodeLines([text('a\r\nb\rc\r\n')]);
    expect(lines.map(textOf)).toEqual(['a', 'b', 'c']);
    expect(endsWithBreak).toBe(true);
    expect(textOf(codeLines([text('a\r\nb\r\n')]))).toBe('a\nb\n');
  });

  it('여러 줄 주석은 줄마다 같은 span 을 다시 열어 색이 이어진다(사본은 따로)', () => {
    const comment = span('hljs-comment', [text('/* 하나\n둘\n셋 */')]);
    const { lines } = splitCodeLines([text('a;\n'), comment, text('\nb;\n')]);
    expect(lines.map(shape)).toEqual([
      ['a;'],
      [{ 'hljs-comment': ['/* 하나'] }],
      [{ 'hljs-comment': ['둘'] }],
      [{ 'hljs-comment': ['셋 */'] }],
      ['b;'],
    ]);
    const [first, second] = [lines[1]?.[0], lines[2]?.[0]];
    expect(first).not.toBe(second);
    expect(first?.properties?.className).not.toBe(comment.properties?.className);
  });

  it('겹친 span(문자열 안의 치환)도 열린 겹 그대로 다음 줄에서 다시 연다', () => {
    const template = span('hljs-string', [text('`a\n'), span('hljs-subst', [text('${x}\ny')]), text('\nb`')]);
    const { lines } = splitCodeLines([text('const s = '), template, text(';\n')]);
    expect(lines.map(shape)).toEqual([
      ['const s = ', { 'hljs-string': ['`a'] }],
      [{ 'hljs-string': [{ 'hljs-subst': ['${x}'] }] }],
      [{ 'hljs-string': [{ 'hljs-subst': ['y'] }] }],
      [{ 'hljs-string': ['b`'] }, ';'],
    ]);
  });

  it('실제 강조 결과 — 여러 줄 주석 · 여러 줄 문자열의 줄마다 그 색의 span 이 있고 글은 줄 그대로다', () => {
    const source = ['/**', ' * 설명', ' */', 'const s = `첫 줄', '둘째 ${name} 줄`;', 'const n = 1;'].join('\n');
    const { lines } = splitCodeLines(highlighted('ts', source));
    expect(lines.map(textOf)).toEqual(source.split('\n'));
    const classes = (nodes: readonly CodeNode[]) => nodes.flatMap((node) => (node.properties?.className as string[] | undefined) ?? []);
    expect(classes(lines[1] ?? [])).toContain('hljs-comment');
    expect(classes(lines[2] ?? [])).toContain('hljs-comment');
    expect(classes(lines[4] ?? [])).toContain('hljs-string');
    expect(classes(lines[5] ?? [])).toContain('hljs-keyword');
  });
});

describe('줄 마디 (codeLines)', () => {
  it('줄마다 번호 칸(글자 없음 · aria-hidden)과 글 칸을 두고, 글은 줄바꿈까지 원래와 같다', () => {
    const source = 'let a = 1;\n\n// 끝\n';
    const lines = codeLines(highlighted('js', source.slice(0, -1)));
    expect(lines).toHaveLength(3);
    expect(lines[1]?.children?.[0]).toEqual({
      type: 'element',
      tagName: 'span',
      properties: { className: ['code-ln'], ariaHidden: 'true', dataLine: 2 },
      children: [],
    });
    expect(lines.map((line) => line.properties?.className)).toEqual([['code-line'], ['code-line'], ['code-line']]);
    expect(textOf(lines)).toBe(source);
  });

  it('줄바꿈으로 끝나지 않은 마지막 줄에는 줄바꿈을 붙이지 않는다', () => {
    expect(textOf(codeLines([text('a\nb')]))).toBe('a\nb');
  });

  it('강조할 줄에 code-line-mark', () => {
    const lines = codeLines([text('1\n2\n3\n4\n5\n')], { marked: [{ from: 2, to: 2 }, { from: 4, to: 9 }] });
    expect(lines.map((line) => (line.properties?.className as string[]).includes('code-line-mark'))).toEqual([
      false,
      true,
      false,
      true,
      true,
    ]);
  });

  it('diff — + 줄은 더한 줄, - 줄은 뺀 줄, 파일 머리(+++ · ---)와 나머지는 그대로', () => {
    const source = ['--- a/x.ts', '+++ b/x.ts', '@@ -1 +1 @@', '-old', '+new', ' same'].join('\n');
    const lines = codeLines(highlighted('diff', source), { diff: true });
    expect(lines.map((line) => (line.properties?.className as string[]).slice(1))).toEqual([
      [],
      [],
      [],
      ['code-line-del'],
      ['code-line-add'],
      [],
    ]);
    expect(diffChange('++x')).toBe('code-line-add');
    expect(diffChange('---')).toBeNull();
    expect(diffChange('')).toBeNull();
  });

  it('줄 수(countLines)는 나눈 줄 수와 같다', () => {
    for (const source of ['', '\n', 'a', 'a\n', 'a\n\n', 'a\nb', 'a\r\nb\r\n', 'a\n\n\nb\n']) {
      const pre: CodeNode = { type: 'element', tagName: 'pre', children: [{ type: 'element', tagName: 'code', children: codeLines([text(source)]) }] };
      expect(countLines(codeText(pre)), JSON.stringify(source)).toBe(codeLines([text(source)]).length);
    }
  });
});

describe('rehypeCodeLines', () => {
  const tree = (className: string[] | undefined, value: string, meta?: string): CodeNode => ({
    type: 'root',
    children: [
      {
        type: 'element',
        tagName: 'pre',
        properties: {},
        children: [
          { type: 'element', tagName: 'code', properties: className ? { className } : {}, children: [text(value)], data: meta ? { meta } : undefined },
        ],
      },
    ],
  });
  const code = (root: CodeNode) => root.children?.[0]?.children?.[0];

  it('코드 블록(언어가 없어도)을 줄로 나누고, meta 의 {…} 줄을 강조한다', () => {
    const root = tree(undefined, 'a\nb\nc\n', 'title="x" {2}');
    rehypeCodeLines()(root);
    const lines = code(root)?.children ?? [];
    expect(lines.map((line) => line.properties?.className)).toEqual([['code-line'], ['code-line', 'code-line-mark'], ['code-line']]);
  });

  it('그림으로 그리는 블록(skip, 대소문자 무시)은 그대로 둔다', () => {
    const root = tree(['language-Mermaid'], 'flowchart TD\nA-->B\n');
    rehypeCodeLines({ skip: ['mermaid'] })(root);
    expect(code(root)?.children).toEqual([text('flowchart TD\nA-->B\n')]);
  });

  it('diff · patch 만 + · - 줄을 칠한다(목록 같은 다른 언어의 - 는 아니다)', () => {
    const yaml = tree(['language-yaml'], '- a\n+ b\n');
    rehypeCodeLines()(yaml);
    expect((code(yaml)?.children ?? []).map((line) => line.properties?.className)).toEqual([['code-line'], ['code-line']]);
    const patch = tree(['language-patch'], '- a\n+ b\n');
    rehypeCodeLines()(patch);
    expect((code(patch)?.children ?? []).map((line) => line.properties?.className)).toEqual([
      ['code-line', 'code-line-del'],
      ['code-line', 'code-line-add'],
    ]);
  });

  it('줄 안 코드(pre 밖의 code)는 손대지 않는다', () => {
    const root: CodeNode = { type: 'root', children: [{ type: 'element', tagName: 'p', children: [{ type: 'element', tagName: 'code', children: [text('a\nb')] }] }] };
    rehypeCodeLines()(root);
    expect(root.children?.[0]?.children?.[0]?.children).toEqual([text('a\nb')]);
  });
});
