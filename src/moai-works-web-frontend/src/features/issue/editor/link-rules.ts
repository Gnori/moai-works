import { attachmentIdOf } from '../markdown-urls';

/**
 * 편집기에서 넣는 링크 주소의 규칙 — 보기(MarkdownView)가 열 수 있는 주소만 받는다.
 * http(s) · mailto, 이 화면 안의 경로(/…) · 같은 글 안의 자리(#…), 첨부 표시(attachment:<uuid>).
 * javascript: · data: 같은 것은 받지 않는다(보기에서도 버린다).
 *
 * @returns 문제의 말 열쇠(editor.*) — 문제가 없으면 null
 */
export function linkProblem(href: string): 'linkRequired' | 'linkInvalid' | null {
  const value = href.trim();
  if (!value) {
    return 'linkRequired';
  }
  if (/\s/.test(value)) {
    return 'linkInvalid';
  }
  if (attachmentIdOf(value)) {
    return null;
  }
  if (value.startsWith('/') && !value.startsWith('//')) {
    return null;
  }
  if (value.startsWith('#')) {
    return null;
  }
  return /^(https?:\/\/[^/\s]+|mailto:[^\s@]+@[^\s@]+)/i.test(value) ? null : 'linkInvalid';
}
