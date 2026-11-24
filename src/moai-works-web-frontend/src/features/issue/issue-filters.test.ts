import { describe, expect, it } from 'vitest';

import {
  hasIssueFilters,
  issueNumberQuery,
  readIssueFilters,
  toIssueListParams,
  toggleIssueSort,
  writeIssueFilters,
  sortDirection,
} from './issue-filters';

const ME = '11111111-2222-3333-4444-555555555555';

describe('일감 목록 조건 (주소 ↔ api)', () => {
  it('비어 있는 주소는 기본 조건이다', () => {
    const filters = readIssueFilters(new URLSearchParams());
    expect(filters).toEqual({ project: [], status: [], type: [], assignee: null, manager: null, agentSession: null, module: [], top: false, q: '', sort: '-number', page: 1 });
    expect(hasIssueFilters(filters)).toBe(false);
    expect(writeIssueFilters(filters).toString()).toBe('');
  });

  it('주소의 조건을 읽고 같은 주소로 다시 쓴다', () => {
    const search = 'status=NEW&status=IN_PROGRESS&type=bug&assignee=me&manager=me&top=1&q=%EB%A1%9C%EA%B7%B8%EC%9D%B8&sort=due&page=3';
    const filters = readIssueFilters(new URLSearchParams(search));
    expect(filters).toMatchObject({ status: ['NEW', 'IN_PROGRESS'], type: ['bug'], assignee: 'me', manager: 'me', top: true, q: '로그인', sort: 'due', page: 3 });
    expect(hasIssueFilters(filters)).toBe(true);
    expect(writeIssueFilters(filters).toString()).toBe(search);
  });

  it('관리자 확인 넷도 상태 조건으로 주소에 남는다(2026-11-25, 일감 #272)', () => {
    const filters = readIssueFilters(new URLSearchParams('status=REQUIREMENT_REVIEW&status=REVIEW&status=CONFIRMED&status=WAITING'));
    expect(filters.status).toEqual(['REQUIREMENT_REVIEW', 'REVIEW', 'CONFIRMED']);
    expect(writeIssueFilters(filters).toString()).toBe('status=REQUIREMENT_REVIEW&status=REVIEW&status=CONFIRMED');
  });

  it('보류 · 취소도 상태 조건으로 주소에 남는다(2026-11-24, 일감 #152)', () => {
    const filters = readIssueFilters(new URLSearchParams('status=ON_HOLD&status=CANCELLED'));
    expect(filters.status).toEqual(['ON_HOLD', 'CANCELLED']);
    expect(writeIssueFilters(filters).toString()).toBe('status=ON_HOLD&status=CANCELLED');
  });

  it('모르는 값은 버린다', () => {
    const filters = readIssueFilters(new URLSearchParams('status=OPEN&status=DONE&status=DONE&sort=title&page=-2&assignee=someone&manager=none&type=Bad Type'));
    expect(filters.status).toEqual(['DONE']);
    expect(filters.sort).toBe('-number');
    expect(filters.page).toBe(1);
    expect(filters.assignee).toBeNull();
    // 관리자는 "없음"이 없다(늘 있다)
    expect(filters.manager).toBeNull();
    expect(filters.type).toEqual([]);
  });

  it('api 요청 값으로 바꾼다 — me 는 내 id, 맨 위만은 parent=top, 쪽은 0부터', () => {
    const filters = readIssueFilters(new URLSearchParams('assignee=none&manager=me&top=1&page=2&q=%20%2312%20'));
    expect(toIssueListParams(filters, ME)).toEqual({
      status: undefined,
      type: undefined,
      assignee: 'none',
      manager: ME,
      agentSession: undefined,
      module: undefined,
      parent: 'top',
      q: '#12',
      sort: '-number',
      page: 1,
      size: 50,
    });
  });

  it('에이전트 세션 — 이름 · none(세션 없음)을 주소에 남기고 그대로 api 로 보낸다. 빈 값 · 너무 긴 값 · 제어 문자는 버린다', () => {
    const named = readIssueFilters(new URLSearchParams('agentSession=%20moai-works%20'));
    expect(named.agentSession).toBe('moai-works');
    expect(hasIssueFilters(named)).toBe(true);
    expect(writeIssueFilters(named).toString()).toBe('agentSession=moai-works');
    expect(toIssueListParams(named, ME).agentSession).toBe('moai-works');

    const none = readIssueFilters(new URLSearchParams('agentSession=none'));
    expect(toIssueListParams(none, ME).agentSession).toBe('none');

    expect(readIssueFilters(new URLSearchParams('agentSession=')).agentSession).toBeNull();
    expect(readIssueFilters(new URLSearchParams(`agentSession=${'a'.repeat(101)}`)).agentSession).toBeNull();
    expect(readIssueFilters(new URLSearchParams('agentSession=a%0Ab')).agentSession).toBeNull();
  });

  it('모듈 — 여럿을 주소에 남기고(겹친 것 · 모양이 틀린 key 는 버린다) 그대로 api 로 보낸다', () => {
    const filters = readIssueFilters(new URLSearchParams('module=api&module=web-frontend&module=api&module=Bad&module=web-'));
    expect(filters.module).toEqual(['api', 'web-frontend']);
    expect(hasIssueFilters(filters)).toBe(true);
    expect(writeIssueFilters(filters).toString()).toBe('module=api&module=web-frontend');
    expect(toIssueListParams(filters, ME).module).toEqual(['api', 'web-frontend']);
    expect(toIssueListParams(readIssueFilters(new URLSearchParams()), ME).module).toBeUndefined();
  });

  it('표 머리를 누르면 같은 항목은 방향을 뒤집고, 새 항목은 기본 방향으로 정렬한다', () => {
    expect(toggleIssueSort('-number', 'number')).toBe('number');
    expect(toggleIssueSort('number', 'number')).toBe('-number');
    expect(toggleIssueSort('-number', 'due')).toBe('due');
    expect(toggleIssueSort('due', 'updated')).toBe('-updated');
    expect(sortDirection('-updated', 'updated')).toBe('desc');
    expect(sortDirection('planned', 'planned')).toBe('asc');
    expect(sortDirection('planned', 'due')).toBe(false);
  });
});

describe('워크스페이스 일감 목록의 조건 (2026-11-24)', () => {
  it('프로젝트 조건(여럿)을 주소에 남기고, 워크스페이스 목록에서만 api 로 보낸다', () => {
    const filters = readIssueFilters(new URLSearchParams('project=web&project=moai-wiki&project=Bad_Key&status=NEW'));
    expect(filters.project).toEqual(['web', 'moai-wiki']);
    expect(hasIssueFilters({ ...filters, status: [] })).toBe(true);
    expect(writeIssueFilters(filters).toString()).toBe('project=web&project=moai-wiki&status=NEW');
    expect(toIssueListParams(filters, ME, true).project).toEqual(['web', 'moai-wiki']);
    // 프로젝트 하나의 목록은 주소가 이미 그 프로젝트다 — 보내지 않는다
    expect(toIssueListParams(filters, ME).project).toBeUndefined();
  });

  it('찾는 글이 번호 하나면(#123 · 123) 그 번호 — 바로 가기 링크를 보인다', () => {
    expect(issueNumberQuery('#57')).toBe(57);
    expect(issueNumberQuery(' 57 ')).toBe(57);
    expect(issueNumberQuery('#0')).toBeNull();
    expect(issueNumberQuery('#12 로그인')).toBeNull();
    expect(issueNumberQuery('로그인')).toBeNull();
  });
});
