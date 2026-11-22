/**
 * 디자인 토큰 — 스킨과 상관없이 같은 값(중립색 · 상태색 · 글꼴 · 모서리 · 크기). 스킨(주 색)은 skins.ts.
 * 화면 코드는 여기 값을 직접 쓰지 않고 MUI theme 이름(primary.main, text.secondary …)으로만 쓴다.
 *
 * 중립색 "현무암": 순회색이 아니라 파랑 쪽으로 아주 조금 기운 회색.
 */

export const neutral = {
  0: '#FFFFFF',
  25: '#FAFBFC',
  50: '#F5F7FA',
  100: '#EEF1F5',
  200: '#E2E6EC',
  300: '#CDD3DC',
  400: '#9AA3B2',
  500: '#6B7487',
  600: '#4B5468',
  700: '#353D4F',
  800: '#212837',
  850: '#191F2C',
  900: '#121722',
  950: '#0C1018',
} as const;

/** 상태색 — 라이트: light 는 연한 색조, main 은 그 위 글자색. 다크: main 이 밝다 */
export const status = {
  light: {
    success: { light: '#E5F6EC', main: '#13733A', dark: '#14532D' },
    warning: { light: '#FEF3E2', main: '#B45309', dark: '#92400E' },
    error: { light: '#FDECEC', main: '#C42B2B', dark: '#991B1B' },
    info: { light: '#E8F1FE', main: '#1D5FD6', dark: '#1E40AF' },
  },
  dark: {
    success: { main: '#4ADE80', dark: '#22C55E' },
    warning: { main: '#FBBF24', dark: '#F59E0B' },
    error: { main: '#F87171', dark: '#EF4444' },
    info: { main: '#7FB0FA', dark: '#5B94F5' },
  },
} as const;

/**
 * 코드 강조의 글자 색 — 본문의 코드 블록(Markdown 보기 화면을 만들 때 쓴다). 스킨과 상관없이 같다.
 * 코드 상자의 바탕(라이트 neutral 100 · 다크 neutral 850) 위에서 모두 WCAG AA 를 넘는다(contrast.test.ts).
 */
export const code = {
  light: {
    comment: '#57606A',
    keyword: '#CF222E',
    string: '#0A3069',
    value: '#0550AE',
    title: '#6F42C1',
    builtin: '#953800',
    tag: '#116329',
    added: '#116329',
    addedBg: '#DAFBE1',
    removed: '#82071E',
    removedBg: '#FFEBE9',
  },
  dark: {
    comment: '#9198A1',
    keyword: '#FF7B72',
    string: '#A5D6FF',
    value: '#79C0FF',
    title: '#D2A8FF',
    builtin: '#FFA657',
    tag: '#7EE787',
    added: '#AFF5B4',
    addedBg: '#033A16',
    removed: '#FFDCD7',
    removedBg: '#67060C',
  },
} as const;

export const typography = {
  fontFamily:
    "'Pretendard Variable', Pretendard, -apple-system, BlinkMacSystemFont, 'Apple SD Gothic Neo', 'Malgun Gothic', 'Noto Sans KR', system-ui, sans-serif",
  monoFamily: "'JetBrains Mono', 'SFMono-Regular', Menlo, Consolas, monospace",
} as const;

export const radius = {
  sm: 6,
  md: 10,
  lg: 14,
} as const;

/** 사이드바 · 머리글 크기 */
export const layout = {
  sidebarWidth: 272,
  /** 접은 사이드바(아이콘만) 너비 */
  sidebarCompactWidth: 72,
  topbarHeight: 56,
} as const;
