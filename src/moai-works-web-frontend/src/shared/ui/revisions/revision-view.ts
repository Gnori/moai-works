/**
 * 리비전 화면에서 고른 것 — 주소에 남긴다(?rev=5&from=2&view=snapshot). 새로 고치거나 링크로 보내도 같은 화면이 열린다.
 * 판은 1부터 빠짐없이 쌓인다(latest = 지금 판 번호).
 */
export type RevisionView = 'diff' | 'snapshot';

export interface RevisionSelection {
  /** 고른 판 (새 쪽) */
  rev: number;
  /** 견줄 판 (옛 쪽). undefined = 바로 앞 판(1판이면 빈 일감), 0 = 빈 일감 */
  from: number | undefined;
  view: RevisionView;
}

function intParam(value: string | null): number | undefined {
  return value !== null && /^\d{1,9}$/.test(value) ? Number(value) : undefined;
}

/**
 * 주소의 조건 → 고른 것. 맞지 않는 값은 버린다 —
 * rev 는 1 ~ latest(아니면 지금 판), from 은 0 ~ latest 이고 rev 와 달라야 한다.
 * from 이 바로 앞 판(rev - 1)이면 기본값과 같으므로 undefined 로 둔다(주소를 짧게, 고른 칸이 "바로 앞"으로 보이게).
 */
export function readRevisionSelection(params: URLSearchParams, latest: number): RevisionSelection {
  const requested = intParam(params.get('rev'));
  const rev = requested !== undefined && requested >= 1 && requested <= latest ? requested : Math.max(latest, 1);
  const base = intParam(params.get('from'));
  const from = base !== undefined && base <= latest && base !== rev && base !== rev - 1 ? base : undefined;
  return { rev, from, view: params.get('view') === 'snapshot' ? 'snapshot' : 'diff' };
}

/** 고른 것 → 주소의 조건. 기본값(바로 앞 판 · 차이 보기)은 적지 않는다. 다른 조건은 그대로 둔다 */
export function writeRevisionSelection(params: URLSearchParams, selection: RevisionSelection): URLSearchParams {
  const next = new URLSearchParams(params);
  next.set('rev', String(selection.rev));
  if (selection.from === undefined || selection.from === selection.rev - 1) {
    next.delete('from');
  } else {
    next.set('from', String(selection.from));
  }
  if (selection.view === 'snapshot') {
    next.set('view', 'snapshot');
  } else {
    next.delete('view');
  }
  return next;
}

/**
 * "비교할 판" 고르기의 후보 — 새 판부터, 고른 판은 빼고, 맨 끝에 0(빈 일감).
 * 옛 판만이 아니라 새 판도 고를 수 있다(거꾸로 견주기 — 무엇이 되돌려졌는지 볼 때).
 */
export function compareCandidates(latest: number, rev: number): number[] {
  const out: number[] = [];
  for (let n = latest; n >= 1; n -= 1) {
    if (n !== rev) {
      out.push(n);
    }
  }
  out.push(0);
  return out;
}
