import { dateRangeToInstants } from '@/features/workspace/audit-log';
import {
  mcpAccessOperations,
  mcpAccessOutcomes,
  type McpAccessEntry,
  type McpAccessOperation,
  type McpAccessOutcome,
  type McpAccessQuery,
} from '@/shared/api/types';

/**
 * MCP 접근 기록 화면의 조건 — 워크스페이스 화면은 주소(?person=…&operation=CREATE&outcome=DENIED&from=2026-11-01)에 남겨
 * 새로 고치거나 링크를 나눠도(키 화면의 "접근 기록" 링크) 같은 기록이 열린다(WEB-DESIGN §6). 내 키 대화상자는 같은 조건을 화면 상태로 쓴다.
 * 날짜는 이 브라우저의 날짜로 고르고, 서버에는 순간(ISO)으로 보낸다(감사 기록과 같다).
 */

/** 한 번에 받는 줄 수 — "더 보기"는 앞 쪽 마지막 줄의 시각과 id 를 before · beforeId 로 */
export const ACCESS_PAGE_SIZE = 50;

/** 지금 있는 MCP 도구 — 도구 조건의 고르기 목록(받은 기록의 도구도 더한다) */
export const knownMcpTools = [
  'list_workspaces',
  'list_projects',
  'list_issue_types',
  'search_issues',
  'get_issue',
  'get_issue_template',
  'create_issue',
  'update_issue',
  'delete_issue',
  'add_comment',
  'log_work',
  'link_issues',
  'list_attachments',
  'request_attachment_upload',
  'request_attachment_download',
  'delete_attachment',
  'list_issue_templates',
  'set_issue_template',
  'reset_issue_template',
] as const;

export interface AccessFilters {
  /** 키 주인(사용자 id) */
  person: string | null;
  /** 키 id */
  key: string | null;
  tool: string | null;
  operation: McpAccessOperation | null;
  outcome: McpAccessOutcome | null;
  /** 클라이언트 이름에 든 말 */
  client: string | null;
  /** 프로젝트 key */
  project: string | null;
  /** MCP 세션 id — 한 줄의 상세에서 "같은 세션"으로 건다 */
  session: string | null;
  /** 이 브라우저의 날짜 (YYYY-MM-DD) — 그날 0시부터 */
  from: string | null;
  /** 이 브라우저의 날짜 (YYYY-MM-DD) — 그날 끝까지 */
  to: string | null;
}

export const emptyAccessFilters: AccessFilters = {
  person: null,
  key: null,
  tool: null,
  operation: null,
  outcome: null,
  client: null,
  project: null,
  session: null,
  from: null,
  to: null,
};

const filterNames = ['person', 'key', 'tool', 'operation', 'outcome', 'client', 'project', 'session', 'from', 'to'] as const;
/** 기본으로 접혀 있는 조건 — 하나라도 걸려 있으면 펼친 채로 연다 */
export const advancedFilterNames = ['key', 'tool', 'client', 'project', 'session'] as const;

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const TOOL = /^[a-z][a-z0-9_]{0,59}$/;
const PROJECT = /^[A-Za-z0-9][A-Za-z0-9_-]{0,31}$/;
const SESSION = /^[\x21-\x7e]{1,100}$/;

function day(value: string | null): string | null {
  if (!value || !/^\d{4}-\d{2}-\d{2}$/.test(value)) {
    return null;
  }
  const [y, m, d] = value.split('-').map(Number) as [number, number, number];
  const parsed = new Date(y, m - 1, d);
  // 2026-02-31 처럼 없는 날은 버린다
  return parsed.getFullYear() === y && parsed.getMonth() === m - 1 && parsed.getDate() === d ? value : null;
}

function oneOf<T extends string>(value: string | null, allowed: readonly T[]): T | null {
  return value !== null && (allowed as readonly string[]).includes(value) ? (value as T) : null;
}

function text(value: string | null, max: number): string | null {
  const v = value?.trim();
  return v ? v.slice(0, max) : null;
}

/** 주소에서 조건을 읽는다 — 모르는 값은 버린다(손으로 고친 주소도 화면이 깨지지 않게) */
export function readAccessFilters(params: URLSearchParams): AccessFilters {
  const person = params.get('person');
  const key = params.get('key');
  const tool = params.get('tool');
  const project = params.get('project');
  const session = params.get('session');
  return {
    person: person && UUID.test(person) ? person : null,
    key: key && UUID.test(key) ? key : null,
    tool: tool && TOOL.test(tool) ? tool : null,
    operation: oneOf(params.get('operation'), mcpAccessOperations),
    outcome: oneOf(params.get('outcome'), mcpAccessOutcomes),
    client: text(params.get('client'), 200),
    project: project && PROJECT.test(project) ? project : null,
    session: session && SESSION.test(session) ? session : null,
    from: day(params.get('from')),
    to: day(params.get('to')),
  };
}

/** 조건을 주소로 — 비운 값은 적지 않는다 */
export function writeAccessFilters(filters: AccessFilters): URLSearchParams {
  const params = new URLSearchParams();
  for (const name of filterNames) {
    const value = filters[name];
    if (value) {
      params.set(name, value);
    }
  }
  return params;
}

export function hasAccessFilters(filters: AccessFilters): boolean {
  return filterNames.some((name) => !!filters[name]);
}

export function hasAdvancedFilters(filters: AccessFilters): boolean {
  return advancedFilterNames.some((name) => !!filters[name]);
}

/** 더 보기의 기준 — 앞 쪽 마지막 줄의 시각과 id */
export interface AccessCursor {
  before: string;
  beforeId: string;
}

/** 조건 → api 요청 값(목록). cursor 는 더 보기의 기준 */
export function toAccessQuery(filters: AccessFilters, cursor?: AccessCursor): McpAccessQuery {
  return { limit: ACCESS_PAGE_SIZE, ...toExportQuery(filters), before: cursor?.before, beforeId: cursor?.beforeId };
}

/** 조건 → 내려받기 요청 값(쪽 · 커서 없이) — 비운 값은 넣지 않는다 */
export function toExportQuery(filters: AccessFilters): McpAccessQuery {
  const query: McpAccessQuery = {};
  if (filters.person) query.person = filters.person;
  if (filters.key) query.key = filters.key;
  if (filters.tool) query.tool = filters.tool;
  if (filters.operation) query.operation = filters.operation;
  if (filters.outcome) query.outcome = filters.outcome;
  if (filters.client) query.client = filters.client;
  if (filters.project) query.project = filters.project;
  if (filters.session) query.session = filters.session;
  return { ...query, ...dateRangeToInstants(filters.from, filters.to) };
}

/** 다음 쪽의 기준 — 한 쪽을 다 채웠으면 마지막 줄의 시각과 id, 덜 채웠으면 끝 */
export function nextAccessCursor(page: readonly Pick<McpAccessEntry, 'occurredAt' | 'id'>[]): AccessCursor | undefined {
  const last = page[page.length - 1];
  if (page.length < ACCESS_PAGE_SIZE || !last) {
    return undefined;
  }
  return { before: last.occurredAt, beforeId: last.id };
}

/** 결과 칩 색 — 색만으로 전하지 않게 칩에는 늘 결과 이름을 함께 적는다 */
export function outcomeColor(outcome: McpAccessOutcome): 'success' | 'warning' | 'error' {
  return outcome === 'OK' ? 'success' : outcome === 'DENIED' ? 'warning' : 'error';
}

/** 한 줄이 무엇을 했나 — 도구 이름, 도구가 없으면 MCP 메서드 */
export function accessAction(entry: Pick<McpAccessEntry, 'tool' | 'mcpMethod'>): string {
  return entry.tool ?? entry.mcpMethod;
}

/** 대상 한 줄 — 프로젝트 #번호 · 그 밖 (없으면 빈 글) */
export function accessTarget(entry: Pick<McpAccessEntry, 'projectKey' | 'issueNumber' | 'targetRef'>): string {
  const parts: string[] = [];
  if (entry.projectKey) {
    parts.push(entry.issueNumber !== null && entry.issueNumber !== undefined ? `${entry.projectKey} #${entry.issueNumber}` : entry.projectKey);
  } else if (entry.issueNumber !== null && entry.issueNumber !== undefined) {
    parts.push(`#${entry.issueNumber}`);
  }
  if (entry.targetRef) {
    parts.push(`→ ${entry.targetRef}`);
  }
  return parts.join(' ');
}

/** 클라이언트 한 줄 — 이름 · 판 */
export function accessClient(entry: Pick<McpAccessEntry, 'clientName' | 'clientVersion'>): string {
  return [entry.clientName, entry.clientVersion].filter(Boolean).join(' ');
}

/** 인자 요약(JSON 글자)을 보기 좋게 — JSON 이 아니면 받은 글 그대로. 글자로만 보이고 HTML 로 해석하지 않는다 */
export function prettyArguments(value: string | null | undefined): string {
  if (!value) {
    return '';
  }
  try {
    return JSON.stringify(JSON.parse(value), null, 2);
  } catch {
    return value;
  }
}
