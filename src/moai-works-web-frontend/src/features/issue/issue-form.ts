import type { IssueCreateBody, IssueDateField, IssueDetail, IssuePatch, IssueStatus } from '@/shared/api/types';

/**
 * 일감 양식의 값 — 새 일감 · 고치기 화면이 같이 쓴다.
 * 날짜는 입력 칸 그대로(yyyy-MM-dd, 비우면 ''), 사람은 id(비우면 null), 상위 일감은 번호(없으면 null).
 */
export interface IssueFormValues {
  type: string;
  title: string;
  description: string;
  status: IssueStatus;
  /** 비우면 만들 때는 내가 관리자가 된다 */
  managerId: string | null;
  assigneeId: string | null;
  parent: number | null;
  dueDate: string;
  startDate: string;
  plannedEndDate: string;
  completedOn: string;
  closedOn: string;
}

export function emptyIssueForm(type: string, parent: number | null = null): IssueFormValues {
  return {
    type,
    title: '',
    description: '',
    status: 'NEW',
    managerId: null,
    assigneeId: null,
    parent,
    dueDate: '',
    startDate: '',
    plannedEndDate: '',
    completedOn: '',
    closedOn: '',
  };
}

export function formFromIssue(issue: IssueDetail): IssueFormValues {
  return {
    type: issue.type,
    title: issue.title,
    description: issue.description,
    status: issue.status,
    managerId: issue.manager?.id ?? null,
    assigneeId: issue.assignee?.id ?? null,
    parent: issue.parent?.number ?? null,
    dueDate: issue.dueDate ?? '',
    startDate: issue.startDate ?? '',
    plannedEndDate: issue.plannedEndDate ?? '',
    completedOn: issue.completedOn ?? '',
    closedOn: issue.closedOn ?? '',
  };
}

const dateFields: readonly IssueDateField[] = ['dueDate', 'startDate', 'plannedEndDate', 'completedOn', 'closedOn'];

/** 새 일감 요청 — 비운 값은 보내지 않는다(서버 기본값: 상태 NEW · 관리자 = 만든 사람) */
export function buildIssueCreate(form: IssueFormValues): IssueCreateBody {
  const body: IssueCreateBody = { type: form.type, title: form.title.trim(), status: form.status };
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
  return body;
}

/**
 * 고치기 요청 — 처음 읽은 일감과 달라진 필드만 넣는다(null 은 비우기). 바뀐 것도 남길 말도 없으면 null.
 * 상태만 바꾸고 완료일 · 종료일을 손대지 않으면 그 날짜는 보내지 않는다 — 서버가 상태에 맞춰 채우거나 비운다.
 */
export function buildIssuePatch(issue: IssueDetail, form: IssueFormValues, notes = ''): IssuePatch | null {
  const patch: IssuePatch = { rowVersion: issue.rowVersion };
  let changed = false;
  const put = <K extends keyof IssuePatch>(key: K, value: IssuePatch[K]) => {
    patch[key] = value;
    changed = true;
  };
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
  if (form.managerId !== (issue.manager?.id ?? null)) {
    put('managerId', form.managerId);
  }
  if (form.assigneeId !== (issue.assignee?.id ?? null)) {
    put('assigneeId', form.assigneeId);
  }
  if (form.parent !== (issue.parent?.number ?? null)) {
    put('parent', form.parent);
  }
  for (const field of dateFields) {
    const value = form[field] || null;
    if (value !== issue[field]) {
      put(field, value);
    }
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
};

export function issueErrorField(code: string): keyof IssueFormValues | undefined {
  return errorFields[code];
}
