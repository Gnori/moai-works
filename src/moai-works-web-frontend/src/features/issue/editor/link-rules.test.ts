import { describe, expect, it } from 'vitest';

import { linkProblem } from './link-rules';

describe('linkProblem — 보기가 열 수 있는 주소만', () => {
  it.each([
    'https://example.com/a?b=1',
    'http://localhost:11003/w/a',
    'mailto:someone@example.com',
    '/w/a/projects/web/issues/7',
    '#제목',
    'attachment:0193a1b2-c3d4-7e5f-8a9b-0c1d2e3f4a5b',
  ])('%s 는 받는다', (href) => {
    expect(linkProblem(href)).toBeNull();
  });

  it.each(['javascript:alert(1)', 'data:text/html,x', '//evil.example', 'ftp://x', 'attachment:../secret', 'https://', 'a b'])(
    '%s 는 받지 않는다',
    (href) => {
      expect(linkProblem(href)).toBe('linkInvalid');
    },
  );

  it('비어 있으면 넣으라고 한다', () => {
    expect(linkProblem('  ')).toBe('linkRequired');
  });
});
