/**
 * 본문의 코드 블록 읽기 — 보기 화면이 다이어그램(```mermaid) · 차트(```chart)와 코드 상자를 가려 그릴 때 쓴다.
 *
 * react-markdown 이 넘겨주는 마디(hast)는 `<pre><code class="language-xxx">…</code></pre>` 모양이다.
 * 코드 강조를 거치면 code 안이 여러 겹의 span 이 되므로, 글자는 끝까지 내려가 모은다.
 */
export interface CodeNode {
  type: string;
  tagName?: string;
  value?: string;
  properties?: Record<string, unknown>;
  children?: CodeNode[];
}

/** 그림으로 그리는 코드 블록의 언어 이름 */
export const DIAGRAM_LANGUAGE = 'mermaid';

/** 차트로 그리는 코드 블록의 언어 이름(```chart — JSON 하나, chart-spec.ts) */
export const CHART_LANGUAGE = 'chart';

/** 편집기의 코드 블록 언어(적힌 그대로)가 이 이름인가 — 대소문자는 가리지 않는다 */
export function isLanguage(language: string | null | undefined, name: string): boolean {
  return (language ?? '').trim().toLowerCase() === name;
}

function codeElement(pre: CodeNode | undefined): CodeNode | undefined {
  return pre?.children?.find((child) => child.type === 'element' && child.tagName === 'code');
}

/** 코드 블록에 적힌 언어(```js 의 js). 적지 않았으면 null */
export function codeLanguage(pre: CodeNode | undefined): string | null {
  const className = codeElement(pre)?.properties?.className;
  const names = Array.isArray(className) ? className : typeof className === 'string' ? className.split(/\s+/) : [];
  for (const name of names) {
    if (typeof name === 'string' && name.startsWith('language-') && name.length > 'language-'.length) {
      return name.slice('language-'.length).toLowerCase();
    }
  }
  return null;
}

/** 코드 블록의 글자 그대로. 맨 끝의 줄바꿈 하나는 뗀다 */
export function codeText(pre: CodeNode | undefined): string {
  const collect = (node: CodeNode | undefined): string =>
    !node ? '' : node.type === 'text' ? (node.value ?? '') : (node.children ?? []).map(collect).join('');
  return collect(codeElement(pre)).replace(/\n$/, '');
}
