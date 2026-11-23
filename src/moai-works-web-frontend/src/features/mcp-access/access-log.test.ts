import { describe, expect, it } from 'vitest';

import {
  ACCESS_PAGE_SIZE,
  accessClient,
  accessTarget,
  emptyAccessFilters,
  hasAccessFilters,
  hasAdvancedFilters,
  nextAccessCursor,
  outcomeColor,
  prettyArguments,
  readAccessFilters,
  toAccessQuery,
  toExportQuery,
  writeAccessFilters,
} from './access-log';

/** MCP 접근 기록 화면의 조건 — 주소에 남고(새로 고쳐도 같은 결과) 그대로 서버 요청이 된다 */

const OWNER = '11111111-2222-3333-4444-555555555555';
const KEY = 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee';

describe('주소 ↔ 조건', () => {
  it('모든 조건을 읽고 같은 주소로 다시 쓴다', () => {
    const url = `person=${OWNER}&key=${KEY}&tool=create_issue&operation=CREATE&outcome=DENIED&client=claude&project=web-shop&session=abc-123&from=2026-11-01&to=2026-11-22`;
    const filters = readAccessFilters(new URLSearchParams(url));
    expect(filters).toEqual({
      person: OWNER,
      key: KEY,
      tool: 'create_issue',
      operation: 'CREATE',
      outcome: 'DENIED',
      client: 'claude',
      project: 'web-shop',
      session: 'abc-123',
      from: '2026-11-01',
      to: '2026-11-22',
    });
    expect(writeAccessFilters(filters).toString()).toBe(url);
    expect(hasAccessFilters(filters)).toBe(true);
    expect(hasAdvancedFilters(filters)).toBe(true);
  });

  it('모르는 값 · 모양이 틀린 값은 버린다(손으로 고친 주소도 화면이 깨지지 않게)', () => {
    const filters = readAccessFilters(
      new URLSearchParams('person=me&key=x&tool=DROP TABLE&operation=EXECUTE&outcome=MAYBE&project=웹&from=2026-02-31&to=어제&session=a b'),
    );
    expect(filters).toEqual(emptyAccessFilters);
    expect(hasAccessFilters(filters)).toBe(false);
    expect(writeAccessFilters(filters).toString()).toBe('');
  });

  it('기본 조건(키 주인 · 작업 · 결과 · 날짜)만 걸려 있으면 더 보기를 접은 채로 연다', () => {
    const filters = readAccessFilters(new URLSearchParams(`person=${OWNER}&operation=READ&from=2026-11-01`));
    expect(hasAccessFilters(filters)).toBe(true);
    expect(hasAdvancedFilters(filters)).toBe(false);
  });
});

describe('조건 → 서버 요청', () => {
  it('날짜는 이 브라우저의 하루 전체(시작 0시 · 다음 날 0시 앞까지)', () => {
    const filters = { ...emptyAccessFilters, outcome: 'ERROR' as const, from: '2026-11-01', to: '2026-11-22' };
    expect(toAccessQuery(filters)).toEqual({
      limit: ACCESS_PAGE_SIZE,
      outcome: 'ERROR',
      from: new Date(2026, 10, 1).toISOString(),
      to: new Date(2026, 10, 23).toISOString(),
      before: undefined,
      beforeId: undefined,
    });
  });

  it('더 보기는 앞 쪽 마지막 줄의 시각과 id 를 함께 — 한 쪽을 덜 채웠으면 끝', () => {
    const page = Array.from({ length: ACCESS_PAGE_SIZE }, (_, i) => ({ id: `r${i}`, occurredAt: `2026-11-22T09:00:00.${String(i).padStart(3, '0')}Z` }));
    const cursor = nextAccessCursor(page);
    expect(cursor).toEqual({ before: page[ACCESS_PAGE_SIZE - 1]?.occurredAt, beforeId: `r${ACCESS_PAGE_SIZE - 1}` });
    expect(toAccessQuery({ ...emptyAccessFilters, tool: 'get_issue' }, cursor)).toMatchObject({ tool: 'get_issue', ...cursor });
    expect(nextAccessCursor(page.slice(1))).toBeUndefined();
  });

  it('내려받기 요청에는 쪽 · 커서가 없다', () => {
    expect(toExportQuery({ ...emptyAccessFilters, person: OWNER, session: 's1' })).toEqual({ person: OWNER, session: 's1' });
  });
});

describe('한 줄 보이기', () => {
  it('대상 · 클라이언트 · 결과 색', () => {
    expect(accessTarget({ projectKey: 'web', issueNumber: 12, targetRef: '#13' })).toBe('web #12 → #13');
    expect(accessTarget({ projectKey: 'web', issueNumber: null, targetRef: null })).toBe('web');
    expect(accessTarget({ projectKey: null, issueNumber: null, targetRef: null })).toBe('');
    expect(accessClient({ clientName: 'claude-code', clientVersion: '2.0.1' })).toBe('claude-code 2.0.1');
    expect(accessClient({ clientName: null, clientVersion: null })).toBe('');
    expect(outcomeColor('OK')).toBe('success');
    expect(outcomeColor('DENIED')).toBe('warning');
    expect(outcomeColor('ERROR')).toBe('error');
  });

  it('인자 요약은 보기 좋게, JSON 이 아니면 받은 글 그대로(HTML 로 해석하지 않는다)', () => {
    expect(prettyArguments('{"project":"web","description":{"length":10,"sha256":"ab"}}')).toBe(
      '{\n  "project": "web",\n  "description": {\n    "length": 10,\n    "sha256": "ab"\n  }\n}',
    );
    expect(prettyArguments('<b>x</b>')).toBe('<b>x</b>');
    expect(prettyArguments(null)).toBe('');
  });
});
