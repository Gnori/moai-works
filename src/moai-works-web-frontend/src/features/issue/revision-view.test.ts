import { describe, expect, it } from 'vitest';

import { compareCandidates, readRevisionSelection, writeRevisionSelection } from './revision-view';

const q = (search: string) => new URLSearchParams(search);

describe('리비전 화면의 주소 조건 읽기', () => {
  it('아무것도 없으면 지금 판을 바로 앞 판과 견준다', () => {
    expect(readRevisionSelection(q(''), 5)).toEqual({ rev: 5, from: undefined, view: 'diff' });
  });

  it('고른 판 · 견줄 판 · 보기를 읽는다', () => {
    expect(readRevisionSelection(q('rev=3&from=1&view=snapshot'), 5)).toEqual({ rev: 3, from: 1, view: 'snapshot' });
    // 새 판을 옛 판으로 견줄 수도 있다(거꾸로)
    expect(readRevisionSelection(q('rev=2&from=5'), 5)).toEqual({ rev: 2, from: 5, view: 'diff' });
    // 0 = 빈 일감
    expect(readRevisionSelection(q('rev=4&from=0'), 5)).toMatchObject({ rev: 4, from: 0 });
  });

  it('맞지 않는 값은 버린다', () => {
    expect(readRevisionSelection(q('rev=9'), 5).rev).toBe(5);
    expect(readRevisionSelection(q('rev=0'), 5).rev).toBe(5);
    expect(readRevisionSelection(q('rev=abc'), 5).rev).toBe(5);
    expect(readRevisionSelection(q('rev=2.5'), 5).rev).toBe(5);
    expect(readRevisionSelection(q('rev=3&from=3'), 5).from).toBeUndefined();
    expect(readRevisionSelection(q('rev=3&from=7'), 5).from).toBeUndefined();
    expect(readRevisionSelection(q('rev=3&from=-1'), 5).from).toBeUndefined();
    expect(readRevisionSelection(q('view=other'), 5).view).toBe('diff');
  });

  it('바로 앞 판은 기본값이라 undefined 로 둔다 (1판의 0 도)', () => {
    expect(readRevisionSelection(q('rev=3&from=2'), 5).from).toBeUndefined();
    expect(readRevisionSelection(q('rev=1&from=0'), 5).from).toBeUndefined();
  });

  it('판이 아직 없으면(0) 1판으로 둔다', () => {
    expect(readRevisionSelection(q(''), 0).rev).toBe(1);
  });
});

describe('리비전 화면의 주소 조건 쓰기', () => {
  it('기본값(바로 앞 판 · 차이 보기)은 적지 않고 다른 조건은 둔다', () => {
    expect(writeRevisionSelection(q('x=1&from=1&view=snapshot'), { rev: 4, from: undefined, view: 'diff' }).toString()).toBe('x=1&rev=4');
    expect(writeRevisionSelection(q(''), { rev: 4, from: 3, view: 'diff' }).toString()).toBe('rev=4');
    expect(writeRevisionSelection(q(''), { rev: 4, from: 1, view: 'snapshot' }).toString()).toBe('rev=4&from=1&view=snapshot');
    expect(writeRevisionSelection(q(''), { rev: 4, from: 0, view: 'diff' }).toString()).toBe('rev=4&from=0');
  });

  it('쓴 것을 다시 읽으면 같은 것이 나온다', () => {
    const selection = { rev: 2, from: 5, view: 'snapshot' as const };
    expect(readRevisionSelection(writeRevisionSelection(q(''), selection), 5)).toEqual(selection);
  });
});

describe('비교할 판 후보', () => {
  it('새 판부터, 고른 판은 빼고, 맨 끝에 빈 일감(0)', () => {
    expect(compareCandidates(5, 3)).toEqual([5, 4, 2, 1, 0]);
    expect(compareCandidates(1, 1)).toEqual([0]);
  });
});
