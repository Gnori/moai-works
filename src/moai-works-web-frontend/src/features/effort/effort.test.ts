import { describe, expect, it } from 'vitest';

import {
  barPercent,
  carriesEstimate,
  effortCsvName,
  effortGroupingsFor,
  hasEffortFilters,
  rangeDates,
  readEffortFilters,
  seoulToday,
  signedHours,
  toEffortQuery,
  writeEffortFilters,
} from './effort';

/** 공수 집계의 조건 ↔ 주소 · 기간 계산 · 보이는 모양 */

const PERSON = 'aaaaaaaa-0000-0000-0000-000000000001';

describe('오늘 (한국 시간)', () => {
  it('UTC 로 전날 밤이어도 한국은 이미 다음 날이다', () => {
    expect(seoulToday(new Date('2026-11-30T15:30:00Z'))).toBe('2026-12-01');
    expect(seoulToday(new Date('2026-11-30T14:59:59Z'))).toBe('2026-11-30');
  });
});

describe('기간', () => {
  const today = '2026-11-26'; // 목요일
  it('이번 주는 월요일부터 오늘까지, 이번 달 · 올해는 첫날부터 오늘까지', () => {
    expect(rangeDates({ range: 'thisWeek', from: null, to: null }, today)).toEqual({ from: '2026-11-23', to: today });
    expect(rangeDates({ range: 'thisWeek', from: null, to: null }, '2026-11-29')).toEqual({ from: '2026-11-23', to: '2026-11-29' });
    expect(rangeDates({ range: 'thisWeek', from: null, to: null }, '2026-11-23')).toEqual({ from: '2026-11-23', to: '2026-11-23' });
    expect(rangeDates({ range: 'thisMonth', from: null, to: null }, today)).toEqual({ from: '2026-11-01', to: today });
    expect(rangeDates({ range: 'thisYear', from: null, to: null }, today)).toEqual({ from: '2026-01-01', to: today });
  });

  it('지난 달은 그 달 전체, 전체는 끝이 없고, 직접 고른 날은 그대로', () => {
    expect(rangeDates({ range: 'lastMonth', from: null, to: null }, '2026-03-31')).toEqual({ from: '2026-02-01', to: '2026-02-28' });
    expect(rangeDates({ range: 'lastMonth', from: null, to: null }, '2026-01-15')).toEqual({ from: '2025-12-01', to: '2025-12-31' });
    expect(rangeDates({ range: 'all', from: null, to: null }, today)).toEqual({ from: null, to: null });
    expect(rangeDates({ range: 'custom', from: '2026-10-01', to: null }, today)).toEqual({ from: '2026-10-01', to: null });
  });
});

describe('조건 ↔ 주소', () => {
  it('비우면 이번 달 · 워크스페이스는 프로젝트별, 프로젝트 화면은 일감별', () => {
    expect(readEffortFilters(new URLSearchParams(), 'workspace')).toEqual({
      range: 'thisMonth', from: null, to: null, groupBy: 'project', project: null, person: null, type: null,
    });
    expect(readEffortFilters(new URLSearchParams(), 'project').groupBy).toBe('issue');
  });

  it('날짜가 있으면 직접 고름이고, 모르는 값 · 없는 날은 버린다', () => {
    const f = readEffortFilters(new URLSearchParams(`from=2026-10-01&to=2026-02-31&groupBy=year&person=${PERSON}&type=bug&project=Bad Key`), 'workspace');
    expect(f).toEqual({ range: 'custom', from: '2026-10-01', to: null, groupBy: 'project', project: null, person: PERSON, type: 'bug' });
    expect(readEffortFilters(new URLSearchParams('range=lastMonth&person=nope'), 'workspace')).toMatchObject({ range: 'lastMonth', person: null });
    expect(readEffortFilters(new URLSearchParams('range=custom'), 'workspace').range).toBe('thisMonth');
  });

  it('프로젝트 화면은 프로젝트 조건 · 프로젝트별 기준을 받지 않는다', () => {
    const f = readEffortFilters(new URLSearchParams('project=ea&groupBy=project'), 'project');
    expect(f.project).toBeNull();
    expect(f.groupBy).toBe('issue');
    expect(effortGroupingsFor('project')).not.toContain('project');
    expect(effortGroupingsFor('workspace')).toEqual(['person', 'project', 'issue', 'type', 'day', 'week', 'month']);
  });

  it('기본값은 주소에 적지 않고, 읽고 다시 적으면 같다', () => {
    const base = readEffortFilters(new URLSearchParams(), 'workspace');
    expect(writeEffortFilters(base, 'workspace').toString()).toBe('');
    expect(hasEffortFilters(base, 'workspace')).toBe(false);
    const search = `range=lastMonth&groupBy=person&project=ea&person=${PERSON}&type=bug`;
    expect(writeEffortFilters(readEffortFilters(new URLSearchParams(search), 'workspace'), 'workspace').toString()).toBe(search);
    expect(writeEffortFilters({ ...base, range: 'custom', from: '2026-10-01', to: '2026-10-31' }, 'workspace').toString()).toBe('from=2026-10-01&to=2026-10-31');
    // 두 끝을 모두 비운 직접 고르기는 전체
    expect(writeEffortFilters({ ...base, range: 'custom' }, 'workspace').toString()).toBe('range=all');
  });
});

describe('api 요청 값', () => {
  it('기간을 날짜로 바꾸고, 비운 조건은 보내지 않는다', () => {
    const f = readEffortFilters(new URLSearchParams(`groupBy=week&person=${PERSON}`), 'workspace');
    expect(toEffortQuery(f, '2026-11-26')).toEqual({ groupBy: 'week', from: '2026-11-01', to: '2026-11-26', person: [PERSON] });
    expect(toEffortQuery({ ...f, range: 'all', person: null }, '2026-11-26')).toEqual({ groupBy: 'week' });
  });

  it('프로젝트 화면은 그 프로젝트로 묻는다', () => {
    const f = readEffortFilters(new URLSearchParams('range=all&type=bug'), 'project');
    expect(toEffortQuery(f, '2026-11-26', 'ea')).toEqual({ groupBy: 'issue', project: ['ea'], type: ['bug'] });
  });

  it('CSV 파일 이름은 기준과 기간 — 서버가 주는 이름과 같은 꼴', () => {
    expect(effortCsvName({ groupBy: 'issue', from: '2026-11-01', to: '2026-11-26' })).toBe('effort-issue-2026-11-01-2026-11-26.csv');
    expect(effortCsvName({ groupBy: 'person' })).toBe('effort-person-all-all.csv');
  });
});

describe('보이는 모양', () => {
  it('예상은 일감 · 프로젝트 · 유형으로 모을 때만 줄마다 있다 (서버와 같은 규칙)', () => {
    expect(['project', 'issue', 'type'].every((g) => carriesEstimate(g as never))).toBe(true);
    expect(['person', 'day', 'week', 'month'].some((g) => carriesEstimate(g as never))).toBe(false);
  });

  it('막대는 가장 큰 줄을 100 으로', () => {
    expect(barPercent(5, 10)).toBe(50);
    expect(barPercent(10, 10)).toBe(100);
    expect(barPercent(1, 3)).toBe(33.3);
    expect(barPercent(0, 10)).toBe(0);
    expect(barPercent(3, 0)).toBe(0);
  });

  it('차이는 기호를 함께 적는다', () => {
    expect(signedHours(1.5)).toBe('+1.5');
    expect(signedHours(-2)).toBe('−2');
    expect(signedHours(0)).toBe('0');
    expect(signedHours(0.1 + 0.2)).toBe('+0.3');
  });
});
