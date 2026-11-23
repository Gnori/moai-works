import { describe, expect, it } from 'vitest';

import {
  AUDIT_PAGE_SIZE,
  auditActionGroups,
  dateRangeToInstants,
  hasAuditFilters,
  nextAuditCursor,
  parseAuditDetails,
  readAuditFilters,
  toAuditQuery,
  writeAuditFilters,
} from './audit-log';

const PERSON = '11111111-2222-3333-4444-555555555555';

/** 이 컴퓨터 시간대의 그날 0시 — 시간대가 어디든 같게 견준다 */
function localMidnight(year: number, month: number, day: number): string {
  return new Date(year, month - 1, day).toISOString();
}

describe('날짜 범위 → 서버 시각', () => {
  it('시작 날은 그날 0시, 끝 날은 그 다음 날 0시(그날 전체가 들어간다)', () => {
    expect(dateRangeToInstants('2026-11-01', '2026-11-22')).toEqual({
      from: localMidnight(2026, 11, 1),
      to: localMidnight(2026, 11, 23),
    });
  });

  it('달 · 해의 끝을 넘긴다', () => {
    expect(dateRangeToInstants(null, '2026-11-30').to).toBe(localMidnight(2026, 12, 1));
    expect(dateRangeToInstants(null, '2026-12-31').to).toBe(localMidnight(2027, 1, 1));
    expect(dateRangeToInstants(null, '2028-02-28').to).toBe(localMidnight(2028, 2, 29));
  });

  it('한쪽만 있거나 없으면 그쪽만 보낸다 · 틀린 날은 버린다', () => {
    expect(dateRangeToInstants('2026-11-01', null)).toEqual({ from: localMidnight(2026, 11, 1) });
    expect(dateRangeToInstants(null, null)).toEqual({});
    expect(dateRangeToInstants('2026-02-31', 'yesterday')).toEqual({});
  });
});

describe('감사 기록 조건 (주소 ↔ api)', () => {
  it('비어 있는 주소는 조건 없음', () => {
    const filters = readAuditFilters(new URLSearchParams());
    expect(filters).toEqual({ person: null, action: null, from: null, to: null });
    expect(hasAuditFilters(filters)).toBe(false);
    expect(writeAuditFilters(filters).toString()).toBe('');
  });

  it('MCP 키 화면의 링크(사람 + mcp.key)를 그대로 읽고 다시 쓴다', () => {
    const search = `person=${PERSON}&action=mcp.key&from=2026-11-01&to=2026-11-22`;
    const filters = readAuditFilters(new URLSearchParams(search));
    expect(filters).toEqual({ person: PERSON, action: 'mcp.key', from: '2026-11-01', to: '2026-11-22' });
    expect(hasAuditFilters(filters)).toBe(true);
    expect(writeAuditFilters(filters).toString()).toBe(search);
  });

  it('모르는 값은 버린다', () => {
    const filters = readAuditFilters(new URLSearchParams('person=kim&action=MCP.KEY;drop&from=2026-13-01&to=22-11-2026'));
    expect(filters).toEqual({ person: null, action: null, from: null, to: null });
    // 동작 이름 하나(묶음이 아닌 것)도 받는다
    expect(readAuditFilters(new URLSearchParams('action=issue.work_log.add')).action).toBe('issue.work_log.add');
  });

  it('api 요청 값 — 50건씩, 날짜는 순간으로, 더 보기는 시각 + id', () => {
    const filters = readAuditFilters(new URLSearchParams(`person=${PERSON}&action=mcp.key&to=2026-11-22`));
    expect(toAuditQuery(filters)).toEqual({
      limit: 50,
      person: PERSON,
      action: 'mcp.key',
      to: localMidnight(2026, 11, 23),
      before: undefined,
      beforeId: undefined,
    });
    expect(toAuditQuery(readAuditFilters(new URLSearchParams()), { before: '2026-11-22T01:02:03.004Z', beforeId: 'a1' })).toEqual({
      limit: 50,
      person: undefined,
      action: undefined,
      before: '2026-11-22T01:02:03.004Z',
      beforeId: 'a1',
    });
  });

  it('다음 쪽은 한 쪽을 다 채웠을 때만 — 마지막 줄의 시각과 id', () => {
    // 같은 밀리초의 줄이 쪽 경계에 걸려도 id 로 이어 받는다
    const full = Array.from({ length: AUDIT_PAGE_SIZE }, (_, i) => ({ id: `id-${i}`, occurredAt: '2026-11-22T00:00:00.123Z' }));
    expect(nextAuditCursor(full)).toEqual({ before: '2026-11-22T00:00:00.123Z', beforeId: `id-${AUDIT_PAGE_SIZE - 1}` });
    expect(nextAuditCursor(full.slice(0, 10))).toBeUndefined();
    expect(nextAuditCursor([])).toBeUndefined();
  });

  it('동작 묶음은 서버의 묶음 앞부분과 같다', () => {
    expect(auditActionGroups.map((g) => g.value)).toEqual([
      'mcp.key',
      'mcp.access_log',
      'issue',
      'project',
      'member',
      'role',
      'invitation',
      'join_request',
      'workspace',
      'account',
      'admin',
    ]);
  });
});

describe('감사 내용 (글자로만)', () => {
  it('프로그램 정보를 따로 꺼내고 빈 값은 뺀다', () => {
    const details = JSON.stringify({ keyPrefix: 'mai-wks-Ab12', reason: 'MANUAL', note: '', userAgent: 'curl/8.4.0' });
    expect(parseAuditDetails(details)).toEqual({ text: 'keyPrefix: mai-wks-Ab12 · reason: MANUAL', userAgent: 'curl/8.4.0' });
  });

  it('HTML 은 해석하지 않고 글자 그대로 둔다 · JSON 이 아니면 받은 글 그대로', () => {
    expect(parseAuditDetails(JSON.stringify({ name: '<img src=x onerror=alert(1)>' })).text).toBe('name: <img src=x onerror=alert(1)>');
    expect(parseAuditDetails('not json')).toEqual({ text: 'not json', userAgent: null });
    expect(parseAuditDetails('')).toEqual({ text: '', userAgent: null });
    expect(parseAuditDetails(JSON.stringify({ roles: ['admin'] })).text).toBe('roles: ["admin"]');
  });
});
