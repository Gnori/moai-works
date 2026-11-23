import { Fragment, type Node } from '@milkdown/kit/prose/model';

/**
 * 바뀐 맨 위 덩어리만 다시 적기(일감 #16) — 편집기가 문서 전체를 한 번에 Markdown 으로 적은 글과 **한 글자도 다르지 않은** 글을,
 * 손대지 않은 맨 위 마디는 기억해 둔 글로 이어 만든다. 3만 자 문서를 한 번 적는 데 수십 ms 가 드는데, 쓰다 멈출 때마다 그러지 않게.
 *
 * 어떻게:
 *   - ProseMirror 마디는 바뀌지 않는 값이라 손대지 않은 맨 위 마디는 트랜잭션을 넘어 같은 객체다. 맨 위 마디 묶음마다 적은 글을
 *     WeakMap(첫 마디 → 묶음 · 글)으로 기억하고, 처음 보는 묶음만 그 묶음만 든 임시 문서로 적는다(같은 적기 함수 serializerCtx).
 *   - 이음은 편집기(mdast-util-to-markdown)의 규칙과 같다: 맨 위 덩어리 사이는 빈 줄 하나(`\n\n`), 다 이은 글이 비어 있지 않고
 *     줄바꿈으로 끝나지 않으면 끝에 `\n`. 글 끝의 빈 문단(입력 자리)은 적지 않는다(Milkdown doc 의 toMarkdown).
 *   - 묶음의 글은 그 묶음 뒤에 표지 문단(`x`)을 붙여 적고 표지를 떼어 얻는다 — 혼자 적으면 끝 줄바꿈을 적기 함수가 붙인 것인지
 *     덩어리 글의 것인지(줄 끝 강제 줄바꿈) 가를 수 없어서. 표지가 빈 줄 하나로 이어지지 않으면 규칙이 바뀐 것이니 전체를 적는다.
 *
 * 붙어 적히는 이웃(따로 적어 이으면 다시 읽을 때 모양이 달라진다) — 한 묶음으로 함께 적는다:
 *   - 목록 뒤 목록: 편집기는 이어진 목록의 기호를 번갈아 쓴다(`-` 다음 `*`, `1.` 다음 `1)`). 따로 적으면 모두 `-` 가 되어
 *     다시 읽을 때 한 목록으로 합쳐진다(moai-wiki 세션이 짚은 함정). 이어진 목록 전체가 한 묶음이라 앞 목록을 지우면 뒤 기호도 다시 적는다.
 *   - 목록 · 코드 뒤 코드: 들여 쓴 코드로 적는 설정(fences: false)이면 편집기가 사이에 `<!---->` 를 넣는다. 지금 설정은 늘 ``` 로
 *     적지만 이음 규칙이 이 짝에만 있어 함께 묶어 둔다.
 *   - 그 밖의 짝은 편집기의 이음 규칙(join)이 맨 위에서 늘 빈 줄 하나다 — 시험 자료 55건과 시험(block-markdown.test.ts)으로 확인한다.
 * 모르는 맨 위 마디가 있으면 묶음을 믿지 않고 문서 전체를 한 번에 적는다.
 *
 * 편집기 하나에 하나씩 만든다(기억은 그 편집기의 적기 함수 · schema 에만 맞다).
 */

/** 편집기가 문서를 Markdown 으로 적는 함수(serializerCtx) */
export type Serialize = (doc: Node) => string;

/** 마지막으로 적은 때의 수 — 시험 · 측정용 */
export interface BlockStats {
  /** 묶음 수 */
  groups: number;
  /** 기억에 없어 새로 적은 묶음 수 */
  written: number;
  /** 묶음을 믿지 않고 문서 전체를 한 번에 적었다 */
  whole: boolean;
}

export interface BlockSerializer {
  (doc: Node): string;
  readonly stats: BlockStats;
}

/** 맨 위에 올 수 있고, 적으면 덩어리(flow) 하나가 되는 마디 — 이 밖의 마디가 있으면 전체를 적는다 */
const FLOW = new Set(['paragraph', 'heading', 'blockquote', 'code_block', 'hr', 'bullet_list', 'ordered_list', 'table', 'footnote_definition']);
const LISTS = new Set(['bullet_list', 'ordered_list']);

/** 앞 마디와 함께 적어야 하는가(붙어 적히는 이웃) */
function sticks(previous: Node, next: Node): boolean {
  const before = previous.type.name;
  const after = next.type.name;
  return (LISTS.has(before) && LISTS.has(after)) || (after === 'code_block' && (LISTS.has(before) || before === 'code_block'));
}

interface Piece {
  nodes: readonly Node[];
  text: string;
}

export function createBlockSerializer(serialize: Serialize): BlockSerializer {
  const memory = new WeakMap<Node, Piece>();
  const stats: BlockStats = { groups: 0, written: 0, whole: false };
  /** 표지 문단과 그것을 혼자 적은 글 앞에 빈 줄을 붙인 것 — schema 가 처음 보일 때 만든다 */
  let marker: { node: Node; suffix: string } | null = null;

  const whole = (doc: Node) => {
    stats.whole = true;
    return serialize(doc);
  };

  /** 묶음의 글 — 기억에 있으면 그것, 없으면 적어서 기억한다. 표지가 맞지 않으면 null */
  const pieceOf = (doc: Node, group: Node[]): string | null => {
    const first = group[0];
    if (!first || !marker) {
      return null;
    }
    const known = memory.get(first);
    if (known && known.nodes.length === group.length && known.nodes.every((node, index) => node === group[index])) {
      return known.text;
    }
    const out = serialize(doc.copy(Fragment.from([...group, marker.node])));
    if (!out.endsWith(marker.suffix)) {
      return null;
    }
    const text = out.slice(0, out.length - marker.suffix.length);
    memory.set(first, { nodes: group, text });
    stats.written++;
    return text;
  };

  const run = (doc: Node): string => {
    stats.groups = 0;
    stats.written = 0;
    stats.whole = false;
    if (!marker) {
      const paragraph = doc.type.schema.nodes.paragraph;
      if (!paragraph) {
        return whole(doc);
      }
      const node = paragraph.create(null, doc.type.schema.text('x'));
      marker = { node, suffix: `\n\n${serialize(doc.copy(Fragment.from(node)))}` };
    }
    const children: Node[] = [];
    doc.forEach((child) => children.push(child));
    // 글 끝의 빈 문단은 입력 자리라 편집기가 적지 않는다
    const last = children.at(-1);
    if (last && last.type.name === 'paragraph' && last.content.size === 0) {
      children.pop();
    }
    if (children.some((child) => !FLOW.has(child.type.name))) {
      return whole(doc);
    }
    const texts: string[] = [];
    let start = 0;
    for (let index = 1; index <= children.length; index++) {
      const previous = children[index - 1];
      const next = children[index];
      if (previous && next && sticks(previous, next)) {
        continue;
      }
      const text = pieceOf(doc, children.slice(start, index));
      if (text === null) {
        return whole(doc);
      }
      texts.push(text);
      start = index;
    }
    stats.groups = texts.length;
    const out = texts.join('\n\n');
    return out && !/[\n\r]$/.test(out) ? `${out}\n` : out;
  };

  return Object.assign(run, { stats });
}
