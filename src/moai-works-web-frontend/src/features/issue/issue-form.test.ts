import { describe, expect, it } from 'vitest';

import type { IssueDetail } from '@/shared/api/types';

import {
  buildIssueCreate,
  buildIssuePatch,
  dateProblems,
  emptyIssueForm,
  estimateProblem,
  formFromIssue,
  isMoveError,
  isMoving,
  issueErrorField,
} from './issue-form';

const KIM = { id: 'aaaaaaaa-0000-0000-0000-000000000001', username: 'kim', displayName: '김철수' };
const LEE = { id: 'aaaaaaaa-0000-0000-0000-000000000002', username: 'lee', displayName: '이영희' };

const issue: IssueDetail = {
  project: { key: 'web', name: '웹' },
  number: 7,
  type: 'bug',
  title: '로그인 실패',
  description: '재현 방법',
  status: 'NEW',
  manager: KIM,
  assignee: null,
  parent: { number: 3, title: '인증', status: 'IN_PROGRESS', type: 'feature', project: { key: 'web', name: '웹' } },
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
  revisionNo: 3,
  estimatedHours: 8,
  spentHours: 2.5,
  agentSession: null,
  modules: [],
  children: [],
  links: [],
  journal: [],
  workLogs: [],
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

  it('예상 공수는 숫자로 견주고, 바꾸면 숫자로 · 비우면 null 로 보낸다', () => {
    expect(formFromIssue(issue).estimatedHours).toBe('8');
    expect(formFromIssue({ ...issue, estimatedHours: null }).estimatedHours).toBe('');
    // 8 과 8.0 · " 8 " 은 같은 값이다
    expect(buildIssuePatch(issue, { ...formFromIssue(issue), estimatedHours: '8.0' })).toBeNull();
    expect(buildIssuePatch(issue, { ...formFromIssue(issue), estimatedHours: ' 8 ' })).toBeNull();
    expect(buildIssuePatch(issue, { ...formFromIssue(issue), estimatedHours: '12.5' })).toEqual({ rowVersion: 4, estimatedHours: 12.5 });
    expect(buildIssuePatch(issue, { ...formFromIssue(issue), estimatedHours: '' })).toEqual({ rowVersion: 4, estimatedHours: null });
    // 서버처럼 소수 둘째 자리까지
    expect(buildIssuePatch(issue, { ...formFromIssue(issue), estimatedHours: '1.234' })).toEqual({ rowVersion: 4, estimatedHours: 1.23 });
    // 없던 공수를 넣는다
    expect(buildIssuePatch({ ...issue, estimatedHours: null }, { ...formFromIssue(issue), estimatedHours: '0' })).toEqual({
      rowVersion: 4,
      estimatedHours: 0,
    });
  });

  it('숫자가 아닌 예상 공수는 보내지 않는다(양식이 먼저 막는다)', () => {
    expect(buildIssuePatch(issue, { ...formFromIssue(issue), estimatedHours: 'abc' })).toBeNull();
  });

  it('관련 모듈은 묶음으로 견주고, 바뀌면 통째로 보낸다(모두 빼면 빈 목록)', () => {
    const withModules = {
      ...issue,
      modules: [
        { key: 'api', name: 'API', active: true },
        { key: 'infra', name: '인프라', active: false },
      ],
    };
    expect(formFromIssue(withModules).modules).toEqual(['api', 'infra']);
    // 고른 차례만 다르면 같다
    expect(buildIssuePatch(withModules, { ...formFromIssue(withModules), modules: ['infra', 'api'] })).toBeNull();
    expect(buildIssuePatch(withModules, { ...formFromIssue(withModules), modules: ['api', 'docs'] })).toEqual({ rowVersion: 4, modules: ['api', 'docs'] });
    expect(buildIssuePatch(withModules, { ...formFromIssue(withModules), modules: [] })).toEqual({ rowVersion: 4, modules: [] });
  });

  it('에이전트 세션은 앞뒤 공백을 빼고 견주고, 비우면 null(세션 없음)로 보낸다', () => {
    const withSession = { ...issue, agentSession: 'moai-works' };
    expect(formFromIssue(issue).agentSession).toBe('');
    expect(formFromIssue(withSession).agentSession).toBe('moai-works');
    expect(buildIssuePatch(withSession, { ...formFromIssue(withSession), agentSession: ' moai-works ' })).toBeNull();
    expect(buildIssuePatch(withSession, { ...formFromIssue(withSession), agentSession: 'moai-wiki' })).toEqual({ rowVersion: 4, agentSession: 'moai-wiki' });
    expect(buildIssuePatch(withSession, { ...formFromIssue(withSession), agentSession: '  ' })).toEqual({ rowVersion: 4, agentSession: null });
    expect(buildIssuePatch(issue, { ...formFromIssue(issue), agentSession: 'echomind-world' })).toEqual({ rowVersion: 4, agentSession: 'echomind-world' });
  });
});

describe('새 일감 요청', () => {
  it('비운 값은 보내지 않는다', () => {
    expect(buildIssueCreate({ ...emptyIssueForm('task'), title: ' 할 일 ' })).toEqual({ type: 'task', title: '할 일', status: 'NEW' });
    expect(emptyIssueForm('task').estimatedHours).toBe('');
  });

  it('예상 공수를 적으면 숫자로 보낸다', () => {
    expect(buildIssueCreate({ ...emptyIssueForm('task'), title: '할 일', estimatedHours: '16' })).toEqual({
      type: 'task',
      title: '할 일',
      status: 'NEW',
      estimatedHours: 16,
    });
    expect(buildIssueCreate({ ...emptyIssueForm('task'), title: '할 일', estimatedHours: '0.5' })).toMatchObject({ estimatedHours: 0.5 });
    expect(buildIssueCreate({ ...emptyIssueForm('task'), title: '할 일', estimatedHours: 'x' })).not.toHaveProperty('estimatedHours');
  });

  it('관련 모듈은 고른 것이 있을 때만 보낸다', () => {
    expect(emptyIssueForm('task').modules).toEqual([]);
    expect(buildIssueCreate({ ...emptyIssueForm('task'), title: '할 일', modules: ['web-frontend', 'api'] })).toMatchObject({
      modules: ['web-frontend', 'api'],
    });
    expect(buildIssueCreate({ ...emptyIssueForm('task'), title: '할 일' })).not.toHaveProperty('modules');
  });

  it('에이전트 세션은 다듬어 보내고, 비었으면 보내지 않는다', () => {
    expect(emptyIssueForm('task').agentSession).toBe('');
    expect(buildIssueCreate({ ...emptyIssueForm('task'), title: '할 일', agentSession: ' moai-works ' })).toMatchObject({ agentSession: 'moai-works' });
    expect(buildIssueCreate({ ...emptyIssueForm('task'), title: '할 일', agentSession: '   ' })).not.toHaveProperty('agentSession');
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

describe('예상 공수 칸 (api 와 같은 규칙: 0 ~ 10000 시간)', () => {
  it('비우거나 범위 안의 숫자면 괜찮다', () => {
    expect(estimateProblem('')).toBeUndefined();
    expect(estimateProblem('  ')).toBeUndefined();
    expect(estimateProblem('0')).toBeUndefined();
    expect(estimateProblem('2.5')).toBeUndefined();
    expect(estimateProblem('.5')).toBeUndefined();
    expect(estimateProblem('10000')).toBeUndefined();
  });

  it('숫자가 아니거나 범위를 넘으면 알린다', () => {
    expect(estimateProblem('10000.01')).toBe('estimateInvalid');
    expect(estimateProblem('-1')).toBe('estimateInvalid');
    expect(estimateProblem('1e3')).toBe('estimateInvalid');
    expect(estimateProblem('여덟')).toBe('estimateInvalid');
  });
});

describe('api 오류를 보일 칸', () => {
  it('칸이 있는 오류는 그 칸으로, 아니면 undefined', () => {
    expect(issueErrorField('INVALID_ESTIMATE')).toBe('estimatedHours');
    expect(issueErrorField('MANAGER_NOT_READER')).toBe('managerId');
    expect(issueErrorField('ASSIGNEE_NOT_READER')).toBe('assigneeId');
    expect(issueErrorField('PARENT_CYCLE')).toBe('parent');
    expect(issueErrorField('CONCURRENT_UPDATE')).toBeUndefined();
  });
});

describe('다른 프로젝트로 옮기기 (2026-11-24 — 프로젝트는 일감의 속성)', () => {
  it('양식의 프로젝트는 지금 프로젝트로 시작하고, 그대로면 옮기지 않는다', () => {
    expect(formFromIssue(issue).project).toBe('web');
    expect(isMoving(issue, formFromIssue(issue))).toBe(false);
    expect(buildIssuePatch(issue, formFromIssue(issue))).toBeNull();
  });

  it('다른 프로젝트를 고르면 project 를 보내고, 상위 일감 · 관련 모듈은 보내지 않는다 — 서버가 옮기면서 정한다', () => {
    const withModules = { ...issue, modules: [{ key: 'api', name: 'API', active: true }] };
    const form = { ...formFromIssue(withModules), project: 'moai-wiki', parent: null, modules: [], title: '로그인 실패 (옮김)' };
    expect(isMoving(withModules, form)).toBe(true);
    expect(buildIssuePatch(withModules, form)).toEqual({ rowVersion: 4, project: 'moai-wiki', title: '로그인 실패 (옮김)' });
  });

  it('옮길 때만 권한 · 사람 오류를 프로젝트 칸에 보인다', () => {
    expect(issueErrorField('TARGET_PROJECT_NOT_FOUND')).toBe('project');
    expect(issueErrorField('ISSUE_TYPE_NOT_ALLOWED')).toBe('type');
    expect(isMoveError('PERMISSION_REQUIRED')).toBe(true);
    expect(isMoveError('USER_NOT_PROJECT_READER')).toBe(true);
    expect(isMoveError('CONCURRENT_UPDATE')).toBe(false);
  });
});
