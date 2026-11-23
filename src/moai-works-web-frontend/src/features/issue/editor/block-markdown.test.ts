import { editorViewCtx, parserCtx, serializerCtx } from '@milkdown/kit/core';
import { closeHistory, undo } from '@milkdown/kit/prose/history';
import { Fragment, Schema, type Node } from '@milkdown/kit/prose/model';
import { canJoin } from '@milkdown/kit/prose/transform';
import type { EditorView } from '@milkdown/kit/prose/view';
import { afterAll, beforeAll, describe, expect, it, vi } from 'vitest';

import roundtrip from './__fixtures__/roundtrip.json';
import { createBlockSerializer } from './block-markdown';
import { createCrepe, startCrepe } from './crepe-setup';
import { createPreserver, preserveSource } from './source-blocks';

/**
 * 바뀐 맨 위 덩어리만 다시 적기(일감 #16) — 실제 편집기(crepe-setup.ts 와 같은 설정)를 jsdom 에서 띄워,
 * 덩어리로 나눠 적은 글이 편집기가 문서 전체를 한 번에 적은 글(serializerCtx = getMarkdown)과 한 글자도 다르지 않은지 본다.
 * 따로 적어 이으면 다시 읽을 때 모양이 달라지는 이웃(moai-wiki 세션이 짚은 붙은 목록 함정)을 먼저 박는다.
 */

interface Pair {
  source: 'real' | 'synthetic';
  original: string;
  editor: string;
}
const pairs = roundtrip as Pair[];

// jsdom 에는 IntersectionObserver 가 없다 — 코드 블록(CodeMirror) 마디 보기가 만들 때 부른다
class NoIntersectionObserver {
  observe() {}
  unobserve() {}
  disconnect() {}
  takeRecords() {
    return [];
  }
}
beforeAll(() => {
  vi.stubGlobal('IntersectionObserver', NoIntersectionObserver);
});
afterAll(() => {
  vi.unstubAllGlobals();
});

interface Opened {
  view: EditorView;
  /** 편집기가 문서 전체를 한 번에 적는 함수(getMarkdown 과 같다) */
  serialize: (doc: Node) => string;
  /** 편집기의 Markdown 해석기 — 적은 글을 다시 읽어 모양을 본다 */
  parse: (markdown: string) => Node;
  close: () => Promise<void>;
}

async function open(markdown: string): Promise<Opened> {
  const root = document.createElement('div');
  document.body.append(root);
  const crepe = createCrepe({ root, value: markdown });
  await startCrepe(crepe);
  const ctx = crepe.editor.ctx;
  return {
    view: ctx.get(editorViewCtx),
    serialize: ctx.get(serializerCtx),
    parse: (text) => ctx.get(parserCtx)(text) as Node,
    close: async () => {
      await crepe.destroy();
      root.remove();
    },
  };
}

/** 글자가 든 덩어리(문단 · 제목 · 코드 …)마다 [내용 시작, 내용 끝] */
function textblocks(doc: Node): { from: number; to: number }[] {
  const found: { from: number; to: number }[] = [];
  doc.descendants((node, pos) => {
    if (node.isTextblock) {
      found.push({ from: pos + 1, to: pos + 1 + node.content.size });
      return false;
    }
    return true;
  });
  return found;
}

/** 맨 위 덩어리마다 [자리, 마디] */
function topLevel(doc: Node): { pos: number; node: Node }[] {
  const found: { pos: number; node: Node }[] = [];
  doc.forEach((node, pos) => found.push({ pos, node }));
  return found;
}

/** 한 번의 고침 — 되돌리기가 고침마다 따로 되게 이력을 끊는다 */
function edit(view: EditorView, change: (tr: EditorView['state']['tr']) => void) {
  const tr = view.state.tr;
  change(tr);
  view.dispatch(closeHistory(tr));
}

/** 다시 읽었을 때 맨 위 목록의 수 */
function listCount(doc: Node): number {
  return topLevel(doc).filter(({ node }) => node.type.name === 'bullet_list' || node.type.name === 'ordered_list').length;
}

/** 맨 위 덩어리마다 따로 적어 빈 줄로 잇기 — 이 함정을 그대로 밟는 순진한 방법(시험이 함정을 잡는지 보이려고) */
function naive(opened: Opened): string {
  const doc = opened.view.state.doc;
  const parts = topLevel(doc)
    .filter(({ node }, index, all) => !(index === all.length - 1 && node.type.name === 'paragraph' && node.content.size === 0))
    .map(({ node }) => opened.serialize(doc.copy(Fragment.from(node))).replace(/\n$/, ''));
  return `${parts.join('\n\n')}\n`;
}

describe('createBlockSerializer — 시험 자료 55건', () => {
  it.each(pairs.map((p, i) => [i, p.source, p] as const))('%i (%s) 열자마자 덩어리로 적은 글 = 통째로 적은 글 = 시험 자료의 편집기 글', async (_i, _source, pair) => {
    const opened = await open(pair.original);
    const blocks = createBlockSerializer(opened.serialize);
    const doc = opened.view.state.doc;
    expect(opened.serialize(doc)).toBe(pair.editor);
    expect(blocks(doc)).toBe(pair.editor);
    expect(blocks.stats.whole).toBe(false);
    await opened.close();
  });

  it.each(pairs.map((p, i) => [i, p.source, p] as const))('%i (%s) 앞 · 가운데 · 끝을 고치고, 덩어리를 지우고 합치고, 되돌려도 통째로 적은 글과 같다', async (_i, _source, pair) => {
    const opened = await open(pair.original);
    const { view } = opened;
    const blocks = createBlockSerializer(opened.serialize);
    const same = (step: string) => expect(blocks(view.state.doc), step).toBe(opened.serialize(view.state.doc));
    same('처음');
    let steps = 0;
    const firstBlock = textblocks(view.state.doc)[0];
    if (firstBlock) {
      edit(view, (tr) => tr.insertText('앞 ', firstBlock.from));
      steps++;
      same('앞');
    }
    const all = textblocks(view.state.doc);
    const middle = all[Math.floor(all.length / 2)];
    if (middle) {
      edit(view, (tr) => tr.insertText(' 가운데 ', Math.floor((middle.from + middle.to) / 2)));
      steps++;
      same('가운데');
    }
    const lastBlock = textblocks(view.state.doc).at(-1);
    if (lastBlock) {
      edit(view, (tr) => tr.insertText(' 끝', lastBlock.to));
      steps++;
      same('끝');
    }
    const children = topLevel(view.state.doc);
    const victim = children[Math.floor(children.length / 2)];
    if (children.length >= 3 && victim) {
      edit(view, (tr) => tr.delete(victim.pos, victim.pos + victim.node.nodeSize));
      steps++;
      same('지움');
    }
    // 이웃한 두 문단 · 두 목록 · 두 인용을 하나로 합친다(표 · 코드는 합치면 모양이 깨져 ProseMirror 가 거절한다)
    const joinable = new Set(['paragraph', 'bullet_list', 'ordered_list', 'blockquote']);
    const boundary = topLevel(view.state.doc)
      .slice(1)
      .find(({ pos, node }) => joinable.has(node.type.name) && view.state.doc.resolve(pos).nodeBefore?.type === node.type && canJoin(view.state.doc, pos));
    if (boundary) {
      edit(view, (tr) => tr.join(boundary.pos));
      steps++;
      same('합침');
    }
    for (let k = 0; k < steps; k++) {
      undo(view.state, view.dispatch);
      same(`되돌림 ${k + 1}`);
    }
    expect(blocks(view.state.doc)).toBe(pair.editor);
    await opened.close();
  });
});

describe('붙어 적히는 이웃 — 따로 적어 이으면 다시 읽을 때 모양이 달라지는 경우', () => {
  it.each([
    ['`-` 목록 뒤 `*` 목록', '- 하나\n- 둘\n\n* 셋\n* 넷\n'],
    ['`1.` 번호 뒤 `1)` 번호', '1. 하나\n2. 둘\n\n1) 셋\n2) 넷\n'],
    ['목록 넷이 이어짐(기호가 번갈아 든다)', '- 하나\n\n* 둘\n\n- 셋\n\n* 넷\n'],
    ['할 일 목록 뒤 목록', '- [ ] 할 일\n- [x] 한 일\n\n* 보통\n'],
  ])('%s — 하나만 고쳐도 통째로 적은 글과 같고, 다시 읽으면 목록 수가 그대로다', async (_name, markdown) => {
    const opened = await open(markdown);
    const { view } = opened;
    const lists = listCount(view.state.doc);
    expect(lists).toBeGreaterThanOrEqual(2);
    // 함정이 진짜다 — 덩어리마다 따로 적어 이으면 기호가 모두 같아져 다시 읽을 때 목록이 합쳐진다
    expect(listCount(opened.parse(naive(opened)))).toBeLessThan(lists);
    const blocks = createBlockSerializer(opened.serialize);
    blocks(view.state.doc);
    for (const target of [textblocks(view.state.doc)[0], textblocks(view.state.doc).at(-1)]) {
      if (!target) {
        throw new Error('no text');
      }
      edit(view, (tr) => tr.insertText(' 고침', target.to));
      const out = blocks(view.state.doc);
      expect(out).toBe(opened.serialize(view.state.doc));
      expect(listCount(opened.parse(out))).toBe(lists);
    }
    await opened.close();
  });

  it('글머리 뒤 번호 — 기호 종류가 달라 따로 적어도 합쳐지지 않지만 같은 규칙으로 함께 적는다', async () => {
    const opened = await open('- 하나\n\n1. 둘\n');
    const blocks = createBlockSerializer(opened.serialize);
    blocks(opened.view.state.doc);
    edit(opened.view, (tr) => tr.insertText(' 고침', textblocks(opened.view.state.doc)[0]?.to ?? 1));
    expect(blocks(opened.view.state.doc)).toBe(opened.serialize(opened.view.state.doc));
    await opened.close();
  });

  it('목록 사이 문단을 지우면 두 목록이 붙는다 — 뒤 목록의 기호가 바뀐다', async () => {
    const opened = await open('- 하나\n\n사이 문단\n\n- 둘\n');
    const { view } = opened;
    const blocks = createBlockSerializer(opened.serialize);
    expect(blocks(view.state.doc)).toBe('- 하나\n\n사이 문단\n\n- 둘\n');
    const middle = topLevel(view.state.doc)[1];
    if (!middle) {
      throw new Error('no middle');
    }
    edit(view, (tr) => tr.delete(middle.pos, middle.pos + middle.node.nodeSize));
    const out = blocks(view.state.doc);
    expect(out).toBe(opened.serialize(view.state.doc));
    expect(out).toBe('- 하나\n\n* 둘\n');
    expect(listCount(opened.parse(out))).toBe(2);
    await opened.close();
  });

  it('앞 목록을 지우면 뒤 목록들의 기호가 한 칸씩 당겨진다', async () => {
    const opened = await open('- 하나\n\n* 둘\n\n- 셋\n');
    const { view } = opened;
    const blocks = createBlockSerializer(opened.serialize);
    blocks(view.state.doc);
    const first = topLevel(view.state.doc)[0];
    if (!first) {
      throw new Error('no first');
    }
    edit(view, (tr) => tr.delete(first.pos, first.pos + first.node.nodeSize));
    expect(blocks(view.state.doc)).toBe('- 둘\n\n* 셋\n');
    expect(blocks(view.state.doc)).toBe(opened.serialize(view.state.doc));
    undo(view.state, view.dispatch);
    expect(blocks(view.state.doc)).toBe('- 하나\n\n* 둘\n\n- 셋\n');
    await opened.close();
  });

  it('문단을 목록으로 바꿔 두 목록 사이에 끼우면 세 목록이 번갈아 적힌다', async () => {
    const opened = await open('- 하나\n\n문단\n\n* 둘\n');
    const { view } = opened;
    const { schema } = view.state;
    const blocks = createBlockSerializer(opened.serialize);
    blocks(view.state.doc);
    const middle = topLevel(view.state.doc)[1];
    if (!middle) {
      throw new Error('no middle');
    }
    const list = schema.nodes.bullet_list!.create(null, schema.nodes.list_item!.create(null, middle.node));
    edit(view, (tr) => tr.replaceWith(middle.pos, middle.pos + middle.node.nodeSize, list));
    const out = blocks(view.state.doc);
    expect(out).toBe(opened.serialize(view.state.doc));
    expect(listCount(opened.parse(out))).toBe(3);
    await opened.close();
  });

  it.each([
    ['목록 바로 뒤 코드 블록', '- 하나\n- 둘\n\n```\ncode\n```\n'],
    ['목록 뒤 들여 쓴 코드(사이에 빈 주석)', '- 하나\n\n<!---->\n\n    들여 쓴 코드\n'],
    ['코드 블록 둘이 이웃', '```\na\n```\n\n```\nb\n```\n'],
    ['빽빽한 목록 뒤 느슨한 목록', '- 하나\n- 둘\n\n1. 셋\n\n2. 넷\n'],
    ['느슨한 목록', '- 하나\n\n- 둘\n\n- 셋\n'],
    ['두 문단 · 두 인용 · 두 표가 이웃', '문단 하나\n\n문단 둘\n\n> 인용 하나\n\n> 인용 둘\n\n| a |\n|---|\n| 1 |\n\n| b |\n|---|\n| 2 |\n'],
    ['주석 · 각주', '본문[^1]\n\n[^1]: 각주 하나\n\n[^2]: 각주 둘\n'],
  ])('%s — 덩어리마다 고쳐도 통째로 적은 글과 같다', async (_name, markdown) => {
    const opened = await open(markdown);
    const { view } = opened;
    const blocks = createBlockSerializer(opened.serialize);
    expect(blocks(view.state.doc)).toBe(opened.serialize(view.state.doc));
    for (let index = 0; index < textblocks(view.state.doc).length; index++) {
      const target = textblocks(view.state.doc)[index];
      if (target) {
        edit(view, (tr) => tr.insertText('고침', target.to));
        expect(blocks(view.state.doc)).toBe(opened.serialize(view.state.doc));
      }
    }
    await opened.close();
  });

  it('빈 문단 — 가운데 빈 문단은 빈 줄로 적고, 글 끝의 빈 문단(입력 자리)은 적지 않는다', async () => {
    const opened = await open('하나\n\n둘\n');
    const { view } = opened;
    const paragraph = view.state.schema.nodes.paragraph!;
    const blocks = createBlockSerializer(opened.serialize);
    blocks(view.state.doc);
    const second = topLevel(view.state.doc)[1];
    if (!second) {
      throw new Error('no second');
    }
    edit(view, (tr) => tr.insert(second.pos, [paragraph.create(), paragraph.create()]));
    expect(blocks(view.state.doc)).toBe(opened.serialize(view.state.doc));
    edit(view, (tr) => tr.insert(tr.doc.content.size, paragraph.create()));
    expect(blocks(view.state.doc)).toBe(opened.serialize(view.state.doc));
    edit(view, (tr) => tr.insert(0, paragraph.create()));
    expect(blocks(view.state.doc)).toBe(opened.serialize(view.state.doc));
    await opened.close();
  });

  it('줄 끝 강제 줄바꿈 — 덩어리 글이 줄바꿈으로 끝나도 이음이 같다', async () => {
    const opened = await open('하나\n\n둘\n');
    const { view } = opened;
    const hardbreak = view.state.schema.nodes.hardbreak!;
    const blocks = createBlockSerializer(opened.serialize);
    blocks(view.state.doc);
    for (const target of textblocks(view.state.doc)) {
      edit(view, (tr) => tr.insert(target.to, hardbreak.create()));
      expect(blocks(view.state.doc)).toBe(opened.serialize(view.state.doc));
    }
    const first = textblocks(view.state.doc)[0];
    if (first) {
      edit(view, (tr) => tr.insert(first.from + 1, hardbreak.create()));
      expect(blocks(view.state.doc)).toBe(opened.serialize(view.state.doc));
    }
    await opened.close();
  });

  it('빈 글 · 빈 문단만 있는 글', async () => {
    const opened = await open('');
    const { view } = opened;
    const blocks = createBlockSerializer(opened.serialize);
    expect(blocks(view.state.doc)).toBe(opened.serialize(view.state.doc));
    edit(view, (tr) => tr.insert(0, view.state.schema.nodes.paragraph!.create()));
    expect(blocks(view.state.doc)).toBe(opened.serialize(view.state.doc));
    await opened.close();
  });
});

/** 브라우저에서 잰 것과 같은 3만 자 글 — 실제 일감 글을 이어 붙인다 */
function bigDocument(): string {
  const real = pairs.filter((p) => p.source === 'real').map((p) => p.original.trim());
  let doc = '';
  for (let i = 0; doc.length < 30_000; i++) {
    doc += `${real[i % real.length]}\n\n`;
  }
  return `${doc.trimEnd()}\n`;
}

describe('바뀐 덩어리만 다시 적는다', () => {
  it('3만 자 글에서 한 글자를 고치면 그 덩어리 하나만 다시 적고, 되돌려도 같다', async () => {
    const opened = await open(bigDocument());
    const { view } = opened;
    const blocks = createBlockSerializer(opened.serialize);
    blocks(view.state.doc);
    expect(blocks.stats.groups).toBeGreaterThan(150);
    expect(blocks.stats.written).toBe(blocks.stats.groups);
    // 다시 적을 것이 없으면 하나도 적지 않는다
    blocks(view.state.doc);
    expect(blocks.stats.written).toBe(0);
    const all = textblocks(view.state.doc);
    const middle = all[Math.floor(all.length / 2)];
    if (!middle) {
      throw new Error('no middle');
    }
    edit(view, (tr) => tr.insertText('가', middle.to));
    expect(blocks(view.state.doc)).toBe(opened.serialize(view.state.doc));
    expect(blocks.stats.written).toBe(1);
    undo(view.state, view.dispatch);
    expect(blocks(view.state.doc)).toBe(opened.serialize(view.state.doc));
    expect(blocks.stats.written).toBeLessThanOrEqual(1);
    await opened.close();
  });
});

describe('알 수 없는 모양은 문서 전체를 한 번에 적는다', () => {
  const schema = new Schema({
    nodes: {
      doc: { content: 'block+' },
      paragraph: { group: 'block', content: 'text*' },
      mystery: { group: 'block', content: 'text*' },
      text: {},
    },
  });
  const node = (type: 'paragraph' | 'mystery', text: string) => schema.node(type, null, text ? [schema.text(text)] : []);
  /** 덩어리를 빈 줄로 잇는 흉내 적기 */
  const joinWithBlankLine = (doc: Node) => {
    const texts: string[] = [];
    doc.forEach((child) => texts.push(child.textContent));
    return `${texts.join('\n\n')}\n`;
  };

  it('모르는 맨 위 마디가 있으면', () => {
    const serialize = vi.fn(joinWithBlankLine);
    const blocks = createBlockSerializer(serialize);
    const doc = schema.node('doc', null, [node('paragraph', '하나'), node('mystery', '둘')]);
    expect(blocks(doc)).toBe('하나\n\n둘\n');
    expect(blocks.stats.whole).toBe(true);
  });

  it('덩어리 뒤에 붙인 표지 문단이 빈 줄 하나로 이어지지 않으면(적는 규칙이 바뀌었다)', () => {
    const odd = (doc: Node) => {
      const texts: string[] = [];
      doc.forEach((child) => texts.push(child.textContent));
      return `${texts.join(' | ')}\n`;
    };
    const blocks = createBlockSerializer(odd);
    const doc = schema.node('doc', null, [node('paragraph', '하나'), node('paragraph', '둘')]);
    expect(blocks(doc)).toBe('하나 | 둘\n');
    expect(blocks.stats.whole).toBe(true);
  });

  it('흉내 적기로도 덩어리마다 적어 이은 글이 같다', () => {
    const blocks = createBlockSerializer(joinWithBlankLine);
    const doc = schema.node('doc', null, [node('paragraph', '하나'), node('paragraph', ''), node('paragraph', '둘')]);
    expect(blocks(doc)).toBe(joinWithBlankLine(doc));
    expect(blocks.stats.whole).toBe(false);
  });
});

describe('덩어리로 적은 글 + createPreserver = 통째로 적은 글 + preserveSource', () => {
  it.each(pairs.map((p, i) => [i, p.source, p] as const))('%i (%s) 고칠 때마다 같은 글을 저장한다', async (_i, _source, pair) => {
    const opened = await open(pair.original);
    const { view } = opened;
    const blocks = createBlockSerializer(opened.serialize);
    const preserve = createPreserver(pair.original, pair.editor);
    expect(preserve(blocks(view.state.doc))).toBe(pair.original);
    for (const at of ['first', 'middle', 'last'] as const) {
      const all = textblocks(view.state.doc);
      const target = at === 'first' ? all[0] : at === 'last' ? all.at(-1) : all[Math.floor(all.length / 2)];
      if (!target) {
        continue;
      }
      edit(view, (tr) => tr.insertText(' 고침', target.to));
      const full = opened.serialize(view.state.doc);
      expect(preserve(blocks(view.state.doc))).toBe(preserveSource(pair.original, pair.editor, full));
    }
    await opened.close();
  });
});
