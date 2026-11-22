import { describe, expect, it } from 'vitest';

import type { IssueDiffLine } from '@/shared/api/types';

import { collapseSame } from './diff-lines';

function lines(ops: string): IssueDiffLine[] {
  return [...ops].map((op, index) => ({
    op: op === '+' ? 'ADD' : op === '-' ? 'DEL' : 'SAME',
    text: `줄 ${index + 1}`,
    oldLine: op === '+' ? null : index + 1,
    newLine: op === '-' ? null : index + 1,
  }));
}

function shape(ops: string, context = 2): string {
  return collapseSame(lines(ops), context)
    .map((row) => (row.kind === 'gap' ? `(${row.lines.length})` : row.line.op === 'SAME' ? '.' : row.line.op === 'ADD' ? '+' : '-'))
    .join('');
}

describe('차이 화면의 같은 줄 접기', () => {
  it('바뀐 줄 앞뒤로 context 줄만 남기고 접는다', () => {
    expect(shape('..........+..........')).toBe('(8)..+..(8)');
    expect(shape('-.........+')).toBe('-..(5)..+');
  });

  it('짧은 사이는 접지 않는다', () => {
    expect(shape('-....+')).toBe('-....+');
    expect(shape('-.....+')).toBe('-.....+');
    expect(shape('-......+')).toBe('-..(2)..+');
  });

  it('바뀐 곳이 없으면 전부 접힌다', () => {
    expect(shape('.....')).toBe('(5)');
    expect(shape('')).toBe('');
  });

  it('접은 줄은 그대로 들고 있다', () => {
    const rows = collapseSame(lines('.....+'), 1);
    const gap = rows[0];
    expect(gap?.kind === 'gap' && gap.lines.map((line) => line.text)).toEqual(['줄 1', '줄 2', '줄 3', '줄 4']);
  });

  it('기본 context 는 3줄이고, 접은 덩어리마다 id 가 다르다', () => {
    const rows = collapseSame(lines('..........+..........-..........'));
    expect(rows.filter((row) => row.kind === 'gap').map((row) => row.kind === 'gap' && row.id)).toEqual([0, 1, 2]);
  });
});
