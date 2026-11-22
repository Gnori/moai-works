/**
 * Markdown 을 화면에 그리기 전에 손보는 것(remark 플러그인) — 일감 설명 · 댓글에 쓴다.
 *
 *   ## 제목 {#anchor}   → 제목에서 {#anchor} 를 떼고 그 제목의 id 로 삼는다 (섹션 링크가 여기로 온다)
 *   #123                → 같은 프로젝트의 일감 123 으로 가는 링크 (issuePath 를 줄 때만)
 *   <br>                → 줄바꿈 (HTML 은 꺼 두었으므로 이것만 따로 살린다)
 *
 * 그 밖의 HTML 은 글자 그대로 보인다(<KOSPI200> 같은 꺾쇠 글자가 사라지지 않는다).
 * 링크 · 코드 안의 글은 손대지 않는다(`#123` 을 코드로 적으면 그대로 보인다).
 */

/** mdast 마디 — 여기서 쓰는 것만 */
interface MdNode {
  type: string;
  value?: string;
  url?: string;
  title?: string;
  children?: MdNode[];
  data?: { hName?: string; hProperties?: Record<string, unknown> };
}

export interface IssueMarkdownOptions {
  /** 일감 번호를 화면 주소로 바꾼다. 주지 않으면 #123 을 글자 그대로 둔다 */
  issuePath?: (number: number) => string;
}

const ANCHOR = /[ \t]*\{#([a-z][a-z0-9]*(?:-[a-z0-9]+)*)\}[ \t]*$/;
const LINE_BREAK = /^<br\s*\/?>$/i;
/** 글자 · 숫자 · & 바로 뒤의 # 는 일감 번호가 아니다(C#1 · &#123;) */
const ISSUE_REF = /(^|[^\p{L}\p{N}&#/])#(\d{1,9})(?![\p{L}\p{N}])/gu;
/** 이 안의 글은 손대지 않는다 */
const SKIP = new Set(['link', 'linkReference', 'code', 'inlineCode']);

export function remarkIssueText(options: IssueMarkdownOptions = {}) {
  const visit = (node: MdNode): void => {
    if (node.type === 'heading') {
      liftAnchor(node);
    }
    if (!node.children) {
      return;
    }
    const next: MdNode[] = [];
    for (const child of node.children) {
      if (child.type === 'html' && LINE_BREAK.test((child.value ?? '').trim())) {
        next.push({ type: 'break' });
      } else if (child.type === 'text' && options.issuePath && child.value?.includes('#')) {
        next.push(...issueRefs(child.value, options.issuePath));
      } else {
        if (!SKIP.has(child.type)) {
          visit(child);
        }
        next.push(child);
      }
    }
    node.children = next;
  };
  return (tree: MdNode) => visit(tree);
}

/** 제목 끝의 {#anchor} 를 떼어 그 제목의 id 로 삼는다 */
function liftAnchor(heading: MdNode): void {
  const last = heading.children?.at(-1);
  if (last?.type !== 'text' || last.value === undefined) {
    return;
  }
  const match = ANCHOR.exec(last.value);
  if (!match) {
    return;
  }
  last.value = last.value.slice(0, match.index);
  heading.data = { ...heading.data, hProperties: { ...heading.data?.hProperties, id: match[1] } };
}

/** 글 속의 #123 을 일감 링크로 나눈다 */
function issueRefs(text: string, issuePath: (number: number) => string): MdNode[] {
  const nodes: MdNode[] = [];
  let last = 0;
  for (const match of text.matchAll(ISSUE_REF)) {
    const lead = match[1] ?? '';
    const start = (match.index ?? 0) + lead.length;
    const number = Number(match[2]);
    if (start > last) {
      nodes.push({ type: 'text', value: text.slice(last, start) });
    }
    nodes.push({
      type: 'link',
      url: issuePath(number),
      data: { hProperties: { className: 'issue-ref' } },
      children: [{ type: 'text', value: `#${number}` }],
    });
    last = start + 1 + (match[2]?.length ?? 0);
  }
  if (last < text.length) {
    nodes.push({ type: 'text', value: text.slice(last) });
  }
  return nodes.length > 0 ? nodes : [{ type: 'text', value: text }];
}
