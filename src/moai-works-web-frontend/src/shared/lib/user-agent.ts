/**
 * 브라우저 · 프로그램 정보(User-Agent)를 사람이 알아보는 짧은 말로 — "Chrome 131 · macOS".
 * 보이기만 하는 요약이다(판정에 쓰지 않는다). 원문은 화면에서 title 로 함께 보인다.
 * 알아보지 못하면 앞 40자를 그대로 쓴다.
 */
export function summarizeUserAgent(ua: string | null | undefined): string | null {
  const text = ua?.trim();
  if (!text) {
    return null;
  }
  const parts = [program(text), os(text)].filter((v): v is string => !!v);
  if (parts.length > 0) {
    return parts.join(' · ');
  }
  return text.length > 40 ? `${text.slice(0, 40)}…` : text;
}

/** 프로그램 이름 + 주 버전. 순서가 중요하다 — Edge · Opera 의 글에는 Chrome 도, Chrome 의 글에는 Safari 도 들어 있다 */
function program(ua: string): string | null {
  const rules: [RegExp, string][] = [
    [/Edg(?:e|A|iOS)?\/(\d+)/, 'Edge'],
    [/OPR\/(\d+)/, 'Opera'],
    [/Firefox\/(\d+)/, 'Firefox'],
    [/CriOS\/(\d+)/, 'Chrome'],
    [/Chrome\/(\d+)/, 'Chrome'],
    [/Version\/(\d+)[^ ]* (?:Mobile\/\S+ )?Safari\//, 'Safari'],
  ];
  for (const [pattern, name] of rules) {
    const match = pattern.exec(ua);
    if (match) {
      return `${name} ${match[1]}`;
    }
  }
  // 명령줄 · 프로그램(curl/8.4.0, claude-code/1.2.3 …) — 맨 앞 "이름/버전" 그대로
  const tool = /^([A-Za-z][\w.-]*)\/([\w.-]+)/.exec(ua);
  if (tool && !ua.startsWith('Mozilla/')) {
    return `${tool[1]} ${tool[2]}`;
  }
  return null;
}

/** 운영체제. 아이폰의 글에도 "Mac OS X" 가, 안드로이드의 글에도 "Linux" 가 들어 있어 먼저 본다 */
function os(ua: string): string | null {
  if (/iPhone|iPad|iPod/.test(ua)) {
    return 'iOS';
  }
  if (/Android/.test(ua)) {
    return 'Android';
  }
  if (/Windows/.test(ua)) {
    return 'Windows';
  }
  if (/Mac OS X|Macintosh/.test(ua)) {
    return 'macOS';
  }
  if (/CrOS/.test(ua)) {
    return 'ChromeOS';
  }
  if (/Linux/.test(ua)) {
    return 'Linux';
  }
  return null;
}
