import { issueLinkTypes, issueStatuses, type IssueChange, type IssueLinkType, type IssueStatus } from '@/shared/api/types';

/**
 * 이력 한 줄의 바뀐 필드를 사람이 읽을 모양으로 푼다 — 말(번역)은 화면이 붙이고, 여기서는 값의 뜻만 가린다.
 * 서버가 적는 모양(IssueService.diff · link · unlink):
 *   manager · assignee → {id, name} · parent → 번호 · link → {type, direction, number}(예전 줄은 "BLOCKS #12") ·
 *   description → 전 · 후 글 전체 ·
 *   type → 유형 key · status → 상태 · 날짜 → yyyy-MM-dd · title → 글자 ·
 *   estimatedHours → "8" · "2.5" 같은 글자(시간) ·
 *   workLog → {id, user: {id, name}, date, hours, note?} — 넣었으면 before 가 null, 지웠으면 after 가 null
 * 리비전 차이(IssueService.diff)의 fields 도 같은 모양이다.
 */

/** 이력에 나오는, 화면이 이름을 아는 필드 */
export const journalFields = [
  'type',
  'title',
  'status',
  'manager',
  'assignee',
  'parent',
  'dueDate',
  'startDate',
  'plannedEndDate',
  'completedOn',
  'closedOn',
  'estimatedHours',
] as const;
export type JournalField = (typeof journalFields)[number];

export type ChangeValue =
  | { kind: 'empty' }
  | { kind: 'text'; text: string }
  | { kind: 'person'; name: string }
  | { kind: 'issue'; number: number }
  | { kind: 'status'; status: IssueStatus }
  | { kind: 'type'; key: string }
  | { kind: 'date'; date: string }
  /** 시간 — 서버가 준 글 그대로("8" · "2.5") */
  | { kind: 'hours'; hours: string };

export type ReadableChange =
  | { kind: 'field'; field: JournalField; before: ChangeValue; after: ChangeValue }
  /** 화면이 모르는 필드 — 서버가 새로 더했을 때. 이름 · 값을 적힌 대로 보인다 */
  | { kind: 'unknown'; field: string; before: ChangeValue; after: ChangeValue }
  | { kind: 'description' }
  | {
      kind: 'link';
      action: 'added' | 'removed';
      type: IssueLinkType | null;
      /** 이 일감 쪽에서 본 방향 — 예전 줄에는 없다 */
      direction: 'OUTGOING' | 'INCOMING' | null;
      number: number | null;
      raw: string;
    }
  | {
      /** 작업 시간 기록을 넣거나 지웠다 — 일한 사람(지운 사람은 이력 줄의 사람) · 일한 날 · 시간 · 메모 */
      kind: 'workLog';
      action: 'added' | 'removed';
      user: string | null;
      date: string | null;
      hours: string | null;
      note: string | null;
    };

const DATE_FIELDS = new Set<string>(['dueDate', 'startDate', 'plannedEndDate', 'completedOn', 'closedOn']);
const LINK = /^([A-Z_]+)\s+#(\d+)$/;

function isJournalField(field: string): field is JournalField {
  return (journalFields as readonly string[]).includes(field);
}

function asText(value: unknown): ChangeValue {
  if (value === null || value === undefined || value === '') {
    return { kind: 'empty' };
  }
  if (typeof value === 'object') {
    const named = value as { name?: unknown };
    return typeof named.name === 'string' ? { kind: 'text', text: named.name } : { kind: 'text', text: JSON.stringify(value) };
  }
  return { kind: 'text', text: String(value) };
}

/** 필드에 맞춰 값 하나를 읽는다 */
export function readValue(field: string, value: unknown): ChangeValue {
  if (value === null || value === undefined || value === '') {
    return { kind: 'empty' };
  }
  if (field === 'manager' || field === 'assignee') {
    if (typeof value === 'object') {
      const p = value as { name?: unknown; id?: unknown };
      const name = typeof p.name === 'string' && p.name ? p.name : typeof p.id === 'string' ? p.id : '';
      return name ? { kind: 'person', name } : { kind: 'empty' };
    }
    return { kind: 'person', name: String(value) };
  }
  if (field === 'parent') {
    const n = typeof value === 'number' ? value : Number.parseInt(String(value).replace(/^#/, ''), 10);
    return Number.isFinite(n) ? { kind: 'issue', number: n } : asText(value);
  }
  if (field === 'status' && typeof value === 'string' && (issueStatuses as readonly string[]).includes(value)) {
    return { kind: 'status', status: value as IssueStatus };
  }
  if (field === 'type' && typeof value === 'string') {
    return { kind: 'type', key: value };
  }
  if (DATE_FIELDS.has(field) && typeof value === 'string') {
    return { kind: 'date', date: value };
  }
  if (field === 'estimatedHours' && (typeof value === 'string' || typeof value === 'number')) {
    return { kind: 'hours', hours: String(value) };
  }
  return asText(value);
}

function readWorkLog(change: IssueChange): ReadableChange {
  const added = change.before === null || change.before === undefined;
  const value = (added ? change.after : change.before) as Record<string, unknown> | null | undefined;
  const v = value && typeof value === 'object' ? value : {};
  const user = v.user && typeof v.user === 'object' ? (v.user as { name?: unknown; id?: unknown }) : null;
  const userName = user && typeof user.name === 'string' && user.name ? user.name : user && typeof user.id === 'string' ? user.id : null;
  const text = (x: unknown): string | null => (typeof x === 'string' && x ? x : typeof x === 'number' ? String(x) : null);
  return {
    kind: 'workLog',
    action: added ? 'added' : 'removed',
    user: userName,
    date: text(v.date),
    hours: text(v.hours),
    note: text(v.note),
  };
}

function linkType(value: unknown): IssueLinkType | null {
  return typeof value === 'string' && (issueLinkTypes as readonly string[]).includes(value) ? (value as IssueLinkType) : null;
}

function readLink(change: IssueChange): ReadableChange {
  const added = change.before === null || change.before === undefined;
  const value = added ? change.after : change.before;
  const action = added ? 'added' : 'removed';
  if (value && typeof value === 'object') {
    const v = value as { type?: unknown; direction?: unknown; number?: unknown };
    const direction = v.direction === 'OUTGOING' || v.direction === 'INCOMING' ? v.direction : null;
    const number = typeof v.number === 'number' ? v.number : null;
    return { kind: 'link', action, type: linkType(v.type), direction, number, raw: JSON.stringify(value) };
  }
  const raw = String(value ?? '');
  const match = LINK.exec(raw.trim());
  return { kind: 'link', action, type: match ? linkType(match[1]) : null, direction: null, number: match ? Number(match[2]) : null, raw };
}

export function readChange(change: IssueChange): ReadableChange {
  if (change.field === 'description') {
    return { kind: 'description' };
  }
  if (change.field === 'link') {
    return readLink(change);
  }
  if (change.field === 'workLog') {
    return readWorkLog(change);
  }
  const before = readValue(change.field, change.before);
  const after = readValue(change.field, change.after);
  return isJournalField(change.field)
    ? { kind: 'field', field: change.field, before, after }
    : { kind: 'unknown', field: change.field, before, after };
}
