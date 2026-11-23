import { issueStatuses, type IssueListParams, type IssueSort, type IssueSortKey, type IssueStatus } from '@/shared/api/types';

/**
 * 일감 목록의 조건 — 주소(?status=NEW&status=DONE&assignee=me&sort=-updated&page=2)에 남겨,
 * 새로 고치거나 링크를 나눠도 같은 목록이 열린다(WEB-DESIGN §6). 주소의 page 는 1부터, api 의 page 는 0부터다.
 */
export interface IssueFilters {
  status: IssueStatus[];
  type: string[];
  /** me · none · 사용자 id. null 이면 누구나 */
  assignee: string | null;
  /** me · 사용자 id. null 이면 누구나 */
  manager: string | null;
  /** 에이전트 세션 이름 · none(세션 없음). null 이면 누구나 */
  agentSession: string | null;
  /** 모듈 key — 그 가운데 하나라도 붙은 일감. 비면 전부 */
  module: string[];
  /** 맨 위 일감만 */
  top: boolean;
  q: string;
  sort: IssueSort;
  /** 1부터 */
  page: number;
}

export const DEFAULT_SORT: IssueSort = '-number';
export const ISSUE_PAGE_SIZE = 50;

/** 정렬할 수 있는 항목 — api 의 IssueService.sortOf 와 같다 */
export const issueSortKeys: readonly IssueSortKey[] = ['number', 'updated', 'due', 'start', 'planned'];

/** 고를 수 있는 정렬 (목록 위 정렬 칸의 순서) */
export const issueSorts: readonly IssueSort[] = [
  '-number',
  'number',
  '-updated',
  'updated',
  'due',
  '-due',
  'start',
  '-start',
  'planned',
  '-planned',
];

/** 모듈 key 모양 — api 의 ProjectModule.KEY_PATTERN 과 같다 */
const MODULE_KEY = /^[a-z]([a-z0-9-]{0,30}[a-z0-9])?$/;

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

function person(value: string | null, allowNone: boolean): string | null {
  if (!value) {
    return null;
  }
  if (value === 'me' || (allowNone && value === 'none') || UUID.test(value)) {
    return value;
  }
  return null;
}

/** 주소의 세션 이름 — 앞뒤 공백을 빼고 100자까지, 제어 문자가 있으면 버린다(api 의 규칙과 같다) */
function session(value: string | null): string | null {
  const v = (value ?? '').trim();
  // eslint-disable-next-line no-control-regex -- 제어 문자를 막는 검사
  if (!v || v.length > 100 || /[\u0000-\u001f\u007f-\u009f]/.test(v)) {
    return null;
  }
  return v;
}

function isSort(value: string | null): value is IssueSort {
  return value !== null && (issueSorts as readonly string[]).includes(value);
}

/** 주소에서 조건을 읽는다 — 모르는 값은 버린다(손으로 고친 주소도 목록이 깨지지 않게) */
export function readIssueFilters(params: URLSearchParams): IssueFilters {
  const status = [...new Set(params.getAll('status'))].filter((s): s is IssueStatus =>
    (issueStatuses as readonly string[]).includes(s),
  );
  const type = [...new Set(params.getAll('type').filter((s) => /^[a-z][a-z0-9-]{0,63}$/.test(s)))];
  const sort = params.get('sort');
  const page = Number.parseInt(params.get('page') ?? '', 10);
  return {
    status,
    type,
    assignee: person(params.get('assignee'), true),
    manager: person(params.get('manager'), false),
    agentSession: session(params.get('agentSession')),
    module: [...new Set(params.getAll('module').filter((s) => MODULE_KEY.test(s)))],
    top: params.get('top') === '1',
    q: (params.get('q') ?? '').slice(0, 200),
    sort: isSort(sort) ? sort : DEFAULT_SORT,
    page: Number.isFinite(page) && page >= 1 ? page : 1,
  };
}

/** 조건을 주소로 — 기본값은 적지 않는다 */
export function writeIssueFilters(filters: IssueFilters): URLSearchParams {
  const params = new URLSearchParams();
  filters.status.forEach((s) => params.append('status', s));
  filters.type.forEach((s) => params.append('type', s));
  if (filters.assignee) {
    params.set('assignee', filters.assignee);
  }
  if (filters.manager) {
    params.set('manager', filters.manager);
  }
  if (filters.agentSession) {
    params.set('agentSession', filters.agentSession);
  }
  filters.module.forEach((m) => params.append('module', m));
  if (filters.top) {
    params.set('top', '1');
  }
  if (filters.q.trim()) {
    params.set('q', filters.q.trim());
  }
  if (filters.sort !== DEFAULT_SORT) {
    params.set('sort', filters.sort);
  }
  if (filters.page > 1) {
    params.set('page', String(filters.page));
  }
  return params;
}

/** 목록을 좁히는 조건이 있는가 (정렬 · 쪽은 빼고) — "조건 지우기"를 보일지 */
export function hasIssueFilters(filters: IssueFilters): boolean {
  return (
    filters.status.length > 0 ||
    filters.type.length > 0 ||
    filters.assignee !== null ||
    filters.manager !== null ||
    filters.agentSession !== null ||
    filters.module.length > 0 ||
    filters.top ||
    filters.q.trim() !== ''
  );
}

/** 조건 → api 요청 값. me 는 내 id 로 바꾼다 */
export function toIssueListParams(filters: IssueFilters, myId: string): IssueListParams {
  const who = (value: string | null) => (value === 'me' ? myId : (value ?? undefined));
  return {
    status: filters.status.length > 0 ? filters.status : undefined,
    type: filters.type.length > 0 ? filters.type : undefined,
    assignee: who(filters.assignee),
    manager: who(filters.manager),
    agentSession: filters.agentSession ?? undefined,
    module: filters.module.length > 0 ? filters.module : undefined,
    parent: filters.top ? 'top' : undefined,
    q: filters.q.trim() || undefined,
    sort: filters.sort,
    page: filters.page - 1,
    size: ISSUE_PAGE_SIZE,
  };
}

/** 표 머리를 눌렀을 때의 정렬 — 같은 항목이면 방향을 뒤집고, 다른 항목이면 그 항목의 기본 방향(번호 · 고친 날은 최근부터, 날짜는 빠른 것부터) */
export function toggleIssueSort(current: IssueSort, key: IssueSortKey): IssueSort {
  const currentKey = current.replace(/^-/, '');
  if (currentKey === key) {
    return (current.startsWith('-') ? key : `-${key}`) as IssueSort;
  }
  return key === 'number' || key === 'updated' ? `-${key}` : key;
}

/** 이 항목으로 정렬하고 있으면 방향, 아니면 false */
export function sortDirection(current: IssueSort, key: IssueSortKey): 'asc' | 'desc' | false {
  if (current.replace(/^-/, '') !== key) {
    return false;
  }
  return current.startsWith('-') ? 'desc' : 'asc';
}
