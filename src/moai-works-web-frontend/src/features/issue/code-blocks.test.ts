import { codeLanguage, codeText, type CodeNode } from './code-blocks';

const text = (value: string): CodeNode => ({ type: 'text', value });
const element = (tagName: string, className: string[] | undefined, children: CodeNode[]): CodeNode => ({
  type: 'element',
  tagName,
  properties: className ? { className } : {},
  children,
});
const pre = (className: string[] | undefined, children: CodeNode[]) => element('pre', undefined, [element('code', className, children)]);

describe('본문의 코드 블록 읽기', () => {
  it('적힌 언어를 읽는다 — 코드 강조가 붙인 다른 이름은 건너뛴다', () => {
    expect(codeLanguage(pre(['hljs', 'language-TypeScript'], [text('const a = 1;\n')]))).toBe('typescript');
    expect(codeLanguage(pre(['language-mermaid'], [text('flowchart TD\n')]))).toBe('mermaid');
  });

  it('언어를 적지 않았거나 코드 블록이 아니면 null', () => {
    expect(codeLanguage(pre(undefined, [text('그냥 글\n')]))).toBeNull();
    expect(codeLanguage(pre(['language-'], [text('x')]))).toBeNull();
    expect(codeLanguage(undefined)).toBeNull();
    expect(codeLanguage(element('pre', undefined, [text('code 가 없다')]))).toBeNull();
  });

  it('강조로 여러 겹이 된 코드에서도 글자를 그대로 모으고, 맨 끝 줄바꿈 하나만 뗀다', () => {
    const highlighted = pre(
      ['hljs', 'language-js'],
      [element('span', ['hljs-keyword'], [text('const')]), text(' a = '), element('span', ['hljs-number'], [text('1')]), text(';\n\n')],
    );
    expect(codeText(highlighted)).toBe('const a = 1;\n');
    expect(codeText(undefined)).toBe('');
  });
});
