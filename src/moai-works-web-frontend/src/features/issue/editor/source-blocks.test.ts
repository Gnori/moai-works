// 사본 — moai-wiki 의 source-blocks.test.ts (위키 커밋 166bbd8). 예시 글의 [[위키 링크]] 는 위키 표기지만 덩어리 나누기 시험에는 그대로 쓸 수 있다.
import { describe, expect, it } from 'vitest';

import { joinBlocks, matchBlocks, preserveSource, splitBlocks } from './source-blocks';

/** 사람이 쓴 모양 그대로의 원문 — 목록 기호 `-`, 칸을 맞추지 않은 표, 위키 링크, 인용 표지 */
const original = [
  '# 출장비 규정 {#travel}',
  '',
  '국내 출장은 [[travel-domestic|국내 출장]]을 본다.[^c1]',
  '',
  '',
  '- 영수증 원본을 낸다.',
  '- 귀국 후 7영업일 안에 정산한다.',
  '',
  '| 직급 | 숙박비 |',
  '|---|---|',
  '| 부장 | 12만 원 |',
  '',
  '```sql',
  'select 1;',
  '```',
  '',
  '끝 문단.',
  '',
].join('\n');

/** 편집기가 같은 글을 자기 방식으로 다시 적은 모양 — 기호와 칸 맞춤이 바뀐다 */
const baseline = [
  '# 출장비 규정 {#travel}',
  '',
  '국내 출장은 [[travel-domestic|국내 출장]]을 본다.[^c1]',
  '',
  '* 영수증 원본을 낸다.',
  '* 귀국 후 7영업일 안에 정산한다.',
  '',
  '| 직급 | 숙박비   |',
  '| -- | ----- |',
  '| 부장 | 12만 원 |',
  '',
  '```sql',
  'select 1;',
  '```',
  '',
  '끝 문단.',
  '',
].join('\n');

describe('splitBlocks', () => {
  it('맨 위 덩어리로 나누고, 이으면 원래 글이 된다', () => {
    const split = splitBlocks(original);
    expect(split.blocks.map((block) => block.text.split('\n')[0])).toEqual([
      '# 출장비 규정 {#travel}',
      '국내 출장은 [[travel-domestic|국내 출장]]을 본다.[^c1]',
      '- 영수증 원본을 낸다.',
      '| 직급 | 숙박비 |',
      '```sql',
      '끝 문단.',
    ]);
    // 문단과 목록 사이의 빈 줄 둘도 사이 글로 남는다
    expect(split.blocks[2]?.before).toBe('\n\n\n');
    expect(split.tail).toBe('\n');
    expect(joinBlocks(split)).toBe(original);
  });

  it('빈 글은 덩어리가 없다', () => {
    expect(splitBlocks('')).toEqual({ blocks: [], tail: '' });
    expect(joinBlocks(splitBlocks('\n\n'))).toBe('\n\n');
  });
});

describe('matchBlocks', () => {
  it('순서를 지키며 같은 덩어리를 짝짓는다', () => {
    expect(matchBlocks(['a', 'b', 'c', 'd'], ['a', 'x', 'c', 'd'])).toEqual([0, -1, 2, 3]);
    expect(matchBlocks(['a', 'b', 'c'], ['a', 'c'])).toEqual([0, 2]);
    expect(matchBlocks(['a', 'b'], ['n', 'a', 'b'])).toEqual([-1, 0, 1]);
    // 자리를 바꾸면 한쪽만 짝이 된다(순서를 지킨다)
    expect(matchBlocks(['a', 'b'], ['b', 'a']).filter((index) => index !== -1)).toHaveLength(1);
    expect(matchBlocks([], ['a'])).toEqual([-1]);
  });
});

describe('preserveSource', () => {
  it('고치지 않았으면 원문과 한 글자도 다르지 않다', () => {
    expect(preserveSource(original, baseline, baseline)).toBe(original);
  });

  it('한 문단만 고치면 그 문단만 바뀌고 나머지는 원문 그대로다', () => {
    const current = baseline.replace('끝 문단.', '끝 문단을 고쳤다.');
    expect(preserveSource(original, baseline, current)).toBe(original.replace('끝 문단.', '끝 문단을 고쳤다.'));
  });

  it('목록을 고치면 그 목록만 편집기의 모양이 되고, 표 · 코드 · 빈 줄은 원문 그대로다', () => {
    const current = baseline.replace('* 귀국 후 7영업일 안에 정산한다.', '* 귀국 후 5영업일 안에 정산한다.');
    const saved = preserveSource(original, baseline, current);
    expect(saved).toContain('* 영수증 원본을 낸다.\n* 귀국 후 5영업일 안에 정산한다.');
    // 손대지 않은 표는 칸을 맞추지 않은 원래 모양
    expect(saved).toContain('| 직급 | 숙박비 |\n|---|---|\n| 부장 | 12만 원 |');
    // 앞 문단과의 사이(빈 줄 둘)도 그대로 — 앞뒤 덩어리가 원문에서 이웃이었다
    expect(saved).toContain('본다.[^c1]\n\n\n* 영수증');
    expect(saved.endsWith('끝 문단.\n')).toBe(true);
  });

  it('덩어리를 새로 넣거나 지워도 나머지는 원문 그대로다', () => {
    const added = baseline.replace('```sql', '새 문단.\n\n```sql');
    expect(preserveSource(original, baseline, added)).toBe(original.replace('```sql', '새 문단.\n\n```sql'));
    const removed = baseline.replace('```sql\nselect 1;\n```\n\n', '');
    expect(preserveSource(original, baseline, removed)).toBe(original.replace('```sql\nselect 1;\n```\n\n', ''));
  });

  it('글 끝의 모양(줄바꿈이 있나)은 제자리에서 고치면 원문대로, 끝에 덧붙이면 줄바꿈 하나로 맺는다', () => {
    const noNewline = original.trimEnd();
    expect(preserveSource(noNewline, baseline, baseline)).toBe(noNewline);
    expect(preserveSource(noNewline, baseline, baseline.replace('끝 문단.', '다른 끝.'))).toBe(noNewline.replace('끝 문단.', '다른 끝.'));
    expect(preserveSource(noNewline, baseline, `${baseline}\n덧붙인 문단.\n`)).toBe(`${noNewline}\n\n덧붙인 문단.\n`);
  });

  it('편집기가 덩어리 수를 바꿔 내놓으면(짝을 알 수 없다) 편집기의 글을 그대로 쓴다', () => {
    const merged = '한 덩어리로 합쳐졌다.\n';
    const current = '한 덩어리로 합쳐졌다. 고침.\n';
    expect(preserveSource(original, merged, current)).toBe(current);
    // 그래도 고치지 않았으면 원문이다
    expect(preserveSource(original, merged, merged)).toBe(original);
  });

  it('참조 링크의 정의 줄은 편집기가 버려도 원문에 있던 자리에 남는다', () => {
    const withReferences = ['첫 문단은 [규정][rule]을 본다.', '', '둘째 문단.', '', '[rule]: https://example.com/rule', ''].join('\n');
    // 편집기는 링크를 제자리에 풀어 적고 정의 줄을 버린다
    const inlined = ['첫 문단은 [규정](https://example.com/rule)을 본다.', '', '둘째 문단.', ''].join('\n');
    expect(preserveSource(withReferences, inlined, inlined)).toBe(withReferences);
    // 둘째 문단만 고쳤다 — 첫 문단은 이름으로 가리키는 원래 모양 그대로이고, 정의 줄도 그 자리에 있다
    expect(preserveSource(withReferences, inlined, inlined.replace('둘째 문단.', '둘째 문단을 고쳤다.'))).toBe(
      withReferences.replace('둘째 문단.', '둘째 문단을 고쳤다.'),
    );
    // 정의 줄 바로 앞 덩어리를 지워도 정의 줄은 글 끝에 남는다(첫 문단이 아직 그 이름을 쓴다)
    const removed = preserveSource(withReferences, inlined, '첫 문단은 [규정](https://example.com/rule)을 본다.\n');
    expect(removed).toBe('첫 문단은 [규정][rule]을 본다.\n\n[rule]: https://example.com/rule\n');
  });

  it('다 지우면 빈 글이다', () => {
    expect(preserveSource(original, baseline, '')).toBe('');
  });
});
