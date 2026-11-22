import { describe, expect, it } from 'vitest';

import type { IssueDetail } from '@/shared/api/types';

import { buildIssueCreate, buildIssuePatch, dateProblems, emptyIssueForm, formFromIssue, issueErrorField } from './issue-form';

const KIM = { id: 'aaaaaaaa-0000-0000-0000-000000000001', username: 'kim', displayName: '김철수' };
const LEE = { id: 'aaaaaaaa-0000-0000-0000-000000000002', username: 'lee', displayName: '이영희' };

const issue: IssueDetail = {
  number: 7,
  type: 'bug',
  title: '로그인 실패',
  description: '재현 방법',
  status: 'NEW',
  manager: KIM,
  assignee: null,
  parent: { number: 3, title: '인증', status: 'IN_PROGRESS', type: 'feature' },
  dueDate: '2026-12-01',
  startDate: null,
  plannedEndDate: null,
  completedOn: null,
  closedOn: null,
  createdBy: KIM,
  createdAt: '2026-11-20T00:00:00Z',
  updatedAt: '2026-11-21T00:00:00Z',
  rowVersion: 4,
  overdue: false,
  children: [],
  links: [],
  journal: [],
};

describe('일감 고치기 요청 (바꾼 필드만)', () => {
  it('아무것도 안 바꿨으면 보내지 않는다', () => {
    expect(buildIssuePatch(issue, formFromIssue(issue))).toBeNull();
    // 제목 앞뒤 공백만 다르면 바꾼 것이 아니다
    expect(buildIssuePatch(issue, { ...formFromIssue(issue), title: '  로그인 실패 ' })).toBeNull();
  });

  it('바꾼 필드만 rowVersion 과 함께 넣고, 비운 값은 null 로 보낸다', () => {
    const form = { ...formFromIssue(issue), status: 'IN_PROGRESS' as const, assigneeId: LEE.id, parent: null, dueDate: '' };
    expect(buildIssuePatch(issue, form)).toEqual({ rowVersion: 4, status: 'IN_PROGRESS', assigneeId: LEE.id, parent: null, dueDate: null });
  });

  it('관리자 · 설명 · 날짜를 바꾸고 이유를 적으면 댓글(notes)로 함께 보낸다', () => {
    const form = { ...formFromIssue(issue), managerId: LEE.id, description: '', startDate: '2026-11-25' };
    expect(buildIssuePatch(issue, form, '  담당 바꿈 ')).toEqual({
      rowVersion: 4,
      managerId: LEE.id,
      description: '',
      startDate: '2026-11-25',
      notes: '담당 바꿈',
    });
    // 바꾼 것 없이 이유만 적어도 보낸다(댓글이 남는다)
    expect(buildIssuePatch(issue, formFromIssue(issue), '메모')).toEqual({ rowVersion: 4, notes: '메모' });
  });

  it('상태만 바꾸면 완료일 · 종료일은 보내지 않는다 — 서버가 채운다', () => {
    const patch = buildIssuePatch(issue, { ...formFromIssue(issue), status: 'DONE' });
    expect(patch).toEqual({ rowVersion: 4, status: 'DONE' });
  });
});

describe('새 일감 요청', () => {
  it('비운 값은 보내지 않는다', () => {
    expect(buildIssueCreate({ ...emptyIssueForm('task'), title: ' 할 일 ' })).toEqual({ type: 'task', title: '할 일', status: 'NEW' });
  });

  it('채운 값은 모두 보낸다', () => {
    const form = { ...emptyIssueForm('bug', 3), title: '버그', description: '본문', managerId: KIM.id, assigneeId: LEE.id, dueDate: '2026-12-31', closedOn: '2026-12-01' };
    expect(buildIssueCreate(form)).toEqual({
      type: 'bug',
      title: '버그',
      status: 'NEW',
      description: '본문',
      managerId: KIM.id,
      assigneeId: LEE.id,
      parent: 3,
      dueDate: '2026-12-31',
      closedOn: '2026-12-01',
    });
  });
});

describe('날짜 순서 (api 와 같은 규칙)', () => {
  const empty = emptyIssueForm('task');
  it('값이 있는 것끼리만 본다', () => {
    expect(dateProblems(empty)).toEqual([]);
    expect(dateProblems({ ...empty, startDate: '2026-12-10' })).toEqual([]);
    expect(dateProblems({ ...empty, startDate: '2026-12-10', plannedEndDate: '2026-12-10', dueDate: '2026-12-11' })).toEqual([]);
  });

  it('시작일이 늦거나 완료일이 종료일보다 늦으면 알린다', () => {
    expect(dateProblems({ ...empty, startDate: '2026-12-10', plannedEndDate: '2026-12-09', dueDate: '2026-12-01' })).toEqual([
      { field: 'plannedEndDate', problem: 'startAfterPlanned' },
      { field: 'dueDate', problem: 'startAfterDue' },
    ]);
    expect(dateProblems({ ...empty, completedOn: '2026-12-02', closedOn: '2026-12-01' })).toEqual([
      { field: 'closedOn', problem: 'completedAfterClosed' },
    ]);
  });
});

describe('api 오류를 보일 칸', () => {
  it('칸이 있는 오류는 그 칸으로, 아니면 undefined', () => {
    expect(issueErrorField('MANAGER_NOT_READER')).toBe('managerId');
    expect(issueErrorField('ASSIGNEE_NOT_READER')).toBe('assigneeId');
    expect(issueErrorField('PARENT_CYCLE')).toBe('parent');
    expect(issueErrorField('CONCURRENT_UPDATE')).toBeUndefined();
  });
});
