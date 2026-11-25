import { confirmStatuses, issueStatuses, reasonStatuses, reviewStatuses, type IssueCreateBody, type IssueDateField, type IssueDetail, type IssuePatch, type IssuePriority, type IssueStatus } from '@/shared/api/types';

import { MAX_ESTIMATE_HOURS, parseHours } from './work-hours';

/**
 * 일감 양식의 값 — 새 일감 · 고치기 화면이 같이 쓴다.
 * 날짜는 입력 칸 그대로(yyyy-MM-dd, 비우면 ''), 사람은 id(비우면 null), 상위 일감은 번호(없으면 null),
 * 예상 공수는 입력 칸 그대로(시간, 비우면 '').
 */
export interface IssueFormValues {
  type: string;
  title: string;
  description: string;
  status: IssueStatus;
  /** 우선순위 — 새 일감은 보통(NORMAL) */
  priority: IssuePriority;
  /** 비우면 만들 때는 내가 관리자가 된다 */
  managerId: string | null;
  assigneeId: string | null;
  parent: number | null;
  dueDate: string;
  startDate: string;
  plannedEndDate: string;
  completedOn: string;
  closedOn: string;
  /** 예상 공수(시간) — 입력 칸의 글. 비우면 '' */
  estimatedHours: string;
  /** 에이전트 세션 — 이 일을 맡은 AI 세션 이름(입력 칸 그대로). 비우면 '' — 서버는 앞뒤 공백을 빼고 비면 없음으로 둔다 */
  agentSession: string;
  /** 관련 모듈 key — 고른 차례 그대로(서버가 모듈 순서로 정리한다). 없으면 [] */
  modules: string[];
  /**
   * 프로젝트 key — 고치기에서 다른 프로젝트를 고르면 그리로 옮긴다(2026-11-24, Redmine 의 Project 칸). 새 일감은 쓰지 않는다(''): 지금 프로젝트에 만든다.
   * 옮길 때 상위 일감 · 관련 모듈은 보내지 않는다 — 상위가 남으면 서버가 끊고, 모듈은 같은 key 가 옮길 프로젝트에 있을 때만 남긴다
   */
  project: string;
}

/** 에이전트 세션 이름 상한 — api 의 IssueService.MAX_AGENT_SESSION 과 같다 */
export const MAX_AGENT_SESSION = 100;

export function emptyIssueForm(type: string, parent: number | null = null): IssueFormValues {
  return {
    type,
    title: '',
    description: '',
    status: 'NEW',
    priority: 'NORMAL',
    managerId: null,
    assigneeId: null,
    parent,
    dueDate: '',
    startDate: '',
    plannedEndDate: '',
    completedOn: '',
    closedOn: '',
    estimatedHours: '',
    agentSession: '',
    modules: [],
    project: '',
  };
}

export function formFromIssue(issue: IssueDetail): IssueFormValues {
  return {
    type: issue.type,
    title: issue.title,
    description: issue.description,
    status: issue.status,
    priority: issue.priority,
    managerId: issue.manager?.id ?? null,
    assigneeId: issue.assignee?.id ?? null,
    parent: issue.parent?.number ?? null,
    dueDate: issue.dueDate ?? '',
    startDate: issue.startDate ?? '',
    plannedEndDate: issue.plannedEndDate ?? '',
    completedOn: issue.completedOn ?? '',
    closedOn: issue.closedOn ?? '',
    estimatedHours: issue.estimatedHours === null ? '' : String(issue.estimatedHours),
    agentSession: issue.agentSession ?? '',
    modules: issue.modules.map((m) => m.key),
    project: issue.project.key,
  };
}

/** 확인요청의 짝인 확인완료 — 요구사항 확인요청 → 요구사항 확인완료, 확인요청 → 확인완료. 확인요청이 아니면 null (api 의 Issue.Status.confirmation) */
export function confirmationOf(status: IssueStatus): IssueStatus | null {
  switch (status) {
    case 'REQUIREMENT_REVIEW':
      return 'REQUIREMENT_CONFIRMED';
    case 'REVIEW':
      return 'CONFIRMED';
    default:
      return null;
  }
}

/** 확인요청을 반려하면 돌아갈 곳 — 요구사항 확인요청 → 등록, 확인요청 → 진행 (2026-11-25, 일감 #272) */
export function rejectTargetOf(status: IssueStatus): IssueStatus | null {
  switch (status) {
    case 'REQUIREMENT_REVIEW':
      return 'NEW';
    case 'REVIEW':
      return 'IN_PROGRESS';
    default:
      return null;
  }
}

/** 확인요청을 짝인 확인완료가 아닌 곳으로 옮기는가 — 관리자의 반려 · 요청한 쪽의 거둠(까닭이 있어야 한다, api 의 Issue.Status.returnsReview) */
export function returnsReview(from: IssueStatus | null, to: IssueStatus): boolean {
  return from !== null && from !== to && reviewStatuses.includes(from) && to !== confirmationOf(from);
}

/**
 * 이 상태로 옮기면 까닭이 있어야 하는가 — 보류 · 취소 · 확인요청으로 "옮길" 때(이미 그 상태면 아니다)와 확인요청을 되돌릴 때.
 * api 의 STATUS_REASON_REQUIRED 와 같은 규칙
 */
export function needsStatusReason(from: IssueStatus | null, to: IssueStatus): boolean {
  return (from !== to && reasonStatuses.includes(to)) || returnsReview(from, to);
}

/**
 * 확인완료로 옮길 수 있는 사람인가 — 그 일감의 관리자이거나 이 프로젝트의 project.manage 가 있다(2026-11-25, 일감 #272).
 * 화면은 가리기만 한다 — 판정은 api(아니면 403 CONFIRM_BY_MANAGER_ONLY, MCP 로는 늘 안 된다)
 */
export function canConfirmIssue(issue: Pick<IssueDetail, 'manager'>, meId: string, permissions: readonly string[]): boolean {
  return issue.manager?.id === meId || permissions.includes('project.manage');
}

/**
 * 새 일감에서 고를 수 있는 상태 — 보류 · 취소 · 확인요청은 까닭과 함께 옮기는 상태라 만든 뒤에 바꾸고, 확인완료는 확인요청을 관리자가
 * 확인할 때 쓴다(api 도 까닭 없는 만들기를 막는다)
 */
export const creatableStatuses: readonly IssueStatus[] = issueStatuses.filter(
  (s) => !reasonStatuses.includes(s) && !confirmStatuses.includes(s),
);

/** 고치기 양식이 다른 프로젝트로 옮기려 하는가 */
export function isMoving(issue: Pick<IssueDetail, 'project'>, form: Pick<IssueFormValues, 'project'>): boolean {
  return form.project !== '' && form.project !== issue.project.key;
}

const dateFields: readonly IssueDateField[] = ['dueDate', 'startDate', 'plannedEndDate', 'completedOn', 'closedOn'];

/** 새 일감 요청 — 비운 값은 보내지 않는다(서버 기본값: 상태 NEW · 우선순위 NORMAL · 관리자 = 만든 사람). 상태 · 우선순위는 늘 보낸다 */
export function buildIssueCreate(form: IssueFormValues): IssueCreateBody {
  const body: IssueCreateBody = { type: form.type, title: form.title.trim(), status: form.status, priority: form.priority };
  if (form.description.trim()) {
    body.description = form.description;
  }
  if (form.managerId) {
    body.managerId = form.managerId;
  }
  if (form.assigneeId) {
    body.assigneeId = form.assigneeId;
  }
  if (form.parent !== null) {
    body.parent = form.parent;
  }
  for (const field of dateFields) {
    if (form[field]) {
      body[field] = form[field];
    }
  }
  const estimate = parseHours(form.estimatedHours);
  if (estimate !== null && !Number.isNaN(estimate)) {
    body.estimatedHours = estimate;
  }
  if (form.agentSession.trim()) {
    body.agentSession = form.agentSession.trim();
  }
  if (form.modules.length > 0) {
    body.modules = [...form.modules];
  }
  return body;
}

/**
 * 고치기 요청 — 처음 읽은 일감과 달라진 필드만 넣는다(null 은 비우기). 바뀐 것도 남길 말도 없으면 null.
 * 상태만 바꾸고 완료일 · 종료일을 손대지 않으면 그 날짜는 보내지 않는다 — 서버가 상태에 맞춰 채우거나 비운다.
 * 다른 프로젝트를 골랐으면 project 를 넣고(옮기기), 상위 일감 · 관련 모듈은 보내지 않는다 — 서버가 옮기면서 정한다
 * (상위가 남으면 연결을 끊고, 모듈은 옮길 프로젝트에 같은 key 로 쓰는 것만 남긴다).
 */
export function buildIssuePatch(issue: IssueDetail, form: IssueFormValues, notes = ''): IssuePatch | null {
  const patch: IssuePatch = { rowVersion: issue.rowVersion };
  let changed = false;
  const put = <K extends keyof IssuePatch>(key: K, value: IssuePatch[K]) => {
    patch[key] = value;
    changed = true;
  };
  const moving = isMoving(issue, form);
  if (moving) {
    put('project', form.project);
  }
  if (form.type !== issue.type) {
    put('type', form.type);
  }
  if (form.title.trim() !== issue.title) {
    put('title', form.title.trim());
  }
  if (form.description !== issue.description) {
    put('description', form.description);
  }
  if (form.status !== issue.status) {
    put('status', form.status);
  }
  if (form.priority !== issue.priority) {
    put('priority', form.priority);
  }
  if (form.managerId !== (issue.manager?.id ?? null)) {
    put('managerId', form.managerId);
  }
  if (form.assigneeId !== (issue.assignee?.id ?? null)) {
    put('assigneeId', form.assigneeId);
  }
  if (!moving && form.parent !== (issue.parent?.number ?? null)) {
    put('parent', form.parent);
  }
  for (const field of dateFields) {
    const value = form[field] || null;
    if (value !== issue[field]) {
      put(field, value);
    }
  }
  // 8 과 8.0 은 같은 값이다 — 숫자로 견준다. 숫자가 아니면 보내지 않는다(양식이 먼저 막는다)
  const estimate = parseHours(form.estimatedHours);
  if (!Number.isNaN(estimate) && estimate !== issue.estimatedHours) {
    put('estimatedHours', estimate);
  }
  // 앞뒤 공백만 다르면 같은 값이다 — 비우면 null(세션 없음)
  const session = form.agentSession.trim() || null;
  if (session !== (issue.agentSession ?? null)) {
    put('agentSession', session);
  }
  // 모듈은 묶음으로 견준다(고른 차례는 뜻이 없다) — 바뀌었으면 통째로 보낸다
  const before = new Set(issue.modules.map((m) => m.key));
  if (!moving && (form.modules.length !== before.size || form.modules.some((key) => !before.has(key)))) {
    put('modules', [...form.modules]);
  }
  if (notes.trim()) {
    put('notes', notes.trim());
  }
  return changed ? patch : null;
}

export type DateProblem = 'startAfterPlanned' | 'startAfterDue' | 'completedAfterClosed';

/**
 * 날짜 순서 — api 의 IssueService.validate 와 같은 규칙(값이 있을 때만):
 * 시작일 ≤ 완료 예정일, 시작일 ≤ 완료 기한, 완료일 ≤ 종료일. 어긋난 칸과 이유를 돌려준다(먼저 알려 주기만 — 판정은 api).
 */
export function dateProblems(form: Pick<IssueFormValues, IssueDateField>): { field: IssueDateField; problem: DateProblem }[] {
  const problems: { field: IssueDateField; problem: DateProblem }[] = [];
  // yyyy-MM-dd 는 글자 순서가 날짜 순서와 같다
  const after = (a: string, b: string) => a !== '' && b !== '' && a > b;
  if (after(form.startDate, form.plannedEndDate)) {
    problems.push({ field: 'plannedEndDate', problem: 'startAfterPlanned' });
  }
  if (after(form.startDate, form.dueDate)) {
    problems.push({ field: 'dueDate', problem: 'startAfterDue' });
  }
  if (after(form.completedOn, form.closedOn)) {
    problems.push({ field: 'closedOn', problem: 'completedAfterClosed' });
  }
  return problems;
}

/** 예상 공수 칸 — 비우면 괜찮고, 숫자면 0 ~ 10000 시간 (api 의 INVALID_ESTIMATE 와 같은 규칙. 먼저 알려 주기만) */
export function estimateProblem(text: string): 'estimateInvalid' | undefined {
  const value = parseHours(text);
  if (value === null) {
    return undefined;
  }
  return Number.isNaN(value) || value < 0 || value > MAX_ESTIMATE_HOURS ? 'estimateInvalid' : undefined;
}

/** api 오류 코드 → 그 오류를 보일 칸 (IssueService 의 코드). 칸이 없는 오류는 undefined — 알림으로 보인다 */
const errorFields: Readonly<Record<string, keyof IssueFormValues>> = {
  TITLE_REQUIRED: 'title',
  TITLE_TOO_LONG: 'title',
  ISSUE_TYPE_NOT_FOUND: 'type',
  MANAGER_REQUIRED: 'managerId',
  MANAGER_NOT_READER: 'managerId',
  ASSIGNEE_NOT_READER: 'assigneeId',
  PARENT_CYCLE: 'parent',
  PARENT_TOO_DEEP: 'parent',
  INVALID_ESTIMATE: 'estimatedHours',
  INVALID_AGENT_SESSION: 'agentSession',
  UNKNOWN_MODULE: 'modules',
  MODULE_INACTIVE: 'modules',
  // 옮기기(2026-11-24) — 옮길 프로젝트가 없거나 볼 수 없음 · 그 프로젝트에서 쓸 수 없는 유형
  TARGET_PROJECT_NOT_FOUND: 'project',
  ISSUE_TYPE_NOT_ALLOWED: 'type',
};

/** 옮길 때만 프로젝트 칸에 보일 오류 — 옮기지 않을 때는 같은 코드라도 다른 뜻이다(관리자 · 담당자가 이 프로젝트를 볼 수 없음 등) */
const moveErrorCodes = new Set(['USER_NOT_PROJECT_READER', 'PERMISSION_REQUIRED', 'PROJECT_NOT_WRITABLE']);

/** 옮기기 요청의 오류를 프로젝트 칸에 보일까 */
export function isMoveError(code: string): boolean {
  return moveErrorCodes.has(code);
}

export function issueErrorField(code: string): keyof IssueFormValues | undefined {
  return errorFields[code];
}
