import type { IssueDiffLine } from '@/shared/api/types';

/** 차이 화면의 한 줄 — 줄 하나이거나, 접어 둔 "같은 줄 N개" (moai-wiki 의 diff-lines 와 같다) */
export type DiffRow = { kind: 'line'; line: IssueDiffLine } | { kind: 'gap'; id: number; lines: IssueDiffLine[] };

/**
 * 같은 줄이 길게 이어지는 곳을 접는다 — 바뀐 줄의 앞뒤 context 줄만 남긴다.
 * 바뀐 곳이 하나도 없으면 전부 접힌 한 덩어리가 나온다.
 */
export function collapseSame(lines: IssueDiffLine[], context = 3): DiffRow[] {
  const rows: DiffRow[] = [];
  let gapId = 0;
  let i = 0;
  while (i < lines.length) {
    const line = lines[i] as IssueDiffLine;
    if (line.op !== 'SAME') {
      rows.push({ kind: 'line', line });
      i += 1;
      continue;
    }
    let end = i;
    while (end < lines.length && (lines[end] as IssueDiffLine).op === 'SAME') {
      end += 1;
    }
    const run = lines.slice(i, end);
    // 맨 앞 · 맨 뒤의 같은 줄 덩어리는 바뀐 줄이 있는 쪽으로만 context 를 남긴다
    const head = i === 0 ? 0 : context;
    const tail = end === lines.length ? 0 : context;
    if (run.length <= head + tail + 1) {
      run.forEach((same) => rows.push({ kind: 'line', line: same }));
    } else {
      run.slice(0, head).forEach((same) => rows.push({ kind: 'line', line: same }));
      rows.push({ kind: 'gap', id: gapId, lines: run.slice(head, run.length - tail) });
      gapId += 1;
      run.slice(run.length - tail).forEach((same) => rows.push({ kind: 'line', line: same }));
    }
    i = end;
  }
  return rows;
}
