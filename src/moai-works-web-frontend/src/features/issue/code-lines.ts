/**
 * 코드 블록을 줄마다 나누기(rehype 플러그인) — rehype-highlight 뒤에 돈다.
 *
 * 색을 입힌 <code> 안은 여러 겹의 span 과 줄바꿈이 섞인 글이다. 이것을 한 줄에 하나씩 나눈다:
 *
 *   <span class="code-line [code-line-mark] [code-line-add | code-line-del]">
 *     <span class="code-ln" aria-hidden="true" data-line="3"></span>   ← 줄 번호. 글자가 없다 — CSS 가 data-line 을 그린다
 *     <span class="code-src">…색 입힌 글…\n</span>
 *   </span>
 *
 * - 여러 줄에 걸친 토큰(긴 주석 · 여러 줄 문자열)은 줄마다 같은 span 을 다시 열어 색이 이어진다.
 * - 글자는 그대로다. 줄바꿈도 각 줄 끝에 남기므로 <code> 의 글(복사 단추 · 골라서 복사)이 원래와 같다. CRLF · CR 은 \n 하나로.
 *   줄 번호는 글자가 아니라서 복사되지 않고, 화면 읽기 프로그램도 읽지 않는다.
 * - 줄 번호 · 줄 바꿈 · 접기는 CodeBlock 이 상자의 class 로 켜고 끈다 — 나눈 마디는 그대로다.
 */
import { codeLanguage, type CodeNode } from './code-blocks';
import { isMarked, parseCodeMeta, type LineRange } from './code-meta';

/** 이만큼 줄이 있으면 처음부터 줄 번호를 보인다 */
export const LINE_NUMBERS_FROM = 4;
/** 이보다 길면 접어 둔다 */
export const COLLAPSE_OVER = 30;
/** 접었을 때 보이는 줄 수 */
export const COLLAPSED_LINES = 20;

/** 이 언어는 줄 맨 앞의 + · - 를 더한 줄 · 뺀 줄로 칠한다 */
const DIFF_LANGUAGES = new Set(['diff', 'patch']);
const LINE_BREAK = /\r\n|\r|\n/;

export interface CodeLinesOptions {
  /** 나누지 않을 언어(그림으로 그리는 블록) */
  skip?: readonly string[];
}

export function rehypeCodeLines(options: CodeLinesOptions = {}) {
  const skip = new Set((options.skip ?? []).map((name) => name.toLowerCase()));
  const visit = (node: CodeNode): void => {
    if (node.type === 'element' && node.tagName === 'pre') {
      const code = node.children?.find((child) => child.type === 'element' && child.tagName === 'code');
      if (code) {
        const language = codeLanguage(node);
        if (!language || !skip.has(language)) {
          code.children = codeLines(code.children ?? [], {
            diff: language !== null && DIFF_LANGUAGES.has(language),
            marked: parseCodeMeta(code.data?.meta).marked,
          });
        }
        return;
      }
    }
    node.children?.forEach(visit);
  };
  return (tree: CodeNode) => visit(tree);
}

/** 줄 마디를 만든다 — 줄 번호 · 강조 · 더한 줄 / 뺀 줄 표시를 붙인다 */
export function codeLines(children: readonly CodeNode[], options: { diff?: boolean; marked?: readonly LineRange[] } = {}): CodeNode[] {
  const { lines, endsWithBreak } = splitCodeLines(children);
  return lines.map((content, index) => {
    const number = index + 1;
    const classes = ['code-line'];
    if (options.marked && isMarked(number, options.marked)) {
      classes.push('code-line-mark');
    }
    const change = options.diff ? diffChange(textOf(content)) : null;
    if (change) {
      classes.push(change);
    }
    const terminated = index < lines.length - 1 || endsWithBreak;
    return element('span', { className: classes }, [
      element('span', { className: ['code-ln'], ariaHidden: 'true', dataLine: number }, []),
      element('span', { className: ['code-src'] }, terminated ? [...content, text('\n')] : content),
    ]);
  });
}

export interface SplitLines {
  /** 줄마다 그 줄의 마디(줄바꿈 없이) */
  lines: CodeNode[][];
  /** 글이 줄바꿈으로 끝났나 — 그 뒤의 빈 줄은 줄로 세지 않는다 */
  endsWithBreak: boolean;
}

/**
 * 색 입힌 마디를 줄로 나눈다. 줄바꿈이 span 안에 있으면 그 줄에서 span 을 닫고 다음 줄에서 같은 span(같은 class)을 다시 연다.
 * 맨 끝 줄바꿈 뒤의 빈 줄 하나는 버린다(Markdown 의 코드 글은 늘 줄바꿈으로 끝난다).
 */
export function splitCodeLines(children: readonly CodeNode[]): SplitLines {
  const lines: CodeNode[][] = [[]];
  /** 지금 줄에 열어 둔 span — 원래 마디와 이 줄의 사본 */
  let open: { source: CodeNode; copy: CodeNode }[] = [];

  const target = (): CodeNode[] => {
    const deepest = open[open.length - 1];
    return deepest ? (deepest.copy.children ??= []) : (lines[lines.length - 1] ?? []);
  };

  const place = (node: CodeNode, ancestors: readonly CodeNode[]) => {
    let same = 0;
    while (same < open.length && same < ancestors.length && open[same]?.source === ancestors[same]) {
      same += 1;
    }
    open = open.slice(0, same);
    for (const source of ancestors.slice(same)) {
      const copy: CodeNode = { type: 'element', tagName: source.tagName, properties: copyProperties(source.properties), children: [] };
      target().push(copy);
      open.push({ source, copy });
    }
    target().push(node);
  };

  const walk = (nodes: readonly CodeNode[], ancestors: readonly CodeNode[]) => {
    for (const node of nodes) {
      if (node.type === 'text') {
        (node.value ?? '').split(LINE_BREAK).forEach((part, index) => {
          if (index > 0) {
            lines.push([]);
            open = [];
          }
          if (part) {
            place(text(part), ancestors);
          }
        });
      } else if (node.type === 'element') {
        walk(node.children ?? [], [...ancestors, node]);
      }
    }
  };
  walk(children, []);

  const endsWithBreak = lines.length > 1 && lines[lines.length - 1]?.length === 0;
  if (endsWithBreak) {
    lines.pop();
  }
  return { lines, endsWithBreak };
}

/** 줄 수 — 코드 글(맨 끝 줄바꿈 하나를 뗀 것, codeText)로 센다. 나눈 줄 수와 같다 */
export function countLines(code: string): number {
  return code.split(LINE_BREAK).length;
}

/** diff 의 한 줄 — + 로 시작하면 더한 줄, - 면 뺀 줄. 파일 머리(+++ · ---)는 아니다 */
export function diffChange(line: string): 'code-line-add' | 'code-line-del' | null {
  if (line.startsWith('+') && !line.startsWith('+++')) {
    return 'code-line-add';
  }
  if (line.startsWith('-') && !line.startsWith('---')) {
    return 'code-line-del';
  }
  return null;
}

function textOf(nodes: readonly CodeNode[]): string {
  return nodes.map((node) => (node.type === 'text' ? (node.value ?? '') : textOf(node.children ?? []))).join('');
}

function copyProperties(properties: Record<string, unknown> | undefined): Record<string, unknown> {
  const copy: Record<string, unknown> = {};
  for (const [key, value] of Object.entries(properties ?? {})) {
    copy[key] = Array.isArray(value) ? [...(value as unknown[])] : value;
  }
  return copy;
}

function element(tagName: string, properties: Record<string, unknown>, children: CodeNode[]): CodeNode {
  return { type: 'element', tagName, properties, children };
}

function text(value: string): CodeNode {
  return { type: 'text', value };
}
