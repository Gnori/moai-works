import type { ApiError } from '@/shared/api/http';
import type { PasswordPolicyReason } from '@/shared/api/types';
import i18n from '@/shared/i18n/i18n';

const REASONS: readonly PasswordPolicyReason[] = [
  'MIN_LENGTH',
  'MAX_LENGTH',
  'DIGITS',
  'LOWER_CASE',
  'UPPER_CASE',
  'SPECIAL_CHARS',
  'NOT_USERNAME',
  'NOT_CONTAINS_USERNAME',
  'NOT_EMAIL',
  'PATTERN',
  'HISTORY',
  'BLACKLISTED',
  'OTHER',
];

function isReason(value: unknown): value is PasswordPolicyReason {
  return typeof value === 'string' && (REASONS as readonly string[]).includes(value);
}

/**
 * 비밀번호 정책 오류(400 PASSWORD_POLICY)를 화면 언어의 문장으로 — 서버가 준 까닭(reason)과 정책의 수(value, 예: 최소 길이 8)로 만든다.
 * 모르는 까닭이면 서버 문장 그대로. 가입 · 비밀번호 변경 · 비밀번호 찾기가 같이 쓴다.
 */
export function passwordPolicyMessage(error: ApiError): string {
  const reason = error.details.reason;
  if (!isReason(reason)) {
    return error.message;
  }
  const value = typeof error.details.value === 'number' ? error.details.value : undefined;
  if (value === undefined && ['MIN_LENGTH', 'MAX_LENGTH', 'DIGITS', 'LOWER_CASE', 'UPPER_CASE', 'SPECIAL_CHARS'].includes(reason)) {
    return error.message;
  }
  return i18n.t(`common:passwordPolicy.${reason}`, { value: value ?? '' });
}
