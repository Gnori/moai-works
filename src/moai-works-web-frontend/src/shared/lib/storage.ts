/**
 * 브라우저 저장소 — 화면 설정(언어 · 색 · 밝기)만 둔다. 토큰 · 개인 정보는 절대 넣지 않는다(SECURITY.md §9).
 * 개인 창 · 막힌 저장소에서는 읽기 · 쓰기가 실패할 수 있으므로 늘 try/catch 로 감싸고, 실패하면 기본값으로 동작한다.
 */
export const storageKeys = {
  language: 'moai-works.language',
  skin: 'moai-works.skin',
  /** 프로젝트 화면 곁의 메뉴를 접었는가 ('1' · '0') */
  projectNavCollapsed: 'moai-works.project-nav-collapsed',
  /** 다른 탭에 로그인 · 로그아웃을 알리는 신호 (BroadcastChannel 이 없을 때). 값은 사건 종류와 시각뿐 */
  sessionSignal: 'moai-works.session-signal',
} as const;

export function readSetting(key: string): string | null {
  try {
    return window.localStorage.getItem(key);
  } catch {
    return null;
  }
}

export function writeSetting(key: string, value: string): void {
  try {
    window.localStorage.setItem(key, value);
  } catch {
    // 저장할 수 없어도 화면은 그대로 동작한다
  }
}
