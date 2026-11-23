/**
 * 스킬 입력 칸의 안내 — 서버(SkillFile)와 같은 한도. 판정은 서버가 하고, 화면은 저장 전에 미리 알려 줄 뿐이다.
 */

/** 이름 — Claude Code 스킬 이름 규칙: 영문 소문자 · 숫자 · 하이픈(앞뒤 · 연달아 금지), 64자까지 */
export const SKILL_NAME_MAX = 64;
const NAME = /^[a-z0-9]+(-[a-z0-9]+)*$/;

/** 설명(Agent 가 언제 쓸지 고르는 글) — 1024자까지 */
export const SKILL_DESCRIPTION_MAX = 1024;

/** 본문 — 10만 자까지(moai-wiki 와 같다) */
export const SKILL_BODY_MAX = 100_000;

/** 바꾼 까닭 — 500자까지 */
export const SKILL_NOTE_MAX = 500;

export type SkillNameProblem = 'required' | 'tooLong' | 'pattern';

/** 이름이 규칙에 맞지 않는 까닭 — 맞으면 null */
export function skillNameProblem(raw: string): SkillNameProblem | null {
  const name = raw.trim();
  if (!name) {
    return 'required';
  }
  if (name.length > SKILL_NAME_MAX) {
    return 'tooLong';
  }
  return NAME.test(name) ? null : 'pattern';
}

/** 설명의 길이 — 서버와 같이 앞뒤 공백을 빼고 센다 */
export function descriptionLength(value: string): number {
  return value.replace(/\r\n?/g, '\n').trim().length;
}

/** 서버가 저장하는 모양의 본문 — 줄바꿈 LF, 앞의 빈 줄 · 끝의 공백을 걷는다(편집기가 끝에 줄바꿈을 붙여도 바뀐 것으로 보지 않게) */
export function normalizeBody(value: string): string {
  return value.replace(/\r\n?/g, '\n').replace(/^(?:[^\S\n]*\n)+/, '').trimEnd();
}

/** 서버가 저장하는 모양의 설명 — 줄바꿈 LF, 앞뒤 공백을 걷는다 */
export function normalizeDescription(value: string): string {
  return value.replace(/\r\n?/g, '\n').trim();
}

/** 서버 오류 code → 그 오류를 보일 칸 */
export function skillErrorField(code: string): 'name' | 'description' | 'body' | 'changeNote' | undefined {
  if (code.startsWith('SKILL_NAME_')) {
    return 'name';
  }
  if (code.startsWith('SKILL_DESCRIPTION_')) {
    return 'description';
  }
  if (code.startsWith('SKILL_BODY_')) {
    return 'body';
  }
  if (code === 'SKILL_NOTE_TOO_LONG') {
    return 'changeNote';
  }
  return undefined;
}

/** 글 한 덩어리를 한 줄로 줄인다(목록의 설명 칸) */
export function oneLine(value: string): string {
  return value.replace(/\s+/g, ' ').trim();
}
