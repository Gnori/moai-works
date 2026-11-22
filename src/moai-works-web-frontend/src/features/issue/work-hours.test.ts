import { describe, expect, it } from 'vitest';

import {
  buildWorkLog,
  canRemoveWorkLog,
  formatHours,
  parseHours,
  spentProgress,
  workLogErrorField,
  workLogProblems,
} from './work-hours';

const ME = 'aaaaaaaa-0000-0000-0000-000000000001';
const OTHER = 'aaaaaaaa-0000-0000-0000-000000000002';
const TODAY = '2026-11-22';

describe('시간 칸 읽기 · 보이기', () => {
  it('비우면 null, 숫자가 아니면 NaN, 숫자는 소수 둘째 자리까지', () => {
    expect(parseHours('')).toBeNull();
    expect(parseHours('   ')).toBeNull();
    expect(parseHours('8')).toBe(8);
    expect(parseHours(' 1.5 ')).toBe(1.5);
    expect(parseHours('.25')).toBe(0.25);
    expect(parseHours('2.')).toBe(2);
    expect(parseHours('1.234')).toBe(1.23);
    expect(parseHours('-1')).toBeNaN();
    expect(parseHours('1e2')).toBeNaN();
    expect(parseHours('두 시간')).toBeNaN();
  });

  it('보일 때는 끝의 0 과 부동소수 찌꺼기를 뺀다', () => {
    expect(formatHours(10)).toBe('10');
    expect(formatHours(6.5)).toBe('6.5');
    expect(formatHours(0.25)).toBe('0.25');
    expect(formatHours(0.1 + 0.2)).toBe('0.3');
  });
});

describe('작업 시간 적기 (api 와 같은 규칙)', () => {
  const ok = { workDate: TODAY, hours: '1.5', note: '' };

  it('맞는 값이면 문제가 없다', () => {
    expect(workLogProblems(ok, TODAY)).toEqual({});
    expect(workLogProblems({ ...ok, workDate: '2026-11-01', hours: '24' }, TODAY)).toEqual({});
    expect(workLogProblems({ ...ok, hours: '0.25' }, TODAY)).toEqual({});
  });

  it('일한 날은 꼭 있어야 하고 오늘까지', () => {
    expect(workLogProblems({ ...ok, workDate: '' }, TODAY)).toEqual({ workDate: 'dateRequired' });
    expect(workLogProblems({ ...ok, workDate: '2026-11-23' }, TODAY)).toEqual({ workDate: 'dateFuture' });
  });

  it('시간은 0 보다 크고 24 이하', () => {
    for (const hours of ['', '0', '24.01', '25', '-1', 'abc']) {
      expect(workLogProblems({ ...ok, hours }, TODAY)).toEqual({ hours: 'hoursInvalid' });
    }
  });

  it('메모는 1000자까지', () => {
    expect(workLogProblems({ ...ok, note: 'a'.repeat(1000) }, TODAY)).toEqual({});
    expect(workLogProblems({ ...ok, note: 'a'.repeat(1001) }, TODAY)).toEqual({ note: 'noteTooLong' });
  });

  it('요청 — 시간은 숫자로, 메모는 다듬고 비우면 뺀다', () => {
    expect(buildWorkLog({ workDate: TODAY, hours: '2', note: '  ' })).toEqual({ workDate: TODAY, hours: 2 });
    expect(buildWorkLog({ workDate: TODAY, hours: '0.75', note: ' 설계 검토 ' })).toEqual({ workDate: TODAY, hours: 0.75, note: '설계 검토' });
  });

  it('api 오류를 보일 칸', () => {
    expect(workLogErrorField('INVALID_WORK_DATE')).toBe('workDate');
    expect(workLogErrorField('WORK_DATE_REQUIRED')).toBe('workDate');
    expect(workLogErrorField('INVALID_WORK_HOURS')).toBe('hours');
    expect(workLogErrorField('TEXT_TOO_LONG')).toBe('note');
    expect(workLogErrorField('PERMISSION_REQUIRED')).toBeUndefined();
  });
});

describe('작업 시간 기록의 지우기 단추', () => {
  const mine = { user: { id: ME, username: 'kim', displayName: '김철수' } };
  const theirs = { user: { id: OTHER, username: 'lee', displayName: '이영희' } };

  it('내 기록은 지울 수 있고, 남의 기록은 project.manage 가 있을 때만', () => {
    expect(canRemoveWorkLog(mine, ME, false)).toBe(true);
    expect(canRemoveWorkLog(theirs, ME, false)).toBe(false);
    expect(canRemoveWorkLog(theirs, ME, true)).toBe(true);
    expect(canRemoveWorkLog({ user: null }, ME, false)).toBe(false);
    expect(canRemoveWorkLog({ user: null }, ME, true)).toBe(true);
  });
});

describe('쓴 시간 / 예상', () => {
  it('예상이 없거나 0 이면 견주지 않는다', () => {
    expect(spentProgress(3, null)).toBeNull();
    expect(spentProgress(3, 0)).toBeNull();
  });

  it('백분율과 넘긴 시간', () => {
    expect(spentProgress(6.5, 10)).toEqual({ percent: 65, over: 0 });
    expect(spentProgress(0, 8)).toEqual({ percent: 0, over: 0 });
    expect(spentProgress(12.25, 10)).toEqual({ percent: 123, over: 2.25 });
    expect(spentProgress(1, 3)).toEqual({ percent: 33, over: 0 });
  });
});
