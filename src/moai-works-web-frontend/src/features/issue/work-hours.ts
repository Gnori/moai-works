import dayjs from 'dayjs';

import type { IssueWorkLog, IssueWorkLogBody } from '@/shared/api/types';

/**
 * 작업 시간 — 예상 공수(계획, 일감에 하나)와 쓴 시간(작업 시간 기록, 일감에 여러 건)을 다루는 순수 함수.
 * 규칙은 api(IssueService.logWork · hours)와 같다. 화면은 먼저 알려 주기만 하고 판정은 api 가 한다.
 */

/** 예상 공수 상한 (api MAX_ESTIMATE) */
export const MAX_ESTIMATE_HOURS = 10000;
/** 작업 시간 기록 한 건의 상한 (api MAX_WORK_HOURS) */
export const MAX_WORK_HOURS = 24;
/** 작업 시간 메모 상한 */
export const MAX_WORK_NOTE = 1000;

const HOURS = /^(\d+(\.\d*)?|\.\d+)$/;

/**
 * 시간 칸의 글 → 숫자(시간). 비우면 null, 숫자가 아니면 NaN.
 * 서버처럼 소수 둘째 자리까지만 쓴다(1.234 → 1.23).
 */
export function parseHours(text: string): number | null {
  const trimmed = text.trim();
  if (!trimmed) {
    return null;
  }
  if (!HOURS.test(trimmed)) {
    return Number.NaN;
  }
  return Math.round(Number(trimmed) * 100) / 100;
}

/** 시간 숫자 → 보이는 글. 6.5 → "6.5", 10 → "10", 0.1 + 0.2 → "0.3" (단위는 화면 글이 붙인다) */
export function formatHours(hours: number): string {
  return String(Math.round(hours * 100) / 100);
}

/** 오늘 (이 브라우저의 날짜, yyyy-MM-dd) — 작업 시간의 일한 날 기본값이자 상한 */
export function todayIso(): string {
  return dayjs().format('YYYY-MM-DD');
}

/** 작업 시간 적기 양식의 값 (입력 칸 그대로) */
export interface WorkLogFormValues {
  workDate: string;
  hours: string;
  note: string;
}

export type WorkLogProblem = 'dateRequired' | 'dateFuture' | 'hoursInvalid' | 'noteTooLong';

/** 칸마다 무엇이 잘못됐나 — 일한 날은 오늘까지, 시간은 0 보다 크고 24 이하, 메모는 1000자까지 */
export function workLogProblems(form: WorkLogFormValues, today: string): Partial<Record<keyof WorkLogFormValues, WorkLogProblem>> {
  const problems: Partial<Record<keyof WorkLogFormValues, WorkLogProblem>> = {};
  if (!form.workDate) {
    problems.workDate = 'dateRequired';
  } else if (form.workDate > today) {
    // yyyy-MM-dd 는 글자 순서가 날짜 순서와 같다
    problems.workDate = 'dateFuture';
  }
  const hours = parseHours(form.hours);
  if (hours === null || Number.isNaN(hours) || hours <= 0 || hours > MAX_WORK_HOURS) {
    problems.hours = 'hoursInvalid';
  }
  if (form.note.trim().length > MAX_WORK_NOTE) {
    problems.note = 'noteTooLong';
  }
  return problems;
}

/** 작업 시간 적기 요청 — 메모는 비우면 보내지 않는다. 양식이 맞는지(workLogProblems)를 먼저 본 뒤에 부른다 */
export function buildWorkLog(form: WorkLogFormValues): IssueWorkLogBody {
  const body: IssueWorkLogBody = { workDate: form.workDate, hours: parseHours(form.hours) ?? 0 };
  const note = form.note.trim();
  if (note) {
    body.note = note;
  }
  return body;
}

/** api 오류 코드 → 그 오류를 보일 칸. 칸이 없는 오류는 undefined — 알림으로 보인다 */
export function workLogErrorField(code: string): keyof WorkLogFormValues | undefined {
  switch (code) {
    case 'WORK_DATE_REQUIRED':
    case 'INVALID_WORK_DATE':
      return 'workDate';
    case 'INVALID_WORK_HOURS':
      return 'hours';
    case 'TEXT_TOO_LONG':
      return 'note';
    default:
      return undefined;
  }
}

/**
 * 이 기록의 지우기 단추를 보일까 — 내 기록이거나, 프로젝트를 관리할 수 있으면(project.manage) 남의 기록도.
 * api 가 같은 규칙으로 판정한다(여기는 단추를 가리기만).
 */
export function canRemoveWorkLog(log: Pick<IssueWorkLog, 'user'>, myId: string, canManage: boolean): boolean {
  return canManage || (log.user !== null && log.user.id === myId);
}

/**
 * 쓴 시간 / 예상 — 예상이 있고 0 보다 클 때만. percent 는 반올림한 백분율, over 는 예상을 넘긴 시간(안 넘었으면 0).
 */
export function spentProgress(spent: number, estimate: number | null): { percent: number; over: number } | null {
  if (estimate === null || estimate <= 0) {
    return null;
  }
  return {
    percent: Math.round((spent / estimate) * 100),
    over: Math.max(0, Math.round((spent - estimate) * 100) / 100),
  };
}
