import { describe, expect, it } from 'vitest';

import { summarizeUserAgent } from './user-agent';

describe('프로그램 정보 요약 (summarizeUserAgent)', () => {
  it('브라우저 이름 · 주 버전 · 운영체제', () => {
    expect(
      summarizeUserAgent('Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36'),
    ).toBe('Chrome 131 · macOS');
    expect(
      summarizeUserAgent(
        'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36 Edg/131.0.2903.70',
      ),
    ).toBe('Edge 131 · Windows');
    expect(summarizeUserAgent('Mozilla/5.0 (X11; Linux x86_64; rv:133.0) Gecko/20100101 Firefox/133.0')).toBe('Firefox 133 · Linux');
    expect(
      summarizeUserAgent(
        'Mozilla/5.0 (iPhone; CPU iPhone OS 18_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.1 Mobile/15E148 Safari/604.1',
      ),
    ).toBe('Safari 18 · iOS');
    expect(
      summarizeUserAgent('Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36'),
    ).toBe('Chrome 131 · Android');
  });

  it('명령줄 · 프로그램은 이름 · 버전 그대로', () => {
    expect(summarizeUserAgent('curl/8.4.0')).toBe('curl 8.4.0');
    expect(summarizeUserAgent('claude-code/1.0.3 (external, cli)')).toBe('claude-code 1.0.3');
  });

  it('알아보지 못하면 앞 40자, 없으면 null', () => {
    expect(summarizeUserAgent('something odd')).toBe('something odd');
    expect(summarizeUserAgent('x'.repeat(60))).toBe(`${'x'.repeat(40)}…`);
    expect(summarizeUserAgent(null)).toBeNull();
    expect(summarizeUserAgent('  ')).toBeNull();
  });
});
