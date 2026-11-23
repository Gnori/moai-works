import dayjs from 'dayjs';

import { effortGroupings, type EffortGroupBy, type EffortQuery } from '@/shared/api/types';

/**
 * 공수 집계 화면의 조건 — 주소(?range=lastMonth&groupBy=person&person=…)에 남겨 새로 고치거나 링크를 나눠도 같은 집계가 열린다(WEB-DESIGN §6).
 * 날은 서버와 같이 한국 시간(Asia/Seoul)의 달력 날짜다 — 작업 시간의 일한 날이 그 날짜로 적힌다.
 * 판정(어느 프로젝트의 기록이 들어가는가)은 서버가 한다. 여기는 조건과 보이는 모양만 다룬다.
 */

/** 기간 고르기 — 직접 고른 날짜는 custom */
export const effortRanges = ['thisWeek', 'thisMonth', 'lastMonth', 'thisYear', 'all', 'custom'] as const;
export type EffortRange = (typeof effortRanges)[number];

/** 화면의 범위 — 워크스페이스 전체(프로젝트를 고른다) · 프로젝트 하나(프로젝트가 정해져 있다) */
export type EffortScope = 'workspace' | 'project';

export interface EffortFilters {
  range: EffortRange;
  /** custom 일 때만 — yyyy-MM-dd, 비우면 그쪽 끝이 없다 */
  from: string | null;
  to: string | null;
  groupBy: EffortGroupBy;
  /** 프로젝트 주소 이름 — 워크스페이스 화면에서만 */
  project: string | null;
  /** 일한 사람(사용자 id) */
  person: string | null;
  /** 일감 유형 key */
  type: string | null;
}

const DEFAULT_RANGE: EffortRange = 'thisMonth';
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const KEY = /^[a-z0-9][a-z0-9_-]{0,63}$/i;

/** 그 범위에서 고를 수 있는 기준 — 프로젝트 하나의 화면에서는 "프로젝트별"이 한 줄뿐이라 뺀다 */
export function effortGroupingsFor(scope: EffortScope): EffortGroupBy[] {
  return scope === 'project' ? effortGroupings.filter((g) => g !== 'project') : [...effortGroupings];
}

export function defaultGroupBy(scope: EffortScope): EffortGroupBy {
  return scope === 'project' ? 'issue' : 'project';
}

/** 예상 공수가 줄마다 뜻이 있는 기준 — 일감이 한 줄에만 속한다(사람 · 기간은 합계에만 예상이 있다). 서버와 같은 규칙 */
export function carriesEstimate(groupBy: EffortGroupBy): boolean {
  return groupBy === 'project' || groupBy === 'issue' || groupBy === 'type';
}

export function isPeriodGrouping(groupBy: EffortGroupBy): boolean {
  return groupBy === 'day' || groupBy === 'week' || groupBy === 'month';
}

/** 오늘 — 한국 시간(Asia/Seoul)의 날짜(yyyy-MM-dd). 서버가 작업 시간의 "오늘"을 정하는 시간대와 같다 */
export function seoulToday(now: Date = new Date()): string {
  const parts = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Seoul', year: 'numeric', month: '2-digit', day: '2-digit' }).formatToParts(now);
  const part = (type: string) => parts.find((p) => p.type === type)?.value ?? '';
  return `${part('year')}-${part('month')}-${part('day')}`;
}

function day(value: string | null): string | null {
  if (!value || !/^\d{4}-\d{2}-\d{2}$/.test(value)) {
    return null;
  }
  const parsed = dayjs(value);
  // 2026-02-31 처럼 없는 날은 dayjs 가 다음 달로 넘긴다 — 같은 날로 돌아오지 않으면 버린다
  return parsed.isValid() && parsed.format('YYYY-MM-DD') === value ? value : null;
}

/** 기간 → 첫날 · 끝날(포함). today 는 한국 시간의 오늘. 주는 월요일부터 */
export function rangeDates(filters: Pick<EffortFilters, 'range' | 'from' | 'to'>, today: string): { from: string | null; to: string | null } {
  const now = dayjs(today);
  const f = (d: dayjs.Dayjs) => d.format('YYYY-MM-DD');
  switch (filters.range) {
    case 'thisWeek':
      return { from: f(now.subtract((now.day() + 6) % 7, 'day')), to: today };
    case 'thisMonth':
      return { from: f(now.startOf('month')), to: today };
    case 'lastMonth': {
      const last = now.subtract(1, 'month');
      return { from: f(last.startOf('month')), to: f(last.endOf('month')) };
    }
    case 'thisYear':
      return { from: f(now.startOf('year')), to: today };
    case 'all':
      return { from: null, to: null };
    default:
      return { from: filters.from, to: filters.to };
  }
}

/** 주소에서 조건을 읽는다 — 모르는 값은 버리고 기본값(이번 달 · 범위의 기본 기준)으로 */
export function readEffortFilters(params: URLSearchParams, scope: EffortScope): EffortFilters {
  const from = day(params.get('from'));
  const to = day(params.get('to'));
  const rangeParam = params.get('range');
  const range: EffortRange =
    from || to ? 'custom' : (effortRanges as readonly string[]).includes(rangeParam ?? '') && rangeParam !== 'custom' ? (rangeParam as EffortRange) : DEFAULT_RANGE;
  const groupParam = params.get('groupBy');
  const allowed = effortGroupingsFor(scope);
  const groupBy = allowed.find((g) => g === groupParam) ?? defaultGroupBy(scope);
  const project = params.get('project');
  const person = params.get('person');
  const type = params.get('type');
  return {
    range,
    from: range === 'custom' ? from : null,
    to: range === 'custom' ? to : null,
    groupBy,
    project: scope === 'workspace' && project && KEY.test(project) ? project : null,
    person: person && UUID.test(person) ? person : null,
    type: type && KEY.test(type) ? type : null,
  };
}

/** 조건을 주소로 — 기본값과 비운 값은 적지 않는다 */
export function writeEffortFilters(filters: EffortFilters, scope: EffortScope): URLSearchParams {
  const params = new URLSearchParams();
  if (filters.range === 'custom') {
    if (filters.from) {
      params.set('from', filters.from);
    }
    if (filters.to) {
      params.set('to', filters.to);
    }
    // 두 끝을 모두 비운 직접 고르기는 "전체"와 같다
    if (!filters.from && !filters.to) {
      params.set('range', 'all');
    }
  } else if (filters.range !== DEFAULT_RANGE) {
    params.set('range', filters.range);
  }
  if (filters.groupBy !== defaultGroupBy(scope)) {
    params.set('groupBy', filters.groupBy);
  }
  if (scope === 'workspace' && filters.project) {
    params.set('project', filters.project);
  }
  if (filters.person) {
    params.set('person', filters.person);
  }
  if (filters.type) {
    params.set('type', filters.type);
  }
  return params;
}

/** 기본값에서 바꾼 조건이 있는가 (조건 지우기 단추) */
export function hasEffortFilters(filters: EffortFilters, scope: EffortScope): boolean {
  return writeEffortFilters(filters, scope).toString() !== '';
}

/**
 * 조건 → api 요청 값. fixedProject 는 프로젝트 화면의 프로젝트(조건의 project 대신).
 * 같은 값으로 CSV 도 받는다 — 화면과 CSV 가 같은 조건이다.
 */
export function toEffortQuery(filters: EffortFilters, today: string, fixedProject?: string): EffortQuery {
  const { from, to } = rangeDates(filters, today);
  const project = fixedProject ?? filters.project;
  const query: EffortQuery = { groupBy: filters.groupBy };
  if (from) {
    query.from = from;
  }
  if (to) {
    query.to = to;
  }
  if (project) {
    query.project = [project];
  }
  if (filters.person) {
    query.person = [filters.person];
  }
  if (filters.type) {
    query.type = [filters.type];
  }
  return query;
}

/** 막대 길이(%) — 표에서 가장 큰 실제 투입을 100 으로 */
export function barPercent(value: number, max: number): number {
  if (max <= 0 || value <= 0) {
    return 0;
  }
  return Math.min(100, Math.round((value / max) * 1000) / 10);
}

/** 차이 — 넘으면 +, 덜 쓰면 − (기호를 함께 적는다 — 색만으로 전하지 않는다) */
export function signedHours(value: number): string {
  const rounded = Math.round(value * 100) / 100;
  if (rounded > 0) {
    return `+${rounded}`;
  }
  if (rounded < 0) {
    return `−${Math.abs(rounded)}`;
  }
  return '0';
}

/** 내려받는 파일 이름 — 서버가 주는 이름과 같은 꼴 */
export function effortCsvName(query: EffortQuery): string {
  return `effort-${query.groupBy}-${query.from ?? 'all'}-${query.to ?? 'all'}.csv`;
}

/** 받은 파일을 이 브라우저에 저장한다 */
export function saveFile(blob: Blob, name: string): void {
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = name;
  document.body.appendChild(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(url), 0);
}
