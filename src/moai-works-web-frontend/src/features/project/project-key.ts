/**
 * 프로젝트 주소 이름(key) 규칙 — api 의 Project.KEY_PATTERN · ProjectService.RESERVED_KEYS 와 같다.
 * 화면은 미리 알려 주기만 하고, 판정은 api 가 한다.
 */

/** 영문 소문자로 시작, 소문자 · 숫자 · 하이픈, 2~32자, 하이픈으로 끝나지 않음 */
export const PROJECT_KEY_PATTERN = /^[a-z][a-z0-9-]{0,30}[a-z0-9]$/;

/** 화면 주소와 부딪혀 쓸 수 없는 이름 */
export const RESERVED_PROJECT_KEYS: readonly string[] = ['new', 'archive', 'archived', 'settings'];

export type ProjectKeyProblem = 'invalid' | 'reserved';

/** 문제가 없으면 undefined */
export function checkProjectKey(key: string): ProjectKeyProblem | undefined {
  if (!PROJECT_KEY_PATTERN.test(key)) {
    return 'invalid';
  }
  return RESERVED_PROJECT_KEYS.includes(key) ? 'reserved' : undefined;
}

/**
 * 이름에서 주소 이름을 제안한다 — 영문 · 숫자만 남기고 나머지는 하이픈으로 바꾼다.
 * 한글처럼 영문이 없는 이름이면 빈 문자열을 돌려준다(사람이 직접 적는다).
 */
export function suggestProjectKey(name: string): string {
  const key = name
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^[^a-z]+/, '')
    .slice(0, 32)
    .replace(/-+$/, '');
  return checkProjectKey(key) === undefined ? key : '';
}
