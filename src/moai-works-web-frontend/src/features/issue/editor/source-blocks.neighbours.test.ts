import { describe, expect, it } from 'vitest';

import roundtrip from './__fixtures__/roundtrip.json';
import { createPreserver, preserveSource, resplit, splitBlocks, type SourceSplit } from './source-blocks';

/**
 * 원문 보존이 원문 조각과 편집기 글을 이웃으로 붙일 때의 함정(일감 #16 수용 기준) — 빈 줄만 두고 이어진 두 덩어리가
 * 다시 읽으면 하나로 합쳐지는 경우. 원문은 `*` · `-` 처럼 다른 기호로 두 목록을 갈라 두었는데, 편집기는 자기 규칙(첫 목록 `-`, 다음 `*`)으로
 * 다시 적는다. 하나만 고치면 고친 목록은 편집기 글, 고치지 않은 목록은 원문 조각이 되어 둘 다 `-` 가 될 수 있다.
 * baseline 은 모두 실제 편집기(crepe-setup.ts)에 원문을 열자마자 꺼낸 글이다.
 */

/** 다시 읽었을 때 맨 위 덩어리의 종류 */
const shape = (markdown: string) => splitBlocks(markdown).blocks.map((block) => block.type);

describe('preserveSource — 붙어 적히는 이웃', () => {
  it('`*` 목록 뒤 `-` 목록에서 앞 목록만 고쳐도 다시 읽으면 목록이 둘이다', () => {
    const original = '* 하나\n\n- 둘\n';
    const baseline = '- 하나\n\n* 둘\n';
    const saved = preserveSource(original, baseline, '- 하나 고침\n\n* 둘\n');
    expect(shape(saved)).toEqual(['list', 'list']);
    // 이어진 목록은 편집기가 함께 적은 모양(기호가 번갈아 든다)을 쓴다
    expect(saved).toBe('- 하나 고침\n\n* 둘\n');
  });

  it('뒤 목록만 고쳐도 목록이 둘이다', () => {
    const saved = preserveSource('* 하나\n\n- 둘\n', '- 하나\n\n* 둘\n', '- 하나\n\n* 둘 고침\n');
    expect(shape(saved)).toEqual(['list', 'list']);
    expect(saved).toBe('- 하나\n\n* 둘 고침\n');
  });

  it('`1)` 번호 뒤 `1.` 번호', () => {
    const saved = preserveSource('1) 하나\n\n1. 둘\n', '1. 하나\n\n1) 둘\n', '1. 하나 고침\n\n1) 둘\n');
    expect(shape(saved)).toEqual(['list', 'list']);
    expect(saved).toBe('1. 하나 고침\n\n1) 둘\n');
  });

  it('목록 셋이 이어질 때 가운데만 고쳐도 셋이고, 뒤의 다른 덩어리는 원문 그대로다', () => {
    const original = '* 하나\n\n- 둘\n\n* 셋\n\n끝 *강조*\n';
    const baseline = '- 하나\n\n* 둘\n\n- 셋\n\n끝 *강조*\n';
    const saved = preserveSource(original, baseline, '- 하나\n\n* 둘 고침\n\n- 셋\n\n끝 *강조*\n');
    expect(shape(saved)).toEqual(['list', 'list', 'list', 'paragraph']);
    expect(saved).toBe('- 하나\n\n* 둘 고침\n\n- 셋\n\n끝 *강조*\n');
  });

  it('글머리 목록과 번호 목록이 이어지면 겹친 종류의 목록만 편집기 글로 쓴다', () => {
    // 브라우저에서 본 모양 — 글머리 둘 · 번호 둘이 이어진다. 첫 목록만 고치면 번호 목록은 원문 그대로
    const original = '* 하나\n* 둘\n\n- 셋\n- 넷\n\n1) 다섯\n\n1. 여섯\n';
    const baseline = '- 하나\n- 둘\n\n* 셋\n* 넷\n\n1. 다섯\n\n1) 여섯\n';
    const first = preserveSource(original, baseline, '- 하나 고침\n- 둘\n\n* 셋\n* 넷\n\n1. 다섯\n\n1) 여섯\n');
    expect(first).toBe('- 하나 고침\n- 둘\n\n* 셋\n* 넷\n\n1) 다섯\n\n1. 여섯\n');
    expect(shape(first)).toEqual(['list', 'list', 'list', 'list']);
    const second = preserveSource(original, baseline, '- 하나\n- 둘\n\n* 셋\n* 넷\n\n1. 다섯 고침\n\n1) 여섯\n');
    expect(second).toBe('* 하나\n* 둘\n\n- 셋\n- 넷\n\n1. 다섯 고침\n\n1) 여섯\n');
    expect(shape(second)).toEqual(['list', 'list', 'list', 'list']);
  });

  it('기호가 겹치지 않으면 원문 조각을 그대로 둔다', () => {
    // 원문이 편집기와 같은 순서(`-` 다음 `*`)면 고친 목록만 바뀐다
    expect(preserveSource('- 하나\n\n* 둘\n', '- 하나\n\n* 둘\n', '- 하나 고침\n\n* 둘\n')).toBe('- 하나 고침\n\n* 둘\n');
    // 글머리 뒤 번호는 합쳐지지 않는다
    expect(preserveSource('* 하나\n\n1) 둘\n', '- 하나\n\n1. 둘\n', '- 하나 고침\n\n1. 둘\n')).toBe('- 하나 고침\n\n1) 둘\n');
  });

  it('목록과 들여 쓴 코드 사이의 덩어리를 지우면 코드가 목록 안으로 들어가지 않는다', () => {
    const original = '- 하나\n\n<!-- -->\n\n    코드\n';
    const baseline = '- 하나\n\n<!-- -->\n\n```\n코드\n```\n';
    const saved = preserveSource(original, baseline, '- 하나\n\n```\n코드\n```\n');
    expect(shape(saved)).toEqual(['list', 'code']);
    expect(saved).toBe('- 하나\n\n```\n코드\n```\n');
  });

  it('들여 쓴 코드 둘 사이를 지우면 한 코드로 합쳐지지 않는다', () => {
    const original = '    가\n\n<!-- -->\n\n    나\n';
    const baseline = '```\n가\n```\n\n<!-- -->\n\n```\n나\n```\n';
    const saved = preserveSource(original, baseline, '```\n가\n```\n\n```\n나\n```\n');
    expect(shape(saved)).toEqual(['code', 'code']);
    expect(saved).toBe('    가\n\n```\n나\n```\n');
  });

  it('각주 정의 뒤 들여 쓴 코드도 같다', () => {
    const original = '[^1]: 각주\n\n<!-- -->\n\n    코드\n';
    const baseline = '[^1]: 각주\n\n<!-- -->\n\n```\n코드\n```\n';
    const saved = preserveSource(original, baseline, '[^1]: 각주\n\n```\n코드\n```\n');
    expect(shape(saved)).toEqual(['footnoteDefinition', 'code']);
  });

  it('원문에서 이미 이웃이던 목록과 들여 쓴 코드는 다른 곳을 고쳐도 원문 그대로다', () => {
    // `1.   하나` 는 내용이 다섯째 칸에서 시작해, 네 칸 들여 쓴 코드는 목록 밖의 코드 덩어리다
    const original = '1.   하나\n\n    코드\n\n끝 문단\n';
    expect(shape(original)).toEqual(['list', 'code', 'paragraph']);
    const baseline = '1. 하나\n\n```\n코드\n```\n\n끝 문단\n';
    expect(preserveSource(original, baseline, '1. 하나\n\n```\n코드\n```\n\n끝 문단 고침\n')).toBe('1.   하나\n\n    코드\n\n끝 문단 고침\n');
  });

  it('고친 목록 뒤 들여 쓴 문단은 앞 들여쓰기를 떼어 목록의 이어짐으로 읽히지 않게 한다', () => {
    // 원문은 내용이 넷째 칸에서 시작하는 목록 뒤 세 칸 들여 쓴 문단(목록 밖). 편집기는 목록을 `1. ` 로 적어 세 칸이면 이어짐이 된다
    const original = '1.  하나\n\n   문단\n';
    expect(shape(original)).toEqual(['list', 'paragraph']);
    const saved = preserveSource(original, '1. 하나\n\n문단\n', '1. 하나 고침\n\n문단\n');
    expect(shape(saved)).toEqual(['list', 'paragraph']);
    expect(saved).toBe('1. 하나 고침\n\n문단\n');
  });
});

describe('preserveSource — 빈 줄 없이 이어진 이음매(moai-wiki 8e2af72 가 실제 페이지에서 찾은 넷째 함정)', () => {
  // 빈 항목 `2.` 은 뒤 줄을 삼키지 못해, 원문에서는 문단이 빈 줄 없이 따로 선다(사이 글이 "\n")
  const original = '1. 하나\n2.\n[식]\n둘째 줄\n\n끝\n';
  const baseline = '1. 하나\n2. \n\n\\[식]\n둘째 줄\n\n끝\n';

  it('빈 항목에 글을 쓰면 뒤 문단(원문 조각)이 목록의 이어짐으로 들어가지 않게 빈 줄을 둔다', () => {
    const now = '1. 하나\n2. 둘\n\n\\[식]\n둘째 줄\n\n끝\n';
    const saved = preserveSource(original, baseline, now);
    expect(shape(saved)).toEqual(shape(now));
    expect(saved).toBe('1. 하나\n2. 둘\n\n[식]\n둘째 줄\n\n끝\n');
  });

  it('createPreserver 도 같은 글을 낸다', () => {
    const now = '1. 하나\n2. 둘\n\n\\[식]\n둘째 줄\n\n끝\n';
    expect(createPreserver(original, baseline)(now)).toBe(preserveSource(original, baseline, now));
  });

  it('붙여도 따로인 이음매(제목 뒤 문단)에는 빈 줄을 더하지 않는다 — 고친 줄만 바뀐다', () => {
    const saved = preserveSource('## 제목\n문단\n', '## 제목\n\n문단\n', '## 제목 고침\n\n문단\n');
    expect(saved).toBe('## 제목 고침\n문단\n');
  });

  it('고치지 않으면 원문 그대로다', () => {
    expect(preserveSource(original, baseline, baseline)).toBe(original);
  });
});

describe('preserveSource — 실제 글의 덩어리를 하나씩 고쳐도 구조가 그대로다(moai-wiki 와 같은 견주기)', () => {
  interface Pair {
    original: string;
    editor: string;
  }
  // 정의 줄([x]: …)은 자리를 옮겨 적을 수 있어 견주기에서 뺀다
  const kinds = (markdown: string) => shape(markdown).filter((type) => type !== 'definition');
  const pairs = roundtrip as Pair[];

  it.each(pairs.map((p, i) => [i, p] as const))('%i 글이 든 덩어리 끝에 " 고침"을 붙이면 저장한 글을 다시 나눈 종류가 지금 글과 같다', (_i, pair) => {
    const base = splitBlocks(pair.editor);
    base.blocks.forEach((block, index) => {
      if (!['paragraph', 'heading', 'list', 'blockquote'].includes(block.type)) {
        return;
      }
      const current = base.blocks.map((b, k) => b.before + (k === index ? `${block.text} 고침` : b.text)).join('') + base.tail;
      expect(kinds(preserveSource(pair.original, pair.editor, current))).toEqual(kinds(current));
    });
  });
});

describe('preserveSource — 원문 자리의 앞 들여쓰기', () => {
  it('들여 쓴 목록을 고치면 편집기 글을 0번째 칸에 둔다 — 안쪽 항목이 형제가 되지 않는다', () => {
    const original = '문단\n\n  - 하나\n    - 안쪽\n';
    const baseline = '문단\n\n- 하나\n  - 안쪽\n';
    expect(preserveSource(original, baseline, '문단\n\n- 하나 고침\n  - 안쪽\n')).toBe('문단\n\n- 하나 고침\n  - 안쪽\n');
  });

  it('고친 목록 뒤 들여 쓴 목록(원문 조각)은 편집기 글로 — 들여쓰기만 떼면 안쪽 줄이 어긋난다', () => {
    const original = '1.  하나\n\n   - 둘\n     - 안쪽\n';
    expect(shape(original)).toEqual(['list', 'list']);
    const saved = preserveSource(original, '1. 하나\n\n- 둘\n  - 안쪽\n', '1. 하나 고침\n\n- 둘\n  - 안쪽\n');
    expect(saved).toBe('1. 하나 고침\n\n- 둘\n  - 안쪽\n');
  });

  it('손대지 않은 들여 쓴 덩어리는 원문 그대로다', () => {
    const original = '문단\n\n  - 하나\n    - 안쪽\n\n끝\n';
    expect(preserveSource(original, '문단\n\n- 하나\n  - 안쪽\n\n끝\n', '문단\n\n- 하나\n  - 안쪽\n\n끝 고침\n')).toBe('문단\n\n  - 하나\n    - 안쪽\n\n끝 고침\n');
  });
});

describe('createPreserver — 원문 · baseline 을 한 번만 나눈다', () => {
  interface Pair {
    original: string;
    editor: string;
  }
  const pairs = roundtrip as Pair[];

  it.each(pairs.map((p, i) => [i, p] as const))('%i 여러 번 불러도 preserveSource 와 같다', (_i, pair) => {
    const preserve = createPreserver(pair.original, pair.editor);
    const base = splitBlocks(pair.editor);
    expect(preserve(pair.editor)).toBe(pair.original);
    // 덩어리마다 끝에 글을 덧붙여 본다(같은 preserver 를 거듭 쓴다)
    base.blocks.forEach((block, index) => {
      const current = base.blocks.map((b, k) => b.before + (k === index ? `${block.text} 고침` : b.text)).join('') + base.tail;
      expect(preserve(current)).toBe(preserveSource(pair.original, pair.editor, current));
    });
    expect(preserve('')).toBe(preserveSource(pair.original, pair.editor, ''));
  });
});

describe('resplit — 바뀐 곳 둘레만 다시 나눠도 통째로 나눈 것과 같다', () => {
  interface Pair {
    source: 'real' | 'synthetic';
    original: string;
    editor: string;
  }
  const pairs = roundtrip as Pair[];

  /** 늘 같은 차례로 나오는 난수(mulberry32) */
  function random(seed: number) {
    let state = seed >>> 0;
    return () => {
      state = (state + 0x6d2b79f5) >>> 0;
      let t = state;
      t = Math.imul(t ^ (t >>> 15), t | 1);
      t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
      return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
    };
  }
  /** 이음매를 흔드는 조각 — 빈 줄 · 목록 표지 · 들여쓰기 · 울타리 · 끝나지 않는 HTML · setext 밑줄 · 표 · 각주 · 정의 줄 */
  const snippets = ['가', ' ', '\n', '\n\n', '\n\n\n', '- ', '* ', '+ ', '1. ', '1) ', '3. ', '    ', '\t', '```', '```\n', '\n```\n', '~~~', '<!--', '-->', '<div>', '</div>', '<script>', '<pre>', '<?', '?>', '# ', '> ', '| a |\n| - |\n', '[^1]: ', '[x]: https://e.com', '***', '===', '---', '\\', '`', '  \n', '\n- 새 항목\n', '\n\n문단\n\n'];

  /** 한 번 고친다 — 조각 넣기 · 지우기 · 바꾸기 */
  function mutate(text: string, next: () => number): string {
    const at = Math.floor(next() * (text.length + 1));
    const kind = next();
    const snippet = snippets[Math.floor(next() * snippets.length)] ?? '';
    if (kind < 0.5) {
      return text.slice(0, at) + snippet + text.slice(at);
    }
    const length = Math.floor(next() * 24);
    if (kind < 0.8) {
      return text.slice(0, at) + text.slice(at + length);
    }
    return text.slice(0, at) + snippet + text.slice(at + length);
  }

  const seeds = pairs.flatMap((p, i) => [[`${i} 편집기 글`, p.editor] as const, [`${i} 원문`, p.original] as const]);

  it.each(seeds)('%s — 40번 이어 고쳐도 같다', (name, seed) => {
    const next = random(name.length * 7919 + seed.length);
    let text = seed;
    let split: SourceSplit = splitBlocks(text);
    for (let step = 0; step < 40; step++) {
      const changed = mutate(text, next);
      const fast = resplit(text, split, changed);
      expect(fast, `${step}: ${JSON.stringify(changed)}`).toEqual(splitBlocks(changed));
      text = changed;
      split = fast;
    }
  });

  it('3만 자 글에서 가운데 한 글자를 고치면 앞 · 뒤 덩어리는 고치기 전 것을 그대로 쓴다', () => {
    const real = pairs.filter((p) => p.source === 'real').map((p) => p.editor.trim());
    let text = '';
    for (let i = 0; text.length < 30_000; i++) {
      text += `${real[i % real.length]}\n\n`;
    }
    text = `${text.trimEnd()}\n`;
    const split = splitBlocks(text);
    const middle = Math.floor(split.blocks.length / 2);
    const at = split.blocks.slice(0, middle + 1).reduce((sum, b) => sum + b.before.length + b.text.length, 0);
    const changed = `${text.slice(0, at)}가${text.slice(at)}`;
    const fast = resplit(text, split, changed);
    expect(fast).toEqual(splitBlocks(changed));
    expect(fast.blocks[0]).toBe(split.blocks[0]);
    expect(fast.blocks.at(-1)).toBe(split.blocks.at(-1));
    // 큰 글을 마구 고쳐도 같다
    const next = random(2026);
    let current = text;
    let currentSplit = split;
    for (let step = 0; step < 30; step++) {
      const mutated = mutate(current, next);
      const result = resplit(current, currentSplit, mutated);
      expect(result).toEqual(splitBlocks(mutated));
      current = mutated;
      currentSplit = result;
    }
  });
});
