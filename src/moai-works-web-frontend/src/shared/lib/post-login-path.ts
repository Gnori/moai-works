/**
 * 로그인 뒤 돌아갈 경로 — 열린 리디렉트를 막는다. 같은 사이트의 화면 경로만 받는다.
 *
 * 받지 않는 것: 다른 사이트(//evil.com, https://…), 역슬래시 우회(/\evil.com), 인증 화면 자체, API 경로.
 */
export function safeReturnPath(raw: string | null | undefined): string | null {
  if (!raw) {
    return null;
  }
  let value: string;
  try {
    value = decodeURIComponent(raw).trim();
  } catch {
    return null;
  }
  const hasControl = [...value].some((ch) => ch.charCodeAt(0) < 0x20 || ch.charCodeAt(0) === 0x7f);
  if (!value.startsWith('/') || value.startsWith('//') || value.includes('\\') || hasControl) {
    return null;
  }
  if (value.startsWith('/sign-in') || value.startsWith('/sign-up') || value.startsWith('/password-reset') || value.startsWith('/api')) {
    return null;
  }
  return value;
}

/** 인증 화면 사이를 오갈 때 returnTo 를 이어 붙인다 (안전한 값만) */
export function withReturnTo(path: string, returnTo: string | null | undefined): string {
  const safe = safeReturnPath(returnTo);
  if (!safe) {
    return path;
  }
  return `${path}${path.includes('?') ? '&' : '?'}returnTo=${encodeURIComponent(safe)}`;
}
