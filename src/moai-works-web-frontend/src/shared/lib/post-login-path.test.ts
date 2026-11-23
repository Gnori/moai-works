import { safeReturnPath } from './post-login-path';

describe('safeReturnPath', () => {
  it('같은 사이트 화면 경로는 받는다', () => {
    expect(safeReturnPath('/w/workspace-a/members')).toBe('/w/workspace-a/members');
    expect(safeReturnPath(encodeURIComponent('/w/workspace-a?tab=1'))).toBe('/w/workspace-a?tab=1');
  });

  it('다른 사이트로 가는 경로는 막는다', () => {
    expect(safeReturnPath('https://evil.example')).toBeNull();
    expect(safeReturnPath('//evil.example')).toBeNull();
    expect(safeReturnPath('/\\evil.example')).toBeNull();
    expect(safeReturnPath('evil')).toBeNull();
  });

  it('인증 화면 · API 경로 · 빈 값은 막는다', () => {
    expect(safeReturnPath('/sign-in')).toBeNull();
    expect(safeReturnPath('/password-reset')).toBeNull();
    expect(safeReturnPath('/password-reset/confirm?email=a%40b.c')).toBeNull();
    expect(safeReturnPath('/api/v1/me')).toBeNull();
    expect(safeReturnPath('')).toBeNull();
    expect(safeReturnPath(null)).toBeNull();
    expect(safeReturnPath('%E0%A4%A')).toBeNull();
  });
});
