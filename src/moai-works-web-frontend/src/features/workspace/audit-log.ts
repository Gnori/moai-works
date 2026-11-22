import dayjs from 'dayjs';

import type { AuditEntry, AuditQuery } from '@/shared/api/types';

/**
 * 감사 기록 화면의 조건 — 주소(?person=…&action=mcp.key&from=2026-11-01&to=2026-11-22)에 남겨,
 * 새로 고치거나 링크를 나눠도(MCP 키 화면의 "감사 기록" 링크) 같은 기록이 열린다(WEB-DESIGN §6).
 * 날짜는 이 브라우저의 날짜로 고르고, 서버에는 순간(ISO)으로 보낸다.
 */

/** 한 번에 받는 줄 수 — "더 보기"는 앞 쪽 마지막 줄의 시각과 id 를 before · beforeId 로 */
export const AUDIT_PAGE_SIZE = 50;

/** 동작 묶음 — 값은 동작 이름의 앞부분(점 경계에서만 맞는다: mcp.key → mcp.key.issue · mcp.key.expire) */
export const auditActionGroups = [
  { value: 'mcp.key', label: 'mcpKey' },
  { value: 'issue', label: 'issue' },
  { value: 'project', label: 'project' },
  { value: 'member', label: 'member' },
  { value: 'role', label: 'role' },
  { value: 'invitation', label: 'invitation' },
  { value: 'join_request', label: 'joinRequest' },
  { value: 'workspace', label: 'workspace' },
  { value: 'account', label: 'account' },
] as const;

export type AuditGroupLabel = (typeof auditActionGroups)[number]['label'];

export interface AuditFilters {
  /** 사람(사용자 id) — 그 사람이 한 일 · 그 사람을 대상으로 한 일 · 그 사람의 MCP 키에 일어난 일 */
  person: string | null;
  /** 동작 이름 또는 묶음 */
  action: string | null;
  /** 이 브라우저의 날짜 (YYYY-MM-DD) — 그날 0시부터 */
  from: string | null;
  /** 이 브라우저의 날짜 (YYYY-MM-DD) — 그날 끝까지 */
  to: string | null;
}

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const ACTION = /^[a-z][a-z_]*(\.[a-z][a-z_]*)*$/;

function day(value: string | null): string | null {
  if (!value || !/^\d{4}-\d{2}-\d{2}$/.test(value)) {
    return null;
  }
  const parsed = dayjs(value);
  // 2026-02-31 처럼 없는 날은 dayjs 가 다음 달로 넘긴다 — 같은 날로 돌아오지 않으면 버린다
  return parsed.isValid() && parsed.format('YYYY-MM-DD') === value ? value : null;
}

/** 주소에서 조건을 읽는다 — 모르는 값은 버린다 */
export function readAuditFilters(params: URLSearchParams): AuditFilters {
  const person = params.get('person');
  const action = params.get('action');
  return {
    person: person && UUID.test(person) ? person : null,
    action: action && action.length <= 100 && ACTION.test(action) ? action : null,
    from: day(params.get('from')),
    to: day(params.get('to')),
  };
}

/** 조건을 주소로 — 비운 값은 적지 않는다 */
export function writeAuditFilters(filters: AuditFilters): URLSearchParams {
  const params = new URLSearchParams();
  for (const name of ['person', 'action', 'from', 'to'] as const) {
    const value = filters[name];
    if (value) {
      params.set(name, value);
    }
  }
  return params;
}

export function hasAuditFilters(filters: AuditFilters): boolean {
  return !!(filters.person || filters.action || filters.from || filters.to);
}

/**
 * 고른 날(이 브라우저의 날짜) → 서버의 시각 범위. from 은 그날 0시, to 는 그 다음 날 0시(서버는 to 앞까지 — 그날 전체가 들어간다).
 * 시간대 · 서머타임은 dayjs 가 이 브라우저 기준으로 맞춘다.
 */
export function dateRangeToInstants(from: string | null, to: string | null): { from?: string; to?: string } {
  const range: { from?: string; to?: string } = {};
  const start = day(from);
  const end = day(to);
  if (start) {
    range.from = dayjs(start).startOf('day').toISOString();
  }
  if (end) {
    range.to = dayjs(end).add(1, 'day').startOf('day').toISOString();
  }
  return range;
}

/** 더 보기의 기준 — 앞 쪽 마지막 줄의 시각과 id. 둘을 함께 넘겨야 같은 밀리초의 줄을 건너뛰지 않는다 */
export interface AuditCursor {
  before: string;
  beforeId: string;
}

/** 조건 → api 요청 값. cursor 는 더 보기의 기준 */
export function toAuditQuery(filters: AuditFilters, cursor?: AuditCursor): AuditQuery {
  return {
    limit: AUDIT_PAGE_SIZE,
    person: filters.person ?? undefined,
    action: filters.action ?? undefined,
    ...dateRangeToInstants(filters.from, filters.to),
    before: cursor?.before,
    beforeId: cursor?.beforeId,
  };
}

/** 다음 쪽의 기준 — 한 쪽을 다 채웠으면 마지막 줄의 시각과 id, 덜 채웠으면 끝 */
export function nextAuditCursor(page: readonly Pick<AuditEntry, 'occurredAt' | 'id'>[]): AuditCursor | undefined {
  const last = page[page.length - 1];
  if (page.length < AUDIT_PAGE_SIZE || !last) {
    return undefined;
  }
  return { before: last.occurredAt, beforeId: last.id };
}

/**
 * 감사 내용(JSON 글자)을 한 줄 글로 — 글자로만 보이고 HTML 로 해석하지 않는다.
 * 프로그램 정보(userAgent)는 따로 꺼내 짧게 보이게 하고, 빈 값은 뺀다. JSON 이 아니면 받은 글 그대로.
 */
export function parseAuditDetails(details: string | null | undefined): { text: string; userAgent: string | null } {
  if (!details) {
    return { text: '', userAgent: null };
  }
  try {
    const value: unknown = JSON.parse(details);
    if (typeof value !== 'object' || value === null || Array.isArray(value)) {
      return { text: details, userAgent: null };
    }
    let userAgent: string | null = null;
    const parts: string[] = [];
    for (const [k, v] of Object.entries(value as Record<string, unknown>)) {
      if (k === 'userAgent') {
        userAgent = typeof v === 'string' && v.trim() ? v : null;
        continue;
      }
      if (v === null || v === undefined || v === '') {
        continue;
      }
      parts.push(`${k}: ${typeof v === 'object' ? JSON.stringify(v) : String(v)}`);
    }
    return { text: parts.join(' · '), userAgent };
  } catch {
    return { text: details, userAgent: null };
  }
}
